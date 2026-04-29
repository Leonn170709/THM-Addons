package xyz.thm.addon.hud;

import com.google.common.util.concurrent.AtomicDouble;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringListSetting;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import xyz.thm.addon.THMAddon;
import xyz.thm.addon.system.THMSystem;
import xyz.thm.addon.utils.ThmMembers;

import java.util.*;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class MemberHud extends HudElement {
    public static final HudElementInfo<MemberHud> INFO = new HudElementInfo<>(THMAddon.HUD_GROUP, "THM Member Hud", "Shows all online THM members and ranks", MemberHud::new);
    private static final List<String> RANK_HIERARCHY = Arrays.asList(
        "King/Owner",
        "Prince/Co-Owner",
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
        "Retired",
        "Novice",
        "Bot"
    );

    public MemberHud() {
        super(INFO);
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgColors = settings.createGroup("Colors");
    private final SettingGroup sgFilters = settings.createGroup("Filters");

    public final Setting<Boolean> showSelf = sgGeneral.add(new BoolSetting.Builder()
        .name("show-self")
        .description("Whether to show yourself in the list.")
        .defaultValue(false)
        .build()
    );

    public final Setting<Boolean> showBots = sgGeneral.add(new BoolSetting.Builder()
        .name("show-bots")
        .description("Whether to show bots in the list.")
        .defaultValue(true)
        .build()
    );

    public final Setting<List<String>> visibleRoles = sgFilters.add(new StringListSetting.Builder()
        .name("visible-roles")
        .description("Roles to display in the member HUD.")
        .defaultValue(RANK_HIERARCHY)
        .build()
    );

    public final Setting<Boolean> showUnknownRoles = sgFilters.add(new BoolSetting.Builder()
        .name("show-unknown-roles")
        .description("Whether to show members with roles that don't have their own toggle.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Double> scale = sgGeneral.add(new DoubleSetting.Builder()
        .name("scale")
        .description("Scales the member HUD text.")
        .defaultValue(1.0)
        .min(0.5)
        .sliderMax(2.0)
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

    // Reset cache on world join
    public void onWorldJoin() {
        ThmMembers.resetCache();
    }

    @Override
    public void render(HudRenderer renderer) {
        if (mc.player == null) return;
        double textScale = scale.get();

        // Get all online players from tab list
        List<String> onlinePlayers = new ArrayList<>(mc.player.networkHandler.getPlayerList().stream()
            .map(playerInfo -> playerInfo.getProfile().name()).toList());

        // Create a map of player to member for easier lookup (case-insensitive)
        Map<String, ThmMembers.Member> playerMemberMap = new HashMap<>();
        onlinePlayers.forEach(player -> {
            ThmMembers.Member member = ThmMembers.getMemberByMcName(player);
            if (member != null) playerMemberMap.put(player, member);
        });

        // Sort players by rank hierarchy
        List<String> sortedPlayers = onlinePlayers.stream()
            .filter(playerMemberMap::containsKey)
            .sorted((player1, player2) -> {
                ThmMembers.Member member1 = playerMemberMap.get(player1);
                ThmMembers.Member member2 = playerMemberMap.get(player2);

                int rank1Index = RANK_HIERARCHY.indexOf(member1.rank);
                int rank2Index = RANK_HIERARCHY.indexOf(member2.rank);

                if (rank1Index == -1) rank1Index = RANK_HIERARCHY.size();
                if (rank2Index == -1) rank2Index = RANK_HIERARCHY.size();

                return Integer.compare(rank1Index, rank2Index);
            })
            .toList();

        AtomicDouble screenY = new AtomicDouble(y + 4 * textScale);

        // Render header with configurable color
        renderer.text("Online THM Members: ", x, screenY.get(), colorHeader.get(), true, textScale);
        screenY.addAndGet((renderer.textHeight(true) + 1) * textScale);
        screenY.addAndGet(5 * textScale);

        AtomicDouble largestWidth = new AtomicDouble(renderer.textWidth("Online THM Members: ", true) * textScale);

        sortedPlayers.forEach(player -> {
            ThmMembers.Member member = playerMemberMap.get(player);

            String branchFilter = THMSystem.get().showBranch.get();
            if (!THMSystem.BRANCH_ALL.equalsIgnoreCase(branchFilter)) {
                if (THMSystem.BRANCH_PVP.equalsIgnoreCase(branchFilter) && !"PvP".equalsIgnoreCase(member.branch)) return;
                if (THMSystem.BRANCH_MAIN.equalsIgnoreCase(branchFilter) && !"Main".equalsIgnoreCase(member.branch)) return;
            }

            if (!showBots.get() && member.rank.equals("Bot")) {
                return;
            }
            if (!isRoleVisible(member.rank)) return;

            if (ThmMembers.isKillOnSight(member)) {
                return;
            }
            if (ThmMembers.isIgnore(member)) {
                return;
            }

            if (!showSelf.get() && player.equals(mc.player.getName().getString())) {
                return;
            }

            // Get the color for this rank
            Color rankColor = ThmMembers.getRankColor(member.rank);

            // Build complete display text for width calculation
            String displayText = String.format("[%s] %s", member.rank, player);

            // Render opening bracket
            renderer.text("[", x, screenY.get(), colorMemberInfo.get(), true, textScale);
            double xOffset = x + renderer.textWidth("[", true) * textScale;

            // Render rank with rank-specific color
            renderer.text(member.rank, xOffset, screenY.get(), rankColor, true, textScale);
            xOffset += renderer.textWidth(member.rank, true) * textScale;

            // Render closing bracket and player name
            renderer.text(String.format("] %s", player), xOffset, screenY.get(), colorMemberInfo.get(), true, textScale);

            // Calculate total width for background
            double totalWidth = renderer.textWidth(displayText, true) * textScale;
            if (totalWidth > largestWidth.get()) {
                largestWidth.set(totalWidth);
            }

            screenY.addAndGet((renderer.textHeight(true) + 2) * textScale);
        });

        // Render background if enabled
        if (showBackground.get()) {
            renderer.quad(x - 2, y, largestWidth.get() + 4, screenY.get() - y + 2, colorBackground.get());
        }

        setSize(largestWidth.get() + 4, screenY.get() - y + 4);
    }

    private boolean isRoleVisible(String rank) {
        if (rank == null) return showUnknownRoles.get();

        for (String selectedRole : visibleRoles.get()) {
            if (rank.equalsIgnoreCase(selectedRole)) return true;
        }

        boolean isKnownRole = RANK_HIERARCHY.stream().anyMatch(known -> known.equalsIgnoreCase(rank));
        return !isKnownRole && showUnknownRoles.get();
    }
}
