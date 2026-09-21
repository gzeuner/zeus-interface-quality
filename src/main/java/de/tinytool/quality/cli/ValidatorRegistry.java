package de.tinytool.quality.cli;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.tinytool.quality.adapter.csv.CsvValidator;
import de.tinytool.quality.adapter.fixedwidth.FixedWidthValidator;
import de.tinytool.quality.adapter.http.HttpValidator;
import de.tinytool.quality.adapter.jsonschema.JsonSchemaValidator;
import de.tinytool.quality.core.Validator;

import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;

/**
 * Selects the adapter for an explicit or detected input format.
 */
public final class ValidatorRegistry {

    private final Map<InputFormat, Validator> validators;
    private final ObjectMapper objectMapper;

    public ValidatorRegistry() {
        this(new JsonSchemaValidator(), new CsvValidator(), new FixedWidthValidator(), new HttpValidator());
    }

    public ValidatorRegistry(Validator jsonValidator, Validator csvValidator, Validator fixedWidthValidator) {
        this(jsonValidator, csvValidator, fixedWidthValidator, new HttpValidator());
    }

    public ValidatorRegistry(
            Validator jsonValidator,
            Validator csvValidator,
            Validator fixedWidthValidator,
            Validator httpValidator
    ) {
        Map<InputFormat, Validator> map = new EnumMap<>(InputFormat.class);
        map.put(InputFormat.JSON, jsonValidator);
        map.put(InputFormat.CSV, csvValidator);
        map.put(InputFormat.FIXED_WIDTH, fixedWidthValidator);
        map.put(InputFormat.HTTP, httpValidator);
        this.validators = Map.copyOf(map);
        this.objectMapper = new ObjectMapper();
    }

    public Validator validatorFor(InputFormat requested, Path profile) {
        InputFormat format = requested == null || requested == InputFormat.AUTO
                ? detect(profile)
                : requested;
        Validator validator = validators.get(format);
        if (validator == null) {
            throw new IllegalStateException("No validator registered for " + format);
        }
        return validator;
    }

    public InputFormat detect(Path profile) {
        if (profile == null) {
            return InputFormat.JSON;
        }
        try {
            JsonNode root = objectMapper.readTree(profile.toFile());
            if (root != null && root.path("format").isTextual()) {
                String format = root.get("format").asText();
                if ("csv".equals(format)) {
                    return InputFormat.CSV;
                }
                if ("fixed-width".equals(format)) {
                    return InputFormat.FIXED_WIDTH;
                }
                if ("http".equals(format)) {
                    return InputFormat.HTTP;
                }
            }
        } catch (Exception ignored) {
            // JSON Schema documents and unreadable files fall back to the JSON adapter.
        }
        return InputFormat.JSON;
    }
}
