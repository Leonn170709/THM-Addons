/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.mixin.meteor;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.entity.player.PlayerMoveEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.systems.modules.movement.speed.Speed;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Speed.class, remap = false)
public class SpeedMixin {
    @Inject(method = "onPlayerMove", at = @At("HEAD"), cancellable = true)
    private void thm$guardNullPlayerMove(PlayerMoveEvent event, CallbackInfo ci) {
        if (MeteorClient.mc.player == null || MeteorClient.mc.level == null) ci.cancel();
    }

    @Inject(method = "onPreTick", at = @At("HEAD"), cancellable = true)
    private void thm$guardNullPreTick(TickEvent.Pre event, CallbackInfo ci) {
        if (MeteorClient.mc.player == null || MeteorClient.mc.level == null) ci.cancel();
    }
}
