package de.tinytool.quality.cli;

import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import static org.assertj.core.api.Assertions.assertThat;

class MainTest {

    @Test
    void exposesThePlannedCommandName() {
        CommandLine commandLine = new CommandLine(new Main());

        assertThat(commandLine.getCommandName()).isEqualTo("zeus-interface-quality");
    }
}
