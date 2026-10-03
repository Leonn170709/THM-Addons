/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xyz.thm.addon.utils.ChunkResync;

@Mixin(KeyboardHandler.class)
public abstract class KeyboardMixin {
    /** F3+A+S resyncs chunks from the server; cancels vanilla's F3+S texture dump for that press. */
    @Inject(method = "handleDebugKeys", at = @At("HEAD"), cancellable = true)
    private void thm$chunkResync(KeyEvent input, CallbackInfoReturnable<Boolean> cir) {
        if (input.key() != GLFW.GLFW_KEY_S) return;
        if (!InputConstants.isKeyDown(Minecraft.getInstance().getWindow(), GLFW.GLFW_KEY_A)) return;
        ChunkResync.trigger();
        cir.setReturnValue(true);
    }
}
