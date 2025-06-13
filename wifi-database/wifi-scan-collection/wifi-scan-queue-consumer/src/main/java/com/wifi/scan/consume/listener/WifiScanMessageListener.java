package com.wifi.scan.consume.listener;

import com.wifi.scan.consume.metrics.KafkaConsumerMetrics;
import com.wifi.scan.consume.service.KafkaMonitoringService;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Kafka message listener for processing WiFi scan data messages.
 * Consumes messages from the configured Kafka topic and processes them.
 */
@Slf4j
@Component
public class WifiScanMessageListener {

    @Getter
    private final AtomicLong processedMessageCount = new AtomicLong(0);

    @Getter
    private volatile long lastProcessingTimeMs = 0;

    private Logger logger = log;

    @Autowired
    private KafkaConsumerMetrics metrics;

    @Autowired
    private KafkaMonitoringService monitoringService;

    /**
     * Kafka listener method for processing WiFi scan messages.
     * 
     * @param record the consumed Kafka record
     * @param acknowledgment manual acknowledgment for offset management
     */
    @KafkaListener(topics = "${kafka.topic.name}", groupId = "${kafka.consumer.group-id}")
    public void listen(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) {
        long startTime = System.currentTimeMillis();
        
        try {
            // Record polling activity - indicates consumer is actively polling and receiving messages
            monitoringService.recordPollActivity();
            
            // Record message consumption
            metrics.recordMessageConsumed();
            
            logger.info("Received WiFi scan message from Kafka");
            logger.info("Topic: {}", record.topic());
            logger.info("Partition: {}", record.partition());
            logger.info("Offset: {}", record.offset());
            logger.info("Key: {}", record.key());
            logger.info("Value: {}", record.value());
            
            // Extract and log metadata
            String metadata = extractMessageMetadata(record);
            logger.debug("Message metadata: {}", metadata);
            
            // Validate message format
            boolean isValid = validateMessageFormat(record.value());
            if (!isValid) {
                logger.warn("Invalid message format received: {}", record.value());
            }
            
            // Process the message (basic logging for now)
            processWifiScanMessage(record);
            
            // Manual acknowledgment (enable-auto-commit: false in config)
            if (acknowledgment != null) {
                acknowledgment.acknowledge();
                logger.debug("Message acknowledged successfully");
            } else {
                logger.warn("Acknowledgment is null - check Kafka listener configuration");
            }
            
            // Update metrics
            processedMessageCount.incrementAndGet();
            lastProcessingTimeMs = System.currentTimeMillis() - startTime;
            metrics.recordMessageProcessed(lastProcessingTimeMs);
            
            logger.info("Message processed successfully");
            logger.debug("Processing time: {} ms", lastProcessingTimeMs);
            
        } catch (Exception e) {
            logger.error("Error processing WiFi scan message from topic: {}, partition: {}, offset: {}", 
                    record.topic(), record.partition(), record.offset(), e);
            
            // Update metrics even for failed messages
            lastProcessingTimeMs = System.currentTimeMillis() - startTime;
            metrics.recordMessageFailed();
            
            // In production, you might want to send to a dead letter topic
            // For now, we'll acknowledge the message to avoid reprocessing
            if (acknowledgment != null) {
                try {
                    acknowledgment.acknowledge();
                } catch (Exception ackException) {
                    logger.error("Failed to acknowledge message after processing error", ackException);
                }
            }
            
            // Don't rethrow the exception to prevent container from stopping
            // The error is already logged and metrics updated
        }
    }

    /**
     * Validates the format of the received message.
     * 
     * @param messageValue the message content to validate
     * @return true if the message format is valid, false otherwise
     */
    public boolean validateMessageFormat(String messageValue) {
        if (messageValue == null || messageValue.trim().isEmpty()) {
            logger.warn("Received null or empty message");
            return false;
        }
        
        try {
            // Basic JSON validation - check if it starts and ends with braces
            String trimmed = messageValue.trim();
            if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
                logger.debug("Message appears to be valid JSON format");
                return true;
            } else {
                logger.warn("Message does not appear to be JSON format: {}", messageValue);
                return false;
            }
        } catch (Exception e) {
            logger.error("Error validating message format", e);
            return false;
        }
    }

    /**
     * Extracts metadata from the consumer record.
     * 
     * @param record the Kafka consumer record
     * @return formatted metadata string
     */
    public String extractMessageMetadata(ConsumerRecord<String, String> record) {
        return String.format("Topic=%s, Partition=%d, Offset=%d, Key=%s, Timestamp=%d",
                record.topic(),
                record.partition(),
                record.offset(),
                record.key(),
                record.timestamp());
    }

    /**
     * Processes the WiFi scan message content.
     * 
     * @param record the Kafka consumer record containing the WiFi scan data
     */
    private void processWifiScanMessage(ConsumerRecord<String, String> record) {
        logger.debug("Processing WiFi scan data from message");
        
        try {
            String messageValue = record.value();
            
            // For now, just log the message content
            // In future phases, this will be enhanced to:
            // 1. Parse the JSON message
            // 2. Validate the WiFi scan data structure
            // 3. Transform the data if needed
            // 4. Send to Kinesis Data Firehose
            
            logger.debug("WiFi scan message content length: {} characters", 
                    messageValue != null ? messageValue.length() : 0);
            
            // Simulate processing time for realistic behavior
            Thread.sleep(10); // 10ms processing simulation
            
        } catch (Exception e) {
            logger.error("Error during WiFi scan message processing", e);
            throw new RuntimeException("Failed to process WiFi scan message", e);
        }
    }

    /**
     * Sets a custom logger for testing purposes.
     * 
     * @param logger the logger to use
     */
    public void setLogger(Logger logger) {
        this.logger = logger;
    }

    /**
     * Gets the current processed message count.
     * 
     * @return the number of processed messages
     */
    public long getProcessedMessageCount() {
        return processedMessageCount.get();
    }

    /**
     * Gets the last processing time in milliseconds.
     * 
     * @return the last processing time
     */
    public long getLastProcessingTimeMs() {
        return lastProcessingTimeMs;
    }
} 