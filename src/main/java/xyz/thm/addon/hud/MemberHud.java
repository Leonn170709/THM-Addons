package xyz.thm.addon.hud;

import com.google.common.util.concurrent.AtomicDouble;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import xyz.thm.addon.THMAddon;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

import static meteordevelopment.meteorclient.MeteorClient.mc;
import static xyz.thm.addon.utils.password.*;

public class MemberHud extends HudElement {
    public static final HudElementInfo<MemberHud> INFO = new HudElementInfo<>(THMAddon.HUD_GROUP, "THM Member Hud", "Shows all online THM members and ranks", MemberHud::new);

    public MemberHud() {
        super(INFO);
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgColors = settings.createGroup("Colors");

    public final Setting<Boolean> showSelf = sgGeneral.add(new BoolSetting.Builder()
        .name("show-self")
        .description("Whether to show yourself in the list.")
        .defaultValue(false)
        .build()
    );

    public final Setting<Boolean> showBots = sgGeneral.add(new BoolSetting.Builder()
        .name("show-bots")
        .description("Whether to show Kitbot in the list.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Boolean> showBackground = sgGeneral.add(new BoolSetting.Builder()
        .name("show-background")
        .description("Whether to show a background behind the member list.")
        .defaultValue(false)
        .build()
    );

    // Color settings for text display
    public final Setting<SettingColor> colorHeader = sgColors.add(new ColorSetting.Builder()
        .name("header-color")
        .description("Color of the header text (Online THM Members:)")
        .defaultValue(new SettingColor(255, 0, 0, 255))
        .build()
    );

    public final Setting<SettingColor> colorMemberInfo = sgColors.add(new ColorSetting.Builder()
        .name("member-info-color")
        .description("Color of member name and player name")
        .defaultValue(new SettingColor(255, 255, 255, 255))
        .build()
    );

    public final Setting<SettingColor> colorBackground = sgColors.add(new ColorSetting.Builder()
        .name("background-color")
        .description("Color of the background")
        .defaultValue(new SettingColor(0, 0, 0, 150))
        .visible(showBackground::get)
        .build()
    );

    // Inner class to represent a member
    private static class User {
        String name;
        String[] mcNames;
        String rank;
        String rankId;

        public User(String name, String[] mcNames, String rank, String rankId) {
            this.name = name;
            this.mcNames = mcNames;
            this.rank = rank;
            this.rankId = rankId;
        }
    }

    public String apiUrl = getAPIMemberHud();

    // Cache variables
    private List<User> cachedMembers = null;
    private long lastCacheTime = 0;
    private static final long CACHE_DURATION = 30 * 60 * 1000; // 30 minutes in milliseconds

    private String decryptAPI(String encryptedapi, String password) {
        try {
            // Derive a 256-bit (32 byte) key from the password using SHA-256
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] keyBytes = digest.digest(password.getBytes(StandardCharsets.UTF_8));

            // Add proper Base64 padding for encrypted data if needed
            String padded = encryptedapi;
            int padding = padded.length() % 4;
            if (padding > 0) {
                padded += "=".repeat(4 - padding);
            }

            byte[] encryptedBytes = Base64.getDecoder().decode(padded);

            // Create AES-256 cipher
            SecretKeySpec secretKey = new SecretKeySpec(keyBytes, 0, keyBytes.length, "AES");
            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.DECRYPT_MODE, secretKey);

            byte[] decrypted = cipher.doFinal(encryptedBytes);
            String result = new String(decrypted, StandardCharsets.UTF_8);

            // Validate result looks like a URL
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

    private List<User> fetchMembersFromApi() {
        List<User> members = new ArrayList<>();

        try {
            // Create HTTP connection to the API endpoint
            HttpURLConnection connection = (HttpURLConnection) new URI(Objects.requireNonNull(decryptAPI(apiUrl, getPassword()))).toURL().openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);

            // Check if the response is successful
            if (connection.getResponseCode() != 200) {
                THMAddon.LOG.error("Failed to fetch members from API. Response code: {}", connection.getResponseCode());
                return members;
            }
            THMAddon.LOG.info("Fetched Members");

            // Read the response body
            StringBuilder response = new StringBuilder();
            try (Scanner scanner = new Scanner(connection.getInputStream())) {
                while (scanner.hasNextLine()) {
                    response.append(scanner.nextLine());
                }
            }

            // Parse JSON response
            Gson gson = new Gson();
            JsonArray jsonArray = gson.fromJson(response.toString(), JsonArray.class);

            // Convert JSON to User objects
            for (int i = 0; i < jsonArray.size(); i++) {
                JsonObject jsonObject = jsonArray.get(i).getAsJsonObject();

                // Extract usernames array
                JsonArray usernamesArray = jsonObject.getAsJsonArray("usernames");
                String[] usernames = new String[usernamesArray.size()];
                for (int j = 0; j < usernamesArray.size(); j++) {
                    usernames[j] = usernamesArray.get(j).getAsString();
                }

                // Extract rank and rankId
                String rank = jsonObject.get("rank").getAsString();
                String rankId = jsonObject.has("rankId") ? jsonObject.getAsJsonPrimitive("rankId").getAsString() : "";

                // Create User object with first username as the display name
                String displayName = usernames.length > 0 ? usernames[0] : "Unknown";
                members.add(new User(displayName, usernames, rank, rankId));
            }

            connection.disconnect();
        } catch (Exception e) {
            System.err.println("Error fetching members from API: " + e.getMessage());
            e.printStackTrace();
        }

        return members;
    }

    // Get cached members or fetch from API if cache expired
    private List<User> getCachedMembers() {
        long currentTime = System.currentTimeMillis();

        // Check if cache is still valid
        if (cachedMembers != null && (currentTime - lastCacheTime) < CACHE_DURATION) {
            return cachedMembers;
        }

        // Fetch new data from API and update cache
        cachedMembers = fetchMembersFromApi();
        lastCacheTime = currentTime;
        return cachedMembers;
    }

    // Reset cache on world join
    public void onWorldJoin() {
        cachedMembers = null;
        lastCacheTime = 0;
    }

    // Parse hex string with optional '#' prefix
    private Color getColorForRank(String rankName) {
        return switch (rankName) {
            case "King" -> new Color(255, 217, 94, 255); // Orange
            case "Prince" -> new Color(218, 160, 52, 255); // Deep Pink
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

    @Override
    public void render(HudRenderer renderer) {
        if (mc.player == null) return;

        // Get cached members (API is only called if cache expired)
        List<User> thmMembers = getCachedMembers();

        // Get all online players from tab list
        List<String> onlinePlayers = new ArrayList<>(mc.player.networkHandler.getPlayerList().stream()
            .map(playerInfo -> playerInfo.getProfile().name()).toList());

        // Define rank hierarchy
        List<String> rankHierarchy = Arrays.asList(
            "King",
            "Prince",
            "The Chosen One",
            "Major",
            "Mayor",
            "Elite Highway Man",
            "Journeyman",
            "Highway Man",
            "PvP Manager",
            "PvP Lead",
            "PvP Branch",
            "Apprentice",
            "Novice",
            "Bot"
        );

        // Create a map of player to member for easier lookup
        Map<String, User> playerMemberMap = new HashMap<>();
        onlinePlayers.forEach(player -> {
            thmMembers.stream()
                .filter(member -> member.mcNames.length > 0 && Arrays.asList(member.mcNames).contains(player))
                .findFirst()
                .ifPresent(member -> playerMemberMap.put(player, member));
        });

        // Sort players by rank hierarchy
        List<String> sortedPlayers = onlinePlayers.stream()
            .filter(playerMemberMap::containsKey)
            .sorted((player1, player2) -> {
                User member1 = playerMemberMap.get(player1);
                User member2 = playerMemberMap.get(player2);

                int rank1Index = rankHierarchy.indexOf(member1.rank);
                int rank2Index = rankHierarchy.indexOf(member2.rank);

                if (rank1Index == -1) rank1Index = rankHierarchy.size();
                if (rank2Index == -1) rank2Index = rankHierarchy.size();

                return Integer.compare(rank1Index, rank2Index);
            })
            .toList();

        AtomicDouble screenY = new AtomicDouble(y + 4);

        // Render header with configurable color
        renderer.text("Online THM Members: ", x, screenY.get(), colorHeader.get(), true);
        screenY.addAndGet(renderer.textHeight(true) + 1);
        double storedY = screenY.get();
        screenY.addAndGet(5);

        AtomicDouble largestWidth = new AtomicDouble(renderer.textWidth("Online THM Members: ", true));

        sortedPlayers.forEach(player -> {
            User member = playerMemberMap.get(player);

            if (!showBots.get() && member.rank.equals("Bot")) {
                return;
            }

            if (!showSelf.get() && player.equals(mc.player.getName().getString())) {
                return;
            }

            // Get the color for this rank
            Color rankColor = getColorForRank(member.rank);

            // Build complete display text for width calculation
            String displayText = String.format("[%s] %s", member.rank, player);

            // Render opening bracket
            renderer.text("[", x, screenY.get(), colorMemberInfo.get(), true);
            double xOffset = x + renderer.textWidth("[", true);

            // Render rank with rank-specific color
            renderer.text(member.rank, xOffset, screenY.get(), rankColor, true);
            xOffset += renderer.textWidth(member.rank, true);

            // Render closing bracket and player name
            renderer.text(String.format("] %s", player), xOffset, screenY.get(), colorMemberInfo.get(), true);

            // Calculate total width for background
            double totalWidth = renderer.textWidth(displayText, true);
            if (totalWidth > largestWidth.get()) {
                largestWidth.set(totalWidth);
            }

            screenY.addAndGet(renderer.textHeight(true) + 2);
        });

        // Render background if enabled
        if (showBackground.get()) {
            renderer.quad(x - 2, y, largestWidth.get() + 4, screenY.get() - y + 2, colorBackground.get());
        }

        setSize(largestWidth.get() + 4, screenY.get() - y + 4);
    }
}
