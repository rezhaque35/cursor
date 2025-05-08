package com.wifi.positioning.service.impl;

import com.wifi.positioning.algorithm.GPSPositioningCalculatorAdapter;
import com.wifi.positioning.dto.PositionRequestDto;
import com.wifi.positioning.dto.PositionResponseDto;
import com.wifi.positioning.dto.WifiScanResult;
import com.wifi.positioning.exception.PositioningException;
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
    
    /**
     * Default high accuracy setting for backward compatibility
     */
    private static final boolean DEFAULT_HIGH_ACCURACY = false;
    
    /**
     * Default return all methods setting for backward compatibility
     */
    private static final boolean DEFAULT_RETURN_ALL_METHODS = false;
    
    private final GPSPositioningCalculatorAdapter positioningCalculator;
    
    @Autowired
    public PositioningServiceImpl(GPSPositioningCalculatorAdapter positioningCalculator) {
        this.positioningCalculator = positioningCalculator;
    }
    
    @Override
    public PositionResponseDto calculatePosition(PositionRequestDto request) {
        logger.info("Calculating position for {} WiFi scan results from client {} with requestId {}", 
                request.wifiScanResults().size(), request.client(), request.requestId());
        
        // Check if we have any scan results
        if (request.wifiScanResults().isEmpty()) {
            throw new PositioningException("No WiFi scan results provided");
        }
        
        try {
        
       
            
            // Create options map and calculate position
            Map<String, Object> result = positioningCalculator.calculatePosition(
                request.wifiScanResults(), 
                createOptionsMap(request)
            );
            
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
     * Creates an options map for the positioning calculator with client information and timestamp.
     *
     * @param request The position request DTO containing client information
     * @return A map of options for the positioning calculator
     */
    private Map<String, Object> createOptionsMap(PositionRequestDto request) {
        Map<String, Object> options = new HashMap<>();
        
        // Add client information
        options.put("client", request.client());
        options.put("requestId", request.requestId());
        
        if (request.application() != null) {
            options.put("application", request.application());
        }
        
        // Add timestamp
        options.put("timestamp", Instant.now().toEpochMilli());
        
        // Add calculationDetail flag if it's true
        if (Boolean.TRUE.equals(request.calculationDetail())) {
            options.put("calculationDetail", true);
        }
        
        return options;
    }
} 