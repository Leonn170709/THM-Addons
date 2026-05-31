package xyz.thm.addon.utils;

import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.systems.modules.render.blockesp.ESPBlockData;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.world.Dir;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.NotNull;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/**
 * Preferred render utility for all THM addon rendering.
 * Use this instead of calling event.renderer directly.
 */
public class RenderUtilsTHM {
    private static final VertexConsumerProvider.Immediate vertex =
        VertexConsumerProvider.immediate(new BufferAllocator(2048));

    private RenderUtilsTHM() {}

    // =========================================================
    // Block sets — optimized, merges shared faces between adjacent blocks
    // =========================================================

    /**
     * Renders a set of blocks stored as packed longs (BlockPos.asLong).
     * Adjacent blocks in the set have their shared inner faces excluded for a cleaner hull look.
     */
    public static void renderBlockSet(Render3DEvent event, LongOpenHashSet set,
                                      Color sideColor, Color lineColor, ShapeMode shapeMode) {
        if (set.isEmpty()) return;
        var iter = set.longIterator();
        while (iter.hasNext()) {
            long encoded = iter.nextLong();
            int x = BlockPos.unpackLongX(encoded);
            int y = BlockPos.unpackLongY(encoded);
            int z = BlockPos.unpackLongZ(encoded);

            int excludeDir = 0;
            for (Direction side : Direction.values()) {
                if (set.contains(BlockPos.asLong(
                        x + side.getOffsetX(),
                        y + side.getOffsetY(),
                        z + side.getOffsetZ()))) {
                    excludeDir |= Dir.get(side);
                }
            }
            event.renderer.box(x, y, z, x + 1, y + 1, z + 1, sideColor, lineColor, shapeMode, excludeDir);
        }
    }

    // =========================================================
    // Single block
    // =========================================================

    public static void renderBlock(Render3DEvent event, BlockPos pos,
                                   Color sideColor, Color lineColor, ShapeMode shapeMode) {
        event.renderer.box(pos, sideColor, lineColor, shapeMode, 0);
    }

    /** Lines only, same color for both. */
    public static void renderBlock(Render3DEvent event, @NotNull BlockPos pos, Color color) {
        event.renderer.box(pos, color, color, ShapeMode.Lines, 0);
    }

    /** Filled sides only. */
    public static void renderBlockFilled(Render3DEvent event, BlockPos pos, Color color) {
        event.renderer.box(pos, color, color, ShapeMode.Sides, 0);
    }

    /** Lines only (alias for clarity). */
    public static void renderBlockOutline(Render3DEvent event, BlockPos pos, Color color) {
        event.renderer.box(pos, color, color, ShapeMode.Lines, 0);
    }

    // =========================================================
    // Arbitrary boxes
    // =========================================================

    public static void renderBox(Render3DEvent event,
                                 double x1, double y1, double z1,
                                 double x2, double y2, double z2,
                                 Color sideColor, Color lineColor, ShapeMode shapeMode) {
        event.renderer.box(x1, y1, z1, x2, y2, z2, sideColor, lineColor, shapeMode, 0);
    }

    public static void renderBox(Render3DEvent event, Box box,
                                 Color sideColor, Color lineColor, ShapeMode shapeMode) {
        event.renderer.box(box, sideColor, lineColor, shapeMode, 0);
    }

    // =========================================================
    // Entity box — lerped position + RenderMode support
    // =========================================================

    /**
     * Renders an entity's bounding box with position interpolation and optional RenderMode effects.
     * Pass {@code lastInteractMs = System.currentTimeMillis()} at the moment of interaction,
     * and {@code durationMs} as the effect lifetime.
     */
    public static void renderEntity(Render3DEvent event, Entity entity,
                                    Color sideColor, Color lineColor, ShapeMode shapeMode,
                                    RenderMode mode, long lastInteractMs, int durationMs) {
        double x = MathHelper.lerp(event.tickDelta, entity.lastX, entity.getX());
        double y = MathHelper.lerp(event.tickDelta, entity.lastY, entity.getY());
        double z = MathHelper.lerp(event.tickDelta, entity.lastZ, entity.getZ());
        Box box = entity.getBoundingBox()
            .offset(-entity.getX(), -entity.getY(), -entity.getZ())
            .offset(x, y, z);

        if (mode == RenderMode.Shrink) {
            double expansion = 0.1 * (1.0 - MathHelper.clamp(
                (System.currentTimeMillis() - lastInteractMs) / (double) durationMs, 0.0, 1.0));
            box = box.expand(expansion);
        }

        event.renderer.box(box,
            applyRenderMode(sideColor, mode, lastInteractMs, durationMs),
            applyRenderMode(lineColor, mode, lastInteractMs, durationMs),
            shapeMode, 0);
    }

    // =========================================================
    // Lines / tracers
    // =========================================================

    public static void renderLine(Render3DEvent event,
                                  double x1, double y1, double z1,
                                  double x2, double y2, double z2, Color color) {
        event.renderer.line(x1, y1, z1, x2, y2, z2, color);
    }

    /** Tracer from screen centre to the centre of a block. */
    public static void renderTracerTo(Render3DEvent event, @NotNull BlockPos pos, Color color) {
        Vec3d c = pos.toCenterPos();
        Vec3d src = meteordevelopment.meteorclient.utils.render.RenderUtils.center;
        event.renderer.line(src.x, src.y, src.z, c.x, c.y, c.z, color);
    }

    /** Tracer from screen centre to an arbitrary world position. */
    public static void renderTracerToVec(Render3DEvent event, Vec3d target, Color color) {
        Vec3d src = meteordevelopment.meteorclient.utils.render.RenderUtils.center;
        event.renderer.line(src.x, src.y, src.z, target.x, target.y, target.z, color);
    }

    // =========================================================
    // ESP / visibility helpers
    // =========================================================

    public static boolean shouldRenderBox(ESPBlockData esp) {
        return switch (esp.shapeMode) {
            case Both  -> esp.lineColor.a > 0 || esp.sideColor.a > 0;
            case Lines -> esp.lineColor.a > 0;
            case Sides -> esp.sideColor.a > 0;
        };
    }

    public static boolean shouldRenderTracer(ESPBlockData esp) {
        return esp.tracer && esp.tracerColor.a > 0;
    }

    /** Returns true if anything would actually be drawn given these settings. */
    public static boolean isVisible(Color sideColor, Color lineColor, ShapeMode shapeMode) {
        return switch (shapeMode) {
            case Both  -> lineColor.a > 0 || sideColor.a > 0;
            case Lines -> lineColor.a > 0;
            case Sides -> sideColor.a > 0;
        };
    }

    // =========================================================
    // Color helpers
    // =========================================================

    /**
     * Modifies a color's alpha according to a RenderMode effect.
     * For Shrink the box itself must be expanded — see renderEntity.
     */
    public static Color applyRenderMode(Color base, RenderMode mode, long lastInteractMs, int durationMs) {
        return switch (mode) {
            case Solid, Shrink -> base;
            case Fade -> {
                float t = 1f - MathHelper.clamp(
                    (System.currentTimeMillis() - lastInteractMs) / (float) durationMs, 0f, 1f);
                yield withAlpha(base, Math.max(0, (int) (base.a * t)));
            }
            case Pulse -> {
                double pulse = Math.sin(System.currentTimeMillis() / 200.0) * 0.5 + 0.5;
                yield withAlpha(base, Math.max(10, (int) (base.a * pulse)));
            }
        };
    }

    /** Returns a new Color with the same RGB but a different alpha. */
    public static Color withAlpha(Color base, int alpha) {
        return new Color(base.r, base.g, base.b, alpha);
    }

    /** Linearly interpolates between two colors component-wise. */
    public static Color lerp(Color a, Color b, float t) {
        return new Color(
            (int) MathHelper.lerp(t, a.r, b.r),
            (int) MathHelper.lerp(t, a.g, b.g),
            (int) MathHelper.lerp(t, a.b, b.b),
            (int) MathHelper.lerp(t, a.a, b.a)
        );
    }

    // =========================================================
    // 2D text (drawn into 3D world via matrix stack)
    // =========================================================

    public static void text(String text, MatrixStack stack, float x, float y, int color) {
        mc.textRenderer.draw(text, x, y, color, false,
            stack.peek().getPositionMatrix(), vertex,
            TextRenderer.TextLayerType.NORMAL, 0, 15728880);
        vertex.draw();
    }

    // =========================================================
    // Ticking block (fade/shrink animation on a single block)
    // =========================================================

    /** Thin wrapper around Meteor's RenderUtils.renderTickingBlock — keeps all render calls in one place. */
    public static void renderTickingBlock(BlockPos pos, Color sideColor, Color lineColor,
                                          ShapeMode shapeMode, int excludeDir,
                                          int duration, boolean fade, boolean shrink) {
        meteordevelopment.meteorclient.utils.render.RenderUtils.renderTickingBlock(
            pos, sideColor, lineColor, shapeMode, excludeDir, duration, fade, shrink);
    }

    // =========================================================
    // TimedBlockSet — face-connected placed-block trail with auto-fade
    // =========================================================

    /**
     * Holds a set of block positions that expire over time and render with face exclusion.
     * Call {@link #add} when a block is placed, {@link #render} in a Render3DEvent handler.
     * No tick() needed — expiry is handled lazily inside render().
     */
    public static class TimedBlockSet {
        private final Long2LongOpenHashMap expiry = new Long2LongOpenHashMap();
        private final LongOpenHashSet keySet = new LongOpenHashSet();
        private final int durationMs;

        public TimedBlockSet(int durationMs) {
            this.durationMs = durationMs;
        }

        public void add(BlockPos pos) {
            long key = BlockPos.asLong(pos.getX(), pos.getY(), pos.getZ());
            expiry.put(key, System.currentTimeMillis() + durationMs);
            keySet.add(key);
        }

        /**
         * Renders the set with face exclusion.
         * Expired blocks are removed here; alpha is faded based on the oldest remaining block.
         */
        public void render(Render3DEvent event, Color sideColor, Color lineColor, ShapeMode shapeMode) {
            if (keySet.isEmpty()) return;
            long now = System.currentTimeMillis();

            // Expire old entries, track oldest remaining for fade
            long minExpiry = Long.MAX_VALUE;
            LongIterator iter = keySet.longIterator();
            while (iter.hasNext()) {
                long key = iter.nextLong();
                long exp = expiry.get(key);
                if (now >= exp) {
                    iter.remove();
                    expiry.remove(key);
                } else if (exp < minExpiry) {
                    minExpiry = exp;
                }
            }

            if (keySet.isEmpty()) return;

            float fade = MathHelper.clamp((minExpiry - now) / (float) durationMs, 0f, 1f);
            renderBlockSet(event, keySet,
                withAlpha(sideColor, Math.max(1, (int) (sideColor.a * fade))),
                withAlpha(lineColor, Math.max(1, (int) (lineColor.a * fade))),
                shapeMode);
        }

        public void clear() {
            expiry.clear();
            keySet.clear();
        }

        public boolean isEmpty() { return keySet.isEmpty(); }
    }

    // =========================================================
    // RenderMode
    // =========================================================

    public enum RenderMode {
        Solid,
        Fade,
        Pulse,
        Shrink
    }
}
