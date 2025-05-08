package com.wifi.positioning.service;

import com.wifi.positioning.algorithm.GPSPositioningCalculatorAdapter;
import com.wifi.positioning.dto.PositionRequestDto;
import com.wifi.positioning.dto.PositionResponseDto;
import com.wifi.positioning.dto.WifiScanResultDto;
import com.wifi.positioning.exception.PositioningException;
import com.wifi.positioning.model.WifiAccessPoint;
import com.wifi.positioning.repository.WifiAccessPointRepository;
import com.wifi.positioning.service.impl.PositioningServiceImpl;
import com.wifi.positioning.service.impl.PositioningServiceTestImpl;
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
        
        WifiScanResultDto scanResult = new WifiScanResultDto(
                "00:11:22:33:44:55", 
                -65, 
                2437, 
                "test-ssid", 
                54, 
                40);
        
        validRequest = new PositionRequestDto(
                List.of(scanResult),
                TEST_CLIENT,
                TEST_REQUEST_ID,
                TEST_APPLICATION,
                false);
                
        // Use lenient stubbing to avoid unnecessary stubbing errors
        lenient().when(accessPointRepository.findByMacAddress(anyString()))
                .thenReturn(List.of(testAccessPoint));
    }

    @Test
    void calculatePositionSuccess() {
        // Mock calculator response
        Map<String, Object> calculatorResult = new HashMap<>();
        calculatorResult.put("latitude", 37.7749);
        calculatorResult.put("longitude", -122.4194);
        calculatorResult.put("altitude", 10.0);
        calculatorResult.put("horizontalAccuracy", 5.0);
        calculatorResult.put("verticalAccuracy", 2.0);
        calculatorResult.put("confidence", 0.85);
        calculatorResult.put("methodsUsed", List.of("test-method-1", "test-method-2"));
        calculatorResult.put("apCount", 1);
        calculatorResult.put("positionFound", true);
        calculatorResult.put("timestamp", 1621234567890L);
        calculatorResult.put("calculationTimeMs", 150L);
        
        // Create empty alternatives list
        calculatorResult.put("alternatives", new ArrayList<>());
        
        // Create arguments captor
        ArgumentCaptor<Map<String, Object>> optionsCaptor = ArgumentCaptor.forClass(Map.class);
        
        // Mock the positioning calculator
        when(positioningCalculator.calculatePosition(anyMap(), optionsCaptor.capture()))
                .thenReturn(calculatorResult);
                
        // Call the service method
        PositionResponseDto response = positioningService.calculatePosition(validRequest);
        
        // Verify response
        assertNotNull(response);
        assertEquals(37.7749, response.latitude());
        assertEquals(-122.4194, response.longitude());
        assertEquals(10.0, response.altitude());
        assertEquals(5.0, response.horizontalAccuracy());
        assertEquals(2.0, response.verticalAccuracy());
        assertEquals(0.85, response.confidence());
        assertEquals(List.of("test-method-1", "test-method-2"), response.methodsUsed());
        assertEquals(1, response.apCount());
        assertNotNull(response.metadata());
        assertTrue(response.metadata().containsKey("timestamp"));
        
        // Verify calculator was called with correct options
        Map<String, Object> options = optionsCaptor.getValue();
        assertEquals(TEST_CLIENT, options.get("client"));
        assertEquals(TEST_REQUEST_ID, options.get("requestId"));
        assertEquals(TEST_APPLICATION, options.get("application"));
    }

    @Test
    void calculatePositionWithAlternatives() {
        // Mock calculator response with alternatives
        Map<String, Object> calculatorResult = new HashMap<>();
        calculatorResult.put("latitude", 37.7749);
        calculatorResult.put("longitude", -122.4194);
        calculatorResult.put("altitude", 10.0);
        calculatorResult.put("horizontalAccuracy", 5.0);
        calculatorResult.put("verticalAccuracy", 2.0);
        calculatorResult.put("confidence", 0.85);
        calculatorResult.put("methodsUsed", List.of("test-method-1", "test-method-2"));
        calculatorResult.put("apCount", 1);
        calculatorResult.put("positionFound", true);
        calculatorResult.put("timestamp", 1621234567890L);
        calculatorResult.put("calculationTimeMs", 150L);
        
        // Create alternatives list
        List<Map<String, Object>> alternativesList = new ArrayList<>();
        Map<String, Object> alternative1 = new HashMap<>();
        alternative1.put("latitude", 37.775);
        alternative1.put("longitude", -122.42);
        alternative1.put("altitude", 12.0);
        alternative1.put("horizontalAccuracy", 6.0);
        alternative1.put("verticalAccuracy", 3.0);
        alternative1.put("confidence", 0.75);
        alternative1.put("method", "test-method-alt");
        alternativesList.add(alternative1);
        calculatorResult.put("alternatives", alternativesList);
        
        // Mock the positioning calculator
        when(positioningCalculator.calculatePosition(anyMap(), any(Map.class)))
                .thenReturn(calculatorResult);
                
        // Call the service method
        PositionResponseDto response = positioningService.calculatePosition(validRequest);
        
        // Verify response
        assertNotNull(response);
        assertEquals(37.7749, response.latitude());
        assertEquals(-122.4194, response.longitude());
        assertEquals(1, response.alternatives().size());
        
        PositionResponseDto.AlternativePositionDto alt = response.alternatives().get(0);
        assertEquals(37.775, alt.latitude());
        assertEquals(-122.42, alt.longitude());
        assertEquals("test-method-alt", alt.method());
    }

    @Test
    void calculatePositionEmptyRequest() {
        PositionRequestDto emptyRequest = new PositionRequestDto(
                Collections.emptyList(),
                TEST_CLIENT,
                TEST_REQUEST_ID,
                TEST_APPLICATION,
                false);
        
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

    @Test
    void verifyOptionsPassedToCalculator() {
        // Mock calculator response with simple success
        Map<String, Object> calculatorResult = new HashMap<>();
        calculatorResult.put("latitude", 37.7749);
        calculatorResult.put("longitude", -122.4194);
        calculatorResult.put("positionFound", true);
        
        // Create a proper mock instead of using lenient()
        when(positioningCalculator.calculatePosition(anyMap(), any()))
                .thenAnswer(invocation -> {
                    Map<String, Object> options = invocation.getArgument(1);
                    
                    // Verify new fields are passed correctly
                    assertEquals(TEST_CLIENT, options.get("client"));
                    assertEquals(TEST_REQUEST_ID, options.get("requestId"));
                    assertEquals(TEST_APPLICATION, options.get("application"));
                    
                    return calculatorResult;
                });
        
        positioningService.calculatePosition(validRequest);
        
        // Verify the calculator was called
        verify(positioningCalculator).calculatePosition(anyMap(), any());
    }

    @Test
    void verifyCalculationDetailFlagPassedToCalculator() {
        // Create a request with calculationDetail set to true
        PositionRequestDto requestWithDetail = new PositionRequestDto(
                validRequest.wifiScanResults(),
                TEST_CLIENT,
                TEST_REQUEST_ID,
                TEST_APPLICATION,
                true);
        
        // Create arguments captor
        ArgumentCaptor<Map<String, Object>> optionsCaptor = ArgumentCaptor.forClass(Map.class);
        
        // Mock calculator response
        Map<String, Object> calculatorResult = new HashMap<>();
        calculatorResult.put("positionFound", true);
        calculatorResult.put("latitude", 37.7749);
        calculatorResult.put("longitude", -122.4194);
        calculatorResult.put("calculationInfo", "Detailed calculation information");
        
        // Mock the positioning calculator
        when(positioningCalculator.calculatePosition(anyMap(), optionsCaptor.capture()))
                .thenReturn(calculatorResult);
                
        // Call the service method
        positioningService.calculatePosition(requestWithDetail);
        
        // Verify calculator was called with correct options
        Map<String, Object> options = optionsCaptor.getValue();
        assertTrue(options.containsKey("calculationDetail"));
        assertEquals(true, options.get("calculationDetail"));
        
        // Test with calculationDetail set to false
        PositionRequestDto requestWithoutDetail = new PositionRequestDto(
                validRequest.wifiScanResults(),
                TEST_CLIENT,
                TEST_REQUEST_ID,
                TEST_APPLICATION,
                false);
        
        // Reset to capture new options
        optionsCaptor = ArgumentCaptor.forClass(Map.class);
        
        // Mock the positioning calculator again
        when(positioningCalculator.calculatePosition(anyMap(), optionsCaptor.capture()))
                .thenReturn(calculatorResult);
                
        // Call the service method
        positioningService.calculatePosition(requestWithoutDetail);
        
        // Verify calculator was called without calculationDetail flag
        options = optionsCaptor.getValue();
        assertFalse(options.containsKey("calculationDetail"));
    }
} 