package com.wifi.positioning.model;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WifiAccessPoint {
    private String macAddress;
    private String version;
    private Double latitude;
    private Double longitude;
    private Double altitude;
    private Double horizontalAccuracy;
    private Double verticalAccuracy;
    private Double confidence;
    private String bestMethod;
    private String[] methodsUsed;
    private Integer sampleCount;
    private Double signalStrengthAvg;
    private Double signalStrengthStd;
    private String geohash;
    private String ssid;
    private Integer frequency;
    private Integer channel;
    private String countryCode;
    private String vendor;
} 