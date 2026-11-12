package com.epictalenttrust.hr.hruser;

import com.epictalenttrust.hr.common.PgEnumConverter;
import jakarta.persistence.Converter;

public enum HrUserStatus {
    INVITED, ACTIVE, DISABLED;

    @Converter(autoApply = true)
    public static class DbConverter extends PgEnumConverter<HrUserStatus> {
        public DbConverter() {
            super(HrUserStatus.class);
        }
    }
}
