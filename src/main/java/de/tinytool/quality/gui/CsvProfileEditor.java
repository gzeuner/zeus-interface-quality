/*
 * Author: tiny-tool.de
 * Copyright 2026 tiny-tool.de
 * SPDX-License-Identifier: Apache-2.0
 * Licensed under the Apache License, Version 2.0.
 * See LICENSE or https://www.apache.org/licenses/LICENSE-2.0.
 */

package de.tinytool.quality.gui;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.tinytool.quality.adapter.csv.CsvValidator;
import de.tinytool.quality.adapter.support.LocalFiles;
import de.tinytool.quality.adapter.support.LocalOutputs;
import de.tinytool.quality.core.ValidationException;

import javax.swing.BorderFactory;
import javax.swing.DefaultCellEditor;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

final class CsvProfileEditor extends JDialog {

    private final CsvRulesTableModel model;
    private final JTable table;
    private final Map<String, JTextField> fields = new LinkedHashMap<>();
    private final JTextArea uniqueBy = new JTextArea(2, 30);
    private final JCheckBox correction = new JCheckBox();
    private final Path input;
    private final Path originalProfile;
    private final UiLanguage language;
    private Path savedProfile;

    CsvProfileEditor(JFrame owner, JsonNode profile, Path input, Path originalProfile) throws ValidationException {
        this(owner, profile, input, originalProfile, UiLanguage.DE);
    }

    CsvProfileEditor(JFrame owner, JsonNode profile, Path input, Path originalProfile,
                     UiLanguage language) throws ValidationException {
        super(owner, language.text("CSV-Prüfregeln konfigurieren", "Configure CSV validation rules"), true);
        setName("profileEditor");
        this.input = input;
        this.originalProfile = originalProfile;
        this.language = language;
        model = new CsvRulesTableModel(profile, language);
        table = new JTable(model);
        table.setName("rules");
        table.setRowHeight(26);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        for (int i = 0; i < table.getColumnCount(); i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(i == 0 || i == 8 ? 200 : i < 3 ? 65 : 105);
        }
        table.getColumnModel().getColumn(3).setCellEditor(new DefaultCellEditor(
                new JComboBox<>(new String[]{"string", "integer", "decimal", "email", "date", "time"})));
        JPanel root = new JPanel(new BorderLayout(12, 12));
        root.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        JPanel settings = new JPanel(new GridLayout(0, 4, 8, 8));
        addField(settings, t("Trennzeichen", "Delimiter"), "delimiter", profile.path("delimiter").asText());
        addField(settings, t("Zeichensatz", "Encoding"), "encoding", profile.path("encoding").asText());
        addField(settings, t("Dezimalzeichen", "Decimal separator"), "decimalSeparator",
                profile.path("decimalSeparator").asText("."));
        addField(settings, t("Anfangsmarker (optional)", "Start marker (optional)"), "begin",
                profile.path("envelope").path("begin").asText(""));
        addField(settings, t("Endmarker (optional)", "End marker (optional)"), "end",
                profile.path("envelope").path("end").asText(""));
        addField(settings, t("Min. Datensätze (optional)", "Min. records (optional)"), "minRecords",
                profile.path("minRecords").asText(""));
        addField(settings, t("Max. Datensätze (optional)", "Max. records (optional)"), "maxRecords",
                profile.path("maxRecords").asText(""));
        addField(settings, t("Dateinamenmuster (optional)", "File name pattern (optional)"), "fileNamePattern",
                profile.path("fileNamePattern").asText(""));
        correction.setSelected(profile.path("correction").path("enabled").asBoolean(false));
        correction.setText(t("Fehlerhafte Datensätze entfernen und protokollieren",
                "Remove and log invalid records"));
        settings.add(new JLabel(t("Korrektur (standardmäßig aus)", "Correction (off by default)")));
        settings.add(correction);
        JPanel top = new JPanel(new BorderLayout(8, 8));
        top.add(settings, BorderLayout.NORTH);
        JPanel key = new JPanel(new BorderLayout(8, 8));
        key.add(new JLabel(t("Eindeutiger Schlüssel: ein Spaltenname pro Zeile (leer = aus)",
                "Unique key: one column name per line (empty = off)")), BorderLayout.NORTH);
        uniqueBy.setName("uniqueBy");
        if (profile.path("uniqueBy").isArray()) {
            StringBuilder names = new StringBuilder();
            profile.path("uniqueBy").forEach(node -> names.append(node.asText()).append('\n'));
            uniqueBy.setText(names.toString());
        }
        key.add(new JScrollPane(uniqueBy), BorderLayout.CENTER);
        top.add(key, BorderLayout.CENTER);
        root.add(top, BorderLayout.NORTH);
        root.add(new JScrollPane(table), BorderLayout.CENTER);
        JPanel bottom = new JPanel(new BorderLayout(0, 8));
        bottom.add(new JLabel("<html>" + t(
                "Aktiv = Spaltenregeln anwenden. Kopfzeile und Spaltenanzahl bleiben verbindlich."
                        + "<br>Grenzwerte mit Dezimalpunkt. Leere Regelfelder = keine Einschränkung."
                        + "<br>Dateinamenmuster prüft den vollständigen Basisnamen."
                        + " Speichern erzeugt ein portables CLI-/GUI-Profil.",
                "Enabled = apply column rules. Header and column count remain mandatory."
                        + "<br>Use a decimal point for limits. Empty rule fields = no restriction."
                        + "<br>File name pattern matches the complete basename."
                        + " Saving creates a portable CLI/GUI profile.") + "</html>"), BorderLayout.NORTH);
        JPanel actions = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT));
        JButton add = new JButton(t("Spalte hinzufügen", "Add column"));
        add.addActionListener(event -> model.addColumn());
        JButton remove = new JButton(t("Spalte entfernen", "Remove column"));
        remove.addActionListener(event -> {
            if (table.getSelectedRow() >= 0) {
                if (table.isEditing() && !table.getCellEditor().stopCellEditing()) {
                    return;
                }
                model.removeColumn(table.getSelectedRow());
            }
        });
        JButton cancel = new JButton(t("Abbrechen", "Cancel"));
        cancel.addActionListener(event -> dispose());
        JButton save = new JButton(t("Profil speichern und verwenden ...", "Save and use profile ..."));
        save.setName("saveProfile");
        save.addActionListener(event -> save());
        actions.add(add);
        actions.add(remove);
        actions.add(cancel);
        actions.add(save);
        bottom.add(actions, BorderLayout.SOUTH);
        root.add(bottom, BorderLayout.SOUTH);
        setContentPane(root);
        setMinimumSize(new Dimension(950, 600));
        setSize(1180, 700);
        setLocationRelativeTo(owner);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
    }

    private void addField(JPanel parent, String label, String key, String value) {
        JTextField field = new JTextField(value);
        field.setName(key);
        fields.put(key, field);
        JLabel caption = new JLabel(label);
        caption.setLabelFor(field);
        parent.add(caption);
        parent.add(field);
    }

    private String t(String german, String english) {
        return language.text(german, english);
    }

    ObjectNode configuredProfile() throws ValidationException {
        if (table.isEditing() && !table.getCellEditor().stopCellEditing()) {
            throw new ValidationException(t("Bitte die Bearbeitung der Zelle abschließen.",
                    "Please finish editing the cell."));
        }
        ObjectNode result = model.toProfile();
        for (String key : new String[]{"delimiter", "encoding", "decimalSeparator"}) {
            result.put(key, fields.get(key).getText());
        }
        for (String key : new String[]{"minRecords", "maxRecords"}) {
            String value = fields.get(key).getText().strip();
            if (value.isEmpty()) {
                result.remove(key);
            } else {
                try {
                    result.put(key, Integer.parseInt(value));
                } catch (NumberFormatException e) {
                    throw new ValidationException(key + t(" muss eine nicht-negative ganze Zahl sein.",
                            " must be a non-negative integer."), e);
                }
            }
        }
        String fileNamePattern = fields.get("fileNamePattern").getText();
        if (fileNamePattern.isBlank()) {
            result.remove("fileNamePattern");
        } else {
            result.put("fileNamePattern", fileNamePattern);
        }
        String begin = fields.get("begin").getText();
        String end = fields.get("end").getText();
        result.remove("envelope");
        if (!begin.isEmpty() || !end.isEmpty()) {
            result.putObject("envelope").put("begin", begin).put("end", end);
        }
        result.remove("uniqueBy");
        if (!uniqueBy.getText().isBlank()) {
            var keys = result.putArray("uniqueBy");
            uniqueBy.getText().lines().filter(line -> !line.isBlank()).forEach(keys::add);
        }
        result.putObject("correction").put("enabled", correction.isSelected());
        new CsvValidator().checkProfile(result);
        return result;
    }

    private void save() {
        try {
            ObjectNode configured = configuredProfile();
            Path dataDirectory = Path.of("data").toAbsolutePath();
            Files.createDirectories(dataDirectory);
            JFileChooser chooser = new JFileChooser(dataDirectory.toFile());
            chooser.setDialogTitle(t("Portables Prüfprofil speichern", "Save portable validation profile"));
            chooser.setFileFilter(new FileNameExtensionFilter(t("CSV-Profil (*.json)",
                    "CSV profile (*.json)"), "json"));
            chooser.setSelectedFile(originalProfile == null
                    ? dataDirectory.resolve("custom.csv-profile.json").toFile() : originalProfile.toFile());
            if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
                return;
            }
            Path target = chooser.getSelectedFile().toPath().toAbsolutePath().normalize();
            if (!target.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".json")) {
                target = target.resolveSibling(target.getFileName() + ".json");
            }
            LocalOutputs.protectSources(target, input);
            if (Files.exists(target) && JOptionPane.showConfirmDialog(this,
                    t("Profil ersetzen?\n", "Replace profile?\n") + target,
                    t("Profil speichern", "Save profile"), JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION) {
                return;
            }
            LocalOutputs.write(target, new ObjectMapper().writerWithDefaultPrettyPrinter()
                    .writeValueAsString(configured), input);
            savedProfile = target;
            dispose();
        } catch (ValidationException | IOException e) {
            JOptionPane.showMessageDialog(this, LocalFiles.safeMessage(e),
                    t("Profil nicht gespeichert", "Profile not saved"), JOptionPane.ERROR_MESSAGE);
        }
    }

    Path savedProfile() {
        return savedProfile;
    }
}
