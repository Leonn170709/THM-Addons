package xyz.thm.addon.gui.themes;

import meteordevelopment.meteorclient.gui.themes.meteor.MeteorGuiTheme;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import xyz.thm.addon.gui.AdvancedGuiTheme;
import xyz.thm.addon.gui.ClientLook;
import xyz.thm.addon.gui.ClientLookTheme;
import xyz.thm.addon.gui.RecolorGuiTheme;

public class ImpactTheme extends MeteorGuiTheme implements RecolorGuiTheme, ClientLookTheme, AdvancedGuiTheme {
    public static final ImpactTheme INSTANCE = new ImpactTheme();

    @Override
    public String getName() {
        return "Impact";
    }

    @Override
    public ClientLook getClientLook() {
        return ClientLook.IMPACT;
    }

    @Override
    public boolean useInlineModuleSettings() {
        return true;
    }

    @Override
    public SettingColor getAccentColor() {
        return new SettingColor(85, 255, 255);
    }

    @Override
    public SettingColor getCheckboxColor() {
        return new SettingColor(85, 255, 255);
    }

    @Override
    public SettingColor getTextColor() {
        return new SettingColor(224, 234, 246);
    }

    @Override
    public SettingColor getTextSecondaryColor() {
        return new SettingColor(165, 180, 201);
    }

    @Override
    public TriColorSetting getBackgroundColor() {
        return new TriColorSetting(
            new SettingColor(28, 34, 44, 180),
            new SettingColor(45, 45, 45, 153),
            new SettingColor(45, 45, 45, 153)
        );
    }

    @Override
    public SettingColor getModuleBackground() {
        return new SettingColor(45, 58, 77, 170);
    }
}
