/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.modules;

import meteordevelopment.meteorclient.events.entity.player.StartBreakingBlockEvent;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
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
import meteordevelopment.meteorclient.utils.world.TickRate;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectUtil;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.state.BlockState;
import xyz.thm.addon.THMAddon;
import xyz.thm.addon.mixin.accessor.ClientLevelPredictionAccessor;
import xyz.thm.addon.mixin.accessor.ClientPlayerInteractionManagerTHMAccessor;
import xyz.thm.addon.mixin.accessor.PlayerInventoryAccessor;
import xyz.thm.addon.settings.DescribedOption;
import xyz.thm.addon.system.THMSystem;
import xyz.thm.addon.utils.InventoryManager;
import xyz.thm.addon.utils.RangeUtils;
import xyz.thm.addon.utils.RenderUtilsTHM;
import xyz.thm.addon.utils.ThmMembers;

import java.util.*;
import java.util.function.Function;

import static xyz.thm.addon.THMAddon.THMColor;

//Thank you very much mushek
/** Packet miner with server-timed progress and sequenced client prediction. */
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
        .description("Mining progress needed before sending STOP.")
        .defaultValue(0.7).min(0.1).max(1.0).decimalPlaces(2)
        .build());

    public final Setting<Boolean> clientPrediction = sgMine.add(new BoolSetting.Builder()
        .name("client-prediction")
        .description("Predict completed breaks locally, including rebreaks.")
        .defaultValue(true)
        .build());

    public final Setting<Boolean> autoRebreak = sgMine.add(new BoolSetting.Builder()
        .name("auto-rebreak")
        .description("Rebreak the last position if a block reappears there.")
        .defaultValue(true)
        .build());

    public final Setting<RebreakMode> rebreakMode = sgMine.add(new EnumSetting.Builder<RebreakMode>()
        .name("rebreak-mode")
        .description("How instant rebreaks wait for server confirmation.")
        .defaultValue(RebreakMode.Strong)
        .visible(autoRebreak::get)
        .build());

    public final Setting<RebreakTrigger> rebreakTrigger = sgMine.add(new EnumSetting.Builder<RebreakTrigger>()
        .name("rebreak-trigger")
        .description("Detect replacements from server packets or the client world.")
        .defaultValue(RebreakTrigger.onPacket)
        .visible(autoRebreak::get)
        .build());

    private final Setting<Boolean> debugRebreak = sgMine.add(new BoolSetting.Builder()
        .name("debug-rebreak")
        .description("Show rebreak attempts per second in chat and module info.")
        .defaultValue(false)
        .onChanged(enabled -> resetRebreakMonitor())
        .build());

    public final Setting<Boolean> silentSwap = sgMine.add(new BoolSetting.Builder()
        .name("silent-swap")
        .description("Swap to the best tool via packet without visually changing your held item.")
        .defaultValue(true)
        .build());

    public final Setting<SwapMode> swapMode = sgMine.add(new EnumSetting.Builder<SwapMode>()
        .name("swap-mode")
        .description("Swap timing for instant breaks and rebreaks.")
        .defaultValue(SwapMode.SameTick)
        .visible(silentSwap::get)
        .onChanged(mode -> releaseHeldSlot())
        .build());

    public final Setting<Boolean> tpsSync = sgMine.add(new BoolSetting.Builder()
        .name("tps-sync")
        .description("Match mining progress and start rate to server TPS.")
        .defaultValue(false)
        .build());

    public final Setting<Boolean> multitask = sgMine.add(new BoolSetting.Builder()
        .name("multitask")
        .description("Continue mining while using items.")
        .defaultValue(false)
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
    private boolean lastBreakConfirmed;
    private boolean rebreakPending;
    private boolean bypassConfirmed;
    private long rebreakDeadlineMs;
    private BlockState packetReplacement;
    private long rebreakMonitorStartNs;
    private int rebreakCount;
    private String rebreakRate = "0.0";
    private BlockPos lastStartedPos;
    public final Deque<BlockPos> queue = new ArrayDeque<>();

    /** Tool slot owned by this miner; InventoryManager tracks the actual server slot. */
    private int heldSlot       = -1;
    private int idleTicks      = 0;
    private final List<PendingBreak> pendingBreaks = new ArrayList<>(3);
    private long lastStartCreditMs;
    private double startCredit = 1;
    private long clientTick;
    private long lastMiningActionTick;

    private BlockPos bedrockPos;
    private boolean  warnedSwingBlocked;

    // ── Constructor ───────────────────────────────────────────────────────────

    public Speedmine() {
        super(THMAddon.PVP, "speedmine", "Grim-safe packet miner with queue and double break.");
        INSTANCE = this;
    }

    // ── Module lifecycle ──────────────────────────────────────────────────────

    @Override
    public Module fromTag(CompoundTag tag) {
        return super.fromTag(migrateSettings(tag));
    }

    static CompoundTag migrateSettings(CompoundTag tag) {
        CompoundTag migrated = tag.copy();
        for (Tag entry : migrated.getCompoundOrEmpty("settings").getListOrEmpty("groups")) {
            if (!(entry instanceof CompoundTag group) || !group.getStringOr("name", "").equals("General")) continue;
            var values = group.getListOrEmpty("settings");
            CompoundTag prediction = null;
            CompoundTag swap = null;
            Boolean legacyHold = null;
            boolean enabled = false;
            for (int i = values.size() - 1; i >= 0; i--) {
                if (!(values.get(i) instanceof CompoundTag setting)) continue;
                String name = setting.getStringOr("name", "");
                switch (name) {
                    case "client-prediction" -> {
                        prediction = setting;
                        enabled |= setting.getBooleanOr("value", false);
                    }
                    case "swap-mode" -> swap = setting;
                    case "tool-hold" -> legacyHold = setting.getBooleanOr("value", true);
                    case "instant-client-remove" -> enabled |= setting.getBooleanOr("value", false);
                    case "validate-break" -> enabled |= !setting.getBooleanOr("value", true);
                }
                if (name.equals("instant-client-remove") || name.equals("validate-break") || name.equals("remove-slow-blocks") || name.equals("tool-hold")) values.remove(i);
            }
            if (enabled) {
                if (prediction == null) {
                    prediction = new CompoundTag();
                    prediction.putString("name", "client-prediction");
                    values.add(prediction);
                }
                prediction.putBoolean("value", true);
            }
            if (swap == null && legacyHold != null) {
                swap = new CompoundTag();
                swap.putString("name", "swap-mode");
                swap.putString("value", legacyHold ? SwapMode.ToolHold.toString() : SwapMode.SameTick.toString());
                values.add(swap);
            }
        }
        return migrated;
    }

    @Override
    public void onActivate() {
        lastStartCreditMs = System.currentTimeMillis();
        startCredit = 1;
        clientTick = 0;
        lastMiningActionTick = 0;
        resetRebreakMonitor();
    }

    @Override
    public void onDeactivate() {
        releaseHeldSlot();
        primary            = null;
        secondary          = null;
        lastBrokenPos      = null;
        lastBreakConfirmed = false;
        rebreakPending = false;
        bypassConfirmed = false;
        packetReplacement = null;
        resetRebreakMonitor();
        lastStartedPos = null;
        bedrockPos         = null;
        warnedSwingBlocked = false;
        pendingBreaks.clear();
        queue.clear();
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        heldSlot = -1;
        onDeactivate();
    }

    /** Called on the client thread before a server update changes the world. */
    public void onServerBlockUpdate(ClientLevel level, BlockPos pos, BlockState state) {
        if (!isActive() || level != mc.level || mc.player == null) return;
        confirmBreak(pos, state);
        if (!pos.equals(lastBrokenPos)) return;
        packetReplacement = state.isAir() ? null : state;
        if (rebreakTrigger.get() == RebreakTrigger.onPacket && packetReplacement != null) {
            tryRebreak(pos, packetReplacement, true);
        }
    }

    private void confirmBreak(BlockPos pos, BlockState state) {
        if (!state.isAir()) return;
        if (pos.equals(lastStartedPos) && (pos.equals(lastBrokenPos) || (primary != null && pos.equals(primary.pos)))) {
            lastBrokenPos = pos.immutable();
            lastBreakConfirmed = true;
            if (rebreakPending) bypassConfirmed = true;
            rebreakPending = false;
        }
        pendingBreaks.removeIf(pending -> pending.pos().equals(pos));
        if (primary != null && pos.equals(primary.pos)) primary = null;
        if (secondary != null && pos.equals(secondary.pos)) secondary = null;
    }

    // ── Events ────────────────────────────────────────────────────────────────

    @EventHandler
    private void onStartBreaking(StartBreakingBlockEvent event) {
        if (mc.level == null || mc.player == null) return;
        // Hands manual clicks straight back to vanilla — auto-mine owns the module
        if (autoMine.get() && autoMineOnly.get()) return;
        BlockState state = mc.level.getBlockState(event.blockPos);
        if (!BlockUtils.canBreak(event.blockPos, state)) return;
        if (outOfRange(event.blockPos)) return;
        event.cancel();
        if (!isMining(event.blockPos)) {
            handleBlockClick(event.blockPos, state);
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.level == null || mc.player == null) return;

        clientTick++;
        long now = System.currentTimeMillis();
        startCredit = replenishStartCredit(startCredit, now - lastStartCreditMs, effectiveTps());
        lastStartCreditMs = now;
        pendingBreaks.removeIf(pending -> now >= pending.deadlineMs());
        if (rebreakPending && now >= rebreakDeadlineMs) rebreakPending = false;
        updateRebreakMonitor();

        InventoryManager inventory = InventoryManager.getInstance();
        if (shouldPauseMining()) {
            releaseHeldSlot();
            if (primary != null) primary.lastProgressMs = now;
            if (secondary != null) secondary.lastProgressMs = now;
            return;
        }
        int validationSlot = validationToolSlot();
        if (!silentSwap.get()) {
            releaseHeldSlot();
            if (validationSlot != -1) {
                ((PlayerInventoryAccessor) mc.player.getInventory()).setSelectedSlot(validationSlot);
                if (inventory.getServerSlot() != validationSlot) inventory.setSlotForced(validationSlot);
            }
        } else if (validationSlot != -1) holdTool(validationSlot, false);
        else if (swapMode.get() == SwapMode.SameTick) releaseHeldSlot();
        else if (swapMode.get() != SwapMode.EndOfTick) {
            if (primary != null) holdTool(primary.startSlot, false);
            else if (secondary != null) holdTool(secondary.startSlot, false);
            else if (heldSlot != -1 && !pendingBreaks.isEmpty()) holdTool(heldSlot, false);
        }

        tickAutoMine();

        if (lastBrokenPos != null) {
            boolean packet = rebreakTrigger.get() == RebreakTrigger.onPacket;
            BlockState replacement = packet ? packetReplacement : mc.level.getBlockState(lastBrokenPos);
            if (replacement != null) tryRebreak(lastBrokenPos, replacement, packet);
        }

        pruneCompletedOrInvalid();

        if (secondary != null && secondary.progress() >= 1.0) finishBreak(secondary, silentSwap.get());
        if (primary != null && primary.progress() >= 1.0) finishBreak(primary, silentSwap.get());

        drainQueue();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    private void onTickPost(TickEvent.Post event) {
        if (mc.player == null || mc.level == null || heldSlot == -1) return;
        if (!silentSwap.get() || shouldPauseMining()) {
            releaseHeldSlot();
            return;
        }
        boolean mining = primary != null || secondary != null || !queue.isEmpty();
        boolean replacement = autoRebreak.get() && rebreakMode.get() != RebreakMode.Off && lastBreakConfirmed && lastBrokenPos != null
            && !outOfRange(lastBrokenPos) && BlockUtils.canBreak(lastBrokenPos, mc.level.getBlockState(lastBrokenPos));
        boolean pending = !pendingBreaks.isEmpty();
        idleTicks = mining || pending ? 0 : Math.min(IDLE_RELEASE_TICKS, idleTicks + 1);
        // Check after this tick's placements before releasing the keep-mode hold.
        SwapMode mode = validationToolSlot() != -1 ? SwapMode.ToolHold : swapMode.get();
        if (mode.shouldRelease(clientTick - lastMiningActionTick, mining, replacement, pending, idleTicks)) releaseHeldSlot();
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.level == null || mc.player == null) return;

        for (BlockPos pos : queue) renderBlock(event, pos);
        if (bedrockPos != null) {
            RenderUtilsTHM.renderBlockShape(event, bedrockPos, mc.level.getBlockState(bedrockPos),
                RenderUtilsTHM.withAlpha(bedrockColor.get(), bedrockColor.get().a / 3),
                bedrockColor.get(), bedrockShape.get());
        }
        if (secondary != null) renderMineContext(event, secondary);
        if (primary   != null) renderMineContext(event, primary);

        if (lastBrokenPos != null && autoRebreak.get()
                && !mc.level.getBlockState(lastBrokenPos).isAir()) {
            renderBlock(event, lastBrokenPos);
        }
    }

    // ── Core break logic ──────────────────────────────────────────────────────

    private void tryRebreak(BlockPos pos, BlockState state, boolean packet) {
        if (mc.gameMode == null || !autoRebreak.get() || !pos.equals(lastBrokenPos)
            || !rebreakMode.get().canRebreak(lastBreakConfirmed, rebreakPending, bypassConfirmed)
            || primary != null || secondary != null || hasPendingNormalBreak()
            || outOfRange(pos) || state.isAir() || !BlockUtils.canBreak(pos, state)) return;
        if (shouldPauseMining()) return;
        boolean strong = rebreakMode.get().isStrong(bypassConfirmed);
        if (!strong && !canStart()) return;

        MineContext ctx = new MineContext(pos, state, false);
        ctx.rebreak = true;
        ctx.packetRebreak = packet;
        if (!strong && tpsSync.get()) startCredit = Math.max(0, startCredit - 1);
        if (rotate.get()) lookAt(pos);
        finishBreak(ctx, silentSwap.get());
    }

    private void handleBlockClick(BlockPos pos, BlockState state) {
        if (isMining(pos)) return;
        if (autoRebreak.get() && rebreakMode.get() != RebreakMode.Off && lastBreakConfirmed && pos.equals(lastBrokenPos)) {
            boolean packet = rebreakTrigger.get() == RebreakTrigger.onPacket;
            if (!packet || packetReplacement != null) tryRebreak(pos, packet ? packetReplacement : state, packet);
            return;
        }
        if (shouldPauseMining()) return;
        if (hasPendingNormalBreak()) {
            if (queueEnabled.get()) queue.addLast(pos.immutable());
            return;
        }

        if (!canStart()) {
            queue.addLast(pos.immutable());
            return;
        }

        boolean canAddSecondary = secondary == null && doubleBreak.get();

        if (primary == null) {
            primary = new MineContext(pos, state, true);
            sendStart(primary);
        } else if (canAddSecondary) {
            stopWithTool(primary, silentSwap.get());
            secondary = primary;
            secondary.isPrimary = false;
            primary   = new MineContext(pos, state, true);
            sendStart(primary);
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
        return mc.level.getBlockState(pos).isAir() || outOfRange(pos);
    }

    private void drainQueue() {
        if (hasPendingNormalBreak() || queue.isEmpty() || !canStart()) return;

        if (primary == null || (doubleBreak.get() && secondary == null)) {
            BlockPos pos = queue.pollFirst();
            handleBlockClick(pos, mc.level.getBlockState(pos));
        }
    }

    // ── Packet building ───────────────────────────────────────────────────────

    private void sendStart(MineContext ctx) {
        rebreakPending = false;
        bypassConfirmed = false;
        packetReplacement = null;
        lastBreakConfirmed = false;
        lastStartedPos = ctx.pos;
        if (tpsSync.get()) startCredit = Math.max(0, startCredit - 1);
        if (rotate.get()) lookAt(ctx.pos);
        withMiningTool(ctx, silentSwap.get(), () -> {
            if (grimBypass.get()) {
                sendSequencedAction(ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK, ctx.pos);
            }
            sendSequencedAction(ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, ctx.pos);
            if (ctx.instaBreak) finishBreak(ctx, silentSwap.get());
        });
    }

    /** STOP must use the same tool as START. */
    private void stopWithTool(MineContext ctx, boolean silent) {
        if (mc.level == null || mc.player == null) return;
        withMiningTool(ctx, silent, () -> sendSequencedAction(ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK, ctx.pos));
    }

    private void finishBreak(MineContext ctx, boolean silent) {
        if (mc.level == null || mc.player == null || mc.gameMode == null) return;

        boolean clientRemove = clientPrediction.get();
        withMiningTool(ctx, silent, () ->
            ((ClientPlayerInteractionManagerTHMAccessor) mc.gameMode).thm$sendSequencedPacket(mc.level, sequence -> {
                // Prediction must share the STOP sequence so rejected breaks restore the server state.
                if (clientRemove && ctx.packetRebreak) {
                    var prediction = ((ClientLevelPredictionAccessor) mc.level).thm$getBlockStatePredictionHandler();
                    // The replacement may still be air locally; retain the packet state for rollback.
                    prediction.retainKnownServerState(ctx.pos, ctx.state, mc.player);
                    prediction.updateKnownServerState(ctx.pos, ctx.state);
                }
                if (clientRemove) mc.level.setBlock(ctx.pos, Blocks.AIR.defaultBlockState(), 3);
                return new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK,
                    ctx.pos, Direction.DOWN, sequence);
            }));

        if (ctx.requiresValidation() || (silent && swapMode.get() == SwapMode.ToolHold)) {
            double ticksPerMs = serverTicks(1, TickRate.INSTANCE.getTickRate()) * ctx.calcDelta();
            long deadline = System.currentTimeMillis() + (long) Math.clamp(
                ticksPerMs > 0 ? 1 / ticksPerMs + 1000 : 30000, 2000, 30000);
            // At most two normal validations and the latest instant/rebreak hold.
            pendingBreaks.removeIf(pending -> !pending.normal() || pending.pos().equals(ctx.pos));
            pendingBreaks.add(new PendingBreak(ctx.pos, ctx.startSlot, ctx.requiresValidation(), !ctx.isPrimary, deadline));
        }

        if (clientRemove) {
            mc.level.levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, ctx.pos, Block.getId(ctx.state));
        }

        if (!ctx.pos.equals(lastBrokenPos)) lastBreakConfirmed = false;
        lastBrokenPos = ctx.pos;
        if (ctx.rebreak) {
            rebreakPending = true;
            rebreakDeadlineMs = System.currentTimeMillis() + 2000;
            packetReplacement = null;
            if (debugRebreak.get()) rebreakCount++;
        }
        ctx.active    = false;
        if (ctx == primary)        primary   = null;
        else if (ctx == secondary) secondary = null;
        if (silent && hasPendingNormalBreak()) holdTool(validationToolSlot(), false);
    }

    // ── Silent swap ───────────────────────────────────────────────────────────

    private void withMiningTool(MineContext ctx, boolean silent, Runnable action) {
        SwapMode mode = swapMode.get().forBreak(ctx.instaBreak, ctx.rebreak);
        if (validationToolSlot() != -1) mode = SwapMode.ToolHold;
        if (silent && mode == SwapMode.SameTick) {
            releaseHeldSlot();
            withSilentTool(ctx.state, action);
        } else {
            if (silent) holdTool(ctx.startSlot, true);
            else equipBestTool(ctx.state);
            action.run();
        }
        int validationSlot = validationToolSlot();
        if (validationSlot != -1) {
            if (silent) holdTool(validationSlot, false);
            else {
                ((PlayerInventoryAccessor) mc.player.getInventory()).setSelectedSlot(validationSlot);
                InventoryManager inventory = InventoryManager.getInstance();
                if (inventory.getServerSlot() != validationSlot) inventory.setSlotForced(validationSlot);
            }
        }
        lastMiningActionTick = clientTick;
    }

    /** Restores the previous server slot, including an existing silent hold. */
    public void withSilentTool(BlockState state, Runnable action) {
        if (mc.player == null) { action.run(); return; }
        int best = findBestHotbarSlot(state);
        InventoryManager inventory = InventoryManager.getInstance();
        int prev = inventory.getServerSlot();
        boolean swap = best != -1 && best != prev;
        if (swap) inventory.setSlotForced(best);
        try {
            action.run();
        } finally {
            if (swap) inventory.setSlotForced(prev);
        }
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

        List<BlockPos> targets = findAutoTargets();
        // Auto-mine outranks the queue: drop the backlog instead of mining it first
        if (!targets.isEmpty()) queue.clear();

        for (BlockPos target : targets) {
            if (mc.level.getBlockState(target).getBlock() == Blocks.BEDROCK) {
                // Bedrock is mined one at a time — it's a vanilla progress bar, not a packet break
                if (bedrockPos == null) mineBedrock(target);
                continue;
            }
            if (budget-- <= 0) break;
            // Both break slots busy: skip rather than queue — auto targets never enter the queue
            if (primary != null && (secondary != null || !doubleBreak.get())) continue;
            requestBreak(target);
        }
    }

    /** A bound key is hold-to-mine; an unbound one means "always on". */
    private boolean autoMineHeld() {
        return !autoMineBind.get().isSet() || autoMineBind.get().isPressed();
    }

    /** Every valid target around nearby enemies, nearest first. */
    private List<BlockPos> findAutoTargets() {
        List<Player> enemies = new ArrayList<>();
        for (Player player : mc.level.players()) {
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

    private List<BlockPos> collect(List<Player> enemies, Function<Player, List<BlockPos>> candidates) {
        List<BlockPos> out = new ArrayList<>();
        for (Player enemy : enemies) {
            for (BlockPos pos : candidates.apply(enemy)) {
                if (pos != null && !outOfRange(pos) && !out.contains(pos)) out.add(pos);
            }
        }
        Comparator<BlockPos> byDistance =
            Comparator.comparingDouble(pos -> mc.player.getEyePosition().distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(pos)));
        // ponytail: absolute Y, not per-enemy feet level — right for one enemy, good enough for a pile of them
        out.sort(feetFirst.get() ? Comparator.<BlockPos>comparingInt(BlockPos::getY).thenComparing(byDistance) : byDistance);
        return out;
    }

    /** Every block the enemy's hitbox overlaps. */
    private List<BlockPos> phaseBlocks(Player enemy) {
        List<BlockPos> out = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(enemy.getBoundingBox().getMinPosition()),
                                             BlockPos.containing(enemy.getBoundingBox().getMaxPosition()))) {
            if (mineable(pos)) out.add(pos.immutable());
        }
        return out;
    }

    /** Everything boxing the enemy in: surround ring, head-level ring, and the block above their head. */
    private List<BlockPos> surroundBlocks(Player enemy) {
        BlockPos feet = feet(enemy);
        BlockPos head = new BlockPos(enemy.getBlockX(), (int) Math.floor(enemy.getBoundingBox().maxY), enemy.getBlockZ());

        List<BlockPos> out = new ArrayList<>();
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            out.add(feet.relative(dir));
            out.add(head.relative(dir));
        }
        out.add(head.above());
        return mineableOf(out.toArray(new BlockPos[0]));
    }

    private List<BlockPos> mineableOf(BlockPos... positions) {
        List<BlockPos> out = new ArrayList<>();
        for (BlockPos pos : positions) if (mineable(pos)) out.add(pos);
        return out;
    }

    private BlockPos feet(Player enemy) {
        return new BlockPos(enemy.getBlockX(), (int) Math.round(enemy.getY()), enemy.getBlockZ());
    }

    /** True if the block is inside or directly against our own hitbox — breaking it drops or exposes us. */
    private boolean touchesSelf(BlockPos pos) {
        return mc.player.getBoundingBox().inflate(1).intersects(
            pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1);
    }

    private boolean mineable(BlockPos pos) {
        BlockState state = mc.level.getBlockState(pos);
        if (state.isAir()) return false;
        if (neverMineOwn.get() && touchesSelf(pos)) return false;
        // Bedrock's hardness is -1, so BlockUtils.canBreak always rejects it — it has to be checked first
        if (state.is(Blocks.BEDROCK)) return mineBedrock.get() && canSwing();
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
            && limiter.alwaysBlock.get().contains(ServerboundSwingPacket.class);
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
        if (mc.gameMode == null) return;

        bedrockPos = pos;
        if (bedrockRotate.get()) lookAt(pos);

        mc.gameMode.continueDestroyBlock(pos, RangeUtils.nearestFace(pos));
        mc.player.swing(InteractionHand.MAIN_HAND);
    }

    // ── Silent tool hold ──────────────────────────────────────────────────────

    /** Ticks with nothing left to mine before the slot is handed back to the client's real one. */
    private static final int IDLE_RELEASE_TICKS = 3;

    /** A delayed secondary completes in a server tick, using the tool held then. */
    private int validationToolSlot() {
        return validationToolSlot(
            secondary != null && secondary.requiresValidation() ? secondary.startSlot : -1,
            pendingBreaks, primary != null && primary.requiresValidation() ? primary.startSlot : -1);
    }

    static int validationToolSlot(int secondarySlot, List<PendingBreak> pendingBreaks, int primarySlot) {
        for (PendingBreak pending : pendingBreaks) {
            if (pending.normal() && pending.secondary()) return pending.toolSlot();
        }
        if (secondarySlot != -1) return secondarySlot;
        for (PendingBreak pending : pendingBreaks) {
            if (pending.normal()) return pending.toolSlot();
        }
        return primarySlot;
    }

    private boolean hasPendingNormalBreak() {
        return pendingBreaks.stream().anyMatch(PendingBreak::normal);
    }

    record PendingBreak(BlockPos pos, int toolSlot, boolean normal, boolean secondary, long deadlineMs) {}

    /** Mining packets force selection; tick refreshes can reuse the tracked slot. */
    private void holdTool(int slot, boolean force) {
        if (slot < 0 || mc.player == null) return;

        InventoryManager inventory = InventoryManager.getInstance();
        boolean retained = (swapMode.get() == SwapMode.Keep || swapMode.get() == SwapMode.EndOfTick) && heldSlot == slot;
        if ((force && !retained) || inventory.getServerSlot() != slot) inventory.setSlotForced(slot);
        heldSlot = slot;
    }

    private void releaseHeldSlot() {
        InventoryManager inventory = InventoryManager.getInstance();
        if (heldSlot != -1 && mc.player != null && inventory.getServerSlot() == heldSlot) {
            inventory.setSlotForced(mc.player.getInventory().getSelectedSlot());
        }
        heldSlot  = -1;
        idleTicks = 0;
    }

    // ── Sequenced packet helpers ──────────────────────────────────────────────

    private void sendSequencedAction(ServerboundPlayerActionPacket.Action action, BlockPos pos) {
        if (mc.gameMode == null || mc.level == null) return;
        ((ClientPlayerInteractionManagerTHMAccessor) mc.gameMode)
            .thm$sendSequencedPacket(mc.level, seq -> new ServerboundPlayerActionPacket(action, pos, Direction.DOWN, seq));
    }

    // ── Tool selection ────────────────────────────────────────────────────────

    private void equipBestTool(BlockState state) {
        if (silentSwap.get()) return;
        int slot = findBestHotbarSlot(state);
        if (slot != -1 && mc.player != null) {
            ((PlayerInventoryAccessor) mc.player.getInventory()).setSelectedSlot(slot);
            InventoryManager.getInstance().setSlotForced(slot);
        }
    }

    private int findBestHotbarSlot(BlockState state) {
        if (mc.player == null) return -1;
        int   best      = -1;
        float bestSpeed = -1;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            float s = stack.getDestroySpeed(state);
            if (s > 1) {
                for (var entry : stack.getEnchantments().entrySet()) {
                    if (entry.getKey().is(Enchantments.EFFICIENCY)) {
                        int level = entry.getIntValue();
                        s += level * level + 1;
                        break;
                    }
                }
            }
            if (state.requiresCorrectToolForDrops() && !stack.isCorrectToolForDrops(state)) s /= 100;
            else s /= 30;
            if (s > bestSpeed) { bestSpeed = s; best = i; }
        }
        return best;
    }

    // ── Util ─────────────────────────────────────────────────────────────────

    private boolean shouldPauseMining() {
        InventoryManager inventory = InventoryManager.getInstance();
        return shouldPauseMining(multitask.get(), mc.player.isUsingItem(), inventory.isEating(), inventory.getCurrentPriority());
    }

    static boolean shouldPauseMining(boolean multitask, boolean usingItem, boolean eating, int priority) {
        return (!multitask && (usingItem || eating))
            || (priority > InventoryManager.Priority.NORMAL && (!multitask || priority != InventoryManager.Priority.EATING));
    }

    private void resetRebreakMonitor() {
        rebreakMonitorStartNs = System.nanoTime();
        rebreakCount = 0;
        rebreakRate = "0.0";
    }

    private void updateRebreakMonitor() {
        if (!debugRebreak.get()) return;
        long now = System.nanoTime();
        long elapsed = now - rebreakMonitorStartNs;
        if (elapsed < 1_000_000_000L) return;
        rebreakRate = String.format(Locale.ROOT, "%.1f", rebreakCount * 1_000_000_000.0 / elapsed);
        info("Rebreaks/s: %s attempts", rebreakRate);
        rebreakMonitorStartNs = now;
        rebreakCount = 0;
    }

    @Override
    public String getInfoString() {
        return debugRebreak.get() ? rebreakRate + " rebreaks/s" : null;
    }

    private float effectiveTps() {
        return tpsSync.get() ? TickRate.INSTANCE.getTickRate() : 20;
    }

    private boolean canStart() {
        return canStart(tpsSync.get(), effectiveTps(), startCredit);
    }

    static boolean canStart(boolean sync, float tps, double credit) {
        return !sync || !Float.isFinite(tps) || tps >= 20 || credit >= 1;
    }

    static double replenishStartCredit(double credit, long elapsedMillis, float tps) {
        // Retain fractional overflow so tick jitter does not discard earned starts.
        return Math.min(2, credit + serverTicks(elapsedMillis, tps));
    }

    static double serverTicks(long elapsedMillis, float tps) {
        return Math.max(0, elapsedMillis) / 1000.0 * Math.clamp(Float.isFinite(tps) ? tps : 20, 0, 20);
    }

    public void requestBreak(BlockPos pos) {
        if (mc.level == null || mc.player == null) return;
        BlockState state = mc.level.getBlockState(pos);
        if (state.isAir()) return;
        if (!isMining(pos)) handleBlockClick(pos, state);
    }

    public boolean isMining(BlockPos pos) {
        return (primary   != null && primary.pos.equals(pos))
            || (secondary != null && secondary.pos.equals(pos))
            || pendingBreaks.stream().anyMatch(pending -> pending.normal() && pending.pos().equals(pos))
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
        RenderUtilsTHM.renderBlockShape(event, pos, mc.level.getBlockState(pos),
            renderColor.get(), renderColor.get(), ShapeMode.Lines);
    }

    public enum RebreakTrigger implements DescribedOption {
        onPacket("Rebreak before server updates change the client world."),
        onClientWorld("Check for replacement blocks each client tick.");

        private final String description;

        RebreakTrigger(String description) {
            this.description = description;
        }

        @Override
        public String description() {
            return description;
        }
    }

    public enum RebreakMode implements DescribedOption {
        Off("Disable instant rebreaks."),
        Strict("Wait for server confirmation between rebreaks."),
        Strong("Skip confirmation waits and TPS start delays."),
        Bypass("Use Strict until a rebreak is confirmed, then Strong.");

        private final String description;

        RebreakMode(String description) {
            this.description = description;
        }

        @Override
        public String description() {
            return description;
        }

        boolean isStrong(boolean confirmedRebreak) {
            return this == Strong || (this == Bypass && confirmedRebreak);
        }

        boolean canRebreak(boolean primed, boolean waiting, boolean confirmedRebreak) {
            return this != Off && primed && (!waiting || isStrong(confirmedRebreak));
        }
    }

    public enum SwapMode implements DescribedOption {
        Keep("Keep for Next Tick", "Keep the tool through the next tick and consecutive rebreaks."),
        SameTick("Same Tick", "Restore the previous slot immediately after mining packets."),
        EndOfTick("End of Tick", "Restore the selected slot at the end of the tick."),
        ToolHold("Tool Hold", "Hold the tool through server validation and three idle ticks.");

        private final String title;
        private final String description;

        SwapMode(String title, String description) {
            this.title = title;
            this.description = description;
        }

        @Override
        public String description() {
            return description;
        }

        SwapMode forBreak(boolean instant, boolean primedRebreak) {
            return instant || primedRebreak ? this : ToolHold;
        }

        boolean shouldRelease(long ticksSinceAction, boolean mining, boolean replacement, boolean pending, int idleTicks) {
            return switch (this) {
                case SameTick, EndOfTick -> true;
                case Keep -> !mining && !replacement && ticksSinceAction >= 1;
                case ToolHold -> !mining && !pending && idleTicks >= IDLE_RELEASE_TICKS;
            };
        }

        @Override
        public String toString() {
            return title;
        }
    }

    // ── MineContext ───────────────────────────────────────────────────────────

    public class MineContext {

        public final BlockPos   pos;
        public final BlockState state;
        public final long       startMs;
        public final float      hardness;
        public boolean          isPrimary;
        public final boolean    instaBreak;
        /** Hotbar slot holding the tool this block was started with; -1 if none. */
        public final int        startSlot;
        public boolean          active = true;
        private boolean rebreak;
        private boolean packetRebreak;
        private long lastProgressMs;
        private double elapsedTicks;

        public MineContext(BlockPos pos, BlockState state, boolean isPrimary) {
            this.pos            = pos.immutable();
            this.state          = state;
            this.hardness       = mc.level != null ? state.getDestroySpeed(mc.level, pos) : 0;
            this.isPrimary      = isPrimary;
            this.startSlot      = findBestHotbarSlot(state);
            this.startMs        = System.currentTimeMillis();
            this.lastProgressMs = startMs;
            this.elapsedTicks   = 1;
            float delta         = calcDelta();
            this.instaBreak     = delta >= 1.0f;
        }

        private boolean requiresValidation() {
            return !instaBreak && !rebreak;
        }

        public double progress() {
            if (mc.player == null || mc.level == null || hardness < 0) return 0;
            long now = System.currentTimeMillis();
            if (!shouldPauseMining()) {
                elapsedTicks += serverTicks(now - lastProgressMs, effectiveTps());
            }
            lastProgressMs = now;
            float perTick = calcDelta();
            if (perTick <= 0) return 0;
            float target  = isPrimary ? breakThreshold.get().floatValue() : 1.0f;
            return Math.min((perTick * elapsedTicks) / target, 1.0);
        }

        private float calcDelta() {
            if (mc.player == null || mc.level == null) return 0;
            if (hardness <= 0) return hardness == 0f ? Float.MAX_VALUE : 0f;

            ItemStack tool = mc.player.getInventory().getItem(startSlot < 0 ? mc.player.getInventory().getSelectedSlot() : startSlot);

            int divisor = state.requiresCorrectToolForDrops() && !tool.isCorrectToolForDrops(state) ? 100 : 30;

            float speed = tool.getDestroySpeed(state);

            if (!tool.isEmpty() && speed > 1.0f) {
                int effLevel = 0;
                for (var entry : tool.getEnchantments().entrySet()) {
                    if (entry.getKey().is(Enchantments.EFFICIENCY)) {
                        effLevel = entry.getIntValue();
                        break;
                    }
                }
                if (effLevel > 0) speed += effLevel * effLevel + 1;
            }

            if (MobEffectUtil.hasDigSpeed(mc.player)) {
                speed *= 1.0f + (MobEffectUtil.getDigSpeedAmplification(mc.player) + 1) * 0.2f;
            }

            if (mc.player.hasEffect(MobEffects.MINING_FATIGUE)) {
                float penalty = switch (mc.player.getEffect(MobEffects.MINING_FATIGUE).getAmplifier()) {
                    case 0  -> 0.3f;
                    case 1  -> 0.09f;
                    case 2  -> 0.0027f;
                    default -> 8.1e-4f;
                };
                speed *= penalty;
            }

            speed *= (float) mc.player.getAttributeValue(Attributes.BLOCK_BREAK_SPEED);

            if (mc.player.isEyeInFluid(FluidTags.WATER)) {
                speed *= (float) mc.player.getAttributeValue(Attributes.SUBMERGED_MINING_SPEED);
            }

            if (!mc.player.onGround()) speed /= 5.0f;

            return speed / hardness / divisor;
        }
    }
}
