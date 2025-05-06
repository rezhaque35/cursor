package com.wifi.positioning.algorithm.factor;

import com.wifi.positioning.dto.Position;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class GeometricQualityFactorTest {

    @Test
    @DisplayName("fromGDOP should return correct factor based on GDOP value")
    public void testFromGDOP() {
        assertEquals(GeometricQualityFactor.EXCELLENT_GDOP, GeometricQualityFactor.fromGDOP(1.5));
        assertEquals(GeometricQualityFactor.GOOD_GDOP, GeometricQualityFactor.fromGDOP(3.0));
        assertEquals(GeometricQualityFactor.FAIR_GDOP, GeometricQualityFactor.fromGDOP(5.0));
        assertEquals(GeometricQualityFactor.POOR_GDOP, GeometricQualityFactor.fromGDOP(7.0));
    }

    @Test
    @DisplayName("isCollinear should return false when there are fewer than 3 positions")
    public void testIsCollinearWithFewerThanThreePositions() {
        // Single position
        List<Position> singlePosition = Arrays.asList(
            Position.of(51.5074, -0.1278)
        );
        assertFalse(GeometricQualityFactor.isCollinear(singlePosition));
        
        // Two positions
        List<Position> twoPositions = Arrays.asList(
            Position.of(51.5074, -0.1278),
            Position.of(48.8566, 2.3522)
        );
        assertFalse(GeometricQualityFactor.isCollinear(twoPositions));
    }
    
    @Test
    @DisplayName("isCollinear should return true for positions in a horizontal line")
    public void testIsCollinearWithHorizontalLine() {
        List<Position> horizontalLine = Arrays.asList(
            Position.of(40.0, -74.0),
            Position.of(40.0, -75.0),
            Position.of(40.0, -76.0),
            Position.of(40.0, -77.0)
        );
        
        assertTrue(GeometricQualityFactor.isCollinear(horizontalLine));
    }
    
    @Test
    @DisplayName("isCollinear should return true for positions in a vertical line")
    public void testIsCollinearWithVerticalLine() {
        List<Position> verticalLine = Arrays.asList(
            Position.of(40.0, -74.0),
            Position.of(41.0, -74.0),
            Position.of(42.0, -74.0),
            Position.of(43.0, -74.0)
        );
        
        assertTrue(GeometricQualityFactor.isCollinear(verticalLine));
    }
    
    @Test
    @DisplayName("isCollinear should return true for positions in a diagonal line")
    public void testIsCollinearWithDiagonalLine() {
        // Create a more precise diagonal line
        List<Position> diagonalLine = Arrays.asList(
            Position.of(40.0, -74.0),
            Position.of(40.1, -73.9),
            Position.of(40.2, -73.8),
            Position.of(40.3, -73.7)
        );
        
        assertTrue(GeometricQualityFactor.isCollinear(diagonalLine));
    }
    
    @Test
    @DisplayName("isCollinear should return false for positions not in a line")
    public void testIsCollinearWithNonCollinearPositions() {
        List<Position> nonCollinearPositions = Arrays.asList(
            Position.of(40.0, -74.0),  // New York
            Position.of(34.0, -118.0), // Los Angeles
            Position.of(41.9, -87.6),  // Chicago
            Position.of(39.1, -94.6)   // Kansas City
        );
        
        assertFalse(GeometricQualityFactor.isCollinear(nonCollinearPositions));
    }
    
    @Test
    @DisplayName("isCollinear should return false for positions forming a triangle")
    public void testIsCollinearWithTrianglePositions() {
        List<Position> trianglePositions = Arrays.asList(
            Position.of(0.0, 0.0),
            Position.of(0.0, 1.0),
            Position.of(1.0, 0.0)
        );
        
        assertFalse(GeometricQualityFactor.isCollinear(trianglePositions));
    }
    
    @Test
    @DisplayName("isCollinear should return true for nearly collinear positions")
    public void testIsCollinearWithNearlyCollinearPositions() {
        // Small variations from a perfect line should still be detected as collinear
        List<Position> nearlyCollinearPositions = Arrays.asList(
            Position.of(40.0, -74.0),
            Position.of(40.001, -75.0), // Tiny deviation
            Position.of(40.002, -76.0), // Tiny deviation
            Position.of(40.0, -77.0)
        );
        
        assertTrue(GeometricQualityFactor.isCollinear(nearlyCollinearPositions));
    }
} 