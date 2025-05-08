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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests for the GPSPositioningCalculatorAdapter focusing on batch operations.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("GPSPositioningCalculatorAdapter Batch Operations Tests")
class GPSPositioningCalculatorAdapterBatchTest {

    @Mock
    private GPSPositioningCalculator calculator;

    @Mock
    private WifiAccessPointRepository accessPointRepository;

    @Mock
    private SignalPhysicsValidator signalPhysicsValidator;

    private GPSPositioningCalculatorAdapter adapter;
    
    // Test constants
    private static final String MAC_1 = "00:11:22:33:44:55";
    private static final String MAC_2 = "AA:BB:CC:DD:EE:FF";
    private static final String MAC_3 = "11:22:33:44:55:66";
    private static final double LAT_1 = 37.7749;
    private static final double LON_1 = -122.4194;
    private static final double LAT_2 = 37.7750;
    private static final double LON_2 = -122.4195;
    private static final double LAT_3 = 37.7751;
    private static final double LON_3 = -122.4196;

    @BeforeEach
    void setUp() {
        adapter = new GPSPositioningCalculatorAdapter(calculator, accessPointRepository, signalPhysicsValidator);
        // Use lenient() to avoid unnecessary stubbing errors when this method isn't called in all tests
        lenient().when(signalPhysicsValidator.isPhysicallyPossible(any())).thenReturn(true);
    }
    
    /**
     * Helper method to create scan results for testing
     */
    private List<WifiScanResult> createScanResults(String... macAddresses) {
        List<WifiScanResult> scanResults = new ArrayList<>();
        
        for (int i = 0; i < macAddresses.length; i++) {
            String macAddress = macAddresses[i];
            double signalStrength = -60.0 - (i * 2); // Strong signal
            int frequency = i % 2 == 0 ? 5180 : 2462;
            scanResults.add(WifiScanResult.of(macAddress, signalStrength, frequency, "TestAP_" + i));
        }
        
        return scanResults;
    }
    
    /**
     * Helper method to create a WiFi access point for testing
     */
    private WifiAccessPoint createAccessPoint(String macAddress, double latitude, double longitude) {
        return WifiAccessPoint.builder()
                .macAddress(macAddress)
                .version("1.0")
                .latitude(latitude)
                .longitude(longitude)
                .confidence(0.8)
                .horizontalAccuracy(15.0)
                .status(WifiAccessPoint.STATUS_ACTIVE)
                .build();
    }

    @Test
    @DisplayName("Should use batch operation for multiple MAC addresses")
    void shouldUseBatchOperationForMultipleMacAddresses() {
        // Prepare test data
        List<WifiScanResult> scanResults = createScanResults(MAC_1, MAC_2, MAC_3);
        
        // Configure mocks
        Map<String, WifiAccessPoint> batchResult = new HashMap<>();
        batchResult.put(MAC_1, createAccessPoint(MAC_1, LAT_1, LON_1));
        batchResult.put(MAC_2, createAccessPoint(MAC_2, LAT_2, LON_2));
        batchResult.put(MAC_3, createAccessPoint(MAC_3, LAT_3, LON_3));
        
        // Capture the set of MAC addresses
        ArgumentCaptor<Set<String>> macAddressesCaptor = ArgumentCaptor.forClass(Set.class);
        
        when(accessPointRepository.findByMacAddresses(macAddressesCaptor.capture())).thenReturn(batchResult);
        
        // Mock the calculator response
        Position position = new Position(LAT_1, LON_1, 0.0, 15.0, 0.8);
        Map<PositioningAlgorithm, Double> algorithmWeights = new HashMap<>();
        Map<PositioningAlgorithm, List<String>> selectionReasons = new HashMap<>();
        GPSPositioningCalculator.PositioningResult positioningResult = 
                new GPSPositioningCalculator.PositioningResult(position, algorithmWeights, selectionReasons, null);
        when(calculator.calculatePosition(any(), any())).thenReturn(positioningResult);
        
        // Call the method under test
        adapter.calculatePosition(scanResults, Collections.emptyMap());
        
        // Verify batch operation was used
        verify(accessPointRepository, times(1)).findByMacAddresses(any());
        verify(accessPointRepository, never()).findByMacAddress(any());
        
        // Verify the correct MAC addresses were passed to the batch operation
        Set<String> capturedMacAddresses = macAddressesCaptor.getValue();
        assertEquals(3, capturedMacAddresses.size());
        assertTrue(capturedMacAddresses.contains(MAC_1));
        assertTrue(capturedMacAddresses.contains(MAC_2));
        assertTrue(capturedMacAddresses.contains(MAC_3));
    }
    
    @Test
    @DisplayName("Should fall back to individual lookups if batch operation fails")
    void shouldFallBackToIndividualLookupsIfBatchOperationFails() {
        // Prepare test data
        List<WifiScanResult> scanResults = createScanResults(MAC_1, MAC_2, MAC_3);
        
        // Configure mocks to simulate batch operation failure
        when(accessPointRepository.findByMacAddresses(any())).thenThrow(new RuntimeException("Batch operation failed"));
        
        // Setup individual lookup responses
        when(accessPointRepository.findByMacAddress(MAC_1)).thenReturn(Optional.of(createAccessPoint(MAC_1, LAT_1, LON_1)));
        when(accessPointRepository.findByMacAddress(MAC_2)).thenReturn(Optional.of(createAccessPoint(MAC_2, LAT_2, LON_2)));
        when(accessPointRepository.findByMacAddress(MAC_3)).thenReturn(Optional.of(createAccessPoint(MAC_3, LAT_3, LON_3)));
        
        // Mock the calculator response
        Position position = new Position(LAT_1, LON_1, 0.0, 15.0, 0.8);
        Map<PositioningAlgorithm, Double> algorithmWeights = new HashMap<>();
        Map<PositioningAlgorithm, List<String>> selectionReasons = new HashMap<>();
        GPSPositioningCalculator.PositioningResult positioningResult = 
                new GPSPositioningCalculator.PositioningResult(position, algorithmWeights, selectionReasons, null);
        when(calculator.calculatePosition(any(), any())).thenReturn(positioningResult);
        
        // Call the method under test
        adapter.calculatePosition(scanResults, Collections.emptyMap());
        
        // Verify batch operation was attempted
        verify(accessPointRepository, times(1)).findByMacAddresses(any());
        
        // Verify fallback to individual lookups
        verify(accessPointRepository, times(1)).findByMacAddress(MAC_1);
        verify(accessPointRepository, times(1)).findByMacAddress(MAC_2);
        verify(accessPointRepository, times(1)).findByMacAddress(MAC_3);
    }
    
    @Test
    @DisplayName("Should handle empty scan results properly")
    void shouldHandleEmptyScanResultsProperly() {
        // Call with empty scan results
        Map<String, Object> result = adapter.calculatePosition(Collections.emptyList(), Collections.emptyMap());
        
        // Verify
        assertNotNull(result);
        assertTrue(result.isEmpty());
        
        // Verify no repository calls were made
        verifyNoInteractions(accessPointRepository);
    }
    
    @Test
    @DisplayName("Should handle no known access points properly")
    void shouldHandleNoKnownAccessPointsProperly() {
        // Prepare test data
        List<WifiScanResult> scanResults = createScanResults(MAC_1, MAC_2, MAC_3);
        
        // Configure mocks to return empty results
        Map<String, WifiAccessPoint> emptyResult = new HashMap<>();
        
        when(accessPointRepository.findByMacAddresses(any())).thenReturn(emptyResult);
        
        // Call the method under test
        Map<String, Object> result = adapter.calculatePosition(scanResults, Collections.emptyMap());
        
        // Verify
        assertNotNull(result);
        assertFalse((Boolean) result.get("positionFound"));
        assertEquals(3, result.get("apCount"));
        
        // Verify batch operation was used
        verify(accessPointRepository, times(1)).findByMacAddresses(any());
        verify(calculator, never()).calculatePosition(any(), any());
    }
} 