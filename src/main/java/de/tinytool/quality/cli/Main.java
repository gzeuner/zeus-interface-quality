package de.tinytool.quality.cli;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.IVersionProvider;

/**
 * Entry point for the ZEUS Interface Quality command-line application.
 */
@Command(
        name = "zeus-interface-quality",
        mixinStandardHelpOptions = true,
        versionProvider = Main.ManifestVersionProvider.class,
        description = "Technology-agnostic interface quality tool.",
        subcommands = ValidateCommand.class
)
public final class Main implements Runnable {

    static final String FALLBACK_VERSION = "0.1.0-SNAPSHOT";

    public static void main(String[] args) {
        int exitCode = new CommandLine(new Main()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public void run() {
        CommandLine.usage(this, System.out);
    }

    public static final class ManifestVersionProvider implements IVersionProvider {

        @Override
        public String[] getVersion() {
            String version = Main.class.getPackage().getImplementationVersion();
            return new String[] {version == null || version.isBlank() ? FALLBACK_VERSION : version};
        }
    }
}
