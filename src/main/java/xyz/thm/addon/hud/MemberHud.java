/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.hud;

import com.google.common.util.concurrent.AtomicDouble;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import xyz.thm.addon.THMAddon;
import xyz.thm.addon.settings.StringMultiSelect;
import xyz.thm.addon.system.THMSystem;
import xyz.thm.addon.utils.ThmMembers;

import java.util.*;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class MemberHud extends HudElement {
    public static final HudElementInfo<MemberHud> INFO = new HudElementInfo<>(THMAddon.HUD_GROUP, "THM Member Hud", "Shows all online THM members and ranks", MemberHud::new);
    private static final List<String> RANK_HIERARCHY = ThmMembers.RANK_HIERARCHY;

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

    public final Setting<StringMultiSelect> visibleRoles = sgFilters.add(new GenericSetting.Builder<StringMultiSelect>()
        .name("visible-roles")
        .description("Roles to display in the member HUD.")
        .defaultValue(defaultVisibleRoles())
        .build()
    );

    private static StringMultiSelect defaultVisibleRoles() {
        StringMultiSelect select = new StringMultiSelect("Visible Roles", () -> RANK_HIERARCHY);
        select.selected().addAll(RANK_HIERARCHY);
        return select;
    }

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

    public final Setting<Boolean> showHighwayStatus = sgGeneral.add(new BoolSetting.Builder()
        .name("show-highway-status")
        .description("Shows the highway assignment after the player name in brackets.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Boolean> groupByDiscordName = sgGeneral.add(new BoolSetting.Builder()
        .name("group-by-discord-name")
        .description("Groups a member's alts under their Discord name. Bots are never grouped.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Boolean> showAccountCount = sgGeneral.add(new BoolSetting.Builder()
        .name("show-account-count")
        .description("Shows how many of a member's accounts are online, e.g. Discordname x3.")
        .defaultValue(false)
        .visible(groupByDiscordName::get)
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
        // Keep startup-loaded members cached. Only manual refresh should reload member API.
    }

    private record Row(String rank, String label, String suffix) {}

    @Override
    public void render(HudRenderer renderer) {
        if (mc.player == null) return;
        double textScale = scale.get();

        // Get all online players from tab list
        List<String> onlinePlayers = new ArrayList<>(mc.player.connection.getOnlinePlayers().stream()
            .map(playerInfo -> playerInfo.getProfile().name()).toList());

        // Group online usernames by their underlying member (multiple alts share the same Member instance)
        Map<ThmMembers.Member, List<String>> usernamesByMember = new LinkedHashMap<>();
        onlinePlayers.forEach(player -> {
            ThmMembers.Member member = ThmMembers.getMemberByMcName(player);
            if (member != null) usernamesByMember.computeIfAbsent(member, m -> new ArrayList<>()).add(player);
        });

        String selfName = mc.player.getName().getString();
        String branchFilter = THMSystem.get().showBranch.get();
        boolean grouping = groupByDiscordName.get();

        List<Row> rows = new ArrayList<>();
        usernamesByMember.forEach((member, usernames) -> {
            if (!THMSystem.BRANCH_ALL.equalsIgnoreCase(branchFilter)) {
                if (THMSystem.BRANCH_PVP.equalsIgnoreCase(branchFilter) && !"PvP".equalsIgnoreCase(member.branch)) return;
                if (THMSystem.BRANCH_MAIN.equalsIgnoreCase(branchFilter) && !"Main".equalsIgnoreCase(member.branch)) return;
            }

            boolean isBot = "Bot".equals(member.rank);
            if (!showBots.get() && isBot) return;
            if (!isRoleVisible(member.rank)) return;
            if (ThmMembers.isKillOnSight(member)) return;
            if (ThmMembers.isIgnore(member)) return;

            List<String> visibleUsernames = usernames.stream()
                .filter(name -> showSelf.get() || !name.equals(selfName))
                .toList();
            if (visibleUsernames.isEmpty()) return;

            if (isBot || !grouping) {
                for (String username : visibleUsernames) {
                    String status = showHighwayStatus.get() ? ThmMembers.getHighwayStatusByMcName(username) : null;
                    String suffix = status != null && !status.isBlank() ? String.format(" (%s)", status) : "";
                    rows.add(new Row(member.rank, username, suffix));
                }
            } else {
                String label = member.discordName != null && !member.discordName.isBlank() ? member.discordName : member.name;
                if (showAccountCount.get()) label += " x" + visibleUsernames.size();
                String statuses = visibleUsernames.stream()
                    .map(username -> showHighwayStatus.get() ? ThmMembers.getHighwayStatusByMcName(username) : null)
                    .filter(status -> status != null && !status.isBlank())
                    .collect(java.util.stream.Collectors.joining(","));
                String suffix = statuses.isEmpty() ? "" : String.format("(%s)", statuses);
                rows.add(new Row(member.rank, label, suffix));
            }
        });

        // Sort rows by rank hierarchy
        rows.sort(Comparator.comparingInt(row -> {
            int index = RANK_HIERARCHY.indexOf(row.rank());
            return index == -1 ? RANK_HIERARCHY.size() : index;
        }));

        AtomicDouble screenY = new AtomicDouble(y + 4 * textScale);

        // Render header with configurable color
        renderer.text("Online THM Members: ", x, screenY.get(), colorHeader.get(), true, textScale);
        screenY.addAndGet((renderer.textHeight(true) + 1) * textScale);
        screenY.addAndGet(5 * textScale);

        AtomicDouble largestWidth = new AtomicDouble(renderer.textWidth("Online THM Members: ", true) * textScale);

        rows.forEach(row -> {
            // Get the color for this rank
            Color rankColor = ThmMembers.getRankColor(row.rank());

            // Build complete display text for width calculation
            String displayText = String.format("[%s] %s%s", row.rank(), row.label(), row.suffix());

            // Render opening bracket
            renderer.text("[", x, screenY.get(), colorMemberInfo.get(), true, textScale);
            double xOffset = x + renderer.textWidth("[", true) * textScale;

            // Render rank with rank-specific color
            renderer.text(row.rank(), xOffset, screenY.get(), rankColor, true, textScale);
            xOffset += renderer.textWidth(row.rank(), true) * textScale;

            // Render closing bracket and label
            renderer.text(String.format("] %s%s", row.label(), row.suffix()), xOffset, screenY.get(), colorMemberInfo.get(), true, textScale);

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

        for (String selectedRole : visibleRoles.get().selected()) {
            if (rank.equalsIgnoreCase(selectedRole)) return true;
        }

        boolean isKnownRole = RANK_HIERARCHY.stream().anyMatch(known -> known.equalsIgnoreCase(rank));
        return !isKnownRole && showUnknownRoles.get();
    }
}
