package com.epictalenttrust.hr.screening;

import com.epictalenttrust.hr.common.PgEnumConverter;
import jakarta.persistence.Converter;

/** What the AI agent recommends. HR always makes the decision. */
public enum AiSuggestion {
    SHORTLIST, REVIEW, REJECT;

    @Converter(autoApply = true)
    public static class DbConverter extends PgEnumConverter<AiSuggestion> {
        public DbConverter() {
            super(AiSuggestion.class);
        }
    }
}
