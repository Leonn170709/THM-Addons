/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.mixin.meteor;

import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.input.WDropdown;
import meteordevelopment.meteorclient.gui.widgets.pressable.WPressable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
import xyz.thm.addon.settings.DescribedDropdown;
import xyz.thm.addon.settings.DropdownDescriptions;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

@Mixin(value = WDropdown.class, remap = false)
public abstract class DropdownTooltipMixin extends WPressable implements DescribedDropdown {
    @Shadow protected Object[] values;
    @Shadow protected Object value;
    @Unique private final List<WWidget> thm$options = new ArrayList<>();
    @Unique private Function<Object, String> thm$descriptions = DropdownDescriptions::description;

    @Unique
    @Override
    public void thm$setDescriptions(Function<Object, String> descriptions) {
        thm$descriptions = descriptions;
        for (int i = 0; i < thm$options.size(); i++) {
            thm$options.get(i).tooltip = descriptions.apply(values[i]);
        }
    }

    @Inject(method = "init", at = @At("HEAD"))
    private void thm$resetOptions(CallbackInfo ci) {
        thm$options.clear();
    }

    @ModifyArg(method = "init", at = @At(value = "INVOKE", target = "Lmeteordevelopment/meteorclient/gui/widgets/input/WDropdown$WDropdownRoot;add(Lmeteordevelopment/meteorclient/gui/widgets/WWidget;)Lmeteordevelopment/meteorclient/gui/utils/Cell;"), index = 0)
    private WWidget thm$describeOption(WWidget widget) {
        widget.tooltip = thm$descriptions.apply(values[thm$options.size()]);
        thm$options.add(widget);
        return widget;
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void thm$describeSelection(GuiRenderer renderer, double mouseX, double mouseY, double delta, CallbackInfoReturnable<Boolean> cir) {
        String description = thm$descriptions.apply(value);
        if (description != null) tooltip = description;
    }

    @ModifyArgs(method = "lambda$render$0", at = @At(value = "INVOKE", target = "Lmeteordevelopment/meteorclient/gui/widgets/input/WDropdown$WDropdownRoot;render(Lmeteordevelopment/meteorclient/gui/renderer/GuiRenderer;DDD)Z"))
    private void thm$popupTooltip(Args args, GuiRenderer renderer, double dropdownY, double scissorHeight, double mouseX, double mouseY, double delta) {
        if (mouseX >= x && mouseX < x + width && mouseY >= dropdownY && mouseY < dropdownY + scissorHeight) {
            // The popup renders last, so discard tooltips from covered settings first.
            renderer.tooltip(null);
        } else {
            // Clipped rows must not offer tooltips outside the visible popup.
            args.set(1, -Double.MAX_VALUE);
            args.set(2, -Double.MAX_VALUE);
        }
    }
}
