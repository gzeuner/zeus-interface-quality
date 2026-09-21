package de.tinytool.quality.adapter.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import de.tinytool.quality.cli.Main;
import de.tinytool.quality.core.Status;
import de.tinytool.quality.core.ValidationException;
import de.tinytool.quality.core.ValidationResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HttpValidatorTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void validatesRequestAndResponseThroughTheHttpAdapter(@TempDir Path directory) throws Exception {
        AtomicReference<String> receivedBody = new AtomicReference<>();
        HttpServer server = server(exchange -> {
            receivedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
            exchange.getResponseHeaders().add("X-Request-Id", "test-request");
            respond(exchange, 201, "{\"accepted\":true}");
        });

        try {
            Path profile = writeProfile(directory, server, 201);
            Path request = Files.writeString(directory.resolve("request.json"),
                    "{\"deliveryId\":\"D-100\",\"quantity\":2}");

            ValidationResult result = new HttpValidator().validate(request, profile);

            assertThat(result.status()).isEqualTo(Status.VALID);
            assertThat(receivedBody).hasValue("{\"deliveryId\":\"D-100\",\"quantity\":2}");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void invalidRequestIsReportedWithoutSendingIt(@TempDir Path directory) throws Exception {
        AtomicInteger calls = new AtomicInteger();
        HttpServer server = server(exchange -> {
            calls.incrementAndGet();
            respond(exchange, 201, "{\"accepted\":true}");
        });

        try {
            Path profile = writeProfile(directory, server, 201);
            Path request = Files.writeString(directory.resolve("request.json"),
                    "{\"deliveryId\":\"D-100\"}");

            ValidationResult result = new HttpValidator().validate(request, profile);

            assertThat(result.status()).isEqualTo(Status.INVALID);
            assertThat(result.findings()).anySatisfy(finding -> {
                assertThat(finding.instancePath()).isEqualTo("/request/body");
                assertThat(finding.keyword()).isEqualTo("required");
            });
            assertThat(calls.get()).isZero();
        } finally {
            server.stop(0);
        }
    }

    @Test
    void reportsStatusHeadersAndResponseSchemaFindings(@TempDir Path directory) throws Exception {
        HttpServer server = server(exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "text/plain");
            respond(exchange, 500, "{\"accepted\":\"yes\"}");
        });

        try {
            Path profile = writeProfile(directory, server, 201);
            Path request = Files.writeString(directory.resolve("request.json"),
                    "{\"deliveryId\":\"D-100\",\"quantity\":2}");

            ValidationResult result = new HttpValidator().validate(request, profile);

            assertThat(result.status()).isEqualTo(Status.INVALID);
            assertThat(result.findings()).extracting(finding -> finding.keyword())
                    .contains("status", "required", "contentType", "type");
            assertThat(result.findings()).anySatisfy(finding ->
                    assertThat(finding.instancePath()).isEqualTo("/response/body/accepted"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void malformedResponseIsAContractFinding(@TempDir Path directory) throws Exception {
        HttpServer server = server(exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            respond(exchange, 201, "{\"accepted\":");
        });

        try {
            Path profile = writeProfile(directory, server, 201);
            Path request = Files.writeString(directory.resolve("request.json"),
                    "{\"deliveryId\":\"D-100\",\"quantity\":2}");

            ValidationResult result = new HttpValidator().validate(request, profile);

            assertThat(result.status()).isEqualTo(Status.INVALID);
            assertThat(result.findings()).anySatisfy(finding -> {
                assertThat(finding.instancePath()).isEqualTo("/response/body");
                assertThat(finding.keyword()).isEqualTo("json");
            });
        } finally {
            server.stop(0);
        }
    }

    @Test
    void cliReturnsTheSharedJsonReportForHttpValidation(@TempDir Path directory) throws Exception {
        HttpServer server = server(exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.getResponseHeaders().add("X-Request-Id", "cli-test");
            respond(exchange, 201, "{\"accepted\":true}");
        });

        try {
            Path profile = writeProfile(directory, server, 201);
            Path request = Files.writeString(directory.resolve("request.json"),
                    "{\"deliveryId\":\"D-100\",\"quantity\":2}");
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            ByteArrayOutputStream errors = new ByteArrayOutputStream();

            int exitCode = new CommandLine(new Main())
                    .setOut(new PrintWriter(output, true))
                    .setErr(new PrintWriter(errors, true))
                    .execute("validate", "--schema", profile.toString(), "--input", request.toString(),
                            "--report", "json");

            JsonNode report = MAPPER.readTree(output.toString(StandardCharsets.UTF_8));
            assertThat(exitCode).withFailMessage(errors::toString).isZero();
            assertThat(report.get("status").asText()).isEqualTo("VALID");
            assertThat(errors.toString(StandardCharsets.UTF_8)).isEmpty();
        } finally {
            server.stop(0);
        }
    }

    @Test
    void transportFailureIsOperationalError(@TempDir Path directory) throws Exception {
        HttpServer server = server(exchange -> respond(exchange, 201, "{\"accepted\":true}"));
        int port = server.getAddress().getPort();
        server.stop(0);
        Path profile = writeProfile(directory, "http://127.0.0.1:" + port + "/deliveries", 201);
        Path request = Files.writeString(directory.resolve("request.json"),
                "{\"deliveryId\":\"D-100\",\"quantity\":2}");

        assertThatThrownBy(() -> new HttpValidator().validate(request, profile))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Could not execute HTTP request");
    }

    private static HttpServer server(ExchangeHandler handler) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/deliveries", handler::handle);
        server.start();
        return server;
    }

    private static Path writeProfile(Path directory, HttpServer server, int expectedStatus)
            throws IOException {
        return writeProfile(directory,
                "http://127.0.0.1:" + server.getAddress().getPort() + "/deliveries",
                expectedStatus);
    }

    private static Path writeProfile(Path directory, String url, int expectedStatus) throws IOException {
        Files.writeString(directory.resolve("request.schema.json"), requestSchema());
        Files.writeString(directory.resolve("response.schema.json"), responseSchema());
        return Files.writeString(directory.resolve("http-profile.json"), """
                {
                  "format": "http",
                  "version": 1,
                  "method": "POST",
                  "url": "%s",
                  "requestHeaders": {
                    "Content-Type": "application/json",
                    "Accept": "application/json"
                  },
                  "requestSchema": "request.schema.json",
                  "expectedStatus": %d,
                  "requiredResponseHeaders": ["Content-Type", "X-Request-Id"],
                  "responseContentType": "application/json",
                  "responseSchema": "response.schema.json",
                  "timeoutSeconds": 5
                }
                """.formatted(url, expectedStatus));
    }

    private static String requestSchema() {
        return """
                {
                  "$schema": "https://json-schema.org/draft/2020-12/schema",
                  "type": "object",
                  "required": ["deliveryId", "quantity"],
                  "properties": {
                    "deliveryId": {"type": "string"},
                    "quantity": {"type": "integer", "minimum": 1}
                  },
                  "additionalProperties": false
                }
                """;
    }

    private static String responseSchema() {
        return """
                {
                  "$schema": "https://json-schema.org/draft/2020-12/schema",
                  "type": "object",
                  "required": ["accepted"],
                  "properties": {
                    "accepted": {"type": "boolean"}
                  },
                  "additionalProperties": false
                }
                """;
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (var output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    @FunctionalInterface
    private interface ExchangeHandler {
        void handle(HttpExchange exchange) throws IOException;
    }
}
