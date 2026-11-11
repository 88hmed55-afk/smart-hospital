package com.smarthospital.common.persistence;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.UUID;

/**
 * Maps {@link UUID} identifiers to their canonical CHAR(36) representation.
 *
 * <p>EclipseLink's built-in UUID converter fails with a NullPointerException when a
 * nullable UUID column contains {@code NULL} (for example {@code parent_account_id}
 * on top-level chart-of-account rows). This auto-applied converter handles nulls and
 * keeps the database representation identical to the MySQL schema.</p>
 */
@Converter(autoApply = true)
public class UuidAttributeConverter implements AttributeConverter<UUID, String> {

    @Override
    public String convertToDatabaseColumn(UUID attribute) {
        return attribute == null ? null : attribute.toString();
    }

    @Override
    public UUID convertToEntityAttribute(String databaseValue) {
        if (databaseValue == null) return null;
        String value = databaseValue.trim();
        return value.isEmpty() ? null : UUID.fromString(value);
    }
}
