package com.wifi.positioning.algorithm.impl;

import com.wifi.positioning.algorithm.PositioningAlgorithm;
import com.wifi.positioning.dto.Position;
import com.wifi.positioning.dto.WifiScanResult;
import com.wifi.positioning.model.WifiAccessPoint;
import org.springframework.stereotype.Component;
import org.apache.commons.math3.linear.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.DoubleAdder;
import java.util.stream.Collectors;

/**
 * Implementation of the Trilateration positioning algorithm.
 * 
 * USE CASES:
 * - Best suited for environments with 3+ well-distributed APs
 * - Effective when accurate signal strength to distance conversion is possible
 * - Ideal for open spaces with minimal signal interference
 * - Good for scenarios requiring high position accuracy
 * 
 * STRENGTHS:
 * - High accuracy when AP geometry is good
 * - Mathematically rigorous position calculation
 * - Works well with strong, stable signals
 * - Provides accurate 3D positioning
 * 
 * WEAKNESSES:
 * - Requires at least 3 APs for position calculation
 * - Sensitive to AP geometry (collinear APs cause issues)
 * - Accuracy degrades with noisy signals
 * - Computationally more intensive than simpler methods
 * 
 * TUNABLE PARAMETERS:
 * - PATH_LOSS_EXPONENT: Signal degradation model (2.0-4.0)
 * - MIN_CONFIDENCE: Lower bound for confidence values
 * - MAX_CONFIDENCE: Upper bound for confidence values
 * - CONFIDENCE_THRESHOLD: Signal strength threshold for confidence
 * - Distance limits for reasonable position estimates
 * 
 * MATHEMATICAL MODEL:
 * The algorithm uses the following steps:
 * 
 * 1. Distance Estimation:
 *    d = d0 * 10^((RSSI0 - RSSI)/(10 * n))
 *    where:
 *    - d is the estimated distance
 *    - d0 is the reference distance (1m)
 *    - RSSI0 is the signal strength at d0
 *    - n is the path loss exponent
 * 
 * 2. Position Calculation:
 *    Uses least squares optimization to solve:
 *    (x - x1)² + (y - y1)² = d1²
 *    (x - x2)² + (y - y2)² = d2²
 *    (x - x3)² + (y - y3)² = d3²
 *    where:
 *    - (x,y) is the unknown position
 *    - (xi,yi) are AP positions
 *    - di are estimated distances
 * 
 * 3. Confidence Calculation:
 *    confidence = MIN_CONF + (MAX_CONF - MIN_CONF) * 
 *                (0.7 * signalFactor + 0.3 * apCountFactor)
 *    where:
 *    - signalFactor is based on average signal strength
 *    - apCountFactor is based on number of APs (3-8 scale)
 */
@Component
public class TrilaterationAlgorithm implements PositioningAlgorithm {

    private static final double REFERENCE_DISTANCE = 1.0; // 1 meter reference distance
    private static final double PATH_LOSS_EXPONENT = 3.0; // Path loss exponent for indoor environments (2.0-4.0)
    private static final double MIN_CONFIDENCE = 0.55;
    private static final double MAX_CONFIDENCE = 0.85;
    private static final double CONFIDENCE_THRESHOLD = -75.0; // dBm
    private static final String ALGORITHM_NAME = "trilateration";
    private static final double SPEED_OF_LIGHT = 299792458.0; // meters per second
    private static final double MIN_DISTANCE = 1.0; // minimum distance in meters
    private static final double MAX_DISTANCE = 100.0; // maximum distance in meters

    /**
     * Helper class to store coordinate calculations for each AP.
     * Used to cache intermediate results and improve performance.
     */
    private static class CachedCoordinates {
        final double x;
        final double y;
        final double distance;

        CachedCoordinates(double x, double y, double distance) {
            this.x = x;
            this.y = y;
            this.distance = distance;
        }
    }

    /**
     * Calculates position using trilateration with least squares optimization.
     * Process:
     * 1. Validate inputs and create AP lookup map
     * 2. Calculate distances from signal strengths
     * 3. Convert to local coordinate system
     * 4. Apply least squares trilateration
     * 5. Convert back to geographic coordinates
     * 6. Calculate confidence based on signal quality and AP count
     *
     * @param wifiScan List of WiFi scan results containing signal strengths
     * @param knownAPs List of known access points with their locations
     * @return Calculated position with confidence metrics
     */
    @Override
    public Position calculatePosition(List<WifiScanResult> wifiScan, List<WifiAccessPoint> knownAPs) {
        // Input validation
        if (wifiScan == null || wifiScan.isEmpty() || knownAPs == null || knownAPs.isEmpty()) {
            return null;
        }

        // We need at least 3 APs for trilateration
        if (wifiScan.size() < 3 || knownAPs.size() < 3) {
            return null;
        }

        // Create map of MAC addresses to known APs for quick lookup using concurrent map
        Map<String, WifiAccessPoint> apMap = knownAPs.parallelStream()
            .collect(Collectors.toConcurrentMap(
                WifiAccessPoint::getMacAddress,
                ap -> ap,
                (ap1, ap2) -> ap1 // In case of duplicates, keep the first one
            ));

        // Filter scans to only include APs we know the location of - using parallel stream
        List<WifiScanResult> validScans = wifiScan.parallelStream()
            .filter(scan -> apMap.containsKey(scan.macAddress()))
            .collect(Collectors.toList());

        if (validScans.size() < 3) {
            return null;
        }

        // Find the AP with the strongest signal to use as reference point
        WifiScanResult strongestSignalScan = validScans.stream()
            .max(java.util.Comparator.comparingDouble(WifiScanResult::signalStrength))
            .orElse(validScans.get(0));
        
        // Calculate distances and cache coordinates using concurrent collections
        Map<String, CachedCoordinates> coordinateCache = new ConcurrentHashMap<>();
        DoubleAdder totalSignalStrength = new DoubleAdder();
        DoubleAdder totalDistance = new DoubleAdder();

        // Use the strongest AP as reference point instead of first one
        WifiAccessPoint refAP = apMap.get(strongestSignalScan.macAddress());
        double refLat = refAP.getLatitude();
        double refLon = refAP.getLongitude();
        
        // Conversion factors
        double latToMeters = 111000.0;
        double lonToMeters = 111000.0 * Math.cos(Math.toRadians(refLat));

        // Process scans in parallel
        validScans.parallelStream().forEach(scan -> {
            WifiAccessPoint ap = apMap.get(scan.macAddress());
            double distance = calculateDistanceFromRSSI(scan.signalStrength(), scan.frequency());
            
            // Convert to local coordinates
            double x = (ap.getLatitude() - refLat) * latToMeters;
            double y = (ap.getLongitude() - refLon) * lonToMeters;
            
            coordinateCache.put(scan.macAddress(), new CachedCoordinates(x, y, distance));
            totalSignalStrength.add(scan.signalStrength());
            totalDistance.add(distance);
        });

        // Apply trilateration using least squares method with Apache Commons Math
        double[] position = leastSquaresTrilateration(validScans, coordinateCache);
        
        // If trilateration fails or produces unreasonable results, fall back to centroid method
        if (position == null || Double.isNaN(position[0]) || Double.isNaN(position[1]) ||
            Double.isInfinite(position[0]) || Double.isInfinite(position[1])) {
            // Fall back to weighted centroid calculation
            DoubleAdder weightedLatSum = new DoubleAdder();
            DoubleAdder weightedLonSum = new DoubleAdder();
            DoubleAdder weightSum = new DoubleAdder();
            
            validScans.parallelStream().forEach(scan -> {
                WifiAccessPoint ap = apMap.get(scan.macAddress());
                // Use signal strength as weight - stronger signals get higher weight
                double weight = Math.pow(10, scan.signalStrength() / 10.0);
                weightedLatSum.add(ap.getLatitude() * weight);
                weightedLonSum.add(ap.getLongitude() * weight);
                weightSum.add(weight);
            });
            
            double totalWeight = weightSum.doubleValue();
            if (totalWeight > 0) {
                double lat = weightedLatSum.doubleValue() / totalWeight;
                double lon = weightedLonSum.doubleValue() / totalWeight;
                position = new double[]{(lat - refLat) * latToMeters, (lon - refLon) * lonToMeters};
            } else {
                return null;
            }
        }
        
        // Convert back to latitude/longitude
        double latitude = refLat + position[0] / latToMeters;
        double longitude = refLon + position[1] / lonToMeters;
        
        // Estimate altitude as weighted average of AP altitudes
        DoubleAdder weightedAltitude = new DoubleAdder();
        DoubleAdder weightSum = new DoubleAdder();
        
        // Process altitude calculation in parallel
        validScans.parallelStream().forEach(scan -> {
            WifiAccessPoint ap = apMap.get(scan.macAddress());
            CachedCoordinates coords = coordinateCache.get(scan.macAddress());
            double weight = 1.0 / coords.distance;
            weightedAltitude.add(ap.getAltitude() * weight);
            weightSum.add(weight);
        });
        
        double altitude = weightSum.doubleValue() > 0 ? 
            weightedAltitude.doubleValue() / weightSum.doubleValue() : 0.0;
        
        // Calculate average accuracy based on known APs and signal strength
        double avgAccuracy = totalDistance.doubleValue() / validScans.size();
        
        // Calculate confidence based on signal strength and number of APs
        double avgSignalStrength = totalSignalStrength.doubleValue() / validScans.size();
        double signalFactor = Math.min(1.0, Math.max(0.0, (avgSignalStrength - (-100)) / (CONFIDENCE_THRESHOLD - (-100))));
        double apCountFactor = Math.min(1.0, (validScans.size() - 2) / 6.0); // 3-8 APs scale
        
        double confidence = MIN_CONFIDENCE + (MAX_CONFIDENCE - MIN_CONFIDENCE) * 
                           (0.7 * signalFactor + 0.3 * apCountFactor);
        
        return new Position(latitude, longitude, altitude, avgAccuracy, confidence);
    }

    /**
     * Calculates distance from RSSI using the log-distance path loss model.
     * The model accounts for:
     * 1. Free space path loss at reference distance
     * 2. Signal frequency effects on wavelength
     * 3. Environmental path loss characteristics
     * 4. Distance constraints for reasonable estimates
     *
     * @param rssi Signal strength in dBm
     * @param frequency Frequency in MHz
     * @return Estimated distance in meters
     */
    private double calculateDistanceFromRSSI(double rssi, int frequency) {
        // Calculate reference power at 1m distance using free space path loss formula
        double wavelength = SPEED_OF_LIGHT / (frequency * 1000.0); // Speed of light / frequency in Hz
        double referenceRSSI = -20.0 * Math.log10(4.0 * Math.PI * REFERENCE_DISTANCE / wavelength);
        
        // Apply log-distance path loss model
        double pathLoss = referenceRSSI - rssi;
        double distance = REFERENCE_DISTANCE * Math.pow(10, pathLoss / (10 * PATH_LOSS_EXPONENT));
        
        // Limit the distance to a reasonable range
        return Math.min(MAX_DISTANCE, Math.max(MIN_DISTANCE, distance));
    }

    /**
     * Implements trilateration using least squares method.
     * The method:
     * 1. Creates a system of equations from AP positions and distances
     * 2. Linearizes the equations by subtracting reference AP equation
     * 3. Solves using QR decomposition for numerical stability
     * 4. Handles singular matrices and numerical errors gracefully
     *
     * @param validScans Filtered WiFi scan results
     * @param coordinateCache Map of cached coordinates and distances
     * @return [latitude, longitude] coordinates or null if calculation fails
     */
    private double[] leastSquaresTrilateration(List<WifiScanResult> validScans, Map<String, CachedCoordinates> coordinateCache) {
        int n = validScans.size();
        
        if (n < 3) {
            return null;
        }
        
        // Reference point is the first AP
        CachedCoordinates ref = coordinateCache.get(validScans.get(0).macAddress());
        
        // Create matrices for least squares calculation
        double[][] matrixData = new double[n-1][2];
        double[] constants = new double[n-1];
        
        // Matrix population can't be easily parallelized due to index dependencies
        for (int i = 1; i < n; i++) {
            CachedCoordinates coords = coordinateCache.get(validScans.get(i).macAddress());
            
            matrixData[i-1][0] = 2 * coords.x;
            matrixData[i-1][1] = 2 * coords.y;
            
            constants[i-1] = Math.pow(coords.distance, 2) - Math.pow(ref.distance, 2) - 
                            Math.pow(coords.x, 2) - Math.pow(coords.y, 2);
        }
        
        // Use Apache Commons Math for matrix operations
        RealMatrix A = new Array2DRowRealMatrix(matrixData);
        RealVector b = new ArrayRealVector(constants);
        
        try {
            // Use QR decomposition for better numerical stability
            DecompositionSolver solver = new QRDecomposition(A).getSolver();
            
            if (!solver.isNonSingular()) {
                return null;
            }
            
            RealVector solution = solver.solve(b);
            return solution.toArray();
        } catch (Exception e) {
            // If matrix operations fail, return null
            return null;
        }
    }

    @Override
    public double getConfidence() {
        return MAX_CONFIDENCE;
    }

    @Override
    public String getName() {
        return ALGORITHM_NAME;
    }
} 