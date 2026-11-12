package com.epictalenttrust.hr.job;

import com.epictalenttrust.hr.common.PgEnumConverter;
import jakarta.persistence.Converter;

public enum ContractType {
    PERMANENT, FIXED_TERM, CONTRACT, PART_TIME, INTERNSHIP;

    @Converter(autoApply = true)
    public static class DbConverter extends PgEnumConverter<ContractType> {
        public DbConverter() {
            super(ContractType.class);
        }
    }
}
