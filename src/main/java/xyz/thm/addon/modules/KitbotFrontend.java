package xyz.thm.addon.modules;

import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import xyz.thm.addon.THMAddon;

import java.util.Locale;

public class KitbotFrontend extends Module {
    public static final String KITBOT_NAME = "KitBot1";

    public KitbotFrontend() {
        super(THMAddon.MAIN, "Kitbot-frontend", "Send kitbot commands ($update, $goto, $kit)");
    }

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

    private final Setting<Boolean> autoTp = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-tp")
        .description("Automatically handles teleport requests for kitbot commands.")
        .defaultValue(true)
        .visible(() -> mode.get() == Mode.Kit || mode.get() == Mode.Goto || mode.get() == Mode.Update)
        .build()
    );

    private boolean tpaHandled = false;

    @Override
    public void onActivate() {
        switch (mode.get()) {
            case Update -> sendUpdate(updateDirection.get());
            case Goto -> sendGoto(gotoDirection.get());
            case Kit -> kitOrder(kitName.get(), kitAmount.get());
        }

        if (autoTp.get() && (mode.get() == Mode.Kit || mode.get() == Mode.Goto || mode.get() == Mode.Update)) {
            tpaHandled = false;
            return;
        }

        toggle();
    }

    @EventHandler
    private void onMessageReceive(ReceiveMessageEvent event) {
        if (!isActive()) return;
        if (!autoTp.get() || tpaHandled) return;

        String msg = event.getMessage().getString();
        if ((mode.get() == Mode.Kit || mode.get() == Mode.Update)
            && msg.contains("KitBot1 wants to teleport to you")) {
            ChatUtils.sendPlayerMsg("/tpy " + KITBOT_NAME);
            info("Accepted KitBot1 teleport request.");
            tpaHandled = true;
            toggle();
        }

        if (mode.get() == Mode.Goto
            && msg.contains("KitBot1 whispers: Bot has arrived at highway")
            && msg.contains("you may teleport")) {
            ChatUtils.sendPlayerMsg("/tpa " + KITBOT_NAME);
            info("TPA has been sent.");
            tpaHandled = true;
            toggle();
        }
    }

    public static void sendUpdate(Direction direction) {
        ChatUtils.sendPlayerMsg("/msg " + KITBOT_NAME + " $update " + direction.command);
    }

    public static void sendGoto(Direction direction) {
        ChatUtils.sendPlayerMsg("/msg " + KITBOT_NAME + " $goto " + direction.command);
    }

    public static void kitOrder(KitName kit, int amount) {
        kitOrder(kit.label, amount);
    }

    public static void kitOrder(String kit, int amount) {
        String safeKit = kit == null ? "" : kit.trim();
        if (safeKit.isEmpty()) return;
        int safeAmount = Math.max(1, amount);
        ChatUtils.sendPlayerMsg(String.format(Locale.ROOT, "/msg %s $kit %s %d", KITBOT_NAME, safeKit, safeAmount));
    }

    public static void sendUpdate(String direction) {
        Direction parsed = Direction.fromString(direction);
        if (parsed != null) sendUpdate(parsed);
    }

    public static void sendGoto(String direction) {
        Direction parsed = Direction.fromString(direction);
        if (parsed != null) sendGoto(parsed);
    }

    public enum Mode {
        Update,
        Goto,
        Kit
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
