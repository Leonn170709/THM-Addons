/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xyz.thm.addon.utils.FastTab;

import java.util.List;
import java.util.UUID;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;

@Mixin(PlayerTabOverlay.class)
public class PlayerListHudMixin {
    @Inject(method = "getPlayerInfos", at = @At("HEAD"), cancellable = true)
    private void thmAddon$cachedEntries(CallbackInfoReturnable<List<PlayerInfo>> cir) {
        List<PlayerInfo> cached = FastTab.entries();
        if (cached != null) cir.setReturnValue(cached);
    }

    @Inject(method = "getPlayerInfos", at = @At("RETURN"))
    private void thmAddon$storeEntries(CallbackInfoReturnable<List<PlayerInfo>> cir) {
        FastTab.storeEntries(cir.getReturnValue());
    }

    // Redirecting the call site rather than injecting into getPlayerName keeps this independent of the
    // order Meteor's own HEAD injection into that method is applied in - a miss still runs the full
    // Meteor + THM name pipeline, we just stop it running once per player per frame.
    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/PlayerTabOverlay;getNameForDisplay(Lnet/minecraft/client/multiplayer/PlayerInfo;)Lnet/minecraft/network/chat/Component;"))
    private Component thmAddon$cachedName(PlayerTabOverlay hud, PlayerInfo entry) {
        Component cached = FastTab.name(entry);
        return cached != null ? cached : FastTab.store(entry, hud.getNameForDisplay(entry));
    }

    // Every head is two textured quads with a texture nothing else in the frame uses, so the GUI renderer
    // has to break its batch per player - a thousand-entry tab list is a thousand extra draw calls, which
    // is what actually costs the framerate. Caching cannot help with that; not drawing them can.
    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/PlayerFaceRenderer;draw(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/resources/Identifier;IIIZZI)V"))
    private void thmAddon$skipHead(GuiGraphics context, Identifier texture, int x, int y, int size, boolean hat, boolean upsideDown, int color) {
        if (!FastTab.hideHeads()) PlayerFaceRenderer.draw(context, texture, x, y, size, hat, upsideDown, color);
    }

    // this lookup is a linear scan of the loaded players, per tab entry, and its only use is deciding
    // whether the head is drawn upside down - dead work once the head isn't drawn at all
    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;getPlayerByUUID(Ljava/util/UUID;)Lnet/minecraft/world/entity/player/Player;"))
    private Player thmAddon$skipUpsideDownLookup(ClientLevel world, UUID uuid) {
        return FastTab.hideHeads() ? null : world.getPlayerByUUID(uuid);
    }
}
