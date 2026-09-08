package xyz.thm.addon.utils.render;

import meteordevelopment.meteorclient.events.render.Render3DEvent;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.command.OrderedRenderCommandQueueImpl;
import net.minecraft.client.render.command.RenderDispatcher;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

import static meteordevelopment.meteorclient.MeteorClient.mc;
import static org.lwjgl.opengl.GL11.*;

/** Renders an entity with its real model and skin, like Meteor's WireframeEntityRenderer but textured. */
public final class GhostRenderer {
    private record Ghost(Entity entity, double scale) {}

    private static final List<Ghost> QUEUED = new ArrayList<>();
    private static final OrderedRenderCommandQueueImpl QUEUE = new OrderedRenderCommandQueueImpl();
    private static RenderDispatcher dispatcher;

    private GhostRenderer() {
    }

    /** Draws with the world's own entities, so blocks hide it but glass and portals don't. */
    public static void submit(Entity entity, double scale) {
        // Drained every world render; a full list means nothing is draining it.
        if (QUEUED.size() > 256) QUEUED.clear();
        QUEUED.add(new Ghost(entity, scale));
    }

    /** Called from the vanilla entity pass; ghosts submitted during a frame show up in the next one. */
    public static void drawQueued(MatrixStack matrices, OrderedRenderCommandQueue queue) {
        if (QUEUED.isEmpty()) return;

        Vec3d cam = mc.gameRenderer.getCamera().getCameraPos();
        float tickDelta = mc.getRenderTickCounter().getTickProgress(false);
        for (Ghost ghost : QUEUED) draw(ghost.entity, ghost.scale, cam.x, cam.y, cam.z, matrices, queue, tickDelta);
        QUEUED.clear();
    }

    /** Draws after the world with a depth offset, so solid blocks don't hide it either. */
    public static void renderThroughWalls(Render3DEvent event, Entity entity, double scale) {
        if (mc.world == null) return;

        VertexConsumerProvider.Immediate immediate = mc.getBufferBuilders().getEntityVertexConsumers();
        if (dispatcher == null) {
            dispatcher = new RenderDispatcher(
                QUEUE,
                mc.getBlockRenderManager(),
                immediate,
                mc.getAtlasManager(),
                mc.getBufferBuilders().getOutlineVertexConsumers(),
                mc.getBufferBuilders().getEffectVertexConsumers(),
                mc.textRenderer
            );
        }

        draw(entity, scale, event.offsetX, event.offsetY, event.offsetZ, event.matrices, QUEUE, event.tickDelta);

        // Same trick Meteor's chams uses: pull the depth towards the camera instead of turning depth testing off.
        glEnable(GL_POLYGON_OFFSET_FILL);
        glPolygonOffset(1, -1100000);
        dispatcher.render();
        QUEUE.clear();
        immediate.draw();
        glPolygonOffset(1, 1100000);
        glDisable(GL_POLYGON_OFFSET_FILL);
    }

    @SuppressWarnings("unchecked")
    private static void draw(Entity entity, double scale, double camX, double camY, double camZ, MatrixStack matrices, OrderedRenderCommandQueue queue, float tickDelta) {
        EntityRenderer<Entity, EntityRenderState> renderer =
            (EntityRenderer<Entity, EntityRenderState>) mc.getEntityRenderDispatcher().getRenderer(entity);
        EntityRenderState state = renderer.getAndUpdateRenderState(entity, tickDelta);

        matrices.push();
        matrices.translate(entity.getX() - camX, entity.getY() - camY, entity.getZ() - camZ);
        matrices.scale((float) scale, (float) scale, (float) scale);
        mc.getEntityRenderDispatcher().render(state, mc.gameRenderer.getEntityRenderStates().cameraRenderState, 0, 0, 0, matrices, queue);
        matrices.pop();
    }
}
