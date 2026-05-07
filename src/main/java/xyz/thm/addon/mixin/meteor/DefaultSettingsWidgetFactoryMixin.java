package xyz.thm.addon.mixin.meteor;

import meteordevelopment.meteorclient.gui.DefaultSettingsWidgetFactory;
import meteordevelopment.meteorclient.gui.widgets.WLabel;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.pressable.WCheckbox;
import meteordevelopment.meteorclient.settings.BoolSetting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = DefaultSettingsWidgetFactory.class, remap = false)
public abstract class DefaultSettingsWidgetFactoryMixin {
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
