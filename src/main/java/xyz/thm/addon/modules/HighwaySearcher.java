package xyz.thm.addon.modules;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.World;
import xyz.thm.addon.THMAddon;


public class HighwaySearcher extends Module {
    public HighwaySearcher() {
        super(THMAddon.MAIN, "HighwaySearchers", "Automatically paths to the nearest Axis.");
    }
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    public final Setting<Boolean> axiswalker = sgGeneral.add(new BoolSetting.Builder()
        .name("Axis Walker")
        .description("Walks to the nearest Axis")
        .defaultValue(false)
        .build()
    );
    public final Setting<Boolean> Highwaytp = sgGeneral.add(new BoolSetting.Builder()
        .name("Highway Tp")
        .description("Tps you to the selected Highway")
        .defaultValue(false)
        .build()
    );
    public final Setting<Boolean> autoTp = sgGeneral.add(new BoolSetting.Builder()
        .name("Auto Tp")
        .description("Automatically sends a tprequest to KitBot1")
        .defaultValue(true)
        .visible(() -> Highwaytp.get())
        .build()
    );
    public final Setting<Highway> highway = sgGeneral.add(new EnumSetting.Builder<Highway>()
        .name("Highway")
        .description("Highway to go to.")
        .defaultValue(Highway.West)
        .visible(() -> Highwaytp.get())
            .build()
    );

    private final IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
    @Override
    public void onActivate() {
        assert mc.player != null;
        if (Highwaytp.get() && axiswalker.get()) {
            error("You cant have two modes enabled at the same time.");
            toggle();
            return;
        }
        if (axiswalker.get()) {
            if (mc.player == null) return;
            if (mc.world == null) return;
            if (mc.world.getRegistryKey() == World.NETHER) {
                baritone.getCommandManager().execute("axis");
                baritone.getCommandManager().execute("path");
            } else {
                error("You can only use this in the Nether");
            }
            toggle();
        }
        if (Highwaytp.get()) {
            tpaSent = false;
            // Normal
            if (highway.get() == Highway.West)      ChatUtils.sendPlayerMsg("$goto W");
            if (highway.get() == Highway.East)      ChatUtils.sendPlayerMsg("$goto E");
            if (highway.get() == Highway.North)     ChatUtils.sendPlayerMsg("$goto N");
            if (highway.get() == Highway.South)     ChatUtils.sendPlayerMsg("$goto S");
            if (highway.get() == Highway.NorthEast) ChatUtils.sendPlayerMsg("$goto NE");
            if (highway.get() == Highway.SouthEast) ChatUtils.sendPlayerMsg("$goto SE");
            if (highway.get() == Highway.SouthWest) ChatUtils.sendPlayerMsg("$goto SW");
            if (highway.get() == Highway.NorthWest) ChatUtils.sendPlayerMsg("$goto NW");
            // Dug
            if (highway.get() == Highway.DugWest)      ChatUtils.sendPlayerMsg("$goto dugW");
            if (highway.get() == Highway.DugEast)      ChatUtils.sendPlayerMsg("$goto dugE");
            if (highway.get() == Highway.DugNorth)     ChatUtils.sendPlayerMsg("$goto dugN");
            if (highway.get() == Highway.DugSouth)     ChatUtils.sendPlayerMsg("$goto dugS");
            if (highway.get() == Highway.DugNorthEast) ChatUtils.sendPlayerMsg("$goto dugNE");
            if (highway.get() == Highway.DugSouthEast) ChatUtils.sendPlayerMsg("$goto dugSE");
            if (highway.get() == Highway.DugSouthWest) ChatUtils.sendPlayerMsg("$goto dugSW");
            if (highway.get() == Highway.DugNorthWest) ChatUtils.sendPlayerMsg("$goto dugNW");

        }

    }
    private boolean tpaSent = false;
    @EventHandler
    private void onMessageReceive(ReceiveMessageEvent event) {
        if (mc.player == null) return;
        if (tpaSent) return;

        String msg = event.getMessage().getString();

        if (autoTp.get()
            && msg.contains("KitBot1 whispers: Bot has arrived at highway")
            && msg.contains("you may teleport")) {

            ChatUtils.sendPlayerMsg("/tpa KitBot1");
            info("TPA has been sent.");

            tpaSent = true; //// ONLY ONCE
            toggle();
        }
    }

    public enum Highway {
        West,
        East,
        North,
        South,
        NorthEast,
        SouthEast,
        SouthWest,
        NorthWest,
        DugWest,
        DugEast,
        DugNorth,
        DugSouth,
        DugNorthEast,
        DugSouthEast,
        DugSouthWest,
        DugNorthWest
    }
}
