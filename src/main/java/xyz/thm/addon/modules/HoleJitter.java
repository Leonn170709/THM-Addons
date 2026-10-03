/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.modules;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import xyz.thm.addon.THMAddon;

import java.util.Random;

/**
 * Snaps the player to random sub-block offsets around the centre of the block they're standing in,
 * so an enemy crystal aura's movement prediction keeps aiming at where you were rather than where
 * you are. Every offset is measured from the feet block's centre, never from the current position,
 * so the jitter can't drift out of the 1x1 no matter how long it runs.
 */
public class HoleJitter extends Module {
    /** Half a block minus the player's own half-width (0.3), i.e. as far off-centre as the hitbox fits. */
    private static final double MAX_OFFSET = 0.2;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> onlyInHole = sgGeneral.add(new BoolSetting.Builder()
        .name("only-in-hole")
        .description("Only jitter while all four sides of your block are solid.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("delay")
        .description("Ticks between jitters.")
        .defaultValue(20)
        .min(1)
        .sliderRange(1, 40)
        .build()
    );

    private final Setting<Double> distance = sgGeneral.add(new DoubleSetting.Builder()
        .name("distance")
        .description("How far off the block's centre to move.")
        .defaultValue(0.15)
        .min(0.01)
        .max(MAX_OFFSET)
        .sliderRange(0.01, MAX_OFFSET)
        .build()
    );

    private final Random random = new Random();
    private int timer;

    public HoleJitter() {
        super(THMAddon.PVP, "hole-jitter", "Random sub-block clips to break enemy crystal aura prediction.");
    }

    @Override
    public void onActivate() {
        timer = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.level == null) return;
        if (onlyInHole.get() && !inHole()) return;
        if (timer-- > 0) return;

        timer = delay.get();
        jitter();
    }

    private void jitter() {
        BlockPos feet = mc.player.blockPosition();
        // One of the 8 compass directions; the diagonals come out at 0.707 * distance per axis, so
        // they stay inside the block just like the straight ones.
        double angle = random.nextInt(8) * (Math.PI / 4);
        double offset = distance.get();

        mc.player.setPos(
            feet.getX() + 0.5 + Math.cos(angle) * offset,
            mc.player.getY(),
            feet.getZ() + 0.5 + Math.sin(angle) * offset
        );
    }

    private boolean inHole() {
        BlockPos feet = mc.player.blockPosition();
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos side = feet.relative(dir);
            if (mc.level.getBlockState(side).getCollisionShape(mc.level, side).isEmpty()) return false;
        }
        return true;
    }
}
