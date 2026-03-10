package xyz.thm.addon.modules;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.pathing.goals.GoalBlock;
import baritone.api.utils.Rotation;
import it.unimi.dsi.fastutil.Pair;
import it.unimi.dsi.fastutil.objects.ObjectObjectImmutablePair;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.game.GameJoinedEvent;
import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WVerticalList;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.pathing.BaritoneUtils;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.macros.Macro;
import meteordevelopment.meteorclient.systems.macros.Macros;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.movement.speed.Speed;
import meteordevelopment.meteorclient.systems.modules.misc.AutoReconnect;
import meteordevelopment.meteorclient.systems.modules.world.Timer;
import meteordevelopment.meteorclient.utils.misc.HorizontalDirection;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Blocks;
import net.minecraft.client.gui.screen.DisconnectedScreen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.registry.RegistryKey;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import xyz.thm.addon.THMAddon;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_F7;

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
    private static final int DEBUG_RESTART_FALLBACK_DELAY_MS = 2000;
    private static final String DEBUG_RESTART_REASON = "[THM-HwyMonitor] Debug Restart Trigger";
    private static final int DEBUG_NON_RESTART_HARD_FAIL_DELAY_MS = 3000;
    private static final String DEBUG_NON_RESTART_HARD_FAIL_REASON = "[THM-HighwayBuilder] Debug Non-Restart Hard Fail";
    private static final String BARITONE_PATH_COMPLETE_MARKER = "pathing complete";
    private static final String CRACKED_LOGIN_SUCCESS_MARKER = "you are now logged in!";
    private static final String LOGIN_PROMPT_MARKER = "please login with the command: /login";
    private static final AtomicBoolean NON_RESTART_HARD_FAIL_SIGNAL = new AtomicBoolean(false);
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

    private final Setting<Boolean> autoRejoinOnRestartDetection = sgGeneral.add(new BoolSetting.Builder()
        .name("automatic-restart-handling")
        .description("Handles restart recovery loop: reconnects, runs login flow, and executes post-join pathing.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> restartRejoinDelayMinutes = sgGeneral.add(new IntSetting.Builder()
        .name("restart-rejoin-delay-minutes")
        .description("Delay in minutes applied to Meteor AutoReconnect.")
        .defaultValue(15)
        .range(1, 240)
        .sliderRange(1, 60)
        .visible(autoRejoinOnRestartDetection::get)
        .build()
    );

    private final Setting<Boolean> crackedAccountMode = sgGeneral.add(new BoolSetting.Builder()
        .name("cracked-account-mode")
        .description("Marks this account as cracked for restart-login flow selection.")
        .defaultValue(false)
        .visible(autoRejoinOnRestartDetection::get)
        .build()
    );

    private final Setting<Boolean> enableHighwayBuilderOnRestart = sgGeneral.add(new BoolSetting.Builder()
        .name("enable-highway-builder-on-restart")
        .description("Enable THM HighwayBuilder after successful restart rejoin flow.")
        .defaultValue(true)
        .visible(autoRejoinOnRestartDetection::get)
        .build()
    );

    private final Setting<Boolean> debugRuntimeFlagLog = sgGeneral.add(new BoolSetting.Builder()
        .name("debug-runtime-flag-log")
        .description("Writes THM Hwy Monitor lifecycle flags to a file for reconnect/disconnect diagnosis.")
        .defaultValue(false)
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
    // Legacy cache fields retained for validation testing.
    // Currently only written by cacheCurrentServerIfConnected() and read by resolveLastServerConnection(),
    // and that resolver is not used in the active flow.
    private Pair<ServerAddress, ServerInfo> lastServerConnection;
    private ServerAddress lastServerAddress;
    private ServerInfo lastServerInfo;
    private boolean debugRestartDisconnectPending;
    private boolean debugRestartFallbackScheduled;
    private long debugRestartFallbackAtMs;
    private boolean debugNonRestartHardFailPending;
    private boolean suppressAutoReconnectAfterHardFail;
    private boolean awaitPostRejoinAfterReconnect;
    private boolean nonRestartHardFailArmed;
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
    private volatile boolean runtimeWatchdogRunning;
    private Thread runtimeWatchdogThread;
    private String lastRuntimeWatchdogState = "";

    public THMHwyMonitor() {
        super(THMAddon.MAIN, "THM Hwy Monitor", "Monitors alignment and recovers HighwayBuilder from drift.");
    }

    public static void signalNonRestartHardFailFromHighwayBuilder() {
        NON_RESTART_HARD_FAIL_SIGNAL.set(true);
    }

    private static boolean consumeNonRestartHardFailSignal() {
        return NON_RESTART_HARD_FAIL_SIGNAL.getAndSet(false);
    }

    private static void clearNonRestartHardFailSignal() {
        NON_RESTART_HARD_FAIL_SIGNAL.set(false);
    }

    @Override
    public void onActivate() {
        startRuntimeWatchdogIfNeeded();
        runtimeFlag("onActivate");
        boolean preserveRestartFlow = restartHandlingArmed;
        boolean preserveRestartPostJoinStart = restartHandlingArmed && postRejoinStartedForCurrentRestart;
        boolean preserveRestartStage = restartHandlingArmed && restartRoutineStage != RestartRoutineStage.None;
        boolean preserveRestartModuleSnapshot = restartHandlingArmed && restartModuleStateSnapshotTaken;
        boolean preservePendingBuilderEnable = pendingHighwayBuilderEnableAfterRestore;
        boolean preserveModuleRestoreState = postJoinModuleStateCaptured || timerWasActiveBeforePostJoin || speedWasActiveBeforePostJoin;
        clearNonRestartHardFailSignal();
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
        restartScreenshotScheduled = false;
        pendingPostRejoinActions = false;
        postRejoinActionsAtMs = 0L;
        awaitingCrackedLoginSuccess = false;
        postRejoinRoutineRetryScheduled = false;
        postRejoinRoutineRetryAtMs = 0L;
        postRejoinRoutineRetryCount = 0;
        if (!preserveModuleRestoreState) {
            postJoinModuleStateCaptured = false;
            timerWasActiveBeforePostJoin = false;
            speedWasActiveBeforePostJoin = false;
        }
        resetPostRejoinPathState();
        debugRestartDisconnectPending = false;
        debugRestartFallbackScheduled = false;
        debugRestartFallbackAtMs = 0L;
        debugNonRestartHardFailPending = false;
        if (!preserveRestartFlow) {
            awaitPostRejoinAfterReconnect = false;
            restartHandlingArmed = false;
        }
        if (!preserveRestartPostJoinStart) postRejoinStartedForCurrentRestart = false;
        if (!preserveRestartStage) restartRoutineStage = RestartRoutineStage.None;
        if (!preserveRestartModuleSnapshot) restartModuleStateSnapshotTaken = false;
        if (!preservePendingBuilderEnable) {
            pendingHighwayBuilderEnableAfterRestore = false;
            pendingHighwayBuilderDirection = null;
            pendingHighwayBuilderEnableAtMs = 0L;
            pendingBuilderDirectionProbeFailures = 0;
        }
        boolean connectedOnActivate = isSuccessfullyConnectedToServer();
        if (connectedOnActivate && autoRejoinOnRestartDetection.get() && suppressAutoReconnectAfterHardFail) {
            suppressAutoReconnectAfterHardFail = false;
            nonRestartHardFailArmed = false;
            clearNonRestartHardFailSignal();
            runtimeFlag("clearSuppression:onActivateConnected");
            info("Successful server reconnection detected (activate fallback). AutoReconnect suppression cleared.");
        }
        wasConnectedLastTick = connectedOnActivate;
        if (preserveRestartFlow) {
            runtimeFlag("activate-preserved-restart-flow");
            info("Preserved restart flow state through module re-activation.");
        }
        if (preserveModuleRestoreState) runtimeFlag("activate-preserved-module-restore-state");
        runtimeFlag("activate-state-initialized");
    }

    @Override
    public void onDeactivate() {
        runtimeFlag("onDeactivate");
        boolean preserveRestartFlow = restartHandlingArmed;
        boolean preserveRestartPostJoinStart = restartHandlingArmed && postRejoinStartedForCurrentRestart;
        boolean preserveRestartStage = restartHandlingArmed && restartRoutineStage != RestartRoutineStage.None;
        boolean preserveRestartModuleSnapshot = restartHandlingArmed && restartModuleStateSnapshotTaken;
        boolean preservePendingBuilderEnable = pendingHighwayBuilderEnableAfterRestore;
        boolean preserveModuleRestoreState = postJoinModuleStateCaptured || timerWasActiveBeforePostJoin || speedWasActiveBeforePostJoin;
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
        restartScreenshotScheduled = false;
        pendingPostRejoinActions = false;
        postRejoinActionsAtMs = 0L;
        awaitingCrackedLoginSuccess = false;
        postRejoinRoutineRetryScheduled = false;
        postRejoinRoutineRetryAtMs = 0L;
        postRejoinRoutineRetryCount = 0;
        restorePostJoinModuleStatesIfNeeded();
        if (!preserveModuleRestoreState) {
            postJoinModuleStateCaptured = false;
            timerWasActiveBeforePostJoin = false;
            speedWasActiveBeforePostJoin = false;
        }
        resetPostRejoinPathState();
        debugRestartDisconnectPending = false;
        debugRestartFallbackScheduled = false;
        debugRestartFallbackAtMs = 0L;
        debugNonRestartHardFailPending = false;
        if (!preserveRestartFlow) {
            awaitPostRejoinAfterReconnect = false;
            restartHandlingArmed = false;
        }
        if (!preserveRestartPostJoinStart) postRejoinStartedForCurrentRestart = false;
        if (!preserveRestartStage) restartRoutineStage = RestartRoutineStage.None;
        if (!preserveRestartModuleSnapshot) restartModuleStateSnapshotTaken = false;
        if (!preservePendingBuilderEnable) {
            pendingHighwayBuilderEnableAfterRestore = false;
            pendingHighwayBuilderDirection = null;
            pendingHighwayBuilderEnableAtMs = 0L;
            pendingBuilderDirectionProbeFailures = 0;
        }
        wasConnectedLastTick = false;
        if (preserveRestartFlow) runtimeFlag("deactivate-preserved-restart-flow");
        if (preserveModuleRestoreState) runtimeFlag("deactivate-preserved-module-restore-state");
        stopRuntimeWatchdog();
    }

    @EventHandler(priority = 999)
    private void onMessageReceive(ReceiveMessageEvent event) {
        String message = event.getMessage().getString();
        if (message == null) return;
        String lower = message.toLowerCase(Locale.ROOT);

        if (pendingHighwayBuilderEnableAfterRestore && !restartHandlingArmed && lower.contains(LOGIN_PROMPT_MARKER)) {
            pendingHighwayBuilderEnableAfterRestore = false;
            pendingHighwayBuilderDirection = null;
            pendingHighwayBuilderEnableAtMs = 0L;
            pendingBuilderDirectionProbeFailures = 0;
            info("Detected login prompt while deferred HighwayBuilder enable was pending. Re-arming post-rejoin flow.");
            runtimeFlag("rearmPostRejoinFromLoginPrompt");
            rearmPostRejoinFlow("login prompt");
            return;
        }

        if (awaitingCrackedLoginSuccess && lower.contains(CRACKED_LOGIN_SUCCESS_MARKER)) {
            awaitingCrackedLoginSuccess = false;
            info("Detected cracked-account login success. Starting cracked login-lobby path routine.");
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
        runtimeFlag("onGameJoined");
        wasConnectedLastTick = true;
        boolean wasAwaitingPostRejoin = awaitPostRejoinAfterReconnect;
        boolean wasSuppressed = suppressAutoReconnectAfterHardFail;
        cacheCurrentServerIfConnected();
        clearNonRestartHardFailSignal();
        nonRestartHardFailArmed = false;
        info(
            "Server rejoin detected (GameJoinedEvent). awaitPostRejoin=%s, suppressAutoReconnect=%s, autoRestartHandling=%s",
            wasAwaitingPostRejoin,
            wasSuppressed,
            autoRejoinOnRestartDetection.get()
        );

        tryStartPostRejoinAfterReconnect("join event");

        if (autoRejoinOnRestartDetection.get() && suppressAutoReconnectAfterHardFail) {
            suppressAutoReconnectAfterHardFail = false;
            info("Successful server reconnection detected (join event). AutoReconnect suppression cleared.");
        }
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        runtimeFlag("onGameLeft");
        boolean startingNewDisconnectCycle = !restartHandlingArmed && !awaitPostRejoinAfterReconnect && !pendingHighwayBuilderEnableAfterRestore;
        if (startingNewDisconnectCycle && (postJoinModuleStateCaptured || restartModuleStateSnapshotTaken || timerWasActiveBeforePostJoin || speedWasActiveBeforePostJoin)) {
            postJoinModuleStateCaptured = false;
            timerWasActiveBeforePostJoin = false;
            speedWasActiveBeforePostJoin = false;
            restartModuleStateSnapshotTaken = false;
            runtimeFlag("resetPostJoinModuleSnapshot:newDisconnectCycle");
        }
        captureAndDisablePostJoinModulesForDisconnectSafety();
        boolean wasDebugRestartDisconnect = debugRestartDisconnectPending;
        debugRestartDisconnectPending = false;
        debugRestartFallbackScheduled = false;
        debugRestartFallbackAtMs = 0L;
        wasConnectedLastTick = false;

        boolean nonRestartHardFail = suppressAutoReconnectAfterHardFail || nonRestartHardFailArmed || consumeNonRestartHardFailSignal();
        nonRestartHardFailArmed = false;

        if (nonRestartHardFail) {
            disableAutoReconnectForNonRestartHardFail("HighwayBuilder signaled non-restart hard fail");
            return;
        }

        if (restartHandlingArmed || awaitPostRejoinAfterReconnect || pendingHighwayBuilderEnableAfterRestore) {
            ensureHighwayBuilderDisabledForRestart("game left", false);
        }

        if (pendingHighwayBuilderEnableAfterRestore && wasDebugRestartDisconnect) {
            pendingHighwayBuilderEnableAfterRestore = false;
            pendingHighwayBuilderDirection = null;
            pendingHighwayBuilderEnableAtMs = 0L;
            pendingBuilderDirectionProbeFailures = 0;
            runtimeFlag("pendingBuilderEnable:clearedForDebugRestart");
        }

        if (pendingHighwayBuilderEnableAfterRestore) {
            // During server-transfer reconnects after the public routine, ignore extra disconnects.
            // We only need to wait for reconnect so deferred HighwayBuilder enable can finish.
            awaitPostRejoinAfterReconnect = false;
            clearPostRejoinFlowState();
            pendingHighwayBuilderEnableAtMs = 0L;
            pendingBuilderDirectionProbeFailures = 0;
            info("Disconnect detected during deferred HighwayBuilder enable. Waiting for reconnect without re-running post-rejoin pathing.");
            runtimeFlag("pendingBuilderEnable:disconnectIgnored");
            return;
        }

        if (restartHandlingArmed) {
            if (crackedAccountMode.get() && restartRoutineStage == RestartRoutineStage.CrackedLoginLobby && postRejoinStartedForCurrentRestart) {
                restartRoutineStage = RestartRoutineStage.CrackedAwaitingTransferReconnect;
                postRejoinStartedForCurrentRestart = false;
                awaitPostRejoinAfterReconnect = true;
                clearPostRejoinFlowState();
                info("Cracked login-lobby disconnect detected. Awaiting reconnect to public lobby for shared routine.");
                runtimeFlag("crackedTransition:loginLobby->awaitingTransferReconnect");
                return;
            }

            if (crackedAccountMode.get() && restartRoutineStage == RestartRoutineStage.CrackedAwaitingTransferReconnect) {
                awaitPostRejoinAfterReconnect = true;
                clearPostRejoinFlowState();
                info("Additional disconnect while awaiting public lobby reconnect for cracked flow.");
                runtimeFlag("crackedAwaitingTransferReconnect:disconnect");
                return;
            }

            if (restartRoutineStage == RestartRoutineStage.PublicRoutine && postRejoinStartedForCurrentRestart) {
                postRejoinStartedForCurrentRestart = false;
                awaitPostRejoinAfterReconnect = false;
                clearPostRejoinFlowState();

                pendingHighwayBuilderEnableAfterRestore = enableHighwayBuilderOnRestart.get();
                pendingHighwayBuilderDirection = null;
                pendingHighwayBuilderEnableAtMs = 0L;
                pendingBuilderDirectionProbeFailures = 0;
                restartHandlingArmed = false;
                restartRoutineStage = RestartRoutineStage.None;
                activePostRejoinPathPurpose = PostRejoinPathPurpose.None;

                info("Public-routine transfer disconnect detected. THM HighwayBuilder will be enabled 6.0s after Timer/Speed restore when reconnected.");
                runtimeFlag("publicRoutineTransferDisconnectHandled");
                return;
            }

            // Reconnect loops on some servers can emit additional disconnects while reconnect is still in progress.
            // If post-rejoin already started for this restart cycle, do not start it again on transfer reconnects.
            if (postRejoinStartedForCurrentRestart) {
                awaitPostRejoinAfterReconnect = false;
                clearPostRejoinFlowState();
                info("Additional disconnect detected after post-rejoin already started for this restart. Not re-running routine.");
                runtimeFlag("skipRerunPostRejoinSameRestart");
            } else {
                // Otherwise keep waiting for first successful reconnect in this restart cycle.
                awaitPostRejoinAfterReconnect = true;
                clearPostRejoinFlowState();
                info("Additional disconnect detected while restart handling is already armed. Waiting for reconnect.");
            }
            return;
        }

        info("Disconnect detected without non-restart hard-fail signal. Treating as restart.");
        handleRestartDetectionTrigger();
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
        pendingHighwayBuilderEnableAfterRestore = false;
        pendingHighwayBuilderDirection = null;
        pendingHighwayBuilderEnableAtMs = 0L;
        pendingBuilderDirectionProbeFailures = 0;
        awaitPostRejoinAfterReconnect = false;
        disableMeteorAutoReconnect(true);
        warning("AutoReconnect disabled due to non-restart hard fail: %s", reason);
    }

    // Legacy helper retained for validation testing.
    // Not used by the current restart/rejoin flow.
    private void preparePostRejoinFlowForReconnectSchedule() {
        restorePostJoinModuleStatesIfNeeded();
        clearPostRejoinFlowState();
    }

    private void beginPostRejoinFlowAfterReconnect() {
        pendingPostRejoinActions = true;
        postRejoinActionsAtMs = 0L;
        awaitingCrackedLoginSuccess = false;
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

    private void ensureHighwayBuilderDisabledForRestart(String source, boolean verbose) {
        HighwayBuilderTHM builder = Modules.get().get(HighwayBuilderTHM.class);
        if (builder == null || !builder.isActive()) return;

        builder.toggle();
        boolean disabled = !builder.isActive();
        runtimeFlag("forceBuilderOff:" + source + ":" + (disabled ? "ok" : "failed"));

        if (disabled) {
            if (verbose) info("Disabled THM HighwayBuilder during restart handling (%s).", source);
        } else {
            warning("Failed to disable THM HighwayBuilder during restart handling (%s).", source);
        }
    }

    private void handleRestartDetectionTrigger() {
        if (restartHandlingArmed) return;

        // Reconnect handling is delegated to Meteor AutoReconnect.
        restartHandlingArmed = true;
        postRejoinStartedForCurrentRestart = false;
        restartModuleStateSnapshotTaken = postJoinModuleStateCaptured || timerWasActiveBeforePostJoin || speedWasActiveBeforePostJoin;
        restartRoutineStage = crackedAccountMode.get() ? RestartRoutineStage.CrackedLoginLobby : RestartRoutineStage.PublicRoutine;
        activePostRejoinPathPurpose = PostRejoinPathPurpose.None;
        pendingHighwayBuilderEnableAfterRestore = false;
        pendingHighwayBuilderDirection = null;
        pendingHighwayBuilderEnableAtMs = 0L;
        pendingBuilderDirectionProbeFailures = 0;
        suppressAutoReconnectAfterHardFail = false;
        awaitPostRejoinAfterReconnect = true;
        clearPostRejoinFlowState();
        ensureHighwayBuilderDisabledForRestart("restart detection", true);
        info(
            "Restart handling armed. awaitPostRejoin=%s, autoRestartHandling=%s, autoScreenshot=%s",
            awaitPostRejoinAfterReconnect,
            autoRejoinOnRestartDetection.get(),
            autoScreenshotOnRestartDetection.get()
        );
        if (autoRejoinOnRestartDetection.get()) scheduleRestartReconnect();
        if (autoScreenshotOnRestartDetection.get()) scheduleRestartScreenshot();
        runtimeFlag("restartHandlingArmed");
    }

    private void triggerDebugRestartHandling() {
        info("Running debug restart trigger.");
        runtimeFlag("triggerDebugRestartHandling");
        cacheCurrentServerIfConnected();

        // Debug restart tests should always start from a clean post-rejoin state.
        pendingHighwayBuilderEnableAfterRestore = false;
        pendingHighwayBuilderDirection = null;
        pendingHighwayBuilderEnableAtMs = 0L;
        pendingBuilderDirectionProbeFailures = 0;
        restartHandlingArmed = false;
        postRejoinStartedForCurrentRestart = false;
        restartRoutineStage = RestartRoutineStage.None;
        activePostRejoinPathPurpose = PostRejoinPathPurpose.None;
        awaitPostRejoinAfterReconnect = false;
        clearPostRejoinFlowState();
        ensureHighwayBuilderDisabledForRestart("debug trigger", true);

        if (mc != null && mc.getNetworkHandler() != null && mc.getNetworkHandler().getConnection() != null) {
            debugRestartDisconnectPending = true;
            debugRestartFallbackScheduled = true;
            debugRestartFallbackAtMs = System.currentTimeMillis() + DEBUG_RESTART_FALLBACK_DELAY_MS;
            mc.getNetworkHandler().getConnection().disconnect(Text.literal(DEBUG_RESTART_REASON));
        } else {
            debugRestartDisconnectPending = false;
            debugRestartFallbackScheduled = false;
            debugRestartFallbackAtMs = 0L;
            warning("Debug restart trigger: not currently connected, no disconnect was issued.");
        }
    }

    private void triggerDebugNonRestartHardFailHandling() {
        info("Running debug non-restart hard-fail trigger.");
        runtimeFlag("triggerDebugNonRestartHardFail");

        if (debugNonRestartHardFailPending) {
            info("Debug non-restart hard-fail trigger is already pending.");
            return;
        }

        debugNonRestartHardFailPending = true;
        signalNonRestartHardFailFromHighwayBuilder();
        nonRestartHardFailArmed = true;
        disableAutoReconnectForNonRestartHardFail("Debug non-restart hard fail trigger");

        Thread thread = new Thread(() -> {
            try {
                Thread.sleep(DEBUG_NON_RESTART_HARD_FAIL_DELAY_MS);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
                debugNonRestartHardFailPending = false;
                return;
            }

            if (mc == null) {
                debugNonRestartHardFailPending = false;
                return;
            }

            mc.execute(() -> {
                debugNonRestartHardFailPending = false;
                runtimeFlag("debugNonRestartHardFail:delayElapsed");

                HighwayBuilderTHM builder = Modules.get().get(HighwayBuilderTHM.class);
                if (builder != null && builder.isActive()) {
                    builder.toggle();
                    if (!builder.isActive()) info("Disabled THM HighwayBuilder for debug non-restart hard fail.");
                    else warning("Failed to disable THM HighwayBuilder for debug non-restart hard fail.");
                }

                if (mc.getNetworkHandler() != null && mc.getNetworkHandler().getConnection() != null) {
                    mc.getNetworkHandler().getConnection().disconnect(Text.literal(DEBUG_NON_RESTART_HARD_FAIL_REASON));
                } else {
                    warning("Debug non-restart hard-fail trigger: not currently connected, no disconnect was issued.");
                }
            });
        }, "thm-debug-non-restart-hard-fail");
        thread.setDaemon(true);
        thread.start();
    }

    // Legacy server-cache writer retained for validation testing.
    // The cached values are currently not consumed by the active reconnect flow.
    private void cacheCurrentServerIfConnected() {
        if (mc == null || mc.player == null || mc.world == null || mc.getNetworkHandler() == null) return;

        try {
            ServerInfo current = mc.getCurrentServerEntry();
            if (current == null || current.address == null || current.address.isEmpty()) return;
            lastServerConnection = new ObjectObjectImmutablePair<>(ServerAddress.parse(current.address), current);
            lastServerInfo = current;
            lastServerAddress = ServerAddress.parse(current.address);
        } catch (Throwable ignored) {
            // Ignore parse/update errors here; reconnect fallback may still use cached values.
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        cacheCurrentServerIfConnected();
        boolean connectedNow = isSuccessfullyConnectedToServer();
        boolean connectedTransitionToConnected = connectedNow && !wasConnectedLastTick;
        if (connectedNow != wasConnectedLastTick) {
            runtimeFlag("tickConnectedTransition:" + (wasConnectedLastTick ? "connected->disconnected" : "disconnected->connected"));
        }

        if (connectedTransitionToConnected && autoRejoinOnRestartDetection.get() && suppressAutoReconnectAfterHardFail) {
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
        if (autoRejoinOnRestartDetection.get() && !suppressAutoReconnectAfterHardFail) configureMeteorAutoReconnect(true, false);
        else {
            disableMeteorAutoReconnect(false);
        }

        if (!autoRecover.get()) return;
        if (mc.player == null || mc.world == null) return;

        if (recoveryPhase != RecoveryPhase.None) {
            handleRecoveryPhase();
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
            trackedLine = null;
            trackedDirection = "";
            return;
        }

        int recoveryGoalY = usesObsidianPaving(builder) ? 120 : 119;

        RecoveryTarget target = determineRecoveryTarget(
            mc.player.getX(),
            mc.player.getZ(),
            mc.player.getYaw(),
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
                mc.player.getYaw(),
                recoveryGoalY,
                trueCenterMode.get(),
                trackedLine,
                trackedDirection
            );
            if (target == null) return;
        }

        if (target.distance() <= ALIGN_TOLERANCE) return;

        if (target.distance() > maxCorrectionDistance.get()) {
            warning("Misaligned by %.2f on %s %s. Exceeds max-correction-distance %.2f.",
                target.distance(), target.highway(), target.direction(), maxCorrectionDistance.get());
            handleExcessiveMisalignment(builder, target);
            return;
        }

        if (!pauseAllActiveModulesForRecovery()) {
            warning("Failed to pause active modules for recovery.");
            cooldownTicks = recoveryCooldown.get();
            return;
        }

        recoveryBuilder = builder;
        pendingCorrectionTarget = target;
        trackedLine = target.line();
        trackedDirection = target.direction();
        recoveryYawBeforeMove = mc.player != null ? mc.player.getYaw() : Float.NaN;
        recoveryTicks = RECOVERY_DELAY_TICKS;
        recoveryPhase = RecoveryPhase.WaitBeforeCorrection;
        info("Paused active modules for recovery on %s %s (off by %.2f). Starting Baritone correction in 2.0s.", target.highway(), target.direction(), target.distance());
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
            info("Resumed paused modules after recovery.");

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
        disableAutoReconnectForNonRestartHardFail("HighwayBuilder excessive misalignment");

        // Ensure no previously paused modules remain paused before forcing a disconnect.
        resumePausedModulesAfterRecovery();

        if (builder != null && builder.isActive()) {
            builder.toggle();
            if (builder.isActive()) warning("Failed to toggle THM HighwayBuilder off after excessive misalignment.");
            else info("Toggled THM HighwayBuilder off after excessive misalignment.");
        }

        resetRecoveryState();
        cooldownTicks = recoveryCooldown.get();

        String reason = String.format(Locale.ROOT,
            "THM Hwy Monitor: excessive misalignment %.2f on %s %s (max %.2f).",
            target.distance(), target.highway(), target.direction(), maxCorrectionDistance.get());
        if (mc != null && mc.getNetworkHandler() != null && mc.getNetworkHandler().getConnection() != null) {
            mc.getNetworkHandler().getConnection().disconnect(Text.literal(reason));
        }
    }

    private boolean pauseAllActiveModulesForRecovery() {
        if (recoveryModulesPaused) return true;

        recoveryPausedModules.clear();
        List<Module> activeModules = new ArrayList<>(Modules.get().getActive());
        for (Module module : activeModules) {
            if (module == null || module == this) continue;
            if (!module.isActive()) continue;

            recoveryPausedModules.add(module);
            module.disable();
        }

        recoveryModulesPaused = true;
        return true;
    }

    private void resumePausedModulesAfterRecovery() {
        if (!recoveryModulesPaused) return;

        for (Module module : recoveryPausedModules) {
            if (module == null) continue;
            module.enable();
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

    private static boolean usesObsidianPaving(HighwayBuilderTHM builder) {
        return builder.blocksToPlace.get().contains(Blocks.OBSIDIAN);
    }

    private void takeRestartScreenshot() {
        if (mc == null || mc.getFramebuffer() == null) return;
        ScreenshotRecorder.saveScreenshot(mc.runDirectory, mc.getFramebuffer(), message -> info(message.getString()));
    }

    private void handleRestartAutomationTick() {
        long now = System.currentTimeMillis();

        if (debugRestartFallbackScheduled && debugRestartDisconnectPending && now >= debugRestartFallbackAtMs) {
            debugRestartFallbackScheduled = false;
            debugRestartFallbackAtMs = 0L;
            debugRestartDisconnectPending = false;
            warning("Debug restart fallback engaged: no disconnect event detected within 2.0s.");
        }
        
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

    // Legacy resolver retained for validation testing.
    // Currently not used by the active reconnect flow.
    private Pair<ServerAddress, ServerInfo> resolveLastServerConnection() {
        if (lastServerConnection != null) return lastServerConnection;

        if (lastServerAddress != null && lastServerInfo != null) {
            return Pair.of(lastServerAddress, lastServerInfo);
        }
        
        return null;
    }

    private void runPostRejoinActions() {
        postRejoinActionsAtMs = 0L;
        pendingPostRejoinActions = false;
        resetPostRejoinPathState();
        awaitingCrackedLoginSuccess = false;

        if (crackedAccountMode.get() && restartRoutineStage == RestartRoutineStage.CrackedLoginLobby) {
            Macro macro = findFirstF7Macro();
            if (macro != null) {
                macro.onAction();
                info("Ran required F7 login macro for cracked account mode.");
            } else {
                warning("Cracked account mode requires an F7 macro, but none was found.");
                return;
            }

            awaitingCrackedLoginSuccess = true;
            info("Waiting for cracked-account login confirmation message before pathing.");
            return;
        }

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

            if (activePostRejoinPathPurpose == PostRejoinPathPurpose.CrackedLoginLobby) {
                resetPostRejoinPathState();
                if (restartHandlingArmed) {
                    restartRoutineStage = RestartRoutineStage.CrackedAwaitingTransferReconnect;
                    postRejoinStartedForCurrentRestart = false;
                    awaitPostRejoinAfterReconnect = true;
                    info("Cracked login-lobby routine complete. Awaiting reconnect to public lobby for shared routine.");
                    runtimeFlag("crackedTransition:loginLobbyPathComplete->awaitingTransferReconnect");
                }
                return;
            }

            verifyPostRejoinConnectionOrScheduleRetry();
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
            postRejoinRoutineRetryScheduled = false;
            postRejoinRoutineRetryAtMs = 0L;
            postRejoinRoutineRetryCount = 0;
            completePostRejoinSuccessFlow();
            return;
        }

        postRejoinRoutineRetryScheduled = true;
        postRejoinRoutineRetryAtMs = System.currentTimeMillis() + POST_REJOIN_RETRY_DELAY_MS;
        warning("Post-join routine finished but server connection is not verified. Retrying in 10.0s.");
    }

    private void runPostRejoinRoutineRetry() {
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
            restorePostJoinModuleStatesIfNeeded();
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
        if (crackedAccountMode.get() && restartRoutineStage == RestartRoutineStage.CrackedLoginLobby) {
            activePostRejoinPathPurpose = PostRejoinPathPurpose.CrackedLoginLobby;
        } else {
            activePostRejoinPathPurpose = PostRejoinPathPurpose.PublicRoutine;
            if (restartRoutineStage == RestartRoutineStage.CrackedAwaitingTransferReconnect) {
                restartRoutineStage = RestartRoutineStage.PublicRoutine;
                postRejoinStartedForCurrentRestart = false;
                info("Cracked flow reached public lobby. Starting shared post-rejoin routine.");
                runtimeFlag("crackedTransition:awaitingTransferReconnect->publicRoutine");
            } else if (restartRoutineStage == RestartRoutineStage.None) {
                restartRoutineStage = RestartRoutineStage.PublicRoutine;
            }
        }

        startPostRejoinPathSequence(postRejoinRoutineCommands());
    }

    private void tryStartPostRejoinAfterReconnect(String source) {
        if (!restartHandlingArmed) return;
        if (!awaitPostRejoinAfterReconnect) return;

        if (crackedAccountMode.get() && restartRoutineStage == RestartRoutineStage.CrackedAwaitingTransferReconnect) {
            restartRoutineStage = RestartRoutineStage.PublicRoutine;
            postRejoinStartedForCurrentRestart = false;
            runtimeFlag("crackedTransition:awaitingTransferReconnect->publicRoutine");
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
        restartHandlingArmed = true;
        postRejoinStartedForCurrentRestart = false;
        restartModuleStateSnapshotTaken = postJoinModuleStateCaptured || timerWasActiveBeforePostJoin || speedWasActiveBeforePostJoin;
        restartRoutineStage = crackedAccountMode.get() ? RestartRoutineStage.CrackedLoginLobby : RestartRoutineStage.PublicRoutine;
        activePostRejoinPathPurpose = PostRejoinPathPurpose.None;
        awaitPostRejoinAfterReconnect = true;
        clearPostRejoinFlowState();
        runtimeFlag("rearmPostRejoinFlow:" + source);
        tryStartPostRejoinAfterReconnect(source);
    }

    private void completePostRejoinSuccessFlow() {
        info("Post-join connection verification succeeded.");
        restartHandlingArmed = false;
        postRejoinStartedForCurrentRestart = false;
        restartRoutineStage = RestartRoutineStage.None;
        activePostRejoinPathPurpose = PostRejoinPathPurpose.None;
        runtimeFlag("completePostRejoinSuccessFlow");

        HorizontalDirection workingDirection = determinePostRejoinWorkingDirection();
        if (workingDirection == null) return;

        // Restore original module states first; HighwayBuilder activation must happen only after this completes.
        restorePostJoinModuleStatesIfNeeded();
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
        info("Post-rejoin routine complete. THM HighwayBuilder will be enabled 6.0s after Timer/Speed restore.");
        runtimeFlag("pendingBuilderEnable:armed");
    }

    private void handleDeferredHighwayBuilderEnableAfterRestore() {
        if (!pendingHighwayBuilderEnableAfterRestore) return;
        if (!isSuccessfullyConnectedToServer()) {
            // Require a full connected delay window; do not carry a previous timer across reconnects.
            pendingHighwayBuilderEnableAtMs = 0L;
            return;
        }
        if (!isSupportedVanillaDimension()) {
            pendingHighwayBuilderEnableAtMs = 0L;
            runtimeFlag("pendingBuilderEnable:unsupportedDimension");
            return;
        }

        restorePostJoinModuleStatesIfNeeded();
        if (postJoinModuleStateCaptured) {
            pendingHighwayBuilderEnableAtMs = 0L;
            return;
        }

        if (pendingHighwayBuilderEnableAtMs == 0L) {
            pendingHighwayBuilderEnableAtMs = System.currentTimeMillis() + BUILDER_ENABLE_DELAY_MS;
            runtimeFlag("pendingBuilderEnable:delayStarted");
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

            if (!restartHandlingArmed && pendingBuilderDirectionProbeFailures >= PENDING_BUILDER_DIRECTION_FAIL_REARM_THRESHOLD) {
                pendingHighwayBuilderEnableAfterRestore = false;
                pendingHighwayBuilderDirection = null;
                pendingHighwayBuilderEnableAtMs = 0L;
                pendingBuilderDirectionProbeFailures = 0;
                info("Deferred HighwayBuilder direction checks failed repeatedly. Re-arming post-rejoin flow.");
                rearmPostRejoinFlow("direction not ready threshold");
            }
            return;
        }
        pendingBuilderDirectionProbeFailures = 0;

        pendingHighwayBuilderEnableAfterRestore = false;
        pendingHighwayBuilderDirection = null;
        pendingHighwayBuilderEnableAtMs = 0L;
        applyDirectionAndEnableHighwayBuilder(direction);
        info("Deferred THM HighwayBuilder activation completed after module restore and 6.0s delay.");
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

        HorizontalDirection[] axisDirections = resolvePostRejoinAxisDirections();
        if (axisDirections == null) {
            warning("Unable to resolve highway axis after rejoin. Using current facing direction.");
            return HorizontalDirection.get(mc.player.getYaw());
        }

        HorizontalDirection dirA = axisDirections[0];
        HorizontalDirection dirB = axisDirections[1];
        HighwayBuilderTHM builder = Modules.get().get(HighwayBuilderTHM.class);
        boolean obsidianPavingSelected = builder != null && usesObsidianPaving(builder);

        if (obsidianPavingSelected) {
            boolean dirAObsidianY119 = isObsidianAtAxisProbe(dirA, POST_REJOIN_AXIS_PROBE_DISTANCE, 119);
            boolean dirBObsidianY119 = isObsidianAtAxisProbe(dirB, POST_REJOIN_AXIS_PROBE_DISTANCE, 119);
            if (dirAObsidianY119 != dirBObsidianY119) return dirAObsidianY119 ? dirB : dirA;

            warning("Post-rejoin obsidian direction checks at Y=119 were ambiguous. Falling back to current facing direction.");
            return closerToCurrentFacing(dirA, dirB);
        }

        boolean dirAAirY122 = isAirAtAxisProbe(dirA, POST_REJOIN_AXIS_PROBE_DISTANCE, 122);
        boolean dirBAirY122 = isAirAtAxisProbe(dirB, POST_REJOIN_AXIS_PROBE_DISTANCE, 122);
        if (dirAAirY122 != dirBAirY122) return dirAAirY122 ? dirB : dirA;

        if (dirAAirY122 && dirBAirY122) {
            warning("Post-rejoin direction not ready yet: both axis directions are air at Y=122. Deferring HighwayBuilder enable.");
            return null;
        }

        warning("Post-rejoin direction checks were ambiguous. Falling back to current facing direction.");
        return closerToCurrentFacing(dirA, dirB);
    }

    private HorizontalDirection[] resolvePostRejoinAxisDirections() {
        HighwayBuilderTHM builder = Modules.get().get(HighwayBuilderTHM.class);
        if (builder != null && builder.dir != null) return new HorizontalDirection[] {builder.dir, builder.dir.opposite()};

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

    private HorizontalDirection closerToCurrentFacing(HorizontalDirection a, HorizontalDirection b) {
        if (mc.player == null) return a;
        HorizontalDirection facing = HorizontalDirection.get(mc.player.getYaw());
        double dotA = (facing.offsetX * a.offsetX) + (facing.offsetZ * a.offsetZ);
        double dotB = (facing.offsetX * b.offsetX) + (facing.offsetZ * b.offsetZ);
        return dotA >= dotB ? a : b;
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
        restorePostJoinModuleStatesIfNeeded();
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

    private void hardFailCrackedLoginDisconnect(String reason) {
        disableAutoReconnectForNonRestartHardFail(reason);
        warning(reason);
        awaitingCrackedLoginSuccess = false;

        pendingPostRejoinActions = false;
        postRejoinActionsAtMs = 0L;
        resetPostRejoinRetryState();
        resetPostRejoinPathState();
        restorePostJoinModuleStatesIfNeeded();

        if (isActive()) toggle();
    }

    private void captureAndDisablePostJoinModules() {
        Timer timer = Modules.get().get(Timer.class);
        Speed speed = Modules.get().get(Speed.class);

        if (!restartModuleStateSnapshotTaken) {
            if (!postJoinModuleStateCaptured) {
                timerWasActiveBeforePostJoin = timer != null && timer.isActive();
                speedWasActiveBeforePostJoin = speed != null && speed.isActive();
                runtimeFlag("captureInitialPostJoinModuleSnapshot");
            } else {
                runtimeFlag("captureInitialPostJoinModuleSnapshot:preserved");
            }
            restartModuleStateSnapshotTaken = true;
        }

        postJoinModuleStateCaptured = timerWasActiveBeforePostJoin || speedWasActiveBeforePostJoin;

        if (timerWasActiveBeforePostJoin && timer != null && timer.isActive()) {
            timer.toggle();
            info("Disabled Timer for post-join routine.");
        }

        if (speedWasActiveBeforePostJoin && speed != null && speed.isActive()) {
            speed.toggle();
            info("Disabled Speed for post-join routine.");
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

        if (timerWasActiveBeforePostJoin && timer != null && !timer.isActive()) {
            timer.toggle();
            info("Restored Timer state after post-join routine.");
        }

        if (speedWasActiveBeforePostJoin && speed != null && !speed.isActive()) {
            speed.toggle();
            info("Restored Speed state after post-join routine.");
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

    private static Macro findFirstF7Macro() {
        for (Macro macro : Macros.get()) {
            if (macro.keybind.get().matches(true, GLFW_KEY_F7, 0)) return macro;
        }
        return null;
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

    private static int floorToBlock(double value) {
        return (int) Math.floor(value);
    }

    @Override
    public WWidget getWidget(GuiTheme theme) {
        WVerticalList list = theme.verticalList();

        WButton debugRestart = theme.button("Debug Restart Trigger");
        debugRestart.action = this::triggerDebugRestartHandling;
        list.add(debugRestart).expandX();

        WButton debugNonRestartHardFail = theme.button("Debug Non-Restart Hard Fail");
        debugNonRestartHardFail.action = this::triggerDebugNonRestartHardFailHandling;
        list.add(debugNonRestartHardFail).expandX();

        return list;
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
        if (!debugRuntimeFlagLog.get() || runtimeWatchdogRunning) return;
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
            "active=%s connected=%s player=%s world=%s net=%s screen=%s restartArmed=%s stage=%s stageStarted=%s moduleSnapshot=%s awaitPost=%s pendingPost=%s pendingBuilderEnable=%s pendingBuilderAtMs=%s pathPurpose=%s",
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
            activePostRejoinPathPurpose
        );
    }

    private void runtimeFlag(String flag) {
        if (!debugRuntimeFlagLog.get()) return;

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
        CrackedLoginLobby,
        CrackedAwaitingTransferReconnect,
        PublicRoutine
    }

    private enum PostRejoinPathPurpose {
        None,
        CrackedLoginLobby,
        PublicRoutine
    }

    private enum WorkLine {
        CardinalNS,
        CardinalEW,
        DiagonalNWSE,
        DiagonalNESW
    }
}
