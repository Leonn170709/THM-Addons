/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.utils.webp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

// Fixtures: lossless WebPs written by libwebp (Pillow) with the source PNG as the expected pixels.
class Vp8LDecoderTest {
    @ParameterizedTest
    @ValueSource(strings = {"gradient", "palette", "noise", "single", "mixed"})
    void decodesLosslessExactly(String name) throws IOException {
        assertDecodesTo(read("/webp/" + name + ".webp"), "/webp/" + name + ".png");
    }

    /** The icons the addon actually ships; obby.webp is a VP8X container with EXIF/XMP chunks. */
    @ParameterizedTest
    @ValueSource(strings = {"blacktransparent", "whitetransparent", "obby"})
    void decodesShippedIcons(String name) throws IOException {
        assertDecodesTo(read("/assets/icon/" + name + ".webp"), "/webp/icon-" + name + ".png");
    }

    @Test
    void lossyReturnsNull() throws IOException {
        assertNull(Vp8LDecoder.decode(read("/webp/lossy.webp")));
    }

    @Test
    void garbageReturnsNull() {
        assertNull(Vp8LDecoder.decode(new byte[0]));
        assertNull(Vp8LDecoder.decode("not a webp at all".getBytes()));
        assertNull(Vp8LDecoder.decode("RIFF\0\0\0\0WEBP".getBytes()));
    }

    @Test
    void truncatedFilesNeverThrow() throws IOException {
        byte[] full = read("/webp/mixed.webp");
        for (int len = 0; len < full.length; len += 7) {
            byte[] cut = Arrays.copyOf(full, len);
            assertDoesNotThrow(() -> Vp8LDecoder.decode(cut), "length " + len);
        }
    }

    @Test
    void corruptedBytesNeverThrow() throws IOException {
        byte[] full = read("/webp/mixed.webp");
        java.util.Random rng = new java.util.Random(1);
        for (int i = 0; i < 300; i++) {
            byte[] bad = full.clone();
            for (int j = 0; j < 4; j++) bad[20 + rng.nextInt(bad.length - 20)] ^= (byte) (1 + rng.nextInt(255));
            assertDoesNotThrow(() -> Vp8LDecoder.decode(bad));
        }
    }

    private static void assertDecodesTo(byte[] webp, String pngPath) throws IOException {
        Vp8LDecoder.DecodedImage img = Vp8LDecoder.decode(webp);
        assertNotNull(img, "decoder returned null");

        BufferedImage expected;
        try (InputStream in = Vp8LDecoderTest.class.getResourceAsStream(pngPath)) {
            expected = ImageIO.read(in);
        }
        assertEquals(expected.getWidth(), img.width);
        assertEquals(expected.getHeight(), img.height);
        for (int y = 0; y < img.height; y++) {
            for (int x = 0; x < img.width; x++) {
                int want = expected.getRGB(x, y);
                int got = img.argb[y * img.width + x];
                if (want != got) fail(String.format("pixel (%d,%d): want %08x got %08x", x, y, want, got));
            }
        }
    }

    private static byte[] read(String path) throws IOException {
        try (InputStream in = Vp8LDecoderTest.class.getResourceAsStream(path)) {
            assertNotNull(in, path);
            return in.readAllBytes();
        }
    }
}
