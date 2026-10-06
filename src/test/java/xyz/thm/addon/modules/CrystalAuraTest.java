/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.modules;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class CrystalAuraTest {
    @Test void existingBasesTakePriorityOverHigherDamageSupport() {
        var existing = new CrystalAura.Opportunity(new BlockPos(0, 64, 0), 8, 1, 8, null, null, false);
        var support = new CrystalAura.Opportunity(new BlockPos(1, 64, 0), 15, 1, 15, null, null, true);
        var weakerSupport = new CrystalAura.Opportunity(new BlockPos(2, 64, 0), 10, 1, 10, null, null, true);
        assertSame(existing, CrystalAura.bestOpportunity(List.of(support, existing), null));
        assertSame(existing, CrystalAura.bestOpportunity(List.of(existing, support), null));
        assertSame(support, CrystalAura.bestOpportunity(List.of(weakerSupport, support), null));
    }

    @Test void damageAndAdvantageChooseDifferentOpportunities() {
        assertEquals(12, CrystalAura.Priority.Damage.score(12, 7));
        assertEquals(5, CrystalAura.Priority.Advantage.score(12, 7));
        var highDamage = new CrystalAura.Opportunity(new BlockPos(0, 64, 0), 12, 7, 12, null, null, false);
        var safeDamage = new CrystalAura.Opportunity(new BlockPos(1, 64, 0), 10, 1, 10, null, null, false);
        assertSame(highDamage, CrystalAura.bestOpportunity(List.of(highDamage, safeDamage), null));
        var advantage = new CrystalAura.Opportunity(safeDamage.base, 10, 1, 9, null, null, false);
        var weaker = new CrystalAura.Opportunity(highDamage.base, 12, 7, 5, null, null, false);
        assertSame(advantage, CrystalAura.bestOpportunity(List.of(weaker, advantage), null));
        assertNull(CrystalAura.bestOpportunity(List.of(), null));
    }

    @Test void safeDamageRejectsLowDamageExcessiveSelfDamageAndLethalHits() {
        assertTrue(allowed(10, 4, 20, CrystalAura.Priority.Damage));
        assertFalse(allowed(7, 4, 20, CrystalAura.Priority.Damage));
        assertFalse(allowed(10, 9, 20, CrystalAura.Priority.Damage));
        assertFalse(allowed(10, 5, 10, CrystalAura.Priority.Damage));
        assertFalse(allowed(10, 5, 5, CrystalAura.Priority.Damage));
        assertFalse(allowed(10, 7, 20, CrystalAura.Priority.Advantage));
        assertTrue(allowed(10, 6, 20, CrystalAura.Priority.Advantage));
        assertFalse(allowed(Double.NaN, 0, 20, CrystalAura.Priority.Damage));
        assertFalse(allowed(10, Double.NaN, 20, CrystalAura.Priority.Damage));
    }

    @Test void fivePredictionModesPreserveLambdaTimingFlags() {
        var none = CrystalAura.PredictionMode.None;
        assertFalse(none.onPacket || none.onPlace || none.onTick);
        assertTrue(CrystalAura.PredictionMode.Packet.onPacket);
        assertFalse(CrystalAura.PredictionMode.Packet.onPlace);
        assertTrue(CrystalAura.PredictionMode.Deferred.onPlace);
        assertFalse(CrystalAura.PredictionMode.Deferred.onPacket);
        assertTrue(CrystalAura.PredictionMode.Tick.onTick);
        assertFalse(CrystalAura.PredictionMode.Tick.onPacket || CrystalAura.PredictionMode.Tick.onPlace);
        assertTrue(CrystalAura.PredictionMode.Mixed.onPacket && CrystalAura.PredictionMode.Mixed.onPlace);
    }

    @Test void placementVolumeMatchesTwoBlocksAboveTheBase() {
        var base = new BlockPos(5, 64, -2);
        var position = CrystalAura.crystalPosition(base);
        var box = CrystalAura.crystalBox(base);
        assertEquals(5.5, position.x);
        assertEquals(65, position.y);
        assertEquals(-1.5, position.z);
        assertEquals(65, box.minY);
        assertEquals(67, box.maxY);
        assertEquals(1, box.getXsize());
        assertEquals(1, box.getZsize());
    }

    private static boolean allowed(double target, double self, double health, CrystalAura.Priority priority) {
        return CrystalAura.allowedDamage(target, self, health, 8, 8, 5, true, priority, 4);
    }
}
