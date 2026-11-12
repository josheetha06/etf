package com.epictalenttrust.hr.application;

import com.epictalenttrust.hr.common.PgEnumConverter;
import jakarta.persistence.Converter;

public enum ApplicationStatus {
    ACTIVE, REJECTED, WITHDRAWN, HIRED;

    @Converter(autoApply = true)
    public static class DbConverter extends PgEnumConverter<ApplicationStatus> {
        public DbConverter() {
            super(ApplicationStatus.class);
        }
    }
}
