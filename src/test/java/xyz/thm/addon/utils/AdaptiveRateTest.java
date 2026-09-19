/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

class AdaptiveRateTest {
    /** Same numbers HighwayBuilder uses for adaptive-placements. */
    private static AdaptiveRate placements(double start) {
        AdaptiveRate r = new AdaptiveRate(0.5, 3.0, 0.5, 0.1, 200);
        r.reset(start);
        return r;
    }

    @ParameterizedTest
    @CsvSource({"1.5, 1.5", "3, 3", "7, 3", "0.1, 0.5", "1.44, 1.4", "1.46, 1.5"})
    void resetClampsAndRounds(double start, double expected) {
        assertEquals(expected, placements(start).get(), 1e-9);
    }

    @Test
    void troubleDropsByHalfDownToFloor() {
        AdaptiveRate r = placements(1.5);
        assertTrue(r.onTrouble());
        assertEquals(1.0, r.get(), 1e-9);
        assertTrue(r.onTrouble());
        assertEquals(0.5, r.get(), 1e-9);
        assertFalse(r.onTrouble(), "already at the floor");
        assertEquals(0.5, r.get(), 1e-9);
    }

    @Test
    void climbsOneTenthPer200StableTicks() {
        AdaptiveRate r = placements(1.0);
        for (int i = 0; i < 199; i++) assertFalse(r.onStableTick());
        assertTrue(r.onStableTick());
        assertEquals(1.1, r.get(), 1e-9);
    }

    @Test
    void neverClimbsPastThree() {
        AdaptiveRate r = placements(2.9);
        for (int i = 0; i < 200 * 50; i++) r.onStableTick();
        assertEquals(3.0, r.get(), 1e-9);
    }

    @Test
    void troubleRestartsTheStableCounter() {
        AdaptiveRate r = placements(2.0);
        for (int i = 0; i < 150; i++) r.onStableTick();
        r.onTrouble();
        for (int i = 0; i < 199; i++) assertFalse(r.onStableTick(), "tick " + i);
        assertTrue(r.onStableTick());
        assertEquals(1.6, r.get(), 1e-9);
    }

    @Test
    void repeatedStepsStayOnTenths() {
        AdaptiveRate r = placements(0.5);
        for (int i = 0; i < 200 * 25; i++) r.onStableTick();
        assertEquals(3.0, r.get(), 0.0, "no float drift like 2.9999999");
    }

    /** Same numbers HighwayBuilder uses for adaptive-mining. */
    private static AdaptiveRate mining(double start) {
        AdaptiveRate r = new AdaptiveRate(1.0, 29.0, 3.0, 1.0, 200);
        r.reset(start);
        return r;
    }

    @ParameterizedTest
    @CsvSource({"7, 7", "30, 29", "0.5, 1", "12.5, 12.5"})
    void miningResetClampsToUnder30(double start, double expected) {
        assertEquals(expected, mining(start).get(), 1e-9);
    }

    @Test
    void miningDropsByThreeDownToOne() {
        AdaptiveRate r = mining(7);
        r.onTrouble();
        assertEquals(4, r.get(), 1e-9);
        r.onTrouble();
        assertEquals(1, r.get(), 1e-9);
        assertFalse(r.onTrouble());
        assertEquals(1, r.get(), 1e-9);
    }

    @Test
    void miningClimbsOnePerTenSecondsUpTo29() {
        AdaptiveRate r = mining(27);
        for (int i = 0; i < 200; i++) r.onStableTick();
        assertEquals(28, r.get(), 1e-9);
        for (int i = 0; i < 200 * 10; i++) r.onStableTick();
        assertEquals(29, r.get(), 1e-9);
    }

    // ---- actionsThisTick ----

    private static int[] run(double rate, int ticks) {
        double[] carry = {0};
        int[] out = new int[ticks];
        for (int i = 0; i < ticks; i++) out[i] = AdaptiveRate.actionsThisTick(rate, carry);
        return out;
    }

    private static int sum(int[] a) {
        int s = 0;
        for (int v : a) s += v;
        return s;
    }

    @Test
    void onePointFiveAlternates() {
        assertArrayEquals(new int[]{1, 2, 1, 2, 1, 2}, run(1.5, 6));
    }

    @Test
    void onePointFiveIsThirtyPerSecond() {
        assertEquals(30, sum(run(1.5, 20)));
    }

    @ParameterizedTest
    @CsvSource({"0.1, 200, 20", "0.3, 100, 30", "2.7, 100, 270", "7, 20, 140", "0.5, 20, 10"})
    void averagesExactlyWithoutDrift(double rate, int ticks, int expected) {
        assertEquals(expected, sum(run(rate, ticks)));
    }

    @Test
    void pointOneFiresOnTheTenthTick() {
        int[] a = run(0.1, 10);
        for (int i = 0; i < 9; i++) assertEquals(0, a[i], "tick " + i);
        assertEquals(1, a[9], "float sum 0.1*10 = 0.9999999 must still fire");
    }

    @Test
    void wholeRatesNeverCarry() {
        double[] carry = {0};
        for (int i = 0; i < 100; i++) assertEquals(3, AdaptiveRate.actionsThisTick(3.0, carry));
        assertEquals(0.0, carry[0], 0.0);
    }

    @Test
    void badRatesGiveNothing() {
        double[] carry = {0};
        assertEquals(0, AdaptiveRate.actionsThisTick(0, carry));
        assertEquals(0, AdaptiveRate.actionsThisTick(-2, carry));
        assertEquals(0, AdaptiveRate.actionsThisTick(Double.NaN, carry));
        assertEquals(0, AdaptiveRate.actionsThisTick(Double.POSITIVE_INFINITY, carry));
        assertEquals(0.0, carry[0], 0.0);
    }
}
