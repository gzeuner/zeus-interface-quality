package de.tinytool.quality.adapter.support;

import de.tinytool.quality.core.ValidationException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Shared local-file checks used by every adapter.
 */
public final class LocalFiles {

    private LocalFiles() {
    }

    public static Path requireRegularFile(Path path, String description) throws ValidationException {
        if (path == null) {
            throw new ValidationException(description + " path is required");
        }
        Path normalized = path.toAbsolutePath().normalize();
        if (!Files.isRegularFile(normalized)) {
            throw new ValidationException(description + " file does not exist: " + normalized);
        }
        try {
            return normalized.toRealPath();
        } catch (IOException e) {
            throw new ValidationException(description + " file cannot be resolved: " + normalized, e);
        }
    }

    public static String safeMessage(Throwable exception) {
        return exception.getMessage() == null
                ? exception.getClass().getSimpleName()
                : exception.getMessage();
    }
}
