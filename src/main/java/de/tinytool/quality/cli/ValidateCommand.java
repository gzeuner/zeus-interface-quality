/*
 * Author: tiny-tool.de
 * Copyright 2026 tiny-tool.de
 * SPDX-License-Identifier: Apache-2.0
 * Licensed under the Apache License, Version 2.0.
 * See LICENSE or https://www.apache.org/licenses/LICENSE-2.0.
 */

package de.tinytool.quality.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.tinytool.quality.adapter.csv.CsvValidator;
import de.tinytool.quality.adapter.csv.CsvCorrectionService;
import de.tinytool.quality.adapter.fixedwidth.FixedWidthValidator;
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
import de.tinytool.quality.report.SarifReportWriter;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;

import java.io.PrintWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.file.DirectoryStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.nio.file.FileSystems;
import java.nio.file.PathMatcher;
import java.util.Locale;
import java.util.concurrent.Callable;

/**
 * Validates one local input document against a format-specific local profile.
 */
@Command(
        name = "validate",
        mixinStandardHelpOptions = true,
        description = "Validate local files or an HTTP/SFTP exchange against a local profile."
)
public final class ValidateCommand implements Callable<Integer> {

    @Option(
            names = {"--schema", "--profile"},
            paramLabel = "PATH",
            description = "Path to the local JSON Schema or format profile."
    )
    private Path schema;

    @Option(names = "--report-file", paramLabel = "PATH",
            description = "Also save the report as UTF-8. Parent directory must exist. Never overwrites input/profile.")
    private Path reportFile;

    @Option(names = "--local-only",
            description = "Reject HTTP/SFTP before validation. Suitable for offline build pipelines.")
    private boolean localOnly;

    @Option(
            names = "--input",
            paramLabel = "PATH",
            description = "Path to the local JSON, CSV, fixed-width, or optional HTTP request body."
    )
    private Path input;

    @Option(names = "--input-dir", paramLabel = "DIR",
            description = "Validate matching regular files directly inside this local directory.")
    private Path inputDirectory;

    @Option(names = "--glob", defaultValue = "*", paramLabel = "PATTERN",
            description = "Filename glob used with --input-dir (default: *).")
    private String glob;

    @Option(names = "--dry-run-correction", description = "Preview eligible CSV row removals without changing files.")
    private boolean dryRunCorrection;

    @Option(
            names = "--report",
            defaultValue = "TEXT",
            paramLabel = "FORMAT",
            description = "Report format: TEXT, JSON, or SARIF (default: TEXT).",
            converter = ReportFormatConverter.class
    )
    private ReportFormat reportFormat;

    @Option(
            names = "--input-format",
            defaultValue = "AUTO",
            paramLabel = "FORMAT",
            description = "Input format: AUTO, JSON, CSV, FIXED-WIDTH, HTTP, or SFTP (default: AUTO).",
            converter = InputFormatConverter.class
    )
    private InputFormat inputFormat;

    @Option(names = "--language", defaultValue = "de", paramLabel = "LANGUAGE",
            description = "Finding language: de or en (default: de).")
    private String language = "de";

    @Spec
    private CommandSpec spec;

    private final ValidatorRegistry validators;
    private final JsonReportWriter jsonReportWriter;
    private final TextReportWriter textReportWriter;
    private final SarifReportWriter sarifReportWriter = new SarifReportWriter();

    public ValidateCommand() {
        this(new ValidatorRegistry());
    }

    ValidateCommand(Validator validator) {
        this(new ValidatorRegistry(validator, new CsvValidator(), new FixedWidthValidator()));
    }

    ValidateCommand(Validator jsonValidator, Validator csvValidator) {
        this(new ValidatorRegistry(jsonValidator, csvValidator, new FixedWidthValidator()));
    }

    ValidateCommand(Validator jsonValidator, Validator csvValidator, Validator fixedWidthValidator) {
        this(new ValidatorRegistry(jsonValidator, csvValidator, fixedWidthValidator));
    }

    ValidateCommand(ValidatorRegistry validators) {
        this.validators = validators;
        this.jsonReportWriter = new JsonReportWriter();
        this.textReportWriter = new TextReportWriter();
    }

    @Override
    public Integer call() {
        PrintWriter out = spec.commandLine().getOut();
        PrintWriter err = spec.commandLine().getErr();

        try {
            if (inputDirectory != null) return validateDirectory(out, err);
            if (dryRunCorrection && reportFormat == ReportFormat.SARIF) {
                throw new ValidationException("--dry-run-correction requires TEXT or JSON output.");
            }
            if (schema == null) {
                throw new ValidationException("Specify a local --profile or --schema.");
            }
            if (reportFile != null) {
                LocalOutputs.protectSources(reportFile, input, schema);
            }
            if (localOnly) {
                requireLocalProfile();
            }
            Validator validator = validators.validatorFor(inputFormat, schema);
            InputFormat detectedFormat = inputFormat == InputFormat.AUTO ? validators.detect(schema) : inputFormat;
            if (reportFormat == ReportFormat.SARIF
                    && (detectedFormat == InputFormat.HTTP || detectedFormat == InputFormat.SFTP)) {
                throw new ValidationException("SARIF output requires a local input format.");
            }
            ValidationLanguage selectedLanguage = ValidationLanguage.from(language);
            ValidationResult result = validator.validate(input, schema, selectedLanguage);
            if (dryRunCorrection && !(validator instanceof CsvValidator)) {
                throw new ValidationException("--dry-run-correction applies only to CSV profiles.");
            }
            if (validator instanceof CsvValidator) {
                CsvCorrectionService correction = new CsvCorrectionService();
                result = dryRunCorrection
                        ? correction.preview(input, schema, selectedLanguage, result)
                        : correction.correct(input, schema, selectedLanguage, result);
            }
            String report = switch (reportFormat) {
                case JSON -> jsonReportWriter.write(result) + System.lineSeparator();
                case SARIF -> sarifReportWriter.write(result, input) + System.lineSeparator();
                case TEXT -> textReportWriter.write(result);
            };
            if (reportFile != null) {
                LocalOutputs.write(reportFile, report, input, schema);
            }
            out.print(report);
            out.flush();
            return result.isValid() ? 0 : 1;
        } catch (ValidationException | IOException e) {
            err.println("ERROR: " + e.getMessage());
            err.flush();
            return 2;
        } catch (RuntimeException e) {
            err.println("ERROR: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
            err.flush();
            return 2;
        }
    }

    private Integer validateDirectory(PrintWriter out, PrintWriter err) throws IOException, ValidationException {
        if (schema == null) throw new ValidationException("Specify a local --profile or --schema.");
        if (input != null) throw new ValidationException("Use either --input or --input-dir, not both.");
        if (reportFormat == ReportFormat.SARIF) throw new ValidationException("SARIF output is not supported with --input-dir yet.");
        if (dryRunCorrection) throw new ValidationException("--dry-run-correction cannot be combined with --input-dir.");
        Path directory = LocalFiles.requireDirectory(inputDirectory, "Input directory");
        requireLocalProfile();
        PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + glob);
        List<Path> files = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
            for (Path path : stream) if (Files.isRegularFile(path) && matcher.matches(path.getFileName())) files.add(path);
        }
        files.sort(Comparator.comparing(path -> path.getFileName().toString()));
        if (files.isEmpty()) throw new ValidationException("No regular files match --glob '" + glob + "' in " + directory + ".");
        if (reportFile != null) {
            for (Path path : files) LocalOutputs.protectSources(reportFile, path, schema);
        }
        ValidationLanguage selectedLanguage = ValidationLanguage.from(language);
        var mapper = new ObjectMapper();
        var rows = mapper.createArrayNode();
        boolean anyInvalid = false;
        boolean anyError = false;
        StringBuilder text = new StringBuilder();
        for (Path path : files) {
            try {
                Validator validator = validators.validatorFor(inputFormat, schema);
                ValidationResult result = validator.validate(path, schema, selectedLanguage);
                if (validator instanceof CsvValidator) result = new CsvCorrectionService().correct(path, schema, selectedLanguage, result);
                anyInvalid |= !result.isValid();
                ObjectNode row = rows.addObject();
                row.put("file", path.getFileName().toString());
                row.put("status", result.status().name());
                row.set("findings", mapper.valueToTree(result.findings()));
                if (!result.metadata().isEmpty()) row.set("metadata", mapper.valueToTree(result.metadata()));
                text.append(path.getFileName()).append(System.lineSeparator())
                        .append(textReportWriter.write(result));
            } catch (ValidationException | RuntimeException e) {
                anyError = true;
                ObjectNode row = rows.addObject();
                row.put("file", path.getFileName().toString());
                row.put("status", "ERROR");
                row.put("error", e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
                text.append(path.getFileName()).append(System.lineSeparator()).append("ERROR: ")
                        .append(row.path("error").asText()).append(System.lineSeparator());
            }
        }
        String report;
        if (reportFormat == ReportFormat.JSON) {
            ObjectNode batch = mapper.createObjectNode();
            batch.put("format", "zeus-validation-batch-v1");
            batch.put("status", anyError ? "ERROR" : anyInvalid ? "INVALID" : "VALID");
            batch.put("valid", !anyError && !anyInvalid);
            batch.set("results", rows);
            report = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(batch) + System.lineSeparator();
        } else {
            report = text.append("SUMMARY: ").append(anyError ? "ERROR" : anyInvalid ? "INVALID" : "VALID")
                    .append(" (files=").append(files.size()).append(")").append(System.lineSeparator()).toString();
        }
        if (reportFile != null) LocalOutputs.write(reportFile, report, null, schema);
        out.print(report);
        out.flush();
        return anyError ? 2 : anyInvalid ? 1 : 0;
    }

    private void requireLocalProfile() throws ValidationException {
        var root = ProfileDocuments.readRoot(LocalFiles.requireRegularFile(schema, "Profile"),
                ProfileDocuments.strictMapper(new ObjectMapper()), "profile");
        String format = root == null ? "" : root.path("format").asText("");
        if (inputFormat == InputFormat.HTTP || inputFormat == InputFormat.SFTP
                || "http".equals(format) || "sftp".equals(format)) {
            throw new ValidationException("HTTP/SFTP is disabled by --local-only.");
        }
    }

    static final class InputFormatConverter implements CommandLine.ITypeConverter<InputFormat> {

        @Override
        public InputFormat convert(String value) {
            return InputFormat.fromCli(value);
        }
    }

    static final class ReportFormatConverter implements CommandLine.ITypeConverter<ReportFormat> {

        @Override
        public ReportFormat convert(String value) {
            return ReportFormat.valueOf(value.toUpperCase(Locale.ROOT));
        }
    }
}
