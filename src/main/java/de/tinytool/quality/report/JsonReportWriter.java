package de.tinytool.quality.report;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.tinytool.quality.core.ValidationResult;

/**
 * Serializes the frozen version-one report contract.
 */
public final class JsonReportWriter {

    private final ObjectMapper objectMapper;

    public JsonReportWriter() {
        this(new ObjectMapper());
    }

    public JsonReportWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String write(ValidationResult result) throws JsonProcessingException {
        return objectMapper.writerWithDefaultPrettyPrinter()
                .writeValueAsString(ValidationReport.from(result));
    }
}
