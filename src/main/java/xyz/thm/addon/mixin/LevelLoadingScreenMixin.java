/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;

/** "Loading terrain" can hang forever and vanilla offers no way out, not even Esc: this adds Disconnect. */
@Mixin(LevelLoadingScreen.class)
public abstract class LevelLoadingScreenMixin extends Screen {
    protected LevelLoadingScreenMixin(Component title) {
        super(title);
    }

    @Override
    protected void init() {
        super.init();
        addRenderableWidget(Button.builder(Component.translatable("menu.disconnect"), button -> {
            button.active = false;
            // Same call as the pause menu's Disconnect; copes with no world loaded yet.
            Minecraft.getInstance().disconnectFromWorld(ClientLevel.DEFAULT_QUIT_MESSAGE);
        }).bounds(width / 2 - 100, height - 40, 200, 20).build());
    }
}
