/*
 * Author: tiny-tool.de
 * Copyright 2026 tiny-tool.de
 * SPDX-License-Identifier: Apache-2.0
 * Licensed under the Apache License, Version 2.0.
 * See LICENSE or https://www.apache.org/licenses/LICENSE-2.0.
 */

package de.tinytool.quality.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;

import java.io.PrintWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.Callable;

/**
 * Validates one local input document against a format-specific local profile.
 */
@Command(
        name = "validate",
        mixinStandardHelpOptions = true,
        description = "Validate a local JSON, CSV, fixed-width, HTTP, or SFTP exchange against a local profile."
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

    @Option(
            names = "--report",
            defaultValue = "TEXT",
            paramLabel = "FORMAT",
            description = "Report format: TEXT or JSON (default: TEXT).",
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
            ValidationLanguage selectedLanguage = ValidationLanguage.from(language);
            ValidationResult result = validator.validate(input, schema, selectedLanguage);
            if (validator instanceof CsvValidator) {
                result = new CsvCorrectionService().correct(input, schema, selectedLanguage, result);
            }
            String report = reportFormat == ReportFormat.JSON
                    ? jsonReportWriter.write(result) + System.lineSeparator()
                    : textReportWriter.write(result);
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
        }
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
