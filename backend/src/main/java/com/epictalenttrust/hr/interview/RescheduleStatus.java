package com.epictalenttrust.hr.interview;

import com.epictalenttrust.hr.common.PgEnumConverter;
import jakarta.persistence.Converter;

public enum RescheduleStatus {
    PENDING, APPROVED, DECLINED;

    @Converter(autoApply = true)
    public static class DbConverter extends PgEnumConverter<RescheduleStatus> {
        public DbConverter() {
            super(RescheduleStatus.class);
        }
    }
}
