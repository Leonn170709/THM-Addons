/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.utils;

import org.lwjgl.util.tinyfd.TinyFileDialogs;
import xyz.thm.addon.Main;
import xyz.thm.addon.THMAddon;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Startup error popup, before a world or GUI exists. The game's JVM runs with
 * {@code -Djava.awt.headless=true}, so Swing can't open a window in it and Fabric Loader's own error
 * window bails out for the same reason - both only work in a separate process. So the dialog is run
 * as one (Main), exactly like Fabric Loader forks its GUI. TinyFileDialogs is only the fallback: on
 * Linux it shells out to zenity/kdialog and silently shows nothing when neither is installed.
 */
public final class StartupDialog {
    private static final long TIMEOUT_MINUTES = 10;

    private StartupDialog() {}

    /** @return true if a window was actually shown */
    public static boolean show(String title, String message, String downloadUrl) {
        return viaOwnProcess(title, message, downloadUrl) || viaTinyFileDialogs(title, message, downloadUrl);
    }

    private static boolean viaOwnProcess(String title, String message, String downloadUrl) {
        try {
            Path javaBin = Path.of(System.getProperty("java.home"), "bin",
                System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win") ? "java.exe" : "java");
            String classpath = ownClasspath();
            if (classpath == null || !Files.exists(javaBin)) return false;

            List<String> command = new ArrayList<>(List.of(
                javaBin.toString(), "-cp", classpath, "xyz.thm.addon.Main", title, message));
            if (downloadUrl != null && !downloadUrl.isEmpty()) command.add(downloadUrl);

            // Output is captured, not inherited: a failing child's error then ends up in the game log.
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            if (!process.waitFor(TIMEOUT_MINUTES, TimeUnit.MINUTES)) {
                process.destroy();
                return true; // it was on screen, the player just left it open
            }
            if (process.exitValue() == 0) return true;

            String output = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).trim();
            if (output.length() > 800) output = output.substring(0, 800) + "...";
            THMAddon.LOG.warn("Dialog process exited with code {} (2 = no display available), classpath {}: {}", process.exitValue(), classpath, output);
            return false;
        } catch (Throwable e) {
            THMAddon.LOG.warn("Could not open the dialog in a separate process: {}", e.toString());
            return false;
        }
    }

    /**
     * Main only needs the JDK, so its class file is copied out and run on its own. That works however
     * the addon was loaded (installed jar, dev folders, launcher quirks) - the loader's origin paths
     * turned out not to hold the compiled classes in a dev run.
     */
    private static String ownClasspath() {
        try (InputStream in = Main.class.getResourceAsStream("Main.class")) {
            if (in == null) return null;
            Path dir = Files.createTempDirectory("thm-addon-dialog");
            Path target = dir.resolve("xyz").resolve("thm").resolve("addon").resolve("Main.class");
            Files.createDirectories(target.getParent());
            Files.write(target, in.readAllBytes());
            // deleteOnExit runs in reverse order and only removes empty folders: outermost first.
            for (Path path : List.of(dir, dir.resolve("xyz"), dir.resolve("xyz/thm"), target.getParent(), target)) {
                path.toFile().deleteOnExit();
            }
            return dir.toString();
        } catch (Throwable e) {
            THMAddon.LOG.warn("Could not copy the dialog class out: {}", e.toString());
            return null;
        }
    }

    private static boolean viaTinyFileDialogs(String title, String message, String downloadUrl) {
        try {
            boolean hasUrl = downloadUrl != null && !downloadUrl.isEmpty();
            // tinyfd refuses any text containing quotes and shows its own error instead of the message.
            String text = hasUrl ? message + "\n\nOK opens the download page, Cancel just closes." : message;
            boolean download = TinyFileDialogs.tinyfd_messageBox(
                withoutQuotes(title), withoutQuotes(text), hasUrl ? "okcancel" : "ok", "error", true);
            if (hasUrl && download) openBrowser(downloadUrl);
            return true;
        } catch (Throwable e) {
            THMAddon.LOG.warn("Native message box unavailable: {}", e.toString());
            return false;
        }
    }

    static String withoutQuotes(String text) {
        return text.replace('"', '`').replace('\'', '`');
    }

    public static void openBrowser(String url) {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        List<String> command = os.contains("win")
            ? List.of("rundll32", "url.dll,FileProtocolHandler", url)
            : os.contains("mac") ? List.of("open", url) : List.of("xdg-open", url);
        try {
            new ProcessBuilder(command).start();
        } catch (Exception e) {
            THMAddon.LOG.error("Failed to open URL: {}", url, e);
        }
    }
}
