package com.epictalenttrust.hr.interview;

import com.epictalenttrust.hr.common.PgEnumConverter;
import jakarta.persistence.Converter;

/** DRAFT means HR has set a time but the candidate hasn't been told yet. */
public enum InterviewStatus {
    DRAFT, INVITED, CONFIRMED, RESCHEDULE_REQUESTED, COMPLETED, CANCELLED, NO_SHOW;

    public boolean isUpcoming() {
        return this == INVITED || this == CONFIRMED || this == RESCHEDULE_REQUESTED;
    }

    @Converter(autoApply = true)
    public static class DbConverter extends PgEnumConverter<InterviewStatus> {
        public DbConverter() {
            super(InterviewStatus.class);
        }
    }
}
