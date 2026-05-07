package xyz.thm.addon.gui.themes;

import meteordevelopment.meteorclient.gui.themes.meteor.MeteorGuiTheme;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import xyz.thm.addon.gui.AdvancedGuiTheme;
import xyz.thm.addon.gui.ClientLook;
import xyz.thm.addon.gui.ClientLookTheme;
import xyz.thm.addon.gui.RecolorGuiTheme;

public class FutureOldTheme extends MeteorGuiTheme implements RecolorGuiTheme, AdvancedGuiTheme, ClientLookTheme {
    public static final FutureOldTheme INSTANCE = new FutureOldTheme();

    @Override
    public String getName() {
        return "Future (Old)";
    }

    @Override
    public boolean useInlineModuleSettings() {
        return true;
    }

    @Override
    public ClientLook getClientLook() {
        return ClientLook.FUTURE_OLD;
    }

    @Override
    public SettingColor getAccentColor() {
        return new SettingColor(170, 0, 0);
    }

    @Override
    public SettingColor getCheckboxColor() {
        return new SettingColor(170, 0, 0);
    }

    @Override
    public SettingColor getTextColor() {
        return new SettingColor(210, 210, 210);
    }

    @Override
    public SettingColor getTextSecondaryColor() {
        return new SettingColor(150, 150, 150);
    }

    @Override
    public TriColorSetting getBackgroundColor() {
        return new TriColorSetting(
            new SettingColor(10, 10, 10, 215),
            new SettingColor(0, 0, 0, 204),
            new SettingColor(0, 0, 0, 204)
        );
    }

    @Override
    public SettingColor getModuleBackground() {
        return new SettingColor(40, 40, 40, 200);
    }

    @Override
    public SettingColor getSeparatorCenter() {
        return new SettingColor(203, 45, 62);
    }

    @Override
    public TriColorSetting getSliderHandle() {
        return new TriColorSetting(
            new SettingColor(170, 30, 44),
            new SettingColor(203, 45, 62),
            new SettingColor(224, 77, 92)
        );
    }
}
