/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.interfaces;

import net.minecraft.entity.player.SkinTextures;

import java.util.UUID;

public interface LogoutSpotsPoseData {
    double thm$getX();
    double thm$getY();
    double thm$getZ();
    double thm$getXWidth();
    double thm$getZWidth();
    double thm$getHeight();
    UUID thm$getUuid();

    String thm$getName();
    SkinTextures thm$getSkin();
    byte thm$getModelParts();
    float thm$getBodyYaw();
    float thm$getYaw();
    float thm$getPitch();
    float thm$getHeadYaw();
    float thm$getLimbPos();
    float thm$getLimbAmplitude();
    boolean thm$isSneaking();
    boolean thm$isLowPose();
}
