/*
 * Author: tiny-tool.de
 * Copyright 2026 tiny-tool.de
 * SPDX-License-Identifier: Apache-2.0
 * Licensed under the Apache License, Version 2.0.
 * See LICENSE or https://www.apache.org/licenses/LICENSE-2.0.
 */

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
