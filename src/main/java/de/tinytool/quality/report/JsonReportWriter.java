/*
 * Author: tiny-tool.de
 * Copyright 2026 tiny-tool.de
 * SPDX-License-Identifier: Apache-2.0
 * Licensed under the Apache License, Version 2.0.
 * See LICENSE or https://www.apache.org/licenses/LICENSE-2.0.
 */

package de.tinytool.quality.report;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.tinytool.quality.core.ValidationResult;

/**
 * Serializes the frozen version-one report contract.
 */
public final class JsonReportWriter {

    private final ObjectMapper objectMapper;

    public JsonReportWriter() {
        this(new ObjectMapper());
    }

    public JsonReportWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String write(ValidationResult result) throws JsonProcessingException {
        return objectMapper.writerWithDefaultPrettyPrinter()
                .writeValueAsString(ValidationReport.from(result));
    }
}
