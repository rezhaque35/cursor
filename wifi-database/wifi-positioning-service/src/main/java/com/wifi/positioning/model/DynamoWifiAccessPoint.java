package com.wifi.positioning.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*;

/**
 * DynamoDB entity model for WiFi access points.
 * Contains only the essential fields that are required for positioning calculations,
 * matching the fields in the WifiAccessPoint model.
 */
@Data
@NoArgsConstructor
@DynamoDbBean
public class DynamoWifiAccessPoint {

    private String macAddress;
    private String version;
    private Double latitude;
    private Double longitude;
    private Double altitude;
    private Double horizontalAccuracy;
    private Double verticalAccuracy;
    private Double confidence;
    private String ssid;
    private Integer frequency;
    private String vendor;
    private String status;
    private String geohash;

    @DynamoDbPartitionKey
    @DynamoDbAttribute("mac_addr")
    public String getMacAddress() {
        return macAddress;
    }

    @DynamoDbAttribute("version")
    public String getVersion() {
        return version;
    }

    @DynamoDbSecondaryPartitionKey(indexNames = "GeohashIndex")
    @DynamoDbSecondarySortKey(indexNames = "SSIDIndex")
    @DynamoDbAttribute("geohash")
    public String getGeohash() {
        return geohash;
    }

    @DynamoDbSecondaryPartitionKey(indexNames = "SSIDIndex")
    @DynamoDbSecondarySortKey(indexNames = "GeohashIndex")
    @DynamoDbAttribute("ssid")
    public String getSsid() {
        return ssid;
    }

    @DynamoDbSecondaryPartitionKey(indexNames = "StatusIndex")
    @DynamoDbAttribute("status")
    public String getStatus() {
        return status;
    }

    @DynamoDbAttribute("latitude")
    public Double getLatitude() {
        return latitude;
    }

    @DynamoDbAttribute("longitude")
    public Double getLongitude() {
        return longitude;
    }

    @DynamoDbAttribute("altitude")
    public Double getAltitude() {
        return altitude;
    }

    @DynamoDbAttribute("horizontal_accuracy")
    public Double getHorizontalAccuracy() {
        return horizontalAccuracy;
    }

    @DynamoDbAttribute("vertical_accuracy")
    public Double getVerticalAccuracy() {
        return verticalAccuracy;
    }

    @DynamoDbAttribute("confidence")
    public Double getConfidence() {
        return confidence;
    }

    @DynamoDbAttribute("frequency")
    public Integer getFrequency() {
        return frequency;
    }

    @DynamoDbAttribute("vendor")
    public String getVendor() {
        return vendor;
    }

    public DynamoWifiAccessPoint fromWifiAccessPoint(WifiAccessPoint ap) {
        this.macAddress = ap.getMacAddress();
        this.version = ap.getVersion();
        this.latitude = ap.getLatitude();
        this.longitude = ap.getLongitude();
        this.altitude = ap.getAltitude();
        this.horizontalAccuracy = ap.getHorizontalAccuracy();
        this.verticalAccuracy = ap.getVerticalAccuracy();
        this.confidence = ap.getConfidence();
        this.geohash = ap.getGeohash();
        this.ssid = ap.getSsid();
        this.frequency = ap.getFrequency();
        this.vendor = ap.getVendor();
        this.status = ap.getStatus();
        return this;
    }

    public WifiAccessPoint toWifiAccessPoint() {
        return WifiAccessPoint.builder()
                .macAddress(this.macAddress)
                .version(this.version)
                .latitude(this.latitude)
                .longitude(this.longitude)
                .altitude(this.altitude)
                .horizontalAccuracy(this.horizontalAccuracy)
                .verticalAccuracy(this.verticalAccuracy)
                .confidence(this.confidence)
                .geohash(this.geohash)
                .ssid(this.ssid)
                .frequency(this.frequency)
                .vendor(this.vendor)
                .status(this.status)
                .build();
    }
} 