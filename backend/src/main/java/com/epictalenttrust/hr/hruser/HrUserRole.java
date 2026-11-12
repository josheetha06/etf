package com.epictalenttrust.hr.hruser;

import com.epictalenttrust.hr.common.PgEnumConverter;
import jakarta.persistence.Converter;

public enum HrUserRole {
    ADMIN, RECRUITER, HIRING_MANAGER,
    /** No longer used: replaced by the panel login (kept so older data still reads). */
    INTERVIEWER,
    /** The generic panel login: employee ID + interview code, reviews only. */
    PANEL;

    /** Jobs, CVs, scheduling and offers. */
    public boolean canManageHiring() {
        return this == ADMIN || this == RECRUITER;
    }

    /** Shortlist and round decisions. */
    public boolean canDecide() {
        return this == ADMIN || this == RECRUITER || this == HIRING_MANAGER;
    }

    /** Sees every job in the company. Others see their own jobs and any they are covering. */
    public boolean seesAllJobs() {
        return this == ADMIN;
    }

    @Converter(autoApply = true)
    public static class DbConverter extends PgEnumConverter<HrUserRole> {
        public DbConverter() {
            super(HrUserRole.class);
        }
    }
}
