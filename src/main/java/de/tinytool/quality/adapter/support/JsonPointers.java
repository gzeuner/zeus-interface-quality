package de.tinytool.quality.adapter.support;

/**
 * JSON Pointer helpers for stable finding paths.
 */
public final class JsonPointers {

    private JsonPointers() {
    }

    public static String escape(String value) {
        return value.replace("~", "~0").replace("/", "~1");
    }
}
