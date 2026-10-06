/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.SpriteIconButton;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.thm.addon.THMAddon;
import xyz.thm.addon.gui.MainMenuFx;
import xyz.thm.addon.gui.ThmStyledButtons;

// Styles marked menu buttons while preserving native icon contents.
@Mixin(AbstractButton.class)
public abstract class ButtonWidgetStyleMixin extends AbstractWidget {
    @Unique private int thm$styleLogState = -1;
    @Unique private boolean thm$iconHookLogged;


    protected ButtonWidgetStyleMixin(int x, int y, int width, int height, Component message) {
        super(x, y, width, height, message);
    }

    @Inject(method = "extractWidgetRenderState", at = @At("HEAD"), cancellable = true)
    private void thm$renderThmStyle(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks, CallbackInfo ci) {
        boolean styled = ThmStyledButtons.isStyled(this);
        boolean nativeContents = this.getWidth() <= 40 || (Object) this instanceof SpriteIconButton || (Object) this instanceof ImageButton;
        int state = !styled ? 0 : nativeContents ? 1 : 2;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null && state != thm$styleLogState) {
            thm$styleLogState = state;
            THMAddon.LOG.info("[THM/Menu] ButtonWidgetStyleMixin hook; screen={}, widget={}, styled={}, route={}",
                mc.gui.screen() == null ? "<none>" : mc.gui.screen().getClass().getName(), this.getClass().getName(), styled,
                state == 0 ? "vanilla unmarked" : state == 1 ? "preserve native contents" : "THM text button");
        }
        if (!styled || nativeContents) return;

        MainMenuFx.renderButton(context, Minecraft.getInstance().font,
            this.getX(), this.getY(), this.getX() + this.getWidth(), this.getY() + this.getHeight(),
            this.getMessage().getString(), this.isHoveredOrFocused(), this.active);
        this.handleCursor(context);
        ci.cancel();
    }
    @Inject(method = "extractDefaultSprite", at = @At("HEAD"), cancellable = true)
    private void thm$styleIconBackground(GuiGraphicsExtractor context, CallbackInfo ci) {
        if (!ThmStyledButtons.isStyled(this) || (this.getWidth() > 40 && !((Object) this instanceof SpriteIconButton))) return;
        if (!thm$iconHookLogged) {
            thm$iconHookLogged = true;
            THMAddon.LOG.info("[THM/Menu] ButtonWidgetStyleMixin icon background applied; widget={}", this.getClass().getName());
        }
        MainMenuFx.renderButton(context, Minecraft.getInstance().font,
            this.getX(), this.getY(), this.getX() + this.getWidth(), this.getY() + this.getHeight(),
            "", this.isHoveredOrFocused(), this.active);
        ci.cancel();
    }

}
