package de.tinytool.quality.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ValidationResultTest {

    @Test
    void sortsFindingsDeterministicallyAndKeepsThemImmutable() {
        Finding later = new Finding("/z", "/properties/z/type", "type", "wrong type");
        Finding earlier = new Finding("/a", "/properties/a/type", "type", "wrong type");

        ValidationResult result = ValidationResult.invalid(List.of(later, earlier));

        assertThat(result.findings()).containsExactly(earlier, later);
        assertThatThrownBy(() -> result.findings().add(later))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
