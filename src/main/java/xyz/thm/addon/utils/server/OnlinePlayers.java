/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.utils.server;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundCommandSuggestionsPacket;
import net.minecraft.network.protocol.game.ServerboundCommandSuggestionPacket;

import java.util.Collection;
import java.util.List;
import java.util.TreeSet;
import java.util.regex.Pattern;

/** Shared online names, including players exposed only by server command completion. */
public final class OnlinePlayers {
    private static final String COMMAND = "/msg ";
    private static final long REFRESH_MS = 10_000, TIMEOUT_MS = 5_000, TTL_MS = 30_000;
    private static final int MAX_NAMES = 8192;
    private static final Pattern PLAYER_NAME = Pattern.compile("[A-Za-z0-9_]{1,16}");
    private static final OnlinePlayers INSTANCE = new OnlinePlayers();
    private static boolean initialized;

    private ClientPacketListener connection;
    private List<String> suggestions = List.of();
    private long expiresAt, nextRequestAt, pendingSentAt, usedUntil;
    // Vanilla uses nonnegative request IDs and -1 for its idle state.
    private int nextId = -2, pendingId;

    OnlinePlayers() {}

    public static void initialize() {
        if (initialized) return;
        initialized = true;
        MeteorClient.EVENT_BUS.subscribe(INSTANCE);
    }

    /** Known profiles plus fresh /msg suggestions; no UUIDs or player entities are invented. */
    public static List<String> getNames() {
        var current = MeteorClient.mc.getConnection();
        INSTANCE.useConnection(current);
        if (current == null) return List.of();
        long now = System.currentTimeMillis();
        INSTANCE.usedUntil = now + TTL_MS;
        return INSTANCE.merge(current.getOnlinePlayers().stream().map(p -> p.getProfile().name()).toList(), now);
    }

    /** Null means the player is absent from known profiles and completion is unavailable or stale. */
    public static Boolean isOnline(String name) {
        if (getNames().stream().anyMatch(n -> n.equalsIgnoreCase(name))) return true;
        return INSTANCE.fresh(System.currentTimeMillis()) ? false : null;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        var mc = MeteorClient.mc;
        useConnection(mc.getConnection());
        if (connection == null || mc.player == null || mc.level == null || mc.hasSingleplayerServer()) return;
        long now = System.currentTimeMillis();
        if (now >= usedUntil) return;
        int id = request(now);
        if (id != 0) connection.send(new ServerboundCommandSuggestionPacket(id, COMMAND));
    }

    @EventHandler
    private void onPacket(PacketEvent.Receive event) {
        if (!(event.packet instanceof ClientboundCommandSuggestionsPacket packet) || packet.id() >= -1) return;
        var mc = MeteorClient.mc;
        var source = mc.getConnection();
        mc.execute(() -> {
            if (source != connection || source != mc.getConnection()) return;
            accept(packet.id(), packet.start(), packet.length(),
                packet.suggestions().stream().map(ClientboundCommandSuggestionsPacket.Entry::text).toList(),
                System.currentTimeMillis());
        });
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        useConnection(null);
    }

    private void useConnection(ClientPacketListener current) {
        if (connection == current) return;
        reset();
        connection = current;
    }

    int request(long now) {
        if (pendingId != 0 && now - pendingSentAt >= TIMEOUT_MS) pendingId = 0;
        if (pendingId != 0 || now < nextRequestAt) return 0;
        pendingId = nextId--;
        if (nextId >= -1) nextId = -2;
        pendingSentAt = now;
        nextRequestAt = now + REFRESH_MS;
        return pendingId;
    }

    void accept(int id, int start, int length, List<String> names, long now) {
        if (pendingId == 0 || id != pendingId) return;
        pendingId = 0;
        if (now - pendingSentAt >= TIMEOUT_MS || start != COMMAND.length() || length != 0
            || names.size() > MAX_NAMES) return;
        TreeSet<String> valid = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (String name : names) {
            if (name != null && PLAYER_NAME.matcher(name).matches()) valid.add(name);
        }
        suggestions = List.copyOf(valid);
        // An empty response can mean unsupported or denied completion, not an empty server.
        expiresAt = valid.isEmpty() ? 0 : now + TTL_MS;
    }

    boolean fresh(long now) {
        return now < expiresAt;
    }

    List<String> merge(Collection<String> known, long now) {
        TreeSet<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        names.addAll(known);
        if (fresh(now)) names.addAll(suggestions);
        return List.copyOf(names);
    }

    void reset() {
        suggestions = List.of();
        expiresAt = nextRequestAt = pendingSentAt = usedUntil = 0;
        pendingId = 0;
    }
}
