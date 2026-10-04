/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.modules;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SpeedmineTest {
    @ParameterizedTest
    @CsvSource({"1000,20,20", "1000,10,10", "1000,5,5", "50,20,1", "100,10,1", "1000,0,0", "1000,-5,0", "1000,30,20", "-50,20,0"})
    void progressAndStartBudgetFollowServerTicks(long millis, float tps, double ticks) {
        assertEquals(ticks, Speedmine.serverTicks(millis, tps), 1e-9);
    }

    @Test
    void changingTpsKeepsAlreadyEarnedProgress() {
        assertEquals(15, Speedmine.serverTicks(500, 20) + Speedmine.serverTicks(500, 10), 1e-9);
    }

    @Test
    void invalidEstimateFallsBackToVanillaRate() {
        assertEquals(20, Speedmine.serverTicks(1000, Float.NaN), 1e-9);
    }

    @ParameterizedTest
    @CsvSource({"false,false,false,false,false", "false,true,false,false,true", "false,false,true,false,true", "false,false,false,true,false", "true,false,false,false,true", "false,true,true,true,true"})
    void migrateLocalRemovalToSingleToggle(boolean current, boolean instant, boolean unvalidated,
                                          boolean slow, boolean expected) {
        CompoundTag original = new CompoundTag();
        ListTag values = new ListTag();
        values.add(booleanSetting("client-prediction", current));
        values.add(booleanSetting("instant-client-remove", instant));
        values.add(booleanSetting("validate-break", !unvalidated));
        values.add(booleanSetting("remove-slow-blocks", slow));
        values.add(booleanSetting("tps-sync", true));
        CompoundTag general = new CompoundTag();
        general.putString("name", "General");
        general.put("settings", values);
        ListTag groups = new ListTag();
        groups.add(general);
        CompoundTag savedSettings = new CompoundTag();
        savedSettings.put("groups", groups);
        original.put("settings", savedSettings);
        original.putBoolean("active", true);

        CompoundTag migrated = Speedmine.migrateSettings(original);
        assertTrue(migrated.getBooleanOr("active", false));
        assertEquals(5, values.size(), "Loading must not modify the original profile.");
        var migratedGroup = (CompoundTag) migrated.getCompoundOrEmpty("settings").getListOrEmpty("groups").get(0);
        var migratedValues = migratedGroup.getListOrEmpty("settings");
        assertEquals(2, migratedValues.size());
        assertEquals(expected, ((CompoundTag) migratedValues.get(0)).getBooleanOr("value", false));
        assertTrue(((CompoundTag) migratedValues.get(1)).getBooleanOr("value", false));
        assertEquals(migrated, Speedmine.migrateSettings(migrated));
    }

    @Test
    void migrationHandlesUnchangedLegacyDefaultsWithoutSettings() {
        CompoundTag tag = new CompoundTag();
        assertEquals(tag, Speedmine.migrateSettings(tag));
    }

    @Test
    void migrationAddsPredictionForLegacyProfileWithoutNewToggle() {
        CompoundTag tag = new CompoundTag();
        ListTag values = new ListTag();
        values.add(booleanSetting("instant-client-remove", true));
        CompoundTag general = new CompoundTag();
        general.putString("name", "General");
        general.put("settings", values);
        ListTag groups = new ListTag();
        groups.add(general);
        CompoundTag settings = new CompoundTag();
        settings.put("groups", groups);
        tag.put("settings", settings);

        var migratedGroup = (CompoundTag) Speedmine.migrateSettings(tag)
            .getCompoundOrEmpty("settings").getListOrEmpty("groups").get(0);
        assertEquals(1, migratedGroup.getListOrEmpty("settings").size());
        var prediction = (CompoundTag) migratedGroup.getListOrEmpty("settings").get(0);
        assertEquals("client-prediction", prediction.getStringOr("name", ""));
        assertTrue(prediction.getBooleanOr("value", false));
    }

    @Test
    void sameTickRestoresImmediatelyWithoutWaitingForConfirmation() {
        assertTrue(Speedmine.SwapMode.SameTick.shouldRelease(0, true, true, true, 0));
    }

    @Test
    void endOfTickRestoresAtPostWithoutWaitingForServer() {
        assertTrue(Speedmine.SwapMode.EndOfTick.shouldRelease(0, true, true, true, 0));
    }

    @Test
    void keepSpansNextTickAndConsecutiveReplacements() {
        var mode = Speedmine.SwapMode.Keep;
        assertFalse(mode.shouldRelease(0, false, false, false, 0));
        assertFalse(mode.shouldRelease(1, false, true, false, 1));
        assertFalse(mode.shouldRelease(5, false, true, false, 3), "TPS throttling must not release a queued rebreak.");
        assertFalse(mode.shouldRelease(0, false, false, false, 0), "Another STOP renews the hold.");
        assertTrue(mode.shouldRelease(1, false, false, false, 1));
    }

    @Test
    void keepDoesNotWaitForServerButRetainsActiveMining() {
        var mode = Speedmine.SwapMode.Keep;
        assertTrue(mode.shouldRelease(1, false, false, true, 0));
        assertFalse(mode.shouldRelease(10, true, false, false, 0));
    }

    @Test
    void confirmationModePreservesServerWaitAndIdleGrace() {
        var mode = Speedmine.SwapMode.ToolHold;
        assertFalse(mode.shouldRelease(100, false, false, true, 3));
        assertFalse(mode.shouldRelease(100, true, false, false, 3));
        assertFalse(mode.shouldRelease(100, false, false, false, 2));
        assertTrue(mode.shouldRelease(100, false, false, false, 3));
    }

    @ParameterizedTest
    @EnumSource(Speedmine.SwapMode.class)
    void normalBreaksHoldThroughSecondaryValidationInEveryMode(Speedmine.SwapMode selected) {
        var normal = selected.forBreak(false, false);
        assertEquals(Speedmine.SwapMode.ToolHold, normal);
        assertFalse(normal.shouldRelease(20, true, false, false, 3), "The secondary still needs a server tick with its tool.");
        assertFalse(normal.shouldRelease(20, false, false, true, 3), "Client prediction is not confirmation.");
        assertTrue(normal.shouldRelease(20, false, false, false, 3));
    }

    @ParameterizedTest
    @EnumSource(Speedmine.SwapMode.class)
    void instantAndPrimedBreaksKeepSelectedSwapTiming(Speedmine.SwapMode selected) {
        assertEquals(selected, selected.forBreak(true, false));
        assertEquals(selected, selected.forBreak(false, true));
    }

    @Test
    void bothCompletedBlocksRetainTheDelayedBlocksTool() {
        var firstClicked = new Speedmine.PendingBreak(new BlockPos(1, 64, 0), 2, true, true, 10000);
        var secondClicked = new Speedmine.PendingBreak(new BlockPos(2, 64, 0), 5, true, false, 10000);
        assertEquals(2, Speedmine.validationToolSlot(-1, List.of(secondClicked, firstClicked), -1));
        assertEquals(2, Speedmine.validationToolSlot(-1, List.of(firstClicked), -1),
            "Confirming the second block must not release the first block's tool.");
        assertEquals(5, Speedmine.validationToolSlot(-1, List.of(secondClicked), -1),
            "Confirming the first block must preserve the second block's validation.");
        assertEquals(-1, Speedmine.validationToolSlot(-1, List.of(), -1));
    }

    @Test
    void finishingPrimaryRestoresTheStillMiningSecondaryTool() {
        var secondClicked = new Speedmine.PendingBreak(new BlockPos(2, 64, 0), 5, true, false, 10000);
        assertEquals(2, Speedmine.validationToolSlot(2, List.of(secondClicked), -1));
        assertEquals(2, Speedmine.validationToolSlot(2, List.of(), 5));
    }

    @Test
    void predictedSecondaryKeepsItsToolWhilePrimaryStillMines() {
        var firstClicked = new Speedmine.PendingBreak(new BlockPos(1, 64, 0), 2, true, true, 10000);
        assertEquals(2, Speedmine.validationToolSlot(-1, List.of(firstClicked), 5));
        assertEquals(5, Speedmine.validationToolSlot(-1, List.of(), 5));
    }

    @Test
    void primedRebreakHoldDoesNotOverrideNormalValidation() {
        var rebreak = new Speedmine.PendingBreak(new BlockPos(3, 64, 0), 7, false, true, 10000);
        assertEquals(5, Speedmine.validationToolSlot(-1, List.of(rebreak), 5));
        assertEquals(-1, Speedmine.validationToolSlot(-1, List.of(rebreak), -1));
    }

    @ParameterizedTest
    @CsvSource({"true,ToolHold", "false,SameTick"})
    void migrateLegacyToolHold(boolean hold, Speedmine.SwapMode expected) {
        CompoundTag original = moduleSettings(booleanSetting("tool-hold", hold));
        CompoundTag migrated = Speedmine.migrateSettings(original);
        var group = (CompoundTag) migrated.getCompoundOrEmpty("settings").getListOrEmpty("groups").get(0);
        assertEquals(1, group.getListOrEmpty("settings").size());
        var mode = (CompoundTag) group.getListOrEmpty("settings").get(0);
        assertEquals("swap-mode", mode.getStringOr("name", ""));
        assertEquals(expected.toString(), mode.getStringOr("value", ""));
        assertEquals(migrated, Speedmine.migrateSettings(migrated));
    }

    @Test
    void explicitSwapModeWinsOverLegacyToolHold() {
        CompoundTag mode = new CompoundTag();
        mode.putString("name", "swap-mode");
        mode.putString("value", Speedmine.SwapMode.Keep.toString());
        CompoundTag migrated = Speedmine.migrateSettings(moduleSettings(booleanSetting("tool-hold", true), mode));
        var group = (CompoundTag) migrated.getCompoundOrEmpty("settings").getListOrEmpty("groups").get(0);
        assertEquals(1, group.getListOrEmpty("settings").size());
        assertEquals(mode, group.getListOrEmpty("settings").get(0));
    }

    private static CompoundTag moduleSettings(CompoundTag... entries) {
        ListTag values = new ListTag();
        for (CompoundTag entry : entries) values.add(entry);
        CompoundTag general = new CompoundTag();
        general.putString("name", "General");
        general.put("settings", values);
        ListTag groups = new ListTag();
        groups.add(general);
        CompoundTag settings = new CompoundTag();
        settings.put("groups", groups);
        CompoundTag module = new CompoundTag();
        module.put("settings", settings);
        return module;
    }

    private static CompoundTag booleanSetting(String name, boolean value) {
        CompoundTag setting = new CompoundTag();
        setting.putString("name", name);
        setting.putBoolean("value", value);
        return setting;
    }

}
