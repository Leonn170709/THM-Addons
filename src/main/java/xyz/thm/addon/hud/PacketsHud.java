/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.hud;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.PacketListSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.hud.Hud;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.network.PacketUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.Packet;
import xyz.thm.addon.THMAddon;

import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public class PacketsHud extends HudElement {
    public static final HudElementInfo<PacketsHud> INFO = new HudElementInfo<>(
        THMAddon.HUD_GROUP,
        "packets-hud",
        "Shows packets per second or per tick.",
        PacketsHud::new
    );

    public enum Mode { PerSecond, PerTick }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("mode")
        .description("Per second or per tick.")
        .defaultValue(Mode.PerSecond)
        .build()
    );

    private final Setting<Set<Class<? extends Packet<?>>>> sentPackets = sgGeneral.add(new PacketListSetting.Builder()
        .name("sent-packets")
        .description("Sent packets to count.")
        .filter(aClass -> PacketUtils.getC2SPackets().contains(aClass))
        .defaultValue(new ObjectOpenHashSet<>(PacketUtils.getC2SPackets()))
        .build()
    );

    private final Setting<Set<Class<? extends Packet<?>>>> receivedPackets = sgGeneral.add(new PacketListSetting.Builder()
        .name("received-packets")
        .description("Received packets to count.")
        .filter(aClass -> PacketUtils.getS2CPackets().contains(aClass))
        .defaultValue(new ObjectOpenHashSet<>(PacketUtils.getS2CPackets()))
        .build()
    );

    // Packet events fire on the netty thread.
    private final AtomicInteger sentNow = new AtomicInteger(), receivedNow = new AtomicInteger();
    private final int[] sentWindow = new int[20], receivedWindow = new int[20];
    private int windowIndex, sentSum, receivedSum, sentLastTick, receivedLastTick;

    public PacketsHud() {
        super(INFO);
        MeteorClient.EVENT_BUS.subscribe(this);
    }

    @EventHandler
    private void onReceive(PacketEvent.Receive event) {
        if (receivedPackets.get().contains(event.packet.getClass())) receivedNow.incrementAndGet();
    }

    @EventHandler
    private void onSent(PacketEvent.Sent event) {
        if (sentPackets.get().contains(event.packet.getClass())) sentNow.incrementAndGet();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        // Hud.clear() drops elements without notifying them.
        boolean present = false;
        for (HudElement e : Hud.get()) if (e == this) { present = true; break; }
        if (!present) {
            MeteorClient.EVENT_BUS.unsubscribe(this);
            return;
        }

        int sent = sentNow.getAndSet(0), received = receivedNow.getAndSet(0);
        sentSum += sent - sentWindow[windowIndex];
        receivedSum += received - receivedWindow[windowIndex];
        sentWindow[windowIndex] = sent;
        receivedWindow[windowIndex] = received;
        windowIndex = (windowIndex + 1) % sentWindow.length;
        sentLastTick = sent;
        receivedLastTick = received;
    }

    @Override
    public void render(HudRenderer renderer) {
        boolean perSecond = mode.get() == Mode.PerSecond;
        String unit = perSecond ? "/s: " : "/t: ";
        int sent = perSecond ? sentSum : sentLastTick;
        int received = perSecond ? receivedSum : receivedLastTick;
        String[][] lines = {
            {"Sent" + unit, String.valueOf(sent)},
            {"Received" + unit, String.valueOf(received)},
            {"Total" + unit, String.valueOf(sent + received)}
        };

        double lineHeight = renderer.textHeight(true), width = 0;
        for (String[] line : lines) width = Math.max(width, renderer.textWidth(line[0], true) + renderer.textWidth(line[1], true));
        setSize(width, lineHeight * lines.length);

        double currentY = y;
        for (String[] line : lines) {
            renderer.text(line[0], x, currentY, Color.WHITE, true);
            renderer.text(line[1], x + renderer.textWidth(line[0], true), currentY, Color.RED, true);
            currentY += lineHeight;
        }
    }
}
