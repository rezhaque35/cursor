package com.wifi.positioning.algorithm.impl;

import com.wifi.positioning.dto.Position;
import com.wifi.positioning.dto.WifiAccessPoint;
import com.wifi.positioning.dto.WifiScanResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for the RSSI Ratio positioning algorithm.
 * These tests verify the algorithm's ability to calculate positions using signal strength ratios
 * and validate its behavior under various real-world conditions.
 * 
 * Test Categories:
 * 1. Basic Functionality - Core algorithm behavior
 * 2. Input Validation - Error handling and edge cases
 * 3. Position Calculation - Accuracy and precision
 * 4. Accuracy Tests - Performance under different conditions
 */
@DisplayName("RSSI Ratio Algorithm Tests")
class RSSIRatioAlgorithmTest {
    private RSSIRatioAlgorithm algorithm;

    @BeforeEach
    void setUp() {
        algorithm = new RSSIRatioAlgorithm();
    }

    private WifiAccessPoint createAP(String mac, String vendor, double lat, double lon) {
        return WifiAccessPoint.builder()
            .macAddress(mac)
            .vendor(vendor)
            .latitude(lat)
            .longitude(lon)
            .altitude(0.0)
            .horizontalAccuracy(5.0)
            .confidence(0.8)
            .build();
    }

    private WifiScanResult createScan(String mac, double signalStrength) {
        return WifiScanResult.of(mac, signalStrength, 2400, "test-ssid");
    }

    /**
     * Tests for core algorithm functionality and basic behaviors.
     * These tests verify that the algorithm provides consistent and reliable results
     * under normal operating conditions.
     */
    @Nested
    @DisplayName("Basic Functionality Tests")
    class BasicFunctionalityTests {
        /**
         * Verifies that the algorithm correctly identifies itself.
         * Important for algorithm selection in the hybrid positioning system.
         */
        @Test
        @DisplayName("should return correct algorithm name")
        void shouldReturnCorrectName() {
            assertEquals("RSSI Ratio", algorithm.getName());
        }

        /**
         * Validates that confidence values are within valid range (0-1).
         * Tests with a simple two-AP scenario to verify basic confidence calculation.
         * Expected: Confidence should be:
         * - Greater than 0 (some level of certainty)
         * - Less than or equal to 1 (no over-confidence)
         * - Reflective of signal quality and AP geometry
         */
        @Test
        @DisplayName("should return valid confidence level")
        void shouldReturnValidConfidence() {
            List<WifiAccessPoint> knownAPs = Arrays.asList(
                createAP("AP1", "Cisco", 1.0, 1.0),
                createAP("AP2", "Cisco", 1.0, 2.0)
            );

            List<WifiScanResult> scans = Arrays.asList(
                createScan("AP1", -65.0),
                createScan("AP2", -70.0)
            );

            Position position = algorithm.calculatePosition(scans, knownAPs);
            assertNotNull(position);
            assertTrue(position.confidence() > 0 && position.confidence() <= 1.0);
        }
    }

    /**
     * Tests for proper handling of invalid or edge case inputs.
     * These tests ensure the algorithm fails gracefully and provides
     * appropriate error messages when given problematic input data.
     */
    @Nested
    @DisplayName("Input Validation Tests")
    class InputValidationTests {
        /**
         * Verifies proper exception handling for null inputs.
         * Both WiFi scan results and known AP lists must be non-null.
         * Expected: IllegalArgumentException with descriptive message
         */
        @Test
        @DisplayName("should handle null inputs")
        void shouldHandleNullInputs() {
            assertThrows(IllegalArgumentException.class, () -> 
                algorithm.calculatePosition(null, Collections.emptyList()));
            assertThrows(IllegalArgumentException.class, () -> 
                algorithm.calculatePosition(Collections.emptyList(), null));
        }

        /**
         * Verifies proper exception handling for empty input collections.
         * Position calculation requires non-empty data sets.
         * Expected: IllegalArgumentException with descriptive message
         */
        @Test
        @DisplayName("should handle empty inputs")
        void shouldHandleEmptyInputs() {
            assertThrows(IllegalArgumentException.class, () -> 
                algorithm.calculatePosition(Collections.emptyList(), Collections.emptyList()));
        }

        /**
         * Validates the minimum AP requirement (2 APs) for ratio calculation.
         * RSSI ratio method requires at least two APs to compute ratios.
         * Expected: IllegalArgumentException when fewer than 2 APs provided
         */
        @Test
        @DisplayName("should require minimum number of access points")
        void shouldRequireMinimumAPs() {
            List<WifiAccessPoint> knownAPs = Collections.singletonList(
                createAP("AP1", "Cisco", 1.0, 1.0)
            );

            List<WifiScanResult> scans = Collections.singletonList(
                createScan("AP1", -65.0)
            );

            assertThrows(IllegalArgumentException.class, () -> 
                algorithm.calculatePosition(scans, knownAPs));
        }
    }

    /**
     * Tests for the core position calculation functionality.
     * These tests verify the algorithm's ability to:
     * 1. Calculate positions from RSSI ratios
     * 2. Handle varying signal strengths
     * 3. Provide reasonable position estimates
     */
    @Nested
    @DisplayName("Position Calculation Tests")
    class PositionCalculationTests {
        /**
         * Tests basic position calculation with two APs.
         * Verifies that:
         * 1. Position is between the two APs
         * 2. Calculated position has reasonable accuracy
         * 3. Result includes valid confidence value
         * Expected: Position should lie on a curve between the two APs,
         * weighted by their relative signal strengths
         */
        @Test
        @DisplayName("should calculate position with two access points")
        void shouldCalculatePositionWithTwoAPs() {
            List<WifiAccessPoint> knownAPs = Arrays.asList(
                createAP("AP1", "Cisco", 1.0, 1.0),
                createAP("AP2", "Cisco", 1.0, 2.0)
            );

            List<WifiScanResult> scans = Arrays.asList(
                createScan("AP1", -65.0),
                createScan("AP2", -70.0)
            );

            Position position = algorithm.calculatePosition(scans, knownAPs);
            assertNotNull(position);
            assertTrue(position.latitude() >= 1.0 && position.latitude() <= 2.0);
            assertTrue(position.longitude() >= 1.0 && position.longitude() <= 2.0);
            assertTrue(position.accuracy() > 0);
        }

        /**
         * Tests position calculation with significantly different signal strengths.
         * Verifies that:
         * 1. Position is biased toward stronger signal
         * 2. Weighting properly accounts for signal strength differences
         * Expected: Position should be closer to the AP with stronger signal (-50dBm)
         * than the AP with weaker signal (-80dBm)
         */
        @Test
        @DisplayName("should handle signal strength variations")
        void shouldHandleSignalStrengthVariations() {
            List<WifiAccessPoint> knownAPs = Arrays.asList(
                createAP("AP1", "Cisco", 1.0, 1.0),
                createAP("AP2", "Cisco", 1.0, 2.0)
            );

            List<WifiScanResult> scans = Arrays.asList(
                createScan("AP1", -50.0),
                createScan("AP2", -80.0)
            );

            Position position = algorithm.calculatePosition(scans, knownAPs);
            assertNotNull(position);
            // Position should be closer to AP1 due to stronger signal
            assertTrue(Math.abs(position.latitude() - 1.0) < Math.abs(position.latitude() - 2.0));
        }
    }

    /**
     * Tests focusing on the accuracy and precision of position estimates.
     * These tests verify that the algorithm provides results within
     * acceptable error margins under various conditions.
     */
    @Nested
    @DisplayName("Accuracy Tests")
    class AccuracyTests {
        /**
         * Tests position accuracy with three APs in a triangle configuration.
         * Verifies that:
         * 1. Position falls within the triangle formed by APs
         * 2. Accuracy estimate is reasonable (≤ 50m)
         * 3. Position reflects relative signal strengths
         * Expected: Position should be within the bounds of known APs
         * and have an accuracy appropriate for the signal strengths
         */
        @Test
        @DisplayName("should provide position within expected range")
        void shouldProvidePositionWithinRange() {
            List<WifiAccessPoint> knownAPs = Arrays.asList(
                createAP("AP1", "Cisco", 1.0, 1.0),
                createAP("AP2", "Cisco", 1.0, 2.0),
                createAP("AP3", "Cisco", 2.0, 1.5)
            );

            List<WifiScanResult> scans = Arrays.asList(
                createScan("AP1", -65.0),
                createScan("AP2", -70.0),
                createScan("AP3", -75.0)
            );

            Position position = algorithm.calculatePosition(scans, knownAPs);
            assertNotNull(position);
            assertTrue(position.latitude() >= 1.0 && position.latitude() <= 2.0);
            assertTrue(position.longitude() >= 1.0 && position.longitude() <= 2.0);
            assertTrue(position.accuracy() <= 50.0); // Assuming 50m is maximum acceptable accuracy
        }
    }

    @Nested
    @DisplayName("Accuracy and Confidence Range Tests")
    class AccuracyAndConfidenceRangeTests {
        @Test
        @DisplayName("should return expected accuracy and confidence for strong signals")
        void shouldReturnExpectedAccuracyAndConfidenceForStrongSignals() {
            List<WifiAccessPoint> aps = Arrays.asList(
                createAP("AP1", "Cisco", 1.0, 1.0),
                createAP("AP2", "Cisco", 1.0, 2.0)
            );
            List<WifiScanResult> scans = Arrays.asList(
                createScan("AP1", -65.0),
                createScan("AP2", -62.0)
            );
            Position result = algorithm.calculatePosition(scans, aps);
            assertNotNull(result);
            // Accuracy: Should be within 5-8m for strong signals, RSSI ratio
            assertTrue(result.accuracy() >= 5.0 && result.accuracy() <= 8.0,
                "Expected accuracy between 5 and 8, got " + result.accuracy());
            // Confidence: Should be high for strong signals
            assertTrue(result.confidence() >= 0.7 && result.confidence() <= 0.85,
                "Expected confidence between 0.7 and 0.85, got " + result.confidence());
            // Latitude/Longitude: Should be between APs, with margin
            assertTrue(result.latitude() >= 0.9 && result.latitude() <= 2.1,
                "Expected latitude between 0.9 and 2.1, got " + result.latitude());
            assertTrue(result.longitude() >= 0.9 && result.longitude() <= 2.1,
                "Expected longitude between 0.9 and 2.1, got " + result.longitude());
        }

        @Test
        @DisplayName("should return worse accuracy and lower confidence for weak/mismatched signals")
        void shouldReturnWorseAccuracyAndLowerConfidenceForWeakSignals() {
            List<WifiAccessPoint> aps = Arrays.asList(
                createAP("AP1", "Cisco", 1.0, 1.0),
                createAP("AP2", "Cisco", 1.0, 2.0)
            );
            List<WifiScanResult> scans = Arrays.asList(
                createScan("AP1", -85.0),
                createScan("AP2", -90.0)
            );
            Position result = algorithm.calculatePosition(scans, aps);
            assertNotNull(result);
            // Accuracy: Should be worse (>8m) for weak signals
            assertTrue(result.accuracy() > 8.0,
                "Expected accuracy > 8 for weak signals, got " + result.accuracy());
            // Confidence: Should be lower for weak signals
            assertTrue(result.confidence() < 0.7,
                "Expected confidence < 0.7 for weak signals, got " + result.confidence());
            // Latitude/Longitude: Should be between or near APs, allow wider margin for weak signals
            assertTrue(result.latitude() >= 0.7 && result.latitude() <= 2.3,
                "Expected latitude between 0.7 and 2.3, got " + result.latitude());
            assertTrue(result.longitude() >= 0.7 && result.longitude() <= 2.3,
                "Expected longitude between 0.7 and 2.3, got " + result.longitude());
        }
    }

    @Nested
    @DisplayName("Incomplete Data Handling Tests")
    class IncompleteDataHandlingTests {
        /**
         * Tests algorithm behavior with missing altitude data.
         * Verifies that:
         * 1. Algorithm can properly handle null altitude values
         * 2. Position is calculated using only 2D coordinates (latitude/longitude)
         * 3. Default altitude is set to 0.0
         * Expected: Valid position with 2D coordinates and altitude defaulted to 0.0
         */
        @Test
        @DisplayName("should handle null altitude values")
        void shouldHandleNullAltitudeValues() {
            // Create APs with null altitude values
            List<WifiAccessPoint> knownAPs = Arrays.asList(
                WifiAccessPoint.builder()
                    .macAddress("AP1")
                    .vendor("Vendor1")
                    .latitude(1.0)
                    .longitude(1.0)
                    .altitude(null) // Null altitude
                    .horizontalAccuracy(5.0)
                    .confidence(0.8)
                    .build(),
                WifiAccessPoint.builder()
                    .macAddress("AP2")
                    .vendor("Vendor2")
                    .latitude(3.0)
                    .longitude(3.0)
                    .altitude(null) // Null altitude
                    .horizontalAccuracy(5.0)
                    .confidence(0.8)
                    .build()
            );

            List<WifiScanResult> scans = Arrays.asList(
                createScan("AP1", -60.0),
                createScan("AP2", -80.0)
            );

            Position position = algorithm.calculatePosition(scans, knownAPs);
            
            // Verify position is calculated correctly
            assertNotNull(position);
            assertTrue(position.latitude() >= 1.0 && position.latitude() <= 3.0);
            assertTrue(position.longitude() >= 1.0 && position.longitude() <= 3.0);
            assertEquals(0.0, position.altitude(), 0.001);
        }

        /**
         * Tests algorithm behavior with mixed altitude data (some null, some not).
         * Verifies that:
         * 1. Algorithm correctly calculates altitude using only valid altitude values
         * 2. Position coordinates are calculated correctly
         * Expected: Valid position with altitude calculated from valid altitude data only
         */
        @Test
        @DisplayName("should handle mixed altitude values")
        void shouldHandleMixedAltitudeValues() {
            // Create APs with mixed altitude values (null and non-null)
            List<WifiAccessPoint> knownAPs = Arrays.asList(
                WifiAccessPoint.builder()
                    .macAddress("AP1")
                    .vendor("Vendor1")
                    .latitude(1.0)
                    .longitude(1.0)
                    .altitude(null) // Null altitude
                    .horizontalAccuracy(5.0)
                    .confidence(0.8)
                    .build(),
                WifiAccessPoint.builder()
                    .macAddress("AP2")
                    .vendor("Vendor2")
                    .latitude(3.0)
                    .longitude(3.0)
                    .altitude(20.0) // Valid altitude
                    .horizontalAccuracy(5.0)
                    .confidence(0.8)
                    .build(),
                WifiAccessPoint.builder()
                    .macAddress("AP3")
                    .vendor("Vendor3")
                    .latitude(2.0)
                    .longitude(2.0)
                    .altitude(30.0) // Valid altitude
                    .horizontalAccuracy(5.0)
                    .confidence(0.8)
                    .build()
            );

            List<WifiScanResult> scans = Arrays.asList(
                createScan("AP1", -60.0),
                createScan("AP2", -70.0),
                createScan("AP3", -80.0)
            );

            Position position = algorithm.calculatePosition(scans, knownAPs);
            
            // Verify position is calculated correctly
            assertNotNull(position);
            assertTrue(position.latitude() >= 1.0 && position.latitude() <= 3.0);
            assertTrue(position.longitude() >= 1.0 && position.longitude() <= 3.0);
            
            // Altitude should be calculated from AP2 and AP3 (since AP1 has null altitude)
            assertTrue(position.altitude() > 0.0, "Altitude should be calculated from valid altitude data");
            assertTrue(position.altitude() <= 30.0, "Altitude should not exceed maximum input value");
        }
    }

    /**
     * Tests for performance and stream optimization characteristics.
     * These tests verify that the stream refactoring maintains algorithm correctness
     * while improving code quality and SonarCube compliance.
     */
    @Nested
    @DisplayName("Stream Optimization and Performance Tests")
    class StreamOptimizationTests {
        /**
         * Verifies that the AP pair calculation produces consistent results.
         * This test ensures that the refactored stream processing maintains
         * the same mathematical output as the original implementation.
         */
        @Test
        @DisplayName("should calculate consistent AP pairs regardless of order")
        void shouldCalculateConsistentAPPairs() {
            List<WifiAccessPoint> knownAPs = Arrays.asList(
                createAP("AP1", "Cisco", 1.0, 1.0),
                createAP("AP2", "Cisco", 2.0, 2.0),
                createAP("AP3", "Cisco", 3.0, 3.0)
            );

            List<WifiScanResult> scans1 = Arrays.asList(
                createScan("AP1", -65.0),
                createScan("AP2", -70.0),
                createScan("AP3", -75.0)
            );

            List<WifiScanResult> scans2 = Arrays.asList(
                createScan("AP3", -75.0),
                createScan("AP1", -65.0),
                createScan("AP2", -70.0)
            );

            Position position1 = algorithm.calculatePosition(scans1, knownAPs);
            Position position2 = algorithm.calculatePosition(scans2, knownAPs);

            assertNotNull(position1);
            assertNotNull(position2);
            
            // Results should be very close (within tolerance for floating point precision)
            assertEquals(position1.latitude(), position2.latitude(), 0.0001,
                "Latitude should be consistent regardless of input order");
            assertEquals(position1.longitude(), position2.longitude(), 0.0001,
                "Longitude should be consistent regardless of input order");
            assertEquals(position1.accuracy(), position2.accuracy(), 0.1,
                "Accuracy should be consistent regardless of input order");
            assertEquals(position1.confidence(), position2.confidence(), 0.01,
                "Confidence should be consistent regardless of input order");
        }

        /**
         * Tests behavior with large number of APs to verify stream optimization benefits.
         * Verifies that the algorithm can handle scenarios with many APs efficiently.
         */
        @Test
        @DisplayName("should handle large number of APs efficiently")
        void shouldHandleLargeNumberOfAPsEfficiently() {
            // Create 10 APs in a grid pattern
            List<WifiAccessPoint> knownAPs = Arrays.asList(
                createAP("AP1", "Cisco", 1.0, 1.0),
                createAP("AP2", "Cisco", 1.0, 2.0),
                createAP("AP3", "Cisco", 1.0, 3.0),
                createAP("AP4", "Cisco", 2.0, 1.0),
                createAP("AP5", "Cisco", 2.0, 2.0),
                createAP("AP6", "Cisco", 2.0, 3.0),
                createAP("AP7", "Cisco", 3.0, 1.0),
                createAP("AP8", "Cisco", 3.0, 2.0),
                createAP("AP9", "Cisco", 3.0, 3.0),
                createAP("AP10", "Cisco", 1.5, 2.5)
            );

            // Create scan results with varying signal strengths
            List<WifiScanResult> scans = Arrays.asList(
                createScan("AP1", -60.0),
                createScan("AP2", -65.0),
                createScan("AP3", -70.0),
                createScan("AP4", -62.0),
                createScan("AP5", -58.0),
                createScan("AP6", -68.0),
                createScan("AP7", -75.0),
                createScan("AP8", -72.0),
                createScan("AP9", -80.0),
                createScan("AP10", -55.0)
            );

            long startTime = System.nanoTime();
            Position position = algorithm.calculatePosition(scans, knownAPs);
            long endTime = System.nanoTime();
            
            assertNotNull(position);
            // Verify position is within expected bounds
            assertTrue(position.latitude() >= 1.0 && position.latitude() <= 3.0);
            assertTrue(position.longitude() >= 1.0 && position.longitude() <= 3.0);
            assertTrue(position.accuracy() > 0);
            assertTrue(position.confidence() > 0 && position.confidence() <= 1.0);
            
            // Performance should be reasonable (less than 100ms for 10 APs = 45 pairs)
            long durationMs = (endTime - startTime) / 1_000_000;
            assertTrue(durationMs < 100, 
                "Algorithm should complete in reasonable time, took " + durationMs + "ms");
        }

        /**
         * Tests that the algorithm produces deterministic results for identical inputs.
         * This verifies that the stream optimization doesn't introduce any race conditions
         * or non-deterministic behavior.
         */
        @Test
        @DisplayName("should produce deterministic results for identical inputs")
        void shouldProduceDeterministicResults() {
            List<WifiAccessPoint> knownAPs = Arrays.asList(
                createAP("AP1", "Cisco", 1.0, 1.0),
                createAP("AP2", "Cisco", 2.0, 2.0),
                createAP("AP3", "Cisco", 3.0, 1.5)
            );

            List<WifiScanResult> scans = Arrays.asList(
                createScan("AP1", -65.0),
                createScan("AP2", -70.0),
                createScan("AP3", -68.0)
            );

            // Run calculation multiple times
            Position position1 = algorithm.calculatePosition(scans, knownAPs);
            Position position2 = algorithm.calculatePosition(scans, knownAPs);
            Position position3 = algorithm.calculatePosition(scans, knownAPs);

            assertNotNull(position1);
            assertNotNull(position2);
            assertNotNull(position3);
            
            // All results should be identical
            assertEquals(position1.latitude(), position2.latitude(), 1e-10);
            assertEquals(position1.latitude(), position3.latitude(), 1e-10);
            assertEquals(position1.longitude(), position2.longitude(), 1e-10);
            assertEquals(position1.longitude(), position3.longitude(), 1e-10);
            assertEquals(position1.accuracy(), position2.accuracy(), 1e-10);
            assertEquals(position1.accuracy(), position3.accuracy(), 1e-10);
            assertEquals(position1.confidence(), position2.confidence(), 1e-10);
            assertEquals(position1.confidence(), position3.confidence(), 1e-10);
        }
    }
} 