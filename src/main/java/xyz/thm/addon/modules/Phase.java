/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.modules;

import meteordevelopment.meteorclient.systems.modules.Module;
import xyz.thm.addon.THMAddon;
import xyz.thm.addon.utils.PearlPhaser;

public class Phase extends Module {

    /** Owns the Pearl / Self Place setting groups and the throw itself. */
    private final PearlPhaser pearl = new PearlPhaser(settings);

    public Phase() {
        super(THMAddon.PVP, "phase", "Allows player to phase through solid blocks using ender pearls.");
    }

    @Override
    public void onActivate() {
        if (mc.player == null || mc.level == null) {
            toggle();
            return;
        }
        pearl.throwPearl();
        toggle();
    }

    @Override
    public String getInfoString() {
        return "Pearl Mode";
    }
}
