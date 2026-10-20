/*
 * Author: tiny-tool.de
 * Copyright 2026 tiny-tool.de
 * SPDX-License-Identifier: Apache-2.0
 * Licensed under the Apache License, Version 2.0.
 * See LICENSE or https://www.apache.org/licenses/LICENSE-2.0.
 */

package de.tinytool.quality.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.tinytool.quality.gui.LocalValidationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import picocli.CommandLine;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

class PipelineCommandTest {

    private final StringWriter output = new StringWriter();
    private final StringWriter errors = new StringWriter();

    private CommandLine cli() {
        return new CommandLine(new Main()).setOut(new PrintWriter(output)).setErr(new PrintWriter(errors));
    }

    @Test
    void usesTheSameLocalProfileInGuiAndCli(@TempDir Path directory) throws Exception {
        Path input = fixture("csv/valid-delivery.csv");
        Path profile = fixture("csv/delivery.csv-profile.json");
        var guiResult = new LocalValidationService().validate(input, profile);
        Path report = directory.resolve("report.json");

        assertThat(cli().execute("validate", "--profile", profile.toString(), "--input", input.toString(),
                "--local-only", "--report", "json", "--report-file", report.toString())).isZero();
        assertThat(guiResult.isValid()).isTrue();
        var mapper = new ObjectMapper();
        assertThat(mapper.readTree(report.toFile())).isEqualTo(mapper.readTree(output.toString()));
        assertThat(errors.toString()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"http", "sftp"})
    void localModeRejectsNetworkProfilesBeforeCallingTransport(String format, @TempDir Path directory) throws Exception {
        Path profile = Files.writeString(directory.resolve("profile.json"), "{\"format\":\"" + format + "\"}");
        int code = cli().execute("validate", "--profile", profile.toString(), "--local-only");
        assertThat(code).isEqualTo(2);
        assertThat(output.toString()).isEmpty();
        assertThat(errors.toString()).contains("--local-only");
    }

    @Test
    void localModeAlsoRejectsExplicitNetworkFormat(@TempDir Path directory) throws Exception {
        int code = cli().execute("validate", "--profile", fixture("delivery.schema.json").toString(),
                "--input-format", "sftp", "--local-only");
        assertThat(code).isEqualTo(2);
        assertThat(output.toString()).isEmpty();
        assertThat(errors.toString()).contains("--local-only");
    }

    @Test
    void outputFailuresReturnTwoWithoutSuccessOutput(@TempDir Path directory) throws Exception {
        Path input = fixture("valid-delivery.json");
        Path profile = fixture("delivery.schema.json");
        Path missingParent = directory.resolve("missing").resolve("report.json");
        int code = cli().execute("validate", "--profile", profile.toString(), "--input", input.toString(),
                "--report-file", missingParent.toString());
        assertThat(code).isEqualTo(2);
        assertThat(output.toString()).isEmpty();
        assertThat(errors.toString()).startsWith("ERROR:");
        assertThat(cli().execute("validate", "--profile", profile.toString(), "--input", input.toString(),
                "--report-file", directory.toString())).isEqualTo(2);
        assertThat(output.toString()).isEmpty();
    }

    @Test
    void protectsInputAndProfileFromReportOutput(@TempDir Path directory) throws Exception {
        Path input = Files.copy(fixture("valid-delivery.json"), directory.resolve("input.json"));
        Path profile = Files.copy(fixture("delivery.schema.json"), directory.resolve("profile.json"));
        for (Path source : new Path[]{input, profile}) {
            String before = Files.readString(source);
            assertThat(cli().execute("validate", "--profile", profile.toString(), "--input", input.toString(),
                    "--report-file", source.toString())).isEqualTo(2);
            assertThat(Files.readString(source)).isEqualTo(before);
        }
        assertThat(output.toString()).isEmpty();
    }

    @Test
    void requiresALocalProfile() {
        assertThat(cli().execute("validate")).isEqualTo(2);
        assertThat(cli().execute("profile")).isEqualTo(2);
        assertThat(output.toString()).isEmpty();
    }

    @Test
    void runsInAHeadlessProcessWithMachineReadableOutput(@TempDir Path directory) throws Exception {
        String executable = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        Path report = directory.resolve("headless.json");
        Path diagnostics = directory.resolve("stderr.txt");
        Process process = new ProcessBuilder(executable, "-Djava.awt.headless=true", "-cp",
                System.getProperty("java.class.path"), Main.class.getName(),
                "validate", "--profile", fixture("delivery.schema.json").toString(),
                "--input", fixture("valid-delivery.json").toString(),
                "--local-only", "--report", "json", "--report-file", report.toString())
                .redirectError(diagnostics.toFile()).start();
        try {
            assertThat(process.waitFor(30, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            assertThat(process.exitValue()).isZero();
            var mapper = new ObjectMapper();
            var stdout = mapper.readTree(process.getInputStream());
            assertThat(stdout.path("status").asText()).isEqualTo("VALID");
            assertThat(stdout).isEqualTo(mapper.readTree(report.toFile()));
            assertThat(Files.readString(diagnostics)).isEmpty();
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    private static Path fixture(String name) throws Exception {
        return Path.of(Objects.requireNonNull(
                PipelineCommandTest.class.getResource("/fixtures/" + name)).toURI());
    }
}
