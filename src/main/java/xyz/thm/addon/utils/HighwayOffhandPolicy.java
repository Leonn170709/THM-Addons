/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.utils;

public final class HighwayOffhandPolicy {
    public enum Target { Totem, Enclosure, EnderChest, Preserve, BuildingBlock }

    private HighwayOffhandPolicy() {}

    public static Target target(boolean danger, boolean enclosure, boolean miningEnderChests, boolean restocking) {
        if (danger) return Target.Totem;
        if (enclosure) return Target.Enclosure;
        if (miningEnderChests) return Target.EnderChest;
        return restocking ? Target.Preserve : Target.BuildingBlock;
    }
}
