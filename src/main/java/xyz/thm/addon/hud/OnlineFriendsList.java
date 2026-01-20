package xyz.thm.addon.hud;

import xyz.thm.addon.THMAddon;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.hud.*;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.utils.render.color.Color;
import net.minecraft.client.network.PlayerListEntry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import static meteordevelopment.meteorclient.MeteorClient.mc;

public class OnlineFriendsList extends HudElement {
    public static final HudElementInfo<OnlineFriendsList> INFO = new HudElementInfo<>(
        THMAddon.HUD_GROUP, "friends-online", "Shows all friends that are online on the server.", OnlineFriendsList::new
    );

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final Setting<String> title = sgGeneral.add(new StringSetting.Builder()
        .name("title")
        .description("Title to go above the player names")
        .defaultValue("Online Friends:")
        .build()
    );
    private final Setting<Boolean> includeSelf = sgGeneral.add(new BoolSetting.Builder()
        .name("include-self")
        .description("Include self in list")
        .defaultValue(false)
        .build()
    );

    public OnlineFriendsList() {
        super(INFO);
    }

    @Override
    protected double alignX(double width, Alignment alignment) {
        return box.alignX(getWidth(), width, alignment);
    }

    @Override
    public void render(HudRenderer renderer) {
        if (mc.player == null || mc.getNetworkHandler() == null) return;

        double height = renderer.textHeight(true, 1) + 2;
        double width = renderer.textWidth(title.get(), true, 1);
        setSize(width, height);

        double renderX = x + alignX(renderer.textWidth(title.get(), true, 1), Alignment.Auto);
        renderer.text(title.get(), renderX, y, Hud.get().textColors.get().getFirst(), true, 1);

        List<String> names = new ArrayList<>();
        for (PlayerListEntry entry : mc.player.networkHandler.getPlayerList()) {
            String name = entry.getProfile().getName();

            if (!includeSelf.get() && name.equals(mc.player.getGameProfile().getName())) continue;
            if (Friends.get().get(name) == null) continue;

            names.add(name);
        }

        Collections.sort(names);

        for (String name : names) {
            Color color = Hud.get().textColors.get().get(1);

            double textWidth = renderer.textWidth(name, false, 1);
            width = Math.max(width, textWidth);
            height += renderer.textHeight(false, 1) + 2;

            y += (int) (renderer.textHeight(false, 1) + 2);

            renderer.text(name, renderX, y, color, false);
        }

        setSize(width, height);
    }



}
