package com.wifi.positioning.model;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * Model class representing a WiFi access point with positioning-relevant fields.
 * Contains only fields necessary for positioning algorithm calculations.
 * The status field indicates the current operational state of the access point.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WifiAccessPoint {
    /**
     * Status constants for access point operational states
     */
    public static final String STATUS_ACTIVE = "active";
    public static final String STATUS_ERROR = "error";
    public static final String STATUS_EXPIRED = "expired";
    public static final String STATUS_WARNING = "warning";
    
    private String macAddress;
    private String version;
    private Double latitude;
    private Double longitude;
    private Double altitude;
    private Double horizontalAccuracy;
    private Double verticalAccuracy;
    private Double confidence;
    private String geohash;
    private String ssid;
    private Integer frequency;
    private String vendor;
    private String status;
} 