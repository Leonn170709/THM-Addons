/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.mixin;

import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.suggestion.Suggestions;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.thm.addon.utils.kitbot.KitbotChatCommandParser;
import xyz.thm.addon.utils.kitbot.KitbotChatRouter;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import net.minecraft.util.FormattedCharSequence;

@Mixin(CommandSuggestions.class)
public abstract class ChatInputSuggestorMixin {
    @Shadow @Final private Minecraft minecraft;
    @Shadow @Final private EditBox input;
    @Shadow private ParseResults<ClientSuggestionProvider> currentParse;
    @Shadow private CompletableFuture<Suggestions> pendingSuggestions;
    @Shadow private boolean allowSuggestions;
    @Shadow boolean keepSuggestions;
    @Shadow @Final private List<FormattedCharSequence> commandUsage;

    @Shadow public abstract void hide();
    @Shadow public abstract void showSuggestions(boolean narrateFirstSuggestion);

    @Inject(method = "updateCommandInfo", at = @At("HEAD"), cancellable = true)
    private void thm$refreshKitbotSuggestions(CallbackInfo ci) {
        if (!KitbotChatRouter.isEnabled()) return;

        String text = input.getValue();
        if (text == null || text.isEmpty() || text.charAt(0) != '$') return;

        ci.cancel();

        currentParse = null;
        if (!keepSuggestions) {
            input.setSuggestion(null);
            hide();
        }
        commandUsage.clear();

        Suggestions suggestions = KitbotChatCommandParser.buildSuggestions(
            text,
            input.getCursorPosition(),
            KitbotChatCommandParser.getOnlinePlayerNames()
        );
        pendingSuggestions = CompletableFuture.completedFuture(suggestions);

        if (suggestions.isEmpty()) {
            hide();
            return;
        }

        if (allowSuggestions && minecraft.options.autoSuggestions().get()) {
            showSuggestions(false);
        }
    }
}
