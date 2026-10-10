/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.utils.server;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OnlinePlayersTest {
    @Test void hiddenBotIsMergedWithoutDuplicatingKnownPlayers() {
        var players = new OnlinePlayers();
        int id = players.request(1000);
        players.accept(id, 5, 0, List.of("KitBot1", "alice", "ALICE", "<player>", "bad name"), 1100);

        assertEquals(List.of("Alice", "KitBot1"), players.merge(List.of("Alice"), 1200));
        assertTrue(players.fresh(1200));
    }

    @Test void refreshedListDropsDepartedBotsAndExpiredSuggestions() {
        var players = new OnlinePlayers();
        players.accept(players.request(1000), 5, 0, List.of("KitBot1", "Alice"), 1100);
        players.accept(players.request(11000), 5, 0, List.of("Alice"), 11100);

        assertEquals(List.of("Alice"), players.merge(List.of(), 11200));
        assertEquals(List.of("Bob"), players.merge(List.of("Bob"), 41100));
        assertFalse(players.fresh(41100));
    }

    @Test void pollingIsLimitedAndLateResponsesCannotReplaceTheNextRequest() {
        var players = new OnlinePlayers();
        int first = players.request(1000);
        assertTrue(first < -1);
        assertEquals(0, players.request(2000));
        assertEquals(0, players.request(6000));
        players.accept(first, 5, 0, List.of("OldBot"), 6100);
        assertFalse(players.fresh(6100));

        int second = players.request(11000);
        assertNotEquals(first, second);
        players.accept(first, 5, 0, List.of("OldBot"), 11100);
        players.accept(0, 5, 0, List.of("ChatSuggestion"), 11100);
        players.accept(second, 5, 0, List.of("KitBot1"), 11100);
        assertEquals(List.of("KitBot1"), players.merge(List.of(), 11200));
    }

    @Test void resetInvalidatesNamesAndOutstandingResponsesWithoutReusingIds() {
        var players = new OnlinePlayers();
        int first = players.request(1000);
        players.accept(first, 5, 0, List.of("KitBot1"), 1100);
        int outstanding = players.request(11000);
        players.reset();

        assertEquals(List.of(), players.merge(List.of(), 11200));
        int next = players.request(11200);
        assertNotEquals(outstanding, next);
        players.accept(outstanding, 5, 0, List.of("OldServerBot"), 11300);
        assertFalse(players.fresh(11300));
        players.accept(next, 5, 0, List.of("NewServerBot"), 11300);
        assertEquals(List.of("NewServerBot"), players.merge(List.of(), 11400));
    }

    @Test void emptyMalformedAndOversizedResponsesDoNotConfirmOfflinePlayers() {
        var players = new OnlinePlayers();
        players.accept(players.request(1000), 0, 5, List.of("msg"), 1100);
        assertFalse(players.fresh(1100));
        players.accept(players.request(11000), 5, 1, List.of("KitBot1"), 11100);
        assertFalse(players.fresh(11100));
        players.accept(players.request(21000), 5, 0, Collections.nCopies(8193, "KitBot1"), 21100);
        assertFalse(players.fresh(21100));
        players.accept(players.request(31000), 5, 0, List.of("KitBot1"), 31100);
        players.accept(players.request(41000), 5, 0, List.of(), 41100);
        assertFalse(players.fresh(41100));
        assertEquals(List.of("Alice"), players.merge(List.of("Alice"), 41200));
    }
}
