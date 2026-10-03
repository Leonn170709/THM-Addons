/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static xyz.thm.addon.utils.HighwayOffhandPolicy.Target.*;

class HighwayOffhandPolicyTest {
    @Test
    void chestMiningKeepsOwnershipUntilRestockEnds() {
        assertEquals(EnderChest, HighwayOffhandPolicy.target(false, false, true, true));
        // A full-inventory recovery must not refill the offhand with obsidian.
        assertEquals(Preserve, HighwayOffhandPolicy.target(false, false, false, true));
        assertEquals(EnderChest, HighwayOffhandPolicy.target(false, false, true, true));
        assertEquals(BuildingBlock, HighwayOffhandPolicy.target(false, false, false, false));
    }

    @Test
    void enclosureTemporarilyOwnsTheOffhandDuringRestock() {
        assertEquals(Enclosure, HighwayOffhandPolicy.target(false, true, false, true));
        assertEquals(Preserve, HighwayOffhandPolicy.target(false, false, false, true));
    }

    @Test
    void dangerAlwaysTakesPriorityAndMiningResumesAfterwards() {
        assertEquals(Totem, HighwayOffhandPolicy.target(true, true, true, true));
        assertEquals(EnderChest, HighwayOffhandPolicy.target(false, false, true, true));
    }
}
