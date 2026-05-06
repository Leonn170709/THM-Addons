/*
 * Adapted from Meteor Client's PacketLogger module.
 * Original source: https://github.com/MeteorDevelopment/meteor-client
 * Copyright (c) Meteor Development.
 *
 * THM-specific changes:
 * - Structured JSONL output for replay-grade packet logging
 * - Exact field extraction for common packet types
 * - File-first defaults with THM-owned log directory
 */

package xyz.thm.addon.modules;

import com.google.gson.*;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.network.PacketUtils;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.*;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerActionResponseS2CPacket;
import net.minecraft.network.packet.s2c.play.ScreenHandlerSlotUpdateS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryOps;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.screen.sync.ItemStackHash;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import xyz.thm.addon.THMAddon;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

public class PacketLoggerTHM extends Module {
    private static final int SCHEMA_VERSION = 1;
    private static final Gson GSON = new GsonBuilder()
        .serializeNulls()
        .disableHtmlEscaping()
        .create();
    private static final Path PACKET_LOGS_DIR = FabricLoader.getInstance().getGameDir().resolve("logs").resolve("thm-packet-logs");
    private static final DateTimeFormatter FILE_NAME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    private static final DateTimeFormatter WALL_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
        .withZone(ZoneOffset.UTC);
    private static final int LINE_SEPARATOR_BYTES = System.lineSeparator().getBytes(StandardCharsets.UTF_8).length;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgOutput = settings.createGroup("Output");

    private final Setting<Set<Class<? extends Packet<?>>>> s2cPackets = sgGeneral.add(new PacketListSetting.Builder()
        .name("S2C-packets")
        .description("Server-to-client packets to log.")
        .filter(aClass -> PacketUtils.getS2CPackets().contains(aClass))
        .defaultValue(new ObjectOpenHashSet<>(PacketUtils.getS2CPackets()))
        .build()
    );

    private final Setting<Set<Class<? extends Packet<?>>>> c2sPackets = sgGeneral.add(new PacketListSetting.Builder()
        .name("C2S-packets")
        .description("Client-to-server packets to log.")
        .filter(aClass -> PacketUtils.getC2SPackets().contains(aClass))
        .defaultValue(new ObjectOpenHashSet<>(PacketUtils.getC2SPackets()))
        .build()
    );

    private final Setting<Boolean> logToFile = sgOutput.add(new BoolSetting.Builder()
        .name("log-to-file")
        .description("Write packet logs to structured JSONL files.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> logToChat = sgOutput.add(new BoolSetting.Builder()
        .name("log-to-chat")
        .description("Preview logged packets in chat.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> showTimestamp = sgOutput.add(new BoolSetting.Builder()
        .name("show-timestamp")
        .description("Show timestamps in chat previews.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> showCount = sgOutput.add(new BoolSetting.Builder()
        .name("show-count")
        .description("Show per-packet counts in chat previews.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> showSummary = sgOutput.add(new BoolSetting.Builder()
        .name("show-summary")
        .description("Show a packet count summary in chat when the module deactivates.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> captureRawToString = sgOutput.add(new BoolSetting.Builder()
        .name("capture-raw-to-string")
        .description("Include the packet's raw toString() output in JSONL records.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> flushInterval = sgOutput.add(new IntSetting.Builder()
        .name("flush-interval")
        .description("How often to flush packet logs to disk in seconds.")
        .defaultValue(1)
        .min(1)
        .sliderRange(1, 10)
        .visible(logToFile::get)
        .build()
    );

    private final Setting<Integer> maxFileSizeMB = sgOutput.add(new IntSetting.Builder()
        .name("max-file-size-mb")
        .description("Maximum size of a single packet log file before rotation.")
        .defaultValue(10)
        .min(1)
        .sliderRange(1, 100)
        .visible(logToFile::get)
        .build()
    );

    private final Setting<Integer> maxTotalLogsMB = sgOutput.add(new IntSetting.Builder()
        .name("max-total-logs-mb")
        .description("Maximum total size of all THM packet logs before deleting the oldest files.")
        .defaultValue(100)
        .min(1)
        .sliderRange(1, 500)
        .visible(logToFile::get)
        .build()
    );

    private final Reference2IntOpenHashMap<Class<? extends Packet<?>>> packetCounts = new Reference2IntOpenHashMap<>();
    private final List<Path> sessionFiles = new ArrayList<>();

    private BufferedWriter fileWriter;
    private Path currentFilePath;
    private LocalDateTime sessionFileTime;
    private Instant sessionStartInstant;
    private long sessionStartNano;
    private long lastFlushMs;
    private long currentFileSizeBytes;
    private long ordinalCounter;
    private int currentFileIndex;
    private int serializationErrorCount;
    private int fileWriteErrorCount;

    public PacketLoggerTHM() {
        super(THMAddon.MAIN, "packet-logger-thm", "Logs selected packets to replay-grade JSONL files.");
        runInMainMenu = true;
    }

    @Override
    public void onActivate() {
        closeFileWriter();

        packetCounts.clear();
        sessionFiles.clear();
        currentFilePath = null;
        sessionFileTime = LocalDateTime.now();
        sessionStartInstant = Instant.now();
        sessionStartNano = System.nanoTime();
        lastFlushMs = System.currentTimeMillis();
        currentFileSizeBytes = 0;
        ordinalCounter = 0;
        currentFileIndex = 0;
        serializationErrorCount = 0;
        fileWriteErrorCount = 0;

        if (logToFile.get()) {
            try {
                Files.createDirectories(PACKET_LOGS_DIR);
                cleanupOldLogs();
                openNewLogFile();
                writeJsonRecord(buildStartRecord());
            } catch (IOException e) {
                error("Failed to initialize THM packet logging: %s", e.getMessage());
                fileWriteErrorCount++;
                closeFileWriter();
            }
        }
    }

    @Override
    public void onDeactivate() {
        if (logToFile.get() && fileWriter != null) writeJsonRecord(buildSummaryRecord());
        if (showSummary.get() && logToChat.get() && !packetCounts.isEmpty()) logSummaryToChat();
        closeFileWriter();
    }

    @EventHandler(priority = EventPriority.HIGHEST + 1)
    private void onReceivePacket(PacketEvent.Receive event) {
        if (s2cPackets.get().contains(event.packet.getClass())) logPacket("s2c", "<- S2C", event.packet);
    }

    @EventHandler(priority = EventPriority.HIGHEST + 1)
    private void onSendPacket(PacketEvent.Send event) {
        if (c2sPackets.get().contains(event.packet.getClass())) logPacket("c2s", "-> C2S", event.packet);
    }

    private void logPacket(String dir, String chatDir, Packet<?> packet) {
        if (!logToChat.get() && !logToFile.get()) return;

        @SuppressWarnings("unchecked")
        Class<? extends Packet<?>> packetClass = (Class<? extends Packet<?>>) packet.getClass();
        packetCounts.addTo(packetClass, 1);

        long ordinal = ++ordinalCounter;
        if (logToChat.get()) logPacketToChat(chatDir, packetClass);
        if (logToFile.get()) writeJsonRecord(buildPacketRecord(dir, ordinal, packet));
    }

    private void logPacketToChat(String direction, Class<? extends Packet<?>> packetClass) {
        StringBuilder line = new StringBuilder(96);

        if (showTimestamp.get()) {
            line.append('[')
                .append(WALL_TIME_FORMATTER.format(Instant.now()))
                .append("] ");
        }

        line.append(direction).append(' ').append(PacketUtils.getName(packetClass));

        if (showCount.get()) {
            line.append(" (#").append(packetCounts.getInt(packetClass)).append(')');
        }

        info(line.toString());
    }

    private void logSummaryToChat() {
        int totalPackets = packetCounts.values().intStream().sum();
        info("--- THM Packet Logger Summary ---");
        info("Total packets logged: %d", totalPackets);

        packetCounts.reference2IntEntrySet().stream()
            .sorted((a, b) -> Integer.compare(b.getIntValue(), a.getIntValue()))
            .forEach(entry -> info("%s: %d", PacketUtils.getName(entry.getKey()), entry.getIntValue()));
    }

    private JsonObject buildStartRecord() {
        JsonObject record = baseRecord("start");
        record.addProperty("username", mc.getSession().getUsername());
        record.addProperty("singleplayer", mc.isInSingleplayer());
        addNullableString(record, "server_address", mc.getCurrentServerEntry() != null ? mc.getCurrentServerEntry().address : null);
        addNullableString(record, "dimension", mc.world != null ? mc.world.getRegistryKey().getValue().toString() : null);
        record.addProperty("addon_version", THMAddon.VERSION);
        record.addProperty("game_version", SharedConstants.getGameVersion().name());
        record.add("settings", buildSettingsSnapshot());
        return record;
    }

    private JsonObject buildSummaryRecord() {
        JsonObject record = baseRecord("summary");
        record.addProperty("elapsed_ms", getMonotonicMillis());
        record.addProperty("total_packets", packetCounts.values().intStream().sum());
        record.add("packet_counts", buildPacketCountsArray());
        record.add("output_files", buildOutputFilesArray());
        record.addProperty("serialization_error_count", serializationErrorCount);
        record.addProperty("file_write_error_count", fileWriteErrorCount);
        return record;
    }

    private JsonObject buildPacketRecord(String dir, long ordinal, Packet<?> packet) {
        JsonObject record = baseRecord("packet");
        @SuppressWarnings("unchecked")
        Class<? extends Packet<?>> packetClass = (Class<? extends Packet<?>>) packet.getClass();

        record.addProperty("dir", dir);
        record.addProperty("ordinal", ordinal);
        record.addProperty("packet_class", packetClass.getName());
        record.addProperty("packet_name", PacketUtils.getName(packetClass));

        JsonObject fields;
        try {
            fields = serializePacket(packet);
        } catch (Exception e) {
            serializationErrorCount++;
            fields = new JsonObject();
            fields.addProperty("serialization_error", e.toString());
        }

        record.add("fields", fields);

        if (captureRawToString.get()) {
            try {
                record.addProperty("raw_to_string", String.valueOf(packet));
            } catch (Exception e) {
                record.addProperty("raw_to_string", "<toString failed: " + e.getClass().getSimpleName() + ">");
            }
        }

        return record;
    }

    private JsonObject baseRecord(String kind) {
        JsonObject record = new JsonObject();
        record.addProperty("kind", kind);
        record.addProperty("schema_version", SCHEMA_VERSION);
        record.addProperty("ts_wall", WALL_TIME_FORMATTER.format(Instant.now()));
        record.addProperty("ts_mono_ms", getMonotonicMillis());
        return record;
    }

    private JsonObject buildSettingsSnapshot() {
        JsonObject settingsJson = new JsonObject();
        settingsJson.addProperty("log_to_file", logToFile.get());
        settingsJson.addProperty("log_to_chat", logToChat.get());
        settingsJson.addProperty("show_timestamp", showTimestamp.get());
        settingsJson.addProperty("show_count", showCount.get());
        settingsJson.addProperty("show_summary", showSummary.get());
        settingsJson.addProperty("capture_raw_to_string", captureRawToString.get());
        settingsJson.addProperty("flush_interval_s", flushInterval.get());
        settingsJson.addProperty("max_file_size_mb", maxFileSizeMB.get());
        settingsJson.addProperty("max_total_logs_mb", maxTotalLogsMB.get());
        settingsJson.add("c2s_packets", packetNamesToJsonArray(c2sPackets.get()));
        settingsJson.add("s2c_packets", packetNamesToJsonArray(s2cPackets.get()));
        return settingsJson;
    }

    private JsonArray buildPacketCountsArray() {
        JsonArray counts = new JsonArray();
        packetCounts.reference2IntEntrySet().stream()
            .sorted((a, b) -> Integer.compare(b.getIntValue(), a.getIntValue()))
            .forEach(entry -> {
                JsonObject packetCount = new JsonObject();
                packetCount.addProperty("packet_class", entry.getKey().getName());
                packetCount.addProperty("packet_name", PacketUtils.getName(entry.getKey()));
                packetCount.addProperty("count", entry.getIntValue());
                counts.add(packetCount);
            });
        return counts;
    }

    private JsonArray buildOutputFilesArray() {
        JsonArray files = new JsonArray();
        for (Path path : sessionFiles) {
            files.add(path.toAbsolutePath().toString());
        }
        return files;
    }

    private JsonArray packetNamesToJsonArray(Set<Class<? extends Packet<?>>> packets) {
        JsonArray array = new JsonArray();
        packets.stream()
            .map(PacketUtils::getName)
            .sorted()
            .forEach(array::add);
        return array;
    }

    private JsonObject serializePacket(Packet<?> packet) {
        JsonObject fields = new JsonObject();

        if (packet instanceof PlayerActionC2SPacket p) {
            fields.addProperty("action", p.getAction().name());
            fields.add("block_pos", serializeBlockPos(p.getPos()));
            fields.addProperty("direction", p.getDirection().name());
            fields.addProperty("sequence", p.getSequence());
            return fields;
        }

        if (packet instanceof PlayerInteractBlockC2SPacket p) {
            BlockHitResult hit = p.getBlockHitResult();
            fields.addProperty("hand", p.getHand().name());
            fields.add("block_pos", serializeBlockPos(hit.getBlockPos()));
            fields.addProperty("side", hit.getSide().name());
            fields.add("hit_vec", serializeVec3d(hit.getPos()));
            fields.addProperty("inside_block", hit.isInsideBlock());
            fields.addProperty("sequence", p.getSequence());
            return fields;
        }

        if (packet instanceof PlayerInteractItemC2SPacket p) {
            fields.addProperty("hand", p.getHand().name());
            fields.addProperty("sequence", p.getSequence());
            fields.addProperty("yaw", p.getYaw());
            fields.addProperty("pitch", p.getPitch());
            return fields;
        }

        if (packet instanceof UpdateSelectedSlotC2SPacket p) {
            fields.addProperty("slot", p.getSelectedSlot());
            return fields;
        }

        if (packet instanceof ClickSlotC2SPacket p) {
            fields.addProperty("sync_id", p.syncId());
            fields.addProperty("revision", p.revision());
            fields.addProperty("slot", p.slot());
            fields.addProperty("button", p.button());
            fields.addProperty("action_type", p.actionType().name());
            fields.add("cursor", serializeItemStackHash(p.cursor()));
            fields.add("changed_stacks", serializeChangedStackHashes(p.modifiedStacks()));
            return fields;
        }

        if (packet instanceof HandSwingC2SPacket p) {
            fields.addProperty("hand", p.getHand().name());
            return fields;
        }

        if (packet instanceof PlayerMoveC2SPacket p) {
            fields.addProperty("subtype", getMoveSubtype(p));
            fields.addProperty("changes_position", p.changesPosition());
            fields.addProperty("changes_look", p.changesLook());
            fields.addProperty("on_ground", p.isOnGround());
            fields.addProperty("horizontal_collision", p.horizontalCollision());
            if (p.changesPosition()) {
                fields.addProperty("x", p.getX(Double.NaN));
                fields.addProperty("y", p.getY(Double.NaN));
                fields.addProperty("z", p.getZ(Double.NaN));
            }
            if (p.changesLook()) {
                fields.addProperty("yaw", p.getYaw(Float.NaN));
                fields.addProperty("pitch", p.getPitch(Float.NaN));
            }
            return fields;
        }

        if (packet instanceof PlayerInputC2SPacket p) {
            PlayerInput input = p.input();
            fields.addProperty("forward", input.forward());
            fields.addProperty("backward", input.backward());
            fields.addProperty("left", input.left());
            fields.addProperty("right", input.right());
            fields.addProperty("jump", input.jump());
            fields.addProperty("sneak", input.sneak());
            fields.addProperty("sprint", input.sprint());
            return fields;
        }

        if (packet instanceof ClientCommandC2SPacket p) {
            fields.addProperty("mode", p.getMode().name());
            fields.addProperty("entity_id", p.getEntityId());
            fields.addProperty("mount_jump_height", p.getMountJumpHeight());
            return fields;
        }

        if (packet instanceof ScreenHandlerSlotUpdateS2CPacket p) {
            fields.addProperty("sync_id", p.getSyncId());
            fields.addProperty("slot", p.getSlot());
            fields.addProperty("revision", p.getRevision());
            fields.add("stack", serializeItemStack(p.getStack()));
            return fields;
        }

        if (packet instanceof BlockUpdateS2CPacket p) {
            fields.add("block_pos", serializeBlockPos(p.getPos()));
            JsonObject state = new JsonObject();
            state.addProperty("block_id", Registries.BLOCK.getId(p.getState().getBlock()).toString());
            state.addProperty("state", p.getState().toString());
            fields.add("block_state", state);
            return fields;
        }

        if (packet instanceof PlayerActionResponseS2CPacket p) {
            fields.addProperty("sequence", p.sequence());
            return fields;
        }

        return fields;
    }

    private JsonArray serializeChangedStackHashes(Int2ObjectMap<ItemStackHash> modifiedStacks) {
        JsonArray stacks = new JsonArray();
        modifiedStacks.int2ObjectEntrySet().stream()
            .sorted(Comparator.comparingInt(Int2ObjectMap.Entry::getIntKey))
            .forEach(entry -> {
                JsonObject stackEntry = new JsonObject();
                stackEntry.addProperty("slot", entry.getIntKey());
                stackEntry.add("stack", serializeItemStackHash(entry.getValue()));
                stacks.add(stackEntry);
            });
        return stacks;
    }

    private JsonObject serializeItemStackHash(ItemStackHash stackHash) {
        JsonObject json = new JsonObject();
        json.addProperty("empty", stackHash == null || stackHash == ItemStackHash.EMPTY);
        if (stackHash == null || stackHash == ItemStackHash.EMPTY) return json;

        if (stackHash instanceof ItemStackHash.Impl impl) {
            json.addProperty("item_id", getRegistryEntryId(impl.item()));
            json.addProperty("count", impl.count());
            json.addProperty("components", String.valueOf(impl.components()));
        } else {
            json.addProperty("raw", String.valueOf(stackHash));
        }

        return json;
    }

    private JsonObject serializeItemStack(ItemStack stack) {
        JsonObject json = new JsonObject();
        json.addProperty("empty", stack == null || stack.isEmpty());
        if (stack == null || stack.isEmpty()) return json;

        json.addProperty("item_id", Registries.ITEM.getId(stack.getItem()).toString());
        json.addProperty("count", stack.getCount());
        json.addProperty("damage", stack.getDamage());
        json.addProperty("max_damage", stack.getMaxDamage());
        json.addProperty("damageable", stack.isDamageable());

        Text customName = stack.getCustomName();
        if (customName != null) json.addProperty("custom_name", customName.getString());
        else json.add("custom_name", JsonNull.INSTANCE);

        json.add("enchantments", serializeEnchantments(stack.getEnchantments()));

        if (mc.world != null) {
            RegistryWrapper.WrapperLookup lookup = (RegistryWrapper.WrapperLookup) mc.world.getRegistryManager();
            NbtElement nbt = ItemStack.CODEC.encodeStart(RegistryOps.of(NbtOps.INSTANCE, lookup), stack).result().orElse(null);
            if (nbt != null) json.addProperty("nbt_snbt", nbt.asString().orElse(""));
            else json.add("nbt_snbt", JsonNull.INSTANCE);
        } else {
            json.add("nbt_snbt", JsonNull.INSTANCE);
        }

        return json;
    }

    private JsonArray serializeEnchantments(ItemEnchantmentsComponent enchantments) {
        JsonArray array = new JsonArray();
        List<JsonObject> entries = new ArrayList<>();

        for (Object2IntMap.Entry<RegistryEntry<Enchantment>> entry : enchantments.getEnchantmentEntries()) {
            JsonObject enchantment = new JsonObject();
            enchantment.addProperty("id", getEnchantmentId(entry.getKey()));
            enchantment.addProperty("level", entry.getIntValue());
            entries.add(enchantment);
        }

        entries.stream()
            .sorted(Comparator.comparing(e -> e.get("id").getAsString()))
            .forEach(array::add);

        return array;
    }

    private String getEnchantmentId(RegistryEntry<Enchantment> entry) {
        return entry.getKey()
            .map(RegistryKey::getValue)
            .map(Identifier::toString)
            .orElse(entry.toString());
    }

    private <T> String getRegistryEntryId(RegistryEntry<T> entry) {
        return entry.getKey()
            .map(RegistryKey::getValue)
            .map(Identifier::toString)
            .orElse(entry.toString());
    }

    private JsonObject serializeBlockPos(BlockPos pos) {
        JsonObject json = new JsonObject();
        json.addProperty("x", pos.getX());
        json.addProperty("y", pos.getY());
        json.addProperty("z", pos.getZ());
        return json;
    }

    private JsonObject serializeVec3d(Vec3d vec) {
        JsonObject json = new JsonObject();
        json.addProperty("x", vec.x);
        json.addProperty("y", vec.y);
        json.addProperty("z", vec.z);
        return json;
    }

    private String getMoveSubtype(PlayerMoveC2SPacket packet) {
        if (packet instanceof PlayerMoveC2SPacket.Full) return "full";
        if (packet instanceof PlayerMoveC2SPacket.LookAndOnGround) return "look_and_on_ground";
        if (packet instanceof PlayerMoveC2SPacket.PositionAndOnGround) return "position_and_on_ground";
        if (packet instanceof PlayerMoveC2SPacket.OnGroundOnly) return "on_ground_only";
        return "base";
    }

    private void writeJsonRecord(JsonObject record) {
        if (fileWriter == null) return;

        try {
            String line = GSON.toJson(record);
            int lineBytes = line.getBytes(StandardCharsets.UTF_8).length + LINE_SEPARATOR_BYTES;

            if (currentFileSizeBytes + lineBytes > maxFileSizeMB.get() * 1024L * 1024L) {
                openNewLogFile();
            }

            fileWriter.write(line);
            fileWriter.newLine();
            currentFileSizeBytes += lineBytes;

            long now = System.currentTimeMillis();
            if (now - lastFlushMs >= flushInterval.get() * 1000L) {
                fileWriter.flush();
                lastFlushMs = now;
            }
        } catch (IOException e) {
            fileWriteErrorCount++;
            error("Failed to write packet log file: %s", e.getMessage());
            closeFileWriter();
        }
    }

    private void openNewLogFile() throws IOException {
        closeFileWriter();

        String fileName = "packets-%s-%d.jsonl".formatted(sessionFileTime.format(FILE_NAME_FORMATTER), currentFileIndex++);
        Path path = PACKET_LOGS_DIR.resolve(fileName);

        fileWriter = Files.newBufferedWriter(
            path,
            StandardCharsets.UTF_8,
            StandardOpenOption.CREATE_NEW,
            StandardOpenOption.WRITE
        );

        currentFilePath = path;
        currentFileSizeBytes = 0;
        lastFlushMs = System.currentTimeMillis();
        sessionFiles.add(path);
        cleanupOldLogs();
    }

    private void closeFileWriter() {
        if (fileWriter == null) return;

        try {
            fileWriter.flush();
            fileWriter.close();
        } catch (IOException ignored) {
            // Safe to ignore on shutdown/rotation.
        } finally {
            fileWriter = null;
        }
    }

    private void cleanupOldLogs() throws IOException {
        long maxBytes = maxTotalLogsMB.get() * 1024L * 1024L;
        List<LogFileEntry> logFiles = new ArrayList<>();

        if (!Files.isDirectory(PACKET_LOGS_DIR)) return;

        try (var stream = Files.list(PACKET_LOGS_DIR)) {
            for (Path path : stream.toList()) {
                String name = path.getFileName().toString();
                if (!name.startsWith("packets-") || !name.endsWith(".jsonl")) continue;

                try {
                    logFiles.add(new LogFileEntry(
                        path,
                        Files.size(path),
                        Files.getLastModifiedTime(path).toMillis()
                    ));
                } catch (IOException ignored) {
                    // Skip inaccessible files.
                }
            }
        }

        logFiles.sort(Comparator.comparingLong(LogFileEntry::lastModified));
        long totalSize = logFiles.stream().mapToLong(LogFileEntry::size).sum();

        for (LogFileEntry entry : logFiles) {
            if (totalSize <= maxBytes) break;
            if (sessionFiles.contains(entry.path)) continue;

            if (Files.deleteIfExists(entry.path)) {
                totalSize -= entry.size;
            }
        }
    }

    private long getMonotonicMillis() {
        return (System.nanoTime() - sessionStartNano) / 1_000_000L;
    }

    private void addNullableString(JsonObject object, String key, String value) {
        if (value == null) object.add(key, JsonNull.INSTANCE);
        else object.addProperty(key, value);
    }

    private record LogFileEntry(Path path, long size, long lastModified) {
    }
}
