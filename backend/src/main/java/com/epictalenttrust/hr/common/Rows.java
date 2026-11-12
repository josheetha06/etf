package com.epictalenttrust.hr.common;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;

/** Small helpers for reading JDBC rows into records. */
public final class Rows {

    private Rows() {
    }

    public static UUID uuid(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, UUID.class);
    }

    public static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp ts = rs.getTimestamp(column);
        return ts == null ? null : ts.toInstant();
    }

    public static LocalDate date(ResultSet rs, String column) throws SQLException {
        java.sql.Date date = rs.getDate(column);
        return date == null ? null : date.toLocalDate();
    }

    public static Integer integer(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    public static <E extends Enum<E>> E enumOf(ResultSet rs, String column, Class<E> type) throws SQLException {
        String label = rs.getString(column);
        return label == null ? null : Enum.valueOf(type, label.toUpperCase(Locale.ROOT));
    }
}
