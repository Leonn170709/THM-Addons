/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.mixin;

import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import com.mojang.blaze3d.systems.RenderSystem;
import xyz.thm.addon.THMAddon;
import xyz.thm.addon.shaders.ShaderManager;
import net.minecraft.client.renderer.CubeMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.thm.addon.shaders.ShaderBackground;
import xyz.thm.addon.gui.MainMenuFx;
import xyz.thm.addon.system.THMSystem;

@Mixin(GuiRenderer.class)
public abstract class GuiBackgroundMixin {
    @Unique private String thm$lastScreen;
    @Unique private String thm$lastShader;
    @Unique private String thm$lastShaderStatus;
    @Unique private boolean thm$panoramaSeen;
    @Unique private boolean thm$prepareLogged;
    @Unique private int thm$lastBlurStrength = -1;
    @Unique private boolean thm$lastBlurDrawn;

    @Inject(method = "render", at = @At("HEAD"))
    private void thm$logRenderEntry(CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) return;
        String screen = mc.gui.screen() == null ? "<none>" : mc.gui.screen().getClass().getName();
        if (screen.equals(thm$lastScreen)) return;
        thm$lastScreen = screen;
        thm$lastShaderStatus = null;
        thm$panoramaSeen = false;
        thm$prepareLogged = false;
        thm$lastBlurStrength = -1;
        THMAddon.LOG.info("[THM/Menu] GuiBackgroundMixin.render entered; screen={}, backend={}, shader={}",
            screen, RenderSystem.getDevice().getDeviceInfo().backendName(), ShaderManager.active());
    }

    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/CubeMap;render(FF)V"))
    private void thm$background(CubeMap panorama, float pitch, float spin) {
        thm$panoramaSeen = true;
        boolean drawn = ShaderBackground.render();
        String shader = ShaderManager.active();
        String status = ShaderBackground.renderStatus();
        if (!status.equals(thm$lastShaderStatus) || !java.util.Objects.equals(shader, thm$lastShader)) {
            thm$lastShader = shader;
            thm$lastShaderStatus = status;
            THMAddon.LOG.info("[THM/Menu] GuiBackgroundMixin.panorama callback; shader={}, result={}, vanillaFallback={}", shader, status, !drawn);
        }
        if (!drawn) panorama.render(pitch, spin);
        Minecraft mc = Minecraft.getInstance();
        THMSystem system = THMSystem.get();
        if (mc.gui.screen() instanceof TitleScreen screen && system != null
            && system.mainMenuWindow.get() && !MainMenuFx.previewMode) {
            int strength = system.mainMenuBlur.get();
            int[] bounds = MainMenuFx.windowBounds(screen.width, screen.height);
            boolean blurred = ShaderBackground.renderBlurredRegion(bounds[0], bounds[1], bounds[2], bounds[3], strength);
            if (strength != thm$lastBlurStrength || blurred != thm$lastBlurDrawn) {
                thm$lastBlurStrength = strength;
                thm$lastBlurDrawn = blurred;
                THMAddon.LOG.info("[THM/Menu] Window blur after background; strength={}, drawn={}", strength, blurred);
            }
        }
    }

    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/render/GuiRenderer;prepare()V"))
    private void thm$logPrepare(CallbackInfo ci) {
        if (Minecraft.getInstance().level == null && !thm$prepareLogged) {
            thm$prepareLogged = true;
            THMAddon.LOG.info("[THM/Menu] GuiBackgroundMixin.prepare reached; panoramaCallback={}, shader={}", thm$panoramaSeen, ShaderManager.active());
        }
    }
}
