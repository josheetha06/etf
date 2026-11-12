package com.epictalenttrust.hr.job;

import com.epictalenttrust.hr.common.PgEnumConverter;
import jakarta.persistence.Converter;

public enum CriterionKind {
    MUST_HAVE, NICE_TO_HAVE, CERTIFICATION;

    @Converter(autoApply = true)
    public static class DbConverter extends PgEnumConverter<CriterionKind> {
        public DbConverter() {
            super(CriterionKind.class);
        }
    }
}
