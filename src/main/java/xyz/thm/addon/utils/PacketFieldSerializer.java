/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.utils;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtElement;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.Packet;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;

public final class PacketFieldSerializer {
    private PacketFieldSerializer() {
    }

    public static JsonElement serialize(Object value) {
        return serialize(value, new IdentityHashMap<>());
    }

    public static String encodePayload(Packet<?> packet, DynamicRegistryManager registries) throws ReflectiveOperationException {
        @SuppressWarnings("unchecked")
        PacketCodec<RegistryByteBuf, Packet<?>> codec = (PacketCodec<RegistryByteBuf, Packet<?>>) packet.getClass().getField("CODEC").get(null);
        RegistryByteBuf buf = new RegistryByteBuf(Unpooled.buffer(), registries);
        try {
            codec.encode(buf, packet);
            return ByteBufUtil.hexDump(buf, 0, buf.writerIndex());
        } finally {
            buf.release();
        }
    }

    private static JsonElement serialize(Object value, IdentityHashMap<Object, Boolean> visiting) {
        if (value == null) return JsonNull.INSTANCE;
        if (value instanceof Boolean b) return new JsonPrimitive(b);
        if (value instanceof Number n) return Double.isFinite(n.doubleValue()) ? new JsonPrimitive(n) : new JsonPrimitive(n.toString());
        if (value instanceof CharSequence || value instanceof Character || value instanceof Enum<?>) return new JsonPrimitive(value.toString());
        if (value instanceof byte[] bytes) return new JsonPrimitive(java.util.HexFormat.of().formatHex(bytes));
        if (value instanceof ByteBuf buf) return new JsonPrimitive(ByteBufUtil.hexDump(buf, buf.readerIndex(), buf.readableBytes()));
        if (value instanceof RegistryKey<?> key) return new JsonPrimitive(key.getValue().toString());
        if (value instanceof RegistryEntry<?> entry) return new JsonPrimitive(entry.getKey().map(key -> key.getValue().toString()).orElseGet(entry::toString));
        if (value instanceof NbtElement) return new JsonPrimitive(value.toString());
        if (value instanceof BlockState) return new JsonPrimitive(value.toString());
        if (value instanceof ItemStack stack) {
            JsonObject item = new JsonObject();
            item.addProperty("item_id", Registries.ITEM.getId(stack.getItem()).toString());
            item.addProperty("count", stack.getCount());
            item.addProperty("components", stack.getComponents().toString());
            return item;
        }
        if (value instanceof Optional<?> optional) return optional.map(item -> serialize(item, visiting)).orElse(JsonNull.INSTANCE);

        if (visiting.put(value, true) != null) return new JsonPrimitive("<cycle>");
        try {
            if (value.getClass().isArray()) {
                JsonArray array = new JsonArray();
                for (int i = 0; i < Array.getLength(value); i++) array.add(serialize(Array.get(value, i), visiting));
                return array;
            }
            if (value instanceof Iterable<?> iterable) {
                JsonArray array = new JsonArray();
                for (Object item : iterable) array.add(serialize(item, visiting));
                return array;
            }
            if (value instanceof Map<?, ?> map) {
                JsonArray entries = new JsonArray();
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    JsonObject item = new JsonObject();
                    item.add("key", serialize(entry.getKey(), visiting));
                    item.add("value", serialize(entry.getValue(), visiting));
                    entries.add(item);
                }
                return entries;
            }

            Class<?> type = value.getClass();
            if (type.getName().startsWith("java.")) return new JsonPrimitive(value.toString());

            JsonObject fields = new JsonObject();
            for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
                for (Field field : current.getDeclaredFields()) {
                    if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) continue;
                    try {
                        field.setAccessible(true);
                        String name = fields.has(field.getName()) ? current.getSimpleName() + "." + field.getName() : field.getName();
                        fields.add(name, serialize(field.get(value), visiting));
                    } catch (RuntimeException | IllegalAccessException e) {
                        fields.addProperty(field.getName(), "<unavailable: " + e.getClass().getSimpleName() + ">");
                    }
                }
            }
            return fields;
        } finally {
            visiting.remove(value);
        }
    }
}
