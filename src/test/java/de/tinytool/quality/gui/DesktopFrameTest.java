/*
 * Author: tiny-tool.de
 * Copyright 2026 tiny-tool.de
 * SPDX-License-Identifier: Apache-2.0
 * Licensed under the Apache License, Version 2.0.
 * See LICENSE or https://www.apache.org/licenses/LICENSE-2.0.
 */

package de.tinytool.quality.gui;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

class DesktopFrameTest {

    private DesktopFrame frame;

    @BeforeEach
    void openDesktop() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "Desktop smoke tests require a graphical environment");
        SwingUtilities.invokeAndWait(() -> {
            frame = new DesktopFrame();
            frame.setVisible(true);
        });
    }

    @AfterEach
    void closeDesktop() throws Exception {
        if (frame != null) {
            SwingUtilities.invokeAndWait(frame::dispose);
        }
    }

    @Test
    void runsTheExampleByDefaultAndInvalidatesStaleReports() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            assertThat(component("status", JLabel.class).getIcon()).isEqualTo(LedIcon.IDLE);
            assertThat(component("input", JTextField.class).getText())
                    .endsWith("fictional-bicycle-rentals.csv");
            assertThat(component("profile", JTextField.class).getText())
                    .endsWith("fictional-bicycle-rentals.csv-profile.json");
            assertThat(component("inputLed", JLabel.class).getIcon()).isEqualTo(LedIcon.VALID);
            assertThat(component("profileLed", JLabel.class).getIcon()).isEqualTo(LedIcon.VALID);
            assertThat(component("profileGuide", JLabel.class).getText()).contains("Beispiel-CSV");
            assertThat(component("profileGuide", JLabel.class).getText())
                    .contains("*.csv-profile.json", "*.schema.json", "Ergebnis-JSON");
            assertThat(component("save", JButton.class).isEnabled()).isFalse();
            component("validate", JButton.class).doClick(0);
            assertThat(component("status", JLabel.class).getIcon()).isEqualTo(LedIcon.RUNNING);
            assertThat(component("input", JTextField.class).isEnabled()).isFalse();
        });
        awaitResult();
        saveScreenshot("gui-example.png");
        SwingUtilities.invokeAndWait(() -> {
            assertThat(component("status", JLabel.class).getText()).startsWith("GÜLTIG");
            assertThat(component("status", JLabel.class).getIcon()).isEqualTo(LedIcon.VALID);
            assertThat(component("context", JLabel.class).getText())
                    .isEqualTo("Datei: fictional-bicycle-rentals.csv | Prüfregeln: "
                            + "fictional-bicycle-rentals.csv-profile.json");
            assertThat(component("findings", JTable.class).getRowCount()).isZero();
            assertThat(component("save", JButton.class).isEnabled()).isTrue();
            component("input", JTextField.class).setText("another.csv");
            assertThat(component("status", JLabel.class).getIcon()).isEqualTo(LedIcon.IDLE);
            assertThat(component("inputLed", JLabel.class).getIcon()).isEqualTo(LedIcon.ERROR);
            assertThat(component("save", JButton.class).isEnabled()).isFalse();
            assertThat(component("status", JLabel.class).getText()).doesNotContain("GÜLTIG");
        });
    }

    @Test
    void displaysFindingsForTheInvalidCsvExample() throws Exception {
        String input = Path.of("docs", "examples", "fictional-bicycle-rentals-invalid.csv").toString();
        String profile = Path.of("docs", "examples", "fictional-bicycle-rentals.csv-profile.json").toString();
        SwingUtilities.invokeAndWait(() -> {
            component("input", JTextField.class).setText(input);
            component("profile", JTextField.class).setText(profile);
            component("validate", JButton.class).doClick(0);
        });
        awaitResult();
        SwingUtilities.invokeAndWait(() -> {
            assertThat(component("status", JLabel.class).getText()).startsWith("UNGÜLTIG");
            assertThat(component("status", JLabel.class).getIcon()).isEqualTo(LedIcon.INVALID);
            assertThat(component("findings", JTable.class).getRowCount()).isEqualTo(5);
            assertThat(component("save", JButton.class).isEnabled()).isTrue();
        });
        saveScreenshot("gui-invalid-example.png");
    }

    @Test
    void switchesToEnglishAndBackWithoutLosingTheResult() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JButton banner = component("brandBanner", JButton.class);
            assertThat(banner.getIcon()).isNotNull();
            assertThat(banner.getToolTipText()).contains("https://tiny-tool.de/");
            component("validate", JButton.class).doClick(0);
        });
        awaitResult();
        SwingUtilities.invokeAndWait(() -> {
            component("language", JComboBox.class).setSelectedItem(UiLanguage.EN);
            assertThat(component("validate", JButton.class).getText()).isEqualTo("Validate now");
            assertThat(component("status", JLabel.class).getText()).startsWith("VALID");
            assertThat(component("status", JLabel.class).getIcon()).isEqualTo(LedIcon.VALID);
            assertThat(component("context", JLabel.class).getText()).contains("Rules:");
            assertThat(component("profileGuide", JLabel.class).getText()).contains("sample CSV");
            assertThat(component("findings", JTable.class).getColumnName(0)).isEqualTo("Data path");
            assertThat(component("save", JButton.class).isEnabled()).isTrue();
        });
        saveScreenshot("gui-example-en.png");
        SwingUtilities.invokeAndWait(() -> {
            component("language", JComboBox.class).setSelectedItem(UiLanguage.DE);
            assertThat(component("validate", JButton.class).getText()).isEqualTo("Jetzt prüfen");
            assertThat(component("status", JLabel.class).getText()).startsWith("GÜLTIG");
            assertThat(component("context", JLabel.class).getText()).contains("Prüfregeln:");
        });
    }

    private void awaitResult() throws Exception {
        long deadline = System.nanoTime() + java.time.Duration.ofSeconds(60).toNanos();
        AtomicBoolean finished = new AtomicBoolean();
        while (!finished.get() && System.nanoTime() < deadline) {
            SwingUtilities.invokeAndWait(() -> finished.set(component("validate", JButton.class).isEnabled()));
            if (!finished.get()) {
                Thread.sleep(25);
            }
        }
        assertThat(finished.get()).as("Validation finished without blocking the desktop").isTrue();
    }

    private void saveScreenshot(String name) throws Exception {
        Path target = Path.of("target", name);
        Files.createDirectories(target.toAbsolutePath().getParent());
        SwingUtilities.invokeAndWait(() -> {
            BufferedImage image = new BufferedImage(frame.getWidth(), frame.getHeight(), BufferedImage.TYPE_INT_RGB);
            var graphics = image.createGraphics();
            try {
                frame.paint(graphics);
            } finally {
                graphics.dispose();
            }
            try {
                ImageIO.write(image, "png", target.toFile());
            } catch (java.io.IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
        });
    }

    private <T extends Component> T component(String name, Class<T> type) {
        Component component = find(frame, name);
        assertThat(component).as("Component %s", name).isNotNull();
        return type.cast(component);
    }

    private static Component find(Container parent, String name) {
        for (Component child : parent.getComponents()) {
            if (name.equals(child.getName())) {
                return child;
            }
            if (child instanceof Container container) {
                Component found = find(container, name);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}
