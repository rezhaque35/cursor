package com.wifi.positioning.service.impl;

import com.wifi.positioning.algorithm.GPSPositioningCalculatorAdapter;
import com.wifi.positioning.dto.PositionRequestDto;
import com.wifi.positioning.dto.PositionResponseDto;
import com.wifi.positioning.dto.WifiScanResultDto;
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
    public PositionResponseDto calculatePosition(PositionRequestDto request) {
        if (request == null || request.wifiScanResults() == null || request.wifiScanResults().isEmpty()) {
            throw new PositioningException("No scan results provided", HttpStatus.BAD_REQUEST);
        }

        try {
            // Convert scan results to map for calculator
            Map<String, Map<String, Object>> scanResults = request.wifiScanResults().stream()
                    .collect(Collectors.toMap(
                            WifiScanResultDto::macAddress,
                            scan -> {
                                Map<String, Object> data = new HashMap<>();
                                data.put("signalStrength", scan.signalStrength());
                                data.put("frequency", scan.frequency());
                                data.put("ssid", scan.ssid());
                                data.put("linkSpeed", scan.linkSpeed());
                                data.put("channelWidth", scan.channelWidth());
                                return data;
                            }
                    ));

            // Get known access points
            Map<String, Map<String, Object>> knownAPs = new HashMap<>();
            for (String macAddress : scanResults.keySet()) {
                List<WifiAccessPoint> aps = accessPointRepository.findByMacAddress(macAddress);
                if (!aps.isEmpty()) {
                    // Use the most recent version
                    WifiAccessPoint ap = aps.get(0);
                    Map<String, Object> apData = new HashMap<>();
                    apData.put("latitude", ap.getLatitude());
                    apData.put("longitude", ap.getLongitude());
                    apData.put("altitude", ap.getAltitude());
                    apData.put("horizontalAccuracy", ap.getHorizontalAccuracy());
                    apData.put("verticalAccuracy", ap.getVerticalAccuracy());
                    apData.put("confidence", ap.getConfidence());
                    knownAPs.put(macAddress, apData);
                }
            }

            // Calculate position using the created options map
            Map<String, Object> result = positioningCalculator.calculatePosition(
                scanResults, 
                createOptionsMap(request, knownAPs)
            );

            // Build response
            return new PositionResponseDto(
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
                    Map.of(
                            "calculationTimeMs", result.get("calculationTimeMs"),
                            "timestamp", Instant.now().toString()
                    ),
                    buildAlternatives(result)
            );

        } catch (PositioningException e) {
            throw e;
        } catch (Exception e) {
            throw new PositioningException("Error calculating position: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private List<PositionResponseDto.AlternativePositionDto> buildAlternatives(Map<String, Object> result) {
        if (!result.containsKey("alternatives")) {
            return Collections.emptyList();
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> alternatives = (List<Map<String, Object>>) result.get("alternatives");

        return alternatives.stream()
                .map(alt -> new PositionResponseDto.AlternativePositionDto(
                        (Double) alt.get("latitude"),
                        (Double) alt.get("longitude"),
                        (Double) alt.get("altitude"),
                        (Double) alt.get("horizontalAccuracy"),
                        (Double) alt.get("verticalAccuracy"),
                        (Double) alt.get("confidence"),
                        (String) alt.get("method")
                ))
                .collect(Collectors.toList());
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
        
        return options;
    }
} 