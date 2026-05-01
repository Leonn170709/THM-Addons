package xyz.thm.addon.mixin.accessor;

import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Entity.class)
public interface EntityPositionAccessor {
    @Accessor("lastX")
    void thm$setLastX(double value);

    @Accessor("lastY")
    void thm$setLastY(double value);

    @Accessor("lastZ")
    void thm$setLastZ(double value);

    @Accessor("lastRenderX")
    void thm$setLastRenderX(double value);

    @Accessor("lastRenderY")
    void thm$setLastRenderY(double value);

    @Accessor("lastRenderZ")
    void thm$setLastRenderZ(double value);
}
