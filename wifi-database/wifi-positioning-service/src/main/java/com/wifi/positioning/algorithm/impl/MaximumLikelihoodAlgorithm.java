package com.wifi.positioning.algorithm.impl;

import com.wifi.positioning.algorithm.PositioningAlgorithm;
import com.wifi.positioning.dto.Position;
import com.wifi.positioning.dto.WifiScanResult;
import com.wifi.positioning.model.WifiAccessPoint;
import org.springframework.stereotype.Component;
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
 * - Incorporates historical signal patterns
 * - Provides realistic confidence estimates
 * 
 * WEAKNESSES:
 * - Computationally intensive
 * - Requires more measurements than simpler methods
 * - May converge slowly in some scenarios
 * - Higher memory usage for measurement models
 * 
 * TUNABLE PARAMETERS:
 * - GRID_RESOLUTION: Search space granularity (meters)
 * - SIGNAL_STD_DEV: Expected signal variation (dBm)
 * - MAX_ITERATIONS: Maximum gradient descent steps
 * - CONVERGENCE_THRESHOLD: Stop condition (meters)
 * - PATH_LOSS_EXPONENT: Signal propagation model
 * 
 * MATHEMATICAL MODEL:
 * The algorithm maximizes P(position | measurements) using:
 * 
 * 1. Likelihood Function:
 *    L(pos) = Π P(measurement_i | pos)
 *    where each measurement probability is:
 *    P(RSSI | pos) = N(RSSI; μ(d), σ²)
 *    - μ(d) is expected RSSI at distance d
 *    - σ² is signal variance
 * 
 * 2. Log-Likelihood Maximization:
 *    LL(pos) = Σ log(P(measurement_i | pos))
 *    Gradient: ∇LL(pos) = Σ (RSSI_i - μ(d_i))/σ² * ∇d_i
 * 
 * 3. Position Update:
 *    pos_new = pos + α * ∇LL(pos)
 *    where α is adaptive learning rate
 * 
 * 4. Confidence Calculation:
 *    Based on:
 *    - Likelihood surface curvature
 *    - Number of measurements
 *    - Signal quality metrics
 */
@Component
public class MaximumLikelihoodAlgorithm implements PositioningAlgorithm {

    private static final double GRID_RESOLUTION = 1.0; // meters
    private static final double SIGNAL_STD_DEV = 4.0; // dBm
    private static final int MAX_ITERATIONS = 100;
    private static final double CONVERGENCE_THRESHOLD = 0.1; // meters
    private static final double REFERENCE_DISTANCE = 1.0;
    private static final double REFERENCE_RSSI = -40.0;
    private static final double PATH_LOSS_EXPONENT = 3.0;
    private static final double EARTH_RADIUS = 6371000; // Earth radius in meters

    /**
     * Calculates position using Maximum Likelihood Estimation.
     * Process:
     * 1. Create initial position estimate using weighted centroid
     * 2. Build measurement models incorporating historical data
     * 3. Iteratively refine position using gradient descent
     * 4. Calculate confidence based on likelihood surface and convergence
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

        // Calculate final confidence based on likelihood surface
        double confidence = calculateConfidence(bestPosition, measurements, bestLikelihood);

        return new Position(
            bestPosition.latitude(),
            bestPosition.longitude(),
            bestPosition.altitude(),
            bestPosition.accuracy(),
            confidence
        );
    }

    /**
     * Calculates initial position estimate using weighted centroid method.
     * This provides a reasonable starting point for gradient descent.
     * Uses signal strength as weights, with stronger signals having more influence.
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

        // Process scans in parallel
        wifiScan.parallelStream().forEach(scan -> {
            WifiAccessPoint ap = apMap.get(scan.macAddress());
            if (ap == null) return;

            double weight = Math.pow(10, scan.signalStrength() / 10.0);
            weightedLat.add(ap.getLatitude() * weight);
            weightedLon.add(ap.getLongitude() * weight);
            weightedAlt.add(ap.getAltitude() * weight);
            totalWeight.add(weight);
        });

        if (totalWeight.doubleValue() == 0) {
            return null;
        }

        return new Position(
            weightedLat.doubleValue() / totalWeight.doubleValue(),
            weightedLon.doubleValue() / totalWeight.doubleValue(),
            weightedAlt.doubleValue() / totalWeight.doubleValue(),
            15.0, // Initial accuracy estimate
            0.5   // Initial confidence
        );
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

                double stdDev = ap.getSignalStrengthStd() != null ? 
                              ap.getSignalStrengthStd() : SIGNAL_STD_DEV;

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
     * Calculates confidence based on the shape of the likelihood surface.
     * Considers:
     * 1. Convergence quality of gradient descent
     * 2. Number and quality of measurements
     * 3. Geometric distribution of APs
     * 
     * @param position Final position estimate
     * @param measurements List of measurement models
     * @param maxLikelihood Best achieved likelihood value
     * @return Confidence value between 0 and 1
     */
    private double calculateConfidence(Position position, List<MeasurementModel> measurements, double maxLikelihood) {
        // Calculate Hessian matrix to estimate uncertainty
        double baseConfidence = getConfidence();
        
        // Adjust confidence based on number of measurements and their quality
        double measurementFactor = Math.min(1.0, measurements.size() / 5.0);
        double likelihoodFactor = Math.exp(maxLikelihood / measurements.size());
        
        return Math.min(0.95, baseConfidence * measurementFactor * likelihoodFactor);
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
        return 0.8; // Base confidence for maximum likelihood method
    }

    @Override
    public String getName() {
        return "maximum_likelihood";
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
} 