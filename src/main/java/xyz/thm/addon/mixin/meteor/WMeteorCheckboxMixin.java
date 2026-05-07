package xyz.thm.addon.mixin.meteor;

import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.themes.meteor.MeteorGuiTheme;
import meteordevelopment.meteorclient.gui.themes.meteor.widgets.pressable.WMeteorCheckbox;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.pressable.WCheckbox;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.thm.addon.gui.ClientLook;
import xyz.thm.addon.gui.ClientLookHelper;

@Mixin(value = WMeteorCheckbox.class, remap = false)
public abstract class WMeteorCheckboxMixin {
    @Inject(method = "onRender", at = @At("TAIL"))
    private void thm$customCheckboxRender(GuiRenderer renderer, double mouseX, double mouseY, double delta, CallbackInfo ci) {
        MeteorGuiTheme theme = (MeteorGuiTheme) ((WWidget) (Object) this).theme;
        ClientLook look = ClientLookHelper.getLook(theme);
        if (look == ClientLook.DEFAULT || look == ClientLook.FUTURE_OLD) return;

        WWidget widget = (WWidget) (Object) this;
        WCheckbox checkbox = (WCheckbox) (Object) this;

        if (checkbox.checked) {
            double inset = switch (look) {
                case RUSHERHACK, FUTURE_NEW -> theme.scale(3);
                default -> theme.scale(4);
            };
            double cs = Math.max(2, widget.width - inset * 2);
            SettingColor mark = switch (look) {
                case RUSHERHACK -> new SettingColor(255, 170, 0, 255);
                case FUTURE_NEW -> new SettingColor(0, 214, 240, 255);
                case VAPE -> new SettingColor(255, 35, 110, 255);
                case WURST -> new SettingColor(0, 220, 96, 255);
                default -> theme.checkboxColor.get();
            };
            renderer.quad(widget.x + (widget.width - cs) / 2, widget.y + (widget.height - cs) / 2, cs, cs, mark);
        }

        if (look == ClientLook.RUSHERHACK || look == ClientLook.FUTURE_NEW) {
            SettingColor border = look == ClientLook.RUSHERHACK
                ? new SettingColor(255, 170, 0, 155)
                : new SettingColor(0, 214, 240, 155);
            double line = theme.scale(1);
            renderer.quad(widget.x, widget.y, widget.width, line, border);
            renderer.quad(widget.x, widget.y + widget.height - line, widget.width, line, border);
            renderer.quad(widget.x, widget.y, line, widget.height, border);
            renderer.quad(widget.x + widget.width - line, widget.y, line, widget.height, border);
        }
    }
}
