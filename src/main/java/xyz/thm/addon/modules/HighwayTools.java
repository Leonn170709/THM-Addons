/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.modules;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.ChatFormatting;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;
import net.minecraft.core.*;
import net.minecraft.world.phys.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.vehicle.boat.Boat;
import net.minecraft.world.item.BoatItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import com.google.gson.JsonObject;
import xyz.thm.addon.settings.DescribedOption;
import xyz.thm.addon.THMAddon;
import xyz.thm.addon.utils.THMUtils;
import xyz.thm.addon.utils.TrustedHttp;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class HighwayTools extends Module {
    private static final int TRUST_RADIUS_BLOCKS = 48;
    private static final int BOAT_RECOVERY_TIMEOUT_TICKS = 100;
    private static final int SAMPLE_Y_LOW = 118;
    private static final int SAMPLE_Y_HIGH = 119;
    private static final int PATH_Y_LOW = 120;
    private static final int PATH_Y_HIGH = 122;
    private static final int MAX_ALLOWED_Y = 122;
    private static final double MIN_PLAYER_TRAVEL_Y = 118.6;
    private static final double MAX_PLAYER_TRAVEL_Y = 121.1;
    private static final int PATH_LOOKAHEAD = 8;
    private static final int PATH_RADIUS = 10;
    private static final int PATH_NODE_LIMIT = 300;
    private static final int STALL_TIMEOUT_TICKS = 200;
    private static final int SECTION_HYSTERESIS_SAMPLES = 2;
    private static final int MIN_BOUNDARY_GAP_BLOCKS = 8;
    private static final int BOAT_MOUNT_GRACE_TICKS = 20;
    private static final int OUTPUT_RECOVERY_TIMEOUT_TICKS = 60;
    private static final double STEP_STRAIGHT_COST = 1.0;
    private static final double STEP_DIAGONAL_COST = Math.sqrt(2.0);
    private static final double MINE_SECTION_COST = 100.0;
    private static final double MINE_BLOCK_COST = 12.0;
    private static final double OFF_LINE_PENALTY = 0.35;
    private static final double START_ALIGNMENT_TOLERANCE = 5.0;
    private static final double REANCHOR_DISTANCE_THRESHOLD = 1.5;
    private static final double BOAT_INTERACT_RANGE = 5.25;
    private static final DateTimeFormatter SESSION_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH.mm.ss").withZone(ZoneId.systemDefault());
    private static final int[][] NEIGHBOR_DIRS = {
        { 1, 0 }, { 1, 1 }, { 0, 1 }, { -1, 1 },
        { -1, 0 }, { -1, -1 }, { 0, -1 }, { 1, -1 }
    };

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgChecker = settings.createGroup("Highway Checker");
    private final SettingGroup sgLogging = settings.createGroup("Highway Checker Logging");

    public final Setting<Boolean> axiswalker = sgGeneral.add(new BoolSetting.Builder()
        .name("Axis Walker")
        .description("Uses Baritone to walk to the nearest Nether Axis. Must be in the Nether.")
        .defaultValue(false)
        .build()
    );

    public final Setting<Boolean> highwayTp = sgGeneral.add(new BoolSetting.Builder()
        .name("Highway Tp")
        .description("Sends a $goto command to KitBot1 to teleport you to the selected highway.")
        .defaultValue(false)
        .build()
    );

    public final Setting<Boolean> autoTp = sgGeneral.add(new BoolSetting.Builder()
        .name("Auto Tp")
        .description("Automatically sends /tpa KitBot1 once the bot has arrived at the highway.")
        .defaultValue(true)
        .visible(() -> highwayTp.get())
        .build()
    );

    public final Setting<Highway> highway = sgGeneral.add(new EnumSetting.Builder<Highway>()
        .name("Highway")
        .description("The highway to teleport to.")
        .defaultValue(Highway.West)
        .visible(() -> highwayTp.get())
        .build()
    );

    public final Setting<Boolean> highwayCheckerEnabled = sgChecker.add(new BoolSetting.Builder()
        .name("Enable Highway Checker")
        .description("Runs the full Highway Checker logic (boat highway scanning).")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> notifyChat = sgChecker.add(new BoolSetting.Builder()
        .name("chat-notify")
        .description("Show section and failure events in Meteor chat.")
        .defaultValue(true)
        .visible(highwayCheckerEnabled::get)
        .build()
    );

    private final Setting<Boolean> notifyDesktop = sgChecker.add(new BoolSetting.Builder()
        .name("desktop-notify")
        .description("Send desktop notifications for section and failure events.")
        .defaultValue(false)
        .visible(highwayCheckerEnabled::get)
        .build()
    );

    private final Setting<Boolean> debugLog = sgChecker.add(new BoolSetting.Builder()
        .name("debug-log")
        .description("Write bounded runtime diagnostics to a debug file.")
        .defaultValue(true)
        .visible(highwayCheckerEnabled::get)
        .build()
    );

    private final Setting<Boolean> webhookEnabled = sgChecker.add(new BoolSetting.Builder()
        .name("webhook-enabled")
        .description("Send section events and/or CSV rows to a webhook.")
        .defaultValue(false)
        .visible(highwayCheckerEnabled::get)
        .build()
    );

    private final Setting<String> webhookUrl = sgChecker.add(new StringSetting.Builder()
        .name("webhook-url")
        .description("Webhook URL for Highway Checker events.")
        .defaultValue("")
        .visible(() -> highwayCheckerEnabled.get() && webhookEnabled.get())
        .build()
    );

    private final Setting<Boolean> webhookSendEvents = sgLogging.add(new BoolSetting.Builder()
        .name("webhook-send-events")
        .description("Send section start/stop events to the webhook.")
        .defaultValue(true)
        .visible(() -> highwayCheckerEnabled.get() && webhookEnabled.get())
        .build()
    );

    private final Setting<Boolean> webhookSendCsv = sgLogging.add(new BoolSetting.Builder()
        .name("webhook-send-csv")
        .description("Send each CSV row to the webhook while running.")
        .defaultValue(false)
        .visible(() -> highwayCheckerEnabled.get() && webhookEnabled.get())
        .build()
    );

    private final Setting<Boolean> csvLogAllSamples = sgLogging.add(new BoolSetting.Builder()
        .name("csv-log-all-samples")
        .description("Log every trusted sample row (not just boundary events).")
        .defaultValue(false)
        .visible(highwayCheckerEnabled::get)
        .build()
    );

    public final Setting<Boolean> obsidianGuardEnabled = sgChecker.add(new BoolSetting.Builder()
        .name("Enable Finder")
        .description("Monitors obsidian in your current chunk to detect the start or end of a highway.")
        .defaultValue(false)
        .build()
    );
    public final Setting<CheckerMode> guardMode = sgChecker.add(new EnumSetting.Builder<CheckerMode>()
        .name("Mode")
        .description("Whether to trigger where the highway ends or where it begins.")
        .defaultValue(CheckerMode.HighwayEnd)
        .visible(() -> obsidianGuardEnabled.get())
        .build()
    );

    public final Setting<Integer> obsidianThreshold = sgChecker.add(new IntSetting.Builder()
        .name("Obsidian Threshold")
        .description("The number of obsidian blocks in the chunk that marks the highway boundary.")
        .defaultValue(12)
        .min(1)
        .sliderMax(64)
        .visible(() -> obsidianGuardEnabled.get())
        .build()
    );

    public final Setting<Boolean> sendWarning = sgChecker.add(new BoolSetting.Builder()
        .name("Chat Warning")
        .description("Prints a warning in the Meteor client chat when the highway boundary is detected.")
        .defaultValue(true)
        .visible(() -> obsidianGuardEnabled.get())
        .build()
    );

    public final Setting<Boolean> disconnect = sgChecker.add(new BoolSetting.Builder()
        .name("Disconnect")
        .description("Disconnects from the server when the highway boundary is detected.")
        .defaultValue(true)
        .visible(() -> obsidianGuardEnabled.get())
        .build()
    );

    public final Setting<Boolean> desktopWarning = sgChecker.add(new BoolSetting.Builder()
        .name("Desktop Notification")
        .description("Sends a desktop notification when the highway boundary is detected.")
        .defaultValue(true)
        .visible(() -> obsidianGuardEnabled.get())
        .build()
    );


    private final IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
    private boolean tpaSent = false;
    private int tickTimer = 0;
    private static final int CHECK_INTERVAL = 20;

    private TravelDirection direction;
    private WorkLine line;
    private String sessionId;
    private String activeSectionId;
    private int nextSectionNumber;
    private boolean sectionOpen;
    private SectionPhase sectionPhase;
    private BoundaryType lastBoundaryType;
    private long lastSampleKey = Long.MIN_VALUE;
    private long lastBoundaryKey = Long.MIN_VALUE;
    private boolean lastSampleTrusted = true;
    private int boatRecoveryTicks;
    private int boatMountGraceTicks;
    private int lastProgressAge;
    private double lastMeasuredY;
    private String lastPathDecision = "";
    private String pendingStopReason;
    private int startSectionStreak;
    private int stopSectionStreak;
    private PendingOutputWrite pendingOutputWrite;
    private Path csvPath;
    private Path sectionsPath;
    private Path debugPath;

    public HighwayTools() {
        super(THMAddon.MAIN, "Highway-Tools", "Highway utilities: axis walker, highway teleporter, boundary finder, and highway checker.");
    }

    @Override
    public void onActivate() {
        if (mc.player == null || mc.level == null) return;

        if (highwayTp.get() && axiswalker.get()) {
            error("You cannot have both Highway Tp and Axis Walker enabled at the same time.");
            toggle();
            return;
        }

        if (axiswalker.get()) {
            if (mc.level.dimension() == Level.NETHER) {
                baritone.getCommandManager().execute("axis");
                baritone.getCommandManager().execute("path");
            } else {
                error("Axis Walker can only be used in the Nether.");
            }
            toggle();
            return;
        }

        if (highwayTp.get()) {
            tpaSent = false;
            String prefix = "/msg KitBot1 $goto ";
            switch (highway.get()) {
                case West -> ChatUtils.sendPlayerMsg(prefix + "W");
                case East -> ChatUtils.sendPlayerMsg(prefix + "E");
                case North -> ChatUtils.sendPlayerMsg(prefix + "N");
                case South -> ChatUtils.sendPlayerMsg(prefix + "S");
                case NorthEast -> ChatUtils.sendPlayerMsg(prefix + "NE");
                case SouthEast -> ChatUtils.sendPlayerMsg(prefix + "SE");
                case SouthWest -> ChatUtils.sendPlayerMsg(prefix + "SW");
                case NorthWest -> ChatUtils.sendPlayerMsg(prefix + "NW");

                case DugWest -> ChatUtils.sendPlayerMsg(prefix + "dugW");
                case DugEast -> ChatUtils.sendPlayerMsg(prefix + "dugE");
                case DugNorth -> ChatUtils.sendPlayerMsg(prefix + "dugN");
                case DugSouth -> ChatUtils.sendPlayerMsg(prefix + "dugS");
                case DugNorthEast -> ChatUtils.sendPlayerMsg(prefix + "dugNE");
                case DugSouthEast -> ChatUtils.sendPlayerMsg(prefix + "dugSE");
                case DugSouthWest -> ChatUtils.sendPlayerMsg(prefix + "dugSW");
                case DugNorthWest -> ChatUtils.sendPlayerMsg(prefix + "dugNW");
            }
            tickTimer = 0;
        }

        if (!highwayCheckerEnabled.get()) return;

        if (THMUtils.isNot6B6T() && !mc.isLocalServer()) {
            error("Highway Checker is intended for 6B6T highway runs.");
            toggle();
            return;
        }

        if (Level.NETHER != mc.level.dimension()) {
            error("Highway Checker can only be used in the Nether.");
            toggle();
            return;
        }

        direction = TravelDirection.fromYaw(mc.player.getYRot());
        line = direction.line;
        double distanceToLine = distanceToLockedLine(mc.player.getX(), mc.player.getZ(), line);
        if (distanceToLine > START_ALIGNMENT_TOLERANCE) {
            error("Too far from the inferred %s highway line (%.2f blocks).", direction.label, distanceToLine);
            toggle();
            return;
        }

        sessionId = UUID.randomUUID().toString().substring(0, 8);
        activeSectionId = null;
        nextSectionNumber = 1;
        sectionOpen = false;
        sectionPhase = SectionPhase.LOOKING_FOR_ANY;
        lastBoundaryType = null;
        lastSampleKey = Long.MIN_VALUE;
        lastBoundaryKey = Long.MIN_VALUE;
        boatRecoveryTicks = 0;
        boatMountGraceTicks = 0;
        lastPathDecision = "";
        lastSampleTrusted = true;
        lastProgressAge = mc.player.tickCount;
        lastMeasuredY = mc.player.getY();
        pendingStopReason = null;
        startSectionStreak = 0;
        stopSectionStreak = 0;
        pendingOutputWrite = null;

        try {
            initializeOutputFiles();
        } catch (IOException e) {
            error("Failed to initialize Highway Checker output files.");
            toggle();
            return;
        }

        alignPlayerToLockedLine();
        stopMovementKeys();
        debug("activate", "session=%s direction=%s line=%s", sessionId, direction.label, line);
        notifyEvent("Started Highway Checker on %s.", direction.label);
    }

    @Override
    public void onDeactivate() {
        if (!highwayCheckerEnabled.get()) return;
        stopMovementKeys();

        if (sectionOpen) {
            BlockPos center = currentCenterBlock();
            closeSection(center, sampleCurrentSlice(center), pendingStopReason == null ? "manual-stop" : pendingStopReason);
        }

        if (sessionId != null) debug("deactivate", "session=%s", sessionId);

        direction = null;
        line = null;
        sessionId = null;
        activeSectionId = null;
        nextSectionNumber = 0;
        sectionOpen = false;
        sectionPhase = SectionPhase.LOOKING_FOR_ANY;
        lastBoundaryType = null;
        lastSampleKey = Long.MIN_VALUE;
        lastBoundaryKey = Long.MIN_VALUE;
        boatRecoveryTicks = 0;
        boatMountGraceTicks = 0;
        lastPathDecision = "";
        lastMeasuredY = 0.0;
        pendingStopReason = null;
        startSectionStreak = 0;
        stopSectionStreak = 0;
        pendingOutputWrite = null;
        csvPath = null;
        sectionsPath = null;
        debugPath = null;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (obsidianGuardEnabled.get()) {
            if (mc.player != null && mc.level != null) {
                tickTimer++;
                if (tickTimer >= CHECK_INTERVAL) {
                    tickTimer = 0;
                    int count = countObsidianInChunk(mc.player);
                    boolean triggered = false;
                    String reason = "";

                    if (guardMode.get() == CheckerMode.HighwayEnd && count < obsidianThreshold.get()) {
                        triggered = true;
                        reason = String.format("Highway end detected — only %d obsidian in chunk (threshold: %d).", count, obsidianThreshold.get());
                    } else if (guardMode.get() == CheckerMode.HighwayStart && count > obsidianThreshold.get()) {
                        triggered = true;
                        reason = String.format("Highway start detected — %d obsidian in chunk (threshold: %d).", count, obsidianThreshold.get());
                    }

                    if (triggered) {
                        if (sendWarning.get()) warning(reason);
                        if (desktopWarning.get()) THMUtils.Notify(name, reason);
                        if (disconnect.get()) disconnectPlayer();
                    }
                }
            }
        }

        if (!highwayCheckerEnabled.get()) return;
        if (mc.player == null || mc.level == null || direction == null || line == null) return;

        if (Level.NETHER != mc.level.dimension()) {
            fail("Left the Nether while Highway Checker was active.");
            return;
        }

        if (!Utils.canUpdate()) {
            stopMovementKeys();
            return;
        }

        if (!recoverPendingOutputWrite()) return;

        if (!isRidingBoat()) {
            handleBoatRecovery();
            return;
        }

        boatRecoveryTicks = 0;
        boatMountGraceTicks = 0;
        if (handleMountedBoatHeightRecovery()) return;
        sampleIfAdvanced();
        if (mc.player.tickCount - lastProgressAge > STALL_TIMEOUT_TICKS) {
            fail("Highway Checker stalled without progress.");
            return;
        }

        StepDecision step = chooseStep();
        if (step == null) {
            fail("Unable to find a traversable section path.");
            return;
        }

        if (!step.mode.equals(lastPathDecision)) {
            lastPathDecision = step.mode;
            debug("path", "mode=%s next=(%d,%d) obstacles=%d", step.mode, step.dx, step.dz, step.obstacleCount);
        }

        if (step.requiresMining) {
            mineSection(step.probe);
            return;
        }

        steerToward(step.dx, step.dz);
    }

    @EventHandler
    private void onMessageReceive(ReceiveMessageEvent event) {
        if (mc.player == null) return;
        if (tpaSent) return;

        String msg = event.getMessage().getString();

        if (autoTp.get()
            && msg.contains("KitBot1 whispers: Bot has arrived at highway")
            && msg.contains("you may teleport")) {

            ChatUtils.sendPlayerMsg("/tpa KitBot1");
            info("TPA request sent.");
            tpaSent = true;
            toggle();
        }
    }

    private void handleBoatRecovery() {
        stopMovementKeys();
        boatRecoveryTicks++;
        if (boatRecoveryTicks == 1) debug("boat-recovery", "started");
        if (boatRecoveryTicks > BOAT_RECOVERY_TIMEOUT_TICKS) {
            fail("Boat recovery timed out.");
            return;
        }

        Boat nearbyBoat = findNearestBoatEntity();
        if (nearbyBoat != null) {
            if (tryMountBoat(nearbyBoat)) {
                boatRecoveryTicks = 0;
                boatMountGraceTicks = 0;
                lastProgressAge = mc.player != null ? mc.player.tickCount : lastProgressAge;
                debug("boat-recovery", "mounted-existing-boat");
                return;
            }
            if (boatMountGraceTicks > 0) {
                boatMountGraceTicks--;
                return;
            }
            steerTowardEntity(nearbyBoat);
            return;
        }

        ItemEntity boatDrop = findNearestBoatDrop();
        if (boatDrop != null && !hasBoatItem()) {
            steerTowardEntity(boatDrop);
            return;
        }

        FindItemResult boatItem = findBoatItem();
        if (!boatItem.found()) return;

        BoatPlacementCandidate placement = findBoatPlacementCandidate();
        if (placement == null) return;

        if (!boatItem.isHotbar()) {
            int hotbarSlot = findEmptyHotbarSlot();
            if (hotbarSlot == -1) {
                debug("boat-recovery", "no-empty-hotbar-slot-for-boat");
                return;
            }
            InvUtils.move().from(boatItem.slot()).toHotbar(hotbarSlot);
            boatItem = findBoatItem();
            if (!boatItem.found()) return;
        }

        if (!InvUtils.swap(boatItem.slot(), true)) return;

        mc.gameMode.useItemOn(
            mc.player,
            InteractionHand.MAIN_HAND,
            placement.hitResult()
        );
        mc.player.swing(InteractionHand.MAIN_HAND);
        boatMountGraceTicks = BOAT_MOUNT_GRACE_TICKS;
        debug("boat-recovery", "placed-boat floor=%s", formatPos(placement.floorAnchor()));
    }

    private void sampleIfAdvanced() {
        BlockPos center = currentCenterBlock();
        long key = pack(center.getX(), center.getZ());
        if (key == lastSampleKey) return;
        lastSampleKey = key;
        lastProgressAge = mc.player.tickCount;

        SampleSnapshot snapshot = sampleCurrentSlice(center);
        if (!snapshot.trusted()) {
            if (lastSampleTrusted) {
                debug("sample", "skipped-untrusted center=%s", formatPos(center));
                lastSampleTrusted = false;
            }
            startSectionStreak = 0;
            stopSectionStreak = 0;
            return;
        }
        lastSampleTrusted = true;

        if (csvLogAllSamples.get()) {
            writeCsvRow(BoundaryType.SAMPLE, activeSectionId, snapshot, sectionOpen);
        }

        if (sectionPhase == SectionPhase.LOOKING_FOR_ANY) {
            if (snapshot.startsSection()) {
                startSectionStreak++;
                stopSectionStreak = 0;
                if (startSectionStreak >= SECTION_HYSTERESIS_SAMPLES && canRecordBoundaryAt(center) && canRecordBoundaryType(BoundaryType.START)) {
                    openSection(center, snapshot);
                    startSectionStreak = 0;
                }
            } else if (snapshot.stopsSection()) {
                stopSectionStreak++;
                startSectionStreak = 0;
                if (stopSectionStreak >= SECTION_HYSTERESIS_SAMPLES && canRecordBoundaryAt(center) && canRecordBoundaryType(BoundaryType.STOP)) {
                    closeSection(center, snapshot, "detected-stop");
                    stopSectionStreak = 0;
                }
            } else {
                startSectionStreak = 0;
                stopSectionStreak = 0;
            }
        } else if (sectionPhase == SectionPhase.LOOKING_FOR_START) {
            if (snapshot.startsSection()) {
                startSectionStreak++;
                stopSectionStreak = 0;
                if (startSectionStreak >= SECTION_HYSTERESIS_SAMPLES && canRecordBoundaryAt(center) && canRecordBoundaryType(BoundaryType.START)) {
                    openSection(center, snapshot);
                    startSectionStreak = 0;
                }
            } else {
                startSectionStreak = 0;
            }
        } else {
            if (snapshot.stopsSection()) {
                stopSectionStreak++;
                startSectionStreak = 0;
                if (stopSectionStreak >= SECTION_HYSTERESIS_SAMPLES && canRecordBoundaryAt(center) && canRecordBoundaryType(BoundaryType.STOP)) {
                    closeSection(center, snapshot, "detected-stop");
                    stopSectionStreak = 0;
                }
            } else {
                stopSectionStreak = 0;
            }
        }
    }

    private SampleSnapshot sampleCurrentSlice(BlockPos center) {
        ArrayList<BlockPos> y118Positions = new ArrayList<>(5);
        ArrayList<BlockPos> y119Positions = new ArrayList<>(5);
        ArrayList<String> y118Blocks = new ArrayList<>(5);
        ArrayList<String> y119Blocks = new ArrayList<>(5);
        boolean trusted = true;

        int[] perp = direction.perpendicular();
        for (int offset = -2; offset <= 2; offset++) {
            int sx = center.getX() + perp[0] * offset;
            int sz = center.getZ() + perp[1] * offset;
            BlockPos low = new BlockPos(sx, SAMPLE_Y_LOW, sz);
            BlockPos high = new BlockPos(sx, SAMPLE_Y_HIGH, sz);

            if (!isTrustedSamplePos(low) || !isTrustedSamplePos(high)) trusted = false;

            y118Positions.add(low);
            y119Positions.add(high);
            y118Blocks.add(blockId(mc.level.getBlockState(low)));
            y119Blocks.add(blockId(mc.level.getBlockState(high)));
        }

        boolean lowAllObs = allObsidian(y118Positions);
        boolean highAllObs = allObsidian(y119Positions);
        boolean lowAllNonObs = allNonObsidian(y118Positions);
        boolean highAllNonObs = allNonObsidian(y119Positions);

        return new SampleSnapshot(
            trusted,
            center,
            new Vec3(mc.player.getX(), mc.player.getY(), mc.player.getZ()),
            lowAllObs || highAllObs,
            lowAllNonObs && highAllNonObs,
            y118Blocks,
            y119Blocks
        );
    }

    private StepDecision chooseStep() {
        BlockPos center = currentCenterBlock();
        BlockPos actual = mc.player.blockPosition();
        int startX = actual.getX();
        int startZ = actual.getZ();
        int destX = center.getX() + direction.dx * PATH_LOOKAHEAD;
        int destZ = center.getZ() + direction.dz * PATH_LOOKAHEAD;
        return findAStarStep(startX, startZ, destX, destZ);
    }

    private StepDecision findAStarStep(int startX, int startZ, int destX, int destZ) {
        long startKey = pack(startX, startZ);
        PriorityQueue<AStarNode> open = new PriorityQueue<>(Comparator.comparingDouble(node -> node.f));
        HashMap<Long, Double> gScore = new HashMap<>();
        HashMap<Long, Long> cameFrom = new HashMap<>();
        HashMap<Long, SectionProbe> probes = new HashMap<>();
        HashSet<Long> closed = new HashSet<>();

        open.add(new AStarNode(startX, startZ, 0.0, octileDistance(startX, startZ, destX, destZ)));
        gScore.put(startKey, 0.0);

        long bestKey = startKey;
        double bestH = octileDistance(startX, startZ, destX, destZ);
        int expanded = 0;

        while (!open.isEmpty() && expanded < PATH_NODE_LIMIT) {
            AStarNode current = open.poll();
            long currentKey = pack(current.x, current.z);
            if (!closed.add(currentKey)) continue;
            expanded++;

            double h = octileDistance(current.x, current.z, destX, destZ);
            if (h < bestH) {
                bestH = h;
                bestKey = currentKey;
            }
            if (current.x == destX && current.z == destZ) {
                bestKey = currentKey;
                break;
            }

            for (int[] dir : NEIGHBOR_DIRS) {
                int nx = current.x + dir[0];
                int nz = current.z + dir[1];

                int rx = nx - startX;
                int rz = nz - startZ;
                if (rx * rx + rz * rz > PATH_RADIUS * PATH_RADIUS) continue;

                SectionProbe probe = probeTraversalSection(nx, nz);
                if (!probe.open() && !probe.mineable()) continue;

                long neighborKey = pack(nx, nz);
                if (closed.contains(neighborKey)) continue;

                double tentativeG = current.g
                    + ((Math.abs(dir[0]) == 1 && Math.abs(dir[1]) == 1) ? STEP_DIAGONAL_COST : STEP_STRAIGHT_COST)
                    + lineDistancePenalty(nx, nz)
                    + (probe.mineable() ? MINE_SECTION_COST + probe.obstacles().size() * MINE_BLOCK_COST : 0.0);

                if (tentativeG >= gScore.getOrDefault(neighborKey, Double.POSITIVE_INFINITY)) continue;

                gScore.put(neighborKey, tentativeG);
                cameFrom.put(neighborKey, currentKey);
                probes.put(neighborKey, probe);
                double f = tentativeG + octileDistance(nx, nz, destX, destZ);
                open.add(new AStarNode(nx, nz, tentativeG, f));
            }
        }

        if (bestKey == startKey) return null;

        long stepKey = bestKey;
        while (cameFrom.containsKey(stepKey) && cameFrom.get(stepKey) != startKey) {
            stepKey = cameFrom.get(stepKey);
        }

        int sx = unpackX(stepKey);
        int sz = unpackZ(stepKey);
        SectionProbe probe = probes.getOrDefault(stepKey, probeTraversalSection(sx, sz));
        int dx = Integer.compare(sx, startX);
        int dz = Integer.compare(sz, startZ);
        String mode = probe.mineable() ? "mine-fallback" : (dx == direction.dx && dz == direction.dz ? "forward-open" : "detour-open");
        return new StepDecision(dx, dz, probe, probe.mineable(), mode, probe.obstacles().size());
    }

    private SectionProbe probeTraversalSection(int centerX, int centerZ) {
        ArrayList<BlockPos> obstacles = new ArrayList<>();
        boolean nonMineable = false;

        for (BlockPos pos : getSectionProbeVolume(centerX, centerZ)) {
            BlockState state = mc.level.getBlockState(pos);
            if (state.isAir() || state.canBeReplaced()) continue;

            if (!isMineableBlock(state, pos)) nonMineable = true;
            else obstacles.add(pos);
        }

        boolean open = obstacles.isEmpty() && !nonMineable && isTravelSpaceClear(centerX, centerZ);
        boolean mineable = !open && !nonMineable;
        return new SectionProbe(open, mineable, obstacles);
    }

    private void mineSection(SectionProbe probe) {
        if (probe.obstacles().isEmpty()) {
            fail("Mine fallback selected without mineable obstacles.");
            return;
        }

        BlockPos target = probe.obstacles().stream()
            .filter(pos -> mc.player.distanceToSqr(Vec3.atCenterOf(pos)) <= BOAT_INTERACT_RANGE * BOAT_INTERACT_RANGE)
            .min(Comparator.comparingDouble(pos -> mc.player.distanceToSqr(Vec3.atCenterOf(pos))))
            .orElse(null);

        if (target == null) {
            debug("mine", "no-reachable-obstacle");
            return;
        }

        FindItemResult tool = InvUtils.findFastestTool(mc.level.getBlockState(target));
        if (tool.found() && tool.slot() != mc.player.getInventory().getSelectedSlot()) InvUtils.swap(tool.slot(), false);
        BlockUtils.breakBlock(target, true);
        debug("mine", "target=%s", formatPos(target));
    }

    private boolean handleMountedBoatHeightRecovery() {
        if (mc.player == null) return false;

        mc.options.keyJump.setDown(false);
        mc.options.keySprint.setDown(false);

        double y = mc.player.getY();
        if (Math.abs(y - lastMeasuredY) >= 0.05) {
            lastMeasuredY = y;
            lastProgressAge = mc.player.tickCount;
        }

        if (y < MIN_PLAYER_TRAVEL_Y) {
            mc.options.keyJump.setDown(true);
            steerToward(direction.dx, direction.dz);
            return true;
        }

        if (y > MAX_PLAYER_TRAVEL_Y) {
            mc.options.keySprint.setDown(true);
            steerToward(direction.dx, direction.dz);
            return true;
        }

        return false;
    }

    private void steerToward(int dx, int dz) {
        if (mc.player == null) return;
        if (distanceToLockedLine(mc.player.getX(), mc.player.getZ(), line) > REANCHOR_DISTANCE_THRESHOLD) {
            debug("path", "reanchor distance=%.2f", distanceToLockedLine(mc.player.getX(), mc.player.getZ(), line));
        }

        float yaw = yawForStep(dx, dz);
        mc.player.setYRot(yaw);
        Entity vehicle = mc.player.getVehicle();
        if (vehicle != null) vehicle.setYRot(yaw);

        mc.options.keyUp.setDown(true);
        mc.options.keyDown.setDown(false);
        mc.options.keyLeft.setDown(false);
        mc.options.keyRight.setDown(false);
    }

    private void steerTowardEntity(Entity entity) {
        if (mc.player == null || entity == null) return;

        Vec3 delta = new Vec3(
            entity.getX() - mc.player.getX(),
            entity.getY() - mc.player.getY(),
            entity.getZ() - mc.player.getZ()
        );
        float yaw = (float) Math.toDegrees(Math.atan2(-delta.x, delta.z));
        mc.player.setYRot(yaw);
        mc.options.keyUp.setDown(true);
        mc.options.keyDown.setDown(false);
        mc.options.keyLeft.setDown(false);
        mc.options.keyRight.setDown(false);
    }

    private boolean tryMountBoat(Boat boat) {
        if (mc.player == null || boat == null) return false;
        if (mc.player.distanceToSqr(boat) > BOAT_INTERACT_RANGE * BOAT_INTERACT_RANGE) return false;

        mc.gameMode.interact(mc.player, boat, new net.minecraft.world.phys.EntityHitResult(boat), InteractionHand.MAIN_HAND);
        boatMountGraceTicks = BOAT_MOUNT_GRACE_TICKS;
        return isRidingBoat();
    }

    private Boat findNearestBoatEntity() {
        if (mc.player == null || mc.level == null) return null;

        return mc.level.getEntitiesOfClass(
            Boat.class,
            new AABB(mc.player.blockPosition()).inflate(6.0),
            boat -> boat.isAlive() && boat.getPassengers().size() < 2
        ).stream().min(Comparator.comparingDouble(mc.player::distanceToSqr)).orElse(null);
    }

    private ItemEntity findNearestBoatDrop() {
        if (mc.player == null || mc.level == null) return null;

        return mc.level.getEntitiesOfClass(
            ItemEntity.class,
            new AABB(mc.player.blockPosition()).inflate(8.0),
            item -> isBoatItem(item.getItem().getItem())
        ).stream().min(Comparator.comparingDouble(mc.player::distanceToSqr)).orElse(null);
    }

    private boolean hasBoatItem() {
        return findBoatItem().found();
    }

    private FindItemResult findBoatItem() {
        return InvUtils.find(stack -> isBoatItem(stack.getItem()));
    }

    private boolean isBoatItem(Item item) {
        return item instanceof BoatItem;
    }

    private int findEmptyHotbarSlot() {
        if (mc.player == null) return -1;
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getItem(i).isEmpty()) return i;
        }
        return -1;
    }

    private BoatPlacementCandidate findBoatPlacementCandidate() {
        if (mc.player == null || mc.level == null) return null;

        BlockPos playerPos = mc.player.blockPosition();
        int[] floorYs = { 119, 118 };
        for (int floorY : floorYs) {
            for (int ox = -2; ox <= 2; ox++) {
                for (int oz = -2; oz <= 2; oz++) {
                    BlockPos floor = new BlockPos(playerPos.getX() + ox, floorY, playerPos.getZ() + oz);
                    if (!isValidBoatPlacementArea(floor)) continue;
                    return new BoatPlacementCandidate(
                        floor,
                        new BlockHitResult(Vec3.atCenterOf(floor), Direction.UP, floor, false),
                        floor.above()
                    );
                }
            }
        }
        return null;
    }

    private boolean isValidBoatPlacementArea(BlockPos floorAnchor) {
        if (mc.level == null) return false;

        for (int dx = 0; dx < 2; dx++) {
            for (int dz = 0; dz < 2; dz++) {
                BlockPos floor = floorAnchor.offset(dx, 0, dz);
                if (!mc.level.getBlockState(floor).isRedstoneConductor(mc.level, floor)) return false;
            }
        }

        for (int dx = 0; dx < 2; dx++) {
            for (int dz = 0; dz < 2; dz++) {
                for (int y = 1; y <= 3; y++) {
                    BlockPos air = floorAnchor.offset(dx, y, dz);
                    BlockState state = mc.level.getBlockState(air);
                    if (!state.isAir() && !state.canBeReplaced()) return false;
                }
            }
        }

        return true;
    }

    private List<BlockPos> getSectionProbeVolume(int centerX, int centerZ) {
        ArrayList<BlockPos> positions = new ArrayList<>(15);
        int[] perp = direction.perpendicular();

        for (int offset = -2; offset <= 2; offset++) {
            int sx = centerX + perp[0] * offset;
            int sz = centerZ + perp[1] * offset;
            for (int y = PATH_Y_LOW; y <= PATH_Y_HIGH; y++) {
                positions.add(new BlockPos(sx, y, sz));
            }
        }

        return positions;
    }

    private boolean isTravelSpaceClear(int centerX, int centerZ) {
        if (mc.level == null) return false;

        for (BlockPos pos : getSectionProbeVolume(centerX, centerZ)) {
            BlockState state = mc.level.getBlockState(pos);
            if (!state.isAir() && !state.canBeReplaced()) return false;
        }

        return true;
    }

    private boolean isTrustedSamplePos(BlockPos pos) {
        if (mc.player == null) return false;
        double dx = pos.getX() + 0.5 - mc.player.getX();
        double dz = pos.getZ() + 0.5 - mc.player.getZ();
        return Math.hypot(dx, dz) <= TRUST_RADIUS_BLOCKS;
    }

    private boolean isRidingBoat() {
        return mc.player != null && mc.player.getVehicle() instanceof Boat;
    }

    private boolean allObsidian(List<BlockPos> positions) {
        for (BlockPos pos : positions) {
            if (!mc.level.getBlockState(pos).is(Blocks.OBSIDIAN)) return false;
        }
        return true;
    }

    private boolean allNonObsidian(List<BlockPos> positions) {
        for (BlockPos pos : positions) {
            if (mc.level.getBlockState(pos).is(Blocks.OBSIDIAN)) return false;
        }
        return true;
    }

    private boolean isMineableBlock(BlockState state, BlockPos pos) {
        if (state.isAir() || state.canBeReplaced()) return true;
        if (state.is(Blocks.BEDROCK) || state.is(Blocks.OBSIDIAN) || state.is(Blocks.CRYING_OBSIDIAN)) return false;
        return state.getDestroySpeed(mc.level, pos) >= 0;
    }

    private void openSection(BlockPos center, SampleSnapshot snapshot) {
        activeSectionId = sessionId + "-S" + nextSectionNumber++;
        sectionOpen = true;
        if (!appendCoreLine(sectionsPath, "START," + activeSectionId + "," + center.getX() + "," + center.getY() + "," + center.getZ(), "section-start")) return;
        lastBoundaryKey = pack(center.getX(), center.getZ());
        sectionPhase = SectionPhase.LOOKING_FOR_STOP;
        lastBoundaryType = BoundaryType.START;
        writeCsvRow(BoundaryType.START, activeSectionId, snapshot);
        debug("section", "start id=%s center=%s", activeSectionId, formatPos(center));
        notifyEvent("Section %s started at %s.", activeSectionId, formatPos(center));
        if (webhookEnabled.get() && webhookSendEvents.get()) {
            sendWebhookAsync("Highway Checker START " + activeSectionId + " at " + formatPos(center), "section-start");
        }
    }

    private void closeSection(BlockPos center, SampleSnapshot snapshot, String reason) {
        String sectionIdForRow = activeSectionId == null ? "" : activeSectionId;
        if (!appendCoreLine(sectionsPath, "STOP," + sectionIdForRow + "," + center.getX() + "," + center.getY() + "," + center.getZ(), "section-stop")) return;
        lastBoundaryKey = pack(center.getX(), center.getZ());
        sectionPhase = SectionPhase.LOOKING_FOR_START;
        lastBoundaryType = BoundaryType.STOP;
        writeCsvRow(BoundaryType.STOP, sectionIdForRow, snapshot);
        debug("section", "stop id=%s center=%s reason=%s", sectionIdForRow, formatPos(center), reason);
        notifyEvent("Section %s stopped at %s.", sectionIdForRow.isEmpty() ? "<none>" : sectionIdForRow, formatPos(center));
        if (webhookEnabled.get() && webhookSendEvents.get()) {
            sendWebhookAsync("Highway Checker STOP " + sectionIdForRow + " at " + formatPos(center) + " reason=" + reason, "section-stop");
        }
        activeSectionId = null;
        sectionOpen = false;
    }

    private boolean canRecordBoundaryAt(BlockPos center) {
        if (lastBoundaryKey == Long.MIN_VALUE) return true;

        int lastX = unpackX(lastBoundaryKey);
        int lastZ = unpackZ(lastBoundaryKey);
        int dx = center.getX() - lastX;
        int dz = center.getZ() - lastZ;
        return Math.hypot(dx, dz) >= MIN_BOUNDARY_GAP_BLOCKS;
    }

    private boolean canRecordBoundaryType(BoundaryType boundaryType) {
        return lastBoundaryType == null || lastBoundaryType != boundaryType;
    }

    private void writeCsvRow(BoundaryType boundaryType, String sectionId, SampleSnapshot snapshot) {
        writeCsvRow(boundaryType, sectionId, snapshot, boundaryType == BoundaryType.START);
    }

    private void writeCsvRow(BoundaryType boundaryType, String sectionId, SampleSnapshot snapshot, boolean sectionOpenValue) {
        if (csvPath == null) return;

        StringBuilder lineOut = new StringBuilder(256);
        lineOut.append(Instant.now()).append(',')
            .append(sessionId).append(',')
            .append(boundaryType.name()).append(',')
            .append(sectionId == null ? "" : sectionId).append(',')
            .append(direction.label).append(',')
            .append(snapshot.center().getX()).append(',')
            .append(snapshot.center().getY()).append(',')
            .append(snapshot.center().getZ()).append(',')
            .append(ChunkPos.containing(snapshot.center()).x()).append(',')
            .append(ChunkPos.containing(snapshot.center()).z()).append(',')
            .append(String.format(Locale.ROOT, "%.3f", snapshot.playerPos().x)).append(',')
            .append(String.format(Locale.ROOT, "%.3f", snapshot.playerPos().y)).append(',')
            .append(String.format(Locale.ROOT, "%.3f", snapshot.playerPos().z)).append(',')
            .append(snapshot.trusted()).append(',')
            .append(snapshot.startsSection()).append(',')
            .append(snapshot.stopsSection()).append(',')
            .append(sectionOpenValue);

        for (String block : snapshot.y118Blocks()) lineOut.append(',').append(block);
        for (String block : snapshot.y119Blocks()) lineOut.append(',').append(block);
        String csvLine = lineOut.toString();
        appendCoreLine(csvPath, csvLine, "csv-sample");
        if (webhookEnabled.get() && webhookSendCsv.get()) {
            sendWebhookAsync(csvLine, "csv");
        }
    }

    private void initializeOutputFiles() throws IOException {
        Path sessionCsv = THMAddon.GetConfigFile("highway-checker", "highway-checker-samples-" + SESSION_TIME_FORMAT.format(Instant.now()) + "-" + sessionId + ".csv").toPath();
        Path sections = THMAddon.GetConfigFile("highway-checker", "highway-checker-sections.txt").toPath();
        Path debug = THMAddon.GetConfigFile("highway-checker", "highway-checker-debug.log").toPath();

        csvPath = sessionCsv;
        sectionsPath = sections;
        debugPath = debug;

        Files.createDirectories(csvPath.getParent());
        if (!Files.exists(csvPath)) {
            Files.writeString(
                csvPath,
                "timestamp,session_id,boundary_type,section_id,direction,center_x,center_y,center_z,chunk_x,chunk_z,player_x,player_y,player_z,trusted,starts_section,stops_section,section_open,"
                    + "y118_m2,y118_m1,y118_0,y118_p1,y118_p2,"
                    + "y119_m2,y119_m1,y119_0,y119_p1,y119_p2"
                    + System.lineSeparator(),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE
            );
        }
    }

    private void appendLine(Path path, String line) {
        if (path == null) return;
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(
                path,
                line + System.lineSeparator(),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
                StandardOpenOption.APPEND
            );
        } catch (IOException e) {
            THMAddon.LOG.warn("Highway Checker failed to append to {}: {}", path, e.getMessage());
        }
    }

    private boolean appendCoreLine(Path path, String line, String kind) {
        if (path == null) return true;
        if (tryAppendLine(path, line)) {
            if (pendingOutputWrite != null && pendingOutputWrite.path().equals(path) && pendingOutputWrite.line().equals(line)) {
                debug("io", "recovered kind=%s path=%s", kind, path.getFileName());
                pendingOutputWrite = null;
            }
            return true;
        }

        if (pendingOutputWrite == null) {
            pendingOutputWrite = new PendingOutputWrite(path, line, kind, OUTPUT_RECOVERY_TIMEOUT_TICKS);
            debug("io", "pause-for-retry kind=%s path=%s", kind, path.getFileName());
        }
        return false;
    }

    private boolean recoverPendingOutputWrite() {
        if (pendingOutputWrite == null) return true;

        stopMovementKeys();
        PendingOutputWrite pending = pendingOutputWrite;
        if (tryAppendLine(pending.path(), pending.line())) {
            debug("io", "retry-success kind=%s path=%s", pending.kind(), pending.path().getFileName());
            pendingOutputWrite = null;
            return true;
        }

        int remaining = pending.ticksRemaining() - 1;
        if (remaining <= 0) {
            String kind = pending.kind();
            pendingOutputWrite = null;
            fail("Highway Checker %s logging failed after retry.", kind);
            return false;
        }

        pendingOutputWrite = new PendingOutputWrite(pending.path(), pending.line(), pending.kind(), remaining);
        return false;
    }

    private boolean tryAppendLine(Path path, String line) {
        if (path == null) return false;
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(
                path,
                line + System.lineSeparator(),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
                StandardOpenOption.APPEND
            );
            return true;
        } catch (IOException e) {
            THMAddon.LOG.warn("Highway Checker failed to append to {}: {}", path, e.getMessage());
            return false;
        }
    }

    private void debug(String event, String format, Object... args) {
        if (!debugLog.get() || debugPath == null) return;
        String line = String.format(Locale.ROOT, "%s session=%s event=%s %s",
            Instant.now(),
            sessionId == null ? "none" : sessionId,
            event,
            String.format(Locale.ROOT, format, args)
        );
        appendLine(debugPath, line);
    }

    private void notifyEvent(String format, Object... args) {
        String message = String.format(Locale.ROOT, format, args);
        if (notifyChat.get()) info(message);
        if (notifyDesktop.get()) THMUtils.Notify("Highway Checker", message);
    }

    private int countObsidianInChunk(LocalPlayer player) {
        ChunkPos chunkPos = ChunkPos.containing(player.blockPosition());
        LevelChunk chunk = mc.level.getChunk(chunkPos.x(), chunkPos.z());

        int minX = chunkPos.getMinBlockX();
        int minZ = chunkPos.getMinBlockZ();
        int maxX = chunkPos.getMaxBlockX();
        int maxZ = chunkPos.getMaxBlockZ();
        int minY = mc.level.getMinY();
        int maxY = mc.level.getMinY() + mc.level.getHeight();
        int count = 0;

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = minY; y < maxY; y++) {
                    if (chunk.getBlockState(new BlockPos(x, y, z)).getBlock() == Blocks.OBSIDIAN) {
                        count++;
                        if (guardMode.get() == CheckerMode.HighwayStart && count > obsidianThreshold.get()) {
                            return count;
                        }
                    }
                }
            }
        }
        return count;
    }

    private void disconnectPlayer() {
        if (mc.getConnection() != null) {
            toggle();

            MutableComponent text = Component.literal("[")
                .withStyle(style -> style.withColor(ChatFormatting.WHITE))
                .append(Component.literal("HighwayFinder").withStyle(style -> style.withColor(ChatFormatting.BLUE)))
                .append(Component.literal("] ").withStyle(style -> style.withColor(ChatFormatting.WHITE)))
                .append(Component.literal("Highway boundary reached.").withStyle(style -> style.withColor(ChatFormatting.RED)));

            mc.getConnection().getConnection().disconnect(text);
        }
    }

    private void sendWebhookAsync(String message, String kind) {
        if (!webhookEnabled.get()) return;
        String url = webhookUrl.get().trim();
        if (url.isEmpty()) return;
        JsonObject payloadJson = new JsonObject();
        payloadJson.addProperty("content", message);
        String payload = payloadJson.toString();

        THMUtils.async("highway-checker-webhook", () -> {
            if (!TrustedHttp.postJson(url, payload, TrustedHttp.Kind.USER_WEBHOOK, null)) {
                debug("webhook", "send-failed kind=%s", kind);
            }
        });
    }

    private void fail(String format, Object... args) {
        String message = String.format(Locale.ROOT, format, args);
        pendingStopReason = "hard-fail";
        debug("fail", "%s", message);
        if (notifyDesktop.get()) THMUtils.Notify("Highway Checker", message);
        error(message);
        toggle();
    }

    private void alignPlayerToLockedLine() {
        if (mc.player == null || line == null) return;

        double[] projected = projectToLine(mc.player.getX(), mc.player.getZ(), line);
        mc.player.setDeltaMovement(0.0, mc.player.getDeltaMovement().y, 0.0);
        mc.player.setPos(projected[0], mc.player.getY(), projected[1]);
        mc.player.setYRot(yawForStep(direction.dx, direction.dz));
    }

    private BlockPos currentCenterBlock() {
        if (mc.player == null || line == null) return BlockPos.ZERO;
        double[] projected = projectToLine(mc.player.getX(), mc.player.getZ(), line);
        return new BlockPos(floorToBlock(projected[0]), SAMPLE_Y_LOW, floorToBlock(projected[1]));
    }

    private double[] projectToLine(double x, double z, WorkLine workLine) {
        return closestPointOnLine(x, z, workLine.a, workLine.b, workLine.c);
    }

    private double distanceToLockedLine(double x, double z, WorkLine workLine) {
        return distanceToLine(x, z, workLine.a, workLine.b, workLine.c);
    }

    private double lineDistancePenalty(int x, int z) {
        return distanceToLockedLine(x + 0.5, z + 0.5, line) * OFF_LINE_PENALTY;
    }

    private static double distanceToLine(double x, double z, double a, double b, double c) {
        return Math.abs(a * x + b * z - c) / Math.sqrt(a * a + b * b);
    }

    private static double[] closestPointOnLine(double x, double z, double a, double b, double c) {
        double denom = a * a + b * b;
        if (denom == 0.0) return new double[] { x, z };

        double t = (a * x + b * z - c) / denom;
        return new double[] { x - a * t, z - b * t };
    }

    private static int floorToBlock(double value) {
        return Mth.floor(value);
    }

    private static long pack(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }

    private static int unpackX(long packed) {
        return (int) (packed >> 32);
    }

    private static int unpackZ(long packed) {
        return (int) packed;
    }

    private static double octileDistance(int x1, int z1, int x2, int z2) {
        int dx = Math.abs(x2 - x1);
        int dz = Math.abs(z2 - z1);
        int min = Math.min(dx, dz);
        int max = Math.max(dx, dz);
        return min * STEP_DIAGONAL_COST + (max - min) * STEP_STRAIGHT_COST;
    }

    private static float yawForStep(int dx, int dz) {
        return (float) Math.toDegrees(Math.atan2(-dx, dz));
    }

    private static String blockId(BlockState state) {
        return BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
    }

    private static String formatPos(BlockPos pos) {
        return pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    private void stopMovementKeys() {
        if (mc.options == null) return;
        mc.options.keyUp.setDown(false);
        mc.options.keyDown.setDown(false);
        mc.options.keyLeft.setDown(false);
        mc.options.keyRight.setDown(false);
        mc.options.keyJump.setDown(false);
        mc.options.keySprint.setDown(false);
        mc.options.keyShift.setDown(false);
    }

    public enum CheckerMode implements DescribedOption {
        HighwayEnd,
        HighwayStart;

        @Override
        public String description() {
            return switch (this) {
                case HighwayEnd -> "Trigger where the highway ends.";
                case HighwayStart -> "Trigger where the highway begins.";
            };
        }
    }

    public enum Highway implements DescribedOption {
        West,
        East,
        North,
        South,
        NorthEast,
        SouthEast,
        SouthWest,
        NorthWest,
        DugWest,
        DugEast,
        DugNorth,
        DugSouth,
        DugNorthEast,
        DugSouthEast,
        DugSouthWest,
        DugNorthWest;

        @Override
        public String description() {
            return switch (this) {
                case West -> "Use the west highway.";
                case DugWest -> "Use the dug west highway.";
                case East -> "Use the east highway.";
                case DugEast -> "Use the dug east highway.";
                case North -> "Use the north highway.";
                case DugNorth -> "Use the dug north highway.";
                case South -> "Use the south highway.";
                case DugSouth -> "Use the dug south highway.";
                case NorthEast -> "Use the northeast highway.";
                case DugNorthEast -> "Use the dug northeast highway.";
                case SouthEast -> "Use the southeast highway.";
                case DugSouthEast -> "Use the dug southeast highway.";
                case SouthWest -> "Use the southwest highway.";
                case DugSouthWest -> "Use the dug southwest highway.";
                case NorthWest -> "Use the northwest highway.";
                case DugNorthWest -> "Use the dug northwest highway.";
            };
        }
    }

    private enum WorkLine {
        X_AXIS("x-axis", 0.0, 1.0, 0.5),
        Z_AXIS("z-axis", 1.0, 0.0, 0.5),
        NW_SE("nw-se", 1.0, -1.0, 0.0),
        NE_SW("ne-sw", 1.0, 1.0, 1.0);

        private final String label;
        private final double a;
        private final double b;
        private final double c;

        WorkLine(String label, double a, double b, double c) {
            this.label = label;
            this.a = a;
            this.b = b;
            this.c = c;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private enum TravelDirection {
        EAST("east", 1, 0, WorkLine.X_AXIS),
        SOUTH_EAST("south-east", 1, 1, WorkLine.NW_SE),
        SOUTH("south", 0, 1, WorkLine.Z_AXIS),
        SOUTH_WEST("south-west", -1, 1, WorkLine.NE_SW),
        WEST("west", -1, 0, WorkLine.X_AXIS),
        NORTH_WEST("north-west", -1, -1, WorkLine.NW_SE),
        NORTH("north", 0, -1, WorkLine.Z_AXIS),
        NORTH_EAST("north-east", 1, -1, WorkLine.NE_SW);

        private final String label;
        private final int dx;
        private final int dz;
        private final WorkLine line;

        TravelDirection(String label, int dx, int dz, WorkLine line) {
            this.label = label;
            this.dx = dx;
            this.dz = dz;
            this.line = line;
        }

        private int[] perpendicular() {
            return new int[] { -dz, dx };
        }

        private double normX() {
            return dx == 0 || dz == 0 ? dx : dx / Math.sqrt(2.0);
        }

        private double normZ() {
            return dx == 0 || dz == 0 ? dz : dz / Math.sqrt(2.0);
        }

        private static TravelDirection fromYaw(float yaw) {
            double radians = Math.toRadians(yaw);
            double fx = -Math.sin(radians);
            double fz = Math.cos(radians);

            TravelDirection best = SOUTH;
            double bestDot = Double.NEGATIVE_INFINITY;
            for (TravelDirection value : values()) {
                double dot = fx * value.normX() + fz * value.normZ();
                if (dot > bestDot) {
                    bestDot = dot;
                    best = value;
                }
            }
            return best;
        }
    }

    private record AStarNode(int x, int z, double g, double f) {}

    private record SampleSnapshot(
        boolean trusted,
        BlockPos center,
        Vec3 playerPos,
        boolean startsSection,
        boolean stopsSection,
        List<String> y118Blocks,
        List<String> y119Blocks
    ) {}

    private record SectionProbe(boolean open, boolean mineable, List<BlockPos> obstacles) {}

    private record StepDecision(
        int dx,
        int dz,
        SectionProbe probe,
        boolean requiresMining,
        String mode,
        int obstacleCount
    ) {}

    private record BoatPlacementCandidate(
        BlockPos floorAnchor,
        BlockHitResult hitResult,
        BlockPos placePos
    ) {}

    private record PendingOutputWrite(
        Path path,
        String line,
        String kind,
        int ticksRemaining
    ) {}

    private enum BoundaryType {
        SAMPLE,
        START,
        STOP
    }

    private enum SectionPhase {
        LOOKING_FOR_ANY,
        LOOKING_FOR_START,
        LOOKING_FOR_STOP
    }
}
