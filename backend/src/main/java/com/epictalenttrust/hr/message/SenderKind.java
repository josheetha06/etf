package com.epictalenttrust.hr.message;

import com.epictalenttrust.hr.common.PgEnumConverter;
import jakarta.persistence.Converter;

/** SYSTEM messages are sent by the AI agent and labelled AI in the portal. */
public enum SenderKind {
    HR_USER, CANDIDATE, ETT_STAFF, SYSTEM;

    @Converter(autoApply = true)
    public static class DbConverter extends PgEnumConverter<SenderKind> {
        public DbConverter() {
            super(SenderKind.class);
        }
    }
}
