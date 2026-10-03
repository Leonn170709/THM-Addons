/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.mixin.meteor;

import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.render.BetterTab;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xyz.thm.addon.system.THMSystem;
import xyz.thm.addon.utils.FastTab;
import xyz.thm.addon.utils.ThmMembers;

@Mixin(value = BetterTab.class, priority = 1001)
public class BetterTabMixin extends Module {
    public BetterTabMixin(Category category, String name, String description, String... aliases) {
        super(category, name, description, aliases);
    }

    @Shadow @Final private Setting<Boolean> self;
    @Shadow @Final private Setting<Boolean> friends;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void thmAddon$addFastTabSetting(CallbackInfo ci) {
        FastTab.enabled = settings.getDefaultGroup().add(new BoolSetting.Builder()
            .name("fast-tab")
            .description("Caches the tab list to stop FPS drops with many players.")
            .defaultValue(true)
            .build()
        );

        FastTab.heads = settings.getDefaultGroup().add(new BoolSetting.Builder()
            .name("tab-heads")
            .description("Draw player heads in the tab list (off = big FPS win).")
            .defaultValue(false)
            .build()
        );
    }

    @Inject(method = "getPlayerName", at = @At("RETURN"), cancellable = true)
    private void thmAddon$highlightThmMembers(PlayerInfo entry, CallbackInfoReturnable<Component> cir) {
        THMSystem system = THMSystem.get();
        if (system == null || !system.highlightInTab.get()) return;

        String playerName = entry.getProfile().name();
        ThmMembers.Member member = ThmMembers.getMemberByMcName(playerName);
        if (member == null) return;
        if (ThmMembers.isKillOnSight(member)) return;
        if (ThmMembers.isIgnore(member)) return;

        String branchFilter = system.showBranch.get();
        if (!THMSystem.BRANCH_ALL.equalsIgnoreCase(branchFilter)) {
            if (THMSystem.BRANCH_PVP.equalsIgnoreCase(branchFilter) && !"PvP".equalsIgnoreCase(member.branch)) return;
            if (THMSystem.BRANCH_MAIN.equalsIgnoreCase(branchFilter) && !"Main".equalsIgnoreCase(member.branch)) return;
        }

        // Let self/friends highlights take priority
        if (self.get() && mc.player != null &&
            entry.getProfile().id().equals(mc.player.getGameProfile().id())) return;
        if (friends.get() && Friends.get().isFriend(entry)) return;

        meteordevelopment.meteorclient.utils.render.color.Color color = system.useRankColor.get()
            ? ThmMembers.getRankColor(member.rank)
            : system.highlightColor.get();

        TextColor textColor = TextColor.fromRgb((color.r << 16) | (color.g << 8) | color.b);

        Component original = cir.getReturnValue();
        if (original == null) return;

        cir.setReturnValue(rebuildNode(original, textColor));
    }

    // Recursively rebuilds the text tree, forcing our color on every node
    // while preserving structure (prefix/suffix siblings) and non-color styles.
    private static MutableComponent rebuildNode(Component text, TextColor color) {
        MutableComponent node = ((MutableComponent) text).plainCopy()
            .withStyle(s -> s.withColor(color));

        for (Component sibling : text.getSiblings()) {
            node.append(rebuildNode(sibling, color));
        }

        return node;
    }
}
