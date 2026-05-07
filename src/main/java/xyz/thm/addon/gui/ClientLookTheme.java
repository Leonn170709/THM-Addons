package xyz.thm.addon.gui;

public interface ClientLookTheme {
    default ClientLook getClientLook() {
        return ClientLook.DEFAULT;
    }
}
