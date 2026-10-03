/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.mixin.xaero;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.EmptyLevelChunk;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Same fix as {@link XaeroMinimapEdgeMixin}, for the world map: {@code MapWriter#writeChunk} skips
 * the chunk outright when any of its 3x3 neighbors is null or an EmptyChunk.
 */
@Mixin(targets = "xaero.map.MapWriter", remap = false)
public abstract class XaeroWorldMapEdgeMixin {
    @Redirect(
        method = "writeChunk",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getChunk(II)Lnet/minecraft/world/level/chunk/LevelChunk;", remap = true)
    )
    private LevelChunk thm$neighborAlwaysLoaded(Level world, int x, int z) {
        LevelChunk chunk = world.getChunk(x, z);
        if (!(chunk instanceof EmptyLevelChunk)) return chunk;

        Player player = Minecraft.getInstance().player;
        if (player == null) return chunk;
        return world.getChunk(player.chunkPosition().x(), player.chunkPosition().z());
    }
}
