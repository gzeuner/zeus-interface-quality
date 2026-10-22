/*
 * Author: tiny-tool.de
 * Copyright 2026 tiny-tool.de
 * SPDX-License-Identifier: Apache-2.0
 * Licensed under the Apache License, Version 2.0.
 * See LICENSE or https://www.apache.org/licenses/LICENSE-2.0.
 */

package de.tinytool.quality.adapter.support;

import de.tinytool.quality.core.Finding;
import de.tinytool.quality.core.ValidationLanguage;
import de.tinytool.quality.core.ValidationMessages;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.time.temporal.ChronoField;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Type and constraint checks shared by CSV and fixed-width adapters.
 */
public final class ScalarValidation {

    private static final Pattern INTEGER = Pattern.compile("[+-]?\\d+");
    private static final Pattern DECIMAL_COMMA = Pattern.compile("[+-]?(?:\\d+(?:,\\d+)?|,\\d+)");
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final DateTimeFormatter ISO_TIME_MINUTE = new java.time.format.DateTimeFormatterBuilder()
            .appendValue(ChronoField.HOUR_OF_DAY, 2)
            .appendLiteral(':')
            .appendValue(ChronoField.MINUTE_OF_HOUR, 2)
            .toFormatter()
            .withResolverStyle(ResolverStyle.STRICT);

    private ScalarValidation() {
    }

    public static void validateField(
            String rawValue,
            long recordNumber,
            String columnName,
            ScalarType type,
            ScalarRules rules,
            String instancePath,
            String schemaPath,
            List<Finding> findings
    ) {
        validateField(rawValue, recordNumber, columnName, type, rules, instancePath, schemaPath, '.',
                ValidationLanguage.DE, findings);
    }

    public static void validateField(
            String rawValue,
            long recordNumber,
            String columnName,
            ScalarType type,
            ScalarRules rules,
            String instancePath,
            String schemaPath,
            char decimalSeparator,
            List<Finding> findings
    ) {
        validateField(rawValue, recordNumber, columnName, type, rules, instancePath, schemaPath,
                decimalSeparator, ValidationLanguage.DE, findings);
    }

    public static void validateField(
            String rawValue, long recordNumber, String columnName, ScalarType type, ScalarRules rules,
            String instancePath, String schemaPath, char decimalSeparator,
            ValidationLanguage language, List<Finding> findings
    ) {
        String value = rawValue == null ? "" : rawValue;
        if (value.isEmpty()) {
            if (rules.required()) {
                findings.add(new Finding(
                        instancePath,
                        schemaPath + "/required",
                        "required",
                        ValidationMessages.text(language, "required", recordNumber, columnName)));
            }
            return;
        }

        switch (type) {
            case STRING -> {
                int length = value.codePointCount(0, value.length());
                if (rules.minLength() != null && length < rules.minLength()) {
                    findings.add(new Finding(
                            instancePath,
                            schemaPath + "/minLength",
                            "minLength",
                            ValidationMessages.text(language, "minLength", recordNumber, columnName, rules.minLength())));
                }
                if (rules.maxLength() != null && length > rules.maxLength()) {
                    findings.add(new Finding(
                            instancePath,
                            schemaPath + "/maxLength",
                            "maxLength",
                            ValidationMessages.text(language, "maxLength", recordNumber, columnName, rules.maxLength())));
                }
            }
            case INTEGER -> {
                if (!INTEGER.matcher(value).matches()) {
                    findings.add(new Finding(
                            instancePath,
                            schemaPath + "/type",
                            "type",
                            ValidationMessages.text(language, "integer", recordNumber, columnName, value)));
                } else {
                    addNumericBounds(new BigDecimal(value), rules, recordNumber, columnName, language, instancePath, schemaPath, findings);
                }
            }
            case DECIMAL -> {
                try {
                    addNumericBounds(
                            parseDecimal(value, decimalSeparator),
                            rules,
                            recordNumber,
                            columnName,
                            language,
                            instancePath,
                            schemaPath,
                            findings);
                } catch (NumberFormatException e) {
                    findings.add(new Finding(
                            instancePath,
                            schemaPath + "/type",
                            "type",
                            ValidationMessages.text(language, "decimal", recordNumber, columnName, value)));
                }
            }
            case DATE -> {
                try {
                    LocalDate.parse(value, DateTimeFormatter.ISO_LOCAL_DATE);
                } catch (java.time.format.DateTimeParseException e) {
                    findings.add(new Finding(
                            instancePath,
                            schemaPath + "/type",
                            "type",
                            ValidationMessages.text(language, "date", recordNumber, columnName, value)));
                }
            }
            case TIME -> {
                try {
                    LocalTime.parse(value, ISO_TIME_MINUTE);
                } catch (java.time.format.DateTimeParseException e) {
                    findings.add(new Finding(
                            instancePath,
                            schemaPath + "/type",
                            "type",
                            ValidationMessages.text(language, "time", recordNumber, columnName, value)));
                }
            }
            case EMAIL -> {
                if (!EMAIL.matcher(value).matches()) {
                    findings.add(new Finding(
                            instancePath,
                            schemaPath + "/type",
                            "type",
                            ValidationMessages.text(language, "email", recordNumber, columnName, value)));
                }
            }
        }

        if (rules.pattern() != null && !rules.pattern().matcher(value).matches()) {
            findings.add(new Finding(
                    instancePath,
                    schemaPath + "/pattern",
                    "pattern",
                    ValidationMessages.text(language, "pattern", recordNumber, columnName)));
        }
    }

    private static BigDecimal parseDecimal(String value, char decimalSeparator) {
        if (decimalSeparator == '.') {
            return new BigDecimal(value);
        }
        if (!DECIMAL_COMMA.matcher(value).matches()) {
            throw new NumberFormatException("Value does not use the configured decimal separator");
        }
        String normalized = value.replace(',', '.');
        if (normalized.startsWith(".")) {
            normalized = "0" + normalized;
        } else if (normalized.startsWith("-.")) {
            normalized = "-0" + normalized.substring(1);
        } else if (normalized.startsWith("+.")) {
            normalized = "+0" + normalized.substring(1);
        }
        return new BigDecimal(normalized);
    }

    private static void addNumericBounds(
            BigDecimal value,
            ScalarRules rules,
            long recordNumber,
            String columnName,
            ValidationLanguage language,
            String instancePath,
            String schemaPath,
            List<Finding> findings
    ) {
        if (rules.minimum() != null && value.compareTo(rules.minimum()) < 0) {
            findings.add(new Finding(
                    instancePath,
                    schemaPath + "/minimum",
                    "minimum",
                    ValidationMessages.text(language, "minimum", recordNumber, columnName, plain(rules.minimum()))));
        }
        if (rules.maximum() != null && value.compareTo(rules.maximum()) > 0) {
            findings.add(new Finding(
                    instancePath,
                    schemaPath + "/maximum",
                    "maximum",
                    ValidationMessages.text(language, "maximum", recordNumber, columnName, plain(rules.maximum()))));
        }
    }

    private static String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }
}
