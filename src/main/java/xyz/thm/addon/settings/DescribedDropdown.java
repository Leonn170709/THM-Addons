/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.settings;

import java.util.function.Function;

public interface DescribedDropdown {
    void thm$setDescriptions(Function<Object, String> descriptions);
}
