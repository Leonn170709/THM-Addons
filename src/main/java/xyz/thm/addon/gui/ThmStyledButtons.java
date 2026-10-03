/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.gui;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.client.gui.components.AbstractWidget;

// Marks which vanilla ClickableWidgets TitleScreenMenuMixin has repositioned into the THM
// window frame, so ButtonWidgetStyleMixin knows which buttons to re-skin (and leaves every
// other screen's buttons - options, pause menu, etc. - untouched).
public class ThmStyledButtons {
    private static final Set<AbstractWidget> styled = Collections.newSetFromMap(new WeakHashMap<>());

    public static void mark(AbstractWidget widget) {
        styled.add(widget);
    }

    public static boolean isStyled(Object widget) {
        return widget instanceof AbstractWidget w && styled.contains(w);
    }
}
