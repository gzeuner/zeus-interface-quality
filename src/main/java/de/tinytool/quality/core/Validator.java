package de.tinytool.quality.core;

import java.nio.file.Path;

/**
 * Port used by the CLI to execute a validation without depending on a concrete adapter.
 */
public interface Validator {

    ValidationResult validate(Path input, Path schema) throws ValidationException;
}
