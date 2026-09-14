package de.tinytool.quality.adapter.jsonschema;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.AbsoluteIri;
import com.networknt.schema.Error;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SchemaRegistryConfig;
import com.networknt.schema.SpecificationVersion;
import com.networknt.schema.resource.IriResourceLoader;
import com.networknt.schema.resource.SchemaLoader;
import de.tinytool.quality.core.Finding;
import de.tinytool.quality.core.ValidationException;
import de.tinytool.quality.core.ValidationResult;
import de.tinytool.quality.core.Validator;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Local JSON document validation against a local JSON Schema Draft 2020-12 schema.
 *
 * <p>The validator library is deliberately kept inside this adapter. The resource
 * loader accepts only file URIs below the directory containing the root schema;
 * remote and outside-directory references are rejected before a resource is loaded.</p>
 */
public final class JsonSchemaValidator implements Validator {

    private final ObjectMapper objectMapper;

    public JsonSchemaValidator() {
        this(new ObjectMapper());
    }

    public JsonSchemaValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public ValidationResult validate(Path input, Path schema) throws ValidationException {
        Path inputFile = requireRegularFile(input, "Input document");
        Path schemaFile = requireRegularFile(schema, "Schema");
        Path schemaDirectory = schemaFile.getParent();

        try {
            JsonNode inputNode = objectMapper.readTree(inputFile.toFile());
            JsonNode schemaNode = objectMapper.readTree(schemaFile.toFile());
            Schema validatorSchema = createRegistry(schemaDirectory)
                    .getSchema(SchemaLocation.of(schemaFile.toUri().toString()), schemaNode);

            List<Error> errors = validatorSchema.validate(inputNode);
            if (errors.isEmpty()) {
                return ValidationResult.valid();
            }

            return ValidationResult.invalid(errors.stream()
                    .map(JsonSchemaValidator::toFinding)
                    .toList());
        } catch (IOException e) {
            throw new ValidationException("Could not read JSON input or schema: " + e.getMessage(), e);
        } catch (RuntimeException e) {
            throw new ValidationException("Could not prepare JSON Schema validation: " + safeMessage(e), e);
        }
    }

    private static Path requireRegularFile(Path path, String description) throws ValidationException {
        if (path == null) {
            throw new ValidationException(description + " path is required");
        }

        Path normalized = path.toAbsolutePath().normalize();
        if (!Files.isRegularFile(normalized)) {
            throw new ValidationException(description + " file does not exist: " + normalized);
        }
        return normalized;
    }

    private static SchemaRegistry createRegistry(Path schemaDirectory) {
        SchemaLoader schemaLoader = SchemaLoader.builder()
                .resourceLoaders(loaders -> loaders.add(IriResourceLoader.getInstance()))
                .allow(iri -> isAllowedLocalReference(iri, schemaDirectory))
                .build();

        SchemaRegistryConfig registryConfig = SchemaRegistryConfig.builder()
                .pathType(com.networknt.schema.path.PathType.JSON_POINTER)
                .formatAssertionsEnabled(true)
                .build();

        return SchemaRegistry.withDefaultDialect(
                SpecificationVersion.DRAFT_2020_12,
                builder -> builder
                        .schemaLoader(schemaLoader)
                        .schemaRegistryConfig(registryConfig));
    }

    private static boolean isAllowedLocalReference(AbsoluteIri iri, Path schemaDirectory) {
        if (!"file".equalsIgnoreCase(iri.getScheme())) {
            return false;
        }

        try {
            URI uri = URI.create(removeFragment(iri.toString()));
            if (uri.getRawAuthority() != null && !uri.getRawAuthority().isBlank()) {
                return false;
            }
            Path referencedFile = Path.of(uri).toAbsolutePath().normalize();
            return referencedFile.startsWith(schemaDirectory);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static String removeFragment(String iri) {
        int fragmentStart = iri.indexOf('#');
        return fragmentStart >= 0 ? iri.substring(0, fragmentStart) : iri;
    }

    private static Finding toFinding(Error error) {
        return new Finding(
                pathOf(error.getInstanceLocation()),
                pathOf(error.getEvaluationPath()),
                error.getKeyword(),
                error.getMessage());
    }

    private static String pathOf(Object path) {
        return path == null ? "" : path.toString();
    }

    private static String safeMessage(RuntimeException exception) {
        return exception.getMessage() == null
                ? exception.getClass().getSimpleName()
                : exception.getMessage();
    }
}
