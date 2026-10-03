/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.hud;

import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.entity.EntityUtils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.minecraft.world.entity.player.Player;
import xyz.thm.addon.THMAddon;
import xyz.thm.addon.utils.TotemTracker;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class PlayerListHud extends HudElement {
    public static final HudElementInfo<PlayerListHud> INFO = new HudElementInfo<>(
        THMAddon.HUD_GROUP, "player-list-hud", "Lists players in render distance, with configurable ping/distance/hp/armor/totem columns.", PlayerListHud::new
    );

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgColumns = settings.createGroup("Columns");
    private final SettingGroup sgColors = settings.createGroup("Colors");

    private final Setting<Boolean> showSelf = sgGeneral.add(new BoolSetting.Builder()
        .name("show-self")
        .description("Whether to show yourself in the list.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Double> scale = sgGeneral.add(new DoubleSetting.Builder()
        .name("scale")
        .description("Scales the list text.")
        .defaultValue(1.0)
        .min(0.5)
        .sliderMax(2.0)
        .build()
    );

    private final Setting<Boolean> showBackground = sgGeneral.add(new BoolSetting.Builder()
        .name("show-background")
        .description("Whether to show a background behind the list.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> showPing = sgColumns.add(new BoolSetting.Builder()
        .name("show-ping")
        .description("Shows each player's ping.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> showDistance = sgColumns.add(new BoolSetting.Builder()
        .name("show-distance")
        .description("Shows each player's distance from you.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> showHp = sgColumns.add(new BoolSetting.Builder()
        .name("show-hp")
        .description("Shows each player's health.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> showArmor = sgColumns.add(new BoolSetting.Builder()
        .name("show-armor")
        .description("Shows each player's armor points.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> showTotemPops = sgColumns.add(new BoolSetting.Builder()
        .name("show-totem-pops")
        .description("Shows each player's totem of undying pop count.")
        .defaultValue(false)
        .build()
    );

    private final Setting<SettingColor> nameColor = sgColors.add(new ColorSetting.Builder()
        .name("name-color")
        .description("Color of player names.")
        .defaultValue(new SettingColor(255, 255, 255, 255))
        .build()
    );

    private final Setting<SettingColor> statColor = sgColors.add(new ColorSetting.Builder()
        .name("stat-color")
        .description("Color of the stat columns after the name.")
        .defaultValue(new SettingColor(180, 180, 180, 255))
        .build()
    );

    private final Setting<SettingColor> backgroundColor = sgColors.add(new ColorSetting.Builder()
        .name("background-color")
        .description("Color of the background.")
        .defaultValue(new SettingColor(0, 0, 0, 150))
        .visible(showBackground::get)
        .build()
    );

    public PlayerListHud() {
        super(INFO);
    }

    @Override
    public void render(HudRenderer renderer) {
        if (mc.player == null || mc.level == null) return;
        double textScale = scale.get();

        List<Player> players = new ArrayList<>();
        for (Player player : mc.level.players()) {
            if (!showSelf.get() && player == mc.player) continue;
            if (!EntityUtils.isInRenderDistance(player)) continue;
            players.add(player);
        }
        players.sort(Comparator.comparingDouble(PlayerUtils::distanceToCamera));

        double screenY = y;
        double largestWidth = 0;

        for (Player player : players) {
            String name = player.getName().getString();
            String stats = buildStatsText(player);

            renderer.text(name, x, screenY, nameColor.get(), true, textScale);
            if (!stats.isEmpty()) {
                double statsX = x + renderer.textWidth(name, true) * textScale;
                renderer.text(stats, statsX, screenY, statColor.get(), true, textScale);
            }

            double rowWidth = (renderer.textWidth(name, true) + renderer.textWidth(stats, true)) * textScale;
            largestWidth = Math.max(largestWidth, rowWidth);

            screenY += (renderer.textHeight(true) + 2) * textScale;
        }

        if (showBackground.get() && !players.isEmpty()) {
            renderer.quad(x - 2, y - 2, largestWidth + 4, screenY - y + 2, backgroundColor.get());
        }

        setSize(largestWidth + 4, Math.max(screenY - y, 0) + 4);
    }

    private String buildStatsText(Player player) {
        StringBuilder stats = new StringBuilder();

        if (showPing.get()) stats.append(" [").append(EntityUtils.getPing(player)).append("ms]");
        if (showDistance.get()) {
            double dist = Math.round(PlayerUtils.distanceToCamera(player) * 10.0) / 10.0;
            stats.append(' ').append(dist).append('m');
        }
        if (showHp.get()) {
            int hp = Math.round(player.getHealth() + player.getAbsorptionAmount());
            stats.append(" hp:").append(hp);
        }
        if (showArmor.get()) stats.append(" armor:").append(player.getArmorValue());
        if (showTotemPops.get()) stats.append(" totems:").append(TotemTracker.get(player));

        return stats.toString();
    }
}
