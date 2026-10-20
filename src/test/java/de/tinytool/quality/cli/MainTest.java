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

import static org.assertj.core.api.Assertions.assertThat;

class MainTest {

    @Test
    void exposesThePlannedCommandName() {
        CommandLine commandLine = new CommandLine(new Main());

        assertThat(commandLine.getCommandName()).isEqualTo("zeus-interface-quality");
        assertThat(commandLine.getSubcommands()).containsKey("validate");
    }
}
