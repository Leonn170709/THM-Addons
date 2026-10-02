/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.mixin;

import io.netty.channel.ChannelFutureListener;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.thm.addon.modules.HighwayBuilderTHM;

@Mixin(ClientConnection.class)
public abstract class ClientConnectionChokeMixin {
    @Inject(method = "send(Lnet/minecraft/network/packet/Packet;Lio/netty/channel/ChannelFutureListener;Z)V", at = @At("HEAD"), cancellable = true)
    private void thm$holdPacket(Packet<?> packet, ChannelFutureListener listener, boolean flush, CallbackInfo ci) {
        Modules modules = Modules.get();
        HighwayBuilderTHM builder = modules == null ? null : modules.get(HighwayBuilderTHM.class);
        if (builder != null && builder.holdOutgoingPacket((ClientConnection) (Object) this, packet, listener, flush)) ci.cancel();
    }
}
