/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.mixin.accessor;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.WalkAnimationState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LivingEntity.class)
public interface LivingEntityAccessor {
    @Accessor("walkAnimation")
    WalkAnimationState thm$getLimbAnimator();

    @Accessor("yBodyRotO")
    void thm$setLastBodyYaw(float value);

    @Accessor("yHeadRot")
    void thm$setHeadYaw(float value);

    @Accessor("yHeadRotO")
    void thm$setLastHeadYaw(float value);
}
