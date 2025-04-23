package com.wifi.positioning.util;

/**
 * Utility class for geohash operations.
 * Geohash is a public domain geocoding system that encodes geographic coordinates
 * (latitude and longitude) into a short string of letters and digits.
 */
public class GeohashUtil {

    private static final String BASE32 = "0123456789bcdefghjkmnpqrstuvwxyz";
    private static final int DEFAULT_PRECISION = 7;

    /**
     * Encodes a latitude and longitude into a geohash string.
     *
     * @param latitude  the latitude to encode
     * @param longitude the longitude to encode
     * @return the geohash string
     */
    public static String encode(double latitude, double longitude) {
        return encode(latitude, longitude, DEFAULT_PRECISION);
    }

    /**
     * Encodes a latitude and longitude into a geohash string with the specified precision.
     *
     * @param latitude  the latitude to encode
     * @param longitude the longitude to encode
     * @param precision the precision of the geohash
     * @return the geohash string
     */
    public static String encode(double latitude, double longitude, int precision) {
        // Input validation
        if (latitude < -90.0 || latitude > 90.0) {
            throw new IllegalArgumentException("Latitude must be between -90.0 and 90.0");
        }
        if (longitude < -180.0 || longitude > 180.0) {
            throw new IllegalArgumentException("Longitude must be between -180.0 and 180.0");
        }
        if (precision <= 0) {
            throw new IllegalArgumentException("Precision must be greater than 0");
        }

        double latMin = -90.0;
        double latMax = 90.0;
        double lonMin = -180.0;
        double lonMax = 180.0;

        StringBuilder geohash = new StringBuilder();
        boolean isEven = true;
        int bit = 0;
        int ch = 0;

        while (geohash.length() < precision) {
            if (isEven) {
                double mid = (lonMin + lonMax) / 2;
                if (longitude >= mid) {
                    ch |= (1 << (4 - bit));
                    lonMin = mid;
                } else {
                    lonMax = mid;
                }
            } else {
                double mid = (latMin + latMax) / 2;
                if (latitude >= mid) {
                    ch |= (1 << (4 - bit));
                    latMin = mid;
                } else {
                    latMax = mid;
                }
            }

            isEven = !isEven;
            bit++;

            if (bit == 5) {
                geohash.append(BASE32.charAt(ch));
                bit = 0;
                ch = 0;
            }
        }

        return geohash.toString();
    }

    /**
     * Decodes a geohash string into a latitude and longitude.
     *
     * @param geohash the geohash string to decode
     * @return an array of [latitude, longitude]
     */
    public static double[] decode(String geohash) {
        if (geohash == null || geohash.isEmpty()) {
            throw new IllegalArgumentException("Geohash cannot be null or empty");
        }

        double latMin = -90.0;
        double latMax = 90.0;
        double lonMin = -180.0;
        double lonMax = 180.0;

        boolean isEven = true;

        for (char c : geohash.toCharArray()) {
            int cd = BASE32.indexOf(c);
            if (cd == -1) {
                throw new IllegalArgumentException("Invalid character in geohash: " + c);
            }

            for (int i = 4; i >= 0; i--) {
                int mask = 1 << i;
                if (isEven) {
                    if ((cd & mask) != 0) {
                        lonMin = (lonMin + lonMax) / 2;
                    } else {
                        lonMax = (lonMin + lonMax) / 2;
                    }
                } else {
                    if ((cd & mask) != 0) {
                        latMin = (latMin + latMax) / 2;
                    } else {
                        latMax = (latMin + latMax) / 2;
                    }
                }
                isEven = !isEven;
            }
        }

        double latitude = (latMin + latMax) / 2;
        double longitude = (lonMin + lonMax) / 2;

        return new double[]{latitude, longitude};
    }

    /**
     * Calculates the Haversine distance between two points on the Earth's surface.
     *
     * @param lat1 latitude of point 1
     * @param lon1 longitude of point 1
     * @param lat2 latitude of point 2
     * @param lon2 longitude of point 2
     * @return the distance in kilometers
     */
    public static double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371; // Earth's radius in kilometers

        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);

        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return R * c;
    }
} 