/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.mixin.sodium;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Sodium-side equivalent of {@code EdgeRenderMixin}. Sodium tracks, per chunk column, whether
 * every column in its 3x3 neighborhood has both block and light data before marking it "ready"
 * to build/render — same idea as vanilla's isChunkNonEmpty check that EdgeRenderMixin bypasses,
 * just reimplemented inside Sodium's own renderer (which never goes through vanilla's
 * ChunkBuilder, so that mixin has no effect once Sodium is installed). At the outer ring of
 * render distance the one-out neighbor never finishes loading, so that column is never marked
 * ready and its section stays permanently unrendered. Only loads when Sodium's ChunkTracker
 * class is actually present ("required": false in thm-addon.sodium.mixins.json).
 */
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.map.ChunkTracker", remap = false)
public abstract class SodiumEdgeRenderMixin {
    @Shadow
    private LongOpenHashSet chunkReady;
    @Shadow
    private LongSet unloadQueue;
    @Shadow
    private LongSet loadQueue;

    @Inject(method = "updateMerged(II)V", at = @At("HEAD"), cancellable = true)
    private void thm$forceReady(int x, int z, CallbackInfo ci) {
        long key = ChunkPos.asLong(x, z);
        if (this.chunkReady.add(key) && !this.unloadQueue.remove(key)) {
            this.loadQueue.add(key);
        }
        ci.cancel();
    }
}
