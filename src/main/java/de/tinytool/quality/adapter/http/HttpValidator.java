package de.tinytool.quality.adapter.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.tinytool.quality.adapter.jsonschema.JsonSchemaValidator;
import de.tinytool.quality.adapter.support.LocalFiles;
import de.tinytool.quality.adapter.support.ProfileDocuments;
import de.tinytool.quality.core.Finding;
import de.tinytool.quality.core.ValidationException;
import de.tinytool.quality.core.ValidationResult;
import de.tinytool.quality.core.Validator;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Validates a JSON request and JSON HTTP response against a local HTTP profile.
 */
public final class HttpValidator implements Validator {

    private final ObjectMapper objectMapper;
    private final JsonSchemaValidator jsonSchemaValidator;
    private final HttpClient httpClient;

    public HttpValidator() {
        this(new ObjectMapper(), HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .build());
    }

    HttpValidator(ObjectMapper objectMapper, HttpClient httpClient) {
        this.objectMapper = ProfileDocuments.strictMapper(objectMapper);
        this.jsonSchemaValidator = new JsonSchemaValidator(this.objectMapper);
        this.httpClient = httpClient;
    }

    @Override
    public ValidationResult validate(Path input, Path schema) throws ValidationException {
        Path profileFile = LocalFiles.requireRegularFile(schema, "HTTP profile");
        HttpProfile profile = HttpProfile.read(profileFile, objectMapper);
        Path inputFile = input == null ? null : LocalFiles.requireRegularFile(input, "HTTP request body");
        List<Finding> findings = new ArrayList<>();

        if (profile.requestSchema() != null && inputFile == null) {
            throw new ValidationException("HTTP profile requestSchema requires an input body");
        }

        if (inputFile != null && profile.requestSchema() != null) {
            ValidationResult requestResult = jsonSchemaValidator.validate(inputFile, profile.requestSchema());
            addPrefixedFindings(findings, requestResult.findings(), "/request/body", "/request/schema");
            if (!requestResult.isValid()) {
                return ValidationResult.invalid(findings);
            }
        } else if (inputFile != null) {
            requireJsonRequest(inputFile);
        }

        byte[] requestBody = inputFile == null ? new byte[0] : readBytes(inputFile, "HTTP request body");
        HttpResponse<byte[]> response = send(profile, requestBody);

        if (response.statusCode() != profile.expectedStatus()) {
            findings.add(new Finding(
                    "/response/status",
                    "/expectedStatus",
                    "status",
                    "HTTP-Status " + profile.expectedStatus() + " erwartet, "
                            + response.statusCode() + " erhalten"));
        }

        for (String header : profile.requiredResponseHeaders()) {
            if (response.headers().firstValue(header).isEmpty()) {
                findings.add(new Finding(
                        "/response/headers/" + escapePointer(header),
                        "/requiredResponseHeaders",
                        "required",
                        "HTTP-Response enthält den erforderlichen Header nicht: " + header));
            }
        }

        if (profile.expectedResponseContentType() != null) {
            String actualContentType = response.headers().firstValue("Content-Type").orElse(null);
            if (actualContentType == null
                    || !mediaType(actualContentType).equalsIgnoreCase(
                    profile.expectedResponseContentType())) {
                findings.add(new Finding(
                        "/response/headers/Content-Type",
                        "/responseContentType",
                        "contentType",
                        "HTTP-Response muss Content-Type "
                                + profile.expectedResponseContentType() + " liefern"));
            }
        }

        String responseBody = decodeUtf8(response.body(), findings);
        if (responseBody != null) {
            JsonNode responseNode = parseResponse(responseBody, findings);
            if (responseNode != null) {
                ValidationResult responseResult = jsonSchemaValidator
                        .validateNode(responseNode, profile.responseSchema());
                addPrefixedFindings(
                        findings,
                        responseResult.findings(),
                        "/response/body",
                        "/response/schema");
            }
        }

        return findings.isEmpty() ? ValidationResult.valid() : ValidationResult.invalid(findings);
    }

    private void requireJsonRequest(Path inputFile) throws ValidationException {
        try {
            JsonNode node = objectMapper.readTree(Files.readAllBytes(inputFile));
            if (node == null) {
                throw new ValidationException("HTTP request body is empty");
            }
        } catch (IOException e) {
            throw new ValidationException("Could not read HTTP request body: "
                    + LocalFiles.safeMessage(e), e);
        }
    }

    private static byte[] readBytes(Path inputFile, String description) throws ValidationException {
        try {
            return Files.readAllBytes(inputFile);
        } catch (IOException e) {
            throw new ValidationException("Could not read " + description + ": "
                    + LocalFiles.safeMessage(e), e);
        }
    }

    private HttpResponse<byte[]> send(HttpProfile profile, byte[] requestBody)
            throws ValidationException {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(profile.uri())
                    .timeout(profile.timeout());
            profile.requestHeaders().forEach(builder::header);
            HttpRequest.BodyPublisher body = requestBody.length == 0
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofByteArray(requestBody);
            HttpRequest request = builder.method(profile.method(), body).build();
            return httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ValidationException("HTTP request was interrupted", e);
        } catch (IOException | IllegalArgumentException e) {
            throw new ValidationException("Could not execute HTTP request: "
                    + LocalFiles.safeMessage(e), e);
        }
    }

    private String decodeUtf8(byte[] body, List<Finding> findings) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(body))
                    .toString();
        } catch (CharacterCodingException e) {
            findings.add(new Finding(
                    "/response/body",
                    "/response/schema",
                    "encoding",
                    "HTTP-Response ist kein gültiger UTF-8-Text"));
            return null;
        }
    }

    private JsonNode parseResponse(String body, List<Finding> findings) {
        try {
            JsonNode node = objectMapper.readTree(body);
            if (node == null) {
                findings.add(new Finding(
                        "/response/body",
                        "/response/schema",
                        "json",
                        "HTTP-Response enthält keinen JSON-Datensatz"));
                return null;
            }
            return node;
        } catch (IOException e) {
            findings.add(new Finding(
                    "/response/body",
                    "/response/schema",
                    "json",
                    "HTTP-Response enthält ungültiges JSON"));
            return null;
        }
    }

    private static String mediaType(String value) {
        int separator = value.indexOf(';');
        return (separator < 0 ? value : value.substring(0, separator)).trim();
    }

    private static void addPrefixedFindings(
            List<Finding> target,
            List<Finding> source,
            String instancePrefix,
            String schemaPrefix
    ) {
        for (Finding finding : source) {
            target.add(new Finding(
                    prefix(finding.instancePath(), instancePrefix),
                    prefix(finding.schemaPath(), schemaPrefix),
                    finding.keyword(),
                    finding.message()));
        }
    }

    private static String prefix(String path, String prefix) {
        if (path == null || path.isEmpty()) {
            return prefix;
        }
        return prefix + (path.startsWith("/") ? path : "/" + path);
    }

    private static String escapePointer(String value) {
        return value.replace("~", "~0").replace("/", "~1");
    }
}
