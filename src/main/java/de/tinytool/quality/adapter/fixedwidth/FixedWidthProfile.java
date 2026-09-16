package de.tinytool.quality.adapter.fixedwidth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.tinytool.quality.adapter.support.ProfileDocuments;
import de.tinytool.quality.adapter.support.ScalarRules;
import de.tinytool.quality.adapter.support.ScalarType;
import de.tinytool.quality.core.ValidationException;

import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Version-one profile for fixed-width records.
 *
 * <p>Positions and lengths are one-based and measured in decoded Java
 * characters. A source language such as COBOL or RPG is descriptive metadata;
 * the adapter does not depend on that language.</p>
 */
record FixedWidthProfile(
        Charset charset,
        int recordLength,
        String sourceLanguage,
        List<FixedWidthColumn> columns
) {

    private static final Set<String> PROFILE_PROPERTIES = Set.of(
            "$id", "$schema", "format", "version", "encoding",
            "recordLength", "sourceLanguage", "columns");
    private static final Set<String> COLUMN_PROPERTIES = Set.of(
            "name", "start", "length", "type", "required", "trim",
            "minLength", "maxLength", "minimum", "maximum", "pattern");

    static FixedWidthProfile read(Path profileFile, ObjectMapper sourceMapper)
            throws ValidationException {
        ObjectMapper mapper = ProfileDocuments.strictMapper(sourceMapper);
        JsonNode root = ProfileDocuments.readRoot(profileFile, mapper, "fixed-width profile");

        ProfileDocuments.requireObject(root, "Fixed-width profile");
        ProfileDocuments.rejectUnknownProperties(root, PROFILE_PROPERTIES, "Fixed-width profile");
        if (!"fixed-width".equals(ProfileDocuments.requiredText(root, "format", "Fixed-width profile"))) {
            throw ProfileDocuments.invalid("Fixed-width profile format must be 'fixed-width'");
        }
        ProfileDocuments.requireVersionOne(root, "fixed-width profile");

        String encoding = ProfileDocuments.requiredText(root, "encoding", "Fixed-width profile");
        final Charset charset;
        try {
            charset = Charset.forName(encoding);
        } catch (IllegalArgumentException e) {
            throw new ValidationException("Unsupported fixed-width profile encoding: " + encoding, e);
        }

        int recordLength = ProfileDocuments.requiredPositiveInteger(
                root, "recordLength", "Fixed-width profile");
        String sourceLanguage = ProfileDocuments.optionalText(
                root, "sourceLanguage", "Fixed-width profile");
        List<FixedWidthColumn> columns = readColumns(
                ProfileDocuments.required(root, "columns", "Fixed-width profile"), recordLength);
        return new FixedWidthProfile(charset, recordLength, sourceLanguage, columns);
    }

    private static List<FixedWidthColumn> readColumns(JsonNode node, int recordLength)
            throws ValidationException {
        if (!node.isArray() || node.isEmpty()) {
            throw ProfileDocuments.invalid("Fixed-width profile columns must be a non-empty array");
        }

        List<FixedWidthColumn> columns = new ArrayList<>();
        Set<String> names = new HashSet<>();
        int previousEnd = 0;
        for (int index = 0; index < node.size(); index++) {
            JsonNode column = node.get(index);
            ProfileDocuments.requireObject(column, "Fixed-width profile column " + index);
            ProfileDocuments.rejectUnknownProperties(
                    column, COLUMN_PROPERTIES, "Fixed-width profile column " + index);

            String name = ProfileDocuments.requiredText(
                    column, "name", "Fixed-width profile column " + index);
            if (!names.add(name)) {
                throw ProfileDocuments.invalid("Fixed-width profile contains duplicate column: " + name);
            }
            int start = ProfileDocuments.requiredPositiveInteger(
                    column, "start", "Fixed-width profile column " + name);
            int length = ProfileDocuments.requiredPositiveInteger(
                    column, "length", "Fixed-width profile column " + name);
            if (start <= previousEnd) {
                throw ProfileDocuments.invalid(
                        "Fixed-width profile columns must be ordered and must not overlap: " + name);
            }
            long end = (long) start + length - 1;
            if (end > recordLength) {
                throw ProfileDocuments.invalid("Fixed-width column exceeds recordLength: " + name);
            }
            previousEnd = Math.toIntExact(end);

            String typeName = ProfileDocuments.requiredText(
                    column, "type", "Fixed-width profile column " + name);
            ScalarType type = ScalarType.fromProfile(typeName, name);
            ScalarRules rules = ProfileDocuments.readRules(column, type, name);
            boolean trim = ProfileDocuments.optionalBoolean(
                    column, "trim", true, "Fixed-width profile column " + name);
            columns.add(new FixedWidthColumn(name, start, length, type, trim, rules));
        }
        return List.copyOf(columns);
    }
}

record FixedWidthColumn(
        String name,
        int start,
        int length,
        ScalarType type,
        boolean trim,
        ScalarRules rules
) {
}
