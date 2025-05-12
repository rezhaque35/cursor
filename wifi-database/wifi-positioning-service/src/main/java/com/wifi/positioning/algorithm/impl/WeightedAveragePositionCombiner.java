package com.wifi.positioning.algorithm.impl;

import com.wifi.positioning.dto.Position;
import com.wifi.positioning.algorithm.selection.factor.GeometricQualityFactor;
import com.wifi.positioning.algorithm.util.GDOPCalculator;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Default implementation of the PositionCombiner interface that uses
 * a weighted average approach to combine multiple positions.
 * 
 * This implementation includes advanced geometric quality assessment to adjust
 * accuracy and confidence values based on the geometric distribution
 * of access points. It properly handles geometric edge cases like collinearity
 * using GDOP (Geometric Dilution of Precision) principles.
 */
@Component
public class WeightedAveragePositionCombiner implements PositionCombiner {
    
    private static final Logger logger = LoggerFactory.getLogger(WeightedAveragePositionCombiner.class);
    
    // Constants for collinear AP handling
    private static final double MAX_COLLINEAR_CONFIDENCE = 0.69; // Maximum confidence for collinear configurations
    private static final double MIN_COLLINEAR_ACCURACY = 6.0; // Minimum accuracy for collinear configurations
    private static final double ACCURACY_SCALE_FACTOR = 0.8; // Used to scale maxAccuracy for collinear base accuracy
    
    @Override
    public Position combinePositions(List<WeightedPosition> positions) {
        if (positions == null || positions.isEmpty()) {
            return null;
        }
        
        if (positions.size() == 1) {
            return positions.get(0).position();
        }
        
        double totalWeight = positions.stream()
            .mapToDouble(wp -> wp.weight())
            .sum();

        if (totalWeight == 0) {
            return positions.get(0).position();
        }

        // Log positions for debugging
        logger.debug("Combining {} positions:", positions.size());
        for (WeightedPosition wp : positions) {
            logger.debug("  Position: lat={}, lon={}, accuracy={}, confidence={}, weight={}",
                wp.position().latitude(), wp.position().longitude(), 
                wp.position().accuracy(), wp.position().confidence(), wp.weight());
        }
        
        // Calculate mean position and gather accuracies
        double meanLat = 0, meanLon = 0;
        List<Double> accuracies = new ArrayList<>();
        
        for (WeightedPosition wp : positions) {
            double normalizedWeight = wp.weight() / totalWeight;
            meanLat += wp.position().latitude() * normalizedWeight;
            meanLon += wp.position().longitude() * normalizedWeight;
            accuracies.add(wp.position().accuracy());
        }
        
        // Calculate covariance matrix elements
        double[] covarianceElements = calculateCovarianceMatrix(positions, meanLat, meanLon);
        double covLatLat = covarianceElements[0];
        double covLonLon = covarianceElements[1];
        double covLatLon = covarianceElements[2];
        
        // Calculate condition number for geometric quality assessment
        double conditionNumber = GDOPCalculator.calculateConditionNumber(covLatLat, covLonLon, covLatLon);
        
        // Extract positions to check for collinearity
        List<Position> positionList = positions.stream()
            .map(WeightedPosition::position)
            .collect(Collectors.toList());
            
        // Determine if the points are collinear using the GeometricQualityFactor utility
        boolean isCollinear = GeometricQualityFactor.isCollinear(positionList);
        logger.debug("Is collinear: {}", isCollinear);
        
        // Calculate geometric quality factor based on condition number and collinearity
        double geometricQualityFactor = GDOPCalculator.calculateGeometricQualityFactor(conditionNumber, isCollinear);
        logger.debug("Geometric quality factor: {}, Condition number: {}", geometricQualityFactor, conditionNumber);
        
        // Calculate weighted position components
        double weightedLat = 0, weightedLon = 0, weightedAlt = 0;
        double combinedConfidence = 0;

        for (WeightedPosition wp : positions) {
            double normalizedWeight = wp.weight() / totalWeight;
            weightedLat += wp.position().latitude() * normalizedWeight;
            weightedLon += wp.position().longitude() * normalizedWeight;
            weightedAlt += wp.position().altitude() * normalizedWeight;
            combinedConfidence += wp.position().confidence() * normalizedWeight;
        }

        // Calculate average and max accuracy
        double avgAccuracy = accuracies.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double maxAccuracy = accuracies.stream().max(Double::compare).orElse(0.0);
        logger.debug("Average accuracy: {}, Max accuracy: {}", avgAccuracy, maxAccuracy);
        
        // Calculate adjusted accuracy based on geometric quality
        double adjustedAccuracy = calculateAdjustedAccuracy(accuracies, geometricQualityFactor, conditionNumber, isCollinear);
        logger.debug("Adjusted accuracy: {}", adjustedAccuracy);
        
        // Adjust confidence based on geometric quality
        double adjustedConfidence = adjustConfidence(combinedConfidence, geometricQualityFactor, isCollinear);
        logger.debug("Adjusted confidence: {}", adjustedConfidence);
        
        Position result = new Position(weightedLat, weightedLon, weightedAlt, adjustedAccuracy, adjustedConfidence);
        logger.debug("Final position: {}", result);
        return result;
    }
    
    /**
     * Calculates the covariance matrix elements for the position distribution.
     * The covariance matrix quantifies how the positions vary together and is used
     * to assess the geometric quality of the position distribution.
     * 
     * Mathematical formula:
     * Cov(X,Y) = (1/n) * Σ[(Xi - X_mean)(Yi - Y_mean)]
     * 
     * Where:
     * - n is the number of positions
     * - Xi represents latitude values
     * - Yi represents longitude values
     * - X_mean is the mean latitude
     * - Y_mean is the mean longitude
     * 
     * The covariance matrix has the form:
     * [Cov(lat,lat)  Cov(lat,lon)]
     * [Cov(lon,lat)  Cov(lon,lon)]
     * 
     * @param positions List of weighted positions
     * @param meanLat Mean latitude of all positions
     * @param meanLon Mean longitude of all positions
     * @return Array containing [covLatLat, covLonLon, covLatLon]
     */
    private double[] calculateCovarianceMatrix(List<WeightedPosition> positions, double meanLat, double meanLon) {
        double covLatLat = 0, covLonLon = 0, covLatLon = 0;
        int n = positions.size();
        
        for (WeightedPosition wp : positions) {
            double latDiff = wp.position().latitude() - meanLat;
            double lonDiff = wp.position().longitude() - meanLon;
            
            covLatLat += latDiff * latDiff;
            covLonLon += lonDiff * lonDiff;
            covLatLon += latDiff * lonDiff;
        }
        
        covLatLat /= n;
        covLonLon /= n;
        covLatLon /= n;
        
        logger.debug("Covariance matrix: [{}, {}; {}, {}]", 
            covLatLat, covLatLon, covLatLon, covLonLon);
            
        return new double[] {covLatLat, covLonLon, covLatLon};
    }
    
    /**
     * Calculates an adjusted accuracy value based on the geometric quality of the position
     * distribution. Uses GDOP principles to scale accuracy appropriately for different
     * geometric configurations.
     * 
     * Mathematical formulation:
     * For collinear geometries:
     *   1. Calculate geometric weakness = √(conditionNumber/normalization)
     *   2. Calculate base accuracy = max(avgAccuracy, maxAccuracy*scaleFactor)
     *   3. Scale accuracy = baseAccuracy * max(geometricQualityFactor, geometricWeakness)
     *   4. Apply minimum threshold: adjustedAccuracy = max(MIN_THRESHOLD, scaledAccuracy)
     * 
     * For non-collinear geometries:
     *   adjustedAccuracy = max(maxAccuracy, avgAccuracy * geometricQualityFactor)
     * 
     * This approach follows GDOP principles where position uncertainty increases
     * proportionally to the geometric quality of the reference points.
     * 
     * @param accuracies List of accuracy values from input positions
     * @param geometricQualityFactor Calculated geometric quality factor
     * @param conditionNumber Condition number of the position distribution
     * @param isCollinear Whether the positions form a collinear pattern
     * @return Adjusted accuracy value reflecting geometric uncertainty
     */
    private double calculateAdjustedAccuracy(List<Double> accuracies, double geometricQualityFactor, 
                                           double conditionNumber, boolean isCollinear) {
        double avgAccuracy = accuracies.stream()
            .mapToDouble(Double::doubleValue)
            .average()
            .orElse(0);
        
        double maxAccuracy = accuracies.stream()
            .max(Double::compare)
            .orElse(0.0);
            
        if (isCollinear) {
            // For collinear configurations, apply GDOP scaling using condition number
            // High condition numbers result in greater accuracy values (less precision)
            double geometricWeakness = Math.sqrt(conditionNumber / GDOPCalculator.CONDITION_NUMBER_NORMALIZATION);
            
            // Calculate a robust base accuracy using both average and maximum values
            double baseAccuracy = Math.max(avgAccuracy, maxAccuracy * ACCURACY_SCALE_FACTOR);
            double scaledAccuracy = baseAccuracy * Math.max(geometricQualityFactor, geometricWeakness);
            
            // Ensure minimum accuracy for collinear cases
            return Math.max(MIN_COLLINEAR_ACCURACY, scaledAccuracy);
        }
        
        // For non-collinear cases, use standard accuracy scaling based on geometric quality
        return Math.max(maxAccuracy, avgAccuracy * geometricQualityFactor);
    }
    
    /**
     * Adjusts the confidence value based on geometric quality.
     * Poor geometry results in lower confidence values.
     * 
     * Mathematical approach:
     * For collinear configurations:
     *   1. adjustedConfidence = confidence / (geometricQualityFactor * multiplier)
     *   2. Cap at maximum allowed: min(MAX_VALUE, adjustedConfidence)
     * 
     * For non-collinear configurations:
     *   adjustedConfidence = confidence / √(geometricQualityFactor)
     * 
     * Using square root for non-collinear cases creates a more moderate reduction,
     * while the direct division with an additional multiplier for collinear cases
     * creates a more aggressive confidence reduction.
     * 
     * @param confidence Original combined confidence value
     * @param geometricQualityFactor The calculated geometric quality factor
     * @param isCollinear Whether the points form a collinear pattern
     * @return Adjusted confidence value
     */
    private double adjustConfidence(double confidence, double geometricQualityFactor, boolean isCollinear) {
        if (isCollinear) {
            // For collinear configurations, reduce confidence more significantly
            // and ensure it stays below maximum threshold for collinear cases
            double adjustedConfidence = confidence / (geometricQualityFactor * GDOPCalculator.COLLINEAR_CONFIDENCE_MULTIPLIER);
            return Math.min(MAX_COLLINEAR_CONFIDENCE, adjustedConfidence);
        }
        
        // For non-collinear configurations, use moderate confidence adjustment
        // Square root creates a less aggressive reduction than linear scaling
        return confidence / Math.sqrt(geometricQualityFactor);
    }
} 