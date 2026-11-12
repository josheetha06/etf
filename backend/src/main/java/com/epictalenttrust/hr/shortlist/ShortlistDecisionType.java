package com.epictalenttrust.hr.shortlist;

import com.epictalenttrust.hr.common.PgEnumConverter;
import jakarta.persistence.Converter;

/** HR's shortlist decision. Shown as Shortlist, Not now and Reserve. */
public enum ShortlistDecisionType {
    SHORTLISTED, REJECTED, HOLD;

    @Converter(autoApply = true)
    public static class DbConverter extends PgEnumConverter<ShortlistDecisionType> {
        public DbConverter() {
            super(ShortlistDecisionType.class);
        }
    }
}
