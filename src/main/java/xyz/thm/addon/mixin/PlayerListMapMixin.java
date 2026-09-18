/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.mixin;

import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Vanilla's playerListEntries is a plain HashMap that the render thread mutates on every join/leave
 * while getCaseInsensitivePlayerInfo iterates it off-thread, from the async profile lookup behind
 * 1.21.9 player-head chat components — a CME there crashes the client.
 */
@Mixin(ClientPlayNetworkHandler.class)
public abstract class PlayerListMapMixin {
    @Shadow @Final @Mutable private Map<UUID, PlayerListEntry> playerListEntries;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void thm$concurrentPlayerList(CallbackInfo ci) {
        playerListEntries = new ConcurrentHashMap<>(playerListEntries);
    }
}
