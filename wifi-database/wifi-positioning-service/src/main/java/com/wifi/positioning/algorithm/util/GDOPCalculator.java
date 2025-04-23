package com.wifi.positioning.algorithm.util;

import com.wifi.positioning.model.WifiAccessPoint;
import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.SingularValueDecomposition;

import java.util.List;

/**
 * Utility class for calculating Geometric Dilution of Precision (GDOP).
 * GDOP is a measure of the quality of the geometric distribution of access points
 * and its impact on positioning accuracy.
 */
public class GDOPCalculator {
    
    private static final int MIN_APS_FOR_POSITION = 3;
    private static final double SINGULAR_VALUE_THRESHOLD = 1e-6;
    private static final double DISTANCE_THRESHOLD = 1e-6;
    
    /**
     * Calculates the GDOP value for a given set of access points.
     * Higher GDOP values indicate poor geometric distribution.
     * Lower GDOP values (< 5) indicate good geometric distribution.
     *
     * @param accessPoints List of access points with known positions
     * @return GDOP value, or Double.POSITIVE_INFINITY if calculation is not possible
     */
    public static double calculate(List<WifiAccessPoint> accessPoints) {
        if (accessPoints == null || accessPoints.size() < MIN_APS_FOR_POSITION) {
            return Double.POSITIVE_INFINITY;
        }
        
        // Calculate centroid as approximate position
        double[] centroid = calculateCentroid(accessPoints);
        
        // Calculate scale factors for each dimension
        double maxDx = 0.0, maxDy = 0.0, maxDz = 0.0;
        for (WifiAccessPoint ap : accessPoints) {
            maxDx = Math.max(maxDx, Math.abs(ap.getLatitude() - centroid[0]));
            maxDy = Math.max(maxDy, Math.abs(ap.getLongitude() - centroid[1]));
            maxDz = Math.max(maxDz, Math.abs(ap.getAltitude() - centroid[2]));
        }
        
        // Avoid division by zero
        maxDx = Math.max(maxDx, DISTANCE_THRESHOLD);
        maxDy = Math.max(maxDy, DISTANCE_THRESHOLD);
        maxDz = Math.max(maxDz, DISTANCE_THRESHOLD);
        
        // Build geometry matrix
        double[][] geometry = new double[accessPoints.size()][4];
        
        // Build normalized geometry matrix
        for (int i = 0; i < accessPoints.size(); i++) {
            WifiAccessPoint ap = accessPoints.get(i);
            
            // Normalize each dimension independently
            double dx = (ap.getLatitude() - centroid[0]) / maxDx;
            double dy = (ap.getLongitude() - centroid[1]) / maxDy;
            double dz = (ap.getAltitude() - centroid[2]) / maxDz;
            
            // Calculate unit vector components
            double magnitude = Math.sqrt(dx*dx + dy*dy + dz*dz);
            if (magnitude > DISTANCE_THRESHOLD) {
                geometry[i][0] = dx / magnitude;
                geometry[i][1] = dy / magnitude;
                geometry[i][2] = dz / magnitude;
            } else {
                geometry[i][0] = 0.0;
                geometry[i][1] = 0.0;
                geometry[i][2] = 0.0;
            }
            geometry[i][3] = 1.0; // Time component
        }
        
        // Calculate GDOP using SVD
        RealMatrix G = new Array2DRowRealMatrix(geometry);
        SingularValueDecomposition svd = new SingularValueDecomposition(G);
        double[] singularValues = svd.getSingularValues();
        
        // Calculate GDOP from singular values
        double sumInverseSquares = 0.0;
        int validValues = 0;
        
        for (double value : singularValues) {
            if (value > SINGULAR_VALUE_THRESHOLD) {
                sumInverseSquares += 1.0 / (value * value);
                validValues++;
            }
        }
        
        // Check for poor geometry
        if (validValues < 3) {
            return Double.POSITIVE_INFINITY;
        }
        
        // Calculate GDOP with proper scaling
        double gdop = Math.sqrt(sumInverseSquares);
        
        // Scale based on number of APs and dimensionality
        // The factor 3.0 represents the minimum number of APs needed
        return gdop * Math.sqrt(3.0 / accessPoints.size());
    }
    
    private static double[] calculateCentroid(List<WifiAccessPoint> accessPoints) {
        double sumLat = 0, sumLon = 0, sumAlt = 0;
        for (WifiAccessPoint ap : accessPoints) {
            sumLat += ap.getLatitude();
            sumLon += ap.getLongitude();
            sumAlt += ap.getAltitude();
        }
        int count = accessPoints.size();
        return new double[] {
            sumLat / count,
            sumLon / count,
            sumAlt / count
        };
    }
} 