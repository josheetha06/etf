package com.epictalenttrust.hr.staff;

import com.epictalenttrust.hr.common.PgEnumConverter;
import jakarta.persistence.Converter;

public enum EttStaffStatus {
    ACTIVE, INACTIVE;

    @Converter(autoApply = true)
    public static class DbConverter extends PgEnumConverter<EttStaffStatus> {
        public DbConverter() {
            super(EttStaffStatus.class);
        }
    }
}
