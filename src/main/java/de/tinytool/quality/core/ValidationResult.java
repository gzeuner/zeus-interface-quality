/*
 * Author: tiny-tool.de
 * Copyright 2026 tiny-tool.de
 * SPDX-License-Identifier: Apache-2.0
 * Licensed under the Apache License, Version 2.0.
 * See LICENSE or https://www.apache.org/licenses/LICENSE-2.0.
 */

package de.tinytool.quality.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The application-level result of a validation run.
 */
public record ValidationResult(Status status, List<Finding> findings, Map<String, Object> metadata) {

    private static final Comparator<Finding> FINDING_ORDER = Comparator
            .comparing(Finding::instancePath)
            .thenComparing(Finding::schemaPath)
            .thenComparing(Finding::keyword)
            .thenComparing(Finding::message);

    public ValidationResult {
        status = Objects.requireNonNull(status, "status must not be null");
        findings = sortFindings(findings);
        metadata = copyMetadata(metadata);
        if (status == Status.VALID && !findings.isEmpty()) {
            throw new IllegalArgumentException("A valid result must not contain findings");
        }
        if (status == Status.INVALID && findings.isEmpty()) {
            throw new IllegalArgumentException("An invalid result must contain findings");
        }
    }

    public ValidationResult(Status status, List<Finding> findings) {
        this(status, findings, Map.of());
    }

    public static ValidationResult valid() {
        return new ValidationResult(Status.VALID, List.of());
    }

    public static ValidationResult invalid(Collection<Finding> findings) {
        return new ValidationResult(Status.INVALID, findings.stream().toList());
    }

    public static ValidationResult valid(Map<String, Object> metadata) {
        return new ValidationResult(Status.VALID, List.of(), metadata);
    }

    public static ValidationResult invalid(Collection<Finding> findings, Map<String, Object> metadata) {
        return new ValidationResult(Status.INVALID, findings.stream().toList(), metadata);
    }

    public boolean isValid() {
        return status == Status.VALID;
    }

    private static List<Finding> sortFindings(List<Finding> findings) {
        Objects.requireNonNull(findings, "findings must not be null");
        var sorted = new ArrayList<>(findings);
        sorted.sort(FINDING_ORDER);
        return List.copyOf(sorted);
    }

    private static Map<String, Object> copyMetadata(Map<String, Object> metadata) {
        Objects.requireNonNull(metadata, "metadata must not be null");
        return Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
    }
}
