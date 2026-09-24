/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.utils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.mojang.serialization.JsonOps;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.settings.SettingGroup;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtOps;
import xyz.thm.addon.THMAddon;
import xyz.thm.addon.modules.HighwayBuilderTHM;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class HighwayPresetManager {
    private static final int MAX_JSON_BYTES = 512 * 1024;
    private static final int MAX_JSON_DEPTH = 32;
    private static final int MAX_JSON_TOKENS = 50_000;
    private static final int MAX_JSON_STRING_LENGTH = 16_384;
    private static final int MAX_PRESETS = 256;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<String, NbtCompound> PRESETS = new LinkedHashMap<>();
    private static HighwayBuilderTHM module;

    private HighwayPresetManager() {}

    public static void initialize(HighwayBuilderTHM highwayBuilder) {
        module = highwayBuilder;
        loadAll();
    }

    public static List<String> names() {
        return List.copyOf(PRESETS.keySet());
    }

    public static boolean apply(String name) {
        if (module == null) return false;
        NbtCompound settings = PRESETS.get(name);
        if (settings == null) return false;

        NbtCompound previous = module.settings.toTag().copy();
        try {
            module.settings.fromTag(settings.copy());
            module.normalizeAfterThmProfileLoad();
            return true;
        } catch (RuntimeException e) {
            module.settings.fromTag(previous);
            module.normalizeAfterThmProfileLoad();
            THMAddon.LOG.warn("Failed to apply HighwayBuilder preset {}: {}", name, e.getMessage());
            return false;
        }
    }

    public static String save(String requestedName) throws IOException {
        if (module == null) throw new IOException("HighwayBuilder is not registered.");
        String name = safeName(requestedName);
        if (name.isEmpty()) throw new IOException("Enter a preset name.");

        Files.createDirectories(directory());
        Path target = directory().resolve(name + ".json");
        Path temporary = Files.createTempFile(directory(), "." + name + "-", ".tmp");
        NbtCompound settings = module.settings.toTag().copy();

        try {
            Files.writeString(temporary, encode(settings), StandardCharsets.UTF_8);
            try {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }

        PRESETS.put(name, settings);
        sortPresets();
        return name;
    }

    public static String safeName(String requestedName) {
        if (requestedName == null) return "";

        StringBuilder result = new StringBuilder();
        for (int i = 0; i < requestedName.length() && result.length() < 64; i++) {
            char c = requestedName.charAt(i);
            if (Character.isISOControl(c) || "\\/:*?\"<>|".indexOf(c) >= 0) continue;
            result.append(c);
        }

        String name = result.toString().trim();
        return name.equals(".") || name.equals("..") ? "" : name;
    }

    static String encode(NbtCompound settings) {
        JsonObject root = new JsonObject();
        root.addProperty("version", 1);
        root.add("settings", NbtOps.INSTANCE.convertTo(JsonOps.INSTANCE, settings));
        return GSON.toJson(root);
    }

    static NbtCompound decode(String json) throws IOException {
        return decode(json, false);
    }

    private static NbtCompound decode(String json, boolean validateModuleSettings) throws IOException {
        try {
            JsonObject root = parseRoot(json);
            JsonElement settingsJson = root.get("settings");
            if (validateModuleSettings) validateModuleSettings(settingsJson.getAsJsonObject());
            NbtElement settings = JsonOps.INSTANCE.convertTo(NbtOps.INSTANCE, settingsJson);
            if (!(settings instanceof NbtCompound compound)) throw new IOException("Settings must be an object.");
            return compound;
        } catch (IOException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new IOException("Invalid preset JSON.", e);
        }
    }

    private static JsonObject parseRoot(String json) throws IOException {
        if (json == null || json.length() > MAX_JSON_BYTES) throw new IOException("Preset JSON is too large.");
        validateJsonStructure(json);

        try (JsonReader reader = new JsonReader(new StringReader(json))) {
            reader.setStrictness(Strictness.STRICT);
            reader.setNestingLimit(MAX_JSON_DEPTH);
            JsonElement parsed = JsonParser.parseReader(reader);
            if (reader.peek() != JsonToken.END_DOCUMENT || !parsed.isJsonObject()) {
                throw new IOException("Preset root must be an object.");
            }

            JsonObject root = parsed.getAsJsonObject();
            if (root.size() != 2 || !root.has("version") || !root.has("settings")) {
                throw new IOException("Preset must contain only version and settings.");
            }
            JsonElement version = root.get("version");
            if (!version.isJsonPrimitive() || !version.getAsJsonPrimitive().isNumber() || !"1".equals(version.getAsString())) {
                throw new IOException("Unsupported preset version.");
            }
            if (!root.get("settings").isJsonObject()) throw new IOException("Settings must be an object.");
            return root;
        }
    }

    private static void validateJsonStructure(String json) throws IOException {
        int[] tokens = {0};
        try (JsonReader reader = new JsonReader(new StringReader(json))) {
            reader.setStrictness(Strictness.STRICT);
            reader.setNestingLimit(MAX_JSON_DEPTH);
            validateJsonValue(reader, 0, tokens);
            if (reader.peek() != JsonToken.END_DOCUMENT) throw new IOException("Preset contains trailing data.");
        } catch (RuntimeException e) {
            throw new IOException("Invalid preset JSON.", e);
        }
    }

    private static void validateJsonValue(JsonReader reader, int depth, int[] tokens) throws IOException {
        if (++tokens[0] > MAX_JSON_TOKENS) throw new IOException("Preset JSON is too complex.");
        if (depth > MAX_JSON_DEPTH) throw new IOException("Preset JSON is nested too deeply.");

        switch (reader.peek()) {
            case BEGIN_ARRAY -> {
                reader.beginArray();
                while (reader.hasNext()) validateJsonValue(reader, depth + 1, tokens);
                reader.endArray();
            }
            case BEGIN_OBJECT -> {
                reader.beginObject();
                Set<String> names = new HashSet<>();
                while (reader.hasNext()) {
                    String name = reader.nextName();
                    if (name.length() > MAX_JSON_STRING_LENGTH) throw new IOException("Preset JSON name is too long.");
                    if (!names.add(name)) throw new IOException("Preset JSON contains a duplicate field.");
                    validateJsonValue(reader, depth + 1, tokens);
                }
                reader.endObject();
            }
            case STRING, NUMBER -> {
                if (reader.nextString().length() > MAX_JSON_STRING_LENGTH) {
                    throw new IOException("Preset JSON value is too long.");
                }
            }
            case BOOLEAN -> reader.nextBoolean();
            case NULL -> reader.nextNull();
            default -> throw new IOException("Invalid preset JSON value.");
        }
    }

    private static void validateModuleSettings(JsonObject settings) throws IOException {
        if (module == null) throw new IOException("HighwayBuilder is not registered.");
        if (settings.size() > 1 || (settings.size() == 1 && !settings.has("groups"))) {
            throw new IOException("Unknown settings fields.");
        }
        if (!settings.has("groups")) return;
        if (!settings.get("groups").isJsonArray()) throw new IOException("Settings groups must be an array.");

        Set<String> seenGroups = new HashSet<>();
        for (JsonElement groupElement : settings.getAsJsonArray("groups")) {
            if (!groupElement.isJsonObject()) throw new IOException("Preset group must be an object.");
            JsonObject groupJson = groupElement.getAsJsonObject();
            rejectUnknownKeys(groupJson, Set.of("name", "sectionExpanded", "settings"), "group");
            String groupName = requiredString(groupJson, "name", "group");
            SettingGroup group = module.settings.getGroup(groupName);
            if (group == null) throw new IOException("Unknown settings group: " + groupName);
            if (!seenGroups.add(groupName)) throw new IOException("Duplicate settings group: " + groupName);
            if (groupJson.has("sectionExpanded")
                && (!groupJson.get("sectionExpanded").isJsonPrimitive()
                || !groupJson.getAsJsonPrimitive("sectionExpanded").isBoolean())) {
                throw new IOException("Invalid group expansion value.");
            }
            if (!groupJson.has("settings")) continue;
            if (!groupJson.get("settings").isJsonArray()) throw new IOException("Group settings must be an array.");

            Set<String> seenSettings = new HashSet<>();
            for (JsonElement settingElement : groupJson.getAsJsonArray("settings")) {
                if (!settingElement.isJsonObject()) throw new IOException("Preset setting must be an object.");
                JsonObject settingJson = settingElement.getAsJsonObject();
                String settingName = requiredString(settingJson, "name", "setting");
                if (group.get(settingName) == null) throw new IOException("Unknown setting: " + settingName);
                if (!seenSettings.add(settingName)) throw new IOException("Duplicate setting: " + settingName);
            }
        }
    }

    private static void rejectUnknownKeys(JsonObject object, Set<String> allowed, String label) throws IOException {
        for (String key : object.keySet()) {
            if (!allowed.contains(key)) throw new IOException("Unknown " + label + " field: " + key);
        }
    }

    private static String requiredString(JsonObject object, String key, String label) throws IOException {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new IOException("Missing or invalid " + label + " " + key + ".");
        }
        return value.getAsString();
    }

    private static void loadAll() {
        PRESETS.clear();
        Path directory = directory();
        if (!Files.isDirectory(directory)) return;

        try (var files = Files.list(directory)) {
            files.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json"))
                .sorted((left, right) -> left.getFileName().toString().compareToIgnoreCase(right.getFileName().toString()))
                .limit(MAX_PRESETS)
                .forEach(HighwayPresetManager::load);
        } catch (IOException e) {
            THMAddon.LOG.warn("Failed to load HighwayBuilder presets: {}", e.getMessage());
        }
    }

    private static void load(Path path) {
        String fileName = path.getFileName().toString();
        String name = safeName(fileName.substring(0, fileName.length() - ".json".length()));
        if (name.isEmpty()) return;

        try {
            if (Files.size(path) > MAX_JSON_BYTES) throw new IOException("Preset JSON is too large.");
            PRESETS.put(name, decode(Files.readString(path, StandardCharsets.UTF_8), true));
        } catch (IOException e) {
            THMAddon.LOG.warn("Failed to load HighwayBuilder preset {}: {}", fileName, e.getMessage());
        }
    }

    private static void sortPresets() {
        List<Map.Entry<String, NbtCompound>> entries = new ArrayList<>(PRESETS.entrySet());
        entries.sort(Map.Entry.comparingByKey(String.CASE_INSENSITIVE_ORDER));
        PRESETS.clear();
        for (Map.Entry<String, NbtCompound> entry : entries) PRESETS.put(entry.getKey(), entry.getValue());
    }

    private static Path directory() {
        return MeteorClient.FOLDER.toPath().resolve("thm").resolve("highway-builder-presets");
    }
}
