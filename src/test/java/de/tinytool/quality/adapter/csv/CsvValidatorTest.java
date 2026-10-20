/*
 * Author: tiny-tool.de
 * Copyright 2026 tiny-tool.de
 * SPDX-License-Identifier: Apache-2.0
 * Licensed under the Apache License, Version 2.0.
 * See LICENSE or https://www.apache.org/licenses/LICENSE-2.0.
 */

package de.tinytool.quality.adapter.csv;

import de.tinytool.quality.core.Finding;
import de.tinytool.quality.core.ValidationException;
import de.tinytool.quality.core.ValidationResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CsvValidatorTest {

    private final CsvValidator validator = new CsvValidator();

    @Test
    void acceptsAValidCsvWithQuotedFields() throws Exception {
        ValidationResult result = validator.validate(
                fixture("valid-delivery.csv"),
                fixture("delivery.csv-profile.json"));

        assertThat(result.isValid()).isTrue();
        assertThat(result.findings()).isEmpty();
    }

    @Test
    void reportsATypeFindingWithStableRowAndColumnPaths() throws Exception {
        ValidationResult result = validator.validate(
                fixture("invalid-wrong-type.csv"),
                fixture("delivery.csv-profile.json"));

        assertThat(result.findings()).containsExactly(new Finding(
                "/rows/0/quantity",
                "/columns/quantity/type",
                "type",
                "Zeile 2, Spalte quantity: 'three' ist keine ganze Zahl"));
    }

    @Test
    void reportsMissingRequiredValues() throws Exception {
        ValidationResult result = validator.validate(
                fixture("invalid-missing-required.csv"),
                fixture("delivery.csv-profile.json"));

        assertThat(result.findings()).containsExactly(new Finding(
                "/rows/0/quantity",
                "/columns/quantity/required",
                "required",
                "Zeile 2, Spalte quantity: Pflichtwert fehlt"));
    }

    @Test
    void reportsHeaderMismatchesAsDataFindings() throws Exception {
        ValidationResult result = validator.validate(
                fixture("invalid-header.csv"),
                fixture("delivery.csv-profile.json"));

        assertThat(result.findings()).containsExactly(new Finding(
                "",
                "/header",
                "header",
                "CSV-Kopfzeile stimmt nicht mit dem Profil überein "
                        + "(erwartet: deliveryId;quantity;email, gefunden: deliveryId;email;quantity)"));
    }

    @Test
    void reportsAnUnexpectedColumnCount() throws Exception {
        ValidationResult result = validator.validate(
                fixture("invalid-column-count.csv"),
                fixture("delivery.csv-profile.json"));

        assertThat(result.findings()).containsExactly(new Finding(
                "/rows/0",
                "/columns",
                "columns",
                "Zeile 2: 2 Spalten gefunden, 3 erwartet"));
    }

    @Test
    void treatsMalformedCsvAsAnOperationalError() {
        assertThatThrownBy(() -> validator.validate(
                fixture("malformed.csv"),
                fixture("delivery.csv-profile.json")))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Could not read CSV input");
    }

    @Test
    void rejectsUnsupportedProfileTypes() {
        assertThatThrownBy(() -> validator.validate(
                fixture("valid-delivery.csv"),
                fixture("invalid-profile.json")))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Unsupported column type");
    }

    @Test
    void supportsProfilePatternsAndDecimalMinimums(@TempDir Path directory) throws Exception {
        Path profile = Files.writeString(directory.resolve("profile.json"), """
                {
                  "format": "csv",
                  "version": 1,
                  "delimiter": ",",
                  "encoding": "UTF-8",
                  "header": ["code", "amount"],
                  "columns": [
                    {"name": "code", "type": "string", "required": true, "pattern": "^[A-Z]{3}$"},
                    {"name": "amount", "type": "decimal", "required": true, "minimum": 10.5}
                  ]
                }
                """);
        Path input = Files.writeString(directory.resolve("input.csv"), """
                code,amount
                ab,9.2
                """);

        ValidationResult result = validator.validate(input, profile);

        assertThat(result.findings()).extracting(Finding::keyword)
                .containsExactly("minimum", "pattern");
    }

    @Test
    void reportsMaximumAndMaxLength(@TempDir Path directory) throws Exception {
        Path profile = Files.writeString(directory.resolve("profile.json"), """
                {
                  "format": "csv",
                  "version": 1,
                  "delimiter": ",",
                  "encoding": "UTF-8",
                  "header": ["code", "amount"],
                  "columns": [
                    {"name": "code", "type": "string", "required": true, "maxLength": 3},
                    {"name": "amount", "type": "decimal", "required": true, "maximum": 10.5}
                  ]
                }
                """);
        Path input = Files.writeString(directory.resolve("input.csv"), """
                code,amount
                ABCD,10.6
                """);

        ValidationResult result = validator.validate(input, profile);

        assertThat(result.findings()).extracting(Finding::keyword)
                .containsExactly("maximum", "maxLength");
    }

    @Test
    void acceptsAnEnvelopedCsvWithDecimalCommaAndDateTimeTypes(@TempDir Path directory) throws Exception {
        Path profile = writeRentalProfile(directory);
        Path input = Files.writeString(directory.resolve("input.csv"), """
                "BOF";"";""
                "amount";"date";"time"
                ",30";"2026-10-01";"12:17"
                "EOF";"";""
                """);

        ValidationResult result = validator.validate(input, profile);

        assertThat(result.isValid()).isTrue();
        assertThat(result.findings()).isEmpty();
    }

    @Test
    void reportsInvalidEnvelopedCsvValuesAndMissingEndMarker(@TempDir Path directory) throws Exception {
        Path profile = writeRentalProfile(directory);
        Path input = Files.writeString(directory.resolve("input.csv"), """
                "BOF";"";""
                "amount";"date";"time"
                "1.234";"2026-02-30";"25:61"
                """);

        ValidationResult result = validator.validate(input, profile);

        assertThat(result.findings()).extracting(Finding::keyword)
                .containsExactly("marker", "pattern", "type", "type", "type");
    }

    private static Path writeRentalProfile(Path directory) throws Exception {
        return Files.writeString(directory.resolve("profile.json"), """
                {
                  "format": "csv",
                  "version": 1,
                  "delimiter": ";",
                  "decimalSeparator": ",",
                  "encoding": "UTF-8",
                  "header": ["amount", "date", "time"],
                  "envelope": {"begin": "BOF", "end": "EOF"},
                  "columns": [
                    {"name": "amount", "type": "decimal", "required": true,
                     "pattern": "^-?([0-9]{1,5},[0-9]{2}|,[0-9]{2})$"},
                    {"name": "date", "type": "date", "required": true},
                    {"name": "time", "type": "time", "required": true}
                  ]
                }
                """);
    }

    @Test
    void disabledColumnsSkipTheirRulesButNotTheFileStructure(@TempDir Path directory) throws Exception {
        Path profile = Files.writeString(directory.resolve("profile.json"), """
                {"format":"csv","version":1,"delimiter":";","encoding":"UTF-8",
                 "header":["code"],"columns":[
                   {"name":"code","type":"integer","required":true,"enabled":false,"minimum":10}]}
                """);
        Path input = Files.writeString(directory.resolve("input.csv"), "code\nnot-an-integer\n\n");
        assertThat(validator.validate(input, profile).isValid()).isTrue();
        Files.writeString(input, "code\none;two\n");
        assertThat(validator.validate(input, profile).findings()).extracting(Finding::keyword)
                .containsExactly("columns");
    }

    @Test
    void detectsCompositeDuplicateKeysWithoutConcatenationCollisions(@TempDir Path directory) throws Exception {
        Path profile = Files.writeString(directory.resolve("profile.json"), """
                {"format":"csv","version":1,"delimiter":";","encoding":"UTF-8",
                 "header":["a","b"],"uniqueBy":["a","b"],
                 "columns":[{"name":"a","type":"string"},{"name":"b","type":"string"}]}
                """);
        Path input = Files.writeString(directory.resolve("input.csv"), "a;b\nab;c\na;bc\nab;c\n");
        var findings = validator.validate(input, profile).findings();
        assertThat(findings).singleElement().isEqualTo(new Finding("/rows/2", "/uniqueBy", "uniqueBy",
                "Zeile 4: Schluessel aus a, b bereits in Zeile 2 vorhanden"));
    }

    @Test
    void recordLimitsExcludeEnvelopeAndHeaderAndIncludeBoundaryValues(@TempDir Path directory) throws Exception {
        Path profile = writeRentalProfile(directory);
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var node = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(profile.toFile());
        node.put("minRecords", 1).put("maxRecords", 1);
        mapper.writeValue(profile.toFile(), node);
        Path input = Files.writeString(directory.resolve("input.csv"), """
                BOF;;
                amount;date;time
                ,30;2026-10-01;12:17
                EOF;;
                """);
        assertThat(validator.validate(input, profile).isValid()).isTrue();
        Files.writeString(input, "BOF;;\namount;date;time\nEOF;;\n");
        assertThat(validator.validate(input, profile).findings()).extracting(Finding::keyword)
                .containsExactly("minRecords");
        node.put("minRecords", 0).put("maxRecords", 0);
        mapper.writeValue(profile.toFile(), node);
        assertThat(validator.validate(input, profile).isValid()).isTrue();
        Files.writeString(input, "BOF;;\namount;date;time\n,30;2026-10-01;12:17\nEOF;;\n");
        assertThat(validator.validate(input, profile).findings()).extracting(Finding::keyword)
                .containsExactly("maxRecords");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "\"minRecords\":-1", "\"maxRecords\":1.5", "\"minRecords\":2,\"maxRecords\":1",
            "\"uniqueBy\":[]", "\"uniqueBy\":[\"missing\"]", "\"uniqueBy\":[\"code\",\"code\"]",
            "\"uniqueBy\":null"
    })
    void rejectsInvalidDatasetRules(String rule, @TempDir Path directory) throws Exception {
        Path profile = Files.writeString(directory.resolve("profile.json"), """
                {"format":"csv","version":1,"delimiter":";","encoding":"UTF-8",
                 "header":["code"],"columns":[{"name":"code","type":"string"}],
                """ + rule + "}");
        assertThatThrownBy(() -> validator.checkProfile(profile)).isInstanceOf(ValidationException.class);
    }

    @Test
    void disabledColumnsStillRequireValidConfiguration(@TempDir Path directory) throws Exception {
        Path profile = Files.writeString(directory.resolve("profile.json"), """
                {"format":"csv","version":1,"delimiter":";","encoding":"UTF-8",
                 "header":["code"],"columns":[{"name":"code","enabled":false,"type":"typo"}]}
                """);
        assertThatThrownBy(() -> validator.checkProfile(profile)).isInstanceOf(ValidationException.class);
    }

    private static Path fixture(String name) {
        try {
            return Path.of(Objects.requireNonNull(
                    CsvValidatorTest.class.getResource("/fixtures/csv/" + name)).toURI());
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }
}
