/*
 * Author: tiny-tool.de
 * Copyright 2026 tiny-tool.de
 * SPDX-License-Identifier: Apache-2.0
 * Licensed under the Apache License, Version 2.0.
 * See LICENSE or https://www.apache.org/licenses/LICENSE-2.0.
 */

package de.tinytool.quality.adapter.support;

import de.tinytool.quality.adapter.csv.CsvValidator;
import de.tinytool.quality.core.ValidationException;
import de.tinytool.quality.core.ValidationResult;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class BundledProfiles {

    private BundledProfiles() {
    }

    public static boolean hasRelex() {
        return BundledProfiles.class.getResource("/profiles/pos-relex.csv-profile.json") != null;
    }

    public static String relex() throws ValidationException {
        try (InputStream resource = BundledProfiles.class.getResourceAsStream("/profiles/pos-relex.csv-profile.json")) {
            if (resource == null) {
                throw new ValidationException("Das mitgelieferte Relex-Profil fehlt. Bitte neu paketieren.");
            }
            return new String(resource.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new ValidationException("Relex-Profil konnte nicht gelesen werden: " + LocalFiles.safeMessage(e), e);
        }
    }

    public static ValidationResult validateRelex(Path input) throws ValidationException {
        String content = relex();
        try {
            Path profile = Files.createTempFile("zeus-relex-", ".json");
            try {
                Files.writeString(profile, content, StandardCharsets.UTF_8);
                return new CsvValidator().validate(input, profile);
            } finally {
                Files.delete(profile);
            }
        } catch (IOException e) {
            throw new ValidationException("Relex-Profil konnte nicht bereitgestellt oder entfernt werden: "
                    + LocalFiles.safeMessage(e), e);
        }
    }
}
