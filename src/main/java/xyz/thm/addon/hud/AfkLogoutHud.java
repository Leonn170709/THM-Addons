package xyz.thm.addon.hud;

import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.render.color.Color;
import xyz.thm.addon.THMAddon;
import xyz.thm.addon.modules.AfkLogout;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class AfkLogoutHud extends HudElement {
    public static final HudElementInfo<AfkLogoutHud> INFO = new HudElementInfo<>(
        THMAddon.HUD_GROUP,
        "afk-logout-hud",
        "Displays AFK Logout remaining time.",
        AfkLogoutHud::new
    );

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> showRemainingTime = sgGeneral.add(new BoolSetting.Builder()
        .name("show-remaining-time")
        .description("Shows remaining time until logout.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> showElytras = sgGeneral.add(new BoolSetting.Builder()
        .name("show-elytras")
        .description("Shows remaining elytras until the threshold is reached.")
        .defaultValue(false)
        .build()
    );

    private final AfkLogout mod = Modules.get().get(AfkLogout.class);

    public AfkLogoutHud() {
        super(INFO);
    }

    private String[][] getLines() {
        if (mc.player == null || mod == null) return new String[0][0];

        java.util.ArrayList<String[]> lines = new java.util.ArrayList<>();

        if (showRemainingTime.get()) {
            if (!mod.isActive()) {
                lines.add(new String[]{"Remaining time", "Inactive"});
            } else {
                long remainingMs = mod.getEstimatedTimeRemainingMs();
                String display = remainingMs >= 0L ? mod.getEstimatedTimeRemainingDisplay() : "N/A";
                lines.add(new String[]{"Remaining time", display});
            }
        }

        if (showElytras.get()) {
            if (!mod.isActive()) {
                lines.add(new String[]{"Elytras", "Inactive"});
            } else {
                lines.add(new String[]{"Elytras", String.valueOf(mod.getElytrasUntilThreshold())});
            }
        }

        return lines.toArray(new String[0][0]);
    }

    @Override
    public void render(HudRenderer renderer) {
        String[][] lines = getLines();

        double lineHeight = renderer.textHeight(true);
        double width = 0;

        for (String[] line : lines) {
            double lineWidth = renderer.textWidth(line[0], true)
                + renderer.textWidth(": ", true)
                + renderer.textWidth(line[1], true);
            width = Math.max(width, lineWidth);
        }

        setSize(width, lineHeight * lines.length);

        double currentY = y;
        for (String[] line : lines) {
            double currentX = x;

            renderer.text(line[0], currentX, currentY, Color.WHITE, true);
            currentX += renderer.textWidth(line[0], true);

            renderer.text(": ", currentX, currentY, Color.WHITE, true);
            currentX += renderer.textWidth(": ", true);

            renderer.text(line[1], currentX, currentY, Color.RED, true);
            currentY += lineHeight;
        }
    }
}
