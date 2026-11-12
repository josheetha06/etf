package com.epictalenttrust.hr.routing;

/** A point on the map, in decimal degrees. */
public record GeoPoint(double latitude, double longitude) {

    private static final double EARTH_RADIUS_KM = 6371.0088;

    /** Great-circle ("as the crow flies") distance in kilometres, by the haversine formula. */
    public double kmTo(GeoPoint other) {
        double lat1 = Math.toRadians(latitude);
        double lat2 = Math.toRadians(other.latitude);
        double dLat = lat2 - lat1;
        double dLon = Math.toRadians(other.longitude - longitude);
        double h = Math.pow(Math.sin(dLat / 2), 2) + Math.cos(lat1) * Math.cos(lat2) * Math.pow(Math.sin(dLon / 2), 2);
        return 2 * EARTH_RADIUS_KM * Math.asin(Math.min(1, Math.sqrt(h)));
    }
}
