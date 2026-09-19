/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.utils;

/**
 * Drops fast on trouble, climbs back slowly while stable, and remembers the rate that failed:
 * it settles one step below it and only retries it after {@code probeTicks} of calm, so it holds
 * the best working rate instead of sawtoothing into the bad one. Values are kept to one decimal.
 */
public final class AdaptiveRate {
    private final double min, max, down, up;
    private final int stableTicksPerStep, probeTicks;
    private double rate;
    private double ceiling = Double.POSITIVE_INFINITY;
    private int stableTicks, capTicks;

    public AdaptiveRate(double min, double max, double down, double up, int stableTicksPerStep, int probeTicks) {
        this.min = min;
        this.max = max;
        this.down = down;
        this.up = up;
        this.stableTicksPerStep = stableTicksPerStep;
        this.probeTicks = probeTicks;
    }

    public void reset(double start) {
        rate = Math.max(min, Math.min(max, tenth(start)));
        ceiling = Double.POSITIVE_INFINITY;
        stableTicks = capTicks = 0;
    }

    public double get() {
        return rate;
    }

    /** Lowest rate known to fail, or infinity. */
    public double ceiling() {
        return ceiling;
    }

    /** @return true if the rate went down */
    public boolean onTrouble() {
        stableTicks = capTicks = 0;
        ceiling = rate;
        double lowered = Math.max(min, tenth(rate - down));
        boolean changed = lowered < rate;
        rate = lowered;
        return changed;
    }

    /** @return true if the rate went up */
    public boolean onStableTick() {
        if (rate >= max) return false;

        double cap = Double.isInfinite(ceiling) ? max : Math.max(min, Math.min(max, tenth(ceiling - up)));
        if (rate < cap - 1e-9) {
            capTicks = 0;
            if (++stableTicks < stableTicksPerStep) return false;
            stableTicks = 0;
            rate = Math.min(cap, tenth(rate + up));
            return true;
        }

        // Sitting just under the known-bad rate: retry it now and then, conditions change.
        if (++capTicks < probeTicks) return false;
        capTicks = stableTicks = 0;
        ceiling = Double.POSITIVE_INFINITY;
        rate = Math.min(max, tenth(rate + up));
        return true;
    }

    /** Whole actions for this tick; the fraction carries over so e.g. 1.5 gives 1, 2, 1, 2. */
    public static int actionsThisTick(double rate, double[] carry) {
        if (!(rate > 0) || Double.isInfinite(rate)) return 0;
        int whole = (int) Math.floor(rate);
        carry[0] += rate - whole;
        if (carry[0] >= 1.0 - 1e-9) {
            whole++;
            carry[0] -= 1.0;
        }
        return whole;
    }

    static double tenth(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
