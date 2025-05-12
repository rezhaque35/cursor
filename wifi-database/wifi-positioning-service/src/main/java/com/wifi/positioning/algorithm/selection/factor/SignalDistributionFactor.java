package com.wifi.positioning.algorithm.selection.factor;

import com.wifi.positioning.dto.WifiScanResult;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Enum representing different signal distribution patterns that affect algorithm weights.
 * Based on the algorithm selection framework documentation.
 */
public enum SignalDistributionFactor {
    /** Uniform signal levels across APs */
    UNIFORM_SIGNALS,
    
    /** Mixed signal levels with moderate variation */
    MIXED_SIGNALS,
    
    /** Signal distribution with significant outliers */
    SIGNAL_OUTLIERS;
    
    /**
     * Standard deviation threshold for determining uniform vs. mixed signals.
     * Signals with standard deviation below this are considered uniform.
     */
    private static final double UNIFORM_THRESHOLD = 3.0;
    
    /**
     * Standard deviation threshold for determining outlier presence.
     * Signals with standard deviation above this are considered to have outliers.
     */
    private static final double OUTLIER_THRESHOLD = 10.0;
    
    /**
     * Determine the appropriate signal distribution factor based on WiFi scan results.
     * 
     * @param wifiScans List of WiFi scan results
     * @return The corresponding SignalDistributionFactor
     */
    public static SignalDistributionFactor fromWifiScans(List<WifiScanResult> wifiScans) {
        if (wifiScans == null || wifiScans.size() < 2) {
            return UNIFORM_SIGNALS; // Default for insufficient data
        }
        
        // Extract signal strengths
        List<Double> signalStrengths = wifiScans.stream()
                .map(WifiScanResult::signalStrength)
                .collect(Collectors.toList());
                
        // Calculate mean
        double mean = signalStrengths.stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);
                
        // Calculate standard deviation
        double sumSquaredDiff = signalStrengths.stream()
                .mapToDouble(signal -> Math.pow(signal - mean, 2))
                .sum();
        double stdDev = Math.sqrt(sumSquaredDiff / signalStrengths.size());
        
        // Determine distribution type based on standard deviation
        if (stdDev > OUTLIER_THRESHOLD) {
            return SIGNAL_OUTLIERS;
        } else if (stdDev > UNIFORM_THRESHOLD) {
            return MIXED_SIGNALS;
        } else {
            return UNIFORM_SIGNALS;
        }
    }
} 