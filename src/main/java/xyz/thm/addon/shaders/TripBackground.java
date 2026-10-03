/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.shaders;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.shaders.ShaderType;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import org.lwjgl.system.MemoryStack;
import xyz.thm.addon.THMAddon;

import java.nio.ByteBuffer;
import java.util.OptionalInt;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

// Joke "I'm high" full-frame post effect - the same Blaze3D framebuffer-post plumbing as
// BlurBackground (attributeless big-triangle vertex shader, POST_EFFECT_PROCESSOR_SNIPPET
// pipelines, inline shader sources via precompilePipeline), but instead of a blur it warps the
// whole screen with a nausea-style wave, pulses a green<->red tint, and double-samples so the
// image "melts into itself". Two full-res passes per frame: copy the framebuffer into a temp
// texture (can't sample + write the same target), then draw the distorted result back over it.
class TripBackground {
    private static final Identifier VSH_ID = Identifier.fromNamespaceAndPath(THMAddon.MOD_ID, "trip_vsh");
    private static final Identifier COPY_FSH_ID = Identifier.fromNamespaceAndPath(THMAddon.MOD_ID, "trip_copy_fsh");
    private static final Identifier TRIP_FSH_ID = Identifier.fromNamespaceAndPath(THMAddon.MOD_ID, "trip_fsh");
    private static final long PARAMS_SIZE = 16L; // std140: vec4(time, intensity, unused, unused)

    private static final String VSH_SRC = """
        #version 330 core
        out vec2 texCoord;
        void main() {
            vec2 uv = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2);
            gl_Position = vec4(uv * 2.0 - 1.0, 0.0, 1.0);
            texCoord = uv;
        }
        """;

    private static final String COPY_FSH_SRC = """
        #version 330 core
        in vec2 texCoord;
        uniform sampler2D InSampler;
        out vec4 fragColor;
        void main() {
            fragColor = texture(InSampler, texCoord);
        }
        """;

    // Wavey nausea warp + green<->red pulse + chromatic "melt" (each channel sampled at a slightly
    // different swimming offset, so edges smear into each other). intensity scales it all.
    private static final String TRIP_FSH_SRC = """
        #version 330 core
        layout(std140) uniform TripParams {
            vec4 params; // x = time, y = intensity
        };
        in vec2 texCoord;
        uniform sampler2D InSampler;
        out vec4 fragColor;
        void main() {
            float t = params.x;
            float k = params.y;
            vec2 uv = texCoord;

            // Nausea-style swirling wave distortion - big, layered waves that swim around.
            float amp = 0.09 * k;
            uv.x += sin(uv.y * 14.0 + t * 2.3) * amp;
            uv.y += cos(uv.x * 11.0 + t * 1.9) * amp;
            uv.x += sin(uv.y * 5.0 - t * 1.1) * amp * 0.7;
            uv.y += cos(uv.x * 6.0 + t * 1.4) * amp * 0.7;
            // Radial pulse dragging everything toward/away from centre (that "walls breathing" feel).
            vec2 c = uv - 0.5;
            float rad = length(c);
            uv += normalize(c + 1e-5) * sin(rad * 18.0 - t * 3.0) * 0.05 * k;
            // Slow whole-screen drift.
            uv += vec2(sin(t * 0.7), cos(t * 0.9)) * 0.03 * k;

            // Chromatic melt: sample R/G/B at drifting offsets so they smear far apart.
            vec2 d = vec2(sin(t * 1.3 + uv.y * 8.0), cos(t * 1.1 + uv.x * 8.0)) * 0.04 * k;
            float r = texture(InSampler, uv + d).r;
            float g = texture(InSampler, uv + d * 0.3).g;
            float b = texture(InSampler, uv - d).b;
            vec3 col = vec3(r, g, b);

            // Saturation boost so the smear reads harder.
            float luma = dot(col, vec3(0.299, 0.587, 0.114));
            col = mix(vec3(luma), col, 1.0 + 1.2 * k);

            // Fast, strong pulsing green <-> red tint.
            float pulse = 0.5 + 0.5 * sin(t * 1.6);
            vec3 tint = mix(vec3(0.2, 1.0, 0.2), vec3(1.0, 0.12, 0.1), pulse);
            col = mix(col, col * tint * 1.4, 0.85 * k);

            // Hard throbbing brightness.
            col *= 1.0 + 0.3 * k * sin(t * 5.0);

            fragColor = vec4(col, 1.0);
        }
        """;

    private static RenderPipeline copyPipeline;
    private static RenderPipeline tripPipeline;
    private static Boolean pipelinesValid;
    private static GpuBuffer paramsBuffer;

    private static GpuTexture temp;
    private static GpuTextureView tempView;
    private static int texWidth = -1, texHeight = -1;

    static boolean render(float intensity) {
        GpuDevice device = RenderSystem.getDevice();
        if (!ensurePipelines(device)) return false;

        RenderTarget framebuffer = Minecraft.getInstance().getMainRenderTarget();
        GpuTextureView mainView = framebuffer.getColorTextureView();
        if (mainView == null) return false;

        int w = framebuffer.width, h = framebuffer.height;
        if (w <= 0 || h <= 0) return false;
        ensureTexture(device, w, h);

        CommandEncoder encoder = device.createCommandEncoder();
        writeParams(encoder, (float) (System.nanoTime() / 1.0e9), intensity);

        // Pass 1: framebuffer -> temp (passthrough copy, so pass 2 has a stable source to warp).
        try (RenderPass pass = encoder.createRenderPass(() -> "THM trip copy", tempView, OptionalInt.empty())) {
            pass.setPipeline(copyPipeline);
            pass.bindTexture("InSampler", mainView, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
            pass.draw(0, 3);
        }

        // Pass 2: warped temp -> main framebuffer.
        try (RenderPass pass = encoder.createRenderPass(() -> "THM trip draw", mainView, OptionalInt.empty())) {
            pass.setPipeline(tripPipeline);
            pass.setUniform("TripParams", paramsBuffer);
            pass.bindTexture("InSampler", tempView, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
            pass.draw(0, 3);
        }

        return true;
    }

    private static void writeParams(CommandEncoder encoder, float time, float intensity) {
        if (paramsBuffer == null) {
            paramsBuffer = RenderSystem.getDevice().createBuffer(() -> "THM trip params", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, PARAMS_SIZE);
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer data = stack.calloc((int) PARAMS_SIZE);
            data.putFloat(0, time);
            data.putFloat(4, intensity);
            encoder.writeToBuffer(paramsBuffer.slice(), data);
        }
    }

    private static boolean ensurePipelines(GpuDevice device) {
        if (pipelinesValid != null) return pipelinesValid;

        copyPipeline = RenderPipeline.builder(RenderPipelines.POST_PROCESSING_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(THMAddon.MOD_ID, "trip_copy"))
            .withVertexShader(VSH_ID)
            .withFragmentShader(COPY_FSH_ID)
            .withSampler("InSampler")
            .build();

        tripPipeline = RenderPipeline.builder(RenderPipelines.POST_PROCESSING_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(THMAddon.MOD_ID, "trip_draw"))
            .withVertexShader(VSH_ID)
            .withFragmentShader(TRIP_FSH_ID)
            .withUniform("TripParams", UniformType.UNIFORM_BUFFER)
            .withSampler("InSampler")
            .build();

        boolean copyOk = device.precompilePipeline(copyPipeline, TripBackground::shaderSource).isValid();
        boolean tripOk = device.precompilePipeline(tripPipeline, TripBackground::shaderSource).isValid();
        pipelinesValid = copyOk && tripOk;
        if (!pipelinesValid) THMAddon.LOG.warn("[THM] 'I'm high' shaders failed to compile, disabling the effect");
        return pipelinesValid;
    }

    private static String shaderSource(Identifier id, ShaderType type) {
        if (id.equals(VSH_ID)) return VSH_SRC;
        if (id.equals(COPY_FSH_ID)) return COPY_FSH_SRC;
        if (id.equals(TRIP_FSH_ID)) return TRIP_FSH_SRC;
        return null;
    }

    private static void ensureTexture(GpuDevice device, int w, int h) {
        if (temp != null && texWidth == w && texHeight == h) return;

        if (tempView != null) tempView.close();
        if (temp != null) temp.close();

        temp = device.createTexture(() -> "THM trip temp",
            GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_TEXTURE_BINDING, TextureFormat.RGBA8, w, h, 1, 1);
        tempView = device.createTextureView(temp);
        texWidth = w;
        texHeight = h;
    }
}
