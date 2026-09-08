/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.mixin.meteor;

import meteordevelopment.meteorclient.systems.modules.combat.KillAura;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xyz.thm.addon.system.THMSystem;
import xyz.thm.addon.utils.ThmMembers;

@Mixin(value = KillAura.class, remap = false)
public abstract class KillAuraMixin {
    @Inject(method = "entityCheck", at = @At("HEAD"), cancellable = true)
    private void thm$ignoreThmMembers(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (!(entity instanceof PlayerEntity player)) return;

        THMSystem system = THMSystem.get();
        if (system != null && system.ignoreThmMembers.get() && ThmMembers.isThmMember(player)) cir.setReturnValue(false);
    }
}
