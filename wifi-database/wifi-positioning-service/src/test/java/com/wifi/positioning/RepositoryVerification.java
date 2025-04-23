package com.wifi.positioning;

import com.wifi.positioning.model.WifiAccessPoint;
import com.wifi.positioning.repository.impl.InMemoryWifiAccessPointRepository;

import java.util.List;

/**
 * Simple standalone verification program for the InMemoryWifiAccessPointRepository
 * implementation. This bypasses the need for Spring and testing frameworks.
 */
public class RepositoryVerification {

    public static void main(String[] args) {
        System.out.println("Verifying InMemoryWifiAccessPointRepository implementation...");
        
        // Create repository instance
        InMemoryWifiAccessPointRepository repository = new InMemoryWifiAccessPointRepository();
        
        // Create test access point
        WifiAccessPoint testAP = WifiAccessPoint.builder()
                .macAddress("00:11:22:33:44:55")
                .version("test-1.0")
                .latitude(37.7749)
                .longitude(-122.4194)
                .altitude(10.0)
                .horizontalAccuracy(5.0)
                .verticalAccuracy(2.0)
                .confidence(0.85)
                .bestMethod("test-method")
                .methodsUsed(new String[]{"test-method-1", "test-method-2"})
                .sampleCount(10)
                .signalStrengthAvg(-65.0)
                .signalStrengthStd(3.0)
                .ssid("test-ssid")
                .frequency(2437)
                .channel(6)
                .countryCode("US")
                .vendor("test-vendor")
                .build();
        
        // Test basic operations
        verifyAddAndFind(repository, testAP);
        
        // Test scenario data loading
        verifyScenarioLoading(repository);
        
        System.out.println("\nAll verifications completed!");
    }
    
    private static void verifyAddAndFind(InMemoryWifiAccessPointRepository repository, WifiAccessPoint testAP) {
        System.out.println("\n== Verifying basic add and find operations ==");
        
        // Clear any previous data
        repository.clearAll();
        
        // Verify empty results when nothing added
        List<WifiAccessPoint> emptyResults = repository.findByMacAddress("00:11:22:33:44:55");
        System.out.println("Empty results: " + (emptyResults.isEmpty() ? "PASS" : "FAIL"));
        
        // Add test access point
        repository.addAccessPoint(testAP);
        
        // Find by MAC address
        List<WifiAccessPoint> results = repository.findByMacAddress(testAP.getMacAddress());
        
        // Verify results
        boolean singleResult = results.size() == 1;
        System.out.println("Single result: " + (singleResult ? "PASS" : "FAIL"));
        
        boolean correctMac = results.get(0).getMacAddress().equals(testAP.getMacAddress());
        System.out.println("Correct MAC address: " + (correctMac ? "PASS" : "FAIL"));
        
        boolean correctVersion = results.get(0).getVersion().equals(testAP.getVersion());
        System.out.println("Correct version: " + (correctVersion ? "PASS" : "FAIL"));
        
        boolean correctSSID = results.get(0).getSsid().equals(testAP.getSsid());
        System.out.println("Correct SSID: " + (correctSSID ? "PASS" : "FAIL"));
    }
    
    private static void verifyScenarioLoading(InMemoryWifiAccessPointRepository repository) {
        System.out.println("\n== Verifying scenario data loading ==");
        
        // Clear any previous data
        repository.clearAll();
        
        // Load proximity detection scenario
        repository.loadProximityDetectionScenario();
        
        // Verify proximity detection scenario data
        List<WifiAccessPoint> proximityAP = repository.findByMacAddress("00:11:22:33:44:01");
        boolean proximityLoaded = !proximityAP.isEmpty();
        System.out.println("Proximity scenario loaded: " + (proximityLoaded ? "PASS" : "FAIL"));
        
        if (proximityLoaded) {
            boolean correctMethod = proximityAP.get(0).getBestMethod().equals("proximity");
            System.out.println("Proximity method correct: " + (correctMethod ? "PASS" : "FAIL"));
            
            boolean correctConfidence = proximityAP.get(0).getConfidence() == 0.65;
            System.out.println("Proximity confidence correct: " + (correctConfidence ? "PASS" : "FAIL"));
        }
        
        // Load all scenarios
        repository.loadAllTestScenarios();
        
        // Check number of collinear APs (should be 5)
        int collinearCount = 0;
        for (int i = 6; i <= 10; i++) {
            String macAddress = String.format("00:11:22:33:44:%02d", i);
            List<WifiAccessPoint> aps = repository.findByMacAddress(macAddress);
            if (!aps.isEmpty()) {
                collinearCount++;
            }
        }
        
        System.out.println("Collinear APs loaded: " + (collinearCount == 5 ? "PASS" : "FAIL (" + collinearCount + "/5)"));
        
        // Check weak signal scenario
        List<WifiAccessPoint> weakSignalAP = repository.findByMacAddress("00:11:22:33:44:05");
        boolean weakSignalLoaded = !weakSignalAP.isEmpty();
        System.out.println("Weak signal scenario loaded: " + (weakSignalLoaded ? "PASS" : "FAIL"));
        
        if (weakSignalLoaded) {
            boolean weakSignalStrength = weakSignalAP.get(0).getSignalStrengthAvg() < -80.0;
            System.out.println("Weak signal strength correct: " + (weakSignalStrength ? "PASS" : "FAIL"));
        }
    }
} 