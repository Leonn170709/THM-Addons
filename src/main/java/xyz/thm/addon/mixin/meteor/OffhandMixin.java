/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.mixin.meteor;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.systems.modules.combat.Offhand;
import meteordevelopment.meteorclient.systems.modules.Modules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.thm.addon.modules.HighwayBuilderTHM;
import xyz.thm.addon.utils.InventoryManager;

/**
 * Offhand's swap is a two-click {@code PICKUP} pair, which only behaves if the cursor is empty when
 * it starts and gets emptied again when it ends. Meteor guarantees neither: with something already
 * on the cursor the first click *drops* it into the source slot instead of picking up (wrong item in
 * the wrong slot, old offhand item stranded on the cursor), and its own recovery in
 * {@code InvUtils.Action#run} is gated on the cursor having started empty, so that case is never
 * cleaned up. This clears the cursor on both ends instead of leaving it stuck.
 */
@Mixin(value = Offhand.class, remap = false)
public class OffhandMixin {
    @Inject(method = "onTick", at = @At("HEAD"), cancellable = true)
    private void thm$clearCursorBefore(TickEvent.Pre event, CallbackInfo ci) {
        if (MeteorClient.mc.player == null) {
            ci.cancel();
            return;
        }
        HighwayBuilderTHM builder = Modules.get().get(HighwayBuilderTHM.class);
        if (builder != null && builder.isActive() && builder.noSwapLoadout.get()) {
            ci.cancel();
            return;
        }
        if (MeteorClient.mc.player.currentScreenHandler.getCursorStack().isEmpty()) return;

        // With a screen open the player is dragging that stack — theirs, don't touch it, just sit out.
        // Otherwise it is a leftover from a desynced swap: park it so the module isn't stuck forever.
        if (MeteorClient.mc.currentScreen != null || !InventoryManager.parkCursor()) ci.cancel();
    }

    @Inject(method = "onTick", at = @At("RETURN"))
    private void thm$clearCursorAfter(TickEvent.Pre event, CallbackInfo ci) {
        // Cursor was empty above, so anything on it now is the swap's own leftover. Never ours to drop:
        // a failed park just leaves it for the next tick's HEAD to retry.
        if (!MeteorClient.mc.player.currentScreenHandler.getCursorStack().isEmpty()) InventoryManager.parkCursor();
    }
}
