/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.utils;

import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ParticleStatus;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.ChatVisiblity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChunkResyncTest {
    @Test void distanceDipAndRestorePreserveAllOtherClientPreferences() {
        var original = new ClientInformation("de_de", 16, ChatVisiblity.SYSTEM, false, 85,
            HumanoidArm.LEFT, true, false, ParticleStatus.MINIMAL);
        var dipped = ChunkResync.withViewDistance(original, 2);
        assertEquals(2, dipped.viewDistance());
        assertEquals(original, ChunkResync.withViewDistance(dipped, original.viewDistance()));
        assertEquals(16, original.viewDistance());
    }
}
