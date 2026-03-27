package xyz.thm.addon.mixin.meteor;

import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.render.Nametags;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.renderer.text.TextRenderer;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.player.NameProtect;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.client.gl.RenderPipelines;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.Unique;
import xyz.thm.addon.system.THMSystem;
import xyz.thm.addon.utils.ThmMembers;

@Mixin(value = Nametags.class, priority = 1001)
public abstract class NametagsMixin extends Module {
    public NametagsMixin(Category category, String name, String description, String... aliases) {
        super(category, name, description, aliases);
    }

    @Unique private static final Identifier THM_ICON = Identifier.of("icon", "icon.png");
    @Unique private static final int THM_ICON_SIZE = 64;
    @Unique private static final int THM_ICON_PAD = 2;

    @Unique private DrawContext thm$drawContext;
    @Unique private PlayerEntity thm$player;
    @Inject(method = "renderNametagPlayer", at = @At("HEAD"))
    private void thmAddon$captureContext(Render2DEvent event, PlayerEntity player, boolean shadow, CallbackInfo ci) {
        thm$drawContext = event.drawContext;
        thm$player = player;
    }

    @Inject(method = "renderNametagPlayer", at = @At("RETURN"))
    private void thmAddon$clearContext(Render2DEvent event, PlayerEntity player, boolean shadow, CallbackInfo ci) {
        thm$drawContext = null;
        thm$player = null;
    }

    @Redirect(method = "renderNametagPlayer", at = @At(value = "INVOKE", target = "Lmeteordevelopment/meteorclient/utils/player/PlayerUtils;getPlayerColor(Lnet/minecraft/entity/player/PlayerEntity;Lmeteordevelopment/meteorclient/utils/render/color/Color;)Lmeteordevelopment/meteorclient/utils/render/color/Color;"))
    private Color thmAddon$overrideNameColor(PlayerEntity player, Color originalColor) {
        Color baseColor = PlayerUtils.getPlayerColor(player, originalColor);

        THMSystem system = THMSystem.get();
        if (system == null || !system.highlightNametags.get()) return baseColor;
        ThmMembers.Member member = thm$getEligibleMember(player, system);
        if (member == null) return baseColor;

        return system.useRankColor.get()
            ? ThmMembers.getRankColor(member.rank)
            : system.highlightColor.get();
    }

    @Redirect(method = "renderNametagPlayer", at = @At(value = "INVOKE", target = "Lmeteordevelopment/meteorclient/renderer/text/TextRenderer;getWidth(Ljava/lang/String;Z)D"))
    private double thmAddon$iconWidth(TextRenderer text, String string, boolean shadow) {
        double width = text.getWidth(string, shadow);

        if (!thm$shouldRenderIcon(string)) return width;

        double iconSize = text.getHeight(shadow);
        return width + iconSize + THM_ICON_PAD;
    }

    @Redirect(method = "renderNametagPlayer", at = @At(value = "INVOKE", target = "Lmeteordevelopment/meteorclient/renderer/text/TextRenderer;render(Ljava/lang/String;DDLmeteordevelopment/meteorclient/utils/render/color/Color;Z)D"))
    private double thmAddon$renderNameWithIcon(TextRenderer text, String string, double x, double y, Color color, boolean shadow) {
        if (!thm$shouldRenderIcon(string)) {
            return text.render(string, x, y, color, shadow);
        }

        double iconSize = text.getHeight(shadow);
        if (thm$drawContext != null) {
            int ix = (int) Math.round(x);
            int iy = (int) Math.round(y);
            thm$drawContext.drawTexture(
                RenderPipelines.GUI_TEXTURED,
                THM_ICON,
                ix,
                iy,
                0f,
                0f,
                (int) Math.round(iconSize),
                (int) Math.round(iconSize),
                THM_ICON_SIZE,
                THM_ICON_SIZE,
                THM_ICON_SIZE,
                THM_ICON_SIZE
            );
        }

        return text.render(string, x + iconSize + THM_ICON_PAD, y, color, shadow);
    }

    @Unique
    private boolean thm$shouldRenderIcon(String string) {
        THMSystem system = THMSystem.get();
        if (system == null || !system.highlightNametags.get() || !system.showNametagIcon.get()) return false;
        if (thm$player == null || thm$getEligibleMember(thm$player, system) == null) return false;
        return string.equals(thm$getDisplayName(thm$player));
    }

    @Unique
    private String thm$getDisplayName(PlayerEntity player) {
        if (player == null) return "";
        if (player == mc.player) return Modules.get().get(NameProtect.class).getName(player.getName().getString());
        return player.getName().getString();
    }

    @Unique
    private ThmMembers.Member thm$getEligibleMember(PlayerEntity player, THMSystem system) {
        if (player == null) return null;
        ThmMembers.Member member = ThmMembers.getMemberByMcName(player.getGameProfile().name());
        if (member == null) return null;

        String branchFilter = system.showBranch.get();
        if (!THMSystem.BRANCH_ALL.equalsIgnoreCase(branchFilter)) {
            if (THMSystem.BRANCH_PVP.equalsIgnoreCase(branchFilter) && !"PvP".equalsIgnoreCase(member.branch)) return null;
            if (THMSystem.BRANCH_MAIN.equalsIgnoreCase(branchFilter) && !"Main".equalsIgnoreCase(member.branch)) return null;
        }

        return member;
    }
}
