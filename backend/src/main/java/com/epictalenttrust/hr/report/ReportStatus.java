package com.epictalenttrust.hr.report;

import com.epictalenttrust.hr.common.PgEnumConverter;
import jakarta.persistence.Converter;

/** HR only sees RELEASED reports: an ETT reviewer has checked the AI's draft scores. */
public enum ReportStatus {
    DRAFT, IN_REVIEW, RELEASED;

    @Converter(autoApply = true)
    public static class DbConverter extends PgEnumConverter<ReportStatus> {
        public DbConverter() {
            super(ReportStatus.class);
        }
    }
}
