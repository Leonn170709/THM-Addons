/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.mixin;

import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.movement.NoSlow;
import net.minecraft.block.BlockState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xyz.thm.addon.interfaces.NoSlowAntiClimb;
import xyz.thm.addon.utils.AntiClimb;

import static meteordevelopment.meteorclient.MeteorClient.mc;

@Mixin(LivingEntity.class)
public abstract class LivingEntityClimbMixin {
    /** How far down the ground is looked for; deeper than this is always "more than a block". */
    @Unique private static final int THM_GROUND_SEARCH = 3;

    /** NoSlow anti-climb: ladders, vines and scaffolding stop acting as climbable. Local player only. */
    @Inject(method = "isClimbing", at = @At("HEAD"), cancellable = true)
    private void thm$antiClimb(CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this != mc.player || mc.world == null) return;
        NoSlow noSlow = Modules.get() == null ? null : Modules.get().get(NoSlow.class);
        if (noSlow == null || !noSlow.isActive()) return;

        NoSlowAntiClimb.Mode mode = ((NoSlowAntiClimb) noSlow).thm$antiClimbMode();
        if (mode == NoSlowAntiClimb.Mode.Always) cir.setReturnValue(false);
        else if (mode == NoSlowAntiClimb.Mode.Smart && thm$smartSuppress()) cir.setReturnValue(false);
    }

    @Unique
    private boolean thm$smartSuppress() {
        boolean falling = !mc.player.isOnGround() && mc.player.getVelocity().y < 0;
        return AntiClimb.suppress(falling, falling ? thm$dropToGround(mc.player.getBlockPos()) : 0);
    }

    /** Distance from the feet down to the first block you'd stand on; climbables don't count as ground. */
    @Unique
    private double thm$dropToGround(BlockPos feet) {
        double feetY = mc.player.getY();
        for (int i = 0; i <= THM_GROUND_SEARCH; i++) {
            BlockPos pos = feet.down(i);
            BlockState state = mc.world.getBlockState(pos);
            if (state.isIn(BlockTags.CLIMBABLE)) continue;
            VoxelShape shape = state.getCollisionShape(mc.world, pos);
            if (shape.isEmpty()) continue;
            return feetY - (pos.getY() + shape.getMax(Direction.Axis.Y));
        }
        return Double.MAX_VALUE;
    }
}
