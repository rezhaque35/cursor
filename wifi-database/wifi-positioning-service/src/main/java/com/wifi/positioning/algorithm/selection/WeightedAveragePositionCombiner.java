package com.wifi.positioning.algorithm.selection;

import com.wifi.positioning.dto.Position;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Default implementation of the PositionCombiner interface that uses
 * a weighted average approach to combine multiple positions.
 */
@Component
public class WeightedAveragePositionCombiner implements PositionCombiner {
    
    @Override
    public Position combinePositions(List<WeightedPosition> positions) {
        if (positions == null || positions.isEmpty()) {
            return null;
        }
        
        if (positions.size() == 1) {
            return positions.get(0).position();
        }
        
        double totalWeight = positions.stream()
            .mapToDouble(wp -> wp.weight())
            .sum();

        if (totalWeight == 0) {
            return positions.get(0).position();
        }

        double weightedLat = 0, weightedLon = 0, weightedAlt = 0;
        double maxAccuracy = 0;
        double combinedConfidence = 0;

        for (WeightedPosition wp : positions) {
            double normalizedWeight = wp.weight() / totalWeight;
            weightedLat += wp.position().latitude() * normalizedWeight;
            weightedLon += wp.position().longitude() * normalizedWeight;
            weightedAlt += wp.position().altitude() * normalizedWeight;
            maxAccuracy = Math.max(maxAccuracy, wp.position().accuracy());
            combinedConfidence += wp.position().confidence() * normalizedWeight;
        }

        return new Position(weightedLat, weightedLon, weightedAlt, maxAccuracy, combinedConfidence);
    }
} 