package xyz.thm.addon.modules;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.pathing.goals.GoalBlock;
import baritone.api.utils.Rotation;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.game.GameJoinedEvent;
import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.events.meteor.ActiveModulesChangedEvent;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.pathing.BaritoneUtils;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.movement.speed.Speed;
import meteordevelopment.meteorclient.systems.modules.world.Timer;
import meteordevelopment.meteorclient.utils.misc.HorizontalDirection;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Blocks;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.DisconnectedScreen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.ChunkStatus;
import xyz.thm.addon.THMAddon;
import xyz.thm.addon.utils.ServerReconnectService;
import xyz.thm.addon.utils.ServerStatusHandler;
import xyz.thm.addon.utils.ServerStatusHandler.ServerState;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

public class THMHwyMonitor extends Module {
    private static final double ALIGN_TOLERANCE = 0.4;
    private static final double HUGE_DISTANCE = 1.0e30;
    private static final int RECOVERY_DELAY_TICKS = 40;
    private static final int YAW_SET_DELAY_TICKS = 10;
    private static final int BARITONE_PATH_STARTUP_TICKS = 10;
    private static final int BARITONE_PATH_TIMEOUT_TICKS = 20 * 20;
    private static final long ALIGNMENT_GATE_TIMEOUT_MS = 10_000L;
    private static final int POST_REJOIN_AXIS_PROBE_DISTANCE = 20;
    private static final int POST_REJOIN_AXIS_NEAR_PROBE_DISTANCE = 8;
    private static final double RECONNECT_LINE_AMBIGUITY_THRESHOLD = 0.05;
    private static final double RECONNECT_LINE_MAX_DISTANCE = 6.0;
    private static final long POST_REJOIN_DIRECTION_RETRY_DELAY_MS = 1_000L;
    private static final int POST_REJOIN_DIRECTION_RETRY_LIMIT = 30;
    private static final int RESTART_SCREENSHOT_DELAY_MS = 2000;
    private static final int RESTART_BUILDER_DISABLE_GRACE_MS = 3000;
    private static final long MAIN_SERVER_RESUME_DELAY_MS = 6_000L;
    private static final int DISCONNECT_SCREEN_EVIDENCE_TIMEOUT_MS = 3000;
    private static final String RECONNECT_RESUME_LISTENER_KEY = "thm-hwymonitor-resume";
    private static final String RECONNECT_FAILURE_LISTENER_KEY = "thm-hwymonitor-failure";
    // IMPORTANT: Any caller that arms reconnect must store cycleId and ignore stale resume/failure callbacks.
    private static final String RESTART_DETECTED_MARKER = "server restart detected";
    private static final String RESTOCK_FAILURE_MARKER = "unable to perform restock";
    private static final String THM_HIGHWAYBUILDER_TAG_A = "thm highwaybuilder";
    private static final String THM_HIGHWAYBUILDER_TAG_B = "thm-highwaybuilder";
    private static final String AUTO_LOG_TAG = "[autolog]";
    private static final long RESTART_EVIDENCE_TTL_MS = 20_000L;
    private static final AtomicBoolean NON_RESTART_HARD_FAIL_SIGNAL = new AtomicBoolean(false);
    private static final AtomicBoolean RESTART_HARD_FAIL_SIGNAL = new AtomicBoolean(false);

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
        .description("upon alignment recovery, it will travel backwards 2 spaces to fix possible misaligned paving/digging")
        .defaultValue(false)
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

    private final Setting<Boolean> autoScreenshotOnRestartDetection = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-screenshot-on-restart-detection")
        .description("Takes a screenshot automatically when the restart-detected disconnect screen appears.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> autoReconnect = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-reconnect")
        .description("Handles Automatically Reconnecting on Disconnects, and Restarting HighwayBuilderTHM")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> restartRejoinDelayMinutes = sgGeneral.add(new IntSetting.Builder()
        .name("restart-rejoin-delay-minutes")
        .description("Delay in minutes applied to Meteor AutoReconnect.")
        .defaultValue(15)
        .range(1, 240)
        .sliderRange(1, 60)
        .build()
    );

    private int ticksSinceCheck;
    private int cooldownTicks;
    private HighwayBuilderTHM recoveryBuilder;
    private RecoveryTarget pendingCorrectionTarget;
    private int recoveryTicks;
    private int baritoneStartupTicks;
    private int baritoneTimeoutTicks;
    private RecoveryPhase recoveryPhase = RecoveryPhase.None;
    private boolean recoveryModulesPaused;
    private final List<Module> recoveryPausedModules = new ArrayList<>();
    private WorkLine trackedLine;
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
    private boolean internalTimerSpeedToggleInProgress;
    private static Field disconnectedScreenReasonField;
    private static boolean disconnectedScreenReasonFieldResolved;
    private CompletableFuture<ServerState> pendingAlignmentGateFuture;
    private long pendingAlignmentGateAttemptId;
    private long nextAlignmentGateAttemptId = 1L;
    private long activeReconnectCycleId;
    private long restartEvidenceGateCycleId;
    private boolean delayedMainServerResumePending;
    private long delayedMainServerResumeCycleId;
    private long delayedMainServerResumeAtMs;
    private String delayedMainServerResumeContext = "";
    private boolean restartRecoveryActive;
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
    private volatile boolean restartBuilderDisableGraceScheduled;
    private long restartBuilderDisableGraceId;
    private long nextRestartBuilderDisableGraceId = 1L;
    private boolean previousAutoReconnectToggleState;

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

    private record ReconnectLineResolution(
        WorkLine line,
        double distance
    ) {}

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
        recoveryPhase = RecoveryPhase.None;
        recoveryModulesPaused = false;
        recoveryPausedModules.clear();
        trackedLine = null;
        trackedDirection = "";
        recoveryYawBeforeMove = Float.NaN;
        clearPendingAlignmentGateRequest();
        clearPostRejoinDirectionGateState();
        resetReconnectAutomationState(true);
        registerReconnectServiceListeners();
        wasConnectedLastTick = isSuccessfullyConnectedToServer();
        if (reconnectAutomationEnabled()) refreshTimerSpeedSnapshotFromCurrentState("activate");
        previousAutoReconnectToggleState = autoReconnect.get();
        if (autoReconnect.get()) {
            armReconnectCycle("onActivate", false);
        }
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
        recoveryPhase = RecoveryPhase.None;
        resumePausedModulesAfterRecovery();
        trackedLine = null;
        trackedDirection = "";
        recoveryYawBeforeMove = Float.NaN;
        clearPendingAlignmentGateRequest();
        clearPostRejoinDirectionGateState();
        unregisterReconnectServiceListeners();
        clearRestartAutomationState("deactivate", true, true);
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

        clearRestartRecoveryState("failure:" + reason.name(), false, true);
        warning("Reconnect failed (%s): %s", reason.name(), detail == null ? "" : detail);
    }

    private void clearRestartRecoveryState(String reason, boolean disarmService, boolean clearCycleBinding) {
        restartRecoveryActive = false;
        clearPendingRestartBuilderDisableGrace();
        restartEvidenceGateCycleId = 0L;
        clearDelayedMainServerResumeState();
        clearPostRejoinDirectionGateState();
        if (clearCycleBinding) activeReconnectCycleId = 0L;
        if (disarmService) reconnectService().disarmReconnect("THMHwyMonitor clearRestartRecoveryState: " + reason);
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
        if (!reconnectAutomationEnabled()) return;
        refreshTimerSpeedSnapshotFromCurrentState("activeModulesChanged");
    }

    @EventHandler(priority = 1000)
    private void onTickCaptureYawBeforeOtherModules(TickEvent.Pre event) {
        if (mc == null || mc.player == null) return;
        preTickYawSnapshot = mc.player.getYaw();
        preTickYawSnapshotAtMs = System.currentTimeMillis();
    }

    @EventHandler(priority = 999)
    private void onMessageReceive(ReceiveMessageEvent event) {
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
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        wasConnectedLastTick = false;
        pendingDisconnectScreenEvidenceCheck = false;
        pendingDisconnectScreenEvidenceUntilMs = 0L;

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

        if (reconnectRecoveryInFlight()) {
            unresolvedMainServerDisconnectCandidate = false;
            info("Reconnect transfer hop observed while reconnect recovery is already in flight; suppressing fresh disconnect-evidence cycle.");
            return;
        }

        HighwayBuilderTHM builderBeforeDisconnect = Modules.get().get(HighwayBuilderTHM.class);
        boolean builderWasActiveAtDisconnect = builderBeforeDisconnect != null && builderBeforeDisconnect.isActive();
        unresolvedMainServerDisconnectCandidate = builderWasActiveAtDisconnect;
        String disconnectScreenReason = readDisconnectedScreenReasonLower();

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

        if (restartEvidence != null) {
            unresolvedMainServerDisconnectCandidate = false;
            info("Disconnect matched restart evidence (%s). Treating as restart.", restartEvidence);
            handleRestartDetectionTrigger();
            return;
        }

        pendingDisconnectScreenEvidenceCheck = true;
        pendingDisconnectScreenEvidenceUntilMs = System.currentTimeMillis() + DISCONNECT_SCREEN_EVIDENCE_TIMEOUT_MS;
        info("Disconnect detected without immediate hard-fail evidence. Waiting up to 3.0s for disconnect-screen reason.");
    }

    private void handleDetectedNonRestartHardFail(String source) {
        clearRestartAutomationState("non-restart-hard-fail:" + source, true, true);
        clearRestartDisconnectEvidence();
        unresolvedMainServerDisconnectCandidate = false;
        deferRestartScreenshotUntilReconnect = false;
        deferredRestartScreenshotAfterReconnectPending = false;
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
        handleRestartDetectionTrigger();
    }

    private void handleUnclassifiedMainServerDisconnectFallback(String source) {
        if (!unresolvedMainServerDisconnectCandidate) return;
        unresolvedMainServerDisconnectCandidate = false;

        info("Unclassified disconnect detected (%s) while THM HighwayBuilder was active. Treating as restart recovery.", source);
        deferRestartScreenshotUntilReconnect = true;
        handleRestartDetectionTrigger();
    }

    private void maybeTakeDeferredRestartScreenshotAfterReconnect(String source) {
        if (!deferredRestartScreenshotAfterReconnectPending) return;

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
        if (restartScreenshotScheduled) return;
        restartScreenshotScheduled = true;
        int effectiveDelayMs = Math.max(0, delayMs);
        if (effectiveDelayMs <= 0) info("Restart detection screen found. Taking screenshot now.");
        else info("Restart detection screen found. Taking screenshot in %.1fs.", effectiveDelayMs / 1000.0);

        Thread thread = new Thread(() -> {
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
        }, "thm-restart-screenshot");
        thread.setDaemon(true);
        thread.start();
    }

    private void clearPendingRestartBuilderDisableGrace() {
        restartBuilderDisableGraceScheduled = false;
        restartBuilderDisableGraceId = 0L;
    }

    private void scheduleRestartBuilderDisableAndArmAfterGrace() {
        if (restartBuilderDisableGraceScheduled) {
            return;
        }

        final long graceId = nextRestartBuilderDisableGraceId++;
        restartBuilderDisableGraceScheduled = true;
        restartBuilderDisableGraceId = graceId;
        info("Restart evidence detected. Waiting 3.0s before disabling THM HighwayBuilder.");

        Thread thread = new Thread(() -> {
            try {
                Thread.sleep(RESTART_BUILDER_DISABLE_GRACE_MS);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }

            if (!isActive() || mc == null) {
                if (restartBuilderDisableGraceId == graceId) clearPendingRestartBuilderDisableGrace();
                return;
            }

            mc.execute(() -> {
                if (!isActive()) {
                    if (restartBuilderDisableGraceId == graceId) clearPendingRestartBuilderDisableGrace();
                    return;
                }
                if (!restartBuilderDisableGraceScheduled || restartBuilderDisableGraceId != graceId) return;
                if (!autoRestartHandlingEnabled()) {
                    clearPendingRestartBuilderDisableGrace();
                    return;
                }

                clearPendingRestartBuilderDisableGrace();
                restartRecoveryActive = true;
                ensureHighwayBuilderDisabledForRestart("restart detection", true);

                long cycleId;
                ServerReconnectService.ReconnectPreflight preflight = reconnectService().getReconnectPreflight();
                if (preflight.serviceArmed() && preflight.cycleId() > 0L) {
                    cycleId = preflight.cycleId();
                    activeReconnectCycleId = cycleId;
                    restartEvidenceGateCycleId = cycleId;
                } else {
                    cycleId = armReconnectCycle("restart-detection", true);
                }

                info("Restart reconnect handling armed through ServerReconnectService (cycle %d).", cycleId);
            });
        }, "thm-restart-disable-grace");
        thread.setDaemon(true);
        thread.start();
    }

    private boolean autoRestartHandlingEnabled() {
        return reconnectAutomationEnabled();
    }

    private boolean reconnectAutomationEnabled() {
        return autoReconnect.get();
    }

    private boolean hasRestartAutomationState() {
        return activeReconnectCycleId != 0L
            || restartEvidenceGateCycleId != 0L
            || delayedMainServerResumePending
            || delayedMainServerResumeCycleId != 0L
            || delayedMainServerResumeAtMs != 0L
            || restartRecoveryActive
            || restartBuilderDisableGraceScheduled
            || restartDisconnectEvidenceArmed
            || restartScreenshotScheduled
            || postJoinModuleStateCaptured
            || restartModuleStateSnapshotTaken
            || nonRestartHardFailArmed
            || unresolvedMainServerDisconnectCandidate
            || deferRestartScreenshotUntilReconnect
            || deferredRestartScreenshotAfterReconnectPending
            || pendingDisconnectScreenEvidenceCheck
            || pendingDisconnectScreenEvidenceUntilMs != 0L;
    }

    private void resetReconnectAutomationState(boolean clearCycleBinding) {
        restartScreenshotScheduled = false;
        clearPendingRestartBuilderDisableGrace();
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
        clearRestartRecoveryState("reset-automation", false, clearCycleBinding);
    }

    private void clearRestartAutomationState(String reason, boolean disarmService, boolean clearCycleBinding) {
        boolean hadState = hasRestartAutomationState();
        resetReconnectAutomationState(clearCycleBinding);
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

        builder.disableForMonitorRealignPause();
        boolean disabled = !builder.isActive();

        if (disabled) {
            if (verbose) info("Disabled THM HighwayBuilder during restart handling (%s).", source);
        } else {
            warning("Failed to disable THM HighwayBuilder during restart handling (%s).", source);
        }
    }

    private void handleRestartDetectionTrigger() {
        deferRestartScreenshotUntilReconnect = false;
        if (!autoRestartHandlingEnabled()) {
            info("Restart evidence detected, but auto-reconnect is disabled. Skipping reconnect arming.");
            return;
        }

        clearRestartRecoveryState("restart-detection-prep", false, false);
        deferredRestartScreenshotAfterReconnectPending = false;
        scheduleRestartScreenshot(0, "restart-detected");
        scheduleRestartBuilderDisableAndArmAfterGrace();
    }

    private void handleAutoReconnectToggleTransitions() {
        boolean currentToggle = autoReconnect.get();
        if (currentToggle == previousAutoReconnectToggleState) return;

        if (currentToggle) {
            long cycleId = armReconnectCycle("toggle-on", false);
            info("Auto-reconnect enabled. Armed reconnect cycle %d.", cycleId);
        } else {
            clearRestartAutomationState("toggle-off", true, true);
            info("Auto-reconnect disabled. Reconnect cycle and policy state were cleared.");
        }

        previousAutoReconnectToggleState = currentToggle;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        handleReconnectAutomationTickLane();

        if (mc.player == null || mc.world == null) {
            clearPendingAlignmentGateRequest();
            return;
        }

        if (recoveryPhase != RecoveryPhase.None) {
            handleRecoveryPhase();
            return;
        }

        if (!autoRecover.get()) {
            clearPendingAlignmentGateRequest();
            return;
        }

        if (cooldownTicks > 0) {
            cooldownTicks--;
            return;
        }

        ticksSinceCheck++;
        if (ticksSinceCheck < checkInterval.get()) return;
        ticksSinceCheck = 0;

        HighwayBuilderTHM builder = Modules.get().get(HighwayBuilderTHM.class);
        if (builder == null || !builder.isActive()) {
            if (mc.player != null) lastReliableRecoveryYaw = mc.player.getYaw();
            trackedLine = null;
            trackedDirection = "";
            clearPendingAlignmentGateRequest();
            return;
        }

        if (builder.shouldSuppressThmHwyMonitorMisalignmentRecovery()) {
            clearPendingAlignmentGateRequest();
            return;
        }

        int recoveryGoalY = isPavingMode(builder) ? 120 : 119;
        float recoveryDirectionYaw = resolveRecoveryDirectionYawForInference(builder);
        RecoveryTarget target = computeCurrentRecoveryTarget(recoveryDirectionYaw, recoveryGoalY);
        if (target == null) {
            clearPendingAlignmentGateRequest();
            return;
        }

        double yDelta = recoveryYDelta(mc.player.getY(), recoveryGoalY);
        boolean yAligned = Math.abs(yDelta) <= ALIGN_TOLERANCE;
        if (target.distance() <= ALIGN_TOLERANCE && yAligned) {
            clearPendingAlignmentGateRequest();
            return;
        }

        if (!tryPassMainServerAlignmentGate()) return;

        if (!isActive() || mc.player == null || mc.world == null) {
            clearPendingAlignmentGateRequest();
            return;
        }

        builder = Modules.get().get(HighwayBuilderTHM.class);
        if (builder == null || !builder.isActive()) {
            trackedLine = null;
            trackedDirection = "";
            clearPendingAlignmentGateRequest();
            return;
        }

        if (builder.shouldSuppressThmHwyMonitorMisalignmentRecovery()) {
            clearPendingAlignmentGateRequest();
            return;
        }

        recoveryGoalY = isPavingMode(builder) ? 120 : 119;
        recoveryDirectionYaw = resolveRecoveryDirectionYawForInference(builder);
        target = computeCurrentRecoveryTarget(recoveryDirectionYaw, recoveryGoalY);
        if (target == null) {
            clearPendingAlignmentGateRequest();
            return;
        }

        yDelta = recoveryYDelta(mc.player.getY(), recoveryGoalY);
        yAligned = Math.abs(yDelta) <= ALIGN_TOLERANCE;
        if (target.distance() <= ALIGN_TOLERANCE && yAligned) {
            clearPendingAlignmentGateRequest();
            return;
        }

        String yOffset = yAligned ? "" : String.format(Locale.ROOT, ", Y %+.2f", yDelta);
        beginRecoveryRoutine(builder, target, yOffset, recoveryDirectionYaw);
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
        String preferredDirection = normalizeDirection(directionCode(builder != null ? builder.getWorkingDirection() : null));
        if (preferredDirection.isEmpty() || Float.isNaN(recoveryDirectionYaw)) return null;

        RecoveryTarget target = determineRecoveryTarget(
            mc.player.getX(),
            mc.player.getZ(),
            recoveryDirectionYaw,
            recoveryGoalY,
            trueCenterMode.get(),
            trackedLine,
            preferredDirection
        );
        if (target == null) return null;

        if (trackedLine == null && target.distance() <= ALIGN_TOLERANCE) {
            trackedLine = target.line();
        }

        if (trackedLine != null) {
            target = determineRecoveryTarget(
                mc.player.getX(),
                mc.player.getZ(),
                recoveryDirectionYaw,
                recoveryGoalY,
                trueCenterMode.get(),
                trackedLine,
                preferredDirection
            );
            if (target == null) return null;
        }

        trackedDirection = target.direction();
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

    private void beginRecoveryRoutine(HighwayBuilderTHM builder, RecoveryTarget target, String yOffset, float recoveryDirectionYaw) {
        if (target.distance() > maxCorrectionDistance.get()) {
            warning("Misaligned by %.2f%s on %s %s. Exceeds max-correction-distance %.2f.",
                target.distance(), yOffset, target.highway(), target.direction(), maxCorrectionDistance.get());
            handleExcessiveMisalignment(builder, target);
            return;
        }

        if (!pauseAllActiveModulesForRecovery()) {
            warning("Failed to pause recovery modules (THM HighwayBuilder / Timer / Speed).");
            cooldownTicks = recoveryCooldown.get();
            return;
        }

        RecoveryTarget correctionTarget = applyRepairMisalignmentBackstepForGoto(target);
        recoveryBuilder = builder;
        pendingCorrectionTarget = correctionTarget;
        trackedLine = target.line();
        trackedDirection = target.direction();
        recoveryYawBeforeMove = recoveryDirectionYaw;
        recoveryTicks = RECOVERY_DELAY_TICKS;
        recoveryPhase = RecoveryPhase.WaitBeforeCorrection;
        info("Paused recovery modules (THM HighwayBuilder / Timer / Speed) on %s %s (off by %.2f%s). Starting Baritone correction in 2.0s.", target.highway(), target.direction(), target.distance(), yOffset);
    }

    private void handleReconnectAutomationTickLane() {
        handleAutoReconnectToggleTransitions();
        refreshReconnectBaselineValidity();
        maybeRunDelayedMainServerResumeFinalization();
        maybeRunPostRejoinDirectionGate();

        boolean liveConnectedNow = hasLiveServerConnection();
        clearStaleDisconnectedScreenIfLiveConnected();
        handlePendingDisconnectScreenEvidenceCheck(liveConnectedNow);
        wasConnectedLastTick = liveConnectedNow;

        if (consumeNonRestartHardFailSignal()) {
            nonRestartHardFailArmed = true;
            handleDetectedNonRestartHardFail("signal");
        }

        if (reconnectService().isReconnectArmed() && restartRecoveryActive) {
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

    private boolean reconnectRecoveryInFlight() {
        if (activeReconnectCycleId == 0L) return false;

        return reconnectService().isReconnectArmed()
            || restartBuilderDisableGraceScheduled
            || restartRecoveryActive
            || restartEvidenceGateCycleId == activeReconnectCycleId
            || delayedMainServerResumePending
            || delayedMainServerResumeCycleId == activeReconnectCycleId
            || postRejoinDirectionGateActive
            || deferredRestartScreenshotAfterReconnectPending;
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

        long cycleId = delayedMainServerResumeCycleId;
        String contextTag = delayedMainServerResumeContext;
        clearDelayedMainServerResumeState();

        info(
            "Reconnect MAIN_SERVER delay complete (%s). Entering reconnect direction gate (cycle %d).",
            contextTag,
            cycleId
        );
        beginPostRejoinDirectionGate(cycleId, contextTag);
    }

    private float resolveRecoveryDirectionYawForInference(HighwayBuilderTHM builder) {
        if (builder == null) return Float.NaN;
        HorizontalDirection direction = builder.getWorkingDirection();
        return direction == null ? Float.NaN : direction.yaw;
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
                warning("Baritone is not available. Cannot perform Baritone-based recovery.");
                recoveryPhase = RecoveryPhase.WaitBeforeResume;
                recoveryTicks = RECOVERY_DELAY_TICKS;
                return;
            }

            IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
            if (baritone == null) {
                warning("Unable to acquire primary Baritone instance for recovery.");
                recoveryPhase = RecoveryPhase.WaitBeforeResume;
                recoveryTicks = RECOVERY_DELAY_TICKS;
                return;
            }

            baritone.getPathingBehavior().cancelEverything();
            baritone.getCustomGoalProcess().setGoalAndPath(new GoalBlock(
                pendingCorrectionTarget.goalX(),
                pendingCorrectionTarget.goalY(),
                pendingCorrectionTarget.goalZ()
            ));

            baritoneStartupTicks = BARITONE_PATH_STARTUP_TICKS;
            baritoneTimeoutTicks = BARITONE_PATH_TIMEOUT_TICKS;
            recoveryPhase = RecoveryPhase.BaritoneWalking;
            info("Baritone recovery path started to (%d, %d, %d).", pendingCorrectionTarget.goalX(), pendingCorrectionTarget.goalY(), pendingCorrectionTarget.goalZ());
            return;
        }

        if (recoveryPhase == RecoveryPhase.BaritoneWalking) {
            if (pendingCorrectionTarget == null || mc.player == null) {
                resetRecoveryState();
                cooldownTicks = recoveryCooldown.get();
                return;
            }

            IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
            if (baritone == null) {
                warning("Baritone instance became unavailable during recovery.");
                recoveryPhase = RecoveryPhase.WaitBeforeResume;
                recoveryTicks = RECOVERY_DELAY_TICKS;
                return;
            }

            if (baritone.getPathingBehavior().isPathing()) {
                if (baritoneTimeoutTicks > 0) baritoneTimeoutTicks--;
                if (baritoneTimeoutTicks == 0) {
                    baritone.getPathingBehavior().cancelEverything();
                    warning("Baritone recovery timed out.");
                    recoveryPhase = RecoveryPhase.WaitBeforeResume;
                    recoveryTicks = RECOVERY_DELAY_TICKS;
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
                warning("Baritone stopped before recovery goal (%.2f blocks away).", distanceToGoal);
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

    private void resetRecoveryState() {
        recoveryBuilder = null;
        pendingCorrectionTarget = null;
        recoveryTicks = 0;
        baritoneStartupTicks = 0;
        baritoneTimeoutTicks = 0;
        recoveryPhase = RecoveryPhase.None;
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
            builder.preserveCenterSpeedBaselineForMonitorRecovery("thm-monitor-pause");
            builder.disableForMonitorRealignPause();
        } else {
            module.disable();
        }
    }

    private void resumePausedModulesAfterRecovery() {
        if (!recoveryModulesPaused) return;

        internalTimerSpeedToggleInProgress = true;
        try {
            for (Module module : recoveryPausedModules) {
                if (module == null) continue;
                module.enable();
            }
        } finally {
            internalTimerSpeedToggleInProgress = false;
        }

        recoveryPausedModules.clear();
        recoveryModulesPaused = false;
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

        String bestHighway = "None";
        String bestDirection = "None";
        double bestDistance = HUGE_DISTANCE;

        if (cardinal) {
            // z = offset (east-west highway), portion is W/E relative to origin.
            double dXAxis = Math.abs(playerZ - centerOffset);
            if (dXAxis < bestDistance) {
                bestDistance = dXAxis;
                bestHighway = "Cardinal";
                bestDirection = resolveWorkingDirection(playerX, playerZ, centerOffset);
            }

            // x = offset (north-south highway), portion is N/S relative to origin.
            double dZAxis = Math.abs(playerX - centerOffset);
            if (dZAxis < bestDistance) {
                bestDistance = dZAxis;
                bestHighway = "Cardinal";
                bestDirection = resolveWorkingDirection(playerX, playerZ, centerOffset);
            }
        }

        if (diagonal) {
            // AxisViewer diagonal 1: x - z = 0, corresponds to NW <-> SE.
            double d1 = distanceToLine(playerX, playerZ, 1.0, -1.0, 0.0);
            if (d1 < bestDistance) {
                bestDistance = d1;
                bestHighway = "Diagonal";
                bestDirection = resolveWorkingDirection(playerX, playerZ, centerOffset);
            }

            // AxisViewer diagonal 2: x + z = 0 (or 1 in true-center mode), corresponds to NE <-> SW.
            double c = trueCenterMode ? 1.0 : 0.0;
            double d2 = distanceToLine(playerX, playerZ, 1.0, 1.0, c);
            if (d2 < bestDistance) {
                bestDistance = d2;
                bestHighway = "Diagonal";
                bestDirection = resolveWorkingDirection(playerX, playerZ, centerOffset);
            }
        }

        if (ring) {
            for (int r : RING_ROADS) {
                double left = -r + centerOffset;
                double right = r + centerOffset;
                double bottom = -r + centerOffset;
                double top = r + centerOffset;

                double dBottom = distancePointToSegment(playerX, playerZ, left, bottom, right, bottom);
                if (dBottom < bestDistance) {
                    bestDistance = dBottom;
                    bestHighway = "Ring";
                    bestDirection = "N";
                }

                double dTop = distancePointToSegment(playerX, playerZ, left, top, right, top);
                if (dTop < bestDistance) {
                    bestDistance = dTop;
                    bestHighway = "Ring";
                    bestDirection = "S";
                }

                double dLeft = distancePointToSegment(playerX, playerZ, left, bottom, left, top);
                if (dLeft < bestDistance) {
                    bestDistance = dLeft;
                    bestHighway = "Ring";
                    bestDirection = "W";
                }

                double dRight = distancePointToSegment(playerX, playerZ, right, bottom, right, top);
                if (dRight < bestDistance) {
                    bestDistance = dRight;
                    bestHighway = "Ring";
                    bestDirection = "E";
                }
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

                double dSE = distancePointToSegment(playerX, playerZ, x1, z1, x2, z2);
                if (dSE < bestDistance) {
                    bestDistance = dSE;
                    bestHighway = "Diamond";
                    bestDirection = "SE";
                }

                double dSW = distancePointToSegment(playerX, playerZ, x2, z2, x3, z3);
                if (dSW < bestDistance) {
                    bestDistance = dSW;
                    bestHighway = "Diamond";
                    bestDirection = "SW";
                }

                double dNW = distancePointToSegment(playerX, playerZ, x3, z3, x4, z4);
                if (dNW < bestDistance) {
                    bestDistance = dNW;
                    bestHighway = "Diamond";
                    bestDirection = "NW";
                }

                double dNE = distancePointToSegment(playerX, playerZ, x4, z4, x1, z1);
                if (dNE < bestDistance) {
                    bestDistance = dNE;
                    bestHighway = "Diamond";
                    bestDirection = "NE";
                }
            }
        }

        if (bestDistance <= ALIGN_TOLERANCE) {
            if ("Ring".equals(bestHighway) || "Diamond".equals(bestHighway)) {
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
        WorkLine preferredLine,
        String preferredDirection
    ) {
        double centerOffset = trueCenterMode ? 0.5 : 0.0;
        WorkLine line = preferredLine != null ? preferredLine : nearestWorkLine(playerX, playerZ, centerOffset, trueCenterMode);
        if (line == null) return null;

        double targetX = playerX;
        double targetZ = playerZ;
        switch (line) {
            case CardinalNS -> targetX = centerOffset;
            case CardinalEW -> targetZ = centerOffset;
            case DiagonalNWSE -> {
                double[] point = closestPointOnLine(playerX, playerZ, 1.0, -1.0, 0.0);
                targetX = point[0];
                targetZ = point[1];
            }
            case DiagonalNESW -> {
                double c = trueCenterMode ? 1.0 : 0.0;
                double[] point = closestPointOnLine(playerX, playerZ, 1.0, 1.0, c);
                targetX = point[0];
                targetZ = point[1];
            }
        }

        String direction = normalizeDirection(preferredDirection);
        if (!isDirectionCompatible(line, direction)) return null;

        double distance = Math.hypot(playerX - targetX, playerZ - targetZ);
        int goalX = floorToBlock(targetX);
        int goalY = recoveryGoalY;
        int goalZ = floorToBlock(targetZ);
        String highway = (line == WorkLine.CardinalNS || line == WorkLine.CardinalEW) ? "Cardinal" : "Diagonal";

        return new RecoveryTarget(highway, direction, targetX, targetZ, goalX, goalY, goalZ, directionToYaw(direction), distance, line);
    }

    private RecoveryTarget applyRepairMisalignmentBackstepForGoto(RecoveryTarget target) {
        if (target == null || !repairMisalignments.get()) return target;

        int directionOffsetX = workingDirectionOffsetX(target.direction());
        int directionOffsetZ = workingDirectionOffsetZ(target.direction());
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
            correctedTargetX,
            correctedTargetZ,
            correctedGoalX,
            target.goalY(),
            correctedGoalZ,
            target.yaw(),
            correctedDistance,
            target.line()
        );
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

        PostRejoinDirectionResult result = determineConclusivePostRejoinWorkingDirection();
        if (result.conclusive()) {
            if (applyDirectionAndEnableHighwayBuilder(result.direction())) finishSuccessfulReconnectResume();
            return;
        }

        postRejoinDirectionRetryCount++;
        postRejoinDirectionBlockReason = result.reason();
        postRejoinDirectionBlockSummary = result.summary();
        postRejoinDirectionNextAttemptAtMs = System.currentTimeMillis() + POST_REJOIN_DIRECTION_RETRY_DELAY_MS;

        if (autoScreenshotOnRestartDetection.get() && postRejoinDirectionRetryCount >= 3 && !postRejoinBlockedScreenshotTaken) {
            postRejoinBlockedScreenshotTaken = true;
            takeRestartScreenshot();
        }

        if (postRejoinDirectionRetryCount >= POST_REJOIN_DIRECTION_RETRY_LIMIT) {
            enterReconnectSafetyStop("Reconnect resume stopped: " + result.reason());
        }
    }

    private boolean applyDirectionAndEnableHighwayBuilder(HorizontalDirection workingDirection) {
        applyPostRejoinYaw(workingDirection);
        info("Post-rejoin direction selected: %s.", workingDirection.name);

        HighwayBuilderTHM builder = Modules.get().get(HighwayBuilderTHM.class);
        if (builder == null) {
            enterReconnectSafetyStop("THM HighwayBuilder module not found, cannot resume.");
            return false;
        }

        if (!builder.resumeFromReconnect(workingDirection, activeReconnectCycleId)) {
            enterReconnectSafetyStop("HighwayBuilder refused reconnect resume for locked direction.");
            return false;
        }

        info("Resumed THM HighwayBuilder after post-rejoin checks.");
        return true;
    }

    private void finishSuccessfulReconnectResume() {
        maybeTakeDeferredRestartScreenshotAfterReconnect("main-server-ready");
        clearRestartAutomationState("post-main-server finalization complete", true, true);
    }

    private PostRejoinDirectionResult determineConclusivePostRejoinWorkingDirection() {
        if (mc.player == null || mc.world == null) return PostRejoinDirectionResult.blocked("player-or-world-missing", "");

        HorizontalDirection[] axisDirections = resolvePostRejoinAxisDirections();
        if (axisDirections == null) {
            postRejoinLastCompleteProbeWinner = null;
            return PostRejoinDirectionResult.blocked("axis-unresolved", "line=unresolved");
        }

        HorizontalDirection dirA = axisDirections[0];
        HorizontalDirection dirB = axisDirections[1];
        HighwayBuilderTHM builder = Modules.get().get(HighwayBuilderTHM.class);
        boolean pavingSelected = builder != null && isPavingMode(builder);
        AxisProbeResult probe = pavingSelected
            ? probeAxis(dirA, dirB, 119, true)
            : probeAxis(dirA, dirB, 122, false);

        String summary = String.format(Locale.ROOT, "%s=%d %s=%d",
            probe.dirA().name,
            probe.dirAScore(),
            probe.dirB().name,
            probe.dirBScore()
        );

        if (!probe.allSamplesLoaded()) {
            postRejoinLastCompleteProbeWinner = null;
            return PostRejoinDirectionResult.blocked("probe-unloaded", summary);
        }

        if (!probe.strongWinner() || probe.selectedDirection() == null) {
            postRejoinLastCompleteProbeWinner = null;
            return PostRejoinDirectionResult.blocked("probe-ambiguous", summary);
        }

        if (postRejoinLastCompleteProbeWinner == probe.selectedDirection()) {
            postRejoinLastCompleteProbeWinner = probe.selectedDirection();
            return PostRejoinDirectionResult.success(probe.selectedDirection(), summary);
        }

        postRejoinLastCompleteProbeWinner = probe.selectedDirection();
        return PostRejoinDirectionResult.blocked("probe-ambiguous", summary);
    }

    private HorizontalDirection[] resolvePostRejoinAxisDirections() {
        if (mc.player == null) return null;

        WorkLine line = resolveReconnectLineFromCurrentPosition();
        if (line == null) return null;

        return switch (line) {
            case CardinalNS -> new HorizontalDirection[] {HorizontalDirection.South, HorizontalDirection.North};
            case CardinalEW -> new HorizontalDirection[] {HorizontalDirection.East, HorizontalDirection.West};
            case DiagonalNWSE -> new HorizontalDirection[] {HorizontalDirection.NorthWest, HorizontalDirection.SouthEast};
            case DiagonalNESW -> new HorizontalDirection[] {HorizontalDirection.NorthEast, HorizontalDirection.SouthWest};
        };
    }

    private WorkLine resolveReconnectLineFromCurrentPosition() {
        if (mc == null || mc.player == null) return null;
        double centerOffset = trueCenterMode.get() ? 0.5 : 0.0;
        ReconnectLineResolution resolved = nearestWorkLineWithAmbiguityBlock(
            mc.player.getX(),
            mc.player.getZ(),
            centerOffset,
            trueCenterMode.get(),
            RECONNECT_LINE_AMBIGUITY_THRESHOLD
        );
        if (resolved == null || resolved.distance() > RECONNECT_LINE_MAX_DISTANCE) return null;
        return resolved.line();
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

        if (builder != null) {
            builder.restoreCenterSpeedBaselineForFailedReconnect(cycleId);
            builder.disableForReconnectSafetyStop();
        }

        if (autoScreenshotOnRestartDetection.get() && !postRejoinTerminalScreenshotTaken) {
            postRejoinTerminalScreenshotTaken = true;
            takeRestartScreenshot();
        }

        intentionalSafetyDisconnectArmed = true;
        disableMonitorAfterIntentionalSafetyDisconnect = true;
        clearRestartAutomationStateForTerminalStop("reconnect-safety-stop");

        if (hasLiveServerConnection()) {
            mc.getNetworkHandler().getConnection().disconnect(Text.of("THMHwyMonitor Safety Stop: " + reason));
            return;
        }

        intentionalSafetyDisconnectArmed = false;
        disableMonitorAfterIntentionalSafetyDisconnect = false;

        if (mc != null) {
            mc.setScreen(new DisconnectedScreen(
                new TitleScreen(),
                Text.of("THMHwyMonitor Safety Stop"),
                Text.of(reason + " HighwayBuilder stayed off for safety.")
            ));
        }

        if (isActive()) toggle();
    }

    private void applyPostRejoinYaw(HorizontalDirection direction) {
        if (mc.player == null) return;

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
        if (!isSuccessfullyConnectedToServer()) {
            return;
        }

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

    private static WorkLine nearestWorkLine(double playerX, double playerZ, double centerOffset, boolean trueCenterMode) {
        double best = HUGE_DISTANCE;
        WorkLine line = null;

        double dCardinalNs = Math.abs(playerX - centerOffset);
        if (dCardinalNs < best) {
            best = dCardinalNs;
            line = WorkLine.CardinalNS;
        }

        double dCardinalEw = Math.abs(playerZ - centerOffset);
        if (dCardinalEw < best) {
            best = dCardinalEw;
            line = WorkLine.CardinalEW;
        }

        double dDiagNwSe = distanceToLine(playerX, playerZ, 1.0, -1.0, 0.0);
        if (dDiagNwSe < best) {
            best = dDiagNwSe;
            line = WorkLine.DiagonalNWSE;
        }

        double c = trueCenterMode ? 1.0 : 0.0;
        double dDiagNeSw = distanceToLine(playerX, playerZ, 1.0, 1.0, c);
        if (dDiagNeSw < best) {
            line = WorkLine.DiagonalNESW;
        }

        return line;
    }

    private static ReconnectLineResolution nearestWorkLineWithAmbiguityBlock(double playerX, double playerZ, double centerOffset, boolean trueCenterMode, double ambiguityThreshold) {
        double dCardinalNs = Math.abs(playerX - centerOffset);
        double dCardinalEw = Math.abs(playerZ - centerOffset);
        double dDiagNwSe = distanceToLine(playerX, playerZ, 1.0, -1.0, 0.0);
        double c = trueCenterMode ? 1.0 : 0.0;
        double dDiagNeSw = distanceToLine(playerX, playerZ, 1.0, 1.0, c);

        double best = dCardinalNs;
        double second = HUGE_DISTANCE;
        WorkLine line = WorkLine.CardinalNS;

        double[] distances = new double[] {dCardinalEw, dDiagNwSe, dDiagNeSw};
        WorkLine[] lines = new WorkLine[] {WorkLine.CardinalEW, WorkLine.DiagonalNWSE, WorkLine.DiagonalNESW};
        for (int i = 0; i < distances.length; i++) {
            double distance = distances[i];
            if (distance < best) {
                second = best;
                best = distance;
                line = lines[i];
            } else if (distance < second) {
                second = distance;
            }
        }

        if (Math.abs(second - best) <= ambiguityThreshold) return null;
        return new ReconnectLineResolution(line, best);
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
        if (pendingCorrectionTarget == null || mc.player == null || recoveryBuilder == null) return;

        HorizontalDirection direction = recoveryBuilder.getWorkingDirection();
        if (direction == null) return;

        float yaw = direction.yaw;
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

    private static String resolveWorkingDirection(double playerX, double playerZ, double centerOffset) {
        boolean xAxis = Math.abs(playerZ - centerOffset) <= ALIGN_TOLERANCE;
        boolean zAxis = Math.abs(playerX - centerOffset) <= ALIGN_TOLERANCE;

        String northSouth = northSouthDirection(playerZ, centerOffset);
        String eastWest = eastWestDirection(playerX, centerOffset);

        if (xAxis && ("North".equals(northSouth) || "South".equals(northSouth))) return northSouth;
        if (zAxis && ("East".equals(eastWest) || "West".equals(eastWest))) return eastWest;

        if (!northSouth.isEmpty() && !eastWest.isEmpty()) return northSouth + eastWest;
        if (!northSouth.isEmpty()) return northSouth;
        if (!eastWest.isEmpty()) return eastWest;

        return "Center";
    }

    private static String northSouthDirection(double playerZ, double centerOffset) {
        if (playerZ >= centerOffset + ALIGN_TOLERANCE) return "South";
        if (playerZ <= centerOffset - ALIGN_TOLERANCE) return "North";
        return "";
    }

    private static String eastWestDirection(double playerX, double centerOffset) {
        if (playerX >= centerOffset + ALIGN_TOLERANCE) return "East";
        if (playerX <= centerOffset - ALIGN_TOLERANCE) return "West";
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

    private static float closestParallelYawForSegment(float referenceYaw, String highway, String direction, WorkLine line) {
        float[] pair = parallelYawPairForSegment(highway, direction, line);
        if (pair == null) return yawForWorkingDirection(direction);

        float reference = Float.isNaN(referenceYaw) ? yawForWorkingDirection(direction) : referenceYaw;
        float yawA = pair[0];
        float yawB = pair[1];
        float distanceA = yawDistance(reference, yawA);
        float distanceB = yawDistance(reference, yawB);
        if (distanceA < distanceB) return yawA;
        if (distanceB < distanceA) return yawB;

        String preferred = normalizeDirection(direction);
        float preferredYaw = yawForWorkingDirection(preferred);
        float preferredDistanceA = yawDistance(preferredYaw, yawA);
        float preferredDistanceB = yawDistance(preferredYaw, yawB);
        if (preferredDistanceA < preferredDistanceB) return yawA;
        if (preferredDistanceB < preferredDistanceA) return yawB;

        return yawA;
    }

    private static float[] parallelYawPairForSegment(String highway, String direction, WorkLine line) {
        float[] linePair = parallelYawPairForLine(line);
        if (linePair != null) return linePair;

        String dir = normalizeDirection(direction);

        if ("Ring".equals(highway)) {
            return switch (dir) {
                case "N", "S" -> yawPair("E", "W");
                case "E", "W" -> yawPair("N", "S");
                default -> null;
            };
        }

        if ("Diamond".equals(highway)) {
            return switch (dir) {
                case "NE", "SW" -> yawPair("NW", "SE");
                case "NW", "SE" -> yawPair("NE", "SW");
                default -> null;
            };
        }

        return switch (dir) {
            case "N", "S" -> yawPair("N", "S");
            case "E", "W" -> yawPair("E", "W");
            case "NE", "SW" -> yawPair("NE", "SW");
            case "NW", "SE" -> yawPair("NW", "SE");
            default -> null;
        };
    }

    private static float[] parallelYawPairForLine(WorkLine line) {
        if (line == null) return null;

        return switch (line) {
            case CardinalNS -> yawPair("N", "S");
            case CardinalEW -> yawPair("E", "W");
            case DiagonalNWSE -> yawPair("NW", "SE");
            case DiagonalNESW -> yawPair("NE", "SW");
        };
    }

    private static float[] yawPair(String directionA, String directionB) {
        return new float[] {yawForWorkingDirection(directionA), yawForWorkingDirection(directionB)};
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
        double targetX,
        double targetZ,
        int goalX,
        int goalY,
        int goalZ,
        float yaw,
        double distance,
        WorkLine line
    ) {}

    private enum RecoveryPhase {
        None,
        WaitBeforeCorrection,
        BaritoneWalking,
        WaitBeforeYaw,
        WaitBeforeResume
    }

    private enum WorkLine {
        CardinalNS,
        CardinalEW,
        DiagonalNWSE,
        DiagonalNESW
    }
}


