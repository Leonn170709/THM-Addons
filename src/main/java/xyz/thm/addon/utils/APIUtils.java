/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.utils;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import meteordevelopment.meteorclient.MeteorClient;
import xyz.thm.addon.THMAddon;
import xyz.thm.addon.system.THMSystem;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/**
 * Talks to the THM API. Endpoint URLs live encrypted in {@link GeneratedApiEndpoints} (build-time
 * generated, git-ignored); this file stays plain and is never touched by the build. Every request
 * goes through {@link TrustedHttp}, which rejects SSRF targets, cross-host redirects, and
 * oversized responses. Response bodies are treated as untrusted data.
 */
public class APIUtils {
    private static final Gson GSON = new Gson();
    private static final int MAX_MEMBERS = 4_000;
    private static final int MAX_USERNAMES_PER_MEMBER = 32;
    private static final int MAX_CAPES = 64;
    private static final int MAX_HIGHWAY_ROWS = 8_000;

    private APIUtils() {}

    private static volatile String cachedPassword;

    /**
     * Key for the local on-disk stats-cache encryption (see HighwayBuilderTHM's stats artifacts) -
     * not an API secret. Persisted to disk so it survives client restarts (the cache written before
     * a restart must still decrypt after it); generated once on first use.
     */
    public static synchronized String getPassword() {
        if (cachedPassword != null) return cachedPassword;

        File file = new File(new File(MeteorClient.FOLDER, "thm"), "stats-key");
        try {
            if (file.isFile()) {
                String existing = Files.readString(file.toPath(), StandardCharsets.UTF_8).trim();
                if (!existing.isEmpty()) {
                    cachedPassword = existing;
                    return cachedPassword;
                }
            }

            byte[] random = new byte[32];
            new SecureRandom().nextBytes(random);
            StringBuilder hex = new StringBuilder();
            for (byte b : random) hex.append(String.format("%02x", b));

            file.getParentFile().mkdirs();
            Files.writeString(file.toPath(), hex.toString(), StandardCharsets.UTF_8);
            cachedPassword = hex.toString();
        } catch (IOException e) {
            THMAddon.LOG.warn("Failed to load/generate stats cache key: {}", e.getMessage());
            cachedPassword = "thm-fallback-stats-key";
        }
        return cachedPassword;
    }

    private static String apiToken() {
        try {
            THMSystem system = THMSystem.get();
            return system == null ? "" : system.getApiToken();
        } catch (Throwable t) {
            return "";
        }
    }

    private static String jsonContent(String message) {
        return "{\"content\": \"" + message.replace("\"", "\\\"") + "\"}";
    }

    public static void sendStatus(String message) {
        new Thread(() ->
            TrustedHttp.postJson(GeneratedApiEndpoints.statusUrl(), jsonContent(message), TrustedHttp.Kind.API, apiToken()),
            "thm-status").start();
    }

    public static void sendStatistics(String message) {
        new Thread(() ->
            TrustedHttp.postJson(GeneratedApiEndpoints.highwayUrl(), jsonContent(message), TrustedHttp.Kind.API, apiToken()),
            "thm-statistics").start();
    }

    // Discord webhook URL, supplied by the player at runtime - never attach our API token to it.
    public static void sendToWebhook(String url, String message) {
        new Thread(() ->
            TrustedHttp.postJson(url, jsonContent(message), TrustedHttp.Kind.USER_WEBHOOK, null),
            "thm-webhook").start();
    }

    private static String stringField(JsonObject o, String field) {
        return o.has(field) && o.get(field).isJsonPrimitive() ? o.get(field).getAsString() : "";
    }

    public static List<ThmMembers.Member> fetchMembersFromApi() {
        try {
            String response = TrustedHttp.getString(GeneratedApiEndpoints.memberHudUrl(), TrustedHttp.Kind.API, TrustedHttp.MAX_JSON_BYTES);
            if (response == null) return null;

            JsonArray jsonArray = GSON.fromJson(response, JsonArray.class);
            if (jsonArray == null) return null;
            if (jsonArray.size() > MAX_MEMBERS) {
                THMAddon.LOG.warn("Member list exceeded {} entries; ignoring remote payload", MAX_MEMBERS);
                return null;
            }

            List<ThmMembers.Member> members = new ArrayList<>();
            for (int i = 0; i < jsonArray.size(); i++) {
                JsonElement element = jsonArray.get(i);
                if (element == null || !element.isJsonObject()) continue;
                JsonObject jsonObject = element.getAsJsonObject();
                JsonArray usernamesArray = jsonObject.getAsJsonArray("usernames");
                if (usernamesArray == null) continue;

                int count = Math.min(usernamesArray.size(), MAX_USERNAMES_PER_MEMBER);
                List<String> valid = new ArrayList<>(count);
                for (int j = 0; j < count; j++) {
                    JsonElement nameEl = usernamesArray.get(j);
                    if (nameEl == null || !nameEl.isJsonPrimitive()) continue;
                    String name = nameEl.getAsString();
                    if (isMinecraftUsername(name)) valid.add(name);
                }
                if (valid.isEmpty()) continue;

                String rank = stringField(jsonObject, "rank");
                String rankId = stringField(jsonObject, "rankId");
                String branch = stringField(jsonObject, "branch");
                String discordName = stringField(jsonObject, "discordname");
                String displayName = valid.get(0);
                if (discordName.isEmpty()) discordName = displayName;

                members.add(new ThmMembers.Member(displayName, valid.toArray(new String[0]), rank, rankId, branch, discordName));
            }
            THMAddon.LOG.info("Fetched Members");
            return members;
        } catch (Exception e) {
            THMAddon.LOG.warn("Error fetching members from API: {}", e.getMessage());
            return null;
        }
    }

    private static boolean isMinecraftUsername(String name) {
        if (name == null || name.isEmpty() || name.length() > 16) return false;
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_';
            if (!ok) return false;
        }
        return true;
    }

    public static Map<String, String> fetchHighwayStatusFromApi() {
        try {
            String body = TrustedHttp.getString(GeneratedApiEndpoints.highwayStatusUrl(), TrustedHttp.Kind.API, TrustedHttp.MAX_JSON_BYTES);
            if (body == null) return null;

            JsonObject root = GSON.fromJson(body, JsonObject.class);
            Map<String, String> highwayByName = new HashMap<>();
            Map<String, Long> newestTimestampByName = new HashMap<>();
            if (root != null) {
                if (root.size() > MAX_HIGHWAY_ROWS) {
                    THMAddon.LOG.warn("Highway status payload exceeded {} entries; ignoring remote payload", MAX_HIGHWAY_ROWS);
                    return highwayByName;
                }
                for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
                    String highway = entry.getKey();
                    if (!entry.getValue().isJsonObject()) continue;
                    JsonObject value = entry.getValue().getAsJsonObject();
                    if (!value.has("username")) continue;

                    String raw = value.get("username").getAsString().trim().toLowerCase(Locale.ROOT);
                    if (raw.isEmpty() || "unknown".equals(raw)) continue;

                    long timestamp = value.has("timestamp") ? value.get("timestamp").getAsLong() : Long.MIN_VALUE;
                    Long existingTimestamp = newestTimestampByName.get(raw);
                    if (existingTimestamp == null || timestamp >= existingTimestamp) {
                        newestTimestampByName.put(raw, timestamp);
                        highwayByName.put(raw, highway);
                    }
                }
            }
            return highwayByName;
        } catch (Exception e) {
            THMAddon.LOG.warn("Error fetching highway status from API: {}", e.getMessage());
            return null;
        }
    }

    public static Map<String, String> fetchCapeListFromApi() {
        try {
            String body = TrustedHttp.getString(GeneratedApiEndpoints.capeListUrl(), TrustedHttp.Kind.API, TrustedHttp.MAX_JSON_BYTES);
            if (body == null) return null;

            JsonObject root = GSON.fromJson(body, JsonObject.class);
            if (root == null || !root.has("players")) return null;

            JsonObject players = root.getAsJsonObject("players");
            Map<String, String> result = new HashMap<>();
            for (Map.Entry<String, JsonElement> entry : players.entrySet()) {
                if (result.size() >= MAX_MEMBERS) break;
                String key = entry.getKey().toLowerCase(Locale.ROOT);
                if (!entry.getValue().isJsonObject()) continue;
                JsonObject p = entry.getValue().getAsJsonObject();
                if (p.has("cape")) result.put(key, p.get("cape").getAsString());
            }
            THMAddon.LOG.info("Fetched Cape List");
            return result;
        } catch (Exception e) {
            THMAddon.LOG.warn("Error fetching cape list: {}", e.getMessage());
            return null;
        }
    }

    public static void postCapeSelection(String cape) {
        if (mc.player == null) return;
        String username = mc.player.getGameProfile().name();
        String token = apiToken();
        if (token.isEmpty()) return;

        String json = "{\"username\":\"" + username.replace("\"", "\\\"")
            + "\",\"cape\":\"" + cape.replace("\"", "\\\"")
            + "\",\"timestamp\":" + System.currentTimeMillis()
            + ",\"token\":\"" + token.replace("\"", "\\\"") + "\"}";
        new Thread(() -> TrustedHttp.postJson(GeneratedApiEndpoints.capePostUrl(), json, TrustedHttp.Kind.API, token), "thm-cape-post").start();
    }

    public static List<CapeManager.CapeEntry> fetchCapeIndexFromApi() {
        try {
            String body = TrustedHttp.getString(GeneratedApiEndpoints.capeIndexUrl(), TrustedHttp.Kind.API, TrustedHttp.MAX_JSON_BYTES);
            if (body == null) return null;

            JsonObject root = GSON.fromJson(body, JsonObject.class);
            if (root == null || !root.has("capes")) return null;

            JsonArray arr = root.getAsJsonArray("capes");
            List<CapeManager.CapeEntry> result = new ArrayList<>();
            for (int i = 0; i < arr.size() && result.size() < MAX_CAPES; i++) {
                JsonElement element = arr.get(i);
                if (element == null || !element.isJsonObject()) continue;
                JsonObject o = element.getAsJsonObject();
                if (!o.has("id") || !o.has("url")) continue;
                result.add(new CapeManager.CapeEntry(o.get("id").getAsString(), o.get("url").getAsString()));
            }
            return result;
        } catch (Exception e) {
            THMAddon.LOG.warn("Error fetching cape index from API: {}", e.getMessage());
            return null;
        }
    }
}
