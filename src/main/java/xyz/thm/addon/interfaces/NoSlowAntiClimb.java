/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.interfaces;

import xyz.thm.addon.settings.DescribedOption;

/** Implemented by Meteor's NoSlow through NoSlowMixin. */
public interface NoSlowAntiClimb {
    enum Mode implements DescribedOption {
        Off, Always, Smart;

        @Override
        public String description() {
            return switch (this) {
                case Off -> "Keep normal climbing behavior.";
                case Always -> "Block all automatic climbing.";
                case Smart -> "Block climbing while keeping fall catches over blocks.";
            };
        }
    }

    Mode thm$antiClimbMode();
}
