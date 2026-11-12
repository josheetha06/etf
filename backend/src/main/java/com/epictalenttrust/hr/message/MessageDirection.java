package com.epictalenttrust.hr.message;

import com.epictalenttrust.hr.common.PgEnumConverter;
import jakarta.persistence.Converter;

public enum MessageDirection {
    INBOUND, OUTBOUND;

    @Converter(autoApply = true)
    public static class DbConverter extends PgEnumConverter<MessageDirection> {
        public DbConverter() {
            super(MessageDirection.class);
        }
    }
}
