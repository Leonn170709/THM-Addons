/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.mixin.meteor;

import meteordevelopment.meteorclient.gui.widgets.WWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xyz.thm.addon.gui.ThmChrome;
import xyz.thm.addon.gui.themes.ThmTheme;

/**
 * Wires up the header's "x" close glyph (drawn by ThmMeteorHeaderMixin / ThmChrome) on the
 * single-window settings/tab screens: a click on it closes the current screen, i.e. does what Esc
 * does. The "_" collapse glyph needs no handling here - clicking anywhere else on the header
 * already toggles the window collapse (WHeader#onMouseReleased), which is exactly what the arrow
 * used to do. Clicks reaching the header are already in Meteor-scaled coordinates (WidgetScreen
 * re-wraps them), so click.x()/y() hit-test directly against the header's bounds.
 */
@Mixin(targets = "meteordevelopment.meteorclient.gui.widgets.containers.WWindow$WHeader", remap = false)
public abstract class ThmHeaderClickMixin {
    @Inject(method = "onMouseClicked", at = @At("HEAD"), cancellable = true)
    private void thm$closeOnGlyph(MouseButtonEvent click, boolean doubled, CallbackInfoReturnable<Boolean> cir) {
        WWidget self = (WWidget) (Object) this;
        if (!(self.theme instanceof ThmTheme) || !ThmChrome.settingsWindow()) return;

        if (ThmChrome.closeGlyphHit(click.x(), click.y())) {
            Screen screen = Minecraft.getInstance().screen;
            if (screen != null) screen.onClose();
            cir.setReturnValue(true);
        }
    }
}
