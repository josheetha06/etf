package com.epictalenttrust.hr.job;

import com.epictalenttrust.hr.common.PgEnumConverter;
import jakarta.persistence.Converter;

public enum InterviewMode {
    ONLINE, PHONE, IN_PERSON;

    @Converter(autoApply = true)
    public static class DbConverter extends PgEnumConverter<InterviewMode> {
        public DbConverter() {
            super(InterviewMode.class);
        }
    }
}
