/*
 * Author: tiny-tool.de
 * Copyright 2026 tiny-tool.de
 * SPDX-License-Identifier: Apache-2.0
 * Licensed under the Apache License, Version 2.0.
 * See LICENSE or https://www.apache.org/licenses/LICENSE-2.0.
 */

package de.tinytool.quality.adapter.support;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Shared UTF-8 output with source protection and staged replacement.
 */
public final class LocalOutputs {

    private LocalOutputs() {
    }

    public static void protectSources(Path target, Path... sources) throws IOException {
        Path destination = target.toAbsolutePath().normalize();
        for (Path source : sources) {
            if (source != null && (destination.equals(source.toAbsolutePath().normalize())
                    || (Files.exists(destination) && Files.exists(source) && Files.isSameFile(destination, source)))) {
                throw new IOException("Die Eingabe oder das Profil darf nicht durch eine Ausgabe ersetzt werden.");
            }
        }
    }

    public static void write(Path target, String content, Path... sources) throws IOException {
        protectSources(target, sources);
        Path destination = target.toAbsolutePath().normalize();
        if (destination.getParent() == null || Files.isDirectory(destination)) {
            throw new IOException("Das Ausgabeziel muss eine Datei sein: " + destination);
        }
        Path temporary = Files.createTempFile(destination.getParent(), ".zeus-output-", ".tmp");
        try {
            Files.writeString(temporary, content, StandardCharsets.UTF_8);
            try {
                Files.move(temporary, destination,
                        StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
