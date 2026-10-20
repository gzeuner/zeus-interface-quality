/*
 * Author: tiny-tool.de
 * Copyright 2026 tiny-tool.de
 * SPDX-License-Identifier: Apache-2.0
 * Licensed under the Apache License, Version 2.0.
 * See LICENSE or https://www.apache.org/licenses/LICENSE-2.0.
 */

package de.tinytool.quality.cli;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.tinytool.quality.adapter.csv.CsvValidator;
import de.tinytool.quality.adapter.fixedwidth.FixedWidthValidator;
import de.tinytool.quality.adapter.http.HttpValidator;
import de.tinytool.quality.adapter.jsonschema.JsonSchemaValidator;
import de.tinytool.quality.adapter.sftp.SftpValidator;
import de.tinytool.quality.core.Validator;

import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Selects the adapter for an explicit or detected input format.
 */
public final class ValidatorRegistry {

    private final Map<InputFormat, Validator> validators = new EnumMap<>(InputFormat.class);
    private final Map<InputFormat, Supplier<Validator>> remoteFactories = new EnumMap<>(InputFormat.class);
    private final ObjectMapper objectMapper;

    public ValidatorRegistry() {
        this(new JsonSchemaValidator(), new CsvValidator(), new FixedWidthValidator());
    }

    public ValidatorRegistry(Validator jsonValidator, Validator csvValidator, Validator fixedWidthValidator) {
        this(jsonValidator, csvValidator, fixedWidthValidator, HttpValidator::new, SftpValidator::new);
    }

    public ValidatorRegistry(
            Validator jsonValidator,
            Validator csvValidator,
            Validator fixedWidthValidator,
            Validator httpValidator
    ) {
        this(jsonValidator, csvValidator, fixedWidthValidator, () -> httpValidator, SftpValidator::new);
    }

    public ValidatorRegistry(
            Validator jsonValidator,
            Validator csvValidator,
            Validator fixedWidthValidator,
            Validator httpValidator,
            Validator sftpValidator
    ) {
        this(jsonValidator, csvValidator, fixedWidthValidator, () -> httpValidator, () -> sftpValidator);
    }

    private ValidatorRegistry(
            Validator jsonValidator,
            Validator csvValidator,
            Validator fixedWidthValidator,
            Supplier<Validator> httpFactory,
            Supplier<Validator> sftpFactory
    ) {
        validators.put(InputFormat.JSON, jsonValidator);
        validators.put(InputFormat.CSV, csvValidator);
        validators.put(InputFormat.FIXED_WIDTH, fixedWidthValidator);
        remoteFactories.put(InputFormat.HTTP, httpFactory);
        remoteFactories.put(InputFormat.SFTP, sftpFactory);
        this.objectMapper = new ObjectMapper();
    }

    public synchronized Validator validatorFor(InputFormat requested, Path profile) {
        InputFormat format = requested == null || requested == InputFormat.AUTO
                ? detect(profile)
                : requested;
        Validator validator = validators.get(format);
        if (validator == null && remoteFactories.containsKey(format)) {
            validator = remoteFactories.get(format).get();
            validators.put(format, validator);
        }
        if (validator == null) {
            throw new IllegalStateException("No validator registered for " + format);
        }
        return validator;
    }

    public InputFormat detect(Path profile) {
        if (profile == null) {
            return InputFormat.JSON;
        }
        try {
            JsonNode root = objectMapper.readTree(profile.toFile());
            if (root != null && root.path("format").isTextual()) {
                String format = root.get("format").asText();
                if ("csv".equals(format)) {
                    return InputFormat.CSV;
                }
                if ("fixed-width".equals(format)) {
                    return InputFormat.FIXED_WIDTH;
                }
                if ("http".equals(format)) {
                    return InputFormat.HTTP;
                }
                if ("sftp".equals(format)) {
                    return InputFormat.SFTP;
                }
            }
        } catch (Exception ignored) {
            // JSON Schema documents and unreadable files fall back to the JSON adapter.
        }
        return InputFormat.JSON;
    }
}
