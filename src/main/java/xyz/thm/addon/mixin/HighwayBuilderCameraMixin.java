/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.mixin;

import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.render.FreeLook;
import meteordevelopment.meteorclient.systems.modules.render.Freecam;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
import xyz.thm.addon.modules.HighwayBuilderTHM;
import xyz.thm.addon.modules.THMHwyMonitor;

@Mixin(Camera.class)
public abstract class HighwayBuilderCameraMixin {
    @Shadow private float yRot;
    @Shadow private float xRot;

    @ModifyArgs(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/Camera;setRotation(FF)V"))
    private void thm$lockCameraToFreelookState(Args args) {
        HighwayBuilderTHM highwayBuilder = Modules.get().get(HighwayBuilderTHM.class);
        THMHwyMonitor highwayMonitor = Modules.get().get(THMHwyMonitor.class);
        boolean highwayBuilderOwnsControl = highwayBuilder != null && highwayBuilder.isActive();
        boolean highwayMonitorOwnsControl = highwayMonitor != null && highwayMonitor.ownsIntegratedFreelookControl();
        if (!highwayBuilderOwnsControl && !highwayMonitorOwnsControl) return;

        Freecam freecam = Modules.get().get(Freecam.class);
        if (freecam != null && freecam.isActive()) return;

        FreeLook freeLook = Modules.get().get(FreeLook.class);
        if (freeLook != null && freeLook.isActive()) return;

        // Preserve camera rotation set by HighwayBuilder's freelook.
        args.set(0, yRot);
        args.set(1, xRot);
    }
}
