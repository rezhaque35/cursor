package com.wifi.positioning.algorithm.selection;

import com.wifi.positioning.algorithm.PositioningAlgorithm;
import com.wifi.positioning.dto.WifiScanResult;
import com.wifi.positioning.model.WifiAccessPoint;

import java.util.List;
import java.util.Map;

/**
 * Interface for algorithm selection rules that determine which positioning algorithms
 * should be applied in a given scenario. Each rule can evaluate specific conditions
 * and contribute to the algorithm selection decision.
 */
public interface AlgorithmSelectionRule {
    
    /**
     * Name of the rule for identification purposes.
     * 
     * @return The name of the rule
     */
    String getName();
    
    /**
     * Evaluate the current scenario and determine if each algorithm
     * should be applied, and with what base weight.
     * 
     * @param algorithms All available positioning algorithms
     * @param validScans The valid WiFi scan results
     * @param apMap Map of known access points by MAC address
     * @param context Additional context information about the scenario
     * @return List of algorithms that should be applied with their initial weights
     */
    List<WeightedAlgorithm> selectAlgorithms(
        List<PositioningAlgorithm> algorithms,
        List<WifiScanResult> validScans,
        Map<String, WifiAccessPoint> apMap,
        SelectionContext context
    );
    
    /**
     * Determine the priority of this rule. Rules with higher priority
     * are evaluated first and may override decisions from lower-priority rules.
     * 
     * @return The priority value
     */
    int getPriority();
} 