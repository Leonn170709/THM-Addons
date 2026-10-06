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
import net.minecraft.network.protocol.common.ServerboundClientInformationPacket;
import net.minecraft.server.level.ClientInformation;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/** Briefly lowers the reported view distance, then restores the latest player options. */
public final class ChunkResync {
    private static final int DIP_TICKS = 10;
    // The restore goes out twice: if one got dropped (e.g. by PaketLimiter) you'd be stuck without chunks.
    private static final int RESEND_TICKS = DIP_TICKS + 20;
    private static final int MAX_HOLD_TICKS = 60 * 20;

    private static final ChunkResync INSTANCE = new ChunkResync();

    private int ticks = -1;
    private ClientInformation lastSent;
    private ClientInformation saved;
    private boolean sendingOwn;
    private boolean held;
    private boolean reapplyDistance;

    private ChunkResync() {}

    public static void init() {
        MeteorClient.EVENT_BUS.subscribe(INSTANCE);
    }

    public static void trigger() {
        if (INSTANCE.begin(false)) ChatUtils.info("Refreshing outer chunks (view distance %d -> 2 -> %d)...",
            INSTANCE.saved.viewDistance(), INSTANCE.saved.viewDistance());
    }

    public static boolean beginHeld() { return INSTANCE.begin(true); }
    public static boolean isHeld() { return INSTANCE.held; }
    public static boolean isActive() { return INSTANCE.ticks >= 0; }

    public static void finishHeld() {
        if (!INSTANCE.held) return;
        INSTANCE.held = false;
        INSTANCE.reapplyDistance = false;
        INSTANCE.ticks = DIP_TICKS;
        INSTANCE.restore();
    }

    private boolean begin(boolean hold) {
        if (mc.player == null || mc.getConnection() == null || ticks >= 0) return false;
        saved = lastSent != null ? lastSent : mc.options.buildPlayerInformation();
        held = hold;
        ticks = 0;
        send(withViewDistance(saved, 2));
        return true;
    }

    static ClientInformation withViewDistance(ClientInformation o, int distance) {
        return new ClientInformation(o.language(), distance, o.chatVisibility(), o.chatColors(), o.modelCustomisation(),
            o.mainHand(), o.textFilteringEnabled(), o.allowsListing(), o.particleStatus());
    }

    @EventHandler
    private void onSend(PacketEvent.Send event) {
        if (!sendingOwn && event.packet instanceof ServerboundClientInformationPacket p) {
            lastSent = p.information();
            if (ticks >= 0) {
                saved = lastSent;
                reapplyDistance = held;
            }
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (ticks < 0) return;
        ticks++;
        if (held) {
            if (reapplyDistance) {
                send(withViewDistance(saved, 2));
                reapplyDistance = false;
            }
            if (ticks >= MAX_HOLD_TICKS) {
                finishHeld();
                ChatUtils.warning("Chunk refresh timed out; restored view distance.");
            }
            return;
        }
        if (ticks == DIP_TICKS) {
            restore();
        } else if (ticks >= RESEND_TICKS) {
            restore();
            ticks = -1;
            ChatUtils.info("View distance restored; nearby chunks may remain cached.");
        }
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        ticks = -1; // the next join sends fresh options anyway
        lastSent = null;
        saved = null;
        held = reapplyDistance = false;
    }

    private void restore() {
        if (mc.player != null && mc.getConnection() != null && saved != null) send(saved);
    }

    private void send(ClientInformation options) {
        sendingOwn = true;
        try {
            mc.getConnection().send(new ServerboundClientInformationPacket(options));
        } finally {
            sendingOwn = false;
        }
    }
}
