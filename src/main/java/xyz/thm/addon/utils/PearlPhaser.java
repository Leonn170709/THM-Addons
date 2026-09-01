/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.utils;

import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import net.minecraft.block.ScaffoldingBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.*;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import xyz.thm.addon.mixin.accessor.PlayerInventoryAccessor;

import java.util.function.BooleanSupplier;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/**
 * The straight-down phase-pearl throw, plus the "Pearl" and "Self Place" setting groups that configure
 * it. Construct one per module ({@code new PearlPhaser(settings)} in the module constructor) and call
 * {@link #throwPearl()} — each module then owns its own independent copy of every knob.
 */
public class PearlPhaser {

    private final InventoryManager inventoryManager = InventoryManager.getInstance();

    public final Setting<Boolean> autoPitch;
    public final Setting<Integer> pitch;
    public final Setting<Boolean> swapAlternative;
    /** Null when built without extras. */
    public final Setting<Boolean> attack;
    public final Setting<Boolean> swing;
    public final Setting<Boolean> antiAntiPhase;
    /** Null when built without extras. */
    public final Setting<Boolean> selfFill;
    public final Setting<Boolean> selfPlace;
    public final Setting<SelfPlaceType> selfPlaceType;
    public final Setting<Boolean> optimize;
    public final Setting<Boolean> selfPlaceRotate;

    /** Everything, always visible. Unaimed owner — no auto-pitch, the slider is the only pitch there is. */
    public PearlPhaser(Settings settings) {
        this(settings, true, () -> true, false);
    }

    public PearlPhaser(Settings settings, boolean extras, BooleanSupplier visible) {
        this(settings, extras, visible, true);
    }

    /**
     * {@code extras = false} leaves out attack and self-fill. {@code visible} hides every setting this
     * creates when it returns false — Meteor has no per-group visibility, so it's ANDed into each one.
     * {@code aimed} says whether the owner calls {@link #throwPearl(Vec3d)}; only then can a pitch be
     * solved, so only then is the auto-pitch toggle shown.
     */
    public PearlPhaser(Settings settings, boolean extras, BooleanSupplier visible, boolean aimed) {
        SettingGroup sgPearl = settings.createGroup("Pearl");

        autoPitch = sgPearl.add(new BoolSetting.Builder()
            .name("auto-pitch")
            .description("Solves the pitch from where the pearl has to land. Off uses the pitch slider instead.")
            .defaultValue(true)
            .visible(() -> visible.getAsBoolean() && aimed)
            .build()
        );
        pitch = sgPearl.add(new IntSetting.Builder()
            .name("pitch")
            .description("The pitch angle to throw pearls.")
            .defaultValue(85)
            .range(70, 90)
            .visible(() -> visible.getAsBoolean() && (!aimed || !autoPitch.get()))
            .build()
        );
        swapAlternative = sgPearl.add(new BoolSetting.Builder()
            .name("swap-alternative")
            .description("Uses inventory swap for swapping to pearls.")
            .defaultValue(true)
            .visible(visible::getAsBoolean)
            .build()
        );
        attack = !extras ? null : sgPearl.add(new BoolSetting.Builder()
            .name("attack")
            .description("Attacks entities in the way of the pearl phase.")
            .defaultValue(false)
            .visible(visible::getAsBoolean)
            .build()
        );
        swing = sgPearl.add(new BoolSetting.Builder()
            .name("swing")
            .description("Swings the hand when throwing pearls.")
            .defaultValue(true)
            .visible(visible::getAsBoolean)
            .build()
        );
        antiAntiPhase = sgPearl.add(new BoolSetting.Builder()
            .name("anti-anti-phase")
            .description("Breaks scaffolding placed inside you, in the same tick as the throw.")
            .defaultValue(true)
            .visible(visible::getAsBoolean)
            .build()
        );
        selfFill = !extras ? null : sgPearl.add(new BoolSetting.Builder()
            .name("self-fill")
            .description("Automatically fills blocks you are phasing on.")
            .defaultValue(false)
            .visible(visible::getAsBoolean)
            .build()
        );

        SettingGroup sgSelfPlace = settings.createGroup("Self Place");

        selfPlace = sgSelfPlace.add(new BoolSetting.Builder()
            .name("self-place")
            .description("Places fire or a cobweb at your position when phase is triggered.")
            .defaultValue(false)
            .visible(visible::getAsBoolean)
            .build()
        );
        selfPlaceType = sgSelfPlace.add(new EnumSetting.Builder<SelfPlaceType>()
            .name("type")
            .description("What to self-place: fire below you, or a cobweb at your feet.")
            .defaultValue(SelfPlaceType.Fire)
            .visible(() -> visible.getAsBoolean() && selfPlace.get())
            .build()
        );
        optimize = sgSelfPlace.add(new BoolSetting.Builder()
            .name("optimize")
            .description("Reuses the pearl throw rotation for self-place, instead of rotating twice.")
            .defaultValue(false)
            .visible(() -> visible.getAsBoolean() && selfPlace.get())
            .build()
        );
        selfPlaceRotate = sgSelfPlace.add(new BoolSetting.Builder()
            .name("rotate")
            .description("Silently rotates for the self-place action.")
            .defaultValue(true)
            .visible(() -> visible.getAsBoolean() && selfPlace.get() && !optimize.get())
            .build()
        );
    }

    /** True when a pearl is available and off cooldown. */
    public boolean canThrow() {
        return PlacementUtils.getEnderPearlSlot() != -1
            && !mc.player.getItemCooldownManager().isCoolingDown(Items.ENDER_PEARL.getDefaultStack());
    }

    /** Straight down into your own block, at the configured pitch. */
    public void throwPearl() {
        throwPearl(null);
    }

    /**
     * {@code aim} non-null aims the throw at that exact world point instead — yaw <i>and</i> pitch are
     * solved from the real eye position, so any sub-block offset is accounted for and the {@code pitch}
     * setting doesn't apply.
     */
    public void throwPearl(Vec3d aim) {
        int pearlSlot = PlacementUtils.getEnderPearlSlot();
        if (pearlSlot == -1 || mc.player.getItemCooldownManager().isCoolingDown(Items.ENDER_PEARL.getDefaultStack())) {
            return;
        }
        float yaw, throwPitch;
        if (aim != null) {
            // Solve from where the pearl actually spawns (ThrownItemEntity: player x/z, eyeY - 0.1), not
            // from the eye. Gravity is 0.03/tick against a 1.5 b/tick throw, so over the ~1 block this
            // covers the trajectory is a straight line and no ballistic solve is needed.
            float[] rotations = RotationUtils.getRotationsTo(mc.player.getEyePos().subtract(0.0, 0.1, 0.0), aim);
            yaw = rotations[0];
            // Yaw stays solved either way — a fixed yaw would just miss the block sideways.
            throwPitch = autoPitch.get() ? rotations[1] : pitch.get();
        } else {
            Vec3d pearlTargetVec = new Vec3d(Math.floor(mc.player.getX()) + 0.5, 0.0, Math.floor(mc.player.getZ()) + 0.5);
            yaw = RotationUtils.getRotationsTo(mc.player.getEyePos(), pearlTargetVec)[0] + 180.0f;
            throwPitch = pitch.get();
        }

        if (antiAntiPhase.get()) {
            breakScaffolding();
        }
        if (on(attack)) {
            handlePearlAttacks(yaw);
        }
        if (on(selfFill)) {
            handleSelfFill(yaw);
        }
        // Non-optimized: self-place has its own rotation and runs before the pearl swap.
        if (selfPlace.get() && !optimize.get()) {
            performSelfPlace(false);
        }

        RotationUtils rotationManager = RotationUtils.getInstance();
        int targetSlot;
        if (swapAlternative.get()) {
            targetSlot = ((PlayerInventoryAccessor) mc.player.getInventory()).getSelectedSlot();
            performInventorySwapPVP(pearlSlot);
        } else if (pearlSlot < 9) {
            targetSlot = pearlSlot;
        } else {
            return;
        }

        inventoryManager.setSlot(targetSlot, InventoryManager.Priority.PEARL_PHASE);
        rotationManager.setRotationSilent(yaw, throwPitch);
        // Optimized: pearl is swapped in and rotation is set; self-place reuses that rotation.
        if (selfPlace.get() && optimize.get()) {
            performSelfPlace(true);
        }
        mc.getNetworkHandler().sendPacket(new PlayerInteractItemC2SPacket(Hand.MAIN_HAND, 0, yaw, throwPitch));

        if (swing.get()) {
            mc.player.swingHand(Hand.MAIN_HAND);
        } else {
            mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
        }

        if (swapAlternative.get()) {
            performInventorySwapPVP(pearlSlot);
        }

        inventoryManager.syncToClient();
        rotationManager.setRotationSilentSync();
    }

    /** A setting left out by {@code extras = false} is simply off. */
    private static boolean on(Setting<Boolean> setting) {
        return setting != null && setting.get();
    }

    private void handlePearlAttacks(float yaw) {
        BlockHitResult hitResult = (BlockHitResult) mc.player.raycast(3.0, 0, false);
        Box searchBox = Box.from(Vec3d.ofCenter(hitResult.getBlockPos())).expand(0.2);
        for (Entity entity : mc.world.getOtherEntities(null, searchBox)) {
            if (entity instanceof ItemFrameEntity itemFrame) {
                mc.getNetworkHandler().sendPacket(PlayerInteractEntityC2SPacket.attack(entity, mc.player.isSneaking()));
                mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
            }
        }
    }

    /** Scaffolding is 0-hardness, so a START+STOP pair breaks it instantly and the pearl still leaves this tick. */
    private void breakScaffolding() {
        BlockPos feet = mc.player.getBlockPos();
        for (BlockPos pos : new BlockPos[]{feet, feet.up()}) {
            if (!(mc.world.getBlockState(pos).getBlock() instanceof ScaffoldingBlock)) continue;
            mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, pos, Direction.UP));
            mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, pos, Direction.UP));
            mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
        }
    }

    private void handleSelfFill(float yaw) {
        float yaw1 = yaw % 360.0f;
        if (yaw1 < 0.0f) {
            yaw1 += 360.0f;
        }
        BlockPos blockPos = mc.player.getBlockPos();
        if (yaw1 >= 22.5 && yaw1 < 67.5) {
            blockPos = blockPos.south().west();
        } else if (yaw1 >= 67.5 && yaw1 < 112.5) {
            blockPos = blockPos.west();
        } else if (yaw1 >= 112.5 && yaw1 < 157.5) {
            blockPos = blockPos.north().west();
        } else if (yaw1 >= 157.5 && yaw1 < 202.5) {
            blockPos = blockPos.north();
        } else if (yaw1 >= 202.5 && yaw1 < 247.5) {
            blockPos = blockPos.north().east();
        } else if (yaw1 >= 247.5 && yaw1 < 292.5) {
            blockPos = blockPos.east();
        } else if (yaw1 >= 292.5 && yaw1 < 337.5) {
            blockPos = blockPos.south().east();
        } else {
            blockPos = blockPos.south();
        }
        FindItemResult resistantBlock = PlacementUtils.findResistantBlock();
        if (resistantBlock.found() && blockPos != null && !mc.world.getBlockState(blockPos.down()).isReplaceable()) {
            PlacementUtils.placeBlock(blockPos, true, true, true);
        }
    }

    private int findSelfPlaceItem() {
        if (selfPlaceType.get() == SelfPlaceType.Fire) {
            for (int i = 0; i < 45; i++) {
                ItemStack stack = mc.player.getInventory().getStack(i);
                if (stack.getItem() == Items.FLINT_AND_STEEL || stack.getItem() == Items.FIRE_CHARGE) return i;
            }
        } else {
            for (int i = 0; i < 45; i++) {
                ItemStack stack = mc.player.getInventory().getStack(i);
                if (stack.getItem() == Items.COBWEB) return i;
            }
        }
        return -1;
    }

    private void performSelfPlace(boolean optimized) {
        int itemSlot = findSelfPlaceItem();
        if (itemSlot == -1) return;

        int targetSlot;
        if (swapAlternative.get()) {
            targetSlot = ((PlayerInventoryAccessor) mc.player.getInventory()).getSelectedSlot();
            performInventorySwapPVP(itemSlot);
        } else if (itemSlot < 9) {
            targetSlot = itemSlot;
        } else {
            return;
        }

        inventoryManager.setSlot(targetSlot, InventoryManager.Priority.PEARL_PHASE);
        RotationUtils rotationManager = RotationUtils.getInstance();

        if (selfPlaceType.get() == SelfPlaceType.Fire) {
            BlockPos belowPos = mc.player.getBlockPos().down();
            Vec3d hitVec = Vec3d.ofCenter(belowPos).add(0, 0.5, 0);
            if (!optimized && selfPlaceRotate.get()) {
                rotationManager.setRotationSilent(mc.player.getYaw(), 90.0f);
            }
            interact(new BlockHitResult(hitVec, Direction.UP, belowPos, false));
        } else {
            BlockPos feetPos = mc.player.getBlockPos();
            Direction side = PlacementUtils.getPlaceSide(feetPos);
            if (side == null) side = Direction.DOWN;
            BlockPos neighbor = feetPos.offset(side);
            Direction opposite = side.getOpposite();
            Vec3d hitVec = Vec3d.ofCenter(neighbor).add(Vec3d.of(opposite.getVector()).multiply(0.5));
            if (!optimized && selfPlaceRotate.get()) {
                float[] rot = RotationUtils.getRotationsTo(mc.player.getEyePos(), hitVec);
                rotationManager.setRotationSilent(rot[0], rot[1]);
            }
            interact(new BlockHitResult(hitVec, opposite, neighbor, false));
        }

        if (swapAlternative.get()) {
            performInventorySwapPVP(itemSlot);
        }

        // In the optimized path the pearl flow owns the final sync; don't duplicate it here.
        if (!optimized) {
            inventoryManager.syncToClient();
            if (selfPlaceRotate.get()) {
                rotationManager.setRotationSilentSync();
            }
        }
    }

    /**
     * Vanilla's own use-block path. The hand-rolled {@code PlayerInteractBlockC2SPacket} this replaces
     * always sent sequence 0, so the server acked a stale block-change sequence and simply dropped the
     * interaction — which is why self-place never lit the fire.
     */
    private void interact(BlockHitResult hitResult) {
        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hitResult);
        mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
    }

    private void performInventorySwapPVP(int pearlSlot) {
        mc.interactionManager.clickSlot(0, pearlSlot < 9 ? pearlSlot + 36 : pearlSlot, 0, SlotActionType.PICKUP, mc.player);
        mc.interactionManager.clickSlot(0, ((PlayerInventoryAccessor) mc.player.getInventory()).getSelectedSlot() + 36, 0, SlotActionType.PICKUP, mc.player);
        mc.interactionManager.clickSlot(0, pearlSlot < 9 ? pearlSlot + 36 : pearlSlot, 0, SlotActionType.PICKUP, mc.player);
    }

    public enum SelfPlaceType {
        Fire, Web
    }
}
