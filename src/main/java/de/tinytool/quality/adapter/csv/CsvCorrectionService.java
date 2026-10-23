package de.tinytool.quality.adapter.csv;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.tinytool.quality.adapter.support.LocalFiles;
import de.tinytool.quality.adapter.support.ProfileDocuments;
import de.tinytool.quality.core.Finding;
import de.tinytool.quality.core.ValidationException;
import de.tinytool.quality.core.ValidationLanguage;
import de.tinytool.quality.core.ValidationResult;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.CodingErrorAction;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Applies the CSV row-removal correction strategy with a backup and an audit trail. */
public final class CsvCorrectionService {
    private final ObjectMapper mapper = ProfileDocuments.strictMapper(new ObjectMapper());

    public ValidationResult correct(Path input, Path profileFile, ValidationLanguage language,
                                    ValidationResult initial) throws ValidationException {
        Path source = LocalFiles.requireRegularFile(input, "CSV input");
        CsvProfile profile = CsvProfile.read(LocalFiles.requireRegularFile(profileFile, "CSV profile"), mapper);
        if (!profile.correctionEnabled()) return initial;
        if (initial.isValid()) return initial;

        Set<Integer> rejectedRows = new HashSet<>();
        Map<Integer, List<Finding>> rowFindings = new LinkedHashMap<>();
        for (Finding finding : initial.findings()) {
            String path = finding.instancePath();
            if (!path.matches("/rows/[0-9]+(?:/.*)?")) return initial;
            int row = Integer.parseInt(path.split("/", 4)[2]);
            rejectedRows.add(row);
            rowFindings.computeIfAbsent(row, ignored -> new ArrayList<>()).add(finding);
        }
        if (rejectedRows.isEmpty()) return initial;

        Path backup = source.resolveSibling(source.getFileName() + ".original.bak");
        Path rejected = source.resolveSibling(source.getFileName() + ".rejected.csv");
        Path audit = source.resolveSibling(source.getFileName() + ".correction.json");
        Path directory = source.getParent();
        Path correctedTemp = null, rejectedTemp = null, auditTemp = null;
        boolean committed = false;
        boolean artifactsReserved = false;
        try {
            if (Files.exists(backup) || Files.exists(rejected) || Files.exists(audit)) {
                throw new IOException("Correction artifacts already exist; refusing to overwrite them.");
            }
            artifactsReserved = true;
            List<List<String>> records = readRecords(source, profile);
            int headerIndex = profile.envelope() == null ? 0 : 1;
            int firstDataIndex = headerIndex + 1;
            int endIndex = records.size() - (profile.envelope() == null ? 0 : 1);
            if (headerIndex >= records.size() || firstDataIndex > endIndex) return initial;

            correctedTemp = Files.createTempFile(directory, ".zeus-corrected-", ".tmp");
            rejectedTemp = Files.createTempFile(directory, ".zeus-rejected-", ".tmp");
            auditTemp = Files.createTempFile(directory, ".zeus-audit-", ".tmp");
            writeRecords(correctedTemp, records, profile, rejectedRows, firstDataIndex, endIndex, false);
            writeRecords(rejectedTemp, records, profile, rejectedRows, firstDataIndex, endIndex, true);

            ValidationResult after = new CsvValidator(mapper).validate(correctedTemp, profileFile,
                    source.getFileName().toString(), language);
            if (!after.isValid()) {
                throw new IOException("Corrected CSV still fails validation; the original was left unchanged.");
            }

            List<Map<String, Object>> removed = new ArrayList<>();
            for (int row : rejectedRows.stream().sorted().toList()) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("dataRow", row + 1);
                entry.put("sourceRecord", firstDataIndex + row + 1);
                entry.put("findings", rowFindings.get(row).stream().map(f -> Map.of(
                        "instancePath", f.instancePath(), "schemaPath", f.schemaPath(),
                        "keyword", f.keyword(), "message", f.message())).toList());
                removed.add(entry);
            }
            mapper.writerWithDefaultPrettyPrinter().writeValue(auditTemp.toFile(), Map.of(
                    "format", "zeus-csv-correction-v1", "source", source.getFileName().toString(),
                    "profile", profileFile.getFileName().toString(), "removedRows", removed,
                    "backup", backup.getFileName().toString(), "rejectedRecords", rejected.getFileName().toString()));

            Files.move(rejectedTemp, rejected);
            rejectedTemp = null;
            Files.move(auditTemp, audit);
            auditTemp = null;
            Files.copy(source, backup, StandardCopyOption.COPY_ATTRIBUTES);
            try {
                Files.move(correctedTemp, source, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                correctedTemp = null;
                committed = true;
            } catch (IOException commitFailure) {
                Files.copy(backup, source, StandardCopyOption.REPLACE_EXISTING);
                throw commitFailure;
            }
            return ValidationResult.valid(Map.of(
                    "correction", Map.of("removedRecords", rejectedRows.size(),
                            "backup", backup.getFileName().toString(),
                            "rejectedRecords", rejected.getFileName().toString(),
                            "audit", audit.getFileName().toString())));
        } catch (IOException | RuntimeException e) {
            throw new ValidationException("CSV correction failed: " + LocalFiles.safeMessage(e), e);
        } finally {
            if (!committed && artifactsReserved) {
                deleteQuietly(backup);
                deleteQuietly(rejected);
                deleteQuietly(audit);
            }
            deleteQuietly(correctedTemp);
            deleteQuietly(rejectedTemp);
            deleteQuietly(auditTemp);
        }
    }

    /** Validates the proposed correction in a temporary file and reports eligible rows without changing source files. */
    public ValidationResult preview(Path input, Path profileFile, ValidationLanguage language,
                                    ValidationResult initial) throws ValidationException {
        Path source = LocalFiles.requireRegularFile(input, "CSV input");
        CsvProfile profile = CsvProfile.read(LocalFiles.requireRegularFile(profileFile, "CSV profile"), mapper);
        if (!profile.correctionEnabled() || initial.isValid()) return initial;
        Set<Integer> rowsToRemove = new HashSet<>();
        for (Finding finding : initial.findings()) {
            String path = finding.instancePath();
            if (!path.matches("/rows/[0-9]+(?:/.*)?")) return initial;
            rowsToRemove.add(Integer.parseInt(path.split("/", 4)[2]));
        }
        if (rowsToRemove.isEmpty()) return initial;
        Path staged = null;
        try {
            List<List<String>> records = readRecords(source, profile);
            int headerIndex = profile.envelope() == null ? 0 : 1;
            int firstDataIndex = headerIndex + 1;
            int endIndex = records.size() - (profile.envelope() == null ? 0 : 1);
            if (headerIndex >= records.size() || firstDataIndex > endIndex) return initial;
            staged = Files.createTempFile(source.getParent(), ".zeus-preview-", ".tmp");
            writeRecords(staged, records, profile, rowsToRemove, firstDataIndex, endIndex, false);
            ValidationResult after = new CsvValidator(mapper).validate(staged, profileFile,
                    source.getFileName().toString(), language);
            if (!after.isValid()) return initial;
            Map<String, Object> metadata = new LinkedHashMap<>(initial.metadata());
            metadata.put("correctionPreview", Map.of("removedRecords", rowsToRemove.size(),
                    "wouldCreate", List.of(source.getFileName() + ".original.bak",
                            source.getFileName() + ".rejected.csv", source.getFileName() + ".correction.json")));
            return ValidationResult.invalid(initial.findings(), metadata);
        } catch (IOException | RuntimeException e) {
            throw new ValidationException("CSV correction preview failed: " + LocalFiles.safeMessage(e), e);
        } finally {
            deleteQuietly(staged);
        }
    }

    private static List<List<String>> readRecords(Path input, CsvProfile profile) throws IOException {
        var decoder = profile.charset().newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        CSVFormat format = CSVFormat.DEFAULT.builder().setDelimiter(profile.delimiter())
                .setIgnoreEmptyLines(false).setTrim(false).get();
        try (var reader = new InputStreamReader(Files.newInputStream(input), decoder);
             CSVParser parser = format.parse(reader)) {
            List<List<String>> records = new ArrayList<>();
            for (CSVRecord record : parser) {
                List<String> row = new ArrayList<>();
                record.forEach(row::add);
                records.add(List.copyOf(row));
            }
            return records;
        }
    }

    private static void writeRecords(Path output, List<List<String>> records, CsvProfile profile,
                                     Set<Integer> rejectedRows, int firstDataIndex, int endIndex,
                                     boolean rejectedOnly) throws IOException {
        var encoder = profile.charset().newEncoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        CSVFormat format = CSVFormat.DEFAULT.builder().setDelimiter(profile.delimiter()).get();
        try (var writer = new OutputStreamWriter(Files.newOutputStream(output), encoder);
             CSVPrinter printer = new CSVPrinter(writer, format)) {
            if (!rejectedOnly) {
                for (int i = 0; i < records.size(); i++) {
                    boolean data = i >= firstDataIndex && i < endIndex;
                    int row = i - firstDataIndex;
                    if (!data || !rejectedRows.contains(row)) printer.printRecord(records.get(i));
                }
            } else {
                printer.printRecord(records.get(profile.envelope() == null ? 0 : 1));
                for (int i = firstDataIndex; i < endIndex; i++) {
                    if (rejectedRows.contains(i - firstDataIndex)) printer.printRecord(records.get(i));
                }
            }
        }
    }

    private static void deleteQuietly(Path path) {
        if (path != null) try { Files.deleteIfExists(path); } catch (IOException ignored) { }
    }
}
