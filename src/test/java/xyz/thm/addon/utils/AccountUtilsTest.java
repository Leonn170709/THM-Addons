/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.utils;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AccountUtilsTest {
    @Test void offlineDetectionUsesTheExactProfileName() {
        UUID offline = UUID.fromString("b50ad385-829d-3141-a216-7e7d7539ba7f");
        assertEquals(offline, AccountUtils.offlineUuid("Notch"));
        assertTrue(AccountUtils.isCracked("Notch", offline));
        assertFalse(AccountUtils.isCracked("notch", offline));
        assertFalse(AccountUtils.isCracked("Notch", UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5")));
        assertFalse(AccountUtils.isCracked("Notch", null));
        assertFalse(AccountUtils.isCracked(null, offline));
        assertFalse(AccountUtils.isCracked("", offline));
    }

}
