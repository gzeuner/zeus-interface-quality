package de.tinytool.quality.cli;

import com.fasterxml.jackson.core.JsonProcessingException;
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
 * Validates one local JSON document against one local JSON Schema.
 */
@Command(
        name = "validate",
        mixinStandardHelpOptions = true,
        description = "Validate a local JSON document against a local JSON Schema."
)
public final class ValidateCommand implements Callable<Integer> {

    @Option(
            names = "--schema",
            required = true,
            paramLabel = "PATH",
            description = "Path to the local JSON Schema."
    )
    private Path schema;

    @Option(
            names = "--input",
            required = true,
            paramLabel = "PATH",
            description = "Path to the local JSON document."
    )
    private Path input;

    @Option(
            names = "--report",
            defaultValue = "TEXT",
            paramLabel = "FORMAT",
            description = "Report format: ${COMPLETION-CANDIDATES} (default: ${DEFAULT-VALUE}).",
            converter = ReportFormatConverter.class
    )
    private ReportFormat reportFormat;

    @Spec
    private CommandSpec spec;

    private final Validator validator;
    private final JsonReportWriter jsonReportWriter;
    private final TextReportWriter textReportWriter;

    public ValidateCommand() {
        this(new JsonSchemaValidator());
    }

    ValidateCommand(Validator validator) {
        this.validator = validator;
        this.jsonReportWriter = new JsonReportWriter();
        this.textReportWriter = new TextReportWriter();
    }

    @Override
    public Integer call() {
        PrintWriter out = spec.commandLine().getOut();
        PrintWriter err = spec.commandLine().getErr();

        try {
            ValidationResult result = validator.validate(input, schema);
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

    static final class ReportFormatConverter implements CommandLine.ITypeConverter<ReportFormat> {

        @Override
        public ReportFormat convert(String value) {
            return ReportFormat.valueOf(value.toUpperCase(Locale.ROOT));
        }
    }
}
