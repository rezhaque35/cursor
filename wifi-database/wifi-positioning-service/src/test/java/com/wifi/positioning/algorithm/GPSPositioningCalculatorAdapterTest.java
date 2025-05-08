package com.wifi.positioning.algorithm;

import com.wifi.positioning.dto.Position;
import com.wifi.positioning.dto.WifiScanResult;
import com.wifi.positioning.model.WifiAccessPoint;
import com.wifi.positioning.repository.WifiAccessPointRepository;
import com.wifi.positioning.validation.SignalPhysicsValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests for GPSPositioningCalculatorAdapter focused on verifying the algorithm name mapping.
 * This test ensures that the methodsUsed field in the result contains the correct algorithm names
 * rather than hardcoded values.
 */
@ExtendWith(MockitoExtension.class)
class GPSPositioningCalculatorAdapterTest {

    @Mock
    private GPSPositioningCalculator calculator;

    @Mock
    private WifiAccessPointRepository accessPointRepository;

    @Mock
    private SignalPhysicsValidator signalPhysicsValidator;
    
    @Mock
    private PositioningAlgorithm proximityAlgorithm;
    
    @Mock
    private PositioningAlgorithm rssiRatioAlgorithm;
    
    @Mock
    private PositioningAlgorithm trilaterationAlgorithm;
    
    @Mock
    private PositioningAlgorithm maximumLikelihoodAlgorithm;

    @InjectMocks
    private GPSPositioningCalculatorAdapter adapter;

    @BeforeEach
    void setUp() {
        // Always return true for signal physics validation
        when(signalPhysicsValidator.isPhysicallyPossible(any())).thenReturn(true);
        
        // Set up algorithm names with lenient() to avoid UnnecessaryStubbingException
        lenient().when(proximityAlgorithm.getName()).thenReturn("ProximityDetection");
        lenient().when(rssiRatioAlgorithm.getName()).thenReturn("RSSIRatio");
        lenient().when(trilaterationAlgorithm.getName()).thenReturn("Trilateration");
        lenient().when(maximumLikelihoodAlgorithm.getName()).thenReturn("MaximumLikelihood");
        
        // Set up default batch response for repository
        lenient().when(accessPointRepository.findByMacAddresses(any())).thenAnswer(invocation -> {
            Set<String> macs = invocation.getArgument(0);
            Map<String, WifiAccessPoint> result = new HashMap<>();
            
            for (String mac : macs) {
                WifiAccessPoint ap = createTestAP(mac, 37.7749, -122.4194, 10.0);
                // Mock the WifiAccessPoint to ensure getMacAddress returns the correct value
                lenient().when(ap.getMacAddress()).thenReturn(mac);
                result.put(mac, ap);
            }
            
            return result;
        });
    }

    @Test
    @DisplayName("Single AP Test - Should use Proximity Algorithm Name")
    void singleAPShouldUseProximityAlgorithmName() {
        // Create test data for a single AP scenario
        List<WifiScanResult> scanResults = Collections.singletonList(
            WifiScanResult.of("00:11:22:33:44:01", -65.0, 2437, "SingleAP_Test")
        );

        // Mock the repository to return a known AP
        WifiAccessPoint ap = createTestAP("00:11:22:33:44:01", 37.7749, -122.4194, 10.5);
        when(accessPointRepository.findByMacAddress("00:11:22:33:44:01")).thenReturn(Optional.of(ap));

        // Mock calculator to return a positioning result
        Position position = new Position(37.7749, -122.4194, 10.5, 50.0, 0.65);
        Map<PositioningAlgorithm, Double> algorithmWeights = Collections.singletonMap(proximityAlgorithm, 1.0);
        GPSPositioningCalculator.PositioningResult positioningResult = 
            new GPSPositioningCalculator.PositioningResult(position, algorithmWeights);
        when(calculator.calculatePosition(any(), any())).thenReturn(positioningResult);

        // Call the method under test
        Map<String, Object> result = adapter.calculatePosition(scanResults, new HashMap<>());

        // Verify the result
        assertNotNull(result);
        
        // Verify methodsUsed contains the correct algorithm name
        assertTrue(result.containsKey("methodsUsed"));
        @SuppressWarnings("unchecked")
        List<String> methodsUsed = (List<String>) result.get("methodsUsed");
        assertTrue(methodsUsed.contains("proximitydetection"));
    }

    @Test
    @DisplayName("Two APs Test - Should use RSSI Ratio Algorithm Name")
    void twoAPsShouldUseRSSIRatioAlgorithmName() {
        // Create test data for a two AP scenario
        List<WifiScanResult> scanResults = Arrays.asList(
            WifiScanResult.of("00:11:22:33:44:02", -68.5, 5180, "DualAP_Test"),
            WifiScanResult.of("00:11:22:33:44:03", -62.3, 2462, "DualAP_Test")
        );

        // Mock the repository to return known APs
        WifiAccessPoint ap1 = createTestAP("00:11:22:33:44:02", 37.7750, -122.4195, 12.5);
        WifiAccessPoint ap2 = createTestAP("00:11:22:33:44:03", 37.7751, -122.4196, 15.0);
        when(accessPointRepository.findByMacAddress("00:11:22:33:44:02")).thenReturn(Optional.of(ap1));
        when(accessPointRepository.findByMacAddress("00:11:22:33:44:03")).thenReturn(Optional.of(ap2));

        // Mock calculator to return a positioning result
        Position position = new Position(37.7750, -122.4195, 12.5, 25.0, 0.78);
        Map<PositioningAlgorithm, Double> algorithmWeights = Collections.singletonMap(rssiRatioAlgorithm, 1.0);
        GPSPositioningCalculator.PositioningResult positioningResult = 
            new GPSPositioningCalculator.PositioningResult(position, algorithmWeights);
        when(calculator.calculatePosition(any(), any())).thenReturn(positioningResult);

        // Call the method under test
        Map<String, Object> result = adapter.calculatePosition(scanResults, new HashMap<>());

        // Verify the result
        assertNotNull(result);
        
        // Verify methodsUsed contains the correct algorithm name
        assertTrue(result.containsKey("methodsUsed"));
        @SuppressWarnings("unchecked")
        List<String> methodsUsed = (List<String>) result.get("methodsUsed");
        assertTrue(methodsUsed.contains("rssiratio"));
    }

    @Test
    @DisplayName("Three APs Test - Should use Trilateration Algorithm Name")
    void threeAPsShouldUseTrilaterationAlgorithmName() {
        // Create test data for a three AP scenario
        List<WifiScanResult> scanResults = Arrays.asList(
            WifiScanResult.of("00:11:22:33:44:02", -68.5, 5180, "TriAP_Test"),
            WifiScanResult.of("00:11:22:33:44:03", -62.3, 2462, "TriAP_Test"),
            WifiScanResult.of("00:11:22:33:44:04", -71.2, 5240, "TriAP_Test")
        );

        // Mock the repository to return known APs
        WifiAccessPoint ap1 = createTestAP("00:11:22:33:44:02", 37.7750, -122.4195, 12.5);
        WifiAccessPoint ap2 = createTestAP("00:11:22:33:44:03", 37.7751, -122.4196, 15.0);
        WifiAccessPoint ap3 = createTestAP("00:11:22:33:44:04", 37.7752, -122.4197, 18.0);
        when(accessPointRepository.findByMacAddress("00:11:22:33:44:02")).thenReturn(Optional.of(ap1));
        when(accessPointRepository.findByMacAddress("00:11:22:33:44:03")).thenReturn(Optional.of(ap2));
        when(accessPointRepository.findByMacAddress("00:11:22:33:44:04")).thenReturn(Optional.of(ap3));

        // Mock calculator to return a positioning result
        Position position = new Position(37.7751, -122.4196, 15.0, 8.5, 0.92);
        Map<PositioningAlgorithm, Double> algorithmWeights = new HashMap<>();
        algorithmWeights.put(trilaterationAlgorithm, 1.0);
        GPSPositioningCalculator.PositioningResult positioningResult = 
            new GPSPositioningCalculator.PositioningResult(position, algorithmWeights);
        when(calculator.calculatePosition(any(), any())).thenReturn(positioningResult);

        // Call the method under test
        Map<String, Object> result = adapter.calculatePosition(scanResults, new HashMap<>());

        // Verify the result
        assertNotNull(result);
        
        // Verify methodsUsed contains the correct algorithm name
        assertTrue(result.containsKey("methodsUsed"));
        @SuppressWarnings("unchecked")
        List<String> methodsUsed = (List<String>) result.get("methodsUsed");
        assertTrue(methodsUsed.contains("trilateration"));
    }

    @Test
    @DisplayName("Multiple APs Test - Should use Maximum Likelihood Algorithm Name")
    void multipleAPsShouldUseMaximumLikelihoodAlgorithmName() {
        // Create test data for a multiple AP scenario
        List<WifiScanResult> scanResults = new ArrayList<>();
        
        // Add 4 APs
        for (int i = 1; i <= 4; i++) {
            String macAddress = String.format("00:11:22:33:44:%02d", i);
            double signalStrength = -60.0 - (i * 2); // Strong signal
            int frequency = i % 2 == 0 ? 5180 : 2462;
            
            scanResults.add(WifiScanResult.of(macAddress, signalStrength, frequency, "MultiAP_Test"));
            
            // Mock repository to return known AP
            WifiAccessPoint ap = createTestAP(macAddress, 37.7750 + (i * 0.0001), -122.4195 + (i * 0.0001), 12.5 + i);
            when(accessPointRepository.findByMacAddress(macAddress)).thenReturn(Optional.of(ap));
        }

        // Mock calculator to return a positioning result
        Position position = new Position(37.7752, -122.4197, 18.0, 15.5, 0.85);
        Map<PositioningAlgorithm, Double> algorithmWeights = new HashMap<>();
        algorithmWeights.put(maximumLikelihoodAlgorithm, 1.0);
        GPSPositioningCalculator.PositioningResult positioningResult = 
            new GPSPositioningCalculator.PositioningResult(position, algorithmWeights);
        when(calculator.calculatePosition(any(), any())).thenReturn(positioningResult);

        // Call the method under test
        Map<String, Object> result = adapter.calculatePosition(scanResults, new HashMap<>());

        // Verify the result
        assertNotNull(result);
        
        // Verify methodsUsed contains the correct algorithm name
        assertTrue(result.containsKey("methodsUsed"));
        @SuppressWarnings("unchecked")
        List<String> methodsUsed = (List<String>) result.get("methodsUsed");
        assertTrue(methodsUsed.contains("maximumlikelihood"));
    }

    @Test
    @DisplayName("Multiple Weighted Algorithms Test - Should Return All Algorithm Names")
    void multipleWeightedAlgorithmsShouldReturnAllNames() {
        // Create test data for a multiple algorithm scenario
        List<WifiScanResult> scanResults = Arrays.asList(
            WifiScanResult.of("00:11:22:33:44:02", -68.5, 5180, "MixedAP_Test"),
            WifiScanResult.of("00:11:22:33:44:03", -62.3, 2462, "MixedAP_Test"),
            WifiScanResult.of("00:11:22:33:44:04", -71.2, 5240, "MixedAP_Test")
        );

        // Mock the repository to return known APs
        WifiAccessPoint ap1 = createTestAP("00:11:22:33:44:02", 37.7750, -122.4195, 12.5);
        WifiAccessPoint ap2 = createTestAP("00:11:22:33:44:03", 37.7751, -122.4196, 15.0);
        WifiAccessPoint ap3 = createTestAP("00:11:22:33:44:04", 37.7752, -122.4197, 18.0);
        when(accessPointRepository.findByMacAddress("00:11:22:33:44:02")).thenReturn(Optional.of(ap1));
        when(accessPointRepository.findByMacAddress("00:11:22:33:44:03")).thenReturn(Optional.of(ap2));
        when(accessPointRepository.findByMacAddress("00:11:22:33:44:04")).thenReturn(Optional.of(ap3));

        // Mock calculator to return a positioning result with multiple algorithm weights
        Position position = new Position(37.7751, -122.4196, 15.0, 10.0, 0.85);
        Map<PositioningAlgorithm, Double> algorithmWeights = new HashMap<>();
        algorithmWeights.put(trilaterationAlgorithm, 0.7);
        algorithmWeights.put(rssiRatioAlgorithm, 0.3);
        
        Map<PositioningAlgorithm, List<String>> selectionReasons = new HashMap<>();
        GPSPositioningCalculator.PositioningResult positioningResult = 
            new GPSPositioningCalculator.PositioningResult(position, algorithmWeights, selectionReasons, null);
        when(calculator.calculatePosition(any(), any())).thenReturn(positioningResult);

        // Call the method under test
        Map<String, Object> result = adapter.calculatePosition(scanResults, new HashMap<>());

        // Verify the result
        assertNotNull(result);
        assertTrue(result.containsKey("methodsUsed"));
        
        // Verify both algorithms are in methodsUsed
        @SuppressWarnings("unchecked")
        List<String> methodsUsed = (List<String>) result.get("methodsUsed");
        assertTrue(methodsUsed.contains("trilateration"));
        assertTrue(methodsUsed.contains("rssiratio"));
    }

    private WifiAccessPoint createTestAP(String macAddress, double lat, double lon, double alt) {
        WifiAccessPoint ap = new WifiAccessPoint();
        ap.setMacAddress(macAddress);
        ap.setLatitude(lat);
        ap.setLongitude(lon);
        ap.setAltitude(alt);
        ap.setSsid("Test-SSID");
        ap.setConfidence(0.85);
        ap.setHorizontalAccuracy(10.0);
        ap.setVerticalAccuracy(5.0);
        ap.setFrequency(2437);
        ap.setVendor("Test-Vendor");
        ap.setGeohash("9q8yyk");
        ap.setStatus(WifiAccessPoint.STATUS_ACTIVE);
        return ap;
    }
} 