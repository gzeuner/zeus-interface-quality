/*
 * Author: tiny-tool.de
 * Copyright 2026 tiny-tool.de
 * SPDX-License-Identifier: Apache-2.0
 * Licensed under the Apache License, Version 2.0.
 * See LICENSE or https://www.apache.org/licenses/LICENSE-2.0.
 */

package de.tinytool.quality.report;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.tinytool.quality.core.Finding;
import de.tinytool.quality.core.ValidationResult;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

class JsonReportWriterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void writesTheFrozenVersionOneShape() throws Exception {
        ValidationResult result = ValidationResult.invalid(List.of(
                new Finding(
                        "/quantity",
                        "/properties/quantity/type",
                        "type",
                        "string gefunden, integer erwartet")
        ));

        JsonNode report = objectMapper.readTree(new JsonReportWriter().write(result));
        JsonNode expected;
        try (InputStream stream = expectedReport()) {
            expected = objectMapper.readTree(stream);
        }

        assertThat(report).isEqualTo(expected);

        assertThat(report.fieldNames()).toIterable()
                .containsExactly("status", "valid", "findings");
        assertThat(report.get("status").asText()).isEqualTo("INVALID");
        assertThat(report.get("valid").asBoolean()).isFalse();
        assertThat(report.get("findings")).hasSize(1);
        assertThat(report.get("findings").get(0).fieldNames()).toIterable()
                .containsExactly("instancePath", "schemaPath", "keyword", "message");
    }

    @Test
    void writesOptionalOperationalMetadataWithoutChangingTheCoreFields() throws Exception {
        ValidationResult result = ValidationResult.valid(Map.of(
                "deliveryId", "D-100",
                "filesValidated", 2,
                "lifecycle", "ARCHIVED"));

        JsonNode report = objectMapper.readTree(new JsonReportWriter().write(result));

        assertThat(report.get("status").asText()).isEqualTo("VALID");
        assertThat(report.get("valid").asBoolean()).isTrue();
        assertThat(report.get("findings")).isEmpty();
        assertThat(report.get("metadata").get("deliveryId").asText()).isEqualTo("D-100");
        assertThat(report.get("metadata").get("filesValidated").asInt()).isEqualTo(2);
        assertThat(report.get("metadata").get("lifecycle").asText()).isEqualTo("ARCHIVED");
    }

    private static InputStream expectedReport() {
        return Objects.requireNonNull(JsonReportWriterTest.class.getResourceAsStream(
                "/fixtures/expected-invalid-wrong-type-report.json"));
    }
}
