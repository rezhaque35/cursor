package com.wifi.positioning.algorithm.impl;

import com.wifi.positioning.algorithm.PositioningAlgorithm;
import com.wifi.positioning.algorithm.selection.factor.APCountFactor;
import com.wifi.positioning.algorithm.selection.factor.GeometricQualityFactor;
import com.wifi.positioning.algorithm.selection.factor.SignalDistributionFactor;
import com.wifi.positioning.algorithm.selection.factor.SignalQualityFactor;
import com.wifi.positioning.dto.Position;
import com.wifi.positioning.dto.WifiScanResult;
import com.wifi.positioning.dto.WifiAccessPoint;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.DoubleAdder;
import java.util.stream.Collectors;

/**
 * Implementation of the Weighted Centroid positioning algorithm.
 * 
 * USE CASES:
 * - Best suited for environments with many APs (4+ APs)
 * - Effective when AP geometry is poor for trilateration
 * - Useful in areas with high AP density but variable signal quality
 * - Good fallback when more precise methods fail
 * 
 * STRENGTHS:
 * - Simple and computationally efficient
 * - Robust to individual AP failures or signal anomalies
 * - Works well with non-uniform AP distributions
 * - Handles overlapping AP coverage effectively
 * 
 * WEAKNESSES:
 * - Less accurate than trilateration in ideal conditions
 * - Sensitive to AP distribution geometry
 * - May be biased toward areas of high AP density
 * - Accuracy depends heavily on signal strength quality
 * 
 * TUNABLE PARAMETERS:
 * - MAX_WIFI_SIGNAL: Upper bound for signal strength normalization (-30dBm typical)
 * - MIN_WIFI_SIGNAL: Lower bound for signal strength normalization (-100dBm typical)
 * - Base confidence value (0.7) for algorithm weighting
 * - Signal strength to weight exponential factor
 * 
 * MATHEMATICAL MODEL:
 * The algorithm uses a weighted average of AP positions:
 * 
 * 1. Signal Strength Normalization:
 *    normalized = (RSSI - MAX_SIGNAL) / (MIN_SIGNAL - MAX_SIGNAL)
 *    where:
 *    - RSSI is the received signal strength
 *    - Normalized value is between 0 and 1
 * 
 * 2. Weight Calculation:
 *    weight = 10^normalized
 *    This gives exponentially more weight to stronger signals
 * 
 * 3. Position Calculation:
 *    P = Σ(Pi * wi) / Σ(wi)
 *    where:
 *    - P is final position (lat, lon, alt)
 *    - Pi is position of AP i
 *    - wi is weight of AP i
 * 
 * 4. Confidence Calculation:
 *    confidence = min(0.8, coverage * base_confidence)
 *    where:
 *    - coverage is ratio of visible APs to known APs
 *    - base_confidence is algorithm's inherent confidence (0.7)
 */
@Component
public class WeightedCentroidAlgorithm implements PositioningAlgorithm {

    private static final double MAX_WIFI_SIGNAL = -30.0;  // Typical maximum WiFi signal strength
    private static final double MIN_WIFI_SIGNAL = -100.0; // Typical minimum WiFi signal strength
    
    /**
     * Weight constants from the algorithm selection framework.
     * These reflect the strengths and weaknesses of the Weighted Centroid algorithm:
     * - Works well with 2+ APs, optimal with 3-4+ APs
     * - Robust to signal quality variations
     * - Improved performance with poor geometry (unlike trilateration)
     * - Very effective with mixed signals and outliers
     */
    // AP Count weights from framework document
    private static final double WEIGHTED_CENTROID_SINGLE_AP_WEIGHT = 0.0;     // Not applicable for single AP
    private static final double WEIGHTED_CENTROID_TWO_APS_WEIGHT = 0.8;       // Good for two APs
    private static final double WEIGHTED_CENTROID_THREE_APS_WEIGHT = 0.8;     // Good for three APs
    private static final double WEIGHTED_CENTROID_FOUR_PLUS_APS_WEIGHT = 0.7; // Good for 4+ APs
    
    // Signal quality multipliers from framework document
    private static final double WEIGHTED_CENTROID_STRONG_SIGNAL_MULTIPLIER = 1.0;  // No change with strong signals
    private static final double WEIGHTED_CENTROID_MEDIUM_SIGNAL_MULTIPLIER = 1.0;  // No change with medium signals
    private static final double WEIGHTED_CENTROID_WEAK_SIGNAL_MULTIPLIER = 0.8;    // Moderate reduction with weak signals
    private static final double WEIGHTED_CENTROID_VERY_WEAK_SIGNAL_MULTIPLIER = 0.0; // ×0.0 for very weak signals
    
    // Geometric quality multipliers from framework document
    private static final double WEIGHTED_CENTROID_EXCELLENT_GDOP_MULTIPLIER = 1.0; // No change for excellent geometry
    private static final double WEIGHTED_CENTROID_GOOD_GDOP_MULTIPLIER = 1.1;      // Slight boost with good geometry
    private static final double WEIGHTED_CENTROID_FAIR_GDOP_MULTIPLIER = 1.2;      // Better with fair geometry
    private static final double WEIGHTED_CENTROID_POOR_GDOP_MULTIPLIER = 1.3;      // Best with poor geometry
    
    // Signal distribution multipliers from framework document
    private static final double WEIGHTED_CENTROID_UNIFORM_SIGNALS_MULTIPLIER = 1.0;  // No change for uniform signals
    private static final double WEIGHTED_CENTROID_MIXED_SIGNALS_MULTIPLIER = 1.8;    // Better with mixed signals
    private static final double WEIGHTED_CENTROID_SIGNAL_OUTLIERS_MULTIPLIER = 1.4;  // Best with signal outliers
    
    /**
     * Helper class to store weighted position calculation results
     */
    private static class WeightedPositionResult {
        final double weightedLat;
        final double weightedLon;
        final double weightedAlt;
        final double weight;

        WeightedPositionResult(double weightedLat, double weightedLon, double weightedAlt, double weight) {
            this.weightedLat = weightedLat;
            this.weightedLon = weightedLon;
            this.weightedAlt = weightedAlt;
            this.weight = weight;
        }
    }

    @Override
    public Position calculatePosition(List<WifiScanResult> wifiScan, List<WifiAccessPoint> knownAPs) {
        if (wifiScan == null || wifiScan.isEmpty() || knownAPs == null || knownAPs.isEmpty()) {
            return null;
        }

        // Create map of MAC addresses to known APs with thread-safe implementation
        Map<String, WifiAccessPoint> apMap = knownAPs.stream()
            .collect(Collectors.toConcurrentMap(
                WifiAccessPoint::getMacAddress, 
                ap -> ap,
                (ap1, ap2) -> ap1 // In case of duplicate keys, keep the first one
            ));

        // Use atomic accumulators for thread-safe calculations
        DoubleAdder totalWeight = new DoubleAdder();
        DoubleAdder weightedLat = new DoubleAdder();
        DoubleAdder weightedLon = new DoubleAdder();
        DoubleAdder weightedAlt = new DoubleAdder();
        DoubleAdder altitudeWeightSum = new DoubleAdder(); // Track weights for altitude separately

        // Process each AP in parallel
        List<WeightedPositionResult> results = wifiScan.parallelStream()
            .map(scan -> {
                WifiAccessPoint ap = apMap.get(scan.macAddress());
                if (ap == null) {
                    return null;
                }

                // Normalize signal strength to weight (0-1)
                double normalizedSignal = (scan.signalStrength() - MAX_WIFI_SIGNAL) / (MIN_WIFI_SIGNAL - MAX_WIFI_SIGNAL);
                double weight = Math.pow(10, normalizedSignal); // Exponential weighting

                // Only include altitude in weighted calculation if it's not null
                double weightedAltitude = 0.0;
                if (ap.getAltitude() != null) {
                    weightedAltitude = ap.getAltitude() * weight;
                }

                return new WeightedPositionResult(
                    ap.getLatitude() * weight,
                    ap.getLongitude() * weight,
                    weightedAltitude,
                    weight
                );
            })
            .filter(result -> result != null)
            .collect(Collectors.toList());

        // Combine all results
        results.forEach(result -> {
            weightedLat.add(result.weightedLat);
            weightedLon.add(result.weightedLon);
            weightedAlt.add(result.weightedAlt);
            totalWeight.add(result.weight);
            
            // Only add to altitude weight sum if there's a valid altitude contribution
            if (result.weightedAlt != 0.0) {
                altitudeWeightSum.add(result.weight);
            }
        });

        if (totalWeight.doubleValue() == 0) {
            return null;
        }

        // Calculate average accuracy using parallel stream
        double avgAccuracy = knownAPs.parallelStream()
            .mapToDouble(WifiAccessPoint::getHorizontalAccuracy)
            .average()
            .orElse(15.0);

        // Calculate confidence based on number of APs and signal distribution
        double coverage = (double) wifiScan.size() / knownAPs.size();
        double confidence = Math.min(0.8, coverage * getConfidence());
        
        // Calculate altitude only if we have valid altitude data, otherwise default to 0.0
        double altitude = 0.0;
        if (altitudeWeightSum.doubleValue() > 0) {
            altitude = weightedAlt.doubleValue() / altitudeWeightSum.doubleValue();
        }

        return new Position(
            weightedLat.doubleValue() / totalWeight.doubleValue(),
            weightedLon.doubleValue() / totalWeight.doubleValue(),
            altitude,
            avgAccuracy,
            confidence
        );
    }

    @Override
    public double getConfidence() {
        return 0.7; // Base confidence for weighted centroid method
    }

    @Override
    public String getName() {
        return "weighted_centroid";
    }
    
    @Override
    public double getBaseWeight(APCountFactor factor) {
        switch (factor) {
            case SINGLE_AP:
                return WEIGHTED_CENTROID_SINGLE_AP_WEIGHT;      // Not applicable for single AP
            case TWO_APS:
                return WEIGHTED_CENTROID_TWO_APS_WEIGHT;        // Good for two APs
            case THREE_APS:
                return WEIGHTED_CENTROID_THREE_APS_WEIGHT;      // Good for three APs
            case FOUR_PLUS_APS:
                return WEIGHTED_CENTROID_FOUR_PLUS_APS_WEIGHT;  // Good for 4+ APs
            default:
                return 0.0;
        }
    }
    
    @Override
    public double getSignalQualityMultiplier(SignalQualityFactor factor) {
        switch (factor) {
            case STRONG_SIGNAL:
                return WEIGHTED_CENTROID_STRONG_SIGNAL_MULTIPLIER;
            case MEDIUM_SIGNAL:
                return WEIGHTED_CENTROID_MEDIUM_SIGNAL_MULTIPLIER;
            case WEAK_SIGNAL:
                return WEIGHTED_CENTROID_WEAK_SIGNAL_MULTIPLIER;
            case VERY_WEAK_SIGNAL:
                return WEIGHTED_CENTROID_VERY_WEAK_SIGNAL_MULTIPLIER;
            default:
                return WEIGHTED_CENTROID_MEDIUM_SIGNAL_MULTIPLIER;
        }
    }
    
    @Override
    public double getGeometricQualityMultiplier(GeometricQualityFactor factor) {
        switch (factor) {
            case EXCELLENT_GDOP:
                return WEIGHTED_CENTROID_EXCELLENT_GDOP_MULTIPLIER;
            case GOOD_GDOP:
                return WEIGHTED_CENTROID_GOOD_GDOP_MULTIPLIER;
            case FAIR_GDOP:
                return WEIGHTED_CENTROID_FAIR_GDOP_MULTIPLIER;
            case POOR_GDOP:
                return WEIGHTED_CENTROID_POOR_GDOP_MULTIPLIER;
            default:
                return WEIGHTED_CENTROID_GOOD_GDOP_MULTIPLIER;
        }
    }
    
    @Override
    public double getSignalDistributionMultiplier(SignalDistributionFactor factor) {
        switch (factor) {
            case UNIFORM_SIGNALS:
                return WEIGHTED_CENTROID_UNIFORM_SIGNALS_MULTIPLIER;
            case MIXED_SIGNALS:
                return WEIGHTED_CENTROID_MIXED_SIGNALS_MULTIPLIER;
            case SIGNAL_OUTLIERS:
                return WEIGHTED_CENTROID_SIGNAL_OUTLIERS_MULTIPLIER;
            default:
                return WEIGHTED_CENTROID_MIXED_SIGNALS_MULTIPLIER;
        }
    }
} 