/*
 * Author: tiny-tool.de
 * Copyright 2026 tiny-tool.de
 * SPDX-License-Identifier: Apache-2.0
 * Licensed under the Apache License, Version 2.0.
 * See LICENSE or https://www.apache.org/licenses/LICENSE-2.0.
 */

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
