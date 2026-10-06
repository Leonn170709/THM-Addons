/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.mixin;

import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xyz.thm.addon.modules.Surround;

@Mixin(ClientChunkCache.class)
public class ClientChunkCacheSurroundMixin {
    @Shadow @Final private ClientLevel level;

    @Inject(method = "replaceWithPacketData", at = @At("RETURN"))
    private void thm$confirmSurroundChunk(CallbackInfoReturnable<LevelChunk> ci) {
        Surround surround = Surround.INSTANCE;
        if (surround != null) surround.onServerChunkUpdate(level, ci.getReturnValue());
    }
}
