/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.modules;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.*;

class HighwayBuilderTHMTest {
    @Test
    void safetyEnclosureCoversAllFourSidesAtFeetAndHeadWithoutTouchingFloor() {
        BlockPos anchor = new BlockPos(-17, 119, 23);
        var targets = HighwayBuilderTHM.tpsSafetyTargets(anchor);
        assertEquals(8, targets.size());
        assertEquals(8, new HashSet<>(targets).size());
        for (BlockPos target : targets) {
            assertEquals(1, Math.abs(target.getX() - anchor.getX()) + Math.abs(target.getZ() - anchor.getZ()));
            assertTrue(target.getY() == anchor.getY() || target.getY() == anchor.getY() + 1);
        }
        assertFalse(targets.contains(anchor));
        assertFalse(targets.contains(anchor.below()));
    }
}
