package com.epictalenttrust.hr.job;

import com.epictalenttrust.hr.common.PgEnumConverter;
import jakarta.persistence.Converter;

public enum JobStatus {
    DRAFT, OPEN, SHORTLIST_CONFIRMED, ON_HOLD, CLOSED, ARCHIVED;

    /** Open for work: CVs and new interviews (on hold and closed jobs take neither). */
    public boolean isLive() {
        return this == OPEN || this == SHORTLIST_CONFIRMED;
    }

    /** CVs can be added while the job is live. */
    public boolean acceptsCvs() {
        return this == OPEN || this == SHORTLIST_CONFIRMED;
    }

    @Converter(autoApply = true)
    public static class DbConverter extends PgEnumConverter<JobStatus> {
        public DbConverter() {
            super(JobStatus.class);
        }
    }
}
