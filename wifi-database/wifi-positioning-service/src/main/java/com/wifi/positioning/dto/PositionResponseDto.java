package com.wifi.positioning.dto;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public record PositionResponseDto(
    Double latitude,
    Double longitude,
    Double altitude,
    Double horizontalAccuracy,
    Double verticalAccuracy,
    Double confidence,
    String bestMethod,
    List<String> methodsUsed,
    Integer apCount,
    Map<String, Object> metadata,
    List<AlternativePositionDto> alternatives
) {
    /**
     * Constructs a PositionResponseDto from a map of calculation results.
     * 
     * @param calculationResult Map containing the calculation results
     */
    public PositionResponseDto(Map<String, Object> calculationResult) {
        this(
            (Double) calculationResult.get("latitude"),
            (Double) calculationResult.get("longitude"),
            (Double) calculationResult.get("altitude"),
            (Double) calculationResult.get("horizontalAccuracy"),
            (Double) calculationResult.get("verticalAccuracy"),
            (Double) calculationResult.get("confidence"),
            (String) calculationResult.get("bestMethod"),
            getMethodsUsedFromMap(calculationResult),
            (Integer) calculationResult.get("apCount"),
            getMetadataFromMap(calculationResult),
            getAlternativesFromMap(calculationResult)
        );
    }
    
    private static List<String> getMethodsUsedFromMap(Map<String, Object> calculationResult) {
        @SuppressWarnings("unchecked")
        List<String> methodsUsed = (List<String>) calculationResult.get("methodsUsed");
        if (methodsUsed == null) {
            String bestMethod = (String) calculationResult.get("bestMethod");
            if (bestMethod != null) {
                return Collections.singletonList(bestMethod);
            }
            return Collections.emptyList();
        }
        return methodsUsed;
    }
    
    private static Map<String, Object> getMetadataFromMap(Map<String, Object> calculationResult) {
        Map<String, Object> metadata = new HashMap<>();
        
        // Copy known metadata fields
        Object calculationTime = calculationResult.get("calculationTimeMs");
        if (calculationTime != null) {
            metadata.put("calculationTimeMs", calculationTime);
        }
        
        Object timestamp = calculationResult.get("timestamp");
        if (timestamp != null) {
            metadata.put("timestamp", timestamp);
        }
        
        // Copy any extra fields that aren't in the main response
        for (Map.Entry<String, Object> entry : calculationResult.entrySet()) {
            String key = entry.getKey();
            if (!isMainResponseField(key)) {
                metadata.put(key, entry.getValue());
            }
        }
        
        return metadata;
    }
    
    private static boolean isMainResponseField(String key) {
        return switch (key) {
            case "latitude", "longitude", "altitude", "horizontalAccuracy", "verticalAccuracy",
                 "confidence", "bestMethod", "methodsUsed", "apCount", "alternatives" -> true;
            default -> false;
        };
    }
    
    private static List<AlternativePositionDto> getAlternativesFromMap(Map<String, Object> calculationResult) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> alternativesList = (List<Map<String, Object>>) calculationResult.get("alternatives");
        
        if (alternativesList == null || alternativesList.isEmpty()) {
            return Collections.emptyList();
        }
        
        List<AlternativePositionDto> alternatives = new ArrayList<>();
        for (Map<String, Object> alt : alternativesList) {
            alternatives.add(new AlternativePositionDto(
                (Double) alt.get("latitude"),
                (Double) alt.get("longitude"),
                (Double) alt.get("altitude"),
                (Double) alt.get("horizontalAccuracy"),
                (Double) alt.get("verticalAccuracy"),
                (Double) alt.get("confidence"),
                (String) alt.get("method")
            ));
        }
        
        return alternatives;
    }
    
    public record AlternativePositionDto(
        Double latitude,
        Double longitude,
        Double altitude,
        Double horizontalAccuracy,
        Double verticalAccuracy,
        Double confidence,
        String method
    ) {}
} 