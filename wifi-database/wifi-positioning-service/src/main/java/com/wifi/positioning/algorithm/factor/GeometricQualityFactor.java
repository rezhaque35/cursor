package com.wifi.positioning.algorithm.factor;

import java.util.List;
import com.wifi.positioning.dto.Position;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Enum representing different geometric quality scenarios based on GDOP (Geometric Dilution of Precision)
 * that affect algorithm weights.
 * Based on the algorithm selection framework documentation.
 */
public enum GeometricQualityFactor {
    /** Excellent geometric distribution (GDOP < 2) */
    EXCELLENT_GDOP(0.0, 2.0),
    
    /** Good geometric distribution (GDOP 2-4) */
    GOOD_GDOP(2.0, 4.0),
    
    /** Fair geometric distribution (GDOP 4-6) */
    FAIR_GDOP(4.0, 6.0),
    
    /** Poor geometric distribution (GDOP > 6) */
    POOR_GDOP(6.0, Double.POSITIVE_INFINITY);
    
    private static final Logger logger = LoggerFactory.getLogger(GeometricQualityFactor.class);
    private final double lowerBound;
    private final double upperBound;
    
    GeometricQualityFactor(double lowerBound, double upperBound) {
        this.lowerBound = lowerBound;
        this.upperBound = upperBound;
    }
    
    /**
     * Get the lower bound GDOP value for this factor.
     * 
     * @return The lower bound GDOP value
     */
    public double getLowerBound() {
        return lowerBound;
    }
    
    /**
     * Get the upper bound GDOP value for this factor.
     * 
     * @return The upper bound GDOP value
     */
    public double getUpperBound() {
        return upperBound;
    }
    
    /**
     * Determine the appropriate geometric quality factor based on the GDOP value.
     * 
     * @param gdop The Geometric Dilution of Precision value
     * @return The corresponding GeometricQualityFactor
     */
    public static GeometricQualityFactor fromGDOP(double gdop) {
        if (gdop < 2.0) {
            return EXCELLENT_GDOP;
        } else if (gdop < 4.0) {
            return GOOD_GDOP;
        } else if (gdop < 6.0) {
            return FAIR_GDOP;
        } else {
            return POOR_GDOP;
        }
    }

    private static final double COLLINEARITY_THRESHOLD = 0.002; // Maximum allowed deviation from line of best fit
    private static final double AREA_THRESHOLD = 0.0001; // Threshold for area-based check
    private static final double SINGULARITY_THRESHOLD = 1e-10; // Threshold for near-zero values

    /**
     * Checks if a list of positions are collinear (lie on the same line).
     * Uses the maximum deviation from the line of best fit to determine collinearity.
     * The method is designed to be robust to small deviations from perfect collinearity.
     *
     * @param positions List of positions to check
     * @return true if positions are collinear, false otherwise
     */
    public static boolean isCollinear(List<Position> positions) {
        if (positions == null || positions.size() < 3) {
            return false;
        }

        // Calculate mean position
        double meanLat = positions.stream().mapToDouble(Position::latitude).average().orElse(0);
        double meanLon = positions.stream().mapToDouble(Position::longitude).average().orElse(0);

        // Calculate covariance matrix elements
        double covLatLat = 0, covLonLon = 0, covLatLon = 0;
        for (Position p : positions) {
            double dLat = p.latitude() - meanLat;
            double dLon = p.longitude() - meanLon;
            covLatLat += dLat * dLat;
            covLonLon += dLon * dLon;
            covLatLon += dLat * dLon;
        }
        int n = positions.size();
        covLatLat /= n;
        covLonLon /= n;
        covLatLon /= n;

        // Check for perfect horizontal or vertical lines
        if (covLatLat < SINGULARITY_THRESHOLD || covLonLon < SINGULARITY_THRESHOLD) {
            return true;
        }

        // Calculate line of best fit parameters
        double slope;
        if (covLonLon < SINGULARITY_THRESHOLD) {
            // Vertical line
            return true;
        } else {
            slope = covLatLon / covLonLon;
        }
        double intercept = meanLat - slope * meanLon;

        // Calculate maximum deviation from line
        double maxDeviation = 0;
        for (Position p : positions) {
            double expectedLat = slope * p.longitude() + intercept;
            double deviation = Math.abs(p.latitude() - expectedLat);
            maxDeviation = Math.max(maxDeviation, deviation);
        }

        logger.debug("Max deviation: {}, Slope: {}", maxDeviation, slope);
        return maxDeviation <= COLLINEARITY_THRESHOLD;
    }
} 