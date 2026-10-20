/*
 * Author: tiny-tool.de
 * Copyright 2026 tiny-tool.de
 * SPDX-License-Identifier: Apache-2.0
 * Licensed under the Apache License, Version 2.0.
 * See LICENSE or https://www.apache.org/licenses/LICENSE-2.0.
 */

package de.tinytool.quality.adapter.csv;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.tinytool.quality.adapter.support.ProfileDocuments;
import de.tinytool.quality.adapter.support.ScalarRules;
import de.tinytool.quality.adapter.support.ScalarType;
import de.tinytool.quality.core.ValidationException;

import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Version-one profile for delimited text files.
 *
 * <p>The profile is intentionally not part of the core model. It describes
 * CSV-specific transport and column rules while the resulting findings use
 * the common validation contract.</p>
 */
record CsvProfile(
        char delimiter,
        char decimalSeparator,
        Charset charset,
        List<String> header,
        List<CsvColumn> columns,
        CsvEnvelope envelope,
        Integer minRecords,
        Integer maxRecords,
        List<String> uniqueBy
) {

    private static final Set<String> PROFILE_PROPERTIES = Set.of(
            "$id", "$schema", "format", "version", "delimiter", "decimalSeparator", "encoding",
            "header", "columns", "envelope", "minRecords", "maxRecords", "uniqueBy");
    private static final Set<String> COLUMN_PROPERTIES = Set.of(
            "name", "type", "enabled", "required", "minLength", "maxLength", "minimum", "maximum", "pattern");

    static CsvProfile read(Path profileFile, ObjectMapper sourceMapper) throws ValidationException {
        ObjectMapper mapper = ProfileDocuments.strictMapper(sourceMapper);
        JsonNode root = ProfileDocuments.readRoot(profileFile, mapper, "CSV profile");
        return fromNode(root);
    }

    static CsvProfile fromNode(JsonNode root) throws ValidationException {
        ProfileDocuments.requireObject(root, "CSV profile");
        ProfileDocuments.rejectUnknownProperties(root, PROFILE_PROPERTIES, "CSV profile");

        String format = ProfileDocuments.requiredText(root, "format", "CSV profile");
        if (!"csv".equals(format)) {
            throw ProfileDocuments.invalid("CSV profile format must be 'csv'");
        }
        ProfileDocuments.requireVersionOne(root, "CSV profile");

        String delimiterText = ProfileDocuments.requiredText(root, "delimiter", "CSV profile");
        if (delimiterText.length() != 1
                || delimiterText.charAt(0) == '\r'
                || delimiterText.charAt(0) == '\n'
                || delimiterText.charAt(0) == '"') {
            throw ProfileDocuments.invalid(
                    "CSV profile delimiter must be one character and must not be a quote or line break");
        }
        char delimiter = delimiterText.charAt(0);

        String decimalSeparatorText = ProfileDocuments.optionalText(root, "decimalSeparator", "CSV profile");
        char decimalSeparator = '.';
        if (decimalSeparatorText != null) {
            if (decimalSeparatorText.length() != 1
                    || (decimalSeparatorText.charAt(0) != '.' && decimalSeparatorText.charAt(0) != ',')
                    || decimalSeparatorText.charAt(0) == delimiter) {
                throw ProfileDocuments.invalid(
                        "CSV profile decimalSeparator must be '.' or ',' and must differ from the delimiter");
            }
            decimalSeparator = decimalSeparatorText.charAt(0);
        }

        String encoding = ProfileDocuments.requiredText(root, "encoding", "CSV profile");
        final Charset charset;
        try {
            charset = Charset.forName(encoding);
        } catch (IllegalArgumentException e) {
            throw new ValidationException("Unsupported CSV profile encoding: " + encoding, e);
        }

        List<String> header = readNames(ProfileDocuments.required(root, "header", "CSV profile"), "header");
        List<CsvColumn> columns = readColumns(
                ProfileDocuments.required(root, "columns", "CSV profile"), header);
        CsvEnvelope envelope = readEnvelope(root.get("envelope"));
        Integer minRecords = ProfileDocuments.optionalNonNegativeInteger(root, "minRecords", "CSV profile");
        Integer maxRecords = ProfileDocuments.optionalNonNegativeInteger(root, "maxRecords", "CSV profile");
        if (minRecords != null && maxRecords != null && minRecords > maxRecords) {
            throw ProfileDocuments.invalid("CSV profile minRecords must not exceed maxRecords");
        }
        List<String> uniqueBy = root.has("uniqueBy") ? readNames(root.get("uniqueBy"), "uniqueBy") : List.of();
        if (!header.containsAll(uniqueBy)) {
            throw ProfileDocuments.invalid("CSV profile uniqueBy must reference existing header columns");
        }
        return new CsvProfile(delimiter, decimalSeparator, charset, header, columns, envelope,
                minRecords, maxRecords, uniqueBy);
    }

    private static CsvEnvelope readEnvelope(JsonNode node) throws ValidationException {
        if (node == null) {
            return null;
        }
        ProfileDocuments.requireObject(node, "CSV profile envelope");
        ProfileDocuments.rejectUnknownProperties(node, Set.of("begin", "end"), "CSV profile envelope");
        String begin = ProfileDocuments.requiredText(node, "begin", "CSV profile envelope");
        String end = ProfileDocuments.requiredText(node, "end", "CSV profile envelope");
        if (begin.equals(end)) {
            throw ProfileDocuments.invalid("CSV profile envelope begin and end markers must differ");
        }
        return new CsvEnvelope(begin, end);
    }

    private static List<String> readNames(JsonNode node, String property) throws ValidationException {
        if (!node.isArray() || node.isEmpty()) {
            throw ProfileDocuments.invalid("CSV profile " + property + " must be a non-empty array");
        }

        List<String> header = new ArrayList<>();
        Set<String> names = new HashSet<>();
        for (JsonNode column : node) {
            if (!column.isTextual() || column.textValue().isBlank()) {
                throw ProfileDocuments.invalid("CSV profile " + property + " names must be non-empty strings");
            }
            if (!names.add(column.textValue())) {
                throw ProfileDocuments.invalid(
                        "CSV profile " + property + " contains duplicate column: " + column.textValue());
            }
            header.add(column.textValue());
        }
        return List.copyOf(header);
    }

    private static List<CsvColumn> readColumns(JsonNode node, List<String> header)
            throws ValidationException {
        if (!node.isArray() || node.size() != header.size()) {
            throw ProfileDocuments.invalid(
                    "CSV profile columns must contain one definition for every header column");
        }

        List<CsvColumn> columns = new ArrayList<>();
        Set<String> names = new HashSet<>();
        for (int index = 0; index < node.size(); index++) {
            JsonNode column = node.get(index);
            ProfileDocuments.requireObject(column, "CSV profile column " + index);
            ProfileDocuments.rejectUnknownProperties(
                    column, COLUMN_PROPERTIES, "CSV profile column " + index);

            String name = ProfileDocuments.requiredText(column, "name", "CSV profile column " + index);
            if (!header.get(index).equals(name)) {
                throw ProfileDocuments.invalid("CSV profile column " + index
                        + " must define header column '" + header.get(index) + "'");
            }
            if (!names.add(name)) {
                throw ProfileDocuments.invalid("CSV profile contains duplicate column: " + name);
            }

            String typeName = ProfileDocuments.requiredText(column, "type", "CSV profile column " + name);
            ScalarType type = ScalarType.fromProfile(typeName, name);
            ScalarRules rules = ProfileDocuments.readRules(column, type, name);
            boolean enabled = ProfileDocuments.optionalBoolean(column, "enabled", true, "CSV profile column " + name);
            columns.add(new CsvColumn(name, type, rules, enabled));
        }
        return List.copyOf(columns);
    }
}

record CsvColumn(
        String name,
        ScalarType type,
        ScalarRules rules,
        boolean enabled
) {
}

record CsvEnvelope(
        String begin,
        String end
) {
}
