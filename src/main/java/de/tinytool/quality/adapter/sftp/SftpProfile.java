/*
 * Author: tiny-tool.de
 * Copyright 2026 tiny-tool.de
 * SPDX-License-Identifier: Apache-2.0
 * Licensed under the Apache License, Version 2.0.
 * See LICENSE or https://www.apache.org/licenses/LICENSE-2.0.
 */

package de.tinytool.quality.adapter.sftp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.tinytool.quality.adapter.support.LocalFiles;
import de.tinytool.quality.adapter.support.ProfileDocuments;
import de.tinytool.quality.core.ValidationException;

import java.io.IOException;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/** Versioned local profile for key-authenticated SFTP validation. */
record SftpProfile(
        int version,
        String host,
        int port,
        String username,
        String remotePath,
        String remoteDirectory,
        Pattern fileNamePattern,
        Pattern companionFileNamePattern,
        Pattern deliveryIdPattern,
        Path knownHosts,
        Path privateKey,
        String privateKeyPassphraseEnv,
        String contentFormat,
        Path contentProfile,
        String companionContentFormat,
        Path companionContentProfile,
        String quarantineRemoteDirectory,
        String claimRemoteDirectory,
        String archiveRemoteDirectory,
        String expectedSha256,
        String companionSha256,
        int stabilityChecks,
        Duration stabilityDelay,
        int maxAttempts,
        Duration retryDelay,
        Duration connectTimeout
) {

    private static final Set<String> PROFILE_PROPERTIES = Set.of(
            "$id", "$schema", "format", "version", "host", "port", "username",
            "remotePath", "knownHosts", "privateKey", "privateKeyPassphraseEnv",
            "contentFormat", "contentProfile", "quarantineRemoteDirectory", "sha256",
            "stabilityChecks", "stabilityDelaySeconds", "maxAttempts", "retryDelaySeconds",
            "connectTimeoutSeconds", "remoteDirectory", "filePattern", "companionPattern",
            "deliveryIdPattern", "claimRemoteDirectory", "archiveRemoteDirectory",
            "companionContentFormat", "companionContentProfile", "companionSha256");
    private static final Pattern HASH = Pattern.compile("[0-9a-fA-F]{64}");
    private static final Pattern ENVIRONMENT_NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    static SftpProfile read(Path profileFile, ObjectMapper sourceMapper)
            throws ValidationException {
        ObjectMapper mapper = ProfileDocuments.strictMapper(sourceMapper);
        JsonNode root = ProfileDocuments.readRoot(profileFile, mapper, "SFTP profile");

        ProfileDocuments.requireObject(root, "SFTP profile");
        ProfileDocuments.rejectUnknownProperties(root, PROFILE_PROPERTIES, "SFTP profile");
        if (!"sftp".equals(ProfileDocuments.requiredText(root, "format", "SFTP profile"))) {
            throw ProfileDocuments.invalid("SFTP profile format must be 'sftp'");
        }
        int version = ProfileDocuments.requiredPositiveInteger(root, "version", "SFTP profile");
        if (version < 1 || version > 4) {
            throw ProfileDocuments.invalid("Unsupported SFTP profile version: " + version);
        }

        String host = readHost(ProfileDocuments.requiredText(root, "host", "SFTP profile"));
        Integer configuredPort = ProfileDocuments.optionalNonNegativeInteger(root, "port", "SFTP profile");
        int port = configuredPort == null ? 22 : configuredPort;
        if (port < 1 || port > 65535) {
            throw ProfileDocuments.invalid("SFTP profile port must be between 1 and 65535");
        }

        String username = ProfileDocuments.requiredText(root, "username", "SFTP profile");
        if (containsWhitespace(username)) {
            throw ProfileDocuments.invalid("SFTP profile username must not contain whitespace");
        }

        String remotePath = null;
        String remoteDirectory = null;
        Pattern fileNamePattern = null;
        Pattern companionFileNamePattern = null;
        Pattern deliveryIdPattern = null;
        if (version == 1) {
            if (root.has("remoteDirectory") || root.has("filePattern") || root.has("companionPattern")
                    || root.has("deliveryIdPattern") || root.has("claimRemoteDirectory")
                    || root.has("archiveRemoteDirectory")) {
                throw ProfileDocuments.invalid(
                        "SFTP profile version 1 requires remotePath and does not support directory selection");
            }
            remotePath = readRemotePath(
                    ProfileDocuments.requiredText(root, "remotePath", "SFTP profile"),
                    "SFTP profile remotePath", false);
        } else {
            if (root.has("remotePath")) {
                throw ProfileDocuments.invalid(
                        "SFTP profile version " + version
                                + " requires remoteDirectory and does not support remotePath");
            }
            remoteDirectory = readRemotePath(
                    ProfileDocuments.requiredText(root, "remoteDirectory", "SFTP profile"),
                    "SFTP profile remoteDirectory", true);
            fileNamePattern = readFileNamePattern(root);
            if (version == 2 && (root.has("companionPattern") || root.has("deliveryIdPattern")
                    || root.has("claimRemoteDirectory") || root.has("archiveRemoteDirectory"))) {
                throw ProfileDocuments.invalid(
                        "SFTP profile version 2 does not support correlated delivery lifecycle fields");
            }
            if (version >= 3) {
                companionFileNamePattern = optionalFileNamePattern(root, "companionPattern");
                deliveryIdPattern = optionalDeliveryIdPattern(root);
                if (companionFileNamePattern != null && deliveryIdPattern == null) {
                    throw ProfileDocuments.invalid(
                            "SFTP profile version " + version
                                    + " requires deliveryIdPattern with companionPattern");
                }
            }
        }
        Path knownHosts = requiredContainedFile(
                profileFile, root, "knownHosts", "SFTP known_hosts file");
        Path privateKey = requiredContainedFile(
                profileFile, root, "privateKey", "SFTP private key");

        String passphraseEnv = ProfileDocuments.optionalText(
                root, "privateKeyPassphraseEnv", "SFTP profile");
        if (passphraseEnv != null && !ENVIRONMENT_NAME.matcher(passphraseEnv).matches()) {
            throw ProfileDocuments.invalid(
                    "SFTP profile privateKeyPassphraseEnv must be a valid environment variable name");
        }

        String contentFormat = ProfileDocuments.requiredText(
                root, "contentFormat", "SFTP profile").toLowerCase(Locale.ROOT);
        if (!Set.of("json", "csv", "fixed-width").contains(contentFormat)) {
            throw ProfileDocuments.invalid(
                    "SFTP profile contentFormat must be json, csv, or fixed-width");
        }
        Path contentProfile = requiredContainedFile(
                profileFile, root, "contentProfile", "SFTP content profile");

        String companionContentFormat = null;
        Path companionContentProfile = null;
        if (version == 4) {
            if (companionFileNamePattern == null) {
                throw ProfileDocuments.invalid(
                        "SFTP profile version 4 requires companionPattern");
            }
            companionContentFormat = requiredContentFormat(root, "companionContentFormat");
            companionContentProfile = requiredContainedFile(
                    profileFile, root, "companionContentProfile", "SFTP companion content profile");
        } else if (root.has("companionContentFormat") || root.has("companionContentProfile")
                || root.has("companionSha256")) {
            throw ProfileDocuments.invalid(
                    "companionContentFormat, companionContentProfile, and companionSha256 "
                            + "require SFTP profile version 4");
        }

        String quarantineDirectory = ProfileDocuments.optionalText(
                root, "quarantineRemoteDirectory", "SFTP profile");
        if (quarantineDirectory != null) {
            quarantineDirectory = readRemotePath(
                    quarantineDirectory,
                    "SFTP profile quarantineRemoteDirectory",
                    true);
        }

        String claimDirectory = optionalRemoteDirectory(
                root, "claimRemoteDirectory", "SFTP profile claimRemoteDirectory");
        String archiveDirectory = optionalRemoteDirectory(
                root, "archiveRemoteDirectory", "SFTP profile archiveRemoteDirectory");
        if (version < 3 && (claimDirectory != null || archiveDirectory != null)) {
            throw ProfileDocuments.invalid(
                    "claimRemoteDirectory and archiveRemoteDirectory require SFTP profile version 3 or later");
        }
        if ((claimDirectory == null) != (archiveDirectory == null)) {
            throw ProfileDocuments.invalid(
                    "SFTP profile version " + version
                            + " requires claimRemoteDirectory and archiveRemoteDirectory together");
        }

        String expectedSha256 = optionalHash(root, "sha256");
        String companionSha256 = version == 4 ? optionalHash(root, "companionSha256") : null;

        int stabilityChecks = bounded(
                root, "stabilityChecks", 2, 1, 5, "SFTP profile");
        int stabilityDelaySeconds = bounded(
                root, "stabilityDelaySeconds", 1, 0, 60, "SFTP profile");
        int maxAttempts = bounded(root, "maxAttempts", 2, 1, 5, "SFTP profile");
        int retryDelaySeconds = bounded(root, "retryDelaySeconds", 1, 0, 60, "SFTP profile");
        int connectTimeoutSeconds = bounded(root, "connectTimeoutSeconds", 10, 1, 120, "SFTP profile");

        return new SftpProfile(
                version,
                host,
                port,
                username,
                remotePath,
                remoteDirectory,
                fileNamePattern,
                companionFileNamePattern,
                deliveryIdPattern,
                knownHosts,
                privateKey,
                passphraseEnv,
                contentFormat,
                contentProfile,
                companionContentFormat,
                companionContentProfile,
                quarantineDirectory,
                claimDirectory,
                archiveDirectory,
                expectedSha256 == null ? null : expectedSha256.toLowerCase(Locale.ROOT),
                companionSha256 == null ? null : companionSha256.toLowerCase(Locale.ROOT),
                stabilityChecks,
                Duration.ofSeconds(stabilityDelaySeconds),
                maxAttempts,
                Duration.ofSeconds(retryDelaySeconds),
                Duration.ofSeconds(connectTimeoutSeconds));
    }

    boolean directorySelection() {
        return version >= 2;
    }

    boolean correlatedDelivery() {
        return companionFileNamePattern != null;
    }

    boolean lifecycleEnabled() {
        return claimRemoteDirectory != null;
    }

    Path contentProfileFor(String role) {
        return role.equals("companion") && companionContentProfile != null
                ? companionContentProfile : contentProfile;
    }

    String expectedSha256For(String role) {
        return role.equals("companion") ? companionSha256 : expectedSha256;
    }

    private static String requiredContentFormat(JsonNode root, String property)
            throws ValidationException {
        String value = ProfileDocuments.requiredText(root, property, "SFTP profile")
                .toLowerCase(Locale.ROOT);
        if (!Set.of("json", "csv", "fixed-width").contains(value)) {
            throw ProfileDocuments.invalid("SFTP profile " + property
                    + " must be json, csv, or fixed-width");
        }
        return value;
    }

    private static String optionalHash(JsonNode root, String property) throws ValidationException {
        String value = ProfileDocuments.optionalText(root, property, "SFTP profile");
        if (value != null && !HASH.matcher(value).matches()) {
            throw ProfileDocuments.invalid("SFTP profile " + property
                    + " must be a 64-character hexadecimal hash");
        }
        return value;
    }

    private static Pattern readFileNamePattern(JsonNode root) throws ValidationException {
        String expression = ProfileDocuments.requiredText(root, "filePattern", "SFTP profile");
        return compileFileNamePattern(expression, "filePattern");
    }

    private static Pattern optionalFileNamePattern(JsonNode root, String property)
            throws ValidationException {
        String expression = ProfileDocuments.optionalText(root, property, "SFTP profile");
        return expression == null ? null : compileFileNamePattern(expression, property);
    }

    private static Pattern compileFileNamePattern(String expression, String property)
            throws ValidationException {
        if (expression.contains("/") || expression.indexOf('\0') >= 0) {
            throw ProfileDocuments.invalid(
                    "SFTP profile " + property
                            + " must match a file name and must not contain path separators");
        }
        try {
            return Pattern.compile(expression);
        } catch (PatternSyntaxException e) {
            throw new ValidationException("SFTP profile " + property
                    + " contains an invalid regular expression: "
                    + e.getDescription(), e);
        }
    }

    private static Pattern optionalDeliveryIdPattern(JsonNode root) throws ValidationException {
        String expression = ProfileDocuments.optionalText(root, "deliveryIdPattern", "SFTP profile");
        if (expression == null) {
            return null;
        }
        if (!expression.contains("(?<deliveryId>")) {
            throw ProfileDocuments.invalid(
                    "SFTP profile deliveryIdPattern must contain one named capture group 'deliveryId'");
        }
        try {
            return Pattern.compile(expression);
        } catch (PatternSyntaxException e) {
            throw new ValidationException(
                    "SFTP profile deliveryIdPattern contains an invalid regular expression: "
                            + e.getDescription(), e);
        }
    }

    private static String optionalRemoteDirectory(
            JsonNode root, String property, String context) throws ValidationException {
        String value = ProfileDocuments.optionalText(root, property, "SFTP profile");
        return value == null ? null : readRemotePath(value, context, true);
    }

    private static int bounded(
            JsonNode root,
            String property,
            int defaultValue,
            int minimum,
            int maximum,
            String context
    ) throws ValidationException {
        Integer value = ProfileDocuments.optionalNonNegativeInteger(root, property, context);
        int result = value == null ? defaultValue : value;
        if (result < minimum || result > maximum) {
            throw ProfileDocuments.invalid(context + " " + property + " must be between "
                    + minimum + " and " + maximum);
        }
        return result;
    }

    private static String readHost(String value) throws ValidationException {
        if (containsWhitespace(value) || value.contains("/") || value.contains("\\")
                || value.indexOf('\0') >= 0) {
            throw ProfileDocuments.invalid("SFTP profile host must be a plain host name or address");
        }
        return value;
    }

    private static String readRemotePath(String value, String context, boolean allowRoot)
            throws ValidationException {
        if (!value.startsWith("/") || value.indexOf('\0') >= 0 || value.contains("\\")
                || value.length() > 1 && value.endsWith("/")) {
            throw ProfileDocuments.invalid(context + " must be an absolute POSIX path");
        }
        if (value.length() == 1 && !allowRoot) {
            throw ProfileDocuments.invalid(context + " must identify a file");
        }
        for (String segment : value.split("/", -1)) {
            if (segment.equals(".") || segment.equals("..")) {
                throw ProfileDocuments.invalid(context + " must not contain '.' or '..' segments");
            }
        }
        return value;
    }

    private static boolean containsWhitespace(String value) {
        return value.chars().anyMatch(Character::isWhitespace);
    }

    private static Path requiredContainedFile(
            Path profileFile,
            JsonNode root,
            String property,
            String description
    ) throws ValidationException {
        JsonNode node = ProfileDocuments.required(root, property, "SFTP profile");
        if (!node.isTextual() || node.textValue().isBlank()) {
            throw ProfileDocuments.invalid("SFTP profile property '" + property
                    + "' must be a non-empty relative path");
        }

        final Path relative;
        try {
            relative = Path.of(node.textValue());
        } catch (InvalidPathException e) {
            throw ProfileDocuments.invalid("SFTP profile property '" + property
                    + "' must be a valid relative path");
        }
        if (relative.isAbsolute()) {
            throw ProfileDocuments.invalid("SFTP profile property '" + property
                    + "' must be relative to the profile");
        }

        try {
            Path base = profileFile.toRealPath().getParent();
            Path candidate = base.resolve(relative).normalize();
            if (!candidate.startsWith(base)) {
                throw ProfileDocuments.invalid("SFTP profile " + property
                        + " must stay inside the profile directory");
            }
            Path real = candidate.toRealPath();
            if (!real.startsWith(base)) {
                throw ProfileDocuments.invalid("SFTP profile " + property
                        + " resolves outside the profile directory");
            }
            return LocalFiles.requireRegularFile(real, description);
        } catch (IOException e) {
            throw new ValidationException("SFTP profile " + property
                    + " cannot be resolved: " + LocalFiles.safeMessage(e), e);
        }
    }
}
