package de.tinytool.quality.cli;

import java.util.Locale;

/**
 * Input formats supported by the validate command.
 */
public enum InputFormat {
    AUTO,
    JSON,
    CSV,
    FIXED_WIDTH,
    HTTP;

    public static InputFormat fromCli(String value) {
        return InputFormat.valueOf(value.toUpperCase(Locale.ROOT).replace('-', '_'));
    }
}
