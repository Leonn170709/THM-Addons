//Thank you https://github.com/1exmc that you have alr added support for Ring and diamond Highways

package xyz.thm.addon.modules;

import it.unimi.dsi.fastutil.ints.IntList;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.Dimension;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.math.Vec3d;
import xyz.thm.addon.THMAddon;

public class AxisViewer extends Module {
    private final SettingGroup sgOverworld = settings.createGroup("Overworld");
    private final SettingGroup sgNether = settings.createGroup("Nether");
    private final SettingGroup sgEnd = settings.createGroup("End");

    // Overworld

    private final Setting<AxisType> overworldAxisTypes = sgOverworld.add(new EnumSetting.Builder<AxisType>()
        .name("render")
        .description("Which axes to display.")
        .defaultValue(AxisType.Both)
        .build()
    );

    private final Setting<Integer> overworldY = sgOverworld.add(new IntSetting.Builder()
        .name("height")
        .description("Y position of the line.")
        .defaultValue(63)
        .sliderMin(-64)
        .sliderMax(319)
        .visible(() -> overworldAxisTypes.get() != AxisType.None)
        .build()
    );

    private final Setting<SettingColor> overworldColor = sgOverworld.add(new ColorSetting.Builder()
        .name("color")
        .description("The line color.")
        .defaultValue(new SettingColor(25, 25, 225, 255))
        .visible(() -> overworldAxisTypes.get() != AxisType.None)
        .build()
    );

    // Nether

    private final Setting<Boolean> netherCardinal = sgNether.add(new BoolSetting.Builder()
        .name("Render Cardinal Highway")
        .description("Draw cardinal highways")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> netherDiagonal = sgNether.add(new BoolSetting.Builder()
        .name("Render Diagonal Highway")
        .description("Draw diagonal highways")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> netherRing = sgNether.add(new BoolSetting.Builder()
        .name("Render Ring Highway")
        .description("Draw ring highways")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> netherDiamond = sgNether.add(new BoolSetting.Builder()
        .name("Render Diamond Highway")
        .description("Draw diamond highways")
        .defaultValue(false)
        .build()
    );

   // private final Setting<Integer> netherY = sgNether.add(new IntSetting.Builder()
   //     .name("height")
   //     .description("Y position of the line.")
   //     .defaultValue(120)
   //     .sliderMin(0)
   //     .sliderMax(255)
   //     .build()
   // );

    private final Setting<SettingColor> netherColor = sgNether.add(new ColorSetting.Builder()
        .name("color")
        .description("The line color.")
        .defaultValue(new SettingColor(225, 25, 25, 255))
        .build()
    );

    private final Setting<Boolean> highwayCenterMode = sgNether.add(new BoolSetting.Builder()
        .name("Draw True Center: ")
        .description("This will move the axis line to the center of the middle block instead of the block edge.")
        .defaultValue(false)
        .build()
    );

    // End

    private final Setting<AxisType> endAxisTypes = sgEnd.add(new EnumSetting.Builder<AxisType>()
        .name("render")
        .description("Which axes to display.")
        .defaultValue(AxisType.Both)
        .build()
    );

    private final Setting<Integer> endY = sgEnd.add(new IntSetting.Builder()
        .name("height")
        .description("Y position of the line.")
        .defaultValue(64)
        .sliderMin(0)
        .sliderMax(255)
        .visible(() -> endAxisTypes.get() != AxisType.None)
        .build()
    );

    private final Setting<SettingColor> endColor = sgEnd.add(new ColorSetting.Builder()
        .name("color")
        .description("The line color.")
        .defaultValue(new SettingColor(225, 25, 25, 255))
        .visible(() -> endAxisTypes.get() != AxisType.None)
        .build()
    );

    public AxisViewer() {
        super(THMAddon.CATEGORY, "axis-viewer", "Displays world axes.");
    }

    private static final IntList RING_ROADS = IntList.of(
        200,
        500,
        750,
        1000,
        1500,
        2000,
        2500,
        5000,
        7500,
        10000,
        15000,
        20000,
        25000,
        50000,
        55000,
        62500,
        75000,
        100000,
        125000,
        250000,
        500000,
        750000,
        1000000,
        1250000,
        1568852,
        1875000,
        2500000,
        3750000
    );

    private static final IntList DIAMONDS = IntList.of(
        1000,
        2000,
        2500,
        5000,
        25000,
        50000,
        125000,
        250000,
        500000,
        3750000
    );

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (mc.options.hudHidden) return;

        AxisType axisType;
        int y;
        Color lineColor;
        boolean netherCardinalLocal = false;
        boolean netherDiagonalLocal = false;
        boolean netherRingLocal = false;
        boolean netherDiamondLocal = false;

        switch (PlayerUtils.getDimension()) {
            case Overworld -> {
                axisType = overworldAxisTypes.get();
                y = overworldY.get();
                lineColor = overworldColor.get();
            }
            case Nether -> {
                axisType = AxisType.None;
                netherCardinalLocal = netherCardinal.get();
                netherDiagonalLocal = netherDiagonal.get();
                netherRingLocal = netherRing.get();
                netherDiamondLocal = netherDiamond.get();
                y = 120;
                lineColor = netherColor.get();
            }
            case End -> {
                axisType = endAxisTypes.get();
                y = endY.get();
                lineColor = endColor.get();
            }
            default -> throw new IllegalStateException("Unexpected value: " + PlayerUtils.getDimension());
        }

        if (axisType == AxisType.None && PlayerUtils.getDimension() != Dimension.Nether) return;

        double renderY = y;

        double centerOffset = highwayCenterMode.get() ? 0.5 : 0.0;

        // Render cardinal lines
        if (axisType.cardinals() || netherCardinalLocal) {
            // X axis
            drawSegmentedLine(event,
                new Vec3d(-30_000_000, renderY, centerOffset),
                new Vec3d( 30_000_000, renderY, centerOffset),
                lineColor
            );

            // Z axis
            drawSegmentedLine(event,
                new Vec3d(centerOffset, renderY, -30_000_000),
                new Vec3d(centerOffset, renderY,  30_000_000),
                lineColor
            );
        }

        if (axisType.diagonals() || netherDiagonalLocal) {
            // Diagonal 1: x = z
            drawSegmentedLine(event,
                new Vec3d(-30_000_000 + centerOffset, renderY, -30_000_000 + centerOffset),
                new Vec3d( 30_000_000 + centerOffset, renderY,  30_000_000 + centerOffset),
                lineColor
            );

            // Diagonal 2:
            if (!highwayCenterMode.get()) {
                drawSegmentedLine(event,
                    new Vec3d(-30_000_000, renderY,  30_000_000),
                    new Vec3d( 30_000_000, renderY, -30_000_000),
                    lineColor
                );
            } else {
                drawSegmentedLine(event,
                    new Vec3d(-30_000_000, renderY,  30_000_000 + 1),
                    new Vec3d( 30_000_000, renderY, -30_000_000 + 1),
                    lineColor
                );
            }
        }

        // Render ring lines
        if (PlayerUtils.getDimension() == Dimension.Nether && netherRingLocal) {
            for (int r : RING_ROADS) {
                drawRing(event, renderY, r, centerOffset, lineColor);
            }
        }

        // Render diamond lines
        if (PlayerUtils.getDimension() == Dimension.Nether && netherDiamondLocal) {
            for (int d : DIAMONDS) {
                drawDiamond(event, renderY, d, centerOffset, lineColor);
            }
        }
    }

    private void drawRing(Render3DEvent event, double y, double r, double centerOffset, Color color) {
        double left   = -r + centerOffset;
        double right  =  r + centerOffset;
        double bottom = -r + centerOffset;
        double top    =  r + centerOffset;

        // bottom
        drawSegmentedLine(event, new Vec3d(left, y, bottom), new Vec3d(right, y, bottom), color);
        // top
        drawSegmentedLine(event, new Vec3d(left, y, top), new Vec3d(right, y, top), color);
        // left
        drawSegmentedLine(event, new Vec3d(left, y, bottom), new Vec3d(left,  y, top), color);
        // right
        drawSegmentedLine(event, new Vec3d(right,y, bottom), new Vec3d(right, y, top), color);
    }

    private void drawDiamond(Render3DEvent event, double y, int d, double centerOffset, Color color) {
        drawSegmentedLine(event,
            new Vec3d( d + centerOffset, y,  0 + centerOffset),
            new Vec3d( 0 + centerOffset, y,  d + centerOffset),
            color
        );

        drawSegmentedLine(event,
            new Vec3d( 0 + centerOffset, y,  d + centerOffset),
            new Vec3d(-d + centerOffset, y,  0 + centerOffset),
            color
        );

        drawSegmentedLine(event,
            new Vec3d(-d + centerOffset, y,  0 + centerOffset),
            new Vec3d( 0 + centerOffset, y, -d + centerOffset),
            color
        );

        drawSegmentedLine(event,
            new Vec3d( 0 + centerOffset, y, -d + centerOffset),
            new Vec3d( d + centerOffset, y,  0 + centerOffset),
            color
        );
    }

    private void drawSegmentedLine(Render3DEvent event, Vec3d start, Vec3d end, Color color) {
        double segmentLength = 100_000; // Length of each segment to avoid rendering issues
        Vec3d direction = end.subtract(start).normalize();
        double totalLength = start.distanceTo(end);
        int segments = (int) (totalLength / segmentLength);

        Vec3d currentStart = start;
        for (int i = 0; i < segments; i++) {
            Vec3d currentEnd = currentStart.add(direction.multiply(segmentLength));
            drawLine(event, currentStart, currentEnd, color);
            currentStart = currentEnd;
        }
        // Draw remaining part
        drawLine(event, currentStart, end, color);
    }

    private void drawLine(Render3DEvent event, Vec3d start, Vec3d end, Color color) {
        event.renderer.line(start.getX(), start.getY(), start.getZ(),
            end.getX(), end.getY(), end.getZ(), color);
    }

    public enum AxisType {
        Both,
        Cardinals,
        Diagonals,
        None;

        boolean cardinals() {
            return this == Both || this == Cardinals;
        }

        boolean diagonals() {
            return this == Both || this == Diagonals;
        }
    }
}

