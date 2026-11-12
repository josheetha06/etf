package com.epictalenttrust.hr.company;

import com.epictalenttrust.hr.common.PgEnumConverter;
import jakarta.persistence.Converter;

public enum CompanyStatus {
    ACTIVE, SUSPENDED, INACTIVE;

    @Converter(autoApply = true)
    public static class DbConverter extends PgEnumConverter<CompanyStatus> {
        public DbConverter() {
            super(CompanyStatus.class);
        }
    }
}
