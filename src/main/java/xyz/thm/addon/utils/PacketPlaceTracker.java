/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.utils;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/**
 * Packet placing without client prediction leaves a sent block as air here until the server answers, so
 * callers would re-send it every tick. This keeps one packet per block: a block is only sent again once
 * the server reports it as still air. When the server stays silent it is asked with an inert
 * use-on-block (empty hand, pickaxe, totem), which can't place anything. Re-sending on a guess instead
 * makes air-place stack a block on top of the one that did go through.
 */
public final class PacketPlaceTracker {
    /** Default wait before asking the server about an unanswered block. */
    public static final int DEFAULT_RESEND_TICKS = 20;

    private static final PacketPlaceTracker INSTANCE = new PacketPlaceTracker();

    private final Long2LongOpenHashMap sentAt = new Long2LongOpenHashMap();
    private final Long2IntOpenHashMap resendTicks = new Long2IntOpenHashMap();
    // Filled from the netty thread, drained on tick.
    private final ConcurrentLinkedQueue<Long> answered = new ConcurrentLinkedQueue<>();
    private volatile boolean listening;

    private PacketPlaceTracker() {}

    public static void init() {
        MeteorClient.EVENT_BUS.subscribe(INSTANCE);
    }

    /** False while a packet for this block is still waiting for the server's answer. */
    public static boolean canSend(BlockPos pos) {
        return !INSTANCE.sentAt.containsKey(pos.asLong());
    }

    public static void markSent(BlockPos pos, int resendTicks) {
        if (mc.level == null) return;
        long key = pos.asLong();
        INSTANCE.sentAt.put(key, mc.level.getGameTime());
        INSTANCE.resendTicks.put(key, Math.max(1, resendTicks));
        INSTANCE.listening = true;
    }

    public static void forget(BlockPos pos) {
        if (pos == null) return;
        long key = pos.asLong();
        INSTANCE.forget(key);
        INSTANCE.answered.removeIf(answer -> answer == key);
        INSTANCE.listening = !INSTANCE.sentAt.isEmpty();
    }

    @EventHandler
    private void onReceive(PacketEvent.Receive event) {
        if (!listening) return;
        if (event.packet instanceof ClientboundBlockUpdatePacket update) {
            answered.add(update.getPos().asLong());
        } else if (event.packet instanceof ClientboundSectionBlocksUpdatePacket delta) {
            delta.runUpdates((pos, state) -> answered.add(pos.asLong()));
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        for (Long pos; (pos = answered.poll()) != null; ) forget(pos);
        if (sentAt.isEmpty() || mc.level == null || mc.player == null) {
            listening = !sentAt.isEmpty();
            return;
        }

        long now = mc.level.getGameTime();
        List<BlockPos> ask = null;
        var it = sentAt.long2LongEntrySet().fastIterator();
        while (it.hasNext()) {
            var entry = it.next();
            long key = entry.getLongKey();
            BlockPos pos = BlockPos.of(key);
            if (!mc.level.getBlockState(pos).canBeReplaced()) {
                it.remove();
                resendTicks.remove(key);
                continue;
            }
            if (now >= entry.getLongValue() && now - entry.getLongValue() < resendTicks.get(key)) continue;

            if (inertHand() == null) {
                // Nothing safe to ask with: let it be placed again rather than stall.
                it.remove();
                resendTicks.remove(key);
                continue;
            }
            if (ask == null) ask = new ArrayList<>();
            ask.add(pos);
            entry.setValue(now);
        }
        if (ask != null) sendProbes(ask);
        listening = !sentAt.isEmpty();
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        sentAt.clear();
        resendTicks.clear();
        answered.clear();
        listening = false;
    }

    private void forget(long key) {
        sentAt.remove(key);
        resendTicks.remove(key);
    }

    /**
     * Asks the server for the real state of these blocks: it answers every in-reach use-on-block with the
     * hit block and the block on the hit side, so two neighbours share one packet.
     */
    public static void sendProbes(Collection<BlockPos> targets) {
        InteractionHand hand = inertHand();
        if (hand == null || mc.getConnection() == null) return;
        for (GhostBlockProbe.Probe probe : GhostBlockProbe.plan(targets)) {
            Direction side = probe.side();
            Vec3 hit = Vec3.atCenterOf(probe.pos()).add(side.getStepX() * 0.5, side.getStepY() * 0.5, side.getStepZ() * 0.5);
            // Sequence 0 is never a pending client prediction, so the ack changes nothing client-side.
            mc.getConnection().send(new ServerboundUseItemOnPacket(hand, new BlockHitResult(hit, side, probe.pos(), false), 0));
        }
    }

    /** A hand whose item does nothing when used on a block, so a probe can't place or strip anything. */
    public static InteractionHand inertHand() {
        if (mc.player == null) return null;
        if (isInert(mc.player.getOffhandItem())) return InteractionHand.OFF_HAND;
        if (isInert(mc.player.getMainHandItem())) return InteractionHand.MAIN_HAND;
        return null;
    }

    private static boolean isInert(ItemStack stack) {
        return stack.isEmpty() || stack.is(ItemTags.PICKAXES) || stack.is(Items.TOTEM_OF_UNDYING);
    }
}
