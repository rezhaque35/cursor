package com.wifi.positioning.repository;

import com.wifi.positioning.model.WifiAccessPoint;

import java.util.List;

/**
 * Repository interface for accessing WiFi access point data.
 * Only includes methods that are necessary for position calculation.
 */
public interface WifiAccessPointRepository {
    
    /**
     * Find all versions of an access point by its MAC address.
     * This is the only method used by the positioning service.
     * 
     * @param macAddress MAC address of the access point
     * @return List of access points matching the MAC address
     */
    List<WifiAccessPoint> findByMacAddress(String macAddress);
} 