package com.wifi.positioning.algorithm.util;

import com.wifi.positioning.model.WifiAccessPoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GDOPCalculatorTest {

    @Nested
    @DisplayName("GDOP Calculation Tests")
    class GDOPTests {
        
        @Test
        @DisplayName("should calculate high GDOP for collinear APs")
        void shouldCalculateHighGDOPForCollinearAPs() {
            List<WifiAccessPoint> aps = Arrays.asList(
                createAP(1.0, 1.0, 0.0),
                createAP(1.0, 2.0, 0.0),
                createAP(1.0, 3.0, 0.0)
            );
            
            double gdop = GDOPCalculator.calculate(aps);
            assertTrue(gdop > 5.0, "GDOP should be high for collinear APs");
        }
        
        @Test
        @DisplayName("should calculate low GDOP for well-distributed APs")
        void shouldCalculateLowGDOPForWellDistributedAPs() {
            List<WifiAccessPoint> aps = Arrays.asList(
                createAP(1.0, 1.0, 0.0),
                createAP(2.0, 2.0, 0.0),
                createAP(1.0, 2.0, 0.0),
                createAP(2.0, 1.0, 0.0)
            );
            
            double gdop = GDOPCalculator.calculate(aps);
            assertTrue(gdop < 3.0, "GDOP should be low for well-distributed APs");
        }
        
        @Test
        @DisplayName("should calculate infinite GDOP for less than 3 APs")
        void shouldCalculateInfiniteGDOPForInsufficientAPs() {
            List<WifiAccessPoint> aps = Arrays.asList(
                createAP(1.0, 1.0, 0.0),
                createAP(2.0, 2.0, 0.0)
            );
            
            double gdop = GDOPCalculator.calculate(aps);
            assertEquals(Double.POSITIVE_INFINITY, gdop, "GDOP should be infinite for insufficient APs");
        }
        
        @Test
        @DisplayName("should calculate 3D GDOP correctly")
        void shouldCalculate3DGDOPCorrectly() {
            List<WifiAccessPoint> aps = Arrays.asList(
                createAP(1.0, 1.0, 0.0),
                createAP(2.0, 2.0, 10.0),
                createAP(1.0, 2.0, 5.0),
                createAP(2.0, 1.0, 15.0)
            );
            
            double gdop = GDOPCalculator.calculate(aps);
            assertTrue(gdop > 0 && gdop < 5.0, "3D GDOP should be reasonable for well-distributed APs");
        }
    }
    
    private WifiAccessPoint createAP(double lat, double lon, double alt) {
        return WifiAccessPoint.builder()
            .macAddress("test-mac")
            .latitude(lat)
            .longitude(lon)
            .altitude(alt)
            .horizontalAccuracy(5.0)
            .confidence(0.8)
            .build();
    }
} 