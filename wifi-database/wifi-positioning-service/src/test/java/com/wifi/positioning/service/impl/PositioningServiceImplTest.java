package com.wifi.positioning.service.impl;

import com.wifi.positioning.algorithm.GPSPositioningCalculator;
import com.wifi.positioning.algorithm.PositioningAlgorithm;
import com.wifi.positioning.algorithm.selection.SelectionContext;
import com.wifi.positioning.dto.Position;
import com.wifi.positioning.dto.PositionRequestDto;
import com.wifi.positioning.dto.PositionResponseDto;
import com.wifi.positioning.dto.WifiScanResult;
import com.wifi.positioning.exception.PositioningException;
import com.wifi.positioning.model.WifiAccessPoint;
import com.wifi.positioning.repository.WifiAccessPointRepository;
import com.wifi.positioning.validation.SignalPhysicsValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for PositioningServiceImpl without the adapter.
 * These tests verify that the service correctly calculates positions
 * after merging with the adapter's functionality.
 * 
 * Test scenarios include:
 * - Successful position calculation
 * - Empty scan results
 * - Physics validation failures
 * - No known access points
 * - Calculation failures
 * - Batch lookup failures
 * - Invalid coordinates
 */
@ExtendWith(MockitoExtension.class)
public class PositioningServiceImplTest {

    @Mock
    private GPSPositioningCalculator calculator;
    
    @Mock
    private WifiAccessPointRepository accessPointRepository;
    
    @Mock
    private SignalPhysicsValidator signalPhysicsValidator;
    
    @Mock
    private PositioningAlgorithm algorithm;
    
    private PositioningServiceImpl service;
    
    private List<WifiScanResult> scanResults;
    private List<WifiAccessPoint> knownAPs;
    private Position position;
    private GPSPositioningCalculator.PositioningResult positioningResult;
    private PositionRequestDto request;
    
    private static final double VALID_LATITUDE = 37.7749;
    private static final double VALID_LONGITUDE = -122.4194;
    private static final double VALID_ALTITUDE = 10.0;
    private static final double VALID_ACCURACY = 25.0;
    private static final double VALID_CONFIDENCE = 0.5;
    
    @BeforeEach
    void setUp() {
        service = new PositioningServiceImpl(calculator, accessPointRepository, signalPhysicsValidator);
        
        // Set up test data
        scanResults = List.of(
            WifiScanResult.of("00:11:22:33:44:55", -65.0, 2437, "TestAP"),
            WifiScanResult.of("AA:BB:CC:DD:EE:FF", -70.0, 5180, "TestAP2")
        );
        
        WifiAccessPoint ap1 = mock(WifiAccessPoint.class);
        lenient().when(ap1.getMacAddress()).thenReturn("00:11:22:33:44:55");
        lenient().when(ap1.getLatitude()).thenReturn(37.7749);
        lenient().when(ap1.getLongitude()).thenReturn(-122.4194);
        lenient().when(ap1.getAltitude()).thenReturn(10.0);
        
        WifiAccessPoint ap2 = mock(WifiAccessPoint.class);
        lenient().when(ap2.getMacAddress()).thenReturn("AA:BB:CC:DD:EE:FF");
        lenient().when(ap2.getLatitude()).thenReturn(37.7748);
        lenient().when(ap2.getLongitude()).thenReturn(-122.4192);
        lenient().when(ap2.getAltitude()).thenReturn(12.0);
        
        knownAPs = List.of(ap1, ap2);
        
        // Create a position
        position = new Position(37.7749, -122.4194, 10.0, 25.0, 0.5);
        
        // Create a positioning result
        Map<PositioningAlgorithm, Double> algorithmWeights = new HashMap<>();
        algorithmWeights.put(algorithm, 1.0);
        
        Map<PositioningAlgorithm, List<String>> selectionReasons = new HashMap<>();
        selectionReasons.put(algorithm, List.of("Strong signal", "Good geometry"));
        
        SelectionContext context = mock(SelectionContext.class);
        
        positioningResult = new GPSPositioningCalculator.PositioningResult(
            position, algorithmWeights, selectionReasons, context
        );
        
        // Set up algorithm mock
        lenient().when(algorithm.getName()).thenReturn("Weighted Centroid");
        
        // Create request
        request = new PositionRequestDto(
            scanResults, 
            "test-client", 
            "test-request-id", 
            "test-app",
            true
        );
    }
    
    @Test
    void should_ReturnPositionResponse_When_CalculationSucceeds() {
        // Arrange
        when(signalPhysicsValidator.isPhysicallyPossible(any())).thenReturn(true);
        
        Map<String, WifiAccessPoint> apMap = Map.of(
            "00:11:22:33:44:55", knownAPs.get(0),
            "AA:BB:CC:DD:EE:FF", knownAPs.get(1)
        );
        when(accessPointRepository.findByMacAddresses(any())).thenReturn(apMap);
        
        when(calculator.calculatePosition(anyList(), anyList())).thenReturn(positioningResult);
        
        // Act
        PositionResponseDto response = service.calculatePosition(request);
        
        // Assert
        assertNotNull(response);
        assertEquals(position.latitude(), response.latitude());
        assertEquals(position.longitude(), response.longitude());
        assertEquals(position.altitude(), response.altitude());
        assertEquals(position.accuracy(), response.horizontalAccuracy());
        assertEquals(position.confidence(), response.confidence());
        assertEquals(1, response.methodsUsed().size());
        assertEquals("weightedcentroid", response.methodsUsed().get(0));
        assertEquals(Integer.valueOf(scanResults.size()), response.apCount());
        assertNotNull(response.metadata());
        assertTrue(response.metadata().containsKey("calculationTimeMs"));
        assertTrue(response.metadata().containsKey("timestamp"));
        assertTrue(response.metadata().containsKey("calculationInfo"));
        
        // Verify interactions
        verify(signalPhysicsValidator).isPhysicallyPossible(scanResults);
        verify(accessPointRepository).findByMacAddresses(any());
        verify(calculator).calculatePosition(anyList(), anyList());
    }
    
    @Test
    void should_ThrowException_When_NoScanResults() {
        // Arrange
        PositionRequestDto emptyRequest = new PositionRequestDto(
            Collections.emptyList(), 
            "test-client", 
            "test-request-id", 
            "test-app",
            false
        );
        
        // Act & Assert
        PositioningException exception = assertThrows(
            PositioningException.class, 
            () -> service.calculatePosition(emptyRequest)
        );
        
        assertEquals("No WiFi scan results provided", exception.getMessage());
    }
    
    @Test
    void should_ReturnErrorResponse_When_PhysicsValidationFails() {
        // Arrange
        when(signalPhysicsValidator.isPhysicallyPossible(any())).thenReturn(false);
        
        // Act
        PositionResponseDto response = service.calculatePosition(request);
        
        // Assert
        assertNotNull(response);
        assertNotNull(response.metadata());
        assertTrue(response.metadata().containsKey("errorMessage"));
        assertEquals("Physically impossible signal strength relationships", response.metadata().get("errorMessage"));
        assertEquals("ERROR", response.metadata().get("result"));
        assertTrue((Boolean) response.metadata().get("error"));
        
        // Verify interactions
        verify(signalPhysicsValidator).isPhysicallyPossible(scanResults);
        verify(accessPointRepository, never()).findByMacAddresses(any());
        verify(calculator, never()).calculatePosition(any(), any());
    }
    
    @Test
    void should_ReturnNotFoundResponse_When_NoKnownAccessPoints() {
        // Arrange
        when(signalPhysicsValidator.isPhysicallyPossible(any())).thenReturn(true);
        when(accessPointRepository.findByMacAddresses(any())).thenReturn(Collections.emptyMap());
        
        // Act
        PositionResponseDto response = service.calculatePosition(request);
        
        // Assert
        assertNotNull(response);
        assertNotNull(response.metadata());
        assertFalse((Boolean) response.metadata().get("positionFound"));
        assertTrue(Integer.valueOf(scanResults.size()).equals(response.metadata().get("apCount")) 
               || response.metadata().get("apCount") == null);
        assertEquals(0L, response.metadata().get("calculationTimeMs"));
        
        // Verify interactions
        verify(signalPhysicsValidator).isPhysicallyPossible(scanResults);
        verify(accessPointRepository).findByMacAddresses(any());
        verify(calculator, never()).calculatePosition(any(), any());
    }
    
    @Test
    void should_ReturnNotFoundResponse_When_CalculationFails() {
        // Arrange
        when(signalPhysicsValidator.isPhysicallyPossible(any())).thenReturn(true);
        
        Map<String, WifiAccessPoint> apMap = Map.of(
            "00:11:22:33:44:55", knownAPs.get(0),
            "AA:BB:CC:DD:EE:FF", knownAPs.get(1)
        );
        when(accessPointRepository.findByMacAddresses(any())).thenReturn(apMap);
        
        when(calculator.calculatePosition(anyList(), anyList())).thenReturn(null);
        
        // Act
        PositionResponseDto response = service.calculatePosition(request);
        
        // Assert
        assertNotNull(response);
        assertNotNull(response.metadata());
        assertFalse((Boolean) response.metadata().get("positionFound"));
        assertTrue(Integer.valueOf(scanResults.size()).equals(response.metadata().get("apCount")) 
               || response.metadata().get("apCount") == null);
        assertEquals(0L, response.metadata().get("calculationTimeMs"));
        
        // Verify interactions
        verify(signalPhysicsValidator).isPhysicallyPossible(scanResults);
        verify(accessPointRepository).findByMacAddresses(any());
        verify(calculator).calculatePosition(anyList(), anyList());
    }
    
    @Test
    void should_UseFallbackMethod_When_BatchLookupFails() {
        // Arrange
        when(signalPhysicsValidator.isPhysicallyPossible(any())).thenReturn(true);
        
        // Simulate batch lookup failure
        when(accessPointRepository.findByMacAddresses(any())).thenThrow(new RuntimeException("Batch lookup failed"));
        
        // But individual lookups succeed
        when(accessPointRepository.findByMacAddress("00:11:22:33:44:55")).thenReturn(Optional.of(knownAPs.get(0)));
        when(accessPointRepository.findByMacAddress("AA:BB:CC:DD:EE:FF")).thenReturn(Optional.of(knownAPs.get(1)));
        
        when(calculator.calculatePosition(anyList(), anyList())).thenReturn(positioningResult);
        
        // Act
        PositionResponseDto response = service.calculatePosition(request);
        
        // Assert
        assertNotNull(response);
        assertEquals(position.latitude(), response.latitude());
        assertEquals(position.longitude(), response.longitude());
        
        // Verify interactions
        verify(accessPointRepository).findByMacAddresses(any());
        verify(accessPointRepository).findByMacAddress("00:11:22:33:44:55");
        verify(accessPointRepository).findByMacAddress("AA:BB:CC:DD:EE:FF");
        verify(calculator).calculatePosition(anyList(), anyList());
    }
    
    @Test
    void should_ReturnErrorResponse_When_CoordinatesAreInvalid() {
        // Arrange
        when(signalPhysicsValidator.isPhysicallyPossible(any())).thenReturn(true);
        
        Map<String, WifiAccessPoint> apMap = Map.of(
            "00:11:22:33:44:55", knownAPs.get(0),
            "AA:BB:CC:DD:EE:FF", knownAPs.get(1)
        );
        when(accessPointRepository.findByMacAddresses(any())).thenReturn(apMap);
        
        // Mock the calculator to return a result with invalid coordinates
        when(calculator.calculatePosition(anyList(), anyList())).thenReturn(new GPSPositioningCalculator.PositioningResult(
            null, // Invalid position
            Map.of(algorithm, 1.0),
            Map.of(algorithm, List.of("Strong signal", "Good geometry")),
            mock(SelectionContext.class)
        ));
        
        // Act
        PositionResponseDto response = service.calculatePosition(request);
        
        // Assert
        assertNotNull(response);
        assertNotNull(response.metadata());
        assertFalse((Boolean) response.metadata().get("positionFound"));
        assertEquals("ERROR", response.metadata().get("result"));
        assertTrue(Integer.valueOf(scanResults.size()).equals(response.metadata().get("apCount")) 
               || response.metadata().get("apCount") == null);
        assertEquals(0L, response.metadata().get("calculationTimeMs"));
        
        // Verify interactions
        verify(signalPhysicsValidator).isPhysicallyPossible(scanResults);
        verify(accessPointRepository).findByMacAddresses(any());
        verify(calculator).calculatePosition(anyList(), anyList());
    }

    @Test
    void should_ReturnErrorResponse_When_CoordinatesAreNaN() {
        // Arrange
        when(signalPhysicsValidator.isPhysicallyPossible(any())).thenReturn(true);
        
        Map<String, WifiAccessPoint> apMap = Map.of(
            "00:11:22:33:44:55", knownAPs.get(0),
            "AA:BB:CC:DD:EE:FF", knownAPs.get(1)
        );
        when(accessPointRepository.findByMacAddresses(any())).thenReturn(apMap);
        
        // Create a position with NaN coordinates
        Position nanPosition = new Position(Double.NaN, Double.NaN, VALID_ALTITUDE, VALID_ACCURACY, VALID_CONFIDENCE);
        
        Map<PositioningAlgorithm, Double> algorithmWeights = new HashMap<>();
        algorithmWeights.put(algorithm, 1.0);
        
        Map<PositioningAlgorithm, List<String>> selectionReasons = new HashMap<>();
        selectionReasons.put(algorithm, List.of("Strong signal", "Good geometry"));
        
        SelectionContext context = mock(SelectionContext.class);
        
        GPSPositioningCalculator.PositioningResult nanResult = new GPSPositioningCalculator.PositioningResult(
            nanPosition, algorithmWeights, selectionReasons, context
        );
        
        when(calculator.calculatePosition(anyList(), anyList())).thenReturn(nanResult);
        
        // Act
        PositionResponseDto response = service.calculatePosition(request);
        
        // Assert
        assertNotNull(response);
        assertNotNull(response.metadata());
        assertFalse((Boolean) response.metadata().get("positionFound"));
        assertEquals("ERROR", response.metadata().get("result"));
        assertTrue(Integer.valueOf(scanResults.size()).equals(response.metadata().get("apCount")) 
               || response.metadata().get("apCount") == null);
        assertEquals(0L, response.metadata().get("calculationTimeMs"));
        
        // Verify interactions
        verify(signalPhysicsValidator).isPhysicallyPossible(scanResults);
        verify(accessPointRepository).findByMacAddresses(any());
        verify(calculator).calculatePosition(anyList(), anyList());
    }
} 