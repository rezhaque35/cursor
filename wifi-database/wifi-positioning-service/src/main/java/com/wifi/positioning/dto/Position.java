package com.wifi.positioning.dto;

public record Position(
    Double latitude,
    Double longitude,
    Double altitude,
    Double accuracy,
    Double confidence
) {
    public Position {
        if (latitude == null || latitude < -90 || latitude > 90) {
            throw new IllegalArgumentException("Invalid latitude value");
        }
        if (longitude == null || longitude < -180 || longitude > 180) {
            throw new IllegalArgumentException("Invalid longitude value");
        }
        if (accuracy == null || accuracy < 0) {
            throw new IllegalArgumentException("Invalid accuracy value");
        }
        if (confidence == null || confidence < 0 || confidence > 1) {
            throw new IllegalArgumentException("Confidence must be between 0 and 1");
        }
    }
} 