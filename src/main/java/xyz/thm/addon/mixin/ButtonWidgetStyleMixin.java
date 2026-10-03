/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.thm.addon.gui.MainMenuFx;
import xyz.thm.addon.gui.ThmStyledButtons;

// Re-skins buttons TitleScreenMenuMixin marked via ThmStyledButtons (the repositioned title
// screen buttons) with BleachHack-flat/THM-gold chrome instead of vanilla's beveled texture.
// Every other PressableWidget (options screen, pause menu, ...) is untouched.
@Mixin(AbstractButton.class)
public abstract class ButtonWidgetStyleMixin extends AbstractWidget {

    protected ButtonWidgetStyleMixin(int x, int y, int width, int height, Component message) {
        super(x, y, width, height, message);
    }

    @Inject(method = "renderWidget", at = @At("HEAD"), cancellable = true)
    private void thm$renderThmStyle(GuiGraphics context, int mouseX, int mouseY, float deltaTicks, CallbackInfo ci) {
        if (!ThmStyledButtons.isStyled(this)) return;

        MainMenuFx.renderButton(context, Minecraft.getInstance().font,
            this.getX(), this.getY(), this.getX() + this.getWidth(), this.getY() + this.getHeight(),
            this.getMessage().getString(), this.isHovered(), this.active);
        ci.cancel();
    }
}
