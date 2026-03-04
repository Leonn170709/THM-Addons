package xyz.thm.addon.system;

import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.Settings;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.System;
import meteordevelopment.meteorclient.systems.Systems;
import net.minecraft.nbt.NbtCompound;
import xyz.thm.addon.THMAddon;

public class THMSystem extends System<THMSystem> {
    public final Settings settings = new Settings();

    private final SettingGroup sgPrefix = settings.createGroup("Hash");

    public final Setting<String> hash = sgPrefix.add(new StringSetting.Builder()
        .name("Hash")
        .description("The Hash that you got")
        .defaultValue("SetYourHash")
        .wide()
        .build()
    );
    public THMSystem() {
        super("THM-Addon");
    }

    public static THMSystem get() {
        return Systems.get(THMSystem.class);
    }
    @Override
    public NbtCompound toTag() {
        NbtCompound tag = new NbtCompound();

        tag.putString("version", THMAddon.VERSION);
        tag.put("settings", settings.toTag());

        return tag;
    }

    @Override
    public THMSystem fromTag(NbtCompound tag) {
        if (tag.contains("settings")) tag.getCompound("settings");
        return this;
    }
}
