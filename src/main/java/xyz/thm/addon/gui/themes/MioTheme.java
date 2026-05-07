package xyz.thm.addon.gui.themes;

import meteordevelopment.meteorclient.gui.themes.meteor.MeteorGuiTheme;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import xyz.thm.addon.gui.AdvancedGuiTheme;
import xyz.thm.addon.gui.ClientLook;
import xyz.thm.addon.gui.ClientLookTheme;
import xyz.thm.addon.gui.RecolorGuiTheme;

public class MioTheme extends MeteorGuiTheme implements RecolorGuiTheme, ClientLookTheme, AdvancedGuiTheme {
    public static final MioTheme INSTANCE = new MioTheme();

    @Override
    public String getName() {
        return "Mio Client";
    }

    @Override
    public ClientLook getClientLook() {
        return ClientLook.MIO;
    }

    @Override
    public boolean useInlineModuleSettings() {
        return true;
    }

    @Override
    public SettingColor getAccentColor() {
        return new SettingColor(176, 224, 230);
    }

    @Override
    public SettingColor getCheckboxColor() {
        return new SettingColor(176, 224, 230);
    }

    @Override
    public SettingColor getFavoriteColor() {
        return new SettingColor(255, 255, 255);
    }

    @Override
    public SettingColor getTextColor() {
        return new SettingColor(238, 248, 250);
    }

    @Override
    public TriColorSetting getBackgroundColor() {
        return new TriColorSetting(
            new SettingColor(26, 11, 10, 215),
            new SettingColor(10, 10, 10, 128),
            new SettingColor(10, 10, 10, 128)
        );
    }

    @Override
    public SettingColor getModuleBackground() {
        return new SettingColor(16, 20, 24, 110);
    }
}
