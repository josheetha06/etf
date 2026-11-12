package com.epictalenttrust.hr.interview;

import com.epictalenttrust.hr.common.PgEnumConverter;
import jakarta.persistence.Converter;

public enum RescheduleReason {
    CLASH, ILLNESS, TRAVEL, OTHER;

    @Converter(autoApply = true)
    public static class DbConverter extends PgEnumConverter<RescheduleReason> {
        public DbConverter() {
            super(RescheduleReason.class);
        }
    }
}
