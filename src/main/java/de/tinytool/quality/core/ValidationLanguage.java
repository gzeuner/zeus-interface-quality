package de.tinytool.quality.core;

import java.util.Locale;

/** Languages supported for validation findings and reports. */
public enum ValidationLanguage {
    DE(Locale.GERMAN), EN(Locale.ENGLISH);

    private final Locale locale;

    ValidationLanguage(Locale locale) { this.locale = locale; }

    public Locale locale() { return locale; }

    public static ValidationLanguage from(String value) {
        if (value == null) return DE;
        return switch (value.strip().toLowerCase(Locale.ROOT)) {
            case "de", "deutsch", "german" -> DE;
            case "en", "english" -> EN;
            default -> throw new IllegalArgumentException("Language must be 'de' or 'en'.");
        };
    }
}
