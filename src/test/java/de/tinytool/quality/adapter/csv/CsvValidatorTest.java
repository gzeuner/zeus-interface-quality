package de.tinytool.quality.adapter.csv;

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

class CsvValidatorTest {

    private final CsvValidator validator = new CsvValidator();

    @Test
    void acceptsAValidCsvWithQuotedFields() throws Exception {
        ValidationResult result = validator.validate(
                fixture("valid-delivery.csv"),
                fixture("delivery.csv-profile.json"));

        assertThat(result.isValid()).isTrue();
        assertThat(result.findings()).isEmpty();
    }

    @Test
    void reportsATypeFindingWithStableRowAndColumnPaths() throws Exception {
        ValidationResult result = validator.validate(
                fixture("invalid-wrong-type.csv"),
                fixture("delivery.csv-profile.json"));

        assertThat(result.findings()).containsExactly(new Finding(
                "/rows/0/quantity",
                "/columns/quantity/type",
                "type",
                "Zeile 2, Spalte quantity: 'three' ist keine ganze Zahl"));
    }

    @Test
    void reportsMissingRequiredValues() throws Exception {
        ValidationResult result = validator.validate(
                fixture("invalid-missing-required.csv"),
                fixture("delivery.csv-profile.json"));

        assertThat(result.findings()).containsExactly(new Finding(
                "/rows/0/quantity",
                "/columns/quantity/required",
                "required",
                "Zeile 2, Spalte quantity: Pflichtwert fehlt"));
    }

    @Test
    void reportsHeaderMismatchesAsDataFindings() throws Exception {
        ValidationResult result = validator.validate(
                fixture("invalid-header.csv"),
                fixture("delivery.csv-profile.json"));

        assertThat(result.findings()).containsExactly(new Finding(
                "",
                "/header",
                "header",
                "CSV-Kopfzeile stimmt nicht mit dem Profil überein "
                        + "(erwartet: deliveryId;quantity;email, gefunden: deliveryId;email;quantity)"));
    }

    @Test
    void reportsAnUnexpectedColumnCount() throws Exception {
        ValidationResult result = validator.validate(
                fixture("invalid-column-count.csv"),
                fixture("delivery.csv-profile.json"));

        assertThat(result.findings()).containsExactly(new Finding(
                "/rows/0",
                "/columns",
                "columns",
                "Zeile 2: 2 Spalten gefunden, 3 erwartet"));
    }

    @Test
    void treatsMalformedCsvAsAnOperationalError() {
        assertThatThrownBy(() -> validator.validate(
                fixture("malformed.csv"),
                fixture("delivery.csv-profile.json")))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Could not read CSV input");
    }

    @Test
    void rejectsUnsupportedProfileTypes() {
        assertThatThrownBy(() -> validator.validate(
                fixture("valid-delivery.csv"),
                fixture("invalid-profile.json")))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Unsupported column type");
    }

    @Test
    void supportsProfilePatternsAndDecimalMinimums(@TempDir Path directory) throws Exception {
        Path profile = Files.writeString(directory.resolve("profile.json"), """
                {
                  "format": "csv",
                  "version": 1,
                  "delimiter": ",",
                  "encoding": "UTF-8",
                  "header": ["code", "amount"],
                  "columns": [
                    {"name": "code", "type": "string", "required": true, "pattern": "^[A-Z]{3}$"},
                    {"name": "amount", "type": "decimal", "required": true, "minimum": 10.5}
                  ]
                }
                """);
        Path input = Files.writeString(directory.resolve("input.csv"), """
                code,amount
                ab,9.2
                """);

        ValidationResult result = validator.validate(input, profile);

        assertThat(result.findings()).extracting(Finding::keyword)
                .containsExactly("minimum", "pattern");
    }

    @Test
    void reportsMaximumAndMaxLength(@TempDir Path directory) throws Exception {
        Path profile = Files.writeString(directory.resolve("profile.json"), """
                {
                  "format": "csv",
                  "version": 1,
                  "delimiter": ",",
                  "encoding": "UTF-8",
                  "header": ["code", "amount"],
                  "columns": [
                    {"name": "code", "type": "string", "required": true, "maxLength": 3},
                    {"name": "amount", "type": "decimal", "required": true, "maximum": 10.5}
                  ]
                }
                """);
        Path input = Files.writeString(directory.resolve("input.csv"), """
                code,amount
                ABCD,10.6
                """);

        ValidationResult result = validator.validate(input, profile);

        assertThat(result.findings()).extracting(Finding::keyword)
                .containsExactly("maximum", "maxLength");
    }

    private static Path fixture(String name) {
        try {
            return Path.of(Objects.requireNonNull(
                    CsvValidatorTest.class.getResource("/fixtures/csv/" + name)).toURI());
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }
}
