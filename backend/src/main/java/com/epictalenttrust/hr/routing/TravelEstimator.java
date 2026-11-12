package com.epictalenttrust.hr.routing;

/**
 * How far a candidate has to travel to a centre. The built-in estimate needs no outside service; a road-routing
 * engine (for example OSRM or OpenRouteService on OpenStreetMap data, or a commercial maps API) can replace it
 * by providing another bean of this type.
 */
public interface TravelEstimator {

    record Travel(double straightKm, double roadKm, int minutes) {
    }

    Travel estimate(GeoPoint from, GeoPoint to);
}
