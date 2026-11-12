package com.epictalenttrust.hr.report;

import com.epictalenttrust.hr.common.PgEnumConverter;
import jakarta.persistence.Converter;

public enum IntegrityStatus {
    CLEAR, FLAGGED, UNDER_REVIEW;

    @Converter(autoApply = true)
    public static class DbConverter extends PgEnumConverter<IntegrityStatus> {
        public DbConverter() {
            super(IntegrityStatus.class);
        }
    }
}
