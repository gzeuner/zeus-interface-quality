package de.tinytool.quality.adapter.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.tinytool.quality.adapter.support.LocalFiles;
import de.tinytool.quality.adapter.support.ProfileDocuments;
import de.tinytool.quality.core.ValidationException;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.time.Duration;
import java.nio.file.InvalidPathException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Version-one profile for a JSON request and JSON HTTP response.
 *
 * <p>Schema paths are deliberately relative to the profile directory. The
 * adapter is a transport boundary, not a general-purpose HTTP client: it does
 * not follow redirects and never prints request headers or response bodies.</p>
 */
record HttpProfile(
        String method,
        URI uri,
        Map<String, String> requestHeaders,
        Path requestSchema,
        int expectedStatus,
        List<String> requiredResponseHeaders,
        String expectedResponseContentType,
        Path responseSchema,
        Duration timeout
) {

    private static final Set<String> PROFILE_PROPERTIES = Set.of(
            "$id", "$schema", "format", "version", "method", "url",
            "requestHeaders", "requestSchema", "expectedStatus",
            "requiredResponseHeaders", "responseContentType", "responseSchema",
            "timeoutSeconds");
    private static final Pattern HEADER_NAME = Pattern.compile("^[!#$%&'*+.^_`|~0-9A-Za-z-]+$");
    private static final Set<String> METHODS = Set.of("GET", "POST", "PUT", "PATCH", "DELETE");
    private static final int DEFAULT_TIMEOUT_SECONDS = 10;
    private static final int MAX_TIMEOUT_SECONDS = 120;

    static HttpProfile read(Path profileFile, ObjectMapper sourceMapper) throws ValidationException {
        ObjectMapper mapper = ProfileDocuments.strictMapper(sourceMapper);
        JsonNode root = ProfileDocuments.readRoot(profileFile, mapper, "HTTP profile");

        ProfileDocuments.requireObject(root, "HTTP profile");
        ProfileDocuments.rejectUnknownProperties(root, PROFILE_PROPERTIES, "HTTP profile");
        if (!"http".equals(ProfileDocuments.requiredText(root, "format", "HTTP profile"))) {
            throw ProfileDocuments.invalid("HTTP profile format must be 'http'");
        }
        ProfileDocuments.requireVersionOne(root, "HTTP profile");

        String method = ProfileDocuments.requiredText(root, "method", "HTTP profile")
                .toUpperCase(Locale.ROOT);
        if (!METHODS.contains(method)) {
            throw ProfileDocuments.invalid("HTTP profile method must be one of " + METHODS);
        }

        URI uri = readUri(ProfileDocuments.requiredText(root, "url", "HTTP profile"));
        Map<String, String> requestHeaders = readHeaders(
                root.get("requestHeaders"), "HTTP profile requestHeaders");
        Path requestSchema = optionalContainedFile(
                profileFile, root.get("requestSchema"), "requestSchema", "HTTP request schema");

        int expectedStatus = ProfileDocuments.requiredPositiveInteger(
                root, "expectedStatus", "HTTP profile");
        if (expectedStatus < 100 || expectedStatus > 599) {
            throw ProfileDocuments.invalid("HTTP profile expectedStatus must be between 100 and 599");
        }

        List<String> requiredResponseHeaders = readHeaderNames(
                root.get("requiredResponseHeaders"), "HTTP profile requiredResponseHeaders");
        String responseContentType = ProfileDocuments.optionalText(
                root, "responseContentType", "HTTP profile");
        Path responseSchema = requiredContainedFile(
                profileFile, root, "responseSchema", "HTTP response schema");

        Integer timeoutSeconds = ProfileDocuments.optionalNonNegativeInteger(
                root, "timeoutSeconds", "HTTP profile");
        int timeout = timeoutSeconds == null ? DEFAULT_TIMEOUT_SECONDS : timeoutSeconds;
        if (timeout < 1 || timeout > MAX_TIMEOUT_SECONDS) {
            throw ProfileDocuments.invalid("HTTP profile timeoutSeconds must be between 1 and "
                    + MAX_TIMEOUT_SECONDS);
        }

        return new HttpProfile(
                method,
                uri,
                requestHeaders,
                requestSchema,
                expectedStatus,
                requiredResponseHeaders,
                responseContentType,
                responseSchema,
                Duration.ofSeconds(timeout));
    }

    private static URI readUri(String value) throws ValidationException {
        try {
            URI uri = new URI(value);
            String scheme = uri.getScheme();
            if (!("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
                throw ProfileDocuments.invalid("HTTP profile url must use http or https");
            }
            if (uri.getHost() == null || uri.getUserInfo() != null || uri.getFragment() != null) {
                throw ProfileDocuments.invalid(
                        "HTTP profile url must contain a host and must not contain credentials or a fragment");
            }
            return uri;
        } catch (URISyntaxException e) {
            throw new ValidationException("HTTP profile url is not a valid URI", e);
        }
    }

    private static Map<String, String> readHeaders(JsonNode node, String context)
            throws ValidationException {
        if (node == null) {
            return Map.of();
        }
        ProfileDocuments.requireObject(node, context);
        Map<String, String> headers = new HashMap<>();
        Set<String> normalized = new HashSet<>();
        var fields = node.fields();
        while (fields.hasNext()) {
            var entry = fields.next();
            validateHeaderName(entry.getKey(), context);
            if (!normalized.add(entry.getKey().toLowerCase(Locale.ROOT))) {
                throw ProfileDocuments.invalid(context + " contains duplicate header names");
            }
            JsonNode value = entry.getValue();
            if (!value.isTextual() || value.textValue().contains("\r")
                    || value.textValue().contains("\n")) {
                throw ProfileDocuments.invalid(context + " values must be single-line strings");
            }
            headers.put(entry.getKey(), value.textValue());
        }
        return Map.copyOf(headers);
    }

    private static List<String> readHeaderNames(JsonNode node, String context)
            throws ValidationException {
        if (node == null) {
            return List.of();
        }
        if (!node.isArray()) {
            throw ProfileDocuments.invalid(context + " must be an array");
        }
        List<String> headers = new ArrayList<>();
        Set<String> normalized = new HashSet<>();
        for (JsonNode value : node) {
            if (!value.isTextual() || value.textValue().isBlank()) {
                throw ProfileDocuments.invalid(context + " must contain non-empty header names");
            }
            validateHeaderName(value.textValue(), context);
            String key = value.textValue().toLowerCase(Locale.ROOT);
            if (!normalized.add(key)) {
                throw ProfileDocuments.invalid(context + " contains duplicate header names");
            }
            headers.add(value.textValue());
        }
        return List.copyOf(headers);
    }

    private static void validateHeaderName(String name, String context) throws ValidationException {
        if (!HEADER_NAME.matcher(name).matches()) {
            throw ProfileDocuments.invalid(context + " contains an invalid header name");
        }
    }

    private static Path requiredContainedFile(
            Path profileFile,
            JsonNode root,
            String property,
            String description
    ) throws ValidationException {
        return optionalContainedFile(
                profileFile,
                ProfileDocuments.required(root, property, "HTTP profile"),
                property,
                description);
    }

    private static Path optionalContainedFile(
            Path profileFile,
            JsonNode node,
            String property,
            String description
    ) throws ValidationException {
        if (node == null) {
            return null;
        }
        if (!node.isTextual() || node.textValue().isBlank()) {
            throw ProfileDocuments.invalid("HTTP profile property '" + property
                    + "' must be a non-empty relative path");
        }
        final Path relative;
        try {
            relative = Path.of(node.textValue());
        } catch (InvalidPathException e) {
            throw ProfileDocuments.invalid("HTTP profile property '" + property
                    + "' must be a valid relative path");
        }
        if (relative.isAbsolute()) {
            throw ProfileDocuments.invalid("HTTP profile property '" + property
                    + "' must be relative to the profile");
        }
        try {
            Path base = profileFile.getParent().toRealPath();
            Path candidate = base.resolve(relative).normalize();
            if (!candidate.startsWith(base)) {
                throw ProfileDocuments.invalid("HTTP profile " + property
                        + " must stay inside the profile directory");
            }
            Path real = candidate.toRealPath();
            if (!real.startsWith(base)) {
                throw ProfileDocuments.invalid("HTTP profile " + property
                        + " resolves outside the profile directory");
            }
            return LocalFiles.requireRegularFile(real, description);
        } catch (IOException e) {
            throw new ValidationException(description + " cannot be resolved: " + e.getMessage(), e);
        }
    }

}
