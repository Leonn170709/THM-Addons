/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.mixin;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import xyz.thm.addon.THMAddon;
import xyz.thm.addon.gui.MainMenuFx;
import xyz.thm.addon.gui.ThmStyledButtons;

@Mixin(ImageButton.class)
public abstract class ImageButtonStyleMixin {
    @Unique private boolean thm$styleLogged;
    @Redirect(method = "extractContents", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V"))
    private void thm$styleImageButton(GuiGraphicsExtractor context, RenderPipeline pipeline, Identifier sprite, int x, int y, int width, int height) {
        ImageButton button = (ImageButton) (Object) this;
        if (ThmStyledButtons.isStyled(button)) {
            if (!thm$styleLogged) {
                thm$styleLogged = true;
                THMAddon.LOG.info("[THM/Menu] ImageButtonStyleMixin style applied; widget={}", button.getClass().getName());
            }
            MainMenuFx.renderButton(context, Minecraft.getInstance().font, x, y, x + width, y + height,
                "", button.isHoveredOrFocused(), button.active);
            // The vanilla frame is part of the sprite; crop it rather than shrinking it.
            context.blitSprite(pipeline, sprite, width, height, 2, 2, x + 2, y + 2, width - 4, height - 4);
        } else {
            context.blitSprite(pipeline, sprite, x, y, width, height);
        }
    }
}
