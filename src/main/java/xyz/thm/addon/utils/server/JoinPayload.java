/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.utils.server;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public class JoinPayload implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<JoinPayload> ID = new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("anarchymod", "join"));
    public static final StreamCodec<FriendlyByteBuf, JoinPayload> CODEC = StreamCodec.ofMember((payload, buf) -> {},buf -> new JoinPayload());
    public static final CustomPacketPayload.TypeAndCodec<FriendlyByteBuf, JoinPayload> TYPE = new CustomPacketPayload.TypeAndCodec<>(ID, CODEC);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {return ID;}
}
