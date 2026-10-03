/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.utils.render;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.world.entity.player.PlayerSkin;
import xyz.thm.addon.interfaces.GhostPose;
import xyz.thm.addon.mixin.accessor.PlayerModelPartsAccessor;

/** Ghost player that keeps a skin snapshot, since a logged out player has no player list entry left. */
public class SkinGhostPlayer extends RemotePlayer implements GhostPose {
    private final PlayerSkin skin;
    private float limbPos, limbAmplitude;

    public SkinGhostPlayer(ClientLevel world, GameProfile profile, PlayerSkin skin) {
        super(world, profile);
        this.skin = skin;
    }

    public void setModelParts(byte modelParts) {
        getEntityData().set(PlayerModelPartsAccessor.thm$getModelParts(), modelParts);
    }

    public void setLimbs(float pos, float amplitude) {
        limbPos = pos;
        limbAmplitude = amplitude;
    }

    @Override
    public float thm$limbPos() {
        return limbPos;
    }

    @Override
    public float thm$limbAmplitude() {
        return limbAmplitude;
    }

    @Override
    public PlayerSkin getSkin() {
        return skin != null ? skin : super.getSkin();
    }
}
