package xyz.thm.addon.hud;

import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import xyz.thm.addon.modules.HighwayBuilderTHM;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.render.color.Color;
import xyz.thm.addon.THMAddon;
import xyz.thm.addon.modules.HighwaySearcher;

import java.util.Objects;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class HighwayHud extends HudElement {
    public static final HudElementInfo<HighwayHud> INFO = new HudElementInfo<>(THMAddon.HUD_GROUP, "Highway-hud", "View your stats while paving.", HighwayHud::new);

    public HighwayHud() {
        super(INFO);
    }
    private int lastDistance = 0;
    HighwayBuilderTHM mod = Modules.get().get(HighwayBuilderTHM.class);
    private String[][] getLines() {
        if (mc.player == null) return new String[0][0];
        if (mod == null) return new String[0][0];

        // Distanz NUR aktualisieren, wenn Modul aktiv + start != null
        if (mod.isActive() && mod.start != null) {
            lastDistance = (int) PlayerUtils.distanceTo(mod.start);
        }
        String direction = mod.dir != null ? mod.dir.toString() : "";


        return new String[][]{
            {"Distance travelled", String.valueOf(lastDistance)},
            {"Blocks broken", String.valueOf(mod.blocksBroken)},
            {"Blocks placed", String.valueOf(mod.blocksPlaced)},
            {"Direction", direction}
        };
    }

    @Override
    public void render(HudRenderer renderer) {
        if (!Objects.requireNonNull(Modules.get().get(HighwayBuilderTHM.class)).enablehud.get()) return;

        String[][] lines = getLines();

        double lineHeight = renderer.textHeight(true);
        double width = 0;

        // Calculate maximum width of title-value pairs
        for (String[] line : lines) {
            double lineWidth = renderer.textWidth(line[0], true) + renderer.textWidth(": ", true) + renderer.textWidth(line[1], true);
            width = Math.max(width, lineWidth);
        }

        setSize(width, lineHeight * lines.length);

        // Render each line
        double currentY = y;
        for (String[] line : lines) {
            double currentX = x;

            renderer.text(line[0], currentX, currentY, Color.WHITE, true);
            currentX += renderer.textWidth(line[0], true);

            renderer.text(": ", currentX, currentY, Color.WHITE, true);
            currentX += renderer.textWidth(": ", true);

            // Handle the progress line differently
            if (line[0].equals("Progress")) {
                String percentageText = line[1];
                String doneText = " done";

                double percentageWidth = renderer.textWidth(percentageText, true);
                renderer.textWidth(doneText, true);

                renderer.text(percentageText, currentX, currentY, Color.RED, true);
                currentX += percentageWidth;

                renderer.text(doneText, currentX, currentY, Color.WHITE, true);
            } else {
                renderer.text(line[1], currentX, currentY, Color.RED, true);
            }

            currentY += lineHeight;  // Move to the next line
        }
    }
}
