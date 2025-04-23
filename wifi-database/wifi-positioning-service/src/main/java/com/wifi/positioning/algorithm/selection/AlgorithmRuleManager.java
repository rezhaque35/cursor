package com.wifi.positioning.algorithm.selection;

import com.wifi.positioning.algorithm.PositioningAlgorithm;
import com.wifi.positioning.dto.WifiScanResult;
import com.wifi.positioning.model.WifiAccessPoint;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Manages the algorithm selection rules and coordinates the selection process.
 */
@Component
public class AlgorithmRuleManager {
    
    private final List<AlgorithmSelectionRule> rules;
    
    public AlgorithmRuleManager(List<AlgorithmSelectionRule> rules) {
        // Sort rules by priority (highest first)
        this.rules = rules.stream()
                .sorted(Comparator.comparing(AlgorithmSelectionRule::getPriority).reversed())
                .collect(Collectors.toList());
    }
    
    /**
     * Apply all rules to select and weight the appropriate algorithms for the given scenario.
     * 
     * @param algorithms All available positioning algorithms
     * @param validScans The valid WiFi scan results
     * @param apMap Map of known access points by MAC address
     * @param context Additional context information about the scenario
     * @return Map of selected algorithms with their calculated weights
     */
    public Map<PositioningAlgorithm, Double> selectAlgorithms(
            List<PositioningAlgorithm> algorithms,
            List<WifiScanResult> validScans,
            Map<String, WifiAccessPoint> apMap,
            SelectionContext context) {
        
        Map<PositioningAlgorithm, Double> result = new HashMap<>();
        Set<PositioningAlgorithm> excludedAlgorithms = new HashSet<>();
        
        // Apply rules in priority order
        for (AlgorithmSelectionRule rule : rules) {
            List<WeightedAlgorithm> ruleSelections = rule.selectAlgorithms(
                    algorithms.stream()
                            .filter(algo -> !excludedAlgorithms.contains(algo))
                            .collect(Collectors.toList()),
                    validScans, 
                    apMap, 
                    context);
            
            // Update weights and track excluded algorithms
            for (WeightedAlgorithm weighted : ruleSelections) {
                PositioningAlgorithm algorithm = weighted.algorithm();
                
                if (weighted.weight() <= 0) {
                    excludedAlgorithms.add(algorithm);
                    result.remove(algorithm);
                } else {
                    // Multiply weights from different rules
                    double currentWeight = result.getOrDefault(algorithm, 1.0);
                    result.put(algorithm, currentWeight * weighted.weight());
                }
            }
        }
        
        return result;
    }
} 