package com.epictalenttrust.hr.common;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.Locale;

/** Default time zones for the countries ETT works in. */
public final class TimeZones {

    public static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");
    public static final ZoneId UK = ZoneId.of("Europe/London");

    private TimeZones() {
    }

    public static ZoneId forCountry(String country) {
        if (country == null) {
            return INDIA;
        }
        return switch (country.trim().toLowerCase(Locale.ROOT)) {
            case "united kingdom", "uk", "england", "scotland", "wales", "northern ireland", "great britain" -> UK;
            default -> INDIA;
        };
    }

    public static ZoneId parseOr(String zone, ZoneId fallback) {
        if (zone == null || zone.isBlank()) {
            return fallback;
        }
        try {
            return ZoneId.of(zone);
        } catch (DateTimeException ex) {
            return fallback;
        }
    }

    /** The zone shown next to the candidate's own: GMT for India, IST for the UK. */
    public static ZoneId otherZone(ZoneId candidateZone) {
        return INDIA.equals(candidateZone) ? UK : INDIA;
    }
}
