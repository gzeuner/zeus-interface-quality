package de.tinytool.quality.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.tinytool.quality.adapter.jsonschema.JsonSchemaValidator;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.Objects;

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
    void returnsTwoForOperationalErrors() {
        ByteArrayOutputStream errors = new ByteArrayOutputStream();
        CommandLine commandLine = commandLine(new ByteArrayOutputStream(), errors);

        int exitCode = commandLine.execute(
                "--schema", "does-not-exist.json",
                "--input", "does-not-exist.json");

        assertThat(exitCode).isEqualTo(2);
        assertThat(errors.toString()).contains("ERROR:");
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
