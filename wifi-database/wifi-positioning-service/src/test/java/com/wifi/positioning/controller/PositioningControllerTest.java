package com.wifi.positioning.controller;

import com.wifi.positioning.dto.PositionRequestDto;
import com.wifi.positioning.dto.WifiPositioningResponse;
import com.wifi.positioning.dto.WifiPositioningResponse.WifiPosition;
import com.wifi.positioning.dto.WifiScanResult;
import com.wifi.positioning.exception.PositioningException;
import com.wifi.positioning.service.PositioningService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class PositioningControllerTest {

    @Mock
    private PositioningService positioningService;

    @InjectMocks
    private PositioningController controller;

    private PositionRequestDto validRequest;
    private WifiPositioningResponse successResponse;
    private WifiPositioningResponse errorResponse;

    @BeforeEach
    void setUp() {
        // Create a valid request
        validRequest = new PositionRequestDto(
            List.of(WifiScanResult.of("00:11:22:33:44:55", -65.0, 2437, "TestAP")),
            "test-client",
            "test-request-id",
            "test-app",
            false
        );

        // Create a success response
        WifiPosition wifiPosition = new WifiPosition(
            37.7749,
            -122.4194,
            10.0,
            25.0,
            0.0,
            0.5,
            List.of("weightedcentroid"),
            1,
            100L
        );
        
        successResponse = WifiPositioningResponse.success(
            validRequest,
            wifiPosition,
            null
        );

        // Create an error response
        errorResponse = WifiPositioningResponse.error(
            "Error calculating position",
            validRequest
        );
    }

    @Test
    void should_ReturnSuccessResponse_When_CalculationSucceeds() {
        // Arrange
        when(positioningService.calculatePosition(any(PositionRequestDto.class)))
            .thenReturn(successResponse);

        // Act
        ResponseEntity<WifiPositioningResponse> response = controller.calculatePosition(validRequest);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(successResponse, response.getBody());
        assertEquals("SUCCESS", response.getBody().result());
        
        // Verify
        verify(positioningService).calculatePosition(validRequest);
    }

    @Test
    void should_ReturnErrorResponse_When_PositioningExceptionIsThrown() {
        // Arrange
        PositioningException exception = new PositioningException("Invalid input");
        when(positioningService.calculatePosition(any(PositionRequestDto.class)))
            .thenThrow(exception);

        // Act
        ResponseEntity<WifiPositioningResponse> response = controller.calculatePosition(validRequest);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("ERROR", response.getBody().result());
        assertEquals("Invalid input", response.getBody().message());
        
        // Verify
        verify(positioningService).calculatePosition(validRequest);
    }

    @Test
    void should_ReturnErrorResponse_When_NullPointerExceptionIsThrown() {
        // Arrange
        when(positioningService.calculatePosition(any(PositionRequestDto.class)))
            .thenThrow(new NullPointerException("Null value found"));

        // Act
        ResponseEntity<WifiPositioningResponse> response = controller.calculatePosition(validRequest);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("ERROR", response.getBody().result());
        assertEquals("Error processing WiFi data: No matching access points found in database", 
                    response.getBody().message());
        
        // Verify
        verify(positioningService).calculatePosition(validRequest);
    }

    @Test
    void should_ReturnErrorResponse_When_OtherExceptionIsThrown() {
        // Arrange
        when(positioningService.calculatePosition(any(PositionRequestDto.class)))
            .thenThrow(new RuntimeException("Unexpected error"));

        // Act
        ResponseEntity<WifiPositioningResponse> response = controller.calculatePosition(validRequest);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("ERROR", response.getBody().result());
        assertEquals("Unexpected error", response.getBody().message());
        
        // Verify
        verify(positioningService).calculatePosition(validRequest);
    }
} 