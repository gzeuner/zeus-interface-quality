package de.tinytool.quality.adapter.jsonschema;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.networknt.schema.AbsoluteIri;
import com.networknt.schema.Error;
import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SchemaRegistryConfig;
import com.networknt.schema.SpecificationVersion;
import com.networknt.schema.resource.SchemaLoader;
import com.networknt.schema.serialization.NodeReader;
import de.tinytool.quality.core.Finding;
import de.tinytool.quality.core.ValidationException;
import de.tinytool.quality.core.ValidationResult;
import de.tinytool.quality.core.Validator;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

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
        this.objectMapper = objectMapper.copy()
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .enable(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY);
    }

    @Override
    public ValidationResult validate(Path input, Path schema) throws ValidationException {
        Path inputFile = requireRegularFile(input, "Input document");
        Path schemaFile = requireRegularFile(schema, "Schema");
        Path schemaDirectory = schemaFile.getParent();

        try {
            JsonNode inputNode = requireDocument(objectMapper.readTree(inputFile.toFile()));
            // Meta-schemas are bundled in NetworkNT. This registry has no remote fetcher.
            Schema metaSchema = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12)
                    .getSchema(SchemaLocation.of(SpecificationVersion.DRAFT_2020_12.getDialectId()));
            NodeReader schemaReader = verifiedSchemaReader(metaSchema);
            JsonNode schemaNode;
            try (InputStream stream = Files.newInputStream(schemaFile)) {
                schemaNode = schemaReader.readTree(stream, InputFormat.JSON);
            }
            Schema validatorSchema = createRegistry(schemaDirectory, schemaReader)
                    .getSchema(SchemaLocation.of(schemaFile.toUri().toString()), schemaNode);
            validatorSchema.initializeValidators();

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
        try {
            return normalized.toRealPath();
        } catch (IOException e) {
            throw new ValidationException(description + " file cannot be resolved: " + normalized, e);
        }
    }

    private static SchemaRegistry createRegistry(Path schemaDirectory, NodeReader schemaReader) {
        SchemaLoader schemaLoader = SchemaLoader.builder()
                .fetchRemoteResources(false)
                .resourceLoaders(loaders -> loaders.add(iri ->
                        () -> Files.newInputStream(checkedLocalReference(iri, schemaDirectory))))
                .allow(iri -> isAllowedLocalReference(iri, schemaDirectory))
                .build();

        SchemaRegistryConfig registryConfig = SchemaRegistryConfig.builder()
                .pathType(com.networknt.schema.path.PathType.JSON_POINTER)
                .formatAssertionsEnabled(true)
                .locale(Locale.GERMAN)
                .build();

        return SchemaRegistry.withDefaultDialect(
                SpecificationVersion.DRAFT_2020_12,
                builder -> builder
                        .nodeReader(schemaReader)
                        .schemaLoader(schemaLoader)
                        .schemaRegistryConfig(registryConfig));
    }

    private static boolean isAllowedLocalReference(AbsoluteIri iri, Path schemaDirectory) {
        try {
            checkedLocalReference(iri, schemaDirectory);
            return true;
        } catch (IOException | IllegalArgumentException e) {
            return false;
        }
    }

    private static Path checkedLocalReference(AbsoluteIri iri, Path schemaDirectory) throws IOException {
        if (!"file".equalsIgnoreCase(iri.getScheme())) {
            throw new IOException("Only local schema references are allowed");
        }

        URI uri = URI.create(removeFragment(iri.toString()));
        if (uri.getRawAuthority() != null && !uri.getRawAuthority().isBlank()) {
            throw new IOException("Schema references with a host are not allowed");
        }
        Path referencedFile = Path.of(uri).toAbsolutePath().normalize();
        if (!referencedFile.startsWith(schemaDirectory)) {
            throw new IOException("Schema reference is outside the schema directory");
        }
        Path realFile = referencedFile.toRealPath();
        if (!realFile.startsWith(schemaDirectory) || !Files.isRegularFile(realFile)) {
            throw new IOException("Schema reference target is outside the schema directory or is not a file");
        }
        return realFile;
    }

    private static JsonNode requireDocument(JsonNode node) throws IOException {
        if (node == null || node.isMissingNode()) {
            throw new IOException("Expected one JSON document; the file is empty");
        }
        return node;
    }

    private NodeReader verifiedSchemaReader(Schema metaSchema) {
        return new NodeReader() {
            @Override
            public JsonNode readTree(String data, InputFormat format) throws IOException {
                requireJson(format);
                return checkSchema(objectMapper.readTree(data));
            }

            @Override
            public JsonNode readTree(InputStream stream, InputFormat format) throws IOException {
                requireJson(format);
                return checkSchema(objectMapper.readTree(stream));
            }

            private JsonNode checkSchema(JsonNode node) throws IOException {
                requireDocument(node);
                List<Error> errors = metaSchema.validate(node);
                if (!errors.isEmpty()) {
                    Error error = errors.getFirst();
                    throw new IOException("Invalid Draft 2020-12 schema at "
                            + error.getInstanceLocation() + " (" + error.getKeyword() + ")");
                }
                return node;
            }

            private void requireJson(InputFormat format) throws IOException {
                if (format != InputFormat.JSON) {
                    throw new IOException("Only JSON schema documents are supported");
                }
            }
        };
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
