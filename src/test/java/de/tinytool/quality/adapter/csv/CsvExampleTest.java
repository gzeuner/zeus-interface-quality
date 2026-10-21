/*
 * Author: tiny-tool.de
 * Copyright 2026 tiny-tool.de
 * SPDX-License-Identifier: Apache-2.0
 * Licensed under the Apache License, Version 2.0.
 * See LICENSE or https://www.apache.org/licenses/LICENSE-2.0.
 */

package de.tinytool.quality.adapter.csv;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class CsvExampleTest {

    private static final Path CSV = Path.of("docs", "examples", "fictional-bicycle-rentals.csv");
    private static final Path INVALID_CSV = Path.of("docs", "examples", "fictional-bicycle-rentals-invalid.csv");
    private static final Path PROFILE = Path.of("docs", "examples", "fictional-bicycle-rentals.csv-profile.json");

    @Test
    void exampleMatchesItsProfile() throws Exception {
        var result = new CsvValidator().validate(CSV, PROFILE);

        assertThat(result.isValid()).isTrue();
        assertThat(result.findings()).isEmpty();
    }

    @Test
    void exampleProfileDetectsDuplicateRentalId(@TempDir Path directory) throws Exception {
        String duplicate = Files.readString(CSV).replace("BIKE-005", "BIKE-001");
        Path input = Files.writeString(directory.resolve("duplicate.csv"), duplicate);

        var result = new CsvValidator().validate(input, PROFILE);

        assertThat(result.isValid()).isFalse();
        assertThat(result.findings()).anyMatch(finding -> "uniqueBy".equals(finding.keyword()));
    }

    @Test
    void invalidExampleProducesDistinctActionableFindings() throws Exception {
        var result = new CsvValidator().validate(INVALID_CSV, PROFILE);

        assertThat(result.isValid()).isFalse();
        assertThat(result.findings()).hasSize(5);
        assertThat(result.findings()).extracting(finding -> finding.keyword())
                .contains("minimum", "type", "uniqueBy", "pattern");
        assertThat(result.findings()).extracting(finding -> finding.instancePath())
                .contains("/rows/1/durationMinutes", "/rows/2/startDate",
                        "/rows/3", "/rows/3/bikeType", "/rows/3/contactEmail");
    }
}
