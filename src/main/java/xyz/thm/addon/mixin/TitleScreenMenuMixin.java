/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.mixin;

import net.minecraft.SharedConstants;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.LogoRenderer;
import net.minecraft.client.gui.components.SplashRenderer;
import net.minecraft.client.gui.components.SpriteIconButton;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xyz.thm.addon.gui.MainMenuFx;
import xyz.thm.addon.gui.MainMenuSettingsScreen;
import xyz.thm.addon.gui.ThmStyledButtons;
import xyz.thm.addon.shaders.ShaderBackground;
import xyz.thm.addon.shaders.ShaderManager;
import xyz.thm.addon.system.THMSystem;

import java.util.ArrayList;
import java.util.List;

// Reuses vanilla's own buttons (so their click handlers stay untouched) but moves them into
// a BleachHack-styled window frame (see MainMenuFx) instead of vanilla's default layout, and
// hides the vanilla logo/splash the window's own title replaces. All purely GUI-layer (button
// repositioning + DrawContext fills), no custom rendering pipeline - see MainMenuFx for why
// that matters.
@Mixin(TitleScreen.class)
public abstract class TitleScreenMenuMixin extends Screen {

    protected TitleScreenMenuMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void thm$layoutWindow(CallbackInfo ci) {
        ShaderManager.reroll();

        if (THMSystem.get().mainMenuWindow.get()) {
            int[] bounds = MainMenuFx.windowBounds(this.width, this.height);
            int x1 = bounds[0], y1 = bounds[1], x2 = bounds[2], y2 = bounds[3];
            int h = y2 - y1;
            int centerX = x1 + (x2 - x1) / 2;
            int maxY = y1 + Mth.clamp(h / 4 + 119, 0, h - 22);

            thm$repositionByLabel("menu.singleplayer", centerX - 100, y1 + h / 4 + 38);
            thm$repositionByLabel("menu.multiplayer", centerX - 100, y1 + h / 4 + 62);
            thm$repositionByLabel("menu.online", centerX - 100, y1 + h / 4 + 86);
            thm$repositionByLabel("menu.options", centerX - 100, maxY);
            thm$repositionByLabel("menu.quit", centerX + 2, maxY);

            List<SpriteIconButton> iconButtons = new ArrayList<>();
            for (GuiEventListener el : this.children()) {
                if (el instanceof SpriteIconButton icon) iconButtons.add(icon);
            }
            // Icon-only buttons (language/accessibility) keep their vanilla look - just repositioned,
            // not re-skinned, since BleachHack's own recreation doesn't have icon buttons either.
            if (!iconButtons.isEmpty()) iconButtons.get(0).setPosition(centerX - 124, maxY);
            if (iconButtons.size() > 1) iconButtons.get(1).setPosition(centerX + 104, maxY);
        }

        // The "THM Menu" button is the only way into the settings screen (which now also hosts the
        // shader preview toggle), so it lives in the bottom-left corner - always reachable
        // regardless of window on/off.
        AbstractWidget menuButton = this.addRenderableWidget(Button.builder(Component.literal("THM Menu"), b ->
                this.minecraft.setScreen(new MainMenuSettingsScreen(this)))
            .bounds(6, this.height - 22, 90, 16)
            .build());
        ThmStyledButtons.mark(menuButton);

        // Preview mode strips everything down to the raw shader; the toggle to *enter* it lives in
        // the settings screen, but the exit has to live here since that screen is hidden meanwhile.
        if (MainMenuFx.previewMode) {
            AbstractWidget showUi = this.addRenderableWidget(Button.builder(Component.literal("Show UI"), b -> {
                    MainMenuFx.previewMode = false;
                    this.minecraft.setScreen(new MainMenuSettingsScreen(this));
                })
                .bounds(6, this.height - 22, 90, 16)
                .build());
            ThmStyledButtons.mark(showUi);

            for (GuiEventListener el : this.children()) {
                if (el instanceof AbstractWidget widget && widget != showUi) {
                    widget.visible = false;
                }
            }
        }
    }

    private void thm$repositionByLabel(String i18nKey, int x, int y) {
        String label = I18n.get(i18nKey);
        for (GuiEventListener el : this.children()) {
            if (el instanceof AbstractWidget widget && widget.getMessage().getString().equals(label)) {
                widget.setPosition(x, y);
                ThmStyledButtons.mark(widget);
                return;
            }
        }
    }

    // Chrome is drawn before super.render() (which draws the buttons) so the buttons sit on
    // top of the window frame, not under it. The particle trail is handled globally by
    // ScreenMixin (every world-not-loaded screen, not just this one).
    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/Screen;render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V"))
    private void thm$renderWindowChrome(GuiGraphics context, int mouseX, int mouseY, float deltaTicks, CallbackInfo ci) {
        if (MainMenuFx.previewMode) return;

        if (THMSystem.get().mainMenuWindow.get()) {
            int[] bounds = MainMenuFx.windowBounds(this.width, this.height);
            ShaderBackground.renderBlurredRegion(bounds[0], bounds[1], bounds[2], bounds[3], THMSystem.get().mainMenuBlur.get());
        }

        MainMenuFx.renderWindow(context, this.font, this.width, this.height);
    }

    // Window chrome's "x"/"_" glyphs, wired up here since the window only exists while
    // TitleScreen is showing: "x" quits the game outright (this IS the main menu - there's
    // nothing to go "back" to). "_" doesn't touch the actual OS window - it minimizes the THM
    // window itself (turns mainMenuWindow off and relayouts back to vanilla's own button
    // positions), same as BleachHack's own click-gui panels collapsing rather than iconifying
    // the game. The always-present "THM Menu" button (see thm$layoutWindow) is what brings it
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
    @Inject(method = "render", at = @At("TAIL"))
    private void thm$restoreVersionText(GuiGraphics context, int mouseX, int mouseY, float deltaTicks, CallbackInfo ci) {
        if (!THMSystem.get().mainMenuWindow.get() || MainMenuFx.previewMode) return;
        String text = "Minecraft " + SharedConstants.getCurrentVersion().name();
        context.drawString(this.font, text, 2, this.height - 10, -1);
    }

    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/LogoRenderer;renderLogo(Lnet/minecraft/client/gui/GuiGraphics;IF)V"))
    private void thm$suppressLogo(LogoRenderer logoDrawer, GuiGraphics context, int screenWidth, float alpha) {
        if (THMSystem.get().mainMenuWindow.get() || MainMenuFx.previewMode) return;
        logoDrawer.renderLogo(context, screenWidth, alpha);
    }

    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/SplashRenderer;render(Lnet/minecraft/client/gui/GuiGraphics;ILnet/minecraft/client/gui/Font;F)V"))
    private void thm$suppressSplash(SplashRenderer splashText, GuiGraphics context, int screenWidth, Font textRenderer, float alpha) {
        if (THMSystem.get().mainMenuWindow.get() || MainMenuFx.previewMode) return;
        splashText.render(context, screenWidth, textRenderer, alpha);
    }
}
