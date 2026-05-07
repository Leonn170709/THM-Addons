package xyz.thm.addon.mixin.meteor;

import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.themes.meteor.MeteorGuiTheme;
import meteordevelopment.meteorclient.gui.themes.meteor.widgets.WMeteorHorizontalSeparator;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.thm.addon.gui.ClientLook;
import xyz.thm.addon.gui.ClientLookHelper;

@Mixin(value = WMeteorHorizontalSeparator.class, remap = false)
public abstract class WMeteorHorizontalSeparatorMixin {
    @Inject(method = "onRender", at = @At("TAIL"))
    private void thm$customSeparatorRender(GuiRenderer renderer, double mouseX, double mouseY, double delta, CallbackInfo ci) {
        MeteorGuiTheme theme = (MeteorGuiTheme) ((WWidget) (Object) this).theme;
        ClientLook look = ClientLookHelper.getLook(theme);
        if (look == ClientLook.DEFAULT || look == ClientLook.FUTURE_OLD) return;

        WWidget widget = (WWidget) (Object) this;
        double lineH = theme.scale(1);
        SettingColor center = switch (look) {
            case RUSHERHACK -> new SettingColor(255, 170, 0, 220);
            case FUTURE_NEW -> new SettingColor(0, 214, 240, 220);
            case VAPE -> new SettingColor(255, 35, 110, 220);
            default -> theme.separatorCenter.get();
        };
        double y = widget.y + Math.round(widget.height / 2.0);
        renderer.quad(widget.x, y, widget.width, lineH, center);
    }
}
