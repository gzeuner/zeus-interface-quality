package de.tinytool.quality.report;

import de.tinytool.quality.core.Finding;
import de.tinytool.quality.core.ValidationResult;

/**
 * Human-readable report for local terminal use.
 */
public final class TextReportWriter {

    public String write(ValidationResult result) {
        StringBuilder output = new StringBuilder(result.status().name()).append(System.lineSeparator());
        for (Finding finding : result.findings()) {
            output.append("- ")
                    .append(finding.instancePath())
                    .append(" [")
                    .append(finding.keyword())
                    .append("] ")
                    .append(finding.message())
                    .append(System.lineSeparator());
        }
        return output.toString();
    }
}
