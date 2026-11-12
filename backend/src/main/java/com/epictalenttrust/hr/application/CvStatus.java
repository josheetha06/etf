package com.epictalenttrust.hr.application;

import com.epictalenttrust.hr.common.PgEnumConverter;
import jakarta.persistence.Converter;

public enum CvStatus {
    UPLOADED, PARSED, FAILED, DELETED;

    @Converter(autoApply = true)
    public static class DbConverter extends PgEnumConverter<CvStatus> {
        public DbConverter() {
            super(CvStatus.class);
        }
    }
}
