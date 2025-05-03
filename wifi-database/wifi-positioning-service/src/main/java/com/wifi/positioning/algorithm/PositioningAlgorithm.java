package com.wifi.positioning.algorithm;

import com.wifi.positioning.algorithm.factor.APCountFactor;
import com.wifi.positioning.algorithm.factor.GeometricQualityFactor;
import com.wifi.positioning.algorithm.factor.SignalDistributionFactor;
import com.wifi.positioning.algorithm.factor.SignalQualityFactor;
import com.wifi.positioning.dto.Position;
import com.wifi.positioning.dto.WifiScanResult;
import com.wifi.positioning.model.WifiAccessPoint;
import java.util.List;

/**
 * Interface for positioning algorithms that calculate location based on WiFi access points.
 * Includes methods for obtaining algorithm-specific weights based on various factors
 * that affect positioning accuracy.
 */
public interface PositioningAlgorithm {
    /**
     * Calculates the position based on the provided WiFi scan results and known access points.
     *
     * @param wifiScan List of WiFi scan results
     * @param knownAPs List of known WiFi access points
     * @return The calculated position
     */
    Position calculatePosition(List<WifiScanResult> wifiScan, List<WifiAccessPoint> knownAPs);

    /**
     * Returns the confidence level of the algorithm's calculation.
     *
     * @return Confidence value between 0 and 1
     */
    double getConfidence();

    /**
     * Returns the name of the algorithm.
     *
     * @return Algorithm name
     */
    String getName();
    
    /**
     * Returns the base weight of the algorithm for a given AP count scenario.
     * 
     * Implementations should provide weights based on AP count:
     * - Single AP: moderate weight (0.6)
     * - Two APs: increased weight (0.7)
     * - Three APs: good weight (0.8)
     * - Four+ APs: high weight (0.9)
     * 
     * @param factor The AP count factor
     * @return The base weight value for this algorithm
     */
    double getBaseWeight(APCountFactor factor);
    
    /**
     * Returns the weight adjustment for this algorithm based on signal quality.
     * 
     * Implementations should provide adjustments based on signal quality:
     * - Strong signals: positive adjustment (+0.1)
     * - Medium signals: no adjustment (0.0)
     * - Weak signals: negative adjustment (-0.1)
     * 
     * @param factor The signal quality factor
     * @return The weight adjustment value
     */
    double getSignalQualityAdjustment(SignalQualityFactor factor);
    
    /**
     * Returns the weight adjustment for this algorithm based on geometric quality.
     * 
     * Implementations should provide adjustments based on GDOP:
     * - Excellent GDOP: significant positive adjustment (+0.15)
     * - Good GDOP: slight positive adjustment (+0.05)
     * - Fair GDOP: no adjustment (0.0)
     * - Poor GDOP: negative adjustment (-0.1)
     * 
     * @param factor The geometric quality factor
     * @return The weight adjustment value
     */
    double getGeometricQualityAdjustment(GeometricQualityFactor factor);
    
    /**
     * Returns the weight adjustment for this algorithm based on signal distribution.
     * 
     * Implementations should provide adjustments based on signal distribution:
     * - Uniform signals: positive adjustment (+0.1)
     * - Mixed signals: no adjustment (0.0)
     * - Signal outliers: negative adjustment (-0.1)
     * 
     * @param factor The signal distribution factor
     * @return The weight adjustment value
     */
    double getSignalDistributionAdjustment(SignalDistributionFactor factor);
    
    /**
     * Calculates the final weight for this algorithm based on all factors.
     * Default implementation that can be overridden by specific algorithms.
     * 
     * @param apCount Number of access points
     * @param wifiScan List of WiFi scan results
     * @param gdop Geometric Dilution of Precision value
     * @return The final calculated weight for this algorithm
     */
    default double calculateWeight(int apCount, List<WifiScanResult> wifiScan, double gdop) {
        APCountFactor apFactor = APCountFactor.fromCount(apCount);
        SignalQualityFactor signalFactor = SignalQualityFactor.fromWifiScans(wifiScan);
        GeometricQualityFactor geoFactor = GeometricQualityFactor.fromGDOP(gdop);
        SignalDistributionFactor distFactor = SignalDistributionFactor.fromWifiScans(wifiScan);
        
        double baseWeight = getBaseWeight(apFactor);
        double signalAdjustment = getSignalQualityAdjustment(signalFactor);
        double geoAdjustment = getGeometricQualityAdjustment(geoFactor);
        double distAdjustment = getSignalDistributionAdjustment(distFactor);
        
        return baseWeight * (1 + signalAdjustment + geoAdjustment + distAdjustment);
    }
} 