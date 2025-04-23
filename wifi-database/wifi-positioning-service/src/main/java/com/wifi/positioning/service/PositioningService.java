package com.wifi.positioning.service;

import com.wifi.positioning.dto.PositionRequestDto;
import com.wifi.positioning.dto.PositionResponseDto;

/**
 * Service interface for WiFi positioning calculations.
 * Provides methods for calculating positions based on WiFi scan results.
 */
public interface PositioningService {
    
    /**
     * Calculate position based on WiFi scan results.
     *
     * @param request The position request containing WiFi scan results
     * @return A position response with latitude, longitude, and accuracy
     */
    PositionResponseDto calculatePosition(PositionRequestDto request);
} 