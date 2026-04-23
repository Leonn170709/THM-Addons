package xyz.thm.addon.mixin;

import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.suggestion.Suggestions;
import meteordevelopment.meteorclient.MeteorClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatInputSuggestor;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.network.ClientCommandSource;
import net.minecraft.text.OrderedText;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.thm.addon.utils.KitbotChatCommandParser;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Mixin(ChatInputSuggestor.class)
public abstract class ChatInputSuggestorMixin {
    @Shadow @Final private MinecraftClient client;
    @Shadow @Final private TextFieldWidget textField;
    @Shadow private ParseResults<ClientCommandSource> parse;
    @Shadow private CompletableFuture<Suggestions> pendingSuggestions;
    @Shadow private boolean windowActive;
    @Shadow boolean completingSuggestions;
    @Shadow @Final private List<OrderedText> messages;

    @Shadow public abstract void clearWindow();
    @Shadow public abstract void show(boolean narrateFirstSuggestion);

    @Inject(method = "refresh", at = @At("HEAD"), cancellable = true)
    private void thm$refreshKitbotSuggestions(CallbackInfo ci) {
        String text = textField.getText();
        if (text == null || text.isEmpty() || text.charAt(0) != '$') return;

        ci.cancel();

        parse = null;
        if (!completingSuggestions) {
            textField.setSuggestion(null);
            clearWindow();
        }
        messages.clear();

        Suggestions suggestions = KitbotChatCommandParser.buildSuggestions(
            text,
            textField.getCursor(),
            KitbotChatCommandParser.getOnlinePlayerNames()
        );
        pendingSuggestions = CompletableFuture.completedFuture(suggestions);

        if (suggestions.isEmpty()) {
            clearWindow();
            return;
        }

        if (windowActive && client.options.getAutoSuggestions().getValue()) {
            show(false);
        }
    }
}
