package xyz.thm.addon.mixin.accessor;

import net.minecraft.entity.PlayerLikeEntity;
import net.minecraft.entity.data.TrackedData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PlayerLikeEntity.class)
public interface PlayerModelPartsAccessor {
    @Accessor("PLAYER_MODE_CUSTOMIZATION_ID")
    static TrackedData<Byte> thm$getModelParts() {
        throw new AssertionError();
    }
}
