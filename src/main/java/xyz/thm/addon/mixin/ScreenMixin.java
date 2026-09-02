/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.DeathScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xyz.thm.addon.gui.DeathChatScreen;
import xyz.thm.addon.gui.MainMenuFx;
import xyz.thm.addon.shaders.ShaderBackground;

@Mixin(Screen.class)
public abstract class ScreenMixin {

    @Shadow @Final protected MinecraftClient client;

    // Mouse particle trail on every screen shown before a world is loaded (title, singleplayer/
    // multiplayer/realms/create-world selection, ...). Injected into Screen's own base render() (not
    // TitleScreen's override) so it fires both for screens that use that base implementation
    // directly (e.g. MultiplayerScreen) and for TitleScreen via its super.render() call -
    // TitleScreenMenuMixin no longer ticks/renders particles itself, to avoid double-drawing.
    @Inject(method = "render", at = @At("TAIL"))
    private void thm$renderMenuParticles(DrawContext context, int mouseX, int mouseY, float deltaTicks, CallbackInfo ci) {
        if (this.client.world != null) return;

        MainMenuFx.tick(mouseX, mouseY);
        MainMenuFx.renderParticles(context);
    }

    // Swaps the vanilla rotating-cube panorama for the active THM shader background - vanilla's own
    // Screen#renderBackground already scopes renderPanoramaBackground to "no world loaded" screens,
    // so no extra instanceof check is needed here. See ShaderBackground for the render path.
    //
    // ponytail: no dimming/blur layered on top here anymore. Both vanilla's GameRenderer#renderBlur()
    // (real box-blur) and a plain translucent DrawContext.fill() were tried and both left later
    // draws that same frame (the "Minecraft <version>" text, at minimum) not rendering - root cause
    // not fully pinned down. Contrast against the shader is handled by MainMenuFx's window/button
    // fills being mostly opaque instead.
    @Inject(method = "renderPanoramaBackground", at = @At("HEAD"), cancellable = true)
    private void thm$renderShaderBackground(DrawContext context, float deltaTicks, CallbackInfo ci) {
        if (ShaderBackground.render()) ci.cancel();
    }

    // Lets the chat/command key open chat on the death screen - it is client-side only, the server
    // accepts chat from a dead player. DeathScreen doesn't override keyPressed, so this targets
    // Screen's and filters on the instance.
    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void thm$chatOnDeathScreen(KeyInput input, CallbackInfoReturnable<Boolean> cir) {
        if (!((Object) this instanceof DeathScreen)) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        boolean command = mc.options.commandKey.matchesKey(input);
        if (!command && !mc.options.chatKey.matchesKey(input)) return;

        mc.setScreen(new DeathChatScreen((Screen) (Object) this, command ? "/" : ""));
        cir.setReturnValue(true);
    }
}
