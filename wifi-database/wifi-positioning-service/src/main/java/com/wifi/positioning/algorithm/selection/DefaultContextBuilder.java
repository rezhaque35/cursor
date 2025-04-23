package com.wifi.positioning.algorithm.selection;

import com.wifi.positioning.dto.WifiScanResult;
import com.wifi.positioning.model.WifiAccessPoint;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Default implementation of the ContextBuilder interface.
 * Evaluates geometry, signal quality, and location certainty to build a selection context.
 */
@Component
public class DefaultContextBuilder implements ContextBuilder {
    
    @Override
    public SelectionContext buildContext(List<WifiScanResult> validScans, Map<String, WifiAccessPoint> apMap) {
        // Evaluate geometry
        GeometryFactors geometry = evaluateAPGeometry(validScans, apMap);
        
        // Evaluate signal quality
        SignalQualityFactors signalQuality = evaluateSignalQuality(validScans);
        
        // Evaluate AP location certainty
        double locationCertainty = evaluateAPLocationCertainty(validScans, apMap);
        
        // Build context
        return SelectionContext.builder()
                .isCollinear(geometry.isCollinear)
                .isClustered(geometry.isClustered)
                .isWeakSignal(signalQuality.isWeak)
                .isVariableSignal(signalQuality.isVariable)
                .apLocationConfidence(locationCertainty)
                .build();
    }

    private GeometryFactors evaluateAPGeometry(List<WifiScanResult> validScans, 
                                             Map<String, WifiAccessPoint> apMap) {
        GeometryFactors factors = new GeometryFactors();
        
        if (validScans.size() < 3) {
            return factors;
        }
        
        // Check for collinearity
        List<WifiAccessPoint> aps = validScans.stream()
            .map(scan -> apMap.get(scan.macAddress()))
            .collect(Collectors.toList());
        
        // Calculate centroid
        double centroidLat = 0, centroidLon = 0;
        for (WifiAccessPoint ap : aps) {
            centroidLat += ap.getLatitude();
            centroidLon += ap.getLongitude();
        }
        centroidLat /= aps.size();
        centroidLon /= aps.size();
        
        // Check collinearity using linear regression and R² value
        double sumXY = 0, sumX = 0, sumY = 0, sumX2 = 0;
        double n = aps.size();
        
        for (WifiAccessPoint ap : aps) {
            double x = ap.getLatitude() - centroidLat;
            double y = ap.getLongitude() - centroidLon;
            sumXY += x * y;
            sumX += x;
            sumY += y;
            sumX2 += x * x;
        }
        
        // Calculate coefficient of determination (R²)
        double numerator = n * sumXY - sumX * sumY;
        double denominator = Math.sqrt((n * sumX2 - sumX * sumX) * (n * sumX2 - sumX * sumX));
        
        double r2 = Math.pow(numerator / denominator, 2);
        
        // Closer to 1 means more collinear
        factors.isCollinear = r2 > 0.9;
        
        // Check for clustering
        double distances = 0;
        int count = 0;
        
        for (int i = 0; i < aps.size(); i++) {
            for (int j = i + 1; j < aps.size(); j++) {
                WifiAccessPoint ap1 = aps.get(i);
                WifiAccessPoint ap2 = aps.get(j);
                
                double distance = calculateDistance(
                    ap1.getLatitude(), ap1.getLongitude(),
                    ap2.getLatitude(), ap2.getLongitude()
                );
                
                distances += distance;
                count++;
            }
        }
        
        double avgDistance = count > 0 ? distances / count : 0;
        
        // If average distance is small, consider it clustered
        factors.isClustered = avgDistance < 0.001; // ~100m in decimal degrees
        
        return factors;
    }

    private SignalQualityFactors evaluateSignalQuality(List<WifiScanResult> validScans) {
        SignalQualityFactors factors = new SignalQualityFactors();
        
        // Calculate average signal strength
        double totalSignalStrength = 0;
        double minSignal = 0;
        double maxSignal = -100;
        
        for (WifiScanResult scan : validScans) {
            totalSignalStrength += scan.signalStrength();
            minSignal = Math.min(minSignal, scan.signalStrength());
            maxSignal = Math.max(maxSignal, scan.signalStrength());
        }
        
        double avgSignalStrength = totalSignalStrength / validScans.size();
        
        // Consider signals weak if average is below -75 dBm
        factors.isWeak = avgSignalStrength < -75;
        
        // Consider signals variable if the range is more than 15 dBm
        factors.isVariable = (maxSignal - minSignal) > 15;
        
        return factors;
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
    
    private static class GeometryFactors {
        boolean isCollinear = false;
        boolean isClustered = false;
    }
    
    private static class SignalQualityFactors {
        boolean isWeak = false;
        boolean isVariable = false;
    }
} 