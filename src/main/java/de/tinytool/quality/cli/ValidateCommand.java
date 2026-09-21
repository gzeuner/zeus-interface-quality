package de.tinytool.quality.cli;

import com.fasterxml.jackson.core.JsonProcessingException;
import de.tinytool.quality.adapter.csv.CsvValidator;
import de.tinytool.quality.adapter.fixedwidth.FixedWidthValidator;
import de.tinytool.quality.adapter.http.HttpValidator;
import de.tinytool.quality.core.ValidationException;
import de.tinytool.quality.core.ValidationResult;
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
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.Callable;

/**
 * Validates one local input document against a format-specific local profile.
 */
@Command(
        name = "validate",
        mixinStandardHelpOptions = true,
        description = "Validate a local JSON, CSV, fixed-width, or HTTP exchange against a local profile."
)
public final class ValidateCommand implements Callable<Integer> {

    @Option(
            names = {"--schema", "--profile"},
            required = true,
            paramLabel = "PATH",
            description = "Path to the local JSON Schema or format profile."
    )
    private Path schema;

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
            description = "Input format: AUTO, JSON, CSV, FIXED-WIDTH, or HTTP (default: AUTO).",
            converter = InputFormatConverter.class
    )
    private InputFormat inputFormat;

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
        this(new ValidatorRegistry(jsonValidator, csvValidator, fixedWidthValidator, new HttpValidator()));
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
            ValidationResult result = validators.validatorFor(inputFormat, schema).validate(input, schema);
            if (reportFormat == ReportFormat.JSON) {
                out.println(jsonReportWriter.write(result));
            } else {
                out.print(textReportWriter.write(result));
            }
            out.flush();
            return result.isValid() ? 0 : 1;
        } catch (ValidationException | JsonProcessingException e) {
            err.println("ERROR: " + e.getMessage());
            err.flush();
            return 2;
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
