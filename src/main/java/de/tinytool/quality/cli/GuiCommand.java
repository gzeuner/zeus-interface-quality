/*
 * Author: tiny-tool.de
 * Copyright 2026 tiny-tool.de
 * SPDX-License-Identifier: Apache-2.0
 * Licensed under the Apache License, Version 2.0.
 * See LICENSE or https://www.apache.org/licenses/LICENSE-2.0.
 */

package de.tinytool.quality.cli;

import de.tinytool.quality.adapter.support.LocalFiles;
import de.tinytool.quality.gui.DesktopFrame;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;

@Command(
        name = "gui",
        mixinStandardHelpOptions = true,
        description = "Open the local desktop interface for JSON, CSV and fixed-width validation."
)
public final class GuiCommand implements Callable<Integer> {

    @Spec
    private CommandSpec spec;

    @Override
    public Integer call() {
        if (GraphicsEnvironment.isHeadless()) {
            return fail("Keine grafische Desktop-Umgebung verfuegbar. Bitte 'validate' verwenden.");
        }
        CountDownLatch closed = new CountDownLatch(1);
        try {
            SwingUtilities.invokeAndWait(() -> {
                DesktopFrame frame = new DesktopFrame();
                frame.addWindowListener(new WindowAdapter() {
                    @Override
                    public void windowClosed(WindowEvent event) {
                        closed.countDown();
                    }
                });
                frame.setVisible(true);
            });
            closed.await();
            return 0;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return fail("Start der GUI wurde unterbrochen.");
        } catch (InvocationTargetException e) {
            return fail("GUI konnte nicht gestartet werden: " + LocalFiles.safeMessage(e.getCause()));
        }
    }

    private int fail(String message) {
        spec.commandLine().getErr().println("ERROR: " + message);
        spec.commandLine().getErr().flush();
        return 2;
    }
}
