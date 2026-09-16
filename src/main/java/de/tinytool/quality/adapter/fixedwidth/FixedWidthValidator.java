package de.tinytool.quality.adapter.fixedwidth;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.tinytool.quality.adapter.support.JsonPointers;
import de.tinytool.quality.adapter.support.LocalFiles;
import de.tinytool.quality.adapter.support.ProfileDocuments;
import de.tinytool.quality.adapter.support.ScalarValidation;
import de.tinytool.quality.core.Finding;
import de.tinytool.quality.core.ValidationException;
import de.tinytool.quality.core.ValidationResult;
import de.tinytool.quality.core.Validator;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.CodingErrorAction;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Validates local fixed-width records against a version-one profile.
 */
public final class FixedWidthValidator implements Validator {

    private final ObjectMapper objectMapper;

    public FixedWidthValidator() {
        this(new ObjectMapper());
    }

    public FixedWidthValidator(ObjectMapper objectMapper) {
        this.objectMapper = ProfileDocuments.strictMapper(objectMapper);
    }

    @Override
    public ValidationResult validate(Path input, Path schema) throws ValidationException {
        Path inputFile = LocalFiles.requireRegularFile(input, "Fixed-width input");
        Path profileFile = LocalFiles.requireRegularFile(schema, "Fixed-width profile");
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
            if (recordNumber == 0) {
                findings.add(new Finding(
                        "",
                        "/records",
                        "minRecords",
                        "Datei enthält keine Datensätze"));
            }
        } catch (IOException | RuntimeException e) {
            throw new ValidationException(
                    "Could not read fixed-width input: " + LocalFiles.safeMessage(e), e);
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
            String path = "/records/" + rowIndex + "/" + JsonPointers.escape(column.name());
            String schemaPath = "/columns/" + JsonPointers.escape(column.name());
            ScalarValidation.validateField(
                    value,
                    recordNumber,
                    column.name(),
                    column.type(),
                    column.rules(),
                    path,
                    schemaPath,
                    findings);
        }
    }
}
