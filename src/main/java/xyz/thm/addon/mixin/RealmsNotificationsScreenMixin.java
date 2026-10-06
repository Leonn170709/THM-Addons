/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.mixin;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.realmsclient.gui.screens.RealmsNotificationsScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.thm.addon.gui.MainMenuFx;
import xyz.thm.addon.system.THMSystem;

@Mixin(RealmsNotificationsScreen.class)
public abstract class RealmsNotificationsScreenMixin {
    @Unique private int thm$badgeOffset;
    @Inject(method = "extractIcons", at = @At("HEAD"), cancellable = true)
    private void thm$hidePreviewBadges(GuiGraphicsExtractor context, CallbackInfo ci) {
        thm$badgeOffset = 0;
        if (MainMenuFx.previewMode) ci.cancel();
    }

    @Redirect(method = "extractIcons", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V"))
    private void thm$positionBadge(GuiGraphicsExtractor context, RenderPipeline pipeline, Identifier sprite, int x, int y, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        if (THMSystem.get().mainMenuWindow.get() && mc.gui.screen() instanceof TitleScreen screen) {
            AbstractWidget realms = null;
            for (var child : screen.children()) {
                if (child instanceof Button widget && widget.getMessage().getString().equals(I18n.get("menu.online"))) {
                    realms = widget;
                    break;
                }
            }
            if (realms != null) {
                x = realms.getX() + realms.getWidth() - 4 - width - thm$badgeOffset;
                y = realms.getY() + (realms.getHeight() - height) / 2;
                thm$badgeOffset += width + 4;
                MainMenuFx.renderButton(context, mc.font, x - 1, y - 1, x + width + 1, y + height + 1, "", false, true);
            }
        }
        context.blitSprite(pipeline, sprite, x, y, width, height);
    }
}
