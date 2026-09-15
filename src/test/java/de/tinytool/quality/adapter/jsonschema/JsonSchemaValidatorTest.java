package de.tinytool.quality.adapter.jsonschema;

import de.tinytool.quality.core.Status;
import de.tinytool.quality.core.ValidationException;
import de.tinytool.quality.core.ValidationResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class JsonSchemaValidatorTest {

    private final JsonSchemaValidator validator = new JsonSchemaValidator();

    @Test
    void acceptsValidDocumentAndResolvesLocalReference() throws Exception {
        ValidationResult result = validateFixture("valid-delivery.json");

        assertThat(result.status()).isEqualTo(Status.VALID);
        assertThat(result.findings()).isEmpty();
    }

    @Test
    void reportsWrongTypeWithStablePaths() throws Exception {
        ValidationResult result = validateFixture("invalid-wrong-type.json");

        assertThat(result.status()).isEqualTo(Status.INVALID);
        assertThat(result.findings()).anySatisfy(finding -> {
            assertThat(finding.instancePath()).isEqualTo("/quantity");
            assertThat(finding.schemaPath()).isEqualTo("/properties/quantity/type");
            assertThat(finding.keyword()).isEqualTo("type");
            assertThat(finding.message()).isNotBlank();
        });
    }

    @Test
    void reportsUnknownPropertiesAtTheDocumentRoot() throws Exception {
        ValidationResult result = validateFixture("invalid-additional-property.json");

        assertThat(result.status()).isEqualTo(Status.INVALID);
        assertThat(result.findings()).anySatisfy(finding -> {
            assertThat(finding.instancePath()).isEqualTo("");
            assertThat(finding.schemaPath()).isEqualTo("/additionalProperties");
            assertThat(finding.keyword()).isEqualTo("additionalProperties");
        });
    }

    @Test
    void enforcesFormatAssertions() throws Exception {
        ValidationResult result = validateFixture("invalid-format.json");

        assertThat(result.status()).isEqualTo(Status.INVALID);
        assertThat(result.findings()).anySatisfy(finding -> {
            assertThat(finding.instancePath()).isEqualTo("/email");
            assertThat(finding.keyword()).isEqualTo("format");
        });
    }

    @Test
    void rejectsReferencesOutsideTheSchemaDirectory(@TempDir Path tempDirectory) throws IOException {
        Path schemaDirectory = Files.createDirectories(tempDirectory.resolve("schemas"));
        Path schema = schemaDirectory.resolve("root.schema.json");
        Path outsideSchema = tempDirectory.resolve("outside.schema.json");
        Path input = tempDirectory.resolve("input.json");

        Files.writeString(schema, "{\"$schema\":\"https://json-schema.org/draft/2020-12/schema\",\"$ref\":\"../outside.schema.json\"}");
        Files.writeString(outsideSchema, "{\"type\":\"object\"}");
        Files.writeString(input, "{}");

        assertThatThrownBy(() -> validator.validate(input, schema))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void rejectsHttpReferencesWithoutNetworkAccess(@TempDir Path tempDirectory) throws IOException {
        Path schema = tempDirectory.resolve("root.schema.json");
        Path input = tempDirectory.resolve("input.json");

        Files.writeString(schema, "{\"$schema\":\"https://json-schema.org/draft/2020-12/schema\",\"$ref\":\"https://example.invalid/schema.json\"}");
        Files.writeString(input, "{}");

        assertThatThrownBy(() -> validator.validate(input, schema))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("not allowed");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "{} {}", "{} trailing", "{\"quantity\":1,\"quantity\":2}"})
    void rejectsAmbiguousOrIncompleteInput(String json, @TempDir Path directory) throws IOException {
        Path schema = Files.writeString(directory.resolve("schema.json"), "true");
        Path input = Files.writeString(directory.resolve("input.json"), json);

        assertThatThrownBy(() -> validator.validate(input, schema))
                .isInstanceOf(ValidationException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "{} {}", "{\"type\":\"object\",\"type\":\"string\"}",
            "{\"minimum\":\"oops\"}", "{\"required\":\"id\"}"})
    void rejectsInvalidRootSchemas(String json, @TempDir Path directory) throws IOException {
        Path schema = Files.writeString(directory.resolve("schema.json"), json);
        Path input = Files.writeString(directory.resolve("input.json"), "{}");

        assertThatThrownBy(() -> validator.validate(input, schema))
                .isInstanceOf(ValidationException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "{} {}", "{\"type\":\"object\",\"type\":\"string\"}",
            "{\"minimum\":\"oops\"}", "{\"required\":\"id\"}"})
    void appliesTheSameChecksToReferencedSchemas(String json, @TempDir Path directory) throws IOException {
        Path schema = Files.writeString(directory.resolve("schema.json"), "{\"$ref\":\"child.json\"}");
        Files.writeString(directory.resolve("child.json"), json);
        Path input = Files.writeString(directory.resolve("input.json"), "{}");

        assertThatThrownBy(() -> validator.validate(input, schema))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void rejectsSymlinksEscapingTheSchemaDirectory(@TempDir Path directory) throws IOException {
        Path schemas = Files.createDirectory(directory.resolve("schemas"));
        Path outside = Files.writeString(directory.resolve("outside.json"), "true");
        createSymlinkOrSkip(schemas.resolve("link.json"), outside);
        Path schema = Files.writeString(schemas.resolve("schema.json"), "{\"$ref\":\"link.json\"}");
        Path input = Files.writeString(directory.resolve("input.json"), "{}");

        assertThatThrownBy(() -> validator.validate(input, schema))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void acceptsSymlinksStayingInsideTheSchemaDirectory(@TempDir Path directory) throws Exception {
        Path child = Files.writeString(directory.resolve("child.json"), "{\"type\":\"object\"}");
        createSymlinkOrSkip(directory.resolve("link.json"), child);
        Path schema = Files.writeString(directory.resolve("schema.json"), "{\"$ref\":\"link.json\"}");
        Path input = Files.writeString(directory.resolve("input.json"), "{}");

        assertThat(validator.validate(input, schema).isValid()).isTrue();
    }

    @Test
    void rejectsMissingReferencesEvenInAbsentProperties(@TempDir Path directory) throws IOException {
        Path schema = Files.writeString(directory.resolve("schema.json"),
                "{\"properties\":{\"optional\":{\"$ref\":\"missing.json\"}}}");
        Path input = Files.writeString(directory.resolve("input.json"), "{}");

        assertThatThrownBy(() -> validator.validate(input, schema))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void acceptsJsonNullAndBooleanSchemas(@TempDir Path directory) throws Exception {
        Path input = Files.writeString(directory.resolve("input.json"), "null");
        Path accept = Files.writeString(directory.resolve("accept.json"), "true");
        Path reject = Files.writeString(directory.resolve("reject.json"), "false");

        assertThat(validator.validate(input, accept).status()).isEqualTo(Status.VALID);
        assertThat(validator.validate(input, reject).status()).isEqualTo(Status.INVALID);
    }

    private static void createSymlinkOrSkip(Path link, Path target) throws IOException {
        try {
            Files.createSymbolicLink(link, target);
        } catch (IOException | UnsupportedOperationException e) {
            // Ubuntu CI must execute this test; Windows may require Developer Mode.
            assumeTrue(!System.getProperty("os.name").startsWith("Windows"),
                    "Windows did not permit creating a test symlink: " + e.getMessage());
            throw e;
        }
    }

    private ValidationResult validateFixture(String inputName) throws Exception {
        Path schema = fixture("delivery.schema.json");
        Path input = fixture(inputName);
        return validator.validate(input, schema);
    }

    private static Path fixture(String name) throws Exception {
        return Path.of(Objects.requireNonNull(
                JsonSchemaValidatorTest.class.getResource("/fixtures/" + name)).toURI());
    }
}
