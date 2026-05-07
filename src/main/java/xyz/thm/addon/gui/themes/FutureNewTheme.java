package xyz.thm.addon.gui.themes;

import meteordevelopment.meteorclient.gui.themes.meteor.MeteorGuiTheme;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import xyz.thm.addon.gui.AdvancedGuiTheme;
import xyz.thm.addon.gui.ClientLook;
import xyz.thm.addon.gui.ClientLookTheme;
import xyz.thm.addon.gui.RecolorGuiTheme;

public class FutureNewTheme extends MeteorGuiTheme implements RecolorGuiTheme, ClientLookTheme, AdvancedGuiTheme {
    public static final FutureNewTheme INSTANCE = new FutureNewTheme();

    @Override
    public String getName() {
        return "Future (New)";
    }

    @Override
    public ClientLook getClientLook() {
        return ClientLook.FUTURE_NEW;
    }

    @Override
    public boolean useInlineModuleSettings() {
        return true;
    }

    @Override
    public boolean getCategoryIcons() {
        return true;
    }

    @Override
    public SettingColor getAccentColor() {
        return new SettingColor(30, 110, 230);
    }

    @Override
    public SettingColor getCheckboxColor() {
        return new SettingColor(40, 120, 245);
    }

    @Override
    public SettingColor getTextColor() {
        return new SettingColor(230, 236, 245);
    }

    @Override
    public SettingColor getTextSecondaryColor() {
        return new SettingColor(165, 176, 196);
    }

    @Override
    public TriColorSetting getBackgroundColor() {
        return new TriColorSetting(
            new SettingColor(4, 14, 18, 220),
            new SettingColor(28, 36, 50, 220),
            new SettingColor(38, 48, 64, 220)
        );
    }

    @Override
    public SettingColor getModuleBackground() {
        return new SettingColor(7, 37, 47, 205);
    }

    @Override
    public SettingColor getSeparatorCenter() {
        return new SettingColor(0, 188, 212);
    }
}
