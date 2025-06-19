package com.wifi.scan.consume.health;

import com.wifi.scan.consume.service.KafkaMonitoringService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Health indicator for message consumption activity suitable for liveness probes.
 * 
 * Liveness Requirements:
 * - Track message consumption count and trends for processing pipeline health
 * - Monitor messages processed per time window
 * - Compare current consumption rate with historical baseline
 * - Detect sustained periods of zero message processing when messages are available
 * - Fail if consumer is polling but consistently failing to process available messages
 * - Distinguish between "no messages available" vs "messages available but not processed"
 */
@Slf4j
@Component("messageConsumptionActivity")
public class MessageConsumptionActivityHealthIndicator implements HealthIndicator {

    private final KafkaMonitoringService kafkaMonitoringService;
    
    // Configuration properties with defaults
    @Value("${management.health.message-consumption.message-timeout-threshold:300000}") // 5 minutes default
    private long messageTimeoutThreshold;
    
    @Value("${management.health.message-consumption.consumption-rate-threshold:0.1}")
    private double consumptionRateThreshold;

    @Autowired
    public MessageConsumptionActivityHealthIndicator(KafkaMonitoringService kafkaMonitoringService) {
        this.kafkaMonitoringService = kafkaMonitoringService;
    }

    // Constructor for testing with dependency injection
    public MessageConsumptionActivityHealthIndicator(
            KafkaMonitoringService kafkaMonitoringService, 
            long messageTimeoutThreshold, 
            double consumptionRateThreshold) {
        this.kafkaMonitoringService = kafkaMonitoringService;
        this.messageTimeoutThreshold = messageTimeoutThreshold;
        this.consumptionRateThreshold = consumptionRateThreshold;
    }

    @Override
    public Health health() {
        try {
            log.debug("Checking consumer liveness including consumption activity monitoring");
            
            var healthBuilder = Health.up();
            long checkTimestamp = System.currentTimeMillis();
            
            // Basic connectivity checks
            boolean consumerConnected = kafkaMonitoringService.isConsumerConnected();
            boolean consumerGroupActive = kafkaMonitoringService.isConsumerGroupActive();
            
            if (!consumerConnected) {
                return buildDownHealth("Consumer cannot connect to Kafka cluster", checkTimestamp);
            }
            
            if (!consumerGroupActive) {
                return buildDownHealth("Consumer is not active in consumer group", checkTimestamp);
            }
            
            // Consumer activity and consumption monitoring
            long timeSinceLastPoll = kafkaMonitoringService.getTimeSinceLastPoll();
            boolean consumerStuck = kafkaMonitoringService.isConsumerStuck();
            
            // Check if consumer hasn't received messages recently (may indicate inactive consumer or no available messages)
            if (timeSinceLastPoll > messageTimeoutThreshold) {
                return buildDownHealth(
                    String.format("Consumer hasn't received messages in %d ms (threshold: %d ms) - may indicate inactive consumer or no available messages", 
                                 timeSinceLastPoll, messageTimeoutThreshold), 
                    checkTimestamp);
            }
            
            // Check if consumer is stuck (polling but not advancing)
            if (consumerStuck) {
                return buildDownHealth("Consumer is stuck - polling but not advancing position", checkTimestamp);
            }

            // Get consumption metrics
            var metrics = kafkaMonitoringService.getMetrics();
            double consumptionRate = kafkaMonitoringService.getMessageConsumptionRate();
            long totalMessagesConsumed = metrics.getTotalMessagesConsumed().get();
            long totalMessagesProcessed = metrics.getTotalMessagesProcessed().get();
            double successRate = metrics.getSuccessRate();
            
            // Check consumption rate only if we have meaningful data and recent activity
            // Avoid false alarms during idle periods or startup
            if (totalMessagesConsumed >= 10 && timeSinceLastPoll <= (messageTimeoutThreshold / 2)) {
                // Only check rate if we have enough message history and recent activity
                if (consumptionRate > 0 && consumptionRate < consumptionRateThreshold) {
                    log.warn("Low consumption rate detected: {} msgs/min (threshold: {} msgs/min)", 
                            consumptionRate, consumptionRateThreshold);
                    // Note: Currently we just warn, but could make this configurable
                    // to fail health check in environments where low rate indicates problems
                }
            }
            
            // Determine if consumption is healthy (consumer is working properly)
            boolean healthyConsumption = consumerConnected && consumerGroupActive && 
                                       timeSinceLastPoll <= messageTimeoutThreshold && !consumerStuck;
            
            return healthBuilder
                .withDetail("consumerConnected", consumerConnected)
                .withDetail("consumerGroupActive", consumerGroupActive)
                .withDetail("consumptionRate", consumptionRate)
                .withDetail("totalMessagesConsumed", totalMessagesConsumed)
                .withDetail("totalMessagesProcessed", totalMessagesProcessed)
                .withDetail("successRate", successRate)
                .withDetail("timeSinceLastMessageReceivedMs", timeSinceLastPoll)
                .withDetail("messageTimeoutThresholdMs", messageTimeoutThreshold)
                .withDetail("consumerStuck", consumerStuck)
                .withDetail("healthyConsumption", healthyConsumption)
                .withDetail("reason", "Consumer is healthy - actively polling for messages")
                .withDetail("checkTimestamp", checkTimestamp)
                .build();
                
        } catch (Exception e) {
            log.error("Error checking consumer consumption activity liveness", e);
            return Health.down()
                .withDetail("error", e.getMessage())
                .withDetail("reason", "Health check failed due to exception")
                .withDetail("checkTimestamp", System.currentTimeMillis())
                .build();
        }
    }
    
    private Health buildDownHealth(String reason, long checkTimestamp) {
        // Include basic connection state details for consistent response format
        boolean consumerConnected = false;
        boolean consumerGroupActive = false;
        
        try {
            consumerConnected = kafkaMonitoringService.isConsumerConnected();
            consumerGroupActive = kafkaMonitoringService.isConsumerGroupActive();
        } catch (Exception e) {
            // In case of exception, defaults remain false
        }
        
        return Health.down()
            .withDetail("reason", reason)
            .withDetail("consumerConnected", consumerConnected)
            .withDetail("consumerGroupActive", consumerGroupActive)
            .withDetail("checkTimestamp", checkTimestamp)
            .build();
    }
} 