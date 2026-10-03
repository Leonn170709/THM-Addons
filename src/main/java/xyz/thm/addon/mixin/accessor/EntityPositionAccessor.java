/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.mixin.accessor;

import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Entity.class)
public interface EntityPositionAccessor {
    @Accessor("xo")
    void thm$setLastX(double value);

    @Accessor("yo")
    void thm$setLastY(double value);

    @Accessor("zo")
    void thm$setLastZ(double value);

    @Accessor("xOld")
    void thm$setLastRenderX(double value);

    @Accessor("yOld")
    void thm$setLastRenderY(double value);

    @Accessor("zOld")
    void thm$setLastRenderZ(double value);
}
