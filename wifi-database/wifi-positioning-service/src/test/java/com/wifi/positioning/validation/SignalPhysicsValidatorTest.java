package com.wifi.positioning.validation;

import com.wifi.positioning.dto.WifiScanResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SignalPhysicsValidator Tests")
class SignalPhysicsValidatorTest {

    private SignalPhysicsValidator validator;

    @BeforeEach
    void setUp() {
        validator = new SignalPhysicsValidator();
    }

    @Nested
    @DisplayName("Basic Validation Tests")
    class BasicValidationTests {
        @Test
        @DisplayName("should return false for null input")
        void shouldReturnFalseForNullInput() {
            assertFalse(validator.isPhysicallyPossible(null));
        }

        @Test
        @DisplayName("should return false for empty list")
        void shouldReturnFalseForEmptyList() {
            assertFalse(validator.isPhysicallyPossible(Collections.emptyList()));
        }

        @Test
        @DisplayName("should validate single valid signal")
        void shouldValidateSingleValidSignal() {
            List<WifiScanResult> scanResults = Collections.singletonList(
                new WifiScanResult("00:11:22:33:44:55", -65.0, 2412, "Test")
            );
            assertTrue(validator.isPhysicallyPossible(scanResults));
        }
    }

    @Nested
    @DisplayName("Signal Strength Range Tests")
    class SignalStrengthRangeTests {
        @Test
        @DisplayName("should reject signals outside valid range")
        void shouldRejectSignalsOutsideValidRange() {
            // Test signal too strong
            List<WifiScanResult> tooStrong = Collections.singletonList(
                new WifiScanResult("00:11:22:33:44:55", -29.9, 2412, "Test")
            );
            assertFalse(validator.isPhysicallyPossible(tooStrong));

            // Test signal too weak
            List<WifiScanResult> tooWeak = Collections.singletonList(
                new WifiScanResult("00:11:22:33:44:55", -100.1, 2412, "Test")
            );
            assertFalse(validator.isPhysicallyPossible(tooWeak));
        }

        @Test
        @DisplayName("should accept signals at boundary values")
        void shouldAcceptSignalsAtBoundaryValues() {
            List<WifiScanResult> scanResults = Arrays.asList(
                new WifiScanResult("00:11:22:33:44:55", -30.0, 2412, "Test"),
                new WifiScanResult("00:11:22:33:44:56", -100.0, 2412, "Test")
            );
            assertTrue(validator.isPhysicallyPossible(scanResults));
        }
    }

    @Nested
    @DisplayName("Same Frequency Tests")
    class SameFrequencyTests {
        @Test
        @DisplayName("should detect physically impossible signal relationships")
        void shouldDetectPhysicallyImpossibleSignalRelationships() {
            // Test Case 39 scenario: Strong signal mixed with very weak signals on same frequency
            List<WifiScanResult> impossibleScenario = Arrays.asList(
                new WifiScanResult("00:11:22:33:44:39", -90.0, 2412, "Test1"),
                new WifiScanResult("00:11:22:33:44:40", -40.0, 2412, "Test2"),
                new WifiScanResult("00:11:22:33:44:41", -95.0, 2412, "Test3")
            );
            assertFalse(validator.isPhysicallyPossible(impossibleScenario));
        }

        @Test
        @DisplayName("should accept reasonable signal variations")
        void shouldAcceptReasonableSignalVariations() {
            // Test reasonable signal variations on same frequency
            List<WifiScanResult> reasonableScenario = Arrays.asList(
                new WifiScanResult("00:11:22:33:44:55", -65.0, 2412, "Test1"),
                new WifiScanResult("00:11:22:33:44:56", -75.0, 2412, "Test2"),
                new WifiScanResult("00:11:22:33:44:57", -85.0, 2412, "Test3")
            );
            assertTrue(validator.isPhysicallyPossible(reasonableScenario));
        }
    }

    @Nested
    @DisplayName("Different Frequency Tests")
    class DifferentFrequencyTests {
        @Test
        @DisplayName("should allow larger variations across different frequencies")
        void shouldAllowLargerVariationsAcrossDifferentFrequencies() {
            List<WifiScanResult> mixedFrequencyScenario = Arrays.asList(
                new WifiScanResult("00:11:22:33:44:55", -45.0, 2412, "Test1"),
                new WifiScanResult("00:11:22:33:44:56", -90.0, 5180, "Test2")
            );
            assertTrue(validator.isPhysicallyPossible(mixedFrequencyScenario));
        }
    }

    @Nested
    @DisplayName("Strong Signal Tests")
    class StrongSignalTests {
        @Test
        @DisplayName("should enforce stricter rules for strong signals")
        void shouldEnforceStricterRulesForStrongSignals() {
            // Test strong signal with inconsistent nearby signals
            List<WifiScanResult> inconsistentStrong = Arrays.asList(
                new WifiScanResult("00:11:22:33:44:55", -35.0, 2412, "Test1"),
                new WifiScanResult("00:11:22:33:44:56", -95.0, 2412, "Test2")
            );
            assertFalse(validator.isPhysicallyPossible(inconsistentStrong));

            // Test strong signals with consistent nearby signals
            List<WifiScanResult> consistentStrong = Arrays.asList(
                new WifiScanResult("00:11:22:33:44:55", -45.0, 2412, "Test1"),
                new WifiScanResult("00:11:22:33:44:56", -65.0, 2412, "Test2")
            );
            assertTrue(validator.isPhysicallyPossible(consistentStrong));
        }
    }
} 