/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.shaders;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BlurBackgroundTest {
    @Test
    void backgroundCadenceDoesNotFollowUiFrameRate() {
        long lastDraw = 0;
        int draws = 0;
        for (int frame = 0; frame < 120; frame++) {
            long now = frame * 1_000_000_000L / 120;
            if (BlurBackground.shouldUpdateShader(now, lastDraw, 30, frame == 0)) {
                lastDraw = now;
                draws++;
            }
        }
        assertEquals(30, draws);
    }

    @Test
    void changedBackgroundAndUnlimitedRateDrawImmediately() {
        assertFalse(BlurBackground.shouldUpdateShader(10, 9, 30, false));
        assertTrue(BlurBackground.shouldUpdateShader(10, 9, 30, true));
        assertTrue(BlurBackground.shouldUpdateShader(10, 9, 0, false));
    }

    @Test
    void renderResolutionPreservesAspectAndBounds() {
        assertEquals(960, BlurBackground.shaderDimension(1920, 50));
        assertEquals(540, BlurBackground.shaderDimension(1080, 50));
        assertEquals(480, BlurBackground.shaderDimension(1920, 25));
        assertEquals(1920, BlurBackground.shaderDimension(1920, 100));
        assertEquals(480, BlurBackground.shaderDimension(1920, 0));
        assertEquals(1920, BlurBackground.shaderDimension(1920, 200));
        assertEquals(4, BlurBackground.shaderDimension(1, 25));
    }
}
