/*
 * Author: tiny-tool.de
 * Copyright 2026 tiny-tool.de
 * SPDX-License-Identifier: Apache-2.0
 * Licensed under the Apache License, Version 2.0.
 * See LICENSE or https://www.apache.org/licenses/LICENSE-2.0.
 */

package de.tinytool.quality.gui;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.tinytool.quality.adapter.support.BundledProfiles;
import de.tinytool.quality.core.ValidationException;
import de.tinytool.quality.core.ValidationResult;
import de.tinytool.quality.report.ReportFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class LocalValidationServiceTest {

    private final LocalValidationService service = new LocalValidationService();

    @ParameterizedTest
    @CsvSource({
            "valid-delivery.json, delivery.schema.json",
            "csv/valid-delivery.csv, csv/delivery.csv-profile.json",
            "fixedwidth/valid-delivery.dat, fixedwidth/delivery.rpg-profile.json"
    })
    void validatesEveryLocalFormat(String input, String profile) throws Exception {
        assertThat(service.validate(fixture(input), fixture(profile)).isValid()).isTrue();
    }

    @Test
    void loadsTheBundledPresetIncludingWindows1252(@TempDir Path directory) throws Exception {
        assumeTrue(BundledProfiles.hasRelex(), "Relex profile is not bundled");
        String text = Files.readString(fixture("csv/relex-synthetic.csv"))
                .replace("Test-Coupon", "Test-Gutschein \u00e4");
        Path input = directory.resolve("sample.csv");
        Files.writeString(input, text, Charset.forName("windows-1252"));

        assertThat(service.validateRelex(input).isValid()).isTrue();
    }

    @Test
    void reportsInvalidDataAgainstTheBundledPreset(@TempDir Path directory) throws Exception {
        assumeTrue(BundledProfiles.hasRelex(), "Relex profile is not bundled");
        Path input = Files.writeString(directory.resolve("sample.csv"),
                Files.readString(fixture("csv/relex-synthetic.csv")).replace("Lieferungen", "Abholung"));

        ValidationResult result = service.validateRelex(input);

        assertThat(result.isValid()).isFalse();
        assertThat(result.findings()).singleElement().satisfies(finding -> {
            assertThat(finding.instancePath()).isEqualTo("/rows/0/FULFILLMENT_TYP");
            assertThat(finding.keyword()).isEqualTo("pattern");
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"http", "sftp"})
    void rejectsNetworkProfilesBeforeAnyTransportIsCreated(String format, @TempDir Path directory) throws Exception {
        Path profile = Files.writeString(directory.resolve("remote.json"),
                "{\"format\":\"" + format + "\",\"url\":\"http://127.0.0.1:9\"}");

        assertThatThrownBy(() -> service.validate(fixture("valid-delivery.json"), profile))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("gesperrt");
    }

    @Test
    void rejectsMalformedProfilesInsteadOfFallingBack(@TempDir Path directory) throws Exception {
        Path profile = Files.writeString(directory.resolve("broken.json"),
                "{\"format\":\"csv\",\"format\":\"sftp\"}");

        assertThatThrownBy(() -> service.validate(fixture("valid-delivery.json"), profile))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Could not read");
    }

    @Test
    void explainsHowToUseArchives(@TempDir Path directory) throws Exception {
        Path archive = Files.writeString(directory.resolve("data.7z"), "not a CSV");

        assertThatThrownBy(() -> service.validateRelex(archive))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("entpacken");
    }

    @ParameterizedTest
    @ValueSource(strings = {"JSON", "TEXT"})
    void exportsTheExistingReportContract(String value, @TempDir Path directory) throws Exception {
        ReportFormat format = ReportFormat.valueOf(value);
        Path input = fixture("invalid-wrong-type.json");
        Path profile = fixture("delivery.schema.json");
        ValidationResult result = service.validate(input, profile);
        Path target = directory.resolve("report");

        service.saveReport(target, result, format, input, profile);

        if (format == ReportFormat.JSON) {
            var json = new ObjectMapper().readTree(target.toFile());
            assertThat(json.path("status").asText()).isEqualTo("INVALID");
            assertThat(json.path("findings").size()).isEqualTo(result.findings().size());
        } else {
            assertThat(Files.readString(target)).startsWith("INVALID").contains("[type]");
        }
        try (var files = Files.list(directory)) {
            assertThat(files.map(path -> path.getFileName().toString())).containsExactly("report");
        }
    }

    @Test
    void doesNotOverwriteInputOrProfile(@TempDir Path directory) throws Exception {
        Path input = Files.writeString(directory.resolve("input.json"), "{}");
        Path profile = Files.writeString(directory.resolve("schema.json"), "{}");

        for (Path source : new Path[]{input, profile}) {
            assertThatThrownBy(() -> service.saveReport(
                    source, ValidationResult.valid(), ReportFormat.JSON, input, profile))
                    .isInstanceOf(java.io.IOException.class)
                    .hasMessageContaining("nicht");
            assertThat(Files.readString(source)).isEqualTo("{}");
        }
    }

    @Test
    void protectsSourceAliases(@TempDir Path directory) throws Exception {
        Path input = Files.writeString(directory.resolve("input.json"), "{}");
        Path alias = directory.resolve("alias.json");
        try {
            Files.createLink(alias, input);
        } catch (UnsupportedOperationException | java.io.IOException e) {
            assumeTrue(false, "Hard links unavailable: " + e.getMessage());
        }

        assertThatThrownBy(() -> service.saveReport(
                alias, ValidationResult.valid(), ReportFormat.JSON, input, null))
                .isInstanceOf(java.io.IOException.class)
                .hasMessageContaining("nicht");
        assertThat(Files.readString(input)).isEqualTo("{}");
    }

    @Test
    void reportsExportFailures(@TempDir Path directory) throws Exception {
        Path target = directory.resolve("missing").resolve("report.json");

        assertThatThrownBy(() -> service.saveReport(target, ValidationResult.valid(), ReportFormat.JSON,
                fixture("valid-delivery.json"), fixture("delivery.schema.json")))
                .isInstanceOf(java.io.IOException.class);
    }

    @Test
    void replacesAnExistingReportWithoutLeavingTemporaryFiles(@TempDir Path directory) throws Exception {
        Path report = Files.writeString(directory.resolve("report.json"), "old");

        service.saveReport(report, ValidationResult.valid(), ReportFormat.JSON,
                fixture("valid-delivery.json"), fixture("delivery.schema.json"));

        assertThat(new ObjectMapper().readTree(report.toFile()).path("status").asText()).isEqualTo("VALID");
        try (var files = Files.list(directory)) {
            assertThat(files.toList()).containsExactly(report);
        }
    }

    @Test
    void cleansUpAfterAFailedReportReplacement(@TempDir Path directory) throws Exception {
        Path target = Files.createDirectory(directory.resolve("not-a-file.json"));
        Files.writeString(target.resolve("keep.txt"), "preserve");

        assertThatThrownBy(() -> service.saveReport(target, ValidationResult.valid(), ReportFormat.JSON,
                fixture("valid-delivery.json"), fixture("delivery.schema.json")))
                .isInstanceOf(java.io.IOException.class);
        assertThat(Files.readString(target.resolve("keep.txt"))).isEqualTo("preserve");
        try (var files = Files.list(directory)) {
            assertThat(files.toList()).containsExactly(target);
        }
    }

    static Path fixture(String name) throws Exception {
        return Path.of(Objects.requireNonNull(
                LocalValidationServiceTest.class.getResource("/fixtures/" + name)).toURI());
    }
}
