/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.waveycapes;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.util.AssetInfo;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import xyz.thm.addon.waveycapes.sim.BasicSimulation;
import xyz.thm.addon.waveycapes.util.CapePoint;
import xyz.thm.addon.waveycapes.util.Mth;
import xyz.thm.addon.waveycapes.util.Vector3;
import xyz.thm.addon.waveycapes.util.Vector4;

import java.util.List;

public class WaveyCapeRenderLayer extends FeatureRenderer<PlayerEntityRenderState, PlayerEntityModel> {

    private static final int PART_COUNT = 16;

    private static final float CAPE_WIDTH = 10F / 16F;
    private static final float CAPE_HEIGHT = 1F;
    private static final float CAPE_DEPTH = 1F / 16F;

    public WaveyCapeRenderLayer(FeatureRendererContext<PlayerEntityRenderState, PlayerEntityModel> ctx) {
        super(ctx);
    }

    @Override
    public void render(MatrixStack matrices, OrderedRenderCommandQueue queue, int light,
                       PlayerEntityRenderState state, float yaw, float pitch) {
        if (!WaveyCapesConfig.enabled) return;

        if (state.invisible || !state.capeVisible) return;

        SkinTextures skins = state.skinTextures;
        if (skins == null) return;
        AssetInfo.TextureAsset capeAsset = skins.cape();
        if (capeAsset == null) return;
        Identifier capeId = capeAsset.texturePath();

        float delta = MinecraftClient.getInstance().getRenderTickCounter().getTickProgress(false);

        matrices.push();
        getContextModel().getRootPart().applyTransform(matrices);
        ((BipedEntityModel<?>) getContextModel()).body.applyTransform(matrices);

        if (state.equippedChestStack != null && !state.equippedChestStack.isEmpty()) {
            matrices.translate(0.0f, -0.053125f, 0.06875f);
        }

        renderSimulationCape(matrices, queue, light, state, capeId, delta);

        matrices.pop();
    }

    private void renderSimulationCape(MatrixStack matrices, OrderedRenderCommandQueue queue,
                                      int light, PlayerEntityRenderState state,
                                      Identifier capeId, float delta) {
        net.minecraft.client.network.AbstractClientPlayerEntity player = findPlayerForState(state);
        if (player == null) return;

        CapeHolder holder = (CapeHolder) player;
        BasicSimulation simulation = holder.getSimulation();
        if (simulation == null || simulation.empty()) return;

        if (WaveyCapesConfig.computeGravityVector && holder.canUpdateGravityVector()) {
            simulation.setGravityDirection(modelDownDirection(matrices, state.bodyYaw));
            holder.setGravityVectorRequest(false);
        }

        RenderLayer layer = RenderLayers.entityTranslucent(capeId);
        if (WaveyCapesConfig.capeStyle == CapeStyle.SMOOTH) {
            queue.submitCustom(matrices, layer, (entry, consumer) -> {
                MatrixStack shared = new MatrixStack();
                shared.peek().copy(entry);
                renderSmoothCapeSimulation(shared, consumer, simulation, player.isSubmergedInWater(), delta, light);
            });
        } else {
            queue.submitCustom(matrices, layer, (entry, consumer) -> {
                MatrixStack shared = new MatrixStack();
                shared.peek().copy(entry);
                Matrix4f[] pm = new Matrix4f[PART_COUNT];
                for (int part = 0; part < PART_COUNT; part++) {
                    modifyPoseStackSimulation(shared, simulation, player.isSubmergedInWater(), delta, part);
                    pm[part] = new Matrix4f(shared.peek().getPositionMatrix());
                    shared.pop();
                }
                emitBlockyVertices(consumer, pm, light);
            });
        }
    }

    /**
     * The model's real "up" in body space: where the cape hangs from vs. one block above it, with the
     * body yaw taken back out. Beats the swim-pose approximation for any pose that tilts the model.
     */
    private Vector3 modelDownDirection(MatrixStack matrices, float bodyYaw) {
        Matrix4f pose = matrices.peek().getPositionMatrix();
        org.joml.Vector3f body = pose.transformPosition(new org.joml.Vector3f());
        org.joml.Vector3f up = pose.transformPosition(new org.joml.Vector3f(0, 1, 0)).sub(body).normalize();

        float rot = -(bodyYaw - 90) * ((float) Math.PI / 180f);
        return new Vector3(
            up.x * Mth.cos(rot) - up.z * Mth.sin(rot),
            up.y,
            up.x * Mth.sin(rot) + up.z * Mth.cos(rot));
    }

    private net.minecraft.client.network.AbstractClientPlayerEntity findPlayerForState(PlayerEntityRenderState state) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || state.skinTextures == null) return null;
        AssetInfo.TextureAsset stateBody = state.skinTextures.body();
        if (stateBody == null) return null;
        Identifier stateBodyPath = stateBody.texturePath();
        for (net.minecraft.entity.player.PlayerEntity player : mc.world.getPlayers()) {
            if (player instanceof net.minecraft.client.network.AbstractClientPlayerEntity acp) {
                AssetInfo.TextureAsset body = acp.getSkin().body();
                if (body != null && body.texturePath().equals(stateBodyPath)) return acp;
            }
        }
        return null;
    }

    // ---- Simulation pose stack ----

    private void modifyPoseStackSimulation(MatrixStack matrices, BasicSimulation simulation,
                                            boolean underwater, float delta, int part) {
        matrices.push();
        matrices.translate(0.0, 0.0, 0.125);

        float x = simulation.getPoints().get(part).getLerpX(delta) - simulation.getPoints().get(0).getLerpX(delta);
        if (x > 0) x = 0;
        float y = simulation.getPoints().get(0).getLerpY(delta) - part - simulation.getPoints().get(part).getLerpY(delta);
        float z = simulation.getPoints().get(0).getLerpZ(delta) - simulation.getPoints().get(part).getLerpZ(delta);

        float partRotation = getPartRotation(delta, part, simulation);
        float naturalWindSwing = getNaturalWindSwing(part, underwater);

        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(6.0f + naturalWindSwing));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(0));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0f));
        matrices.translate(-z / PART_COUNT, y / PART_COUNT, x / PART_COUNT);
        matrices.translate(0, (0.48 / 16), -(0.48 / 16));
        matrices.translate(0, part * 1f / PART_COUNT, 0);
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-partRotation));
        matrices.translate(0, -part * 1f / PART_COUNT, 0);
        matrices.translate(0, -(0.48 / 16), (0.48 / 16));
    }

    // ---- Smooth simulation rendering ----

    private void renderSmoothCapeSimulation(MatrixStack matrices, VertexConsumer consumer,
                                             BasicSimulation simulation, boolean underwater,
                                             float delta, int light) {
        Matrix4f[] positionMatrices = new Matrix4f[PART_COUNT];
        Vector3[] frontNormals = new Vector3[PART_COUNT];
        Vector3[] backNormals = new Vector3[PART_COUNT];

        for (int part = 0; part < PART_COUNT; part++) {
            modifyPoseStackSimulation(matrices, simulation, underwater, delta, part);
            positionMatrices[part] = new Matrix4f(matrices.peek().getPositionMatrix());
            frontNormals[part] = getNormalVec(
                positionMatrices[Math.max(part - 1, 0)], positionMatrices[Math.max(part - 1, 0)], positionMatrices[part],
                new Vector3(CAPE_WIDTH / 2F, part * (CAPE_HEIGHT / PART_COUNT), -CAPE_DEPTH),
                new Vector3(-CAPE_WIDTH / 2F, part * (CAPE_HEIGHT / PART_COUNT), -CAPE_DEPTH),
                new Vector3(CAPE_WIDTH / 2F, (part + 1) * (CAPE_HEIGHT / PART_COUNT), -CAPE_DEPTH), light == 15728880);
            backNormals[part] = getNormalVec(
                positionMatrices[Math.max(part - 1, 0)], positionMatrices[Math.max(part - 1, 0)], positionMatrices[part],
                new Vector3(CAPE_WIDTH / 2F, (part + 1) * (CAPE_HEIGHT / PART_COUNT), 0),
                new Vector3(-CAPE_WIDTH / 2F, (part + 1) * (CAPE_HEIGHT / PART_COUNT), 0),
                new Vector3(CAPE_WIDTH / 2F, part * (CAPE_HEIGHT / PART_COUNT), 0), light == 15728880);
            matrices.pop();
        }

        emitSmoothVertices(consumer, positionMatrices, frontNormals, backNormals, light, 1.0f);
    }

    // ---- Blocky vertex emitter ----

    private void emitBlockyVertices(VertexConsumer consumer, Matrix4f[] pm, int light) {
        float segH = CAPE_HEIGHT / PART_COUNT;
        for (int part = 0; part < PART_COUNT; part++) {
            float y0 = part * segH;
            float y1 = y0 + segH;
            float minV = (1 / 32F) * (part + 1);
            float maxV = minV + (1 / 32F);
            Vector3 nf = getNormalVec(pm[part], pm[part], pm[part],
                new Vector3(CAPE_WIDTH / 2F, y0, -CAPE_DEPTH),
                new Vector3(-CAPE_WIDTH / 2F, y0, -CAPE_DEPTH),
                new Vector3(CAPE_WIDTH / 2F, y1, -CAPE_DEPTH), light == 15728880);
            addVertex(consumer, pm[part], CAPE_WIDTH / 2F, y0, -CAPE_DEPTH, 11 / 64F, minV, light, nf, 1f);
            addVertex(consumer, pm[part], -CAPE_WIDTH / 2F, y0, -CAPE_DEPTH, 1 / 64F, minV, light, nf, 1f);
            addVertex(consumer, pm[part], -CAPE_WIDTH / 2F, y1, -CAPE_DEPTH, 1 / 64F, maxV, light, nf, 1f);
            addVertex(consumer, pm[part], CAPE_WIDTH / 2F, y1, -CAPE_DEPTH, 11 / 64F, maxV, light, nf, 1f);
            Vector3 nb = getNormalVec(pm[part], pm[part], pm[part],
                new Vector3(CAPE_WIDTH / 2F, y1, 0),
                new Vector3(-CAPE_WIDTH / 2F, y1, 0),
                new Vector3(CAPE_WIDTH / 2F, y0, 0), light == 15728880);
            addVertex(consumer, pm[part], CAPE_WIDTH / 2F, y1, 0, 22 / 64F, maxV, light, nb, 1f);
            addVertex(consumer, pm[part], -CAPE_WIDTH / 2F, y1, 0, 12 / 64F, maxV, light, nb, 1f);
            addVertex(consumer, pm[part], -CAPE_WIDTH / 2F, y0, 0, 12 / 64F, minV, light, nb, 1f);
            addVertex(consumer, pm[part], CAPE_WIDTH / 2F, y0, 0, 22 / 64F, minV, light, nb, 1f);
        }
    }

    // ---- Shared smooth vertex emitter ----

    private void emitSmoothVertices(VertexConsumer consumer, Matrix4f[] pm,
                                     Vector3[] fn, Vector3[] bn, int light, float alpha) {
        for (int part = 0; part < PART_COUNT; part++) {
            if (part == 0) {
                float minU = 1 / 64F, maxU = 11 / 64F, minV = 0, maxV = 1 / 32F;
                Vector3 n = getNormalVec(pm[0], pm[0], pm[0],
                    new Vector3(CAPE_WIDTH / 2, 0, 0), new Vector3(-CAPE_WIDTH / 2, 0, 0),
                    new Vector3(CAPE_WIDTH / 2, 0, CAPE_DEPTH), light == 15728880);
                addVertex(consumer, pm[0], CAPE_WIDTH / 2, 0, 0, maxU, maxV, light, n, alpha);
                addVertex(consumer, pm[0], -CAPE_WIDTH / 2, 0, 0, minU, maxV, light, n, alpha);
                addVertex(consumer, pm[0], -CAPE_WIDTH / 2, 0, -CAPE_DEPTH, minU, minV, light, n, alpha);
                addVertex(consumer, pm[0], CAPE_WIDTH / 2, 0, -CAPE_DEPTH, maxU, minV, light, n, alpha);
            }
            if (part == PART_COUNT - 1) {
                float minU = 11 / 64F, maxU = 21 / 64F, minV = 0, maxV = 1 / 32F;
                Vector3 n = getNormalVec(pm[part], pm[part], pm[part],
                    new Vector3(CAPE_WIDTH / 2F, CAPE_HEIGHT, -CAPE_DEPTH),
                    new Vector3(-CAPE_WIDTH / 2F, CAPE_HEIGHT, -CAPE_DEPTH),
                    new Vector3(CAPE_WIDTH / 2F, CAPE_HEIGHT, 0), light == 15728880);
                addVertex(consumer, pm[part], CAPE_WIDTH / 2F, CAPE_HEIGHT, -CAPE_DEPTH, maxU, minV, light, n, alpha);
                addVertex(consumer, pm[part], -CAPE_WIDTH / 2F, CAPE_HEIGHT, -CAPE_DEPTH, minU, minV, light, n, alpha);
                addVertex(consumer, pm[part], -CAPE_WIDTH / 2F, CAPE_HEIGHT, 0, minU, maxV, light, n, alpha);
                addVertex(consumer, pm[part], CAPE_WIDTH / 2F, CAPE_HEIGHT, 0, maxU, maxV, light, n, alpha);
            }

            // Left edge
            {
                float minU = 0, maxU = 1 / 64F;
                float minV = (1 / 32F) * (part + 1), maxV = minV + (1 / 32F);
                int prev = Math.max(part - 1, 0);
                Vector3 n = getNormalVec(pm[part], pm[part], pm[prev],
                    new Vector3(-CAPE_WIDTH / 2F, (part + 1) * (CAPE_HEIGHT / PART_COUNT), 0),
                    new Vector3(-CAPE_WIDTH / 2F, (part + 1) * (CAPE_HEIGHT / PART_COUNT), -CAPE_DEPTH),
                    new Vector3(-CAPE_WIDTH / 2F, part * (CAPE_HEIGHT / PART_COUNT), 0), light == 15728880);
                addVertex(consumer, pm[part], -CAPE_WIDTH / 2F, (part + 1) * (CAPE_HEIGHT / PART_COUNT), 0, minU, maxV, light, n, alpha);
                addVertex(consumer, pm[part], -CAPE_WIDTH / 2F, (part + 1) * (CAPE_HEIGHT / PART_COUNT), -CAPE_DEPTH, maxU, maxV, light, n, alpha);
                addVertex(consumer, pm[prev], -CAPE_WIDTH / 2F, part * (CAPE_HEIGHT / PART_COUNT), -CAPE_DEPTH, maxU, minV, light, n, alpha);
                addVertex(consumer, pm[prev], -CAPE_WIDTH / 2F, part * (CAPE_HEIGHT / PART_COUNT), 0, minU, minV, light, n, alpha);
            }
            // Right edge
            {
                float minU = 11 / 64F, maxU = 12 / 64F;
                float minV = (1 / 32F) * (part + 1), maxV = minV + (1 / 32F);
                int prev = Math.max(part - 1, 0);
                Vector3 n = getNormalVec(pm[part], pm[part], pm[prev],
                    new Vector3(CAPE_WIDTH / 2F, (part + 1) * (CAPE_HEIGHT / PART_COUNT), -CAPE_DEPTH),
                    new Vector3(CAPE_WIDTH / 2F, (part + 1) * (CAPE_HEIGHT / PART_COUNT), 0),
                    new Vector3(CAPE_WIDTH / 2F, part * (CAPE_HEIGHT / PART_COUNT), -CAPE_DEPTH), light == 15728880);
                addVertex(consumer, pm[part], CAPE_WIDTH / 2F, (part + 1) * (CAPE_HEIGHT / PART_COUNT), -CAPE_DEPTH, minU, maxV, light, n, alpha);
                addVertex(consumer, pm[part], CAPE_WIDTH / 2F, (part + 1) * (CAPE_HEIGHT / PART_COUNT), 0, maxU, maxV, light, n, alpha);
                addVertex(consumer, pm[prev], CAPE_WIDTH / 2F, part * (CAPE_HEIGHT / PART_COUNT), 0, maxU, minV, light, n, alpha);
                addVertex(consumer, pm[prev], CAPE_WIDTH / 2F, part * (CAPE_HEIGHT / PART_COUNT), -CAPE_DEPTH, minU, minV, light, n, alpha);
            }
            // Front face
            {
                float minU = 1 / 64F, maxU = 11 / 64F;
                float minV = (1 / 32F) * (part + 1), maxV = minV + (1 / 32F);
                int prev = Math.max(part - 1, 0);
                int next = Math.min(part + 1, PART_COUNT - 1);
                Vector3 nTop = fn[part].clone().add(fn[prev]).div(2);
                Vector3 nBot = fn[part].clone().add(fn[next]).div(2);
                addVertex(consumer, pm[prev], CAPE_WIDTH / 2, part * (CAPE_HEIGHT / PART_COUNT), -CAPE_DEPTH, maxU, minV, light, nTop, alpha);
                addVertex(consumer, pm[prev], -CAPE_WIDTH / 2, part * (CAPE_HEIGHT / PART_COUNT), -CAPE_DEPTH, minU, minV, light, nTop, alpha);
                addVertex(consumer, pm[part], -CAPE_WIDTH / 2, (part + 1) * (CAPE_HEIGHT / PART_COUNT), -CAPE_DEPTH, minU, maxV, light, nBot, alpha);
                addVertex(consumer, pm[part], CAPE_WIDTH / 2, (part + 1) * (CAPE_HEIGHT / PART_COUNT), -CAPE_DEPTH, maxU, maxV, light, nBot, alpha);
            }
            // Back face
            {
                float minU = 12 / 64F, maxU = 22 / 64F;
                float minV = (1 / 32F) * (part + 1), maxV = minV + (1 / 32F);
                int prev = Math.max(part - 1, 0);
                int next = Math.min(part + 1, PART_COUNT - 1);
                Vector3 nTop = bn[part].clone().add(bn[prev]).div(2);
                Vector3 nBot = bn[part].clone().add(bn[next]).div(2);
                addVertex(consumer, pm[prev], CAPE_WIDTH / 2, part * (CAPE_HEIGHT / PART_COUNT), 0, minU, minV, light, nTop, alpha);
                addVertex(consumer, pm[prev], -CAPE_WIDTH / 2, part * (CAPE_HEIGHT / PART_COUNT), 0, maxU, minV, light, nTop, alpha);
                addVertex(consumer, pm[part], -CAPE_WIDTH / 2, (part + 1) * (CAPE_HEIGHT / PART_COUNT), 0, maxU, maxV, light, nBot, alpha);
                addVertex(consumer, pm[part], CAPE_WIDTH / 2, (part + 1) * (CAPE_HEIGHT / PART_COUNT), 0, minU, maxV, light, nBot, alpha);
            }
        }
    }

    // ---- Helpers ----

    private void addVertex(VertexConsumer consumer, Matrix4f matrix, float x, float y, float z,
                            float u, float v, int light, Vector3 normal, float alpha) {
        consumer.vertex(matrix, x, y, z)
            .color(1f, 1f, 1f, alpha)
            .texture(u, v)
            .overlay(net.minecraft.client.render.OverlayTexture.DEFAULT_UV)
            .light(light)
            .normal(normal.x, normal.y, normal.z);
    }

    private float getPartRotation(float delta, int part, BasicSimulation simulation) {
        if (part == PART_COUNT - 1) return getPartRotation(delta, part - 1, simulation);
        List<CapePoint> points = simulation.getPoints();
        Vector3 a = points.get(part).getLerpedPos(delta);
        Vector3 b = points.get(part + 1).getLerpedPos(delta);
        Vector3 diff = b.subtract(a);
        return (float) (Math.toDegrees(Math.atan2(diff.x, diff.y)) + 180);
    }

    private float getNaturalWindSwing(int part, boolean underwater) {
        long t = (System.currentTimeMillis() / (underwater ? 9 : 3)) % 360;
        float relativePart = (float) (part + 1) / PART_COUNT;
        if (WaveyCapesConfig.windMode == WindMode.WAVES) {
            return (float) (Math.sin(Math.toRadians(relativePart * 360 - t)) * 3);
        }
        return 0;
    }

    private static Vector3 getNormalVec(Matrix4f m1, Matrix4f m2, Matrix4f m3,
                                         Vector3 v1, Vector3 v2, Vector3 v3, boolean inverse) {
        Vector3 t1 = transform(m1, new Vector4(v1.x, v1.y, v1.z, 1)).toVec3();
        Vector3 t2 = transform(m2, new Vector4(v2.x, v2.y, v2.z, 1)).toVec3();
        Vector3 t3 = transform(m3, new Vector4(v3.x, v3.y, v3.z, 1)).toVec3();
        t2.subtract(t1);
        t3.subtract(t1);
        t2.cross(t3).normalize();
        return inverse ? t2.mul(-1) : t2;
    }

    private static Vector4 transform(Matrix4f matrix, Vector4 v) {
        Vector4f r = matrix.transform(new Vector4f(v.x, v.y, v.z, v.w));
        return new Vector4(r.x, r.y, r.z, r.w);
    }
}
