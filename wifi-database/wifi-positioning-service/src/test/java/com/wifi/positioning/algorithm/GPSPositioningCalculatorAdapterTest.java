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
        Map<String, Map<String, Object>> scanResultsMap = new HashMap<>();
        Map<String, Object> scanData = new HashMap<>();
        scanData.put("macAddress", "00:11:22:33:44:01");
        scanData.put("ssid", "SingleAP_Test");
        scanData.put("signalStrength", -65.0);
        scanData.put("frequency", 2437);
        scanResultsMap.put("00:11:22:33:44:01", scanData);

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
        Map<String, Object> result = adapter.calculatePosition(scanResultsMap, new HashMap<>());

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
        Map<String, Map<String, Object>> scanResultsMap = new HashMap<>();
        
        Map<String, Object> scanData1 = new HashMap<>();
        scanData1.put("macAddress", "00:11:22:33:44:02");
        scanData1.put("ssid", "DualAP_Test");
        scanData1.put("signalStrength", -68.5);
        scanData1.put("frequency", 5180);
        scanResultsMap.put("00:11:22:33:44:02", scanData1);
        
        Map<String, Object> scanData2 = new HashMap<>();
        scanData2.put("macAddress", "00:11:22:33:44:03");
        scanData2.put("ssid", "DualAP_Test");
        scanData2.put("signalStrength", -62.3);
        scanData2.put("frequency", 2462);
        scanResultsMap.put("00:11:22:33:44:03", scanData2);

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
        Map<String, Object> result = adapter.calculatePosition(scanResultsMap, new HashMap<>());

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
        Map<String, Map<String, Object>> scanResultsMap = new HashMap<>();
        
        Map<String, Object> scanData1 = new HashMap<>();
        scanData1.put("macAddress", "00:11:22:33:44:02");
        scanData1.put("ssid", "TriAP_Test");
        scanData1.put("signalStrength", -68.5);
        scanData1.put("frequency", 5180);
        scanResultsMap.put("00:11:22:33:44:02", scanData1);
        
        Map<String, Object> scanData2 = new HashMap<>();
        scanData2.put("macAddress", "00:11:22:33:44:03");
        scanData2.put("ssid", "TriAP_Test");
        scanData2.put("signalStrength", -62.3);
        scanData2.put("frequency", 2462);
        scanResultsMap.put("00:11:22:33:44:03", scanData2);

        Map<String, Object> scanData3 = new HashMap<>();
        scanData3.put("macAddress", "00:11:22:33:44:04");
        scanData3.put("ssid", "TriAP_Test");
        scanData3.put("signalStrength", -71.2);
        scanData3.put("frequency", 5240);
        scanResultsMap.put("00:11:22:33:44:04", scanData3);

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
        Map<String, Object> result = adapter.calculatePosition(scanResultsMap, new HashMap<>());

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
        Map<String, Map<String, Object>> scanResultsMap = new HashMap<>();
        
        // Add 4 APs
        for (int i = 1; i <= 4; i++) {
            String macAddress = String.format("00:11:22:33:44:%02d", i);
            Map<String, Object> scanData = new HashMap<>();
            scanData.put("macAddress", macAddress);
            scanData.put("ssid", "MultiAP_Test");
            scanData.put("signalStrength", -60.0 - (i * 2)); // Strong signal
            scanData.put("frequency", i % 2 == 0 ? 5180 : 2462);
            scanResultsMap.put(macAddress, scanData);
            
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
        Map<String, Object> result = adapter.calculatePosition(scanResultsMap, new HashMap<>());

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
        // Create test data for a multiple AP scenario
        Map<String, Map<String, Object>> scanResultsMap = new HashMap<>();
        
        // Add 2 APs
        for (int i = 1; i <= 2; i++) {
            String macAddress = String.format("00:11:22:33:44:%02d", i);
            Map<String, Object> scanData = new HashMap<>();
            scanData.put("macAddress", macAddress);
            scanData.put("ssid", "WeightedAlgorithm_Test");
            scanData.put("signalStrength", -65.0 - (i * 2));
            scanData.put("frequency", i % 2 == 0 ? 5180 : 2462);
            scanResultsMap.put(macAddress, scanData);
            
            // Mock repository to return known AP
            WifiAccessPoint ap = createTestAP(macAddress, 37.7750 + (i * 0.0001), -122.4195 + (i * 0.0001), 12.5 + i);
            when(accessPointRepository.findByMacAddress(macAddress)).thenReturn(Optional.of(ap));
        }

        // Mock calculator to return a positioning result with multiple weighted algorithms
        Position position = new Position(37.7752, -122.4197, 18.0, 10.0, 0.85);
        Map<PositioningAlgorithm, Double> algorithmWeights = new HashMap<>();
        algorithmWeights.put(trilaterationAlgorithm, 0.6);
        algorithmWeights.put(rssiRatioAlgorithm, 0.4);
        
        // Use the first algorithm as the "best" one
        GPSPositioningCalculator.PositioningResult positioningResult = 
            new GPSPositioningCalculator.PositioningResult(position, algorithmWeights);
        when(calculator.calculatePosition(any(), any())).thenReturn(positioningResult);

        // Call the method under test
        Map<String, Object> result = adapter.calculatePosition(scanResultsMap, new HashMap<>());

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