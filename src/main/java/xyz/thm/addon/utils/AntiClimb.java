/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.utils;

/** NoSlow's smart anti-climb decision, kept free of the world so it can be tested. */
public final class AntiClimb {
    private AntiClimb() {}

    /**
     * @param falling    in the air and moving down
     * @param dropBlocks distance from the feet to the ground below
     * @return true to cancel climbing
     */
    public static boolean suppress(boolean falling, double dropBlocks) {
        // Never lifted up; when falling, a step of up to a block needs no catching, anything deeper keeps the vanilla save.
        return !falling || dropBlocks <= 1.0;
    }
}
