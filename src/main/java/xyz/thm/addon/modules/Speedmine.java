/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.modules;

import meteordevelopment.meteorclient.events.entity.player.StartBreakingBlockEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.misc.Keybind;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectUtil;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.WorldEvents;
import xyz.thm.addon.THMAddon;
import xyz.thm.addon.mixin.accessor.ClientPlayerInteractionManagerTHMAccessor;
import xyz.thm.addon.mixin.accessor.PlayerInventoryAccessor;
import xyz.thm.addon.system.THMSystem;
import xyz.thm.addon.utils.RangeUtils;
import xyz.thm.addon.utils.RenderUtilsTHM;
import xyz.thm.addon.utils.ThmMembers;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.function.Function;

import static xyz.thm.addon.THMAddon.THMColor;

//Thank you very much mushek
/**
 * Grim-safe packet miner.
 *
 * HOW THE 20 BPS INSTA-BREAK WORKS
 * ─────────────────────────────────
 * Minecraft calculates a "break delta" each tick:
 *   delta = miningSpeed / hardness / (requiresTool && !correctTool ? 100 : 30)
 *
 * When delta >= 1.0 the block breaks in a single tick (vanilla "instant break").
 * When delta >= breakThreshold (default 0.7) we treat it as *effectively* instant —
 * a single START+STOP pair sent in the same tick breaks the block server-side.
 * At 20 ticks/second that gives up to 20 blocks/second (20 BPS).
 *
 * GRIM BYPASS
 * ───────────
 * Normally the client sends START then STOP for each block.
 * With grimBypass enabled we send STOP *before* START, which confuses Grim's
 * sequence validator (it expects START → STOP, not STOP → START).
 *
 * CLIENT-SIDE REMOVAL (validateBreak = false)
 * ───────────────────────────────────────────
 * On high-ping servers, waiting for the server to confirm each break adds lag.
 * With validateBreak disabled we immediately set the block to AIR on the client
 * and play the break particles/sound, trusting the server will agree.
 *
 * DOUBLE BREAK
 * ────────────
 * Tracks two blocks simultaneously (primary and secondary slots).  When the
 * primary's progress hits the threshold, a STOP is sent for it and a new block
 * can start immediately — overlapping the server round-trip.
 */
public class Speedmine extends Module {

    private final SettingGroup sgMine   = settings.getDefaultGroup();
    private final SettingGroup sgAuto   = settings.createGroup("Auto Mine", false);
    private final SettingGroup sgRender = settings.createGroup("Render");

    // ── Auto Mine (target selection, ported from BlackOut's AutoMine) ─────────

    public final Setting<Boolean> autoMine = sgAuto.add(new BoolSetting.Builder()
        .name("auto-mine")
        .description("Automatically pick blocks to break around nearby enemies.")
        .defaultValue(false)
        .build());

    public final Setting<Keybind> autoMineBind = sgAuto.add(new KeybindSetting.Builder()
        .name("auto-mine-bind")
        .description("Hold this to auto-mine. Leave unbound to have auto-mine run whenever the module is on.")
        .defaultValue(Keybind.none())
        .visible(autoMine::get)
        .build());

    public final Setting<Boolean> autoMineOnly = sgAuto.add(new BoolSetting.Builder()
        .name("auto-mine-only")
        .description("Ignore blocks you click yourself — only auto-mine targets get broken.")
        .defaultValue(false)
        .visible(autoMine::get)
        .build());

    public final Setting<Boolean> feetFirst = sgAuto.add(new BoolSetting.Builder()
        .name("feet-first")
        .description("Break the lowest targets first, so an enemy's feet go before their head. Applies to bedrock too.")
        .defaultValue(false)
        .visible(autoMine::get)
        .build());

    public final Setting<Double> enemyRange = sgAuto.add(new DoubleSetting.Builder()
        .name("enemy-range")
        .description("How far away an enemy can be to be considered.")
        .defaultValue(10).min(1).max(20).sliderRange(1, 20)
        .visible(autoMine::get)
        .build());

    public final Setting<Boolean> antiPhase = sgAuto.add(new BoolSetting.Builder()
        .name("anti-phase")
        .description("Mine the blocks an enemy is standing inside.")
        .defaultValue(true)
        .visible(autoMine::get)
        .build());

    public final Setting<Boolean> antiSurround = sgAuto.add(new BoolSetting.Builder()
        .name("anti-surround")
        .description("Mine the blocks boxing an enemy in.")
        .defaultValue(true)
        .visible(autoMine::get)
        .build());

    public final Setting<Boolean> neverMineOwn = sgAuto.add(new BoolSetting.Builder()
        .name("never-mine-own")
        .description("Never auto-mine blocks touching you.")
        .defaultValue(true)
        .visible(autoMine::get)
        .build());

    public final Setting<Boolean> autoDoubleMine = sgAuto.add(new BoolSetting.Builder()
        .name("auto-double-mine")
        .description("Break two auto-mine targets at once, using double-break.")
        .defaultValue(true)
        .visible(autoMine::get)
        .build());

    public final Setting<Boolean> mineBedrock = sgAuto.add(new BoolSetting.Builder()
        .name("mine-bedrock")
        .description("Also target bedrock, broken vanilla-style with hand swings instead of packets.")
        .defaultValue(false)
        .visible(autoMine::get)
        .build());

    public final Setting<Boolean> bedrockOnly = sgAuto.add(new BoolSetting.Builder()
        .name("bedrock-only")
        .description("Only break bedrock, ignore every other block.")
        .defaultValue(false)
        .visible(() -> autoMine.get() && mineBedrock.get())
        .build());

    public final Setting<Boolean> bedrockRotate = sgAuto.add(new BoolSetting.Builder()
        .name("bedrock-rotate")
        .description("Silently look at the bedrock before breaking it. Usually not needed.")
        .defaultValue(false)
        .visible(() -> autoMine.get() && mineBedrock.get())
        .build());

    public final Setting<Boolean> rotate = sgMine.add(new BoolSetting.Builder()
        .name("rotate")
        .description("Silently look at each block before mining it. Bedrock has its own toggle.")
        .defaultValue(false)
        .build());

    public final Setting<Boolean> grimBypass = sgMine.add(new BoolSetting.Builder()
        .name("grim-bypass")
        .description("Send STOP_DESTROY_BLOCK before START to bypass Grim's sequence check.")
        .defaultValue(true)
        .build());

    public final Setting<Boolean> doubleBreak = sgMine.add(new BoolSetting.Builder()
        .name("double-break")
        .description("Track a primary and secondary block simultaneously.")
        .defaultValue(true)
        .build());

    public final Setting<Boolean> queueEnabled = sgMine.add(new BoolSetting.Builder()
        .name("queue")
        .description("Queue extra blocks when both break slots are occupied.")
        .defaultValue(true)
        .build());

    public final Setting<Double> breakThreshold = sgMine.add(new DoubleSetting.Builder()
        .name("break-threshold")
        .description("Break-delta fraction at which a block is treated as instant. "
                   + "0.7 = 20-BPS sweet spot.")
        .defaultValue(0.7).min(0.1).max(1.0).decimalPlaces(2)
        .build());

    public final Setting<Boolean> validateBreak = sgMine.add(new BoolSetting.Builder()
        .name("validate-break")
        .description("Wait for the server to confirm each break. Disable on high ping.")
        .defaultValue(true)
        .build());

    public final Setting<Boolean> removeSlowBlocks = sgMine.add(new BoolSetting.Builder()
        .name("remove-slow-blocks")
        .description("Also remove blocks below the instant-break threshold client-side.")
        .defaultValue(false)
        .visible(() -> !validateBreak.get())
        .build());

    public final Setting<Boolean> instantClientRemove = sgMine.add(new BoolSetting.Builder()
        .name("instant-client-remove")
        .description("Remove above-threshold blocks client-side immediately, even when validate-break is on. Reduces perceived latency on high-ping servers.")
        .defaultValue(false)
        .build());

    public final Setting<Boolean> autoRebreak = sgMine.add(new BoolSetting.Builder()
        .name("auto-rebreak")
        .description("Rebreak the last position if a block reappears there.")
        .defaultValue(true)
        .build());

    public final Setting<Boolean> silentSwap = sgMine.add(new BoolSetting.Builder()
        .name("silent-swap")
        .description("Swap to the best tool via packet without visually changing your held item.")
        .defaultValue(true)
        .build());

    public final Setting<Boolean> toolHold = sgMine.add(new BoolSetting.Builder()
        .name("tool-hold")
        .description("Keep the swapped tool held until mining is done, so the server breaks with it.")
        .defaultValue(true)
        .build());

    public final Setting<Double> range = sgMine.add(new DoubleSetting.Builder()
        .name("range")
        .description("Maximum block-breaking distance.")
        .defaultValue(5.2).min(1).max(6).decimalPlaces(1)
        .build());

    private final Setting<SettingColor> renderColor = sgRender.add(new ColorSetting.Builder()
        .name("color")
        .defaultValue(THMColor)
        .build());

    private final Setting<SettingColor> bedrockColor = sgRender.add(new ColorSetting.Builder()
        .name("bedrock-color")
        .description("Color of the bedrock blocks currently being broken.")
        .defaultValue(new SettingColor(255, 70, 70, 255))
        .visible(mineBedrock::get)
        .build());

    private final Setting<ShapeMode> bedrockShape = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("bedrock-shape")
        .description("How the bedrock being broken is drawn.")
        .defaultValue(ShapeMode.Both)
        .visible(mineBedrock::get)
        .build());

    // ── State ─────────────────────────────────────────────────────────────────

    public static Speedmine INSTANCE;

    private MineContext primary;
    private MineContext secondary;
    public  BlockPos    lastBrokenPos;
    public final Deque<BlockPos> queue = new ArrayDeque<>();

    /** Slot currently held server-side by the silent swap; -1 = server is on the client's real slot. */
    private int heldSlot       = -1;
    private int lastClientSlot = -1;
    private int idleTicks      = 0;

    private BlockPos bedrockPos;
    private boolean  warnedSwingBlocked;

    // ── Constructor ───────────────────────────────────────────────────────────

    public Speedmine() {
        super(THMAddon.PVP, "speedmine", "Grim-safe packet miner with queue and double break.");
        INSTANCE = this;
    }

    // ── Module lifecycle ──────────────────────────────────────────────────────

    @Override
    public void onDeactivate() {
        releaseHeldSlot();
        primary            = null;
        secondary          = null;
        lastBrokenPos      = null;
        bedrockPos         = null;
        warnedSwingBlocked = false;
        queue.clear();
    }

    // ── Events ────────────────────────────────────────────────────────────────

    @EventHandler
    private void onStartBreaking(StartBreakingBlockEvent event) {
        if (mc.world == null || mc.player == null) return;
        // Hands manual clicks straight back to vanilla — auto-mine owns the module
        if (autoMine.get() && autoMineOnly.get()) return;
        BlockState state = mc.world.getBlockState(event.blockPos);
        if (!BlockUtils.canBreak(event.blockPos, state)) return;
        if (outOfRange(event.blockPos)) return;
        event.cancel();
        if (!isMining(event.blockPos)) {
            handleBlockClick(event.blockPos, state);
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.world == null || mc.player == null) return;

        // The player picking a slot themselves resyncs the server to it, dropping our hold
        int clientSlot = ((PlayerInventoryAccessor) mc.player.getInventory()).getSelectedSlot();
        if (clientSlot != lastClientSlot) {
            lastClientSlot = clientSlot;
            heldSlot       = -1;
        }

        tickAutoMine();

        // Auto-rebreak: if the last broken position got a block placed in it, break it again
        if (lastBrokenPos != null
                && autoRebreak.get()
                && primary == null && secondary == null
                && !mc.world.getBlockState(lastBrokenPos).isAir()) {
            MineContext rebreakCtx = new MineContext(lastBrokenPos, mc.world.getBlockState(lastBrokenPos), false);
            // Insta-break blocks need a START (server auto-completes); sendStopPacket is a no-op for them.
            // Non-insta blocks just need a bare STOP to trigger the server's pending completion.
            if (rebreakCtx.instaBreak) {
                sendStart(rebreakCtx);
            } else {
                sendStopPacket(rebreakCtx, silentSwap.get());
            }
            // Don't return — let pruning, finishing, and draining still run this tick
        }

        pruneCompletedOrInvalid();

        if (secondary != null && secondary.progress() >= 1.0) finishBreak(secondary, silentSwap.get());
        if (primary   != null && primary.progress()   >= 1.0) finishBreak(primary,   silentSwap.get());

        drainQueue();

        // Give the slot back only once nothing is mining anymore. The server breaks a block in its
        // own update() when its mining timer completes, which can be several ticks after our STOP —
        // reverting on a fixed short delay races that and the break lands with the wrong item.
        if (!toolHold.get()) releaseHeldSlot();
        else if (primary != null || secondary != null || !queue.isEmpty()) idleTicks = 0;
        else if (heldSlot != -1 && ++idleTicks >= IDLE_RELEASE_TICKS) releaseHeldSlot();
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.world == null || mc.player == null) return;

        for (BlockPos pos : queue) renderBlock(event, pos);
        if (bedrockPos != null) {
            RenderUtilsTHM.renderBlockShape(event, bedrockPos, mc.world.getBlockState(bedrockPos),
                RenderUtilsTHM.withAlpha(bedrockColor.get(), bedrockColor.get().a / 3),
                bedrockColor.get(), bedrockShape.get());
        }
        if (secondary != null) renderMineContext(event, secondary);
        if (primary   != null) renderMineContext(event, primary);

        if (lastBrokenPos != null && autoRebreak.get()
                && !mc.world.getBlockState(lastBrokenPos).isAir()) {
            renderBlock(event, lastBrokenPos);
        }
    }

    // ── Core break logic ──────────────────────────────────────────────────────

    private void handleBlockClick(BlockPos pos, BlockState state) {
        if (isMining(pos)) return;

        boolean canAddSecondary = secondary == null && doubleBreak.get();

        if (primary == null) {
            equipBestTool(state);
            primary = new MineContext(pos, state, true);
            sendStart(primary);
            // Only true insta-break blocks (delta >= 1.0) can be safely finished in the same tick.
            // Non-insta above-threshold blocks still need the server to accumulate progress first.
            if (primary != null && primary.instaBreak) finishBreak(primary, silentSwap.get());
        } else if (canAddSecondary) {
            stopWithTool(primary, silentSwap.get());
            secondary = new MineContext(primary.pos, primary.state, false);
            primary   = new MineContext(pos, state, true);
            sendStart(primary);
            if (primary != null && primary.instaBreak) finishBreak(primary, silentSwap.get());
        } else {
            if (queueEnabled.get() && !queue.contains(pos)) queue.addLast(pos);
        }
    }

    private void pruneCompletedOrInvalid() {
        if (primary   != null && shouldRemove(primary.pos))   primary   = null;
        if (secondary != null && shouldRemove(secondary.pos)) secondary = null;
        queue.removeIf(this::shouldRemove);
    }

    private boolean shouldRemove(BlockPos pos) {
        return mc.world.getBlockState(pos).isAir() || outOfRange(pos);
    }

    private void drainQueue() {
        if (!queueEnabled.get() || queue.isEmpty()) return;

        if (primary == null) {
            BlockPos   pos   = queue.pollFirst();
            BlockState state = mc.world.getBlockState(pos);
            equipBestTool(state);
            primary = new MineContext(pos, state, true);
            sendStart(primary);
            if (primary != null && primary.instaBreak) finishBreak(primary, silentSwap.get());
        } else if (doubleBreak.get() && secondary == null) {
            stopWithTool(primary, silentSwap.get());
            BlockPos   nextPos   = queue.pollFirst();
            BlockState nextState = mc.world.getBlockState(nextPos);
            secondary = new MineContext(primary.pos, primary.state, false);
            primary   = new MineContext(nextPos, nextState, true);
            sendStart(primary);
            if (primary != null && primary.instaBreak) finishBreak(primary, silentSwap.get());
        }
    }

    // ── Packet building ───────────────────────────────────────────────────────

    private void sendStart(MineContext ctx) {
        if (rotate.get()) lookAt(ctx.pos);
        // Server mines with whatever it thinks we hold, so the tool goes in before the START
        if (silentSwap.get()) holdTool(ctx.startSlot);
        if (grimBypass.get()) {
            sendSequencedAction(PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, ctx.pos);
        }
        sendSequencedAction(PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, ctx.pos);
        // For true insta-break blocks (delta >= 1.0), the server accepts an immediate STOP
        // in the same tick — send it now to avoid a 50ms round-trip through the tick handler.
        if (ctx.instaBreak) {
            sendSequencedAction(PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, ctx.pos);
        }
    }

    private void sendStopPacket(MineContext ctx, boolean silent) {
        if (mc.world == null || mc.player == null) return;

        if (silent) holdTool(ctx.startSlot);
        // Vanilla insta-break already sent its START+STOP in sendStart; the held tool is all it needs
        if (!ctx.instaBreak) stopWithTool(ctx, silent);
    }

    /**
     * Sends a STOP for {@code ctx} with the tool it was started with held server-side, so the
     * server resolves the break with that tool in hand. Always sends the STOP.
     */
    private void stopWithTool(MineContext ctx, boolean silent) {
        if (mc.world == null || mc.player == null) return;

        if (silent) holdTool(ctx.startSlot);
        sendSequencedAction(PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, ctx.pos);
    }

    private void finishBreak(MineContext ctx, boolean silent) {
        if (mc.world == null || mc.player == null) return;

        sendStopPacket(ctx, silent);

        boolean clientRemove = (!validateBreak.get() && (removeSlowBlocks.get() || ctx.instaBreak || ctx.aboveThreshold))
                            || (instantClientRemove.get() && (ctx.instaBreak || ctx.aboveThreshold));
        if (clientRemove) {
            mc.world.syncWorldEvent(WorldEvents.BLOCK_BROKEN, ctx.pos, Block.getRawIdFromState(ctx.state));
            mc.world.setBlockState(ctx.pos, Blocks.AIR.getDefaultState(), 3);
        }

        lastBrokenPos = ctx.pos;
        ctx.active    = false;
        if (ctx == primary)        primary   = null;
        else if (ctx == secondary) secondary = null;
    }

    // ── Silent swap ───────────────────────────────────────────────────────────

    /**
     * Silently swaps to the best hotbar tool for {@code state}, runs {@code action},
     * then swaps back — all via sequenced packets so the visual held item never changes.
     *
     * <pre>{@code
     * BlockState state = mc.world.getBlockState(pos);
     * Speedmine.INSTANCE.withSilentTool(state, () -> {
     *     mc.interactionManager.sendSequencedPacket(mc.world, seq ->
     *         new PlayerActionC2SPacket(STOP_DESTROY_BLOCK, pos, dir, seq));
     * });
     * }</pre>
     */
    public void withSilentTool(BlockState state, Runnable action) {
        if (mc.player == null) { action.run(); return; }
        int best = findBestHotbarSlot(state);
        int prev = ((PlayerInventoryAccessor) mc.player.getInventory()).getSelectedSlot();
        boolean swap = best != -1 && best != prev;
        if (swap) sendSequencedUpdateSlot(best);
        action.run();
        if (swap) sendSequencedUpdateSlot(prev);
    }

    // ── Auto Mine ─────────────────────────────────────────────────────────────

    /**
     * Picks blocks to break around nearby enemies, in BlackOut AutoMine's priority order, and
     * hands them to the normal packet miner via {@link #requestBreak(BlockPos)}. Bedrock can't be
     * packet-mined, so it goes down Nuker's vanilla progress+swing path instead.
     */
    private void tickAutoMine() {
        if (!autoMine.get() || !autoMineHeld()) {
            bedrockPos = null;
            return;
        }

        // Warn about blocked swings even when no bedrock is in range yet
        if (mineBedrock.get()) canSwing();

        bedrockPos = null;
        int budget = autoDoubleMine.get() && doubleBreak.get() ? 2 : 1;

        for (BlockPos target : findAutoTargets()) {
            if (mc.world.getBlockState(target).getBlock() == Blocks.BEDROCK) {
                // Bedrock is mined one at a time — it's a vanilla progress bar, not a packet break
                if (bedrockPos == null) mineBedrock(target);
                continue;
            }
            if (budget-- <= 0) break;
            requestBreak(target);
        }
    }

    /** A bound key is hold-to-mine; an unbound one means "always on". */
    private boolean autoMineHeld() {
        return !autoMineBind.get().isSet() || autoMineBind.get().isPressed();
    }

    /** Every valid target around nearby enemies, nearest first. */
    private List<BlockPos> findAutoTargets() {
        List<PlayerEntity> enemies = new ArrayList<>();
        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player || player.isSpectator()) continue;
            if (Friends.get().isFriend(player)) continue;
            if (THMSystem.get().ignoreThmMembers.get() && ThmMembers.isThmMember(player)) continue;
            if (player.distanceTo(mc.player) > enemyRange.get()) continue;
            enemies.add(player);
        }
        if (enemies.isEmpty()) return List.of();

        // Digging an enemy out beats chipping at their surround
        List<BlockPos> targets = new ArrayList<>();
        if (antiPhase.get())    targets.addAll(collect(enemies, this::phaseBlocks));
        if (targets.isEmpty() && antiSurround.get()) targets.addAll(collect(enemies, this::surroundBlocks));
        return targets;
    }

    private List<BlockPos> collect(List<PlayerEntity> enemies, Function<PlayerEntity, List<BlockPos>> candidates) {
        List<BlockPos> out = new ArrayList<>();
        for (PlayerEntity enemy : enemies) {
            for (BlockPos pos : candidates.apply(enemy)) {
                if (pos != null && !outOfRange(pos) && !out.contains(pos)) out.add(pos);
            }
        }
        Comparator<BlockPos> byDistance =
            Comparator.comparingDouble(pos -> mc.player.getEyePos().squaredDistanceTo(pos.toCenterPos()));
        // ponytail: absolute Y, not per-enemy feet level — right for one enemy, good enough for a pile of them
        out.sort(feetFirst.get() ? Comparator.comparingInt(BlockPos::getY).thenComparing(byDistance) : byDistance);
        return out;
    }

    /** Every block the enemy's hitbox overlaps. */
    private List<BlockPos> phaseBlocks(PlayerEntity enemy) {
        List<BlockPos> out = new ArrayList<>();
        for (BlockPos pos : BlockPos.iterate(BlockPos.ofFloored(enemy.getBoundingBox().getMinPos()),
                                             BlockPos.ofFloored(enemy.getBoundingBox().getMaxPos()))) {
            if (mineable(pos)) out.add(pos.toImmutable());
        }
        return out;
    }

    /** Everything boxing the enemy in: surround ring, head-level ring, and the block above their head. */
    private List<BlockPos> surroundBlocks(PlayerEntity enemy) {
        BlockPos feet = feet(enemy);
        BlockPos head = new BlockPos(enemy.getBlockX(), (int) Math.floor(enemy.getBoundingBox().maxY), enemy.getBlockZ());

        List<BlockPos> out = new ArrayList<>();
        for (Direction dir : Direction.Type.HORIZONTAL) {
            out.add(feet.offset(dir));
            out.add(head.offset(dir));
        }
        out.add(head.up());
        return mineableOf(out.toArray(new BlockPos[0]));
    }

    private List<BlockPos> mineableOf(BlockPos... positions) {
        List<BlockPos> out = new ArrayList<>();
        for (BlockPos pos : positions) if (mineable(pos)) out.add(pos);
        return out;
    }

    private BlockPos feet(PlayerEntity enemy) {
        return new BlockPos(enemy.getBlockX(), (int) Math.round(enemy.getY()), enemy.getBlockZ());
    }

    /** True if the block is inside or directly against our own hitbox — breaking it drops or exposes us. */
    private boolean touchesSelf(BlockPos pos) {
        return mc.player.getBoundingBox().expand(1).intersects(
            pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1);
    }

    private boolean mineable(BlockPos pos) {
        BlockState state = mc.world.getBlockState(pos);
        if (state.isAir()) return false;
        if (neverMineOwn.get() && touchesSelf(pos)) return false;
        // Bedrock's hardness is -1, so BlockUtils.canBreak always rejects it — it has to be checked first
        if (state.isOf(Blocks.BEDROCK)) return mineBedrock.get() && canSwing();
        return !bedrockOnly.get() && BlockUtils.canBreak(pos, state);
    }

    // ── Bedrock (vanilla progress + swing, like Nuker — packets can't break it) ───

    /**
     * The server's bedrock plugin needs the hand-swing packet, which PaketLimiter's own default
     * preset puts in its always-block list — so bedrock silently wouldn't break with it enabled.
     */
    private boolean canSwing() {
        PaketLimiter limiter = Modules.get().get(PaketLimiter.class);
        boolean blocked = limiter != null && limiter.isActive() && limiter.limit.get() != 0
            && limiter.alwaysBlock.get().contains(HandSwingC2SPacket.class);
        if (!blocked) {
            warnedSwingBlocked = false;
            return true;
        }

        if (!warnedSwingBlocked) {
            warnedSwingBlocked = true;
            warning("Bedrock needs hand swings, but Paket Limiter is blocking them — remove HandSwingC2SPacket from its always-block list.");
        }
        return false;
    }

    /**
     * Vanilla break: bedrock is a server-side progress bar driven by holding the dig, not something a
     * START/STOP packet pair can pop, so it has to go through {@code updateBlockBreakingProgress} —
     * which tracks exactly one position, hence one block at a time.
     *
     * No tool swap — bedrock breaks at the same speed with anything, so whatever is held works.
     */
    private void mineBedrock(BlockPos pos) {
        if (mc.interactionManager == null) return;

        bedrockPos = pos;
        if (bedrockRotate.get()) lookAt(pos);

        mc.interactionManager.updateBlockBreakingProgress(pos, RangeUtils.nearestFace(pos));
        mc.player.swingHand(Hand.MAIN_HAND);
    }

    // ── Silent tool hold ──────────────────────────────────────────────────────

    /** Ticks with nothing left to mine before the slot is handed back to the client's real one. */
    private static final int IDLE_RELEASE_TICKS = 3;

    /** Silently holds {@code slot} server-side (no visual change), keeping it until mining goes idle. */
    private void holdTool(int slot) {
        if (slot < 0 || mc.player == null) return;

        int serverSlot = heldSlot != -1
            ? heldSlot
            : ((PlayerInventoryAccessor) mc.player.getInventory()).getSelectedSlot();
        if (serverSlot != slot) sendSequencedUpdateSlot(slot);
        heldSlot = slot;
    }

    private void releaseHeldSlot() {
        if (heldSlot != -1 && mc.player != null) {
            sendSequencedUpdateSlot(((PlayerInventoryAccessor) mc.player.getInventory()).getSelectedSlot());
        }
        heldSlot  = -1;
        idleTicks = 0;
    }

    // ── Sequenced packet helpers ──────────────────────────────────────────────

    private void sendSequencedAction(PlayerActionC2SPacket.Action action, BlockPos pos) {
        if (mc.interactionManager == null || mc.world == null) return;
        ((ClientPlayerInteractionManagerTHMAccessor) mc.interactionManager)
            .thm$sendSequencedPacket(mc.world, seq -> new PlayerActionC2SPacket(action, pos, Direction.DOWN, seq));
    }

    private void sendSequencedUpdateSlot(int slot) {
        if (mc.interactionManager == null || mc.world == null || slot < 0) return;
        ((ClientPlayerInteractionManagerTHMAccessor) mc.interactionManager)
            .thm$sendSequencedPacket(mc.world, seq -> new UpdateSelectedSlotC2SPacket(slot));
    }

    // ── Tool selection ────────────────────────────────────────────────────────

    private void equipBestTool(BlockState state) {
        if (silentSwap.get()) return;
        int slot = findBestHotbarSlot(state);
        if (slot != -1 && mc.player != null) {
            ((PlayerInventoryAccessor) mc.player.getInventory()).setSelectedSlot(slot);
        }
    }

    private int findBestHotbarSlot(BlockState state) {
        if (mc.player == null) return -1;
        int   best      = -1;
        float bestSpeed = -1;
        for (int i = 0; i < 9; i++) {
            float s = mc.player.getInventory().getStack(i).getMiningSpeedMultiplier(state);
            if (s > bestSpeed) { bestSpeed = s; best = i; }
        }
        return best;
    }

    // ── Util ─────────────────────────────────────────────────────────────────

    public void requestBreak(BlockPos pos) {
        if (mc.world == null || mc.player == null) return;
        BlockState state = mc.world.getBlockState(pos);
        if (state.isAir()) return;
        if (!isMining(pos)) handleBlockClick(pos, state);
    }

    public boolean isMining(BlockPos pos) {
        return (primary   != null && primary.pos.equals(pos))
            || (secondary != null && secondary.pos.equals(pos))
            || queue.contains(pos);
    }

    /** Server-side only look at {@code pos} — the camera doesn't move. */
    private void lookAt(BlockPos pos) {
        Rotations.rotate(Rotations.getYaw(pos), Rotations.getPitch(pos));
    }

    public boolean outOfRange(BlockPos pos) {
        return !RangeUtils.isInRange(range.get(), pos);
    }

    // ── Rendering ─────────────────────────────────────────────────────────────

    private void renderMineContext(Render3DEvent event, MineContext ctx) {
        RenderUtilsTHM.renderBlockShapeScaled(event, ctx.pos, ctx.state, ctx.progress(),
            renderColor.get(), renderColor.get(), ShapeMode.Lines);
    }

    private void renderBlock(Render3DEvent event, BlockPos pos) {
        RenderUtilsTHM.renderBlockShape(event, pos, mc.world.getBlockState(pos),
            renderColor.get(), renderColor.get(), ShapeMode.Lines);
    }

    // ── MineContext ───────────────────────────────────────────────────────────

    public class MineContext {

        public final BlockPos   pos;
        public final BlockState state;
        public final long       startMs;
        public final float      hardness;
        public final boolean    isPrimary;
        public final boolean    instaBreak;
        public final boolean    aboveThreshold;
        /** Hotbar slot holding the tool this block was started with; -1 if none. */
        public final int        startSlot;
        public boolean          active = true;

        public MineContext(BlockPos pos, BlockState state, boolean isPrimary) {
            this.pos            = pos.toImmutable();
            this.state          = state;
            this.hardness       = mc.world != null ? state.getHardness(mc.world, pos) : 0;
            this.isPrimary      = isPrimary;
            this.startSlot      = findBestHotbarSlot(state);
            this.startMs        = System.currentTimeMillis();
            float delta         = calcDelta();
            this.instaBreak     = delta >= 1.0f;
            this.aboveThreshold = delta >= breakThreshold.get().floatValue();
        }

        public double progress() {
            if (mc.player == null || mc.world == null || hardness < 0) return 0;
            float perTick = calcDelta();
            if (perTick <= 0) return Double.MAX_VALUE;
            float elapsed = Math.max((System.currentTimeMillis() - startMs) / 50f + 1f, 1f);
            float target  = isPrimary ? breakThreshold.get().floatValue() : 1.0f;
            return Math.min((perTick * elapsed) / target, 1.0);
        }

        private float calcDelta() {
            if (mc.player == null || mc.world == null) return 0;
            if (hardness <= 0) return hardness == 0f ? Float.MAX_VALUE : 0f;

            int       bestSlot = findBestHotbarSlot(state);
            ItemStack tool     = mc.player.getInventory().getStack(bestSlot < 0 ? 0 : bestSlot);

            int divisor = state.isToolRequired() && !tool.isSuitableFor(state) ? 100 : 30;

            float speed = tool.getMiningSpeedMultiplier(state);

            if (!tool.isEmpty() && speed > 1.0f) {
                int effLevel = 0;
                for (var entry : tool.getEnchantments().getEnchantmentEntries()) {
                    if (entry.getKey().matchesKey(Enchantments.EFFICIENCY)) {
                        effLevel = entry.getIntValue();
                        break;
                    }
                }
                if (effLevel > 0) speed += effLevel * effLevel + 1;
            }

            if (StatusEffectUtil.hasHaste(mc.player)) {
                speed *= 1.0f + (StatusEffectUtil.getHasteAmplifier(mc.player) + 1) * 0.2f;
            }

            if (mc.player.hasStatusEffect(StatusEffects.MINING_FATIGUE)) {
                float penalty = switch (mc.player.getStatusEffect(StatusEffects.MINING_FATIGUE).getAmplifier()) {
                    case 0  -> 0.3f;
                    case 1  -> 0.09f;
                    case 2  -> 0.0027f;
                    default -> 8.1e-4f;
                };
                speed *= penalty;
            }

            if (mc.player.isSubmergedIn(FluidTags.WATER)) {
                speed *= (float) mc.player.getAttributeValue(EntityAttributes.SUBMERGED_MINING_SPEED);
            }

            if (!mc.player.isOnGround()) speed /= 5.0f;

            return speed / hardness / divisor;
        }
    }
}
