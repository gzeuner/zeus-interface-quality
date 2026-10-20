/*
 * Author: tiny-tool.de
 * Copyright 2026 tiny-tool.de
 * SPDX-License-Identifier: Apache-2.0
 * Licensed under the Apache License, Version 2.0.
 * See LICENSE or https://www.apache.org/licenses/LICENSE-2.0.
 */

package de.tinytool.quality.gui;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import java.awt.Cursor;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

/** The bundled banner is trimmed at display time to keep its transparent glow margin small. */
final class BrandBanner extends JButton {

    BrandBanner() {
        setName("brandBanner");
        setBorderPainted(false);
        setContentAreaFilled(false);
        setFocusPainted(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        try (InputStream resource = BrandBanner.class.getResourceAsStream(
                "/brand/tt-tools-tipps-facts.png")) {
            if (resource == null) {
                throw new IllegalStateException("Bundled TT banner is missing");
            }
            BufferedImage source = ImageIO.read(resource);
            if (source == null) {
                throw new IllegalStateException("Bundled TT banner cannot be decoded");
            }
            BufferedImage trimmed = trimTransparent(source);
            int width = 300;
            int height = Math.max(1, Math.round((float) trimmed.getHeight() * width / trimmed.getWidth()));
            Image scaled = trimmed.getScaledInstance(width, height, Image.SCALE_SMOOTH);
            setIcon(new ImageIcon(scaled));
        } catch (IOException e) {
            throw new IllegalStateException("Bundled TT banner cannot be loaded", e);
        }
    }

    private static BufferedImage trimTransparent(BufferedImage image) {
        int left = image.getWidth();
        int top = image.getHeight();
        int right = -1;
        int bottom = -1;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if ((image.getRGB(x, y) >>> 24) > 8) {
                    left = Math.min(left, x);
                    top = Math.min(top, y);
                    right = Math.max(right, x);
                    bottom = Math.max(bottom, y);
                }
            }
        }
        if (right < left) {
            return image;
        }
        int margin = 8;
        left = Math.max(0, left - margin);
        top = Math.max(0, top - margin);
        right = Math.min(image.getWidth() - 1, right + margin);
        bottom = Math.min(image.getHeight() - 1, bottom + margin);
        return image.getSubimage(left, top, right - left + 1, bottom - top + 1);
    }
}
