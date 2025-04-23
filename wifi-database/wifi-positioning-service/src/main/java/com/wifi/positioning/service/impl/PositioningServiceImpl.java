package com.wifi.positioning.service.impl;

import com.wifi.positioning.algorithm.GPSPositioningCalculatorAdapter;
import com.wifi.positioning.dto.PositionRequestDto;
import com.wifi.positioning.dto.PositionResponseDto;
import com.wifi.positioning.dto.WifiScanResultDto;
import com.wifi.positioning.exception.PositioningException;
import com.wifi.positioning.mapper.WifiScanResultMapper;
import com.wifi.positioning.service.PositioningService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

/**
 * Implementation of the PositioningService interface.
 * Provides functionality for calculating positions based on WiFi scan results.
 */
@Service
@Profile("!test")
public class PositioningServiceImpl implements PositioningService {

    private static final Logger logger = LoggerFactory.getLogger(PositioningServiceImpl.class);
    private final GPSPositioningCalculatorAdapter positioningCalculator;
    
    @Autowired
    public PositioningServiceImpl(GPSPositioningCalculatorAdapter positioningCalculator) {
        this.positioningCalculator = positioningCalculator;
    }
    
    @Override
    public PositionResponseDto calculatePosition(PositionRequestDto request) {
        logger.info("Calculating position for {} WiFi scan results", request.wifiScanResults().size());
        
        // Check if we have any scan results
        if (request.wifiScanResults().isEmpty()) {
            throw new PositioningException("No WiFi scan results provided");
        }
        
        try {
            // Convert DTOs to the input format expected by the calculator
            Map<String, Map<String, Object>> scanResultsMap = convertScanResults(request.wifiScanResults());
            
            // Create options map
            Map<String, Object> options = new HashMap<>();
            options.put("preferHighAccuracy", request.preferHighAccuracy());
            options.put("returnAllMethods", request.returnAllMethods());
            if (request.sessionId() != null) {
                options.put("sessionId", request.sessionId());
            }
            options.put("timestamp", Instant.now().toEpochMilli());
            
            // Calculate position
            Map<String, Object> result = positioningCalculator.calculatePosition(scanResultsMap, options);
            
            if (result == null || result.isEmpty() || Boolean.FALSE.equals(result.get("positionFound"))) {
                if (result.containsKey("errorMessage")) {
                    throw new PositioningException((String) result.get("errorMessage"));
                }
                throw new PositioningException("Unable to calculate position with provided scan results");
            }
            
            return new PositionResponseDto(result);
        } catch (Exception e) {
            if (e instanceof PositioningException) {
                throw e;
            }
            throw new PositioningException("Error calculating position: " + e.getMessage(), e);
        }
    }
    
    /**
     * Converts a list of WifiScanResultDto objects to a map format used by the positioning calculator.
     */
    private Map<String, Map<String, Object>> convertScanResults(List<WifiScanResultDto> scanResults) {
        return WifiScanResultMapper.toCalculatorMap(scanResults);
    }
} 