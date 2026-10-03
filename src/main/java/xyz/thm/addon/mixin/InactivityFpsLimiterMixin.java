/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.mixin;

import com.mojang.blaze3d.platform.FramerateLimitTracker;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Makes the "Never" value added by {@link InactivityFpsLimitMixin} actually do something. Vanilla
 * only consults the option for its AFK stages, so minimized and the out-of-level menu cap would
 * still apply; {@code update()} is a pure switch over {@code getLimitReason()}, so forcing NONE there
 * covers every reduction path at once and keeps the F3 readout honest.
 */
@Mixin(FramerateLimitTracker.class)
public class InactivityFpsLimiterMixin {
    @Inject(method = "getThrottleReason", at = @At("HEAD"), cancellable = true)
    private void thm$neverReduceFps(CallbackInfoReturnable<FramerateLimitTracker.FramerateThrottleReason> cir) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options == null) return;
        if ("never".equals(mc.options.inactivityFpsLimit().get().getSerializedName())) {
            cir.setReturnValue(FramerateLimitTracker.FramerateThrottleReason.NONE);
        }
    }
}
