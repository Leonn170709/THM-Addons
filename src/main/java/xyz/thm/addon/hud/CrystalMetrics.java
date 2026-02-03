package xyz.thm.addon.hud;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import xyz.thm.addon.THMAddon;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class CrystalMetrics extends HudElement {
    public static final HudElementInfo<CrystalMetrics> INFO = new HudElementInfo<>(THMAddon.HUD_GROUP, "crystal-metrics", "ThunderHack Style Monitor.", CrystalMetrics::new);

    private final MinecraftClient mc = MinecraftClient.getInstance();
    private static Field entityIdField;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgVisuals = settings.createGroup("Visuals");

    private final Setting<Double> scale = sgGeneral.add(new DoubleSetting.Builder().name("scale").description("Vertical scale.").defaultValue(3.0).min(1.0).sliderMax(15.0).build());
    private final Setting<Integer> speed = sgGeneral.add(new IntSetting.Builder().name("update-speed").description("Graph updates per second.").defaultValue(20).min(1).sliderMax(60).build());

    private final Setting<Double> widthSetting = sgVisuals.add(new DoubleSetting.Builder().name("width").description("Width.").defaultValue(140).min(100).sliderMax(300).build());
    private final Setting<Double> heightSetting = sgVisuals.add(new DoubleSetting.Builder().name("height").description("Height.").defaultValue(50).min(30).sliderMax(150).build());
    private final Setting<SettingColor> colorLine = sgVisuals.add(new ColorSetting.Builder().name("line-color").description("Main line color.").defaultValue(new SettingColor(165, 65, 240, 255)).build());
    private final Setting<SettingColor> colorFill = sgVisuals.add(new ColorSetting.Builder().name("fill-color").description("Under graph fill.").defaultValue(new SettingColor(165, 65, 240, 0)).build());
    private final Setting<SettingColor> colorBg = sgVisuals.add(new ColorSetting.Builder().name("background").description("Panel background.").defaultValue(new SettingColor(10, 10, 10, 0)).build());
    private final Setting<SettingColor> colorText = sgVisuals.add(new ColorSetting.Builder().name("text-color").description("Value color.").defaultValue(new SettingColor(255, 255, 255, 255)).build());

    private final Setting<String> Text = sgGeneral.add(new StringSetting.Builder()
        .name("Hud-Text")
        .description("The text to display.")
        .defaultValue("Crystals per second")
        .build()
    );

    private final ArrayList<Long> rawTimestamps = new ArrayList<>();
    private final LinkedList<Double> graphPoints = new LinkedList<>();
    private long lastGraphUpdate = 0;
    private double currentCPA = 0;

    static {
        try {
            for (Field f : PlayerInteractEntityC2SPacket.class.getDeclaredFields()) {
                if (f.getType() == int.class) {
                    f.setAccessible(true);
                    entityIdField = f;
                    break;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public CrystalMetrics() {
        super(INFO);
        MeteorClient.EVENT_BUS.subscribe(this);
        for(int i=0; i<60; i++) graphPoints.add(0.0);
    }

    @EventHandler
    private void onPacketSend(PacketEvent.Send event) {
        if (mc.player == null || mc.world == null) return;
        boolean action = false;

        if (event.packet instanceof PlayerInteractBlockC2SPacket p) {
            if (mc.player.getStackInHand(p.getHand()).getItem() == Items.END_CRYSTAL) action = true;
        }
        else if (event.packet instanceof PlayerInteractItemC2SPacket p) {
            if (mc.player.getStackInHand(p.getHand()).getItem() == Items.END_CRYSTAL) action = true;
        }
        else if (event.packet instanceof PlayerInteractEntityC2SPacket p) {
            Entity t = null;
            if (entityIdField != null) {
                try { t = mc.world.getEntityById((int) entityIdField.get(p)); } catch (Exception ignored) {}
            }
            if (t instanceof EndCrystalEntity) action = true;
        }

        if (action) {
            synchronized (rawTimestamps) {
                rawTimestamps.add(System.currentTimeMillis());
            }
        }
    }

    @Override
    public void tick(HudRenderer renderer) {
        setSize(widthSetting.get(), heightSetting.get());

        long now = System.currentTimeMillis();

        synchronized (rawTimestamps) {
            rawTimestamps.removeIf(ts -> now - ts > 1000);
            currentCPA = rawTimestamps.size();
        }

        long delay = 1000 / Math.max(1, speed.get());

        if (now - lastGraphUpdate > delay) {
            graphPoints.add(currentCPA);
            if (graphPoints.size() > 60) graphPoints.removeFirst();
            lastGraphUpdate = now;
        }
    }

    @Override
    public void render(HudRenderer renderer) {
        double x = getX();
        double y = getY();
        double w = getWidth();
        double h = getHeight();

        renderer.quad(x, y, w, h, colorBg.get());

        String valStr = String.format("%.0f", currentCPA);
        String labelStr = " "+Text.get();

        double valW = renderer.textWidth(valStr);
        double labelW = renderer.textWidth(labelStr);

        double textX = x + 4;
        double textY = y + 4;

        renderer.text(valStr, textX, textY, colorText.get(), true);
        renderer.text(labelStr, textX + valW, textY, colorLine.get(), true);

        if (graphPoints.size() < 2) return;

        drawStableSpline(renderer, x, y + 14, w, h - 14);
    }

    private void drawStableSpline(HudRenderer r, double x, double y, double w, double h) {
        Double[] values = graphPoints.toArray(new Double[0]);
        int size = values.length;

        double maxVal = scale.get();
        for (double v : values) if (v > maxVal) maxVal = v;

        List<Point> points = new ArrayList<>();
        int samples = 4;

        for (int i = 0; i < size - 1; i++) {
            double p0 = (i > 0) ? values[i - 1] : values[i];
            double p1 = values[i];
            double p2 = values[i + 1];
            double p3 = (i + 2 < size) ? values[i + 2] : values[i + 1];

            for (int j = 0; j < samples; j++) {
                double t = (double) j / samples;

                double val = 0.5 * (
                        (2 * p1) +
                                (-p0 + p2) * t +
                                (2 * p0 - 5 * p1 + 4 * p2 - p3) * t * t +
                                (-p0 + 3 * p1 - 3 * p2 + p3) * t * t * t
                );

                val = Math.max(0, val);
                double normY = (val / maxVal) * (h - 2);

                double stepX = w / (double)((size - 1) * samples);
                double currentX = x + ((i * samples + j) * stepX);
                double currentY = (y + h) - normY;

                points.add(new Point(currentX, currentY));
            }
        }

        Color lineC = colorLine.get();
        Color fillC = colorFill.get();

        for (int i = 0; i < points.size(); i += 2) {
            Point p = points.get(i);
            if (p.x > x + w) break;
            r.line(p.x, p.y, p.x, y + h, fillC);
        }

        for (int i = 0; i < points.size() - 1; i++) {
            Point p1 = points.get(i);
            Point p2 = points.get(i+1);

            if (p1.x > x + w) break;

            r.line(p1.x, p1.y + 1, p2.x, p2.y + 1, new Color(lineC.r, lineC.g, lineC.b, 100));
            r.line(p1.x, p1.y, p2.x, p2.y, lineC);
        }
    }

    private static class Point {
        double x, y;
        Point(double x, double y) { this.x = x; this.y = y; }
    }
}
