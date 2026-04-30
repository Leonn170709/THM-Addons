package xyz.thm.addon.mixin.meteor;

import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LivingEntity.class)
public interface LivingEntityRotationAccessor {
    @Accessor("lastBodyYaw")
    void thm$setLastBodyYaw(float value);

    @Accessor("headYaw")
    void thm$setHeadYaw(float value);

    @Accessor("lastHeadYaw")
    void thm$setLastHeadYaw(float value);
}
