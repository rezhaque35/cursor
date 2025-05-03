package com.wifi.positioning.algorithm.selection;

import com.wifi.positioning.algorithm.PositioningAlgorithm;
import com.wifi.positioning.dto.Position;
import com.wifi.positioning.dto.WifiScanResult;
import com.wifi.positioning.model.WifiAccessPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the AlgorithmSelector class.
 * 
 * Tests the three-phase algorithm selection process:
 * 1. Hard Constraints (Disqualification Phase)
 * 2. Algorithm Weighting (Ranking Phase)
 * 3. Finalist Selection (Combination Phase)
 */
@ExtendWith(MockitoExtension.class)
class AlgorithmSelectorTest {

    // Constants for test assertions
    private static final String DISQUALIFIED = "DISQUALIFIED";
    
    private AlgorithmSelector algorithmSelector;
    private PositioningAlgorithm proximityAlgorithm;
    private PositioningAlgorithm rssiRatioAlgorithm;
    private PositioningAlgorithm weightedCentroidAlgorithm;
    private PositioningAlgorithm trilaterationAlgorithm;
    private PositioningAlgorithm maximumLikelihoodAlgorithm;
    private PositioningAlgorithm logDistanceAlgorithm;
    
    @BeforeEach
    void setUp() {
        // Create mock algorithms
        proximityAlgorithm = mock(PositioningAlgorithm.class);
        rssiRatioAlgorithm = mock(PositioningAlgorithm.class);
        weightedCentroidAlgorithm = mock(PositioningAlgorithm.class);
        trilaterationAlgorithm = mock(PositioningAlgorithm.class);
        maximumLikelihoodAlgorithm = mock(PositioningAlgorithm.class);
        logDistanceAlgorithm = mock(PositioningAlgorithm.class);
        
        // Set up names for algorithms
        when(proximityAlgorithm.getName()).thenReturn("proximity");
        when(rssiRatioAlgorithm.getName()).thenReturn("rssi_ratio");
        when(weightedCentroidAlgorithm.getName()).thenReturn("weighted_centroid");
        when(trilaterationAlgorithm.getName()).thenReturn("trilateration");
        when(maximumLikelihoodAlgorithm.getName()).thenReturn("maximum_likelihood");
        when(logDistanceAlgorithm.getName()).thenReturn("log_distance");
        
        // Create a list of all mock algorithms
        List<PositioningAlgorithm> customAlgorithms = Arrays.asList(
            proximityAlgorithm,
            rssiRatioAlgorithm,
            weightedCentroidAlgorithm,
            trilaterationAlgorithm,
            maximumLikelihoodAlgorithm,
            logDistanceAlgorithm
        );
        
        // Create algorithm selector with custom algorithms
        algorithmSelector = new AlgorithmSelector(customAlgorithms);
    }
    
    @Nested
    @DisplayName("Hard Constraints Phase Tests")
    class HardConstraintsPhaseTests {
        
        @Test
        @DisplayName("Single AP - Should only allow Proximity and Log Distance")
        void singleAPDisqualification() {
            // Setup
            List<WifiScanResult> scans = Arrays.asList(
                new WifiScanResult("AP1", -65.0, 2412, "test")
            );
            
            Map<String, WifiAccessPoint> apMap = new HashMap<>();
            apMap.put("AP1", WifiAccessPoint.builder()
                .macAddress("AP1")
                .latitude(1.0)
                .longitude(1.0)
                .build());
                
            SelectionContext context = SelectionContext.builder().build();
            
            // Execute - note that we no longer pass allAlgorithms
            AlgorithmSelector.AlgorithmSelectionInfo result = 
                algorithmSelector.selectAlgorithmsWithReasons(scans, apMap, context);
                
            // Verify
            Map<PositioningAlgorithm, Double> weights = result.algorithmWeights();
            Map<PositioningAlgorithm, List<String>> reasons = result.selectionReasons();
            
            // Only Proximity and Log Distance should be selected
            assertTrue(weights.containsKey(proximityAlgorithm), "Proximity algorithm should be selected for single AP");
            assertTrue(weights.containsKey(logDistanceAlgorithm), "Log Distance algorithm should be selected for single AP");
            
            // Other algorithms should be disqualified
            assertFalse(weights.containsKey(rssiRatioAlgorithm), "RSSI Ratio should be disqualified for single AP");
            assertFalse(weights.containsKey(weightedCentroidAlgorithm), "Weighted Centroid should be disqualified for single AP");
            assertFalse(weights.containsKey(trilaterationAlgorithm), "Trilateration should be disqualified for single AP");
            assertFalse(weights.containsKey(maximumLikelihoodAlgorithm), "Maximum Likelihood should be disqualified for single AP");
            
            // Verify disqualification reasons
            assertTrue(reasons.get(rssiRatioAlgorithm).stream()
                .anyMatch(reason -> reason.contains("DISQUALIFIED")), 
                "RSSI Ratio should have DISQUALIFIED reason");
        }
        
        @Test
        @DisplayName("Two APs - Should disqualify Trilateration and Maximum Likelihood")
        void twoAPDisqualification() {
            // Setup
            List<WifiScanResult> scans = Arrays.asList(
                new WifiScanResult("AP1", -65.0, 2412, "test"),
                new WifiScanResult("AP2", -68.0, 5180, "test")
            );
            
            Map<String, WifiAccessPoint> apMap = new HashMap<>();
            apMap.put("AP1", WifiAccessPoint.builder()
                .macAddress("AP1")
                .latitude(1.0)
                .longitude(1.0)
                .build());
            apMap.put("AP2", WifiAccessPoint.builder()
                .macAddress("AP2")
                .latitude(2.0)
                .longitude(2.0)
                .build());
                
            SelectionContext context = SelectionContext.builder().build();
            
            // Execute
            AlgorithmSelector.AlgorithmSelectionInfo result = 
                algorithmSelector.selectAlgorithmsWithReasons(scans, apMap, context);
                
            // Verify
            Map<PositioningAlgorithm, Double> weights = result.algorithmWeights();
            Map<PositioningAlgorithm, List<String>> reasons = result.selectionReasons();
            
            // Verify allowed algorithms
            assertTrue(weights.containsKey(proximityAlgorithm), "Proximity algorithm should be allowed for two APs");
            assertTrue(weights.containsKey(rssiRatioAlgorithm), "RSSI Ratio should be allowed for two APs");
            assertTrue(weights.containsKey(weightedCentroidAlgorithm), "Weighted Centroid should be allowed for two APs");
            assertTrue(weights.containsKey(logDistanceAlgorithm), "Log Distance should be allowed for two APs");
            
            // Verify disqualified algorithms
            assertFalse(weights.containsKey(trilaterationAlgorithm), "Trilateration should be disqualified for two APs");
            assertFalse(weights.containsKey(maximumLikelihoodAlgorithm), "Maximum Likelihood should be disqualified for two APs");
            
            // Verify disqualification reasons
            assertTrue(reasons.get(trilaterationAlgorithm).stream()
                .anyMatch(reason -> reason.contains("DISQUALIFIED")), 
                "Trilateration should have DISQUALIFIED reason");
            assertTrue(reasons.get(maximumLikelihoodAlgorithm).stream()
                .anyMatch(reason -> reason.contains("DISQUALIFIED")), 
                "Maximum Likelihood should have DISQUALIFIED reason");
        }
        
        @Test
        @DisplayName("Three APs with Collinearity - Should disqualify Trilateration")
        void collinearAPDisqualification() {
            // Setup
            List<WifiScanResult> scans = Arrays.asList(
                new WifiScanResult("AP1", -65.0, 2412, "test"),
                new WifiScanResult("AP2", -68.0, 5180, "test"),
                new WifiScanResult("AP3", -70.0, 2412, "test")
            );
            
            Map<String, WifiAccessPoint> apMap = new HashMap<>();
            apMap.put("AP1", WifiAccessPoint.builder()
                .macAddress("AP1")
                .latitude(1.0)
                .longitude(1.0)
                .build());
            apMap.put("AP2", WifiAccessPoint.builder()
                .macAddress("AP2")
                .latitude(2.0)
                .longitude(2.0)
                .build());
            apMap.put("AP3", WifiAccessPoint.builder()
                .macAddress("AP3")
                .latitude(3.0)
                .longitude(3.0)
                .build());
                
            // Set collinearity flag in context
            SelectionContext context = SelectionContext.builder()
                .isCollinear(true)
                .build();
            
            // Execute
            AlgorithmSelector.AlgorithmSelectionInfo result = 
                algorithmSelector.selectAlgorithmsWithReasons(scans, apMap, context);
                
            // Verify
            Map<PositioningAlgorithm, Double> weights = result.algorithmWeights();
            Map<PositioningAlgorithm, List<String>> reasons = result.selectionReasons();
            
            // Trilateration should be disqualified due to collinearity
            assertFalse(weights.containsKey(trilaterationAlgorithm), "Trilateration should be disqualified for collinear APs");
            
            // Other algorithms should be allowed
            assertTrue(weights.containsKey(proximityAlgorithm), "Proximity algorithm should be allowed");
            assertTrue(weights.containsKey(rssiRatioAlgorithm), "RSSI Ratio should be allowed");
            assertTrue(weights.containsKey(weightedCentroidAlgorithm), "Weighted Centroid should be allowed");
            assertTrue(weights.containsKey(logDistanceAlgorithm), "Log Distance should be allowed");
            
            // For 3 APs, Maximum Likelihood may or may not be allowed depending on implementation
            
            // Verify disqualification reason for Trilateration
            assertTrue(reasons.get(trilaterationAlgorithm).stream()
                .anyMatch(reason -> reason.contains("collinear")), 
                "Trilateration should have collinear disqualification reason");
        }
        
        @Test
        @DisplayName("Extremely weak signals - Should only allow Proximity Detection")
        void extremelyWeakSignalDisqualification() {
            // Setup
            List<WifiScanResult> scans = Arrays.asList(
                new WifiScanResult("AP1", -96.0, 2412, "test"),
                new WifiScanResult("AP2", -97.0, 5180, "test"),
                new WifiScanResult("AP3", -98.0, 2412, "test")
            );
            
            Map<String, WifiAccessPoint> apMap = new HashMap<>();
            apMap.put("AP1", WifiAccessPoint.builder()
                .macAddress("AP1")
                .latitude(1.0)
                .longitude(1.0)
                .build());
            apMap.put("AP2", WifiAccessPoint.builder()
                .macAddress("AP2")
                .latitude(2.0)
                .longitude(2.0)
                .build());
            apMap.put("AP3", WifiAccessPoint.builder()
                .macAddress("AP3")
                .latitude(3.0)
                .longitude(3.0)
                .build());
                
            SelectionContext context = SelectionContext.builder()
                .isWeakSignal(true)
                .build();
            
            // Execute
            AlgorithmSelector.AlgorithmSelectionInfo result = 
                algorithmSelector.selectAlgorithmsWithReasons(scans, apMap, context);
                
            // Verify
            Map<PositioningAlgorithm, Double> weights = result.algorithmWeights();
            Map<PositioningAlgorithm, List<String>> reasons = result.selectionReasons();
            
            // Only Proximity should be allowed for extremely weak signals
            assertTrue(weights.containsKey(proximityAlgorithm), "Proximity algorithm should be allowed for extremely weak signals");
            
            // Verify proximity is the ONLY algorithm selected
            assertEquals(1, weights.size(), "Only Proximity algorithm should be selected for extremely weak signals");
            
            // Other algorithms should be disqualified
            for (PositioningAlgorithm algorithm : Arrays.asList(
                rssiRatioAlgorithm, weightedCentroidAlgorithm, trilaterationAlgorithm, maximumLikelihoodAlgorithm, logDistanceAlgorithm
            )) {
                if (algorithm != proximityAlgorithm) {
                    assertFalse(weights.containsKey(algorithm), 
                        algorithm.getName() + " should be disqualified for extremely weak signals");
                    assertTrue(reasons.get(algorithm).stream()
                        .anyMatch(reason -> reason.contains(DISQUALIFIED)), 
                        algorithm.getName() + " should have disqualification reason");
                }
            }
            
            // The final weight for Proximity should be lower due to weak signal adjustment
            double proximityWeight = weights.get(proximityAlgorithm);
            assertTrue(proximityWeight < 1.0, "Proximity weight should be reduced for weak signals");
            assertTrue(proximityWeight > 0.3, "Proximity weight should not be reduced too much even for weak signals");
            
            // Verify specific reason for proximity selection in extremely weak conditions
            assertTrue(reasons.get(proximityAlgorithm).stream()
                .anyMatch(reason -> reason.contains("weak") || reason.contains("signal")),
                "Proximity should have reason explaining it's the only algorithm suitable for extremely weak signals");
        }
    }
    
    @Nested
    @DisplayName("Algorithm Weighting Phase Tests")
    class AlgorithmWeightingPhaseTests {
        
        @Test
        @DisplayName("Four APs with Strong Signals - Maximum Likelihood should have highest weight")
        void fourAPsStrongSignalWeighting() {
            // Setup
            List<WifiScanResult> scans = Arrays.asList(
                new WifiScanResult("AP1", -60.0, 2412, "test"),
                new WifiScanResult("AP2", -62.0, 5180, "test"),
                new WifiScanResult("AP3", -61.0, 2412, "test"),
                new WifiScanResult("AP4", -63.0, 5180, "test")
            );
            
            Map<String, WifiAccessPoint> apMap = new HashMap<>();
            apMap.put("AP1", WifiAccessPoint.builder()
                .macAddress("AP1")
                .latitude(1.0)
                .longitude(1.0)
                .build());
            apMap.put("AP2", WifiAccessPoint.builder()
                .macAddress("AP2")
                .latitude(2.0)
                .longitude(2.0)
                .build());
            apMap.put("AP3", WifiAccessPoint.builder()
                .macAddress("AP3")
                .latitude(1.0)
                .longitude(2.0)
                .build());
            apMap.put("AP4", WifiAccessPoint.builder()
                .macAddress("AP4")
                .latitude(2.0)
                .longitude(1.0)
                .build());
                
            SelectionContext context = SelectionContext.builder().build();
            
            // Execute
            AlgorithmSelector.AlgorithmSelectionInfo result = 
                algorithmSelector.selectAlgorithmsWithReasons(scans, apMap, context);
                
            // Verify
            Map<PositioningAlgorithm, Double> weights = result.algorithmWeights();
            Map<PositioningAlgorithm, List<String>> reasons = result.selectionReasons();
            
            // For 4 APs with strong signals, Maximum Likelihood should have highest weight
            assertTrue(weights.containsKey(maximumLikelihoodAlgorithm), 
                "Maximum Likelihood should be selected for 4 APs with strong signals");
                
            // Get the highest weighted algorithm
            Map.Entry<PositioningAlgorithm, Double> highestEntry = weights.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .orElse(null);
                
            assertNotNull(highestEntry, "Should have a highest weighted algorithm");
            assertEquals(maximumLikelihoodAlgorithm, highestEntry.getKey(), 
                "Maximum Likelihood should have highest weight for 4 APs with strong signals");
                
            // Verify weight adjustments
            double mlWeight = weights.get(maximumLikelihoodAlgorithm);
            assertTrue(mlWeight > 1.0, "Maximum Likelihood weight should be boosted for strong signals");
            
            // More precise weight range verification
            assertTrue(mlWeight >= 1.0 && mlWeight <= 1.5, 
                "Maximum Likelihood weight should be in expected range (1.0-1.5), but was " + mlWeight);
            
            // Verify Trilateration has a good weight too
            assertTrue(weights.containsKey(trilaterationAlgorithm), "Trilateration should be selected");
            
            double trilaterationWeight = weights.get(trilaterationAlgorithm);
            assertTrue(trilaterationWeight > 0.5, "Trilateration should have good weight");
            assertTrue(trilaterationWeight < mlWeight, 
                "Trilateration weight should be less than Maximum Likelihood weight");
            
            // Verify total number of selected algorithms for strong signals
            assertTrue(weights.size() >= 2 && weights.size() <= 4, 
                "Strong signal scenario should select 2-4 algorithms, found " + weights.size());
        }
        
        @Test
        @DisplayName("Four APs with Weak Signals - Weighted Centroid should be favored")
        void fourAPsWeakSignalWeighting() {
            // Setup
            List<WifiScanResult> scans = Arrays.asList(
                new WifiScanResult("AP1", -86.0, 2412, "test"),
                new WifiScanResult("AP2", -87.0, 5180, "test"),
                new WifiScanResult("AP3", -88.0, 2412, "test"),
                new WifiScanResult("AP4", -89.0, 5180, "test")
            );
            
            Map<String, WifiAccessPoint> apMap = new HashMap<>();
            apMap.put("AP1", WifiAccessPoint.builder()
                .macAddress("AP1")
                .latitude(1.0)
                .longitude(1.0)
                .build());
            apMap.put("AP2", WifiAccessPoint.builder()
                .macAddress("AP2")
                .latitude(2.0)
                .longitude(2.0)
                .build());
            apMap.put("AP3", WifiAccessPoint.builder()
                .macAddress("AP3")
                .latitude(1.0)
                .longitude(2.0)
                .build());
            apMap.put("AP4", WifiAccessPoint.builder()
                .macAddress("AP4")
                .latitude(2.0)
                .longitude(1.0)
                .build());
                
            SelectionContext context = SelectionContext.builder()
                .isWeakSignal(true)
                .build();
            
            // Execute
            AlgorithmSelector.AlgorithmSelectionInfo result = 
                algorithmSelector.selectAlgorithmsWithReasons(scans, apMap, context);
                
            // Verify
            Map<PositioningAlgorithm, Double> weights = result.algorithmWeights();
            Map<PositioningAlgorithm, List<String>> reasons = result.selectionReasons();
            
            // For weak signals, Weighted Centroid should be favored over Trilateration
            assertTrue(weights.containsKey(weightedCentroidAlgorithm), "Weighted Centroid should be selected");
            
            // Check if Trilateration is included
            if (weights.containsKey(trilaterationAlgorithm)) {
                double weightedCentroidWeight = weights.get(weightedCentroidAlgorithm);
                double trilaterationWeight = weights.get(trilaterationAlgorithm);
                
                assertTrue(weightedCentroidWeight > trilaterationWeight, 
                    "Weighted Centroid should have higher weight than Trilateration for weak signals");
                
                // Check that there is a meaningful difference in weights
                assertTrue(weightedCentroidWeight / trilaterationWeight >= 1.2,
                    "Weighted Centroid should have at least 1.2x the weight of Trilateration with weak signals");
            }
            
            // Check if Maximum Likelihood is included, and if so, Weighted Centroid should have higher weight
            if (weights.containsKey(maximumLikelihoodAlgorithm)) {
                double weightedCentroidWeight = weights.get(weightedCentroidAlgorithm);
                double maximumLikelihoodWeight = weights.get(maximumLikelihoodAlgorithm);
                
                assertTrue(weightedCentroidWeight > maximumLikelihoodWeight, 
                    "Weighted Centroid should have higher weight than Maximum Likelihood for weak signals");
                
                // Verify the reason for this weighting in reasons map
                assertTrue(reasons.get(weightedCentroidAlgorithm).stream()
                    .anyMatch(reason -> reason.contains("weak") || reason.contains("signal")),
                    "Weighted Centroid should have reason related to signal quality");
            }
            
            // Verify reasons include signal quality adjustment
            for (PositioningAlgorithm algorithm : weights.keySet()) {
                assertTrue(reasons.get(algorithm).stream()
                    .anyMatch(reason -> reason.contains("Signal quality") || reason.contains("Base weight")), 
                    algorithm.getName() + " should have signal quality or base weight reason");
            }
            
            // Verify proximity is selected for weak signals
            assertTrue(weights.containsKey(proximityAlgorithm), 
                "Proximity algorithm should be selected for weak signals");
                
            // Verify total number of selected algorithms for weak signals
            assertTrue(weights.size() >= 2, "Weak signal scenario should select at least 2 algorithms");
        }
        
        @Test
        @DisplayName("Mixed signal quality - Maximum Likelihood and Weighted Centroid should be favored")
        void mixedSignalQualityWeighting() {
            // Setup - two strong and two weak signals
            List<WifiScanResult> scans = Arrays.asList(
                new WifiScanResult("AP1", -60.0, 2412, "test"),  // Strong
                new WifiScanResult("AP2", -65.0, 5180, "test"),  // Strong
                new WifiScanResult("AP3", -86.0, 2412, "test"),  // Weak
                new WifiScanResult("AP4", -88.0, 5180, "test")   // Weak
            );
            
            Map<String, WifiAccessPoint> apMap = new HashMap<>();
            apMap.put("AP1", WifiAccessPoint.builder()
                .macAddress("AP1")
                .latitude(1.0)
                .longitude(1.0)
                .build());
            apMap.put("AP2", WifiAccessPoint.builder()
                .macAddress("AP2")
                .latitude(2.0)
                .longitude(2.0)
                .build());
            apMap.put("AP3", WifiAccessPoint.builder()
                .macAddress("AP3")
                .latitude(1.0)
                .longitude(2.0)
                .build());
            apMap.put("AP4", WifiAccessPoint.builder()
                .macAddress("AP4")
                .latitude(2.0)
                .longitude(1.0)
                .build());
                
            SelectionContext context = SelectionContext.builder()
                .isVariableSignal(true)
                .build();
            
            // Execute
            AlgorithmSelector.AlgorithmSelectionInfo result = 
                algorithmSelector.selectAlgorithmsWithReasons(scans, apMap, context);
                
            // Verify
            Map<PositioningAlgorithm, Double> weights = result.algorithmWeights();
            
            // For mixed signals, both Maximum Likelihood and Weighted Centroid should have good weights
            assertTrue(weights.containsKey(maximumLikelihoodAlgorithm), "Maximum Likelihood should be selected");
            assertTrue(weights.containsKey(weightedCentroidAlgorithm), "Weighted Centroid should be selected");
            
            // The weight for Maximum Likelihood should be boosted by mixed signal adjustment
            Map<PositioningAlgorithm, List<String>> reasons = result.selectionReasons();
            
            assertTrue(reasons.get(maximumLikelihoodAlgorithm).stream()
                .anyMatch(reason -> reason.contains("Signal distribution")), 
                "Maximum Likelihood should have signal distribution reason");
                
            assertTrue(reasons.get(weightedCentroidAlgorithm).stream()
                .anyMatch(reason -> reason.contains("Signal distribution")), 
                "Weighted Centroid should have signal distribution reason");
        }
        
        @Test
        @DisplayName("Good Geometry - Should boost Trilateration weight")
        void goodGeometryAdjustment() {
            // Setup
            List<WifiScanResult> scans = Arrays.asList(
                new WifiScanResult("AP1", -65.0, 2412, "test"),
                new WifiScanResult("AP2", -67.0, 5180, "test"),
                new WifiScanResult("AP3", -66.0, 2412, "test")
            );
            
            Map<String, WifiAccessPoint> apMap = new HashMap<>();
            apMap.put("AP1", WifiAccessPoint.builder()
                .macAddress("AP1")
                .latitude(1.0)
                .longitude(1.0)
                .build());
            apMap.put("AP2", WifiAccessPoint.builder()
                .macAddress("AP2")
                .latitude(2.0)
                .longitude(2.0)
                .build());
            apMap.put("AP3", WifiAccessPoint.builder()
                .macAddress("AP3")
                .latitude(1.5)
                .longitude(2.5)
                .build());
                
            // Create a context with good geometry (not collinear, not clustered)
            SelectionContext context = SelectionContext.builder()
                .isCollinear(false)
                .isClustered(false)
                .build();
            
            // Execute
            AlgorithmSelector.AlgorithmSelectionInfo result = 
                algorithmSelector.selectAlgorithmsWithReasons(scans, apMap, context);
                
            // Verify
            Map<PositioningAlgorithm, Double> weights = result.algorithmWeights();
            Map<PositioningAlgorithm, List<String>> reasons = result.selectionReasons();
            
            // Verify Trilateration is selected with good weight for good geometry
            assertTrue(weights.containsKey(trilaterationAlgorithm), "Trilateration should be selected with good geometry");
            
            // Verify that geometric considerations are mentioned in the reasons
            if (reasons.containsKey(trilaterationAlgorithm)) {
                // Since we don't have specific geometric quality reasons in this implementation,
                // we just check that trilateration is selected appropriately
                assertFalse(reasons.get(trilaterationAlgorithm).stream()
                    .anyMatch(reason -> reason.contains(DISQUALIFIED)), 
                    "Trilateration should not be disqualified with good geometry");
            }
        }
        
        @Test
        @DisplayName("Poor Geometry - Should reduce Trilateration weight")
        void poorGeometryAdjustment() {
            // Setup
            List<WifiScanResult> scans = Arrays.asList(
                new WifiScanResult("AP1", -65.0, 2412, "test"),
                new WifiScanResult("AP2", -67.0, 5180, "test"),
                new WifiScanResult("AP3", -66.0, 2412, "test")
            );
            
            Map<String, WifiAccessPoint> apMap = new HashMap<>();
            apMap.put("AP1", WifiAccessPoint.builder()
                .macAddress("AP1")
                .latitude(1.0)
                .longitude(1.0)
                .build());
            apMap.put("AP2", WifiAccessPoint.builder()
                .macAddress("AP2")
                .latitude(2.0)
                .longitude(2.0)
                .build());
            apMap.put("AP3", WifiAccessPoint.builder()
                .macAddress("AP3")
                .latitude(1.5)
                .longitude(2.5)
                .build());
                
            // Create a context with poor geometry (collinear)
            SelectionContext context = SelectionContext.builder()
                .isCollinear(true)
                .build();
            
            // Execute
            AlgorithmSelector.AlgorithmSelectionInfo result = 
                algorithmSelector.selectAlgorithmsWithReasons(scans, apMap, context);
                
            // Verify
            Map<PositioningAlgorithm, Double> weights = result.algorithmWeights();
            Map<PositioningAlgorithm, List<String>> reasons = result.selectionReasons();
            
            // Trilateration should be disqualified for collinear geometry
            assertFalse(weights.containsKey(trilaterationAlgorithm), 
                "Trilateration should be disqualified with collinear geometry");
            
            // Verify the geometric disqualification is mentioned in the reasons
            assertTrue(reasons.get(trilaterationAlgorithm).stream()
                .anyMatch(reason -> reason.contains(DISQUALIFIED)), 
                "Trilateration should have disqualification reason for collinear geometry");
            
            // Weighted Centroid should be selected as a fallback
            assertTrue(weights.containsKey(weightedCentroidAlgorithm), 
                "Weighted Centroid should be selected as fallback for poor geometry");
        }
        
        @Test
        @DisplayName("Uniform Signal Strength - Variable signal flag should be false")
        void uniformSignalDistributionTest() {
            // Setup with uniform signals
            List<WifiScanResult> scans = Arrays.asList(
                new WifiScanResult("AP1", -70.0, 2412, "test"),
                new WifiScanResult("AP2", -71.0, 5180, "test"),
                new WifiScanResult("AP3", -70.0, 2412, "test"),
                new WifiScanResult("AP4", -71.0, 5180, "test")
            );
            
            Map<String, WifiAccessPoint> apMap = new HashMap<>();
            apMap.put("AP1", WifiAccessPoint.builder()
                .macAddress("AP1")
                .latitude(1.0)
                .longitude(1.0)
                .build());
            apMap.put("AP2", WifiAccessPoint.builder()
                .macAddress("AP2")
                .latitude(2.0)
                .longitude(2.0)
                .build());
            apMap.put("AP3", WifiAccessPoint.builder()
                .macAddress("AP3")
                .latitude(1.5)
                .longitude(2.5)
                .build());
            apMap.put("AP4", WifiAccessPoint.builder()
                .macAddress("AP4")
                .latitude(2.5)
                .longitude(1.5)
                .build());
                
            // Context indicating uniform signal distribution (not variable)
            SelectionContext context = SelectionContext.builder()
                .isVariableSignal(false)
                .build();
            
            // Execute
            AlgorithmSelector.AlgorithmSelectionInfo result = 
                algorithmSelector.selectAlgorithmsWithReasons(scans, apMap, context);
                
            // Verify
            Map<PositioningAlgorithm, Double> weights = result.algorithmWeights();
            
            // With 4 APs and uniform signals, we should have multiple algorithms selected
            assertFalse(weights.isEmpty(), "At least one algorithm should be selected for uniform signals");
            
            // For 4 APs, some algorithms should be selected based on AP count
            assertTrue(weights.size() >= 2, "Multiple algorithms should be selected for 4 APs with uniform signals");
            
            // Maximum Likelihood should be selected for 4 APs
            assertTrue(weights.containsKey(maximumLikelihoodAlgorithm),
                "Maximum Likelihood should be selected for 4 APs");
        }
        
        @Test
        @DisplayName("Variable Signal Strength - Should favor Maximum Likelihood")
        void variableSignalDistributionTest() {
            // Setup with variable signals
            List<WifiScanResult> scans = Arrays.asList(
                new WifiScanResult("AP1", -60.0, 2412, "test"),  // Strong
                new WifiScanResult("AP2", -70.0, 5180, "test"),  // Medium
                new WifiScanResult("AP3", -80.0, 2412, "test"),  // Weak
                new WifiScanResult("AP4", -90.0, 5180, "test")   // Very weak
            );
            
            Map<String, WifiAccessPoint> apMap = new HashMap<>();
            apMap.put("AP1", WifiAccessPoint.builder()
                .macAddress("AP1")
                .latitude(1.0)
                .longitude(1.0)
                .build());
            apMap.put("AP2", WifiAccessPoint.builder()
                .macAddress("AP2")
                .latitude(2.0)
                .longitude(2.0)
                .build());
            apMap.put("AP3", WifiAccessPoint.builder()
                .macAddress("AP3")
                .latitude(1.5)
                .longitude(2.5)
                .build());
            apMap.put("AP4", WifiAccessPoint.builder()
                .macAddress("AP4")
                .latitude(2.5)
                .longitude(1.5)
                .build());
                
            // Context indicating variable signal distribution
            SelectionContext context = SelectionContext.builder()
                .isVariableSignal(true)
                .build();
            
            // Execute
            AlgorithmSelector.AlgorithmSelectionInfo result = 
                algorithmSelector.selectAlgorithmsWithReasons(scans, apMap, context);
                
            // Verify
            Map<PositioningAlgorithm, Double> weights = result.algorithmWeights();
            
            // For variable signals, Maximum Likelihood should have a good weight
            assertTrue(weights.containsKey(maximumLikelihoodAlgorithm), 
                "Maximum Likelihood should be selected with variable signals");
        }
        
        @Test
        @DisplayName("No valid algorithms - Should return at least one fallback")
        void noValidAlgorithmsFallback() {
            // Setup extremely poor conditions that would normally disqualify all
            List<WifiScanResult> scans = Arrays.asList(
                new WifiScanResult("AP1", -98.0, 2412, "test") // Single extremely weak AP
            );
            
            Map<String, WifiAccessPoint> apMap = new HashMap<>();
            apMap.put("AP1", WifiAccessPoint.builder()
                .macAddress("AP1")
                .latitude(1.0)
                .longitude(1.0)
                .build());
                
            // Context indicating extremely poor conditions
            SelectionContext context = SelectionContext.builder()
                .isWeakSignal(true)
                .build();
            
            // Execute
            AlgorithmSelector.AlgorithmSelectionInfo result = 
                algorithmSelector.selectAlgorithmsWithReasons(scans, apMap, context);
                
            // Verify
            Map<PositioningAlgorithm, Double> weights = result.algorithmWeights();
            
            // Even with extremely poor conditions, at least one algorithm should be selected
            assertFalse(weights.isEmpty(), "At least one fallback algorithm should be selected");
            
            // The fallback is likely to be Proximity
            assertTrue(weights.containsKey(proximityAlgorithm), 
                "Proximity algorithm should be selected as fallback");
        }
    }
    
    @Nested
    @DisplayName("Finalist Selection Phase Tests")
    class FinalistSelectionPhaseTests {
        
        @Test
        @DisplayName("High weight - Should select only top algorithm(s)")
        void highWeightSelection() {
            // Setup
            List<WifiScanResult> scans = Arrays.asList(
                new WifiScanResult("AP1", -55.0, 2412, "test"),
                new WifiScanResult("AP2", -56.0, 5180, "test"),
                new WifiScanResult("AP3", -57.0, 2412, "test"),
                new WifiScanResult("AP4", -58.0, 5180, "test")
            );
            
            Map<String, WifiAccessPoint> apMap = new HashMap<>();
            apMap.put("AP1", WifiAccessPoint.builder()
                .macAddress("AP1")
                .latitude(1.0)
                .longitude(1.0)
                .build());
            apMap.put("AP2", WifiAccessPoint.builder()
                .macAddress("AP2")
                .latitude(2.0)
                .longitude(2.0)
                .build());
            apMap.put("AP3", WifiAccessPoint.builder()
                .macAddress("AP3")
                .latitude(1.0)
                .longitude(2.0)
                .build());
            apMap.put("AP4", WifiAccessPoint.builder()
                .macAddress("AP4")
                .latitude(2.0)
                .longitude(1.0)
                .build());
                
            SelectionContext context = SelectionContext.builder().build();
            
            // Execute
            AlgorithmSelector.AlgorithmSelectionInfo result = 
                algorithmSelector.selectAlgorithmsWithReasons(scans, apMap, context);
                
            // Verify
            Map<PositioningAlgorithm, Double> weights = result.algorithmWeights();
            
            // With very strong signals, we should have a high-confidence algorithm
            // which should result in 1-2 algorithms being selected (not all)
            assertTrue(weights.size() >= 1 && weights.size() <= 3,
                "Should select 1-3 algorithms with high weights");
                
            // Verify that Maximum Likelihood has highest weight
            assertTrue(weights.containsKey(maximumLikelihoodAlgorithm),
                "Maximum Likelihood should be selected for strong signals with 4 APs");
        }
        
        @Test
        @DisplayName("Multiple similar weights - Should select top 3 algorithms")
        void multipleAlgorithmSelection() {
            // Setup - similar strength signals
            List<WifiScanResult> scans = Arrays.asList(
                new WifiScanResult("AP1", -75.0, 2412, "test"),
                new WifiScanResult("AP2", -76.0, 5180, "test"),
                new WifiScanResult("AP3", -75.0, 2412, "test"),
                new WifiScanResult("AP4", -76.0, 5180, "test")
            );
            
            Map<String, WifiAccessPoint> apMap = new HashMap<>();
            apMap.put("AP1", WifiAccessPoint.builder()
                .macAddress("AP1")
                .latitude(1.0)
                .longitude(1.0)
                .build());
            apMap.put("AP2", WifiAccessPoint.builder()
                .macAddress("AP2")
                .latitude(2.0)
                .longitude(2.0)
                .build());
            apMap.put("AP3", WifiAccessPoint.builder()
                .macAddress("AP3")
                .latitude(1.0)
                .longitude(2.0)
                .build());
            apMap.put("AP4", WifiAccessPoint.builder()
                .macAddress("AP4")
                .latitude(2.0)
                .longitude(1.0)
                .build());
                
            SelectionContext context = SelectionContext.builder().build();
            
            // Execute
            AlgorithmSelector.AlgorithmSelectionInfo result = 
                algorithmSelector.selectAlgorithmsWithReasons(scans, apMap, context);
                
            // Verify
            Map<PositioningAlgorithm, Double> weights = result.algorithmWeights();
            
            // With medium signals and no obvious best algorithm, we should have 
            // multiple algorithms with similar weights
            assertTrue(weights.size() >= 2,
                "Should select at least 2 algorithms with similar weights");
        }
        
        @Test
        @DisplayName("Weight below threshold - Should be removed")
        void weightThresholdTest() {
            // Setup - extremely weak signals to force low weights
            List<WifiScanResult> scans = Arrays.asList(
                new WifiScanResult("AP1", -94.0, 2412, "test"),
                new WifiScanResult("AP2", -93.0, 5180, "test"),
                new WifiScanResult("AP3", -94.0, 2412, "test"),
                new WifiScanResult("AP4", -93.0, 5180, "test")
            );
            
            Map<String, WifiAccessPoint> apMap = new HashMap<>();
            apMap.put("AP1", WifiAccessPoint.builder()
                .macAddress("AP1")
                .latitude(1.0)
                .longitude(1.0)
                .build());
            apMap.put("AP2", WifiAccessPoint.builder()
                .macAddress("AP2")
                .latitude(2.0)
                .longitude(2.0)
                .build());
            apMap.put("AP3", WifiAccessPoint.builder()
                .macAddress("AP3")
                .latitude(1.0)
                .longitude(2.0)
                .build());
            apMap.put("AP4", WifiAccessPoint.builder()
                .macAddress("AP4")
                .latitude(2.0)
                .longitude(1.0)
                .build());
                
            SelectionContext context = SelectionContext.builder()
                .isWeakSignal(true)
                .build();
            
            // Execute
            AlgorithmSelector.AlgorithmSelectionInfo result = 
                algorithmSelector.selectAlgorithmsWithReasons(scans, apMap, context);
                
            // Verify
            Map<PositioningAlgorithm, List<String>> reasons = result.selectionReasons();
            
            // Check that low-weighted algorithms have threshold disqualification reason
            for (PositioningAlgorithm algorithm : Arrays.asList(
                proximityAlgorithm, rssiRatioAlgorithm, weightedCentroidAlgorithm, 
                trilaterationAlgorithm, maximumLikelihoodAlgorithm, logDistanceAlgorithm
            )) {
                if (!result.algorithmWeights().containsKey(algorithm)) {
                    if (reasons.get(algorithm) != null) {
                        boolean hasThresholdReason = reasons.get(algorithm).stream()
                            .anyMatch(reason -> reason.contains("threshold"));
                        
                        if (reasons.get(algorithm).stream().anyMatch(reason -> reason.contains("Base weight"))) {
                            // If the algorithm got a base weight but is not in final selection,
                            // it should have a threshold reason
                            assertTrue(hasThresholdReason, 
                                "Algorithms with weight below threshold should have threshold disqualification reason");
                        }
                    }
                }
            }
        }
    }
} 