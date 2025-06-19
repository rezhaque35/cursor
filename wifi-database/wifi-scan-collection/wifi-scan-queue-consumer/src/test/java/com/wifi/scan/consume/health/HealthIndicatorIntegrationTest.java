package com.wifi.scan.consume.health;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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
} 