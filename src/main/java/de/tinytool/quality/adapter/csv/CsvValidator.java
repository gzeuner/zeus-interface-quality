package de.tinytool.quality.adapter.csv;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.tinytool.quality.adapter.support.JsonPointers;
import de.tinytool.quality.adapter.support.LocalFiles;
import de.tinytool.quality.adapter.support.ProfileDocuments;
import de.tinytool.quality.adapter.support.ScalarValidation;
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
import java.nio.charset.CodingErrorAction;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Validates a delimited text file against a version-one CSV profile.
 */
public final class CsvValidator implements Validator {

    private final ObjectMapper objectMapper;

    public CsvValidator() {
        this(new ObjectMapper());
    }

    public CsvValidator(ObjectMapper objectMapper) {
        this.objectMapper = ProfileDocuments.strictMapper(objectMapper);
    }

    @Override
    public ValidationResult validate(Path input, Path schema) throws ValidationException {
        Path inputFile = LocalFiles.requireRegularFile(input, "CSV input");
        Path profileFile = LocalFiles.requireRegularFile(schema, "CSV profile");
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
                validateRecord(records.next(), profile, findings);
            }
        } catch (IOException | RuntimeException e) {
            throw new ValidationException("Could not read CSV input: " + LocalFiles.safeMessage(e), e);
        }

        return findings.isEmpty()
                ? ValidationResult.valid()
                : ValidationResult.invalid(findings);
    }

    private static Reader newStrictReader(Path inputFile, CsvProfile profile) throws IOException {
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
            String path = "/rows/" + rowIndex + "/" + JsonPointers.escape(column.name());
            String schemaPath = "/columns/" + JsonPointers.escape(column.name());
            ScalarValidation.validateField(
                    record.get(index),
                    recordNumber,
                    column.name(),
                    column.type(),
                    column.rules(),
                    path,
                    schemaPath,
                    findings);
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
}
