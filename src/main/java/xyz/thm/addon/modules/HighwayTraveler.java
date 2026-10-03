/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.modules;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.misc.HorizontalDirection;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.SlotUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.EndPortalBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import meteordevelopment.meteorclient.events.entity.player.PlayerMoveEvent;
import meteordevelopment.meteorclient.mixininterface.IVec3;
import xyz.thm.addon.THMAddon;
import xyz.thm.addon.utils.THMUtils;

import java.util.*;

/**
 * WARNING: The Wasp travel method utilizes rigid flight vectors to override standard Minecraft physics.
 * This poses a high risk of rubberbanding, desyncs, or triggering standard anarchy anti-cheats (like NCP or Grim)
 * when compared to the existing, more natural Elytra Bounce logic. Use Wasp mode with caution on strict servers.
 */
public class HighwayTraveler extends Module {

    public enum TravelMethod {
        WALK("Walk"),
        WASP("Wasp");

        public final String label;
        TravelMethod(String l) { label = l; }

        @Override
        public String toString() { return label; }
    }

    public enum TravelDirection {
        AUTO("Auto"),
        POS_X("East (+X)"),
        NEG_X("West (-X)"),
        POS_Z("South (+Z)"),
        NEG_Z("North (-Z)"),
        POS_X_POS_Z("SE (+X +Z)"),
        POS_X_NEG_Z("NE (+X -Z)"),
        NEG_X_POS_Z("SW (-X +Z)"),
        NEG_X_NEG_Z("NW (-X -Z)");

        public final String label;
        TravelDirection(String l) { label = l; }

        @Override
        public String toString() { return label; }
    }

    private enum TravelState { FORWARD, STOPPED, PATH_FOLLOW, BACKUP }
    private enum ObstacleKind { CLEAR, JUMPABLE, WALL }

    private final SettingGroup sgGeneral  = settings.getDefaultGroup();
    private final SettingGroup sgObstacle = settings.createGroup("Obstacle Avoidance");
    private final SettingGroup sgAutoRepair = settings.createGroup("Auto Repair Handoff");
    private final SettingGroup sgBounce = settings.createGroup("Elytra Bounce", false);

    private final Setting<TravelDirection> dirSetting = sgGeneral.add(
        new EnumSetting.Builder<TravelDirection>()
            .name("direction")
            .description("Highway direction. AUTO snaps to your current facing when the module is enabled.")
            .defaultValue(TravelDirection.AUTO)
            .build()
    );

    private final Setting<TravelMethod> methodSetting = sgGeneral.add(
        new EnumSetting.Builder<TravelMethod>()
            .name("method")
            .description("The travel method to use.")
            .defaultValue(TravelMethod.WALK)
            .build()
    );

    private final Setting<Double> waspHorizontalSpeed = sgGeneral.add(
        new DoubleSetting.Builder()
            .name("horizontal-speed")
            .description("Horizontal speed for Wasp mode.")
            .defaultValue(1.8)
            .min(0.1)
            .sliderMax(5.0)
            .visible(() -> methodSetting.get() == TravelMethod.WASP)
            .build()
    );

    private final Setting<Double> waspFallSpeed = sgGeneral.add(
        new DoubleSetting.Builder()
            .name("fall-speed")
            .description("Vertical fall speed for Wasp mode.")
            .defaultValue(0.01)
            .min(0.0)
            .sliderMax(0.5)
            .visible(() -> methodSetting.get() == TravelMethod.WASP)
            .build()
    );

    private final Setting<Boolean> sprintSetting = sgGeneral.add(
        new BoolSetting.Builder()
            .name("sprint")
            .description("Sprint continuously while traveling.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> autoJumpSetting = sgObstacle.add(
        new BoolSetting.Builder()
            .name("auto-jump")
            .description("Jump over 1-block-tall obstacles in the path.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> avoidSetting = sgObstacle.add(
        new BoolSetting.Builder()
            .name("pathfind")
            .description("BFS pathfind around walls and multi-block obstacles.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Integer> searchWidthSetting = sgObstacle.add(
        new IntSetting.Builder()
            .name("search-width")
            .description("Max lateral blocks to explore when routing around an obstacle.")
            .defaultValue(8)
            .min(2)
            .sliderMax(24)
            .build()
    );

    private final Setting<Integer> lookAheadSetting = sgObstacle.add(
        new IntSetting.Builder()
            .name("look-ahead")
            .description("How many blocks ahead to scan for obstacles.")
            .defaultValue(3)
            .min(1)
            .sliderMax(6)
            .build()
    );

    private final Setting<Boolean> autoRepairSwap = sgAutoRepair.add(
        new BoolSetting.Builder()
            .name("auto-repair-swap")
            .description("Hands control to Highway Builder when the highway ahead is broken.")
            .defaultValue(false)
            .build()
    );

    private final Setting<Integer> repairScanBlocks = sgAutoRepair.add(
        new IntSetting.Builder()
            .name("repair-scan")
            .description("How many blocks ahead to check for a broken highway.")
            .defaultValue(20)
            .min(5)
            .sliderMax(64)
            .visible(autoRepairSwap::get)
            .build()
    );

    private final Setting<Integer> repairCheckInterval = sgAutoRepair.add(
        new IntSetting.Builder()
            .name("repair-check-interval")
            .description("How often to re-scan the highway ahead for breaks.")
            .defaultValue(10)
            .min(1)
            .sliderMax(40)
            .visible(autoRepairSwap::get)
            .build()
    );

    private final Setting<Boolean> repairToleratesBounce = sgAutoRepair.add(
        new BoolSetting.Builder()
            .name("tolerate-bounce")
            .description("Ignores bounce altitude changes, so they aren't read as a broken highway.")
            .defaultValue(true)
            .visible(autoRepairSwap::get)
            .build()
    );

    private final Setting<Double> handoffLandingDelay = sgAutoRepair.add(
        new DoubleSetting.Builder()
            .name("landing-delay")
            .description("Seconds to hold still and land before handing over to Highway Builder.")
            .defaultValue(5)
            .min(0)
            .sliderMax(20)
            .visible(autoRepairSwap::get)
            .build()
    );

    private final Setting<Boolean> autoBounce = sgBounce.add(
        new BoolSetting.Builder()
            .name("auto-bounce")
            .description("Deploys and recasts your elytra like Elytra Fly's Bounce mode.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> bounceAutoJump = sgBounce.add(
        new BoolSetting.Builder()
            .name("auto-jump")
            .description("Automatically jumps to keep the elytra bounce going.")
            .defaultValue(true)
            .visible(autoBounce::get)
            .build()
    );

    private final Setting<Boolean> bounceLockPitch = sgBounce.add(
        new BoolSetting.Builder()
            .name("pitch-lock")
            .description("Whether to lock your pitch angle while bouncing.")
            .defaultValue(true)
            .visible(autoBounce::get)
            .build()
    );

    private final Setting<Double> bouncePitch = sgBounce.add(
        new DoubleSetting.Builder()
            .name("pitch")
            .description("The pitch angle to look at while bouncing.")
            .defaultValue(85)
            .range(0, 90)
            .sliderRange(0, 90)
            .visible(() -> autoBounce.get() && bounceLockPitch.get())
            .build()
    );

    private final Setting<Boolean> bounceRestart = sgBounce.add(
        new BoolSetting.Builder()
            .name("restart")
            .description("Unequips and re-equips the elytra to restart flight after a rubberband.")
            .defaultValue(true)
            .visible(autoBounce::get)
            .build()
    );

    private final Setting<Integer> bounceRestartDelay = sgBounce.add(
        new IntSetting.Builder()
            .name("restart-delay")
            .description("Ticks to keep the elytra off before re-equipping and taking off again.")
            .defaultValue(10)
            .min(0)
            .sliderRange(0, 20)
            .visible(() -> autoBounce.get() && bounceRestart.get())
            .build()
    );

    private final Setting<Boolean> bounceManualTakeoff = sgBounce.add(
        new BoolSetting.Builder()
            .name("manual-takeoff")
            .description("Does not automatically take off; you still have to jump to start gliding.")
            .defaultValue(false)
            .visible(autoBounce::get)
            .build()
    );

    private static final int OBSTACLE_WAIT_TICKS = 10;
    private static final int BOUNCE_Y_TOLERANCE = 2;
    private static final int CHEST_SLOT_INDEX = 38;

    private TravelState     travelState = TravelState.FORWARD;
    private TravelDirection activeDir   = TravelDirection.POS_Z;
    private float           hwYaw       = 0f;
    private int             fwdX        = 0;
    private int             fwdZ        = 1;

    private final Deque<BlockPos> path = new ArrayDeque<>();

    private int     backupTicks       = 0;
    private Vec3   prevPos           = Vec3.ZERO;
    private int     noMoveTicks       = 0;
    private int     obstacleWaitTicks = 0;
    private int     repairCheckTicks  = 0;
    private int     handoffTicks       = -1;
    private boolean bounceRubberbanded  = false;
    private int     bounceRestartTicks  = 0;
    private double  bouncePrevFov       = 0;
    private int     stashedElytraSlot   = -1;

    public HighwayTraveler() {
        super(THMAddon.MAIN, "highway-traveler",
            "Travels along anarchy highways with smart BFS obstacle avoidance.");
    }

    @Override
    public void onActivate() {
        if (mc.player == null) return;

        applyDirectionSetting();

        travelState = TravelState.FORWARD;
        path.clear();
        noMoveTicks        = 0;
        backupTicks        = 0;
        obstacleWaitTicks  = 0;
        repairCheckTicks   = 0;
        handoffTicks       = -1;
        bounceRubberbanded = false;
        bounceRestartTicks = bounceRestartDelay.get();
        stashedElytraSlot  = -1;
        bouncePrevFov      = mc.options.fovEffectScale().get();
        prevPos     = new Vec3(mc.player.getX(), mc.player.getY(), mc.player.getZ());

        if (autoBounce.get()) bounceRecast(mc.player);

        info("Heading " + activeDir.label + " (yaw " + (int) hwYaw + ")");
    }

    @Override
    public void onDeactivate() {
        releaseAll();
        mc.options.keyJump.setDown(false);
        bounceRubberbanded = false;
        handoffTicks = -1;
        // If the module gets toggled off mid unequip-recast, don't leave the elytra sitting
        // unworn in the player's inventory - put it straight back on.
        if (stashedElytraSlot != -1) reequipElytra();
        if (mc.player != null) {
            mc.player.setXRot(0);
            if (bouncePrevFov != 0 && !sprintSetting.get()) mc.options.fovEffectScale().set(bouncePrevFov);
        }
    }

    @Override
    public String getInfoString() {
        return activeDir != null ? activeDir.name() : "?";
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.level == null) return;
        LocalPlayer p = mc.player;

        if (handoffTicks >= 0) {
            tickAwaitingHandoff(p);
            return;
        }

        if (autoRepairSwap.get() && ++repairCheckTicks >= repairCheckInterval.get()) {
            repairCheckTicks = 0;
            if (highwayAheadBroken()) {
                beginHandoffWait(p);
                return;
            }
        }

        if (travelState != TravelState.PATH_FOLLOW) {
            p.setYRot(hwYaw);
        }

        Vec3 pos = new Vec3(p.getX(), p.getY(), p.getZ());
        noMoveTicks = pos.subtract(prevPos).lengthSqr() < 1e-4 ? noMoveTicks + 1 : 0;
        prevPos     = pos;

        if (noMoveTicks > 25 && travelState != TravelState.BACKUP && travelState != TravelState.STOPPED) {
            beginBackup();
            return;
        }

        switch (travelState) {
            case FORWARD     -> tickForward(p);
            case STOPPED     -> tickStopped(p);
            case PATH_FOLLOW -> tickPathFollow(p);
            case BACKUP      -> tickBackup(p);
        }

        if (sprintSetting.get() && travelState != TravelState.BACKUP && travelState != TravelState.STOPPED) {
            p.setSprinting(true);
        }

        // Bounce owns pitch (needs to dive to build speed) while it's active; otherwise keep it level.
        if (!autoBounce.get()) p.setXRot(0);
    }

    /**
     * Auto elytra bounce, ported from Elytra Fly's Bounce mode (same recast-on-rubberband
     * mechanics) so traveling doesn't depend on that module being separately active. Traveler
     * already owns yaw/forward-key steering itself, so (unlike Elytra Fly's Bounce) this only
     * drives takeoff, pitch and the rubberband recast — not direction or movement keys.
     */
    @EventHandler
    private void onBouncePostTick(TickEvent.Post event) {
        if (!autoBounce.get() || mc.player == null || handoffTicks >= 0) return;
        LocalPlayer p = mc.player;

        if (mc.options.keyJump.isDown() && !p.isFallFlying() && !bounceManualTakeoff.get()) {
            mc.getConnection().send(new ServerboundPlayerCommandPacket(p, ServerboundPlayerCommandPacket.Action.START_FALL_FLYING));
        }

        if (!bounceConditionsMet(p)) return;

        if (!bounceRubberbanded) {
            if (bouncePrevFov != 0 && !sprintSetting.get()) mc.options.fovEffectScale().set(0.0);
            if (bounceAutoJump.get()) mc.options.keyJump.setDown(true);
            if (bounceLockPitch.get()) p.setXRot(bouncePitch.get().floatValue());
        }

        if (!sprintSetting.get()) {
            if (p.isFallFlying()) p.setSprinting(p.onGround());
            else p.setSprinting(true);
        }

        if (bounceRubberbanded && bounceRestart.get()) {
            if (bounceRestartTicks > 0) {
                bounceRestartTicks--;
            } else {
                reequipElytra();
                mc.getConnection().send(new ServerboundPlayerCommandPacket(p, ServerboundPlayerCommandPacket.Action.START_FALL_FLYING));
                bounceRubberbanded = false;
                bounceRestartTicks = bounceRestartDelay.get();
            }
        }
    }

    @EventHandler
    private void onBouncePreTick(TickEvent.Pre event) {
        if (!autoBounce.get() || mc.player == null || handoffTicks >= 0) return;
        if (bounceConditionsMet(mc.player) && sprintSetting.get()) mc.player.setSprinting(true);
    }

    @EventHandler
    private void onBouncePacketReceive(PacketEvent.Receive event) {
        if (!autoBounce.get() || mc.player == null || handoffTicks >= 0) return;
        if (event.packet instanceof ClientboundPlayerPositionPacket) {
            bounceRubberbanded = true;
            mc.player.stopFallFlying();
            // A plain stopGliding() call is client-side only and sometimes doesn't actually clear
            // the server's flying state, leaving the bounce stuck mid-air unable to restart.
            // Physically unequipping the elytra forces a real stop; re-equipping (see
            // onBouncePostTick's restart block) after restart-delay ticks is guaranteed clean.
            if (bounceRestart.get()) forceUnequipElytra();
        }
    }

    @EventHandler
    private void onBouncePacketSend(PacketEvent.Send event) {
        if (!autoBounce.get() || mc.player == null || handoffTicks >= 0 || sprintSetting.get()) return;
        if (event.packet instanceof ServerboundPlayerCommandPacket cmd && cmd.getAction() == ServerboundPlayerCommandPacket.Action.START_FALL_FLYING) {
            mc.player.setSprinting(true);
        }
    }

    /**
     * Handles PlayerMoveEvent to apply strict Wasp vector calculations.
     *
     * @param event The player move event.
     */
    @EventHandler
    private void onPlayerMove(PlayerMoveEvent event) {
        if (mc.player == null || methodSetting.get() != TravelMethod.WASP || !mc.player.isFallFlying()) return;
        if (travelState == TravelState.STOPPED || travelState == TravelState.BACKUP || handoffTicks >= 0) return;

        float yaw = travelState == TravelState.PATH_FOLLOW ? mc.player.getYRot() : hwYaw;

        double cos = Math.cos(Math.toRadians(yaw + 90));
        double sin = Math.sin(Math.toRadians(yaw + 90));

        double speed = waspHorizontalSpeed.get();
        double x = cos * speed;
        double z = sin * speed;
        double y = -waspFallSpeed.get();

        ((IVec3) event.movement).meteor$set(x, y, z);
    }

    private boolean bounceConditionsMet(LocalPlayer p) {
        BlockState blockState = p.getInBlockState();
        boolean isClimbing = blockState.is(BlockTags.CLIMBABLE) && !blockState.is(BlockTags.CAN_GLIDE_THROUGH);
        return !p.getAbilities().flying && !p.isPassenger() && !isClimbing && !p.isInWater() && !p.hasEffect(MobEffects.LEVITATION);
    }

    private boolean bounceStartGliding(LocalPlayer p) {
        for (EquipmentSlot slot : EquipmentSlot.VALUES) {
            if (LivingEntity.canGlideUsing(p.getItemBySlot(slot), slot)) {
                mc.executeIfPossible(p::startFallFlying);
                return true;
            }
        }
        return false;
    }

    private void bounceRecast(LocalPlayer p) {
        if (bounceConditionsMet(p) && bounceStartGliding(p)) {
            mc.getConnection().send(new ServerboundPlayerCommandPacket(p, ServerboundPlayerCommandPacket.Action.START_FALL_FLYING));
        }
    }

    /**
     * Stashes the worn elytra into a free inventory slot, even mid-air - a real unequip forces the
     * server to actually stop flight, unlike a client-side {@code stopGliding()} call that can leave
     * server/client flying state desynced. {@link #reequipElytra()} puts it back once
     * {@code bounceRestartTicks} elapses. No-op if there's no elytra worn, or one is already stashed.
     */
    private boolean forceUnequipElytra() {
        if (mc.player == null || stashedElytraSlot != -1) return false;

        ItemStack chest = mc.player.getItemBySlot(EquipmentSlot.CHEST);
        if (chest.isEmpty() || !LivingEntity.canGlideUsing(chest, EquipmentSlot.CHEST)) return false;

        // Bounded to hotbar+main (0-35) - InvUtils.findEmpty() searches the whole PlayerInventory,
        // which also covers armor/offhand (36-40), and an empty leg/foot/head/offhand slot won't
        // accept an elytra (the vanilla slot predicate rejects it), silently stranding it on cursor.
        int emptySlot = InvUtils.find(ItemStack::isEmpty, 0, 35).slot();
        if (emptySlot == -1) return false;

        THMUtils.fakeInventoryOpen(true);
        try {
            int syncId = mc.player.containerMenu.containerId;
            int chestId = SlotUtils.indexToId(CHEST_SLOT_INDEX);
            int spareId = SlotUtils.indexToId(emptySlot);
            mc.gameMode.handleContainerInput(syncId, chestId, 0, ContainerInput.PICKUP, mc.player);
            mc.gameMode.handleContainerInput(syncId, spareId, 0, ContainerInput.PICKUP, mc.player);
        } finally {
            THMUtils.fakeInventoryOpen(false);
        }

        stashedElytraSlot = emptySlot;
        return true;
    }

    private void reequipElytra() {
        if (mc.player == null || stashedElytraSlot == -1) return;

        THMUtils.fakeInventoryOpen(true);
        try {
            int syncId = mc.player.containerMenu.containerId;
            int spareId = SlotUtils.indexToId(stashedElytraSlot);
            int chestId = SlotUtils.indexToId(CHEST_SLOT_INDEX);
            mc.gameMode.handleContainerInput(syncId, spareId, 0, ContainerInput.PICKUP, mc.player);
            mc.gameMode.handleContainerInput(syncId, chestId, 0, ContainerInput.PICKUP, mc.player);
        } finally {
            THMUtils.fakeInventoryOpen(false);
        }

        stashedElytraSlot = -1;
    }

    private void tickForward(LocalPlayer p) {
        switch (detectObstacle(p)) {
            case CLEAR    -> pressFwd();
            case JUMPABLE -> {
                pressFwd();
                if (autoJumpSetting.get() && p.onGround()) p.jumpFromGround();
            }
            case WALL -> {
                if (!avoidSetting.get()) {
                    pressFwd();
                    return;
                }
                releaseAll();
                p.setSprinting(false);
                obstacleWaitTicks = 0;
                travelState = TravelState.STOPPED;
            }
        }
    }

    private void tickStopped(LocalPlayer p) {
        releaseAll();
        p.setSprinting(false);

        if (++obstacleWaitTicks < OBSTACLE_WAIT_TICKS) return;

        obstacleWaitTicks = 0;

        Deque<BlockPos> route = bfsRoute(p);
        if (route != null && !route.isEmpty()) {
            path.clear();
            path.addAll(route);
            travelState = TravelState.PATH_FOLLOW;
        } else {
            beginBackup();
        }
    }

    private static final int STEER_LOOKAHEAD = 3;

    private void tickPathFollow(LocalPlayer p) {
        Vec3 pos = new Vec3(p.getX(), p.getY(), p.getZ());

        while (!path.isEmpty()) {
            BlockPos wp = path.peek();
            double   dx = (wp.getX() + 0.5) - pos.x;
            double   dz = (wp.getZ() + 0.5) - pos.z;
            if (dx * dx + dz * dz < 0.49) path.poll();
            else break;
        }

        if (path.isEmpty()) {
            p.setYRot(hwYaw);
            travelState = TravelState.FORWARD;
            return;
        }


        BlockPos aim = null;
        int      i   = 0;
        for (BlockPos wp : path) {
            aim = wp;
            if (++i >= STEER_LOOKAHEAD) break;
        }

        double dx = (aim.getX() + 0.5) - pos.x;
        double dz = (aim.getZ() + 0.5) - pos.z;
        p.setYRot((float) Math.toDegrees(Math.atan2(-dx, dz)));

        BlockPos nextWp = path.peek();
        if (nextWp.getY() > p.blockPosition().getY() && p.onGround()) p.jumpFromGround();

        pressFwd();
    }

    private void beginBackup() {
        travelState = TravelState.BACKUP;
        backupTicks = 0;
        path.clear();
        noMoveTicks = 0;
    }

    private void tickBackup(LocalPlayer p) {
        releaseAll();
        mc.options.keyDown.setDown(true);
        if (++backupTicks >= 30) {
            mc.options.keyDown.setDown(false);
            travelState = TravelState.FORWARD;
        }
    }

    private boolean highwayAheadBroken() {
        HighwayBuilderTHM builder = Modules.get().get(HighwayBuilderTHM.class);
        if (builder == null) return false;

        HorizontalDirection travelDir = HorizontalDirection.get(hwYaw);
        int tolerance = repairToleratesBounce.get() ? BOUNCE_Y_TOLERANCE : 0;
        return !builder.isHighwayAheadClean(travelDir, repairScanBlocks.get(), tolerance);
    }

    /**
     * Broken highway detected — stop moving/bouncing and start the landing-delay countdown
     * instead of handing off immediately, so the player actually lands on the highway before
     * Highway Builder starts placing at whatever mid-air/bounce height it was interrupted at.
     */
    private void beginHandoffWait(LocalPlayer p) {
        releaseAll();
        mc.options.keyJump.setDown(false);
        p.setSprinting(false);
        p.setXRot(0);
        // Don't leave a mid-recast elytra stashed away for the whole landing-delay wait (the bounce
        // handlers that would normally resolve it are suppressed once handoffTicks >= 0) - put it
        // back on right away so the player isn't stuck bare-chested falling with no glide control.
        if (stashedElytraSlot != -1) reequipElytra();
        handoffTicks = (int) Math.round(handoffLandingDelay.get() * 20);
    }

    private void tickAwaitingHandoff(LocalPlayer p) {
        releaseAll();
        mc.options.keyJump.setDown(false);
        p.setSprinting(false);

        if (--handoffTicks > 0) return;
        handoffTicks = -1;

        if (isActive()) toggle();

        // Builder fully deactivates itself for the repair->travel handoff (so it doesn't keep
        // holding player input/movement while "paused" - see HighwayBuilderTHM's
        // travelHandoffDeactivateArmed), so it should always be inactive here. Reactivating it
        // resumes its stats session from the cached snapshot rather than starting fresh.
        HighwayBuilderTHM builder = Modules.get().get(HighwayBuilderTHM.class);
        if (builder != null && !builder.isActive()) builder.toggle();
    }

    private ObstacleKind detectObstacle(LocalPlayer p) {
        BlockPos feet  = p.blockPosition();
        int      steps = lookAheadSetting.get();

        for (int step = 1; step <= steps; step++) {
            BlockPos diag = new BlockPos(feet.getX() + fwdX * step, feet.getY(), feet.getZ() + fwdZ * step);

            boolean blocked;
            if (fwdX != 0 && fwdZ != 0) {

                BlockPos cornerX = new BlockPos(feet.getX() + fwdX * step, feet.getY(), feet.getZ());
                BlockPos cornerZ = new BlockPos(feet.getX(), feet.getY(), feet.getZ() + fwdZ * step);
                blocked = solid(diag) || (solid(cornerX) && solid(cornerZ));
            } else {
                blocked = solid(diag);
            }

            if (!blocked) continue;

            boolean headBlocked  = solid(diag.above());
            boolean aboveBlocked = solid(diag.above(2));

            if (step == 1) {
                if (!headBlocked && !aboveBlocked) return ObstacleKind.JUMPABLE;
                return ObstacleKind.WALL;
            }
            if (headBlocked || aboveBlocked) return ObstacleKind.WALL;
        }
        return ObstacleKind.CLEAR;
    }

    private static final int[][] DIRS4 = {{1,0},{-1,0},{0,1},{0,-1}};

    private Deque<BlockPos> bfsRoute(LocalPlayer p) {
        int sx = p.blockPosition().getX();
        int sy = p.blockPosition().getY();
        int sz = p.blockPosition().getZ();
        int W  = searchWidthSetting.get();

        Map<Long, Long>    cameFrom = new LinkedHashMap<>();
        Map<Long, Integer> yAt      = new HashMap<>();

        long startKey = xzKey(sx, sz);
        cameFrom.put(startKey, -1L);
        yAt.put(startKey, sy);

        Queue<long[]> queue  = new ArrayDeque<>();
        queue.add(new long[]{sx, sy, sz});

        long   goalKey = Long.MIN_VALUE;
        int    iter    = 0;
        int    maxIter = W * W * 8;
        double dirLen = Math.sqrt((double)(fwdX * fwdX + fwdZ * fwdZ));

        while (!queue.isEmpty() && iter++ < maxIter) {
            long[] cur = queue.poll();
            int cx = (int) cur[0], cy = (int) cur[1], cz = (int) cur[2];

            double fwd = ((cx - sx) * fwdX + (cz - sz) * fwdZ) / dirLen;
            double lat = ((cx - sx) * fwdZ - (cz - sz) * fwdX) / dirLen;

            if (fwd >= 4 && Math.abs(lat) <= 1) {
                goalKey = xzKey(cx, cz);
                break;
            }
            if (Math.abs(lat) > W) continue;

            for (int[] d : DIRS4) {
                int nx = cx + d[0], nz = cz + d[1];
                long key = xzKey(nx, nz);
                if (cameFrom.containsKey(key)) continue;

                Integer ny = walkableYNear(nx, cy, nz, 3);
                if (ny == null) continue;

                cameFrom.put(key, xzKey(cx, cz));
                yAt.put(key, ny);
                queue.add(new long[]{nx, ny, nz});
            }
        }

        if (goalKey == Long.MIN_VALUE) return null;

        Deque<BlockPos> result = new ArrayDeque<>();
        long key = goalKey;
        while (key != -1L) {
            int x = decodeX(key);
            int z = decodeZ(key);
            int y = yAt.getOrDefault(key, sy);
            result.addFirst(new BlockPos(x, y, z));

            Long parent = cameFrom.get(key);
            if (parent == null || parent == -1L) break;
            key = parent;
        }

        if (!result.isEmpty()) result.pollFirst();

        return result;
    }

    private Integer walkableYNear(int x, int nearY, int z, int range) {
        for (int dy = 0; dy <= range; dy++) {
            if (dy > 0 && canStand(x, nearY + dy, z)) return nearY + dy;
            if (canStand(x, nearY - dy, z))            return nearY - dy;
        }
        return null;
    }

    private boolean canStand(int x, int y, int z) {
        BlockPos feet = new BlockPos(x, y, z);
        return solid(feet.below()) && passable(feet) && passable(feet.above());
    }

    private boolean solid(BlockPos pos) {
        if (mc.level == null) return false;
        var state = mc.level.getBlockState(pos);

        if (state.isAir()) return false;
        if (state.getBlock() instanceof LiquidBlock) return false;
        if (!state.getFluidState().isEmpty()) return false;
        if (state.getBlock() instanceof NetherPortalBlock) return false;
        if (state.getBlock() instanceof EndPortalBlock) return false;

        return !state.getCollisionShape(mc.level, pos).isEmpty();
    }

    private boolean passable(BlockPos pos) { return !solid(pos); }

    private static long xzKey(int x, int z) {
        return ((long)(x + 30_000_000) << 32) | ((z + 30_000_000) & 0xFFFFFFFFL);
    }

    private static int decodeX(long key) { return (int)(key >> 32) - 30_000_000; }
    private static int decodeZ(long key) { return (int)(key & 0xFFFFFFFFL) - 30_000_000; }

    private void pressFwd() {
        mc.options.keyUp.setDown(true);
        mc.options.keyDown.setDown(false);
        mc.options.keyLeft.setDown(false);
        mc.options.keyRight.setDown(false);
    }

    private void releaseAll() {
        mc.options.keyUp.setDown(false);
        mc.options.keyDown.setDown(false);
        mc.options.keyLeft.setDown(false);
        mc.options.keyRight.setDown(false);
    }

    private void applyDirectionSetting() {
        TravelDirection d = dirSetting.get();
        if (d == TravelDirection.AUTO) {
            d = snapYawToHighway(mc.player.getYRot());
        }
        activeDir = d;
        hwYaw     = yawOf(d);
        int[] s   = stepOf(d);
        fwdX      = s[0];
        fwdZ      = s[1];
    }

    private TravelDirection snapYawToHighway(float rawYaw) {
        float yaw = ((rawYaw % 360f) + 360f) % 360f;
        TravelDirection best     = TravelDirection.POS_Z;
        float           bestDiff = Float.MAX_VALUE;

        for (TravelDirection d : TravelDirection.values()) {
            if (d == TravelDirection.AUTO) continue;
            float dy   = ((yawOf(d) % 360f) + 360f) % 360f;
            float diff = Math.abs(yaw - dy);
            if (diff > 180f) diff = 360f - diff;
            if (diff < bestDiff) { bestDiff = diff; best = d; }
        }
        return best;
    }

    private static float yawOf(TravelDirection d) {
        return switch (d) {
            case POS_Z       ->    0f;
            case NEG_X       ->   90f;
            case NEG_Z       ->  180f;
            case POS_X       ->  -90f;
            case NEG_X_POS_Z ->   45f;
            case POS_X_POS_Z ->  -45f;
            case NEG_X_NEG_Z ->  135f;
            case POS_X_NEG_Z -> -135f;
            default          ->    0f;
        };
    }

    private static int[] stepOf(TravelDirection d) {
        return switch (d) {
            case POS_X       -> new int[]{ 1,  0};
            case NEG_X       -> new int[]{-1,  0};
            case POS_Z       -> new int[]{ 0,  1};
            case NEG_Z       -> new int[]{ 0, -1};
            case POS_X_POS_Z -> new int[]{ 1,  1};
            case POS_X_NEG_Z -> new int[]{ 1, -1};
            case NEG_X_POS_Z -> new int[]{-1,  1};
            case NEG_X_NEG_Z -> new int[]{-1, -1};
            default          -> new int[]{ 0,  1};
        };
    }
}
