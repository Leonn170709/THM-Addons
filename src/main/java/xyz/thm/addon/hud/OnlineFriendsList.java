package xyz.thm.addon.hud;

import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.minecraft.client.network.PlayerListEntry;
import xyz.thm.addon.THMAddon;

import java.util.ArrayList;
import java.util.List;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class OnlineFriendsList extends HudElement {
    public static final HudElementInfo<OnlineFriendsList> INFO = new HudElementInfo<>(
        THMAddon.HUD_GROUP,
        "online-friends",
        "Displays online friends from your friend list.",
        OnlineFriendsList::new
    );

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> background = sgGeneral.add(new BoolSetting.Builder()
        .name("background")
        .description("Displays background behind the friend list.")
        .defaultValue(true)
        .build()
    );

    private final Setting<SettingColor> backgroundColor = sgGeneral.add(new ColorSetting.Builder()
        .name("background-color")
        .description("Color of the background.")
        .defaultValue(new SettingColor(0, 0, 0, 0))
        .build()
    );

    private final Setting<SettingColor> friendColor = sgGeneral.add(new ColorSetting.Builder()
        .name("friend-color")
        .description("Color of friend names.")
        .defaultValue(new SettingColor(173, 216, 230)) // Light blue
        .build()
    );

    public OnlineFriendsList() {
        super(INFO);
    }
    @Override
    public void render(HudRenderer renderer) {
        if (mc.world == null || mc.getNetworkHandler() == null) {
            renderOffline(renderer);
            return;
        }

        List<String> onlineFriends = getOnlineFriends();
        renderFriendsList(renderer, onlineFriends);
    }

    private void renderOffline(HudRenderer renderer) {
        String title = "No Friends Online";
        double width = renderer.textWidth(title, true);
        double height = renderer.textHeight(true);

        setSize(width, height);

        if (background.get()) {
            renderer.quad(x, y, width, height, backgroundColor.get());
        }

        // Red color for "No Friends Online"
        SettingColor redColor = new SettingColor(255, 0, 0);
        renderer.text(title, x, y, redColor, true);
    }

    private void renderFriendsList(HudRenderer renderer, List<String> onlineFriends) {
        String title = onlineFriends.isEmpty() ? "No Friends Online" : "Online Friends";
        double titleWidth = renderer.textWidth(title, true);
        double lineHeight = renderer.textHeight(true);

        // Calculate dimensions
        double maxWidth = titleWidth;
        for (String friend : onlineFriends) {
            double friendWidth = renderer.textWidth(friend, false);
            if (friendWidth > maxWidth) maxWidth = friendWidth;
        }

        double totalHeight = lineHeight;
        if (!onlineFriends.isEmpty()) {
            totalHeight += onlineFriends.size() * lineHeight;
        }

        setSize(maxWidth, totalHeight);

        if (background.get()) {
            renderer.quad(x, y, maxWidth, totalHeight, backgroundColor.get());
        }

        // Title color based on friends status
        SettingColor titleColor;
        if (onlineFriends.isEmpty()) {
            titleColor = new SettingColor(255, 0, 0); // Red for "No Friends Online"
        } else {
            titleColor = new SettingColor(0, 255, 0); // Green for "Online Friends"
        }

        // Render title
        renderer.text(title, x, y, titleColor, true);

        // Render friend names in light blue
        double currentY = y + lineHeight;
        for (String friend : onlineFriends) {
            renderer.text(friend, x, currentY, friendColor.get(), false);
            currentY += lineHeight;
        }
    }

    private List<String> getOnlineFriends() {
        List<String> onlineFriends = new ArrayList<>();

        if (mc.getNetworkHandler() == null || mc.player == null) return onlineFriends;

        // Get our own player name to exclude it
        String ourPlayerName = mc.player.getName().getString();

        for (PlayerListEntry player : mc.getNetworkHandler().getPlayerList()) {
            String playerName = player.getProfile().name();

            // Skip ourselves and only include friends
            if (!playerName.equals(ourPlayerName) && Friends.get().isFriend(player)) {
                onlineFriends.add(playerName);
            }
        }

        return onlineFriends;
    }
}
