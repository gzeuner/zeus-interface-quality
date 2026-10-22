/*
 * Author: tiny-tool.de
 * Copyright 2026 tiny-tool.de
 * SPDX-License-Identifier: Apache-2.0
 * Licensed under the Apache License, Version 2.0.
 * See LICENSE or https://www.apache.org/licenses/LICENSE-2.0.
 */

package de.tinytool.quality.gui;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.tinytool.quality.adapter.csv.CsvValidator;
import de.tinytool.quality.adapter.csv.CsvCorrectionService;
import de.tinytool.quality.adapter.fixedwidth.FixedWidthValidator;
import de.tinytool.quality.adapter.jsonschema.JsonSchemaValidator;
import de.tinytool.quality.adapter.support.LocalFiles;
import de.tinytool.quality.adapter.support.LocalOutputs;
import de.tinytool.quality.adapter.support.ProfileDocuments;
import de.tinytool.quality.core.ValidationException;
import de.tinytool.quality.core.ValidationResult;
import de.tinytool.quality.core.ValidationLanguage;
import de.tinytool.quality.core.Validator;
import de.tinytool.quality.report.JsonReportWriter;
import de.tinytool.quality.report.ReportFormat;
import de.tinytool.quality.report.TextReportWriter;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Desktop workflows deliberately instantiate only local content validators.
 */
public final class LocalValidationService {

    private final ObjectMapper mapper = ProfileDocuments.strictMapper(new ObjectMapper());

    public ValidationResult validate(Path input, Path profile) throws ValidationException {
        return validate(input, profile, ValidationLanguage.DE);
    }

    public ValidationResult validate(Path input, Path profile, ValidationLanguage language) throws ValidationException {
        Path profileFile = LocalFiles.requireRegularFile(profile, "Profil");
        JsonNode root = ProfileDocuments.readRoot(profileFile, mapper, "Profil");
        String format = root == null ? "" : root.path("format").asText("");
        Validator validator = switch (format) {
            case "csv" -> new CsvValidator();
            case "fixed-width" -> new FixedWidthValidator();
            case "http", "sftp" -> throw new ValidationException(
                    "Die GUI prueft nur lokale Dateien. HTTP- und SFTP-Profile sind hier gesperrt.");
            default -> new JsonSchemaValidator();
        };
        Path data = requireDataFile(input);
        ValidationResult result = validator instanceof CsvValidator csv
                ? csv.validate(data, profileFile, data.getFileName().toString(), language)
                : validator instanceof FixedWidthValidator fixed
                    ? fixed.validate(data, profileFile, language)
                    : validator instanceof JsonSchemaValidator json
                        ? json.validate(data, profileFile, language)
                        : validator.validate(data, profileFile);
        return validator instanceof CsvValidator
                ? new CsvCorrectionService().correct(data, profileFile, language, result)
                : result;
    }

    public boolean correctionEnabled(Path profile) throws ValidationException {
        JsonNode root = ProfileDocuments.readRoot(LocalFiles.requireRegularFile(profile, "Profil"), mapper, "Profil");
        return "csv".equals(root.path("format").asText())
                && root.path("correction").path("enabled").asBoolean(false);
    }

    private static Path requireDataFile(Path input) throws ValidationException {
        Path inputFile = LocalFiles.requireRegularFile(input, "Eingabe");
        String fileName = inputFile.getFileName().toString().toLowerCase(Locale.ROOT);
        if (fileName.endsWith(".7z") || fileName.endsWith(".zip")) {
            throw new ValidationException(
                    "Bitte das Archiv zuerst lokal entpacken und die enthaltene Datendatei auswaehlen.");
        }
        return inputFile;
    }

    public void saveReport(
            Path target,
            ValidationResult result,
            ReportFormat format,
            Path input,
            Path profile
    ) throws IOException {
        String content = format == ReportFormat.JSON
                ? new JsonReportWriter().write(result)
                : new TextReportWriter().write(result);
        LocalOutputs.write(target, content, input, profile);
    }
}
