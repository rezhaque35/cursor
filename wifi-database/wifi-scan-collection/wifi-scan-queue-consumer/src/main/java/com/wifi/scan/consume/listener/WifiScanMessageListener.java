package com.wifi.scan.consume.listener;

import java.util.concurrent.atomic.AtomicLong;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import com.wifi.scan.consume.metrics.KafkaConsumerMetrics;
import com.wifi.scan.consume.service.KafkaMonitoringService;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

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


    private KafkaConsumerMetrics metrics;
    private KafkaMonitoringService monitoringService;

    @Autowired
    public WifiScanMessageListener(KafkaConsumerMetrics metrics, KafkaMonitoringService monitoringService) {
        this.metrics = metrics;
        this.monitoringService = monitoringService;
    }

    /**
     * Kafka listener method for processing WiFi scan messages.
     * 
     * @param consumerRecord the consumed Kafka record
     * @param acknowledgment manual acknowledgment for offset management
     */
    @KafkaListener(topics = "${kafka.topic.name}", groupId = "${kafka.consumer.group-id}")
    public void listen(ConsumerRecord<String, String> consumerRecord, Acknowledgment acknowledgment) {
        long startTime = System.currentTimeMillis();

        try {
            // Record polling activity - indicates consumer is actively polling and
            // receiving messages
            monitoringService.recordPollActivity();

            // Record message consumption
            metrics.recordMessageConsumed();

            logInformation(consumerRecord);
            // Validate message format
            boolean isValid = validateMessageFormat(consumerRecord.value());
            if (!isValid) {
                log.warn("Invalid message format received: {}", consumerRecord.value());
            }

            // Process the message (basic logging for now)
            processWifiScanMessage(consumerRecord);

            // Manual acknowledgment (enable-auto-commit: false in config)
            if (acknowledgment != null) {
                acknowledgment.acknowledge();
                log.debug("Message acknowledged successfully");
            } else {
                log.warn("Acknowledgment is null - check Kafka listener configuration");
            }

            // Update metrics
            updateMetrics(startTime);

        } catch (Exception e) {
            handleProcessingException(consumerRecord, acknowledgment, startTime, e);
        }
    }

    private void handleProcessingException(ConsumerRecord<String, String> consumerRecord, Acknowledgment acknowledgment, long startTime,
            Exception e) {
        log.error("Error processing WiFi scan message from topic: {}, partition: {}, offset: {}",
                consumerRecord.topic(), consumerRecord.partition(), consumerRecord.offset(), e);

        // Update metrics even for failed messages
        lastProcessingTimeMs = System.currentTimeMillis() - startTime;
        metrics.recordMessageFailed();

        // In production, you might want to send to a dead letter topic
        // For now, we'll acknowledge the message to avoid reprocessing
        if (acknowledgment != null) {
            try {
                acknowledgment.acknowledge();
            } catch (Exception ackException) {
                log.error("Failed to acknowledge message after processing error", ackException);
            }
        }
    }

    private void updateMetrics(long startTime) {
        processedMessageCount.incrementAndGet();
        lastProcessingTimeMs = System.currentTimeMillis() - startTime;
        metrics.recordMessageProcessed(lastProcessingTimeMs);

        log.info("Message processed successfully");
        log.debug("Processing time: {} ms", lastProcessingTimeMs);
    }

    private void logInformation(ConsumerRecord<String, String> consumerRecord) {
        log.info("Received WiFi scan message from Kafka");
        log.info("Topic: {}", consumerRecord.topic());
        log.info("Partition: {}", consumerRecord.partition());
        log.info("Offset: {}", consumerRecord.offset());
        log.info("Key: {}", consumerRecord.key());
        log.info("Value: {}", consumerRecord.value());

        // Extract and log metadata
        if (log.isDebugEnabled()) {
            String metadata = extractMessageMetadata(consumerRecord);
            log.debug("Message metadata: {}", metadata);
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
            log.warn("Received null or empty message");
            return false;
        }

        try {
            // Basic JSON validation - check if it starts and ends with braces
            String trimmed = messageValue.trim();
            if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
                log.debug("Message appears to be valid JSON format");
                return true;
            } else {
                log.warn("Message does not appear to be JSON format: {}", messageValue);
                return false;
            }
        } catch (Exception e) {
            log.error("Error validating message format", e);
            return false;
        }
    }

    /**
     * Extracts metadata from the consumer record.
     * 
     * @param consumerRecord the Kafka consumer record
     * @return formatted metadata string
     */
    public String extractMessageMetadata(ConsumerRecord<String, String> consumerRecord) {
        return String.format("Topic=%s, Partition=%d, Offset=%d, Key=%s, Timestamp=%d",
                consumerRecord.topic(),
                consumerRecord.partition(),
                consumerRecord.offset(),
                consumerRecord.key(),
                consumerRecord.timestamp());
    }

    /**
     * Processes the WiFi scan message content.
     * 
     * @param consumerRecord the Kafka consumer record containing the WiFi scan data
     */
    private void processWifiScanMessage(ConsumerRecord<String, String> consumerRecord) throws Exception {
        log.debug("Processing WiFi scan data from message");

    
            String messageValue = consumerRecord.value();

            // For now, just log the message content
            // In future phases, this will be enhanced to:
            // 1. Parse the JSON message
            // 2. Validate the WiFi scan data structure
            // 3. Transform the data if needed
            // 4. Send to Kinesis Data Firehose

            log.info("WiFi scan message content length: {} characters",
                    messageValue != null ? messageValue.length() : 0);
            log.debug("WiFi scan message content : {} ",
                    messageValue);

            // Simulate processing time for realistic behavior
            Thread.sleep(10); // 10ms processing simulation
    }

}