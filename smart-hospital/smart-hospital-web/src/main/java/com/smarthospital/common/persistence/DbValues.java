package com.smarthospital.common.persistence;

import java.util.UUID;

/** Helpers for JDBC scalar values returned by native queries across database drivers. */
public final class DbValues {
    private DbValues() { }

    public static UUID uuid(Object value) {
        if (value == null) return null;
        if (value instanceof UUID uuid) return uuid;
        if (value instanceof byte[] bytes) {
            String text = new String(bytes, java.nio.charset.StandardCharsets.US_ASCII).trim();
            return UUID.fromString(text);
        }
        return UUID.fromString(value.toString().trim());
    }
}
