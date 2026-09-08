/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.utils;

import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.Renderer2D;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.systems.modules.render.blockesp.ESPBlockData;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.world.Dir;
import net.minecraft.block.BlockState;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.*;
import net.minecraft.util.shape.VoxelShape;
import org.jetbrains.annotations.NotNull;

import java.util.function.Predicate;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/**
 * Preferred render utility for all THM addon rendering.
 * Use this instead of calling event.renderer directly.
 */
public class RenderUtilsTHM {
    // Direction.values() hands out a fresh array on every call.
    private static final Direction[] DIRECTIONS = Direction.values();

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
            for (Direction side : DIRECTIONS) {
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

    // Scratch state for the helpers below; world rendering is single threaded, so sharing is fine.
    private static final LongOpenHashSet SCRATCH_SET = new LongOpenHashSet();
    private static final Color SCRATCH_SIDE = new Color();
    private static final Color SCRATCH_LINE = new Color();
    private static final BlockPos.Mutable SCRATCH_POS = new BlockPos.Mutable();

    /** Renders block positions as one hull with the shared inner faces skipped. Prefer this over a box-per-position loop. */
    public static void renderBlocks(Render3DEvent event, Iterable<BlockPos> positions,
                                    Color sideColor, Color lineColor, ShapeMode shapeMode) {
        renderBlocks(event, positions, null, sideColor, lineColor, shapeMode);
    }

    /** Same, skipping positions the filter rejects. */
    public static void renderBlocks(Render3DEvent event, Iterable<BlockPos> positions, Predicate<BlockPos> filter,
                                    Color sideColor, Color lineColor, ShapeMode shapeMode) {
        SCRATCH_SET.clear();
        for (BlockPos pos : positions) {
            if (filter == null || filter.test(pos)) SCRATCH_SET.add(pos.asLong());
        }
        renderBlockSet(event, SCRATCH_SET, sideColor, lineColor, shapeMode);
    }

    /** Renders one block with both colors faded to {@code progress} (1 = full alpha), without allocating a color per call. */
    public static void renderBlockFaded(Render3DEvent event, BlockPos pos, Color sideColor, Color lineColor,
                                        ShapeMode shapeMode, double progress) {
        event.renderer.box(pos, fadeInto(SCRATCH_SIDE, sideColor, progress), fadeInto(SCRATCH_LINE, lineColor, progress), shapeMode, 0);
    }

    private static Color fadeInto(Color dst, Color src, double progress) {
        return dst.set(src.r, src.g, src.b, (int) (src.a * progress));
    }

    /**
     * 2D background quad with the color's alpha scaled by {@code alphaFactor}, without allocating a color per call.
     * ponytail: still one draw call per quad; batching needs the caller's pass split into measure and draw halves.
     */
    public static void renderBackground2D(double x, double y, double width, double height, Color color, double alphaFactor) {
        Renderer2D.COLOR.begin();
        Renderer2D.COLOR.quad(x, y, width, height, fadeInto(SCRATCH_SIDE, color, alphaFactor));
        Renderer2D.COLOR.render();
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
    // Block shapes — renders a block's actual outline shape (e.g. a sign's post + plank,
    // not a full cube) instead of approximating every block as a unit cube.
    // =========================================================

    /** Renders every box of the block's actual shape (e.g. a sign renders its post + plank, not a full cube). */
    public static void renderBlockShape(Render3DEvent event, BlockPos pos, BlockState state,
                                        Color sideColor, Color lineColor, ShapeMode shapeMode) {
        renderBlockShapeScaled(event, pos, state, 1, sideColor, lineColor, shapeMode);
    }

    /** Like {@link #renderBlockShape}, but each shape box is scaled toward its own center by {@code scale} (1.0 = full size, 0.0 = a point). Useful for mining-progress shrink effects on non-cube blocks. */
    public static void renderBlockShapeScaled(Render3DEvent event, BlockPos pos, BlockState state, double scale,
                                              Color sideColor, Color lineColor, ShapeMode shapeMode) {
        VoxelShape shape = state.getOutlineShape(mc.world, pos);
        if (shape.isEmpty()) {
            renderScaledBox(event, pos, 0, 0, 0, 1, 1, 1, scale, sideColor, lineColor, shapeMode);
            return;
        }

        shape.forEachBox((minX, minY, minZ, maxX, maxY, maxZ) ->
            renderScaledBox(event, pos, minX, minY, minZ, maxX, maxY, maxZ, scale, sideColor, lineColor, shapeMode));
    }

    /** Block-local box, offset to world and scaled toward its own centre (1 = full size). */
    private static void renderScaledBox(Render3DEvent event, BlockPos pos,
                                        double minX, double minY, double minZ, double maxX, double maxY, double maxZ,
                                        double scale, Color sideColor, Color lineColor, ShapeMode shapeMode) {
        double centerX = pos.getX() + (minX + maxX) / 2.0;
        double centerY = pos.getY() + (minY + maxY) / 2.0;
        double centerZ = pos.getZ() + (minZ + maxZ) / 2.0;
        double halfX = (maxX - minX) / 2.0 * scale;
        double halfY = (maxY - minY) / 2.0 * scale;
        double halfZ = (maxZ - minZ) / 2.0 * scale;

        event.renderer.box(centerX - halfX, centerY - halfY, centerZ - halfZ,
            centerX + halfX, centerY + halfY, centerZ + halfZ, sideColor, lineColor, shapeMode, 0);
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
        double dx = MathHelper.lerp(event.tickDelta, entity.lastX, entity.getX()) - entity.getX();
        double dy = MathHelper.lerp(event.tickDelta, entity.lastY, entity.getY()) - entity.getY();
        double dz = MathHelper.lerp(event.tickDelta, entity.lastZ, entity.getZ()) - entity.getZ();
        Box box = entity.getBoundingBox();

        double grow = 0;
        if (mode == RenderMode.Shrink) {
            grow = 0.1 * (1.0 - MathHelper.clamp(
                (System.currentTimeMillis() - lastInteractMs) / (double) durationMs, 0.0, 1.0));
        }

        event.renderer.box(
            box.minX + dx - grow, box.minY + dy - grow, box.minZ + dz - grow,
            box.maxX + dx + grow, box.maxY + dy + grow, box.maxZ + dz + grow,
            applyRenderModeInto(SCRATCH_SIDE, sideColor, mode, lastInteractMs, durationMs),
            applyRenderModeInto(SCRATCH_LINE, lineColor, mode, lastInteractMs, durationMs),
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
        Vec3d src = meteordevelopment.meteorclient.utils.render.RenderUtils.center;
        event.renderer.line(src.x, src.y, src.z, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, color);
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
        return applyRenderModeInto(new Color(), base, mode, lastInteractMs, durationMs);
    }

    /** Same, writing into {@code dst} so a render loop doesn't allocate a color per call. */
    private static Color applyRenderModeInto(Color dst, Color base, RenderMode mode, long lastInteractMs, int durationMs) {
        return switch (mode) {
            case Solid, Shrink -> base;
            case Fade -> {
                float t = 1f - MathHelper.clamp(
                    (System.currentTimeMillis() - lastInteractMs) / (float) durationMs, 0f, 1f);
                yield dst.set(base.r, base.g, base.b, Math.max(0, (int) (base.a * t)));
            }
            case Pulse -> {
                double pulse = Math.sin(System.currentTimeMillis() / 200.0) * 0.5 + 0.5;
                yield dst.set(base.r, base.g, base.b, Math.max(10, (int) (base.a * pulse)));
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
    // Persistent block set — renders until blocks are filled by the world
    // =========================================================

    /**
     * Renders a set of pending block positions with face exclusion.
     * Positions where the world already has a non-replaceable block are removed from the set —
     * so blocks stay visible until the server confirms placement, then disappear automatically.
     */
    public static void renderAndPruneBlockSet(Render3DEvent event, LongOpenHashSet set,
                                              Color sideColor, Color lineColor, ShapeMode shapeMode) {
        if (set.isEmpty() || mc.world == null) return;
        LongIterator iter = set.longIterator();
        while (iter.hasNext()) {
            long key = iter.nextLong();
            SCRATCH_POS.set(BlockPos.unpackLongX(key), BlockPos.unpackLongY(key), BlockPos.unpackLongZ(key));
            if (!mc.world.getBlockState(SCRATCH_POS).isReplaceable()) iter.remove();
        }
        renderBlockSet(event, set, sideColor, lineColor, shapeMode);
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
                SCRATCH_SIDE.set(sideColor.r, sideColor.g, sideColor.b, Math.max(1, (int) (sideColor.a * fade))),
                SCRATCH_LINE.set(lineColor.r, lineColor.g, lineColor.b, Math.max(1, (int) (lineColor.a * fade))),
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
