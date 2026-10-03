/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.utils;

import static meteordevelopment.meteorclient.MeteorClient.mc;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Direction-aware, AABB-based reach checks — the same measure the server uses.
 *
 * <p>Vanilla's {@code Entity#canInteractWithBlockAt} measures from the eyes to the <b>nearest point
 * of the block's full cube</b>, not to the block's centre and not from the player's feet. Measuring
 * to the centre is up to {@code sqrt(3)/2 ≈ 0.87} blocks pessimistic, which is why a block you can
 * mine by hand at reach 5.2 got rejected by a module configured for exactly 5.2.
 *
 * <p>The block's real {@code VoxelShape} is deliberately ignored — the server checks the full cube.
 */
public final class RangeUtils {
    private RangeUtils() {}

    /** Nearest point on {@code pos}'s full cube to {@code from}. */
    public static Vec3 nearestPoint(BlockPos pos, Vec3 from) {
        return new Vec3(
            Mth.clamp(from.x, pos.getX(), pos.getX() + 1.0),
            Mth.clamp(from.y, pos.getY(), pos.getY() + 1.0),
            Mth.clamp(from.z, pos.getZ(), pos.getZ() + 1.0)
        );
    }

    /** Nearest point on {@code pos}'s full cube to the player's eyes — the spot to aim/click at. */
    public static Vec3 nearestPoint(BlockPos pos) {
        return nearestPoint(pos, mc.player.getEyePosition());
    }

    public static double squaredDistance(BlockPos pos, Vec3 from) {
        return from.distanceToSqr(nearestPoint(pos, from));
    }

    /** Eyes to the nearest point of the block. */
    public static double distanceTo(BlockPos pos) {
        if (mc.player == null) return Double.MAX_VALUE;
        return Math.sqrt(squaredDistance(pos, mc.player.getEyePosition()));
    }

    public static boolean isInRange(double reach, BlockPos pos, Vec3 from) {
        return squaredDistance(pos, from) <= reach * reach;
    }

    /** Whether {@code pos} is within {@code reach} of the player's eyes. */
    public static boolean isInRange(double reach, BlockPos pos) {
        if (mc.player == null) return false;
        return isInRange(reach, pos, mc.player.getEyePosition());
    }

    /**
     * Whether the server would accept an interaction with {@code pos} at all.
     *
     * <p>Strict {@code <}, unlike {@link #isInRange}: vanilla's own check is
     * {@code squaredMagnitude < range * range}, so a block sitting exactly on the limit is rejected
     * server-side. A user-configured range still uses {@code <=} — "range 5" should mine at 5.
     */
    public static boolean isInReach(BlockPos pos) {
        if (mc.player == null) return false;
        double reach = mc.player.blockInteractionRange();
        return squaredDistance(pos, mc.player.getEyePosition()) < reach * reach;
    }

    /**
     * The block face facing {@code from} — the one to click when breaking or interacting.
     *
     * <p>Picks the face whose outward side {@code from} is furthest beyond, i.e. the most face-on
     * (and so most visible) one. When {@code from} is inside the block every value is negative and
     * the same comparison yields the closest wall instead.
     */
    public static Direction nearestFace(BlockPos pos, Vec3 from) {
        Direction best = Direction.UP;
        double bestOutside = -Double.MAX_VALUE;

        for (Direction dir : Direction.values()) {
            double outside = outsideDistance(pos, dir, from);

            if (outside > bestOutside) {
                bestOutside = outside;
                best = dir;
            }
        }

        return best;
    }

    /** The block face facing the player's eyes. */
    public static Direction nearestFace(BlockPos pos) {
        if (mc.player == null) return Direction.UP;
        return nearestFace(pos, mc.player.getEyePosition());
    }

    /**
     * Like {@link #nearestFace}, but never picks a face that is buried behind a solid neighbour
     * when an exposed one is available — clicking a covered face is something a real player can
     * never do. Falls back to the plain geometric nearest face when the block is fully enclosed.
     */
    public static Direction nearestExposedFace(BlockPos pos, Vec3 from) {
        if (mc.level == null) return nearestFace(pos, from);

        Direction best = null;
        double bestOutside = -Double.MAX_VALUE;

        for (Direction dir : Direction.values()) {
            BlockPos neighbor = pos.relative(dir);
            if (mc.level.getBlockState(neighbor).isRedstoneConductor(mc.level, neighbor)) continue;

            double outside = outsideDistance(pos, dir, from);
            if (outside > bestOutside) {
                bestOutside = outside;
                best = dir;
            }
        }

        return best != null ? best : nearestFace(pos, from);
    }

    /** The exposed block face facing the player's eyes. */
    public static Direction nearestExposedFace(BlockPos pos) {
        if (mc.player == null) return Direction.UP;
        return nearestExposedFace(pos, mc.player.getEyePosition());
    }

    /**
     * Nearest point of one specific face of {@code pos} — an edge or corner of that face when
     * {@code from} is off to the side of it.
     *
     * <p>{@link #nearestPoint} and {@link #nearestFace} are solved independently, so on a corner
     * approach the nearest point can sit on an edge that is not part of the face being clicked.
     * Aim and hit vectors should use this instead, so the point actually lies on the clicked face.
     */
    public static Vec3 nearestPointOnFace(BlockPos pos, Direction face, Vec3 from) {
        Vec3 nearest = nearestPoint(pos, from);
        double plane = face.getAxis().choose(pos.getX(), pos.getY(), pos.getZ())
            + (face.getAxisDirection().getStep() > 0 ? 1.0 : 0.0);

        return switch (face.getAxis()) {
            case X -> new Vec3(plane, nearest.y, nearest.z);
            case Y -> new Vec3(nearest.x, plane, nearest.z);
            case Z -> new Vec3(nearest.x, nearest.y, plane);
        };
    }

    /** Nearest point of {@code face} to the player's eyes. */
    public static Vec3 nearestPointOnFace(BlockPos pos, Direction face) {
        if (mc.player == null) return pos.getCenter();
        return nearestPointOnFace(pos, face, mc.player.getEyePosition());
    }

    /** How far {@code from} lies beyond {@code dir}'s outward plane (negative when behind it). */
    private static double outsideDistance(BlockPos pos, Direction dir, Vec3 from) {
        double sign = dir.getAxisDirection().getStep();
        double plane = dir.getAxis().choose(pos.getX(), pos.getY(), pos.getZ()) + (sign > 0 ? 1.0 : 0.0);
        return (dir.getAxis().choose(from.x, from.y, from.z) - plane) * sign;
    }
}
