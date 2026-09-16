package de.tinytool.quality.adapter.support;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.tinytool.quality.core.ValidationException;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Shared JSON profile reading for adapter-specific contracts.
 */
public final class ProfileDocuments {

    private ProfileDocuments() {
    }

    public static ObjectMapper strictMapper(ObjectMapper sourceMapper) {
        return sourceMapper.copy()
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .enable(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY);
    }

    public static JsonNode readRoot(Path profileFile, ObjectMapper mapper, String label)
            throws ValidationException {
        try {
            return mapper.readTree(profileFile.toFile());
        } catch (IOException e) {
            throw new ValidationException("Could not read " + label + ": " + e.getMessage(), e);
        }
    }

    public static void requireObject(JsonNode node, String context) throws ValidationException {
        if (node == null || !node.isObject()) {
            throw invalid(context + " must be a JSON object");
        }
    }

    public static void rejectUnknownProperties(JsonNode object, Set<String> allowed, String context)
            throws ValidationException {
        Iterator<String> names = object.fieldNames();
        while (names.hasNext()) {
            String name = names.next();
            if (!allowed.contains(name)) {
                throw invalid(context + " contains unsupported property '" + name + "'");
            }
        }
    }

    public static JsonNode required(JsonNode object, String name, String context)
            throws ValidationException {
        JsonNode value = object.get(name);
        if (value == null || value.isNull()) {
            throw invalid(context + " requires property '" + name + "'");
        }
        return value;
    }

    public static String requiredText(JsonNode object, String name, String context)
            throws ValidationException {
        String value = optionalText(object, name, context);
        if (value == null) {
            throw invalid(context + " requires property '" + name + "'");
        }
        return value;
    }

    public static String optionalText(JsonNode object, String name, String context)
            throws ValidationException {
        JsonNode value = object.get(name);
        if (value == null) {
            return null;
        }
        if (!value.isTextual() || value.textValue().isBlank()) {
            throw invalid(context + " property '" + name + "' must be a non-empty string");
        }
        return value.textValue();
    }

    public static int requiredPositiveInteger(JsonNode object, String name, String context)
            throws ValidationException {
        JsonNode value = required(object, name, context);
        if (!value.isIntegralNumber() || !value.canConvertToInt() || value.intValue() <= 0) {
            throw invalid(context + " property '" + name + "' must be a positive integer");
        }
        return value.intValue();
    }

    public static boolean optionalBoolean(
            JsonNode object,
            String name,
            boolean defaultValue,
            String context
    ) throws ValidationException {
        JsonNode value = object.get(name);
        if (value == null) {
            return defaultValue;
        }
        if (!value.isBoolean()) {
            throw invalid(context + " property '" + name + "' must be boolean");
        }
        return value.booleanValue();
    }

    public static Integer optionalNonNegativeInteger(JsonNode object, String name, String context)
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

    public static BigDecimal optionalNumber(JsonNode object, String name, String context)
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

    public static Pattern optionalPattern(JsonNode object, String name, String context)
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

    public static void requireVersionOne(JsonNode root, String context) throws ValidationException {
        JsonNode version = required(root, "version", context);
        if (!version.isIntegralNumber() || version.intValue() != 1) {
            throw invalid("Unsupported " + context + " version: " + version);
        }
    }

    public static ScalarRules readRules(JsonNode column, ScalarType type, String columnName)
            throws ValidationException {
        String context = "profile column " + columnName;
        boolean required = optionalBoolean(column, "required", false, context);
        Integer minLength = optionalNonNegativeInteger(column, "minLength", context);
        Integer maxLength = optionalNonNegativeInteger(column, "maxLength", context);
        BigDecimal minimum = optionalNumber(column, "minimum", context);
        BigDecimal maximum = optionalNumber(column, "maximum", context);
        Pattern pattern = optionalPattern(column, "pattern", context);

        if ((minLength != null || maxLength != null) && !type.supportsLength()) {
            throw invalid("minLength and maxLength are only supported for string columns: " + columnName);
        }
        if ((minimum != null || maximum != null) && !type.supportsNumericBounds()) {
            throw invalid("minimum and maximum are only supported for integer or decimal columns: "
                    + columnName);
        }
        if (minLength != null && maxLength != null && minLength > maxLength) {
            throw invalid("minLength must not be greater than maxLength: " + columnName);
        }
        if (minimum != null && maximum != null && minimum.compareTo(maximum) > 0) {
            throw invalid("minimum must not be greater than maximum: " + columnName);
        }
        return new ScalarRules(required, minLength, maxLength, minimum, maximum, pattern);
    }

    public static ValidationException invalid(String message) {
        return new ValidationException(message);
    }
}
