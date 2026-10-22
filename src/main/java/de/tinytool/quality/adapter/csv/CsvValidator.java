/*
 * Author: tiny-tool.de
 * Copyright 2026 tiny-tool.de
 * SPDX-License-Identifier: Apache-2.0
 * Licensed under the Apache License, Version 2.0.
 * See LICENSE or https://www.apache.org/licenses/LICENSE-2.0.
 */

package de.tinytool.quality.adapter.csv;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import de.tinytool.quality.adapter.support.JsonPointers;
import de.tinytool.quality.adapter.support.LocalFiles;
import de.tinytool.quality.adapter.support.ProfileDocuments;
import de.tinytool.quality.adapter.support.ScalarValidation;
import de.tinytool.quality.core.Finding;
import de.tinytool.quality.core.ValidationException;
import de.tinytool.quality.core.ValidationResult;
import de.tinytool.quality.core.Validator;
import de.tinytool.quality.core.ValidationLanguage;
import de.tinytool.quality.core.ValidationMessages;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.CodingErrorAction;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Validates a delimited text file against a version-one CSV profile.
 */
public final class CsvValidator implements Validator {

    private final ObjectMapper objectMapper;

    public CsvValidator() {
        this(new ObjectMapper());
    }

    public CsvValidator(ObjectMapper objectMapper) {
        this.objectMapper = ProfileDocuments.strictMapper(objectMapper);
    }

    public void checkProfile(Path profile) throws ValidationException {
        CsvProfile.read(LocalFiles.requireRegularFile(profile, "CSV profile"), objectMapper);
    }

    public void checkProfile(JsonNode profile) throws ValidationException {
        CsvProfile.fromNode(profile);
    }

    @Override
    public ValidationResult validate(Path input, Path schema) throws ValidationException {
        Path inputFile = LocalFiles.requireRegularFile(input, "CSV input");
        return validate(inputFile, schema, inputFile.getFileName().toString(), ValidationLanguage.DE);
    }

    /** Validates a downloaded CSV against its logical source file name. */
    public ValidationResult validate(Path input, Path schema, String logicalFileName) throws ValidationException {
        return validate(input, schema, logicalFileName, ValidationLanguage.DE);
    }

    public ValidationResult validate(Path input, Path schema, String logicalFileName, ValidationLanguage language) throws ValidationException {
        Path inputFile = LocalFiles.requireRegularFile(input, "CSV input");
        Path profileFile = LocalFiles.requireRegularFile(schema, "CSV profile");
        CsvProfile profile = CsvProfile.read(profileFile, objectMapper);

        if (logicalFileName == null || logicalFileName.isBlank()) {
            throw new ValidationException("CSV logical file name must not be empty");
        }
        if (profile.fileNamePattern() != null && !profile.fileNamePattern().matcher(logicalFileName).matches()) {
            return ValidationResult.invalid(List.of(new Finding(
                    "", "/fileNamePattern", "fileNamePattern",
                    ValidationMessages.text(language, "filename", logicalFileName,
                            profile.fileNamePattern().pattern()))));
        }

        List<Finding> findings = new ArrayList<>();
        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setDelimiter(profile.delimiter())
                .setIgnoreEmptyLines(false)
                .setTrim(false)
                .get();

        try (Reader reader = newStrictReader(inputFile, profile);
             CSVParser parser = format.parse(reader)) {
            var records = parser.iterator();
            if (!records.hasNext()) {
                return ValidationResult.invalid(List.of(new Finding(
                        "", "/header", "header", ValidationMessages.text(language, "headerMissing"))));
            }

            if (profile.envelope() != null) {
                CSVRecord beginMarker = records.next();
                if (!isMarker(beginMarker, profile.envelope().begin(), profile.columns().size())) {
                    return ValidationResult.invalid(List.of(new Finding(
                            "",
                            "/envelope/begin",
                            "marker",
                            "CSV-Datei enthält keinen gültigen Anfangsmarker '"
                                    + profile.envelope().begin() + "'")));
                }
                if (!records.hasNext()) {
                    return ValidationResult.invalid(List.of(new Finding(
                            "", "/header", "header", ValidationMessages.text(language, "headerMissing"))));
                }
            }

            CSVRecord header = records.next();
            String headerProblem = compareHeader(header, profile.header(), profile.delimiter(), language);
            if (headerProblem != null) {
                return ValidationResult.invalid(List.of(new Finding(
                        "", "/header", "header", headerProblem)));
            }

            validateRows(records, profile, header.getRecordNumber(), findings, language);
        } catch (IOException | RuntimeException e) {
            throw new ValidationException("Could not read CSV input: " + LocalFiles.safeMessage(e), e);
        }

        return findings.isEmpty()
                ? ValidationResult.valid()
                : ValidationResult.invalid(findings);
    }

    @Override
    public ValidationResult validate(Path input, Path schema, ValidationLanguage language) throws ValidationException {
        Path inputFile = LocalFiles.requireRegularFile(input, "CSV input");
        return validate(inputFile, schema, inputFile.getFileName().toString(), language);
    }

    private static void validateRows(
            java.util.Iterator<CSVRecord> records,
            CsvProfile profile,
            long headerRecordNumber,
            List<Finding> findings,
            ValidationLanguage language
    ) {
        RowChecks checks = new RowChecks(profile, findings, language);
        if (profile.envelope() == null) {
            while (records.hasNext()) {
                checks.accept(records.next(), headerRecordNumber);
            }
            checks.finish();
            return;
        }

        CSVRecord pending = null;
        while (records.hasNext()) {
            CSVRecord current = records.next();
            if (pending != null) {
                checks.accept(pending, headerRecordNumber);
            }
            pending = current;
        }

        if (pending == null || !isMarker(pending, profile.envelope().end(), profile.columns().size())) {
            if (pending != null) {
                checks.accept(pending, headerRecordNumber);
            }
            findings.add(new Finding(
                    "",
                    "/envelope/end",
                    "marker",
                    ValidationMessages.text(language, "envelopeEnd", profile.envelope().end())));
        }
        checks.finish();
    }

    private static final class RowChecks {
        private final CsvProfile profile;
        private final List<Finding> findings;
        private final List<Integer> keyColumns;
        private final ValidationLanguage language;
        private final Map<List<String>, Long> firstRows = new HashMap<>();
        private long count;

        private RowChecks(CsvProfile profile, List<Finding> findings, ValidationLanguage language) {
            this.profile = profile;
            this.findings = findings;
            this.language = language;
            this.keyColumns = profile.uniqueBy().stream().map(profile.header()::indexOf).toList();
        }

        private void accept(CSVRecord record, long headerRecordNumber) {
            count++;
            validateRecord(record, profile, headerRecordNumber, findings, language);
            if (keyColumns.isEmpty() || record.size() != profile.columns().size()) {
                return;
            }
            List<String> key = keyColumns.stream().map(record::get).toList();
            Long first = firstRows.putIfAbsent(key, record.getRecordNumber());
            if (first != null) {
                findings.add(new Finding(
                        "/rows/" + (record.getRecordNumber() - headerRecordNumber - 1),
                        "/uniqueBy", "uniqueBy",
                        ValidationMessages.text(language, "duplicate", record.getRecordNumber(),
                                String.join(", ", profile.uniqueBy()), first)));
            }
        }

        private void finish() {
            if (profile.minRecords() != null && count < profile.minRecords()) {
                findings.add(new Finding("", "/minRecords", "minRecords",
                        ValidationMessages.text(language, "minRecords", count, profile.minRecords())));
            }
            if (profile.maxRecords() != null && count > profile.maxRecords()) {
                findings.add(new Finding("", "/maxRecords", "maxRecords",
                        ValidationMessages.text(language, "maxRecords", count, profile.maxRecords())));
            }
        }
    }

    private static boolean isMarker(CSVRecord record, String marker, int expectedColumnCount) {
        if (record.size() != expectedColumnCount || !marker.equals(record.get(0))) {
            return false;
        }
        for (int index = 1; index < record.size(); index++) {
            if (!record.get(index).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private static Reader newStrictReader(Path inputFile, CsvProfile profile) throws IOException {
        var decoder = profile.charset().newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        return new InputStreamReader(Files.newInputStream(inputFile), decoder);
    }

    private static void validateRecord(
            CSVRecord record,
            CsvProfile profile,
            long headerRecordNumber,
            List<Finding> findings,
            ValidationLanguage language
    ) {
        long recordNumber = record.getRecordNumber();
        int rowIndex = Math.toIntExact(recordNumber - headerRecordNumber - 1);
        if (record.size() != profile.columns().size()) {
            findings.add(new Finding(
                    "/rows/" + rowIndex,
                    "/columns",
                    "columns",
                    ValidationMessages.text(language, "columns", recordNumber,
                            record.size(), profile.columns().size())));
            return;
        }

        for (int index = 0; index < profile.columns().size(); index++) {
            CsvColumn column = profile.columns().get(index);
            if (!column.enabled()) {
                continue;
            }
            String path = "/rows/" + rowIndex + "/" + JsonPointers.escape(column.name());
            String schemaPath = "/columns/" + JsonPointers.escape(column.name());
            ScalarValidation.validateField(
                    record.get(index),
                    recordNumber,
                    column.name(),
                    column.type(),
                    column.rules(),
                    path,
                    schemaPath,
                    profile.decimalSeparator(),
                    language,
                    findings);
        }
    }

    private static String compareHeader(CSVRecord actual, List<String> expected, char delimiter,
                                        ValidationLanguage language) {
        if (actual.size() != expected.size()) {
            return ValidationMessages.text(language, "headerCount", actual.size(), expected.size());
        }
        for (int index = 0; index < expected.size(); index++) {
            if (!expected.get(index).equals(actual.get(index))) {
            return ValidationMessages.text(language, "headerMismatch",
                    String.join(String.valueOf(delimiter), expected),
                    String.join(String.valueOf(delimiter), actual));
            }
        }
        return null;
    }
}
