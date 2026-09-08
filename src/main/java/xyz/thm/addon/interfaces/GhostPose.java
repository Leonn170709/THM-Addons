/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.interfaces;

/** Limb snapshot for ghost players: they are never ticked, so their own LimbAnimator stays empty. */
public interface GhostPose {
    float thm$limbPos();

    float thm$limbAmplitude();
}
