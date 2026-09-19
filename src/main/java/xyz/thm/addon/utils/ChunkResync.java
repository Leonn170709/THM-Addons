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
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.c2s.common.ClientOptionsC2SPacket;
import net.minecraft.network.packet.c2s.common.SyncedClientOptions;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/** F3+A+S: sends view distance 0 so the server unloads chunks, then restores the previously sent options. */
public final class ChunkResync {
    private static final int DIP_TICKS = 10;
    // The restore goes out twice: if one got dropped (e.g. by PaketLimiter) you'd be stuck without chunks.
    private static final int RESEND_TICKS = DIP_TICKS + 20;

    private static final ChunkResync INSTANCE = new ChunkResync();

    private int ticks = -1;
    private SyncedClientOptions lastSent;
    private SyncedClientOptions saved;
    private boolean sendingOwn;

    private ChunkResync() {}

    public static void init() {
        MeteorClient.EVENT_BUS.subscribe(INSTANCE);
    }

    public static void trigger() {
        ChunkResync r = INSTANCE;
        if (mc.player == null || mc.getNetworkHandler() == null || r.ticks >= 0) return;

        r.saved = r.lastSent != null ? r.lastSent : mc.options.getSyncedOptions();
        SyncedClientOptions o = r.saved;
        r.send(new SyncedClientOptions(
            o.language(), 0, o.chatVisibility(), o.chatColorsEnabled(), o.playerModelParts(),
            o.mainArm(), o.filtersText(), o.allowsServerListing(), o.particleStatus()
        ));
        r.ticks = 0;
        ChatUtils.info("Resyncing chunks (view distance %d -> 0 -> %d)...", o.viewDistance(), o.viewDistance());
    }

    @EventHandler
    private void onSend(PacketEvent.Send event) {
        if (!sendingOwn && event.packet instanceof ClientOptionsC2SPacket p) lastSent = p.options();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (ticks < 0) return;
        ticks++;
        if (ticks == DIP_TICKS) {
            restore();
        } else if (ticks >= RESEND_TICKS) {
            restore();
            ticks = -1;
            ChatUtils.info("Chunks resynced.");
        }
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        ticks = -1; // the next join sends fresh options anyway
        lastSent = null;
    }

    private void restore() {
        if (mc.player != null && mc.getNetworkHandler() != null && saved != null) send(saved);
    }

    private void send(SyncedClientOptions options) {
        sendingOwn = true;
        try {
            mc.getNetworkHandler().sendPacket(new ClientOptionsC2SPacket(options));
        } finally {
            sendingOwn = false;
        }
    }
}
