/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.mixin;

import net.minecraft.client.render.command.OrderedRenderCommandQueueImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import xyz.thm.addon.utils.render.GhostRenderer;

/** Last stop before the tint is baked in, so Meteor's Chams color fades along with the ghost. */
@Mixin(OrderedRenderCommandQueueImpl.class)
public class GhostAlphaMixin {
    @ModifyVariable(method = "submitModel", at = @At("HEAD"), argsOnly = true, ordinal = 2)
    private int thm$fadeGhost(int color) {
        if (!GhostRenderer.isFading()) return color;

        int a = (int) (((color >>> 24) & 0xFF) * GhostRenderer.alpha());
        return (color & 0xFFFFFF) | (a << 24);
    }
}
