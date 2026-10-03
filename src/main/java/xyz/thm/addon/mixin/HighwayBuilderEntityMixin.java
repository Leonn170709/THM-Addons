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
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.thm.addon.mixin.accessor.CameraAccessor;
import xyz.thm.addon.modules.HighwayBuilderTHM;
import xyz.thm.addon.modules.THMHwyMonitor;

@Mixin(Entity.class)
public abstract class HighwayBuilderEntityMixin {
    @Inject(method = "turn", at = @At("HEAD"), cancellable = true)
    private void thm$integratedFreelook(double cursorDeltaX, double cursorDeltaY, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if ((Object) this != mc.player) return;

        HighwayBuilderTHM highwayBuilder = Modules.get().get(HighwayBuilderTHM.class);
        THMHwyMonitor highwayMonitor = Modules.get().get(THMHwyMonitor.class);
        boolean highwayBuilderOwnsControl = highwayBuilder != null && highwayBuilder.isActive();
        boolean highwayMonitorOwnsControl = highwayMonitor != null && highwayMonitor.ownsIntegratedFreelookControl();
        if (!highwayBuilderOwnsControl && !highwayMonitorOwnsControl) return;

        FreeLook freeLook = Modules.get().get(FreeLook.class);
        if (freeLook != null && freeLook.isActive()) return;

        Freecam freecam = Modules.get().get(Freecam.class);
        if (freecam != null && freecam.isActive()) return;

        Camera camera = mc.gameRenderer.getMainCamera();
        float nextYaw = Mth.wrapDegrees((float) (camera.yRot() + cursorDeltaX * 0.15));
        float nextPitch = Mth.clamp((float) (camera.xRot() + cursorDeltaY * 0.15), -90.0f, 90.0f);
        ((CameraAccessor) camera).thm$setRotation(nextYaw, nextPitch);

        ci.cancel();
    }
}
