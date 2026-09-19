/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.utils;

public final class TimeFormat {
    private TimeFormat() {}

    /** "45s", "4m 10s", "2h 05m". */
    public static String duration(long seconds) {
        if (seconds < 0) seconds = 0;
        if (seconds < 60) return seconds + "s";
        if (seconds < 3600) return (seconds / 60) + "m " + (seconds % 60) + "s";
        return String.format(java.util.Locale.ROOT, "%dh %02dm", seconds / 3600, (seconds % 3600) / 60);
    }

    /** Time until {@code amount} runs out at {@code perSecond}, or "-" when not consuming. */
    public static String eta(int amount, double perSecond) {
        if (amount <= 0) return "now";
        if (!(perSecond >= 0.05) || Double.isInfinite(perSecond)) return "-";
        return "~" + duration(Math.round(amount / perSecond));
    }
}
