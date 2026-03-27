package xyz.thm.addon.utils;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.game.GameJoinedEvent;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.gui.hud.MessageIndicator;
import net.minecraft.world.GameMode;
import xyz.thm.addon.THMAddon;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/**
 * Always-on server-state detection service for THM modules.
 *
 * <p>This singleton runs independently of module toggles and exposes async/sync-style state APIs
 * for callers that need reliable phase detection.
 *
 * <p><b>How detection works (current implementation)</b>
 * <ul>
 *     <li>Sampling cadence: about every 100 ms on client thread.</li>
 *     <li>Commit stabilization: 3 consistent candidate samples with minimum confidence 3.</li>
 *     <li>Deterministic scoring source: self {@code PlayerListEntry} gamemode only.
 *     If that source is unavailable, state remains {@link ServerState#UNKNOWN}.</li>
 *     <li>System-message spoof hardening for cracked prompt:
 *     only system-indicated chat can arm cracked-login evidence.</li>
 * </ul>
 *
 * <p><b>State rules</b>
 * <ul>
 *     <li>{@link ServerState#CRACKED_LOGIN}:
 *     trusted prompt active + ADVENTURE + {@code minecraft:the_end} + teleport gate not met.</li>
 *     <li>{@link ServerState#CRACKED_LOBBY}:
 *     ADVENTURE + {@code minecraft:the_end} + teleport gate met
 *     (distance from captured prompt position >= 3 blocks).</li>
 *     <li>{@link ServerState#MAIN_LOBBY}:
 *     ADVENTURE + {@code minecraft:overworld}.</li>
 *     <li>{@link ServerState#TRANSFER_SERVER}: SPECTATOR.</li>
 *     <li>{@link ServerState#MAIN_SERVER}: SURVIVAL (dimension-agnostic).</li>
 *     <li>Otherwise {@link ServerState#UNKNOWN}.</li>
 * </ul>
 *
 * <p><b>API usage</b>
 * <pre>{@code
 * import xyz.thm.addon.utils.ServerStatusHandler;
 * import xyz.thm.addon.utils.ServerStatusHandler.ServerState;
 *
 * private final ServerStatusHandler status = ServerStatusHandler.getInstance();
 * }</pre>
 *
 * <p>Async (recommended):
 * <pre>{@code
 * status.returnStateAsync().thenAccept(state -> {
 *     if (state == ServerState.MAIN_SERVER) {
 *         // main-server logic
 *     }
 * });
 *
 * status.returnStateAsync(1000).thenAccept(state -> {
 *     // resolves early when known, else UNKNOWN near timeout
 * });
 *
 * // Async aliases:
 * status.returnState().thenAccept(state -> { });      // default timeout
 * status.returnState(5000).thenAccept(state -> { });  // custom timeout
 * }</pre>
 *
 * <p>Default async timeout for no-arg calls is 10 seconds.
 *
 * <p>Blocking helper (off-thread only):
 * <pre>{@code
 * ServerState state = status.returnStateBlocking(1000);
 * }</pre>
 *
 * <p><b>Immediate snapshot helpers (no waiting)</b>
 * <ul>
 *     <li>{@link #getCommittedState()}</li>
 *     <li>{@link #isMainServer()}</li>
 *     <li>{@link #isTransferServer()}</li>
 *     <li>{@link #isMainLobby()}</li>
 *     <li>{@link #isCrackedLogin()}</li>
 *     <li>{@link #isCrackedLobby()}</li>
 *     <li>{@link #getLastTransitionAtMs()}</li>
 *     <li>{@link #getLastEvidenceSummary()}</li>
 *     <li>{@link #getDetectionDebugSnapshot()} for candidate/commit debug view</li>
 * </ul>
 *
 * <p><b>Return semantics for async APIs</b>
 * <ul>
 *     <li>If committed state is known: complete immediately.</li>
 *     <li>If committed state is unknown: wait up to per-request timeout.</li>
 *     <li>If known state appears before timeout: complete early with that state.</li>
 *     <li>If still unknown at deadline: complete with {@link ServerState#UNKNOWN}.</li>
 * </ul>
 */


public final class ServerStatusHandler {
    public enum ServerState {
        UNKNOWN,
        MAIN_LOBBY,
        CRACKED_LOGIN,
        CRACKED_LOBBY,
        TRANSFER_SERVER,
        MAIN_SERVER
    }

    public record DetectionDebugSnapshot(
        ServerState committedState,
        ServerState candidateState,
        int candidateStableTicks,
        int candidateConfidence,
        long lastTransitionAtMs,
        long lastJoinAtMs,
        String lastEvidenceSummary
    ) {}

    private static final long DEFAULT_API_TIMEOUT_MS = 10_000L;
    private static final long CHECK_INTERVAL_MS = 100L; // ~2 ticks
    private static final int STABILIZATION_TICKS = 3;
    private static final int MIN_CONFIDENCE = 3;
    private static final long CRACKED_PROMPT_JOIN_WINDOW_MS = 45_000L;
    private static final long CRACKED_PROMPT_TTL_MS = 20_000L;
    private static final int CRACKED_LOGIN_TELEPORT_MIN_BLOCKS = 3;
    private static final long SAMPLE_QUEUE_STUCK_RESET_MS = 2_000L;
    private static final boolean DEBUG_LOGS = false;

    private static volatile ServerStatusHandler INSTANCE;

    private volatile ServerState committedState = ServerState.UNKNOWN;
    private ServerState candidateState = ServerState.UNKNOWN;
    private int candidateStableTicks;
    private int candidateConfidence;
    private long lastTransitionAtMs;
    private String lastEvidenceSummary = "no-evidence";
    private long lastCrackedPromptAtMs;
    private boolean crackedPromptPosValid;
    private int crackedPromptX;
    private int crackedPromptY;
    private int crackedPromptZ;
    private long lastJoinAtMs;
    private long lastDetectionStepAtMs;

    private final Object phaseRequestLock = new Object();
    private final Deque<StateRequest> pendingStateRequests = new ArrayDeque<>();
    private final AtomicBoolean sampleQueued = new AtomicBoolean(false);
    private volatile long sampleQueuedAtMs;
    private volatile boolean workerRunning;
    private Thread worker;

    private static void debugInfo(String message, Object... args) {
        if (DEBUG_LOGS) THMAddon.LOG.info(message, args);
    }

    private static void debugWarn(String message, Object... args) {
        if (DEBUG_LOGS) THMAddon.LOG.warn(message, args);
    }

    private ServerStatusHandler() {
        MeteorClient.EVENT_BUS.subscribe(this);
        startWorker();
    }

    public static ServerStatusHandler getInstance() {
        if (INSTANCE == null) {
            synchronized (ServerStatusHandler.class) {
                if (INSTANCE == null) INSTANCE = new ServerStatusHandler();
            }
        }
        return INSTANCE;
    }

    public CompletableFuture<ServerState> returnStateAsync() {
        return returnStateAsync(DEFAULT_API_TIMEOUT_MS);
    }

    public CompletableFuture<ServerState> returnState() {
        return returnStateAsync(DEFAULT_API_TIMEOUT_MS);
    }

    public CompletableFuture<ServerState> returnState(long timeoutMs) {
        return returnStateAsync(timeoutMs);
    }

    public CompletableFuture<ServerState> returnStateAsync(long timeoutMs) {
        return enqueueStateRequest(timeoutMs);
    }

    // Off-thread wrapper. Prefer async API on gameplay threads.
    public ServerState returnStateBlocking(long timeoutMs) {
        long waitMs = Math.max(0L, timeoutMs);
        try {
            return returnStateAsync(waitMs).get(waitMs + 250L, TimeUnit.MILLISECONDS);
        } catch (Throwable ignored) {
            return ServerState.UNKNOWN;
        }
    }

    public ServerState getCommittedState() {
        return committedState;
    }

    public boolean isMainServer() {
        return committedState == ServerState.MAIN_SERVER;
    }

    public boolean isTransferServer() {
        return committedState == ServerState.TRANSFER_SERVER;
    }

    public boolean isMainLobby() {
        return committedState == ServerState.MAIN_LOBBY;
    }

    public boolean isCrackedLogin() {
        return committedState == ServerState.CRACKED_LOGIN;
    }

    public boolean isCrackedLobby() {
        return committedState == ServerState.CRACKED_LOBBY;
    }

    public long getLastTransitionAtMs() {
        return lastTransitionAtMs;
    }

    public String getLastEvidenceSummary() {
        return lastEvidenceSummary;
    }

    public DetectionDebugSnapshot getDetectionDebugSnapshot() {
        return new DetectionDebugSnapshot(
            committedState,
            candidateState,
            candidateStableTicks,
            candidateConfidence,
            lastTransitionAtMs,
            lastJoinAtMs,
            lastEvidenceSummary
        );
    }

    @EventHandler
    private void onGameJoined(GameJoinedEvent event) {
        lastJoinAtMs = System.currentTimeMillis();
        forceUnknownState("game-joined");
        debugInfo("ServerStatusHandler observed GameJoined. Detection reset to UNKNOWN.");
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        forceUnknownState("game-left");
        debugInfo("ServerStatusHandler observed GameLeft. Detection reset to UNKNOWN.");
    }

    @EventHandler
    private void onMessage(ReceiveMessageEvent event) {
        String msg = event.getMessage().getString();
        String lower = msg == null ? "" : msg.toLowerCase(Locale.ROOT);
        long now = System.currentTimeMillis();

        // Spoof-hardening: only server/system messages may contribute cracked prompt evidence.
        if (!isSystemIndicator(event.getIndicator())) return;
        if (!isTrustedCrackedPrompt(lower, now)) return;

        lastCrackedPromptAtMs = now;
        if (mc != null && mc.player != null) {
            crackedPromptPosValid = true;
            crackedPromptX = mc.player.getBlockX();
            crackedPromptY = mc.player.getBlockY();
            crackedPromptZ = mc.player.getBlockZ();
        } else {
            crackedPromptPosValid = false;
        }
    }

    private static boolean isSystemIndicator(MessageIndicator indicator) {
        if (indicator == null) return false;
        try {
            return indicator == MessageIndicator.system();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean isTrustedCrackedPrompt(String lowerMessage, long nowMs) {
        if (lowerMessage == null || lowerMessage.isBlank()) return false;
        if (nowMs - lastJoinAtMs > CRACKED_PROMPT_JOIN_WINDOW_MS) return false;
        if (!lowerMessage.contains("/login")) return false;
        return lowerMessage.contains("<password>");
    }

    private void startWorker() {
        if (workerRunning) return;
        workerRunning = true;
        worker = new Thread(() -> {
            while (workerRunning) {
                requestSample();
                try {
                    Thread.sleep(Math.max(50L, CHECK_INTERVAL_MS));
                } catch (InterruptedException ignored) {
                }
            }
        }, "THM-ServerStatusHandler");
        worker.setDaemon(true);
        worker.start();
    }

    private void requestSample() {
        if (mc == null) return;
        long now = System.currentTimeMillis();

        if (sampleQueued.get()) {
            long queuedAt = sampleQueuedAtMs;
            if (queuedAt > 0L && now - queuedAt >= SAMPLE_QUEUE_STUCK_RESET_MS) {
                sampleQueued.set(false);
                sampleQueuedAtMs = 0L;
                debugWarn("ServerStatusHandler sample queue watchdog reset triggered after {} ms.", now - queuedAt);
            } else {
                return;
            }
        }

        if (!sampleQueued.compareAndSet(false, true)) return;
        sampleQueuedAtMs = now;

        try {
            mc.execute(() -> {
                try {
                    runSampleOnClientThread();
                } finally {
                    sampleQueued.set(false);
                    sampleQueuedAtMs = 0L;
                }
            });
        } catch (Throwable ignored) {
            sampleQueued.set(false);
            sampleQueuedAtMs = 0L;
        }
    }

    private void runSampleOnClientThread() {
        long now = System.currentTimeMillis();
        EvidenceScore strictScore = scoreStrictEvidence();
        if (now - lastDetectionStepAtMs >= CHECK_INTERVAL_MS) {
            runDetectionStep(now, strictScore);
            lastDetectionStepAtMs = now;
        }
        processPendingStateRequests(now);
    }

    private void runDetectionStep(long now, EvidenceScore strictScore) {
        ServerState nextCandidate = resolveCandidate(strictScore);
        int confidence = strictScore.confidenceFor(nextCandidate);

        if (nextCandidate == candidateState) candidateStableTicks++;
        else {
            candidateState = nextCandidate;
            candidateStableTicks = 1;
        }

        candidateConfidence = confidence;
        lastEvidenceSummary = strictScore.summary();

        if (candidateState != committedState
            && candidateState != ServerState.UNKNOWN
            && candidateConfidence >= MIN_CONFIDENCE
            && candidateStableTicks >= STABILIZATION_TICKS) {
            ServerState old = committedState;
            committedState = candidateState;
            lastTransitionAtMs = now;
            debugInfo(
                "ServerStatus committed transition: {} -> {} (stableTicks={}, confidence={}, evidence={})",
                old,
                committedState,
                candidateStableTicks,
                candidateConfidence,
                lastEvidenceSummary
            );
        } else if (candidateState == ServerState.UNKNOWN
            && committedState != ServerState.UNKNOWN
            && candidateStableTicks >= STABILIZATION_TICKS) {
            ServerState old = committedState;
            committedState = ServerState.UNKNOWN;
            lastTransitionAtMs = now;
            debugInfo(
                "ServerStatus committed transition: {} -> UNKNOWN (stableTicks={}, confidence={}, evidence={})",
                old,
                candidateStableTicks,
                candidateConfidence,
                lastEvidenceSummary
            );
        }
    }

    private EvidenceScore scoreStrictEvidence() {
        EvidenceScore score = new EvidenceScore();
        long now = System.currentTimeMillis();

        // Deterministic commit policy: only self PlayerListEntry gamemode is authoritative.
        // If unavailable, remain UNKNOWN to avoid false positives from weaker sources.
        GameMode gm = resolvePlayerListGameMode();
        if (gm == null) {
            score.reasons.add("strict:missing-player-list-gamemode");
            return score;
        }
        String gmSource = "player-list";

        String dimensionId = "";
        if (mc != null && mc.world != null && mc.world.getRegistryKey() != null) {
            try {
                dimensionId = String.valueOf(mc.world.getRegistryKey().getValue());
            } catch (Throwable ignored) {
            }
        }

        boolean promptActive = isCrackedPromptActive(now);
        boolean crackedAdventureEnd = gm == GameMode.ADVENTURE && "minecraft:the_end".equals(dimensionId);
        boolean postLoginTeleported = hasMetPostLoginTeleportGate();

        if (promptActive && crackedAdventureEnd && !postLoginTeleported) {
            score.crackedLogin = 100;
            score.reasons.add("strict:trusted-prompt+adventure-the_end-awaiting-post-login-teleport+gm-source:" + gmSource);
            return score;
        }

        if (gm == GameMode.ADVENTURE
            && "minecraft:the_end".equals(dimensionId)
            && postLoginTeleported) {
            score.cracked = 100;
            score.reasons.add("strict:gamemode:ADVENTURE+dimension:the_end+teleport->cracked-lobby+gm-source:" + gmSource);
            return score;
        }

        if (gm == GameMode.ADVENTURE && "minecraft:overworld".equals(dimensionId)) {
            score.lobby = 100;
            score.reasons.add("strict:gamemode:ADVENTURE+dimension:overworld->main-lobby+gm-source:" + gmSource);
            return score;
        }

        if (gm == GameMode.SPECTATOR) {
            score.transfer = 100;
            score.reasons.add("strict:gamemode:SPECTATOR->transfer+gm-source:" + gmSource);
            return score;
        }

        if (gm == GameMode.SURVIVAL) {
            score.main = 100;
            score.reasons.add("strict:gamemode:SURVIVAL->main+gm-source:" + gmSource);
            return score;
        }

        score.reasons.add("strict:no-consistent-signal");
        return score;
    }

    private GameMode resolvePlayerListGameMode() {
        if (mc == null || mc.player == null || mc.getNetworkHandler() == null) return null;
        try {
            PlayerListEntry selfEntry = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
            if (selfEntry == null) return null;
            return selfEntry.getGameMode();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private ServerState resolveCandidate(EvidenceScore score) {
        int best = Math.max(Math.max(score.lobby, score.main), Math.max(Math.max(score.crackedLogin, score.cracked), score.transfer));
        if (best < MIN_CONFIDENCE) return ServerState.UNKNOWN;

        int ties = 0;
        if (score.lobby == best) ties++;
        if (score.main == best) ties++;
        if (score.crackedLogin == best) ties++;
        if (score.cracked == best) ties++;
        if (score.transfer == best) ties++;
        if (ties > 1) return ServerState.UNKNOWN;

        if (score.crackedLogin == best) return ServerState.CRACKED_LOGIN;
        if (score.cracked == best) return ServerState.CRACKED_LOBBY;
        if (score.transfer == best) return ServerState.TRANSFER_SERVER;
        if (score.main == best) return ServerState.MAIN_SERVER;
        if (score.lobby == best) return ServerState.MAIN_LOBBY;
        return ServerState.UNKNOWN;
    }

    private boolean isCrackedPromptActive(long nowMs) {
        return nowMs - lastCrackedPromptAtMs <= CRACKED_PROMPT_TTL_MS;
    }

    private boolean hasMetPostLoginTeleportGate() {
        if (!crackedPromptPosValid) return false;
        if (mc == null || mc.player == null) return false;

        long dx = (long) mc.player.getBlockX() - crackedPromptX;
        long dy = (long) mc.player.getBlockY() - crackedPromptY;
        long dz = (long) mc.player.getBlockZ() - crackedPromptZ;
        long distSq = dx * dx + dy * dy + dz * dz;
        long threshold = CRACKED_LOGIN_TELEPORT_MIN_BLOCKS;
        return distSq >= threshold * threshold;
    }

    private void forceUnknownState(String reason) {
        // Defensive reset: if a queued sample was dropped during join/leave transitions,
        // do not let the worker deadlock on a stale queued flag.
        sampleQueued.set(false);
        sampleQueuedAtMs = 0L;

        committedState = ServerState.UNKNOWN;
        candidateState = ServerState.UNKNOWN;
        candidateStableTicks = 0;
        candidateConfidence = 0;
        lastTransitionAtMs = System.currentTimeMillis();
        lastDetectionStepAtMs = 0L;
        lastEvidenceSummary = "scores[lobby=0,main=0,crackedLogin=0,cracked=0,transfer=0],reasons=forced-unknown:" + reason;

        lastCrackedPromptAtMs = 0L;
        crackedPromptPosValid = false;
        crackedPromptX = 0;
        crackedPromptY = 0;
        crackedPromptZ = 0;
    }

    private CompletableFuture<ServerState> enqueueStateRequest(long timeoutMs) {
        long clampedTimeoutMs = Math.max(0L, timeoutMs);
        ServerState current = committedState;
        if (current != ServerState.UNKNOWN) return CompletableFuture.completedFuture(current);
        if (clampedTimeoutMs == 0L) return CompletableFuture.completedFuture(ServerState.UNKNOWN);

        CompletableFuture<ServerState> future = new CompletableFuture<>();
        long now = System.currentTimeMillis();
        long deadlineMs = now + clampedTimeoutMs;
        synchronized (phaseRequestLock) {
            ServerState recheck = committedState;
            if (recheck != ServerState.UNKNOWN) {
                future.complete(recheck);
                return future;
            }
            pendingStateRequests.addLast(new StateRequest(deadlineMs, future));
        }
        return future;
    }

    private void processPendingStateRequests(long nowMs) {
        List<CompletableFuture<ServerState>> completeAsKnown = new ArrayList<>();
        List<CompletableFuture<ServerState>> completeAsUnknown = new ArrayList<>();
        ServerState knownState = committedState;

        synchronized (phaseRequestLock) {
            if (pendingStateRequests.isEmpty()) return;
            if (knownState != ServerState.UNKNOWN) {
                while (!pendingStateRequests.isEmpty()) {
                    StateRequest req = pendingStateRequests.pollFirst();
                    if (req != null) completeAsKnown.add(req.future());
                }
            } else {
                int size = pendingStateRequests.size();
                for (int i = 0; i < size; i++) {
                    StateRequest req = pendingStateRequests.pollFirst();
                    if (req == null) continue;
                    if (nowMs >= req.deadlineMs()) completeAsUnknown.add(req.future());
                    else pendingStateRequests.addLast(req);
                }
            }
        }

        for (CompletableFuture<ServerState> future : completeAsKnown) future.complete(knownState);
        for (CompletableFuture<ServerState> future : completeAsUnknown) future.complete(ServerState.UNKNOWN);
    }

    private record StateRequest(long deadlineMs, CompletableFuture<ServerState> future) {}

    private static final class EvidenceScore {
        int lobby;
        int main;
        int crackedLogin;
        int cracked;
        int transfer;
        final List<String> reasons = new ArrayList<>();

        int confidenceFor(ServerState state) {
            return switch (state) {
                case MAIN_LOBBY -> lobby;
                case MAIN_SERVER -> main;
                case CRACKED_LOGIN -> crackedLogin;
                case CRACKED_LOBBY -> cracked;
                case TRANSFER_SERVER -> transfer;
                default -> Math.max(Math.max(lobby, main), Math.max(Math.max(crackedLogin, cracked), transfer));
            };
        }

        String summary() {
            return String.format(Locale.ROOT, "scores[lobby=%d,main=%d,crackedLogin=%d,cracked=%d,transfer=%d],reasons=%s", lobby, main, crackedLogin, cracked, transfer, String.join(",", reasons));
        }
    }
}
