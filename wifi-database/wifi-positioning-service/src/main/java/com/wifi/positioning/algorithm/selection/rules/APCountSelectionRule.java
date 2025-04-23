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
 * Selection rule that chooses algorithms based on the number of visible access points.
 */
@Component
public class APCountSelectionRule implements AlgorithmSelectionRule {

    @Override
    public String getName() {
        return "ap-count";
    }

    @Override
    public List<WeightedAlgorithm> selectAlgorithms(
            List<PositioningAlgorithm> algorithms,
            List<WifiScanResult> validScans,
            Map<String, WifiAccessPoint> apMap,
            SelectionContext context) {
        
        int apCount = validScans.size();
        List<WeightedAlgorithm> selectedAlgorithms = new ArrayList<>();
        
        for (PositioningAlgorithm algorithm : algorithms) {
            String name = algorithm.getName();
            
            if (apCount == 1 && name.equals("proximity")) {
                selectedAlgorithms.add(new WeightedAlgorithm(algorithm, 1.0));
            } else if (apCount == 2 && (name.equals("rssi_ratio") || name.equals("weighted_centroid"))) {
                selectedAlgorithms.add(new WeightedAlgorithm(algorithm, 1.0));
            } else if (apCount >= 3 && apCount < 5) {
                if (name.equals("trilateration") || 
                    name.equals("log_distance") || 
                    name.equals("weighted_centroid") ||
                    name.equals("rssi_ratio")) {
                    selectedAlgorithms.add(new WeightedAlgorithm(algorithm, 1.0));
                }
            } else if (apCount >= 5) {
                // For many APs, all algorithms are potentially applicable
                selectedAlgorithms.add(new WeightedAlgorithm(algorithm, 1.0));
            }
        }
        
        return selectedAlgorithms;
    }

    @Override
    public int getPriority() {
        return 100; // High priority - this is the base selection
    }
} 