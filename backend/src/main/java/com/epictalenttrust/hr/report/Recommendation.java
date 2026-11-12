package com.epictalenttrust.hr.report;

import com.epictalenttrust.hr.common.PgEnumConverter;
import jakarta.persistence.Converter;

public enum Recommendation {
    ADVANCE, HOLD, REJECT;

    @Converter(autoApply = true)
    public static class DbConverter extends PgEnumConverter<Recommendation> {
        public DbConverter() {
            super(Recommendation.class);
        }
    }
}
