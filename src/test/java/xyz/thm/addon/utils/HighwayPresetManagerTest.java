/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.utils;

import net.minecraft.nbt.NbtCompound;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HighwayPresetManagerTest {
    @Test
    void presetJsonRoundTripsSettings() throws IOException {
        NbtCompound settings = new NbtCompound();
        settings.putString("profile", "fast-paving");
        settings.putInt("placements", 7);

        NbtCompound decoded = HighwayPresetManager.decode(HighwayPresetManager.encode(settings));

        assertEquals("fast-paving", decoded.getString("profile", ""));
        assertEquals(7, decoded.getInt("placements", 0));
    }

    @Test
    void presetNameCannotEscapeItsDirectory() {
        assertEquals("MyPreset", HighwayPresetManager.safeName(" My/Preset "));
        assertEquals("", HighwayPresetManager.safeName(".."));
    }

    @Test
    void untrustedJsonRejectsAmbiguousOrDeepDocuments() {
        assertThrows(IOException.class, () -> HighwayPresetManager.decode(
            "{\"version\":1,\"version\":1,\"settings\":{}}"
        ));
        assertThrows(IOException.class, () -> HighwayPresetManager.decode(
            "{\"version\":1,\"settings\":{},\"extra\":true}"
        ));

        String nested = "[".repeat(40) + "]".repeat(40);
        assertThrows(IOException.class, () -> HighwayPresetManager.decode(
            "{\"version\":1,\"settings\":{\"groups\":" + nested + "}}"
        ));
    }
}
