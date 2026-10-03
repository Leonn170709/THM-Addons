/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.gui;

import xyz.thm.addon.THMAddon;
import xyz.thm.addon.system.THMSystem;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.ARGB;

// ponytail: ported from BleachHack's BleachTitleScreen/ParticleManager/Particle/Window - an
// OS-window-styled frame (same chrome as BleachHack's gui/window/Window.java, recolored to
// THMAddon.THMColor/THMSideColor) wrapping the title screen's actual buttons, which
// TitleScreenMenuMixin repositions into it and ButtonWidgetStyleMixin re-skins. Everything here
// is plain DrawContext.fill/text - same cost as any other GUI widget, no custom GL state - so
// it doesn't add meaningfully to title screen render time.
public class MainMenuFx {

    // Brand colors are now live and player-editable from the THM tab (THMSystem.thmColor /
    // thmSideColor); these read the current setting each call (fall back to the THMAddon defaults
    // if the system isn't up yet). The main color is the "line" color (border/title bar/buttons/
    // text/particles), the side color the translucent "fill" color (window body) - the same
    // line/side split THM's block-ESP renders use. All ARGB ints (getPacked() returns ARGB).
    private static int thmColor() {
        THMSystem sys = THMSystem.get();
        return sys != null ? sys.thmColor.get().getPacked() : THMAddon.THMColor.getPacked();
    }

    private static int thmSide() {
        THMSystem sys = THMSystem.get();
        return sys != null ? sys.thmSideColor.get().getPacked() : THMAddon.THMSideColor.getPacked();
    }

    // Public so the THM Meteor theme (ThmTheme / ThmChrome) reuses the exact same window chrome
    // colors when restyling Meteor's own GUI to match this window - recomputed live from thmColor().
    public static int borderTop()      { return ARGB.srgbLerp(0.3f, 0xff000000, thmColor()); }
    public static int borderBottom()   { return thmColor(); }
    public static int titlebarLeft()   { return ARGB.srgbLerp(0.75f, 0xff000000, thmColor()); }
    public static int titlebarRight()  { return ARGB.srgbLerp(0.55f, 0xff000000, thmColor()); }
    // Body/fill is the side color directly (already a translucent tint, like the block side color);
    // it sits over a blurred backdrop so a see-through pane stays readable.
    public static int bodyFill()       { return thmSide(); }
    public static int buttonFill()     { return withAlpha(ARGB.srgbLerp(0.7f, 0xff000000, thmColor()), 0xe0); }
    public static int buttonFillHover(){ return withAlpha(thmColor(), 0xe0); }

    // Header text above the buttons - edit this to change it. Defaults to "Minecraft" (the
    // word the vanilla logo it replaces used to show).
    private static final String HEADER_TEXT = "Minecraft";
    private static final float HEADER_SCALE = 3f;

    private static final List<Particle> particles = new ArrayList<>();
    private static final Random RAND = new Random();

    // Shader-only preview toggle. Static/unpersisted (a dev/design aid, resets on relaunch);
    // lives here rather than in the mixin so MainMenuSettingsScreen can flip it on. Read by
    // TitleScreenMenuMixin to strip the UI down to just the shader background.
    public static boolean previewMode = false;

    public static void tick(int mouseX, int mouseY) {
        if (!THMSystem.get().mainMenuParticles.get()) return;

        particles.add(new Particle(mouseX, mouseY));
        particles.removeIf(Particle::update);
    }

    /** Window bounds matching BleachHack's own layout: centered, 3/4 of the screen. */
    public static int[] windowBounds(int screenWidth, int screenHeight) {
        return new int[] {
            screenWidth / 8, screenHeight / 8,
            screenWidth - screenWidth / 8, screenHeight - screenHeight / 8 + 2
        };
    }

    /** Window frame + big title - draw this BEFORE the screen's buttons so they sit on top of it. */
    public static void renderWindow(GuiGraphicsExtractor context, Font tr, int screenWidth, int screenHeight) {
        if (!THMSystem.get().mainMenuWindow.get()) return;

        int[] bounds = windowBounds(screenWidth, screenHeight);
        int x1 = bounds[0], y1 = bounds[1], x2 = bounds[2], y2 = bounds[3];
        int centerX = x1 + (x2 - x1) / 2;
        int headerY = y1 + (y2 - y1) / 4 - 25;

        renderChrome(context, tr, x1, y1, x2, y2, "THM Addons");
        renderBigTitle(context, tr, centerX, headerY);

        String version = "THM Addon v" + THMAddon.VERSION;
        context.text(tr, version, centerX - tr.width(version) / 2, headerY + (int) (10 * HEADER_SCALE) + 2, thmColor());
    }

    /** Mouse particle trail - draw this AFTER the screen's buttons so it drifts over everything. */
    public static void renderParticles(GuiGraphicsExtractor context) {
        if (!THMSystem.get().mainMenuParticles.get()) return;

        for (Particle p : particles) {
            for (double[] pos : p.points) {
                int px = (int) pos[0], py = (int) pos[1];
                context.fill(px, py, px + 1, py + 1, thmColor());
            }
        }
    }

    /** BleachHack-styled flat button chrome, drawn in place of a repositioned vanilla button. */
    public static void renderButton(GuiGraphicsExtractor context, Font tr, int x1, int y1, int x2, int y2, String text, boolean hovered, boolean active) {
        int fill = hovered && active ? buttonFillHover() : buttonFill();

        context.fill(x1, y1 + 1, x1 + 1, y2 - 1, borderTop());
        horizontalGradient(context, x1 + 1, y1, x2 - 1, y1 + 1, borderTop(), borderBottom());
        context.fill(x2 - 1, y1 + 1, x2, y2 - 1, borderBottom());
        horizontalGradient(context, x1 + 1, y2 - 1, x2 - 1, y2, borderTop(), borderBottom());
        context.fill(x1 + 1, y1 + 1, x2 - 1, y2 - 1, fill);

        int color = active ? 0xffffffff : 0xffa0a0a0;
        int textWidth = tr.width(text);
        context.text(tr, text, x1 + (x2 - x1) / 2 - textWidth / 2, y1 + (y2 - y1) / 2 - 4, color);
    }

    // Found it: both earlier attempts here passed 0xffffff as the text color, which is only
    // 6 hex digits - i.e. alpha byte 0x00, fully transparent. Every OTHER text draw in this
    // file (version/button/title text, all confirmed visible in screenshots) uses a color with
    // alpha explicitly set (THM_COLOR, -1, ...). Scaled via the same pushMatrix/translate/scale
    // wrapper as before, just with that one-byte bug fixed and a plain String instead of a
    // MutableText (no need for per-character styling here).
    private static void renderBigTitle(GuiGraphicsExtractor context, Font tr, int centerX, int y) {
        float textWidth = tr.width(HEADER_TEXT) * HEADER_SCALE;

        context.pose().pushMatrix();
        context.pose().translate(centerX - textWidth / 2f, (float) y);
        context.pose().scale(HEADER_SCALE, HEADER_SCALE);
        context.text(tr, HEADER_TEXT, 0, 0, thmColor());
        context.pose().popMatrix();
    }

    // Same chrome as BleachHack's Window, recolored to the THM gold accent: gradient border,
    // title bar, translucent body, close/minimize glyphs (wired up - see isCloseButton/
    // isMinimizeButton below, hit-tested by callers against the same coordinates used to draw
    // them here).
    // Public so MainMenuSettingsScreen (the non-Meteor settings screen) can reuse the same look.
    public static void renderChrome(GuiGraphicsExtractor context, Font tr, int x1, int y1, int x2, int y2, String title) {
        context.fill(x1, y1 + 1, x1 + 1, y2 - 1, borderTop());
        horizontalGradient(context, x1 + 1, y1, x2 - 1, y1 + 1, borderTop(), borderBottom());
        context.fill(x2 - 1, y1 + 1, x2, y2 - 1, borderBottom());
        horizontalGradient(context, x1 + 1, y2 - 1, x2 - 1, y2, borderTop(), borderBottom());

        context.fill(x1 + 1, y1 + 12, x2 - 1, y2 - 1, bodyFill());
        horizontalGradient(context, x1 + 1, y1 + 1, x2 - 1, y1 + 12, titlebarLeft(), titlebarRight());

        context.text(tr, title, x1 + 4, y1 + 3, -1);

        context.text(tr, "x", x2 - 10, y1 + 3, 0, false);
        context.text(tr, "x", x2 - 11, y1 + 2, -1, false);
        context.text(tr, "_", x2 - 21, y1 + 2, 0, false);
        context.text(tr, "_", x2 - 22, y1 + 1, -1, false);
    }

    /** Hit-box for the "x" glyph drawn by renderChrome, in the same x1/y1/x2/y2 coordinates. */
    public static boolean isCloseButton(int x1, int y1, int x2, int y2, double mouseX, double mouseY) {
        return mouseX >= x2 - 11 && mouseX < x2 - 1 && mouseY >= y1 && mouseY < y1 + 11;
    }

    /** Hit-box for the "_" glyph drawn by renderChrome, in the same x1/y1/x2/y2 coordinates. */
    public static boolean isMinimizeButton(int x1, int y1, int x2, int y2, double mouseX, double mouseY) {
        return mouseX >= x2 - 23 && mouseX < x2 - 12 && mouseY >= y1 && mouseY < y1 + 11;
    }

    // 1.21.11's DrawContext.fillGradient(...) only interpolates vertically - see BleachHack's
    // own Window.horizontalGradient for the same workaround on this MC version.
    private static void horizontalGradient(GuiGraphicsExtractor context, int x1, int y1, int x2, int y2, int color1, int color2) {
        int width = x2 - x1;
        if (width <= 0) return;

        for (int i = 0; i < width; i++) {
            float t = width <= 1 ? 0f : (float) i / (width - 1);
            context.fill(x1 + i, y1, x1 + i + 1, y2, ARGB.srgbLerp(t, color1, color2));
        }
    }

    private static int withAlpha(int argb, int alpha) {
        return (argb & 0x00ffffff) | (alpha << 24);
    }

    private static class Particle {
        final int x, y;
        final int lifespan;
        final List<double[]> points = new ArrayList<>();
        int tick;
        long lastTick;

        Particle(int x, int y) {
            this.x = x;
            this.y = y;
            this.lifespan = RAND.nextInt(20);
            this.lastTick = System.currentTimeMillis();
            for (int j = 0; j < RAND.nextInt(10); j++) {
                double angle = RAND.nextDouble() * Math.PI * 2;
                double radius = RAND.nextDouble() * 3;
                points.add(new double[] { x + Math.cos(angle) * radius, y + Math.sin(angle) * radius });
            }
        }

        /** @return true once the particle has finished its life and should be removed */
        boolean update() {
            if (System.currentTimeMillis() < lastTick + 16) return false;
            lastTick = System.currentTimeMillis();
            tick++;

            if (tick > lifespan) {
                points.clear();
                return true;
            }

            for (int i = 0; i < points.size(); i++) {
                double[] pos = points.get(i);
                points.set(i, new double[] { pos[0] + (pos[0] - x) / tick, pos[1] + (pos[1] - y) / tick });
            }
            return false;
        }
    }
}
