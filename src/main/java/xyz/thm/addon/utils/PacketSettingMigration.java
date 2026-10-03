/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.utils;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Properties;

/** Preserves packet selections saved by the 1.21.11 Meteor build. */
public final class PacketSettingMigration {
    private static final Properties ALIASES = new Properties();
    static {
        try (var in = PacketSettingMigration.class.getResourceAsStream("/assets/thm-addon/legacy-packets.properties")) {
            if (in == null) throw new IllegalStateException("Missing legacy packet names");
            ALIASES.load(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private PacketSettingMigration() {}

    public static CompoundTag migrate(CompoundTag tag) {
        if (!(tag.get("value") instanceof ListTag values)) return tag;
        ListTag migrated = new ListTag();
        boolean changed = false;
        for (var value : values) {
            String old = value.asString().orElse("");
            String replacement = ALIASES.getProperty(old);
            if (replacement == null) migrated.add(value.copy());
            else {
                changed = true;
                for (String name : replacement.split(",")) migrated.add(StringTag.valueOf(name));
            }
        }
        if (!changed) return tag;
        CompoundTag result = tag.copy();
        result.put("value", migrated);
        return result;
    }
}
