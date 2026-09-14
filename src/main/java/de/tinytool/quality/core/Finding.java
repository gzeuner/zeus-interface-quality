package de.tinytool.quality.core;

import java.util.Objects;

/**
 * A deterministic, validator-independent description of a validation finding.
 */
public record Finding(
        String instancePath,
        String schemaPath,
        String keyword,
        String message
) {

    public Finding {
        instancePath = requireNonNull(instancePath, "instancePath");
        schemaPath = requireNonNull(schemaPath, "schemaPath");
        keyword = requireNonBlank(keyword, "keyword");
        message = requireNonBlank(message, "message");
    }

    private static String requireNonNull(String value, String fieldName) {
        return Objects.requireNonNull(value, fieldName + " must not be null");
    }

    private static String requireNonBlank(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}
