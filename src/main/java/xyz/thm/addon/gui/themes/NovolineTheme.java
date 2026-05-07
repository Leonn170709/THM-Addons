package xyz.thm.addon.gui.themes;

import meteordevelopment.meteorclient.gui.themes.meteor.MeteorGuiTheme;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import xyz.thm.addon.gui.AdvancedGuiTheme;
import xyz.thm.addon.gui.ClientLook;
import xyz.thm.addon.gui.ClientLookTheme;
import xyz.thm.addon.gui.RecolorGuiTheme;

public class NovolineTheme extends MeteorGuiTheme implements RecolorGuiTheme, ClientLookTheme, AdvancedGuiTheme {
    public static final NovolineTheme INSTANCE = new NovolineTheme();

    @Override
    public String getName() {
        return "Novoline";
    }

    @Override
    public ClientLook getClientLook() {
        return ClientLook.NOVOLINE;
    }

    @Override
    public boolean useInlineModuleSettings() {
        return true;
    }

    @Override
    public SettingColor getAccentColor() {
        return new SettingColor(0, 255, 0);
    }

    @Override
    public SettingColor getCheckboxColor() {
        return new SettingColor(0, 255, 0);
    }

    @Override
    public SettingColor getTextColor() {
        return new SettingColor(233, 244, 255);
    }

    @Override
    public SettingColor getTextSecondaryColor() {
        return new SettingColor(140, 180, 160);
    }

    @Override
    public TriColorSetting getBackgroundColor() {
        return new TriColorSetting(
            new SettingColor(7, 14, 28, 220),
            new SettingColor(26, 26, 26, 255),
            new SettingColor(26, 26, 26, 255)
        );
    }
}
