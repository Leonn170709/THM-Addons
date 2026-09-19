/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.utils;

/** Drops fast on trouble, climbs back slowly while stable. Values are kept to one decimal. */
public final class AdaptiveRate {
    private final double min, max, down, up;
    private final int stableTicksPerStep;
    private double rate;
    private int stableTicks;

    public AdaptiveRate(double min, double max, double down, double up, int stableTicksPerStep) {
        this.min = min;
        this.max = max;
        this.down = down;
        this.up = up;
        this.stableTicksPerStep = stableTicksPerStep;
    }

    public void reset(double start) {
        rate = Math.max(min, Math.min(max, tenth(start)));
        stableTicks = 0;
    }

    public double get() {
        return rate;
    }

    /** @return true if the rate went down */
    public boolean onTrouble() {
        stableTicks = 0;
        double lowered = Math.max(min, tenth(rate - down));
        boolean changed = lowered < rate;
        rate = lowered;
        return changed;
    }

    /** @return true if the rate went up */
    public boolean onStableTick() {
        if (++stableTicks < stableTicksPerStep || rate >= max) return false;
        stableTicks = 0;
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
