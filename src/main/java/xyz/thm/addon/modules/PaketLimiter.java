/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.modules;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.PacketListSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.network.PacketUtils;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.network.packet.Packet;
import xyz.thm.addon.THMAddon;

import java.util.Set;

public class PaketLimiter extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    public final Setting<Integer> limit = sgGeneral.add(new IntSetting.Builder()
        .name("packet-limit")
        .description("Max packets per tick (0 = no limit).")
        .defaultValue(23)
        .min(0)
        .sliderRange(0, 1000)
        .build()
    );

    public final Setting<Boolean> allowBursts = sgGeneral.add(new BoolSetting.Builder()
        .name("allow-bursts")
        .description("Let one tick exceed the packet limit, at most once every 20 ticks.")
        .defaultValue(false)
        .build()
    );

    public final Setting<Integer> burstLimit = sgGeneral.add(new IntSetting.Builder()
        .name("burst-limit")
        .description("Max packets on a burst tick.")
        .defaultValue(60)
        .min(0)
        .sliderRange(0, 1000)
        .visible(allowBursts::get)
        .build()
    );

    public final Setting<Set<Class<? extends Packet<?>>>> bypass = sgGeneral.add(new PacketListSetting.Builder()
        .name("bypass")
        .description("C2S packets that bypass the limiter.")
        .filter(aClass -> PacketUtils.getC2SPackets().contains(aClass))
        .build()
    );
    public final Setting<Set<Class<? extends Packet<?>>>> alwaysBlock = sgGeneral.add(new PacketListSetting.Builder()
        .name("always-block")
        .description("C2S packets that are always cancelled, even if in bypass.")
        .defaultValue(java.util.Set.<Class<? extends Packet<?>>>of(net.minecraft.network.packet.c2s.play.HandSwingC2SPacket.class))
        .filter(aClass -> PacketUtils.getC2SPackets().contains(aClass))
        .build()
    );

    private int sentThisTick = 0;
    private int tick = 0;
    private int lastBurstTick = -20;

    public PaketLimiter() {
        super(THMAddon.MAIN, "paket-limiter", "Limits outgoing packets per tick with a bypass list.");
    }

    @Override
    public void onActivate() {
        if (bypass.get().isEmpty()) applyPresets();
    }

    public void applyPresets() {
        bypass.get().clear();
        bypass.get().add(net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.class);
        bypass.get().add(net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.PositionAndOnGround.class);
        bypass.get().add(net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.LookAndOnGround.class);
        bypass.get().add(net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.Full.class);
        bypass.get().add(net.minecraft.network.packet.c2s.play.VehicleMoveC2SPacket.class);
        bypass.get().add(net.minecraft.network.packet.c2s.play.TeleportConfirmC2SPacket.class);
        bypass.get().add(net.minecraft.network.packet.c2s.common.KeepAliveC2SPacket.class);
        bypass.get().add(net.minecraft.network.packet.c2s.common.CommonPongC2SPacket.class);
        bypass.get().add(net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket.class);
        if (!isActive()) toggle();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        sentThisTick = 0;
        tick++;
    }

    @EventHandler(priority = EventPriority.HIGHEST + 2)
    private void onSendPacket(PacketEvent.Send event) {
        int max = limit.get();
        if (max == 0) return;
        if (alwaysBlock.get().contains(event.packet.getClass())) {
            event.cancel();
            return;
        }
        if (bypass.get().contains(event.packet.getClass())) return;

        if (sentThisTick >= max) {
            // ponytail: fixed 20-tick burst cooldown, make it a setting if someone asks
            if (!allowBursts.get()) {
                event.cancel();
                return;
            }
            if (tick != lastBurstTick) { // not already bursting this tick — start a new one if off cooldown
                if (tick - lastBurstTick < 20) {
                    event.cancel();
                    return;
                }
                lastBurstTick = tick;
            }
            if (sentThisTick >= burstLimit.get()) {
                event.cancel();
                return;
            }
        }
        sentThisTick++;
    }
}
