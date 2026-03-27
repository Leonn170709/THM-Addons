package xyz.thm.addon.mixin;

import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.render.FreeLook;
import meteordevelopment.meteorclient.systems.modules.render.Freecam;
import net.minecraft.client.render.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
import xyz.thm.addon.modules.HighwayBuilderTHM;

@Mixin(Camera.class)
public abstract class HighwayBuilderCameraMixin {
    @Shadow private float yaw;
    @Shadow private float pitch;

    @ModifyArgs(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/Camera;setRotation(FF)V"))
    private void thm$lockCameraToFreelookState(Args args) {
        HighwayBuilderTHM highwayBuilder = Modules.get().get(HighwayBuilderTHM.class);
        if (highwayBuilder == null || !highwayBuilder.isActive()) return;

        Freecam freecam = Modules.get().get(Freecam.class);
        if (freecam != null && freecam.isActive()) return;

        FreeLook freeLook = Modules.get().get(FreeLook.class);
        if (freeLook != null && freeLook.isActive()) return;

        // Preserve camera rotation set by HighwayBuilder's freelook.
        args.set(0, yaw);
        args.set(1, pitch);
    }
}
