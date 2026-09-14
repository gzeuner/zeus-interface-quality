package de.tinytool.quality.adapter.jsonschema;

import de.tinytool.quality.core.Status;
import de.tinytool.quality.core.ValidationException;
import de.tinytool.quality.core.ValidationResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
            assertThat(finding.schemaPath()).contains("/properties/quantity/type");
            assertThat(finding.keyword()).isEqualTo("type");
            assertThat(finding.message()).isNotBlank();
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
