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
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Downloads THM capes listed in the API's cape index to disk and registers them as textures on demand. */
public final class CapeManager {
    public record CapeEntry(String id, String url) {}

    private static volatile String[] availableIds = {"None"};
    // Rendered (e.g. on the KitBot NPC via its assigned cape) but hidden from the self-cape picker.
    private static final Set<String> HIDDEN_CAPE_IDS = Set.of("kitbot");
    private static final Map<String, Identifier> textureCache = new HashMap<>();
    private static final Identifier MISSING = Identifier.of("thm-addon", "cape/missing");

    private CapeManager() {
    }

    public static void initialize() {
        Thread t = new Thread(CapeManager::refresh, "THM-CapeDownload");
        t.setDaemon(true);
        t.start();
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
        if (file.exists()) return;

        try {
            HttpURLConnection cn = (HttpURLConnection) new URI(entry.url()).toURL().openConnection();
            cn.setRequestMethod("GET");
            cn.setConnectTimeout(10000);
            cn.setReadTimeout(10000);
            if (cn.getResponseCode() != 200) {
                cn.disconnect();
                return;
            }
            byte[] bytes = cn.getInputStream().readAllBytes();
            cn.disconnect();

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
        if (!file.isFile()) return null;

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

    private static File capeFile(String id) {
        return new File(new File(MeteorClient.FOLDER, "thm/capes"), id + ".webp");
    }
}
