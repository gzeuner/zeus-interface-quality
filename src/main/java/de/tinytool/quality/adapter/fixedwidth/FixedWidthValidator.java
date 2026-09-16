package de.tinytool.quality.adapter.fixedwidth;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.tinytool.quality.core.Finding;
import de.tinytool.quality.core.ValidationException;
import de.tinytool.quality.core.ValidationResult;
import de.tinytool.quality.core.Validator;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.math.BigDecimal;
import java.nio.charset.CodingErrorAction;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Validates local fixed-width records against a version-one profile.
 */
public final class FixedWidthValidator implements Validator {

    private static final Pattern INTEGER = Pattern.compile("[+-]?\\d+");
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final ObjectMapper objectMapper;

    public FixedWidthValidator() {
        this(new ObjectMapper());
    }

    public FixedWidthValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper.copy()
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .enable(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY);
    }

    @Override
    public ValidationResult validate(Path input, Path schema) throws ValidationException {
        Path inputFile = requireRegularFile(input, "Fixed-width input");
        Path profileFile = requireRegularFile(schema, "Fixed-width profile");
        FixedWidthProfile profile = FixedWidthProfile.read(profileFile, objectMapper);

        List<Finding> findings = new ArrayList<>();
        try (Reader inputReader = newStrictReader(inputFile, profile);
             BufferedReader reader = new BufferedReader(inputReader)) {
            String line;
            long recordNumber = 0;
            while ((line = reader.readLine()) != null) {
                recordNumber++;
                validateRecord(line, recordNumber, profile, findings);
            }
        } catch (IOException | RuntimeException e) {
            throw new ValidationException(
                    "Could not read fixed-width input: " + safeMessage(e), e);
        }

        return findings.isEmpty()
                ? ValidationResult.valid()
                : ValidationResult.invalid(findings);
    }

    private static Reader newStrictReader(Path inputFile, FixedWidthProfile profile)
            throws IOException {
        var decoder = profile.charset().newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        return new InputStreamReader(Files.newInputStream(inputFile), decoder);
    }

    private static void validateRecord(
            String line,
            long recordNumber,
            FixedWidthProfile profile,
            List<Finding> findings
    ) {
        int rowIndex = Math.toIntExact(recordNumber - 1);
        if (line.length() != profile.recordLength()) {
            findings.add(new Finding(
                    "/records/" + rowIndex,
                    "/recordLength",
                    "recordLength",
                    "Zeile " + recordNumber + ": " + line.length()
                            + " Zeichen gefunden, " + profile.recordLength() + " erwartet"));
            return;
        }

        for (FixedWidthColumn column : profile.columns()) {
            int startIndex = column.start() - 1;
            String value = line.substring(startIndex, startIndex + column.length());
            if (column.trim()) {
                value = value.strip();
            }
            String path = "/records/" + rowIndex + "/" + escapeJsonPointer(column.name());
            String schemaPath = "/columns/" + escapeJsonPointer(column.name());
            if (value.isEmpty()) {
                if (column.required()) {
                    findings.add(new Finding(
                            path,
                            schemaPath + "/required",
                            "required",
                            "Zeile " + recordNumber + ", Spalte " + column.name()
                                    + ": Pflichtwert fehlt"));
                }
                continue;
            }
            validateValue(value, recordNumber, column, path, schemaPath, findings);
        }
    }

    private static void validateValue(
            String value,
            long recordNumber,
            FixedWidthColumn column,
            String path,
            String schemaPath,
            List<Finding> findings
    ) {
        String prefix = "Zeile " + recordNumber + ", Spalte " + column.name() + ": ";
        switch (column.type()) {
            case STRING -> {
                if (column.minLength() != null
                        && value.codePointCount(0, value.length()) < column.minLength()) {
                    findings.add(new Finding(
                            path,
                            schemaPath + "/minLength",
                            "minLength",
                            prefix + "muss mindestens " + column.minLength()
                                    + " Zeichen enthalten"));
                }
            }
            case INTEGER -> {
                if (!INTEGER.matcher(value).matches()) {
                    findings.add(new Finding(
                            path,
                            schemaPath + "/type",
                            "type",
                            prefix + "'" + value + "' ist keine ganze Zahl"));
                } else if (column.minimum() != null
                        && new BigDecimal(value).compareTo(column.minimum()) < 0) {
                    findings.add(new Finding(
                            path,
                            schemaPath + "/minimum",
                            "minimum",
                            prefix + "muss mindestens "
                                    + column.minimum().stripTrailingZeros().toPlainString()
                                    + " sein"));
                }
            }
            case DECIMAL -> {
                try {
                    BigDecimal decimal = new BigDecimal(value);
                    if (column.minimum() != null
                            && decimal.compareTo(column.minimum()) < 0) {
                        findings.add(new Finding(
                                path,
                                schemaPath + "/minimum",
                                "minimum",
                                prefix + "muss mindestens "
                                        + column.minimum().stripTrailingZeros().toPlainString()
                                        + " sein"));
                    }
                } catch (NumberFormatException e) {
                    findings.add(new Finding(
                            path,
                            schemaPath + "/type",
                            "type",
                            prefix + "'" + value + "' ist keine Dezimalzahl"));
                }
            }
            case EMAIL -> {
                if (!EMAIL.matcher(value).matches()) {
                    findings.add(new Finding(
                            path,
                            schemaPath + "/type",
                            "type",
                            prefix + "'" + value
                                    + "' ist keine gültige E-Mail-Adresse"));
                }
            }
        }

        if (column.pattern() != null && !column.pattern().matcher(value).matches()) {
            findings.add(new Finding(
                    path,
                    schemaPath + "/pattern",
                    "pattern",
                    prefix + "entspricht nicht dem vorgegebenen Muster"));
        }
    }

    private static String escapeJsonPointer(String value) {
        return value.replace("~", "~0").replace("/", "~1");
    }

    private static Path requireRegularFile(Path path, String description)
            throws ValidationException {
        if (path == null) {
            throw new ValidationException(description + " path is required");
        }
        Path normalized = path.toAbsolutePath().normalize();
        if (!Files.isRegularFile(normalized)) {
            throw new ValidationException(
                    description + " file does not exist: " + normalized);
        }
        try {
            return normalized.toRealPath();
        } catch (IOException e) {
            throw new ValidationException(
                    description + " file cannot be resolved: " + normalized, e);
        }
    }

    private static String safeMessage(Exception exception) {
        return exception.getMessage() == null
                ? exception.getClass().getSimpleName()
                : exception.getMessage();
    }
}
