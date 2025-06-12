package com.wifi.scan.consume.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Enhanced configuration properties for health indicators.
 * Controls timeouts, thresholds, and behavior of custom health indicators.
 * 
 * Enhanced SSL Readiness Configuration Support:
 * - Multi-threshold certificate expiry warnings (30/15/7 days)
 * - CloudWatch metric export configuration
 * - Kubernetes event generation settings
 * - Enhanced readiness-specific SSL validation
 */
@Data
@Component
@ConfigurationProperties(prefix = "health.indicator")
public class HealthIndicatorConfiguration {

    // General health indicator settings
    private int timeoutSeconds = 5;
    private int memoryThresholdPercentage = 90;
    private int consumptionTimeoutMinutes = 5;
    private double minimumConsumptionRate = 0.0;
    private int retryAttempts = 3;
    private boolean enableCaching = true;
    private int cacheTtlSeconds = 30;
    
    // Enhanced SSL Certificate Readiness Configuration
    private int certificateExpirationWarningDays = 30;
    private int certificateExpirationUrgentDays = 15;
    private int certificateExpirationCriticalDays = 7;
    private int certificateValidationTimeoutSeconds = 10;
    private boolean enableCertificateChainValidation = true;
    private boolean enableEnhancedSslMonitoring = true;
    
    // CloudWatch Integration Configuration
    private boolean enableCloudWatchMetrics = true;
    private String cloudWatchMetricNamespace = "KafkaConsumer/SSL";
    private String certificateExpiryMetricName = "ssl_certificate_expiry_days";
    private boolean enableKubernetesEventGeneration = true;
    
    // Enhanced Readiness Specific Settings
    private boolean enableReadinessOptimization = true;
    private boolean enableOperationalGuidance = true;
    private boolean enableCertificateHealthScore = true;
    private int readinessProbeFrequencySeconds = 10;
    
    /**
     * Get the warning threshold for the specified alert stage.
     * 
     * @param stage the alert stage (WARNING, URGENT, CRITICAL)
     * @return the number of days for the threshold
     */
    public int getCertificateWarningThreshold(String stage) {
        return switch (stage.toUpperCase()) {
            case "CRITICAL" -> certificateExpirationCriticalDays;
            case "URGENT" -> certificateExpirationUrgentDays;
            case "WARNING" -> certificateExpirationWarningDays;
            default -> certificateExpirationWarningDays;
        };
    }
    
    /**
     * Check if enhanced SSL monitoring features are enabled.
     * 
     * @return true if enhanced monitoring is enabled
     */
    public boolean isEnhancedSslMonitoringEnabled() {
        return enableEnhancedSslMonitoring && enableReadinessOptimization;
    }
    
    /**
     * Check if CloudWatch integration is fully enabled.
     * 
     * @return true if CloudWatch integration is enabled
     */
    public boolean isCloudWatchIntegrationEnabled() {
        return enableCloudWatchMetrics && enableKubernetesEventGeneration;
    }
} 