/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.utils;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class PacketSettingMigrationTest {
    @Test
    void preservesOldSelectionsAndExpandsSplitInteractionPackets() {
        CompoundTag tag = new CompoundTag();
        tag.putString("name", "always-block");
        ListTag values = new ListTag();
        for (String name : List.of("HandSwingC2SPacket", "PlayerInteractEntityC2SPacket", "clientbound/minecraft:ping", "unknown"))
            values.add(StringTag.valueOf(name));
        tag.put("value", values);

        CompoundTag result = PacketSettingMigration.migrate(tag);
        assertEquals(List.of("serverbound/minecraft:swing", "serverbound/minecraft:interact", "serverbound/minecraft:attack",
            "clientbound/minecraft:ping", "unknown"), result.getListOrEmpty("value").stream().map(t -> t.asString().orElseThrow()).toList());
        assertEquals("always-block", result.getStringOr("name", ""));
        assertEquals(4, tag.getListOrEmpty("value").size());
        assertSame(result, PacketSettingMigration.migrate(result));
    }

    @Test
    void preservesMovementAndEmptySelections() {
        CompoundTag tag = new CompoundTag();
        ListTag values = new ListTag();
        values.add(StringTag.valueOf("PlayerMoveC2SPacket"));
        tag.put("value", values);
        assertEquals(4, PacketSettingMigration.migrate(tag).getListOrEmpty("value").size());
        CompoundTag empty = new CompoundTag();
        empty.put("value", new ListTag());
        assertSame(empty, PacketSettingMigration.migrate(empty));
    }
}
