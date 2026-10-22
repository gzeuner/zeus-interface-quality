/*
 * Author: tiny-tool.de
 * Copyright 2026 tiny-tool.de
 * SPDX-License-Identifier: Apache-2.0
 * Licensed under the Apache License, Version 2.0.
 * See LICENSE or https://www.apache.org/licenses/LICENSE-2.0.
 */

package de.tinytool.quality.gui;

import java.util.Locale;
import de.tinytool.quality.core.ValidationLanguage;

enum UiLanguage {
    DE("Deutsch", Locale.GERMAN),
    EN("English", Locale.ENGLISH);

    private final String label;
    private final Locale locale;

    UiLanguage(String label, Locale locale) {
        this.label = label;
        this.locale = locale;
    }

    String text(String german, String english) {
        return this == DE ? german : english;
    }

    Locale locale() {
        return locale;
    }

    ValidationLanguage validationLanguage() {
        return this == DE ? ValidationLanguage.DE : ValidationLanguage.EN;
    }

    @Override
    public String toString() {
        return label;
    }
}
