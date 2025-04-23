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
 * Selection rule that adjusts algorithm weights based on the geometric
 * distribution of access points.
 */
@Component
public class GeometrySelectionRule implements AlgorithmSelectionRule {

    @Override
    public String getName() {
        return "geometry";
    }

    @Override
    public List<WeightedAlgorithm> selectAlgorithms(
            List<PositioningAlgorithm> algorithms,
            List<WifiScanResult> validScans,
            Map<String, WifiAccessPoint> apMap,
            SelectionContext context) {
        
        List<WeightedAlgorithm> adjustedAlgorithms = new ArrayList<>();
        boolean isCollinear = context.isCollinear();
        boolean isClustered = context.isClustered();
        
        for (PositioningAlgorithm algorithm : algorithms) {
            String name = algorithm.getName();
            double weight = 1.0;
            
            // Exclude trilateration for collinear AP arrangements
            if (isCollinear && name.equals("trilateration")) {
                continue;
            }
            
            // Boost certain algorithms for clustered scenarios
            if (isClustered) {
                if (name.equals("maximum_likelihood") || name.equals("rssi_ratio")) {
                    weight = 1.2;
                }
            }
            
            adjustedAlgorithms.add(new WeightedAlgorithm(algorithm, weight));
        }
        
        return adjustedAlgorithms;
    }

    @Override
    public int getPriority() {
        return 80; // Medium-high priority
    }
} 