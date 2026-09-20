/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.utils;

/** Splits a per-tick packet budget between mining and placing. Mining wins: you can't place into rock. */
public final class PacketBudget {
    public record Plan(int mine, int place) {}

    private PacketBudget() {}

    /**
     * @param budget    packets the tick may send in total
     * @param overhead  packets sent regardless (movement, slot swaps)
     * @param mineCost  packets per mine action
     * @param placeCost packets per place action
     * @param wantMine  mine actions the module asked for
     * @param wantPlace place actions the module asked for ({@code Integer.MAX_VALUE} = unlimited)
     */
    public static Plan split(int budget, int overhead, int mineCost, int placeCost, int wantMine, int wantPlace) {
        int left = Math.max(0, budget - Math.max(0, overhead));

        int mine = Math.max(0, wantMine);
        if (mineCost > 0) mine = Math.min(mine, left / mineCost);
        // One mine action always goes through: a budget too small to mine would stall the module.
        if (mine == 0 && wantMine > 0) mine = 1;
        left -= mine * Math.max(0, mineCost);

        int place = Math.max(0, wantPlace);
        if (placeCost > 0) place = (int) Math.min(place, Math.max(0, left) / (long) placeCost);
        return new Plan(mine, place);
    }
}
