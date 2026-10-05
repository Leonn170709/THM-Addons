/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.gui;

import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.WindowScreen;
import meteordevelopment.meteorclient.gui.widgets.WLabel;
import meteordevelopment.meteorclient.gui.widgets.containers.WHorizontalList;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.containers.WVerticalList;
import meteordevelopment.meteorclient.gui.widgets.input.WDropdown;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.gui.widgets.pressable.WCheckbox;
import net.minecraft.client.Minecraft;
import xyz.thm.addon.THMAddon;
import xyz.thm.addon.system.THMSystem;
import xyz.thm.addon.utils.AddonUpdater;

import java.util.List;

public class UpdaterScreen extends WindowScreen {
    private WLabel status;
    private WVerticalList actions;
    private List<AddonUpdater.Entry> shownEntries;
    private boolean shownQuit;

    public UpdaterScreen(GuiTheme theme) {
        super(theme, "THM Addon Updater");
    }

    @Override
    public void initWidgets() {
        THMSystem system = THMSystem.get();
        add(theme.label("Installed: " + THMAddon.VERSION)).expandX();
        add(theme.label("Minecraft branch: " + AddonUpdater.branch())).expandX();
        add(theme.horizontalSeparator("Update preferences")).expandX();

        WTable preferences = add(theme.table()).expandX().widget();
        preferences.add(theme.label("Update channel"));
        WDropdown<String> channel = preferences.add(theme.dropdown(new String[] { "Stable", "Dev" },
            system.updateChannel.get() == THMSystem.UpdateChannel.STABLE ? "Stable" : "Dev")).expandX().widget();
        channel.action = () -> {
            system.updateChannel.set(channel.get().equals("Stable")
                ? THMSystem.UpdateChannel.STABLE : THMSystem.UpdateChannel.DEV);
            system.save();
            updateContent();
            AddonUpdater.refresh(null);
        };
        preferences.row();
        preferences.add(theme.label("Auto update on startup"));
        WCheckbox auto = preferences.add(theme.checkbox(system.autoUpdate.get())).widget();
        auto.action = () -> {
            system.autoUpdate.set(auto.checked);
            system.save();
            updateContent();
        };

        add(theme.horizontalSeparator("Update status")).expandX();
        status = add(theme.label(AddonUpdater.status(), 480)).expandX().widget();
        actions = add(theme.verticalList()).expandX().widget();
        WButton history = add(theme.button("Browse history")).expandX().widget();
        history.action = () -> Minecraft.getInstance().gui.setScreen(new HistoryScreen(theme));
        updateContent();
    }

    private void updateContent() {
        THMSystem system = THMSystem.get();
        shownEntries = AddonUpdater.entries();
        shownQuit = AddonUpdater.restartRequired();
        actions.clear();
        WHorizontalList buttons = actions.add(theme.horizontalList()).expandX().widget();
        WButton refresh = buttons.add(theme.button("Check for updates")).expandX().widget();
        refresh.action = () -> AddonUpdater.refresh(null);
        AddonUpdater.Entry latest = shownEntries.stream()
            .filter(e -> e.channel() == system.updateChannel.get()).findFirst().orElse(null);
        if (shownQuit) {
            WButton quit = buttons.add(theme.button("Quit Minecraft")).expandX().widget();
            quit.action = () -> Minecraft.getInstance().stop();
        } else if (!system.autoUpdate.get() && latest != null) {
            if (AddonUpdater.isInstalled(latest)) buttons.add(theme.label("Installed"));
            else {
                WButton manual = buttons.add(theme.button("Update now")).expandX().widget();
                manual.action = () -> AddonUpdater.install(latest, null);
                manual.tooltip = "Install " + latest.title();
            }
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (shownQuit != AddonUpdater.restartRequired() || shownEntries != AddonUpdater.entries()) updateContent();
        status.set(AddonUpdater.status());
    }

    private static class HistoryScreen extends WindowScreen {
        private WLabel status;
        private List<AddonUpdater.Entry> shownEntries;
        private boolean shownQuit;

        HistoryScreen(GuiTheme theme) {
            super(theme, "THM Update History");
        }

        @Override
        public void initWidgets() {
            THMSystem.UpdateChannel channel = THMSystem.get().updateChannel.get();
            shownEntries = AddonUpdater.entries();
            shownQuit = AddonUpdater.restartRequired();
            add(theme.label((channel == THMSystem.UpdateChannel.STABLE ? "Stable releases" : "Dev commits")
                + " / " + AddonUpdater.branch())).expandX();
            status = add(theme.label(AddonUpdater.status(), 480)).expandX().widget();
            WButton refresh = add(theme.button("Check for updates")).expandX().widget();
            refresh.action = () -> AddonUpdater.refresh(null);
            add(theme.horizontalSeparator()).expandX();

            WTable builds = add(theme.table()).expandX().widget();
            boolean found = false;
            for (AddonUpdater.Entry entry : shownEntries) {
                if (entry.channel() != channel) continue;
                found = true;
                builds.add(theme.label(entry.title(), 380)).expandX();
                if (AddonUpdater.isInstalled(entry)) builds.add(theme.label("Installed"));
                else {
                    WButton install = builds.add(theme.button("Install")).widget();
                    install.action = () -> AddonUpdater.install(entry,
                        () -> { if (Minecraft.getInstance().gui.screen() == this) reload(); });
                }
                WButton browse = builds.add(theme.button("Browse")).widget();
                browse.action = () -> Minecraft.getInstance().gui.setScreen(new BuildScreen(theme, entry));
                builds.row();
            }
            if (!found) builds.add(theme.label("No builds available. Check for updates to load history."));
            add(theme.horizontalSeparator()).expandX();
            if (shownQuit) {
                WButton quit = add(theme.button("Quit Minecraft")).expandX().widget();
                quit.action = () -> Minecraft.getInstance().stop();
            }
            WButton back = add(theme.button("Back to updater")).expandX().widget();
            back.action = () -> Minecraft.getInstance().gui.setScreen(new UpdaterScreen(theme));
        }

        @Override
        public void tick() {
            super.tick();
            if (shownQuit != AddonUpdater.restartRequired() || shownEntries != AddonUpdater.entries()) reload();
            status.set(AddonUpdater.status());
        }
    }

    private static class BuildScreen extends WindowScreen {
        private final AddonUpdater.Entry entry;
        private WLabel status;
        private boolean shownQuit;

        BuildScreen(GuiTheme theme, AddonUpdater.Entry entry) {
            super(theme, entry.title());
            this.entry = entry;
        }

        @Override
        public void initWidgets() {
            shownQuit = AddonUpdater.restartRequired();
            add(theme.label("Version: " + entry.version())).expandX();
            add(theme.label("Channel: " + (entry.channel() == THMSystem.UpdateChannel.STABLE ? "Stable" : "Dev"))).expandX();
            add(theme.label("Branch: " + entry.branch() + " / Minecraft " + entry.minecraft())).expandX();
            add(theme.label("Commit: " + entry.commit())).expandX();
            add(theme.horizontalSeparator(entry.channel() == THMSystem.UpdateChannel.STABLE
                ? "Release notes" : "Commit description")).expandX();
            if (entry.notes().isBlank()) add(theme.label("No description provided."));
            else for (String paragraph : entry.notes().split("\\R", -1)) {
                add(theme.label(paragraph.isBlank() ? " " : paragraph, 480)).expandX();
            }
            add(theme.horizontalSeparator("Update status")).expandX();
            status = add(theme.label(AddonUpdater.status(), 480)).expandX().widget();
            WHorizontalList buttons = add(theme.horizontalList()).expandX().widget();
            if (AddonUpdater.isInstalled(entry)) buttons.add(theme.label("Installed"));
            else {
                WButton install = buttons.add(theme.button("Install this build")).expandX().widget();
                install.action = () -> AddonUpdater.install(entry,
                    () -> { if (Minecraft.getInstance().gui.screen() == this) reload(); });
            }
            if (shownQuit) {
                WButton quit = buttons.add(theme.button("Quit Minecraft")).expandX().widget();
                quit.action = () -> Minecraft.getInstance().stop();
            }
            WButton back = buttons.add(theme.button("Back to builds")).expandX().widget();
            back.action = () -> Minecraft.getInstance().gui.setScreen(new HistoryScreen(theme));
        }

        @Override
        public void tick() {
            super.tick();
            if (shownQuit != AddonUpdater.restartRequired()) reload();
            status.set(AddonUpdater.status());
        }
    }
}
