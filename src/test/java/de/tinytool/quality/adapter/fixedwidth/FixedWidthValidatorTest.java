package de.tinytool.quality.adapter.fixedwidth;

import de.tinytool.quality.core.Finding;
import de.tinytool.quality.core.ValidationException;
import de.tinytool.quality.core.ValidationResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FixedWidthValidatorTest {

    private final FixedWidthValidator validator = new FixedWidthValidator();

    @Test
    void acceptsAValidCobolProfile() throws Exception {
        ValidationResult result = validator.validate(
                fixture("valid-delivery.dat"),
                fixture("delivery.cobol-profile.json"));

        assertThat(result.isValid()).isTrue();
        assertThat(result.findings()).isEmpty();
    }

    @Test
    void acceptsTheSameLayoutWithRpgMetadata() throws Exception {
        ValidationResult result = validator.validate(
                fixture("valid-delivery.dat"),
                fixture("delivery.rpg-profile.json"));

        assertThat(result.isValid()).isTrue();
    }

    @Test
    void reportsATypeFindingWithStableRecordAndColumnPaths() throws Exception {
        ValidationResult result = validator.validate(
                fixture("invalid-wrong-type.dat"),
                fixture("delivery.cobol-profile.json"));

        assertThat(result.findings()).containsExactly(new Finding(
                "/records/0/quantity",
                "/columns/quantity/type",
                "type",
                "Zeile 1, Spalte quantity: 'three' ist keine ganze Zahl"));
    }

    @Test
    void reportsWrongRecordLength() throws Exception {
        ValidationResult result = validator.validate(
                fixture("invalid-record-length.dat"),
                fixture("delivery.cobol-profile.json"));

        assertThat(result.findings()).containsExactly(new Finding(
                "/records/0",
                "/recordLength",
                "recordLength",
                "Zeile 1: 9 Zeichen gefunden, 31 erwartet"));
    }

    @Test
    void reportsRequiredFieldsAfterRemovingPadding() throws Exception {
        ValidationResult result = validator.validate(
                fixture("invalid-required.dat"),
                fixture("delivery.cobol-profile.json"));

        assertThat(result.findings()).containsExactly(new Finding(
                "/records/0/quantity",
                "/columns/quantity/required",
                "required",
                "Zeile 1, Spalte quantity: Pflichtwert fehlt"));
    }

    @Test
    void rejectsOverlappingProfileColumns(@TempDir Path directory) throws Exception {
        Path profile = Files.writeString(directory.resolve("profile.json"), """
                {
                  "format": "fixed-width",
                  "version": 1,
                  "encoding": "UTF-8",
                  "recordLength": 10,
                  "columns": [
                    {"name": "a", "start": 1, "length": 5, "type": "string"},
                    {"name": "b", "start": 5, "length": 2, "type": "string"}
                  ]
                }
                """);
        Path input = Files.writeString(directory.resolve("input.dat"), "1234567890");

        assertThatThrownBy(() -> validator.validate(input, profile))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("must be ordered and must not overlap");
    }

    @Test
    void reportsEmptyInputAsInvalid(@TempDir Path directory) throws Exception {
        Path profile = Files.writeString(directory.resolve("profile.json"), """
                {
                  "format": "fixed-width",
                  "version": 1,
                  "encoding": "UTF-8",
                  "recordLength": 4,
                  "columns": [
                    {"name": "code", "start": 1, "length": 4, "type": "string", "required": true}
                  ]
                }
                """);
        Path input = Files.writeString(directory.resolve("input.dat"), "");

        ValidationResult result = validator.validate(input, profile);

        assertThat(result.findings()).containsExactly(new Finding(
                "",
                "/records",
                "minRecords",
                "Datei enthält keine Datensätze"));
    }

    private static Path fixture(String name) {
        try {
            return Path.of(Objects.requireNonNull(
                    FixedWidthValidatorTest.class.getResource("/fixtures/fixedwidth/" + name))
                    .toURI());
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }
}
