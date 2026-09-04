/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.utils;

import meteordevelopment.meteorclient.MeteorClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import xyz.thm.addon.THMAddon;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Downloads THM capes listed in the API's cape index to disk and registers them as textures on demand. */
public final class CapeManager {
    public record CapeEntry(String id, String url) {}

    // Capes are full texture images, not JSON metadata - much larger cap than TrustedHttp's
    // default JSON-response limit.
    private static final int MAX_CAPE_BYTES = 50 * 1024 * 1024;

    private static volatile String[] availableIds = {"None"};
    // Rendered (e.g. on the KitBot NPC via its assigned cape) but hidden from the self-cape picker.
    private static final Set<String> HIDDEN_CAPE_IDS = Set.of("kitbot");
    private static final Map<String, Identifier> textureCache = new HashMap<>();
    private static final Identifier MISSING = Identifier.of("thm-addon", "cape/missing");

    private CapeManager() {
    }

    public static void initialize() {
        start(false);
    }

    /** Deletes every downloaded cape and pulls them again from the index. */
    public static void redownload() {
        start(true);
    }

    private static void start(boolean clear) {
        Thread t = new Thread(() -> {
            if (clear) purge();
            refresh();
        }, "THM-CapeDownload");
        t.setDaemon(true);
        t.start();
    }

    private static synchronized void purge() {
        File dir = new File(new File(MeteorClient.FOLDER, "thm"), "capes");
        File[] files = dir.listFiles();
        if (files != null) for (File f : files) f.delete();
        textureCache.clear();
    }

    private static void refresh() {
        List<CapeEntry> entries = APIUtils.fetchCapeIndexFromApi();
        if (entries == null) return;

        List<String> ids = new ArrayList<>();
        ids.add("None");
        for (CapeEntry entry : entries) {
            downloadIfMissing(entry);
            if (!HIDDEN_CAPE_IDS.contains(entry.id().toLowerCase(Locale.ROOT))) ids.add(entry.id());
        }
        availableIds = ids.toArray(new String[0]);
    }

    private static void downloadIfMissing(CapeEntry entry) {
        File file = capeFile(entry.id());
        if (file == null) {
            THMAddon.LOG.warn("Ignoring cape index entry with invalid id '{}'", entry.id());
            return;
        }
        if (file.exists()) return;

        try {
            byte[] bytes = TrustedHttp.getBytes(entry.url(), TrustedHttp.Kind.IMAGE, MAX_CAPE_BYTES);
            if (bytes == null) {
                THMAddon.LOG.warn("Failed to download cape '{}'", entry.id());
                return;
            }

            file.getParentFile().mkdirs();
            Files.write(file.toPath(), bytes);
            THMAddon.LOG.info("Downloaded cape '{}'", entry.id());
        } catch (Exception e) {
            THMAddon.LOG.warn("Failed to download cape '{}': {}", entry.id(), e.getMessage());
        }
    }

    /** Options for the self-cape picker: "None" plus every id known from the cape index. */
    public static String[] availableCapeIds() {
        return availableIds;
    }

    /** Resolves (loading + registering the texture on first use) the render Identifier for a cape id, or null if unavailable. */
    public static synchronized Identifier getCapeTexture(String id) {
        if (id == null || id.isBlank() || id.equalsIgnoreCase("None")) return null;

        Identifier cached = textureCache.get(id);
        if (cached != null) return cached == MISSING ? null : cached;

        Identifier resolved = loadTexture(id);
        textureCache.put(id, resolved == null ? MISSING : resolved);
        return resolved;
    }

    private static Identifier loadTexture(String id) {
        File file = capeFile(id);
        if (file == null || !file.isFile()) return null;

        try {
            byte[] bytes = Files.readAllBytes(file.toPath());
            NativeImage image = NativeImage.read(bytes);
            Identifier textureId = Identifier.of("thm-addon", "cape/" + id);
            MinecraftClient.getInstance().getTextureManager().registerTexture(
                textureId, new NativeImageBackedTexture(() -> "thm-cape/" + id, image)
            );
            return textureId;
        } catch (Exception e) {
            THMAddon.LOG.warn("Failed to load cape texture '{}': {}", id, e.getMessage());
            return null;
        }
    }

    // Same shape the backend itself enforces for cape ids (POST /cape). id ultimately comes from
    // API responses (cape index, per-player cape map) - without this, a path-separator/traversal
    // id could point the read (getCapeTexture) or write (downloadIfMissing) at an arbitrary file.
    private static final Pattern SAFE_CAPE_ID = Pattern.compile("^[a-zA-Z0-9_-]{1,32}$");

    /** Null if {@code id} isn't a safe filename fragment - callers must treat that as "unavailable". */
    private static File capeFile(String id) {
        if (id == null || !SAFE_CAPE_ID.matcher(id).matches()) return null;
        return new File(new File(MeteorClient.FOLDER, "thm/capes"), id + ".webp");
    }
}
