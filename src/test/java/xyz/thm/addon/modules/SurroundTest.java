/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.modules;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ClientboundBlockDestructionPacket;
import net.minecraft.network.protocol.game.ClientboundLevelEventPacket;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SurroundTest {
    @Test void miningProgressAndAbortPacketsDoNotTriggerPreplace() {
        BlockPos pos = new BlockPos(1, 64, 2);
        assertTrue(Surround.isBreakEffect(new ClientboundLevelEventPacket(LevelEvent.PARTICLES_DESTROY_BLOCK, pos, 1, false)));
        assertFalse(Surround.isBreakEffect(new ClientboundLevelEventPacket(0, pos, 1, false)));
        for (int progress : new int[]{-1, 0, 8, 9}) {
            assertFalse(Surround.isBreakEffect(new ClientboundBlockDestructionPacket(1, pos, progress)));
        }
    }

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

    @Test void packetPreplaceSkipsBatchDelayButStillRespectsTheTickLimit() {
        var budget = new Surround.PlacementBudget();
        assertTrue(budget.reserve(2, 5));
        assertTrue(budget.dispatch(2, 5));
        budget.advanceTick();
        assertFalse(budget.reserve(2, 5));
        assertTrue(budget.reserve(2, 0));
        assertTrue(budget.dispatch(2, 0));
        assertTrue(budget.reserve(2, 0));
        assertTrue(budget.dispatch(2, 0));
        assertFalse(budget.reserve(2, 0));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3})
    void dropdownMigrationPreservesEveryLegacyToggleCombination(int mask) {
        boolean first = (mask & 1) != 0, second = (mask & 2) != 0;
        CompoundTag module = new CompoundTag(), settings = new CompoundTag(), group = new CompoundTag();
        group.putString("name", "Place Logic");
        group.putBoolean("sectionExpanded", true);
        ListTag values = new ListTag();
        for (String name : new String[]{"packet", "head-level", "extend", "disable-on-jump", "attack-crystals", "pre-place-explosion", "render"}) {
            values.add(booleanSetting(name, first));
        }
        for (String name : new String[]{"packet-place-once", "cover-head", "mine-extend", "disable-on-y-change", "desync-protection", "pre-place-crystal-spawn", "fade"}) {
            values.add(booleanSetting(name, second));
        }
        group.put("settings", values);
        ListTag groups = new ListTag(); groups.add(group); settings.put("groups", groups); module.put("settings", settings);
        CompoundTag original = module.copy(), migrated = Surround.migrateSettings(module);
        assertEquals(original, module, "Loading must leave the original profile untouched.");
        var placement = Surround.PlacementMode.valueOf(choice(migrated, "place-mode"));
        assertEquals(first, placement.packet);
        if (first) assertEquals(second, placement.waitForAnswer);
        var coverage = Surround.Coverage.valueOf(choice(migrated, "coverage"));
        assertEquals(first, coverage.eyes); assertEquals(second, coverage.roof);
        var expansion = Surround.Expansion.valueOf(choice(migrated, "expansion"));
        assertEquals(first, expansion.edges); assertEquals(second, expansion.mining);
        var disable = Surround.DisableOn.valueOf(choice(migrated, "disable-on"));
        assertEquals(first, disable.jump); assertEquals(second, disable.height);
        var crystals = Surround.CrystalHandling.valueOf(choice(migrated, "crystal-handling"));
        assertEquals(first, crystals.attack); assertEquals(second, crystals.desync);
        var packets = Surround.PacketEvents.valueOf(choice(migrated, "packet-events"));
        assertEquals(first, packets.explosions); assertEquals(second, packets.crystals);
        assertEquals(!first ? "Off" : second ? "Fade" : "Static", choice(migrated, "render-style"));
        assertEquals(migrated, Surround.migrateSettings(migrated));
        for (Tag entry : migrated.getCompoundOrEmpty("settings").getListOrEmpty("groups")) {
            CompoundTag migratedGroup = (CompoundTag) entry;
            assertEquals(!migratedGroup.getStringOr("name", "").equals("Advanced"), migratedGroup.getBooleanOr("sectionExpanded", true));
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2})
    void migrationRetainsChoicesAndValuesWhileUpgradingLayoutOnce(int version) {
        CompoundTag module = new CompoundTag(), settings = new CompoundTag(), general = new CompoundTag(), render = new CompoundTag();
        module.putInt("surround-settings-version", version);
        module.putString("name", "surround-plus");
        general.putString("name", "General");
        ListTag values = new ListTag();
        CompoundTag choice = new CompoundTag(); choice.putString("name", "coverage"); choice.putString("value", "Roof"); values.add(choice);
        values.add(booleanSetting("head-level", true)); values.add(booleanSetting("cover-head", false));
        CompoundTag limit = new CompoundTag(); limit.putString("name", "blocks-per-tick"); limit.putInt("value", 7); values.add(limit);
        values.add(booleanSetting("tag-switch", true));
        general.put("settings", values);
        render.putString("name", "Render"); render.putBoolean("sectionExpanded", true);
        ListTag groups = new ListTag(); groups.add(general); groups.add(render); settings.put("groups", groups); module.put("settings", settings);
        CompoundTag migrated = Surround.migrateSettings(module);
        assertEquals("Roof", choice(migrated, "coverage"));
        assertEquals(7, setting(migrated, "blocks-per-tick").getIntOr("value", 0));
        assertTrue(setting(migrated, "auto-disable").getBooleanOr("value", false));
        assertEquals("surround-plus", migrated.getStringOr("name", ""));
        for (Tag entry : migrated.getCompoundOrEmpty("settings").getListOrEmpty("groups")) {
            CompoundTag group = (CompoundTag) entry;
            if (group.getStringOr("name", "").equals("General")) assertEquals(version < 2, group.getBooleanOr("sectionExpanded", true));
            if (group.getStringOr("name", "").equals("Render")) assertTrue(group.getBooleanOr("sectionExpanded", false));
            for (Tag value : group.getListOrEmpty("settings")) {
                assertNotEquals("head-level", ((CompoundTag) value).getStringOr("name", ""));
                assertNotEquals("tag-switch", ((CompoundTag) value).getStringOr("name", ""));
            }
        }
    }

    private static CompoundTag booleanSetting(String name, boolean value) {
        CompoundTag setting = new CompoundTag(); setting.putString("name", name); setting.putBoolean("value", value); return setting;
    }

    private static String choice(CompoundTag module, String name) { return setting(module, name).getStringOr("value", ""); }

    private static CompoundTag setting(CompoundTag module, String name) {
        for (Tag entry : module.getCompoundOrEmpty("settings").getListOrEmpty("groups")) {
            for (Tag value : ((CompoundTag) entry).getListOrEmpty("settings")) {
                CompoundTag setting = (CompoundTag) value;
                if (setting.getStringOr("name", "").equals(name)) return setting;
            }
        }
        throw new AssertionError("Missing setting: " + name);
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
