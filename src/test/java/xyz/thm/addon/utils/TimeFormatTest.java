/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.utils;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TimeFormatTest {
    @ParameterizedTest
    @CsvSource({
        "0, 0s", "-5, 0s", "59, 59s", "60, 1m 0s", "250, 4m 10s", "3599, 59m 59s",
        "3600, 1h 00m", "7500, 2h 05m", "90000, 25h 00m",
    })
    void duration(long seconds, String expected) {
        assertEquals(expected, TimeFormat.duration(seconds));
    }

    @ParameterizedTest
    @CsvSource({
        "7500, 30, ~4m 10s",    // 7500 obsidian at 1.5/tick
        "30, 30, ~1s",
        "1, 30, ~0s",
        "0, 30, now",
        "-3, 30, now",
        "100, 0, -",            // not placing: no ETA
        "100, 0.01, -",
        "100, NaN, -",
        "100, Infinity, -",
    })
    void eta(int amount, double perSecond, String expected) {
        assertEquals(expected, TimeFormat.eta(amount, perSecond));
    }
}
