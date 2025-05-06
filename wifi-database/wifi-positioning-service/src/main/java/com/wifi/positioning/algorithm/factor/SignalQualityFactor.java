package com.wifi.positioning.algorithm.factor;

import com.wifi.positioning.dto.WifiScanResult;
import java.util.List;

/**
 * Enum representing different signal quality scenarios that affect algorithm weights.
 * Based on the algorithm selection framework documentation.
 */
public enum SignalQualityFactor {
    /** Strong signals (better than -70 dBm) */
    STRONG_SIGNAL(Double.NEGATIVE_INFINITY, -70.0),
    
    /** Medium signals (between -70 and -85 dBm) */
    MEDIUM_SIGNAL(-70.0, -85.0),
    
    /** Weak signals (between -85 and -95 dBm) */
    WEAK_SIGNAL(-85.0, -95.0),
    
    /** Very weak signals (worse than -95 dBm) */
    VERY_WEAK_SIGNAL(-95.0, Double.POSITIVE_INFINITY);
    
    private final double upperBound;
    private final double lowerBound;
    
    SignalQualityFactor(double upperBound, double lowerBound) {
        this.upperBound = upperBound;
        this.lowerBound = lowerBound;
    }
    
    /**
     * Determine the appropriate signal quality factor based on the average signal strength.
     * 
     * @param signalStrength The signal strength in dBm
     * @return The corresponding SignalQualityFactor
     */
    public static SignalQualityFactor fromSignalStrength(double signalStrength) {
        if (signalStrength > -70.0) {
            return STRONG_SIGNAL;
        } else if (signalStrength > -85.0) {
            return MEDIUM_SIGNAL;
        } else if (signalStrength > -95.0) {
            return WEAK_SIGNAL;
        } else {
            return VERY_WEAK_SIGNAL;
        }
    }
    
    /**
     * Calculate the average signal quality from a list of WiFi scan results.
     * 
     * @param wifiScans List of WiFi scan results
     * @return The corresponding SignalQualityFactor based on average signal strength
     */
    public static SignalQualityFactor fromWifiScans(List<WifiScanResult> wifiScans) {
        if (wifiScans == null || wifiScans.isEmpty()) {
            return MEDIUM_SIGNAL;
        }
        
        double avgSignalStrength = wifiScans.stream()
                .mapToDouble(WifiScanResult::signalStrength)
                .average()
                .orElse(-80.0); // Default to medium if no signal
                
        return fromSignalStrength(avgSignalStrength);
    }
} 