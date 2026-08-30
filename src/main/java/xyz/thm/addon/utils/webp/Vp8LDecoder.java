/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.utils.webp;

import net.minecraft.client.texture.NativeImage;
import xyz.thm.addon.THMAddon;

import java.nio.ByteBuffer;

/**
 * From-scratch decoder for lossless WebP (VP8L), per the spec at
 * https://developers.google.com/speed/webp/docs/webp_lossless_bitstream_specification
 * <p>
 * Only the lossless codec is implemented (no lossy VP8, no animation, no ALPH chunk) —
 * that's all THM's own bundled assets ever use, since we control the encoder.
 * Returns null for anything else so the caller can fall back / report unsupported.
 */
public final class Vp8LDecoder {

    public static final class DecodedImage {
        public final int width;
        public final int height;
        public final int[] argb;

        DecodedImage(int width, int height, int[] argb) {
            this.width = width;
            this.height = height;
            this.argb = argb;
        }
    }

    private Vp8LDecoder() {
    }

    /** Walks the RIFF container to find the VP8L chunk and decodes it. Returns null if none is found (e.g. lossy WebP) or decoding fails. */
    public static DecodedImage decode(byte[] file) {
        try {
            return decodeUnsafe(file);
        } catch (Exception | StackOverflowError e) {
            return null;
        }
    }

    private static DecodedImage decodeUnsafe(byte[] file) {
        if (file.length < 12 || file[0] != 'R' || file[1] != 'I' || file[2] != 'F' || file[3] != 'F'
            || file[8] != 'W' || file[9] != 'E' || file[10] != 'B' || file[11] != 'P') {
            return null;
        }

        int pos = 12;
        while (pos + 8 <= file.length) {
            String fourCc = new String(file, pos, 4, java.nio.charset.StandardCharsets.US_ASCII);
            long size = readUint32LE(file, pos + 4);
            int dataStart = pos + 8;
            if (fourCc.equals("VP8L")) {
                int len = (int) Math.min(size, file.length - dataStart);
                return decodeVp8l(file, dataStart, len);
            }
            pos = dataStart + (int) size + ((size & 1) != 0 ? 1 : 0);
        }
        return null;
    }

    private static long readUint32LE(byte[] b, int off) {
        return (b[off] & 0xffL) | ((b[off + 1] & 0xffL) << 8) | ((b[off + 2] & 0xffL) << 16) | ((b[off + 3] & 0xffL) << 24);
    }

    // 14 bits each lets the header claim up to 16384x16384 (~1GB for the pixel array alone) from
    // a file a few dozen bytes long - a cosmetic cape texture never needs anywhere near that, so
    // cap it well below what any real asset uses rather than let a crafted/corrupted file try to
    // allocate a gigabyte-scale array (OutOfMemoryError isn't even caught by decode()'s handler).
    private static final int MAX_DIMENSION = 2048;

    private static DecodedImage decodeVp8l(byte[] data, int offset, int length) {
        BitReader br = new BitReader(data, offset, length);
        if (br.readBits(8) != 0x2f) return null; // signature

        int width = br.readBits(14) + 1;
        int height = br.readBits(14) + 1;
        br.readBits(1); // alpha_is_used hint, irrelevant to decoding
        int version = br.readBits(3);
        if (version != 0) return null;
        if (width > MAX_DIMENSION || height > MAX_DIMENSION) return null;

        int[] result = decodeImageStream(br, width, height, true);
        return new DecodedImage(width, height, result);
    }

    // ---- Transforms ----

    private enum TransformKind {PREDICTOR, COLOR, SUBTRACT_GREEN, COLOR_INDEXING}

    private static final class Transform {
        final TransformKind kind;
        int sizeBits;
        int subWidth;
        int[] subPixels;
        int[] colorTable;
        int widthBits;
        int preWidth;

        Transform(TransformKind kind) {
            this.kind = kind;
        }
    }

    private static int divRoundUp(int num, int den) {
        return (num + den - 1) / den;
    }

    /**
     * Decodes one "spatially-coded-image" / "entropy-coded-image". {@code allowRecursion} is true only
     * for the top-level image: it gates both the transform loop and meta-prefix (per-block Huffman group) support.
     */
    private static int[] decodeImageStream(BitReader br, int width, int height, boolean allowRecursion) {
        int workWidth = width;
        java.util.List<Transform> transforms = new java.util.ArrayList<>();

        if (allowRecursion) {
            while (br.readBits(1) == 1) {
                int type = br.readBits(2);
                switch (type) {
                    case 0 -> transforms.add(readBlockTransform(br, TransformKind.PREDICTOR, workWidth, height));
                    case 1 -> transforms.add(readBlockTransform(br, TransformKind.COLOR, workWidth, height));
                    case 2 -> transforms.add(new Transform(TransformKind.SUBTRACT_GREEN));
                    case 3 -> {
                        Transform t = readColorIndexingTransform(br, workWidth);
                        workWidth = (t.widthBits > 0) ? divRoundUp(t.preWidth, 1 << t.widthBits) : t.preWidth;
                        transforms.add(t);
                    }
                    default -> throw new IllegalStateException("unreachable");
                }
            }
        }

        int[] pixels = decodeEntropyCodedImage(br, workWidth, height, allowRecursion);

        int[] result = pixels;
        int resultWidth = workWidth;
        for (int i = transforms.size() - 1; i >= 0; i--) {
            Transform t = transforms.get(i);
            switch (t.kind) {
                case SUBTRACT_GREEN -> applySubtractGreen(result);
                case PREDICTOR -> applyPredictor(result, resultWidth, height, t);
                case COLOR -> applyColorTransform(result, resultWidth, height, t);
                case COLOR_INDEXING -> {
                    result = applyColorIndexing(result, resultWidth, height, t);
                    resultWidth = t.preWidth;
                }
            }
        }
        return result;
    }

    private static Transform readBlockTransform(BitReader br, TransformKind kind, int width, int height) {
        Transform t = new Transform(kind);
        t.sizeBits = br.readBits(3) + 2;
        t.subWidth = divRoundUp(width, 1 << t.sizeBits);
        int subHeight = divRoundUp(height, 1 << t.sizeBits);
        t.subPixels = decodeImageStream(br, t.subWidth, subHeight, false);
        return t;
    }

    private static Transform readColorIndexingTransform(BitReader br, int width) {
        Transform t = new Transform(TransformKind.COLOR_INDEXING);
        int colorTableSize = br.readBits(8) + 1;
        int[] table = decodeImageStream(br, colorTableSize, 1, false);
        // Cumulative (subtraction-coded) delta decode, per ARGB channel, mod 256.
        for (int i = 1; i < table.length; i++) {
            table[i] = addChannelsMod256(table[i - 1], table[i]);
        }
        t.colorTable = table;
        t.widthBits = colorTableSize <= 2 ? 3 : colorTableSize <= 4 ? 2 : colorTableSize <= 16 ? 1 : 0;
        t.preWidth = width;
        return t;
    }

    private static int addChannelsMod256(int p1, int p2) {
        int a = (((p1 >>> 24) & 0xff) + ((p2 >>> 24) & 0xff)) & 0xff;
        int r = (((p1 >>> 16) & 0xff) + ((p2 >>> 16) & 0xff)) & 0xff;
        int g = (((p1 >>> 8) & 0xff) + ((p2 >>> 8) & 0xff)) & 0xff;
        int b = ((p1 & 0xff) + (p2 & 0xff)) & 0xff;
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static void applySubtractGreen(int[] pixels) {
        for (int i = 0; i < pixels.length; i++) {
            int p = pixels[i];
            int green = (p >>> 8) & 0xff;
            int red = ((p >>> 16) & 0xff) + green & 0xff;
            int blue = (p & 0xff) + green & 0xff;
            pixels[i] = (p & 0xff00ff00) | (red << 16) | blue;
        }
    }

    private static int average2(int p1, int p2) {
        int a = (((p1 >>> 24) & 0xff) + ((p2 >>> 24) & 0xff)) / 2;
        int r = (((p1 >>> 16) & 0xff) + ((p2 >>> 16) & 0xff)) / 2;
        int g = (((p1 >>> 8) & 0xff) + ((p2 >>> 8) & 0xff)) / 2;
        int b = ((p1 & 0xff) + (p2 & 0xff)) / 2;
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int clamp255(int v) {
        return v < 0 ? 0 : Math.min(v, 255);
    }

    private static int clampAddSubtractFull(int p1, int p2, int p3) {
        int a = clamp255(((p1 >>> 24) & 0xff) + ((p2 >>> 24) & 0xff) - ((p3 >>> 24) & 0xff));
        int r = clamp255(((p1 >>> 16) & 0xff) + ((p2 >>> 16) & 0xff) - ((p3 >>> 16) & 0xff));
        int g = clamp255(((p1 >>> 8) & 0xff) + ((p2 >>> 8) & 0xff) - ((p3 >>> 8) & 0xff));
        int b = clamp255((p1 & 0xff) + (p2 & 0xff) - (p3 & 0xff));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int clampAddSubtractHalf(int p1, int p2) {
        int a1 = (p1 >>> 24) & 0xff, a2 = (p2 >>> 24) & 0xff;
        int r1 = (p1 >>> 16) & 0xff, r2 = (p2 >>> 16) & 0xff;
        int g1 = (p1 >>> 8) & 0xff, g2 = (p2 >>> 8) & 0xff;
        int b1 = p1 & 0xff, b2 = p2 & 0xff;
        int a = clamp255(a1 + (a1 - a2) / 2);
        int r = clamp255(r1 + (r1 - r2) / 2);
        int g = clamp255(g1 + (g1 - g2) / 2);
        int b = clamp255(b1 + (b1 - b2) / 2);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int selectPredictor(int l, int t, int tl) {
        int pAlpha = ((l >>> 24) & 0xff) + ((t >>> 24) & 0xff) - ((tl >>> 24) & 0xff);
        int pRed = ((l >>> 16) & 0xff) + ((t >>> 16) & 0xff) - ((tl >>> 16) & 0xff);
        int pGreen = ((l >>> 8) & 0xff) + ((t >>> 8) & 0xff) - ((tl >>> 8) & 0xff);
        int pBlue = (l & 0xff) + (t & 0xff) - (tl & 0xff);

        int pL = Math.abs(pAlpha - ((l >>> 24) & 0xff)) + Math.abs(pRed - ((l >>> 16) & 0xff))
            + Math.abs(pGreen - ((l >>> 8) & 0xff)) + Math.abs(pBlue - (l & 0xff));
        int pT = Math.abs(pAlpha - ((t >>> 24) & 0xff)) + Math.abs(pRed - ((t >>> 16) & 0xff))
            + Math.abs(pGreen - ((t >>> 8) & 0xff)) + Math.abs(pBlue - (t & 0xff));

        return pL < pT ? l : t;
    }

    private static int predict(int mode, int l, int t, int tl, int tr) {
        return switch (mode) {
            case 0 -> 0xff000000;
            case 1 -> l;
            case 2 -> t;
            case 3 -> tr;
            case 4 -> tl;
            case 5 -> average2(average2(l, tr), t);
            case 6 -> average2(l, tl);
            case 7 -> average2(l, t);
            case 8 -> average2(tl, t);
            case 9 -> average2(t, tr);
            case 10 -> average2(average2(l, tl), average2(t, tr));
            case 11 -> selectPredictor(l, t, tl);
            case 12 -> clampAddSubtractFull(l, t, tl);
            case 13 -> clampAddSubtractHalf(average2(l, t), tl);
            default -> 0;
        };
    }

    private static void applyPredictor(int[] pixels, int width, int height, Transform t) {
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int idx = y * width + x;
                int predicted;
                if (x == 0 && y == 0) {
                    predicted = 0xff000000;
                } else if (y == 0) {
                    predicted = pixels[idx - 1];
                } else if (x == 0) {
                    predicted = pixels[idx - width];
                } else {
                    int l = pixels[idx - 1];
                    int top = pixels[idx - width];
                    int tl = pixels[idx - width - 1];
                    int tr = (x == width - 1) ? pixels[idx - x] : pixels[idx - width + 1];
                    int blockIndex = (y >> t.sizeBits) * t.subWidth + (x >> t.sizeBits);
                    int mode = (t.subPixels[blockIndex] >>> 8) & 0xff;
                    predicted = predict(mode, l, top, tl, tr);
                }
                pixels[idx] = addChannelsMod256(pixels[idx], predicted);
            }
        }
    }

    private static int colorTransformDelta(byte t, byte c) {
        return (t * c) >> 5;
    }

    private static void applyColorTransform(int[] pixels, int width, int height, Transform t) {
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int idx = y * width + x;
                int p = pixels[idx];
                int alpha = p & 0xff000000;
                int green = (p >>> 8) & 0xff;
                int red = (p >>> 16) & 0xff;
                int blue = p & 0xff;

                int blockIndex = (y >> t.sizeBits) * t.subWidth + (x >> t.sizeBits);
                int sub = t.subPixels[blockIndex];
                byte greenToRed = (byte) (sub & 0xff);
                byte greenToBlue = (byte) ((sub >>> 8) & 0xff);
                byte redToBlue = (byte) ((sub >>> 16) & 0xff);

                int tmpRed = red + colorTransformDelta(greenToRed, (byte) green);
                int tmpBlue = blue + colorTransformDelta(greenToBlue, (byte) green);
                tmpBlue += colorTransformDelta(redToBlue, (byte) (tmpRed & 0xff));

                int newRed = tmpRed & 0xff;
                int newBlue = tmpBlue & 0xff;
                pixels[idx] = alpha | (newRed << 16) | (green << 8) | newBlue;
            }
        }
    }

    private static int[] applyColorIndexing(int[] pixels, int reducedWidth, int height, Transform t) {
        int origWidth = t.preWidth;
        int[] table = t.colorTable;

        if (t.widthBits == 0) {
            int[] out = new int[pixels.length];
            for (int i = 0; i < pixels.length; i++) {
                out[i] = lookupColor(table, (pixels[i] >>> 8) & 0xff);
            }
            return out;
        }

        int subPerByte = 1 << t.widthBits;
        int bitsPerIndex = 8 / subPerByte;
        int mask = (1 << bitsPerIndex) - 1;
        int[] out = new int[origWidth * height];
        for (int y = 0; y < height; y++) {
            for (int packedX = 0; packedX < reducedWidth; packedX++) {
                int green = (pixels[y * reducedWidth + packedX] >>> 8) & 0xff;
                for (int k = 0; k < subPerByte; k++) {
                    int x = packedX * subPerByte + k;
                    if (x >= origWidth) break;
                    int index = (green >> (k * bitsPerIndex)) & mask;
                    out[y * origWidth + x] = lookupColor(table, index);
                }
            }
        }
        return out;
    }

    private static int lookupColor(int[] table, int index) {
        return (index < 0 || index >= table.length) ? 0 : table[index];
    }

    // ---- Entropy-coded image data (color cache + optional meta-prefix + Huffman + LZ77) ----

    private static final int[] DISTANCE_MAP_X = new int[120];
    private static final int[] DISTANCE_MAP_Y = new int[120];

    static {
        int[][] pairs = {
            {0, 1}, {1, 0}, {1, 1}, {-1, 1}, {0, 2}, {2, 0}, {1, 2},
            {-1, 2}, {2, 1}, {-2, 1}, {2, 2}, {-2, 2}, {0, 3}, {3, 0},
            {1, 3}, {-1, 3}, {3, 1}, {-3, 1}, {2, 3}, {-2, 3}, {3, 2},
            {-3, 2}, {0, 4}, {4, 0}, {1, 4}, {-1, 4}, {4, 1}, {-4, 1},
            {3, 3}, {-3, 3}, {2, 4}, {-2, 4}, {4, 2}, {-4, 2}, {0, 5},
            {3, 4}, {-3, 4}, {4, 3}, {-4, 3}, {5, 0}, {1, 5}, {-1, 5},
            {5, 1}, {-5, 1}, {2, 5}, {-2, 5}, {5, 2}, {-5, 2}, {4, 4},
            {-4, 4}, {3, 5}, {-3, 5}, {5, 3}, {-5, 3}, {0, 6}, {6, 0},
            {1, 6}, {-1, 6}, {6, 1}, {-6, 1}, {2, 6}, {-2, 6}, {6, 2},
            {-6, 2}, {4, 5}, {-4, 5}, {5, 4}, {-5, 4}, {3, 6}, {-3, 6},
            {6, 3}, {-6, 3}, {0, 7}, {7, 0}, {1, 7}, {-1, 7}, {5, 5},
            {-5, 5}, {7, 1}, {-7, 1}, {4, 6}, {-4, 6}, {6, 4}, {-6, 4},
            {2, 7}, {-2, 7}, {7, 2}, {-7, 2}, {3, 7}, {-3, 7}, {7, 3},
            {-7, 3}, {5, 6}, {-5, 6}, {6, 5}, {-6, 5}, {8, 0}, {4, 7},
            {-4, 7}, {7, 4}, {-7, 4}, {8, 1}, {8, 2}, {6, 6}, {-6, 6},
            {8, 3}, {5, 7}, {-5, 7}, {7, 5}, {-7, 5}, {8, 4}, {6, 7},
            {-6, 7}, {7, 6}, {-7, 6}, {8, 5}, {7, 7}, {-7, 7}, {8, 6},
            {8, 7}
        };
        for (int i = 0; i < 120; i++) {
            DISTANCE_MAP_X[i] = pairs[i][0];
            DISTANCE_MAP_Y[i] = pairs[i][1];
        }
    }

    private static int mapDistance(int distanceCode, int width) {
        if (distanceCode > 120) return distanceCode - 120;
        int dist = DISTANCE_MAP_X[distanceCode - 1] + DISTANCE_MAP_Y[distanceCode - 1] * width;
        return Math.max(dist, 1);
    }

    private static int prefixToValue(int prefixCode, BitReader br) {
        if (prefixCode < 4) return prefixCode + 1;
        int extraBits = (prefixCode - 2) >> 1;
        int offset = (2 + (prefixCode & 1)) << extraBits;
        return offset + br.readBits(extraBits) + 1;
    }

    private static int[] decodeEntropyCodedImage(BitReader br, int width, int height, boolean allowMetaPrefix) {
        boolean useColorCache = br.readBits(1) == 1;
        int cacheBits = 0;
        if (useColorCache) {
            cacheBits = br.readBits(4);
        }

        int[] huffmanImage = null;
        int prefixBits = 0;
        int prefixImageWidth = 0;
        int numGroups = 1;

        if (allowMetaPrefix && br.readBits(1) == 1) {
            prefixBits = br.readBits(3) + 2;
            prefixImageWidth = divRoundUp(width, 1 << prefixBits);
            int prefixImageHeight = divRoundUp(height, 1 << prefixBits);
            int[] entropyImage = decodeImageStream(br, prefixImageWidth, prefixImageHeight, false);
            huffmanImage = new int[entropyImage.length];
            int maxGroup = 0;
            for (int i = 0; i < entropyImage.length; i++) {
                int group = (entropyImage[i] >>> 8) & 0xffff;
                huffmanImage[i] = group;
                maxGroup = Math.max(maxGroup, group);
            }
            numGroups = maxGroup + 1;
        }

        int greenAlphabetSize = 256 + 24 + (useColorCache ? (1 << cacheBits) : 0);
        HuffmanTable[][] groups = new HuffmanTable[numGroups][5];
        for (int g = 0; g < numGroups; g++) {
            groups[g][0] = HuffmanTable.read(br, greenAlphabetSize);
            groups[g][1] = HuffmanTable.read(br, 256);
            groups[g][2] = HuffmanTable.read(br, 256);
            groups[g][3] = HuffmanTable.read(br, 256);
            groups[g][4] = HuffmanTable.read(br, 40);
        }

        int[] cache = useColorCache ? new int[1 << cacheBits] : null;
        int[] pixels = new int[width * height];
        int pos = 0, x = 0, y = 0;

        while (pos < pixels.length) {
            HuffmanTable[] group;
            if (huffmanImage != null) {
                int blockPos = (y >> prefixBits) * prefixImageWidth + (x >> prefixBits);
                group = groups[huffmanImage[blockPos]];
            } else {
                group = groups[0];
            }

            int s = group[0].decode(br);
            if (s < 256) {
                int green = s;
                int red = group[1].decode(br);
                int blue = group[2].decode(br);
                int alpha = group[3].decode(br);
                int argb = (alpha << 24) | (red << 16) | (green << 8) | blue;
                pixels[pos] = argb;
                if (cache != null) cache[colorCacheHash(argb, cacheBits)] = argb;
                pos++;
                x++;
                if (x == width) {
                    x = 0;
                    y++;
                }
            } else if (s < 256 + 24) {
                int length = prefixToValue(s - 256, br);
                int distPrefix = group[4].decode(br);
                int distanceCode = prefixToValue(distPrefix, br);
                int dist = mapDistance(distanceCode, width);
                for (int i = 0; i < length && pos < pixels.length; i++) {
                    int v = pixels[pos - dist];
                    pixels[pos] = v;
                    if (cache != null) cache[colorCacheHash(v, cacheBits)] = v;
                    pos++;
                    x++;
                    if (x == width) {
                        x = 0;
                        y++;
                    }
                }
            } else {
                int index = s - (256 + 24);
                int v = cache[index];
                pixels[pos] = v;
                cache[colorCacheHash(v, cacheBits)] = v;
                pos++;
                x++;
                if (x == width) {
                    x = 0;
                    y++;
                }
            }
        }
        return pixels;
    }

    private static int colorCacheHash(int color, int cacheBits) {
        return (0x1e35a7bd * color) >>> (32 - cacheBits);
    }

    // ---- Huffman ----

    private static final int[] CODE_LENGTH_CODE_ORDER = {17, 18, 0, 1, 2, 3, 4, 5, 16, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15};
    private static final int[] REPEAT_EXTRA_BITS = {2, 3, 7};
    private static final int[] REPEAT_OFFSETS = {3, 3, 11};

    private static final class HuffmanNode {
        HuffmanNode left, right;
        int symbol = -1;
    }

    private static final class HuffmanTable {
        int soleSymbol = -1;
        HuffmanNode root;

        int decode(BitReader br) {
            if (soleSymbol >= 0) return soleSymbol;
            HuffmanNode n = root;
            while (n.symbol < 0) {
                n = br.readBits(1) == 0 ? n.left : n.right;
            }
            return n.symbol;
        }

        static HuffmanTable read(BitReader br, int alphabetSize) {
            int[] codeLengths = new int[alphabetSize];
            boolean simple = br.readBits(1) == 1;
            if (simple) {
                int numSymbols = br.readBits(1) + 1;
                boolean first8Bits = br.readBits(1) == 1;
                int symbol0 = br.readBits(first8Bits ? 8 : 1);
                codeLengths[symbol0] = 1;
                if (numSymbols == 2) {
                    int symbol1 = br.readBits(8);
                    codeLengths[symbol1] = 1;
                }
            } else {
                int numCodeLengths = 4 + br.readBits(4);
                int[] codeLengthCodeLengths = new int[19];
                for (int i = 0; i < numCodeLengths; i++) {
                    codeLengthCodeLengths[CODE_LENGTH_CODE_ORDER[i]] = br.readBits(3);
                }
                HuffmanTable codeLengthTable = build(codeLengthCodeLengths);

                int maxSymbol;
                if (br.readBits(1) == 1) {
                    int lengthNBits = 2 + 2 * br.readBits(3);
                    maxSymbol = 2 + br.readBits(lengthNBits);
                } else {
                    maxSymbol = alphabetSize;
                }

                int symbol = 0;
                int prevCodeLength = 8;
                while (symbol < alphabetSize) {
                    if (maxSymbol-- == 0) break;
                    int codeLen = codeLengthTable.decode(br);
                    if (codeLen < 16) {
                        codeLengths[symbol++] = codeLen;
                        if (codeLen != 0) prevCodeLength = codeLen;
                    } else {
                        int slot = codeLen - 16;
                        int repeat = br.readBits(REPEAT_EXTRA_BITS[slot]) + REPEAT_OFFSETS[slot];
                        int useLen = (codeLen == 16) ? prevCodeLength : 0;
                        while (repeat-- > 0 && symbol < alphabetSize) {
                            codeLengths[symbol++] = useLen;
                        }
                    }
                }
            }
            return build(codeLengths);
        }

        private static HuffmanTable build(int[] codeLengths) {
            HuffmanTable table = new HuffmanTable();
            int nonZero = 0, sole = -1, maxLen = 0;
            for (int i = 0; i < codeLengths.length; i++) {
                if (codeLengths[i] > 0) {
                    nonZero++;
                    sole = i;
                    maxLen = Math.max(maxLen, codeLengths[i]);
                }
            }
            if (nonZero <= 1) {
                table.soleSymbol = Math.max(sole, 0);
                return table;
            }

            int[] blCount = new int[maxLen + 1];
            for (int len : codeLengths) if (len > 0) blCount[len]++;
            int[] nextCode = new int[maxLen + 1];
            int code = 0;
            for (int bits = 1; bits <= maxLen; bits++) {
                code = (code + blCount[bits - 1]) << 1;
                nextCode[bits] = code;
            }

            HuffmanNode root = new HuffmanNode();
            for (int symbol = 0; symbol < codeLengths.length; symbol++) {
                int len = codeLengths[symbol];
                if (len == 0) continue;
                int c = nextCode[len]++;
                HuffmanNode node = root;
                for (int bit = len - 1; bit > 0; bit--) {
                    boolean right = ((c >> bit) & 1) != 0;
                    if (right) {
                        if (node.right == null) node.right = new HuffmanNode();
                        node = node.right;
                    } else {
                        if (node.left == null) node.left = new HuffmanNode();
                        node = node.left;
                    }
                }
                if ((c & 1) != 0) node.right = leaf(symbol);
                else node.left = leaf(symbol);
            }
            table.root = root;
            return table;
        }

        private static HuffmanNode leaf(int symbol) {
            HuffmanNode n = new HuffmanNode();
            n.symbol = symbol;
            return n;
        }
    }

    // ---- Bit reader (LSB-first, per spec section 1) ----

    private static final class BitReader {
        private final byte[] data;
        private final int end;
        private int pos;
        private long buffer;
        private int bitCount;

        BitReader(byte[] data, int offset, int length) {
            this.data = data;
            this.pos = offset;
            this.end = offset + length;
        }

        private void fill() {
            while (bitCount <= 56 && pos < end) {
                buffer |= (data[pos++] & 0xffL) << bitCount;
                bitCount += 8;
            }
        }

        int readBits(int n) {
            if (n == 0) return 0;
            fill();
            int result = (int) (buffer & ((1L << n) - 1));
            buffer >>>= n;
            bitCount -= n;
            return result;
        }
    }

    private static final byte[] RIFF = {'R', 'I', 'F', 'F'};
    private static final byte[] WEBP = {'W', 'E', 'B', 'P'};

    /** Returns the decoded image if {@code buffer} (from its current position) holds WebP data, else null. Leaves buffer position untouched. */
    public static NativeImage tryDecode(NativeImage.Format format, ByteBuffer buffer) {
        if (!isWebp(buffer)) return null;

        try {
            byte[] bytes = new byte[buffer.remaining()];
            buffer.duplicate().get(bytes);

            DecodedImage image = decode(bytes);
            if (image == null) return null;
            return toNativeImage(format, image);
        } catch (Exception e) {
            THMAddon.LOG.warn("Failed to decode WebP image: {}", e.getMessage());
            return null;
        }
    }

    private static boolean isWebp(ByteBuffer buffer) {
        if (buffer.remaining() < 12) return false;
        int base = buffer.position();
        for (int i = 0; i < 4; i++) if (buffer.get(base + i) != RIFF[i]) return false;
        for (int i = 0; i < 4; i++) if (buffer.get(base + 8 + i) != WEBP[i]) return false;
        return true;
    }

    private static NativeImage toNativeImage(NativeImage.Format format, DecodedImage image) {
        NativeImage.Format target = format != null ? format : NativeImage.Format.RGBA;
        NativeImage nativeImage = new NativeImage(target, image.width, image.height, false);
        for (int y = 0; y < image.height; y++) {
            for (int x = 0; x < image.width; x++) {
                nativeImage.setColorArgb(x, y, image.argb[y * image.width + x]);
            }
        }
        return nativeImage;
    }
}
