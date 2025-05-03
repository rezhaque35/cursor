package com.wifi.positioning.repository.impl;

import com.wifi.positioning.model.WifiAccessPoint;
import com.wifi.positioning.repository.TestWifiAccessPointRepository;
import com.wifi.positioning.repository.WifiAccessPointRepository;
import org.springframework.context.annotation.Profile;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory implementation of the WifiAccessPointRepository interface for testing.
 * This implementation does not require any external dependencies like DynamoDB.
 */
@Profile("test")
public class InMemoryWifiAccessPointRepository implements TestWifiAccessPointRepository {
    
    // Map of MAC address to list of access points (for different versions)
    private final Map<String, List<WifiAccessPoint>> dataStore = new ConcurrentHashMap<>();
    
    @Override
    public List<WifiAccessPoint> findByMacAddress(String macAddress) {
        return dataStore.getOrDefault(macAddress, Collections.emptyList());
    }
    
    /**
     * Find an access point by MAC address and version
     * 
     * @param macAddress MAC address of the access point
     * @param version Version identifier
     * @return Optional containing the access point if found
     */
    @Override
    public Optional<WifiAccessPoint> findByMacAddressAndVersion(String macAddress, String version) {
        if (macAddress == null || version == null) {
            return Optional.empty();
        }
        
        List<WifiAccessPoint> accessPoints = dataStore.getOrDefault(macAddress, Collections.emptyList());
        return accessPoints.stream()
                .filter(ap -> version.equals(ap.getVersion()))
                .findFirst();
    }
    
    /**
     * Save an access point to the repository
     * 
     * @param accessPoint The access point to save
     */
    @Override
    public void save(WifiAccessPoint accessPoint) {
        if (accessPoint == null || accessPoint.getMacAddress() == null) {
            return;
        }
        
        String macAddress = accessPoint.getMacAddress();
        List<WifiAccessPoint> existing = dataStore.getOrDefault(macAddress, new ArrayList<>());
        
        // Remove any existing version with the same version identifier
        if (accessPoint.getVersion() != null) {
            existing.removeIf(ap -> accessPoint.getVersion().equals(ap.getVersion()));
        }
        
        // Add the new access point
        existing.add(accessPoint);
        dataStore.put(macAddress, existing);
    }
    
    /**
     * Batch save multiple access points
     * 
     * @param accessPoints List of access points to save
     */
    @Override
    public <T> void batchSave(List<T> accessPoints) {
        if (accessPoints == null) {
            return;
        }
        
        for (Object obj : accessPoints) {
            if (obj instanceof WifiAccessPoint) {
                save((WifiAccessPoint) obj);
            }
        }
    }
    
    /**
     * Delete an access point by MAC address and version
     * 
     * @param macAddress MAC address of the access point
     * @param version Version identifier
     */
    @Override
    public void delete(String macAddress, String version) {
        if (macAddress == null || version == null) {
            return;
        }
        
        List<WifiAccessPoint> existing = dataStore.getOrDefault(macAddress, Collections.emptyList());
        existing.removeIf(ap -> version.equals(ap.getVersion()));
        
        if (existing.isEmpty()) {
            dataStore.remove(macAddress);
        } else {
            dataStore.put(macAddress, existing);
        }
    }
    
    /**
     * Find access points by geohash prefix
     * 
     * @param geohash The geohash prefix to search for
     * @return List of access points matching the geohash prefix
     */
    @Override
    public List<WifiAccessPoint> findByGeohashStartingWith(String geohash) {
        if (geohash == null || geohash.isEmpty()) {
            return Collections.emptyList();
        }
        
        return dataStore.values().stream()
                .flatMap(List::stream)
                .filter(ap -> ap.getGeohash() != null && ap.getGeohash().startsWith(geohash))
                .collect(Collectors.toList());
    }
    
    /**
     * Find access points near a given coordinate, within a given distance
     * 
     * @param latitude Latitude coordinate
     * @param longitude Longitude coordinate
     * @param limit Maximum number of access points to return
     * @return List of access points near the coordinate
     */
    @Override
    public List<WifiAccessPoint> findNearestByCoordinates(double latitude, double longitude, int limit) {
        // This is a simplified implementation that just sorts by distance
        return dataStore.values().stream()
                .flatMap(List::stream)
                .filter(ap -> ap.getLatitude() != null && ap.getLongitude() != null)
                .sorted(Comparator.comparingDouble(ap -> 
                    calculateDistance(latitude, longitude, ap.getLatitude(), ap.getLongitude())))
                .limit(limit)
                .collect(Collectors.toList());
    }
    
    /**
     * Simple distance calculation using the Haversine formula
     */
    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371; // Radius of the earth in km
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c * 1000; // convert to meters
    }
    
    /**
     * Helper method for tests to add access points to the in-memory store
     */
    public void addAccessPoint(WifiAccessPoint accessPoint) {
        if (accessPoint == null || accessPoint.getMacAddress() == null) {
            return;
        }
        
        dataStore.computeIfAbsent(accessPoint.getMacAddress(), k -> new ArrayList<>())
                .add(accessPoint);
    }
    
    /**
     * Helper method for tests to remove all access points
     */
    public void clearAll() {
        dataStore.clear();
    }
    
    /**
     * Loads test data for the "Single AP - Proximity Detection" scenario
     */
    public void loadProximityDetectionScenario() {
        WifiAccessPoint ap = WifiAccessPoint.builder()
                .macAddress("00:11:22:33:44:01")
                .version("20240411-120000")
                .latitude(37.7749)
                .longitude(-122.4194)
                .altitude(10.5)
                .horizontalAccuracy(50.0)
                .verticalAccuracy(8.0)
                .confidence(0.65)
                .bestMethod("proximity")
                .methodsUsed(new String[]{"proximity"})
                .sampleCount(1)
                .signalStrengthAvg(-65.0)
                .signalStrengthStd(0.0)
                .ssid("SingleAP_Test")
                .frequency(2437)
                .countryCode("US")
                .vendor("Cisco")
                .geohash("9q8yyk")
                .build();
        
        addAccessPoint(ap);
    }
    
    /**
     * Loads test data for the "Two APs - RSSI Ratio Method" scenario
     */
    public void loadRssiRatioScenario() {
        WifiAccessPoint ap = WifiAccessPoint.builder()
                .macAddress("00:11:22:33:44:02")
                .version("20240411-120100")
                .latitude(37.7750)
                .longitude(-122.4195)
                .altitude(12.5)
                .horizontalAccuracy(25.0)
                .verticalAccuracy(5.0)
                .confidence(0.78)
                .bestMethod("rssi_ratio")
                .methodsUsed(new String[]{"rssi_ratio", "weighted_centroid"})
                .sampleCount(15)
                .signalStrengthAvg(-68.5)
                .signalStrengthStd(2.1)
                .ssid("DualAP_Test")
                .frequency(5180)
                .countryCode("US")
                .vendor("Aruba")
                .geohash("9q8yyk")
                .build();
        
        addAccessPoint(ap);
    }
    
    /**
     * Loads test data for the "Three APs - Trilateration" scenario
     */
    public void loadTrilaterationScenario() {
        WifiAccessPoint ap = WifiAccessPoint.builder()
                .macAddress("00:11:22:33:44:03")
                .version("20240411-120200")
                .latitude(37.7751)
                .longitude(-122.4196)
                .altitude(15.0)
                .horizontalAccuracy(8.5)
                .verticalAccuracy(3.0)
                .confidence(0.92)
                .bestMethod("trilateration")
                .methodsUsed(new String[]{"trilateration", "weighted_centroid", "rssi_ratio"})
                .sampleCount(45)
                .signalStrengthAvg(-62.3)
                .signalStrengthStd(1.8)
                .ssid("TriAP_Test")
                .frequency(2462)
                .countryCode("US")
                .vendor("Ubiquiti")
                .geohash("9q8yyk")
                .build();
        
        addAccessPoint(ap);
    }
    
    /**
     * Loads test data for the "Weak Signals" scenario
     */
    public void loadWeakSignalsScenario() {
        WifiAccessPoint ap = WifiAccessPoint.builder()
                .macAddress("00:11:22:33:44:05")
                .version("20240411-120400")
                .latitude(37.7753)
                .longitude(-122.4198)
                .altitude(20.0)
                .horizontalAccuracy(35.0)
                .verticalAccuracy(12.0)
                .confidence(0.45)
                .bestMethod("maximum_likelihood")
                .methodsUsed(new String[]{"maximum_likelihood", "weighted_centroid", "rssi_ratio"})
                .sampleCount(30)
                .signalStrengthAvg(-85.5)
                .signalStrengthStd(5.2)
                .ssid("WeakSignal_Test")
                .frequency(2412)
                .countryCode("US")
                .vendor("HPE-Aruba")
                .geohash("9q8yyk")
                .build();
        
        addAccessPoint(ap);
    }
    
    /**
     * Loads multiple test scenarios based on the test data script
     */
    public void loadAllTestScenarios() {
        clearAll();
        loadProximityDetectionScenario();
        loadRssiRatioScenario();
        loadTrilaterationScenario();
        loadWeakSignalsScenario();
        
        // Add collinear APs
        for (int i = 6; i <= 10; i++) {
            String macSuffix = String.format("%02d", i);
            double latitude = 37.7754 + (i-6)*0.0001;
            double longitude = -122.4194;
            double altitude = 15.0 + (i-6)*2;
            double signalStrength = -70.0 + (i-6)*2;
            int sampleCount = 25 + (i-6)*5;
            
            WifiAccessPoint ap = WifiAccessPoint.builder()
                    .macAddress("00:11:22:33:44:" + macSuffix)
                    .version("20240411-1205" + (i-6) + "0")
                    .latitude(latitude)
                    .longitude(longitude)
                    .altitude(altitude)
                    .horizontalAccuracy(18.5)
                    .verticalAccuracy(6.0)
                    .confidence(0.72)
                    .bestMethod("weighted_centroid")
                    .methodsUsed(new String[]{"weighted_centroid", "rssi_ratio"})
                    .sampleCount(sampleCount)
                    .signalStrengthAvg(signalStrength)
                    .signalStrengthStd(2.8)
                    .ssid("Collinear_Test_" + i)
                    .frequency(2437)
                    .countryCode("US")
                    .vendor("Ruckus")
                    .geohash("9q8yyk")
                    .build();
            
            addAccessPoint(ap);
        }
    }
} 