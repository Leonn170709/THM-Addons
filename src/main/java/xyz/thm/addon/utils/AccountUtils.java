/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.utils;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public final class AccountUtils {
    private AccountUtils() {}

    public static UUID offlineUuid(String name) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
    }

    public static boolean isCracked(String name, UUID uuid) {
        return uuid != null && name != null && !name.isBlank() && offlineUuid(name).equals(uuid);
    }

    public static boolean isCurrentAccountCracked() {
        if (mc.player != null) {
            var profile = mc.player.getGameProfile();
            return isCracked(profile.name(), profile.id());
        }
        var user = mc.getUser();
        return user != null && isCracked(user.getName(), user.getProfileId());
    }
}
