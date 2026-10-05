/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.mixin.meteor;

import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import org.junit.jupiter.api.Test;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import static org.junit.jupiter.api.Assertions.*;

class DropdownTooltipMixinTest {
    @Test
    void customDescriptionsRefreshInitializedRowsAndChangedSelection() throws Exception {
        var dropdown = new DropdownTooltipMixin() {};
        dropdown.values = new Object[] { "First", "Second" };
        dropdown.value = "First";

        var add = DropdownTooltipMixin.class.getDeclaredMethod("thm$describeOption", WWidget.class);
        add.setAccessible(true);
        var first = new WWidget() {};
        var second = new WWidget() {};
        add.invoke(dropdown, first);
        add.invoke(dropdown, second);
        assertNull(first.tooltip);

        dropdown.thm$setDescriptions(value -> value.equals("First") ? "First action." : "Second action.");
        assertEquals("First action.", first.tooltip);
        assertEquals("Second action.", second.tooltip);

        dropdown.value = "Second";
        var selection = DropdownTooltipMixin.class.getDeclaredMethod("thm$describeSelection", GuiRenderer.class,
            double.class, double.class, double.class, CallbackInfoReturnable.class);
        selection.setAccessible(true);
        selection.invoke(dropdown, null, 0, 0, 0, null);
        assertEquals(second.tooltip, dropdown.tooltip);

        var reset = DropdownTooltipMixin.class.getDeclaredMethod("thm$resetOptions", CallbackInfo.class);
        reset.setAccessible(true);
        reset.invoke(dropdown, (Object) null);
        var rebuilt = new WWidget() {};
        add.invoke(dropdown, rebuilt);
        assertEquals(first.tooltip, rebuilt.tooltip);
    }
}
