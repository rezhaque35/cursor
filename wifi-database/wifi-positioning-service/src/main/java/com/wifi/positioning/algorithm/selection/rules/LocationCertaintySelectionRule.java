package com.wifi.positioning.algorithm.selection.rules;

import com.wifi.positioning.algorithm.PositioningAlgorithm;
import com.wifi.positioning.algorithm.selection.AlgorithmSelectionRule;
import com.wifi.positioning.algorithm.selection.SelectionContext;
import com.wifi.positioning.algorithm.selection.WeightedAlgorithm;
import com.wifi.positioning.dto.WifiScanResult;
import com.wifi.positioning.model.WifiAccessPoint;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Selection rule that adjusts algorithm weights based on the certainty
 * of the access point location data.
 */
@Component
public class LocationCertaintySelectionRule implements AlgorithmSelectionRule {

    @Override
    public String getName() {
        return "location-certainty";
    }

    @Override
    public List<WeightedAlgorithm> selectAlgorithms(
            List<PositioningAlgorithm> algorithms,
            List<WifiScanResult> validScans,
            Map<String, WifiAccessPoint> apMap,
            SelectionContext context) {
        
        List<WeightedAlgorithm> adjustedAlgorithms = new ArrayList<>();
        double locationConfidence = context.getApLocationConfidence();
        
        for (PositioningAlgorithm algorithm : algorithms) {
            String name = algorithm.getName();
            double weight = 1.0;
            
            // Adjust weights based on location certainty
            if (locationConfidence > 0.8 && name.equals("trilateration")) {
                weight = 1.2; // Boost trilateration for precise AP locations
            } else if (locationConfidence < 0.6 && name.equals("weighted_centroid")) {
                weight = 1.2; // Boost weighted centroid for uncertain AP locations
            }
            
            adjustedAlgorithms.add(new WeightedAlgorithm(algorithm, weight));
        }
        
        return adjustedAlgorithms;
    }

    @Override
    public int getPriority() {
        return 60; // Medium-low priority
    }
} 