package xyz.thm.addon.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.command.CommandSource;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import meteordevelopment.meteorclient.MeteorClient;

public class DesyncCommand extends Command {
    private boolean desynced = false;

    public DesyncCommand() {
        super("desync", "Desyncs your client position from the server.");
    }

    private void enable() {
        desynced = true;
        MeteorClient.EVENT_BUS.subscribe(this);
        info("Desync started. Server position is now frozen.");
    }

    private void disable() {
        desynced = false;
        MeteorClient.EVENT_BUS.unsubscribe(this);
        info("Desync stopped. Position resynced.");
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        // .desync — toggles
        builder.executes(context -> {
            if (desynced) disable();
            else enable();
            return SINGLE_SUCCESS;
        });

        // .desync start
        builder.then(literal("start").executes(context -> {
            if (desynced) { info("Already desynced."); return SINGLE_SUCCESS; }
            enable();
            return SINGLE_SUCCESS;
        }));

        // .desync stop
        builder.then(literal("stop").executes(context -> {
            if (!desynced) { info("Not desynced."); return SINGLE_SUCCESS; }
            disable();
            return SINGLE_SUCCESS;
        }));
    }

    @EventHandler(priority = EventPriority.HIGHEST + 1)
    private void onSendPacket(PacketEvent.Send event) {
        if (!desynced) return;
        if (event.packet instanceof PlayerMoveC2SPacket) {
            event.cancel();
        }
    }
}
