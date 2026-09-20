/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.gui;

import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.WindowScreen;
import meteordevelopment.meteorclient.gui.widgets.WKeybind;
import meteordevelopment.meteorclient.gui.widgets.WLabel;
import meteordevelopment.meteorclient.gui.widgets.containers.WHorizontalList;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.containers.WVerticalList;
import meteordevelopment.meteorclient.gui.widgets.input.WTextBox;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.Settings;
import meteordevelopment.meteorclient.systems.modules.Modules;
import xyz.thm.addon.hud.HighwayHud;
import xyz.thm.addon.modules.HighwayBuilderTHM;
import xyz.thm.addon.system.THMSystem;
import xyz.thm.addon.utils.TimeFormat;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/** Tabbed control screen for HighwayBuilder: status, start/stop, profiles, per-area settings and search. */
public class HighwayBuilderScreen extends WindowScreen {
    private static final String BASICS = "Basics", ALL = "All";
    private static final String[] BASIC_SETTINGS = {
        "width", "height", "floor", "railings", "blocks-to-place",
        "blocks-per-tick", "adaptive-mining", "placements-per-tick", "adaptive-placements", "packet-build",
        "check-behind", "pause-on-lag", "use-thm-speed",
        "food-restock", "mine-ender-chests", "save-ender-chests"
    };

    // Kept between openings so you come back to the tab you were using.
    private static String activeTab = BASICS;

    private final HighwayBuilderTHM module;
    private final Map<String, Settings> tabs = new LinkedHashMap<>();
    private WLabel statusLabel, statsLabel, rateLabel;
    private WButton toggleButton;
    private WVerticalList content;
    private String search = "";

    public HighwayBuilderScreen(GuiTheme theme, HighwayBuilderTHM module) {
        super(theme, module.title);
        this.module = module;

        tabs.put(BASICS, view(BASICS, BASIC_SETTINGS));
        for (SettingGroup group : module.settings) tabs.put(group.name, view(group));
        tabs.put(ALL, module.settings);
        if (!tabs.containsKey(activeTab)) activeTab = BASICS;
    }

    /** Views over settings that stay owned by the module, so editing and saving are unchanged. */
    private Settings view(String name, String[] settingNames) {
        Settings view = new Settings();
        SettingGroup group = view.createGroup(name);
        for (String settingName : settingNames) {
            Setting<?> setting = module.settings.get(settingName);
            if (setting != null) group.add(setting);
        }
        return view;
    }

    private Settings view(SettingGroup source) {
        Settings view = new Settings();
        SettingGroup group = view.createGroup(source.name);
        for (Setting<?> setting : source) group.add(setting);
        return view;
    }

    @Override
    public void initWidgets() {
        statusLabel = add(theme.label("")).expandX().widget();
        statsLabel = add(theme.label("")).expandX().widget();
        rateLabel = add(theme.label("")).expandX().widget();
        updateStatus();

        WHorizontalList controls = add(theme.horizontalList()).expandX().widget();
        toggleButton = controls.add(theme.button(module.isActive() ? "Stop builder" : "Start builder")).expandX().widget();
        toggleButton.action = () -> {
            module.toggle();
            toggleButton.set(module.isActive() ? "Stop builder" : "Start builder");
            updateStatus();
        };

        WHorizontalList profiles = add(theme.horizontalList()).expandX().widget();
        profiles.add(theme.label("Profile:"));
        for (THMSystem.Mode mode : THMSystem.Mode.values()) {
            WButton button = profiles.add(theme.button(mode.name())).expandX().widget();
            button.tooltip = THMSystem.get().mode.get() == mode ? "Active profile" : "Apply this profile";
            button.action = () -> {
                THMSystem.get().mode.set(mode);
                THMSystem.get().applyProfile();
                reload();
            };
        }

        add(theme.horizontalSeparator()).expandX();

        WTable tabRow = add(theme.table()).expandX().widget();
        int i = 0;
        for (String name : tabs.keySet()) {
            WButton tab = tabRow.add(theme.button(name)).expandX().widget();
            if (name.equals(activeTab)) tab.tooltip = "Open tab";
            tab.action = () -> {
                activeTab = name;
                reload();
            };
            if (++i % 4 == 0) tabRow.row();
        }

        WHorizontalList searchRow = add(theme.horizontalList()).expandX().widget();
        searchRow.add(theme.label("Search:"));
        WTextBox searchBox = searchRow.add(theme.textBox("", "all settings")).expandX().widget();

        add(theme.horizontalSeparator()).expandX();

        // Only the results are rebuilt while typing, so the search box keeps focus.
        content = add(theme.verticalList()).expandX().widget();
        fillContent("");
        searchBox.action = () -> fillContent(searchBox.get().trim());

        add(theme.horizontalSeparator()).expandX();

        WButton classic = add(theme.button("Classic settings list")).expandX().widget();
        classic.tooltip = "Switch back to Meteor's list; change it again in the THM tab.";
        classic.action = () -> {
            THMSystem.get().tabbedHighwayGui.set(false);
            mc.setScreen(theme.moduleScreen(module));
        };

        WHorizontalList bind = add(theme.horizontalList()).expandX().widget();
        bind.add(theme.label("Bind:"));
        WKeybind keybind = bind.add(theme.keybind(module.keybind)).expandX().widget();
        keybind.actionOnSet = () -> Modules.get().setModuleToBind(module);
    }

    private void fillContent(String search) {
        this.search = search;
        content.clear();

        if (search.isEmpty()) {
            content.add(theme.settings(tabs.get(activeTab), "")).expandX();
            return;
        }

        // Searching looks through every group, but only groups with a hit are listed.
        Settings results = new Settings();
        int hits = 0;
        for (SettingGroup group : module.settings) {
            SettingGroup matches = null;
            for (Setting<?> setting : group) {
                if (!matches(setting, search)) continue;
                if (matches == null) matches = results.createGroup(group.name);
                matches.add(setting);
                hits++;
            }
        }

        if (hits == 0) content.add(theme.label("No setting matches \"" + search + "\".")).expandX();
        else content.add(theme.settings(results, search)).expandX();
    }

    private static boolean matches(Setting<?> setting, String search) {
        return setting.title.toLowerCase(Locale.ROOT).contains(search.toLowerCase(Locale.ROOT));
    }

    @Override
    public void tick() {
        super.tick();
        updateStatus();

        // Settings.tick would rebuild the list from every group, dropping the tab and search.
        boolean visibilityChanged = false;
        for (SettingGroup group : module.settings) {
            for (Setting<?> setting : group) {
                boolean visible = setting.isVisible();
                if (visible != setting.lastWasVisible) visibilityChanged = true;
                setting.lastWasVisible = visible;
            }
        }
        if (visibilityChanged) fillContent(search);
    }

    private void updateStatus() {
        if (statusLabel == null) return;

        statusLabel.set(module.isActive()
            ? String.format(Locale.ROOT, "%s  -  heading %s", module.currentStateName(), module.dir == null ? "?" : module.dir.toString())
            : "Off");
        statsLabel.set(String.format(Locale.ROOT, "placed %d   broken %d   %.1f blocks/s",
            module.blocksPlaced, module.blocksBroken, module.getMeasuredPlacesPerSecond()));
        rateLabel.set(String.format(Locale.ROOT, "mine %.1f/tick   place %.1f/tick   restock %s",
            module.currentMineRate(), module.currentPlaceRate(),
            TimeFormat.eta(HighwayHud.getRestockBlocks(), module.getMeasuredPlacesPerSecond())));
    }
}
