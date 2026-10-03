/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.mixin;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.thm.addon.gui.themes.ThmTheme;
import xyz.thm.addon.shaders.ShaderBackground;

/**
 * Joke "I'm high" effect. At the tail of GameRenderer#render the whole frame - world, HUD and any
 * open screen (including Meteor's GUI) - is already composited onto the main framebuffer, before
 * it's blit to the window, so a full-framebuffer post pass here warps everything, the Meteor tab
 * included (which vanilla's nausea shader can't touch). Gated on ThmTheme's "im-high" toggle.
 */
@Mixin(GameRenderer.class)
public abstract class ImHighMixin {
    @Inject(method = "render(Lnet/minecraft/client/DeltaTracker;Z)V", at = @At("TAIL"))
    private void thm$imHigh(DeltaTracker tickCounter, boolean tick, CallbackInfo ci) {
        if (ThmTheme.isHigh()) ShaderBackground.renderTrip(1.0f);
    }
}
