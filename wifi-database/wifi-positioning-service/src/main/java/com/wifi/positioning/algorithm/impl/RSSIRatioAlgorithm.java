package com.wifi.positioning.algorithm.impl;

import com.wifi.positioning.algorithm.PositioningAlgorithm;
import com.wifi.positioning.dto.Position;
import com.wifi.positioning.dto.WifiScanResult;
import com.wifi.positioning.model.WifiAccessPoint;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.DoubleAdder;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Implementation of the RSSI Ratio positioning algorithm.
 * 
 * USE CASES:
 * - Ideal for scenarios with 2-3 access points
 * - Effective in environments where absolute signal calibration is difficult
 * - Works well when APs have similar hardware characteristics
 * 
 * STRENGTHS:
 * - No need for absolute signal strength calibration
 * - Resistant to environmental changes affecting all signals equally
 * - Computationally efficient for small numbers of APs
 * - Works well with dynamic transmit power changes
 * 
 * WEAKNESSES:
 * - Accuracy decreases with dissimilar AP hardware
 * - Performance degrades with more than 4-5 APs
 * - Sensitive to individual signal fluctuations
 * - Less accurate than trilateration in ideal conditions
 * 
 * TUNABLE PARAMETERS:
 * - BASE_CONFIDENCE: Base confidence level for the algorithm (0.0-1.0)
 * - MIN_REQUIRED_APS: Minimum number of APs needed (typically 2)
 * - Weight normalization factor (30.0) in ratio calculations
 * 
 * MATHEMATICAL MODEL:
 * The algorithm uses signal strength ratios to estimate relative distances:
 * 
 * 1. RSSI Ratio Calculation:
 *    ratio = 10^((RSSI1 - RSSI2)/20)
 *    where:
 *    - RSSI1, RSSI2 are signal strengths in dBm
 *    - 20 is the path loss coefficient for free space
 * 
 * 2. Position Estimation:
 *    For each AP pair (AP1, AP2):
 *    P = (P1 + ratio * P2)/(1 + ratio)
 *    where:
 *    - P is the estimated position (lat, lon, alt)
 *    - P1, P2 are the positions of AP1 and AP2
 *    - ratio is the calculated RSSI ratio
 * 
 * 3. Confidence Calculation:
 *    confidence = min(0.85, totalWeight / maxPossibleWeight)
 *    where:
 *    - totalWeight is sum of all pair weights
 *    - maxPossibleWeight is n*(n-1)/2 for n APs
 */
@Component
public class RSSIRatioAlgorithm implements PositioningAlgorithm {

    private static final String ALGORITHM_NAME = "RSSI Ratio";
    private static final double BASE_CONFIDENCE = 0.75;
    private static final int MIN_REQUIRED_APS = 2;

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
        // Input validation with proper exceptions
        if (wifiScan == null || knownAPs == null) {
            throw new IllegalArgumentException("WiFi scan and known APs cannot be null");
        }
        if (wifiScan.isEmpty() || knownAPs.isEmpty()) {
            throw new IllegalArgumentException("WiFi scan and known APs cannot be empty");
        }
        if (wifiScan.size() < MIN_REQUIRED_APS) {
            throw new IllegalArgumentException("At least " + MIN_REQUIRED_APS + " APs are required for RSSI ratio calculation");
        }

        // Create map of MAC addresses to known APs using ConcurrentHashMap for thread safety
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

        // Process AP pairs in parallel
        List<WeightedPositionResult> results = IntStream.range(0, wifiScan.size())
            .parallel()
            .boxed()
            .flatMap(i -> IntStream.range(i + 1, wifiScan.size())
                .parallel()
                .mapToObj(j -> {
                    WifiScanResult scan1 = wifiScan.get(i);
                    WifiScanResult scan2 = wifiScan.get(j);

                    WifiAccessPoint ap1 = apMap.get(scan1.macAddress());
                    WifiAccessPoint ap2 = apMap.get(scan2.macAddress());

                    if (ap1 == null || ap2 == null) {
                        return null;
                    }

                    // Calculate ratio of signal strengths
                    double ratio = Math.pow(10, (scan1.signalStrength() - scan2.signalStrength()) / 20.0);
                    double weight = Math.abs(scan1.signalStrength() - scan2.signalStrength()) / 30.0; // normalize weight

                    // Calculate weighted midpoint
                    double lat = (ap1.getLatitude() + ratio * ap2.getLatitude()) / (1 + ratio);
                    double lon = (ap1.getLongitude() + ratio * ap2.getLongitude()) / (1 + ratio);
                    double alt = (ap1.getAltitude() + ratio * ap2.getAltitude()) / (1 + ratio);

                    return new WeightedPositionResult(lat * weight, lon * weight, alt * weight, weight);
                })
            )
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
            throw new IllegalArgumentException("No valid AP pairs found for position calculation");
        }

        // Calculate average accuracy from known APs using parallel stream
        double avgAccuracy = knownAPs.parallelStream()
            .mapToDouble(WifiAccessPoint::getHorizontalAccuracy)
            .average()
            .orElse(15.0); // default accuracy if none available

        return new Position(
            weightedLat.doubleValue() / totalWeight.doubleValue(),
            weightedLon.doubleValue() / totalWeight.doubleValue(),
            weightedAlt.doubleValue() / totalWeight.doubleValue(),
            avgAccuracy,
            Math.min(0.85, totalWeight.doubleValue() / (wifiScan.size() * (wifiScan.size() - 1) / 2))
        );
    }

    @Override
    public double getConfidence() {
        return BASE_CONFIDENCE;
    }

    @Override
    public String getName() {
        return ALGORITHM_NAME;
    }
} 