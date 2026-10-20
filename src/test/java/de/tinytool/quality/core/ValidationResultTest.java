/*
 * Author: tiny-tool.de
 * Copyright 2026 tiny-tool.de
 * SPDX-License-Identifier: Apache-2.0
 * Licensed under the Apache License, Version 2.0.
 * See LICENSE or https://www.apache.org/licenses/LICENSE-2.0.
 */

package de.tinytool.quality.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ValidationResultTest {

    @Test
    void sortsFindingsDeterministicallyAndKeepsThemImmutable() {
        Finding later = new Finding("/z", "/properties/z/type", "type", "wrong type");
        Finding earlier = new Finding("/a", "/properties/a/type", "type", "wrong type");

        ValidationResult result = ValidationResult.invalid(List.of(later, earlier));

        assertThat(result.findings()).containsExactly(earlier, later);
        assertThatThrownBy(() -> result.findings().add(later))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
