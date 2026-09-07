package xyz.thm.addon.utils.render;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.SkinTextures;
import xyz.thm.addon.mixin.accessor.PlayerModelPartsAccessor;

/** Ghost player that keeps a skin snapshot, since a logged out player has no player list entry left. */
public class SkinGhostPlayer extends OtherClientPlayerEntity {
    private final SkinTextures skin;

    public SkinGhostPlayer(ClientWorld world, GameProfile profile, SkinTextures skin) {
        super(world, profile);
        this.skin = skin;
    }

    public void setModelParts(byte modelParts) {
        getDataTracker().set(PlayerModelPartsAccessor.thm$getModelParts(), modelParts);
    }

    @Override
    public SkinTextures getSkin() {
        return skin != null ? skin : super.getSkin();
    }
}
