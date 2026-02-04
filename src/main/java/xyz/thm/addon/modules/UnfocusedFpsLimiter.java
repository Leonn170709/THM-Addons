package xyz.thm.addon.modules;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.SimpleOption;
import net.minecraft.sound.SoundCategory;
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
        MinecraftClient mc = MinecraftClient.getInstance();
        originalFps = mc.options.getMaxFps().getValue();
        if (limitSound.get()) {
            originalMasterVolume = mc.options.getSoundVolumeOption(SoundCategory.MASTER).getValue();
        }
    }

    @Override
    public void onDeactivate() {
        MinecraftClient mc = MinecraftClient.getInstance();
        mc.options.getMaxFps().setValue(originalFps);
        if (limitSound.get() && originalMasterVolume != null) {
            mc.options.getSoundVolumeOption(SoundCategory.MASTER).setValue(originalMasterVolume);
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (isWindowFocused()) {
            // Window Focused
            if (mc.options.getMaxFps().getValue() != originalFps) {
                mc.options.getMaxFps().setValue(originalFps);
            }
            if (limitSound.get() && originalMasterVolume != null) {
                SimpleOption<Double> soundOption = mc.options.getSoundVolumeOption(SoundCategory.MASTER);
                if (!soundOption.getValue().equals(originalMasterVolume)) {
                    soundOption.setValue(originalMasterVolume);
                }
            }
        } else {
            // Window not focused
            if (!Objects.equals(mc.options.getMaxFps().getValue(), unfocusedFps.get())) {
                mc.options.getMaxFps().setValue(unfocusedFps.get());
            }
            if (limitSound.get()) {
                SimpleOption<Double> soundOption = mc.options.getSoundVolumeOption(SoundCategory.MASTER);
                double targetVolume = soundVolume.get() / 100.0;
                double currentVolume = soundOption.getValue();
                if (Math.abs(currentVolume - targetVolume) > 0.01) {
                    soundOption.setValue(targetVolume);
                }
            }
        }
    }

    private boolean isWindowFocused() {
        return MinecraftClient.getInstance().isWindowFocused();
    }
}
