package com.epictalenttrust.hr.screening;

import java.time.LocalDate;
import java.util.List;

/** Facts Indian recruiters ask first, as read from a CV (any may be missing). */
public record KeyFacts(
        Integer noticePeriodDays,
        Boolean servingNotice,
        LocalDate lastWorkingDay,
        Double currentCtcLpa,
        Double expectedCtcLpa,
        String currentLocation,
        List<String> preferredLocations,
        Boolean willingToRelocate,
        String workMode) {

    public static final KeyFacts EMPTY = new KeyFacts(null, null, null, null, null, null, List.of(), null, null);

    public boolean isEmpty() {
        return noticePeriodDays == null && servingNotice == null && lastWorkingDay == null && currentCtcLpa == null
                && expectedCtcLpa == null && currentLocation == null && (preferredLocations == null || preferredLocations.isEmpty())
                && willingToRelocate == null && workMode == null;
    }
}
