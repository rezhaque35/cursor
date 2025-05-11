package com.wifi.positioning.service;

import com.wifi.positioning.algorithm.GPSPositioningCalculator;
import com.wifi.positioning.algorithm.PositioningAlgorithm;
import com.wifi.positioning.algorithm.selection.SelectionContext;
import com.wifi.positioning.dto.Position;
import com.wifi.positioning.dto.PositionRequestDto;
import com.wifi.positioning.dto.WifiPositioningResponse;
import com.wifi.positioning.dto.WifiScanResult;
import com.wifi.positioning.exception.PositioningException;
import com.wifi.positioning.model.WifiAccessPoint;
import com.wifi.positioning.repository.WifiAccessPointRepository;
import com.wifi.positioning.service.impl.PositioningServiceImpl;
import com.wifi.positioning.validation.SignalPhysicsValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
    private GPSPositioningCalculator calculator;
    
    @Mock
    private SignalPhysicsValidator signalPhysicsValidator;
    
    @Mock
    private PositioningAlgorithm algorithm;
    
    @InjectMocks
    private PositioningServiceImpl positioningService;
    
    private WifiAccessPoint testAccessPoint;
    private PositionRequestDto validRequest;
    private Position testPosition;
    private GPSPositioningCalculator.PositioningResult positioningResult;
    
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
                .status(WifiAccessPoint.STATUS_ACTIVE)
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
        
        // Create position and positioning result
        testPosition = new Position(37.7749, -122.4194, 10.0, 15.0, 0.85);
        
        Map<PositioningAlgorithm, Double> algorithmWeights = new HashMap<>();
        algorithmWeights.put(algorithm, 1.0);
        
        Map<PositioningAlgorithm, List<String>> selectionReasons = new HashMap<>();
        selectionReasons.put(algorithm, List.of("Strong signal", "Good geometry"));
        
        SelectionContext context = mock(SelectionContext.class);
        
        positioningResult = new GPSPositioningCalculator.PositioningResult(
            testPosition, algorithmWeights, selectionReasons, context
        );
        
        // Use lenient stubbing to avoid unnecessary stubbing errors
        lenient().when(accessPointRepository.findByMacAddress(testAccessPoint.getMacAddress()))
                .thenReturn(Optional.of(testAccessPoint));
        
        Map<String, WifiAccessPoint> batchResult = new HashMap<>();
        batchResult.put(testAccessPoint.getMacAddress(), testAccessPoint);
        lenient().when(accessPointRepository.findByMacAddresses(anySet()))
                .thenReturn(batchResult);
                
        lenient().when(signalPhysicsValidator.isPhysicallyPossible(any())).thenReturn(true);
        
        lenient().when(algorithm.getName()).thenReturn("Proximity");
    }

    @Test
    void should_CalculatePosition_When_ValidRequest() {
        // Setup
        when(calculator.calculatePosition(any(), any())).thenReturn(positioningResult);
        
        // Execute
        WifiPositioningResponse response = positioningService.calculatePosition(validRequest);
        
        // Verify
        assertNotNull(response);
        assertEquals("SUCCESS", response.result());
        assertEquals("Request processed successfully", response.message());
        assertEquals(TEST_REQUEST_ID, response.requestId());
        assertEquals(TEST_CLIENT, response.client());
        assertEquals(TEST_APPLICATION, response.application());
        assertNotNull(response.timestamp());
        
        // Verify position data
        assertNotNull(response.wifiPosition());
        assertEquals(testPosition.latitude(), response.wifiPosition().latitude());
        assertEquals(testPosition.longitude(), response.wifiPosition().longitude());
        assertEquals(testPosition.accuracy(), response.wifiPosition().horizontalAccuracy());
        assertEquals(testPosition.confidence(), response.wifiPosition().confidence());
        assertEquals(1, response.wifiPosition().methodsUsed().size());
        assertEquals("proximity", response.wifiPosition().methodsUsed().get(0));
        
        // Verify the calculator was called with the correct data
        verify(calculator).calculatePosition(any(), any());
        verify(signalPhysicsValidator).isPhysicallyPossible(any());
        verify(accessPointRepository).findByMacAddresses(anySet());
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
    void should_ReturnPositionNotFoundResponse_When_PositionNotFound() {
        // Setup
        when(calculator.calculatePosition(any(), any())).thenReturn(null);
        
        // Execute
        WifiPositioningResponse response = positioningService.calculatePosition(validRequest);
        
        // Verify
        assertNotNull(response);
        assertEquals("ERROR", response.result());
        assertEquals("Position calculation failed: no position could be determined", response.message());
        assertEquals(TEST_REQUEST_ID, response.requestId());
        assertEquals(TEST_CLIENT, response.client());
        assertEquals(TEST_APPLICATION, response.application());
        assertNull(response.wifiPosition());
    }
    
    @Test
    void should_ReturnErrorResponse_When_SignalPhysicsInvalid() {
        // Setup
        when(signalPhysicsValidator.isPhysicallyPossible(any())).thenReturn(false);
        
        // Execute
        WifiPositioningResponse response = positioningService.calculatePosition(validRequest);
        
        // Verify
        assertNotNull(response);
        assertEquals("ERROR", response.result());
        assertEquals("Physically impossible signal strength relationships", response.message());
        assertEquals(TEST_REQUEST_ID, response.requestId());
        assertEquals(TEST_CLIENT, response.client());
        assertEquals(TEST_APPLICATION, response.application());
        assertNull(response.wifiPosition());
    }
    
    @Test
    void should_ReturnErrorResponse_When_CalculatorThrowsException() {
        // Setup
        when(calculator.calculatePosition(any(), any()))
                .thenThrow(new RuntimeException("Calculator internal error"));
        
        // Use Mockito lenient() to avoid UnnecessaryStubbingException
        lenient().when(signalPhysicsValidator.isPhysicallyPossible(any())).thenReturn(true);
        lenient().when(accessPointRepository.findByMacAddresses(anySet())).thenReturn(
            Map.of(testAccessPoint.getMacAddress(), testAccessPoint)
        );
        
        // Execute
        WifiPositioningResponse response = positioningService.calculatePosition(validRequest);
        
        // Verify
        assertNotNull(response);
        assertEquals("ERROR", response.result());
        assertEquals("Calculator internal error", response.message());
        assertEquals(TEST_REQUEST_ID, response.requestId());
        assertEquals(TEST_CLIENT, response.client());
        assertEquals(TEST_APPLICATION, response.application());
        assertNull(response.wifiPosition());
    }
} 