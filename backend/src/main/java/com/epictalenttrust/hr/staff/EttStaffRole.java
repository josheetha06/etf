package com.epictalenttrust.hr.staff;

import com.epictalenttrust.hr.common.PgEnumConverter;
import jakarta.persistence.Converter;

public enum EttStaffRole {
    ADMIN, REVIEWER;

    @Converter(autoApply = true)
    public static class DbConverter extends PgEnumConverter<EttStaffRole> {
        public DbConverter() {
            super(EttStaffRole.class);
        }
    }
}
