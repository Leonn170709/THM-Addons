/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.utils;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import xyz.thm.addon.THMAddon;
import xyz.thm.addon.system.THMSystem;

import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.jar.JarFile;

public final class AddonUpdater {
    private static final int MAX_JAR_BYTES = 32 * 1024 * 1024;
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "THM addon updater");
        thread.setDaemon(true);
        return thread;
    });
    private static volatile List<Entry> entries = List.of();
    private static volatile String status = "Not checked yet";
    private static volatile boolean restartRequired;
    private static volatile Entry installedUpdate;
    private static boolean prompted;

    public record Entry(THMSystem.UpdateChannel channel, String branch, String version, String commit,
                        String title, String notes, String minecraft, String url, String sha256) {}

    private AddonUpdater() {}

    public static List<Entry> entries() { return entries; }
    public static String status() { return status; }
    public static boolean restartRequired() { return restartRequired; }
    public static String branch() { return currentBranch(); }

    public static boolean isInstalled(Entry entry) {
        Entry installed = installedUpdate;
        return entry.commit().equalsIgnoreCase(installed == null ? currentCommit() : installed.commit())
            && entry.version().equals(installed == null ? THMAddon.VERSION : installed.version())
            && entry.branch().equals(installed == null ? currentBranch() : installed.branch());
    }

    public static void promptOnTitle(Minecraft mc) {
        if (!restartRequired || prompted || !(mc.gui.screen() instanceof TitleScreen title)) return;
        prompted = true;
        mc.gui.setScreen(new ConfirmScreen(quit -> {
            if (quit) mc.stop();
            else mc.gui.setScreen(title);
        }, Component.literal("THM Addon updated"), Component.literal("Quit Minecraft to load the new JAR?"),
            Component.literal("Quit Minecraft"), Component.literal("Later")));
    }

    public static void checkOnStartup() {
        refresh(true, null);
    }

    public static void refresh(Runnable onDone) {
        refresh(false, onDone);
    }

    private static void refresh(boolean startup, Runnable onDone) {
        WORKER.execute(() -> {
            if (FabricLoader.getInstance().isDevelopmentEnvironment()) {
                status = "Updater disabled in development run";
                finish(onDone);
                return;
            }
            String url = GeneratedApiEndpoints.updaterUrl();
            String key = GeneratedApiEndpoints.updaterPublicKey();
            if (url == null || url.isBlank() || key.isBlank()) {
                entries = List.of();
                status = "Updater API is not configured in this build";
                finish(onDone);
                return;
            }
            if (!hasApiToken()) {
                entries = List.of();
                status = "Set a valid personal API token to access member updates";
                finish(onDone);
                return;
            }
            String branch = currentBranch();
            if (branch.isBlank()) {
                status = "Updater disabled: this JAR has no build branch";
                finish(onDone);
                return;
            }
            try {
                status = "Checking " + branch + " updates...";
                String response = TrustedHttp.getString(url, TrustedHttp.Kind.API, TrustedHttp.MAX_JSON_BYTES);
                if (response == null) throw new IllegalStateException("Member update API refused the request or is unavailable");
                status = "Verifying signed update feed...";
                entries = parseSignedFeed(response, URI.create(url), key,
                    FabricLoader.getInstance().getModContainer("minecraft").orElseThrow().getMetadata().getVersion().getFriendlyString(), branch);
                THMSystem system = THMSystem.get();
                Entry latest = system == null ? null : entries.stream()
                    .filter(e -> e.channel() == system.updateChannel.get()).findFirst().orElse(null);
                if (latest == null) {
                    status = "No " + branch + " builds for this channel";
                } else if (isInstalled(latest)) {
                    status = restartRequired ? "Update installed. Restart Minecraft to load it."
                        : "Already on the newest " + system.updateChannel.get() + " build";
                } else if (startup && system.autoUpdate.get()) {
                    installNow(latest);
                } else {
                    status = "Update available: " + latest.title();
                }
            } catch (Exception e) {
                status = "Update check failed: " + safeMessage(e);
                THMAddon.LOG.warn("Updater check failed: {}", safeMessage(e));
            }
            finish(onDone);
        });
    }

    public static void install(Entry entry, Runnable onDone) {
        WORKER.execute(() -> {
            if (!hasApiToken()) {
                status = "Set a valid personal API token to access member updates";
            } else if (!entries.contains(entry)) {
                status = "Choose a build from the verified feed";
            } else {
                installNow(entry);
            }
            finish(onDone);
        });
    }

    private static void installNow(Entry entry) {
        Path pending = null;
        try {
            if (isInstalled(entry)) {
                status = restartRequired ? "This build is installed. Restart Minecraft to load it."
                    : "This build is already installed";
                return;
            }
            if (!hasApiToken()) throw new IllegalStateException("No valid personal API token");
            status = "Downloading " + entry.title() + "...";
            byte[] jar = TrustedHttp.getBytes(entry.url(), TrustedHttp.Kind.API, MAX_JAR_BYTES);
            if (jar == null) throw new IllegalStateException("Could not download JAR");
            status = "Verifying SHA-256 for " + entry.title() + "...";
            String actual = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(jar));
            if (!MessageDigest.isEqual(actual.getBytes(StandardCharsets.US_ASCII), entry.sha256().getBytes(StandardCharsets.US_ASCII))) {
                throw new IllegalStateException("JAR SHA-256 mismatch; download discarded, installed addon unchanged");
            }
            Path mods = FabricLoader.getInstance().getGameDir().resolve("mods").toRealPath();
            Path current = FabricLoader.getInstance().getModContainer(THMAddon.MOD_ID).orElseThrow()
                .getOrigin().getPaths().getFirst().toRealPath();
            if (!current.getParent().equals(mods) || !current.getFileName().toString().endsWith(".jar")) {
                throw new IllegalStateException("Addon JAR must be directly inside the mods folder");
            }
            pending = Files.createTempFile(mods, ".thm-update-", ".pending");
            Files.write(pending, jar);
            status = "Checking addon JAR metadata...";
            validateJar(pending, entry);
            status = "Replacing current addon JAR...";
            Files.copy(current, mods.resolve("thm-addon-previous.jar.bak"), StandardCopyOption.REPLACE_EXISTING);
            Files.move(pending, current, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            installedUpdate = entry;
            restartRequired = true;
            status = "Installed " + entry.title() + ". Restart Minecraft to load it.";
        } catch (Exception e) {
            status = "Install failed: " + safeMessage(e);
            THMAddon.LOG.warn("Updater install failed: {}", safeMessage(e));
        } finally {
            if (pending != null) {
                try { Files.deleteIfExists(pending); } catch (Exception ignored) {}
            }
        }
    }

    private static void validateJar(Path path, Entry entry) throws Exception {
        try (JarFile jar = new JarFile(path.toFile())) {
            var metadata = jar.getJarEntry("fabric.mod.json");
            if (metadata == null || metadata.getSize() > 65_536) throw new IllegalStateException("Missing mod metadata");
            JsonObject json;
            try (InputStream in = jar.getInputStream(metadata)) {
                byte[] bytes = in.readNBytes(65_537);
                if (bytes.length > 65_536) throw new IllegalStateException("Oversized mod metadata");
                json = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
            }
            if (!THMAddon.MOD_ID.equals(json.get("id").getAsString())
                || !entry.version().equals(json.get("version").getAsString())
                || !entry.commit().equalsIgnoreCase(json.getAsJsonObject("custom").get("github:sha").getAsString())
                || !entry.branch().equals(json.getAsJsonObject("custom").get("github:branch").getAsString())
                || !entry.minecraft().equals(json.getAsJsonObject("depends").get("minecraft").getAsString())) {
                throw new IllegalStateException("JAR metadata does not match signed feed");
            }
        }
    }

    static List<Entry> parseSignedFeed(String envelope, URI feedUrl, String publicKey,
                                       String minecraftVersion, String branch) throws Exception {
        JsonObject outer = JsonParser.parseString(envelope).getAsJsonObject();
        byte[] payload = Base64.getDecoder().decode(outer.get("payload").getAsString());
        if (payload.length > TrustedHttp.MAX_JSON_BYTES) throw new IllegalArgumentException("Oversized signed feed");
        byte[] key = Base64.getDecoder().decode(publicKey);
        Signature verifier = Signature.getInstance("Ed25519");
        verifier.initVerify(KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(key)));
        verifier.update(payload);
        if (!verifier.verify(Base64.getDecoder().decode(outer.get("signature").getAsString()))) {
            throw new IllegalArgumentException("Invalid feed signature");
        }
        JsonObject data = JsonParser.parseString(new String(payload, StandardCharsets.UTF_8)).getAsJsonObject();
        if (data.get("format").getAsInt() != 1) throw new IllegalArgumentException("Unsupported feed format");
        JsonArray versions = data.getAsJsonArray("entries");
        if (versions.size() > 100) throw new IllegalArgumentException("Too many builds in feed");
        List<Entry> parsed = new ArrayList<>();
        for (var item : versions) {
            JsonObject row = item.getAsJsonObject();
            Entry entry = new Entry(
                THMSystem.UpdateChannel.valueOf(required(row, "channel", 6)),
                required(row, "branch", 100), required(row, "version", 80), required(row, "commit", 40),
                required(row, "title", 200), required(row, "notes", 20_000),
                required(row, "minecraft", 20), required(row, "url", 2048), required(row, "sha256", 64)
            );
            URI jarUrl = URI.create(entry.url());
            if (!"https".equals(jarUrl.getScheme()) || jarUrl.getUserInfo() != null
                || !feedUrl.getScheme().equalsIgnoreCase(jarUrl.getScheme())
                || !feedUrl.getHost().equalsIgnoreCase(jarUrl.getHost())
                || feedUrl.getPort() != jarUrl.getPort()
                || !entry.branch().matches("[A-Za-z0-9][A-Za-z0-9._-]{0,99}")
                || !entry.commit().matches("[0-9a-fA-F]{40}")
                || !entry.sha256().matches("[0-9a-fA-F]{64}")) {
                throw new IllegalArgumentException("Invalid build URL or digest");
            }
            if (entry.minecraft().equals(minecraftVersion) && entry.branch().equals(branch)) parsed.add(entry);
        }
        return List.copyOf(parsed);
    }

    private static String required(JsonObject row, String name, int limit) {
        String value = row.get(name).getAsString();
        if (value.isBlank() || value.length() > limit) throw new IllegalArgumentException("Invalid " + name);
        return value;
    }

    private static boolean hasApiToken() {
        THMSystem system = THMSystem.get();
        return system != null && system.hasApiToken();
    }

    private static String currentCommit() {
        return FabricLoader.getInstance().getModContainer(THMAddon.MOD_ID).orElseThrow()
            .getMetadata().getCustomValue("github:sha").getAsString().trim();
    }

    private static String currentBranch() {
        return FabricLoader.getInstance().getModContainer(THMAddon.MOD_ID).orElseThrow()
            .getMetadata().getCustomValue("github:branch").getAsString().trim();
    }

    private static void finish(Runnable callback) {
        if (callback != null) Minecraft.getInstance().execute(callback);
    }

    private static String safeMessage(Exception e) {
        if (e instanceof IllegalArgumentException || e instanceof IllegalStateException) return e.getMessage();
        return e.getClass().getSimpleName();
    }
}
