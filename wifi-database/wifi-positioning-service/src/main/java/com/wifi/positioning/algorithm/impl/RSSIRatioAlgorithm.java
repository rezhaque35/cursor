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
     * Weight constants from the algorithm selection framework.
     * These reflect the strengths and weaknesses of the RSSI Ratio algorithm:
     * - Works optimally with 2 APs
     * - Good with 3 APs but less effective with more APs
     * - Medium signal quality sensitivity
     * - Moderate impact from geometric quality
     * - Good performance with uniform signals, worse with outliers
     */
    // AP Count weights from framework document
    private static final double RSSI_RATIO_SINGLE_AP_WEIGHT = 0.0;    // Not applicable for single AP
    private static final double RSSI_RATIO_TWO_APS_WEIGHT = 1.0;      // Optimal for two APs
    private static final double RSSI_RATIO_THREE_APS_WEIGHT = 0.7;    // Good for three APs
    private static final double RSSI_RATIO_FOUR_PLUS_APS_WEIGHT = 0.5;// Useful but not optimal for 4+ APs
    
    // Signal quality multipliers from framework document
    private static final double RSSI_RATIO_STRONG_SIGNAL_MULTIPLIER = 1.0;  // No change with strong signals
    private static final double RSSI_RATIO_MEDIUM_SIGNAL_MULTIPLIER = 0.9;  // Slight reduction with medium signals
    private static final double RSSI_RATIO_WEAK_SIGNAL_MULTIPLIER = 0.6;    // Significant reduction with weak signals
    private static final double RSSI_RATIO_VERY_WEAK_SIGNAL_MULTIPLIER = 0.0; // ×0.0 for very weak signals
    
    // Geometric quality multipliers from framework document
    private static final double RSSI_RATIO_EXCELLENT_GDOP_MULTIPLIER = 1.0; // No change for excellent geometry
    private static final double RSSI_RATIO_GOOD_GDOP_MULTIPLIER = 1.0;      // No change for good geometry
    private static final double RSSI_RATIO_FAIR_GDOP_MULTIPLIER = 0.9;      // Slight reduction for fair geometry
    private static final double RSSI_RATIO_POOR_GDOP_MULTIPLIER = 0.8;      // More reduction for poor geometry
    
    // Signal distribution multipliers from framework document
    private static final double RSSI_RATIO_UNIFORM_SIGNALS_MULTIPLIER = 1.2; // Significant improvement for uniform signals
    private static final double RSSI_RATIO_MIXED_SIGNALS_MULTIPLIER = 0.9;   // Slight reduction for mixed signals
    private static final double RSSI_RATIO_SIGNAL_OUTLIERS_MULTIPLIER = 0.7; // Significant reduction for outliers

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

        // Improved accuracy calculation
        double avgSignalStrength = wifiScan.stream()
            .mapToDouble(WifiScanResult::signalStrength)
            .average()
            .orElse(-80.0);

        double baseAccuracy = knownAPs.parallelStream()
            .mapToDouble(WifiAccessPoint::getHorizontalAccuracy)
            .average()
            .orElse(15.0);

        // Scale accuracy based on signal strength - weak signals get worse accuracy
        double signalFactor = Math.max(1.0, Math.min(3.0, 
            (-avgSignalStrength - 50) / 10.0));
        double finalAccuracy = baseAccuracy * signalFactor;

        // Improved confidence calculation
        double signalQuality = wifiScan.stream()
            .mapToDouble(scan -> Math.min(1.0, Math.max(0.0, (scan.signalStrength() + 95.0) / 45.0)))
            .average()
            .orElse(0.5);

        double baseConfidence = Math.min(0.85, totalWeight.doubleValue() / 
            (wifiScan.size() * (wifiScan.size() - 1) / 2));

        double computedConfidence = Math.min(0.85, baseConfidence + (signalQuality * 1.0));
        double finalConfidence = (avgSignalStrength >= -70) ? Math.max(0.7, computedConfidence) : computedConfidence;

        return new Position(
            weightedLat.doubleValue() / totalWeight.doubleValue(),
            weightedLon.doubleValue() / totalWeight.doubleValue(),
            weightedAlt.doubleValue() / totalWeight.doubleValue(),
            finalAccuracy,
            finalConfidence
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
    
    @Override
    public double getBaseWeight(APCountFactor factor) {
        switch (factor) {
            case SINGLE_AP:
                return RSSI_RATIO_SINGLE_AP_WEIGHT;      // Not applicable for single AP
            case TWO_APS:
                return RSSI_RATIO_TWO_APS_WEIGHT;        // Optimal for two APs
            case THREE_APS:
                return RSSI_RATIO_THREE_APS_WEIGHT;      // Good for three APs
            case FOUR_PLUS_APS:
                return RSSI_RATIO_FOUR_PLUS_APS_WEIGHT;  // Useful but not optimal for 4+ APs
            default:
                return 0.0;
        }
    }
    
    @Override
    public double getSignalQualityMultiplier(SignalQualityFactor factor) {
        switch (factor) {
            case STRONG_SIGNAL:
                return RSSI_RATIO_STRONG_SIGNAL_MULTIPLIER;
            case MEDIUM_SIGNAL:
                return RSSI_RATIO_MEDIUM_SIGNAL_MULTIPLIER;
            case WEAK_SIGNAL:
                return RSSI_RATIO_WEAK_SIGNAL_MULTIPLIER;
            case VERY_WEAK_SIGNAL:
                return RSSI_RATIO_VERY_WEAK_SIGNAL_MULTIPLIER;
            default:
                return RSSI_RATIO_MEDIUM_SIGNAL_MULTIPLIER;
        }
    }
    
    @Override
    public double getGeometricQualityMultiplier(GeometricQualityFactor factor) {
        switch (factor) {
            case EXCELLENT_GDOP:
                return RSSI_RATIO_EXCELLENT_GDOP_MULTIPLIER;
            case GOOD_GDOP:
                return RSSI_RATIO_GOOD_GDOP_MULTIPLIER;
            case FAIR_GDOP:
                return RSSI_RATIO_FAIR_GDOP_MULTIPLIER;
            case POOR_GDOP:
                return RSSI_RATIO_POOR_GDOP_MULTIPLIER;
            default:
                return RSSI_RATIO_GOOD_GDOP_MULTIPLIER;
        }
    }
    
    @Override
    public double getSignalDistributionMultiplier(SignalDistributionFactor factor) {
        switch (factor) {
            case UNIFORM_SIGNALS:
                return RSSI_RATIO_UNIFORM_SIGNALS_MULTIPLIER;
            case MIXED_SIGNALS:
                return RSSI_RATIO_MIXED_SIGNALS_MULTIPLIER;
            case SIGNAL_OUTLIERS:
                return RSSI_RATIO_SIGNAL_OUTLIERS_MULTIPLIER;
            default:
                return RSSI_RATIO_MIXED_SIGNALS_MULTIPLIER;
        }
    }
} 