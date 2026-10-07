/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.utils.server;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ServerTelemetryTest {
    private static final long SECOND = 1_000_000_000L;

    @Test
    void millisecondsRequireThreeConsistentPacketsAndExtrapolateOnlyFreshSamples() {
        var telemetry = new ServerTelemetry();
        telemetry.observeKeepalive(86_400_000, 0);
        assertEquals(-1, telemetry.uptimeMillis(0));
        telemetry.observeKeepalive(86_415_000, 15 * SECOND);
        assertEquals(-1, telemetry.uptimeMillis(15 * SECOND));
        telemetry.observeKeepalive(86_430_000, 30 * SECOND);
        assertEquals(ServerTelemetry.ClockType.Milliseconds, telemetry.clockType());
        assertEquals(86_435_000, telemetry.uptimeMillis(35 * SECOND));
        assertEquals(-1, telemetry.uptimeMillis(91 * SECOND));
        telemetry.observeKeepalive(86_505_000, 105 * SECOND);
        assertEquals(86_505_000, telemetry.uptimeMillis(105 * SECOND));
    }

    @Test
    void nanosecondClockIsConvertedWithoutConfusingItWithEpochMilliseconds() {
        var telemetry = new ServerTelemetry();
        for (int i = 0; i < 3; i++) telemetry.observeKeepalive((86400L + 15 * i) * SECOND, i * 15 * SECOND);
        assertEquals(ServerTelemetry.ClockType.Nanoseconds, telemetry.clockType());
        assertEquals(86_435_000, telemetry.uptimeMillis(35 * SECOND));
    }

    @Test
    void epochValuesAreNotPresentedAsDecadesOfUptime() {
        var telemetry = new ServerTelemetry();
        for (int i = 0; i < 3; i++) telemetry.observeKeepalive(1_780_000_000_000L + 15_000 * i, i * 15 * SECOND);
        assertEquals(ServerTelemetry.ClockType.Epoch, telemetry.clockType());
        assertEquals(-1, telemetry.uptimeMillis(30 * SECOND));
    }

    @Test
    void countersRandomIdsRepeatedIdsAndNegativeValuesDoNotResolveUptime() {
        for (long[] ids : new long[][]{{1, 2, 3}, {500000, 10, 987654321}, {10000, 10000, 10000}, {-30000, -15000, -1}, {Long.MIN_VALUE, 1, Long.MAX_VALUE}}) {
            var telemetry = new ServerTelemetry();
            for (int i = 0; i < ids.length; i++) telemetry.observeKeepalive(ids[i], i * 15 * SECOND);
            assertEquals(-1, telemetry.uptimeMillis(30 * SECOND));
        }
    }

    @Test
    void fastCountersAndPacketBurstsCannotMasqueradeAsClocks() {
        var counter = new ServerTelemetry();
        var burst = new ServerTelemetry();
        for (int i = 0; i < 3; i++) {
            counter.observeKeepalive(10 + i, i * SECOND / 10);
            burst.observeKeepalive(100000 + 15000 * i, i * SECOND / 1000);
        }
        assertEquals(-1, counter.uptimeMillis(SECOND));
        assertEquals(-1, burst.uptimeMillis(SECOND));
    }

    @Test
    void moderateNetworkJitterDoesNotPreventClockDetection() {
        var telemetry = new ServerTelemetry();
        telemetry.observeKeepalive(100000, 0);
        telemetry.observeKeepalive(115000, 14 * SECOND);
        telemetry.observeKeepalive(130000, 31 * SECOND);
        assertEquals(130000, telemetry.uptimeMillis(31 * SECOND));
    }

    @Test
    void changedClockNeedsNewEvidenceInsteadOfKeepingThePreviousEstimate() {
        var telemetry = new ServerTelemetry();
        for (int i = 0; i < 3; i++) telemetry.observeKeepalive(100000 + 15000 * i, i * 15 * SECOND);
        assertTrue(telemetry.uptimeMillis(30 * SECOND) > 0);
        telemetry.observeKeepalive(1000, 45 * SECOND);
        assertEquals(-1, telemetry.uptimeMillis(45 * SECOND));
        telemetry.observeKeepalive(16000, 60 * SECOND);
        assertEquals(-1, telemetry.uptimeMillis(60 * SECOND));
        telemetry.observeKeepalive(31000, 75 * SECOND);
        assertEquals(31000, telemetry.uptimeMillis(75 * SECOND));
    }

    @Test
    void tpsUsesTickDeltasAndElapsedTimeRatherThanAssumingTwentyTicksPerPacket() {
        var telemetry = new ServerTelemetry();
        telemetry.observeWorldTime(0, 0);
        telemetry.observeWorldTime(20, 2 * SECOND);
        assertEquals(10, telemetry.observedTps(2 * SECOND), 1e-9);
        telemetry.resetWorldTime();
        telemetry.observeWorldTime(0, 0);
        telemetry.observeWorldTime(50, SECOND);
        assertEquals(50, telemetry.observedTps(SECOND), 1e-9);
    }

    @Test
    void tpsAveragesJitterAndReportsFrozenWorldTicksWithoutInventingMspt() {
        var telemetry = new ServerTelemetry();
        telemetry.observeWorldTime(0, 0);
        telemetry.observeWorldTime(20, SECOND * 9 / 10);
        telemetry.observeWorldTime(40, 2 * SECOND);
        assertEquals(20, telemetry.observedTps(2 * SECOND), 1e-9);
        telemetry.resetWorldTime();
        telemetry.observeWorldTime(40, 3 * SECOND);
        telemetry.observeWorldTime(40, 4 * SECOND);
        assertEquals(0, telemetry.observedTps(4 * SECOND));
        assertTrue(Double.isNaN(telemetry.observedTps(35 * SECOND)));
    }

    @Test
    void boundedTpsWindowDropsOldLagSamples() {
        var telemetry = new ServerTelemetry();
        telemetry.observeWorldTime(0, 0);
        long ticks = 0;
        for (int i = 1; i <= 100; i++) {
            ticks += i <= 92 ? 5 : 20;
            telemetry.observeWorldTime(ticks, i * SECOND);
        }
        assertEquals(20, telemetry.observedTps(100 * SECOND), 1e-9);
    }

    @Test
    void worldClockResetsDoNotEraseKeepaliveEvidenceAndReconnectDoes() {
        var telemetry = new ServerTelemetry();
        for (int i = 0; i < 3; i++) telemetry.observeKeepalive(100000 + 15000 * i, i * 15 * SECOND);
        telemetry.observeWorldTime(1000, 0);
        telemetry.observeWorldTime(1020, SECOND);
        telemetry.observeWorldTime(10, 2 * SECOND);
        assertTrue(Double.isNaN(telemetry.observedTps(2 * SECOND)));
        assertEquals(10, telemetry.worldTicks());
        assertEquals(130000, telemetry.uptimeMillis(30 * SECOND));
        telemetry.reset();
        assertEquals(-1, telemetry.uptimeMillis(30 * SECOND));
        assertEquals(0, telemetry.keepaliveSamples());
        assertEquals(-1, telemetry.worldTicks());
    }

    @Test
    void invalidWorldTimesAndNonAdvancingArrivalTimesResetTheRate() {
        var telemetry = new ServerTelemetry();
        telemetry.observeWorldTime(0, 0);
        telemetry.observeWorldTime(20, SECOND);
        telemetry.observeWorldTime(40, SECOND);
        assertTrue(Double.isNaN(telemetry.observedTps(SECOND)));
        telemetry.observeWorldTime(-1, 2 * SECOND);
        assertEquals(-1, telemetry.worldTicks());
        assertEquals(-1, telemetry.timeUpdateAgeMillis(2 * SECOND));
    }

    @Test
    void durationsRemainReadableAcrossDayBoundaries() {
        assertEquals("1d 02h 03m 04s", ServerTelemetry.duration(((26 * 60 + 3) * 60 + 4) * 1000L));
        assertEquals("0d 00h 00m 00s", ServerTelemetry.duration(0));
    }

    @Test
    void pluginHintsDeduplicateNamespacesAndExcludeBuiltInCommands() {
        assertEquals(List.of("essentials", "luckperms", "worldedit"), ServerTelemetry.pluginHints(List.of(
            "essentials:home", "essentials:warp", "WorldEdit:wand", "luckperms:lp",
            "minecraft:give", "bukkit:plugins", "paper:paper", "SPIGOT:tps", "warp")));
    }

    @Test
    void bareAliasesAndMalformedNamespacesDoNotInventPluginNames() {
        assertEquals(List.of(), ServerTelemetry.pluginHints(List.of(
            "warp", "plugins", ":home", "essentials:", "essentials:home:extra", "bad namespace:home")));
        assertEquals(List.of("custom-plugin", "my.mod"), ServerTelemetry.pluginHints(List.of(
            "custom-plugin:home", "my.mod:teleport")));
    }
}
