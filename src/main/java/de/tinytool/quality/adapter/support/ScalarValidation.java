package de.tinytool.quality.adapter.support;

import de.tinytool.quality.core.Finding;

import java.math.BigDecimal;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Type and constraint checks shared by CSV and fixed-width adapters.
 */
public final class ScalarValidation {

    private static final Pattern INTEGER = Pattern.compile("[+-]?\\d+");
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private ScalarValidation() {
    }

    public static void validateField(
            String rawValue,
            long recordNumber,
            String columnName,
            ScalarType type,
            ScalarRules rules,
            String instancePath,
            String schemaPath,
            List<Finding> findings
    ) {
        String value = rawValue == null ? "" : rawValue;
        if (value.isEmpty()) {
            if (rules.required()) {
                findings.add(new Finding(
                        instancePath,
                        schemaPath + "/required",
                        "required",
                        "Zeile " + recordNumber + ", Spalte " + columnName + ": Pflichtwert fehlt"));
            }
            return;
        }

        String prefix = "Zeile " + recordNumber + ", Spalte " + columnName + ": ";
        switch (type) {
            case STRING -> {
                int length = value.codePointCount(0, value.length());
                if (rules.minLength() != null && length < rules.minLength()) {
                    findings.add(new Finding(
                            instancePath,
                            schemaPath + "/minLength",
                            "minLength",
                            prefix + "muss mindestens " + rules.minLength() + " Zeichen enthalten"));
                }
                if (rules.maxLength() != null && length > rules.maxLength()) {
                    findings.add(new Finding(
                            instancePath,
                            schemaPath + "/maxLength",
                            "maxLength",
                            prefix + "darf höchstens " + rules.maxLength() + " Zeichen enthalten"));
                }
            }
            case INTEGER -> {
                if (!INTEGER.matcher(value).matches()) {
                    findings.add(new Finding(
                            instancePath,
                            schemaPath + "/type",
                            "type",
                            prefix + "'" + value + "' ist keine ganze Zahl"));
                } else {
                    addNumericBounds(new BigDecimal(value), rules, prefix, instancePath, schemaPath, findings);
                }
            }
            case DECIMAL -> {
                try {
                    addNumericBounds(new BigDecimal(value), rules, prefix, instancePath, schemaPath, findings);
                } catch (NumberFormatException e) {
                    findings.add(new Finding(
                            instancePath,
                            schemaPath + "/type",
                            "type",
                            prefix + "'" + value + "' ist keine Dezimalzahl"));
                }
            }
            case EMAIL -> {
                if (!EMAIL.matcher(value).matches()) {
                    findings.add(new Finding(
                            instancePath,
                            schemaPath + "/type",
                            "type",
                            prefix + "'" + value + "' ist keine gültige E-Mail-Adresse"));
                }
            }
        }

        if (rules.pattern() != null && !rules.pattern().matcher(value).matches()) {
            findings.add(new Finding(
                    instancePath,
                    schemaPath + "/pattern",
                    "pattern",
                    prefix + "entspricht nicht dem vorgegebenen Muster"));
        }
    }

    private static void addNumericBounds(
            BigDecimal value,
            ScalarRules rules,
            String prefix,
            String instancePath,
            String schemaPath,
            List<Finding> findings
    ) {
        if (rules.minimum() != null && value.compareTo(rules.minimum()) < 0) {
            findings.add(new Finding(
                    instancePath,
                    schemaPath + "/minimum",
                    "minimum",
                    prefix + "muss mindestens " + plain(rules.minimum()) + " sein"));
        }
        if (rules.maximum() != null && value.compareTo(rules.maximum()) > 0) {
            findings.add(new Finding(
                    instancePath,
                    schemaPath + "/maximum",
                    "maximum",
                    prefix + "darf höchstens " + plain(rules.maximum()) + " sein"));
        }
    }

    private static String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }
}
