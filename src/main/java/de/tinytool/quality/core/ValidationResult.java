package de.tinytool.quality.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * The application-level result of a validation run.
 */
public record ValidationResult(Status status, List<Finding> findings) {

    private static final Comparator<Finding> FINDING_ORDER = Comparator
            .comparing(Finding::instancePath)
            .thenComparing(Finding::schemaPath)
            .thenComparing(Finding::keyword)
            .thenComparing(Finding::message);

    public ValidationResult {
        status = Objects.requireNonNull(status, "status must not be null");
        findings = sortFindings(findings);
        if (status == Status.VALID && !findings.isEmpty()) {
            throw new IllegalArgumentException("A valid result must not contain findings");
        }
        if (status == Status.INVALID && findings.isEmpty()) {
            throw new IllegalArgumentException("An invalid result must contain findings");
        }
    }

    public static ValidationResult valid() {
        return new ValidationResult(Status.VALID, List.of());
    }

    public static ValidationResult invalid(Collection<Finding> findings) {
        return new ValidationResult(Status.INVALID, findings.stream().toList());
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
}
