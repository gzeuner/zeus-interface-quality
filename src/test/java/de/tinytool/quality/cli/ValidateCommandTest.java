package de.tinytool.quality.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.tinytool.quality.adapter.jsonschema.JsonSchemaValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.Objects;
import java.util.Locale;

import picocli.CommandLine;

import static org.assertj.core.api.Assertions.assertThat;

class ValidateCommandTest {

    @Test
    void returnsZeroForValidInputAndWritesJsonReport() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ByteArrayOutputStream errors = new ByteArrayOutputStream();
        CommandLine commandLine = commandLine(output, errors);

        int exitCode = commandLine.execute(
                "--schema", fixture("delivery.schema.json").toString(),
                "--input", fixture("valid-delivery.json").toString(),
                "--report", "json");

        assertThat(exitCode).withFailMessage(errors::toString).isZero();
        assertThat(new ObjectMapper().readTree(output.toString()).get("status").asText())
                .isEqualTo("VALID");
    }

    @Test
    void returnsOneForInvalidInput() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ByteArrayOutputStream errors = new ByteArrayOutputStream();
        CommandLine commandLine = commandLine(output, errors);

        int exitCode = commandLine.execute(
                "--schema", fixture("delivery.schema.json").toString(),
                "--input", fixture("invalid-wrong-type.json").toString(),
                "--report", "json");

        assertThat(exitCode).withFailMessage(errors::toString).isEqualTo(1);
        assertThat(new ObjectMapper().readTree(output.toString()).get("status").asText())
                .isEqualTo("INVALID");
    }

    @Test
    void returnsZeroForValidCsvAndWritesJsonReport() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ByteArrayOutputStream errors = new ByteArrayOutputStream();
        CommandLine commandLine = commandLine(output, errors);

        int exitCode = commandLine.execute(
                "--input-format", "csv",
                "--schema", fixture("csv/delivery.csv-profile.json").toString(),
                "--input", fixture("csv/valid-delivery.csv").toString(),
                "--report", "json");

        assertThat(exitCode).withFailMessage(errors::toString).isZero();
        assertThat(new ObjectMapper().readTree(output.toString()).get("status").asText())
                .isEqualTo("VALID");
    }

    @Test
    void returnsOneForInvalidCsv() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ByteArrayOutputStream errors = new ByteArrayOutputStream();
        CommandLine commandLine = commandLine(output, errors);

        int exitCode = commandLine.execute(
                "--input-format", "csv",
                "--schema", fixture("csv/delivery.csv-profile.json").toString(),
                "--input", fixture("csv/invalid-wrong-type.csv").toString(),
                "--report", "json");

        assertThat(exitCode).withFailMessage(errors::toString).isEqualTo(1);
        assertThat(new ObjectMapper().readTree(output.toString()).get("status").asText())
                .isEqualTo("INVALID");
    }

    @Test
    void autoDetectsCsvFromProfile() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ByteArrayOutputStream errors = new ByteArrayOutputStream();
        CommandLine commandLine = commandLine(output, errors);

        int exitCode = commandLine.execute(
                "--schema", fixture("csv/delivery.csv-profile.json").toString(),
                "--input", fixture("csv/valid-delivery.csv").toString(),
                "--report", "json");

        assertThat(exitCode).withFailMessage(errors::toString).isZero();
        assertThat(new ObjectMapper().readTree(output.toString()).get("status").asText())
                .isEqualTo("VALID");
    }

    @Test
    void returnsZeroForValidFixedWidthInput() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ByteArrayOutputStream errors = new ByteArrayOutputStream();
        CommandLine commandLine = commandLine(output, errors);

        int exitCode = commandLine.execute(
                "--input-format", "fixed-width",
                "--schema", fixture("fixedwidth/delivery.cobol-profile.json").toString(),
                "--input", fixture("fixedwidth/valid-delivery.dat").toString(),
                "--report", "json");

        assertThat(exitCode).withFailMessage(errors::toString).isZero();
        assertThat(new ObjectMapper().readTree(output.toString()).get("status").asText())
                .isEqualTo("VALID");
    }

    @Test
    void returnsOneForInvalidFixedWidthInput() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ByteArrayOutputStream errors = new ByteArrayOutputStream();
        CommandLine commandLine = commandLine(output, errors);

        int exitCode = commandLine.execute(
                "--input-format", "fixed-width",
                "--schema", fixture("fixedwidth/delivery.cobol-profile.json").toString(),
                "--input", fixture("fixedwidth/invalid-wrong-type.dat").toString(),
                "--report", "json");

        assertThat(exitCode).withFailMessage(errors::toString).isEqualTo(1);
        assertThat(new ObjectMapper().readTree(output.toString()).get("status").asText())
                .isEqualTo("INVALID");
    }

    @Test
    void autoDetectsFixedWidthFromProfile() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ByteArrayOutputStream errors = new ByteArrayOutputStream();
        CommandLine commandLine = commandLine(output, errors);

        int exitCode = commandLine.execute(
                "--schema", fixture("fixedwidth/delivery.cobol-profile.json").toString(),
                "--input", fixture("fixedwidth/valid-delivery.dat").toString(),
                "--report", "json");

        assertThat(exitCode).withFailMessage(errors::toString).isZero();
        assertThat(new ObjectMapper().readTree(output.toString()).get("status").asText())
                .isEqualTo("VALID");
    }

    @Test
    void returnsTwoForMalformedCsvWithoutPrintingAReport() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ByteArrayOutputStream errors = new ByteArrayOutputStream();
        CommandLine commandLine = commandLine(output, errors);

        int exitCode = commandLine.execute(
                "--input-format", "csv",
                "--schema", fixture("csv/delivery.csv-profile.json").toString(),
                "--input", fixture("csv/malformed.csv").toString(),
                "--report", "json");

        assertThat(exitCode).isEqualTo(2);
        assertThat(output.toString()).isEmpty();
        assertThat(errors.toString()).startsWith("ERROR:")
                .doesNotContain("\tat ");
    }

    @Test
    void actualReportMatchesFrozenExampleOnAnEnglishHost() throws Exception {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.US);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            ByteArrayOutputStream errors = new ByteArrayOutputStream();
            CommandLine cli = new CommandLine(new Main())
                    .setOut(new PrintWriter(output, true))
                    .setErr(new PrintWriter(errors, true));
            int exitCode = cli.execute("validate",
                    "--schema", fixture("delivery.schema.json"),
                    "--input", fixture("invalid-wrong-type.json"), "--report", "json");

            ObjectMapper mapper = new ObjectMapper();
            assertThat(exitCode).withFailMessage(errors::toString).isEqualTo(1);
            assertThat(mapper.readTree(output.toString()))
                    .isEqualTo(mapper.readTree(Path.of(fixture("expected-invalid-wrong-type-report.json")).toFile()));
            assertThat(errors.toString()).isEmpty();
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    void returnsTwoForOperationalErrors() {
        ByteArrayOutputStream errors = new ByteArrayOutputStream();
        CommandLine commandLine = commandLine(new ByteArrayOutputStream(), errors);

        int exitCode = commandLine.execute(
                "--schema", "does-not-exist.json",
                "--input", "does-not-exist.json");

        assertThat(exitCode).isEqualTo(2);
        assertThat(errors.toString()).contains("ERROR:");
    }

    @Test
    void returnsTwoForMalformedJsonWithoutPrintingAStacktrace() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ByteArrayOutputStream errors = new ByteArrayOutputStream();
        CommandLine commandLine = commandLine(output, errors);

        int exitCode = commandLine.execute(
                "--schema", fixture("delivery.schema.json").toString(),
                "--input", fixture("malformed.json").toString());

        assertThat(exitCode).isEqualTo(2);
        assertThat(output.toString()).isEmpty();
        assertThat(errors.toString()).contains("ERROR:")
                .doesNotContain("\tat ")
                .doesNotContain("Exception");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "{} {}", "{\"id\":1,\"id\":2}"})
    void parsingFailuresProduceExitTwoAndNoReport(String json, @TempDir Path directory) throws Exception {
        Path input = Files.writeString(directory.resolve("input.json"), json);
        Path schema = Files.writeString(directory.resolve("schema.json"), "true");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ByteArrayOutputStream errors = new ByteArrayOutputStream();
        int code = commandLine(output, errors).execute(
                "--schema", schema.toString(), "--input", input.toString(), "--report", "json");

        assertThat(code).isEqualTo(2);
        assertThat(output.toString()).isEmpty();
        assertThat(errors.toString()).startsWith("ERROR:").doesNotContain("\tat ");
    }

    private static CommandLine commandLine(ByteArrayOutputStream output, ByteArrayOutputStream errors) {
        return new CommandLine(new ValidateCommand(new JsonSchemaValidator()))
                .setOut(new PrintWriter(output, true))
                .setErr(new PrintWriter(errors, true));
    }

    private static String fixture(String name) throws Exception {
        return Path.of(Objects.requireNonNull(
                ValidateCommandTest.class.getResource("/fixtures/" + name)).toURI()).toString();
    }
}
