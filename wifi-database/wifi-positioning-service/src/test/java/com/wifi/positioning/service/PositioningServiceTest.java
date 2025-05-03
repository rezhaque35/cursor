package com.wifi.positioning.service;

import com.wifi.positioning.algorithm.GPSPositioningCalculatorAdapter;
import com.wifi.positioning.dto.PositionRequestDto;
import com.wifi.positioning.dto.PositionResponseDto;
import com.wifi.positioning.dto.WifiScanResultDto;
import com.wifi.positioning.exception.PositioningException;
import com.wifi.positioning.model.WifiAccessPoint;
import com.wifi.positioning.repository.WifiAccessPointRepository;
import com.wifi.positioning.service.impl.PositioningServiceTestImpl;
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

    @Mock
    private WifiAccessPointRepository accessPointRepository;
    
    @Mock
    private GPSPositioningCalculatorAdapter positioningCalculator;
    
    @InjectMocks
    private PositioningServiceTestImpl positioningService;
    
    private WifiAccessPoint testAccessPoint;
    private PositionRequestDto validRequest;
    
    @BeforeEach
    void setUp() {
        testAccessPoint = WifiAccessPoint.builder()
                .macAddress("00:11:22:33:44:55")
                .version("test-1.0")
                .latitude(37.7749)
                .longitude(-122.4194)
                .altitude(10.0)
                .horizontalAccuracy(5.0)
                .verticalAccuracy(2.0)
                .confidence(0.85)
                .bestMethod("test-method")
                .methodsUsed(new String[]{"test-method-1", "test-method-2"})
                .sampleCount(10)
                .signalStrengthAvg(-65.0)
                .signalStrengthStd(3.0)
                .ssid("test-ssid")
                .frequency(2437)
                .countryCode("US")
                .vendor("test-vendor")
                .build();
        
        WifiScanResultDto scanResult = new WifiScanResultDto(
                "00:11:22:33:44:55", 
                -65, 
                2437, 
                "test-ssid", 
                54, 
                40);
        
        validRequest = new PositionRequestDto(
                List.of(scanResult),
                true,
                false,
                "test-session-id");
    }

    @Test
    void calculatePositionSuccess() {
        // Mock repository response
        when(accessPointRepository.findByMacAddress(anyString()))
                .thenReturn(List.of(testAccessPoint));

        // Mock calculator response
        Map<String, Object> calculatorResult = new HashMap<>();
        calculatorResult.put("latitude", 37.7749);
        calculatorResult.put("longitude", -122.4194);
        calculatorResult.put("altitude", 10.0);
        calculatorResult.put("horizontalAccuracy", 5.0);
        calculatorResult.put("verticalAccuracy", 2.0);
        calculatorResult.put("confidence", 0.85);
        calculatorResult.put("bestMethod", "test-method");
        calculatorResult.put("methodsUsed", List.of("test-method-1", "test-method-2"));
        calculatorResult.put("apCount", 1);
        calculatorResult.put("calculationTimeMs", 50L);
        
        when(positioningCalculator.calculatePosition(anyMap(), anyMap()))
                .thenReturn(calculatorResult);
        
        PositionResponseDto response = positioningService.calculatePosition(validRequest);
        
        assertNotNull(response);
        assertEquals(37.7749, response.latitude());
        assertEquals(-122.4194, response.longitude());
        assertEquals(10.0, response.altitude());
        assertEquals(5.0, response.horizontalAccuracy());
        assertEquals(2.0, response.verticalAccuracy());
        assertEquals(0.85, response.confidence());
        assertEquals("test-method", response.bestMethod());
        assertEquals(2, response.methodsUsed().size());
        assertEquals(1, response.apCount());
        assertNotNull(response.metadata());
        assertTrue(response.metadata().containsKey("calculationTimeMs"));
        assertTrue(response.metadata().containsKey("timestamp"));
    }

    @Test
    void calculatePositionWithAlternatives() {
        // Mock repository response
        when(accessPointRepository.findByMacAddress(anyString()))
                .thenReturn(List.of(testAccessPoint));

        Map<String, Object> calculatorResult = new HashMap<>();
        calculatorResult.put("latitude", 37.7749);
        calculatorResult.put("longitude", -122.4194);
        calculatorResult.put("altitude", 10.0);
        calculatorResult.put("horizontalAccuracy", 5.0);
        calculatorResult.put("verticalAccuracy", 2.0);
        calculatorResult.put("confidence", 0.85);
        calculatorResult.put("bestMethod", "test-method");
        calculatorResult.put("methodsUsed", List.of("test-method-1", "test-method-2"));
        calculatorResult.put("apCount", 1);
        calculatorResult.put("calculationTimeMs", 50L);
        
        // Add alternatives
        List<Map<String, Object>> alternatives = new ArrayList<>();
        Map<String, Object> alt1 = new HashMap<>();
        alt1.put("latitude", 37.7750);
        alt1.put("longitude", -122.4195);
        alt1.put("altitude", 11.0);
        alt1.put("horizontalAccuracy", 6.0);
        alt1.put("verticalAccuracy", 3.0);
        alt1.put("confidence", 0.80);
        alt1.put("method", "alternative-method-1");
        alternatives.add(alt1);
        calculatorResult.put("alternatives", alternatives);
        
        when(positioningCalculator.calculatePosition(anyMap(), anyMap()))
                .thenReturn(calculatorResult);
        
        PositionResponseDto response = positioningService.calculatePosition(validRequest);
        
        assertNotNull(response);
        assertNotNull(response.alternatives());
        assertEquals(1, response.alternatives().size());
        assertEquals(37.7750, response.alternatives().get(0).latitude());
        assertEquals("alternative-method-1", response.alternatives().get(0).method());
    }

    @Test
    void calculatePositionEmptyRequest() {
        PositionRequestDto emptyRequest = new PositionRequestDto(
                Collections.emptyList(),
                true,
                false,
                "test-session-id");
        
        assertThrows(PositioningException.class, () -> 
                positioningService.calculatePosition(emptyRequest));
    }

    @Test
    void calculatePositionCalculatorError() {
        when(positioningCalculator.calculatePosition(anyMap(), anyMap()))
                .thenThrow(new RuntimeException("Test calculator error"));
        
        assertThrows(PositioningException.class, () -> 
                positioningService.calculatePosition(validRequest));
    }
} 