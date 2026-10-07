/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.utils.server;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/** Bounded observations of clocks already sent by the server. */
public final class ServerTelemetry {
    public enum ClockType { Collecting, Milliseconds, Nanoseconds, Epoch, Unsupported }

    private static final long MAX_UPTIME_MS = 30L * 366 * 24 * 60 * 60 * 1000;
    private long lastId, lastKeepaliveNs, lastTimeNs, worldTicks = -1;
    private int keepaliveSamples, matchingIntervals, timeSamples, nextTimeSample;
    private ClockType candidate = ClockType.Collecting;
    private final double[] tickDeltas = new double[8], intervalMillis = new double[8];

    public void reset() {
        lastId = lastKeepaliveNs = 0;
        keepaliveSamples = matchingIntervals = 0;
        candidate = ClockType.Collecting;
        resetWorldTime();
    }

    public void resetWorldTime() {
        lastTimeNs = 0;
        worldTicks = -1;
        timeSamples = nextTimeSample = 0;
        Arrays.fill(tickDeltas, 0);
        Arrays.fill(intervalMillis, 0);
    }

    public void observeKeepalive(long id, long receivedNs) {
        if (keepaliveSamples > 0) {
            double elapsedMs = (receivedNs - lastKeepaliveNs) / 1_000_000.0;
            double delta = (double) id - lastId;
            ClockType next = ClockType.Unsupported;
            if (id > 0 && lastId > 0 && elapsedMs >= 100 && delta > 0) {
                if (matches(delta, elapsedMs)) next = id < MAX_UPTIME_MS ? ClockType.Milliseconds : ClockType.Epoch;
                else if (matches(delta / 1_000_000.0, elapsedMs) && id / 1_000_000 < MAX_UPTIME_MS) next = ClockType.Nanoseconds;
            }
            matchingIntervals = next == candidate ? Math.min(2, matchingIntervals + 1) : 1;
            candidate = next;
        }
        lastId = id;
        lastKeepaliveNs = receivedNs;
        keepaliveSamples = Math.min(3, keepaliveSamples + 1);
    }

    private static boolean matches(double deltaMs, double elapsedMs) {
        return Math.abs(deltaMs - elapsedMs) <= Math.max(25, elapsedMs * .2);
    }

    public ClockType clockType() {
        return matchingIntervals >= 2 ? candidate : ClockType.Collecting;
    }

    public int keepaliveSamples() { return keepaliveSamples; }
    public long keepaliveAgeMillis(long nowNs) {
        return keepaliveSamples == 0 ? -1 : Math.max(0, (nowNs - lastKeepaliveNs) / 1_000_000);
    }

    public long uptimeMillis(long nowNs) {
        long age = keepaliveAgeMillis(nowNs);
        if (age < 0 || age > 60_000) return -1;
        // A monotonic clock's origin is JVM/platform-dependent, so this remains an estimate.
        return switch (clockType()) {
            case Milliseconds -> lastId + age;
            case Nanoseconds -> lastId / 1_000_000 + age;
            default -> -1;
        };
    }

    public void observeWorldTime(long ticks, long receivedNs) {
        if (ticks < 0) { resetWorldTime(); return; }
        if (worldTicks >= 0 && (ticks < worldTicks || receivedNs <= lastTimeNs)) resetWorldTime();
        if (worldTicks >= 0) {
            tickDeltas[nextTimeSample] = (double) ticks - worldTicks;
            intervalMillis[nextTimeSample] = (receivedNs - lastTimeNs) / 1_000_000.0;
            nextTimeSample = (nextTimeSample + 1) % tickDeltas.length;
            timeSamples = Math.min(tickDeltas.length, timeSamples + 1);
        }
        worldTicks = ticks;
        lastTimeNs = receivedNs;
    }

    public long worldTicks() { return worldTicks; }
    public long timeUpdateAgeMillis(long nowNs) {
        return worldTicks < 0 ? -1 : Math.max(0, (nowNs - lastTimeNs) / 1_000_000);
    }

    public double observedTps(long nowNs) {
        if (timeSamples == 0 || timeUpdateAgeMillis(nowNs) > 30_000) return Double.NaN;
        double ticks = 0, millis = 0;
        for (int i = 0; i < timeSamples; i++) {
            ticks += tickDeltas[i];
            millis += intervalMillis[i];
        }
        return millis > 0 ? ticks * 1000 / millis : Double.NaN;
    }

    public static String duration(long millis) {
        long seconds = Math.max(0, millis) / 1000;
        return String.format(Locale.ROOT, "%dd %02dh %02dm %02ds", seconds / 86400, seconds / 3600 % 24, seconds / 60 % 60, seconds % 60);
    }

    public static List<String> pluginHints(Collection<String> commands) {
        Set<String> builtins = Set.of("minecraft", "bukkit", "paper", "spigot");
        var hints = new TreeSet<String>();
        for (String command : commands) {
            int colon = command.indexOf(':');
            if (colon <= 0 || colon == command.length() - 1 || colon != command.lastIndexOf(':')) continue;
            String namespace = command.substring(0, colon).toLowerCase(Locale.ROOT);
            if (namespace.matches("[a-z0-9_.-]+") && !builtins.contains(namespace)) hints.add(namespace);
        }
        return List.copyOf(hints);
    }
}
