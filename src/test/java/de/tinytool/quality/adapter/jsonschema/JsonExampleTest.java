/*
 * Author: tiny-tool.de
 * Copyright 2026 tiny-tool.de
 * SPDX-License-Identifier: Apache-2.0
 * Licensed under the Apache License, Version 2.0.
 * See LICENSE or https://www.apache.org/licenses/LICENSE-2.0.
 */

package de.tinytool.quality.adapter.jsonschema;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class JsonExampleTest {

    private static final Path SCHEMA = Path.of("docs", "examples", "delivery.schema.json");
    private static final Path VALID_JSON = Path.of("docs", "examples", "delivery.json");
    private static final Path INVALID_JSON = Path.of("docs", "examples", "delivery-invalid.json");

    @Test
    void validJsonExampleMatchesItsSchema() throws Exception {
        var result = new JsonSchemaValidator().validate(VALID_JSON, SCHEMA);

        assertThat(result.isValid()).isTrue();
        assertThat(result.findings()).isEmpty();
    }

    @Test
    void invalidJsonExampleShowsSchemaFindings() throws Exception {
        var result = new JsonSchemaValidator().validate(INVALID_JSON, SCHEMA);

        assertThat(result.isValid()).isFalse();
        assertThat(result.findings()).hasSize(3);
        assertThat(result.findings()).extracting(finding -> finding.keyword())
                .contains("minLength", "minimum", "additionalProperties");
    }
}
