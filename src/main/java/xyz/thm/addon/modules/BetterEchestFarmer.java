/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package xyz.thm.addon.modules;

import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.world.PacketMine;
import meteordevelopment.meteorclient.systems.modules.world.Timer;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import xyz.thm.addon.THMAddon;

public class BetterEchestFarmer extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Boolean> selfToggle = sgGeneral.add(new BoolSetting.Builder()
        .name("self-toggle")
        .description("Disables when you reach the desired amount of obsidian.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> amount = sgGeneral.add(new IntSetting.Builder()
        .name("amount")
        .description("The amount of obsidian to farm.")
        .defaultValue(64)
        .sliderMax(128)
        .range(8, 512)
        .sliderRange(8, 128)
        .visible(selfToggle::get)
        .build()
    );

    private final Setting<Boolean> ignoreExisting = sgGeneral.add(new BoolSetting.Builder()
        .name("ignore-existing")
        .description("Ignores existing obsidian in your inventory and mines the total target amount.")
        .defaultValue(true)
        .visible(selfToggle::get)
        .build()
    );

    private final Setting<Boolean> rotatePlace = sgGeneral.add(new BoolSetting.Builder()
        .name("rotate-place")
        .description("Rotate when placing the ender chest.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> useTimer = sgGeneral.add(new BoolSetting.Builder()
        .name("use-timer")
        .description("Use Timer module override while the farmer is active.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Double> timerMultiplier = sgGeneral.add(new DoubleSetting.Builder()
        .name("timer-multiplier")
        .description("Timer speed multiplier while the farmer is active.")
        .defaultValue(1.5)
        .range(1.0, 5.0)
        .sliderRange(1.0, 3.0)
        .visible(useTimer::get)
        .build()
    );

    private final Setting<Boolean> instaMineSilentSwap = sgGeneral.add(new BoolSetting.Builder()
        .name("Silent-swap")
        .description("Silently swap to the enderchest when placing it")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> instaMine = sgGeneral.add(new BoolSetting.Builder()
        .name("insta-mine")
        .description("Use instant packet mining to break the ender chest.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> instaMineRotate = sgGeneral.add(new BoolSetting.Builder()
        .name("insta-mine-rotate")
        .description("Rotate when instant mining the ender chest.")
        .defaultValue(false)
        .visible(instaMine::get)
        .build()
    );

    private final Setting<Integer> instaMineDelay = sgGeneral.add(new IntSetting.Builder()
        .name("insta-mine-delay")
        .description("Ticks to wait between place and instant break.")
        .defaultValue(1)
        .range(0, 20)
        .sliderRange(0, 10)
        .visible(instaMine::get)
        .build()
    );

    // Render

    private final Setting<Boolean> swingHand = sgRender.add(new BoolSetting.Builder()
        .name("swing-hand")
        .description("Swing hand client-side.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> render = sgRender.add(new BoolSetting.Builder()
        .name("render")
        .description("Renders a block overlay where the obsidian will be placed.")
        .defaultValue(true)
        .build()
    );

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .description("How the shapes are rendered.")
        .defaultValue(ShapeMode.Both)
        .build()
    );

    private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder()
        .name("side-color")
        .description("The color of the sides of the blocks being rendered.")
        .defaultValue(new SettingColor(204, 0, 0, 50))
        .build()
    );

    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
        .name("line-color")
        .description("The color of the lines of the blocks being rendered.")
        .defaultValue(new SettingColor(204, 0, 0, 255))
        .build()
    );

    private final VoxelShape SHAPE = Block.createCuboidShape(1.0D, 0.0D, 1.0D, 15.0D, 14.0D, 15.0D);
    private static final double PLACE_RANGE = 4.0;

    private BlockPos target;
    private int startCount;
    private boolean primed;
    private int rebreakTimer;
    private int rebreakTimeout;
    private boolean timerApplied;
    private boolean timerWasActive;
    private Double timerPrevOverride;

    public BetterEchestFarmer() {
        super(THMAddon.MAIN, "Better-echest-farmer", "Places and breaks EChests to farm obsidian.");
    }

    @Override
    public void onActivate() {
        target = null;
        startCount = InvUtils.find(Items.OBSIDIAN).count();
        primed = false;
        rebreakTimer = 0;
        rebreakTimeout = 0;
        timerApplied = false;
        timerWasActive = false;
        timerPrevOverride = null;
        if (useTimer.get()) applyTimerOverride();
    }

    @Override
    public void onDeactivate() {
        InvUtils.swapBack();
        primed = false;
        rebreakTimer = 0;
        rebreakTimeout = 0;
        restoreTimerOverride();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (useTimer.get()) {
            if (!timerApplied) applyTimerOverride();
            else syncTimerOverride();
        } else if (timerApplied) {
            restoreTimerOverride();
        }

        // Finding target pos
        if (target == null) {
            if (mc.player == null) return;
            HitResult hit = mc.player.raycast(PLACE_RANGE, 0, false);
            if (hit.getType() != HitResult.Type.BLOCK) return;

            BlockPos pos = ((BlockHitResult) hit).getBlockPos().up();
            BlockState state = mc.world.getBlockState(pos);

            if (state.isReplaceable() || state.getBlock() == Blocks.ENDER_CHEST) {
                target = ((BlockHitResult) hit).getBlockPos().up();
            } else return;
        }

        // Disable if the block is too far away
        if (!PlayerUtils.isWithinReach(target) || mc.player.getEyePos().distanceTo(target.toCenterPos()) > PLACE_RANGE) {
            error("Target block pos out of reach.");
            target = null;
            return;
        }

        // Toggle if obby amount reached
        if (selfToggle.get() && InvUtils.find(Items.OBSIDIAN).count() - (ignoreExisting.get() ? startCount : 0) >= amount.get()) {
            InvUtils.swapBack();
            toggle();
            return;
        }

        // Keep pickaxe selected unless we are placing.
        ensurePickaxeSelected();

        // Break existing echest at target pos
        if (mc.world.getBlockState(target).getBlock() == Blocks.ENDER_CHEST) {
            breakEchest(target);
        }

        // Place echest if the target pos is empty
        if (mc.world.getBlockState(target).isReplaceable()) {
            FindItemResult echest = ensureEchestInHotbar();

            if (!echest.found()) {
                error("No Echests in hotbar, disabling");
                toggle();
                return;
            }

            placeEchest(target, echest);
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (target == null || !render.get() || Modules.get().get(PacketMine.class).isMiningBlock(target)) return;

        Box box = SHAPE.getBoundingBoxes().getFirst();
        event.renderer.box(target.getX() + box.minX, target.getY() + box.minY, target.getZ() + box.minZ,
            target.getX() + box.maxX, target.getY() + box.maxY, target.getZ() + box.maxZ,
            sideColor.get(), lineColor.get(), shapeMode.get(), 0);
    }

    private void breakEchest(BlockPos pos) {
        if (instaMine.get()) {
            if (primed) {
                if (rebreakTimeout++ > 60) {
                    primed = false;
                    rebreakTimeout = 0;
                }
                if (!primed) return;
                if (rebreakTimer > 0) {
                    rebreakTimer--;
                    return;
                }

                int bestSlot = findBestNonSilkToolSlot();
                if (bestSlot == -1) return;
                int selectedSlot = mc.player.getInventory().getSelectedSlot();
                boolean swappedForRebreak = false;

                if (instaMineSilentSwap.get()) {
                    if (selectedSlot != bestSlot) {
                        InvUtils.swap(bestSlot, false);
                        swappedForRebreak = true;
                    }
                } else {
                    if (selectedSlot != bestSlot) InvUtils.swap(bestSlot, false);
                }

                Runnable sendRebreakPackets = () -> {
                    if (mc.getNetworkHandler() == null) return;
                    mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(
                        PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, pos, BlockUtils.getDirection(pos)));
                };

                if (instaMineRotate.get()) {
                    Rotations.rotate(Rotations.getYaw(pos.toCenterPos()), Rotations.getPitch(pos.toCenterPos()), sendRebreakPackets);
                } else {
                    sendRebreakPackets.run();
                }

                if (swappedForRebreak) InvUtils.swap(selectedSlot, false);
                rebreakTimer = instaMineDelay.get();
                return;
            }

            int bestSlot = findBestNonSilkToolSlot();
            if (bestSlot == -1) return;
            InvUtils.swap(bestSlot, instaMineSilentSwap.get());
            if (instaMineRotate.get()) rotateTo(pos);
            sendInstantMinePackets(pos, getMineDirection(pos));
            InvUtils.swapBack();
            return;
        }

        int bestSlot = findBestNonSilkToolSlot();
        if (bestSlot == -1) return;
        InvUtils.swap(bestSlot, true);
        BlockUtils.breakBlock(pos, swingHand.get());
    }

    private void placeEchest(BlockPos pos, FindItemResult echest) {
        int slot = echest.slot();
        if (slot < 0 || slot > 8) return;
        int selectedSlot = mc.player.getInventory().getSelectedSlot();
        InvUtils.swap(slot, false);
        BlockUtils.place(pos, Hand.MAIN_HAND, slot, rotatePlace.get(), 0, true, true, true);
        if (selectedSlot != slot) InvUtils.swap(selectedSlot, false);
        primed = true;
        rebreakTimer = instaMineDelay.get();
        rebreakTimeout = 0;
    }

    private Direction getMineDirection(BlockPos pos) {
        if (mc.player == null) return Direction.UP;
        double dx = pos.getX() + 0.5 - mc.player.getX();
        double dy = pos.getY() + 0.5 - mc.player.getEyeY();
        double dz = pos.getZ() + 0.5 - mc.player.getZ();
        return Direction.getFacing(dx, dy, dz);
    }

    private int findBestNonSilkToolSlot() {
        double bestScore = -1;
        int bestSlot = -1;

        for (int i = 0; i < 9; i++) {
            ItemStack itemStack = mc.player.getInventory().getStack(i);
            if (Utils.hasEnchantment(itemStack, Enchantments.SILK_TOUCH)) continue;

            double score = itemStack.getMiningSpeedMultiplier(Blocks.ENDER_CHEST.getDefaultState());
            if (score > bestScore) {
                bestScore = score;
                bestSlot = i;
            }
        }

        return bestSlot;
    }

    private void ensurePickaxeSelected() {
        int bestSlot = findBestNonSilkToolSlot();
        if (bestSlot == -1) return;
        int selectedSlot = mc.player.getInventory().getSelectedSlot();
        if (selectedSlot != bestSlot) InvUtils.swap(bestSlot, false);
    }

    private FindItemResult ensureEchestInHotbar() {
        FindItemResult hotbar = InvUtils.findInHotbar(Items.ENDER_CHEST);
        if (hotbar.found()) return hotbar;

        FindItemResult inv = InvUtils.find(Items.ENDER_CHEST);
        if (!inv.found()) return hotbar;

        int pickSlot = findBestNonSilkToolSlot();
        int hotbarSlot = findHotbarSlotForEchest(pickSlot);
        if (hotbarSlot == -1) return hotbar;

        InvUtils.move().from(inv.slot()).toHotbar(hotbarSlot);
        return InvUtils.findInHotbar(Items.ENDER_CHEST);
    }

    private int findHotbarSlotForEchest(int pickSlot) {
        int fallback = -1;
        for (int i = 0; i < 9; i++) {
            if (i == pickSlot) continue;
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isEmpty()) return i;
            if (fallback == -1) fallback = i;
        }
        return fallback;
    }

    private void rotateTo(BlockPos pos) {
        if (mc.player == null) return;
        Rotations.rotate(Rotations.getYaw(pos.toCenterPos()), Rotations.getPitch(pos.toCenterPos()));
    }

    private void sendInstantMinePackets(BlockPos pos, Direction direction) {
        if (mc.getNetworkHandler() == null) return;
        mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, pos, direction));
        mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.ABORT_DESTROY_BLOCK, pos, direction));
        mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, pos, direction));
        if (swingHand.get()) {
            mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
        }
    }

    private void applyTimerOverride() {
        Timer timer = Modules.get().get(Timer.class);
        if (timer == null) return;
        timerWasActive = timer.isActive();
        timerPrevOverride = readRawTimerOverride(timer);
        timer.setOverride(timerMultiplier.get());
        if (!timer.isActive()) timer.toggle();
        timerApplied = true;
    }

    private void syncTimerOverride() {
        Timer timer = Modules.get().get(Timer.class);
        if (timer == null) return;
        timer.setOverride(timerMultiplier.get());
    }

    private void restoreTimerOverride() {
        if (!timerApplied) return;
        Timer timer = Modules.get().get(Timer.class);
        if (timer == null) {
            timerApplied = false;
            return;
        }
        double restore = timerPrevOverride == null ? Timer.OFF : timerPrevOverride;
        timer.setOverride(restore);
        if (!timerWasActive && timer.isActive()) timer.toggle();
        timerApplied = false;
    }

    private Double readRawTimerOverride(Timer timer) {
        try {
            java.lang.reflect.Field overrideField = Timer.class.getDeclaredField("override");
            overrideField.setAccessible(true);
            Object value = overrideField.get(timer);
            return value instanceof Double ? (Double) value : null;
        } catch (Throwable ignored) {
            return null;
        }
    }
}
