package de.tinytool.quality.adapter.csv;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.tinytool.quality.core.Finding;
import de.tinytool.quality.core.ValidationException;
import de.tinytool.quality.core.ValidationResult;
import de.tinytool.quality.core.Validator;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

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
 * Validates a delimited text file against a version-one CSV profile.
 */
public final class CsvValidator implements Validator {

    private static final Pattern INTEGER = Pattern.compile("[+-]?\\d+");
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final ObjectMapper objectMapper;

    public CsvValidator() {
        this(new ObjectMapper());
    }

    public CsvValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper.copy()
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .enable(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY);
    }

    @Override
    public ValidationResult validate(Path input, Path schema) throws ValidationException {
        Path inputFile = requireRegularFile(input, "CSV input");
        Path profileFile = requireRegularFile(schema, "CSV profile");
        CsvProfile profile = CsvProfile.read(profileFile, objectMapper);

        List<Finding> findings = new ArrayList<>();
        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setDelimiter(profile.delimiter())
                .setIgnoreEmptyLines(false)
                .setTrim(false)
                .get();

        try (Reader reader = newStrictReader(inputFile, profile);
             CSVParser parser = format.parse(reader)) {
            var records = parser.iterator();
            if (!records.hasNext()) {
                return ValidationResult.invalid(List.of(new Finding(
                        "", "/header", "header", "CSV-Datei enthält keine Kopfzeile")));
            }

            CSVRecord header = records.next();
            String headerProblem = compareHeader(header, profile.header(), profile.delimiter());
            if (headerProblem != null) {
                return ValidationResult.invalid(List.of(new Finding(
                        "", "/header", "header", headerProblem)));
            }

            while (records.hasNext()) {
                CSVRecord record = records.next();
                validateRecord(record, profile, findings);
            }
        } catch (IOException | RuntimeException e) {
            throw new ValidationException("Could not read CSV input: " + safeMessage(e), e);
        }

        return findings.isEmpty()
                ? ValidationResult.valid()
                : ValidationResult.invalid(findings);
    }

    private static Reader newStrictReader(Path inputFile, CsvProfile profile)
            throws IOException {
        var decoder = profile.charset().newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        return new InputStreamReader(Files.newInputStream(inputFile), decoder);
    }

    private static void validateRecord(
            CSVRecord record,
            CsvProfile profile,
            List<Finding> findings
    ) {
        long recordNumber = record.getRecordNumber();
        int rowIndex = Math.toIntExact(recordNumber - 2);
        if (record.size() != profile.columns().size()) {
            findings.add(new Finding(
                    "/rows/" + rowIndex,
                    "/columns",
                    "columns",
                    "Zeile " + recordNumber + ": " + record.size()
                            + " Spalten gefunden, " + profile.columns().size() + " erwartet"));
            return;
        }

        for (int index = 0; index < profile.columns().size(); index++) {
            CsvColumn column = profile.columns().get(index);
            String value = record.get(index);
            String path = "/rows/" + rowIndex + "/" + escapeJsonPointer(column.name());
            String schemaPath = "/columns/" + escapeJsonPointer(column.name());

            if (value == null || value.isEmpty()) {
                if (column.required()) {
                    findings.add(new Finding(
                            path, schemaPath + "/required", "required",
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
            CsvColumn column,
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
                            path, schemaPath + "/minLength", "minLength",
                            prefix + "muss mindestens " + column.minLength() + " Zeichen enthalten"));
                }
            }
            case INTEGER -> {
                if (!INTEGER.matcher(value).matches()) {
                    findings.add(new Finding(
                            path, schemaPath + "/type", "type",
                            prefix + "'" + value + "' ist keine ganze Zahl"));
                } else if (column.minimum() != null
                        && new BigDecimal(value).compareTo(column.minimum()) < 0) {
                    findings.add(new Finding(
                            path, schemaPath + "/minimum", "minimum",
                            prefix + "muss mindestens " + column.minimum().stripTrailingZeros().toPlainString()
                                    + " sein"));
                }
            }
            case DECIMAL -> {
                try {
                    BigDecimal decimal = new BigDecimal(value);
                    if (column.minimum() != null && decimal.compareTo(column.minimum()) < 0) {
                        findings.add(new Finding(
                                path, schemaPath + "/minimum", "minimum",
                                prefix + "muss mindestens " + column.minimum().stripTrailingZeros().toPlainString()
                                        + " sein"));
                    }
                } catch (NumberFormatException e) {
                    findings.add(new Finding(
                            path, schemaPath + "/type", "type",
                            prefix + "'" + value + "' ist keine Dezimalzahl"));
                }
            }
            case EMAIL -> {
                if (!EMAIL.matcher(value).matches()) {
                    findings.add(new Finding(
                            path, schemaPath + "/type", "type",
                            prefix + "'" + value + "' ist keine gültige E-Mail-Adresse"));
                }
            }
        }

        if (column.pattern() != null && !column.pattern().matcher(value).matches()) {
            findings.add(new Finding(
                    path, schemaPath + "/pattern", "pattern", prefix
                    + "entspricht nicht dem vorgegebenen Muster"));
        }
    }

    private static String compareHeader(CSVRecord actual, List<String> expected, char delimiter) {
        if (actual.size() != expected.size()) {
            return "CSV-Kopfzeile enthält " + actual.size() + " Spalten, "
                    + expected.size() + " erwartet";
        }
        for (int index = 0; index < expected.size(); index++) {
            if (!expected.get(index).equals(actual.get(index))) {
                return "CSV-Kopfzeile stimmt nicht mit dem Profil überein (erwartet: "
                        + String.join(String.valueOf(delimiter), expected)
                        + ", gefunden: " + String.join(String.valueOf(delimiter), actual)
                        + ")";
            }
        }
        return null;
    }

    private static String escapeJsonPointer(String value) {
        return value.replace("~", "~0").replace("/", "~1");
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

    private static String safeMessage(Exception exception) {
        return exception.getMessage() == null
                ? exception.getClass().getSimpleName()
                : exception.getMessage();
    }
}
