/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.utils;

import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;
import xyz.thm.addon.utils.GhostBlockProbe.Probe;
import xyz.thm.addon.utils.GhostBlockProbe.Result;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class GhostBlockProbeTest {
    /** A 5-wide floor row plus two railings one block up at the edges. */
    private static List<BlockPos> highwayRow() {
        List<BlockPos> row = new ArrayList<>();
        for (int z = -2; z <= 2; z++) row.add(new BlockPos(10, 119, z));
        row.add(new BlockPos(10, 120, -3));
        row.add(new BlockPos(10, 120, 3));
        return row;
    }

    // ---- plan ----

    @Test
    void planCoversEveryTargetExactlyOnce() {
        List<BlockPos> targets = highwayRow();
        List<Probe> probes = GhostBlockProbe.plan(targets);

        Set<BlockPos> covered = new HashSet<>();
        for (Probe p : probes) {
            assertTrue(covered.add(p.pos()), "covered twice: " + p.pos());
            if (targets.contains(p.other())) assertTrue(covered.add(p.other()), "covered twice: " + p.other());
        }
        assertEquals(new HashSet<>(targets), covered);
    }

    @Test
    void planPairsAdjacentFloorBlocks() {
        // 5 floor blocks in a line pair up into 3 packets; the 2 railings touch nothing and get one each.
        assertEquals(5, GhostBlockProbe.plan(highwayRow()).size());
    }

    @Test
    void planUsesUpForLoneBlocks() {
        List<Probe> probes = GhostBlockProbe.plan(List.of(new BlockPos(0, 64, 0)));
        assertEquals(1, probes.size());
        assertEquals(net.minecraft.util.math.Direction.UP, probes.getFirst().side());
    }

    @Test
    void planIgnoresDuplicatesAndMutablePositions() {
        BlockPos.Mutable m = new BlockPos.Mutable(1, 2, 3);
        List<Probe> probes = GhostBlockProbe.plan(List.of(m, new BlockPos(1, 2, 3)));
        assertEquals(1, probes.size());
        m.set(9, 9, 9);
        assertEquals(new BlockPos(1, 2, 3), probes.getFirst().pos());
    }

    @Test
    void planOfNothingIsEmpty() {
        assertTrue(GhostBlockProbe.plan(List.of()).isEmpty());
    }

    // ---- verification ----

    @Test
    void verifiesWhenEveryBlockComesBackSolid() {
        GhostBlockProbe probe = new GhostBlockProbe(20);
        probe.start(4, highwayRow(), 100);
        assertTrue(probe.isProbing());
        assertFalse(probe.isVerified(4));

        for (BlockPos pos : highwayRow()) {
            assertEquals(Result.WAITING, probe.tick(101));
            probe.onBlockUpdate(pos.asLong(), true);
        }
        assertEquals(Result.VERIFIED, probe.tick(102));
        assertTrue(probe.isVerified(4));
        assertTrue(probe.isVerified(3), "a later row covers the earlier ones");
        assertFalse(probe.isVerified(5));
        assertEquals(Result.IDLE, probe.tick(103));
    }

    @Test
    void ghostWhenServerSaysAir() {
        GhostBlockProbe probe = new GhostBlockProbe(20);
        List<BlockPos> row = highwayRow();
        probe.start(4, row, 0);
        for (int i = 0; i < row.size(); i++) probe.onBlockUpdate(row.get(i).asLong(), i != 2);

        assertEquals(Result.GHOST, probe.tick(1));
        assertFalse(probe.isVerified(4), "a row with a ghost must not unlock movement");
        assertFalse(probe.isProbing());

        // After the re-place, a clean probe verifies it.
        probe.start(4, row, 5);
        for (BlockPos pos : row) probe.onBlockUpdate(pos.asLong(), true);
        assertEquals(Result.VERIFIED, probe.tick(6));
        assertTrue(probe.isVerified(4));
    }

    @Test
    void unrelatedUpdatesAreIgnored() {
        GhostBlockProbe probe = new GhostBlockProbe(20);
        probe.start(1, List.of(new BlockPos(0, 0, 0)), 0);
        probe.onBlockUpdate(new BlockPos(5, 5, 5).asLong(), false);
        assertEquals(Result.WAITING, probe.tick(1));
        probe.onBlockUpdate(new BlockPos(0, 0, 0).asLong(), true);
        assertEquals(Result.VERIFIED, probe.tick(2));
    }

    @Test
    void duplicateUpdateAfterAnswerIsIgnored() {
        GhostBlockProbe probe = new GhostBlockProbe(20);
        BlockPos pos = new BlockPos(0, 0, 0);
        probe.start(1, List.of(pos, new BlockPos(0, 0, 1)), 0);
        probe.onBlockUpdate(pos.asLong(), true);
        probe.onBlockUpdate(pos.asLong(), false); // late second update for an answered block
        probe.onBlockUpdate(new BlockPos(0, 0, 1).asLong(), true);
        assertEquals(Result.VERIFIED, probe.tick(1));
    }

    @Test
    void timesOutAndResendsOnlyUnanswered() {
        GhostBlockProbe probe = new GhostBlockProbe(20);
        List<BlockPos> row = highwayRow();
        probe.start(2, row, 0);
        for (int i = 0; i < 5; i++) probe.onBlockUpdate(row.get(i).asLong(), true);

        assertEquals(Result.WAITING, probe.tick(19));
        assertEquals(Result.TIMEOUT, probe.tick(20));

        List<BlockPos> resend = probe.unanswered(20);
        assertEquals(Set.copyOf(row.subList(5, 7)), Set.copyOf(resend));
        assertEquals(Result.WAITING, probe.tick(21), "resend restarts the timeout");
        assertEquals(Result.TIMEOUT, probe.tick(40));
    }

    @Test
    void emptyRowVerifiesImmediately() {
        GhostBlockProbe probe = new GhostBlockProbe(20);
        probe.start(7, List.of(), 0);
        assertFalse(probe.isProbing());
        assertTrue(probe.isVerified(7));
    }

    @Test
    void startingANewRowDropsTheOldOne() {
        GhostBlockProbe probe = new GhostBlockProbe(20);
        probe.start(1, List.of(new BlockPos(0, 0, 0)), 0);
        probe.start(2, List.of(new BlockPos(1, 0, 0)), 1);
        probe.onBlockUpdate(new BlockPos(0, 0, 0).asLong(), false); // old row's reply, no longer relevant
        probe.onBlockUpdate(new BlockPos(1, 0, 0).asLong(), true);
        assertEquals(Result.VERIFIED, probe.tick(2));
        assertTrue(probe.isVerified(2));
    }

    @Test
    void resetForgetsEverything() {
        GhostBlockProbe probe = new GhostBlockProbe(20);
        probe.start(3, List.of(), 0);
        assertTrue(probe.isVerified(3));
        probe.reset();
        assertFalse(probe.isVerified(3));
        assertFalse(probe.isVerified(Long.MIN_VALUE + 1));
        assertEquals(Result.IDLE, probe.tick(1));
    }

    @Test
    void negativeRowsWork() {
        GhostBlockProbe probe = new GhostBlockProbe(20);
        probe.start(-50, List.of(), 0);
        assertTrue(probe.isVerified(-50));
        assertTrue(probe.isVerified(-51));
        assertFalse(probe.isVerified(-49));
    }
}
