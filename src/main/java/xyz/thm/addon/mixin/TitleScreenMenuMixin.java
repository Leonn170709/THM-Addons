/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.mixin;

import net.minecraft.SharedConstants;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.LogoRenderer;
import net.minecraft.client.gui.components.SplashRenderer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import xyz.thm.addon.THMAddon;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xyz.thm.addon.gui.MainMenuFx;
import xyz.thm.addon.system.THMSystem;

// Reuses vanilla's own buttons (so their click handlers stay untouched) but moves them into
// a BleachHack-styled window frame (see MainMenuFx) instead of vanilla's default layout, and
// hides the vanilla logo/splash the window's own title replaces. All purely GUI-layer (button
// repositioning + DrawContext fills), no custom rendering pipeline - see MainMenuFx for why
// that matters.
@Mixin(TitleScreen.class)
public abstract class TitleScreenMenuMixin extends Screen {
    @Unique private boolean thm$renderEntered;
    @Unique private int thm$chromeLogState = -1;

    protected TitleScreenMenuMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("HEAD"))
    private void thm$logInitEntry(CallbackInfo ci) {
        thm$renderEntered = false;
        thm$chromeLogState = -1;
        THMAddon.LOG.info("[THM/Menu] TitleScreenMenuMixin.init entered; screen={}, size={}x{}, systemReady={}",
            this.getClass().getName(), this.width, this.height, THMSystem.get() != null);
    }

    @Inject(method = "extractRenderState", at = @At("HEAD"))
    private void thm$logRenderEntry(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks, CallbackInfo ci) {
        if (thm$renderEntered) return;
        thm$renderEntered = true;
        THMAddon.LOG.info("[THM/Menu] TitleScreenMenuMixin.extractRenderState entered; screen={}", this.getClass().getName());
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void thm$logInitReturn(CallbackInfo ci) {
        THMAddon.LOG.info("[THM/Menu] TitleScreenMenuMixin.init tail reached; children={}, outer setup follows", this.children().size());
    }

    // Chrome is drawn before super.render() (which draws the buttons) so the buttons sit on
    // top of the window frame, not under it. The particle trail is handled globally by
    // ScreenMixin (every world-not-loaded screen, not just this one).
    @Inject(method = "extractRenderState", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/Screen;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V"))
    private void thm$renderWindowChrome(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks, CallbackInfo ci) {
        int state = (THMSystem.get().mainMenuWindow.get() ? 1 : 0) | (MainMenuFx.previewMode ? 2 : 0);
        if (state != thm$chromeLogState) {
            thm$chromeLogState = state;
            THMAddon.LOG.info("[THM/Menu] TitleScreenMenuMixin.chrome hook reached; window={}, preview={}, action={}",
                (state & 1) != 0, MainMenuFx.previewMode, MainMenuFx.previewMode ? "skip preview" : (state & 1) != 0 ? "draw window" : "skip disabled window");
        }
        if (MainMenuFx.previewMode) return;

        MainMenuFx.renderWindow(context, this.font, this.width, this.height);
    }

    // Window chrome's "x"/"_" glyphs, wired up here since the window only exists while
    // TitleScreen is showing: "x" quits the game outright (this IS the main menu - there's
    // nothing to go "back" to). "_" doesn't touch the actual OS window - it minimizes the THM
    // window itself (turns mainMenuWindow off and relayouts back to vanilla's own button
    // positions), same as BleachHack's own click-gui panels collapsing rather than iconifying
    // the game. The always-present "THM Menu" button (see ScreenMixin) is what brings it
    // back afterwards.
    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void thm$handleChromeClick(MouseButtonEvent click, boolean doubled, CallbackInfoReturnable<Boolean> cir) {
        if (!THMSystem.get().mainMenuWindow.get() || MainMenuFx.previewMode) return;

        int[] bounds = MainMenuFx.windowBounds(this.width, this.height);
        int x1 = bounds[0], y1 = bounds[1], x2 = bounds[2], y2 = bounds[3];

        if (MainMenuFx.isCloseButton(x1, y1, x2, y2, click.x(), click.y())) {
            this.minecraft.stop();
            cir.setReturnValue(true);
        } else if (MainMenuFx.isMinimizeButton(x1, y1, x2, y2, click.x(), click.y())) {
            THMSystem.get().mainMenuWindow.set(false);
            this.rebuildWidgets();
            cir.setReturnValue(true);
        }
    }

    // Belt-and-suspenders: vanilla's own bottom-left "Minecraft <version>" text (drawn at the
    // very end of TitleScreen#render, after the logo/splash we suppress above) has stopped
    // showing up at some point while iterating on the window/blur rendering, for a reason not
    // fully root-caused. Redrawing it ourselves - guaranteed to run, since our own version text
    // inside the window is confirmed visible - is a lot more reliable than continuing to guess
    // at whichever of our render calls is responsible.
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void thm$restoreVersionText(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks, CallbackInfo ci) {
        if (!THMSystem.get().mainMenuWindow.get() || MainMenuFx.previewMode) return;
        String text = "Minecraft " + SharedConstants.getCurrentVersion().name();
        context.text(this.font, text, 2, this.height - 10, -1);
    }

    @Redirect(method = "extractRenderState", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/LogoRenderer;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IF)V"))
    private void thm$suppressLogo(LogoRenderer logoDrawer, GuiGraphicsExtractor context, int screenWidth, float alpha) {
        if (THMSystem.get().mainMenuWindow.get() || MainMenuFx.previewMode) return;
        logoDrawer.extractRenderState(context, screenWidth, alpha);
    }

    @Redirect(method = "extractRenderState", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/SplashRenderer;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;ILnet/minecraft/client/gui/Font;F)V"))
    private void thm$suppressSplash(SplashRenderer splashText, GuiGraphicsExtractor context, int screenWidth, Font textRenderer, float alpha) {
        if (THMSystem.get().mainMenuWindow.get() || MainMenuFx.previewMode) return;
        splashText.extractRenderState(context, screenWidth, textRenderer, alpha);
    }
}
