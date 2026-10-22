package de.tinytool.quality.core;

import java.text.MessageFormat;
import java.util.Map;

/** Central message catalog. Add a stable key and both translations when adding a rule. */
public final class ValidationMessages {
    private static final Map<String, String[]> MESSAGES = Map.ofEntries(
            Map.entry("required", new String[]{"Zeile {0}, Spalte {1}: Pflichtwert fehlt", "Row {0}, column {1}: required value is missing"}),
            Map.entry("minLength", new String[]{"Zeile {0}, Spalte {1}: muss mindestens {2} Zeichen enthalten", "Row {0}, column {1}: must contain at least {2} characters"}),
            Map.entry("maxLength", new String[]{"Zeile {0}, Spalte {1}: darf höchstens {2} Zeichen enthalten", "Row {0}, column {1}: must contain at most {2} characters"}),
            Map.entry("integer", new String[]{"Zeile {0}, Spalte {1}: '{2}' ist keine ganze Zahl", "Row {0}, column {1}: '{2}' is not an integer"}),
            Map.entry("decimal", new String[]{"Zeile {0}, Spalte {1}: '{2}' ist keine Dezimalzahl", "Row {0}, column {1}: '{2}' is not a decimal number"}),
            Map.entry("date", new String[]{"Zeile {0}, Spalte {1}: '{2}' ist kein gültiges Datum im Format JJJJ-MM-TT", "Row {0}, column {1}: '{2}' is not a valid date in YYYY-MM-DD format"}),
            Map.entry("time", new String[]{"Zeile {0}, Spalte {1}: '{2}' ist keine gültige Uhrzeit im Format HH:mm", "Row {0}, column {1}: '{2}' is not a valid time in HH:mm format"}),
            Map.entry("email", new String[]{"Zeile {0}, Spalte {1}: '{2}' ist keine gültige E-Mail-Adresse", "Row {0}, column {1}: '{2}' is not a valid email address"}),
            Map.entry("pattern", new String[]{"Zeile {0}, Spalte {1}: entspricht nicht dem vorgegebenen Muster", "Row {0}, column {1}: does not match the configured pattern"}),
            Map.entry("minimum", new String[]{"Zeile {0}, Spalte {1}: muss mindestens {2} sein", "Row {0}, column {1}: must be at least {2}"}),
            Map.entry("maximum", new String[]{"Zeile {0}, Spalte {1}: darf höchstens {2} sein", "Row {0}, column {1}: must be at most {2}"}),
            Map.entry("columns", new String[]{"Zeile {0}: {1} Spalten gefunden, {2} erwartet", "Row {0}: found {1} columns; expected {2}"}),
            Map.entry("duplicate", new String[]{"Zeile {0}: Schlüssel aus {1} bereits in Zeile {2} vorhanden", "Row {0}: key from {1} already occurs in row {2}"}),
            Map.entry("minRecords", new String[]{"{0} Datensätze gefunden, mindestens {1} erwartet", "Found {0} records; expected at least {1}"}),
            Map.entry("maxRecords", new String[]{"{0} Datensätze gefunden, höchstens {1} erwartet", "Found {0} records; expected at most {1}"}),
            Map.entry("headerMissing", new String[]{"CSV-Datei enthält keine Kopfzeile", "CSV file has no header row"}),
            Map.entry("headerCount", new String[]{"CSV-Kopfzeile enthält {0} Spalten, {1} erwartet", "CSV header has {0} columns; expected {1}"}),
            Map.entry("headerMismatch", new String[]{"CSV-Kopfzeile stimmt nicht mit dem Profil überein (erwartet: {0}, gefunden: {1})", "CSV header does not match the profile (expected: {0}, found: {1})"}),
            Map.entry("emptyRecords", new String[]{"Datei enthält keine Datensätze", "File contains no records"}),
            Map.entry("envelopeEnd", new String[]{"CSV-Datei enthält keinen gültigen Endmarker '{0}'", "CSV file has no valid end marker '{0}'"}),
            Map.entry("filename", new String[]{"Dateiname '{0}' entspricht nicht dem Muster '{1}'", "File name '{0}' does not match pattern '{1}'"}),
            Map.entry("generic", new String[]{"Ungültiger Wert ({0})", "Invalid value ({0})"}));

    private ValidationMessages() { }

    public static String text(ValidationLanguage language, String key, Object... args) {
        String[] pair = MESSAGES.get(key);
        if (pair == null) throw new IllegalArgumentException("Unknown validation message key: " + key);
        return new MessageFormat(pair[language == ValidationLanguage.DE ? 0 : 1], language.locale()).format(args);
    }
}
