/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.gui;

import meteordevelopment.meteorclient.gui.WindowScreen;
import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.tabs.WindowTabScreen;
import meteordevelopment.meteorclient.gui.themes.meteor.MeteorGuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.utils.render.color.Color;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/**
 * Draws the BleachHack-styled main-menu window chrome (see {@link MainMenuFx}) through Meteor's
 * own {@link GuiRenderer}, so {@code ThmTheme}'s widget/window mixins can make Meteor's GUI look
 * exactly like the title-screen window - same purple gradient border, translucent body and flat
 * button fills. Colors are the same ARGB values MainMenuFx draws with, read live each call (so the
 * player-editable THM colors from the THM tab apply instantly) and converted to Meteor {@link
 * Color}s (ARGB int -> r,g,b,a - MainMenuFx's ints are standard ARGB, so extract the channels by
 * hand rather than trusting Color(int)'s RGBA-packed interpretation).
 */
public final class ThmChrome {
    public static Color borderTop()       { return argb(MainMenuFx.borderTop()); }
    public static Color borderBottom()    { return argb(MainMenuFx.borderBottom()); }
    public static Color titlebarLeft()    { return argb(MainMenuFx.titlebarLeft()); }
    public static Color titlebarRight()   { return argb(MainMenuFx.titlebarRight()); }
    public static Color bodyFill()        { return argb(MainMenuFx.bodyFill()); }
    public static Color buttonFill()      { return argb(MainMenuFx.buttonFill()); }
    public static Color buttonFillHover() { return argb(MainMenuFx.buttonFillHover()); }

    private ThmChrome() {}

    private static Color argb(int c) {
        return new Color((c >> 16) & 0xff, (c >> 8) & 0xff, c & 0xff, (c >>> 24) & 0xff);
    }

    /**
     * BleachHack gradient border: dark purple top-left, bright THM purple bottom-right (left edge
     * solid dark, right edge solid bright, top/bottom horizontal-gradient between them) - a
     * one-for-one match of MainMenuFx.renderChrome's border, minus the corners it also leaves open.
     * {@code s} is the border thickness (Meteor's usual scale(2)).
     */
    public static void border(GuiRenderer renderer, double x, double y, double w, double h, double s) {
        Color top = borderTop(), bottom = borderBottom();
        renderer.quad(x, y + s, s, h - s * 2, top);                        // left edge
        renderer.quad(x + s, y, w - s * 2, s, top, bottom);               // top edge (horizontal gradient)
        renderer.quad(x + w - s, y + s, s, h - s * 2, bottom);            // right edge
        renderer.quad(x + s, y + h - s, w - s * 2, s, top, bottom);       // bottom edge
    }

    /** Filled body + gradient border in one call. */
    public static void panel(GuiRenderer renderer, double x, double y, double w, double h, double s, Color fill) {
        renderer.quad(x + s, y + s, w - s * 2, h - s * 2, fill);
        border(renderer, x, y, w, h, s);
    }

    // --- Title-bar window controls ("_" minimize, "x" close), drawn as bare glyphs like the
    //     main-menu window, only on single-window settings/tab screens (not the clickgui's
    //     category-list windows). ---

    // Last "x" glyph rect (Meteor-scaled coords), stored by headerGlyphs and read by closeGlyphHit
    // so the click box always matches exactly where the glyph was drawn. Fine as static single-slot:
    // only single-window settings screens draw these, so there's one header per frame.
    private static double closeX, closeY, closeW, closeH;

    /**
     * True only for the "real window" screens reachable from the top bar and module settings
     * (Config/GUI/THM Addon tabs = {@link WindowTabScreen}, module & list-setting popups =
     * {@link WindowScreen}). The clickgui's own category-list windows live in a plain
     * {@code ModulesScreen}, so they return false and get no title-bar controls.
     */
    public static boolean settingsWindow() {
        Screen screen = Minecraft.getInstance().screen;
        return screen instanceof WindowScreen || screen instanceof WindowTabScreen;
    }

    /**
     * Bare "_" and "x" glyphs pinned to the header's top-right, vertically centered - no button box.
     * Drawn with the same title font/size as the window title (title = true) so they match the rest
     * of the header. Positions/sizes are derived from the theme's own text metrics and {@code
     * scale()} so they sit correctly at any GUI scale (Meteor widgets live in framebuffer-pixel
     * space, so fixed pixel offsets read as "off" - measured widths + scaled padding do not).
     */
    public static void headerGlyphs(GuiRenderer renderer, WWidget header, MeteorGuiTheme theme) {
        Color color = theme.titleTextColor.get();
        double pad = theme.scale(6);
        double th = theme.textHeight(true);
        double gy = header.y + (header.height - th) / 2;

        double xw = theme.textWidth("x", 1, true);
        double uw = theme.textWidth("_", 1, true);
        double cx = header.x + header.width - pad - xw;
        double ux = cx - theme.scale(12) - uw;

        renderer.text("_", ux, gy, color, true);
        renderer.text("x", cx, gy, color, true);

        // Store a slightly padded click box around the "x" so it's comfortable to hit.
        double m = theme.scale(3);
        closeX = cx - m;
        closeY = header.y;
        closeW = xw + m * 2;
        closeH = header.height;
    }

    /** Hit-test for the "x" glyph against the box headerGlyphs last drew (Meteor-scaled coords). */
    public static boolean closeGlyphHit(double mx, double my) {
        return mx >= closeX && mx <= closeX + closeW && my >= closeY && my <= closeY + closeH;
    }
}
