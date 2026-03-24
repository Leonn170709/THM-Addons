package xyz.thm.addon.utils;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import meteordevelopment.meteorclient.utils.render.color.Color;
import xyz.thm.addon.THMAddon;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

import static xyz.thm.addon.utils.password.getAPIMemberHud;
import static xyz.thm.addon.utils.password.getPassword;

public final class ThmMembers {
    public static final class Member {
        public final String name;
        public final String[] mcNames;
        public final String rank;
        public final String rankId;

        public Member(String name, String[] mcNames, String rank, String rankId) {
            this.name = name;
            this.mcNames = mcNames;
            this.rank = rank;
            this.rankId = rankId;
        }
    }

    private static final String API_URL = getAPIMemberHud();

    private static List<Member> cachedMembers = null;
    private static Map<String, Member> cachedByMcName = null;
    private static long lastCacheTime = 0;
    private static final long CACHE_DURATION = 30 * 60 * 1000; // 30 minutes

    private ThmMembers() {
    }

    private static String decryptAPI(String encryptedapi, String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] keyBytes = digest.digest(password.getBytes(StandardCharsets.UTF_8));

            String padded = encryptedapi;
            int padding = padded.length() % 4;
            if (padding > 0) {
                padded += "=".repeat(4 - padding);
            }

            byte[] encryptedBytes = Base64.getDecoder().decode(padded);

            SecretKeySpec secretKey = new SecretKeySpec(keyBytes, 0, keyBytes.length, "AES");
            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.DECRYPT_MODE, secretKey);

            byte[] decrypted = cipher.doFinal(encryptedBytes);
            String result = new String(decrypted, StandardCharsets.UTF_8);

            if (!result.startsWith("http://") && !result.startsWith("https://")) {
                THMAddon.LOG.warn("Decrypted API URL invalid - wrong password or corrupted data");
                return null;
            }

            return result;
        } catch (Exception e) {
            THMAddon.LOG.warn("Failed to decrypt API: " + e.getMessage());
            return null;
        }
    }

    private static List<Member> fetchMembersFromApi() {
        List<Member> members = new ArrayList<>();

        try {
            String apiUrl = decryptAPI(API_URL, getPassword());
            if (apiUrl == null) return members;

            HttpURLConnection connection = (HttpURLConnection) new URI(apiUrl).toURL().openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);

            if (connection.getResponseCode() != 200) {
                THMAddon.LOG.error("Failed to fetch members from API. Response code: {}", connection.getResponseCode());
                return members;
            }
            THMAddon.LOG.info("Fetched Members");

            StringBuilder response = new StringBuilder();
            try (Scanner scanner = new Scanner(connection.getInputStream())) {
                while (scanner.hasNextLine()) {
                    response.append(scanner.nextLine());
                }
            }

            Gson gson = new Gson();
            JsonArray jsonArray = gson.fromJson(response.toString(), JsonArray.class);

            for (int i = 0; i < jsonArray.size(); i++) {
                JsonObject jsonObject = jsonArray.get(i).getAsJsonObject();

                JsonArray usernamesArray = jsonObject.getAsJsonArray("usernames");
                String[] usernames = new String[usernamesArray.size()];
                for (int j = 0; j < usernamesArray.size(); j++) {
                    usernames[j] = usernamesArray.get(j).getAsString();
                }

                String rank = jsonObject.get("rank").getAsString();
                String rankId = jsonObject.has("rankId") ? jsonObject.getAsJsonPrimitive("rankId").getAsString() : "";

                String displayName = usernames.length > 0 ? usernames[0] : "Unknown";
                members.add(new Member(displayName, usernames, rank, rankId));
            }

            connection.disconnect();
        } catch (Exception e) {
            System.err.println("Error fetching members from API: " + e.getMessage());
            e.printStackTrace();
        }

        return members;
    }

    private static void refreshIfNeeded() {
        long currentTime = System.currentTimeMillis();
        if (cachedMembers != null && (currentTime - lastCacheTime) < CACHE_DURATION) return;

        cachedMembers = fetchMembersFromApi();
        cachedByMcName = new HashMap<>();
        for (Member member : cachedMembers) {
            for (String mcName : member.mcNames) {
                cachedByMcName.put(mcName, member);
            }
        }
        lastCacheTime = currentTime;
    }

    public static synchronized List<Member> getCachedMembers() {
        refreshIfNeeded();
        return cachedMembers == null ? Collections.emptyList() : cachedMembers;
    }

    public static synchronized Member getMemberByMcName(String mcName) {
        refreshIfNeeded();
        if (cachedByMcName == null) return null;
        return cachedByMcName.get(mcName);
    }

    public static synchronized boolean isNovice(String mcName) {
        Member member = getMemberByMcName(mcName);
        return member != null && "Novice".equalsIgnoreCase(member.rank);
    }

    public static synchronized void resetCache() {
        cachedMembers = null;
        cachedByMcName = null;
        lastCacheTime = 0;
    }

    public static Color getRankColor(String rankName) {
        return switch (rankName) {
            case "King","King/Owner" -> new Color(255, 217, 94, 255); // Orange
            case "Prince", "Prince/Co-Owner" -> new Color(218, 160, 52, 255); // Deep Pink
            case "The Chosen One" -> new Color(255, 215, 0, 255); // Gold
            case "Major" -> new Color(249, 204, 158, 255); // Tan
            case "Mayor" -> new Color(156, 232, 180, 255); // Light Green
            case "Elite Highway Man" -> new Color(185, 230, 88, 255); // Yellow Green
            case "Journeyman" -> new Color(116, 148, 114, 255); // Sage Green
            case "Highway Man" -> new Color(133, 89, 221, 255); // Purple
            case "PvP Manager" -> new Color(255, 0, 55, 255); // Red
            case "PvP Lead" -> new Color(218, 109, 255, 255); // Magenta
            case "PvP Branch" -> new Color(255, 0, 4, 255); // Bright Red
            case "Apprentice" -> new Color(95, 70, 53, 255); // Brown
            case "Novice" -> new Color(76, 173, 208, 255); // Cyan
            case "Bot" -> new Color(52, 152, 219, 255); // Blue
            default -> new Color(255, 255, 255, 255); // White fallback
        };
    }
}
