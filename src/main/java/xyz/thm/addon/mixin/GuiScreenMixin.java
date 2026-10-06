/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.mixin;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.thm.addon.THMAddon;
import xyz.thm.addon.gui.HighwayBuilderScreen;

@Mixin(Gui.class)
public abstract class GuiScreenMixin {
    @Unique private String thm$lastRequestedScreen;
    @Unique private String thm$lastActiveScreen;

    @ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true)
    private Screen thm$highwayBuilderScreen(Screen screen) {
        Screen replacement = HighwayBuilderScreen.replaceModuleScreen(screen);
        if (Minecraft.getInstance().level == null) {
            String name = replacement == null ? "<none>" : replacement.getClass().getName();
            if (!name.equals(thm$lastRequestedScreen)) {
                thm$lastRequestedScreen = name;
                THMAddon.LOG.info("[THM/Menu] GuiScreenMixin.setScreen requested {}; vanillaTitle={}, bozeLoaded={}",
                    name, replacement instanceof TitleScreen, FabricLoader.getInstance().isModLoaded("boze-loader"));
            }
        }
        return replacement;
    }
    @Inject(method = "setScreen", at = @At("TAIL"))
    private void thm$logActiveScreen(Screen requested, CallbackInfo ci) {
        if (Minecraft.getInstance().level != null) return;
        Screen active = ((Gui) (Object) this).screen();
        String name = active == null ? "<none>" : active.getClass().getName();
        if (!name.equals(thm$lastActiveScreen)) {
            thm$lastActiveScreen = name;
            THMAddon.LOG.info("[THM/Menu] GuiScreenMixin.setScreen completed; active={}, vanillaTitle={}", name, active instanceof TitleScreen);
        }
    }

}
