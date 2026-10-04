/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.network.chat.Component;
import xyz.thm.addon.gui.MainMenuSettingsScreen;
import xyz.thm.addon.shaders.ShaderManager;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xyz.thm.addon.gui.DeathChatScreen;
import xyz.thm.addon.gui.MainMenuFx;
import xyz.thm.addon.gui.ThmStyledButtons;
import xyz.thm.addon.THMAddon;
import xyz.thm.addon.system.THMSystem;

@Mixin(Screen.class)
public abstract class ScreenMixin {

    @Shadow @Final protected Minecraft minecraft;
    @Shadow protected abstract <T extends GuiEventListener & Renderable & NarratableEntry> T addRenderableWidget(T widget);

    @Inject(method = {"init(II)V", "resize", "rebuildWidgets"}, at = @At("TAIL"))
    private void thm$layoutTitleButtons(CallbackInfo ci) {
        if (this.minecraft.level != null) return;
        Screen screen = (Screen) (Object) this;
        boolean title = screen instanceof TitleScreen;
        boolean menuPresent = screen.children().stream().anyMatch(child -> child instanceof AbstractWidget widget
            && widget.getMessage().getString().equals("THM Menu"));
        if (title && THMSystem.get() != null && !menuPresent) {
            // The inner TitleScreen.init can be cancelled by another client.
            ShaderManager.reroll();
            AbstractWidget menu = addRenderableWidget(Button.builder(Component.literal("THM Menu"), b ->
                this.minecraft.gui.setScreen(new MainMenuSettingsScreen(screen)))
                .bounds(screen.width / 2 - 45, screen.height - 22, 90, 16).build());
            ThmStyledButtons.mark(menu);
            if (MainMenuFx.previewMode) {
                AbstractWidget showUi = addRenderableWidget(Button.builder(Component.literal("Show UI"), b -> {
                    MainMenuFx.previewMode = false;
                    this.minecraft.gui.setScreen(new MainMenuSettingsScreen(screen));
                }).bounds(screen.width / 2 - 45, screen.height - 22, 90, 16).build());
                ThmStyledButtons.mark(showUi);
                for (var child : screen.children()) {
                    if (child instanceof AbstractWidget widget && widget != showUi) widget.visible = false;
                }
            }
            THMAddon.LOG.info("[THM/Menu] ScreenMixin title setup completed; shader={}, THM Menu button added", ShaderManager.active());
        }
        if (title && THMSystem.get() != null) MainMenuFx.layoutTitleButtons(screen);
        THMSystem system = THMSystem.get();
        THMAddon.LOG.info("[THM/Menu] ScreenMixin.init/resize/rebuild completed; screen={}, vanillaTitle={}, systemReady={}, window={}, preview={}, children={}, styled={}",
            screen.getClass().getName(), title, system != null, system != null && system.mainMenuWindow.get(),
            MainMenuFx.previewMode, screen.children().size(), screen.children().stream().filter(ThmStyledButtons::isStyled).count());
    }

    // Base-screen extraction also covers title and selection screens.
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void thm$renderMenuParticles(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks, CallbackInfo ci) {
        if (this.minecraft.level != null) return;

        MainMenuFx.tick(mouseX, mouseY);
        MainMenuFx.renderParticles(context);
    }

    // Lets the chat/command key open chat on the death screen - it is client-side only, the server
    // accepts chat from a dead player. DeathScreen doesn't override keyPressed, so this targets
    // Screen's and filters on the instance.
    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void thm$chatOnDeathScreen(KeyEvent input, CallbackInfoReturnable<Boolean> cir) {
        if (!((Object) this instanceof DeathScreen)) return;

        Minecraft mc = Minecraft.getInstance();
        boolean command = mc.options.keyCommand.matches(input);
        if (!command && !mc.options.keyChat.matches(input)) return;

        mc.gui.setScreen(new DeathChatScreen((Screen) (Object) this, command ? "/" : ""));
        cir.setReturnValue(true);
    }
}
