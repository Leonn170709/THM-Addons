/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.shaders;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.CompiledRenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import org.lwjgl.system.MemoryStack;
import xyz.thm.addon.THMAddon;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.OptionalInt;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

// Renders the currently active .fsh background as the title screen's panorama replacement.
// Unlike the raw-GL approach this replaced, this goes through Blaze3D's actual RenderPipeline/
// RenderPass API (the same machinery vanilla's own post-processing effects, e.g. the menu
// blur, use), reusing vanilla's attributeless "core/screenquad" vertex shader. The .fsh files
// were adapted from RusherHack (see assets/thm-addon/shaders/) to declare their time/mouse/
// resolution uniforms as a single std140 block ("ThmShaderData") instead of individual
// uniforms, since Blaze3D only supports whole-uniform-buffer bindings, not per-scalar
// glUniform calls. Every shader was validated with glslangValidator before shipping.
public class ShaderBackground {
    private static final Identifier VERTEX_SHADER = Identifier.withDefaultNamespace("core/screenquad");
    private static final long UNIFORM_BUFFER_SIZE = 32L; // std140: float time, pad, vec2 mouse, vec2 resolution

    private static final long START_NANOS = System.nanoTime();
    private static final Map<String, RenderPipeline> pipelines = new HashMap<>();
    private static final Map<String, Boolean> valid = new HashMap<>();
    private static GpuBuffer uniformBuffer;
    private static boolean blurBroken;
    private static boolean scaledBroken;
    private static boolean tripBroken;

    /** @return true if a shader was drawn (caller should skip the vanilla panorama). */
    public static boolean render() {
        String name = ShaderManager.active();
        if (name == null) return false;

        try {
            RenderPipeline pipeline = pipelineFor(name);
            if (pipeline == null) return false;

            Minecraft mc = Minecraft.getInstance();
            RenderTarget framebuffer = mc.getMainRenderTarget();
            GpuTextureView colorView = framebuffer.getColorTextureView();
            if (colorView == null) return false;

            // Draw at reduced resolution and upscale - these shaders are per-pixel raymarchers/
            // noise fields and are by far the most expensive thing in a menu frame at native res.
            if (!scaledBroken) {
                try {
                    if (BlurBackground.renderScaled(pipeline)) return true;
                } catch (Throwable t) {
                    THMAddon.LOG.warn("[THM] Scaled shader draw failed, falling back to full resolution", t);
                    scaledBroken = true;
                }
            }

            drawInto(pipeline, colorView, framebuffer.width, framebuffer.height);
            return true;
        } catch (Throwable t) {
            THMAddon.LOG.warn("[THM] Main-menu shader '{}' failed to render, disabling it", name, t);
            valid.put(name, false);
            return false;
        }
    }

    /**
     * "Frosted glass" blur applied only within the given window rectangle (GUI-scaled screen
     * coordinates), not the whole shader background - see BlurBackground. Call this before
     * drawing window chrome on top, so the chrome's translucent fill sits over the blurred
     * patch instead of the sharp shader. Works whether or not a custom shader is active - with
     * none active (e.g. "None" chosen, or random with no candidates), BlurBackground falls back
     * to blurring whatever's already in that region of the framebuffer (the vanilla panorama).
     */
    public static boolean renderBlurredRegion(int x1, int y1, int x2, int y2, int strength) {
        if (strength <= 0 || blurBroken) return false;

        try {
            return BlurBackground.renderRegion(strength, x1, y1, x2, y2);
        } catch (Throwable t) {
            THMAddon.LOG.warn("[THM] Main-menu window blur failed, disabling it for this session", t);
            blurBroken = true;
            return false;
        }
    }

    /**
     * Joke "I'm high" full-frame post-process: wavey nausea distortion + greenish/red pulsing tint
     * + a "melt into each other" double-sample, applied over the ENTIRE main framebuffer (world +
     * HUD + Meteor GUI). {@code intensity} 0..1. Latches off on any failure so a broken driver
     * can never crash a frame.
     */
    public static boolean renderTrip(float intensity) {
        if (intensity <= 0 || tripBroken) return false;

        try {
            return TripBackground.render(intensity);
        } catch (Throwable t) {
            THMAddon.LOG.warn("[THM] 'I'm high' effect failed, disabling it for this session", t);
            tripBroken = true;
            return false;
        }
    }

    private static RenderPipeline pipelineFor(String name) {
        if (valid.getOrDefault(name, true) == Boolean.FALSE) return null;

        return pipelines.computeIfAbsent(name, n -> {
            RenderPipeline pipeline = RenderPipeline.builder(RenderPipelines.POST_PROCESSING_SNIPPET)
                .withLocation(Identifier.fromNamespaceAndPath(THMAddon.MOD_ID, "bg_" + n))
                .withVertexShader(VERTEX_SHADER)
                .withFragmentShader(Identifier.fromNamespaceAndPath(THMAddon.MOD_ID, n))
                .withUniform("ThmShaderData", UniformType.UNIFORM_BUFFER)
                .build();

            CompiledRenderPipeline compiled = RenderSystem.getDevice().precompilePipeline(pipeline);
            if (!compiled.isValid()) {
                THMAddon.LOG.warn("[THM] Main-menu shader '{}' failed to compile, disabling it", n);
                valid.put(n, false);
                return null;
            }

            valid.put(n, true);
            return pipeline;
        });
    }

    /** Draws the given (already-compiled) shader pipeline into an arbitrary render target. */
    static void drawInto(RenderPipeline pipeline, GpuTextureView target, int width, int height) {
        GpuDevice device = RenderSystem.getDevice();
        CommandEncoder encoder = device.createCommandEncoder();

        if (uniformBuffer == null) {
            uniformBuffer = device.createBuffer(() -> "THM shader background", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, UNIFORM_BUFFER_SIZE);
        }

        Window window = Minecraft.getInstance().getWindow();
        float mouseScaleX = width / (float) window.getWidth();
        float mouseScaleY = height / (float) window.getHeight();

        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer data = stack.calloc((int) UNIFORM_BUFFER_SIZE);
            float time = (System.nanoTime() - START_NANOS) / 1_000_000_000f;
            data.putFloat(0, time);
            data.putFloat(8, (float) Minecraft.getInstance().mouseHandler.xpos() * mouseScaleX);
            data.putFloat(12, height - (float) Minecraft.getInstance().mouseHandler.ypos() * mouseScaleY);
            data.putFloat(16, (float) width);
            data.putFloat(20, (float) height);
            encoder.writeToBuffer(uniformBuffer.slice(), data);
        }

        try (RenderPass pass = encoder.createRenderPass(() -> "THM shader background", target, OptionalInt.empty())) {
            pass.setPipeline(pipeline);
            pass.setUniform("ThmShaderData", uniformBuffer);
            pass.draw(0, 3);
        }
    }
}
