package xyz.thm.addon.gui.themes;

import meteordevelopment.meteorclient.gui.themes.meteor.MeteorGuiTheme;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import xyz.thm.addon.gui.AdvancedGuiTheme;
import xyz.thm.addon.gui.ClientLook;
import xyz.thm.addon.gui.ClientLookTheme;
import xyz.thm.addon.gui.RecolorGuiTheme;

public class VapeTheme extends MeteorGuiTheme implements RecolorGuiTheme, ClientLookTheme, AdvancedGuiTheme {
    public static final VapeTheme INSTANCE = new VapeTheme();

    @Override
    public String getName() {
        return "Vape";
    }

    @Override
    public ClientLook getClientLook() {
        return ClientLook.VAPE;
    }

    @Override
    public boolean useInlineModuleSettings() {
        return true;
    }

    @Override
    public SettingColor getAccentColor() {
        return new SettingColor(255, 35, 110);
    }

    @Override
    public SettingColor getCheckboxColor() {
        return new SettingColor(255, 35, 110);
    }

    @Override
    public SettingColor getTextColor() {
        return new SettingColor(255, 225, 237);
    }

    @Override
    public SettingColor getTextSecondaryColor() {
        return new SettingColor(201, 132, 159);
    }

    @Override
    public TriColorSetting getBackgroundColor() {
        return new TriColorSetting(
            new SettingColor(17, 7, 13, 220),
            new SettingColor(35, 11, 23, 220),
            new SettingColor(50, 17, 34, 220)
        );
    }
}
