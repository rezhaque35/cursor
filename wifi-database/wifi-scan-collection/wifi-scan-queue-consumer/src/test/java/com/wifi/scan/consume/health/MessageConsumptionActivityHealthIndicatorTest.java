package com.wifi.scan.consume.health;

import com.wifi.scan.consume.config.HealthIndicatorConfiguration;
import com.wifi.scan.consume.metrics.KafkaConsumerMetrics;
import com.wifi.scan.consume.service.KafkaMonitoringService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Unit tests for MessageConsumptionActivityHealthIndicator (Liveness).
 * Tests the liveness-focused behavior that checks connectivity and responsiveness,
 * not active message consumption.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Message Consumption Activity Health Indicator Tests (Liveness)")
class MessageConsumptionActivityHealthIndicatorTest {

    @Mock
    private KafkaMonitoringService kafkaMonitoringService;

    @Mock
    private HealthIndicatorConfiguration config;

    @Mock
    private KafkaConsumerMetrics metrics;

    private MessageConsumptionActivityHealthIndicator healthIndicator;

    @BeforeEach
    void setUp() {
        healthIndicator = new MessageConsumptionActivityHealthIndicator(kafkaMonitoringService, config);
    }

    @Test
    @DisplayName("should_ReturnUp_When_ConsumerConnectedAndGroupActive")
    void should_ReturnUp_When_ConsumerConnectedAndGroupActive() {
        // Given
        when(kafkaMonitoringService.isConsumerConnected()).thenReturn(true);
        when(kafkaMonitoringService.isConsumerGroupActive()).thenReturn(true);
        when(kafkaMonitoringService.getMessageConsumptionRate(Duration.ofMinutes(10))).thenReturn(0.0);
        when(kafkaMonitoringService.getMetrics()).thenReturn(metrics);
        when(metrics.getTotalMessagesConsumed()).thenReturn(new AtomicLong(0));
        when(metrics.getTotalMessagesProcessed()).thenReturn(new AtomicLong(0));
        when(metrics.getSuccessRate()).thenReturn(100.0);

        // When
        Health health = healthIndicator.health();

        // Then
        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails().get("consumerConnected")).isEqualTo(true);
        assertThat(health.getDetails().get("consumerGroupActive")).isEqualTo(true);
        assertThat(health.getDetails().get("reason")).isEqualTo("Consumer is alive and ready to process messages");
        assertThat(health.getDetails().get("livenessNote")).isEqualTo("This probe indicates application liveness, not active consumption");
    }

    @Test
    @DisplayName("should_ReturnUp_When_ConsumerConnectedButNoMessages")
    void should_ReturnUp_When_ConsumerConnectedButNoMessages() {
        // Given: Consumer is connected but no messages have been processed
        when(kafkaMonitoringService.isConsumerConnected()).thenReturn(true);
        when(kafkaMonitoringService.isConsumerGroupActive()).thenReturn(true);
        when(kafkaMonitoringService.getMessageConsumptionRate(Duration.ofMinutes(10))).thenReturn(0.0);
        when(kafkaMonitoringService.getMetrics()).thenReturn(metrics);
        when(metrics.getTotalMessagesConsumed()).thenReturn(new AtomicLong(0));
        when(metrics.getTotalMessagesProcessed()).thenReturn(new AtomicLong(0));
        when(metrics.getSuccessRate()).thenReturn(0.0);

        // When
        Health health = healthIndicator.health();

        // Then: Should still be UP because liveness only cares about connectivity
        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails().get("consumptionRate")).isEqualTo(0.0);
        assertThat(health.getDetails().get("totalMessagesConsumed")).isEqualTo(0L);
        assertThat(health.getDetails().get("reason")).isEqualTo("Consumer is alive and ready to process messages");
    }

    @Test
    @DisplayName("should_ReturnDown_When_ConsumerNotConnected")
    void should_ReturnDown_When_ConsumerNotConnected() {
        // Given
        when(kafkaMonitoringService.isConsumerConnected()).thenReturn(false);
        when(kafkaMonitoringService.isConsumerGroupActive()).thenReturn(false);
        when(kafkaMonitoringService.getMessageConsumptionRate(Duration.ofMinutes(10))).thenReturn(0.0);
        when(kafkaMonitoringService.getMetrics()).thenReturn(metrics);
        when(metrics.getTotalMessagesConsumed()).thenReturn(new AtomicLong(0));
        when(metrics.getTotalMessagesProcessed()).thenReturn(new AtomicLong(0));
        when(metrics.getSuccessRate()).thenReturn(0.0);

        // When
        Health health = healthIndicator.health();

        // Then
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails().get("consumerConnected")).isEqualTo(false);
        assertThat(health.getDetails().get("reason")).isEqualTo("Consumer cannot connect to Kafka cluster");
    }

    @Test
    @DisplayName("should_ReturnDown_When_ConsumerGroupNotActive")
    void should_ReturnDown_When_ConsumerGroupNotActive() {
        // Given
        when(kafkaMonitoringService.isConsumerConnected()).thenReturn(true);
        when(kafkaMonitoringService.isConsumerGroupActive()).thenReturn(false);
        when(kafkaMonitoringService.getMessageConsumptionRate(Duration.ofMinutes(10))).thenReturn(0.0);
        when(kafkaMonitoringService.getMetrics()).thenReturn(metrics);
        when(metrics.getTotalMessagesConsumed()).thenReturn(new AtomicLong(0));
        when(metrics.getTotalMessagesProcessed()).thenReturn(new AtomicLong(0));
        when(metrics.getSuccessRate()).thenReturn(0.0);

        // When
        Health health = healthIndicator.health();

        // Then
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails().get("consumerConnected")).isEqualTo(true);
        assertThat(health.getDetails().get("consumerGroupActive")).isEqualTo(false);
        assertThat(health.getDetails().get("reason")).isEqualTo("Consumer is not active in consumer group");
    }

    @Test
    @DisplayName("should_ReturnUp_When_ConsumerConnectedAndProcessingMessages")
    void should_ReturnUp_When_ConsumerConnectedAndProcessingMessages() {
        // Given: Consumer is connected and has processed messages
        when(kafkaMonitoringService.isConsumerConnected()).thenReturn(true);
        when(kafkaMonitoringService.isConsumerGroupActive()).thenReturn(true);
        when(kafkaMonitoringService.getMessageConsumptionRate(Duration.ofMinutes(10))).thenReturn(5.5);
        when(kafkaMonitoringService.getMetrics()).thenReturn(metrics);
        when(metrics.getTotalMessagesConsumed()).thenReturn(new AtomicLong(100));
        when(metrics.getTotalMessagesProcessed()).thenReturn(new AtomicLong(98));
        when(metrics.getSuccessRate()).thenReturn(98.0);

        // When
        Health health = healthIndicator.health();

        // Then
        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails().get("consumptionRate")).isEqualTo(5.5);
        assertThat(health.getDetails().get("totalMessagesConsumed")).isEqualTo(100L);
        assertThat(health.getDetails().get("totalMessagesProcessed")).isEqualTo(98L);
        assertThat(health.getDetails().get("successRate")).isEqualTo(98.0);
    }

    @Test
    @DisplayName("should_ReturnDown_When_ExceptionOccurs")
    void should_ReturnDown_When_ExceptionOccurs() {
        // Given
        when(kafkaMonitoringService.isConsumerConnected()).thenThrow(new RuntimeException("Connection failed"));

        // When
        Health health = healthIndicator.health();

        // Then
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails().get("error")).isEqualTo("Connection failed");
        assertThat(health.getDetails().get("reason")).isEqualTo("Health check failed due to exception");
    }

    @Test
    @DisplayName("should_IncludeAllRequiredDetails_When_HealthChecked")
    void should_IncludeAllRequiredDetails_When_HealthChecked() {
        // Given
        when(kafkaMonitoringService.isConsumerConnected()).thenReturn(true);
        when(kafkaMonitoringService.isConsumerGroupActive()).thenReturn(true);
        when(kafkaMonitoringService.getMessageConsumptionRate(Duration.ofMinutes(10))).thenReturn(2.5);
        when(kafkaMonitoringService.getMetrics()).thenReturn(metrics);
        when(metrics.getTotalMessagesConsumed()).thenReturn(new AtomicLong(50));
        when(metrics.getTotalMessagesProcessed()).thenReturn(new AtomicLong(48));
        when(metrics.getSuccessRate()).thenReturn(96.0);

        // When
        Health health = healthIndicator.health();

        // Then
        assertThat(health.getDetails()).containsKeys(
            "consumerConnected",
            "consumerGroupActive", 
            "consumptionRate",
            "totalMessagesConsumed",
            "totalMessagesProcessed",
            "successRate",
            "livenessNote",
            "reason",
            "checkTimestamp"
        );
    }
} 