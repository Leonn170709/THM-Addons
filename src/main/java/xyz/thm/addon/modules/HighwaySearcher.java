package xyz.thm.addon.modules;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;

import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import net.minecraft.world.World;
import xyz.thm.addon.THMAddon;


public class HighwaySearcher extends Module {
    public HighwaySearcher() {
        super(THMAddon.CATEGORY, "HighwaySearchers", "Automatically paths to the nearest Axis.");
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
    public final Setting<Highway> highway = sgGeneral.add(new EnumSetting.Builder<Highway>()
        .name("HIghway")
        .description("Highway to go to.")
        .defaultValue(Highway.West)
        .visible(() -> Highwaytp.get())
            .build()
    );

    private final IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
    @Override
    public void onActivate() {
        assert mc.player != null;
        if (Highwaytp.get() && axiswalker.get()) {error("You cant have two modes enabled at the same time.");
            toggle();}
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
            //Normal
            if (highway.get() == Highway.West) mc.player.networkHandler.sendChatCommand("msg KitBot1 $goto W");
            if (highway.get() == Highway.East) mc.player.networkHandler.sendChatCommand("msg KitBot1 $goto E");
            if (highway.get() == Highway.North) mc.player.networkHandler.sendChatCommand("msg KitBot1 $goto N");
            if (highway.get() == Highway.South) mc.player.networkHandler.sendChatCommand("msg KitBot1 $goto S");
            if (highway.get() == Highway.NorthEast) mc.player.networkHandler.sendChatCommand("msg KitBot1 $goto NE");
            if (highway.get() == Highway.SouthEast) mc.player.networkHandler.sendChatCommand("msg KitBot1 $goto SE");
            if (highway.get() == Highway.SouthWest) mc.player.networkHandler.sendChatCommand("msg KitBot1 $goto SW");
            if (highway.get() == Highway.NorthWest) mc.player.networkHandler.sendChatCommand("msg KitBot1 $goto NW");
            // Dug
            if (highway.get() == Highway.DugWest) mc.player.networkHandler.sendChatCommand("msg KitBot1 $goto dugW");
            if (highway.get() == Highway.DugEast) mc.player.networkHandler.sendChatCommand("msg KitBot1 $goto dugE");
            if (highway.get() == Highway.DugNorth) mc.player.networkHandler.sendChatCommand("msg KitBot1 $goto dugN");
            if (highway.get() == Highway.DugSouth) mc.player.networkHandler.sendChatCommand("msg KitBot1 $goto dugS");
            if (highway.get() == Highway.DugNorthEast) mc.player.networkHandler.sendChatCommand("msg KitBot1 $goto dugNE");
            if (highway.get() == Highway.DugSouthEast) mc.player.networkHandler.sendChatCommand("msg KitBot1 $goto dugSE");
            if (highway.get() == Highway.DugSouthWest) mc.player.networkHandler.sendChatCommand("msg KitBot1 $goto dugSW");
            if (highway.get() == Highway.DugNorthWest) mc.player.networkHandler.sendChatCommand("msg KitBot1 $goto dugNW");
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
