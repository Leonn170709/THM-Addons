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
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
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
import net.minecraft.world.level.block.LevelEvent;
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
    private final SettingGroup sgShape = settings.createGroup("Shape");
    private final SettingGroup sgPlace = settings.createGroup("Placement");
    private final SettingGroup sgMovement = settings.createGroup("Movement");
    private final SettingGroup sgAdvanced = settings.createGroup("Advanced", false);
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<List<Block>> blocks = sgGeneral.add(new BlockListSetting.Builder()
        .name("blocks").description("Blocks to use for surrounding.")
        .defaultValue(Blocks.OBSIDIAN, Blocks.CRYING_OBSIDIAN, Blocks.NETHERITE_BLOCK).build());

    private final Setting<PlacementMode> placementMode = sgGeneral.add(new EnumSetting.Builder<PlacementMode>()
        .name("place-mode").description("How to place surround blocks.")
        .defaultValue(PlacementMode.Normal).build());

    private final Setting<ReplaceTrigger> replaceTrigger = sgGeneral.add(new EnumSetting.Builder<ReplaceTrigger>()
        .name("replace-trigger").description("When to replace broken blocks.")
        .defaultValue(ReplaceTrigger.onPacket).build());

    private final Setting<Integer> blocksPerTick = sgGeneral.add(new IntSetting.Builder()
        .name("blocks-per-tick").description("Maximum placements per tick.")
        .defaultValue(4).min(1).sliderMax(8).build());

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("place-delay").description("Ticks between normal placement batches.")
        .defaultValue(0).min(0).sliderMax(10).build());

    private final Setting<Coverage> coverage = sgShape.add(new EnumSetting.Builder<Coverage>()
        .name("coverage").description("Which levels to surround.")
        .defaultValue(Coverage.Feet).build());

    private final Setting<Expansion> expansion = sgShape.add(new EnumSetting.Builder<Expansion>()
        .name("expansion").description("When to extend the surround.")
        .defaultValue(Expansion.Edges).build());

    private final Setting<Boolean> support = sgShape.add(new BoolSetting.Builder()
        .name("support").description("Add blocks below your feet.")
        .defaultValue(true).build());

    private final Setting<Boolean> rotate = sgPlace.add(new BoolSetting.Builder()
        .name("rotate").description("Rotate toward each placement.")
        .defaultValue(false).build());

    private final Setting<Boolean> airplace = sgPlace.add(new BoolSetting.Builder()
        .name("airplace").description("Place without an adjacent block.")
        .defaultValue(false).build());

    private final Setting<Boolean> inventorySwap = sgPlace.add(new BoolSetting.Builder()
        .name("inventory-swap").description("Use blocks from your main inventory.")
        .defaultValue(false).build());

    private final Setting<Boolean> multitask = sgPlace.add(new BoolSetting.Builder()
        .name("multitask").description("Place while using items.")
        .defaultValue(false).build());

    private final Setting<Boolean> strict = sgPlace.add(new BoolSetting.Builder()
        .name("strict-directions").description("Require a visible support face.")
        .defaultValue(false).build());

    private final Setting<CenterMode> centerMode = sgMovement.add(new EnumSetting.Builder<CenterMode>()
        .name("center-mode").description("How to center the player.")
        .defaultValue(CenterMode.NCP).build());

    private final Setting<Boolean> phased = sgMovement.add(new BoolSetting.Builder()
        .name("phased").description("Skip centering inside solid blocks.")
        .defaultValue(false).visible(() -> centerMode.get() == CenterMode.NCP).build());

    private final Setting<Boolean> onlyOnGround = sgMovement.add(new BoolSetting.Builder()
        .name("only-on-ground").description("Only place while on the ground.")
        .defaultValue(false).build());

    private final Setting<DisableOn> disableOn = sgMovement.add(new EnumSetting.Builder<DisableOn>()
        .name("disable-on").description("When movement disables Surround.")
        .defaultValue(DisableOn.Both).build());

    private final Setting<CrystalHandling> crystalHandling = sgAdvanced.add(new EnumSetting.Builder<CrystalHandling>()
        .name("crystal-handling").description("How to handle obstructing crystals.")
        .defaultValue(CrystalHandling.Both).build());

    private final Setting<PacketEvents> packetEvents = sgAdvanced.add(new EnumSetting.Builder<PacketEvents>()
        .name("packet-events").description("Extra packets that trigger placement checks.")
        .defaultValue(PacketEvents.Both).visible(() -> replaceTrigger.get() == ReplaceTrigger.onPacket).build());

    private final Setting<Double> shiftDelay = sgAdvanced.add(new DoubleSetting.Builder()
        .name("shift-delay").description("Ticks between rejected placement retries.")
        .defaultValue(1.0).min(0).sliderMax(5).build());

    private final Setting<Integer> confirmationTimeout = sgAdvanced.add(new IntSetting.Builder()
        .name("confirmation-timeout").description("Ticks before clearing an unconfirmed placement.")
        .defaultValue(20).min(1).max(200).sliderMax(100).build());

    private final Setting<Boolean> tagSwitch = sgAdvanced.add(new BoolSetting.Builder()
        .name("auto-disable").description("Disable once the surround is confirmed.")
        .defaultValue(false).build());

    private final Setting<RenderStyle> renderStyle = sgRender.add(new EnumSetting.Builder<RenderStyle>()
        .name("render-style").description("How to highlight placements.")
        .defaultValue(RenderStyle.Fade).build());

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode").description("Draw sides, outlines, or both.")
        .defaultValue(ShapeMode.Both).visible(() -> renderStyle.get() != RenderStyle.Off).build());

    private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder()
        .name("side-color").description("Fill color.")
        .defaultValue(new SettingColor(THMAddon.THMSideColor.r, THMAddon.THMSideColor.g, THMAddon.THMSideColor.b, THMAddon.THMSideColor.a)).visible(() -> renderStyle.get() != RenderStyle.Off && shapeMode.get() != ShapeMode.Lines).build());

    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
        .name("line-color").description("Outline color.")
        .defaultValue(new SettingColor(THMAddon.THMColor.r, THMAddon.THMColor.g, THMAddon.THMColor.b, THMAddon.THMColor.a)).visible(() -> renderStyle.get() != RenderStyle.Off && shapeMode.get() != ShapeMode.Sides).build());

    private final Setting<Double> fadeTime = sgRender.add(new DoubleSetting.Builder()
        .name("fade-time").description("Fade duration in seconds.")
        .defaultValue(0.5).min(0.1).sliderMax(2).visible(() -> renderStyle.get() == RenderStyle.Fade).build());

    private final Map<BlockPos, Placement> pending = new HashMap<>();
    private final Map<BlockPos, Long> lastAttempt = new HashMap<>();
    private final Map<BlockPos, Long> mining = new HashMap<>();
    private final Map<BlockPos, Long> renderMap = new HashMap<>();
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
    public CompoundTag toTag() {
        CompoundTag tag = super.toTag();
        tag.putInt("surround-settings-version", 2);
        return tag;
    }

    @Override
    public Module fromTag(CompoundTag tag) { return super.fromTag(migrateSettings(tag)); }

    static CompoundTag migrateSettings(CompoundTag source) {
        CompoundTag migrated = source.copy();
        Map<String, CompoundTag> values = new TreeMap<>();
        Map<String, Boolean> expanded = new HashMap<>();
        boolean resetLayout = source.getIntOr("surround-settings-version", 0) < 2;
        for (Tag entry : source.getCompoundOrEmpty("settings").getListOrEmpty("groups")) {
            if (!(entry instanceof CompoundTag group)) continue;
            expanded.put(group.getStringOr("name", ""), group.getBooleanOr("sectionExpanded", false));
            for (Tag value : group.getListOrEmpty("settings")) {
                if (value instanceof CompoundTag setting) values.put(setting.getStringOr("name", ""), setting.copy());
            }
        }
        putChoice(values, "place-mode", !oldFlag(values, "packet", false) ? PlacementMode.Normal
            : oldFlag(values, "packet-place-once", true) ? PlacementMode.Packet : PlacementMode.PacketRepeat);
        putChoice(values, "coverage", Coverage.values()[(oldFlag(values, "head-level", false) ? 1 : 0)
            + (oldFlag(values, "cover-head", false) ? 2 : 0)]);
        putChoice(values, "expansion", Expansion.values()[(oldFlag(values, "extend", true) ? 1 : 0)
            + (oldFlag(values, "mine-extend", false) ? 2 : 0)]);
        putChoice(values, "disable-on", DisableOn.values()[(oldFlag(values, "disable-on-jump", true) ? 1 : 0)
            + (oldFlag(values, "disable-on-y-change", true) ? 2 : 0)]);
        putChoice(values, "crystal-handling", CrystalHandling.values()[(oldFlag(values, "attack-crystals", true) ? 1 : 0)
            + (oldFlag(values, "desync-protection", true) ? 2 : 0)]);
        putChoice(values, "packet-events", PacketEvents.values()[(oldFlag(values, "pre-place-explosion", true) ? 1 : 0)
            + (oldFlag(values, "pre-place-crystal-spawn", true) ? 2 : 0)]);
        putChoice(values, "render-style", !oldFlag(values, "render", true) ? RenderStyle.Off
            : oldFlag(values, "fade", true) ? RenderStyle.Fade : RenderStyle.Static);
        if (!values.containsKey("auto-disable") && values.containsKey("tag-switch")) {
            CompoundTag renamed = values.get("tag-switch").copy();
            renamed.putString("name", "auto-disable");
            values.put("auto-disable", renamed);
        }
        Map<String, ListTag> grouped = new LinkedHashMap<>();
        for (String group : List.of("General", "Shape", "Placement", "Movement", "Advanced", "Render")) grouped.put(group, new ListTag());
        for (var entry : values.entrySet()) {
            String group = switch (entry.getKey()) {
                case "blocks", "place-mode", "replace-trigger", "blocks-per-tick", "place-delay" -> "General";
                case "coverage", "expansion", "support" -> "Shape";
                case "rotate", "airplace", "inventory-swap", "multitask", "strict-directions" -> "Placement";
                case "center-mode", "phased", "only-on-ground", "disable-on" -> "Movement";
                case "crystal-handling", "packet-events", "shift-delay", "confirmation-timeout", "auto-disable" -> "Advanced";
                case "render-style", "shape-mode", "side-color", "line-color", "fade-time" -> "Render";
                default -> null;
            };
            if (group != null) grouped.get(group).add(entry.getValue());
        }
        ListTag groups = new ListTag();
        grouped.forEach((name, settings) -> {
            CompoundTag group = new CompoundTag();
            group.putString("name", name);
            group.putBoolean("sectionExpanded", resetLayout ? !name.equals("Advanced") : expanded.getOrDefault(name, !name.equals("Advanced")));
            group.put("settings", settings);
            groups.add(group);
        });
        CompoundTag settings = new CompoundTag();
        settings.put("groups", groups);
        migrated.put("settings", settings);
        migrated.putInt("surround-settings-version", 2);
        return migrated;
    }

    private static boolean oldFlag(Map<String, CompoundTag> values, String name, boolean fallback) {
        CompoundTag setting = values.get(name);
        return setting == null ? fallback : setting.getBooleanOr("value", fallback);
    }

    private static void putChoice(Map<String, CompoundTag> values, String name, Enum<?> choice) {
        if (values.containsKey(name)) return;
        CompoundTag setting = new CompoundTag();
        setting.putString("name", name);
        setting.putString("value", choice.toString());
        values.put(name, setting);
    }

    @Override
    public void onActivate() {
        reset(false);
        world = mc.level;
        if (mc.player == null || world == null) return;
        initialY = mc.player.getY();
        if (centerMode.get() == CenterMode.Teleport) PlayerUtils.centerPlayer();
        rebuildTargets();
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
        return (disableOn.get().jump && mc.options.keyJump.isDown())
            || (disableOn.get().height && Math.abs(mc.player.getY() - initialY) > 0.05);
    }

    private boolean canAct() {
        if (!isActive() || world == null || world != mc.level || mc.player == null || mc.gameMode == null
            || mc.getConnection() == null || shouldDisable() || (onlyOnGround.get() && !mc.player.onGround())) return false;
        int priority = InventoryManager.getInstance().getCurrentPriority();
        if (priority > InventoryManager.Priority.SURROUND) return false;
        return multitask.get() || (!mc.player.isUsingItem() && !InventoryManager.getInstance().isEating());
    }

    private void rebuildTargets() {
        Set<BlockPos> feet = footprint(mc.player.getBoundingBox(), mc.player.blockPosition(), expansion.get().edges);
        targets = plan(feet, coverage.get().eyes, coverage.get().roof, airplace.get(), support.get());
        if (expansion.get().mining) {
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
        tryPlace(pos, false);
    }

    private void tryPlace(BlockPos pos, boolean immediate) {
        if (!canAct() || !targets.contains(pos) || pending.containsKey(pos)
            || !world.getBlockState(pos).canBeReplaced()) return;
        if ((!placementMode.get().packet || placementMode.get().waitForAnswer) && !PacketPlaceTracker.canSend(pos)) return;
        long now = System.nanoTime();
        Long previous = lastAttempt.get(pos);
        if (!immediate && previous != null && now - previous < shiftDelay.get() * 50_000_000L) return;
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
        if (!budget.reserve(blocksPerTick.get(), immediate ? 0 : delay.get())) return;
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
            if (!budget.dispatch(blocksPerTick.get(), immediate ? 0 : delay.get())) { pending.remove(pos); return; }
            if (immediate && rotate.get()) {
                float yaw = (float) Rotations.getYaw(actualHit.getLocation()), pitch = (float) Rotations.getPitch(actualHit.getLocation());
                mc.getConnection().send(new ServerboundMovePlayerPacket.Rot(yaw, pitch, mc.player.onGround(), mc.player.horizontalCollision));
                Rotations.setCamRotation(yaw, pitch);
            }
            lastAttempt.put(pos, System.nanoTime());
            placement.sent = true;
            placement.started = System.nanoTime();
            sendPlacement(actualHit, actualSlot);
            BlockState predicted = world.getBlockState(pos);
            if (!placementMode.get().packet && !predicted.canBeReplaced()) placement.predicted = predicted;
            if (placementMode.get().packet && placementMode.get().waitForAnswer) PacketPlaceTracker.markSent(pos, confirmationTimeout.get());
            if ((renderStyle.get() != RenderStyle.Off)) renderMap.put(pos, System.nanoTime());
        };
        if (rotate.get() && !immediate) Rotations.rotate(Rotations.getYaw(hit.getLocation()), Rotations.getPitch(hit.getLocation()), 50, action);
        else action.run();
    }

    private void cancel(BlockPos pos, Placement placement) {
        if (pending.remove(pos, placement)) budget.cancel();
    }

    private boolean canPlace(BlockPos pos, Block block) {
        if (!Level.isInSpawnableBounds(pos) || !world.getWorldBorder().isWithinBounds(pos)
            || !world.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4)
            || !world.getBlockState(pos).canBeReplaced()) return false;
        if (crystalHandling.get().attack) {
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
                if (!placementMode.get().packet) playerInventory.setSelectedSlot(slot);
                if (inventory.getServerSlot() != slot) inventory.setSlotForced(slot);
            }
            if (placementMode.get().packet) {
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
        Placement early = pending.get(pos);
        if (early != null && early.speculative && state.canBeReplaced()) {
            // This is the break update following the effect, not a second placement request.
            early.speculative = false;
            early.before = state;
            ((ClientLevelPredictionAccessor) level).thm$getBlockStatePredictionHandler().updateKnownServerState(pos, state);
            PacketPlaceTracker.forget(pos);
            if (placementMode.get().packet && placementMode.get().waitForAnswer) PacketPlaceTracker.markSent(pos, confirmationTimeout.get());
            return;
        }
        Placement placement = pending.remove(pos);
        if (placement != null) {
            if (!placement.sent) budget.cancel();
            // An explicit rejection must not wait for a missing prediction ACK.
            if (placement.predicted != null && level.getBlockState(pos) == placement.predicted) level.setBlock(pos, state, 19);
        }
        PacketPlaceTracker.forget(pos);
        if (!state.canBeReplaced()) lastAttempt.remove(pos);
        if (replaceTrigger.get() != ReplaceTrigger.onPacket || !state.canBeReplaced() || !canAct()) return;
        rebuildTargets();
        if (!targets.contains(pos)) return;
        var prediction = ((ClientLevelPredictionAccessor) level).thm$getBlockStatePredictionHandler();
        prediction.updateKnownServerState(pos, state);
        // The newly sent placement retains this packet's air state for rollback.
        level.setBlock(pos, state, 19);
        tryPlace(pos.immutable(), true);
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
            if (placement.sent && placementMode.get() == PlacementMode.PacketRepeat) {
                iterator.remove();
                continue;
            }
            if (placement.predicted != null && world.getBlockState(entry.getKey()).canBeReplaced()) {
                iterator.remove();
                continue;
            }
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
            || isBreakEffect(packet)
            || packet instanceof ClientboundAddEntityPacket spawn && spawn.getType() == EntityTypes.END_CRYSTAL;
    }

    static boolean isBreakEffect(Packet<?> packet) {
        return packet instanceof ClientboundLevelEventPacket effect && effect.getType() == LevelEvent.PARTICLES_DESTROY_BLOCK;
    }

    private void onAuxiliaryPacket(Packet<?> received) {
        if (isBreakEffect(received)) {
            preplaceBreak((ClientboundLevelEventPacket) received);
        } else if (received instanceof ClientboundBlockDestructionPacket crack && expansion.get().mining) {
            if (targets.contains(crack.getPos())) {
                if (crack.getProgress() >= 0 && crack.getProgress() <= 9) mining.put(crack.getPos().immutable(), System.nanoTime());
                else mining.remove(crack.getPos());
            }
        } else if (received instanceof ClientboundAddEntityPacket spawn && spawn.getType() == EntityTypes.END_CRYSTAL) {
            if (crystalHandling.get().desync) {
                AABB crystal = new AABB(spawn.getX() - 1, spawn.getY(), spawn.getZ() - 1, spawn.getX() + 1, spawn.getY() + 2, spawn.getZ() + 1);
                for (var entry : new ArrayList<>(pending.entrySet())) {
                    if (entry.getValue().predicted != null && crystal.intersects(new AABB(entry.getKey()))) {
                        rollback(entry.getKey(), entry.getValue());
                        pending.remove(entry.getKey());
                    }
                }
            }
            if (packetEvents.get().crystals && replaceTrigger.get() == ReplaceTrigger.onPacket) fill();
        } else if (received instanceof ClientboundExplodePacket && packetEvents.get().explosions && replaceTrigger.get() == ReplaceTrigger.onPacket) fill();
    }

    private void preplaceBreak(ClientboundLevelEventPacket effect) {
        if (replaceTrigger.get() != ReplaceTrigger.onPacket || !canAct()) return;
        rebuildTargets();
        BlockPos pos = effect.getPos().immutable();
        if (!targets.contains(pos) || pending.containsKey(pos)) return;
        BlockState broken = Block.stateById(effect.getData()), original = world.getBlockState(pos);
        if (broken.canBeReplaced() || (!original.canBeReplaced() && original.getBlock() != broken.getBlock())) return;
        Placement placed = null;
        try {
            world.setBlock(pos, Blocks.AIR.defaultBlockState(), 19);
            tryPlace(pos, true);
            placed = pending.get(pos);
            if (placed != null && placed.sent) {
                placed.before = original.canBeReplaced() ? broken : original;
                placed.speculative = true;
                ((ClientLevelPredictionAccessor) world).thm$getBlockStatePredictionHandler().updateKnownServerState(pos, placed.before);
            }
        } finally {
            if (placed == null || placed.predicted == null) world.setBlock(pos, original, 19);
        }
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
        if (renderStyle.get() == RenderStyle.Off) return;
        long now = System.nanoTime();
        renderMap.forEach((pos, time) -> {
            double progress = (renderStyle.get() == RenderStyle.Fade) ? Math.clamp(1 - (now - time) / (fadeTime.get() * 1_000_000_000L), 0, 1) : 1;
            RenderUtilsTHM.renderBlockFaded(event, pos, sideColor.get(), lineColor.get(), shapeMode.get(), progress);
        });
    }

    private static class Placement {
        BlockState before;
        long started;
        BlockState predicted;
        boolean sent;
        boolean speculative;
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

    public enum PlacementMode implements DescribedOption {
        Normal(false, false, "Use normal placement with client prediction."),
        Packet(true, true, "Send packets and wait for server confirmation."),
        PacketRepeat(true, false, "Send packets without waiting for confirmation.");
        final boolean packet;
        final boolean waitForAnswer;
        private final String description;
        PlacementMode(boolean packet, boolean waitForAnswer, String description) {
            this.packet = packet;
            this.waitForAnswer = waitForAnswer;
            this.description = description;
        }
        @Override public String description() { return description; }
    }

    public enum Coverage implements DescribedOption {
        Feet(false, false, "Surround your feet."),
        Eyes(true, false, "Surround your feet and eye level."),
        Roof(false, true, "Surround your feet and add a roof."),
        Full(true, true, "Surround your feet, eye level, and roof.");
        final boolean eyes;
        final boolean roof;
        private final String description;
        Coverage(boolean eyes, boolean roof, String description) {
            this.eyes = eyes;
            this.roof = roof;
            this.description = description;
        }
        @Override public String description() { return description; }
    }

    public enum Expansion implements DescribedOption {
        None(false, false, "Use a single block footprint."),
        Edges(true, false, "Cover every block beneath your feet."),
        Mining(false, true, "Extend beside blocks being mined."),
        Both(true, true, "Cover edges and extend beside mined blocks.");
        final boolean edges;
        final boolean mining;
        private final String description;
        Expansion(boolean edges, boolean mining, String description) {
            this.edges = edges;
            this.mining = mining;
            this.description = description;
        }
        @Override public String description() { return description; }
    }

    public enum DisableOn implements DescribedOption {
        Never(false, false, "Stay enabled when you move."),
        Jump(true, false, "Disable when you jump."),
        HeightChange(false, true, "Disable when your height changes."),
        Both(true, true, "Disable on jumps or height changes.");
        final boolean jump;
        final boolean height;
        private final String description;
        DisableOn(boolean jump, boolean height, String description) {
            this.jump = jump;
            this.height = height;
            this.description = description;
        }
        @Override public String description() { return description; }
    }

    public enum CrystalHandling implements DescribedOption {
        Off(false, false, "Skip crystal-specific handling."),
        Attack(true, false, "Attack crystals blocking placement."),
        Desync(false, true, "Clear unconfirmed blocks overlapping new crystals."),
        Both(true, true, "Attack blocking crystals and clear conflicting predictions.");
        final boolean attack;
        final boolean desync;
        private final String description;
        CrystalHandling(boolean attack, boolean desync, String description) {
            this.attack = attack;
            this.desync = desync;
            this.description = description;
        }
        @Override public String description() { return description; }
    }

    public enum PacketEvents implements DescribedOption {
        None(false, false, "Skip extra explosion and crystal checks."),
        Explosions(true, false, "Also check missing blocks on explosions."),
        Crystals(false, true, "Also check missing blocks on crystal spawns."),
        Both(true, true, "Also check on explosions and crystal spawns.");
        final boolean explosions;
        final boolean crystals;
        private final String description;
        PacketEvents(boolean explosions, boolean crystals, String description) {
            this.explosions = explosions;
            this.crystals = crystals;
            this.description = description;
        }
        @Override public String description() { return description; }
    }

    public enum RenderStyle implements DescribedOption {
        Off("Hide placement highlights."),
        Static("Show highlights without fading."),
        Fade("Fade placement highlights over time.");
        private final String description;
        RenderStyle(String description) {
            this.description = description;
        }
        @Override public String description() { return description; }
    }

    public enum ReplaceTrigger implements DescribedOption {
        onPacket, onClientWorld;
        @Override public String description() {
            return this == onPacket ? "Preplace on break packets and repair client-world air." : "Place when the client world shows air.";
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
