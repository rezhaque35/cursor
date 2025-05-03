package com.wifi.positioning.algorithm.selection;

import com.wifi.positioning.algorithm.factor.GeometricQualityFactor;
import com.wifi.positioning.algorithm.factor.SignalDistributionFactor;
import com.wifi.positioning.algorithm.factor.SignalQualityFactor;
import com.wifi.positioning.algorithm.util.GDOPCalculator;
import com.wifi.positioning.dto.WifiScanResult;
import com.wifi.positioning.model.WifiAccessPoint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DefaultContextBuilder Tests")
class DefaultContextBuilderTest {

    @InjectMocks
    private DefaultContextBuilder contextBuilder;

    @Nested
    @DisplayName("Signal Quality Factor Tests")
    class SignalQualityFactorTests {
        
        @Test
        @DisplayName("should return STRONG_SIGNAL for high signal strengths")
        void shouldReturnStrongSignalForHighSignalStrengths() {
            // Arrange
            List<WifiScanResult> scans = createWifiScans(new double[]{-60.0, -65.0, -68.0});
            
            // Act
            SignalQualityFactor factor = contextBuilder.determineSignalQuality(scans);
            
            // Assert
            assertEquals(SignalQualityFactor.STRONG_SIGNAL, factor, 
                    "Expected STRONG_SIGNAL for signals better than -70dBm");
        }
        
        @Test
        @DisplayName("should return MEDIUM_SIGNAL for medium signal strengths")
        void shouldReturnMediumSignalForMediumSignalStrengths() {
            // Arrange
            List<WifiScanResult> scans = createWifiScans(new double[]{-75.0, -80.0, -72.0});
            
            // Act
            SignalQualityFactor factor = contextBuilder.determineSignalQuality(scans);
            
            // Assert
            assertEquals(SignalQualityFactor.MEDIUM_SIGNAL, factor, 
                    "Expected MEDIUM_SIGNAL for signals between -70dBm and -85dBm");
        }
        
        @Test
        @DisplayName("should return WEAK_SIGNAL for low signal strengths")
        void shouldReturnWeakSignalForLowSignalStrengths() {
            // Arrange
            List<WifiScanResult> scans = createWifiScans(new double[]{-86.0, -90.0, -88.0});
            
            // Act
            SignalQualityFactor factor = contextBuilder.determineSignalQuality(scans);
            
            // Assert
            assertEquals(SignalQualityFactor.WEAK_SIGNAL, factor, 
                    "Expected WEAK_SIGNAL for signals worse than -85dBm");
        }
        
        @Test
        @DisplayName("should handle empty scan list")
        void shouldHandleEmptyScanList() {
            // Arrange
            List<WifiScanResult> scans = new ArrayList<>();
            
            // Act
            SignalQualityFactor factor = contextBuilder.determineSignalQuality(scans);
            
            // Assert
            assertEquals(SignalQualityFactor.MEDIUM_SIGNAL, factor, 
                    "Expected MEDIUM_SIGNAL as default for empty scan list");
        }
    }
    
    @Nested
    @DisplayName("Signal Distribution Factor Tests")
    class SignalDistributionFactorTests {
        
        @Test
        @DisplayName("should return UNIFORM_SIGNALS for low standard deviation")
        void shouldReturnUniformSignalsForLowStandardDeviation() {
            // Arrange
            List<WifiScanResult> scans = createWifiScans(new double[]{-75.0, -76.0, -74.0});
            
            // Act
            SignalDistributionFactor factor = contextBuilder.determineSignalDistribution(scans);
            
            // Assert
            assertEquals(SignalDistributionFactor.UNIFORM_SIGNALS, factor, 
                    "Expected UNIFORM_SIGNALS for standard deviation < 3.0");
        }
        
        @Test
        @DisplayName("should return MIXED_SIGNALS for medium standard deviation")
        void shouldReturnMixedSignalsForMediumStandardDeviation() {
            // Arrange
            List<WifiScanResult> scans = createWifiScans(new double[]{-70.0, -80.0, -75.0});
            
            // Act
            SignalDistributionFactor factor = contextBuilder.determineSignalDistribution(scans);
            
            // Assert
            assertEquals(SignalDistributionFactor.MIXED_SIGNALS, factor, 
                    "Expected MIXED_SIGNALS for standard deviation between 3.0 and 10.0");
        }
        
        @Test
        @DisplayName("should return SIGNAL_OUTLIERS for high standard deviation")
        void shouldReturnSignalOutliersForHighStandardDeviation() {
            // Arrange
            List<WifiScanResult> scans = createWifiScans(new double[]{-60.0, -90.0, -75.0});
            
            // Act
            SignalDistributionFactor factor = contextBuilder.determineSignalDistribution(scans);
            
            // Assert
            assertEquals(SignalDistributionFactor.SIGNAL_OUTLIERS, factor, 
                    "Expected SIGNAL_OUTLIERS for standard deviation > 10.0");
        }
        
        @Test
        @DisplayName("should handle empty scan list")
        void shouldHandleEmptyScanList() {
            // Arrange
            List<WifiScanResult> scans = new ArrayList<>();
            
            // Act
            SignalDistributionFactor factor = contextBuilder.determineSignalDistribution(scans);
            
            // Assert
            assertEquals(SignalDistributionFactor.UNIFORM_SIGNALS, factor, 
                    "Expected UNIFORM_SIGNALS as default for empty scan list");
        }
    }
    
    @Nested
    @DisplayName("Geometric Quality Factor Tests")
    class GeometricQualityFactorTests {
        
        @Test
        @DisplayName("should return EXCELLENT_GDOP for triangular AP arrangement")
        void shouldReturnExcellentGdopForTriangularArrangement() {
            // Arrange
            List<WifiScanResult> scans = createWifiScans(new double[]{-70.0, -72.0, -74.0});
            Map<String, WifiAccessPoint> apMap = createAccessPoints(new double[][]{
                {37.7751, -122.4196},      // AP1 at origin
                {37.7751, -122.4186},      // AP2 ~100m east
                {37.7756, -122.4191}       // AP3 ~100m northeast (forms equilateral triangle)
            });
            
            // Act
            GeometricQualityFactor factor = contextBuilder.determineGeometricQuality(scans, apMap);
            
            // Assert
            assertEquals(GeometricQualityFactor.EXCELLENT_GDOP, factor, 
                    "Expected EXCELLENT_GDOP for triangular AP arrangement");
        }
        
        @Test
        @DisplayName("should return POOR_GDOP for collinear APs")
        void shouldReturnPoorGdopForCollinearAPs() {
            // Arrange
            List<WifiScanResult> scans = createWifiScans(new double[]{-70.0, -72.0, -74.0});
            Map<String, WifiAccessPoint> apMap = createAccessPoints(new double[][]{
                {0.0, 0.0},      // AP1 at origin
                {0.001, 0.0},    // AP2 ~100m east
                {0.002, 0.0}     // AP3 ~200m east (collinear with AP1 and AP2)
            });
            
            // Act
            GeometricQualityFactor factor = contextBuilder.determineGeometricQuality(scans, apMap);
            
            // Assert
            assertEquals(GeometricQualityFactor.POOR_GDOP, factor, 
                    "Expected POOR_GDOP for collinear AP arrangement");
        }
        
        @Test
        @DisplayName("should handle insufficient APs")
        void shouldHandleInsufficientAPs() {
            // Arrange
            List<WifiScanResult> scans = createWifiScans(new double[]{-70.0, -72.0});
            Map<String, WifiAccessPoint> apMap = createAccessPoints(new double[][]{
                {0.0, 0.0},      // AP1 at origin
                {0.001, 0.0}     // AP2 ~100m east
            });
            
            // Act
            GeometricQualityFactor factor = contextBuilder.determineGeometricQuality(scans, apMap);
            
            // Assert
            assertEquals(GeometricQualityFactor.POOR_GDOP, factor, 
                    "Expected POOR_GDOP for fewer than 3 APs");
        }
        
        @Test
        @DisplayName("should handle missing APs in map")
        void shouldHandleMissingAPsInMap() {
            // Arrange
            List<WifiScanResult> scans = createWifiScans(new double[]{-70.0, -72.0, -74.0});
            Map<String, WifiAccessPoint> apMap = createAccessPoints(new double[][]{
                {0.0, 0.0},      // AP1 at origin
                {0.001, 0.0}     // AP2 ~100m east
                // AP3 is missing from the map
            });
            
            // Act
            GeometricQualityFactor factor = contextBuilder.determineGeometricQuality(scans, apMap);
            
            // Assert
            assertEquals(GeometricQualityFactor.POOR_GDOP, factor, 
                    "Expected POOR_GDOP when APs are missing from the map");
        }
    }
    
    // Helper methods
    
    private List<WifiScanResult> createWifiScans(double[] signalStrengths) {
        List<WifiScanResult> scans = new ArrayList<>();
        for (int i = 0; i < signalStrengths.length; i++) {
            scans.add(new WifiScanResult(
                    String.format("00:11:22:33:44:%02d", i + 10),
                    signalStrengths[i],
                    2400 + i * 5,
                    "TestSSID"
            ));
        }
        return scans;
    }
    
    private Map<String, WifiAccessPoint> createAccessPoints(double[][] coordinates) {
        Map<String, WifiAccessPoint> apMap = new HashMap<>();
        for (int i = 0; i < coordinates.length; i++) {
            String macAddress = String.format("00:11:22:33:44:%02d", i + 10);
            WifiAccessPoint ap = WifiAccessPoint.builder()
                    .macAddress(macAddress)
                    .latitude(coordinates[i][0])
                    .longitude(coordinates[i][1])
                    .confidence(0.9)
                    .build();
            apMap.put(macAddress, ap);
        }
        return apMap;
    }
} 