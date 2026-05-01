package xyz.thm.addon.utils;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.game.SendMessageEvent;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.orbit.EventHandler;
import xyz.thm.addon.modules.KitbotFrontend;

public final class KitbotChatRouter {
    private static KitbotChatRouter INSTANCE;
    private static boolean enabled = true;

    private KitbotChatRouter() {
        MeteorClient.EVENT_BUS.subscribe(this);
    }

    public static KitbotChatRouter getInstance() {
        if (INSTANCE == null) INSTANCE = new KitbotChatRouter();
        return INSTANCE;
    }

    public static void setEnabled(boolean value) {
        enabled = value;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    @EventHandler
    private void onSendMessage(SendMessageEvent event) {
        if (!enabled) return;

        if (event.message == null) return;
        String message = event.message.trim();
        if (!message.startsWith("$")) return;

        KitbotChatCommandParser.ParseResult result = KitbotChatCommandParser.parse(message, KitbotChatCommandParser.getOnlinePlayerNames());
        if (!result.isRecognized()) return;

        event.cancel();

        KitbotFrontend frontend = Modules.get().get(KitbotFrontend.class);
        if (frontend == null) return;

        if (!result.isValid()) {
            frontend.warning(result.errorMessage());
            return;
        }

        if (!KitbotFrontend.submitChatRequest(result.request())) {
            frontend.warning("KitBot request window is already active.");
            return;
        }
    }
}
