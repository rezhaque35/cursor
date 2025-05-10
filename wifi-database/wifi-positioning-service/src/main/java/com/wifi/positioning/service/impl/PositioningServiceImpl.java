package com.wifi.positioning.service.impl;

import com.wifi.positioning.algorithm.GPSPositioningCalculator;
import com.wifi.positioning.dto.Position;
import com.wifi.positioning.dto.PositionRequestDto;
import com.wifi.positioning.dto.WifiPositioningResponse;
import com.wifi.positioning.dto.WifiPositioningResponse.WifiPosition;
import com.wifi.positioning.dto.WifiScanResult;
import com.wifi.positioning.exception.PositioningException;
import com.wifi.positioning.model.WifiAccessPoint;
import com.wifi.positioning.repository.WifiAccessPointRepository;
import com.wifi.positioning.service.PositioningService;
import com.wifi.positioning.validation.SignalPhysicsValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Implementation of the PositioningService interface.
 * Provides functionality for calculating positions based on WiFi scan results.
 * 
 * This service:
 * 1. Receives WiFi scan results from client devices
 * 2. Performs validation on input data
 * 3. Looks up known access points from the repository using optimized batch operations
 * 4. Delegates positioning calculation to GPSPositioningCalculator
 * 5. Processes the PositioningResult to extract algorithm information
 * 6. Formats the response with algorithm names and positioning data
 */
@Service
@Profile("!test")
public class PositioningServiceImpl implements PositioningService {

    private static final Logger logger = LoggerFactory.getLogger(PositioningServiceImpl.class);
    
    /**
     * Default value for vertical accuracy when not provided by the positioning algorithms.
     * Set to 0.0 as most algorithms in this system only calculate horizontal accuracy.
     */
    private static final double DEFAULT_VERTICAL_ACCURACY = 0.0;
    
    /**
     * Default high accuracy setting for backward compatibility
     */
    private static final boolean DEFAULT_HIGH_ACCURACY = false;
    
    /**
     * Default return all methods setting for backward compatibility
     */
    private static final boolean DEFAULT_RETURN_ALL_METHODS = false;
    
    private final GPSPositioningCalculator calculator;
    private final WifiAccessPointRepository accessPointRepository;
    private final SignalPhysicsValidator signalPhysicsValidator;
    
    @Autowired
    public PositioningServiceImpl(
            GPSPositioningCalculator calculator,
            WifiAccessPointRepository accessPointRepository,
            SignalPhysicsValidator signalPhysicsValidator) {
        this.calculator = calculator;
        this.accessPointRepository = accessPointRepository;
        this.signalPhysicsValidator = signalPhysicsValidator;
    }
    
    @Override
    public WifiPositioningResponse calculatePosition(PositionRequestDto request) {
        logger.info("Calculating position for {} WiFi scan results from client {} with requestId {}", 
                request.wifiScanResults().size(), request.client(), request.requestId());
        
        // Check if we have any scan results
        if (request.wifiScanResults().isEmpty()) {
            throw new PositioningException("No WiFi scan results provided");
        }
        
        try {
            List<WifiScanResult> scanResults = request.wifiScanResults();
            
            // Check if the signal physics is valid
            if (!signalPhysicsValidator.isPhysicallyPossible(scanResults)) {
                logger.warn("Physically impossible signal strength relationships detected");
                return WifiPositioningResponse.error(
                    "Physically impossible signal strength relationships", 
                    request
                );
            }
            
            // Lookup known APs using their MAC addresses
            List<WifiAccessPoint> knownAPs = lookupKnownAccessPoints(scanResults);
            logger.info("Found {} known access points in database out of {} scan results", 
                    knownAPs.size(), scanResults.size());
            
            if (knownAPs.isEmpty()) {
                logger.warn("No known access points found in database");
                return createPositionNotFoundResponse(scanResults.size(), request);
            }
            
            // Calculate position
            long startTime = System.currentTimeMillis();
            GPSPositioningCalculator.PositioningResult positioningResult = calculator.calculatePosition(scanResults, knownAPs);
            long calculationTime = System.currentTimeMillis() - startTime;
            
            if (positioningResult == null || positioningResult.position() == null) {
                logger.warn("Position calculation failed");
                return createPositionNotFoundResponse(scanResults.size(), request);
            }

            // Validate position coordinates
            Position position = positioningResult.position();
            if (position.latitude() == null || position.longitude() == null || 
                Double.isNaN(position.latitude()) || Double.isNaN(position.longitude())) {
                logger.warn("Invalid coordinates in position result");
                return createPositionNotFoundResponse(scanResults.size(), request);
            }
            
            // Get methods used from the positioning result
            List<String> methodsUsed = positioningResult.getMethodsUsedNames();
            
            // Convert position to WifiPosition object
            WifiPosition wifiPosition = WifiPosition.fromPosition(
                position,
                methodsUsed,
                scanResults.size(),
                calculationTime
            );
            
            // Add calculation info if requested
            String calculationInfo = null;
            if (Boolean.TRUE.equals(request.calculationDetail())) {
                calculationInfo = positioningResult.getCalculationInfo();
                if (calculationInfo == null || calculationInfo.isEmpty()) {
                    logger.warn("No calculation info available despite calculationDetail flag being true");
                }
            }
            
            return WifiPositioningResponse.success(request, wifiPosition, calculationInfo);
            
        } catch (Exception e) {
            if (e instanceof PositioningException) {
                // For specific handled exceptions, propagate them
                throw (PositioningException) e;
            }
            // For unhandled exceptions, return an error response
            logger.error("Error calculating position", e);
            return WifiPositioningResponse.error(e.getMessage(), request);
        }
    }
    
    /**
     * Create a response when position calculation fails
     */
    private WifiPositioningResponse createPositionNotFoundResponse(int apCount, PositionRequestDto request) {
        String message = "Position calculation failed: no position could be determined";
        return WifiPositioningResponse.error(message, request);
    }
    
    /**
     * Lookup known access points from the repository based on MAC addresses from scan results.
     * Uses batch operation to optimize DynamoDB access.
     */
    private List<WifiAccessPoint> lookupKnownAccessPoints(List<WifiScanResult> scanResults) {
        // Extract all MAC addresses from scan results
        Set<String> macAddresses = scanResults.stream()
                .map(WifiScanResult::macAddress)
                .collect(Collectors.toSet());
        
        if (macAddresses.isEmpty()) {
            logger.warn("No MAC addresses found in scan results");
            return Collections.emptyList();
        }
        
        try {
            // Use batch operation to retrieve all access points in a single call
            Map<String, WifiAccessPoint> apMap = accessPointRepository.findByMacAddresses(macAddresses);
            
            List<WifiAccessPoint> knownAPs = new ArrayList<>();
            
            // Process the results with null safety
            if (apMap != null) {
                // Convert map values to list
                knownAPs.addAll(apMap.values());
            } else {
                logger.warn("Batch lookup returned null map");
            }
            
            logger.info("Found {} known access points in database out of {} scan results", 
                    knownAPs.size(), scanResults.size());
            
            return knownAPs;
            
        } catch (Exception e) {
            logger.error("Error in batch lookup of access points: {}", e.getMessage(), e);
            
            // Fall back to individual lookups if batch operation fails
            logger.warn("Falling back to individual lookups due to batch operation failure");
            return fallbackIndividualLookups(macAddresses);
        }
    }
    
    /**
     * Fallback method to look up access points individually if batch operation fails.
     * This ensures the system continues to function even if the batch operation encounters an error.
     */
    private List<WifiAccessPoint> fallbackIndividualLookups(Set<String> macAddresses) {
        List<WifiAccessPoint> knownAPs = new ArrayList<>();
        
        // Look up each MAC address individually
        for (String macAddress : macAddresses) {
            try {
                Optional<WifiAccessPoint> ap = accessPointRepository.findByMacAddress(macAddress);
                ap.ifPresent(knownAPs::add);
            } catch (Exception e) {
                logger.warn("Error looking up access point with MAC {}: {}", macAddress, e.getMessage());
            }
        }
        
        return knownAPs;
    }
} 