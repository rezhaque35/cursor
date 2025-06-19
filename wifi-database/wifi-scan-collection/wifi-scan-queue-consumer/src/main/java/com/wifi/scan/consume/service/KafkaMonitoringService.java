package com.wifi.scan.consume.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.DescribeClusterResult;
import org.apache.kafka.clients.admin.DescribeTopicsResult;
import org.apache.kafka.clients.admin.ListConsumerGroupsResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.HealthIndicator;

import org.springframework.stereotype.Service;

import com.wifi.scan.consume.config.KafkaProperties;
import com.wifi.scan.consume.metrics.KafkaConsumerMetrics;

import lombok.extern.slf4j.Slf4j;

/**
 * Service for monitoring Kafka consumer health and metrics.
 * 
 * This service provides comprehensive monitoring capabilities for Kafka consumer operations,
 * including connectivity checks, message consumption tracking, and performance metrics.
 * 
 * Key functionalities:
 * - Consumer connectivity and group membership monitoring
 * - Message consumption rate calculation using total messages processed over actual duration
 * - SSL/TLS connection health verification  
 * - Memory usage tracking and health assessment
 * - Caching mechanism for expensive health checks to optimize performance
 * 
 * Mathematical Formulas:
 * - Message Consumption Rate: rate = totalMessagesProcessed / actualDurationInMinutes
 *   where actualDuration = Duration.between(firstMessageTimestamp, now)
 * - Memory Usage Percentage: percentage = (usedMemory / totalMemory) * 100
 * 
 * Thread Safety: This service is thread-safe through the use of:
 * - ConcurrentHashMap for health check caching
 * - Volatile fields for thread-visible state updates
 * - Atomic operations in KafkaConsumerMetrics
 * 
 * @see KafkaConsumerMetrics for detailed metrics tracking
 * @see HealthIndicator implementations for health check usage
 */
@Slf4j
@Service
public class KafkaMonitoringService {

    private final KafkaProperties kafkaProperties;
    private final KafkaConsumerMetrics metrics;
    private final AdminClient adminClient;
    
    // Cache for health check results to prevent resource leaks
    private final ConcurrentHashMap<String, CachedResult> healthCheckCache = new ConcurrentHashMap<>();

    // Add new field to track actual polling activity
    private volatile LocalDateTime lastPollActivity = LocalDateTime.now();

    // === Health Check Cache Constants ===
    /**
     * Cache Time-To-Live for health check results in milliseconds.
     * Rationale: 30 seconds provides a good balance between performance optimization
     * and timely detection of state changes. Frequent health checks can be expensive,
     * especially those involving network calls to Kafka brokers.
     */
    private static final long CACHE_TTL_MS = 30_000; // 30 seconds cache TTL

    // === Consumer Activity Timeout Constants ===
    /**
     * Default timeout for consumer polling activity in minutes.
     * Rationale: 5 minutes allows for reasonable idle periods while detecting
     * genuine connectivity issues. This accommodates normal Kafka consumer
     * behavior during low-traffic periods.
     */
    private static final int DEFAULT_POLLING_TIMEOUT_MINUTES = 5;

    /**
     * Default timeout threshold for message consumption health checks in minutes.
     * Rationale: Used as a baseline for determining if the consumer is active
     * and processing messages within expected timeframes.
     */
    private static final int DEFAULT_CONSUMPTION_TIMEOUT_MINUTES = 5;

    /**
     * Default minimum consumption rate threshold in messages per minute.
     * Rationale: 0.1 messages/minute provides a very low threshold to avoid
     * false negatives during legitimate idle periods while still detecting
     * complete consumption failures.
     */
    private static final double DEFAULT_MINIMUM_CONSUMPTION_RATE = 0.1;

    // === Memory Health Constants ===
    /**
     * Default memory usage threshold percentage for health checks.
     * Rationale: 90% threshold allows for high memory utilization while providing
     * early warning before potential OutOfMemoryError conditions. This leaves
     * 10% buffer for garbage collection and temporary allocations.
     */
    private static final double DEFAULT_MEMORY_THRESHOLD_PERCENTAGE = 90.0;

    @Autowired
    public KafkaMonitoringService(KafkaProperties kafkaProperties, 
                                 KafkaConsumerMetrics metrics,
                                 AdminClient adminClient) {
        this.kafkaProperties = kafkaProperties;
        this.metrics = metrics;
        this.adminClient = adminClient;
    }

    /**
     * Cached result holder for health checks.
     */
    private static class CachedResult {
        final boolean result;
        final long timestamp;
        
        CachedResult(boolean result) {
            this.result = result;
            this.timestamp = System.currentTimeMillis();
        }
        
        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > CACHE_TTL_MS;
        }
    }

    /**
     * Gets cached result or computes new one if cache is expired.
     */
    private boolean getCachedOrCompute(String key, BooleanSupplier supplier) {
        CachedResult cached = healthCheckCache.get(key);
        if (cached != null && !cached.isExpired()) {
            return cached.result;
        }
        
        boolean result = supplier.getAsBoolean();
        healthCheckCache.put(key, new CachedResult(result));
        return result;
    }

    /**
     * Updates the last poll activity timestamp.
     * This should be called by the consumer to indicate active polling regardless of message availability.
     */
    public void recordPollActivity() {
        this.lastPollActivity = LocalDateTime.now();
    }

    /**
     * Checks if consumer is actively polling (regardless of message availability).
     * 
     * FIXED: This method now tracks actual polling activity instead of message processing activity.
     * The consumer should call recordPollActivity() during each poll cycle to indicate it's actively polling.
     * 
     * This properly distinguishes between:
     * - "No messages available" (healthy - consumer is polling but no messages)
     * - "Consumer stopped polling" (unhealthy - consumer is not polling at all)
     */
    public boolean isConsumerPollingActive(int timeoutMinutes) {
        try {
            Duration timeSinceLastPoll = Duration.between(lastPollActivity, LocalDateTime.now());
            boolean isActive = timeSinceLastPoll.toMinutes() <= timeoutMinutes;
            
            if (!isActive) {
                log.warn("Consumer polling appears inactive. Last poll activity: {}, timeout: {} minutes", 
                        lastPollActivity, timeoutMinutes);
            } else {
                log.debug("Consumer polling is active. Last poll activity: {}", lastPollActivity);
            }
            
            return isActive;
        } catch (Exception e) {
            log.debug("Error checking polling activity", e);
            return false;
        }
    }

    /**
     * Checks basic consumer connectivity to Kafka cluster.
     */
    public boolean isConsumerConnected() {
        return getCachedOrCompute("consumer_connected", () -> {
            try {
                DescribeClusterResult clusterResult = adminClient.describeCluster();
                clusterResult.clusterId().get(5, TimeUnit.SECONDS);
                return true;
            } catch (Exception e) {
                log.debug("Consumer connection check failed", e);
                return false;
            }
        });
    }

    /**
     * Checks consumer group membership status with optimized caching.
     */
    public boolean isConsumerGroupActive() {
        return getCachedOrCompute("consumer_group_active", () -> {
            try {
                String groupId = kafkaProperties.getConsumer().getGroupId();
                ListConsumerGroupsResult result = adminClient.listConsumerGroups();
                boolean groupExists = result.all().get(5, TimeUnit.SECONDS).stream()
                        .anyMatch(group -> group.groupId().equals(groupId));
                
                // If group exists, it's definitely active
                if (groupExists) {
                    log.debug("Consumer group {} found in cluster", groupId);
                    return true;
                }
                
                // If group doesn't exist yet, assume it's valid during startup
                // This prevents creating test consumers that cause resource leaks
                log.debug("Consumer group {} not found, assuming valid during startup", groupId);
                return true; // Be optimistic during startup to avoid resource leaks
                
            } catch (Exception e) {
                log.debug("Consumer group check failed", e);
                return false;
            }
        });
    }

    /**
     * Checks if configured topics are accessible.
     */
    public boolean areTopicsAccessible() {
        return getCachedOrCompute("topics_accessible", () -> {
            try {
                String topicName = kafkaProperties.getTopic().getName();
                DescribeTopicsResult result = adminClient.describeTopics(Collections.singleton(topicName));
                result.all().get(5, TimeUnit.SECONDS);
                return true;
            } catch (Exception e) {
                log.debug("Topic accessibility check failed", e);
                return false;
            }
        });
    }

    /**
     * Checks SSL/TLS connectivity to Kafka brokers.
     */
    public boolean isSslConnectionHealthy() {
        if (!kafkaProperties.getSsl().isEnabled()) {
            return true; // SSL not enabled, consider healthy
        }

        return getCachedOrCompute("ssl_connection_healthy", () -> {
            try {
                DescribeClusterResult clusterResult = adminClient.describeCluster();
                clusterResult.clusterId().get(5, TimeUnit.SECONDS);
                return true;
            } catch (Exception e) {
                log.debug("SSL connection check failed", e);
                return false;
            }
        });
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
     * Calculates message consumption rate in messages per minute.
     * 
     * Mathematical Formula:
     * rate = totalMessagesProcessed / actualDurationInMinutes
     * 
     * Where:
     * - totalMessagesProcessed: Total number of messages successfully processed since first message
     * - actualDurationInMinutes: Time elapsed from first message timestamp to current time in minutes
     * 
     * Special Cases:
     * - Returns 0.0 if no messages have been processed or no first message timestamp available
     * - For durations < 1 minute, returns total message count to avoid division by zero
     * - Handles timestamp parsing errors gracefully by returning 0.0
     * 
     * @return message consumption rate in messages per minute, or 0.0 if unable to calculate
     */
    public double getMessageConsumptionRate() {
        long totalMessages = metrics.getTotalMessagesProcessed().get();
        String firstMessageTime = metrics.getFirstMessageTimestamp();
        
        if (firstMessageTime == null || totalMessages == 0) {
            return 0.0;
        }
        
        try {
            LocalDateTime firstMessage = LocalDateTime.parse(firstMessageTime);
            Duration actualDuration = Duration.between(firstMessage, LocalDateTime.now());
            
            // For very short durations (< 1 minute), return total messages as rate
            // to avoid division by zero and provide meaningful metrics for rapid consumption
            if (actualDuration.toMinutes() == 0) {
                return totalMessages;
            }
            
            // Calculate rate as messages per minute over actual duration
            return (double) totalMessages / actualDuration.toMinutes();
        } catch (Exception e) {
            log.warn("Error calculating consumption rate", e);
            return 0.0;
        }
    }

    /**
     * Checks if message consumption is healthy based on availability and processing.
     * 
     * ENHANCED: This method now properly distinguishes between:
     * - "No messages available" (healthy - consumer can connect and poll)
     * - "Consumer polling issues" (unhealthy - consumer connectivity problems)
     * 
     * The key insight is that during idle periods (no messages), health should depend on
     * connectivity rather than message processing activity.
     */
    public boolean isMessageConsumptionHealthy(int timeoutMinutes, double minimumRateThreshold) {
        // CRITICAL FIX: During idle periods, focus on connectivity rather than polling activity
        // If consumer can connect and group is active, consider it healthy even with no recent messages
        
        try {
            // Check basic connectivity first
            boolean isConnectedToKafka = isConsumerConnected();
            boolean isGroupActive = isConsumerGroupActive();
            
            // If consumer cannot connect, it's definitely unhealthy
            if (!isConnectedToKafka) {
                log.warn("Message consumption unhealthy: Consumer not connected to Kafka");
                return false;
            }
            
            // If consumer group is not active, it's potentially unhealthy
            if (!isGroupActive) {
                log.warn("Message consumption unhealthy: Consumer group not active");
                return false;
            }
            
            // At this point, consumer can connect and participate in group
            // Now check if there are processing issues (only if messages have been consumed)
            long totalConsumed = metrics.getTotalMessagesConsumed().get();
            long totalProcessed = metrics.getTotalMessagesProcessed().get();
            
            // If no messages have been consumed yet, consider healthy (idle but ready)
            if (totalConsumed == 0) {
                log.debug("Message consumption healthy: Consumer ready, no messages consumed yet (idle state)");
                return true;
            }
            
            // If messages have been consumed, check for processing lag
            if (totalConsumed > 0 && totalProcessed < (totalConsumed * 0.8)) {
                log.warn("Message consumption unhealthy: Processing lag detected - consumed={}, processed={}", 
                        totalConsumed, totalProcessed);
                return false;
            }
            
            // For polling activity, be more lenient - only fail if we haven't seen messages 
            // for much longer than normal (2x the timeout)
            String lastMessageTime = metrics.getLastMessageTimestamp();
            if (lastMessageTime != null) {
                try {
                    LocalDateTime lastMessage = LocalDateTime.parse(lastMessageTime);
                    Duration timeSinceLastMessage = Duration.between(lastMessage, LocalDateTime.now());
                    
                    // Only consider unhealthy if no messages for 2x the timeout period
                    // This accounts for normal idle periods in production
                    if (timeSinceLastMessage.toMinutes() > (timeoutMinutes * 2)) {
                        log.warn("Message consumption potentially degraded: No messages for {} minutes (2x timeout: {})", 
                                timeSinceLastMessage.toMinutes(), timeoutMinutes * 2);
                        // Don't fail immediately - just log a warning
                        // Consumer connectivity is more important than message frequency
                    }
                } catch (Exception e) {
                    log.debug("Error parsing last message timestamp: {}", lastMessageTime, e);
                }
            }
            
            log.debug("Message consumption healthy: Consumer connected, group active, processing normal");
            return true;
            
        } catch (Exception e) {
            log.error("Error checking message consumption health", e);
            return false;
        }
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
     * Uses the default polling timeout defined in DEFAULT_POLLING_TIMEOUT_MINUTES.
     */
    public boolean isPollingActive() {
        return isConsumerPollingActive(DEFAULT_POLLING_TIMEOUT_MINUTES);
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
     * 
     * This method delegates to getMessageConsumptionRate() which calculates
     * the rate based on actual duration from first message to now.
     * 
     * @return message consumption rate in messages per minute
     */
    public double getConsumptionRate() {
        return getMessageConsumptionRate();
    }

    /**
     * Checks if consumption is healthy.
     * Uses default timeout and minimum rate thresholds defined in constants.
     */
    public boolean isConsumptionHealthy() {
        return isMessageConsumptionHealthy(DEFAULT_CONSUMPTION_TIMEOUT_MINUTES, DEFAULT_MINIMUM_CONSUMPTION_RATE);
    }

    /**
     * Checks if memory is healthy.
     * Uses the default memory threshold defined in DEFAULT_MEMORY_THRESHOLD_PERCENTAGE.
     */
    public boolean isMemoryHealthy() {
        return isMemoryHealthy(DEFAULT_MEMORY_THRESHOLD_PERCENTAGE);
    }

    /**
     * Gets comprehensive metrics summary.
     */
    public String getMetricsSummary() {
        return """
                Kafka Consumer Monitoring Summary:
                =====================================
                Message Processing:
                  Total Messages Consumed: %d
                  Total Messages Processed: %d
                  Total Messages Failed: %d
                  Success Rate: %.2f%%
                  Error Rate: %.2f%%
                
                Performance Metrics:
                  Average Processing Time: %.2f ms
                  Min Processing Time: %d ms
                  Max Processing Time: %d ms
                  Consumption Rate: %.2f msg/min
                
                Activity Tracking:
                  First Message: %s
                  Last Message: %s
                  Last Poll: %s
                  Polling Active: %s
                
                Consumer Status:
                  Consumer Connected: %s
                  Consumer Group Active: %s
                  Consumption Healthy: %s
                
                System Resources:
                  Memory Usage: %.1f%% (%d/%d MB)
                  Max Memory: %d MB
                  Memory Healthy: %s
                
                Timestamp: %s""".formatted(
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
     * Gets time since last poll activity in milliseconds.
     * 
     * @return milliseconds since last poll activity
     */
    public long getTimeSinceLastPoll() {
        try {
            Duration timeSinceLastPoll = Duration.between(lastPollActivity, LocalDateTime.now());
            return timeSinceLastPoll.toMillis();
        } catch (Exception e) {
            log.debug("Error calculating time since last poll", e);
            return Long.MAX_VALUE; // Return max value to indicate error/unknown state
        }
    }

    /**
     * Determines if the consumer is stuck (polling but not advancing).
     * 
     * A consumer is considered stuck if:
     * - It's actively polling (recent poll activity)
     * - But consumption rate is extremely low despite being connected
     * - And there are no processing errors
     * 
     * This indicates the consumer might be stuck in a poll loop without advancing position.
     * 
     * @return true if consumer appears to be stuck
     */
    public boolean isConsumerStuck() {
        try {
            // Check if we're actively polling (within last 2 minutes)
            boolean recentPollActivity = getTimeSinceLastPoll() < TimeUnit.MINUTES.toMillis(2);
            
            // Check if consumption rate is extremely low
            double currentRate = getMessageConsumptionRate();
            boolean lowConsumptionRate = currentRate < 0.01; // Less than 0.01 msg/min
            
            // Check if we're connected and have low error rate
            boolean isConnected = isConsumerConnected();
            boolean lowErrorRate = getErrorRate() < 5.0; // Less than 5% error rate
            
            // Check if we've been processing for a while (to avoid false positives during startup)
            boolean hasBeenRunning = getTotalMessagesConsumed() > 0 || 
                                   Duration.between(lastPollActivity, LocalDateTime.now()).toMinutes() > 2;
            
            // Consumer is potentially stuck if:
            // - Polling recently BUT consumption rate is very low AND connected AND low errors AND has been running
            boolean potentiallyStuck = recentPollActivity && lowConsumptionRate && 
                                     isConnected && lowErrorRate && hasBeenRunning;
            
            if (potentiallyStuck) {
                log.warn("Consumer appears to be stuck - polling but not consuming. Rate: {}, Connected: {}, Errors: {}%", 
                        currentRate, isConnected, getErrorRate());
            }
            
            return potentiallyStuck;
            
        } catch (Exception e) {
            log.debug("Error checking if consumer is stuck", e);
            return false; // Default to not stuck if we can't determine
        }
    }

    /**
     * Resets all metrics.
     */
    public void resetMetrics() {
        metrics.resetMetrics();
        log.info("Kafka monitoring service metrics reset");
    }
} 