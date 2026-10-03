/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.ThreadPoolExecutor;

@Pseudo
@Mixin(targets = "baritone.Baritone", remap = false)
public abstract class BaritoneWorkerMixin {
    @Shadow(aliases = "a") @Final
    private static ThreadPoolExecutor threadPool;

    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void thm$daemonWorkers(CallbackInfo ci) {
        // Permanent cache workers otherwise trigger Minecraft's shutdown watchdog.
        var factory = threadPool.getThreadFactory();
        threadPool.setThreadFactory(task -> {
            Thread thread = factory.newThread(task);
            thread.setDaemon(true);
            return thread;
        });
    }
}
