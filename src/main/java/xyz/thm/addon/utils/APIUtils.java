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
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Scanner;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class APIUtils {
    // Filled in at build time from secrets.properties (git-ignored) - see generateApiSecrets in
    // build.gradle.kts, which rewrites these exact literals in place. Never commit real values.
    private static final String MEMBER_HUD_URL = "PLACEHOLDER_MEMBER_HUD_URL";
    private static final String HIGHWAY_URL = "PLACEHOLDER_HIGHWAY_URL";
    private static final String STATUS_URL = "PLACEHOLDER_STATUS_URL";
    private static final String HIGHWAY_STATUS_URL = "PLACEHOLDER_HIGHWAY_STATUS_URL";
    private static final String CAPE_URL = "PLACEHOLDER_CAPE_URL";
    private static final String CAPE_POST_URL = "PLACEHOLDER_CAPE_POST_URL";
    private static final String CAPE_INDEX_URL = "PLACEHOLDER_CAPE_INDEX_URL";

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

    private static String httpGet(String url) {
        try {
            HttpURLConnection cn = (HttpURLConnection) new URI(url).toURL().openConnection();
            cn.setRequestMethod("GET");
            cn.setConnectTimeout(5000);
            cn.setReadTimeout(5000);
            String token = apiToken();
            if (!token.isEmpty()) cn.setRequestProperty("Authorization", "Bearer " + token);

            if (cn.getResponseCode() != 200) {
                cn.disconnect();
                return null;
            }
            StringBuilder sb = new StringBuilder();
            try (Scanner sc = new Scanner(cn.getInputStream())) {
                while (sc.hasNextLine()) sb.append(sc.nextLine());
            }
            cn.disconnect();
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private static void httpPostJson(String url, String json, String token, String logLabel) {
        new Thread(() -> {
            HttpURLConnection cn = null;
            try {
                cn = (HttpURLConnection) new URI(url).toURL().openConnection();
                cn.setRequestMethod("POST");
                cn.setRequestProperty("Content-Type", "application/json");
                if (token != null && !token.isEmpty()) cn.setRequestProperty("Authorization", "Bearer " + token);
                cn.setDoOutput(true);
                cn.setConnectTimeout(10000);
                cn.setReadTimeout(10000);

                byte[] body = json.getBytes(StandardCharsets.UTF_8);
                try (OutputStream os = cn.getOutputStream()) {
                    os.write(body, 0, body.length);
                    os.flush();
                }

                int rc = cn.getResponseCode();
                try (InputStream is = rc >= 400 ? cn.getErrorStream() : cn.getInputStream()) {
                    if (is != null) is.readAllBytes();
                }
                if (rc != 204 && rc != 200) THMAddon.LOG.warn("{} response code: {}", logLabel, rc);
            } catch (Exception e) {
                THMAddon.LOG.warn("Failed to send {}: {}", logLabel, e.getMessage());
            } finally {
                if (cn != null) cn.disconnect();
            }
        }).start();
    }

    public static void sendStatus(String message) {
        httpPostJson(STATUS_URL, "{\"content\": \"" + message.replace("\"", "\\\"") + "\"}", apiToken(), "status");
    }

    public static void sendStatistics(String message) {
        httpPostJson(HIGHWAY_URL, "{\"content\": \"" + message.replace("\"", "\\\"") + "\"}", apiToken(), "statistics");
    }

    // Discord webhook URL, supplied by the player at runtime - never attach our API token to it.
    public static void sendToWebhook(String url, String message) {
        httpPostJson(url, "{\"content\": \"" + message.replace("\"", "\\\"") + "\"}", null, "webhook");
    }

    public static List<ThmMembers.Member> fetchMembersFromApi() {
        try {
            String response = httpGet(MEMBER_HUD_URL);
            if (response == null) return null;
            THMAddon.LOG.info("Fetched Members");

            Gson gson = new Gson();
            JsonArray jsonArray = gson.fromJson(response, JsonArray.class);
            List<ThmMembers.Member> members = new ArrayList<>();
            for (int i = 0; i < jsonArray.size(); i++) {
                JsonObject jsonObject = jsonArray.get(i).getAsJsonObject();
                JsonArray usernamesArray = jsonObject.getAsJsonArray("usernames");
                String[] usernames = new String[usernamesArray.size()];
                for (int j = 0; j < usernamesArray.size(); j++) usernames[j] = usernamesArray.get(j).getAsString();

                String rank = jsonObject.get("rank").getAsString();
                String rankId = jsonObject.has("rankId") ? jsonObject.getAsJsonPrimitive("rankId").getAsString() : "";
                String branch = jsonObject.has("branch") ? jsonObject.getAsJsonPrimitive("branch").getAsString() : "";
                String discordName = jsonObject.has("discordname") ? jsonObject.getAsJsonPrimitive("discordname").getAsString() : "";
                String displayName = usernames.length > 0 ? usernames[0] : "Unknown";
                if (discordName.isEmpty()) discordName = displayName;

                members.add(new ThmMembers.Member(displayName, usernames, rank, rankId, branch, discordName));
            }
            return members;
        } catch (Exception e) {
            THMAddon.LOG.warn("Error fetching members from API: {}", e.getMessage());
        }
        return null;
    }

    public static Map<String, String> fetchHighwayStatusFromApi() {
        try {
            String body = httpGet(HIGHWAY_STATUS_URL);
            if (body == null) return null;

            Gson gson = new Gson();
            JsonObject root = gson.fromJson(body, JsonObject.class);
            Map<String, String> highwayByName = new HashMap<>();
            Map<String, Long> newestTimestampByName = new HashMap<>();
            if (root != null) {
                for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
                    String highway = entry.getKey();
                    JsonObject value = entry.getValue().getAsJsonObject();
                    if (value == null || !value.has("username")) continue;

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
            String body = httpGet(CAPE_URL);
            if (body == null) return null;

            Gson gson = new Gson();
            JsonObject root = gson.fromJson(body, JsonObject.class);
            if (root == null || !root.has("players")) return null;

            JsonObject players = root.getAsJsonObject("players");
            Map<String, String> result = new HashMap<>();
            for (Map.Entry<String, JsonElement> entry : players.entrySet()) {
                String key = entry.getKey().toLowerCase(Locale.ROOT);
                JsonObject p = entry.getValue().getAsJsonObject();
                if (p != null && p.has("cape")) result.put(key, p.get("cape").getAsString());
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
        httpPostJson(CAPE_POST_URL, json, token, "cape POST");
    }

    public static List<CapeManager.CapeEntry> fetchCapeIndexFromApi() {
        try {
            String body = httpGet(CAPE_INDEX_URL);
            if (body == null) return null;

            Gson gson = new Gson();
            JsonObject root = gson.fromJson(body, JsonObject.class);
            if (root == null || !root.has("capes")) return null;

            JsonArray arr = root.getAsJsonArray("capes");
            List<CapeManager.CapeEntry> result = new ArrayList<>();
            for (int i = 0; i < arr.size(); i++) {
                JsonObject o = arr.get(i).getAsJsonObject();
                if (o == null || !o.has("id") || !o.has("url")) continue;
                result.add(new CapeManager.CapeEntry(o.get("id").getAsString(), o.get("url").getAsString()));
            }
            return result;
        } catch (Exception e) {
            THMAddon.LOG.warn("Error fetching cape index from API: {}", e.getMessage());
            return null;
        }
    }
}
