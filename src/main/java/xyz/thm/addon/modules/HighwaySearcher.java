package xyz.thm.addon.modules;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Blocks;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;
import xyz.thm.addon.THMAddon;

import static xyz.thm.addon.utils.THMUtils.Notify;


public class HighwaySearcher extends Module {
    public HighwaySearcher() {
        super(THMAddon.MAIN, "HighwaySearcher", "Automatically paths to the nearest Axis or teleports to a highway. Alerts you when the highway ends or begins.");
    }

    // -------------------------------------------------------------------------
    // Setting Groups
    // -------------------------------------------------------------------------
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgChecker = settings.createGroup("Highway Checker");

    // -------------------------------------------------------------------------
    // Highway Settings
    // -------------------------------------------------------------------------
    public final Setting<Boolean> axiswalker = sgGeneral.add(new BoolSetting.Builder()
        .name("Axis Walker")
        .description("Uses Baritone to walk to the nearest Nether Axis. Must be in the Nether.")
        .defaultValue(false)
        .build()
    );

    public final Setting<Boolean> highwayTp = sgGeneral.add(new BoolSetting.Builder()
        .name("Highway Tp")
        .description("Sends a $goto command to KitBot1 to teleport you to the selected highway.")
        .defaultValue(false)
        .build()
    );

    public final Setting<Boolean> autoTp = sgGeneral.add(new BoolSetting.Builder()
        .name("Auto Tp")
        .description("Automatically sends /tpa KitBot1 once the bot has arrived at the highway.")
        .defaultValue(true)
        .visible(() -> highwayTp.get())
        .build()
    );

    public final Setting<Highway> highway = sgGeneral.add(new EnumSetting.Builder<Highway>()
        .name("Highway")
        .description("The highway to teleport to.")
        .defaultValue(Highway.West)
        .visible(() -> highwayTp.get())
        .build()
    );

    // -------------------------------------------------------------------------
    // Highway Checker Settings
    // -------------------------------------------------------------------------
    public final Setting<Boolean> obsidianGuardEnabled = sgChecker.add(new BoolSetting.Builder()
        .name("Enable Checker")
        .description("Monitors obsidian in your current chunk to detect the start or end of a highway.")
        .defaultValue(false)
        .build()
    );

    public final Setting<CheckerMode> guardMode = sgChecker.add(new EnumSetting.Builder<CheckerMode>()
        .name("Mode")
        .description("HighwayEnd: Triggers when obsidian drops below the threshold (highway ends). HighwayStart: Triggers when obsidian exceeds the threshold (highway begins).")
        .defaultValue(CheckerMode.HighwayEnd)
        .visible(() -> obsidianGuardEnabled.get())
        .build()
    );

    public final Setting<Integer> obsidianThreshold = sgChecker.add(new IntSetting.Builder()
        .name("Obsidian Threshold")
        .description("The number of obsidian blocks in the chunk that marks the highway boundary.")
        .defaultValue(12)
        .min(1)
        .sliderMax(64)
        .visible(() -> obsidianGuardEnabled.get())
        .build()
    );

    public final Setting<Boolean> sendWarning = sgChecker.add(new BoolSetting.Builder()
        .name("Chat Warning")
        .description("Prints a warning in the Meteor client chat when the highway boundary is detected.")
        .defaultValue(true)
        .visible(() -> obsidianGuardEnabled.get())
        .build()
    );

    public final Setting<Boolean> disconnect = sgChecker.add(new BoolSetting.Builder()
        .name("Disconnect")
        .description("Disconnects from the server when the highway boundary is detected.")
        .defaultValue(true)
        .visible(() -> obsidianGuardEnabled.get())
        .build()
    );

    public final Setting<Boolean> desktopWarning = sgChecker.add(new BoolSetting.Builder()
        .name("Desktop Notification")
        .description("Sends a desktop notification when the highway boundary is detected.")
        .defaultValue(true)
        .visible(() -> obsidianGuardEnabled.get())
        .build()
    );

    // -------------------------------------------------------------------------
    // Internal state
    // -------------------------------------------------------------------------
    private final IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
    private boolean tpaSent   = false;
    private int     tickTimer = 0;
    private static final int CHECK_INTERVAL = 20; // check once per second

    // -------------------------------------------------------------------------
    // Activation
    // -------------------------------------------------------------------------
    @Override
    public void onActivate() {
        if (mc.player == null) return;

        if (highwayTp.get() && axiswalker.get()) {
            error("You cannot have both Highway Tp and Axis Walker enabled at the same time.");
            toggle();
            return;
        }

        if (axiswalker.get()) {
            if (mc.world == null) return;
            if (mc.world.getRegistryKey() == World.NETHER) {
                baritone.getCommandManager().execute("axis");
                baritone.getCommandManager().execute("path");
            } else {
                error("Axis Walker can only be used in the Nether.");
            }
            toggle();
            return;
        }

        if (highwayTp.get()) {
            tpaSent = false;
            // Normal highways
            if (highway.get() == Highway.West)      ChatUtils.sendPlayerMsg("$goto W");
            if (highway.get() == Highway.East)      ChatUtils.sendPlayerMsg("$goto E");
            if (highway.get() == Highway.North)     ChatUtils.sendPlayerMsg("$goto N");
            if (highway.get() == Highway.South)     ChatUtils.sendPlayerMsg("$goto S");
            if (highway.get() == Highway.NorthEast) ChatUtils.sendPlayerMsg("$goto NE");
            if (highway.get() == Highway.SouthEast) ChatUtils.sendPlayerMsg("$goto SE");
            if (highway.get() == Highway.SouthWest) ChatUtils.sendPlayerMsg("$goto SW");
            if (highway.get() == Highway.NorthWest) ChatUtils.sendPlayerMsg("$goto NW");
            // Dug highways
            if (highway.get() == Highway.DugWest)      ChatUtils.sendPlayerMsg("$goto dugW");
            if (highway.get() == Highway.DugEast)      ChatUtils.sendPlayerMsg("$goto dugE");
            if (highway.get() == Highway.DugNorth)     ChatUtils.sendPlayerMsg("$goto dugN");
            if (highway.get() == Highway.DugSouth)     ChatUtils.sendPlayerMsg("$goto dugS");
            if (highway.get() == Highway.DugNorthEast) ChatUtils.sendPlayerMsg("$goto dugNE");
            if (highway.get() == Highway.DugSouthEast) ChatUtils.sendPlayerMsg("$goto dugSE");
            if (highway.get() == Highway.DugSouthWest) ChatUtils.sendPlayerMsg("$goto dugSW");
            if (highway.get() == Highway.DugNorthWest) ChatUtils.sendPlayerMsg("$goto dugNW");
        }

        tickTimer = 0;
    }

    // -------------------------------------------------------------------------
    // Tick – Highway Checker
    // -------------------------------------------------------------------------
    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (!obsidianGuardEnabled.get()) return;
        if (mc.player == null || mc.world == null) return;

        tickTimer++;
        if (tickTimer < CHECK_INTERVAL) return;
        tickTimer = 0;

        int count = countObsidianInChunk(mc.player);
        boolean triggered = false;
        String reason = "";

        if (guardMode.get() == CheckerMode.HighwayEnd && count < obsidianThreshold.get()) {
            triggered = true;
            reason = String.format("Highway end detected — only %d obsidian in chunk (threshold: %d).", count, obsidianThreshold.get());
        } else if (guardMode.get() == CheckerMode.HighwayStart && count > obsidianThreshold.get()) {
            triggered = true;
            reason = String.format("Highway start detected — %d obsidian in chunk (threshold: %d).", count, obsidianThreshold.get());
        }

        if (triggered) {
            if (sendWarning.get())    warning(reason);
            if (desktopWarning.get()) Notify(name, reason);
            if (disconnect.get())     disconnectPlayer();
        }
    }

    // -------------------------------------------------------------------------
    // Message listener – KitBot1 TPA
    // -------------------------------------------------------------------------
    @EventHandler
    private void onMessageReceive(ReceiveMessageEvent event) {
        if (mc.player == null) return;
        if (tpaSent) return;

        String msg = event.getMessage().getString();

        if (autoTp.get()
            && msg.contains("KitBot1 whispers: Bot has arrived at highway")
            && msg.contains("you may teleport")) {

            ChatUtils.sendPlayerMsg("/tpa KitBot1");
            info("TPA request sent.");
            tpaSent = true;
            toggle();
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------
    private int countObsidianInChunk(ClientPlayerEntity player) {
        ChunkPos chunkPos = new ChunkPos(player.getBlockPos());
        WorldChunk chunk  = mc.world.getChunk(chunkPos.x, chunkPos.z);

        int minX = chunkPos.getStartX();
        int minZ = chunkPos.getStartZ();
        int maxX = chunkPos.getEndX();
        int maxZ = chunkPos.getEndZ();
        int minY = mc.world.getBottomY();
        int maxY = mc.world.getBottomY() + mc.world.getHeight();
        int count = 0;

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = minY; y < maxY; y++) {
                    if (chunk.getBlockState(new BlockPos(x, y, z)).getBlock() == Blocks.OBSIDIAN) {
                        count++;
                        // Early exit in HighwayStart mode once threshold is exceeded
                        if (guardMode.get() == CheckerMode.HighwayStart && count > obsidianThreshold.get()) {
                            return count;
                        }
                    }
                }
            }
        }
        return count;
    }

    private void disconnectPlayer() {
        if (mc.getNetworkHandler() != null) {
            toggle();

            MutableText text = Text.literal("[")
                .styled(style -> style.withColor(Formatting.WHITE))
                .append(Text.literal("HighwayChecker").styled(style -> style.withColor(Formatting.BLUE)))
                .append(Text.literal("] ").styled(style -> style.withColor(Formatting.WHITE)))
                .append(Text.literal("Highway boundary reached.").styled(style -> style.withColor(Formatting.RED)));

            mc.getNetworkHandler().getConnection().disconnect(text);
        }
    }

    // -------------------------------------------------------------------------
    // Enums
    // -------------------------------------------------------------------------
    public enum CheckerMode {
        HighwayEnd,
        HighwayStart
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
