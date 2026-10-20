/*
 * Author: tiny-tool.de
 * Copyright 2026 tiny-tool.de
 * SPDX-License-Identifier: Apache-2.0
 * Licensed under the Apache License, Version 2.0.
 * See LICENSE or https://www.apache.org/licenses/LICENSE-2.0.
 */

package de.tinytool.quality.gui;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.tinytool.quality.adapter.support.BundledProfiles;
import de.tinytool.quality.adapter.support.LocalFiles;
import de.tinytool.quality.adapter.support.ProfileDocuments;
import de.tinytool.quality.core.Finding;
import de.tinytool.quality.core.ValidationResult;
import de.tinytool.quality.report.ReportFormat;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingWorker;
import javax.swing.border.TitledBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;

public final class DesktopFrame extends JFrame {

    private static final Color BACKGROUND = new Color(242, 245, 250);
    private static final Color INK = new Color(24, 42, 65);
    private static final Color ACCENT = new Color(26, 83, 161);
    private static final URI HOMEPAGE = URI.create("https://tiny-tool.de/");
    private static final Path EXAMPLE_INPUT = Path.of("docs", "examples", "fictional-bicycle-rentals.csv");
    private static final Path EXAMPLE_PROFILE = Path.of("docs", "examples", "fictional-bicycle-rentals.csv-profile.json");

    private final LocalValidationService service = new LocalValidationService();
    private final boolean relexAvailable = BundledProfiles.hasRelex();
    private UiLanguage language = UiLanguage.DE;
    private final JComboBox<UiLanguage> languageSelect = named(
            new JComboBox<>(UiLanguage.values()), "language");
    private final BrandBanner brandBanner = new BrandBanner();
    private final JLabel languageCaption = new JLabel();
    private final JLabel localLabel = new JLabel();
    private final JLabel footer = new JLabel();
    private final JLabel inputCaption = new JLabel();
    private final JLabel presetCaption = new JLabel();
    private final JLabel profileCaption = new JLabel();
    private final JLabel formatCaption = new JLabel();
    private final TitledBorder selectionBorder = titledBorder();
    private final TitledBorder resultBorder = titledBorder();
    private final JTextField input = named(new JTextField(), "input");
    private final JTextField profile = named(new JTextField(), "profile");
    private final JComboBox<String> preset = named(new JComboBox<>(), "preset");
    private final JButton browseInput = named(new JButton(), "browseInput");
    private final JButton browseProfile = named(new JButton(), "browseProfile");
    private final JButton configure = named(new JButton(), "configure");
    private final JButton validate = named(new JButton(), "validate");
    private final JButton save = named(new JButton(), "save");
    private final JComboBox<ReportFormat> reportFormat = named(new JComboBox<>(ReportFormat.values()), "reportFormat");
    private final JLabel status = named(new JLabel(), "status");
    private final JLabel summary = named(new JLabel(), "summary");
    private final JLabel context = named(new JLabel(" "), "context");
    private final JProgressBar progress = new JProgressBar();
    private final FindingsTableModel findings = new FindingsTableModel();
    private final JTable table = named(new JTable(findings), "findings");
    private final JTextArea detail = named(new JTextArea(4, 40), "detail");
    private final JLabel inputLed = named(new JLabel("", LedIcon.IDLE, JLabel.LEFT), "inputLed");
    private final JLabel profileLed = named(new JLabel("", LedIcon.VALID, JLabel.LEFT), "profileLed");
    private final JLabel profileGuide = named(new JLabel(), "profileGuide");
    private ValidationResult lastResult;
    private Path checkedInput;
    private Path checkedProfile;
    private boolean busy;
    private int previousPreset = relexAvailable ? 1 : 0;
    private String customInput = EXAMPLE_INPUT.toString();
    private String customProfile = EXAMPLE_PROFILE.toString();
    private String relexInput = "";
    private double lastDurationSeconds;
    private boolean applyingLanguage;

    public DesktopFrame() {
        super("ZEUS Interface Quality");
        setName("zeus");
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setMinimumSize(new Dimension(880, 720));
        setSize(1100, 900);
        setLocationRelativeTo(null);
        setContentPane(content());
        input.setText(customInput);
        profile.setText(customProfile);
        wireActions();
        applyLanguage();
        updateProfileSelection();
        invalidateResult();
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                if (busy) {
                    JOptionPane.showMessageDialog(DesktopFrame.this,
                            t("Bitte die laufende Prüfung oder Speicherung abwarten.",
                                    "Please wait for validation or saving to finish."),
                            t("Vorgang läuft", "Operation in progress"), JOptionPane.INFORMATION_MESSAGE);
                } else {
                    dispose();
                }
            }
        });
    }

    private JPanel content() {
        JPanel root = new JPanel(new BorderLayout(0, 12));
        root.setBackground(BACKGROUND);
        root.setBorder(BorderFactory.createEmptyBorder(16, 28, 12, 28));
        JPanel heading = new JPanel(new BorderLayout());
        heading.setOpaque(false);
        JLabel title = new JLabel("ZEUS  /  Interface Quality");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 26f));
        title.setForeground(INK);
        JPanel titleBlock = new JPanel();
        titleBlock.setOpaque(false);
        titleBlock.setLayout(new BoxLayout(titleBlock, BoxLayout.Y_AXIS));
        titleBlock.add(title);
        localLabel.setForeground(ACCENT);
        titleBlock.add(localLabel);
        heading.add(titleBlock, BorderLayout.WEST);
        JPanel headingActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        headingActions.setOpaque(false);
        headingActions.add(brandBanner);
        headingActions.add(languageCaption);
        headingActions.add(languageSelect);
        heading.add(headingActions, BorderLayout.EAST);
        root.add(heading, BorderLayout.NORTH);

        JPanel body = new JPanel(new BorderLayout(0, 12));
        body.setOpaque(false);
        body.add(selectionPanel(), BorderLayout.NORTH);
        body.add(resultPanel(), BorderLayout.CENTER);
        root.add(body, BorderLayout.CENTER);
        footer.setForeground(INK);
        root.add(footer, BorderLayout.SOUTH);
        return root;
    }

    private JPanel selectionPanel() {
        JPanel card = card(selectionBorder);
        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        addField(form, 0, inputCaption, input, browseInput);
        addField(form, 1, presetCaption, preset, null);
        addField(form, 2, profileCaption, profile, browseProfile);
        GridBagConstraints guideConstraints = new GridBagConstraints();
        guideConstraints.gridx = 0;
        guideConstraints.gridy = 3;
        guideConstraints.gridwidth = 3;
        guideConstraints.weightx = 1;
        guideConstraints.fill = GridBagConstraints.HORIZONTAL;
        guideConstraints.insets = new Insets(8, 0, 0, 0);
        profileGuide.setForeground(INK);
        form.add(profileGuide, guideConstraints);
        card.add(form, BorderLayout.CENTER);

        JPanel actions = new JPanel(new BorderLayout(16, 0));
        actions.setOpaque(false);
        JPanel indicators = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 12, 0));
        indicators.setOpaque(false);
        indicators.add(inputLed);
        indicators.add(profileLed);
        actions.add(indicators, BorderLayout.WEST);
        progress.setIndeterminate(false);
        progress.setVisible(false);
        actions.add(progress, BorderLayout.CENTER);
        validate.setFont(validate.getFont().deriveFont(Font.BOLD));
        JPanel buttons = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        buttons.add(configure);
        buttons.add(validate);
        actions.add(buttons, BorderLayout.EAST);
        actions.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));
        card.add(actions, BorderLayout.SOUTH);
        return card;
    }

    private JPanel resultPanel() {
        JPanel card = card(resultBorder);
        JPanel state = new JPanel();
        state.setLayout(new BoxLayout(state, BoxLayout.Y_AXIS));
        state.setOpaque(false);
        status.setFont(status.getFont().deriveFont(Font.BOLD, 18f));
        status.setForeground(ACCENT);
        state.add(status);
        state.add(Box.createVerticalStrut(4));
        state.add(summary);
        state.add(Box.createVerticalStrut(4));
        state.add(context);
        state.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));
        card.add(state, BorderLayout.NORTH);

        table.setRowHeight(28);
        table.setAutoCreateRowSorter(true);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setFillsViewportHeight(true);
        table.getTableHeader().setReorderingAllowed(false);
        table.getColumnModel().getColumn(0).setPreferredWidth(240);
        table.getColumnModel().getColumn(1).setPreferredWidth(110);
        table.getColumnModel().getColumn(2).setPreferredWidth(600);
        detail.setEditable(false);
        detail.setLineWrap(true);
        detail.setWrapStyleWord(true);
        detail.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        JScrollPane tableScroll = new JScrollPane(table);
        JScrollPane detailScroll = new JScrollPane(detail);
        tableScroll.setMinimumSize(new Dimension(200, 130));
        detailScroll.setMinimumSize(new Dimension(200, 90));
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, tableScroll, detailScroll);
        split.setResizeWeight(0.65);
        split.setDividerLocation(0.65);
        split.setBorder(BorderFactory.createEmptyBorder());
        card.add(split, BorderLayout.CENTER);

        JPanel export = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 8, 0));
        export.setOpaque(false);
        export.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));
        export.add(formatCaption);
        export.add(reportFormat);
        export.add(save);
        save.setEnabled(false);
        card.add(export, BorderLayout.SOUTH);
        return card;
    }

    private static TitledBorder titledBorder() {
        return BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(218, 225, 235)), "",
                TitledBorder.LEADING, TitledBorder.TOP,
                new Font(Font.DIALOG, Font.BOLD, 15), INK);
    }

    private static JPanel card(TitledBorder title) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(Color.WHITE);
        panel.setBorder(BorderFactory.createCompoundBorder(
                title,
                BorderFactory.createEmptyBorder(12, 18, 12, 18)));
        return panel;
    }

    private static void addField(JPanel form, int row, JLabel label, JComponent field, JButton button) {
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridy = row;
        constraints.insets = new Insets(4, 0, 4, 12);
        constraints.anchor = GridBagConstraints.WEST;
        label.setLabelFor(field);
        form.add(label, constraints);
        constraints.gridx = 1;
        constraints.weightx = 1;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        form.add(field, constraints);
        if (button != null) {
            constraints.gridx = 2;
            constraints.weightx = 0;
            constraints.insets = new Insets(4, 0, 4, 0);
            form.add(button, constraints);
        }
    }

    private String t(String german, String english) {
        return language.text(german, english);
    }

    private int customIndex() {
        return relexAvailable ? 1 : 0;
    }

    private boolean relexSelected() {
        return relexAvailable && preset.getSelectedIndex() == 0;
    }

    private void applyLanguage() {
        int selected = preset.getSelectedIndex();
        applyingLanguage = true;
        try {
            preset.removeAllItems();
            if (relexAvailable) {
                preset.addItem(t("Lokales Relex-Profil (im JAR)", "Local Relex profile (in JAR)"));
            }
            preset.addItem(t("JSON-Profil / JSON Schema aus Datei", "JSON profile / JSON Schema from file"));
            preset.setSelectedIndex(selected < 0 ? customIndex() : selected);
        } finally {
            applyingLanguage = false;
        }
        setTitle(t("ZEUS Interface Quality – Lokale Prüfung",
                "ZEUS Interface Quality – Local validation"));
        localLabel.setText(t("LOKAL | OFFLINE", "LOCAL | OFFLINE"));
        languageCaption.setText(t("Sprache:", "Language:"));
        brandBanner.setToolTipText(t("Tiny Tool im Standardbrowser öffnen: " + HOMEPAGE,
                "Open Tiny Tool in your default browser: " + HOMEPAGE));
        footer.setText(t("Nur lokale Dateien. Kein Upload, kein SFTP. Reports standardmäßig unter reports/.",
                "Local files only. No upload or SFTP. Reports default to reports/."));
        selectionBorder.setTitle(t("1  Daten und Prüfregeln", "1  Data and validation rules"));
        resultBorder.setTitle(t("2  Prüfergebnis", "2  Validation result"));
        inputCaption.setText(t("Datendatei (CSV/JSON)", "Data file (CSV/JSON)"));
        presetCaption.setText(t("Profilquelle", "Profile source"));
        profileCaption.setText(t("Prüfregeln (JSON)", "Validation rules (JSON)"));
        formatCaption.setText(t("Ergebnisformat:", "Result format:"));
        browseInput.setText(t("Datei auswählen ...", "Choose data file ..."));
        browseProfile.setText(t("Profil auswählen ...", "Choose profile ..."));
        configure.setText(t("CSV-Regeln konfigurieren ...", "Configure CSV rules ..."));
        validate.setText(t("Jetzt prüfen", "Validate now"));
        save.setText(t("Ergebnis speichern ...", "Save result ..."));
        inputLed.setText(t("Datei", "File"));
        profileLed.setText(t("Profil", "Profile"));
        validate.setToolTipText(t("Prüfung starten (Strg+Enter)",
                "Start validation (Ctrl+Enter)"));
        preset.setToolTipText(relexAvailable
                ? t("Profilquelle wählen", "Choose profile source")
                : t("Das lokale Relex-Profil fehlt im JAR und wird daher nicht angeboten.",
                        "The local Relex profile is absent from the JAR and is not offered."));
        findings.setLanguage(language);
        table.getTableHeader().repaint();
        updateFileLeds();
        updateProfileSelection();
        if (lastResult != null) {
            renderCompletedResult();
        } else if (!busy) {
            invalidateResult();
        }
        repaint();
    }

    private void openHomepage() {
        try {
            if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                throw new UnsupportedOperationException(t("Kein Systembrowser verfügbar.",
                        "No system browser is available."));
            }
            Desktop.getDesktop().browse(HOMEPAGE);
        } catch (IOException | UnsupportedOperationException | SecurityException e) {
            showError(t("Website konnte nicht geöffnet werden", "Could not open website"), e);
        }
    }

    private void wireActions() {
        browseInput.setMnemonic(KeyEvent.VK_D);
        browseProfile.setMnemonic(KeyEvent.VK_P);
        save.setMnemonic(KeyEvent.VK_R);
        browseInput.addActionListener(event -> chooseInput());
        browseProfile.addActionListener(event -> chooseProfile());
        preset.addActionListener(event -> switchProfileSource());
        validate.addActionListener(event -> startValidation());
        save.addActionListener(event -> chooseReport());
        configure.addActionListener(event -> configureProfile());
        brandBanner.addActionListener(event -> openHomepage());
        languageSelect.addActionListener(event -> {
            UiLanguage selected = (UiLanguage) languageSelect.getSelectedItem();
            if (selected != null && selected != language) {
                language = selected;
                applyLanguage();
            }
        });
        DocumentListener change = new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent event) {
                invalidateResult();
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                invalidateResult();
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                invalidateResult();
            }
        };
        input.getDocument().addDocumentListener(change);
        profile.getDocument().addDocumentListener(change);
        table.getSelectionModel().addListSelectionListener(event -> {
            int row = table.getSelectedRow();
            if (row >= 0) {
                Finding finding = findings.findingAt(table.convertRowIndexToModel(row));
                detail.setText(finding.message() + "\n" + t("Datenpfad: ", "Data path: ")
                        + finding.instancePath() + "\n" + t("Profilpfad: ", "Profile path: ")
                        + finding.schemaPath() + "\n" + t("Regel: ", "Rule: ") + finding.keyword());
                detail.setCaretPosition(0);
            }
        });
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, KeyEvent.CTRL_DOWN_MASK), "validate");
        getRootPane().getActionMap().put("validate", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                if (!busy) {
                    startValidation();
                }
            }
        });
    }

    private void updateProfileSelection() {
        boolean custom = !relexSelected();
        boolean available = custom || relexAvailable;
        profile.setEnabled(!busy && custom);
        browseProfile.setEnabled(!busy && custom);
        validate.setEnabled(!busy && available);
        configure.setEnabled(!busy && available);
        profile.setToolTipText(custom
                ? t("JSON mit Prüfregeln: CSV-/Fixed-Width-Profil oder JSON Schema",
                        "JSON rules: CSV/fixed-width profile or JSON Schema")
                : t("Relex-Regeln werden aus dem lokal gebauten JAR geladen.",
                        "Relex rules are loaded from the locally built JAR."));
        updateProfileGuide();
    }

    private void switchProfileSource() {
        if (applyingLanguage) {
            return;
        }
        int selected = preset.getSelectedIndex();
        if (selected == previousPreset) {
            return;
        }
        if (previousPreset == customIndex()) {
            customInput = input.getText();
            customProfile = profile.getText();
        } else {
            relexInput = input.getText();
        }
        previousPreset = selected;
        input.setText(relexSelected() ? relexInput : customInput);
        profile.setText(relexSelected() ? "" : customProfile);
        updateProfileSelection();
        invalidateResult();
    }

    private void updateProfileGuide() {
        String selected = relexSelected()
                ? t("Aktiv: lokales Relex-Profil aus dem JAR. Bitte eine lokale CSV wählen.",
                        "Active: local Relex profile from the JAR. Select a local CSV.")
                : isExampleSelection()
                        ? t("Aktiv: Beispiel-CSV mit passender JSON-Regeldatei.",
                                "Active: sample CSV with matching JSON rules.")
                        : t("Aktiv: eigene Datendatei und eigene JSON-Regeldatei.",
                                "Active: your own data file and JSON rules file.");
        profileGuide.setText("<html>" + selected
                + "<br>" + t("CSV: *.csv-profile.json; JSON-Daten: *.schema.json; "
                        + "Fixed-Width: Profil-JSON; Ergebnis-JSON: Report nach der Prüfung.",
                        "CSV: *.csv-profile.json; JSON data: *.schema.json; "
                        + "fixed-width: profile JSON; result JSON: report after validation.") + "</html>");
    }

    private boolean isExampleSelection() {
        try {
            return Path.of(input.getText().strip()).toAbsolutePath().normalize()
                    .equals(EXAMPLE_INPUT.toAbsolutePath().normalize())
                    && Path.of(profile.getText().strip()).toAbsolutePath().normalize()
                    .equals(EXAMPLE_PROFILE.toAbsolutePath().normalize());
        } catch (java.nio.file.InvalidPathException e) {
            return false;
        }
    }

    private void invalidateResult() {
        input.setToolTipText(input.getText().isBlank()
                ? t("Lokale Datendatei", "Local data file") : input.getText());
        updateFileLeds();
        updateProfileGuide();
        lastResult = null;
        checkedInput = null;
        checkedProfile = null;
        save.setEnabled(false);
        findings.setFindings(List.of());
        setStatus(LedIcon.IDLE, t("Datei auswählen und Prüfung starten.",
                "Choose a file and start validation."));
        status.setForeground(ACCENT);
        summary.setText(t("Noch keine aktuelle Prüfung.", "No current validation result."));
        context.setText(" ");
        context.setToolTipText(null);
        detail.setText("");
    }

    private void updateFileLeds() {
        inputLed.setIcon(fileLed(input.getText()));
        inputLed.setToolTipText(t("Grau = nicht ausgewählt, grün = Datei vorhanden, rot = Datei fehlt. Keine Inhaltsprüfung.",
                "Gray = not selected, green = file exists, red = file missing. Content not checked."));
        profileLed.setIcon(relexSelected()
                ? LedIcon.VALID
                : fileLed(profile.getText()));
        profileLed.setToolTipText(relexSelected()
                ? t("Lokales Relex-Profil im JAR", "Local Relex profile in JAR")
                : t("Dateiexistenz; Profilregeln werden beim Prüfen validiert.",
                        "File existence; profile rules are checked during validation."));
    }

    private static LedIcon fileLed(String text) {
        if (text.isBlank()) {
            return LedIcon.IDLE;
        }
        try {
            return Files.isRegularFile(Path.of(text.strip())) ? LedIcon.VALID : LedIcon.ERROR;
        } catch (java.nio.file.InvalidPathException e) {
            return LedIcon.ERROR;
        }
    }

    private void setStatus(LedIcon led, String text) {
        status.setIcon(led);
        status.setIconTextGap(10);
        status.setText(text);
        status.getAccessibleContext().setAccessibleDescription(led.name() + ": " + text);
    }

    private void configureProfile() {
        try {
            ObjectMapper mapper = ProfileDocuments.strictMapper(new ObjectMapper());
            Path source = null;
            com.fasterxml.jackson.databind.JsonNode document;
            if (relexSelected()) {
                document = mapper.readTree(BundledProfiles.relex());
            } else {
                if (profile.getText().isBlank()) {
                    throw new java.io.IOException(t("Bitte ein CSV-Profil wählen oder das Relex-Preset verwenden.",
                            "Choose a CSV profile or use the Relex preset."));
                }
                source = Path.of(profile.getText().strip()).toAbsolutePath().normalize();
                document = ProfileDocuments.readRoot(source, mapper, "CSV profile");
                if (source.equals(EXAMPLE_PROFILE.toAbsolutePath().normalize())) {
                    source = null;
                }
            }
            Path data = input.getText().isBlank() ? null : Path.of(input.getText().strip());
            CsvProfileEditor editor = new CsvProfileEditor(this, document, data, source, language);
            editor.setVisible(true);
            if (editor.savedProfile() != null) {
                String selectedInput = input.getText();
                preset.setSelectedIndex(customIndex());
                input.setText(selectedInput);
                profile.setText(editor.savedProfile().toString());
            }
        } catch (de.tinytool.quality.core.ValidationException | java.io.IOException | IllegalArgumentException e) {
            showError(t("CSV-Konfiguration nicht verfügbar", "CSV configuration unavailable"), e);
        }
    }

    private void chooseInput() {
        JFileChooser chooser = chooser(t("Datendatei auswählen", "Choose data file"));
        chooser.setFileFilter(new FileNameExtensionFilter(t("Datendateien (*.csv, *.json, *.dat, *.txt)",
                "Data files (*.csv, *.json, *.dat, *.txt)"),
                "csv", "json", "dat", "txt"));
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            input.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    private void chooseProfile() {
        JFileChooser chooser = chooser(t("Lokales Prüfprofil auswählen", "Choose local validation profile"));
        chooser.setFileFilter(new FileNameExtensionFilter(t("JSON-Profil / JSON Schema (*.json)",
                "JSON profile / JSON Schema (*.json)"), "json"));
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            profile.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    private JFileChooser chooser(String title) {
        Path data = Path.of("data").toAbsolutePath();
        JFileChooser chooser = new JFileChooser(Files.isDirectory(data) ? data.toFile() : null);
        chooser.setDialogTitle(title);
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        return chooser;
    }

    private void startValidation() {
        final Path inputPath;
        final Path profilePath;
        try {
            if (input.getText().isBlank()) {
                throw new IllegalArgumentException(t("Bitte eine lokale Datendatei auswählen.",
                        "Please choose a local data file."));
            }
            inputPath = Path.of(input.getText().strip()).toAbsolutePath().normalize();
            if (!relexSelected() && profile.getText().isBlank()) {
                throw new IllegalArgumentException(t("Bitte eine lokale Profildatei auswählen.",
                        "Please choose a local profile file."));
            }
            profilePath = relexSelected()
                    ? null : Path.of(profile.getText().strip()).toAbsolutePath().normalize();
        } catch (IllegalArgumentException e) {
            setStatus(LedIcon.ERROR, t("FEHLER – Auswahl unvollständig",
                    "ERROR – incomplete selection"));
            showError(t("Auswahl unvollständig", "Incomplete selection"), e);
            return;
        }
        invalidateResult();
        setBusy(true);
        setStatus(LedIcon.RUNNING, t("Prüfung läuft ...", "Validation running ..."));
        summary.setText(t("Die Anwendung bleibt bedienbar. Die Eingabedatei wird nicht verändert.",
                "The app stays responsive. The input file is not changed."));
        long start = System.nanoTime();
        new SwingWorker<ValidationResult, Void>() {
            @Override
            protected ValidationResult doInBackground() throws Exception {
                return profilePath == null
                        ? service.validateRelex(inputPath) : service.validate(inputPath, profilePath);
            }

            @Override
            protected void done() {
                try {
                    lastResult = get();
                    checkedInput = inputPath;
                    checkedProfile = profilePath;
                    findings.setFindings(lastResult.findings());
                    lastDurationSeconds = (System.nanoTime() - start) / 1_000_000_000.0;
                    renderCompletedResult();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    validationFailed(e);
                } catch (ExecutionException e) {
                    validationFailed(e.getCause());
                } finally {
                    setBusy(false);
                }
            }
        }.execute();
    }

    private void validationFailed(Throwable exception) {
        lastResult = null;
        setStatus(LedIcon.ERROR, t("FEHLER – Prüfung nicht abgeschlossen",
                "ERROR – validation did not finish"));
        status.setForeground(new Color(159, 47, 30));
        summary.setText(t("Kein gültiges Prüfergebnis vorhanden.", "No valid result is available."));
        detail.setText(LocalFiles.safeMessage(exception));
        showError(t("Prüfung fehlgeschlagen", "Validation failed"), exception);
    }

    private void renderCompletedResult() {
        if (lastResult == null) {
            return;
        }
        setStatus(lastResult.isValid() ? LedIcon.VALID : LedIcon.INVALID,
                lastResult.isValid() ? t("GÜLTIG – Prüfung bestanden", "VALID – validation passed")
                        : t("UNGÜLTIG – Daten bitte korrigieren", "INVALID – please correct the data"));
        status.setForeground(lastResult.isValid() ? new Color(22, 102, 61) : new Color(159, 47, 30));
        summary.setText(lastResult.findings().size()
                + t(" Befunde | Dauer: ", " findings | Duration: ")
                + String.format(language.locale(), "%.2f s", lastDurationSeconds));
        String profileName = checkedProfile == null
                ? t("Relex-Profil im JAR", "Relex profile in JAR") : checkedProfile.getFileName().toString();
        context.setText(t("Datei: ", "File: ") + checkedInput.getFileName()
                + t(" | Prüfregeln: ", " | Rules: ") + profileName);
        context.setToolTipText(t("Datei: ", "File: ") + checkedInput
                + t(" | Prüfregeln: ", " | Rules: ")
                + (checkedProfile == null ? profileName : checkedProfile));
        if (findings.getRowCount() == 0) {
            detail.setText(t("Keine Regelverletzungen gefunden. Das Ergebnis gilt für das ausgewählte Profil.",
                    "No rule violations found. The result applies to the selected profile."));
        } else {
            table.setRowSelectionInterval(0, 0);
        }
    }

    private void chooseReport() {
        if (lastResult == null || busy) {
            return;
        }
        ReportFormat format = (ReportFormat) reportFormat.getSelectedItem();
        String extension = format == ReportFormat.JSON ? ".json" : ".txt";
        Path reports = Path.of("reports").toAbsolutePath();
        try {
            Files.createDirectories(reports);
        } catch (IOException e) {
            showError(t("Reportverzeichnis nicht verfügbar", "Report directory unavailable"), e);
            return;
        }
        JFileChooser chooser = new JFileChooser(reports.toFile());
        chooser.setDialogTitle(t("Prüfreport speichern", "Save validation report"));
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        chooser.setSelectedFile(reports.resolve("zeus.report" + extension).toFile());
        chooser.setFileFilter(new FileNameExtensionFilter(t("Report (*", "Report (*") + extension + ")",
                extension.substring(1)));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        Path target = chooser.getSelectedFile().toPath().toAbsolutePath().normalize();
        if (!target.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(extension)) {
            target = target.resolveSibling(target.getFileName() + extension);
        }
        if (Files.exists(target) && JOptionPane.showConfirmDialog(this,
                t("Vorhandene Datei ersetzen?\n", "Replace existing file?\n") + target,
                t("Report speichern", "Save report"),
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION) {
            return;
        }
        saveReport(target, format);
    }

    private void saveReport(Path target, ReportFormat format) {
        ValidationResult result = lastResult;
        Path source = checkedInput;
        Path schema = checkedProfile;
        setBusy(true);
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                service.saveReport(target, result, format, source, schema);
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    JOptionPane.showMessageDialog(DesktopFrame.this,
                            t("Report gespeichert:\n", "Report saved:\n") + target,
                            t("Report gespeichert", "Report saved"), JOptionPane.INFORMATION_MESSAGE);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    showError(t("Report nicht gespeichert", "Report not saved"), e);
                } catch (ExecutionException e) {
                    showError(t("Report nicht gespeichert", "Report not saved"), e.getCause());
                } finally {
                    setBusy(false);
                }
            }
        }.execute();
    }

    private void setBusy(boolean value) {
        busy = value;
        input.setEnabled(!busy);
        preset.setEnabled(!busy);
        browseInput.setEnabled(!busy);
        validate.setEnabled(!busy);
        configure.setEnabled(!busy);
        reportFormat.setEnabled(!busy);
        languageSelect.setEnabled(!busy);
        progress.setIndeterminate(busy);
        progress.setVisible(busy);
        save.setEnabled(!busy && lastResult != null);
        updateProfileSelection();
    }

    private void showError(String title, Throwable exception) {
        JOptionPane.showMessageDialog(this, LocalFiles.safeMessage(exception), title, JOptionPane.ERROR_MESSAGE);
    }

    private static <T extends JComponent> T named(T component, String name) {
        component.setName(name);
        return component;
    }
}
