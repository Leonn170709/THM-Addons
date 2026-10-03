/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.utils;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityEvent;
import net.minecraft.world.entity.player.Player;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Session-scoped totem-of-undying pop counter, keyed by player UUID. Resets on death and disconnect. */
public final class TotemTracker {
    private static final Map<UUID, Integer> pops = new HashMap<>();
    private static boolean subscribed = false;

    private TotemTracker() {
    }

    private static final Object LISTENER = new Object() {
        @EventHandler
        private void onPacket(PacketEvent.Receive event) {
            if (!(event.packet instanceof ClientboundEntityEventPacket packet)) return;
            if (packet.getEventId() != EntityEvent.PROTECTED_FROM_DEATH) return;

            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) return;

            Entity entity = packet.getEntity(mc.level);
            if (!(entity instanceof Player player)) return;

            pops.merge(player.getUUID(), 1, Integer::sum);
        }

        // Death only shows up as 0 health client-side; a player dying out of render distance keeps their count.
        @EventHandler
        private void onTick(TickEvent.Post event) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null || pops.isEmpty()) return;

            for (Player player : mc.level.players()) {
                if (player.deathTime > 0 || player.getHealth() <= 0) pops.remove(player.getUUID());
            }
        }

        @EventHandler
        private void onGameLeft(GameLeftEvent event) {
            pops.clear();
        }
    };

    public static synchronized void initialize() {
        if (subscribed) return;
        subscribed = true;
        MeteorClient.EVENT_BUS.subscribe(LISTENER);
    }

    public static int get(UUID uuid) {
        return uuid == null ? 0 : pops.getOrDefault(uuid, 0);
    }

    public static int get(Player player) {
        return player == null ? 0 : get(player.getUUID());
    }
}
