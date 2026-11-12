package com.epictalenttrust.hr.candidate;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * A candidate's location for lists and pages.
 *
 * @param text   what the CV, the AI interviewer or HR said, as written
 * @param label  where it is on the map: "Sivakasi, Virudhunagar" (null if it couldn't be placed)
 * @param source cv, ai_interview or hr
 * @param status known (placed on the map), unplaced (written but not found), ask_ai (not known yet; the AI
 *               interviewer will ask) or missing
 */
public record LocationSummary(String text, String label, String source, String status) {

    /** Reads the location columns of the pipeline query (see PipelineQueries). */
    public static LocationSummary from(ResultSet rs) throws SQLException {
        String text = rs.getString("location_text");
        if (text == null) {
            return new LocationSummary(null, null, null, rs.getBoolean("ai_round_ahead") ? "ask_ai" : "missing");
        }
        if (!rs.getBoolean("location_placed")) {
            return new LocationSummary(text, null, rs.getString("location_source"), "unplaced");
        }
        String place = rs.getString("location_place");
        String district = rs.getString("location_district");
        String label = district == null || district.equalsIgnoreCase(place) ? place : place + ", " + district;
        return new LocationSummary(text, label, rs.getString("location_source"), "known");
    }
}
