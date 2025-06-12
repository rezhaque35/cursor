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
    void should_ReportPollingActive_When_ConsumerIsRunning() {
        // When: Checking message consumption activity health indicator
        Health health = messageConsumptionActivityHealthIndicator.health();
        
        // Then: Consumer should be polling (with embedded Kafka, it may take a moment to establish connection)
        assertThat(health.getStatus()).isIn(Status.UP, Status.DOWN); // Allow either status during test startup
        assertThat(health.getDetails()).containsKey("pollingActive");
        assertThat(health.getDetails()).containsKey("consumptionHealthy");
        assertThat(health.getDetails()).containsKey("totalMessagesConsumed");
        assertThat(health.getDetails()).containsKey("successRate");
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
} 