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
 * Selection rule that adjusts algorithm weights based on signal quality factors.
 */
@Component
public class SignalQualitySelectionRule implements AlgorithmSelectionRule {

    @Override
    public String getName() {
        return "signal-quality";
    }

    @Override
    public List<WeightedAlgorithm> selectAlgorithms(
            List<PositioningAlgorithm> algorithms,
            List<WifiScanResult> validScans,
            Map<String, WifiAccessPoint> apMap,
            SelectionContext context) {
        
        List<WeightedAlgorithm> adjustedAlgorithms = new ArrayList<>();
        boolean isWeak = context.isWeakSignal();
        boolean isVariable = context.isVariableSignal();
        
        for (PositioningAlgorithm algorithm : algorithms) {
            String name = algorithm.getName();
            double weight = 1.0;
            
            // Adjust weights based on signal quality
            if (isWeak && name.equals("maximum_likelihood")) {
                weight = 1.2; // Maximum likelihood handles weak signals better
            }
            
            if (isVariable && name.equals("rssi_ratio")) {
                weight = 1.2; // RSSI ratio handles variable signals better
            }
            
            adjustedAlgorithms.add(new WeightedAlgorithm(algorithm, weight));
        }
        
        return adjustedAlgorithms;
    }

    @Override
    public int getPriority() {
        return 70; // Medium priority
    }
} 