package de.tinytool.quality.adapter.support;

import java.math.BigDecimal;
import java.util.regex.Pattern;

/**
 * Shared field constraints used by tabular adapters.
 */
public record ScalarRules(
        boolean required,
        Integer minLength,
        Integer maxLength,
        BigDecimal minimum,
        BigDecimal maximum,
        Pattern pattern
) {
}
