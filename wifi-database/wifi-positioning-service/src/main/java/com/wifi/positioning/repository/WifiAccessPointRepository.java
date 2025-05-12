package com.wifi.positioning.repository;

import com.wifi.positioning.dto.WifiAccessPoint;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Repository interface for accessing WiFi access point data.
 * Includes methods necessary for efficient position calculation.
 */
public interface WifiAccessPointRepository {
    
    /**
     * Find an access point by its MAC address.
     * This is the primary method used by the positioning service.
     * 
     * @param macAddress MAC address of the access point
     * @return Optional containing the access point if found, empty otherwise
     */
    Optional<WifiAccessPoint> findByMacAddress(String macAddress);
    
    /**
     * Find multiple access points by their MAC addresses in a single batch operation.
     * This method optimizes DynamoDB access by reducing the number of API calls.
     * 
     * @param macAddresses Set of MAC addresses to look up
     * @return Map of MAC addresses to matching access points
     */
    Map<String, WifiAccessPoint> findByMacAddresses(Set<String> macAddresses);
} 