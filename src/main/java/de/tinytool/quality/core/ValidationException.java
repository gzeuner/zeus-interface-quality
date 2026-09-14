package de.tinytool.quality.core;

/**
 * Signals an operational problem while preparing or executing validation.
 */
public class ValidationException extends Exception {

    public ValidationException(String message) {
        super(message);
    }

    public ValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
