package xyz.thm.addon.modules;

import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.Formatting;
import xyz.thm.addon.THMAddon;

import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TPAAutomationModule extends Module {
    public TPAAutomationModule() {
        super(THMAddon.CATEGORY, "TPA-automations", "A module that automatically accepts or denies teleport requests based on a list of approved players.");
    }

    private final SettingGroup sgApprovedUsers = this.settings.createGroup("Approved Users");
    private final SettingGroup sgGeneral = this.settings.createGroup("General");

    private final Setting<List<String>> approvedUsers = sgApprovedUsers.add(new StringListSetting.Builder()
        .name("approved-users-list")
        .description("A list of users to filter.")
        .defaultValue(List.of("Steve", "Notch", "GommeHD"))
        .build()
    );
    private final Setting<String> tpystring = sgGeneral.add(new StringSetting.Builder()
        .name("TP Accept Command")
        .description("The Command that accepts tps")
        .defaultValue("/tpy")
        .build()
    );
    private final Setting<String> tpnstring = sgGeneral.add(new StringSetting.Builder()
        .name("TP Deny Command")
        .description("TThe Command that denys tps")
        .defaultValue("/tpn")
        .build()
    );

    private final Setting<Boolean> acceptFriends = sgGeneral.add(new BoolSetting.Builder()
        .name("accept-friends")
        .description("Automatically accept teleport requests from your Meteor friend list.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> acceptTHMBots = sgGeneral.add(new BoolSetting.Builder()
        .name("accept-THM-bot")
        .description("Automatically accept teleport requests that are from the THM bot.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> autoDeny = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-deny")
        .description("Automatically deny teleport requests that are not from approved users.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> printTpaDetected = sgGeneral.add(new BoolSetting.Builder()
        .name("print-tpa-detected")
        .description("Print a message when a teleport request is detected.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> printTpaAccepted = sgGeneral.add(new BoolSetting.Builder()
        .name("print-tpa-accepted")
        .description("Print a message when a teleport request is accepted.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> printTpaIgnored = sgGeneral.add(new BoolSetting.Builder()
        .name("print-tpa-ignored")
        .description("Print a message when a teleport request is ignored.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> filterTpaMessages = sgGeneral.add(new BoolSetting.Builder()
        .name("filter-tpa-messages")
        .description("Filter out the servers TPA messages from the chat.")
        .defaultValue(true)
        .build()
    );

    private static final Pattern TPA_MESSAGE_PATTERN = Pattern.compile("^Type /tpy ([A-Za-z0-9_]{3,16}) to accept or /tpn [A-Za-z0-9_]{3,16} to deny\\.$");
    private static final Pattern TPA_ACCEPTED_PATTERN = Pattern.compile("^Request from ([A-Za-z0-9_]{3,16}) accepted!$");
    private static final Pattern TPA_DENIED_PATTERN = Pattern.compile("^Request from ([A-Za-z0-9_]{3,16}) denied!$");
    private static final Pattern TPA_REQUEST_PATTERN = Pattern.compile("^([A-Za-z0-9_]{3,16}) wants to teleport to you\\.$");

    private static final Set<String> THM_KIT_BOT_USERS = Set.of(
            "KitBot1"

    );

    @Override
    public void onActivate() {
    }

    @EventHandler
    private void onMessageReceive(ReceiveMessageEvent event) {
        if (!isActive() || mc.player == null) return;

        String message = event.getMessage().getString();

        if (filterTpaMessages.get()) {
            Matcher matcher = TPA_MESSAGE_PATTERN.matcher(message);
            if (matcher.matches()) {
                event.cancel();
                return;
            }

            matcher = TPA_ACCEPTED_PATTERN.matcher(message);
            if (matcher.matches()) {
                event.cancel();
                return;
            }

            matcher = TPA_DENIED_PATTERN.matcher(message);
            if (matcher.matches() && autoDeny.get()) {
                event.cancel();
                return;
            }
        }

        Matcher matcher = TPA_REQUEST_PATTERN.matcher(message);
        if (!matcher.matches()) return;

        String username = matcher.group(1);

        if (printTpaDetected.get()) info("%sTPA Detected:%s %s!", Formatting.RED, Formatting.WHITE, username);

        if (approvedUsers.get().contains(username) || (acceptFriends.get() && Friends.get().get(username) != null) || (acceptTHMBots.get() &&  THM_KIT_BOT_USERS.contains(username))) {
            ChatUtils.sendPlayerMsg(tpystring + username);

            if (printTpaAccepted.get()) info("%sAuto Accepted:%s %s!", Formatting.GREEN, Formatting.WHITE, username);

        } else if (autoDeny.get()){
            ChatUtils.sendPlayerMsg(tpnstring + username);

            if (printTpaIgnored.get()) info("%sIgnored:%s %s!", Formatting.RED, Formatting.WHITE, username);
        }

        if (filterTpaMessages.get() && printTpaDetected.get()) event.cancel();
    }
}
