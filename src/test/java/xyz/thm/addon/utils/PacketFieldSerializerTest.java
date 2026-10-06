/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.utils;

import com.google.gson.JsonObject;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.protocol.game.ClientboundBlockChangedAckPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PacketFieldSerializerTest {
    private static class BaseData {
        private final int inherited = 7;
    }

    private static final class PacketData extends BaseData {
        private final byte[] payload = {0, 15, (byte) 255};
        private final List<int[]> positions = List.of(new int[]{1, 2});
        private final ByteBuf buffer = Unpooled.wrappedBuffer(new byte[]{10, 11});
        private final PacketData self = this;
    }

    @Test
    void capturesInheritedNestedAndBinaryFieldsWithoutConsumingBuffer() {
        PacketData packet = new PacketData();
        try {
            JsonObject fields = PacketFieldSerializer.serialize(packet).getAsJsonObject();
            assertEquals(7, fields.get("inherited").getAsInt());
            assertEquals("000fff", fields.get("payload").getAsString());
            assertEquals(2, fields.getAsJsonArray("positions").get(0).getAsJsonArray().get(1).getAsInt());
            assertEquals("0a0b", fields.get("buffer").getAsString());
            assertEquals(0, packet.buffer.readerIndex());
            assertEquals("<cycle>", fields.get("self").getAsString());
        } finally {
            packet.buffer.release();
        }
    }

    @Test
    void encodesCompletePayloadForBothDirections() throws ReflectiveOperationException {
        assertEquals("0003", PacketFieldSerializer.encodePayload(new ServerboundSetCarriedItemPacket(3), RegistryAccess.EMPTY));
        assertEquals("03", PacketFieldSerializer.encodePayload(new ClientboundBlockChangedAckPacket(3), RegistryAccess.EMPTY));
    }
}
