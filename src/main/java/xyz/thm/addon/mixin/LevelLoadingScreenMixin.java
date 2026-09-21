/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.world.LevelLoadingScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;

/** "Loading terrain" can hang forever and vanilla offers no way out, not even Esc: this adds Disconnect. */
@Mixin(LevelLoadingScreen.class)
public abstract class LevelLoadingScreenMixin extends Screen {
    protected LevelLoadingScreenMixin(Text title) {
        super(title);
    }

    @Override
    protected void init() {
        super.init();
        addDrawableChild(ButtonWidget.builder(Text.translatable("menu.disconnect"), button -> {
            button.active = false;
            // Same call as the pause menu's Disconnect; copes with no world loaded yet.
            MinecraftClient.getInstance().disconnect(ClientWorld.QUITTING_MULTIPLAYER_TEXT);
        }).dimensions(width / 2 - 100, height - 40, 200, 20).build());
    }
}
