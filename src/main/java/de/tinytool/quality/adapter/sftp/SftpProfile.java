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

/** Version-one local profile for a key-authenticated SFTP file validation. */
record SftpProfile(
        String host,
        int port,
        String username,
        String remotePath,
        Path knownHosts,
        Path privateKey,
        String privateKeyPassphraseEnv,
        String contentFormat,
        Path contentProfile,
        String quarantineRemoteDirectory,
        String expectedSha256,
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
            "connectTimeoutSeconds");
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
        ProfileDocuments.requireVersionOne(root, "SFTP profile");

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

        String remotePath = readRemotePath(
                ProfileDocuments.requiredText(root, "remotePath", "SFTP profile"),
                "SFTP profile remotePath", false);
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

        String quarantineDirectory = ProfileDocuments.optionalText(
                root, "quarantineRemoteDirectory", "SFTP profile");
        if (quarantineDirectory != null) {
            quarantineDirectory = readRemotePath(
                    quarantineDirectory,
                    "SFTP profile quarantineRemoteDirectory",
                    true);
        }

        String expectedSha256 = ProfileDocuments.optionalText(root, "sha256", "SFTP profile");
        if (expectedSha256 != null && !HASH.matcher(expectedSha256).matches()) {
            throw ProfileDocuments.invalid("SFTP profile sha256 must be a 64-character hexadecimal hash");
        }

        int stabilityChecks = bounded(
                root, "stabilityChecks", 2, 1, 5, "SFTP profile");
        int stabilityDelaySeconds = bounded(
                root, "stabilityDelaySeconds", 1, 0, 60, "SFTP profile");
        int maxAttempts = bounded(root, "maxAttempts", 2, 1, 5, "SFTP profile");
        int retryDelaySeconds = bounded(root, "retryDelaySeconds", 1, 0, 60, "SFTP profile");
        int connectTimeoutSeconds = bounded(root, "connectTimeoutSeconds", 10, 1, 120, "SFTP profile");

        return new SftpProfile(
                host,
                port,
                username,
                remotePath,
                knownHosts,
                privateKey,
                passphraseEnv,
                contentFormat,
                contentProfile,
                quarantineDirectory,
                expectedSha256 == null ? null : expectedSha256.toLowerCase(Locale.ROOT),
                stabilityChecks,
                Duration.ofSeconds(stabilityDelaySeconds),
                maxAttempts,
                Duration.ofSeconds(retryDelaySeconds),
                Duration.ofSeconds(connectTimeoutSeconds));
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
