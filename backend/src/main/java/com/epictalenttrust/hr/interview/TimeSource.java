package com.epictalenttrust.hr.interview;

import com.epictalenttrust.hr.common.PgEnumConverter;
import jakarta.persistence.Converter;

public enum TimeSource {
    HR_SET, CANDIDATE_PICKED, CENTRE_SLOT;

    @Converter(autoApply = true)
    public static class DbConverter extends PgEnumConverter<TimeSource> {
        public DbConverter() {
            super(TimeSource.class);
        }
    }
}
