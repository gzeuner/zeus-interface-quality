package de.tinytool.quality.report;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import de.tinytool.quality.core.Finding;
import de.tinytool.quality.core.Status;
import de.tinytool.quality.core.ValidationResult;

import java.util.List;

/**
 * Version-one report contract for machine-readable validation results.
 */
@JsonPropertyOrder({"status", "valid", "findings"})
public record ValidationReport(
        Status status,
        boolean valid,
        List<Finding> findings
) {

    public static ValidationReport from(ValidationResult result) {
        return new ValidationReport(result.status(), result.isValid(), result.findings());
    }
}
