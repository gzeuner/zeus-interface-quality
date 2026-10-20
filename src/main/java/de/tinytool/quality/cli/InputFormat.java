/*
 * Author: tiny-tool.de
 * Copyright 2026 tiny-tool.de
 * SPDX-License-Identifier: Apache-2.0
 * Licensed under the Apache License, Version 2.0.
 * See LICENSE or https://www.apache.org/licenses/LICENSE-2.0.
 */

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
    HTTP,
    SFTP;

    public static InputFormat fromCli(String value) {
        return InputFormat.valueOf(value.toUpperCase(Locale.ROOT).replace('-', '_'));
    }
}
