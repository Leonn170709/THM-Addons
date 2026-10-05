/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.waveycapes;

import xyz.thm.addon.settings.DescribedOption;

public enum WindMode implements DescribedOption {
    NONE, WAVES;

    @Override
    public String description() {
        return switch (this) {
            case NONE -> "Disable cape wind.";
            case WAVES -> "Add wind waves to the cape.";
        };
    }
}
