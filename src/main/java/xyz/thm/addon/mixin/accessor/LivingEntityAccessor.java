package xyz.thm.addon.mixin.accessor;

import net.minecraft.entity.LimbAnimator;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LivingEntity.class)
public interface LivingEntityAccessor {
    @Accessor("limbAnimator")
    LimbAnimator thm$getLimbAnimator();
}
