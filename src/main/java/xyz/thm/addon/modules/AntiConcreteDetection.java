package xyz.thm.addon.modules;

import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import xyz.thm.addon.THMAddon;

public class AntiConcreteDetection extends Module {
    public AntiConcreteDetection() {
        super(THMAddon.PVP, "AntiConcreteDetection",
            "Breaks buttons and torches inside enemy hit-box.");
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    // -------------------- General Settings -------------------- //
    private final Setting<BreakMode> breakMode = sgGeneral.add(new EnumSetting.Builder<BreakMode>()
        .name("break-mode")
        .description("How to break buttons/torches under enemies.")
        .defaultValue(BreakMode.Tap)
        .build()
    );

    private final Setting<Boolean> rotate = sgGeneral.add(new BoolSetting.Builder()
        .name("rotate")
        .description("Rotate toward the block being broken.")
        .defaultValue(true)
        .build()
    );

    // -------------------- Event Handlers -------------------- //
    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        for (Entity entity : mc.world.getPlayers()) {
            if (!(entity instanceof PlayerEntity player)) continue;
            if (player == mc.player || player.isSpectator() || player.isCreative()) continue;

            BlockPos blockPos = player.getBlockPos(); // The block that has the button/torch inside
            Block block = mc.world.getBlockState(blockPos).getBlock();

            if (isButtonBlock(block) || isTorchBlock(block)) {
                if (rotate.get()) {
                    Rotations.rotate(Rotations.getYaw(blockPos.toCenterPos()), Rotations.getPitch(blockPos.toCenterPos()));
                }

                if (breakMode.get() == BreakMode.Hold) {
                    mc.interactionManager.updateBlockBreakingProgress(blockPos, Direction.UP);
                } else {
                    mc.interactionManager.attackBlock(blockPos, Direction.UP);
                    mc.player.swingHand(Hand.MAIN_HAND);
                }

                break;
            }
        }
    }

    // -------------------- Helpers -------------------- //
    private boolean isButtonBlock(Block block) {
        return block.getTranslationKey().toLowerCase().contains("button");
    }

    private boolean isTorchBlock(Block block) {
        return block.getTranslationKey().toLowerCase().contains("torch");
    }

    // -------------------- Enums -------------------- //
    public enum BreakMode {
        Tap,
        Hold
    }
}
