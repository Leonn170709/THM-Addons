package xyz.thm.addon.gui.themes;

import meteordevelopment.meteorclient.gui.themes.meteor.MeteorGuiTheme;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import xyz.thm.addon.gui.AdvancedGuiTheme;
import xyz.thm.addon.gui.ClientLook;
import xyz.thm.addon.gui.ClientLookTheme;
import xyz.thm.addon.gui.RecolorGuiTheme;

public class WurstTheme extends MeteorGuiTheme implements RecolorGuiTheme, ClientLookTheme, AdvancedGuiTheme {
    public static final WurstTheme INSTANCE = new WurstTheme();

    @Override
    public String getName() {
        return "Wurst";
    }

    @Override
    public ClientLook getClientLook() {
        return ClientLook.WURST;
    }

    @Override
    public boolean useInlineModuleSettings() {
        return true;
    }

    @Override
    public SettingColor getAccentColor() {
        return new SettingColor(204, 0, 102);
    }

    @Override
    public SettingColor getCheckboxColor() {
        return new SettingColor(0, 235, 84);
    }

    @Override
    public SettingColor getTextColor() {
        return new SettingColor(255, 110, 204);
    }

    @Override
    public SettingColor getTextSecondaryColor() {
        return new SettingColor(255, 138, 214);
    }

    @Override
    public TriColorSetting getBackgroundColor() {
        return new TriColorSetting(
            new SettingColor(8, 17, 10, 220),
            new SettingColor(24, 10, 20, 220),
            new SettingColor(35, 14, 29, 220)
        );
    }
}
