package xyz.thm.addon.system;

import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.tabs.Tab;
import meteordevelopment.meteorclient.gui.tabs.TabScreen;
import meteordevelopment.meteorclient.gui.tabs.WindowTabScreen;
import meteordevelopment.meteorclient.settings.Settings;
import meteordevelopment.meteorclient.utils.misc.NbtUtils;
import net.minecraft.client.gui.screen.Screen;

public class THMTab extends Tab {
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

    private static class THMScreen extends WindowTabScreen {
        private final Settings settings;

        public THMScreen(GuiTheme theme, Tab tab) {
            super(theme, tab);
            settings = THMSystem.get().settings;
            settings.onActivated();
        }

        @Override
        public void initWidgets() {
            add(theme.settings(settings)).expandX();
        }

        @Override
        public void tick() {
            super.tick();
            settings.tick(window, theme);
        }

        @Override
        public boolean toClipboard() {
            return NbtUtils.toClipboard(THMSystem.get());
        }

        @Override
        public boolean fromClipboard() {
            return NbtUtils.fromClipboard(THMSystem.get());
        }
    }
}
