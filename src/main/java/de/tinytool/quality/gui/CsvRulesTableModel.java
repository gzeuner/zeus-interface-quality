/*
 * Author: tiny-tool.de
 * Copyright 2026 tiny-tool.de
 * SPDX-License-Identifier: Apache-2.0
 * Licensed under the Apache License, Version 2.0.
 * See LICENSE or https://www.apache.org/licenses/LICENSE-2.0.
 */

package de.tinytool.quality.gui;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.tinytool.quality.adapter.csv.CsvValidator;
import de.tinytool.quality.core.ValidationException;

import javax.swing.table.AbstractTableModel;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

final class CsvRulesTableModel extends AbstractTableModel {

    private static final String[] KEYS = {
            "name", "enabled", "required", "type", "minLength", "maxLength", "minimum", "maximum", "pattern"
    };
    private static final String[] GERMAN_LABELS = {
            "Spalte", "Aktiv", "Pflicht", "Typ", "Min. Länge", "Max. Länge", "Minimum", "Maximum", "Muster (Regex)"
    };
    private static final String[] ENGLISH_LABELS = {
            "Column", "Enabled", "Required", "Type", "Min. length", "Max. length", "Minimum", "Maximum", "Pattern (regex)"
    };
    private final ObjectNode original;
    private final List<Object[]> rows = new ArrayList<>();
    private final UiLanguage language;

    CsvRulesTableModel(JsonNode profile) throws ValidationException {
        this(profile, UiLanguage.DE);
    }

    CsvRulesTableModel(JsonNode profile, UiLanguage language) throws ValidationException {
        this.language = language;
        new CsvValidator().checkProfile(profile);
        original = ((ObjectNode) profile).deepCopy();
        for (JsonNode column : profile.path("columns")) {
            Object[] row = new Object[KEYS.length];
            for (int index = 0; index < KEYS.length; index++) {
                row[index] = switch (index) {
                    case 1 -> column.path("enabled").asBoolean(true);
                    case 2 -> column.path("required").asBoolean(false);
                    default -> column.path(KEYS[index]).asText("");
                };
            }
            rows.add(row);
        }
    }

    ObjectNode toProfile() throws ValidationException {
        ObjectNode result = original.deepCopy();
        var header = result.putArray("header");
        var columns = result.putArray("columns");
        for (Object[] row : rows) {
            header.add(row[0].toString());
            ObjectNode column = columns.addObject();
            for (int index = 0; index < KEYS.length; index++) {
                String key = KEYS[index];
                if (index == 1 || index == 2) {
                    column.put(key, (Boolean) row[index]);
                    continue;
                }
                String value = row[index].toString();
                if (value.isEmpty() && index > 3) {
                    continue;
                }
                try {
                    if (index == 4 || index == 5) {
                        column.put(key, Integer.parseInt(value));
                    } else if (index == 6 || index == 7) {
                        column.put(key, new BigDecimal(value));
                    } else {
                        column.put(key, value);
                    }
                } catch (NumberFormatException e) {
                    throw new ValidationException(language.text("Spalte ", "Column ") + row[0] + ": "
                            + getColumnName(index) + language.text(
                            " muss eine Zahl sein (Dezimalpunkt, keine Tausendertrennzeichen).",
                            " must be a number (decimal point, no thousands separator)."), e);
                }
            }
        }
        return result;
    }

    void addColumn() {
        rows.add(new Object[]{language.text("NEUE_SPALTE", "NEW_COLUMN"),
                true, false, "string", "", "", "", "", ""});
        fireTableRowsInserted(rows.size() - 1, rows.size() - 1);
    }

    void removeColumn(int row) {
        rows.remove(row);
        fireTableRowsDeleted(row, row);
    }

    @Override
    public int getRowCount() {
        return rows.size();
    }

    @Override
    public int getColumnCount() {
        return KEYS.length;
    }

    @Override
    public String getColumnName(int column) {
        return language == UiLanguage.DE ? GERMAN_LABELS[column] : ENGLISH_LABELS[column];
    }

    @Override
    public Class<?> getColumnClass(int column) {
        return column == 1 || column == 2 ? Boolean.class : String.class;
    }

    @Override
    public boolean isCellEditable(int row, int column) {
        return true;
    }

    @Override
    public Object getValueAt(int row, int column) {
        return rows.get(row)[column];
    }

    @Override
    public void setValueAt(Object value, int row, int column) {
        rows.get(row)[column] = value == null ? "" : value;
        fireTableCellUpdated(row, column);
    }
}
