package com.wifi.positioning.algorithm.util;

import org.apache.commons.math3.linear.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class for calculating Geometric Dilution of Precision (GDOP) and related
 * metrics for WiFi positioning systems.
 * 
 * GDOP is a measure of how the geometric configuration of access points affects the
 * accuracy of a position calculation. Better AP geometry provides better positioning
 * accuracy, while poor geometry (like collinear APs) reduces accuracy.
 * 
 * This class provides methods for:
 * 1. Calculating GDOP from AP coordinates and estimated position
 * 2. Converting GDOP to scaling factors for accuracy/confidence adjustments
 * 3. Detecting collinearity and poor geometry conditions
 * 4. Calculating condition number for geometric quality assessment
 */
public class GDOPCalculator {
    
    private static final Logger logger = LoggerFactory.getLogger(GDOPCalculator.class);
    
    // GDOP quality thresholds
    public static final double EXCELLENT_GDOP = 2.0;  // GDOP values below this indicate excellent AP geometry
    public static final double GOOD_GDOP = 4.0;       // GDOP values below this indicate good AP geometry
    public static final double FAIR_GDOP = 6.0;       // GDOP values below this indicate fair AP geometry
    public static final double MAX_ALLOWED_GDOP = 10.0; // Maximum GDOP value allowed for position calculation
    
    // GDOP scaling factors for confidence and accuracy
    public static final double GDOP_CONFIDENCE_WEIGHT = 0.30; // Weight of GDOP in confidence calculation (0-1)
    public static final double GDOP_ACCURACY_MULTIPLIER = 0.5; // How much GDOP affects accuracy estimation
    
    // Constants for geometric quality assessment in position combining
    public static final double COLLINEARITY_THRESHOLD = 0.01; // Variance ratio below this is considered collinear
    public static final double SINGULARITY_THRESHOLD = 1e-10;  // Determinant below this is considered singular
    public static final double GOOD_GEOMETRY_THRESHOLD = 5.0;  // Condition numbers below this indicate good geometry
    public static final double MODERATE_GEOMETRY_THRESHOLD = 20.0; // Values between 5-20 indicate moderate geometry
    public static final double CONDITION_NUMBER_SCALING_FACTOR = 15.0; // For moderate geometry scaling
    public static final double POOR_GEOMETRY_SCALING_FACTOR = 80.0; // For poor geometry scaling
    public static final double CONDITION_NUMBER_NORMALIZATION = 10.0; // Normalizes condition number for accuracy scaling
    
    // Constants for collinear AP handling
    public static final double COLLINEAR_BASE_FACTOR = 2.0; // Base factor for collinear geometry
    public static final double COLLINEAR_LOG_SCALE = 2.0; // Log scale divider for collinear condition number
    public static final double COLLINEAR_CONFIDENCE_MULTIPLIER = 1.2; // Stronger confidence reduction for collinearity

    /**
     * Private constructor to prevent instantiation of utility class
     */
    private GDOPCalculator() {
        throw new AssertionError("Utility class should not be instantiated");
    }
    
    /**
     * Calculates Geometric Dilution of Precision (GDOP) for the position solution.
     * GDOP indicates how geometric AP distribution affects position accuracy.
     * 
     * Low GDOP (1-3): Excellent AP geometry, high position accuracy
     * Medium GDOP (3-6): Good AP geometry, reasonable accuracy
     * High GDOP (6-10): Poor AP geometry, reduced accuracy
     * Very High GDOP (>10): Very poor geometry, significantly reduced accuracy
     * 
     * The mathematical formula used is:
     *   GDOP = sqrt(trace((H^T * H)^-1))
     * 
     * Where:
     * - H is the geometry matrix containing unit vectors from the estimated position to each AP
     * - H^T is the transpose of H
     * - (H^T * H)^-1 is the inverse of the product of H^T and H
     * - trace() is the sum of the diagonal elements of the matrix
     * - sqrt() is the square root operation
     * 
     * The geometry matrix H is constructed as:
     *   H = [ (x1-x)/r1  (y1-y)/r1  1 ]
     *       [ (x2-x)/r2  (y2-y)/r2  1 ]
     *       [ (x3-x)/r3  (y3-y)/r3  1 ]
     *       [     ...        ...    ... ]
     * 
     * Where:
     * - (xi,yi) are the coordinates of the i-th AP
     * - (x,y) is the estimated position
     * - ri is the distance from the position to the i-th AP
     * - The last column (all 1's) is a bias term
     * 
     * GDOP is widely used in GPS/GNSS positioning systems to evaluate the quality
     * of satellite geometry and its impact on positioning accuracy.
     * 
     * @param coordinates Array of AP coordinates [x, y] or [x, y, z]
     * @param position Estimated position [x, y] or [x, y, z]
     * @param includeBiasTerm Whether to include a bias term (column of 1's) in the geometry matrix
     * @return GDOP value, higher values indicate poorer geometry (limited to MAX_ALLOWED_GDOP)
     */
    public static double calculateGDOP(double[][] coordinates, double[] position, boolean includeBiasTerm) {
        int n = coordinates.length;
        int dimensions = position.length;
        
        // Need at least 3 APs for GDOP calculation
        if (n < 3 || position == null) {
            return MAX_ALLOWED_GDOP;
        }
        
        // Verify all coordinates have same dimensions as position
        for (double[] coordinate : coordinates) {
            if (coordinate.length < dimensions) {
                logger.warn("Coordinate dimensions do not match position dimensions");
                return MAX_ALLOWED_GDOP;
            }
        }
        
        try {
            // Create geometry matrix H (with or without bias term)
            int matrixCols = includeBiasTerm ? dimensions + 1 : dimensions;
            double[][] H = new double[n][matrixCols];
            
            // Constants for converting lat/lon differences to meters
            final double EARTH_RADIUS = 6371000; // Earth's radius in meters
            final double LAT_TO_METERS = EARTH_RADIUS * Math.PI / 180.0;
            final double LON_TO_METERS = LAT_TO_METERS * Math.cos(Math.toRadians(position[0]));
            
            // Create rows of unit vectors from position to each AP
            for (int i = 0; i < n; i++) {
                double[] ap = coordinates[i];
                
                // Convert lat/lon differences to meters
                double dx = (ap[0] - position[0]) * LAT_TO_METERS;  // latitude difference in meters
                double dy = (ap[1] - position[1]) * LON_TO_METERS;  // longitude difference in meters
                double dz = dimensions > 2 ? ap[2] - position[2] : 0.0;
                
                // Distance from position to AP in meters
                double r = Math.sqrt(dx*dx + dy*dy + dz*dz);
                
                // Avoid division by zero
                if (r < 1.0) {  // Changed threshold to 1 meter
                    // AP too close to position, use fixed vector
                    H[i][0] = 1.0;
                    if (dimensions > 1) H[i][1] = 0.0;
                    if (dimensions > 2) H[i][2] = 0.0;
                } else {
                    // Unit vector components
                    H[i][0] = dx/r;
                    if (dimensions > 1) H[i][1] = dy/r;
                    if (dimensions > 2) H[i][2] = dz/r;
                }
                
                // Add bias term if needed
                if (includeBiasTerm) {
                    H[i][matrixCols-1] = 1.0;
                }
            }
            
            // Create H matrix using Apache Commons Math
            RealMatrix geometry = new Array2DRowRealMatrix(H);
            RealMatrix HT = geometry.transpose();
            RealMatrix HTH = HT.multiply(geometry);
            
            // Calculate inverse of HTH
            DecompositionSolver solver = new QRDecomposition(HTH).getSolver();
            
            if (!solver.isNonSingular()) {
                logger.debug("Matrix is singular, returning maximum GDOP");
                return MAX_ALLOWED_GDOP;
            }
            
            RealMatrix inverse = solver.getInverse();
            
            // GDOP is square root of trace
            double trace = inverse.getTrace();
            
            // Ensure trace is positive
            trace = Math.max(0, trace);
            
            // Calculate GDOP
            double gdop = Math.sqrt(trace);
            
            logger.debug("Calculated GDOP = {} for {} APs", gdop, n);
            
            // Limit GDOP to reasonable range
            return Math.min(MAX_ALLOWED_GDOP, gdop);
        } catch (Exception e) {
            // Matrix operations failed, return maximum GDOP
            logger.debug("Matrix operation failed: {}", e.getMessage());
            return MAX_ALLOWED_GDOP;
        }
    }
    
    /**
     * Calculates a GDOP factor for adjusting accuracy and confidence values.
     * Converts raw GDOP values to usable scaling factors based on geometric quality.
     * 
     * The transformation follows a piecewise linear function with these segments:
     * 1. Excellent geometry (GDOP ≤ 2): Factor = 1.0 (no adjustment)
     * 2. Good geometry (2 < GDOP ≤ 4): Factor ranges from 1.0 to 1.5
     * 3. Fair geometry (4 < GDOP ≤ 6): Factor ranges from 1.5 to 2.0
     * 4. Poor geometry (GDOP > 6): Factor ranges from 2.0 up to 4.0
     * 
     * This creates a continuous function where:
     * - Better geometry (lower GDOP) results in factors closer to 1.0
     * - Poorer geometry (higher GDOP) results in larger factors
     * 
     * The resulting factor is used to:
     * 1. Scale accuracy values (higher factor = worse accuracy)
     * 2. Adjust confidence values (higher factor = lower confidence)
     * 
     * @param gdop Raw GDOP value as calculated from the geometry matrix
     * @return Scaling factor for accuracy/confidence adjustments (range: 1.0 to 4.0)
     */
    public static double calculateGDOPFactor(double gdop) {
        if (gdop <= EXCELLENT_GDOP) {
            // Excellent geometry, minimal adjustment
            return 1.0;
        } else if (gdop <= GOOD_GDOP) {
            // Good geometry, small adjustment
            double factor = 1.0 + 0.5 * ((gdop - EXCELLENT_GDOP) / (GOOD_GDOP - EXCELLENT_GDOP));
            return factor;
        } else if (gdop <= FAIR_GDOP) {
            // Fair geometry, moderate adjustment
            double factor = 1.5 + 0.5 * ((gdop - GOOD_GDOP) / (FAIR_GDOP - GOOD_GDOP));
            return factor;
        } else {
            // Poor geometry, significant adjustment
            double factor = 2.0 + (gdop - FAIR_GDOP) / 2.0;
            return Math.min(4.0, factor); // Cap at 4.0
        }
    }
    
    /**
     * Calculates the condition number of a covariance matrix, which is a key
     * indicator of geometric quality. Higher condition numbers indicate poor geometry.
     * 
     * Mathematical formula:
     * 1. Calculate the eigenvalues (λ1, λ2) of the covariance matrix using:
     *    λ1,λ2 = (trace ± √[(trace)² - 4*det])/2
     *    where:
     *    - trace = covLatLat + covLonLon
     *    - det = covLatLat*covLonLon - covLatLon²
     * 
     * 2. The condition number is:
     *    κ = |λmax| / |λmin|
     * 
     * The condition number measures how "stretched" the error ellipse is.
     * For excellent geometry, κ ≈ 1
     * For poor geometry (like collinearity), κ >> 1
     * 
     * @param covLatLat Covariance of first dimension with itself
     * @param covLonLon Covariance of second dimension with itself
     * @param covLatLon Cross-covariance between first and second dimensions
     * @return The condition number of the covariance matrix
     */
    public static double calculateConditionNumber(double covLatLat, double covLonLon, double covLatLon) {
        // Calculate trace and determinant of the covariance matrix
        double trace = covLatLat + covLonLon;
        double determinant = covLatLat * covLonLon - covLatLon * covLatLon;
        
        // Ensure determinant is not too close to zero to avoid numerical issues
        if (Math.abs(determinant) < SINGULARITY_THRESHOLD) {
            // Near-singular matrix indicates very poor geometry
            return Double.MAX_VALUE;
        }
        
        // Calculate discriminant for eigenvalue computation
        double discriminant = trace * trace - 4 * determinant;
        
        // If discriminant is negative, the matrix has complex eigenvalues
        // This shouldn't happen with a covariance matrix (which is positive semi-definite)
        if (discriminant < 0) {
            return Double.MAX_VALUE;
        }
        
        // Calculate eigenvalues
        double sqrtDiscriminant = Math.sqrt(discriminant);
        double lambda1 = (trace + sqrtDiscriminant) / 2.0;
        double lambda2 = (trace - sqrtDiscriminant) / 2.0;
        
        // Condition number is ratio of largest to smallest eigenvalue
        return Math.abs(lambda1) / Math.max(Math.abs(lambda2), SINGULARITY_THRESHOLD);
    }
    
    /**
     * Calculates a geometric quality factor based on the condition number and collinearity.
     * The factor represents how much the accuracy should be inflated due to poor geometry.
     * 
     * Mathematical approach:
     * For collinear cases:
     *   factor = baseline + log10(conditionNumber)/scale
     * 
     * For non-collinear cases, piecewise function:
     *   - Good geometry (κ < threshold1): factor = 1.0
     *   - Moderate geometry (threshold1 ≤ κ < threshold2): 
     *     factor = 1.0 + (κ - threshold1)/scale1
     *   - Poor geometry (κ ≥ threshold2): 
     *     factor = 2.0 + min(1.0, (κ - threshold2)/scale2)
     * 
     * Where:
     * - κ is the condition number
     * - The thresholds and scales are constants defining the geometry quality ranges
     * 
     * @param conditionNumber Condition number of the covariance matrix
     * @param isCollinear Whether the positions are determined to be collinear
     * @return Geometric quality factor (1.0 for good geometry, >1.0 for poor geometry)
     */
    public static double calculateGeometricQualityFactor(double conditionNumber, boolean isCollinear) {
        if (isCollinear) {
            // For collinear cases, use a more aggressive scaling factor
            // Using logarithmic scaling to handle potentially very large condition numbers
            return COLLINEAR_BASE_FACTOR + Math.min(1.0, Math.log10(conditionNumber) / COLLINEAR_LOG_SCALE);
        } else {
            // For non-collinear cases, use standard geometric quality assessment
            if (conditionNumber < GOOD_GEOMETRY_THRESHOLD) {
                // Good geometry
                return 1.0;
            } else if (conditionNumber < MODERATE_GEOMETRY_THRESHOLD) {
                // Moderately poor geometry
                return 1.0 + (conditionNumber - GOOD_GEOMETRY_THRESHOLD) / CONDITION_NUMBER_SCALING_FACTOR;
            } else {
                // Very poor geometry
                return 2.0 + Math.min(1.0, (conditionNumber - MODERATE_GEOMETRY_THRESHOLD) / POOR_GEOMETRY_SCALING_FACTOR);
            }
        }
    }
} 