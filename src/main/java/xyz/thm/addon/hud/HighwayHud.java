package xyz.thm.addon.hud;

import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import xyz.thm.addon.THMAddon;
import xyz.thm.addon.modules.HighwayBuilderTHM;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class HighwayHud extends HudElement {
    public static final HudElementInfo<HighwayHud> INFO = new HudElementInfo<>(THMAddon.HUD_GROUP, "Highway-hud", "View your stats while paving.", HighwayHud::new);

    public HighwayHud() {
        super(INFO);
    }
    private int lastDistance = 0;
    HighwayBuilderTHM mod = Modules.get().get(HighwayBuilderTHM.class);
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final Setting<Boolean> showDistance = sgGeneral.add(new BoolSetting.Builder()
        .name("Show-distance")
        .description("Displays the distance")
        .defaultValue(true)
        .build()
    );
    private final Setting<Boolean> showBroken = sgGeneral.add(new BoolSetting.Builder()
        .name("show-blocks-broken")
        .description("Displays the blocks broken")
        .defaultValue(true)
        .build()
    );
    private final Setting<Boolean> showPlaced = sgGeneral.add(new BoolSetting.Builder()
        .name("show-Placed")
        .description("Displays the blocks placed")
        .defaultValue(true)
        .build()
    );
    private final Setting<Boolean> showDirection = sgGeneral.add(new BoolSetting.Builder()
        .name("show-direction")
        .description("Displays direction you're heading in")
        .defaultValue(true)
        .build()
    );
    private final Setting<Boolean> showRefill = sgGeneral.add(new BoolSetting.Builder()
        .name("show-distance-till-restock")
        .description("Shows the eta of the distance till restock")
        .defaultValue(true)
        .build()
    );
    private String[][] getLines() {
        if (mc.player == null || mod == null) return new String[0][0];

        if (mod.isActive() && mod.start != null)
            lastDistance = (int) PlayerUtils.distanceTo(mod.start);

        List<String[]> l = new ArrayList<>();
        String dir = mod.dir != null ? mod.dir.toString() : "";
        distanceTillRestock = getDistanceTillRestock();

        if (showDistance.get())  l.add(new String[]{"Distance travelled", String.valueOf(lastDistance)});
        if (showBroken.get())   l.add(new String[]{"Blocks broken", String.valueOf(mod.blocksBroken)});
        if (showPlaced.get())   l.add(new String[]{"Blocks placed", String.valueOf(mod.blocksPlaced)});
        if (showDirection.get())l.add(new String[]{"Direction", dir});
        if (showRefill.get())   l.add(new String[]{"Distance till restock", String.valueOf(distanceTillRestock)});

        return l.toArray(new String[0][0]);
    }
    private int distanceTillRestock = 0;
    public static int getDistanceTillRestock() {
        if (mc.player == null) return 0;

        int obsidian = 0;
        int echests = 0;

        // Player inventory (Hotbar + Main)
        for (int i = 0; i < mc.player.getInventory().size(); i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);

            if (stack.isEmpty()) continue;

            if (stack.getItem() == Items.OBSIDIAN) {
                obsidian += stack.getCount();
            } else if (stack.getItem() == Items.ENDER_CHEST) {
                echests += stack.getCount();
            }
        }

        int totalBlocks = obsidian + (echests * 8);

        // 7 Blocks pro Distanz-Einheit
        return totalBlocks / 7;
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
