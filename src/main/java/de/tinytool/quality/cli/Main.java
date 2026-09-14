package de.tinytool.quality.cli;

import picocli.CommandLine;
import picocli.CommandLine.Command;

/**
 * Entry point for the ZEUS Interface Quality command-line application.
 */
@Command(
        name = "zeus-interface-quality",
        mixinStandardHelpOptions = true,
        version = "0.1.0-SNAPSHOT",
        description = "Technology-agnostic interface quality tool.",
        subcommands = ValidateCommand.class
)
public final class Main implements Runnable {

    public static void main(String[] args) {
        int exitCode = new CommandLine(new Main()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public void run() {
        CommandLine.usage(this, System.out);
    }
}
