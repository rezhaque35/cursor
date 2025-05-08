package com.wifi.positioning.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PositionRequestDtoTest {

    private Validator validator;
    private WifiScanResultDto validScanResult;

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
        
        validScanResult = new WifiScanResultDto(
                "00:11:22:33:44:55", 
                -65, 
                2437, 
                "test-ssid", 
                54, 
                40);
    }

    @Test
    void validRequest() {
        // Given
        PositionRequestDto request = new PositionRequestDto(
                List.of(validScanResult),
                "mobile-app",
                UUID.randomUUID().toString(),
                "my-test-app",
                false);
        
        // When
        Set<ConstraintViolation<PositionRequestDto>> violations = validator.validate(request);
        
        // Then
        assertTrue(violations.isEmpty());
    }
    
    @Test
    void requestWithEmptyScanResults() {
        // Given
        PositionRequestDto request = new PositionRequestDto(
                List.of(),
                "mobile-app",
                UUID.randomUUID().toString(),
                "my-test-app",
                false);
        
        // When
        Set<ConstraintViolation<PositionRequestDto>> violations = validator.validate(request);
        
        // Then
        assertFalse(violations.isEmpty());
        assertEquals(2, violations.size()); // Both @NotEmpty and @Size(min=1) constraints are violated
        assertTrue(violations.stream()
                .anyMatch(v -> v.getMessage().contains("At least one WiFi scan result is required")));
    }
    
    @Test
    void requestWithNullClient() {
        // Given
        PositionRequestDto request = new PositionRequestDto(
                List.of(validScanResult),
                null,
                UUID.randomUUID().toString(),
                "my-test-app",
                false);
        
        // When
        Set<ConstraintViolation<PositionRequestDto>> violations = validator.validate(request);
        
        // Then
        assertFalse(violations.isEmpty());
        assertEquals(1, violations.size());
        assertTrue(violations.stream()
                .anyMatch(v -> v.getMessage().contains("Client is required")));
    }
    
    @Test
    void requestWithLongRequestId() {
        // Given
        String longRequestId = "a".repeat(65);
        PositionRequestDto request = new PositionRequestDto(
                List.of(validScanResult),
                "mobile-app",
                longRequestId,
                "my-test-app",
                false);
        
        // When
        Set<ConstraintViolation<PositionRequestDto>> violations = validator.validate(request);
        
        // Then
        assertFalse(violations.isEmpty());
        assertEquals(1, violations.size());
        assertTrue(violations.stream()
                .anyMatch(v -> v.getMessage().contains("Request ID must be at most 64 characters")));
    }
    
    @Test
    void defaultValues() {
        // Given
        PositionRequestDto request = new PositionRequestDto(
                List.of(validScanResult),
                "mobile-app",
                UUID.randomUUID().toString(),
                null,
                null);
        
        // Then
        assertNull(request.application());
        assertFalse(request.calculationDetail());
    }

    @Test
    void calculationDetailDefault() {
        // Given
        PositionRequestDto request = new PositionRequestDto(
                List.of(validScanResult),
                "mobile-app",
                UUID.randomUUID().toString(),
                "my-test-app",
                null);
        
        // Then
        assertFalse(request.calculationDetail());
    }
} 