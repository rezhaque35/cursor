package com.wifi.positioning.service;

import com.wifi.positioning.algorithm.GPSPositioningCalculatorAdapter;
import com.wifi.positioning.dto.Position;
import com.wifi.positioning.dto.PositionRequestDto;
import com.wifi.positioning.dto.PositionResponseDto;
import com.wifi.positioning.dto.WifiScanResult;
import com.wifi.positioning.exception.PositioningException;
import com.wifi.positioning.model.WifiAccessPoint;
import com.wifi.positioning.repository.WifiAccessPointRepository;
import com.wifi.positioning.service.impl.PositioningServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.ArgumentCaptor;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PositioningServiceTest {

    private static final String TEST_CLIENT = "test-client";
    private static final String TEST_REQUEST_ID = "test-request-id";
    private static final String TEST_APPLICATION = "test-application";

    @Mock
    private WifiAccessPointRepository accessPointRepository;
    
    @Mock
    private GPSPositioningCalculatorAdapter positioningCalculator;
    
    @InjectMocks
    private PositioningServiceImpl positioningService;
    
    private WifiAccessPoint testAccessPoint;
    private PositionRequestDto validRequest;
    
    @BeforeEach
    void setUp() {
        // Create test data
        testAccessPoint = WifiAccessPoint.builder()
                .macAddress("00:11:22:33:44:55")
                .version("test-1.0")
                .latitude(37.7749)
                .longitude(-122.4194)
                .altitude(10.0)
                .horizontalAccuracy(5.0)
                .verticalAccuracy(2.0)
                .confidence(0.85)
                .ssid("test-ssid")
                .frequency(2437)
                .vendor("test-vendor")
                .geohash("9q8yyk")
                .build();
        
        // Create map for mac address lookup
        Map<String, WifiAccessPoint> macAddressToAP = new HashMap<>();
        macAddressToAP.put(testAccessPoint.getMacAddress(), testAccessPoint);
        
        WifiScanResult scanResult = WifiScanResult.of(
                "00:11:22:33:44:55", 
                -65.0, 
                2437, 
                "test-ssid");
        
        validRequest = new PositionRequestDto(
                List.of(scanResult),
                TEST_CLIENT,
                TEST_REQUEST_ID,
                TEST_APPLICATION,
                false);
        
        // Use lenient stubbing to avoid unnecessary stubbing errors
        lenient().when(accessPointRepository.findByMacAddress(testAccessPoint.getMacAddress()))
                .thenReturn(Optional.of(testAccessPoint));
        
        Map<String, WifiAccessPoint> batchResult = new HashMap<>();
        batchResult.put(testAccessPoint.getMacAddress(), testAccessPoint);
        lenient().when(accessPointRepository.findByMacAddresses(anySet()))
                .thenReturn(batchResult);
    }

    @Test
    void should_CalculatePosition_When_ValidRequest() {
        // Setup
        Map<String, Object> calculatorResult = new HashMap<>();
        calculatorResult.put("positionFound", true);
        calculatorResult.put("latitude", 37.7749);
        calculatorResult.put("longitude", -122.4194);
        calculatorResult.put("horizontalAccuracy", 15.0);
        calculatorResult.put("confidence", 0.85);
        calculatorResult.put("methodsUsed", List.of("proximity"));
        
        when(positioningCalculator.calculatePosition(any(), any())).thenReturn(calculatorResult);
        
        // Execute
        PositionResponseDto response = positioningService.calculatePosition(validRequest);
        
        // Verify
        assertNotNull(response);
        assertEquals(37.7749, response.latitude());
        assertEquals(-122.4194, response.longitude());
        assertEquals(15.0, response.horizontalAccuracy());
        assertEquals(0.85, response.confidence());
        assertEquals(List.of("proximity"), response.methodsUsed());
        
        // Verify the calculator was called with the correct data
        ArgumentCaptor<List<WifiScanResult>> scanResultsCaptor = 
                ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<Map<String, Object>> optionsCaptor = 
                ArgumentCaptor.forClass(Map.class);
        
        verify(positioningCalculator).calculatePosition(scanResultsCaptor.capture(), optionsCaptor.capture());
        
        List<WifiScanResult> capturedScanResults = scanResultsCaptor.getValue();
        assertNotNull(capturedScanResults);
        assertEquals(1, capturedScanResults.size());
        assertEquals("00:11:22:33:44:55", capturedScanResults.get(0).macAddress());
        
        Map<String, Object> capturedOptions = optionsCaptor.getValue();
        assertEquals(TEST_CLIENT, capturedOptions.get("client"));
        assertEquals(TEST_REQUEST_ID, capturedOptions.get("requestId"));
        assertEquals(TEST_APPLICATION, capturedOptions.get("application"));
    }
    
    @Test
    void should_ThrowException_When_NoScanResults() {
        // Setup
        PositionRequestDto emptyRequest = new PositionRequestDto(
                Collections.emptyList(),
                TEST_CLIENT,
                TEST_REQUEST_ID,
                TEST_APPLICATION,
                false);
        
        // Execute & Verify
        PositioningException exception = assertThrows(
                PositioningException.class,
                () -> positioningService.calculatePosition(emptyRequest));
        
        assertEquals("No WiFi scan results provided", exception.getMessage());
    }
    
    @Test
    void should_ThrowException_When_PositionNotFound() {
        // Setup
        Map<String, Object> calculatorResult = new HashMap<>();
        calculatorResult.put("positionFound", false);
        
        when(positioningCalculator.calculatePosition(any(), any())).thenReturn(calculatorResult);
        
        // Execute & Verify
        PositioningException exception = assertThrows(
                PositioningException.class,
                () -> positioningService.calculatePosition(validRequest));
        
        assertEquals("Unable to calculate position with provided scan results", exception.getMessage());
    }
    
    @Test
    void should_ThrowException_When_ErrorMessagePresent() {
        // Setup
        Map<String, Object> calculatorResult = new HashMap<>();
        calculatorResult.put("positionFound", false);
        calculatorResult.put("errorMessage", "Signal physics validation failed");
        
        when(positioningCalculator.calculatePosition(any(), any())).thenReturn(calculatorResult);
        
        // Execute & Verify
        PositioningException exception = assertThrows(
                PositioningException.class,
                () -> positioningService.calculatePosition(validRequest));
        
        assertEquals("Signal physics validation failed", exception.getMessage());
    }
    
    @Test
    void should_ThrowException_When_CalculatorThrowsException() {
        // Setup
        when(positioningCalculator.calculatePosition(any(), any()))
                .thenThrow(new RuntimeException("Calculator internal error"));
        
        // Execute & Verify
        PositioningException exception = assertThrows(
                PositioningException.class,
                () -> positioningService.calculatePosition(validRequest));
        
        assertEquals("Error calculating position: Calculator internal error", exception.getMessage());
    }
} 