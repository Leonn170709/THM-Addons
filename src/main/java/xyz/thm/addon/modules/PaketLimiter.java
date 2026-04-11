package xyz.thm.addon.modules;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.network.PacketUtils;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.network.packet.Packet;
import xyz.thm.addon.THMAddon;
import java.util.Set;

public class PaketLimiter extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> limit = sgGeneral.add(new IntSetting.Builder()
        .name("packet-limit")
        .description("Max packets per tick (0 = no limit).")
        .defaultValue(23)
        .min(0)
        .sliderRange(0, 1000)
        .build()
    );

    private final Setting<Set<Class<? extends Packet<?>>>> bypass = sgGeneral.add(new PacketListSetting.Builder()
        .name("bypass")
        .description("C2S packets that bypass the limiter.")
        .filter(aClass -> PacketUtils.getC2SPackets().contains(aClass))
        .build()
    );

    private int sentThisTick = 0;

    public PaketLimiter() {
        super(THMAddon.MAIN, "paket-limiter", "Limits outgoing packets per tick with a bypass list.");
    }

    @Override
    public void onActivate() {
        if (bypass.get().isEmpty()) {
            // Movement
            bypass.get().add(net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.class);
            bypass.get().add(net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.PositionAndOnGround.class);
            bypass.get().add(net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.LookAndOnGround.class);
            bypass.get().add(net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.Full.class);
            bypass.get().add(net.minecraft.network.packet.c2s.play.VehicleMoveC2SPacket.class);
            // Connection keeping
            bypass.get().add(net.minecraft.network.packet.c2s.play.TeleportConfirmC2SPacket.class);
            bypass.get().add(net.minecraft.network.packet.c2s.common.KeepAliveC2SPacket.class);
            bypass.get().add(net.minecraft.network.packet.c2s.common.CommonPongC2SPacket.class);
            bypass.get().add(net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket.class);
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        sentThisTick = 0;
    }

    @EventHandler(priority = EventPriority.HIGHEST + 2)
    private void onSendPacket(PacketEvent.Send event) {
        int max = limit.get();
        if (max == 0) return;
        if (bypass.get().contains(event.packet.getClass())) return;

        if (sentThisTick >= max) {
            event.cancel();
            return;
        }
        sentThisTick++;
    }
}
