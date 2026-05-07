package xyz.thm.addon.mixin.accessor;

import meteordevelopment.meteorclient.gui.widgets.containers.WSection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = WSection.class, remap = false)
public interface WSectionAccessor {
    @Accessor("forcedHeight") void thm$setForcedHeight(double value);
    @Accessor("firstTime")    void thm$setFirstTime(boolean value);
    @Accessor("animProgress") double thm$getAnimProgress();
    @Accessor("animProgress") void thm$setAnimProgress(double value);
}
