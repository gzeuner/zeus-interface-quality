package de.tinytool.quality.report;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.tinytool.quality.core.Finding;
import de.tinytool.quality.core.ValidationResult;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

class JsonReportWriterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void writesTheFrozenVersionOneShape() throws Exception {
        ValidationResult result = ValidationResult.invalid(List.of(
                new Finding(
                        "/quantity",
                        "/properties/quantity/type",
                        "type",
                        "string gefunden, integer erwartet")
        ));

        JsonNode report = objectMapper.readTree(new JsonReportWriter().write(result));
        JsonNode expected = objectMapper.readTree(expectedReport());

        assertThat(report).isEqualTo(expected);

        assertThat(report.fieldNames()).toIterable()
                .containsExactly("status", "valid", "findings");
        assertThat(report.get("status").asText()).isEqualTo("INVALID");
        assertThat(report.get("valid").asBoolean()).isFalse();
        assertThat(report.get("findings")).hasSize(1);
        assertThat(report.get("findings").get(0).fieldNames()).toIterable()
                .containsExactly("instancePath", "schemaPath", "keyword", "message");
    }

    private static InputStream expectedReport() {
        return Objects.requireNonNull(JsonReportWriterTest.class.getResourceAsStream(
                "/fixtures/expected-invalid-wrong-type-report.json"));
    }
}
