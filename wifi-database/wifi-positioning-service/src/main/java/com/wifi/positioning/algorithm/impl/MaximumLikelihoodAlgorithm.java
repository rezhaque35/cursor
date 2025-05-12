package com.wifi.positioning.algorithm.impl;

import com.wifi.positioning.algorithm.PositioningAlgorithm;
import com.wifi.positioning.algorithm.selection.factor.APCountFactor;
import com.wifi.positioning.algorithm.selection.factor.GeometricQualityFactor;
import com.wifi.positioning.algorithm.selection.factor.SignalDistributionFactor;
import com.wifi.positioning.algorithm.selection.factor.SignalQualityFactor;
import com.wifi.positioning.algorithm.util.GDOPCalculator;
import com.wifi.positioning.dto.Position;
import com.wifi.positioning.dto.WifiScanResult;
import com.wifi.positioning.dto.WifiAccessPoint;
import org.springframework.stereotype.Component;
import org.apache.commons.math3.linear.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.DoubleAdder;
import java.util.stream.Collectors;

/**
 * Implementation of Maximum Likelihood Estimation for WiFi positioning.
 * 
 * USE CASES:
 * - Best suited for environments with many APs (5+ APs)
 * - Effective in complex indoor environments with multipath
 * - Ideal when historical signal data is available
 * - Good for scenarios requiring high accuracy and confidence
 * 
 * STRENGTHS:
 * - Most accurate algorithm when sufficient data available
 * - Handles noisy measurements robustly
 * - Provides realistic confidence estimates
 * - Accounts for AP geometry quality via GDOP
 * - Adaptive signal variance estimation based on signal strength
 * 
 * WEAKNESSES:
 * - Computationally intensive
 * - Requires more measurements than simpler methods
 * - May converge slowly in some scenarios
 * - Higher memory usage for measurement models
 * 
 * TUNABLE PARAMETERS:
 * - GRID_RESOLUTION: Search space granularity (meters)
 * - MAX_ITERATIONS: Maximum gradient descent steps
 * - CONVERGENCE_THRESHOLD: Stop condition (meters)
 * - PATH_LOSS_EXPONENT: Signal propagation model
 * - MIN_CONFIDENCE: Lower bound for confidence values
 * - MAX_CONFIDENCE: Upper bound for confidence values
 * - GDOP_CONFIDENCE_WEIGHT: How much AP geometry affects confidence
 * - GDOP_ACCURACY_MULTIPLIER: How much AP geometry affects accuracy
 * 
 * MATHEMATICAL MODEL:
 * The algorithm maximizes P(position | measurements) using:
 * 
 * 1. Likelihood Function:
 *    L(pos) = Π P(measurement_i | pos)
 *    where each measurement probability is:
 *    P(RSSI | pos) = N(RSSI; μ(d), σ²)
 *    - μ(d) is expected RSSI at distance d
 *    - σ² is signal variance, adaptively calculated based on signal strength:
 *      * Strong signals (>-60 dBm): σ = 2.5 dBm
 *      * Medium signals (-60 to -80 dBm): σ = 4.0 dBm
 *      * Weak signals (<-80 dBm): σ = 6.0 dBm
 * 
 * 2. Log-Likelihood Maximization:
 *    LL(pos) = Σ log(P(measurement_i | pos))
 *    Gradient: ∇LL(pos) = Σ (RSSI_i - μ(d_i))/σ² * ∇d_i
 * 
 * 3. Position Update:
 *    pos_new = pos + α * ∇LL(pos)
 *    where α is adaptive learning rate
 * 
 * 4. Geometric Dilution of Precision (GDOP):
 *    GDOP = sqrt(trace((H^T * H)^-1))
 *    where:
 *    - H is the geometry matrix containing unit vectors from position to APs
 *    - H^T is the transpose of H
 *    - Lower GDOP values indicate better geometric AP distribution
 *    - Higher GDOP values indicate poorer AP distribution, reducing accuracy
 * 
 * 5. Accuracy Calculation:
 *    For strong signals:
 *    accuracy = baseAccuracy * (1.0 + (gdopFactor - 1.0) * GDOP_ACCURACY_MULTIPLIER)
 *    
 *    For weaker signals:
 *    accuracy = baseAccuracy * gdopFactor
 *    
 *    where:
 *    - baseAccuracy is either fixed (3.0m for strong signals) or distance-based
 *    - gdopFactor is a scaling value derived from GDOP
 * 
 * 6. Confidence Calculation:
 *    Base confidence is calculated as:
 *    confidence = MIN_CONF + (MAX_CONF - MIN_CONF) * 
 *                (0.7 * signalFactor + 0.3 * apCountFactor)
 *    
 *    Then adjusted for geometric quality:
 *    confidence = confidence * (1.0 - GDOP_WEIGHT * (1.0 - 1.0/gdopFactor))
 *    
 *    where:
 *    - signalFactor is based on average signal strength
 *    - apCountFactor is based on number of APs (3-8 scale)
 *    - gdopFactor reflects geometric quality of AP distribution
 */
@Component
public class MaximumLikelihoodAlgorithm implements PositioningAlgorithm {

    private static final double GRID_RESOLUTION = 1.0; // meters
    private static final int MAX_ITERATIONS = 100;
    private static final double CONVERGENCE_THRESHOLD = 0.1; // meters
    private static final double REFERENCE_DISTANCE = 1.0;
    private static final double REFERENCE_RSSI = -40.0;
    private static final double PATH_LOSS_EXPONENT = 3.0;
    private static final double EARTH_RADIUS = 6371000; // Earth radius in meters
    
    // Signal strength thresholds and corresponding standard deviations
    private static final double STRONG_SIGNAL_THRESHOLD = -60.0; // dBm
    private static final double WEAK_SIGNAL_THRESHOLD = -80.0;   // dBm
    private static final double STRONG_SIGNAL_STD_DEV = 2.5;    // Based on empirical studies for strong signals
    private static final double MEDIUM_SIGNAL_STD_DEV = 4.0;    // Default for typical indoor environments
    private static final double WEAK_SIGNAL_STD_DEV = 6.0;      // Higher uncertainty for weak signals
    
    // Constants for signal strength thresholds
    private static final double MIN_ACCURACY = 1.0; // meters, minimum accuracy value for strong signals
    private static final double MAX_ACCURACY = 5.0; // meters, maximum accuracy value for strong signals
    private static final double MIN_CONFIDENCE = 0.6;
    private static final double MAX_CONFIDENCE = 0.95;
    private static final double HIGH_CONFIDENCE = 0.8; // Minimum confidence for strong signals
    private static final double WEAK_CONFIDENCE_CAP = 0.65; // maximum confidence for weak signals

    /**
     * Weight constants from the algorithm selection framework.
     * These reflect the strengths and weaknesses of the Maximum Likelihood algorithm:
     * - Works best with 4+ APs (optimal with many APs)
     * - Highly dependent on signal quality (works best with strong signals)
     * - Moderately sensitive to geometric quality
     * - Extremely effective with mixed signals and outliers
     */
    // AP Count weights from framework document
    private static final double MAXIMUM_LIKELIHOOD_SINGLE_AP_WEIGHT = 0.0;    // Not applicable for single AP
    private static final double MAXIMUM_LIKELIHOOD_TWO_APS_WEIGHT = 0.0;      // Not applicable for two APs
    private static final double MAXIMUM_LIKELIHOOD_THREE_APS_WEIGHT = 0.0;    // Not applicable for three APs (needs more APs)
    private static final double MAXIMUM_LIKELIHOOD_FOUR_PLUS_APS_WEIGHT = 1.0;// Optimal for four+ APs
    
    // Signal quality multipliers from framework document
    private static final double MAXIMUM_LIKELIHOOD_STRONG_SIGNAL_MULTIPLIER = 1.2;  // Significant improvement with strong signals
    private static final double MAXIMUM_LIKELIHOOD_MEDIUM_SIGNAL_MULTIPLIER = 0.9;  // Slight reduction with medium signals
    private static final double MAXIMUM_LIKELIHOOD_WEAK_SIGNAL_MULTIPLIER = 0.5;    // Major reduction for weak signals
    private static final double MAXIMUM_LIKELIHOOD_VERY_WEAK_SIGNAL_MULTIPLIER = 0.0; // ×0.0 for very weak signals
    
    // Geometric quality multipliers from framework document
    private static final double MAXIMUM_LIKELIHOOD_EXCELLENT_GDOP_MULTIPLIER = 1.2; // Significant boost for excellent geometry
    private static final double MAXIMUM_LIKELIHOOD_GOOD_GDOP_MULTIPLIER = 1.1;      // Good boost for good geometry
    private static final double MAXIMUM_LIKELIHOOD_FAIR_GDOP_MULTIPLIER = 0.9;      // Some reduction for fair geometry
    private static final double MAXIMUM_LIKELIHOOD_POOR_GDOP_MULTIPLIER = 0.7;      // Significant reduction for poor geometry
    
    // Signal distribution multipliers from framework document
    private static final double MAXIMUM_LIKELIHOOD_UNIFORM_SIGNALS_MULTIPLIER = 0.9;  // Slightly reduced with uniform signals
    private static final double MAXIMUM_LIKELIHOOD_MIXED_SIGNALS_MULTIPLIER = 1.1;    // Some improvement with mixed signals
    private static final double MAXIMUM_LIKELIHOOD_SIGNAL_OUTLIERS_MULTIPLIER = 1.2;  // Significant improvement with outliers
    
    /**
     * Calculates position using Maximum Likelihood Estimation.
     * Process:
     * 1. Create initial position estimate using weighted centroid
     * 2. Build measurement models incorporating historical data
     * 3. Iteratively refine position using gradient descent
     * 4. Calculate confidence based on likelihood surface and convergence
     * 5. Apply GDOP analysis to refine accuracy and confidence
     *
     * @param wifiScan List of WiFi scan results containing signal strengths
     * @param knownAPs List of known access points with their locations
     * @return Calculated position with confidence metrics
     */
    @Override
    public Position calculatePosition(List<WifiScanResult> wifiScan, List<WifiAccessPoint> knownAPs) {
        if (wifiScan == null || wifiScan.isEmpty() || knownAPs == null || knownAPs.isEmpty()) {
            return null;
        }

        // Create AP lookup map for efficiency
        Map<String, WifiAccessPoint> apMap = knownAPs.parallelStream()
            .collect(Collectors.toConcurrentMap(
                WifiAccessPoint::getMacAddress,
                ap -> ap,
                (ap1, ap2) -> ap1 // In case of duplicates, keep the first one
            ));

        // Create initial estimate using weighted centroid
        Position initialEstimate = calculateInitialEstimate(wifiScan, apMap);
        if (initialEstimate == null) {
            return null;
        }

        // Create measurement model for each AP
        List<MeasurementModel> measurements = createMeasurementModels(wifiScan, apMap);
        if (measurements.isEmpty()) {
            return initialEstimate;
        }

        // Iteratively refine position using gradient descent
        Position currentPosition = initialEstimate;
        Position bestPosition = initialEstimate;
        double bestLikelihood = Double.NEGATIVE_INFINITY;
        double learningRate = 1.0;

        for (int i = 0; i < MAX_ITERATIONS; i++) {
            // Calculate gradient of log-likelihood
            double[] gradient = calculateLogLikelihoodGradient(currentPosition, measurements);
            
            // Update position
            Position newPosition = new Position(
                currentPosition.latitude() + learningRate * gradient[0],
                currentPosition.longitude() + learningRate * gradient[1],
                currentPosition.altitude() + learningRate * gradient[2],
                currentPosition.accuracy(),
                currentPosition.confidence()
            );

            // Calculate new likelihood
            double newLikelihood = calculateLogLikelihood(newPosition, measurements);

            // Update best position if likelihood improved
            if (newLikelihood > bestLikelihood) {
                bestLikelihood = newLikelihood;
                bestPosition = newPosition;
                currentPosition = newPosition;
            } else {
                learningRate *= 0.5;
            }

            // Check for convergence
            if (learningRate < CONVERGENCE_THRESHOLD) {
                break;
            }
        }

        // Prepare coordinates for GDOP calculation
        double[][] coordinates = new double[measurements.size()][3];
        for (int i = 0; i < measurements.size(); i++) {
            MeasurementModel ap = measurements.get(i);
            coordinates[i][0] = ap.lat;
            coordinates[i][1] = ap.lon;
            coordinates[i][2] = ap.alt;
        }
        
        // Calculate position as a double array for GDOP calculation
        double[] position = new double[] {
            bestPosition.latitude(),
            bestPosition.longitude(),
            bestPosition.altitude()
        };
        
        // Calculate GDOP using the GDOPCalculator utility
        double gdop = GDOPCalculator.calculateGDOP(coordinates, position, true);
        double gdopFactor = GDOPCalculator.calculateGDOPFactor(gdop);
        
        // Calculate average signal strength for accuracy and confidence calculations
        double avgSignalStrength = wifiScan.stream()
            .mapToDouble(WifiScanResult::signalStrength)
            .average()
            .orElse(-85.0);
        
        // Calculate accuracy using GDOP
        double refinedAccuracy = calculateAccuracy(bestPosition.accuracy(), gdopFactor, avgSignalStrength);
        
        // Calculate final confidence based on likelihood surface and GDOP
        double confidence = calculateConfidence(bestPosition, measurements, bestLikelihood, 
                                               gdopFactor, avgSignalStrength, wifiScan.size());

        return new Position(
            bestPosition.latitude(),
            bestPosition.longitude(),
            bestPosition.altitude(),
            refinedAccuracy,
            confidence
        );
    }

    /**
     * Calculates initial position estimate using weighted centroid method.
     * This provides a reasonable starting point for gradient descent.
     * Uses signal strength as weights, with stronger signals having more influence.
     * 
     * The initial accuracy estimate is based on:
     * 1. Average signal strength - stronger signals provide better accuracy
     * 2. Number of access points - more APs generally provide better accuracy
     * 3. Geometric distribution - better AP distribution improves accuracy
     * 
     * @param wifiScan List of WiFi scan results
     * @param apMap Map of known AP locations
     * @return Initial position estimate or null if calculation fails
     */
    private Position calculateInitialEstimate(List<WifiScanResult> wifiScan, Map<String, WifiAccessPoint> apMap) {
        // Use atomic accumulators for thread-safe calculations
        DoubleAdder totalWeight = new DoubleAdder();
        DoubleAdder weightedLat = new DoubleAdder();
        DoubleAdder weightedLon = new DoubleAdder();
        DoubleAdder weightedAlt = new DoubleAdder();
        DoubleAdder totalSignal = new DoubleAdder();

        // Process scans in parallel
        wifiScan.parallelStream().forEach(scan -> {
            WifiAccessPoint ap = apMap.get(scan.macAddress());
            if (ap == null) return;

            double weight = Math.pow(10, scan.signalStrength() / 10.0);
            weightedLat.add(ap.getLatitude() * weight);
            weightedLon.add(ap.getLongitude() * weight);
            weightedAlt.add(ap.getAltitude() * weight);
            totalWeight.add(weight);
            totalSignal.add(scan.signalStrength());
        });

        if (totalWeight.doubleValue() == 0) {
            return null;
        }
        
        // Calculate position
        double latitude = weightedLat.doubleValue() / totalWeight.doubleValue();
        double longitude = weightedLon.doubleValue() / totalWeight.doubleValue();
        double altitude = weightedAlt.doubleValue() / totalWeight.doubleValue();
        
        // Calculate average signal strength
        double avgSignalStrength = totalSignal.doubleValue() / wifiScan.size();
        
        // Create measurement models for GDOP calculation
        List<MeasurementModel> measurements = createMeasurementModels(wifiScan, apMap);
        Position initialPosition = new Position(latitude, longitude, altitude, 0.0, 0.0);
        
        // Prepare coordinates for GDOP calculation
        double[][] coordinates = new double[measurements.size()][3];
        for (int i = 0; i < measurements.size(); i++) {
            MeasurementModel ap = measurements.get(i);
            coordinates[i][0] = ap.lat;
            coordinates[i][1] = ap.lon;
            coordinates[i][2] = ap.alt;
        }
        
        // Calculate position as a double array for GDOP calculation
        double[] position = new double[] {
            initialPosition.latitude(),
            initialPosition.longitude(),
            initialPosition.altitude()
        };
        
        // Calculate GDOP using the GDOPCalculator utility
        double gdop = GDOPCalculator.calculateGDOP(coordinates, position, true);
        double gdopFactor = GDOPCalculator.calculateGDOPFactor(gdop);
        
        // Calculate initial accuracy
        double baseAccuracy;
        if (avgSignalStrength >= STRONG_SIGNAL_THRESHOLD) {
            // For strong signals, use a fixed base accuracy
            baseAccuracy = 3.0; // Middle of expected range for strong signals (1-5m)
        } else {
            // For weaker signals, use a signal-strength based accuracy
            baseAccuracy = 6.0 + Math.abs(avgSignalStrength + 70.0) * 0.2;
        }
        
        // Apply GDOP factor to accuracy
        double initialAccuracy;
        if (avgSignalStrength >= STRONG_SIGNAL_THRESHOLD) {
            // For strong signals, apply a controlled GDOP adjustment
            initialAccuracy = baseAccuracy * (1.0 + (gdopFactor - 1.0) * GDOPCalculator.GDOP_ACCURACY_MULTIPLIER);
        } else {
            // For weaker signals, apply full GDOP adjustment
            initialAccuracy = baseAccuracy * gdopFactor;
        }
        
        // Ensure accuracy is within reasonable bounds
        initialAccuracy = Math.max(MIN_ACCURACY, Math.min(25.0, initialAccuracy));
        
        // Initial confidence will be refined later
        double initialConfidence = 0.5;
        
        return new Position(latitude, longitude, altitude, initialAccuracy, initialConfidence);
    }

    /**
     * Creates measurement models for each AP observation.
     * Models incorporate:
     * 1. Historical signal strength patterns
     * 2. AP-specific standard deviations
     * 3. Location confidence from AP database
     * 
     * @param wifiScan List of WiFi scan results
     * @param apMap Map of known AP locations
     * @return List of measurement models for gradient descent
     */
    private List<MeasurementModel> createMeasurementModels(List<WifiScanResult> wifiScan, Map<String, WifiAccessPoint> apMap) {
        // Create measurement models in parallel
        return wifiScan.parallelStream()
            .map(scan -> {
                WifiAccessPoint ap = apMap.get(scan.macAddress());
                if (ap == null) return null;

                double stdDev = calculateAdaptiveStdDev(scan.signalStrength());

                return new MeasurementModel(
                    ap.getLatitude(),
                    ap.getLongitude(),
                    ap.getAltitude(),
                    scan.signalStrength(),
                    stdDev,
                    ap.getConfidence()
                );
            })
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }

    /**
     * Calculates the gradient of the log-likelihood function.
     * This drives the gradient descent optimization by:
     * 1. Computing partial derivatives for each coordinate
     * 2. Weighting contributions by measurement confidence
     * 3. Combining gradients from all measurements
     * 
     * @param position Current position estimate
     * @param measurements List of measurement models
     * @return Gradient vector [dLat, dLon, dAlt]
     */
    private double[] calculateLogLikelihoodGradient(Position position, List<MeasurementModel> measurements) {
        // These arrays will be updated by multiple threads, so we need thread-safe handling
        double[] gradient = new double[3];
        final Object lock = new Object();
        
        measurements.parallelStream().forEach(m -> {
            double distance = calculateDistance(
                position.latitude(), position.longitude(), position.altitude(),
                m.lat, m.lon, m.alt
            );
            
            double expectedRSSI = calculateExpectedRSSI(distance);
            double error = m.rssi - expectedRSSI;
            
            // Partial derivatives
            double scale = error / (m.stdDev * m.stdDev * distance);
            double gradLat = scale * (position.latitude() - m.lat) * m.confidence;
            double gradLon = scale * (position.longitude() - m.lon) * m.confidence;
            double gradAlt = scale * (position.altitude() - m.alt) * m.confidence;
            
            // Thread-safe update of gradient array
            synchronized(lock) {
                gradient[0] += gradLat;
                gradient[1] += gradLon;
                gradient[2] += gradAlt;
            }
        });
        
        return gradient;
    }

    /**
     * Calculates the log-likelihood of a position given the measurements.
     * Higher values indicate better fit to the measurements.
     * Incorporates:
     * 1. Signal strength error terms
     * 2. Measurement standard deviations
     * 3. AP location confidence weights
     * 
     * @param position Position to evaluate
     * @param measurements List of measurement models
     * @return Log-likelihood value
     */
    private double calculateLogLikelihood(Position position, List<MeasurementModel> measurements) {
        // Use atomic accumulator for thread-safe summation
        DoubleAdder logLikelihood = new DoubleAdder();
        
        measurements.parallelStream().forEach(m -> {
            double distance = calculateDistance(
                position.latitude(), position.longitude(), position.altitude(),
                m.lat, m.lon, m.alt
            );
            
            double expectedRSSI = calculateExpectedRSSI(distance);
            double error = m.rssi - expectedRSSI;
            
            logLikelihood.add(-(error * error) / (2 * m.stdDev * m.stdDev) * m.confidence);
        });
        
        return logLikelihood.doubleValue();
    }

    /**
     * Calculates confidence based on multiple factors:
     * 1. Signal strength quality
     * 2. Number of access points 
     * 3. Likelihood convergence quality
     * 4. Geometric distribution of APs (GDOP)
     * 
     * The base confidence is calculated from signal strength and AP count:
     * confidence = MIN_CONF + (MAX_CONF - MIN_CONF) * (0.7 * signalFactor + 0.3 * apCountFactor)
     * 
     * Then adjusted for geometric quality:
     * confidence = confidence * (1.0 - GDOP_WEIGHT * (1.0 - 1.0/gdopFactor))
     * 
     * @param position Final position estimate
     * @param measurements List of measurement models
     * @param maxLikelihood Best achieved likelihood value
     * @param gdopFactor GDOP factor indicating geometric quality
     * @param avgSignalStrength Average signal strength (dBm)
     * @param apCount Number of APs used in calculation
     * @return Confidence value between 0 and 1
     */
    private double calculateConfidence(Position position, List<MeasurementModel> measurements, 
                                     double maxLikelihood, double gdopFactor, 
                                     double avgSignalStrength, int apCount) {
        double confidence;
        
        // Calculate signal quality factor (0-1)
        double signalFactor;
        if (avgSignalStrength >= STRONG_SIGNAL_THRESHOLD) {
            // Strong signals provide high confidence
            signalFactor = Math.min(1.0, Math.max(0.0, 
                           (avgSignalStrength - WEAK_SIGNAL_THRESHOLD) / 
                           (STRONG_SIGNAL_THRESHOLD - WEAK_SIGNAL_THRESHOLD)));
        } else {
            // Weaker signals provide lower confidence
            signalFactor = Math.min(1.0, Math.max(0.0, 
                           (avgSignalStrength - (-100.0)) / 
                           (WEAK_SIGNAL_THRESHOLD - (-100.0))));
        }
        
        // Calculate AP count factor (0-1)
        double apCountFactor = Math.min(1.0, (apCount - 2) / 6.0); // 3-8 APs scale
        
        // Calculate convergence quality from likelihood
        double likelihoodFactor = 0.7;
        if (!Double.isInfinite(maxLikelihood) && !Double.isNaN(maxLikelihood)) {
            likelihoodFactor = Math.min(1.0, Math.max(0.0, 
                               (Math.exp(maxLikelihood / measurements.size()) - 0.1) / 0.9));
        }
        
        // Combine factors with appropriate weights
        if (avgSignalStrength >= STRONG_SIGNAL_THRESHOLD) {
            // For strong signals, ensure confidence is high
            double baseConfidence = HIGH_CONFIDENCE + 
                                  (MAX_CONFIDENCE - HIGH_CONFIDENCE) * 
                                  (0.6 * signalFactor + 0.3 * apCountFactor + 0.1 * likelihoodFactor);
            
            // Apply GDOP adjustment - only minor for strong signals
            confidence = baseConfidence * (1.0 - GDOPCalculator.GDOP_CONFIDENCE_WEIGHT * (1.0 - 1.0/Math.max(1.0, gdopFactor)));
            
            // Ensure confidence remains high for strong signals
            confidence = Math.max(HIGH_CONFIDENCE, Math.min(MAX_CONFIDENCE, confidence));
        } else if (avgSignalStrength < WEAK_SIGNAL_THRESHOLD) {
            // For weak signals, ensure confidence is lower
            double baseConfidence = MIN_CONFIDENCE + 
                                  (WEAK_CONFIDENCE_CAP - MIN_CONFIDENCE) * 
                                  (0.7 * signalFactor + 0.2 * apCountFactor + 0.1 * likelihoodFactor);
            
            // Apply stronger GDOP adjustment for weak signals
            confidence = baseConfidence * (1.0 - GDOPCalculator.GDOP_CONFIDENCE_WEIGHT * (1.0 - 1.0/Math.max(1.0, gdopFactor)));
            
            // Ensure confidence is capped for weak signals
            confidence = Math.min(WEAK_CONFIDENCE_CAP, confidence);
        } else {
            // For medium signals
            double baseConfidence = WEAK_CONFIDENCE_CAP + 
                                  (HIGH_CONFIDENCE - WEAK_CONFIDENCE_CAP) * 
                                  (0.6 * signalFactor + 0.25 * apCountFactor + 0.15 * likelihoodFactor);
            
            // Apply balanced GDOP adjustment
            confidence = baseConfidence * (1.0 - GDOPCalculator.GDOP_CONFIDENCE_WEIGHT * (1.0 - 1.0/Math.max(1.0, gdopFactor)));
        }
        
        return confidence;
    }

    /**
     * Calculates expected RSSI at a given distance using path loss model.
     * 
     * @param distance Distance in meters
     * @return Expected RSSI in dBm
     */
    private double calculateExpectedRSSI(double distance) {
        return REFERENCE_RSSI - 10 * PATH_LOSS_EXPONENT * Math.log10(distance / REFERENCE_DISTANCE);
    }

    /**
     * Calculates 3D distance between two points using Haversine formula.
     * Accounts for Earth's curvature in horizontal distance.
     * 
     * @param lat1 First point latitude
     * @param lon1 First point longitude
     * @param alt1 First point altitude
     * @param lat2 Second point latitude
     * @param lon2 Second point longitude
     * @param alt2 Second point altitude
     * @return 3D distance in meters
     */
    private double calculateDistance(double lat1, double lon1, double alt1, 
                                   double lat2, double lon2, double alt2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat/2) * Math.sin(dLat/2) +
                  Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                  Math.sin(dLon/2) * Math.sin(dLon/2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
        double horizontalDist = EARTH_RADIUS * c;
        double verticalDist = alt2 - alt1;
        
        return Math.sqrt(horizontalDist * horizontalDist + verticalDist * verticalDist);
    }

    @Override
    public double getConfidence() {
        return HIGH_CONFIDENCE; // Base confidence for maximum likelihood method
    }

    @Override
    public String getName() {
        return "maximum_likelihood";
    }

    @Override
    public double getSignalDistributionMultiplier(SignalDistributionFactor factor) {
        switch (factor) {
            case UNIFORM_SIGNALS:
                return MAXIMUM_LIKELIHOOD_UNIFORM_SIGNALS_MULTIPLIER;
            case MIXED_SIGNALS:
                return MAXIMUM_LIKELIHOOD_MIXED_SIGNALS_MULTIPLIER;
            case SIGNAL_OUTLIERS:
                return MAXIMUM_LIKELIHOOD_SIGNAL_OUTLIERS_MULTIPLIER;
            default:
                return MAXIMUM_LIKELIHOOD_MIXED_SIGNALS_MULTIPLIER;
        }
    }

    @Override
    public double getBaseWeight(APCountFactor factor) {
        switch (factor) {
            case SINGLE_AP:
                return MAXIMUM_LIKELIHOOD_SINGLE_AP_WEIGHT;      // Not applicable for single AP
            case TWO_APS:
                return MAXIMUM_LIKELIHOOD_TWO_APS_WEIGHT;        // Not applicable for two APs
            case THREE_APS:
                return MAXIMUM_LIKELIHOOD_THREE_APS_WEIGHT;      // Not applicable for three APs
            case FOUR_PLUS_APS:
                return MAXIMUM_LIKELIHOOD_FOUR_PLUS_APS_WEIGHT;  // Optimal for four+ APs
            default:
                return 0.0;
        }
    }
    
    @Override
    public double getSignalQualityMultiplier(SignalQualityFactor factor) {
        switch (factor) {
            case STRONG_SIGNAL:
                return MAXIMUM_LIKELIHOOD_STRONG_SIGNAL_MULTIPLIER;
            case MEDIUM_SIGNAL:
                return MAXIMUM_LIKELIHOOD_MEDIUM_SIGNAL_MULTIPLIER;
            case WEAK_SIGNAL:
                return MAXIMUM_LIKELIHOOD_WEAK_SIGNAL_MULTIPLIER;
            case VERY_WEAK_SIGNAL:
                return MAXIMUM_LIKELIHOOD_VERY_WEAK_SIGNAL_MULTIPLIER;
            default:
                return MAXIMUM_LIKELIHOOD_MEDIUM_SIGNAL_MULTIPLIER;
        }
    }
    
    @Override
    public double getGeometricQualityMultiplier(GeometricQualityFactor factor) {
        switch (factor) {
            case EXCELLENT_GDOP:
                return MAXIMUM_LIKELIHOOD_EXCELLENT_GDOP_MULTIPLIER;
            case GOOD_GDOP:
                return MAXIMUM_LIKELIHOOD_GOOD_GDOP_MULTIPLIER;
            case FAIR_GDOP:
                return MAXIMUM_LIKELIHOOD_FAIR_GDOP_MULTIPLIER;
            case POOR_GDOP:
                return MAXIMUM_LIKELIHOOD_POOR_GDOP_MULTIPLIER;
            default:
                return MAXIMUM_LIKELIHOOD_GOOD_GDOP_MULTIPLIER;
        }
    }

    /**
     * Measurement model for a single access point.
     * Encapsulates all information needed for likelihood calculation:
     * - AP location (lat, lon, alt)
     * - Signal strength (RSSI)
     * - Measurement uncertainty (stdDev)
     * - Location confidence from database
     */
    private static class MeasurementModel {
        final double lat;
        final double lon;
        final double alt;
        final double rssi;
        final double stdDev;
        final double confidence;

        MeasurementModel(double lat, double lon, double alt, double rssi, 
                       double stdDev, double confidence) {
            this.lat = lat;
            this.lon = lon;
            this.alt = alt;
            this.rssi = rssi;
            this.stdDev = stdDev;
            this.confidence = confidence;
        }
    }

    /**
     * Calculates accuracy based on base accuracy value, GDOP factor, and signal strength.
     * The calculation differs for strong vs. weak signals:
     * 
     * For strong signals:
     *   accuracy = baseAccuracy * (1.0 + (gdopFactor - 1.0) * GDOP_ACCURACY_MULTIPLIER)
     * 
     * For weaker signals:
     *   accuracy = baseAccuracy * gdopFactor
     * 
     * This ensures that GDOP has a controlled effect on strong signals (maintaining high accuracy)
     * while having a stronger impact on weak signals (where geometry is more critical).
     * 
     * @param baseAccuracy The initial accuracy estimate
     * @param gdopFactor The GDOP factor (1.0-4.0)
     * @param avgSignalStrength Average signal strength (dBm)
     * @return Refined accuracy value (meters)
     */
    private double calculateAccuracy(double baseAccuracy, double gdopFactor, double avgSignalStrength) {
        double refinedAccuracy;
        
        if (avgSignalStrength >= STRONG_SIGNAL_THRESHOLD) {
            // For strong signals, apply a controlled GDOP adjustment
            refinedAccuracy = baseAccuracy * (1.0 + (gdopFactor - 1.0) * GDOPCalculator.GDOP_ACCURACY_MULTIPLIER);
            
            // Ensure accuracy is within expected range for strong signals
            refinedAccuracy = Math.max(MIN_ACCURACY, Math.min(MAX_ACCURACY, refinedAccuracy));
        } else {
            // For weaker signals, apply full GDOP adjustment
            refinedAccuracy = baseAccuracy * gdopFactor;
            
            // Cap maximum accuracy value
            refinedAccuracy = Math.min(25.0, refinedAccuracy);
        }
        
        return refinedAccuracy;
    }

    /**
     * Calculates adaptive standard deviation based on signal strength.
     * 
     * @param signalStrength Signal strength in dBm
     * @return Adaptive standard deviation in dBm
     */
    private double calculateAdaptiveStdDev(double signalStrength) {
        if (signalStrength >= STRONG_SIGNAL_THRESHOLD) {
            return STRONG_SIGNAL_STD_DEV;
        } else if (signalStrength >= WEAK_SIGNAL_THRESHOLD) {
            return MEDIUM_SIGNAL_STD_DEV;
        } else {
            return WEAK_SIGNAL_STD_DEV;
        }
    }
} 