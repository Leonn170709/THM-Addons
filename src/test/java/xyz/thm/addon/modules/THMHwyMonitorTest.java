/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.modules;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ChunkTrackingView;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

class THMHwyMonitorTest {
    @ParameterizedTest
    @CsvSource({"1,0", "-1,0", "0,1", "0,-1", "1,1", "1,-1", "-1,1", "-1,-1"})
    void retreatClearsServerTrackingAndReturnsWithinTheOriginalDistance(int dx, int dz) {
        for (BlockPos start : new BlockPos[] {new BlockPos(-1, 120, -17), new BlockPos(15, 120, 32)}) {
            ChunkPos original = ChunkPos.containing(start);
            BlockPos first = THMHwyMonitor.chunkBackstepGoal(start, dx, dz, 1);
            BlockPos far = THMHwyMonitor.chunkBackstepGoal(start, dx, dz, 4);
            ChunkPos firstChunk = ChunkPos.containing(first);
            ChunkPos farChunk = ChunkPos.containing(far);
            assertEquals(original.x() - dx, firstChunk.x());
            assertEquals(original.z() - dz, firstChunk.z());
            assertEquals(start.getY(), far.getY());
            assertTrue(ChunkTrackingView.of(firstChunk, 2).contains(original));
            var dipped = ChunkTrackingView.of(farChunk, 2);
            assertFalse(dipped.contains(original));
            for (int x = -8; x <= 8; x++) {
                for (int z = -8; z <= 8; z++) {
                    if (x * dx + z * dz >= 0) assertFalse(dipped.contains(original.x() + x, original.z() + z));
                }
            }
        }
    }
}
