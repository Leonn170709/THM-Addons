package xyz.thm.addon.modules;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.pathing.goals.GoalBlock;
import baritone.api.utils.Rotation;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.game.GameJoinedEvent;
import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.events.meteor.ActiveModulesChangedEvent;
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
import meteordevelopment.meteorclient.systems.modules.misc.AutoReconnect;
import meteordevelopment.meteorclient.systems.modules.world.Timer;
import meteordevelopment.meteorclient.utils.misc.HorizontalDirection;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Blocks;
import net.minecraft.client.gui.screen.DisconnectedScreen;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.registry.RegistryKey;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import xyz.thm.addon.THMAddon;
import xyz.thm.addon.system.THMSystem;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

public class THMHwyMonitor extends Module {
    private static final double ALIGN_TOLERANCE = 0.4;
    private static final double HUGE_DISTANCE = 1.0e30;
    private static final int RECOVERY_DELAY_TICKS = 40;
    private static final int YAW_SET_DELAY_TICKS = 10;
    private static final int BARITONE_PATH_STARTUP_TICKS = 10;
    private static final int BARITONE_PATH_TIMEOUT_TICKS = 20 * 20;
    private static final int POST_REJOIN_ACTION_DELAY_MS = 2000;
    private static final int POST_REJOIN_RETRY_DELAY_MS = 10_000;
    private static final int POST_REJOIN_MAX_RETRIES = 20;
    private static final int POST_REJOIN_PRE_STEP_DELAY_TICKS = 40;
    private static final double POST_REJOIN_COORD_SUCCESS_DISTANCE = 100.0;
    private static final int POST_REJOIN_PATH_STARTUP_TICKS = 20;
    private static final int POST_REJOIN_PATH_TIMEOUT_TICKS = 20 * 180;
    private static final int POST_REJOIN_AXIS_PROBE_DISTANCE = 5;
    private static final int PENDING_BUILDER_DIRECTION_FAIL_REARM_THRESHOLD = 4;
    private static final String POST_REJOIN_PORTAL_COMMAND = "goto nether_portal";
    private static final int RESTART_SCREENSHOT_DELAY_MS = 2000;
    private static final int BUILDER_ENABLE_DELAY_MS = 6000;
    private static final int DISCONNECT_SCREEN_EVIDENCE_TIMEOUT_MS = 3000;
    // Release gate: keep restart automation code present but hidden/disabled in UI and runtime.
    private static final boolean EXPOSE_RESTART_AUTOMATION_SETTINGS = false;
    private static final boolean RUNTIME_WATCHDOG_LOG_ENABLED = false;
    private static final boolean EXECUTION_TRACE_LOG_ENABLED = false;
    private static final String BARITONE_PATH_COMPLETE_MARKER = "pathing complete";
    private static final String CRACKED_LOGIN_SUCCESS_MARKER = "you are now logged in!";
    private static final String LOGIN_PROMPT_MARKER = "please login with the command: /login";
    private static final String RESTART_DETECTED_MARKER = "server restart detected";
    private static final String RESTOCK_FAILURE_MARKER = "unable to perform restock";
    private static final String THM_HIGHWAYBUILDER_TAG_A = "thm highwaybuilder";
    private static final String THM_HIGHWAYBUILDER_TAG_B = "thm-highwaybuilder";
    private static final long RESTART_EVIDENCE_TTL_MS = 20_000L;
    private static final AtomicBoolean NON_RESTART_HARD_FAIL_SIGNAL = new AtomicBoolean(false);
    private static final AtomicBoolean RESTART_HARD_FAIL_SIGNAL = new AtomicBoolean(false);
    private static final DateTimeFormatter RUNTIME_FLAG_TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

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
        .visible(() -> EXPOSE_RESTART_AUTOMATION_SETTINGS)
        .build()
    );

    private final Setting<Boolean> autoRejoinOnRestartDetection = sgGeneral.add(new BoolSetting.Builder()
        .name("automatic-restart-handling")
        .description("Handles restart recovery loop: reconnects, runs login flow, and executes post-join pathing.")
        .defaultValue(false)
        .visible(() -> EXPOSE_RESTART_AUTOMATION_SETTINGS)
        .build()
    );

    private final Setting<Integer> restartRejoinDelayMinutes = sgGeneral.add(new IntSetting.Builder()
        .name("restart-rejoin-delay-minutes")
        .description("Delay in minutes applied to Meteor AutoReconnect.")
        .defaultValue(15)
        .range(1, 240)
        .sliderRange(1, 60)
        .visible(() -> EXPOSE_RESTART_AUTOMATION_SETTINGS && autoRejoinOnRestartDetection.get())
        .build()
    );

    private final Setting<Boolean> crackedAccountMode = sgGeneral.add(new BoolSetting.Builder()
        .name("cracked-account-mode")
        .description("Use THM Tab cracked-password to run /login during cracked-account reconnect flow.")
        .defaultValue(false)
        .visible(() -> EXPOSE_RESTART_AUTOMATION_SETTINGS && autoRejoinOnRestartDetection.get())
        .build()
    );

    private final Setting<Boolean> enableHighwayBuilderOnRestart = sgGeneral.add(new BoolSetting.Builder()
        .name("enable-highway-builder-on-restart")
        .description("Enable THM HighwayBuilder after successful restart rejoin flow.")
        .defaultValue(true)
        .visible(() -> EXPOSE_RESTART_AUTOMATION_SETTINGS && autoRejoinOnRestartDetection.get())
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
    private boolean pendingPostRejoinActions;
    private long postRejoinActionsAtMs;
    private boolean awaitingCrackedLoginSuccess;
    private boolean postRejoinRoutineRetryScheduled;
    private long postRejoinRoutineRetryAtMs;
    private int postRejoinRoutineRetryCount;
    private boolean postJoinModuleStateCaptured;
    private boolean timerWasActiveBeforePostJoin;
    private boolean speedWasActiveBeforePostJoin;
    private String[] postRejoinPathCommands = new String[0];
    private int nextPostRejoinPathCommandIndex;
    private int activePostRejoinPathCommandIndex = -1;
    private boolean waitingForPostRejoinCommandDelay;
    private int postRejoinCommandDelayTicks;
    private boolean waitingForPostRejoinPathStart;
    private boolean waitingForPostRejoinCompletionMessage;
    private boolean postRejoinStepStartCaptured;
    private double postRejoinStepStartX;
    private double postRejoinStepStartZ;
    private int postRejoinPathStartupTicks;
    private int postRejoinPathTimeoutTicks;
    private boolean suppressAutoReconnectAfterHardFail;
    private boolean awaitPostRejoinAfterReconnect;
    private boolean nonRestartHardFailArmed;
    private boolean unresolvedMainServerDisconnectCandidate;
    private boolean deferRestartScreenshotUntilReconnect;
    private boolean deferredRestartScreenshotAfterReconnectPending;
    private boolean pendingDisconnectScreenEvidenceCheck;
    private long pendingDisconnectScreenEvidenceUntilMs;
    private boolean restartHandlingArmed;
    private boolean postRejoinStartedForCurrentRestart;
    private boolean restartModuleStateSnapshotTaken;
    private RestartRoutineStage restartRoutineStage = RestartRoutineStage.None;
    private PostRejoinPathPurpose activePostRejoinPathPurpose = PostRejoinPathPurpose.None;
    private boolean pendingHighwayBuilderEnableAfterRestore;
    private HorizontalDirection pendingHighwayBuilderDirection;
    private long pendingHighwayBuilderEnableAtMs;
    private int pendingBuilderDirectionProbeFailures;
    private boolean wasConnectedLastTick;
    private boolean crackedLoginPromptSeenThisCycle;
    private boolean crackedLoginSuccessSeenThisCycle;
    private boolean restartDisconnectEvidenceArmed;
    private long restartDisconnectEvidenceAtMs;
    private String restartDisconnectEvidenceSource = "";
    private float lastReliableRecoveryYaw = Float.NaN;
    private float preTickYawSnapshot = Float.NaN;
    private long preTickYawSnapshotAtMs;
    private boolean internalTimerSpeedToggleInProgress;
    private static Field disconnectedScreenReasonField;
    private static boolean disconnectedScreenReasonFieldResolved;
    private volatile boolean runtimeWatchdogRunning;
    private Thread runtimeWatchdogThread;
    private String lastRuntimeWatchdogState = "";
    private long executionTraceCounter;

    public THMHwyMonitor() {
        super(THMAddon.MAIN, "THM Highway Monitor", "Monitors alignment and recovers HighwayBuilder from drift.");
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

    private static boolean isRestartHardFailMessage(String lower) {
        return isHighwayBuilderTaggedMessage(lower) && lower.contains(RESTART_DETECTED_MARKER);
    }

    private static boolean isKnownNonRestartHardFailMessage(String lower) {
        return isHighwayBuilderTaggedMessage(lower) && lower.contains(RESTOCK_FAILURE_MARKER);
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
            traceExec("consumeRestartDisconnectEvidence:disconnectScreen");
            clearRestartDisconnectEvidence();
            return "disconnect-screen";
        }

        if (restartDisconnectEvidenceArmed) {
            long ageMs = System.currentTimeMillis() - restartDisconnectEvidenceAtMs;
            if (ageMs <= RESTART_EVIDENCE_TTL_MS) {
                String source = restartDisconnectEvidenceSource == null || restartDisconnectEvidenceSource.isEmpty()
                    ? "message"
                    : restartDisconnectEvidenceSource;
                traceExec("consumeRestartDisconnectEvidence:" + source + ":ageMs=" + ageMs);
                clearRestartDisconnectEvidence();
                return source;
            }

            traceExec("consumeRestartDisconnectEvidence:expired:ageMs=" + ageMs);
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
        startRuntimeWatchdogIfNeeded();
        runtimeFlag("onActivate");
        executionTraceCounter = 0L;
        traceExec("onActivate:begin");
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
        resetReconnectAutomationState(false);
        wasConnectedLastTick = isSuccessfullyConnectedToServer();
        if (reconnectAutomationEnabled()) refreshTimerSpeedSnapshotFromCurrentState("activate");
        runtimeFlag("activate-state-initialized");
        traceExec("onActivate:end");
    }

    @Override
    public void onDeactivate() {
        runtimeFlag("onDeactivate");
        traceExec("onDeactivate:begin");
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
        resetReconnectAutomationState(false);
        wasConnectedLastTick = false;
        stopRuntimeWatchdog();
        traceExec("onDeactivate:end");
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
        runtimeFlag(String.format(Locale.ROOT, "cacheRecoveryYaw:onActivate:%.2f", yaw));
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
        runtimeFlag(String.format(
            Locale.ROOT,
            "refreshTimerSpeedSnapshot:%s:timer=%s:speed=%s",
            source,
            timerWasActiveBeforePostJoin,
            speedWasActiveBeforePostJoin
        ));
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
        if (!reconnectAutomationEnabled()) return;
        String lower = message.toLowerCase(Locale.ROOT);
        traceExec("onMessageReceive:len=" + lower.length());

        if (isRestartHardFailMessage(lower)) {
            armRestartDisconnectEvidence("message");
            runtimeFlag("restartEvidence:message");
            traceExec("message:restartEvidence");
        }

        if (isKnownNonRestartHardFailMessage(lower)) {
            nonRestartHardFailArmed = true;
            signalNonRestartHardFailFromHighwayBuilder();
            runtimeFlag("nonRestartEvidence:message");
            traceExec("message:nonRestartEvidence");
        }

        if (restartHandlingArmed && crackedAccountMode.get() && restartRoutineStage == RestartRoutineStage.CrackedAuthLoginLobby) {
            if (lower.contains(LOGIN_PROMPT_MARKER)) {
                crackedLoginPromptSeenThisCycle = true;
                runtimeFlag("crackedLoginPromptSeen");
                traceExec("message:crackedLoginPromptSeen");
            }

            if (lower.contains(CRACKED_LOGIN_SUCCESS_MARKER)) {
                crackedLoginSuccessSeenThisCycle = true;
                if (!awaitingCrackedLoginSuccess && activePostRejoinPathPurpose == PostRejoinPathPurpose.None) {
                    restartRoutineStage = RestartRoutineStage.CrackedLoginLobbyTransferRoutine;
                    info("Detected cracked-account login success before /login wait state. Starting cracked login-lobby path routine.");
                    traceExec("message:crackedLoginSuccessEarly");
                    startPathSequenceForCurrentRestartStage();
                    return;
                }
            }
        }

        if (pendingHighwayBuilderEnableAfterRestore && !restartHandlingArmed && lower.contains(LOGIN_PROMPT_MARKER)) {
            pendingHighwayBuilderEnableAfterRestore = false;
            pendingHighwayBuilderDirection = null;
            pendingHighwayBuilderEnableAtMs = 0L;
            pendingBuilderDirectionProbeFailures = 0;
            info("Detected login prompt while deferred HighwayBuilder enable was pending. Re-arming post-rejoin flow.");
            runtimeFlag("rearmPostRejoinFromLoginPrompt");
            traceExec("message:rearmPostRejoinFromLoginPrompt");
            rearmPostRejoinFlow("login prompt");
            return;
        }

        if (awaitingCrackedLoginSuccess && lower.contains(CRACKED_LOGIN_SUCCESS_MARKER)) {
            awaitingCrackedLoginSuccess = false;
            crackedLoginSuccessSeenThisCycle = true;
            restartRoutineStage = RestartRoutineStage.CrackedLoginLobbyTransferRoutine;
            info("Detected cracked-account login success. Starting cracked login-lobby path routine.");
            traceExec("message:crackedLoginSuccessAwaited");
            startPathSequenceForCurrentRestartStage();
            return;
        }

        if (activePostRejoinPathCommandIndex < 0 || !waitingForPostRejoinCompletionMessage) return;
        if (!lower.contains(BARITONE_PATH_COMPLETE_MARKER)) return;

        waitingForPostRejoinCompletionMessage = false;
        info("Received Baritone completion message for post-rejoin step %d/%d.",
            activePostRejoinPathCommandIndex + 1, postRejoinPathCommands.length);
    }

    @EventHandler
    private void onGameJoined(GameJoinedEvent event) {
        if (!reconnectAutomationEnabled()) return;
        runtimeFlag("onGameJoined");
        traceExec("onGameJoined");
        wasConnectedLastTick = true;
        boolean wasAwaitingPostRejoin = awaitPostRejoinAfterReconnect;
        boolean wasSuppressed = suppressAutoReconnectAfterHardFail;
        clearNonRestartHardFailSignal();
        clearRestartHardFailSignal();
        nonRestartHardFailArmed = false;
        info(
            "Server rejoin detected (GameJoinedEvent). awaitPostRejoin=%s, suppressAutoReconnect=%s, autoRestartHandling=%s",
            wasAwaitingPostRejoin,
            wasSuppressed,
            autoRestartHandlingEnabled()
        );

        if (restartHandlingArmed || awaitPostRejoinAfterReconnect || pendingHighwayBuilderEnableAfterRestore) {
            ensureHighwayBuilderDisabledForRestart("join guard", false);
        }
        tryStartPostRejoinAfterReconnect("join event");

        if (autoRestartHandlingEnabled() && suppressAutoReconnectAfterHardFail) {
            suppressAutoReconnectAfterHardFail = false;
            info("Successful server reconnection detected (join event). AutoReconnect suppression cleared.");
            traceExec("onGameJoined:clearSuppression");
        }
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        if (!reconnectAutomationEnabled()) return;
        runtimeFlag("onGameLeft");
        traceExec("onGameLeft:begin");
        boolean startingNewDisconnectCycle = !restartHandlingArmed && !awaitPostRejoinAfterReconnect && !pendingHighwayBuilderEnableAfterRestore;
        HighwayBuilderTHM builderBeforeDisconnect = Modules.get().get(HighwayBuilderTHM.class);
        boolean builderWasActiveAtDisconnect = builderBeforeDisconnect != null && builderBeforeDisconnect.isActive();
        if (startingNewDisconnectCycle && (postJoinModuleStateCaptured || restartModuleStateSnapshotTaken || timerWasActiveBeforePostJoin || speedWasActiveBeforePostJoin)) {
            postJoinModuleStateCaptured = false;
            timerWasActiveBeforePostJoin = false;
            speedWasActiveBeforePostJoin = false;
            restartModuleStateSnapshotTaken = false;
            runtimeFlag("resetPostJoinModuleSnapshot:newDisconnectCycle");
        }
        unresolvedMainServerDisconnectCandidate = startingNewDisconnectCycle && builderWasActiveAtDisconnect;
        wasConnectedLastTick = false;
        String disconnectScreenReason = readDisconnectedScreenReasonLower();
        pendingDisconnectScreenEvidenceCheck = false;
        pendingDisconnectScreenEvidenceUntilMs = 0L;

        boolean nonRestartHardFail = suppressAutoReconnectAfterHardFail || nonRestartHardFailArmed || consumeNonRestartHardFailSignal();
        if (!nonRestartHardFail && isKnownNonRestartHardFailMessage(disconnectScreenReason)) {
            nonRestartHardFail = true;
            runtimeFlag("nonRestartEvidence:disconnectScreen");
            traceExec("onGameLeft:nonRestartEvidence=disconnect-screen");
        }
        nonRestartHardFailArmed = false;

        if (nonRestartHardFail) {
            unresolvedMainServerDisconnectCandidate = false;
            handleDetectedNonRestartHardFail("onGameLeft");
            return;
        }

        if (consumeRestartHardFailSignal()) {
            armRestartDisconnectEvidence("hb-signal");
            runtimeFlag("restartEvidence:hb-signal");
            traceExec("onGameLeft:restartEvidence=hb-signal");
        }

        String restartEvidence = consumeRestartDisconnectEvidence();

        if (restartHandlingArmed || awaitPostRejoinAfterReconnect || pendingHighwayBuilderEnableAfterRestore) {
            ensureHighwayBuilderDisabledForRestart("game left", false);
        }

        if (pendingHighwayBuilderEnableAfterRestore) {
            unresolvedMainServerDisconnectCandidate = false;
            // During server-transfer reconnects after the public routine, ignore extra disconnects.
            // We only need to wait for reconnect so deferred HighwayBuilder enable can finish.
            awaitPostRejoinAfterReconnect = false;
            clearPostRejoinFlowState();
            pendingHighwayBuilderEnableAtMs = 0L;
            pendingBuilderDirectionProbeFailures = 0;
            info("Disconnect detected during deferred HighwayBuilder enable. Waiting for reconnect without re-running post-rejoin pathing.");
            runtimeFlag("pendingBuilderEnable:disconnectIgnored");
            traceExec("onGameLeft:pendingBuilderDisconnectIgnored");
            return;
        }

        if (restartHandlingArmed) {
            unresolvedMainServerDisconnectCandidate = false;
            if (crackedAccountMode.get() && restartRoutineStage == RestartRoutineStage.CrackedLoginLobbyTransferRoutine && postRejoinStartedForCurrentRestart) {
                restartRoutineStage = RestartRoutineStage.CrackedAwaitingMainLobbyReconnect;
                postRejoinStartedForCurrentRestart = false;
                awaitPostRejoinAfterReconnect = true;
                clearPostRejoinFlowState();
                info("Cracked login-lobby transfer disconnect detected. Awaiting reconnect to main lobby.");
                runtimeFlag("crackedTransition:loginLobbyTransfer->awaitingMainLobbyReconnect");
                traceExec("onGameLeft:crackedTransition:loginLobbyTransfer->awaitingMainLobbyReconnect");
                return;
            }

            if (crackedAccountMode.get() && restartRoutineStage == RestartRoutineStage.CrackedAwaitingMainLobbyReconnect) {
                awaitPostRejoinAfterReconnect = true;
                clearPostRejoinFlowState();
                info("Additional disconnect while awaiting cracked-flow reconnect to main lobby.");
                runtimeFlag("crackedAwaitingMainLobbyReconnect:disconnect");
                traceExec("onGameLeft:crackedAwaitingMainLobbyReconnect:disconnect");
                return;
            }

            if (restartRoutineStage == RestartRoutineStage.MainLobbyTransferRoutine && postRejoinStartedForCurrentRestart) {
                postRejoinStartedForCurrentRestart = false;
                awaitPostRejoinAfterReconnect = true;
                clearPostRejoinFlowState();
                restartRoutineStage = RestartRoutineStage.AwaitingMainServerReconnect;
                info("Main-lobby transfer disconnect detected. Awaiting reconnect to main server.");
                runtimeFlag("mainLobbyTransferDisconnectHandled");
                traceExec("onGameLeft:mainLobbyTransferDisconnectHandled");
                return;
            }

            if (restartRoutineStage == RestartRoutineStage.AwaitingMainServerReconnect) {
                awaitPostRejoinAfterReconnect = true;
                clearPostRejoinFlowState();
                info("Additional disconnect while awaiting reconnect to main server.");
                runtimeFlag("awaitingMainServerReconnect:disconnect");
                traceExec("onGameLeft:awaitingMainServerReconnect:disconnect");
                return;
            }

            // Reconnect loops on some servers can emit additional disconnects while reconnect is still in progress.
            // If post-rejoin already started for this restart cycle, do not start it again on transfer reconnects.
            if (postRejoinStartedForCurrentRestart) {
                awaitPostRejoinAfterReconnect = false;
                clearPostRejoinFlowState();
                info("Additional disconnect detected after post-rejoin already started for this restart. Not re-running routine.");
                runtimeFlag("skipRerunPostRejoinSameRestart");
                traceExec("onGameLeft:skipRerunPostRejoinSameRestart");
            } else {
                // Otherwise keep waiting for first successful reconnect in this restart cycle.
                awaitPostRejoinAfterReconnect = true;
                clearPostRejoinFlowState();
                info("Additional disconnect detected while restart handling is already armed. Waiting for reconnect.");
                traceExec("onGameLeft:restartArmed:awaitReconnect");
            }
            return;
        }

        if (restartEvidence != null) {
            unresolvedMainServerDisconnectCandidate = false;
            if (!restartAutomationAllowed()) {
                runtimeFlag("restartEvidenceIgnored:autoRestartHandlingDisabled");
                traceExec("onGameLeft:restartEvidenceIgnored:autoRestartHandlingDisabled");
                clearRestartAutomationState("restart evidence ignored on disconnect (toggle off)", true, false);
                info("Restart evidence detected, but Automatic Restart Handling is disabled. Ignoring restart automation.");
                return;
            }

            info("Disconnect matched restart evidence (%s). Treating as restart.", restartEvidence);
            traceExec("onGameLeft:restartEvidence=" + restartEvidence);
            handleRestartDetectionTrigger();
            return;
        }

        pendingDisconnectScreenEvidenceCheck = true;
        pendingDisconnectScreenEvidenceUntilMs = System.currentTimeMillis() + DISCONNECT_SCREEN_EVIDENCE_TIMEOUT_MS;
        runtimeFlag("disconnectScreenEvidenceCheck:scheduled");
        info("Disconnect detected without immediate hard-fail evidence. Waiting up to 3.0s for disconnect-screen reason.");
        traceExec("onGameLeft:scheduledDisconnectScreenEvidenceCheck");
    }

    private void handleDetectedNonRestartHardFail(String source) {
        disableAutoReconnectForNonRestartHardFail("HighwayBuilder signaled non-restart hard fail");
        clearRestartDisconnectEvidence();
        unresolvedMainServerDisconnectCandidate = false;
        deferRestartScreenshotUntilReconnect = false;
        deferredRestartScreenshotAfterReconnectPending = false;
        clearPostRejoinFlowState();
        crackedLoginPromptSeenThisCycle = false;
        crackedLoginSuccessSeenThisCycle = false;
        info("Non-restart hard fail detected (%s). Disabling THM Hwy Monitor to prevent restart-login automation on manual rejoin.", source);
        runtimeFlag("nonRestartHardFail:toggleMonitorOff");
        traceExec("nonRestartHardFail:toggleMonitorOff:" + source);
        if (isActive()) toggle();
        traceExec("nonRestartHardFail:handled:" + source);
    }

    private void handlePendingDisconnectScreenEvidenceCheck(boolean connectedNow) {
        if (!pendingDisconnectScreenEvidenceCheck) return;

        if (connectedNow) {
            pendingDisconnectScreenEvidenceCheck = false;
            pendingDisconnectScreenEvidenceUntilMs = 0L;
            unresolvedMainServerDisconnectCandidate = false;
            runtimeFlag("disconnectScreenEvidenceCheck:clearedOnReconnect");
            traceExec("disconnectScreenEvidenceCheck:clearedOnReconnect");
            return;
        }

        long now = System.currentTimeMillis();
        String disconnectScreenReason = readDisconnectedScreenReasonLower();
        if (disconnectScreenReason == null || disconnectScreenReason.isEmpty()) {
            if (now < pendingDisconnectScreenEvidenceUntilMs) return;
            pendingDisconnectScreenEvidenceCheck = false;
            pendingDisconnectScreenEvidenceUntilMs = 0L;
            runtimeFlag("disconnectScreenEvidenceCheck:expired");
            traceExec("disconnectScreenEvidenceCheck:expired");
            handleUnclassifiedMainServerDisconnectFallback("disconnect-screen-timeout");
            return;
        }

        pendingDisconnectScreenEvidenceCheck = false;
        pendingDisconnectScreenEvidenceUntilMs = 0L;

        if (isKnownNonRestartHardFailMessage(disconnectScreenReason)) {
            unresolvedMainServerDisconnectCandidate = false;
            runtimeFlag("nonRestartEvidence:disconnectScreenDeferred");
            traceExec("disconnectScreenEvidenceCheck:nonRestart");
            handleDetectedNonRestartHardFail("disconnect-screen");
            return;
        }

        if (!isRestartHardFailMessage(disconnectScreenReason)) {
            runtimeFlag("disconnectScreenEvidenceCheck:unmatchedReason");
            traceExec("disconnectScreenEvidenceCheck:unmatchedReason");
            handleUnclassifiedMainServerDisconnectFallback("disconnect-screen-unmatched");
            return;
        }

        unresolvedMainServerDisconnectCandidate = false;
        runtimeFlag("restartEvidence:disconnectScreenDeferred");
        traceExec("disconnectScreenEvidenceCheck:restart");
        armRestartDisconnectEvidence("disconnect-screen");
        if (!restartAutomationAllowed()) {
            runtimeFlag("restartEvidenceIgnored:autoRestartHandlingDisabled");
            traceExec("disconnectScreenEvidenceCheck:restartIgnored:autoRestartHandlingDisabled");
            clearRestartAutomationState("restart evidence ignored from disconnect screen (toggle off)", true, false);
            info("Restart evidence detected from disconnect screen, but Automatic Restart Handling is disabled. Ignoring restart automation.");
            return;
        }

        info("Disconnect matched restart evidence (disconnect-screen). Treating as restart.");
        handleRestartDetectionTrigger();
    }

    private void handleUnclassifiedMainServerDisconnectFallback(String source) {
        if (!unresolvedMainServerDisconnectCandidate) return;
        unresolvedMainServerDisconnectCandidate = false;

        if (!restartAutomationAllowed()) {
            runtimeFlag("unclassifiedDisconnectIgnored:autoRestartHandlingDisabled");
            traceExec("unclassifiedDisconnectIgnored:autoRestartHandlingDisabled:" + source);
            info("Unclassified disconnect detected (%s) while THM HighwayBuilder was active, but Automatic Restart Handling is disabled.", source);
            return;
        }

        runtimeFlag("unclassifiedDisconnect:mainServerFallback");
        traceExec("unclassifiedDisconnect:mainServerFallback:" + source);
        info("Unclassified disconnect detected (%s) while THM HighwayBuilder was active. Treating as restart recovery.", source);
        deferRestartScreenshotUntilReconnect = true;
        handleRestartDetectionTrigger();
    }

    private void maybeTakeDeferredRestartScreenshotAfterReconnect(String source) {
        if (!deferredRestartScreenshotAfterReconnectPending) return;

        ensureHighwayBuilderDisabledForRestart("deferred reconnect screenshot", false);
        HighwayBuilderTHM builder = Modules.get().get(HighwayBuilderTHM.class);
        if (builder != null && builder.isActive()) {
            runtimeFlag("deferredRestartScreenshot:builderStillActive");
            traceExec("deferredRestartScreenshot:builderStillActive:" + source);
            return;
        }

        deferredRestartScreenshotAfterReconnectPending = false;
        runtimeFlag("deferredRestartScreenshot:takeAfterReconnect");
        traceExec("deferredRestartScreenshot:takeAfterReconnect:" + source);
        info("Taking deferred restart screenshot after successful reconnect (%s).", source);
        scheduleRestartScreenshot();
    }

    private void scheduleRestartScreenshot() {
        if (restartScreenshotScheduled) return;
        restartScreenshotScheduled = true;
        runtimeFlag("scheduleRestartScreenshot");
        info("Restart detection screen found. Taking screenshot in 2.0s.");

        Thread thread = new Thread(() -> {
            try {
                Thread.sleep(RESTART_SCREENSHOT_DELAY_MS);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }

            if (!isActive()) {
                restartScreenshotScheduled = false;
                return;
            }

            mc.execute(() -> {
                runtimeFlag("takeRestartScreenshot");
                takeRestartScreenshot();
                restartScreenshotScheduled = false;
            });
        }, "thm-restart-screenshot");
        thread.setDaemon(true);
        thread.start();
    }

    private void scheduleRestartReconnect() {
        configureMeteorAutoReconnect(true, true);
    }

    private void configureMeteorAutoReconnect(boolean enableIfNeeded, boolean verboseLogging) {
        AutoReconnect autoReconnect = Modules.get().get(AutoReconnect.class);
        if (autoReconnect == null) {
            if (verboseLogging) warning("Meteor AutoReconnect module not found.");
            return;
        }

        double delaySeconds = restartRejoinDelayMinutes.get() * 60.0;
        autoReconnect.time.set(delaySeconds);

        if (enableIfNeeded && !autoReconnect.isActive()) {
            autoReconnect.toggle();
            if (verboseLogging) {
                if (autoReconnect.isActive()) info("Enabled Meteor AutoReconnect.");
                else warning("Failed to enable Meteor AutoReconnect.");
            }
        } else if (verboseLogging) {
            info("Meteor AutoReconnect is already enabled.");
        }
    }

    private void disableMeteorAutoReconnect(boolean verboseLogging) {
        AutoReconnect autoReconnect = Modules.get().get(AutoReconnect.class);
        if (autoReconnect == null) {
            if (verboseLogging) warning("Meteor AutoReconnect module not found.");
            return;
        }

        if (autoReconnect.isActive()) {
            autoReconnect.toggle();
            if (verboseLogging) {
                if (!autoReconnect.isActive()) info("Disabled Meteor AutoReconnect.");
                else warning("Failed to disable Meteor AutoReconnect.");
            }
        }
    }

    private void disableAutoReconnectForNonRestartHardFail(String reason) {
        suppressAutoReconnectAfterHardFail = true;
        restartHandlingArmed = false;
        postRejoinStartedForCurrentRestart = false;
        restartModuleStateSnapshotTaken = postJoinModuleStateCaptured || timerWasActiveBeforePostJoin || speedWasActiveBeforePostJoin;
        restartRoutineStage = RestartRoutineStage.None;
        activePostRejoinPathPurpose = PostRejoinPathPurpose.None;
        unresolvedMainServerDisconnectCandidate = false;
        deferRestartScreenshotUntilReconnect = false;
        deferredRestartScreenshotAfterReconnectPending = false;
        pendingHighwayBuilderEnableAfterRestore = false;
        pendingHighwayBuilderDirection = null;
        pendingHighwayBuilderEnableAtMs = 0L;
        pendingBuilderDirectionProbeFailures = 0;
        awaitPostRejoinAfterReconnect = false;
        disableMeteorAutoReconnect(true);
        warning("AutoReconnect disabled due to non-restart hard fail: %s", reason);
    }

    private void beginPostRejoinFlowAfterReconnect() {
        if (!restartAutomationAllowed()) {
            clearRestartAutomationState("beginPostRejoinFlowAfterReconnect blocked: toggle off", true, true);
            return;
        }

        pendingPostRejoinActions = true;
        postRejoinActionsAtMs = 0L;
        awaitingCrackedLoginSuccess = false;
        if (crackedAccountMode.get() && restartRoutineStage == RestartRoutineStage.CrackedAuthLoginLobby) {
            crackedLoginPromptSeenThisCycle = false;
            crackedLoginSuccessSeenThisCycle = false;
        }
        resetPostRejoinRetryState();
        resetPostRejoinPathState();
    }

    private void clearPostRejoinFlowState() {
        pendingPostRejoinActions = false;
        postRejoinActionsAtMs = 0L;
        awaitingCrackedLoginSuccess = false;
        resetPostRejoinRetryState();
        resetPostRejoinPathState();
    }

    private boolean autoRestartHandlingEnabled() {
        return reconnectAutomationEnabled();
    }

    private boolean autoRestartScreenshotEnabled() {
        return EXPOSE_RESTART_AUTOMATION_SETTINGS && autoScreenshotOnRestartDetection.get();
    }

    private boolean restartAutomationAllowed() {
        return reconnectAutomationEnabled();
    }

    private boolean reconnectAutomationEnabled() {
        return EXPOSE_RESTART_AUTOMATION_SETTINGS && autoRejoinOnRestartDetection.get();
    }

    private boolean hasRestartAutomationState() {
        return restartHandlingArmed
            || awaitPostRejoinAfterReconnect
            || postRejoinStartedForCurrentRestart
            || pendingPostRejoinActions
            || postRejoinRoutineRetryScheduled
            || awaitingCrackedLoginSuccess
            || restartRoutineStage != RestartRoutineStage.None
            || activePostRejoinPathPurpose != PostRejoinPathPurpose.None
            || pendingHighwayBuilderEnableAfterRestore
            || pendingHighwayBuilderDirection != null
            || pendingHighwayBuilderEnableAtMs != 0L
            || pendingBuilderDirectionProbeFailures != 0
            || postRejoinPathCommands.length != 0
            || activePostRejoinPathCommandIndex != -1
            || nextPostRejoinPathCommandIndex != 0
            || waitingForPostRejoinCommandDelay
            || waitingForPostRejoinPathStart
            || waitingForPostRejoinCompletionMessage
            || postRejoinStepStartCaptured
            || postRejoinPathStartupTicks != 0
            || postRejoinPathTimeoutTicks != 0
            || restartDisconnectEvidenceArmed
            || crackedLoginPromptSeenThisCycle
            || crackedLoginSuccessSeenThisCycle;
    }

    private void resetReconnectAutomationState(boolean disableAutoReconnect) {
        restartScreenshotScheduled = false;
        pendingPostRejoinActions = false;
        postRejoinActionsAtMs = 0L;
        awaitingCrackedLoginSuccess = false;
        postRejoinRoutineRetryScheduled = false;
        postRejoinRoutineRetryAtMs = 0L;
        postRejoinRoutineRetryCount = 0;
        postJoinModuleStateCaptured = false;
        timerWasActiveBeforePostJoin = false;
        speedWasActiveBeforePostJoin = false;
        suppressAutoReconnectAfterHardFail = false;
        awaitPostRejoinAfterReconnect = false;
        nonRestartHardFailArmed = false;
        unresolvedMainServerDisconnectCandidate = false;
        deferRestartScreenshotUntilReconnect = false;
        deferredRestartScreenshotAfterReconnectPending = false;
        pendingDisconnectScreenEvidenceCheck = false;
        pendingDisconnectScreenEvidenceUntilMs = 0L;
        restartHandlingArmed = false;
        postRejoinStartedForCurrentRestart = false;
        restartModuleStateSnapshotTaken = false;
        restartRoutineStage = RestartRoutineStage.None;
        activePostRejoinPathPurpose = PostRejoinPathPurpose.None;
        pendingHighwayBuilderEnableAfterRestore = false;
        pendingHighwayBuilderDirection = null;
        pendingHighwayBuilderEnableAtMs = 0L;
        pendingBuilderDirectionProbeFailures = 0;
        crackedLoginPromptSeenThisCycle = false;
        crackedLoginSuccessSeenThisCycle = false;
        internalTimerSpeedToggleInProgress = false;
        clearRestartDisconnectEvidence();
        clearPostRejoinFlowState();
        clearNonRestartHardFailSignal();
        clearRestartHardFailSignal();

        if (disableAutoReconnect) disableMeteorAutoReconnect(false);
    }

    private void clearRestartAutomationState(String reason, boolean disableAutoReconnect, boolean includeWarning) {
        boolean hadState = hasRestartAutomationState();
        resetReconnectAutomationState(disableAutoReconnect);

        if (!hadState) return;
        runtimeFlag("restartAutomationStateCleared:" + reason);
        traceExec("restartAutomationStateCleared:" + reason);
        if (includeWarning) warning("Restart automation state cleared: %s", reason);
    }

    private void ensureHighwayBuilderDisabledForRestart(String source, boolean verbose) {
        HighwayBuilderTHM builder = Modules.get().get(HighwayBuilderTHM.class);
        if (builder == null || !builder.isActive()) return;

        builder.disable();
        boolean disabled = !builder.isActive();
        runtimeFlag("forceBuilderOff:" + source + ":" + (disabled ? "ok" : "failed"));

        if (disabled) {
            if (verbose) info("Disabled THM HighwayBuilder during restart handling (%s).", source);
        } else {
            warning("Failed to disable THM HighwayBuilder during restart handling (%s).", source);
        }
    }

    private void handleRestartDetectionTrigger() {
        boolean deferScreenshot = deferRestartScreenshotUntilReconnect;
        deferRestartScreenshotUntilReconnect = false;

        if (!restartAutomationAllowed()) {
            deferredRestartScreenshotAfterReconnectPending = false;
            runtimeFlag("restartTriggerBlocked:autoRestartHandlingDisabled");
            traceExec("restartTriggerBlocked:autoRestartHandlingDisabled");
            clearRestartAutomationState("restart trigger blocked (toggle off)", true, false);
            return;
        }

        if (restartHandlingArmed) return;
        traceExec("handleRestartDetectionTrigger");

        // Reconnect handling is delegated to Meteor AutoReconnect.
        restartHandlingArmed = true;
        postRejoinStartedForCurrentRestart = false;
        restartModuleStateSnapshotTaken = postJoinModuleStateCaptured || timerWasActiveBeforePostJoin || speedWasActiveBeforePostJoin;
        restartRoutineStage = crackedAccountMode.get() ? RestartRoutineStage.CrackedAuthLoginLobby : RestartRoutineStage.MainLobbyTransferRoutine;
        activePostRejoinPathPurpose = PostRejoinPathPurpose.None;
        pendingHighwayBuilderEnableAfterRestore = false;
        pendingHighwayBuilderDirection = null;
        pendingHighwayBuilderEnableAtMs = 0L;
        pendingBuilderDirectionProbeFailures = 0;
        suppressAutoReconnectAfterHardFail = false;
        awaitPostRejoinAfterReconnect = true;
        crackedLoginPromptSeenThisCycle = false;
        crackedLoginSuccessSeenThisCycle = false;
        clearPostRejoinFlowState();
        ensureHighwayBuilderDisabledForRestart("restart detection", true);
        info(
            "Restart handling armed. awaitPostRejoin=%s, autoRestartHandling=%s, autoScreenshot=%s",
            awaitPostRejoinAfterReconnect,
            autoRestartHandlingEnabled(),
            autoRestartScreenshotEnabled()
        );
        if (autoRestartHandlingEnabled()) scheduleRestartReconnect();
        if (autoRestartScreenshotEnabled()) {
            if (deferScreenshot) {
                deferredRestartScreenshotAfterReconnectPending = true;
                runtimeFlag("scheduleRestartScreenshot:deferredUntilReconnect");
                info("Restart screenshot deferred until successful reconnect and THM HighwayBuilder-off confirmation.");
            } else {
                deferredRestartScreenshotAfterReconnectPending = false;
                scheduleRestartScreenshot();
            }
        } else {
            deferredRestartScreenshotAfterReconnectPending = false;
        }
        runtimeFlag("restartHandlingArmed");
        traceExec("handleRestartDetectionTrigger:armed");
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        traceExec("onTick");
        handleReconnectAutomationTickLane();

        if (mc.player == null || mc.world == null) return;

        if (recoveryPhase != RecoveryPhase.None) {
            handleRecoveryPhase();
            return;
        }

        if (!autoRecover.get()) return;

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
            return;
        }

        int recoveryGoalY = isPavingMode(builder) ? 120 : 119;
        float recoveryDirectionYaw = resolveRecoveryDirectionYawForInference(builder);

        RecoveryTarget target = determineRecoveryTarget(
            mc.player.getX(),
            mc.player.getZ(),
            recoveryDirectionYaw,
            recoveryGoalY,
            trueCenterMode.get(),
            trackedLine,
            trackedDirection
        );
        if (target == null) return;

        if (trackedLine == null && target.distance() <= ALIGN_TOLERANCE) {
            trackedLine = target.line();
            trackedDirection = target.direction();
        }

        if (trackedLine != null) {
            target = determineRecoveryTarget(
                mc.player.getX(),
                mc.player.getZ(),
                recoveryDirectionYaw,
                recoveryGoalY,
                trueCenterMode.get(),
                trackedLine,
                trackedDirection
            );
            if (target == null) return;
        }

        double yDelta = recoveryYDelta(mc.player.getY(), recoveryGoalY);
        boolean yAligned = Math.abs(yDelta) <= ALIGN_TOLERANCE;
        String yOffset = yAligned ? "" : String.format(Locale.ROOT, ", Y %+.2f", yDelta);

        if (target.distance() <= ALIGN_TOLERANCE && yAligned) return;

        beginRecoveryRoutine(builder, target, yOffset, recoveryDirectionYaw);
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
        if (!reconnectAutomationEnabled()) {
            if (hasRestartAutomationState()) resetReconnectAutomationState(false);
            return;
        }

        boolean connectedNow = isSuccessfullyConnectedToServer();
        handlePendingDisconnectScreenEvidenceCheck(connectedNow);
        boolean connectedTransitionToConnected = connectedNow && !wasConnectedLastTick;
        if (connectedNow != wasConnectedLastTick) {
            runtimeFlag("tickConnectedTransition:" + (wasConnectedLastTick ? "connected->disconnected" : "disconnected->connected"));
        }

        if (connectedTransitionToConnected && suppressAutoReconnectAfterHardFail) {
            suppressAutoReconnectAfterHardFail = false;
            nonRestartHardFailArmed = false;
            clearNonRestartHardFailSignal();
            info("Successful server reconnection detected (tick transition). AutoReconnect suppression cleared.");
            runtimeFlag("clearSuppression:tickConnectedTransition");
        }

        if (connectedNow && restartHandlingArmed && awaitPostRejoinAfterReconnect) {
            info("Server rejoin detected (tick fallback).");
            runtimeFlag("tickFallbackDetected");
            tryStartPostRejoinAfterReconnect("tick fallback");
        }
        wasConnectedLastTick = connectedNow;

        if (consumeNonRestartHardFailSignal()) {
            nonRestartHardFailArmed = true;
            disableAutoReconnectForNonRestartHardFail("HighwayBuilder signaled non-restart hard fail");
        }

        if (restartHandlingArmed || awaitPostRejoinAfterReconnect || pendingHighwayBuilderEnableAfterRestore) {
            ensureHighwayBuilderDisabledForRestart("tick guard", false);
        }

        handleRestartAutomationTick();
        handleDeferredHighwayBuilderEnableAfterRestore();

        // Do not clear non-restart suppression from tick while still on the same connection.
        // Suppression is cleared on GameJoinedEvent after an actual rejoin.
        if (!suppressAutoReconnectAfterHardFail) configureMeteorAutoReconnect(true, false);
        else disableMeteorAutoReconnect(false);
    }

    private float resolveRecoveryDirectionYawForInference(HighwayBuilderTHM builder) {
        if (mc == null || mc.player == null) return Float.NaN;

        float liveYaw = mc.player.getYaw();
        float candidateYaw = liveYaw;
        if (!Float.isNaN(preTickYawSnapshot)) {
            long ageMs = System.currentTimeMillis() - preTickYawSnapshotAtMs;
            if (ageMs >= 0L && ageMs <= 500L) candidateYaw = preTickYawSnapshot;
        }

        if (isLikelyCenterYawOverride(builder, candidateYaw)) {
            if (!Float.isNaN(lastReliableRecoveryYaw)) return lastReliableRecoveryYaw;
            if (builder != null && builder.dir != null) return builder.dir.yaw;
            return liveYaw;
        }

        lastReliableRecoveryYaw = candidateYaw;
        return candidateYaw;
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
        if (recoveryModulesPaused) resumePausedModulesAfterRecovery();
    }

    private void handleExcessiveMisalignment(HighwayBuilderTHM builder, RecoveryTarget target) {
        // Ensure no previously paused modules remain paused before stopping monitor recovery.
        resumePausedModulesAfterRecovery();

        if (builder != null && builder.isActive()) {
            builder.disable();
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
        if (!isDirectionCompatible(line, direction)) direction = inferDirectionForLine(line, playerYaw);

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

    private void handleRestartAutomationTick() {
        long now = System.currentTimeMillis();

        if (postRejoinRoutineRetryScheduled) {
            if (isSuccessfullyConnectedToServer()) {
                postRejoinRoutineRetryScheduled = false;
                postRejoinRoutineRetryAtMs = 0L;
                postRejoinRoutineRetryCount = 0;
                completePostRejoinSuccessFlow();
            } else if (now >= postRejoinRoutineRetryAtMs) {
                runPostRejoinRoutineRetry();
                return;
            }
        }

        if (pendingPostRejoinActions && mc.player != null && mc.world != null) {
            if (postRejoinActionsAtMs == 0L) {
                postRejoinActionsAtMs = now + POST_REJOIN_ACTION_DELAY_MS;
                return;
            }

            if (now < postRejoinActionsAtMs) return;

            captureAndDisablePostJoinModules();
            runPostRejoinActions();
            return;
        }

        handlePostRejoinPathTick();
    }

    private void runPostRejoinActions() {
        traceExec("runPostRejoinActions:begin");
        postRejoinActionsAtMs = 0L;
        pendingPostRejoinActions = false;
        resetPostRejoinPathState();
        awaitingCrackedLoginSuccess = false;

        if (crackedAccountMode.get() && restartRoutineStage == RestartRoutineStage.CrackedAuthLoginLobby) {
            if (crackedLoginSuccessSeenThisCycle) {
                restartRoutineStage = RestartRoutineStage.CrackedLoginLobbyTransferRoutine;
                info("Cracked login already confirmed for this cycle. Skipping /login command and starting path routine.");
                traceExec("runPostRejoinActions:crackedLoginAlreadyConfirmed");
                startPathSequenceForCurrentRestartStage();
                return;
            }

            if (!crackedLoginPromptSeenThisCycle) {
                info("Cracked login prompt not detected yet. Delaying /login command.");
                traceExec("runPostRejoinActions:waitingForLoginPrompt");
                pendingPostRejoinActions = true;
                postRejoinActionsAtMs = System.currentTimeMillis() + POST_REJOIN_ACTION_DELAY_MS;
                return;
            }

            String crackedPassword = configuredCrackedPassword();
            if (crackedPassword.isEmpty()) {
                disconnectAndDisableHwyMonitor("No login password found for cracked account under THM tab, please enter password in field and try again");
                return;
            }
            if (mc.getNetworkHandler() == null) {
                warning("Network handler unavailable during cracked login command. Delaying retry.");
                pendingPostRejoinActions = true;
                postRejoinActionsAtMs = System.currentTimeMillis() + POST_REJOIN_ACTION_DELAY_MS;
                return;
            }
            mc.getNetworkHandler().sendChatCommand("login " + crackedPassword);
            info("Sent cracked-account /login command using THM Addon tab password.");

            awaitingCrackedLoginSuccess = true;
            info("Waiting for cracked-account login confirmation message before pathing.");
            traceExec("runPostRejoinActions:awaitingCrackedLoginSuccess");
            return;
        }

        traceExec("runPostRejoinActions:startPathSequence");
        startPathSequenceForCurrentRestartStage();
    }

    private void startPostRejoinPathSequence(String... commands) {
        if (!BaritoneUtils.IS_AVAILABLE) {
            warning("Post-rejoin pathing skipped: Baritone not available.");
            return;
        }

        IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
        if (baritone == null) {
            warning("Post-rejoin pathing skipped: primary Baritone instance unavailable.");
            return;
        }

        int count = 0;
        for (String command : commands) {
            if (command != null && !command.trim().isEmpty()) count++;
        }
        if (count == 0) return;

        postRejoinPathCommands = new String[count];
        int i = 0;
        for (String command : commands) {
            if (command != null && !command.trim().isEmpty()) {
                postRejoinPathCommands[i] = command.trim();
                i++;
            }
        }

        nextPostRejoinPathCommandIndex = 0;
        activePostRejoinPathCommandIndex = -1;
        waitingForPostRejoinCommandDelay = false;
        postRejoinCommandDelayTicks = 0;
        waitingForPostRejoinPathStart = false;
        waitingForPostRejoinCompletionMessage = false;
        postRejoinPathStartupTicks = 0;
        postRejoinPathTimeoutTicks = 0;
        tryBeginNextPostRejoinPathCommand(baritone);
    }

    private void handlePostRejoinPathTick() {
        if (postRejoinPathCommands.length == 0) return;
        if (mc.player == null || mc.world == null) return;

        if (!BaritoneUtils.IS_AVAILABLE) {
            warning("Post-rejoin pathing aborted: Baritone not available.");
            resetPostRejoinPathState();
            return;
        }

        IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
        if (baritone == null) {
            warning("Post-rejoin pathing aborted: primary Baritone instance unavailable.");
            resetPostRejoinPathState();
            return;
        }

        if (waitingForPostRejoinCommandDelay) {
            if (postRejoinCommandDelayTicks > 0) {
                postRejoinCommandDelayTicks--;
                return;
            }

            if (activePostRejoinPathCommandIndex < 0 || activePostRejoinPathCommandIndex >= postRejoinPathCommands.length) {
                warning("Post-rejoin pathing aborted: invalid step index.");
                resetPostRejoinPathState();
                return;
            }

            String command = postRejoinPathCommands[activePostRejoinPathCommandIndex];
            baritone.getPathingBehavior().cancelEverything();
            if (mc.player != null) {
                postRejoinStepStartX = mc.player.getX();
                postRejoinStepStartZ = mc.player.getZ();
                postRejoinStepStartCaptured = true;
            } else {
                postRejoinStepStartCaptured = false;
            }
            baritone.getCommandManager().execute(command);
            waitingForPostRejoinCommandDelay = false;
            waitingForPostRejoinPathStart = true;
            waitingForPostRejoinCompletionMessage = true;
            postRejoinPathStartupTicks = POST_REJOIN_PATH_STARTUP_TICKS;
            postRejoinPathTimeoutTicks = POST_REJOIN_PATH_TIMEOUT_TICKS;
            info("Executed post-rejoin path step %d/%d: %s", activePostRejoinPathCommandIndex + 1, postRejoinPathCommands.length, command);
            return;
        }

        if (postRejoinPathTimeoutTicks > 0) postRejoinPathTimeoutTicks--;
        if (postRejoinPathTimeoutTicks == 0) {
            baritone.getPathingBehavior().cancelEverything();
            warning("Post-rejoin path step %d/%d timed out waiting for completion message. Aborting remaining steps.",
                activePostRejoinPathCommandIndex + 1, postRejoinPathCommands.length);
            resetPostRejoinPathState();
            return;
        }

        boolean isPathing = baritone.getPathingBehavior().isPathing();

        if (waitingForPostRejoinPathStart) {
            if (isPathing) {
                waitingForPostRejoinPathStart = false;
                info("Post-rejoin path step %d/%d started.", activePostRejoinPathCommandIndex + 1, postRejoinPathCommands.length);
            } else if (postRejoinPathStartupTicks > 0) {
                postRejoinPathStartupTicks--;
            }
        }

        if (waitingForPostRejoinCompletionMessage && hasPostRejoinCoordinateSuccess()) {
            waitingForPostRejoinCompletionMessage = false;
            info("Detected post-rejoin coordinate movement >= %.0f blocks for step %d/%d. Treating as completed.",
                POST_REJOIN_COORD_SUCCESS_DISTANCE, activePostRejoinPathCommandIndex + 1, postRejoinPathCommands.length);
            if (isPathing) {
                baritone.getPathingBehavior().cancelEverything();
                isPathing = false;
            }
        }

        if (waitingForPostRejoinCompletionMessage) return;
        if (isPathing) return;

        info("Post-rejoin path step %d/%d completed.", activePostRejoinPathCommandIndex + 1, postRejoinPathCommands.length);
        tryBeginNextPostRejoinPathCommand(baritone);
    }

    private void tryBeginNextPostRejoinPathCommand(IBaritone baritone) {
        if (nextPostRejoinPathCommandIndex >= postRejoinPathCommands.length) {
            info("Post-rejoin path sequence completed.");

            if (activePostRejoinPathPurpose == PostRejoinPathPurpose.CrackedLoginLobbyTransferRoutine) {
                resetPostRejoinPathState();
                if (restartHandlingArmed) {
                    restartRoutineStage = RestartRoutineStage.CrackedAwaitingMainLobbyReconnect;
                    postRejoinStartedForCurrentRestart = false;
                    awaitPostRejoinAfterReconnect = true;
                    info("Cracked login-lobby transfer routine complete. Awaiting reconnect to main lobby.");
                    runtimeFlag("crackedTransition:loginLobbyTransferComplete->awaitingMainLobbyReconnect");
                }
                return;
            }

            if (activePostRejoinPathPurpose == PostRejoinPathPurpose.MainLobbyTransferRoutine) {
                resetPostRejoinPathState();
                if (restartHandlingArmed) {
                    restartRoutineStage = RestartRoutineStage.AwaitingMainServerReconnect;
                    postRejoinStartedForCurrentRestart = false;
                    awaitPostRejoinAfterReconnect = true;
                    info("Main-lobby transfer routine complete. Awaiting reconnect to main server.");
                    runtimeFlag("mainLobbyTransferComplete:awaitingMainServerReconnect");
                }
                return;
            }

            resetPostRejoinPathState();
            return;
        }

        activePostRejoinPathCommandIndex = nextPostRejoinPathCommandIndex;
        nextPostRejoinPathCommandIndex++;

        waitingForPostRejoinCommandDelay = true;
        postRejoinCommandDelayTicks = POST_REJOIN_PRE_STEP_DELAY_TICKS;
        waitingForPostRejoinPathStart = false;
        waitingForPostRejoinCompletionMessage = false;
        postRejoinPathStartupTicks = 0;
        postRejoinPathTimeoutTicks = 0;
        info("Post-rejoin path step %d/%d scheduled in 2.0s.",
            activePostRejoinPathCommandIndex + 1, postRejoinPathCommands.length);
    }

    private void verifyPostRejoinConnectionOrScheduleRetry() {
        if (isSuccessfullyConnectedToServer()) {
            traceExec("verifyPostRejoinConnectionOrScheduleRetry:connected");
            postRejoinRoutineRetryScheduled = false;
            postRejoinRoutineRetryAtMs = 0L;
            postRejoinRoutineRetryCount = 0;
            completePostRejoinSuccessFlow();
            return;
        }

        traceExec("verifyPostRejoinConnectionOrScheduleRetry:retryScheduled");
        postRejoinRoutineRetryScheduled = true;
        postRejoinRoutineRetryAtMs = System.currentTimeMillis() + POST_REJOIN_RETRY_DELAY_MS;
        warning("Post-join routine finished but server connection is not verified. Retrying in 10.0s.");
    }

    private void runPostRejoinRoutineRetry() {
        traceExec("runPostRejoinRoutineRetry");
        postRejoinRoutineRetryScheduled = false;
        postRejoinRoutineRetryAtMs = 0L;

        if (isSuccessfullyConnectedToServer()) {
            postRejoinRoutineRetryCount = 0;
            completePostRejoinSuccessFlow();
            return;
        }

        postRejoinRoutineRetryCount++;
        if (postRejoinRoutineRetryCount >= POST_REJOIN_MAX_RETRIES) {
            warning("Post-join connection verification failed after %d retries. Disabling THM Hwy Monitor.",
                POST_REJOIN_MAX_RETRIES);
            disableAutoReconnectForNonRestartHardFail("post-join verification retry limit reached");
            resetPostRejoinRetryState();
            if (isActive()) toggle();
            return;
        }

        if (mc.player == null || mc.world == null || mc.getNetworkHandler() == null) {
            warning("Still not connected. Retrying reconnect now (%d/%d).",
                postRejoinRoutineRetryCount, POST_REJOIN_MAX_RETRIES);
            scheduleRestartReconnect();
            return;
        }

        info("Still not verified as connected. Retrying post-join path routine now (%d/%d).",
            postRejoinRoutineRetryCount, POST_REJOIN_MAX_RETRIES);
        startPathSequenceForCurrentRestartStage();
    }

    private String[] postRejoinRoutineCommands() {
        return new String[] {POST_REJOIN_PORTAL_COMMAND};
    }

    private void startPathSequenceForCurrentRestartStage() {
        if (!restartAutomationAllowed()) {
            clearRestartAutomationState("startPathSequenceForCurrentRestartStage blocked: toggle off", true, false);
            return;
        }

        traceExec("startPathSequenceForCurrentRestartStage:stage=" + restartRoutineStage);
        if (crackedAccountMode.get() && restartRoutineStage == RestartRoutineStage.CrackedAuthLoginLobby) {
            warning("Cracked login-lobby transfer routine cannot start before login success.");
            traceExec("startPathSequenceForCurrentRestartStage:blockedCrackedAuth");
            return;
        }

        if (crackedAccountMode.get() && restartRoutineStage == RestartRoutineStage.CrackedLoginLobbyTransferRoutine) {
            activePostRejoinPathPurpose = PostRejoinPathPurpose.CrackedLoginLobbyTransferRoutine;
        } else {
            if (restartRoutineStage == RestartRoutineStage.AwaitingMainServerReconnect) {
                warning("Main-lobby transfer routine already completed; waiting for main-server reconnect.");
                traceExec("startPathSequenceForCurrentRestartStage:blockedAwaitingMainServerReconnect");
                return;
            }

            activePostRejoinPathPurpose = PostRejoinPathPurpose.MainLobbyTransferRoutine;
            if (restartRoutineStage == RestartRoutineStage.CrackedAwaitingMainLobbyReconnect) {
                restartRoutineStage = RestartRoutineStage.MainLobbyTransferRoutine;
                postRejoinStartedForCurrentRestart = false;
                info("Cracked flow reached main lobby. Starting main-lobby transfer routine.");
                runtimeFlag("crackedTransition:awaitingMainLobbyReconnect->mainLobbyTransferRoutine");
            } else if (restartRoutineStage == RestartRoutineStage.None) {
                restartRoutineStage = RestartRoutineStage.MainLobbyTransferRoutine;
            }
        }

        startPostRejoinPathSequence(postRejoinRoutineCommands());
    }

    private void tryStartPostRejoinAfterReconnect(String source) {
        if (!restartAutomationAllowed()) {
            clearRestartAutomationState("tryStartPostRejoinAfterReconnect blocked: toggle off (" + source + ")", true, false);
            return;
        }

        if (!restartHandlingArmed) return;
        if (!awaitPostRejoinAfterReconnect) return;
        traceExec("tryStartPostRejoinAfterReconnect:" + source + ":stage=" + restartRoutineStage);
        maybeTakeDeferredRestartScreenshotAfterReconnect(source);

        // Resume Timer/Speed state on each successful reconnect hop before any next-stage routine work.
        restorePostJoinModuleStatesIfNeeded();

        if (crackedAccountMode.get() && restartRoutineStage == RestartRoutineStage.CrackedAwaitingMainLobbyReconnect) {
            restartRoutineStage = RestartRoutineStage.MainLobbyTransferRoutine;
            postRejoinStartedForCurrentRestart = false;
            runtimeFlag("crackedTransition:awaitingMainLobbyReconnect->mainLobbyTransferRoutine");
        }

        if (restartRoutineStage == RestartRoutineStage.AwaitingMainServerReconnect) {
            awaitPostRejoinAfterReconnect = false;
            postRejoinStartedForCurrentRestart = false;
            info("Main-server reconnect detected (%s). Finalizing restart recovery.", source);
            runtimeFlag("mainServerReconnectDetected:" + source);
            completePostRejoinSuccessFlow();
            return;
        }

        if (postRejoinStartedForCurrentRestart) {
            runtimeFlag("tryStartPostRejoinAfterReconnectSkippedAlreadyStarted");
            return;
        }

        ensureHighwayBuilderDisabledForRestart("post-rejoin start", false);
        awaitPostRejoinAfterReconnect = false;
        postRejoinStartedForCurrentRestart = true;
        beginPostRejoinFlowAfterReconnect();
        info("Successful server reconnection detected (%s). Starting post-rejoin routine.", source);
        runtimeFlag("tryStartPostRejoinAfterReconnect:" + source);
    }

    private void rearmPostRejoinFlow(String source) {
        if (!restartAutomationAllowed()) {
            clearRestartAutomationState("rearmPostRejoinFlow blocked: toggle off (" + source + ")", true, false);
            return;
        }

        traceExec("rearmPostRejoinFlow:" + source);
        restartHandlingArmed = true;
        postRejoinStartedForCurrentRestart = false;
        restartModuleStateSnapshotTaken = postJoinModuleStateCaptured || timerWasActiveBeforePostJoin || speedWasActiveBeforePostJoin;
        restartRoutineStage = crackedAccountMode.get() ? RestartRoutineStage.CrackedAuthLoginLobby : RestartRoutineStage.MainLobbyTransferRoutine;
        activePostRejoinPathPurpose = PostRejoinPathPurpose.None;
        awaitPostRejoinAfterReconnect = true;
        crackedLoginPromptSeenThisCycle = false;
        crackedLoginSuccessSeenThisCycle = false;
        clearPostRejoinFlowState();
        runtimeFlag("rearmPostRejoinFlow:" + source);
        tryStartPostRejoinAfterReconnect(source);
    }

    private void completePostRejoinSuccessFlow() {
        if (!restartAutomationAllowed()) {
            clearRestartAutomationState("completePostRejoinSuccessFlow blocked: toggle off", true, false);
            return;
        }

        info("Post-join connection verification succeeded.");
        traceExec("completePostRejoinSuccessFlow:begin");
        restartHandlingArmed = false;
        postRejoinStartedForCurrentRestart = false;
        restartRoutineStage = RestartRoutineStage.None;
        activePostRejoinPathPurpose = PostRejoinPathPurpose.None;
        crackedLoginPromptSeenThisCycle = false;
        crackedLoginSuccessSeenThisCycle = false;
        runtimeFlag("completePostRejoinSuccessFlow");

        HorizontalDirection workingDirection = determinePostRejoinWorkingDirection();
        if (workingDirection == null) traceExec("completePostRejoinSuccessFlow:direction=null");

        if (!enableHighwayBuilderOnRestart.get()) {
            pendingHighwayBuilderEnableAfterRestore = false;
            pendingHighwayBuilderDirection = null;
            pendingHighwayBuilderEnableAtMs = 0L;
            pendingBuilderDirectionProbeFailures = 0;
            info("Enable Highway Builder on restart is disabled. Skipping THM HighwayBuilder activation.");
            return;
        }

        pendingHighwayBuilderEnableAfterRestore = true;
        pendingHighwayBuilderDirection = workingDirection;
        pendingHighwayBuilderEnableAtMs = 0L;
        pendingBuilderDirectionProbeFailures = 0;
        if (workingDirection == null) {
            info("Post-rejoin routine complete. Highway direction is not ready yet; THM HighwayBuilder enable is armed and will retry after Timer/Speed restore.");
            runtimeFlag("pendingBuilderEnable:directionPending");
        } else {
            info("Post-rejoin routine complete. THM HighwayBuilder will be enabled 6.0s after Timer/Speed restore.");
        }
        runtimeFlag("pendingBuilderEnable:armed");
        traceExec("completePostRejoinSuccessFlow:pendingBuilderEnableArmed");
    }

    private void handleDeferredHighwayBuilderEnableAfterRestore() {
        if (!restartAutomationAllowed()) {
            if (pendingHighwayBuilderEnableAfterRestore) {
                clearRestartAutomationState("deferred builder enable blocked: toggle off", true, false);
            }
            return;
        }

        if (!pendingHighwayBuilderEnableAfterRestore) return;
        traceExec("handleDeferredHighwayBuilderEnableAfterRestore:pending");
        if (!isSuccessfullyConnectedToServer()) {
            // Require a full connected delay window; do not carry a previous timer across reconnects.
            pendingHighwayBuilderEnableAtMs = 0L;
            traceExec("handleDeferredHighwayBuilderEnableAfterRestore:notConnected");
            return;
        }
        if (!isSupportedVanillaDimension()) {
            pendingHighwayBuilderEnableAtMs = 0L;
            runtimeFlag("pendingBuilderEnable:unsupportedDimension");
            traceExec("handleDeferredHighwayBuilderEnableAfterRestore:unsupportedDimension");
            return;
        }

        if (pendingHighwayBuilderEnableAtMs == 0L) {
            pendingHighwayBuilderEnableAtMs = System.currentTimeMillis() + BUILDER_ENABLE_DELAY_MS;
            runtimeFlag("pendingBuilderEnable:delayStarted");
            traceExec("handleDeferredHighwayBuilderEnableAfterRestore:delayStarted");
            return;
        }
        if (System.currentTimeMillis() < pendingHighwayBuilderEnableAtMs) return;

        HorizontalDirection direction = pendingHighwayBuilderDirection;

        if (!enableHighwayBuilderOnRestart.get()) {
            pendingHighwayBuilderEnableAfterRestore = false;
            pendingHighwayBuilderDirection = null;
            pendingHighwayBuilderEnableAtMs = 0L;
            pendingBuilderDirectionProbeFailures = 0;
            info("Enable Highway Builder on restart was disabled before deferred activation.");
            return;
        }

        if (direction == null) direction = determinePostRejoinWorkingDirection();
        if (!isActive()) {
            pendingHighwayBuilderEnableAfterRestore = false;
            pendingHighwayBuilderDirection = null;
            pendingHighwayBuilderEnableAtMs = 0L;
            pendingBuilderDirectionProbeFailures = 0;
            warning("THM Hwy Monitor is not active. Skipping THM HighwayBuilder activation.");
            return;
        }
        if (direction == null) {
            pendingBuilderDirectionProbeFailures++;
            pendingHighwayBuilderEnableAtMs = 0L;
            runtimeFlag("pendingBuilderEnable:directionNotReady:" + pendingBuilderDirectionProbeFailures);
            traceExec("handleDeferredHighwayBuilderEnableAfterRestore:directionNull:" + pendingBuilderDirectionProbeFailures);

            if (!restartHandlingArmed && pendingBuilderDirectionProbeFailures >= PENDING_BUILDER_DIRECTION_FAIL_REARM_THRESHOLD) {
                pendingHighwayBuilderEnableAfterRestore = false;
                pendingHighwayBuilderDirection = null;
                pendingHighwayBuilderEnableAtMs = 0L;
                pendingBuilderDirectionProbeFailures = 0;
                warning("Deferred HighwayBuilder direction checks failed repeatedly. Aborting deferred activation without re-arming login routines.");
                runtimeFlag("pendingBuilderEnable:directionNotReadyAbort");
                traceExec("handleDeferredHighwayBuilderEnableAfterRestore:abortFromDirectionNotReady");
            }
            return;
        }
        pendingBuilderDirectionProbeFailures = 0;

        pendingHighwayBuilderEnableAfterRestore = false;
        pendingHighwayBuilderDirection = null;
        pendingHighwayBuilderEnableAtMs = 0L;
        applyDirectionAndEnableHighwayBuilder(direction);
        info("Deferred THM HighwayBuilder activation completed after module restore and 6.0s delay.");
        traceExec("handleDeferredHighwayBuilderEnableAfterRestore:enabledBuilder");
    }

    private void applyDirectionAndEnableHighwayBuilder(HorizontalDirection workingDirection) {
        if (!enableHighwayBuilderOnRestart.get()) {
            info("Enable Highway Builder on restart is disabled. Skipping THM HighwayBuilder activation.");
            return;
        }

        applyPostRejoinYaw(workingDirection);
        info("Post-rejoin direction selected: %s.", workingDirection.name);

        HighwayBuilderTHM builder = Modules.get().get(HighwayBuilderTHM.class);
        if (builder == null) {
            warning("THM HighwayBuilder module not found, cannot resume.");
            return;
        }

        if (!builder.isActive()) {
            builder.toggle();
            if (builder.isActive()) info("Resumed THM HighwayBuilder after post-rejoin checks.");
            else warning("Failed to resume THM HighwayBuilder after post-rejoin checks.");
        } else {
            info("THM HighwayBuilder already active after post-rejoin checks.");
        }
    }

    private HorizontalDirection determinePostRejoinWorkingDirection() {
        if (mc.player == null || mc.world == null) return null;
        traceExec("determinePostRejoinWorkingDirection:begin");
        int probeDistance = postRejoinAxisProbeDistanceForCurrentAttempt();
        traceExec("determinePostRejoinWorkingDirection:probeDistance=" + probeDistance + ":retryIndex=" + (pendingBuilderDirectionProbeFailures + 1));

        HorizontalDirection[] axisDirections = resolvePostRejoinAxisDirections();
        if (axisDirections == null) {
            warning("Unable to resolve highway axis after rejoin. Using current facing direction.");
            traceExec("determinePostRejoinWorkingDirection:axisUnknownFallbackFacing");
            return HorizontalDirection.get(mc.player.getYaw());
        }

        HorizontalDirection dirA = axisDirections[0];
        HorizontalDirection dirB = axisDirections[1];
        HighwayBuilderTHM builder = Modules.get().get(HighwayBuilderTHM.class);
        boolean pavingSelected = builder != null && isPavingMode(builder);

        if (pavingSelected) {
            boolean dirAObsidianY119 = isObsidianAtAxisProbe(dirA, probeDistance, 119);
            boolean dirBObsidianY119 = isObsidianAtAxisProbe(dirB, probeDistance, 119);
            traceExec("determinePostRejoinWorkingDirection:obsidianMode:dirA119=" + dirAObsidianY119 + ":dirB119=" + dirBObsidianY119);
            if (dirAObsidianY119 != dirBObsidianY119) {
                HorizontalDirection selected = dirAObsidianY119 ? dirB : dirA;
                traceExec("determinePostRejoinWorkingDirection:obsidianMode:selected=" + selected.name);
                return selected;
            }

            warning("Post-rejoin obsidian direction checks at Y=119 were ambiguous. Deferring HighwayBuilder enable.");
            traceExec("determinePostRejoinWorkingDirection:obsidianMode:ambiguous");
            return null;
        }

        boolean dirAAirY122 = isAirAtAxisProbe(dirA, probeDistance, 122);
        boolean dirBAirY122 = isAirAtAxisProbe(dirB, probeDistance, 122);
        traceExec("determinePostRejoinWorkingDirection:airMode:dirA122=" + dirAAirY122 + ":dirB122=" + dirBAirY122);
        if (dirAAirY122 != dirBAirY122) {
            HorizontalDirection selected = dirAAirY122 ? dirB : dirA;
            traceExec("determinePostRejoinWorkingDirection:airMode:selected=" + selected.name);
            return selected;
        }

        if (dirAAirY122 && dirBAirY122) {
            warning("Post-rejoin direction not ready yet: both axis directions are air at Y=122. Deferring HighwayBuilder enable.");
            traceExec("determinePostRejoinWorkingDirection:airMode:bothAir");
            return null;
        }

        warning("Post-rejoin direction checks were ambiguous. Deferring HighwayBuilder enable.");
        traceExec("determinePostRejoinWorkingDirection:airMode:ambiguous");
        return null;
    }

    private int postRejoinAxisProbeDistanceForCurrentAttempt() {
        int retryIndex = Math.max(1, pendingBuilderDirectionProbeFailures + 1);
        return POST_REJOIN_AXIS_PROBE_DISTANCE * retryIndex;
    }

    private HorizontalDirection[] resolvePostRejoinAxisDirections() {
        WorkLine line = trackedLine;
        if (line == null && mc.player != null) {
            double centerOffset = trueCenterMode.get() ? 0.5 : 0.0;
            line = nearestWorkLine(mc.player.getX(), mc.player.getZ(), centerOffset, trueCenterMode.get());
        }
        if (line == null) return null;

        return switch (line) {
            case CardinalNS -> new HorizontalDirection[] {HorizontalDirection.South, HorizontalDirection.North};
            case CardinalEW -> new HorizontalDirection[] {HorizontalDirection.East, HorizontalDirection.West};
            case DiagonalNWSE -> new HorizontalDirection[] {HorizontalDirection.NorthWest, HorizontalDirection.SouthEast};
            case DiagonalNESW -> new HorizontalDirection[] {HorizontalDirection.NorthEast, HorizontalDirection.SouthWest};
        };
    }

    private boolean isObsidianAtAxisProbe(HorizontalDirection direction, int distance, int y) {
        if (mc.player == null || mc.world == null) return false;
        BlockPos probe = BlockPos.ofFloored(
            mc.player.getX() + (direction.offsetX * distance),
            y,
            mc.player.getZ() + (direction.offsetZ * distance)
        );
        return mc.world.getBlockState(probe).getBlock() == Blocks.OBSIDIAN;
    }

    private boolean isAirAtAxisProbe(HorizontalDirection direction, int distance, int y) {
        if (mc.player == null || mc.world == null) return false;
        BlockPos probe = BlockPos.ofFloored(
            mc.player.getX() + (direction.offsetX * distance),
            y,
            mc.player.getZ() + (direction.offsetZ * distance)
        );
        return mc.world.getBlockState(probe).isAir();
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

    private void disconnectAndDisableHwyMonitor(String reason) {
        disableAutoReconnectForNonRestartHardFail(reason);
        warning(reason);
        awaitingCrackedLoginSuccess = false;

        if (mc != null && mc.getNetworkHandler() != null && mc.getNetworkHandler().getConnection() != null) {
            mc.getNetworkHandler().getConnection().disconnect(Text.literal(reason));
        }

        pendingPostRejoinActions = false;
        postRejoinActionsAtMs = 0L;
        resetPostRejoinRetryState();
        resetPostRejoinPathState();

        if (isActive()) toggle();
    }

    private void captureAndDisablePostJoinModules() {
        Timer timer = Modules.get().get(Timer.class);
        Speed speed = Modules.get().get(Speed.class);
        timerWasActiveBeforePostJoin = timer != null && timer.isActive();
        speedWasActiveBeforePostJoin = speed != null && speed.isActive();
        postJoinModuleStateCaptured = timerWasActiveBeforePostJoin || speedWasActiveBeforePostJoin;
        restartModuleStateSnapshotTaken = postJoinModuleStateCaptured;
        runtimeFlag("captureInitialPostJoinModuleSnapshot");

        internalTimerSpeedToggleInProgress = true;
        try {
            if (timerWasActiveBeforePostJoin && timer != null && timer.isActive()) {
                timer.toggle();
                info("Disabled Timer for post-join routine.");
            }

            if (speedWasActiveBeforePostJoin && speed != null && speed.isActive()) {
                speed.toggle();
                info("Disabled Speed for post-join routine.");
            }
        } finally {
            internalTimerSpeedToggleInProgress = false;
        }
    }

    private void captureAndDisablePostJoinModulesForDisconnectSafety() {
        Timer timer = Modules.get().get(Timer.class);
        Speed speed = Modules.get().get(Speed.class);
        boolean changed = false;

        if (!restartModuleStateSnapshotTaken) {
            if (!postJoinModuleStateCaptured) {
                timerWasActiveBeforePostJoin = timer != null && timer.isActive();
                speedWasActiveBeforePostJoin = speed != null && speed.isActive();
                runtimeFlag("captureInitialPostJoinModuleSnapshot:disconnectSafety");
            } else {
                runtimeFlag("captureInitialPostJoinModuleSnapshot:disconnectSafety:preserved");
            }
            restartModuleStateSnapshotTaken = true;
        }

        if (timer != null && timer.isActive()) {
            timer.toggle();
            info("Disabled Timer for disconnect safety.");
            changed = true;
        }

        if (speed != null && speed.isActive()) {
            speed.toggle();
            info("Disabled Speed for disconnect safety.");
            changed = true;
        }

        postJoinModuleStateCaptured = timerWasActiveBeforePostJoin || speedWasActiveBeforePostJoin;
        if (changed) runtimeFlag("captureAndDisablePostJoinModulesForDisconnectSafety");
    }

    private void restorePostJoinModuleStatesIfNeeded() {
        if (!postJoinModuleStateCaptured) return;
        if (!isSuccessfullyConnectedToServer()) {
            runtimeFlag("restorePostJoinModuleStatesDeferred:notConnected");
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
            runtimeFlag(String.format(
                Locale.ROOT,
                "restorePostJoinModuleStatesDeferred:timerRestored=%s,speedRestored=%s",
                timerRestored,
                speedRestored
            ));
            return;
        }

        postJoinModuleStateCaptured = false;
        timerWasActiveBeforePostJoin = false;
        speedWasActiveBeforePostJoin = false;
        restartModuleStateSnapshotTaken = false;
        runtimeFlag("restorePostJoinModuleStatesComplete");
    }

    private boolean isSuccessfullyConnectedToServer() {
        return mc != null
            && mc.player != null
            && mc.world != null
            && mc.getNetworkHandler() != null
            && !(mc.currentScreen instanceof DisconnectedScreen);
    }

    private boolean isSupportedVanillaDimension() {
        if (mc == null || mc.world == null) return false;
        RegistryKey<World> key = mc.world.getRegistryKey();
        return key == World.OVERWORLD || key == World.NETHER || key == World.END;
    }

    private boolean hasPostRejoinCoordinateSuccess() {
        if (!postRejoinStepStartCaptured || mc.player == null) return false;
        double dx = mc.player.getX() - postRejoinStepStartX;
        double dz = mc.player.getZ() - postRejoinStepStartZ;
        return Math.hypot(dx, dz) >= POST_REJOIN_COORD_SUCCESS_DISTANCE;
    }

    private void resetPostRejoinRetryState() {
        postRejoinRoutineRetryScheduled = false;
        postRejoinRoutineRetryAtMs = 0L;
        postRejoinRoutineRetryCount = 0;
    }

    private void resetPostRejoinPathState() {
        postRejoinPathCommands = new String[0];
        nextPostRejoinPathCommandIndex = 0;
        activePostRejoinPathCommandIndex = -1;
        activePostRejoinPathPurpose = PostRejoinPathPurpose.None;
        waitingForPostRejoinCommandDelay = false;
        postRejoinCommandDelayTicks = 0;
        waitingForPostRejoinPathStart = false;
        waitingForPostRejoinCompletionMessage = false;
        postRejoinStepStartCaptured = false;
        postRejoinStepStartX = 0.0;
        postRejoinStepStartZ = 0.0;
        postRejoinPathStartupTicks = 0;
        postRejoinPathTimeoutTicks = 0;
    }

    private static String configuredCrackedPassword() {
        THMSystem system = THMSystem.get();
        if (system == null) return "";

        // Login routine is currently disabled; keep the original call for future re-enable.
        // String value = system.getCrackedPassword();
        String value = "";
        if (value == null) return "";

        value = value.trim();
        if (value.startsWith("/")) value = value.substring(1).trim();

        String lower = value.toLowerCase(Locale.ROOT);
        if (lower.startsWith("login ")) value = value.substring(6).trim();

        return value;
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
        if (pendingCorrectionTarget == null || mc.player == null) return;

        float referenceYaw = Float.isNaN(recoveryYawBeforeMove) ? mc.player.getYaw() : recoveryYawBeforeMove;
        float yaw = closestParallelYawForSegment(
            referenceYaw,
            pendingCorrectionTarget.highway(),
            pendingCorrectionTarget.direction(),
            pendingCorrectionTarget.line()
        );
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

    private void startRuntimeWatchdogIfNeeded() {
        if (!RUNTIME_WATCHDOG_LOG_ENABLED || runtimeWatchdogRunning) return;
        runtimeWatchdogRunning = true;
        lastRuntimeWatchdogState = "";
        runtimeWatchdogThread = new Thread(() -> {
            runtimeFlag("runtimeWatchdogStart");
            while (runtimeWatchdogRunning) {
                String snapshot = runtimeStateSnapshot();
                if (!snapshot.equals(lastRuntimeWatchdogState)) {
                    lastRuntimeWatchdogState = snapshot;
                    runtimeFlag("runtimeState:" + snapshot);
                }

                try {
                    Thread.sleep(1000L);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            runtimeFlag("runtimeWatchdogStop");
        }, "thm-hwymonitor-runtime-watchdog");
        runtimeWatchdogThread.setDaemon(true);
        runtimeWatchdogThread.start();
    }

    private void stopRuntimeWatchdog() {
        runtimeWatchdogRunning = false;
        if (runtimeWatchdogThread != null) {
            runtimeWatchdogThread.interrupt();
            runtimeWatchdogThread = null;
        }
        lastRuntimeWatchdogState = "";
    }

    private String runtimeStateSnapshot() {
        if (mc == null) return "mc=null";

        String screen = mc.currentScreen == null ? "none" : mc.currentScreen.getClass().getSimpleName();
        return String.format(
            Locale.ROOT,
            "active=%s connected=%s player=%s world=%s net=%s screen=%s restartArmed=%s stage=%s stageStarted=%s moduleSnapshot=%s awaitPost=%s pendingPost=%s pendingBuilderEnable=%s pendingBuilderAtMs=%s pathPurpose=%s crackedPrompt=%s crackedSuccess=%s restartEvidence=%s traceSeq=%s",
            isActive(),
            isSuccessfullyConnectedToServer(),
            mc.player != null,
            mc.world != null,
            mc.getNetworkHandler() != null,
            screen,
            restartHandlingArmed,
            restartRoutineStage,
            postRejoinStartedForCurrentRestart,
            restartModuleStateSnapshotTaken,
            awaitPostRejoinAfterReconnect,
            pendingPostRejoinActions,
            pendingHighwayBuilderEnableAfterRestore,
            pendingHighwayBuilderEnableAtMs,
            activePostRejoinPathPurpose,
            crackedLoginPromptSeenThisCycle,
            crackedLoginSuccessSeenThisCycle,
            restartDisconnectEvidenceArmed,
            executionTraceCounter
        );
    }

    private void traceExec(String step) {
        if (!EXECUTION_TRACE_LOG_ENABLED) return;
        executionTraceCounter++;
        runtimeFlag("exec#" + executionTraceCounter + ":" + step);
    }

    private void runtimeFlag(String flag) {
        if (!RUNTIME_WATCHDOG_LOG_ENABLED) return;

        Path logPath = resolveRuntimeFlagLogPath();
        String line = String.format(
            Locale.ROOT,
            "%s | %s%n",
            LocalDateTime.now().format(RUNTIME_FLAG_TS),
            flag
        );

        try {
            Path parent = logPath.getParent();
            if (parent != null) Files.createDirectories(parent);
            Files.writeString(
                logPath,
                line,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND
            );
        } catch (IOException ignored) {
            // Debug logging should never interrupt module behavior.
        }
    }

    private Path resolveRuntimeFlagLogPath() {
        if (mc != null && mc.runDirectory != null) {
            return mc.runDirectory.toPath().resolve("thm-hwymonitor-runtime-flags.log");
        }
        return Path.of("thm-hwymonitor-runtime-flags.log").toAbsolutePath();
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

    private enum RestartRoutineStage {
        None,
        CrackedAuthLoginLobby,
        CrackedLoginLobbyTransferRoutine,
        CrackedAwaitingMainLobbyReconnect,
        MainLobbyTransferRoutine,
        AwaitingMainServerReconnect
    }

    private enum PostRejoinPathPurpose {
        None,
        CrackedLoginLobbyTransferRoutine,
        MainLobbyTransferRoutine
    }

    private enum WorkLine {
        CardinalNS,
        CardinalEW,
        DiagonalNWSE,
        DiagonalNESW
    }
}
