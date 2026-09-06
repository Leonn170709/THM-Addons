/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.modules;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.friends.Friend;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.network.PlayerListEntry;
import xyz.thm.addon.THMAddon;
import xyz.thm.addon.settings.StringMultiSelect;
import xyz.thm.addon.utils.FriendClients;
import xyz.thm.addon.utils.ThmMembers;

import java.util.*;

public class FriendsSyncModule extends Module {
    private static final String PLACEHOLDER = "%player%";

    private static final Color INSTALLED = new Color(85, 255, 85);

    public enum SyncMode {
        Command,
        File
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<SyncMode> syncMode = sgGeneral.add(new EnumSetting.Builder<SyncMode>()
        .name("sync-mode")
        .description("Command: run chat commands when a tracked player joins. File: write straight into the installed clients' friend lists — no waiting for anyone to join.")
        .defaultValue(SyncMode.File)
        .build()
    );

    private final Setting<List<String>> commands = sgGeneral.add(new StringListSetting.Builder()
        .name("commands")
        .description("Commands to run when a tracked player joins. %player% = their name.")
        .defaultValue(Collections.singletonList(",friend add %player%"))
        .visible(() -> syncMode.get() == SyncMode.Command)
        .build()
    );

    private final Setting<Double> commandDelay = sgGeneral.add(new DoubleSetting.Builder()
        .name("command-delay-seconds")
        .description("Delay in seconds between each command.")
        .defaultValue(2.0)
        .min(0.1)
        .sliderMin(0.5)
        .sliderMax(30.0)
        .visible(() -> syncMode.get() == SyncMode.Command)
        .build()
    );

    private final Setting<Boolean> syncThmMembers = sgGeneral.add(new BoolSetting.Builder()
        .name("sync-thm-members")
        .description("Include THM members, not just Meteor friends — online ones in Command mode, the whole member list in File mode.")
        .defaultValue(false)
        .build()
    );

    private final Setting<StringMultiSelect> syncRanks = sgGeneral.add(new GenericSetting.Builder<StringMultiSelect>()
        .name("sync-ranks")
        .description("THM ranks to add as friends.")
        .defaultValue(defaultSyncRanks())
        .visible(syncThmMembers::get)
        .build()
    );

    private static StringMultiSelect defaultSyncRanks() {
        StringMultiSelect select = new StringMultiSelect("Sync Ranks", () -> ThmMembers.RANK_HIERARCHY);
        select.selected().addAll(ThmMembers.RANK_HIERARCHY);
        return select;
    }

    private final Setting<Boolean> higherRanks = sgGeneral.add(new BoolSetting.Builder()
        .name("include-higher-ranks")
        .description("Also add everyone ranked above the picked ranks.")
        .defaultValue(true)
        .visible(syncThmMembers::get)
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

        // File mode is button-driven: it writes straight into the other clients, which is not
        // something to do behind the user's back just because the module got toggled on.
        if (syncMode.get() == SyncMode.File) {
            info("File mode. Use Sync All in the settings to sync now.");
            return;
        }

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
        if (syncMode.get() == SyncMode.File) return;
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

    // One row per *installed* client — clients that aren't there aren't listed at all, so the table
    // doubles as the "what have I got" indicator. The rest of the module works either way.
    //
    // Import and Export are named from the other client's point of view: Import puts friends *into*
    // that client, Export takes them *out of* it. Meteor is the hub either way.
    @Override
    public WWidget getWidget(GuiTheme theme) {
        List<FriendClients.Source> installed = installedClients();
        if (installed.isEmpty()) return theme.label("No supported clients found.");

        WTable table = theme.table();

        WButton syncAll = table.add(theme.button("Sync All")).expandX().widget();
        syncAll.tooltip = "Merge every installed client's friend list with Meteor's, so all of them end up with all of the friends. Only ever adds, never removes.";
        syncAll.action = this::syncAll;
        table.row();

        for (FriendClients.Source source : installed) {
            table.add(theme.label(source.name()).color(INSTALLED));

            if (source.canReceive()) {
                WButton importButton = table.add(theme.button("Import")).widget();
                importButton.tooltip = "Import your Meteor friends into " + source.name() + ". Nothing is removed from it.";
                importButton.action = () -> importInto(source);
            }

            WButton exportButton = table.add(theme.button("Export")).widget();
            exportButton.tooltip = "Export " + source.name() + "'s friends out into Meteor.";
            exportButton.action = () -> exportFrom(source);

            table.row();
        }

        return table;
    }

    private List<FriendClients.Source> installedClients() {
        List<FriendClients.Source> installed = new ArrayList<>();
        for (FriendClients.Source source : FriendClients.ALL) {
            if (source.installed()) installed.add(source);
        }
        return installed;
    }

    /**
     * Export out of every installed client into Meteor, then import that merged list into all of
     * them — so a friend known to only one client ends up in every other one. The two passes have
     * to stay separate: exporting and importing one client at a time would write the first client
     * before the last one had ever been read.
     */
    private void syncAll() {
        List<FriendClients.Source> installed = installedClients();
        if (installed.isEmpty()) {
            info("No supported clients found.");
            return;
        }

        for (FriendClients.Source source : installed) exportFrom(source);
        for (FriendClients.Source source : installed) {
            if (source.canReceive()) importInto(source);
        }
    }

    /** That client's friends → Meteor. */
    private void exportFrom(FriendClients.Source source) {
        try {
            int added = 0;
            for (String name : source.read().get()) {
                if (Friends.get().add(new Friend(name))) added++;
            }
            info("Exported " + added + " friend(s) out of " + source.name() + ".");
        } catch (Throwable t) {
            error("Could not read " + source.name() + " friends: " + t.getMessage());
        }
    }

    /** Meteor's friends (and THM members, if enabled) → that client. */
    private void importInto(FriendClients.Source source) {
        try {
            // Each source reports whether it really took the name, so duplicates and the ones a
            // client can't accept (Lambda needs a resolvable UUID) never inflate the count.
            Set<String> seen = new HashSet<>();
            int added = 0;

            for (String name : namesToShare()) {
                if (!seen.add(name.toLowerCase(Locale.ENGLISH))) continue;
                if (source.add().test(name)) added++;
            }

            info("Imported " + added + " friend(s) into " + source.name() + ".");
        } catch (Throwable t) {
            error("Could not write " + source.name() + " friends: " + t.getMessage());
        }
    }

    /** Meteor's friends, plus every THM member's names when sync-thm-members is on. */
    private List<String> namesToShare() {
        List<String> names = new ArrayList<>();
        for (Friend friend : Friends.get()) names.add(friend.getName());

        if (syncThmMembers.get()) {
            for (ThmMembers.Member member : ThmMembers.getCachedMembers()) {
                if (ThmMembers.isKillOnSight(member) || ThmMembers.isIgnore(member)) continue;
                if (!rankSelected(member)) continue;
                Collections.addAll(names, member.mcNames);
            }
        }

        return names;
    }

    private boolean isThmMember(String playerName) {
        ThmMembers.Member member = ThmMembers.getMemberByMcName(playerName);
        return member != null && !ThmMembers.isKillOnSight(member) && !ThmMembers.isIgnore(member)
            && rankSelected(member);
    }

    /**
     * Whether this member's rank is one of the picked ones — plus, with include-higher-ranks on,
     * anything ranked strictly above the highest picked rank.
     *
     * <p>ponytail: a rank missing from {@link ThmMembers#RANK_HIERARCHY} is never synced; add it
     * there if it should be pickable.
     */
    private boolean rankSelected(ThmMembers.Member member) {
        int index = ThmMembers.rankIndex(member == null ? null : member.rank);
        if (index == -1) return false;

        int highestPicked = ThmMembers.RANK_HIERARCHY.size();
        for (String rank : syncRanks.get().selected()) {
            int picked = ThmMembers.rankIndex(rank);
            if (picked == index) return true;
            if (picked != -1) highestPicked = Math.min(highestPicked, picked);
        }

        // Nothing (valid) picked means nothing syncs — otherwise include-higher-ranks would read
        // an empty selection as "everyone is above it".
        if (highestPicked == ThmMembers.RANK_HIERARCHY.size()) return false;

        return higherRanks.get() && index < highestPicked;
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
