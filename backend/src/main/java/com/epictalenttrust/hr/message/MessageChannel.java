package com.epictalenttrust.hr.message;

import com.epictalenttrust.hr.common.PgEnumConverter;
import jakarta.persistence.Converter;

public enum MessageChannel {
    EMAIL;

    @Converter(autoApply = true)
    public static class DbConverter extends PgEnumConverter<MessageChannel> {
        public DbConverter() {
            super(MessageChannel.class);
        }
    }
}
