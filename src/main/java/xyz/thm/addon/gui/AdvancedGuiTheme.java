package xyz.thm.addon.gui;

public interface AdvancedGuiTheme {
    default boolean useInlineModuleSettings() {
        return false;
    }

    default boolean startModuleSettingsExpanded() {
        return false;
    }
}
