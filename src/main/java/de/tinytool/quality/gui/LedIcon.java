/*
 * Author: tiny-tool.de
 * Copyright 2026 tiny-tool.de
 * SPDX-License-Identifier: Apache-2.0
 * Licensed under the Apache License, Version 2.0.
 * See LICENSE or https://www.apache.org/licenses/LICENSE-2.0.
 */

package de.tinytool.quality.gui;

import javax.swing.Icon;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;

enum LedIcon implements Icon {
    IDLE(new Color(133, 145, 160)),
    RUNNING(new Color(232, 163, 25)),
    VALID(new Color(29, 163, 82)),
    INVALID(new Color(218, 58, 49)),
    ERROR(new Color(218, 58, 49));

    private final Color color;

    LedIcon(Color color) {
        this.color = color;
    }

    @Override
    public void paintIcon(Component component, Graphics graphics, int x, int y) {
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(new Color(205, 214, 224));
            g.fillOval(x + 1, y + 1, 20, 20);
            g.setPaint(new RadialGradientPaint(x + 9f, y + 8f, 12f, new float[]{0f, 1f},
                    new Color[]{color.brighter().brighter(), color.darker()}));
            g.fillOval(x + 3, y + 3, 16, 16);
            g.setColor(new Color(255, 255, 255, 170));
            g.fillOval(x + 6, y + 5, 6, 4);
        } finally {
            g.dispose();
        }
    }

    @Override
    public int getIconWidth() {
        return 22;
    }

    @Override
    public int getIconHeight() {
        return 22;
    }
}
