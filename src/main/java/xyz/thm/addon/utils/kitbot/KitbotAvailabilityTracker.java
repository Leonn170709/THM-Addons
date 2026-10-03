/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.utils.kitbot;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.multiplayer.PlayerInfo;
import xyz.thm.addon.THMAddon;
import xyz.thm.addon.utils.server.ServerStatusHandler;
import xyz.thm.addon.utils.server.ServerStatusHandler.ServerState;

import java.util.Collection;
import java.util.concurrent.CopyOnWriteArraySet;

public final class KitbotAvailabilityTracker {
    public static final String KITBOT_NAME = "KitBot1";
    public static final long OFFLINE_UNAVAILABLE_MS = 5L * 60L * 1000L;
    public static final long RESTOCK_ONLINE_FRESH_MS = 60L * 1000L;

    private static final long SAMPLE_INTERVAL_MS = 1000L;
    private static volatile KitbotAvailabilityTracker INSTANCE;

    private final Object lock = new Object();
    private final CopyOnWriteArraySet<Listener> listeners = new CopyOnWriteArraySet<>();

    private Snapshot snapshot = new Snapshot(
        AvailabilityStatus.AwaitingRestart,
        KITBOT_NAME,
        false,
        false,
        0L,
        0L,
        0L,
        0L
    );
    private long lastSampleAttemptAtMs;
    private boolean offlineObservationPaused = true;

    private KitbotAvailabilityTracker() {
        MeteorClient.EVENT_BUS.subscribe(this);
    }

    public static KitbotAvailabilityTracker getInstance() {
        if (INSTANCE == null) {
            synchronized (KitbotAvailabilityTracker.class) {
                if (INSTANCE == null) INSTANCE = new KitbotAvailabilityTracker();
            }
        }
        return INSTANCE;
    }

    public AvailabilityStatus getStatus() {
        return getSnapshot().status();
    }

    public Snapshot getSnapshot() {
        synchronized (lock) {
            return snapshot;
        }
    }

    public boolean isAvailableForRestock() {
        long now = System.currentTimeMillis();
        Snapshot current = getSnapshot();
        return isMainServerContext()
            && current.status() == AvailabilityStatus.Available
            && current.online()
            && current.lastSeenOnlineAtMs() > 0L
            && now - current.lastSeenOnlineAtMs() <= RESTOCK_ONLINE_FRESH_MS;
    }

    public void addListener(Listener listener) {
        if (listener != null) listeners.add(listener);
    }

    public void removeListener(Listener listener) {
        if (listener != null) listeners.remove(listener);
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        long now = System.currentTimeMillis();
        if (now - lastSampleAttemptAtMs < SAMPLE_INTERVAL_MS) {
            refreshFreshness(now, isMainServerContext(), false);
            return;
        }

        lastSampleAttemptAtMs = now;
        boolean mainServerContext = isMainServerContext();
        Boolean online = mainServerContext ? sampleKitbotOnline() : null;
        if (online == null) {
            refreshFreshness(now, mainServerContext, true);
            return;
        }

        applyValidSample(online, now);
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        refreshFreshness(System.currentTimeMillis(), false, true);
    }

    private Boolean sampleKitbotOnline() {
        try {
            Collection<PlayerInfo> playerList = MeteorClient.mc.getConnection().getOnlinePlayers();
            if (playerList == null) return null;

            for (PlayerInfo entry : playerList) {
                if (entry == null || entry.getProfile() == null) continue;
                String name = entry.getProfile().name();
                if (KITBOT_NAME.equalsIgnoreCase(name)) return true;
            }

            return false;
        } catch (RuntimeException e) {
            THMAddon.LOG.warn("KitBot availability sample failed: {}", e.getMessage());
            return null;
        }
    }

    private boolean isMainServerContext() {
        return MeteorClient.mc != null
            && MeteorClient.mc.player != null
            && MeteorClient.mc.level != null
            && MeteorClient.mc.getConnection() != null
            && ServerStatusHandler.getInstance().getCommittedState() == ServerState.MAIN_SERVER;
    }

    private void applyValidSample(boolean online, long now) {
        Snapshot previous;
        Snapshot current;
        boolean fire;

        synchronized (lock) {
            previous = snapshot;
            current = online ? onlineSnapshot(now) : offlineSnapshot(now, previous);
            snapshot = current;
            offlineObservationPaused = online;
            fire = shouldFire(previous, current);
        }

        if (fire) fire(new AvailabilityEvent(previous, current));
    }

    private Snapshot onlineSnapshot(long now) {
        return new Snapshot(
            AvailabilityStatus.Available,
            KITBOT_NAME,
            true,
            true,
            now,
            now,
            0L,
            0L
        );
    }

    private Snapshot offlineSnapshot(long now, Snapshot previous) {
        long offlineSince = previous.offlineSinceMs() > 0L && !previous.online()
            ? previous.offlineSinceMs()
            : now;
        long offlineDuration = previous.offlineSinceMs() > 0L && !previous.online()
            ? previous.offlineDurationMs()
            : 0L;
        if (!offlineObservationPaused && previous.lastValidSampleAtMs() > 0L) {
            offlineDuration += Math.max(0L, now - previous.lastValidSampleAtMs());
        }
        AvailabilityStatus status = offlineDuration >= OFFLINE_UNAVAILABLE_MS
            ? AvailabilityStatus.Unavailable
            : AvailabilityStatus.AwaitingRestart;

        return new Snapshot(
            status,
            KITBOT_NAME,
            false,
            true,
            now,
            previous.lastSeenOnlineAtMs(),
            offlineSince,
            offlineDuration
        );
    }

    private void refreshFreshness(long now, boolean mainServerContext, boolean pauseOfflineObservation) {
        Snapshot previous;
        Snapshot current;
        boolean fire;

        synchronized (lock) {
            previous = snapshot;
            boolean sampleFresh = mainServerContext
                && previous.lastValidSampleAtMs() > 0L
                && now - previous.lastValidSampleAtMs() <= RESTOCK_ONLINE_FRESH_MS;
            current = new Snapshot(
                previous.status(),
                previous.kitbotName(),
                previous.online(),
                sampleFresh,
                previous.lastValidSampleAtMs(),
                previous.lastSeenOnlineAtMs(),
                previous.offlineSinceMs(),
                previous.offlineDurationMs()
            );
            snapshot = current;
            if (pauseOfflineObservation) offlineObservationPaused = true;
            fire = shouldFire(previous, current);
        }

        if (fire) fire(new AvailabilityEvent(previous, current));
    }

    private boolean shouldFire(Snapshot previous, Snapshot current) {
        return previous.status() != current.status()
            || previous.online() != current.online()
            || previous.sampleFresh() != current.sampleFresh();
    }

    private void fire(AvailabilityEvent event) {
        for (Listener listener : listeners) {
            try {
                listener.onKitbotAvailabilityChanged(event);
            } catch (Throwable t) {
                THMAddon.LOG.warn("KitBot availability listener failed: {}", t.getMessage(), t);
            }
        }
    }

    public enum AvailabilityStatus {
        Available,
        AwaitingRestart,
        Unavailable
    }

    @FunctionalInterface
    public interface Listener {
        void onKitbotAvailabilityChanged(AvailabilityEvent event);
    }

    public record Snapshot(
        AvailabilityStatus status,
        String kitbotName,
        boolean online,
        boolean sampleFresh,
        long lastValidSampleAtMs,
        long lastSeenOnlineAtMs,
        long offlineSinceMs,
        long offlineDurationMs
    ) {}

    public record AvailabilityEvent(
        Snapshot previous,
        Snapshot current
    ) {}
}
