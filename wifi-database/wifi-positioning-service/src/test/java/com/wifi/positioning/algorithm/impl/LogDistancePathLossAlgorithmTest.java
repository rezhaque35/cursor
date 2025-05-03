package com.wifi.positioning.algorithm.impl;

import com.wifi.positioning.dto.Position;
import com.wifi.positioning.dto.WifiScanResult;
import com.wifi.positioning.model.WifiAccessPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for the Log-Distance Path Loss Algorithm implementation.
 * These tests verify the algorithm's ability to handle various real-world scenarios
 * and validate its core mathematical model and assumptions.
 */
class LogDistancePathLossAlgorithmTest {

    private LogDistancePathLossAlgorithm algorithm;
    private static final double DELTA = 0.0001;

    @BeforeEach
    void setUp() {
        algorithm = new LogDistancePathLossAlgorithm();
    }

    /**
     * Basic input validation tests ensure the algorithm handles edge cases and invalid inputs gracefully.
     * These tests verify the robustness of the algorithm in production environments where
     * input data quality cannot be guaranteed.
     */
    @Nested
    @DisplayName("Basic Input Validation Tests")
    class InputValidationTests {
        /**
         * Verifies that the algorithm safely handles null WiFi scan data.
         * This scenario can occur when the scanning hardware fails or returns no data.
         */
        @Test
        @DisplayName("should return null when wifiScan is null")
        void shouldReturnNullWhenWifiScanIsNull() {
            assertNull(algorithm.calculatePosition(null, Collections.emptyList()));
        }

        /**
         * Verifies handling of empty scan results.
         * This can happen when no APs are detected in range.
         */
        @Test
        @DisplayName("should return null when wifiScan is empty")
        void shouldReturnNullWhenWifiScanIsEmpty() {
            assertNull(algorithm.calculatePosition(Collections.emptyList(), Collections.emptyList()));
        }

        /**
         * Verifies handling of missing AP reference data.
         * This scenario can occur when the AP database is unavailable or corrupted.
         */
        @Test
        @DisplayName("should return null when knownAPs is null")
        void shouldReturnNullWhenKnownAPsIsNull() {
            List<WifiScanResult> scans = Collections.singletonList(
                new WifiScanResult("00:11:22:33:44:55", -65.0, 2400, "test-ssid")
            );
            assertNull(algorithm.calculatePosition(scans, null));
        }
    }

    /**
     * Tests for vendor-specific path loss characteristics.
     * These tests verify that the algorithm correctly adjusts its calculations
     * based on vendor-specific AP characteristics and environmental factors.
     */
    @Nested
    @DisplayName("Vendor Information Handling Tests")
    class VendorInformationTests {
        private WifiAccessPoint createAP(String mac, String vendor, double lat, double lon) {
            return WifiAccessPoint.builder()
                .macAddress(mac)
                .vendor(vendor)
                .latitude(lat)
                .longitude(lon)
                .altitude(0.0)
                .horizontalAccuracy(5.0)
                .confidence(0.8)
                .signalStrengthAvg(-65.0)
                .build();
        }

        private WifiScanResult createScan(String mac, double signalStrength) {
            return new WifiScanResult(mac, signalStrength, 2400, "test-ssid");
        }

        /**
         * Verifies that the algorithm properly utilizes vendor information
         * to adjust path loss calculations and confidence levels.
         * Expected: Higher confidence due to known vendor characteristics.
         */
        @Test
        @DisplayName("should handle known vendor information correctly")
        void shouldHandleKnownVendorCorrectly() {
            // Create APs with known vendor
            List<WifiAccessPoint> knownAPs = Arrays.asList(
                createAP("00:11:22:33:44:55", "Cisco", 1.0, 1.0),
                createAP("66:77:88:99:AA:BB", "Cisco", 1.0, 2.0)
            );

            List<WifiScanResult> scans = Arrays.asList(
                createScan("00:11:22:33:44:55", -65),
                createScan("66:77:88:99:AA:BB", -70)
            );

            Position position = algorithm.calculatePosition(scans, knownAPs);
            assertNotNull(position);
            assertTrue(position.confidence() > 0.7); // High confidence with known vendors
        }

        /**
         * Verifies degraded performance handling when vendor info is missing.
         * The algorithm should still function but with reduced confidence.
         * Expected: Lower confidence due to unknown environmental characteristics.
         */
        @Test
        @DisplayName("should handle missing vendor information with reduced confidence")
        void shouldHandleMissingVendorWithReducedConfidence() {
            // Create APs without vendor information
            List<WifiAccessPoint> knownAPs = Arrays.asList(
                createAP("00:11:22:33:44:55", null, 1.0, 1.0),
                createAP("66:77:88:99:AA:BB", "", 1.0, 2.0)
            );

            List<WifiScanResult> scans = Arrays.asList(
                createScan("00:11:22:33:44:55", -65),
                createScan("66:77:88:99:AA:BB", -70)
            );

            Position position = algorithm.calculatePosition(scans, knownAPs);
            assertNotNull(position);
            assertTrue(position.confidence() < 0.8); // Lower confidence without vendor info
        }

        /**
         * Tests algorithm's ability to handle mixed vendor information.
         * Real-world deployments often have APs from multiple vendors.
         * Expected: Balanced confidence based on partial vendor information.
         */
        @Test
        @DisplayName("should handle mixed vendor information appropriately")
        void shouldHandleMixedVendorInformation() {
            // Create APs with mixed vendor information
            List<WifiAccessPoint> knownAPs = Arrays.asList(
                createAP("00:11:22:33:44:55", "Cisco", 1.0, 1.0),
                createAP("66:77:88:99:AA:BB", null, 1.0, 2.0),
                createAP("CC:DD:EE:FF:00:11", "Aruba", 1.0, 3.0)
            );

            List<WifiScanResult> scans = Arrays.asList(
                createScan("00:11:22:33:44:55", -65),
                createScan("66:77:88:99:AA:BB", -70),
                createScan("CC:DD:EE:FF:00:11", -75)
            );

            Position position = algorithm.calculatePosition(scans, knownAPs);
            assertNotNull(position);
            assertTrue(position.confidence() > 0.7); // Good confidence with some vendor info
        }
    }

    /**
     * Tests for path loss exponent calculations under different signal conditions.
     * These tests verify the algorithm's ability to adapt to varying signal qualities
     * and propagation environments.
     */
    @Nested
    @DisplayName("Path Loss Exponent Tests")
    class PathLossExponentTests {
        /**
         * Verifies path loss model adaptation to different signal strengths.
         * Tests three scenarios:
         * 1. Strong signals (-45dBm): Expect smaller path loss exponent
         * 2. Medium signals (-65dBm): Expect nominal path loss exponent
         * 3. Weak signals (-85dBm): Expect larger path loss exponent
         * Expected: Increasing position uncertainty with decreasing signal strength
         */
        @Test
        @DisplayName("should use appropriate path loss exponents for different signal strengths")
        void shouldUseAppropriatePathLossExponents() {
            // Test with different signal strengths
            List<WifiAccessPoint> knownAPs = Arrays.asList(
                WifiAccessPoint.builder()
                    .macAddress("00:11:22:33:44:55")
                    .latitude(1.0)
                    .longitude(1.0)
                    .altitude(0.0)
                    .horizontalAccuracy(5.0)
                    .confidence(0.8)
                    .signalStrengthAvg(-65.0)
                    .build()
            );

            // Test strong signal
            List<WifiScanResult> strongSignal = Collections.singletonList(
                new WifiScanResult("00:11:22:33:44:55", -45.0, 2400, "test-ssid")
            );
            Position strongPosition = algorithm.calculatePosition(strongSignal, knownAPs);
            assertNotNull(strongPosition);

            // Test medium signal
            List<WifiScanResult> mediumSignal = Collections.singletonList(
                new WifiScanResult("00:11:22:33:44:55", -65.0, 2400, "test-ssid")
            );
            Position mediumPosition = algorithm.calculatePosition(mediumSignal, knownAPs);
            assertNotNull(mediumPosition);

            // Test weak signal
            List<WifiScanResult> weakSignal = Collections.singletonList(
                new WifiScanResult("00:11:22:33:44:55", -85.0, 2400, "test-ssid")
            );
            Position weakPosition = algorithm.calculatePosition(weakSignal, knownAPs);
            assertNotNull(weakPosition);

            // Verify that distances increase with signal weakness
            assertTrue(weakPosition.accuracy() > mediumPosition.accuracy());
            assertTrue(mediumPosition.accuracy() > strongPosition.accuracy());
        }
    }

    /**
     * Tests for the core position calculation functionality.
     * These tests verify the algorithm's ability to produce accurate
     * position estimates under various real-world conditions.
     */
    @Nested
    @DisplayName("Position Calculation Tests")
    class PositionCalculationTests {
        /**
         * Comprehensive test of position calculation with mixed signal qualities.
         * Tests the algorithm's ability to:
         * 1. Handle multiple APs with different signal strengths
         * 2. Properly weight contributions based on signal quality
         * 3. Account for vendor-specific characteristics
         * Expected: Position estimates within reasonable bounds of actual AP locations
         */
        @Test
        @DisplayName("should calculate reasonable positions with mixed signal qualities")
        void shouldCalculateReasonablePositions() {
            List<WifiAccessPoint> knownAPs = Arrays.asList(
                WifiAccessPoint.builder()
                    .macAddress("AP1")
                    .vendor("Cisco")
                    .latitude(1.0)
                    .longitude(1.0)
                    .altitude(0.0)
                    .horizontalAccuracy(5.0)
                    .confidence(0.9)
                    .signalStrengthAvg(-65.0)
                    .build(),
                WifiAccessPoint.builder()
                    .macAddress("AP2")
                    .latitude(1.0)
                    .longitude(2.0)
                    .altitude(0.0)
                    .horizontalAccuracy(5.0)
                    .confidence(0.8)
                    .signalStrengthAvg(-70.0)
                    .build()
            );

            List<WifiScanResult> scans = Arrays.asList(
                new WifiScanResult("AP1", -65.0, 2400, "test-ssid"),
                new WifiScanResult("AP2", -70.0, 2400, "test-ssid")
            );

            Position position = algorithm.calculatePosition(scans, knownAPs);
            assertNotNull(position);
            // Accuracy: Should be within 6-10m for strong signals, log-distance path loss
            assertTrue(position.accuracy() >= 6.0 && position.accuracy() <= 10.0,
                "Expected accuracy between 6 and 10, got " + position.accuracy());
            // Confidence: Should be high for strong signals
            assertTrue(position.confidence() >= 0.69 && position.confidence() <= 0.95,
                "Expected confidence between 0.69 and 0.95, got " + position.confidence());
            // Latitude/Longitude: Should be between APs, with margin
            assertTrue(position.latitude() >= 0.9 && position.latitude() <= 2.1,
                "Expected latitude between 0.9 and 2.1, got " + position.latitude());
            assertTrue(position.longitude() >= 0.9 && position.longitude() <= 2.1,
                "Expected longitude between 0.9 and 2.1, got " + position.longitude());
        }
    }
} 