package xyz.thm.addon.mixin;

import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.thm.addon.mixin.accessor.CameraAccessor;
import xyz.thm.addon.modules.HighwayBuilderTHM;

@Mixin(Entity.class)
public abstract class HighwayBuilderEntityMixin {
    @Inject(method = "changeLookDirection", at = @At("HEAD"), cancellable = true)
    private void thm$integratedFreelook(double cursorDeltaX, double cursorDeltaY, CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if ((Object) this != mc.player) return;

        HighwayBuilderTHM highwayBuilder = Modules.get().get(HighwayBuilderTHM.class);
        if (highwayBuilder == null || !highwayBuilder.isActive()) return;

        Camera camera = mc.gameRenderer.getCamera();
        float nextYaw = MathHelper.wrapDegrees((float) (camera.getYaw() + cursorDeltaX * 0.15));
        float nextPitch = MathHelper.clamp((float) (camera.getPitch() + cursorDeltaY * 0.15), -90.0f, 90.0f);
        ((CameraAccessor) camera).thm$setRotation(nextYaw, nextPitch);

        ci.cancel();
    }
}
