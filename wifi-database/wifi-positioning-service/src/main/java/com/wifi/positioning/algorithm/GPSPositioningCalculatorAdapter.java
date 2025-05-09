package com.wifi.positioning.algorithm;

import com.wifi.positioning.dto.Position;
import com.wifi.positioning.dto.WifiScanResult;
import com.wifi.positioning.model.WifiAccessPoint;
import com.wifi.positioning.repository.WifiAccessPointRepository;
import com.wifi.positioning.validation.SignalPhysicsValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Adapter for the GPSPositioningCalculator to handle Map input instead of List<WifiScanResult>
 * and provide detailed positioning results.
 * 
 * This adapter:
 * 1. Converts the input map format to domain objects
 * 2. Performs validation on input data
 * 3. Looks up known access points from the repository using optimized batch operations
 * 4. Delegates positioning calculation to GPSPositioningCalculator
 * 5. Processes the PositioningResult to extract algorithm information
 * 6. Formats the response with algorithm names and positioning data
 * 
 * The adapter ensures that the algorithm names in the response directly correspond
 * to the algorithms that were actually used in the calculation, rather than
 * using hardcoded or independently determined values.
 */
@Component
public class GPSPositioningCalculatorAdapter {
    
    private static final Logger logger = LoggerFactory.getLogger(GPSPositioningCalculatorAdapter.class);
    
    
    /**
     * Default value for vertical accuracy when not provided by the positioning algorithms.
     * Set to 0.0 as most algorithms in this system only calculate horizontal accuracy.
     */
    private static final double DEFAULT_VERTICAL_ACCURACY = 0.0;
    

    
    private final GPSPositioningCalculator calculator;
    private final WifiAccessPointRepository accessPointRepository;
    private final SignalPhysicsValidator signalPhysicsValidator;
    
    @Autowired
    public GPSPositioningCalculatorAdapter(
            GPSPositioningCalculator calculator,
            WifiAccessPointRepository accessPointRepository,
            SignalPhysicsValidator signalPhysicsValidator) {
        this.calculator = calculator;
        this.accessPointRepository = accessPointRepository;
        this.signalPhysicsValidator = signalPhysicsValidator;
    }
    
    /**
     * Calculate position using a list of WifiScanResult objects.
     * 
     * @param scanResults List of WiFi scan results
     * @param options Optional calculation parameters
     * @return A map containing position results
     */
    public Map<String, Object> calculatePosition(List<WifiScanResult> scanResults, Map<String, Object> options) {
        if (scanResults == null || scanResults.isEmpty()) {
            logger.warn("No scan results provided");
            return new HashMap<>();
        }
        
        try {
            
            // Check if the signal physics is valid
            if (!signalPhysicsValidator.isPhysicallyPossible(scanResults)) {
                logger.warn("Physically impossible signal strength relationships detected");
                return createErrorResponse("Physically impossible signal strength relationships", options);
            }
            
            // Lookup known APs using their MAC addresses
            List<WifiAccessPoint> knownAPs = lookupKnownAccessPoints(scanResults);
            logger.info("Found {} known access points in database out of {} scan results", 
                    knownAPs.size(), scanResults.size());
            
            if (knownAPs.isEmpty()) {
                logger.warn("No known access points found in database");
                return createPositionNotFoundResponse(scanResults.size(), options);
            }
            
            // Calculate position
            long startTime = System.currentTimeMillis();
            GPSPositioningCalculator.PositioningResult positioningResult = calculator.calculatePosition(scanResults, knownAPs);
            long calculationTime = System.currentTimeMillis() - startTime;
            
            if (positioningResult == null || positioningResult.position() == null) {
                logger.warn("Position calculation failed");
                return createPositionNotFoundResponse(scanResults.size(), options);
            }
            
            // Get methods used from the positioning result
            List<String> methodsUsed = positioningResult.getMethodsUsedNames();
            
            // Convert position to result map
            Map<String, Object> result = positionToResultMap(
                positioningResult.position(), 
                scanResults.size(), 
                calculationTime, 
                options,
                methodsUsed
            );
            
            // Add calculation info if requested
            if (options != null && Boolean.TRUE.equals(options.get("calculationDetail"))) {
                String calculationInfo = positioningResult.getCalculationInfo();
                if (calculationInfo != null && !calculationInfo.isEmpty()) {
                    logger.info("Adding calculation info to the response, size: {}", calculationInfo.length());
                    result.put("calculationInfo", calculationInfo);
                } else {
                    logger.warn("No calculation info available despite calculationDetail flag being true");
                }
            }
            
            return result;
        } catch (Exception e) {
            logger.error("Error calculating position", e);
            return createErrorResponse(e.getMessage(), options);
        }
    }
    

    
    /**
     * Create a response map when position calculation fails
     */
    private Map<String, Object> createPositionNotFoundResponse(int apCount, Map<String, Object> options) {
        Map<String, Object> result = new HashMap<>();
        result.put("positionFound", false);
        result.put("apCount", apCount);
        result.put("calculationTimeMs", 0L);
        
        // Include any additional options
        if (options != null) {
            result.putAll(options);
        }
        
        return result;
    }
    
    /**
     * Create a response map when an error occurs
     */
    private Map<String, Object> createErrorResponse(String errorMessage, Map<String, Object> options) {
        Map<String, Object> result = new HashMap<>();
        result.put("error", true);
        result.put("errorMessage", errorMessage);
        result.put("result", "ERROR");
        
        // Include any additional options
        if (options != null) {
            result.putAll(options);
        }
        
        return result;
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
    
    
    /**
     * Convert a Position object to a map containing all position data
     */
    private Map<String, Object> positionToResultMap(Position position, int apCount, long calculationTime, 
                                                   Map<String, Object> options, List<String> methodsUsed) {
        Map<String, Object> result = new HashMap<>();
        
        if (position != null) {
            result.put("positionFound", true);
            result.put("result", "SUCCESS");
            result.put("latitude", position.latitude());
            result.put("longitude", position.longitude());
            result.put("altitude", position.altitude());
            result.put("horizontalAccuracy", position.accuracy());
            result.put("verticalAccuracy", DEFAULT_VERTICAL_ACCURACY);
            result.put("confidence", position.confidence());
            result.put("methodsUsed", methodsUsed);
            result.put("apCount", apCount);
            result.put("calculationTimeMs", calculationTime);
            
            // Include any additional options
            if (options != null) {
                result.putAll(options);
            }
        }
        
        return result;
    }
} 