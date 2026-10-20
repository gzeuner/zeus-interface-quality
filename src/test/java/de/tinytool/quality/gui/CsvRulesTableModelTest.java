/*
 * Author: tiny-tool.de
 * Copyright 2026 tiny-tool.de
 * SPDX-License-Identifier: Apache-2.0
 * Licensed under the Apache License, Version 2.0.
 * See LICENSE or https://www.apache.org/licenses/LICENSE-2.0.
 */

package de.tinytool.quality.gui;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.tinytool.quality.adapter.csv.CsvValidator;
import de.tinytool.quality.core.ValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CsvRulesTableModelTest {

    @Test
    void persistsEditedRulesAndAppliesThemInTheValidator(@TempDir Path directory) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        var model = new CsvRulesTableModel(mapper.readTree(exampleProfile().toFile()));
        model.setValueAt(false, 5, 1);
        Path profile = directory.resolve("custom.json");
        var document = model.toProfile();
        new CsvValidator().checkProfile(document);
        mapper.writeValue(profile.toFile(), document);
        Path input = Files.writeString(directory.resolve("input.csv"),
                Files.readString(exampleCsv()).replace(";city;", ";tandem;"));

        assertThat(new LocalValidationService().validate(input, profile).isValid()).isTrue();
        assertThat(document.path("columns").get(5).path("enabled").asBoolean()).isFalse();
        assertThat(model.getColumnClass(1)).isEqualTo(Boolean.class);
    }

    @Test
    void retainsBoundsAndOptionalProfileMetadata() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        var original = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(exampleProfile().toFile());
        original.put("$id", "urn:team:profile");
        original.put("maxRecords", 50);
        original.putArray("uniqueBy").add("rentalId");
        var document = new CsvRulesTableModel(original).toProfile();
        new CsvValidator().checkProfile(document);
        assertThat(document.path("$id").asText()).isEqualTo("urn:team:profile");
        assertThat(document.path("maxRecords").asInt()).isEqualTo(50);
        assertThat(document.path("uniqueBy")).isEqualTo(original.path("uniqueBy"));
        assertThat(document.path("columns").get(4).path("minimum").decimalValue())
                .isEqualByComparingTo("0");
    }

    @Test
    void supportsAddingRemovingAndRenamingColumns() throws Exception {
        var model = new CsvRulesTableModel(new ObjectMapper().readTree(exampleProfile().toFile()));
        model.addColumn();
        model.setValueAt("Extra", 7, 0);
        assertThat(model.toProfile().path("header").get(7).asText()).isEqualTo("Extra");
        model.removeColumn(7);
        assertThat(model.toProfile().path("header").size()).isEqualTo(7);
    }

    @Test
    void rejectsInvalidNumericEdits() throws Exception {
        var model = new CsvRulesTableModel(new ObjectMapper().readTree(exampleProfile().toFile()));
        model.setValueAt("no-number", 4, 6);
        assertThatThrownBy(model::toProfile).isInstanceOf(ValidationException.class).hasMessageContaining("Zahl");
    }

    private static Path exampleProfile() {
        return Path.of("docs", "examples", "fictional-bicycle-rentals.csv-profile.json");
    }

    private static Path exampleCsv() {
        return Path.of("docs", "examples", "fictional-bicycle-rentals.csv");
    }
}
