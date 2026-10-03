/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xyz.thm.addon.utils.webp.Vp8LDecoder;
import com.mojang.blaze3d.platform.NativeImage;
import java.nio.ByteBuffer;

/** Every NativeImage.read(...) overload funnels through read(Format, ByteBuffer); intercepting it here adds WebP support everywhere (nametag icons, capes, and any other texture). */
@Mixin(NativeImage.class)
public abstract class NativeImageWebpMixin {

    @Inject(method = "read(Lcom/mojang/blaze3d/platform/NativeImage$Format;Ljava/nio/ByteBuffer;)Lcom/mojang/blaze3d/platform/NativeImage;", at = @At("HEAD"), cancellable = true)
    private static void thm$readWebp(NativeImage.Format format, ByteBuffer buffer, CallbackInfoReturnable<NativeImage> cir) {
        NativeImage decoded = Vp8LDecoder.tryDecode(format, buffer);
        if (decoded != null) cir.setReturnValue(decoded);
    }
}
