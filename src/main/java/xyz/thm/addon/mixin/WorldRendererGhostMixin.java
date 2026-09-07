package xyz.thm.addon.mixin;

import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.state.WorldRenderState;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.thm.addon.utils.render.GhostRenderer;

@Mixin(WorldRenderer.class)
public class WorldRendererGhostMixin {
    @Inject(method = "pushEntityRenders", at = @At("TAIL"))
    private void thm$drawGhosts(MatrixStack matrices, WorldRenderState state, OrderedRenderCommandQueue queue, CallbackInfo ci) {
        GhostRenderer.drawQueued(matrices, queue);
    }
}
