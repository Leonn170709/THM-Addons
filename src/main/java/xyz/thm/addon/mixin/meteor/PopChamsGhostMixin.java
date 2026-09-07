package xyz.thm.addon.mixin.meteor;

import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.render.PopChams;
import meteordevelopment.meteorclient.utils.render.WireframeEntityRenderer;
import meteordevelopment.meteorclient.utils.render.color.Color;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import xyz.thm.addon.utils.render.GhostRenderer;

@Mixin(targets = "meteordevelopment.meteorclient.systems.modules.render.PopChams$GhostPlayer", remap = false)
public class PopChamsGhostMixin {
    @Unique private static Setting<Boolean> thm$renderSkin;
    @Unique private static Setting<Boolean> thm$throughWalls;

    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Lmeteordevelopment/meteorclient/utils/render/WireframeEntityRenderer;render"))
    private void thm$renderGhost(Render3DEvent event, Entity entity, double scale, Color sideColor, Color lineColor, ShapeMode shapeMode) {
        if (thm$renderSkin == null) {
            thm$renderSkin = Modules.get().get(PopChams.class).settings.get("render-skin", Boolean.class);
            thm$throughWalls = Modules.get().get(PopChams.class).settings.get("skin-through-walls", Boolean.class);
        }

        if (thm$renderSkin == null || !thm$renderSkin.get()) WireframeEntityRenderer.render(event, entity, scale, sideColor, lineColor, shapeMode);
        else if (thm$throughWalls.get()) GhostRenderer.renderThroughWalls(event, entity, scale);
        else GhostRenderer.submit(entity, scale);
    }
}
