/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.utils;

import meteordevelopment.meteorclient.settings.Setting;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Caches the two things the tab list rebuilds from scratch on every single frame: the sorted entry list
 * and each player's display name. Both are pure functions of state that changes a few times a second at
 * most, so a short TTL is enough — see {@code PlayerListHudMixin} for the hooks.
 */
public final class FastTab {
    // ponytail: fixed TTL instead of a setting - the tab visibly updating 5x/s is fine, and a slider here
    // would only ever be moved by someone who wants it slower, which is what the toggle is for.
    private static final long TTL_MS = 200;

    /** The toggles, created by {@code BetterTabMixin} so they live on Meteor's own module. */
    public static Setting<Boolean> enabled;
    public static Setting<Boolean> heads;

    private static final Map<UUID, Component> names = new HashMap<>();
    private static long namesAt;
    private static List<PlayerInfo> entries;
    private static long entriesAt;

    private FastTab() {}

    // deliberately not gated on Better Tab being active: the settings live on that module for lack of a
    // better home, but the cost they cut is vanilla's, and it is there whether or not Better Tab is on
    public static boolean on() {
        return enabled != null && enabled.get();
    }

    /** True while the tab list should skip drawing player heads. */
    public static boolean hideHeads() {
        return heads != null && !heads.get();
    }

    /** The cached entry list, or null when it is stale or the optimization is off. */
    public static List<PlayerInfo> entries() {
        return on() && System.currentTimeMillis() - entriesAt <= TTL_MS ? entries : null;
    }

    public static void storeEntries(List<PlayerInfo> list) {
        // our own cached list coming back out of the cancelled call - don't refresh the timestamp with it,
        // that would keep the cache alive forever
        if (list == entries) return;
        entries = list;
        entriesAt = System.currentTimeMillis();
    }

    /** The cached name, or null when it is a miss and the caller has to build one. */
    public static Component name(PlayerInfo entry) {
        if (!on()) return null;
        long now = System.currentTimeMillis();
        if (now - namesAt > TTL_MS) {
            names.clear();
            namesAt = now;
        }
        return names.get(entry.getProfile().id());
    }

    public static Component store(PlayerInfo entry, Component name) {
        if (name != null && on()) names.put(entry.getProfile().id(), name);
        return name;
    }
}
