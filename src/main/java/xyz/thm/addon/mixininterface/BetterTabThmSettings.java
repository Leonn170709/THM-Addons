package xyz.thm.addon.mixininterface;

import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;

public interface BetterTabThmSettings {
    Setting<Boolean> thm$getHighlightMembers();
    Setting<Boolean> thm$getUseRankColor();
    Setting<SettingColor> thm$getHighlightColor();
}
