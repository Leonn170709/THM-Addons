/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.utils;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PacketChokeTest {
    @Test
    void flushesInOrderAtTheHoldLimitThenReleasesForConfiguredTicks() {
        PacketChoke<Integer> choke = new PacketChoke<>();
        List<Integer> sent = new ArrayList<>();

        assertTrue(choke.hold(1));
        choke.tick(5, 2, 2, sent::add);
        assertTrue(choke.hold(2));
        assertTrue(sent.isEmpty());

        choke.tick(5, 2, 2, sent::add);
        assertEquals(List.of(1, 2), sent);
        assertFalse(choke.hold(3));
        choke.tick(5, 2, 2, sent::add);
        assertFalse(choke.isHolding());
        choke.tick(5, 2, 2, sent::add);
        assertTrue(choke.hold(4));

        choke.flush(packet -> {
            assertFalse(choke.hold(99));
            sent.add(packet);
        });
        assertEquals(List.of(1, 2, 4), sent);
    }

    @Test
    void usesConfiguredHoldAndKeepsPacketsWhenSendingFails() {
        PacketChoke<Integer> choke = new PacketChoke<>();
        assertTrue(choke.hold(7));
        choke.tick(2, 1, 5, packet -> fail("released too early"));
        assertThrows(IllegalStateException.class, () -> choke.tick(2, 1, 5, packet -> {
            throw new IllegalStateException("send failed");
        }));

        List<Integer> sent = new ArrayList<>();
        choke.tick(2, 1, 5, sent::add);
        assertEquals(List.of(7), sent);
    }
}
