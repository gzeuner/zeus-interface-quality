/*
 * Author: tiny-tool.de
 * Copyright 2026 tiny-tool.de
 * SPDX-License-Identifier: Apache-2.0
 * Licensed under the Apache License, Version 2.0.
 * See LICENSE or https://www.apache.org/licenses/LICENSE-2.0.
 */

package de.tinytool.quality.cli;

import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class GuiCommandTest {

    @Test
    void exposesHelpWithoutOpeningAWindow() {
        StringWriter output = new StringWriter();
        CommandLine commandLine = new CommandLine(new Main());
        commandLine.setOut(new PrintWriter(output));

        assertThat(commandLine.execute("gui", "--help")).isZero();
        assertThat(output.toString()).contains("gui", "local desktop");
        assertThat(commandLine.getSubcommands()).containsKeys("validate", "gui");
    }

    @Test
    void returnsAnExplicitErrorWithoutADesktop() throws Exception {
        String executable = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        Process process = new ProcessBuilder(executable, "-Djava.awt.headless=true", "-cp",
                System.getProperty("java.class.path"), Main.class.getName(), "gui")
                .redirectErrorStream(true).start();
        try {
            assertThat(process.waitFor(20, TimeUnit.SECONDS)).isTrue();
            assertThat(process.exitValue()).isEqualTo(2);
            assertThat(new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8))
                    .contains("ERROR:", "validate");
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }
}
