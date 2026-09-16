package de.tinytool.quality.adapter.support;

import de.tinytool.quality.core.ValidationException;

import java.util.Locale;

/**
 * Shared column types for CSV and fixed-width profiles.
 */
public enum ScalarType {
    STRING,
    INTEGER,
    DECIMAL,
    EMAIL;

    public static ScalarType fromProfile(String typeName, String columnName) throws ValidationException {
        try {
            return ScalarType.valueOf(typeName.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ValidationException(
                    "Unsupported column type '" + typeName + "' for column " + columnName, e);
        }
    }

    public boolean supportsLength() {
        return this == STRING;
    }

    public boolean supportsNumericBounds() {
        return this == INTEGER || this == DECIMAL;
    }
}
