/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.system;

import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.tabs.Tab;
import meteordevelopment.meteorclient.gui.tabs.TabScreen;
import meteordevelopment.meteorclient.gui.tabs.WindowTabScreen;
import meteordevelopment.meteorclient.gui.widgets.containers.WVerticalList;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.settings.Settings;
import meteordevelopment.meteorclient.utils.misc.NbtUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import xyz.thm.addon.utils.ThmMembers;

public class THMTab extends Tab {
    private static boolean thmTabOpen;

    public THMTab() {
        super("THM Addon");
    }

    @Override
    public TabScreen createScreen(GuiTheme theme) {
        return new THMScreen(theme, this);
    }

    @Override
    public boolean isScreen(Screen screen) {
        return screen instanceof THMScreen;
    }

    // ---- Main THM screen ----

    private static class THMScreen extends WindowTabScreen {
        private final Settings settings;
        private WVerticalList settingsContainer;

        public THMScreen(GuiTheme theme, Tab tab) {
            super(theme, tab);
            settings = THMSystem.get().settings;
            settings.onActivated();
        }

        @Override
        public void tick() {
            super.tick();
            settings.tick(settingsContainer, theme);
        }

        @Override
        public boolean toClipboard() {
            return NbtUtils.toClipboard(THMSystem.get());
        }

        @Override
        public void initWidgets() {
            thmTabOpen = true;
            settingsContainer = add(theme.verticalList()).expandX().widget();
            settingsContainer.add(theme.settings(settings)).expandX();

            add(theme.horizontalSeparator()).expandX();

            WButton wavyCapesBtn = add(theme.button("Wavy Capes Settings")).expandX().widget();
            wavyCapesBtn.action = () ->
                Minecraft.getInstance().setScreen(WaveyCapesTab.INSTANCE.createScreen(theme));

            add(theme.horizontalSeparator()).expandX();

            WButton applyButton = theme.button("Apply Profile");
            applyButton.action = () -> THMSystem.get().applyProfile();
            add(applyButton).expandX();

            WButton refreshMembers = theme.button("Refresh Cache");
            refreshMembers.action = ThmMembers::refreshNow;
            add(refreshMembers).expandX();
        }

        @Override
        public void removed() {
            thmTabOpen = false;
            super.removed();
        }

        @Override
        public boolean fromClipboard() {
            return NbtUtils.fromClipboard(THMSystem.get());
        }
    }

    // ---- Wavy Capes sub-screen tab (not registered in the tab bar) ----

    public static class WaveyCapesTab extends Tab {
        public static final WaveyCapesTab INSTANCE = new WaveyCapesTab();

        public WaveyCapesTab() {
            super("Wavy Capes Settings");
        }

        @Override
        public TabScreen createScreen(GuiTheme theme) {
            return new WaveyCapesScreen(theme, this);
        }

        @Override
        public boolean isScreen(Screen screen) {
            return screen instanceof WaveyCapesScreen;
        }
    }

    private static class WaveyCapesScreen extends WindowTabScreen {
        private final Settings wavySettings;
        private WVerticalList settingsContainer;

        public WaveyCapesScreen(GuiTheme theme, Tab tab) {
            super(theme, tab);
            wavySettings = THMSystem.get().wavyCapesSettings;
            wavySettings.onActivated();
        }

        @Override
        public void tick() {
            super.tick();
            wavySettings.tick(settingsContainer, theme);
        }

        @Override
        public void initWidgets() {
            settingsContainer = add(theme.verticalList()).expandX().widget();
            settingsContainer.add(theme.settings(wavySettings)).expandX();
        }

        @Override
        public boolean toClipboard() { return false; }

        @Override
        public boolean fromClipboard() { return false; }
    }

    public static boolean isThmTabOpen() {
        return thmTabOpen;
    }
}
