package com.wifi.positioning.repository;

import com.wifi.positioning.config.TestApplicationConfig;
import com.wifi.positioning.model.WifiAccessPoint;
import com.wifi.positioning.repository.impl.InMemoryWifiAccessPointRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = TestApplicationConfig.class)
@ActiveProfiles("test")
class WifiAccessPointRepositoryTest {

    @Autowired
    private WifiAccessPointRepository repository;

    private WifiAccessPoint testAccessPoint;
    private InMemoryWifiAccessPointRepository inMemoryRepository;

    @BeforeEach
    void setUp() {
        // Cast to access helper methods
        inMemoryRepository = (InMemoryWifiAccessPointRepository) repository;
        inMemoryRepository.clearAll();
        
        testAccessPoint = WifiAccessPoint.builder()
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
    }

    @Test
    void findByMacAddress_shouldReturnEmptyList_whenNoAccessPointExists() {
        // Act
        List<WifiAccessPoint> found = repository.findByMacAddress("non:existent:mac");
        
        // Assert
        assertTrue(found.isEmpty());
    }

    @Test
    void findByMacAddress_shouldReturnAccessPoint_whenSingleVersionExists() {
        // Arrange
        inMemoryRepository.addAccessPoint(testAccessPoint);
        
        // Act
        List<WifiAccessPoint> found = repository.findByMacAddress(testAccessPoint.getMacAddress());
        
        // Assert
        assertEquals(1, found.size());
        assertEquals(testAccessPoint.getMacAddress(), found.get(0).getMacAddress());
        assertEquals(testAccessPoint.getVersion(), found.get(0).getVersion());
        assertEquals(testAccessPoint.getLatitude(), found.get(0).getLatitude());
        assertEquals(testAccessPoint.getLongitude(), found.get(0).getLongitude());
    }

    @Test
    void findByMacAddress_shouldReturnMultipleVersions_whenMultipleVersionsExist() {
        // Arrange
        inMemoryRepository.addAccessPoint(testAccessPoint);
        
        // Create a second version
        WifiAccessPoint secondVersion = WifiAccessPoint.builder()
                .macAddress(testAccessPoint.getMacAddress())
                .version("test-2.0")
                .latitude(37.7750)
                .longitude(-122.4195)
                .confidence(0.90)
                .bestMethod("test-method-updated")
                .methodsUsed(new String[]{"test-method-1", "test-method-3"})
                .build();
        
        inMemoryRepository.addAccessPoint(secondVersion);
        
        // Act
        List<WifiAccessPoint> found = repository.findByMacAddress(testAccessPoint.getMacAddress());
        
        // Assert
        assertEquals(2, found.size());
        assertTrue(found.stream().anyMatch(ap -> ap.getVersion().equals("test-1.0")));
        assertTrue(found.stream().anyMatch(ap -> ap.getVersion().equals("test-2.0")));
    }
    
    @Test
    void findByMacAddress_shouldReturnCorrectData_forProximityDetectionScenario() {
        // Arrange
        inMemoryRepository.loadProximityDetectionScenario();
        
        // Act
        List<WifiAccessPoint> found = repository.findByMacAddress("00:11:22:33:44:01");
        
        // Assert
        assertFalse(found.isEmpty());
        assertEquals(1, found.size());
        WifiAccessPoint ap = found.get(0);
        assertEquals("00:11:22:33:44:01", ap.getMacAddress());
        assertEquals("20240411-120000", ap.getVersion());
        assertEquals("proximity", ap.getBestMethod());
        assertEquals(0.65, ap.getConfidence());
        assertEquals(-65.0, ap.getSignalStrengthAvg());
        assertEquals("SingleAP_Test", ap.getSsid());
    }
    
    @Test
    void findByMacAddress_shouldReturnCorrectData_forRssiRatioScenario() {
        // Arrange
        inMemoryRepository.loadRssiRatioScenario();
        
        // Act
        List<WifiAccessPoint> found = repository.findByMacAddress("00:11:22:33:44:02");
        
        // Assert
        assertFalse(found.isEmpty());
        assertEquals(1, found.size());
        WifiAccessPoint ap = found.get(0);
        assertEquals("00:11:22:33:44:02", ap.getMacAddress());
        assertEquals("rssi_ratio", ap.getBestMethod());
        assertEquals(0.78, ap.getConfidence());
        assertEquals(2, ap.getMethodsUsed().length);
        assertTrue(List.of(ap.getMethodsUsed()).contains("rssi_ratio"));
        assertTrue(List.of(ap.getMethodsUsed()).contains("weighted_centroid"));
    }
    
    @Test
    void findByMacAddress_shouldReturnCorrectData_forWeakSignalsScenario() {
        // Arrange
        inMemoryRepository.loadWeakSignalsScenario();
        
        // Act
        List<WifiAccessPoint> found = repository.findByMacAddress("00:11:22:33:44:05");
        
        // Assert
        assertFalse(found.isEmpty());
        assertEquals(1, found.size());
        WifiAccessPoint ap = found.get(0);
        assertEquals("00:11:22:33:44:05", ap.getMacAddress());
        assertEquals("maximum_likelihood", ap.getBestMethod());
        assertEquals(0.45, ap.getConfidence());
        assertEquals(-85.5, ap.getSignalStrengthAvg());
        assertTrue(ap.getSignalStrengthAvg() < -80.0, "Should be a weak signal (less than -80 dBm)");
    }
    
    @Test
    void findByMacAddress_shouldReturnCorrectData_forCollinearApsScenario() {
        // Arrange - Load all data including collinear APs
        inMemoryRepository.loadAllTestScenarios();
        
        // Act - Check for AP #8 which is part of the collinear set
        List<WifiAccessPoint> found = repository.findByMacAddress("00:11:22:33:44:08");
        
        // Assert
        assertFalse(found.isEmpty());
        assertEquals(1, found.size());
        WifiAccessPoint ap = found.get(0);
        assertEquals("00:11:22:33:44:08", ap.getMacAddress());
        assertEquals("weighted_centroid", ap.getBestMethod());
        // Verify it's in the expected collinear pattern along latitude
        assertEquals(37.7754 + 2*0.0001, ap.getLatitude(), 0.0001);
        assertEquals(-122.4194, ap.getLongitude(), 0.0001);
    }
    
    @Test
    void findByMacAddress_shouldReturnAllScenarioData_whenAllScenariosAreLoaded() {
        // Arrange
        inMemoryRepository.loadAllTestScenarios();
        
        // Assert multiple scenarios are loaded
        assertFalse(repository.findByMacAddress("00:11:22:33:44:01").isEmpty()); // Proximity
        assertFalse(repository.findByMacAddress("00:11:22:33:44:02").isEmpty()); // RSSI Ratio
        assertFalse(repository.findByMacAddress("00:11:22:33:44:03").isEmpty()); // Trilateration
        assertFalse(repository.findByMacAddress("00:11:22:33:44:05").isEmpty()); // Weak Signal
        
        // Check collinear APs (should have 5 of them)
        int collinearCount = 0;
        for (int i = 6; i <= 10; i++) {
            String macAddress = String.format("00:11:22:33:44:%02d", i);
            List<WifiAccessPoint> aps = repository.findByMacAddress(macAddress);
            if (!aps.isEmpty()) {
                collinearCount++;
            }
        }
        assertEquals(5, collinearCount, "Should have 5 collinear APs");
    }
} 