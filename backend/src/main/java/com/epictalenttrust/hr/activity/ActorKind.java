package com.epictalenttrust.hr.activity;

import com.epictalenttrust.hr.common.PgEnumConverter;
import jakarta.persistence.Converter;

/** Who performed an action. SYSTEM covers the AI agent and scheduled jobs. */
public enum ActorKind {
    HR_USER, ETT_STAFF, CANDIDATE, SYSTEM, PANEL_MEMBER, PARTNER_ASSESSOR;

    @Converter(autoApply = true)
    public static class DbConverter extends PgEnumConverter<ActorKind> {
        public DbConverter() {
            super(ActorKind.class);
        }
    }
}
