package com.wifi.positioning.algorithm.factor;

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
} 