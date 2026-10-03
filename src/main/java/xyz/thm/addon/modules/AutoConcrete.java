/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.modules;

import meteordevelopment.meteorclient.events.game.OpenScreenEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.entity.EntityUtils;
import meteordevelopment.meteorclient.utils.entity.SortPriority;
import meteordevelopment.meteorclient.utils.entity.TargetUtils;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screens.inventory.AnvilScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import xyz.thm.addon.THMAddon;

public class AutoConcrete extends Module {
    public AutoConcrete() {
        super(THMAddon.PVP, "AutoConcrete",
            "Drops falling blocks above enemies' heads.");
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    // -------------------- General Settings -------------------- //
    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("range").defaultValue(4).min(0).sliderMax(6).build());

    private final Setting<Integer> concreteCount = sgGeneral.add(new IntSetting.Builder()
        .name("concrete-count").description("How many falling blocks to drop at once.")
        .defaultValue(1).min(1).max(3).sliderMax(3).build());

    private final Setting<Integer> pillarDelay = sgGeneral.add(new IntSetting.Builder()
        .name("pillar-delay").defaultValue(30).min(0).sliderMax(100).build());

    private final Setting<Integer> concreteDelay = sgGeneral.add(new IntSetting.Builder()
        .name("drop-delay").defaultValue(50).min(0).sliderMax(100).build());

    private final Setting<Boolean> rotate = sgGeneral.add(new BoolSetting.Builder()
        .name("rotate").defaultValue(true).build());

    private final Setting<Boolean> detectCrystals = sgGeneral.add(new BoolSetting.Builder()
        .name("detect-crystals").defaultValue(true).build());

    private final Setting<Boolean> airPlace = sgGeneral.add(new BoolSetting.Builder()
        .name("air-place").defaultValue(false).build());

    private final Setting<Boolean> placeSupport = sgGeneral.add(new BoolSetting.Builder()
        .name("place-support").defaultValue(true).build());

    private final Setting<Boolean> disableOnUse = sgGeneral.add(new BoolSetting.Builder()
        .name("disable-on-use").description("Turns off the module after placing falling blocks.")
        .defaultValue(false).build());

    private final Setting<Boolean> onlyInHole = sgGeneral.add(new BoolSetting.Builder()
        .name("only-in-hole")
        .description("Only place blocks if the target has blocks on all 4 sides.")
        .defaultValue(false)
        .build());

    // Pause while eating
    private final Setting<Boolean> pauseWhileEating = sgGeneral.add(new BoolSetting.Builder()
        .name("pause-while-eating")
        .description("Temporarily pauses AutoConcrete while you're eating food.")
        .defaultValue(true)
        .build());

    // -------------------- State -------------------- //
    private Player target;
    private BlockPos basePos;
    private BlockPos[] concretePositions;
    private BlockPos lastTargetEntityPos;
    private Direction placedDirection;
    private int currentPillarHeight;
    private int cooldown = 0;

    // track eating state
    private boolean wasEating = false;

    // -------------------- Lifecycle -------------------- //
    @Override
    public void onActivate() {
        reset();
        wasEating = false;
    }

    private void reset() {
        basePos = null;
        placedDirection = null;
        lastTargetEntityPos = null;
        currentPillarHeight = 1 + concreteCount.get();
        concretePositions = new BlockPos[concreteCount.get()];
        cooldown = 0;
    }

    // -------------------- Event Handlers -------------------- //
    @EventHandler
    private void onOpenScreen(OpenScreenEvent event) {
        if (event.screen instanceof AnvilScreen) event.cancel();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.level == null) return;

        // cooldown always decrements regardless of pause
        if (cooldown > 0) {
            cooldown--;
            return;
        }

        // ----- Pause While Eating -----
        if (pauseWhileEating.get() && mc.player != null) {
            boolean eatingNow = mc.player.isUsingItem() && isFood(mc.player.getUseItem());
            if (eatingNow) {
                wasEating = true;
                return; // pause this tick
            } else if (wasEating) {
                // finished eating, resume next tick
                wasEating = false;
            }
        }

        if (TargetUtils.isBadTarget(target, range.get())) {
            target = TargetUtils.getPlayerTarget(range.get(), SortPriority.LowestHealth);
            if (TargetUtils.isBadTarget(target, range.get())) return;
            reset();
        }

        BlockPos targetEntityPos = target.blockPosition();

        if (onlyInHole.get() && !isInHole(targetEntityPos)) return;

        if (lastTargetEntityPos != null && !lastTargetEntityPos.equals(targetEntityPos)) {
            info("Target moved. Resetting.");
            reset();
        }
        lastTargetEntityPos = targetEntityPos;

        FindItemResult obsidian = InvUtils.findInHotbar(stack -> Block.byItem(stack.getItem()) == Blocks.OBSIDIAN);
        FindItemResult fallingBlock = InvUtils.findInHotbar(stack -> {
            Block block = Block.byItem(stack.getItem());
            return block == Blocks.SAND
                || block == Blocks.RED_SAND
                || block == Blocks.GRAVEL
                || block == Blocks.SUSPICIOUS_SAND
                || block == Blocks.SUSPICIOUS_GRAVEL
                || block.getDescriptionId().contains("concrete_powder");
        });

        if (!fallingBlock.found() || (!obsidian.found() && !airPlace.get())) return;

        boolean crystalPresent = detectCrystals.get() && isCrystalOnSurround(target);
        currentPillarHeight = 1 + concreteCount.get();
        if (crystalPresent) currentPillarHeight++;

        if (airPlace.get()) {
            for (int i = 0; i < concreteCount.get(); i++) {
                concretePositions[i] = targetEntityPos.above(2 + i);
            }
        } else if (placeSupport.get()) {
            if (placedDirection == null || basePos == null) {
                for (Direction dir : Direction.Plane.HORIZONTAL) {
                    BlockPos side = targetEntityPos.relative(dir);
                    if (!mc.level.getBlockState(side).isAir()) {
                        boolean clear = true;
                        for (int i = 0; i < currentPillarHeight; i++) {
                            if (!mc.level.getBlockState(side.above(i + 1)).canBeReplaced()) {
                                clear = false;
                                break;
                            }
                        }
                        if (clear) {
                            placedDirection = dir;
                            basePos = side.above();
                            break;
                        }
                    }
                }
            }

            if (basePos == null || placedDirection == null) return;

            boolean allPlaced = true;
            for (int i = 0; i < currentPillarHeight; i++) {
                BlockPos pos = basePos.above(i);
                if (!mc.level.getBlockState(pos).is(Blocks.OBSIDIAN)) {
                    BlockUtils.place(pos, obsidian, rotate.get(), 0);
                    cooldown = pillarDelay.get();
                    allPlaced = false;
                    break;
                }
            }

            if (!allPlaced) return;

            for (int i = 0; i < concreteCount.get(); i++) {
                concretePositions[i] = targetEntityPos.above(2 + i);
            }
        }

        for (BlockPos pos : concretePositions) {
            if (pos != null && mc.level.getBlockState(pos).canBeReplaced()) {
                BlockUtils.place(pos, fallingBlock, rotate.get(), 0);
            }
        }

        cooldown = concreteDelay.get();

        if (disableOnUse.get()) toggle();
    }

    // -------------------- Helpers -------------------- //
    private boolean isCrystalOnSurround(Player target) {
        BlockPos pos = target.blockPosition();
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos surround = pos.relative(dir);
            for (net.minecraft.world.entity.Entity entity : mc.level.entitiesForRendering()) {
                if (entity instanceof EndCrystal) {
                    if (entity.getBoundingBox().intersects(
                        surround.getCenter().add(-0.5, 0, -0.5),
                        surround.getCenter().add(0.5, 2.5, 0.5))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean isInHole(BlockPos pos) {
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            if (mc.level.getBlockState(pos.relative(dir)).isAir()) return false;
        }
        return true;
    }

    // Same helper signature/logic as in AutoMinePlus
    private boolean isFood(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.get(DataComponents.FOOD) != null;
    }

    @Override
    public String getInfoString() {
        return target != null ? (airPlace.get() ? "AirPlace - " : "Pillar - ") + EntityUtils.getName(target) : null;
    }
}
