/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.utils;

import org.junit.jupiter.api.Test;
import xyz.thm.addon.utils.PacketBudget.Plan;

import static org.junit.jupiter.api.Assertions.*;

class PacketBudgetTest {
    // HighwayBuilder's costs: mine = start+stop (+swing), place = use-on-block (+swing).
    private static Plan highway(int budget, int wantMine, int wantPlace) {
        return PacketBudget.split(budget, 3, 2, 1, wantMine, wantPlace);
    }

    @Test
    void fitsBothWhenThereIsRoom() {
        Plan plan = highway(23, 7, 2);
        assertEquals(7, plan.mine());
        assertEquals(2, plan.place());
        assertTrue(3 + plan.mine() * 2 + plan.place() <= 23);
    }

    @Test
    void placingGivesWayFirst() {
        // 23 - 3 overhead = 20; 7 mines cost 14, leaving 6 places even though 99 were asked for.
        Plan plan = highway(23, 7, 99);
        assertEquals(7, plan.mine());
        assertEquals(6, plan.place());
    }

    @Test
    void unlimitedPlacingIsCappedNotOverflowed() {
        Plan plan = highway(23, 7, Integer.MAX_VALUE);
        assertEquals(6, plan.place(), "packet build asks for MAX_VALUE");
    }

    @Test
    void miningIsTrimmedWhenTheBudgetIsTight() {
        Plan plan = highway(11, 7, 5);
        assertEquals(4, plan.mine());
        assertEquals(0, plan.place());
        assertTrue(3 + plan.mine() * 2 <= 11);
    }

    @Test
    void neverStallsMiningCompletely() {
        Plan plan = highway(4, 7, 5);
        assertEquals(1, plan.mine(), "one mine action always goes through");
        assertEquals(0, plan.place());
    }

    @Test
    void askingForNothingGetsNothing() {
        Plan plan = highway(23, 0, 0);
        assertEquals(0, plan.mine());
        assertEquals(0, plan.place());
    }

    @Test
    void placesEvenWhenNoMiningIsWanted() {
        assertEquals(20, highway(23, 0, 99).place());
    }

    @Test
    void swingPacketsHalveThePlaceCount() {
        // Same budget, but each action also sends a swing: mine 3, place 2.
        Plan plan = PacketBudget.split(23, 3, 3, 2, 7, 99);
        assertEquals(6, plan.mine());
        assertEquals(1, plan.place());
    }

    @Test
    void overheadAloneCanEatTheBudget() {
        Plan plan = PacketBudget.split(3, 3, 2, 1, 5, 5);
        assertEquals(1, plan.mine());
        assertEquals(0, plan.place());
    }

    @Test
    void negativeInputsAreClamped() {
        Plan plan = PacketBudget.split(-5, -2, 2, 1, -3, -3);
        assertEquals(0, plan.mine());
        assertEquals(0, plan.place());
    }
}
