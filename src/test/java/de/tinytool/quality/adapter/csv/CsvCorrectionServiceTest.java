/*
 * Author: tiny-tool.de
 * Copyright 2026 tiny-tool.de
 * SPDX-License-Identifier: Apache-2.0
 * Licensed under the Apache License, Version 2.0.
 * See LICENSE or https://www.apache.org/licenses/LICENSE-2.0.
 */

package de.tinytool.quality.adapter.csv;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.tinytool.quality.core.ValidationException;
import de.tinytool.quality.core.ValidationLanguage;
import de.tinytool.quality.core.ValidationResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CsvCorrectionServiceTest {

    private final CsvValidator validator = new CsvValidator();
    private final CsvCorrectionService correction = new CsvCorrectionService();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void previewsEligibleCorrectionWithoutChangingInputOrCreatingArtifacts(@TempDir Path directory) throws Exception {
        Path profile = profile(directory, "\"correction\":{\"enabled\":true}");
        Path input = Files.writeString(directory.resolve("input.csv"), "code\n1\nnot-an-integer\n2\n");
        String original = Files.readString(input);
        ValidationResult initial = validator.validate(input, profile, ValidationLanguage.EN);

        ValidationResult preview = correction.preview(input, profile, ValidationLanguage.EN, initial);

        assertThat(preview.isValid()).isFalse();
        assertThat(preview.metadata().get("correctionPreview").toString()).contains("removedRecords=1");
        assertThat(Files.readString(input)).isEqualTo(original);
        assertThat(sidecars(directory)).isEmpty();
    }

    @Test
    void previewExplainsWhenExistingArtifactsBlockCorrection(@TempDir Path directory) throws Exception {
        Path profile = profile(directory, "\"correction\":{\"enabled\":true}");
        Path input = Files.writeString(directory.resolve("input.csv"), "code\nnot-an-integer\n");
        Path existing = Files.writeString(directory.resolve("input.csv.correction.json"), "preserve me");
        ValidationResult initial = validator.validate(input, profile, ValidationLanguage.EN);

        ValidationResult preview = correction.preview(input, profile, ValidationLanguage.EN, initial);

        assertThat(preview.isValid()).isFalse();
        assertThat(preview.metadata().get("correctionPreview").toString())
                .contains("eligible=false", "input.csv.correction.json");
        assertThat(Files.readString(existing)).isEqualTo("preserve me");
        assertThat(Files.readString(input)).isEqualTo("code\nnot-an-integer\n");
    }

    @Test
    void activeCorrectionLockBlocksPreviewAndIsPreserved(@TempDir Path directory) throws Exception {
        Path profile = profile(directory, "\"correction\":{\"enabled\":true}");
        Path input = Files.writeString(directory.resolve("input.csv"), "code\nnot-an-integer\n");
        Path lock = Files.writeString(directory.resolve("input.csv.correction.lock"), "active");
        ValidationResult initial = validator.validate(input, profile, ValidationLanguage.EN);

        ValidationResult preview = correction.preview(input, profile, ValidationLanguage.EN, initial);
        assertThat(preview.metadata().get("correctionPreview").toString())
                .contains("eligible=false", "input.csv.correction.lock");
        assertThatThrownBy(() -> correction.correct(input, profile, ValidationLanguage.EN, initial))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("already exist");

        assertThat(Files.readString(lock)).isEqualTo("active");
        assertThat(Files.readString(input)).isEqualTo("code\nnot-an-integer\n");
    }

    @Test
    void removesInvalidRowsAndKeepsOriginalRejectedRowsAndAudit(@TempDir Path directory) throws Exception {
        Path profile = profile(directory, "\"correction\":{\"enabled\":true}");
        byte[] original = "code\r\n1\r\nnot-an-integer\r\n2\r\n".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Path input = Files.write(directory.resolve("input.csv"), original);

        ValidationResult initial = validator.validate(
                input, profile, input.getFileName().toString(), ValidationLanguage.EN);
        ValidationResult result = correction.correct(input, profile, ValidationLanguage.EN, initial);

        assertThat(initial.isValid()).isFalse();
        assertThat(result.isValid()).isTrue();
        assertThat(result.metadata()).containsKey("correction");
        assertThat(Files.readString(input)).isEqualTo("code\r\n1\r\n2\r\n");
        assertThat(Files.readAllBytes(directory.resolve("input.csv.original.bak"))).containsExactly(original);
        assertThat(Files.readString(directory.resolve("input.csv.rejected.csv")))
                .isEqualTo("code\r\nnot-an-integer\r\n");

        var audit = mapper.readTree(directory.resolve("input.csv.correction.json").toFile());
        assertThat(audit.path("format").asText()).isEqualTo("zeus-csv-correction-v1");
        assertThat(audit.path("removedRows")).hasSize(1);
        assertThat(audit.path("removedRows").get(0).path("dataRow").asInt()).isEqualTo(2);
        assertThat(audit.path("removedRows").get(0).path("sourceRecord").asInt()).isEqualTo(3);
        assertThat(audit.path("removedRows").get(0).path("findings").get(0).path("instancePath").asText())
                .isEqualTo("/rows/1/code");
        assertThat(audit.path("removedRows").get(0).path("findings").get(0).path("message").asText())
                .contains("Row 3");
        assertThat(validator.validate(input, profile).isValid()).isTrue();
    }

    @Test
    void doesNotChangeValidInputOrInputWithCorrectionDisabled(@TempDir Path directory) throws Exception {
        Path profile = profile(directory, "\"correction\":{\"enabled\":false}");
        Path input = Files.writeString(directory.resolve("input.csv"), "code\nnot-an-integer\n");
        ValidationResult initial = validator.validate(input, profile);

        ValidationResult result = correction.correct(input, profile, ValidationLanguage.DE, initial);

        assertThat(result).isSameAs(initial);
        assertThat(Files.readString(input)).isEqualTo("code\nnot-an-integer\n");
        assertThat(sidecars(directory)).isEmpty();
    }

    @Test
    void leavesRowsForManualRepairWhenAnyFindingIsFileLevel(@TempDir Path directory) throws Exception {
        Path profile = profile(directory,
                "\"fileNamePattern\":\"^expected\\\\.csv$\",\"correction\":{\"enabled\":true}");
        Path input = Files.writeString(directory.resolve("input.csv"), "code\nnot-an-integer\n");
        ValidationResult initial = validator.validate(input, profile);

        ValidationResult result = correction.correct(input, profile, ValidationLanguage.DE, initial);

        assertThat(initial.isValid()).isFalse();
        assertThat(initial.findings()).extracting(finding -> finding.instancePath()).contains("");
        assertThat(result).isSameAs(initial);
        assertThat(Files.readString(input)).isEqualTo("code\nnot-an-integer\n");
        assertThat(sidecars(directory)).isEmpty();
    }

    @Test
    void preservesInputAndCleansStagingFilesWhenCorrectedFileStillViolatesProfile(@TempDir Path directory)
            throws Exception {
        Path profile = profile(directory,
                "\"minRecords\":2,\"correction\":{\"enabled\":true}");
        String original = "code\n1\nnot-an-integer\n";
        Path input = Files.writeString(directory.resolve("input.csv"), original);
        ValidationResult initial = validator.validate(input, profile);

        assertThatThrownBy(() -> correction.correct(input, profile, ValidationLanguage.DE, initial))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("CSV correction failed");

        assertThat(Files.readString(input)).isEqualTo(original);
        assertThat(sidecars(directory)).isEmpty();
    }

    @Test
    void refusesToOverwriteExistingCorrectionArtifacts(@TempDir Path directory) throws Exception {
        Path profile = profile(directory, "\"correction\":{\"enabled\":true}");
        String original = "code\nnot-an-integer\n";
        Path input = Files.writeString(directory.resolve("input.csv"), original);
        Path rejected = Files.writeString(directory.resolve("input.csv.rejected.csv"), "keep this\n");
        ValidationResult initial = validator.validate(input, profile);

        assertThatThrownBy(() -> correction.correct(input, profile, ValidationLanguage.DE, initial))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("already exist");

        assertThat(Files.readString(input)).isEqualTo(original);
        assertThat(Files.readString(rejected)).isEqualTo("keep this\n");
        try (var files = Files.list(directory)) {
            assertThat(files.map(path -> path.getFileName().toString()))
                    .containsExactlyInAnyOrder("profile.json", "input.csv", "input.csv.rejected.csv");
        }
    }

    private static Path profile(Path directory, String extraProperties) throws Exception {
        return Files.writeString(directory.resolve("profile.json"), """
                {
                  "format":"csv", "version":1, "delimiter":",", "encoding":"UTF-8",
                  "header":["code"], "columns":[{"name":"code","type":"integer","required":true}],
                  %s
                }
                """.formatted(extraProperties));
    }

    private static java.util.List<String> sidecars(Path directory) throws Exception {
        try (var files = Files.list(directory)) {
            return files.map(path -> path.getFileName().toString())
                    .filter(name -> name.startsWith("input.csv."))
                    .sorted()
                    .toList();
        }
    }
}
