package xyz.thm.addon.mixin.accessor;

import net.minecraft.entity.LimbAnimator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LimbAnimator.class)
public interface LimbAnimatorAccessor {
    @Accessor("lastSpeed")
    void thm$setLastSpeed(float value);

    @Accessor("speed")
    void thm$setSpeedInternal(float value);

    @Accessor("animationProgress")
    void thm$setAnimationProgress(float value);

    @Accessor("timeScale")
    void thm$setTimeScale(float value);
}
