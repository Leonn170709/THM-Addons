package xyz.thm.addon.gui;

import meteordevelopment.meteorclient.gui.GuiTheme;

public final class ClientLookHelper {
    private ClientLookHelper() {}

    public static ClientLook getLook(GuiTheme theme) {
        if (theme instanceof ClientLookTheme lookTheme) return lookTheme.getClientLook();
        return ClientLook.DEFAULT;
    }
}
