package com.wifi.positioning.algorithm.selection;

import com.wifi.positioning.algorithm.factor.GeometricQualityFactor;
import com.wifi.positioning.algorithm.factor.SignalDistributionFactor;
import com.wifi.positioning.algorithm.factor.SignalQualityFactor;
import com.wifi.positioning.algorithm.util.GDOPCalculator;
import com.wifi.positioning.dto.WifiScanResult;
import com.wifi.positioning.dto.Position;
import com.wifi.positioning.model.WifiAccessPoint;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Default implementation of the ContextBuilder interface.
 * Evaluates geometry, signal quality, and location certainty to build a selection context.
 * 
 * The builder includes methods to determine:
 * - Geometric Quality Factor: Based on AP geometry/GDOP calculation
 * - Signal Distribution Factor: Based on standard deviation of signal strengths
 * - Signal Quality Factor: Based on average signal strength
 */
@Component
public class DefaultContextBuilder implements ContextBuilder {
    
    // Constants for weighted centroid calculation
    private static final double SIGNAL_WEIGHT_FACTOR = 10.0; // Base for signal weight (10^(dBm/10))
    private static final double MIN_AP_COUNT_FOR_GEOMETRY = 3; // Minimum APs needed for valid geometry
    
    
    @Override
    public SelectionContext buildContext(List<WifiScanResult> validScans, Map<String, WifiAccessPoint> apMap) {
        // Calculate selection factors
        GeometricQualityFactor geometricQuality = determineGeometricQuality(validScans, apMap);
        SignalQualityFactor signalQualityFactor = determineSignalQuality(validScans);
        SignalDistributionFactor distributionFactor = determineSignalDistribution(validScans);
        
        // Calculate AP count factor
        long apCount = validScans.stream()
            .map(WifiScanResult::macAddress)
            .distinct()
            .count();
        
        // Build context
        return SelectionContext.builder()
                .isCollinear(checkCollinearity(validScans, apMap))
                .geometricQuality(geometricQuality)
                .signalQuality(signalQualityFactor)
                .signalDistribution(distributionFactor)
                .apCountFactor(com.wifi.positioning.algorithm.factor.APCountFactor.fromCount((int)apCount))
                .build();
    }

    /**
     * Checks if the access points in the valid scans are collinear.
     * 
     * @param validScans List of valid WiFi scan results
     * @param apMap Map of known access points by MAC address
     * @return true if the access points are collinear, false otherwise
     */
    private boolean checkCollinearity(List<WifiScanResult> validScans, Map<String, WifiAccessPoint> apMap) {
        if (validScans.size() < 3) {
            return false;
        }
        
        return GeometricQualityFactor.isCollinear(
            validScans.stream()
                .map(scan -> apMap.get(scan.macAddress()))
                .filter(ap -> ap != null && ap.getLatitude() != null && ap.getLongitude() != null)
                .map(ap -> new Position(ap.getLatitude(), ap.getLongitude(), 0.0, 0.0, 0.0))
                .collect(Collectors.toList())
        );
    }

    /**
     * Determines the geometric quality factor based on AP positions.
     * 
     * This method uses GDOP (Geometric Dilution of Precision) to assess how the
     * geometric configuration of access points affects position accuracy:
     * - EXCELLENT_GDOP: GDOP < 2.0 (optimal AP geometry)
     * - GOOD_GDOP: 2.0 ≤ GDOP < 4.0 (good AP geometry)
     * - FAIR_GDOP: 4.0 ≤ GDOP < 6.0 (acceptable AP geometry)
     * - POOR_GDOP: GDOP ≥ 6.0 (poor AP geometry, including collinear arrangements)
     * 
     * The calculation includes:
     * 1. Estimating user position using weighted centroid (weights based on signal strength)
     * 2. Creating an array of AP coordinates
     * 3. Computing GDOP using the GDOPCalculator
     * 4. Mapping the GDOP value to the appropriate GeometricQualityFactor
     * 
     * @param wifiScans List of WiFi scan results
     * @param apMap Map of known access points by MAC address
     * @return The corresponding GeometricQualityFactor
     */
    public GeometricQualityFactor determineGeometricQuality(List<WifiScanResult> wifiScans, 
                                                             Map<String, WifiAccessPoint> apMap) {
        // Check if we have enough APs for a meaningful geometry calculation
        if (wifiScans == null || wifiScans.size() < MIN_AP_COUNT_FOR_GEOMETRY || apMap == null) {
            return GeometricQualityFactor.POOR_GDOP;
        }
        
        // Extract APs with known positions
        List<WifiAccessPoint> validAPs = wifiScans.stream()
            .map(scan -> apMap.get(scan.macAddress()))
            .filter(ap -> ap != null && ap.getLatitude() != null && ap.getLongitude() != null)
            .collect(Collectors.toList());
        
        // Create a map of MAC address to signal strength for weighting
        Map<String, Double> signalMap = wifiScans.stream()
            .collect(Collectors.toMap(
                WifiScanResult::macAddress,
                WifiScanResult::signalStrength,
                (a, b) -> a  // If duplicate keys, take the first one
            ));
        
        // Check if we have enough valid APs
        if (validAPs.size() < MIN_AP_COUNT_FOR_GEOMETRY) {
            return GeometricQualityFactor.POOR_GDOP;
        }


        // Calculate weighted centroid based on signal strength as an estimate for user position
        double totalWeight = 0, weightedLat = 0, weightedLon = 0;
        
        for (WifiAccessPoint ap : validAPs) {
            // Signal strength is negative, so we need to take power(10, signal/10) for proper weighting
            // Stronger signals (less negative) will have higher weights
            double signalStrength = signalMap.getOrDefault(ap.getMacAddress(), -80.0);
            double weight = Math.pow(SIGNAL_WEIGHT_FACTOR, signalStrength / 10.0);
            
            weightedLat += ap.getLatitude() * weight;
            weightedLon += ap.getLongitude() * weight;
            totalWeight += weight;
        }
        
        // Normalize weighted coordinates
        double[] estimatedPosition = new double[2];
        if (totalWeight > 0) {
            estimatedPosition[0] = weightedLat / totalWeight;
            estimatedPosition[1] = weightedLon / totalWeight;
        } else {
            // If weighting fails, use simple average
            estimatedPosition[0] = validAPs.stream().mapToDouble(WifiAccessPoint::getLatitude).average().orElse(0);
            estimatedPosition[1] = validAPs.stream().mapToDouble(WifiAccessPoint::getLongitude).average().orElse(0);
        }
        
        // Create array of AP coordinates for GDOP calculation
        double[][] apCoordinates = validAPs.stream()
            .map(ap -> new double[] { ap.getLatitude(), ap.getLongitude() })
            .toArray(double[][]::new);
        
        // Calculate GDOP (include bias term for 2D positioning)
        double gdop = GDOPCalculator.calculateGDOP(apCoordinates, estimatedPosition, true);
        
        // Map GDOP to GeometricQualityFactor
        return GeometricQualityFactor.fromGDOP(gdop);
    }
    

    
    /**
     * Determines the signal distribution factor based on signal strength variation.
     * 
     * This method assesses how signal strengths vary across different APs by calculating
     * the standard deviation of signal strengths and categorizing:
     * - UNIFORM_SIGNALS: standard deviation < 3.0 dB (signals are consistent)
     * - MIXED_SIGNALS: 3.0 ≤ standard deviation < 10.0 dB (moderate variation)
     * - SIGNAL_OUTLIERS: standard deviation ≥ 10.0 dB (significant variation/outliers)
     * 
     * @param wifiScans List of WiFi scan results
     * @return The corresponding SignalDistributionFactor
     */
    public SignalDistributionFactor determineSignalDistribution(List<WifiScanResult> wifiScans) {
        return SignalDistributionFactor.fromWifiScans(wifiScans);
    }
    
    /**
     * Determines the signal quality factor based on average signal strength.
     * 
     * This method assesses the overall signal quality by averaging signal strengths
     * and categorizing:
     * - STRONG_SIGNAL: average > -70 dBm (good signal quality)
     * - MEDIUM_SIGNAL: -70 dBm ≥ average ≥ -85 dBm (moderate signal quality)
     * - WEAK_SIGNAL: average < -85 dBm (poor signal quality)
     * 
     * @param wifiScans List of WiFi scan results
     * @return The corresponding SignalQualityFactor
     */
    public SignalQualityFactor determineSignalQuality(List<WifiScanResult> wifiScans) {
        return SignalQualityFactor.fromWifiScans(wifiScans);
    }

    private double evaluateAPLocationCertainty(List<WifiScanResult> validScans, 
                                            Map<String, WifiAccessPoint> apMap) {
        double totalConfidence = 0;
        
        for (WifiScanResult scan : validScans) {
            WifiAccessPoint ap = apMap.get(scan.macAddress());
            if (ap != null && ap.getConfidence() != null) {
                totalConfidence += ap.getConfidence();
            }
        }
        
        return validScans.size() > 0 ? totalConfidence / validScans.size() : 0;
    }
    
    /**
     * Calculate Haversine distance between two points in decimal degrees
     */
    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        final double R = 6371000; // Earth radius in meters
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                   Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                   Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }
} 