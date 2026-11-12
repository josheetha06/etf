package com.epictalenttrust.hr.application;

import com.epictalenttrust.hr.common.PgEnumConverter;
import jakarta.persistence.Converter;

public enum ApplicationSource {
    HR_UPLOAD, EMAIL, PORTAL, REFERRAL, AGENCY, JOB_BOARD;

    @Converter(autoApply = true)
    public static class DbConverter extends PgEnumConverter<ApplicationSource> {
        public DbConverter() {
            super(ApplicationSource.class);
        }
    }
}
