package com.wifi.positioning.service.impl;

import com.wifi.positioning.algorithm.GPSPositioningCalculatorAdapter;
import com.wifi.positioning.dto.PositionRequestDto;
import com.wifi.positioning.dto.WifiPositioningResponse;
import com.wifi.positioning.dto.WifiPositioningResponse.WifiPosition;
import com.wifi.positioning.dto.WifiScanResult;
import com.wifi.positioning.exception.PositioningException;
import com.wifi.positioning.model.WifiAccessPoint;
import com.wifi.positioning.repository.WifiAccessPointRepository;
import com.wifi.positioning.service.PositioningService;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Test implementation of PositioningService for unit testing.
 */
@Service
@Profile("test")
public class PositioningServiceTestImpl implements PositioningService {

    private final GPSPositioningCalculatorAdapter positioningCalculator;
    private final WifiAccessPointRepository accessPointRepository;

    public PositioningServiceTestImpl(
            GPSPositioningCalculatorAdapter positioningCalculator,
            WifiAccessPointRepository accessPointRepository) {
        this.positioningCalculator = positioningCalculator;
        this.accessPointRepository = accessPointRepository;
    }

    @Override
    public WifiPositioningResponse calculatePosition(PositionRequestDto request) {
        if (request == null || request.wifiScanResults() == null || request.wifiScanResults().isEmpty()) {
            throw new PositioningException("No scan results provided", HttpStatus.BAD_REQUEST);
        }

        try {
            // Get known access points for creating options map
            Map<String, Map<String, Object>> knownAPs = new HashMap<>();
            for (WifiScanResult scan : request.wifiScanResults()) {
                String macAddress = scan.macAddress();
                Optional<WifiAccessPoint> ap = accessPointRepository.findByMacAddress(macAddress);
                if (ap.isPresent()) {
                    Map<String, Object> apData = new HashMap<>();
                    apData.put("latitude", ap.get().getLatitude());
                    apData.put("longitude", ap.get().getLongitude());
                    apData.put("altitude", ap.get().getAltitude());
                    apData.put("horizontalAccuracy", ap.get().getHorizontalAccuracy());
                    apData.put("verticalAccuracy", ap.get().getVerticalAccuracy());
                    apData.put("confidence", ap.get().getConfidence());
                    knownAPs.put(macAddress, apData);
                }
            }

            // Calculate position using the WifiScanResult objects directly
            Map<String, Object> result = positioningCalculator.calculatePosition(
                request.wifiScanResults(), 
                createOptionsMap(request, knownAPs)
            );

            // If no position found, return error response
            if (result.get("latitude") == null || result.get("longitude") == null) {
                return WifiPositioningResponse.error(
                    "Position calculation failed: no position could be determined",
                    request
                );
            }

            // Build position data
            WifiPosition wifiPosition = new WifiPosition(
                (Double) result.get("latitude"),
                (Double) result.get("longitude"),
                (Double) result.get("altitude"),
                (Double) result.get("horizontalAccuracy"),
                (Double) result.get("verticalAccuracy"),
                (Double) result.get("confidence"),
                ((List<?>) result.get("methodsUsed")).stream()
                    .map(Object::toString)
                    .collect(Collectors.toList()),
                (Integer) result.get("apCount"),
                (Long) result.get("calculationTimeMs")
            );
            
            // Extract calculation info if present
            String calculationInfo = (String) result.get("calculationInfo");
            
            // Build success response
            return WifiPositioningResponse.success(
                request,
                wifiPosition,
                calculationInfo
            );

        } catch (PositioningException e) {
            throw e;
        } catch (Exception e) {
            return WifiPositioningResponse.error(
                "Error calculating position: " + e.getMessage(),
                request
            );
        }
    }

    /**
     * Creates an options map for the positioning calculator with client information, 
     * known access points, and timestamp.
     *
     * @param request The position request DTO containing client information
     * @param knownAPs The map of known access points
     * @return A map of options for the positioning calculator
     */
    private Map<String, Object> createOptionsMap(PositionRequestDto request, Map<String, Map<String, Object>> knownAPs) {
        Map<String, Object> options = new HashMap<>();
        
        // Add known access points
        options.put("knownAccessPoints", knownAPs);
        
        // Add client information
        options.put("client", request.client());
        options.put("requestId", request.requestId());
        
        if (request.application() != null) {
            options.put("application", request.application());
        }
        
        // Add backward compatibility fields with default values
        options.put("preferHighAccuracy", false);
        options.put("returnAllMethods", false);
        options.put("timestamp", Instant.now().toEpochMilli());
        
        // Add calculation detail flag if it's true
        if (Boolean.TRUE.equals(request.calculationDetail())) {
            options.put("calculationDetail", true);
        }
        
        return options;
    }

    private Map<String, WifiAccessPoint> lookupWifiInfo(Map<String, Map<String, Object>> scanResults) {
        if (scanResults == null || scanResults.isEmpty()) {
            return Collections.emptyMap();
        }
        
        Map<String, WifiAccessPoint> wifiInfoMap = new HashMap<>();
        
        for (Map.Entry<String, Map<String, Object>> entry : scanResults.entrySet()) {
            String macAddress = entry.getKey();
            
            // Look up access point information
            Optional<WifiAccessPoint> ap = accessPointRepository.findByMacAddress(macAddress);
            ap.ifPresent(wifiAccessPoint -> wifiInfoMap.put(macAddress, wifiAccessPoint));
        }
        
        return wifiInfoMap;
    }
} 