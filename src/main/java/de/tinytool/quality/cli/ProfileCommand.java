/*
 * Author: tiny-tool.de
 * Copyright 2026 tiny-tool.de
 * SPDX-License-Identifier: Apache-2.0
 * Licensed under the Apache License, Version 2.0.
 * See LICENSE or https://www.apache.org/licenses/LICENSE-2.0.
 */

package de.tinytool.quality.cli;

import de.tinytool.quality.adapter.support.BundledProfiles;
import de.tinytool.quality.core.ValidationException;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.Callable;

@Command(name = "profile", mixinStandardHelpOptions = true,
        description = "Export an editable bundled profile for GUI and CLI use.")
public final class ProfileCommand implements Callable<Integer> {

    @Option(names = "--preset", defaultValue = "relex", description = "Bundled profile (default: relex).")
    private String preset;

    @Option(names = "--output", paramLabel = "PATH",
            description = "Write a new UTF-8 profile file; existing files are not overwritten. Default: stdout.")
    private Path output;

    @Spec
    private CommandSpec spec;

    @Override
    public Integer call() {
        try {
            if (!"relex".equalsIgnoreCase(preset)) {
                throw new ValidationException("Unknown preset: " + preset + ". Available: relex.");
            }
            String content = BundledProfiles.relex();
            if (output == null) {
                spec.commandLine().getOut().print(content);
                spec.commandLine().getOut().flush();
            } else {
                Files.writeString(output, content, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
            }
            return 0;
        } catch (ValidationException | IOException e) {
            spec.commandLine().getErr().println("ERROR: " + e.getMessage());
            spec.commandLine().getErr().flush();
            return 2;
        }
    }
}
