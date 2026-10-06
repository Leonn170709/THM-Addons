/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.modules;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.network.PacketUtils;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import xyz.thm.addon.THMAddon;

import java.util.Set;

public class PaketLimiter extends Module {
    /** Movement and keep-alive traffic: dropping any of it desyncs you, so it never counts against the limit. */
    private static final Set<PacketType<? extends Packet<?>>> PRESET_BYPASS = Set.of(
        net.minecraft.network.protocol.game.GamePacketTypes.SERVERBOUND_MOVE_PLAYER_POS,
        net.minecraft.network.protocol.game.GamePacketTypes.SERVERBOUND_MOVE_PLAYER_ROT,
        net.minecraft.network.protocol.game.GamePacketTypes.SERVERBOUND_MOVE_PLAYER_POS_ROT,
        net.minecraft.network.protocol.game.GamePacketTypes.SERVERBOUND_MOVE_PLAYER_STATUS_ONLY,
        net.minecraft.network.protocol.game.GamePacketTypes.SERVERBOUND_MOVE_VEHICLE,
        net.minecraft.network.protocol.game.GamePacketTypes.SERVERBOUND_ACCEPT_TELEPORTATION,
        net.minecraft.network.protocol.common.CommonPacketTypes.SERVERBOUND_KEEP_ALIVE,
        net.minecraft.network.protocol.common.CommonPacketTypes.SERVERBOUND_PONG,
        net.minecraft.network.protocol.game.GamePacketTypes.SERVERBOUND_PLAYER_COMMAND
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

    public final Setting<Set<PacketType<? extends Packet<?>>>> bypass = sgGeneral.add(new PacketListSetting.Builder()
        .name("bypass")
        .description("C2S packets that bypass the limiter.")
        .filter(aClass -> PacketUtils.getServerboundPackets().contains(aClass))
        .defaultValue(new ObjectOpenHashSet<>(PRESET_BYPASS))
        .build()
    );
    public final Setting<Set<PacketType<? extends Packet<?>>>> alwaysBlock = sgGeneral.add(new PacketListSetting.Builder()
        .name("always-block")
        .description("C2S packets that are always cancelled, even if in bypass.")
        .defaultValue(new ObjectOpenHashSet<PacketType<? extends Packet<?>>>(Set.of(
            net.minecraft.network.protocol.game.GamePacketTypes.SERVERBOUND_SWING,
            net.minecraft.network.protocol.game.GamePacketTypes.SERVERBOUND_CLIENT_TICK_END
        )))
        .filter(aClass -> PacketUtils.getServerboundPackets().contains(aClass))
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
        if (alwaysBlock.get().contains(event.packet.type())) {
            event.cancel();
            return;
        }
        if (bypass.get().contains(event.packet.type())) return;

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
