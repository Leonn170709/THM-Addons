/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.mixin.meteor;

import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.WidgetScreen;
import meteordevelopment.meteorclient.systems.modules.Module;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xyz.thm.addon.gui.HighwayBuilderScreen;
import xyz.thm.addon.modules.HighwayBuilderTHM;
import xyz.thm.addon.system.THMSystem;

/** HighwayBuilder opens its own tabbed control screen instead of the flat settings list. */
@Mixin(GuiTheme.class)
public abstract class GuiThemeModuleScreenMixin {
    @Inject(method = "moduleScreen", at = @At("HEAD"), cancellable = true)
    private void thm$highwayBuilderScreen(Module module, CallbackInfoReturnable<WidgetScreen> cir) {
        if (module instanceof HighwayBuilderTHM builder && THMSystem.get().tabbedHighwayGui.get()) {
            cir.setReturnValue(new HighwayBuilderScreen((GuiTheme) (Object) this, builder));
        }
    }
}
