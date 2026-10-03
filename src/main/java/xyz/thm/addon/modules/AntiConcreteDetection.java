/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.modules;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
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
        if (mc.player == null || mc.level == null) return;

        for (Entity entity : mc.level.players()) {
            if (!(entity instanceof Player player)) continue;
            if (player == mc.player || player.isSpectator() || player.isCreative()) continue;

            BlockPos blockPos = player.blockPosition(); // The block that has the button/torch inside
            Block block = mc.level.getBlockState(blockPos).getBlock();

            if (isButtonBlock(block) || isTorchBlock(block)) {
                if (rotate.get()) {
                    Rotations.rotate(Rotations.getYaw(blockPos.getCenter()), Rotations.getPitch(blockPos.getCenter()));
                }

                if (breakMode.get() == BreakMode.Hold) {
                    mc.gameMode.continueDestroyBlock(blockPos, Direction.UP);
                } else {
                    mc.gameMode.startDestroyBlock(blockPos, Direction.UP);
                    mc.player.swing(InteractionHand.MAIN_HAND);
                }

                break;
            }
        }
    }

    // -------------------- Helpers -------------------- //
    private boolean isButtonBlock(Block block) {
        return block.getDescriptionId().toLowerCase().contains("button");
    }

    private boolean isTorchBlock(Block block) {
        return block.getDescriptionId().toLowerCase().contains("torch");
    }

    // -------------------- Enums -------------------- //
    public enum BreakMode {
        Tap,
        Hold
    }
}
