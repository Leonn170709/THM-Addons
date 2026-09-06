/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.mixin;

import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.thm.addon.waveycapes.CapeHolder;
import xyz.thm.addon.waveycapes.WaveyCapesConfig;
import xyz.thm.addon.waveycapes.sim.BasicSimulation;

@Mixin(LivingEntity.class)
public abstract class WaveyCapesEntityMixin extends Entity implements CapeHolder {

    @Unique private BasicSimulation thm$simulation;
    @Unique private boolean thm$dirty;
    @Unique private boolean thm$canUpdateGravityVector;

    public WaveyCapesEntityMixin(EntityType<?> type, World world) {
        super(type, world);
    }

    @Override public BasicSimulation getSimulation() { return thm$simulation; }
    @Override public void setSimulation(BasicSimulation sim) { thm$simulation = sim; }
    @Override public void setDirty() { thm$dirty = true; }
    @Override public void setGravityVectorRequest(boolean canUpdate) { thm$canUpdateGravityVector = canUpdate; }
    @Override public boolean canUpdateGravityVector() { return thm$canUpdateGravityVector; }

    @Inject(method = "tick", at = @At("TAIL"))
    private void thm$wavyCapesTick(CallbackInfo ci) {
        if (!((Object) this instanceof AbstractClientPlayerEntity)) return;
        AbstractClientPlayerEntity player = (AbstractClientPlayerEntity) (Object) this;
        if (!WaveyCapesConfig.enabled) return;
        if (player.getSkin().cape() == null) return;

        updateSimulation(16);
        setGravityVectorRequest(true);
        if (thm$dirty) {
            thm$dirty = false;
            getSimulation().applyMovement(new xyz.thm.addon.waveycapes.util.Vector3(1f, 1f, 0));
            for (int i = 0; i < 5; i++) simulate(player);
        }
        simulate(player);
    }
}
