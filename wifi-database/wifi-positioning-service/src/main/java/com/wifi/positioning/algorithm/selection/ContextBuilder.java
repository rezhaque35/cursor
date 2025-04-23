package com.wifi.positioning.algorithm.selection;

import com.wifi.positioning.dto.WifiScanResult;
import com.wifi.positioning.model.WifiAccessPoint;

import java.util.List;
import java.util.Map;

/**
 * Interface for building selection context from WiFi scan results and access points.
 * Implementations can provide different strategies for evaluating scenario characteristics.
 */
public interface ContextBuilder {
    
    /**
     * Evaluate scenario characteristics and build a selection context.
     * 
     * @param validScans List of valid WiFi scan results
     * @param apMap Map of known access points by MAC address
     * @return The selection context with scenario characteristics
     */
    SelectionContext buildContext(List<WifiScanResult> validScans, Map<String, WifiAccessPoint> apMap);
} 