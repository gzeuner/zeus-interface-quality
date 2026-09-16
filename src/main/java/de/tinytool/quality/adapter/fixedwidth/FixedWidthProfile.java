package de.tinytool.quality.adapter.fixedwidth;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.tinytool.quality.core.ValidationException;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Version-one profile for fixed-width records.
 *
 * <p>Positions and lengths are one-based and measured in decoded Java
 * characters. A source language such as COBOL or RPG is descriptive metadata;
 * the adapter does not depend on that language.</p>
 */
record FixedWidthProfile(
        Charset charset,
        int recordLength,
        String sourceLanguage,
        List<FixedWidthColumn> columns
) {

    private static final Set<String> PROFILE_PROPERTIES = Set.of(
            "$id", "$schema", "format", "version", "encoding",
            "recordLength", "sourceLanguage", "columns");
    private static final Set<String> COLUMN_PROPERTIES = Set.of(
            "name", "start", "length", "type", "required", "trim",
            "minLength", "minimum", "pattern");

    static FixedWidthProfile read(Path profileFile, ObjectMapper sourceMapper)
            throws ValidationException {
        ObjectMapper mapper = sourceMapper.copy()
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .enable(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY);
        final JsonNode root;
        try {
            root = mapper.readTree(profileFile.toFile());
        } catch (IOException e) {
            throw new ValidationException("Could not read fixed-width profile: "
                    + e.getMessage(), e);
        }

        requireObject(root, "Fixed-width profile");
        rejectUnknownProperties(root, PROFILE_PROPERTIES, "Fixed-width profile");
        if (!"fixed-width".equals(requiredText(root, "format", "Fixed-width profile"))) {
            throw invalid("Fixed-width profile format must be 'fixed-width'");
        }

        JsonNode version = required(root, "version", "Fixed-width profile");
        if (!version.isIntegralNumber() || version.intValue() != 1) {
            throw invalid("Unsupported fixed-width profile version: " + version);
        }

        String encoding = requiredText(root, "encoding", "Fixed-width profile");
        final Charset charset;
        try {
            charset = Charset.forName(encoding);
        } catch (IllegalArgumentException e) {
            throw new ValidationException("Unsupported fixed-width profile encoding: "
                    + encoding, e);
        }

        int recordLength = requiredPositiveInteger(
                root, "recordLength", "Fixed-width profile");
        String sourceLanguage = optionalText(
                root, "sourceLanguage", "Fixed-width profile");
        List<FixedWidthColumn> columns = readColumns(
                required(root, "columns", "Fixed-width profile"), recordLength);
        return new FixedWidthProfile(charset, recordLength, sourceLanguage, columns);
    }

    private static List<FixedWidthColumn> readColumns(JsonNode node, int recordLength)
            throws ValidationException {
        if (!node.isArray() || node.isEmpty()) {
            throw invalid("Fixed-width profile columns must be a non-empty array");
        }

        List<FixedWidthColumn> columns = new ArrayList<>();
        Set<String> names = new HashSet<>();
        int previousEnd = 0;
        for (int index = 0; index < node.size(); index++) {
            JsonNode column = node.get(index);
            requireObject(column, "Fixed-width profile column " + index);
            rejectUnknownProperties(
                    column, COLUMN_PROPERTIES, "Fixed-width profile column " + index);

            String name = requiredText(
                    column, "name", "Fixed-width profile column " + index);
            if (!names.add(name)) {
                throw invalid("Fixed-width profile contains duplicate column: " + name);
            }
            int start = requiredPositiveInteger(
                    column, "start", "Fixed-width profile column " + name);
            int length = requiredPositiveInteger(
                    column, "length", "Fixed-width profile column " + name);
            if (start <= previousEnd) {
                throw invalid("Fixed-width profile columns must be ordered and must not overlap: "
                        + name);
            }
            long end = (long) start + length - 1;
            if (end > recordLength) {
                throw invalid("Fixed-width column exceeds recordLength: " + name);
            }
            previousEnd = Math.toIntExact(end);

            String typeName = requiredText(
                    column, "type", "Fixed-width profile column " + name);
            final FixedWidthColumnType type;
            try {
                type = FixedWidthColumnType.valueOf(
                        typeName.toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw invalid("Unsupported fixed-width column type '" + typeName
                        + "' for column " + name);
            }

            boolean required = optionalBoolean(
                    column, "required", false, "Fixed-width profile column " + name);
            boolean trim = optionalBoolean(
                    column, "trim", true, "Fixed-width profile column " + name);
            Integer minLength = optionalNonNegativeInteger(
                    column, "minLength", "Fixed-width profile column " + name);
            BigDecimal minimum = optionalNumber(
                    column, "minimum", "Fixed-width profile column " + name);
            Pattern pattern = optionalPattern(
                    column, "pattern", "Fixed-width profile column " + name);

            if (minLength != null && type != FixedWidthColumnType.STRING) {
                throw invalid("minLength is only supported for string columns: " + name);
            }
            if (minimum != null
                    && type != FixedWidthColumnType.INTEGER
                    && type != FixedWidthColumnType.DECIMAL) {
                throw invalid("minimum is only supported for integer or decimal columns: "
                        + name);
            }
            columns.add(new FixedWidthColumn(
                    name, start, length, type, required, trim, minLength, minimum, pattern));
        }
        return List.copyOf(columns);
    }

    private static JsonNode required(JsonNode object, String name, String context)
            throws ValidationException {
        JsonNode value = object.get(name);
        if (value == null || value.isNull()) {
            throw invalid(context + " requires property '" + name + "'");
        }
        return value;
    }

    private static String requiredText(JsonNode object, String name, String context)
            throws ValidationException {
        String value = optionalText(object, name, context);
        if (value == null) {
            throw invalid(context + " requires property '" + name + "'");
        }
        return value;
    }

    private static String optionalText(JsonNode object, String name, String context)
            throws ValidationException {
        JsonNode value = object.get(name);
        if (value == null) {
            return null;
        }
        if (!value.isTextual() || value.textValue().isBlank()) {
            throw invalid(context + " property '" + name
                    + "' must be a non-empty string");
        }
        return value.textValue();
    }

    private static int requiredPositiveInteger(JsonNode object, String name, String context)
            throws ValidationException {
        JsonNode value = required(object, name, context);
        if (!value.isIntegralNumber() || !value.canConvertToInt() || value.intValue() <= 0) {
            throw invalid(context + " property '" + name
                    + "' must be a positive integer");
        }
        return value.intValue();
    }

    private static boolean optionalBoolean(
            JsonNode object, String name, boolean defaultValue, String context)
            throws ValidationException {
        JsonNode value = object.get(name);
        if (value == null) {
            return defaultValue;
        }
        if (!value.isBoolean()) {
            throw invalid(context + " property '" + name + "' must be boolean");
        }
        return value.booleanValue();
    }

    private static Integer optionalNonNegativeInteger(
            JsonNode object, String name, String context) throws ValidationException {
        JsonNode value = object.get(name);
        if (value == null) {
            return null;
        }
        if (!value.isIntegralNumber() || !value.canConvertToInt() || value.intValue() < 0) {
            throw invalid(context + " property '" + name
                    + "' must be a non-negative integer");
        }
        return value.intValue();
    }

    private static BigDecimal optionalNumber(
            JsonNode object, String name, String context) throws ValidationException {
        JsonNode value = object.get(name);
        if (value == null) {
            return null;
        }
        if (!value.isNumber()) {
            throw invalid(context + " property '" + name + "' must be a number");
        }
        return value.decimalValue();
    }

    private static Pattern optionalPattern(
            JsonNode object, String name, String context) throws ValidationException {
        JsonNode value = object.get(name);
        if (value == null) {
            return null;
        }
        if (!value.isTextual()) {
            throw invalid(context + " property '" + name
                    + "' must be a regular expression string");
        }
        try {
            return Pattern.compile(value.textValue());
        } catch (PatternSyntaxException e) {
            throw new ValidationException(context + " property '" + name
                    + "' contains an invalid regular expression: "
                    + e.getDescription(), e);
        }
    }

    private static void requireObject(JsonNode node, String context)
            throws ValidationException {
        if (node == null || !node.isObject()) {
            throw invalid(context + " must be a JSON object");
        }
    }

    private static void rejectUnknownProperties(
            JsonNode object, Set<String> allowed, String context)
            throws ValidationException {
        Iterator<String> names = object.fieldNames();
        while (names.hasNext()) {
            String name = names.next();
            if (!allowed.contains(name)) {
                throw invalid(context + " contains unsupported property '" + name + "'");
            }
        }
    }

    private static ValidationException invalid(String message) {
        return new ValidationException(message);
    }
}

enum FixedWidthColumnType {
    STRING,
    INTEGER,
    DECIMAL,
    EMAIL
}

record FixedWidthColumn(
        String name,
        int start,
        int length,
        FixedWidthColumnType type,
        boolean required,
        boolean trim,
        Integer minLength,
        BigDecimal minimum,
        Pattern pattern
) {
}
