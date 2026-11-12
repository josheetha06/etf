package com.epictalenttrust.hr.job;

import com.epictalenttrust.hr.common.PgEnumConverter;
import jakarta.persistence.Converter;

/** What happens in a round. HR picks one per round, in any order. */
public enum RoundKind {
    /** The ETT AI agent interviews the candidate online; ETT releases the report. */
    AI_INTERVIEW,
    /** A partner assessor interviews the candidate in person at a partner centre and sends a report. */
    PARTNER,
    /** The company's own interview panel, online or in person; each member sends a detailed review. */
    PANEL;

    public String label() {
        return switch (this) {
            case AI_INTERVIEW -> "AI interview";
            case PARTNER -> "Partner assessment centre";
            case PANEL -> "Panel interview";
        };
    }

    @Converter(autoApply = true)
    public static class DbConverter extends PgEnumConverter<RoundKind> {
        public DbConverter() {
            super(RoundKind.class);
        }
    }
}
