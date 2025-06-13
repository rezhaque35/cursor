package com.wifi.scan.consume.health;

import com.wifi.scan.consume.config.HealthIndicatorConfiguration;
import com.wifi.scan.consume.service.KafkaMonitoringService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Health indicator for message consumption activity suitable for liveness probes.
 * 
 * Liveness focuses on whether the application is alive and responsive, not on active consumption.
 * This indicator checks:
 * - Consumer connectivity (can connect to Kafka)
 * - Consumer group membership (properly registered)
 * - Application responsiveness (not deadlocked)
 * 
 * It does NOT fail when there are no messages to consume, as this is a normal operational state.
 */
@Slf4j
@Component("messageConsumptionActivity")
public class MessageConsumptionActivityHealthIndicator implements HealthIndicator {

    private final KafkaMonitoringService kafkaMonitoringService;
    private final HealthIndicatorConfiguration config;

    @Autowired
    public MessageConsumptionActivityHealthIndicator(KafkaMonitoringService kafkaMonitoringService,
                                                   HealthIndicatorConfiguration config) {
        this.kafkaMonitoringService = kafkaMonitoringService;
        this.config = config;
    }

    @Override
    public Health health() {
        try {
            log.debug("Checking consumer liveness (connectivity and responsiveness)");
            
            // For liveness, we only care if the consumer is connected and responsive
            // We don't fail if there are no messages to consume
            boolean isConsumerConnected = kafkaMonitoringService.isConsumerConnected();
            boolean isConsumerGroupActive = kafkaMonitoringService.isConsumerGroupActive();
            
            // Calculate metrics for monitoring purposes (but don't fail on them)
            double consumptionRate = kafkaMonitoringService.getMessageConsumptionRate(Duration.ofMinutes(10));
            long totalConsumed = kafkaMonitoringService.getMetrics().getTotalMessagesConsumed().get();
            long totalProcessed = kafkaMonitoringService.getMetrics().getTotalMessagesProcessed().get();
            
            // Liveness is UP if consumer can connect and participate in the consumer group
            // This means the application is alive and capable of consuming messages when they arrive
            Health.Builder healthBuilder = (isConsumerConnected && isConsumerGroupActive) ? 
                    Health.up() : Health.down();
            
            // Provide detailed reason only for actual connectivity issues
            if (!isConsumerConnected) {
                healthBuilder.withDetail("reason", "Consumer cannot connect to Kafka cluster");
            } else if (!isConsumerGroupActive) {
                healthBuilder.withDetail("reason", "Consumer is not active in consumer group");
            } else {
                healthBuilder.withDetail("reason", "Consumer is alive and ready to process messages");
            }
            
            return healthBuilder
                    .withDetail("consumerConnected", isConsumerConnected)
                    .withDetail("consumerGroupActive", isConsumerGroupActive)
                    .withDetail("consumptionRate", consumptionRate)
                    .withDetail("totalMessagesConsumed", totalConsumed)
                    .withDetail("totalMessagesProcessed", totalProcessed)
                    .withDetail("successRate", kafkaMonitoringService.getMetrics().getSuccessRate())
                    .withDetail("livenessNote", "This probe indicates application liveness, not active consumption")
                    .withDetail("checkTimestamp", System.currentTimeMillis())
                    .build();
            
        } catch (Exception e) {
            log.error("Error checking consumer liveness", e);
            return Health.down()
                    .withDetail("error", e.getMessage())
                    .withDetail("reason", "Health check failed due to exception")
                    .withDetail("checkTimestamp", System.currentTimeMillis())
                    .build();
        }
    }
} 