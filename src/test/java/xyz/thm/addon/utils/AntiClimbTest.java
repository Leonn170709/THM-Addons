/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.utils;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AntiClimbTest {
    @ParameterizedTest(name = "{2}")
    @CsvSource({
        // falling, drop, case -> suppress
        "false, 0,   walking into a ladder or vine or scaffold of any height,  true",
        "false, 5,   standing inside a tall scaffold column,                   true",
        "true,  0.5, half a block down a ladder,                               true",
        "true,  1.0, exactly one block down,                                   true",
        "true,  1.2, a bit more than a block gets the vanilla catch,           false",
        "true,  3.0, long ladder shaft gets the vanilla catch,                 false",
    })
    void decides(boolean falling, double drop, String description, boolean expected) {
        assertEquals(expected, AntiClimb.suppress(falling, drop), description);
    }
}
