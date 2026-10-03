/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.modules;

import net.minecraft.network.protocol.game.ServerboundAttackPacket;

import xyz.thm.addon.utils.PacketPlaceTracker;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.BundlePacket;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.core.*;
import net.minecraft.world.phys.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import xyz.thm.addon.utils.RenderUtilsTHM;
import xyz.thm.addon.THMAddon;
import xyz.thm.addon.mixin.accessor.ExplosionS2CPacketAccessor;
import xyz.thm.addon.utils.PlacementUtils;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

public class SurroundPlus extends Module {
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
        .description("Only place via packets (no client-side block set).")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> packetOnce = sgPlace.add(new BoolSetting.Builder()
        .name("packet-place-once")
        .description("Experimental: one packet per block, sent again only once the server says it's still air.")
        .defaultValue(false)
        .visible(packet::get)
        .build()
    );

    private final Setting<Boolean> tagSwitch = sgGeneral.add(new BoolSetting.Builder()
        .name("tag-switch")
        .description("Disables the module immediately after placing missing blocks.")
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
        .description("Sends rotation packets when placing (Crucial for GrimAC).")
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
        .description("Only places on visible block faces to bypass strict anti-cheats.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> airplace = sgPlace.add(new BoolSetting.Builder()
        .name("airplace")
        .description("Places blocks even with no adjacent face, by sending UP as the hit face.")
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
        .description("Removes a client-side block when a crystal spawns inside it (desynced from server).")
        .defaultValue(true)
        .build()
    );
    private final Setting<Boolean> headLevel = sgPlace.add(new BoolSetting.Builder()
        .name("head-level")
        .description("Also places surround at Y+1.")
        .defaultValue(false)
        .build()
    );
    private final Setting<Boolean> coverHead = sgPlace.add(new BoolSetting.Builder()
        .name("cover-head")
        .description("Places a block at Y+2.")
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

    private final Setting<TimingMode> timingMode = sgTiming.add(new EnumSetting.Builder<TimingMode>()
        .name("timing-mode")
        .description("Timing mode for replacement.")
        .defaultValue(TimingMode.Sequential)
        .build()
    );
    private final Setting<Boolean> prePlaceExplosion = sgTiming.add(new BoolSetting.Builder()
        .name("pre-place-explosion")
        .description("Attempts immediate replacement on explosion packets.")
        .defaultValue(true)
        .visible(() -> timingMode.get() == TimingMode.Sequential)
        .build()
    );
    private final Setting<Boolean> prePlaceCrystalSpawn = sgTiming.add(new BoolSetting.Builder()
        .name("pre-place-crystal-spawn")
        .description("Attempts immediate replacement when crystals spawn on surround.")
        .defaultValue(true)
        .visible(() -> timingMode.get() == TimingMode.Sequential)
        .build()
    );
    private final Setting<Double> shiftDelay = sgTiming.add(new DoubleSetting.Builder()
        .name("shift-delay")
        .description("Minimum delay between retries for the same surround position.")
        .defaultValue(1.0)
        .min(0.0)
        .sliderMax(5.0)
        .build()
    );

    private final Setting<Boolean> onlyOnGround = sgPlace.add(new BoolSetting.Builder()
        .name("only-on-ground")
        .description("Only activates when you are on the ground.")
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

    private final Map<BlockPos, Long> renderMap = new HashMap<>();
    private final Map<BlockPos, Long> packetPlacedAt = new HashMap<>();
    private final List<BlockPos> surroundCache = new ArrayList<>();
    // Thread-safe queue for placements triggered from the packet (Netty) thread
    private final Queue<BlockPos> fallbackQueue = new ConcurrentLinkedQueue<>();
    private int delayTimer;
    private BlockPos initialPos;

    public SurroundPlus() {
        super(THMAddon.PVP, "surround-plus", "Surrounds feet with Obsidian using strict logic.");
    }

    @Override
    public void onActivate() {
        delayTimer = 0;
        renderMap.clear();
        packetPlacedAt.clear();
        surroundCache.clear();
        fallbackQueue.clear();
        if (mc.player == null) return;
        initialPos = mc.player.blockPosition();

        if (centerMode.get() == CenterMode.Teleport) {
            PlayerUtils.centerPlayer();
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.level == null) return;
        if (!multitask.get() && mc.player.isUsingItem() && mc.player.getUsedItemHand() == InteractionHand.MAIN_HAND) return;

        if ((disableOnJump.get() && mc.options.keyJump.isDown()) || (disableOnYChange.get() && mc.player.getY() != initialPos.getY())) {
            toggle();
            return;
        }

        if (onlyOnGround.get() && !mc.player.onGround()) return;

        handleCentering();

        // Process fallback placements queued from the packet (Netty) thread — must run on main thread
        BlockPos fallback;
        while ((fallback = fallbackQueue.poll()) != null) {
            FindItemResult fallbackItem = InvUtils.findInHotbar(itemStack -> blocks.get().contains(Block.byItem(itemStack.getItem())));
            if (fallbackItem.found()) placeBlock(fallback, fallbackItem);
        }

        if (delayTimer > 0) {
            delayTimer--;
            return;
        }

        FindItemResult block = InvUtils.findInHotbar(itemStack -> blocks.get().contains(Block.byItem(itemStack.getItem())));
        if (!block.found()) return;

        int placed = 0;
        Set<BlockPos> insideBlocks = getInsideBlocks();

        if (support.get()) {
            for (BlockPos inside : insideBlocks) {
                BlockPos underPos = inside.below();
                if (mc.level.getBlockState(underPos).canBeReplaced()) {
                    if (placed >= blocksPerTick.get()) break;
                    if (placeBlock(underPos, block)) {
                        placed++;
                    }
                }
            }
        }

        Set<BlockPos> surroundPositions = getSurroundPositions(insideBlocks);
        surroundCache.clear();
        surroundCache.addAll(surroundPositions);
        if (attackCrystals.get()) attackCrystals(surroundCache);

        boolean allPlaced = true;

        for (BlockPos pos : surroundPositions) {
            if (!mc.level.getBlockState(pos).canBeReplaced()) continue;
            if (shiftDelay.get() > 0.0) {
                Long last = packetPlacedAt.get(pos);
                if (last != null && System.currentTimeMillis() - last < shiftDelay.get() * 50.0) continue;
            }

            // If support is enabled and the target block has no placeable side,
            // try to place a support block underneath first.
            if (support.get() && PlacementUtils.getPlaceSide(pos) == null) {
                BlockPos supportPos = pos.below();
                if (mc.level.getBlockState(supportPos).canBeReplaced()) {
                    if (placed >= blocksPerTick.get()) {
                        allPlaced = false;
                        break;
                    }
                    if (placeBlock(supportPos, block)) {
                        placed++;
                    } else {
                        allPlaced = false;
                        continue;
                    }
                } else {
                    // Can't place support and no side to place on: skip for now.
                    allPlaced = false;
                    continue;
                }
            }

            if (!BlockUtils.canPlace(pos)) {
                allPlaced = false;
                continue;
            }

            if (placed >= blocksPerTick.get()) {
                allPlaced = false;
                break;
            }

            if (placeBlock(pos, block)) {
                placed++;
            } else {
                allPlaced = false;
            }
        }

        if (placed > 0) {
            delayTimer = delay.get();
        }

        if (tagSwitch.get() && allPlaced) {
            toggle();
        }
    }

    private boolean placeBlock(BlockPos pos, FindItemResult item) {
        if (packet.get()) {
            if (!placePacket(pos, item, airplace.get())) return false;
            renderMap.put(pos, System.currentTimeMillis());
            packetPlacedAt.put(pos, System.currentTimeMillis());
            return true;
        }

        if (PlacementUtils.placeOnSolidSide(pos, item, rotate.get(), 50, true)) {
            setBlock(pos, item);
            renderMap.put(pos, System.currentTimeMillis());
            packetPlacedAt.put(pos, System.currentTimeMillis());
            return true;
        }

        // Airplace fallback for normal mode: no adjacent face found, send packet directly
        if (airplace.get() && PlacementUtils.getPlaceSide(pos) == null && BlockUtils.canPlace(pos)) {
            if (placePacket(pos, item, true)) {
                renderMap.put(pos, System.currentTimeMillis());
                packetPlacedAt.put(pos, System.currentTimeMillis());
                return true;
            }
        }

        return false;
    }

    private boolean placePacket(BlockPos pos, FindItemResult item, boolean airPlace) {
        if (packetOnce.get() && !PacketPlaceTracker.canSend(pos)) return false;
        if (!PlacementUtils.placeBlockPacket(pos, item, rotate.get(), 50, airPlace)) return false;
        if (packetOnce.get()) PacketPlaceTracker.markSent(pos, PacketPlaceTracker.DEFAULT_RESEND_TICKS);
        return true;
    }

    private void setBlock(BlockPos pos, FindItemResult item) {
        Item it = mc.player.getInventory().getItem(item.slot()).getItem();
        if (!(it instanceof BlockItem block)) return;

        mc.level.setBlockAndUpdate(pos, block.getBlock().defaultBlockState());
        mc.level.playSound(mc.player, pos.getX(), pos.getY(), pos.getZ(), SoundEvents.STONE_PLACE, SoundSource.BLOCKS, 1, 1);
    }

    private void handleCentering() {
        if (centerMode.get() != CenterMode.NCP) return;
        if (phased.get() && !mc.level.getBlockState(mc.player.blockPosition()).canBeReplaced()) return;

        Vec3 centerPos = Vec3.atBottomCenterOf(mc.player.blockPosition());
        double xDiff = Math.abs(centerPos.x - mc.player.getX());
        double zDiff = Math.abs(centerPos.z - mc.player.getZ());

        if (xDiff <= 0.1 && zDiff <= 0.1) {
            mc.player.setDeltaMovement(0, mc.player.getDeltaMovement().y, 0);
            return;
        }

        double motionX = (centerPos.x - mc.player.getX()) / 2.0;
        double motionZ = (centerPos.z - mc.player.getZ()) / 2.0;

        mc.player.setDeltaMovement(motionX, mc.player.getDeltaMovement().y, motionZ);
    }

    private Set<BlockPos> getInsideBlocks() {
        BlockPos base = mc.player.blockPosition();
        LinkedHashSet<BlockPos> inside = new LinkedHashSet<>();

        if (!extend.get()) {
            inside.add(base);
            return inside;
        }

        int[] size = getSize(mc.player);
        for (int x = size[0]; x <= size[1]; x++) {
            for (int z = size[2]; z <= size[3]; z++) {
                inside.add(base.offset(x, 0, z));
            }
        }

        return inside;
    }

    private Set<BlockPos> getSurroundPositions(Set<BlockPos> insideBlocks) {
        LinkedHashSet<BlockPos> surround = new LinkedHashSet<>();
        Set<BlockPos> footBlocks = new LinkedHashSet<>(insideBlocks);
        for (BlockPos pos : insideBlocks) {
            BlockPos north = pos.north();
            BlockPos south = pos.south();
            BlockPos east = pos.east();
            BlockPos west = pos.west();

            if (!insideBlocks.contains(north)) surround.add(north);
            if (!insideBlocks.contains(south)) surround.add(south);
            if (!insideBlocks.contains(east)) surround.add(east);
            if (!insideBlocks.contains(west)) surround.add(west);
        }

        if (headLevel.get()) {
            LinkedHashSet<BlockPos> head = new LinkedHashSet<>();
            for (BlockPos foot : footBlocks) {
                BlockPos up = foot.above();
                head.add(up.north());
                head.add(up.south());
                head.add(up.east());
                head.add(up.west());
            }
            for (BlockPos foot : footBlocks) head.remove(foot.above());
            surround.addAll(head);
        }

        if (coverHead.get()) {
            for (BlockPos foot : footBlocks) surround.add(foot.above(2));
        }

        if (mineExtend.get()) {
            LinkedHashSet<BlockPos> ext = new LinkedHashSet<>();
            for (BlockPos pos : new ArrayList<>(surround)) {
                BlockState s = mc.level.getBlockState(pos);
                if (s.canBeReplaced()) continue;
                if (s.getDestroySpeed(mc.level, pos) < 0) continue;
                for (Direction d : Direction.Plane.HORIZONTAL) {
                    BlockPos e = pos.relative(d);
                    if (!footBlocks.contains(e) && !surround.contains(e)) ext.add(e);
                }
            }
            surround.addAll(ext);
        }
        return surround;
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (mc.player == null || mc.level == null) return;

        if (event.packet instanceof BundlePacket<?> bundle) {
            for (Object sub : bundle.subPackets()) {
                if (sub instanceof Packet<?> p) handlePacket(p);
            }
        } else if (event.packet instanceof Packet<?> p) {
            handlePacket(p);
        }
    }

    private void handlePacket(Packet<?> packet) {
        if (packet instanceof ClientboundAddEntityPacket p && desyncProtection.get() && p.getType() == net.minecraft.world.entity.EntityTypes.END_CRYSTAL) {
            BlockPos pos = BlockPos.containing(p.getX(), p.getY(), p.getZ());
            if (!mc.level.getBlockState(pos).canBeReplaced()) mc.level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        }

        if (timingMode.get() != TimingMode.Sequential) return;

        if (packet instanceof ClientboundBlockUpdatePacket p) {
            BlockPos pos = p.getPos();
            if (!surroundCache.contains(pos)) return;
            BlockState state = p.getBlockState();
            if (state.canBeReplaced() && mc.level.isUnobstructed(Blocks.OBSIDIAN.defaultBlockState(), pos, CollisionContext.empty())) {
                placeFallbackDirect(pos);
            } else if (!state.canBeReplaced()) {
                packetPlacedAt.remove(pos);
            }
            return;
        }

        if (packet instanceof ClientboundExplodePacket p && prePlaceExplosion.get()) {
            Vec3 c = ((ExplosionS2CPacketAccessor) (Object) p).getCenter();
            BlockPos pos = BlockPos.containing(c.x, c.y, c.z);
            if (surroundCache.contains(pos)) placeFallbackDirect(pos);
            return;
        }

        if (packet instanceof ClientboundAddEntityPacket p && prePlaceCrystalSpawn.get() && p.getType() == net.minecraft.world.entity.EntityTypes.END_CRYSTAL) {
            BlockPos pos = BlockPos.containing(p.getX(), p.getY(), p.getZ());
            if (surroundCache.contains(pos)) placeFallbackDirect(pos);
        }
    }

    // Queue the position for placement on the main thread instead of placing directly
    // (BlockUtils.place triggers a chunk rebuild which must run on the render/main thread)
    private void placeFallbackDirect(BlockPos pos) {
        fallbackQueue.add(pos);
    }

    private void attackCrystals(List<BlockPos> positions) {
        for (BlockPos pos : positions) {
            Entity crystal = mc.level.getEntities(null, new AABB(pos)).stream()
                .filter(e -> e instanceof EndCrystal)
                .findFirst()
                .orElse(null);
            if (crystal != null) {
                mc.getConnection().send(new ServerboundAttackPacket(crystal.getId()));
                mc.getConnection().send(new ServerboundSwingPacket(InteractionHand.MAIN_HAND));
                return;
            }
        }
    }

    private int[] getSize(Player player) {
        int[] size = new int[] {0, 0, 0, 0};

        double x = player.getX() - player.getBlockX();
        double z = player.getZ() - player.getBlockZ();

        if (x < 0.3) size[0] = -1;
        if (x > 0.7) size[1] = 1;
        if (z < 0.3) size[2] = -1;
        if (z > 0.7) size[3] = 1;

        return size;
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (!render.get() || renderMap.isEmpty()) return;

        renderMap.entrySet().removeIf(entry -> System.currentTimeMillis() - entry.getValue() > fadeTime.get() * 1000);

        renderMap.forEach((pos, time) -> {
            double progress = 1.0;
            if (fade.get()) {
                long alive = System.currentTimeMillis() - time;
                progress = 1.0 - Mth.clamp((double) alive / (fadeTime.get() * 1000), 0.0, 1.0);
            }

            RenderUtilsTHM.renderBlockFaded(event, pos, sideColor.get(), lineColor.get(), shapeMode.get(), progress);
        });
    }

    public enum CenterMode {
        Teleport,
        NCP,
        None
    }

    public enum TimingMode {
        Vanilla,
        Sequential
    }
}
