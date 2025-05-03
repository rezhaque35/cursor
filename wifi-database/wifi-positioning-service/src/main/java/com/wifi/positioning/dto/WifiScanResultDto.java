package com.wifi.positioning.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record WifiScanResultDto(
    @NotBlank(message = "MAC address is required")
    @Pattern(regexp = "([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})", message = "Invalid MAC address format")
    String macAddress,
    
    @Min(value = -100, message = "Signal strength must be at least -100 dBm")
    @Max(value = 0, message = "Signal strength must be at most 0 dBm")
    Integer signalStrength,
    
    @Min(value = 2400, message = "Frequency must be at least 2400 MHz")
    @Max(value = 6000, message = "Frequency must be at most 6000 MHz")
    Integer frequency,
    
    String ssid,
    
    @Min(value = 0, message = "Link speed must be non-negative")
    Integer linkSpeed,
    
    @Min(value = 20, message = "Channel width must be at least 20 MHz")
    @Max(value = 160, message = "Channel width must be at most 160 MHz")
    Integer channelWidth
) {
} 