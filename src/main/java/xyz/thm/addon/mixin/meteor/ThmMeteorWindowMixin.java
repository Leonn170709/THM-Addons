/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.mixin.meteor;

import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.themes.meteor.MeteorGuiTheme;
import meteordevelopment.meteorclient.gui.themes.meteor.widgets.WMeteorWindow;
import meteordevelopment.meteorclient.gui.utils.BaseWidget;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.utils.render.color.Color;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.thm.addon.gui.ThmChrome;
import xyz.thm.addon.gui.themes.ThmTheme;
import xyz.thm.addon.shaders.ShaderBackground;

/**
 * Gives Meteor windows the THM main-menu window look while ThmTheme is active: a translucent
 * purple body (BODY_FILL) with the same gradient border as the title-screen window, instead of
 * the flat single-color body Meteor draws by default. Also frosts the window's own backdrop
 * (blur slider in the GUI tab) so the translucent body reads like frosted glass - the same
 * per-region blur the main-menu window uses, applied to just this window's rect (not the whole
 * screen).
 *
 * Redirects the one body quad in WMeteorWindow#onRender rather than shadowing WWindow's protected
 * header/expanded fields - the redirect's own args already carry the body's top (y + header.height)
 * and height, so the window rect and header height are both recoverable from them plus this.x/y.
 */
@Mixin(value = WMeteorWindow.class, remap = false)
public abstract class ThmMeteorWindowMixin {
    // At onRender HEAD the framebuffer holds only what's behind the window (Meteor batches its own
    // quads and flushes them later), so blurring this window's rect now frosts the backdrop and the
    // body/widgets draw sharp on top. Meteor renders in framebuffer-pixel space; renderBlurredRegion
    // wants GUI-scaled coords, so divide by the window scale factor.
    @Inject(method = "onRender", at = @At("HEAD"))
    private void thm$blurBackdrop(GuiRenderer renderer, double mouseX, double mouseY, double delta, CallbackInfo ci) {
        GuiTheme theme = ((BaseWidget) this).getTheme();
        if (!(theme instanceof ThmTheme meteor)) return;

        int strength = meteor.blur.get();
        if (strength <= 0) return;

        WWidget self = (WWidget) (Object) this;
        double sf = Minecraft.getInstance().getWindow().getGuiScale();
        int x1 = (int) Math.round(self.x / sf);
        int y1 = (int) Math.round(self.y / sf);
        int x2 = (int) Math.round((self.x + self.width) / sf);
        int y2 = (int) Math.round((self.y + self.height) / sf);
        ShaderBackground.renderBlurredRegion(x1, y1, x2, y2, strength);
    }

    @Redirect(method = "onRender", at = @At(value = "INVOKE", target = "Lmeteordevelopment/meteorclient/gui/renderer/GuiRenderer;quad(DDDDLmeteordevelopment/meteorclient/utils/render/color/Color;)V"))
    private void thm$body(GuiRenderer renderer, double bodyX, double bodyY, double bodyWidth, double bodyHeight, Color color) {
        GuiTheme theme = ((BaseWidget) this).getTheme();
        if (!(theme instanceof ThmTheme meteor)) {
            renderer.quad(bodyX, bodyY, bodyWidth, bodyHeight, color);
            return;
        }

        WWidget self = (WWidget) (Object) this;
        double s = ((MeteorGuiTheme) theme).scale(2);
        double x = self.x, y = self.y, w = self.width, h = self.height;

        // Body sits below the header (bodyY = y + header.height), inset to sit inside the border.
        renderer.quad(x + s, bodyY, w - s * 2, h - (bodyY - y) - s, ThmChrome.bodyFill());
        ThmChrome.border(renderer, x, y, w, h, s);
    }
}
