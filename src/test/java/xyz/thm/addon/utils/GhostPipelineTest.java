/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.utils;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import xyz.thm.addon.utils.render.GhostRenderer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GhostPipelineTest {
    @Test
    void disablesDepthAndPreservesShaderBindingsAndColorState() {
        RenderPipeline original = RenderPipeline.builder()
            .withLocation("test/ghost")
            .withVertexShader("test/entity")
            .withFragmentShader("test/entity")
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .withBindGroupLayout(BindGroupLayout.builder().withSampler("Sampler0").build())
            .withColorTargetState(ColorTargetState.DEFAULT)
            .withShaderDefine("ALPHA_CUTOUT", 0.1f)
            .withDepthStencilState(DepthStencilState.DEFAULT)
            .withCull(false)
            .build();

        RenderPipeline ghost = GhostRenderer.withoutDepth(original);
        assertNull(ghost.getDepthStencilState());
        assertEquals(DepthStencilState.DEFAULT, original.getDepthStencilState());
        assertEquals(original.getVertexShader(), ghost.getVertexShader());
        assertEquals(original.getFragmentShader(), ghost.getFragmentShader());
        assertEquals(original.getShaderDefines(), ghost.getShaderDefines());
        assertEquals(original.getBindGroupLayouts(), ghost.getBindGroupLayouts());
        assertArrayEquals(original.getColorTargetStates(), ghost.getColorTargetStates());
        assertArrayEquals(original.getVertexFormatBindings(), ghost.getVertexFormatBindings());
        assertEquals(original.getPrimitiveTopology(), ghost.getPrimitiveTopology());
        assertFalse(ghost.isCull());
        assertSame(ghost, GhostRenderer.withoutDepth(original));
        assertSame(original, GhostRenderer.pipeline(original));
    }
}
