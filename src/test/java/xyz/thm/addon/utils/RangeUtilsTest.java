/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.utils;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RangeUtilsTest {
    private static final Vec3d EYE = new Vec3d(0.5, 1.62, 0.5);

    @Test
    void nearestPointClampsToCube() {
        Vec3d p = RangeUtils.nearestPoint(new BlockPos(3, 0, 0), EYE);
        assertEquals(new Vec3d(3.0, 1.0, 0.5), p);
    }

    @Test
    void nearestPointInsideBlockIsEyeItself() {
        assertEquals(EYE, RangeUtils.nearestPoint(new BlockPos(0, 1, 0), EYE));
    }

    @Test
    void edgeCountsNotCenter() {
        // Block centre is 5.0 away horizontally, its near face 4.5: nuker-style reach accepts it at 4.5.
        BlockPos pos = new BlockPos(5, 1, 0);
        assertTrue(RangeUtils.isInRange(4.5, pos, EYE));
        assertFalse(RangeUtils.isInRange(4.49, pos, EYE));
    }

    @Test
    void rangeIsInclusive() {
        BlockPos pos = new BlockPos(4, 1, 0); // near face at x=4, eye at x=0.5 -> 3.5
        assertEquals(3.5 * 3.5, RangeUtils.squaredDistance(pos, EYE), 1e-9);
        assertTrue(RangeUtils.isInRange(3.5, pos, EYE));
    }

    @Test
    void diagonalUsesNearestCorner() {
        BlockPos pos = new BlockPos(3, 1, 3); // corner (3, *, 3): dx = dz = 2.5
        assertEquals(2.5 * 2.5 * 2, RangeUtils.squaredDistance(pos, EYE), 1e-9);
    }

    @Test
    void floorBlockAddsVerticalOffset() {
        BlockPos floor = new BlockPos(0, -1, 0); // top face y=0, eye at 1.62
        assertEquals(1.62 * 1.62, RangeUtils.squaredDistance(floor, EYE), 1e-9);
    }
}
