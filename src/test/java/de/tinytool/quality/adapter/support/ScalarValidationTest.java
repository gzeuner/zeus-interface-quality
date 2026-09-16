package de.tinytool.quality.adapter.support;

import de.tinytool.quality.core.Finding;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class ScalarValidationTest {

    @Test
    void reportsRequiredMissingValues() {
        List<Finding> findings = new ArrayList<>();

        ScalarValidation.validateField(
                "",
                2,
                "quantity",
                ScalarType.INTEGER,
                new ScalarRules(true, null, null, BigDecimal.ONE, null, null),
                "/rows/0/quantity",
                "/columns/quantity",
                findings);

        assertThat(findings).extracting(Finding::keyword).containsExactly("required");
    }

    @Test
    void reportsNumericBoundsAndStringLength() {
        List<Finding> findings = new ArrayList<>();
        ScalarRules numberRules = new ScalarRules(true, null, null, new BigDecimal("1"), new BigDecimal("5"), null);
        ScalarRules textRules = new ScalarRules(true, 2, 3, null, null, Pattern.compile("^[A-Z]+$"));

        ScalarValidation.validateField("9", 1, "amount", ScalarType.DECIMAL, numberRules,
                "/rows/0/amount", "/columns/amount", findings);
        ScalarValidation.validateField("abcd", 1, "code", ScalarType.STRING, textRules,
                "/rows/0/code", "/columns/code", findings);

        assertThat(findings).extracting(Finding::keyword).contains("maximum", "maxLength", "pattern");
    }
}
