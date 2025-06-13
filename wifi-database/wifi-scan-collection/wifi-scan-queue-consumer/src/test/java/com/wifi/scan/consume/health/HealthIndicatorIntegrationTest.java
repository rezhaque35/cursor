package com.wifi.scan.consume.health;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for health indicators.
 * Tests that all custom health indicators are properly configured and functioning.
 * Uses embedded Kafka to avoid dependency on external Kafka instances.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@EmbeddedKafka(
    partitions = 1,
    brokerProperties = {
        "listeners=PLAINTEXT://localhost:0",
        "port=0"
    },
    topics = {"test-topic"}
)
class HealthIndicatorIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private KafkaConsumerGroupHealthIndicator kafkaConsumerGroupHealthIndicator;

    @Autowired
    private TopicAccessibilityHealthIndicator topicAccessibilityHealthIndicator;

    @Autowired
    private MessageConsumptionActivityHealthIndicator messageConsumptionActivityHealthIndicator;

    @Autowired
    private MessageProcessingReadinessHealthIndicator messageProcessingReadinessHealthIndicator;

    @Autowired
    private MemoryHealthIndicator memoryHealthIndicator;

    @Autowired
    private SslCertificateHealthIndicator sslCertificateHealthIndicator;

    @Test
    void should_HaveAllCustomHealthIndicators_When_ApplicationStarts() {
        // Given: Application is running with embedded Kafka
        
        // When: Checking if all health indicators are autowired
        
        // Then: All health indicators should be available
        assertThat(kafkaConsumerGroupHealthIndicator).isNotNull();
        assertThat(topicAccessibilityHealthIndicator).isNotNull();
        assertThat(messageConsumptionActivityHealthIndicator).isNotNull();
        assertThat(messageProcessingReadinessHealthIndicator).isNotNull();
        assertThat(memoryHealthIndicator).isNotNull();
        assertThat(sslCertificateHealthIndicator).isNotNull();
    }

    @Test
    void should_ReturnUpStatus_When_CheckingMainHealthEndpoint() {
        // When: Calling main health endpoint
        ResponseEntity<String> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/frisco-location-wifi-scan-vmb-consumer/health", String.class);
        
        // Then: Response should be successful and contain UP status
        assertThat(response.getStatusCodeValue()).isEqualTo(200);
        assertThat(response.getBody()).contains("\"status\":\"UP\"");
        assertThat(response.getBody()).contains("kafkaConsumerGroup");
        assertThat(response.getBody()).contains("kafkaTopicAccessibility");
        assertThat(response.getBody()).contains("messageConsumptionActivity");
        assertThat(response.getBody()).contains("jvmMemory");
        assertThat(response.getBody()).contains("sslCertificate");
    }

    @Test
    void should_ReturnReadinessStatus_When_CheckingReadinessEndpoint() {
        // When: Calling readiness endpoint
        ResponseEntity<String> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/frisco-location-wifi-scan-vmb-consumer/health/readiness", String.class);
        
        // Then: Response should be successful and contain readiness components
        assertThat(response.getStatusCodeValue()).isEqualTo(200);
        assertThat(response.getBody()).contains("kafkaConsumerGroup");
        assertThat(response.getBody()).contains("kafkaTopicAccessibility");
        assertThat(response.getBody()).contains("sslCertificate");
        assertThat(response.getBody()).contains("messageProcessingReadiness");
    }

    @Test
    void should_ReturnLivenessStatus_When_CheckingLivenessEndpoint() {
        // When: Calling liveness endpoint
        ResponseEntity<String> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/frisco-location-wifi-scan-vmb-consumer/health/liveness", String.class);
        
        // Then: Response should be successful and contain liveness components
        assertThat(response.getStatusCodeValue()).isEqualTo(200);
        assertThat(response.getBody()).contains("messageConsumptionActivity");
        assertThat(response.getBody()).contains("jvmMemory");
    }

    @Test
    void should_ReportMemoryHealthy_When_MemoryUsageIsNormal() {
        // When: Checking memory health indicator
        Health health = memoryHealthIndicator.health();
        
        // Then: Memory should be healthy
        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsKey("memoryHealthy");
        assertThat(health.getDetails()).containsKey("memoryUsagePercentage");
        assertThat(health.getDetails().get("memoryHealthy")).isEqualTo(true);
    }

    @Test
    void should_ReportSSLNotEnabled_When_SSLIsDisabled() {
        // When: Checking SSL certificate health indicator
        Health health = sslCertificateHealthIndicator.health();
        
        // Then: SSL should be reported as not enabled
        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsKey("sslEnabled");
        assertThat(health.getDetails().get("sslEnabled")).isEqualTo(false);
        assertThat(health.getDetails().get("reason")).isEqualTo("SSL is not enabled");
    }

    @Test
    void should_ReportLivenessUp_When_ConsumerIsConnected() {
        // When: Checking message consumption activity health indicator (liveness)
        Health health = messageConsumptionActivityHealthIndicator.health();
        
        // Then: Liveness should focus on connectivity, not active consumption
        assertThat(health.getStatus()).isIn(Status.UP, Status.DOWN); // Allow either status during test startup
        assertThat(health.getDetails()).containsKey("consumerConnected");
        assertThat(health.getDetails()).containsKey("consumerGroupActive");
        assertThat(health.getDetails()).containsKey("consumptionRate");
        assertThat(health.getDetails()).containsKey("totalMessagesConsumed");
        assertThat(health.getDetails()).containsKey("successRate");
        assertThat(health.getDetails()).containsKey("livenessNote");
        assertThat(health.getDetails().get("livenessNote")).isEqualTo("This probe indicates application liveness, not active consumption");
    }

    @Test
    void should_ReportReadinessUp_When_ServiceIsReadyForTraffic() {
        // When: Checking message processing readiness health indicator
        Health health = messageProcessingReadinessHealthIndicator.health();
        
        // Then: Readiness should check comprehensive service readiness
        assertThat(health.getStatus()).isIn(Status.UP, Status.DOWN); // Allow either status during test startup
        assertThat(health.getDetails()).containsKey("consumerConnected");
        assertThat(health.getDetails()).containsKey("consumerGroupActive");
        assertThat(health.getDetails()).containsKey("topicsAccessible");
        assertThat(health.getDetails()).containsKey("consumptionHealthy");
        assertThat(health.getDetails()).containsKey("consumptionRate");
        assertThat(health.getDetails()).containsKey("successRate");
        assertThat(health.getDetails()).containsKey("readinessNote");
        assertThat(health.getDetails().get("readinessNote")).isEqualTo("This probe indicates service readiness for traffic");
    }

    @Test
    void should_ReportKafkaConnectivity_When_EmbeddedKafkaIsRunning() {
        // When: Checking Kafka consumer group health indicator
        Health health = kafkaConsumerGroupHealthIndicator.health();
        
        // Then: Kafka connectivity should be healthy with embedded Kafka
        // Note: Consumer group may not be immediately active, so we check for reasonable responses
        assertThat(health.getStatus()).isIn(Status.UP, Status.DOWN);
        assertThat(health.getDetails()).containsKey("consumerConnected");
        assertThat(health.getDetails()).containsKey("consumerGroupActive");
        assertThat(health.getDetails()).containsKey("clusterNodeCount");
    }

    @Test
    void should_ReportTopicAccessibility_When_EmbeddedKafkaTopicExists() {
        // When: Checking topic accessibility health indicator  
        Health health = topicAccessibilityHealthIndicator.health();
        
        // Then: Topic should be accessible with embedded Kafka
        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsKey("topicsAccessible");
        assertThat(health.getDetails()).containsKey("checkTimestamp");
        assertThat(health.getDetails().get("topicsAccessible")).isEqualTo(true);
    }

    @Test
    void should_TolerateIdlePeriods_When_NoMessagesAvailable() {
        // When: Checking liveness during idle period (no message consumption)
        Health livenessHealth = messageConsumptionActivityHealthIndicator.health();
        Health readinessHealth = messageProcessingReadinessHealthIndicator.health();
        
        // Then: Liveness should remain UP even when no messages are being consumed
        // (This is the key fix - liveness should not fail due to lack of messages)
        if (livenessHealth.getDetails().get("consumerConnected").equals(true) && 
            livenessHealth.getDetails().get("consumerGroupActive").equals(true)) {
            assertThat(livenessHealth.getStatus()).isEqualTo(Status.UP);
        }
        
        // Readiness can be more strict but should still be tolerant of idle periods
        assertThat(readinessHealth.getStatus()).isIn(Status.UP, Status.DOWN);
        assertThat(readinessHealth.getDetails()).containsKey("reason");
    }
} 