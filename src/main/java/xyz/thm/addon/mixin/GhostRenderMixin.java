/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.mixin;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xyz.thm.addon.interfaces.GhostPose;
import xyz.thm.addon.utils.render.GhostRenderer;

/** Ghost players (logout spots, pop chams): frozen limb pose and a see-through skin. */
@Mixin(LivingEntityRenderer.class)
public abstract class GhostRenderMixin<T extends LivingEntity, S extends LivingEntityRenderState> {
    @Shadow public abstract Identifier getTexture(S state);

    @Inject(method = "updateRenderState(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;F)V", at = @At("TAIL"))
    private void thm$ghostLimbs(T entity, S state, float tickProgress, CallbackInfo ci) {
        if (!(entity instanceof GhostPose ghost)) return;

        state.limbSwingAnimationProgress = ghost.thm$limbPos();
        state.limbSwingAmplitude = ghost.thm$limbAmplitude();
    }

    @Inject(method = "getRenderLayer", at = @At("RETURN"), cancellable = true)
    private void thm$ghostTranslucent(S state, boolean showBody, boolean translucent, boolean showOutline, CallbackInfoReturnable<RenderLayer> cir) {
        RenderLayer layer = cir.getReturnValue();
        // Cutout layers ignore vertex alpha; entityTranslucent is the no-cull counterpart, and the model's
        // mirrored left limbs lose their faces on a culling layer.
        if (!GhostRenderer.isFading() || layer == null || layer.isTranslucent()) return;

        cir.setReturnValue(RenderLayers.entityTranslucent(getTexture(state)));
    }
}
