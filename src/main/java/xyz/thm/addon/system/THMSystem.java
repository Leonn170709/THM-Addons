/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.system;

import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.System;
import meteordevelopment.meteorclient.systems.Systems;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.nbt.NbtCompound;
import xyz.thm.addon.THMAddon;
import xyz.thm.addon.modules.HighwayBuilderTHM;
import xyz.thm.addon.settings.StringMultiSelect;
import xyz.thm.addon.shaders.ShaderManager;
import xyz.thm.addon.utils.APIUtils;
import xyz.thm.addon.utils.CapeManager;
import xyz.thm.addon.utils.Homes;
import xyz.thm.addon.utils.kitbot.KitbotChatRouter;
import xyz.thm.addon.utils.ThmMembers;
import xyz.thm.addon.waveycapes.CapeStyle;
import xyz.thm.addon.waveycapes.WaveyCapesConfig;
import xyz.thm.addon.waveycapes.WindMode;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.UUID;

public class THMSystem extends System<THMSystem> {
    private static final String HIGHWAY_PROFILE_SNAPSHOTS_TAG = "highwayProfileSnapshots";
    private static final String ACTIVE_HIGHWAY_PROFILE_TAG = "activeHighwayProfile";

    public final Settings settings = new Settings();

    // Group creation order = display order in the THM tab. Appearance first (brand colors), API
    // Token last (rarely touched).
    // Display order in the THM tab is the createGroup call order: the things you touch most first,
    // the one-time setup (token) last.
    private final SettingGroup sgGeneral = settings.createGroup("General");
    private final SettingGroup sgAppearance = settings.createGroup("Appearance");
    private final SettingGroup sgRender = settings.createGroup("THM Rendering");
    private final SettingGroup sgPvp = settings.createGroup("PVP");
    private final SettingGroup sgProfiles = settings.createGroup("Highway Profiles");
    private final SettingGroup sgHomes = settings.createGroup("Homes");
    private final SettingGroup sgKitbot = settings.createGroup("KitBot");
    private final SettingGroup sgPrefix = settings.createGroup("API Token");

    // Separate settings object for the Wavy Capes screen (not shown in the main THM tab)
    public final Settings wavyCapesSettings = new Settings();
    private final SettingGroup sgWavyCapes = wavyCapesSettings.createGroup("Wavy Capes");

    // Separate settings object for the Main Menu screen, opened from a button on the title screen
    public final Settings mainMenuSettings = new Settings();
    private final SettingGroup sgMainMenu = mainMenuSettings.createGroup("Main Menu");

    // Appearance Settings - the addon's brand colors. Drive the THM main-menu window and the THM
    // Meteor theme (see MainMenuFx / ThmChrome): the main color is the "line" color (border,
    // title bar, button fills, title text, particles); the side color is the translucent "fill"
    // color (the window body), mirroring how THM's block-ESP renders use a line + side color.
    public final Setting<meteordevelopment.meteorclient.utils.render.color.SettingColor> thmColor = sgAppearance.add(new ColorSetting.Builder()
        .name("thm-color")
        .description("Main THM brand color - borders, title bar, buttons and text of the THM window/theme.")
        .defaultValue(new meteordevelopment.meteorclient.utils.render.color.SettingColor(145, 60, 255, 255))
        .build()
    );

    public final Setting<meteordevelopment.meteorclient.utils.render.color.SettingColor> thmSideColor = sgAppearance.add(new ColorSetting.Builder()
        .name("thm-side-color")
        .description("Translucent THM fill color - the window body of the THM window/theme.")
        .defaultValue(new meteordevelopment.meteorclient.utils.render.color.SettingColor(145, 60, 255, 75))
        .build()
    );

    // General Settings
    public final Setting<Boolean> screenshotToClipboard = sgGeneral.add(new BoolSetting.Builder()
        .name("screenshot-to-clipboard")
        .description("Automatically copies screenshots to the clipboard when taken.")
        .defaultValue(true)
        .build()
    );

    // Highway Profiles Settings
    public final Setting<Mode> mode = sgProfiles.add(new EnumSetting.Builder<Mode>()
        .name("profile")
        .description("Which highway profile to use.")
        .defaultValue(Mode.None)
        .build()
    );

    private final Setting<Boolean> toggleModules = sgProfiles.add(new BoolSetting.Builder()
        .name("toggle-modules")
        .description("Turn on Highwaybuilder when toggled.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Boolean> ignoreThmMembers = sgPvp.add(new BoolSetting.Builder()
        .name("ignore-thm-members")
        .description("Ignore THM members in PvP modules.")
        .defaultValue(true)
        .build()
    );

    public static final String BRANCH_ALL = "All";
    public static final String BRANCH_MAIN = "Main";
    public static final String BRANCH_PVP = "PvP";

    public final Setting<String> showBranch = sgPvp.add(new ProvidedStringSetting.Builder()
        .name("show-branch")
        .description("Which branch members to show in THM member lists.")
        .defaultValue(BRANCH_ALL)
        .supplier(() -> new String[] { BRANCH_ALL, BRANCH_MAIN, BRANCH_PVP })
        .build()
    );

    // THM Rendering Settings - nametags first, then the tab list, then what colors both of them use
    public final Setting<Boolean> highlightNametags = sgRender.add(new BoolSetting.Builder()
        .name("highlight-nametags")
        .description("Highlights THM members in nametags.")
        .defaultValue(false)
        .build()
    );

    public final Setting<Boolean> showNametagIcon = sgRender.add(new BoolSetting.Builder()
        .name("show-nametag-icon")
        .description("Shows the THM icon before member names in nametags.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Type> nametagType = sgRender.add(new EnumSetting.Builder<Type>()
        .name("nametag-icon-type")
        .description("Which nametag icon style to use.")
        .defaultValue(Type.TransparentWhite)
        .visible(showNametagIcon::get)
        .build()
    );

    public final Setting<Boolean> showTotemCounter = sgRender.add(new BoolSetting.Builder()
        .name("show-totem-counter")
        .description("Shows each player's totem of undying pop count in their nametag.")
        .defaultValue(false)
        .build()
    );

    public final Setting<Boolean> highlightInTab = sgRender.add(new BoolSetting.Builder()
        .name("highlight-in-tab")
        .description("Highlights THM members in the player tab list.")
        .defaultValue(false)
        .build()
    );

    public final Setting<Boolean> useRankColor = sgRender.add(new BoolSetting.Builder()
        .name("use-rank-color")
        .description("Use the member's rank color instead of a single highlight color.")
        .defaultValue(true)
        .visible(() -> highlightNametags.get() || highlightInTab.get())
        .build()
    );

    public final Setting<meteordevelopment.meteorclient.utils.render.color.SettingColor> highlightColor = sgRender.add(new ColorSetting.Builder()
        .name("highlight-color")
        .description("Highlight color for THM members.")
        .defaultValue(new meteordevelopment.meteorclient.utils.render.color.SettingColor(255, 217, 94, 255))
        .visible(() -> !useRankColor.get() && (highlightNametags.get() || highlightInTab.get()))
        .build()
    );

    public final Setting<String> cape = sgRender.add(new ProvidedStringSetting.Builder()
        .name("thm-cape")
        .description("Cape shown on yourself and other THM members.")
        .defaultValue("None")
        .supplier(CapeManager::availableCapeIds)
        .onChanged(id -> {
            if (FabricLoader.getInstance().isDevelopmentEnvironment()) return;
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc == null || mc.player == null) return;
            if (!ThmMembers.isThmMember(mc.player)) return;
            String username = mc.player.getGameProfile().name();
            if (!hasApiToken()) return;
            APIUtils.postCapeSelection(username, id, getApiToken());
        })
        .build()
    );

    // Homes Settings - see xyz.thm.addon.utils.Homes
    public final Setting<Boolean> homesGui = sgHomes.add(new BoolSetting.Builder()
        .name("homes-gui")
        .description("Turns the /homes chat list into a clickable screen.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Homes.GuiStyle> homesGuiStyle = sgHomes.add(new EnumSetting.Builder<Homes.GuiStyle>()
        .name("homes-gui-style")
        .description("Which look the homes screen uses.")
        .defaultValue(Homes.GuiStyle.Minecraft)
        .visible(homesGui::get)
        .build()
    );

    public final Setting<Boolean> homesAutoOpen = sgHomes.add(new BoolSetting.Builder()
        .name("homes-auto-open")
        .description("Opens the screen when /homes is answered.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Boolean> kitbotChatRouterEnabled = sgKitbot.add(new BoolSetting.Builder()
        .name("kitbot-chat-router")
        .description("Routes recognized $kitbot chat commands through Kitbot Frontend.")
        .defaultValue(true)
        .onChanged(KitbotChatRouter::setEnabled)
        .build()
    );

    // API Token Settings
    private final Setting<String> apiToken = sgPrefix.add(new StringSetting.Builder()
        .name("api-token")
        .description("Your personal API token (UUID) - get one from the Discord bot's player panel.")
        .defaultValue("")
        .build()
    );

    private final Setting<String> crackedPassword = sgPrefix.add(new StringSetting.Builder()
        .name("cracked-password")
        .description("Password used for cracked-account reconnect /login.")
        .defaultValue("")
        .build()
    );

    public final Setting<Boolean> wavyCapes = sgWavyCapes.add(new BoolSetting.Builder()
        .name("wavy-capes")
        .description("Replaces the vanilla stiff cape with rope physics. Off = vanilla behavior.")
        .defaultValue(false)
        .onChanged(v -> WaveyCapesConfig.syncFromSystem())
        .build()
    );

    public final Setting<CapeStyle> wavyCapeStyle = sgWavyCapes.add(new EnumSetting.Builder<CapeStyle>()
        .name("rendering")
        .description("Blocky: 16 rigid segments. Smooth: interpolated quads between segments.")
        .defaultValue(CapeStyle.SMOOTH)
        .visible(wavyCapes::get)
        .onChanged(v -> WaveyCapesConfig.syncFromSystem())
        .build()
    );

    public final Setting<WindMode> wavyWindMode = sgWavyCapes.add(new EnumSetting.Builder<WindMode>()
        .name("wind")
        .description("None: cape only moves from player motion. Waves: adds a gentle idle sway.")
        .defaultValue(WindMode.NONE)
        .visible(wavyCapes::get)
        .onChanged(v -> WaveyCapesConfig.syncFromSystem())
        .build()
    );

    public final Setting<Double> wavyGravity = sgWavyCapes.add(new DoubleSetting.Builder()
        .name("gravity")
        .description("How hard gravity pulls the cape down. Higher = heavier cape.")
        .defaultValue(25)
        .min(0).max(100).sliderRange(0, 100)
        .visible(wavyCapes::get)
        .onChanged(v -> WaveyCapesConfig.syncFromSystem())
        .build()
    );

    public final Setting<Double> wavyHeightMultiplier = sgWavyCapes.add(new DoubleSetting.Builder()
        .name("vertical-response")
        .description("How much jumping or falling tosses the cape upward.")
        .defaultValue(6)
        .min(0).max(20).sliderRange(0, 20)
        .visible(wavyCapes::get)
        .onChanged(v -> WaveyCapesConfig.syncFromSystem())
        .build()
    );

    public final Setting<Double> wavyStraveMultiplier = sgWavyCapes.add(new DoubleSetting.Builder()
        .name("strafe-response")
        .description("How much strafing flares the cape sideways.")
        .defaultValue(2)
        .min(0).max(10).sliderRange(0, 10)
        .visible(wavyCapes::get)
        .onChanged(v -> WaveyCapesConfig.syncFromSystem())
        .build()
    );

    public final Setting<Double> wavyDamping = sgWavyCapes.add(new DoubleSetting.Builder()
        .name("damping")
        .description("Velocity damping per tick — lower = more floaty.")
        .defaultValue(0.85)
        .min(0).max(1).sliderRange(0, 1)
        .visible(wavyCapes::get)
        .onChanged(v -> WaveyCapesConfig.syncFromSystem())
        .build()
    );

    public final Setting<Integer> wavyStiffness = sgWavyCapes.add(new IntSetting.Builder()
        .name("stiffness")
        .description("Constraint solver iterations — higher = stiffer cape.")
        .defaultValue(3)
        .min(1).max(10).sliderRange(1, 10)
        .visible(wavyCapes::get)
        .onChanged(v -> WaveyCapesConfig.syncFromSystem())
        .build()
    );

    public final Setting<Double> wavyMaxBend = sgWavyCapes.add(new DoubleSetting.Builder()
        .name("max-bend")
        .description("Maximum bend angle between cape segments (degrees).")
        .defaultValue(20)
        .min(1).max(90).sliderRange(1, 90)
        .visible(wavyCapes::get)
        .onChanged(v -> WaveyCapesConfig.syncFromSystem())
        .build()
    );

    public final Setting<Boolean> mainMenuWindow = sgMainMenu.add(new BoolSetting.Builder()
        .name("styled-window")
        .description("Shows the BleachHack-styled window frame on the title screen.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Boolean> mainMenuParticles = sgMainMenu.add(new BoolSetting.Builder()
        .name("particle-trail")
        .description("Shows a mouse particle trail on the title screen.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Boolean> shaderRandom = sgMainMenu.add(new BoolSetting.Builder()
        .name("random-shader")
        .description("Picks a random title screen background shader each time the menu opens.")
        .defaultValue(true)
        .build()
    );

    public final Setting<String> shaderChoice = sgMainMenu.add(new ProvidedStringSetting.Builder()
        .name("shader")
        .description("Which background shader to show when random is off.")
        .defaultValue("None")
        .supplier(() -> {
            List<String> options = new ArrayList<>();
            options.add("None");
            options.addAll(ShaderManager.availableShaders());
            return options.toArray(new String[0]);
        })
        .visible(() -> !shaderRandom.get())
        .build()
    );

    public final Setting<StringMultiSelect> shaderPool = sgMainMenu.add(new GenericSetting.Builder<StringMultiSelect>()
        .name("shader-pool")
        .description("Limits which shaders can be picked when random is on. Empty = allow all.")
        .defaultValue(new StringMultiSelect("Allowed Shaders", ShaderManager::availableShaders))
        .visible(shaderRandom::get)
        .build()
    );

    public final Setting<Integer> mainMenuBlur = sgMainMenu.add(new IntSetting.Builder()
        .name("blur")
        .description("Blurs the title screen shader background. 0 = no blur, 100 = full blur.")
        .defaultValue(0)
        .min(0).max(100).sliderRange(0, 100)
        .build()
    );

    private final EnumMap<Mode, NbtCompound> highwayProfileSnapshots = new EnumMap<>(Mode.class);
    private Mode activeHighwayProfile = Mode.None;

    public THMSystem() {
        super("THM-Addon");
        KitbotChatRouter.setEnabled(kitbotChatRouterEnabled.get());
        WaveyCapesConfig.syncFromSystem();
    }

    public Mode getMode() {
        return mode.get();
    }

    public static THMSystem get() {
        return Systems.get(THMSystem.class);
    }

    public String getApiToken() {
        return apiToken.get().trim();
    }

    /** The API only accepts UUID tokens, so blank/malformed values count as "no token set". */
    public boolean hasApiToken() {
        try {
            UUID.fromString(getApiToken());
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public String getCrackedPassword() {
        return crackedPassword.get();
    }

    public void applyProfile() {
        HighwayBuilderTHM hwBuilder = Modules.get().get(HighwayBuilderTHM.class);
        if (hwBuilder == null) return;

        Mode previousProfile = activeHighwayProfile == null ? Mode.None : activeHighwayProfile;
        Mode targetProfile = mode.get();

        // Park the live settings back on the profile they belong to — but never on the one we are
        // about to apply, since saving then loading the same values is what made pressing Apply on
        // the already-active profile do nothing at all.
        if (previousProfile != targetProfile) saveHighwayProfileSnapshot(hwBuilder, previousProfile);

        // Everything this profile isn't opinionated about comes from its snapshot (or the seed, the
        // first time it's used)...
        loadHighwayProfileSnapshot(hwBuilder, targetProfile);
        // ...and the preset's own settings are then forced on top, every time. That's what the
        // button means: pressing Apply with Building selected must leave you with the Building
        // preset, whatever the profile's snapshot happened to hold.
        hwBuilder.applyThmProfileSeed(targetProfile);
        hwBuilder.normalizeAfterThmProfileLoad();
        activeHighwayProfile = targetProfile;
        saveHighwayProfileSnapshot(hwBuilder, targetProfile);

        if (toggleModules.get() && !hwBuilder.isActive()) {
            hwBuilder.toggle();
        }
    }

    public void restoreProfile() {
        HighwayBuilderTHM hwBuilder = Modules.get().get(HighwayBuilderTHM.class);
        if (hwBuilder == null) return;
        if (toggleModules.get() && hwBuilder.isActive()) {
            hwBuilder.toggle();
        }
    }

    @Override
    public NbtCompound toTag() {
        captureActiveHighwayProfileSnapshotIfPossible();

        NbtCompound tag = new NbtCompound();
        tag.putString("version", THMAddon.VERSION);
        tag.put("settings", settings.toTag());
        tag.put("wavyCapesSettings", wavyCapesSettings.toTag());
        tag.put("mainMenuSettings", mainMenuSettings.toTag());
        tag.putString(ACTIVE_HIGHWAY_PROFILE_TAG, (activeHighwayProfile == null ? Mode.None : activeHighwayProfile).name());
        tag.put(HIGHWAY_PROFILE_SNAPSHOTS_TAG, highwayProfileSnapshotsToTag());
        return tag;
    }

    @Override
    public THMSystem fromTag(NbtCompound tag) {
        if (tag.contains("settings")) {
            settings.fromTag(tag.getCompound("settings").orElse(new NbtCompound()));
        }
        if (tag.contains("wavyCapesSettings")) {
            wavyCapesSettings.fromTag(tag.getCompound("wavyCapesSettings").orElse(new NbtCompound()));
        }
        if (tag.contains("mainMenuSettings")) {
            mainMenuSettings.fromTag(tag.getCompound("mainMenuSettings").orElse(new NbtCompound()));
        }
        activeHighwayProfile = readHighwayProfileMode(tag, ACTIVE_HIGHWAY_PROFILE_TAG, Mode.None);
        highwayProfileSnapshots.clear();
        if (tag.contains(HIGHWAY_PROFILE_SNAPSHOTS_TAG)) {
            NbtCompound profilesTag = tag.getCompound(HIGHWAY_PROFILE_SNAPSHOTS_TAG).orElse(new NbtCompound());
            for (Mode profile : Mode.values()) {
                if (profilesTag.contains(profile.name())) {
                    highwayProfileSnapshots.put(profile, copyTag(profilesTag.getCompound(profile.name()).orElse(new NbtCompound())));
                }
            }
        }
        KitbotChatRouter.setEnabled(kitbotChatRouterEnabled.get());
        WaveyCapesConfig.syncFromSystem();
        return this;
    }

    private void captureActiveHighwayProfileSnapshotIfPossible() {
        try {
            HighwayBuilderTHM hwBuilder = Modules.get().get(HighwayBuilderTHM.class);
            if (hwBuilder != null) saveHighwayProfileSnapshot(hwBuilder, activeHighwayProfile == null ? Mode.None : activeHighwayProfile);
        } catch (Throwable ignored) {
            // Modules may not be available during early system serialization.
        }
    }

    private void saveHighwayProfileSnapshot(HighwayBuilderTHM hwBuilder, Mode profile) {
        if (hwBuilder == null || profile == null) return;
        hwBuilder.normalizeAfterThmProfileLoad();
        highwayProfileSnapshots.put(profile, copyTag(hwBuilder.settings.toTag()));
    }

    private void loadHighwayProfileSnapshot(HighwayBuilderTHM hwBuilder, Mode profile) {
        if (hwBuilder == null || profile == null) return;
        NbtCompound snapshot = highwayProfileSnapshots.get(profile);
        if (snapshot == null) {
            hwBuilder.applyThmProfileSeed(profile);
            saveHighwayProfileSnapshot(hwBuilder, profile);
            return;
        }

        hwBuilder.settings.fromTag(copyTag(snapshot));
        hwBuilder.normalizeAfterThmProfileLoad();
    }

    private NbtCompound highwayProfileSnapshotsToTag() {
        NbtCompound tag = new NbtCompound();
        for (Mode profile : Mode.values()) {
            NbtCompound snapshot = highwayProfileSnapshots.get(profile);
            if (snapshot != null) tag.put(profile.name(), copyTag(snapshot));
        }
        return tag;
    }

    private static Mode readHighwayProfileMode(NbtCompound tag, String key, Mode fallback) {
        if (tag == null || !tag.contains(key)) return fallback;
        return parseMode(tag.getString(key, fallback.name()), fallback);
    }

    private static Mode parseMode(String value, Mode fallback) {
        if (value == null || value.isBlank()) return fallback;
        try {
            return Mode.valueOf(value);
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    private static NbtCompound copyTag(NbtCompound tag) {
        return tag == null ? new NbtCompound() : tag.copy();
    }

    public enum Mode {
        None,
        HighwayBuilding,
        HighwayDigging
    }
    public enum Type {
        Obby,
        TransparentWhite,
        TransparentBlack
    }

}
