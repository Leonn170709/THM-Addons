/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.mixin.accessor;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.renderer.rendertype.LayeringTransform;
import net.minecraft.client.renderer.rendertype.OutputTarget;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.TextureTransform;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.Map;

@Mixin(RenderSetup.class)
public interface RenderSetupAccessor {
    @Accessor("textures") Map<String, ?> thm$getTextures();
    @Accessor("useLightmap") boolean thm$usesLightmap();
    @Accessor("useOverlay") boolean thm$usesOverlay();
    @Accessor("layeringTransform") LayeringTransform thm$getLayering();
    @Accessor("outputTarget") OutputTarget thm$getOutput();
    @Accessor("textureTransform") TextureTransform thm$getTextureTransform();
    @Accessor("outlineProperty") RenderSetup.OutlineProperty thm$getOutline();
    @Accessor("affectsCrumbling") boolean thm$affectsCrumbling();
    @Accessor("sortOnUpload") boolean thm$sortsOnUpload();

    @Invoker("<init>")
    static RenderSetup thm$create(RenderPipeline pipeline, Map<String, ?> textures, boolean lightmap,
                                 boolean overlay, LayeringTransform layering, OutputTarget output,
                                 TextureTransform textureTransform, RenderSetup.OutlineProperty outline,
                                 boolean crumbling, boolean sort) {
        throw new AssertionError();
    }

    default RenderSetup thm$withPipeline(RenderPipeline pipeline) {
        return thm$create(pipeline, thm$getTextures(), thm$usesLightmap(), thm$usesOverlay(), thm$getLayering(),
            thm$getOutput(), thm$getTextureTransform(), thm$getOutline(), thm$affectsCrumbling(), thm$sortsOnUpload());
    }
}
