/*
 * Author: tiny-tool.de
 * Copyright 2026 tiny-tool.de
 * SPDX-License-Identifier: Apache-2.0
 * Licensed under the Apache License, Version 2.0.
 * See LICENSE or https://www.apache.org/licenses/LICENSE-2.0.
 */

package de.tinytool.quality.gui;

import de.tinytool.quality.core.Finding;

import javax.swing.table.AbstractTableModel;
import java.util.List;

final class FindingsTableModel extends AbstractTableModel {

    private List<Finding> findings = List.of();
    private UiLanguage language = UiLanguage.DE;

    void setLanguage(UiLanguage language) {
        this.language = language;
        fireTableDataChanged();
    }

    void setFindings(List<Finding> findings) {
        this.findings = List.copyOf(findings);
        fireTableDataChanged();
    }

    Finding findingAt(int row) {
        return findings.get(row);
    }

    @Override
    public int getRowCount() {
        return findings.size();
    }

    @Override
    public int getColumnCount() {
        return 3;
    }

    @Override
    public String getColumnName(int column) {
        return switch (column) {
            case 0 -> language.text("Datenpfad", "Data path");
            case 1 -> language.text("Regel", "Rule");
            case 2 -> language.text("Beschreibung", "Description");
            default -> throw new IndexOutOfBoundsException(column);
        };
    }

    @Override
    public Object getValueAt(int row, int column) {
        Finding finding = findingAt(row);
        return switch (column) {
            case 0 -> finding.instancePath().isEmpty()
                    ? language.text("(Datei)", "(file)") : finding.instancePath();
            case 1 -> finding.keyword();
            case 2 -> finding.message();
            default -> throw new IndexOutOfBoundsException("Unknown finding column: " + column);
        };
    }
}
