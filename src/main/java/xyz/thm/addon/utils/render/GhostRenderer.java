/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.utils.render;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.mixininterface.IEntityRenderState;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.render.Chams;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import xyz.thm.addon.mixin.accessor.RenderSetupAccessor;
import xyz.thm.addon.mixin.accessor.RenderTypeAccessor;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/** Renders an entity with its real model and skin, like Meteor's WireframeEntityRenderer but textured. */
public final class GhostRenderer {
    private record Ghost(Entity entity, double scale, float alpha, boolean cape) {}

    private static final List<Ghost> QUEUED = new ArrayList<>();
    private static final SubmitNodeStorage QUEUE = new SubmitNodeStorage();
    private static boolean throughWalls;
    private static boolean submitting;
    private static final Map<RenderType, RenderType> CHAMS_TYPES = new IdentityHashMap<>();
    private static final Map<RenderPipeline, RenderPipeline> THROUGH_WALL_PIPELINES = new IdentityHashMap<>();

    /** Opacity of the ghost currently being drawn; 1 while anything else renders. */
    private static float alpha = 1;

    private GhostRenderer() {
    }

    /** 0-1, only meaningful inside a ghost draw. */
    public static float alpha() {
        return alpha;
    }

    public static boolean isFading() {
        return alpha < 1;
    }

    /** Draws with the world's own entities, so blocks hide it but glass and portals don't. */
    public static void submit(Entity entity, double scale, float alpha, boolean cape) {
        // Drained every world render; a full list means nothing is draining it.
        if (QUEUED.size() > 256) QUEUED.clear();
        QUEUED.add(new Ghost(entity, scale, alpha, cape));
    }

    /** Called from the vanilla entity pass; ghosts submitted during a frame show up in the next one. */
    public static void drawQueued(PoseStack matrices, SubmitNodeCollector queue) {
        if (QUEUED.isEmpty()) return;

        Vec3 cam = mc.gameRenderer.mainCamera().position();
        float tickDelta = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        for (Ghost ghost : QUEUED) draw(ghost.entity, ghost.scale, ghost.alpha, ghost.cape, cam.x, cam.y, cam.z, matrices, queue, tickDelta);
        QUEUED.clear();
    }

    /** Draws after the world without depth testing, so solid blocks don't hide it either. */
    public static void renderThroughWalls(Render3DEvent event, Entity entity, double scale, float alpha, boolean cape) {
        if (mc.level == null) return;

        draw(entity, scale, alpha, cape, event.offsetX, event.offsetY, event.offsetZ, event.matrices, QUEUE, event.tickDelta);
        throughWalls = true;
        try {
            mc.gameRenderer.featureRenderDispatcher().renderAllFeatures(QUEUE);
        } finally {
            throughWalls = false;
        }
    }

    public static RenderPipeline pipeline(RenderPipeline original) {
        return throughWalls ? withoutDepth(original) : original;
    }

    public static RenderType chamsRenderType(RenderType original, Object state) {
        if (submitting || !(state instanceof IEntityRenderState renderState)) return original;
        Entity entity = renderState.meteor$getEntity();
        if (!(entity instanceof LivingEntity)) return original;
        Chams chams = Modules.get().get(Chams.class);
        if (chams == null || !chams.isActive() || chams.isShader() || !chams.entities.get().contains(entity.getType())
            || (entity == mc.player && chams.ignoreSelfDepth.get())) return original;
        if (CHAMS_TYPES.size() >= 1024) CHAMS_TYPES.clear();
        return CHAMS_TYPES.computeIfAbsent(original, type -> {
            var setup = (RenderSetupAccessor) (Object) ((RenderTypeAccessor) type).thm$getSetup();
            return RenderType.create("thm_chams", setup.thm$withPipeline(withoutDepth(type.pipeline())));
        });
    }

    public static RenderPipeline withoutDepth(RenderPipeline original) {
        return THROUGH_WALL_PIPELINES.computeIfAbsent(original, source -> {
            var snippet = new RenderPipeline.Snippet(
                Optional.of(source.getVertexShader()), Optional.of(source.getFragmentShader()),
                Optional.of(source.getShaderDefines()), Optional.of(source.getBindGroupLayouts()),
                source.getColorTargetStates(), source.getColorTargetStates().length, Optional.empty(),
                Optional.of(source.getPolygonMode()), Optional.of(source.isCull()),
                source.getVertexFormatBindings(), Optional.of(source.getPrimitiveTopology()));
            return RenderPipeline.builder(snippet)
                .withLocation(Identifier.fromNamespaceAndPath("thm-addon", "ghost/" + source.getLocation().getNamespace() + "/" + source.getLocation().getPath()))
                .withDepthStencilState(Optional.empty()).build();
        });
    }

    private static void draw(Entity entity, double scale, float ghostAlpha, boolean cape, double camX, double camY, double camZ, PoseStack matrices, SubmitNodeCollector queue, float tickDelta) {
        alpha = Math.clamp(ghostAlpha, 0f, 1f);
        submitting = true;

        try {
            // The dispatcher, not the renderer, is what tags the state with its entity - Meteor's Chams needs that tag.
            EntityRenderState state = mc.getEntityRenderDispatcher().extractEntity(entity, tickDelta);

            // The cape renders on its own opaque layer, so it does not fade with the rest.
            if (!cape && state instanceof AvatarRenderState player) player.showCape = false;

            matrices.pushPose();
            try {
                matrices.translate(entity.getX() - camX, entity.getY() - camY, entity.getZ() - camZ);
                matrices.scale((float) scale, (float) scale, (float) scale);
                mc.getEntityRenderDispatcher().submit(state, mc.gameRenderer.gameRenderState().levelRenderState.cameraRenderState, 0, 0, 0, matrices, queue);
            } finally {
                matrices.popPose();
            }
        } finally {
            submitting = false;
            alpha = 1;
        }
    }
}
