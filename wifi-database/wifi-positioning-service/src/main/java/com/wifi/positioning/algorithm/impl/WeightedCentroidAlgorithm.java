package com.wifi.positioning.algorithm.impl;

import com.wifi.positioning.algorithm.PositioningAlgorithm;
import com.wifi.positioning.dto.Position;
import com.wifi.positioning.dto.WifiScanResult;
import com.wifi.positioning.model.WifiAccessPoint;
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

                return new WeightedPositionResult(
                    ap.getLatitude() * weight,
                    ap.getLongitude() * weight,
                    ap.getAltitude() * weight,
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

        return new Position(
            weightedLat.doubleValue() / totalWeight.doubleValue(),
            weightedLon.doubleValue() / totalWeight.doubleValue(),
            weightedAlt.doubleValue() / totalWeight.doubleValue(),
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
} 