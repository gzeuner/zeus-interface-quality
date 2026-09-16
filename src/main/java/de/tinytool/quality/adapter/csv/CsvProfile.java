package de.tinytool.quality.adapter.csv;

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
 * Version-one profile for delimited text files.
 *
 * <p>The profile is intentionally not part of the core model. It describes
 * CSV-specific transport and column rules while the resulting findings use
 * the common validation contract.</p>
 */
record CsvProfile(
        char delimiter,
        Charset charset,
        List<String> header,
        List<CsvColumn> columns
) {

    private static final Set<String> PROFILE_PROPERTIES = Set.of(
            "$id", "$schema", "format", "version", "delimiter", "encoding", "header", "columns");
    private static final Set<String> COLUMN_PROPERTIES = Set.of(
            "name", "type", "required", "minLength", "minimum", "pattern");

    static CsvProfile read(Path profileFile, ObjectMapper sourceMapper) throws ValidationException {
        ObjectMapper mapper = sourceMapper.copy()
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .enable(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY);
        final JsonNode root;
        try {
            root = mapper.readTree(profileFile.toFile());
        } catch (IOException e) {
            throw new ValidationException("Could not read CSV profile: " + e.getMessage(), e);
        }

        requireObject(root, "CSV profile");
        rejectUnknownProperties(root, PROFILE_PROPERTIES, "CSV profile");

        String format = requiredText(root, "format", "CSV profile");
        if (!"csv".equals(format)) {
            throw invalid("CSV profile format must be 'csv'");
        }

        JsonNode version = required(root, "version", "CSV profile");
        if (!version.isIntegralNumber() || version.intValue() != 1) {
            throw invalid("Unsupported CSV profile version: " + version);
        }

        String delimiterText = requiredText(root, "delimiter", "CSV profile");
        if (delimiterText.length() != 1
                || delimiterText.charAt(0) == '\r'
                || delimiterText.charAt(0) == '\n'
                || delimiterText.charAt(0) == '"') {
            throw invalid("CSV profile delimiter must be one character and must not be a quote or line break");
        }
        char delimiter = delimiterText.charAt(0);

        String encoding = requiredText(root, "encoding", "CSV profile");
        final Charset charset;
        try {
            charset = Charset.forName(encoding);
        } catch (IllegalArgumentException e) {
            throw new ValidationException("Unsupported CSV profile encoding: " + encoding, e);
        }

        List<String> header = readHeader(required(root, "header", "CSV profile"));
        List<CsvColumn> columns = readColumns(required(root, "columns", "CSV profile"), header);
        return new CsvProfile(delimiter, charset, header, columns);
    }

    private static List<String> readHeader(JsonNode node) throws ValidationException {
        if (!node.isArray() || node.isEmpty()) {
            throw invalid("CSV profile header must be a non-empty array");
        }

        List<String> header = new ArrayList<>();
        Set<String> names = new HashSet<>();
        for (JsonNode column : node) {
            if (!column.isTextual() || column.textValue().isBlank()) {
                throw invalid("CSV profile header names must be non-empty strings");
            }
            if (!names.add(column.textValue())) {
                throw invalid("CSV profile header contains duplicate column: " + column.textValue());
            }
            header.add(column.textValue());
        }
        return List.copyOf(header);
    }

    private static List<CsvColumn> readColumns(JsonNode node, List<String> header)
            throws ValidationException {
        if (!node.isArray() || node.size() != header.size()) {
            throw invalid("CSV profile columns must contain one definition for every header column");
        }

        List<CsvColumn> columns = new ArrayList<>();
        Set<String> names = new HashSet<>();
        for (int index = 0; index < node.size(); index++) {
            JsonNode column = node.get(index);
            requireObject(column, "CSV profile column " + index);
            rejectUnknownProperties(column, COLUMN_PROPERTIES, "CSV profile column " + index);

            String name = requiredText(column, "name", "CSV profile column " + index);
            if (!header.get(index).equals(name)) {
                throw invalid("CSV profile column " + index + " must define header column '"
                        + header.get(index) + "'");
            }
            if (!names.add(name)) {
                throw invalid("CSV profile contains duplicate column: " + name);
            }

            String typeName = requiredText(column, "type", "CSV profile column " + name);
            final CsvColumnType type;
            try {
                type = CsvColumnType.valueOf(typeName.toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw invalid("Unsupported CSV column type '" + typeName + "' for column " + name);
            }

            boolean required = optionalBoolean(column, "required", false, "CSV profile column " + name);
            Integer minLength = optionalNonNegativeInteger(
                    column, "minLength", "CSV profile column " + name);
            BigDecimal minimum = optionalNumber(column, "minimum", "CSV profile column " + name);
            Pattern pattern = optionalPattern(column, "pattern", "CSV profile column " + name);

            if (minLength != null && type != CsvColumnType.STRING) {
                throw invalid("minLength is only supported for string columns: " + name);
            }
            if (minimum != null
                    && type != CsvColumnType.INTEGER
                    && type != CsvColumnType.DECIMAL) {
                throw invalid("minimum is only supported for integer or decimal columns: " + name);
            }
            columns.add(new CsvColumn(name, type, required, minLength, minimum, pattern));
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
        JsonNode value = required(object, name, context);
        if (!value.isTextual() || value.textValue().isBlank()) {
            throw invalid(context + " property '" + name + "' must be a non-empty string");
        }
        return value.textValue();
    }

    private static boolean optionalBoolean(JsonNode object, String name, boolean defaultValue, String context)
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

    private static Integer optionalNonNegativeInteger(JsonNode object, String name, String context)
            throws ValidationException {
        JsonNode value = object.get(name);
        if (value == null) {
            return null;
        }
        if (!value.isIntegralNumber() || !value.canConvertToInt() || value.intValue() < 0) {
            throw invalid(context + " property '" + name + "' must be a non-negative integer");
        }
        return value.intValue();
    }

    private static BigDecimal optionalNumber(JsonNode object, String name, String context)
            throws ValidationException {
        JsonNode value = object.get(name);
        if (value == null) {
            return null;
        }
        if (!value.isNumber()) {
            throw invalid(context + " property '" + name + "' must be a number");
        }
        return value.decimalValue();
    }

    private static Pattern optionalPattern(JsonNode object, String name, String context)
            throws ValidationException {
        JsonNode value = object.get(name);
        if (value == null) {
            return null;
        }
        if (!value.isTextual()) {
            throw invalid(context + " property '" + name + "' must be a regular expression string");
        }
        try {
            return Pattern.compile(value.textValue());
        } catch (PatternSyntaxException e) {
            throw new ValidationException(context + " property '" + name
                    + "' contains an invalid regular expression: " + e.getDescription(), e);
        }
    }

    private static void requireObject(JsonNode node, String context) throws ValidationException {
        if (node == null || !node.isObject()) {
            throw invalid(context + " must be a JSON object");
        }
    }

    private static void rejectUnknownProperties(JsonNode object, Set<String> allowed, String context)
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

enum CsvColumnType {
    STRING,
    INTEGER,
    DECIMAL,
    EMAIL
}

record CsvColumn(
        String name,
        CsvColumnType type,
        boolean required,
        Integer minLength,
        BigDecimal minimum,
        Pattern pattern
) {
}
