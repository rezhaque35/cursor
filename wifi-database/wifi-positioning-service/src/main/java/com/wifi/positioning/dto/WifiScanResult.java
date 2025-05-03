package com.wifi.positioning.dto;

public record WifiScanResult(
    String macAddress,
    Double signalStrength,
    Integer frequency,
    String ssid
) {
    public WifiScanResult {
        if (macAddress == null || macAddress.isBlank()) {
            throw new IllegalArgumentException("MAC address cannot be null or blank");
        }
        if (signalStrength == null) {
            throw new IllegalArgumentException("Signal strength cannot be null");
        }
        if (frequency == null) {
            throw new IllegalArgumentException("Frequency cannot be null");
        }
    }
} 