package xyz.thm.addon.gui.themes;

import meteordevelopment.meteorclient.gui.themes.meteor.MeteorGuiTheme;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import xyz.thm.addon.gui.RecolorGuiTheme;

/**
 * Nether Theme - A dark, corrupted Minecraft Nether-inspired GUI theme
 * featuring deep reds, oranges, and blacks with hints of crimson and soul fire.
 * Perfect for a mysterious, hellish aesthetic.
 */
public class Nether extends MeteorGuiTheme implements RecolorGuiTheme {
    public static final Nether INSTANCE = new Nether();

    //Unused
    // Nether color palette constants
    private static final int CRIMSON_BRIGHT = 255;
    private static final int CRIMSON_MID = 200;
    private static final int CRIMSON_DARK = 139;

    private static final int SOUL_FIRE = 100;
    private static final int SOUL_FIRE_BRIGHT = 150;

    private static final int OBSIDIAN_DARK = 30;
    private static final int OBSIDIAN_MID = 60;
    private static final int OBSIDIAN_LIGHT = 90;

    private static final int LAVA_ORANGE = 255;
    private static final int LAVA_RED = 150;

    @Override
    public String getName() {
        return "Nether";
    }

    @Override
    public boolean getCategoryIcons() {
        return true;
    }

    // ============================================
    // PRIMARY ACCENT COLORS
    // ============================================

    @Override
    public SettingColor getAccentColor() {
        // Crimson red - primary interactive element with Nether energy
        return new SettingColor(220, 20, 60);
    }

    @Override
    public SettingColor getCheckboxColor() {
        // Deep crimson for checkboxes
        return new SettingColor(200, 30, 70);
    }

    @Override
    public SettingColor getPlusColor() {
        // Bright lava orange for addition
        return new SettingColor(255, 140, 0);
    }

    @Override
    public SettingColor getMinusColor() {
        // Dark obsidian for removal
        return new SettingColor(30, 20, 20);
    }

    @Override
    public SettingColor getFavoriteColor() {
        // Soul fire blue - mystical accent
        return new SettingColor(100, 150, 255);
    }

    // ============================================
    // TEXT COLORS
    // ============================================

    @Override
    public SettingColor getTextSecondaryColor() {
        // Light gray text for readability on dark backgrounds
        return new SettingColor(200, 150, 150);
    }

    @Override
    public SettingColor getTextHighlightColor() {
        // Crimson highlight with transparency
        return new SettingColor(255, 80, 80, 100);
    }

    @Override
    public SettingColor getTitleTextColor() {
        // Bright lava orange for titles
        return new SettingColor(255, 120, 0);
    }

    @Override
    public SettingColor getLoggedInColor() {
        // Soul fire blue for confirmation
        return new SettingColor(100, 180, 255);
    }

    // ============================================
    // BACKGROUND COLORS
    // ============================================

    @Override
    public TriColorSetting getBackgroundColor() {
        // Gradient from black obsidian to dark crimson - creates hellish depth
        return new TriColorSetting(
            new SettingColor(20, 15, 15, 230),      // Dark obsidian base
            new SettingColor(60, 40, 40, 230),      // Dark crimson mid
            new SettingColor(90, 50, 50, 230)       // Lighter crimson
        );
    }

    @Override
    public SettingColor getModuleBackground() {
        // Dark module background with crimson tint
        return new SettingColor(50, 35, 35, 200);
    }

    // ============================================
    // OUTLINE & BORDERS
    // ============================================

    @Override
    public TriColorSetting getOutlineColor() {
        // Crimson and obsidian gradient for borders
        return new TriColorSetting(
            new SettingColor(60, 30, 30),           // Dark crimson border
            new SettingColor(100, 50, 50),          // Mid-crimson
            new SettingColor(200, 100, 100)         // Bright crimson highlight
        );
    }

    // ============================================
    // SEPARATORS
    // ============================================

    @Override
    public SettingColor getSeparatorCenter() {
        // Bright crimson separator for clear division
        return new SettingColor(220, 80, 80);
    }

    @Override
    public SettingColor getSeparatorEdges() {
        // Fading crimson edges for smooth transitions
        return new SettingColor(150, 60, 60, 180);
    }

    // ============================================
    // SCROLLBAR
    // ============================================

    @Override
    public TriColorSetting getScrollbarColor() {
        // Crimson gradient scrollbar with dark base
        return new TriColorSetting(
            new SettingColor(80, 40, 40, 220),      // Dark pressed state
            new SettingColor(150, 60, 60, 220),     // Normal state
            new SettingColor(220, 100, 100, 220)    // Hover state - bright
        );
    }

    // ============================================
    // SLIDER CONTROLS
    // ============================================

    @Override
    public TriColorSetting getSliderHandle() {
        // Luminous lava handle with crimson glow effect
        return new TriColorSetting(
            new SettingColor(150, 60, 60),          // Pressed state
            new SettingColor(200, 100, 100),        // Normal state
            new SettingColor(255, 140, 0)           // Hover state - bright lava
        );
    }

    @Override
    public SettingColor getSliderLeft() {
        // Active/filled portion - bright lava orange
        return new SettingColor(255, 120, 0);
    }

    @Override
    public SettingColor getSliderRight() {
        // Inactive/empty portion - dark obsidian
        return new SettingColor(40, 30, 30);
    }

    // ============================================
    // STARSCRIPT SYNTAX HIGHLIGHTING
    // ============================================

    @Override
    public SettingColor getStarscriptText() {
        // Default text - light gray for readability on dark
        return new SettingColor(220, 220, 220);
    }

    @Override
    public SettingColor getStarscriptBraces() {
        // Braces {} - bright lava orange
        return new SettingColor(255, 140, 0);
    }

    @Override
    public SettingColor getStarscriptParenthesis() {
        // Parenthesis () - crimson red
        return new SettingColor(200, 80, 80);
    }

    @Override
    public SettingColor getStarscriptStrings() {
        // String literals - soul fire blue
        return new SettingColor(120, 160, 255);
    }

    @Override
    public SettingColor getStarscriptNumbers() {
        // Numeric values - lava orange
        return new SettingColor(255, 160, 80);
    }

    @Override
    public SettingColor getStarscriptKeywords() {
        // Keywords - bright crimson
        return new SettingColor(255, 80, 80);
    }

    @Override
    public SettingColor getStarscriptAccessedObjects() {
        // Object properties - light crimson
        return new SettingColor(200, 120, 120);
    }
}
