/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.utils;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import net.minecraft.nbt.NbtCompound;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

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
    void movedSettingsFromOlderPresetsKeepTheirValues(@TempDir Path temp) throws IOException {
        String old = """
            {"version":1,"settings":{"groups":[
              {"name":"Experimental","settings":[
                {"name":"mine-lookahead","value":0},
                {"name":"predictive-echest-replace","value":1}
              ]},
              {"name":"Digging","settings":[]},
              {"name":"Ender Chests","settings":[]}
            ]}}
            """;

        Path preset = temp.resolve("old.json");
        Files.writeString(preset, old);
        HighwayPresetManager.persistMigratedPreset(preset, HighwayPresetManager.decode(old));
        assertEquals(old, Files.readString(temp.resolve("old.json.pre-migration.bak")));

        JsonArray groups = JsonParser.parseString(Files.readString(preset))
            .getAsJsonObject().getAsJsonObject("settings").getAsJsonArray("groups");

        assertEquals(0, groups.get(0).getAsJsonObject().getAsJsonArray("settings").size());
        assertEquals("mine-lookahead", groups.get(1).getAsJsonObject().getAsJsonArray("settings")
            .get(0).getAsJsonObject().get("name").getAsString());
        assertEquals(0, groups.get(1).getAsJsonObject().getAsJsonArray("settings")
            .get(0).getAsJsonObject().get("value").getAsInt());
        assertEquals("predictive-echest-replace", groups.get(2).getAsJsonObject().getAsJsonArray("settings")
            .get(0).getAsJsonObject().get("name").getAsString());
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
