/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.LevelRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.thm.addon.utils.render.GhostRenderer;

@Mixin(LevelRenderer.class)
public class WorldRendererGhostMixin {
    @Inject(method = "submitEntities", at = @At("TAIL"))
    private void thm$drawGhosts(PoseStack matrices, LevelRenderState state, SubmitNodeCollector queue, CallbackInfo ci) {
        GhostRenderer.drawQueued(matrices, queue);
    }
}
