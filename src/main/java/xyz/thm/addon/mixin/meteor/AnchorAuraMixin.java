/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.mixin.meteor;

import meteordevelopment.meteorclient.systems.modules.combat.AnchorAura;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.thm.addon.system.THMSystem;
import xyz.thm.addon.utils.ThmMembers;

@Mixin(value = AnchorAura.class, remap = false)
public class AnchorAuraMixin {
    @Shadow private Player target;
    @Shadow private BlockPos renderBlockPos;

    @Inject(method = "onTick", at = @At(value = "INVOKE", target = "Lmeteordevelopment/meteorclient/systems/modules/combat/AnchorAura;doAnchorAura()V"), cancellable = true)
    private void thm$ignoreThmMembers(CallbackInfo ci) {
        THMSystem system = THMSystem.get();
        if (system == null || !system.ignoreThmMembers.get()) return;
        if (ThmMembers.isThmMember(target)) {
            renderBlockPos = null;
            target = null;
            ci.cancel();
        }
    }
}
