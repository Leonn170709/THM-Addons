/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.modules;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SurroundTest {
    @Test void packetAndTickPlacementsShareTheSameBudget() {
        var budget = new Surround.PlacementBudget();
        assertTrue(budget.reserve(2, 0));
        assertTrue(budget.dispatch(2, 0));
        assertTrue(budget.reserve(2, 0));
        assertFalse(budget.reserve(2, 0));
        assertTrue(budget.dispatch(2, 0));
        assertFalse(budget.reserve(2, 0));
        budget.advanceTick();
        assertTrue(budget.reserve(2, 0));
        assertTrue(budget.dispatch(2, 0));
    }

    @Test void delayedRotationCannotBypassThePlaceDelay() {
        var budget = new Surround.PlacementBudget();
        assertTrue(budget.reserve(3, 2));
        assertTrue(budget.reserve(3, 2));
        assertTrue(budget.dispatch(3, 2));
        budget.advanceTick();
        assertFalse(budget.dispatch(3, 2));
        assertFalse(budget.reserve(3, 2));
        budget.advanceTick();
        assertFalse(budget.reserve(3, 2));
        budget.advanceTick();
        assertTrue(budget.reserve(3, 2));
        budget.cancel();
        assertTrue(budget.reserve(1, 2));
        assertTrue(budget.dispatch(1, 2));
        assertFalse(budget.reserve(1, 2));
    }

    @Test void footprintCoversEdgesAndNegativeCoordinatesWithoutPlacingInsideThePlayer() {
        BlockPos base = new BlockPos(-1, 64, -1);
        AABB body = new AABB(-.3, 64, -.3, .3, 65.8, .3);
        Set<BlockPos> feet = Surround.footprint(body, base, true);
        assertEquals(Set.of(base, base.east(), base.south(), base.east().south()), feet);
        assertEquals(Set.of(base), Surround.footprint(body, base, false));
        Set<BlockPos> targets = Surround.plan(feet, true, true, false, false);
        for (BlockPos pos : feet) {
            assertFalse(targets.contains(pos));
            assertFalse(targets.contains(pos.above()));
            assertTrue(targets.contains(pos.above(2)));
        }
        assertEquals(1, Surround.footprint(new AABB(0, 64, 0, 1, 65.8, 1), new BlockPos(0, 64, 0), true).size());
    }

    @Test void roofSupportsFormAPlaceableChainOutsideThePlayer() {
        Set<BlockPos> feet = Surround.footprint(new AABB(-.3, 64, -.3, .3, 65.8, .3), new BlockPos(-1, 64, -1), true);
        Set<BlockPos> plan = Surround.plan(feet, false, true, false, false);
        Set<BlockPos> built = new HashSet<>();
        for (BlockPos pos : plan) {
            if (pos.getY() == 64) {
                built.add(pos);
                continue;
            }
            assertTrue(java.util.Arrays.stream(Direction.values()).anyMatch(side -> built.contains(pos.relative(side))),
                "A roof or support must have an earlier neighbor: " + pos);
            built.add(pos);
        }
        Set<BlockPos> airplace = Surround.plan(feet, false, true, true, false);
        assertEquals(12, airplace.size());
        assertEquals(14, plan.size());
    }
}
