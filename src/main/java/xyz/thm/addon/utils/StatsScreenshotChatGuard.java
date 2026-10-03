/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.utils;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.GuiMessageTag;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import xyz.thm.addon.THMAddon;
import xyz.thm.addon.mixin.accessor.ChatHudAccessor;

import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class StatsScreenshotChatGuard {
    private static final int MAX_BUFFERED_MESSAGES = 1_000;
    private static final long CAPTURE_WATCHDOG_MS = 5_000L;

    private static volatile StatsScreenshotChatGuard INSTANCE;

    private final LinkedHashMap<Long, Long> captureDeadlines = new LinkedHashMap<>();
    private final ArrayDeque<BufferedMessage> bufferedMessages = new ArrayDeque<>();

    private long nextCaptureToken = 1L;
    private int droppedMessages;

    private StatsScreenshotChatGuard() {
        MeteorClient.EVENT_BUS.subscribe(this);
    }

    public static StatsScreenshotChatGuard getInstance() {
        if (INSTANCE == null) {
            synchronized (StatsScreenshotChatGuard.class) {
                if (INSTANCE == null) INSTANCE = new StatsScreenshotChatGuard();
            }
        }
        return INSTANCE;
    }

    public long beginCapture() {
        long token = nextCaptureToken++;
        if (nextCaptureToken <= 0L) nextCaptureToken = 1L;
        captureDeadlines.put(token, System.currentTimeMillis() + CAPTURE_WATCHDOG_MS);
        return token;
    }

    public void finishCapture(long token) {
        if (captureDeadlines.remove(token) == null) return;
        if (captureDeadlines.isEmpty()) replayBufferedMessages();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    private void onReceiveMessage(ReceiveMessageEvent event) {
        if (captureDeadlines.isEmpty() || event.isCancelled()) return;
        if (isLocalAddonOutput(event.getMessage())) return;

        Component message = event.getMessage();
        if (message != null && bufferedMessages.size() < MAX_BUFFERED_MESSAGES) {
            bufferedMessages.addLast(new BufferedMessage(message.copy(), event.getIndicator()));
        } else {
            droppedMessages++;
        }
        event.cancel();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (captureDeadlines.isEmpty()) return;

        long now = System.currentTimeMillis();
        boolean expired = false;
        Iterator<Map.Entry<Long, Long>> iterator = captureDeadlines.entrySet().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().getValue() <= now) {
                iterator.remove();
                expired = true;
            }
        }

        if (expired) THMAddon.LOG.warn("Stats screenshot chat guard watchdog released an expired capture.");
        if (captureDeadlines.isEmpty()) replayBufferedMessages();
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        captureDeadlines.clear();
        bufferedMessages.clear();
        droppedMessages = 0;
    }

    private void replayBufferedMessages() {
        Minecraft mc = Minecraft.getInstance();
        ChatComponent chatHud = mc.gui == null ? null : mc.gui.getChat();

        if (chatHud != null) {
            int creationTick = mc.gui.getGuiTicks();
            while (!bufferedMessages.isEmpty()) {
                BufferedMessage buffered = bufferedMessages.removeFirst();
                ((ChatHudAccessor) chatHud).thm$addMessage(new GuiMessage(
                    creationTick,
                    buffered.message(),
                    null,
                    buffered.indicator()
                ));
            }
        } else {
            bufferedMessages.clear();
        }

        int dropped = droppedMessages;
        droppedMessages = 0;
        if (dropped > 0) {
            ChatUtils.warningPrefix("THM", "Stats screenshot chat guard dropped %d excess server messages.", dropped);
        }
    }

    /** True for chat lines this addon printed itself — those aren't server output, so they don't need buffering. */
    private static boolean isLocalAddonOutput(Component message) {
        if (message == null) return false;

        String trimmed = message.getString().stripLeading();
        if (trimmed.isEmpty()) return false;

        String lower = trimmed.toLowerCase(Locale.ROOT);
        return lower.startsWith("[meteor]")
            || lower.startsWith("[thm]")
            || lower.startsWith("[kitbot frontend]")
            || lower.startsWith("[thm highwaybuilder]")
            || lower.startsWith("[thm hwy monitor]")
            || lower.startsWith("[obsidian farmer thm]")
            || lower.startsWith("[elytra route]");
    }

    private record BufferedMessage(Component message, GuiMessageTag indicator) {
    }
}
