package com.epictalenttrust.hr.company;

import com.epictalenttrust.hr.common.PgEnumConverter;
import jakarta.persistence.Converter;

public enum DataRegion {
    EU, UK, US, APAC, OTHER;

    @Converter(autoApply = true)
    public static class DbConverter extends PgEnumConverter<DataRegion> {
        public DbConverter() {
            super(DataRegion.class);
        }
    }
}
