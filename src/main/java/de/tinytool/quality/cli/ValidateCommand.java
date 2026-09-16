package de.tinytool.quality.cli;

import com.fasterxml.jackson.core.JsonProcessingException;
import de.tinytool.quality.adapter.csv.CsvValidator;
import de.tinytool.quality.adapter.fixedwidth.FixedWidthValidator;
import de.tinytool.quality.adapter.jsonschema.JsonSchemaValidator;
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
        description = "Validate a local JSON, CSV, or fixed-width input against a local profile."
)
public final class ValidateCommand implements Callable<Integer> {

    @Option(
            names = "--schema",
            required = true,
            paramLabel = "PATH",
            description = "Path to the local JSON Schema or CSV profile."
    )
    private Path schema;

    @Option(
            names = "--input",
            required = true,
            paramLabel = "PATH",
            description = "Path to the local JSON or CSV input."
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
            defaultValue = "JSON",
            paramLabel = "FORMAT",
            description = "Input format: JSON, CSV, or FIXED-WIDTH (default: JSON).",
            converter = InputFormatConverter.class
    )
    private InputFormat inputFormat;

    @Spec
    private CommandSpec spec;

    private final Validator jsonValidator;
    private final Validator csvValidator;
    private final Validator fixedWidthValidator;
    private final JsonReportWriter jsonReportWriter;
    private final TextReportWriter textReportWriter;

    public ValidateCommand() {
        this(new JsonSchemaValidator(), new CsvValidator(), new FixedWidthValidator());
    }

    ValidateCommand(Validator validator) {
        this(validator, new CsvValidator(), new FixedWidthValidator());
    }

    ValidateCommand(Validator jsonValidator, Validator csvValidator) {
        this(jsonValidator, csvValidator, new FixedWidthValidator());
    }

    ValidateCommand(
            Validator jsonValidator,
            Validator csvValidator,
            Validator fixedWidthValidator
    ) {
        this.jsonValidator = jsonValidator;
        this.csvValidator = csvValidator;
        this.fixedWidthValidator = fixedWidthValidator;
        this.jsonReportWriter = new JsonReportWriter();
        this.textReportWriter = new TextReportWriter();
    }

    @Override
    public Integer call() {
        PrintWriter out = spec.commandLine().getOut();
        PrintWriter err = spec.commandLine().getErr();

        try {
            ValidationResult result = validatorFor(inputFormat).validate(input, schema);
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

    private Validator validatorFor(InputFormat format) {
        return switch (format) {
            case CSV -> csvValidator;
            case FIXED_WIDTH -> fixedWidthValidator;
            case JSON -> jsonValidator;
        };
    }

    enum InputFormat {
        JSON,
        CSV,
        FIXED_WIDTH
    }

    static final class InputFormatConverter implements CommandLine.ITypeConverter<InputFormat> {

        @Override
        public InputFormat convert(String value) {
            return InputFormat.valueOf(value.toUpperCase(Locale.ROOT).replace('-', '_'));
        }
    }

    static final class ReportFormatConverter implements CommandLine.ITypeConverter<ReportFormat> {

        @Override
        public ReportFormat convert(String value) {
            return ReportFormat.valueOf(value.toUpperCase(Locale.ROOT));
        }
    }
}
