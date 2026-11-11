package com.smarthospital.common.persistence;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.sql.Timestamp;
import java.time.Instant;

/**
 * Maps {@link Instant} values to MySQL DATETIME columns in a portable way.
 *
 * <p>EclipseLink 4 running on GlassFish 7 does not always infer a JDBC temporal
 * type for {@code java.time.Instant}; without an explicit converter the attribute
 * can be treated as a serializable byte array. This auto-applied converter makes
 * every Instant field map to a standard {@link Timestamp}.</p>
 */
@Converter(autoApply = true)
public class InstantAttributeConverter implements AttributeConverter<Instant, Timestamp> {

    @Override
    public Timestamp convertToDatabaseColumn(Instant attribute) {
        return attribute == null ? null : Timestamp.from(attribute);
    }

    @Override
    public Instant convertToEntityAttribute(Timestamp databaseValue) {
        return databaseValue == null ? null : databaseValue.toInstant();
    }
}
