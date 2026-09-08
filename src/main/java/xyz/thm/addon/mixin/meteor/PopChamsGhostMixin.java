/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.mixin.meteor;

import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.mixin.AbstractClientPlayerEntityAccessor;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.Settings;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.render.PopChams;
import meteordevelopment.meteorclient.utils.render.WireframeEntityRenderer;
import meteordevelopment.meteorclient.utils.render.color.Color;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LimbAnimator;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.thm.addon.interfaces.GhostPose;
import xyz.thm.addon.mixin.accessor.LivingEntityAccessor;
import xyz.thm.addon.mixin.accessor.PlayerModelPartsAccessor;
import xyz.thm.addon.utils.render.GhostRenderer;

import static meteordevelopment.meteorclient.MeteorClient.mc;

@Mixin(targets = "meteordevelopment.meteorclient.systems.modules.render.PopChams$GhostPlayer", remap = false)
public abstract class PopChamsGhostMixin implements GhostPose {
    @Shadow private double timer;

    @Unique private static Setting<Boolean> thm$captureLimbs;
    @Unique private static Setting<Boolean> thm$renderSkin;
    @Unique private static Setting<Boolean> thm$throughWalls;
    @Unique private static Setting<Double> thm$transparency;
    @Unique private static Setting<Boolean> thm$fadeOut;
    @Unique private static Setting<Double> thm$renderTime;

    @Unique
    private static boolean thm$settings() {
        if (thm$renderSkin == null) {
            Settings settings = Modules.get().get(PopChams.class).settings;
            thm$captureLimbs = settings.get("capture-limb-animation", Boolean.class);
            thm$throughWalls = settings.get("skin-through-walls", Boolean.class);
            thm$transparency = settings.get("transparency", Double.class);
            thm$fadeOut = settings.get("fade-out", Boolean.class);
            thm$renderTime = settings.get("render-time", Double.class);
            thm$renderSkin = settings.get("render-skin", Boolean.class);
        }

        return thm$renderSkin != null && thm$captureLimbs != null && thm$throughWalls != null
            && thm$transparency != null && thm$fadeOut != null && thm$renderTime != null;
    }

    @Unique private float thm$limbPos;
    @Unique private float thm$limbAmplitude;

    @Override
    public float thm$limbPos() {
        return thm$limbPos;
    }

    @Override
    public float thm$limbAmplitude() {
        return thm$limbAmplitude;
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void thm$capture(PopChams outer, PlayerEntity player, CallbackInfo ci) {
        if (!thm$settings()) return;

        // Ghosts get a random profile, so point them at the popping player's list entry for their skin.
        if (mc.getNetworkHandler() != null) {
            ((AbstractClientPlayerEntityAccessor) this).meteor$setPlayerListEntry(mc.getNetworkHandler().getPlayerListEntry(player.getUuid()));
        }

        // The wireframe would trace the outer skin layer too, so only the skin render gets it.
        if (thm$renderSkin.get()) {
            ((PlayerEntity) (Object) this).getDataTracker().set(PlayerModelPartsAccessor.thm$getModelParts(),
                player.getDataTracker().get(PlayerModelPartsAccessor.thm$getModelParts()));
        }

        if (thm$captureLimbs.get()) {
            LimbAnimator source = ((LivingEntityAccessor) player).thm$getLimbAnimator();
            thm$limbPos = source.getAnimationProgress();
            thm$limbAmplitude = source.getAmplitude(1);
        }
    }

    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Lmeteordevelopment/meteorclient/utils/render/WireframeEntityRenderer;render"))
    private void thm$renderGhost(Render3DEvent event, Entity entity, double scale, Color sideColor, Color lineColor, ShapeMode shapeMode) {
        if (!thm$settings() || !thm$renderSkin.get()) {
            WireframeEntityRenderer.render(event, entity, scale, sideColor, lineColor, shapeMode);
            return;
        }

        float alpha = (float) (1 - thm$transparency.get());
        if (thm$fadeOut.get()) alpha *= (float) Math.max(0, 1 - timer / thm$renderTime.get());

        if (thm$throughWalls.get()) GhostRenderer.renderThroughWalls(event, entity, scale, alpha);
        else GhostRenderer.submit(entity, scale, alpha);
    }
}
