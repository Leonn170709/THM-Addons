/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.modules;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.sounds.SoundSource;
import xyz.thm.addon.THMAddon;

import java.util.Objects;

public class UnfocusedFpsLimiter extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> unfocusedFps = sgGeneral.add(new IntSetting.Builder()
        .name("unfocused-fps")
        .description("The FPS limit when the game window is not focused.")
        .defaultValue(30)
        .min(1)
        .max(260)
        .sliderRange(1, 260)
        .build()
    );

    private final Setting<Boolean> limitSound = sgGeneral.add(new BoolSetting.Builder()
        .name("limit-sound")
        .description("Limits the sound volume when the game window is not focused.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> soundVolume = sgGeneral.add(new IntSetting.Builder()
        .name("sound-volume")
        .description("The sound volume percentage when the game window is not focused (0-100%).")
        .defaultValue(50)
        .min(0)
        .max(100)
        .sliderRange(0, 100)
        .visible(() -> limitSound.get())
        .build()
    );

    private int originalFps;
    private Double originalMasterVolume;

    public UnfocusedFpsLimiter() {
        super(THMAddon.MAIN, "unfocused-fps", "Limits the FPS and optionally sound when the game is unfocused or not the main task.");
    }

    @Override
    public void onActivate() {
        Minecraft mc = Minecraft.getInstance();
        originalFps = mc.options.framerateLimit().get();
        if (limitSound.get()) {
            originalMasterVolume = mc.options.getSoundSourceOptionInstance(SoundSource.MASTER).get();
        }
    }

    @Override
    public void onDeactivate() {
        Minecraft mc = Minecraft.getInstance();
        mc.options.framerateLimit().set(originalFps);
        if (limitSound.get() && originalMasterVolume != null) {
            mc.options.getSoundSourceOptionInstance(SoundSource.MASTER).set(originalMasterVolume);
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();

        if (isWindowFocused()) {
            // Window Focused
            if (mc.options.framerateLimit().get() != originalFps) {
                mc.options.framerateLimit().set(originalFps);
            }
            if (limitSound.get() && originalMasterVolume != null) {
                OptionInstance<Double> soundOption = mc.options.getSoundSourceOptionInstance(SoundSource.MASTER);
                if (!soundOption.get().equals(originalMasterVolume)) {
                    soundOption.set(originalMasterVolume);
                }
            }
        } else {
            // Window not focused
            if (!Objects.equals(mc.options.framerateLimit().get(), unfocusedFps.get())) {
                mc.options.framerateLimit().set(unfocusedFps.get());
            }
            if (limitSound.get()) {
                OptionInstance<Double> soundOption = mc.options.getSoundSourceOptionInstance(SoundSource.MASTER);
                double targetVolume = soundVolume.get() / 100.0;
                double currentVolume = soundOption.get();
                if (Math.abs(currentVolume - targetVolume) > 0.01) {
                    soundOption.set(targetVolume);
                }
            }
        }
    }

    private boolean isWindowFocused() {
        return Minecraft.getInstance().isWindowActive();
    }
}
