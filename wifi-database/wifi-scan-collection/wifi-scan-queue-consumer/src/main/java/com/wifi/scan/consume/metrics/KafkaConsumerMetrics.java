package com.wifi.scan.consume.metrics;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Metrics collection component for Kafka consumer operations.
 * Tracks message consumption statistics and performance metrics.
 */
@Slf4j
@Component("customKafkaConsumerMetrics")
@Getter
public class KafkaConsumerMetrics {

    private final AtomicLong totalMessagesConsumed = new AtomicLong(0);
    private final AtomicLong totalMessagesProcessed = new AtomicLong(0);
    private final AtomicLong totalMessagesFailed = new AtomicLong(0);
    private final AtomicLong totalProcessingTimeMs = new AtomicLong(0);
    
    private volatile LocalDateTime lastMessageTimestamp;
    private volatile LocalDateTime firstMessageTimestamp;
    private volatile long minProcessingTimeMs = Long.MAX_VALUE;
    private volatile long maxProcessingTimeMs = 0;

    /**
     * Records a successfully consumed message.
     */
    public void recordMessageConsumed() {
        totalMessagesConsumed.incrementAndGet();
        updateTimestamp();
        log.debug("Message consumed. Total: {}", totalMessagesConsumed.get());
    }

    /**
     * Records a successfully processed message with processing time.
     * 
     * @param processingTimeMs the time taken to process the message in milliseconds
     */
    public void recordMessageProcessed(long processingTimeMs) {
        totalMessagesProcessed.incrementAndGet();
        totalProcessingTimeMs.addAndGet(processingTimeMs);
        updateProcessingTimeStats(processingTimeMs);
        log.debug("Message processed in {} ms. Total processed: {}", processingTimeMs, totalMessagesProcessed.get());
    }

    /**
     * Records a failed message processing attempt.
     */
    public void recordMessageFailed() {
        totalMessagesFailed.incrementAndGet();
        log.debug("Message processing failed. Total failures: {}", totalMessagesFailed.get());
    }

    /**
     * Gets the current success rate as a percentage.
     * 
     * @return success rate percentage (0-100)
     */
    public double getSuccessRate() {
        long total = totalMessagesConsumed.get();
        if (total == 0) {
            return 100.0;
        }
        
        long successful = totalMessagesProcessed.get();
        return (successful * 100.0) / total;
    }

    /**
     * Gets the current error rate as a percentage.
     * 
     * @return error rate percentage (0-100)
     */
    public double getErrorRate() {
        long total = totalMessagesConsumed.get();
        if (total == 0) {
            return 0.0;
        }
        
        long failed = totalMessagesFailed.get();
        return (failed * 100.0) / total;
    }

    /**
     * Gets the average processing time in milliseconds.
     * 
     * @return average processing time or 0 if no messages processed
     */
    public double getAverageProcessingTimeMs() {
        long totalProcessed = totalMessagesProcessed.get();
        if (totalProcessed == 0) {
            return 0.0;
        }
        
        return (double) totalProcessingTimeMs.get() / totalProcessed;
    }

    /**
     * Gets the minimum processing time recorded.
     * 
     * @return minimum processing time in milliseconds
     */
    public long getMinProcessingTimeMs() {
        return minProcessingTimeMs == Long.MAX_VALUE ? 0 : minProcessingTimeMs;
    }

    /**
     * Gets the maximum processing time recorded.
     * 
     * @return maximum processing time in milliseconds
     */
    public long getMaxProcessingTimeMs() {
        return maxProcessingTimeMs;
    }

    /**
     * Gets the timestamp of the last processed message.
     * 
     * @return formatted timestamp string or null if no messages processed
     */
    public String getLastMessageTimestamp() {
        if (lastMessageTimestamp == null) {
            return null;
        }
        return lastMessageTimestamp.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }

    /**
     * Gets the timestamp of the first processed message.
     * 
     * @return formatted timestamp string or null if no messages processed
     */
    public String getFirstMessageTimestamp() {
        if (firstMessageTimestamp == null) {
            return null;
        }
        return firstMessageTimestamp.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }

    /**
     * Resets all metrics to their initial state.
     * Useful for testing or metrics refresh.
     */
    public void resetMetrics() {
        totalMessagesConsumed.set(0);
        totalMessagesProcessed.set(0);
        totalMessagesFailed.set(0);
        totalProcessingTimeMs.set(0);
        lastMessageTimestamp = null;
        firstMessageTimestamp = null;
        minProcessingTimeMs = Long.MAX_VALUE;
        maxProcessingTimeMs = 0;
        log.info("Kafka consumer metrics reset");
    }

    /**
     * Updates the message timestamp tracking.
     */
    private void updateTimestamp() {
        LocalDateTime now = LocalDateTime.now();
        this.lastMessageTimestamp = now;
        
        if (this.firstMessageTimestamp == null) {
            this.firstMessageTimestamp = now;
        }
    }

    /**
     * Updates processing time statistics.
     * 
     * @param processingTimeMs the processing time to record
     */
    private void updateProcessingTimeStats(long processingTimeMs) {
        // Update minimum processing time
        long currentMin = this.minProcessingTimeMs;
        while (processingTimeMs < currentMin) {
            if (compareAndSetMinProcessingTime(currentMin, processingTimeMs)) {
                break;
            }
            currentMin = this.minProcessingTimeMs;
        }
        
        // Update maximum processing time
        long currentMax = this.maxProcessingTimeMs;
        while (processingTimeMs > currentMax) {
            if (compareAndSetMaxProcessingTime(currentMax, processingTimeMs)) {
                break;
            }
            currentMax = this.maxProcessingTimeMs;
        }
    }

    /**
     * Atomic compare and set for minimum processing time.
     */
    private synchronized boolean compareAndSetMinProcessingTime(long expected, long newValue) {
        if (this.minProcessingTimeMs == expected) {
            this.minProcessingTimeMs = newValue;
            return true;
        }
        return false;
    }

    /**
     * Atomic compare and set for maximum processing time.
     */
    private synchronized boolean compareAndSetMaxProcessingTime(long expected, long newValue) {
        if (this.maxProcessingTimeMs == expected) {
            this.maxProcessingTimeMs = newValue;
            return true;
        }
        return false;
    }

    /**
     * Gets a summary of all metrics as a formatted string.
     * 
     * @return metrics summary
     */
    public String getMetricsSummary() {
        return String.format(
                "Kafka Consumer Metrics Summary:\n" +
                "  Total Messages Consumed: %d\n" +
                "  Total Messages Processed: %d\n" +
                "  Total Messages Failed: %d\n" +
                "  Success Rate: %.2f%%\n" +
                "  Error Rate: %.2f%%\n" +
                "  Average Processing Time: %.2f ms\n" +
                "  Min Processing Time: %d ms\n" +
                "  Max Processing Time: %d ms\n" +
                "  First Message: %s\n" +
                "  Last Message: %s",
                totalMessagesConsumed.get(),
                totalMessagesProcessed.get(),
                totalMessagesFailed.get(),
                getSuccessRate(),
                getErrorRate(),
                getAverageProcessingTimeMs(),
                getMinProcessingTimeMs(),
                getMaxProcessingTimeMs(),
                getFirstMessageTimestamp(),
                getLastMessageTimestamp()
        );
    }
} 