/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon;

import javax.swing.JOptionPane;
import javax.swing.UIManager;
import java.awt.Desktop;
import java.net.URI;

// Jar's Main-Class (see build.gradle.kts jar.manifest): this is what runs when someone
// double-clicks the addon jar directly instead of dropping it in their mods folder. Meteor's
// own Main (meteordevelopment.meteorclient.Main) does the same thing for its jar, but its
// classes aren't on the classpath here - this is a plain "java -jar" launch, before Fabric/
// Meteor ever loads - so we can't call into it, only mirror the pattern with java.awt.Desktop
// instead of Meteor's hand-rolled per-OS Runtime.exec.
public class Main {

    private static final String RELEASES_URL = "https://github.com/Leonn170709/THM-Addons/releases/latest";

    /** Exit code 2 tells the caller nothing could be shown, so it can fall back to another popup. */
    private static void showStartupError(String title, String message, String downloadUrl) {
        if (java.awt.GraphicsEnvironment.isHeadless()) {
            System.err.println(title + "\n" + message);
            System.exit(2);
        }

        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
        }

        if (downloadUrl == null || downloadUrl.isEmpty()) {
            JOptionPane.showMessageDialog(null, message, title, JOptionPane.ERROR_MESSAGE);
            return;
        }

        Object[] options = {"Download", "Close"};
        int choice = JOptionPane.showOptionDialog(null, message, title,
            JOptionPane.DEFAULT_OPTION, JOptionPane.ERROR_MESSAGE, null, options, options[0]);

        if (choice == 0) {
            try {
                Desktop.getDesktop().browse(new URI(downloadUrl));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public static void main(String[] args) throws Exception {
        // Started by StartupDialog as its own process (title, message[, download url]): the game's
        // own JVM is headless, this one is not, so Swing can actually show a window here.
        if (args.length >= 2) {
            showStartupError(args[0], args[1], args.length >= 3 ? args[2] : null);
            return;
        }

        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());

        Object[] options = {"Download", "No thanks"};
        int choice = JOptionPane.showOptionDialog(null,
            "This is a Meteor Client addon, not a program you run directly.\nPut it in your mods folder next to Meteor Client and Fabric.",
            "THM Addon",
            JOptionPane.DEFAULT_OPTION, JOptionPane.INFORMATION_MESSAGE, null,
            options, options[0]);

        if (choice == 0) {
            try {
                Desktop.getDesktop().browse(new URI(RELEASES_URL));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
