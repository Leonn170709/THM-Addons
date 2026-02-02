package xyz.thm.addon.modules;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.MinecraftClient;
import xyz.thm.addon.THMAddon;

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

    private int originalFps;

    public UnfocusedFpsLimiter() {
        super(THMAddon.MAIN, "unfocused-fps", "Limits the FPS when the game is unfocused or not the main task.");
    }

    @Override
    public void onActivate() {
        originalFps = mc.options.getMaxFps().getValue();
    }

    @Override
    public void onDeactivate() {
        mc.options.getMaxFps().setValue(originalFps);
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (isWindowFocused()) {
            if (mc.options.getMaxFps().getValue() != originalFps) {
                mc.options.getMaxFps().setValue(originalFps);
            }
        } else {
            if (mc.options.getMaxFps().getValue() != unfocusedFps.get()) {
                mc.options.getMaxFps().setValue(unfocusedFps.get());
            }
        }
    }

    private boolean isWindowFocused() {
        // Use reflection or check for other indicators since window is private
        return MinecraftClient.getInstance().isWindowFocused();
    }
}
