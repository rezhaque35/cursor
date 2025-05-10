package com.wifi.positioning.dto;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

/**
 * Combined response object for WiFi positioning calculations.
 * This record encapsulates both the API response metadata and positioning data
 * in a flattened structure for easier client consumption.
 */
public record WifiPositioningResponse(
    // API Response fields
    String result,             // SUCCESS or ERROR
    String message,            // Success or error message
    String requestId,          // ID that matches the original request ID
    String client,             // Client that made the request
    String application,        // Application that made the request
    Long timestamp,            // Timestamp of the response
    
    // Position data (may be null in error scenarios)
    WifiPosition wifiPosition,
    
    // Calculation information (optional)
    String calculationInfo
) {
    /**
     * Creates a success response with position data.
     */
    public static WifiPositioningResponse success(
            PositionRequestDto request, 
            WifiPosition wifiPosition,
            String calculationInfo) {
        return new WifiPositioningResponse(
            "SUCCESS",
            "Request processed successfully",
            request.requestId(),
            request.client(),
            request.application(),
            Instant.now().toEpochMilli(),
            wifiPosition,
            calculationInfo
        );
    }
    
    /**
     * Creates an error response with a specific error message.
     */
    public static WifiPositioningResponse error(
            String errorMessage,
            PositionRequestDto request) {
        return new WifiPositioningResponse(
            "ERROR",
            errorMessage,
            request.requestId(),
            request.client(),
            request.application(),
            Instant.now().toEpochMilli(),
            null,
            null
        );
    }
    
    /**
     * Position data specific to WiFi positioning.
     */
    public record WifiPosition(
        Double latitude,
        Double longitude,
        Double altitude,
        Double horizontalAccuracy,
        Double verticalAccuracy,
        Double confidence,
        List<String> methodsUsed,
        Integer apCount,
        Long calculationTimeMs
    ) {
        /**
         * Convenience constructor that sets default values for optional fields.
         */
        public WifiPosition {
            if (methodsUsed == null) {
                methodsUsed = Collections.emptyList();
            }
        }
        
        /**
         * Creates a WifiPosition from a Position object.
         */
        public static WifiPosition fromPosition(
                Position position, 
                List<String> methodsUsed, 
                int apCount, 
                long calculationTimeMs) {
            if (position == null) {
                return null;
            }
            
            return new WifiPosition(
                position.latitude(),
                position.longitude(),
                position.altitude(),
                position.accuracy(),  // horizontalAccuracy
                0.0,                  // Default verticalAccuracy
                position.confidence(),
                methodsUsed,
                apCount,
                calculationTimeMs
            );
        }
    }
} 