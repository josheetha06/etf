package com.epictalenttrust.hr.routing;

import org.springframework.stereotype.Component;

/**
 * Estimates road distance and journey time from the straight-line distance:
 * <ul>
 *   <li>road distance = straight line × 1.3 (roads wind; 1.2–1.4 is typical in Indian and UK cities)</li>
 *   <li>time: the first 10 km at 25 km/h (city traffic), the next 40 km at 45 km/h, the rest at 60 km/h</li>
 * </ul>
 * Good enough to rank centres. To use a road-routing engine instead, add a TravelEstimator bean marked @Primary.
 */
@Component
public class StraightLineTravelEstimator implements TravelEstimator {

    static final double CIRCUITY = 1.3;

    @Override
    public Travel estimate(GeoPoint from, GeoPoint to) {
        double straight = from.kmTo(to);
        double road = straight * CIRCUITY;
        double hours = Math.min(road, 10) / 25.0
                + Math.max(0, Math.min(road, 50) - 10) / 45.0
                + Math.max(0, road - 50) / 60.0;
        int minutes = (int) Math.round(hours * 60);
        return new Travel(straight, road, Math.max(minutes, road > 0.5 ? 5 : 0));
    }
}
