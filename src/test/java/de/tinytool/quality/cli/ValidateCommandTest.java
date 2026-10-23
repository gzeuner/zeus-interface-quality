/*
 * Author: tiny-tool.de
 * Copyright 2026 tiny-tool.de
 * SPDX-License-Identifier: Apache-2.0
 * Licensed under the Apache License, Version 2.0.
 * See LICENSE or https://www.apache.org/licenses/LICENSE-2.0.
 */

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
    void emitsSarifForSingleInput(@TempDir Path directory) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ByteArrayOutputStream errors = new ByteArrayOutputStream();
        Path report = directory.resolve("findings.sarif");
        int code = commandLine(output, errors).execute("--schema", fixture("delivery.schema.json").toString(),
                "--input", fixture("invalid-wrong-type.json").toString(), "--report", "sarif",
                "--report-file", report.toString());
        assertThat(code).isEqualTo(1);
        var sarif = new ObjectMapper().readTree(report.toFile());
        assertThat(sarif.path("version").asText()).isEqualTo("2.1.0");
        assertThat(sarif.at("/runs/0/tool/driver/name").asText()).isEqualTo("ZEUS Interface Quality");
        assertThat(sarif.at("/runs/0/results/0/ruleId").asText()).isEqualTo("type");
        assertThat(sarif.at("/runs/0/results/0/properties/instancePath").asText()).isEqualTo("/quantity");
    }

    @Test
    void validatesDirectoryInSortedOrderAndAggregatesResults(@TempDir Path directory) throws Exception {
        Path inputs = Files.createDirectory(directory.resolve("inputs"));
        Files.copy(Path.of(fixture("csv/valid-delivery.csv")), inputs.resolve("b-valid.csv"));
        Files.copy(Path.of(fixture("csv/invalid-wrong-type.csv")), inputs.resolve("a-invalid.csv"));
        Path profile = Files.writeString(directory.resolve("profile.json"), """
                {"format":"csv","version":1,"delimiter":";","encoding":"UTF-8",
                 "header":["deliveryId","quantity","email"],"columns":[
                 {"name":"deliveryId","type":"string","required":true},
                 {"name":"quantity","type":"integer","required":true},
                 {"name":"email","type":"email","required":true}]}
                """);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ByteArrayOutputStream errors = new ByteArrayOutputStream();
        Path report = directory.resolve("batch.json");
        int code = commandLine(output, errors).execute("--profile", profile.toString(), "--input-dir",
                inputs.toString(), "--glob", "*.csv", "--local-only", "--report", "json",
                "--report-file", report.toString());
        assertThat(code).isEqualTo(1);
        var batch = new ObjectMapper().readTree(report.toFile());
        assertThat(batch.path("format").asText()).isEqualTo("zeus-validation-batch-v1");
        assertThat(batch.path("status").asText()).isEqualTo("INVALID");
        assertThat(batch.path("results").size()).isEqualTo(2);
        assertThat(batch.at("/results/0/file").asText()).isEqualTo("a-invalid.csv");
        assertThat(batch.at("/results/1/file").asText()).isEqualTo("b-valid.csv");
    }

    @Test
    void batchContinuesAfterFileOperationalError(@TempDir Path directory) throws Exception {
        Path inputs = Files.createDirectory(directory.resolve("inputs"));
        Files.writeString(inputs.resolve("a-malformed.csv"), "\"unterminated\n");
        Files.copy(Path.of(fixture("csv/valid-delivery.csv")), inputs.resolve("b-valid.csv"));
        Path profile = Files.writeString(directory.resolve("profile.json"), """
                {"format":"csv","version":1,"delimiter":";","encoding":"UTF-8",
                 "header":["deliveryId","quantity","email"],"columns":[
                 {"name":"deliveryId","type":"string","required":true},
                 {"name":"quantity","type":"integer","required":true},
                 {"name":"email","type":"email","required":true}]}
                """);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ByteArrayOutputStream errors = new ByteArrayOutputStream();
        int code = commandLine(output, errors).execute("--profile", profile.toString(), "--input-dir",
                inputs.toString(), "--local-only", "--report", "json");
        assertThat(code).isEqualTo(2);
        var batch = new ObjectMapper().readTree(output.toString());
        assertThat(batch.path("status").asText()).isEqualTo("ERROR");
        assertThat(batch.at("/results/0/status").asText()).isEqualTo("ERROR");
        assertThat(batch.at("/results/1/status").asText()).isEqualTo("VALID");

        ByteArrayOutputStream sarifOutput = new ByteArrayOutputStream();
        int sarifCode = commandLine(sarifOutput, new ByteArrayOutputStream()).execute("--profile",
                profile.toString(), "--input-dir", inputs.toString(), "--local-only", "--report", "sarif");
        var sarif = new ObjectMapper().readTree(sarifOutput.toString());
        assertThat(sarifCode).isEqualTo(2);
        assertThat(sarif.at("/runs/0/properties/status").asText()).isEqualTo("ERROR");
        assertThat(sarif.at("/runs/0/results/0/ruleId").asText()).isEqualTo("zeus.operationalError");
    }

    @Test
    void dryRunCorrectionReportsPreviewAndLeavesCsvUntouched(@TempDir Path directory) throws Exception {
        Path input = Files.writeString(directory.resolve("input.csv"), "code\n1\nnot-an-integer\n2\n");
        String original = Files.readString(input);
        Path profile = Files.writeString(directory.resolve("profile.json"), """
                {"format":"csv","version":1,"delimiter":",","encoding":"UTF-8",
                 "header":["code"],"columns":[{"name":"code","type":"integer","required":true}],
                 "correction":{"enabled":true}}
                """);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ByteArrayOutputStream errors = new ByteArrayOutputStream();

        int code = commandLine(output, errors).execute("--profile", profile.toString(), "--input",
                input.toString(), "--dry-run-correction", "--report", "json");

        assertThat(code).isEqualTo(1);
        assertThat(new ObjectMapper().readTree(output.toString()).at("/metadata/correctionPreview/removedRecords").asInt())
                .isEqualTo(1);
        assertThat(Files.readString(input)).isEqualTo(original);
        assertThat(Files.exists(directory.resolve("input.csv.original.bak"))).isFalse();
        assertThat(Files.exists(directory.resolve("input.csv.rejected.csv"))).isFalse();
        assertThat(Files.exists(directory.resolve("input.csv.correction.json"))).isFalse();
    }

    @Test
    void recursiveBatchIncludesNestedFilesAndReportsRelativePaths(@TempDir Path directory) throws Exception {
        Path inputs = Files.createDirectory(directory.resolve("inputs"));
        Path nested = Files.createDirectory(inputs.resolve("nested"));
        Files.copy(Path.of(fixture("csv/valid-delivery.csv")), inputs.resolve("z-valid.csv"));
        Files.copy(Path.of(fixture("csv/valid-delivery.csv")), nested.resolve("a-valid.csv"));
        Path profile = csvProfile(directory, false);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ByteArrayOutputStream errors = new ByteArrayOutputStream();

        int code = commandLine(output, errors).execute("--profile", profile.toString(), "--input-dir",
                inputs.toString(), "--glob", "*.csv", "--recursive", "--local-only", "--report", "json");

        var batch = new ObjectMapper().readTree(output.toString());
        assertThat(code).isZero();
        assertThat(batch.path("results").size()).isEqualTo(2);
        assertThat(batch.at("/results/0/relativePath").asText()).isEqualTo("nested/a-valid.csv");
        assertThat(batch.at("/results/1/relativePath").asText()).isEqualTo("z-valid.csv");
    }

    @Test
    void batchCorrectionPreviewDoesNotModifyInputs(@TempDir Path directory) throws Exception {
        Path inputs = Files.createDirectory(directory.resolve("inputs"));
        Path input = Files.copy(Path.of(fixture("csv/invalid-wrong-type.csv")), inputs.resolve("invalid.csv"));
        byte[] original = Files.readAllBytes(input);
        Path profile = csvProfile(directory, true);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ByteArrayOutputStream errors = new ByteArrayOutputStream();

        int code = commandLine(output, errors).execute("--profile", profile.toString(), "--input-dir",
                inputs.toString(), "--glob", "*.csv", "--local-only", "--dry-run-correction", "--report", "json");

        var batch = new ObjectMapper().readTree(output.toString());
        assertThat(code).isEqualTo(1);
        assertThat(batch.at("/results/0/metadata/correctionPreview/removedRecords").asInt()).isEqualTo(1);
        assertThat(Files.readAllBytes(input)).containsExactly(original);
        assertThat(Files.exists(inputs.resolve("invalid.csv.original.bak"))).isFalse();
        assertThat(Files.exists(inputs.resolve("invalid.csv.rejected.csv"))).isFalse();
        assertThat(Files.exists(inputs.resolve("invalid.csv.correction.json"))).isFalse();
    }

    @Test
    void sarifBatchReportsFindingsWithPerFileLocations(@TempDir Path directory) throws Exception {
        Path inputs = Files.createDirectory(directory.resolve("inputs"));
        Files.copy(Path.of(fixture("csv/invalid-wrong-type.csv")), inputs.resolve("invalid.csv"));
        Files.copy(Path.of(fixture("csv/valid-delivery.csv")), inputs.resolve("valid.csv"));
        Path profile = csvProfile(directory, false);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ByteArrayOutputStream errors = new ByteArrayOutputStream();

        int code = commandLine(output, errors).execute("--profile", profile.toString(), "--input-dir",
                inputs.toString(), "--glob", "*.csv", "--local-only", "--report", "sarif");

        var sarif = new ObjectMapper().readTree(output.toString());
        assertThat(code).isEqualTo(1);
        assertThat(sarif.path("runs").size()).isEqualTo(1);
        assertThat(sarif.at("/runs/0/properties/status").asText()).isEqualTo("INVALID");
        assertThat(sarif.at("/runs/0/results/0/locations/0/physicalLocation/artifactLocation/uri")
                .asText()).endsWith("/invalid.csv");
    }

    private static Path csvProfile(Path directory, boolean correction) throws Exception {
        String correctionSetting = correction ? ",\"correction\":{\"enabled\":true}" : "";
        return Files.writeString(directory.resolve("profile.json"), """
                {"format":"csv","version":1,"delimiter":";","encoding":"UTF-8",
                 "header":["deliveryId","quantity","email"],"columns":[
                 {"name":"deliveryId","type":"string","required":true},
                 {"name":"quantity","type":"integer","required":true},
                 {"name":"email","type":"email","required":true}]%s}
                """.formatted(correctionSetting));
    }

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
    void correctsCsvWhenEnabledInProfileAndReportsTheSuccessfulResult(@TempDir Path directory) throws Exception {
        Path profile = Files.writeString(directory.resolve("profile.json"), """
                {"format":"csv","version":1,"delimiter":",","encoding":"UTF-8",
                 "header":["code"],"columns":[{"name":"code","type":"integer","required":true}],
                 "correction":{"enabled":true}}
                """);
        Path input = Files.writeString(directory.resolve("input.csv"), "code\n1\nnot-an-integer\n2\n");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ByteArrayOutputStream errors = new ByteArrayOutputStream();

        int exitCode = commandLine(output, errors).execute(
                "--input-format", "csv", "--schema", profile.toString(), "--input", input.toString(),
                "--report", "json", "--language", "en");

        var report = new ObjectMapper().readTree(output.toString());
        assertThat(exitCode).withFailMessage(errors::toString).isZero();
        assertThat(report.path("status").asText()).isEqualTo("VALID");
        assertThat(report.path("metadata").path("correction").path("removedRecords").asInt()).isEqualTo(1);
        assertThat(Files.readString(input)).isEqualTo("code\r\n1\r\n2\r\n");
        assertThat(Files.exists(directory.resolve("input.csv.original.bak"))).isTrue();
        assertThat(Files.exists(directory.resolve("input.csv.rejected.csv"))).isTrue();
        assertThat(Files.exists(directory.resolve("input.csv.correction.json"))).isTrue();
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
    void returnsTwoForMalformedCsvWithoutPrintingAReport(@TempDir Path directory) throws Exception {
        Path malformedCsv = directory.resolve("valid-delivery.csv");
        Files.copy(Path.of(fixture("csv/malformed.csv")), malformedCsv);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ByteArrayOutputStream errors = new ByteArrayOutputStream();
        CommandLine commandLine = commandLine(output, errors);

        int exitCode = commandLine.execute(
                "--input-format", "csv",
                "--schema", fixture("csv/delivery.csv-profile.json").toString(),
                "--input", malformedCsv.toString(),
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
