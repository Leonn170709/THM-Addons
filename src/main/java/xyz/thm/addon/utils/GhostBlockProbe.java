/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.utils;

import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Asks the server for the real state of blocks the client thinks are solid: the server answers every
 * in-reach use-on-block packet with a block update for the hit block and the block on the hit side.
 * Main thread only.
 */
public final class GhostBlockProbe {
    public enum Result { IDLE, WAITING, VERIFIED, GHOST, TIMEOUT }

    /** One use-on-block packet; the server replies for {@code pos} and {@code pos.offset(side)}. */
    public record Probe(BlockPos pos, Direction side) {
        public BlockPos other() {
            return pos.offset(side);
        }
    }

    private final int timeoutTicks;
    private final Long2LongOpenHashMap pending = new Long2LongOpenHashMap();
    private long verifiedRow = Long.MIN_VALUE;
    private long probingRow = Long.MIN_VALUE;
    private boolean ghostSeen;

    public GhostBlockProbe(int timeoutTicks) {
        this.timeoutTicks = timeoutTicks;
    }

    /** Pairs adjacent targets so one packet covers two of them. */
    public static List<Probe> plan(Collection<BlockPos> targets) {
        Set<BlockPos> left = new LinkedHashSet<>();
        for (BlockPos pos : targets) left.add(pos.toImmutable());

        List<Probe> probes = new ArrayList<>();
        while (!left.isEmpty()) {
            BlockPos pos = left.iterator().next();
            left.remove(pos);
            Direction side = Direction.UP;
            for (Direction d : Direction.values()) {
                if (left.remove(pos.offset(d))) {
                    side = d;
                    break;
                }
            }
            probes.add(new Probe(pos, side));
        }
        return probes;
    }

    public void reset() {
        pending.clear();
        verifiedRow = Long.MIN_VALUE;
        probingRow = Long.MIN_VALUE;
        ghostSeen = false;
    }

    public boolean isVerified(long row) {
        return verifiedRow != Long.MIN_VALUE && verifiedRow >= row;
    }

    public boolean isProbing() {
        return !pending.isEmpty();
    }

    /** Start waiting on {@code targets} for {@code row}. An empty target list verifies the row at once. */
    public void start(long row, Collection<BlockPos> targets, long now) {
        pending.clear();
        ghostSeen = false;
        probingRow = row;
        for (BlockPos pos : targets) pending.put(pos.asLong(), now);
        if (pending.isEmpty()) verifiedRow = Math.max(verifiedRow, row);
    }

    /** Feed every block update from the server; {@code solid} is the server's state. */
    public void onBlockUpdate(long pos, boolean solid) {
        if (pending.isEmpty() || !pending.containsKey(pos)) return;
        pending.remove(pos);
        if (!solid) ghostSeen = true;
    }

    /** Positions still unanswered, for a resend after {@link Result#TIMEOUT}. */
    public List<BlockPos> unanswered(long now) {
        List<BlockPos> out = new ArrayList<>(pending.size());
        for (var e : pending.long2LongEntrySet()) {
            out.add(BlockPos.fromLong(e.getLongKey()));
            e.setValue(now);
        }
        return out;
    }

    public Result tick(long now) {
        if (probingRow == Long.MIN_VALUE) return Result.IDLE;

        if (pending.isEmpty()) {
            long row = probingRow;
            probingRow = Long.MIN_VALUE;
            if (ghostSeen) return Result.GHOST;
            verifiedRow = Math.max(verifiedRow, row);
            return Result.VERIFIED;
        }

        for (long sent : pending.values()) {
            if (now - sent >= timeoutTicks) return Result.TIMEOUT;
        }
        return Result.WAITING;
    }
}
