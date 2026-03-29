package xyz.thm.addon.mixin.meteor;

import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.render.BetterTab;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xyz.thm.addon.system.THMSystem;
import xyz.thm.addon.utils.ThmMembers;

@Mixin(value = BetterTab.class, priority = 1001)
public class BetterTabMixin extends Module {
    public BetterTabMixin(Category category, String name, String description, String... aliases) {
        super(category, name, description, aliases);
    }

    @Shadow @Final private Setting<Boolean> self;
    @Shadow @Final private Setting<Boolean> friends;

    @Inject(method = "getPlayerName", at = @At("RETURN"), cancellable = true)
    private void thmAddon$highlightThmMembers(PlayerListEntry entry, CallbackInfoReturnable<Text> cir) {
        THMSystem system = THMSystem.get();
        if (system == null || !system.highlightInTab.get()) return;

        String playerName = entry.getProfile().getName();
        ThmMembers.Member member = ThmMembers.getMemberByMcName(playerName);
        if (member == null) return;

        String branchFilter = system.showBranch.get();
        if (!THMSystem.BRANCH_ALL.equalsIgnoreCase(branchFilter)) {
            if (THMSystem.BRANCH_PVP.equalsIgnoreCase(branchFilter) && !"PvP".equalsIgnoreCase(member.branch)) return;
            if (THMSystem.BRANCH_MAIN.equalsIgnoreCase(branchFilter) && !"Main".equalsIgnoreCase(member.branch)) return;
        }

        // Let self/friends highlights take priority
        if (self.get() && mc.player != null &&
            entry.getProfile().getId().equals(mc.player.getGameProfile().getId())) return;
        if (friends.get() && Friends.get().isFriend(entry)) return;

        meteordevelopment.meteorclient.utils.render.color.Color color = system.useRankColor.get()
            ? ThmMembers.getRankColor(member.rank)
            : system.highlightColor.get();

        TextColor textColor = TextColor.fromRgb((color.r << 16) | (color.g << 8) | color.b);

        Text original = cir.getReturnValue();
        if (original == null) return;

        cir.setReturnValue(rebuildNode(original, textColor));
    }

    // Recursively rebuilds the text tree, forcing our color on every node
    // while preserving structure (prefix/suffix siblings) and non-color styles.
    private static MutableText rebuildNode(Text text, TextColor color) {
        MutableText node = ((MutableText) text).copyContentOnly()
            .styled(s -> s.withColor(color));

        for (Text sibling : text.getSiblings()) {
            node.append(rebuildNode(sibling, color));
        }

        return node;
    }
}
