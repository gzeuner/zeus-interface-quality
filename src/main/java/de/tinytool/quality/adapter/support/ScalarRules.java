/*
 * Author: tiny-tool.de
 * Copyright 2026 tiny-tool.de
 * SPDX-License-Identifier: Apache-2.0
 * Licensed under the Apache License, Version 2.0.
 * See LICENSE or https://www.apache.org/licenses/LICENSE-2.0.
 */

package de.tinytool.quality.adapter.support;

import java.math.BigDecimal;
import java.util.regex.Pattern;

/**
 * Shared field constraints used by tabular adapters.
 */
public record ScalarRules(
        boolean required,
        Integer minLength,
        Integer maxLength,
        BigDecimal minimum,
        BigDecimal maximum,
        Pattern pattern
) {
}
