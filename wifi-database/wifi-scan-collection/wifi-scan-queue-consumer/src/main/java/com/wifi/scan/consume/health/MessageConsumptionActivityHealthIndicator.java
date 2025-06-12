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
 * Health indicator for message consumption activity.
 * Monitors message processing rates, polling activity, and consumption trends.
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
            log.debug("Checking message consumption activity");
            
            boolean isConsumptionHealthy = kafkaMonitoringService.isMessageConsumptionHealthy(
                    config.getConsumptionTimeoutMinutes(), 
                    config.getMinimumConsumptionRate());
            
            boolean isPollingActive = kafkaMonitoringService.isConsumerPollingActive(
                    config.getConsumptionTimeoutMinutes());
            
            double consumptionRate = kafkaMonitoringService.getMessageConsumptionRate(Duration.ofMinutes(10));
            
            Health.Builder healthBuilder = isConsumptionHealthy && isPollingActive ? 
                    Health.up() : Health.down();
            
            if (!isPollingActive) {
                healthBuilder.withDetail("reason", "Consumer is not actively polling");
            } else if (!isConsumptionHealthy) {
                healthBuilder.withDetail("reason", "Message consumption is unhealthy");
            }
            
            return healthBuilder
                    .withDetail("consumptionHealthy", isConsumptionHealthy)
                    .withDetail("pollingActive", isPollingActive)
                    .withDetail("consumptionRate", consumptionRate)
                    .withDetail("totalMessagesConsumed", kafkaMonitoringService.getMetrics().getTotalMessagesConsumed().get())
                    .withDetail("totalMessagesProcessed", kafkaMonitoringService.getMetrics().getTotalMessagesProcessed().get())
                    .withDetail("successRate", kafkaMonitoringService.getMetrics().getSuccessRate())
                    .withDetail("checkTimestamp", System.currentTimeMillis())
                    .build();
            
        } catch (Exception e) {
            log.error("Error checking message consumption activity", e);
            return Health.down()
                    .withDetail("error", e.getMessage())
                    .withDetail("checkTimestamp", System.currentTimeMillis())
                    .build();
        }
    }
} 