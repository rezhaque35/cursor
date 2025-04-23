package com.wifi.positioning.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GeohashUtilTest {

    @Test
    void encodeValidCoordinates() {
        // San Francisco coordinates
        String geohash = GeohashUtil.encode(37.7749, -122.4194);
        assertEquals("9q8yyk", geohash.substring(0, 6));
    }

    @Test
    void encodeWithCustomPrecision() {
        String geohash3 = GeohashUtil.encode(37.7749, -122.4194, 3);
        assertEquals("9q8", geohash3);

        String geohash5 = GeohashUtil.encode(37.7749, -122.4194, 5);
        assertEquals("9q8yy", geohash5);
    }

    @Test
    void encodeDecode() {
        double lat = 37.7749;
        double lon = -122.4194;
        String geohash = GeohashUtil.encode(lat, lon);
        
        double[] decoded = GeohashUtil.decode(geohash);
        
        assertEquals(lat, decoded[0], 0.001);  // Precision loss is expected, test within 0.001
        assertEquals(lon, decoded[1], 0.001);
    }

    @Test
    void invalidLatitude() {
        assertThrows(IllegalArgumentException.class, () -> GeohashUtil.encode(91.0, -122.4194));
        assertThrows(IllegalArgumentException.class, () -> GeohashUtil.encode(-91.0, -122.4194));
    }

    @Test
    void invalidLongitude() {
        assertThrows(IllegalArgumentException.class, () -> GeohashUtil.encode(37.7749, 181.0));
        assertThrows(IllegalArgumentException.class, () -> GeohashUtil.encode(37.7749, -181.0));
    }

    @Test
    void invalidPrecision() {
        assertThrows(IllegalArgumentException.class, () -> GeohashUtil.encode(37.7749, -122.4194, 0));
        assertThrows(IllegalArgumentException.class, () -> GeohashUtil.encode(37.7749, -122.4194, -1));
    }

    @Test
    void calculateDistance() {
        // San Francisco to Los Angeles (about 559 km)
        double distance = GeohashUtil.calculateDistance(37.7749, -122.4194, 34.0522, -118.2437);
        assertTrue(distance > 550 && distance < 570);
        
        // Same point should have zero distance
        assertEquals(0, GeohashUtil.calculateDistance(37.7749, -122.4194, 37.7749, -122.4194));
        
        // Points 1km apart
        double distance1km = GeohashUtil.calculateDistance(37.7749, -122.4194, 37.7839, -122.4194);
        assertTrue(distance1km > 0.9 && distance1km < 1.1);
    }
} 