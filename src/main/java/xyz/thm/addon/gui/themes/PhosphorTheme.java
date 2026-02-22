package xyz.thm.addon.gui.themes;

import xyz.thm.addon.gui.RecolorGuiTheme;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.gui.themes.meteor.MeteorGuiTheme;

public class PhosphorTheme extends MeteorGuiTheme implements RecolorGuiTheme {
    public static final PhosphorTheme INSTANCE = new PhosphorTheme();

    @Override
    public String getName() {
        return "Phosphor";
    }

    @Override
    public boolean getCategoryIcons() {
        return true;
    }

    // Colors
    @Override
    public SettingColor getAccentColor() {
        return new SettingColor(0, 69, 0, 242);
    }

    @Override
    public SettingColor getCheckboxColor() {
        return new SettingColor(57, 255, 20);
    }

    @Override
    public SettingColor getPlusColor() {
        return new SettingColor(0, 255, 0);
    }

    @Override
    public SettingColor getMinusColor() {
        return new SettingColor(0, 255, 0);
    }

    @Override
    public SettingColor getFavoriteColor() {
        return new SettingColor(0, 255, 0);
    }

    // Text
    @Override
    public SettingColor getTextColor() {
        return new SettingColor(0, 137, 0);
    }

    @Override
    public SettingColor getTextSecondaryColor() {
        return new SettingColor(0, 102, 0);
    }

    @Override
    public SettingColor getTextHighlightColor() {
        return new SettingColor(119, 255, 169, 100);
    }

    @Override
    public SettingColor getTitleTextColor() {
        return new SettingColor(0, 200, 0);
    }

    @Override
    public SettingColor getLoggedInColor() {
        return new SettingColor(45, 225, 45);
    }

    @Override
    public SettingColor getPlaceholderColor() {
        return new SettingColor(0, 255, 0, 20);
    }

    // Background
    @Override
    public TriColorSetting getBackgroundColor() {
        return new TriColorSetting(
            new SettingColor(0, 20, 0, 242),
            new SettingColor(0, 30, 0, 242),
            new SettingColor(0, 37, 0, 242)
        );
    }

    @Override
    public SettingColor getModuleBackground() {
        return new SettingColor(0, 42, 0);
    }

    // Outline
    @Override
    public TriColorSetting getOutlineColor() {
        return new TriColorSetting(
            new SettingColor(0, 13, 0),
            new SettingColor(0, 37, 0),
            new SettingColor(0, 42, 0)
        );
    }

    // Separator
    @Override
    public SettingColor getSeparatorText() {
        return new SettingColor(0, 200, 0);
    }

    @Override
    public SettingColor getSeparatorCenter() {
        return new SettingColor(0, 137, 0);
    }

    @Override
    public SettingColor getSeparatorEdges() {
        return new SettingColor(0, 42, 0);
    }

    // Scrollbar
    @Override
    public TriColorSetting getScrollbarColor() {
        return new TriColorSetting(
            new SettingColor(0, 42, 0),
            new SettingColor(0, 69, 0),
            new SettingColor(0, 75, 0)
        );
    }

    // Slider
    @Override
    public TriColorSetting getSliderHandle() {
        return new TriColorSetting(
            new SettingColor(0, 42, 0),
            new SettingColor(0, 69, 0),
            new SettingColor(0, 77, 0)
        );
    }

    @Override
    public SettingColor getSliderLeft() {
        return new SettingColor(0, 113, 0);
    }

    @Override
    public SettingColor getSliderRight() {
        return new SettingColor(0, 50, 0);
    }

    // Starscript
    @Override
    public SettingColor getStarscriptText() {
        return new SettingColor(0, 137, 0);
    }

    @Override
    public SettingColor getStarscriptBraces() {
        return new SettingColor(0, 200, 0);
    }

    @Override
    public SettingColor getStarscriptParenthesis() {
        return new SettingColor(0, 200, 0);
    }

    @Override
    public SettingColor getStarscriptDots() {
        return new SettingColor(0, 200, 0);
    }

    @Override
    public SettingColor getStarscriptCommas() {
        return new SettingColor(0, 200, 0);
    }

    @Override
    public SettingColor getStarscriptOperators() {
        return new SettingColor(0, 200, 0);
    }

    @Override
    public SettingColor getStarscriptStrings() {
        return new SettingColor(0, 102, 0);
    }

    @Override
    public SettingColor getStarscriptNumbers() {
        return new SettingColor(0, 200, 0);
    }

    @Override
    public SettingColor getStarscriptKeywords() {
        return new SettingColor(0, 255, 0);
    }

    @Override
    public SettingColor getStarscriptAccessedObjects() {
        return new SettingColor(0, 137, 0);
    }
}
