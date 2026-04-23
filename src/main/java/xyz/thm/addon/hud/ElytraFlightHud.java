package xyz.thm.addon.hud;

import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.render.color.Color;
import xyz.thm.addon.THMAddon;
import xyz.thm.addon.modules.ElytraRoute;

import java.util.ArrayList;
import java.util.List;

public class ElytraFlightHud extends HudElement {
    public static final HudElementInfo<ElytraFlightHud> INFO = new HudElementInfo<>(THMAddon.HUD_GROUP, "elytra-flight-hud", "Displays Elytra Route flight stats.", ElytraFlightHud::new);

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> showElytraCount = sgGeneral.add(new BoolSetting.Builder()
        .name("show-elytra-count")
        .description("Shows the usable elytra count.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> showTotalFlightTime = sgGeneral.add(new BoolSetting.Builder()
        .name("show-total-flight-time")
        .description("Shows the total estimated flight time for usable elytras.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> showDestinationEta = sgGeneral.add(new BoolSetting.Builder()
        .name("show-destination-eta")
        .description("Shows the estimated arrival time to the destination.")
        .defaultValue(true)
        .build()
    );

    public ElytraFlightHud() {
        super(INFO);
    }

    @Override
    public void render(HudRenderer renderer) {
        ElytraRoute route = ElytraRoute.INSTANCE;
        if (route == null || !route.isActive()) {
            String text = "Elytra Route: Inactive";
            setSize(renderer.textWidth(text, true), renderer.textHeight(true));
            renderer.text(text, x, y, Color.GRAY, true);
            return;
        }

        List<String[]> lines = new ArrayList<>();
        if (showElytraCount.get()) lines.add(new String[] { "Usable elytras", String.valueOf(route.getHudUsableElytraCount()) });
        if (showTotalFlightTime.get()) lines.add(new String[] { "Flight time", formatFlightTime(route.getHudFlightTimeSeconds()) });
        if (showDestinationEta.get()) lines.add(new String[] { "Destination ETA", formatEta(route.getHudEtaSeconds()) });

        if (lines.isEmpty()) {
            String text = "Elytra Route: Active";
            setSize(renderer.textWidth(text, true), renderer.textHeight(true));
            renderer.text(text, x, y, Color.WHITE, true);
            return;
        }

        double lineHeight = renderer.textHeight(true);
        double width = 0;
        for (String[] line : lines) {
            double lineWidth = renderer.textWidth(line[0], true) + renderer.textWidth(": ", true) + renderer.textWidth(line[1], true);
            width = Math.max(width, lineWidth);
        }

        setSize(width, lineHeight * lines.size());

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

    private String formatFlightTime(long totalSeconds) {
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        return String.format("%02d:%02d", hours, minutes);
    }

    private String formatEta(double etaSeconds) {
        if (etaSeconds < 0 || Double.isNaN(etaSeconds) || Double.isInfinite(etaSeconds)) return "N/A";
        if (etaSeconds >= 3600) return String.format("%.2fh", etaSeconds / 3600.0);
        if (etaSeconds >= 60) return String.format("%.1fm", etaSeconds / 60.0);
        return String.format("%.0fs", etaSeconds);
    }
}
