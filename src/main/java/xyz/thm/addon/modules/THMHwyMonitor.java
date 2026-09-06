/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.modules;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.pathing.goals.GoalBlock;
import baritone.api.utils.Rotation;
import meteordevelopment.meteorclient.events.game.GameJoinedEvent;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.events.meteor.ActiveModulesChangedEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.pathing.BaritoneUtils;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.movement.speed.Speed;
import meteordevelopment.meteorclient.systems.modules.world.Timer;
import meteordevelopment.meteorclient.utils.misc.HorizontalDirection;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.DisconnectedScreen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.ChunkStatus;
import xyz.thm.addon.THMAddon;
import xyz.thm.addon.utils.server.ServerReconnectService;
import xyz.thm.addon.utils.server.ServerStatusHandler;
import xyz.thm.addon.utils.server.ServerStatusHandler.ServerState;
import xyz.thm.addon.utils.ThmMembers;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import xyz.thm.addon.utils.THMUtils;
import static xyz.thm.addon.utils.THMUtils.getSaveName;

public class THMHwyMonitor extends Module {
    private static final double WORKING_LINE_TOLERANCE = 0.1;
    private static final double Y_ALIGNMENT_TOLERANCE = 0.4;
    private static final double DIRECTION_RESOLUTION_TOLERANCE = 0.4;
    private static final double FORWARD_PROGRESS_RESET_EPSILON = 0.125;
    private static final double HUGE_DISTANCE = 1.0e30;
    private static final int RECOVERY_DELAY_TICKS = 40;
    private static final int YAW_SET_DELAY_TICKS = 10;
    private static final int BARITONE_PATH_STARTUP_TICKS = 10;
    private static final int BARITONE_PATH_TIMEOUT_TICKS = 20 * 20;
    private static final int BARITONE_RECOVERY_MAX_START_ATTEMPTS = 5;
    private static final int BARITONE_RECOVERY_RETRY_DELAY_TICKS = 20;
    private static final int BARITONE_STOP_SETTLE_TICKS = 2;
    private static final int BLOCKED_CENTER_STALL_TICKS = 5 * 20;
    private static final long ALIGNMENT_GATE_TIMEOUT_MS = 10_000L;
    private static final int POST_REJOIN_AXIS_PROBE_DISTANCE = 20;
    private static final int POST_REJOIN_AXIS_NEAR_PROBE_DISTANCE = 8;
    private static final double RECONNECT_LINE_AMBIGUITY_THRESHOLD = 0.05;
    private static final double RECONNECT_LINE_MAX_DISTANCE = 6.0;
    private static final long POST_REJOIN_DIRECTION_RETRY_DELAY_MS = 1_000L;
    private static final int POST_REJOIN_DIRECTION_RETRY_LIMIT = 30;
    private static final int RESTART_SCREENSHOT_DELAY_MS = 2000;
    private static final long MAIN_SERVER_RESUME_DELAY_MS = 6_000L;
    private static final int DISCONNECT_SCREEN_EVIDENCE_TIMEOUT_MS = 3000;
    private static final String RECONNECT_RESUME_LISTENER_KEY = "thm-hwymonitor-resume";
    private static final String RECONNECT_FAILURE_LISTENER_KEY = "thm-hwymonitor-failure";
    private static final boolean RUNTIME_WATCHDOG_LOG_ENABLED = false;
    private static final boolean EXECUTION_TRACE_LOG_ENABLED = false;
    private static final String BARITONE_PATH_COMPLETE_MARKER = "pathing complete";
    private static final String CRACKED_LOGIN_SUCCESS_MARKER = "you are now logged in!";
    private static final String LOGIN_PROMPT_MARKER = "please login with the command: /login";
    private static final String RESTART_DETECTED_MARKER = "server restart detected";
    private static final String RESTOCK_FAILURE_MARKER = "unable to perform restock";
    private static final String THM_HIGHWAYBUILDER_TAG_A = "thm highwaybuilder";
    private static final String THM_HIGHWAYBUILDER_TAG_B = "thm-highwaybuilder";
    private static final String AUTO_LOG_TAG = "[autolog]";
    private static final long RESTART_EVIDENCE_TTL_MS = 20_000L;
    private static final AtomicBoolean NON_RESTART_HARD_FAIL_SIGNAL = new AtomicBoolean(false);
    private static final AtomicBoolean RESTART_HARD_FAIL_SIGNAL = new AtomicBoolean(false);
    private static final int GHOSTBLOCK_LOW_RATE_SAMPLE_TICKS = 20;
    private static final int GHOSTBLOCK_ESCALATE_TICKS = 20 * 20;
    private static final int GHOSTBLOCK_CONFIRM_TICKS = 2 * 20;
    private static final int GHOSTBLOCK_NO_PROGRESS_TRIGGER_TICKS = 5 * 60 * 20;
    private static final int RUBBERBAND_FORWARD_WARMUP_TICKS = 15 * 20;
    private static final int RUBBERBAND_EVENT_WINDOW_TICKS = 90 * 20;
    private static final int RUBBERBAND_EVENT_TRIGGER_COUNT = 3;
    private static final int RUBBERBAND_RECONNECT_DELAY_SECONDS = 60;
    private static final int FORWARD_CORRECTION_FAST_WINDOW_TICKS = 10 * 20;
    private static final int FORWARD_CORRECTION_FAST_TRIGGER_COUNT = 120;
    private static final int FORWARD_CORRECTION_FAST_MIN_WATCH_TICKS = 3 * 20;
    private static final int FORWARD_CORRECTION_SLOW_WINDOW_TICKS = 20 * 20;
    private static final int FORWARD_CORRECTION_SLOW_TRIGGER_COUNT = 60;
    private static final int FORWARD_CORRECTION_SLOW_MIN_STALLED_TICKS = 10 * 20;
    private static final int FORWARD_PACKET_DESYNC_WINDOW_TICKS = 60;
    private static final int FORWARD_PACKET_DESYNC_MIN_ACTIONS = 20;
    private static final int FORWARD_PACKET_DESYNC_MAX_DISTINCT_TARGETS = 16;
    private static final double GHOSTBLOCK_CONFIRMED_PROGRESS_BLOCKS = 0.75;
    private static final double RUBBERBAND_BACKTRACK_BLOCKS = 1.5;

    private static final int[] RING_ROADS = new int[] {
        200, 500, 750, 1000, 1500, 2000, 2500, 5000, 7500, 10000, 15000, 20000, 25000,
        50000, 55000, 62500, 75000, 100000, 125000, 250000, 500000, 750000, 1000000,
        1250000, 1568852, 1875000, 2500000, 3750000
    };

    private static final int[] DIAMONDS = new int[] {
        1000, 2000, 2500, 5000, 25000, 50000, 125000, 250000, 500000, 3750000
    };

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> autoRecover = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-recover")
        .description("Auto-corrects misalignment while THM HighwayBuilder is active.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> trueCenterMode = sgGeneral.add(new BoolSetting.Builder()
        .name("true-center-mode")
        .description("Use 0.5-centered highway math for alignment and recovery.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> checkInterval = sgGeneral.add(new IntSetting.Builder()
        .name("check-interval")
        .description("How often to check alignment while HighwayBuilder is active.")
        .defaultValue(2)
        .range(1, 20)
        .sliderRange(1, 10)
        .build()
    );

    private final Setting<Double> maxCorrectionDistance = sgGeneral.add(new DoubleSetting.Builder()
        .name("max-correction-distance")
        .description("Maximum horizontal distance that can be corrected automatically.")
        .defaultValue(10.0)
        .range(0.5, 32.0)
        .sliderRange(0.5, 16.0)
        .build()
    );

    private final Setting<Boolean> repairMisalignments = sgGeneral.add(new BoolSetting.Builder()
        .name("repair-misalignments")
        .description("Step back 2 blocks during recovery to repair misaligned work.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> recoverForwardStalls = sgGeneral.add(new BoolSetting.Builder()
        .name("recover-forward-stalls")
        .description("Runs recovery when HighwayBuilder stays stuck in Forward or Center.")
        .defaultValue(true)
        .visible(autoRecover::get)
        .build()
    );

    private final Setting<Integer> forwardStallTimeoutSeconds = sgGeneral.add(new IntSetting.Builder()
        .name("forward-stall-timeout-seconds")
        .description("Seconds without HighwayBuilder progress before the stall escape begins.")
        .defaultValue(20)
        .range(10, 900)
        .sliderRange(10, 300)
        .visible(() -> autoRecover.get() && recoverForwardStalls.get())
        .build()
    );

    private final Setting<Boolean> recoverRubberbandGhostblocks = sgGeneral.add(new BoolSetting.Builder()
        .name("recover-rubberband-ghostblocks")
        .description("Reconnects if HighwayBuilder looks rubberbanded or ghostblocked.")
        .defaultValue(true)
        .visible(autoRecover::get)
        .build()
    );

    private final Setting<Integer> recoveryCooldown = sgGeneral.add(new IntSetting.Builder()
        .name("recovery-cooldown")
        .description("Ticks to wait before checking again after a recovery attempt.")
        .defaultValue(10)
        .range(1, 100)
        .sliderRange(1, 40)
        .build()
    );

    private final Setting<Boolean> autoReconnect = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-reconnect")
        .description("Handles Automatically Reconnecting on Disconnects, and Restarting HighwayBuilderTHM")
        .defaultValue(false)
        .visible(this::exposeRestartAutomationSettings)
        .build()
    );

    private final Setting<Integer> restartRejoinDelayMinutes = sgGeneral.add(new IntSetting.Builder()
        .name("restart-rejoin-delay-minutes")
        .description("Delay in minutes applied to Meteor AutoReconnect.")
        .defaultValue(15)
        .range(1, 240)
        .sliderRange(1, 60)
        .visible(this::exposeRestartAutomationSettings)
        .build()
    );

    private boolean exposeRestartAutomationSettings() {
        return !ThmMembers.isNovice(getSaveName());
    }

    private int ticksSinceCheck;
    private int cooldownTicks;
    private HighwayBuilderTHM recoveryBuilder;
    private RecoveryTarget pendingCorrectionTarget;
    private RecoveryTarget pendingLocalStallEscapeTarget;
    private int recoveryTicks;
    private int baritoneStartupTicks;
    private int baritoneTimeoutTicks;
    private int baritoneRecoveryStartAttempts;
    private boolean baritoneStopCommandTried;
    private RecoveryPhase recoveryPhase = RecoveryPhase.None;
    private RecoveryCause recoveryCause = RecoveryCause.None;
    private boolean recoveryModulesPaused;
    private final List<Module> recoveryPausedModules = new ArrayList<>();
    private HighwaySegment trackedSegment;
    private String trackedDirection;
    private float recoveryYawBeforeMove = Float.NaN;
    // Restart automation subsystem. This code stays in the file but must remain fully dormant
    // unless reconnectAutomationEnabled() returns true.
    private volatile boolean restartScreenshotScheduled;
    private boolean postJoinModuleStateCaptured;
    private boolean timerWasActiveBeforePostJoin;
    private boolean speedWasActiveBeforePostJoin;
    private boolean nonRestartHardFailArmed;
    private boolean unresolvedMainServerDisconnectCandidate;
    private boolean deferRestartScreenshotUntilReconnect;
    private boolean deferredRestartScreenshotAfterReconnectPending;
    private boolean pendingDisconnectScreenEvidenceCheck;
    private long pendingDisconnectScreenEvidenceUntilMs;
    private boolean restartModuleStateSnapshotTaken;
    private boolean wasConnectedLastTick;
    private boolean restartDisconnectEvidenceArmed;
    private long restartDisconnectEvidenceAtMs;
    private String restartDisconnectEvidenceSource = "";
    private float lastReliableRecoveryYaw = Float.NaN;
    private float preTickYawSnapshot = Float.NaN;
    private long preTickYawSnapshotAtMs;
    private StallWatchMode stallWatchMode = StallWatchMode.None;
    private HorizontalDirection stallWatchDirection;
    private double bestForwardProgressCoordinate;
    private int stallWatchTicks;
    private boolean internalTimerSpeedToggleInProgress;
    private static Field disconnectedScreenReasonField;
    private static boolean disconnectedScreenReasonFieldResolved;
    private CompletableFuture<ServerState> pendingAlignmentGateFuture;
    private long pendingAlignmentGateAttemptId;
    private long nextAlignmentGateAttemptId = 1L;
    private long activeReconnectCycleId;
    private ReconnectOwner reconnectOwner = ReconnectOwner.None;
    private long restartEvidenceGateCycleId;
    private boolean delayedMainServerResumePending;
    private long delayedMainServerResumeCycleId;
    private long delayedMainServerResumeAtMs;
    private String delayedMainServerResumeContext = "";
    private boolean restartRecoveryActive;
    private THMStashMover recoveryStashMover;
    private boolean postRejoinDirectionGateActive;
    private int postRejoinDirectionRetryCount;
    private long postRejoinDirectionNextAttemptAtMs;
    private String postRejoinDirectionBlockReason = "";
    private String postRejoinDirectionBlockSummary = "";
    private boolean postRejoinBlockedScreenshotTaken;
    private boolean postRejoinTerminalScreenshotTaken;
    private HorizontalDirection postRejoinLastCompleteProbeWinner;
    private boolean intentionalSafetyDisconnectArmed;
    private boolean disableMonitorAfterIntentionalSafetyDisconnect;
    private long pendingAbortRestoreCycleId;
    private String pendingAbortRestoreReason = "";
    private long pendingAbortRestoreNextAttemptAtMs;
    private boolean previousAutoReconnectToggleState;
    private boolean rearmNormalReconnectAfterForwardReconnectResume;
    private HorizontalDirection ghostblockWatchDirection;
    private boolean ghostblockWatchActive;
    private boolean ghostblockTickSamplingActive;
    private int ghostblockObservationTicks;
    private int ghostblockNoConfirmedProgressTicks;
    private int ghostblockLowRateSampleTicks;
    private boolean ghostblockCandidateActive;
    private double ghostblockCandidateCoordinate;
    private int ghostblockCandidateTicks;
    private double ghostblockConfirmedBestCoordinate;
    private double ghostblockRecentPeakCoordinate;
    private double ghostblockLastProjectedCoordinate;
    private boolean ghostblockHasLastProjectedCoordinate;
    private final List<Integer> ghostblockRubberbandEventTicks = new ArrayList<>();
    private final List<Integer> ghostblockCorrectionPacketTicks = new ArrayList<>();
    private final AtomicInteger pendingForwardCorrectionPackets = new AtomicInteger();
    private volatile boolean forwardCorrectionPacketWatchArmed;
    private final List<ForwardDestroyPacketSample> forwardDestroyPacketSamples = new ArrayList<>();
    private HorizontalDirection forwardPacketDesyncDirection;
    private boolean forwardPacketDesyncEpisodeActive;
    private boolean forwardPacketDesyncWiggleUsed;
    private boolean forwardPacketDesyncAwaitingRetry;
    private int forwardPacketDesyncEpisodeId;
    private String forwardPacketDesyncLastSummary = "";

    private record AxisProbeResult(
        boolean allSamplesLoaded,
        boolean strongWinner,
        HorizontalDirection selectedDirection,
        HorizontalDirection dirA,
        int dirAScore,
        HorizontalDirection dirB,
        int dirBScore
    ) {}

    private record PostRejoinDirectionResult(
        HorizontalDirection direction,
        String reason,
        String summary
    ) {
        private static PostRejoinDirectionResult success(HorizontalDirection direction, String summary) {
            return new PostRejoinDirectionResult(direction, "", summary);
        }

        private static PostRejoinDirectionResult blocked(String reason, String summary) {
            return new PostRejoinDirectionResult(null, reason, summary);
        }

        private boolean conclusive() {
            return direction != null;
        }
    }

    private record ForwardDestroyPacketSample(
        int tick,
        BlockPos pos,
        PlayerActionC2SPacket.Action action
    ) {}

    private record ForwardPacketDesyncWindow(
        int actions,
        int starts,
        int aborts,
        int distinctTargets,
        int firstTick,
        int lastTick,
        String summary
    ) {}

    private record HighwaySegment(
        String highway,
        int roadValue,
        String segmentLabel,
        WorkLine line
    ) {
        private boolean isRingOrDiamond() {
            return "Ring".equals(highway) || "Diamond".equals(highway);
        }
    }

    private record SegmentProjection(
        HighwaySegment segment,
        double targetX,
        double targetZ,
        double distance
    ) {}

    private enum ReconnectOwner {
        None,
        HighwayBuilder,
        StashMover
    }

    public THMHwyMonitor() {
        super(THMAddon.MAIN, "THM Highway Monitor", "Monitors alignment and recovers HighwayBuilder from drift.");
        runInMainMenu = true;
    }

    public static void signalNonRestartHardFailFromHighwayBuilder() {
        NON_RESTART_HARD_FAIL_SIGNAL.set(true);
    }

    public static void signalRestartHardFailFromHighwayBuilder() {
        RESTART_HARD_FAIL_SIGNAL.set(true);
    }

    public void prepareForAutoLogTerminalLogout(String reason) {
        String safeReason = reason == null || reason.isBlank() ? "unknown" : reason;
        abortActiveRecoveryForNonRestartHardFail();
        clearHighwayBuilderReconnectModuleRestoreSnapshot("autolog-terminal-logout:" + safeReason);
        clearRestartAutomationState("autolog-terminal-logout:" + safeReason, true, true);
        clearRestartDisconnectEvidence();
        unresolvedMainServerDisconnectCandidate = false;
        deferRestartScreenshotUntilReconnect = false;
        deferredRestartScreenshotAfterReconnectPending = false;
        pendingDisconnectScreenEvidenceCheck = false;
        pendingDisconnectScreenEvidenceUntilMs = 0L;
        nonRestartHardFailArmed = true;
        signalNonRestartHardFailFromHighwayBuilder();
    }

    public boolean beginStashMoverReconnectHandling(THMStashMover stashMover, String source) {
        if (stashMover == null || !stashMover.isActive() || !stashMover.isManagingThmHwyMonitorReconnect()) return false;

        recoveryStashMover = stashMover;
        reconnectOwner = ReconnectOwner.StashMover;
        restartRecoveryActive = true;
        resetForwardProgressWatch();
        resetRubberbandGhostblockWatch();
        resetForwardPacketDesyncEpisode("stash-mover-reconnect");
        clearPendingAlignmentGateRequest();
        clearPostRejoinDirectionGateState();

        if (activeReconnectCycleId != 0L && reconnectService().isReconnectArmed()) return true;

        long cycleId;
        ServerReconnectService.ReconnectPreflight preflight = reconnectService().getReconnectPreflight();
        if (preflight.serviceArmed() && preflight.cycleId() > 0L) {
            cycleId = preflight.cycleId();
            activeReconnectCycleId = cycleId;
        } else {
            String safeSource = source == null || source.isBlank() ? "unknown" : source;
            cycleId = armReconnectCycle("stash-mover-" + safeSource, false);
        }

        info("StashMover armed THMHwyMonitor reconnect handling (cycle %d).", cycleId);
        return true;
    }

    public void clearStashMoverReconnectHandling(THMStashMover stashMover, String reason) {
        boolean ownsStashMover = reconnectOwner == ReconnectOwner.StashMover || recoveryStashMover == stashMover;
        if (!ownsStashMover) return;

        clearStashMoverReconnectState(reason == null || reason.isBlank() ? "stash-mover-clear" : reason, true, true);
        if (autoReconnect.get() && isActive()) {
            long cycleId = armReconnectCycle("stash-mover-clear-auto-reconnect-restore", false);
            reconnectOwner = ReconnectOwner.HighwayBuilder;
            info("Restored normal THMHwyMonitor AutoReconnect ownership after StashMover clear (cycle %d).", cycleId);
        }
    }

    private static boolean consumeNonRestartHardFailSignal() {
        return NON_RESTART_HARD_FAIL_SIGNAL.getAndSet(false);
    }

    private static boolean consumeRestartHardFailSignal() {
        return RESTART_HARD_FAIL_SIGNAL.getAndSet(false);
    }

    private static void clearNonRestartHardFailSignal() {
        NON_RESTART_HARD_FAIL_SIGNAL.set(false);
    }

    private static void clearRestartHardFailSignal() {
        RESTART_HARD_FAIL_SIGNAL.set(false);
    }

    private static boolean isHighwayBuilderTaggedMessage(String lower) {
        return lower.contains(THM_HIGHWAYBUILDER_TAG_A) || lower.contains(THM_HIGHWAYBUILDER_TAG_B);
    }

    private static boolean isAutoLogTaggedMessage(String lower) {
        return lower != null && lower.contains(AUTO_LOG_TAG);
    }

    private static boolean isRestartHardFailMessage(String lower) {
        return isHighwayBuilderTaggedMessage(lower) && lower.contains(RESTART_DETECTED_MARKER);
    }

    private static boolean isKnownNonRestartHardFailMessage(String lower) {
        return isAutoLogTaggedMessage(lower)
            || (isHighwayBuilderTaggedMessage(lower) && lower.contains(RESTOCK_FAILURE_MARKER));
    }

    private void armRestartDisconnectEvidence(String source) {
        restartDisconnectEvidenceArmed = true;
        restartDisconnectEvidenceAtMs = System.currentTimeMillis();
        restartDisconnectEvidenceSource = source;
    }

    private void clearRestartDisconnectEvidence() {
        restartDisconnectEvidenceArmed = false;
        restartDisconnectEvidenceAtMs = 0L;
        restartDisconnectEvidenceSource = "";
    }

    private String consumeRestartDisconnectEvidence() {
        String screenReason = readDisconnectedScreenReasonLower();
        if (isRestartHardFailMessage(screenReason)) {
            clearRestartDisconnectEvidence();
            return "disconnect-screen";
        }

        if (restartDisconnectEvidenceArmed) {
            long ageMs = System.currentTimeMillis() - restartDisconnectEvidenceAtMs;
            if (ageMs <= RESTART_EVIDENCE_TTL_MS) {
                String source = restartDisconnectEvidenceSource == null || restartDisconnectEvidenceSource.isEmpty()
                    ? "message"
                    : restartDisconnectEvidenceSource;
                clearRestartDisconnectEvidence();
                return source;
            }

            clearRestartDisconnectEvidence();
        }

        return null;
    }

    private String readDisconnectedScreenReasonLower() {
        if (mc == null || !(mc.currentScreen instanceof DisconnectedScreen screen)) return "";

        Text reason = null;
        try {
            if (!disconnectedScreenReasonFieldResolved) {
                disconnectedScreenReasonFieldResolved = true;
                try {
                    disconnectedScreenReasonField = DisconnectedScreen.class.getDeclaredField("reason");
                    disconnectedScreenReasonField.setAccessible(true);
                } catch (NoSuchFieldException ignored) {
                    for (Field field : DisconnectedScreen.class.getDeclaredFields()) {
                        if (Text.class.isAssignableFrom(field.getType())) {
                            field.setAccessible(true);
                            disconnectedScreenReasonField = field;
                            break;
                        }
                    }
                }
            }

            if (disconnectedScreenReasonField != null) {
                Object value = disconnectedScreenReasonField.get(screen);
                if (value instanceof Text text) reason = text;
            }
        } catch (Throwable ignored) {
            // If reflection fails we still have message-based restart evidence.
        }

        if (reason == null) return "";
        return reason.getString().toLowerCase(Locale.ROOT);
    }

    @Override
    public void onActivate() {
        cacheRecoveryYawOnMonitorToggle();
        if (Float.isNaN(lastReliableRecoveryYaw) && mc != null && mc.player != null) {
            lastReliableRecoveryYaw = mc.player.getYaw();
        }
        preTickYawSnapshot = lastReliableRecoveryYaw;
        preTickYawSnapshotAtMs = System.currentTimeMillis();
        ticksSinceCheck = 0;
        cooldownTicks = 0;
        recoveryBuilder = null;
        pendingCorrectionTarget = null;
        recoveryTicks = 0;
        baritoneStartupTicks = 0;
        baritoneTimeoutTicks = 0;
        baritoneRecoveryStartAttempts = 0;
        baritoneStopCommandTried = false;
        recoveryPhase = RecoveryPhase.None;
        recoveryModulesPaused = false;
        recoveryPausedModules.clear();
        trackedSegment = null;
        trackedDirection = "";
        recoveryYawBeforeMove = Float.NaN;
        resetForwardProgressWatch();
        resetRubberbandGhostblockWatch();
        resetForwardPacketDesyncEpisode("activate");
        clearPendingAlignmentGateRequest();
        clearPostRejoinDirectionGateState();
        resetReconnectAutomationState(true);
        discoverPendingAbortRestore("monitor-activate");
        registerReconnectServiceListeners();
        wasConnectedLastTick = isSuccessfullyConnectedToServer();
        if (reconnectAutomationEnabled()) refreshTimerSpeedSnapshotFromCurrentState("activate");
        previousAutoReconnectToggleState = autoReconnect.get();
        if (autoReconnect.get()) {
            armReconnectCycle("onActivate", false);
            reconnectOwner = ReconnectOwner.HighwayBuilder;
        }
    }

    public boolean usesTrueCenterMode() {
        return trueCenterMode.get();
    }

    public static HorizontalDirection inferClosestWorkingDirection(double playerX, double playerZ, float yaw, boolean trueCenterMode) {
        if (Float.isNaN(yaw)) return null;
        SegmentProjection projection = selectBestSegmentProjection(
            collectSegmentProjections(playerX, playerZ, true, true, true, true, trueCenterMode),
            null,
            "",
            yaw,
            0.0
        );
        if (projection == null) return null;

        return parseDirectionCode(chooseSegmentTravelDirection(projection.segment(), yaw, ""));
    }

    @Override
    public void onDeactivate() {
        lastReliableRecoveryYaw = Float.NaN;
        preTickYawSnapshot = Float.NaN;
        preTickYawSnapshotAtMs = 0L;
        recoveryBuilder = null;
        pendingCorrectionTarget = null;
        recoveryTicks = 0;
        baritoneStartupTicks = 0;
        baritoneTimeoutTicks = 0;
        baritoneRecoveryStartAttempts = 0;
        baritoneStopCommandTried = false;
        recoveryPhase = RecoveryPhase.None;
        resumePausedModulesAfterRecovery();
        trackedSegment = null;
        trackedDirection = "";
        recoveryYawBeforeMove = Float.NaN;
        resetForwardProgressWatch();
        resetRubberbandGhostblockWatch();
        resetForwardPacketDesyncEpisode("deactivate");
        clearPendingAlignmentGateRequest();
        clearPostRejoinDirectionGateState();
        if (restartRecoveryActive
            && reconnectOwner == ReconnectOwner.HighwayBuilder
            && activeReconnectCycleId > 0L) {
            schedulePendingAbortRestore(activeReconnectCycleId, "monitor-deactivate");
        }
        unregisterReconnectServiceListeners();
        clearRestartAutomationState("deactivate", true, true);
        tryPendingAbortRestore("monitor-deactivate");
        wasConnectedLastTick = false;
    }

    private void cacheRecoveryYawOnMonitorToggle() {
        if (mc == null) return;

        float yaw = Float.NaN;
        if (mc.player != null) yaw = mc.player.getYaw();
        else if (mc.getCameraEntity() != null) yaw = mc.getCameraEntity().getYaw();
        if (Float.isNaN(yaw)) return;

        lastReliableRecoveryYaw = yaw;
        preTickYawSnapshot = yaw;
        preTickYawSnapshotAtMs = System.currentTimeMillis();
    }

    private ServerReconnectService reconnectService() {
        return ServerReconnectService.getInstance();
    }

    public boolean ownsIntegratedFreelookControl() {
        return isActive() && (recoveryModulesPaused || recoveryPhase != RecoveryPhase.None || postRejoinDirectionGateActive);
    }

    private void registerReconnectServiceListeners() {
        reconnectService().registerResumeListener(RECONNECT_RESUME_LISTENER_KEY, this::onReconnectMainServerReady);
        reconnectService().registerFailureListener(RECONNECT_FAILURE_LISTENER_KEY, this::onReconnectFailure);
    }

    private void unregisterReconnectServiceListeners() {
        reconnectService().unregisterResumeListener(RECONNECT_RESUME_LISTENER_KEY);
        reconnectService().unregisterFailureListener(RECONNECT_FAILURE_LISTENER_KEY);
    }

    private void onReconnectMainServerReady(long cycleId, String contextTag, long armedAtMs, long detectedAtMs) {
        if (mc != null && !mc.isOnThread()) {
            mc.execute(() -> onReconnectMainServerReady(cycleId, contextTag, armedAtMs, detectedAtMs));
            return;
        }
        if (!isActive()) return;
        if (cycleId != activeReconnectCycleId) {
            return;
        }

        if (reconnectOwner == ReconnectOwner.StashMover) {
            delayedMainServerResumePending = true;
            delayedMainServerResumeCycleId = cycleId;
            delayedMainServerResumeAtMs = System.currentTimeMillis() + MAIN_SERVER_RESUME_DELAY_MS;
            delayedMainServerResumeContext = contextTag == null ? "stash-mover" : contextTag;
            info(
                "Reconnect service reached MAIN_SERVER (%s). Waiting 6.0s before THM Stash mover resume (cycle %d).",
                delayedMainServerResumeContext,
                cycleId
            );
            return;
        }

        boolean restartEvidenceMatched = restartEvidenceGateCycleId == cycleId;
        if (!restartEvidenceMatched) {
            return;
        }

        restartEvidenceGateCycleId = 0L;
        delayedMainServerResumePending = true;
        delayedMainServerResumeCycleId = cycleId;
        delayedMainServerResumeAtMs = System.currentTimeMillis() + MAIN_SERVER_RESUME_DELAY_MS;
        delayedMainServerResumeContext = contextTag == null ? "unknown" : contextTag;
        info(
            "Reconnect service reached MAIN_SERVER (%s). Waiting 6.0s before post-main-server finalization (cycle %d).",
            delayedMainServerResumeContext,
            cycleId
        );
    }

    private void onReconnectFailure(
        long cycleId,
        ServerReconnectService.FailureReason reason,
        String detail,
        String contextTag,
        long armedAtMs,
        long failedAtMs
    ) {
        if (mc != null && !mc.isOnThread()) {
            mc.execute(() -> onReconnectFailure(cycleId, reason, detail, contextTag, armedAtMs, failedAtMs));
            return;
        }
        if (!isActive()) return;
        if (cycleId != activeReconnectCycleId) {
            return;
        }

        if (reconnectOwner == ReconnectOwner.StashMover) {
            THMStashMover stashMover = recoveryStashMover;
            clearStashMoverReconnectState("stash-mover-failure:" + reason.name(), true, true);
            if (stashMover != null) stashMover.onMonitorReconnectFailure(cycleId, reason.name(), detail);
            warning("Reconnect failed for THM Stash mover (%s): %s", reason.name(), detail == null ? "" : detail);
            return;
        }

        if (reconnectOwner == ReconnectOwner.HighwayBuilder && cycleId > 0L) {
            schedulePendingAbortRestore(cycleId, "reconnect-failure:" + reason.name());
        }
        clearHighwayBuilderReconnectModuleRestoreSnapshot("reconnect-failure:" + reason.name());
        clearRestartRecoveryState("failure:" + reason.name(), false, true);
        tryPendingAbortRestore("reconnect-failure:" + reason.name());
        warning("Reconnect failed (%s): %s", reason.name(), detail == null ? "" : detail);
    }

    private void clearRestartRecoveryState(String reason, boolean disarmService, boolean clearCycleBinding) {
        restartRecoveryActive = false;
        resetRubberbandGhostblockWatch();
        restartEvidenceGateCycleId = 0L;
        clearDelayedMainServerResumeState();
        clearPostRejoinDirectionGateState();
        if (clearCycleBinding) activeReconnectCycleId = 0L;
        if (disarmService) reconnectService().disarmReconnect("THMHwyMonitor clearRestartRecoveryState: " + reason);
    }

    private void clearStashMoverReconnectState(String reason, boolean disarmService, boolean clearCycleBinding) {
        restartRecoveryActive = false;
        reconnectOwner = ReconnectOwner.None;
        recoveryStashMover = null;
        restartEvidenceGateCycleId = 0L;
        clearDelayedMainServerResumeState();
        clearPostRejoinDirectionGateState();
        resetRubberbandGhostblockWatch();
        resetForwardProgressWatch();
        resetForwardPacketDesyncEpisode("stash-mover-clear:" + reason);
        clearPendingAlignmentGateRequest();
        if (clearCycleBinding) activeReconnectCycleId = 0L;
        if (disarmService) reconnectService().disarmReconnect("THMHwyMonitor clearStashMoverReconnectState: " + reason);
        info("StashMover reconnect handling cleared: %s", reason);
    }

    private void clearHighwayBuilderReconnectModuleRestoreSnapshot(String reason) {
        ModuleManager manager = getModuleManager();
        if (manager != null) manager.clearReconnectModuleRestoreSnapshot(reason);
    }

    private void clearDelayedMainServerResumeState() {
        delayedMainServerResumePending = false;
        delayedMainServerResumeCycleId = 0L;
        delayedMainServerResumeAtMs = 0L;
        delayedMainServerResumeContext = "";
    }

    private void clearPostRejoinDirectionGateState() {
        postRejoinDirectionGateActive = false;
        postRejoinDirectionRetryCount = 0;
        postRejoinDirectionNextAttemptAtMs = 0L;
        postRejoinDirectionBlockReason = "";
        postRejoinDirectionBlockSummary = "";
        postRejoinBlockedScreenshotTaken = false;
        postRejoinTerminalScreenshotTaken = false;
        postRejoinLastCompleteProbeWinner = null;
    }

    private long armReconnectCycle(String source, boolean markRestartEvidenceGate) {
        long cycleId = reconnectService().armReconnect(restartRejoinDelayMinutes.get(), "THMHwyMonitor:" + source);
        activeReconnectCycleId = cycleId;
        if (markRestartEvidenceGate) restartEvidenceGateCycleId = cycleId;
        return cycleId;
    }

    private long armReconnectCycleSeconds(int delaySeconds, String source, boolean markRestartEvidenceGate) {
        long cycleId = reconnectService().armReconnectSeconds(delaySeconds, "THMHwyMonitor:" + source);
        activeReconnectCycleId = cycleId;
        if (markRestartEvidenceGate) restartEvidenceGateCycleId = cycleId;
        return cycleId;
    }

    // --- Restart automation subsystem entrypoints and helpers ---

    private void refreshTimerSpeedSnapshotFromCurrentState(String source) {
        if (!reconnectAutomationEnabled()) return;
        if (postJoinModuleStateCaptured || internalTimerSpeedToggleInProgress) return;

        Timer timer = Modules.get().get(Timer.class);
        Speed speed = Modules.get().get(Speed.class);
        timerWasActiveBeforePostJoin = timer != null && timer.isActive();
        speedWasActiveBeforePostJoin = speed != null && speed.isActive();
        restartModuleStateSnapshotTaken = timerWasActiveBeforePostJoin || speedWasActiveBeforePostJoin;
    }

    @EventHandler
    private void onActiveModulesChanged(ActiveModulesChangedEvent event) {
        if (stashMoverReconnectHandlingActive()) return;
        if (!reconnectAutomationEnabled()) return;
        refreshTimerSpeedSnapshotFromCurrentState("activeModulesChanged");
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (stashMoverReconnectHandlingActive()) return;
        if (!(event.packet instanceof PlayerPositionLookS2CPacket)) return;
        queueForwardCorrectionPacket();
    }

    @EventHandler
    private void onPacketSend(PacketEvent.Send event) {
        if (stashMoverReconnectHandlingActive()) return;
        if (!(event.packet instanceof PlayerActionC2SPacket packet)) return;
        recordForwardDestroyPacket(packet);
    }

    @EventHandler(priority = 1000)
    private void onTickCaptureYawBeforeOtherModules(TickEvent.Pre event) {
        if (mc == null || mc.player == null) return;
        preTickYawSnapshot = mc.player.getYaw();
        preTickYawSnapshotAtMs = System.currentTimeMillis();
    }

    @EventHandler(priority = 999)
    private void onMessageReceive(ReceiveMessageEvent event) {
        if (stashMoverReconnectHandlingActive()) return;
        String message = event.getMessage().getString();
        if (message == null) return;
        String lower = message.toLowerCase(Locale.ROOT);

        if (isRestartHardFailMessage(lower)) {
            armRestartDisconnectEvidence("message");
        }

        if (isKnownNonRestartHardFailMessage(lower)) {
            nonRestartHardFailArmed = true;
            signalNonRestartHardFailFromHighwayBuilder();
        }
    }

    @EventHandler
    private void onGameJoined(GameJoinedEvent event) {
        intentionalSafetyDisconnectArmed = false;
        disableMonitorAfterIntentionalSafetyDisconnect = false;
        wasConnectedLastTick = true;
        clearNonRestartHardFailSignal();
        clearRestartHardFailSignal();
        nonRestartHardFailArmed = false;
        pendingDisconnectScreenEvidenceCheck = false;
        pendingDisconnectScreenEvidenceUntilMs = 0L;
        unresolvedMainServerDisconnectCandidate = false;
        clearStaleDisconnectedScreenIfLiveConnected();
        tryPendingAbortRestore("game-joined");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    private void onGameLeft(GameLeftEvent event) {
        wasConnectedLastTick = false;
        pendingDisconnectScreenEvidenceCheck = false;
        pendingDisconnectScreenEvidenceUntilMs = 0L;
        resetForwardPacketDesyncEpisode("game-left");
        abortActiveRecoveryForNonMainServer("game-left");

        if (intentionalSafetyDisconnectArmed) {
            intentionalSafetyDisconnectArmed = false;
            clearRestartAutomationState("intentional-safety-disconnect", true, true);
            unresolvedMainServerDisconnectCandidate = false;
            if (disableMonitorAfterIntentionalSafetyDisconnect) {
                disableMonitorAfterIntentionalSafetyDisconnect = false;
                if (isActive()) toggle();
            }
            return;
        }

        HighwayBuilderTHM builderBeforeDisconnect = Modules.get().get(HighwayBuilderTHM.class);
        boolean builderWasActiveAtDisconnect = builderBeforeDisconnect != null && builderBeforeDisconnect.isActive();
        THMStashMover stashMoverBeforeDisconnect = Modules.get().get(THMStashMover.class);
        boolean stashMoverWasActiveAtDisconnect = stashMoverBeforeDisconnect != null
            && stashMoverBeforeDisconnect.isActive()
            && stashMoverBeforeDisconnect.isManagingThmHwyMonitorReconnect();
        String disconnectScreenReason = readDisconnectedScreenReasonLower();

        if (reconnectRecoveryInFlight()) {
            unresolvedMainServerDisconnectCandidate = false;
            info("Reconnect transfer hop observed while reconnect recovery is already in flight; suppressing fresh disconnect-evidence cycle.");
            return;
        }

        unresolvedMainServerDisconnectCandidate = builderWasActiveAtDisconnect && !stashMoverWasActiveAtDisconnect;

        if (stashMoverWasActiveAtDisconnect) {
            unresolvedMainServerDisconnectCandidate = false;
            recoveryStashMover = stashMoverBeforeDisconnect;
            reconnectOwner = ReconnectOwner.StashMover;
            restartRecoveryActive = true;
            clearRestartDisconnectEvidence();
            clearNonRestartHardFailSignal();
            clearRestartHardFailSignal();
            nonRestartHardFailArmed = false;
            long cycleId = armReconnectCycle("stash-mover-disconnect", false);
            info("Detected disconnect while THM Stash mover owned reconnect handling. Armed reconnect cycle %d.", cycleId);
            return;
        }

        boolean nonRestartHardFail = nonRestartHardFailArmed || consumeNonRestartHardFailSignal();
        if (!nonRestartHardFail && isKnownNonRestartHardFailMessage(disconnectScreenReason)) {
            nonRestartHardFail = true;
        }
        nonRestartHardFailArmed = false;

        if (nonRestartHardFail) {
            unresolvedMainServerDisconnectCandidate = false;
            handleDetectedNonRestartHardFail("onGameLeft");
            return;
        }

        if (consumeRestartHardFailSignal()) {
            armRestartDisconnectEvidence("hb-signal");
        }

        String restartEvidence = consumeRestartDisconnectEvidence();

        if (builderWasActiveAtDisconnect && reconnectAutomationEnabled()) {
            if (!prepareFreshHighwayBuilderReconnect(builderBeforeDisconnect, "game-left")) return;

            if (restartEvidence != null) {
                unresolvedMainServerDisconnectCandidate = false;
                confirmPreparedReconnectEvidence("restart-evidence:" + restartEvidence);
                return;
            }
        } else if (restartEvidence != null) {
            unresolvedMainServerDisconnectCandidate = false;
            info("Disconnect matched restart evidence (%s), but no active HighwayBuilder reconnect transaction was prepared.", restartEvidence);
            return;
        }

        pendingDisconnectScreenEvidenceCheck = true;
        pendingDisconnectScreenEvidenceUntilMs = System.currentTimeMillis() + DISCONNECT_SCREEN_EVIDENCE_TIMEOUT_MS;
        info("Disconnect detected without immediate hard-fail evidence. Waiting up to 3.0s for disconnect-screen reason.");
    }

    private void handleDetectedNonRestartHardFail(String source) {
        if (restartRecoveryActive
            && reconnectOwner == ReconnectOwner.HighwayBuilder
            && activeReconnectCycleId > 0L) {
            schedulePendingAbortRestore(activeReconnectCycleId, "non-restart-hard-fail:" + source);
        }
        abortActiveRecoveryForNonRestartHardFail();
        clearHighwayBuilderReconnectModuleRestoreSnapshot("non-restart-hard-fail:" + source);
        clearRestartAutomationState("non-restart-hard-fail:" + source, true, true);
        clearRestartDisconnectEvidence();
        unresolvedMainServerDisconnectCandidate = false;
        deferRestartScreenshotUntilReconnect = false;
        deferredRestartScreenshotAfterReconnectPending = false;
        tryPendingAbortRestore("non-restart-hard-fail:" + source);
        warning("Non-restart hard fail detected (%s). Reconnect handling was disarmed, but THM Hwy Monitor remains enabled.", source);
    }

    private void handlePendingDisconnectScreenEvidenceCheck(boolean connectedNow) {
        if (!pendingDisconnectScreenEvidenceCheck) return;

        if (connectedNow) {
            pendingDisconnectScreenEvidenceCheck = false;
            pendingDisconnectScreenEvidenceUntilMs = 0L;
            unresolvedMainServerDisconnectCandidate = false;
            return;
        }

        long now = System.currentTimeMillis();
        String disconnectScreenReason = readDisconnectedScreenReasonLower();
        if (disconnectScreenReason == null || disconnectScreenReason.isEmpty()) {
            if (now < pendingDisconnectScreenEvidenceUntilMs) return;
            pendingDisconnectScreenEvidenceCheck = false;
            pendingDisconnectScreenEvidenceUntilMs = 0L;
            handleUnclassifiedMainServerDisconnectFallback("disconnect-screen-timeout");
            return;
        }

        pendingDisconnectScreenEvidenceCheck = false;
        pendingDisconnectScreenEvidenceUntilMs = 0L;

        if (isKnownNonRestartHardFailMessage(disconnectScreenReason)) {
            unresolvedMainServerDisconnectCandidate = false;
            handleDetectedNonRestartHardFail("disconnect-screen");
            return;
        }

        if (!isRestartHardFailMessage(disconnectScreenReason)) {
            handleUnclassifiedMainServerDisconnectFallback("disconnect-screen-unmatched");
            return;
        }

        unresolvedMainServerDisconnectCandidate = false;
        armRestartDisconnectEvidence("disconnect-screen");
        info("Disconnect matched restart evidence (disconnect-screen). Treating as restart.");
        confirmPreparedReconnectEvidence("disconnect-screen");
    }

    private void handleUnclassifiedMainServerDisconnectFallback(String source) {
        if (!unresolvedMainServerDisconnectCandidate) return;
        unresolvedMainServerDisconnectCandidate = false;

        String rawReason = readDisconnectedScreenReasonLower();
        if (reconnectAutomationEnabled()) {
            info(
                "Connection to server dropped; awaiting reconnect. source=%s rawReason=%s",
                source,
                rawReason == null || rawReason.isBlank() ? "unavailable" : rawReason
            );
            deferRestartScreenshotUntilReconnect = true;
            confirmPreparedReconnectEvidence(source);
            return;
        }

        warning(
            "Connection to server dropped. Please reconnect and restart HighwayBuilder to continue. source=%s rawReason=%s",
            source,
            rawReason == null || rawReason.isBlank() ? "unavailable" : rawReason
        );
    }

    private void maybeTakeDeferredRestartScreenshotAfterReconnect(String source) {
        if (!deferredRestartScreenshotAfterReconnectPending) return;
        if (!restartScreenshotsEnabled()) {
            deferredRestartScreenshotAfterReconnectPending = false;
            return;
        }

        ensureHighwayBuilderDisabledForRestart("deferred reconnect screenshot", false);
        HighwayBuilderTHM builder = Modules.get().get(HighwayBuilderTHM.class);
        if (builder != null && builder.isActive()) {
            return;
        }

        deferredRestartScreenshotAfterReconnectPending = false;
        info("Taking deferred restart screenshot after successful reconnect (%s).", source);
        scheduleRestartScreenshot(RESTART_SCREENSHOT_DELAY_MS, "deferred-after-reconnect");
    }

    private void scheduleRestartScreenshot(int delayMs, String source) {
        if (!restartScreenshotsEnabled()) return;
        if (restartScreenshotScheduled) return;
        restartScreenshotScheduled = true;
        int effectiveDelayMs = Math.max(0, delayMs);
        if (effectiveDelayMs <= 0) info("Restart detection screen found. Taking screenshot now.");
        else info("Restart detection screen found. Taking screenshot in %.1fs.", effectiveDelayMs / 1000.0);

        THMUtils.async("restart-screenshot", () -> {
            try {
                if (effectiveDelayMs > 0) Thread.sleep(effectiveDelayMs);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }

            if (!isActive()) {
                restartScreenshotScheduled = false;
                return;
            }

            mc.execute(() -> {
                takeRestartScreenshot();
                restartScreenshotScheduled = false;
            });
        });
    }

    private boolean prepareFreshHighwayBuilderReconnect(HighwayBuilderTHM builder, String source) {
        if (builder == null || !builder.isActive() || !reconnectAutomationEnabled()) return false;

        ServerReconnectService.ReconnectPreflight preflight = reconnectService().getReconnectPreflight();
        long cycleId;
        if (preflight.serviceArmed() && preflight.cycleId() > 0L) {
            cycleId = preflight.cycleId();
            activeReconnectCycleId = cycleId;
        } else {
            cycleId = armReconnectCycle("unexpected-disconnect", true);
        }

        reconnectOwner = ReconnectOwner.HighwayBuilder;
        restartRecoveryActive = true;
        restartEvidenceGateCycleId = cycleId;

        if (!builder.prepareForMonitorReconnectPause(cycleId)) {
            failDisconnectPreparation(builder, cycleId, source, "Unable to persist HighwayBuilder reconnect baseline and stats checkpoint.");
            return false;
        }

        ModuleManager manager = getModuleManager();
        if (manager != null && manager.isActive() && !manager.prepareForMonitorReconnectPause(cycleId, source)) {
            failDisconnectPreparation(builder, cycleId, source, "Unable to freeze Module Manager reconnect snapshot.");
            return false;
        }

        builder.disableForMonitorRealignPause();
        if (builder.isActive()) {
            failDisconnectPreparation(builder, cycleId, source, "HighwayBuilder did not disable for reconnect pause.");
            return false;
        }

        info("Prepared transactional HighwayBuilder reconnect cycle %d before disconnect cleanup (%s).", cycleId, source);
        return true;
    }

    private void failDisconnectPreparation(HighwayBuilderTHM builder, long cycleId, String source, String detail) {
        schedulePendingAbortRestore(cycleId, "disconnect-preparation-failed:" + source);

        if (builder != null && builder.isActive()) {
            builder.disableForMonitorRealignPause();
        }

        clearHighwayBuilderReconnectModuleRestoreSnapshot("disconnect-preparation-failed:" + source);
        clearRestartAutomationState("disconnect-preparation-failed:" + source, true, true);
        tryPendingAbortRestore("disconnect-preparation-failed:" + source);
        warning("%s Reconnect automation was disarmed without issuing another disconnect.", detail);
    }

    private void confirmPreparedReconnectEvidence(String source) {
        if (!restartRecoveryActive
            || reconnectOwner != ReconnectOwner.HighwayBuilder
            || activeReconnectCycleId <= 0L
            || restartEvidenceGateCycleId != activeReconnectCycleId) {
            warning("Reconnect evidence arrived without a prepared HighwayBuilder transaction (%s).", source);
            return;
        }

        deferRestartScreenshotUntilReconnect = false;
        deferredRestartScreenshotAfterReconnectPending = false;
        scheduleRestartScreenshot(0, "restart-detected");
        info("Confirmed prepared HighwayBuilder reconnect cycle %d (%s).", activeReconnectCycleId, source);
    }

    private void schedulePendingAbortRestore(long cycleId, String reason) {
        if (cycleId <= 0L) return;
        if (pendingAbortRestoreCycleId > 0L && pendingAbortRestoreCycleId != cycleId) {
            warning("Retaining reconnect abort cycle %d; refused conflicting cycle %d.", pendingAbortRestoreCycleId, cycleId);
            return;
        }

        pendingAbortRestoreCycleId = cycleId;
        pendingAbortRestoreReason = reason == null || reason.isBlank() ? "unknown" : reason;
        pendingAbortRestoreNextAttemptAtMs = 0L;
    }

    private void discoverPendingAbortRestore(String source) {
        if (pendingAbortRestoreCycleId > 0L) return;

        HighwayBuilderTHM builder = Modules.get().get(HighwayBuilderTHM.class);
        if (builder == null || builder.isActive()) return;

        long cycleId = builder.getPendingReconnectAbortCycleId();
        if (cycleId <= 0L) return;

        schedulePendingAbortRestore(cycleId, "durable-discovery:" + source);
        info("Discovered pending reconnect abort restore cycle %d (%s).", cycleId, source);
    }

    private void tryPendingAbortRestore(String source) {
        if (pendingAbortRestoreCycleId <= 0L) return;
        if (!hasLiveServerConnection()) return;

        long now = System.currentTimeMillis();
        if (now < pendingAbortRestoreNextAttemptAtMs) return;

        HighwayBuilderTHM builder = Modules.get().get(HighwayBuilderTHM.class);
        if (builder == null || builder.isActive()) return;

        HighwayBuilderTHM.ReconnectAbortRestoreOutcome outcome = builder.abortPreparedReconnectPause(
            pendingAbortRestoreCycleId,
            pendingAbortRestoreReason + ":" + source
        );

        switch (outcome) {
            case Restored -> {
                info("Restored aborted reconnect Timer/Speed state for cycle %d (%s).", pendingAbortRestoreCycleId, source);
                pendingAbortRestoreCycleId = 0L;
                pendingAbortRestoreReason = "";
                pendingAbortRestoreNextAttemptAtMs = 0L;
            }
            case DeferredNoLiveWorld -> pendingAbortRestoreNextAttemptAtMs = now + 1_000L;
            case RejectedGeneration -> {
                pendingAbortRestoreNextAttemptAtMs = now + 5_000L;
                warning("Reconnect abort restore rejected generation for cycle %d; preserving all snapshots.", pendingAbortRestoreCycleId);
            }
            case StatsPersistenceFailed -> {
                pendingAbortRestoreNextAttemptAtMs = now + 5_000L;
                warning("Reconnect abort restore could not persist safely for cycle %d; preserving all snapshots for retry.", pendingAbortRestoreCycleId);
            }
        }
    }

    private boolean restartScreenshotsEnabled() {
        // HighwayBuilder owns the user-facing proof screenshot flow now.
        return false;
    }

    private boolean reconnectAutomationEnabled() {
        return autoReconnect.get();
    }

    private boolean stashMoverReconnectHandlingActive() {
        if (reconnectOwner == ReconnectOwner.StashMover) return true;

        try {
            THMStashMover stashMover = Modules.get().get(THMStashMover.class);
            return stashMover != null
                && stashMover.isActive()
                && stashMover.isManagingThmHwyMonitorReconnect();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void resetHighwayBuilderCorrectionForStashMover(String source) {
        resetForwardProgressWatch();
        resetRubberbandGhostblockWatch();
        resetForwardPacketDesyncEpisode("stash-mover-" + source);
        clearPendingAlignmentGateRequest();
        trackedSegment = null;
        trackedDirection = "";
        if (recoveryPhase != RecoveryPhase.None || recoveryModulesPaused) {
            preserveHighwayBuilderDisabledAcrossRecoveryResume();
            resetRecoveryState();
        }
    }

    private boolean hasRestartAutomationState() {
        return activeReconnectCycleId != 0L
            || restartEvidenceGateCycleId != 0L
            || delayedMainServerResumePending
            || delayedMainServerResumeCycleId != 0L
            || delayedMainServerResumeAtMs != 0L
            || restartRecoveryActive
            || restartDisconnectEvidenceArmed
            || restartScreenshotScheduled
            || postJoinModuleStateCaptured
            || restartModuleStateSnapshotTaken
            || nonRestartHardFailArmed
            || unresolvedMainServerDisconnectCandidate
            || deferRestartScreenshotUntilReconnect
            || deferredRestartScreenshotAfterReconnectPending
            || pendingDisconnectScreenEvidenceCheck
            || pendingDisconnectScreenEvidenceUntilMs != 0L
            || rearmNormalReconnectAfterForwardReconnectResume
            || reconnectOwner == ReconnectOwner.StashMover
            || recoveryStashMover != null;
    }

    private void resetReconnectAutomationState(boolean clearCycleBinding) {
        restartScreenshotScheduled = false;
        postJoinModuleStateCaptured = false;
        timerWasActiveBeforePostJoin = false;
        speedWasActiveBeforePostJoin = false;
        nonRestartHardFailArmed = false;
        unresolvedMainServerDisconnectCandidate = false;
        deferRestartScreenshotUntilReconnect = false;
        deferredRestartScreenshotAfterReconnectPending = false;
        pendingDisconnectScreenEvidenceCheck = false;
        pendingDisconnectScreenEvidenceUntilMs = 0L;
        restartModuleStateSnapshotTaken = false;
        internalTimerSpeedToggleInProgress = false;
        clearRestartDisconnectEvidence();
        clearNonRestartHardFailSignal();
        clearRestartHardFailSignal();
        reconnectOwner = ReconnectOwner.None;
        recoveryStashMover = null;
        rearmNormalReconnectAfterForwardReconnectResume = false;
        clearRestartRecoveryState("reset-automation", false, clearCycleBinding);
    }

    private void clearRestartAutomationState(String reason, boolean disarmService, boolean clearCycleBinding) {
        boolean hadState = hasRestartAutomationState();
        resetReconnectAutomationState(clearCycleBinding);
        clearHighwayBuilderReconnectModuleRestoreSnapshot("clear-restart-automation:" + reason);
        if (disarmService) reconnectService().disarmReconnect("THMHwyMonitor clearRestartAutomationState: " + reason);

        if (!hadState) return;
        info("Restart automation state cleared: %s", reason);
    }

    private void ensureHighwayBuilderDisabledForRestart(String source, boolean verbose) {
        HighwayBuilderTHM builder = Modules.get().get(HighwayBuilderTHM.class);
        if (builder == null || !builder.isActive()) return;

        if (!builder.prepareForMonitorReconnectPause(activeReconnectCycleId)) {
            enterReconnectSafetyStop("Unable to establish reconnect baseline before restart pause.");
            return;
        }

        ModuleManager manager = getModuleManager();
        if (manager != null && manager.isActive() && !manager.prepareForMonitorReconnectPause(activeReconnectCycleId, source)) {
            enterReconnectSafetyStop("Unable to freeze Module Manager reconnect snapshot before restart pause.");
            return;
        }

        builder.disableForMonitorRealignPause();
        boolean disabled = !builder.isActive();

        if (disabled) {
            if (verbose) info("Disabled THM HighwayBuilder during restart handling (%s).", source);
        } else {
            warning("Failed to disable THM HighwayBuilder during restart handling (%s).", source);
        }
    }

    private void handleAutoReconnectToggleTransitions() {
        boolean currentToggle = autoReconnect.get();
        if (currentToggle == previousAutoReconnectToggleState) return;

        if (stashMoverReconnectHandlingActive()) {
            previousAutoReconnectToggleState = currentToggle;
            info("Auto-reconnect toggle changed while THM Stash mover owns reconnect handling; HighwayBuilder reconnect side effects were suppressed.");
            return;
        }

        if (currentToggle) {
            long cycleId = armReconnectCycle("toggle-on", false);
            reconnectOwner = ReconnectOwner.HighwayBuilder;
            info("Auto-reconnect enabled. Armed reconnect cycle %d.", cycleId);
        } else {
            if (restartRecoveryActive
                && reconnectOwner == ReconnectOwner.HighwayBuilder
                && activeReconnectCycleId > 0L) {
                schedulePendingAbortRestore(activeReconnectCycleId, "auto-reconnect-toggle-off");
            }
            clearRestartAutomationState("toggle-off", true, true);
            tryPendingAbortRestore("auto-reconnect-toggle-off");
            info("Auto-reconnect disabled. Reconnect cycle and policy state were cleared.");
        }

        previousAutoReconnectToggleState = currentToggle;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        handleReconnectAutomationTickLane();

        if (mc.player == null || mc.world == null) {
            resetRubberbandGhostblockWatch();
            clearPendingAlignmentGateRequest();
            return;
        }

        if (!isHighwayRecoveryAllowedOnCurrentServer()) {
            resetRubberbandGhostblockWatch();
            abortActiveRecoveryForNonMainServer("server-state-" + getCommittedServerState().name());
            return;
        }

        if (stashMoverReconnectHandlingActive()) {
            resetHighwayBuilderCorrectionForStashMover("tick");
            return;
        }

        HighwayBuilderTHM ghostblockBuilder = Modules.get().get(HighwayBuilderTHM.class);
        GhostblockReconnectTrigger ghostblockTrigger = updateRubberbandGhostblockWatch(ghostblockBuilder);
        if (ghostblockTrigger != GhostblockReconnectTrigger.None && tryBeginRubberbandGhostblockReconnect(ghostblockBuilder, ghostblockTrigger)) {
            return;
        }

        if (recoveryPhase != RecoveryPhase.None) {
            resetForwardProgressWatch();
            handleRecoveryPhase();
            return;
        }

        if (!autoRecover.get()) {
            resetForwardProgressWatch();
            resetRubberbandGhostblockWatch();
            clearPendingAlignmentGateRequest();
            return;
        }

        if (cooldownTicks > 0) {
            resetForwardProgressWatch();
            cooldownTicks--;
            return;
        }

        ticksSinceCheck++;
        if (ticksSinceCheck < checkInterval.get()) return;
        ticksSinceCheck = 0;

        HighwayBuilderTHM builder = Modules.get().get(HighwayBuilderTHM.class);
        if (builder == null || !builder.isActive()) {
            if (mc.player != null) lastReliableRecoveryYaw = mc.player.getYaw();
            trackedSegment = null;
            trackedDirection = "";
            resetForwardProgressWatch();
            clearPendingAlignmentGateRequest();
            return;
        }

        if (builder.isTpsThrottlePaused()) {
            resetForwardProgressWatch();
            resetRubberbandGhostblockWatch();
            clearPendingAlignmentGateRequest();
            return;
        }

        RecoveryCause recoveryCause = updateStallWatch(builder);
        boolean stallRecoveryArmed = recoveryCause != RecoveryCause.None;
        boolean centerStallRecovery = recoveryCause == RecoveryCause.CenterStall;

        if (builder.shouldSuppressThmHwyMonitorRecovery(centerStallRecovery)) {
            if (!builder.isInCenterState()) resetForwardProgressWatch();
            clearPendingAlignmentGateRequest();
            return;
        }

        int recoveryGoalY = isPavingMode(builder) ? 120 : 119;
        float recoveryDirectionYaw = resolveRecoveryDirectionYawForInference(builder);
        RecoveryTarget target = computeCurrentRecoveryTarget(recoveryDirectionYaw, recoveryGoalY);
        if (target == null && !isStallRecoveryCause(recoveryCause)) {
            resetForwardProgressWatch();
            clearPendingAlignmentGateRequest();
            return;
        }

        double yDelta = recoveryYDelta(mc.player.getY(), recoveryGoalY);
        boolean yAligned = Math.abs(yDelta) <= Y_ALIGNMENT_TOLERANCE;
        boolean aligned = target != null && target.distance() <= WORKING_LINE_TOLERANCE && yAligned;
        if (aligned && !stallRecoveryArmed) {
            clearPendingAlignmentGateRequest();
            return;
        }

        if (recoveryCause == RecoveryCause.Misalignment) {
            if (!tryPassMainServerAlignmentGate()) return;

            if (!isActive() || mc.player == null || mc.world == null) {
                clearPendingAlignmentGateRequest();
                return;
            }

            builder = Modules.get().get(HighwayBuilderTHM.class);
            if (builder == null || !builder.isActive()) {
                trackedSegment = null;
                trackedDirection = "";
                resetForwardProgressWatch();
                clearPendingAlignmentGateRequest();
                return;
            }

            if (builder.isTpsThrottlePaused()) {
                resetForwardProgressWatch();
                resetRubberbandGhostblockWatch();
                clearPendingAlignmentGateRequest();
                return;
            }

            if (builder.shouldSuppressThmHwyMonitorRecovery(false)) {
                resetForwardProgressWatch();
                clearPendingAlignmentGateRequest();
                return;
            }

            recoveryGoalY = isPavingMode(builder) ? 120 : 119;
            recoveryDirectionYaw = resolveRecoveryDirectionYawForInference(builder);
            target = computeCurrentRecoveryTarget(recoveryDirectionYaw, recoveryGoalY);
            if (target == null) {
                resetForwardProgressWatch();
                clearPendingAlignmentGateRequest();
                return;
            }

            yDelta = recoveryYDelta(mc.player.getY(), recoveryGoalY);
            yAligned = Math.abs(yDelta) <= Y_ALIGNMENT_TOLERANCE;
            aligned = target.distance() <= WORKING_LINE_TOLERANCE && yAligned;
            if (aligned) {
                clearPendingAlignmentGateRequest();
                return;
            }
        } else {
            clearPendingAlignmentGateRequest();
        }

        String yOffset = yAligned ? "" : String.format(Locale.ROOT, ", Y %+.2f", yDelta);
        beginRecoveryRoutine(builder, target, yOffset, recoveryDirectionYaw, recoveryCause);
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (!postRejoinDirectionGateActive) return;
        if (mc == null || mc.textRenderer == null) return;

        DrawContext context = event.drawContext;
        List<String> lines = new ArrayList<>();
        lines.add("THMHwyMonitor reconnect blocked");
        lines.add(String.format(Locale.ROOT, "Retry %d/%d", postRejoinDirectionRetryCount, POST_REJOIN_DIRECTION_RETRY_LIMIT));
        lines.add("Reason: " + postRejoinDirectionBlockReason);
        if (!postRejoinDirectionBlockSummary.isBlank()) lines.add(postRejoinDirectionBlockSummary);

        int x = 8;
        int y = 8;
        int width = 0;
        for (String line : lines) width = Math.max(width, mc.textRenderer.getWidth(line));

        int lineHeight = mc.textRenderer.fontHeight + 2;
        int height = (lines.size() * lineHeight) + 6;
        context.fill(x - 4, y - 4, x + width + 6, y + height, 0xCC000000);

        int drawY = y;
        for (String line : lines) {
            context.drawText(mc.textRenderer, line, x, drawY, 0xFFFFAA00, false);
            drawY += lineHeight;
        }
    }

    private RecoveryTarget computeCurrentRecoveryTarget(float recoveryDirectionYaw, int recoveryGoalY) {
        if (mc == null || mc.player == null) return null;
        HighwayBuilderTHM builder = Modules.get().get(HighwayBuilderTHM.class);
        HorizontalDirection resolvedDirection = resolveWorkingDirectionForAlignment(builder, recoveryDirectionYaw);
        String preferredDirection = normalizeDirection(directionCode(resolvedDirection));
        if (preferredDirection.isEmpty() || Float.isNaN(recoveryDirectionYaw)) return null;

        RecoveryTarget target = determineRecoveryTarget(
            mc.player.getX(),
            mc.player.getZ(),
            recoveryDirectionYaw,
            recoveryGoalY,
            trueCenterMode.get(),
            trackedSegment,
            preferredDirection
        );
        if (target == null) return null;

        if (trackedSegment == null && target.distance() <= WORKING_LINE_TOLERANCE) {
            trackedSegment = target.segment();
        }

        if (trackedSegment != null) {
            target = determineRecoveryTarget(
                mc.player.getX(),
                mc.player.getZ(),
                recoveryDirectionYaw,
                recoveryGoalY,
                trueCenterMode.get(),
                trackedSegment,
                preferredDirection
            );
            if (target == null) return null;
        }

        trackedDirection = target.travelDirection();
        return target;
    }

    private boolean tryPassMainServerAlignmentGate() {
        if (pendingAlignmentGateFuture == null) {
            startMainServerAlignmentGateRequest();
            return false;
        }

        CompletableFuture<ServerState> future = pendingAlignmentGateFuture;
        long attemptId = pendingAlignmentGateAttemptId;
        if (future == null || attemptId <= 0L) {
            clearPendingAlignmentGateRequest();
            return false;
        }

        if (!future.isDone()) return false;

        ServerState state = consumeMainServerAlignmentGateResult(attemptId);
        if (state != ServerState.MAIN_SERVER) {
            cooldownTicks = 0;
            return false;
        }

        return true;
    }

    private void startMainServerAlignmentGateRequest() {
        if (pendingAlignmentGateFuture != null) return;

        long attemptId = nextAlignmentGateAttemptId++;
        pendingAlignmentGateAttemptId = attemptId;
        try {
            pendingAlignmentGateFuture = ServerStatusHandler.getInstance().returnStateAsync(ALIGNMENT_GATE_TIMEOUT_MS);
        } catch (Throwable ignored) {
            clearPendingAlignmentGateRequest();
        }
    }

    private ServerState consumeMainServerAlignmentGateResult(long expectedAttemptId) {
        if (pendingAlignmentGateFuture == null) return ServerState.UNKNOWN;
        if (pendingAlignmentGateAttemptId != expectedAttemptId) {
            return ServerState.UNKNOWN;
        }

        CompletableFuture<ServerState> future = pendingAlignmentGateFuture;
        clearPendingAlignmentGateRequest();
        try {
            ServerState state = future.getNow(ServerState.UNKNOWN);
            if (state == null) state = ServerState.UNKNOWN;
            return state;
        } catch (Throwable ignored) {
            return ServerState.UNKNOWN;
        }
    }

    private void clearPendingAlignmentGateRequest() {
        pendingAlignmentGateFuture = null;
        pendingAlignmentGateAttemptId = 0L;
    }

    private void beginRecoveryRoutine(HighwayBuilderTHM builder, RecoveryTarget target, String yOffset, float recoveryDirectionYaw, RecoveryCause recoveryCause) {
        if (recoveryCause == RecoveryCause.Misalignment && target != null && target.distance() > maxCorrectionDistance.get()) {
            warning("Misaligned by %.2f%s on %s %s. Exceeds max-correction-distance %.2f.",
                target.distance(), yOffset, target.highway(), target.direction(), maxCorrectionDistance.get());
            handleExcessiveMisalignment(builder, target);
            return;
        }

        RecoveryTarget localStallEscapeTarget = isStallRecoveryCause(recoveryCause)
            ? createLocalStallEscapeTarget(builder, target)
            : null;
        if (isStallRecoveryCause(recoveryCause) && localStallEscapeTarget == null) {
            triggerMonitorSafeBuilderHardFail(builder, stallRecoveryLabel(recoveryCause) + " recovery could not compute a local 2-block escape target.");
            return;
        }

        if (!pauseAllActiveModulesForRecovery()) {
            warning("Failed to pause recovery modules (THM HighwayBuilder / Timer / Speed).");
            cooldownTicks = recoveryCooldown.get();
            return;
        }

        RecoveryTarget correctionTarget;
        boolean updateTracking = target != null;
        boolean localEscapeOnly = false;
        if (recoveryCause == RecoveryCause.CenterStall) {
            correctionTarget = localStallEscapeTarget;
            localEscapeOnly = true;
            updateTracking = false;
        } else if (recoveryCause == RecoveryCause.ForwardStall) {
            if (target == null || target.distance() > maxCorrectionDistance.get()) {
                correctionTarget = localStallEscapeTarget;
                localEscapeOnly = true;
            } else {
                correctionTarget = applyForcedStallBackstepForGoto(target);
            }
        } else {
            correctionTarget = applyRepairMisalignmentBackstepForGoto(target);
        }

        recoveryBuilder = builder;
        pendingCorrectionTarget = correctionTarget;
        pendingLocalStallEscapeTarget = localStallEscapeTarget;
        this.recoveryCause = recoveryCause;
        if (updateTracking) {
            trackedSegment = target.segment();
            trackedDirection = target.travelDirection();
        }
        recoveryYawBeforeMove = recoveryDirectionYaw;
        recoveryTicks = RECOVERY_DELAY_TICKS;
        baritoneRecoveryStartAttempts = 0;
        baritoneStopCommandTried = false;
        recoveryPhase = RecoveryPhase.WaitBeforeCorrection;
        int stalledTicksSnapshot = stallWatchTicks;
        resetForwardProgressWatch();
        if (isStallRecoveryCause(recoveryCause)) {
            double stalledSeconds = stalledTicksSnapshot / 20.0;
            String recoveryLabel = target != null ? target.highway() + " " + target.direction() : "working direction";
            String escapeLabel = localEscapeOnly ? "local 2-block stall escape" : "stall correction";
            info("Paused recovery modules (THM HighwayBuilder / Timer / Speed) on %s after %.1fs stalled in %s. Starting %s in 2.0s.",
                recoveryLabel, stalledSeconds, stallRecoveryLabel(recoveryCause), escapeLabel);
            if (recoveryCause == RecoveryCause.CenterStall && builder.isCenterTeleportBlockedForMonitorRecovery()) {
                BlockPos blockedTarget = builder.getCenterTeleportBlockedTargetForMonitorRecovery();
                info(
                    "Center teleport blocked for %.1fs before recovery (reason=%s%s).",
                    builder.getCenterTeleportBlockedTicksForMonitorRecovery() / 20.0,
                    builder.getCenterTeleportBlockedReasonForMonitorRecovery(),
                    blockedTarget == null
                        ? ""
                        : String.format(Locale.ROOT, ", target=(%d,%d,%d)", blockedTarget.getX(), blockedTarget.getY(), blockedTarget.getZ())
                );
            }
        } else {
            info("Paused recovery modules (THM HighwayBuilder / Timer / Speed) on %s %s (off by %.2f%s). Starting Baritone correction in 2.0s.",
                target.highway(), target.direction(), target.distance(), yOffset);
        }
    }

    private RecoveryCause updateStallWatch(HighwayBuilderTHM builder) {
        if (!recoverForwardStalls.get() || mc.player == null || builder == null || !builder.isActive() || builder.isTpsThrottlePaused()) {
            resetForwardProgressWatch();
            return RecoveryCause.None;
        }

        HorizontalDirection direction = builder.getWorkingDirection();
        if (direction == null) {
            resetForwardProgressWatch();
            return RecoveryCause.None;
        }

        if (builder.isInCenterState()) {
            boolean blockedCenterTeleport = builder.isCenterTeleportBlockedForMonitorRecovery();
            if (stallWatchMode != StallWatchMode.Center || stallWatchDirection != direction) {
                stallWatchMode = StallWatchMode.Center;
                stallWatchDirection = direction;
                stallWatchTicks = 0;
                bestForwardProgressCoordinate = 0.0;
                return RecoveryCause.None;
            }

            int increment = Math.max(checkInterval.get(), 1);
            if (stallWatchTicks <= Integer.MAX_VALUE - increment) stallWatchTicks += increment;
            else stallWatchTicks = Integer.MAX_VALUE;

            if (blockedCenterTeleport && builder.getCenterTeleportBlockedTicksForMonitorRecovery() >= BLOCKED_CENTER_STALL_TICKS) {
                return RecoveryCause.CenterStall;
            }

            return stallWatchTicks >= forwardStallTimeoutSeconds.get() * 20 ? RecoveryCause.CenterStall : RecoveryCause.None;
        }

        if (!builder.isInForwardState()) {
            resetForwardProgressWatch();
            return RecoveryCause.None;
        }

        double currentProgressCoordinate = projectedForwardCoordinate(mc.player.getX(), mc.player.getZ(), direction);
        if (stallWatchMode != StallWatchMode.Forward || stallWatchDirection != direction) {
            stallWatchMode = StallWatchMode.Forward;
            stallWatchDirection = direction;
            bestForwardProgressCoordinate = currentProgressCoordinate;
            stallWatchTicks = 0;
            return RecoveryCause.None;
        }

        if (currentProgressCoordinate > bestForwardProgressCoordinate + FORWARD_PROGRESS_RESET_EPSILON) {
            bestForwardProgressCoordinate = currentProgressCoordinate;
            stallWatchTicks = 0;
            return RecoveryCause.None;
        }

        int increment = Math.max(checkInterval.get(), 1);
        if (stallWatchTicks <= Integer.MAX_VALUE - increment) stallWatchTicks += increment;
        else stallWatchTicks = Integer.MAX_VALUE;
        return stallWatchTicks >= forwardStallTimeoutSeconds.get() * 20 ? RecoveryCause.ForwardStall : RecoveryCause.None;
    }

    private boolean isForwardProgressWatchArmed(HighwayBuilderTHM builder) {
        if (stallWatchMode != StallWatchMode.Forward || !recoverForwardStalls.get() || builder == null || !builder.isActive() || builder.isTpsThrottlePaused() || !builder.isInForwardState()) {
            return false;
        }

        HorizontalDirection direction = builder.getWorkingDirection();
        return direction != null
            && direction == stallWatchDirection
            && stallWatchTicks >= forwardStallTimeoutSeconds.get() * 20;
    }

    private void resetForwardProgressWatch() {
        stallWatchMode = StallWatchMode.None;
        stallWatchDirection = null;
        bestForwardProgressCoordinate = 0.0;
        stallWatchTicks = 0;
    }

    private boolean isStallRecoveryCause(RecoveryCause recoveryCause) {
        return recoveryCause == RecoveryCause.ForwardStall || recoveryCause == RecoveryCause.CenterStall;
    }

    private String stallRecoveryLabel(RecoveryCause recoveryCause) {
        return recoveryCause == RecoveryCause.CenterStall ? "Center" : "Forward";
    }

    private void queueForwardCorrectionPacket() {
        if (!forwardCorrectionPacketWatchArmed) return;
        pendingForwardCorrectionPackets.incrementAndGet();
    }

    private void armForwardCorrectionPacketWatch() {
        forwardCorrectionPacketWatchArmed = true;
    }

    private void disarmForwardCorrectionPacketWatch() {
        forwardCorrectionPacketWatchArmed = false;
        discardPendingForwardCorrectionPackets();
    }

    private void discardPendingForwardCorrectionPackets() {
        pendingForwardCorrectionPackets.getAndSet(0);
    }

    private void drainPendingForwardCorrectionPackets() {
        int pendingPackets = pendingForwardCorrectionPackets.getAndSet(0);
        for (int i = 0; i < pendingPackets; i++) {
            ghostblockCorrectionPacketTicks.add(ghostblockObservationTicks);
        }
    }

    private void recordForwardDestroyPacket(PlayerActionC2SPacket packet) {
        PlayerActionC2SPacket.Action action = packet.getAction();
        if (action != PlayerActionC2SPacket.Action.START_DESTROY_BLOCK
            && action != PlayerActionC2SPacket.Action.ABORT_DESTROY_BLOCK) {
            return;
        }

        if (mc == null || mc.player == null) return;

        HighwayBuilderTHM builder = Modules.get().get(HighwayBuilderTHM.class);
        if (builder == null || !builder.isActive() || !builder.isInForwardState() || builder.isTpsThrottlePaused()) return;

        HorizontalDirection direction = builder.getWorkingDirection();
        if (direction == null) return;

        if (forwardPacketDesyncDirection != null && forwardPacketDesyncDirection != direction) {
            resetForwardPacketDesyncEpisode("direction-change:" + forwardPacketDesyncDirection.name + "->" + direction.name);
        }

        if (!forwardPacketDesyncEpisodeActive) {
            forwardPacketDesyncEpisodeActive = true;
            forwardPacketDesyncDirection = direction;
            forwardPacketDesyncEpisodeId++;
            forwardPacketDesyncWiggleUsed = false;
            forwardPacketDesyncAwaitingRetry = false;
            forwardPacketDesyncLastSummary = "";
        }

        int tick = mc.player.age;
        forwardDestroyPacketSamples.add(new ForwardDestroyPacketSample(tick, packet.getPos().toImmutable(), action));
        pruneForwardPacketDesyncSamples(tick);
    }

    private void pruneForwardPacketDesyncSamples(int tick) {
        int oldestAllowedTick = tick - FORWARD_PACKET_DESYNC_WINDOW_TICKS;
        forwardDestroyPacketSamples.removeIf(sample -> sample.tick < oldestAllowedTick);
    }

    private void resetForwardPacketDesyncEpisode(String reason) {
        boolean logReset = forwardPacketDesyncWiggleUsed
            || forwardPacketDesyncAwaitingRetry
            || (forwardPacketDesyncLastSummary != null && !forwardPacketDesyncLastSummary.isBlank());
        if (logReset) {
            info(
                "Forward packet-desync episode reset (%s, episode=%d, wiggleUsed=%s, awaitingRetry=%s, samples=%d, last=%s).",
                reason,
                forwardPacketDesyncEpisodeId,
                forwardPacketDesyncWiggleUsed,
                forwardPacketDesyncAwaitingRetry,
                forwardDestroyPacketSamples.size(),
                forwardPacketDesyncLastSummary == null || forwardPacketDesyncLastSummary.isBlank() ? "none" : forwardPacketDesyncLastSummary
            );
        }

        forwardDestroyPacketSamples.clear();
        forwardPacketDesyncDirection = null;
        forwardPacketDesyncEpisodeActive = false;
        forwardPacketDesyncWiggleUsed = false;
        forwardPacketDesyncAwaitingRetry = false;
        forwardPacketDesyncLastSummary = "";
    }

    private void clearForwardPacketDesyncSamples(String reason) {
        if (!forwardDestroyPacketSamples.isEmpty() && (forwardPacketDesyncWiggleUsed || forwardPacketDesyncAwaitingRetry)) {
            info(
                "Forward packet-desync sample window cleared (%s, episode=%d, samples=%d, wiggleUsed=%s, awaitingRetry=%s).",
                reason,
                forwardPacketDesyncEpisodeId,
                forwardDestroyPacketSamples.size(),
                forwardPacketDesyncWiggleUsed,
                forwardPacketDesyncAwaitingRetry
            );
        }
        forwardDestroyPacketSamples.clear();
        forwardPacketDesyncLastSummary = "";
    }

    private ForwardPacketDesyncWindow currentForwardPacketDesyncWindow() {
        if (mc == null || mc.player == null) return null;
        if (forwardDestroyPacketSamples.isEmpty()) return null;
        pruneForwardPacketDesyncSamples(mc.player.age);

        Map<BlockPos, int[]> countsByPos = new HashMap<>();
        int starts = 0;
        int aborts = 0;
        int firstTick = Integer.MAX_VALUE;
        int lastTick = Integer.MIN_VALUE;

        for (ForwardDestroyPacketSample sample : forwardDestroyPacketSamples) {
            firstTick = Math.min(firstTick, sample.tick);
            lastTick = Math.max(lastTick, sample.tick);

            int[] counts = countsByPos.computeIfAbsent(sample.pos, ignored -> new int[2]);
            if (sample.action == PlayerActionC2SPacket.Action.START_DESTROY_BLOCK) {
                starts++;
                counts[0]++;
            } else if (sample.action == PlayerActionC2SPacket.Action.ABORT_DESTROY_BLOCK) {
                aborts++;
                counts[1]++;
            }
        }

        if (firstTick == Integer.MAX_VALUE) return null;
        int actions = starts + aborts;
        StringBuilder targets = new StringBuilder();
        int written = 0;
        for (Map.Entry<BlockPos, int[]> entry : countsByPos.entrySet()) {
            if (written++ > 0) targets.append(';');
            BlockPos pos = entry.getKey();
            int[] counts = entry.getValue();
            targets.append('(')
                .append(pos.getX()).append(',')
                .append(pos.getY()).append(',')
                .append(pos.getZ()).append(")=")
                .append(counts[0]).append('/')
                .append(counts[1]);
        }

        String summary = String.format(
            Locale.ROOT,
            "episode=%d actions=%d starts=%d aborts=%d distinct=%d firstTick=%d lastTick=%d noProgressTicks=%d targets=%s",
            forwardPacketDesyncEpisodeId,
            actions,
            starts,
            aborts,
            countsByPos.size(),
            firstTick,
            lastTick,
            ghostblockNoConfirmedProgressTicks,
            targets
        );

        return new ForwardPacketDesyncWindow(actions, starts, aborts, countsByPos.size(), firstTick, lastTick, summary);
    }

    private boolean shouldTriggerForwardPacketDesync() {
        if (!forwardPacketDesyncEpisodeActive) return false;
        if (forwardPacketDesyncAwaitingRetry) return false;
        if (ghostblockNoConfirmedProgressTicks < FORWARD_PACKET_DESYNC_WINDOW_TICKS) return false;

        ForwardPacketDesyncWindow window = currentForwardPacketDesyncWindow();
        if (window == null) return false;
        if (window.actions() < FORWARD_PACKET_DESYNC_MIN_ACTIONS) return false;
        if (window.distinctTargets() > FORWARD_PACKET_DESYNC_MAX_DISTINCT_TARGETS) return false;

        forwardPacketDesyncLastSummary = window.summary();
        return true;
    }

    private static double projectedForwardCoordinate(double x, double z, HorizontalDirection direction) {
        double magnitude = Math.hypot(direction.offsetX, direction.offsetZ);
        if (magnitude <= 0.0) return 0.0;
        return (x * direction.offsetX + z * direction.offsetZ) / magnitude;
    }

    private GhostblockReconnectTrigger updateRubberbandGhostblockWatch(HighwayBuilderTHM builder) {
        if (!autoRecover.get() || !recoverRubberbandGhostblocks.get()) {
            resetRubberbandGhostblockWatch();
            resetForwardPacketDesyncEpisode("rubberband-ghostblock-disabled");
            return GhostblockReconnectTrigger.None;
        }

        if (isReconnectRecoveryWorkActive()) {
            resetRubberbandGhostblockWatch();
            resetForwardPacketDesyncEpisode("reconnect-work-active");
            return GhostblockReconnectTrigger.None;
        }

        if (recoveryPhase != RecoveryPhase.None || cooldownTicks > 0) {
            disarmForwardCorrectionPacketWatch();
            if (recoveryPhase != RecoveryPhase.None) resetForwardPacketDesyncEpisode("monitor-recovery-active");
            return GhostblockReconnectTrigger.None;
        }

        if (mc.player == null || mc.world == null || builder == null || !builder.isActive()) {
            resetRubberbandGhostblockWatch();
            resetForwardPacketDesyncEpisode("builder-inactive");
            return GhostblockReconnectTrigger.None;
        }

        if (builder.isTpsThrottlePaused()) {
            resetRubberbandGhostblockWatch();
            return GhostblockReconnectTrigger.None;
        }

        if (builder.shouldSuppressThmHwyMonitorMisalignmentRecovery() || !builder.isInForwardState()) {
            resetRubberbandGhostblockWatch();
            return GhostblockReconnectTrigger.None;
        }

        HorizontalDirection direction = builder.getWorkingDirection();
        if (direction == null) {
            resetRubberbandGhostblockWatch();
            resetForwardPacketDesyncEpisode("direction-null");
            return GhostblockReconnectTrigger.None;
        }

        if (forwardPacketDesyncDirection != null && forwardPacketDesyncDirection != direction) {
            resetForwardPacketDesyncEpisode("direction-change:" + forwardPacketDesyncDirection.name + "->" + direction.name);
        }

        if (forwardPacketDesyncAwaitingRetry) {
            forwardPacketDesyncAwaitingRetry = false;
            clearForwardPacketDesyncSamples("returned-to-forward-after-wiggle");
            info("Forward packet-desync episode %d returned to Forward after wiggle; resuming packet fingerprinting before reconnect fallback.", forwardPacketDesyncEpisodeId);
        }

        double projected = projectedForwardCoordinate(mc.player.getX(), mc.player.getZ(), direction);
        if (!ghostblockWatchActive || ghostblockWatchDirection != direction) {
            startRubberbandGhostblockWatch(direction, projected);
            return GhostblockReconnectTrigger.None;
        }

        armForwardCorrectionPacketWatch();

        ghostblockObservationTicks++;
        ghostblockNoConfirmedProgressTicks++;
        if (ghostblockCandidateActive) ghostblockCandidateTicks++;

        boolean shouldSampleProgress = ghostblockTickSamplingActive || ++ghostblockLowRateSampleTicks >= GHOSTBLOCK_LOW_RATE_SAMPLE_TICKS;
        if (shouldSampleProgress) {
            ghostblockLowRateSampleTicks = 0;
            sampleGhostblockConfirmedProgress(projected);
        }

        if (!ghostblockTickSamplingActive && ghostblockNoConfirmedProgressTicks >= GHOSTBLOCK_ESCALATE_TICKS) {
            ghostblockTickSamplingActive = true;
            ghostblockRecentPeakCoordinate = Math.max(ghostblockRecentPeakCoordinate, projected);
            ghostblockHasLastProjectedCoordinate = false;
        }

        drainPendingForwardCorrectionPackets();
        pruneForwardCorrectionPackets();
        if (shouldTriggerForwardCorrectionRecovery()) {
            return GhostblockReconnectTrigger.Rubberband;
        }

        if (shouldTriggerForwardPacketDesync()) {
            return GhostblockReconnectTrigger.PacketDesync;
        }

        if (ghostblockTickSamplingActive) {
            sampleRubberbandMovement(projected);
            pruneRubberbandEvents();
            if (
                ghostblockObservationTicks >= RUBBERBAND_FORWARD_WARMUP_TICKS
                    && ghostblockRubberbandEventTicks.size() >= RUBBERBAND_EVENT_TRIGGER_COUNT
            ) {
                return GhostblockReconnectTrigger.Rubberband;
            }
        }

        if (ghostblockNoConfirmedProgressTicks >= GHOSTBLOCK_NO_PROGRESS_TRIGGER_TICKS) {
            return GhostblockReconnectTrigger.LongNoProgress;
        }

        return GhostblockReconnectTrigger.None;
    }

    private void startRubberbandGhostblockWatch(HorizontalDirection direction, double projected) {
        ghostblockWatchActive = true;
        ghostblockWatchDirection = direction;
        ghostblockTickSamplingActive = false;
        ghostblockObservationTicks = 0;
        ghostblockNoConfirmedProgressTicks = 0;
        ghostblockLowRateSampleTicks = 0;
        ghostblockCandidateActive = false;
        ghostblockCandidateCoordinate = 0.0;
        ghostblockCandidateTicks = 0;
        ghostblockConfirmedBestCoordinate = projected;
        ghostblockRecentPeakCoordinate = projected;
        ghostblockLastProjectedCoordinate = projected;
        ghostblockHasLastProjectedCoordinate = true;
        ghostblockRubberbandEventTicks.clear();
        ghostblockCorrectionPacketTicks.clear();
        discardPendingForwardCorrectionPackets();
        armForwardCorrectionPacketWatch();
    }

    private void sampleGhostblockConfirmedProgress(double projected) {
        if (ghostblockCandidateActive) {
            if (projected + FORWARD_PROGRESS_RESET_EPSILON < ghostblockCandidateCoordinate) {
                ghostblockCandidateActive = false;
                ghostblockCandidateTicks = 0;
            } else if (ghostblockCandidateTicks >= GHOSTBLOCK_CONFIRM_TICKS && projected >= ghostblockCandidateCoordinate) {
                confirmGhostblockProgress(Math.max(projected, ghostblockCandidateCoordinate));
                return;
            }
        }

        if (!ghostblockCandidateActive && projected >= ghostblockConfirmedBestCoordinate + GHOSTBLOCK_CONFIRMED_PROGRESS_BLOCKS) {
            ghostblockCandidateActive = true;
            ghostblockCandidateCoordinate = projected;
            ghostblockCandidateTicks = 0;
        }
    }

    private void confirmGhostblockProgress(double projected) {
        resetForwardPacketDesyncEpisode("confirmed-forward-progress");
        ghostblockConfirmedBestCoordinate = Math.max(ghostblockConfirmedBestCoordinate, projected);
        ghostblockRecentPeakCoordinate = ghostblockConfirmedBestCoordinate;
        ghostblockNoConfirmedProgressTicks = 0;
        ghostblockLowRateSampleTicks = 0;
        ghostblockCandidateActive = false;
        ghostblockCandidateTicks = 0;
        ghostblockTickSamplingActive = false;
        ghostblockHasLastProjectedCoordinate = false;
        ghostblockRubberbandEventTicks.clear();
        ghostblockCorrectionPacketTicks.clear();
        discardPendingForwardCorrectionPackets();
    }

    private void sampleRubberbandMovement(double projected) {
        if (!ghostblockHasLastProjectedCoordinate) {
            ghostblockLastProjectedCoordinate = projected;
            ghostblockRecentPeakCoordinate = Math.max(ghostblockRecentPeakCoordinate, projected);
            ghostblockHasLastProjectedCoordinate = true;
            return;
        }

        if (projected > ghostblockRecentPeakCoordinate) {
            ghostblockRecentPeakCoordinate = projected;
        }

        if (ghostblockRecentPeakCoordinate - projected >= RUBBERBAND_BACKTRACK_BLOCKS) {
            ghostblockRubberbandEventTicks.add(ghostblockObservationTicks);
            ghostblockRecentPeakCoordinate = projected;
            ghostblockCandidateActive = false;
            ghostblockCandidateTicks = 0;
        }

        ghostblockLastProjectedCoordinate = projected;
    }

    private void pruneRubberbandEvents() {
        int oldestAllowedTick = ghostblockObservationTicks - RUBBERBAND_EVENT_WINDOW_TICKS;
        ghostblockRubberbandEventTicks.removeIf(tick -> tick < oldestAllowedTick);
    }

    private void pruneForwardCorrectionPackets() {
        int oldestAllowedTick = ghostblockObservationTicks - FORWARD_CORRECTION_SLOW_WINDOW_TICKS;
        ghostblockCorrectionPacketTicks.removeIf(tick -> tick < oldestAllowedTick);
    }

    private boolean shouldTriggerForwardCorrectionRecovery() {
        if (ghostblockNoConfirmedProgressTicks <= 0) return false;

        if (
            ghostblockObservationTicks >= FORWARD_CORRECTION_FAST_MIN_WATCH_TICKS
                && ghostblockNoConfirmedProgressTicks >= FORWARD_CORRECTION_FAST_MIN_WATCH_TICKS
                && countForwardCorrectionPackets(FORWARD_CORRECTION_FAST_WINDOW_TICKS) >= FORWARD_CORRECTION_FAST_TRIGGER_COUNT
        ) {
            return true;
        }

        return ghostblockNoConfirmedProgressTicks >= FORWARD_CORRECTION_SLOW_MIN_STALLED_TICKS
            && countForwardCorrectionPackets(FORWARD_CORRECTION_SLOW_WINDOW_TICKS) >= FORWARD_CORRECTION_SLOW_TRIGGER_COUNT;
    }

    private int countForwardCorrectionPackets(int windowTicks) {
        int oldestAllowedTick = ghostblockObservationTicks - windowTicks;
        int count = 0;
        for (int tick : ghostblockCorrectionPacketTicks) {
            if (tick >= oldestAllowedTick) count++;
        }
        return count;
    }

    private void resetRubberbandGhostblockWatch() {
        ghostblockWatchActive = false;
        ghostblockWatchDirection = null;
        ghostblockTickSamplingActive = false;
        ghostblockObservationTicks = 0;
        ghostblockNoConfirmedProgressTicks = 0;
        ghostblockLowRateSampleTicks = 0;
        ghostblockCandidateActive = false;
        ghostblockCandidateCoordinate = 0.0;
        ghostblockCandidateTicks = 0;
        ghostblockConfirmedBestCoordinate = 0.0;
        ghostblockRecentPeakCoordinate = 0.0;
        ghostblockLastProjectedCoordinate = 0.0;
        ghostblockHasLastProjectedCoordinate = false;
        ghostblockRubberbandEventTicks.clear();
        ghostblockCorrectionPacketTicks.clear();
        disarmForwardCorrectionPacketWatch();
    }

    private boolean isReconnectRecoveryWorkActive() {
        return restartRecoveryActive
            || restartEvidenceGateCycleId != 0L
            || delayedMainServerResumePending
            || delayedMainServerResumeCycleId != 0L
            || postRejoinDirectionGateActive
            || deferredRestartScreenshotAfterReconnectPending;
    }

    private boolean tryBeginRubberbandGhostblockReconnect(HighwayBuilderTHM builder, GhostblockReconnectTrigger trigger) {
        if (trigger == GhostblockReconnectTrigger.None || builder == null) return false;
        if (builder.isTpsThrottlePaused()) {
            resetRubberbandGhostblockWatch();
            return false;
        }

        String triggerLabel = switch (trigger) {
            case Rubberband -> "rubberband";
            case PacketDesync -> "packet-desync";
            case LongNoProgress -> "ghostblock";
            case None -> "none";
        };

        if (trigger == GhostblockReconnectTrigger.PacketDesync) {
            ForwardPacketDesyncWindow window = currentForwardPacketDesyncWindow();
            String summary = window == null
                ? (forwardPacketDesyncLastSummary == null || forwardPacketDesyncLastSummary.isBlank() ? "unavailable" : forwardPacketDesyncLastSummary)
                : window.summary();
            int actions = window == null ? 0 : window.actions();
            int distinctTargets = window == null ? 0 : window.distinctTargets();
            HighwayBuilderTHM.DesyncWiggleProbeResult probeResult = builder.tryStartForwardDesyncWiggleProbe(
                forwardPacketDesyncEpisodeId,
                summary,
                actions,
                distinctTargets,
                FORWARD_PACKET_DESYNC_WINDOW_TICKS
            );

            info(
                "Forward packet-desync monitor handoff result=%s episode=%d wiggleUsed=%s awaitingRetry=%s summary=%s.",
                probeResult,
                forwardPacketDesyncEpisodeId,
                forwardPacketDesyncWiggleUsed,
                forwardPacketDesyncAwaitingRetry,
                summary
            );

            if (probeResult == HighwayBuilderTHM.DesyncWiggleProbeResult.Started) {
                forwardPacketDesyncWiggleUsed = true;
                forwardPacketDesyncAwaitingRetry = true;
                clearForwardPacketDesyncSamples("wiggle-started");
                return true;
            }

            if (probeResult == HighwayBuilderTHM.DesyncWiggleProbeResult.AlreadyRunning) {
                forwardPacketDesyncAwaitingRetry = true;
                return true;
            }

            if (probeResult != HighwayBuilderTHM.DesyncWiggleProbeResult.AlreadyUsed) {
                warning("Forward packet-desync wiggle probe was unavailable; continuing existing reconnect recovery path.");
            }
        }

        if (!reconnectAutomationEnabled()) {
            warning("Forward %s recovery detected, but THMHwyMonitor auto-reconnect is disabled. Leaving HighwayBuilder running.", triggerLabel);
            resetRubberbandGhostblockWatch();
            return false;
        }

        if (!hasLiveServerConnection()) {
            warning("Forward %s recovery detected, but there is no live server connection to disconnect from.", triggerLabel);
            resetRubberbandGhostblockWatch();
            return false;
        }

        if (!builder.isActive() || !builder.isInForwardState() || builder.getWorkingDirection() == null) {
            resetRubberbandGhostblockWatch();
            return false;
        }

        long cycleId = switch (trigger) {
            case Rubberband -> armReconnectCycleSeconds(RUBBERBAND_RECONNECT_DELAY_SECONDS, "forward-rubberband", true);
            case PacketDesync -> armReconnectCycle("forward-packet-desync", true);
            case LongNoProgress -> armReconnectCycle("forward-ghostblock", true);
            case None -> armReconnectCycle("forward-unknown", true);
        };
        reconnectOwner = ReconnectOwner.HighwayBuilder;
        rearmNormalReconnectAfterForwardReconnectResume = true;

        if (!verifyForwardReconnectPreflight(cycleId, trigger)) {
            abortRubberbandGhostblockReconnectAttempt("preflight-failed", true);
            return false;
        }

        restartRecoveryActive = true;
        resetRubberbandGhostblockWatch();
        resetForwardPacketDesyncEpisode("monitor-reconnect:" + triggerLabel);

        if (!builder.prepareForMonitorReconnectPause(cycleId)) {
            warning("Forward %s recovery aborted: unable to establish HighwayBuilder reconnect baseline.", triggerLabel);
            abortRubberbandGhostblockReconnectAttempt("builder-baseline-failed", true);
            return false;
        }

        ModuleManager manager = getModuleManager();
        if (manager != null && manager.isActive() && !manager.prepareForMonitorReconnectPause(cycleId, "forward-" + triggerLabel)) {
            warning("Forward %s recovery aborted: unable to freeze Module Manager reconnect snapshot.", triggerLabel);
            abortRubberbandGhostblockReconnectAttempt("module-manager-baseline-failed", true);
            return false;
        }

        if (!builder.checkpointStatsForMonitorReconnectPause("forward-" + triggerLabel + "-pre-disconnect")) {
            warning("Forward %s recovery aborted: unable to checkpoint HighwayBuilder stats before disconnect.", triggerLabel);
            abortRubberbandGhostblockReconnectAttempt("stats-checkpoint-failed", true);
            return false;
        }

        builder.disableForMonitorRealignPause();
        if (builder.isActive()) {
            warning("Forward %s recovery aborted: HighwayBuilder did not disable for reconnect pause.", triggerLabel);
            abortRubberbandGhostblockReconnectAttempt("builder-disable-failed", true);
            return false;
        }

        info(
            "Forward %s recovery armed cycle %d. Disconnecting so AutoReconnect can resume HighwayBuilder.",
            triggerLabel,
            cycleId
        );
        mc.getNetworkHandler().getConnection().disconnect(Text.of("THMHwyMonitor Forward " + triggerLabel + " recovery"));
        return true;
    }

    private boolean verifyForwardReconnectPreflight(long cycleId, GhostblockReconnectTrigger trigger) {
        ServerReconnectService.ReconnectPreflight preflight = reconnectService().getReconnectPreflight();
        String triggerLabel = switch (trigger) {
            case Rubberband -> "rubberband";
            case PacketDesync -> "packet-desync";
            case LongNoProgress -> "ghostblock";
            case None -> "unknown";
        };

        if (!preflight.serviceArmed() || preflight.cycleId() != cycleId) {
            warning("Forward %s recovery aborted: reconnect service was not armed for cycle %d.", triggerLabel, cycleId);
            return false;
        }

        if (!preflight.autoReconnectModulePresent() || !preflight.autoReconnectActive()) {
            warning("Forward %s recovery aborted: Meteor AutoReconnect is missing or inactive.", triggerLabel);
            return false;
        }

        Double moduleDelaySeconds = preflight.autoReconnectSettingDelaySeconds();
        if (moduleDelaySeconds == null) {
            warning("Forward %s recovery aborted: Meteor AutoReconnect delay could not be read.", triggerLabel);
            return false;
        }

        int effectiveDelaySeconds = preflight.effectiveDelaySeconds();
        if (trigger == GhostblockReconnectTrigger.Rubberband && effectiveDelaySeconds != RUBBERBAND_RECONNECT_DELAY_SECONDS) {
            warning(
                "Forward rubberband recovery aborted: expected exact %d second reconnect delay, got %d.",
                RUBBERBAND_RECONNECT_DELAY_SECONDS,
                effectiveDelaySeconds
            );
            return false;
        }

        if (Math.abs(moduleDelaySeconds - effectiveDelaySeconds) > 0.5) {
            warning(
                "Forward %s recovery aborted: Meteor AutoReconnect delay %.1fs does not match service delay %ds.",
                triggerLabel,
                moduleDelaySeconds,
                effectiveDelaySeconds
            );
            return false;
        }

        return true;
    }

    private void abortRubberbandGhostblockReconnectAttempt(String reason, boolean rearmNormalReconnect) {
        clearRestartAutomationState("forward-rubberband-ghostblock-abort:" + reason, true, true);
        resetRubberbandGhostblockWatch();

        if (rearmNormalReconnect && reconnectAutomationEnabled() && isActive()) {
            long cycleId = armReconnectCycle("forward-recovery-abort-rearm", false);
            reconnectOwner = ReconnectOwner.HighwayBuilder;
            info("Re-armed normal AutoReconnect cycle %d after aborted forward reconnect recovery.", cycleId);
        }
    }

    private void handleReconnectAutomationTickLane() {
        handleAutoReconnectToggleTransitions();
        tryPendingAbortRestore("tick");
        refreshReconnectBaselineValidity();
        maybeRunDelayedMainServerResumeFinalization();
        maybeRunPostRejoinDirectionGate();

        boolean liveConnectedNow = hasLiveServerConnection();
        clearStaleDisconnectedScreenIfLiveConnected();
        handlePendingDisconnectScreenEvidenceCheck(liveConnectedNow);
        wasConnectedLastTick = liveConnectedNow;

        if (consumeNonRestartHardFailSignal() && !stashMoverReconnectHandlingActive()) {
            nonRestartHardFailArmed = true;
            handleDetectedNonRestartHardFail("signal");
        }

        if (reconnectService().isReconnectArmed() && restartRecoveryActive && reconnectOwner == ReconnectOwner.HighwayBuilder) {
            ensureHighwayBuilderDisabledForRestart("tick guard", false);
        }

        if (liveConnectedNow) maybeTakeDeferredRestartScreenshotAfterReconnect("tick");
    }

    private boolean hasLiveServerConnection() {
        return mc != null
            && mc.player != null
            && mc.world != null
            && mc.getNetworkHandler() != null
            && mc.getNetworkHandler().getConnection() != null
            && mc.getNetworkHandler().getConnection().isOpen();
    }

    private ServerState getCommittedServerState() {
        return ServerStatusHandler.getInstance().getCommittedState();
    }

    private ModuleManager getModuleManager() {
        try {
            return Modules.get().get(ModuleManager.class);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private boolean isHighwayRecoveryAllowedOnCurrentServer() {
        return getCommittedServerState() == ServerState.MAIN_SERVER;
    }

    private void abortActiveRecoveryForNonMainServer(String source) {
        resetForwardProgressWatch();
        resetRubberbandGhostblockWatch();
        clearPendingAlignmentGateRequest();

        if (recoveryPhase == RecoveryPhase.None && !recoveryModulesPaused) return;

        preserveHighwayBuilderDisabledAcrossRecoveryResume();
        resetRecoveryState();
        cooldownTicks = recoveryCooldown.get();
        info(
            "Cleared THMHwyMonitor recovery state outside MAIN_SERVER (%s, committedState=%s).",
            source,
            getCommittedServerState().name()
        );
    }

    private boolean reconnectRecoveryInFlight() {
        if (activeReconnectCycleId == 0L) return false;
        return isReconnectRecoveryWorkActive();
    }

    private void clearStaleDisconnectedScreenIfLiveConnected() {
        if (!hasLiveServerConnection()) return;
        if (!(mc.currentScreen instanceof DisconnectedScreen)) return;
        info("Clearing stale DisconnectedScreen while client is already live in-world.");
        mc.setScreen(null);
    }

    private void clearRestartAutomationStateForTerminalStop(String reason) {
        boolean preserveIntentionalSafetyDisconnect = intentionalSafetyDisconnectArmed;
        boolean preserveDeferredDisable = disableMonitorAfterIntentionalSafetyDisconnect;
        clearRestartAutomationState(reason, true, true);
        intentionalSafetyDisconnectArmed = preserveIntentionalSafetyDisconnect;
        disableMonitorAfterIntentionalSafetyDisconnect = preserveDeferredDisable;
    }

    private void refreshReconnectBaselineValidity() {
        HighwayBuilderTHM builder = Modules.get().get(HighwayBuilderTHM.class);
        if (builder == null) return;
        builder.refreshReconnectBaselineValidity(activeReconnectCycleId);
    }

    private void maybeRunDelayedMainServerResumeFinalization() {
        if (!delayedMainServerResumePending) return;

        if (!isActive()) {
            clearDelayedMainServerResumeState();
            return;
        }

        if (delayedMainServerResumeCycleId <= 0L || delayedMainServerResumeCycleId != activeReconnectCycleId) {
            clearDelayedMainServerResumeState();
            return;
        }

        long now = System.currentTimeMillis();
        if (now < delayedMainServerResumeAtMs) return;
        if (mc == null || mc.player == null || mc.world == null) return;
        if (!isHighwayRecoveryAllowedOnCurrentServer()) return;

        long cycleId = delayedMainServerResumeCycleId;
        String contextTag = delayedMainServerResumeContext;
        clearDelayedMainServerResumeState();

        if (reconnectOwner == ReconnectOwner.StashMover) {
            THMStashMover stashMover = recoveryStashMover;
            clearStashMoverReconnectState("stash-mover-main-server-ready", true, true);
            if (stashMover != null && stashMover.isActive()) {
                stashMover.onMonitorReconnectMainServerReady(cycleId, contextTag);
            }
            return;
        }

        info(
            "Reconnect MAIN_SERVER delay complete (%s). Entering reconnect direction gate (cycle %d).",
            contextTag,
            cycleId
        );
        beginPostRejoinDirectionGate(cycleId, contextTag);
    }

    private float resolveRecoveryDirectionYawForInference(HighwayBuilderTHM builder) {
        HorizontalDirection direction = resolveWorkingDirectionForAlignment(
            builder,
            mc != null && mc.player != null ? mc.player.getYaw() : Float.NaN
        );
        return direction == null ? Float.NaN : direction.yaw;
    }

    private HorizontalDirection resolveWorkingDirectionForAlignment(HighwayBuilderTHM builder, float fallbackYaw) {
        HorizontalDirection builderDirection = builder == null ? null : builder.getWorkingDirection();
        if (mc == null || mc.player == null) return builderDirection;

        String preferredDirection = normalizeDirection(directionCode(builderDirection));
        if (preferredDirection.isEmpty()) preferredDirection = normalizeDirection(trackedDirection);

        SegmentProjection projection = selectBestSegmentProjection(
            collectSegmentProjections(mc.player.getX(), mc.player.getZ(), true, true, true, true, trueCenterMode.get()),
            trackedSegment,
            preferredDirection,
            fallbackYaw,
            WORKING_LINE_TOLERANCE
        );
        if (projection != null) {
            String detectedDirection = chooseSegmentTravelDirection(projection.segment(), fallbackYaw, preferredDirection);
            HorizontalDirection detected = parseDirectionCode(detectedDirection);
            if (projection.segment().isRingOrDiamond()) {
                if (detected != null) return detected;
            } else if (builderDirection != null && isDirectionCompatible(projection.segment(), preferredDirection)) {
                return builderDirection;
            } else if (detected != null) {
                return detected;
            }
        }

        if (builderDirection != null) return builderDirection;
        return inferClosestWorkingDirection(mc.player.getX(), mc.player.getZ(), fallbackYaw, trueCenterMode.get());
    }

    private boolean isLikelyCenterYawOverride(HighwayBuilderTHM builder, float yaw) {
        if (builder == null || !builder.isActive() || mc == null || mc.player == null) return false;
        if (Math.abs(wrapYaw(yaw)) > 0.01f) return false;

        // Mirrors HighwayBuilder Center state's "is centered" check.
        double x = Math.abs(mc.player.getX() - (int) mc.player.getX()) - 0.5;
        double z = Math.abs(mc.player.getZ() - (int) mc.player.getZ()) - 0.5;
        boolean isX = Math.abs(x) <= 0.1;
        boolean isZ = Math.abs(z) <= 0.1;
        return !(isX && isZ);
    }

    private void handleRecoveryPhase() {
        if (recoveryBuilder == null) {
            resetRecoveryState();
            return;
        }

        if (recoveryPhase == RecoveryPhase.WaitBeforeCorrection) {
            if (recoveryTicks > 0) {
                recoveryTicks--;
                return;
            }

            if (pendingCorrectionTarget == null || mc.player == null) {
                resetRecoveryState();
                cooldownTicks = recoveryCooldown.get();
                return;
            }

            if (!BaritoneUtils.IS_AVAILABLE) {
                failBaritoneRecovery("Baritone is not available");
                return;
            }

            if (!preparePendingRecoveryTargetForBaritoneAttempt()) return;

            IBaritone baritone = getPrimaryBaritoneForRecovery();
            if (baritone == null) {
                scheduleBaritoneRecoveryRetry("Unable to acquire primary Baritone instance");
                return;
            }

            if (!beginBaritoneStopPhase(baritone)) return;
            return;
        }

        if (recoveryPhase == RecoveryPhase.BaritoneStopping) {
            if (pendingCorrectionTarget == null || mc.player == null) {
                resetRecoveryState();
                cooldownTicks = recoveryCooldown.get();
                return;
            }

            if (recoveryTicks > 0) {
                recoveryTicks--;
                return;
            }

            IBaritone baritone = getPrimaryBaritoneForRecovery();
            if (baritone == null) {
                scheduleBaritoneRecoveryRetry("Primary Baritone instance disappeared during stop phase");
                return;
            }

            if (!isBaritoneStoppedForRecovery(baritone)) {
                if (!baritoneStopCommandTried) {
                    baritoneStopCommandTried = true;
                    executeBaritoneStopCommand(baritone);
                    recoveryTicks = 1;
                    return;
                }

                scheduleBaritoneRecoveryRetry("Baritone did not settle after stop");
                return;
            }

            if (!preparePendingRecoveryTargetForBaritoneAttempt()) return;

            try {
                baritone.getCustomGoalProcess().setGoalAndPath(new GoalBlock(
                    pendingCorrectionTarget.goalX(),
                    pendingCorrectionTarget.goalY(),
                    pendingCorrectionTarget.goalZ()
                ));
            } catch (Throwable t) {
                scheduleBaritoneRecoveryRetry("Baritone failed to start path: " + t.getClass().getSimpleName());
                return;
            }

            baritoneStartupTicks = BARITONE_PATH_STARTUP_TICKS;
            baritoneTimeoutTicks = BARITONE_PATH_TIMEOUT_TICKS;
            recoveryPhase = RecoveryPhase.BaritoneWalking;
            info("Baritone recovery path attempt %d/%d started to (%d, %d, %d).",
                baritoneRecoveryStartAttempts,
                BARITONE_RECOVERY_MAX_START_ATTEMPTS,
                pendingCorrectionTarget.goalX(),
                pendingCorrectionTarget.goalY(),
                pendingCorrectionTarget.goalZ()
            );
            return;
        }

        if (recoveryPhase == RecoveryPhase.BaritoneWalking) {
            if (pendingCorrectionTarget == null || mc.player == null) {
                resetRecoveryState();
                cooldownTicks = recoveryCooldown.get();
                return;
            }

            IBaritone baritone = getPrimaryBaritoneForRecovery();
            if (baritone == null) {
                scheduleBaritoneRecoveryRetry("Baritone became unavailable during recovery");
                return;
            }

            if (baritone.getPathingBehavior().isPathing()) {
                if (baritoneTimeoutTicks > 0) baritoneTimeoutTicks--;
                if (baritoneTimeoutTicks == 0) {
                    baritone.getPathingBehavior().cancelEverything();
                    scheduleBaritoneRecoveryRetry("Baritone recovery timed out");
                }
                return;
            }

            if (baritoneStartupTicks > 0) {
                baritoneStartupTicks--;
                return;
            }

            double distanceToGoal = horizontalDistanceToGoalCenter(pendingCorrectionTarget.goalX(), pendingCorrectionTarget.goalZ());
            if (distanceToGoal <= 0.85) {
                info("Baritone arrived at recovery goal. Setting yaw in 0.5s.");
            } else {
                scheduleBaritoneRecoveryRetry(String.format(Locale.ROOT, "Baritone stopped before recovery goal (%.2f blocks away)", distanceToGoal));
                return;
            }

            recoveryPhase = RecoveryPhase.WaitBeforeYaw;
            recoveryTicks = YAW_SET_DELAY_TICKS;
            return;
        }

        if (recoveryPhase == RecoveryPhase.WaitBeforeYaw) {
            if (recoveryTicks > 0) {
                recoveryTicks--;
                return;
            }

            applyStrictAlignmentSnap();
            applyWorkingYaw();
            info("Applied working-direction yaw. Resuming THM HighwayBuilder in 2.0s.");
            recoveryPhase = RecoveryPhase.WaitBeforeResume;
            recoveryTicks = RECOVERY_DELAY_TICKS;
            return;
        }

        if (recoveryPhase == RecoveryPhase.WaitBeforeResume) {
            if (recoveryTicks > 0) {
                recoveryTicks--;
                return;
            }

            resumePausedModulesAfterRecovery();
            info("Resumed recovery modules (THM HighwayBuilder / Timer / Speed) after recovery.");

            resetRecoveryState();
            cooldownTicks = recoveryCooldown.get();
        }
    }

    private boolean beginBaritoneStopPhase(IBaritone baritone) {
        if (baritoneRecoveryStartAttempts >= BARITONE_RECOVERY_MAX_START_ATTEMPTS) {
            failBaritoneRecovery("Baritone recovery exhausted start attempts");
            return false;
        }

        baritoneRecoveryStartAttempts++;
        baritoneStopCommandTried = false;
        stopBaritoneForRecovery(baritone);
        recoveryPhase = RecoveryPhase.BaritoneStopping;
        recoveryTicks = BARITONE_STOP_SETTLE_TICKS;
        info("Stopping Baritone before recovery attempt %d/%d.", baritoneRecoveryStartAttempts, BARITONE_RECOVERY_MAX_START_ATTEMPTS);
        return true;
    }

    private IBaritone getPrimaryBaritoneForRecovery() {
        if (!BaritoneUtils.IS_AVAILABLE) return null;
        try {
            return BaritoneAPI.getProvider().getPrimaryBaritone();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private void stopBaritoneForRecovery(IBaritone baritone) {
        if (baritone == null) return;
        try {
            baritone.getPathingBehavior().cancelEverything();
        } catch (Throwable ignored) {
        }
        try {
            baritone.getCustomGoalProcess().setGoal(null);
        } catch (Throwable ignored) {
        }
        try {
            baritone.getInputOverrideHandler().clearAllKeys();
        } catch (Throwable ignored) {
        }
    }

    private boolean isBaritoneStoppedForRecovery(IBaritone baritone) {
        if (baritone == null) return false;
        try {
            boolean pathClear = !baritone.getPathingBehavior().isPathing() && !baritone.getPathingBehavior().hasPath();
            boolean goalClear = baritone.getCustomGoalProcess().getGoal() == null;
            return pathClear && goalClear;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void executeBaritoneStopCommand(IBaritone baritone) {
        if (baritone == null) return;
        try {
            baritone.getCommandManager().execute("stop");
            info("Baritone recovery used command-manager stop fallback.");
        } catch (Throwable t) {
            warning("Baritone recovery stop fallback failed (%s).", t.getClass().getSimpleName());
        }
    }

    private boolean preparePendingRecoveryTargetForBaritoneAttempt() {
        if (recoveryCause != RecoveryCause.CenterStall) return true;

        RecoveryTarget refreshed = createLocalStallEscapeTarget(recoveryBuilder, null);
        if (refreshed == null) {
            triggerMonitorSafeBuilderHardFail(recoveryBuilder, "Center stall recovery could not recompute a local 2-block escape target.");
            return false;
        }

        if (!isSafeLocalStallEscapeTarget(refreshed)) {
            triggerMonitorSafeBuilderHardFail(recoveryBuilder, "Center stall recovery could not perform the 2-block backstep because the recomputed destination was blocked or unsafe.");
            return false;
        }

        pendingLocalStallEscapeTarget = refreshed;
        pendingCorrectionTarget = refreshed;
        return true;
    }

    private void scheduleBaritoneRecoveryRetry(String reason) {
        if (baritoneRecoveryStartAttempts >= BARITONE_RECOVERY_MAX_START_ATTEMPTS) {
            failBaritoneRecovery(reason);
            return;
        }

        warning(
            "%s. Retrying Baritone recovery in %.1fs (%d/%d attempts used).",
            reason,
            BARITONE_RECOVERY_RETRY_DELAY_TICKS / 20.0,
            baritoneRecoveryStartAttempts,
            BARITONE_RECOVERY_MAX_START_ATTEMPTS
        );
        baritoneStartupTicks = 0;
        baritoneTimeoutTicks = 0;
        baritoneStopCommandTried = false;
        recoveryPhase = RecoveryPhase.WaitBeforeCorrection;
        recoveryTicks = BARITONE_RECOVERY_RETRY_DELAY_TICKS;
    }

    private void failBaritoneRecovery(String reason) {
        if (isStallRecoveryCause(recoveryCause)) {
            triggerMonitorSafeBuilderHardFail(
                recoveryBuilder,
                "%s stall recovery failed: %s.",
                stallRecoveryLabel(recoveryCause),
                reason
            );
            return;
        }

        warning("Baritone recovery failed: %s.", reason);
        recoveryPhase = RecoveryPhase.WaitBeforeResume;
        recoveryTicks = RECOVERY_DELAY_TICKS;
    }

    private void resetRecoveryState() {
        recoveryBuilder = null;
        pendingCorrectionTarget = null;
        pendingLocalStallEscapeTarget = null;
        recoveryTicks = 0;
        baritoneStartupTicks = 0;
        baritoneTimeoutTicks = 0;
        baritoneRecoveryStartAttempts = 0;
        baritoneStopCommandTried = false;
        recoveryPhase = RecoveryPhase.None;
        recoveryCause = RecoveryCause.None;
        recoveryYawBeforeMove = Float.NaN;
        clearPendingAlignmentGateRequest();
        if (recoveryModulesPaused) resumePausedModulesAfterRecovery();
    }

    private void handleExcessiveMisalignment(HighwayBuilderTHM builder, RecoveryTarget target) {
        // Ensure no previously paused modules remain paused before stopping monitor recovery.
        resumePausedModulesAfterRecovery();

        if (builder != null && builder.isActive()) {
            builder.disableForMonitorRealignPause();
            if (builder.isActive()) warning("Failed to toggle THM HighwayBuilder off after excessive misalignment.");
            else info("Toggled THM HighwayBuilder off after excessive misalignment.");
        }

        resetRecoveryState();
        cooldownTicks = recoveryCooldown.get();
        warning(
            "Too far from a highway to realign automatically (%.2f on %s %s, max %.2f). Disabled THM HighwayBuilder and THM Hwy Monitor. Move closer to a highway and try again.",
            target.distance(),
            target.highway(),
            target.direction(),
            maxCorrectionDistance.get()
        );
        if (isActive()) toggle();
    }

    private boolean pauseAllActiveModulesForRecovery() {
        if (recoveryModulesPaused) return true;

        recoveryPausedModules.clear();
        Module highwayBuilder = Modules.get().get(HighwayBuilderTHM.class);
        Module timer = Modules.get().get(Timer.class);
        Module speed = Modules.get().get(Speed.class);

        internalTimerSpeedToggleInProgress = true;
        try {
            pauseModuleForRecovery(highwayBuilder);
            pauseModuleForRecovery(timer);
            pauseModuleForRecovery(speed);
        } finally {
            internalTimerSpeedToggleInProgress = false;
        }

        recoveryModulesPaused = true;
        return true;
    }

    private void pauseModuleForRecovery(Module module) {
        if (module == null || module == this) return;
        if (!module.isActive()) return;

        recoveryPausedModules.add(module);
        if (module instanceof HighwayBuilderTHM builder) {
            if (builder.isLegacyCenterSpeedOwnershipAllowed()) {
                builder.preserveCenterSpeedBaselineForMonitorRecovery("thm-monitor-pause");
            }
            builder.disableForMonitorRealignPause();
        } else {
            module.disable();
        }
    }

    private void resumePausedModulesAfterRecovery() {
        if (!recoveryModulesPaused) return;

        internalTimerSpeedToggleInProgress = true;
        try {
            for (Module module : new ArrayList<>(recoveryPausedModules)) {
                if (module == null) continue;
                module.enable();
            }
        } finally {
            internalTimerSpeedToggleInProgress = false;
        }

        recoveryPausedModules.clear();
        recoveryModulesPaused = false;
    }

    private void abortActiveRecoveryForNonRestartHardFail() {
        preserveHighwayBuilderDisabledAcrossRecoveryResume();
        resetRecoveryState();
        resetForwardProgressWatch();
        cooldownTicks = recoveryCooldown.get();
    }

    private void preserveHighwayBuilderDisabledAcrossRecoveryResume() {
        if (!recoveryModulesPaused) return;
        recoveryPausedModules.removeIf(module -> module instanceof HighwayBuilderTHM);
    }

    public boolean IsAligned(boolean cardinal, boolean diagonal, boolean ring, boolean diamond, boolean trueCenterMode) {
        if (mc.player == null) return false;

        return IsAligned(mc.player.getX(), mc.player.getZ(), cardinal, diagonal, ring, diamond, trueCenterMode);
    }

    public boolean IsAligned(double playerX, double playerZ, boolean cardinal, boolean diagonal, boolean ring, boolean diamond, boolean trueCenterMode) {
        return IsAlignedResult(playerX, playerZ, cardinal, diagonal, ring, diamond, trueCenterMode).aligned();
    }

    public AlignmentResult IsAlignedResult(boolean cardinal, boolean diagonal, boolean ring, boolean diamond, boolean trueCenterMode) {
        if (mc.player == null) return AlignmentResult.notAligned();

        return IsAlignedResult(mc.player.getX(), mc.player.getZ(), mc.player.getYaw(), cardinal, diagonal, ring, diamond, trueCenterMode);
    }

    public AlignmentResult IsAlignedResult(double playerX, double playerZ, boolean cardinal, boolean diagonal, boolean ring, boolean diamond, boolean trueCenterMode) {
        float yaw = mc.player != null ? mc.player.getYaw() : Float.NaN;
        return IsAlignedResult(playerX, playerZ, yaw, cardinal, diagonal, ring, diamond, trueCenterMode);
    }

    public AlignmentResult IsAlignedResult(double playerX, double playerZ, float playerYaw, boolean cardinal, boolean diagonal, boolean ring, boolean diamond, boolean trueCenterMode) {
        double centerOffset = trueCenterMode ? 0.5 : 0.0;
        SegmentProjection bestProjection = selectBestSegmentProjection(
            collectSegmentProjections(playerX, playerZ, cardinal, diagonal, ring, diamond, trueCenterMode),
            null,
            "",
            playerYaw,
            0.0
        );
        if (bestProjection == null) return AlignmentResult.notAligned();

        String bestHighway = bestProjection.segment().highway();
        String bestDirection = resolveAlignmentDirectionLabel(bestProjection.segment(), playerX, playerZ, playerYaw, centerOffset);
        double bestDistance = bestProjection.distance();

        if (bestDistance <= WORKING_LINE_TOLERANCE) {
            if (bestProjection.segment().isRingOrDiamond()) {
                String facing = yawToDirection(playerYaw);
                bestDirection = bestDirection + "->" + facing;
            }
            return new AlignmentResult(true, bestHighway, bestDirection, bestDistance);
        }

        return new AlignmentResult(false, "None", "None", bestDistance);
    }

    private static RecoveryTarget determineRecoveryTarget(
        double playerX,
        double playerZ,
        float playerYaw,
        int recoveryGoalY,
        boolean trueCenterMode,
        HighwaySegment preferredSegment,
        String preferredDirection
    ) {
        List<SegmentProjection> candidates = collectSegmentProjections(playerX, playerZ, true, true, true, true, trueCenterMode);
        SegmentProjection projection = preferredSegment != null
            ? findProjectionForSegment(candidates, preferredSegment)
            : selectBestSegmentProjection(candidates, null, preferredDirection, playerYaw, WORKING_LINE_TOLERANCE);
        if (projection == null) return null;

        String travelDirection = chooseSegmentTravelDirection(projection.segment(), playerYaw, preferredDirection);
        if (travelDirection.isEmpty()) return null;

        String direction = displayDirectionForSegment(projection.segment(), travelDirection);
        return new RecoveryTarget(
            projection.segment().highway(),
            direction,
            travelDirection,
            projection.targetX(),
            projection.targetZ(),
            floorToBlock(projection.targetX()),
            recoveryGoalY,
            floorToBlock(projection.targetZ()),
            yawForWorkingDirection(travelDirection),
            projection.distance(),
            projection.segment()
        );
    }

    private static String resolveAlignmentDirectionLabel(HighwaySegment segment, double playerX, double playerZ, float playerYaw, double centerOffset) {
        if (segment == null) return "None";
        if (segment.line() != null) {
            return resolveDirectionForAlignmentResult(segment.line(), playerX, playerZ, playerYaw, centerOffset);
        }
        return segment.segmentLabel();
    }

    private static String displayDirectionForSegment(HighwaySegment segment, String travelDirection) {
        if (segment == null) return normalizeDirection(travelDirection);
        return segment.line() != null ? normalizeDirection(travelDirection) : segment.segmentLabel();
    }

    private static SegmentProjection findProjectionForSegment(List<SegmentProjection> projections, HighwaySegment segment) {
        if (segment == null) return null;
        for (SegmentProjection projection : projections) {
            if (segment.equals(projection.segment())) return projection;
        }
        return null;
    }

    private static List<SegmentProjection> collectSegmentProjections(
        double playerX,
        double playerZ,
        boolean cardinal,
        boolean diagonal,
        boolean ring,
        boolean diamond,
        boolean trueCenterMode
    ) {
        List<SegmentProjection> projections = new ArrayList<>();
        double centerOffset = trueCenterMode ? 0.5 : 0.0;

        if (cardinal) {
            addProjection(projections, new HighwaySegment("Cardinal", 0, "NS", WorkLine.CardinalNS), centerOffset, playerZ, playerX, playerZ);
            addProjection(projections, new HighwaySegment("Cardinal", 0, "EW", WorkLine.CardinalEW), playerX, centerOffset, playerX, playerZ);
        }

        if (diagonal) {
            double[] nwsePoint = closestPointOnLine(playerX, playerZ, 1.0, -1.0, 0.0);
            addProjection(projections, new HighwaySegment("Diagonal", 0, "NWSE", WorkLine.DiagonalNWSE), nwsePoint[0], nwsePoint[1], playerX, playerZ);

            double diagonalOffset = trueCenterMode ? 1.0 : 0.0;
            double[] neswPoint = closestPointOnLine(playerX, playerZ, 1.0, 1.0, diagonalOffset);
            addProjection(projections, new HighwaySegment("Diagonal", 0, "NESW", WorkLine.DiagonalNESW), neswPoint[0], neswPoint[1], playerX, playerZ);
        }

        if (ring) {
            for (int r : RING_ROADS) {
                double left = -r + centerOffset;
                double right = r + centerOffset;
                double bottom = -r + centerOffset;
                double top = r + centerOffset;

                addSegmentProjection(projections, "Ring", r, "N", left, bottom, right, bottom, playerX, playerZ);
                addSegmentProjection(projections, "Ring", r, "S", left, top, right, top, playerX, playerZ);
                addSegmentProjection(projections, "Ring", r, "W", left, bottom, left, top, playerX, playerZ);
                addSegmentProjection(projections, "Ring", r, "E", right, bottom, right, top, playerX, playerZ);
            }
        }

        if (diamond) {
            for (int d : DIAMONDS) {
                double x1 = d + centerOffset;
                double z1 = centerOffset;
                double x2 = centerOffset;
                double z2 = d + centerOffset;
                double x3 = -d + centerOffset;
                double z3 = centerOffset;
                double x4 = centerOffset;
                double z4 = -d + centerOffset;

                addSegmentProjection(projections, "Diamond", d, "SE", x1, z1, x2, z2, playerX, playerZ);
                addSegmentProjection(projections, "Diamond", d, "SW", x2, z2, x3, z3, playerX, playerZ);
                addSegmentProjection(projections, "Diamond", d, "NW", x3, z3, x4, z4, playerX, playerZ);
                addSegmentProjection(projections, "Diamond", d, "NE", x4, z4, x1, z1, playerX, playerZ);
            }
        }

        return projections;
    }

    private static void addProjection(List<SegmentProjection> projections, HighwaySegment segment, double targetX, double targetZ, double playerX, double playerZ) {
        projections.add(new SegmentProjection(segment, targetX, targetZ, Math.hypot(playerX - targetX, playerZ - targetZ)));
    }

    private static void addSegmentProjection(
        List<SegmentProjection> projections,
        String highway,
        int roadValue,
        String segmentLabel,
        double x1,
        double z1,
        double x2,
        double z2,
        double playerX,
        double playerZ
    ) {
        double[] point = closestPointOnSegment(playerX, playerZ, x1, z1, x2, z2);
        addProjection(projections, new HighwaySegment(highway, roadValue, segmentLabel, null), point[0], point[1], playerX, playerZ);
    }

    private static SegmentProjection selectBestSegmentProjection(
        List<SegmentProjection> candidates,
        HighwaySegment trackedSegment,
        String preferredDirection,
        float referenceYaw,
        double ambiguityThreshold
    ) {
        if (candidates == null || candidates.isEmpty()) return null;

        double bestDistance = HUGE_DISTANCE;
        for (SegmentProjection candidate : candidates) {
            if (candidate.distance() < bestDistance) bestDistance = candidate.distance();
        }

        double threshold = Math.max(ambiguityThreshold, 0.0) + 1.0e-9;
        List<SegmentProjection> ambiguous = new ArrayList<>();
        for (SegmentProjection candidate : candidates) {
            if (candidate.distance() <= bestDistance + threshold) ambiguous.add(candidate);
        }
        if (ambiguous.isEmpty()) return null;
        if (ambiguous.size() == 1) return ambiguous.get(0);

        SegmentProjection trackedProjection = findProjectionForSegment(ambiguous, trackedSegment);
        if (trackedProjection != null) return trackedProjection;

        String normalizedPreferredDirection = normalizeDirection(preferredDirection);
        if (!normalizedPreferredDirection.isEmpty()) {
            List<SegmentProjection> compatible = new ArrayList<>();
            for (SegmentProjection candidate : ambiguous) {
                if (isDirectionCompatible(candidate.segment(), normalizedPreferredDirection)) compatible.add(candidate);
            }
            if (!compatible.isEmpty()) {
                return chooseProjectionByYawThenFallback(compatible, referenceYaw, normalizedPreferredDirection);
            }
        }

        SegmentProjection yawPreferred = chooseProjectionByYawThenFallback(ambiguous, referenceYaw, normalizedPreferredDirection);
        if (yawPreferred != null) return yawPreferred;

        return chooseNearestProjection(ambiguous);
    }

    private static SegmentProjection chooseProjectionByYawThenFallback(List<SegmentProjection> candidates, float referenceYaw, String preferredDirection) {
        if (candidates == null || candidates.isEmpty()) return null;
        if (Float.isNaN(referenceYaw)) return chooseNearestProjection(candidates);

        SegmentProjection best = null;
        float bestYawDistance = Float.MAX_VALUE;
        for (SegmentProjection candidate : candidates) {
            String chosenDirection = chooseSegmentTravelDirection(candidate.segment(), referenceYaw, preferredDirection);
            if (chosenDirection.isEmpty()) continue;

            float candidateYawDistance = yawDistance(referenceYaw, yawForWorkingDirection(chosenDirection));
            if (best == null || candidateYawDistance < bestYawDistance - 1.0e-4f ||
                (Math.abs(candidateYawDistance - bestYawDistance) <= 1.0e-4f && compareProjectionTieBreak(candidate, best) < 0)) {
                best = candidate;
                bestYawDistance = candidateYawDistance;
            }
        }

        return best != null ? best : chooseNearestProjection(candidates);
    }

    private static SegmentProjection chooseNearestProjection(List<SegmentProjection> candidates) {
        SegmentProjection best = null;
        for (SegmentProjection candidate : candidates) {
            if (best == null || compareProjectionTieBreak(candidate, best) < 0) best = candidate;
        }
        return best;
    }

    private static int compareProjectionTieBreak(SegmentProjection left, SegmentProjection right) {
        if (left == right) return 0;
        int distanceCompare = Double.compare(left.distance(), right.distance());
        if (distanceCompare != 0) return distanceCompare;
        return Integer.compare(segmentFallbackOrder(left.segment()), segmentFallbackOrder(right.segment()));
    }

    private static int segmentFallbackOrder(HighwaySegment segment) {
        if (segment == null) return Integer.MAX_VALUE;
        if (segment.line() != null) {
            return switch (segment.line()) {
                case CardinalNS -> 0;
                case CardinalEW -> 1;
                case DiagonalNWSE -> 2;
                case DiagonalNESW -> 3;
            };
        }

        int labelOrder = switch (segment.segmentLabel()) {
            case "N", "SE" -> 0;
            case "S", "SW" -> 1;
            case "W", "NW" -> 2;
            case "E", "NE" -> 3;
            default -> 9;
        };

        int roadValueOrder = Math.max(segment.roadValue(), 0) * 10;
        if ("Ring".equals(segment.highway())) return 1000 + roadValueOrder + labelOrder;
        if ("Diamond".equals(segment.highway())) return 2000 + roadValueOrder + labelOrder;
        return 3000 + roadValueOrder + labelOrder;
    }

    private static boolean isDirectionCompatible(HighwaySegment segment, String direction) {
        if (segment == null) return false;
        String normalizedDirection = normalizeDirection(direction);
        if (normalizedDirection.isEmpty()) return false;
        if (segment.line() != null) return isDirectionCompatible(segment.line(), normalizedDirection);

        String[] parallelDirections = parallelDirectionCodesForSegment(segment);
        return parallelDirections != null &&
            (normalizedDirection.equals(parallelDirections[0]) || normalizedDirection.equals(parallelDirections[1]));
    }

    private static String chooseSegmentTravelDirection(HighwaySegment segment, float referenceYaw, String preferredDirection) {
        if (segment == null) return "";

        String[] parallelDirections = parallelDirectionCodesForSegment(segment);
        if (parallelDirections == null) return "";

        String normalizedPreferredDirection = normalizeDirection(preferredDirection);
        boolean preferredMatchesFirst = normalizedPreferredDirection.equals(parallelDirections[0]);
        boolean preferredMatchesSecond = normalizedPreferredDirection.equals(parallelDirections[1]);
        if (preferredMatchesFirst ^ preferredMatchesSecond) return normalizedPreferredDirection;

        if (!Float.isNaN(referenceYaw)) {
            float firstDistance = yawDistance(referenceYaw, yawForWorkingDirection(parallelDirections[0]));
            float secondDistance = yawDistance(referenceYaw, yawForWorkingDirection(parallelDirections[1]));
            if (firstDistance < secondDistance) return parallelDirections[0];
            if (secondDistance < firstDistance) return parallelDirections[1];
        }

        return parallelDirections[0];
    }

    private static String[] parallelDirectionCodesForSegment(HighwaySegment segment) {
        if (segment == null) return null;
        if (segment.line() != null) {
            return switch (segment.line()) {
                case CardinalNS -> new String[] {"N", "S"};
                case CardinalEW -> new String[] {"E", "W"};
                case DiagonalNWSE -> new String[] {"NW", "SE"};
                case DiagonalNESW -> new String[] {"NE", "SW"};
            };
        }

        return switch (segment.highway()) {
            case "Ring" -> switch (segment.segmentLabel()) {
                case "N", "S" -> new String[] {"E", "W"};
                case "E", "W" -> new String[] {"N", "S"};
                default -> null;
            };
            case "Diamond" -> switch (segment.segmentLabel()) {
                case "NE", "SW" -> new String[] {"NW", "SE"};
                case "NW", "SE" -> new String[] {"NE", "SW"};
                default -> null;
            };
            default -> null;
        };
    }

    private RecoveryTarget applyRepairMisalignmentBackstepForGoto(RecoveryTarget target) {
        if (target == null || !repairMisalignments.get()) return target;
        return applyDirectionalBackstepForGoto(target);
    }

    private RecoveryTarget applyForcedStallBackstepForGoto(RecoveryTarget target) {
        if (target == null) return null;
        return applyDirectionalBackstepForGoto(target);
    }

    private RecoveryTarget applyDirectionalBackstepForGoto(RecoveryTarget target) {
        int directionOffsetX = workingDirectionOffsetX(target.travelDirection());
        int directionOffsetZ = workingDirectionOffsetZ(target.travelDirection());
        if (directionOffsetX == 0 && directionOffsetZ == 0) return target;

        double correctedTargetX = target.targetX() - directionOffsetX * 2.0;
        double correctedTargetZ = target.targetZ() - directionOffsetZ * 2.0;
        int correctedGoalX = floorToBlock(correctedTargetX);
        int correctedGoalZ = floorToBlock(correctedTargetZ);
        double correctedDistance = target.distance();
        if (mc != null && mc.player != null) {
            correctedDistance = Math.hypot(mc.player.getX() - correctedTargetX, mc.player.getZ() - correctedTargetZ);
        }

        return new RecoveryTarget(
            target.highway(),
            target.direction(),
            target.travelDirection(),
            correctedTargetX,
            correctedTargetZ,
            correctedGoalX,
            target.goalY(),
            correctedGoalZ,
            target.yaw(),
            correctedDistance,
            target.segment()
        );
    }

    private RecoveryTarget createLocalStallEscapeTarget(HighwayBuilderTHM builder, RecoveryTarget inferredTarget) {
        if (mc == null || mc.player == null || builder == null) return null;

        HorizontalDirection direction = builder.getWorkingDirection();
        if (direction == null) return null;

        double targetX = mc.player.getX() - direction.offsetX * 2.0;
        double targetZ = mc.player.getZ() - direction.offsetZ * 2.0;
        String travelDirection = normalizeDirection(directionCode(direction));
        if (travelDirection.isEmpty() && inferredTarget != null) travelDirection = inferredTarget.travelDirection();
        if (travelDirection.isEmpty()) travelDirection = "Unknown";
        String directionLabel = inferredTarget != null ? inferredTarget.direction() : travelDirection;

        return new RecoveryTarget(
            inferredTarget != null ? inferredTarget.highway() : "Local",
            directionLabel,
            travelDirection,
            targetX,
            targetZ,
            floorToBlock(targetX),
            floorToBlock(mc.player.getY()),
            floorToBlock(targetZ),
            direction.yaw,
            Math.hypot(mc.player.getX() - targetX, mc.player.getZ() - targetZ),
            inferredTarget != null ? inferredTarget.segment() : null
        );
    }

    private boolean isSafeLocalStallEscapeTarget(RecoveryTarget target) {
        if (mc == null || mc.world == null || mc.player == null || target == null) return false;

        BlockPos feetPos = BlockPos.ofFloored(target.targetX(), mc.player.getY(), target.targetZ());
        BlockPos headPos = feetPos.up();
        BlockPos floorPos = feetPos.down();
        BlockState feetState = mc.world.getBlockState(feetPos);
        BlockState headState = mc.world.getBlockState(headPos);
        BlockState floorState = mc.world.getBlockState(floorPos);

        return isClearLocalStallEscapeSpace(feetState, feetPos)
            && isClearLocalStallEscapeSpace(headState, headPos)
            && floorState.isSolidBlock(mc.world, floorPos);
    }

    private boolean isClearLocalStallEscapeSpace(BlockState state, BlockPos pos) {
        if (mc == null || mc.world == null || state == null || pos == null) return false;
        return (state.isAir() || state.isReplaceable()) && state.getCollisionShape(mc.world, pos).isEmpty();
    }

    private void triggerMonitorSafeBuilderHardFail(HighwayBuilderTHM builder, String message, Object... args) {
        HighwayBuilderTHM failureBuilder = builder != null ? builder : recoveryBuilder;
        if (failureBuilder != null) failureBuilder.hardFailForMonitorRecovery(message, args);
        else warning(message, args);
        consumeNonRestartHardFailSignal();
        nonRestartHardFailArmed = true;
        handleDetectedNonRestartHardFail("monitor-recovery");
    }

    private static boolean isPavingMode(HighwayBuilderTHM builder) {
        return !builder.blocksToPlace.get().contains(Blocks.NETHERRACK);
    }

    private static double recoveryYDelta(double playerY, int goalY) {
        return goalY - playerY;
    }

    private void takeRestartScreenshot() {
        if (mc == null || mc.getFramebuffer() == null) return;
        ScreenshotRecorder.saveScreenshot(mc.runDirectory, mc.getFramebuffer(), message -> info(message.getString()));
    }

    private void beginPostRejoinDirectionGate(long cycleId, String contextTag) {
        if (!isActive()) return;
        if (cycleId <= 0L || cycleId != activeReconnectCycleId) return;

        postRejoinDirectionGateActive = true;
        postRejoinDirectionRetryCount = 0;
        postRejoinDirectionNextAttemptAtMs = 0L;
        postRejoinDirectionBlockReason = "waiting";
        postRejoinDirectionBlockSummary = contextTag == null ? "" : contextTag;
        postRejoinBlockedScreenshotTaken = false;
        postRejoinTerminalScreenshotTaken = false;
        postRejoinLastCompleteProbeWinner = null;
    }

    private void maybeRunPostRejoinDirectionGate() {
        if (!postRejoinDirectionGateActive) return;
        if (!isActive()) {
            clearPostRejoinDirectionGateState();
            return;
        }
        if (activeReconnectCycleId <= 0L || mc == null || mc.player == null || mc.world == null) return;
        if (postRejoinDirectionNextAttemptAtMs > System.currentTimeMillis()) return;
        if (!isHighwayRecoveryAllowedOnCurrentServer()) {
            postRejoinDirectionBlockReason = "waiting-main-server";
            postRejoinDirectionBlockSummary = "committedState=" + getCommittedServerState().name();
            postRejoinDirectionNextAttemptAtMs = System.currentTimeMillis() + POST_REJOIN_DIRECTION_RETRY_DELAY_MS;
            return;
        }

        PostRejoinDirectionResult result = determineConclusivePostRejoinWorkingDirection();
        if (result.conclusive()) {
            if (applyDirectionAndEnableHighwayBuilder(result.direction())) finishSuccessfulReconnectResume();
            return;
        }

        postRejoinDirectionRetryCount++;
        postRejoinDirectionBlockReason = result.reason();
        postRejoinDirectionBlockSummary = result.summary();
        postRejoinDirectionNextAttemptAtMs = System.currentTimeMillis() + POST_REJOIN_DIRECTION_RETRY_DELAY_MS;

        if (restartScreenshotsEnabled() && postRejoinDirectionRetryCount >= 3 && !postRejoinBlockedScreenshotTaken) {
            postRejoinBlockedScreenshotTaken = true;
            takeRestartScreenshot();
        }

        if (postRejoinDirectionRetryCount >= POST_REJOIN_DIRECTION_RETRY_LIMIT) {
            enterReconnectSafetyStop("Reconnect resume stopped: " + result.reason());
        }
    }

    private boolean applyDirectionAndEnableHighwayBuilder(HorizontalDirection workingDirection) {
        if (!isHighwayRecoveryAllowedOnCurrentServer()) return false;

        applyPostRejoinYaw(workingDirection);
        info("Post-rejoin direction selected: %s.", workingDirection.name);

        HighwayBuilderTHM builder = Modules.get().get(HighwayBuilderTHM.class);
        if (builder == null) {
            enterReconnectSafetyStop("THM HighwayBuilder module not found, cannot resume.");
            return false;
        }

        HighwayBuilderTHM.ReconnectResumeResult resumeResult = builder.resumeFromReconnect(workingDirection, activeReconnectCycleId);
        if (!resumeResult.success()) {
            enterReconnectSafetyStop("HighwayBuilder refused reconnect resume: " + resumeResult.reason());
            return false;
        }

        ModuleManager manager = getModuleManager();
        if (manager != null && manager.isActive()) {
            ModuleManager.ReconnectRestoreOutcome restoreOutcome = manager.restoreReconnectManagedModules(activeReconnectCycleId);
            if (restoreOutcome == ModuleManager.ReconnectRestoreOutcome.CriticalFailure) {
                enterReconnectSafetyStop("Critical managed module failed to restore after reconnect.");
                return false;
            }
        }

        if (!builder.completeReconnectResumeAfterManagedModules(activeReconnectCycleId)) {
            enterReconnectSafetyStop("HighwayBuilder refused reconnect finalization after managed module restore.");
            return false;
        }

        info("Resumed THM HighwayBuilder after post-rejoin checks.");
        return true;
    }

    private void finishSuccessfulReconnectResume() {
        maybeTakeDeferredRestartScreenshotAfterReconnect("main-server-ready");
        boolean rearmNormalReconnect = rearmNormalReconnectAfterForwardReconnectResume;
        clearRestartAutomationState("post-main-server finalization complete", true, true);
        resetRubberbandGhostblockWatch();

        if (rearmNormalReconnect && reconnectAutomationEnabled() && isActive()) {
            long cycleId = armReconnectCycle("forward-recovery-resume-rearm", false);
            reconnectOwner = ReconnectOwner.HighwayBuilder;
            info("Re-armed normal AutoReconnect cycle %d after forward reconnect recovery resume.", cycleId);
        }
    }

    private PostRejoinDirectionResult determineConclusivePostRejoinWorkingDirection() {
        if (mc.player == null || mc.world == null) return PostRejoinDirectionResult.blocked("player-or-world-missing", "");

        HighwayBuilderTHM builder = Modules.get().get(HighwayBuilderTHM.class);
        HorizontalDirection cachedDirection = builder == null ? null : builder.getCachedWorkingDirectionForMonitorReconnect(activeReconnectCycleId);
        String cachedDirectionCode = normalizeDirection(directionCode(cachedDirection));
        SegmentProjection reconnectSegment = resolveReconnectLineFromCurrentPosition(cachedDirectionCode);
        if (reconnectSegment == null) {
            postRejoinLastCompleteProbeWinner = null;
            return PostRejoinDirectionResult.blocked("axis-unresolved", "segment=unresolved");
        }

        String segmentSummary = String.format(Locale.ROOT, "%s %s dist=%.2f",
            reconnectSegment.segment().highway(),
            reconnectSegment.segment().segmentLabel(),
            reconnectSegment.distance()
        );

        if (cachedDirection != null && isDirectionCompatible(reconnectSegment.segment(), cachedDirectionCode)) {
            postRejoinLastCompleteProbeWinner = cachedDirection;
            return PostRejoinDirectionResult.success(cachedDirection, "cached-direction=" + cachedDirection.name + " " + segmentSummary);
        }

        HorizontalDirection[] axisDirections = resolvePostRejoinAxisDirections(reconnectSegment.segment());
        if (axisDirections == null) {
            postRejoinLastCompleteProbeWinner = null;
            return PostRejoinDirectionResult.blocked("axis-unresolved", segmentSummary);
        }

        HorizontalDirection dirA = axisDirections[0];
        HorizontalDirection dirB = axisDirections[1];
        boolean pavingSelected = builder != null && isPavingMode(builder);
        AxisProbeResult probe = pavingSelected
            ? probeAxis(dirA, dirB, 119, true)
            : probeAxis(dirA, dirB, 122, false);

        String summary = String.format(Locale.ROOT, "%s %s=%d %s=%d",
            segmentSummary,
            probe.dirA().name,
            probe.dirAScore(),
            probe.dirB().name,
            probe.dirBScore()
        );

        if (!probe.allSamplesLoaded()) {
            postRejoinLastCompleteProbeWinner = null;
            return PostRejoinDirectionResult.blocked("probe-unloaded", summary);
        }

        if (probe.strongWinner() && probe.selectedDirection() != null) {
            if (postRejoinLastCompleteProbeWinner == probe.selectedDirection()) {
                postRejoinLastCompleteProbeWinner = probe.selectedDirection();
                return PostRejoinDirectionResult.success(probe.selectedDirection(), summary);
            }

            postRejoinLastCompleteProbeWinner = probe.selectedDirection();
            return PostRejoinDirectionResult.blocked("probe-ambiguous", summary);
        }

        String yawPreferredCode = normalizeDirection(directionCode(postRejoinLastCompleteProbeWinner));
        HorizontalDirection yawPreferred = parseDirectionCode(chooseSegmentTravelDirection(
            reconnectSegment.segment(),
            mc.player.getYaw(),
            yawPreferredCode
        ));
        if (yawPreferred != null) {
            postRejoinLastCompleteProbeWinner = yawPreferred;
            return PostRejoinDirectionResult.success(yawPreferred, summary + " yaw-fallback=" + yawPreferred.name);
        }

        postRejoinLastCompleteProbeWinner = null;
        return PostRejoinDirectionResult.blocked("probe-ambiguous", summary);
    }

    private HorizontalDirection[] resolvePostRejoinAxisDirections(HighwaySegment segment) {
        return parallelDirectionsForSegment(segment);
    }

    private SegmentProjection resolveReconnectLineFromCurrentPosition(String preferredDirection) {
        if (mc == null || mc.player == null) return null;

        String resolvedPreferredDirection = normalizeDirection(preferredDirection);
        if (resolvedPreferredDirection.isEmpty()) {
            resolvedPreferredDirection = normalizeDirection(directionCode(postRejoinLastCompleteProbeWinner));
        }
        if (resolvedPreferredDirection.isEmpty()) {
            resolvedPreferredDirection = normalizeDirection(trackedDirection);
        }

        SegmentProjection resolved = selectBestSegmentProjection(
            collectSegmentProjections(
                mc.player.getX(),
                mc.player.getZ(),
                true,
                true,
                true,
                true,
                trueCenterMode.get()
            ),
            trackedSegment,
            resolvedPreferredDirection,
            mc.player.getYaw(),
            RECONNECT_LINE_AMBIGUITY_THRESHOLD
        );
        if (resolved == null || resolved.distance() > RECONNECT_LINE_MAX_DISTANCE) return null;
        return resolved;
    }

    private static HorizontalDirection[] parallelDirectionsForSegment(HighwaySegment segment) {
        String[] directionCodes = parallelDirectionCodesForSegment(segment);
        if (directionCodes == null || directionCodes.length != 2) return null;

        HorizontalDirection first = parseDirectionCode(directionCodes[0]);
        HorizontalDirection second = parseDirectionCode(directionCodes[1]);
        if (first == null || second == null) return null;
        return new HorizontalDirection[] {first, second};
    }

    private AxisProbeResult probeAxis(HorizontalDirection dirA, HorizontalDirection dirB, int y, boolean obsidianProbe) {
        if (mc.player == null || mc.world == null) {
            return new AxisProbeResult(false, false, null, dirA, 0, dirB, 0);
        }

        int totalA = 0;
        int totalB = 0;
        int nearA = 0;
        int nearB = 0;

        for (int distance = 1; distance <= POST_REJOIN_AXIS_PROBE_DISTANCE; distance++) {
            BlockPos probeA = BlockPos.ofFloored(
                mc.player.getX() + (dirA.offsetX * distance),
                y,
                mc.player.getZ() + (dirA.offsetZ * distance)
            );
            BlockPos probeB = BlockPos.ofFloored(
                mc.player.getX() + (dirB.offsetX * distance),
                y,
                mc.player.getZ() + (dirB.offsetZ * distance)
            );

            if (!isReconnectProbeChunkLoaded(probeA) || !isReconnectProbeChunkLoaded(probeB)) {
                return new AxisProbeResult(false, false, null, dirA, totalA, dirB, totalB);
            }

            boolean matchA = obsidianProbe
                ? mc.world.getBlockState(probeA).getBlock() == Blocks.OBSIDIAN
                : mc.world.getBlockState(probeA).isAir();
            boolean matchB = obsidianProbe
                ? mc.world.getBlockState(probeB).getBlock() == Blocks.OBSIDIAN
                : mc.world.getBlockState(probeB).isAir();

            if (matchA) totalA++;
            if (matchB) totalB++;
            if (distance <= POST_REJOIN_AXIS_NEAR_PROBE_DISTANCE) {
                if (matchA) nearA++;
                if (matchB) nearB++;
            }
        }

        int totalMargin = Math.abs(totalA - totalB);
        int nearMargin = Math.abs(nearA - nearB);
        if (totalMargin < 2 || nearMargin < 1 || totalA == totalB) {
            return new AxisProbeResult(true, false, null, dirA, totalA, dirB, totalB);
        }

        HorizontalDirection selected = totalA > totalB ? dirB : dirA;
        return new AxisProbeResult(true, true, selected, dirA, totalA, dirB, totalB);
    }

    private boolean isReconnectProbeChunkLoaded(BlockPos probe) {
        if (mc == null || mc.world == null) return false;
        return mc.world.getChunkManager().getChunk(probe.getX() >> 4, probe.getZ() >> 4, ChunkStatus.FULL, false) != null;
    }

    private void enterReconnectSafetyStop(String reason) {
        HighwayBuilderTHM builder = Modules.get().get(HighwayBuilderTHM.class);
        long cycleId = activeReconnectCycleId;
        HighwayBuilderTHM.StatsDisconnectPresentation disconnectPresentation = builder == null
            ? new HighwayBuilderTHM.StatsDisconnectPresentation(Text.of("THMHwyMonitor Safety Stop: " + reason), null)
            : builder.getReconnectSafetyStopPresentation(reason, cycleId);

        if (cycleId > 0L) schedulePendingAbortRestore(cycleId, "reconnect-safety-stop:" + reason);
        if (builder != null) {
            builder.disableForReconnectSafetyStop();
        }
        clearHighwayBuilderReconnectModuleRestoreSnapshot("reconnect-safety-stop");

        if (restartScreenshotsEnabled() && !postRejoinTerminalScreenshotTaken) {
            postRejoinTerminalScreenshotTaken = true;
            takeRestartScreenshot();
        }

        intentionalSafetyDisconnectArmed = true;
        disableMonitorAfterIntentionalSafetyDisconnect = true;
        clearRestartAutomationStateForTerminalStop("reconnect-safety-stop");
        tryPendingAbortRestore("reconnect-safety-stop");

        if (hasLiveServerConnection()) {
            mc.getNetworkHandler().getConnection().disconnect(disconnectPresentation.text());
            if (builder != null) {
                builder.scheduleRetiredStatsDisconnectScreenshot(
                    disconnectPresentation.retiredSessionId(),
                    "reconnect-safety-stop"
                );
            }
            return;
        }

        intentionalSafetyDisconnectArmed = false;
        disableMonitorAfterIntentionalSafetyDisconnect = false;

        if (mc != null) {
            mc.setScreen(new DisconnectedScreen(
                new TitleScreen(),
                Text.of("THMHwyMonitor Safety Stop"),
                disconnectPresentation.text()
            ));
            if (builder != null) {
                builder.scheduleRetiredStatsDisconnectScreenshot(
                    disconnectPresentation.retiredSessionId(),
                    "reconnect-safety-stop-no-live-connection"
                );
            }
        }

        if (isActive()) toggle();
    }

    private void applyPostRejoinYaw(HorizontalDirection direction) {
        if (mc.player == null || !isHighwayRecoveryAllowedOnCurrentServer()) return;

        float pitch = mc.player.getPitch();
        mc.player.setYaw(direction.yaw);
        if (BaritoneUtils.IS_AVAILABLE) {
            IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
            if (baritone != null) baritone.getLookBehavior().updateTarget(new Rotation(direction.yaw, pitch), false);
        }
    }

    private void restorePostJoinModuleStatesIfNeeded() {
        if (activeReconnectCycleId > 0L) return;
        if (!postJoinModuleStateCaptured) return;
        if (!isSuccessfullyConnectedToServer()) return;

        Timer timer = Modules.get().get(Timer.class);
        Speed speed = Modules.get().get(Speed.class);
        internalTimerSpeedToggleInProgress = true;
        try {
            if (timerWasActiveBeforePostJoin && timer != null && !timer.isActive()) {
                timer.toggle();
                info("Restored Timer state after post-join routine.");
            }

            if (speedWasActiveBeforePostJoin && speed != null && !speed.isActive()) {
                speed.toggle();
                info("Restored Speed state after post-join routine.");
            }
        } finally {
            internalTimerSpeedToggleInProgress = false;
        }

        boolean timerRestored = !timerWasActiveBeforePostJoin || (timer != null && timer.isActive());
        boolean speedRestored = !speedWasActiveBeforePostJoin || (speed != null && speed.isActive());
        if (!timerRestored || !speedRestored) {
            return;
        }

        postJoinModuleStateCaptured = false;
        timerWasActiveBeforePostJoin = false;
        speedWasActiveBeforePostJoin = false;
        restartModuleStateSnapshotTaken = false;
    }

    private boolean isSuccessfullyConnectedToServer() {
        return hasLiveServerConnection() && !(mc.currentScreen instanceof DisconnectedScreen);
    }

    private static String inferDirectionForLine(WorkLine line, float yaw) {
        double radians = Math.toRadians(yaw);
        double vx = -Math.sin(radians);
        double vz = Math.cos(radians);

        return switch (line) {
            case CardinalNS -> pickByDot(vx, vz, "N", 0.0, -1.0, "S", 0.0, 1.0);
            case CardinalEW -> pickByDot(vx, vz, "E", 1.0, 0.0, "W", -1.0, 0.0);
            case DiagonalNWSE -> pickByDot(vx, vz, "NW", -1.0, -1.0, "SE", 1.0, 1.0);
            case DiagonalNESW -> pickByDot(vx, vz, "NE", 1.0, -1.0, "SW", -1.0, 1.0);
        };
    }

    private static String pickByDot(double vx, double vz, String aName, double ax, double az, String bName, double bx, double bz) {
        double dotA = (vx * ax) + (vz * az);
        double dotB = (vx * bx) + (vz * bz);
        return dotA >= dotB ? aName : bName;
    }

    private static boolean isDirectionCompatible(WorkLine line, String direction) {
        if (direction.isEmpty()) return false;
        return switch (line) {
            case CardinalNS -> "N".equals(direction) || "S".equals(direction);
            case CardinalEW -> "E".equals(direction) || "W".equals(direction);
            case DiagonalNWSE -> "NW".equals(direction) || "SE".equals(direction);
            case DiagonalNESW -> "NE".equals(direction) || "SW".equals(direction);
        };
    }

    private static String normalizeDirection(String direction) {
        if (direction == null || direction.isEmpty()) return "";
        String value = direction;
        int split = value.indexOf("->");
        if (split >= 0) value = value.substring(0, split);
        value = value.trim();

        return switch (value) {
            case "N", "North" -> "N";
            case "NE", "NorthEast" -> "NE";
            case "E", "East" -> "E";
            case "SE", "SouthEast" -> "SE";
            case "S", "South" -> "S";
            case "SW", "SouthWest" -> "SW";
            case "W", "West" -> "W";
            case "NW", "NorthWest" -> "NW";
            default -> "";
        };
    }

    private static HorizontalDirection parseDirectionCode(String direction) {
        return switch (normalizeDirection(direction)) {
            case "N" -> HorizontalDirection.North;
            case "NE" -> HorizontalDirection.NorthEast;
            case "E" -> HorizontalDirection.East;
            case "SE" -> HorizontalDirection.SouthEast;
            case "S" -> HorizontalDirection.South;
            case "SW" -> HorizontalDirection.SouthWest;
            case "W" -> HorizontalDirection.West;
            case "NW" -> HorizontalDirection.NorthWest;
            default -> null;
        };
    }

    private static String directionCode(HorizontalDirection direction) {
        if (direction == null) return "";
        return switch (direction) {
            case North -> "N";
            case NorthEast -> "NE";
            case East -> "E";
            case SouthEast -> "SE";
            case South -> "S";
            case SouthWest -> "SW";
            case West -> "W";
            case NorthWest -> "NW";
        };
    }

    private static int workingDirectionOffsetX(String direction) {
        return switch (normalizeDirection(direction)) {
            case "E", "NE", "SE" -> 1;
            case "W", "NW", "SW" -> -1;
            default -> 0;
        };
    }

    private static int workingDirectionOffsetZ(String direction) {
        return switch (normalizeDirection(direction)) {
            case "S", "SE", "SW" -> 1;
            case "N", "NE", "NW" -> -1;
            default -> 0;
        };
    }

    private static int floorToBlock(double value) {
        return (int) Math.floor(value);
    }

    private double horizontalDistanceToGoalCenter(int goalX, int goalZ) {
        if (mc.player == null) return HUGE_DISTANCE;
        double dx = mc.player.getX() - (goalX + 0.5);
        double dz = mc.player.getZ() - (goalZ + 0.5);
        return Math.hypot(dx, dz);
    }

    private void applyWorkingYaw() {
        if (pendingCorrectionTarget == null || mc.player == null || recoveryBuilder == null || !isHighwayRecoveryAllowedOnCurrentServer()) return;
        float yaw = pendingCorrectionTarget.yaw();
        mc.player.setYaw(yaw);
        mc.player.setPitch(20.0f);

        if (!BaritoneUtils.IS_AVAILABLE) return;
        IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
        if (baritone != null) {
            baritone.getLookBehavior().updateTarget(new Rotation(yaw, 20.0f), false);
        }
    }

    private void applyStrictAlignmentSnap() {
        if (pendingCorrectionTarget == null || mc.player == null) return;

        // Final exact snap to computed aligned line position so true-center is strict.
        mc.player.setVelocity(0.0, mc.player.getVelocity().y, 0.0);
        mc.player.setPosition(pendingCorrectionTarget.targetX(), mc.player.getY(), pendingCorrectionTarget.targetZ());
    }

    private static double distanceToLine(double x, double z, double a, double b, double c) {
        // Distance from point (x, z) to line ax + bz = c.
        return Math.abs(a * x + b * z - c) / Math.sqrt(a * a + b * b);
    }

    private static double[] closestPointOnLine(double x, double z, double a, double b, double c) {
        double denom = a * a + b * b;
        if (denom == 0.0) return new double[] {x, z};

        double t = (a * x + b * z - c) / denom;
        return new double[] {x - a * t, z - b * t};
    }

    private static double distancePointToSegment(double px, double pz, double x1, double z1, double x2, double z2) {
        double dx = x2 - x1;
        double dz = z2 - z1;

        if (dx == 0.0 && dz == 0.0) {
            return Math.hypot(px - x1, pz - z1);
        }

        double t = ((px - x1) * dx + (pz - z1) * dz) / (dx * dx + dz * dz);
        t = Math.max(0.0, Math.min(1.0, t));

        double cx = x1 + t * dx;
        double cz = z1 + t * dz;
        return Math.hypot(px - cx, pz - cz);
    }

    private static double[] closestPointOnSegment(double px, double pz, double x1, double z1, double x2, double z2) {
        double dx = x2 - x1;
        double dz = z2 - z1;

        if (dx == 0.0 && dz == 0.0) {
            return new double[] {x1, z1};
        }

        double t = ((px - x1) * dx + (pz - z1) * dz) / (dx * dx + dz * dz);
        t = Math.max(0.0, Math.min(1.0, t));
        return new double[] {x1 + t * dx, z1 + t * dz};
    }

    private static String resolveWorkingDirection(double playerX, double playerZ, double centerOffset) {
        boolean xAxis = Math.abs(playerZ - centerOffset) <= DIRECTION_RESOLUTION_TOLERANCE;
        boolean zAxis = Math.abs(playerX - centerOffset) <= DIRECTION_RESOLUTION_TOLERANCE;

        String northSouth = northSouthDirection(playerZ, centerOffset);
        String eastWest = eastWestDirection(playerX, centerOffset);

        if (xAxis && ("North".equals(northSouth) || "South".equals(northSouth))) return northSouth;
        if (zAxis && ("East".equals(eastWest) || "West".equals(eastWest))) return eastWest;

        if (!northSouth.isEmpty() && !eastWest.isEmpty()) return northSouth + eastWest;
        if (!northSouth.isEmpty()) return northSouth;
        if (!eastWest.isEmpty()) return eastWest;

        return "Center";
    }

    private static String resolveDirectionForAlignmentResult(WorkLine line, double playerX, double playerZ, float playerYaw, double centerOffset) {
        if (!Float.isNaN(playerYaw)) return inferDirectionForLine(line, playerYaw);
        return resolveWorkingDirection(playerX, playerZ, centerOffset);
    }

    private static String northSouthDirection(double playerZ, double centerOffset) {
        if (playerZ >= centerOffset + DIRECTION_RESOLUTION_TOLERANCE) return "South";
        if (playerZ <= centerOffset - DIRECTION_RESOLUTION_TOLERANCE) return "North";
        return "";
    }

    private static String eastWestDirection(double playerX, double centerOffset) {
        if (playerX >= centerOffset + DIRECTION_RESOLUTION_TOLERANCE) return "East";
        if (playerX <= centerOffset - DIRECTION_RESOLUTION_TOLERANCE) return "West";
        return "";
    }

    private static String yawToDirection(float yaw) {
        if (Float.isNaN(yaw)) return "Unknown";

        double wrapped = yaw % 360.0;
        if (wrapped < 0.0) wrapped += 360.0;

        int index = (int) Math.floor((wrapped + 22.5) / 45.0) % 8;
        return switch (index) {
            case 0 -> "S";
            case 1 -> "SW";
            case 2 -> "W";
            case 3 -> "NW";
            case 4 -> "N";
            case 5 -> "NE";
            case 6 -> "E";
            case 7 -> "SE";
            default -> "Unknown";
        };
    }

    private static float directionToYaw(String direction) {
        String value = direction;
        int split = value.indexOf("->");
        if (split >= 0) value = value.substring(0, split);

        return switch (value) {
            case "S", "South" -> 0.0f;
            case "SW", "SouthWest" -> 45.0f;
            case "W", "West" -> 90.0f;
            case "NW", "NorthWest" -> 135.0f;
            case "N", "North" -> 180.0f;
            case "NE", "NorthEast" -> -135.0f;
            case "E", "East" -> -90.0f;
            case "SE", "SouthEast" -> -45.0f;
            default -> 0.0f;
        };
    }

    private static float yawDistance(float fromYaw, float toYaw) {
        return Math.abs(wrapYaw(toYaw - fromYaw));
    }

    private static float wrapYaw(float yaw) {
        float wrapped = yaw % 360.0f;
        if (wrapped >= 180.0f) wrapped -= 360.0f;
        if (wrapped < -180.0f) wrapped += 360.0f;
        return wrapped;
    }

    private static float yawForWorkingDirection(String workingDirection) {
        return switch (workingDirection) {
            case "N", "North" -> 180.0f;
            case "NE", "NorthEast" -> -135.0f;
            case "E", "East" -> -90.0f;
            case "SE", "SouthEast" -> -45.0f;
            case "S", "South" -> 0.0f;
            case "SW", "SouthWest" -> 45.0f;
            case "W", "West" -> 90.0f;
            case "NW", "NorthWest" -> 135.0f;
            default -> directionToYaw(workingDirection);
        };
    }

    public record AlignmentResult(boolean aligned, String highway, String direction, double distance) {
        public static AlignmentResult notAligned() {
            return new AlignmentResult(false, "None", "None", HUGE_DISTANCE);
        }

        public String label() {
            if (!aligned) return "Not aligned";
            return highway + " " + direction;
        }
    }

    private record RecoveryTarget(
        String highway,
        String direction,
        String travelDirection,
        double targetX,
        double targetZ,
        int goalX,
        int goalY,
        int goalZ,
        float yaw,
        double distance,
        HighwaySegment segment
    ) {}

    private enum RecoveryPhase {
        None,
        WaitBeforeCorrection,
        BaritoneStopping,
        BaritoneWalking,
        WaitBeforeYaw,
        WaitBeforeResume
    }

    private enum RecoveryCause {
        None,
        Misalignment,
        ForwardStall,
        CenterStall
    }

    private enum StallWatchMode {
        None,
        Forward,
        Center
    }

    private enum GhostblockReconnectTrigger {
        None,
        Rubberband,
        PacketDesync,
        LongNoProgress
    }

    private enum WorkLine {
        CardinalNS,
        CardinalEW,
        DiagonalNWSE,
        DiagonalNESW
    }
}

