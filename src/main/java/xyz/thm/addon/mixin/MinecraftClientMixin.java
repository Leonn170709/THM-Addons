/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.mixin;

import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import xyz.thm.addon.gui.HighwayBuilderScreen;
import xyz.thm.addon.modules.HighwayBuilderTHM;

@Mixin(Minecraft.class)
public abstract class MinecraftClientMixin {

    // Themes like Catppuccin override GuiTheme.moduleScreen, so the swap happens where every theme's
    // screen ends up: opening it.
    @ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true)
    private Screen thm$highwayBuilderScreen(Screen screen) {
        return HighwayBuilderScreen.replaceModuleScreen(screen);
    }

    // Vanilla's doItemUse returns early while ClientPlayerEntity#isRiding() - true only while
    // steering a boat with a movement key held - so item use in a moving boat is a client-side
    // stop only; the server accepts the packets fine.
    @Redirect(
        method = "startUseItem",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;isHandsBusy()Z")
    )
    private boolean thm$allowItemUseWhileRiding(LocalPlayer player) {
        return false;
    }

    @Redirect(
        method = "handleKeybinds",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;releaseUsingItem(Lnet/minecraft/world/entity/player/Player;)V"
        )
    )
    private void thm$preserveHighwayBuilderBowDraw(MultiPlayerGameMode interactionManager, Player player) {
        HighwayBuilderTHM builder = Modules.get().get(HighwayBuilderTHM.class);
        if (builder != null && builder.isActive() && builder.drawingBow) return;

        interactionManager.releaseUsingItem(player);
    }
}
