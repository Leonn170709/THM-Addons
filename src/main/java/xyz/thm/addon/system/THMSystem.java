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
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import xyz.thm.addon.THMAddon;
import xyz.thm.addon.modules.HighwayBuilderTHM;
import xyz.thm.addon.settings.DescribedOption;
import xyz.thm.addon.settings.StringMultiSelect;
import xyz.thm.addon.shaders.ShaderManager;
import xyz.thm.addon.utils.APIUtils;
import xyz.thm.addon.utils.AccountUtils;
import xyz.thm.addon.utils.CapeManager;
import xyz.thm.addon.utils.ThmMembers;
import xyz.thm.addon.utils.TrustedHttp;
import xyz.thm.addon.utils.kitbot.KitbotChatRouter;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

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
    private final SettingGroup sgKitbot = settings.createGroup("KitBot");
    private final SettingGroup sgPrefix = settings.createGroup("API Token");

    public final Settings updaterSettings = new Settings();
    private final SettingGroup sgUpdater = updaterSettings.createGroup("Updater");

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

    public final Setting<Boolean> tabbedHighwayGui = sgGeneral.add(new BoolSetting.Builder()
        .name("tabbed-highway-gui")
        .description("Opens HighwayBuilder in the tabbed control screen instead of Meteor's list.")
        .defaultValue(true)
        .build()
    );

    public enum UpdateChannel implements DescribedOption { STABLE, DEV;

        @Override
        public String description() {
            return switch (this) {
                case STABLE -> "Use stable addon releases.";
                case DEV -> "Use development builds.";
            };
        }
    }

    public final Setting<UpdateChannel> updateChannel = sgUpdater.add(new EnumSetting.Builder<UpdateChannel>()
        .name("update-channel")
        .description("Choose stable releases or development builds.")
        .defaultValue(UpdateChannel.STABLE)
        .build()
    );

    public final Setting<Boolean> autoUpdate = sgUpdater.add(new BoolSetting.Builder()
        .name("auto-update")
        .description("Install updates on startup. Restart to apply.")
        .defaultValue(false)
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
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.player == null) return;
            if (!ThmMembers.isThmMember(mc.player)) return;
            if (!hasApiToken()) return;
            APIUtils.postCapeSelection(id);
        })
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
        .visible(AccountUtils::isCurrentAccountCracked)
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

    public final Setting<Integer> shaderResolution = sgMainMenu.add(new IntSetting.Builder()
        .name("shader-resolution")
        .description("Background render resolution as a percentage.")
        .defaultValue(25)
        .min(25).max(100).sliderRange(25, 100)
        .build()
    );

    public final Setting<Integer> shaderFps = sgMainMenu.add(new IntSetting.Builder()
        .name("shader-fps")
        .description("Background animation FPS. 0 = unlimited.")
        .defaultValue(30)
        .min(0).max(120).sliderRange(0, 120)
        .build()
    );

    public final Setting<Integer> mainMenuBlur = sgMainMenu.add(new IntSetting.Builder()
        .name("blur")
        .description("Blurs the title screen shader background. 0 = no blur, 100 = full blur.")
        .defaultValue(0)
        .min(0).max(100).sliderRange(0, 100)
        .build()
    );

    private final EnumMap<Mode, CompoundTag> highwayProfileSnapshots = new EnumMap<>(Mode.class);
    private Mode activeHighwayProfile = Mode.None;

    public THMSystem() {
        super("THM-Addon");
        KitbotChatRouter.setEnabled(kitbotChatRouterEnabled.get());
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
        return TrustedHttp.isValidApiToken(getApiToken());
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
    public CompoundTag toTag() {
        captureActiveHighwayProfileSnapshotIfPossible();

        CompoundTag tag = new CompoundTag();
        tag.putString("version", THMAddon.VERSION);
        tag.put("settings", settings.toTag());
        tag.put("updaterSettings", updaterSettings.toTag());
        tag.put("mainMenuSettings", mainMenuSettings.toTag());
        tag.putString(ACTIVE_HIGHWAY_PROFILE_TAG, (activeHighwayProfile == null ? Mode.None : activeHighwayProfile).name());
        tag.put(HIGHWAY_PROFILE_SNAPSHOTS_TAG, highwayProfileSnapshotsToTag());
        return tag;
    }

    @Override
    public THMSystem fromTag(CompoundTag tag) {
        if (tag.contains("settings")) {
            settings.fromTag(tag.getCompound("settings").orElse(new CompoundTag()));
        }
        // Older configs stored the Updater group in the main tab settings.
        updaterSettings.fromTag(tag.getCompound(tag.contains("updaterSettings") ? "updaterSettings" : "settings")
            .orElse(new CompoundTag()));
        if (tag.contains("mainMenuSettings")) {
            mainMenuSettings.fromTag(tag.getCompound("mainMenuSettings").orElse(new CompoundTag()));
        }
        activeHighwayProfile = readHighwayProfileMode(tag, ACTIVE_HIGHWAY_PROFILE_TAG, Mode.None);
        highwayProfileSnapshots.clear();
        if (tag.contains(HIGHWAY_PROFILE_SNAPSHOTS_TAG)) {
            CompoundTag profilesTag = tag.getCompound(HIGHWAY_PROFILE_SNAPSHOTS_TAG).orElse(new CompoundTag());
            for (Mode profile : Mode.values()) {
                if (profilesTag.contains(profile.name())) {
                    highwayProfileSnapshots.put(profile, copyTag(profilesTag.getCompound(profile.name()).orElse(new CompoundTag())));
                }
            }
        }
        KitbotChatRouter.setEnabled(kitbotChatRouterEnabled.get());
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
        CompoundTag snapshot = highwayProfileSnapshots.get(profile);
        if (snapshot == null) {
            hwBuilder.applyThmProfileSeed(profile);
            saveHighwayProfileSnapshot(hwBuilder, profile);
            return;
        }

        hwBuilder.settings.fromTag(copyTag(snapshot));
        hwBuilder.normalizeAfterThmProfileLoad();
    }

    private CompoundTag highwayProfileSnapshotsToTag() {
        CompoundTag tag = new CompoundTag();
        for (Mode profile : Mode.values()) {
            CompoundTag snapshot = highwayProfileSnapshots.get(profile);
            if (snapshot != null) tag.put(profile.name(), copyTag(snapshot));
        }
        return tag;
    }

    private static Mode readHighwayProfileMode(CompoundTag tag, String key, Mode fallback) {
        if (tag == null || !tag.contains(key)) return fallback;
        return parseMode(tag.getStringOr(key, fallback.name()), fallback);
    }

    private static Mode parseMode(String value, Mode fallback) {
        if (value == null || value.isBlank()) return fallback;
        try {
            return Mode.valueOf(value);
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    private static CompoundTag copyTag(CompoundTag tag) {
        return tag == null ? new CompoundTag() : tag.copy();
    }

    public enum Mode implements DescribedOption {
        None,
        HighwayBuilding,
        HighwayDigging;

        @Override
        public String description() {
            return switch (this) {
                case None -> "Keep your current highway settings.";
                case HighwayBuilding -> "Apply the highway-building profile.";
                case HighwayDigging -> "Apply the highway-digging profile.";
            };
        }
    }
    public enum Type implements DescribedOption {
        Obby,
        TransparentWhite,
        TransparentBlack;

        @Override
        public String description() {
            return switch (this) {
                case Obby -> "Use the obsidian icon.";
                case TransparentWhite -> "Use the transparent white icon.";
                case TransparentBlack -> "Use the transparent black icon.";
            };
        }
    }

}
