/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.mixin.meteor;

import meteordevelopment.meteorclient.gui.DefaultSettingsWidgetFactory;
import meteordevelopment.meteorclient.gui.widgets.WLabel;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.input.WDropdown;
import meteordevelopment.meteorclient.gui.widgets.pressable.WCheckbox;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ProvidedStringSetting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.thm.addon.settings.DropdownDescriptions;

@Mixin(value = DefaultSettingsWidgetFactory.class, remap = false)
public abstract class DefaultSettingsWidgetFactoryMixin {
    @Inject(method = "providedStringW", at = @At("RETURN"))
    private void thm$stringDescriptions(WTable table, ProvidedStringSetting setting, CallbackInfo ci) {
        for (int i = table.cells.size() - 1; i >= 0; i--) {
            if (table.cells.get(i).widget() instanceof WDropdown<?> dropdown) {
                DropdownDescriptions.configure(dropdown, value -> DropdownDescriptions.providedString(setting.name, value));
                return;
            }
        }
    }

    // Make the setting title label clickable to toggle the bool setting
    @Inject(method = "boolW", at = @At("RETURN"))
    private void thm$boolLabelClick(WTable table, BoolSetting setting, CallbackInfo ci) {
        for (int i = 1; i < table.cells.size(); i++) {
            WWidget w = table.cells.get(i).widget();
            if (!(w instanceof WCheckbox checkbox)) continue;

            WWidget prev = table.cells.get(i - 1).widget();
            if (prev instanceof WLabel label) {
                label.action = () -> {
                    checkbox.checked = !checkbox.checked;
                    setting.set(checkbox.checked);
                };
            }
            break;
        }
    }
}
