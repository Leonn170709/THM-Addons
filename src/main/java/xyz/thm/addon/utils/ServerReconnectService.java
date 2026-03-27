package xyz.thm.addon.utils;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.game.GameJoinedEvent;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.pathing.BaritoneUtils;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.movement.speed.Speed;
import meteordevelopment.meteorclient.systems.modules.misc.AutoReconnect;
import meteordevelopment.meteorclient.systems.modules.world.Timer;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.text.Text;
import xyz.thm.addon.THMAddon;
import xyz.thm.addon.system.THMSystem;
import xyz.thm.addon.utils.ServerStatusHandler.ServerState;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/**
 * Reconnect authority service for THM modules.
 *
 * <p>This singleton owns reconnect lifecycle, cracked-account login, lobby portal pathing,
 * transfer wait, and main-server ready signaling. It is event-driven and does not require a
 * module toggle once initialized.
 *
 * <p>Authoritative server-state input comes from {@link ServerStatusHandler}.
 *
 * <p><b>Quick start (module author)</b>
 * <ol>
 *     <li>Get singleton once:
 *     {@code ServerReconnectService reconnect = ServerReconnectService.getInstance();}</li>
 *     <li>Register callbacks with stable keys:
 *     {@link #registerResumeListener(String, ResumeListener)} and
 *     {@link #registerFailureListener(String, FailureListener)}.</li>
 *     <li>Arm reconnect when you detect restart/disconnect evidence:
 *     {@link #armReconnect(int, String)} or {@link #armReconnectSeconds(int, String)}.</li>
 *     <li>Resume module behavior only from
 *     {@link ResumeListener#onMainServerReady(long, String, long, long)}.</li>
 *     <li>On module deactivate/hard-stop, call {@link #disarmReconnect(String)} and unregister listeners.</li>
 * </ol>
 *
 * <p><b>Important contracts</b>
 * <ul>
 *     <li>Do not poll for "success"; use resume callback as success signal.</li>
 *     <li>Use returned {@code cycleId} from arm calls and ignore stale callbacks in callers.</li>
 *     <li>Service stays armed after {@link ReconnectStage#MAIN_SERVER_READY}; it does not auto-disarm on success.</li>
 *     <li>While armed from an already-connected state, stage stays {@link ReconnectStage#AWAITING_RECONNECT}
 *     until a real disconnect is observed (prevents false immediate success callbacks).</li>
 * </ul>
 *
 * <p><b>Public API summary</b>
 * <ul>
 *     <li>{@link #getInstance()}: singleton accessor.</li>
 *     <li>{@link #armReconnect(int, String)}:
 *     arms/refreshes cycle, configures + enables Meteor AutoReconnect, and returns new cycleId.
 *     Delay uses per-arm jitter:
 *     <ul>
 *         <li>{@code delayMinutes == 1}: random 60..180 seconds (+2/-0 behavior).</li>
 *         <li>otherwise: random {@code base-60 .. base+60} seconds (clamped at 0 minimum).</li>
 *     </ul>
 *     </li>
 *     <li>{@link #armReconnectSeconds(int, String)}:
 *     same as above but uses explicit seconds (no minute-jitter transform).</li>
 *     <li>{@link #disarmReconnect(String)}:
 *     disables AutoReconnect, cancels Baritone pathing/goal, and clears reconnect runtime state.</li>
 *     <li>{@link #isReconnectArmed()} / {@link #getReconnectStage()}:
 *     read current armed/stage state.</li>
 *     <li>{@link #getReconnectPreflight()}:
 *     returns runtime diagnostics snapshot (armed/stage/cycle/effective delay +
 *     AutoReconnect module and lastServerConnection visibility flags).</li>
 *     <li>{@link #registerResumeListener(String, ResumeListener)} and
 *     {@link #unregisterResumeListener(String)}.</li>
 *     <li>{@link #registerFailureListener(String, FailureListener)} and
 *     {@link #unregisterFailureListener(String)}.</li>
 * </ul>
 *
 * <p><b>Stage behavior (high level)</b>
 * <ul>
 *     <li>{@link ReconnectStage#CRACKED_LOGIN}: sends {@code /login <password>} once per stage entry.</li>
 *     <li>{@link ReconnectStage#CRACKED_LOBBY_PATH} and {@link ReconnectStage#MAIN_LOBBY_PATH}:
 *     snapshots Timer/Speed state, forces both OFF, waits pre-delay, issues {@code goto nether_portal},
 *     waits for Baritone success marker, then applies post-delay.</li>
 *     <li>Timer/Speed restore is deferred and gate-checked by stage target:
 *     cracked path restores at MAIN_LOBBY, main-lobby path restores at MAIN_SERVER.</li>
 *     <li>Path timeout retries same path stage (no stage advance on timeout).</li>
 * </ul>
 *
 * <p><b>Success + failure callbacks</b>
 * <ul>
 *     <li>Success = stage reaches {@link ReconnectStage#MAIN_SERVER_READY}.</li>
 *     <li>Resume callback is dispatched on client thread via {@code mc.execute(...)}.</li>
 *     <li>Resume callback is one-shot per return to MAIN_SERVER while armed (latch resets on stage regression/game left).</li>
 *     <li>Failure callback fires for cycle-ending hard failures:
 *     {@link FailureReason#MISSING_CRACKED_PASSWORD},
 *     {@link FailureReason#DISCONNECTED_AFTER_CRACKED_LOGIN_ATTEMPT}.</li>
 * </ul>
 *
 * <p><b>Usage example</b>
 * <pre>{@code
 * ServerReconnectService reconnect = ServerReconnectService.getInstance();
 *
 * reconnect.registerResumeListener("my-module-key", (cycleId, contextTag, armedAtMs, detectedAtMs) -> {
 *     // MAIN_SERVER reached. Resume module behavior here.
 *     myModule.enable();
 * });
 *
 * reconnect.registerFailureListener("my-module-key", (cycleId, reason, detail, contextTag, armedAtMs, failedAtMs) -> {
 *     // Cycle-ending hard-fail for reconnect flow.
 *     myModule.warn("Reconnect failed: " + reason + " (" + detail + ")");
 * });
 *
 * long cycleId = reconnect.armReconnect(15, "MyModule");
 * // Keep cycleId in caller state and ignore stale callbacks where callbackCycleId != storedCycleId.
 *
 * // Optional later cleanup:
 * reconnect.unregisterResumeListener("my-module-key");
 * reconnect.unregisterFailureListener("my-module-key");
 * reconnect.disarmReconnect("manual stop");
 * }</pre>
 */
public final class ServerReconnectService {
    public enum ReconnectStage {
        DISARMED,
        AWAITING_RECONNECT,
        CRACKED_LOGIN,
        CRACKED_LOBBY_PATH,
        MAIN_LOBBY_PATH,
        TRANSFER_WAIT,
        MAIN_SERVER_READY
    }

    private enum PendingRestoreGate {
        NONE,
        MAIN_LOBBY,
        MAIN_SERVER
    }

    public record ReconnectPreflight(
        boolean serviceArmed,
        ReconnectStage stage,
        long cycleId,
        int effectiveDelaySeconds,
        boolean connectedAtArm,
        boolean joinedSinceArm,
        boolean disconnectObservedSinceArm,
        boolean autoReconnectModulePresent,
        boolean autoReconnectActive,
        Double autoReconnectSettingDelaySeconds,
        boolean lastServerConnectionPresent
    ) {}

    @FunctionalInterface
    public interface ResumeListener {
        void onMainServerReady(long cycleId, String contextTag, long armedAtMs, long detectedAtMs);
    }

    public enum FailureReason {
        MISSING_CRACKED_PASSWORD,
        DISCONNECTED_AFTER_CRACKED_LOGIN_ATTEMPT
    }

    @FunctionalInterface
    public interface FailureListener {
        void onReconnectFailure(long cycleId, FailureReason reason, String detail, String contextTag, long armedAtMs, long failedAtMs);
    }

    private static final String BARITONE_PATH_COMPLETE_MARKER = "pathing complete";
    private static final String PORTAL_COMMAND = "goto nether_portal";
    private static final long PATH_PRE_COMMAND_DELAY_MS = 1_500L;
    private static final long PATH_POST_SUCCESS_DELAY_MS = 2_000L;
    private static final long PATH_STAGE_TIMEOUT_MS = 180_000L;
    private static final long CRACKED_LOGIN_MISSING_PASSWORD_GRACE_MS = 5_000L;
    private static final long AWAITING_RECONNECT_DIAGNOSTIC_INTERVAL_MS = 1_000L;
    private static final long MODULE_RESTORE_STAGE_STABLE_MS = 1_000L;
    private static final boolean TIMER_SPEED_TRACE_LOGS = false;
    private static final long RESTORE_BLOCK_LOG_INTERVAL_MS = 500L;
    private static final boolean DEBUG_LOGS = false;

    private static volatile ServerReconnectService INSTANCE;

    private final Object lock = new Object();
    private final Map<String, ResumeListener> resumeListeners = new HashMap<>();
    private final Map<String, FailureListener> failureListeners = new HashMap<>();

    private boolean armed;
    private long activeCycleId;
    private long nextCycleId = 1L;
    private ReconnectStage stage = ReconnectStage.DISARMED;
    private String contextTag = "";
    private int delayMinutes;
    private int effectiveDelaySeconds;
    private long armedAtMs;
    private boolean resumeCallbackFired;
    private boolean disconnectObservedSinceArm;
    private boolean connectedAtArm;
    private boolean joinedSinceArm;

    private long stageEnteredAtMs;
    private long lastAwaitingDiagnosticAtMs;
    private boolean crackedLoginSentThisStage;
    private long crackedLoginMissingPasswordSinceMs;

    private boolean crackedLobbyPathCompleted;
    private boolean mainLobbyPathCompleted;

    private ReconnectStage pathStageInFlight = ReconnectStage.DISARMED;
    private boolean waitingForPathSuccess;
    private long pathPreCommandDelayUntilMs;
    private long pathPostDelayUntilMs;
    private long pathDeadlineAtMs;
    private int pathAttemptCount;
    private boolean pathModuleSnapshotCaptured;
    private boolean pathTimerWasActive;
    private boolean pathSpeedWasActive;
    private boolean pathModulesForcedOff;
    private ReconnectStage pathSnapshotOwnerStage = ReconnectStage.DISARMED;
    private long pathSnapshotCycleId;
    private boolean deferredRestorePending;
    private long deferredRestoreCycleId;
    private PendingRestoreGate deferredRestoreTarget = PendingRestoreGate.NONE;
    private boolean deferredRestoreTimerWasActive;
    private boolean deferredRestoreSpeedWasActive;
    private String deferredRestoreReason = "";
    private boolean traceEdgeInitialized;
    private boolean traceLastTimerActive;
    private boolean traceLastSpeedActive;
    private long lastRestoreBlockedLogAtMs;

    private static void debugInfo(String message, Object... args) {
        if (DEBUG_LOGS) THMAddon.LOG.info(message, args);
    }

    private static void debugWarn(String message, Object... args) {
        if (DEBUG_LOGS) THMAddon.LOG.warn(message, args);
    }

    private ServerReconnectService() {
        MeteorClient.EVENT_BUS.subscribe(this);
    }

    public static ServerReconnectService getInstance() {
        if (INSTANCE == null) {
            synchronized (ServerReconnectService.class) {
                if (INSTANCE == null) INSTANCE = new ServerReconnectService();
            }
        }
        return INSTANCE;
    }

    public long armReconnect(int delayMinutes, String contextTag) {
        int safeDelay = Math.max(0, delayMinutes);
        String safeContext = contextTag == null ? "" : contextTag.trim();
        if (safeContext.isEmpty()) safeContext = "unknown";
        int selectedDelaySeconds = selectReconnectDelaySeconds(safeDelay);
        return armReconnectInternal(safeDelay, selectedDelaySeconds, safeContext);
    }

    public long armReconnectSeconds(int delaySeconds, String contextTag) {
        int safeSeconds = Math.max(0, delaySeconds);
        int approxMinutes = safeSeconds / 60;
        String safeContext = contextTag == null ? "" : contextTag.trim();
        if (safeContext.isEmpty()) safeContext = "unknown";
        return armReconnectInternal(approxMinutes, safeSeconds, safeContext);
    }

    private long armReconnectInternal(int safeDelayMinutes, int selectedDelaySeconds, String safeContext) {
        long cycleId;

        synchronized (lock) {
            cycleId = nextCycleId++;
            this.activeCycleId = cycleId;
            this.armed = true;
            this.stage = ReconnectStage.AWAITING_RECONNECT;
            this.contextTag = safeContext;
            this.delayMinutes = safeDelayMinutes;
            this.effectiveDelaySeconds = selectedDelaySeconds;
            this.armedAtMs = System.currentTimeMillis();
            this.resumeCallbackFired = false;
            this.connectedAtArm = isSuccessfullyConnectedToServer();
            this.joinedSinceArm = false;
            this.disconnectObservedSinceArm = false;
            this.stageEnteredAtMs = this.armedAtMs;
            this.lastAwaitingDiagnosticAtMs = 0L;
            this.crackedLoginSentThisStage = false;
            this.crackedLoginMissingPasswordSinceMs = 0L;
            this.crackedLobbyPathCompleted = false;
            this.mainLobbyPathCompleted = false;
            this.deferredRestorePending = false;
            this.deferredRestoreCycleId = 0L;
            this.deferredRestoreTarget = PendingRestoreGate.NONE;
            this.deferredRestoreTimerWasActive = false;
            this.deferredRestoreSpeedWasActive = false;
            this.deferredRestoreReason = "";
            resetPathRuntimeLocked();
        }

        configureAutoReconnectSeconds(selectedDelaySeconds, true);
        debugInfo(
            "Reconnect service armed. cycleId={}, context={}, delayMinutes={}, effectiveDelaySeconds={}, caller={}",
            cycleId,
            safeContext,
            safeDelayMinutes,
            selectedDelaySeconds,
            resolveCallerSummary()
        );
        logReconnectPreflight("armReconnect");
        return cycleId;
    }

    public void disarmReconnect(String reason) {
        String safeReason = reason == null ? "manual-disarm" : reason;
        debugInfo(
            "Reconnect disarm requested. reason='{}', caller={}, thread={}",
            safeReason,
            resolveCallerSummary(),
            Thread.currentThread().getName()
        );
        disarmInternal(safeReason, true, true);
    }

    public boolean isReconnectArmed() {
        synchronized (lock) {
            return armed;
        }
    }

    public ReconnectStage getReconnectStage() {
        synchronized (lock) {
            return stage;
        }
    }

    public ReconnectPreflight getReconnectPreflight() {
        synchronized (lock) {
            AutoReconnect autoReconnect = Modules.get().get(AutoReconnect.class);
            boolean modulePresent = autoReconnect != null;
            boolean moduleActive = modulePresent && autoReconnect.isActive();
            Double moduleDelay = modulePresent ? autoReconnect.time.get() : null;
            boolean lastServerPresent = modulePresent && autoReconnect.lastServerConnection != null;

            return new ReconnectPreflight(
                armed,
                stage,
                activeCycleId,
                effectiveDelaySeconds,
                connectedAtArm,
                joinedSinceArm,
                disconnectObservedSinceArm,
                modulePresent,
                moduleActive,
                moduleDelay,
                lastServerPresent
            );
        }
    }

    public void registerResumeListener(String key, ResumeListener listener) {
        if (key == null || key.isBlank() || listener == null) return;
        synchronized (lock) {
            resumeListeners.put(key, listener);
        }
    }

    public void unregisterResumeListener(String key) {
        if (key == null || key.isBlank()) return;
        synchronized (lock) {
            resumeListeners.remove(key);
        }
    }

    public void registerFailureListener(String key, FailureListener listener) {
        if (key == null || key.isBlank() || listener == null) return;
        synchronized (lock) {
            failureListeners.put(key, listener);
        }
    }

    public void unregisterFailureListener(String key) {
        if (key == null || key.isBlank()) return;
        synchronized (lock) {
            failureListeners.remove(key);
        }
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        FailureDispatch failureDispatch = null;
        boolean shouldLogPreflight = false;
        synchronized (lock) {
            if (!armed) return;
            // Mark reconnect flow as valid only after an observed disconnect from a known connected session.
            if (connectedAtArm || joinedSinceArm) disconnectObservedSinceArm = true;
            shouldLogPreflight = true;
            if (stage == ReconnectStage.CRACKED_LOGIN && crackedLoginSentThisStage) {
                debugWarn("Reconnect hard-fail: disconnected after cracked /login attempt before reaching CRACKED_LOBBY. Disarming AutoReconnect.");
                failureDispatch = snapshotFailureDispatchLocked(
                    FailureReason.DISCONNECTED_AFTER_CRACKED_LOGIN_ATTEMPT,
                    "Disconnected after cracked /login attempt before reaching CRACKED_LOBBY."
                );
                clearArmedStateLocked("cracked-login-disconnect-after-login-attempt", true, true);
            } else {
                stage = ReconnectStage.AWAITING_RECONNECT;
                stageEnteredAtMs = System.currentTimeMillis();
                lastAwaitingDiagnosticAtMs = 0L;
                resumeCallbackFired = false;
                crackedLobbyPathCompleted = false;
                mainLobbyPathCompleted = false;
                crackedLoginSentThisStage = false;
                crackedLoginMissingPasswordSinceMs = 0L;
                resetPathRuntimeLocked();
            }
        }
        if (shouldLogPreflight) logReconnectPreflight("onGameLeft");
        if (failureDispatch != null) fireFailureDispatch(failureDispatch);
    }

    @EventHandler
    private void onGameJoined(GameJoinedEvent event) {
        boolean shouldLogPreflight = false;
        synchronized (lock) {
            if (!armed) return;
            joinedSinceArm = true;
            if (stage == ReconnectStage.DISARMED) stage = ReconnectStage.AWAITING_RECONNECT;
            stageEnteredAtMs = System.currentTimeMillis();
            lastAwaitingDiagnosticAtMs = 0L;
            resetPathRuntimeLocked();
            shouldLogPreflight = true;
        }
        if (shouldLogPreflight) logReconnectPreflight("onGameJoined");
    }

    @EventHandler
    private void onMessage(ReceiveMessageEvent event) {
        String message = event.getMessage().getString();
        if (message == null) return;
        String lower = message.toLowerCase(Locale.ROOT);
        long now = System.currentTimeMillis();

        synchronized (lock) {
            if (!armed) return;
            if (!waitingForPathSuccess) return;
            if (!lower.contains(BARITONE_PATH_COMPLETE_MARKER)) return;
            if (pathStageInFlight != ReconnectStage.CRACKED_LOBBY_PATH && pathStageInFlight != ReconnectStage.MAIN_LOBBY_PATH) return;
            logTimerSpeedSnapshotLocked("path-success-message-received:" + pathStageInFlight);

            moveActiveSnapshotToDeferredRestoreLocked(resolveRestoreTargetForPathStage(pathStageInFlight), "path-success-message");
            clearPathModuleSnapshotLocked();
            waitingForPathSuccess = false;
            pathPreCommandDelayUntilMs = 0L;
            pathPostDelayUntilMs = now + PATH_POST_SUCCESS_DELAY_MS;
            debugInfo("Reconnect path stage {} reported success. Waiting {} ms post-delay.", pathStageInFlight, PATH_POST_SUCCESS_DELAY_MS);
            logTimerSpeedSnapshotLocked("path-success-post-delay-armed:" + pathStageInFlight);
        }
    }

    @EventHandler
    private void onTick(meteordevelopment.meteorclient.events.world.TickEvent.Pre event) {
        ResumeDispatch resumeDispatch = null;
        FailureDispatch failureDispatch = null;
        long now = System.currentTimeMillis();

        synchronized (lock) {
            if (!armed) return;
            logTimerSpeedEdgeLocked("onTick");
            ensureAutoReconnectEnabledLocked();

            ServerState state = ServerStatusHandler.getInstance().getCommittedState();
            if (tryApplyDeferredRestoreLocked("onTick-pre-stage", state, now)) return;
            updateProgressFlagsFromStateLocked(state);

            ReconnectStage desired = desiredStageForStateLocked(state);
            if (desired != stage) {
                if (stage == ReconnectStage.MAIN_SERVER_READY && desired != ReconnectStage.MAIN_SERVER_READY) {
                    resumeCallbackFired = false;
                }
                stage = desired;
                stageEnteredAtMs = now;
                lastAwaitingDiagnosticAtMs = 0L;
                resetPathRuntimeLocked();
                crackedLoginSentThisStage = false;
                crackedLoginMissingPasswordSinceMs = 0L;
                debugInfo("Reconnect service stage -> {} (serverState={})", stage, state);
                logTimerSpeedSnapshotLocked("stage-transition:" + stage);
                if (stage == ReconnectStage.AWAITING_RECONNECT && disconnectObservedSinceArm) {
                    logReconnectPreflight("stage->AWAITING_RECONNECT");
                }
            }

            if (stage == ReconnectStage.AWAITING_RECONNECT) {
                logAwaitingReconnectDiagnosticLocked(now, state);
            }

            switch (stage) {
                case CRACKED_LOGIN -> {
                    String crackedPassword = resolveCrackedPassword();
                    if (crackedPassword.isEmpty()) {
                        if (crackedLoginMissingPasswordSinceMs <= 0L) {
                            crackedLoginMissingPasswordSinceMs = now;
                            debugWarn(
                                "Reconnect CRACKED_LOGIN entered without cracked password. Waiting up to {} ms for non-cracked route/state transition before hard-fail.",
                                CRACKED_LOGIN_MISSING_PASSWORD_GRACE_MS
                            );
                        }

                        if (now - crackedLoginMissingPasswordSinceMs >= CRACKED_LOGIN_MISSING_PASSWORD_GRACE_MS) {
                            failureDispatch = hardFailMissingPasswordLocked();
                        }
                        break;
                    }
                    crackedLoginMissingPasswordSinceMs = 0L;

                    if (!crackedLoginSentThisStage) {
                        if (mc != null && mc.getNetworkHandler() != null) {
                            mc.getNetworkHandler().sendChatCommand("login " + crackedPassword);
                            crackedLoginSentThisStage = true;
                            debugInfo("Reconnect service sent cracked login command.");
                        }
                    }
                }

                case CRACKED_LOBBY_PATH, MAIN_LOBBY_PATH -> tickPathStageLocked(stage, now);

                case MAIN_SERVER_READY -> {
                    if (!resumeCallbackFired) {
                        resumeCallbackFired = true;
                        resumeDispatch = snapshotResumeDispatchLocked(now);
                    }
                }

                default -> {
                    // no-op
                }
            }
        }

        if (resumeDispatch != null) fireResumeDispatch(resumeDispatch);
        if (failureDispatch != null) fireFailureDispatch(failureDispatch);
    }

    private void tickPathStageLocked(ReconnectStage activeStage, long now) {
        if (pathStageInFlight != activeStage) {
            resetPathRuntimeLocked();
            pathStageInFlight = activeStage;
            logTimerSpeedSnapshotLocked("path-stage-enter:" + activeStage);
        }

        if (pathPostDelayUntilMs > 0L) {
            if (now < pathPostDelayUntilMs) return;

            if (activeStage == ReconnectStage.CRACKED_LOBBY_PATH) crackedLobbyPathCompleted = true;
            if (activeStage == ReconnectStage.MAIN_LOBBY_PATH) mainLobbyPathCompleted = true;
            resetPathRuntimeLocked();
            return;
        }

        if (waitingForPathSuccess) {
            if (now < pathDeadlineAtMs) return;

            debugWarn("Reconnect path stage {} timed out waiting for success message. Retrying same stage.", activeStage);
            logTimerSpeedSnapshotLocked("path-timeout-before-cancel:" + activeStage);
            cancelBaritonePathingLocked();
            waitingForPathSuccess = false;
            pathPreCommandDelayUntilMs = now + PATH_PRE_COMMAND_DELAY_MS;
            pathPostDelayUntilMs = 0L;
            pathDeadlineAtMs = 0L;
            return;
        }

        if (!pathModulesForcedOff) {
            // Always take a fresh snapshot per command attempt to avoid stale restore state.
            clearPathModuleSnapshotLocked();
            if (!captureAndForcePathModulesOffLocked(activeStage)) return;
            pathModulesForcedOff = true;
            pathPreCommandDelayUntilMs = now + PATH_PRE_COMMAND_DELAY_MS;
            debugInfo(
                "Reconnect path stage {} armed pre-command delay ({} ms) before goto.",
                activeStage,
                PATH_PRE_COMMAND_DELAY_MS
            );
            logTimerSpeedSnapshotLocked("path-force-off-ready:" + activeStage);
            return;
        }

        if (pathPreCommandDelayUntilMs <= 0L) pathPreCommandDelayUntilMs = now + PATH_PRE_COMMAND_DELAY_MS;
        if (now < pathPreCommandDelayUntilMs) return;

        if (!executePortalPathCommandLocked(activeStage)) {
            logTimerSpeedSnapshotLocked("path-start-failed-before-restore:" + activeStage);
            pathPreCommandDelayUntilMs = now + PATH_PRE_COMMAND_DELAY_MS;
            return;
        }

        waitingForPathSuccess = true;
        pathPreCommandDelayUntilMs = 0L;
        pathDeadlineAtMs = now + PATH_STAGE_TIMEOUT_MS;
    }

    private boolean executePortalPathCommandLocked(ReconnectStage activeStage) {
        if (!BaritoneUtils.IS_AVAILABLE) {
            debugWarn("Reconnect path stage {} skipped: Baritone not available.", activeStage);
            return false;
        }

        IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
        if (baritone == null) {
            debugWarn("Reconnect path stage {} skipped: primary Baritone missing.", activeStage);
            return false;
        }

        try {
            logTimerSpeedSnapshotLocked("before-goto-command:" + activeStage);
            baritone.getPathingBehavior().cancelEverything();
            baritone.getCommandManager().execute(PORTAL_COMMAND);
            pathAttemptCount++;
            debugInfo("Reconnect path stage {} started attempt {}.", activeStage, pathAttemptCount);
            return true;
        } catch (Throwable t) {
            debugWarn("Reconnect path stage {} failed to start: {}", activeStage, t.toString());
            return false;
        }
    }

    private FailureDispatch hardFailMissingPasswordLocked() {
        final String reason = "Cracked password is missing in THM Tab. Reconnect routine aborted.";
        debugWarn(reason);

        FailureDispatch dispatch = snapshotFailureDispatchLocked(
            FailureReason.MISSING_CRACKED_PASSWORD,
            "Missing cracked password in THM Tab."
        );
        disableAutoReconnectLocked();
        boolean autoReconnectDisabled = isAutoReconnectDisabledLocked();
        clearArmedStateLocked("missing-cracked-password", false, true);

        if (mc != null && mc.getNetworkHandler() != null && mc.getNetworkHandler().getConnection() != null) {
            String message = autoReconnectDisabled ? reason : reason + " (Failed to disable AutoReconnect cleanly.)";
            mc.getNetworkHandler().getConnection().disconnect(Text.literal(message));
        }

        return dispatch;
    }

    private ResumeDispatch snapshotResumeDispatchLocked(long detectedAtMs) {
        return new ResumeDispatch(new HashMap<>(resumeListeners), activeCycleId, contextTag, armedAtMs, detectedAtMs);
    }

    private FailureDispatch snapshotFailureDispatchLocked(FailureReason reason, String detail) {
        return new FailureDispatch(new HashMap<>(failureListeners), activeCycleId, reason, detail, contextTag, armedAtMs, System.currentTimeMillis());
    }

    private void fireResumeDispatch(ResumeDispatch dispatch) {
        if (dispatch == null || dispatch.listeners().isEmpty()) return;
        if (mc == null) return;

        for (ResumeListener listener : dispatch.listeners().values()) {
            if (listener == null) continue;
            try {
                mc.execute(() -> {
                    try {
                        listener.onMainServerReady(dispatch.cycleId(), dispatch.contextTag(), dispatch.armedAtMs(), dispatch.detectedAtMs());
                    } catch (Throwable t) {
                        debugWarn("Reconnect resume listener failed: {}", t.toString());
                    }
                });
            } catch (Throwable t) {
                debugWarn("Failed to schedule reconnect resume listener on client thread: {}", t.toString());
            }
        }
    }

    private void fireFailureDispatch(FailureDispatch dispatch) {
        if (dispatch == null || dispatch.listeners().isEmpty()) return;
        if (mc == null) return;

        for (FailureListener listener : dispatch.listeners().values()) {
            if (listener == null) continue;
            try {
                mc.execute(() -> {
                    try {
                        listener.onReconnectFailure(
                            dispatch.cycleId(),
                            dispatch.reason(),
                            dispatch.detail(),
                            dispatch.contextTag(),
                            dispatch.armedAtMs(),
                            dispatch.failedAtMs()
                        );
                    } catch (Throwable t) {
                        debugWarn("Reconnect failure listener failed: {}", t.toString());
                    }
                });
            } catch (Throwable t) {
                debugWarn("Failed to schedule reconnect failure listener on client thread: {}", t.toString());
            }
        }
    }

    private void updateProgressFlagsFromStateLocked(ServerState state) {
        switch (state) {
            case CRACKED_LOGIN -> {
                crackedLobbyPathCompleted = false;
                mainLobbyPathCompleted = false;
            }
            case CRACKED_LOBBY -> mainLobbyPathCompleted = false;
            case MAIN_LOBBY -> crackedLobbyPathCompleted = true;
            case TRANSFER_SERVER, MAIN_SERVER -> {
                crackedLobbyPathCompleted = true;
                mainLobbyPathCompleted = true;
            }
            default -> {
                // no-op
            }
        }
    }

    private void logAwaitingReconnectDiagnosticLocked(long now, ServerState committedState) {
        if (now - lastAwaitingDiagnosticAtMs < AWAITING_RECONNECT_DIAGNOSTIC_INTERVAL_MS) return;
        lastAwaitingDiagnosticAtMs = now;

        ServerStatusHandler.DetectionDebugSnapshot detection = ServerStatusHandler.getInstance().getDetectionDebugSnapshot();
        long stageAgeMs = stageEnteredAtMs > 0L ? Math.max(0L, now - stageEnteredAtMs) : -1L;
        long sinceLastJoinMs = detection.lastJoinAtMs() > 0L ? Math.max(0L, now - detection.lastJoinAtMs()) : -1L;

        debugInfo(
            "Reconnect awaiting diagnostic: cycleId={}, stageAgeMs={}, joinedSinceArm={}, disconnectObservedSinceArm={}, committedState={}, candidateState={}, candidateStableTicks={}, candidateConfidence={}, sinceLastJoinMs={}, evidence={}",
            activeCycleId,
            stageAgeMs,
            joinedSinceArm,
            disconnectObservedSinceArm,
            committedState,
            detection.candidateState(),
            detection.candidateStableTicks(),
            detection.candidateConfidence(),
            sinceLastJoinMs,
            detection.lastEvidenceSummary()
        );
    }

    private boolean canToggleTimerSpeedNowLocked() {
        if (mc == null || mc.player == null || mc.world == null) return false;
        if (mc.getNetworkHandler() == null || mc.getNetworkHandler().getConnection() == null) return false;
        return mc.getNetworkHandler().getConnection().isOpen();
    }

    private void moveActiveSnapshotToDeferredRestoreLocked(PendingRestoreGate target, String reason) {
        if (!pathModuleSnapshotCaptured) return;
        if (target == null || target == PendingRestoreGate.NONE) {
            debugInfo("Dropped active path snapshot (reason={}, ownerStage={}, cycleId={}).", reason, pathSnapshotOwnerStage, pathSnapshotCycleId);
            clearPathModuleSnapshotLocked();
            return;
        }

        deferredRestorePending = true;
        deferredRestoreCycleId = pathSnapshotCycleId;
        deferredRestoreTarget = target;
        deferredRestoreTimerWasActive = pathTimerWasActive;
        deferredRestoreSpeedWasActive = pathSpeedWasActive;
        deferredRestoreReason = reason == null ? "unknown" : reason;

        debugInfo(
            "Deferred Timer/Speed restore staged (reason={}, target={}, cycleId={}, timerActive={}, speedActive={}, ownerStage={}).",
            deferredRestoreReason,
            deferredRestoreTarget,
            deferredRestoreCycleId,
            deferredRestoreTimerWasActive,
            deferredRestoreSpeedWasActive,
            pathSnapshotOwnerStage
        );
    }

    private void clearDeferredRestoreLocked() {
        deferredRestorePending = false;
        deferredRestoreCycleId = 0L;
        deferredRestoreTarget = PendingRestoreGate.NONE;
        deferredRestoreTimerWasActive = false;
        deferredRestoreSpeedWasActive = false;
        deferredRestoreReason = "";
    }

    private boolean isPathStageActiveLocked() {
        return stage == ReconnectStage.CRACKED_LOBBY_PATH
            || stage == ReconnectStage.MAIN_LOBBY_PATH
            || pathStageInFlight == ReconnectStage.CRACKED_LOBBY_PATH
            || pathStageInFlight == ReconnectStage.MAIN_LOBBY_PATH;
    }

    private boolean isDeferredRestoreTargetSatisfiedLocked(PendingRestoreGate target, ServerState state) {
        if (target == null || target == PendingRestoreGate.NONE) return true;
        return switch (target) {
            case MAIN_LOBBY -> state == ServerState.MAIN_LOBBY;
            case MAIN_SERVER -> state == ServerState.MAIN_SERVER || stage == ReconnectStage.MAIN_SERVER_READY;
            default -> true;
        };
    }

    private boolean tryApplyDeferredRestoreLocked(String trigger, ServerState state, long now) {
        if (!deferredRestorePending) return false;
        if (deferredRestoreCycleId != activeCycleId) {
            logPendingRestoreBlockedLocked("cycle-mismatch", trigger);
            clearDeferredRestoreLocked();
            return false;
        }
        if (waitingForPathSuccess) {
            logPendingRestoreBlockedLocked("waiting-for-path-success", trigger);
            return false;
        }
        if (isPathStageActiveLocked()) {
            logPendingRestoreBlockedLocked("path-stage-active", trigger);
            return false;
        }
        if (!isDeferredRestoreTargetSatisfiedLocked(deferredRestoreTarget, state)) {
            logPendingRestoreBlockedLocked("gate-not-satisfied", trigger);
            return false;
        }
        if (stageEnteredAtMs > 0L && now - stageEnteredAtMs < MODULE_RESTORE_STAGE_STABLE_MS) {
            logPendingRestoreBlockedLocked("stage-not-stable", trigger);
            return false;
        }
        if (!canToggleTimerSpeedNowLocked()) {
            logPendingRestoreBlockedLocked("toggle-context-unavailable", trigger);
            return false;
        }

        logTimerSpeedSnapshotLocked("deferred-restore-apply-before:" + trigger);
        applyTimerSpeedTargetsLocked(deferredRestoreTimerWasActive, deferredRestoreSpeedWasActive);
        debugInfo(
            "Deferred Timer/Speed restore applied on {} (reason={}, target={}, cycleId={}, timerActive={}, speedActive={}).",
            trigger,
            deferredRestoreReason,
            deferredRestoreTarget,
            deferredRestoreCycleId,
            deferredRestoreTimerWasActive,
            deferredRestoreSpeedWasActive
        );
        clearDeferredRestoreLocked();
        logTimerSpeedSnapshotLocked("deferred-restore-apply-after:" + trigger);
        return true;
    }

    private PendingRestoreGate resolveRestoreTargetForPathStage(ReconnectStage activeStage) {
        if (activeStage == ReconnectStage.CRACKED_LOBBY_PATH) return PendingRestoreGate.MAIN_LOBBY;
        if (activeStage == ReconnectStage.MAIN_LOBBY_PATH) return PendingRestoreGate.MAIN_SERVER;
        return PendingRestoreGate.NONE;
    }

    private void applyTimerSpeedTargetsLocked(boolean timerShouldBeActive, boolean speedShouldBeActive) {
        Timer timer = Modules.get().get(Timer.class);
        Speed speed = Modules.get().get(Speed.class);
        boolean timerBefore = timer != null && timer.isActive();
        boolean speedBefore = speed != null && speed.isActive();

        if (timer != null && timer.isActive() != timerShouldBeActive) {
            try {
                timer.toggle();
            } catch (Throwable t) {
                debugWarn("Failed to apply Timer target state (targetActive={}): {}", timerShouldBeActive, t.toString());
            }
        }

        if (speed != null && speed.isActive() != speedShouldBeActive) {
            try {
                speed.toggle();
            } catch (Throwable t) {
                debugWarn("Failed to apply Speed target state (targetActive={}): {}", speedShouldBeActive, t.toString());
            }
        }

        boolean timerAfter = timer != null && timer.isActive();
        boolean speedAfter = speed != null && speed.isActive();
        debugInfo(
            "Applied Timer/Speed target states (targetTimer={}, targetSpeed={}, beforeTimer={}, beforeSpeed={}, afterTimer={}, afterSpeed={}).",
            timerShouldBeActive,
            speedShouldBeActive,
            timerBefore,
            speedBefore,
            timerAfter,
            speedAfter
        );
    }

    private void logPendingRestoreBlockedLocked(String reason, String trigger) {
        if (!TIMER_SPEED_TRACE_LOGS) return;
        long now = System.currentTimeMillis();
        if (now - lastRestoreBlockedLogAtMs < RESTORE_BLOCK_LOG_INTERVAL_MS) return;
        lastRestoreBlockedLogAtMs = now;
        debugInfo(
            "Deferred Timer/Speed restore blocked (reason={}, trigger={}, target={}, stage={}, inFlightStage={}, waitingForPathSuccess={}, preDelayMs={}, postDelayMs={}, deadlineMs={}, deferredReason={}, deferredCycleId={}, activeCycleId={}).",
            reason,
            trigger,
            deferredRestoreTarget,
            stage,
            pathStageInFlight,
            waitingForPathSuccess,
            pathPreCommandDelayUntilMs,
            pathPostDelayUntilMs,
            pathDeadlineAtMs,
            deferredRestoreReason,
            deferredRestoreCycleId,
            activeCycleId
        );
    }

    private void logTimerSpeedEdgeLocked(String source) {
        if (!TIMER_SPEED_TRACE_LOGS) return;
        boolean timerActive = isTimerActiveLocked();
        boolean speedActive = isSpeedActiveLocked();
        if (!traceEdgeInitialized) {
            traceEdgeInitialized = true;
            traceLastTimerActive = timerActive;
            traceLastSpeedActive = speedActive;
            debugInfo(
                "Timer/Speed edge baseline ({}) timerActive={}, speedActive={}, cycleId={}, stage={}, inFlightStage={}.",
                source,
                timerActive,
                speedActive,
                activeCycleId,
                stage,
                pathStageInFlight
            );
            return;
        }

        if (timerActive == traceLastTimerActive && speedActive == traceLastSpeedActive) return;
        debugInfo(
            "Timer/Speed edge change ({}) timer {} -> {}, speed {} -> {}, cycleId={}, stage={}, inFlightStage={}, waitingForPathSuccess={}.",
            source,
            traceLastTimerActive,
            timerActive,
            traceLastSpeedActive,
            speedActive,
            activeCycleId,
            stage,
            pathStageInFlight,
            waitingForPathSuccess
        );
        traceLastTimerActive = timerActive;
        traceLastSpeedActive = speedActive;
    }

    private void logTimerSpeedSnapshotLocked(String point) {
        if (!TIMER_SPEED_TRACE_LOGS) return;
        debugInfo(
            "Timer/Speed snapshot [{}] cycleId={}, stage={}, inFlightStage={}, waitingForPathSuccess={}, preDelayMs={}, postDelayMs={}, deadlineMs={}, pathAttempt={}, snapshotCaptured={}, snapshotOwnerStage={}, snapshotCycleId={}, snapshotTimer={}, snapshotSpeed={}, timerNow={}, speedNow={}, deferredPending={}, deferredTarget={}, deferredReason={}, deferredCycleId={}, deferredTimerTarget={}, deferredSpeedTarget={}.",
            point,
            activeCycleId,
            stage,
            pathStageInFlight,
            waitingForPathSuccess,
            pathPreCommandDelayUntilMs,
            pathPostDelayUntilMs,
            pathDeadlineAtMs,
            pathAttemptCount,
            pathModuleSnapshotCaptured,
            pathSnapshotOwnerStage,
            pathSnapshotCycleId,
            pathTimerWasActive,
            pathSpeedWasActive,
            isTimerActiveLocked(),
            isSpeedActiveLocked(),
            deferredRestorePending,
            deferredRestoreTarget,
            deferredRestoreReason,
            deferredRestoreCycleId,
            deferredRestoreTimerWasActive,
            deferredRestoreSpeedWasActive
        );
    }

    private boolean isTimerActiveLocked() {
        Timer timer = Modules.get().get(Timer.class);
        return timer != null && timer.isActive();
    }

    private boolean isSpeedActiveLocked() {
        Speed speed = Modules.get().get(Speed.class);
        return speed != null && speed.isActive();
    }

    private ReconnectStage desiredStageForStateLocked(ServerState state) {
        if (!disconnectObservedSinceArm) return ReconnectStage.AWAITING_RECONNECT;
        if (state == ServerState.MAIN_LOBBY
            && deferredRestorePending
            && deferredRestoreTarget == PendingRestoreGate.MAIN_LOBBY
            && deferredRestoreCycleId == activeCycleId) {
            return ReconnectStage.AWAITING_RECONNECT;
        }
        return switch (state) {
            case MAIN_SERVER -> ReconnectStage.MAIN_SERVER_READY;
            case CRACKED_LOGIN -> ReconnectStage.CRACKED_LOGIN;
            case CRACKED_LOBBY -> crackedLobbyPathCompleted ? ReconnectStage.TRANSFER_WAIT : ReconnectStage.CRACKED_LOBBY_PATH;
            case MAIN_LOBBY -> mainLobbyPathCompleted ? ReconnectStage.TRANSFER_WAIT : ReconnectStage.MAIN_LOBBY_PATH;
            case TRANSFER_SERVER -> ReconnectStage.TRANSFER_WAIT;
            case UNKNOWN -> ReconnectStage.AWAITING_RECONNECT;
        };
    }

    private String resolveCrackedPassword() {
        THMSystem system = THMSystem.get();
        if (system == null) return "";
        String value = system.getCrackedPassword();
        if (value == null) return "";
        return value.trim();
    }

    private void capturePathModuleSnapshotLocked(ReconnectStage ownerStage) {
        Timer timer = Modules.get().get(Timer.class);
        Speed speed = Modules.get().get(Speed.class);

        pathTimerWasActive = timer != null && timer.isActive();
        pathSpeedWasActive = speed != null && speed.isActive();
        pathModuleSnapshotCaptured = true;
        pathModulesForcedOff = false;
        pathSnapshotOwnerStage = ownerStage == null ? ReconnectStage.DISARMED : ownerStage;
        pathSnapshotCycleId = activeCycleId;

        debugInfo(
            "Reconnect path module snapshot captured (timerActive={}, speedActive={}, ownerStage={}, cycleId={}).",
            pathTimerWasActive,
            pathSpeedWasActive,
            pathSnapshotOwnerStage,
            pathSnapshotCycleId
        );
        logTimerSpeedSnapshotLocked("snapshot-captured");
    }

    private boolean captureAndForcePathModulesOffLocked(ReconnectStage ownerStage) {
        if (!canToggleTimerSpeedNowLocked()) return false;
        capturePathModuleSnapshotLocked(ownerStage);
        return forcePathModulesOffLocked();
    }

    private boolean forcePathModulesOffLocked() {
        if (!pathModuleSnapshotCaptured) return false;
        if (!canToggleTimerSpeedNowLocked()) {
            debugInfo("Reconnect path force-off blocked: toggle context unavailable.");
            return false;
        }

        Timer timer = Modules.get().get(Timer.class);
        Speed speed = Modules.get().get(Speed.class);
        boolean timerBefore = timer != null && timer.isActive();
        boolean speedBefore = speed != null && speed.isActive();
        debugInfo(
            "Reconnect path force-off attempt (timerBefore={}, speedBefore={}, snapshotTimer={}, snapshotSpeed={}, stage={}, inFlightStage={}, nextAttempt={}).",
            timerBefore,
            speedBefore,
            pathTimerWasActive,
            pathSpeedWasActive,
            stage,
            pathStageInFlight,
            pathAttemptCount + 1
        );

        if (timer != null && timer.isActive()) {
            try {
                timer.toggle();
            } catch (Throwable t) {
                debugWarn("Failed to toggle Timer OFF for reconnect path prep: {}", t.toString());
            }
        }
        if (speed != null && speed.isActive()) {
            try {
                speed.toggle();
            } catch (Throwable t) {
                debugWarn("Failed to toggle Speed OFF for reconnect path prep: {}", t.toString());
            }
        }

        boolean timerOff = timer == null || !timer.isActive();
        boolean speedOff = speed == null || !speed.isActive();
        if (timerOff && speedOff) {
            debugInfo(
                "Reconnect path prep confirmed Timer/Speed OFF before goto (snapshot timerActive={}, speedActive={}).",
                pathTimerWasActive,
                pathSpeedWasActive
            );
            logTimerSpeedSnapshotLocked("force-off-confirmed");
            return true;
        }

        debugWarn(
            "Reconnect path force-off failed to confirm OFF (timerOff={}, speedOff={}, timerNow={}, speedNow={}).",
            timerOff,
            speedOff,
            timer != null && timer.isActive(),
            speed != null && speed.isActive()
        );
        return false;
    }

    private void clearPathModuleSnapshotLocked() {
        pathModuleSnapshotCaptured = false;
        pathTimerWasActive = false;
        pathSpeedWasActive = false;
        pathModulesForcedOff = false;
        pathSnapshotOwnerStage = ReconnectStage.DISARMED;
        pathSnapshotCycleId = 0L;
    }

    private void ensureAutoReconnectEnabledLocked() {
        int seconds = effectiveDelaySeconds > 0 ? effectiveDelaySeconds : Math.max(0, delayMinutes) * 60;
        configureAutoReconnectSeconds(seconds, true);
    }

    private void clearArmedStateLocked(String reason, boolean disableAutoReconnect, boolean cancelBaritonePath) {
        boolean armedBefore = armed;
        ReconnectStage stageBefore = stage;
        long cycleBefore = activeCycleId;
        if (cancelBaritonePath) cancelBaritonePathingLocked();
        if (disableAutoReconnect) disableAutoReconnectLocked();
        clearPathModuleSnapshotLocked();
        clearDeferredRestoreLocked();

        armed = false;
        activeCycleId = 0L;
        stage = ReconnectStage.DISARMED;
        contextTag = "";
        delayMinutes = 0;
        effectiveDelaySeconds = 0;
        armedAtMs = 0L;
        disconnectObservedSinceArm = false;
        connectedAtArm = false;
        joinedSinceArm = false;
        stageEnteredAtMs = 0L;
        lastAwaitingDiagnosticAtMs = 0L;
        resumeCallbackFired = false;
        crackedLoginSentThisStage = false;
        crackedLoginMissingPasswordSinceMs = 0L;
        crackedLobbyPathCompleted = false;
        mainLobbyPathCompleted = false;
        deferredRestorePending = false;
        deferredRestoreCycleId = 0L;
        deferredRestoreTarget = PendingRestoreGate.NONE;
        deferredRestoreTimerWasActive = false;
        deferredRestoreSpeedWasActive = false;
        deferredRestoreReason = "";
        resetPathRuntimeLocked();

        debugInfo(
            "Reconnect service disarmed: reason='{}', armedBefore={}, stageBefore={}, cycleBefore={}, disableAutoReconnect={}, cancelBaritonePath={}, caller={}, thread={}",
            reason,
            armedBefore,
            stageBefore,
            cycleBefore,
            disableAutoReconnect,
            cancelBaritonePath,
            resolveCallerSummary(),
            Thread.currentThread().getName()
        );
    }

    private boolean isSuccessfullyConnectedToServer() {
        if (mc == null || mc.getNetworkHandler() == null || mc.getNetworkHandler().getConnection() == null) return false;
        if (!mc.getNetworkHandler().getConnection().isOpen()) return false;
        return mc.player != null && mc.world != null;
    }

    private void disarmInternal(String reason, boolean disableAutoReconnect, boolean cancelBaritonePath) {
        synchronized (lock) {
            if (!armed && stage == ReconnectStage.DISARMED) {
                debugInfo(
                    "Reconnect disarm request ignored (already disarmed). reason='{}', caller={}, thread={}",
                    reason,
                    resolveCallerSummary(),
                    Thread.currentThread().getName()
                );
                return;
            }
            clearArmedStateLocked(reason, disableAutoReconnect, cancelBaritonePath);
        }
    }

    private String resolveCallerSummary() {
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        StringBuilder sb = new StringBuilder();
        int added = 0;
        for (StackTraceElement e : stack) {
            String cls = e.getClassName();
            if (cls.equals(Thread.class.getName())) continue;
            if (cls.equals(ServerReconnectService.class.getName())) continue;
            if (cls.startsWith("java.lang.reflect.") || cls.startsWith("jdk.internal.reflect.")) continue;
            if (added > 0) sb.append(" <- ");
            sb.append(cls).append(".").append(e.getMethodName()).append(":").append(e.getLineNumber());
            added++;
            if (added >= 4) break;
        }
        return added == 0 ? "unknown" : sb.toString();
    }

    private void configureAutoReconnectSeconds(double seconds, boolean enableIfNeeded) {
        AutoReconnect autoReconnect = Modules.get().get(AutoReconnect.class);
        if (autoReconnect == null) {
            debugWarn("Reconnect service: AutoReconnect module not found.");
            return;
        }

        autoReconnect.time.set(Math.max(0.0, seconds));
        if (enableIfNeeded && !autoReconnect.isActive()) autoReconnect.toggle();
    }

    private void logReconnectPreflight(String source) {
        ReconnectPreflight preflight = getReconnectPreflight();
        debugInfo(
            "Reconnect preflight [{}]: armed={}, stage={}, cycleId={}, effectiveDelaySeconds={}, connectedAtArm={}, joinedSinceArm={}, disconnectObservedSinceArm={}, modulePresent={}, moduleActive={}, moduleDelaySeconds={}, lastServerConnectionPresent={}",
            source,
            preflight.serviceArmed(),
            preflight.stage(),
            preflight.cycleId(),
            preflight.effectiveDelaySeconds(),
            preflight.connectedAtArm(),
            preflight.joinedSinceArm(),
            preflight.disconnectObservedSinceArm(),
            preflight.autoReconnectModulePresent(),
            preflight.autoReconnectActive(),
            preflight.autoReconnectSettingDelaySeconds(),
            preflight.lastServerConnectionPresent()
        );
    }

    private int selectReconnectDelaySeconds(int delayMinutes) {
        if (delayMinutes <= 0) return 0;

        int baseSeconds = delayMinutes * 60;
        int minSeconds;
        int maxSeconds;

        if (delayMinutes == 1) {
            // Special case requested: 1 minute behaves as +2/-0 minutes.
            minSeconds = 60;
            maxSeconds = 180;
        } else {
            // Default jitter: +/- 1 minute around configured delay.
            minSeconds = Math.max(0, baseSeconds - 60);
            maxSeconds = baseSeconds + 60;
        }

        if (minSeconds >= maxSeconds) return minSeconds;
        return ThreadLocalRandom.current().nextInt(minSeconds, maxSeconds + 1);
    }

    private void disableAutoReconnectLocked() {
        AutoReconnect autoReconnect = Modules.get().get(AutoReconnect.class);
        if (autoReconnect == null) return;
        if (autoReconnect.isActive()) autoReconnect.toggle();
    }

    private boolean isAutoReconnectDisabledLocked() {
        AutoReconnect autoReconnect = Modules.get().get(AutoReconnect.class);
        return autoReconnect == null || !autoReconnect.isActive();
    }

    private void cancelBaritonePathingLocked() {
        if (!BaritoneUtils.IS_AVAILABLE) return;
        IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
        if (baritone == null) return;
        try {
            baritone.getPathingBehavior().cancelEverything();
            baritone.getCustomGoalProcess().setGoal(null);
        } catch (Throwable ignored) {
        }
    }

    private void resetPathRuntimeLocked() {
        logTimerSpeedSnapshotLocked("reset-path-runtime-before");
        if (pathModuleSnapshotCaptured) {
            moveActiveSnapshotToDeferredRestoreLocked(resolveRestoreTargetForPathStage(pathSnapshotOwnerStage), "path-runtime-reset");
        }
        clearPathModuleSnapshotLocked();
        pathStageInFlight = ReconnectStage.DISARMED;
        waitingForPathSuccess = false;
        pathPreCommandDelayUntilMs = 0L;
        pathPostDelayUntilMs = 0L;
        pathDeadlineAtMs = 0L;
        pathAttemptCount = 0;
        logTimerSpeedSnapshotLocked("reset-path-runtime-after");
    }

    private record ResumeDispatch(
        Map<String, ResumeListener> listeners,
        long cycleId,
        String contextTag,
        long armedAtMs,
        long detectedAtMs
    ) {}

    private record FailureDispatch(
        Map<String, FailureListener> listeners,
        long cycleId,
        FailureReason reason,
        String detail,
        String contextTag,
        long armedAtMs,
        long failedAtMs
    ) {}
}

