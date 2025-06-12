package com.wifi.scan.consume.controller;

import com.wifi.scan.consume.service.KafkaMonitoringService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * REST controller for exposing Kafka consumer metrics and operational information.
 * Provides detailed metrics endpoints for monitoring systems (Prometheus, Grafana).
 * 
 * NOTE: This controller provides operational metrics, separate from health checks.
 * Health monitoring is handled by Spring Boot Actuator at /frisco-location-wifi-scan-vmb-consumer/health
 */
@Slf4j
@RestController
@RequestMapping("/api/metrics")
@RequiredArgsConstructor
public class MetricsController {

    private final KafkaMonitoringService monitoringService;

    /**
     * Gets current Kafka consumer metrics for operational monitoring.
     * This provides detailed metrics for monitoring systems like Prometheus/Grafana.
     * 
     * @return ResponseEntity containing comprehensive metrics data
     */
    @GetMapping("/kafka")
    public ResponseEntity<Map<String, Object>> getKafkaMetrics() {
        log.debug("Fetching Kafka consumer metrics for operational monitoring");
        
        Map<String, Object> metricsData = new HashMap<>();
        
        // Message processing metrics
        metricsData.put("totalMessagesConsumed", monitoringService.getTotalMessagesConsumed());
        metricsData.put("totalMessagesProcessed", monitoringService.getTotalMessagesProcessed());
        metricsData.put("totalMessagesFailed", monitoringService.getTotalMessagesFailed());
        metricsData.put("successRate", monitoringService.getSuccessRate());
        metricsData.put("errorRate", monitoringService.getErrorRate());
        
        // Performance metrics
        metricsData.put("averageProcessingTimeMs", monitoringService.getAverageProcessingTimeMs());
        metricsData.put("minProcessingTimeMs", monitoringService.getMinProcessingTimeMs());
        metricsData.put("maxProcessingTimeMs", monitoringService.getMaxProcessingTimeMs());
        
        // Activity metrics
        metricsData.put("firstMessageTimestamp", monitoringService.getFirstMessageTimestamp());
        metricsData.put("lastMessageTimestamp", monitoringService.getLastMessageTimestamp());
        metricsData.put("lastPollTimestamp", monitoringService.getLastPollTimestamp());
        metricsData.put("isPollingActive", monitoringService.isPollingActive());
        
        // Consumer state metrics
        metricsData.put("isConsumerConnected", monitoringService.isConsumerConnected());
        metricsData.put("consumerGroupActive", monitoringService.isConsumerGroupActive());
        
        // Memory metrics
        metricsData.put("memoryUsagePercentage", monitoringService.getMemoryUsagePercentage());
        metricsData.put("usedMemoryMB", monitoringService.getUsedMemoryMB());
        metricsData.put("totalMemoryMB", monitoringService.getTotalMemoryMB());
        metricsData.put("maxMemoryMB", monitoringService.getMaxMemoryMB());
        
        // Consumption rate metrics
        metricsData.put("consumptionRate", monitoringService.getConsumptionRate());
        metricsData.put("isConsumptionHealthy", monitoringService.isConsumptionHealthy());
        
        // Metadata
        metricsData.put("timestamp", System.currentTimeMillis());
        metricsData.put("metricsVersion", "2.0.0");
        
        return ResponseEntity.ok(metricsData);
    }

    /**
     * Gets a detailed metrics summary as plain text for operational monitoring.
     * Useful for quick debugging and monitoring dashboards.
     * 
     * @return ResponseEntity containing formatted metrics summary
     */
    @GetMapping("/kafka/summary")
    public ResponseEntity<String> getKafkaMetricsSummary() {
        log.debug("Fetching Kafka consumer metrics summary for operational monitoring");
        
        String summary = monitoringService.getMetricsSummary();
        return ResponseEntity.ok()
                .header("Content-Type", "text/plain")
                .body(summary);
    }

    /**
     * Resets all Kafka consumer metrics.
     * Useful for testing or starting fresh monitoring period.
     * 
     * @return ResponseEntity with success message
     */
    @PostMapping("/kafka/reset")
    public ResponseEntity<Map<String, String>> resetKafkaMetrics() {
        log.info("Resetting Kafka consumer metrics for operational monitoring");
        
        monitoringService.resetMetrics();
        
        Map<String, String> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "Kafka consumer metrics have been reset");
        response.put("timestamp", String.valueOf(System.currentTimeMillis()));
        response.put("note", "Health indicators are not affected by this reset");
        
        return ResponseEntity.ok(response);
    }

    /**
     * Gets operational consumer status for monitoring systems.
     * This provides detailed operational status separate from health checks.
     * 
     * @return ResponseEntity with operational status information
     */
    @GetMapping("/kafka/status")
    public ResponseEntity<Map<String, Object>> getOperationalStatus() {
        log.debug("Fetching operational status for monitoring systems");
        
        Map<String, Object> statusData = new HashMap<>();
        statusData.put("service", "wifi-scan-queue-consumer");
        statusData.put("version", "1.0.0-SNAPSHOT");
        statusData.put("timestamp", System.currentTimeMillis());
        
        // Operational status indicators
        statusData.put("consumerConnected", monitoringService.isConsumerConnected());
        statusData.put("consumerGroupActive", monitoringService.isConsumerGroupActive());
        statusData.put("pollingActive", monitoringService.isPollingActive());
        statusData.put("consumptionHealthy", monitoringService.isConsumptionHealthy());
        statusData.put("memoryHealthy", monitoringService.isMemoryHealthy());
        
        // Operational metrics summary
        statusData.put("totalMessagesProcessed", monitoringService.getTotalMessagesProcessed());
        statusData.put("lastMessageTimestamp", monitoringService.getLastMessageTimestamp());
        statusData.put("lastPollTimestamp", monitoringService.getLastPollTimestamp());
        statusData.put("successRate", monitoringService.getSuccessRate());
        statusData.put("memoryUsagePercentage", monitoringService.getMemoryUsagePercentage());
        
        // Note about health checks
        statusData.put("healthEndpoint", "/frisco-location-wifi-scan-vmb-consumer/health");
        statusData.put("readinessEndpoint", "/frisco-location-wifi-scan-vmb-consumer/health/readiness");
        statusData.put("livenessEndpoint", "/frisco-location-wifi-scan-vmb-consumer/health/liveness");
        
        return ResponseEntity.ok(statusData);
    }
} 