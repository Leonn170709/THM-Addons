package xyz.thm.addon.gui.themes;

import meteordevelopment.meteorclient.gui.themes.meteor.MeteorGuiTheme;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import xyz.thm.addon.gui.AdvancedGuiTheme;
import xyz.thm.addon.gui.ClientLook;
import xyz.thm.addon.gui.ClientLookTheme;
import xyz.thm.addon.gui.RecolorGuiTheme;

public class RusherHackTheme extends MeteorGuiTheme implements RecolorGuiTheme, ClientLookTheme, AdvancedGuiTheme {
    public static final RusherHackTheme INSTANCE = new RusherHackTheme();

    @Override
    public String getName() {
        return "RusherHack";
    }

    @Override
    public ClientLook getClientLook() {
        return ClientLook.RUSHERHACK;
    }

    @Override
    public boolean useInlineModuleSettings() {
        return true;
    }

    @Override
    public SettingColor getAccentColor() {
        return new SettingColor(0, 0, 255);
    }

    @Override
    public SettingColor getCheckboxColor() {
        return new SettingColor(0, 0, 255);
    }

    @Override
    public SettingColor getTextColor() {
        return new SettingColor(222, 228, 239);
    }

    @Override
    public SettingColor getTextSecondaryColor() {
        return new SettingColor(150, 163, 186);
    }

    @Override
    public TriColorSetting getBackgroundColor() {
        return new TriColorSetting(
            new SettingColor(9, 9, 9, 225),
            new SettingColor(0, 0, 0, 235),
            new SettingColor(0, 0, 0, 235)
        );
    }

    @Override
    public SettingColor getModuleBackground() {
        return new SettingColor(15, 18, 24, 220);
    }

    @Override
    public SettingColor getSeparatorCenter() {
        return new SettingColor(41, 132, 255);
    }
}
