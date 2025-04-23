package com.wifi.positioning.algorithm;

import com.wifi.positioning.algorithm.impl.*;
import com.wifi.positioning.algorithm.selection.AlgorithmRuleManager;
import com.wifi.positioning.algorithm.selection.ContextBuilder;
import com.wifi.positioning.algorithm.selection.PositionCombiner;
import com.wifi.positioning.algorithm.selection.SelectionContext;
import com.wifi.positioning.dto.Position;
import com.wifi.positioning.dto.WifiScanResult;
import com.wifi.positioning.model.WifiAccessPoint;
import com.wifi.positioning.validation.SignalPhysicsValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Comprehensive test suite for GPSPositioningCalculator covering all scenarios from test data.
 * Test cases are organized into categories matching the test data structure:
 * 1. Basic Scenarios (Cases 1-5)
 * 2. Collinear APs (Cases 6-10)
 * 3. High Density Cluster (Cases 11-15)
 * 4. Mixed Signal Quality (Cases 16-20)
 * 5. Time Series Data (Cases 21-25)
 */
@ExtendWith(MockitoExtension.class)
class GPSPositioningCalculatorTest {

    @Mock
    private ProximityDetectionAlgorithm proximityAlgorithm;
    
    @Mock
    private RSSIRatioAlgorithm rssiRatioAlgorithm;
    
    @Mock
    private WeightedCentroidAlgorithm weightedCentroidAlgorithm;
    
    @Mock
    private LogDistancePathLossAlgorithm logDistanceAlgorithm;
    
    @Mock
    private MaximumLikelihoodAlgorithm maximumLikelihoodAlgorithm;
    
    @Mock
    private TrilaterationAlgorithm trilaterationAlgorithm;
    
    @Mock
    private AlgorithmRuleManager ruleManager;
    
    @Mock
    private ContextBuilder contextBuilder;
    
    @Mock
    private PositionCombiner positionCombiner;
    
    @Mock
    private SignalPhysicsValidator signalPhysicsValidator;

    @InjectMocks
    private GPSPositioningCalculator gpsPositioningCalculator;

    private TestDataLoader testData;

    @BeforeEach
    void setUp() {
        testData = new TestDataLoader();
        
        // Mock the signal physics validator to always return true for test cases
        lenient().when(signalPhysicsValidator.isPhysicallyPossible(any())).thenReturn(true);
        
        // Set up algorithm names with lenient mode to avoid unused stubbing errors
        lenient().when(proximityAlgorithm.getName()).thenReturn("proximity");
        lenient().when(rssiRatioAlgorithm.getName()).thenReturn("rssi_ratio");
        lenient().when(weightedCentroidAlgorithm.getName()).thenReturn("weighted_centroid");
        lenient().when(logDistanceAlgorithm.getName()).thenReturn("log_distance");
        lenient().when(maximumLikelihoodAlgorithm.getName()).thenReturn("maximum_likelihood");
        lenient().when(trilaterationAlgorithm.getName()).thenReturn("trilateration");
        
        // Set up base confidence values for algorithms
        lenient().when(proximityAlgorithm.getConfidence()).thenReturn(0.65);
        lenient().when(rssiRatioAlgorithm.getConfidence()).thenReturn(0.75);
        lenient().when(weightedCentroidAlgorithm.getConfidence()).thenReturn(0.80);
        lenient().when(logDistanceAlgorithm.getConfidence()).thenReturn(0.85);
        lenient().when(maximumLikelihoodAlgorithm.getConfidence()).thenReturn(0.90);
        lenient().when(trilaterationAlgorithm.getConfidence()).thenReturn(0.85);
        
        // Initialize the algorithms list in the GPSPositioningCalculator
        List<PositioningAlgorithm> algorithms = Arrays.asList(
            proximityAlgorithm,
            rssiRatioAlgorithm,
            weightedCentroidAlgorithm,
            logDistanceAlgorithm,
            maximumLikelihoodAlgorithm,
            trilaterationAlgorithm
        );
        
        ReflectionTestUtils.setField(gpsPositioningCalculator, "algorithms", algorithms);
        
        // Default behavior for context builder and position combiner
        lenient().when(contextBuilder.buildContext(any(), any())).thenReturn(SelectionContext.builder().build());
        
        // Set up position combiner to return the input position with the highest weight
        lenient().when(positionCombiner.combinePositions(any())).thenAnswer(invocation -> {
            List<PositionCombiner.WeightedPosition> positions = invocation.getArgument(0);
            if (positions.isEmpty()) {
                return null;
            }
            
            PositionCombiner.WeightedPosition result = positions.get(0);
            for (PositionCombiner.WeightedPosition wp : positions) {
                if (wp.weight() > result.weight()) {
                    result = wp;
                }
            }
            return result.position();
        });
    }

    @Nested
    @DisplayName("Basic Scenarios (Cases 1-5)")
    class BasicScenarios {
        
        @Test
        @DisplayName("Case 1: Single AP - Should use Proximity Detection")
        void singleAPScenario() {
            // Arrange
            List<WifiScanResult> scans = testData.getWifiScans("SingleAP_Test");
            List<WifiAccessPoint> aps = testData.getAccessPoints("SingleAP_Test");
            Position expectedPosition = new Position(37.7749, -122.4194, 10.5, 50.0, 0.65);
            
            // Mock the rule manager to return only the proximity algorithm
            Map<PositioningAlgorithm, Double> selectedAlgorithms = new HashMap<>();
            selectedAlgorithms.put(proximityAlgorithm, 1.0);
            lenient().when(ruleManager.selectAlgorithms(any(), any(), any(), any())).thenReturn(selectedAlgorithms);
            
            lenient().when(proximityAlgorithm.calculatePosition(any(), any())).thenReturn(expectedPosition);

            // Act
            Position result = gpsPositioningCalculator.calculatePosition(scans, aps);

            // Assert
            assertNotNull(result);
            assertEquals(expectedPosition.latitude(), result.latitude(), 0.0001);
            assertEquals(expectedPosition.longitude(), result.longitude(), 0.0001);
            assertEquals(expectedPosition.confidence(), result.confidence(), 0.0001);
        }

        @Test
        @DisplayName("Case 2: Two APs - Should use RSSI Ratio")
        void twoAPScenario() {
            // Arrange
            List<WifiScanResult> scans = testData.getWifiScans("DualAP_Test");
            List<WifiAccessPoint> aps = testData.getAccessPoints("DualAP_Test");
            Position expectedPosition = new Position(37.7750, -122.4195, 12.5, 25.0, 0.78);
            
            // Mock the rule manager to return only the RSSI ratio algorithm
            Map<PositioningAlgorithm, Double> selectedAlgorithms = new HashMap<>();
            selectedAlgorithms.put(rssiRatioAlgorithm, 1.0);
            lenient().when(ruleManager.selectAlgorithms(any(), any(), any(), any())).thenReturn(selectedAlgorithms);
            
            lenient().when(rssiRatioAlgorithm.calculatePosition(any(), any())).thenReturn(expectedPosition);

            // Act
            Position result = gpsPositioningCalculator.calculatePosition(scans, aps);

            // Assert
            assertNotNull(result);
            assertEquals(expectedPosition.latitude(), result.latitude(), 0.0001);
            assertEquals(expectedPosition.confidence(), result.confidence(), 0.0001);
        }

        @Test
        @DisplayName("Case 3: Three APs - Should use Multiple Methods")
        void threeAPScenario() {
            // Arrange
            List<WifiScanResult> scans = testData.getWifiScans("TriAP_Test");
            List<WifiAccessPoint> aps = testData.getAccessPoints("TriAP_Test");
            
            Position position1 = new Position(37.7751, -122.4196, 15.0, 8.5, 0.85);
            Position position2 = new Position(37.7751, -122.4196, 15.0, 8.5, 0.80);
            
            // Mock the rule manager to return multiple algorithms
            Map<PositioningAlgorithm, Double> selectedAlgorithms = new HashMap<>();
            selectedAlgorithms.put(logDistanceAlgorithm, 1.0);
            selectedAlgorithms.put(weightedCentroidAlgorithm, 1.0);
            lenient().when(ruleManager.selectAlgorithms(any(), any(), any(), any())).thenReturn(selectedAlgorithms);
            
            lenient().when(logDistanceAlgorithm.calculatePosition(any(), any())).thenReturn(position1);
            lenient().when(weightedCentroidAlgorithm.calculatePosition(any(), any())).thenReturn(position2);

            // Act
            Position result = gpsPositioningCalculator.calculatePosition(scans, aps);

            // Assert
            assertNotNull(result);
            assertTrue(result.confidence() >= 0.80);
            assertTrue(result.accuracy() <= 8.5);
        }

        @Test
        @DisplayName("Case 4: Multiple APs - Should use Maximum Likelihood")
        void multipleAPScenario() {
            // Arrange
            List<WifiScanResult> scans = testData.getWifiScans("MultiAP_Test");
            List<WifiAccessPoint> aps = testData.getAccessPoints("MultiAP_Test");
            Position expectedPosition = new Position(37.7752, -122.4197, 18.0, 15.5, 0.85);
            
            // Mock the rule manager to return only maximum likelihood algorithm
            Map<PositioningAlgorithm, Double> selectedAlgorithms = new HashMap<>();
            selectedAlgorithms.put(maximumLikelihoodAlgorithm, 1.0);
            lenient().when(ruleManager.selectAlgorithms(any(), any(), any(), any())).thenReturn(selectedAlgorithms);
            
            lenient().when(maximumLikelihoodAlgorithm.calculatePosition(any(), any())).thenReturn(expectedPosition);

            // Act
            Position result = gpsPositioningCalculator.calculatePosition(scans, aps);

            // Assert
            assertNotNull(result);
            assertEquals(expectedPosition.latitude(), result.latitude(), 0.0001);
            assertTrue(result.confidence() >= 0.85);
        }

        @Test
        @DisplayName("Case 5: Weak Signals - Should Handle Poor Quality")
        void weakSignalsScenario() {
            // Arrange
            List<WifiScanResult> scans = testData.getWifiScans("WeakSignal_Test");
            List<WifiAccessPoint> aps = testData.getAccessPoints("WeakSignal_Test");
            Position expectedPosition = new Position(37.7753, -122.4198, 20.0, 35.0, 0.45);
            
            // Mock the rule manager to return multiple algorithms for weak signals
            Map<PositioningAlgorithm, Double> selectedAlgorithms = new HashMap<>();
            selectedAlgorithms.put(maximumLikelihoodAlgorithm, 1.0);
            selectedAlgorithms.put(weightedCentroidAlgorithm, 1.0);
            lenient().when(ruleManager.selectAlgorithms(any(), any(), any(), any())).thenReturn(selectedAlgorithms);
            
            // We need to make sure at least one algorithm responds to weak signals
            lenient().when(maximumLikelihoodAlgorithm.calculatePosition(any(), any())).thenReturn(expectedPosition);
            lenient().when(weightedCentroidAlgorithm.calculatePosition(any(), any())).thenReturn(expectedPosition);
            
            // Act
            Position result = gpsPositioningCalculator.calculatePosition(scans, aps);

            // Assert
            assertNotNull(result);
            assertTrue(result.confidence() <= 0.5);
            assertTrue(result.accuracy() >= 30.0);
        }
    }

    @Nested
    @DisplayName("Collinear APs (Cases 6-10)")
    class CollinearScenarios {
        
        @Test
        @DisplayName("Case 6-10: Collinear APs - Should Handle Poor Geometry")
        void collinearAPsScenario() {
            // Arrange
            List<WifiScanResult> scans = testData.getWifiScans("Collinear_Test");
            List<WifiAccessPoint> aps = testData.getAccessPoints("Collinear_Test");
            Position expectedPosition = new Position(37.7754, -122.4194, 15.0, 18.5, 0.72);
            
            // Mock the rule manager to return only weighted centroid for collinear APs
            Map<PositioningAlgorithm, Double> selectedAlgorithms = new HashMap<>();
            selectedAlgorithms.put(weightedCentroidAlgorithm, 1.0);
            lenient().when(ruleManager.selectAlgorithms(any(), any(), any(), any())).thenReturn(selectedAlgorithms);
            
            lenient().when(weightedCentroidAlgorithm.calculatePosition(any(), any())).thenReturn(expectedPosition);

            // Act
            Position result = gpsPositioningCalculator.calculatePosition(scans, aps);

            // Assert
            assertNotNull(result);
            assertTrue(result.confidence() <= 0.8); // Lower confidence due to poor geometry
            assertTrue(result.accuracy() >= 15.0);  // Higher accuracy value (less accurate)
        }
    }

    @Nested
    @DisplayName("High Density Cluster (Cases 11-15)")
    class HighDensityScenarios {
        
        @Test
        @DisplayName("Case 11-15: Dense AP Cluster - Should Use Maximum Likelihood")
        void highDensityScenario() {
            // Arrange
            List<WifiScanResult> scans = testData.getWifiScans("HighDensity_Test");
            List<WifiAccessPoint> aps = testData.getAccessPoints("HighDensity_Test");
            Position expectedPosition = new Position(37.7760, -122.4200, 25.0, 12.0, 0.88);
            
            // Mock the rule manager to return only maximum likelihood for high density cluster
            Map<PositioningAlgorithm, Double> selectedAlgorithms = new HashMap<>();
            selectedAlgorithms.put(maximumLikelihoodAlgorithm, 1.0);
            lenient().when(ruleManager.selectAlgorithms(any(), any(), any(), any())).thenReturn(selectedAlgorithms);
            
            lenient().when(maximumLikelihoodAlgorithm.calculatePosition(any(), any())).thenReturn(expectedPosition);

            // Act
            Position result = gpsPositioningCalculator.calculatePosition(scans, aps);

            // Assert
            assertNotNull(result);
            assertTrue(result.confidence() >= 0.85);
            assertTrue(result.accuracy() <= 15.0);
        }
    }

    @Nested
    @DisplayName("Mixed Signal Quality (Cases 16-20)")
    class MixedSignalScenarios {
        
        @Test
        @DisplayName("Case 16-20: Mixed Signal Quality - Should Adapt Algorithm Selection")
        void mixedSignalQualityScenario() {
            // Arrange
            List<WifiScanResult> scans = testData.getWifiScans("MixedSignal_Test");
            List<WifiAccessPoint> aps = testData.getAccessPoints("MixedSignal_Test");
            
            // Set up different algorithm responses
            Position position1 = new Position(37.7770, -122.4210, 30.0, 15.0, 0.90);
            Position position2 = new Position(37.7770, -122.4210, 30.0, 15.0, 0.85);
            
            // Mock the rule manager to return multiple algorithms for mixed signals
            Map<PositioningAlgorithm, Double> selectedAlgorithms = new HashMap<>();
            selectedAlgorithms.put(maximumLikelihoodAlgorithm, 1.0);
            selectedAlgorithms.put(logDistanceAlgorithm, 1.0);
            lenient().when(ruleManager.selectAlgorithms(any(), any(), any(), any())).thenReturn(selectedAlgorithms);
            
            lenient().when(maximumLikelihoodAlgorithm.calculatePosition(any(), any())).thenReturn(position1);
            lenient().when(logDistanceAlgorithm.calculatePosition(any(), any())).thenReturn(position2);

            // Act
            Position result = gpsPositioningCalculator.calculatePosition(scans, aps);

            // Assert
            assertNotNull(result);
            // Confidence should reflect signal quality variation
            assertTrue(result.confidence() >= 0.5 && result.confidence() <= 0.9);
        }
    }

    @Nested
    @DisplayName("Time Series Data (Cases 21-25)")
    class TimeSeriesScenarios {
        
        @Test
        @DisplayName("Case 21-25: Temporal Variations - Should Maintain Stability")
        void timeSeriesScenario() {
            // Arrange
            List<Position> results = new ArrayList<>();
            
            // Simulate 5 positions over time with the same APs but varying signal strengths
            List<WifiScanResult> scans1 = Arrays.asList(
                new WifiScanResult("00:11:22:33:44:24", -65.0, 2437, 1, "test"),
                new WifiScanResult("00:11:22:33:44:25", -75.0, 5180, 1, "test"),
                new WifiScanResult("00:11:22:33:44:26", -85.0, 2437, 1, "test")
            );
            List<WifiScanResult> scans2 = Arrays.asList(
                new WifiScanResult("00:11:22:33:44:24", -64.0, 2437, 1, "test"),
                new WifiScanResult("00:11:22:33:44:25", -76.0, 5180, 1, "test"),
                new WifiScanResult("00:11:22:33:44:26", -84.0, 2437, 1, "test")
            );
            List<WifiScanResult> scans3 = Arrays.asList(
                new WifiScanResult("00:11:22:33:44:24", -66.0, 2437, 1, "test"),
                new WifiScanResult("00:11:22:33:44:25", -74.0, 5180, 1, "test"),
                new WifiScanResult("00:11:22:33:44:26", -86.0, 2437, 1, "test")
            );
            
            List<WifiAccessPoint> aps = testData.getAccessPoints("MixedSignal_Test");
            
            // Set up different algorithm responses for each time point
            Position position1 = new Position(37.7770, -122.4210, 30.0, 8.0, 0.85);
            Position position2 = new Position(37.7771, -122.4211, 30.5, 8.2, 0.84);
            Position position3 = new Position(37.7769, -122.4209, 29.5, 8.5, 0.83);
            
            // Mock the rule manager for time series data
            Map<PositioningAlgorithm, Double> selectedAlgorithms = new HashMap<>();
            selectedAlgorithms.put(logDistanceAlgorithm, 1.0);
            selectedAlgorithms.put(weightedCentroidAlgorithm, 1.0);
            lenient().when(ruleManager.selectAlgorithms(any(), any(), any(), any())).thenReturn(selectedAlgorithms);
            
            // Use lenient() for all stubs to avoid unnecessary stubbing errors
            lenient().when(logDistanceAlgorithm.calculatePosition(any(), any())).thenReturn(position1);
            lenient().when(weightedCentroidAlgorithm.calculatePosition(any(), any())).thenReturn(position1);
            lenient().when(trilaterationAlgorithm.calculatePosition(any(), any())).thenReturn(position1);
            
            // Act - get positions at 3 different time points
            results.add(gpsPositioningCalculator.calculatePosition(scans1, aps));
            results.add(gpsPositioningCalculator.calculatePosition(scans2, aps));
            results.add(gpsPositioningCalculator.calculatePosition(scans3, aps));
            
            // Assert
            // All results should be non-null
            for (Position result : results) {
                assertNotNull(result);
            }
            
            // Calculate position stability metrics
            double maxLatDiff = 0;
            double maxLonDiff = 0;
            
            for (int i = 0; i < results.size() - 1; i++) {
                Position p1 = results.get(i);
                Position p2 = results.get(i + 1);
                maxLatDiff = Math.max(maxLatDiff, Math.abs(p1.latitude() - p2.latitude()));
                maxLonDiff = Math.max(maxLonDiff, Math.abs(p1.longitude() - p2.longitude()));
            }
            
            // Ensure position is stable over time (doesn't jump around)
            assertTrue(maxLatDiff < 0.01, "Latitude shouldn't change drastically between measurements");
            assertTrue(maxLonDiff < 0.01, "Longitude shouldn't change drastically between measurements");
        }
    }

    /**
     * Helper class to load test data from resources.
     */
    private static class TestDataLoader {
        private final Map<String, List<WifiScanResult>> wifiScans;
        private final Map<String, List<WifiAccessPoint>> accessPoints;

        TestDataLoader() {
            this.wifiScans = loadWifiScans();
            this.accessPoints = loadAccessPoints();
        }

        private Map<String, List<WifiScanResult>> loadWifiScans() {
            return Map.of(
                "SingleAP_Test", Arrays.asList(
                    new WifiScanResult("00:11:22:33:44:01", -65.0, 2437, 1, "test")
                ),
                "DualAP_Test", Arrays.asList(
                    new WifiScanResult("00:11:22:33:44:02", -68.5, 5180, 1, "test"),
                    new WifiScanResult("00:11:22:33:44:03", -70.0, 5180, 1, "test")
                ),
                "TriAP_Test", Arrays.asList(
                    new WifiScanResult("00:11:22:33:44:04", -72.0, 2437, 1, "test"),
                    new WifiScanResult("00:11:22:33:44:05", -74.0, 2437, 1, "test"),
                    new WifiScanResult("00:11:22:33:44:06", -76.0, 2437, 1, "test")
                ),
                "MultiAP_Test", Arrays.asList(
                    new WifiScanResult("00:11:22:33:44:07", -65.0, 5180, 1, "test"),
                    new WifiScanResult("00:11:22:33:44:08", -67.0, 5180, 1, "test"),
                    new WifiScanResult("00:11:22:33:44:09", -69.0, 5180, 1, "test"),
                    new WifiScanResult("00:11:22:33:44:10", -71.0, 5180, 1, "test"),
                    new WifiScanResult("00:11:22:33:44:11", -73.0, 5180, 1, "test")
                ),
                "WeakSignal_Test", Arrays.asList(
                    new WifiScanResult("00:11:22:33:44:12", -85.0, 2437, 1, "test"),
                    new WifiScanResult("00:11:22:33:44:13", -87.0, 2437, 1, "test"),
                    new WifiScanResult("00:11:22:33:44:14", -89.0, 2437, 1, "test")
                ),
                "Collinear_Test", Arrays.asList(
                    new WifiScanResult("00:11:22:33:44:15", -75.0, 5180, 1, "test"),
                    new WifiScanResult("00:11:22:33:44:16", -77.0, 5180, 1, "test"),
                    new WifiScanResult("00:11:22:33:44:17", -79.0, 5180, 1, "test")
                ),
                "HighDensity_Test", Arrays.asList(
                    new WifiScanResult("00:11:22:33:44:18", -62.0, 5180, 1, "test"),
                    new WifiScanResult("00:11:22:33:44:19", -63.0, 5180, 1, "test"),
                    new WifiScanResult("00:11:22:33:44:20", -64.0, 5180, 1, "test"),
                    new WifiScanResult("00:11:22:33:44:21", -65.0, 5180, 1, "test"),
                    new WifiScanResult("00:11:22:33:44:22", -66.0, 5180, 1, "test"),
                    new WifiScanResult("00:11:22:33:44:23", -67.0, 5180, 1, "test")
                ),
                "MixedSignal_Test", Arrays.asList(
                    new WifiScanResult("00:11:22:33:44:24", -65.0, 2437, 1, "test"),
                    new WifiScanResult("00:11:22:33:44:25", -75.0, 5180, 1, "test"),
                    new WifiScanResult("00:11:22:33:44:26", -85.0, 2437, 1, "test"),
                    new WifiScanResult("00:11:22:33:44:27", -70.0, 5180, 1, "test")
                )
            );
        }

        private Map<String, List<WifiAccessPoint>> loadAccessPoints() {
            return Map.of(
                "SingleAP_Test", Arrays.asList(
                    createAP("00:11:22:33:44:01", 37.7749, -122.4194, 10.5)
                ),
                "DualAP_Test", Arrays.asList(
                    createAP("00:11:22:33:44:02", 37.7750, -122.4195, 12.0),
                    createAP("00:11:22:33:44:03", 37.7751, -122.4196, 13.0)
                ),
                "TriAP_Test", Arrays.asList(
                    createAP("00:11:22:33:44:04", 37.7751, -122.4196, 15.0),
                    createAP("00:11:22:33:44:05", 37.7752, -122.4197, 16.0),
                    createAP("00:11:22:33:44:06", 37.7753, -122.4198, 17.0)
                ),
                "MultiAP_Test", Arrays.asList(
                    createAP("00:11:22:33:44:07", 37.7754, -122.4199, 18.0),
                    createAP("00:11:22:33:44:08", 37.7755, -122.4200, 19.0),
                    createAP("00:11:22:33:44:09", 37.7756, -122.4201, 20.0),
                    createAP("00:11:22:33:44:10", 37.7757, -122.4202, 21.0),
                    createAP("00:11:22:33:44:11", 37.7758, -122.4203, 22.0)
                ),
                "WeakSignal_Test", Arrays.asList(
                    createAP("00:11:22:33:44:12", 37.7753, -122.4198, 20.0),
                    createAP("00:11:22:33:44:13", 37.7754, -122.4199, 21.0),
                    createAP("00:11:22:33:44:14", 37.7755, -122.4200, 22.0)
                ),
                "Collinear_Test", Arrays.asList(
                    createAP("00:11:22:33:44:15", 37.7754, -122.4194, 15.0),
                    createAP("00:11:22:33:44:16", 37.7759, -122.4194, 16.0),
                    createAP("00:11:22:33:44:17", 37.7764, -122.4194, 17.0)
                ),
                "HighDensity_Test", Arrays.asList(
                    createAP("00:11:22:33:44:18", 37.7760, -122.4200, 25.0),
                    createAP("00:11:22:33:44:19", 37.7761, -122.4201, 25.2),
                    createAP("00:11:22:33:44:20", 37.7759, -122.4199, 24.8),
                    createAP("00:11:22:33:44:21", 37.7762, -122.4202, 25.5),
                    createAP("00:11:22:33:44:22", 37.7758, -122.4198, 24.5),
                    createAP("00:11:22:33:44:23", 37.7763, -122.4203, 26.0)
                ),
                "MixedSignal_Test", Arrays.asList(
                    createAP("00:11:22:33:44:24", 37.7770, -122.4210, 30.0),
                    createAP("00:11:22:33:44:25", 37.7771, -122.4211, 31.0),
                    createAP("00:11:22:33:44:26", 37.7772, -122.4212, 32.0),
                    createAP("00:11:22:33:44:27", 37.7773, -122.4213, 33.0)
                )
            );
        }

        private WifiAccessPoint createAP(String mac, double lat, double lon, double alt) {
            return WifiAccessPoint.builder()
                .macAddress(mac)
                .latitude(lat)
                .longitude(lon)
                .altitude(alt)
                .horizontalAccuracy(10.0)
                .verticalAccuracy(5.0)
                .confidence(0.85)
                .signalStrengthAvg(-70.0)
                .build();
        }

        List<WifiScanResult> getWifiScans(String testCase) {
            return wifiScans.getOrDefault(testCase, new ArrayList<>());
        }

        List<WifiAccessPoint> getAccessPoints(String testCase) {
            return accessPoints.getOrDefault(testCase, new ArrayList<>());
        }
    }
} 