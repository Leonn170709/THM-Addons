/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.modules;

import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.BundlePacket;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.*;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import xyz.thm.addon.THMAddon;
import xyz.thm.addon.mixin.accessor.ClientLevelPredictionAccessor;
import xyz.thm.addon.mixin.accessor.ClientPlayerInteractionManagerTHMAccessor;
import xyz.thm.addon.mixin.accessor.PlayerInventoryAccessor;
import xyz.thm.addon.settings.DescribedOption;
import xyz.thm.addon.utils.InventoryManager;
import xyz.thm.addon.utils.PacketPlaceTracker;
import xyz.thm.addon.utils.PlacementUtils;
import xyz.thm.addon.utils.RenderUtilsTHM;

import java.util.*;

public class Surround extends Module {
    public static Surround INSTANCE;
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgPlace = settings.createGroup("Place Logic");
    private final SettingGroup sgTiming = settings.createGroup("Timing");
    private final SettingGroup sgCenter = settings.createGroup("Center Logic");
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<List<Block>> blocks = sgGeneral.add(new BlockListSetting.Builder()
        .name("blocks")
        .description("Blocks to use for surrounding.")
        .defaultValue(Blocks.OBSIDIAN, Blocks.CRYING_OBSIDIAN, Blocks.NETHERITE_BLOCK)
        .build()
    );

    private final Setting<Boolean> packet = sgPlace.add(new BoolSetting.Builder()
        .name("packet")
        .description("Place without client prediction.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> packetOnce = sgPlace.add(new BoolSetting.Builder()
        .name("packet-place-once")
        .description("Wait for a server answer before resending.")
        .defaultValue(true)
        .visible(packet::get)
        .build()
    );

    private final Setting<Boolean> tagSwitch = sgGeneral.add(new BoolSetting.Builder()
        .name("tag-switch")
        .description("Disable once the surround is confirmed.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> delay = sgPlace.add(new IntSetting.Builder()
        .name("place-delay")
        .description("Tick delay between block placements.")
        .defaultValue(0)
        .min(0)
        .sliderMax(10)
        .build()
    );

    private final Setting<Integer> blocksPerTick = sgPlace.add(new IntSetting.Builder()
        .name("blocks-per-tick")
        .description("Maximum blocks to place per tick.")
        .defaultValue(4)
        .min(1)
        .sliderMax(8)
        .build()
    );

    private final Setting<Boolean> rotate = sgPlace.add(new BoolSetting.Builder()
        .name("rotate")
        .description("Rotate toward each placement.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> extend = sgPlace.add(new BoolSetting.Builder()
        .name("extend")
        .description("Encases your feet even when standing on the edge of blocks.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> strict = sgPlace.add(new BoolSetting.Builder()
        .name("strict-directions")
        .description("Require a visible support face.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> airplace = sgPlace.add(new BoolSetting.Builder()
        .name("airplace")
        .description("Place without an adjacent block.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> support = sgPlace.add(new BoolSetting.Builder()
        .name("support")
        .description("Places a block under your feet if open air.")
        .defaultValue(true)
        .build()
    );
    private final Setting<Boolean> attackCrystals = sgPlace.add(new BoolSetting.Builder()
        .name("attack-crystals")
        .description("Attacks crystals in the way before placing.")
        .defaultValue(true)
        .build()
    );
    private final Setting<Boolean> desyncProtection = sgPlace.add(new BoolSetting.Builder()
        .name("desync-protection")
        .description("Clear unconfirmed blocks intersecting new crystals.")
        .defaultValue(true)
        .build()
    );
    private final Setting<Boolean> headLevel = sgPlace.add(new BoolSetting.Builder()
        .name("head-level")
        .description("Add blocks at eye level.")
        .defaultValue(false)
        .build()
    );
    private final Setting<Boolean> coverHead = sgPlace.add(new BoolSetting.Builder()
        .name("cover-head")
        .description("Add a roof above your head.")
        .defaultValue(false)
        .build()
    );
    private final Setting<Boolean> mineExtend = sgPlace.add(new BoolSetting.Builder()
        .name("mine-extend")
        .description("Extends surround outward when a surround block is being mined.")
        .defaultValue(false)
        .build()
    );
    private final Setting<Boolean> multitask = sgPlace.add(new BoolSetting.Builder()
        .name("multitask")
        .description("Allows placing while using items.")
        .defaultValue(false)
        .build()
    );

    private final Setting<ReplaceTrigger> replaceTrigger = sgTiming.add(new EnumSetting.Builder<ReplaceTrigger>()
        .name("replace-trigger")
        .description("When to replace broken surround blocks.")
        .defaultValue(ReplaceTrigger.onPacket)
        .build()
    );
    private final Setting<Boolean> prePlaceExplosion = sgTiming.add(new BoolSetting.Builder()
        .name("pre-place-explosion")
        .description("Retry missing blocks on explosions.")
        .defaultValue(true)
        .visible(() -> replaceTrigger.get() == ReplaceTrigger.onPacket)
        .build()
    );
    private final Setting<Boolean> prePlaceCrystalSpawn = sgTiming.add(new BoolSetting.Builder()
        .name("pre-place-crystal-spawn")
        .description("Retry missing blocks on crystal spawns.")
        .defaultValue(true)
        .visible(() -> replaceTrigger.get() == ReplaceTrigger.onPacket)
        .build()
    );
    private final Setting<Double> shiftDelay = sgTiming.add(new DoubleSetting.Builder()
        .name("shift-delay")
        .description("Ticks between retries at the same position.")
        .defaultValue(1.0)
        .min(0.0)
        .sliderMax(5.0)
        .build()
    );

    private final Setting<Boolean> onlyOnGround = sgPlace.add(new BoolSetting.Builder()
        .name("only-on-ground")
        .description("Only place while on the ground.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> disableOnJump = sgPlace.add(new BoolSetting.Builder()
        .name("disable-on-jump")
        .description("Automatically disables the module if you jump.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> disableOnYChange = sgPlace.add(new BoolSetting.Builder()
        .name("disable-on-y-change")
        .description("Disables if your Y level changes.")
        .defaultValue(true)
        .build()
    );

    private final Setting<CenterMode> centerMode = sgCenter.add(new EnumSetting.Builder<CenterMode>()
        .name("center-mode")
        .description("Method used to center the player.")
        .defaultValue(CenterMode.NCP)
        .build()
    );

    private final Setting<Boolean> phased = sgCenter.add(new BoolSetting.Builder()
        .name("phased")
        .description("Skips centering while standing inside a solid client-side block.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> render = sgRender.add(new BoolSetting.Builder()
        .name("render")
        .description("Renders the block placements.")
        .defaultValue(true)
        .build()
    );

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .description("How the shapes are rendered.")
        .defaultValue(ShapeMode.Both)
        .visible(render::get)
        .build()
    );

    private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder()
        .name("side-color")
        .description("The side color.")
        .defaultValue(new SettingColor(THMAddon.THMSideColor.r, THMAddon.THMSideColor.g, THMAddon.THMSideColor.b, THMAddon.THMSideColor.a))
        .visible(render::get)
        .build()
    );

    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
        .name("line-color")
        .description("The line color.")
        .defaultValue(new SettingColor(THMAddon.THMColor.r, THMAddon.THMColor.g, THMAddon.THMColor.b, THMAddon.THMColor.a))
        .visible(render::get)
        .build()
    );

    private final Setting<Boolean> fade = sgRender.add(new BoolSetting.Builder()
        .name("fade")
        .description("Fades the rendered block over time.")
        .defaultValue(true)
        .visible(render::get)
        .build()
    );

    private final Setting<Double> fadeTime = sgRender.add(new DoubleSetting.Builder()
        .name("fade-time")
        .description("How long the fade lasts in seconds.")
        .defaultValue(0.5)
        .min(0.1)
        .sliderMax(2)
        .visible(() -> render.get() && fade.get())
        .build()
    );

    private final Setting<Boolean> inventorySwap = sgPlace.add(new BoolSetting.Builder()
        .name("inventory-swap")
        .description("Use blocks from your main inventory.")
        .defaultValue(false)
        .build()
    );
    private final Setting<Integer> confirmationTimeout = sgTiming.add(new IntSetting.Builder()
        .name("confirmation-timeout")
        .description("Ticks before clearing an unconfirmed placement.")
        .defaultValue(20)
        .min(1)
        .max(200)
        .sliderMax(100)
        .build()
    );

    private final Map<BlockPos, Placement> pending = new HashMap<>();
    private final Map<BlockPos, Long> lastAttempt = new HashMap<>();
    private final Map<BlockPos, Long> mining = new HashMap<>();
    private final Map<BlockPos, Long> renderMap = new HashMap<>();
    private final Set<BlockPos> serverSolid = new HashSet<>();
    private final PlacementBudget budget = new PlacementBudget();
    private Set<BlockPos> targets = Set.of();
    private ClientLevel world;
    private double initialY;
    private volatile int generation;
    private boolean attackedThisTick;

    public Surround() {
        super(THMAddon.PVP, "surround-plus", "Builds and maintains a server-confirmed surround.");
        INSTANCE = this;
    }

    @Override
    public void onActivate() {
        reset(false);
        world = mc.level;
        if (mc.player == null || world == null) return;
        initialY = mc.player.getY();
        if (centerMode.get() == CenterMode.Teleport) PlayerUtils.centerPlayer();
        rebuildTargets();
        for (BlockPos pos : targets) if (!world.getBlockState(pos).canBeReplaced()) serverSolid.add(pos);
    }

    @Override
    public void onDeactivate() {
        reset(true);
    }

    private void reset(boolean rollback) {
        generation++;
        if (rollback && world != null && world == mc.level) {
            pending.forEach(this::rollback);
        }
        pending.clear();
        lastAttempt.clear();
        mining.clear();
        renderMap.clear();
        serverSolid.clear();
        targets = Set.of();
        budget.reset();
        world = null;
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) { reset(false); }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (world != mc.level) {
            reset(false);
            world = mc.level;
            if (mc.player != null) initialY = mc.player.getY();
        }
        if (mc.player == null || world == null) return;
        budget.advanceTick();
        attackedThisTick = false;
        expirePlacements();
        long now = System.nanoTime();
        mining.entrySet().removeIf(entry -> now - entry.getValue() > 2_000_000_000L);
        renderMap.entrySet().removeIf(entry -> now - entry.getValue() > fadeTime.get() * 1_000_000_000L);
        if (shouldDisable()) { toggle(); return; }
        if (centerMode.get() == CenterMode.NCP) center();
        rebuildTargets();
        if (!canAct()) return;
        fill();
        if (tagSwitch.get() && !targets.isEmpty() && targets.stream().allMatch(pos ->
            !world.getBlockState(pos).canBeReplaced() && !pending.containsKey(pos))) toggle();
    }

    private boolean shouldDisable() {
        return (disableOnJump.get() && mc.options.keyJump.isDown())
            || (disableOnYChange.get() && Math.abs(mc.player.getY() - initialY) > 0.05);
    }

    private boolean canAct() {
        if (!isActive() || world == null || world != mc.level || mc.player == null || mc.gameMode == null
            || mc.getConnection() == null || shouldDisable() || (onlyOnGround.get() && !mc.player.onGround())) return false;
        int priority = InventoryManager.getInstance().getCurrentPriority();
        if (priority > InventoryManager.Priority.SURROUND) return false;
        return multitask.get() || (!mc.player.isUsingItem() && !InventoryManager.getInstance().isEating());
    }

    private void rebuildTargets() {
        Set<BlockPos> feet = footprint(mc.player.getBoundingBox(), mc.player.blockPosition(), extend.get());
        targets = plan(feet, headLevel.get(), coverHead.get(), airplace.get(), support.get());
        if (mineExtend.get()) {
            for (BlockPos pos : mining.keySet()) {
                if (!targets.contains(pos)) continue;
                for (Direction side : Direction.Plane.HORIZONTAL) {
                    BlockPos extra = pos.relative(side);
                    if (!feet.contains(extra) && !feet.contains(extra.below())) targets.add(extra);
                }
            }
        }
        lastAttempt.keySet().removeIf(pos -> !targets.contains(pos) && !pending.containsKey(pos));
        mining.keySet().removeIf(pos -> !targets.contains(pos));
        serverSolid.retainAll(targets);
    }

    static Set<BlockPos> footprint(AABB bounds, BlockPos base, boolean extend) {
        Set<BlockPos> feet = new LinkedHashSet<>();
        if (!extend) { feet.add(base); return feet; }
        for (int x = Mth.floor(bounds.minX + 1e-7); x <= Mth.floor(bounds.maxX - 1e-7); x++) {
            for (int z = Mth.floor(bounds.minZ + 1e-7); z <= Mth.floor(bounds.maxZ - 1e-7); z++) {
                feet.add(new BlockPos(x, base.getY(), z));
            }
        }
        return feet;
    }

    static Set<BlockPos> plan(Set<BlockPos> feet, boolean eye, boolean roof, boolean air, boolean floor) {
        Set<BlockPos> result = new LinkedHashSet<>();
        if (floor) for (BlockPos pos : feet) result.add(pos.below());
        Set<BlockPos> ring = new LinkedHashSet<>();
        for (BlockPos pos : feet) for (Direction side : Direction.Plane.HORIZONTAL) {
            BlockPos next = pos.relative(side);
            if (!feet.contains(next)) ring.add(next);
        }
        result.addAll(ring);
        if (eye) for (BlockPos pos : ring) result.add(pos.above());
        if (roof && !air && !ring.isEmpty()) {
            // Build outside the player, then attach the roof to the column.
            BlockPos column = ring.iterator().next();
            result.add(column.above());
            result.add(column.above(2));
        }
        if (roof) for (BlockPos pos : feet) result.add(pos.above(2));
        return result;
    }

    private void fill() {
        for (BlockPos pos : targets) tryPlace(pos);
    }

    private void tryPlace(BlockPos pos) {
        if (!canAct() || !targets.contains(pos) || pending.containsKey(pos)
            || !world.getBlockState(pos).canBeReplaced()) return;
        if (replaceTrigger.get() == ReplaceTrigger.onPacket && serverSolid.contains(pos)) return;
        if ((!packet.get() || packetOnce.get()) && !PacketPlaceTracker.canSend(pos)) return;
        long now = System.nanoTime();
        Long previous = lastAttempt.get(pos);
        if (previous != null && now - previous < shiftDelay.get() * 50_000_000L) return;
        int slot = findBlock();
        if (slot == -1) return;
        ItemStack stack = slot == 45 ? mc.player.getOffhandItem() : mc.player.getInventory().getItem(slot);
        Block block = ((BlockItem) stack.getItem()).getBlock();
        if (!canPlace(pos, block)) return;
        BlockHitResult hit = hit(pos);
        if (hit == null) {
            if (support.get() && targets.contains(pos.below())) tryPlace(pos.below());
            return;
        }
        if (!budget.reserve(blocksPerTick.get(), delay.get())) return;
        Placement placement = new Placement(world.getBlockState(pos), now);
        pending.put(pos, placement);
        int expectedGeneration = generation;
        Runnable action = () -> {
            if (generation != expectedGeneration || pending.get(pos) != placement) return;
            rebuildTargets();
            if (!canAct() || !targets.contains(pos)) {
                cancel(pos, placement);
                return;
            }
            int actualSlot = findBlock();
            if (actualSlot == -1) { cancel(pos, placement); return; }
            ItemStack actualStack = actualSlot == 45 ? mc.player.getOffhandItem() : mc.player.getInventory().getItem(actualSlot);
            BlockHitResult actualHit = hit(pos);
            if (actualHit == null || !canPlace(pos, ((BlockItem) actualStack.getItem()).getBlock())) {
                cancel(pos, placement);
                return;
            }
            if (!budget.dispatch(blocksPerTick.get(), delay.get())) { pending.remove(pos); return; }
            lastAttempt.put(pos, System.nanoTime());
            placement.sent = true;
            placement.started = System.nanoTime();
            sendPlacement(actualHit, actualSlot);
            BlockState predicted = world.getBlockState(pos);
            if (!packet.get() && !predicted.canBeReplaced()) placement.predicted = predicted;
            if (packet.get() && packetOnce.get()) PacketPlaceTracker.markSent(pos, confirmationTimeout.get());
            if (render.get()) renderMap.put(pos, System.nanoTime());
        };
        if (rotate.get()) Rotations.rotate(Rotations.getYaw(hit.getLocation()), Rotations.getPitch(hit.getLocation()), 50, action);
        else action.run();
    }

    private void cancel(BlockPos pos, Placement placement) {
        if (pending.remove(pos, placement)) budget.cancel();
    }

    private boolean canPlace(BlockPos pos, Block block) {
        if (!Level.isInSpawnableBounds(pos) || !world.getWorldBorder().isWithinBounds(pos)
            || !world.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4)
            || !world.getBlockState(pos).canBeReplaced()) return false;
        if (attackCrystals.get()) {
            for (var entity : world.getEntities(null, new AABB(pos))) {
                if (entity instanceof EndCrystal crystal && crystal.isAlive()) {
                    if (!attackedThisTick) {
                        mc.getConnection().send(new ServerboundAttackPacket(crystal.getId()));
                        mc.player.swing(InteractionHand.MAIN_HAND);
                        attackedThisTick = true;
                    }
                    return false;
                }
            }
        }
        return world.isUnobstructed(block.defaultBlockState(), pos, CollisionContext.empty());
    }

    private BlockHitResult hit(BlockPos pos) {
        Vec3 eyes = mc.player.getEyePosition();
        for (Direction side : Direction.values()) {
            BlockPos neighbor = pos.relative(side);
            BlockState state = world.getBlockState(neighbor);
            if (state.canBeReplaced() || BlockUtils.isClickable(state.getBlock()) || !state.getFluidState().isEmpty()) continue;
            Vec3 location = Vec3.atCenterOf(pos).add(side.getStepX() * .5, side.getStepY() * .5, side.getStepZ() * .5);
            BlockHitResult hit = new BlockHitResult(location, side.getOpposite(), neighbor, false);
            if (strict.get()) {
                Vec3 inside = location.add(side.getStepX() * 1e-4, side.getStepY() * 1e-4, side.getStepZ() * 1e-4);
                BlockHitResult ray = world.clip(new ClipContext(eyes, inside, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, mc.player));
                if (ray.getType() != HitResult.Type.BLOCK || !ray.getBlockPos().equals(neighbor) || ray.getDirection() != side.getOpposite()) continue;
            }
            return hit;
        }
        return airplace.get() && !strict.get()
            ? new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false) : null;
    }

    private int findBlock() {
        if (isBlock(mc.player.getOffhandItem())) return 45;
        for (int slot = 0; slot < 9; slot++) if (isBlock(mc.player.getInventory().getItem(slot))) return slot;
        if (inventorySwap.get() && mc.player.containerMenu == mc.player.inventoryMenu && mc.player.containerMenu.getCarried().isEmpty()) {
            for (int slot = 9; slot < 36; slot++) if (isBlock(mc.player.getInventory().getItem(slot))) return slot;
        }
        return -1;
    }

    private boolean isBlock(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof BlockItem item && blocks.get().contains(item.getBlock());
    }

    private void sendPlacement(BlockHitResult hit, int slot) {
        InteractionHand hand = slot == 45 ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        InventoryManager inventory = InventoryManager.getInstance();
        var playerInventory = (PlayerInventoryAccessor) mc.player.getInventory();
        int previousClient = inventory.getClientSlot(), previousServer = inventory.getServerSlot();
        int source = slot, buffer = previousClient == 8 ? 7 : 8;
        boolean fromInventory = slot >= 9 && slot < 36;
        if (fromInventory) {
            mc.gameMode.handleContainerInput(mc.player.inventoryMenu.containerId, source, buffer, ContainerInput.SWAP, mc.player);
            slot = buffer;
        }
        try {
            if (hand == InteractionHand.MAIN_HAND) {
                if (!packet.get()) playerInventory.setSelectedSlot(slot);
                if (inventory.getServerSlot() != slot) inventory.setSlotForced(slot);
            }
            if (packet.get()) {
                ((ClientPlayerInteractionManagerTHMAccessor) mc.gameMode).thm$sendSequencedPacket(world,
                    sequence -> new ServerboundUseItemOnPacket(hand, hit, sequence));
                mc.player.swing(hand);
            } else BlockUtils.interact(hit, hand, true);
        } finally {
            playerInventory.setSelectedSlot(previousClient);
            if (inventory.getServerSlot() != previousServer) inventory.setSlotForced(previousServer);
            if (fromInventory) mc.gameMode.handleContainerInput(mc.player.inventoryMenu.containerId, source, buffer, ContainerInput.SWAP, mc.player);
        }
    }

    /** Called on the client thread before vanilla applies an authoritative block update. */
    public void onServerBlockUpdate(ClientLevel level, BlockPos pos, BlockState state) {
        if (!isActive() || level != world || mc.player == null) return;
        Placement placement = pending.remove(pos);
        if (placement != null) {
            if (!placement.sent) budget.cancel();
            // An explicit rejection must not wait for a missing prediction ACK.
            if (placement.predicted != null && level.getBlockState(pos) == placement.predicted) level.setBlock(pos, state, 19);
        }
        PacketPlaceTracker.forget(pos);
        if (state.canBeReplaced()) serverSolid.remove(pos);
        else if (targets.contains(pos)) {
            serverSolid.add(pos.immutable());
            lastAttempt.remove(pos);
        }
        if (replaceTrigger.get() != ReplaceTrigger.onPacket || !state.canBeReplaced() || !canAct()) return;
        rebuildTargets();
        if (!targets.contains(pos)) return;
        var prediction = ((ClientLevelPredictionAccessor) level).thm$getBlockStatePredictionHandler();
        prediction.updateKnownServerState(pos, state);
        // The newly sent placement retains this packet's air state for rollback.
        level.setBlock(pos, state, 19);
        tryPlace(pos.immutable());
    }

    public void onServerChunkUpdate(ClientLevel level, LevelChunk chunk) {
        if (!isActive() || level != world || chunk == null || mc.player == null) return;
        Set<BlockPos> positions = new HashSet<>(targets);
        positions.addAll(pending.keySet());
        Map<BlockPos, BlockState> received = new HashMap<>();
        for (BlockPos pos : positions) {
            if ((pos.getX() >> 4) == chunk.getPos().x() && (pos.getZ() >> 4) == chunk.getPos().z()) {
                received.put(pos, chunk.getBlockState(pos));
            }
        }
        var prediction = ((ClientLevelPredictionAccessor) level).thm$getBlockStatePredictionHandler();
        received.forEach((pos, state) -> {
            prediction.updateKnownServerState(pos, state);
            onServerBlockUpdate(level, pos, state);
        });
    }

    private void expirePlacements() {
        long now = System.nanoTime();
        var iterator = pending.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            Placement placement = entry.getValue();
            if (now - placement.started < confirmationTimeout.get() * 50_000_000L) continue;
            if (!placement.sent) budget.cancel();
            boolean unconfirmed = placement.predicted != null && world.getBlockState(entry.getKey()) == placement.predicted;
            rollback(entry.getKey(), placement);
            iterator.remove();
            if (unconfirmed) {
                // Ask before retrying so an unanswered airplace cannot stack another block.
                PacketPlaceTracker.markSent(entry.getKey(), confirmationTimeout.get());
                PacketPlaceTracker.sendProbes(List.of(entry.getKey()));
            }
        }
    }

    private void rollback(BlockPos pos, Placement placement) {
        if (placement.predicted == null || world.getBlockState(pos) != placement.predicted) return;
        ((ClientLevelPredictionAccessor) world).thm$getBlockStatePredictionHandler().updateKnownServerState(pos, placement.before);
        world.setBlock(pos, placement.before, 19);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    private void onReceive(PacketEvent.Receive event) {
        if (event.isCancelled()) return;
        int expectedGeneration = generation;
        Packet<?> received = event.packet;
        if (!(received instanceof BundlePacket<?>) && !isAuxiliaryPacket(received)) return;
        List<Packet<?>> auxiliary = new ArrayList<>();
        if (received instanceof BundlePacket<?> bundle) {
            for (Packet<?> packet : bundle.subPackets()) if (isAuxiliaryPacket(packet)) auxiliary.add(packet);
        } else if (isAuxiliaryPacket(received)) auxiliary.add(received);
        if (auxiliary.isEmpty()) return;
        mc.execute(() -> {
            if (generation != expectedGeneration || !isActive() || world == null || world != mc.level || mc.player == null) return;
            auxiliary.forEach(this::onAuxiliaryPacket);
        });
    }

    private static boolean isAuxiliaryPacket(Packet<?> packet) {
        return packet instanceof ClientboundBlockDestructionPacket || packet instanceof ClientboundExplodePacket
            || packet instanceof ClientboundAddEntityPacket spawn && spawn.getType() == EntityTypes.END_CRYSTAL;
    }

    private void onAuxiliaryPacket(Packet<?> received) {
        if (received instanceof ClientboundBlockDestructionPacket crack && mineExtend.get()) {
            if (targets.contains(crack.getPos())) {
                if (crack.getProgress() >= 0 && crack.getProgress() <= 9) mining.put(crack.getPos().immutable(), System.nanoTime());
                else mining.remove(crack.getPos());
            }
        } else if (received instanceof ClientboundAddEntityPacket spawn && spawn.getType() == EntityTypes.END_CRYSTAL) {
            if (desyncProtection.get()) {
                AABB crystal = new AABB(spawn.getX() - 1, spawn.getY(), spawn.getZ() - 1, spawn.getX() + 1, spawn.getY() + 2, spawn.getZ() + 1);
                for (var entry : new ArrayList<>(pending.entrySet())) {
                    if (entry.getValue().predicted != null && crystal.intersects(new AABB(entry.getKey()))) {
                        rollback(entry.getKey(), entry.getValue());
                        pending.remove(entry.getKey());
                    }
                }
            }
            if (prePlaceCrystalSpawn.get() && replaceTrigger.get() == ReplaceTrigger.onPacket) fill();
        } else if (received instanceof ClientboundExplodePacket && prePlaceExplosion.get() && replaceTrigger.get() == ReplaceTrigger.onPacket) fill();
    }

    private void center() {
        if (phased.get() && PlacementUtils.isPhasing()) return;
        BlockPos pos = mc.player.blockPosition();
        double dx = pos.getX() + .5 - mc.player.getX(), dz = pos.getZ() + .5 - mc.player.getZ();
        if (Math.abs(dx) < .1 && Math.abs(dz) < .1) {
            mc.player.setDeltaMovement(0, mc.player.getDeltaMovement().y(), 0);
            return;
        }
        mc.player.setPos(mc.player.getX() + dx * .5, mc.player.getY(), mc.player.getZ() + dz * .5);
        mc.player.setDeltaMovement(0, mc.player.getDeltaMovement().y(), 0);
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (!render.get()) return;
        long now = System.nanoTime();
        renderMap.forEach((pos, time) -> {
            double progress = fade.get() ? Math.clamp(1 - (now - time) / (fadeTime.get() * 1_000_000_000L), 0, 1) : 1;
            RenderUtilsTHM.renderBlockFaded(event, pos, sideColor.get(), lineColor.get(), shapeMode.get(), progress);
        });
    }

    private static class Placement {
        final BlockState before;
        long started;
        BlockState predicted;
        boolean sent;
        Placement(BlockState before, long started) { this.before = before; this.started = started; }
    }

    static class PlacementBudget {
        private long tick, lastSend = Long.MIN_VALUE / 2;
        private int sent, reserved;
        void advanceTick() { tick++; sent = 0; }
        void reset() { tick = 0; lastSend = Long.MIN_VALUE / 2; sent = 0; reserved = 0; }
        private boolean ready(int limit, int delay) {
            return sent < limit && (tick == lastSend || tick > lastSend + delay);
        }
        boolean reserve(int limit, int delay) {
            if (sent + reserved >= limit || !ready(limit, delay)) return false;
            reserved++;
            return true;
        }
        boolean dispatch(int limit, int delay) {
            reserved--;
            if (!ready(limit, delay)) return false;
            sent++;
            lastSend = tick;
            return true;
        }
        void cancel() { reserved--; }
    }

    public enum ReplaceTrigger implements DescribedOption {
        onPacket, onClientWorld;
        @Override public String description() {
            return this == onPacket ? "Replace as the server's break update arrives." : "Replace after the client world shows air.";
        }
    }

    public enum CenterMode implements DescribedOption {
        Teleport, NCP, None;
        @Override public String description() {
            return switch (this) {
                case Teleport -> "Snap to the block center when enabled.";
                case NCP -> "Move gradually toward the block center.";
                case None -> "Leave your position unchanged.";
            };
        }
    }
}
