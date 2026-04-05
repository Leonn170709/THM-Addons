package xyz.thm.addon.hud;

import com.google.common.util.concurrent.AtomicDouble;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import xyz.thm.addon.THMAddon;
import xyz.thm.addon.utils.ThmMembers;

import java.util.ArrayList;
import java.util.List;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class KosHud extends HudElement {
    public static final HudElementInfo<KosHud> INFO = new HudElementInfo<>(
        THMAddon.HUD_GROUP,
        "KOS Hud",
        "Shows all online Kill-on-Sight (KOS) players.",
        KosHud::new
    );

    public KosHud() {
        super(INFO);
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgColors = settings.createGroup("Colors");

    public final Setting<Boolean> showSelf = sgGeneral.add(new BoolSetting.Builder()
        .name("show-self")
        .description("Whether to show yourself in the list.")
        .defaultValue(false)
        .build()
    );

    public final Setting<Boolean> showBackground = sgGeneral.add(new BoolSetting.Builder()
        .name("show-background")
        .description("Whether to show a background behind the KOS list.")
        .defaultValue(false)
        .build()
    );

    public final Setting<SettingColor> colorHeader = sgColors.add(new ColorSetting.Builder()
        .name("header-color")
        .description("Color of the header text (Online KOS Players:)")
        .defaultValue(new SettingColor(255, 0, 0, 255))
        .build()
    );

    public final Setting<SettingColor> colorRank = sgColors.add(new ColorSetting.Builder()
        .name("kos-rank-color")
        .description("Color of the Kill on Sight rank.")
        .defaultValue(new SettingColor(255, 0, 0, 255))
        .build()
    );

    public final Setting<SettingColor> colorMemberInfo = sgColors.add(new ColorSetting.Builder()
        .name("member-info-color")
        .description("Color of player name and brackets.")
        .defaultValue(new SettingColor(255, 255, 255, 255))
        .build()
    );

    public final Setting<SettingColor> colorBackground = sgColors.add(new ColorSetting.Builder()
        .name("background-color")
        .description("Color of the background")
        .defaultValue(new SettingColor(0, 0, 0, 150))
        .visible(showBackground::get)
        .build()
    );

    // Reset cache on world join
    public void onWorldJoin() {
        ThmMembers.resetCache();
    }

    @Override
    public void render(HudRenderer renderer) {
        if (mc.player == null) return;

        // Get all online players from tab list
        List<String> onlinePlayers = new ArrayList<>(mc.player.networkHandler.getPlayerList().stream()
            .map(playerInfo -> playerInfo.getProfile().name()).toList());

        List<String> kosPlayers = onlinePlayers.stream()
            .filter(player -> {
                ThmMembers.Member member = ThmMembers.getMemberByMcName(player);
                return ThmMembers.isKillOnSight(member);
            })
            .sorted(String::compareToIgnoreCase)
            .toList();

        AtomicDouble screenY = new AtomicDouble(y + 4);

        renderer.text("Online KOS Players: ", x, screenY.get(), colorHeader.get(), true);
        screenY.addAndGet(renderer.textHeight(true) + 1);
        screenY.addAndGet(5);

        AtomicDouble largestWidth = new AtomicDouble(renderer.textWidth("Online KOS Players: ", true));

        kosPlayers.forEach(player -> {
            if (!showSelf.get() && player.equals(mc.player.getName().getString())) {
                return;
            }
            if (ThmMembers.isIgnore(player)) {
                return;
            }

            String rankLabel = "Kill on Sight";
            String displayText = String.format("[%s] %s", rankLabel, player);

            renderer.text("[", x, screenY.get(), colorMemberInfo.get(), true);
            double xOffset = x + renderer.textWidth("[", true);

            renderer.text(rankLabel, xOffset, screenY.get(), colorRank.get(), true);
            xOffset += renderer.textWidth(rankLabel, true);

            renderer.text(String.format("] %s", player), xOffset, screenY.get(), colorMemberInfo.get(), true);

            double totalWidth = renderer.textWidth(displayText, true);
            if (totalWidth > largestWidth.get()) {
                largestWidth.set(totalWidth);
            }

            screenY.addAndGet(renderer.textHeight(true) + 2);
        });

        if (showBackground.get()) {
            renderer.quad(x - 2, y, largestWidth.get() + 4, screenY.get() - y + 2, colorBackground.get());
        }

        setSize(largestWidth.get() + 4, screenY.get() - y + 4);
    }
}
