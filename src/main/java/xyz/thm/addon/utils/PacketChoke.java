/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.utils;

import java.util.ArrayDeque;
import java.util.function.Consumer;

public final class PacketChoke<T> {
    private final ArrayDeque<T> queued = new ArrayDeque<>();
    private volatile boolean holding = true;
    private volatile boolean flushing;
    private volatile Thread flushingThread;
    private int phaseTicks;

    public synchronized boolean hold(T packet) {
        if (flushing && Thread.currentThread() == flushingThread) return false;
        if (!holding && !flushing) return false;
        queued.addLast(packet);
        return true;
    }

    public void tick(int holdTicks, int releaseTicks, int maxHoldTicks, Consumer<T> send) {
        boolean release = false;
        synchronized (this) {
            if (holding) {
                if (++phaseTicks >= Math.min(holdTicks, maxHoldTicks)) {
                    holding = false;
                    phaseTicks = 0;
                    release = true;
                }
            } else {
                release = !queued.isEmpty();
                if (++phaseTicks >= releaseTicks) {
                    holding = true;
                    phaseTicks = 0;
                }
            }
        }
        if (release) flush(send);
    }

    public void flush(Consumer<T> send) {
        if (flushing) return;
        flushingThread = Thread.currentThread();
        flushing = true;
        try {
            while (true) {
                T packet;
                synchronized (this) {
                    packet = queued.peekFirst();
                }
                if (packet == null) return;
                send.accept(packet);
                synchronized (this) {
                    queued.removeFirst();
                }
            }
        } finally {
            flushing = false;
            flushingThread = null;
        }
    }

    public synchronized void clear() {
        queued.clear();
        holding = true;
        phaseTicks = 0;
    }

    public boolean isHolding() {
        return holding;
    }

    public boolean isFlushing() {
        return flushing;
    }
}
