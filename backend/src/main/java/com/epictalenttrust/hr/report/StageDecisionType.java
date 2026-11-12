package com.epictalenttrust.hr.report;

import com.epictalenttrust.hr.common.PgEnumConverter;
import jakarta.persistence.Converter;

public enum StageDecisionType {
    ADVANCE, HOLD, REJECT;

    @Converter(autoApply = true)
    public static class DbConverter extends PgEnumConverter<StageDecisionType> {
        public DbConverter() {
            super(StageDecisionType.class);
        }
    }
}
