/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.mixin;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.thm.addon.modules.Speedmine;
import xyz.thm.addon.modules.Surround;

@Mixin(ClientLevel.class)
public class ClientLevelBlockUpdateMixin {
    @Inject(method = "setServerVerifiedBlockState", at = @At("HEAD"))
    private void thm$beforeServerBlockUpdate(BlockPos pos, BlockState state, int flags, CallbackInfo ci) {
        Surround surround = Surround.INSTANCE;
        if (surround != null) surround.onServerBlockUpdate((ClientLevel) (Object) this, pos, state);
        Speedmine speedmine = Speedmine.INSTANCE;
        if (speedmine != null) speedmine.onServerBlockUpdate((ClientLevel) (Object) this, pos, state);
    }
}
