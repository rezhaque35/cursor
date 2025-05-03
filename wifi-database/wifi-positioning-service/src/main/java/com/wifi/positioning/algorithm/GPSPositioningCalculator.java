package com.wifi.positioning.algorithm;

import com.wifi.positioning.algorithm.selection.AlgorithmSelector;
import com.wifi.positioning.algorithm.selection.ContextBuilder;
import com.wifi.positioning.algorithm.selection.PositionCombiner;
import com.wifi.positioning.algorithm.selection.SelectionContext;
import com.wifi.positioning.dto.Position;
import com.wifi.positioning.dto.WifiScanResult;
import com.wifi.positioning.model.WifiAccessPoint;
import com.wifi.positioning.validation.SignalPhysicsValidator;
import org.springframework.stereotype.Component;
import jakarta.annotation.PreDestroy;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * Implements a hybrid algorithm selector that chooses and combines multiple WiFi positioning
 * algorithms based on the available data and scenario characteristics.
 * 
 * Uses a flexible rule-based approach to select algorithms and calculate weights.
 * Includes detailed information about algorithm selection reasoning for better explainability.
 */
@Component
public class GPSPositioningCalculator {
    private final List<PositioningAlgorithm> algorithms;
    private final AlgorithmSelector algorithmSelector;
    private final ContextBuilder contextBuilder;
    private final PositionCombiner positionCombiner;
    private final SignalPhysicsValidator signalPhysicsValidator;
    private final ExecutorService executorService;

    /**
     * Result of positioning calculation containing the calculated position,
     * information about algorithms used, and reasons for algorithm selection.
     */
    public record PositioningResult(
        Position position,
        PositioningAlgorithm bestAlgorithm,
        Map<PositioningAlgorithm, Double> algorithmWeights,
        Map<PositioningAlgorithm, List<String>> selectionReasons
    ) {
        /**
         * Constructor that doesn't require selection reasons for backward compatibility.
         */
        public PositioningResult(
            Position position,
            PositioningAlgorithm bestAlgorithm,
            Map<PositioningAlgorithm, Double> algorithmWeights
        ) {
            this(position, bestAlgorithm, algorithmWeights, Map.of());
        }
    }

    public GPSPositioningCalculator(
            List<PositioningAlgorithm> algorithms, 
            AlgorithmSelector algorithmSelector,
            ContextBuilder contextBuilder,
            PositionCombiner positionCombiner,
            SignalPhysicsValidator signalPhysicsValidator) {
        this.algorithms = algorithms;
        this.algorithmSelector = algorithmSelector;
        this.contextBuilder = contextBuilder;
        this.positionCombiner = positionCombiner;
        this.signalPhysicsValidator = signalPhysicsValidator;
        this.executorService = Executors.newFixedThreadPool(
            Math.max(2, Runtime.getRuntime().availableProcessors() / 2)
        );
    }

    /**
     * Calculate position using the best available algorithms for the given scenario.
     * 
     * @param wifiScan List of WiFi scan results
     * @param knownAPs List of known access points
     * @return PositioningResult containing the calculated position and information about algorithms used,
     *         or null if position calculation fails
     */
    public PositioningResult calculatePosition(List<WifiScanResult> wifiScan, List<WifiAccessPoint> knownAPs) {
        if (wifiScan == null || wifiScan.isEmpty() || knownAPs == null || knownAPs.isEmpty()) {
            return null;
        }

        // Filter valid APs (those we know locations for)
        Map<String, WifiAccessPoint> apMap = createAPMap(knownAPs);
        List<WifiScanResult> validScans = filterValidScans(wifiScan, apMap);
        
        if (validScans.isEmpty()) {
            return null;
        }
        
        // Validate if the signal strengths are physically possible
        if (!signalPhysicsValidator.isPhysicallyPossible(validScans)) {
            // Return null when signals violate physical laws
            return null;
        }

        // 1. Evaluate scenario characteristics 
        SelectionContext context = contextBuilder.buildContext(validScans, apMap);
        
        // 2. Apply algorithm selection rules
        AlgorithmSelector.AlgorithmSelectionInfo selectionInfo = 
                algorithmSelector.selectAlgorithmsWithReasons(validScans, apMap, context);
        
        Map<PositioningAlgorithm, Double> weightedAlgorithms = selectionInfo.algorithmWeights();
        Map<PositioningAlgorithm, List<String>> selectionReasons = selectionInfo.selectionReasons();
        
        if (weightedAlgorithms.isEmpty()) {
            return null;
        }
        
        // 3. Calculate positions using selected algorithms in parallel
        List<CompletableFuture<PositionCombiner.WeightedPosition>> futures = new ArrayList<>();
        
        for (Map.Entry<PositioningAlgorithm, Double> entry : weightedAlgorithms.entrySet()) {
            PositioningAlgorithm algorithm = entry.getKey();
            Double baseWeight = entry.getValue();
            
            CompletableFuture<PositionCombiner.WeightedPosition> future = 
                CompletableFuture.supplyAsync(() -> {
                    Position position = algorithm.calculatePosition(validScans, knownAPs);
                    if (position != null) {
                        // Final weight is base weight * algorithm-specific confidence
                        double weight = baseWeight * algorithm.getConfidence();
                        return new PositionCombiner.WeightedPosition(position, weight);
                    }
                    return null;
                }, executorService);
            
            futures.add(future);
        }
        
        // Wait for all calculations to complete
        List<PositionCombiner.WeightedPosition> positions = futures.stream()
            .map(future -> {
                try {
                    return future.get(5, TimeUnit.SECONDS); // Add timeout to prevent hanging
                } catch (Exception e) {
                    return null;
                }
            })
            .filter(Objects::nonNull)
            .collect(Collectors.toList());

        if (positions.isEmpty()) {
            return null;
        }

        // 4. Combine results using configured position combiner
        Position combinedPosition = positionCombiner.combinePositions(positions);
        
        if (combinedPosition == null) {
            return null;
        }
        
        // 5. Determine the best algorithm (highest weight)
        PositioningAlgorithm bestAlgorithm = weightedAlgorithms.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse(null);
            
        return new PositioningResult(combinedPosition, bestAlgorithm, weightedAlgorithms, selectionReasons);
    }

    private Map<String, WifiAccessPoint> createAPMap(List<WifiAccessPoint> knownAPs) {
        return knownAPs.stream()
            .collect(Collectors.toMap(
                WifiAccessPoint::getMacAddress,
                ap -> ap,
                (existing, replacement) -> existing // Keep first in case of duplicates
            ));
    }
    
    private List<WifiScanResult> filterValidScans(List<WifiScanResult> wifiScan, Map<String, WifiAccessPoint> apMap) {
        return wifiScan.stream()
                .filter(scan -> apMap.containsKey(scan.macAddress()))
                .collect(Collectors.toList());
    }
    
    // Clean up executor service on application shutdown
    @PreDestroy
    public void cleanup() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
} 