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
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.network.PacketUtils;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ServerboundKeepAlivePacket;
import net.minecraft.network.protocol.common.ServerboundPongPacket;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.network.protocol.game.ServerboundClientTickEndPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundMoveVehiclePacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import xyz.thm.addon.THMAddon;

import java.util.Set;

public class PaketLimiter extends Module {
    /** Movement and keep-alive traffic: dropping any of it desyncs you, so it never counts against the limit. */
    private static final Set<Class<? extends Packet<?>>> PRESET_BYPASS = Set.of(
        ServerboundMovePlayerPacket.class,
        ServerboundMovePlayerPacket.Pos.class,
        ServerboundMovePlayerPacket.Rot.class,
        ServerboundMovePlayerPacket.PosRot.class,
        ServerboundMoveVehiclePacket.class,
        ServerboundAcceptTeleportationPacket.class,
        ServerboundKeepAlivePacket.class,
        ServerboundPongPacket.class,
        ServerboundPlayerCommandPacket.class
    );

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
        .defaultValue(new ObjectOpenHashSet<>(PRESET_BYPASS))
        .build()
    );
    public final Setting<Set<Class<? extends Packet<?>>>> alwaysBlock = sgGeneral.add(new PacketListSetting.Builder()
        .name("always-block")
        .description("C2S packets that are always cancelled, even if in bypass.")
        .defaultValue(new ObjectOpenHashSet<Class<? extends Packet<?>>>(Set.of(
            ServerboundSwingPacket.class,
            ServerboundClientTickEndPacket.class
        )))
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
        bypass.get().addAll(PRESET_BYPASS);
        if (!isActive()) toggle();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        sentThisTick = 0;
        tick++;
    }

    @EventHandler(priority = EventPriority.HIGHEST + 2)
    private void onSendPacket(PacketEvent.Send event) {
        HighwayBuilderTHM builder = Modules.get().get(HighwayBuilderTHM.class);
        if (builder != null && builder.isChokeHoldingOrFlushing(event.connection)) return;
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
