package xyz.thm.addon.mixin.meteor;

import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.themes.meteor.MeteorGuiTheme;
import meteordevelopment.meteorclient.gui.themes.meteor.widgets.WMeteorModule;
import meteordevelopment.meteorclient.gui.utils.AlignmentX;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.thm.addon.gui.ClientLook;
import xyz.thm.addon.gui.ClientLookHelper;

@Mixin(value = WMeteorModule.class, remap = false)
public abstract class WMeteorModuleMixin {
    @Shadow private Module module;
    @Shadow private String title;
    @Shadow private double titleWidth;
    @Shadow private double animationProgress1;
    @Shadow private double animationProgress2;
    @Shadow public abstract double pad();

    @Inject(method = "onRender", at = @At("HEAD"), cancellable = true)
    private void thm$customModuleRender(GuiRenderer renderer, double mouseX, double mouseY, double delta, CallbackInfo ci) {
        MeteorGuiTheme theme = (MeteorGuiTheme) ((WWidget) (Object) this).theme;
        ClientLook look = ClientLookHelper.getLook(theme);
        if (look == ClientLook.DEFAULT || look == ClientLook.FUTURE_OLD) return;

        double x = ((WWidget) (Object) this).x;
        double y = ((WWidget) (Object) this).y;
        double width = ((WWidget) (Object) this).width;
        double height = ((WWidget) (Object) this).height;
        boolean mouseOver = ((WWidget) (Object) this).mouseOver;
        double pad = pad();

        animationProgress1 += delta * 4 * ((module.isActive() || mouseOver) ? 1 : -1);
        animationProgress1 = thm$clamp(animationProgress1, 0, 1);
        animationProgress2 += delta * 6 * (module.isActive() ? 1 : -1);
        animationProgress2 = thm$clamp(animationProgress2, 0, 1);

        SettingColor accent = theme.accentColor.get();
        SettingColor bg = thm$moduleBgForLook(look, theme);
        SettingColor edge = thm$moduleEdgeForLook(look, accent);

        if (animationProgress1 > 0) renderer.quad(x, y, width * animationProgress1, height, bg);
        if (animationProgress2 > 0) renderer.quad(x, y + height * (1 - animationProgress2), thm$edgeWidth(look, theme), height * animationProgress2, edge);
        if (look == ClientLook.RUSHERHACK) renderer.quad(x, y + height - theme.scale(1), width, theme.scale(1), new SettingColor(255, 170, 0, 70));

        double textX = x + pad;
        double w = width - pad * 2;
        if (theme.moduleAlignment.get() == AlignmentX.Center) textX += w / 2 - titleWidth / 2;
        else if (theme.moduleAlignment.get() == AlignmentX.Right) textX += w - titleWidth;

        renderer.text(title, textX, y + pad, theme.textColor.get(), false);
        ci.cancel();
    }

    @Unique
    private SettingColor thm$moduleBgForLook(ClientLook look, MeteorGuiTheme theme) {
        return switch (look) {
            case FUTURE_NEW -> new SettingColor(0, 188, 212, 40);
            case RUSHERHACK -> new SettingColor(255, 170, 0, 28);
            case MIO -> new SettingColor(255, 120, 86, 36);
            case NOVOLINE -> new SettingColor(0, 126, 255, 34);
            case IMPACT -> new SettingColor(65, 185, 255, 30);
            case VAPE -> new SettingColor(255, 35, 110, 33);
            case WURST -> new SettingColor(0, 200, 83, 33);
            default -> theme.moduleBackground.get();
        };
    }

    @Unique
    private SettingColor thm$moduleEdgeForLook(ClientLook look, SettingColor accent) {
        return switch (look) {
            case RUSHERHACK -> new SettingColor(255, 170, 0, 255);
            case FUTURE_NEW -> new SettingColor(0, 214, 240, 255);
            default -> accent;
        };
    }

    @Unique
    private double thm$edgeWidth(ClientLook look, MeteorGuiTheme theme) {
        return switch (look) {
            case RUSHERHACK -> theme.scale(3);
            case FUTURE_NEW -> theme.scale(2.5);
            default -> theme.scale(2);
        };
    }

    @Unique
    private double thm$clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
