/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.mixin;

import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.movement.NoSlow;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xyz.thm.addon.interfaces.NoSlowAntiClimb;

import static meteordevelopment.meteorclient.MeteorClient.mc;

@Mixin(LivingEntity.class)
public abstract class LivingEntityClimbMixin {
    /** NoSlow anti-climb: ladders, vines and scaffolding no longer lift or catch you. Local player only. */
    @Inject(method = "isClimbing", at = @At("HEAD"), cancellable = true)
    private void thm$antiClimb(CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this != mc.player) return;
        NoSlow noSlow = Modules.get() == null ? null : Modules.get().get(NoSlow.class);
        if (noSlow != null && noSlow.isActive() && ((NoSlowAntiClimb) noSlow).thm$antiClimb()) cir.setReturnValue(false);
    }
}
