/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.mixin.meteor;

import com.mojang.authlib.GameProfile;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.render.LogoutSpots;
import meteordevelopment.meteorclient.utils.render.WireframeEntityRenderer;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.LimbAnimator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.thm.addon.interfaces.LogoutSpotsPlayers;
import xyz.thm.addon.interfaces.LogoutSpotsPoseData;
import xyz.thm.addon.mixin.accessor.*;
import xyz.thm.addon.utils.render.GhostRenderer;
import xyz.thm.addon.utils.render.SkinGhostPlayer;

import java.util.*;

import static meteordevelopment.meteorclient.MeteorClient.mc;

@Mixin(value = LogoutSpots.class, remap = false)
public abstract class LogoutSpotsMixin implements LogoutSpotsPlayers {
    @Shadow private List<?> players;
    @Shadow private Setting<ShapeMode> shapeMode;
    @Shadow private Setting<SettingColor> sideColor;
    @Shadow private Setting<SettingColor> lineColor;

    @Unique private Setting<Boolean> thm$improvedLogoutShape;
    @Unique private Setting<Boolean> thm$captureLimbAnimation;
    @Unique private Setting<Boolean> thm$renderSkin;
    @Unique private Setting<Boolean> thm$skinThroughWalls;
    @Unique private final Map<UUID, SkinGhostPlayer> thm$ghosts = new HashMap<>();

    @Inject(method = "<init>", at = @At("TAIL"))
    private void thm$init(CallbackInfo ci) {
        SettingGroup sgThm = ((Module) (Object) this).settings.createGroup("THM");
        thm$improvedLogoutShape = sgThm.add(new BoolSetting.Builder()
            .name("improved-render-shape")
            .description("Render logout spots using a ghost player model snapshot with real limb poses.")
            .defaultValue(true)
            .build()
        );
        thm$captureLimbAnimation = sgThm.add(new BoolSetting.Builder()
            .name("capture-limb-animation")
            .description("Keep arm and leg motion from the logout moment.")
            .defaultValue(true)
            .visible(thm$improvedLogoutShape::get)
            .build()
        );
        thm$renderSkin = sgThm.add(new BoolSetting.Builder()
            .name("render-skin")
            .description("Render the logged out player's skin instead of the wireframe.")
            .defaultValue(true)
            .visible(thm$improvedLogoutShape::get)
            .build()
        );
        thm$skinThroughWalls = sgThm.add(new BoolSetting.Builder()
            .name("skin-through-walls")
            .description("Show the skin through solid blocks.")
            .defaultValue(false)
            .visible(() -> thm$improvedLogoutShape.get() && thm$renderSkin.get())
            .build()
        );
    }

    @Override
    public List<?> thm$getPlayers() {
        return players;
    }

    @Inject(method = "onDeactivate", at = @At("TAIL"))
    private void thm$onDeactivate(CallbackInfo ci) {
        thm$ghosts.clear();
    }

    @Inject(method = "onRender3D", at = @At("HEAD"), cancellable = true)
    private void thm$onRender3D(Render3DEvent event, CallbackInfo ci) {
        if (thm$improvedLogoutShape == null || !thm$improvedLogoutShape.get()) return;

        if (mc.world == null) return;

        boolean renderedAny = false;
        Set<UUID> seen = new HashSet<>();

        for (Object player : players) {
            if (!(player instanceof LogoutSpotsPoseData poseData)) continue;

            UUID uuid = poseData.thm$getUuid();
            seen.add(uuid);
            SkinGhostPlayer ghost = thm$ghosts.computeIfAbsent(uuid, ignored ->
                new SkinGhostPlayer(mc.world, new GameProfile(uuid, poseData.thm$getName()), poseData.thm$getSkin())
            );

            thm$applySnapshot(ghost, poseData);
            if (!thm$renderSkin.get()) WireframeEntityRenderer.render(event, ghost, 1, sideColor.get(), lineColor.get(), shapeMode.get());
            else if (thm$skinThroughWalls.get()) GhostRenderer.renderThroughWalls(event, ghost, 1);
            else GhostRenderer.submit(ghost, 1);
            renderedAny = true;
        }

        thm$ghosts.keySet().removeIf(uuid -> !seen.contains(uuid));
        if (renderedAny) ci.cancel();
    }

    @Unique
    private void thm$applySnapshot(SkinGhostPlayer ghost, LogoutSpotsPoseData poseData) {
        double x = poseData.thm$getX() + poseData.thm$getXWidth() / 2.0;
        double y = poseData.thm$getY();
        double z = poseData.thm$getZ() + poseData.thm$getZWidth() / 2.0;

        // The wireframe would trace the outer skin layer too, so only the skin render gets it.
        ghost.setModelParts(thm$renderSkin.get() ? poseData.thm$getModelParts() : 0);
        ghost.refreshPositionAndAngles(x, y, z, poseData.thm$getYaw(), poseData.thm$getPitch());
        EntityPositionAccessor entityPos = (EntityPositionAccessor) ghost;
        entityPos.thm$setLastX(x);
        entityPos.thm$setLastY(y);
        entityPos.thm$setLastZ(z);
        entityPos.thm$setLastRenderX(x);
        entityPos.thm$setLastRenderY(y);
        entityPos.thm$setLastRenderZ(z);

        LivingEntityAccessor rot = (LivingEntityAccessor) ghost;
        rot.thm$setHeadYaw(poseData.thm$getHeadYaw());
        rot.thm$setLastHeadYaw(poseData.thm$getHeadYaw());
        ghost.setBodyYaw(poseData.thm$getBodyYaw());
        rot.thm$setLastBodyYaw(poseData.thm$getBodyYaw());
        ghost.setSneaking(poseData.thm$isSneaking());

        if (poseData.thm$isLowPose()) {
            ghost.setPose(EntityPose.SWIMMING);
            ghost.setSwimming(true);
        } else {
            ghost.setPose(poseData.thm$isSneaking() ? EntityPose.CROUCHING : EntityPose.STANDING);
            ghost.setSwimming(false);
        }

        LimbAnimator limbAnimator = ((LivingEntityAccessor) ghost).thm$getLimbAnimator();
        ((LimbAnimatorAccessor) limbAnimator).thm$setAnimationProgress(poseData.thm$getLimbPos());
        if (thm$captureLimbAnimation != null && thm$captureLimbAnimation.get()) {
            // Freeze at captured swing phase/amount so the pose is preserved but does not keep animating.
            ((LimbAnimatorAccessor) limbAnimator).thm$setLastSpeed(poseData.thm$getLimbAmplitude());
            ((LimbAnimatorAccessor) limbAnimator).thm$setSpeedInternal(poseData.thm$getLimbAmplitude());
            ((LimbAnimatorAccessor) limbAnimator).thm$setTimeScale(0);
        } else {
            ((LimbAnimatorAccessor) limbAnimator).thm$setLastSpeed(0);
            ((LimbAnimatorAccessor) limbAnimator).thm$setSpeedInternal(0);
            ((LimbAnimatorAccessor) limbAnimator).thm$setAnimationProgress(0);
            ((LimbAnimatorAccessor) limbAnimator).thm$setTimeScale(0);
        }

    }
}
