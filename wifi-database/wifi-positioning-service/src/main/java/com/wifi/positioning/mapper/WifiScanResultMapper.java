package com.wifi.positioning.mapper;

import com.wifi.positioning.dto.WifiScanResult;
import com.wifi.positioning.dto.WifiScanResultDto;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Mapper class for converting between WifiScanResult and WifiScanResultDto objects.
 */
public class WifiScanResultMapper {

    /**
     * Converts a WifiScanResultDto to a WifiScanResult.
     *
     * @param dto The WifiScanResultDto to convert
     * @return A new WifiScanResult object
     */
    public static WifiScanResult toModel(WifiScanResultDto dto) {
        return new WifiScanResult(
            dto.macAddress(),
            dto.signalStrength().doubleValue(), // Convert Integer to Double
            dto.frequency(),
            dto.ssid()
        );
    }

    /**
     * Converts a list of WifiScanResultDto objects to a list of WifiScanResult objects.
     *
     * @param dtos The list of WifiScanResultDto objects to convert
     * @return A list of WifiScanResult objects
     */
    public static List<WifiScanResult> toModelList(List<WifiScanResultDto> dtos) {
        return dtos.stream()
            .map(WifiScanResultMapper::toModel)
            .collect(Collectors.toList());
    }

    /**
     * Creates a minimal WifiScanResultDto from a WifiScanResult.
     * Note: The returned DTO will not have linkSpeed and channelWidth values.
     *
     * @param model The WifiScanResult to convert
     * @return A new WifiScanResultDto object
     */
    public static WifiScanResultDto toDto(WifiScanResult model) {
        return new WifiScanResultDto(
            model.macAddress(),
            model.signalStrength().intValue(), // Convert Double to Integer
            model.frequency(),
            model.ssid(),
            null, // linkSpeed
            null  // channelWidth
        );
    }

    /**
     * Converts a list of WifiScanResult objects to a list of WifiScanResultDto objects.
     *
     * @param models The list of WifiScanResult objects to convert
     * @return A list of WifiScanResultDto objects
     */
    public static List<WifiScanResultDto> toDtoList(List<WifiScanResult> models) {
        return models.stream()
            .map(WifiScanResultMapper::toDto)
            .collect(Collectors.toList());
    }
    
    /**
     * Converts a list of WifiScanResultDto objects to a map format used by the positioning calculator.
     * The map uses MAC addresses as keys and contains all available properties from the DTO.
     *
     * @param dtos The list of WifiScanResultDto objects to convert
     * @return A map where keys are MAC addresses and values are maps of properties
     */
    public static Map<String, Map<String, Object>> toCalculatorMap(List<WifiScanResultDto> dtos) {
        Map<String, Map<String, Object>> resultMap = new HashMap<>();
        
        for (WifiScanResultDto dto : dtos) {
            Map<String, Object> apData = new HashMap<>();
            apData.put("signalStrength", dto.signalStrength());
            if (dto.frequency() != null) apData.put("frequency", dto.frequency());
            if (dto.ssid() != null) apData.put("ssid", dto.ssid());
            if (dto.linkSpeed() != null) apData.put("linkSpeed", dto.linkSpeed());
            if (dto.channelWidth() != null) apData.put("channelWidth", dto.channelWidth());
            
            resultMap.put(dto.macAddress(), apData);
        }
        
        return resultMap;
    }

    /**
     * Converts a map of scan results to a list of WifiScanResult objects.
     * This is used for converting the format used in the calculator API.
     *
     * @param scanResultsMap Map where keys are MAC addresses and values are property maps
     * @return A list of WifiScanResult objects
     */
    public static List<WifiScanResult> fromCalculatorMap(Map<String, Map<String, Object>> scanResultsMap) {
        List<WifiScanResult> results = new ArrayList<>();
        
        for (Map.Entry<String, Map<String, Object>> entry : scanResultsMap.entrySet()) {
            String macAddress = entry.getKey();
            Map<String, Object> data = entry.getValue();
            
            try {
                Double signalStrength = getDoubleValue(data, "signalStrength");
                if (signalStrength == null) {
                    continue; // Skip results without signal strength
                }
                
                Integer frequency = getIntValue(data, "frequency");
                String ssid = (String) data.get("ssid");
                
                results.add(new WifiScanResult(
                    macAddress,
                    signalStrength,
                    frequency,
                    ssid
                ));
            } catch (IllegalArgumentException e) {
                // Skip invalid entries
            }
        }
        
        return results;
    }
    
    private static Double getDoubleValue(Map<String, Object> data, String key) {
        Object value = data.get(key);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        } else if (value instanceof String) {
            try {
                return Double.parseDouble((String) value);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }
    
    private static Integer getIntValue(Map<String, Object> data, String key) {
        Object value = data.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        } else if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }
} 