/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.mixin;

import meteordevelopment.meteorclient.utils.player.ChatUtils;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.thm.addon.THMAddon;
import xyz.thm.addon.utils.THMUtils;
import xyz.thm.addon.system.THMSystem;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.function.Consumer;

@Mixin(ScreenshotRecorder.class)
public class ScreenshotClipboardMixin {

    // Minecraft launches with -Djava.awt.headless=true; override before AWT is first touched.
    // Safe because Minecraft uses LWJGL for rendering and never initializes AWT itself.
    static {
        System.setProperty("java.awt.headless", "false");
    }

    private static final boolean IS_MAC = System.getProperty("os.name", "").toLowerCase().contains("mac");

    // InputStream representation so native apps (Discord, browsers) receive raw PNG bytes.
    private static final DataFlavor PNG_FLAVOR = new DataFlavor("image/png", "PNG Image");

    // Inject at TAIL so: (1) the file is fully written, (2) the "Saved screenshot" message
    // has already been sent — our clipboard message always appears after it.
    @Inject(method = "method_22691", at = @At("TAIL"))
    private static void onSaveScreenshotFile(NativeImage image, File file, Consumer<Text> messageReceiver, CallbackInfo ci) {
        if (!THMSystem.get().screenshotToClipboard.get()) return;

        THMUtils.async("screenshot-clipboard", () -> {
            try {
                if (IS_MAC) {
                    copyToClipboardMac(file);
                } else {
                    try {
                        copyToClipboardAWT(file);
                    } catch (HeadlessException | AWTError e) {
                        copyToClipboardNative(file);
                    }
                }
                ChatUtils.info("Successfully copied screenshot to clipboard");
            } catch (Exception e) {
                THMAddon.LOG.warn("[THM] Failed to copy screenshot to clipboard", e);
            }
        });
    }

    // macOS: use osascript to write PNG bytes directly into NSPasteboard.
    // Java AWT clipboard does not reliably bridge to NSPasteboard for images on macOS.
    private static void copyToClipboardMac(File file) throws IOException, InterruptedException {
        String script = "set the clipboard to (read (POSIX file \"" + file.getAbsolutePath() + "\") as «class PNGf»)";
        Process proc = Runtime.getRuntime().exec(new String[]{"osascript", "-e", script});
        int exit = proc.waitFor();
        if (exit != 0) {
            throw new IOException("osascript exited with code " + exit);
        }
    }

    // Linux fallback: the static block above only wins if it runs before anything else touches
    // AWT — when it loses, the Toolkit is already a HeadlessToolkit and every AWT clipboard call
    // throws for the rest of the session. wl-copy/xclip are the desktop-native equivalent of the
    // macOS osascript path. ponytail: no session detection, just try both and take the first that
    // exits 0.
    private static void copyToClipboardNative(File file) throws IOException, InterruptedException {
        IOException last = new IOException("no clipboard tool found (install wl-clipboard or xclip)");

        for (String[] cmd : new String[][]{
            {"wl-copy", "--type", "image/png"},
            {"xclip", "-selection", "clipboard", "-t", "image/png"}
        }) {
            try {
                Process proc = new ProcessBuilder(cmd).redirectInput(file).start();
                if (proc.waitFor() == 0) return;
                last = new IOException(cmd[0] + " exited with code " + proc.exitValue());
            } catch (IOException e) {
                last = e;
            }
        }

        throw last;
    }

    private static void copyToClipboardAWT(File file) throws IOException {
        BufferedImage buffered = ImageIO.read(file);
        if (buffered == null) {
            THMAddon.LOG.warn("[THM] Could not read screenshot file for clipboard");
            return;
        }
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new TransferableImage(buffered), null);
    }

    private record TransferableImage(BufferedImage image) implements Transferable {
        @Override
        public DataFlavor[] getTransferDataFlavors() {
            return new DataFlavor[]{PNG_FLAVOR, DataFlavor.imageFlavor};
        }

        @Override
        public boolean isDataFlavorSupported(DataFlavor flavor) {
            return PNG_FLAVOR.equals(flavor) || DataFlavor.imageFlavor.equals(flavor);
        }

        @Override
        public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException, IOException {
            if (DataFlavor.imageFlavor.equals(flavor)) return image;
            if (PNG_FLAVOR.equals(flavor)) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                ImageIO.write(image, "png", out);
                return new ByteArrayInputStream(out.toByteArray());
            }
            throw new UnsupportedFlavorException(flavor);
        }
    }
}
