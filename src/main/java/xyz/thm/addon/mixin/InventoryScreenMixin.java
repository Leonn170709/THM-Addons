/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.mixin;

import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeUpdateListener;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.InventoryMenu;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.thm.addon.modules.Loadouts;

@Mixin(InventoryScreen.class)
public abstract class InventoryScreenMixin extends AbstractRecipeBookScreen<InventoryMenu>
    implements RecipeUpdateListener {
    public InventoryScreenMixin(InventoryMenu handler, RecipeBookComponent<?> recipeBook, Inventory inventory, Component title) {
        super(handler, recipeBook, inventory, title);
    }
    @Unique @Nullable
    private Loadouts loadouts = null;
    @Unique @Nullable
    private Button saveLoadoutButton = null;
    @Unique @Nullable
    private Button loadLoadoutButton = null;
    @Unique
    private int loadAnimTicks = 0;
    @Unique
    private void onSaveLoadoutButtonPress(Button btn) {
        if (loadouts == null) {
            Modules modules = Modules.get();
            if (modules == null ) return;
            loadouts = modules.get(Loadouts.class);
            if (loadouts == null) return;
        }
        loadouts.saveLoadout("quicksave");
        btn.setMessage(Component.nullToEmpty("Save"));
    }
    @Unique
    private void onLoadLoadoutButtonPress(Button btn) {
        if (loadouts == null) {
            Modules modules = Modules.get();
            if (modules == null ) return;
            loadouts = modules.get(Loadouts.class);
            if (loadouts == null) return;
        }
        loadouts.loadLoadout("quicksave");
        btn.setMessage(Component.nullToEmpty("Load"));
    }
    @Inject(method = "init", at = @At("TAIL"))
    private void mixinInit(CallbackInfo ci) {
        if (loadouts == null) {
            Modules modules = Modules.get();
            if (modules == null ) return;
            loadouts = modules.get(Loadouts.class);
            if (loadouts == null) return;
        }
        if (!loadouts.quickLoadout.get()) return;
        saveLoadoutButton = this.addRenderableWidget(
            Button.builder(
                    Component.nullToEmpty("Save"),
                    this::onSaveLoadoutButtonPress
                )
                .bounds(this.width / 2 - 42, this.height / 2 + 83, 42, 16)
                .tooltip(Tooltip.create(Component.nullToEmpty("§7§oSave your current inventory to Loadouts.")))
                .build()
        );
        loadLoadoutButton = this.addRenderableWidget(
            Button.builder(
                    Component.nullToEmpty("Load"),
                    this::onLoadLoadoutButtonPress
                )
                .bounds(this.width / 2, this.height / 2 + 83, 42, 16)
                .tooltip(Tooltip.create(Component.nullToEmpty("§7§oLoad your quicksave loadout.")))
                .build()
        );
        if (saveLoadoutButton != null) saveLoadoutButton.visible = loadouts.isActive();
        if (loadLoadoutButton != null) loadLoadoutButton.visible = loadouts.isActive();
    }
    @Inject(method = "render", at = @At("TAIL"))
    private void mixinRender(GuiGraphics context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (loadouts == null) {
            Modules modules = Modules.get();
            if (modules == null ) return;
            loadouts = modules.get(Loadouts.class);
            if (loadouts == null) return;
        }
        if (!loadouts.quickLoadout.get()) return;
        if (saveLoadoutButton != null) {
            saveLoadoutButton.visible = loadouts.isActive();
        }
        if (loadLoadoutButton != null) {
            loadLoadoutButton.visible = loadouts.isActive();
        }
    }
    @Inject(method = "containerTick", at = @At("HEAD"))
    private void animateButtons(CallbackInfo ci) {
        if (loadouts == null) {
            Modules modules = Modules.get();
            if (modules == null ) return;
            loadouts = modules.get(Loadouts.class);
            if (loadouts == null) return;
        }
        if (!loadouts.quickLoadout.get()) return;
        if (loadLoadoutButton == null) return;

        // ponytail: dots on the Load button only - saving is instant, sorting is the one in-flight state.
        if (loadouts.isActive() && !loadouts.isSorted) {
            loadLoadoutButton.setMessage(Component.nullToEmpty("Load" + ".".repeat((loadAnimTicks++ / 4) % 4)));
        } else {
            loadAnimTicks = 0;
            loadLoadoutButton.setMessage(Component.nullToEmpty("Load"));
        }
    }
}
