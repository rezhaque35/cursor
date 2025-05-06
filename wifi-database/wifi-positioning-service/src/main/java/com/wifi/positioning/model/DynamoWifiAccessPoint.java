package com.wifi.positioning.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

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
    private String bestMethod;
    private List<String> methodsUsed;
    private Integer sampleCount;
    private String firstSeen;
    private String lastSeen;
    private String ssid;
    private Integer frequency;
    private String countryCode;
    private String vendor;
    private Double signalStrengthAvg;
    private Double signalStrengthStd;
    private Map<String, Integer> readingsByHour;
    private String calculationTime;
    private Long ttl;
    private String status;
    private Integer calculationDurationMs;
    private String calculationVersion;
    private String geohash;
    private String errorType;

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

    @DynamoDbAttribute("best_method")
    public String getBestMethod() {
        return bestMethod;
    }

    @DynamoDbAttribute("methods_used")
    public List<String> getMethodsUsed() {
        return methodsUsed;
    }

    @DynamoDbAttribute("sample_count")
    public Integer getSampleCount() {
        return sampleCount;
    }

    @DynamoDbAttribute("first_seen")
    public String getFirstSeen() {
        return firstSeen;
    }

    @DynamoDbAttribute("last_seen")
    public String getLastSeen() {
        return lastSeen;
    }

    @DynamoDbAttribute("frequency")
    public Integer getFrequency() {
        return frequency;
    }

    @DynamoDbAttribute("country_code")
    public String getCountryCode() {
        return countryCode;
    }

    @DynamoDbAttribute("vendor")
    public String getVendor() {
        return vendor;
    }

    @DynamoDbAttribute("signal_strength_avg")
    public Double getSignalStrengthAvg() {
        return signalStrengthAvg;
    }

    @DynamoDbAttribute("signal_strength_std")
    public Double getSignalStrengthStd() {
        return signalStrengthStd;
    }

    @DynamoDbAttribute("readings_by_hour")
    public Map<String, Integer> getReadingsByHour() {
        return readingsByHour;
    }

    @DynamoDbAttribute("calculation_time")
    public String getCalculationTime() {
        return calculationTime;
    }

    @DynamoDbAttribute("ttl")
    public Long getTtl() {
        return ttl;
    }

    @DynamoDbAttribute("calculation_duration_ms")
    public Integer getCalculationDurationMs() {
        return calculationDurationMs;
    }

    @DynamoDbAttribute("calculation_version")
    public String getCalculationVersion() {
        return calculationVersion;
    }

    @DynamoDbAttribute("error_type")
    public String getErrorType() {
        return errorType;
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
                .build();
    }
} 