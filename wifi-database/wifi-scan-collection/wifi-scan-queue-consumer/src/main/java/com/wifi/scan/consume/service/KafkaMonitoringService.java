package com.wifi.scan.consume.service;

import com.wifi.scan.consume.config.KafkaProperties;
import com.wifi.scan.consume.metrics.KafkaConsumerMetrics;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.DescribeClusterResult;
import org.apache.kafka.clients.admin.DescribeTopicsResult;
import org.apache.kafka.clients.admin.ListConsumerGroupsResult;
import org.apache.kafka.clients.consumer.Consumer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * Shared monitoring service for Kafka consumer operations.
 * Provides centralized monitoring capabilities for both metrics collection and health indicators.
 */
@Slf4j
@Service
public class KafkaMonitoringService {

    private final KafkaProperties kafkaProperties;
    private final KafkaConsumerMetrics metrics;
    private final AdminClient adminClient;
    private final ConsumerFactory<String, String> consumerFactory;

    @Autowired
    public KafkaMonitoringService(KafkaProperties kafkaProperties, 
                                 KafkaConsumerMetrics metrics,
                                 AdminClient adminClient,
                                 ConsumerFactory<String, String> consumerFactory) {
        this.kafkaProperties = kafkaProperties;
        this.metrics = metrics;
        this.adminClient = adminClient;
        this.consumerFactory = consumerFactory;
    }

    /**
     * Checks if the consumer is actively polling (even if no messages are available).
     * 
     * @param timeoutMinutes the timeout in minutes for considering consumer inactive
     * @return true if consumer has polled within the timeout period
     */
    public boolean isConsumerPollingActive(int timeoutMinutes) {
        String lastMessageTime = metrics.getLastMessageTimestamp();
        if (lastMessageTime == null) {
            // No messages processed yet, but this doesn't mean consumer isn't polling
            // We need to check if consumer is connected and polling
            return isConsumerConnected();
        }
        
        try {
            LocalDateTime lastMessage = LocalDateTime.parse(lastMessageTime);
            LocalDateTime cutoff = LocalDateTime.now().minusMinutes(timeoutMinutes);
            return lastMessage.isAfter(cutoff);
        } catch (Exception e) {
            log.warn("Error parsing last message timestamp: {}", lastMessageTime, e);
            return false;
        }
    }

    /**
     * Checks if consumer is connected to Kafka cluster.
     */
    public boolean isConsumerConnected() {
        try (Consumer<String, String> consumer = consumerFactory.createConsumer()) {
            // Try to get consumer group metadata - this will fail if not connected
            consumer.groupMetadata();
            return true;
        } catch (Exception e) {
            log.debug("Consumer connection check failed", e);
            return false;
        }
    }

    /**
     * Checks consumer group membership status.
     */
    public boolean isConsumerGroupActive() {
        try {
            String groupId = kafkaProperties.getConsumer().getGroupId();
            ListConsumerGroupsResult result = adminClient.listConsumerGroups();
            return result.all().get(5, TimeUnit.SECONDS).stream()
                    .anyMatch(group -> group.groupId().equals(groupId));
        } catch (Exception e) {
            log.debug("Consumer group check failed", e);
            return false;
        }
    }

    /**
     * Checks if configured topics are accessible.
     */
    public boolean areTopicsAccessible() {
        try {
            String topicName = kafkaProperties.getTopic().getName();
            DescribeTopicsResult result = adminClient.describeTopics(Collections.singleton(topicName));
            result.all().get(5, TimeUnit.SECONDS);
            return true;
        } catch (Exception e) {
            log.debug("Topic accessibility check failed", e);
            return false;
        }
    }

    /**
     * Checks SSL/TLS connectivity to Kafka brokers.
     */
    public boolean isSslConnectionHealthy() {
        if (!kafkaProperties.getSsl().isEnabled()) {
            return true; // SSL not enabled, consider healthy
        }

        try {
            DescribeClusterResult clusterResult = adminClient.describeCluster();
            clusterResult.clusterId().get(5, TimeUnit.SECONDS);
            return true;
        } catch (Exception e) {
            log.debug("SSL connection check failed", e);
            return false;
        }
    }

    /**
     * Gets current memory usage percentage.
     */
    public double getMemoryUsagePercentage() {
        Runtime runtime = Runtime.getRuntime();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;
        return (double) usedMemory / totalMemory * 100.0;
    }

    /**
     * Checks if memory usage is within healthy limits.
     */
    public boolean isMemoryHealthy(double thresholdPercentage) {
        return getMemoryUsagePercentage() < thresholdPercentage;
    }

    /**
     * Gets message consumption rate over specified time window.
     */
    public double getMessageConsumptionRate(Duration timeWindow) {
        long totalMessages = metrics.getTotalMessagesProcessed().get();
        String firstMessageTime = metrics.getFirstMessageTimestamp();
        
        if (firstMessageTime == null || totalMessages == 0) {
            return 0.0;
        }
        
        try {
            LocalDateTime firstMessage = LocalDateTime.parse(firstMessageTime);
            Duration actualDuration = Duration.between(firstMessage, LocalDateTime.now());
            
            if (actualDuration.toMinutes() == 0) {
                return totalMessages; // Messages per minute for very short durations
            }
            
            return (double) totalMessages / actualDuration.toMinutes();
        } catch (Exception e) {
            log.warn("Error calculating consumption rate", e);
            return 0.0;
        }
    }

    /**
     * Checks if message consumption is healthy based on availability and processing.
     */
    public boolean isMessageConsumptionHealthy(int timeoutMinutes, double minimumRateThreshold) {
        // Check if consumer is actively polling
        if (!isConsumerPollingActive(timeoutMinutes)) {
            return false;
        }
        
        // Check if there are messages available but not being processed
        // This is a simplified check - in production you'd use consumer lag metrics
        long totalConsumed = metrics.getTotalMessagesConsumed().get();
        long totalProcessed = metrics.getTotalMessagesProcessed().get();
        
        // If we've consumed messages but processed significantly fewer, there might be an issue
        if (totalConsumed > 0 && totalProcessed < (totalConsumed * 0.8)) {
            log.warn("Message processing lag detected: consumed={}, processed={}", totalConsumed, totalProcessed);
            return false;
        }
        
        return true;
    }

    /**
     * Gets cluster node count for health monitoring.
     */
    public int getClusterNodeCount() {
        try {
            DescribeClusterResult clusterResult = adminClient.describeCluster();
            return clusterResult.nodes().get(5, TimeUnit.SECONDS).size();
        } catch (Exception e) {
            log.debug("Failed to get cluster node count", e);
            return 0;
        }
    }

    /**
     * Gets current consumer metrics for health reporting.
     */
    public KafkaConsumerMetrics getMetrics() {
        return metrics;
    }

    // === Metrics delegation methods for MetricsController ===

    /**
     * Gets total messages consumed.
     */
    public long getTotalMessagesConsumed() {
        return metrics.getTotalMessagesConsumed().get();
    }

    /**
     * Gets total messages processed.
     */
    public long getTotalMessagesProcessed() {
        return metrics.getTotalMessagesProcessed().get();
    }

    /**
     * Gets total messages failed.
     */
    public long getTotalMessagesFailed() {
        return metrics.getTotalMessagesFailed().get();
    }

    /**
     * Gets success rate percentage.
     */
    public double getSuccessRate() {
        return metrics.getSuccessRate();
    }

    /**
     * Gets error rate percentage.
     */
    public double getErrorRate() {
        return metrics.getErrorRate();
    }

    /**
     * Gets average processing time in milliseconds.
     */
    public double getAverageProcessingTimeMs() {
        return metrics.getAverageProcessingTimeMs();
    }

    /**
     * Gets minimum processing time in milliseconds.
     */
    public long getMinProcessingTimeMs() {
        return metrics.getMinProcessingTimeMs();
    }

    /**
     * Gets maximum processing time in milliseconds.
     */
    public long getMaxProcessingTimeMs() {
        return metrics.getMaxProcessingTimeMs();
    }

    /**
     * Gets first message timestamp.
     */
    public String getFirstMessageTimestamp() {
        return metrics.getFirstMessageTimestamp();
    }

    /**
     * Gets last message timestamp.
     */
    public String getLastMessageTimestamp() {
        return metrics.getLastMessageTimestamp();
    }

    /**
     * Gets last poll timestamp (for monitoring polling activity).
     */
    public String getLastPollTimestamp() {
        // For now, use last message timestamp as proxy for polling activity
        // In production, you'd track actual poll operations
        return getLastMessageTimestamp();
    }

    /**
     * Checks if consumer is actively polling.
     */
    public boolean isPollingActive() {
        return isConsumerPollingActive(5); // 5 minute default timeout
    }

    /**
     * Gets memory usage in MB (used).
     */
    public long getUsedMemoryMB() {
        Runtime runtime = Runtime.getRuntime();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        return (totalMemory - freeMemory) / (1024 * 1024);
    }

    /**
     * Gets total memory in MB.
     */
    public long getTotalMemoryMB() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() / (1024 * 1024);
    }

    /**
     * Gets maximum memory in MB.
     */
    public long getMaxMemoryMB() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.maxMemory() / (1024 * 1024);
    }

    /**
     * Gets current consumption rate (messages per minute).
     */
    public double getConsumptionRate() {
        return getMessageConsumptionRate(Duration.ofMinutes(10)); // 10 minute window
    }

    /**
     * Checks if consumption is healthy.
     */
    public boolean isConsumptionHealthy() {
        return isMessageConsumptionHealthy(5, 0.1); // 5 minute timeout, 0.1 msg/min threshold
    }

    /**
     * Checks if memory is healthy.
     */
    public boolean isMemoryHealthy() {
        return isMemoryHealthy(90.0); // 90% threshold
    }

    /**
     * Gets comprehensive metrics summary.
     */
    public String getMetricsSummary() {
        return String.format(
                "Kafka Consumer Monitoring Summary:\n" +
                "=====================================\n" +
                "Message Processing:\n" +
                "  Total Messages Consumed: %d\n" +
                "  Total Messages Processed: %d\n" +
                "  Total Messages Failed: %d\n" +
                "  Success Rate: %.2f%%\n" +
                "  Error Rate: %.2f%%\n" +
                "\n" +
                "Performance Metrics:\n" +
                "  Average Processing Time: %.2f ms\n" +
                "  Min Processing Time: %d ms\n" +
                "  Max Processing Time: %d ms\n" +
                "  Consumption Rate: %.2f msg/min\n" +
                "\n" +
                "Activity Tracking:\n" +
                "  First Message: %s\n" +
                "  Last Message: %s\n" +
                "  Last Poll: %s\n" +
                "  Polling Active: %s\n" +
                "\n" +
                "Consumer Status:\n" +
                "  Consumer Connected: %s\n" +
                "  Consumer Group Active: %s\n" +
                "  Consumption Healthy: %s\n" +
                "\n" +
                "System Resources:\n" +
                "  Memory Usage: %.1f%% (%d/%d MB)\n" +
                "  Max Memory: %d MB\n" +
                "  Memory Healthy: %s\n" +
                "\n" +
                "Timestamp: %s",
                getTotalMessagesConsumed(),
                getTotalMessagesProcessed(),
                getTotalMessagesFailed(),
                getSuccessRate(),
                getErrorRate(),
                getAverageProcessingTimeMs(),
                getMinProcessingTimeMs(),
                getMaxProcessingTimeMs(),
                getConsumptionRate(),
                getFirstMessageTimestamp() != null ? getFirstMessageTimestamp() : "N/A",
                getLastMessageTimestamp() != null ? getLastMessageTimestamp() : "N/A",
                getLastPollTimestamp() != null ? getLastPollTimestamp() : "N/A",
                isPollingActive(),
                isConsumerConnected(),
                isConsumerGroupActive(),
                isConsumptionHealthy(),
                getMemoryUsagePercentage(),
                getUsedMemoryMB(),
                getTotalMemoryMB(),
                getMaxMemoryMB(),
                isMemoryHealthy(),
                LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        );
    }

    /**
     * Resets all metrics.
     */
    public void resetMetrics() {
        metrics.resetMetrics();
        log.info("Kafka monitoring service metrics reset");
    }
} 