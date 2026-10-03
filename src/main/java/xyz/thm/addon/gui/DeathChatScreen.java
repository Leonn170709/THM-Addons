/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;

/**
 * Chat opened from the death screen. Sending (Enter) and Esc both leave the player on no screen at
 * all while still dead — i.e. no respawn button — so the death screen is put back afterwards.
 */
public class DeathChatScreen extends ChatScreen {
    private final Screen parent;

    public DeathChatScreen(Screen parent, String text) {
        super(text, false);
        this.parent = parent;
    }

    @Override
    public void removed() {
        super.removed();
        Minecraft mc = Minecraft.getInstance();
        // Re-opening from inside removed() would be overwritten by the setScreen call that got us
        // here, so it goes through the client's own task queue instead.
        mc.execute(() -> {
            if (mc.screen == null && mc.player != null && mc.player.isDeadOrDying()) mc.setScreen(parent);
        });
    }
}
