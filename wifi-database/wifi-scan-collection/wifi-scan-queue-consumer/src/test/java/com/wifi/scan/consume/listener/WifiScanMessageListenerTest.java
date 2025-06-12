package com.wifi.scan.consume.listener;

import com.wifi.scan.consume.metrics.KafkaConsumerMetrics;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.isNull;

/**
 * Unit tests for WiFi scan message listener functionality.
 * Tests message consumption and processing using TDD approach.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("WiFi Scan Message Listener Tests")
class WifiScanMessageListenerTest {

    @Mock
    private Logger mockLogger;

    @Mock
    private Acknowledgment acknowledgment;

    @Mock
    private KafkaConsumerMetrics mockMetrics;

    @Captor
    private ArgumentCaptor<String> logMessageCaptor;

    private WifiScanMessageListener wifiScanMessageListener;

    @BeforeEach
    void setUp() {
        wifiScanMessageListener = new WifiScanMessageListener();
        // Inject mock logger for testing
        wifiScanMessageListener.setLogger(mockLogger);
        // Inject mock metrics using ReflectionTestUtils
        ReflectionTestUtils.setField(wifiScanMessageListener, "metrics", mockMetrics);
    }

    @Test
    @DisplayName("should_ProcessMessage_When_ValidMessageReceived")
    void should_ProcessMessage_When_ValidMessageReceived() {
        // Given
        String messageKey = "wifi-scan-123";
        String messageValue = "{\"timestamp\":\"2025-01-10T10:30:00Z\",\"ssid\":\"TestNetwork\",\"signal\":-45}";
        int partition = 0;
        long offset = 12345L;
        String topic = "wifi-scan-data";

        ConsumerRecord<String, String> record = new ConsumerRecord<>(
            topic, partition, offset, messageKey, messageValue
        );

        // When
        wifiScanMessageListener.listen(record, acknowledgment);

        // Then
        verify(mockLogger, times(1)).info(contains("Received WiFi scan message"));
        verify(mockLogger, times(1)).info(eq("Topic: {}"), eq(topic));
        verify(mockLogger, times(1)).info(eq("Partition: {}"), eq(partition));
        verify(mockLogger, times(1)).info(eq("Offset: {}"), eq(offset));
        verify(mockLogger, times(1)).info(eq("Key: {}"), eq(messageKey));
        verify(mockLogger, times(1)).info(eq("Value: {}"), eq(messageValue));
        verify(acknowledgment, times(1)).acknowledge();
    }

    @Test
    @DisplayName("should_ProcessMessage_When_NullKeyReceived")
    void should_ProcessMessage_When_NullKeyReceived() {
        // Given
        String messageValue = "{\"timestamp\":\"2025-01-10T10:30:00Z\",\"ssid\":\"TestNetwork\"}";
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
            "wifi-scan-data", 0, 12345L, null, messageValue
        );

        // When
        wifiScanMessageListener.listen(record, acknowledgment);

        // Then
        verify(mockLogger, times(1)).info(eq("Key: {}"), isNull(String.class));
        verify(acknowledgment, times(1)).acknowledge();
    }

    @Test
    @DisplayName("should_ProcessMessage_When_EmptyValueReceived")
    void should_ProcessMessage_When_EmptyValueReceived() {
        // Given
        String messageKey = "wifi-scan-456";
        String messageValue = "";
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
            "wifi-scan-data", 0, 12345L, messageKey, messageValue
        );

        // When
        wifiScanMessageListener.listen(record, acknowledgment);

        // Then
        verify(mockLogger, times(1)).info(eq("Value: {}"), eq(""));
        verify(acknowledgment, times(1)).acknowledge();
    }

    @Test
    @DisplayName("should_HandleException_When_ProcessingFails")
    void should_HandleException_When_ProcessingFails() {
        // Given
        String messageKey = "wifi-scan-789";
        String messageValue = "invalid-json-{";
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
            "wifi-scan-data", 0, 12345L, messageKey, messageValue
        );

        // Simulate processing failure
        doThrow(new RuntimeException("Processing failed")).when(acknowledgment).acknowledge();

        // When & Then
        assertDoesNotThrow(() -> {
            wifiScanMessageListener.listen(record, acknowledgment);
        }, "Listener should handle exceptions gracefully");

        verify(mockLogger, times(1)).error(eq("Error processing WiFi scan message from topic: {}, partition: {}, offset: {}"), 
            eq("wifi-scan-data"), eq(0), eq(12345L), any(RuntimeException.class));
    }

    @Test
    @DisplayName("should_LogProcessingMetrics_When_MessageProcessed")
    void should_LogProcessingMetrics_When_MessageProcessed() {
        // Given
        String messageKey = "wifi-scan-metrics";
        String messageValue = "{\"data\":\"test\"}";
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
            "wifi-scan-data", 2, 99999L, messageKey, messageValue
        );

        // When
        wifiScanMessageListener.listen(record, acknowledgment);

        // Then
        verify(mockLogger, times(1)).info(eq("Message processed successfully"));
        verify(mockLogger, times(1)).debug(eq("Processing time: {} ms"), anyLong());
    }

    @Test
    @DisplayName("should_ValidateMessageContent_When_JSONMessageReceived")
    void should_ValidateMessageContent_When_JSONMessageReceived() {
        // Given
        String validJsonMessage = "{\"timestamp\":\"2025-01-10T10:30:00Z\",\"ssid\":\"TestNetwork\",\"signal\":-45}";
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
            "wifi-scan-data", 0, 12345L, "key", validJsonMessage
        );

        // When
        boolean isValid = wifiScanMessageListener.validateMessageFormat(validJsonMessage);

        // Then
        assertTrue(isValid, "Valid JSON message should pass validation");
    }

    @Test
    @DisplayName("should_RejectInvalidMessage_When_InvalidJSONReceived")
    void should_RejectInvalidMessage_When_InvalidJSONReceived() {
        // Given
        String invalidJsonMessage = "invalid-json-{";

        // When
        boolean isValid = wifiScanMessageListener.validateMessageFormat(invalidJsonMessage);

        // Then
        assertFalse(isValid, "Invalid JSON message should fail validation");
    }

    @Test
    @DisplayName("should_ExtractMetadata_When_ConsumerRecordProvided")
    void should_ExtractMetadata_When_ConsumerRecordProvided() {
        // Given
        String topic = "wifi-scan-data";
        int partition = 3;
        long offset = 54321L;
        String key = "metadata-key";
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
            topic, partition, offset, key, "value"
        );

        // When
        String metadata = wifiScanMessageListener.extractMessageMetadata(record);

        // Then
        assertNotNull(metadata, "Metadata should not be null");
        assertTrue(metadata.contains(topic), "Metadata should contain topic");
        assertTrue(metadata.contains(String.valueOf(partition)), "Metadata should contain partition");
        assertTrue(metadata.contains(String.valueOf(offset)), "Metadata should contain offset");
        assertTrue(metadata.contains(key), "Metadata should contain key");
    }

    @Test
    @DisplayName("should_IncrementMessageCounter_When_MessageProcessed")
    void should_IncrementMessageCounter_When_MessageProcessed() {
        // Given
        ConsumerRecord<String, String> record1 = new ConsumerRecord<>("topic", 0, 1L, "key1", "value1");
        ConsumerRecord<String, String> record2 = new ConsumerRecord<>("topic", 0, 2L, "key2", "value2");

        // When
        wifiScanMessageListener.listen(record1, acknowledgment);
        wifiScanMessageListener.listen(record2, acknowledgment);

        // Then
        long messageCount = wifiScanMessageListener.getProcessedMessageCount();
        assertEquals(2L, messageCount, "Message counter should increment for each processed message");
    }

    @Test
    @DisplayName("should_TrackProcessingTime_When_MessageProcessed")
    void should_TrackProcessingTime_When_MessageProcessed() {
        // Given
        ConsumerRecord<String, String> record = new ConsumerRecord<>("topic", 0, 1L, "key", "value");

        // When
        long startTime = System.currentTimeMillis();
        wifiScanMessageListener.listen(record, acknowledgment);
        long endTime = System.currentTimeMillis();

        // Then
        long lastProcessingTime = wifiScanMessageListener.getLastProcessingTimeMs();
        assertTrue(lastProcessingTime >= 0, "Processing time should be non-negative");
        assertTrue(lastProcessingTime <= (endTime - startTime), "Processing time should be reasonable");
    }

    @Test
    @DisplayName("should_HandleManualAcknowledgment_When_AcknowledgmentProvided")
    void should_HandleManualAcknowledgment_When_AcknowledgmentProvided() {
        // Given
        ConsumerRecord<String, String> record = new ConsumerRecord<>("topic", 0, 1L, "key", "value");

        // When
        wifiScanMessageListener.listen(record, acknowledgment);

        // Then
        verify(acknowledgment, times(1)).acknowledge();
    }

    @Test
    @DisplayName("should_HandleNullAcknowledgment_When_AutoCommitEnabled")
    void should_HandleNullAcknowledgment_When_AutoCommitEnabled() {
        // Given
        ConsumerRecord<String, String> record = new ConsumerRecord<>("topic", 0, 1L, "key", "value");

        // When & Then
        assertDoesNotThrow(() -> {
            wifiScanMessageListener.listen(record, null);
        }, "Should handle null acknowledgment gracefully");

        verify(mockLogger, times(1)).warn(contains("Acknowledgment is null"));
    }
} 