/*
 * Author: tiny-tool.de
 * Copyright 2026 tiny-tool.de
 * SPDX-License-Identifier: Apache-2.0
 * Licensed under the Apache License, Version 2.0.
 * See LICENSE or https://www.apache.org/licenses/LICENSE-2.0.
 */

package de.tinytool.quality.adapter.support;

import de.tinytool.quality.core.Finding;

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
        validateField(rawValue, recordNumber, columnName, type, rules, instancePath, schemaPath, '.', findings);
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
        String value = rawValue == null ? "" : rawValue;
        if (value.isEmpty()) {
            if (rules.required()) {
                findings.add(new Finding(
                        instancePath,
                        schemaPath + "/required",
                        "required",
                        "Zeile " + recordNumber + ", Spalte " + columnName + ": Pflichtwert fehlt"));
            }
            return;
        }

        String prefix = "Zeile " + recordNumber + ", Spalte " + columnName + ": ";
        switch (type) {
            case STRING -> {
                int length = value.codePointCount(0, value.length());
                if (rules.minLength() != null && length < rules.minLength()) {
                    findings.add(new Finding(
                            instancePath,
                            schemaPath + "/minLength",
                            "minLength",
                            prefix + "muss mindestens " + rules.minLength() + " Zeichen enthalten"));
                }
                if (rules.maxLength() != null && length > rules.maxLength()) {
                    findings.add(new Finding(
                            instancePath,
                            schemaPath + "/maxLength",
                            "maxLength",
                            prefix + "darf höchstens " + rules.maxLength() + " Zeichen enthalten"));
                }
            }
            case INTEGER -> {
                if (!INTEGER.matcher(value).matches()) {
                    findings.add(new Finding(
                            instancePath,
                            schemaPath + "/type",
                            "type",
                            prefix + "'" + value + "' ist keine ganze Zahl"));
                } else {
                    addNumericBounds(new BigDecimal(value), rules, prefix, instancePath, schemaPath, findings);
                }
            }
            case DECIMAL -> {
                try {
                    addNumericBounds(
                            parseDecimal(value, decimalSeparator),
                            rules,
                            prefix,
                            instancePath,
                            schemaPath,
                            findings);
                } catch (NumberFormatException e) {
                    findings.add(new Finding(
                            instancePath,
                            schemaPath + "/type",
                            "type",
                            prefix + "'" + value + "' ist keine Dezimalzahl"));
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
                            prefix + "'" + value + "' ist kein gültiges Datum im Format JJJJ-MM-TT"));
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
                            prefix + "'" + value + "' ist keine gültige Uhrzeit im Format HH:mm"));
                }
            }
            case EMAIL -> {
                if (!EMAIL.matcher(value).matches()) {
                    findings.add(new Finding(
                            instancePath,
                            schemaPath + "/type",
                            "type",
                            prefix + "'" + value + "' ist keine gültige E-Mail-Adresse"));
                }
            }
        }

        if (rules.pattern() != null && !rules.pattern().matcher(value).matches()) {
            findings.add(new Finding(
                    instancePath,
                    schemaPath + "/pattern",
                    "pattern",
                    prefix + "entspricht nicht dem vorgegebenen Muster"));
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
            String prefix,
            String instancePath,
            String schemaPath,
            List<Finding> findings
    ) {
        if (rules.minimum() != null && value.compareTo(rules.minimum()) < 0) {
            findings.add(new Finding(
                    instancePath,
                    schemaPath + "/minimum",
                    "minimum",
                    prefix + "muss mindestens " + plain(rules.minimum()) + " sein"));
        }
        if (rules.maximum() != null && value.compareTo(rules.maximum()) > 0) {
            findings.add(new Finding(
                    instancePath,
                    schemaPath + "/maximum",
                    "maximum",
                    prefix + "darf höchstens " + plain(rules.maximum()) + " sein"));
        }
    }

    private static String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }
}
