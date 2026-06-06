package xyz.thm.addon.modules;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.network.PlayerListEntry;
import xyz.thm.addon.THMAddon;
import xyz.thm.addon.utils.ThmMembers;

import java.util.*;

public class FriendsSyncModule extends Module {
    private static final String PLACEHOLDER = "%player%";

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<List<String>> commands = sgGeneral.add(new StringListSetting.Builder()
        .name("commands")
        .description("Commands to run when a tracked player joins. Use %player% as a placeholder for the player's name.")
        .defaultValue(Collections.singletonList(",friend add %player%"))
        .build()
    );

    private final Setting<Double> commandDelay = sgGeneral.add(new DoubleSetting.Builder()
        .name("command-delay-seconds")
        .description("Delay in seconds between each command.")
        .defaultValue(2.0)
        .min(0.1)
        .sliderMin(0.5)
        .sliderMax(30.0)
        .build()
    );

    private final Setting<Boolean> syncThmMembers = sgGeneral.add(new BoolSetting.Builder()
        .name("sync-thm-members")
        .description("Also run commands for online THM members (not just Meteor friends).")
        .defaultValue(false)
        .build()
    );

    private final Set<String> knownPlayers = new HashSet<>();
    private final Deque<PendingCommand> commandQueue = new ArrayDeque<>();
    private int delayTicks = 0;

    public FriendsSyncModule() {
        super(THMAddon.MAIN, "friends-sync", "Runs commands when friends or THM members join. %player% is replaced with their name.");
    }

    @Override
    public void onActivate() {
        knownPlayers.clear();
        commandQueue.clear();
        delayTicks = 0;
        if (mc.getNetworkHandler() != null) {
            for (PlayerListEntry entry : mc.getNetworkHandler().getPlayerList()) {
                String name = entry.getProfile().name();
                knownPlayers.add(name);
                handlePlayerJoin(name);
            }
        }
        info("Friends Sync active. Scanning online players and waiting for joins.");
    }

    @Override
    public void onDeactivate() {
        knownPlayers.clear();
        commandQueue.clear();
        delayTicks = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.getNetworkHandler() == null) return;

        Set<String> currentPlayers = new HashSet<>();
        for (PlayerListEntry entry : mc.getNetworkHandler().getPlayerList()) {
            currentPlayers.add(entry.getProfile().name());
        }

        for (String name : currentPlayers) {
            if (!knownPlayers.contains(name)) {
                handlePlayerJoin(name);
            }
        }

        knownPlayers.clear();
        knownPlayers.addAll(currentPlayers);

        if (delayTicks > 0) {
            delayTicks--;
            return;
        }

        if (commandQueue.isEmpty()) return;

        PendingCommand pending = commandQueue.pollFirst();
        String cmd = pending.command.replace(PLACEHOLDER, pending.playerName);
        submitThroughChatPipeline(cmd);
        info("Ran for " + pending.playerName + ": " + cmd);
        delayTicks = (int) Math.max(1, commandDelay.get() * 20);
    }

    private void handlePlayerJoin(String playerName) {
        if (mc.player != null && playerName.equalsIgnoreCase(mc.player.getGameProfile().name())) return;

        boolean isFriend = Friends.get().get(playerName) != null;
        boolean isThmMember = syncThmMembers.get() && isThmMember(playerName);

        if (!isFriend && !isThmMember) return;

        List<String> cmds = commands.get();
        if (cmds.isEmpty()) {
            info("No commands configured. Skipping " + playerName + ".");
            return;
        }

        String reason = isFriend ? "friend" : "THM member";
        info(playerName + " joined (" + reason + "). Queuing " + cmds.size() + " command(s).");

        for (String cmd : cmds) {
            commandQueue.addLast(new PendingCommand(cmd, playerName));
        }
    }

    private boolean isThmMember(String playerName) {
        ThmMembers.Member member = ThmMembers.getMemberByMcName(playerName);
        return member != null && !ThmMembers.isKillOnSight(member) && !ThmMembers.isIgnore(member);
    }

    private void submitThroughChatPipeline(String cmd) {
        CommandSubmitter.submit(mc, cmd);
    }

    private record PendingCommand(String command, String playerName) {}

    // Routes through ChatScreen.sendMessage() so client-side command interceptors
    // (Future Client, Wurst, etc.) that inject at the ChatScreen level are triggered.
    private static final class CommandSubmitter extends ChatScreen {
        private CommandSubmitter() { super("", false); }

        static void submit(net.minecraft.client.MinecraftClient mc, String cmd) {
            CommandSubmitter submitter = new CommandSubmitter();
            try {
                java.lang.reflect.Field f = net.minecraft.client.gui.screen.Screen.class.getDeclaredField("client");
                f.setAccessible(true);
                f.set(submitter, mc);
                submitter.sendMessage(cmd, false);
            } catch (Exception e) {
                // fallback: goes through Meteor's sendChatMessage hook
                if (cmd.startsWith("/")) mc.player.networkHandler.sendChatCommand(cmd.substring(1));
                else mc.player.networkHandler.sendChatMessage(cmd);
            }
        }
    }
}
