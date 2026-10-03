/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.utils.render;

import meteordevelopment.meteorclient.events.render.Render3DEvent;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;

import static meteordevelopment.meteorclient.MeteorClient.mc;
import static org.lwjgl.opengl.GL11.*;

import com.mojang.blaze3d.vertex.PoseStack;

/** Renders an entity with its real model and skin, like Meteor's WireframeEntityRenderer but textured. */
public final class GhostRenderer {
    private record Ghost(Entity entity, double scale, float alpha, boolean cape) {}

    private static final List<Ghost> QUEUED = new ArrayList<>();
    private static final SubmitNodeStorage QUEUE = new SubmitNodeStorage();
    private static FeatureRenderDispatcher dispatcher;

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

        Vec3 cam = mc.gameRenderer.getMainCamera().position();
        float tickDelta = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        for (Ghost ghost : QUEUED) draw(ghost.entity, ghost.scale, ghost.alpha, ghost.cape, cam.x, cam.y, cam.z, matrices, queue, tickDelta);
        QUEUED.clear();
    }

    /** Draws after the world with a depth offset, so solid blocks don't hide it either. */
    public static void renderThroughWalls(Render3DEvent event, Entity entity, double scale, float alpha, boolean cape) {
        if (mc.level == null) return;

        MultiBufferSource.BufferSource immediate = mc.renderBuffers().bufferSource();
        if (dispatcher == null) {
            dispatcher = new FeatureRenderDispatcher(
                QUEUE,
                mc.getBlockRenderer(),
                immediate,
                mc.getAtlasManager(),
                mc.renderBuffers().outlineBufferSource(),
                mc.renderBuffers().crumblingBufferSource(),
                mc.font
            );
        }

        draw(entity, scale, alpha, cape, event.offsetX, event.offsetY, event.offsetZ, event.matrices, QUEUE, event.tickDelta);

        // Same trick Meteor's chams uses: pull the depth towards the camera instead of turning depth testing off.
        glEnable(GL_POLYGON_OFFSET_FILL);
        glPolygonOffset(1, -1100000);
        dispatcher.renderAllFeatures();
        QUEUE.clear();
        immediate.endBatch();
        glPolygonOffset(1, 1100000);
        glDisable(GL_POLYGON_OFFSET_FILL);
    }

    private static void draw(Entity entity, double scale, float ghostAlpha, boolean cape, double camX, double camY, double camZ, PoseStack matrices, SubmitNodeCollector queue, float tickDelta) {
        alpha = Math.clamp(ghostAlpha, 0f, 1f);

        try {
            // The dispatcher, not the renderer, is what tags the state with its entity - Meteor's Chams needs that tag.
            EntityRenderState state = mc.getEntityRenderDispatcher().extractEntity(entity, tickDelta);

            // The cape renders on its own opaque layer, so it does not fade with the rest.
            if (!cape && state instanceof AvatarRenderState player) player.showCape = false;

            matrices.pushPose();
            matrices.translate(entity.getX() - camX, entity.getY() - camY, entity.getZ() - camZ);
            matrices.scale((float) scale, (float) scale, (float) scale);
            mc.getEntityRenderDispatcher().submit(state, mc.gameRenderer.getLevelRenderState().cameraRenderState, 0, 0, 0, matrices, queue);
            matrices.popPose();
        } finally {
            alpha = 1;
        }
    }
}
