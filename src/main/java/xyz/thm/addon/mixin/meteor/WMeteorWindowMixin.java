package xyz.thm.addon.mixin.meteor;

import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.themes.meteor.MeteorGuiTheme;
import meteordevelopment.meteorclient.gui.themes.meteor.widgets.WMeteorWindow;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.thm.addon.gui.ClientLook;
import xyz.thm.addon.gui.ClientLookHelper;

@Mixin(value = WMeteorWindow.class, remap = false)
public abstract class WMeteorWindowMixin {
    @Inject(method = "onRender", at = @At("TAIL"))
    private void thm$customWindowRender(GuiRenderer renderer, double mouseX, double mouseY, double delta, CallbackInfo ci) {
        MeteorGuiTheme theme = (MeteorGuiTheme) ((WWidget) (Object) this).theme;
        ClientLook look = ClientLookHelper.getLook(theme);
        if (look == ClientLook.DEFAULT || look == ClientLook.FUTURE_OLD) return;

        double x = ((WWidget) (Object) this).x;
        double y = ((WWidget) (Object) this).y;
        double width = ((WWidget) (Object) this).width;
        double height = ((WWidget) (Object) this).height;

        if (look == ClientLook.RUSHERHACK) {
            renderer.quad(x, y, width, theme.scale(2), new SettingColor(255, 170, 0, 255));
        } else if (look == ClientLook.FUTURE_NEW) {
            renderer.quad(x, y, width, theme.scale(2), new SettingColor(0, 214, 240, 255));
        } else if (look == ClientLook.VAPE) {
            renderer.quad(x, y, width, theme.scale(2), new SettingColor(255, 35, 110, 255));
        }
    }
}
