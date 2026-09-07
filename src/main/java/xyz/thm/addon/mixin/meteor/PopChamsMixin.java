/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.mixin.meteor;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.render.PopChams;
import net.minecraft.entity.EntityStatuses;
import net.minecraft.entity.LimbAnimator;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import meteordevelopment.meteorclient.mixin.AbstractClientPlayerEntityAccessor;
import xyz.thm.addon.mixin.accessor.LimbAnimatorAccessor;
import xyz.thm.addon.mixin.accessor.LivingEntityAccessor;
import xyz.thm.addon.mixin.accessor.PlayerModelPartsAccessor;

import java.util.List;

import static meteordevelopment.meteorclient.MeteorClient.mc;

@Mixin(value = PopChams.class, remap = false)
public abstract class PopChamsMixin {
    @Shadow private List<?> ghosts;

    @Unique private Setting<Boolean> thm$captureLimbAnimation;
    @Unique private Setting<Boolean> thm$renderSkin;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void thm$init(CallbackInfo ci) {
        SettingGroup sgThm = ((Module) (Object) this).settings.createGroup("THM");
        thm$captureLimbAnimation = sgThm.add(new BoolSetting.Builder()
            .name("capture-limb-animation")
            .description("Keep arm and leg motion from the pop moment.")
            .defaultValue(true)
            .build()
        );
        thm$renderSkin = sgThm.add(new BoolSetting.Builder()
            .name("render-skin")
            .description("Render the popping player's skin instead of the wireframe.")
            .defaultValue(true)
            .build()
        );
        sgThm.add(new BoolSetting.Builder()
            .name("skin-through-walls")
            .description("Show the skin through solid blocks.")
            .defaultValue(false)
            .visible(thm$renderSkin::get)
            .build()
        );
    }

    @Inject(method = "onReceivePacket", at = @At("TAIL"))
    private void thm$captureLimbs(PacketEvent.Receive event, CallbackInfo ci) {
        if (mc.world == null || !(event.packet instanceof EntityStatusS2CPacket p)) return;
        if (p.getStatus() != EntityStatuses.USE_TOTEM_OF_UNDYING) return;
        if (!(p.getEntity(mc.world) instanceof PlayerEntity player) || player == mc.player) return;

        Object ghost;
        synchronized (ghosts) {
            if (ghosts.isEmpty()) return;
            ghost = ghosts.getLast();
        }

        // Ghosts get a random profile, so point them at the popping player's list entry for their skin.
        if (mc.getNetworkHandler() != null) {
            ((AbstractClientPlayerEntityAccessor) ghost).meteor$setPlayerListEntry(mc.getNetworkHandler().getPlayerListEntry(player.getUuid()));
        }

        // The wireframe would trace the outer skin layer too, so only the skin render gets it.
        if (thm$renderSkin.get()) {
            ((PlayerEntity) ghost).getDataTracker().set(PlayerModelPartsAccessor.thm$getModelParts(),
                player.getDataTracker().get(PlayerModelPartsAccessor.thm$getModelParts()));
        }

        if (!thm$captureLimbAnimation.get()) return;

        LimbAnimator source = ((LivingEntityAccessor) player).thm$getLimbAnimator();
        LimbAnimator target = ((LivingEntityAccessor) ghost).thm$getLimbAnimator();
        float amplitude = source.getAmplitude(1);

        // Freeze at the captured swing phase/amount instead of animating on.
        ((LimbAnimatorAccessor) target).thm$setAnimationProgress(source.getAnimationProgress());
        ((LimbAnimatorAccessor) target).thm$setLastSpeed(amplitude);
        ((LimbAnimatorAccessor) target).thm$setSpeedInternal(amplitude);
        ((LimbAnimatorAccessor) target).thm$setTimeScale(0);
    }
}
