/*
 * Author: tiny-tool.de
 * Copyright 2026 tiny-tool.de
 * SPDX-License-Identifier: Apache-2.0
 * Licensed under the Apache License, Version 2.0.
 * See LICENSE or https://www.apache.org/licenses/LICENSE-2.0.
 */

package de.tinytool.quality.core;

import java.nio.file.Path;

/**
 * Port used by the CLI to execute a validation without depending on a concrete adapter.
 */
public interface Validator {

    ValidationResult validate(Path input, Path schema) throws ValidationException;

    /** Validates with an explicitly selected finding language. */
    default ValidationResult validate(Path input, Path schema, ValidationLanguage language)
            throws ValidationException {
        return validate(input, schema);
    }
}
