package com.wifi.positioning.mapper;

import com.wifi.positioning.dto.WifiScanResult;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Mapper class for converting WifiScanResult objects to and from map formats
 * used by the calculator.
 */
public class WifiScanResultMapper {

    /**
     * Converts a list of WifiScanResult objects to a map format used by the positioning calculator.
     * The map uses MAC addresses as keys and contains all available properties from the DTO.
     *
     * @param dtos The list of WifiScanResult objects to convert
     * @return A map where keys are MAC addresses and values are maps of properties
     */
    public static Map<String, Map<String, Object>> toCalculatorMap(List<WifiScanResult> dtos) {
        Map<String, Map<String, Object>> resultMap = new HashMap<>();
        
        for (WifiScanResult dto : dtos) {
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
                Integer linkSpeed = getIntValue(data, "linkSpeed");
                Integer channelWidth = getIntValue(data, "channelWidth");
                
                results.add(new WifiScanResult(
                    macAddress,
                    signalStrength,
                    frequency,
                    ssid,
                    linkSpeed,
                    channelWidth
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