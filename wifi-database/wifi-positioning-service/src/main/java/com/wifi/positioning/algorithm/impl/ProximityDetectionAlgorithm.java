package com.wifi.positioning.algorithm.impl;

import com.wifi.positioning.algorithm.PositioningAlgorithm;
import com.wifi.positioning.algorithm.factor.APCountFactor;
import com.wifi.positioning.algorithm.factor.GeometricQualityFactor;
import com.wifi.positioning.algorithm.factor.SignalDistributionFactor;
import com.wifi.positioning.algorithm.factor.SignalQualityFactor;
import com.wifi.positioning.dto.Position;
import com.wifi.positioning.dto.WifiScanResult;
import com.wifi.positioning.model.WifiAccessPoint;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Comparator;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Implementation of the Proximity Detection positioning algorithm.
 * 
 * USE CASES:
 * - Single AP environments or sparse AP deployments
 * - When only coarse positioning is needed
 * - Emergency/fallback positioning when other methods fail
 * - Quick initial position estimation
 * 
 * STRENGTHS:
 * - Extremely simple and fast computation
 * - Minimal resource requirements
 * - Works with single AP
 * - Resistant to multipath effects
 * 
 * WEAKNESSES:
 * - Limited accuracy (position is always at AP location)
 * - No interpolation between APs
 * - Highly sensitive to signal strength fluctuations
 * - Poor performance in dense AP environments
 * 
 * TUNABLE PARAMETERS:
 * - MIN_SIGNAL_STRENGTH: Lower bound for usable signals (-90dBm typical)
 * - SIGNAL_RANGE: Range of signal strengths to consider (60dB typical)
 * - Base confidence value (0.6) for algorithm weighting
 * - Maximum confidence cap (0.85) for strongest signals
 * 
 * MATHEMATICAL MODEL:
 * The algorithm uses a simple strongest-signal approach:
 * 
 * 1. Signal Selection:
 *    Find AP with maximum RSSI:
 *    position = position of AP where RSSI = max(RSSI_all)
 * 
 * 2. Confidence Calculation:
 *    normalized = (RSSI - MIN_SIGNAL) / SIGNAL_RANGE
 *    confidence = min(0.85, max(0, normalized))
 *    where:
 *    - RSSI is the strongest signal strength
 *    - MIN_SIGNAL is minimum usable signal (-90dBm)
 *    - SIGNAL_RANGE is typical range (60dB)
 * 
 * 3. Accuracy:
 *    Uses the horizontal accuracy of the selected AP
 *    Typically ranges from 5-15 meters depending on environment
 */
@Component
public class ProximityDetectionAlgorithm implements PositioningAlgorithm {

    private static final double MIN_SIGNAL_STRENGTH = -90.0; // Unusable signal strength
    private static final double SIGNAL_RANGE = 60.0;         // Range from -90 to -30 dBm

    /**
     * Weight constants for Proximity Detection Algorithm
     * These values are based on the algorithm's characteristics:
     * - Works best with single AP (high weight for SINGLE_AP)
     * - Less valuable as AP count increases
     * - Highly dependent on signal strength
     * - No dependence on geometric distribution
     */
    private static final double PROXIMITY_SINGLE_AP_WEIGHT = 0.90;
    private static final double PROXIMITY_TWO_APS_WEIGHT = 0.70;
    private static final double PROXIMITY_THREE_APS_WEIGHT = 0.50;
    private static final double PROXIMITY_FOUR_PLUS_APS_WEIGHT = 0.40;
    
    private static final double PROXIMITY_STRONG_SIGNAL_ADJUSTMENT = 0.15;
    private static final double PROXIMITY_MEDIUM_SIGNAL_ADJUSTMENT = 0.0;
    private static final double PROXIMITY_WEAK_SIGNAL_ADJUSTMENT = -0.25;
    
    // Proximity algorithm doesn't depend on geometric factors
    private static final double PROXIMITY_GDOP_ADJUSTMENT = 0.0;
    
    private static final double PROXIMITY_UNIFORM_SIGNALS_ADJUSTMENT = 0.05;
    private static final double PROXIMITY_MIXED_SIGNALS_ADJUSTMENT = 0.0;
    private static final double PROXIMITY_SIGNAL_OUTLIERS_ADJUSTMENT = 0.10; // Good for outlier detection

    @Override
    public Position calculatePosition(List<WifiScanResult> wifiScan, List<WifiAccessPoint> knownAPs) {
        if (wifiScan == null || wifiScan.isEmpty() || knownAPs == null || knownAPs.isEmpty()) {
            return null;
        }

        // Create map for quick AP lookup
        Map<String, WifiAccessPoint> apMap = knownAPs.parallelStream()
            .collect(Collectors.toConcurrentMap(
                WifiAccessPoint::getMacAddress,
                ap -> ap,
                (ap1, ap2) -> ap1 // In case of duplicates, keep the first one
            ));

        // Find the strongest signal using parallel stream for larger datasets
        WifiScanResult strongestSignal = wifiScan.parallelStream()
            .max(Comparator.comparingDouble(WifiScanResult::signalStrength))
            .orElse(null);

        if (strongestSignal == null) {
            return null;
        }

        // Find corresponding AP
        WifiAccessPoint nearestAP = apMap.get(strongestSignal.macAddress());
        
        if (nearestAP == null) {
            return null;
        }

        // Calculate confidence based on signal strength
        // Typical WiFi range is -30 dBm (excellent) to -90 dBm (unusable)
        double normalizedSignal = (strongestSignal.signalStrength() - MIN_SIGNAL_STRENGTH) / SIGNAL_RANGE;
        double confidence = Math.max(0, Math.min(0.85, normalizedSignal));

        return new Position(
            nearestAP.getLatitude(),
            nearestAP.getLongitude(),
            nearestAP.getAltitude(),
            nearestAP.getHorizontalAccuracy(),
            confidence
        );
    }

    @Override
    public double getConfidence() {
        return 0.6; // Base confidence for proximity detection
    }

    @Override
    public String getName() {
        return "proximity";
    }
    
    @Override
    public double getBaseWeight(APCountFactor factor) {
        switch (factor) {
            case SINGLE_AP:
                return PROXIMITY_SINGLE_AP_WEIGHT;
            case TWO_APS:
                return PROXIMITY_TWO_APS_WEIGHT;
            case THREE_APS:
                return PROXIMITY_THREE_APS_WEIGHT;
            case FOUR_PLUS_APS:
                return PROXIMITY_FOUR_PLUS_APS_WEIGHT;
            default:
                return PROXIMITY_SINGLE_AP_WEIGHT;
        }
    }
    
    @Override
    public double getSignalQualityAdjustment(SignalQualityFactor factor) {
        switch (factor) {
            case STRONG_SIGNAL:
                return PROXIMITY_STRONG_SIGNAL_ADJUSTMENT;
            case MEDIUM_SIGNAL:
                return PROXIMITY_MEDIUM_SIGNAL_ADJUSTMENT;
            case WEAK_SIGNAL:
                return PROXIMITY_WEAK_SIGNAL_ADJUSTMENT;
            default:
                return PROXIMITY_MEDIUM_SIGNAL_ADJUSTMENT;
        }
    }
    
    @Override
    public double getGeometricQualityAdjustment(GeometricQualityFactor factor) {
        // Proximity algorithm doesn't depend on geometric factors
        return PROXIMITY_GDOP_ADJUSTMENT;
    }
    
    @Override
    public double getSignalDistributionAdjustment(SignalDistributionFactor factor) {
        switch (factor) {
            case UNIFORM_SIGNALS:
                return PROXIMITY_UNIFORM_SIGNALS_ADJUSTMENT;
            case MIXED_SIGNALS:
                return PROXIMITY_MIXED_SIGNALS_ADJUSTMENT;
            case SIGNAL_OUTLIERS:
                return PROXIMITY_SIGNAL_OUTLIERS_ADJUSTMENT;
            default:
                return PROXIMITY_MIXED_SIGNALS_ADJUSTMENT;
        }
    }
} 