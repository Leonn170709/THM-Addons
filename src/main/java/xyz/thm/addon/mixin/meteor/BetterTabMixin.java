package xyz.thm.addon.mixin.meteor;

import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.render.BetterTab;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.systems.friends.Friends;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.client.network.PlayerListEntry;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xyz.thm.addon.mixininterface.BetterTabThmSettings;
import xyz.thm.addon.system.THMSystem;
import xyz.thm.addon.utils.ThmMembers;

@Mixin(value = BetterTab.class, priority = 1001)
public class BetterTabMixin extends Module implements BetterTabThmSettings {
    public BetterTabMixin(Category category, String name, String description, String... aliases) {
        super(category, name, description, aliases);
    }

    private final SettingGroup sgTHM = settings.createGroup("THM");
    @Shadow @Final private Setting<Boolean> self;
    @Shadow @Final private Setting<Boolean> friends;

    private final Setting<Boolean> thmHighlightMembers = sgTHM.add(new BoolSetting.Builder()
        .name("highlight-thm-members")
        .description("Highlights THM members in the player tab list.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> thmUseRankColor = sgTHM.add(new BoolSetting.Builder()
        .name("use-rank-color")
        .description("Use the member's rank color instead of a single highlight color.")
        .defaultValue(true)
        .visible(thmHighlightMembers::get)
        .build()
    );

    private final Setting<SettingColor> thmHighlightColor = sgTHM.add(new ColorSetting.Builder()
        .name("highlight-color")
        .description("Highlight color for THM members in the tab list.")
        .defaultValue(new SettingColor(255, 217, 94, 255))
        .visible(() -> thmHighlightMembers.get() && !thmUseRankColor.get())
        .build()
    );

    @Override public Setting<Boolean> thm$getHighlightMembers() { return thmHighlightMembers; }
    @Override public Setting<Boolean> thm$getUseRankColor()      { return thmUseRankColor; }
    @Override public Setting<SettingColor> thm$getHighlightColor() { return thmHighlightColor; }

    @Inject(method = "getPlayerName", at = @At("RETURN"), cancellable = true)
    private void thmAddon$highlightThmMembers(PlayerListEntry entry, CallbackInfoReturnable<Text> cir) {
        if (!thmHighlightMembers.get()) return;

        String playerName = entry.getProfile().name();
        ThmMembers.Member member = ThmMembers.getMemberByMcName(playerName);
        if (member == null) return;

        THMSystem system = THMSystem.get();
        if (system != null) {
            String branchFilter = system.showBranch.get();
            if (!THMSystem.BRANCH_ALL.equalsIgnoreCase(branchFilter)) {
                if (THMSystem.BRANCH_PVP.equalsIgnoreCase(branchFilter) && !"PvP".equalsIgnoreCase(member.branch)) return;
                if (THMSystem.BRANCH_MAIN.equalsIgnoreCase(branchFilter) && !"Main".equalsIgnoreCase(member.branch)) return;
            }
        }

        // Let self/friends highlights take priority
        if (self.get() && mc.player != null &&
            entry.getProfile().id().equals(mc.player.getGameProfile().id())) return;
        if (friends.get() && Friends.get().isFriend(entry)) return;

        meteordevelopment.meteorclient.utils.render.color.Color color = thmUseRankColor.get()
            ? ThmMembers.getRankColor(member.rank)
            : thmHighlightColor.get();

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
