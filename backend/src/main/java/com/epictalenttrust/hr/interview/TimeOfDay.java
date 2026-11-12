package com.epictalenttrust.hr.interview;

import com.epictalenttrust.hr.common.PgEnumConverter;
import jakarta.persistence.Converter;

public enum TimeOfDay {
    MORNING, AFTERNOON, EVENING, ANY;

    @Converter(autoApply = true)
    public static class DbConverter extends PgEnumConverter<TimeOfDay> {
        public DbConverter() {
            super(TimeOfDay.class);
        }
    }
}
