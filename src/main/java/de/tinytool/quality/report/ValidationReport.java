/*
 * Author: tiny-tool.de
 * Copyright 2026 tiny-tool.de
 * SPDX-License-Identifier: Apache-2.0
 * Licensed under the Apache License, Version 2.0.
 * See LICENSE or https://www.apache.org/licenses/LICENSE-2.0.
 */

package de.tinytool.quality.report;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonInclude;
import de.tinytool.quality.core.Finding;
import de.tinytool.quality.core.Status;
import de.tinytool.quality.core.ValidationResult;

import java.util.List;

/**
 * Version-one report contract for machine-readable validation results.
 */
@JsonPropertyOrder({"status", "valid", "findings", "metadata"})
public record ValidationReport(
        Status status,
        boolean valid,
        List<Finding> findings,
        @JsonInclude(JsonInclude.Include.NON_EMPTY) java.util.Map<String, Object> metadata
) {

    public ValidationReport(Status status, boolean valid, List<Finding> findings) {
        this(status, valid, findings, java.util.Map.of());
    }

    public static ValidationReport from(ValidationResult result) {
        return new ValidationReport(result.status(), result.isValid(), result.findings(), result.metadata());
    }
}
