package com.epictalenttrust.hr.common;

import jakarta.persistence.AttributeConverter;

import java.util.Locale;

/**
 * Maps a Java enum to a PostgreSQL enum label. Labels in the schema are the
 * lower-case form of the Java constant names (MUST_HAVE <-> must_have).
 */
public abstract class PgEnumConverter<E extends Enum<E>> implements AttributeConverter<E, String> {

    private final Class<E> type;

    protected PgEnumConverter(Class<E> type) {
        this.type = type;
    }

    @Override
    public String convertToDatabaseColumn(E value) {
        return value == null ? null : toDb(value);
    }

    @Override
    public E convertToEntityAttribute(String label) {
        return label == null ? null : Enum.valueOf(type, label.toUpperCase(Locale.ROOT));
    }

    public static String toDb(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT);
    }
}
