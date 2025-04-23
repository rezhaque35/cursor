package com.wifi.positioning.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record PositionRequestDto(
    @NotEmpty(message = "At least one WiFi scan result is required")
    @Size(min = 1, max = 20, message = "Between 1 and 20 WiFi scan results must be provided")
    @Valid
    List<WifiScanResultDto> wifiScanResults,
    
    Boolean preferHighAccuracy,
    
    Boolean returnAllMethods,
    
    @Size(max = 100, message = "Session ID must be at most 100 characters")
    String sessionId
) {
    public PositionRequestDto {
        // Default values if null
        preferHighAccuracy = preferHighAccuracy != null ? preferHighAccuracy : false;
        returnAllMethods = returnAllMethods != null ? returnAllMethods : false;
    }
} 