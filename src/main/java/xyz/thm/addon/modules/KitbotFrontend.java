package xyz.thm.addon.modules;

import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import xyz.thm.addon.THMAddon;
import xyz.thm.addon.utils.KitbotChatCommandParser;

import java.util.Locale;

public class KitbotFrontend extends Module {
    public static final String KITBOT_NAME = "KitBot1";
    private static final long FOLLOW_UP_TIMEOUT_MS = 60_000L;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("mode")
        .description("Which kitbot command to send.")
        .defaultValue(Mode.Goto)
        .build()
    );

    private final Setting<Direction> updateDirection = sgGeneral.add(new EnumSetting.Builder<Direction>()
        .name("update-direction")
        .description("Direction used for $update.")
        .defaultValue(Direction.West)
        .visible(() -> mode.get() == Mode.Update)
        .build()
    );

    private final Setting<Direction> gotoDirection = sgGeneral.add(new EnumSetting.Builder<Direction>()
        .name("goto-direction")
        .description("Direction used for $goto.")
        .defaultValue(Direction.West)
        .visible(() -> mode.get() == Mode.Goto)
        .build()
    );

    private final Setting<KitName> kitName = sgGeneral.add(new EnumSetting.Builder<KitName>()
        .name("kit")
        .description("Kit to order.")
        .defaultValue(KitName.Pvp)
        .visible(() -> mode.get() == Mode.Kit)
        .build()
    );

    private final Setting<Integer> kitAmount = sgGeneral.add(new IntSetting.Builder()
        .name("amount")
        .description("Amount to order.")
        .defaultValue(1)
        .min(1)
        .max(16)
        .sliderMax(16)
        .visible(() -> mode.get() == Mode.Kit)
        .build()
    );

    private final Setting<String> sendPlayer = sgGeneral.add(new StringSetting.Builder()
        .name("send-player")
        .description("Player name used for $send.")
        .defaultValue("")
        .visible(() -> mode.get() == Mode.Send)
        .build()
    );

    private final Setting<KitName> sendKit = sgGeneral.add(new EnumSetting.Builder<KitName>()
        .name("send-kit")
        .description("Kit used for $send.")
        .defaultValue(KitName.Pvp)
        .visible(() -> mode.get() == Mode.Send)
        .build()
    );

    private final Setting<Integer> sendAmount = sgGeneral.add(new IntSetting.Builder()
        .name("send-amount")
        .description("Amount used for $send.")
        .defaultValue(1)
        .min(1)
        .max(16)
        .sliderMax(16)
        .visible(() -> mode.get() == Mode.Send)
        .build()
    );

    private final Setting<Boolean> autoTp = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-tp")
        .description("Automatically handles teleport requests for kitbot commands.")
        .defaultValue(true)
        .visible(() -> mode.get() == Mode.Kit || mode.get() == Mode.Goto || mode.get() == Mode.Update)
        .build()
    );

    private ActiveRequest activeRequest;
    private long followUpDeadlineMs;

    public KitbotFrontend() {
        super(THMAddon.MAIN, "Kitbot-frontend", "Send kitbot commands ($update, $goto, $kit, $send, $token, $claim)");
    }

    @Override
    public void onActivate() {
        if (mc.player == null || mc.world == null) {
            toggle();
            return;
        }

        activeRequest = createActiveRequestSnapshot();
        if (activeRequest == null) {
            warning("Unable to build a valid KitBot request from current settings.");
            toggle();
            return;
        }

        dispatch(activeRequest);

        if (!activeRequest.expectsFollowUp()) {
            toggle();
            return;
        }

        followUpDeadlineMs = System.currentTimeMillis() + FOLLOW_UP_TIMEOUT_MS;
    }

    @Override
    public void onDeactivate() {
        activeRequest = null;
        followUpDeadlineMs = 0L;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (!isActive() || activeRequest == null || !activeRequest.expectsFollowUp()) return;
        if (System.currentTimeMillis() < followUpDeadlineMs) return;

        warning("KitBot follow-up timed out after 1 minute.");
        toggle();
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        if (!isActive()) return;
        if (activeRequest != null && activeRequest.expectsFollowUp()) {
            warning("KitBot request cleared on disconnect.");
        }
        toggle();
    }

    @EventHandler
    private void onMessageReceive(ReceiveMessageEvent event) {
        if (!isActive() || activeRequest == null || !activeRequest.expectsFollowUp()) return;

        String msg = event.getMessage().getString();
        if (activeRequest.followUpKind() == KitbotChatCommandParser.FollowUpKind.TPY
            && msg.contains(KITBOT_NAME + " wants to teleport to you")) {
            ChatUtils.sendPlayerMsg("/tpy " + KITBOT_NAME);
            info("Accepted KitBot1 teleport request.");
            toggle();
            return;
        }

        if (activeRequest.followUpKind() == KitbotChatCommandParser.FollowUpKind.TPA
            && msg.contains(KITBOT_NAME + " whispers: Bot has arrived at highway")
            && msg.contains("you may teleport")) {
            ChatUtils.sendPlayerMsg("/tpa " + KITBOT_NAME);
            info("TPA has been sent.");
            toggle();
        }
    }

    public void applyChatRequest(KitbotChatCommandParser.CommandRequest request) {
        if (request == null) return;

        mode.set(request.mode());

        if (request.direction() != null) {
            switch (request.mode()) {
                case Goto -> gotoDirection.set(request.direction());
                case Update -> updateDirection.set(request.direction());
            }
        }

        if (request.kit() != null) {
            switch (request.mode()) {
                case Kit -> {
                    kitName.set(request.kit());
                    kitAmount.set(request.amount());
                }
                case Send -> {
                    sendKit.set(request.kit());
                    sendAmount.set(request.amount());
                }
            }
        }

        if (request.playerName() != null && request.mode() == Mode.Send) {
            sendPlayer.set(request.playerName());
        }
    }

    public static void sendUpdate(Direction direction) {
        if (direction == null) return;
        ChatUtils.sendPlayerMsg("/msg " + KITBOT_NAME + " $update " + direction.command);
    }

    public static void sendGoto(Direction direction) {
        if (direction == null) return;
        ChatUtils.sendPlayerMsg("/msg " + KITBOT_NAME + " $goto " + direction.command);
    }

    public static void kitOrder(KitName kit, int amount) {
        if (kit == null) return;
        kitOrder(kit.label, amount);
    }

    public static void kitOrder(String kit, int amount) {
        String safeKit = kit == null ? "" : kit.trim();
        if (safeKit.isEmpty()) return;
        int safeAmount = Math.max(1, amount);
        ChatUtils.sendPlayerMsg(String.format(Locale.ROOT, "/msg %s $kit %s %d", KITBOT_NAME, safeKit, safeAmount));
    }

    public static void sendToPlayer(String player, KitName kit, int amount) {
        if (kit == null) return;
        sendToPlayer(player, kit.label, amount);
    }

    public static void sendToPlayer(String player, String kit, int amount) {
        String safePlayer = player == null ? "" : player.trim();
        String safeKit = kit == null ? "" : kit.trim();
        if (safePlayer.isEmpty() || safeKit.isEmpty()) return;
        int safeAmount = Math.max(1, amount);
        ChatUtils.sendPlayerMsg(String.format(Locale.ROOT, "/msg %s $send %s %s %d", KITBOT_NAME, safePlayer, safeKit, safeAmount));
    }

    public static void sendToken() {
        ChatUtils.sendPlayerMsg("/msg " + KITBOT_NAME + " $token");
    }

    public static void sendClaim() {
        ChatUtils.sendPlayerMsg("/msg " + KITBOT_NAME + " $claim");
    }

    public static void sendUpdate(String direction) {
        Direction parsed = Direction.fromString(direction);
        if (parsed != null) sendUpdate(parsed);
    }

    public static void sendGoto(String direction) {
        Direction parsed = Direction.fromString(direction);
        if (parsed != null) sendGoto(parsed);
    }

    private ActiveRequest createActiveRequestSnapshot() {
        KitbotChatCommandParser.CommandType type = switch (mode.get()) {
            case Goto -> KitbotChatCommandParser.CommandType.Goto;
            case Update -> KitbotChatCommandParser.CommandType.Update;
            case Kit -> KitbotChatCommandParser.CommandType.Kit;
            case Send -> KitbotChatCommandParser.CommandType.Send;
            case Token -> KitbotChatCommandParser.CommandType.Token;
            case Claim -> KitbotChatCommandParser.CommandType.Claim;
        };

        KitbotChatCommandParser.CommandRequest baseRequest = switch (mode.get()) {
            case Goto -> new KitbotChatCommandParser.CommandRequest(type, Mode.Goto, gotoDirection.get(), null, null, 1);
            case Update -> new KitbotChatCommandParser.CommandRequest(type, Mode.Update, updateDirection.get(), null, null, 1);
            case Kit -> new KitbotChatCommandParser.CommandRequest(type, Mode.Kit, null, kitName.get(), null, kitAmount.get());
            case Send -> {
                String player = sendPlayer.get() == null ? "" : sendPlayer.get().trim();
                if (player.isEmpty()) yield null;
                yield new KitbotChatCommandParser.CommandRequest(type, Mode.Send, null, sendKit.get(), player, sendAmount.get());
            }
            case Token -> new KitbotChatCommandParser.CommandRequest(type, Mode.Token, null, null, null, 1);
            case Claim -> new KitbotChatCommandParser.CommandRequest(type, Mode.Claim, null, null, null, 1);
        };

        if (baseRequest == null) return null;

        boolean expectsFollowUp = autoTp.get() && baseRequest.defaultFollowUpKind() != KitbotChatCommandParser.FollowUpKind.NONE;
        KitbotChatCommandParser.FollowUpKind followUpKind = expectsFollowUp
            ? baseRequest.defaultFollowUpKind()
            : KitbotChatCommandParser.FollowUpKind.NONE;

        return new ActiveRequest(
            baseRequest.type(),
            baseRequest.mode(),
            baseRequest.direction(),
            baseRequest.kit(),
            baseRequest.playerName(),
            baseRequest.amount(),
            expectsFollowUp,
            followUpKind
        );
    }

    private void dispatch(ActiveRequest request) {
        switch (request.mode()) {
            case Goto -> sendGoto(request.direction());
            case Update -> sendUpdate(request.direction());
            case Kit -> kitOrder(request.kit(), request.amount());
            case Send -> sendToPlayer(request.playerName(), request.kit(), request.amount());
            case Token -> sendToken();
            case Claim -> sendClaim();
        }
    }

    private record ActiveRequest(
        KitbotChatCommandParser.CommandType type,
        Mode mode,
        Direction direction,
        KitName kit,
        String playerName,
        int amount,
        boolean expectsFollowUp,
        KitbotChatCommandParser.FollowUpKind followUpKind
    ) {}

    public enum Mode {
        Update,
        Goto,
        Kit,
        Send,
        Token,
        Claim
    }

    public enum Direction {
        West("W"),
        East("E"),
        North("N"),
        South("S"),
        NorthEast("NE"),
        SouthEast("SE"),
        SouthWest("SW"),
        NorthWest("NW"),
        DugWest("dugW"),
        DugEast("dugE"),
        DugNorth("dugN"),
        DugSouth("dugS"),
        DugNorthEast("dugNE"),
        DugSouthEast("dugSE"),
        DugSouthWest("dugSW"),
        DugNorthWest("dugNW");

        public final String command;

        Direction(String command) {
            this.command = command;
        }

        public static Direction fromString(String value) {
            if (value == null) return null;
            String normalized = value.trim().toLowerCase(Locale.ROOT);
            for (Direction direction : values()) {
                if (direction.command.toLowerCase(Locale.ROOT).equals(normalized)) return direction;
                if (direction.name().toLowerCase(Locale.ROOT).equals(normalized)) return direction;
            }
            return null;
        }
    }

    public enum KitName {
        Refill("Refill", 1),
        Pvp("PvP", 0.5),
        Echest("Echest", 0.2),
        Pickaxe("Pickaxe", 1),
        Highway("Highway", 0.2),
        Lights("Lights", 1),
        Travel("Travel", 1.5),
        Grief("Grief", 1.5),
        Redstone("Redstone", 1),
        Exp("Exp", 1),
        Logs("Logs", 1),
        Mapart("Mapart", 1.5),
        Stash("Stash", 1),
        Bricks("Bricks", 1),
        Gapples("Gapples", 1),
        Concrete("Concrete", 1);

        public final String label;
        public final double tokens;

        KitName(String label, double tokens) {
            this.label = label;
            this.tokens = tokens;
        }

        @Override
        public String toString() {
            return label;
        }

        public String describe() {
            return String.format(Locale.ROOT, "%s (%.1f tokens)", label, tokens);
        }

        public static KitName fromString(String value) {
            if (value == null) return null;
            String normalized = value.trim().toLowerCase(Locale.ROOT);
            for (KitName kit : values()) {
                if (kit.label.equals(normalized) || kit.name().equalsIgnoreCase(normalized)) {
                    return kit;
                }
            }
            return null;
        }

        public static String list() {
            StringBuilder sb = new StringBuilder();
            for (KitName kit : values()) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(kit.describe());
            }
            return sb.toString();
        }
    }
}
