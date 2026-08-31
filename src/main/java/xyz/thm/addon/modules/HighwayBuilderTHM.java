/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package xyz.thm.addon.modules;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.entity.player.PlayerMoveEvent;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.mixin.ShulkerBoxScreenHandlerAccessor;
import meteordevelopment.meteorclient.mixininterface.IVec3d;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.renderer.text.TextRenderer;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.combat.AutoTotem;
import meteordevelopment.meteorclient.systems.modules.combat.KillAura;
import meteordevelopment.meteorclient.systems.modules.movement.Velocity;
import meteordevelopment.meteorclient.systems.modules.movement.speed.Speed;
import meteordevelopment.meteorclient.systems.modules.movement.speed.SpeedModes;
import meteordevelopment.meteorclient.systems.modules.player.*;
import meteordevelopment.meteorclient.systems.modules.world.NoGhostBlocks;
import meteordevelopment.meteorclient.systems.modules.world.Timer;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.entity.SortPriority;
import meteordevelopment.meteorclient.utils.entity.TargetUtils;
import meteordevelopment.meteorclient.utils.misc.HorizontalDirection;
import meteordevelopment.meteorclient.utils.misc.MBlockPos;
import meteordevelopment.meteorclient.utils.player.*;
import meteordevelopment.meteorclient.utils.render.NametagUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.meteorclient.utils.world.TickRate;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.HangingSignBlockEntity;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.block.entity.SignText;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.gui.screen.ingame.ShulkerBoxScreen;
import net.minecraft.client.input.Input;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.*;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.s2c.play.EntityPositionSyncS2CPacket;
import net.minecraft.network.packet.s2c.play.InventoryS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.EmptyBlockView;
import net.minecraft.world.RaycastContext;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3d;
import xyz.thm.addon.THMAddon;
import xyz.thm.addon.accessor.StuckEatingRetryBridge;
import xyz.thm.addon.accessor.StuckEatingRetryResult;
import xyz.thm.addon.settings.ItemPriorityList;
import xyz.thm.addon.system.THMSystem;
import xyz.thm.addon.utils.*;
import xyz.thm.addon.utils.kitbot.*;
import xyz.thm.addon.utils.server.*;
import xyz.thm.addon.utils.server.ServerStatusHandler.ServerState;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import static xyz.thm.addon.utils.APIUtils.*;
import static xyz.thm.addon.utils.THMUtils.*;

@SuppressWarnings("ConstantConditions")
public class HighwayBuilderTHM extends Module {
    private static final String STATS_ARTIFACT_MAGIC = "HB_STATS_ARTIFACT_V1";
    private static final int STATS_ARTIFACT_VERSION = 1;
    private static final long KITBOT_FRONTEND_WINDOW_BUFFER_MS = 1_000L;
    private static final long KITBOT_INVENTORY_PROOF_GRACE_MS = 30_000L;
    private static final int KITBOT_MAX_ACCEPTED_ATTEMPTS = 3;

    private enum KitbotRestockGate {
        Available,
        AwaitingRestart,
        Unavailable
    }

    private enum TpsThrottlePauseReason {
        None,
        Settling,
        StaleTick,
        InvalidTps,
        LowTps
    }

    @Override
    public String getInfoString() {
        return switch (THMSystem.get().getMode()) {
            case THMSystem.Mode.HighwayBuilding -> "Building";
            case THMSystem.Mode.HighwayDigging -> "Digging";
            default -> "Custom";
        };
    }

    private static final long STATS_CHECKPOINT_INTERVAL_MS = 10 * 60 * 1000L;
    private static final int STATS_SCREENSHOT_DELAY_MS = 250;
    private static final long STATS_MEMORY_RETRY_RECHECK_MS = 5_000L;
    private static final String THM_DEBUG_LOG_DIR_NAME = "thm";
    private static final String HIGHWAYBUILDER_DEBUG_FILE_NAME = "highwaybuilder-debug.log";
    private static final String RESTOCK_DEBUG_FILE_NAME = "highwaybuilder-restock-debug.log";
    private static final String FORWARD_STATS_DEBUG_FILE_NAME = "highwaybuilder-forward-stats-debug.log";
    private static final String FORWARD_SCHEDULER_DEBUG_FILE_NAME = "highwaybuilder-forward-scheduler.log";
    private static final String RECONNECT_DEBUG_FILE_NAME = "thm-reconnect-debug.log";
    private static final long HIGHWAYBUILDER_MAIN_SERVER_RESUME_DELAY_MS = 6_000L;
    private static final long SERVER_STATE_PAUSE_LOG_INTERVAL_MS = 5_000L;
    private static final String THM_SPEED_SNAPSHOT_FILE_NAME = "highwaybuilder-speed-snapshot";
    private static final String THM_SPEED_SNAPSHOT_MAGIC = "HB_THM_SPEED_SNAPSHOT_V1";
    private static final int THM_SPEED_SNAPSHOT_VERSION = 1;
    private static boolean MANAGED_SPEEDMINE_SETTING_ENABLED = false;
    private static boolean MODULE_MANAGER_INTEGRATION_ENABLED = false;
    private static final long THM_DEBUG_LOG_ROTATE_BYTES = 100L * 1024L * 1024L;
    private static final Object THM_DEBUG_LOG_LOCK = new Object();
    private static final String STATS_CANONICAL_FILE_NAME = "highwaybuildersettings";
    private static final String STATS_FINALIZATION_FILE_NAME = "highwaybuildersettings.finalization";
    private static final String STATS_SHADOW_FILE_NAME = "highwaybuildersettings.shadow";
    private static final int STATS_GCM_TAG_BITS = 128;
    private static final int STATS_GCM_NONCE_BYTES = 12;
    private static final int ENCLOSURE_PLACE_CREDIT_SCALE = 10;
    private static final int RESTOCK_WATCHDOG_LOCAL_RETRY_MAX = 3;
    private static final int RESTOCK_WATCHDOG_RECOVERY_DELAY_TICKS = 40;
    private static final int RESTOCK_WATCHDOG_SETUP_TIMEOUT_TICKS = 600;
    private static final int RESTOCK_WATCHDOG_TRANSFER_TIMEOUT_TICKS = 300;
    private static final int RESTOCK_WATCHDOG_MINE_ECHESTS_TIMEOUT_TICKS = 1200;
    private static final int RESTOCK_DUPLICATE_QUEUE_LOG_INTERVAL_TICKS = 100;
    private static final double BLOCKADE_CENTER_TOLERANCE = 0.03;
    private static final int ENCLOSURE_PLACEMENT_CONFIRM_MAX_ATTEMPTS = 3;
    private static final int ENCLOSURE_PLACEMENT_RECENTER_AFTER_ATTEMPTS = 2;
    private static final int ENCLOSURE_PLACEMENT_CONFIRM_WAIT_TICKS = 2;
    private static final int ENCLOSURE_PLACEMENT_DESYNC_MIN_INTERACT_PACKETS = 2;
    private static final int DESYNC_WIGGLE_LEFT_TICKS = 4;
    private static final int DESYNC_WIGGLE_RIGHT_TICKS = 4;
    private static final int DESYNC_WIGGLE_SETTLE_TICKS = 2;
    private static final int DESYNC_WIGGLE_TOTAL_TICKS = DESYNC_WIGGLE_LEFT_TICKS + DESYNC_WIGGLE_RIGHT_TICKS + DESYNC_WIGGLE_SETTLE_TICKS;
    private static final double DESYNC_WIGGLE_SNAP_DISTANCE = 0.5;
    private static final double THM_LATERAL_SPEED = 0.5;
    private static final double THM_CENTER_TELEPORT_SLOWDOWN_DISTANCE = 1.0;
    private static final double THM_CENTER_TELEPORT_SLOWDOWN_MULTIPLIER = 0.5;
    private static final double THM_FORWARD_DRIFT_RECOVERY_START = 0.125;
    private static final double THM_FORWARD_DRIFT_RECOVERY_STOP = 0.03;
    private static final double KITBOT_ORDER_CENTER_RADIUS = 0.125;
    private static final double KITBOT_ORDER_SUBMIT_RADIUS = 0.25;
    private static final double KITBOT_ORDER_CENTER_SLOWDOWN_DISTANCE = 0.75;
    private static final double KITBOT_ORDER_CENTER_SPEED_CAP = 1.0;
    private static final int KITBOT_ORDER_CENTER_TIMEOUT_TICKS = 100;
    private static final int KITBOT_ORDER_VERIFY_RECENTER_TIMEOUT_TICKS = 20;
    private static final int KITBOT_ORDER_VERIFY_SETTLE_TICKS = 5;
    private static final int KITBOT_ORDER_VERIFY_WIGGLE_TICKS = DESYNC_WIGGLE_LEFT_TICKS + DESYNC_WIGGLE_RIGHT_TICKS;
    private static final int TPS_THROTTLE_SAMPLE_INTERVAL_TICKS = 40;
    private static final double TPS_THROTTLE_PAUSE_THRESHOLD = 10.0;
    private static final double TPS_THROTTLE_NORMAL = 20.0;
    private static final double TPS_THROTTLE_DEBUG_DELTA = 0.05;
    private static final long TPS_THROTTLE_SETTLE_MS = 5_000L;
    private static final float TPS_THROTTLE_STALE_TICK_SECONDS = 1.5f;
    private static final long TPS_THROTTLE_PAUSE_REASON_LOG_INTERVAL_MS = 5_000L;
    private static final int FORWARD_BREAK_CREDIT_TTL_TICKS = 60;
    private static final int HIGHWAY_FLOOR_Y = 120;
    private static final int HIGHWAY_RAILING_Y = 121;
    private static final double FALL_SAVE_PAVING_OPERATING_Y = 120.0;
    private static final double FALL_SAVE_DIGGING_OPERATING_Y = 119.0;
    private static final double FALL_SAVE_TRIGGER_DEPTH = 0.25;
    private static final int HIGHWAY_END_SCAN_DISTANCE = 24;
    private static final int HIGHWAY_END_NEAR_THRESHOLD = 10;
    private static final int PAVED_END_REQUIRED_CONSECUTIVE_MISSES = 2;
    private static final int DUG_END_SCAN_STEPS = 20;
    private static final int DUG_END_NETHERRACK_THRESHOLD = 20;
    private static final double PAVED_FLOOR_OBSIDIAN_RATIO = 0.95;
    private static final double PAVED_RAILING_RATIO = 0.95;
    private static final double DUG_EXCAVATED_RATIO = 1.0;
    private static final double DUG_OBSIDIAN_FLOOR_MAX_RATIO = 0.05;
    private static final DateTimeFormatter STATS_SCREENSHOT_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH.mm.ss").withZone(ZoneId.systemDefault());
    private static final SecureRandom STATS_RANDOM = new SecureRandom();

    private boolean suppressThmHwyMonitorSync;

    public enum Floor {
        Replace,
        PlaceMissing
    }

    public enum Rotation {
        None(false, false),
        Mine(true, false),
        Place(false, true),
        Both(true, true);

        public final boolean mine, place;

        Rotation(boolean mine, boolean place) {
            this.mine = mine;
            this.place = place;
        }
    }

    public enum CenterMode {
        Teleport,
        Walk
    }

    public enum BlockadeType {
        Full(6, false),
        FullRoof(6, true),
        Partial(4, false),
        Shulker(3, false);

        public final int columns;
        public final boolean roof;

        BlockadeType(int columns, boolean roof) {
            this.columns = columns;
            this.roof = roof;
        }
    }

    public enum KitbotEChestRestockKit {
        Echest(KitbotFrontend.KitName.Echest),
        Highway(KitbotFrontend.KitName.Highway);

        public final KitbotFrontend.KitName kitName;

        KitbotEChestRestockKit(KitbotFrontend.KitName kitName) {
            this.kitName = kitName;
        }

        @Override
        public String toString() {
            return kitName.toString();
        }
    }

    public enum KitbotPickaxeRestockKit {
        Pickaxe(KitbotFrontend.KitName.Pickaxe),
        Highway(KitbotFrontend.KitName.Highway);

        public final KitbotFrontend.KitName kitName;

        KitbotPickaxeRestockKit(KitbotFrontend.KitName kitName) {
            this.kitName = kitName;
        }

        @Override
        public String toString() {
            return kitName.toString();
        }
    }

    public enum RestockSecondarySourceOrder {
        EnderChestThenKitBot,
        KitBotThenEnderChest
    }

    private enum KitbotStructureBlockType {
        Column,
        Roof
    }

    private enum KitbotCenterWalkResult {
        Reached,
        Walking,
        TimedOut
    }

    public enum AirPlaceMode {
        Never,
        Smart,
        Always
    }

    public enum FoodManagement {
        None("None"),
        AutoEat("Auto Eat"),
        AutoGap("Auto Gap");

        private final String title;

        FoodManagement(String title) {
            this.title = title;
        }

        @Override
        public String toString() {
            return title;
        }
    }

    public enum DesyncWiggleProbeResult {
        Started,
        AlreadyRunning,
        AlreadyUsed,
        Unavailable
    }

    private enum DesyncWiggleReason {
        ForwardPacketDesync,
        EnclosurePlacementDesync
    }

    private static final class KitbotFootprint {
        private final LinkedHashMap<BlockPos, KitbotStructureBlockType> requiredBlocks = new LinkedHashMap<>();
        private final LinkedHashSet<BlockPos> requiredAir = new LinkedHashSet<>();

        private void addRequiredBlock(BlockPos pos, KitbotStructureBlockType type) {
            requiredBlocks.putIfAbsent(pos.toImmutable(), type);
        }

        private void addRequiredAir(BlockPos pos) {
            requiredAir.add(pos.toImmutable());
        }
    }

    private static final class DesyncWiggleProbe {
        private final DesyncWiggleReason reason;
        private final State startState;
        private final State resumeState;
        private final BlockPos targetPos;
        private final String label;
        private final String sourceSummary;
        private final int forwardEpisodeId;
        private final int packetActions;
        private final int distinctTargets;
        private final int windowTicks;
        private final double startX;
        private final double startY;
        private final double startZ;
        private int tick;
        private boolean correctionPacketObserved;
        private String correctionPacketType = "";
        private int correctionEntityId = Integer.MIN_VALUE;

        private DesyncWiggleProbe(
            DesyncWiggleReason reason,
            State startState,
            State resumeState,
            BlockPos targetPos,
            String label,
            String sourceSummary,
            int forwardEpisodeId,
            int packetActions,
            int distinctTargets,
            int windowTicks,
            double startX,
            double startY,
            double startZ
        ) {
            this.reason = reason;
            this.startState = startState;
            this.resumeState = resumeState;
            this.targetPos = targetPos == null ? null : targetPos.toImmutable();
            this.label = label == null ? "" : label;
            this.sourceSummary = sourceSummary == null ? "" : sourceSummary;
            this.forwardEpisodeId = forwardEpisodeId;
            this.packetActions = packetActions;
            this.distinctTargets = distinctTargets;
            this.windowTicks = windowTicks;
            this.startX = startX;
            this.startY = startY;
            this.startZ = startZ;
        }
    }

    private static final class BlockadeBasis {
        private final BlockPos anchor;
        private final BlockPos container;
        private final BlockPos anchorExpand;
        private final BlockPos containerExpand;
        private final BlockPos[] columnSlots;
        private final BlockPos roof0;
        private final BlockPos roof1;
        private final boolean straight;

        private BlockadeBasis(
            BlockPos anchor,
            BlockPos container,
            BlockPos anchorExpand,
            BlockPos containerExpand,
            BlockPos[] columnSlots,
            BlockPos roof0,
            BlockPos roof1,
            boolean straight
        ) {
            this.anchor = anchor;
            this.container = container;
            this.anchorExpand = anchorExpand;
            this.containerExpand = containerExpand;
            this.columnSlots = columnSlots;
            this.roof0 = roof0;
            this.roof1 = roof1;
            this.straight = straight;
        }

        private BlockPos column(int slot) {
            if (slot < 0 || slot >= columnSlots.length) throw new IllegalArgumentException("Unexpected blockade slot: " + slot);
            return columnSlots[slot];
        }
    }

    // Group declaration order is display order (Meteor renders settings groups in insertion
    // order - see Settings.groups/Settings.tick). Kept General-first, then the automation group
    // that matters most up top, then the behaviour groups, with the niche/advanced ones (KitBot
    // Integration, Debugging) and both render groups pushed to the bottom - rendering is pure
    // looks, so it shouldn't sit between the two mode groups people actually configure. Every
    // group must still be declared before the settings that add to it (field init order).
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgAutoTravel = settings.createGroup("Auto Travel Handoff");
    private final SettingGroup sgDigging = settings.createGroup("Digging");
    private final SettingGroup sgPaving = settings.createGroup("Paving");
    private final SettingGroup sgInventory = settings.createGroup("Inventory");
    private final SettingGroup sgNotifies = settings.createGroup("Notifies");
    private final SettingGroup sgStatistics = settings.createGroup("Logging");
    private final SettingGroup sgKitBotIntegration = settings.createGroup("KitBot Integration", false);
    private final SettingGroup sgDebugging = settings.createGroup("Debugging", false);
    private final SettingGroup sgRenderDigging = settings.createGroup("Render Digging");
    private final SettingGroup sgRenderPaving = settings.createGroup("Render Paving");

    public final Setting<Integer> width = sgGeneral.add(new IntSetting.Builder()
        .name("width")
        .description("Width of the highway.")
        .defaultValue(5)
        .range(1, 7)
        .sliderRange(1, 7)
        .build()
    );

    public final Setting<Integer> height = sgGeneral.add(new IntSetting.Builder()
        .name("height")
        .description("Height of the highway.")
        .defaultValue(3)
        .range(2, 5)
        .sliderRange(2, 5)
        .build()
    );

    private final Setting<Floor> floor = sgGeneral.add(new EnumSetting.Builder<Floor>()
        .name("floor")
        .description("What floor placement mode to use.")
        .defaultValue(Floor.Replace)
        .build()
    );

    public final Setting<Boolean> railings = sgGeneral.add(new BoolSetting.Builder()
        .name("railings")
        .description("Builds railings next to the highway.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> cornerBlock = sgGeneral.add(new BoolSetting.Builder()
        .name("corner-support-block")
        .description("Places a support block underneath the railings, to prevent air placing.")
        .defaultValue(false)
        .visible(railings::get)
        .build()
    );

    public final Setting<Boolean> mineAboveRailings = sgGeneral.add(new BoolSetting.Builder()
        .name("mine-above-railings")
        .description("Mines blocks above railings.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Boolean> autoHandoffToTraveler = sgAutoTravel.add(new BoolSetting.Builder()
        .name("auto-handoff-to-traveler")
        .description("Hands control to Highway Traveler once the highway ahead is repaired.")
        .defaultValue(false)
        .build()
    );

    public final Setting<Integer> handoffCleanScanBlocks = sgAutoTravel.add(new IntSetting.Builder()
        .name("handoff-clean-scan")
        .description("How many blocks ahead must be fully repaired before handing off to Highway Traveler.")
        .defaultValue(64)
        .min(8)
        .sliderMax(256)
        .visible(autoHandoffToTraveler::get)
        .build()
    );

    private final Setting<Rotation> rotation = sgGeneral.add(new EnumSetting.Builder<Rotation>()
        .name("rotation")
        .description("Mode of rotation.")
        .defaultValue(Rotation.None)
        .build()
    );

    private final Setting<CenterMode> centerMode = sgGeneral.add(new EnumSetting.Builder<CenterMode>()
        .name("center-mode")
        .description("Method used when HighwayBuilder needs to center before continuing.")
        .defaultValue(CenterMode.Teleport)
        .onChanged(this::onCenterModeChanged)
        .build()
    );

    private final Setting<Boolean> useThmSpeed = sgGeneral.add(new BoolSetting.Builder()
        .name("use-thm-speed")
        .description("Lets HighwayBuilder own horizontal speed while it is running.")
        .defaultValue(false)
        .onChanged(this::onUseThmSpeedChanged)
        .build()
    );

    private final Setting<Double> highwaySpeed = sgGeneral.add(new DoubleSetting.Builder()
        .name("highway-speed")
        .description("Forward and backward HighwayBuilder speed.")
        .defaultValue(5.0)
        .range(1.0, 6.0)
        .sliderRange(1.0, 6.0)
        .visible(useThmSpeed::get)
        .build()
    );

    private final Setting<Boolean> legacyMode = sgGeneral.add(new BoolSetting.Builder()
        .name("legacy-mode")
        .description("Uses the old legacy forward path instead of the new rolling row scheduler.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> kitbotRestock = sgKitBotIntegration.add(new BoolSetting.Builder()
        .name("kitbot-restock")
        .description("Allows restock tasks to order kits from KitBot1.")
        .defaultValue(false)
        .visible(() -> mc.player != null && !ThmMembers.isNovice(mc.player.getName().getString()))
        .build()
    );

    private final Setting<Boolean> kitbotEChestRestock = sgKitBotIntegration.add(new BoolSetting.Builder()
        .name("kitbot-echest-restock")
        .description("Allows KitBot to provide e-chest reserve and obsidian restock supply.")
        .defaultValue(true)
        .visible(kitbotRestock::get)
        .build()
    );

    public final Setting<KitbotEChestRestockKit> kitbotEChestRestockKit = sgKitBotIntegration.add(new EnumSetting.Builder<KitbotEChestRestockKit>()
        .name("kitbot-echest-restock-kit")
        .description("KitBot kit to order for e-chest reserve and obsidian restock supply.")
        .defaultValue(KitbotEChestRestockKit.Echest)
        .visible(() -> kitbotRestock.get() && kitbotEChestRestock.get())
        .build()
    );

    private final Setting<Integer> kitbotEChestRestockAmount = sgKitBotIntegration.add(new IntSetting.Builder()
        .name("kitbot-echest-restock-amount")
        .description("How many e-chest kits to order from KitBot per restock.")
        .defaultValue(4)
        .range(1, 10)
        .sliderRange(1, 10)
        .visible(() -> kitbotRestock.get() && kitbotEChestRestock.get())
        .build()
    );

    private final Setting<Boolean> kitbotPickaxeRestock = sgKitBotIntegration.add(new BoolSetting.Builder()
        .name("kitbot-pickaxe-restock")
        .description("Allows KitBot to provide pickaxe restock supply.")
        .defaultValue(true)
        .visible(kitbotRestock::get)
        .build()
    );

    public final Setting<KitbotPickaxeRestockKit> kitbotPickaxeRestockKit = sgKitBotIntegration.add(new EnumSetting.Builder<KitbotPickaxeRestockKit>()
        .name("kitbot-pickaxe-restock-kit")
        .description("KitBot kit to order for pickaxe restock supply.")
        .defaultValue(KitbotPickaxeRestockKit.Pickaxe)
        .visible(() -> kitbotRestock.get() && kitbotPickaxeRestock.get())
        .build()
    );

    private final Setting<Integer> kitbotPickaxeRestockAmount = sgKitBotIntegration.add(new IntSetting.Builder()
        .name("kitbot-pickaxe-restock-amount")
        .description("How many pickaxe kits to order from KitBot per restock.")
        .defaultValue(4)
        .range(1, 10)
        .sliderRange(1, 10)
        .visible(() -> kitbotRestock.get() && kitbotPickaxeRestock.get())
        .build()
    );

    private final Setting<Boolean> kitbotFoodRestock = sgKitBotIntegration.add(new BoolSetting.Builder()
        .name("kitbot-food-restock")
        .description("Lets KitBot restock food. Forces food to enchanted golden apples.")
        .defaultValue(false)
        .visible(kitbotRestock::get)
        .onChanged(value -> {
            if (value) enforceKitbotFoodRestockFoodType();
        })
        .build()
    );

    private final Setting<Integer> kitbotFoodRestockAmount = sgKitBotIntegration.add(new IntSetting.Builder()
        .name("kitbot-food-restock-amount")
        .description("How many food kits to order from KitBot per restock.")
        .defaultValue(1)
        .range(1, 10)
        .sliderRange(1, 10)
        .visible(() -> kitbotRestock.get() && kitbotFoodRestock.get())
        .build()
    );

    private final Setting<Boolean> kitbotWaitUntilGone = sgKitBotIntegration.add(new BoolSetting.Builder()
        .name("kitbot-wait-until-gone")
        .description("After the kits arrive, wait for KitBot1 to leave before breaking the enclosure.")
        .defaultValue(true)
        .visible(kitbotRestock::get)
        .build()
    );

    private final Setting<Boolean> kitbotCollectDelay = sgKitBotIntegration.add(new BoolSetting.Builder()
        .name("kitbot-collect-delay")
        .description("Wait 2 more seconds before breaking the enclosure, so every dropped kit is picked up.")
        .defaultValue(true)
        .visible(kitbotRestock::get)
        .build()
    );

    private final Setting<RestockSecondarySourceOrder> restockSecondarySourceOrder = sgKitBotIntegration.add(new EnumSetting.Builder<RestockSecondarySourceOrder>()
        .name("restock-secondary-source-order")
        .description("Which external restock source to try first.")
        .defaultValue(RestockSecondarySourceOrder.EnderChestThenKitBot)
        .visible(() -> mc.player != null && !ThmMembers.isNovice(mc.player.getName().getString()))
        .build()
    );

    private final Setting<Boolean> kitbotUpdateOnFinish = sgKitBotIntegration.add(new BoolSetting.Builder()
        .name("kitbot-update-on-finish")
        .description("Sends $update to KitBot1 when finished, waits for the teleport, then disconnects.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> kitbotPeriodicUpdate = sgKitBotIntegration.add(new BoolSetting.Builder()
        .name("kitbot-periodic-update")
        .description("Sends $update to KitBot1 every hour while building.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> disconnectOnToggle = sgGeneral.add(new BoolSetting.Builder()
        .name("disconnect-on-toggle")
        .description("Disconnects when the module turns itself off, e.g. on running out of blocks.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> pauseOnLag = sgGeneral.add(new BoolSetting.Builder()
        .name("pause-on-lag")
        .description("Throttles highway actions based on server TPS and pauses below 10 TPS.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> resumeTPS = sgGeneral.add(new IntSetting.Builder()
        .name("resume-tps")
        .description("Deprecated. TPS throttle now resumes automatically at 10 TPS.")
        .defaultValue(16)
        .range(1, 19)
        .sliderRange(1, 19)
        .visible(() -> false)
        .build()
    );

    private final Setting<Boolean> destroyCrystalTraps = sgGeneral.add(new BoolSetting.Builder()
        .name("destroy-crystal-traps")
        .description("Use a bow to defuse crystal traps safely from a distance. An infinity bow is recommended.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> manageThmHwyMonitor = sgGeneral.add(new BoolSetting.Builder()
        .name("manage-thm-highway-monitor")
        .description("Reduces building drift and auto-aligns you on the highway.")
        .defaultValue(THMUtils.isBaritoneInstalled())
        .onChanged(this::onManageThmHwyMonitorChanged)
        .visible(THMUtils::isBaritoneInstalled)
        .build()
    );

    private final Setting<Boolean> fallSaveAirPlace = sgGeneral.add(new BoolSetting.Builder()
        .name("fall-save-air-place")
        .description("Places a safety block below you when descending below the highway.")
        .defaultValue(false)
        .visible(manageThmHwyMonitor::get)
        .build()
    );

    private final Setting<Integer> fallSaveDistance = sgGeneral.add(new IntSetting.Builder()
        .name("fall-save-distance")
        .description("Vertical distance below your hitbox for fall-save air placement.")
        .defaultValue(3)
        .range(3, 5)
        .sliderRange(3, 5)
        .visible(() -> manageThmHwyMonitor.get() && fallSaveAirPlace.get())
        .build()
    );

    private final Setting<Boolean> manageModuleManager = sgGeneral.add(new BoolSetting.Builder()
        .name("manage-module-manager")
        .description("Automatically enables Module Manager while HighwayBuilder is running.")
        .defaultValue(false)
        .onChanged(value -> {
            if (!isActive()) return;
            if (value) syncModuleManagerOnActivate();
            else syncModuleManagerOnDeactivate();
        })
        .visible(() -> MODULE_MANAGER_INTEGRATION_ENABLED)
        .build()
    );

    private final Setting<Boolean> autosetupModules = sgGeneral.add(new BoolSetting.Builder()
        .name("autosetup-modules")
        .description("Auto-configures Meteor Speed Mine, Reach, Velocity and place range.")
        .defaultValue(true)
        .onChanged(value -> {
            if (!isActive()) return;
            if (value) applyAutosetupModules();
            else syncManagedSpeedMineOwnership();
        })
        .build()
    );

    private final Setting<Boolean> packetMode = sgGeneral.add(new BoolSetting.Builder()
        .name("packet-mode")
        .description("Enables Packet Build and Packet Borer. Leaves them alone if already on.")
        .defaultValue(false)
        .onChanged(value -> { if (value) activatePacketMode(); })
        .build()
    );

    // Digging

    private final Setting<Boolean> instamineBypass = sgDigging.add(new BoolSetting.Builder()
        .name("instamine-bypass")
        .description("Old-style breaking for basalt/blackstone, so fast break only bypasses instamineables.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> doubleMine = sgDigging.add(new BoolSetting.Builder()
        .name("double-mine")
        .description("Mines with a normal and a packet mine at the same time.")
        .defaultValue(true)
        .onChanged(value -> {
            if (!isActive()) return;
            if (!value) releaseActiveDoubleMineRuntime(true);
            syncManagedSpeedMineOwnership();
        })
        .build()
    );

    private final Setting<Boolean> fastBreak = sgDigging.add(new BoolSetting.Builder()
        .name("fast-break")
        .description("Whether to finish breaking blocks faster than normal while double mining.")
        .defaultValue(true)
        .visible(doubleMine::get)
        .build()
    );

    private final Setting<Boolean> speedmine = sgDigging.add(new BoolSetting.Builder()
        .name("speedmine")
        .description("Wether to use the Speedmine module to speed up basalt breaking")
        .defaultValue(false)
        .visible(() -> MANAGED_SPEEDMINE_SETTING_ENABLED && doubleMine.get())
        .onChanged(value -> {
            if (!isActive()) return;
            syncManagedSpeedMineOwnership();
        })
        .build()
    );

    // Settings-sync convenience only - doesn't change HighwayBuilder's own mining logic anywhere.
    private final Setting<Boolean> alwaysUseThmSpeedmine = sgDigging.add(new BoolSetting.Builder()
        .name("always-use-thm-speedmine")
        .description("Keeps the THM Speedmine module (PVP category) active while this runs.")
        .defaultValue(false)
        .onChanged(value -> {
            if (value && Speedmine.INSTANCE != null && !Speedmine.INSTANCE.isActive()) Speedmine.INSTANCE.toggle();
        })
        .build()
    );

    private final Setting<Boolean> thmSpeedmineValidateBlock = sgDigging.add(new BoolSetting.Builder()
        .name("validate-block")
        .description("Sets THM Speedmine's own Validate Break setting.")
        .defaultValue(false)
        .visible(alwaysUseThmSpeedmine::get)
        .onChanged(value -> {
            if (Speedmine.INSTANCE != null) Speedmine.INSTANCE.validateBreak.set(value);
        })
        .build()
    );

    private final Setting<Boolean> dontBreakTools = sgDigging.add(new BoolSetting.Builder()
        .name("dont-break-tools")
        .description("Don't break tools.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> breakDurability = sgDigging.add(new IntSetting.Builder()
        .name("durability-percentage")
        .description("The durability percentage at which to stop using a tool.")
        .defaultValue(2)
        .range(1, 100)
        .sliderRange(1, 100)
        .visible(dontBreakTools::get)
        .build()
    );

    private final Setting<Integer> savePickaxes = sgDigging.add(new IntSetting.Builder()
        .name("save-pickaxes")
        .description("How many pickaxes to keep. Dropping to this count triggers a restock.")
        .defaultValue(1)
        .range(0, 36)
        .sliderRange(0, 36)
        .visible(() -> !dontBreakTools.get())
        .build()
    );

    private final Setting<Integer> restockPickaxesAmount = sgDigging.add(new IntSetting.Builder()
        .name("restock-pickaxes-amount")
        .description("How many pickaxes to pull per pickaxe restock task.")
        .defaultValue(1)
        .range(1, 36)
        .sliderRange(1, 9)
        .visible(() -> !dontBreakTools.get())
        .build()
    );

    private final Setting<Integer> breakDelay = sgDigging.add(new IntSetting.Builder()
        .name("break-delay")
        .description("The delay between breaking blocks.")
        .defaultValue(0)
        .min(0)
        .build()
    );

    private final Setting<Double> blocksPerTick = sgDigging.add(new DoubleSetting.Builder()
        .name("blocks-per-tick")
        .description("Max blocks mined per tick, fractional allowed. Instamineables only.")
        .defaultValue(7)
        .range(1, 30)
        .sliderMax(20)
        .build()
    );

    private final Setting<Double> instamineOverrideBlocksPerTick = sgDigging.add(new DoubleSetting.Builder()
        .name("instamine-override-blocks-per-tick")
        .description("Mining speed for basalt/blackstone-type forward instamine override blocks.")
        .defaultValue(7)
        .range(1, 30)
        .sliderMax(20)
        .visible(() -> false)
        .build()
    );

    private final Setting<Boolean> ignoreSigns = sgDigging.add(new BoolSetting.Builder()
        .name("ignore-signs")
        .description("Ignore breaking signs = preserving history (based).")
        .defaultValue(false)
        .onChanged(value -> updateSignBreakRegex())
        .build()
    );

    private final Setting<Boolean> breakAdvertisementSigns = sgDigging.add(new BoolSetting.Builder()
        .name("break-advertisement-signs")
        .description("Only break signs that look like advertisements/invites.")
        .defaultValue(true)
        .onChanged(value -> updateSignBreakRegex())
        .visible(() -> !ignoreSigns.get())
        .build()
    );

    private final Setting<Boolean> packetBorer = sgDigging.add(new BoolSetting.Builder()
        .name("packet-borer")
        .description("Also spams instant-break packets for the whole highway shape each tick.")
        .defaultValue(false)
        .build()
    );

    // Paving

    public final Setting<List<Block>> blocksToPlace = sgPaving.add(new BlockListSetting.Builder()
        .name("blocks-to-place")
        .description("Blocks it is allowed to place.")
        .defaultValue(Blocks.OBSIDIAN)
        .filter(block -> Block.isShapeFullCube(block.getDefaultState().getCollisionShape(EmptyBlockView.INSTANCE, BlockPos.ORIGIN)))
        .build()
    );

    private final Setting<Double> placeRange = sgPaving.add(new DoubleSetting.Builder()
        .name("place-range")
        .description("The maximum distance at which you can place blocks.")
        .defaultValue(5.2)
        .sliderMax(5.5)
        .build()
    );

    private final Setting<Integer> placeDelay = sgPaving.add(new IntSetting.Builder()
        .name("place-delay")
        .description("The delay between placing blocks.")
        .defaultValue(0)
        .min(0)
        .build()
    );

    private final Setting<Boolean> tpsSafetyEnclosure = sgPaving.add(new BoolSetting.Builder()
        .name("tps-safety-enclosure")
        .description("After TPS settling, builds a small safety enclosure during low/unknown TPS pauses.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> packetBuild = sgPaving.add(new BoolSetting.Builder()
        .name("packet-build")
        .description("Sends placement packets directly. Enables Packet Limiter on activation.")
        .defaultValue(false)
        .build()
    );

    private final Setting<AirPlaceMode> packetBuildAirPlace = sgPaving.add(new EnumSetting.Builder<AirPlaceMode>()
        .name("air-place-mode")
        .description("When to air-place in Packet Build mode: never, only when needed, or always.")
        .defaultValue(AirPlaceMode.Smart)
        .visible(packetBuild::get)
        .build()
    );

    private final Setting<Boolean> packetBuildLookahead = sgPaving.add(new BoolSetting.Builder()
        .name("packet-build-lookahead")
        .description("Also place blocks from upcoming rows in the same tick.")
        .defaultValue(true)
        .visible(packetBuild::get)
        .build()
    );

    private final Setting<Boolean> silentForwardPlaceSwap = sgPaving.add(new BoolSetting.Builder()
        .name("silent-forward-place-swap")
        .description("Silently swaps to scheduler forward placement blocks, then restores your selected slot.")
        .defaultValue(true)
        .visible(() -> !legacyMode.get())
        .build()
    );

    private final Setting<Boolean> silentForwardToolSwap = sgPaving.add(new BoolSetting.Builder()
        .name("silent-forward-tool-swap")
        .description("Silently swaps to scheduler forward mining tools, then restores your selected slot.")
        .defaultValue(true)
        .visible(() -> !legacyMode.get())
        .build()
    );

    private final Setting<Boolean> checkBehind = sgGeneral.add(new BoolSetting.Builder()
        .name("check-behind")
        .description("Checks and repairs missing highway floor and railings behind the player.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> advertise = sgGeneral.add(new BoolSetting.Builder()
        .name("advertise")
        .description("Sends THM Advertisements in chat")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> advertiseIntervalMinutes = sgGeneral.add(new IntSetting.Builder()
        .name("advertise-interval")
        .description("Advertisment delay between messages in minutes")
        .defaultValue(5)
        .range(1, 60)
        .sliderRange(1, 15)
        .visible(advertise::get)
        .build()
    );

    private final Setting<Boolean> debugLog = sgDebugging.add(new BoolSetting.Builder()
        .name("debug")
        .description("Logs state transitions and movement input for debugging.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> forwardSchedulerDebugLog = sgDebugging.add(new BoolSetting.Builder()
        .name("forward-scheduler-debug")
        .description("Logs active row, queue, boundary, and actionability details for the forward scheduler.")
        .defaultValue(false)
        .visible(() -> !legacyMode.get())
        .build()
    );

    private final Setting<Boolean> statisticsDebugLog = sgDebugging.add(new BoolSetting.Builder()
        .name("statistics-debug")
        .description("Logs why each mine/place action was counted or skipped in the stats.")
        .defaultValue(false)
        .build()
    );

    public final Setting<Double> placementsPerTick = sgPaving.add(new DoubleSetting.Builder()
        .name("placements-per-tick")
        .description("Max blocks placed per tick, fractional allowed.")
        .defaultValue(1)
        .range(0.1, 100)
        .sliderRange(0.1, 10)
        .decimalPlaces(1)
        .build()
    );

    // Inventory

    private boolean clampingFoodTypes;

    private final Setting<List<Item>> protectedItems = sgInventory.add(new ItemListSetting.Builder()
        .name("protected-items")
        .description("Items trash cleanup never throws out. Everything else is trash.")
        .defaultValue(
            Items.ENDER_CHEST, Items.OBSIDIAN, Items.NETHERITE_PICKAXE, Items.NETHERITE_SWORD,
            Items.NETHERITE_SHOVEL, Items.NETHERITE_AXE, Items.ELYTRA, Items.TOTEM_OF_UNDYING,
            Items.ENCHANTED_GOLDEN_APPLE, Items.EXPERIENCE_BOTTLE
        )
        .build()
    );

    private final Setting<Boolean> foodRestock = sgInventory.add(new BoolSetting.Builder()
        .name("food-restock")
        .description("Restocks one configured food stack when your valid food count drops to the saved amount.")
        .defaultValue(false)
        .build()
    );

    private final Setting<FoodManagement> foodManagement = sgInventory.add(new EnumSetting.Builder<FoodManagement>()
        .name("food-management")
        .description("Ensures the selected Meteor food module is enabled while HighwayBuilder is running.")
        .defaultValue(FoodManagement.None)
        .build()
    );

    private final Setting<ItemPriorityList> foodTypes = sgInventory.add(new GenericSetting.Builder<ItemPriorityList>()
        .name("food-types")
        .description("Which food items count as restock food. Selection order sets priority — the item on top is preferred.")
        .defaultValue(new ItemPriorityList(item -> item.getDefaultStack().contains(DataComponentTypes.FOOD)))
        .visible(() -> foodRestock.get() || foodManagement.get() != FoodManagement.None)
        .onChanged(this::handleFoodTypesChanged)
        .build()
    );

    private final Setting<Integer> saveFood = sgInventory.add(new IntSetting.Builder()
        .name("save-food")
        .description("Restock food at or below this count. Keep it under half a stack.")
        .defaultValue(16)
        .range(1, 32)
        .sliderRange(1, 32)
        .visible(foodRestock::get)
        .build()
    );

    private final Setting<Integer> keepTrashBlockStacks = sgInventory.add(new IntSetting.Builder()
        .name("keep-trash-block-stacks")
        .description("How many trash block stacks to keep before dropping the rest.")
        .defaultValue(1)
        .range(1, 10)
        .sliderRange(1, 10)
        .build()
    );

    private final Setting<Integer> inventoryDelay = sgInventory.add(new IntSetting.Builder()
        .name("inventory-delay")
        .description("Delay in ticks on inventory interactions.")
        .defaultValue(3)
        .min(0)
        .build()
    );

    private final Setting<Boolean> ejectUselessShulkers = sgInventory.add(new BoolSetting.Builder()
        .name("eject-useless-shulkers")
        .description("Ejects useless shulkers. Ones holding anything needed are kept.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> searchEnderChest = sgInventory.add(new BoolSetting.Builder()
        .name("search-ender-chest")
        .description("Also searches your ender chest for items.")
        .defaultValue(false)
        .build()
    );

    public final Setting<Boolean> doublechest = sgInventory.add(new BoolSetting.Builder()
        .name("double-chest")
        .description("Places a second ender chest to the right for a 54-slot double.")
        .defaultValue(true)
        .visible(searchEnderChest::get)
        .build()
    );

    private final Setting<Boolean> searchShulkers = sgInventory.add(new BoolSetting.Builder()
        .name("search-shulkers")
        .description("Searches through shulkers to find items to use.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Boolean> hotbarmanager = sgInventory.add(new BoolSetting.Builder()
        .name("Manage-hotbar")
        .description("Automatically sorts the Hotbar.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Boolean> antidrop = sgInventory.add(new BoolSetting.Builder()
        .name("Anti-drop")
        .description("Stops you from dropping needed items")
        .defaultValue(false)
        .build()
    );

    public final Setting<Boolean> noSwapLoadout = sgInventory.add(new BoolSetting.Builder()
        .name("offhand-build")
        .description("Mines with the pickaxe in hand and places obsidian from the offhand, taking over AutoTotem to swap in a totem there at low health or when an enemy is nearby.")
        .defaultValue(false)
        .onChanged(v -> syncNoSwapAutoTotem())
        .build()
    );

    private final Setting<Integer> noSwapTotemHealth = sgInventory.add(new IntSetting.Builder()
        .name("offhand-totem-health")
        .description("Health at or below which the offhand holds a totem instead of obsidian.")
        .defaultValue(10)
        .range(0, 36)
        .sliderMax(36)
        .visible(noSwapLoadout::get)
        .build()
    );

    private final Setting<Integer> minEmpty = sgInventory.add(new IntSetting.Builder()
        .name("minimum-empty-slots")
        .description("The minimum amount of empty slots you want left after mining obsidian.")
        .defaultValue(1)
        .sliderRange(0, 9)
        .min(0)
        .build()
    );

    private final Setting<Boolean> mineEnderChests = sgInventory.add(new BoolSetting.Builder()
        .name("mine-ender-chests")
        .description("Mines ender chests for obsidian.")
        .defaultValue(true)
        .build()
    );

    private final Setting<BlockadeType> blockadeType = sgInventory.add(new EnumSetting.Builder<BlockadeType>()
        .name("echest-blockade-type")
        .description("Locked to FullRoof while KitBot shares the normal blockade geometry.")
        .defaultValue(BlockadeType.FullRoof)
        .visible(() -> false)
        .build()
    );

    public final Setting<Integer> saveEchests = sgInventory.add(new IntSetting.Builder()
        .name("save-ender-chests")
        .description("How many ender chests to keep in reserve. Falling below queues a restock.")
        .defaultValue(4)
        .range(4, 64)
        .sliderRange(4, 64)
        .build()
    );

    private final Setting<Boolean> newBreaking = sgInventory.add(new BoolSetting.Builder()
        .name("new-breaking")
        .description("Breaks ender chests with THM Speedmine instead of the legacy method.")
        .defaultValue(true)
        .visible(mineEnderChests::get)
        .build()
    );

    private final Setting<Boolean> rebreakEchests = sgInventory.add(new BoolSetting.Builder()
        .name("instantly-rebreak-echests")
        .description("Uses the legacy instant-rebreak packet method after placing an ender chest.")
        .defaultValue(true)
        .visible(() -> mineEnderChests.get() && !newBreaking.get())
        .build()
    );

    private final Setting<Integer> rebreakTimer = sgInventory.add(new IntSetting.Builder()
        .name("rebreak-delay")
        .description("Delay in ticks between legacy instant-rebreak attempts.")
        .defaultValue(0)
        .sliderMax(20)
        .visible(() -> mineEnderChests.get() && !newBreaking.get() && rebreakEchests.get())
        .build()
    );

    private final Setting<Boolean> useBreakSpeedMultiplier = sgInventory.add(new BoolSetting.Builder()
        .name("use-break-speed-multiplier")
        .description("Boosts Timer while mining ender chests, then restores it.")
        .defaultValue(true)
        .visible(mineEnderChests::get)
        .build()
    );

    private final Setting<Double> breakSpeedMultiplier = sgInventory.add(new DoubleSetting.Builder()
        .name("break-speed-multiplier")
        .description("Break Speed Multiplier")
        .defaultValue(1.5)
        .range(1, 3)
        .sliderRange(1, 3)
        .visible(() -> mineEnderChests.get() && useBreakSpeedMultiplier.get())
        .build()
    );

    private final Setting<Boolean> silentRebreakSwap = sgInventory.add(new BoolSetting.Builder()
        .name("silent-rebreak-swap")
        .description("Silently swaps for rebreak packets and ender chest placement.")
        .defaultValue(true)
        .visible(() -> mineEnderChests.get() && (newBreaking.get() || rebreakEchests.get()))
        .build()
    );

    // Render Digging

    private final Setting<Boolean> renderMine = sgRenderDigging.add(new BoolSetting.Builder()
        .name("render-blocks-to-mine")
        .description("Render blocks to be mined.")
        .defaultValue(true)
        .build()
    );

    private final Setting<ShapeMode> renderMineShape = sgRenderDigging.add(new EnumSetting.Builder<ShapeMode>()
        .name("blocks-to-mine-shape-mode")
        .description("How the blocks to be mined are rendered.")
        .defaultValue(ShapeMode.Both)
        .build()
    );

    private final Setting<SettingColor> renderMineSideColor = sgRenderDigging.add(new ColorSetting.Builder()
        .name("blocks-to-mine-side-color")
        .description("Color of blocks to be mined.")
        .defaultValue(new SettingColor(225, 25, 25, 25))
        .build()
    );

    private final Setting<SettingColor> renderMineLineColor = sgRenderDigging.add(new ColorSetting.Builder()
        .name("blocks-to-mine-line-color")
        .description("Color of blocks to be mined.")
        .defaultValue(new SettingColor(225, 25, 25, 255))
        .build()
    );

    // Render Paving

    private final Setting<Boolean> renderPlace = sgRenderPaving.add(new BoolSetting.Builder()
        .name("render-blocks-to-place")
        .description("Render blocks to be placed.")
        .defaultValue(true)
        .build()
    );

    private final Setting<ShapeMode> renderPlaceShape = sgRenderPaving.add(new EnumSetting.Builder<ShapeMode>()
        .name("blocks-to-place-shape-mode")
        .description("How the blocks to be placed are rendered.")
        .defaultValue(ShapeMode.Both)
        .build()
    );

    private final Setting<SettingColor> renderPlaceSideColor = sgRenderPaving.add(new ColorSetting.Builder()
        .name("blocks-to-place-side-color")
        .description("Color of blocks to be placed.")
        .defaultValue(new SettingColor(25, 25, 225, 25))
        .build()
    );

    private final Setting<SettingColor> renderPlaceLineColor = sgRenderPaving.add(new ColorSetting.Builder()
        .name("blocks-to-place-line-color")
        .description("Color of blocks to be placed.")
        .defaultValue(new SettingColor(25, 25, 225, 255))
        .build()
    );

    // Statistics

    private final Setting<Boolean> printStatistics = sgStatistics.add(new BoolSetting.Builder()
        .name("print-statistics")
        .description("Prints statistics in chat when disabling Highway Builder.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> autoScreenshotStatistics = sgStatistics.add(new BoolSetting.Builder()
        .name("auto-screenshot-statistics")
        .description("Captures a proof screenshot shortly after Highway Builder prints its statistics.")
        .defaultValue(false)
        .visible(printStatistics::get)
        .build()
    );

    private final Setting<Boolean> restockDebugLog = sgStatistics.add(new BoolSetting.Builder()
        .name("restock-debug-log")
        .description("Prints blockade and restock diagnostics.")
        .defaultValue(false)
        .build()
    );


    private final Setting<Boolean> statuslog = sgStatistics.add(new BoolSetting.Builder()
        .name("Send-Status")
        .description("Sends the status every 5 min (Digging/Paving,Axis,Name,hash)")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> sendStatisticsWebhhok = sgStatistics.add(new BoolSetting.Builder()
        .name("sends-statistics(Webhook)")
        .description("Sends Highway Builder statistics to a webhook when the module is disabled.")
        .defaultValue(false)
        .visible(printStatistics::get)
        .build()
    );
    private final Setting<WebhookContent> webhookContent = sgStatistics.add(new EnumSetting.Builder<WebhookContent>()
        .name("webhook-content")
        .description("What the statistics webhook sends: the text stats, the proof screenshot, or both in one request.")
        .defaultValue(WebhookContent.Text)
        .visible(() -> printStatistics.get() && sendStatisticsWebhhok.get())
        .build()
    );
    private final Setting<String> encryptedWebhook = sgStatistics.add(new StringSetting.Builder()
        .name("webhook")
        .description("Webhook URL used to receive statistics.")
        .defaultValue("MyWebhookInHere")
        .visible(() -> printStatistics.get() && sendStatisticsWebhhok.get())
        .build()
    );
    private final Setting<Boolean> sendStatisticsapi = sgStatistics.add(new BoolSetting.Builder()
        .name("sends-statistics(API)")
        .description("Sends statistics to a Api when disabling Highway Builder.")
        .defaultValue(false)
        .visible(printStatistics::get)
        .build()
    );

    private final Setting<Boolean> desktopNotifies = sgNotifies.add(new BoolSetting.Builder()
        .name("desktop-notifies")
        .description("Sends desktop notifications while Highway Builder is running.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> notifyDisconnect = sgNotifies.add(new BoolSetting.Builder()
        .name("disconnect")
        .description("Notify when Highway Builder disconnects you.")
        .defaultValue(true)
        .visible(desktopNotifies::get)
        .build()
    );

    private final Setting<Boolean> notifyRestockIssues = sgNotifies.add(new BoolSetting.Builder()
        .name("restock-issues")
        .description("Notify when restocking fails (materials, slots, or restock container issues).")
        .defaultValue(true)
        .visible(desktopNotifies::get)
        .build()
    );

    private final Setting<Boolean> notifyOutOfBlocks = sgNotifies.add(new BoolSetting.Builder()
        .name("out-of-blocks")
        .description("Notify when there are no placeable blocks left.")
        .defaultValue(true)
        .visible(desktopNotifies::get)
        .build()
    );

    private final Setting<Boolean> notifyPickaxeShortage = sgNotifies.add(new BoolSetting.Builder()
        .name("pickaxe-shortage")
        .description("Notify when there are not enough pickaxes to continue.")
        .defaultValue(true)
        .visible(desktopNotifies::get)
        .build()
    );

    public final Setting<Boolean> togglePerspective = sgGeneral.add(new BoolSetting.Builder()
        .name("toggle-perspective")
        .description("Switches to third person while active, then restores your perspective.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Boolean> enablehud = sgGeneral.add(new BoolSetting.Builder()
        .name("toggle-hud")
        .description("Toggles the hud")
        .defaultValue(true)
        .build()
    );
    public HorizontalDirection dir, leftDir, rightDir;
    private String activationWorkingDirectionSource = "unknown";

    private Input prevInput;
    private CustomPlayerInput input;

    private State state, lastState;
    private IBlockPosProvider blockPosProvider;

    public Vec3d start;
    public int blocksBroken, blocksPlaced;
    private final MBlockPos lastBreakingPos = new MBlockPos();
    private boolean displayInfo;
    private boolean suspended = true, inventory = true;
    private String lastTickDebugMessage;
    private long lastTickDebugAtMs;
    private int placeTimer, breakTimer, count, syncId, statusLogTimer;
    private int forwardMineCount, forwardPlaceCount;
    private int forwardLatchedPlaceSlot = -1;
    private int forwardLatchedMineSlot = -1;
    private int mineActionsThisTick;
    private double mineFractionCarry;
    private int borerPackets;
    private int placeActionsThisTick;
    private double placeFractionCarry;
    private int tpsThrottleLastSampleAge = Integer.MIN_VALUE;
    private double tpsThrottleSampledTps = TPS_THROTTLE_NORMAL;
    private double tpsThrottleAdjustedBlocksPerTick = 1.0;
    private double tpsThrottleAdjustedPlacementsPerTick = 1.0;
    private boolean tpsThrottlePaused;
    private TpsThrottlePauseReason tpsThrottlePauseReason = TpsThrottlePauseReason.None;
    private long tpsThrottleSettleUntilMs;
    private long nextTpsThrottlePauseReasonLogAtMs;
    private boolean safetyPlacedThisTick;
    private boolean disabledAutoTotem; // we turned Meteor AutoTotem off for no-swap-loadout; re-enable it when we're done
    private boolean tpsSafetyEnclosureActive;
    private boolean tpsSafetyEnclosureTriggeredThisEpisode;
    private boolean tpsSafetyCenterOwned;
    private BlockPos tpsSafetyEnclosureAnchor;
    private final ArrayDeque<BlockPos> tpsSafetyEnclosureTargets = new ArrayDeque<>();
    private int enclosurePlaceCreditTenths;
    private final Set<BlockPos> countedBrokenForwardPositions = new HashSet<>();
    private final Set<BlockPos> countedPlacedForwardPositions = new HashSet<>();
    private final Map<BlockPos, ForwardBreakStatCredit> pendingForwardBreakCredits = new HashMap<>();
    private boolean forwardSchedulerDebugFileErrorLogged;
    private boolean thmDebugFileErrorLogged;
    private final RestockTask restockTask = new RestockTask(this);
    private final RestockWatchdog restockWatchdog = new RestockWatchdog(this);
    private final ForwardSchedulerRuntime forwardSchedulerRuntime = new ForwardSchedulerRuntime();
    private int invalidRestockRecoveryRetries;
    private boolean invalidRestockRecoveryPending;
    private boolean kitbotUpdateOnFinishActive;
    private long kitbotUpdateOnFinishStartTick;
    private boolean kitbotUpdateOnFinishTpAccepted;
    private boolean kitbotPeriodicUpdateActive;
    private long kitbotPeriodicUpdateStartTick;
    private boolean kitbotPeriodicUpdateTpAccepted;
    private boolean kitbotPeriodicUpdatePending;
    private long kitbotPeriodicUpdateNextTick;
    private boolean kitbotOrderInFlight;
    private boolean kitbotOrderFrontendSucceeded;
    private boolean kitbotEnclosureActive;
    private boolean kitbotEnclosureRestorePending;
    private boolean kitbotReturnAnchorSaved;
    private boolean kitbotSeenNearby;
    private int kitbotDepartureWaitUntilAge;
    private int kitbotCollectUntilAge;
    private double kitbotReturnX, kitbotReturnY, kitbotReturnZ;
    private float kitbotReturnYaw;
    private final BlockPos.Mutable kitbotAnchorPos = new BlockPos.Mutable();
    private int kitbotOrderBaselineShulkerCount;
    private int kitbotOrderExpectedShulkerGain;
    private int kitbotOrderSentAtAge;
    private int kitbotOrderAcceptedAttempts;
    private int kitbotOrderLastObservedMatchingShulkerCount;
    private int kitbotPartialDeliveryGraceUntilAge;
    private long kitbotNextSubmitAtMs;
    private long kitbotInventoryProofDeadlineMs;
    private boolean kitbotOrderCenterVerificationActive;
    private boolean kitbotOrderCenterVerified;
    private int kitbotOrderCenterVerificationTicks;
    private int kitbotOrderCenterVerificationFailures;
    private int kitbotOrderCenterVerificationRecenterTicks;
    private int kitbotOrderCenterVerificationSettleTicks;
    private boolean kitbotOrderCenterVerificationRecentered;
    private double kitbotOrderCenterVerificationTargetX = Double.NaN;
    private double kitbotOrderCenterVerificationTargetZ = Double.NaN;
    private double kitbotOrderCenterVerificationDistance = Double.POSITIVE_INFINITY;
    private final KitbotFrontend.LifecycleListener kitbotRestockLifecycleListener = this::onKitbotRestockLifecycle;
    private boolean kitbotOrderResumeAfterCenter;
    private BlockPos pendingCenterTargetBlock;
    private BlockPos activeCenterTargetBlock;
    private int centerTeleportInvalidLogCooldownTicks;
    private boolean centerTeleportBlockedForMonitor;
    private int centerTeleportBlockedTicks;
    private BlockPos centerTeleportBlockedTarget;
    private String centerTeleportBlockedReason = "";
    private State pendingEnclosurePlacementConfirmState;
    private BlockPos pendingEnclosurePlacementConfirmPos;
    private BlockPos pendingEnclosurePlacementCenterTarget;
    private KitbotStructureBlockType pendingEnclosurePlacementKitbotType;
    private String pendingEnclosurePlacementLabel = "";
    private int pendingEnclosurePlacementAttempts;
    private int pendingEnclosurePlacementConfirmWaitTicks;
    private boolean pendingEnclosurePlacementRecentered;
    private BlockPos pendingEnclosurePlacementPacketTarget;
    private int pendingEnclosurePlacementInteractPackets;
    private int pendingEnclosurePlacementFirstPacketAge;
    private int pendingEnclosurePlacementLastPacketAge;
    private BlockPos enclosureDesyncWiggleUsedTarget;
    private DesyncWiggleProbe desyncWiggleProbe;
    private int lastForwardDesyncWiggleEpisodeId = Integer.MIN_VALUE;
    private CenterSpeedSnapshot centerSpeedSnapshot;
    private boolean centerSpeedSnapshotOwned;
    private boolean centerSpeedOverrideActive;
    private boolean centerSpeedMonitorRecoveryOwned;
    private boolean centerSpeedRestorePending;
    private int centerSpeedRestoreRetryTicks;
    private String centerSpeedLastReason = "";
    private MeteorSpeedSnapshot thmSpeedSnapshot;
    private boolean thmSpeedOwnershipActive;
    private boolean thmSpeedRestorePending;
    private boolean thmSpeedSnapshotDeletePending;
    private boolean thmSpeedReconnectSuppressed;
    private boolean centerTeleportSlowdownActive;
    private boolean kitbotCenteringActive;
    private int kitbotCenteringTicks;
    private double kitbotCenteringDistance = Double.POSITIVE_INFINITY;
    private double kitbotCenteringTargetX = Double.NaN;
    private double kitbotCenteringTargetZ = Double.NaN;
    private String kitbotCenteringReason = "";
    private boolean forwardDriftTargetValid;
    private double forwardDriftTargetLateral;
    private boolean forwardDriftRecoveryActive;
    private EChestBreakSpeedSnapshot eChestBreakSpeedSnapshot;
    private boolean eChestBreakSpeedSnapshotOwned;
    private ReconnectBaselineLease reconnectBaselineLease;
    private boolean reconnectBaselineRestoreInProgress;
    private boolean reconnectFailureDeactivateArmed;
    private boolean autoLogTerminalDeactivateArmed;
    private ReconnectResumeContext reconnectResumeContext;
    // travelHandoffDeactivateArmed is declared near the Auto Travel Handoff onTick check (its
    // javadoc lives there); it's used in onDeactivate alongside these other deactivate-mode flags.
    private long lastReconnectResumeFailureCycleId;
    private String lastReconnectResumeFailureReason = "";
    private String lastReconnectResumeSelectedDirectionName = "";
    private String lastReconnectResumeCachedDirectionName = "";
    private Field timerOverrideField;
    private boolean timerOverrideFieldInitialized;
    private boolean timerOverrideReflectionFailureLogged;
    private Field eChestMemoryKnownField;
    private boolean eChestMemoryKnownFieldInitialized;
    private boolean eChestMemoryReflectionFailureLogged;
    private String activeStatsSessionId;
    private long activeStatsGeneration;
    private StatsArtifactSnapshot statsCacheSnapshot;
    private StatsArtifactIdentity loadedStatsArtifactIdentity;
    private final Set<String> consumedStatsArtifactKeys = new HashSet<>();
    private RetiredStatsReportSnapshot retiredStatsReportSnapshot;
    private boolean resumeStatsSessionOnNextActivate;
    private boolean monitorPauseDeactivateArmed;
    private long nextStatsCheckpointAtMs;
    private long nextStatsStorageRetryAtMs;
    private boolean statsSessionDirty;
    private boolean statsSessionTerminalOrFinalizing;
    private boolean memoryRetryMode;
    private final AtomicLong statsScreenshotSequence = new AtomicLong();
    private volatile PendingWebhookStats pendingWebhookStats;
    private String lastPrintedStatsSessionId;
    private SpeedMineSettingsSnapshot speedMineSettingsSnapshot;
    private boolean paketLimiterActivatedByBuilder = false;
    private boolean previousPauseOnLostFocus;
    private boolean pauseOnLostFocusChanged;
    private boolean pauseOnLostFocusForcedOff;
    private boolean executionPausedByServerState;
    private boolean offMainEpisodeCheckpointed;
    private boolean mainServerResumeGateActive;
    private long mainServerResumeReadyAtMs;
    private boolean mainServerResumeDelayLogged;
    private ServerState mainServerResumeSourceState = ServerState.UNKNOWN;
    private ServerState lastServerStatePauseLogState;
    private long nextServerStatePauseLogAtMs;
    private int forwardSchedulerLastDebugAge = -1;
    private ServerState lastCommittedServerState = ServerState.UNKNOWN;
    private Perspective previousPerspective;
    private EatingPauseOwner eatingPauseOwner = EatingPauseOwner.NONE;
    private EatingRetryPhase eatingRetryPhase = EatingRetryPhase.NONE;
    private long eatingPauseLastProgressAtMs;
    private long eatingPauseUseStartedAtMs;
    private int eatingPauseRetryAttempts;
    private int eatingHotbarSwapRecoveryAttempts;
    private int eatingPauseLastUseTime;
    private int eatingPauseLastRelevantItemCount = -1;
    private int eatingPauseSettleTicks;
    private long eatingRetryPhaseDeadlineAtMs;
    private long eatingPauseRecoveryToken;
    private boolean eatingPauseFullToggleRecoveryAttempted;
    private boolean perspectiveChanged;
    private final ArrayList<EndCrystalEntity> ignoreCrystals = new ArrayList<>();
    public boolean drawingBow;
    public DoubleMineBlock normalMining, packetMining;
    private final LongOpenHashSet renderPosSet = new LongOpenHashSet();
    final LongOpenHashSet placeTrail = new LongOpenHashSet();

    private int debugStateLastAge = -1;
    private List<Pattern> signBreakPatterns = Collections.emptyList();
    private int nextAdvertisementIndex;
    private long nextAdvertisementAtTick;
    private static final String KITBOT_NAME = "KitBot1";
    private static final double KITBOT_NEARBY_DISTANCE = 30.0;
    private static final int KITBOT_COLLECT_DELAY_TICKS = 40; // 2s
    // Cap on kitbot-wait-until-gone, so a bot that parks next to you can't stall the restock forever.
    private static final int KITBOT_DEPARTURE_WAIT_TICKS = 600; // 30s
    private static final long KITBOT_PERIODIC_UPDATE_INTERVAL_TICKS = 72000L; // 1 hour at 20 tps
    private static final double CENTER_SPEED_OVERRIDE = 0.6;
    private static final int CENTER_SPEED_RESTORE_RETRY_WINDOW_TICKS = 60;
    private static final double CENTER_TELEPORT_MAX_HORIZONTAL_DISTANCE = 4.0;
    private static final double CENTER_TELEPORT_SNAP_RADIUS = 0.25;
    private static final int CENTER_TELEPORT_INVALID_LOG_COOLDOWN_TICKS = 40;
    private static final String[] THMAdvertisements = {
        "Want to get rewarded with kits for building highways?! Join our discord: https://discord.gg/thm",
        "Only working Kitbot right now. Join: https://discord.gg/thm",
        "Need to get to the end of a highway? Join our Discord to use this bot! https://discord.gg/thm",
        "Lowkey yall should join the THM because we bouta do some crazy. https://discord.gg/thm",
        "Join now to be part of history! https://discord.gg/thm",
        "We have our own Kitbot! join https://discord.gg/thm",
        "Fastest growing server! Join https://discord.gg/thm",
        "Doing Highway work for free? Join https://discord.gg/thm and get kits for your work",
        "Let the Highways shine! join https://discord.gg/thm",
        "Help the community and get rewarded! https://discord.gg/thm",
        "Applications are open! join now https://discord.gg/thm",
        "Wanna build highways and earn rewards? Join THM https://discord.gg/thm",
        "We are recruiting! Apply now https://discord.gg/thm",
        "Teleport to highway ends easily with our bot! https://discord.gg/thm",
        "Skip the long walk to highway ends join https://discord.gg/thm",
        "Need a TP to the end of a highway? Join our Discord https://discord.gg/thm",
        "Highway grind too long? Use our TP bot via https://discord.gg/thm",
        "Reach highway ends faster than ever join https://discord.gg/thm",
        "Don’t walk for hours, TP to the highway end! https://discord.gg/thm",
        "Highway ends are just one command away. Join https://discord.gg/thm",
        "Want quick access to highway ends? Join now https://discord.gg/thm",
        "Travel smarter, not harder. TP to highway ends https://discord.gg/thm",
        "Why walk when you can TP? Join https://discord.gg/thm",
        "Get to the end faster and get rewarded https://discord.gg/thm",
        "Work on highways and get kits https://discord.gg/thm",
        "Join THM for fast highway travel and rewards https://discord.gg/thm",
        "Your shortcut to every highway end starts here https://discord.gg/thm",
        "Active community, real rewards. Join https://discord.gg/thm",
        "Don’t miss out. Everyone’s joining https://discord.gg/thm",
        "Earn rewards just for playing smart https://discord.gg/thm",
        "This is where the real players are https://discord.gg/thm"
    };
    private static final String[] ADVERTISEMENT_SIGN_REGEXES = {
        "invite",
        "discord\\.gg",
        "discord\\.com/invite",
        "dsc\\.gg",
        "advertis",
        "discord",
        "dsc",
        "Join",
        "On Top",
    };

    private record CenterSpeedSnapshot(
        String speedModeName,
        double vanillaSpeed,
        double ncpSpeed,
        boolean ncpSpeedLimit,
        double timer,
        boolean inLiquids,
        boolean whenSneaking,
        boolean vanillaOnGround,
        boolean wasActive,
        boolean timerWasActive
    ) {}

    private record MeteorSpeedSnapshot(
        String speedModeName,
        double vanillaSpeed,
        double ncpSpeed,
        boolean ncpSpeedLimit,
        double timer,
        boolean inLiquids,
        boolean whenSneaking,
        boolean vanillaOnGround,
        boolean wasActive
    ) {}

    private record EChestBreakSpeedSnapshot(
        boolean timerWasActive,
        double timerOverrideValue,
        boolean speedWasActive,
        double speedTimerValue
    ) {}

    private enum ReconnectBaselineLeaseState {
        CAPTURED,
        INVALIDATED,
        CONSUMED
    }

    private record ReconnectBaselinePayload(
        String workingDirectionName,
        String speedModeName,
        double vanillaSpeed,
        double ncpSpeed,
        boolean ncpSpeedLimit,
        double timer,
        boolean inLiquids,
        boolean whenSneaking,
        boolean vanillaOnGround,
        boolean speedWasActive,
        boolean timerWasActive,
        double timerEffectiveMultiplier,
        boolean timerOverrideActive,
        double timerOverrideValue
    ) {}

    private record ReconnectBaselineLease(
        long generation,
        ReconnectBaselineLeaseState state,
        ReconnectBaselinePayload payload
    ) {}

    private record ReconnectResumeContext(
        HorizontalDirection direction,
        long generation
    ) {}

    private record SpeedMineSettingsSnapshot(
        boolean wasActive,
        SpeedMine.Mode mode,
        SpeedMine.ListMode blocksFilter,
        List<Block> blocks,
        boolean instamine,
        boolean grimBypass
    ) {}

    private enum StatsArtifactKind {
        CANONICAL,
        FINALIZATION,
        SHADOW
    }

    private record StatsArtifactSnapshot(
        StatsArtifactKind kind,
        String sessionId,
        long generation,
        StatsSessionState state,
        boolean resumeAllowed,
        String workingDirectionName,
        double startX,
        double startY,
        double startZ,
        int blocksBroken,
        int blocksPlaced,
        boolean displayInfo,
        long lastCheckpointAt,
        long printedAt,
        boolean printedToChat,
        boolean webhookSendCommitted,
        boolean apiSendCommitted,
        String finalizationReason,
        long reconnectCycleId,
        ReconnectBaselinePayload reconnectBaselinePayload
    ) {}

    public record ReconnectResumeResult(
        boolean success,
        String reason
    ) {
        private static ReconnectResumeResult ok() {
            return new ReconnectResumeResult(true, "resumed");
        }

        private static ReconnectResumeResult fail(String reason) {
            return new ReconnectResumeResult(false, reason == null || reason.isBlank() ? "unknown" : reason);
        }
    }

    public record StatsDisconnectPresentation(
        Text text,
        String retiredSessionId
    ) {}

    public enum ReconnectAbortRestoreOutcome {
        Restored,
        DeferredNoLiveWorld,
        RejectedGeneration,
        StatsPersistenceFailed
    }

    private record ReconnectBaselineRestoreResult(
        boolean success,
        String reason
    ) {
        private static ReconnectBaselineRestoreResult ok() {
            return new ReconnectBaselineRestoreResult(true, "restored");
        }

        private static ReconnectBaselineRestoreResult fail(String reason) {
            return new ReconnectBaselineRestoreResult(false, reason == null || reason.isBlank() ? "unknown" : reason);
        }
    }

    private record StatsArtifactIdentity(
        StatsArtifactKind kind,
        String sessionId,
        long generation
    ) {
        private String key() {
            return kind.name() + "|" + sessionId + "|" + generation;
        }
    }

    private record RetiredStatsReportSnapshot(
        String sessionId,
        long generation,
        Vec3d startPos,
        int blocksBroken,
        int blocksPlaced
    ) {}

    private record StatsDisplaySnapshot(
        double distance,
        int blocksBroken,
        int blocksPlaced
    ) {
        // ponytail: whole-session average, not a windowed/rolling one
        private double brokenPerBlock() {
            return distance > 0 ? blocksBroken / distance : 0.0;
        }

        private double placedPerBlock() {
            return distance > 0 ? blocksPlaced / distance : 0.0;
        }
    }

    public enum WebhookContent {
        Text,
        Image,
        Both
    }

    /** Webhook send parked until the proof screenshot exists, so both go out in one request. */
    private record PendingWebhookStats(String url, String message) {}

    private enum StatsScreenshotSurface {
        CHAT("chat"),
        DISCONNECT("disconnect");

        private final String fileNamePart;

        StatsScreenshotSurface(String fileNamePart) {
            this.fileNamePart = fileNamePart;
        }
    }

    private record ForwardBreakStatCredit(
        ForwardTaskType type,
        BlockState originalState,
        int expiresAtAge
    ) {}

    private record StatsArtifactLoadResult(
        StatsArtifactSnapshot snapshot,
        boolean transientFailure,
        boolean unsupportedVersion
    ) {}

    private enum StatsSessionState {
        OPEN,
        PENDING_PRINT,
        CLOSED
    }

    public HighwayBuilderTHM() {
        super(THMAddon.MAIN, "THM-HighwayBuilder", "Automatically builds highways according to THMs standards.");
        runInMainMenu = true;
    }

    private String resolveWebhookUrl(String configuredWebhook) {
        if (configuredWebhook == null) return null;
        String raw = configuredWebhook.trim();
        if (raw.isEmpty()) return null;
        if (raw.startsWith("http://") || raw.startsWith("https://")) return raw;
        THMAddon.LOG.warn("Invalid webhook URL format (must start with http/https).");
        return null;
    }

    private void sendStatusLog() {
        if (!statuslog.get() || mc.player == null || dir == null) return;

        // Silent on purpose — see enforceCreativeStatisticsGuard().
        if (mc.player.isCreative()) {
            highwayDebug("HB status log skipped: player is in creative.");
            return;
        }

        if (isNot6B6T()) {
            warning("Status not sent. You are not on 6B6T");
            return;
        }
        if (!isOnMainHighway()) {
            warning("Status wont get send. You are not on a main highway");
            return;
        }
        if (THMSystem.get() == null || !THMSystem.get().hasApiToken()) {
            warning("Status not sent. No valid API token set - get one from the Discord bot's player panel.");
            return;
        }

        String playerName = mc.player.getName().getLiteralString();
        String axis = dir.toString();

        String statusMessage = String.format("%s:%s:%s:%d:%d:%s",
            THMSystem.get().getApiToken(),
            playerName,
            axis,
            blocksBroken,
            blocksPlaced,
            generateTimestamp()
        );

        sendStatus(statusMessage);
    }

    private ServerState getCommittedServerState() {
        return ServerStatusHandler.getInstance().getCommittedState();
    }

    private boolean isExecutionAllowedOnCurrentServer(ServerState committedState) {
        return committedState == ServerState.MAIN_SERVER && !mainServerResumeGateActive;
    }

    private void trackServerExecutionState(ServerState committedState) {
        if (committedState == lastCommittedServerState) return;

        ServerState previousState = lastCommittedServerState;
        lastCommittedServerState = committedState;

        if (committedState == ServerState.MAIN_SERVER) {
            if (mainServerResumeGateActive && mainServerResumeReadyAtMs <= 0L) {
                mainServerResumeReadyAtMs = System.currentTimeMillis() + HIGHWAYBUILDER_MAIN_SERVER_RESUME_DELAY_MS;
                mainServerResumeDelayLogged = false;
                if (debugLog.get()) {
                    highwayDebug(
                        "HB MAIN_SERVER detected after off-main state=%s; waiting %.1fs before resume.",
                        mainServerResumeSourceState,
                        HIGHWAYBUILDER_MAIN_SERVER_RESUME_DELAY_MS / 1000.0
                    );
                }
            }
            if (!mainServerResumeGateActive) offMainEpisodeCheckpointed = false;
            return;
        }

        if (previousState == ServerState.MAIN_SERVER || mainServerResumeGateActive) {
            armMainServerResumeGate(committedState);
        }

        if (previousState == ServerState.MAIN_SERVER && !offMainEpisodeCheckpointed && hasActiveInMemoryStatsSession() && !statsSessionTerminalOrFinalizing) {
            persistCurrentStatsSession(StatsSessionState.OPEN, true, 0L, "server-state-left-main");
            offMainEpisodeCheckpointed = true;
        }
    }

    private void armMainServerResumeGate(ServerState committedState) {
        boolean wasActive = mainServerResumeGateActive;
        mainServerResumeGateActive = true;
        mainServerResumeSourceState = committedState;
        mainServerResumeReadyAtMs = 0L;
        mainServerResumeDelayLogged = false;
        if (debugLog.get() && !wasActive) highwayDebug("HB off-main resume gate armed by server-state=%s.", committedState);
    }

    private boolean tryCompleteMainServerResumeGate(ServerState committedState) {
        if (!mainServerResumeGateActive) return committedState == ServerState.MAIN_SERVER;
        if (committedState != ServerState.MAIN_SERVER) return false;

        long now = System.currentTimeMillis();
        if (mainServerResumeReadyAtMs <= 0L) {
            mainServerResumeReadyAtMs = now + HIGHWAYBUILDER_MAIN_SERVER_RESUME_DELAY_MS;
            mainServerResumeDelayLogged = false;
        }
        if (now < mainServerResumeReadyAtMs) {
            if (debugLog.get() && !mainServerResumeDelayLogged) {
                highwayDebug(
                    "HB waiting %.1fs for MAIN_SERVER settle before resume.",
                    Math.max(0L, mainServerResumeReadyAtMs - now) / 1000.0
                );
                mainServerResumeDelayLogged = true;
            }
            return false;
        }

        if (input != null) input.stop();
        if (mc.player != null && dir != null) {
            mc.player.setYaw(dir.yaw);
            mc.player.setPitch(20.0f);
            if (state == State.Forward) {
                resetForwardDriftTarget();
                if (useForwardRowSchedulerMode()) seedForwardSchedulerWindow();
            }
        }

        if (debugLog.get()) {
            highwayDebug(
                "HB resumed after MAIN_SERVER settle (state=%s, direction=%s, yaw=%.1f).",
                stateName(state),
                dir == null ? "unknown" : dir.name,
                dir == null ? (mc.player == null ? 0.0f : mc.player.getYaw()) : dir.yaw
            );
        }

        clearMainServerResumeGate();
        resumeThmSpeedAfterReconnectIfReady("main-server-settle");
        return true;
    }

    private void clearMainServerResumeGate() {
        mainServerResumeGateActive = false;
        mainServerResumeReadyAtMs = 0L;
        mainServerResumeDelayLogged = false;
        mainServerResumeSourceState = ServerState.UNKNOWN;
        lastServerStatePauseLogState = null;
        nextServerStatePauseLogAtMs = 0L;
        offMainEpisodeCheckpointed = false;
    }

    private void logServerStatePause(ServerState committedState) {
        if (!debugLog.get()) return;
        long now = System.currentTimeMillis();
        if (committedState == lastServerStatePauseLogState && now < nextServerStatePauseLogAtMs) return;

        String reason = mainServerResumeGateActive && committedState == ServerState.MAIN_SERVER ? "main-server-settle" : "off-main";
        highwayDebug("HB paused by server-state=%s (%s).", committedState, reason);
        lastServerStatePauseLogState = committedState;
        nextServerStatePauseLogAtMs = now + SERVER_STATE_PAUSE_LOG_INTERVAL_MS;
    }

    private void pauseExecutionForServerState(ServerState committedState) {
        parkThmSpeedForReconnect("server-state-" + committedState.name());
        clearSafetyRuntime("server-state-" + committedState.name());
        if (input != null) input.stop();
        executionPausedByServerState = true;
    }

    private void enforceDisabledHighwayBuilderSettings() {
        if (!MANAGED_SPEEDMINE_SETTING_ENABLED && speedmine.get()) speedmine.set(false);
        if (!MODULE_MANAGER_INTEGRATION_ENABLED && manageModuleManager.get()) manageModuleManager.set(false);
        if (!manageThmHwyMonitor.get() && fallSaveAirPlace.get()) fallSaveAirPlace.set(false);
        enforceCreativeStatisticsGuard();
    }

    /**
     * Creative blocks are free, so creative stats are fake — neither the API toggle nor the status log
     * can be on in creative. Runs whenever either setting is switched on and again on activate / after
     * a THM profile load (either can be on from a saved config long before a player exists to check).
     * <p>
     * Deliberately silent: never tell the player that creative is what blocked the submission, otherwise
     * they know exactly which check to work around. Only the local debug log records the reason.
     */
    private void enforceCreativeStatisticsGuard() {
        if (mc.player == null || !mc.player.isCreative()) return;
        if (!sendStatisticsapi.get() && !statuslog.get()) return;
        sendStatisticsapi.set(false);
        statuslog.set(false);
        highwayDebug("HB statistics(API) + status log force-disabled: player is in creative.");
    }

    public void normalizeAfterThmProfileLoad() {
        enforceDisabledHighwayBuilderSettings();
        enforceKitbotFoodRestockFoodType();
    }

    /**
     * The preset values of a profile. Forced on top of the profile's snapshot every time the THM
     * tab's "Apply Profile" button is pressed, so adding a setting here reaches existing users too.
     */
    public void applyThmProfileSeed(THMSystem.Mode profile) {
        switch (profile) {
            case None -> {
            }
            case HighwayBuilding -> {
                width.set(5);
                height.set(3);
                blocksToPlace.set(List.of(Blocks.OBSIDIAN));
                mineAboveRailings.set(true);
                railings.set(true);
                floor.set(Floor.Replace);
                noSwapLoadout.set(true);
                if (THMUtils.isBaritoneInstalled()) manageThmHwyMonitor.set(true);
                kitbotEChestRestockKit.set(KitbotEChestRestockKit.Highway);
                kitbotPickaxeRestockKit.set(KitbotPickaxeRestockKit.Highway);
            }
            case HighwayDigging -> {
                width.set(5);
                height.set(4);
                blocksToPlace.set(List.of(Blocks.NETHERRACK, Blocks.BASALT, Blocks.BLACKSTONE, Blocks.SOUL_SOIL));
                mineAboveRailings.set(true);
                railings.set(true);
                floor.set(Floor.Replace);
                noSwapLoadout.set(true);
                if (THMUtils.isBaritoneInstalled()) manageThmHwyMonitor.set(true);
                kitbotPickaxeRestockKit.set(KitbotPickaxeRestockKit.Pickaxe);
            }
        }
    }

    @Override
    public void onActivate() {
        if (mc.player == null || mc.world == null) return;
        if (!Utils.canUpdate()) return;

        // Creative may build freely — it just never reports anything (enforceCreativeStatisticsGuard plus
        // the silent creative skips in sendStatusLog / commitAndSendFinalExternalStats).
        if ((sendStatisticsapi.get() || statuslog.get()) && THMUtils.isNot6B6T()) {
            sendStatisticsapi.set(false);
            statuslog.set(false);
            errorEarly("You can only build on 6b6t");
            return;
        }

        ReconnectResumeContext reconnectResume = reconnectResumeContext;
        reconnectResumeContext = null;
        boolean reconnectActivation = reconnectResume != null;
        if (reconnectActivation) thmSpeedReconnectSuppressed = true;
        KitbotFrontend.addLifecycleListener(kitbotRestockLifecycleListener);
        clearKitbotRuntimeState("module-activate");
        enforceKitbotFoodRestockFoodType();
        syncFoodManagementOnActivate();

        if (packetBuild.get()) {
            PaketLimiter limiter = Modules.get().get(PaketLimiter.class);
            if (limiter != null && !limiter.isActive()) limiter.toggle();
        }

        if (centerSpeedMonitorRecoveryOwned && !resumeStatsSessionOnNextActivate) {
            restockDebug("Center/Speed stale monitor recovery baseline cleared on activate (lastReason=%s).", centerSpeedLastReason);
            clearCenterSpeedOwnership("activate-stale-monitor-baseline");
        }
        clearEChestBreakSpeedOwnership();

        enforceDisabledHighwayBuilderSettings();
        if (!suppressThmHwyMonitorSync) {
            syncModuleManagerOnActivate();
            syncThmHwyMonitorOnActivate();
        }
        loadStatsCacheFromDisk();

        previousPauseOnLostFocus = mc.options.pauseOnLostFocus;
        pauseOnLostFocusForcedOff = false;
        if (previousPauseOnLostFocus) {
            togglePauseOnLostFocus(false);
            pauseOnLostFocusForcedOff = true;
        }
        pauseOnLostFocusChanged = pauseOnLostFocusForcedOff;
        if (blockadeType.get() != getEffectiveBlockadeType()) blockadeType.set(getEffectiveBlockadeType());
        if (saveEchests.get() < 4) saveEchests.set(4);

        updateVariables();
        updateSignBreakRegex();
        HorizontalDirection activationDirection;
        if (reconnectActivation) {
            activationWorkingDirectionSource = "reconnect-locked";
            activationDirection = reconnectResume.direction();
        } else {
            activationDirection = resolveActivationWorkingDirection();
            applyActivationWorkingYaw(activationDirection);
        }
        dir = activationDirection != null ? activationDirection : HorizontalDirection.get(mc.player.getYaw());
        if (debugLog.get()) {
            info("Activation working direction source=%s direction=%s manage-thm-hwy-monitor=%s.",
                activationWorkingDirectionSource,
                dir.name,
                manageThmHwyMonitor.get()
            );
        }
        leftDir = dir.rotateLeftSkipOne();
        rightDir = leftDir.opposite();
        syncThmSpeedOwnership("activate");

        blockPosProvider = dir.diagonal ? new DiagonalBlockPosProvider() : new StraightBlockPosProvider();
        countedBrokenForwardPositions.clear();
        countedPlacedForwardPositions.clear();
        resetForwardSchedulerRuntime();
        seedForwardSchedulerWindow();
        state = State.Forward;
        lastBreakingPos.set(0, 0, 0);
        if (isPendingPrintStatsSession(statsCacheSnapshot)) {
            if (!completeFinalizationRecord(statsCacheSnapshot, "pending-print-on-activate", true)) {
                warning("Unable to complete pending HighwayBuilder statistics safely; keeping session pending.");
                toggle();
                return;
            }
            statsCacheSnapshot = null;
        }

        boolean resumedStatsSession = isResumableStatsSession(statsCacheSnapshot);
        if (resumedStatsSession) {
            restoreStatsFromCache(statsCacheSnapshot, resumeStatsSessionOnNextActivate ? "monitor-resume" : "cache-resume");
            markArtifactConsumed(statsCacheSnapshot);
        }

        if (!reconnectActivation) setState(State.Center);
        if (!resumedStatsSession) startFreshStatsSession();

        resumeStatsSessionOnNextActivate = false;
        monitorPauseDeactivateArmed = false;
        suspended = reconnectActivation;
        statusLogTimer = 6000;
        nextAdvertisementIndex = 0;
        nextAdvertisementAtTick = 0L;
        kitbotPeriodicUpdateActive = false;
        kitbotPeriodicUpdatePending = false;
        kitbotPeriodicUpdateNextTick = mc.world.getTime() + KITBOT_PERIODIC_UPDATE_INTERVAL_TICKS;
        forwardSchedulerDebugFileErrorLogged = false;
        resetTpsThrottleRuntime();
        clearSafetyRuntime("module-activate");
        mineActionsThisTick = Math.max(1, (int) Math.floor(sanitizeMineActionRate(blocksPerTick.get())));
        mineFractionCarry = 0.0;
        placeActionsThisTick = Math.max(0, (int) Math.floor(sanitizePlaceActionRate(placementsPerTick.get())));
        placeFractionCarry = 0.0;
        resetEnclosurePlaceCredit();
        clearEnclosurePlacementConfirmation();
        clearDesyncWiggleProbe("module-activate");
        lastForwardDesyncWiggleEpisodeId = Integer.MIN_VALUE;

        restockTask.complete();
        restockWatchdog.reset("module-activate");

        if (blocksPerTick.get() > 1 && rotation.get().mine)
            warning("With rotations enabled, you can break at most 1 block per tick.");
        if (effectiveInstamineOverrideBlocksPerTick() > 1 && rotation.get().mine)
            warning("With rotations enabled, instamine override blocks can break at most 1 block per tick.");
        if (placementsPerTick.get() > 1 && rotation.get().place)
            warning("With rotations enabled, you can place at most 1 block per tick.");
        //all modules that may cause error now print errors/warnings
        if (Modules.get().get(InstantRebreak.class).isActive())
            warning("It's recommended to disable the Instant Rebreak module because HighwayBuilder manages ender chest rebreaking internally.");
        SpeedMine speedMine = Modules.get().get(SpeedMine.class);
        Setting<SpeedMine.ListMode> speedMineBlocksFilter = speedMine.settings.get("blocks-filter", SpeedMine.ListMode.class);
        @SuppressWarnings("unchecked")
        Setting<List<Block>> speedMineBlocks = (Setting<List<Block>>) speedMine.settings.get("blocks");
        if (speedMineBlocksFilter != null
            && speedMineBlocksFilter.get() == SpeedMine.ListMode.Blacklist
            && speedMineBlocks != null
            && speedMineBlocks.get().contains(Blocks.NETHERRACK)) {
            warning("It's recommended to add Netherrack to the blacklist in Speed Mine (Ignore if you let highway builder manage it)");
        }
        if (Modules.get().get(Speed.class).isActive() && dir.diagonal)
            warning("It's recommended to disable the Speed module to avoid misalignment on diagonals.");
        if (Modules.get().get(Timer.class).isActive() && dir.diagonal)
            warning("It's recommended to disable the Timer module to avoid misalignment on diagonals.");
        //it could be tested to print different warnings depending on the amount of blocks being broken per tick but that would need much testing and wouldn't be reliable
        if (Modules.get().get(NoGhostBlocks.class).isActive())
            warning("It's recommended to disable the NoGhostBlocks module to avoid packet kicks and wrong statistics.");
        if (!autosetupModules.get() && !Modules.get().get(Velocity.class).isActive()) {
            warning("It's recommended to enable the Velocity module to avoid misalignment.");};
        perspectiveChanged = false;
        if (togglePerspective.get()) {
            previousPerspective = mc.options.getPerspective();
            if (previousPerspective != Perspective.THIRD_PERSON_BACK) {
                mc.options.setPerspective(Perspective.THIRD_PERSON_BACK);
                perspectiveChanged = true;
            }
        }
        if (!Modules.get().get(HotbarManager.class).isActive() && hotbarmanager.get()) { Modules.get().get(HotbarManager.class).toggle();}
        validateManagedHotbarReserveSlots();
        if (!Modules.get().get(AntiDrop.class).isActive() && antidrop.get()) { Modules.get().get(AntiDrop.class).toggle();}
        syncManagedSpeedMineOwnership();
        syncNoSwapAutoTotem();

    }
    @Override
    public void onDeactivate() {
        KitbotFrontend.removeLifecycleListener(kitbotRestockLifecycleListener);
        if (input != null) input.stop();
        restoreHotbarManagerAfterTrash("module-deactivate");
        clearDesyncWiggleProbe("module-deactivate");
        clearMainServerResumeGate();
        resetEatingPauseWatchdog();
        releaseActiveDoubleMineRuntime(true);
        countedBrokenForwardPositions.clear();
        countedPlacedForwardPositions.clear();
        clearPendingForwardBreakCredits();
        resetForwardSchedulerRuntime();
        placeTrail.clear();
        forwardSchedulerDebugFileErrorLogged = false;
        mineActionsThisTick = 0;
        mineFractionCarry = 0.0;
        placeActionsThisTick = 0;
        placeFractionCarry = 0.0;
        resetTpsThrottleRuntime();
        clearSafetyRuntime("module-deactivate");
        resetEnclosurePlaceCredit();
        clearEnclosurePlacementConfirmation();
        lastForwardDesyncWiggleEpisodeId = Integer.MIN_VALUE;
        restockWatchdog.reset("module-deactivate");
        boolean isMonitorPauseDeactivate = monitorPauseDeactivateArmed;
        boolean isTravelHandoffDeactivate = travelHandoffDeactivateArmed;
        boolean isReconnectFailureDeactivate = reconnectFailureDeactivateArmed;
        boolean isAutoLogTerminalDeactivate = autoLogTerminalDeactivateArmed;
        monitorPauseDeactivateArmed = false;
        travelHandoffDeactivateArmed = false;
        reconnectFailureDeactivateArmed = false;
        autoLogTerminalDeactivateArmed = false;
        if (isMonitorPauseDeactivate) {
            parkThmSpeedForReconnect("monitor-pause-deactivate");
        } else {
            releaseThmSpeedOwnership(
                isReconnectFailureDeactivate ? "reconnect-failure-deactivate"
                    : isTravelHandoffDeactivate ? "travel-handoff-deactivate"
                    : "module-deactivate",
                true
            );
        }
        cleanupKitbotEnclosureOnDeactivate(isMonitorPauseDeactivate ? "monitor-pause-deactivate" : "module-deactivate");

        if (!suppressThmHwyMonitorSync) syncThmHwyMonitorOnDeactivate();

        if (!isMonitorPauseDeactivate && !isReconnectFailureDeactivate) {
            Timer timer = Modules.get().get(Timer.class);
            if (timer != null) timer.setOverride(Timer.OFF);
            restoreCenterSpeedIfOwned("module-deactivate");
            invalidateReconnectBaseline("non-monitor-deactivate");
        }
        else if (isMonitorPauseDeactivate && centerSpeedSnapshotOwned) {
            centerSpeedLastReason = "monitor-pause-preserved";
            restockDebug("Center/Speed baseline preserved across monitor pause deactivate (active=%s, timerActive=%s, overrideActive=%s, monitorOwned=%s).",
                centerSpeedSnapshot.wasActive(),
                centerSpeedSnapshot.timerWasActive(),
                centerSpeedOverrideActive,
                centerSpeedMonitorRecoveryOwned
            );
        }

        if (pauseOnLostFocusChanged && pauseOnLostFocusForcedOff && previousPauseOnLostFocus) {
            togglePauseOnLostFocus(true);
        }
        pauseOnLostFocusChanged = false;
        pauseOnLostFocusForcedOff = false;

        restoreEChestBreakSpeedIfOwned("module-deactivate");
        syncModuleManagerOnDeactivate();

        restoreSpeedMineSettingsIfNeeded();

        if (mc.player == null || mc.world == null || !Utils.canUpdate()) {
            if (hasActiveInMemoryStatsSession()) {
                if (isMonitorPauseDeactivate || isTravelHandoffDeactivate) {
                    persistCurrentStatsSession(StatsSessionState.OPEN, true, 0L,
                        isMonitorPauseDeactivate ? "monitor-pause-deactivate-no-world" : "travel-handoff-deactivate-no-world");
                } else {
                    StatsArtifactSnapshot finalizationRecord = persistFinalizationRecord(createFinalizationRecord("deactivate-pending-print-no-world"), "deactivate-pending-print-no-world");
                    if (finalizationRecord == null) warning("Unable to persist pending HighwayBuilder statistics safely before deactivate.");
                }
            } else {
                statsCacheDebug("skipped deactivate persistence because no active in-memory stats session exists.");
            }
            return;
        }

        mc.player.input = prevInput;
        mc.options.useKey.setPressed(false);
        if (perspectiveChanged) {
            mc.options.setPerspective(previousPerspective);
            perspectiveChanged = false;
        }
        if (Modules.get().get(HotbarManager.class).isActive() && hotbarmanager.get()) { Modules.get().get(HotbarManager.class).toggle();}
        if (Modules.get().get(AntiDrop.class).isActive() && antidrop.get()) { Modules.get().get(AntiDrop.class).toggle();}
        if (disabledAutoTotem) { // no-swap-loadout no longer owns the offhand; give AutoTotem back
            AutoTotem autoTotem = Modules.get().get(AutoTotem.class);
            if (autoTotem != null && !autoTotem.isActive()) autoTotem.toggle();
            disabledAutoTotem = false;
        }

        if (!hasActiveInMemoryStatsSession()) {
            statsCacheDebug("skipped deactivate persistence because no active in-memory stats session exists.");
            return;
        }

        if (isMonitorPauseDeactivate || isTravelHandoffDeactivate) {
            persistCurrentStatsSession(StatsSessionState.OPEN, true, 0L,
                isMonitorPauseDeactivate ? "monitor-pause-deactivate" : "travel-handoff-deactivate");
            return;
        }

        if (kitbotUpdateOnFinish.get() && !isReconnectFailureDeactivate && !isAutoLogTerminalDeactivate) {
            if (!isOnOfficialHighway()) {
                info("Not on an official highway - skipping KitBot $update.");
            } else if (isAtHighwayEnd()) {
                String kitbotDir = directionToKitbotCommand(dir);
                if (kitbotDir != null && mc.player != null && mc.world != null) {
                    double distFromOrigin = Math.hypot(mc.player.getX(), mc.player.getZ());
                    if (distFromOrigin < 10000) {
                        info("Too close to origin (%.0f blocks) - skipping KitBot $update.", distFromOrigin);
                    } else {
                        buildKitbotUpdateEnclosure();
                        ChatUtils.sendPlayerMsg("/msg KitBot1 $update " + kitbotDir);
                        info("Sent $update %s to KitBot1. Waiting up to 60s for teleport...", kitbotDir);
                        kitbotUpdateOnFinishActive = true;
                        kitbotUpdateOnFinishStartTick = mc.world.getTime();
                        kitbotUpdateOnFinishTpAccepted = false;
                    }
                }
            } else {
                info("Not at highway end - skipping KitBot $update.");
            }
        }

        StatsArtifactSnapshot finalizationRecord = persistFinalizationRecord(createFinalizationRecord("deactivate"), "deactivate-finalization-record");
        if (finalizationRecord == null) {
            warning("Unable to persist final HighwayBuilder statistics safely before deactivate.");
            return;
        }

        if (!completeFinalizationRecord(finalizationRecord, "printed-on-deactivate", true)) {
            warning("Unable to complete HighwayBuilder statistics safely during deactivate; keeping durable finalization record.");
        }

    }

    private void syncThmHwyMonitorOnActivate() {
        if (!manageThmHwyMonitor.get()) return;

        try {
            THMHwyMonitor monitor = Modules.get().get(THMHwyMonitor.class);
            if (monitor == null || monitor.isActive()) return;
            monitor.toggle();
        } catch (NoClassDefFoundError | ExceptionInInitializerError ignored) {
            // Baritone-dependent monitor not available.
        }
    }

    private void onManageThmHwyMonitorChanged(Boolean value) {
        boolean enabled = Boolean.TRUE.equals(value);
        if (!enabled && fallSaveAirPlace != null && fallSaveAirPlace.get()) fallSaveAirPlace.set(false);
        if (!isActive()) return;

        if (enabled) syncThmHwyMonitorOnActivate();
        else disableThmHwyMonitorIfActive();
    }

    private void syncModuleManagerOnActivate() {
        if (!MODULE_MANAGER_INTEGRATION_ENABLED || !manageModuleManager.get()) return;

        try {
            ModuleManager manager = Modules.get().get(ModuleManager.class);
            if (manager == null) return;
            manager.claimHighwayBuilderOwnershipAndEnable("highwaybuilder-activate");
        } catch (Throwable ignored) {
            // Module manager unavailable.
        }
    }

    private void syncModuleManagerOnDeactivate() {
        if (!MODULE_MANAGER_INTEGRATION_ENABLED || !manageModuleManager.get()) return;

        try {
            ModuleManager manager = Modules.get().get(ModuleManager.class);
            if (manager == null) return;
            manager.releaseHighwayBuilderOwnership("highwaybuilder-deactivate");
        } catch (Throwable ignored) {
            // Module manager unavailable.
        }
    }

    private void syncThmHwyMonitorOnDeactivate() {
        if (!manageThmHwyMonitor.get()) return;

        disableThmHwyMonitorIfActive();
    }

    private void disableThmHwyMonitorIfActive() {
        try {
            THMHwyMonitor monitor = Modules.get().get(THMHwyMonitor.class);
            if (monitor == null || !monitor.isActive()) return;
            monitor.toggle();
        } catch (NoClassDefFoundError | ExceptionInInitializerError ignored) {
            // Baritone-dependent monitor not available.
        }
    }

    public void disableForMonitorRealignPause() {
        if (!isActive()) return;

        resumeStatsSessionOnNextActivate = true;
        monitorPauseDeactivateArmed = true;
        parkThmSpeedForReconnect("monitor-realign-pause");
        if (!statsSessionTerminalOrFinalizing) persistCurrentStatsSession(StatsSessionState.OPEN, true, 0L, "monitor-pause-request");
        suppressThmHwyMonitorSync = true;
        try {
            toggle();
        } finally {
            suppressThmHwyMonitorSync = false;
        }
    }

    public boolean checkpointStatsForMonitorReconnectPause(String reason) {
        String safeReason = reason == null || reason.isBlank() ? "monitor-reconnect-pause" : reason;
        return persistCurrentStatsSession(StatsSessionState.OPEN, true, 0L, safeReason);
    }

    public HorizontalDirection getWorkingDirection() {
        return dir;
    }

    public HorizontalDirection getCachedWorkingDirectionForMonitorReconnect(long generation) {
        StatsArtifactSnapshot artifact = peekAuthoritativeStatsArtifact();
        ReconnectBaselinePayload artifactPayload = getDurableReconnectBaselinePayload(artifact, generation);
        HorizontalDirection artifactDirection = parseWorkingDirectionName(artifactPayload == null ? null : artifactPayload.workingDirectionName());
        if (artifactDirection != null) return artifactDirection;

        if (isResumableStatsSession(artifact)
            && (artifact.reconnectBaselinePayload() == null || artifact.reconnectCycleId() == generation)) {
            HorizontalDirection statsDirection = parseWorkingDirectionName(artifact.workingDirectionName());
            if (statsDirection != null) return statsDirection;
        }

        HorizontalDirection baselineDirection = parseWorkingDirectionName(
            hasCapturedReconnectBaselineLease(generation) ? reconnectBaselineLease.payload().workingDirectionName() : null
        );
        if (baselineDirection != null) return baselineDirection;

        if (!isResumableStatsSession(statsCacheSnapshot)) return null;
        return parseWorkingDirectionName(statsCacheSnapshot.workingDirectionName());
    }

    private HorizontalDirection resolveActivationWorkingDirection() {
        if (mc.player == null) {
            activationWorkingDirectionSource = "player-missing";
            return null;
        }

        if (!manageThmHwyMonitor.get()) {
            activationWorkingDirectionSource = "manual-player-yaw";
            return HorizontalDirection.get(mc.player.getYaw());
        }

        HorizontalDirection cachedDirection = peekCachedWorkingDirectionForActivation();
        if (cachedDirection != null) {
            activationWorkingDirectionSource = "managed-cached-stats";
            return cachedDirection;
        }

        THMHwyMonitor monitor = Modules.get().get(THMHwyMonitor.class);
        boolean useTrueCenterMode = monitor == null || monitor.usesTrueCenterMode();
        HorizontalDirection inferredDirection = THMHwyMonitor.inferClosestWorkingDirection(
            mc.player.getX(),
            mc.player.getZ(),
            mc.player.getYaw(),
            useTrueCenterMode
        );
        if (inferredDirection != null) {
            activationWorkingDirectionSource = "managed-line-inference";
            return inferredDirection;
        }

        activationWorkingDirectionSource = "managed-yaw-fallback";
        return HorizontalDirection.get(mc.player.getYaw());
    }

    private void applyActivationWorkingYaw(HorizontalDirection direction) {
        if (mc.player == null || direction == null) return;
        mc.player.setYaw(direction.yaw);
    }

    private HorizontalDirection peekCachedWorkingDirectionForActivation() {
        HorizontalDirection inMemoryDirection = peekInMemoryCachedWorkingDirectionForActivation();
        if (inMemoryDirection != null) return inMemoryDirection;

        StatsArtifactSnapshot selected = peekAuthoritativeStatsArtifact();
        if (!isResumableStatsSession(selected)) return null;
        return parseWorkingDirectionName(selected.workingDirectionName());
    }

    private HorizontalDirection peekInMemoryCachedWorkingDirectionForActivation() {
        if (!isResumableStatsSession(statsCacheSnapshot)) return null;
        if (consumedStatsArtifactKeys.contains(identityOf(statsCacheSnapshot).key())) return null;
        return parseWorkingDirectionName(statsCacheSnapshot.workingDirectionName());
    }

    private boolean isAtHighwayEnd() {
        if (mc.player == null || mc.world == null || dir == null || blockPosProvider == null) {
            if (debugLog.get()) highwayDebug("HB $update debug: missing context player/world/dir/provider -> atEnd=false");
            return false;
        }

        List<BlockPos> floorAhead = snapshotTemplate(blockPosProvider.getFloor());
        List<BlockPos> floorBehind = snapshotTemplate(blockPosProvider.getBehindFloor());
        List<BlockPos> frontAhead = snapshotTemplate(blockPosProvider.getFront());
        List<BlockPos> frontBehind = snapshotTemplate(blockPosProvider.getBehindFront());
        List<BlockPos> railingsAhead = snapshotTemplate(blockPosProvider.getRailings(0));
        List<BlockPos> railingsBehind = snapshotTemplate(blockPosProvider.getBehindRailings(0));

        if (floorAhead.isEmpty()) {
            if (debugLog.get()) highwayDebug("HB $update debug: floorAhead empty -> atEnd=false");
            return false;
        }

        boolean hasDugContext = !frontAhead.isEmpty()
            && (isDugSegment(frontBehind, floorBehind, 0) || isDugSegment(frontAhead, floorAhead, 0));
        boolean hasPavedContext = !railingsAhead.isEmpty()
            && (isPavedSegment(floorBehind, railingsBehind, 0) || isPavedSegment(floorAhead, railingsAhead, 0));
        boolean digging = inferDugModeForUpdate(hasDugContext, hasPavedContext);

        boolean officialHighway = isOnOfficialHighway();
        String axis = directionToKitbotCommand(dir);
        if (debugLog.get()) {
            info(
                "HB $update debug: playerY=%d isDug=%s axis=%s officialHighway=%s dugContext=%s pavedContext=%s",
                mc.player.getBlockY(),
                digging,
                axis != null ? axis : dir.name(),
                officialHighway,
                hasDugContext,
                hasPavedContext
            );
        }

        if (digging) {
            if (frontAhead.isEmpty()) {
                if (debugLog.get()) highwayDebug("HB $update debug: dug frontAhead empty -> atEnd=false");
                return false;
            }
            if (!hasDugContext) {
                if (debugLog.get()) highwayDebug("HB $update debug: dug context missing -> atEnd=false");
                return false;
            }
            DugEndScanResult dugScan = scanDugEnd(frontAhead, floorAhead);
            boolean atEnd = dugScan == DugEndScanResult.DUG_END;
            if (debugLog.get()) highwayDebug("HB $update debug: dug scan result=%s -> atEnd=%s", dugScan, atEnd);
            return atEnd;
        }

        if (railingsAhead.isEmpty()) {
            if (debugLog.get()) highwayDebug("HB $update debug: paved railingsAhead empty -> atEnd=false");
            return false;
        }

        if (!hasPavedContext) {
            if (debugLog.get()) highwayDebug("HB $update debug: paved context missing -> atEnd=false");
            return false;
        }

        int runLength = findForwardRunLength(railingsAhead, null, floorAhead, false, PAVED_END_REQUIRED_CONSECUTIVE_MISSES);
        boolean atEnd = runLength <= HIGHWAY_END_NEAR_THRESHOLD;
        if (debugLog.get()) {
            info(
                "HB $update debug: paved runLength=%d threshold=%d -> atEnd=%s",
                runLength,
                HIGHWAY_END_NEAR_THRESHOLD,
                atEnd
            );
        }
        return atEnd;
    }

    private int findForwardRunLength(
        List<BlockPos> railingsTemplate,
        List<BlockPos> frontTemplate,
        List<BlockPos> floorTemplate,
        boolean digging,
        int requiredConsecutiveMisses
    ) {
        int run = 0;
        int consecutiveMisses = 0;

        for (int step = 0; step < HIGHWAY_END_SCAN_DISTANCE; step++) {
            boolean matches = digging
                ? isDugSegment(frontTemplate, floorTemplate, step)
                : isPavedSegment(floorTemplate, railingsTemplate, step);

            if (matches) {
                run++;
                consecutiveMisses = 0;
            } else {
                consecutiveMisses++;
                if (consecutiveMisses >= requiredConsecutiveMisses) break;
            }
        }

        return run;
    }

    private boolean isPavedSegment(List<BlockPos> floorTemplate, List<BlockPos> railingTemplate, int stepOffset) {
        Ratio floorObsidian = countTemplateMatches(floorTemplate, stepOffset, HIGHWAY_FLOOR_Y, this::isObsidian);
        Ratio railingBlocks = countTemplateMatches(railingTemplate, stepOffset, HIGHWAY_RAILING_Y, this::isRailingBlock);
        if (floorObsidian.total == 0 || railingBlocks.total == 0) return false;

        return floorObsidian.value() >= PAVED_FLOOR_OBSIDIAN_RATIO
            && railingBlocks.value() >= PAVED_RAILING_RATIO;
    }

    private boolean isDugSegment(List<BlockPos> frontTemplate, List<BlockPos> floorTemplate, int stepOffset) {
        Ratio excavated = countTemplateMatches(frontTemplate, stepOffset, Integer.MIN_VALUE, this::isExcavatedSpace);
        Ratio floorObsidian = countTemplateMatches(floorTemplate, stepOffset, HIGHWAY_FLOOR_Y, this::isObsidian);
        if (excavated.total == 0 || floorObsidian.total == 0) return false;

        return excavated.value() >= DUG_EXCAVATED_RATIO
            && floorObsidian.value() <= DUG_OBSIDIAN_FLOOR_MAX_RATIO;
    }

    private DugEndScanResult scanDugEnd(List<BlockPos> frontTemplate, List<BlockPos> floorTemplate) {
        int netherrackCount = 0;
        boolean sawAnyObsidian = false;

        for (int step = 0; step < DUG_END_SCAN_STEPS; step++) {
            Ratio frontObsidian = countTemplateMatches(frontTemplate, step, Integer.MIN_VALUE, this::isObsidian);
            if (frontObsidian.matches > 0) {
                sawAnyObsidian = true;
                if (debugLog.get()) {
                    info("HB $update debug: dug scan step=%d front obsidian matches=%d -> PAVED_AHEAD", step, frontObsidian.matches);
                }
                return DugEndScanResult.PAVED_AHEAD;
            }

            Ratio floorObsidian = countTemplateMatches(floorTemplate, step, HIGHWAY_FLOOR_Y, this::isObsidian);
            if (floorObsidian.matches > 0) {
                sawAnyObsidian = true;
                if (debugLog.get()) {
                    info("HB $update debug: dug scan step=%d floor obsidian matches=%d -> PAVED_AHEAD", step, floorObsidian.matches);
                }
                return DugEndScanResult.PAVED_AHEAD;
            }

            Ratio frontNetherrack = countTemplateMatches(frontTemplate, step, Integer.MIN_VALUE, this::isNetherrack);
            netherrackCount += frontNetherrack.matches;
        }

        DugEndScanResult result;
        if (!sawAnyObsidian) {
            result = DugEndScanResult.DUG_END;
        } else {
            result = netherrackCount >= DUG_END_NETHERRACK_THRESHOLD
                ? DugEndScanResult.DUG_END
                : DugEndScanResult.CONTINUES_DUG;
        }
        if (debugLog.get()) {
            info(
                "HB $update debug: dug scan netherrackCount=%d threshold=%d sawAnyObsidian=%s -> %s",
                netherrackCount,
                DUG_END_NETHERRACK_THRESHOLD,
                sawAnyObsidian,
                result
            );
        }
        return result;
    }

    private Ratio countTemplateMatches(List<BlockPos> template, int stepOffset, int expectedY, Predicate<BlockState> matcher) {
        if (template == null || template.isEmpty()) return Ratio.EMPTY;

        int dx = dir.offsetX * stepOffset;
        int dz = dir.offsetZ * stepOffset;

        int preferredMatches = 0;
        int preferredTotal = 0;
        int allMatches = 0;
        int allTotal = 0;

        for (BlockPos basePos : template) {
            BlockPos pos = basePos.add(dx, 0, dz);
            BlockState state = mc.world.getBlockState(pos);
            boolean matches = matcher.test(state);

            allTotal++;
            if (matches) allMatches++;

            if (expectedY == Integer.MIN_VALUE || pos.getY() == expectedY) {
                preferredTotal++;
                if (matches) preferredMatches++;
            }
        }

        if (expectedY != Integer.MIN_VALUE && preferredTotal > 0) return new Ratio(preferredMatches, preferredTotal);
        return new Ratio(allMatches, allTotal);
    }

    private boolean isObsidian(BlockState state) {
        return state.isOf(Blocks.OBSIDIAN);
    }

    private boolean isRailingBlock(BlockState state) {
        return !state.isAir() && !state.isReplaceable();
    }

    private boolean isExcavatedSpace(BlockState state) {
        return state.isAir() || state.isReplaceable();
    }

    private boolean isNetherrack(BlockState state) {
        return state.isOf(Blocks.NETHERRACK);
    }

    /**
     * Checks one full-width cross-section, {@code step} blocks ahead of {@code origin} along
     * {@code travelDir}, against the currently configured profile: floor fully obsidian (paved)
     * or fully a diggable floor block (dug), and the interior above the floor fully clear.
     * Used to detect a repaired-vs-broken highway for the Highway Traveler auto handoff.
     * {@code origin}'s Y is the highway's interior base (one block above the floor) — same
     * convention as {@link meteordevelopment.meteorclient.utils.misc.MBlockPos#coerceBlockLevel},
     * i.e. relative to wherever the player currently is, not a fixed absolute Y.
     */
    public boolean isCrossSectionHealthy(BlockPos origin, HorizontalDirection travelDir, int step, boolean pavedProfile) {
        if (mc.world == null) return false;

        int cx = origin.getX() + travelDir.offsetX * step;
        int cz = origin.getZ() + travelDir.offsetZ * step;
        int floorY = origin.getY() - 1;
        HorizontalDirection lateral = travelDir.rotateLeftSkipOne();
        int half = width.get() / 2;

        for (int w = -half; w < width.get() - half; w++) {
            int x = cx + lateral.offsetX * w;
            int z = cz + lateral.offsetZ * w;

            BlockState floorState = mc.world.getBlockState(new BlockPos(x, floorY, z));
            boolean floorOk = pavedProfile
                ? isObsidian(floorState)
                : (isNetherrack(floorState) || blocksToPlace.get().contains(floorState.getBlock()));
            if (!floorOk) return false;

            for (int h = 0; h < height.get(); h++) {
                BlockState interior = mc.world.getBlockState(new BlockPos(x, origin.getY() + h, z));
                if (!isExcavatedSpace(interior)) return false;
            }
        }
        return true;
    }

    /** True if every cross-section from 1 up to {@code maxSteps} ahead of the player is healthy. */
    public boolean isHighwayAheadClean(HorizontalDirection travelDir, int maxSteps) {
        return isHighwayAheadClean(travelDir, maxSteps, 0);
    }

    /**
     * Same as {@link #isHighwayAheadClean(HorizontalDirection, int)}, but for each step also
     * accepts a healthy cross-section anywhere within {@code verticalTolerance} blocks above or
     * below the caller's current height. Elytra-bounce travel constantly bobs up and down, so a
     * strict single-Y check would misread normal bounce altitude as a broken highway; a small
     * tolerance lets the check ride out that bob instead of chasing it.
     */
    public boolean isHighwayAheadClean(HorizontalDirection travelDir, int maxSteps, int verticalTolerance) {
        if (mc.player == null || travelDir == null) return false;
        boolean pavedProfile = THMSystem.get().mode.get() == THMSystem.Mode.HighwayBuilding;
        int baseX = mc.player.getBlockX();
        int baseY = (int) Math.round(mc.player.getY());
        int baseZ = mc.player.getBlockZ();

        for (int step = 1; step <= maxSteps; step++) {
            boolean stepHealthy = false;
            for (int dy = -verticalTolerance; dy <= verticalTolerance; dy++) {
                BlockPos origin = new BlockPos(baseX, baseY + dy, baseZ);
                if (isCrossSectionHealthy(origin, travelDir, step, pavedProfile)) {
                    stepHealthy = true;
                    break;
                }
            }
            if (!stepHealthy) return false;
        }
        return true;
    }

    private List<BlockPos> snapshotTemplate(MBPIterator iterator) {
        if (iterator == null) return Collections.emptyList();

        List<BlockPos> positions = new ArrayList<>();
        try {
            while (iterator.hasNext()) {
                MBlockPos pos = iterator.next();
                positions.add(new BlockPos(pos.x, pos.y, pos.z));
            }
        } catch (StackOverflowError ignored) {
            return Collections.emptyList();
        }

        return positions;
    }

    private record Ratio(int matches, int total) {
        private static final Ratio EMPTY = new Ratio(0, 0);

        private double value() {
            return total <= 0 ? 0.0 : (double) matches / total;
        }
    }

    private enum DugEndScanResult {
        CONTINUES_DUG,
        DUG_END,
        PAVED_AHEAD
    }

    private String directionToKitbotCommand(HorizontalDirection dir) {
        if (dir == null) return null;

        boolean digging = inferDugModeForUpdate(false, false);

        String base = switch (dir) {
            case North -> "N";
            case South -> "S";
            case East -> "E";
            case West -> "W";
            case NorthEast -> "NE";
            case NorthWest -> "NW";
            case SouthEast -> "SE";
            case SouthWest -> "SW";
        };

        return digging ? "dug" + base : base;
    }

    private boolean inferDugModeForUpdate(boolean hasDugContext, boolean hasPavedContext) {
        if (mc.player == null || mc.world == null) return hasDugContext && !hasPavedContext;

        BlockState floorUnderPlayer = mc.world.getBlockState(mc.player.getBlockPos().down());
        if (isObsidian(floorUnderPlayer)) return false;

        if (hasDugContext && !hasPavedContext) return true;
        if (hasPavedContext && !hasDugContext) return false;

        int playerY = mc.player.getBlockY();
        if (playerY == HIGHWAY_FLOOR_Y) return true;
        if (playerY == HIGHWAY_RAILING_Y) return false;

        if (hasDugContext && hasPavedContext) {
            if (isObsidian(floorUnderPlayer)) return false;
            if (isNetherrack(floorUnderPlayer)) return true;
        }

        return playerY <= HIGHWAY_FLOOR_Y;
    }

    private void tickKitbotPeriodicUpdate() {
        if (!kitbotPeriodicUpdate.get() || mc.world == null || dir == null) return;

        if (kitbotPeriodicUpdateActive) {
            if (mc.world.getTime() - kitbotPeriodicUpdateStartTick >= 1200) {
                info("No response from KitBot1 after 60s (periodic update). Continuing build.");
                kitbotPeriodicUpdateActive = false;
            }
            return;
        }

        if (mc.world.getTime() < kitbotPeriodicUpdateNextTick) return;

        if (isRestockState(state)) {
            kitbotPeriodicUpdatePending = true;
            kitbotPeriodicUpdateNextTick = mc.world.getTime() + KITBOT_PERIODIC_UPDATE_INTERVAL_TICKS;
            return;
        }

        sendKitbotPeriodicUpdate();
    }

    private void sendKitbotPeriodicUpdate() {
        if (!isOnOfficialHighway() || dir == null || mc.world == null) {
            kitbotPeriodicUpdatePending = false;
            kitbotPeriodicUpdateNextTick = mc.world.getTime() + KITBOT_PERIODIC_UPDATE_INTERVAL_TICKS;
            return;
        }
        String kitbotDir = directionToKitbotCommand(dir);
        if (kitbotDir == null) {
            kitbotPeriodicUpdatePending = false;
            kitbotPeriodicUpdateNextTick = mc.world.getTime() + KITBOT_PERIODIC_UPDATE_INTERVAL_TICKS;
            return;
        }
        ChatUtils.sendPlayerMsg("/msg KitBot1 $update " + kitbotDir);
        info("Sent periodic $update %s to KitBot1. Waiting up to 60s for teleport...", kitbotDir);
        kitbotPeriodicUpdateActive = true;
        kitbotPeriodicUpdateStartTick = mc.world.getTime();
        kitbotPeriodicUpdateTpAccepted = false;
        kitbotPeriodicUpdatePending = false;
        kitbotPeriodicUpdateNextTick = mc.world.getTime() + KITBOT_PERIODIC_UPDATE_INTERVAL_TICKS;
    }

    private void buildKitbotUpdateEnclosure() {
        if (mc.player == null || mc.world == null) return;

        BlockPos base = mc.player.getBlockPos();
        List<BlockPos> enclosureTargets = List.of(
            base.north(),
            base.south(),
            base.east(),
            base.west(),
            base.north().up(),
            base.south().up(),
            base.east().up(),
            base.west().up(),
            base.up(2)
        );

        int placed = 0;
        int attempted = 0;

        for (BlockPos target : enclosureTargets) {
            if (target.equals(base) || target.equals(base.up())) continue;

            BlockState state = mc.world.getBlockState(target);
            if (!state.isAir() && !state.isReplaceable()) continue;
            if (!BlockUtils.canPlace(target)) continue;

            int slot = State.Forward.findAndMoveToHotbar(
                this,
                stack -> stack.getItem() instanceof BlockItem && isDroppableTrashStack(stack),
                false
            );
            if (slot == -1) {
                warning("KitBot enclosure: no trash blocks available in inventory/hotbar.");
                break;
            }

            attempted++;
            if (tryPlaceBlock(target, slot, rotation.get().place)) placed++;
        }

        if (placed > 0) info("KitBot enclosure placed %d/%d trash blocks.", placed, attempted);
        else warning("KitBot enclosure could not place any trash blocks.");
    }

    private StatsArtifactSnapshot peekAuthoritativeStatsArtifact() {
        StatsArtifactSnapshot canonical = peekStatsArtifact(resolveCanonicalStatsArtifactPath(), StatsArtifactKind.CANONICAL);
        StatsArtifactSnapshot finalization = peekStatsArtifact(resolveFinalizationRecordPath(), StatsArtifactKind.FINALIZATION);
        StatsArtifactSnapshot shadow = peekStatsArtifact(resolveShadowStatsArtifactPath(), StatsArtifactKind.SHADOW);

        StatsArtifactSnapshot selected = selectAuthoritativeStatsArtifact(canonical, finalization, shadow);
        if (selected == null) return null;
        if (consumedStatsArtifactKeys.contains(identityOf(selected).key())) return null;
        return selected;
    }

    private StatsArtifactSnapshot peekStatsArtifact(Path path, StatsArtifactKind kind) {
        if (!Files.exists(path)) return null;

        List<String> lines;
        try {
            lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            return null;
        }

        if (lines.isEmpty() || !STATS_ARTIFACT_MAGIC.equals(lines.get(0).trim())) return null;

        String versionValue = null;
        String nonceValue = null;
        String cipherValue = null;
        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line == null) continue;
            line = line.trim();
            if (line.isEmpty()) continue;
            String[] parts = line.split("\\|", 2);
            if (parts.length != 2) continue;
            switch (parts[0]) {
                case "version" -> versionValue = parts[1];
                case "nonce" -> nonceValue = parts[1];
                case "ciphertext" -> cipherValue = parts[1];
                default -> { }
            }
        }

        if (parseIntSafe(versionValue, -1) != STATS_ARTIFACT_VERSION) return null;

        try {
            StatsArtifactSnapshot snapshot = parseStatsArtifactPayload(kind, decryptStatsArtifactPayload(nonceValue, cipherValue));
            return validateStatsArtifactSnapshot(snapshot) ? snapshot : null;
        } catch (IOException | GeneralSecurityException | IllegalArgumentException ignored) {
            return null;
        }
    }

    public boolean isInForwardState() {
        return state == State.Forward;
    }

    public boolean isInCenterState() {
        return state == State.Center;
    }

    public boolean isDesyncWiggleProbeActive() {
        return desyncWiggleProbe != null;
    }

    public DesyncWiggleProbeResult tryStartForwardDesyncWiggleProbe(int episodeId, String sourceSummary, int packetActions, int distinctTargets, int windowTicks) {
        if (desyncWiggleProbe != null) return DesyncWiggleProbeResult.AlreadyRunning;
        if (!isActive() || mc.player == null || mc.world == null || input == null || !isInForwardState() || dir == null || isTpsThrottlePaused()) {
            return DesyncWiggleProbeResult.Unavailable;
        }
        if (lastForwardDesyncWiggleEpisodeId == episodeId) return DesyncWiggleProbeResult.AlreadyUsed;

        lastForwardDesyncWiggleEpisodeId = episodeId;
        releaseActiveDoubleMineRuntime(true);
        startDesyncWiggleProbe(
            DesyncWiggleReason.ForwardPacketDesync,
            State.Forward,
            null,
            "forward-packet-desync",
            sourceSummary,
            episodeId,
            packetActions,
            distinctTargets,
            windowTicks
        );
        return DesyncWiggleProbeResult.Started;
    }

    private boolean tryStartEnclosureDesyncWiggleProbe(State placementState, BlockPos target, String label) {
        if (desyncWiggleProbe != null) return true;
        if (mc.player == null || mc.world == null || input == null || placementState == null || target == null) return false;
        BlockPos immutableTarget = target.toImmutable();
        if (enclosureDesyncWiggleUsedTarget != null && enclosureDesyncWiggleUsedTarget.equals(immutableTarget)) return false;

        enclosureDesyncWiggleUsedTarget = immutableTarget;
        String sourceSummary = String.format(
            Locale.ROOT,
            "state=%s label=%s pendingTarget=%s attempts=%d/%d interactPackets=%d firstPacketAge=%d lastPacketAge=%d recentered=%s confirmWait=%d packetTarget=%s centerTarget=%s player=(%.6f,%.6f,%.6f)",
            stateName(placementState),
            label == null ? "" : label,
            formatBlockPos(immutableTarget),
            pendingEnclosurePlacementAttempts,
            ENCLOSURE_PLACEMENT_CONFIRM_MAX_ATTEMPTS,
            pendingEnclosurePlacementInteractPackets,
            pendingEnclosurePlacementFirstPacketAge,
            pendingEnclosurePlacementLastPacketAge,
            pendingEnclosurePlacementRecentered,
            pendingEnclosurePlacementConfirmWaitTicks,
            formatBlockPos(pendingEnclosurePlacementPacketTarget),
            formatBlockPos(pendingEnclosurePlacementCenterTarget),
            mc.player.getX(),
            mc.player.getY(),
            mc.player.getZ()
        );
        startDesyncWiggleProbe(
            DesyncWiggleReason.EnclosurePlacementDesync,
            placementState,
            immutableTarget,
            label,
            sourceSummary,
            -1,
            pendingEnclosurePlacementInteractPackets,
            1,
            0
        );
        return true;
    }

    private void startDesyncWiggleProbe(
        DesyncWiggleReason reason,
        State resumeState,
        BlockPos targetPos,
        String label,
        String sourceSummary,
        int forwardEpisodeId,
        int packetActions,
        int distinctTargets,
        int windowTicks
    ) {
        if (mc.player == null) return;

        if (input != null) input.stop();
        desyncWiggleProbe = new DesyncWiggleProbe(
            reason,
            state,
            resumeState,
            targetPos,
            label,
            sourceSummary,
            forwardEpisodeId,
            packetActions,
            distinctTargets,
            windowTicks,
            mc.player.getX(),
            mc.player.getY(),
            mc.player.getZ()
        );
        logDesyncWiggleProbe("start", desyncWiggleProbe, "probe started");
    }

    private boolean tickDesyncWiggleProbe() {
        DesyncWiggleProbe probe = desyncWiggleProbe;
        if (probe == null) return false;

        if (mc.player == null || input == null) {
            clearDesyncWiggleProbe("missing-player-or-input");
            return true;
        }

        input.stop();
        String phase;
        if (probe.tick < DESYNC_WIGGLE_LEFT_TICKS) {
            input.left(true);
            phase = "left";
        } else if (probe.tick < DESYNC_WIGGLE_LEFT_TICKS + DESYNC_WIGGLE_RIGHT_TICKS) {
            input.right(true);
            phase = "right";
        } else {
            phase = "settle";
        }

        logDesyncWiggleProbe("tick", probe, "phase=" + phase);
        probe.tick++;

        if (probe.tick >= DESYNC_WIGGLE_TOTAL_TICKS) {
            finishDesyncWiggleProbe(probe);
        }

        return true;
    }

    private void finishDesyncWiggleProbe(DesyncWiggleProbe probe) {
        if (input != null) input.stop();

        double endX = mc.player == null ? Double.NaN : mc.player.getX();
        double endY = mc.player == null ? Double.NaN : mc.player.getY();
        double endZ = mc.player == null ? Double.NaN : mc.player.getZ();
        double horizontalDelta = Double.isFinite(endX) && Double.isFinite(endZ)
            ? Math.hypot(endX - probe.startX, endZ - probe.startZ)
            : Double.NaN;
        boolean positionJumpEvidence = Double.isFinite(horizontalDelta) && horizontalDelta >= DESYNC_WIGGLE_SNAP_DISTANCE;

        logDesyncWiggleProbe(
            "finish",
            probe,
            String.format(
                Locale.ROOT,
                "end=(%.6f,%.6f,%.6f) horizontalDelta=%.6f correctionPacket=%s correctionType=%s correctionEntityId=%d positionJumpEvidence=%s returnState=%s",
                endX,
                endY,
                endZ,
                horizontalDelta,
                probe.correctionPacketObserved,
                probe.correctionPacketType,
                probe.correctionEntityId,
                positionJumpEvidence,
                stateName(probe.resumeState)
            )
        );

        // The wiggle itself is what moved the player; undo that unless something actually
        // points to a genuine server-side desync.
        if (!probe.correctionPacketObserved && !positionJumpEvidence && mc.player != null) {
            mc.player.setPosition(probe.startX, probe.startY, probe.startZ);
        }

        desyncWiggleProbe = null;

        if (probe.reason == DesyncWiggleReason.ForwardPacketDesync && isActive()) {
            setState(State.Center, State.Forward);
        }
    }

    private void clearDesyncWiggleProbe(String reason) {
        DesyncWiggleProbe probe = desyncWiggleProbe;
        if (input != null) input.stop();
        if (probe != null) logDesyncWiggleProbe("clear", probe, reason);
        desyncWiggleProbe = null;
    }

    private void noteDesyncWiggleCorrectionPacket(String packetType, int entityId) {
        DesyncWiggleProbe probe = desyncWiggleProbe;
        if (probe == null) return;
        probe.correctionPacketObserved = true;
        probe.correctionPacketType = packetType == null ? "" : packetType;
        probe.correctionEntityId = entityId;
        logDesyncWiggleProbe("correction-packet", probe, "packet=" + probe.correctionPacketType + " entityId=" + entityId);
    }

    private void logDesyncWiggleProbe(String phase, DesyncWiggleProbe probe, String detail) {
        if (probe == null) return;

        String line = String.format(
            Locale.ROOT,
            "HB desync-wiggle phase=%s reason=%s tick=%d/%d startState=%s currentState=%s resumeState=%s target=%s label=%s episode=%d packetActions=%d distinctTargets=%d windowTicks=%d start=(%.6f,%.6f,%.6f) current=%s correctionPacket=%s correctionType=%s correctionEntityId=%d source=%s detail=%s",
            phase,
            probe.reason,
            probe.tick,
            DESYNC_WIGGLE_TOTAL_TICKS,
            stateName(probe.startState),
            stateName(state),
            stateName(probe.resumeState),
            formatBlockPos(probe.targetPos),
            probe.label,
            probe.forwardEpisodeId,
            probe.packetActions,
            probe.distinctTargets,
            probe.windowTicks,
            probe.startX,
            probe.startY,
            probe.startZ,
            mc.player == null
                ? "null"
                : String.format(Locale.ROOT, "(%.6f,%.6f,%.6f)", mc.player.getX(), mc.player.getY(), mc.player.getZ()),
            probe.correctionPacketObserved,
            probe.correctionPacketType,
            probe.correctionEntityId,
            probe.sourceSummary,
            detail == null ? "" : detail
        );

        writeThmDebugLog(HIGHWAYBUILDER_DEBUG_FILE_NAME, formatThmDebugLine("%s", line));
        if (probe.reason == DesyncWiggleReason.EnclosurePlacementDesync) {
            writeThmDebugLog(RESTOCK_DEBUG_FILE_NAME, formatThmDebugLine("%s", line));
        }
    }

    @SuppressWarnings("unchecked")
    private boolean doesAutoGapWantToEat(AutoGap autoGap) {
        Setting<Boolean> alwaysSetting = (Setting<Boolean>) autoGap.settings.get("always");
        Setting<Boolean> allowEgapSetting = (Setting<Boolean>) autoGap.settings.get("allow-egap");
        if (alwaysSetting == null || allowEgapSetting == null) return false;

        boolean requiresEGap = false;
        if (!alwaysSetting.get()) {
            boolean regenTrigger = shouldAutoGapForRegeneration(autoGap);
            requiresEGap = requiresEGapForPotions(autoGap, allowEgapSetting.get());
            if (!regenTrigger && !requiresEGap && !shouldAutoGapForHealth(autoGap)) return false;
        }

        return hasAutoGapHotbarFood(allowEgapSetting.get(), requiresEGap);
    }

    @SuppressWarnings("unchecked")
    private boolean shouldAutoGapForHealth(AutoGap autoGap) {
        Setting<Boolean> healthEnabledSetting = (Setting<Boolean>) autoGap.settings.get("health-enabled");
        Setting<Integer> healthThresholdSetting = autoGap.settings.get("health-threshold", Integer.class);
        if (healthEnabledSetting == null || healthThresholdSetting == null || !healthEnabledSetting.get() || mc.player == null) return false;

        int health = Math.round(mc.player.getHealth() + mc.player.getAbsorptionAmount());
        return health < healthThresholdSetting.get();
    }

    @SuppressWarnings("unchecked")
    private boolean shouldAutoGapForRegeneration(AutoGap autoGap) {
        Setting<Boolean> regenSetting = (Setting<Boolean>) autoGap.settings.get("potions-regeneration");
        return regenSetting != null && regenSetting.get() && isPotionMissingOrExpiring(StatusEffects.REGENERATION, autoGap);
    }

    @SuppressWarnings("unchecked")
    private boolean requiresEGapForPotions(AutoGap autoGap, boolean allowEgap) {
        if (!allowEgap) return false;

        Setting<Boolean> fireResSetting = (Setting<Boolean>) autoGap.settings.get("potions-fire-resistance");
        if (fireResSetting != null && fireResSetting.get() && isPotionMissingOrExpiring(StatusEffects.FIRE_RESISTANCE, autoGap)) return true;

        Setting<Boolean> absorptionSetting = (Setting<Boolean>) autoGap.settings.get("potions-absorption");
        return absorptionSetting != null && absorptionSetting.get() && isPotionMissingOrExpiring(StatusEffects.ABSORPTION, autoGap);
    }

    @SuppressWarnings("unchecked")
    private boolean isPotionMissingOrExpiring(RegistryEntry<StatusEffect> effect, AutoGap autoGap) {
        if (mc.player == null) return false;

        Setting<Boolean> beforeExpirySetting = (Setting<Boolean>) autoGap.settings.get("before-expiry");
        Setting<Integer> expiryThresholdSetting = autoGap.settings.get("expiry-threshold", Integer.class);

        StatusEffectInstance instance = mc.player.getStatusEffect(effect);
        if (instance == null) return true;
        return beforeExpirySetting != null
            && beforeExpirySetting.get()
            && expiryThresholdSetting != null
            && instance.getDuration() <= expiryThresholdSetting.get();
    }

    private boolean hasAutoGapHotbarFood(boolean allowEgap, boolean requiresEGap) {
        if (mc.player == null) return false;

        for (int i = 0; i < 9; i++) {
            Item item = mc.player.getInventory().getStack(i).getItem();
            if (item == Items.ENCHANTED_GOLDEN_APPLE && allowEgap) return true;
            if (item == Items.GOLDEN_APPLE && !requiresEGap) return true;
        }

        return false;
    }

    private boolean isUsingGapLikeFood(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        Item item = stack.getItem();
        return item == Items.GOLDEN_APPLE || item == Items.ENCHANTED_GOLDEN_APPLE;
    }

    private EatingPauseOwner resolveActiveEatingPauseOwner() {
        AutoEat autoEat = Modules.get().get(AutoEat.class);
        if (autoEat != null && autoEat.eating) return EatingPauseOwner.AUTO_EAT;

        AutoGap autoGap = Modules.get().get(AutoGap.class);
        if (autoGap != null && autoGap.isEating()) return EatingPauseOwner.AUTO_GAP;

        return EatingPauseOwner.NONE;
    }

    private boolean isKillAuraPauseActive() {
        KillAura killAura = Modules.get().get(KillAura.class);
        return killAura != null && killAura.attacking;
    }

    private boolean shouldPauseForEatingWatchdog(EatingPauseOwner activeOwner) {
        if (activeOwner != EatingPauseOwner.NONE) return true;
        if (eatingRetryPhase != EatingRetryPhase.NONE) return true;
        if (eatingPauseOwner == EatingPauseOwner.NONE) return false;

        StuckEatingRetryBridge bridge = getEatingRetryBridge(eatingPauseOwner);
        return bridge != null && bridge.thm$stillNeedsToEat();
    }

    private void tickEatingPauseWatchdog(EatingPauseOwner activeOwner) {
        long now = System.currentTimeMillis();

        if (eatingRetryPhase != EatingRetryPhase.NONE) {
            if (activeOwner != EatingPauseOwner.NONE && activeOwner != eatingPauseOwner) {
                startEatingPauseEpisode(activeOwner, now);
                return;
            }

            processEatingRetryPhase(now);
            return;
        }

        if (activeOwner == EatingPauseOwner.NONE) {
            if (eatingPauseOwner == EatingPauseOwner.NONE) return;

            StuckEatingRetryBridge bridge = getEatingRetryBridge(eatingPauseOwner);
            if (bridge == null || !bridge.thm$stillNeedsToEat()) {
                resetEatingPauseWatchdog();
                return;
            }

            if (eatingPauseLastProgressAtMs == 0L) eatingPauseLastProgressAtMs = now;
            if (now - eatingPauseLastProgressAtMs >= EatingPauseWatchdogState.NO_PROGRESS_TIMEOUT_MS) {
                startEatingRetry(now);
            }
            return;
        }

        if (activeOwner != eatingPauseOwner) {
            startEatingPauseEpisode(activeOwner, now);
        }

        StuckEatingRetryBridge bridge = getEatingRetryBridge(activeOwner);
        if (bridge == null) {
            resetEatingPauseWatchdog();
            return;
        }

        if (!bridge.thm$stillNeedsToEat()) {
            resetEatingPauseWatchdog();
            return;
        }

        observeEatingPauseProgress(activeOwner, bridge, now);
        if (isEatingPauseStuck(bridge, now)) {
            startEatingRetry(now);
        }
    }

    private void startEatingPauseEpisode(EatingPauseOwner owner, long now) {
        if (eatingPauseOwner != EatingPauseOwner.NONE && eatingPauseOwner != owner) {
            endEatingPauseRecovery(eatingPauseOwner, eatingPauseRecoveryToken);
        }

        eatingPauseOwner = owner;
        eatingRetryPhase = EatingRetryPhase.NONE;
        eatingPauseRetryAttempts = 0;
        eatingHotbarSwapRecoveryAttempts = 0;
        eatingPauseLastProgressAtMs = now;
        eatingPauseUseStartedAtMs = 0L;
        eatingPauseLastUseTime = mc.player != null ? mc.player.getItemUseTime() : 0;
        eatingPauseLastRelevantItemCount = countRelevantEatingItems(owner);
        eatingPauseSettleTicks = 0;
        eatingRetryPhaseDeadlineAtMs = 0L;
        eatingPauseRecoveryToken = 0L;
        eatingPauseFullToggleRecoveryAttempted = false;
    }

    private void resetEatingPauseWatchdog() {
        endEatingPauseRecovery(eatingPauseOwner, eatingPauseRecoveryToken);
        eatingPauseOwner = EatingPauseOwner.NONE;
        eatingRetryPhase = EatingRetryPhase.NONE;
        eatingPauseLastProgressAtMs = 0L;
        eatingPauseUseStartedAtMs = 0L;
        eatingPauseRetryAttempts = 0;
        eatingHotbarSwapRecoveryAttempts = 0;
        eatingPauseLastUseTime = 0;
        eatingPauseLastRelevantItemCount = -1;
        eatingPauseSettleTicks = 0;
        eatingRetryPhaseDeadlineAtMs = 0L;
        eatingPauseRecoveryToken = 0L;
        eatingPauseFullToggleRecoveryAttempted = false;
    }

    private long ensureEatingPauseRecoveryToken() {
        if (eatingPauseOwner == EatingPauseOwner.NONE) return 0L;

        StuckEatingRetryBridge bridge = getEatingRetryBridge(eatingPauseOwner);
        if (bridge == null) return 0L;

        long token = bridge.thm$beginWatchdogRecovery();
        if (token != 0L) eatingPauseRecoveryToken = token;
        return token;
    }

    private void endEatingPauseRecovery(EatingPauseOwner owner, long token) {
        if (owner == EatingPauseOwner.NONE || token == 0L) return;

        StuckEatingRetryBridge bridge = getEatingRetryBridge(owner);
        if (bridge != null) bridge.thm$endWatchdogRecovery(token);
    }

    private void observeEatingPauseProgress(EatingPauseOwner owner, StuckEatingRetryBridge bridge, long now) {
        int relevantItemCount = countRelevantEatingItems(owner);
        if (relevantItemCount != -1 && eatingPauseLastRelevantItemCount != -1 && relevantItemCount < eatingPauseLastRelevantItemCount) {
            eatingPauseLastProgressAtMs = now;
        }

        boolean validUse = bridge.thm$hasValidCurrentEatingItem() && mc.player != null && mc.player.isUsingItem();
        int itemUseTime = mc.player != null ? mc.player.getItemUseTime() : 0;

        if (validUse) {
            if (itemUseTime > eatingPauseLastUseTime) {
                eatingPauseLastProgressAtMs = now;
            }

            if (eatingPauseUseStartedAtMs == 0L) eatingPauseUseStartedAtMs = now;
        } else {
            eatingPauseUseStartedAtMs = 0L;
        }

        eatingPauseLastUseTime = itemUseTime;
        eatingPauseLastRelevantItemCount = relevantItemCount;
    }

    private boolean isEatingPauseStuck(StuckEatingRetryBridge bridge, long now) {
        if (bridge == null || !bridge.thm$stillNeedsToEat()) return false;

        if (bridge.thm$hasValidCurrentEatingItem()
            && mc.player != null
            && mc.player.isUsingItem()
            && eatingPauseUseStartedAtMs != 0L
            && now - eatingPauseUseStartedAtMs >= EatingPauseWatchdogState.MAX_CONTINUOUS_USE_MS) {
            return true;
        }

        return eatingPauseLastProgressAtMs != 0L
            && now - eatingPauseLastProgressAtMs >= EatingPauseWatchdogState.NO_PROGRESS_TIMEOUT_MS;
    }

    private void startEatingRetry(long now) {
        if (eatingPauseOwner == EatingPauseOwner.NONE) {
            resetEatingPauseWatchdog();
            return;
        }

        if (ensureEatingPauseRecoveryToken() == 0L) {
            finishEatingRetryFailure(now);
            return;
        }

        if (eatingPauseRetryAttempts >= EatingPauseWatchdogState.MAX_RETRIES) {
            if (attemptEatingHotbarSwapRecovery(now)) return;
            finishEatingRetryFailure(now);
            return;
        }

        StuckEatingRetryBridge bridge = getEatingRetryBridge(eatingPauseOwner);
        if (bridge == null) {
            finishEatingRetryFailure(now);
            return;
        }

        bridge.thm$forceStopEating(eatingPauseRecoveryToken);
        resetEatingRetryProgress(now);

        if (mc.currentScreen != null) {
            closeHandledScreen();
            eatingRetryPhase = EatingRetryPhase.PENDING_SCREEN_CLOSE;
            eatingPauseSettleTicks = 0;
            return;
        }

        eatingRetryPhase = EatingRetryPhase.PENDING_CURSOR_DROP;
    }

    private void processEatingRetryPhase(long now) {
        switch (eatingRetryPhase) {
            case NONE -> {
            }
            case PENDING_SCREEN_CLOSE -> {
                if (mc.currentScreen == null) {
                    eatingRetryPhase = EatingRetryPhase.PENDING_SCREEN_SETTLE;
                    eatingPauseSettleTicks = 1;
                }
            }
            case PENDING_SCREEN_SETTLE -> {
                if (eatingPauseSettleTicks > 0) {
                    eatingPauseSettleTicks--;
                    return;
                }

                eatingRetryPhase = EatingRetryPhase.PENDING_CURSOR_DROP;
            }
            case PENDING_CURSOR_DROP -> {
                forceCursorDropAttemptBypassingAntiDrop("Eating-retry");
                eatingRetryPhase = EatingRetryPhase.PENDING_RESTART;
            }
            case PENDING_RESTART -> {
                StuckEatingRetryBridge bridge = getEatingRetryBridge(eatingPauseOwner);
                if (bridge == null) {
                    finishEatingRetryFailure(now);
                    return;
                }

                StuckEatingRetryResult result = bridge.thm$forceRestartEating(eatingPauseRecoveryToken);
                eatingRetryPhase = EatingRetryPhase.NONE;
                eatingRetryPhaseDeadlineAtMs = 0L;
                resetEatingRetryProgress(now);

                switch (result) {
                    case CLEARED -> resetEatingPauseWatchdog();
                    case RESTARTED -> eatingPauseRetryAttempts++;
                    case IMPOSSIBLE -> {
                        eatingPauseRetryAttempts++;
                        if (eatingPauseRetryAttempts >= EatingPauseWatchdogState.MAX_RETRIES) {
                            if (attemptEatingHotbarSwapRecovery(now)) return;
                            finishEatingRetryFailure(now);
                        }
                    }
                }
            }
            case PENDING_HOTBAR_SWAP_DELAY -> {
                if (now < eatingRetryPhaseDeadlineAtMs) return;
                if (!isEatingHotbarSwapRecoveryStillValid()) {
                    finishEatingRetryFailure(now);
                    return;
                }

                int currentSelectedSlot = mc.player.getInventory().getSelectedSlot();
                int targetSlot = findEatingHotbarSwapTargetSlot();
                if (targetSlot == -1) {
                    finishEatingRetryFailure(now);
                    return;
                }

                if (currentSelectedSlot != targetSlot) InvUtils.swap(targetSlot, false);
                if (mc.player.getInventory().getSelectedSlot() != targetSlot) {
                    finishEatingRetryFailure(now);
                    return;
                }

                eatingHotbarSwapRecoveryAttempts++;
                eatingRetryPhase = EatingRetryPhase.PENDING_HOTBAR_SWAP_GRACE;
                eatingRetryPhaseDeadlineAtMs = now + EatingPauseWatchdogState.HOTBAR_SWAP_GRACE_MS;
                resetEatingRetryProgress(now);
            }
            case PENDING_HOTBAR_SWAP_GRACE -> {
                if (!isEatingHotbarSwapRecoveryStillValid()) {
                    finishEatingRetryFailure(now);
                    return;
                }

                StuckEatingRetryBridge bridge = getEatingRetryBridge(eatingPauseOwner);
                if (bridge == null) {
                    finishEatingRetryFailure(now);
                    return;
                }

                if (!bridge.thm$stillNeedsToEat()) {
                    resetEatingPauseWatchdog();
                    return;
                }

                if (now < eatingRetryPhaseDeadlineAtMs) return;

                resetEatingRetryProgress(now);
                StuckEatingRetryResult result = bridge.thm$forceRestartEating(eatingPauseRecoveryToken);
                eatingRetryPhase = EatingRetryPhase.NONE;
                eatingRetryPhaseDeadlineAtMs = 0L;
                resetEatingRetryProgress(now);

                switch (result) {
                    case CLEARED -> resetEatingPauseWatchdog();
                    case RESTARTED -> {
                    }
                    case IMPOSSIBLE -> {
                        if (attemptEatingHotbarSwapRecovery(now)) return;
                        finishEatingRetryFailure(now);
                    }
                }
            }
            case PENDING_MODULE_REENABLE -> {
                if (eatingPauseSettleTicks > 0) {
                    eatingPauseSettleTicks--;
                    return;
                }

                Module module = getEatingOwnerModule(eatingPauseOwner);
                if (module == null) {
                    error("Eating retry failed for %s.", eatingPauseOwner.label);
                    return;
                }

                if (!module.isActive()) module.toggle();

                eatingRetryPhase = EatingRetryPhase.PENDING_MODULE_RESTART_AFTER_REENABLE;
                eatingRetryPhaseDeadlineAtMs = 0L;
                resetEatingRetryProgress(now);
                eatingPauseSettleTicks = 0;
            }
            case PENDING_MODULE_RESTART_AFTER_REENABLE -> {
                StuckEatingRetryBridge bridge = getEatingRetryBridge(eatingPauseOwner);
                if (bridge == null) {
                    finishEatingRetryFailure(now);
                    return;
                }

                if (!bridge.thm$stillNeedsToEat()) {
                    resetEatingPauseWatchdog();
                    return;
                }

                StuckEatingRetryResult result = bridge.thm$forceRestartEating(eatingPauseRecoveryToken);
                eatingRetryPhase = EatingRetryPhase.NONE;
                eatingRetryPhaseDeadlineAtMs = 0L;
                resetEatingRetryProgress(now);

                switch (result) {
                    case CLEARED -> resetEatingPauseWatchdog();
                    case RESTARTED -> {
                    }
                    case IMPOSSIBLE -> finishEatingRetryFailure(now);
                }
            }
        }
    }

    private void finishEatingRetryFailure(long now) {
        eatingRetryPhase = EatingRetryPhase.NONE;
        eatingRetryPhaseDeadlineAtMs = 0L;
        if (attemptEatingOwnerFullToggleRecovery(now)) return;
        error("Eating retry failed for %s.", eatingPauseOwner.label);
    }

    private boolean attemptEatingHotbarSwapRecovery(long now) {
        if (!hasRemainingEatingHotbarSwapRecoveryAttempts()) return false;
        eatingRetryPhase = EatingRetryPhase.PENDING_HOTBAR_SWAP_DELAY;
        eatingRetryPhaseDeadlineAtMs = now + EatingPauseWatchdogState.HOTBAR_SWAP_DELAY_MS;
        return true;
    }

    private boolean hasRemainingEatingHotbarSwapRecoveryAttempts() {
        return getEatingHotbarSwapRecoveryMaxAttempts(eatingPauseOwner) > eatingHotbarSwapRecoveryAttempts;
    }

    private int getEatingHotbarSwapRecoveryMaxAttempts(EatingPauseOwner owner) {
        return switch (owner) {
            case AUTO_EAT, AUTO_GAP -> EatingPauseWatchdogState.HOTBAR_SWAP_MAX_ATTEMPTS;
            case NONE -> 0;
        };
    }

    private boolean isEatingHotbarSwapRecoveryStillValid() {
        if (mc.player == null) return false;
        if (getEatingHotbarSwapRecoveryMaxAttempts(eatingPauseOwner) <= 0) return false;

        Module module = getEatingOwnerModule(eatingPauseOwner);
        if (module == null || !module.isActive()) return false;

        StuckEatingRetryBridge bridge = getEatingRetryBridge(eatingPauseOwner);
        return bridge != null && bridge.thm$stillNeedsToEat();
    }

    private int findEatingHotbarSwapTargetSlot() {
        if (mc.player == null) return -1;

        int selectedSlot = mc.player.getInventory().getSelectedSlot();
        for (int slot = 0; slot < 9; slot++) {
            if (slot == selectedSlot) continue;

            ItemStack stack = mc.player.getInventory().getStack(slot);
            if (stack.isEmpty()) continue;
            if (stack.contains(DataComponentTypes.FOOD)) continue;
            return slot;
        }

        return -1;
    }

    private void resetEatingRetryProgress(long now) {
        eatingPauseUseStartedAtMs = 0L;
        eatingPauseLastUseTime = 0;
        eatingPauseLastRelevantItemCount = countRelevantEatingItems(eatingPauseOwner);
        eatingPauseLastProgressAtMs = now;
    }

    private boolean attemptEatingOwnerFullToggleRecovery(long now) {
        if (eatingPauseOwner == EatingPauseOwner.NONE || eatingPauseFullToggleRecoveryAttempted) return false;

        Module module = getEatingOwnerModule(eatingPauseOwner);
        if (module == null || !module.isActive()) return false;

        warning("Eating retry exhausted for %s. Performing last-ditch module toggle recovery.", eatingPauseOwner.label);

        try {
            module.toggle();
        } catch (Exception e) {
            THMAddon.LOG.warn("Failed last-ditch eating module toggle recovery for {}: {}", eatingPauseOwner.label, e.getMessage());
            return false;
        }

        if (module.isActive()) return false;

        eatingPauseFullToggleRecoveryAttempted = true;
        eatingRetryPhase = EatingRetryPhase.PENDING_MODULE_REENABLE;
        eatingRetryPhaseDeadlineAtMs = 0L;
        resetEatingRetryProgress(now);
        eatingPauseSettleTicks = EatingPauseWatchdogState.MODULE_REENABLE_DELAY_TICKS;
        return true;
    }

    private StuckEatingRetryBridge getEatingRetryBridge(EatingPauseOwner owner) {
        return switch (owner) {
            case AUTO_EAT -> {
                AutoEat autoEat = Modules.get().get(AutoEat.class);
                yield autoEat instanceof StuckEatingRetryBridge bridge ? bridge : null;
            }
            case AUTO_GAP -> {
                AutoGap autoGap = Modules.get().get(AutoGap.class);
                yield autoGap instanceof StuckEatingRetryBridge bridge ? bridge : null;
            }
            case NONE -> null;
        };
    }

    private Module getEatingOwnerModule(EatingPauseOwner owner) {
        return switch (owner) {
            case AUTO_EAT -> Modules.get().get(AutoEat.class);
            case AUTO_GAP -> Modules.get().get(AutoGap.class);
            case NONE -> null;
        };
    }

    private int countRelevantEatingItems(EatingPauseOwner owner) {
        if (mc.player == null) return -1;

        int count = 0;
        for (int i = 0; i < mc.player.getInventory().getMainStacks().size(); i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (isRelevantEatingItem(owner, stack)) count += stack.getCount();
        }

        ItemStack offhand = mc.player.getOffHandStack();
        if (isRelevantEatingItem(owner, offhand)) count += offhand.getCount();
        return count;
    }

    private boolean isRelevantEatingItem(EatingPauseOwner owner, ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;

        return switch (owner) {
            case AUTO_EAT -> stack.contains(DataComponentTypes.FOOD);
            case AUTO_GAP -> isUsingGapLikeFood(stack);
            case NONE -> false;
        };
    }

    private void touchForwardSchedulerPauseHeartbeat() {
        if (state != State.Forward || !useForwardRowSchedulerMode()) return;
        forwardSchedulerRuntime.lastProgressAtMs = System.currentTimeMillis();
    }

    public void hardFailForMonitorRecovery(String message, Object... args) {
        super.error(message, args);
        try {
            THMHwyMonitor.signalNonRestartHardFailFromHighwayBuilder();
        } catch (NoClassDefFoundError | ExceptionInInitializerError ignored) {
            // Baritone-dependent monitor not available.
        }

        monitorPauseDeactivateArmed = false;
        reconnectFailureDeactivateArmed = false;
        if (isActive()) {
            suppressThmHwyMonitorSync = true;
            try {
                disable();
            } finally {
                suppressThmHwyMonitorSync = false;
            }
        }

        if (disconnectOnToggle.get()) {
            disconnect(message, args);
        }
    }

    public boolean hardFailFromAutoLog(Text autoLogReason) {
        if (!isActive()) return false;
        if (!canDisconnectThroughHighwayBuilder()) return false;

        String reason = autoLogReason == null ? "unknown" : autoLogReason.getString();
        super.error("AutoLog triggered terminal HighwayBuilder logout: %s", reason);
        try {
            THMHwyMonitor.signalNonRestartHardFailFromHighwayBuilder();
        } catch (NoClassDefFoundError | ExceptionInInitializerError ignored) {
            // Baritone-dependent monitor not available.
        }

        monitorPauseDeactivateArmed = false;
        reconnectFailureDeactivateArmed = false;
        autoLogTerminalDeactivateArmed = true;

        suppressThmHwyMonitorSync = true;
        try {
            disable();
        } finally {
            suppressThmHwyMonitorSync = false;
        }

        if (!canDisconnectThroughHighwayBuilder()) return false;

        try {
            disconnect("AutoLog cause: [AutoLog] %s", reason);
            return true;
        } catch (RuntimeException e) {
            warning("Failed to disconnect through HighwayBuilder AutoLog path: %s", e.getClass().getSimpleName());
            return false;
        }
    }

    private boolean canDisconnectThroughHighwayBuilder() {
        return mc != null
            && mc.getNetworkHandler() != null
            && mc.getNetworkHandler().getConnection() != null
            && mc.getNetworkHandler().getConnection().isOpen();
    }

    public ReconnectResumeResult resumeFromReconnect(HorizontalDirection lockedDirection, long generation) {
        clearLastReconnectResumeFailure();
        HorizontalDirection cachedDirection = getCachedWorkingDirectionForMonitorReconnect(generation);
        if (lockedDirection == null || generation <= 0L) {
            return rememberReconnectResumeFailure("invalid-reconnect-request", generation, lockedDirection, cachedDirection);
        }
        if (mc.player == null || mc.world == null || !Utils.canUpdate()) {
            return rememberReconnectResumeFailure("player-or-world-unavailable", generation, lockedDirection, cachedDirection);
        }
        if (isActive()) {
            return rememberReconnectResumeFailure("builder-already-active", generation, lockedDirection, cachedDirection);
        }

        reconnectResumeContext = new ReconnectResumeContext(lockedDirection, generation);
        suppressThmHwyMonitorSync = true;
        try {
            toggle();
        } finally {
            suppressThmHwyMonitorSync = false;
        }

        if (!isActive()) {
            reconnectResumeContext = null;
            return rememberReconnectResumeFailure("activation-failed", generation, lockedDirection, cachedDirection);
        }

        if (dir != lockedDirection) {
            disableForReconnectResumeFailure();
            return rememberReconnectResumeFailure("direction-mismatch", generation, lockedDirection, cachedDirection);
        }

        ReconnectBaselineRestoreResult baselineRestore = restoreReconnectBaselineForResume(generation);
        if (!baselineRestore.success()) {
            disableForReconnectResumeFailure();
            return rememberReconnectResumeFailure(baselineRestore.reason(), generation, lockedDirection, cachedDirection);
        }

        setState(State.Center);
        if (!isActive() || dir != lockedDirection) {
            return rememberReconnectResumeFailure("post-center-direction-mismatch", generation, lockedDirection, cachedDirection);
        }
        return ReconnectResumeResult.ok();
    }

    public boolean completeReconnectResumeAfterManagedModules(long generation) {
        if (generation <= 0L) return false;
        if (!isActive() || mc.player == null || mc.world == null || !Utils.canUpdate()) return false;

        suspended = false;
        resumeThmSpeedAfterReconnectIfReady("reconnect-finalized");
        if (!isThmSpeedReconnectReady()) {
            writeReconnectDebug("resume finalization failed THM speed readiness cycle=%d useThmSpeed=%s suppressed=%s owned=%s snapshot=%s input=%s state=%s",
                generation,
                useThmSpeed.get(),
                thmSpeedReconnectSuppressed,
                thmSpeedOwnershipActive,
                thmSpeedSnapshot != null,
                input != null,
                getCommittedServerState().name()
            );
            return false;
        }

        boolean persisted = persistCurrentStatsSession(StatsSessionState.OPEN, true, 0L, "reconnect-resume-complete");
        if (!persisted) {
            writeReconnectDebug("resume finalization failed stats checkpoint cycle=%d", generation);
            return false;
        }

        return true;
    }

    private boolean isThmSpeedReconnectReady() {
        if (!useThmSpeed.get()) return true;

        return isActive()
            && !suspended
            && !thmSpeedReconnectSuppressed
            && thmSpeedOwnershipActive
            && thmSpeedSnapshot != null
            && input != null
            && mc.player != null
            && mc.world != null
            && isThmSpeedExecutionAllowed();
    }

    private void clearLastReconnectResumeFailure() {
        lastReconnectResumeFailureCycleId = 0L;
        lastReconnectResumeFailureReason = "";
        lastReconnectResumeSelectedDirectionName = "";
        lastReconnectResumeCachedDirectionName = "";
    }

    private ReconnectResumeResult rememberReconnectResumeFailure(
        String reason,
        long generation,
        HorizontalDirection selectedDirection,
        HorizontalDirection cachedDirection
    ) {
        lastReconnectResumeFailureCycleId = generation;
        lastReconnectResumeFailureReason = reason == null || reason.isBlank() ? "unknown" : reason;
        lastReconnectResumeSelectedDirectionName = selectedDirection == null ? "" : selectedDirection.name;
        lastReconnectResumeCachedDirectionName = cachedDirection == null ? "" : cachedDirection.name;
        writeReconnectDebug(
            "resume failed reason=%s cycle=%d selected=%s cached=%s active=%s state=%s",
            lastReconnectResumeFailureReason,
            generation,
            lastReconnectResumeSelectedDirectionName.isBlank() ? "none" : lastReconnectResumeSelectedDirectionName,
            lastReconnectResumeCachedDirectionName.isBlank() ? "none" : lastReconnectResumeCachedDirectionName,
            isActive(),
            stateName(state)
        );
        return ReconnectResumeResult.fail(lastReconnectResumeFailureReason);
    }

    public boolean prepareForMonitorReconnectPause(long generation) {
        if (generation <= 0L) return false;
        if (!hasCapturedReconnectBaselineLease(generation)) {
            if (reconnectBaselineLease != null && reconnectBaselineLease.generation() == generation) return false;
            if (centerSpeedOverrideActive) return false;
            // Otherwise a mid-mining Timer boost gets captured as "normal" and never turns off after resume.
            restoreEChestBreakSpeedIfOwned("monitor-reconnect-pause-prep");

            ReconnectBaselinePayload payload = captureReconnectBaselinePayload();
            if (payload == null) return false;

            reconnectBaselineLease = new ReconnectBaselineLease(generation, ReconnectBaselineLeaseState.CAPTURED, payload);
        }

        boolean persisted = persistCurrentStatsSession(StatsSessionState.OPEN, true, 0L, "monitor-reconnect-baseline");
        writeReconnectDebug(
            "prepared reconnect baseline cycle=%d direction=%s persisted=%s baselinePresent=%s",
            generation,
            reconnectBaselineLease == null || reconnectBaselineLease.payload() == null ? "none" : reconnectBaselineLease.payload().workingDirectionName(),
            persisted,
            hasCapturedReconnectBaselineLease(generation)
        );
        return persisted;
    }

    public boolean restoreCenterSpeedBaselineForFailedReconnect(long generation) {
        if (generation > 0L) {
            ReconnectBaselineRestoreResult result = restoreReconnectBaselineForResume(generation);
            if (result.success()) return true;
            writeReconnectDebug("safe reconnect failure defaults after baseline restore failed cycle=%d reason=%s", generation, result.reason());
        }

        Timer timer = Modules.get().get(Timer.class);
        if (timer != null) timer.setOverride(Timer.OFF);
        restoreCenterSpeedIfOwned("reconnect-safety-stop-fallback");
        return false;
    }

    public long getPendingReconnectAbortCycleId() {
        if (isActive()) return 0L;

        StatsArtifactSnapshot snapshot = findNewestOpenStatsArtifact();
        if (snapshot == null || snapshot.reconnectBaselinePayload() == null || snapshot.reconnectCycleId() <= 0L) return 0L;
        if (hasFinalizationArtifactForSession(snapshot.sessionId())
            && !hasParkedThmSpeedSnapshot()) {
            return 0L;
        }
        return snapshot == null ? 0L : snapshot.reconnectCycleId();
    }

    public ReconnectAbortRestoreOutcome abortPreparedReconnectPause(long expectedCycleId, String reason) {
        if (expectedCycleId <= 0L) return ReconnectAbortRestoreOutcome.RejectedGeneration;
        if (mc.player == null || mc.world == null || !Utils.canUpdate()) {
            return ReconnectAbortRestoreOutcome.DeferredNoLiveWorld;
        }

        ReconnectBaselinePayload inMemoryPayload = null;
        if (reconnectBaselineLease != null && reconnectBaselineLease.generation() == expectedCycleId) {
            inMemoryPayload = reconnectBaselineLease.payload();
        }

        StatsArtifactSnapshot newestOpenArtifact = findNewestOpenStatsArtifact();
        StatsArtifactSnapshot matchingArtifact = null;
        if (newestOpenArtifact != null && newestOpenArtifact.reconnectBaselinePayload() != null) {
            if (newestOpenArtifact.reconnectCycleId() != expectedCycleId) {
                return ReconnectAbortRestoreOutcome.RejectedGeneration;
            }
            matchingArtifact = newestOpenArtifact;
        }

        ReconnectBaselinePayload payload = inMemoryPayload != null
            ? inMemoryPayload
            : matchingArtifact == null ? null : matchingArtifact.reconnectBaselinePayload();
        if (payload == null && matchingArtifact == null && !hasParkedThmSpeedSnapshot()) {
            return ReconnectAbortRestoreOutcome.RejectedGeneration;
        }

        StatsArtifactSnapshot verifiedArtifactForRewrite = null;
        if (matchingArtifact != null && !hasFinalizationArtifactForSession(matchingArtifact.sessionId())) {
            StatsArtifactSnapshot current = peekStatsArtifact(resolveStatsArtifactPath(matchingArtifact.kind()), matchingArtifact.kind());
            if (!sameReconnectArtifactIdentity(matchingArtifact, current)) {
                return ReconnectAbortRestoreOutcome.RejectedGeneration;
            }
            verifiedArtifactForRewrite = current;
        }

        if (payload != null) {
            ReconnectBaselineRestoreResult baselineRestore = restoreReconnectBaselinePayload(payload);
            if (!baselineRestore.success()) {
                writeReconnectDebug("abort restore baseline failed cycle=%d reason=%s", expectedCycleId, baselineRestore.reason());
                return ReconnectAbortRestoreOutcome.StatsPersistenceFailed;
            }
        }

        if (!restoreParkedThmSpeedSnapshotWithoutDeleting(reason)) {
            return ReconnectAbortRestoreOutcome.StatsPersistenceFailed;
        }

        if (verifiedArtifactForRewrite != null) {
            StatsArtifactSnapshot cleared = copyOpenStatsArtifactWithoutReconnectMetadata(
                verifiedArtifactForRewrite,
                nextStatsGenerationAfter(verifiedArtifactForRewrite.generation())
            );
            if (!persistActiveStatsArtifact(cleared, "reconnect-abort-clear-metadata:" + safeReconnectReason(reason))) {
                return ReconnectAbortRestoreOutcome.StatsPersistenceFailed;
            }
        }

        if (payload != null) {
            reconnectBaselineLease = new ReconnectBaselineLease(
                expectedCycleId,
                ReconnectBaselineLeaseState.CONSUMED,
                payload
            );
        }

        if (!deleteThmSpeedSnapshot("reconnect-abort:" + safeReconnectReason(reason))) {
            return ReconnectAbortRestoreOutcome.StatsPersistenceFailed;
        }

        thmSpeedSnapshot = null;
        thmSpeedRestorePending = false;
        thmSpeedSnapshotDeletePending = false;
        thmSpeedReconnectSuppressed = false;
        thmSpeedOwnershipActive = false;
        writeReconnectDebug("abort restore complete cycle=%d reason=%s", expectedCycleId, safeReconnectReason(reason));
        return ReconnectAbortRestoreOutcome.Restored;
    }

    private String safeReconnectReason(String reason) {
        return reason == null || reason.isBlank() ? "unknown" : reason;
    }

    private boolean hasParkedThmSpeedSnapshot() {
        return thmSpeedSnapshot != null || Files.exists(resolveThmSpeedSnapshotPath());
    }

    private boolean restoreParkedThmSpeedSnapshotWithoutDeleting(String reason) {
        MeteorSpeedSnapshot snapshot = thmSpeedSnapshot;
        if (snapshot == null) snapshot = loadThmSpeedSnapshot();
        if (snapshot == null) return true;

        Speed speed = Modules.get().get(Speed.class);
        if (speed == null) return false;

        resetThmSpeedRuntime();
        thmSpeedReconnectSuppressed = false;
        thmSpeedOwnershipActive = false;

        try {
            speed.speedMode.set(parseCenterSpeedModeOrDefault(snapshot.speedModeName()));
            speed.vanillaSpeed.set(snapshot.vanillaSpeed());
            speed.ncpSpeed.set(snapshot.ncpSpeed());
            speed.ncpSpeedLimit.set(snapshot.ncpSpeedLimit());
            speed.timer.set(snapshot.timer());
            speed.inLiquids.set(snapshot.inLiquids());
            speed.whenSneaking.set(snapshot.whenSneaking());
            speed.vanillaOnGround.set(snapshot.vanillaOnGround());

            if (speed.isActive() != snapshot.wasActive()) speed.toggle();
            if (!isThmSpeedStateRestored(speed, snapshot)) return false;

            thmSpeedSnapshot = snapshot;
            thmSpeedRestorePending = false;
            thmSpeedSnapshotDeletePending = false;
            restockDebug("THM speed ownership restored for reconnect abort (reason=%s, active=%s, mode=%s, vanilla=%.2f).",
                safeReconnectReason(reason),
                snapshot.wasActive(),
                snapshot.speedModeName(),
                snapshot.vanillaSpeed()
            );
            return true;
        } catch (Exception e) {
            thmSpeedSnapshot = snapshot;
            restockDebug("THM speed reconnect abort restore failed (reason=%s, error=%s).",
                safeReconnectReason(reason),
                e.getClass().getSimpleName()
            );
            return false;
        }
    }

    public void refreshReconnectBaselineValidity(long activeGeneration) {
        if (reconnectBaselineLease == null || reconnectBaselineRestoreInProgress) return;
        if (reconnectBaselineLease.state() != ReconnectBaselineLeaseState.CAPTURED) return;

        if (activeGeneration <= 0L || reconnectBaselineLease.generation() != activeGeneration) {
            invalidateReconnectBaseline("generation-changed");
            return;
        }

        // A reconnect baseline is a restore point, so live Speed/Timer changes during an armed
        // reconnect should not invalidate the captured payload.
    }

    public void disableForReconnectSafetyStop() {
        disableForMonitorRealignPause();
    }

    private void disableForReconnectResumeFailure() {
        disableForMonitorRealignPause();
    }

    @Override
    public void error(String message, Object... args) {
        super.error(message, args);
        try {
            THMHwyMonitor.signalNonRestartHardFailFromHighwayBuilder();
        } catch (NoClassDefFoundError | ExceptionInInitializerError ignored) {
            // Baritone-dependent monitor not available.
        }
        toggle();

        if (disconnectOnToggle.get()) {
            disconnect(message, args);
        }
    }

    private void errorEarly(String message, Object... args) {
        super.error(message, args);
        try {
            THMHwyMonitor.signalNonRestartHardFailFromHighwayBuilder();
        } catch (NoClassDefFoundError | ExceptionInInitializerError ignored) {
            // Baritone-dependent monitor not available.
        }

        displayInfo = false;
        toggle();
    }

    /**
     * True (armed right before calling {@link #toggle()}) when the handoff to Highway Traveler is
     * what's deactivating this module — a real, full {@link #onDeactivate()} (module shows/behaves
     * as genuinely off: {@code mc.player.input} handed back to {@link #prevInput}, KitBot enclosure
     * cleaned up, everything a normal manual toggle-off does), just with the stats session
     * persisted as {@code StatsSessionState.OPEN} (resumable) instead of finalized/printed, so
     * {@link #onActivate()}'s existing cache-resume path (already used for reconnects) picks the
     * same running total back up next time Traveler hands control back, and the total only
     * actually gets finalized/printed once the user truly disables this module.
     * <p>
     * An earlier version kept this module {@link #isActive()} == true and just froze {@link
     * #onTick} instead of deactivating, to dodge exactly this finalize-on-every-cycle problem —
     * but {@code mc.player.input} stays the custom {@link #input} override for this module's
     * entire active lifetime regardless of whether {@code onTick} runs, so Highway Traveler's
     * vanilla {@code mc.options.*Key.setPressed(...)} calls were silently ignored the whole time,
     * and this module kept steering with its last frozen movement state. A real {@link #toggle()}
     * doesn't have that problem since {@link #onDeactivate()} already releases {@code
     * mc.player.input} unconditionally.
     */
    private boolean travelHandoffDeactivateArmed;

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;
        safetyPlacedThisTick = false;

        // ponytail: self-heal against whatever race leaves this owned outside MineEnderChests, instead of chasing it.
        if (eChestBreakSpeedSnapshotOwned && state != State.MineEnderChests) {
            restoreEChestBreakSpeedIfOwned("safety-net-not-mining");
        }

        maybeCheckpointStatsSession();

        if (kitbotUpdateOnFinishActive) {
            long elapsed = mc.world.getTime() - kitbotUpdateOnFinishStartTick;
            if (elapsed >= 1200) {
                if (kitbotUpdateOnFinishTpAccepted) {
                    info("KitBot1 TPA accepted but 60s elapsed without arrival. Disconnecting.");
                } else {
                    info("No response from KitBot1 after 60 seconds. Disconnecting.");
                }
                kitbotUpdateOnFinishActive = false;
                disconnect("KitBot update timeout");
            }
            tickDebug("idle: waiting for kitbot update-on-finish (tpAccepted=%s)", kitbotUpdateOnFinishTpAccepted);
            return;
        }

        if (!isNot6B6T()) {
            ServerState committedState = getCommittedServerState();
            trackServerExecutionState(committedState);
            if (committedState != ServerState.MAIN_SERVER && !mainServerResumeGateActive) {
                armMainServerResumeGate(committedState);
            }

            boolean executionAllowed = isExecutionAllowedOnCurrentServer(committedState);
            if (!executionAllowed && committedState == ServerState.MAIN_SERVER && mainServerResumeGateActive) {
                executionAllowed = tryCompleteMainServerResumeGate(committedState);
            }

            if (!executionAllowed) {
                logServerStatePause(committedState);
                pauseExecutionForServerState(committedState);
                tickDebug("idle: server state %s not allowed (resumeGate=%s)", committedState, mainServerResumeGateActive);
                return;
            }

            executionPausedByServerState = false;
        } else {
            executionPausedByServerState = false;
        }
        resumeThmSpeedAfterReconnectIfReady("tick-execution-allowed");
        tickDeferredThmSpeedRestore();
        if (useThmSpeed.get() || thmSpeedOwnershipActive || thmSpeedSnapshot != null) syncThmSpeedOwnership("tick");
        tickDeferredCenterSpeedRestore();
        updateTpsActionThrottle();
        if (!shouldPauseForTpsThrottle() && (tpsSafetyEnclosureActive || tpsSafetyCenterOwned || tpsSafetyEnclosureTriggeredThisEpisode)) {
            boolean wasSafetyCenter = tpsSafetyCenterOwned && state == State.Center;
            clearTpsSafetyEnclosureRuntime("tps-recovered", true);
            if (wasSafetyCenter) setState(State.Forward);
        }
        tryFallSaveAirPlace();
        if (shouldPauseForTpsThrottle()) {
            boolean preserveCenterInput = tickTpsSafetyEnclosureDuringPause();
            handleTpsThrottlePauseTick(!preserveCenterInput);
            tickDebug("idle: tps throttle paused (%s, sampledTps=%s)", tpsThrottlePauseReason, formatTpsThrottleSample(tpsThrottleSampledTps));
            return;
        }

        if (statuslog.get()) {
            statusLogTimer++;
            if (statusLogTimer >= 6000) { // 5 minutes
                sendStatusLog();
                statusLogTimer = 0;
            }
        }

        tickAdvertisement();
        tickKitbotPeriodicUpdate();

        if (dir == null) {
            tickDebug("idle: dir == null, re-running onActivate");
            onActivate();
            return;
        }

        if (suspended) {
            if (inventory && Utils.canUpdate()) {
                updateVariables();
                suspended = false;
                resumeThmSpeedAfterReconnectIfReady("tick-unsuspended");
            }
            else {
                tickDebug("idle: suspended (inventoryPacketSeen=%s canUpdate=%s)", inventory, Utils.canUpdate());
                return;
            }
        }

        if (width.get() < 3 && dir.diagonal) {
            errorEarly("Diagonal highways less than 3 blocks wide are not supported, please change the width setting.");
            return;
        }

        EatingPauseOwner activeEatingPauseOwner = resolveActiveEatingPauseOwner();
        boolean killAuraPauseActive = isKillAuraPauseActive();
        boolean shouldPauseForEatingWatchdog = shouldPauseForEatingWatchdog(activeEatingPauseOwner);
        if (shouldPauseForEatingWatchdog || killAuraPauseActive) {
            if (shouldPauseForEatingWatchdog
                && eatingRetryPhase == EatingRetryPhase.NONE
                && mc.player.currentScreenHandler != null
                && !mc.player.currentScreenHandler.getCursorStack().isEmpty()) {
                dropCursorStackIfSafe("AutoEat-pause");
            }

            if (shouldPauseForEatingWatchdog) {
                touchForwardSchedulerPauseHeartbeat();
                tickEatingPauseWatchdog(activeEatingPauseOwner);
            } else {
                resetEatingPauseWatchdog();
            }

            input.stop();
            tickDebug("idle: paused for eating=%s killAura=%s", shouldPauseForEatingWatchdog, killAuraPauseActive);
            return;
        }

        resetEatingPauseWatchdog();

        count = 0;
        forwardMineCount = 0;
        forwardPlaceCount = 0;
        mineActionsThisTick = computeMineActionsThisTick();
        placeActionsThisTick = computePlaceActionsThisTick();
        pruneExpiredForwardBreakCredits();
        refreshCountedForwardStatPositions();

        if (mc.player.getY() < start.y - 0.5) setState(State.ReLevel); // don't let the current state keep ticking, switch to re-levelling straight away
        if (tickDesyncWiggleProbe()) {
            tickDebug("idle: desync wiggle probe running");
            if (breakTimer > 0) breakTimer--;
            if (placeTimer > 0) placeTimer--;
            return;
        }
        tickPacketBorer();
        tickDoubleMine();
        accrueEnclosurePlaceCredit();
        enforceKitbotFoodRestockFoodType();
        maybeQueuePickaxeRestock();
        maybeQueueEnderChestReserveRestock();
        maybeQueueFoodRestock();
        tickNoSwapLoadout();
        tickPacketBuildMainHandObby();
        boolean restockWatchdogOwnedTick = restockWatchdog.tickBeforeState();
        if (!restockWatchdogOwnedTick) {
            if (autoHandoffToTraveler.get() && state == State.Forward
                    && isHighwayAheadClean(dir, handoffCleanScanBlocks.get())) {
                HighwayTraveler traveler = Modules.get().get(HighwayTraveler.class);
                if (traveler != null) {
                    travelHandoffDeactivateArmed = true;
                    toggle();
                    if (!traveler.isActive()) traveler.toggle();
                    return;
                }
            }
            tickDebug("state=%s restock=%s mineActions=%d placeActions=%d creative=%s",
                stateName(state),
                restockTask.activeSummary(),
                mineActionsThisTick,
                placeActionsThisTick,
                mc.player.isCreative()
            );
            state.tick(this);
            restockWatchdog.tickAfterState();
        } else {
            tickDebug("idle: restock watchdog owns the tick (restock=%s)", restockTask.activeSummary());
        }
        maybeLogMovement();

        if (breakTimer > 0) breakTimer--;
        if (placeTimer > 0) placeTimer--;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    private void onPlayerMove(PlayerMoveEvent event) {
        if (event.type != MovementType.SELF || !isThmSpeedControllerActive()) return;

        float forwardAmount = input.forwardAmount();
        float sidewaysAmount = input.sidewaysAmount();
        double y = event.movement.y;
        if (forwardAmount == 0.0f && sidewaysAmount == 0.0f) {
            ((IVec3d) event.movement).meteor$set(0.0, y, 0.0);
            return;
        }

        double yaw = Math.toRadians(mc.player.getYaw());
        double sin = Math.sin(yaw);
        double cos = Math.cos(yaw);
        double forwardSpeed = currentThmForwardSpeedPerTick();
        double lateralSpeed = THM_LATERAL_SPEED / 20.0;

        double x = (-sin * forwardAmount * forwardSpeed) + (cos * sidewaysAmount * lateralSpeed);
        double z = (cos * forwardAmount * forwardSpeed) + (sin * sidewaysAmount * lateralSpeed);
        ((IVec3d) event.movement).meteor$set(x, y, z);
    }

    private double currentThmForwardSpeedPerTick() {
        double speed = highwaySpeed.get();
        if (state == State.Center && centerMode.get() == CenterMode.Walk) speed = Math.min(speed, CENTER_SPEED_OVERRIDE);
        if (state == State.Center && centerMode.get() == CenterMode.Teleport && centerTeleportSlowdownActive) {
            speed *= THM_CENTER_TELEPORT_SLOWDOWN_MULTIPLIER;
        }
        if (kitbotCenteringActive
            && state == State.KitbotOrder
            && kitbotCenteringDistance <= KITBOT_ORDER_CENTER_SLOWDOWN_DISTANCE
            && kitbotCenteringDistance > KITBOT_ORDER_CENTER_RADIUS) {
            speed = Math.min(speed, KITBOT_ORDER_CENTER_SPEED_CAP);
        }
        return speed / 20.0;
    }

    private void tickAdvertisement() {
        if (!advertise.get() || THMAdvertisements.length == 0 || mc.world == null) {
            nextAdvertisementAtTick = 0L;
            return;
        }

        long now = mc.world.getTime();
        if (nextAdvertisementAtTick <= 0L) {
            nextAdvertisementAtTick = now + advertiseIntervalMinutes.get() * 20L * 60L;
            return;
        }

        if (now < nextAdvertisementAtTick) return;

        String message = THMAdvertisements[nextAdvertisementIndex % THMAdvertisements.length];
        if (message != null && !message.isBlank()) ChatUtils.sendPlayerMsg(message);

        nextAdvertisementIndex = (nextAdvertisementIndex + 1) % THMAdvertisements.length;
        nextAdvertisementAtTick = now + advertiseIntervalMinutes.get() * 20L * 60L;
    }

    @EventHandler
    private void onPacket(PacketEvent.Receive event) {
        if (desyncWiggleProbe != null) {
            if (event.packet instanceof PlayerPositionLookS2CPacket) {
                noteDesyncWiggleCorrectionPacket("PlayerPositionLookS2CPacket", mc.player == null ? Integer.MIN_VALUE : mc.player.getId());
            } else if (event.packet instanceof EntityPositionSyncS2CPacket packet && mc.player != null && packet.id() == mc.player.getId()) {
                noteDesyncWiggleCorrectionPacket("EntityPositionSyncS2CPacket", packet.id());
            }
        }

        if (event.packet instanceof InventoryS2CPacket p) {
            if (p.syncId() == 0 && suspended)
                inventory = true;
            else
                this.syncId = p.syncId();
        }
    }

    @EventHandler
    private void onPacketSend(PacketEvent.Send event) {
        if (event.packet instanceof PlayerInteractBlockC2SPacket packet) {
            recordEnclosureInteractPacket(packet);
        }
    }


    @EventHandler
    private void onMessageReceive(ReceiveMessageEvent event) {
        if (!isExecutionAllowedOnCurrentServer(getCommittedServerState())) return;

        String msg = event.getMessage().getString();

        // Weird ahh fix to it never accepting
        if (msg.contains(KITBOT_NAME + " wants to teleport to you")) {
            ChatUtils.sendPlayerMsg("/tpy " + KITBOT_NAME);
            kitbotUpdateOnFinishTpAccepted = true;
            info("Accepted " + KITBOT_NAME + " teleport request.");
        }

        boolean youMayTeleport = msg.contains(KITBOT_NAME + " whispers: Bot has arrived at highway")
            || msg.contains("you may teleport");
        if (kitbotUpdateOnFinishActive) {
            if (youMayTeleport) {
                info("KitBot1 has arrived. Disconnecting.");
                kitbotUpdateOnFinishActive = false;
                disconnect("KitBot update complete");
            }
            return;
        }

        if (kitbotPeriodicUpdateActive) {
            if (msg.contains(KITBOT_NAME + " wants to teleport to you")) {
                ChatUtils.sendPlayerMsg("/tpy " + KITBOT_NAME);
                kitbotPeriodicUpdateTpAccepted = true;
                info("Accepted " + KITBOT_NAME + " teleport request (periodic update).");
            }
            if (youMayTeleport) {
                info("KitBot1 has arrived (periodic update). Continuing build.");
                kitbotPeriodicUpdateActive = false;
            }
        }
    }

    private void onKitbotRestockLifecycle(KitbotFrontend.LifecycleEvent event) {
        if (!shouldProcessKitbotRestockLifecycle(event)) return;

        switch (event.state()) {
            case Succeeded -> handleKitbotRestockFrontendSuccess(event);
            case Failed -> handleKitbotRestockFrontendFailure(event);
            default -> {
            }
        }
    }

    private boolean shouldProcessKitbotRestockLifecycle(KitbotFrontend.LifecycleEvent event) {
        return event != null
            && state == State.KitbotOrder
            && kitbotOrderInFlight
            && event.origin() == KitbotFrontend.RequestOrigin.API
            && event.mode() == KitbotFrontend.Mode.Kit;
    }

    private void handleKitbotRestockFrontendSuccess(KitbotFrontend.LifecycleEvent event) {
        kitbotOrderInFlight = false;
        kitbotOrderFrontendSucceeded = true;
        kitbotInventoryProofDeadlineMs = System.currentTimeMillis() + KITBOT_INVENTORY_PROOF_GRACE_MS;
        kitbotNextSubmitAtMs = 0L;
        restockWatchdog.markProgress("kitbot-frontend-success");

        if (restockDebugLog.get()) {
            restockDebug(
                "KitbotOrder frontend success; waiting up to %dms for matching shulker proof (detail=%s).",
                KITBOT_INVENTORY_PROOF_GRACE_MS,
                event.detail()
            );
        }
    }

    private void handleKitbotRestockFrontendFailure(KitbotFrontend.LifecycleEvent event) {
        kitbotOrderInFlight = false;
        kitbotOrderFrontendSucceeded = false;
        kitbotInventoryProofDeadlineMs = 0L;
        restockWatchdog.markProgress("kitbot-frontend-failure");

        KitbotFrontend.FailureReason reason = event.failureReason();
        String detail = event.detail();
        if (isRetryableKitbotRestockFailure(reason) && kitbotOrderAcceptedAttempts < KITBOT_MAX_ACCEPTED_ATTEMPTS) {
            scheduleKitbotRestockSubmitAfterWindow(
                event.remainingWindowMs(),
                "frontend failure " + describeKitbotFrontendFailure(reason, detail)
            );
            warning(
                "Kitbot restock attempt %d/%d failed (%s). Retrying after KitBot window clears.",
                kitbotOrderAcceptedAttempts,
                KITBOT_MAX_ACCEPTED_ATTEMPTS,
                describeKitbotFrontendFailure(reason, detail)
            );
            return;
        }

        String message = "KitBot restock failed";
        if (kitbotOrderAcceptedAttempts >= KITBOT_MAX_ACCEPTED_ATTEMPTS && isRetryableKitbotRestockFailure(reason)) {
            message += " after " + KITBOT_MAX_ACCEPTED_ATTEMPTS + " attempts";
        }
        message += ": " + describeKitbotFrontendFailure(reason, detail);
        failActiveKitbotRestock(message);
    }

    private boolean isRetryableKitbotRestockFailure(KitbotFrontend.FailureReason reason) {
        if (reason == null) return false;
        return switch (reason) {
            case TimedOut, Disconnected, TransportFailure, GenericError, UnableToFulfill -> true;
            case InsufficientTokens, VoucherCredits, OutOfStock, InvalidKit, NotWhitelisted, MaxOrderViolation, PermissionDenied -> false;
        };
    }

    private String describeKitbotFrontendFailure(KitbotFrontend.FailureReason reason, String detail) {
        if (reason == null) return detail == null || detail.isBlank() ? "unknown error" : detail;
        String hint = switch (reason) {
            case InsufficientTokens -> "not enough KitBot tokens";
            case VoucherCredits -> "not enough voucher credits";
            case OutOfStock -> "item is out of stock";
            case InvalidKit -> "kit is invalid, check your KitBot kit settings";
            case NotWhitelisted -> "you're not whitelisted for this kit";
            case MaxOrderViolation -> "hit KitBot's max order limit";
            case PermissionDenied -> "no permission for this kit";
            case TimedOut -> "KitBot timed out";
            case Disconnected -> "disconnected from KitBot";
            case TransportFailure -> "KitBot connection issue";
            case GenericError, UnableToFulfill -> "KitBot couldn't fulfill the order";
        };
        return detail == null || detail.isBlank() ? hint : hint + " (" + detail + ")";
    }

    private void scheduleKitbotRestockSubmitAfterWindow(long remainingWindowMs, String reason) {
        invalidateKitbotOrderCenterVerification("schedule-submit:" + reason);
        long remaining = Math.max(Math.max(remainingWindowMs, KitbotFrontend.getRemainingWindowMs()), 0L);
        kitbotNextSubmitAtMs = System.currentTimeMillis() + remaining + KITBOT_FRONTEND_WINDOW_BUFFER_MS;
        if (restockDebugLog.get()) {
            restockDebug(
                "KitbotOrder scheduled next submit in %dms after %s (frontendRemaining=%dms).",
                remaining + KITBOT_FRONTEND_WINDOW_BUFFER_MS,
                reason,
                remaining
            );
        }
    }

    private boolean canUseKitbotForActiveRestock() {
        if (!kitbotRestock.get()) return false;
        if (restockTask.pickaxes) return kitbotPickaxeRestock.get();
        if (restockTask.food) return kitbotFoodRestock.get();
        if (restockTask.enderChests) return kitbotEChestRestock.get();
        return restockTask.materials && restockTask.isObsidianRestockSession() && kitbotEChestRestock.get();
    }

    private KitbotRestockGate getKitbotRestockGate() {
        KitbotAvailabilityTracker.Snapshot snapshot = KitbotFrontend.getKitbotAvailabilitySnapshot();
        if (snapshot != null && snapshot.status() == KitbotAvailabilityTracker.AvailabilityStatus.Unavailable) {
            return KitbotRestockGate.Unavailable;
        }

        return KitbotFrontend.isKitbotAvailableForRestock()
            ? KitbotRestockGate.Available
            : KitbotRestockGate.AwaitingRestart;
    }

    private boolean enterKitbotRestockWithAvailabilityGate(String reason, boolean allowEnderChestFallback) {
        if (!canUseKitbotForActiveRestock()) return false;

        restockTask.markKitbotTried();
        restockTask.notePhase(RestockTask.SourcePhase.Kitbot);

        KitbotRestockGate gate = getKitbotRestockGate();
        if (gate == KitbotRestockGate.Unavailable) {
            if (restockDebugLog.get()) {
                restockDebug("%s KitBot availability gate=%s; failing KitBot restock before entry.", reason, gate);
            }
            failActiveKitbotRestock("KitBot is unavailable right now — wait for it to come back or disable KitBot restock.", allowEnderChestFallback);
            return true;
        }

        if (restockDebugLog.get()) {
            restockDebug("%s KitBot availability gate=%s.", reason, gate);
        }
        setState(State.KitbotOrder);
        return true;
    }

    private KitbotFrontend.KitName getKitbotKitNameForActiveRestock() {
        if (restockTask.pickaxes) return kitbotPickaxeRestockKit.get().kitName;
        if (restockTask.food) return KitbotFrontend.KitName.Gapples;
        return kitbotEChestRestockKit.get().kitName;
    }

    private int getKitbotOrderAmountForActiveRestock() {
        if (restockTask.pickaxes) return kitbotPickaxeRestockAmount.get();
        if (restockTask.food) return kitbotFoodRestockAmount.get();
        return kitbotEChestRestockAmount.get();
    }

    private boolean isKitbotProofShulker(ItemStack itemStack) {
        if (!Utils.isShulker(itemStack.getItem())) return false;

        for (ItemStack stack : THMUtils.getContainerContents(itemStack)) {
            if (restockTask.food && isConfiguredFoodStack(stack)) return true;
            if (restockTask.pickaxes && isActivePickaxeRestockMatch(stack)) return true;
            if (restockTask.enderChests && stack.getItem() == Items.ENDER_CHEST) return true;
            if (restockTask.materials && restockTask.isObsidianRestockSession()) {
                if (stack.getItem() == Items.OBSIDIAN || stack.getItem() == Items.ENDER_CHEST) return true;
            }
        }

        return false;
    }

    private int countKitbotProofUnitsForActiveRestock() {
        if (mc.player == null) return 0;

        int count = 0;
        for (int i = 0; i < mc.player.getInventory().getMainStacks().size(); i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack == null || stack.isEmpty()) continue;

            if (isKitbotProofShulker(stack)) count++;
        }

        return count;
    }

    private void failActiveKitbotRestock(String message) {
        failActiveKitbotRestock(message, true);
    }

    private void failActiveKitbotRestock(String message, boolean allowEnderChestFallback) {
        if (allowEnderChestFallback && tryFallbackFromFailedKitbotToEnderChestBank(message)) return;

        returnPlayerToKitbotAnchorIfSaved();
        invalidateKitbotBlockadeState("kitbot-order-failed");
        clearKitbotRuntimeState("kitbot-order-failed");
        if (restockTask.failActiveTaskHard(message)) return;
        error(message);
    }

    private boolean tryFallbackFromFailedKitbotToEnderChestBank(String message) {
        if (!restockTask.isSequenceActive() || restockTask.tasksInactive()) return false;
        if (restockTask.isCurrentTaskSuccessful()) {
            returnPlayerToKitbotAnchorIfSaved();
            invalidateKitbotBlockadeState("kitbot-order-failed-after-success");
            clearKitbotRuntimeState("kitbot-order-failed-after-success");
            completeRestockTaskAndContinue();
            return true;
        }
        if (restockSecondarySourceOrder.get() != RestockSecondarySourceOrder.KitBotThenEnderChest) return false;
        if (!searchEnderChest.get()) return false;
        if (restockTask.hasTriedEnderChestBank()) return false;

        returnPlayerToKitbotAnchorIfSaved();
        invalidateKitbotBlockadeState("kitbot-order-failed-fallback-ender-chest");
        clearKitbotRuntimeState("kitbot-order-failed-fallback-ender-chest");
        restockTask.markKitbotTried();
        restockTask.reopenSourcePhase(RestockTask.SourcePhase.EnderChest);
        restockTask.notePhase(RestockTask.SourcePhase.Inventory);
        restockDebug("Kitbot restock failed (%s); falling back to ender chest bank for active task=%s.", message, restockTask.item());
        setState(State.Restock, State.KitbotOrder);
        return true;
    }

    @EventHandler
    private void onGameLeave(GameLeftEvent event) {
        if (!isActive()) return;
        notifyDesktop(notifyDisconnect, "THM Highway Builder", "Disconnected while Highway Builder was active.");
        if (hasActiveInMemoryStatsSession() && !statsSessionTerminalOrFinalizing) {
            persistCurrentStatsSession(StatsSessionState.OPEN, true, 0L, "game-leave");
        }
        armMainServerResumeGate(ServerState.UNKNOWN);
        lastCommittedServerState = ServerState.UNKNOWN;
        releaseThmSpeedOwnership("game-leave", true);
        clearDesyncWiggleProbe("game-leave");
        releaseActiveDoubleMineRuntime(false);
        clearPendingForwardBreakCredits();
        suspended = true;
        inventory = false;
    }

    @EventHandler
    private void onRender2d(Render2DEvent event) {
        if (suspended || !renderMine.get()) return;

        if (normalMining != null) normalMining.renderLetter();
        if (packetMining != null) packetMining.renderLetter();
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (suspended || blockPosProvider == null || mc.player == null || mc.world == null) return;

        if (renderMine.get()) {
            render(event, blockPosProvider.getFront(), mBlockPos -> canMine(mBlockPos, true), true);
            if (floor.get() == Floor.Replace) render(event, blockPosProvider.getFloor(), mBlockPos -> canMine(mBlockPos, false), true);
            if (railings.get()) render(event, blockPosProvider.getRailings(0), mBlockPos -> canMine(mBlockPos, false), true);
            if (mineAboveRailings.get()) render(event, blockPosProvider.getRailings(1), mBlockPos -> canMine(mBlockPos, true), true);
            if (checkBehind.get()) {
                if (floor.get() == Floor.Replace) render(event, blockPosProvider.getBehindFloor(), mBlockPos -> canMine(mBlockPos, false), true);
                if (railings.get()) render(event, blockPosProvider.getBehindRailings(0), mBlockPos -> canMine(mBlockPos, false), true);
                if (mineAboveRailings.get()) render(event, blockPosProvider.getBehindRailings(1), mBlockPos -> canMine(mBlockPos, true), true);
                render(event, blockPosProvider.getBehindFront(), mBlockPos -> canMine(mBlockPos, true), true);
            }
            if (state == State.MineShulkerBlockade || state == State.MineEChestBlockade) {
                render(event, blockPosProvider.getBlockade(true, getEffectiveBlockadeType()), mBlockPos -> canMine(mBlockPos, true), true);
            }
        }

        if (renderPlace.get()) {
            render(event, blockPosProvider.getLiquids(), mBlockPos -> canPlace(mBlockPos, true), false);

            if (railings.get()) {
                render(event, blockPosProvider.getRailings(0), mBlockPos -> canPlace(mBlockPos, false), false);

                if (cornerBlock.get()) {
                    // make sure we only render corner support blocks if we are actually planning to place a block there
                    render(event, blockPosProvider.getRailings(-1), mBlockPos -> {
                        boolean valid = false;
                        for (MBlockPos pos : blockPosProvider.getRailings(0)) {
                            if (!blocksToPlace.get().contains(pos.getState().getBlock()) && pos.add(0, -1, 0).equals(mBlockPos)) {
                                valid = true;
                                break;
                            }
                        }

                        return valid && canPlace(mBlockPos, false);
                    }, false);
                }
            }

            render(event, blockPosProvider.getFloor(), mBlockPos -> canPlace(mBlockPos, false), false);
            if (checkBehind.get()) {
                if (railings.get()) {
                    render(event, blockPosProvider.getBehindRailings(0), mBlockPos -> canPlace(mBlockPos, false), false);

                    if (cornerBlock.get()) {
                        render(event, blockPosProvider.getBehindRailings(-1), mBlockPos -> {
                            boolean valid = false;
                            for (MBlockPos pos : blockPosProvider.getBehindRailings(0)) {
                                if (!blocksToPlace.get().contains(pos.getState().getBlock()) && pos.add(0, -1, 0).equals(mBlockPos)) {
                                    valid = true;
                                    break;
                                }
                            }

                            return valid && canPlace(mBlockPos, false);
                        }, false);
                    }
                }

                render(event, blockPosProvider.getBehindFloor(), mBlockPos -> canPlace(mBlockPos, false), false);
            }
            if (state == State.PlaceShulkerBlockade || state == State.PlaceEChestBlockade) {
                render(event, blockPosProvider.getBlockade(false, getEffectiveBlockadeType()), mBlockPos -> canPlace(mBlockPos, false), false);
            }
            RenderUtilsTHM.renderAndPruneBlockSet(event, placeTrail, renderPlaceSideColor.get(), renderPlaceLineColor.get(), renderPlaceShape.get());
        }
    }

    private void render(Render3DEvent event, MBPIterator it, Predicate<MBlockPos> predicate, boolean mine) {
        Color sideColor = mine ? renderMineSideColor.get() : renderPlaceSideColor.get();
        Color lineColor = mine ? renderMineLineColor.get() : renderPlaceLineColor.get();
        ShapeMode shapeMode = mine ? renderMineShape.get() : renderPlaceShape.get();

        // Collect all qualifying positions for O(1) neighbour lookup (avoids O(n²) nested iteration)
        renderPosSet.clear();
        for (MBlockPos pos : it) {
            if (predicate.test(pos)) renderPosSet.add(BlockPos.asLong(pos.x, pos.y, pos.z));
        }

        RenderUtilsTHM.renderBlockSet(event, renderPosSet, sideColor, lineColor, shapeMode);
    }

    private void updateVariables() {
        prevInput = mc.player.input;
        mc.player.input = input = new CustomPlayerInput();

        placeTimer = breakTimer = count = syncId = 0;
        forwardMineCount = forwardPlaceCount = 0;
        resetEnclosurePlaceCredit();
        clearEnclosurePlacementConfirmation();
        restockWatchdog.reset("update-variables");
        countedBrokenForwardPositions.clear();
        countedPlacedForwardPositions.clear();
        clearPendingForwardBreakCredits();
        ignoreCrystals.clear();
        resetForwardSchedulerRuntime();

        releaseActiveDoubleMineRuntime(false);
    }

    private void updateSignBreakRegex() {
        if (ignoreSigns.get() || !breakAdvertisementSigns.get()) {
            signBreakPatterns = Collections.emptyList();
            return;
        }

        List<Pattern> compiled = new ArrayList<>();
        for (String regex : ADVERTISEMENT_SIGN_REGEXES) {
            if (regex == null || regex.isEmpty()) continue;
            try {
                compiled.add(Pattern.compile(regex, Pattern.CASE_INSENSITIVE));
            } catch (Exception e) {
                warning("Invalid sign-break-regex: " + e.getMessage());
            }
        }

        signBreakPatterns = compiled;
    }

    private void applyAutosetupModules() {
        if (!autosetupModules.get()) return;

        applyAutosetupSpeedMine();
        applyAutosetupReach();
        applyAutosetupVelocity();
        placeRange.set(5.4);
    }

    @SuppressWarnings("unchecked")
    private void applyAutosetupSpeedMine() {
        SpeedMine speedMine = Modules.get().get(SpeedMine.class);
        if (speedMine == null) return;

        speedMineSettingsSnapshot = null;
        paketLimiterActivatedByBuilder = false;

        if (!speedMine.isActive()) speedMine.toggle();

        speedMine.mode.set(SpeedMine.Mode.Damage);

        Setting<SpeedMine.ListMode> blocksFilter = speedMine.settings.get("blocks-filter", SpeedMine.ListMode.class);
        if (blocksFilter != null) blocksFilter.set(SpeedMine.ListMode.Blacklist);

        Setting<List<Block>> blocksSetting = (Setting<List<Block>>) speedMine.settings.get("blocks");
        if (blocksSetting != null) blocksSetting.set(buildAutosetupSpeedMineBlacklist());

        Setting<Boolean> instamineSetting = (Setting<Boolean>) speedMine.settings.get("instamine");
        if (instamineSetting != null) instamineSetting.set(true);

        Setting<Boolean> grimBypassSetting = (Setting<Boolean>) speedMine.settings.get("grim-bypass");
        if (grimBypassSetting != null) grimBypassSetting.set(false);
    }

    private List<Block> buildAutosetupSpeedMineBlacklist() {
        return new ArrayList<>(List.of(
            Blocks.NETHERRACK,
            Blocks.SHULKER_BOX,
            Blocks.WHITE_SHULKER_BOX,
            Blocks.ORANGE_SHULKER_BOX,
            Blocks.MAGENTA_SHULKER_BOX,
            Blocks.LIGHT_BLUE_SHULKER_BOX,
            Blocks.YELLOW_SHULKER_BOX,
            Blocks.LIME_SHULKER_BOX,
            Blocks.PINK_SHULKER_BOX,
            Blocks.GRAY_SHULKER_BOX,
            Blocks.LIGHT_GRAY_SHULKER_BOX,
            Blocks.CYAN_SHULKER_BOX,
            Blocks.PURPLE_SHULKER_BOX,
            Blocks.BLUE_SHULKER_BOX,
            Blocks.BROWN_SHULKER_BOX,
            Blocks.GREEN_SHULKER_BOX,
            Blocks.RED_SHULKER_BOX,
            Blocks.BLACK_SHULKER_BOX
        ));
    }

    private void applyAutosetupReach() {
        Reach reach = Modules.get().get(Reach.class);
        if (reach == null) return;

        if (!reach.isActive()) reach.toggle();

        Setting<Double> blockReach = reach.settings.get("extra-block-reach", Double.class);
        if (blockReach != null) blockReach.set(2.0);

        Setting<Double> entityReach = reach.settings.get("extra-entity-reach", Double.class);
        if (entityReach != null) entityReach.set(2.0);
    }

    private void applyAutosetupVelocity() {
        Velocity velocity = Modules.get().get(Velocity.class);
        if (velocity == null) return;

        if (!velocity.isActive()) velocity.toggle();

        velocity.knockback.set(true);
        velocity.knockbackHorizontal.set(0.0);
        velocity.knockbackVertical.set(0.0);

        velocity.explosions.set(true);
        velocity.explosionsHorizontal.set(0.0);
        velocity.explosionsVertical.set(0.0);

        velocity.liquids.set(true);
        velocity.liquidsHorizontal.set(0.0);
        velocity.liquidsVertical.set(0.0);

        velocity.entityPush.set(true);
        velocity.entityPushAmount.set(0.0);

        velocity.blocks.set(true);
        velocity.sinking.set(false);
        velocity.fishing.set(false);
    }

    @SuppressWarnings("unchecked")
    private SpeedMineSettingsSnapshot captureSpeedMineSettings(SpeedMine speedMine) {
        Setting<SpeedMine.ListMode> blocksFilter = speedMine.settings.get("blocks-filter", SpeedMine.ListMode.class);
        Setting<List<Block>> blocksSetting = (Setting<List<Block>>) speedMine.settings.get("blocks");
        Setting<Boolean> instamineSetting = (Setting<Boolean>) speedMine.settings.get("instamine");
        Setting<Boolean> grimBypassSetting = (Setting<Boolean>) speedMine.settings.get("grim-bypass");

        List<Block> blocks = blocksSetting != null ? new ArrayList<>(blocksSetting.get()) : new ArrayList<>();
        boolean instamine = instamineSetting != null && instamineSetting.get();
        boolean grimBypass = grimBypassSetting != null && grimBypassSetting.get();
        SpeedMine.ListMode filter = blocksFilter != null ? blocksFilter.get() : SpeedMine.ListMode.Blacklist;

        return new SpeedMineSettingsSnapshot(
            speedMine.isActive(),
            speedMine.mode.get(),
            filter,
            blocks,
            instamine,
            grimBypass
        );
    }

    @SuppressWarnings("unchecked")
    private void applySpeedMineHighwayBuilderOverrides(SpeedMine speedMine) {
        if (speedMineSettingsSnapshot == null) speedMineSettingsSnapshot = captureSpeedMineSettings(speedMine);
        if (!speedMine.isActive()) speedMine.toggle();

        speedMine.mode.set(SpeedMine.Mode.Damage);

        Setting<SpeedMine.ListMode> blocksFilter = speedMine.settings.get("blocks-filter", SpeedMine.ListMode.class);
        if (blocksFilter != null) blocksFilter.set(SpeedMine.ListMode.Blacklist);

        Setting<List<Block>> blocksSetting = (Setting<List<Block>>) speedMine.settings.get("blocks");
        if (blocksSetting != null) {
            List<Block> blocks = new ArrayList<>(1);
            blocks.add(Blocks.NETHERRACK);
            blocksSetting.set(blocks);
        }

        Setting<Boolean> instamineSetting = (Setting<Boolean>) speedMine.settings.get("instamine");
        if (instamineSetting != null) instamineSetting.set(true);

        Setting<Boolean> grimBypassSetting = (Setting<Boolean>) speedMine.settings.get("grim-bypass");
        if (grimBypassSetting != null) grimBypassSetting.set(false);

        PaketLimiter paketLimiter = Modules.get().get(PaketLimiter.class);
        if (paketLimiter != null && !paketLimiter.isActive()) {
            paketLimiter.toggle();
            paketLimiterActivatedByBuilder = true;
        }
    }

    @SuppressWarnings("unchecked")
    private void restoreSpeedMineSettingsIfNeeded() {
        if (speedMineSettingsSnapshot == null) return;

        SpeedMine speedMine = Modules.get().get(SpeedMine.class);
        if (speedMine == null) {
            speedMineSettingsSnapshot = null;
            return;
        }

        SpeedMineSettingsSnapshot snapshot = speedMineSettingsSnapshot;
        speedMine.mode.set(snapshot.mode());

        Setting<SpeedMine.ListMode> blocksFilter = speedMine.settings.get("blocks-filter", SpeedMine.ListMode.class);
        if (blocksFilter != null) blocksFilter.set(snapshot.blocksFilter());

        Setting<List<Block>> blocksSetting = (Setting<List<Block>>) speedMine.settings.get("blocks");
        if (blocksSetting != null) blocksSetting.set(new ArrayList<>(snapshot.blocks()));

        Setting<Boolean> instamineSetting = (Setting<Boolean>) speedMine.settings.get("instamine");
        if (instamineSetting != null) instamineSetting.set(snapshot.instamine());

        Setting<Boolean> grimBypassSetting = (Setting<Boolean>) speedMine.settings.get("grim-bypass");
        if (grimBypassSetting != null) grimBypassSetting.set(snapshot.grimBypass());

        if (snapshot.wasActive() != speedMine.isActive()) speedMine.toggle();

        speedMineSettingsSnapshot = null;

        if (paketLimiterActivatedByBuilder) {
            PaketLimiter paketLimiter = Modules.get().get(PaketLimiter.class);
            if (paketLimiter != null && paketLimiter.isActive()) paketLimiter.toggle();
            paketLimiterActivatedByBuilder = false;
        }
    }

    private boolean shouldOwnManagedSpeedMine() {
        return MANAGED_SPEEDMINE_SETTING_ENABLED && isActive() && doubleMine.get() && speedmine.get();
    }

    private void syncManagedSpeedMineOwnership() {
        if (isActive() && autosetupModules.get()) {
            applyAutosetupModules();
            return;
        }

        SpeedMine speedMineModule = Modules.get().get(SpeedMine.class);
        if (speedMineModule == null) return;

        if (shouldOwnManagedSpeedMine()) applySpeedMineHighwayBuilderOverrides(speedMineModule);
        else restoreSpeedMineSettingsIfNeeded();
    }

    private boolean shouldSkipSignBreak(BlockPos pos, BlockState state) {
        if (!isSignBlock(state)) return false;
        if (ignoreSigns.get()) return true;
        if (!breakAdvertisementSigns.get()) return false;

        List<Pattern> patterns = signBreakPatterns;
        if (patterns == null || patterns.isEmpty()) return true;

        String text = getSignText(pos);
        if (text.isEmpty()) return true;

        for (Pattern pattern : patterns) {
            if (pattern.matcher(text).find()) return false;
        }

        return true;
    }
    //Weird ahh fix
    private boolean isSignBlock(BlockState state) {
        Block block = state.getBlock();
        return block instanceof SignBlock
            || block instanceof WallSignBlock
            || block instanceof HangingSignBlock
            || block instanceof WallHangingSignBlock;
    }

    private String getSignText(BlockPos pos) {
        if (mc.world == null) return "";
        BlockEntity blockEntity = mc.world.getBlockEntity(pos);
        if (blockEntity == null) return "";

        SignText frontText = null;
        SignText backText = null;
        if (blockEntity instanceof SignBlockEntity sign) {
            frontText = sign.getFrontText();
            backText = sign.getBackText();
        } else if (blockEntity instanceof HangingSignBlockEntity sign) {
            frontText = sign.getFrontText();
            backText = sign.getBackText();
        }

        String text = extractSignText(frontText);
        if (!text.isEmpty()) return text;

        return extractSignText(backText);
    }

    private String extractSignText(SignText signText) {
        if (signText == null) return "";

        Text[] messages = signText.getMessages(false);
        if (messages == null) return "";

        StringBuilder sb = new StringBuilder();
        for (Text message : messages) {
            if (message == null) continue;
            String line = cleanSignText(message.getString());
            if (line.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(line);
        }

        return sb.toString();
    }

    private String cleanSignText(String text) {
        if (text == null || text.isEmpty()) return "";
        text = text.replaceAll("Â§.", "");
        text = text.replaceAll("&[0-9a-fklmnor]", "");
        text = text.replaceAll("[\\p{C}&&[^\\s]]", "");
        text = text.replaceAll("[\\u0000-\\u001F\\u007F-\\u009F]", "");
        text = text.replaceAll("\\s+", " ").trim();
        return text;
    }

    private void togglePauseOnLostFocus(boolean b) {
        mc.options.pauseOnLostFocus = b;
        info("Pause on Lost Focus %s.", b ? "enabled" : "disabled");
    }

    private void closeHandledScreen() {
        if (mc.player != null && mc.currentScreen != null) mc.player.closeHandledScreen();
    }

    private void setState(State state) {
        setState(state, this.state);
    }

    private void setState(State state, State lastState) {
        State previousState = this.state;

        if (previousState == State.Center && state != State.Center) {
            centerTeleportSlowdownActive = false;
            restoreCenterSpeedIfOwned("center-exit:" + stateName(state));
            clearCenterTeleportTarget();
        }
        if (state == State.Center && previousState != State.Center) {
            activeCenterTargetBlock = null;
            centerTeleportInvalidLogCooldownTicks = 0;
        }
        if (previousState == State.MineEnderChests && state != State.MineEnderChests) {
            restoreEChestBreakSpeedIfOwned("echest-exit:" + stateName(state));
        }
        if (previousState == State.ThrowOutTrash && state != State.ThrowOutTrash) {
            restoreHotbarManagerAfterTrash("throw-out-trash-exit:" + stateName(state));
        }
        if (previousState == State.KitbotOrder && state != State.KitbotOrder) {
            clearKitbotCenteringRuntime("kitbot-order-exit:" + stateName(state));
            resetKitbotOrderCenterVerificationRuntime("kitbot-order-exit:" + stateName(state));
        }
        if (previousState == State.Forward && state != State.Forward) {
            clearForwardLatchedPlaceSlot();
            clearForwardLatchedMineSlot();
            placeTrail.clear();
            clearForwardDriftRuntime();
        }
        if (state == State.Forward && previousState != State.Forward) {
            resetForwardDriftTarget();
        }

        if (state == State.Forward && restockTask.isSequenceActive()) {
            String activeSummary = restockTask.activeSummary();
            String pendingSummary = restockTask.pendingSummary();
            boolean blockadeReady = restockTask.isBlockadeReady();
            boolean sequenceActive = restockTask.isSequenceActive();

            if (restockDebugLog.get()) {
                restockDebug("Clearing stale restock sequence on Forward entry from %s (last=%s, active=%s, pending=%s, blockadeReady=%s, sequence=%s).",
                    stateName(previousState),
                    stateName(lastState),
                    activeSummary,
                    pendingSummary,
                    blockadeReady,
                    sequenceActive
                );
            }

            invalidRestockRecoveryPending = false;
            restockTask.finishSequence();
            clearKitbotRuntimeState("forward-state-entry");
            restockWatchdog.reset("forward-state-entry");
        }

        this.lastState = lastState;
        this.state = state;
        if (previousState != state && (restockTask.isSequenceActive() || isRestockState(previousState) || isRestockState(state))) {
            restockWatchdog.markProgress("state:" + stateName(previousState) + "->" + stateName(state));
        }

        if (state == State.Forward && isRestockState(previousState) && kitbotPeriodicUpdatePending && kitbotPeriodicUpdate.get()) {
            kitbotPeriodicUpdateNextTick = mc.world.getTime() + 600; // 30s after returning to Forward
        }

        if (shouldLogRestockStateTransition(previousState, state, lastState)) {
            restockDebug("state %s -> %s (last=%s, active=%s, pending=%s, blockadeReady=%s, sequence=%s)",
                stateName(previousState),
                stateName(state),
                stateName(lastState),
                restockTask.activeSummary(),
                restockTask.pendingSummary(),
                restockTask.isBlockadeReady(),
                restockTask.isSequenceActive()
            );
        }

        input.stop();
        state.start(this);
    }

    private void completeRestockTaskAndContinue() {
        if (restockDebugLog.get()) {
            restockDebug("completeRestockTaskAndContinue(active=%s, pending=%s, blockadeReady=%s, sequence=%s)",
                restockTask.activeSummary(),
                restockTask.pendingSummary(),
                restockTask.isBlockadeReady(),
                restockTask.isSequenceActive()
            );
        }

        restockTask.completeActive();

        if (restockTask.advanceToPendingTask()) {
            setState(State.Restock, State.Restock);
            return;
        }

        if (restockTask.shouldTearDownRestockBlockade()) {
            if (restockDebugLog.get()) {
                restockDebug("RestockTask queue drained; deferring blockade teardown before continuing restock-owned teardown wait.");
            }
            restockTask.deferBlockadeTeardown();
            clearKitbotRuntimeState("restock-sequence-finished-with-teardown");
            restockWatchdog.reset("restock-sequence-finished-with-teardown");
            setState(State.WaitForRestockBlockadeTeardown, State.Restock);
            return;
        }

        if (restockDebugLog.get()) {
            restockDebug("RestockTask finished without pending tasks or teardown requirement; returning to Forward.");
        }
        restockTask.finishSequence();
        clearKitbotRuntimeState("restock-sequence-finished");
        restockWatchdog.reset("restock-sequence-finished");
        setState(State.Forward);
    }

    private void finishEmptyRestockTeardownWait(String reason) {
        if (input != null) input.stop();

        if (restockDebugLog.get()) {
            restockDebug("WaitForRestockBlockadeTeardown finishing sequence because no active/pending task remains (%s).",
                reason
            );
        }

        restockTask.finishSequence();
        clearKitbotRuntimeState(reason);
        restockWatchdog.reset(reason);
        setState(State.Forward);
    }

    private void clearKitbotRuntimeState(String reason) {
        clearKitbotCenteringRuntime(reason);
        resetKitbotOrderCenterVerificationRuntime(reason);
        clearKitbotOrderTracking(reason);
        clearKitbotEnclosureState(reason);
        kitbotOrderResumeAfterCenter = false;
        clearEnclosurePlacementConfirmation();
        resetEnclosurePlaceCredit();
        kitbotUpdateOnFinishActive = false;
        kitbotUpdateOnFinishStartTick = 0;
        kitbotUpdateOnFinishTpAccepted = false;
    }

    private void clearKitbotCenteringRuntime(String reason) {
        boolean hadRuntime = kitbotCenteringActive
            || kitbotCenteringTicks != 0
            || Double.isFinite(kitbotCenteringDistance)
            || Double.isFinite(kitbotCenteringTargetX)
            || Double.isFinite(kitbotCenteringTargetZ)
            || !kitbotCenteringReason.isEmpty();

        kitbotCenteringActive = false;
        kitbotCenteringTicks = 0;
        kitbotCenteringDistance = Double.POSITIVE_INFINITY;
        kitbotCenteringTargetX = Double.NaN;
        kitbotCenteringTargetZ = Double.NaN;
        kitbotCenteringReason = "";
        if (input != null) input.stop();

        if (hadRuntime && restockDebugLog.get()) {
            restockDebug("KitbotOrder cleared center-walk runtime (%s).", reason);
        }
    }

    private void resetKitbotOrderCenterVerificationRuntime(String reason) {
        clearKitbotOrderCenterVerificationRuntime(reason, true);
    }

    private void invalidateKitbotOrderCenterVerification(String reason) {
        clearKitbotOrderCenterVerificationRuntime(reason, false);
    }

    private void clearKitbotOrderCenterVerificationRuntime(String reason, boolean resetFailures) {
        boolean hadRuntime = kitbotOrderCenterVerificationActive
            || kitbotOrderCenterVerified
            || kitbotOrderCenterVerificationTicks != 0
            || kitbotOrderCenterVerificationRecenterTicks != 0
            || kitbotOrderCenterVerificationSettleTicks != 0
            || kitbotOrderCenterVerificationRecentered
            || Double.isFinite(kitbotOrderCenterVerificationTargetX)
            || Double.isFinite(kitbotOrderCenterVerificationTargetZ)
            || Double.isFinite(kitbotOrderCenterVerificationDistance)
            || (resetFailures && kitbotOrderCenterVerificationFailures != 0);
        boolean wasActive = kitbotOrderCenterVerificationActive;

        kitbotOrderCenterVerificationActive = false;
        kitbotOrderCenterVerified = false;
        kitbotOrderCenterVerificationTicks = 0;
        kitbotOrderCenterVerificationRecenterTicks = 0;
        kitbotOrderCenterVerificationSettleTicks = 0;
        kitbotOrderCenterVerificationRecentered = false;
        kitbotOrderCenterVerificationTargetX = Double.NaN;
        kitbotOrderCenterVerificationTargetZ = Double.NaN;
        kitbotOrderCenterVerificationDistance = Double.POSITIVE_INFINITY;
        if (resetFailures) kitbotOrderCenterVerificationFailures = 0;
        if (wasActive && input != null) input.stop();

        if (hadRuntime && restockDebugLog.get()) {
            restockDebug(
                "KitbotOrder cleared center verification runtime (%s, resetFailures=%s).",
                reason,
                resetFailures
            );
        }
    }

    private boolean matchesKitbotOrderCenterVerificationTarget(double targetX, double targetZ) {
        return Math.abs(kitbotOrderCenterVerificationTargetX - targetX) <= 0.0001
            && Math.abs(kitbotOrderCenterVerificationTargetZ - targetZ) <= 0.0001;
    }

    private void clearKitbotOrderTracking(String reason) {
        boolean hadOrderState = kitbotOrderInFlight
            || kitbotOrderFrontendSucceeded
            || kitbotOrderBaselineShulkerCount != 0
            || kitbotOrderExpectedShulkerGain != 0
            || kitbotOrderSentAtAge != 0
            || kitbotOrderAcceptedAttempts != 0
            || kitbotOrderLastObservedMatchingShulkerCount != 0
            || kitbotPartialDeliveryGraceUntilAge != 0
            || kitbotNextSubmitAtMs != 0L
            || kitbotInventoryProofDeadlineMs != 0L;
        if (!hadOrderState) return;

        kitbotOrderInFlight = false;
        kitbotOrderFrontendSucceeded = false;
        kitbotOrderBaselineShulkerCount = 0;
        kitbotOrderExpectedShulkerGain = 0;
        kitbotOrderSentAtAge = 0;
        kitbotOrderAcceptedAttempts = 0;
        kitbotOrderLastObservedMatchingShulkerCount = 0;
        kitbotPartialDeliveryGraceUntilAge = 0;
        kitbotNextSubmitAtMs = 0L;
        kitbotInventoryProofDeadlineMs = 0L;
        if (restockDebugLog.get()) restockDebug("KitbotOrder cleared in-flight order tracking (%s).", reason);
    }

    private boolean isKitbotNearby() {
        if (mc.player == null || mc.world == null) return false;

        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == null || player == mc.player) continue;
            if (!KITBOT_NAME.equals(player.getName().getString())) continue;
            if (player.squaredDistanceTo(mc.player) <= KITBOT_NEARBY_DISTANCE * KITBOT_NEARBY_DISTANCE) return true;
        }

        return false;
    }

    private void clearKitbotEnclosureState(String reason) {
        boolean hadEnclosureState = kitbotEnclosureActive
            || kitbotEnclosureRestorePending
            || kitbotReturnAnchorSaved
            || kitbotAnchorPos.getX() != 0
            || kitbotAnchorPos.getY() != 0
            || kitbotAnchorPos.getZ() != 0;

        kitbotEnclosureActive = false;
        kitbotEnclosureRestorePending = false;
        kitbotReturnAnchorSaved = false;
        kitbotSeenNearby = false;
        kitbotDepartureWaitUntilAge = 0;
        kitbotCollectUntilAge = 0;
        kitbotReturnX = kitbotReturnY = kitbotReturnZ = 0;
        kitbotReturnYaw = 0;
        kitbotAnchorPos.set(0, 0, 0);

        if (hadEnclosureState && restockDebugLog.get()) {
            restockDebug("Kitbot enclosure state cleared (%s).", reason);
        }
    }

    private void cacheKitbotReturnAnchorFromPlayer() {
        if (mc.player == null) return;

        kitbotReturnX = mc.player.getX();
        kitbotReturnY = mc.player.getY();
        kitbotReturnZ = mc.player.getZ();
        kitbotReturnYaw = mc.player.getYaw();
        kitbotAnchorPos.set(mc.player.getBlockPos());
        kitbotReturnAnchorSaved = true;
    }

    private void returnPlayerToKitbotAnchorIfSaved() {
        if (!kitbotReturnAnchorSaved || mc.player == null) return;
        if (input != null) input.stop();
        mc.player.setPosition(kitbotReturnX, kitbotReturnY, kitbotReturnZ);
        mc.player.setYaw(kitbotReturnYaw);
    }

    private void invalidateKitbotBlockadeState(String reason) {
        restockTask.setBlockadeReady(false);
        if (restockDebugLog.get()) restockDebug("KitbotOrder invalidated blockade state (%s).", reason);
    }

    private void cleanupKitbotEnclosureOnDeactivate(String reason) {
        if (kitbotEnclosureActive || kitbotEnclosureRestorePending) {
            returnPlayerToKitbotAnchorIfSaved();
            invalidateKitbotBlockadeState(reason);
        }

        clearKitbotRuntimeState(reason);
    }

    private BlockadeType getEffectiveBlockadeType() {
        // Keep the enum and lease plumbing compatible for future shapes, but lock the live
        // runtime to FullRoof while KitBot and normal blockade geometry share one basis.
        return BlockadeType.FullRoof;
    }

    public boolean shouldSuppressThmHwyMonitorMisalignmentRecovery() {
        return shouldSuppressThmHwyMonitorRecovery(false);
    }

    public boolean shouldSuppressThmHwyMonitorRecovery(boolean centerStallRecovery) {
        if (isTpsThrottlePaused()) return true;
        if (centerStallRecovery) return false;

        return restockTask.isSequenceActive()
            || kitbotEnclosureActive
            || kitbotEnclosureRestorePending;
    }

    public boolean isLegacyCenterSpeedOwnershipAllowed() {
        return isActive()
            && centerMode.get() != CenterMode.Teleport
            && !useThmSpeed.get();
    }

    private void onCenterModeChanged(CenterMode value) {
        if (!isActive()) return;
        if (value == CenterMode.Teleport && centerSpeedSnapshotOwned) {
            restoreCenterSpeedIfOwned("center-mode-teleport");
        }
        if (useThmSpeed.get()) syncThmSpeedOwnership("center-mode-changed");
    }

    private void onUseThmSpeedChanged(Boolean value) {
        if (!isActive()) return;
        if (Boolean.TRUE.equals(value)) {
            if (centerSpeedSnapshotOwned) restoreCenterSpeedIfOwned("thm-speed-setting-enabled");
            syncThmSpeedOwnership("setting-enabled");
        } else {
            releaseThmSpeedOwnership("setting-disabled", true);
        }
    }

    private HorizontalDirection getRestockContainerDirection(HorizontalDirection workingDirection) {
        return workingDirection.diagonal
            ? workingDirection.rotateLeft().rotateLeftSkipOne()
            : workingDirection.opposite();
    }

    private HorizontalDirection getKitbotExpansionDirection(HorizontalDirection containerDirection) {
        return containerDirection.rotateLeftSkipOne().opposite();
    }

    // Double-chest: on servers whose plugin merges two adjacent ender chests into a 54-slot double, the
    // blockade column next to the source chest (basis column 1, == container + expansionDirection) is
    // built as an ender chest instead of an obsidian pillar. Opening the front chest then yields the
    // double, at zero extra cost - it's placed during the normal encasing build and reclaimed by the
    // normal teardown mine, so nothing is placed-then-broken.
    private boolean isDoubleChestBlockadeActive() {
        return doublechest.get() && searchEnderChest.get();
    }

    // Only the ender-chest *search* restock benefits from a double, so the extra chest is built only for
    // the container-search blockade (PlaceShulkerBlockade -> Restock), never the mine-echests-for-obsidian
    // one (PlaceEChestBlockade -> MineEnderChests) - so breaking ender chests only ever breaks the one
    // being broken, never an extra to the side - and never for a shulker/trash restock that won't open the
    // ender chest. The remaining gate is "is the ender chest actually a live source right now": we need two
    // spare ender chests (one for this wall column, one for the front chest the Restock state places) and
    // the session's ender-chest source can't already be exhausted.
    private boolean shouldBuildDoubleChestForRestock(State placementState) {
        if (!isDoubleChestBlockadeActive() || placementState != State.PlaceShulkerBlockade) return false;
        if (countLooseInventoryEnderChests() < 2) return false;
        RestockTask.RestockSession session = restockTask.getSession();
        if (session != null && session.isEnderChestExhausted()) return false;
        // The restock searches inventory shulkers first and only opens the ender chest when none matches,
        // so a matching inventory shulker means this is a shulker pull, not an ender-chest search - no
        // double. Mirrors Restock's own shulkerContainsRestockItems check, at the outer level.
        return !hasMatchingInventoryShulkerForRestock();
    }

    private boolean hasMatchingInventoryShulkerForRestock() {
        if (mc.player == null) return false;
        for (int i = 0; i < mc.player.getInventory().getMainStacks().size(); i++) {
            ItemStack itemStack = mc.player.getInventory().getStack(i);
            if (!Utils.isShulker(itemStack.getItem())) continue;
            for (ItemStack stack : THMUtils.getContainerContents(itemStack)) {
                if (stack.getItem() instanceof BlockItem bi
                    && (blocksToPlace.get().contains(bi.getBlock())
                        || (blocksToPlace.get().contains(Blocks.OBSIDIAN) && bi == Items.ENDER_CHEST))) return true;
                if (restockTask.pickaxes && isActivePickaxeRestockMatch(stack)) return true;
                if (restockTask.food && isConfiguredFoodStack(stack)) return true;
            }
        }
        return false;
    }

    private BlockPos doubleChestBlockadePos(State placementState) {
        if (!shouldBuildDoubleChestForRestock(placementState) || dir == null || mc.player == null) return null;
        HorizontalDirection containerDir = getRestockContainerDirection(dir);
        BlockPos container = offsetBlockPos(mc.player.getBlockPos(), containerDir);
        return offsetBlockPos(container, getKitbotExpansionDirection(containerDir));
    }

    private BlockPos offsetBlockPos(BlockPos origin, HorizontalDirection direction) {
        return offsetBlockPos(origin, direction, 1);
    }

    private BlockPos offsetBlockPos(BlockPos origin, HorizontalDirection direction, int amount) {
        if (amount == 0) return origin.toImmutable();

        int dx = 0;
        int dz = 0;

        switch (direction) {
            case North -> dz = -1;
            case South -> dz = 1;
            case East -> dx = 1;
            case West -> dx = -1;
            case NorthEast -> {
                dx = 1;
                dz = -1;
            }
            case NorthWest -> {
                dx = -1;
                dz = -1;
            }
            case SouthEast -> {
                dx = 1;
                dz = 1;
            }
            case SouthWest -> {
                dx = -1;
                dz = 1;
            }
        }

        return origin.add(dx * amount, 0, dz * amount);
    }

    private boolean isValidRestockStructureBlock(BlockState state) {
        if (state == null || state.isAir()) return false;

        Block block = state.getBlock();
        Item item = block.asItem();
        return blocksToPlace.get().contains(block)
            || isDroppableTrashItem(item);
    }

    private boolean isKitbotRequiredAirClear(BlockState state) {
        if (state == null || state.isAir()) return true;
        if (!state.getFluidState().isEmpty()) return false;
        return state.isReplaceable();
    }

    private boolean isSatisfiedKitbotStructureBlock(BlockState state, KitbotStructureBlockType type) {
        if (state == null) return false;
        if (type == KitbotStructureBlockType.Column && state.getBlock() == Blocks.OBSIDIAN) return true;
        return isValidRestockStructureBlock(state);
    }

    private BlockadeBasis getBlockadeBasis(BlockPos anchor, HorizontalDirection workingDirection, HorizontalDirection containerDirection, HorizontalDirection expansionDirection) {
        BlockPos immutableAnchor = anchor.toImmutable();
        BlockPos containerPos = offsetBlockPos(immutableAnchor, containerDirection);
        BlockPos[] columnSlots = new BlockPos[] {
            offsetBlockPos(immutableAnchor, containerDirection, 2),
            offsetBlockPos(containerPos, expansionDirection),
            offsetBlockPos(containerPos, expansionDirection.opposite()),
            offsetBlockPos(immutableAnchor, containerDirection.opposite()),
            offsetBlockPos(immutableAnchor, expansionDirection),
            offsetBlockPos(immutableAnchor, expansionDirection.opposite())
        };

        return new BlockadeBasis(
            immutableAnchor,
            containerPos,
            offsetBlockPos(immutableAnchor, expansionDirection),
            offsetBlockPos(containerPos, expansionDirection),
            columnSlots,
            immutableAnchor.up(2),
            containerPos.up(2),
            !workingDirection.diagonal
        );
    }

    private boolean shouldSkipNormalLowerColumn(BlockadeBasis basis, int slot) {
        return basis.straight && width.get() == 1 && railings.get() && slot > 0;
    }

    private void addRequiredAirStack(KitbotFootprint footprint, BlockPos pos) {
        footprint.addRequiredAir(pos);
        footprint.addRequiredAir(pos.up());
    }

    private void addRequiredColumnStack(KitbotFootprint footprint, BlockPos basePos, boolean includeLowerBlock) {
        if (includeLowerBlock) footprint.addRequiredBlock(basePos, KitbotStructureBlockType.Column);
        footprint.addRequiredBlock(basePos.up(), KitbotStructureBlockType.Column);
    }

    private Vec3d getKitbotOrderTargetPosition(double anchorX, double anchorY, double anchorZ, HorizontalDirection containerDirection, HorizontalDirection expansionDirection) {
        Vec3d moveVector = yawToDirection(containerDirection.yaw).multiply(0.5).add(yawToDirection(expansionDirection.yaw).multiply(0.5));
        return new Vec3d(anchorX + moveVector.x, anchorY, anchorZ + moveVector.z);
    }

    private Vec3d getKitbotOrderTargetPosition(BlockPos anchor, double y, HorizontalDirection containerDirection, HorizontalDirection expansionDirection) {
        if (anchor == null) return null;
        return getKitbotOrderTargetPosition(
            getBlockCenterCoordinate(anchor.getX()),
            y,
            getBlockCenterCoordinate(anchor.getZ()),
            containerDirection,
            expansionDirection
        );
    }

    private KitbotFootprint getNormalBlockadeFootprint(BlockPos anchor, BlockadeType blockadeType, HorizontalDirection workingDirection, HorizontalDirection containerDirection, HorizontalDirection expansionDirection) {
        KitbotFootprint footprint = new KitbotFootprint();
        BlockadeBasis basis = getBlockadeBasis(anchor, workingDirection, containerDirection, expansionDirection);

        addRequiredAirStack(footprint, basis.anchor);
        addRequiredAirStack(footprint, basis.container);

        for (int i = 0; i < blockadeType.columns; i++) {
            addRequiredColumnStack(footprint, basis.column(i), !shouldSkipNormalLowerColumn(basis, i));
        }

        if (blockadeType.roof) {
            footprint.addRequiredBlock(basis.roof0, KitbotStructureBlockType.Roof);
            footprint.addRequiredBlock(basis.roof1, KitbotStructureBlockType.Roof);
        }

        return footprint;
    }

    private KitbotFootprint getKitbotEnclosureFootprint(BlockPos anchor, HorizontalDirection workingDirection, HorizontalDirection containerDirection, HorizontalDirection expansionDirection) {
        KitbotFootprint footprint = new KitbotFootprint();
        BlockadeBasis basis = getBlockadeBasis(anchor, workingDirection, containerDirection, expansionDirection);

        addRequiredAirStack(footprint, basis.anchor);
        addRequiredAirStack(footprint, basis.container);
        addRequiredAirStack(footprint, basis.anchorExpand);
        addRequiredAirStack(footprint, basis.containerExpand);

        // Normal FullRoof slots are [0..5]. KitBot keeps the opposite-side columns (2,5),
        // keeps the front/back columns (0,3), removes the expansion-side columns (1,4),
        // and adds four expansion-side perimeter columns to form the 2x2 enclosure.
        addRequiredColumnStack(footprint, basis.column(0), true);
        addRequiredColumnStack(footprint, offsetBlockPos(basis.column(0), expansionDirection), true);
        addRequiredColumnStack(footprint, basis.column(3), true);
        addRequiredColumnStack(footprint, offsetBlockPos(basis.column(3), expansionDirection), true);
        addRequiredColumnStack(footprint, basis.column(5), true);
        addRequiredColumnStack(footprint, basis.column(2), true);
        addRequiredColumnStack(footprint, offsetBlockPos(basis.column(4), expansionDirection), true);
        addRequiredColumnStack(footprint, offsetBlockPos(basis.column(1), expansionDirection), true);

        footprint.addRequiredBlock(basis.roof0, KitbotStructureBlockType.Roof);
        footprint.addRequiredBlock(basis.roof1, KitbotStructureBlockType.Roof);
        footprint.addRequiredBlock(basis.anchorExpand.up(2), KitbotStructureBlockType.Roof);
        footprint.addRequiredBlock(basis.containerExpand.up(2), KitbotStructureBlockType.Roof);

        return footprint;
    }

    private int getWidthLeft() {
        return switch (width.get()) {
            case 6, 7 -> 3;
            case 5, 4 -> 2;
            case 3, 2 -> 1;
            default -> 0;
        };
    }

    private int getWidthRight() {
        return switch (width.get()) {
            case 7 -> 3;
            case 6, 5 -> 2;
            case 4, 3 -> 1;
            default -> 0;
        };
    }

    private boolean canMine(MBlockPos pos, boolean mineBlocksToPlace) {
        BlockState state = pos.getState();
        if (shouldSkipSignBreak(pos.getBlockPos(), state)) return false;
        return isWithinConfiguredForwardRange(pos.getBlockPos()) && safeCanBreak(pos.getBlockPos(), state) && (mineBlocksToPlace || !blocksToPlace.get().contains(state.getBlock()));
    }

    private boolean canPlace(MBlockPos pos, boolean liquids) {
        if (!isWithinConfiguredForwardRange(pos.getBlockPos())) return false;
        return liquids ? !pos.getState().getFluidState().isEmpty() : BlockUtils.canPlace(pos.getBlockPos());
    }

    private void resetTpsThrottleRuntime() {
        tpsThrottleLastSampleAge = Integer.MIN_VALUE;
        tpsThrottleSampledTps = TPS_THROTTLE_NORMAL;
        nextTpsThrottlePauseReasonLogAtMs = 0L;

        if (pauseOnLag.get()) {
            tpsThrottleAdjustedBlocksPerTick = 0.0;
            tpsThrottleAdjustedPlacementsPerTick = 0.0;
            tpsThrottlePaused = true;
            tpsThrottlePauseReason = TpsThrottlePauseReason.Settling;
            tpsThrottleSettleUntilMs = System.currentTimeMillis() + TPS_THROTTLE_SETTLE_MS;
        } else {
            tpsThrottleAdjustedBlocksPerTick = sanitizeMineActionRate(blocksPerTick.get());
            tpsThrottleAdjustedPlacementsPerTick = sanitizePlaceActionRate(placementsPerTick.get());
            tpsThrottlePaused = false;
            tpsThrottlePauseReason = TpsThrottlePauseReason.None;
            tpsThrottleSettleUntilMs = 0L;
        }
    }

    private void updateTpsActionThrottle() {
        double baseBlocksPerTick = sanitizeMineActionRate(blocksPerTick.get());
        double basePlacementsPerTick = sanitizePlaceActionRate(placementsPerTick.get());
        boolean wasPaused = tpsThrottlePaused;
        TpsThrottlePauseReason previousReason = tpsThrottlePauseReason;

        if (!pauseOnLag.get()) {
            tpsThrottleLastSampleAge = Integer.MIN_VALUE;
            tpsThrottleSampledTps = TPS_THROTTLE_NORMAL;
            tpsThrottleAdjustedBlocksPerTick = baseBlocksPerTick;
            tpsThrottleAdjustedPlacementsPerTick = basePlacementsPerTick;
            tpsThrottlePaused = false;
            tpsThrottlePauseReason = TpsThrottlePauseReason.None;
            tpsThrottleSettleUntilMs = 0L;
            nextTpsThrottlePauseReasonLogAtMs = 0L;
            if (wasPaused) info("Server TPS throttle disabled. Resuming HighwayBuilder.");
            return;
        }

        if (mc.player == null) {
            tpsThrottleAdjustedBlocksPerTick = 0.0;
            tpsThrottleAdjustedPlacementsPerTick = 0.0;
            tpsThrottlePaused = true;
            tpsThrottlePauseReason = TpsThrottlePauseReason.InvalidTps;
            return;
        }

        int age = mc.player.age;
        double sampledTps = TickRate.INSTANCE.getTickRate();
        float secondsSinceLastTick = TickRate.INSTANCE.getTimeSinceLastTick();
        long now = System.currentTimeMillis();
        TpsThrottlePauseReason pauseReason = resolveTpsThrottlePauseReason(sampledTps, secondsSinceLastTick, now);
        boolean forceSample = wasPaused || pauseReason != TpsThrottlePauseReason.None || previousReason != TpsThrottlePauseReason.None;

        if (!forceSample
            && tpsThrottleLastSampleAge != Integer.MIN_VALUE
            && age - tpsThrottleLastSampleAge < TPS_THROTTLE_SAMPLE_INTERVAL_TICKS) {
            return;
        }

        tpsThrottleLastSampleAge = age;
        double previousBlocksPerTick = tpsThrottleAdjustedBlocksPerTick;
        double previousPlacementsPerTick = tpsThrottleAdjustedPlacementsPerTick;
        tpsThrottleSampledTps = sampledTps;

        if (pauseReason != TpsThrottlePauseReason.None) {
            tpsThrottleAdjustedBlocksPerTick = 0.0;
            tpsThrottleAdjustedPlacementsPerTick = 0.0;
            tpsThrottlePaused = true;
            tpsThrottlePauseReason = pauseReason;
        } else {
            double clampedTps = Math.min(TPS_THROTTLE_NORMAL, Math.max(TPS_THROTTLE_PAUSE_THRESHOLD, sampledTps));
            double multiplier = clampedTps / TPS_THROTTLE_NORMAL;
            tpsThrottleAdjustedBlocksPerTick = roundToNearestTenth(baseBlocksPerTick * multiplier);
            tpsThrottleAdjustedPlacementsPerTick = roundToNearestTenth(basePlacementsPerTick * multiplier);
            tpsThrottlePaused = false;
            tpsThrottlePauseReason = TpsThrottlePauseReason.None;
        }

        if (tpsThrottlePaused && !wasPaused) {
            warning("Server TPS throttle pausing HighwayBuilder: %s.", describeTpsThrottlePauseReason(tpsThrottlePauseReason, sampledTps, secondsSinceLastTick, now));
        } else if (!tpsThrottlePaused && wasPaused) {
            info("Server TPS recovered to %.1f. Resuming HighwayBuilder.", tpsThrottleSampledTps);
        } else if (debugLog.get() && tpsThrottlePaused && previousReason != tpsThrottlePauseReason) {
            highwayDebug(
                "HB TPS throttle pause reason changed: %s -> %s (%s).",
                previousReason,
                tpsThrottlePauseReason,
                describeTpsThrottlePauseReason(tpsThrottlePauseReason, sampledTps, secondsSinceLastTick, now)
            );
        }

        if (debugLog.get()
            && (wasPaused != tpsThrottlePaused
                || previousReason != tpsThrottlePauseReason
                || Math.abs(previousBlocksPerTick - tpsThrottleAdjustedBlocksPerTick) >= TPS_THROTTLE_DEBUG_DELTA
                || Math.abs(previousPlacementsPerTick - tpsThrottleAdjustedPlacementsPerTick) >= TPS_THROTTLE_DEBUG_DELTA)) {
            double multiplier = tpsThrottlePaused
                ? 0.0
                : Math.min(TPS_THROTTLE_NORMAL, Math.max(TPS_THROTTLE_PAUSE_THRESHOLD, tpsThrottleSampledTps)) / TPS_THROTTLE_NORMAL;
            highwayDebug(
                "HB TPS throttle sampledTps=%s lastTickAge=%.2f multiplier=%.2f adjustedMine=%.2f adjustedPlace=%.2f paused=%s reason=%s",
                formatTpsThrottleSample(tpsThrottleSampledTps),
                secondsSinceLastTick,
                multiplier,
                tpsThrottleAdjustedBlocksPerTick,
                tpsThrottleAdjustedPlacementsPerTick,
                tpsThrottlePaused,
                tpsThrottlePauseReason
            );
        }

        maybeLogTpsThrottlePauseReason(sampledTps, secondsSinceLastTick, now);
    }

    private TpsThrottlePauseReason resolveTpsThrottlePauseReason(double sampledTps, float secondsSinceLastTick, long now) {
        if (now < tpsThrottleSettleUntilMs) return TpsThrottlePauseReason.Settling;
        if (!Float.isFinite(secondsSinceLastTick) || secondsSinceLastTick > TPS_THROTTLE_STALE_TICK_SECONDS) {
            return TpsThrottlePauseReason.StaleTick;
        }
        if (!Double.isFinite(sampledTps) || sampledTps <= 0.0) return TpsThrottlePauseReason.InvalidTps;
        if (sampledTps < TPS_THROTTLE_PAUSE_THRESHOLD) return TpsThrottlePauseReason.LowTps;
        return TpsThrottlePauseReason.None;
    }

    private void maybeLogTpsThrottlePauseReason(double sampledTps, float secondsSinceLastTick, long now) {
        if (!debugLog.get() || !tpsThrottlePaused) return;
        if (now < nextTpsThrottlePauseReasonLogAtMs) return;

        highwayDebug(
            "HB TPS throttle still paused: reason=%s, detail=%s, sampledTps=%s, lastTickAge=%.2f.",
            tpsThrottlePauseReason,
            describeTpsThrottlePauseReason(tpsThrottlePauseReason, sampledTps, secondsSinceLastTick, now),
            formatTpsThrottleSample(sampledTps),
            secondsSinceLastTick
        );
        nextTpsThrottlePauseReasonLogAtMs = now + TPS_THROTTLE_PAUSE_REASON_LOG_INTERVAL_MS;
    }

    private String describeTpsThrottlePauseReason(TpsThrottlePauseReason reason, double sampledTps, float secondsSinceLastTick, long now) {
        return switch (reason) {
            case Settling -> String.format(Locale.ROOT, "settling for %.1fs", Math.max(0L, tpsThrottleSettleUntilMs - now) / 1000.0);
            case StaleTick -> String.format(Locale.ROOT, "last server tick %.2fs ago", secondsSinceLastTick);
            case InvalidTps -> String.format(Locale.ROOT, "TPS unknown (sample=%s)", formatTpsThrottleSample(sampledTps));
            case LowTps -> String.format(Locale.ROOT, "TPS %s is below 10", formatTpsThrottleSample(sampledTps));
            case None -> "none";
        };
    }

    private String formatTpsThrottleSample(double sampledTps) {
        if (!Double.isFinite(sampledTps)) return "invalid";
        return String.format(Locale.ROOT, "%.1f", sampledTps);
    }

    public boolean isTpsThrottlePaused() {
        return isActive() && pauseOnLag.get() && tpsThrottlePaused;
    }

    private boolean shouldPauseForTpsThrottle() {
        return pauseOnLag.get() && tpsThrottlePaused;
    }

    private void handleTpsThrottlePauseTick() {
        handleTpsThrottlePauseTick(true);
    }

    private void handleTpsThrottlePauseTick(boolean stopInput) {
        if (stopInput && input != null) input.stop();
        releaseActiveDoubleMineRuntime(true);
        count = 0;
        forwardMineCount = 0;
        forwardPlaceCount = 0;
        mineActionsThisTick = 0;
        placeActionsThisTick = 0;
        touchForwardSchedulerPauseHeartbeat();
        restockWatchdog.pauseHeartbeat("tps-throttle");
    }

    private boolean isTpsSettlingPause() {
        return pauseOnLag.get() && tpsThrottlePauseReason == TpsThrottlePauseReason.Settling;
    }

    private boolean isConfirmedTpsSafetyPause() {
        return pauseOnLag.get()
            && tpsThrottlePaused
            && (tpsThrottlePauseReason == TpsThrottlePauseReason.StaleTick
                || tpsThrottlePauseReason == TpsThrottlePauseReason.InvalidTps
                || tpsThrottlePauseReason == TpsThrottlePauseReason.LowTps);
    }

    private boolean isSafetyForwardEligible() {
        return state == State.Forward
            && !suspended
            && input != null
            && dir != null
            && blockPosProvider != null
            && !forwardSchedulerRuntime.backstepping;
    }

    private void clearTpsSafetyEnclosureRuntime(String reason, boolean resetEpisode) {
        boolean hadRuntime = tpsSafetyEnclosureActive
            || tpsSafetyCenterOwned
            || tpsSafetyEnclosureAnchor != null
            || !tpsSafetyEnclosureTargets.isEmpty();

        tpsSafetyEnclosureActive = false;
        tpsSafetyCenterOwned = false;
        tpsSafetyEnclosureAnchor = null;
        tpsSafetyEnclosureTargets.clear();
        if (resetEpisode) tpsSafetyEnclosureTriggeredThisEpisode = false;

        if (hadRuntime && debugLog.get()) highwayDebug("HB TPS safety enclosure cleared (%s).", reason);
    }

    private void clearSafetyRuntime(String reason) {
        safetyPlacedThisTick = false;
        clearTpsSafetyEnclosureRuntime(reason, true);
    }

    private boolean tickTpsSafetyEnclosureDuringPause() {
        if (!tpsSafetyEnclosure.get() || !isConfirmedTpsSafetyPause()) return false;

        if (!tpsSafetyEnclosureActive && !tpsSafetyEnclosureTriggeredThisEpisode) {
            if (!isSafetyForwardEligible()) return false;

            tpsSafetyEnclosureActive = true;
            tpsSafetyEnclosureTriggeredThisEpisode = true;
            tpsSafetyEnclosureAnchor = null;
            tpsSafetyEnclosureTargets.clear();
            if (debugLog.get()) highwayDebug("HB TPS safety enclosure armed (reason=%s).", tpsThrottlePauseReason);
        }

        if (!tpsSafetyEnclosureActive) return false;

        if (tpsSafetyCenterOwned && state == State.Center) {
            state.tick(this);
            if (state == State.Center) return true;

            tpsSafetyCenterOwned = false;
            if (state != State.Forward) {
                clearTpsSafetyEnclosureRuntime("safety-center-left-to-" + stateName(state), false);
                return false;
            }
        }

        if (state != State.Forward || suspended || forwardSchedulerRuntime.backstepping) {
            clearTpsSafetyEnclosureRuntime("ineligible-state-" + stateName(state), false);
            return false;
        }

        if (!isPlayerCenteredForSafety()) {
            tpsSafetyCenterOwned = true;
            if (debugLog.get()) highwayDebug("HB TPS safety enclosure entering Center before build.");
            setState(State.Center, State.Forward);
            return false;
        }

        if (tpsSafetyEnclosureAnchor == null) initializeTpsSafetyEnclosureTargets();
        placeNextTpsSafetyEnclosureBlock();
        return false;
    }

    private void initializeTpsSafetyEnclosureTargets() {
        tpsSafetyEnclosureAnchor = mc.player.getBlockPos().toImmutable();
        tpsSafetyEnclosureTargets.clear();

        addTpsSafetyPillar(tpsSafetyEnclosureAnchor.north());
        addTpsSafetyPillar(tpsSafetyEnclosureAnchor.south());
        addTpsSafetyPillar(tpsSafetyEnclosureAnchor.east());
        addTpsSafetyPillar(tpsSafetyEnclosureAnchor.west());

        if (debugLog.get()) {
            highwayDebug("HB TPS safety enclosure anchor=%s targets=%d.", formatBlockPos(tpsSafetyEnclosureAnchor), tpsSafetyEnclosureTargets.size());
        }
    }

    private void addTpsSafetyPillar(BlockPos base) {
        tpsSafetyEnclosureTargets.add(base.toImmutable());
        tpsSafetyEnclosureTargets.add(base.up().toImmutable());
    }

    private void placeNextTpsSafetyEnclosureBlock() {
        if (safetyPlacedThisTick) return;

        while (!tpsSafetyEnclosureTargets.isEmpty()) {
            BlockPos target = tpsSafetyEnclosureTargets.pollFirst();
            if (!isSafetyPlacementCandidate(target)) continue;

            int slot = findSafetyBlockHotbarSlot();
            if (slot == -1) {
                tpsSafetyEnclosureTargets.addFirst(target);
                if (debugLog.get()) highwayDebug("HB TPS safety enclosure waiting: no non-hard-fail block slot.");
                return;
            }
            if (!canSafetyPlaceBlockAt(target, slot)) continue;

            if (trySafetyAirPlaceBlock(target, slot, "tps-enclosure")) {
                if (debugLog.get()) highwayDebug("HB TPS safety enclosure placed %s.", formatBlockPos(target));
                return;
            }
        }

        if (debugLog.get()) highwayDebug("HB TPS safety enclosure completed.");
        tpsSafetyEnclosureActive = false;
        tpsSafetyCenterOwned = false;
        tpsSafetyEnclosureAnchor = null;
    }

    private boolean tryFallSaveAirPlace() {
        if (!manageThmHwyMonitor.get() || !fallSaveAirPlace.get()) return false;
        if (isTpsSettlingPause()) return false;
        if (!isSafetyForwardEligible()) return false;
        if (mc.player == null || mc.world == null) return false;

        double operatingY = blocksToPlace.get().contains(Blocks.NETHERRACK)
            ? FALL_SAVE_DIGGING_OPERATING_Y
            : FALL_SAVE_PAVING_OPERATING_Y;
        double currentY = mc.player.getY();
        double descentDepth = operatingY - currentY;
        boolean positionDescending = currentY < mc.player.lastY;
        boolean velocityDescending = mc.player.getVelocity().y < 0.0;
        if (descentDepth < FALL_SAVE_TRIGGER_DEPTH || !positionDescending || !velocityDescending) return false;

        Box hitbox = mc.player.getBoundingBox();
        BlockPos dominantColumn = dominantHitboxColumn(hitbox, MathHelper.floor(hitbox.minY));
        if (dominantColumn == null) return false;

        int supportY = MathHelper.floor(hitbox.minY - 0.01);
        BlockPos supportPos = new BlockPos(dominantColumn.getX(), supportY, dominantColumn.getZ());
        boolean supportMissing = mc.world.getBlockState(supportPos).isReplaceable();
        if (!supportMissing) return false;

        int feetY = MathHelper.floor(hitbox.minY);
        int minY = feetY - fallSaveDistance.get();
        int maxY = feetY - 1;

        for (int y = minY; y <= maxY; y++) {
            BlockPos target = new BlockPos(dominantColumn.getX(), y, dominantColumn.getZ());
            if (!isSafetyPlacementCandidate(target)) continue;

            int slot = findSafetyBlockHotbarSlot();
            if (slot == -1) {
                if (debugLog.get()) highwayDebug("HB fall-save skipped: no non-hard-fail block slot.");
                return false;
            }
            if (!canSafetyPlaceBlockAt(target, slot)) continue;

            if (trySafetyAirPlaceBlock(target, slot, "fall-save")) {
                if (debugLog.get()) {
                    highwayDebug(
                        "HB fall-save placed %s (operatingY=%.2f, descentDepth=%.3f, previousY=%.3f, currentY=%.3f, velocityY=%.3f).",
                        formatBlockPos(target),
                        operatingY,
                        descentDepth,
                        mc.player.lastY,
                        currentY,
                        mc.player.getVelocity().y
                    );
                }
                return true;
            }
        }

        return false;
    }

    private BlockPos dominantHitboxColumn(Box hitbox, int y) {
        int minX = MathHelper.floor(hitbox.minX);
        int maxX = MathHelper.floor(Math.nextDown(hitbox.maxX));
        int minZ = MathHelper.floor(hitbox.minZ);
        int maxZ = MathHelper.floor(Math.nextDown(hitbox.maxZ));
        double centerX = (hitbox.minX + hitbox.maxX) * 0.5;
        double centerZ = (hitbox.minZ + hitbox.maxZ) * 0.5;

        BlockPos best = null;
        double bestArea = -1.0;
        double bestDistanceSq = Double.POSITIVE_INFINITY;

        for (int x = minX; x <= maxX; x++) {
            double overlapX = Math.max(0.0, Math.min(hitbox.maxX, x + 1.0) - Math.max(hitbox.minX, x));
            for (int z = minZ; z <= maxZ; z++) {
                double overlapZ = Math.max(0.0, Math.min(hitbox.maxZ, z + 1.0) - Math.max(hitbox.minZ, z));
                double area = overlapX * overlapZ;
                double dx = (x + 0.5) - centerX;
                double dz = (z + 0.5) - centerZ;
                double distanceSq = dx * dx + dz * dz;

                if (area > bestArea || (Math.abs(area - bestArea) <= 1.0e-9 && distanceSq < bestDistanceSq)) {
                    bestArea = area;
                    bestDistanceSq = distanceSq;
                    best = new BlockPos(x, y, z);
                }
            }
        }

        return best;
    }

    private boolean isSafetyPlacementCandidate(BlockPos target) {
        if (mc.player == null || mc.world == null || target == null) return false;
        if (!isWithinConfiguredForwardRange(target)) return false;

        BlockState state = mc.world.getBlockState(target);
        return state.isReplaceable();
    }

    private boolean canSafetyPlaceBlockAt(BlockPos target, int slot) {
        if (mc.player == null || mc.world == null || target == null) return false;
        if (slot < 0 || slot > 8) return false;
        ItemStack stack = mc.player.getInventory().getStack(slot);
        if (!(stack.getItem() instanceof BlockItem blockItem)) return false;
        return BlockUtils.canPlaceBlock(target, true, blockItem.getBlock());
    }

    private int findSafetyBlockHotbarSlot() {
        int slot = State.Forward.findAndMoveToHotbar(
            this,
            itemStack -> itemStack.getItem() instanceof BlockItem blockItem && blocksToPlace.get().contains(blockItem.getBlock()),
            false
        );
        return slot != -1 ? slot : giveCreativePlacementBlock();
    }

    /**
     * Creative players don't carry real stacks of the placement block, so the normal
     * inventory-search paths always fail for them. Give a stack of the configured
     * placement block directly into a hotbar slot instead, same as opening the
     * creative inventory and dragging it in by hand.
     */
    private int giveCreativePlacementBlock() {
        if (mc.player == null || !mc.player.isCreative() || blocksToPlace.get().isEmpty()) return -1;

        int slot = State.Forward.findHotbarSlot(this, false, false);
        if (slot == -1) return -1;

        mc.interactionManager.clickCreativeStack(new ItemStack(blocksToPlace.get().get(0), 64), SlotUtils.indexToId(slot));
        return slot;
    }

    private boolean trySafetyAirPlaceBlock(BlockPos target, int slot, String source) {
        if (mc.player == null || mc.world == null) return false;
        if (slot < 0 || slot > 8) return false;

        ItemStack stack = mc.player.getInventory().getStack(slot);
        if (!(stack.getItem() instanceof BlockItem blockItem)) return false;
        if (!blocksToPlace.get().contains(blockItem.getBlock())) return false;
        if (!BlockUtils.canPlaceBlock(target, true, blockItem.getBlock())) return false;

        FindItemResult item = new FindItemResult(slot, stack.getCount());
        boolean placed = PlacementUtils.placeBlockPacket(target, item, false, 0, true, silentForwardPlaceSwap.get());
        if (!placed) return false;

        safetyPlacedThisTick = true;
        placeTimer = placeDelay.get();
        count++;
        forwardPlaceCount++;
        if (renderPlace.get()) placeTrail.add(BlockPos.asLong(target.getX(), target.getY(), target.getZ()));
        if (debugLog.get()) highwayDebug("HB safety place source=%s target=%s slot=%d.", source, formatBlockPos(target), slot);
        return true;
    }

    private boolean isPlayerCenteredForSafety() {
        if (mc.player == null) return false;
        double centerX = getCenteredBlockCoordinate(mc.player.getX());
        double centerZ = getCenteredBlockCoordinate(mc.player.getZ());
        return Math.abs(mc.player.getX() - centerX) <= 0.1
            && Math.abs(mc.player.getZ() - centerZ) <= 0.1;
    }

    private double effectiveBlocksPerTickActionRate() {
        return pauseOnLag.get() ? Math.max(0.0, tpsThrottleAdjustedBlocksPerTick) : sanitizeMineActionRate(blocksPerTick.get());
    }

    private double effectivePlacementsPerTickActionRate() {
        return pauseOnLag.get() ? Math.max(0.0, tpsThrottleAdjustedPlacementsPerTick) : sanitizePlaceActionRate(placementsPerTick.get());
    }

    private double sanitizeMineActionRate(double value) {
        if (!Double.isFinite(value)) return 1.0;
        return Math.max(1.0, value);
    }

    private double sanitizePlaceActionRate(double value) {
        if (!Double.isFinite(value)) return 0.1;
        return Math.max(0.1, roundToNearestTenth(value));
    }

    private double roundToNearestTenth(double value) {
        if (!Double.isFinite(value)) return 0.0;
        return Math.round(value * 10.0) / 10.0;
    }

    private int computeMineActionsThisTick() {
        double configured = Math.max(0.0, effectiveBlocksPerTickActionRate());
        int whole = (int) Math.floor(configured);
        double fractional = configured - whole;

        mineFractionCarry += fractional;
        if (mineFractionCarry >= 1.0) {
            whole += 1;
            mineFractionCarry -= 1.0;
        }

        return Math.max(0, whole);
    }

    private int computePlaceActionsThisTick() {
        if (packetBuild.get()) return Integer.MAX_VALUE;

        double configured = Math.max(0.0, effectivePlacementsPerTickActionRate());
        int whole = (int) Math.floor(configured);
        double fractional = configured - whole;

        placeFractionCarry += fractional;
        if (placeFractionCarry >= 1.0) {
            whole += 1;
            placeFractionCarry -= 1.0;
        }

        return Math.max(0, whole);
    }

    private int computeEnclosurePlaceCreditGainTenths() {
        double configured = Math.max(0.0, effectivePlacementsPerTickActionRate());
        int gain = (int) Math.round(configured * ENCLOSURE_PLACE_CREDIT_SCALE);
        return Math.max(0, Math.min(gain, ENCLOSURE_PLACE_CREDIT_SCALE));
    }

    private void accrueEnclosurePlaceCredit() {
        enclosurePlaceCreditTenths = Math.min(
            enclosurePlaceCreditTenths + computeEnclosurePlaceCreditGainTenths(),
            ENCLOSURE_PLACE_CREDIT_SCALE
        );
    }

    private void resetEnclosurePlaceCredit() {
        enclosurePlaceCreditTenths = 0;
    }

    private boolean consumeEnclosurePlaceCredit() {
        if (enclosurePlaceCreditTenths < ENCLOSURE_PLACE_CREDIT_SCALE) return false;
        enclosurePlaceCreditTenths -= ENCLOSURE_PLACE_CREDIT_SCALE;
        return true;
    }

    private int currentMineActionsThisTick() {
        return Math.max(0, mineActionsThisTick);
    }

    private int currentPlaceActionsThisTick() {
        return Math.max(0, placeActionsThisTick);
    }

    private boolean useForwardRowSchedulerMode() {
        return !legacyMode.get();
    }

    private double roundToNearestHundredth(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private double effectiveInstamineOverrideBlocksPerTick() {
        return Math.max(0.1, roundToNearestHundredth(effectiveBlocksPerTickActionRate()));
    }

    private boolean isWithinConfiguredForwardRange(BlockPos pos) {
        return RangeUtils.isInRange(placeRange.get(), pos);
    }

    private boolean tryMineBlock(BlockPos pos, BlockState state, boolean rotate) {
        return tryMineBlock(pos, state, rotate, null);
    }

    private boolean tryMineBlock(BlockPos pos, BlockState state, boolean rotate, Runnable beforeBreakAction) {
        if (!isWithinConfiguredForwardRange(pos)) return false;
        if (!safeCanBreak(pos, state)) return false;

        Runnable breakAction = () -> {
            if (beforeBreakAction != null) beforeBreakAction.run();
            BlockUtils.breakBlock(pos, true);
        };

        if (rotate) Rotations.rotate(Rotations.getYaw(pos), Rotations.getPitch(pos), breakAction);
        else breakAction.run();

        breakTimer = breakDelay.get();

        if (!lastBreakingPos.equals(pos)) {
            lastBreakingPos.set(pos.getX(), pos.getY(), pos.getZ());
        }

        count++;
        return true;
    }

    private boolean tryPlaceBlock(BlockPos pos, int slot, boolean rotate) {
        if (!isWithinConfiguredForwardRange(pos)) return false;
        // Meteor's place() rejects anything outside the hotbar, so an offhand place keeps the selected slot
        // (making its internal swap a no-op) and only switches the hand.
        boolean offhand = slot == SlotUtils.OFFHAND;
        boolean placed = BlockUtils.place(
            pos,
            offhand ? Hand.OFF_HAND : Hand.MAIN_HAND,
            offhand ? mc.player.getInventory().getSelectedSlot() : slot,
            rotate, 0, true, true, true
        );
        if (!placed) return false;

        placeTimer = placeDelay.get();
        count++;

        if (renderPlace.get()) placeTrail.add(BlockPos.asLong(pos.getX(), pos.getY(), pos.getZ()));

        return true;
    }

    private void clearForwardLatchedPlaceSlot() {
        forwardLatchedPlaceSlot = -1;
    }

    private void clearForwardLatchedMineSlot() {
        forwardLatchedMineSlot = -1;
    }

    private boolean isForwardTrashPlacementStack(ItemStack stack) {
        return stack.getItem() instanceof BlockItem && isDroppableTrashStack(stack);
    }

    private boolean isForwardLatchedPlaceSlotValid(ForwardTask task) {
        if (mc.player == null || task == null) return false;
        if (forwardLatchedPlaceSlot < 0 || forwardLatchedPlaceSlot > 8) return false;

        ItemStack stack = mc.player.getInventory().getStack(forwardLatchedPlaceSlot);
        if (stack.isEmpty()) return false;

        return task.type.prioritizesTrash() ? isForwardTrashPlacementStack(stack) : isForwardPlaceableBlock(stack);
    }

    private int resolveForwardPlaceSlot(ForwardTask task) {
        if (task == null) return -1;
        if (isForwardLatchedPlaceSlotValid(task)) return forwardLatchedPlaceSlot;

        int slot = task.type.prioritizesTrash()
            ? State.Forward.findBlocksToPlacePrioritizeTrash(this)
            : State.Forward.findBlocksToPlace(this);

        forwardLatchedPlaceSlot = slot;
        if (slot == -1) tickDebug("idle: no hotbar slot with a placement block (%s)", blocksToPlace.get());
        return slot;
    }

    private void revalidateForwardLatchedPlaceSlot(ForwardTask task) {
        if (!isForwardLatchedPlaceSlotValid(task)) clearForwardLatchedPlaceSlot();
    }

    private boolean passesForwardMineDurabilityGuard(ItemStack stack) {
        return !dontBreakTools.get() || stack.getMaxDamage() - stack.getDamage() > (stack.getMaxDamage() * (breakDurability.get() / 100.0));
    }

    private boolean shouldRejectForwardLatchedMineStackForPickaxeShortage(ItemStack stack) {
        if (!stack.isIn(ItemTags.PICKAXES)) return false;

        int count = State.Forward.countItem(this, candidate -> candidate.isIn(ItemTags.PICKAXES));
        return count <= savePickaxes.get() && !(restockTask.pickaxes && passesForwardMineDurabilityGuard(stack));
    }

    private boolean isForwardLatchedMineSlotValid(BlockState blockState, boolean noSilkTouch) {
        if (mc.player == null || blockState == null) return false;
        if (forwardLatchedMineSlot < 0 || forwardLatchedMineSlot > 8) return false;

        ItemStack stack = mc.player.getInventory().getStack(forwardLatchedMineSlot);
        if (stack.isEmpty()) return false;
        if (noSilkTouch && Utils.hasEnchantment(stack, Enchantments.SILK_TOUCH)) return false;
        if (!passesForwardMineDurabilityGuard(stack)) return false;
        if (shouldRejectForwardLatchedMineStackForPickaxeShortage(stack)) return false;

        return AutoTool.getScore(
            stack,
            blockState,
            false,
            false,
            AutoTool.EnchantPreference.None,
            itemStack -> {
                if (noSilkTouch && Utils.hasEnchantment(itemStack, Enchantments.SILK_TOUCH)) return false;
                return passesForwardMineDurabilityGuard(itemStack);
            }
        ) > 0;
    }

    private int resolveForwardMineSlot(BlockState blockState, boolean noSilkTouch) {
        if (blockState == null) return -1;
        if (isForwardLatchedMineSlotValid(blockState, noSilkTouch)) return forwardLatchedMineSlot;

        clearForwardLatchedMineSlot();
        int slot = State.Forward.findAndMoveBestToolToHotbar(this, blockState, noSilkTouch);
        forwardLatchedMineSlot = slot;
        if (slot == -1) tickDebug("idle: no usable tool slot for %s", blockState.getBlock());
        return slot;
    }

    private boolean tryForwardPlaceBlock(BlockPos pos, int slot) {
        if (mc.player == null || mc.world == null) return false;
        if (!isWithinConfiguredForwardRange(pos)) return false;

        boolean offhandPlace = slot == SlotUtils.OFFHAND || placeFromOffhand();
        if (!offhandPlace && (slot < 0 || slot > 8)) return false;

        ItemStack stack = offhandPlace ? mc.player.getOffHandStack() : mc.player.getInventory().getStack(slot);
        if (!(stack.getItem() instanceof BlockItem blockItem)) return false;
        if (!BlockUtils.canPlaceBlock(pos, true, blockItem.getBlock())) return false;

        if (packetBuild.get()) {
            return tryForwardPlaceBlockPacket(pos, slot, stack);
        }

        Vec3d hitPos = Vec3d.ofCenter(pos);
        Direction side = BlockUtils.getPlaceSide(pos);
        BlockPos neighbour;

        if (side == null) {
            side = Direction.UP;
            neighbour = pos;
        } else {
            neighbour = pos.offset(side);
            hitPos = hitPos.add(side.getOffsetX() * 0.5, side.getOffsetY() * 0.5, side.getOffsetZ() * 0.5);
        }

        boolean swapped = false;
        if (!offhandPlace) {
            if (silentForwardPlaceSwap.get()) {
                if (mc.player.getInventory().getSelectedSlot() != slot) {
                    if (!InvUtils.swap(slot, true)) return false;
                    swapped = true;
                }
            } else if (mc.player.getInventory().getSelectedSlot() != slot) {
                InvUtils.swap(slot, false);
            }
        }

        try {
            BlockUtils.interact(new BlockHitResult(hitPos, side.getOpposite(), neighbour, false),
                offhandPlace ? Hand.OFF_HAND : Hand.MAIN_HAND, true);
        } finally {
            if (swapped) InvUtils.swapBack();
        }

        placeTimer = placeDelay.get();
        count++;

        if (renderPlace.get()) placeTrail.add(BlockPos.asLong(pos.getX(), pos.getY(), pos.getZ()));

        return true;
    }

    private boolean getRotateForMine() {
        if (rotation.get().mine) return true;
        return false;
    }

    // ---- No-swap loadout: pickaxe in the main hand, obsidian in the offhand, self-managed totem ----

    /** Offhand stack at or below this gets topped up from the inventory... */
    private static final int OFFHAND_BLOCK_TOPUP_AT = 32;
    /** ...and at or below this, with nothing left in the inventory, starts a container restock. */
    private static final int OFFHAND_BLOCK_RESTOCK_AT = 16;

    /** True when the offhand currently holds a block we're allowed to place (obsidian), so we can place straight from it. */
    private boolean offhandHoldsPlaceable() {
        return mc.player != null
            && mc.player.getOffHandStack().getItem() instanceof BlockItem bi
            && blocksToPlace.get().contains(bi.getBlock());
    }

    /**
     * True when placement should come out of the offhand. The block-finding paths only search the hotbar and
     * main inventory, so once offhand-build has parked the only obsidian in the offhand they find nothing and
     * the builder just stops placing — they return {@link SlotUtils#OFFHAND} instead, which every place path
     * below understands.
     */
    private boolean placeFromOffhand() {
        return noSwapLoadout.get() && offhandHoldsPlaceable();
    }

    /** Turn Meteor's AutoTotem off while no-swap-loadout owns the offhand, and restore it when we let go. */
    private void syncNoSwapAutoTotem() {
        AutoTotem autoTotem = Modules.get().get(AutoTotem.class);
        if (autoTotem == null) return;
        boolean want = isActive() && noSwapLoadout.get();
        if (want && autoTotem.isActive()) {
            autoTotem.toggle();
            disabledAutoTotem = true;
        } else if (!want && disabledAutoTotem) {
            if (!autoTotem.isActive()) autoTotem.toggle();
            disabledAutoTotem = false;
        }
    }

    private boolean noSwapShouldHoldTotem() {
        if (mc.player == null) return false;
        float effective = mc.player.getHealth() + mc.player.getAbsorptionAmount()
            - PlayerUtils.possibleHealthReductions(true, true);
        return effective <= noSwapTotemHealth.get() || enemyInRenderDistance();
    }

    private boolean enemyInRenderDistance() {
        if (mc.world == null) return false;
        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player || player.isSpectator()) continue;
            if (Friends.get().isFriend(player)) continue;
            if (THMSystem.get().ignoreThmMembers.get() && ThmMembers.isThmMember(player)) continue;
            return true;
        }
        return false;
    }

    private boolean isPlaceableBlockStack(ItemStack stack) {
        return stack.getItem() instanceof BlockItem bi && blocksToPlace.get().contains(bi.getBlock());
    }

    /**
     * offhand-build: placement reads {@link SlotUtils#OFFHAND} straight out of the hand, so
     * {@code findBlocksToPlace}'s usual "nothing found anywhere → restock" path never fires until the
     * offhand is completely empty — the builder runs dry mid-row and only then goes looking for a
     * shulker. Start the restock while there are still blocks left to build with.
     */
    private void restockOffhandBlocksIfLow() {
        if (mc.player.getOffHandStack().getCount() > OFFHAND_BLOCK_RESTOCK_AT) return;
        if (InvUtils.find(this::isPlaceableBlockStack, 0, 35).found()) return;
        if (searchEnderChest.get() || searchShulkers.get() || mineEnderChests.get() || kitbotRestock.get()) {
            restockTask.setMaterials();
        }
    }

    private void tickNoSwapLoadout() {
        if (!noSwapLoadout.get() || mc.player == null) return;
        syncNoSwapAutoTotem();

        // About to die: a totem takes the offhand over obsidian.
        if (noSwapShouldHoldTotem()) {
            if (mc.player.getOffHandStack().getItem() != Items.TOTEM_OF_UNDYING) {
                FindItemResult totem = InvUtils.find(Items.TOTEM_OF_UNDYING);
                if (totem.found()) InvUtils.move().from(totem.slot()).toOffhand();
            }
        } else {
            ItemStack offhand = mc.player.getOffHandStack();
            if (!offhandHoldsPlaceable()) {
                // Fill the offhand (search hotbar + main inv, i.e. not the offhand slot itself).
                FindItemResult obby = InvUtils.find(this::isPlaceableBlockStack, 0, 35);
                if (obby.found()) InvUtils.move().from(obby.slot()).toOffhand();
            } else if (offhand.getCount() <= OFFHAND_BLOCK_TOPUP_AT && offhand.getCount() < offhand.getMaxCount()) {
                // Top up a partial stack instead of waiting for it to run out. Same item only:
                // InvUtils.move() is a two-click pickup/place, which merges matching stacks but
                // *swaps* different ones — pulling netherrack onto offhand obsidian would park the
                // obsidian back in the inventory.
                FindItemResult more = InvUtils.find(s -> s.getItem() == offhand.getItem(), 0, 35);
                if (more.found()) InvUtils.move().from(more.slot()).toOffhand();
            }

            restockOffhandBlocksIfLow();
        }

        ensurePickaxeInMainHand();
    }

    /** Feature: while packet-build paving, rest with obsidian actually held in the main hand. */
    private void tickPacketBuildMainHandObby() {
        if (!packetBuild.get() || noSwapLoadout.get() || mc.player == null) return;
        ItemStack held = mc.player.getInventory().getStack(mc.player.getInventory().getSelectedSlot());
        if (held.getItem() instanceof BlockItem bi && blocksToPlace.get().contains(bi.getBlock())) return;
        FindItemResult obby = InvUtils.findInHotbar(
            s -> s.getItem() instanceof BlockItem bi && blocksToPlace.get().contains(bi.getBlock()));
        if (obby.found() && obby.isHotbar()) InvUtils.swap(obby.slot(), false);
    }

    private void ensurePickaxeInMainHand() {
        int sel = mc.player.getInventory().getSelectedSlot();
        if (mc.player.getInventory().getStack(sel).isIn(ItemTags.PICKAXES)) return;
        FindItemResult pick = InvUtils.findInHotbar(s -> s.isIn(ItemTags.PICKAXES));
        if (pick.found() && pick.isHotbar()) {
            InvUtils.swap(pick.slot(), false);
        } else {
            int moved = State.Forward.findAndMoveToHotbar(this, s -> s.isIn(ItemTags.PICKAXES), false);
            if (moved != -1) InvUtils.swap(moved, false);
        }
    }

    private boolean tryForwardPlaceBlockPacket(BlockPos pos, int slot, ItemStack stack) {
        if (!isForwardPlaceableBlock(stack) && !isForwardTrashPlacementStack(stack)) return false;
        Direction side = BlockUtils.getPlaceSide(pos);
        // offhand-build: place straight from the offhand obsidian instead of swapping the main hand.
        FindItemResult item = (slot == SlotUtils.OFFHAND || placeFromOffhand())
            ? new FindItemResult(SlotUtils.OFFHAND, mc.player.getOffHandStack().getCount())
            : new FindItemResult(slot, stack.getCount());
        AirPlaceMode mode = packetBuildAirPlace.get();
        boolean swapBack = silentForwardPlaceSwap.get();

        boolean placed;
        switch (mode) {
            case Never -> {
                if (side == null) return false;
                placed = PlacementUtils.placeBlockPacket(pos, item, getRotateForMine(), 0, false, swapBack);
            }
            case Always -> placed = PlacementUtils.placeBlockPacket(pos, item, getRotateForMine(), 0, true, swapBack);
            case Smart -> {
                if (side != null) {
                    placed = PlacementUtils.placeBlockPacket(pos, item, getRotateForMine(), 0, false, swapBack);
                } else {
                    placed = PlacementUtils.placeBlockPacket(pos, item, getRotateForMine(), 0, true, swapBack);
                }
            }
            default -> placed = false;
        }

        if (!placed) return false;
        placeTimer = placeDelay.get();
        count++;
        if (renderPlace.get()) placeTrail.add(BlockPos.asLong(pos.getX(), pos.getY(), pos.getZ()));
        return true;
    }

    private void debugStateTransition(State nextState, String reason) {
        if (!debugLog.get() || mc.player == null) return;
        int age = mc.player.age;
        if (age == debugStateLastAge) return;
        debugStateLastAge = age;
        highwayDebug("HB state -> %s (reason=%s)", stateName(nextState), reason);
    }

    private void maybeLogMovement() {
        if (!debugLog.get() || mc.player == null || input == null) return;
        if (mc.player.age % 20 != 0) return;
        highwayDebug(
            "HB move state=%s pos=%s f=%s b=%s l=%s r=%s jump=%s sneak=%s",
            stateName(state),
            formatBlockPos(mc.player.getBlockPos()),
            input.isForward(),
            input.isBackward(),
            input.isLeft(),
            input.isRight(),
            input.isJump(),
            input.isSneak()
        );
    }

    private void logForwardSchedulerStatus(String phase, ForwardRowSchedule row, String note, boolean force) {
        if (!forwardSchedulerDebugLog.get() || mc.player == null) return;
        int age = mc.player.age;
        if (!force) {
            if (age == forwardSchedulerLastDebugAge) return;
            if (age % 10 != 0) return;
        }
        forwardSchedulerLastDebugAge = age;

        double playerProjection = currentForwardProjection();
        double boundary = row == null ? Double.NaN : row.frontBoundaryProjection;
        double gap = row == null ? Double.NaN : boundary - playerProjection;
        ForwardTask mineTask = row == null ? null : firstForwardTask(row.mineQueue);
        double mineProjection = mineTask == null ? Double.NaN : projectedForwardCoordinate(mineTask.pos);
        String mineHead = describeForwardTask(mineTask);
        String placeHead = describeForwardTask(row == null ? null : firstForwardTask(row.placeQueue));
       String conflictHead = describeForwardTask(row == null ? null : firstForwardTask(row.conflictQueue));
        boolean includePlayerPosition = "move".equals(phase);
        String playerPositionSegment = includePlayerPosition
            ? String.format(Locale.ROOT, " playerPos=(%.3f, %.3f, %.3f)", mc.player.getX(), mc.player.getY(), mc.player.getZ())
            : "";
        String line = String.format(
            Locale.ROOT,
            "[%s] HB scheduler[%s] row=%s playerProj=%.3f boundary=%.3f gap=%.3f mineProj=%.3f mine=%s place=%s conflict=%s breakTimer=%d placeTimer=%d mineCount=%d/%.2f placeCount=%d/%.2f backstep=%s retries=%d%s note=%s%n",
            Instant.now(),
            phase,
            row == null ? "none" : row.rowId,
            playerProjection,
            boundary,
            gap,
            mineProjection,
            mineHead,
            placeHead,
            conflictHead,
            breakTimer,
            placeTimer,
            forwardMineCount,
            (double) currentMineActionsThisTick(),
            forwardPlaceCount,
            (double) currentPlaceActionsThisTick(),
            forwardSchedulerRuntime.backstepping,
            forwardSchedulerRuntime.recoveryRetries,
            playerPositionSegment,
            note
        );

        writeForwardSchedulerDebugLine(line);
    }

    private void writeForwardSchedulerDebugLine(String line) {
        writeThmDebugLog(FORWARD_SCHEDULER_DEBUG_FILE_NAME, line);
    }

    private void highwayDebug(String message, Object... args) {
        if (!debugLog.get()) return;
        writeThmDebugLog(HIGHWAYBUILDER_DEBUG_FILE_NAME, formatThmDebugLine(message, args));
    }

    /**
     * ponytail: one throttled "what is the builder doing right now / why is it doing nothing" line.
     * Goes to chat and the debug file, only while debug-log is on. Repeats an unchanged reason every
     * 2s so a stall is visibly a stall rather than a single old message.
     */
    private void tickDebug(String reason, Object... args) {
        if (!debugLog.get()) return;

        String message = args.length == 0 ? reason : String.format(Locale.ROOT, reason, args);
        long now = System.currentTimeMillis();
        if (message.equals(lastTickDebugMessage) && now - lastTickDebugAtMs < 2000) return;
        if (now - lastTickDebugAtMs < 1000) return; // ponytail: hard rate limit so a fast state machine can't spam chat

        lastTickDebugMessage = message;
        lastTickDebugAtMs = now;
        info("tick: (highlight)%s", message);
        highwayDebug("HB tick %s", message);
    }

    private void writeReconnectDebug(String message, Object... args) {
        writeThmDebugLog(RECONNECT_DEBUG_FILE_NAME, formatThmDebugLine(message, args));
    }

    private Path getThmDebugLogPath(String fileName) {
        if (mc == null || mc.runDirectory == null) return null;
        return mc.runDirectory.toPath()
            .resolve("logs")
            .resolve(THM_DEBUG_LOG_DIR_NAME)
            .resolve(fileName);
    }

    private String formatThmDebugLine(String message, Object... args) {
        String body;
        try {
            body = String.format(Locale.ROOT, message, args);
        } catch (IllegalFormatException ignored) {
            body = message;
        }
        return String.format(Locale.ROOT, "[%s] %s%n", Instant.now(), body);
    }

    private void writeThmDebugLog(String fileName, String line) {
        Path path = getThmDebugLogPath(fileName);
        if (path == null) return;

        String output = line.endsWith("\n") ? line : line + System.lineSeparator();
        synchronized (THM_DEBUG_LOG_LOCK) {
            try {
                Path parent = path.getParent();
                if (parent != null) Files.createDirectories(parent);
                rotateThmDebugLogIfNeeded(path);
                Files.writeString(path, output, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException e) {
                if (!thmDebugFileErrorLogged) {
                    THMAddon.LOG.warn("Failed to write THM debug log {}: {}", fileName, e.getMessage());
                    thmDebugFileErrorLogged = true;
                }
            }
        }
    }

    private void rotateThmDebugLogIfNeeded(Path path) throws IOException {
        if (!Files.exists(path)) return;
        if (Files.size(path) < THM_DEBUG_LOG_ROTATE_BYTES) return;

        String fileName = path.getFileName().toString();
        String timestamp = STATS_SCREENSHOT_TIME_FORMAT.format(Instant.now());
        Path rotated = path.resolveSibling(fileName + "." + timestamp + ".old");
        int suffix = 1;
        while (Files.exists(rotated)) {
            rotated = path.resolveSibling(fileName + "." + timestamp + "." + suffix++ + ".old");
        }
        Files.move(path, rotated, StandardCopyOption.REPLACE_EXISTING);
    }

    private ForwardTask firstForwardTask(LinkedHashMap<BlockPos, ForwardTask> queue) {
        if (queue == null || queue.isEmpty()) return null;
        return queue.values().iterator().next();
    }

    private String describeForwardTask(ForwardTask task) {
        if (task == null) return "none";

        BlockState state = mc.world == null ? null : mc.world.getBlockState(task.pos);
        boolean inRange = isWithinConfiguredForwardRange(task.pos);
        boolean canMineTask = task.type.mine && state != null && safeCanBreak(task.pos, state) && (task.type.mineBlocksToPlace() || !blocksToPlace.get().contains(state.getBlock()));
        boolean canPlaceTask = !task.type.mine && state != null && (task.type.liquids() ? !state.getFluidState().isEmpty() : BlockUtils.canPlace(task.pos));
        return String.format(
            "%s@%s inRange=%s canMine=%s canPlace=%s owned=%s block=%s",
            task.type,
            formatBlockPos(task.pos),
            inRange,
            canMineTask,
            canPlaceTask,
            hasActiveMineOwnership(task.pos),
            state == null ? "null" : state.getBlock()
        );
    }

    private boolean safeCanBreak(BlockPos pos, BlockState state) {
        try {
            return BlockUtils.canBreak(pos, state);
        } catch (StackOverflowError ignored) {
            if (mc.player == null || mc.world == null) return false;
            if (state.isAir()) return false;
            return state.getHardness(mc.world, pos) >= 0.0f;
        }
    }

    private boolean safeCanInstaBreak(BlockPos pos) {
        try {
            return BlockUtils.canInstaBreak(pos) || isForwardMultiBreakInstamineOverride(pos);
        } catch (StackOverflowError ignored) {
            return false;
        }
    }

    private boolean safeCanInstaBreakForBreaking(BlockPos pos) {
        if (!shouldBypassInstamineOverrideForBreaking(pos)) return safeCanInstaBreak(pos);

        try {
            return BlockUtils.canInstaBreak(pos);
        } catch (StackOverflowError ignored) {
            return false;
        }
    }

    private boolean shouldBypassInstamineOverrideForBreaking(BlockPos pos) {
        return instamineBypass.get() && isForwardMultiBreakInstamineOverride(pos);
    }

    private boolean isForwardMultiBreakInstamineOverride(BlockPos pos) {
        if (mc.world == null || pos == null) return false;

        Block block = mc.world.getBlockState(pos).getBlock();
        return isHalvedInstamineBudgetBlock(block);
    }

    private boolean isHalvedInstamineBudgetBlock(Block block) {
        return block == Blocks.BASALT
            || block == Blocks.SMOOTH_BASALT
            || block == Blocks.POLISHED_BASALT
            || block == Blocks.BLACKSTONE
            || block == Blocks.GILDED_BLACKSTONE;
    }

    private boolean canUseSilentForwardToolSwap(BlockState state, int toolSlot) {
        return silentForwardToolSwap.get()
            && mc.player != null
            && mc.world != null
            && state != null
            && toolSlot >= 0
            && toolSlot < 9
            && !isHalvedInstamineBudgetBlock(state.getBlock())
            && BlockUtils.getBreakDelta(toolSlot, state) >= 1.0;
    }

    private int mineBudgetCost(BlockState state) {
        if (!isHalvedInstamineBudgetBlock(state.getBlock())) return 1;

        double overrideBlocksPerTick = effectiveInstamineOverrideBlocksPerTick();
        double normalBlocksPerTick = Math.max(0.1, effectiveBlocksPerTickActionRate());
        return Math.max(1, (int) Math.ceil(normalBlocksPerTick / overrideBlocksPerTick));
    }

    private void restockDebug(String message, Object... args) {
        if (!restockDebugLog.get()) return;
        writeThmDebugLog(RESTOCK_DEBUG_FILE_NAME, formatThmDebugLine(message, args));
    }

    private boolean shouldLogRestockStateTransition(State previousState, State nextState, State lastState) {
        if (!restockDebugLog.get()) return false;
        return restockTask.isSequenceActive()
            || isRestockState(previousState)
            || isRestockState(nextState)
            || isRestockState(lastState);
    }

    private boolean isRestockState(State state) {
        if (state == null) return false;
        return switch (state) {
            case Center, ThrowOutTrash, Restock, WaitForRestockBlockadeTeardown, PlaceShulkerBlockade, MineShulkerBlockade, PlaceEChestBlockade, MineEChestBlockade, MineEnderChests, KitbotOrder -> true;
            default -> false;
        };
    }

    private String stateName(State state) {
        return state == null ? "null" : state.name();
    }

    private final class RestockWatchdog {
        private final HighwayBuilderTHM b;
        private String taskKey = "none";
        private String lastSignature = "";
        private int lastProgressAge = -1;
        private int retryCount;
        private boolean recoveryPending;
        private int recoveryStartedAge;
        private int recoveryReadyAge;
        private String recoveryReason = "";
        private boolean cleanupBreakStarted;
        private int cleanupBreakDeadlineAge;
        private BlockPos cleanupBreakPos;

        private RestockWatchdog(HighwayBuilderTHM b) {
            this.b = b;
        }

        private void reset(String reason) {
            boolean hadState = lastProgressAge != -1
                || retryCount != 0
                || recoveryPending
                || !lastSignature.isEmpty()
                || cleanupBreakStarted;

            taskKey = "none";
            lastSignature = "";
            lastProgressAge = -1;
            retryCount = 0;
            recoveryPending = false;
            recoveryStartedAge = 0;
            recoveryReadyAge = 0;
            recoveryReason = "";
            cleanupBreakStarted = false;
            cleanupBreakDeadlineAge = 0;
            cleanupBreakPos = null;

            if (hadState && b.restockDebugLog.get()) {
                b.restockDebug("RestockWatchdog reset (%s).", reason);
            }
        }

        private void markProgress(String reason) {
            if (b.mc.player == null || b.mc.world == null) return;
            if (!shouldWatch()) return;

            updateTaskKey();
            lastProgressAge = b.mc.player.age;
            lastSignature = buildSignature();
            if (b.restockDebugLog.get()) {
                b.restockDebug("RestockWatchdog progress (%s, retry=%d/%d).",
                    reason,
                    retryCount,
                    RESTOCK_WATCHDOG_LOCAL_RETRY_MAX
                );
            }
        }

        private void pauseHeartbeat(String reason) {
            if (b.mc.player == null || b.mc.world == null) return;
            if (!shouldWatch()) return;

            updateTaskKey();
            lastProgressAge = b.mc.player.age;
            lastSignature = buildSignature();
            if (recoveryPending) {
                recoveryStartedAge = b.mc.player.age;
                if (recoveryReadyAge > 0) recoveryReadyAge = b.mc.player.age + RESTOCK_WATCHDOG_RECOVERY_DELAY_TICKS;
                if (cleanupBreakStarted) cleanupBreakDeadlineAge = b.mc.player.age + RESTOCK_WATCHDOG_SETUP_TIMEOUT_TICKS;
            }

            if (b.restockDebugLog.get()) {
                b.restockDebug("RestockWatchdog pause heartbeat (%s, retry=%d/%d).",
                    reason,
                    retryCount,
                    RESTOCK_WATCHDOG_LOCAL_RETRY_MAX
                );
            }
        }

        private boolean tickBeforeState() {
            if (!shouldWatch()) {
                if (lastProgressAge != -1 || retryCount != 0 || recoveryPending || !lastSignature.isEmpty()) {
                    reset("inactive");
                }
                return false;
            }

            updateTaskKey();

            if (b.state == State.WaitForRestockBlockadeTeardown && b.restockTask.shouldFinishEmptyBlockadeTeardownWait()) {
                b.finishEmptyRestockTeardownWait("watchdog-empty-teardown");
                return true;
            }

            if (recoveryPending) return tickRecovery();

            detectSignatureProgress("pre-tick");

            if (lastProgressAge == -1) {
                markProgress("watch-start");
                return false;
            }

            int timeoutTicks = getTimeoutTicks();
            if (timeoutTicks <= 0) return false;

            int stagnantTicks = b.mc.player.age - lastProgressAge;
            if (stagnantTicks < timeoutTicks) return false;

            beginRecovery("no progress for " + stagnantTicks + " ticks in " + b.stateName(b.state));
            return true;
        }

        private void tickAfterState() {
            if (!shouldWatch() || recoveryPending) return;
            updateTaskKey();
            detectSignatureProgress("post-tick");
        }

        private boolean shouldWatch() {
            return b.mc.player != null
                && b.mc.world != null
                && b.restockTask.isSequenceActive()
                && (!b.restockTask.tasksInactive() || b.restockTask.hasPendingRestockTasks() || b.restockTask.isBlockadeTeardownPending())
                && b.isRestockState(b.state);
        }

        private void updateTaskKey() {
            String currentTaskKey = b.restockTask.watchdogTaskKey();
            if (Objects.equals(taskKey, currentTaskKey)) return;

            taskKey = currentTaskKey;
            retryCount = 0;
            recoveryPending = false;
            recoveryStartedAge = 0;
            cleanupBreakStarted = false;
            cleanupBreakDeadlineAge = 0;
            cleanupBreakPos = null;
            lastSignature = buildSignature();
            lastProgressAge = b.mc.player != null ? b.mc.player.age : -1;

            if (b.restockDebugLog.get()) {
                b.restockDebug("RestockWatchdog task baseline set to %s.", taskKey);
            }
        }

        private void detectSignatureProgress(String reason) {
            String signature = buildSignature();
            if (signature.equals(lastSignature)) return;

            lastSignature = signature;
            lastProgressAge = b.mc.player.age;
            if (b.restockDebugLog.get()) {
                b.restockDebug("RestockWatchdog observed signature progress (%s, state=%s, task=%s).",
                    reason,
                    b.stateName(b.state),
                    taskKey
                );
            }
        }

        private int getTimeoutTicks() {
            if (b.state == State.MineEnderChests) return RESTOCK_WATCHDOG_MINE_ECHESTS_TIMEOUT_TICKS;

            if (b.state == State.KitbotOrder) {
                if (b.kitbotOrderInFlight) return 0;
                if (b.kitbotOrderFrontendSucceeded && !b.kitbotEnclosureRestorePending) return 0;
                if (b.kitbotNextSubmitAtMs > 0L && System.currentTimeMillis() < b.kitbotNextSubmitAtMs) return 0;
                return RESTOCK_WATCHDOG_SETUP_TIMEOUT_TICKS;
            }

            if (b.state == State.Restock && b.mc.currentScreen != null) {
                return RESTOCK_WATCHDOG_TRANSFER_TIMEOUT_TICKS;
            }

            return RESTOCK_WATCHDOG_SETUP_TIMEOUT_TICKS;
        }

        private void beginRecovery(String reason) {
            if (retryCount >= RESTOCK_WATCHDOG_LOCAL_RETRY_MAX) {
                String message = "Restocking " + b.restockTask.item() + " kept failing (" + reason
                    + ") after " + RESTOCK_WATCHDOG_LOCAL_RETRY_MAX + " retries. Check your restock source is reachable and not blocked.";
                if (b.restockTask.tasksInactive()) {
                    b.notifyDesktop(b.notifyRestockIssues, "THM Highway Builder", message);
                    b.restockTask.finishSequence();
                    b.error(message);
                } else {
                    b.restockTask.failActiveTaskHard(message);
                }
                reset("hard-fail");
                return;
            }

            retryCount++;
            recoveryPending = true;
            recoveryStartedAge = b.mc.player.age;
            recoveryReadyAge = 0;
            recoveryReason = reason;
            cleanupBreakStarted = false;
            cleanupBreakDeadlineAge = 0;
            cleanupBreakPos = null;
            b.input.stop();

            if (b.restockDebugLog.get()) {
                b.restockDebug("RestockWatchdog recovery %d/%d starting for %s (%s).",
                    retryCount,
                    RESTOCK_WATCHDOG_LOCAL_RETRY_MAX,
                    b.restockTask.item(),
                    reason
                );
            }
        }

        private boolean tickRecovery() {
            if (!recoveryPending) return false;
            if (!shouldWatch()) {
                reset("recovery-inactive");
                return false;
            }

            b.input.stop();

            if (recoveryStartedAge > 0
                && b.mc.player.age - recoveryStartedAge > RESTOCK_WATCHDOG_SETUP_TIMEOUT_TICKS) {
                b.restockTask.failActiveTaskHard("Couldn't clean up while recovering " + b.restockTask.item()
                    + " restock (" + recoveryReason + "). Clear your cursor/open screens and try again.");
                reset("cleanup-timeout");
                return true;
            }

            if (cleanupTransientState()) return true;

            if (recoveryReadyAge == 0) {
                recoveryReadyAge = b.mc.player.age + RESTOCK_WATCHDOG_RECOVERY_DELAY_TICKS;
                if (b.restockDebugLog.get()) {
                    b.restockDebug("RestockWatchdog cleanup complete; waiting %d ticks before local retry (%s).",
                        RESTOCK_WATCHDOG_RECOVERY_DELAY_TICKS,
                        recoveryReason
                    );
                }
                return true;
            }

            if (b.mc.player.age < recoveryReadyAge) return true;

            State retryFrom = b.state;
            recoveryPending = false;
            recoveryStartedAge = 0;
            recoveryReadyAge = 0;
            cleanupBreakStarted = false;
            cleanupBreakDeadlineAge = 0;
            cleanupBreakPos = null;

            if (retryFrom == State.KitbotOrder) {
                b.restockDebug("RestockWatchdog restarting KitbotOrder locally after recovery (%s).", recoveryReason);
                b.setState(State.KitbotOrder, State.KitbotOrder);
            } else {
                b.restockTask.setBlockadeReady(false);
                b.invalidRestockRecoveryPending = false;
                b.restockDebug("RestockWatchdog re-centering and rebuilding restock blockade after recovery (%s).", recoveryReason);
                b.setState(State.Center, retryFrom);
            }

            markProgress("recovery-retry");
            return true;
        }

        private boolean cleanupTransientState() {
            if (b.mc.player.currentScreenHandler != null
                && !b.mc.player.currentScreenHandler.getCursorStack().isEmpty()) {
                if (b.clearCursorStackToEmptySlot("RestockWatchdog", true)
                    || b.dropCursorStackIfSafe("RestockWatchdog", true)) {
                    return true;
                }
            }

            if (b.mc.currentScreen != null) {
                b.closeHandledScreen();
                return true;
            }

            if (b.state == State.KitbotOrder) return false;

            return cleanupPlacedSourceContainer();
        }

        private boolean cleanupPlacedSourceContainer() {
            BlockPos sourcePos = getRestockContainerPos();
            if (sourcePos == null) return false;

            BlockState sourceState = b.mc.world.getBlockState(sourcePos);
            Block sourceBlock = sourceState.getBlock();
            boolean sourceContainer = sourceBlock instanceof ShulkerBoxBlock || sourceBlock == Blocks.ENDER_CHEST;
            if (!sourceContainer) {
                cleanupBreakStarted = false;
                cleanupBreakDeadlineAge = 0;
                cleanupBreakPos = null;
                return false;
            }

            if (cleanupBreakStarted
                && Objects.equals(cleanupBreakPos, sourcePos)
                && b.mc.player.age > cleanupBreakDeadlineAge) {
                b.restockTask.failActiveTaskHard("Couldn't break the " + sourceBlock.getName().getString()
                    + " it placed at " + b.formatBlockPos(sourcePos) + ". Break it yourself to continue.");
                reset("cleanup-hard-fail");
                return true;
            }

            if (!b.safeCanBreak(sourcePos, sourceState)) {
                b.restockTask.failActiveTaskHard("Can't break the " + sourceBlock.getName().getString()
                    + " it placed at " + b.formatBlockPos(sourcePos) + " (protected area or wrong tool?). Clear it yourself to continue.");
                reset("cleanup-unbreakable");
                return true;
            }

            boolean noSilkTouch = sourceBlock == Blocks.ENDER_CHEST;
            int selectedSlot = b.mc.player.getInventory().getSelectedSlot();
            int toolSlot = b.findAndMoveBestToolToHotbarForSharedUtility(sourceState, noSilkTouch, false);
            if (sourceBlock == Blocks.ENDER_CHEST && toolSlot == -1) {
                if (b.restockDebugLog.get()) {
                    b.restockDebug("RestockWatchdog leaving placed ender chest at %s because no eligible non-silk pickaxe is available for cleanup.",
                        b.formatBlockPos(sourcePos)
                    );
                }
                return false;
            }

            if (b.breakTimer > 0) return true;

            if (toolSlot != -1 && selectedSlot != toolSlot) InvUtils.swap(toolSlot, false);
            if (b.rotation.get().mine) {
                Rotations.rotate(Rotations.getYaw(sourcePos), Rotations.getPitch(sourcePos), () -> BlockUtils.breakBlock(sourcePos, true));
            } else {
                BlockUtils.breakBlock(sourcePos, true);
            }

            b.breakTimer = b.breakDelay.get();
            cleanupBreakStarted = true;
            cleanupBreakDeadlineAge = b.mc.player.age + RESTOCK_WATCHDOG_SETUP_TIMEOUT_TICKS;
            cleanupBreakPos = sourcePos.toImmutable();

            if (b.restockDebugLog.get()) {
                b.restockDebug("RestockWatchdog cleanup mining %s at %s before retry.",
                    sourceBlock.getName().getString(),
                    b.formatBlockPos(sourcePos)
                );
            }

            return true;
        }

        private BlockPos getRestockContainerPos() {
            if (b.mc.player == null || b.dir == null) return null;

            BlockPos leasedSourcePos = b.restockTask.getSourceContainerPos();
            if (leasedSourcePos != null) return leasedSourcePos;
            if (b.restockTask.isSequenceActive()) return null;

            HorizontalDirection containerDirection = b.getRestockContainerDirection(b.dir);
            MBlockPos restockPos = new MBlockPos();
            restockPos.set(b.mc.player).offset(containerDirection);
            return restockPos.getBlockPos().toImmutable();
        }

        private String buildSignature() {
            StringBuilder sb = new StringBuilder();
            sb.append("state=").append(b.stateName(b.state));
            sb.append("|last=").append(b.stateName(b.lastState));
            sb.append("|active=").append(b.restockTask.activeSummary());
            sb.append("|pending=").append(b.restockTask.pendingSummary());
            sb.append("|blockade=").append(b.restockTask.isBlockadeReady());
            sb.append("|session=").append(b.restockTask.watchdogSessionSummary());
            appendInventorySignature(sb);
            appendScreenSignature(sb);
            appendSourceBlockSignature(sb);
            appendKitbotSignature(sb);
            return sb.toString();
        }

        private void appendInventorySignature(StringBuilder sb) {
            sb.append("|obsidian=").append(countInventoryItems(stack -> stack.getItem() == Items.OBSIDIAN));
            sb.append("|echests=").append(countInventoryItems(stack -> stack.getItem() == Items.ENDER_CHEST));
            sb.append("|pickaxes=").append(countInventoryItems(stack -> stack.isIn(ItemTags.PICKAXES)));
            sb.append("|food=").append(b.countConfiguredFoodItemsInInventory());
            sb.append("|empty=").append(countEmptyInventorySlots());
        }

        private void appendScreenSignature(StringBuilder sb) {
            String screenName = b.mc.currentScreen == null ? "none" : b.mc.currentScreen.getClass().getSimpleName();
            int screenSyncId = b.mc.player.currentScreenHandler != null ? b.mc.player.currentScreenHandler.syncId : -1;
            ItemStack cursorStack = b.mc.player.currentScreenHandler != null
                ? b.mc.player.currentScreenHandler.getCursorStack()
                : ItemStack.EMPTY;

            sb.append("|screen=").append(screenName).append(':').append(screenSyncId);
            if (cursorStack == null || cursorStack.isEmpty()) {
                sb.append("|cursor=empty");
            } else {
                sb.append("|cursor=").append(cursorStack.getItem()).append(':').append(cursorStack.getCount());
            }
        }

        private void appendSourceBlockSignature(StringBuilder sb) {
            BlockPos sourcePos = getRestockContainerPos();
            if (sourcePos == null) return;

            BlockState sourceState = b.mc.world.getBlockState(sourcePos);
            sb.append("|source=").append(sourceState.getBlock()).append('@')
                .append(BlockPos.asLong(sourcePos.getX(), sourcePos.getY(), sourcePos.getZ()));
            sb.append("|sourceObbyDrops=").append(countObsidianItemEntitiesAt(sourcePos));
        }

        private void appendKitbotSignature(StringBuilder sb) {
            if (b.state != State.KitbotOrder) return;

            sb.append("|kitbotActive=").append(b.kitbotEnclosureActive);
            sb.append("|kitbotRestore=").append(b.kitbotEnclosureRestorePending);
            sb.append("|kitbotInFlight=").append(b.kitbotOrderInFlight);
            sb.append("|kitbotSucceeded=").append(b.kitbotOrderFrontendSucceeded);
            sb.append("|kitbotAttempts=").append(b.kitbotOrderAcceptedAttempts);
            sb.append("|kitbotProof=").append(b.countKitbotProofUnitsForActiveRestock());
            appendKitbotFootprintSignature(sb);
        }

        private void appendKitbotFootprintSignature(StringBuilder sb) {
            if (!b.kitbotEnclosureActive && !b.kitbotEnclosureRestorePending) return;
            if (b.kitbotAnchorPos.getY() == 0 || b.dir == null) return;

            HorizontalDirection containerDirection = b.getRestockContainerDirection(b.dir);
            HorizontalDirection expansionDirection = b.getKitbotExpansionDirection(containerDirection);
            KitbotFootprint normal = b.getNormalBlockadeFootprint(b.kitbotAnchorPos.toImmutable(), b.getEffectiveBlockadeType(), b.dir, containerDirection, expansionDirection);
            KitbotFootprint enclosure = b.getKitbotEnclosureFootprint(b.kitbotAnchorPos.toImmutable(), b.dir, containerDirection, expansionDirection);
            LinkedHashSet<BlockPos> positions = new LinkedHashSet<>();
            positions.addAll(normal.requiredAir);
            positions.addAll(normal.requiredBlocks.keySet());
            positions.addAll(enclosure.requiredAir);
            positions.addAll(enclosure.requiredBlocks.keySet());

            sb.append("|kitbotFootprint=");
            for (BlockPos pos : positions) {
                sb.append(BlockPos.asLong(pos.getX(), pos.getY(), pos.getZ())).append(':').append(b.mc.world.getBlockState(pos).getBlock()).append(',');
            }
        }

        private int countInventoryItems(Predicate<ItemStack> predicate) {
            int count = 0;
            for (int i = 0; i < b.mc.player.getInventory().getMainStacks().size(); i++) {
                ItemStack stack = b.mc.player.getInventory().getStack(i);
                if (predicate.test(stack)) count += stack.getCount();
            }
            return count;
        }

        private int countEmptyInventorySlots() {
            int count = 0;
            for (int i = 0; i < b.mc.player.getInventory().getMainStacks().size(); i++) {
                if (b.mc.player.getInventory().getStack(i).isEmpty()) count++;
            }
            return count;
        }

        private int countObsidianItemEntitiesAt(BlockPos pos) {
            int count = 0;
            Box box = new Box(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + 2, pos.getZ() + 1);
            for (Entity entity : b.mc.world.getOtherEntities(b.mc.player, box)) {
                if (entity instanceof ItemEntity itemEntity && itemEntity.getStack().getItem() == Items.OBSIDIAN) {
                    count += itemEntity.getStack().getCount();
                }
            }
            return count;
        }
    }

    private boolean ensureCenterSpeedSnapshotCaptured(String reason) {
        if (!isLegacyCenterSpeedOwnershipAllowed()) {
            centerSpeedLastReason = "capture-skipped-not-legacy:" + reason;
            return false;
        }

        if (centerSpeedSnapshotOwned && centerSpeedSnapshot != null) {
            restockDebug("Center/Speed baseline reused from memory (reason=%s, active=%s, timerActive=%s, monitorOwned=%s, lastReason=%s).",
                reason,
                centerSpeedSnapshot.wasActive(),
                centerSpeedSnapshot.timerWasActive(),
                centerSpeedMonitorRecoveryOwned,
                centerSpeedLastReason
            );
            return true;
        }
        if (centerSpeedSnapshotOwned && centerSpeedSnapshot == null) clearCenterSpeedOwnership("owned-without-snapshot");

        Speed speed = Modules.get().get(Speed.class);
        Timer timer = Modules.get().get(Timer.class);
        if (speed == null) {
            centerSpeedLastReason = "capture-missing-speed:" + reason;
            restockDebug("Center/Speed snapshot skipped: Speed module not found (reason=%s).", reason);
            return false;
        }

        centerSpeedSnapshot = new CenterSpeedSnapshot(
            speed.speedMode.get().name(),
            speed.vanillaSpeed.get(),
            speed.ncpSpeed.get(),
            speed.ncpSpeedLimit.get(),
            speed.timer.get(),
            speed.inLiquids.get(),
            speed.whenSneaking.get(),
            speed.vanillaOnGround.get(),
            speed.isActive(),
            timer != null && timer.isActive()
        );
        centerSpeedSnapshotOwned = true;
        centerSpeedMonitorRecoveryOwned = false;
        centerSpeedLastReason = "captured:" + reason;
        restockDebug(
            "Center/Speed baseline captured (reason=%s, active=%s, mode=%s, vanilla=%.2f, ncp=%.2f, limit=%s, timer=%.2f, liquids=%s, sneaking=%s, onGround=%s, timerActive=%s).",
            reason,
            centerSpeedSnapshot.wasActive(),
            centerSpeedSnapshot.speedModeName(),
            centerSpeedSnapshot.vanillaSpeed(),
            centerSpeedSnapshot.ncpSpeed(),
            centerSpeedSnapshot.ncpSpeedLimit(),
            centerSpeedSnapshot.timer(),
            centerSpeedSnapshot.inLiquids(),
            centerSpeedSnapshot.whenSneaking(),
            centerSpeedSnapshot.vanillaOnGround(),
            centerSpeedSnapshot.timerWasActive()
        );
        return true;
    }

    public void preserveCenterSpeedBaselineForMonitorRecovery(String reason) {
        if (!isLegacyCenterSpeedOwnershipAllowed()) return;
        if (thmSpeedOwnershipActive) return;

        if (centerSpeedSnapshotOwned && centerSpeedSnapshot != null) {
            centerSpeedMonitorRecoveryOwned = true;
            centerSpeedLastReason = "monitor-reuse:" + reason;
            restockDebug("Center/Speed baseline preserved for monitor recovery (reason=%s, reused=true, active=%s, timerActive=%s, overrideActive=%s).",
                reason,
                centerSpeedSnapshot.wasActive(),
                centerSpeedSnapshot.timerWasActive(),
                centerSpeedOverrideActive
            );
            return;
        }

        if (!ensureCenterSpeedSnapshotCaptured("monitor-handoff:" + reason)) return;

        centerSpeedMonitorRecoveryOwned = true;
        centerSpeedLastReason = "monitor-captured:" + reason;
        restockDebug("Center/Speed baseline preserved for monitor recovery (reason=%s, reused=false, active=%s, timerActive=%s, overrideActive=%s).",
            reason,
            centerSpeedSnapshot != null && centerSpeedSnapshot.wasActive(),
            centerSpeedSnapshot != null && centerSpeedSnapshot.timerWasActive(),
            centerSpeedOverrideActive
        );
    }

    private void applyCenterSpeedOverrideIfPossible(String reason) {
        if (!isLegacyCenterSpeedOwnershipAllowed()) return;
        if (isThmSpeedControllerActive()) return;
        if (!ensureCenterSpeedSnapshotCaptured(reason)) return;
        beginCenterSpeedModuleManagerLease(reason);

        Speed speed = Modules.get().get(Speed.class);
        Timer timer = Modules.get().get(Timer.class);
        if (speed == null) {
            centerSpeedLastReason = "override-missing-speed:" + reason;
            restockDebug("Center/Speed override skipped: Speed module not found (reason=%s).", reason);
            return;
        }

        if (timer == null) {
            restockDebug("Center/Speed override continuing without Timer module (reason=%s).", reason);
        } else if (timer.isActive()) {
            timer.toggle();
        }

        speed.speedMode.set(SpeedModes.Vanilla);
        speed.vanillaSpeed.set(CENTER_SPEED_OVERRIDE);
        speed.timer.set(1.0);
        speed.inLiquids.set(false);
        speed.whenSneaking.set(false);
        speed.vanillaOnGround.set(false);
        if (!speed.isActive()) speed.toggle();

        centerSpeedOverrideActive = true;
        centerSpeedLastReason = "override-applied:" + reason;
        restockDebug(
            "Center/Speed override applied (reason=%s, wasActive=%s, mode=%s, vanilla=%.2f, timer=%.2f, liquids=%s, sneaking=%s, onGround=%s, timerForcedOff=%s).",
            reason,
            centerSpeedSnapshot != null && centerSpeedSnapshot.wasActive(),
            SpeedModes.Vanilla.name(),
            CENTER_SPEED_OVERRIDE,
            1.0,
            false,
            false,
            false,
            centerSpeedSnapshot != null && centerSpeedSnapshot.timerWasActive()
        );
    }

    private void restoreCenterSpeedIfOwned(String reason) {
        if (!centerSpeedSnapshotOwned || centerSpeedSnapshot == null) return;

        Speed speed = Modules.get().get(Speed.class);
        Timer timer = Modules.get().get(Timer.class);
        if (speed == null) {
            centerSpeedLastReason = "restore-missing-speed:" + reason;
            restockDebug("Center/Speed restore skipped: Speed module not found (reason=%s).", reason);
            return;
        }

        try {
            speed.speedMode.set(parseCenterSpeedModeOrDefault(centerSpeedSnapshot.speedModeName()));
            speed.vanillaSpeed.set(centerSpeedSnapshot.vanillaSpeed());
            speed.ncpSpeed.set(centerSpeedSnapshot.ncpSpeed());
            speed.ncpSpeedLimit.set(centerSpeedSnapshot.ncpSpeedLimit());
            speed.timer.set(centerSpeedSnapshot.timer());
            speed.inLiquids.set(centerSpeedSnapshot.inLiquids());
            speed.whenSneaking.set(centerSpeedSnapshot.whenSneaking());
            speed.vanillaOnGround.set(centerSpeedSnapshot.vanillaOnGround());

            boolean active = speed.isActive();
            if (centerSpeedSnapshot.wasActive() && !active) speed.toggle();
            else if (!centerSpeedSnapshot.wasActive() && active) speed.toggle();

            if (timer == null) {
                restockDebug("Center/Speed restore continuing without Timer module (reason=%s).", reason);
            } else {
                boolean timerActive = timer.isActive();
                if (centerSpeedSnapshot.timerWasActive() && !timerActive) timer.toggle();
                else if (!centerSpeedSnapshot.timerWasActive() && timerActive) timer.toggle();
            }

            if (!isCenterSpeedStateRestored(speed, timer)) {
                centerSpeedRestorePending = true;
                if (centerSpeedRestoreRetryTicks <= 0) centerSpeedRestoreRetryTicks = CENTER_SPEED_RESTORE_RETRY_WINDOW_TICKS;
                centerSpeedLastReason = "restore-deferred:" + reason;
                restockDebug(
                    "Center/Speed restore deferred (reason=%s, activeNow=%s, timerActiveNow=%s, monitorOwned=%s, cachePreserved=true, retryTicks=%d).",
                    reason,
                    speed.isActive(),
                    timer != null && timer.isActive(),
                    centerSpeedMonitorRecoveryOwned,
                    centerSpeedRestoreRetryTicks
                );
                return;
            }

            centerSpeedRestorePending = false;
            centerSpeedRestoreRetryTicks = 0;
            restockDebug(
                "Center/Speed baseline restored (reason=%s, active=%s, mode=%s, vanilla=%.2f, ncp=%.2f, limit=%s, timer=%.2f, liquids=%s, sneaking=%s, onGround=%s, timerActive=%s, monitorOwned=%s).",
                reason,
                centerSpeedSnapshot.wasActive(),
                centerSpeedSnapshot.speedModeName(),
                centerSpeedSnapshot.vanillaSpeed(),
                centerSpeedSnapshot.ncpSpeed(),
                centerSpeedSnapshot.ncpSpeedLimit(),
                centerSpeedSnapshot.timer(),
                centerSpeedSnapshot.inLiquids(),
                centerSpeedSnapshot.whenSneaking(),
                centerSpeedSnapshot.vanillaOnGround(),
                centerSpeedSnapshot.timerWasActive(),
                centerSpeedMonitorRecoveryOwned
            );
            clearCenterSpeedOwnership("restored:" + reason);
        } catch (Exception e) {
            centerSpeedRestorePending = true;
            if (centerSpeedRestoreRetryTicks <= 0) centerSpeedRestoreRetryTicks = CENTER_SPEED_RESTORE_RETRY_WINDOW_TICKS;
            centerSpeedLastReason = "restore-error:" + e.getClass().getSimpleName();
            restockDebug("Center/Speed restore failed (reason=%s, error=%s).", reason, e.getClass().getSimpleName());
        }
    }

    private void tickDeferredCenterSpeedRestore() {
        if (!centerSpeedRestorePending || !centerSpeedSnapshotOwned || centerSpeedSnapshot == null) return;

        Speed speed = Modules.get().get(Speed.class);
        Timer timer = Modules.get().get(Timer.class);
        if (speed == null) {
            centerSpeedRestorePending = false;
            centerSpeedRestoreRetryTicks = 0;
            centerSpeedLastReason = "restore-abandoned-missing-speed";
            restockDebug("Center/Speed deferred restore abandoned: Speed module missing.");
            return;
        }

        if (isCenterSpeedStateRestored(speed, timer)) {
            centerSpeedRestorePending = false;
            centerSpeedRestoreRetryTicks = 0;
            restockDebug("Center/Speed deferred restore verified complete (lastReason=%s).", centerSpeedLastReason);
            clearCenterSpeedOwnership("restored:deferred-verify");
            return;
        }

        if (centerSpeedRestoreRetryTicks <= 0) {
            centerSpeedRestorePending = false;
            centerSpeedLastReason = "restore-abandoned-timeout";
            restockDebug("Center/Speed deferred restore timed out; preserving snapshot ownership for manual inspection.");
            return;
        }

        centerSpeedRestoreRetryTicks--;
        restoreCenterSpeedIfOwned("deferred-tick");
    }

    private SpeedModes parseCenterSpeedModeOrDefault(String value) {
        if (value == null || value.isBlank()) return SpeedModes.Vanilla;
        try {
            return SpeedModes.valueOf(value.trim());
        } catch (IllegalArgumentException ignored) {
            return SpeedModes.Vanilla;
        }
    }

    private void resumeThmSpeedAfterReconnectIfReady(String reason) {
        if (!thmSpeedReconnectSuppressed) return;
        if (!useThmSpeed.get()) return;
        if (!isActive() || suspended) return;
        if (mc.player == null || mc.world == null || !Utils.canUpdate()) return;
        if (mainServerResumeGateActive) return;
        if (!isNot6B6T() && getCommittedServerState() != ServerState.MAIN_SERVER) return;
        if (!isThmSpeedExecutionAllowed()) return;

        thmSpeedReconnectSuppressed = false;
        syncThmSpeedOwnership(reason);
    }

    private void syncThmSpeedOwnership(String reason) {
        if (!useThmSpeed.get()) {
            if (thmSpeedReconnectSuppressed && !isThmSpeedExecutionAllowed()) {
                parkThmSpeedForReconnect(reason + "-disabled-suppressed");
                return;
            }
            thmSpeedReconnectSuppressed = false;
            releaseThmSpeedOwnership(reason + "-disabled", true);
            return;
        }
        if (thmSpeedReconnectSuppressed) {
            parkThmSpeedForReconnect(reason + "-suppressed");
            return;
        }
        if (!isThmSpeedExecutionAllowed()) {
            if (thmSpeedOwnershipActive || thmSpeedSnapshot != null) {
                parkThmSpeedForReconnect(reason + "-not-allowed");
            }
            return;
        }

        if (centerSpeedSnapshotOwned) {
            restoreCenterSpeedIfOwned("thm-speed-acquire:" + reason);
            if (centerSpeedSnapshotOwned) return;
        }

        if (acquireThmSpeedOwnership(reason)) enforceThmSpeedOwnership(reason);
    }

    private void parkThmSpeedForReconnect(String reason) {
        resetThmSpeedRuntime();

        MeteorSpeedSnapshot snapshot = thmSpeedSnapshot;
        if (snapshot == null) snapshot = loadThmSpeedSnapshot();

        boolean hasHighwayBuilderSpeedState = thmSpeedOwnershipActive || snapshot != null;
        thmSpeedReconnectSuppressed = true;
        thmSpeedOwnershipActive = false;
        thmSpeedRestorePending = false;
        thmSpeedSnapshotDeletePending = false;
        if (snapshot != null) thmSpeedSnapshot = snapshot;

        if (!hasHighwayBuilderSpeedState) return;

        Speed speed = Modules.get().get(Speed.class);
        if (speed == null || !speed.isActive()) return;

        try {
            speed.toggle();
            restockDebug("THM speed ownership parked Meteor Speed off for reconnect (reason=%s).", reason);
        } catch (Exception e) {
            restockDebug("THM speed ownership failed to park Meteor Speed off for reconnect (reason=%s, error=%s).", reason, e.getClass().getSimpleName());
        }
    }

    private boolean acquireThmSpeedOwnership(String reason) {
        Speed speed = Modules.get().get(Speed.class);
        if (speed == null) {
            restockDebug("THM speed ownership skipped: Speed module missing (reason=%s).", reason);
            return false;
        }

        if (thmSpeedSnapshot == null) thmSpeedSnapshot = loadThmSpeedSnapshot();
        if (thmSpeedSnapshot == null) {
            MeteorSpeedSnapshot snapshot = captureMeteorSpeedSnapshot(speed);
            if (!persistThmSpeedSnapshot(snapshot, reason)) {
                restockDebug("THM speed ownership skipped: failed to persist Speed snapshot (reason=%s).", reason);
                thmSpeedSnapshot = null;
                thmSpeedOwnershipActive = false;
                return false;
            }
            thmSpeedSnapshot = snapshot;
        }

        thmSpeedOwnershipActive = true;
        return true;
    }

    private MeteorSpeedSnapshot captureMeteorSpeedSnapshot(Speed speed) {
        return new MeteorSpeedSnapshot(
            speed.speedMode.get().name(),
            speed.vanillaSpeed.get(),
            speed.ncpSpeed.get(),
            speed.ncpSpeedLimit.get(),
            speed.timer.get(),
            speed.inLiquids.get(),
            speed.whenSneaking.get(),
            speed.vanillaOnGround.get(),
            speed.isActive()
        );
    }

    private void enforceThmSpeedOwnership(String reason) {
        if (!thmSpeedOwnershipActive || thmSpeedSnapshot == null || !useThmSpeed.get()) return;
        if (!isThmSpeedExecutionAllowed()) return;

        Speed speed = Modules.get().get(Speed.class);
        if (speed == null || !speed.isActive()) return;

        try {
            speed.toggle();
            restockDebug("THM speed ownership toggled Meteor Speed off (reason=%s).", reason);
        } catch (Exception e) {
            restockDebug("THM speed ownership failed to toggle Meteor Speed off (reason=%s, error=%s).", reason, e.getClass().getSimpleName());
        }
    }

    private void releaseThmSpeedOwnership(String reason, boolean deleteCache) {
        thmSpeedReconnectSuppressed = false;
        resetThmSpeedRuntime();

        MeteorSpeedSnapshot snapshot = thmSpeedSnapshot;
        if (snapshot == null) snapshot = loadThmSpeedSnapshot();
        thmSpeedOwnershipActive = false;
        boolean shouldDeleteCache = deleteCache || thmSpeedSnapshotDeletePending;

        if (snapshot == null) {
            thmSpeedRestorePending = false;
            thmSpeedSnapshotDeletePending = false;
            if (shouldDeleteCache) deleteThmSpeedSnapshot(reason + "-empty");
            return;
        }

        Speed speed = Modules.get().get(Speed.class);
        if (speed == null) {
            thmSpeedSnapshot = snapshot;
            thmSpeedRestorePending = true;
            thmSpeedSnapshotDeletePending = shouldDeleteCache;
            restockDebug("THM speed ownership restore deferred: Speed module missing (reason=%s).", reason);
            return;
        }

        try {
            speed.speedMode.set(parseCenterSpeedModeOrDefault(snapshot.speedModeName()));
            speed.vanillaSpeed.set(snapshot.vanillaSpeed());
            speed.ncpSpeed.set(snapshot.ncpSpeed());
            speed.ncpSpeedLimit.set(snapshot.ncpSpeedLimit());
            speed.timer.set(snapshot.timer());
            speed.inLiquids.set(snapshot.inLiquids());
            speed.whenSneaking.set(snapshot.whenSneaking());
            speed.vanillaOnGround.set(snapshot.vanillaOnGround());

            boolean active = speed.isActive();
            if (active != snapshot.wasActive()) {
                if (!canToggleMeteorSpeedForThmRestore()) {
                    thmSpeedSnapshot = snapshot;
                    thmSpeedRestorePending = true;
                    thmSpeedSnapshotDeletePending = shouldDeleteCache;
                    restockDebug(
                        "THM speed ownership active-state restore deferred until live world (reason=%s, activeNow=%s, targetActive=%s).",
                        reason,
                        active,
                        snapshot.wasActive()
                    );
                    return;
                }

                speed.toggle();
            }

            if (!isThmSpeedStateRestored(speed, snapshot)) {
                thmSpeedSnapshot = snapshot;
                thmSpeedRestorePending = true;
                thmSpeedSnapshotDeletePending = shouldDeleteCache;
                restockDebug(
                    "THM speed ownership restore deferred for verification (reason=%s, activeNow=%s, targetActive=%s).",
                    reason,
                    speed.isActive(),
                    snapshot.wasActive()
                );
                return;
            }

            restockDebug("THM speed ownership restored Meteor Speed (reason=%s, active=%s, mode=%s, vanilla=%.2f).",
                reason,
                snapshot.wasActive(),
                snapshot.speedModeName(),
                snapshot.vanillaSpeed()
            );

            thmSpeedSnapshot = null;
            thmSpeedRestorePending = false;
            thmSpeedSnapshotDeletePending = false;
            if (shouldDeleteCache) deleteThmSpeedSnapshot(reason);
        } catch (Exception e) {
            thmSpeedSnapshot = snapshot;
            thmSpeedRestorePending = true;
            thmSpeedSnapshotDeletePending = shouldDeleteCache;
            restockDebug("THM speed ownership restore failed (reason=%s, error=%s).", reason, e.getClass().getSimpleName());
        }
    }

    private void tickDeferredThmSpeedRestore() {
        if (!thmSpeedRestorePending || thmSpeedSnapshot == null) return;
        if (thmSpeedReconnectSuppressed) return;
        if (useThmSpeed.get() && isActive() && isThmSpeedExecutionAllowed()) return;
        if (!canToggleMeteorSpeedForThmRestore()) return;

        releaseThmSpeedOwnership("deferred-thm-speed-restore", thmSpeedSnapshotDeletePending);
    }

    private boolean canToggleMeteorSpeedForThmRestore() {
        return mc.player != null && mc.world != null && Utils.canUpdate();
    }

    private boolean isThmSpeedStateRestored(Speed speed, MeteorSpeedSnapshot snapshot) {
        return speed != null
            && snapshot != null
            && speed.isActive() == snapshot.wasActive()
            && speed.speedMode.get().name().equals(snapshot.speedModeName())
            && Double.compare(speed.vanillaSpeed.get(), snapshot.vanillaSpeed()) == 0
            && Double.compare(speed.ncpSpeed.get(), snapshot.ncpSpeed()) == 0
            && speed.ncpSpeedLimit.get() == snapshot.ncpSpeedLimit()
            && Double.compare(speed.timer.get(), snapshot.timer()) == 0
            && speed.inLiquids.get() == snapshot.inLiquids()
            && speed.whenSneaking.get() == snapshot.whenSneaking()
            && speed.vanillaOnGround.get() == snapshot.vanillaOnGround();
    }

    private void resetThmSpeedRuntime() {
        boolean wasKitbotCentering = kitbotCenteringActive;
        centerTeleportSlowdownActive = false;
        kitbotCenteringActive = false;
        kitbotCenteringTicks = 0;
        kitbotCenteringDistance = Double.POSITIVE_INFINITY;
        kitbotCenteringTargetX = Double.NaN;
        kitbotCenteringTargetZ = Double.NaN;
        kitbotCenteringReason = "";
        forwardDriftTargetValid = false;
        forwardDriftRecoveryActive = false;
        if (input != null) {
            if (wasKitbotCentering) input.stop();
            else {
                input.left(false);
                input.right(false);
            }
        }
    }

    private void suspendThmSpeedMovementRuntime() {
        boolean wasKitbotCentering = kitbotCenteringActive;
        centerTeleportSlowdownActive = false;
        kitbotCenteringActive = false;
        kitbotCenteringTicks = 0;
        kitbotCenteringDistance = Double.POSITIVE_INFINITY;
        kitbotCenteringTargetX = Double.NaN;
        kitbotCenteringTargetZ = Double.NaN;
        kitbotCenteringReason = "";
        forwardDriftRecoveryActive = false;
        if (input != null) {
            if (wasKitbotCentering) input.stop();
            else {
                input.left(false);
                input.right(false);
            }
        }
    }

    private boolean isThmSpeedControllerActive() {
        if (!isThmSpeedMovementAllowed()) {
            if (useThmSpeed.get()) suspendThmSpeedMovementRuntime();
            return false;
        }

        return isActive()
            && useThmSpeed.get()
            && !thmSpeedReconnectSuppressed
            && thmSpeedOwnershipActive
            && thmSpeedSnapshot != null
            && input != null
            && mc.player != null
            && mc.world != null
            && isThmSpeedExecutionAllowed();
    }

    private boolean isThmSpeedMovementAllowed() {
        return getCommittedServerState() == ServerState.MAIN_SERVER;
    }

    private boolean isThmSpeedExecutionAllowed() {
        return isNot6B6T() || isExecutionAllowedOnCurrentServer(getCommittedServerState());
    }

    private boolean persistThmSpeedSnapshot(MeteorSpeedSnapshot snapshot, String reason) {
        if (snapshot == null) return false;

        Path path = resolveThmSpeedSnapshotPath();
        Path parent = path.getParent();
        Path tmp = path.resolveSibling(path.getFileName() + ".tmp");

        try {
            if (parent != null) Files.createDirectories(parent);
            Files.writeString(tmp, serializeThmSpeedSnapshot(snapshot), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            try {
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
            }
            restockDebug("THM speed snapshot persisted (reason=%s, active=%s, mode=%s, vanilla=%.2f).",
                reason,
                snapshot.wasActive(),
                snapshot.speedModeName(),
                snapshot.vanillaSpeed()
            );
            return true;
        } catch (IOException e) {
            try {
                Files.deleteIfExists(tmp);
            } catch (IOException ignored) {
                // ignore cleanup failure
            }
            restockDebug("THM speed snapshot persist failed (reason=%s, error=%s).", reason, e.getClass().getSimpleName());
            return false;
        }
    }

    private String serializeThmSpeedSnapshot(MeteorSpeedSnapshot snapshot) {
        StringBuilder out = new StringBuilder();
        out.append(THM_SPEED_SNAPSHOT_MAGIC).append('\n');
        appendSpeedSnapshotMeta(out, "version", THM_SPEED_SNAPSHOT_VERSION);
        appendSpeedSnapshotMeta(out, "speedMode", snapshot.speedModeName());
        appendSpeedSnapshotMeta(out, "vanillaSpeed", snapshot.vanillaSpeed());
        appendSpeedSnapshotMeta(out, "ncpSpeed", snapshot.ncpSpeed());
        appendSpeedSnapshotMeta(out, "ncpSpeedLimit", snapshot.ncpSpeedLimit());
        appendSpeedSnapshotMeta(out, "timer", snapshot.timer());
        appendSpeedSnapshotMeta(out, "inLiquids", snapshot.inLiquids());
        appendSpeedSnapshotMeta(out, "whenSneaking", snapshot.whenSneaking());
        appendSpeedSnapshotMeta(out, "vanillaOnGround", snapshot.vanillaOnGround());
        appendSpeedSnapshotMeta(out, "wasActive", snapshot.wasActive());
        return out.toString();
    }

    private void appendSpeedSnapshotMeta(StringBuilder out, String key, Object value) {
        out.append("meta|").append(key).append('|').append(value == null ? "" : value).append('\n');
    }

    private MeteorSpeedSnapshot loadThmSpeedSnapshot() {
        Path path = resolveThmSpeedSnapshotPath();
        if (!Files.exists(path)) return null;

        try {
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            if (lines.isEmpty() || !THM_SPEED_SNAPSHOT_MAGIC.equals(lines.get(0))) {
                deleteThmSpeedSnapshot("invalid-magic");
                return null;
            }

            HashMap<String, String> meta = new HashMap<>();
            for (int i = 1; i < lines.size(); i++) {
                String line = lines.get(i);
                String[] parts = line.split("\\|", 3);
                if (parts.length == 3 && "meta".equals(parts[0])) meta.put(parts[1], parts[2]);
            }

            int version = (int) parseDoubleSafe(meta.get("version"), -1.0);
            if (version != THM_SPEED_SNAPSHOT_VERSION) {
                deleteThmSpeedSnapshot("invalid-version");
                return null;
            }

            MeteorSpeedSnapshot snapshot = new MeteorSpeedSnapshot(
                meta.getOrDefault("speedMode", SpeedModes.Vanilla.name()),
                parseDoubleSafe(meta.get("vanillaSpeed"), 5.6),
                parseDoubleSafe(meta.get("ncpSpeed"), 1.6),
                Boolean.parseBoolean(meta.getOrDefault("ncpSpeedLimit", "false")),
                parseDoubleSafe(meta.get("timer"), 1.0),
                Boolean.parseBoolean(meta.getOrDefault("inLiquids", "false")),
                Boolean.parseBoolean(meta.getOrDefault("whenSneaking", "false")),
                Boolean.parseBoolean(meta.getOrDefault("vanillaOnGround", "false")),
                Boolean.parseBoolean(meta.getOrDefault("wasActive", "false"))
            );
            restockDebug("THM speed snapshot loaded from cache (active=%s, mode=%s, vanilla=%.2f).",
                snapshot.wasActive(),
                snapshot.speedModeName(),
                snapshot.vanillaSpeed()
            );
            return snapshot;
        } catch (IOException e) {
            restockDebug("THM speed snapshot load failed (error=%s).", e.getClass().getSimpleName());
            return null;
        }
    }

    private boolean deleteThmSpeedSnapshot(String reason) {
        try {
            if (Files.deleteIfExists(resolveThmSpeedSnapshotPath())) {
                restockDebug("THM speed snapshot deleted (reason=%s).", reason);
            }
            return true;
        } catch (IOException e) {
            restockDebug("THM speed snapshot delete failed (reason=%s, error=%s).", reason, e.getClass().getSimpleName());
            return false;
        }
    }

    private ReconnectBaselinePayload captureReconnectBaselinePayload() {
        Speed speed = Modules.get().get(Speed.class);
        Timer timer = Modules.get().get(Timer.class);
        if (speed == null) return null;

        double timerEffectiveMultiplier = timer == null ? Timer.OFF : timer.getMultiplier();
        boolean timerActive = timer != null && timer.isActive();
        Double timerOverrideValue = readRawTimerOverrideValue(timer);
        if (timer != null && timerOverrideValue == null) return null;
        boolean timerOverrideActive = timer != null && Double.compare(timerOverrideValue, Timer.OFF) != 0;

        return new ReconnectBaselinePayload(
            dir == null ? "" : dir.name(),
            speed.speedMode.get().name(),
            speed.vanillaSpeed.get(),
            speed.ncpSpeed.get(),
            speed.ncpSpeedLimit.get(),
            speed.timer.get(),
            speed.inLiquids.get(),
            speed.whenSneaking.get(),
            speed.vanillaOnGround.get(),
            speed.isActive(),
            timerActive,
            timerEffectiveMultiplier,
            timerOverrideActive,
            timerOverrideValue == null ? Timer.OFF : timerOverrideValue
        );
    }

    private boolean hasCapturedReconnectBaselineLease(long generation) {
        return reconnectBaselineLease != null
            && reconnectBaselineLease.generation() == generation
            && reconnectBaselineLease.state() == ReconnectBaselineLeaseState.CAPTURED
            && reconnectBaselineLease.payload() != null;
    }

    private boolean reconnectBaselineMatchesLiveState(ReconnectBaselinePayload payload) {
        Speed speed = Modules.get().get(Speed.class);
        Timer timer = Modules.get().get(Timer.class);
        if (speed == null || payload == null) return false;

        boolean timerStateMatches = (timer != null && timer.isActive()) == payload.timerWasActive();
        double timerEffectiveMultiplier = timer == null ? Timer.OFF : timer.getMultiplier();
        Double timerOverrideValue = readRawTimerOverrideValue(timer);
        if (timer != null && timerOverrideValue == null) return false;
        boolean timerOverrideActive = timer != null && Double.compare(timerOverrideValue, Timer.OFF) != 0;

        return timerStateMatches
            && Double.compare(timerEffectiveMultiplier, payload.timerEffectiveMultiplier()) == 0
            && timerOverrideActive == payload.timerOverrideActive()
            && Double.compare(timerOverrideValue == null ? Timer.OFF : timerOverrideValue, payload.timerOverrideValue()) == 0
            && speed.isActive() == payload.speedWasActive()
            && speed.speedMode.get() == parseCenterSpeedModeOrDefault(payload.speedModeName())
            && Double.compare(speed.vanillaSpeed.get(), payload.vanillaSpeed()) == 0
            && Double.compare(speed.ncpSpeed.get(), payload.ncpSpeed()) == 0
            && speed.ncpSpeedLimit.get() == payload.ncpSpeedLimit()
            && Double.compare(speed.timer.get(), payload.timer()) == 0
            && speed.inLiquids.get() == payload.inLiquids()
            && speed.whenSneaking.get() == payload.whenSneaking()
            && speed.vanillaOnGround.get() == payload.vanillaOnGround();
    }

    private ReconnectBaselineRestoreResult restoreReconnectBaselineForResume(long generation) {
        StatsArtifactSnapshot artifact = peekAuthoritativeStatsArtifact();
        if (artifact == null
            && isResumableStatsSession(statsCacheSnapshot)
            && statsCacheSnapshot.reconnectCycleId() == generation
            && statsCacheSnapshot.reconnectBaselinePayload() != null) {
            // Reconnect activation restores and marks the stats artifact consumed before the
            // baseline restore runs. Keep the loaded snapshot usable for this same generation,
            // including after a client restart where no in-memory lease exists yet.
            artifact = statsCacheSnapshot;
        }
        ReconnectBaselinePayload durablePayload = getDurableReconnectBaselinePayload(artifact, generation);
        writeReconnectDebug(
            "resume baseline lookup cycle=%d durablePresent=%s artifactCycle=%d artifactState=%s artifactKind=%s legacyState=%s",
            generation,
            durablePayload != null,
            artifact == null ? 0L : artifact.reconnectCycleId(),
            artifact == null ? "none" : artifact.state().name(),
            artifact == null ? "none" : artifact.kind().name(),
            reconnectBaselineLease == null ? "none" : reconnectBaselineLease.state().name()
        );
        if (durablePayload != null) {
            ReconnectBaselineRestoreResult result = restoreReconnectBaselinePayload(durablePayload);
            if (!result.success()) return ReconnectBaselineRestoreResult.fail("durable-" + result.reason());
            reconnectBaselineLease = new ReconnectBaselineLease(generation, ReconnectBaselineLeaseState.CONSUMED, durablePayload);
            writeReconnectDebug("restored durable reconnect baseline cycle=%d direction=%s", generation, durablePayload.workingDirectionName());
            return ReconnectBaselineRestoreResult.ok();
        }

        if (artifact != null && artifact.reconnectBaselinePayload() != null && artifact.reconnectCycleId() != generation) {
            return ReconnectBaselineRestoreResult.fail(String.format(Locale.ROOT,
                "durable-baseline-cycle-mismatch cached=%d active=%d",
                artifact.reconnectCycleId(),
                generation
            ));
        }

        return consumeReconnectBaseline(generation);
    }

    private ReconnectBaselineRestoreResult consumeReconnectBaseline(long generation) {
        if (reconnectBaselineLease == null) return ReconnectBaselineRestoreResult.fail("legacy-baseline-missing");
        if (reconnectBaselineLease.generation() != generation) return ReconnectBaselineRestoreResult.fail(String.format(Locale.ROOT,
            "legacy-baseline-cycle-mismatch cached=%d active=%d",
            reconnectBaselineLease.generation(),
            generation
        ));
        if (reconnectBaselineLease.state() != ReconnectBaselineLeaseState.CAPTURED) {
            return ReconnectBaselineRestoreResult.fail("legacy-baseline-" + reconnectBaselineLease.state().name().toLowerCase(Locale.ROOT));
        }

        ReconnectBaselinePayload payload = reconnectBaselineLease.payload();
        ReconnectBaselineRestoreResult result = restoreReconnectBaselinePayload(payload);
        if (!result.success()) return result;

        reconnectBaselineLease = new ReconnectBaselineLease(
            generation,
            ReconnectBaselineLeaseState.CONSUMED,
            payload
        );
        return ReconnectBaselineRestoreResult.ok();
    }

    private ReconnectBaselineRestoreResult restoreReconnectBaselinePayload(ReconnectBaselinePayload payload) {
        Speed speed = Modules.get().get(Speed.class);
        Timer timer = Modules.get().get(Timer.class);
        if (speed == null) return ReconnectBaselineRestoreResult.fail("speed-module-missing");
        if (payload == null) return ReconnectBaselineRestoreResult.fail("baseline-payload-missing");

        reconnectBaselineRestoreInProgress = true;
        try {
            speed.speedMode.set(parseCenterSpeedModeOrDefault(payload.speedModeName()));
            speed.vanillaSpeed.set(payload.vanillaSpeed());
            speed.ncpSpeed.set(payload.ncpSpeed());
            speed.ncpSpeedLimit.set(payload.ncpSpeedLimit());
            speed.timer.set(payload.timer());
            speed.inLiquids.set(payload.inLiquids());
            speed.whenSneaking.set(payload.whenSneaking());
            speed.vanillaOnGround.set(payload.vanillaOnGround());

            if (payload.speedWasActive() != speed.isActive()) speed.toggle();

            if (timer != null) {
                timer.setOverride(Timer.OFF);
                if (payload.timerWasActive() != timer.isActive()) timer.toggle();
                if (payload.timerOverrideActive()) timer.setOverride(payload.timerOverrideValue());
            }
        } finally {
            reconnectBaselineRestoreInProgress = false;
        }

        if (!reconnectBaselineMatchesLiveState(payload)) return ReconnectBaselineRestoreResult.fail("baseline-restore-mismatch");
        return ReconnectBaselineRestoreResult.ok();
    }

    private void invalidateReconnectBaseline(String reason) {
        if (reconnectBaselineLease == null) return;
        if (reconnectBaselineLease.state() != ReconnectBaselineLeaseState.CAPTURED) return;

        reconnectBaselineLease = new ReconnectBaselineLease(
            reconnectBaselineLease.generation(),
            ReconnectBaselineLeaseState.INVALIDATED,
            reconnectBaselineLease.payload()
        );
        restockDebug("Reconnect baseline invalidated (reason=%s, generation=%d).", reason, reconnectBaselineLease.generation());
    }

    private Field getTimerOverrideField() {
        if (timerOverrideFieldInitialized) return timerOverrideField;

        timerOverrideFieldInitialized = true;
        try {
            timerOverrideField = Timer.class.getDeclaredField("override");
            timerOverrideField.setAccessible(true);
        } catch (Throwable ignored) {
            timerOverrideField = null;
            noteTimerOverrideReflectionFailure("field-access");
        }

        return timerOverrideField;
    }

    private Double readRawTimerOverrideValue(Timer timer) {
        if (timer == null) return Timer.OFF;

        Field overrideField = getTimerOverrideField();
        if (overrideField == null) return null;

        try {
            return overrideField.getDouble(timer);
        } catch (Throwable ignored) {
            noteTimerOverrideReflectionFailure("field-read");
            return null;
        }
    }

    private void noteTimerOverrideReflectionFailure(String phase) {
        if (timerOverrideReflectionFailureLogged) return;
        timerOverrideReflectionFailureLogged = true;
        warning("Reconnect Timer baseline unavailable: unable to read Timer override state during %s.", phase);
    }

    private Field getEChestMemoryKnownField() {
        if (eChestMemoryKnownFieldInitialized) return eChestMemoryKnownField;

        eChestMemoryKnownFieldInitialized = true;
        try {
            eChestMemoryKnownField = EChestMemory.class.getDeclaredField("isKnown");
            eChestMemoryKnownField.setAccessible(true);
        } catch (Throwable ignored) {
            eChestMemoryKnownField = null;
            noteEChestMemoryReflectionFailure("field-access");
        }

        return eChestMemoryKnownField;
    }

    private void noteEChestMemoryReflectionFailure(String phase) {
        if (eChestMemoryReflectionFailureLogged) return;
        eChestMemoryReflectionFailureLogged = true;
        warning("Unable to invalidate cached ender chest memory during %s.", phase);
    }

    private void invalidateEChestMemorySnapshot(String reason) {
        EChestMemory.ITEMS.clear();

        Field knownField = getEChestMemoryKnownField();
        if (knownField != null) {
            try {
                knownField.setBoolean(null, false);
            } catch (Throwable ignored) {
                noteEChestMemoryReflectionFailure("field-write");
            }
        }

        if (restockDebugLog.get()) {
            restockDebug("Invalidated cached ender chest memory after %s.", reason);
        }
    }

    private boolean isCenterSpeedStateRestored(Speed speed, Timer timer) {
        if (speed == null || centerSpeedSnapshot == null) return false;

        boolean timerStateMatches = timer == null
            ? !centerSpeedSnapshot.timerWasActive()
            : timer.isActive() == centerSpeedSnapshot.timerWasActive();
        return timerStateMatches
            && speed.isActive() == centerSpeedSnapshot.wasActive()
            && speed.speedMode.get() == parseCenterSpeedModeOrDefault(centerSpeedSnapshot.speedModeName())
            && Double.compare(speed.vanillaSpeed.get(), centerSpeedSnapshot.vanillaSpeed()) == 0
            && Double.compare(speed.ncpSpeed.get(), centerSpeedSnapshot.ncpSpeed()) == 0
            && speed.ncpSpeedLimit.get() == centerSpeedSnapshot.ncpSpeedLimit()
            && Double.compare(speed.timer.get(), centerSpeedSnapshot.timer()) == 0
            && speed.inLiquids.get() == centerSpeedSnapshot.inLiquids()
            && speed.whenSneaking.get() == centerSpeedSnapshot.whenSneaking()
            && speed.vanillaOnGround.get() == centerSpeedSnapshot.vanillaOnGround();
    }

    private double parseDoubleSafe(String value, double fallback) {
        if (value == null || value.isBlank()) return fallback;
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private void clearCenterSpeedOwnership(String reason) {
        centerSpeedSnapshotOwned = false;
        centerSpeedSnapshot = null;
        centerSpeedOverrideActive = false;
        centerSpeedMonitorRecoveryOwned = false;
        centerSpeedRestorePending = false;
        centerSpeedRestoreRetryTicks = 0;
        centerSpeedLastReason = reason == null ? "" : reason;
        endCenterSpeedModuleManagerLease(reason);
    }

    private void clearEChestBreakSpeedOwnership() {
        eChestBreakSpeedSnapshotOwned = false;
        eChestBreakSpeedSnapshot = null;
        endEChestBreakSpeedModuleManagerLease("clear-echest-break-speed-ownership");
    }

    private boolean ensureEChestBreakSpeedSnapshotCaptured(String reason) {
        if (eChestBreakSpeedSnapshotOwned && eChestBreakSpeedSnapshot != null) return true;
        if (eChestBreakSpeedSnapshotOwned) clearEChestBreakSpeedOwnership();

        Timer timer = Modules.get().get(Timer.class);
        Speed speed = Modules.get().get(Speed.class);
        Double timerOverrideValue = timer == null ? Timer.OFF : readRawTimerOverrideValue(timer);

        eChestBreakSpeedSnapshot = new EChestBreakSpeedSnapshot(
            timer != null && timer.isActive(),
            timerOverrideValue == null ? Timer.OFF : timerOverrideValue,
            speed != null && speed.isActive(),
            speed == null ? 1.0 : speed.timer.get()
        );
        eChestBreakSpeedSnapshotOwned = true;

        restockDebug(
            "MineEnderChests break-speed baseline captured (reason=%s, timerActive=%s, timerOverride=%.2f, speedActive=%s, speedTimer=%.2f).",
            reason,
            eChestBreakSpeedSnapshot.timerWasActive(),
            eChestBreakSpeedSnapshot.timerOverrideValue(),
            eChestBreakSpeedSnapshot.speedWasActive(),
            eChestBreakSpeedSnapshot.speedTimerValue()
        );
        return true;
    }

    private void applyEChestBreakSpeedOverrideIfPossible(String reason) {
        if (!useBreakSpeedMultiplier.get()) return;
        if (!ensureEChestBreakSpeedSnapshotCaptured(reason)) return;
        beginEChestBreakSpeedModuleManagerLease(reason);

        Timer timer = Modules.get().get(Timer.class);
        Speed speed = Modules.get().get(Speed.class);

        if (speed != null) speed.timer.set(1.0);
        if (timer != null) {
            if (!timer.isActive()) timer.toggle();
            timer.setOverride(breakSpeedMultiplier.get());
        }

        restockDebug(
            "MineEnderChests break-speed override applied (reason=%s, multiplier=%.2f, timerPresent=%s, speedPresent=%s).",
            reason,
            breakSpeedMultiplier.get(),
            timer != null,
            speed != null
        );
    }

    private void restoreEChestBreakSpeedIfOwned(String reason) {
        if (!eChestBreakSpeedSnapshotOwned || eChestBreakSpeedSnapshot == null) return;

        Timer timer = Modules.get().get(Timer.class);
        Speed speed = Modules.get().get(Speed.class);

        if (speed != null) speed.timer.set(eChestBreakSpeedSnapshot.speedTimerValue());
        if (timer != null) {
            timer.setOverride(eChestBreakSpeedSnapshot.timerOverrideValue());
            boolean timerActive = timer.isActive();
            if (eChestBreakSpeedSnapshot.timerWasActive() && !timerActive) timer.toggle();
            else if (!eChestBreakSpeedSnapshot.timerWasActive() && timerActive) timer.toggle();
        }

        restockDebug(
            "MineEnderChests break-speed baseline restored (reason=%s, timerActive=%s, timerOverride=%.2f, speedActive=%s, speedTimer=%.2f).",
            reason,
            eChestBreakSpeedSnapshot.timerWasActive(),
            eChestBreakSpeedSnapshot.timerOverrideValue(),
            eChestBreakSpeedSnapshot.speedWasActive(),
            eChestBreakSpeedSnapshot.speedTimerValue()
        );

        clearEChestBreakSpeedOwnership();
    }

    private boolean hasActiveInMemoryStatsSession() {
        return start != null && activeStatsSessionId != null && !activeStatsSessionId.isBlank();
    }

    private void clearPendingForwardBreakCredits() {
        pendingForwardBreakCredits.clear();
    }

    private void clearActiveInMemoryStatsSession() {
        start = null;
        blocksBroken = 0;
        blocksPlaced = 0;
        displayInfo = false;
        activeStatsSessionId = null;
        activeStatsGeneration = 0L;
        lastPrintedStatsSessionId = null;
        statsSessionDirty = false;
        clearPendingForwardBreakCredits();
        nextStatsCheckpointAtMs = 0L;
        loadedStatsArtifactIdentity = null;
    }

    private void loadStatsCacheFromDisk() {
        clearActiveInMemoryStatsSession();
        statsCacheSnapshot = null;
        retiredStatsReportSnapshot = null;
        memoryRetryMode = false;
        nextStatsStorageRetryAtMs = 0L;

        StatsArtifactLoadResult canonical = loadStatsArtifact(resolveCanonicalStatsArtifactPath(), StatsArtifactKind.CANONICAL);
        StatsArtifactLoadResult finalization = loadStatsArtifact(resolveFinalizationRecordPath(), StatsArtifactKind.FINALIZATION);
        StatsArtifactLoadResult shadow = loadStatsArtifact(resolveShadowStatsArtifactPath(), StatsArtifactKind.SHADOW);

        if (canonical.transientFailure()) {
            memoryRetryMode = true;
            nextStatsStorageRetryAtMs = System.currentTimeMillis() + STATS_MEMORY_RETRY_RECHECK_MS;
        }

        StatsArtifactSnapshot selected = selectAuthoritativeStatsArtifact(canonical.snapshot(), finalization.snapshot(), shadow.snapshot());
        if (selected == null) return;

        StatsArtifactIdentity identity = identityOf(selected);
        if (consumedStatsArtifactKeys.contains(identity.key())) {
            statsCacheDebug("ignored consumed artifact kind={} session={} generation={}",
                selected.kind(),
                shortSessionId(selected.sessionId()),
                selected.generation()
            );
            return;
        }

        statsCacheSnapshot = selected;
        loadedStatsArtifactIdentity = identity;
        if (selected.kind() == StatsArtifactKind.SHADOW) {
            memoryRetryMode = true;
            nextStatsStorageRetryAtMs = System.currentTimeMillis() + STATS_MEMORY_RETRY_RECHECK_MS;
        }

        statsCacheDebug("loaded reason=startup kind={} session={} generation={} state={} resumeAllowed={} broken={} placed={}",
            selected.kind(),
            shortSessionId(selected.sessionId()),
            selected.generation(),
            selected.state(),
            selected.resumeAllowed(),
            selected.blocksBroken(),
            selected.blocksPlaced()
        );
    }

    private StatsArtifactSnapshot selectAuthoritativeStatsArtifact(StatsArtifactSnapshot... snapshots) {
        StatsArtifactSnapshot selected = null;
        for (StatsArtifactSnapshot snapshot : snapshots) {
            if (snapshot == null) continue;
            if (selected == null || compareArtifactPriority(snapshot, selected) > 0) selected = snapshot;
        }
        return selected;
    }

    private StatsArtifactSnapshot findNewestOpenStatsArtifact() {
        StatsArtifactSnapshot canonical = peekStatsArtifact(resolveCanonicalStatsArtifactPath(), StatsArtifactKind.CANONICAL);
        StatsArtifactSnapshot shadow = peekStatsArtifact(resolveShadowStatsArtifactPath(), StatsArtifactKind.SHADOW);
        StatsArtifactSnapshot selected = null;

        for (StatsArtifactSnapshot snapshot : new StatsArtifactSnapshot[] {canonical, shadow}) {
            if (!isResumableStatsSession(snapshot)) continue;
            if (selected == null || compareArtifactPriority(snapshot, selected) > 0) selected = snapshot;
        }

        return selected;
    }

    private boolean hasFinalizationArtifactForSession(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) return false;
        StatsArtifactSnapshot finalization = peekStatsArtifact(resolveFinalizationRecordPath(), StatsArtifactKind.FINALIZATION);
        return finalization != null && Objects.equals(finalization.sessionId(), sessionId);
    }

    private boolean sameReconnectArtifactIdentity(StatsArtifactSnapshot expected, StatsArtifactSnapshot current) {
        return expected != null
            && current != null
            && expected.kind() == current.kind()
            && Objects.equals(expected.sessionId(), current.sessionId())
            && expected.generation() == current.generation()
            && expected.state() == current.state()
            && expected.resumeAllowed() == current.resumeAllowed()
            && expected.reconnectCycleId() == current.reconnectCycleId()
            && current.reconnectBaselinePayload() != null;
    }

    private StatsArtifactSnapshot copyOpenStatsArtifactWithoutReconnectMetadata(StatsArtifactSnapshot snapshot, long generation) {
        return new StatsArtifactSnapshot(
            snapshot.kind(),
            snapshot.sessionId(),
            generation,
            snapshot.state(),
            snapshot.resumeAllowed(),
            snapshot.workingDirectionName(),
            snapshot.startX(),
            snapshot.startY(),
            snapshot.startZ(),
            snapshot.blocksBroken(),
            snapshot.blocksPlaced(),
            snapshot.displayInfo(),
            System.currentTimeMillis(),
            snapshot.printedAt(),
            snapshot.printedToChat(),
            snapshot.webhookSendCommitted(),
            snapshot.apiSendCommitted(),
            snapshot.finalizationReason(),
            0L,
            null
        );
    }

    private int compareArtifactPriority(StatsArtifactSnapshot left, StatsArtifactSnapshot right) {
        int generationCompare = Long.compare(left.generation(), right.generation());
        if (generationCompare != 0) return generationCompare;
        return Integer.compare(artifactPriority(left.kind()), artifactPriority(right.kind()));
    }

    private int artifactPriority(StatsArtifactKind kind) {
        return switch (kind) {
            case FINALIZATION -> 3;
            case SHADOW -> 2;
            case CANONICAL -> 1;
        };
    }

    private boolean isResumableStatsSession(StatsArtifactSnapshot snapshot) {
        return snapshot != null
            && snapshot.state() == StatsSessionState.OPEN
            && snapshot.resumeAllowed()
            && snapshot.kind() != StatsArtifactKind.FINALIZATION;
    }

    private ReconnectBaselinePayload getDurableReconnectBaselinePayload(StatsArtifactSnapshot snapshot, long reconnectCycleId) {
        if (!isResumableStatsSession(snapshot)) return null;
        if (reconnectCycleId <= 0L) return null;
        if (snapshot.reconnectCycleId() != reconnectCycleId) return null;
        return snapshot.reconnectBaselinePayload();
    }

    private boolean isPendingPrintStatsSession(StatsArtifactSnapshot snapshot) {
        return snapshot != null && snapshot.kind() == StatsArtifactKind.FINALIZATION;
    }

    private void startFreshStatsSession() {
        statsSessionTerminalOrFinalizing = false;
        retiredStatsReportSnapshot = null;
        start = mc.player.getEntityPos();
        blocksBroken = 0;
        blocksPlaced = 0;
        displayInfo = true;
        activeStatsSessionId = UUID.randomUUID().toString();
        activeStatsGeneration = 0L;
        statsSessionDirty = false;
        clearPendingForwardBreakCredits();
        persistCurrentStatsSession(StatsSessionState.OPEN, true, 0L, "fresh-activate");
    }

    private void restoreStatsFromCache(StatsArtifactSnapshot snapshot, String reason) {
        if (snapshot == null) return;

        statsSessionTerminalOrFinalizing = false;
        retiredStatsReportSnapshot = null;
        start = new Vec3d(snapshot.startX(), snapshot.startY(), snapshot.startZ());
        blocksBroken = snapshot.blocksBroken();
        blocksPlaced = snapshot.blocksPlaced();
        displayInfo = snapshot.displayInfo();
        activeStatsSessionId = snapshot.sessionId();
        activeStatsGeneration = snapshot.generation();
        lastPrintedStatsSessionId = snapshot.printedToChat() ? snapshot.sessionId() : null;
        statsSessionDirty = false;
        clearPendingForwardBreakCredits();
        nextStatsCheckpointAtMs = snapshot.lastCheckpointAt() <= 0
            ? System.currentTimeMillis() + STATS_CHECKPOINT_INTERVAL_MS
            : snapshot.lastCheckpointAt() + STATS_CHECKPOINT_INTERVAL_MS;

        statsCacheDebug("restored reason={} kind={} session={} generation={} state={} broken={} placed={} start=({}, {}, {})",
            reason,
            snapshot.kind(),
            shortSessionId(snapshot.sessionId()),
            snapshot.generation(),
            snapshot.state(),
            blocksBroken,
            blocksPlaced,
            snapshot.startX(),
            snapshot.startY(),
            snapshot.startZ()
        );
    }

    private void maybeCheckpointStatsSession() {
        if (!hasActiveInMemoryStatsSession()) return;
        if (System.currentTimeMillis() < nextStatsCheckpointAtMs) return;
        persistCurrentStatsSession(StatsSessionState.OPEN, true, 0L, statsSessionDirty ? "interval-checkpoint-dirty" : "interval-checkpoint");
    }

    private boolean persistCurrentStatsSession(StatsSessionState state, boolean resumeAllowed, long printedAt, String reason) {
        if (!hasActiveInMemoryStatsSession()) return false;
        if (state == StatsSessionState.OPEN && statsSessionTerminalOrFinalizing) {
            statsCacheDebug("skipped OPEN persist reason={} session={} because finalization is armed.",
                reason,
                shortSessionId(activeStatsSessionId)
            );
            return false;
        }

        StatsArtifactSnapshot snapshot = createCurrentStatsSnapshot(memoryRetryMode ? StatsArtifactKind.SHADOW : StatsArtifactKind.CANONICAL, state, resumeAllowed, printedAt);
        return persistActiveStatsArtifact(snapshot, reason);
    }

    private StatsArtifactSnapshot createCurrentStatsSnapshot(StatsArtifactKind kind, StatsSessionState state, boolean resumeAllowed, long printedAt) {
        long checkpointAt = System.currentTimeMillis();
        if (activeStatsSessionId == null || activeStatsSessionId.isBlank()) activeStatsSessionId = UUID.randomUUID().toString();
        ReconnectBaselineLease reconnectLease = reconnectBaselineLease;
        ReconnectBaselinePayload reconnectPayload = null;
        long reconnectCycleId = 0L;
        if (state == StatsSessionState.OPEN && resumeAllowed && reconnectLease != null) {
            if (reconnectLease.state() == ReconnectBaselineLeaseState.CAPTURED) {
                reconnectPayload = reconnectLease.payload();
                reconnectCycleId = reconnectLease.generation();
            } else if (reconnectLease.state() == ReconnectBaselineLeaseState.INVALIDATED) {
                StatsArtifactSnapshot previous = peekAuthoritativeStatsArtifact();
                ReconnectBaselinePayload previousPayload = getDurableReconnectBaselinePayload(previous, reconnectLease.generation());
                if (previousPayload != null) {
                    reconnectPayload = previousPayload;
                    reconnectCycleId = reconnectLease.generation();
                }
            }
        }

        return new StatsArtifactSnapshot(
            kind,
            activeStatsSessionId,
            nextStatsGeneration(),
            state,
            resumeAllowed,
            dir == null ? "" : dir.name(),
            start.x,
            start.y,
            start.z,
            blocksBroken,
            blocksPlaced,
            displayInfo,
            checkpointAt,
            printedAt,
            printedAt > 0L,
            false,
            false,
            "",
            reconnectCycleId,
            reconnectPayload
        );
    }

    private long nextStatsGeneration() {
        long now = System.currentTimeMillis();
        activeStatsGeneration = Math.max(now, activeStatsGeneration + 1L);
        return activeStatsGeneration;
    }

    private long nextStatsGenerationAfter(long baselineGeneration) {
        activeStatsGeneration = Math.max(activeStatsGeneration, baselineGeneration);
        return nextStatsGeneration();
    }

    private StatsArtifactSnapshot copyStatsArtifact(
        StatsArtifactSnapshot snapshot,
        StatsArtifactKind kind,
        StatsSessionState state,
        boolean resumeAllowed,
        long printedAt,
        boolean printedToChat,
        boolean webhookSendCommitted,
        boolean apiSendCommitted,
        String finalizationReason,
        long generation
    ) {
        return new StatsArtifactSnapshot(
            kind,
            snapshot.sessionId(),
            generation,
            state,
            resumeAllowed,
            snapshot.workingDirectionName(),
            snapshot.startX(),
            snapshot.startY(),
            snapshot.startZ(),
            snapshot.blocksBroken(),
            snapshot.blocksPlaced(),
            snapshot.displayInfo(),
            System.currentTimeMillis(),
            printedAt,
            printedToChat,
            webhookSendCommitted,
            apiSendCommitted,
            finalizationReason,
            snapshot.reconnectCycleId(),
            snapshot.reconnectBaselinePayload()
        );
    }

    private boolean persistActiveStatsArtifact(StatsArtifactSnapshot snapshot, String reason) {
        if (snapshot == null) return false;

        if (!memoryRetryMode) {
            return persistStatsArtifactSnapshot(copyStatsArtifact(
                snapshot,
                StatsArtifactKind.CANONICAL,
                snapshot.state(),
                snapshot.resumeAllowed(),
                snapshot.printedAt(),
                snapshot.printedToChat(),
                snapshot.webhookSendCommitted(),
                snapshot.apiSendCommitted(),
                snapshot.finalizationReason(),
                snapshot.generation()
            ), reason);
        }

        StatsArtifactSnapshot canonicalSnapshot = copyStatsArtifact(
            snapshot,
            StatsArtifactKind.CANONICAL,
            snapshot.state(),
            snapshot.resumeAllowed(),
            snapshot.printedAt(),
            snapshot.printedToChat(),
            snapshot.webhookSendCommitted(),
            snapshot.apiSendCommitted(),
            snapshot.finalizationReason(),
            snapshot.generation()
        );
        if (System.currentTimeMillis() >= nextStatsStorageRetryAtMs && persistStatsArtifactSnapshot(canonicalSnapshot, reason + "-recover-canonical")) {
            if (deleteStatsArtifact(resolveShadowStatsArtifactPath(), "shadow-cleared-after-canonical-recovery")) {
                memoryRetryMode = false;
                nextStatsStorageRetryAtMs = 0L;
                return true;
            }

            statsCacheDebug("canonical recovery succeeded but shadow cleanup failed; staying in memory-retry mode until shadow is cleared.");
        }

        nextStatsStorageRetryAtMs = System.currentTimeMillis() + STATS_MEMORY_RETRY_RECHECK_MS;
        return persistStatsArtifactSnapshot(copyStatsArtifact(
            snapshot,
            StatsArtifactKind.SHADOW,
            snapshot.state(),
            snapshot.resumeAllowed(),
            snapshot.printedAt(),
            snapshot.printedToChat(),
            snapshot.webhookSendCommitted(),
            snapshot.apiSendCommitted(),
            snapshot.finalizationReason(),
            snapshot.generation()
        ), reason + "-shadow");
    }

    private boolean persistStatsArtifactSnapshot(StatsArtifactSnapshot snapshot, String reason) {
        if (snapshot == null) return false;

        try {
            writeStatsArtifact(resolveStatsArtifactPath(snapshot.kind()), snapshot);
            StatsArtifactSnapshot previous = statsCacheSnapshot;
            statsCacheSnapshot = snapshot;
            if (snapshot.kind() != StatsArtifactKind.FINALIZATION) activeStatsGeneration = snapshot.generation();
            if (snapshot.state() == StatsSessionState.OPEN) nextStatsCheckpointAtMs = snapshot.lastCheckpointAt() + STATS_CHECKPOINT_INTERVAL_MS;
            statsSessionDirty = false;

            statsCacheDebug("saved reason={} kind={} session={} generation={} {}->{} resumeAllowed={} broken={} placed={} printedAt={}",
                reason,
                snapshot.kind(),
                shortSessionId(snapshot.sessionId()),
                snapshot.generation(),
                previous == null ? "null" : previous.state(),
                snapshot.state(),
                snapshot.resumeAllowed(),
                snapshot.blocksBroken(),
                snapshot.blocksPlaced(),
                snapshot.printedAt()
            );
            return true;
        } catch (IOException | GeneralSecurityException e) {
            statsCacheDebug("save failed reason={} session={} message={}",
                reason,
                shortSessionId(snapshot.sessionId()),
                e.getMessage()
            );
            return false;
        }
    }

    private boolean closeAndRetireStatsSession(StatsArtifactSnapshot snapshot, String reason) {
        if (snapshot == null) return true;

        boolean deletedCanonical = deleteStatsArtifact(resolveCanonicalStatsArtifactPath(), reason + "-canonical-delete");
        boolean deletedShadow = deleteStatsArtifact(resolveShadowStatsArtifactPath(), reason + "-shadow-delete");

        if (!deletedCanonical || !deletedShadow) {
            statsCacheDebug("retire failed session={} reason={}; canonical/shadow artifacts were not fully removed, leaving finalization record intact.",
                shortSessionId(snapshot.sessionId()),
                reason
            );
            return false;
        }

        boolean deletedFinalization = deleteStatsArtifact(resolveFinalizationRecordPath(), reason + "-finalization-delete");
        if (!deletedFinalization) {
            statsCacheDebug("retire incomplete session={} reason={}; finalization record remains for safe recovery.",
                shortSessionId(snapshot.sessionId()),
                reason
            );
            return false;
        }

        if (statsCacheSnapshot != null && Objects.equals(statsCacheSnapshot.sessionId(), snapshot.sessionId())) statsCacheSnapshot = null;
        loadedStatsArtifactIdentity = null;
        return true;
    }

    private boolean deleteStatsArtifact(Path path, String reason) {
        try {
            if (Files.exists(path)) Files.delete(path);
            statsCacheDebug("deleted reason={} path={}", reason, path);
            return true;
        } catch (IOException e) {
            statsCacheDebug("delete failed reason={} path={} message={}", reason, path, e.getMessage());
            return false;
        }
    }

    private StatsArtifactLoadResult loadStatsArtifact(Path path, StatsArtifactKind kind) {
        if (!Files.exists(path)) return new StatsArtifactLoadResult(null, false, false);

        List<String> lines;
        try {
            lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            statsCacheDebug("transient read failure kind={} path={} message={}", kind, path, e.getMessage());
            return new StatsArtifactLoadResult(null, true, false);
        }

        if (lines.isEmpty() || !STATS_ARTIFACT_MAGIC.equals(lines.get(0).trim())) {
            quarantineStatsArtifact(path, kind, "invalid-magic", null);
            return new StatsArtifactLoadResult(null, false, false);
        }

        String versionValue = null;
        String nonceValue = null;
        String cipherValue = null;
        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line == null) continue;
            line = line.trim();
            if (line.isEmpty()) continue;
            String[] parts = line.split("\\|", 2);
            if (parts.length != 2) continue;
            switch (parts[0]) {
                case "version" -> versionValue = parts[1];
                case "nonce" -> nonceValue = parts[1];
                case "ciphertext" -> cipherValue = parts[1];
                default -> { }
            }
        }

        int version = parseIntSafe(versionValue, -1);
        if (version != STATS_ARTIFACT_VERSION) {
            statsCacheDebug("unsupported artifact version kind={} path={} version={}", kind, path, version);
            return new StatsArtifactLoadResult(null, false, true);
        }

        try {
            StatsArtifactSnapshot snapshot = parseStatsArtifactPayload(kind, decryptStatsArtifactPayload(nonceValue, cipherValue));
            if (!validateStatsArtifactSnapshot(snapshot)) {
                quarantineStatsArtifact(path, kind, "invalid-payload", null);
                return new StatsArtifactLoadResult(null, false, false);
            }
            return new StatsArtifactLoadResult(snapshot, false, false);
        } catch (IOException e) {
            statsCacheDebug("transient decode failure kind={} path={} message={}", kind, path, e.getMessage());
            return new StatsArtifactLoadResult(null, true, false);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            quarantineStatsArtifact(path, kind, "decrypt-failed", e);
            return new StatsArtifactLoadResult(null, false, false);
        }
    }

    private boolean validateStatsArtifactSnapshot(StatsArtifactSnapshot snapshot) {
        if (snapshot == null) return false;
        if (snapshot.sessionId() == null || snapshot.sessionId().isBlank()) return false;
        if (snapshot.generation() <= 0L) return false;
        if (snapshot.state() == StatsSessionState.OPEN && !snapshot.resumeAllowed()) return false;
        return snapshot.state() == StatsSessionState.OPEN || !snapshot.resumeAllowed();
    }

    private void writeStatsArtifact(Path path, StatsArtifactSnapshot snapshot) throws IOException, GeneralSecurityException {
        Path parent = path.getParent();
        if (parent != null) Files.createDirectories(parent);

        String payload = serializeStatsArtifactPayload(snapshot);
        byte[] nonce = new byte[STATS_GCM_NONCE_BYTES];
        STATS_RANDOM.nextBytes(nonce);
        byte[] ciphertext = encryptStatsArtifactPayload(payload, nonce);

        StringBuilder out = new StringBuilder(1024);
        out.append(STATS_ARTIFACT_MAGIC).append('\n');
        out.append("version|").append(STATS_ARTIFACT_VERSION).append('\n');
        out.append("nonce|").append(Base64.getEncoder().encodeToString(nonce)).append('\n');
        out.append("ciphertext|").append(Base64.getEncoder().encodeToString(ciphertext)).append('\n');

        writeBytesAtomically(path, out.toString().getBytes(StandardCharsets.UTF_8));
    }

    private String serializeStatsArtifactPayload(StatsArtifactSnapshot snapshot) {
        StringBuilder out = new StringBuilder(1024);
        out.append("meta|kind|").append(snapshot.kind().name()).append('\n');
        out.append("meta|sessionId|").append(snapshot.sessionId()).append('\n');
        out.append("meta|generation|").append(snapshot.generation()).append('\n');
        out.append("meta|state|").append(snapshot.state().name()).append('\n');
        out.append("meta|resumeAllowed|").append(snapshot.resumeAllowed()).append('\n');
        out.append("meta|workingDirection|").append(snapshot.workingDirectionName() == null ? "" : snapshot.workingDirectionName()).append('\n');
        out.append("meta|startX|").append(snapshot.startX()).append('\n');
        out.append("meta|startY|").append(snapshot.startY()).append('\n');
        out.append("meta|startZ|").append(snapshot.startZ()).append('\n');
        out.append("meta|blocksBroken|").append(snapshot.blocksBroken()).append('\n');
        out.append("meta|blocksPlaced|").append(snapshot.blocksPlaced()).append('\n');
        out.append("meta|displayInfo|").append(snapshot.displayInfo()).append('\n');
        out.append("meta|lastCheckpointAt|").append(snapshot.lastCheckpointAt()).append('\n');
        out.append("meta|printedAt|").append(snapshot.printedAt()).append('\n');
        out.append("meta|printedToChat|").append(snapshot.printedToChat()).append('\n');
        out.append("meta|webhookSendCommitted|").append(snapshot.webhookSendCommitted()).append('\n');
        out.append("meta|apiSendCommitted|").append(snapshot.apiSendCommitted()).append('\n');
        out.append("meta|finalizationReason|").append(snapshot.finalizationReason() == null ? "" : snapshot.finalizationReason()).append('\n');
        out.append("meta|reconnectCycleId|").append(snapshot.reconnectCycleId()).append('\n');
        ReconnectBaselinePayload reconnectPayload = snapshot.reconnectBaselinePayload();
        out.append("meta|reconnectBaselinePresent|").append(reconnectPayload != null).append('\n');
        if (reconnectPayload != null) {
            out.append("meta|reconnectWorkingDirection|").append(reconnectPayload.workingDirectionName() == null ? "" : reconnectPayload.workingDirectionName()).append('\n');
            out.append("meta|reconnectSpeedModeName|").append(reconnectPayload.speedModeName() == null ? "" : reconnectPayload.speedModeName()).append('\n');
            out.append("meta|reconnectVanillaSpeed|").append(reconnectPayload.vanillaSpeed()).append('\n');
            out.append("meta|reconnectNcpSpeed|").append(reconnectPayload.ncpSpeed()).append('\n');
            out.append("meta|reconnectNcpSpeedLimit|").append(reconnectPayload.ncpSpeedLimit()).append('\n');
            out.append("meta|reconnectSpeedTimer|").append(reconnectPayload.timer()).append('\n');
            out.append("meta|reconnectSpeedInLiquids|").append(reconnectPayload.inLiquids()).append('\n');
            out.append("meta|reconnectSpeedWhenSneaking|").append(reconnectPayload.whenSneaking()).append('\n');
            out.append("meta|reconnectSpeedVanillaOnGround|").append(reconnectPayload.vanillaOnGround()).append('\n');
            out.append("meta|reconnectSpeedWasActive|").append(reconnectPayload.speedWasActive()).append('\n');
            out.append("meta|reconnectTimerWasActive|").append(reconnectPayload.timerWasActive()).append('\n');
            out.append("meta|reconnectTimerEffectiveMultiplier|").append(reconnectPayload.timerEffectiveMultiplier()).append('\n');
            out.append("meta|reconnectTimerOverrideActive|").append(reconnectPayload.timerOverrideActive()).append('\n');
            out.append("meta|reconnectTimerOverrideValue|").append(reconnectPayload.timerOverrideValue()).append('\n');
        }
        return out.toString();
    }

    private byte[] encryptStatsArtifactPayload(String payload, byte[] nonce) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, deriveStatsArtifactKey(), new GCMParameterSpec(STATS_GCM_TAG_BITS, nonce));
        return cipher.doFinal(payload.getBytes(StandardCharsets.UTF_8));
    }

    private String decryptStatsArtifactPayload(String nonceValue, String cipherValue) throws IOException, GeneralSecurityException {
        if (nonceValue == null || cipherValue == null) throw new IOException("missing encrypted fields");

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, deriveStatsArtifactKey(), new GCMParameterSpec(STATS_GCM_TAG_BITS, Base64.getDecoder().decode(nonceValue)));
        return new String(cipher.doFinal(Base64.getDecoder().decode(cipherValue)), StandardCharsets.UTF_8);
    }

    private SecretKeySpec deriveStatsArtifactKey() throws GeneralSecurityException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] keyBytes = digest.digest(getPassword().getBytes(StandardCharsets.UTF_8));
        return new SecretKeySpec(keyBytes, "AES");
    }

    private StatsArtifactSnapshot parseStatsArtifactPayload(StatsArtifactKind kind, String payload) {
        HashMap<String, String> meta = new HashMap<>();
        for (String line : payload.split("\n")) {
            if (line == null) continue;
            line = line.trim();
            if (line.isEmpty()) continue;
            String[] parts = line.split("\\|", 3);
            if (parts.length < 3 || !"meta".equals(parts[0])) continue;
            meta.put(parts[1], parts[2]);
        }

        StatsSessionState state = parseStatsSessionState(meta.get("state"));
        if (state == null) throw new IllegalArgumentException("invalid-state");

        ReconnectBaselinePayload reconnectPayload = parseStatsArtifactReconnectBaselinePayload(meta);
        long reconnectCycleId = reconnectPayload == null ? 0L : parseLongSafe(meta.get("reconnectCycleId"), 0L);

        return new StatsArtifactSnapshot(
            kind,
            meta.getOrDefault("sessionId", ""),
            parseLongSafe(meta.get("generation"), 0L),
            state,
            Boolean.parseBoolean(meta.getOrDefault("resumeAllowed", "false")),
            meta.getOrDefault("workingDirection", ""),
            parseDoubleSafe(meta.get("startX"), 0.0),
            parseDoubleSafe(meta.get("startY"), 0.0),
            parseDoubleSafe(meta.get("startZ"), 0.0),
            parseIntSafe(meta.get("blocksBroken"), 0),
            parseIntSafe(meta.get("blocksPlaced"), 0),
            Boolean.parseBoolean(meta.getOrDefault("displayInfo", "true")),
            parseLongSafe(meta.get("lastCheckpointAt"), 0L),
            parseLongSafe(meta.get("printedAt"), 0L),
            Boolean.parseBoolean(meta.getOrDefault("printedToChat", "false")),
            Boolean.parseBoolean(meta.getOrDefault("webhookSendCommitted", "false")),
            Boolean.parseBoolean(meta.getOrDefault("apiSendCommitted", "false")),
            meta.getOrDefault("finalizationReason", ""),
            reconnectCycleId,
            reconnectPayload
        );
    }

    private ReconnectBaselinePayload parseStatsArtifactReconnectBaselinePayload(HashMap<String, String> meta) {
        if (!Boolean.parseBoolean(meta.getOrDefault("reconnectBaselinePresent", "false"))) return null;
        if (!hasReconnectBaselineMeta(meta)) return null;

        return new ReconnectBaselinePayload(
            meta.getOrDefault("reconnectWorkingDirection", meta.getOrDefault("workingDirection", "")),
            meta.getOrDefault("reconnectSpeedModeName", ""),
            parseDoubleSafe(meta.get("reconnectVanillaSpeed"), 0.0),
            parseDoubleSafe(meta.get("reconnectNcpSpeed"), 0.0),
            Boolean.parseBoolean(meta.getOrDefault("reconnectNcpSpeedLimit", "false")),
            parseDoubleSafe(meta.get("reconnectSpeedTimer"), 0.0),
            Boolean.parseBoolean(meta.getOrDefault("reconnectSpeedInLiquids", "false")),
            Boolean.parseBoolean(meta.getOrDefault("reconnectSpeedWhenSneaking", "false")),
            Boolean.parseBoolean(meta.getOrDefault("reconnectSpeedVanillaOnGround", "false")),
            Boolean.parseBoolean(meta.getOrDefault("reconnectSpeedWasActive", "false")),
            Boolean.parseBoolean(meta.getOrDefault("reconnectTimerWasActive", "false")),
            parseDoubleSafe(meta.get("reconnectTimerEffectiveMultiplier"), Timer.OFF),
            Boolean.parseBoolean(meta.getOrDefault("reconnectTimerOverrideActive", "false")),
            parseDoubleSafe(meta.get("reconnectTimerOverrideValue"), Timer.OFF)
        );
    }

    private boolean hasReconnectBaselineMeta(HashMap<String, String> meta) {
        return meta.containsKey("reconnectCycleId")
            && meta.containsKey("reconnectWorkingDirection")
            && meta.containsKey("reconnectSpeedModeName")
            && meta.containsKey("reconnectVanillaSpeed")
            && meta.containsKey("reconnectNcpSpeed")
            && meta.containsKey("reconnectNcpSpeedLimit")
            && meta.containsKey("reconnectSpeedTimer")
            && meta.containsKey("reconnectSpeedInLiquids")
            && meta.containsKey("reconnectSpeedWhenSneaking")
            && meta.containsKey("reconnectSpeedVanillaOnGround")
            && meta.containsKey("reconnectSpeedWasActive")
            && meta.containsKey("reconnectTimerWasActive")
            && meta.containsKey("reconnectTimerEffectiveMultiplier")
            && meta.containsKey("reconnectTimerOverrideActive")
            && meta.containsKey("reconnectTimerOverrideValue");
    }

    private void writeBytesAtomically(Path path, byte[] bytes) throws IOException {
        Path tmp = path.resolveSibling(path.getFileName().toString() + ".tmp");
        try (FileChannel channel = FileChannel.open(tmp, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
            channel.write(ByteBuffer.wrap(bytes));
            channel.force(true);
        }

        try {
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private RetiredStatsReportSnapshot createRetiredStatsReportSnapshot(StatsArtifactSnapshot snapshot) {
        if (snapshot == null) return null;
        return new RetiredStatsReportSnapshot(
            snapshot.sessionId(),
            snapshot.generation(),
            new Vec3d(snapshot.startX(), snapshot.startY(), snapshot.startZ()),
            snapshot.blocksBroken(),
            snapshot.blocksPlaced()
        );
    }

    private StatsArtifactSnapshot createFinalizationRecord(String reason) {
        if (!hasActiveInMemoryStatsSession()) return null;
        return new StatsArtifactSnapshot(
            StatsArtifactKind.FINALIZATION,
            activeStatsSessionId,
            nextStatsGeneration(),
            StatsSessionState.PENDING_PRINT,
            false,
            dir == null ? "" : dir.name(),
            start.x,
            start.y,
            start.z,
            blocksBroken,
            blocksPlaced,
            displayInfo,
            System.currentTimeMillis(),
            0L,
            false,
            false,
            false,
            reason == null ? "" : reason,
            reconnectBaselineLease != null && reconnectBaselineLease.state() == ReconnectBaselineLeaseState.CAPTURED ? reconnectBaselineLease.generation() : 0L,
            reconnectBaselineLease != null && reconnectBaselineLease.state() == ReconnectBaselineLeaseState.CAPTURED ? reconnectBaselineLease.payload() : null
        );
    }

    private HorizontalDirection parseWorkingDirectionName(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return HorizontalDirection.valueOf(value.trim());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private StatsArtifactSnapshot persistFinalizationRecord(StatsArtifactSnapshot snapshot, String reason) {
        if (snapshot == null) return null;
        boolean previousFinalizing = statsSessionTerminalOrFinalizing;
        statsSessionTerminalOrFinalizing = true;
        if (!persistStatsArtifactSnapshot(snapshot, reason)) {
            statsSessionTerminalOrFinalizing = previousFinalizing;
            return null;
        }
        return snapshot;
    }

    private StatsArtifactSnapshot updateFinalizationRecord(
        StatsArtifactSnapshot snapshot,
        long printedAt,
        boolean printedToChat,
        boolean webhookCommitted,
        boolean apiCommitted,
        String reason
    ) {
        if (snapshot == null) return null;
        StatsArtifactSnapshot updated = copyStatsArtifact(
            snapshot,
            StatsArtifactKind.FINALIZATION,
            StatsSessionState.PENDING_PRINT,
            false,
            printedAt,
            printedToChat,
            webhookCommitted,
            apiCommitted,
            snapshot.finalizationReason(),
            nextStatsGenerationAfter(snapshot.generation())
        );
        return persistFinalizationRecord(updated, reason) != null ? updated : null;
    }

    private boolean completeFinalizationRecord(StatsArtifactSnapshot snapshot, String reason, boolean allowPrinting) {
        if (snapshot == null) return true;

        StatsArtifactSnapshot working = snapshot;

        RetiredStatsReportSnapshot report = createRetiredStatsReportSnapshot(working);
        if (report == null) return false;

        logFinalStatsRecoverySnapshot(working, report, reason + "-finalization-ready");

        working = commitAndSendFinalExternalStats(working, report, reason);
        if (working == null) return false;

        if (!allowPrinting || working.printedToChat()) flushPendingWebhookStats(null);

        if (allowPrinting && !working.printedToChat()) {
            long captureToken = beginStatsProofScreenshotCaptureIfEnabled(working.sessionId());
            boolean printed = false;
            try {
                printed = tryPrintStatsToChat(report, reason);
            } finally {
                if (!printed) finishStatsScreenshotCapture(captureToken);
            }
            if (!printed) {
                flushPendingWebhookStats(null);
                logFinalStatsRecoverySnapshot(working, report, reason + "-print-failed");
                return false;
            }
            if (captureToken > 0L) {
                try {
                    scheduleStatsProofScreenshot(working.sessionId(), reason, captureToken);
                } catch (RuntimeException | Error e) {
                    finishStatsScreenshotCapture(captureToken);
                    flushPendingWebhookStats(null);
                    THMAddon.LOG.warn("Failed to schedule HighwayBuilder stats screenshot for session {}.", shortSessionId(working.sessionId()), e);
                }
            } else {
                flushPendingWebhookStats(null);
            }
            working = updateFinalizationRecord(working, System.currentTimeMillis(), true, working.webhookSendCommitted(), working.apiSendCommitted(), reason + "-printed");
            if (working == null) return false;
            logFinalStatsRecoverySnapshot(working, report, reason + "-printed");
        }

        if (!closeAndRetireStatsSession(working, reason)) return false;

        retiredStatsReportSnapshot = report;
        markArtifactConsumed(snapshot);
        clearActiveInMemoryStatsSession();
        return true;
    }

    private StatsArtifactSnapshot commitAndSendFinalExternalStats(StatsArtifactSnapshot snapshot, RetiredStatsReportSnapshot report, String reason) {
        if (snapshot == null || report == null || mc == null || mc.player == null) return snapshot;

        StatsArtifactSnapshot working = snapshot;

        if (sendStatisticsWebhhok.get() && !working.webhookSendCommitted()) {
            String webhookUrl = resolveWebhookUrl(encryptedWebhook.get());
            if (mc.player.isCreative()) {
                // Silent on purpose — see enforceCreativeStatisticsGuard().
                logExternalStatsDecision(working, report, reason, "webhook", "skipped-creative", 0.0);
            } else if (webhookUrl != null) {
                double distance = PlayerUtils.distanceTo(report.startPos());
                if (distance > 1) {
                    StatsArtifactSnapshot committed = updateFinalizationRecord(
                        working,
                        working.printedAt(),
                        working.printedToChat(),
                        true,
                        working.apiSendCommitted(),
                        reason + "-webhook-commit"
                    );
                    if (committed != null) {
                        String playerName = mc.player.getName().getLiteralString();
                        String statsMessage = String.format("Player: %s , Distance: %.0f , Blocks broken: %d , Blocks placed: %d",
                            playerName,
                            distance,
                            report.blocksBroken(),
                            report.blocksPlaced()
                        );
                        if (webhookContent.get() == WebhookContent.Text) {
                            APIUtils.sendToWebhook(webhookUrl, statsMessage);
                        } else {
                            // Park it — the screenshot doesn't exist yet (it's taken after the chat
                            // print). takeStatsProofScreenshot() flushes it with the file attached;
                            // flushPendingWebhookStats() is the text-only fallback if none is taken.
                            pendingWebhookStats = new PendingWebhookStats(
                                webhookUrl,
                                webhookContent.get() == WebhookContent.Image ? "" : statsMessage
                            );
                        }
                        working = committed;
                        logExternalStatsDecision(working, report, reason, "webhook", "queued", distance);
                    }
                } else {
                    warning("Statistics NOT sent to webhook! Distance too small: (highlight)%.0f", distance);
                    logExternalStatsDecision(working, report, reason, "webhook", "skipped-distance-too-small", distance);
                }
            } else {
                logExternalStatsDecision(working, report, reason, "webhook", "skipped-invalid-url", 0.0);
            }
        }

        if (sendStatisticsapi.get() && !working.apiSendCommitted()) {
            double distance = PlayerUtils.distanceTo(report.startPos());
            if (distance > 1) {
                String playerName = mc.player.getName().getLiteralString();
                ThmMembers.Member selfMember = ThmMembers.getMemberByMcName(playerName);
                double distLimit = ThmMembers.getDistanceLimit(selfMember);
                if (distance < distLimit) {
                    if (report.blocksPlaced() < 300 && report.blocksBroken() < 1000) {
                        warning("Repair detected. Use the /calculate repair command to calculate the distance.");
                        logExternalStatsDecision(working, report, reason, "api", "skipped-repair-detected", distance);
                    } else if (mc.player.isCreative()) {
                        // Silent on purpose — see enforceCreativeStatisticsGuard().
                        logExternalStatsDecision(working, report, reason, "api", "skipped-creative", distance);
                    } else if (isNot6B6T()) {
                        warning("API not sent. You are not on 6B6T");
                        logExternalStatsDecision(working, report, reason, "api", "skipped-not-6b6t", distance);
                    } else if (!THMSystem.get().hasApiToken()) {
                        warning("API not sent. No valid API token set - get one from the Discord bot's player panel.");
                        logExternalStatsDecision(working, report, reason, "api", "skipped-missing-token", distance);
                    } else {
                        StatsArtifactSnapshot committed = updateFinalizationRecord(
                            working,
                            working.printedAt(),
                            working.printedToChat(),
                            working.webhookSendCommitted(),
                            true,
                            reason + "-api-commit"
                        );
                        if (committed != null) {
                            String server = mc.getCurrentServerEntry() != null ? mc.getCurrentServerEntry().address : "singleplayer";
                            String statsMessageapi = String.format("%s:%s:%s:%.0f:%s:%s:%s:%s:%s",
                                THMSystem.get().getApiToken(),
                                playerName,
                                server,
                                distance,
                                report.blocksBroken(),
                                report.blocksPlaced(),
                                dir,
                                generateTimestamp(),
                                isOnMainHighway()
                            );
                            sendStatistics(statsMessageapi);
                            working = committed;
                            logExternalStatsDecision(working, report, reason, "api", "queued", distance);
                        }
                    }
                } else {
                    warning("Statistics NOT sent to Api! Please Calculate the real Distance using the /calculate command in proof-of-work");
                    logExternalStatsDecision(working, report, reason, "api", "skipped-distance-limit", distance);
                }
            } else {
                warning("Statistics NOT sent to Api! Distance too small: (highlight)%.0f", distance);
                logExternalStatsDecision(working, report, reason, "api", "skipped-distance-too-small", distance);
            }
        }

        return working;
    }

    private long beginStatsProofScreenshotCaptureIfEnabled(String sessionId) {
        if (!autoScreenshotStatistics.get()) return 0L;
        if (sessionId == null || sessionId.isBlank()) return 0L;
        try {
            return StatsScreenshotChatGuard.getInstance().beginCapture();
        } catch (RuntimeException | Error e) {
            THMAddon.LOG.warn("Failed to start HighwayBuilder stats screenshot chat guard for session {}.", shortSessionId(sessionId), e);
            return 0L;
        }
    }

    private void scheduleStatsProofScreenshot(String sessionId, String reason, long captureToken) {
        scheduleStatsScreenshot(sessionId, reason, StatsScreenshotSurface.CHAT, captureToken);
    }

    public void scheduleRetiredStatsDisconnectScreenshot(String sessionId, String reason) {
        if (!autoScreenshotStatistics.get()) return;
        if (sessionId == null || sessionId.isBlank()) return;
        scheduleStatsScreenshot(sessionId, reason, StatsScreenshotSurface.DISCONNECT, 0L);
    }

    private void scheduleStatsScreenshot(String sessionId, String reason, StatsScreenshotSurface surface, long captureToken) {
        long sequence = statsScreenshotSequence.incrementAndGet();
        try {
            Thread thread = new Thread(() -> {
                try {
                    Thread.sleep(STATS_SCREENSHOT_DELAY_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    finishStatsScreenshotCapture(captureToken);
                    flushPendingWebhookStats(null);
                    statsCacheDebug("screenshot scheduling interrupted reason={} session={} surface={} sequence={}",
                        reason,
                        shortSessionId(sessionId),
                        surface.fileNamePart,
                        sequence
                    );
                    return;
                }

                try {
                    mc.execute(() -> {
                        try {
                            takeStatsProofScreenshot(sessionId, reason, surface, sequence, captureToken);
                        } catch (RuntimeException | Error e) {
                            finishStatsScreenshotCapture(captureToken);
                            flushPendingWebhookStats(null);
                            statsCacheDebug("screenshot capture failed reason={} session={} surface={} sequence={} error={}",
                                reason,
                                shortSessionId(sessionId),
                                surface.fileNamePart,
                                sequence,
                                e.getClass().getSimpleName()
                            );
                        }
                    });
                } catch (RuntimeException | Error e) {
                    finishStatsScreenshotCapture(captureToken);
                    flushPendingWebhookStats(null);
                    statsCacheDebug("screenshot client scheduling failed reason={} session={} surface={} sequence={} error={}",
                        reason,
                        shortSessionId(sessionId),
                        surface.fileNamePart,
                        sequence,
                        e.getClass().getSimpleName()
                    );
                }
            }, "thm-highwaybuilder-" + surface.fileNamePart + "-stats-screenshot-" + sequence);
            thread.setDaemon(true);
            thread.start();
        } catch (RuntimeException | Error e) {
            finishStatsScreenshotCapture(captureToken);
            flushPendingWebhookStats(null);
            statsCacheDebug("screenshot thread start failed reason={} session={} surface={} sequence={} error={}",
                reason,
                shortSessionId(sessionId),
                surface.fileNamePart,
                sequence,
                e.getClass().getSimpleName()
            );
        }
    }

    private void takeStatsProofScreenshot(String sessionId, String reason, StatsScreenshotSurface surface, long sequence, long captureToken) {
        if (mc == null || mc.getFramebuffer() == null) {
            finishStatsScreenshotCapture(captureToken);
            flushPendingWebhookStats(null);
            return;
        }

        String fileName = buildStatsScreenshotFileName(sessionId, surface, sequence);
        try {
            ScreenshotRecorder.saveScreenshot(mc.runDirectory, fileName, mc.getFramebuffer(), 1, message -> {
                try {
                    mc.execute(() -> {
                        finishStatsScreenshotCapture(captureToken);
                        // The file is fully written by the time the message callback runs.
                        if (surface == StatsScreenshotSurface.CHAT) {
                            flushPendingWebhookStats(mc.runDirectory.toPath().resolve("screenshots").resolve(fileName));
                        }
                        info(message.getString());
                        statsCacheDebug("screenshot completed reason={} session={} surface={} sequence={} file={}",
                            reason,
                            shortSessionId(sessionId),
                            surface.fileNamePart,
                            sequence,
                            fileName
                        );
                    });
                } catch (RuntimeException | Error e) {
                    finishStatsScreenshotCapture(captureToken);
                    flushPendingWebhookStats(null);
                    statsCacheDebug("screenshot callback scheduling failed reason={} session={} surface={} sequence={} error={}",
                        reason,
                        shortSessionId(sessionId),
                        surface.fileNamePart,
                        sequence,
                        e.getClass().getSimpleName()
                    );
                }
            });
        } catch (RuntimeException | Error e) {
            finishStatsScreenshotCapture(captureToken);
            flushPendingWebhookStats(null);
            throw e;
        }
        statsCacheDebug("screenshot requested reason={} session={} surface={} sequence={} file={}",
            reason,
            shortSessionId(sessionId),
            surface.fileNamePart,
            sequence,
            fileName
        );
    }

    /**
     * Sends the parked webhook stats, with {@code file} attached when there is one.
     * Idempotent — whoever gets there first (screenshot done, or any path where no screenshot
     * happens) sends; the rest see null and do nothing.
     */
    private void flushPendingWebhookStats(Path file) {
        PendingWebhookStats pending = pendingWebhookStats;
        if (pending == null) return;
        pendingWebhookStats = null;

        if (file == null && pending.message().isEmpty()) {
            // Image-only mode with no screenshot to send — nothing to post.
            statsCacheDebug("webhook attachment flush skipped: image-only with no screenshot");
            return;
        }
        THMUtils.sendToWebhookWithFile(pending.url(), pending.message(), file);
    }

    private void finishStatsScreenshotCapture(long captureToken) {
        if (captureToken <= 0L) return;
        try {
            StatsScreenshotChatGuard.getInstance().finishCapture(captureToken);
        } catch (RuntimeException | Error e) {
            THMAddon.LOG.warn("Failed to release HighwayBuilder stats screenshot chat guard token {}.", captureToken, e);
        }
    }

    private String buildStatsScreenshotFileName(String sessionId, StatsScreenshotSurface surface, long sequence) {
        return "thm-highwaybuilder-session-"
            + STATS_SCREENSHOT_TIME_FORMAT.format(Instant.now())
            + "-"
            + shortSessionId(sessionId)
            + "-"
            + surface.fileNamePart
            + "-"
            + System.currentTimeMillis()
            + "-"
            + sequence
            + ".png";
    }

    private String shortSessionId(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) return "unknown";
        return sessionId.length() <= 8 ? sessionId : sessionId.substring(0, 8);
    }

    private void quarantineStatsArtifact(Path path, StatsArtifactKind kind, String reason, Exception error) {
        if (statsCacheSnapshot != null && statsCacheSnapshot.kind() == kind) statsCacheSnapshot = null;

        Path quarantine = path.resolveSibling(path.getFileName() + ".corrupt-" + System.currentTimeMillis());
        try {
            Files.move(path, quarantine, StandardCopyOption.REPLACE_EXISTING);
            statsCacheDebug("quarantined kind={} reason={} path={} quarantine={} error={}",
                kind,
                reason,
                path,
                quarantine,
                error == null ? "none" : error.getClass().getSimpleName()
            );
        } catch (IOException moveError) {
            try {
                Files.deleteIfExists(path);
            } catch (IOException ignored) {
                // ignore delete fallback failure after quarantine failure
            }
            statsCacheDebug("quarantine failed kind={} reason={} path={} message={} error={}",
                kind,
                reason,
                path,
                moveError.getMessage(),
                error == null ? "none" : error.getClass().getSimpleName()
            );
        }
    }

    private Path resolveStatsArtifactDirectory() {
        return MeteorClient.FOLDER.toPath().resolve("thm");
    }

    private Path resolveCanonicalStatsArtifactPath() {
        return resolveStatsArtifactDirectory().resolve(STATS_CANONICAL_FILE_NAME);
    }

    private Path resolveFinalizationRecordPath() {
        return resolveStatsArtifactDirectory().resolve(STATS_FINALIZATION_FILE_NAME);
    }

    private Path resolveShadowStatsArtifactPath() {
        return resolveStatsArtifactDirectory().resolve(STATS_SHADOW_FILE_NAME);
    }

    private Path resolveThmSpeedSnapshotPath() {
        return resolveStatsArtifactDirectory().resolve(THM_SPEED_SNAPSHOT_FILE_NAME);
    }

    private Path resolveStatsArtifactPath(StatsArtifactKind kind) {
        return switch (kind) {
            case CANONICAL -> resolveCanonicalStatsArtifactPath();
            case FINALIZATION -> resolveFinalizationRecordPath();
            case SHADOW -> resolveShadowStatsArtifactPath();
        };
    }

    private StatsArtifactIdentity identityOf(StatsArtifactSnapshot snapshot) {
        return new StatsArtifactIdentity(snapshot.kind(), snapshot.sessionId(), snapshot.generation());
    }

    private void markArtifactConsumed(StatsArtifactSnapshot snapshot) {
        if (snapshot == null) return;
        consumedStatsArtifactKeys.add(identityOf(snapshot).key());
    }

    private StatsSessionState parseStatsSessionState(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return StatsSessionState.valueOf(value.trim());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private StatsDisplaySnapshot createStatsDisplaySnapshot(Vec3d startPos, int broken, int placed) {
        double distance = mc.player != null && startPos != null ? PlayerUtils.distanceTo(startPos) : 0.0;
        return new StatsDisplaySnapshot(distance, broken, placed);
    }

    private MutableText createStatsText(Vec3d statsStart, int statsBroken, int statsPlaced) {
        StatsDisplaySnapshot display = createStatsDisplaySnapshot(statsStart, statsBroken, statsPlaced);
        MutableText text = Text.literal(String.format("%sDistance: %s%.0f\n", Formatting.GRAY, Formatting.WHITE, display.distance()));
        text.append(String.format("%sBlocks broken: %s%d\n", Formatting.GRAY, Formatting.WHITE, display.blocksBroken()));
        text.append(String.format("%sBlocks placed: %s%d\n", Formatting.GRAY, Formatting.WHITE, display.blocksPlaced()));
        return text;
    }

    private MutableText getFinalizedStatsText() {
        if (retiredStatsReportSnapshot == null) return null;
        return createStatsText(
            retiredStatsReportSnapshot.startPos(),
            retiredStatsReportSnapshot.blocksBroken(),
            retiredStatsReportSnapshot.blocksPlaced()
        );
    }

    private void logFinalStatsRecoverySnapshot(StatsArtifactSnapshot snapshot, RetiredStatsReportSnapshot report, String reason) {
        if (snapshot == null || report == null) return;

        StatsDisplaySnapshot display = createStatsDisplaySnapshot(
            report.startPos(),
            report.blocksBroken(),
            report.blocksPlaced()
        );
        statsCacheDebug(
            "final-report reason={} session={} generation={} state={} resumeAllowed={} broken={} placed={} brokenPerBlock={} placedPerBlock={} distance={} printed={} webhookCommitted={} apiCommitted={} finalizationReason={}",
            reason,
            shortSessionId(snapshot.sessionId()),
            snapshot.generation(),
            snapshot.state(),
            snapshot.resumeAllowed(),
            report.blocksBroken(),
            report.blocksPlaced(),
            String.format(Locale.ROOT, "%.2f", display.brokenPerBlock()),
            String.format(Locale.ROOT, "%.2f", display.placedPerBlock()),
            String.format(Locale.ROOT, "%.0f", display.distance()),
            snapshot.printedToChat(),
            snapshot.webhookSendCommitted(),
            snapshot.apiSendCommitted(),
            snapshot.finalizationReason() == null ? "" : snapshot.finalizationReason()
        );
    }

    private void logExternalStatsDecision(
        StatsArtifactSnapshot snapshot,
        RetiredStatsReportSnapshot report,
        String reason,
        String target,
        String decision,
        double distance
    ) {
        if (snapshot == null || report == null) return;

        statsCacheDebug(
            "external-stats reason={} target={} decision={} session={} generation={} distance={} broken={} placed={} direction={} webhookCommitted={} apiCommitted={}",
            reason,
            target,
            decision,
            shortSessionId(snapshot.sessionId()),
            snapshot.generation(),
            String.format(Locale.ROOT, "%.0f", distance),
            report.blocksBroken(),
            report.blocksPlaced(),
            snapshot.workingDirectionName() == null || snapshot.workingDirectionName().isBlank() ? "unknown" : snapshot.workingDirectionName(),
            snapshot.webhookSendCommitted(),
            snapshot.apiSendCommitted()
        );
    }

    private boolean tryPrintStatsToChat(RetiredStatsReportSnapshot report, String reason) {
        if (report == null || report.startPos() == null || mc.player == null || mc.world == null) return false;
        if (!Utils.canUpdate()) return false;

        StatsDisplaySnapshot display = createStatsDisplaySnapshot(
            report.startPos(),
            report.blocksBroken(),
            report.blocksPlaced()
        );
        info("Distance: (highlight)%.0f", display.distance());
        info("Blocks broken: (highlight)%d", display.blocksBroken());
        info("Blocks placed: (highlight)%d", display.blocksPlaced());
        lastPrintedStatsSessionId = report.sessionId();
        statsCacheDebug("printed reason={} session={} broken={} placed={}",
            reason,
            shortSessionId(report.sessionId()),
            report.blocksBroken(),
            report.blocksPlaced()
        );
        return true;
    }

    private void recordBlockBroken() {
        blocksBroken++;
        statsSessionDirty = true;
    }

    private void recordConfirmedBrokenBlockForStats(BlockPos pos) {
        if (pos == null) return;
        if (consumePendingForwardBreakCredit(pos)) return;
        if (useForwardRowSchedulerMode()) return;
        if (!isLegacyCountableBrokenState(state)) return;

        recordBrokenForwardPosition(pos);
    }

    private void recordConfirmedBrokenBlockForStats(BlockPos pos, ForwardTaskType type) {
        if (pos == null || !isSchedulerCountableFrontMineTask(type)) return;
        cancelPendingForwardBreakCredit(pos);
        recordBrokenForwardPosition(pos);
    }

    private boolean recordBrokenForwardPosition(BlockPos pos) {
        BlockPos immutablePos = pos.toImmutable();
        if (countedBrokenForwardPositions.add(immutablePos)) {
            recordBlockBroken();
            return true;
        }
        return false;
    }

    private void recordPlacedBlockForStats(BlockPos pos) {
        if (pos == null) return;
        if (useForwardRowSchedulerMode()) return;
        if (!shouldCountLegacyPlacedPosition(state, pos)) return;

        recordPlacedForwardPosition(pos);
    }

    private void recordPlacedBlockForStats(BlockPos pos, ForwardTaskType type) {
        if (pos == null || !isSchedulerCountableFrontPlaceTask(type)) return;
        cancelPendingForwardBreakCredit(pos);
        recordPlacedForwardPosition(pos);
    }

    private boolean recordPlacedForwardPosition(BlockPos pos) {
        BlockPos immutablePos = pos.toImmutable();
        if (countedPlacedForwardPositions.add(immutablePos)) {
            blocksPlaced++;
            statsSessionDirty = true;
            return true;
        }
        return false;
    }

    private void statsDebug(String message, Object... args) {
        if (!statisticsDebugLog.get()) return;
        writeThmDebugLog(FORWARD_STATS_DEBUG_FILE_NAME, formatThmDebugLine(message, args));
    }

    private void statsCacheDebug(String message, Object... args) {
        String line = formatThmDebugLine("[highway-stats-cache] " + message.replace("{}", "%s"), args);
        writeThmDebugLog(FORWARD_STATS_DEBUG_FILE_NAME, line);
        THMAddon.LOG.info("[HighwayBuilder Stats Cache] {}", line.trim());
    }

    private String describeStatsDecisionReason(boolean eligible, boolean alreadyCounted, boolean countedNow) {
        if (countedNow) return "counted";
        if (!eligible) return "skipped-ineligible";
        if (alreadyCounted) return "skipped-duplicate";
        return "skipped-no-increment";
    }

    private void logSchedulerStatsDecision(String source, ForwardTaskType type, BlockPos pos, Block beforeBlock, Block afterBlock, boolean eligible, boolean alreadyCounted, boolean countedNow) {
        statsDebug(
            "%s type=%s pos=%s before=%s after=%s eligible=%s alreadyCounted=%s countedNow=%s reason=%s",
            source,
            type,
            formatBlockPos(pos),
            beforeBlock,
            afterBlock,
            eligible,
            alreadyCounted,
            countedNow,
            describeStatsDecisionReason(eligible, alreadyCounted, countedNow)
        );
    }

    private void logLegacyStatsDecision(String source, State legacyState, BlockPos pos, Block beforeBlock, Block afterBlock, boolean eligible, boolean alreadyCounted, boolean countedNow) {
        statsDebug(
            "%s state=%s pos=%s before=%s after=%s eligible=%s alreadyCounted=%s countedNow=%s reason=%s",
            source,
            stateName(legacyState),
            formatBlockPos(pos),
            beforeBlock,
            afterBlock,
            eligible,
            alreadyCounted,
            countedNow,
            describeStatsDecisionReason(eligible, alreadyCounted, countedNow)
        );
    }

    private int parseIntSafe(String value, int fallback) {
        if (value == null || value.isBlank()) return fallback;
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private long parseLongSafe(String value, long fallback) {
        if (value == null || value.isBlank()) return fallback;
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private String formatBlockPos(BlockPos pos) {
        if (pos == null) return "null";
        return "(" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + ")";
    }

    public static double getCenteredBlockCoordinate(double value) {
        return Math.floor(value) + 0.5;
    }

    public void snapPlayerToCurrentBlockCenter() {
        if (mc == null || mc.player == null) return;

        BlockPos target = mc.player.getBlockPos();
        mc.player.setVelocity(0, 0, 0);
        mc.player.setPosition(
            getBlockCenterCoordinate(target.getX()),
            mc.player.getY(),
            getBlockCenterCoordinate(target.getZ())
        );
    }

    private void setCenterTargetAndState(BlockPos target, State lastState) {
        pendingCenterTargetBlock = target == null ? null : target.toImmutable();
        clearCenterTeleportBlockedState();
        setState(State.Center, lastState);
    }

    private void clearCenterTeleportTarget() {
        activeCenterTargetBlock = null;
        pendingCenterTargetBlock = null;
        centerTeleportInvalidLogCooldownTicks = 0;
        clearCenterTeleportBlockedState();
    }

    private void clearCenterTeleportBlockedState() {
        centerTeleportBlockedForMonitor = false;
        centerTeleportBlockedTicks = 0;
        centerTeleportBlockedTarget = null;
        centerTeleportBlockedReason = "";
    }

    private void noteCenterTeleportBlocked(String reason, BlockPos target) {
        BlockPos immutableTarget = target == null ? null : target.toImmutable();
        String normalizedReason = reason == null || reason.isBlank() ? "unknown" : reason;
        boolean sameTarget = centerTeleportBlockedTarget == null
            ? immutableTarget == null
            : centerTeleportBlockedTarget.equals(immutableTarget);
        boolean sameReason = normalizedReason.equals(centerTeleportBlockedReason);

        if (!centerTeleportBlockedForMonitor || !sameTarget || !sameReason) {
            centerTeleportBlockedTicks = 0;
        }

        centerTeleportBlockedForMonitor = true;
        centerTeleportBlockedTarget = immutableTarget;
        centerTeleportBlockedReason = normalizedReason;
        if (centerTeleportBlockedTicks < Integer.MAX_VALUE) centerTeleportBlockedTicks++;
    }

    public boolean isCenterTeleportBlockedForMonitorRecovery() {
        return isActive()
            && state == State.Center
            && centerMode.get() == CenterMode.Teleport
            && centerTeleportBlockedForMonitor;
    }

    public int getCenterTeleportBlockedTicksForMonitorRecovery() {
        return isCenterTeleportBlockedForMonitorRecovery() ? centerTeleportBlockedTicks : 0;
    }

    public String getCenterTeleportBlockedReasonForMonitorRecovery() {
        return isCenterTeleportBlockedForMonitorRecovery() ? centerTeleportBlockedReason : "";
    }

    public BlockPos getCenterTeleportBlockedTargetForMonitorRecovery() {
        return isCenterTeleportBlockedForMonitorRecovery() && centerTeleportBlockedTarget != null
            ? centerTeleportBlockedTarget.toImmutable()
            : null;
    }

    private BlockPos resolveCenterTeleportTarget() {
        if (pendingCenterTargetBlock != null) {
            activeCenterTargetBlock = pendingCenterTargetBlock.toImmutable();
            pendingCenterTargetBlock = null;
        }

        if (activeCenterTargetBlock == null && mc != null && mc.player != null) {
            activeCenterTargetBlock = mc.player.getBlockPos().toImmutable();
        }

        return activeCenterTargetBlock;
    }

    private void stopCenterTeleportMotion() {
        if (input != null) input.stop();
        centerTeleportSlowdownActive = false;
        if (mc == null || mc.player == null) return;

        Vec3d velocity = mc.player.getVelocity();
        mc.player.setVelocity(0.0, velocity.y, 0.0);
    }

    private boolean tryTeleportToCenterTarget(String reason) {
        if (mc == null || mc.world == null || mc.player == null) return false;

        BlockPos target = resolveCenterTeleportTarget();
        if (target == null) {
            stopCenterTeleportMotion();
            noteCenterTeleportBlocked("missing target", null);
            logCenterTeleportBlocked("missing target", null);
            return false;
        }

        String invalidReason = getCenterTeleportInvalidReason(target);
        if (invalidReason != null) {
            stopCenterTeleportMotion();
            noteCenterTeleportBlocked(invalidReason, target);
            logCenterTeleportBlocked(invalidReason, target);
            return false;
        }

        clearCenterTeleportBlockedState();
        if (!isWithinCenterTeleportSnapRadius(target)) {
            approachCenterTeleportTarget(target, reason);
            return false;
        }

        stopCenterTeleportMotion();
        double targetX = getBlockCenterCoordinate(target.getX());
        double targetZ = getBlockCenterCoordinate(target.getZ());
        mc.player.setPosition(targetX, mc.player.getY(), targetZ);
        stopCenterTeleportMotion();

        if (restockDebugLog.get()) {
            restockDebug("Center teleport snapped to target %s (reason=%s, targetX=%.1f, targetZ=%.1f, last=%s).",
                formatBlockPos(target),
                reason,
                targetX,
                targetZ,
                stateName(lastState)
            );
        }
        return true;
    }

    private boolean isWithinCenterTeleportSnapRadius(BlockPos target) {
        return centerTeleportHorizontalDistance(target) <= CENTER_TELEPORT_SNAP_RADIUS;
    }

    private double centerTeleportHorizontalDistance(BlockPos target) {
        if (target == null || mc == null || mc.player == null) return Double.POSITIVE_INFINITY;

        double dx = mc.player.getX() - getBlockCenterCoordinate(target.getX());
        double dz = mc.player.getZ() - getBlockCenterCoordinate(target.getZ());
        return Math.hypot(dx, dz);
    }

    private void approachCenterTeleportTarget(BlockPos target, String reason) {
        if (target == null || mc == null || mc.player == null || input == null) return;

        double targetX = getBlockCenterCoordinate(target.getX());
        double targetZ = getBlockCenterCoordinate(target.getZ());
        double distance = centerTeleportHorizontalDistance(target);

        centerTeleportSlowdownActive = isThmSpeedControllerActive()
            && distance <= THM_CENTER_TELEPORT_SLOWDOWN_DISTANCE
            && distance > CENTER_TELEPORT_SNAP_RADIUS;
        mc.player.setYaw((float) Rotations.getYaw(new Vec3d(targetX, mc.player.getY(), targetZ)));
        input.setState(true, false, false, false, false, false, false);
        logCenterTeleportApproaching(target, distance, reason);
    }

    private String getCenterTeleportInvalidReason(BlockPos target) {
        if (target == null) return "missing target";
        if (mc == null || mc.world == null || mc.player == null) return "missing world/player";

        int currentFeetY = MathHelper.floor(mc.player.getY());
        if (target.getY() != currentFeetY) {
            return "target Y " + target.getY() + " does not match current feet Y " + currentFeetY;
        }

        double horizontalDistance = centerTeleportHorizontalDistance(target);
        if (horizontalDistance > CENTER_TELEPORT_MAX_HORIZONTAL_DISTANCE) {
            return String.format(Locale.ROOT, "target %.2f blocks away exceeds %.1f", horizontalDistance, CENTER_TELEPORT_MAX_HORIZONTAL_DISTANCE);
        }

        if (!isSafeCenterTeleportTarget(target)) return "target space is blocked or unsafe";
        return null;
    }

    private boolean isSafeCenterTeleportTarget(BlockPos target) {
        if (mc == null || mc.world == null || target == null) return false;

        BlockPos feetPos = target;
        BlockPos headPos = target.up();
        BlockPos floorPos = target.down();
        BlockState feetState = mc.world.getBlockState(feetPos);
        BlockState headState = mc.world.getBlockState(headPos);
        BlockState floorState = mc.world.getBlockState(floorPos);

        return isClearCenterTeleportSpace(feetState, feetPos)
            && isClearCenterTeleportSpace(headState, headPos)
            && floorState.isSolidBlock(mc.world, floorPos);
    }

    private boolean isClearCenterTeleportSpace(BlockState state, BlockPos pos) {
        if (mc == null || mc.world == null || state == null || pos == null) return false;
        return (state.isAir() || state.isReplaceable()) && state.getCollisionShape(mc.world, pos).isEmpty();
    }

    private void logCenterTeleportBlocked(String reason, BlockPos target) {
        if (centerTeleportInvalidLogCooldownTicks > 0) return;
        centerTeleportInvalidLogCooldownTicks = CENTER_TELEPORT_INVALID_LOG_COOLDOWN_TICKS;

        if (restockDebugLog.get()) {
            restockDebug("Center teleport waiting for HwyMonitor recovery: %s%s (last=%s).",
                reason,
                target == null ? "" : ", target=" + formatBlockPos(target),
                stateName(lastState)
            );
        }
    }

    private void logCenterTeleportApproaching(BlockPos target, double distance, String reason) {
        if (centerTeleportInvalidLogCooldownTicks > 0) return;
        centerTeleportInvalidLogCooldownTicks = CENTER_TELEPORT_INVALID_LOG_COOLDOWN_TICKS;

        if (restockDebugLog.get()) {
            restockDebug("Center teleport approaching target %s (reason=%s, distance=%.3f, snapRadius=%.2f, last=%s).",
                formatBlockPos(target),
                reason,
                distance,
                CENTER_TELEPORT_SNAP_RADIUS,
                stateName(lastState)
            );
        }
    }

    private void tickCenterTeleportLogCooldown() {
        if (centerTeleportInvalidLogCooldownTicks > 0) centerTeleportInvalidLogCooldownTicks--;
    }

    private boolean ensureCenteredBeforeBlockadePlacement(State placementState) {
        if (mc.player == null) return false;
        return ensureCenteredOnBlockBeforeEnclosurePlacement(
            placementState,
            mc.player.getBlockPos(),
            stateName(placementState) + "-placement"
        );
    }

    private boolean isCenteredOnBlock(BlockPos target, double tolerance) {
        if (mc.player == null || target == null) return false;

        double targetX = getBlockCenterCoordinate(target.getX());
        double targetZ = getBlockCenterCoordinate(target.getZ());
        return Math.abs(mc.player.getX() - targetX) <= tolerance
            && Math.abs(mc.player.getZ() - targetZ) <= tolerance;
    }

    private KitbotCenterWalkResult walkKitbotToBlockCenter(BlockPos target, String reason) {
        if (target == null) return KitbotCenterWalkResult.TimedOut;
        return walkKitbotToTarget(
            getBlockCenterCoordinate(target.getX()),
            getBlockCenterCoordinate(target.getZ()),
            BLOCKADE_CENTER_TOLERANCE,
            reason,
            "block center"
        );
    }

    private KitbotCenterWalkResult walkKitbotToTarget(double targetX, double targetZ, double tolerance, String reason, String label) {
        if (mc.player == null || input == null) return KitbotCenterWalkResult.Walking;

        double dx = targetX - mc.player.getX();
        double dz = targetZ - mc.player.getZ();
        double distance = Math.hypot(dx, dz);
        kitbotCenteringDistance = distance;

        if (distance <= tolerance) {
            if (restockDebugLog.get()) {
                restockDebug("KitbotOrder reached %s target=(%.3f, %.3f) distance=%.3f tolerance=%.3f reason=%s.",
                    label,
                    targetX,
                    targetZ,
                    distance,
                    tolerance,
                    reason
                );
            }
            clearKitbotCenteringRuntime("kitbot-walk-reached:" + reason);
            return KitbotCenterWalkResult.Reached;
        }

        boolean targetChanged = !kitbotCenteringActive
            || Math.abs(kitbotCenteringTargetX - targetX) > 0.0001
            || Math.abs(kitbotCenteringTargetZ - targetZ) > 0.0001
            || !Objects.equals(kitbotCenteringReason, reason);

        if (targetChanged) {
            kitbotCenteringActive = true;
            kitbotCenteringTicks = 0;
            kitbotCenteringTargetX = targetX;
            kitbotCenteringTargetZ = targetZ;
            kitbotCenteringReason = reason == null ? "" : reason;
            if (restockDebugLog.get()) {
                restockDebug("KitbotOrder walking to %s target=(%.3f, %.3f) distance=%.3f tolerance=%.3f slowdownDistance=%.3f reason=%s.",
                    label,
                    targetX,
                    targetZ,
                    distance,
                    tolerance,
                    KITBOT_ORDER_CENTER_SLOWDOWN_DISTANCE,
                    reason
                );
            }
        }

        kitbotCenteringTicks++;
        if (kitbotCenteringTicks >= KITBOT_ORDER_CENTER_TIMEOUT_TICKS) {
            if (restockDebugLog.get()) {
                restockDebug("KitbotOrder timed out walking to %s target=(%.3f, %.3f) distance=%.3f tolerance=%.3f ticks=%d reason=%s.",
                    label,
                    targetX,
                    targetZ,
                    distance,
                    tolerance,
                    kitbotCenteringTicks,
                    reason
                );
            }
            clearKitbotCenteringRuntime("kitbot-walk-timeout:" + reason);
            return KitbotCenterWalkResult.TimedOut;
        }

        Vec3d yawTarget = new Vec3d(targetX, mc.player.getY(), targetZ);
        mc.player.setYaw((float) Rotations.getYaw(yawTarget));
        input.setState(true, false, false, false, false, false, false);
        return KitbotCenterWalkResult.Walking;
    }

    private boolean ensureCenteredOnBlockBeforeEnclosurePlacement(State placementState, BlockPos target, String reason) {
        if (mc.player == null || target == null) return false;
        if (isCenteredOnBlock(target, BLOCKADE_CENTER_TOLERANCE)) return true;

        if (restockDebugLog.get()) {
            restockDebug("%s redirecting to Center before enclosure placement (reason=%s, x=%.6f, z=%.6f, target=%s, targetX=%.1f, targetZ=%.1f).",
                stateName(placementState),
                reason,
                mc.player.getX(),
                mc.player.getZ(),
                formatBlockPos(target),
                getBlockCenterCoordinate(target.getX()),
                getBlockCenterCoordinate(target.getZ())
            );
        }

        routeToCenterForEnclosurePlacement(placementState, target, reason);
        return false;
    }

    private void routeToCenterForEnclosurePlacement(State resumeState, BlockPos target, String reason) {
        if (target == null) return;
        if (resumeState == State.KitbotOrder) {
            if ("kitbot-normal-blockade-restore".equals(reason)) {
                kitbotOrderResumeAfterCenter = true;
                setCenterTargetAndState(target, resumeState);
                if (restockDebugLog.get()) {
                    restockDebug("KitbotOrder requested Center teleport target=%s resume=%s reason=%s.",
                        formatBlockPos(target),
                        stateName(resumeState),
                        reason
                    );
                }
                return;
            }

            if (restockDebugLog.get()) {
                restockDebug("KitbotOrder ignored Center route request target=%s reason=%s; KitBot restock uses walk-only centering.",
                    formatBlockPos(target),
                    reason
                );
            }
            return;
        }
        setCenterTargetAndState(target, resumeState);
        if (restockDebugLog.get()) {
            restockDebug("Enclosure placement requested Center target=%s resume=%s reason=%s.",
                formatBlockPos(target),
                stateName(resumeState),
                reason
            );
        }
    }

    private BlockPos enclosureCenterTargetForState(State placementState) {
        if (placementState == State.KitbotOrder && kitbotAnchorPos.getY() != 0) {
            return kitbotAnchorPos.toImmutable();
        }
        return mc.player == null ? null : mc.player.getBlockPos().toImmutable();
    }

    private boolean isPlayerHitboxBlockingBlock(BlockPos pos) {
        if (mc.player == null || pos == null) return false;
        Box box = mc.player.getBoundingBox();
        return box.intersects(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1.0, pos.getY() + 1.0, pos.getZ() + 1.0);
    }

    private void clearEnclosurePlacementConfirmation() {
        pendingEnclosurePlacementConfirmState = null;
        pendingEnclosurePlacementConfirmPos = null;
        pendingEnclosurePlacementCenterTarget = null;
        pendingEnclosurePlacementKitbotType = null;
        pendingEnclosurePlacementLabel = "";
        pendingEnclosurePlacementAttempts = 0;
        pendingEnclosurePlacementConfirmWaitTicks = 0;
        pendingEnclosurePlacementRecentered = false;
        pendingEnclosurePlacementPacketTarget = null;
        pendingEnclosurePlacementInteractPackets = 0;
        pendingEnclosurePlacementFirstPacketAge = 0;
        pendingEnclosurePlacementLastPacketAge = 0;
        enclosureDesyncWiggleUsedTarget = null;
    }

    private void startEnclosurePlacementConfirmation(State placementState, BlockPos pos, BlockPos centerTarget, KitbotStructureBlockType kitbotType, String label) {
        if (placementState == null || pos == null) return;

        BlockPos immutablePos = pos.toImmutable();
        boolean sameTarget = placementState == pendingEnclosurePlacementConfirmState
            && immutablePos.equals(pendingEnclosurePlacementConfirmPos);

        if (!sameTarget) {
            pendingEnclosurePlacementConfirmState = placementState;
            pendingEnclosurePlacementConfirmPos = immutablePos;
            pendingEnclosurePlacementCenterTarget = centerTarget == null ? enclosureCenterTargetForState(placementState) : centerTarget.toImmutable();
            pendingEnclosurePlacementKitbotType = kitbotType;
            pendingEnclosurePlacementLabel = label == null ? "" : label;
            pendingEnclosurePlacementAttempts = 0;
            pendingEnclosurePlacementRecentered = false;
            pendingEnclosurePlacementPacketTarget = immutablePos;
            pendingEnclosurePlacementInteractPackets = 0;
            pendingEnclosurePlacementFirstPacketAge = mc.player == null ? 0 : mc.player.age;
            pendingEnclosurePlacementLastPacketAge = pendingEnclosurePlacementFirstPacketAge;
            enclosureDesyncWiggleUsedTarget = null;
        } else {
            pendingEnclosurePlacementKitbotType = kitbotType;
            pendingEnclosurePlacementLabel = label == null ? pendingEnclosurePlacementLabel : label;
            if (centerTarget != null) pendingEnclosurePlacementCenterTarget = centerTarget.toImmutable();
        }

        pendingEnclosurePlacementAttempts++;
        pendingEnclosurePlacementConfirmWaitTicks = ENCLOSURE_PLACEMENT_CONFIRM_WAIT_TICKS;

        if (restockDebugLog.get()) {
            restockDebug("%s awaiting enclosure placement confirmation at %s (label=%s, attempt=%d/%d, recentered=%s).",
                stateName(placementState),
                formatBlockPos(immutablePos),
                pendingEnclosurePlacementLabel,
                pendingEnclosurePlacementAttempts,
                ENCLOSURE_PLACEMENT_CONFIRM_MAX_ATTEMPTS,
                pendingEnclosurePlacementRecentered
            );
        }
    }

    private void recordEnclosureInteractPacket(PlayerInteractBlockC2SPacket packet) {
        if (packet == null || pendingEnclosurePlacementConfirmPos == null || mc.player == null) return;
        BlockHitResult hit = packet.getBlockHitResult();
        if (hit == null || !pendingEnclosurePlacementConfirmPos.equals(hit.getBlockPos())) return;

        if (pendingEnclosurePlacementPacketTarget == null || !pendingEnclosurePlacementPacketTarget.equals(hit.getBlockPos())) {
            pendingEnclosurePlacementPacketTarget = hit.getBlockPos().toImmutable();
            pendingEnclosurePlacementInteractPackets = 0;
            pendingEnclosurePlacementFirstPacketAge = mc.player.age;
        }

        pendingEnclosurePlacementInteractPackets++;
        pendingEnclosurePlacementLastPacketAge = mc.player.age;

        writeThmDebugLog(
            RESTOCK_DEBUG_FILE_NAME,
            formatThmDebugLine(
                "Enclosure desync packet sample state=%s target=%s packets=%d firstAge=%d lastAge=%d attempts=%d/%d label=%s hand=%s side=%s sequence=%d hit=(%.6f,%.6f,%.6f) player=(%.6f,%.6f,%.6f).",
                stateName(pendingEnclosurePlacementConfirmState),
                formatBlockPos(pendingEnclosurePlacementConfirmPos),
                pendingEnclosurePlacementInteractPackets,
                pendingEnclosurePlacementFirstPacketAge,
                pendingEnclosurePlacementLastPacketAge,
                pendingEnclosurePlacementAttempts,
                ENCLOSURE_PLACEMENT_CONFIRM_MAX_ATTEMPTS,
                pendingEnclosurePlacementLabel,
                packet.getHand(),
                hit.getSide(),
                packet.getSequence(),
                hit.getPos().x,
                hit.getPos().y,
                hit.getPos().z,
                mc.player.getX(),
                mc.player.getY(),
                mc.player.getZ()
            )
        );
    }

    private boolean shouldStartEnclosureDesyncWiggleProbe(State placementState) {
        if (placementState == null || pendingEnclosurePlacementConfirmPos == null) return false;
        if (desyncWiggleProbe != null) return false;
        if (enclosureDesyncWiggleUsedTarget != null && enclosureDesyncWiggleUsedTarget.equals(pendingEnclosurePlacementConfirmPos)) return false;
        if (pendingEnclosurePlacementAttempts < ENCLOSURE_PLACEMENT_RECENTER_AFTER_ATTEMPTS) return false;

        boolean packetEvidence = pendingEnclosurePlacementInteractPackets >= ENCLOSURE_PLACEMENT_DESYNC_MIN_INTERACT_PACKETS;
        boolean attemptFallback = pendingEnclosurePlacementAttempts >= ENCLOSURE_PLACEMENT_RECENTER_AFTER_ATTEMPTS;
        if (!packetEvidence && !attemptFallback) return false;

        writeThmDebugLog(
            RESTOCK_DEBUG_FILE_NAME,
            formatThmDebugLine(
                "Enclosure desync wiggle eligible state=%s target=%s attempts=%d/%d interactPackets=%d packetEvidence=%s attemptFallback=%s recentered=%s label=%s centerTarget=%s player=(%.6f,%.6f,%.6f).",
                stateName(placementState),
                formatBlockPos(pendingEnclosurePlacementConfirmPos),
                pendingEnclosurePlacementAttempts,
                ENCLOSURE_PLACEMENT_CONFIRM_MAX_ATTEMPTS,
                pendingEnclosurePlacementInteractPackets,
                packetEvidence,
                attemptFallback,
                pendingEnclosurePlacementRecentered,
                pendingEnclosurePlacementLabel,
                formatBlockPos(pendingEnclosurePlacementCenterTarget),
                mc.player == null ? Double.NaN : mc.player.getX(),
                mc.player == null ? Double.NaN : mc.player.getY(),
                mc.player == null ? Double.NaN : mc.player.getZ()
            )
        );

        return true;
    }

    private boolean isPendingEnclosurePlacementConfirmed() {
        if (mc.world == null || pendingEnclosurePlacementConfirmPos == null) return false;

        BlockState state = mc.world.getBlockState(pendingEnclosurePlacementConfirmPos);
        if (pendingEnclosurePlacementKitbotType != null) {
            return isSatisfiedKitbotStructureBlock(state, pendingEnclosurePlacementKitbotType);
        }
        return isValidRestockStructureBlock(state);
    }

    private boolean handlePendingEnclosurePlacementConfirmation(State placementState, Runnable hardFail) {
        if (pendingEnclosurePlacementConfirmPos == null) return false;

        if (pendingEnclosurePlacementConfirmState != placementState) {
            clearEnclosurePlacementConfirmation();
            return false;
        }

        if (isPendingEnclosurePlacementConfirmed()) {
            BlockPos confirmedPos = pendingEnclosurePlacementConfirmPos;
            recordPlacedBlockForStats(confirmedPos);
            restockWatchdog.markProgress(placementState == State.KitbotOrder ? "kitbot-structure-confirmed" : "blockade-place-confirmed");
            if (restockDebugLog.get()) {
                restockDebug("%s confirmed enclosure placement at %s (label=%s, attempts=%d).",
                    stateName(placementState),
                    formatBlockPos(confirmedPos),
                    pendingEnclosurePlacementLabel,
                    pendingEnclosurePlacementAttempts
                );
            }
            clearEnclosurePlacementConfirmation();
            return true;
        }

        if (pendingEnclosurePlacementConfirmWaitTicks > 0) {
            pendingEnclosurePlacementConfirmWaitTicks--;
            return true;
        }

        if (shouldStartEnclosureDesyncWiggleProbe(placementState)) {
            if (tryStartEnclosureDesyncWiggleProbe(placementState, pendingEnclosurePlacementConfirmPos, pendingEnclosurePlacementLabel)) {
                return true;
            }
        }

        if (pendingEnclosurePlacementAttempts >= ENCLOSURE_PLACEMENT_CONFIRM_MAX_ATTEMPTS) {
            if (hardFail != null) hardFail.run();
            clearEnclosurePlacementConfirmation();
            return true;
        }

        if (placementState == State.KitbotOrder && pendingEnclosurePlacementRecentered) {
            BlockPos target = pendingEnclosurePlacementCenterTarget != null
                ? pendingEnclosurePlacementCenterTarget
                : enclosureCenterTargetForState(placementState);
            KitbotCenterWalkResult result = walkKitbotToBlockCenter(target, "unconfirmed-enclosure-placement");
            if (result == KitbotCenterWalkResult.TimedOut) {
                if (hardFail != null) hardFail.run();
                clearEnclosurePlacementConfirmation();
                return true;
            }
            return result == KitbotCenterWalkResult.Walking;
        }

        if (pendingEnclosurePlacementAttempts >= ENCLOSURE_PLACEMENT_RECENTER_AFTER_ATTEMPTS && !pendingEnclosurePlacementRecentered) {
            pendingEnclosurePlacementRecentered = true;
            BlockPos target = pendingEnclosurePlacementCenterTarget != null
                ? pendingEnclosurePlacementCenterTarget
                : enclosureCenterTargetForState(placementState);
            if (restockDebugLog.get()) {
                restockDebug("%s re-centering after unconfirmed enclosure placement at %s (label=%s, attempts=%d/%d).",
                    stateName(placementState),
                    formatBlockPos(pendingEnclosurePlacementConfirmPos),
                    pendingEnclosurePlacementLabel,
                    pendingEnclosurePlacementAttempts,
                    ENCLOSURE_PLACEMENT_CONFIRM_MAX_ATTEMPTS
                );
            }
            if (placementState == State.KitbotOrder) {
                KitbotCenterWalkResult result = walkKitbotToBlockCenter(target, "unconfirmed-enclosure-placement");
                if (result == KitbotCenterWalkResult.TimedOut) {
                    if (hardFail != null) hardFail.run();
                    clearEnclosurePlacementConfirmation();
                    return true;
                }
                return result == KitbotCenterWalkResult.Walking;
            }

            routeToCenterForEnclosurePlacement(placementState, target, "unconfirmed-enclosure-placement");
            return true;
        }

        return false;
    }

    private boolean isHotbarSlotReservedByManager(int hotbarSlot) {
        if (hotbarSlot < 0 || hotbarSlot >= 9) return false;
        if (!hotbarmanager.get()) return false;

        HotbarManager manager = Modules.get().get(HotbarManager.class);
        return manager != null && manager.isActive() && manager.managesSlot(hotbarSlot);
    }

    private boolean isHotbarSlotConfiguredByManager(int hotbarSlot) {
        if (hotbarSlot < 0 || hotbarSlot >= 9) return false;
        if (!hotbarmanager.get()) return false;

        HotbarManager manager = Modules.get().get(HotbarManager.class);
        return manager != null && manager.managesSlot(hotbarSlot);
    }

    private boolean disableHotbarManagerForTrash(String reason) {
        if (!hotbarmanager.get()) return false;

        HotbarManager manager = Modules.get().get(HotbarManager.class);
        if (manager == null || !manager.isActive()) return false;

        manager.toggle();
        if (restockDebugLog.get()) restockDebug("ThrowOutTrash paused HotbarManager (%s).", reason);
        return true;
    }

    private void restoreHotbarManagerAfterTrash(String reason) {
        if (!hotbarmanager.get()) return;

        HotbarManager manager = Modules.get().get(HotbarManager.class);
        if (manager == null || manager.isActive()) return;

        manager.toggle();
        if (restockDebugLog.get()) restockDebug("ThrowOutTrash restored HotbarManager (%s).", reason);
    }

    private void syncFoodManagementOnActivate() {
        switch (foodManagement.get()) {
            case None -> {
            }
            case AutoEat -> {
                AutoEat autoEat = Modules.get().get(AutoEat.class);
                if (autoEat == null) {
                    warning("Food Management could not find Meteor Auto Eat.");
                    return;
                }

                autoEat.blacklist.set(new ArrayList<>(List.of(Items.CHORUS_FRUIT)));
                if (!autoEat.isActive()) autoEat.enable();
            }
            case AutoGap -> {
                AutoGap autoGap = Modules.get().get(AutoGap.class);
                if (autoGap == null) {
                    warning("Food Management could not find Meteor Auto Gap.");
                    return;
                }

                if (!autoGap.isActive()) autoGap.enable();
            }
        }
    }

    private void validateManagedHotbarReserveSlots() {
        if (!hotbarmanager.get()) return;

        HotbarManager manager = Modules.get().get(HotbarManager.class);
        if (manager == null || !manager.isActive()) return;

        if (!hasManagedHotbarItem(manager, Items.ENDER_CHEST)) {
            warning("Hotbar Manager reserve warning: no managed ender chest slot is configured.");
        }

        if (!hasManagedHighwayPickaxeSlot(manager)) {
            warning("Hotbar Manager reserve warning: no managed diamond or netherite pickaxe slot is configured.");
        } else if (hasInvalidLiveManagedHighwayPickaxe(manager)) {
            warning("Hotbar Manager reserve warning: the live managed pickaxe has Silk Touch or does not pass the durability guard.");
        }

        if (foodManagement.get() != FoodManagement.None) {
            if (!hasConfiguredFoodTypes()) {
                warning("Hotbar Manager reserve warning: Food Management is enabled but no food type is configured.");
            } else if (!hasManagedConfiguredFoodSlot(manager)) {
                warning("Hotbar Manager reserve warning: no managed slot matches the configured food type.");
            }
        }

        if (blocksToPlace.get().contains(Blocks.NETHERRACK)) {
            if (!hasManagedHotbarItem(manager, Items.NETHERRACK)) {
                warning("Hotbar Manager reserve warning: digging with netherrack requires a managed netherrack reserve slot.");
            }
        } else {
            if (!hasManagedHotbarItem(manager, Items.NETHERRACK)) {
                warning("Hotbar Manager reserve warning: paving requires a managed netherrack reserve slot.");
            }
            if (!hasManagedHotbarItem(manager, Items.OBSIDIAN)) {
                warning("Hotbar Manager reserve warning: paving requires a managed obsidian reserve slot.");
            }
        }
    }

    private boolean hasManagedHotbarItem(HotbarManager manager, Item item) {
        if (manager == null || item == null || item == Items.AIR) return false;

        for (int i = 0; i < 9; i++) {
            if (manager.managesSlot(i) && manager.getManagedItem(i) == item) return true;
        }

        return false;
    }

    private boolean hasManagedConfiguredFoodSlot(HotbarManager manager) {
        if (manager == null || !hasConfiguredFoodTypes()) return false;

        for (int i = 0; i < 9; i++) {
            if (manager.managesSlot(i) && isConfiguredFoodItem(manager.getManagedItem(i))) return true;
        }

        return false;
    }

    private boolean isManagedHighwayPickaxeItem(Item item) {
        return item == Items.DIAMOND_PICKAXE || item == Items.NETHERITE_PICKAXE;
    }

    private boolean hasManagedHighwayPickaxeSlot(HotbarManager manager) {
        if (manager == null) return false;

        for (int i = 0; i < 9; i++) {
            if (manager.managesSlot(i) && isManagedHighwayPickaxeItem(manager.getManagedItem(i))) return true;
        }

        return false;
    }

    private boolean hasInvalidLiveManagedHighwayPickaxe(HotbarManager manager) {
        if (manager == null || mc.player == null) return false;

        boolean invalidLiveStack = false;
        for (int i = 0; i < 9; i++) {
            if (!manager.managesSlot(i)) continue;

            Item configuredItem = manager.getManagedItem(i);
            if (!isManagedHighwayPickaxeItem(configuredItem)) continue;

            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isEmpty() || stack.getItem() != configuredItem) return false;
            if (passesEChestMiningPickaxeGuard(stack)) return false;

            invalidLiveStack = true;
        }

        return invalidLiveStack;
    }

    private Item getReservedHotbarItem(int hotbarSlot) {
        HotbarManager manager = Modules.get().get(HotbarManager.class);
        if (manager == null) return Items.AIR;
        return manager.getManagedItem(hotbarSlot);
    }

    private int getPreferredManagedHotbarSlot(Item item) {
        if (item == null || item == Items.AIR) return -1;
        if (!hotbarmanager.get()) return -1;

        HotbarManager manager = Modules.get().get(HotbarManager.class);
        if (manager == null || !manager.isActive()) return -1;

        for (int i = 0; i < 9; i++) {
            if (manager.managesSlot(i) && manager.getManagedItem(i) == item) return i;
        }

        return -1;
    }

    private boolean isForwardPlaceableBlock(ItemStack stack) {
        return stack.getItem() instanceof BlockItem blockItem && blocksToPlace.get().contains(blockItem.getBlock());
    }

    public int findAndMoveBestToolToHotbarForSharedUtility(BlockState blockState, boolean noSilkTouch, boolean failHardNoHotbar) {
        return State.Forward.findAndMoveBestToolToHotbar(this, blockState, noSilkTouch, failHardNoHotbar);
    }

    public int breakContainerBlockForSharedUtility(BlockPos bp, boolean noSilkTouch, boolean failHardNoHotbar) {
        if (bp == null || mc.player == null || mc.world == null) return -1;

        BlockState state = mc.world.getBlockState(bp);
        int toolSlot = findAndMoveBestToolToHotbarForSharedUtility(state, noSilkTouch, failHardNoHotbar);
        if (toolSlot != -1 && toolSlot != mc.player.getInventory().getSelectedSlot()) InvUtils.swap(toolSlot, false);

        if (rotation.get().mine) Rotations.rotate(Rotations.getYaw(bp), Rotations.getPitch(bp), () -> BlockUtils.breakBlock(bp, true));
        else BlockUtils.breakBlock(bp, true);

        return toolSlot;
    }

    private boolean clearCursorStackToEmptySlot(String reason) {
        return clearCursorStackToEmptySlot(reason, false);
    }

    private boolean clearCursorStackToEmptySlot(String reason, boolean avoidReservedHotbarSlots) {
        if (mc.player == null || mc.player.currentScreenHandler == null) return false;
        if (mc.player.currentScreenHandler.getCursorStack().isEmpty()) return true;

        int emptySlot = -1;

        for (int i = 9; i < mc.player.getInventory().getMainStacks().size(); i++) {
            if (mc.player.getInventory().getStack(i).isEmpty()) {
                emptySlot = i;
                break;
            }
        }

        if (emptySlot == -1) {
            for (int i = 0; i < 9; i++) {
                if (avoidReservedHotbarSlots && isHotbarSlotReservedByManager(i)) continue;
                if (mc.player.getInventory().getStack(i).isEmpty()) {
                    emptySlot = i;
                    break;
                }
            }
        }

        if (emptySlot == -1) return false;

        mc.interactionManager.clickSlot(
            mc.player.currentScreenHandler.syncId,
            SlotUtils.indexToId(emptySlot),
            0,
            SlotActionType.PICKUP,
            mc.player
        );

        if (restockDebugLog.get()) {
            restockDebug("Cleared cursor stack into empty slot %d (%s).", emptySlot, reason);
        }

        return mc.player.currentScreenHandler.getCursorStack().isEmpty();
    }

    private boolean protectUsefulCursorStackFromDrop(String reason) {
        return protectUsefulCursorStackFromDrop(reason, false);
    }

    private boolean protectUsefulCursorStackFromDrop(String reason, boolean bypassAntiDrop) {
        if (mc.player == null || mc.player.currentScreenHandler == null) return false;

        ItemStack cursorStack = mc.player.currentScreenHandler.getCursorStack();
        if (cursorStack.isEmpty() || !isUsefulCursorStack(cursorStack)) return false;

        int trashSlot = findTrashBlockSwapSlot();
        if (trashSlot == -1) {
            if (restockDebugLog.get()) {
                restockDebug("Preserved useful cursor stack %s because no trash block swap slot was available (%s).", cursorStack.getItem(), reason);
            }
            return true;
        }

        mc.interactionManager.clickSlot(
            mc.player.currentScreenHandler.syncId,
            SlotUtils.indexToId(trashSlot),
            0,
            SlotActionType.PICKUP,
            mc.player
        );

        ItemStack swappedCursor = mc.player.currentScreenHandler.getCursorStack();
        if (isUsefulCursorStack(swappedCursor)) {
            if (restockDebugLog.get()) {
                restockDebug("Preserved useful cursor stack %s because trash swap did not dislodge it (%s).", swappedCursor.getItem(), reason);
            }
            return true;
        }

        if (bypassAntiDrop) dropCursorHandBypassingAntiDrop();
        else InvUtils.dropHand();

        if (restockDebugLog.get()) {
            restockDebug("Swapped useful cursor stack for droppable trash and dropped cursor item (%s).", reason);
        }

        return true;
    }

    private boolean dropCursorStackIfSafe(String reason) {
        return dropCursorStackIfSafe(reason, false);
    }

    private boolean dropCursorStackIfSafe(String reason, boolean bypassAntiDrop) {
        if (mc.player == null || mc.player.currentScreenHandler == null) return false;

        ItemStack cursorStack = mc.player.currentScreenHandler.getCursorStack();
        if (cursorStack.isEmpty()) return false;

        if (protectUsefulCursorStackFromDrop(reason, bypassAntiDrop)) return true;

        if (bypassAntiDrop) dropCursorHandBypassingAntiDrop();
        else InvUtils.dropHand();

        if (restockDebugLog.get()) {
            restockDebug("Dropped non-useful cursor stack %s (%s).", cursorStack.getItem(), reason);
        }

        return true;
    }

    private void dropCursorHandBypassingAntiDrop() {
        AntiDrop antiDrop = Modules.get().get(AntiDrop.class);
        boolean wasActive = antiDrop != null && antiDrop.isActive();
        if (wasActive) antiDrop.toggle();
        InvUtils.dropHand();
        if (wasActive) antiDrop.toggle();
    }

    private void forceCursorDropAttemptBypassingAntiDrop(String reason) {
        if (mc.player == null || mc.interactionManager == null || mc.player.currentScreenHandler == null) return;

        AntiDrop antiDrop = Modules.get().get(AntiDrop.class);
        boolean wasActive = antiDrop != null && antiDrop.isActive();
        if (wasActive) antiDrop.toggle();

        try {
            mc.interactionManager.clickSlot(
                mc.player.currentScreenHandler.syncId,
                -999,
                0,
                SlotActionType.PICKUP,
                mc.player
            );
        } finally {
            if (wasActive) antiDrop.toggle();
        }

        if (restockDebugLog.get()) {
            restockDebug("Issued unconditional cursor drop attempt (%s).", reason);
        }
    }

    private int findTrashBlockSwapSlot() {
        if (mc.player == null) return -1;

        for (int i = 0; i < mc.player.getInventory().getMainStacks().size(); i++) {
            ItemStack itemStack = mc.player.getInventory().getStack(i);
            if (!(itemStack.getItem() instanceof BlockItem)) continue;
            if (!isDroppableTrashStack(itemStack)) continue;
            return i;
        }

        return -1;
    }

    private void handleFoodTypesChanged(ItemPriorityList selected) {
        if (kitbotFoodRestock != null && kitbotFoodRestock.get()) {
            enforceKitbotFoodRestockFoodType();
        }
    }

    private void enforceKitbotFoodRestockFoodType() {
        if (kitbotFoodRestock == null || !kitbotFoodRestock.get()) return;
        if (foodTypes == null || clampingFoodTypes) return;
        List<Item> selected = foodTypes.get().selected();
        if (selected.size() == 1 && selected.get(0) == Items.ENCHANTED_GOLDEN_APPLE) return;

        clampingFoodTypes = true;
        try {
            selected.clear();
            selected.add(Items.ENCHANTED_GOLDEN_APPLE);
            foodTypes.onChanged();
        } finally {
            clampingFoodTypes = false;
        }
    }

    private boolean hasConfiguredFoodTypes() {
        return !foodTypes.get().selected().isEmpty();
    }

    private boolean isConfiguredFoodItem(Item item) {
        return item != null && foodTypes.get().selected().contains(item);
    }

    private boolean isConfiguredFoodStack(ItemStack itemStack) {
        return itemStack != null && !itemStack.isEmpty() && isConfiguredFoodItem(itemStack.getItem());
    }

    private int countConfiguredFoodItemsInInventory() {
        if (mc.player == null) return 0;

        int count = 0;
        for (int i = 0; i < mc.player.getInventory().getMainStacks().size(); i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (isConfiguredFoodStack(stack)) count += stack.getCount();
        }

        return count;
    }

    private int countLooseInventoryEnderChests() {
        if (mc.player == null) return 0;

        int count = 0;
        for (int i = 0; i < mc.player.getInventory().getMainStacks().size(); i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isOf(Items.ENDER_CHEST)) count += stack.getCount();
        }

        return count;
    }

    private int getConfiguredFoodMaxStackSize() {
        if (!hasConfiguredFoodTypes()) return 0;
        Item foodItem = foodTypes.get().selected().get(0);
        return foodItem != null ? foodItem.getMaxCount() : 0;
    }

    private int countEmptyInventorySlotsInMainInventory() {
        if (mc.player == null) return 0;

        int count = 0;
        for (int i = 0; i < mc.player.getInventory().getMainStacks().size(); i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isEmpty()) count++;
        }

        return count;
    }

    private int countConfiguredFoodMergeCapacityInInventory() {
        if (mc.player == null) return 0;

        int capacity = 0;
        for (int i = 0; i < mc.player.getInventory().getMainStacks().size(); i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!isConfiguredFoodStack(stack)) continue;

            capacity += Math.max(stack.getMaxCount() - stack.getCount(), 0);
        }

        return capacity;
    }

    private int countConfiguredFoodAdditionalCapacityInInventory() {
        int maxStackSize = getConfiguredFoodMaxStackSize();
        if (maxStackSize <= 0) return countConfiguredFoodMergeCapacityInInventory();

        return countConfiguredFoodMergeCapacityInInventory() + (countEmptyInventorySlotsInMainInventory() * maxStackSize);
    }

    private int getConfiguredFoodTargetIncrease(int currentLooseFoodCount) {
        return Math.max((saveFood.get() + 1) - currentLooseFoodCount, 0);
    }

    private int findPreferredConfiguredFoodSlot(Inventory inventory) {
        int fullestMatch = -1;
        int fullestCount = -1;

        for (int i = 0; i < inventory.size(); i++) {
            ItemStack stack = inventory.getStack(i);
            if (!isConfiguredFoodStack(stack)) continue;

            if (stack.getCount() > fullestCount) {
                fullestCount = stack.getCount();
                fullestMatch = i;
            }
        }

        return fullestMatch;
    }

    private int findPreferredLooseEnderChestSlot(Inventory inventory) {
        for (int i = 0; i < inventory.size(); i++) {
            if (inventory.getStack(i).isOf(Items.ENDER_CHEST)) return i;
        }

        return -1;
    }

    private boolean isContainerItemEmpty(ItemStack containerItem) {
        if (containerItem == null || containerItem.isEmpty() || !Utils.isShulker(containerItem.getItem())) return true;

        return THMUtils.getContainerContents(containerItem).isEmpty();
    }

    private boolean isContainerInventoryEmpty(Inventory inventory) {
        for (int i = 0; i < inventory.size(); i++) {
            if (!inventory.getStack(i).isEmpty()) return false;
        }

        return true;
    }

    private boolean shouldTriggerFoodRestock() {
        return foodRestock.get()
            && hasConfiguredFoodTypes()
            && countConfiguredFoodItemsInInventory() <= saveFood.get();
    }

    private void maybeQueueFoodRestock() {
        if (mc.player == null || mc.world == null) return;
        if (!shouldTriggerFoodRestock()) return;
        restockTask.setFood();
    }

    private boolean isEnderChestReserveRestockEnabled() {
        return searchEnderChest.get();
    }

    private boolean shouldTriggerEnderChestReserveRestock() {
        if (mc.player == null || !isEnderChestReserveRestockEnabled()) return false;
        return countLooseInventoryEnderChests() <= saveEchests.get() - 1;
    }

    private void maybeQueueEnderChestReserveRestock() {
        if (mc.player == null || mc.world == null) return;
        if (!isEnderChestReserveRestockEnabled()) return;
        if (!shouldTriggerEnderChestReserveRestock()) return;
        restockTask.setEnderChests();
    }

    private int countLooseInventoryPickaxes() {
        if (mc.player == null) return 0;

        int count = 0;
        for (int i = 0; i < mc.player.getInventory().getMainStacks().size(); i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isIn(ItemTags.PICKAXES)) count++;
        }

        return count;
    }

    private int countLooseEligibleEChestMiningPickaxes() {
        if (mc.player == null) return 0;

        int count = 0;
        for (int i = 0; i < mc.player.getInventory().getMainStacks().size(); i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (passesEChestMiningPickaxeGuard(stack)) count += stack.getCount();
        }

        return count;
    }

    private boolean hasPickaxeRestockSourceAvailable() {
        return searchShulkers.get()
            || searchEnderChest.get()
            || (kitbotRestock.get() && kitbotPickaxeRestock.get());
    }

    private boolean isActivePickaxeRestockMatch(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.isIn(ItemTags.PICKAXES)) return false;
        return !restockTask.isObsidianPickaxePreflightTopoffActive()
            || passesEChestMiningPickaxeGuard(stack);
    }

    private boolean shouldTriggerPickaxeRestock() {
        if (mc.player == null) return false;
        return countLooseInventoryPickaxes() <= savePickaxes.get();
    }

    private void maybeQueuePickaxeRestock() {
        if (mc.player == null || mc.world == null) return;
        if (!hasPickaxeRestockSourceAvailable()) return;
        if (!shouldTriggerPickaxeRestock()) return;
        restockTask.setPickaxes();
    }

    private boolean passesEChestMiningPickaxeGuard(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (!stack.isIn(ItemTags.PICKAXES)) return false;
        if (Utils.hasEnchantment(stack, Enchantments.SILK_TOUCH)) return false;
        return !dontBreakTools.get() || stack.getMaxDamage() - stack.getDamage() > (stack.getMaxDamage() * (breakDurability.get() / 100.0));
    }

    private int findEligibleEChestMiningPickaxeSlot() {
        if (mc.player == null) return -1;

        BlockState eChestState = Blocks.ENDER_CHEST.getDefaultState();
        double bestScore = -1;
        int bestSlot = -1;

        for (int i = 0; i < mc.player.getInventory().getMainStacks().size(); i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            double score = AutoTool.getScore(stack, eChestState, false, false, AutoTool.EnchantPreference.None, this::passesEChestMiningPickaxeGuard);
            if (score > bestScore) {
                bestScore = score;
                bestSlot = i;
            }
        }

        return bestSlot;
    }

    private boolean hasEligibleEChestMiningPickaxe() {
        return findEligibleEChestMiningPickaxeSlot() != -1;
    }

    private int getRemainingUses(ItemStack stack) {
        if (stack == null || stack.isEmpty() || stack.getMaxDamage() <= 0) return Integer.MAX_VALUE;
        return stack.getMaxDamage() - stack.getDamage();
    }

    private boolean prioritizePickaxeRestockBeforeEChestMining(int eChestsToBreak, String reason) {
        if (eChestsToBreak <= 0) return false;
        if (countLooseInventoryPickaxes() > savePickaxes.get() + 1) return false;

        int toolSlot = findEligibleEChestMiningPickaxeSlot();
        boolean needsPickaxeRestock = toolSlot == -1;
        int remainingUses = -1;
        if (!needsPickaxeRestock) {
            ItemStack toolStack = mc.player.getInventory().getStack(toolSlot);
            remainingUses = getRemainingUses(toolStack);
            needsPickaxeRestock = remainingUses - eChestsToBreak <= 0;
        }

        if (restockDebugLog.get()) {
            restockDebug("Obsidian e-chest pickaxe preflight (%s): echestsToBreak=%d toolSlot=%d remainingUses=%d needsPickaxeRestock=%s loosePickaxes=%d savePickaxes=%d.",
                reason,
                eChestsToBreak,
                toolSlot,
                remainingUses,
                needsPickaxeRestock,
                countLooseInventoryPickaxes(),
                savePickaxes.get()
            );
        }

        if (!needsPickaxeRestock) return false;

        if (!hasPickaxeRestockSourceAvailable()) {
            restockTask.failActiveTaskHard("Obsidian restock needs a non-silk pickaxe with durability left, but no pickaxe restock source is enabled. Enable one in Pickaxe Restock settings.");
            return true;
        }

        if (restockTask.prioritizeObsidianPreflightPickaxesBeforeActiveTask(eChestsToBreak, reason)) {
            setState(State.Restock, State.Restock);
            return true;
        }

        return false;
    }

    private boolean isProtectedItem(Item item) {
        return item != null
            && item != Items.AIR
            && (Registries.ITEM.getEntry(item).isIn(ItemTags.PICKAXES) || protectedItems.get().contains(item));
    }

    private boolean isProtectedItemStack(ItemStack itemStack) {
        return itemStack != null
            && !itemStack.isEmpty()
            && isProtectedItem(itemStack.getItem());
    }

    private boolean isDroppableTrashItem(Item item) {
        return item != null
            && item != Items.AIR
            && !Utils.isShulker(item)
            && !isProtectedItem(item);
    }

    private boolean isDroppableTrashStack(ItemStack itemStack) {
        return itemStack != null
            && !itemStack.isEmpty()
            && isDroppableTrashItem(itemStack.getItem());
    }

    private boolean isStableFullTrashBlockStack(ItemStack itemStack) {
        if (!isDroppableTrashStack(itemStack)) return false;
        if (!(itemStack.getItem() instanceof BlockItem blockItem)) return false;
        if (Utils.isShulker(itemStack.getItem())) return false;

        Block block = blockItem.getBlock();
        if (block instanceof FallingBlock || block instanceof BlockWithEntity) return false;

        return Block.isShapeFullCube(block.getDefaultState().getCollisionShape(EmptyBlockView.INSTANCE, BlockPos.ORIGIN));
    }

    private int findPreferredTrashBlockSlot() {
        if (mc.player == null) return -1;

        int bestSlot = -1;
        for (int i = 0; i < mc.player.getInventory().getMainStacks().size(); i++) {
            if (!isStableFullTrashBlockStack(mc.player.getInventory().getStack(i))) continue;
            if (bestSlot == -1 || compareTrashBlockPreferenceSlots(i, bestSlot) < 0) bestSlot = i;
        }

        return bestSlot;
    }

    private int compareTrashBlockPreferenceSlots(int leftSlot, int rightSlot) {
        ItemStack left = mc.player.getInventory().getStack(leftSlot);
        ItemStack right = mc.player.getInventory().getStack(rightSlot);

        int rankCompare = Integer.compare(getTrashBlockPreferenceRank(left), getTrashBlockPreferenceRank(right));
        if (rankCompare != 0) return rankCompare;

        int countCompare = Integer.compare(right.getCount(), left.getCount());
        if (countCompare != 0) return countCompare;

        return Integer.compare(leftSlot, rightSlot);
    }

    private int getTrashBlockPreferenceRank(ItemStack itemStack) {
        if (!isStableFullTrashBlockStack(itemStack)) return Integer.MAX_VALUE;

        Block block = ((BlockItem) itemStack.getItem()).getBlock();
        if (block == Blocks.NETHERRACK) return 0;
        if (canInstamineTrashBlockWithCurrentHotbar(block)) return 1;
        return 2;
    }

    private boolean canInstamineTrashBlockWithCurrentHotbar(Block block) {
        if (mc.player == null || mc.world == null || block == null) return false;

        BlockState state = block.getDefaultState();
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).isEmpty()) continue;

            try {
                if (BlockUtils.getBreakDelta(i, state) >= 1.0) return true;
            } catch (RuntimeException | StackOverflowError ignored) {
                return false;
            }
        }

        return false;
    }

    private boolean isUsefulCursorStack(ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty()) return false;
        if (isProtectedItemStack(itemStack)) return true;
        if (itemStack.isIn(ItemTags.PICKAXES)) return true;
        if (isConfiguredFoodStack(itemStack)) return true;
        if (Utils.isShulker(itemStack.getItem())) return isUsefulShulkerStack(itemStack);
        if (itemStack.getItem() instanceof BlockItem bi) {
            if (isDroppableTrashStack(itemStack)) return false;
            if (blocksToPlace.get().contains(bi.getBlock())) return true;
            if (bi == Items.ENDER_CHEST) return true;
        }
        if (itemStack.isOf(Items.OBSIDIAN) && !isDroppableTrashItem(Items.OBSIDIAN)) return true;
        return false;
    }

    private boolean isUsefulShulkerStack(ItemStack itemStack) {
        if (isProtectedItemStack(itemStack)) return true;

        for (ItemStack stack : THMUtils.getContainerContents(itemStack)) {
            if (isProtectedItemStack(stack)) return true;
            if (stack.getItem() instanceof BlockItem bi
                && (blocksToPlace.get().contains(bi.getBlock())
                || (blocksToPlace.get().contains(Blocks.OBSIDIAN) && bi == Items.ENDER_CHEST))) {
                return true;
            }
            if (stack.isIn(ItemTags.PICKAXES)) return true;
            if (isConfiguredFoodStack(stack)) return true;
        }

        return false;
    }

    private void logRestockBlockadeProbe(String label, MBPIterator it) {
        if (!restockDebugLog.get() || mc.player == null || mc.world == null) return;

        it.save();

        try {
            int index = 0;
            while (it.hasNext()) {
                MBlockPos pos = it.next();
                BlockPos blockPos = pos.getBlockPos();
                BlockState state = mc.world.getBlockState(blockPos);
                boolean inRange = RangeUtils.isInRange(placeRange.get(), blockPos);
                boolean canPlaceHere = BlockUtils.canPlace(blockPos);

                restockDebug("%s probe[%d] pos=%s block=%s replaceable=%s inRange=%s canPlace=%s",
                    label,
                    index++,
                    formatBlockPos(blockPos),
                    state.getBlock(),
                    state.isReplaceable(),
                    inRange,
                    canPlaceHere
                );
            }

            if (index == 0) restockDebug("%s probe found no target positions.", label);
        } finally {
            it.restore();
        }
    }

    private void disconnect(String message, Object... args) {
        notifyDesktop(notifyDisconnect, "THM Highway Builder", "Disconnected: " + String.format(message, args));

        MutableText text = Text.literal("[")
            .styled(style -> style.withColor(Formatting.WHITE))
            .append(Text.literal(title).styled(style -> style.withColor(Formatting.BLUE)))
            .append(Text.literal("] ").styled(style -> style.withColor(Formatting.WHITE)))
            .append(Text.literal(String.format(message, args)).styled(style -> style.withColor(Formatting.RED)));

        StatsDisconnectPresentation presentation = appendRetiredStatsToDisconnectPresentation(text);

        mc.getNetworkHandler().getConnection().disconnect(presentation.text());
        scheduleRetiredStatsDisconnectScreenshot(presentation.retiredSessionId(), "disconnect-screen-stats");
    }

    public MutableText getStatsText() {
        Vec3d statsStart = null;
        int statsBroken = 0;
        int statsPlaced = 0;

        if (hasActiveInMemoryStatsSession()) {
            statsStart = start;
            statsBroken = blocksBroken;
            statsPlaced = blocksPlaced;
        } else if (retiredStatsReportSnapshot != null) {
            statsStart = retiredStatsReportSnapshot.startPos();
            statsBroken = retiredStatsReportSnapshot.blocksBroken();
            statsPlaced = retiredStatsReportSnapshot.blocksPlaced();
        }

        return createStatsText(statsStart, statsBroken, statsPlaced);
    }

    public StatsDisconnectPresentation getReconnectSafetyStopPresentation(String reason, long reconnectCycleId) {
        HorizontalDirection cachedDirection = getCachedWorkingDirectionForMonitorReconnect(reconnectCycleId);
        String cachedName = cachedDirection == null ? lastReconnectResumeCachedDirectionName : cachedDirection.name;
        String selectedName = lastReconnectResumeSelectedDirectionName;
        String failureReason = lastReconnectResumeFailureReason;

        if (lastReconnectResumeFailureCycleId != reconnectCycleId) {
            if (cachedName == null) cachedName = "";
            selectedName = "";
            failureReason = "";
        }

        MutableText text = Text.literal("THMHwyMonitor Safety Stop: " + (reason == null ? "unknown" : reason));
        StatsDisconnectPresentation presentation = appendRetiredStatsToDisconnectPresentation(text);
        if (presentation.retiredSessionId() == null) text.append("\n");
        text.append(String.format("%sCached direction: %s%s\n",
            Formatting.GRAY,
            Formatting.WHITE,
            cachedName == null || cachedName.isBlank() ? "unknown" : cachedName
        ));
        text.append(String.format("%sSelected direction: %s%s\n",
            Formatting.GRAY,
            Formatting.WHITE,
            selectedName == null || selectedName.isBlank() ? "unknown" : selectedName
        ));
        text.append(String.format("%sResume reason: %s%s",
            Formatting.GRAY,
            Formatting.WHITE,
            failureReason == null || failureReason.isBlank() ? reason : failureReason
        ));
        return new StatsDisconnectPresentation(text, presentation.retiredSessionId());
    }

    private StatsDisconnectPresentation appendRetiredStatsToDisconnectPresentation(MutableText text) {
        MutableText finalizedStatsText = getFinalizedStatsText();
        RetiredStatsReportSnapshot report = retiredStatsReportSnapshot;
        if (finalizedStatsText == null || report == null) {
            return new StatsDisconnectPresentation(text, null);
        }

        text.append("\n").append(finalizedStatsText);
        return new StatsDisconnectPresentation(text, report.sessionId());
    }

    private void notifyDesktop(Setting<Boolean> eventToggle, String heading, String description) {
        if (!desktopNotifies.get() || !eventToggle.get()) return;
        Notify(heading, description);
    }

    private void activatePacketMode() {
        packetBuild.set(true);
        packetBorer.set(true);
    }

    private void tickPacketBorer() {
        if (!packetBorer.get() || dir == null || mc.player == null || mc.world == null) return;
        borerPackets = 0;
        int dx = dir.offsetX, dz = dir.offsetZ;
        // perpendicular: left = CW rotation (dz, -dx), right = CCW (-dz, dx)
        int lx = dz, lz = -dx, rx = -dz, rz = dx;
        BlockPos base = mc.player.getBlockPos();
        int xOff = dir.diagonal ? dx * 2 : 0;
        int zOff = dir.diagonal ? dz * 2 : 0;
        doPacketBorerShape(base.add(xOff, 0, zOff), dx, dz, lx, lz, rx, rz);
        // for diagonals, a second shifted pass covers the other arm of the diagonal tunnel
        if (dir.diagonal) doPacketBorerShape(base.add(xOff * -3, 0, zOff * -3), dx, dz, lx, lz, rx, rz);
    }

    private void doPacketBorerShape(BlockPos center, int dx, int dz, int lx, int lz, int rx, int rz) {
        int ext = dir.diagonal ? 1 : 4;
        int back = dir.diagonal ? 2 : 4;
        for (int i = -back; i <= ext; i++) {
            int bx = center.getX() + dx * i;
            int bz = center.getZ() + dz * i;
            int by = center.getY();
            // center col (y+0..3), right-1 (y+0..3), right-2 (y+1..3), left-1 (y+0..3), left-2 (y+0..3), left-3 (y+1..3)
            for (int y = 0; y <= 3; y++) borerBreakBlock(new BlockPos(bx, by + y, bz));
            for (int y = 0; y <= 3; y++) borerBreakBlock(new BlockPos(bx + rx, by + y, bz + rz));
            for (int y = 1; y <= 3; y++) borerBreakBlock(new BlockPos(bx + rx * 2, by + y, bz + rz * 2));
            for (int y = 0; y <= 3; y++) borerBreakBlock(new BlockPos(bx + lx, by + y, bz + lz));
            for (int y = 0; y <= 3; y++) borerBreakBlock(new BlockPos(bx + lx * 2, by + y, bz + lz * 2));
            for (int y = 1; y <= 3; y++) borerBreakBlock(new BlockPos(bx + lx * 3, by + y, bz + lz * 3));
        }
    }

    private void borerBreakBlock(BlockPos pos) {
        if (borerPackets >= 130) return;
        BlockState state = mc.world.getBlockState(pos);
        if (!safeCanBreak(pos, state)) return;
        mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, pos, Direction.UP));
        mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, pos, Direction.UP));
        borerPackets += 2;
    }

    private void tickDoubleMine() {
        // could add clientside block breaking to speed the system up, but it would probably make it too vulnerable to desyncs
        if (normalMining != null) {
            if (normalMining.shouldRemove()) {
                cancelPendingForwardBreakCredit(normalMining.blockPos);
                mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.ABORT_DESTROY_BLOCK, normalMining.blockPos, normalMining.direction));
                normalMining = null;
                DoubleMineBlock.rateLimited = true;
            }
            else if (mc.world.getBlockState(normalMining.blockPos).getBlock() != normalMining.block) {
                BlockPos brokenPos = normalMining.blockPos.toImmutable();
                normalMining = null;
                recordConfirmedBrokenBlockForStats(brokenPos);
                count++;
                if (isForwardSchedulerTick()) {
                    forwardMineCount++;
                    markForwardSchedulerProgress();
                }
                DoubleMineBlock.rateLimited = false;
            }
            else if (normalMining.isReady()) {
                normalMining.stopDestroying();
            }

            mc.player.swingHand(Hand.MAIN_HAND);
        }

        if (packetMining != null) {
            if (packetMining.shouldRemove()) {
                // should we add rate limiting for packet mined blocks? More testing required to see if appropriate
                cancelPendingForwardBreakCredit(packetMining.blockPos);
                packetMining = null;
            }
            else if (mc.world.getBlockState(packetMining.blockPos).getBlock() != packetMining.block) {
                BlockPos brokenPos = packetMining.blockPos.toImmutable();
                packetMining = null;
                recordConfirmedBrokenBlockForStats(brokenPos);
                count++;
                if (isForwardSchedulerTick()) {
                    forwardMineCount++;
                    markForwardSchedulerProgress();
                }
            }
        }
    }

    private boolean isForwardSchedulerTick() {
        return useForwardRowSchedulerMode() && state == State.Forward;
    }

    private boolean isLegacyForwardStatState(State state) {
        return state == State.Forward
            || state == State.MineFront
            || state == State.MineFloor
            || state == State.MineRailings
            || state == State.MineAboveRailings
            || state == State.PlaceCornerBlock
            || state == State.PlaceRailings
            || state == State.PlaceFloor;
    }

    private boolean shouldUseForwardStatsGuard() {
        if (mc.player == null || mc.world == null || blockPosProvider == null) return false;
        if (!useForwardRowSchedulerMode()) return isLegacyForwardStatState(state);
        return state == State.Forward;
    }

    private boolean isForwardBreakCreditEligibleType(ForwardTaskType type) {
        return isSchedulerCountableFrontMineTask(type);
    }

    private void pruneExpiredForwardBreakCredits() {
        if (pendingForwardBreakCredits.isEmpty() || mc.player == null) return;

        int currentAge = mc.player.age;
        pendingForwardBreakCredits.entrySet().removeIf(entry -> currentAge > entry.getValue().expiresAtAge());
    }

    private ForwardTaskType findActiveForwardMineTaskType(BlockPos pos) {
        if (pos == null || !isForwardSchedulerTick()) return null;

        ForwardRowSchedule row = forwardSchedulerRuntime.rows.peekFirst();
        if (row == null) return null;

        ForwardTask task = row.mineQueue.get(pos);
        return task == null ? null : task.type;
    }

    private void createOrRefreshForwardBreakCredit(ForwardTaskType type, BlockPos pos, BlockState originalState) {
        if (type == null || pos == null || originalState == null || mc.player == null) return;
        if (!isForwardSchedulerTick() || !isForwardBreakCreditEligibleType(type)) return;

        pruneExpiredForwardBreakCredits();
        pendingForwardBreakCredits.put(
            pos.toImmutable(),
            new ForwardBreakStatCredit(type, originalState, mc.player.age + FORWARD_BREAK_CREDIT_TTL_TICKS)
        );
        statsDebug(
            "scheduler-mine-credit-create type=%s pos=%s before=%s expiresAtAge=%d",
            type,
            formatBlockPos(pos),
            originalState.getBlock(),
            mc.player.age + FORWARD_BREAK_CREDIT_TTL_TICKS
        );
    }

    private void createOrRefreshForwardBreakCreditIfEligible(BlockPos pos, BlockState originalState) {
        ForwardTaskType type = findActiveForwardMineTaskType(pos);
        if (type == null) return;
        createOrRefreshForwardBreakCredit(type, pos, originalState);
    }

    private void cancelPendingForwardBreakCredit(BlockPos pos) {
        if (pos == null) return;
        pruneExpiredForwardBreakCredits();
        ForwardBreakStatCredit removed = pendingForwardBreakCredits.remove(pos.toImmutable());
        if (removed != null) {
            statsDebug(
                "scheduler-mine-credit-cancel type=%s pos=%s before=%s",
                removed.type(),
                formatBlockPos(pos),
                removed.originalState().getBlock()
            );
        }
    }

    private void releaseActiveDoubleMineRuntime(boolean abortNormalMining) {
        if (normalMining != null) {
            cancelPendingForwardBreakCredit(normalMining.blockPos);
            if (abortNormalMining && mc.getNetworkHandler() != null) {
                mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.ABORT_DESTROY_BLOCK, normalMining.blockPos, normalMining.direction));
            }
            normalMining = null;
        }

        if (packetMining != null) {
            cancelPendingForwardBreakCredit(packetMining.blockPos);
            packetMining = null;
        }

        DoubleMineBlock.rateLimited = false;
    }

    private boolean consumePendingForwardBreakCredit(BlockPos pos) {
        if (pos == null || mc.world == null) return false;

        pruneExpiredForwardBreakCredits();

        BlockPos immutablePos = pos.toImmutable();
        ForwardBreakStatCredit credit = pendingForwardBreakCredits.get(immutablePos);
        if (credit == null) return false;
        if (mc.world.getBlockState(immutablePos).equals(credit.originalState())) return false;

        pendingForwardBreakCredits.remove(immutablePos);
        boolean eligible = credit.type() != null && isSchedulerCountableFrontMineTask(credit.type());
        boolean alreadyCounted = countedBrokenForwardPositions.contains(immutablePos);
        Block afterBlock = mc.world.getBlockState(immutablePos).getBlock();
        boolean countedNow = recordBrokenForwardPosition(immutablePos);
        logSchedulerStatsDecision("scheduler-mine-delayed", credit.type(), immutablePos, credit.originalState().getBlock(), afterBlock, eligible, alreadyCounted, countedNow);
        return true;
    }

    private ModuleManager getModuleManager() {
        if (!MODULE_MANAGER_INTEGRATION_ENABLED) return null;

        try {
            return Modules.get().get(ModuleManager.class);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private void beginCenterSpeedModuleManagerLease(String reason) {
        ModuleManager manager = getModuleManager();
        if (manager == null || !manager.isActive()) return;
        manager.beginHighwayBuilderCenterSpeedLease(reason);
    }

    private void endCenterSpeedModuleManagerLease(String reason) {
        ModuleManager manager = getModuleManager();
        if (manager == null || !manager.isActive()) return;
        manager.endHighwayBuilderCenterSpeedLease(reason);
    }

    private void beginEChestBreakSpeedModuleManagerLease(String reason) {
        ModuleManager manager = getModuleManager();
        if (manager == null || !manager.isActive()) return;
        manager.beginHighwayBuilderEChestBreakSpeedLease(reason);
    }

    private void endEChestBreakSpeedModuleManagerLease(String reason) {
        ModuleManager manager = getModuleManager();
        if (manager == null || !manager.isActive()) return;
        manager.endHighwayBuilderEChestBreakSpeedLease(reason);
    }

    private boolean isSchedulerCountableFrontMineTask(ForwardTaskType type) {
        return type == ForwardTaskType.FRONT_MINE
            || type == ForwardTaskType.FLOOR_MINE
            || type == ForwardTaskType.RAILINGS_MINE
            || type == ForwardTaskType.ABOVE_RAILINGS_MINE;
    }

    private boolean isSchedulerCountableFrontPlaceTask(ForwardTaskType type) {
        return type == ForwardTaskType.CORNER_PLACE
            || type == ForwardTaskType.RAILINGS_PLACE
            || type == ForwardTaskType.FLOOR_PLACE;
    }

    private boolean isLegacyCountableBrokenState(State state) {
        return state == State.MineFront
            || state == State.MineFloor
            || state == State.MineRailings
            || state == State.MineAboveRailings;
    }

    private boolean shouldCountLegacyPlacedPosition(State state, BlockPos pos) {
        if (pos == null || state == null || blockPosProvider == null) return false;

        return switch (state) {
            case PlaceCornerBlock -> iteratorContainsPosition(blockPosProvider.getRailings(-1), pos);
            case PlaceRailings -> iteratorContainsPosition(blockPosProvider.getRailings(0), pos);
            case PlaceFloor -> iteratorContainsPosition(blockPosProvider.getFloor(), pos);
            default -> false;
        };
    }

    private boolean iteratorContainsPosition(MBPIterator iterator, BlockPos target) {
        if (iterator == null || target == null) return false;

        for (MBlockPos pos : iterator) {
            if (target.equals(pos.getBlockPos())) return true;
        }

        return false;
    }

    private void refreshCountedForwardStatPositions() {
        if (countedBrokenForwardPositions.isEmpty() && countedPlacedForwardPositions.isEmpty()) return;
        if (!shouldUseForwardStatsGuard()) return;

        Set<BlockPos> retainedPositions = new HashSet<>();
        collectActiveFrontHorizonPositions(retainedPositions);
        collectBehindReachPositions(retainedPositions);

        countedBrokenForwardPositions.retainAll(retainedPositions);
        countedPlacedForwardPositions.retainAll(retainedPositions);
    }

    private void collectActiveFrontHorizonPositions(Set<BlockPos> positions) {
        if (!shouldUseForwardStatsGuard()) return;

        if (useForwardRowSchedulerMode()) collectSchedulerFrontHorizonPositions(positions);
        else collectLegacyFrontHorizonPositions(positions);
    }

    private void collectSchedulerFrontHorizonPositions(Set<BlockPos> positions) {
        for (ForwardRowSchedule row : forwardSchedulerRuntime.rows) {
            addFrontTemplatePositions(positions, row.mineTemplate);
            addFrontTemplatePositions(positions, row.placeTemplate);
        }
    }

    private void addFrontTemplatePositions(Set<BlockPos> positions, List<ForwardTask> tasks) {
        if (tasks == null) return;
        for (ForwardTask task : tasks) {
            if (!isCountableFrontHorizonTask(task.type)) continue;
            positions.add(task.pos.toImmutable());
        }
    }

    private boolean isCountableFrontHorizonTask(ForwardTaskType type) {
        return isSchedulerCountableFrontMineTask(type) || isSchedulerCountableFrontPlaceTask(type);
    }

    private void collectLegacyFrontHorizonPositions(Set<BlockPos> positions) {
        addIteratorPositions(positions, blockPosProvider.getFront());
        addIteratorPositions(positions, blockPosProvider.getFloor());

        if (railings.get()) {
            addIteratorPositions(positions, blockPosProvider.getRailings(0));
            if (cornerBlock.get()) addIteratorPositions(positions, blockPosProvider.getRailings(-1));
        }

        if (mineAboveRailings.get()) addIteratorPositions(positions, blockPosProvider.getRailings(1));
    }

    private void collectBehindReachPositions(Set<BlockPos> positions) {
        if (blockPosProvider == null || !checkBehind.get()) return;

        addIteratorPositions(positions, blockPosProvider.getBehindFront());
        addIteratorPositions(positions, blockPosProvider.getBehindFloor());

        if (railings.get()) {
            addIteratorPositions(positions, blockPosProvider.getBehindRailings(0));
            if (cornerBlock.get()) addIteratorPositions(positions, blockPosProvider.getBehindRailings(-1));
        }

        if (mineAboveRailings.get()) addIteratorPositions(positions, blockPosProvider.getBehindRailings(1));
    }

    private void addIteratorPositions(Set<BlockPos> positions, MBPIterator iterator) {
        if (iterator == null) return;
        for (MBlockPos pos : iterator) positions.add(pos.getBlockPos().toImmutable());
    }

    private void resetForwardSchedulerRuntime() {
        clearForwardLatchedPlaceSlot();
        clearForwardLatchedMineSlot();
        forwardSchedulerRuntime.reset();
    }

    private void seedForwardSchedulerWindow() {
        resetForwardSchedulerRuntime();
        if (!useForwardRowSchedulerMode() || mc.player == null || mc.world == null || blockPosProvider == null) return;

        ForwardRowSchedule firstRow = buildForwardSeedRow(0);
        forwardSchedulerRuntime.rows.add(firstRow);

        ForwardRowSchedule previous = firstRow;
        for (int i = 1; i < ForwardSchedulerRuntime.LOOKAHEAD_ROWS; i++) {
            ForwardRowSchedule row = shiftForwardRow(previous, i);
            forwardSchedulerRuntime.rows.add(row);
            previous = row;
        }

        forwardSchedulerRuntime.nextRowId = ForwardSchedulerRuntime.LOOKAHEAD_ROWS;
        forwardSchedulerRuntime.lastProgressAtMs = System.currentTimeMillis();
    }

    private void ensureForwardSchedulerWindow() {
        if (!useForwardRowSchedulerMode()) return;
        if (forwardSchedulerRuntime.rows.isEmpty()) {
            seedForwardSchedulerWindow();
            return;
        }

        while (forwardSchedulerRuntime.rows.size() < ForwardSchedulerRuntime.LOOKAHEAD_ROWS) {
            appendForwardSchedulerRow();
        }
    }

    private void appendForwardSchedulerRow() {
        ForwardRowSchedule last = forwardSchedulerRuntime.rows.peekLast();
        if (last == null) {
            seedForwardSchedulerWindow();
            return;
        }

        forwardSchedulerRuntime.rows.addLast(shiftForwardRow(last, forwardSchedulerRuntime.nextRowId++));
    }

    private ForwardRowSchedule buildForwardSeedRow(int rowId) {
        List<ForwardTask> mineTasks = new ArrayList<>();
        List<ForwardTask> placeTasks = new ArrayList<>();
        collectForwardSeedTasks(mineTasks, placeTasks);
        return createForwardRowSchedule(rowId, mineTasks, placeTasks);
    }

    private void collectForwardSeedTasks(List<ForwardTask> mineTasks, List<ForwardTask> placeTasks) {
        addForwardLaneTasks(mineTasks, blockPosProvider.getFront(), ForwardTaskType.FRONT_MINE, 0);
        if (checkBehind.get()) addForwardLaneTasks(mineTasks, blockPosProvider.getBehindFront(), ForwardTaskType.BEHIND_FRONT_MINE, 0);

        if (floor.get() == Floor.Replace) {
            addForwardLaneTasks(mineTasks, blockPosProvider.getFloor(), ForwardTaskType.FLOOR_MINE, 0);
            if (checkBehind.get()) addForwardLaneTasks(mineTasks, blockPosProvider.getBehindFloor(), ForwardTaskType.BEHIND_FLOOR_MINE, 0);
        }

        if (railings.get()) {
            addForwardLaneTasks(mineTasks, blockPosProvider.getRailings(0), ForwardTaskType.RAILINGS_MINE, 0);
            if (checkBehind.get()) addForwardLaneTasks(mineTasks, blockPosProvider.getBehindRailings(0), ForwardTaskType.BEHIND_RAILINGS_MINE, 0);
        }

        if (mineAboveRailings.get()) {
            addForwardLaneTasks(mineTasks, blockPosProvider.getRailings(1), ForwardTaskType.ABOVE_RAILINGS_MINE, 0);
            if (checkBehind.get()) addForwardLaneTasks(mineTasks, blockPosProvider.getBehindRailings(1), ForwardTaskType.BEHIND_ABOVE_RAILINGS_MINE, 0);
        }

        addForwardLaneTasks(placeTasks, blockPosProvider.getLiquids(), ForwardTaskType.LIQUID_PLACE, 0);
        if (railings.get() && cornerBlock.get()) {
            addForwardLaneTasks(placeTasks, blockPosProvider.getRailings(-1), ForwardTaskType.CORNER_PLACE, 0);
            if (checkBehind.get()) addForwardLaneTasks(placeTasks, blockPosProvider.getBehindRailings(-1), ForwardTaskType.BEHIND_CORNER_PLACE, 0);
        }
        if (railings.get()) {
            addForwardLaneTasks(placeTasks, blockPosProvider.getRailings(0), ForwardTaskType.RAILINGS_PLACE, 0);
            if (checkBehind.get()) addForwardLaneTasks(placeTasks, blockPosProvider.getBehindRailings(0), ForwardTaskType.BEHIND_RAILINGS_PLACE, 0);
        }
        addForwardLaneTasks(placeTasks, blockPosProvider.getFloor(), ForwardTaskType.FLOOR_PLACE, 0);
        if (checkBehind.get()) addForwardLaneTasks(placeTasks, blockPosProvider.getBehindFloor(), ForwardTaskType.BEHIND_FLOOR_PLACE, 0);
    }

    private ForwardRowSchedule shiftForwardRow(ForwardRowSchedule previous, int rowId) {
        List<ForwardTask> mineTasks = new ArrayList<>(previous.mineTemplate.size());
        for (ForwardTask task : previous.mineTemplate) mineTasks.add(task.shifted(dir.offsetX, dir.offsetZ));

        List<ForwardTask> placeTasks = new ArrayList<>(previous.placeTemplate.size());
        for (ForwardTask task : previous.placeTemplate) placeTasks.add(task.shifted(dir.offsetX, dir.offsetZ));

        return createForwardRowSchedule(rowId, mineTasks, placeTasks);
    }

    private ForwardRowSchedule createForwardRowSchedule(int rowId, List<ForwardTask> mineTasks, List<ForwardTask> placeTasks) {
        LinkedHashMap<BlockPos, ForwardTask> mineQueue = buildForwardOrderedQueue(mineTasks);
        LinkedHashMap<BlockPos, ForwardTask> placeQueue = buildForwardOrderedQueue(placeTasks);
        ForwardRowSchedule row = new ForwardRowSchedule(rowId, mineTasks, placeTasks, mineQueue, placeQueue, new LinkedHashMap<>(), computeForwardBoundaryProjection(mineTasks, placeTasks));
        refreshForwardActiveRow(row);
        return row;
    }

    private void addForwardLaneTasks(List<ForwardTask> tasks, MBPIterator iterator, ForwardTaskType type, int rowId) {
        if (iterator == null) return;
        for (MBlockPos pos : iterator) {
            BlockPos rowPos = pos.getBlockPos().toImmutable();
            if (rowId > 0) rowPos = rowPos.add(dir.offsetX * rowId, 0, dir.offsetZ * rowId).toImmutable();
            tasks.add(new ForwardTask(rowPos, type));
        }
    }

    private LinkedHashMap<BlockPos, ForwardTask> buildForwardOrderedQueue(List<ForwardTask> tasks) {
        LinkedHashMap<BlockPos, ForwardTask> queue = new LinkedHashMap<>();
        if (tasks.isEmpty()) return queue;

        double minLateral = Double.POSITIVE_INFINITY;
        double maxLateral = Double.NEGATIVE_INFINITY;
        for (ForwardTask task : tasks) {
            double lateral = projectedLateralCoordinate(task.pos);
            minLateral = Math.min(minLateral, lateral);
            maxLateral = Math.max(maxLateral, lateral);
        }

        double center = Double.isFinite(minLateral) && Double.isFinite(maxLateral) ? (minLateral + maxLateral) / 2.0 : 0.0;

        List<ForwardTask> orderedTasks = new ArrayList<>(tasks);
        orderedTasks.sort(Comparator
            .comparingDouble((ForwardTask task) -> Math.abs(projectedLateralCoordinate(task.pos) - center))
            .thenComparingDouble(task -> projectedLateralCoordinate(task.pos))
            .thenComparingInt(task -> task.type.ordinal())
            .thenComparingInt(task -> task.pos.getY())
            .thenComparingInt(task -> task.pos.getX())
            .thenComparingInt(task -> task.pos.getZ()));

        for (ForwardTask task : orderedTasks) queue.putIfAbsent(task.pos, task);
        return queue;
    }

    private double computeForwardBoundaryProjection(List<ForwardTask> mineTasks, List<ForwardTask> placeTasks) {
        double minProjection = Double.POSITIVE_INFINITY;
        double maxProjection = Double.NEGATIVE_INFINITY;

        for (ForwardTask task : mineTasks) {
            if (task.type.isBehind()) continue;
            double projection = projectedForwardCoordinate(task.pos);
            minProjection = Math.min(minProjection, projection);
            maxProjection = Math.max(maxProjection, projection);
        }

        for (ForwardTask task : placeTasks) {
            if (task.type.isBehind()) continue;
            double projection = projectedForwardCoordinate(task.pos);
            minProjection = Math.min(minProjection, projection);
            maxProjection = Math.max(maxProjection, projection);
        }

        if (!Double.isFinite(minProjection) || !Double.isFinite(maxProjection)) {
            return currentForwardProjection() + 1.0;
        }

        double rowCenterProjection = (minProjection + maxProjection) / 2.0;
        return rowCenterProjection - 1.5;
    }

    private double projectedForwardCoordinate(BlockPos pos) {
        return projectedForwardCoordinate(pos.getX() + 0.5, pos.getZ() + 0.5);
    }

    private double projectedForwardCoordinate(double x, double z) {
        double magnitude = directionStepLength();
        if (magnitude <= 0.0) return 0.0;
        return (x * dir.offsetX + z * dir.offsetZ) / magnitude;
    }

    private double projectedLateralCoordinate(BlockPos pos) {
        return projectedLateralCoordinate(pos.getX() + 0.5, pos.getZ() + 0.5);
    }

    private double projectedLateralCoordinate(double x, double z) {
        double magnitude = Math.hypot(rightDir.offsetX, rightDir.offsetZ);
        if (magnitude <= 0.0) return 0.0;
        return (x * rightDir.offsetX + z * rightDir.offsetZ) / magnitude;
    }

    private double currentForwardProjection() {
        return projectedForwardCoordinate(mc.player.getX(), mc.player.getZ());
    }

    private double currentLateralProjection() {
        return projectedLateralCoordinate(mc.player.getX(), mc.player.getZ());
    }

    private double directionStepLength() {
        return Math.hypot(dir.offsetX, dir.offsetZ);
    }

    private ForwardRowSchedule getActiveForwardRow() {
        int promotionsThisTick = 0;
        while (!forwardSchedulerRuntime.rows.isEmpty() && promotionsThisTick < ForwardSchedulerRuntime.LOOKAHEAD_ROWS) {
            ForwardRowSchedule row = forwardSchedulerRuntime.rows.peekFirst();
            refreshForwardActiveRow(row);
            if (!row.isComplete()) return row;

            forwardSchedulerRuntime.rows.removeFirst();
            markForwardSchedulerProgress();
            appendForwardSchedulerRow();
            promotionsThisTick++;
        }

        return null;
    }

    private void refreshForwardActiveRow(ForwardRowSchedule row) {
        if (row == null) return;

        List<ForwardTask> liveMineTasks = new ArrayList<>();
        List<ForwardTask> livePlaceTasks = new ArrayList<>();
        collectForwardSeedTasks(liveMineTasks, livePlaceTasks);
        prependForwardTasks(row.mineQueue, liveMineTasks, this::shouldKeepForwardMineTask);
        prependForwardTasks(row.placeQueue, livePlaceTasks, this::shouldKeepForwardPlaceTask);

        Iterator<Map.Entry<BlockPos, ForwardTask>> mineIt = row.mineQueue.entrySet().iterator();
        while (mineIt.hasNext()) {
            Map.Entry<BlockPos, ForwardTask> entry = mineIt.next();
            if (!shouldKeepForwardMineTask(entry.getValue())) mineIt.remove();
        }

        LinkedHashMap<BlockPos, ForwardTask> deferredConflicts = new LinkedHashMap<>();
        Iterator<Map.Entry<BlockPos, ForwardTask>> placeIt = row.placeQueue.entrySet().iterator();
        while (placeIt.hasNext()) {
            Map.Entry<BlockPos, ForwardTask> entry = placeIt.next();
            ForwardTask task = entry.getValue();
            if (!shouldKeepForwardPlaceTask(task)) {
                placeIt.remove();
                continue;
            }

            if (row.mineQueue.containsKey(task.pos) || hasActiveMineOwnership(task.pos)) {
                placeIt.remove();
                deferredConflicts.putIfAbsent(task.pos, task);
            }
        }

        for (ForwardTask task : deferredConflicts.values()) {
            row.conflictQueue.putIfAbsent(task.pos, task);
        }

        Iterator<Map.Entry<BlockPos, ForwardTask>> conflictIt = row.conflictQueue.entrySet().iterator();
        while (conflictIt.hasNext()) {
            Map.Entry<BlockPos, ForwardTask> entry = conflictIt.next();
            ForwardTask task = entry.getValue();
            if (!shouldKeepForwardPlaceTask(task)) {
                conflictIt.remove();
                continue;
            }

            if (row.placeQueue.containsKey(task.pos)) {
                conflictIt.remove();
            }
        }

        row.frontBoundaryProjection = computeForwardBoundaryProjection(new ArrayList<>(row.mineQueue.values()), combineForwardPlaceTasks(row));
    }

    private void prependForwardTasks(LinkedHashMap<BlockPos, ForwardTask> queue, List<ForwardTask> liveTasks, Predicate<ForwardTask> keepPredicate) {
        if (queue == null || liveTasks.isEmpty()) return;

        LinkedHashMap<BlockPos, ForwardTask> orderedLiveTasks = buildForwardOrderedQueue(liveTasks);
        LinkedHashMap<BlockPos, ForwardTask> merged = new LinkedHashMap<>();

        for (ForwardTask task : orderedLiveTasks.values()) {
            if (!keepPredicate.test(task)) continue;
            if (queue.containsKey(task.pos)) {
                merged.put(task.pos, queue.get(task.pos));
            } else {
                merged.put(task.pos, task);
            }
        }

        for (Map.Entry<BlockPos, ForwardTask> entry : queue.entrySet()) {
            merged.putIfAbsent(entry.getKey(), entry.getValue());
        }

        queue.clear();
        queue.putAll(merged);
    }

    private List<ForwardTask> combineForwardPlaceTasks(ForwardRowSchedule row) {
        List<ForwardTask> tasks = new ArrayList<>(row.placeQueue.size() + row.conflictQueue.size());
        tasks.addAll(row.placeQueue.values());
        tasks.addAll(row.conflictQueue.values());
        return tasks;
    }

    private boolean shouldKeepForwardMineTask(ForwardTask task) {
        if (mc.world == null) return false;
        BlockState state = mc.world.getBlockState(task.pos);
        if (shouldSkipSignBreak(task.pos, state)) return false;
        return safeCanBreak(task.pos, state) && (task.type.mineBlocksToPlace() || !blocksToPlace.get().contains(state.getBlock()));
    }

    private boolean shouldKeepForwardPlaceTask(ForwardTask task) {
        if (mc.world == null) return false;
        BlockState state = mc.world.getBlockState(task.pos);

            if (task.type.liquids()) return !state.getFluidState().isEmpty();

            if ((task.type == ForwardTaskType.CORNER_PLACE || task.type == ForwardTaskType.BEHIND_CORNER_PLACE)
                && !mc.world.getBlockState(task.pos.up()).isReplaceable()) return false;
            return BlockUtils.canPlace(task.pos);
        }

    private boolean hasActiveMineOwnership(BlockPos pos) {
        return (normalMining != null && pos.equals(normalMining.blockPos)) || (packetMining != null && pos.equals(packetMining.blockPos));
    }

    private void markForwardSchedulerProgress() {
        forwardSchedulerRuntime.lastProgressAtMs = System.currentTimeMillis();
        forwardSchedulerRuntime.recoveryRetries = 0;
    }

    private void startForwardSchedulerRecovery() {
        forwardSchedulerRuntime.recoveryRetries++;
        forwardSchedulerRuntime.backstepping = true;
        forwardSchedulerRuntime.backstepStartProjection = currentForwardProjection();
        input.stop();
        logForwardSchedulerStatus("recovery-start", forwardSchedulerRuntime.rows.peekFirst(), "stuck watchdog fired", true);
    }

    private void handleForwardSchedulerRecovery() {
        mc.player.setPitch(20);
        mc.player.setYaw(dir.opposite().yaw);
        input.setState(true, false, false, false, false, false, false);

        logForwardSchedulerStatus("recovery", forwardSchedulerRuntime.rows.peekFirst(), "backstepping toward center", false);

        double progress = forwardSchedulerRuntime.backstepStartProjection - currentForwardProjection();
        if (progress >= 2.0) {
            input.stop();
            forwardSchedulerRuntime.backstepping = false;
            seedForwardSchedulerWindow();
            logForwardSchedulerStatus("recovery-finish", forwardSchedulerRuntime.rows.peekFirst(), "backstep complete, recentering", true);
            setState(State.Center, State.Forward);
        }
    }

    private void resetForwardDriftTarget() {
        forwardDriftRecoveryActive = false;
        if (mc == null || mc.player == null || rightDir == null) {
            forwardDriftTargetValid = false;
            return;
        }

        forwardDriftTargetLateral = projectedLateralCoordinate(mc.player.getBlockPos());
        forwardDriftTargetValid = true;
    }

    private void clearForwardDriftRuntime() {
        forwardDriftTargetValid = false;
        forwardDriftRecoveryActive = false;
        if (input != null) {
            input.left(false);
            input.right(false);
        }
    }

    private void applyForwardDriftCorrection() {
        if (!isThmSpeedControllerActive() || state != State.Forward || !forwardDriftTargetValid || rightDir == null) {
            if (input != null) {
                input.left(false);
                input.right(false);
            }
            return;
        }

        double drift = currentLateralProjection() - forwardDriftTargetLateral;
        double absDrift = Math.abs(drift);
        if (!forwardDriftRecoveryActive && absDrift >= THM_FORWARD_DRIFT_RECOVERY_START) {
            forwardDriftRecoveryActive = true;
        }
        if (forwardDriftRecoveryActive && absDrift <= THM_FORWARD_DRIFT_RECOVERY_STOP) {
            forwardDriftRecoveryActive = false;
        }

        if (!forwardDriftRecoveryActive) {
            input.left(false);
            input.right(false);
            return;
        }

        input.left(drift > 0.0);
        input.right(drift < 0.0);
    }

    private void applyForwardSchedulerMovement(ForwardRowSchedule activeRow) {
        mc.player.setPitch(20);
        mc.player.setYaw(dir.yaw);

        if (activeRow == null || activeRow.isComplete() || currentForwardProjection() < activeRow.frontBoundaryProjection) {
            logForwardSchedulerStatus("move", activeRow, activeRow == null ? "no active row" : "moving toward boundary", true);
            input.setState(true, false, false, false, false, false, false);
            applyForwardDriftCorrection();
        } else {
            input.stop();
            logForwardSchedulerStatus("hold", activeRow, "holding at boundary waiting for row completion", false);
        }
    }

    private void tickForwardScheduler() {
        if (destroyCrystalTraps.get() && isForwardCrystalTrap()) {
            debugStateTransition(State.DefuseCrystalTraps, "crystal-trap");
            setState(State.DefuseCrystalTraps);
            return;
        }

        ensureForwardSchedulerWindow();

        if (forwardSchedulerRuntime.backstepping) {
            handleForwardSchedulerRecovery();
            return;
        }

        ForwardRowSchedule activeRow = getActiveForwardRow();
        refreshCountedForwardStatPositions();
        logForwardSchedulerStatus("tick", activeRow, "pre-work", false);

        boolean minedChangedWorld = runForwardMineWork(activeRow);
        if (minedChangedWorld) logForwardSchedulerStatus("mine", activeRow, "mine changed world", true);
        if (state != State.Forward) return;

        // In paket mode, skip place work while mine tasks are pending so the UpdateSelectedSlot
        // packet (pickaxe switch) and the mine packets are not dropped by the packet rate limiter.
        // offhand-build has no slot switch to protect (pickaxe stays in the main hand, obsidian in the
        // offhand), so mining and placing can share the tick.
        boolean skipPlaceForPaketMine = packetBuild.get()
            && !placeFromOffhand()
            && activeRow != null
            && !activeRow.mineQueue.isEmpty();
        boolean skipPlaceForSafetyPlacement = safetyPlacedThisTick;

        boolean placedChangedWorld = false;
        if (!skipPlaceForPaketMine && !skipPlaceForSafetyPlacement) {
            placedChangedWorld = runForwardPlaceWork(activeRow);
            if (placedChangedWorld) logForwardSchedulerStatus("place", activeRow, "place changed world", true);
            if (state != State.Forward) return;
            if (packetBuild.get() && packetBuildLookahead.get()) {
                boolean skippedActive = false;
                for (ForwardRowSchedule lookaheadRow : forwardSchedulerRuntime.rows) {
                    if (!skippedActive) { skippedActive = true; continue; }
                    if (state != State.Forward) break;
                    runForwardPlaceWork(lookaheadRow);
                }
            }
            if (state != State.Forward) return;
        }

        boolean conflictChangedWorld = runForwardConflictWork(activeRow);
        if (conflictChangedWorld) logForwardSchedulerStatus("conflict", activeRow, "conflict changed world", true);
        if (state != State.Forward) return;

        if (minedChangedWorld || placedChangedWorld || conflictChangedWorld) {
            markForwardSchedulerProgress();
            activeRow = getActiveForwardRow();
        }

        if (activeRow != null && !activeRow.isComplete()) {
            long now = System.currentTimeMillis();
            if (forwardSchedulerRuntime.lastProgressAtMs == 0L) forwardSchedulerRuntime.lastProgressAtMs = now;
            if (now - forwardSchedulerRuntime.lastProgressAtMs >= ForwardSchedulerRuntime.STUCK_TIMEOUT_MS) {
                if (forwardSchedulerRuntime.recoveryRetries >= ForwardSchedulerRuntime.MAX_RECOVERY_RETRIES) {
                    logForwardSchedulerStatus("hard-fail", activeRow, "scheduler stalled past retry limit", true);
                    error("forward scheduler stalled");
                    return;
                }

                startForwardSchedulerRecovery();
                handleForwardSchedulerRecovery();
                return;
            }
        }

        applyForwardSchedulerMovement(activeRow);
    }

    private boolean runForwardMineWork(ForwardRowSchedule row) {
        if (row == null || row.mineQueue.isEmpty()) return false;
        if (forwardMineCount >= currentMineActionsThisTick()) return false;

        if (doubleMine.get()) {
            ArrayDeque<BlockPos> toDoubleMine = new ArrayDeque<>();
            for (ForwardTask task : row.mineQueue.values()) {
                if (forwardMineCount >= currentMineActionsThisTick()) break;
                if (!shouldKeepForwardMineTask(task)) continue;

                BlockState state = mc.world.getBlockState(task.pos);
                if (
                    safeCanBreak(task.pos, state)
                        && (task.type.mineBlocksToPlace() || !blocksToPlace.get().contains(state.getBlock()))
                        && !safeCanInstaBreakForBreaking(task.pos)
                        && (!Modules.get().get(SpeedMine.class).instamine() || state.calcBlockBreakingDelta(mc.player, mc.world, task.pos) <= 0.5)
                        && (normalMining == null || !task.pos.equals(normalMining.blockPos))
                        && (packetMining == null || !task.pos.equals(packetMining.blockPos))
                ) {
                    toDoubleMine.add(task.pos);
                }
            }

            if (!toDoubleMine.isEmpty()) {
                int slot = resolveForwardMineSlot(mc.world.getBlockState(toDoubleMine.peek()), false);
                if (slot == -1) return false;
                if (state != State.Forward) return false;
                if (slot != mc.player.getInventory().getSelectedSlot()) InvUtils.swap(slot, false);
                State.Forward.doubleMine(this, toDoubleMine);
            }

            if (normalMining != null || packetMining != null) {
                int slot = resolveForwardMineSlot(normalMining != null ? normalMining.blockState : packetMining.blockState, false);
                if (slot == -1) return false;
                if (state != State.Forward) return false;
                if (slot != mc.player.getInventory().getSelectedSlot()) InvUtils.swap(slot, false);
                return false;
            }
        }

        boolean changedWorld = false;
        List<ForwardTask> snapshot = new ArrayList<>(row.mineQueue.values());
        for (ForwardTask task : snapshot) {
            if (forwardMineCount >= currentMineActionsThisTick() || breakTimer > 0) break;
            if (!shouldKeepForwardMineTask(task)) {
                row.mineQueue.remove(task.pos);
                continue;
            }

            BlockState state = mc.world.getBlockState(task.pos);
            int mineCost = mineBudgetCost(state);
            if (forwardMineCount + mineCost > currentMineActionsThisTick()) break;
            int slot = resolveForwardMineSlot(state, false);
            if (slot == -1) return changedWorld;
            if (this.state != State.Forward) return changedWorld;

            boolean multiBreak = currentMineActionsThisTick() > 1 && safeCanInstaBreakForBreaking(task.pos);
            if (safeCanBreak(task.pos, state)) {
                Block blockBefore = state.getBlock();
                boolean silentAllowed = canUseSilentForwardToolSwap(state, slot);
                boolean swapped = false;
                if (silentAllowed) {
                    if (slot != mc.player.getInventory().getSelectedSlot()) {
                        if (!InvUtils.swap(slot, true)) continue;
                        swapped = true;
                    }
                } else if (slot != mc.player.getInventory().getSelectedSlot()) {
                    InvUtils.swap(slot, false);
                }

                try {
                    if (!tryMineBlock(task.pos, state, false, () -> createOrRefreshForwardBreakCredit(task.type, task.pos, state))) continue;
                } finally {
                    if (swapped) InvUtils.swapBack();
                }

                forwardMineCount += mineCost;

                if (mc.world.getBlockState(task.pos).getBlock() != blockBefore) {
                    BlockPos countedPos = task.pos.toImmutable();
                    boolean eligible = isSchedulerCountableFrontMineTask(task.type);
                    boolean alreadyCounted = countedBrokenForwardPositions.contains(countedPos);
                    recordConfirmedBrokenBlockForStats(task.pos, task.type);
                    boolean countedNow = !alreadyCounted && countedBrokenForwardPositions.contains(countedPos);
                    logSchedulerStatsDecision("scheduler-mine-inline", task.type, countedPos, blockBefore, mc.world.getBlockState(task.pos).getBlock(), eligible, alreadyCounted, countedNow);
                    row.mineQueue.remove(task.pos);
                    changedWorld = true;
                }

                if (!multiBreak) break;
            }
        }

        return changedWorld;
    }

    private boolean isForwardCrystalTrap() {
        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof EndCrystalEntity endCrystal)) continue;
            if (PlayerUtils.isWithin(endCrystal, 12) || !PlayerUtils.isWithin(endCrystal, 24)) continue;
            if (ignoreCrystals.contains(endCrystal)) continue;

            Vec3d vec1 = new Vec3d(0, 0, 0);
            Vec3d vec2 = new Vec3d(0, 0, 0);

            ((IVec3d) vec1).meteor$set(mc.player.getX(), mc.player.getY() + mc.player.getStandingEyeHeight(), mc.player.getZ());
            ((IVec3d) vec2).meteor$set(entity.getX(), entity.getY() + 0.5, entity.getZ());
            return mc.world.raycast(new RaycastContext(vec1, vec2, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player)).getType() == HitResult.Type.MISS;
        }

        return false;
    }

    private boolean runForwardPlaceWork(ForwardRowSchedule row) {
        return runForwardPlaceQueue(row, row == null ? null : row.placeQueue, false);
    }

    private boolean runForwardConflictWork(ForwardRowSchedule row) {
        return runForwardPlaceQueue(row, row == null ? null : row.conflictQueue, true);
    }

    private boolean runForwardPlaceQueue(ForwardRowSchedule row, LinkedHashMap<BlockPos, ForwardTask> queue, boolean conflictQueue) {
        if (row == null || queue == null || queue.isEmpty()) return false;

        boolean changedWorld = false;
        List<ForwardTask> snapshot = new ArrayList<>(queue.values());
        Vec3d eyePos = mc.player.getEyePos();
        snapshot.sort(Comparator.comparingDouble(task -> task.pos.getSquaredDistance(eyePos)));
        for (ForwardTask task : snapshot) {
            if (forwardPlaceCount >= currentPlaceActionsThisTick() || placeTimer > 0) break;
            if (!shouldKeepForwardPlaceTask(task)) {
                queue.remove(task.pos);
                continue;
            }

            if (row.mineQueue.containsKey(task.pos) || hasActiveMineOwnership(task.pos)) continue;
            if (!RangeUtils.isInRange(placeRange.get(), task.pos)) continue;

            int slot = resolveForwardPlaceSlot(task);
            if (slot == -1) return changedWorld;
            if (this.state != State.Forward) return changedWorld;

            BlockState stateBefore = mc.world.getBlockState(task.pos);
            boolean placedThisTick = tryForwardPlaceBlock(task.pos, slot);
            if (!placedThisTick) continue;

            forwardPlaceCount++;
            revalidateForwardLatchedPlaceSlot(task);
            boolean worldChanged = !mc.world.getBlockState(task.pos).equals(stateBefore);
            if (worldChanged || packetBuild.get()) {
                BlockPos countedPos = task.pos.toImmutable();
                boolean eligible = isSchedulerCountableFrontPlaceTask(task.type);
                boolean alreadyCounted = countedPlacedForwardPositions.contains(countedPos);
                recordPlacedBlockForStats(task.pos, task.type);
                boolean countedNow = !alreadyCounted && countedPlacedForwardPositions.contains(countedPos);
                logSchedulerStatsDecision(conflictQueue ? "scheduler-conflict-place" : "scheduler-place", task.type, countedPos, stateBefore.getBlock(), mc.world.getBlockState(task.pos).getBlock(), eligible, alreadyCounted, countedNow);
            }

            if (!shouldKeepForwardPlaceTask(task) || worldChanged) {
                queue.remove(task.pos);
                changedWorld = true;
            }

            if (currentPlaceActionsThisTick() == 1) break;
            if (conflictQueue) break;
        }

        return changedWorld;
    }

    private enum ForwardTaskType {
        FRONT_MINE(true, true, false, false),
        BEHIND_FRONT_MINE(true, true, false, true),
        FLOOR_MINE(true, false, false, false),
        BEHIND_FLOOR_MINE(true, false, false, true),
        RAILINGS_MINE(true, false, false, false),
        BEHIND_RAILINGS_MINE(true, false, false, true),
        ABOVE_RAILINGS_MINE(true, true, false, false),
        BEHIND_ABOVE_RAILINGS_MINE(true, true, false, true),
        LIQUID_PLACE(false, false, true, false),
        CORNER_PLACE(false, false, true, false),
        BEHIND_CORNER_PLACE(false, false, true, true),
        RAILINGS_PLACE(false, false, false, false),
        BEHIND_RAILINGS_PLACE(false, false, false, true),
        FLOOR_PLACE(false, false, false, false),
        BEHIND_FLOOR_PLACE(false, false, false, true);

        private final boolean mine;
        private final boolean mineBlocksToPlace;
        private final boolean prioritizesTrash;
        private final boolean behind;

        ForwardTaskType(boolean mine, boolean mineBlocksToPlace, boolean prioritizesTrash, boolean behind) {
            this.mine = mine;
            this.mineBlocksToPlace = mineBlocksToPlace;
            this.prioritizesTrash = prioritizesTrash;
            this.behind = behind;
        }

        public boolean mineBlocksToPlace() {
            return mineBlocksToPlace;
        }

        public boolean liquids() {
            return this == LIQUID_PLACE;
        }

        public boolean prioritizesTrash() {
            return prioritizesTrash;
        }

        public boolean isBehind() {
            return behind;
        }
    }

    private static final class ForwardTask {
        private final BlockPos pos;
        private final ForwardTaskType type;

        private ForwardTask(BlockPos pos, ForwardTaskType type) {
            this.pos = pos;
            this.type = type;
        }

        private ForwardTask shifted(int offsetX, int offsetZ) {
            return new ForwardTask(pos.add(offsetX, 0, offsetZ).toImmutable(), type);
        }
    }

    private static final class ForwardRowSchedule {
        private final int rowId;
        private final List<ForwardTask> mineTemplate;
        private final List<ForwardTask> placeTemplate;
        private final LinkedHashMap<BlockPos, ForwardTask> mineQueue;
        private final LinkedHashMap<BlockPos, ForwardTask> placeQueue;
        private final LinkedHashMap<BlockPos, ForwardTask> conflictQueue;
        private double frontBoundaryProjection;

        private ForwardRowSchedule(int rowId, List<ForwardTask> mineTemplate, List<ForwardTask> placeTemplate, LinkedHashMap<BlockPos, ForwardTask> mineQueue, LinkedHashMap<BlockPos, ForwardTask> placeQueue, LinkedHashMap<BlockPos, ForwardTask> conflictQueue, double frontBoundaryProjection) {
            this.rowId = rowId;
            this.mineTemplate = Collections.unmodifiableList(new ArrayList<>(mineTemplate));
            this.placeTemplate = Collections.unmodifiableList(new ArrayList<>(placeTemplate));
            this.mineQueue = mineQueue;
            this.placeQueue = placeQueue;
            this.conflictQueue = conflictQueue;
            this.frontBoundaryProjection = frontBoundaryProjection;
        }

        private boolean isComplete() {
            return mineQueue.isEmpty() && placeQueue.isEmpty() && conflictQueue.isEmpty();
        }
    }

    private static final class ForwardSchedulerRuntime {
        private static final int LOOKAHEAD_ROWS = 5;
        private static final long STUCK_TIMEOUT_MS = 30_000L;
        private static final int MAX_RECOVERY_RETRIES = 2;

        private final ArrayDeque<ForwardRowSchedule> rows = new ArrayDeque<>();
        private int nextRowId;
        private long lastProgressAtMs;
        private int recoveryRetries;
        private boolean backstepping;
        private double backstepStartProjection;

        private void reset() {
            rows.clear();
            nextRowId = 0;
            lastProgressAtMs = 0L;
            recoveryRetries = 0;
            backstepping = false;
            backstepStartProjection = 0.0;
        }
    }

    private enum State {
        Center {
            private static final int RECENTER_TIMEOUT_TICKS = 20 * 20;
            private int timeoutTicks;

            @Override
            protected void start(HighwayBuilderTHM b) {
                timeoutTicks = RECENTER_TIMEOUT_TICKS;

                if (b.centerMode.get() == CenterMode.Teleport) {
                    b.resolveCenterTeleportTarget();
                    if (b.tryTeleportToCenterTarget("center-start")) b.setState(b.lastState);
                    return;
                }

                b.clearCenterTeleportTarget();
                b.applyCenterSpeedOverrideIfPossible("center-start");
                if (b.mc.player.getEntityPos().isInRange(Vec3d.ofBottomCenter(b.mc.player.getBlockPos()), 0.1)) {
                    stopWalk(b);
                }
            }

            @Override
            protected void tick(HighwayBuilderTHM b) {
                if (b.centerMode.get() == CenterMode.Teleport) {
                    b.tickCenterTeleportLogCooldown();

                    if (timeoutTicks > 0) timeoutTicks--;
                    else {
                        b.stopCenterTeleportMotion();
                        b.logCenterTeleportBlocked("target still not centered after center timeout", b.resolveCenterTeleportTarget());
                        b.setState(Center, b.lastState);
                        return;
                    }

                    if (b.tryTeleportToCenterTarget("center-tick")) b.setState(b.lastState);
                    return;
                }

                b.clearCenterTeleportTarget();
                if (!b.centerSpeedOverrideActive) b.applyCenterSpeedOverrideIfPossible("center-walk-tick");

                if (timeoutTicks > 0) timeoutTicks--;
                else {
                    restartWalk(b);
                    return;
                }

                // There is probably a much better way to do this
                double x = Math.abs(b.mc.player.getX() - (int) b.mc.player.getX()) - 0.5;
                double z = Math.abs(b.mc.player.getZ() - (int) b.mc.player.getZ()) - 0.5;

                boolean isX = Math.abs(x) <= 0.1;
                boolean isZ = Math.abs(z) <= 0.1;

                if (isX && isZ) {
                    stopWalk(b);
                }
                else {
                    b.mc.player.setYaw(0);

                    if (!isZ) {
                        b.input.forward(z < 0);
                        b.input.backward(z > 0);

                        if (b.mc.player.getZ() < 0) {
                            boolean forward = b.input.isForward();
                            b.input.forward(b.input.isBackward());
                            b.input.backward(forward);
                        }
                    }

                    if (!isX) {
                        b.input.right(x > 0);
                        b.input.left(x < 0);

                        if (b.mc.player.getX() < 0) {
                            boolean right = b.input.isRight();
                            b.input.right(b.input.isLeft());
                            b.input.left(right);
                        }
                    }

                    if (!b.useThmSpeed.get()) b.input.sneak(true);
                }
            }

            private void stopWalk(HighwayBuilderTHM b) {
                b.input.stop();
                b.mc.player.setVelocity(0, 0, 0);
                b.restoreCenterSpeedIfOwned("center-stop");
                b.mc.player.setPosition(HighwayBuilderTHM.getCenteredBlockCoordinate(b.mc.player.getX()), b.mc.player.getY(), HighwayBuilderTHM.getCenteredBlockCoordinate(b.mc.player.getZ()));
                b.setState(b.lastState);
            }

            private void restartWalk(HighwayBuilderTHM b) {
                b.input.stop();
                b.mc.player.setVelocity(0, 0, 0);
                b.restoreCenterSpeedIfOwned("center-timeout-restart");
                b.restockDebug("Center/Speed timeout restart triggered (ticks=%d, target=%s, lastReason=%s).", RECENTER_TIMEOUT_TICKS, b.stateName(b.lastState), b.centerSpeedLastReason);
                b.setState(Center, b.lastState);
            }
        },

        Forward {
            @Override
            protected void start(HighwayBuilderTHM b) {
                b.mc.player.setPitch(20);
                if (b.state == Forward) b.mc.player.setYaw(b.dir.yaw);
                b.resetForwardDriftTarget();
                if (b.useForwardRowSchedulerMode()) {
                    b.seedForwardSchedulerWindow();
                }
            }

            @Override
            protected void tick(HighwayBuilderTHM b) {
                if (b.useForwardRowSchedulerMode()) {
                    b.tickForwardScheduler();
                    return;
                }

                checkTasks(b);
                b.mc.player.setPitch(20);
                if (b.state == Forward) {
                    b.input.forward(true); // Move
                    b.applyForwardDriftCorrection();
                }
            }

            private void checkTasks(HighwayBuilderTHM b) {
                if (b.destroyCrystalTraps.get() && isCrystalTrap(b)) {
                    b.debugStateTransition(DefuseCrystalTraps, "crystal-trap");
                    b.setState(DefuseCrystalTraps);
                }
                else if (needsToPlace(b, b.blockPosProvider.getLiquids(), true)) {
                    b.debugStateTransition(FillLiquids, "fill-liquids");
                    b.setState(FillLiquids);
                }
                else if (needsToMine(b, b.blockPosProvider.getFront(), true)) {
                    b.debugStateTransition(MineFront, "mine-front");
                    b.setState(MineFront);
                }
                else if (b.checkBehind.get() && needsToMine(b, b.blockPosProvider.getBehindFront(), true)) {
                    b.debugStateTransition(MineBehind, "mine-behind");
                    b.setState(MineBehind);
                }
                else if (b.floor.get() == Floor.Replace && needsToMine(b, b.blockPosProvider.getFloor(), false)) {
                    b.debugStateTransition(MineFloor, "mine-floor");
                    b.setState(MineFloor);
                }
                else if (b.checkBehind.get() && b.floor.get() == Floor.Replace && needsToMine(b, b.blockPosProvider.getBehindFloor(), false)) {
                    b.debugStateTransition(MineBehindFloor, "mine-behind-floor");
                    b.setState(MineBehindFloor);
                }
                else if (b.railings.get() && needsToMine(b, b.blockPosProvider.getRailings(0), false)) {
                    b.debugStateTransition(MineRailings, "mine-railings");
                    b.setState(MineRailings);
                }
                else if (b.checkBehind.get() && b.railings.get() && needsToMine(b, b.blockPosProvider.getBehindRailings(0), false)) {
                    b.debugStateTransition(MineBehindRailings, "mine-behind-railings");
                    b.setState(MineBehindRailings);
                }
                else if (b.mineAboveRailings.get() && needsToMine(b, b.blockPosProvider.getRailings(1), true)) {
                    b.debugStateTransition(MineAboveRailings, "mine-above-railings");
                    b.setState(MineAboveRailings);
                }
                else if (b.checkBehind.get() && b.mineAboveRailings.get() && needsToMine(b, b.blockPosProvider.getBehindRailings(1), true)) {
                    b.debugStateTransition(MineBehindAboveRailings, "mine-behind-above-railings");
                    b.setState(MineBehindAboveRailings);
                }
                else if (b.railings.get() && needsToPlace(b, b.blockPosProvider.getRailings(0, b.checkBehind.get()), false)) {
                    if (b.cornerBlock.get() && needsToPlace(b, b.blockPosProvider.getRailings(-1, b.checkBehind.get()), false)) {
                        b.debugStateTransition(PlaceCornerBlock, "place-corner");
                        b.setState(PlaceCornerBlock);
                    } else {
                        b.debugStateTransition(PlaceRailings, "place-railings");
                        b.setState(PlaceRailings);
                    }
                }
                else if (needsToPlace(b, b.blockPosProvider.getFloor(b.checkBehind.get()), false)) {
                    b.debugStateTransition(PlaceFloor, "place-floor");
                    b.setState(PlaceFloor);
                }
            }

            private boolean needsToMine(HighwayBuilderTHM b, MBPIterator it, boolean mineBlocksToPlace) {
                for (MBlockPos pos : it) {
                    if (b.canMine(pos, mineBlocksToPlace)) return true;
                }

                return false;
            }

            private boolean needsToPlace(HighwayBuilderTHM b, MBPIterator it, boolean liquids) {
                for (MBlockPos pos : it) {
                    if (b.canPlace(pos, liquids)) return true;
                }

                return false;
            }

            private boolean isCrystalTrap(HighwayBuilderTHM b) {
                for (Entity entity : b.mc.world.getEntities()) {
                    if (!(entity instanceof EndCrystalEntity endCrystal)) continue;
                    if (PlayerUtils.isWithin(endCrystal, 12) || !PlayerUtils.isWithin(endCrystal, 24)) continue;
                    if (b.ignoreCrystals.contains(endCrystal)) continue;

                    Vec3d vec1 = new Vec3d(0, 0, 0);
                    Vec3d vec2 = new Vec3d(0, 0, 0);

                    ((IVec3d) vec1).meteor$set(b.mc.player.getX(), b.mc.player.getY() + b.mc.player.getStandingEyeHeight(), b.mc.player.getZ());
                    ((IVec3d) vec2).meteor$set(entity.getX(), entity.getY() + 0.5, entity.getZ());
                    return b.mc.world.raycast(new RaycastContext(vec1, vec2, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, b.mc.player)).getType() == HitResult.Type.MISS;
                }

                return false;
            }
        },

        WaitForRestockBlockadeTeardown {
            @Override
            protected void start(HighwayBuilderTHM b) {
                b.input.stop();
            }

            @Override
            protected void tick(HighwayBuilderTHM b) {
                if (b.restockTask.tasksInactive() && b.restockTask.hasPendingRestockTasks()) {
                    if (b.restockTask.advanceToPendingTask()) {
                        b.restockDebug("WaitForRestockBlockadeTeardown advanced pending task before teardown began.");
                        b.setState(Restock, Restock);
                    }
                    return;
                }

                if (b.restockTask.shouldBeginDeferredBlockadeTeardown()) {
                    b.setState(MineShulkerBlockade, Restock);
                    return;
                }

                if (b.restockTask.shouldFinishEmptyBlockadeTeardownWait()) {
                    b.finishEmptyRestockTeardownWait("empty-teardown-wait");
                    return;
                }

                b.input.stop();
            }
        },

        ReLevel {
            private final BlockPos.Mutable pos = new BlockPos.Mutable();
            private BlockPos startPos;
            private int timer = 30;

            @Override
            protected void start(HighwayBuilderTHM b) {
                startPos = BlockPos.ofFloored(b.start);
            }

            @Override
            protected void tick(HighwayBuilderTHM b) {
                Vec3d vec = b.mc.player.getEntityPos().add(b.mc.player.getVelocity()).add(0, -0.75, 0);
                pos.set(b.mc.player.getBlockX(), vec.y, b.mc.player.getBlockZ());

                if (pos.getY() >= b.mc.player.getBlockPos().getY()) {
                    pos.setY(b.mc.player.getBlockPos().getY() - 1);
                }

                if (pos.getY() >= startPos.getY()) pos.setY(startPos.getY() - 1);

                if (b.mc.player.getY() > b.start.y - 0.5 && !b.mc.world.getBlockState(pos).isReplaceable()) {
                    b.input.jump(false);

                    if (timer > 0) timer--;
                    else {
                        b.setState(Forward);
                        timer = 30;
                    }

                    return;
                }

                if (b.placeTimer > 0) return;

                if (timer < 30) timer = 30;
                b.input.jump(true);

                int slot = -1;
                if (pos.getY() == startPos.down().getY()) {
                    // we would prefer the block flush with the highway to be an appropriate placement block, not trash
                    slot = findAndMoveToHotbar(b, itemStack -> itemStack.getItem() instanceof BlockItem blockItem && b.blocksToPlace.get().contains(blockItem.getBlock()));
                }

                if (slot == -1) {
                    slot = findAcceptablePlacementBlock(b);
                    if (slot == -1) return;
                }

                if (BlockUtils.place(pos.toImmutable(), Hand.MAIN_HAND, slot, b.rotation.get().place, 100, true, true, true)) {
                    if (b.renderPlace.get()) b.placeTrail.add(BlockPos.asLong(pos.getX(), pos.getY(), pos.getZ()));
                    b.placeTimer = b.placeDelay.get();
                }
            }

            private int findAcceptablePlacementBlock(HighwayBuilderTHM b) {
                // still should prioritise trash
                int slot = findAndMoveToHotbar(b, itemStack -> {
                    if (!(itemStack.getItem() instanceof BlockItem)) return false;
                    return b.isDroppableTrashStack(itemStack);
                });

                // next we prioritise placement blocks
                if (slot == -1) slot = findAndMoveToHotbar(b, itemStack -> {
                    if (!(itemStack.getItem() instanceof BlockItem bi)) return false;
                    return b.blocksToPlace.get().contains(bi.getBlock());
                });

                // falling is an emergency; in this case only, we allow access to any whole block in your inventory
                return slot != -1 ? slot : findAndMoveToHotbar(b, itemStack -> {
                    if (!(itemStack.getItem() instanceof BlockItem bi)) return false;
                    if (Utils.isShulker(bi)) return false;
                    Block block = bi.getBlock();

                    if (!Block.isShapeFullCube(block.getDefaultState().getCollisionShape(b.mc.world, pos))) return false;
                    return !(block instanceof FallingBlock) || !FallingBlock.canFallThrough(b.mc.world.getBlockState(pos));
                });
            }
        },

        FillLiquids {
            @Override
            protected void tick(HighwayBuilderTHM b) {
                int slot = findBlocksToPlacePrioritizeTrash(b);
                if (slot == -1) return;

                place(b, new MBPIteratorFilter(b.blockPosProvider.getLiquids(), pos -> !pos.getState().getFluidState().isEmpty()), slot, Forward);
            }
        },

        MineFront {
            @Override
            protected void start(HighwayBuilderTHM b) {
                mine(b, b.blockPosProvider.getFront(), true, Forward, this);
            }

            @Override
            protected void tick(HighwayBuilderTHM b) {
                mine(b, b.blockPosProvider.getFront(), true, Forward, this);
            }
        },

        MineBehind {
            @Override
            protected void start(HighwayBuilderTHM b) {
                mine(b, b.blockPosProvider.getBehindFront(), true, Forward, this);
            }

            @Override
            protected void tick(HighwayBuilderTHM b) {
                mine(b, b.blockPosProvider.getBehindFront(), true, Forward, this);
            }
        },

        MineFloor {
            @Override
            protected void start(HighwayBuilderTHM b) {
                mine(b, b.blockPosProvider.getFloor(), false, Forward, this);
            }

            @Override
            protected void tick(HighwayBuilderTHM b) {
                mine(b, b.blockPosProvider.getFloor(), false, Forward, this);
            }
        },

        MineBehindFloor {
            @Override
            protected void start(HighwayBuilderTHM b) {
                mine(b, b.blockPosProvider.getBehindFloor(), false, Forward, this);
            }

            @Override
            protected void tick(HighwayBuilderTHM b) {
                mine(b, b.blockPosProvider.getBehindFloor(), false, Forward, this);
            }
        },

        MineRailings {
            @Override
            protected void start(HighwayBuilderTHM b) {
                mine(b, b.blockPosProvider.getRailings(0), false, Forward, this);
            }

            @Override
            protected void tick(HighwayBuilderTHM b) {
                mine(b, b.blockPosProvider.getRailings(0), false, Forward, this);
            }
        },

        MineBehindRailings {
            @Override
            protected void start(HighwayBuilderTHM b) {
                mine(b, b.blockPosProvider.getBehindRailings(0), false, Forward, this);
            }

            @Override
            protected void tick(HighwayBuilderTHM b) {
                mine(b, b.blockPosProvider.getBehindRailings(0), false, Forward, this);
            }
        },

        MineAboveRailings {
            @Override
            protected void start(HighwayBuilderTHM b) {
                mine(b, b.blockPosProvider.getRailings(1), true, Forward, this);
            }

            @Override
            protected void tick(HighwayBuilderTHM b) {
                mine(b, b.blockPosProvider.getRailings(1), true, Forward, this);
            }
        },

        MineBehindAboveRailings {
            @Override
            protected void start(HighwayBuilderTHM b) {
                mine(b, b.blockPosProvider.getBehindRailings(1), true, Forward, this);
            }

            @Override
            protected void tick(HighwayBuilderTHM b) {
                mine(b, b.blockPosProvider.getBehindRailings(1), true, Forward, this);
            }
        },

        PlaceCornerBlock {
            @Override
            protected void start(HighwayBuilderTHM b) {
                if (!b.cornerBlock.get()) {
                    b.setState(Forward);
                    return;
                }

                int slot = findBlocksToPlacePrioritizeTrash(b);
                if (slot == -1) return;

                place(b, new MBPIteratorFilter(b.blockPosProvider.getRailings(-1, b.checkBehind.get()), pos -> {
                    if (!b.canPlace(pos, false)) return false;
                    return b.mc.world.getBlockState(pos.getBlockPos().up()).isReplaceable();
                }), slot, Forward);
            }

            @Override
            protected void tick(HighwayBuilderTHM b) {
                if (!b.cornerBlock.get()) {
                    b.setState(Forward);
                    return;
                }

                int slot = findBlocksToPlacePrioritizeTrash(b);
                if (slot == -1) return;

                place(b, new MBPIteratorFilter(b.blockPosProvider.getRailings(-1, b.checkBehind.get()), pos -> {
                    if (!b.canPlace(pos, false)) return false;
                    return b.mc.world.getBlockState(pos.getBlockPos().up()).isReplaceable();
                }), slot, Forward);
            }
        },

        PlaceRailings {
            @Override
            protected void start(HighwayBuilderTHM b) {
                int slot = findBlocksToPlace(b);
                if (slot == -1) return;

                place(b, b.blockPosProvider.getRailings(0, b.checkBehind.get()), slot, Forward);
            }

            @Override
            protected void tick(HighwayBuilderTHM b) {
                int slot = findBlocksToPlace(b);
                if (slot == -1) return;

                place(b, b.blockPosProvider.getRailings(0, b.checkBehind.get()), slot, Forward);
            }
        },

        PlaceFloor {
            @Override
            protected void start(HighwayBuilderTHM b) {
                int slot = findBlocksToPlace(b);
                if (slot == -1) return;

                place(b, b.blockPosProvider.getFloor(b.checkBehind.get()), slot, Forward);
            }

            @Override
            protected void tick(HighwayBuilderTHM b) {
                int slot = findBlocksToPlace(b);
                if (slot == -1) return;

                place(b, b.blockPosProvider.getFloor(b.checkBehind.get()), slot, Forward);
            }
        },

        ThrowOutTrash {
            private static final int MAX_STUCK_DROP_RETRIES = 5;
            private static final int POST_PURGE_DELAY_TICKS = 20;
            private static final int MAX_ROTATION_RETRIES = 2;
            private static final int MAX_DIRECT_TRASH_DROPS_PER_PASS = 2;
            private static final float TARGET_PITCH = -25.0f;
            private static final float ROTATION_TOLERANCE = 0.5f;
            private final Set<Integer> keepSlots = new HashSet<>();
            private final Map<Integer, ItemStack> skippedDropSlots = new HashMap<>();
            private boolean firstTick;
            private boolean completionDelayStarted;
            private int timer;
            private int failedDropSlot;
            private ItemStack failedDropStack = ItemStack.EMPTY;
            private int failedDropRetries;
            private int failedRotationRetries;
            private float cachedPreRotateYaw;
            private float cachedPreRotatePitch;

            @Override
            protected void start(HighwayBuilderTHM b) {
                boolean pausedHotbarManager = b.disableHotbarManagerForTrash("throw-out-trash-start");
                keepSlots.clear();
                skippedDropSlots.clear();
                firstTick = true;
                completionDelayStarted = false;
                timer = pausedHotbarManager ? Math.max(1, b.inventoryDelay.get()) : 0;
                clearFailedDropState();
                failedRotationRetries = 0;
                cachedPreRotateYaw = b.mc.player.getYaw();
                cachedPreRotatePitch = b.mc.player.getPitch();
            }

            @Override
            protected void tick(HighwayBuilderTHM b) {
                b.input.stop();

                if (timer > 0) {
                    timer--;
                    return;
                }

                if (!alignForTrashRoutine(b)) return;

                if (firstTick) {
                    firstTick = false;
                    return;
                }

                if (!b.mc.player.currentScreenHandler.getCursorStack().isEmpty()) {
                    completionDelayStarted = false;
                    if (handleCursorStack(b)) timer = b.inventoryDelay.get();
                    return;
                }

                if (dropDirectTrashBatch(b)) {
                    completionDelayStarted = false;
                    return;
                }

                if (!completionDelayStarted) {
                    completionDelayStarted = true;
                    timer = POST_PURGE_DELAY_TICKS;
                    return;
                }

                b.setState(b.lastState);
            }

            private boolean alignForTrashRoutine(HighwayBuilderTHM b) {
                float targetYaw = b.dir.opposite().yaw;
                float targetPitch = TARGET_PITCH;

                if (isRotationAligned(b.mc.player.getYaw(), b.mc.player.getPitch(), targetYaw, targetPitch)) {
                    failedRotationRetries = 0;
                    return true;
                }

                cachedPreRotateYaw = b.mc.player.getYaw();
                cachedPreRotatePitch = b.mc.player.getPitch();
                b.mc.player.setYaw(targetYaw);
                b.mc.player.setPitch(targetPitch);

                if (isRotationAligned(b.mc.player.getYaw(), b.mc.player.getPitch(), targetYaw, targetPitch)) {
                    failedRotationRetries = 0;
                    return true;
                }

                failedRotationRetries++;
                if (b.restockDebugLog.get()) {
                    b.restockDebug("ThrowOutTrash rotation align failed retry=%d/%d before=(yaw=%.2f,pitch=%.2f) after=(yaw=%.2f,pitch=%.2f) target=(yaw=%.2f,pitch=%.2f).",
                        failedRotationRetries,
                        MAX_ROTATION_RETRIES,
                        cachedPreRotateYaw,
                        cachedPreRotatePitch,
                        b.mc.player.getYaw(),
                        b.mc.player.getPitch(),
                        targetYaw,
                        targetPitch
                    );
                }

                if (failedRotationRetries > MAX_ROTATION_RETRIES) {
                    b.error("trash routine failed");
                }

                return false;
            }

            private boolean isRotationAligned(float yaw, float pitch, float targetYaw, float targetPitch) {
                return Math.abs(MathHelper.wrapDegrees(yaw - targetYaw)) <= ROTATION_TOLERANCE
                    && Math.abs(pitch - targetPitch) <= ROTATION_TOLERANCE;
            }

            private boolean handleCursorStack(HighwayBuilderTHM b) {
                ItemStack cursorStack = b.mc.player.currentScreenHandler.getCursorStack();
                if (b.clearCursorStackToEmptySlot("ThrowOutTrash", true)) return true;

                if (resolveProtectedCursorStack(b)) return true;
                if (resolveTrashCursorStack(b, cursorStack)) return true;

                if (Utils.isShulker(cursorStack.getItem())) {
                    if (b.ejectUselessShulkers.get() && !isProtectedShulkerForTrash(b, cursorStack)) {
                        return dropTrashCursorStack(b, "ThrowOutTrash-useless-shulker");
                    }
                    return false;
                }

                return dropTrashCursorStack(b, "ThrowOutTrash-default");
            }

            private boolean resolveProtectedCursorStack(HighwayBuilderTHM b) {
                ItemStack cursorStack = b.mc.player.currentScreenHandler.getCursorStack();
                if (cursorStack.isEmpty() || !isProtectedCursorStackForTrash(b, cursorStack)) return false;

                int trashSlot = findTrashSwapSlot(b, false);
                boolean usedProtectedTrash = false;
                if (trashSlot == -1) {
                    trashSlot = findTrashSwapSlot(b, true);
                    usedProtectedTrash = trashSlot != -1;
                }
                if (trashSlot == -1) {
                    if (b.restockDebugLog.get()) {
                        b.restockDebug("Preserved useful cursor stack %s because no trash block swap slot was available (ThrowOutTrash-cursor).", cursorStack.getItem());
                    }
                    return true;
                }

                b.mc.interactionManager.clickSlot(
                    b.mc.player.currentScreenHandler.syncId,
                    SlotUtils.indexToId(trashSlot),
                    0,
                    SlotActionType.PICKUP,
                    b.mc.player
                );

                ItemStack swappedCursor = b.mc.player.currentScreenHandler.getCursorStack();
                if (isProtectedCursorStackForTrash(b, swappedCursor)) {
                    if (b.restockDebugLog.get()) {
                        b.restockDebug("Preserved useful cursor stack %s because trash swap did not dislodge it (ThrowOutTrash-cursor).", swappedCursor.getItem());
                    }
                    return true;
                }

                if (b.restockDebugLog.get() && usedProtectedTrash) {
                    b.restockDebug("ThrowOutTrash spent a protected trash block as a last resort to clear a useful cursor stack.");
                }
                return dropTrashCursorStack(
                    b,
                    usedProtectedTrash ? "ThrowOutTrash-protected-trash-cursor-swap" : "ThrowOutTrash-trash-cursor-swap"
                );
            }

            private boolean resolveTrashCursorStack(HighwayBuilderTHM b, ItemStack cursorStack) {
                if (cursorStack == null || cursorStack.isEmpty() || !isEligibleTrashReserveStack(b, cursorStack)) return false;

                int trashSlot = findTrashSwapSlot(b, false);
                if (trashSlot == -1) return false;

                b.mc.interactionManager.clickSlot(
                    b.mc.player.currentScreenHandler.syncId,
                    SlotUtils.indexToId(trashSlot),
                    0,
                    SlotActionType.PICKUP,
                    b.mc.player
                );

                return dropTrashCursorStack(b, "ThrowOutTrash-reserved-trash-cursor-swap");
            }

            private int findTrashSwapSlot(HighwayBuilderTHM b, boolean allowProtectedTrash) {
                refreshProtectedTrashSlots(b);
                for (int i = 0; i < b.mc.player.getInventory().getMainStacks().size(); i++) {
                    if (isManagedHotbarTrashSlot(b, i)) continue;
                    ItemStack itemStack = b.mc.player.getInventory().getStack(i);
                    if (!isEligibleTrashReserveStack(b, itemStack)) continue;
                    if (!allowProtectedTrash && keepSlots.contains(i)) continue;
                    return i;
                }

                return -1;
            }

            private void refreshProtectedTrashSlots(HighwayBuilderTHM b) {
                keepSlots.clear();

                List<Integer> trashBlockSlots = new ArrayList<>();
                for (int i = 0; i < b.mc.player.getInventory().getMainStacks().size(); i++) {
                    if (isManagedHotbarTrashSlot(b, i)) continue;
                    ItemStack itemStack = b.mc.player.getInventory().getStack(i);
                    if (!isEligibleTrashReserveStack(b, itemStack)) continue;
                    trashBlockSlots.add(i);
                }

                trashBlockSlots.sort(b::compareTrashBlockPreferenceSlots);

                int keepBudget = Math.max(b.keepTrashBlockStacks.get(), 0);
                int keepCount = Math.min(keepBudget, trashBlockSlots.size());
                for (int i = 0; i < keepCount; i++) keepSlots.add(trashBlockSlots.get(i));
            }

            private boolean dropDirectTrashBatch(HighwayBuilderTHM b) {
                int maxActions = Math.min(MAX_DIRECT_TRASH_DROPS_PER_PASS, b.mc.player.getInventory().getMainStacks().size());
                boolean onlySkippedTrashRemains = false;

                for (int actions = 0; actions < maxActions; ) {
                    refreshProtectedTrashSlots(b);

                    boolean droppedThisPass = false;
                    boolean blockedThisPass = false;

                    for (int i = 0; i < b.mc.player.getInventory().getMainStacks().size(); i++) {
                        ItemStack itemStack = b.mc.player.getInventory().getStack(i);
                        boolean droppableShulker = Utils.isShulker(itemStack.getItem())
                            && b.ejectUselessShulkers.get()
                            && !isProtectedShulkerForTrash(b, itemStack);
                        boolean droppableTrashItem = b.isDroppableTrashStack(itemStack);
                        if (!droppableShulker && !droppableTrashItem) continue;

                        if (isManagedHotbarTrashSlot(b, i)) {
                            blockedThisPass = true;
                            continue;
                        }

                        if (isSkippedDropSlot(i, itemStack)) {
                            blockedThisPass = true;
                            continue;
                        }

                        if (keepSlots.contains(i) && isEligibleTrashReserveStack(b, itemStack)) continue;

                        ItemStack beforeDrop = itemStack.copy();
                        if (droppableShulker) {
                            if (b.restockDebugLog.get()) {
                                b.restockDebug("ThrowOutTrash dropping useless shulker from slot %d (%s).", i, itemStack.getItem());
                            }
                        } else if (b.restockDebugLog.get()) {
                            b.restockDebug("ThrowOutTrash dropping trash item from slot %d (%s).", i, itemStack.getItem());
                        }

                        // Resend rotation right before the throw so the server drops in the
                        // aligned direction — vanilla only flushes the look packet at tick end,
                        // after this throw would already have been processed. Fixes drop desync.
                        b.mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(
                            b.mc.player.getYaw(), b.mc.player.getPitch(),
                            b.mc.player.isOnGround(), b.mc.player.horizontalCollision));
                        InvUtils.drop().slot(i);

                        ItemStack afterDrop = b.mc.player.getInventory().getStack(i);
                        if (matchesSkippedDropStack(beforeDrop, afterDrop)) {
                            noteFailedDrop(b, i, beforeDrop);
                            timer = b.inventoryDelay.get();
                            return true;
                        }

                        skippedDropSlots.remove(i);
                        clearFailedDropState();
                        completionDelayStarted = false;
                        actions++;
                        droppedThisPass = true;
                        if (actions >= maxActions) {
                            timer = b.inventoryDelay.get();
                            return true;
                        }
                        break;
                    }

                    if (!droppedThisPass) {
                        onlySkippedTrashRemains = blockedThisPass;
                        break;
                    }
                }

                if (onlySkippedTrashRemains && b.restockDebugLog.get()) {
                    b.restockDebug("ThrowOutTrash leaving with only temporarily skipped trash remaining.");
                }

                return false;
            }

            private boolean isManagedHotbarTrashSlot(HighwayBuilderTHM b, int slot) {
                return slot >= 0 && slot < 9 && b.isHotbarSlotConfiguredByManager(slot);
            }

            private boolean isSkippedDropSlot(int slot, ItemStack itemStack) {
                ItemStack skippedStack = skippedDropSlots.get(slot);
                if (skippedStack == null) return false;
                if (matchesSkippedDropStack(skippedStack, itemStack)) return true;

                skippedDropSlots.remove(slot);
                return false;
            }

            private boolean matchesSkippedDropStack(ItemStack expected, ItemStack actual) {
                if (expected == null || actual == null) return false;
                if (expected.isEmpty() || actual.isEmpty()) return expected.isEmpty() && actual.isEmpty();

                return expected.getCount() == actual.getCount()
                    && ItemStack.areItemsAndComponentsEqual(expected, actual);
            }

            private void noteFailedDrop(HighwayBuilderTHM b, int slot, ItemStack attemptedStack) {
                if (failedDropSlot == slot && matchesSkippedDropStack(failedDropStack, attemptedStack)) {
                    failedDropRetries++;
                } else {
                    failedDropSlot = slot;
                    failedDropStack = attemptedStack.copy();
                    failedDropRetries = 1;
                }

                if (failedDropRetries < MAX_STUCK_DROP_RETRIES) return;

                skippedDropSlots.put(slot, attemptedStack.copy());
                clearFailedDropState();

                if (b.restockDebugLog.get()) {
                    b.restockDebug("ThrowOutTrash skipping slot %d for the rest of this visit after repeated failed drops (%s x%d).",
                        slot,
                        attemptedStack.getItem(),
                        attemptedStack.getCount()
                    );
                }
            }

            private void clearFailedDropState() {
                failedDropSlot = -1;
                failedDropStack = ItemStack.EMPTY;
                failedDropRetries = 0;
            }

            private boolean isEligibleTrashReserveStack(HighwayBuilderTHM b, ItemStack itemStack) {
                return b.isStableFullTrashBlockStack(itemStack);
            }

            private boolean isProtectedCursorStackForTrash(HighwayBuilderTHM b, ItemStack itemStack) {
                if (itemStack == null || itemStack.isEmpty()) return false;
                if (Utils.isShulker(itemStack.getItem())) return isProtectedShulkerForTrash(b, itemStack);
                return b.isProtectedItemStack(itemStack);
            }

            private boolean isProtectedShulkerForTrash(HighwayBuilderTHM b, ItemStack itemStack) {
                if (itemStack == null || itemStack.isEmpty()) return false;
                if (!Utils.isShulker(itemStack.getItem())) return false;
                if (b.isProtectedItemStack(itemStack)) return true;

                for (ItemStack stack : THMUtils.getContainerContents(itemStack)) {
                    if (b.isProtectedItemStack(stack)) return true;
                }

                return false;
            }

            private boolean dropTrashCursorStack(HighwayBuilderTHM b, String reason) {
                ItemStack cursorStack = b.mc.player.currentScreenHandler.getCursorStack();
                if (cursorStack.isEmpty() || isProtectedCursorStackForTrash(b, cursorStack)) return false;

                InvUtils.dropHand();

                if (b.restockDebugLog.get()) {
                    b.restockDebug("Dropped trash cursor stack %s (%s).", cursorStack.getItem(), reason);
                }

                return true;
            }
        },

        PlaceEChestBlockade {
            @Override
            protected void tick(HighwayBuilderTHM b) {
                if (!b.ensureCenteredBeforeBlockadePlacement(this)) return;

                int slot = findBlocksToPlacePrioritizeTrash(b);
                if (slot == -1) return;

                place(b, b.blockPosProvider.getBlockade(false, b.getEffectiveBlockadeType()), slot, MineEnderChests);
            }
        },

        MineEChestBlockade {
            @Override
            protected void tick(HighwayBuilderTHM b) {
                mine(b, b.blockPosProvider.getBlockade(true, b.getEffectiveBlockadeType()), true, Center, Forward);
            }
        },

        MineEnderChests {
            private static final MBlockPos pos = new MBlockPos();
            private int targetEchestsToBreak;
            private int targetObsidianCount;
            private int lastObservedObsidianCount;
            private boolean first, primed, breakRequested, newBreakingMode;
            private boolean stopTimerEnabled;
            private int stopTimer, moveTimer, rebreakTimer, timeout;
            private double returnX, returnY, returnZ;
            private boolean returnAnchorSaved;
            @Override
            protected void start(HighwayBuilderTHM b) {
                b.restockTask.refreshSessionProgress();
                b.restockTask.notePhase(RestockTask.SourcePhase.MineEnderChests);
                if (b.restockTask.isSequenceActive() && !b.restockTask.isBlockadeReady()) {
                    if (b.lastState != Center && b.lastState != ThrowOutTrash && b.lastState != PlaceShulkerBlockade) {
                        b.setState(Center);
                        return;
                    }
                    else if (b.lastState == Center) {
                        b.setState(ThrowOutTrash);
                        return;
                    }
                    else if (b.lastState == ThrowOutTrash) {
                        b.setState(PlaceShulkerBlockade);
                        return;
                    }
                }
                else if (!b.restockTask.isSequenceActive()) {
                    if (b.lastState != Center && b.lastState != ThrowOutTrash && b.lastState != PlaceEChestBlockade) {
                        b.setState(Center);
                        return;
                    }
                    else if (b.lastState == Center) {
                        b.setState(ThrowOutTrash);
                        return;
                    }
                    else if (b.lastState == ThrowOutTrash) {
                        b.setState(PlaceEChestBlockade);
                        return;
                    }
                }

                if (b.restockTask.getSession() == null) {
                    b.error("Unable to continue e-chest mining without an active restock session.");
                    return;
                }

                if (!b.restockTask.freezeSessionTargetForSourceSelection()) {
                    b.restockTask.reportUnusableSessionTarget();
                    return;
                }

                RestockTask.RestockSession session = b.restockTask.getSession();
                newBreakingMode = b.newBreaking.get();
                session.refreshProgress();
                b.restockTask.clampObsidianTargetToMineableEChests("mine-echests-start");
                session.refreshProgress();
                targetEchestsToBreak = session.getTargetEchestsToBreak();
                targetObsidianCount = session.getMiningGoalObsidianCount();
                if (targetEchestsToBreak <= 0) {
                    b.restockTask.markCurrentSourceExhausted(RestockTask.SourcePhase.MineEnderChests);
                    if (b.restockTask.isCurrentTaskSuccessful()) {
                        completeAndReturnToAnchor(b);
                    } else if (b.canUseKitbotForActiveRestock() && b.restockTask.shouldAttemptKitbotAfterMineEnderChests()) {
                        b.enterKitbotRestockWithAvailabilityGate("MineEnderChests exhausted; trying KitBot restock.", true);
                    } else {
                        b.error("No usable ender chests available to continue obsidian restock.");
                    }
                    return;
                }
                if (b.prioritizePickaxeRestockBeforeEChestMining(targetEchestsToBreak, "mine-echests-start durability preflight")) return;
                first = true;
                moveTimer = rebreakTimer = timeout = 0;
                lastObservedObsidianCount = 0;
                returnX = b.mc.player.getX();
                returnY = b.mc.player.getY();
                returnZ = b.mc.player.getZ();
                returnAnchorSaved = true;

                stopTimerEnabled = false;
                primed = false;
                breakRequested = false;
                if (newBreakingMode && Speedmine.INSTANCE != null && !Speedmine.INSTANCE.isActive()) Speedmine.INSTANCE.toggle();
                b.applyEChestBreakSpeedOverrideIfPossible("mine-echests-start");
            }

            @Override
            protected void tick(HighwayBuilderTHM b) {
                if (stopTimerEnabled) {
                    if (stopTimer > 0) stopTimer--;
                    else completeAndReturnToAnchor(b);

                    return;
                }

                HorizontalDirection dir = b.dir.diagonal ? b.dir.rotateLeft().rotateLeftSkipOne() : b.dir.opposite();
                BlockPos sourceContainerPos = b.restockTask.getSourceContainerPos();
                if (sourceContainerPos != null) {
                    pos.set(sourceContainerPos.getX(), sourceContainerPos.getY(), sourceContainerPos.getZ());
                } else if (b.restockTask.isSequenceActive()) {
                    b.restockDebug("MineEnderChests could not resolve the dedicated source container slot; rebuilding from Center.");
                    b.restockTask.setBlockadeReady(false);
                    b.setState(Center);
                    return;
                } else {
                    pos.set(b.mc.player).offset(dir);
                }

                // Move
                if (moveTimer > 0) {
                    b.mc.player.setYaw(dir.yaw);
                    b.input.stop();

                    moveTimer--;
                    return;
                }

                // Check for obsidian count
                int obsidianCount = 0;

                for (Entity entity : b.mc.world.getOtherEntities(b.mc.player, new Box(pos.x, pos.y, pos.z, pos.x + 1, pos.y + 2, pos.z + 1))) {
                    if (entity instanceof ItemEntity itemEntity && itemEntity.getStack().getItem() == Items.OBSIDIAN) {
                        obsidianCount += itemEntity.getStack().getCount();
                    }
                }

                for (int i = 0; i < b.mc.player.getInventory().getMainStacks().size(); i++) {
                    ItemStack itemStack = b.mc.player.getInventory().getStack(i);
                    if (itemStack.getItem() == Items.OBSIDIAN) obsidianCount += itemStack.getCount();
                }

                if (obsidianCount > lastObservedObsidianCount) {
                    b.restockWatchdog.markProgress("mine-echests-obsidian-progress");
                }
                lastObservedObsidianCount = obsidianCount;

                if (obsidianCount >= targetObsidianCount) {
                    if (b.restockTask.getSession() != null && b.restockTask.getSession().getRemainingObsidianItems() > targetEchestsToBreak * 8) {
                        b.restockTask.getSession().markGreatestAvailable();
                    }
                    stopTimerEnabled = true;
                    stopTimer = 12;
                    return;
                }

                BlockPos bp = pos.getBlockPos();

                // Check block state
                BlockState blockState = b.mc.world.getBlockState(bp);

                if (blockState.getBlock() == Blocks.ENDER_CHEST) {
                    if (b.mc.currentScreen instanceof GenericContainerScreen screen) {
                        // wait for the screen to be properly loaded
                        if (screen.getScreenHandler().syncId != b.syncId) return;

                        b.closeHandledScreen();
                    }

                    // if we don't know what's in your echest, open it quickly while we have one available to check
                    if (!EChestMemory.isKnown()) {
                        if (b.rotation.get().place) Rotations.rotate(Rotations.getYaw(bp), Rotations.getPitch(bp), () ->
                            b.mc.interactionManager.interactBlock(b.mc.player, Hand.MAIN_HAND, new BlockHitResult(Vec3d.ofCenter(bp), Direction.UP, bp, false)));
                        else b.mc.interactionManager.interactBlock(b.mc.player, Hand.MAIN_HAND, new BlockHitResult(Vec3d.ofCenter(bp), Direction.UP, bp, false));

                        return;
                    }

                    if (first) {
                        moveTimer = 8;
                        first = false;
                        return;
                    }

                    // Mine ender chest
                    int slot = findAndMoveBestToolToHotbar(b, blockState, true);
                    if (slot == -1) {
                        if (newBreakingMode && Speedmine.INSTANCE != null && Speedmine.INSTANCE.isActive()) Speedmine.INSTANCE.toggle();
                        if (b.restockTask.isActiveMaterials() && b.restockTask.hasPendingPickaxes()) {
                            completeAndReturnToAnchor(b);
                        } else if (!b.restockTask.pickaxes
                            && b.hasPickaxeRestockSourceAvailable()
                            && b.restockTask.prioritizePickaxesBeforeActiveTask("missing eligible pickaxe while mining loose e-chests")) {
                            b.setState(Restock, Restock);
                        } else if (b.restockTask.pickaxes && b.canUseKitbotForActiveRestock() && b.restockTask.shouldAttemptKitbot()) {
                            b.enterKitbotRestockWithAvailabilityGate("MineEnderChests missing pickaxe; trying KitBot pickaxe restock.", true);
                        } else {
                            b.error("Cannot find pickaxe without silk touch to mine ender chests.");
                        }
                        return;
                    }

                    if (newBreakingMode && Speedmine.INSTANCE != null) {
                        if (!breakRequested) {
                            b.info("MineEnderChests: requesting first break at %s", bp);
                            Speedmine.INSTANCE.requestBreak(bp);
                            breakRequested = true;
                        } else {
                            b.restockDebug("MineEnderChests: break already requested, Speedmine autoRebreak handles %s (isMining=%s)", bp, Speedmine.INSTANCE.isMining(bp));
                        }
                    } else {
                        int selectedSlot = b.mc.player.getInventory().getSelectedSlot();
                        boolean swappedForRebreak = false;

                        if (b.rebreakEchests.get() && primed) {
                            timeout++;
                            if (timeout > 60) {
                                primed = false;
                                timeout = 0;
                                return;
                            }

                            if (rebreakTimer > 0) {
                                rebreakTimer--;
                                return;
                            }

                            Runnable sendRebreakPackets = () ->
                                b.mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, bp, BlockUtils.getDirection(bp)));
                            rebreakTimer = b.rebreakTimer.get();

                            if (b.silentRebreakSwap.get()) {
                                if (selectedSlot != slot) {
                                    InvUtils.swap(slot, false);
                                    swappedForRebreak = true;
                                }
                            } else if (selectedSlot != slot) {
                                InvUtils.swap(slot, false);
                            }

                            if (b.rotation.get().mine) Rotations.rotate(Rotations.getYaw(bp), Rotations.getPitch(bp), sendRebreakPackets);
                            else sendRebreakPackets.run();

                            if (swappedForRebreak) InvUtils.swap(selectedSlot, false);
                        } else {
                            if (selectedSlot != slot) InvUtils.swap(slot, false);
                            if (b.rotation.get().mine) Rotations.rotate(Rotations.getYaw(bp), Rotations.getPitch(bp), () -> BlockUtils.breakBlock(bp, true));
                            else BlockUtils.breakBlock(bp, true);
                        }
                    }
                }
                else {
                    // Place ender chest
                    int slot = findAndMoveToHotbar(b, itemStack -> itemStack.getItem() == Items.ENDER_CHEST);
                    RestockTask.RestockSession session = b.restockTask.getSession();
                    int minimumRemainingEChests = session != null ? session.saveEchestsReserve : b.saveEchests.get();
                    if (slot == -1 || countItem(b, stack -> stack.getItem().equals(Items.ENDER_CHEST)) <= minimumRemainingEChests) {
                        b.restockTask.markCurrentSourceExhausted(RestockTask.SourcePhase.MineEnderChests);
                        stopTimerEnabled = true;
                        stopTimer = 12;
                        return;
                    }

                    if (!first) primed = true;

                    if (newBreakingMode) {
                        b.restockDebug("MineEnderChests: placing echest at %s (breakRequested=%s, primed=%s)", bp, breakRequested, primed);
                    } else {
                        timeout = 0;
                    }
                    BlockUtils.place(bp, Hand.MAIN_HAND, slot, b.rotation.get().place, 0, true, true, b.silentRebreakSwap.get());
                }
            }

            private void completeAndReturnToAnchor(HighwayBuilderTHM b) {
                if (newBreakingMode && Speedmine.INSTANCE != null && Speedmine.INSTANCE.isActive()) Speedmine.INSTANCE.toggle();
                if (returnAnchorSaved) {
                    b.input.stop();
                    b.mc.player.setPosition(returnX, returnY, returnZ);
                    returnAnchorSaved = false;
                }

                b.completeRestockTaskAndContinue();
            }
        },

        KitbotOrder {
            private static final int KITBOT_FAILSAFE_DELAY_TICKS = 200;
            private static final int KITBOT_PARTIAL_DELIVERY_GRACE_TICKS = 40;
            private enum FailureMode {
                KITBOT_LOCAL,
                POST_DELIVERY_RESTORE
            }
            private boolean positionReady;
            private BlockadeType sourceBlockadeType;
            private HorizontalDirection containerDirection;
            private HorizontalDirection expansionDirection;
            private double targetOrderX, targetOrderZ;

            @Override
            protected void start(HighwayBuilderTHM b) {
                if (b.getKitbotRestockGate() == KitbotRestockGate.Unavailable) {
                    failKitbotOrder(b, "KitBot is unavailable for restock.", FailureMode.KITBOT_LOCAL);
                    return;
                }

                if (b.kitbotOrderResumeAfterCenter) {
                    b.kitbotOrderResumeAfterCenter = false;
                    if (b.kitbotEnclosureActive || b.kitbotEnclosureRestorePending) {
                        if (b.restockDebugLog.get()) {
                            b.restockDebug("KitbotOrder resumed after Center without resetting order state (restorePending=%s, inFlight=%s, frontendSucceeded=%s).",
                                b.kitbotEnclosureRestorePending,
                                b.kitbotOrderInFlight,
                                b.kitbotOrderFrontendSucceeded
                            );
                        }
                        b.input.stop();
                        return;
                    }
                }

                positionReady = false;
                b.kitbotSeenNearby = false;
                b.kitbotDepartureWaitUntilAge = 0;
                b.kitbotCollectUntilAge = 0;
                b.clearKitbotCenteringRuntime("kitbot-order-start");
                b.resetKitbotOrderCenterVerificationRuntime("kitbot-order-start");
                sourceBlockadeType = b.getEffectiveBlockadeType();
                containerDirection = b.getRestockContainerDirection(b.dir);
                expansionDirection = b.getKitbotExpansionDirection(containerDirection);
                b.cacheKitbotReturnAnchorFromPlayer();
                b.kitbotEnclosureActive = true;
                b.kitbotEnclosureRestorePending = false;
                targetOrderX = targetOrderZ = 0.0;
                if (b.restockDebugLog.get()) {
                    b.restockDebug("KitbotOrder.start(anchor=%s, containerDir=%s, expansionDir=%s, mode=%s, effectiveType=%s, removeSlots=[1,4], addExpansionColumns=[0+exp,3+exp,4+exp,1+exp]).",
                        b.formatBlockPos(b.kitbotAnchorPos),
                        containerDirection,
                        expansionDirection,
                        b.dir.diagonal ? "diagonal" : "straight",
                        sourceBlockadeType
                    );
                }
                b.input.stop();
            }

            @Override
            protected void tick(HighwayBuilderTHM b) {
                if (!b.kitbotCenteringActive) b.input.stop();

                if (!b.canUseKitbotForActiveRestock()) {
                    failKitbotOrder(b, "Kitbot restock was disabled while the Kitbot enclosure was active.", FailureMode.KITBOT_LOCAL);
                    return;
                }

                if (b.kitbotEnclosureRestorePending) {
                    if (!b.ensureCenteredOnBlockBeforeEnclosurePlacement(KitbotOrder, b.kitbotAnchorPos.toImmutable(), "kitbot-normal-blockade-restore")) return;
                    if (restoreNormalBlockade(b)) return;
                    finishSuccessfulKitbotHandoff(b);
                    return;
                }

                if (reconcileKitbotEnclosure(b)) return;

                if (!positionReady) {
                    if (handleKitbotOrderCentering(b)) return;
                }

                if (!b.kitbotOrderInFlight && !b.kitbotOrderFrontendSucceeded) {
                    submitKitbotOrderIfReady(b);
                    return;
                }

                if (b.kitbotOrderInFlight) return;

                if (b.isKitbotNearby()) b.kitbotSeenNearby = true;

                if (b.kitbotOrderFrontendSucceeded && hasExpectedKitDelivery(b)) {
                    if (!isKitCollectionSettled(b)) return;
                    b.kitbotEnclosureRestorePending = true;
                    return;
                }

                if (b.kitbotOrderFrontendSucceeded && System.currentTimeMillis() >= b.kitbotInventoryProofDeadlineMs) {
                    failKitbotOrder(
                        b,
                        "Kitbot restock delivery arrived, but matching shulkers were not detected within 30 seconds.",
                        FailureMode.KITBOT_LOCAL
                    );
                }
            }

            private boolean ensureKitbotWalkCenteredOnBlock(HighwayBuilderTHM b, BlockPos target, String reason, FailureMode failureMode) {
                KitbotCenterWalkResult result = b.walkKitbotToBlockCenter(target, reason);
                if (result == KitbotCenterWalkResult.Reached) return true;
                if (result == KitbotCenterWalkResult.TimedOut) {
                    failKitbotOrder(
                        b,
                        "Unable to walk to KitBot block center for " + reason + " before continuing restock.",
                        failureMode
                    );
                }
                return false;
            }

            private boolean handleKitbotOrderCentering(HighwayBuilderTHM b) {
                if (b.mc.player == null) return true;

                Vec3d targetOrderPos = b.getKitbotOrderTargetPosition(
                    b.kitbotAnchorPos.toImmutable(),
                    b.mc.player.getY(),
                    containerDirection,
                    expansionDirection
                );
                if (targetOrderPos == null) {
                    failKitbotOrder(b, "Unable to resolve the KitBot enclosure center before ordering.", FailureMode.KITBOT_LOCAL);
                    return true;
                }

                targetOrderX = targetOrderPos.x;
                targetOrderZ = targetOrderPos.z;

                KitbotCenterWalkResult result = b.walkKitbotToTarget(
                    targetOrderX,
                    targetOrderZ,
                    KITBOT_ORDER_CENTER_RADIUS,
                    "kitbot-order-center",
                    "enclosure center"
                );
                if (result == KitbotCenterWalkResult.Reached) {
                    positionReady = true;
                    return false;
                }

                if (result == KitbotCenterWalkResult.TimedOut) {
                    failKitbotOrder(
                        b,
                        String.format(
                            Locale.ROOT,
                            "Unable to reach KitBot enclosure center after %d ticks (distance=%.3f, targetX=%.3f, targetZ=%.3f).",
                            KITBOT_ORDER_CENTER_TIMEOUT_TICKS,
                            b.kitbotCenteringDistance,
                            targetOrderX,
                            targetOrderZ
                        ),
                        FailureMode.KITBOT_LOCAL
                    );
                    return true;
                }

                return true;
            }

            private boolean verifyKitbotOrderCenterBeforeSubmit(HighwayBuilderTHM b) {
                if (b.mc.player == null) return true;

                Vec3d targetOrderPos = b.getKitbotOrderTargetPosition(
                    b.kitbotAnchorPos.toImmutable(),
                    b.mc.player.getY(),
                    containerDirection,
                    expansionDirection
                );
                if (targetOrderPos == null) {
                    failKitbotOrder(b, "Unable to resolve the KitBot enclosure center before verifying order position.", FailureMode.KITBOT_LOCAL);
                    return true;
                }

                targetOrderX = targetOrderPos.x;
                targetOrderZ = targetOrderPos.z;

                if ((b.kitbotOrderCenterVerificationActive || b.kitbotOrderCenterVerified)
                    && !b.matchesKitbotOrderCenterVerificationTarget(targetOrderX, targetOrderZ)) {
                    b.invalidateKitbotOrderCenterVerification("kitbot-order-target-changed");
                }

                double distance = distanceToKitbotOrderCenter(b);
                boolean playerBlockMatchesAnchor = playerBlockMatchesKitbotAnchor(b);
                b.kitbotOrderCenterVerificationDistance = distance;

                if (b.kitbotOrderCenterVerified) {
                    if (distance <= KITBOT_ORDER_SUBMIT_RADIUS) {
                        if (b.restockDebugLog.get()) {
                            b.restockDebug(
                                "KitbotOrder center verification accepted for submit: player=(%.6f, %.6f) block=%s target=(%.3f, %.3f) distance=%.3f submitRadius=%.3f playerBlockMatchesAnchor=%s failures=%d.",
                                b.mc.player.getX(),
                                b.mc.player.getZ(),
                                b.formatBlockPos(b.mc.player.getBlockPos()),
                                targetOrderX,
                                targetOrderZ,
                                distance,
                                KITBOT_ORDER_SUBMIT_RADIUS,
                                playerBlockMatchesAnchor,
                                b.kitbotOrderCenterVerificationFailures
                            );
                        }
                        return false;
                    }

                    if (b.restockDebugLog.get()) {
                        b.restockDebug(
                            "KitbotOrder center verification invalidated before submit: player=(%.6f, %.6f) block=%s target=(%.3f, %.3f) distance=%.3f submitRadius=%.3f playerBlockMatchesAnchor=%s.",
                            b.mc.player.getX(),
                            b.mc.player.getZ(),
                            b.formatBlockPos(b.mc.player.getBlockPos()),
                            targetOrderX,
                            targetOrderZ,
                            distance,
                            KITBOT_ORDER_SUBMIT_RADIUS,
                            playerBlockMatchesAnchor
                        );
                    }
                    b.invalidateKitbotOrderCenterVerification("kitbot-order-verified-position-invalid");
                    positionReady = false;
                }

                if (b.kitbotOrderCenterVerificationActive) {
                    return tickKitbotOrderCenterVerification(b);
                }

                if (distance > KITBOT_ORDER_CENTER_RADIUS) {
                    if (b.restockDebugLog.get()) {
                        b.restockDebug(
                            "KitbotOrder submit blocked until center walk: player=(%.6f, %.6f) block=%s target=(%.3f, %.3f) distance=%.3f centerRadius=%.3f playerBlockMatchesAnchor=%s failures=%d.",
                            b.mc.player.getX(),
                            b.mc.player.getZ(),
                            b.formatBlockPos(b.mc.player.getBlockPos()),
                            targetOrderX,
                            targetOrderZ,
                            distance,
                            KITBOT_ORDER_CENTER_RADIUS,
                            playerBlockMatchesAnchor,
                            b.kitbotOrderCenterVerificationFailures
                        );
                    }
                    b.invalidateKitbotOrderCenterVerification("kitbot-order-pre-submit-position");
                    positionReady = false;
                    if (handleKitbotOrderCentering(b)) return true;
                }

                return startKitbotOrderCenterVerification(b);
            }

            private boolean startKitbotOrderCenterVerification(HighwayBuilderTHM b) {
                if (b.mc.player == null || b.input == null) return true;

                b.kitbotOrderCenterVerificationActive = true;
                b.kitbotOrderCenterVerified = false;
                b.kitbotOrderCenterVerificationTicks = 0;
                b.kitbotOrderCenterVerificationRecenterTicks = 0;
                b.kitbotOrderCenterVerificationSettleTicks = 0;
                b.kitbotOrderCenterVerificationRecentered = false;
                b.kitbotOrderCenterVerificationTargetX = targetOrderX;
                b.kitbotOrderCenterVerificationTargetZ = targetOrderZ;
                b.kitbotOrderCenterVerificationDistance = distanceToKitbotOrderCenter(b);
                b.input.stop();

                if (b.restockDebugLog.get()) {
                    b.restockDebug(
                        "KitbotOrder center verification started: player=(%.6f, %.6f) target=(%.3f, %.3f) distance=%.3f failures=%d wiggleTicks=%d recenterTimeout=%d settleTicks=%d submitRadius=%.3f.",
                        b.mc.player.getX(),
                        b.mc.player.getZ(),
                        targetOrderX,
                        targetOrderZ,
                        b.kitbotOrderCenterVerificationDistance,
                        b.kitbotOrderCenterVerificationFailures,
                        KITBOT_ORDER_VERIFY_WIGGLE_TICKS,
                        KITBOT_ORDER_VERIFY_RECENTER_TIMEOUT_TICKS,
                        KITBOT_ORDER_VERIFY_SETTLE_TICKS,
                        KITBOT_ORDER_SUBMIT_RADIUS
                    );
                }

                return true;
            }

            private boolean tickKitbotOrderCenterVerification(HighwayBuilderTHM b) {
                if (b.mc.player == null || b.input == null) return true;

                b.input.stop();
                if (b.kitbotOrderCenterVerificationTicks < KITBOT_ORDER_VERIFY_WIGGLE_TICKS) {
                    int tick = b.kitbotOrderCenterVerificationTicks;
                    if (tick < DESYNC_WIGGLE_LEFT_TICKS) {
                        b.input.left(true);
                    } else {
                        b.input.right(true);
                    }

                    b.kitbotOrderCenterVerificationTicks++;
                    return true;
                }

                if (!b.kitbotOrderCenterVerificationRecentered) {
                    KitbotCenterWalkResult result = b.walkKitbotToTarget(
                        targetOrderX,
                        targetOrderZ,
                        KITBOT_ORDER_CENTER_RADIUS,
                        "kitbot-order-wiggle-recenter",
                        "enclosure center recenter"
                    );

                    if (result == KitbotCenterWalkResult.Reached) {
                        b.kitbotOrderCenterVerificationRecentered = true;
                        b.kitbotOrderCenterVerificationRecenterTicks = 0;
                        b.kitbotOrderCenterVerificationSettleTicks = 0;
                        b.input.stop();
                        if (b.restockDebugLog.get()) {
                            b.restockDebug(
                                "KitbotOrder center verification recentered after wiggle: player=(%.6f, %.6f) target=(%.3f, %.3f) distance=%.3f centerRadius=%.3f.",
                                b.mc.player.getX(),
                                b.mc.player.getZ(),
                                targetOrderX,
                                targetOrderZ,
                                distanceToKitbotOrderCenter(b),
                                KITBOT_ORDER_CENTER_RADIUS
                            );
                        }
                        return true;
                    }

                    b.kitbotOrderCenterVerificationRecenterTicks++;
                    if (result == KitbotCenterWalkResult.TimedOut
                        || b.kitbotOrderCenterVerificationRecenterTicks >= KITBOT_ORDER_VERIFY_RECENTER_TIMEOUT_TICKS) {
                        failSoftKitbotOrderCenterVerification(b, "recenter-timeout");
                    }

                    return true;
                }

                if (b.kitbotOrderCenterVerificationSettleTicks < KITBOT_ORDER_VERIFY_SETTLE_TICKS) {
                    b.kitbotOrderCenterVerificationSettleTicks++;
                    b.input.stop();
                    return true;
                }

                b.input.stop();
                double distance = distanceToKitbotOrderCenter(b);
                boolean playerBlockMatchesAnchor = playerBlockMatchesKitbotAnchor(b);
                b.kitbotOrderCenterVerificationActive = false;
                b.kitbotOrderCenterVerificationTicks = 0;
                b.kitbotOrderCenterVerificationRecenterTicks = 0;
                b.kitbotOrderCenterVerificationSettleTicks = 0;
                b.kitbotOrderCenterVerificationRecentered = false;
                b.kitbotOrderCenterVerificationDistance = distance;

                if (distance <= KITBOT_ORDER_SUBMIT_RADIUS) {
                    b.kitbotOrderCenterVerified = true;
                    if (b.restockDebugLog.get()) {
                        b.restockDebug(
                            "KitbotOrder center verification passed: player=(%.6f, %.6f) block=%s target=(%.3f, %.3f) distance=%.3f submitRadius=%.3f playerBlockMatchesAnchor=%s failures=%d.",
                            b.mc.player.getX(),
                            b.mc.player.getZ(),
                            b.formatBlockPos(b.mc.player.getBlockPos()),
                            targetOrderX,
                            targetOrderZ,
                            distance,
                            KITBOT_ORDER_SUBMIT_RADIUS,
                            playerBlockMatchesAnchor,
                            b.kitbotOrderCenterVerificationFailures
                        );
                    }
                    return true;
                }

                failSoftKitbotOrderCenterVerification(b, "submit-radius");
                return true;
            }

            private void failSoftKitbotOrderCenterVerification(HighwayBuilderTHM b, String reason) {
                double distance = distanceToKitbotOrderCenter(b);
                boolean playerBlockMatchesAnchor = playerBlockMatchesKitbotAnchor(b);
                b.kitbotOrderCenterVerified = false;
                b.kitbotOrderCenterVerificationActive = false;
                b.kitbotOrderCenterVerificationTicks = 0;
                b.kitbotOrderCenterVerificationRecenterTicks = 0;
                b.kitbotOrderCenterVerificationSettleTicks = 0;
                b.kitbotOrderCenterVerificationRecentered = false;
                b.kitbotOrderCenterVerificationFailures++;
                b.kitbotOrderCenterVerificationTargetX = Double.NaN;
                b.kitbotOrderCenterVerificationTargetZ = Double.NaN;
                b.kitbotOrderCenterVerificationDistance = distance;
                positionReady = false;
                b.clearKitbotCenteringRuntime("kitbot-order-verification-" + reason);

                if (b.restockDebugLog.get()) {
                    b.restockDebug(
                        "KitbotOrder center verification soft-failed (%s): player=(%.6f, %.6f) block=%s target=(%.3f, %.3f) distance=%.3f centerRadius=%.3f submitRadius=%.3f playerBlockMatchesAnchor=%s failures=%d.",
                        reason,
                        b.mc.player.getX(),
                        b.mc.player.getZ(),
                        b.formatBlockPos(b.mc.player.getBlockPos()),
                        targetOrderX,
                        targetOrderZ,
                        distance,
                        KITBOT_ORDER_CENTER_RADIUS,
                        KITBOT_ORDER_SUBMIT_RADIUS,
                        playerBlockMatchesAnchor,
                        b.kitbotOrderCenterVerificationFailures
                    );
                }
            }

            private double distanceToKitbotOrderCenter(HighwayBuilderTHM b) {
                if (b.mc.player == null) return Double.POSITIVE_INFINITY;
                return Math.hypot(targetOrderX - b.mc.player.getX(), targetOrderZ - b.mc.player.getZ());
            }

            private boolean playerBlockMatchesKitbotAnchor(HighwayBuilderTHM b) {
                if (b.mc.player == null) return false;
                BlockPos playerPos = b.mc.player.getBlockPos();
                return playerPos.getX() == b.kitbotAnchorPos.getX()
                    && playerPos.getY() == b.kitbotAnchorPos.getY()
                    && playerPos.getZ() == b.kitbotAnchorPos.getZ();
            }

            private void submitKitbotOrderIfReady(HighwayBuilderTHM b) {
                long now = System.currentTimeMillis();
                if (b.kitbotNextSubmitAtMs > 0L && now < b.kitbotNextSubmitAtMs) return;

                if (b.kitbotOrderAcceptedAttempts >= KITBOT_MAX_ACCEPTED_ATTEMPTS) {
                    failKitbotOrder(b, "Kitbot restock failed after " + KITBOT_MAX_ACCEPTED_ATTEMPTS + " accepted attempts.", FailureMode.KITBOT_LOCAL);
                    return;
                }

                KitbotRestockGate gate = b.getKitbotRestockGate();
                if (gate == KitbotRestockGate.Unavailable) {
                    failKitbotOrder(b, "KitBot is unavailable for restock.", FailureMode.KITBOT_LOCAL);
                    return;
                }

                if (gate == KitbotRestockGate.AwaitingRestart) {
                    b.input.stop();
                    b.restockWatchdog.pauseHeartbeat("kitbot-awaiting-restart");
                    return;
                }

                if (verifyKitbotOrderCenterBeforeSubmit(b)) return;

                KitbotFrontend.KitName kit = b.getKitbotKitNameForActiveRestock();
                int amount = b.getKitbotOrderAmountForActiveRestock();
                int baseline = b.kitbotOrderAcceptedAttempts == 0
                    ? b.countKitbotProofUnitsForActiveRestock()
                    : b.kitbotOrderBaselineShulkerCount;

                if (!KitbotFrontend.kitOrder(kit, amount)) {
                    long remainingWindowMs = KitbotFrontend.getRemainingWindowMs();
                    if (remainingWindowMs > 0L || KitbotFrontend.hasActiveWindow()) {
                        b.scheduleKitbotRestockSubmitAfterWindow(remainingWindowMs, "blocked frontend window");
                        if (b.restockDebugLog.get()) {
                            b.restockDebug("KitbotOrder submit blocked by frontend window; acceptedAttempts=%d/%d.",
                                b.kitbotOrderAcceptedAttempts,
                                KITBOT_MAX_ACCEPTED_ATTEMPTS
                            );
                        }
                        return;
                    }

                    failKitbotOrder(b, "Kitbot restock request could not be submitted to KitbotFrontend.", FailureMode.KITBOT_LOCAL);
                    return;
                }

                b.invalidateKitbotOrderCenterVerification("kitbot-order-accepted");
                if (b.kitbotOrderAcceptedAttempts == 0) {
                    b.kitbotOrderBaselineShulkerCount = baseline;
                    b.kitbotOrderExpectedShulkerGain = amount;
                    b.kitbotOrderLastObservedMatchingShulkerCount = baseline;
                    b.kitbotPartialDeliveryGraceUntilAge = 0;
                }

                b.kitbotOrderAcceptedAttempts++;
                b.kitbotOrderSentAtAge = b.mc.player.age;
                b.kitbotOrderInFlight = true;
                b.kitbotOrderFrontendSucceeded = false;
                b.kitbotNextSubmitAtMs = 0L;
                b.kitbotInventoryProofDeadlineMs = 0L;
                b.restockWatchdog.markProgress("kitbot-order-accepted");
                b.info(
                    "Ordering kit '%s' x%d from %s (attempt %d/%d).",
                    kit,
                    amount,
                    KITBOT_NAME,
                    b.kitbotOrderAcceptedAttempts,
                    KITBOT_MAX_ACCEPTED_ATTEMPTS
                );

                if (b.restockDebugLog.get()) {
                    b.restockDebug("KitbotOrder accepted by frontend: baseline=%d expectedGain=%d attempts=%d/%d.",
                        b.kitbotOrderBaselineShulkerCount,
                        b.kitbotOrderExpectedShulkerGain,
                        b.kitbotOrderAcceptedAttempts,
                        KITBOT_MAX_ACCEPTED_ATTEMPTS
                    );
                }
            }

            // Kits are delivered before KitBot1 walks off, and the last few land on the floor after
            // the inventory proof already passed - breaking the enclosure right then leaves them
            // behind. Hold the enclosure until the bot is gone, then a beat longer to sweep pickups.
            private boolean isKitCollectionSettled(HighwayBuilderTHM b) {
                if (b.kitbotWaitUntilGone.get() && b.kitbotSeenNearby) {
                    if (b.kitbotDepartureWaitUntilAge == 0) b.kitbotDepartureWaitUntilAge = b.mc.player.age + KITBOT_DEPARTURE_WAIT_TICKS;

                    if (b.isKitbotNearby() && b.mc.player.age < b.kitbotDepartureWaitUntilAge) {
                        b.kitbotCollectUntilAge = 0;
                        return false;
                    }
                }

                if (!b.kitbotCollectDelay.get()) return true;
                if (b.kitbotCollectUntilAge == 0) b.kitbotCollectUntilAge = b.mc.player.age + KITBOT_COLLECT_DELAY_TICKS;
                return b.mc.player.age >= b.kitbotCollectUntilAge;
            }

            private boolean hasExpectedKitDelivery(HighwayBuilderTHM b) {
                int expectedGain = Math.max(b.kitbotOrderExpectedShulkerGain, 1);
                int currentShulkerCount = b.countKitbotProofUnitsForActiveRestock();
                int targetCount = b.kitbotOrderBaselineShulkerCount + expectedGain;
                int failsafeGain = expectedGain == 1 ? 1 : expectedGain - 1;
                int failsafeTarget = b.kitbotOrderBaselineShulkerCount + failsafeGain;
                int ticksWaiting = Math.max(b.mc.player.age - b.kitbotOrderSentAtAge, 0);
                updatePartialDeliveryGrace(b, currentShulkerCount, targetCount);
                boolean partialDeliveryGraceActive = currentShulkerCount < targetCount
                    && b.kitbotPartialDeliveryGraceUntilAge > b.mc.player.age;
                boolean failsafeReady = !partialDeliveryGraceActive
                    && currentShulkerCount >= failsafeTarget
                    && ticksWaiting >= KITBOT_FAILSAFE_DELAY_TICKS;

                if (b.restockDebugLog.get()) {
                    b.restockDebug("KitbotOrder delivery progress: currentMatchingShulkers=%d target=%d baseline=%d expectedGain=%d failsafeTarget=%d ticksWaiting=%d/%d failsafeReady=%s partialGraceActive=%s partialGraceRemaining=%d.",
                        currentShulkerCount,
                        targetCount,
                        b.kitbotOrderBaselineShulkerCount,
                        expectedGain,
                        failsafeTarget,
                        ticksWaiting,
                        KITBOT_FAILSAFE_DELAY_TICKS,
                        failsafeReady,
                        partialDeliveryGraceActive,
                        Math.max(b.kitbotPartialDeliveryGraceUntilAge - b.mc.player.age, 0)
                    );
                }

                return currentShulkerCount >= targetCount || failsafeReady;
            }

            private void updatePartialDeliveryGrace(HighwayBuilderTHM b, int currentShulkerCount, int targetCount) {
                if (currentShulkerCount >= targetCount) {
                    b.kitbotOrderLastObservedMatchingShulkerCount = currentShulkerCount;
                    b.kitbotPartialDeliveryGraceUntilAge = 0;
                    return;
                }

                if (currentShulkerCount > b.kitbotOrderLastObservedMatchingShulkerCount) {
                    b.kitbotOrderLastObservedMatchingShulkerCount = currentShulkerCount;
                    b.restockWatchdog.markProgress("kitbot-proof-progress");
                    if (currentShulkerCount > b.kitbotOrderBaselineShulkerCount) {
                        b.kitbotPartialDeliveryGraceUntilAge = b.mc.player.age + KITBOT_PARTIAL_DELIVERY_GRACE_TICKS;
                        if (b.restockDebugLog.get()) {
                            b.restockDebug("KitbotOrder extended partial-delivery grace after detecting %d/%d matching shulkers (grace=%d ticks).",
                                currentShulkerCount - b.kitbotOrderBaselineShulkerCount,
                                targetCount - b.kitbotOrderBaselineShulkerCount,
                                KITBOT_PARTIAL_DELIVERY_GRACE_TICKS
                            );
                        }
                    }
                }
            }

            private int countMatchingRestockShulkersInInventory(HighwayBuilderTHM b) {
                int count = 0;

                for (int i = 0; i < b.mc.player.getInventory().getMainStacks().size(); i++) {
                    ItemStack itemStack = b.mc.player.getInventory().getStack(i);
                    if (!Utils.isShulker(itemStack.getItem())) continue;
                    if (!shulkerContainsRestockItems(b, itemStack)) continue;
                    count++;
                }

                return count;
            }

            private boolean shulkerContainsRestockItems(HighwayBuilderTHM b, ItemStack itemStack) {
                if (!Utils.isShulker(itemStack.getItem())) return false;

                for (ItemStack stack : THMUtils.getContainerContents(itemStack)) {
                    if (stack.getItem() instanceof BlockItem bi && (b.blocksToPlace.get().contains(bi.getBlock()) || (b.blocksToPlace.get().contains(Blocks.OBSIDIAN) && bi == Items.ENDER_CHEST))) {
                        return true;
                    }
                    if (b.restockTask.pickaxes && b.isActivePickaxeRestockMatch(stack)) return true;
                    if (b.restockTask.food && b.isConfiguredFoodStack(stack)) return true;
                }

                return false;
            }

            private boolean reconcileKitbotEnclosure(HighwayBuilderTHM b) {
                KitbotFootprint source = b.getNormalBlockadeFootprint(b.kitbotAnchorPos.toImmutable(), sourceBlockadeType, b.dir, containerDirection, expansionDirection);
                KitbotFootprint target = b.getKitbotEnclosureFootprint(b.kitbotAnchorPos.toImmutable(), b.dir, containerDirection, expansionDirection);
                if (probeFootprint(b, target)) return false;
                if (!ensureKitbotWalkCenteredOnBlock(b, b.kitbotAnchorPos.toImmutable(), "kitbot-enclosure-build", FailureMode.KITBOT_LOCAL)) return true;
                return reconcileFootprint(b, source, target, "Kitbot enclosure", FailureMode.KITBOT_LOCAL);
            }

            private boolean restoreNormalBlockade(HighwayBuilderTHM b) {
                KitbotFootprint source = b.getKitbotEnclosureFootprint(b.kitbotAnchorPos.toImmutable(), b.dir, containerDirection, expansionDirection);
                KitbotFootprint target = b.getNormalBlockadeFootprint(b.kitbotAnchorPos.toImmutable(), b.getEffectiveBlockadeType(), b.dir, containerDirection, expansionDirection);
                return reconcileFootprint(b, source, target, "normal blockade", FailureMode.POST_DELIVERY_RESTORE);
            }

            private boolean reconcileFootprint(HighwayBuilderTHM b, KitbotFootprint source, KitbotFootprint target, String label, FailureMode failureMode) {
                if (clearRequiredAir(b, target, label, failureMode)) return true;
                if (clearSourceOnlyBlocks(b, source, target, label, failureMode)) return true;
                if (ensureRequiredBlocks(b, target, label, failureMode)) return true;

                if (!probeFootprint(b, target)) {
                    failKitbotOrder(b, "Unable to complete the " + label + " before continuing Kitbot.", failureMode);
                    return true;
                }

                return false;
            }

            private boolean clearRequiredAir(HighwayBuilderTHM b, KitbotFootprint target, String label, FailureMode failureMode) {
                for (BlockPos pos : target.requiredAir) {
                    BlockState state = b.mc.world.getBlockState(pos);
                    if (b.isKitbotRequiredAirClear(state)) continue;

                    if (state.getBlock() == Blocks.OBSIDIAN) {
                        failKitbotOrder(b, "Obsidian is blocking required open space for the " + label + ".", failureMode);
                        return true;
                    }

                    if (breakBlockingBlock(b, pos, state, label, failureMode)) return true;
                }

                return false;
            }

            private boolean clearSourceOnlyBlocks(HighwayBuilderTHM b, KitbotFootprint source, KitbotFootprint target, String label, FailureMode failureMode) {
                for (BlockPos pos : source.requiredBlocks.keySet()) {
                    if (target.requiredBlocks.containsKey(pos) || target.requiredAir.contains(pos)) continue;

                    BlockState state = b.mc.world.getBlockState(pos);
                    if (b.isKitbotRequiredAirClear(state) || state.getBlock() == Blocks.OBSIDIAN) continue;

                    if (breakBlockingBlock(b, pos, state, label, failureMode)) return true;
                }

                return false;
            }

            private boolean ensureRequiredBlocks(HighwayBuilderTHM b, KitbotFootprint target, String label, FailureMode failureMode) {
                if (b.handlePendingEnclosurePlacementConfirmation(KitbotOrder, () ->
                    failKitbotOrder(
                        b,
                        "Unable to complete the " + label + ": placement at " + b.formatBlockPos(b.pendingEnclosurePlacementConfirmPos) + " did not confirm after centering.",
                        failureMode
                    )
                )) return true;

                for (Map.Entry<BlockPos, KitbotStructureBlockType> entry : target.requiredBlocks.entrySet()) {
                    BlockPos pos = entry.getKey();
                    KitbotStructureBlockType type = entry.getValue();
                    BlockState state = b.mc.world.getBlockState(pos);

                    if (b.isSatisfiedKitbotStructureBlock(state, type)) {
                        continue;
                    }

                    if (state.getBlock() == Blocks.OBSIDIAN) {
                        failKitbotOrder(b, "Obsidian is blocking a required " + type.name().toLowerCase(Locale.ROOT) + " block for the " + label + ".", failureMode);
                        return true;
                    }

                    if (!b.isKitbotRequiredAirClear(state)) {
                        if (breakBlockingBlock(b, pos, state, label, failureMode)) return true;
                        continue;
                    }

                    if (!BlockUtils.canPlace(pos)) {
                        if (b.restockDebugLog.get()) {
                            b.restockDebug("KitbotOrder waiting to place %s block at %s while reconciling the %s because BlockUtils.canPlace=false.",
                                type.name().toLowerCase(Locale.ROOT),
                                b.formatBlockPos(pos),
                                label
                            );
                        }
                        return true;
                    }

                    if (b.placeTimer > 0) return true;

                    BlockPos centerTarget = b.enclosureCenterTargetForState(KitbotOrder);
                    if (b.isPlayerHitboxBlockingBlock(pos)) {
                        if (!ensureKitbotWalkCenteredOnBlock(b, centerTarget, "kitbot-" + label + "-target-blocked", failureMode)) return true;
                        failKitbotOrder(
                            b,
                            "Unable to complete the " + label + ": player hitbox is blocking required block " + b.formatBlockPos(pos) + " after centering.",
                            failureMode
                        );
                        return true;
                    }

                    int slot = findKitbotBlockSlot(b);
                    if (slot == -1) {
                        failKitbotOrder(b, "Not enough blocks are available to complete the " + label + ".", failureMode);
                        return true;
                    }

                    if (!b.consumeEnclosurePlaceCredit()) {
                        if (b.restockDebugLog.get()) {
                            b.restockDebug("KitbotOrder paused: enclosure placement throttle credit=%d/%d before placing %s block at %s while reconciling the %s.",
                                b.enclosurePlaceCreditTenths,
                                ENCLOSURE_PLACE_CREDIT_SCALE,
                                type.name().toLowerCase(Locale.ROOT),
                                b.formatBlockPos(pos),
                                label
                            );
                        }
                        return true;
                    }

                    if (BlockUtils.place(pos, Hand.MAIN_HAND, slot, b.rotation.get().place, 0, true, true, true)) {
                        b.placeTimer = b.placeDelay.get();
                        b.startEnclosurePlacementConfirmation(KitbotOrder, pos, centerTarget, type, label);
                        if (b.restockDebugLog.get()) {
                            b.restockDebug("KitbotOrder attempted %s block at %s while reconciling the %s; waiting for confirmation.",
                                type.name().toLowerCase(Locale.ROOT),
                                b.formatBlockPos(pos),
                                label
                            );
                        }
                    } else if (b.restockDebugLog.get()) {
                        b.restockDebug("KitbotOrder placement call returned false for %s block at %s while reconciling the %s; retrying.",
                            type.name().toLowerCase(Locale.ROOT),
                            b.formatBlockPos(pos),
                            label
                        );
                    }

                    return true;
                }

                return false;
            }

            private boolean breakBlockingBlock(HighwayBuilderTHM b, BlockPos pos, BlockState state, String label, FailureMode failureMode) {
                if (b.isKitbotRequiredAirClear(state)) return false;
                if (b.breakTimer > 0) return true;
                if (!b.safeCanBreak(pos, state)) {
                    failKitbotOrder(b, "Unable to clear blocking " + state.getBlock().getName().getString() + " at " + b.formatBlockPos(pos) + " while reconciling the " + label + ".", failureMode);
                    return true;
                }

                int selectedSlot = b.mc.player.getInventory().getSelectedSlot();
                int slot = findAndMoveBestToolToHotbar(b, state, false, false);
                if (slot == -1) slot = selectedSlot;

                if (selectedSlot != slot) InvUtils.swap(slot, false);
                if (b.rotation.get().mine) Rotations.rotate(Rotations.getYaw(pos), Rotations.getPitch(pos), () -> BlockUtils.breakBlock(pos, true));
                else BlockUtils.breakBlock(pos, true);
                b.breakTimer = b.breakDelay.get();

                if (!b.lastBreakingPos.equals(pos)) {
                    b.lastBreakingPos.set(pos.getX(), pos.getY(), pos.getZ());
                    b.recordConfirmedBrokenBlockForStats(pos);
                }

                if (b.restockDebugLog.get()) {
                    b.restockDebug("KitbotOrder broke blocking block %s while reconciling the %s.", b.formatBlockPos(pos), label);
                }

                return true;
            }

            private boolean probeFootprint(HighwayBuilderTHM b, KitbotFootprint footprint) {
                for (BlockPos pos : footprint.requiredAir) {
                    if (!b.isKitbotRequiredAirClear(b.mc.world.getBlockState(pos))) return false;
                }

                for (Map.Entry<BlockPos, KitbotStructureBlockType> entry : footprint.requiredBlocks.entrySet()) {
                    if (!b.isSatisfiedKitbotStructureBlock(b.mc.world.getBlockState(entry.getKey()), entry.getValue())) return false;
                }

                return true;
            }

            private int findKitbotBlockSlot(HighwayBuilderTHM b) {
                int slot = findPreferredTrashBlockToHotbar(b, false);

                if (slot != -1) return slot;

                return findAndMoveToHotbar(b, itemStack -> itemStack.getItem() instanceof BlockItem blockItem && b.blocksToPlace.get().contains(blockItem.getBlock()), false);
            }

            private void finishSuccessfulKitbotHandoff(HighwayBuilderTHM b) {
                b.returnPlayerToKitbotAnchorIfSaved();
                b.restockTask.setBlockadeReady(true);
                if (!b.restockTask.validateOrInvalidateBlockadeLease()) {
                    failKitbotOrder(b, "Kitbot restore completed, but the restored blockade lease could not be revalidated.", FailureMode.POST_DELIVERY_RESTORE);
                    return;
                }

                boolean sessionUsableAfterKitbotClamp = true;
                if (b.restockTask.isSequenceActive() && !b.restockTask.tasksInactive()) {
                    sessionUsableAfterKitbotClamp = b.restockTask.recalculateFrozenSessionCapacityAfterKitbotDelivery();
                }

                b.clearKitbotRuntimeState("kitbot-order-restored");

                if (b.restockTask.isSequenceActive() && !b.restockTask.tasksInactive()) {
                    if (!sessionUsableAfterKitbotClamp) {
                        if (b.restockDebugLog.get()) {
                            b.restockDebug("KitbotOrder post-delivery frozen target clamp found no loose capacity yet; reopening inventory shulkers so the delivered kit can be placed and processed.");
                        }
                    }

                    // Kitbot just injected fresh shulker supply into inventory, so the pre-Kitbot
                    // "inventory shulkers exhausted" result must be reopened before source selection reruns.
                    b.restockTask.reopenSourcePhase(RestockTask.SourcePhase.InventoryShulkers);
                    b.restockDebug("KitbotOrder restored the blockade and is returning to Restock.");
                    b.setState(Restock);
                } else {
                    b.setState(Forward);
                }
            }

            private void failKitbotOrder(HighwayBuilderTHM b, String message, FailureMode failureMode) {
                if (failureMode == FailureMode.POST_DELIVERY_RESTORE && b.restockDebugLog.get()) {
                    b.restockDebug("KitbotOrder post-delivery restore failure: %s", message);
                }
                b.failActiveKitbotRestock(message, failureMode == FailureMode.KITBOT_LOCAL);
            }
        },

        // this one was rough to do
        Restock {
            private static final MBlockPos pos = new MBlockPos();
            private static final int INVALID_RESTOCK_RECOVERY_MAX_RETRIES = 1;
            private static final int SOURCE_READY_MAX_RETRIES = 3;
            private static final int ENDER_CHEST_LIVE_RECHECK_COOLDOWN_TICKS = 40;
            private static final int SOURCE_SELECTION_MOVE_FAILED = -2;
            private static final String ECHEST_RESERVE_NONE = "none";
            private static final String ECHEST_RESERVE_MATCHING = "matching";
            private static final String ECHEST_RESERVE_OBSIDIAN = "obsidian";
            private static final String ECHEST_RESERVE_RAW_ENDER_CHEST = "ender_chest";
            private int minimumSlots, stopTimer, delayTimer;
            private boolean breakContainer, indicateStopping, transitionToMineEnderChests;
            private int restockPickaxesStartCount;
            private Predicate<ItemStack> shulkerPredicate;
            private Predicate<ItemStack> sourceItemPredicate;
            private String sourceLabel;
            private int sourceReadyRetries;
            private boolean usingPlacedEnderChestSource;
            private boolean extractedFoodShulkerFromEnderChest;
            private boolean foodShulkerReturnPending;
            private boolean foodShulkerReturnRetryUsed;
            private boolean foodShulkerReturnFinalizeAfterBreak;
            private int foodShulkerReturnWaitTicks;
            private int foodShulkerReturnInventorySlot = -1;
            private boolean extractedEnderChestShulkerFromEnderChest;
            private boolean enderChestShulkerReturnPending;
            private boolean enderChestShulkerReturnRetryUsed;
            private boolean enderChestShulkerReturnFinalizeAfterBreak;
            private int enderChestShulkerReturnWaitTicks;
            private int enderChestShulkerReturnInventorySlot = -1;
            private int reservedUnitsThisEnderChestPass;
            private String reservedUnitsKindThisEnderChestPass = ECHEST_RESERVE_NONE;
            // if this is ever not -1 when we expect it to be, things break a lot
            private int slot = -1;

            @Override
            protected void start(HighwayBuilderTHM b) {
                restockPickaxesStartCount = b.restockTask.getPickaxeStartCount();
                b.restockTask.refreshSessionProgress();
                b.restockTask.notePhase(RestockTask.SourcePhase.Inventory);

                b.restockDebug("Restock.start(active=%s, pending=%s, blockadeReady=%s, sequence=%s, lastState=%s)",
                    b.restockTask.activeSummary(),
                    b.restockTask.pendingSummary(),
                    b.restockTask.isBlockadeReady(),
                    b.restockTask.isSequenceActive(),
                    b.stateName(b.lastState)
                );

                if (b.lastState == PlaceShulkerBlockade) {
                    b.restockTask.setBlockadeReady(true);
                    b.restockDebug("Restock.start marked blockade ready after %s.", b.stateName(b.lastState));
                }
                else if (b.restockTask.isBlockadeReady() && !b.restockTask.validateOrInvalidateBlockadeLease()) {
                    b.restockDebug("Restock.start invalidated stale blockade lease; requesting rebuild.");
                }

                slot = -1; // :ptsd:
                sourceItemPredicate = null;
                sourceLabel = "unknown";
                sourceReadyRetries = 0;
                transitionToMineEnderChests = false;
                usingPlacedEnderChestSource = false;
                resetEnderChestExtractionReserve(b, "Restock.start");
                if (!b.restockTask.food) clearFoodReturnTracking();
                if (!b.restockTask.enderChests) clearEnderChestShulkerReturnTracking();
                // set the predicate to test for shulker boxes
                if (shulkerPredicate == null) setShulkerPredicate(b);

                if (b.restockTask.tasksInactive()) {
                    if (b.restockTask.advanceToPendingTask()) {
                        start(b);
                        return;
                    }

                    b.restockTask.finishSequence();
                    b.setState(Forward);
                    return;
                }

                if (b.restockTask.enderChests && !b.shouldTriggerEnderChestReserveRestock()) {
                    int looseCount = b.countLooseInventoryEnderChests();
                    int threshold = Math.max(b.saveEchests.get() - 1, 0);
                    if (!b.isEnderChestReserveRestockEnabled()) {
                        b.restockDebug("Restock.start skipping stale ender chest reserve restock because searchEnderChest is disabled (looseCount=%d, threshold=%d).",
                            looseCount,
                            threshold
                        );
                    } else {
                        b.restockDebug("Restock.start skipping stale ender chest reserve restock because loose count has recovered to %d (threshold=%d).",
                            looseCount,
                            threshold
                        );
                    }
                    b.completeRestockTaskAndContinue();
                    return;
                }

                if (b.restockTask.food) {
                    int looseFoodCount = b.countConfiguredFoodItemsInInventory();
                    if (looseFoodCount > b.saveFood.get()) {
                        if (b.restockDebugLog.get()) {
                            b.restockDebug("Restock.start skipping stale food restock because loose food count has recovered to %d (threshold=%d).",
                                looseFoodCount,
                                b.saveFood.get()
                            );
                        }
                        b.completeRestockTaskAndContinue();
                        return;
                    }
                }

                if (!b.restockTask.isBlockadeReady()) {
                    if (b.lastState == Center) {
                        b.restockDebug("Restock.start requesting ThrowOutTrash before blockade placement.");
                        b.setState(ThrowOutTrash);
                        return;
                    }
                    else if (b.lastState == ThrowOutTrash) {
                        b.restockDebug("Restock.start requesting PlaceShulkerBlockade using effective type %s.", b.getEffectiveBlockadeType());
                        b.setState(PlaceShulkerBlockade);
                        return;
                    }

                    if (b.lastState == this || b.lastState == PlaceShulkerBlockade) {
                        b.restockDebug("Restock.start restarting blockade setup from Center because blockadeReady=false after %s.",
                            b.stateName(b.lastState)
                        );
                    } else {
                        b.restockDebug("Restock.start requesting Center before blockade placement.");
                    }

                    b.setState(Center);
                    return;
                }

                if (!b.restockTask.freezeSessionTargetForSourceSelection()) {
                    b.restockTask.reportUnusableSessionTarget();
                    return;
                }

                if (runObsidianPickaxePreflight(b)) return;

                BlockPos sourceContainerPos = b.restockTask.getSourceContainerPos();
                if (sourceContainerPos == null) {
                    b.restockDebug("Restock.start could not resolve the dedicated source container slot from the blockade lease; rebuilding from Center.");
                    b.restockTask.setBlockadeReady(false);
                    b.setState(Center);
                    return;
                }

                pos.set(sourceContainerPos.getX(), sourceContainerPos.getY(), sourceContainerPos.getZ());
                BlockPos restockBlockPos = pos.getBlockPos();
                boolean hasPlacedRestockEnderChest = b.mc.world.getBlockState(restockBlockPos).getBlock() == Blocks.ENDER_CHEST;

                refreshStaleSourceExhaustionFlags(b, hasPlacedRestockEnderChest);

                // firstly search your inventory for shulkers that have the items you need
                if (slot == -1 && b.searchShulkers.get() && (b.restockTask.getSession() == null || !b.restockTask.getSession().isInventoryShulkersExhausted())) {
                    b.restockTask.notePhase(RestockTask.SourcePhase.InventoryShulkers);
                    sourceItemPredicate = shulkerPredicate;
                    sourceLabel = "shulker";
                    int selectedRestockShulkerSlot = findAndMoveBestRestockShulkerToHotbar(b);
                    if (selectedRestockShulkerSlot >= 0) {
                        slot = selectedRestockShulkerSlot;
                    }
                    else if (selectedRestockShulkerSlot == -1) {
                        if (b.restockDebugLog.get()) {
                            if (isObsidianRestockTask(b)) {
                                Inventory playerInventory = b.mc.player.getInventory();
                                b.restockDebug("Restock.start found no currently usable inventory shulker for obsidian restock (usableLooseEchests=%d, obsidianShulkers=%d, rawEchestShulkers=%d, mineEnderChests=%s, searchShulkers=%s).",
                                    countUsableLooseInventoryEnderChests(b),
                                    countRestockShulkersInInventoryByKind(b, playerInventory, ECHEST_RESERVE_OBSIDIAN),
                                    countRestockShulkersInInventoryByKind(b, playerInventory, ECHEST_RESERVE_RAW_ENDER_CHEST),
                                    canMineEnderChestsForObsidian(b),
                                    b.searchShulkers.get()
                                );
                            } else {
                                b.restockDebug("Restock.start found no matching inventory shulker for task=%s (matchingInventoryShulkers=%d, searchShulkers=%s).",
                                    b.restockTask.item(),
                                    countMatchingRestockShulkersInPlayerInventory(b),
                                    b.searchShulkers.get()
                                );
                            }
                        }
                        b.restockTask.markCurrentSourceExhausted(RestockTask.SourcePhase.InventoryShulkers);
                        sourceItemPredicate = null;
                        sourceLabel = "unknown";
                    } else {
                        sourceItemPredicate = null;
                        sourceLabel = "unknown";
                        return;
                    }
                }

                if (!hasSelectedRestockSource() && b.restockTask.isCurrentTaskSuccessful()) {
                    b.completeRestockTaskAndContinue();
                    return;
                }

                if (!hasSelectedRestockSource() && shouldMineEnderChestsForMaterials(b) && (b.restockTask.getSession() == null || !b.restockTask.getSession().isMineEnderChestsExhausted())) {
                    b.restockTask.shrinkObsidianTargetToCurrentRawEChestBatch("loose-echest-mining");
                    b.restockTask.refreshSessionProgress();
                    if (routeMissingPickaxeBeforeEChestPath(b, "obsidian loose e-chest mining")) return;
                    logNoSelectedSourceDecision(b, "Restock.start found no inventory shulker source; continuing into MineEnderChests.");
                    b.restockDebug("Restock.start found %d usable loose ender chests in inventory; continuing into MineEnderChests.",
                        countUsableLooseInventoryEnderChests(b)
                    );
                    b.setState(MineEnderChests);
                    return;
                }

                if (!hasSelectedRestockSource()
                    && shouldTryKitbotBeforeEnderChest(b)
                    && tryStartKitbotFallback(b, "Restock.start trying KitBot before ender chest bank per restock-secondary-source-order.")) {
                    return;
                }

                // next search your ender chest for raw items and shulkers containing items
                if (slot == -1
                    && b.searchEnderChest.get()
                    && (countItem(b, stack -> stack.getItem().equals(Items.ENDER_CHEST)) > 0 || hasPlacedRestockEnderChest)
                    && (b.restockTask.getSession() == null || !b.restockTask.getSession().isEnderChestExhausted())) {
                    if (routeMissingPickaxeBeforeEChestPath(b, "ender chest bank restock")) return;
                    b.restockTask.notePhase(RestockTask.SourcePhase.EnderChest);

                    boolean stop = EChestMemory.isKnown();
                    int cachedRawMatches = EChestMemory.isKnown() ? countRestockRawMatchesInContainer(EChestMemory.ITEMS, b) : 0;
                    int cachedMatchingShulkers = EChestMemory.isKnown() ? countMatchingRestockShulkersInContainerStacks(EChestMemory.ITEMS, b) : 0;
                    if (EChestMemory.isKnown()) {
                        for (ItemStack stack : EChestMemory.ITEMS) {
                            if (b.restockTask.materials && stack.getItem() instanceof BlockItem bi) {
                                if (b.blocksToPlace.get().contains(bi.getBlock()) || (b.blocksToPlace.get().contains(Blocks.OBSIDIAN) && isRawEChestStack(stack) && needsMoreRawEchests(b))) {
                                    stop = false;
                                    break;
                                }
                            }
                            if (b.restockTask.pickaxes && b.isActivePickaxeRestockMatch(stack)) {
                                stop = false;
                                break;
                            }
                            if (b.restockTask.food && b.isConfiguredFoodStack(stack)) {
                                stop = false;
                                break;
                            }
                            if (b.restockTask.enderChests && stack.getItem() == Items.ENDER_CHEST) {
                                stop = false;
                                break;
                            }

                            if (b.searchShulkers.get() && shulkerPredicate.test(stack)) {
                                stop = false;
                                break;
                            }
                        }
                    }

                    if (b.restockDebugLog.get()) {
                        b.restockDebug("Restock.start ender chest precheck: known=%s stop=%s placed=%s looseEchests=%d memoryRawMatches=%d memoryMatchingShulkers=%d.",
                            EChestMemory.isKnown(),
                            stop,
                            hasPlacedRestockEnderChest,
                            countItem(b, stack -> stack.getItem().equals(Items.ENDER_CHEST)),
                            cachedRawMatches,
                            cachedMatchingShulkers
                        );
                    }

                    if (stop) {
                        if (b.restockDebugLog.get()) {
                            b.restockDebug("Restock.start cached ender chest precheck found no immediate matches, but ender chest access is available so a live chest check will still be attempted.");
                        }
                    }

                    if (hasPlacedRestockEnderChest) {
                        usingPlacedEnderChestSource = true;
                        sourceLabel = "ender_chest_placed";
                    } else {
                        sourceItemPredicate = itemStack -> itemStack.getItem() == Items.ENDER_CHEST;
                        sourceLabel = "ender_chest";
                        slot = findAndMoveToHotbar(b, sourceItemPredicate, false);
                        if (slot == -1) {
                            if (b.restockDebugLog.get()) {
                                b.restockDebug("Restock.start could not move an ender chest item to hotbar for restock source selection.");
                            }
                            sourceItemPredicate = null;
                            sourceLabel = "unknown";
                        }
                    }
                }

                if (!hasSelectedRestockSource()
                    && !shouldTryKitbotBeforeEnderChest(b)
                    && tryStartKitbotFallback(b, "Restock.start trying KitBot after inventory and ender chest bank sources made no useful progress.")) {
                    return;
                }

                if (!hasSelectedRestockSource() && shouldHardFailObsidianRawInventorySupply(b)) {
                    logNoSelectedSourceDecision(b, "Restock.start hitting obsidian raw-supply hard fail because no selected restock source remains.");
                    b.restockTask.refreshSessionProgress();
                    if (b.restockTask.isCurrentTaskSuccessfulAtFinalNoSource()) {
                        b.completeRestockTaskAndContinue();
                        return;
                    }

                    String reason = "Unable to perform obsidian restock: only raw ender chest supply remains, but Mine Ender Chests is disabled.";
                    if (b.restockTask.getSession() != null) b.restockTask.getSession().fail();
                    b.notifyDesktop(b.notifyRestockIssues, "THM Highway Builder", reason);
                    b.error(reason);
                    return;
                }

                // by this point we have searched shulkers and your ender chest, and no more items could be found to pull from
                if (!hasSelectedRestockSource()) {
                    logNoSelectedSourceDecision(b, "Restock.start reached final no-source resolution.");
                    b.restockTask.refreshSessionProgress();
                    if (b.restockTask.isCurrentTaskSuccessfulAtFinalNoSource()) {
                        b.completeRestockTaskAndContinue();
                    } else {
                        b.restockTask.failActiveTaskHard();
                    }

                    return;
                }

                minimumSlots = b.restockTask.getTargetFinal();

                b.restockDebug("Restock.start container position set to %s, slot=%d, minimumSlots=%d.",
                    b.formatBlockPos(pos.getBlockPos()),
                    slot,
                    minimumSlots
                );
                if (usingPlacedEnderChestSource) {
                    b.restockDebug("Restock.start selected source=%s using already-placed ender chest at %s.",
                        sourceLabel,
                        b.formatBlockPos(pos.getBlockPos())
                    );
                }
                else if (slot >= 0) {
                    ItemStack hotbarStack = b.mc.player.getInventory().getStack(slot);
                    b.restockDebug("Restock.start selected source=%s, hotbar slot %d now holds %s, ready=%s.",
                        sourceLabel,
                        slot,
                        hotbarStack.getItem(),
                        isSelectedRestockSourceReady(b)
                    );
                }

                // Quick fix for a specific issue - if your pickaxe breaks while mining echests, it will start a new
                // task to restock pickaxes. However, there will be an echest placed down in the same position specified
                // above, and if you have the search echest setting enabled it will assume it needs to pull items from
                // your echest, even if you have a shulker full of pickaxes in your inventory.
                breakContainer = !usingPlacedEnderChestSource && b.mc.world.getBlockState(pos.getBlockPos()).getBlock() == Blocks.ENDER_CHEST;
                b.restockDebug("Restock.start breakContainer=%s because block at restock pos is %s.",
                    breakContainer,
                    b.mc.world.getBlockState(pos.getBlockPos()).getBlock()
                );

                indicateStopping = false;
                delayTimer = b.inventoryDelay.get();
            }

            private void refreshStaleSourceExhaustionFlags(HighwayBuilderTHM b, boolean hasPlacedRestockEnderChest) {
                RestockTask.RestockSession session = b.restockTask.getSession();
                if (session == null) return;

                if (session.isInventoryShulkersExhausted()) {
                    int matchingInventoryShulkers = countMatchingRestockShulkersInPlayerInventory(b);
                    if (matchingInventoryShulkers > 0) {
                        b.restockTask.reopenSourcePhase(RestockTask.SourcePhase.InventoryShulkers);
                        if (b.restockDebugLog.get()) {
                            b.restockDebug("Restock.start reopened InventoryShulkers because matching inventory shulker supply changed (matchingInventoryShulkers=%d).",
                                matchingInventoryShulkers
                            );
                        }
                    }
                }

                if (session.isMineEnderChestsExhausted()) {
                    int usableLooseEchests = countUsableLooseInventoryEnderChests(b);
                    if (shouldMineEnderChestsForMaterials(b) && usableLooseEchests > 0) {
                        b.restockTask.reopenSourcePhase(RestockTask.SourcePhase.MineEnderChests);
                        if (b.restockDebugLog.get()) {
                            b.restockDebug("Restock.start reopened MineEnderChests because usable loose ender chest supply changed (usableLooseEchests=%d).",
                                usableLooseEchests
                            );
                        }
                    }
                }

                if (session.isEnderChestExhausted()) {
                    boolean memoryKnown = EChestMemory.isKnown();
                    int rawMatches = memoryKnown ? countRestockRawMatchesInContainer(EChestMemory.ITEMS, b) : 0;
                    int matchingShulkers = memoryKnown ? countMatchingRestockShulkersInContainerStacks(EChestMemory.ITEMS, b) : 0;
                    boolean accessAvailable = isEnderChestAccessAvailable(b, hasPlacedRestockEnderChest);

                    if (session.shouldReopenEnderChestAfterMeaningfulChange(memoryKnown, rawMatches, matchingShulkers, accessAvailable)) {
                        session.reopenEnderChestAfterMeaningfulChange();
                        if (b.restockDebugLog.get()) {
                            b.restockDebug("Restock.start reopened EnderChest because the observed chest state changed (known=%s, rawMatches=%d, matchingShulkers=%d, accessAvailable=%s).",
                                memoryKnown,
                                rawMatches,
                                matchingShulkers,
                                accessAvailable
                            );
                        }
                    }
                    else if (session.shouldForceDelayedEnderChestLiveRecheck(accessAvailable, ENDER_CHEST_LIVE_RECHECK_COOLDOWN_TICKS)) {
                        session.prepareForcedEnderChestLiveRecheck();
                        if (b.restockDebugLog.get()) {
                            b.restockDebug("Restock.start reopening EnderChest for one delayed live recheck after stale-cache cooldown (known=%s, rawMatches=%d, matchingShulkers=%d, accessAvailable=%s).",
                                memoryKnown,
                                rawMatches,
                                matchingShulkers,
                                accessAvailable
                            );
                        }
                    }
                }
            }

            @Override
            protected void tick(HighwayBuilderTHM b) {
                // this should only tick if there's a valid slot we can restock from
                if (slot == -1 && !usingPlacedEnderChestSource && !foodShulkerReturnPending && !enderChestShulkerReturnPending) {
                    b.restockDebug("Restock.tick hit invalid slot=-1.");
                    b.notifyDesktop(b.notifyRestockIssues, "THM Highway Builder", "Invalid restocking action.");
                    b.error("Invalid restocking action.");
                    return;
                }

                if (clearCursor(b)) return;

                // prevent tasks executing when they shouldn't
                if (b.restockTask.tasksInactive()) {
                    if (clearCursor(b)) return;
                    b.setState(Forward);
                    return;
                }

                if (delayTimer > 0) {
                    delayTimer--;
                    return;
                }

                if (!b.mc.player.currentScreenHandler.getCursorStack().isEmpty()) {
                    b.restockDebug("Restock.tick cursor stack not empty: %s.", b.mc.player.currentScreenHandler.getCursorStack().getItem());
                    if (b.mc.currentScreen != null) b.closeHandledScreen();
                    if (!b.mc.player.currentScreenHandler.getCursorStack().isEmpty()
                        && !b.clearCursorStackToEmptySlot("Restock.tick")
                        && !b.dropCursorStackIfSafe("Restock.tick")) {
                    }
                    delayTimer = b.inventoryDelay.get();
                    return;
                }

                BlockPos blockPos = pos.getBlockPos();
                if (foodShulkerReturnPending && handleFoodShulkerReturn(b, blockPos)) return;
                if (enderChestShulkerReturnPending && handleEnderChestShulkerReturn(b, blockPos)) return;

                if (indicateStopping && !breakContainer) {
                    if (stopTimer > 0) stopTimer--;
                    else if (transitionToMineEnderChests) b.setState(MineEnderChests);
                    else b.completeRestockTaskAndContinue();

                    return;
                }

                b.restockTask.refreshSessionProgress();
                int slotsPulled = b.restockTask.getSession() != null ? b.restockTask.getSession().getProgressTowardsTarget() : 0;
                // whether we have pulled the minimum amount of items we want
                if (slotsPulled >= minimumSlots && !indicateStopping) {
                    if (b.restockTask.food) {
                        Inventory foodContainerInventory = null;
                        boolean directEnderChestFoodContainer = false;

                        if (b.mc.currentScreen instanceof ShulkerBoxScreen screen
                            && screen.getScreenHandler().syncId == b.syncId) {
                            foodContainerInventory = ((ShulkerBoxScreenHandlerAccessor) screen.getScreenHandler()).meteor$getInventory();
                        }
                        else if (b.mc.currentScreen instanceof GenericContainerScreen screen
                            && screen.getScreenHandler().syncId == b.syncId) {
                            foodContainerInventory = screen.getScreenHandler().getInventory();
                            directEnderChestFoodContainer = true;
                        }

                        if (!directEnderChestFoodContainer
                            && foodContainerInventory != null
                            && extractedFoodShulkerFromEnderChest) {
                            prepareFoodShulkerReturn(b, foodContainerInventory);
                        }
                    }
                    if (b.restockTask.enderChests
                        && b.mc.currentScreen instanceof ShulkerBoxScreen screen
                        && screen.getScreenHandler().syncId == b.syncId) {
                        Inventory inv = ((ShulkerBoxScreenHandlerAccessor) screen.getScreenHandler()).meteor$getInventory();
                        if (returnSmallestExtraEnderChestStackToContainer(b)) {
                            delayTimer = b.inventoryDelay.get();
                            return;
                        }
                        if (extractedEnderChestShulkerFromEnderChest) {
                            prepareEnderChestShulkerReturn(b, inv);
                        }
                    }
                    indicateStopping = true;
                    breakContainer = true;
                    stopTimer = 12;
                    if (b.mc.currentScreen != null) b.closeHandledScreen();
                    return;
                }
                if (b.restockTask.canTransitionToMineEnderChests() && !indicateStopping) {
                    b.restockTask.shrinkObsidianTargetToCurrentRawEChestBatch("transition-to-mine-echests");
                    b.restockTask.refreshSessionProgress();
                    if (routeMissingPickaxeBeforeEChestPath(b, "obsidian loose e-chest mining")) return;
                    transitionToMineEnderChests = true;
                    indicateStopping = true;
                    breakContainer = true;
                    stopTimer = 12;
                    if (b.mc.currentScreen != null) b.closeHandledScreen();
                    return;
                }

                // Check block state
                BlockState blockState = b.mc.world.getBlockState(blockPos);
                b.restockDebug("Restock.tick container probe pos=%s block=%s breakContainer=%s indicateStopping=%s slotsPulled=%d/%d",
                    b.formatBlockPos(blockPos),
                    blockState.getBlock(),
                    breakContainer,
                    indicateStopping,
                    slotsPulled,
                    minimumSlots
                );

                switch (blockState.getBlock()) {
                    // if we have placed a shulker box there should be items inside we want
                    case ShulkerBoxBlock ignored -> {
                        if (b.mc.currentScreen instanceof ShulkerBoxScreen screen) {
                            // wait for the screen to be properly loaded
                            if (screen.getScreenHandler().syncId != b.syncId) return;

                            Inventory inv = ((ShulkerBoxScreenHandlerAccessor) screen.getScreenHandler()).meteor$getInventory();

                            if (restockItems(b, inv)) {
                                delayTimer = b.inventoryDelay.get();
                                return;
                            }

                            // we have taken everything we can from the shulker box, and since slotsPulled >= minimumSlots is false, we should keep going
                            // close the screen, break the shulker box, look for more containers to loot from
                            if ("shulker".equals(sourceLabel) && b.restockTask.shouldCompleteAfterInventoryShulkers()) {
                                indicateStopping = true;
                                stopTimer = 12;
                            }
                            b.closeHandledScreen();
                            breakContainer = true;
                        }
                        else {
                            if (!b.searchShulkers.get()) breakContainer = true;
                            handleContainerBlock(b, blockPos);
                        }
                    }

                    // we are either pulling items themselves, or shulkers containing items from your ec
                    case EnderChestBlock ignored -> {
                        if (b.mc.currentScreen instanceof GenericContainerScreen screen) {
                            // wait for the screen to be properly loaded
                            if (screen.getScreenHandler().syncId != b.syncId) return;

                        Inventory inv = screen.getScreenHandler().getInventory();

                        if (handleEnderChestRestockStep(b, inv)) return;

                        // if it reaches here, we have taken everything we can from your ender chest, and may have also grabbed a shulker
                        // we should be finished in your ender chest, so we can break it and either continue on our way or start checking shulkers
                        b.restockTask.markEnderChestSourceExhaustedAfterLiveCheck(
                            EChestMemory.isKnown(),
                            countRestockRawMatchesInContainer(inv, b),
                            countMatchingRestockShulkersInInventory(inv),
                            isEnderChestAccessAvailable(b, true)
                        );
                        b.closeHandledScreen();
                            breakContainer = true;
                        }
                        else {
                            if (!b.searchEnderChest.get()) breakContainer = true;
                            handleContainerBlock(b, blockPos);
                        }
                    }

                    // handling when there is no container there
                    case AirBlock ignored -> {
                        // indicates we have just broken a container
                        if (breakContainer) {
                            breakContainer = false;

                            // if we don't signal intent to stop, we loop back to the start and continue restocking
                            if (indicateStopping) {
                                if (transitionToMineEnderChests) b.setState(MineEnderChests);
                                else b.completeRestockTaskAndContinue();
                            }
                            else start(b);

                            return;
                        }

                        if (usingPlacedEnderChestSource) {
                            b.restockDebug("Restock.tick expected a placed ender chest source at %s but found air; restarting source selection.", b.formatBlockPos(blockPos));
                            start(b);
                            return;
                        }

                        if (!isSelectedRestockSourceReady(b)) {
                            sourceReadyRetries++;
                            b.restockDebug("Restock.tick waiting for source item before container placement. source=%s hotbarSlot=%d currentItem=%s retry=%d/%d",
                                sourceLabel,
                                slot,
                                slot >= 0 && slot < 9 ? b.mc.player.getInventory().getStack(slot).getItem() : Items.AIR,
                                sourceReadyRetries,
                                SOURCE_READY_MAX_RETRIES
                            );

                            if (sourceReadyRetries >= SOURCE_READY_MAX_RETRIES) {
                                b.restockDebug("Restock.tick source was not ready after %d retries, restarting source selection.", SOURCE_READY_MAX_RETRIES);
                                start(b);
                            } else {
                                delayTimer = Math.max(delayTimer, b.inventoryDelay.get());
                            }
                            return;
                        }

                        sourceReadyRetries = 0;
                        if (!BlockUtils.place(blockPos, Hand.MAIN_HAND, slot, b.rotation.get().place, 0, true, true, true)
                            && b.restockDebugLog.get()) {
                            b.restockDebug("Restock.tick did not place source=%s at %s this tick; waiting for the next world probe.",
                                sourceLabel,
                                b.formatBlockPos(blockPos)
                            );
                        }
                        delayTimer = b.inventoryDelay.get();
                    }

                    // the only valid blocks should be air, a shulker box, or an ender chest
                    // if there is another type of block, attempt one full recovery:
                    // break invalid block -> mine blockade -> retry full restock
                    default -> {
                        if (b.invalidRestockRecoveryRetries < INVALID_RESTOCK_RECOVERY_MAX_RETRIES) {
                            b.invalidRestockRecoveryRetries++;
                            b.invalidRestockRecoveryPending = true;
                            b.warning("Invalid block at container restocking position. Recovery attempt (" + b.invalidRestockRecoveryRetries + "/" + INVALID_RESTOCK_RECOVERY_MAX_RETRIES + ").");

                            breakContainer = true;
                            handleContainerBlock(b, blockPos);
                            b.setState(MineShulkerBlockade, this);
                        } else {
                            b.notifyDesktop(b.notifyRestockIssues, "THM Highway Builder", "Invalid block at restock position after recovery retry.");
                            b.error("Invalid block at container restocking position after recovery retry.");
                        }
                    }
                }
            }

            private boolean shouldMineEnderChestsForMaterials(HighwayBuilderTHM b) {
                return canMineEnderChestsForObsidian(b)
                    && countUsableLooseInventoryEnderChests(b) > 0;
            }

            private boolean shouldHardFailObsidianRawInventorySupply(HighwayBuilderTHM b) {
                return isObsidianRestockTask(b)
                    && !canMineEnderChestsForObsidian(b)
                    && !hasInventoryRestockShulkerOfKind(b, ECHEST_RESERVE_OBSIDIAN)
                    && hasAnyRawEnderChestCapableInventorySupply(b);
            }

            private boolean hasSelectedRestockSource() {
                return slot != -1 || usingPlacedEnderChestSource;
            }

            private boolean isEnderChestAccessAvailable(HighwayBuilderTHM b, boolean hasPlacedRestockEnderChest) {
                return b.searchEnderChest.get()
                    && (hasPlacedRestockEnderChest || countItem(b, stack -> stack.getItem().equals(Items.ENDER_CHEST)) > 0);
            }

            private void logNoSelectedSourceDecision(HighwayBuilderTHM b, String message) {
                if (!b.restockDebugLog.get()) return;
                boolean enderChestExhausted = b.restockTask.getSession() != null && b.restockTask.getSession().isEnderChestExhausted();
                b.restockDebug("%s (slot=%d, usingPlacedEnderChestSource=%s, sourceLabel=%s, enderChestExhausted=%s).",
                    message,
                    slot,
                    usingPlacedEnderChestSource,
                    sourceLabel,
                    enderChestExhausted
                );
            }

            private boolean runObsidianPickaxePreflight(HighwayBuilderTHM b) {
                if (!isObsidianRestockTask(b)) return false;
                if (b.restockTask.isObsidianPickaxePreflightSatisfied()) return false;
                if (b.restockTask.hasQueuedObsidianPickaxePreflight()) return false;

                RestockTask.RestockSession session = b.restockTask.getSession();
                if (session == null) return false;
                session.refreshProgress();

                int eChestsToBreak = session.getTargetEchestsToBreak();
                return b.prioritizePickaxeRestockBeforeEChestMining(eChestsToBreak, "obsidian e-chest mining preflight");
            }

            private boolean shouldTryKitbotBeforeEnderChest(HighwayBuilderTHM b) {
                return b.restockSecondarySourceOrder.get() == RestockSecondarySourceOrder.KitBotThenEnderChest;
            }

            private boolean tryStartKitbotFallback(HighwayBuilderTHM b, String reason) {
                if (!b.canUseKitbotForActiveRestock()) return false;
                if (!b.restockTask.shouldAttemptKitbot()) return false;

                logNoSelectedSourceDecision(b, reason);
                return b.enterKitbotRestockWithAvailabilityGate(reason, true);
            }

            private boolean routeMissingPickaxeBeforeEChestPath(HighwayBuilderTHM b, String pathLabel) {
                if (b.hasEligibleEChestMiningPickaxe()) return false;

                if (!b.restockTask.pickaxes) {
                    if (b.hasPickaxeRestockSourceAvailable()
                        && b.restockTask.prioritizePickaxesBeforeActiveTask("missing eligible pickaxe before " + pathLabel)) {
                        b.setState(Restock, Restock);
                        return true;
                    }

                    b.restockTask.failActiveTaskHard("Can't start " + pathLabel + ": no usable pickaxe and no pickaxe restock source enabled. Enable one or bring a spare pickaxe.");
                    return true;
                }

                if (b.canUseKitbotForActiveRestock() && b.restockTask.shouldAttemptKitbot()) {
                    b.enterKitbotRestockWithAvailabilityGate(
                        String.format("Restock.start using KitBot pickaxe restock before %s because no eligible non-silk pickaxe is available.", pathLabel),
                        true
                    );
                    return true;
                }

                b.restockTask.failActiveTaskHard("Can't start " + pathLabel + ": out of usable pickaxes and no KitBot fallback left. Bring more pickaxes or check KitBot pickaxe restock.");
                return true;
            }

            private boolean grabFromInventoryAndRegisterSuccessfulLocalAction(HighwayBuilderTHM b, Inventory inv, Predicate<ItemStack> filterItem, RestockTask.SourcePhase sourcePhase) {
                if (!grabFromInventory(b, inv, filterItem)) return false;
                b.restockTask.refreshSessionProgress();
                b.restockTask.noteSuccessfulLocalSourceAction(sourcePhase);
                return true;
            }

            private boolean grabFromInventoryAndTrackProgress(HighwayBuilderTHM b, Inventory inv, Predicate<ItemStack> filterItem, RestockTask.SourcePhase sourcePhase) {
                RestockTask.RestockSession session = b.restockTask.getSession();
                int beforeProgress = session != null ? session.getProgressTowardsTarget() : 0;
                int beforeUsablePulledEchests = session != null ? session.getUsablePulledEchests() : 0;
                if (!grabFromInventory(b, inv, filterItem)) return false;
                if (b.restockTask.food && sourcePhase == RestockTask.SourcePhase.EnderChest && b.mc.currentScreen instanceof GenericContainerScreen) {
                    b.invalidateEChestMemorySnapshot("direct-food-pull");
                }
                b.restockTask.noteSourceForwardProgress(sourcePhase, beforeProgress, beforeUsablePulledEchests);
                return true;
            }

            private boolean handleEnderChestRestockStep(HighwayBuilderTHM b, Inventory inv) {
                if (b.restockTask.materials && b.restockTask.isObsidianRestockSession()) {
                    if (grabFromInventoryAndRegisterSuccessfulLocalAction(b, inv, itemStack -> itemStack.getItem() == Items.OBSIDIAN, RestockTask.SourcePhase.EnderChest)) {
                        delayTimer = b.inventoryDelay.get();
                        return true;
                    }

                    if (b.searchShulkers.get()) {
                        boolean extractedObsidian = tryExtractRestockShulkerFromEnderChest(
                            b,
                            inv,
                            ECHEST_RESERVE_OBSIDIAN,
                            b.restockTask.getRemainingObsidianItemsForSession()
                        );
                        if (extractedObsidian) return true;
                        if (hasReservedEnderChestUnits(ECHEST_RESERVE_OBSIDIAN)) {
                            return finishEnderChestAfterReservedExtraction(b, "obsidian");
                        }
                    }

                    if (needsMoreRawEchests(b)) {
                        b.restockTask.shrinkObsidianTargetToCurrentRawEChestBatch("ender-chest-raw-stack");
                        if (grabFromInventoryAndRegisterSuccessfulLocalAction(b, inv, this::isRawEChestStack, RestockTask.SourcePhase.EnderChest)) {
                            delayTimer = b.inventoryDelay.get();
                            return true;
                        }
                    }

                    if (needsMoreRawEchests(b)
                        && b.searchShulkers.get()
                    ) {
                        b.restockTask.shrinkObsidianTargetToCurrentRawEChestBatch("ender-chest-raw-shulker");
                        boolean extractedRawEchest = tryExtractRestockShulkerFromEnderChest(
                            b,
                            inv,
                            ECHEST_RESERVE_RAW_ENDER_CHEST,
                            b.restockTask.getRemainingRawEchestsNeeded()
                        );
                        if (extractedRawEchest) return true;
                        if (hasReservedEnderChestUnits(ECHEST_RESERVE_RAW_ENDER_CHEST)) {
                            return finishEnderChestAfterReservedExtraction(b, "ender_chest");
                        }
                    }
                    return false;
                }

                if (b.restockTask.materials) {
                    if (grabFromInventoryAndRegisterSuccessfulLocalAction(b, inv, itemStack -> itemStack.getItem() instanceof BlockItem bi && b.blocksToPlace.get().contains(bi.getBlock()), RestockTask.SourcePhase.EnderChest)) {
                        delayTimer = b.inventoryDelay.get();
                        return true;
                    }
                }
                if (b.restockTask.pickaxes) {
                    if (grabFromInventoryAndRegisterSuccessfulLocalAction(b, inv, b::isActivePickaxeRestockMatch, RestockTask.SourcePhase.EnderChest)) {
                        delayTimer = b.inventoryDelay.get();
                        return true;
                    }
                }
                if (b.restockTask.food) {
                    if (grabFromInventoryAndTrackProgress(b, inv, b::isConfiguredFoodStack, RestockTask.SourcePhase.EnderChest)) {
                        delayTimer = b.inventoryDelay.get();
                        return true;
                    }
                }
                if (b.restockTask.enderChests) {
                    if (grabFromInventoryAndTrackProgress(b, inv, itemStack -> itemStack.getItem() == Items.ENDER_CHEST, RestockTask.SourcePhase.EnderChest)) {
                        delayTimer = b.inventoryDelay.get();
                        return true;
                    }
                }

                if (!b.searchShulkers.get()) return false;
                boolean extracted = tryExtractRestockShulkerFromEnderChest(b, inv, ECHEST_RESERVE_MATCHING, getDesiredShulkerUnitsForSelection(b));
                if (extracted) return true;
                if (hasReservedEnderChestUnits(ECHEST_RESERVE_MATCHING)) {
                    return finishEnderChestAfterReservedExtraction(b, "matching");
                }
                if (b.restockDebugLog.get()) {
                    b.restockDebug("Restock.tick found no usable ender chest extraction for task=%s (rawMatches=%d, matchingShulkers=%d, totalShulkers=%d, emptyInventorySlots=%d).",
                        b.restockTask.item(),
                        countRestockRawMatchesInInventory(inv, b),
                        countMatchingRestockShulkersInInventory(inv),
                        countAllShulkersInInventory(inv),
                        countEmptyPlayerInventorySlots(b)
                    );
                }
                return extracted;
            }

            private boolean finishEnderChestAfterReservedExtraction(HighwayBuilderTHM b, String label) {
                if (b.restockDebugLog.get()) {
                    b.restockDebug("Restock.tick closing the ender chest after reserved %s shulker extraction satisfied the current branch need (reservedUnits=%d).",
                        label,
                        reservedUnitsThisEnderChestPass
                    );
                }
                resetEnderChestExtractionReserve(b, "ender-chest-pass-complete");
                b.closeHandledScreen();
                breakContainer = true;
                return true;
            }

            private boolean restockItems(HighwayBuilderTHM b, Inventory inv) {
                if (b.restockTask.materials) {
                    // take raw material
                    if (grabFromInventoryAndRegisterSuccessfulLocalAction(b, inv, itemStack -> itemStack.getItem() instanceof BlockItem bi && b.blocksToPlace.get().contains(bi.getBlock()), RestockTask.SourcePhase.InventoryShulkers)) return true;

                    // prefer taking raw material before echests
                    if (b.blocksToPlace.get().contains(Blocks.OBSIDIAN) && needsMoreRawEchests(b)) {
                        b.restockTask.shrinkObsidianTargetToCurrentRawEChestBatch("inventory-raw-echest-stack");
                        if (grabFromInventoryAndRegisterSuccessfulLocalAction(b, inv, this::isRawEChestStack, RestockTask.SourcePhase.InventoryShulkers)) return true;
                    }
                }
                if (b.restockTask.pickaxes) {
                    if (grabFromInventoryAndRegisterSuccessfulLocalAction(b, inv, b::isActivePickaxeRestockMatch, RestockTask.SourcePhase.InventoryShulkers)) return true;
                }
                if (b.restockTask.food) {
                    return grabFromInventoryAndTrackProgress(b, inv, b::isConfiguredFoodStack, RestockTask.SourcePhase.InventoryShulkers);
                }
                if (b.restockTask.enderChests) {
                    return grabFromInventoryAndTrackProgress(b, inv, itemStack -> itemStack.getItem() == Items.ENDER_CHEST, RestockTask.SourcePhase.InventoryShulkers);
                }

                return false;
            }

            // scans the inventory, takes out the first item that matches the predicate and returns
            private boolean grabFromInventory(HighwayBuilderTHM b, Inventory inv, Predicate<ItemStack> filterItem) {
                if (b.restockTask.food) {
                    int preferredFoodSlot = b.findPreferredConfiguredFoodSlot(inv);
                    if (preferredFoodSlot != -1 && shiftClickInventorySlot(b, inv, preferredFoodSlot)) return true;
                    return false;
                }
                if (b.restockTask.enderChests) {
                    int preferredEnderChestSlot = b.findPreferredLooseEnderChestSlot(inv);
                    if (preferredEnderChestSlot != -1 && shiftClickInventorySlot(b, inv, preferredEnderChestSlot)) return true;
                    return false;
                }

                for (int i = 0; i < inv.size(); i++) {
                    if (filterItem.test(inv.getStack(i)) && shiftClickInventorySlot(b, inv, i)) return true;
                }

                return false;
            }

            private boolean shiftClickInventorySlot(HighwayBuilderTHM b, Inventory inv, int slotId) {
                if (slotId < 0 || slotId >= inv.size()) return false;

                ItemStack before = inv.getStack(slotId).copy();
                InvUtils.shiftClick().slotId(slotId);
                ItemStack after = inv.getStack(slotId);

                if (clearCursor(b)) return true;

                return after.getCount() < before.getCount() || after.getItem() != before.getItem();
            }

            private boolean clearCursor(HighwayBuilderTHM b) {
                if (!b.mc.player.currentScreenHandler.getCursorStack().isEmpty()) {
                    if (tryPlaceCursorInEmptySlot(b)) {
                        delayTimer = b.inventoryDelay.get();
                        return true;
                    }

                    if (b.mc.currentScreen != null) b.closeHandledScreen();
                    if (!b.mc.player.currentScreenHandler.getCursorStack().isEmpty()) {
                        dropCursorBypassAntiDrop(b);
                    }
                    delayTimer = b.inventoryDelay.get();
                    return true;
                }

                return false;
            }

            private boolean tryPlaceCursorInEmptySlot(HighwayBuilderTHM b) {
                if (b.mc.player == null) return false;
                if (b.mc.player.currentScreenHandler == null) return false;

                for (int i = 0; i < b.mc.player.currentScreenHandler.slots.size(); i++) {
                    Slot slot = b.mc.player.currentScreenHandler.slots.get(i);
                    if (slot.inventory == b.mc.player.getInventory() && slot.getStack().isEmpty()) {
                        b.mc.interactionManager.clickSlot(b.mc.player.currentScreenHandler.syncId, i, 0, SlotActionType.PICKUP, b.mc.player);
                        if (b.mc.player.currentScreenHandler.getCursorStack().isEmpty()) return true;
                    }
                }

                return false;
            }

            private void dropCursorBypassAntiDrop(HighwayBuilderTHM b) {
                if (!b.protectUsefulCursorStackFromDrop("Restock-dropCursorBypassAntiDrop", true)) {
                    b.dropCursorHandBypassingAntiDrop();
                }
            }

            private void setShulkerPredicate(HighwayBuilderTHM b) {
                shulkerPredicate = itemStack -> {
                    return isMatchingRestockShulkerContent(b, itemStack);
                };
            }

            private boolean isPickaxeRestockMatch(HighwayBuilderTHM b, ItemStack stack) {
                return b.isActivePickaxeRestockMatch(stack);
            }

            private int findAndMoveBestRestockShulkerToHotbar(HighwayBuilderTHM b) {
                Inventory inv = b.mc.player.getInventory();
                String selectionKind = getPreferredInventoryRestockShulkerKind(b, inv);
                if (selectionKind == null) return -1;

                int inventorySlot = findBestRestockShulkerSlotInInventory(b, inv, selectionKind, true);
                if (inventorySlot == -1) return -1;

                int usefulUnits = countRestockUnitsInShulker(b, inv.getStack(inventorySlot), selectionKind);
                int hotbarSlot = moveInventorySlotToHotbar(b, inventorySlot, true);
                if (hotbarSlot == -1) {
                    if (b.restockDebugLog.get()) {
                        b.restockDebug("Restock.start found matching %s shulker in inventory slot %d but could not move it into the hotbar.",
                            describeRestockShulkerKind(selectionKind),
                            inventorySlot
                        );
                    }
                    return SOURCE_SELECTION_MOVE_FAILED;
                }

                if (b.restockDebugLog.get()) {
                    b.restockDebug("Restock.start selected inventory %s shulker slot %d -> hotbar %d for task=%s (usefulUnits=%d).",
                        describeRestockShulkerKind(selectionKind),
                        inventorySlot,
                        hotbarSlot,
                        b.restockTask.item(),
                        usefulUnits
                    );
                }

                return hotbarSlot;
            }

            private int moveInventorySlotToHotbar(HighwayBuilderTHM b, int inventorySlot, boolean failHardNoHotbar) {
                if (inventorySlot < 0 || inventorySlot >= b.mc.player.getInventory().getMainStacks().size()) return -1;
                if (inventorySlot < 9) return inventorySlot;

                ItemStack inventoryStack = b.mc.player.getInventory().getStack(inventorySlot);
                int hotbarSlot = b.getPreferredManagedHotbarSlot(inventoryStack.getItem());
                if (hotbarSlot == -1) {
                    hotbarSlot = findHotbarSlot(b, false, failHardNoHotbar);
                }
                if (hotbarSlot == -1) return -1;

                InvUtils.move().from(inventorySlot).toHotbar(hotbarSlot);
                if (!b.clearCursorStackToEmptySlot("Restock-moveInventorySlotToHotbar")
                    && !b.dropCursorStackIfSafe("Restock-moveInventorySlotToHotbar")) {
                }

                return hotbarSlot;
            }

            private boolean tryExtractRestockShulkerFromEnderChest(HighwayBuilderTHM b, Inventory inv, String kind, int remainingNeed) {
                if (remainingNeed <= 0) return false;

                if (!reservedUnitsKindThisEnderChestPass.equals(ECHEST_RESERVE_NONE)
                    && !reservedUnitsKindThisEnderChestPass.equals(kind)
                    && reservedUnitsThisEnderChestPass > 0) {
                    return false;
                }

                if (isEnderChestReserveSatisfied(kind, remainingNeed)) {
                    return finishEnderChestAfterReservedExtraction(b, kind);
                }

                int shulkerSlot = findBestRestockShulkerSlotInInventory(b, inv, kind, false);
                if (shulkerSlot == -1) {
                    if (b.restockDebugLog.get()) {
                        b.restockDebug("Restock.tick did not find any %s shulker inside the opened ender chest for remainingNeed=%d reservedUnits=%d.",
                            describeRestockShulkerKind(kind),
                            remainingNeed,
                            kind.equals(reservedUnitsKindThisEnderChestPass) ? reservedUnitsThisEnderChestPass : 0
                        );
                    }
                    return false;
                }

                int usefulUnits = countRestockUnitsInShulker(b, inv.getStack(shulkerSlot), kind);
                int moveTo = InvUtils.findEmpty().slot();
                if (moveTo == -1) {
                    if (b.restockTask.getSession() != null) b.restockTask.getSession().fail();
                    b.notifyDesktop(b.notifyRestockIssues, "THM Highway Builder", "No empty slots available to extract a " + describeRestockShulkerKind(kind) + " shulker from the ender chest.");
                    b.error("No empty slots available to extract a " + describeRestockShulkerKind(kind) + " shulker from the ender chest.");
                    return true;
                }

                if (!shiftClickInventorySlot(b, inv, shulkerSlot)) return false;

                b.restockTask.reopenSourcePhase(RestockTask.SourcePhase.InventoryShulkers);
                b.restockTask.noteUsefulEnderChestShulkerExtraction();
                reserveEnderChestUnits(b, kind, usefulUnits);
                b.restockDebug("Restock.tick reopened InventoryShulkers after extracting a %s shulker from the ender chest into slot %d (usefulUnits=%d, remainingNeed=%d, reservedUnits=%d, sourceSlot=%d).",
                    describeRestockShulkerKind(kind),
                    moveTo,
                    usefulUnits,
                    remainingNeed,
                    reservedUnitsThisEnderChestPass,
                    shulkerSlot
                );
                if (b.restockTask.food) trackExtractedFoodShulkerFromEnderChest(b, moveTo);
                if (b.restockTask.enderChests) trackExtractedEnderChestShulkerFromEnderChest(b, moveTo);
                delayTimer = b.inventoryDelay.get();

                if (isEnderChestReserveSatisfied(kind, remainingNeed)) {
                    return finishEnderChestAfterReservedExtraction(b, kind);
                }
                return true;
            }

            private boolean isMatchingRestockShulkerContent(HighwayBuilderTHM b, ItemStack itemStack) {
                if (!Utils.isShulker(itemStack.getItem())) return false;
                if (b.restockTask.materials && b.restockTask.isObsidianRestockSession()) {
                    return countRestockUnitsInShulker(b, itemStack, ECHEST_RESERVE_OBSIDIAN) > 0
                        || (canUseRawEnderChestSupply(b)
                        && countRestockUnitsInShulker(b, itemStack, ECHEST_RESERVE_RAW_ENDER_CHEST) > 0);
                }
                return countRestockUnitsInShulker(b, itemStack, ECHEST_RESERVE_MATCHING) > 0;
            }

            private int getDesiredShulkerUnitsForSelection(HighwayBuilderTHM b) {
                int remainingTarget = b.restockTask.getRemainingTarget();
                if (remainingTarget > 0) return remainingTarget;
                return Math.max(minimumSlots, 1);
            }

            private String getPreferredInventoryRestockShulkerKind(HighwayBuilderTHM b, Inventory inv) {
                if (b.restockTask.materials && b.restockTask.isObsidianRestockSession()) {
                    int obsidianSlot = findBestRestockShulkerSlotInInventory(b, inv, ECHEST_RESERVE_OBSIDIAN, true);
                    if (obsidianSlot != -1) return ECHEST_RESERVE_OBSIDIAN;

                    b.restockTask.shrinkObsidianTargetToCurrentRawEChestBatch("inventory-raw-echest-shulker");
                    if (canUseRawEnderChestSupply(b)) {
                        int rawEchestSlot = findBestRestockShulkerSlotInInventory(b, inv, ECHEST_RESERVE_RAW_ENDER_CHEST, true);
                        if (rawEchestSlot != -1) return ECHEST_RESERVE_RAW_ENDER_CHEST;
                    }
                    return null;
                }

                return findBestRestockShulkerSlotInInventory(b, inv, ECHEST_RESERVE_MATCHING, true) != -1
                    ? ECHEST_RESERVE_MATCHING
                    : null;
            }

            private int findBestRestockShulkerSlotInInventory(HighwayBuilderTHM b, Inventory inv, String kind, boolean preferSmallest) {
                int bestSlot = -1;
                int bestUnits = -1;

                for (int i = 0; i < inv.size(); i++) {
                    ItemStack candidate = inv.getStack(i);
                    int usefulUnits = countRestockUnitsInShulker(b, candidate, kind);
                    if (usefulUnits <= 0) continue;

                    if (bestSlot == -1
                        || (preferSmallest && usefulUnits < bestUnits)
                        || (!preferSmallest && usefulUnits > bestUnits)
                        || (usefulUnits == bestUnits && i < bestSlot)) {
                        bestSlot = i;
                        bestUnits = usefulUnits;
                    }
                }

                if (bestSlot != -1 && b.restockDebugLog.get()) {
                    b.restockDebug("Restock.tick selected %s shulker slot %d for task=%s (usefulUnits=%d, policy=%s).",
                        describeRestockShulkerKind(kind),
                        bestSlot,
                        b.restockTask.item(),
                        bestUnits,
                        preferSmallest ? "smallest-useful-first" : "largest-useful-first"
                    );
                }

                return bestSlot;
            }

            private int countRestockUnitsInShulker(HighwayBuilderTHM b, ItemStack itemStack, String kind) {
                if (!Utils.isShulker(itemStack.getItem())) return 0;

                int usefulUnits = 0;
                for (ItemStack stack : THMUtils.getContainerContents(itemStack)) {
                    switch (kind) {
                        case ECHEST_RESERVE_MATCHING -> {
                            if (b.restockTask.materials && stack.getItem() instanceof BlockItem bi && !b.restockTask.isObsidianRestockSession()) {
                                if (b.blocksToPlace.get().contains(bi.getBlock())) usefulUnits++;
                            }
                            else if (b.restockTask.pickaxes && isPickaxeRestockMatch(b, stack)) {
                                usefulUnits += stack.getCount();
                            }
                            else if (b.restockTask.food && b.isConfiguredFoodStack(stack)) {
                                usefulUnits += stack.getCount();
                            }
                            else if (b.restockTask.enderChests && stack.getItem() == Items.ENDER_CHEST) {
                                usefulUnits += stack.getCount();
                            }
                        }
                        case ECHEST_RESERVE_OBSIDIAN -> {
                            if (stack.getItem() == Items.OBSIDIAN) usefulUnits += stack.getCount();
                        }
                        case ECHEST_RESERVE_RAW_ENDER_CHEST -> {
                            if (isRawEChestStack(stack)) usefulUnits += stack.getCount();
                        }
                    }
                }

                return usefulUnits;
            }

            private boolean isRawEChestStack(ItemStack stack) {
                return stack != null
                    && !stack.isEmpty()
                    && stack.getItem() == Items.ENDER_CHEST
                    && stack.getCount() > 0;
            }

            private int countRestockShulkersInInventoryByKind(HighwayBuilderTHM b, Inventory inv, String kind) {
                int count = 0;

                for (int i = 0; i < inv.size(); i++) {
                    if (countRestockUnitsInShulker(b, inv.getStack(i), kind) > 0) count++;
                }

                return count;
            }

            private boolean isObsidianRestockTask(HighwayBuilderTHM b) {
                return b.restockTask.materials && b.restockTask.isObsidianRestockSession();
            }

            private boolean canMineEnderChestsForObsidian(HighwayBuilderTHM b) {
                return isObsidianRestockTask(b)
                    && b.mineEnderChests.get()
                    && (b.restockTask.getSession() == null || !b.restockTask.getSession().isMineEnderChestsExhausted());
            }

            private int countUsableLooseInventoryEnderChests(HighwayBuilderTHM b) {
                int savedEchestReserve = b.restockTask.getSession() != null
                    ? b.restockTask.getSession().saveEchestsReserve
                    : b.saveEchests.get();
                return Math.max(countItem(b, stack -> stack.getItem().equals(Items.ENDER_CHEST)) - savedEchestReserve, 0);
            }

            private boolean hasInventoryRestockShulkerOfKind(HighwayBuilderTHM b, String kind) {
                if (b.mc.player == null || !b.searchShulkers.get()) return false;
                return countRestockShulkersInInventoryByKind(b, b.mc.player.getInventory(), kind) > 0;
            }

            private boolean hasAnyRawEnderChestCapableInventorySupply(HighwayBuilderTHM b) {
                return countUsableLooseInventoryEnderChests(b) > 0
                    || hasInventoryRestockShulkerOfKind(b, ECHEST_RESERVE_RAW_ENDER_CHEST);
            }

            private boolean canUseRawEnderChestSupply(HighwayBuilderTHM b) {
                return canMineEnderChestsForObsidian(b)
                    && b.restockTask.getRemainingRawEchestsNeeded() > 0;
            }

            private boolean hasReservedEnderChestUnits(String kind) {
                return reservedUnitsThisEnderChestPass > 0 && kind.equals(reservedUnitsKindThisEnderChestPass);
            }

            private boolean isEnderChestReserveSatisfied(String kind, int remainingNeed) {
                return remainingNeed > 0 && hasReservedEnderChestUnits(kind) && reservedUnitsThisEnderChestPass >= remainingNeed;
            }

            private void reserveEnderChestUnits(HighwayBuilderTHM b, String kind, int usefulUnits) {
                if (usefulUnits <= 0) return;
                if (!kind.equals(reservedUnitsKindThisEnderChestPass)) {
                    resetEnderChestExtractionReserve(b, "kind-switch-" + kind);
                    reservedUnitsKindThisEnderChestPass = kind;
                }
                reservedUnitsThisEnderChestPass += usefulUnits;
                if (b.restockDebugLog.get()) {
                    b.restockDebug("Restock.tick reserved %d %s units from the current ender chest pass (totalReserved=%d).",
                        usefulUnits,
                        describeRestockShulkerKind(kind),
                        reservedUnitsThisEnderChestPass
                    );
                }
            }

            private void resetEnderChestExtractionReserve(HighwayBuilderTHM b, String reason) {
                if (reservedUnitsThisEnderChestPass > 0 && b.restockDebugLog.get()) {
                    b.restockDebug("Restock.tick reset ender chest reserve (kind=%s, units=%d, reason=%s).",
                        reservedUnitsKindThisEnderChestPass,
                        reservedUnitsThisEnderChestPass,
                        reason
                    );
                }
                reservedUnitsThisEnderChestPass = 0;
                reservedUnitsKindThisEnderChestPass = ECHEST_RESERVE_NONE;
            }

            private String describeRestockShulkerKind(String kind) {
                return switch (kind) {
                    case ECHEST_RESERVE_OBSIDIAN -> "obsidian";
                    case ECHEST_RESERVE_RAW_ENDER_CHEST -> "ender_chest";
                    case ECHEST_RESERVE_MATCHING -> "matching";
                    default -> kind;
                };
            }

            private int countMatchingRestockShulkersInPlayerInventory(HighwayBuilderTHM b) {
                return countMatchingRestockShulkersInInventory(b.mc.player.getInventory());
            }

            private int countMatchingRestockShulkersInInventory(Inventory inv) {
                int count = 0;
                for (int i = 0; i < inv.size(); i++) {
                    if (shulkerPredicate.test(inv.getStack(i))) count++;
                }
                return count;
            }

            private int countAllShulkersInInventory(Inventory inv) {
                int count = 0;
                for (int i = 0; i < inv.size(); i++) {
                    if (Utils.isShulker(inv.getStack(i).getItem())) count++;
                }
                return count;
            }

            private int countRestockRawMatchesInInventory(Inventory inv, HighwayBuilderTHM b) {
                int count = 0;
                for (int i = 0; i < inv.size(); i++) {
                    if (isRestockRawMatch(b, inv.getStack(i))) count++;
                }
                return count;
            }

            private int countRestockRawMatchesInContainer(Iterable<ItemStack> stacks, HighwayBuilderTHM b) {
                int count = 0;
                for (ItemStack stack : stacks) {
                    if (isRestockRawMatch(b, stack)) count++;
                }
                return count;
            }

            private int countMatchingRestockShulkersInContainerStacks(Iterable<ItemStack> stacks, HighwayBuilderTHM b) {
                int count = 0;
                for (ItemStack stack : stacks) {
                    if (isMatchingRestockShulkerContent(b, stack)) count++;
                }
                return count;
            }

            private boolean isRestockRawMatch(HighwayBuilderTHM b, ItemStack stack) {
                if (stack == null || stack.isEmpty()) return false;
                if (b.restockTask.materials && stack.getItem() instanceof BlockItem bi) {
                    return b.blocksToPlace.get().contains(bi.getBlock())
                        || (b.blocksToPlace.get().contains(Blocks.OBSIDIAN) && isRawEChestStack(stack) && needsMoreRawEchests(b));
                }
                if (b.restockTask.pickaxes && isPickaxeRestockMatch(b, stack)) return true;
                if (b.restockTask.enderChests && stack.getItem() == Items.ENDER_CHEST) return true;
                return b.restockTask.food && b.isConfiguredFoodStack(stack);
            }

            private int countEmptyPlayerInventorySlots(HighwayBuilderTHM b) {
                int count = 0;
                for (int i = 0; i < b.mc.player.getInventory().getMainStacks().size(); i++) {
                    if (b.mc.player.getInventory().getStack(i).isEmpty()) count++;
                }
                return count;
            }

            private void trackExtractedFoodShulkerFromEnderChest(HighwayBuilderTHM b, int inventorySlot) {
                ItemStack movedStack = inventorySlot >= 0 && inventorySlot < b.mc.player.getInventory().getMainStacks().size()
                    ? b.mc.player.getInventory().getStack(inventorySlot)
                    : ItemStack.EMPTY;
                if (movedStack.isEmpty() || !Utils.isShulker(movedStack.getItem())) return;

                extractedFoodShulkerFromEnderChest = true;
                foodShulkerReturnPending = false;
                foodShulkerReturnRetryUsed = false;
                foodShulkerReturnFinalizeAfterBreak = false;
                foodShulkerReturnWaitTicks = 0;
                foodShulkerReturnInventorySlot = inventorySlot;
                b.restockDebug("Tracked extracted food shulker in inventory slot %d for eventual ender chest return.", inventorySlot);
            }

            private void trackExtractedEnderChestShulkerFromEnderChest(HighwayBuilderTHM b, int inventorySlot) {
                ItemStack movedStack = inventorySlot >= 0 && inventorySlot < b.mc.player.getInventory().getMainStacks().size()
                    ? b.mc.player.getInventory().getStack(inventorySlot)
                    : ItemStack.EMPTY;
                if (movedStack.isEmpty() || !Utils.isShulker(movedStack.getItem())) return;

                extractedEnderChestShulkerFromEnderChest = true;
                enderChestShulkerReturnPending = false;
                enderChestShulkerReturnRetryUsed = false;
                enderChestShulkerReturnFinalizeAfterBreak = false;
                enderChestShulkerReturnWaitTicks = 0;
                enderChestShulkerReturnInventorySlot = inventorySlot;
                b.restockDebug("Tracked extracted ender chest shulker in inventory slot %d for eventual ender chest return.", inventorySlot);
            }

            private void prepareFoodShulkerReturn(HighwayBuilderTHM b, Inventory inv) {
                if (!extractedFoodShulkerFromEnderChest || foodShulkerReturnPending) return;

                if (b.isContainerInventoryEmpty(inv)) {
                    b.restockDebug("Extracted food shulker became empty after pull; keeping it in inventory for later cleanup.");
                    clearFoodReturnTracking();
                    return;
                }

                int trackedSlot = resolveFoodReturnInventorySlot(b);
                if (trackedSlot != -1) {
                    foodShulkerReturnInventorySlot = trackedSlot;
                } else {
                    b.restockDebug("Extracted food shulker is still placed in-world; deferring inventory slot resolution until after break.");
                }

                foodShulkerReturnPending = true;
                foodShulkerReturnRetryUsed = false;
                foodShulkerReturnFinalizeAfterBreak = false;
                foodShulkerReturnWaitTicks = 40;
                b.restockDebug("Queued extracted food shulker return to ender chest from slot %d.", foodShulkerReturnInventorySlot);
            }

            private boolean handleFoodShulkerReturn(HighwayBuilderTHM b, BlockPos blockPos) {
                BlockState blockState = b.mc.world.getBlockState(blockPos);

                if (breakContainer) {
                    if (!(blockState.getBlock() instanceof AirBlock)) {
                        handleContainerBlock(b, blockPos);
                        return true;
                    }

                    breakContainer = false;
                    if (foodShulkerReturnFinalizeAfterBreak) {
                        clearFoodReturnTracking();
                        b.completeRestockTaskAndContinue();
                        return true;
                    }
                }

                if (foodShulkerReturnWaitTicks > 0) {
                    foodShulkerReturnWaitTicks--;
                    return true;
                }

                if (blockState.getBlock() instanceof AirBlock) {
                    int echestSlot = findAndMoveToHotbar(b, itemStack -> itemStack.getItem() == Items.ENDER_CHEST, false);
                    if (echestSlot == -1) {
                        b.warning("Unable to find an ender chest to return the extracted food shulker. Keeping it in inventory.");
                        clearFoodReturnTracking();
                        b.completeRestockTaskAndContinue();
                        return true;
                    }

                    if (!BlockUtils.place(blockPos, Hand.MAIN_HAND, echestSlot, b.rotation.get().place, 0, true, true, true)
                        && b.restockDebugLog.get()) {
                        b.restockDebug("Food shulker return did not place an ender chest at %s this tick; waiting for the next world probe.",
                            b.formatBlockPos(blockPos)
                        );
                    }
                    delayTimer = b.inventoryDelay.get();
                    return true;
                }

                if (blockState.getBlock() != Blocks.ENDER_CHEST) {
                    handleContainerBlock(b, blockPos);
                    return true;
                }

                if (!(b.mc.currentScreen instanceof GenericContainerScreen screen)) {
                    handleContainerBlock(b, blockPos);
                    return true;
                }

                if (screen.getScreenHandler().syncId != b.syncId) return true;

                int trackedSlot = resolveFoodReturnInventorySlot(b);
                boolean moved = false;

                if (trackedSlot != -1) {
                    ItemStack trackedShulker = b.mc.player.getInventory().getStack(trackedSlot);
                    if (!b.isContainerItemEmpty(trackedShulker)) {
                        ItemStack before = trackedShulker.copy();
                        b.mc.interactionManager.clickSlot(
                            b.mc.player.currentScreenHandler.syncId,
                            SlotUtils.indexToId(trackedSlot),
                            0,
                            SlotActionType.QUICK_MOVE,
                            b.mc.player
                        );
                        ItemStack after = b.mc.player.getInventory().getStack(trackedSlot);
                        moved = after.isEmpty() || after.getCount() < before.getCount() || !ItemStack.areItemsAndComponentsEqual(before, after);
                    }
                }

                if (!moved && !foodShulkerReturnRetryUsed) {
                    foodShulkerReturnRetryUsed = true;
                    foodShulkerReturnWaitTicks = 40;
                    foodShulkerReturnFinalizeAfterBreak = false;
                    b.warning("Retrying extracted food shulker return after the first attempt failed.");
                } else {
                    if (!moved) {
                        b.warning("Keeping extracted food shulker in inventory after return retry failed.");
                    }
                    foodShulkerReturnFinalizeAfterBreak = true;
                }

                b.closeHandledScreen();
                breakContainer = true;
                delayTimer = b.inventoryDelay.get();
                return true;
            }

            private int resolveFoodReturnInventorySlot(HighwayBuilderTHM b) {
                if (foodShulkerReturnInventorySlot >= 0 && foodShulkerReturnInventorySlot < b.mc.player.getInventory().getMainStacks().size()) {
                    ItemStack trackedStack = b.mc.player.getInventory().getStack(foodShulkerReturnInventorySlot);
                    if (isReturnableFoodShulker(b, trackedStack)) return foodShulkerReturnInventorySlot;
                }

                int fallbackSlot = findReturnableFoodShulkerSlot(b);
                if (fallbackSlot != -1) {
                    foodShulkerReturnInventorySlot = fallbackSlot;
                    if (b.restockDebugLog.get()) {
                        b.restockDebug("Resolved extracted food shulker to fallback inventory slot %d after pickup.", fallbackSlot);
                    }
                }
                return fallbackSlot;
            }

            private int findReturnableFoodShulkerSlot(HighwayBuilderTHM b) {
                for (int i = 0; i < b.mc.player.getInventory().getMainStacks().size(); i++) {
                    if (i == foodShulkerReturnInventorySlot) continue;
                    ItemStack stack = b.mc.player.getInventory().getStack(i);
                    if (isReturnableFoodShulker(b, stack)) return i;
                }
                return -1;
            }

            private boolean isReturnableFoodShulker(HighwayBuilderTHM b, ItemStack stack) {
                return !b.isContainerItemEmpty(stack)
                    && countRestockUnitsInShulker(b, stack, ECHEST_RESERVE_MATCHING) > 0;
            }

            private void prepareEnderChestShulkerReturn(HighwayBuilderTHM b, Inventory inv) {
                if (!extractedEnderChestShulkerFromEnderChest || enderChestShulkerReturnPending) return;

                if (b.isContainerInventoryEmpty(inv)) {
                    b.restockDebug("Extracted ender chest shulker became empty after pull; keeping it in inventory for later cleanup.");
                    clearEnderChestShulkerReturnTracking();
                    return;
                }

                int trackedSlot = resolveEnderChestReturnInventorySlot(b);
                if (trackedSlot != -1) {
                    enderChestShulkerReturnInventorySlot = trackedSlot;
                } else {
                    b.restockDebug("Extracted ender chest shulker is still placed in-world; deferring inventory slot resolution until after break.");
                }

                enderChestShulkerReturnPending = true;
                enderChestShulkerReturnRetryUsed = false;
                enderChestShulkerReturnFinalizeAfterBreak = false;
                enderChestShulkerReturnWaitTicks = 40;
                b.restockDebug("Queued extracted ender chest shulker return to ender chest from slot %d.", enderChestShulkerReturnInventorySlot);
            }

            private boolean handleEnderChestShulkerReturn(HighwayBuilderTHM b, BlockPos blockPos) {
                BlockState blockState = b.mc.world.getBlockState(blockPos);

                if (breakContainer) {
                    if (!(blockState.getBlock() instanceof AirBlock)) {
                        handleContainerBlock(b, blockPos);
                        return true;
                    }

                    breakContainer = false;
                    if (enderChestShulkerReturnFinalizeAfterBreak) {
                        clearEnderChestShulkerReturnTracking();
                        b.completeRestockTaskAndContinue();
                        return true;
                    }
                }

                if (enderChestShulkerReturnWaitTicks > 0) {
                    enderChestShulkerReturnWaitTicks--;
                    return true;
                }

                if (blockState.getBlock() instanceof AirBlock) {
                    int echestSlot = findAndMoveToHotbar(b, itemStack -> itemStack.getItem() == Items.ENDER_CHEST, false);
                    if (echestSlot == -1) {
                        b.warning("Unable to find an ender chest to return the extracted ender chest shulker. Keeping it in inventory.");
                        clearEnderChestShulkerReturnTracking();
                        b.completeRestockTaskAndContinue();
                        return true;
                    }

                    if (!BlockUtils.place(blockPos, Hand.MAIN_HAND, echestSlot, b.rotation.get().place, 0, true, true, true)
                        && b.restockDebugLog.get()) {
                        b.restockDebug("Ender chest shulker return did not place an ender chest at %s this tick; waiting for the next world probe.",
                            b.formatBlockPos(blockPos)
                        );
                    }
                    delayTimer = b.inventoryDelay.get();
                    return true;
                }

                if (blockState.getBlock() != Blocks.ENDER_CHEST) {
                    handleContainerBlock(b, blockPos);
                    return true;
                }

                if (!(b.mc.currentScreen instanceof GenericContainerScreen screen)) {
                    handleContainerBlock(b, blockPos);
                    return true;
                }

                if (screen.getScreenHandler().syncId != b.syncId) return true;

                int trackedSlot = resolveEnderChestReturnInventorySlot(b);
                boolean moved = false;

                if (trackedSlot != -1) {
                    ItemStack trackedShulker = b.mc.player.getInventory().getStack(trackedSlot);
                    if (!b.isContainerItemEmpty(trackedShulker)) {
                        ItemStack before = trackedShulker.copy();
                        b.mc.interactionManager.clickSlot(
                            b.mc.player.currentScreenHandler.syncId,
                            SlotUtils.indexToId(trackedSlot),
                            0,
                            SlotActionType.QUICK_MOVE,
                            b.mc.player
                        );
                        ItemStack after = b.mc.player.getInventory().getStack(trackedSlot);
                        moved = after.isEmpty() || after.getCount() < before.getCount() || !ItemStack.areItemsAndComponentsEqual(before, after);
                    }
                }

                if (!moved && !enderChestShulkerReturnRetryUsed) {
                    enderChestShulkerReturnRetryUsed = true;
                    enderChestShulkerReturnWaitTicks = 40;
                    enderChestShulkerReturnFinalizeAfterBreak = false;
                    b.warning("Retrying extracted ender chest shulker return after the first attempt failed.");
                } else {
                    if (!moved) {
                        b.warning("Keeping extracted ender chest shulker in inventory after return retry failed.");
                    }
                    enderChestShulkerReturnFinalizeAfterBreak = true;
                }

                b.closeHandledScreen();
                breakContainer = true;
                delayTimer = b.inventoryDelay.get();
                return true;
            }

            private int resolveEnderChestReturnInventorySlot(HighwayBuilderTHM b) {
                if (enderChestShulkerReturnInventorySlot >= 0 && enderChestShulkerReturnInventorySlot < b.mc.player.getInventory().getMainStacks().size()) {
                    ItemStack trackedStack = b.mc.player.getInventory().getStack(enderChestShulkerReturnInventorySlot);
                    if (isReturnableEnderChestShulker(b, trackedStack)) return enderChestShulkerReturnInventorySlot;
                }

                int fallbackSlot = findReturnableEnderChestShulkerSlot(b);
                if (fallbackSlot != -1) {
                    enderChestShulkerReturnInventorySlot = fallbackSlot;
                    if (b.restockDebugLog.get()) {
                        b.restockDebug("Resolved extracted ender chest shulker to fallback inventory slot %d after pickup.", fallbackSlot);
                    }
                }
                return fallbackSlot;
            }

            private int findReturnableEnderChestShulkerSlot(HighwayBuilderTHM b) {
                for (int i = 0; i < b.mc.player.getInventory().getMainStacks().size(); i++) {
                    if (i == enderChestShulkerReturnInventorySlot) continue;
                    ItemStack stack = b.mc.player.getInventory().getStack(i);
                    if (isReturnableEnderChestShulker(b, stack)) return i;
                }
                return -1;
            }

            private boolean isReturnableEnderChestShulker(HighwayBuilderTHM b, ItemStack stack) {
                return !b.isContainerItemEmpty(stack)
                    && countRestockUnitsInShulker(b, stack, ECHEST_RESERVE_MATCHING) > 0;
            }

            private boolean returnSmallestExtraEnderChestStackToContainer(HighwayBuilderTHM b) {
                int matchingEnderChestStacks = 0;
                int smallestSlot = -1;
                int smallestCount = Integer.MAX_VALUE;

                for (int i = 0; i < b.mc.player.getInventory().getMainStacks().size(); i++) {
                    ItemStack stack = b.mc.player.getInventory().getStack(i);
                    if (!stack.isOf(Items.ENDER_CHEST)) continue;

                    matchingEnderChestStacks++;
                    if (stack.getCount() < smallestCount
                        || (stack.getCount() == smallestCount && preferRestockReturnSlot(i, smallestSlot))) {
                        smallestSlot = i;
                        smallestCount = stack.getCount();
                    }
                }

                if (matchingEnderChestStacks <= 1 || smallestSlot == -1) return false;

                ItemStack before = b.mc.player.getInventory().getStack(smallestSlot).copy();
                b.mc.interactionManager.clickSlot(
                    b.mc.player.currentScreenHandler.syncId,
                    SlotUtils.indexToId(smallestSlot),
                    0,
                    SlotActionType.QUICK_MOVE,
                    b.mc.player
                );

                ItemStack after = b.mc.player.getInventory().getStack(smallestSlot);
                boolean moved = after.isEmpty()
                    || after.getCount() < before.getCount()
                    || !ItemStack.areItemsAndComponentsEqual(before, after);

                if (moved && b.restockDebugLog.get()) {
                    b.restockDebug("Returned smallest loose ender chest stack from inventory slot %d to the open ender chest shulker (count=%d, totalStacksBefore=%d).",
                        smallestSlot,
                        before.getCount(),
                        matchingEnderChestStacks
                    );
                }

                return moved;
            }

            private boolean preferRestockReturnSlot(int candidateSlot, int currentBestSlot) {
                if (currentBestSlot == -1) return true;
                boolean candidateHotbar = candidateSlot < 9;
                boolean currentBestHotbar = currentBestSlot < 9;
                if (candidateHotbar != currentBestHotbar) return !candidateHotbar;
                return candidateSlot > currentBestSlot;
            }

            private void clearFoodReturnTracking() {
                extractedFoodShulkerFromEnderChest = false;
                foodShulkerReturnPending = false;
                foodShulkerReturnRetryUsed = false;
                foodShulkerReturnFinalizeAfterBreak = false;
                foodShulkerReturnWaitTicks = 0;
                foodShulkerReturnInventorySlot = -1;
            }

            private void clearEnderChestShulkerReturnTracking() {
                extractedEnderChestShulkerFromEnderChest = false;
                enderChestShulkerReturnPending = false;
                enderChestShulkerReturnRetryUsed = false;
                enderChestShulkerReturnFinalizeAfterBreak = false;
                enderChestShulkerReturnWaitTicks = 0;
                enderChestShulkerReturnInventorySlot = -1;
            }

            private boolean needsMoreRawEchests(HighwayBuilderTHM b) {
                if (!b.blocksToPlace.get().contains(Blocks.OBSIDIAN)) return false;
                return canUseRawEnderChestSupply(b);
            }

            private boolean isSelectedRestockSourceReady(HighwayBuilderTHM b) {
                if (slot < 0 || slot >= 9) return false;
                if (sourceItemPredicate == null) return false;
                return sourceItemPredicate.test(b.mc.player.getInventory().getStack(slot));
            }

            private void handleContainerBlock(HighwayBuilderTHM b, BlockPos bp) {
                if (breakContainer) {
                    BlockState state = b.mc.world.getBlockState(bp);

                    int toolSlot = findAndMoveBestToolToHotbar(b, state, false);
                    if (toolSlot != b.mc.player.getInventory().getSelectedSlot()) InvUtils.swap(toolSlot, false);

                    if (b.rotation.get().mine) Rotations.rotate(Rotations.getYaw(bp), Rotations.getPitch(bp), () -> BlockUtils.breakBlock(bp, true));
                    else BlockUtils.breakBlock(bp, true);
                } else {
                    if (b.rotation.get().place) {
                        Rotations.rotate(Rotations.getYaw(bp), Rotations.getPitch(bp), () ->
                            b.mc.interactionManager.interactBlock(b.mc.player, Hand.MAIN_HAND, new BlockHitResult(Vec3d.ofCenter(bp), Direction.UP, bp, false))
                        );
                    }
                    else b.mc.interactionManager.interactBlock(b.mc.player, Hand.MAIN_HAND, new BlockHitResult(Vec3d.ofCenter(bp), Direction.UP, bp, false));

                    delayTimer = b.inventoryDelay.get();
                }
            }

        },

        PlaceShulkerBlockade {
            @Override
            protected void tick(HighwayBuilderTHM b) {
                if (!b.ensureCenteredBeforeBlockadePlacement(this)) return;

                int slot = findBlocksToPlacePrioritizeTrash(b);
                if (slot == -1) {
                    b.restockDebug("PlaceShulkerBlockade.tick could not find a block slot for blockade placement.");
                    return;
                }

                place(b, b.blockPosProvider.getBlockade(false, b.getEffectiveBlockadeType()), slot, Restock);
            }
        },

        MineShulkerBlockade {
            private boolean stopTimerEnabled;
            private boolean blockadeTeardownTouched;
            private int stopTimer;

            @Override
            protected void start(HighwayBuilderTHM b) {
                b.restockTask.setBlockadeReady(false);
                b.restockDebug("MineShulkerBlockade.start(blockadeType=%s, lastState=%s)", b.getEffectiveBlockadeType(), b.stateName(b.lastState));
                stopTimerEnabled = false;
                blockadeTeardownTouched = false;
                if (b.lastState == this) {
                    stopTimerEnabled = true;
                    stopTimer = 12;
                    b.restockDebug("MineShulkerBlockade.start entering stop timer cleanup.");
                }
            }

            @Override
            protected void tick(HighwayBuilderTHM b) {
                if (handlePendingTaskDuringTeardown(b)) return;

                if (!stopTimerEnabled) {
                    // mining the effective blockade type instead of BlockadeType.Shulker is the
                    // fastest fix to the module leaving
                    // some blocks behind if you start a pickaxe restock task while mining echests
                    int blockadeBlocksBefore = countCurrentBlockadeBlocks(b);
                    if (blockadeBlocksBefore > 0) blockadeTeardownTouched = true;
                    mine(b, b.blockPosProvider.getBlockade(true, b.getEffectiveBlockadeType()), true, this, this);
                }
                else {
                    stopTimer--;
                    if (stopTimer <= 0) {
                        if (b.invalidRestockRecoveryPending) {
                            b.invalidRestockRecoveryPending = false;
                            b.setState(Restock, Restock);
                        } else {
                            b.restockTask.finishSequence();
                            b.setState(Forward);
                        }
                    }
                }
            }

            private boolean handlePendingTaskDuringTeardown(HighwayBuilderTHM b) {
                if (!b.restockTask.tasksInactive() || !b.restockTask.hasPendingRestockTasks()) return false;

                int blockadeBlocks = countCurrentBlockadeBlocks(b);
                boolean canReuseBlockade = !blockadeTeardownTouched && blockadeBlocks > 0;
                b.restockTask.setBlockadeReady(canReuseBlockade);

                if (!b.restockTask.advanceToPendingTask()) return false;

                if (canReuseBlockade) {
                    b.restockDebug("MineShulkerBlockade advanced pending task before teardown touched the blockade (blocks=%d).", blockadeBlocks);
                    b.setState(Restock, Restock);
                } else {
                    b.restockDebug("MineShulkerBlockade advanced pending task after teardown touched or lost the blockade (blocks=%d); rebuilding from Center.", blockadeBlocks);
                    b.setState(Center, this);
                }
                return true;
            }

            private int countCurrentBlockadeBlocks(HighwayBuilderTHM b) {
                if (b.mc.world == null || b.blockPosProvider == null) return 0;

                int count = 0;
                MBPIterator it = b.blockPosProvider.getBlockade(true, b.getEffectiveBlockadeType());
                for (MBlockPos pos : it) {
                    if (b.isValidRestockStructureBlock(b.mc.world.getBlockState(pos.getBlockPos()))) count++;
                }
                return count;
            }
        },

        DefuseCrystalTraps {
            private int cooldown, shots;
            private EndCrystalEntity target;

            @Override
            protected void start(HighwayBuilderTHM b) {
                if (!InvUtils.find(Items.BOW).found() || (!InvUtils.find(itemStack -> itemStack.getItem() instanceof ArrowItem).found() && !b.mc.player.getAbilities().creativeMode)) {
                    b.destroyCrystalTraps.set(false);
                    b.warning("No bow found to destroy crystal traps with. Toggling the setting off.");
                    b.setState(Forward);
                }

                shots = cooldown = 0;
                target = null;
            }

            /**
             * Need to perform the linked injection to ensure that vanilla code does not interfere with us drawing our
             * //bow. The {@link //MinecraftClient#handleInputEvents} method is only called when you are not in a screen,
             * meaning we cannot draw our bow using {@link GameOptions#useKey} since it would not work if you are in a
             * screen. Similarly, drawing our bow by {@link ClientPlayerInteractionManager#interactItem} would get
             * cancelled by default within the handleInputEvents method if you do not have the use key held down,
             * essentially meaning without the following injection it would not work if you don't have a screen open?
             * //@see meteordevelopment.meteorclient.mixin.MinecraftClientMixin#wrapStopUsing(ClientPlayerInteractionManager, PlayerEntity)
             */
            @Override
            protected void tick(HighwayBuilderTHM b) {
                if (cooldown > 0) {
                    cooldown--;
                    return;
                }

                if (!InvUtils.testInMainHand(Items.BOW)) {
                    int slot = findAndMoveToHotbar(b, itemStack -> itemStack.getItem() instanceof BowItem);
                    if (slot == -1) {
                        b.destroyCrystalTraps.set(false);
                        b.warning("No bow found to destroy crystal traps with. Toggling the setting off.");
                        b.setState(Forward);
                        b.mc.interactionManager.stopUsingItem(b.mc.player);
                        b.drawingBow = false;
                        return;
                    }

                    InvUtils.swap(slot, false);
                }

                EndCrystalEntity potentialTarget = (EndCrystalEntity) TargetUtils.get(entity -> {
                    if (!(entity instanceof EndCrystalEntity endCrystal)) return false;
                    if (PlayerUtils.isWithin(endCrystal, 12) || !PlayerUtils.isWithin(endCrystal, 24)) return false;
                    if (b.ignoreCrystals.contains(endCrystal)) return false;

                    Vec3d vec1 = new Vec3d(0, 0, 0);
                    Vec3d vec2 = new Vec3d(0, 0, 0);

                    ((IVec3d) vec1).meteor$set(b.mc.player.getX(), b.mc.player.getY() + b.mc.player.getStandingEyeHeight(), b.mc.player.getZ());
                    ((IVec3d) vec2).meteor$set(entity.getX(), entity.getY() + 0.5, entity.getZ());
                    return b.mc.world.raycast(new RaycastContext(vec1, vec2, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, b.mc.player)).getType() == HitResult.Type.MISS;
                }, SortPriority.LowestDistance);

                if (target == null || target.isRemoved()) {
                    if (potentialTarget == null) {
                        b.setState(Forward);
                        b.mc.interactionManager.stopUsingItem(b.mc.player);
                        b.drawingBow = false;
                        return;
                    }
                    else {
                        target = potentialTarget;
                        shots = 0;
                    }
                }

                if (shots >= 3) {
                    b.ignoreCrystals.add(target);
                    b.warning("Detected potential hangup on a crystal. Adding it to ignore list and continuing forward.");
                    b.setState(Forward);
                    b.mc.interactionManager.stopUsingItem(b.mc.player);
                    b.drawingBow = false;
                    return;
                }

                b.mc.player.setYaw((float) Rotations.getYaw(target));

                float pitch = aim(b, target);
                if (Float.isNaN(pitch)) b.mc.player.setPitch((float) Rotations.getPitch(target));
                else b.mc.player.setPitch(pitch);

                if (BowItem.getPullProgress(b.mc.player.getItemUseTime() - 3) >= 1.0f) {
                    b.mc.interactionManager.stopUsingItem(b.mc.player);
                    b.drawingBow = false;
                    cooldown = 20;
                    shots++;
                }
                else {
                    b.drawingBow = true;
                    b.mc.interactionManager.interactItem(b.mc.player, Hand.MAIN_HAND);
                }
            }

            private float aim(HighwayBuilderTHM b, Entity target) {
                // Velocity based on bow charge.
                float velocity = BowItem.getPullProgress(b.mc.player.getItemUseTime());

                // Positions
                Vec3d pos = target.getEntityPos();

                double relativeX = pos.x - b.mc.player.getX();
                double relativeY = pos.y + 0.5 - b.mc.player.getEyeY(); // aiming a little bit above the bottom of the crystal, hopefully prevents shooting the floor or failing the raytrace check
                double relativeZ = pos.z - b.mc.player.getZ();

                // Calculate the pitch
                double hDistance = Math.sqrt(relativeX * relativeX + relativeZ * relativeZ);
                double hDistanceSq = hDistance * hDistance;
                float g = 0.006f;
                float velocitySq = velocity * velocity;

                return (float) -Math.toDegrees(Math.atan((velocitySq - Math.sqrt(velocitySq * velocitySq - g * (g * hDistanceSq + 2 * relativeY * velocitySq))) / (g * hDistance)));
            }
        };

        protected void start(HighwayBuilderTHM b) {}

        protected abstract void tick(HighwayBuilderTHM b);

        protected void mine(HighwayBuilderTHM b, MBPIterator it, boolean mineBlocksToPlace, State nextState, State lastState) {
            boolean breaking = false;
            boolean finishedBreaking = false; // if you can multi break this lets you mine blocks between tasks in a single tick

            // extract all candidates for double mining and enqueue them to be mined. After those we can break the remaining
            // blocks normally
            if (b.doubleMine.get()) {
                ArrayDeque<BlockPos> toDoubleMine = new ArrayDeque<>();

                it.save();
                it.forEach(pos -> {
                    if (b.shouldSkipSignBreak(pos.getBlockPos(), pos.getState())) return;
                    // only want to double mine blocks that we can mine, that are not instamined, and we are not already mining
                    if (
                        b.safeCanBreak(pos.getBlockPos(), pos.getState())
                            && (mineBlocksToPlace || !b.blocksToPlace.get().contains(pos.getState().getBlock()))
                            && !b.safeCanInstaBreakForBreaking(pos.getBlockPos()) && (!Modules.get().get(SpeedMine.class).instamine() || pos.getState().calcBlockBreakingDelta(b.mc.player, b.mc.world, pos.getBlockPos()) <= 0.5)
                            && (b.normalMining == null || !pos.getBlockPos().equals(b.normalMining.blockPos))
                            && (b.packetMining == null || !pos.getBlockPos().equals(b.packetMining.blockPos))
                    ) {
                        toDoubleMine.add(pos.getBlockPos().mutableCopy());
                    }
                });

                // have to save and restore the iterator from the beginning to make sure the subsequent loop can use it properly
                it.restore();

                // repeating the code for swapping to a tool, since we don't want to start mining a block if we don't
                // have a tool to mine it with, but also we want to lock the slot to the tool while we are mining even
                // the ArrayDeque is empty
                if (!toDoubleMine.isEmpty()) {
                    int slot = findAndMoveBestToolToHotbar(b, b.mc.world.getBlockState(toDoubleMine.peek()), false);
                    if (slot == -1) return;

                    if (slot != b.mc.player.getInventory().getSelectedSlot()) InvUtils.swap(slot, false);
                    doubleMine(b, toDoubleMine);
                }

                if (b.normalMining != null || b.packetMining != null) {
                    int slot = findAndMoveBestToolToHotbar(b, b.normalMining != null ? b.normalMining.blockState : b.packetMining.blockState, false);
                    if (slot == -1) return;

                    if (slot != b.mc.player.getInventory().getSelectedSlot()) InvUtils.swap(slot, false);
                    return;
                }
            }

            for (MBlockPos pos : it) {
                if (b.count >= b.currentMineActionsThisTick()) return;
                if (b.breakTimer > 0) return;

                BlockState state = pos.getState();
                if (state.isAir() || (!mineBlocksToPlace && b.blocksToPlace.get().contains(state.getBlock()))) continue;
                if (b.shouldSkipSignBreak(pos.getBlockPos(), state)) continue;
                int mineCost = b.mineBudgetCost(state);
                if (b.count + mineCost > b.currentMineActionsThisTick()) return;

                int slot = findAndMoveBestToolToHotbar(b, state, false);
                if (slot == -1) return;

                if (slot != b.mc.player.getInventory().getSelectedSlot()) InvUtils.swap(slot, false);

                BlockPos mcPos = pos.getBlockPos();
                boolean multiBreak = b.currentMineActionsThisTick() > 1 && b.safeCanInstaBreakForBreaking(mcPos) && !b.rotation.get().mine;
                if (b.safeCanBreak(mcPos, state)) {
                    Block blockBefore = state.getBlock();
                    if (!b.tryMineBlock(mcPos, state, b.rotation.get().mine)) continue;
                    if (b.mc.world.getBlockState(mcPos).getBlock() != blockBefore) {
                        BlockPos countedPos = mcPos.toImmutable();
                        boolean eligible = b.isLegacyCountableBrokenState(this);
                        boolean alreadyCounted = b.countedBrokenForwardPositions.contains(countedPos);
                        b.recordConfirmedBrokenBlockForStats(mcPos);
                        boolean countedNow = !alreadyCounted && b.countedBrokenForwardPositions.contains(countedPos);
                        b.logLegacyStatsDecision("legacy-mine", this, countedPos, blockBefore, b.mc.world.getBlockState(mcPos).getBlock(), eligible, alreadyCounted, countedNow);
                    }
                    b.count += mineCost - 1;
                    breaking = true;

                    // can only multi break if we aren't rotating and the block can be insta-mined
                    if (!multiBreak) break;
                }

                if (!it.hasNext() && b.safeCanInstaBreakForBreaking(mcPos)) finishedBreaking = true;
            }

            // we quickly jump to the next state, to remove micro delays in the process and allow us to break blocks
            // between tasks if we can multi break
            if (finishedBreaking || !breaking) {
                b.setState(nextState, lastState);
            }
        }

        private void doubleMine(HighwayBuilderTHM b, ArrayDeque<BlockPos> blocks) {
            if (b.breakTimer > 0) return;

            if (b.normalMining == null) {
                DoubleMineBlock block = new DoubleMineBlock(b, blocks.pop());
                b.normalMining = block.startDestroying();

                b.breakTimer = b.breakDelay.get();
                if (b.breakTimer > 0) return;
            }

            if (DoubleMineBlock.rateLimited) return;

            if (b.packetMining == null && !blocks.isEmpty()) {
                DoubleMineBlock block = new DoubleMineBlock(b, blocks.pop());

                if (block != null) {
                    b.packetMining = b.normalMining.packetMine();
                    b.normalMining = block.startDestroying();

                    b.breakTimer = b.breakDelay.get();
                }
            }
        }

        protected void place(HighwayBuilderTHM b, MBPIterator it, int slot, State nextState) {
            boolean placed = false;
            boolean finishedPlacing = false;
            int scannedTargets = 0;
            boolean throttleEnclosurePlacement = this == PlaceShulkerBlockade || this == PlaceEChestBlockade;
            boolean retryEnclosurePlacement = false;
            // Double-chest: this one blockade column is built as an ender chest instead of obsidian.
            BlockPos doubleChestPos = throttleEnclosurePlacement ? b.doubleChestBlockadePos(this) : null;

            if (b.restockDebugLog.get() && throttleEnclosurePlacement) {
                b.restockDebug("%s tick using hotbar slot %d and blockade=%s.",
                    b.stateName(this),
                    slot,
                    b.getEffectiveBlockadeType()
                );
                b.logRestockBlockadeProbe(b.stateName(this), it);
            }

            if (throttleEnclosurePlacement && b.handlePendingEnclosurePlacementConfirmation(this, () ->
                b.error("Unable to complete restock blockade: placement at " + b.formatBlockPos(b.pendingEnclosurePlacementConfirmPos) + " did not confirm after centering.")
            )) return;

            for (MBlockPos pos : it) {
                scannedTargets++;
                BlockPos targetPos = pos.getBlockPos();
                boolean lastTarget = !it.hasNext();
                // Double-chest: the column at doubleChestPos wants an ender chest, not obsidian.
                boolean doubleChestTarget = doubleChestPos != null && targetPos.equals(doubleChestPos);
                if (doubleChestTarget && b.mc.world.getBlockState(targetPos).getBlock() == Blocks.ENDER_CHEST) {
                    if (lastTarget) finishedPlacing = true;
                    continue; // double chest already down
                }

                if (b.count >= it.placementsPerTick(b)) {
                    if (b.restockDebugLog.get() && throttleEnclosurePlacement) {
                        b.restockDebug("%s paused: placement count limit reached (%d/%d).",
                            b.stateName(this),
                            b.count,
                            it.placementsPerTick(b)
                        );
                    }
                    return;
                }
                if (b.placeTimer > 0) {
                    if (b.restockDebugLog.get() && throttleEnclosurePlacement) {
                        b.restockDebug("%s paused: placeTimer=%d before attempting %s.",
                            b.stateName(this),
                            b.placeTimer,
                            b.formatBlockPos(targetPos)
                        );
                    }
                    return;
                }

                if (throttleEnclosurePlacement && b.isValidRestockStructureBlock(b.mc.world.getBlockState(targetPos))) {
                    if (lastTarget) finishedPlacing = true;
                    continue;
                }

                if (throttleEnclosurePlacement && b.isPlayerHitboxBlockingBlock(targetPos)) {
                    BlockPos centerTarget = b.enclosureCenterTargetForState(this);
                    if (!b.ensureCenteredOnBlockBeforeEnclosurePlacement(this, centerTarget, b.stateName(this) + "-target-blocked")) return;
                    b.error("Unable to complete restock blockade: player hitbox is blocking required block " + b.formatBlockPos(targetPos) + " after centering.");
                    return;
                }

                if (!RangeUtils.isInRange(b.placeRange.get(), targetPos)) {
                    if (b.restockDebugLog.get() && throttleEnclosurePlacement) {
                        b.restockDebug("%s skipped %s: out of range.",
                            b.stateName(this),
                            b.formatBlockPos(targetPos)
                        );
                    }
                    if (throttleEnclosurePlacement) retryEnclosurePlacement = true;
                    if (lastTarget) finishedPlacing = true;
                    continue;
                }

                if (throttleEnclosurePlacement && !b.consumeEnclosurePlaceCredit()) {
                    if (b.restockDebugLog.get()) {
                        b.restockDebug("%s paused: enclosure placement throttle credit=%d/%d before attempting %s.",
                            b.stateName(this),
                            b.enclosurePlaceCreditTenths,
                            ENCLOSURE_PLACE_CREDIT_SCALE,
                            b.formatBlockPos(targetPos)
                        );
                    }
                    return;
                }

                // CheckEntities & SwapBack are disabled for waiting for better accuracy and speed of the builder
                BlockState stateBefore = b.mc.world.getBlockState(targetPos);
                // Double-chest column: place an ender chest from a spare slot instead of obsidian; if we
                // have none, fall through to the normal obsidian block (plain single chest).
                int placeSlot = slot;
                if (doubleChestTarget) {
                    int echestSlot = findAndMoveToHotbar(b, itemStack -> itemStack.getItem() == Items.ENDER_CHEST, false);
                    if (echestSlot >= 0) placeSlot = echestSlot;
                }
                boolean placedThisTick = b.tryPlaceBlock(targetPos, placeSlot, b.rotation.get().place);

                if (b.restockDebugLog.get() && throttleEnclosurePlacement) {
                    BlockState stateAfterAttempt = b.mc.world.getBlockState(targetPos);
                    b.restockDebug("%s attempt %s with slot %d -> success=%s, stateNow=%s, canPlaceNow=%s",
                        b.stateName(this),
                        b.formatBlockPos(targetPos),
                        slot,
                        placedThisTick,
                        stateAfterAttempt.getBlock(),
                        BlockUtils.canPlace(targetPos)
                    );
                }

                if (placedThisTick) {
                    if (throttleEnclosurePlacement) {
                        b.startEnclosurePlacementConfirmation(this, targetPos, b.enclosureCenterTargetForState(this), null, b.stateName(this));
                        placed = true;
                        return;
                    }

                    boolean worldChanged = !b.mc.world.getBlockState(targetPos).equals(stateBefore);
                    if (worldChanged) {
                        BlockPos countedPos = targetPos.toImmutable();
                        boolean eligible = b.shouldCountLegacyPlacedPosition(this, countedPos);
                        boolean alreadyCounted = b.countedPlacedForwardPositions.contains(countedPos);
                        b.recordPlacedBlockForStats(targetPos);
                        b.restockWatchdog.markProgress("blockade-place");
                        boolean countedNow = !alreadyCounted && b.countedPlacedForwardPositions.contains(countedPos);
                        b.logLegacyStatsDecision("legacy-place", this, countedPos, stateBefore.getBlock(), b.mc.world.getBlockState(targetPos).getBlock(), eligible, alreadyCounted, countedNow);
                    }
                    placed = true;
                    if (b.currentPlaceActionsThisTick() == 1) break;
                }
                else if (throttleEnclosurePlacement) {
                    BlockState stateAfterAttempt = b.mc.world.getBlockState(targetPos);
                    if (!b.isValidRestockStructureBlock(stateAfterAttempt)) {
                        retryEnclosurePlacement = true;
                        if (b.restockDebugLog.get()) {
                            b.restockDebug("%s placement failed at %s and target is still unsatisfied; staying in placement state.",
                                b.stateName(this),
                                b.formatBlockPos(targetPos)
                            );
                        }
                        break;
                    }
                }

                if (lastTarget) finishedPlacing = true;
            }

            if (b.restockDebugLog.get() && throttleEnclosurePlacement) {
                b.restockDebug("%s completed tick: scanned=%d placedAny=%s finishedPlacing=%s retry=%s next=%s",
                    b.stateName(this),
                    scannedTargets,
                    placed,
                    finishedPlacing,
                    retryEnclosurePlacement,
                    b.stateName(nextState)
                );
            }

            if (throttleEnclosurePlacement) {
                if (retryEnclosurePlacement) return;
                if (finishedPlacing || scannedTargets == 0 || !placed) b.setState(nextState);
                return;
            }

            if (finishedPlacing || !placed) b.setState(nextState);
        }

        private int findSlot(HighwayBuilderTHM b, Predicate<ItemStack> predicate, boolean hotbar) {
            for (int i = hotbar ? 0 : 9; i < (hotbar ? 9 : b.mc.player.getInventory().getMainStacks().size()); i++) {
                if (predicate.test(b.mc.player.getInventory().getStack(i))) return i;
            }

            return -1;
        }

        protected int findHotbarSlot(HighwayBuilderTHM b, boolean replaceTools) {
            return findHotbarSlot(b, replaceTools, true);
        }

        protected int findHotbarSlot(HighwayBuilderTHM b, boolean replaceTools, boolean failHard) {
            int thrashSlot = -1;
            int slotsWithBlocks = 0;
            int slotWithLeastBlocks = -1;
            int slotWithLeastBlocksCount = Integer.MAX_VALUE;
            int fallbackOccupiedSlot = -1;

            // Loop hotbar
            for (int i = 0; i < 9; i++) {
                ItemStack itemStack = b.mc.player.getInventory().getStack(i);
                if (b.isHotbarSlotReservedByManager(i)) {
                    if (b.restockDebugLog.get()) {
                        b.restockDebug("findHotbarSlot skipping reserved hotbar slot %d for HotbarManager item %s.",
                            i,
                            b.getReservedHotbarItem(i)
                        );
                    }
                    continue;
                }

                if (fallbackOccupiedSlot == -1) fallbackOccupiedSlot = i;

                // Return if the slot is empty
                if (itemStack.isEmpty()) return i;

                // Return if the slot contains a tool and replacing tools is enabled
                if (replaceTools && AutoTool.isTool(itemStack)) return i;

                // Store the slot if it contains thrash
                if (b.isDroppableTrashStack(itemStack)) thrashSlot = i;

                // Update tracked stats about slots that contain building blocks
                if (itemStack.getItem() instanceof BlockItem blockItem && (b.blocksToPlace.get().contains(blockItem.getBlock()) || b.blocksToPlace.get().contains(Blocks.OBSIDIAN) && blockItem == Items.ENDER_CHEST)) {
                    slotsWithBlocks++;

                    if (itemStack.getCount() < slotWithLeastBlocksCount) {
                        slotWithLeastBlocksCount = itemStack.getCount();
                        slotWithLeastBlocks = i;
                    }
                }
            }

            // Return thrash slot if found
            if (thrashSlot != -1) return thrashSlot;

            // If there are more than 1 slots with building blocks return the slot with the lowest amount of blocks
            if (slotsWithBlocks > 0) return slotWithLeastBlocks;

            // As a final fallback, use any unreserved hotbar slot even if it is occupied.
            if (fallbackOccupiedSlot != -1) {
                if (b.restockDebugLog.get()) {
                    b.restockDebug("findHotbarSlot using occupied unreserved hotbar slot %d as fallback.", fallbackOccupiedSlot);
                }
                return fallbackOccupiedSlot;
            }

            // No space found in hotbar
            if (failHard) b.error("No empty space in hotbar.");
            return -1;
        }

        protected int countItem(HighwayBuilderTHM b, Predicate<ItemStack> predicate) {
            int count = 0;
            for (int i = 0; i < b.mc.player.getInventory().getMainStacks().size(); i++) {
                ItemStack stack = b.mc.player.getInventory().getStack(i);
                if (predicate.test(stack)) count += stack.getCount();
            }

            return count;
        }

        protected int findAndMoveToHotbar(HighwayBuilderTHM b, Predicate<ItemStack> predicate) {
            return findAndMoveToHotbar(b, predicate, true);
        }

        protected int findAndMoveToHotbar(HighwayBuilderTHM b, Predicate<ItemStack> predicate, boolean failHardNoHotbar) {
            // Check hotbar
            int slot = findSlot(b, predicate, true);
            if (slot != -1) {
                if (b.restockDebugLog.get()) b.restockDebug("findAndMoveToHotbar found matching item already in hotbar slot %d.", slot);
                return slot;
            }

            slot = tryMoveCursorToHotbar(b, predicate, failHardNoHotbar);
            if (slot != -1) {
                if (b.restockDebugLog.get()) b.restockDebug("findAndMoveToHotbar moved cursor stack into hotbar slot %d.", slot);
                return slot;
            }

            // Check inventory
            slot = findSlot(b, predicate, false);

            // Return if no items were found
            if (slot == -1) {
                if (b.restockDebugLog.get()) b.restockDebug("findAndMoveToHotbar failed: no matching inventory slot found.");
                return -1;
            }

            ItemStack inventoryStack = b.mc.player.getInventory().getStack(slot);
            int hotbarSlot = b.getPreferredManagedHotbarSlot(inventoryStack.getItem());
            if (hotbarSlot != -1) {
                if (b.restockDebugLog.get()) {
                    b.restockDebug("findAndMoveToHotbar using HotbarManager slot %d for managed item %s.", hotbarSlot, inventoryStack.getItem());
                }
            } else {
                hotbarSlot = findHotbarSlot(b, false, failHardNoHotbar);
                if (hotbarSlot == -1) {
                    if (b.restockDebugLog.get()) b.restockDebug("findAndMoveToHotbar failed: no hotbar slot available (failHard=%s).", failHardNoHotbar);
                    return -1;
                }
            }

            if (b.restockDebugLog.get()) {
                b.restockDebug("findAndMoveToHotbar moving inventory slot %d into hotbar slot %d.", slot, hotbarSlot);
            }
            InvUtils.move().from(slot).toHotbar(hotbarSlot);
            if (!b.clearCursorStackToEmptySlot("findAndMoveToHotbar") && !b.dropCursorStackIfSafe("findAndMoveToHotbar")) {
            }

            return hotbarSlot;
        }

        private int tryMoveCursorToHotbar(HighwayBuilderTHM b, Predicate<ItemStack> predicate, boolean failHardNoHotbar) {
            if (b.mc.player == null || b.mc.player.currentScreenHandler == null) return -1;

            ItemStack cursorStack = b.mc.player.currentScreenHandler.getCursorStack();
            if (cursorStack.isEmpty() || !predicate.test(cursorStack)) return -1;

            int hotbarSlot = b.getPreferredManagedHotbarSlot(cursorStack.getItem());
            if (hotbarSlot != -1) {
                if (b.restockDebugLog.get()) {
                    b.restockDebug("findAndMoveToHotbar using HotbarManager slot %d for cursor item %s.", hotbarSlot, cursorStack.getItem());
                }
            } else {
                hotbarSlot = findHotbarSlot(b, false, failHardNoHotbar);
                if (hotbarSlot == -1) {
                    if (b.restockDebugLog.get()) {
                        b.restockDebug("findAndMoveToHotbar failed: no hotbar slot available for cursor item %s (failHard=%s).", cursorStack.getItem(), failHardNoHotbar);
                    }
                    return -1;
                }
            }

            b.mc.interactionManager.clickSlot(
                b.mc.player.currentScreenHandler.syncId,
                SlotUtils.indexToId(hotbarSlot),
                0,
                SlotActionType.PICKUP,
                b.mc.player
            );

            if (!b.clearCursorStackToEmptySlot("findAndMoveToHotbar-cursor") && !b.dropCursorStackIfSafe("findAndMoveToHotbar-cursor")) {
            }

            return predicate.test(b.mc.player.getInventory().getStack(hotbarSlot)) ? hotbarSlot : -1;
        }

        protected int findPreferredTrashBlockToHotbar(HighwayBuilderTHM b, boolean failHardNoHotbar) {
            int slot = b.findPreferredTrashBlockSlot();
            if (slot == -1) return -1;
            if (slot < 9) return slot;

            ItemStack inventoryStack = b.mc.player.getInventory().getStack(slot);
            int hotbarSlot = b.getPreferredManagedHotbarSlot(inventoryStack.getItem());
            if (hotbarSlot != -1) {
                if (b.restockDebugLog.get()) {
                    b.restockDebug("findPreferredTrashBlockToHotbar using HotbarManager slot %d for preferred trash block %s.",
                        hotbarSlot,
                        inventoryStack.getItem()
                    );
                }
            } else {
                hotbarSlot = findHotbarSlot(b, false, failHardNoHotbar);
                if (hotbarSlot == -1) return -1;
            }

            if (b.restockDebugLog.get()) {
                b.restockDebug("findPreferredTrashBlockToHotbar moving inventory slot %d into hotbar slot %d for %s.",
                    slot,
                    hotbarSlot,
                    inventoryStack.getItem()
                );
            }

            InvUtils.move().from(slot).toHotbar(hotbarSlot);
            if (!b.clearCursorStackToEmptySlot("findPreferredTrashBlockToHotbar")
                && !b.dropCursorStackIfSafe("findPreferredTrashBlockToHotbar")) {
            }

            return hotbarSlot;
        }

        protected int findAndMoveBestToolToHotbar(HighwayBuilderTHM b, BlockState blockState, boolean noSilkTouch) {
            return findAndMoveBestToolToHotbar(b, blockState, noSilkTouch, true);
        }

        protected int findAndMoveBestToolToHotbar(HighwayBuilderTHM b, BlockState blockState, boolean noSilkTouch, boolean failHardNoHotbar) {
            // Check for creative
            if (b.mc.player.isCreative()) return b.mc.player.getInventory().getSelectedSlot();

            // Find best tool
            double bestScore = -1;
            int bestSlot = -1;

            for (int i = 0; i < b.mc.player.getInventory().getMainStacks().size(); i++) {
                double score = AutoTool.getScore(b.mc.player.getInventory().getStack(i), blockState, false, false, AutoTool.EnchantPreference.None, itemStack -> {
                    if (noSilkTouch && Utils.hasEnchantment(itemStack, Enchantments.SILK_TOUCH)) return false;
                    return !b.dontBreakTools.get() || itemStack.getMaxDamage() - itemStack.getDamage() > (itemStack.getMaxDamage() * (b.breakDurability.get() / 100.0));
                });

                if (score > bestScore) {
                    bestScore = score;
                    bestSlot = i;
                }
            }

            if (bestSlot == -1) return b.mc.player.getInventory().getSelectedSlot();

            ItemStack bestStack = b.mc.player.getInventory().getStack(bestSlot);
            if (bestStack.isIn(ItemTags.PICKAXES)) {
                int count = countItem(b, stack -> stack.isIn(ItemTags.PICKAXES));

                // If we are in the process of restocking pickaxes and happen to need one, we should allow using it
                // as long as it has enough durability, since we will obtain more shortly thereafter
                if (count <= b.savePickaxes.get() && !(b.restockTask.pickaxes && bestStack.getMaxDamage() - bestStack.getDamage() > (bestStack.getMaxDamage() * (b.breakDurability.get() / 100.0)))) {
                    if (!b.restockTask.pickaxes && (b.searchEnderChest.get() || b.searchShulkers.get())) {
                        b.restockTask.setPickaxes();
                    }
                    else {
                        b.notifyDesktop(b.notifyPickaxeShortage, "THM Highway Builder", "Found less than required pickaxes: " + count + "/" + (b.savePickaxes.get() + 1));
                        b.error("Found less than the minimum amount of pickaxes required: " + count + "/" + (b.savePickaxes.get() + 1));
                    }

                    return -1;
                }
            }

            // Check if the tool is already in hotbar
            if (bestSlot < 9) return bestSlot;

            int hotbarSlot = b.getPreferredManagedHotbarSlot(bestStack.getItem());
            if (hotbarSlot != -1) {
                if (b.restockDebugLog.get()) {
                    b.restockDebug("findAndMoveBestToolToHotbar using HotbarManager slot %d for managed item %s.",
                        hotbarSlot,
                        bestStack.getItem()
                    );
                }
            } else {
                hotbarSlot = findHotbarSlot(b, true, failHardNoHotbar);
                if (hotbarSlot == -1) return -1;
            }

            if (b.restockDebugLog.get()) {
                b.restockDebug("findAndMoveBestToolToHotbar moving inventory slot %d into hotbar slot %d for %s.",
                    bestSlot,
                    hotbarSlot,
                    blockState.getBlock()
                );
            }
            InvUtils.move().from(bestSlot).toHotbar(hotbarSlot);
            if (!b.clearCursorStackToEmptySlot("findAndMoveBestToolToHotbar") && !b.dropCursorStackIfSafe("findAndMoveBestToolToHotbar")) {
            }

            return hotbarSlot;
        }

        protected int findBlocksToPlace(HighwayBuilderTHM b) {
            if (b.placeFromOffhand()) return SlotUtils.OFFHAND;

            // find a block and move it to your hotbar
            int slot = findAndMoveToHotbar(b, itemStack -> itemStack.getItem() instanceof BlockItem blockItem && b.blocksToPlace.get().contains(blockItem.getBlock()));

            if (slot == -1) slot = b.giveCreativePlacementBlock();

            if (slot == -1) {
                if (b.restockDebugLog.get()) {
                    b.restockDebug("findBlocksToPlace failed. searchEnderChest=%s searchShulkers=%s mineEnderChests=%s savedEchests=%d currentEchests=%d",
                        b.searchEnderChest.get(),
                        b.searchShulkers.get(),
                        b.mineEnderChests.get(),
                        b.saveEchests.get(),
                        countItem(b, stack -> stack.getItem().equals(Items.ENDER_CHEST))
                    );
                }
                boolean isObsidianShortage = b.blocksToPlace.get().contains(Blocks.OBSIDIAN);
                if (isObsidianShortage) {
                    if (
                        (b.searchEnderChest.get() || b.searchShulkers.get())
                        || (b.mineEnderChests.get() && countItem(b, stack -> stack.getItem().equals(Items.ENDER_CHEST)) > b.saveEchests.get())
                    ) {
                        b.restockTask.setMaterials();
                    } else if (b.kitbotRestock.get()) {
                        b.restockTask.setMaterials();
                    } else {
                        b.notifyDesktop(b.notifyOutOfBlocks, "THM Highway Builder", "Out of blocks to place.");
                        b.error("Out of blocks to place.");
                    }
                } else {
                    b.notifyDesktop(b.notifyOutOfBlocks, "THM Highway Builder", "Out of blocks to place.");
                    b.error("Out of blocks to place.");
                }

                return -1;
            }

            return slot;
        }

        protected int findBlocksToPlacePrioritizeTrash(HighwayBuilderTHM b) {
            int slot = findPreferredTrashBlockToHotbar(b, true);

            return slot != -1 ? slot : findBlocksToPlace(b);
        }
    }

    private interface MBPIterator extends Iterator<MBlockPos>, Iterable<MBlockPos> {
        void save();
        void restore();

        @NotNull
        @Override
        default Iterator<MBlockPos> iterator() {
            return this;
        }

        default int placementsPerTick(HighwayBuilderTHM b) {
            return b.currentPlaceActionsThisTick();
        }
    }

    private static final MBPIterator EMPTY_ITERATOR = new MBPIterator() {
        @Override
        public void save() {}

        @Override
        public void restore() {}

        @Override
        public boolean hasNext() {
            return false;
        }

        @Override
        public MBlockPos next() {
            throw new NoSuchElementException();
        }
    };

    private enum EatingPauseOwner {
        NONE("none"),
        AUTO_EAT("AutoEat"),
        AUTO_GAP("AutoGap");

        private final String label;

        EatingPauseOwner(String label) {
            this.label = label;
        }
    }

    private enum EatingRetryPhase {
        NONE,
        PENDING_SCREEN_CLOSE,
        PENDING_SCREEN_SETTLE,
        PENDING_CURSOR_DROP,
        PENDING_RESTART,
        PENDING_HOTBAR_SWAP_DELAY,
        PENDING_HOTBAR_SWAP_GRACE,
        PENDING_MODULE_REENABLE,
        PENDING_MODULE_RESTART_AFTER_REENABLE
    }

    private static final class EatingPauseWatchdogState {
        private static final long NO_PROGRESS_TIMEOUT_MS = 3_000L;
        private static final long MAX_CONTINUOUS_USE_MS = 2_500L;
        private static final long HOTBAR_SWAP_DELAY_MS = 250L;
        private static final long HOTBAR_SWAP_GRACE_MS = 3_000L;
        private static final int MAX_RETRIES = 2;
        private static final int HOTBAR_SWAP_MAX_ATTEMPTS = 2;
        private static final int MODULE_REENABLE_DELAY_TICKS = 4;

        private EatingPauseWatchdogState() {
        }
    }

    private static class CustomPlayerInput extends Input {
        private boolean forward;
        private boolean backward;
        private boolean left;
        private boolean right;
        private boolean jump;
        private boolean sneak;
        private boolean sprint;

        public CustomPlayerInput() {
            syncPlayerInput();
            movementVector = Vec2f.ZERO;
        }

        public void setState(boolean forward, boolean backward, boolean left, boolean right, boolean jump, boolean sneak, boolean sprint) {
            if (this.forward == forward
                && this.backward == backward
                && this.left == left
                && this.right == right
                && this.jump == jump
                && this.sneak == sneak
                && this.sprint == sprint) {
                return;
            }

            this.forward = forward;
            this.backward = backward;
            this.left = left;
            this.right = right;
            this.jump = jump;
            this.sneak = sneak;
            this.sprint = sprint;
            syncPlayerInput();
        }

        public void forward(boolean value) {
            setState(value, backward, left, right, jump, sneak, sprint);
        }

        public void backward(boolean value) {
            setState(forward, value, left, right, jump, sneak, sprint);
        }

        public void left(boolean value) {
            setState(forward, backward, value, right, jump, sneak, sprint);
        }

        public void right(boolean value) {
            setState(forward, backward, left, value, jump, sneak, sprint);
        }

        public void jump(boolean value) {
            setState(forward, backward, left, right, value, sneak, sprint);
        }

        public void sneak(boolean value) {
            setState(forward, backward, left, right, jump, value, sprint);
        }

        public void sprint(boolean value) {
            setState(forward, backward, left, right, jump, sneak, value);
        }

        public void stop() {
            if (!forward && !backward && !left && !right && !jump && !sneak && !sprint) {
                movementVector = Vec2f.ZERO;
                return;
            }

            setState(false, false, false, false, false, false, false);
            movementVector = Vec2f.ZERO;
        }

        public boolean isForward() {
            return forward;
        }

        public boolean isBackward() {
            return backward;
        }

        public boolean isLeft() {
            return left;
        }

        public boolean isRight() {
            return right;
        }

        public boolean isJump() {
            return jump;
        }

        public boolean isSneak() {
            return sneak;
        }

        public float forwardAmount() {
            return movementAmount(forward, backward);
        }

        public float sidewaysAmount() {
            return movementAmount(left, right);
        }

        @Override
        public void tick() {
            movementVector = new Vec2f(sidewaysAmount(), forwardAmount()).normalize();
        }

        private void syncPlayerInput() {
            playerInput = new PlayerInput(forward, backward, left, right, jump, sneak, sprint);
        }

    }

    private static class MBPIteratorFilter implements MBPIterator {
        private final MBPIterator it;
        private final Predicate<MBlockPos> predicate;

        private MBlockPos pos;
        private boolean isOld = true;

        private boolean pisOld = true;

        public MBPIteratorFilter(MBPIterator it, Predicate<MBlockPos> predicate) {
            this.it = it;
            this.predicate = predicate;
        }

        @Override
        public void save() {
            it.save();
            pisOld = isOld;
            isOld = true;
        }

        @Override
        public void restore() {
            it.restore();
            isOld = pisOld;
        }

        @Override
        public boolean hasNext() {
            if (isOld) {
                isOld = false;
                pos = null;

                while (it.hasNext()) {
                    pos = it.next();

                    if (predicate.test(pos)) return true;
                    else pos = null;
                }
            }

            return pos != null && predicate.test(pos);
        }

        @Override
        public MBlockPos next() {
            isOld = true;
            return pos;
        }
    }

    private static class MBPIteratorChain implements MBPIterator {
        private final MBPIterator first;
        private final Supplier<MBPIterator> secondFactory;
        private MBPIterator second;
        private boolean usingFirst = true;
        private boolean previousUsingFirst = true;

        public MBPIteratorChain(MBPIterator first, Supplier<MBPIterator> secondFactory) {
            this.first = first;
            this.secondFactory = secondFactory;
        }

        private MBPIterator second() {
            if (second == null) second = secondFactory.get();
            return second;
        }

        @Override
        public void save() {
            first.save();
            if (second != null) second.save();
            previousUsingFirst = usingFirst;
            usingFirst = true;
        }

        @Override
        public void restore() {
            first.restore();
            if (second != null) second.restore();
            usingFirst = previousUsingFirst;
        }

        @Override
        public boolean hasNext() {
            if (usingFirst) {
                if (first.hasNext()) return true;
                usingFirst = false;
            }

            return second().hasNext();
        }

        @Override
        public MBlockPos next() {
            if (usingFirst && !first.hasNext()) usingFirst = false;
            return usingFirst ? first.next() : second().next();
        }

        @Override
        public int placementsPerTick(HighwayBuilderTHM b) {
            return first.placementsPerTick(b);
        }
    }

    private static class MBPIteratorConcatSafe implements MBPIterator {
        private final MBPIterator first;
        private final MBPIterator second;
        private boolean usingFirst = true;
        private boolean previousUsingFirst = true;

        public MBPIteratorConcatSafe(MBPIterator first, MBPIterator second) {
            this.first = first;
            this.second = second;
        }

        @Override
        public void save() {
            try {
                first.save();
                second.save();
                previousUsingFirst = usingFirst;
                usingFirst = true;
            } catch (StackOverflowError ignored) {
                usingFirst = false;
            }
        }

        @Override
        public void restore() {
            try {
                first.restore();
                second.restore();
                usingFirst = previousUsingFirst;
            } catch (StackOverflowError ignored) {
                usingFirst = false;
            }
        }

        @Override
        public boolean hasNext() {
            try {
                if (usingFirst) {
                    if (first.hasNext()) return true;
                    usingFirst = false;
                }
                return second.hasNext();
            } catch (StackOverflowError ignored) {
                return false;
            }
        }

        @Override
        public MBlockPos next() {
            try {
                if (usingFirst && !first.hasNext()) usingFirst = false;
                return usingFirst ? first.next() : second.next();
            } catch (StackOverflowError ignored) {
                throw new NoSuchElementException();
            }
        }

        @Override
        public int placementsPerTick(HighwayBuilderTHM b) {
            return first.placementsPerTick(b);
        }
    }

    private interface IBlockPosProvider {
        MBPIterator getFront();
        MBPIterator getBehindFront();
        MBPIterator getFloor();
        MBPIterator getFloor(boolean includeBehind);
        MBPIterator getBehindFloor();

        /**
         * state:
         *  1 for above the railings,
         *  0 for the railings themselves,
         *  -1 for the block under the railings
         */
        MBPIterator getRailings(int state);
        MBPIterator getRailings(int state, boolean includeBehind);
        MBPIterator getBehindRailings(int state);

        MBPIterator getLiquids();
        MBPIterator getBlockade(boolean mine, BlockadeType type);
    }

    private class StraightBlockPosProvider implements IBlockPosProvider {
        private final MBlockPos pos = new MBlockPos();
        private final MBlockPos pos2 = new MBlockPos();

        @Override
        public MBPIterator getFront() {
            pos.coerceBlockLevel(mc.player).offset(dir).offset(leftDir, getWidthLeft());

            return new MBPIterator() {
                private int w, y;
                private int pw, py;

                @Override
                public boolean hasNext() {
                    return w < width.get() && y < height.get();
                }

                @Override
                public MBlockPos next() {
                    pos2.set(pos).offset(rightDir, w).add(0, y, 0);

                    w++;
                    if (w >= width.get()) {
                        w = 0;
                        y++;
                    }

                    return pos2;
                }

                @Override
                public void save() {
                    pw = w;
                    py = y;
                    w = y = 0;
                }

                @Override
                public void restore() {
                    w = pw;
                    y = py;
                }
            };
        }

        @Override
        public MBPIterator getBehindFront() {
            HorizontalDirection backward = dir.opposite();
            HorizontalDirection backwardLeft = backward.rotateLeftSkipOne();
            HorizontalDirection backwardRight = backwardLeft.opposite();
            pos.coerceBlockLevel(mc.player).offset(backward).offset(backwardLeft, getWidthLeft());

            return new MBPIterator() {
                private int w, y;
                private int pw, py;

                @Override
                public boolean hasNext() {
                    return w < width.get() && y < height.get();
                }

                @Override
                public MBlockPos next() {
                    pos2.set(pos).offset(backwardRight, w).add(0, y, 0);

                    w++;
                    if (w >= width.get()) {
                        w = 0;
                        y++;
                    }

                    return pos2;
                }

                @Override
                public void save() {
                    pw = w;
                    py = y;
                    w = y = 0;
                }

                @Override
                public void restore() {
                    w = pw;
                    y = py;
                }
            };
        }

        @Override
        public MBPIterator getFloor() {
            pos.coerceBlockLevel(mc.player).offset(dir).offset(leftDir, getWidthLeft()).add(0, -1, 0);

            return new MBPIterator() {
                private int w;
                private int pw;

                @Override
                public boolean hasNext() {
                    return w < width.get();
                }

                @Override
                public MBlockPos next() {
                    return pos2.set(pos).offset(rightDir, w++);
                }

                @Override
                public void save() {
                    pw = w;
                    w = 0;
                }

                @Override
                public void restore() {
                    w = pw;
                }
            };
        }

        @Override
        public MBPIterator getFloor(boolean includeBehind) {
            if (!includeBehind) return getFloor();
            return new MBPIteratorChain(getFloor(), this::getBehindFloor);
        }

        @Override
        public MBPIterator getBehindFloor() {
            HorizontalDirection backward = dir.opposite();
            HorizontalDirection backwardLeft = backward.rotateLeftSkipOne();
            HorizontalDirection backwardRight = backwardLeft.opposite();
            pos.coerceBlockLevel(mc.player).offset(backward).offset(backwardLeft, getWidthLeft()).add(0, -1, 0);

            return new MBPIterator() {
                private int w;
                private int pw;

                @Override
                public boolean hasNext() {
                    return w < width.get();
                }

                @Override
                public MBlockPos next() {
                    return pos2.set(pos).offset(backwardRight, w++);
                }

                @Override
                public void save() {
                    pw = w;
                    w = 0;
                }

                @Override
                public void restore() {
                    w = pw;
                }
            };
        }

        @Override
        public MBPIterator getRailings(int state) {
            pos.coerceBlockLevel(mc.player).offset(dir);

            return new MBPIterator() {
                private int i, y = state;
                private int pi, py;

                @Override
                public boolean hasNext() {
                    // state == 1 : height
                    // state == 0 : 1
                    // state == -1 : 0
                    return i < 2 && y < (state == 1 ? height.get() : state + 1);
                }

                @Override
                public MBlockPos next() {
                    if (i == 0) pos2.set(pos).offset(leftDir, getWidthLeft() + 1).add(0, y, 0);
                    else pos2.set(pos).offset(rightDir, getWidthRight() + 1).add(0, y, 0);

                    y++;
                    if (y >= (state == 1 ? height.get() : state + 1)) {
                        y = state;
                        i++;
                    }

                    return pos2;
                }

                @Override
                public void save() {
                    pi = i;
                    py = y;
                    i = 0;
                    y = state;
                }

                @Override
                public void restore() {
                    i = pi;
                    y = py;
                }
            };
        }

        @Override
        public MBPIterator getRailings(int state, boolean includeBehind) {
            if (!includeBehind) return getRailings(state);
            return new MBPIteratorChain(getRailings(state), () -> getBehindRailings(state));
        }

        @Override
        public MBPIterator getBehindRailings(int state) {
            HorizontalDirection backward = dir.opposite();
            HorizontalDirection backwardLeft = backward.rotateLeftSkipOne();
            HorizontalDirection backwardRight = backwardLeft.opposite();
            pos.coerceBlockLevel(mc.player).offset(backward);

            return new MBPIterator() {
                private int i, y = state;
                private int pi, py;

                @Override
                public boolean hasNext() {
                    return i < 2 && y < (state == 1 ? height.get() : state + 1);
                }

                @Override
                public MBlockPos next() {
                    if (i == 0) pos2.set(pos).offset(backwardLeft, getWidthLeft() + 1).add(0, y, 0);
                    else pos2.set(pos).offset(backwardRight, getWidthRight() + 1).add(0, y, 0);

                    y++;
                    if (y >= (state == 1 ? height.get() : state + 1)) {
                        y = state;
                        i++;
                    }

                    return pos2;
                }

                @Override
                public void save() {
                    pi = i;
                    py = y;
                    i = 0;
                    y = state;
                }

                @Override
                public void restore() {
                    i = pi;
                    y = py;
                }
            };
        }

        @Override
        public MBPIterator getLiquids() {
            pos.coerceBlockLevel(mc.player).offset(dir, 2).offset(leftDir, getWidthLeft() + (mineAboveRailings.get() ? 2 : 1));

            return new MBPIterator() {
                private int w, y;
                private int pw, py;

                private int getWidth() {
                    return width.get() + (mineAboveRailings.get() ? 2 : 0);
                }

                @Override
                public boolean hasNext() {
                    return w < getWidth() + 2 && y < height.get() + 1;
                }

                @Override
                public MBlockPos next() {
                    pos2.set(pos).offset(rightDir, w).add(0, y, 0);

                    w++;
                    if (w >= getWidth() + 2) {
                        w = 0;
                        y++;
                    }

                    return pos2;
                }

                @Override
                public void save() {
                    pw = w;
                    py = y;
                    w = y = 0;
                }

                @Override
                public void restore() {
                    w = pw;
                    y = py;
                }
            };
        }

        @Override
        public MBPIterator getBlockade(boolean mine, BlockadeType blockadeType) {
            return new MBPIterator() {
                private int i = mine ? -1 : 0, y;
                private int roofIndex;
                private int pi, py;
                private int proofIndex;

                private MBlockPos get(int i) {
                    pos.coerceBlockLevel(mc.player).offset(dir.opposite());

                    return switch (i) {
                        case -1 -> pos;
                        case 0 -> pos.offset(dir.opposite());
                        case 1 -> pos.offset(leftDir);
                        case 2 -> pos.offset(rightDir);
                        case 3 -> pos.offset(dir, 2);
                        case 4 -> pos.offset(dir).offset(leftDir);
                        case 5 -> pos.offset(dir).offset(rightDir);
                        default -> throw new IllegalStateException("Unexpected value: " + i);
                    };
                }

                @Override
                public boolean hasNext() {
                    return (i < blockadeType.columns && y < 2) || (blockadeType.roof && roofIndex < 2);
                }

                @Override
                public MBlockPos next() {
                    if (i < blockadeType.columns && y < 2) {
                        if (width.get() == 1 && railings.get() && i > 0 && y == 0) y++;

                        MBlockPos pos = get(i).add(0, y, 0);

                        y++;
                        if (y > 1) {
                            y = 0;
                            i++;
                        }

                        return pos;
                    }

                    pos.coerceBlockLevel(mc.player).offset(dir.opposite());
                    MBlockPos roofPos = roofIndex == 0
                        ? pos.offset(dir).add(0, 2, 0) // above player
                        : pos.add(0, 2, 0); // above container
                    roofIndex++;
                    return roofPos;
                }

                @Override
                public void save() {
                    pi = i;
                    py = y;
                    proofIndex = roofIndex;
                    i = y = 0;
                    roofIndex = 0;
                }

                @Override
                public void restore() {
                    i = pi;
                    y = py;
                    roofIndex = proofIndex;
                }

                @Override
                public int placementsPerTick(HighwayBuilderTHM b) {
                    return 1;
                }
            };
        }
    }

    private class DiagonalBlockPosProvider implements IBlockPosProvider {
        private final MBlockPos pos = new MBlockPos();
        private final MBlockPos pos2 = new MBlockPos();

        @Override
        public MBPIterator getFront() {
            pos.coerceBlockLevel(mc.player).offset(dir.rotateLeft()).offset(leftDir, getWidthLeft() - 1);

            return new MBPIterator() {
                private int i, w, y;
                private int pi, pw, py;

                @Override
                public boolean hasNext() {
                    return i < 2 && w < width.get() && y < height.get();
                }

                @Override
                public MBlockPos next() {
                    pos2.set(pos).offset(rightDir, w).add(0, y++, 0);

                    if (y >= height.get()) {
                        y = 0;
                        w++;

                        if (w >= (i == 0 ? width.get() - 1 : width.get())) {
                            w = 0;
                            i++;

                            pos.coerceBlockLevel(mc.player).offset(dir).offset(leftDir, getWidthLeft());
                        }
                    }

                    return pos2;
                }

                private void initPos() {
                    if (i == 0) pos.coerceBlockLevel(mc.player).offset(dir.rotateLeft()).offset(leftDir, getWidthLeft() - 1);
                    else pos.coerceBlockLevel(mc.player).offset(dir).offset(leftDir, getWidthLeft());
                }

                @Override
                public void save() {
                    pi = i;
                    pw = w;
                    py = y;
                    i = w = y = 0;

                    initPos();
                }

                @Override
                public void restore() {
                    i = pi;
                    w = pw;
                    y = py;

                    initPos();
                }
            };
        }

        @Override
        public MBPIterator getBehindFront() {
            HorizontalDirection backward = dir.opposite();
            HorizontalDirection backwardLeft = backward.rotateLeftSkipOne();
            HorizontalDirection backwardRight = backwardLeft.opposite();
            pos.coerceBlockLevel(mc.player).offset(backward.rotateLeft()).offset(backwardLeft, getWidthLeft() - 1);

            return new MBPIterator() {
                private int i, w, y;
                private int pi, pw, py;

                @Override
                public boolean hasNext() {
                    return i < 2 && w < width.get() && y < height.get();
                }

                @Override
                public MBlockPos next() {
                    pos2.set(pos).offset(backwardRight, w).add(0, y++, 0);

                    if (y >= height.get()) {
                        y = 0;
                        w++;

                        if (w >= (i == 0 ? width.get() - 1 : width.get())) {
                            w = 0;
                            i++;

                            pos.coerceBlockLevel(mc.player).offset(backward).offset(backwardLeft, getWidthLeft());
                        }
                    }

                    return pos2;
                }

                private void initPos() {
                    if (i == 0) pos.coerceBlockLevel(mc.player).offset(backward.rotateLeft()).offset(backwardLeft, getWidthLeft() - 1);
                    else pos.coerceBlockLevel(mc.player).offset(backward).offset(backwardLeft, getWidthLeft());
                }

                @Override
                public void save() {
                    pi = i;
                    pw = w;
                    py = y;
                    i = w = y = 0;

                    initPos();
                }

                @Override
                public void restore() {
                    i = pi;
                    w = pw;
                    y = py;

                    initPos();
                }
            };
        }

        @Override
        public MBPIterator getFloor() {
            pos.coerceBlockLevel(mc.player).add(0, -1, 0).offset(dir.rotateLeft()).offset(leftDir, getWidthLeft() - 1);

            return new MBPIterator() {
                private int i, w;
                private int pi, pw;

                @Override
                public boolean hasNext() {
                    return i < 2 && w < width.get();
                }

                @Override
                public MBlockPos next() {
                    pos2.set(pos).offset(rightDir, w++);

                    if (w >= (i == 0 ? width.get() - 1 : width.get())) {
                        w = 0;
                        i++;

                        pos.coerceBlockLevel(mc.player).add(0, -1, 0).offset(dir).offset(leftDir, getWidthLeft());
                    }
                    return pos2;
                }

                private void initPos() {
                    if (i == 0) pos.coerceBlockLevel(mc.player).add(0, -1, 0).offset(dir.rotateLeft()).offset(leftDir, getWidthLeft() - 1);
                    else pos.coerceBlockLevel(mc.player).add(0, -1, 0).offset(dir).offset(leftDir, getWidthLeft());
                }

                @Override
                public void save() {
                    pi = i;
                    pw = w;
                    i = w = 0;

                    initPos();
                }

                @Override
                public void restore() {
                    i = pi;
                    w = pw;

                    initPos();
                }
            };
        }

        @Override
        public MBPIterator getFloor(boolean includeBehind) {
            if (!includeBehind) return getFloor();
            return new MBPIteratorChain(getFloor(), this::getBehindFloor);
        }

        @Override
        public MBPIterator getBehindFloor() {
            HorizontalDirection backward = dir.opposite();
            HorizontalDirection backwardLeft = backward.rotateLeftSkipOne();
            HorizontalDirection backwardRight = backwardLeft.opposite();
            pos.coerceBlockLevel(mc.player).add(0, -1, 0).offset(backward.rotateLeft()).offset(backwardLeft, getWidthLeft() - 1);

            return new MBPIterator() {
                private int i, w;
                private int pi, pw;

                @Override
                public boolean hasNext() {
                    return i < 2 && w < width.get();
                }

                @Override
                public MBlockPos next() {
                    pos2.set(pos).offset(backwardRight, w++);

                    if (w >= (i == 0 ? width.get() - 1 : width.get())) {
                        w = 0;
                        i++;

                        pos.coerceBlockLevel(mc.player).add(0, -1, 0).offset(backward).offset(backwardLeft, getWidthLeft());
                    }
                    return pos2;
                }

                private void initPos() {
                    if (i == 0) pos.coerceBlockLevel(mc.player).add(0, -1, 0).offset(backward.rotateLeft()).offset(backwardLeft, getWidthLeft() - 1);
                    else pos.coerceBlockLevel(mc.player).add(0, -1, 0).offset(backward).offset(backwardLeft, getWidthLeft());
                }

                @Override
                public void save() {
                    pi = i;
                    pw = w;
                    i = w = 0;

                    initPos();
                }

                @Override
                public void restore() {
                    i = pi;
                    w = pw;

                    initPos();
                }
            };
        }

        @Override
        public MBPIterator getRailings(int state) {
            pos.coerceBlockLevel(mc.player).offset(dir.rotateLeft()).offset(leftDir, getWidthLeft());

            return new MBPIterator() {
                private int i, y = state;
                private int pi, py;

                @Override
                public boolean hasNext() {
                    return i < 2 && y < (state == 1 ? height.get() : state + 1);
                }

                @Override
                public MBlockPos next() {
                    pos2.set(pos).add(0, y++, 0);

                    if (y >= (state == 1 ? height.get() : state + 1)) {
                        y = state;
                        i++;

                        pos.coerceBlockLevel(mc.player).offset(dir.rotateRight()).offset(rightDir, getWidthRight());
                    }

                    return pos2;
                }

                private void initPos() {
                    if (i == 0) pos.coerceBlockLevel(mc.player).offset(dir.rotateLeft()).offset(leftDir, getWidthLeft());
                    else pos.coerceBlockLevel(mc.player).offset(dir.rotateRight()).offset(rightDir, getWidthRight());
                }

                @Override
                public void save() {
                    pi = i;
                    py = y;
                    i = 0;
                    y = state;

                    initPos();
                }

                @Override
                public void restore() {
                    i = pi;
                    y = py;

                    initPos();
                }
            };
        }

        @Override
        public MBPIterator getRailings(int state, boolean includeBehind) {
            if (!includeBehind) return getRailings(state);
            return new MBPIteratorChain(getRailings(state), () -> getBehindRailings(state));
        }

        @Override
        public MBPIterator getBehindRailings(int state) {
            HorizontalDirection backward = dir.opposite();
            HorizontalDirection backwardLeft = backward.rotateLeftSkipOne();
            HorizontalDirection backwardRight = backwardLeft.opposite();
            pos.coerceBlockLevel(mc.player).offset(backward.rotateLeft()).offset(backwardLeft, getWidthLeft());

            return new MBPIterator() {
                private int i, y = state;
                private int pi, py;

                @Override
                public boolean hasNext() {
                    return i < 2 && y < (state == 1 ? height.get() : state + 1);
                }

                @Override
                public MBlockPos next() {
                    pos2.set(pos).add(0, y++, 0);

                    if (y >= (state == 1 ? height.get() : state + 1)) {
                        y = state;
                        i++;

                        pos.coerceBlockLevel(mc.player).offset(backward.rotateRight()).offset(backwardRight, getWidthRight());
                    }

                    return pos2;
                }

                private void initPos() {
                    if (i == 0) pos.coerceBlockLevel(mc.player).offset(backward.rotateLeft()).offset(backwardLeft, getWidthLeft());
                    else pos.coerceBlockLevel(mc.player).offset(backward.rotateRight()).offset(backwardRight, getWidthRight());
                }

                @Override
                public void save() {
                    pi = i;
                    py = y;
                    i = 0;
                    y = state;

                    initPos();
                }

                @Override
                public void restore() {
                    i = pi;
                    y = py;

                    initPos();
                }
            };
        }

        @Override
        public MBPIterator getLiquids() {
            boolean m = mineAboveRailings.get();
            pos.coerceBlockLevel(mc.player).offset(dir).offset(dir.rotateLeft()).offset(leftDir, getWidthLeft());

            return new MBPIterator() {
                private int i, w, y;
                private int pi, pw, py;

                private int getWidth() {
                    return width.get() + (i == 0 ? 1 : 0) + (m && i == 1 ? 2 : 0);
                }

                @Override
                public boolean hasNext() {
                    if (m && i == 1 && y == height.get() &&  w == getWidth() - 1) return false;
                    return i < 2 && w < getWidth() && y < height.get() + 1;
                }

                private void updateW() {
                    w++;

                    if (w >= getWidth()) {
                        w = 0;
                        i++;

                        pos.coerceBlockLevel(mc.player).offset(dir, 2).offset(leftDir, getWidthLeft() + (m ? 1 : 0));
                    }
                }

                @Override
                public MBlockPos next() {
                    if (i == (m ? 1 : 0) && y == height.get() && (w == 0 || w == getWidth() - 1)) {
                        y = 0;
                        updateW();
                    }

                    pos2.set(pos).offset(rightDir, w).add(0, y++, 0);

                    if (y >= height.get() + 1) {
                        y = 0;
                        updateW();
                    }

                    return pos2;
                }

                private void initPos() {
                    if (i == 0) pos.coerceBlockLevel(mc.player).offset(dir).offset(dir.rotateLeft()).offset(leftDir, getWidthLeft());
                    else pos.coerceBlockLevel(mc.player).offset(dir, 2).offset(leftDir, getWidthLeft() + (m ? 1 : 0));
                }

                @Override
                public void save() {
                    pi = i;
                    pw = w;
                    py = y;
                    i = w = y = 0;

                    initPos();
                }

                @Override
                public void restore() {
                    i = pi;
                    w = pw;
                    y = py;

                    initPos();
                }
            };
        }

        @Override
        public MBPIterator getBlockade(boolean mine, BlockadeType blockadeType) {
            return new MBPIterator() {
                private int i = mine ? -1 : 0, y;
                private int roofIndex;
                private int pi, py;
                private int proofIndex;

                private MBlockPos get(int i) {
                    HorizontalDirection dir2 = dir.rotateLeft().rotateLeftSkipOne();

                    pos.coerceBlockLevel(mc.player).offset(dir2);

                    return switch (i) {
                        case -1 -> pos;
                        case 0 -> pos.offset(dir2);
                        case 1 -> pos.offset(dir2.rotateLeftSkipOne());
                        case 2 -> pos.offset(dir2.rotateLeftSkipOne().opposite());
                        case 3 -> pos.offset(dir2.opposite(), 2);
                        case 4 -> pos.offset(dir2.opposite()).offset(dir2.rotateLeftSkipOne());
                        case 5 -> pos.offset(dir2.opposite()).offset(dir2.rotateLeftSkipOne().opposite());
                        default -> throw new IllegalStateException("Unexpected value: " + i);
                    };
                }

                @Override
                public boolean hasNext() {
                    return (i < blockadeType.columns && y < 2) || (blockadeType.roof && roofIndex < 2);
                }

                @Override
                public MBlockPos next() {
                    if (i < blockadeType.columns && y < 2) {
                        MBlockPos pos = get(i).add(0, y, 0);

                        y++;
                        if (y > 1) {
                            y = 0;
                            i++;
                        }

                        return pos;
                    }

                    HorizontalDirection dir2 = dir.rotateLeft().rotateLeftSkipOne();
                    pos.coerceBlockLevel(mc.player).offset(dir2);
                    MBlockPos roofPos = roofIndex == 0
                        ? pos.offset(dir2.opposite()).add(0, 2, 0) // above player
                        : pos.add(0, 2, 0); // above container
                    roofIndex++;
                    return roofPos;
                }

                @Override
                public void save() {
                    pi = i;
                    py = y;
                    proofIndex = roofIndex;
                    i = y = 0;
                    roofIndex = 0;
                }

                @Override
                public void restore() {
                    i = pi;
                    y = py;
                    roofIndex = proofIndex;
                }

                @Override
                public int placementsPerTick(HighwayBuilderTHM b) {
                    return 1;
                }
            };
        }
    }

    public static class DoubleMineBlock {
        public static boolean rateLimited = false;
        public final BlockPos blockPos;
        public final BlockState blockState;

        private final Block block;
        private final Direction direction;
        private final HighwayBuilderTHM b;
        private final Vector3d vec3 = new Vector3d(0);

        private int normalStartTime, packetStartTime;
        private boolean packet;

        public DoubleMineBlock(HighwayBuilderTHM b, BlockPos pos) {
            this.b = b;
            this.blockPos = pos;
            this.blockState = b.mc.world.getBlockState(this.blockPos);
            this.block = this.blockState.getBlock();
            this.direction = RangeUtils.nearestFace(pos);
            this.packet = false;
        }

        public DoubleMineBlock startDestroying() {
            b.createOrRefreshForwardBreakCreditIfEligible(this.blockPos, this.blockState);
            b.mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, this.blockPos, this.direction));
            normalStartTime = b.mc.player.age;
            return this;
        }

        public DoubleMineBlock stopDestroying() {
            b.mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, this.blockPos, this.direction));
            return this;
        }

        public DoubleMineBlock packetMine() {
            b.createOrRefreshForwardBreakCreditIfEligible(this.blockPos, this.blockState);
            packetStartTime = b.mc.player.age;
            packet = true;
            return stopDestroying();
        }

        public boolean isReady() {
            return progress() >= (b.fastBreak.get() ? 0.7 : 1.0);
        }

        public boolean shouldRemove() {
            boolean distance = !packet && !RangeUtils.isInReach(blockPos);

            // a minimum amount of time needs to have elapsed for the timeout check to occur, otherwise it may trigger
            // when it isn't supposed to due to latency
            boolean timeout = progress() > 2 && (b.mc.player.age - (packet ? packetStartTime : normalStartTime) > 60);

            return distance || timeout;
        }

        public double progress() {
            int slot = b.mc.player.getInventory().getSelectedSlot();
            return BlockUtils.getBreakDelta(slot , blockState) * ((b.mc.player.age - (packet ? packetStartTime : normalStartTime)) + 1);
        }

        public void renderLetter() {
            vec3.set(blockPos.getX() + 0.5, blockPos.getY() + 0.5, blockPos.getZ() + 0.5);
            if (!NametagUtils.to2D(vec3, 2)) return;

            NametagUtils.begin(vec3);
            TextRenderer.get().begin(1.0, false, true);

            String letter = packet ? "P" : "N";
            double w = TextRenderer.get().getWidth(letter) / 2.0;
            TextRenderer.get().render(letter, -w, 0.0, Color.WHITE, true);

            TextRenderer.get().end();
            NametagUtils.end();
        }
    }

    private class RestockTask {
        private static final int BLOCKADE_TEARDOWN_DELAY_TICKS = 20;
        public boolean materials;
        public boolean pickaxes;
        public boolean food;
        public boolean enderChests;
        private boolean pendingMaterials;
        private boolean pendingPickaxes;
        private boolean pendingFood;
        private boolean pendingEnderChests;
        private boolean sequenceActive;
        private boolean blockadeReady;
        private boolean blockadeTeardownPending;
        private int blockadeTeardownReadyAtAge;
        private int pickaxeStartCount;
        private long blockadeLeaseGenerationCounter;
        private final BlockadeLease blockadeLease = new BlockadeLease();
        private RestockSession session;
        private final Deque<Type> pendingQueue = new ArrayDeque<>();
        private String lastDuplicateQueuedLogKey = "";
        private int lastDuplicateQueuedLogAge = -RESTOCK_DUPLICATE_QUEUE_LOG_INTERVAL_TICKS;
        private boolean obsidianPickaxePreflightTopoffPending;
        private boolean obsidianPickaxePreflightTopoffActive;
        private boolean obsidianPickaxePreflightSatisfied;
        private int obsidianPickaxePreflightTopoffBaseline;
        private int obsidianPickaxePreflightEChestsToBreak;
        private String obsidianPickaxePreflightReason = "";
        private final HighwayBuilderTHM b;

        private enum Type {
            Materials,
            Pickaxes,
            Food,
            EnderChests
        }

        private enum SourcePhase {
            Inventory,
            InventoryShulkers,
            EnderChest,
            MineEnderChests,
            Kitbot,
            Complete,
            Failed
        }

        private enum SourceAttemptResult {
            NO_PROGRESS,
            PARTIAL_PROGRESS,
            TARGET_SATISFIED,
            SOURCE_EXHAUSTED
        }

        private enum BlockadeLeaseState {
            NOT_BUILT,
            BUILT,
            INVALIDATED,
            TEARDOWN_PENDING
        }

        private final class BlockadeLease {
            private long generationId;
            private final BlockPos.Mutable anchorPos = new BlockPos.Mutable();
            private BlockPos sourceContainerPos;
            private BlockadeType blockadeType = BlockadeType.FullRoof;
            private BlockadeLeaseState state = BlockadeLeaseState.NOT_BUILT;
            private long lastValidatedTick;

            private void markBuilt() {
                generationId = ++blockadeLeaseGenerationCounter;
                blockadeType = b.getEffectiveBlockadeType();
                sourceContainerPos = null;
                if (b.mc.player != null) {
                    anchorPos.set(b.mc.player.getBlockPos());
                    if (b.dir != null) {
                        HorizontalDirection containerDirection = b.getRestockContainerDirection(b.dir);
                        HorizontalDirection expansionDirection = b.getKitbotExpansionDirection(containerDirection);
                        sourceContainerPos = b.getBlockadeBasis(anchorPos.toImmutable(), b.dir, containerDirection, expansionDirection).container.toImmutable();
                    }
                }
                state = BlockadeLeaseState.BUILT;
                lastValidatedTick = b.mc.player != null ? b.mc.player.age : 0L;
            }

            private void markTeardownPending() {
                state = BlockadeLeaseState.TEARDOWN_PENDING;
                lastValidatedTick = b.mc.player != null ? b.mc.player.age : lastValidatedTick;
            }

            private void invalidate() {
                state = BlockadeLeaseState.INVALIDATED;
                sourceContainerPos = null;
                lastValidatedTick = b.mc.player != null ? b.mc.player.age : lastValidatedTick;
            }

            private void reset() {
                generationId = 0L;
                anchorPos.set(0, 0, 0);
                sourceContainerPos = null;
                blockadeType = BlockadeType.FullRoof;
                state = BlockadeLeaseState.NOT_BUILT;
                lastValidatedTick = 0L;
            }

            private boolean validateCurrentAnchor() {
                if (state != BlockadeLeaseState.BUILT) return false;
                if (b.mc.player == null || b.mc.world == null) return false;
                if (sourceContainerPos == null || b.dir == null) {
                    invalidate();
                    return false;
                }
                HorizontalDirection containerDirection = b.getRestockContainerDirection(b.dir);
                HorizontalDirection expansionDirection = b.getKitbotExpansionDirection(containerDirection);
                BlockPos expectedSourcePos = b.getBlockadeBasis(anchorPos.toImmutable(), b.dir, containerDirection, expansionDirection).container;
                if (!sourceContainerPos.equals(expectedSourcePos)) {
                    invalidate();
                    return false;
                }
                BlockPos playerPos = b.mc.player.getBlockPos();
                if (Math.abs(playerPos.getY() - anchorPos.getY()) > 3) {
                    invalidate();
                    return false;
                }

                // The blockade is always centered adjacent to the player during restock.
                boolean nearAnchor = Math.abs(playerPos.getX() - anchorPos.getX()) <= 6
                    && Math.abs(playerPos.getZ() - anchorPos.getZ()) <= 6;
                if (!nearAnchor) {
                    invalidate();
                    return false;
                }

                lastValidatedTick = b.mc.player.age;
                return true;
            }

            private BlockPos sourceContainerPos() {
                return state == BlockadeLeaseState.BUILT && sourceContainerPos != null
                    ? sourceContainerPos.toImmutable()
                    : null;
            }
        }

        private final class RestockSession {
            private final Type taskType;
            private SourcePhase phase;
            private SourceAttemptResult lastResult;
            private boolean usingGreatestAvailable;
            private boolean targetFrozen;
            private boolean targetInitialized;
            private boolean inventoryShulkersExhausted;
            private boolean enderChestExhausted;
            private boolean mineEnderChestsExhausted;
            private boolean madeInventoryShulkerForwardProgressThisSession;
            private boolean madeEnderChestForwardProgressThisSession;
            private boolean triedInventoryLocal;
            private boolean triedEnderChestBank;
            private boolean triedKitbot;
            private boolean obsidianPickaxePreflightQueued;
            private boolean enderChestExhaustedSnapshotValid;
            private boolean enderChestExhaustedMemoryKnown;
            private boolean enderChestExhaustedAccessAvailable;
            private boolean enderChestForcedLiveRecheckUsed;
            private int targetFinal;
            private int remainingTarget;
            private int workingStageCapacity;
            private int enderChestExhaustedRawMatches;
            private int enderChestExhaustedMatchingShulkers;
            private int enderChestExhaustedAtAge;
            private int pickaxesStartCount;
            private int eligiblePickaxeStartCount;
            private int materialStartStacks;
            private int foodStartItems;
            private int enderChestStartItems;
            private int obsidianStartItems;
            private int materialStartItems;
            private int pickaxesAcquiredCount;
            private int eligiblePickaxesAcquiredCount;
            private int materialStacksAcquired;
            private int materialItemsAcquired;
            private int foodItemsAcquired;
            private int enderChestItemsAcquired;
            private int obsidianItemsAcquired;
            private int saveEchestsReserve;
            private int reserveRemaining;
            private int usablePulledEchests;

            private RestockSession(Type taskType) {
                this.taskType = taskType;
                this.phase = SourcePhase.Inventory;
                this.lastResult = SourceAttemptResult.NO_PROGRESS;
                this.saveEchestsReserve = b.saveEchests.get();
                refreshBaselines();
                refreshProgress();
            }

            private void refreshBaselines() {
                pickaxesStartCount = countInventoryItems(itemStack -> itemStack.isIn(ItemTags.PICKAXES));
                eligiblePickaxeStartCount = isObsidianPickaxePreflightTopoffTask()
                    ? obsidianPickaxePreflightTopoffBaseline
                    : b.countLooseEligibleEChestMiningPickaxes();
                materialStartStacks = countInventorySlots(this::isTrackedMaterialStack);
                materialStartItems = countInventoryItems(this::isTrackedMaterialStack);
                foodStartItems = b.countConfiguredFoodItemsInInventory();
                enderChestStartItems = countInventoryItems(itemStack -> itemStack.getItem() == Items.ENDER_CHEST);
                obsidianStartItems = countInventoryItems(itemStack -> itemStack.getItem() == Items.OBSIDIAN);
            }

            private int getLooseInventoryPickaxeReserveSlots() {
                return Math.max((b.savePickaxes.get() + 1) - countInventoryItems(itemStack -> itemStack.isIn(ItemTags.PICKAXES)), 0);
            }

            private int getBaseUsableFreeSlots() {
                return Math.max(countEmptyInventorySlots() - b.minEmpty.get(), 0);
            }

            private int getPickaxeUsableFreeSlots() {
                int effectiveMinEmpty = Math.max(b.minEmpty.get() - 2, 0);
                return Math.max(countEmptyInventorySlots() - effectiveMinEmpty, 0);
            }

            private int getUsableFreeSlotsForCurrentTask() {
                if (taskType == Type.Pickaxes) return getPickaxeUsableFreeSlots();

                int usableFreeSlots = getBaseUsableFreeSlots();
                if (!isObsidianTask()) return usableFreeSlots;
                return Math.max(usableFreeSlots - getLooseInventoryPickaxeReserveSlots(), 0);
            }

            private int getLooseObsidianTopOffGoal() {
                int topOff = 0;
                for (int i = 0; i < b.mc.player.getInventory().getMainStacks().size(); i++) {
                    ItemStack stack = b.mc.player.getInventory().getStack(i);
                    if (stack.getItem() == Items.OBSIDIAN && stack.getCount() > 0 && stack.getCount() < stack.getMaxCount()) {
                        topOff += stack.getMaxCount() - stack.getCount();
                    }
                }
                return topOff;
            }

            private int getMissingFoodSupportSlot() {
                boolean foodRestockingEnabled = b.foodRestock.get() && b.hasConfiguredFoodTypes();
                if (!foodRestockingEnabled) return 0;
                return b.countConfiguredFoodItemsInInventory() == 0 ? 1 : 0;
            }

            private int getMissingEChestSupportSlot() {
                boolean protectedEChestSlotWanted = b.saveEchests.get() > 0 || b.searchEnderChest.get() || b.mineEnderChests.get();
                if (!protectedEChestSlotWanted) return 0;
                return countInventoryItems(itemStack -> itemStack.getItem() == Items.ENDER_CHEST) == 0 ? 1 : 0;
            }

            private int getObsidianRestockTargetItems() {
                int reservedEmptySlots = getLooseInventoryPickaxeReserveSlots() + getMissingFoodSupportSlot() + getMissingEChestSupportSlot();
                int emptySlotObbyGoal = Math.max(countEmptyInventorySlots() - b.minEmpty.get() - reservedEmptySlots, 0) * 64;
                return getLooseObsidianTopOffGoal() + emptySlotObbyGoal;
            }

            private boolean freezeTargetForSourceSelection() {
                if (targetFrozen) return targetFinal > 0;
                if (b.mc.player == null) return false;

                int usableFreeSlots = getUsableFreeSlotsForCurrentTask();
                int looseFoodCount = b.countConfiguredFoodItemsInInventory();
                int foodTargetIncrease = b.getConfiguredFoodTargetIncrease(looseFoodCount);
                int foodAdditionalCapacity = b.countConfiguredFoodAdditionalCapacityInInventory();
                workingStageCapacity = taskType == Type.Food ? foodAdditionalCapacity : usableFreeSlots;

                targetFinal = switch (taskType) {
                    case Pickaxes -> isObsidianPickaxePreflightTopoffTask()
                        ? Math.min(usableFreeSlots, 1)
                        : Math.min(usableFreeSlots, b.restockPickaxesAmount.get());
                    case Materials -> isObsidianTask() ? getObsidianRestockTargetItems() : usableFreeSlots;
                    case Food -> foodTargetIncrease;
                    case EnderChests -> 1;
                };

                if (taskType == Type.Food && (targetFinal <= 0 || foodAdditionalCapacity < targetFinal)) {
                    targetFrozen = true;
                    remainingTarget = Math.max(targetFinal, 0);
                    targetInitialized = false;
                    refreshProgress();
                    if (b.restockDebugLog.get()) {
                        b.restockDebug("RestockSession found no usable food target (looseFood=%d, targetFinal=%d, additionalCapacity=%d, threshold=%d).",
                            looseFoodCount,
                            targetFinal,
                            foodAdditionalCapacity,
                            b.saveFood.get()
                        );
                    }
                    return false;
                }

                targetFrozen = true;
                remainingTarget = Math.max(targetFinal, 0);
                targetInitialized = targetFinal > 0;
                refreshProgress();
                if (b.restockDebugLog.get()) {
                    b.restockDebug("RestockSession froze target for task=%s (targetFinal=%d, usableFreeSlots=%d, reserveRemaining=%d).",
                        item(taskType),
                        targetFinal,
                        usableFreeSlots,
                        reserveRemaining
                    );
                }
                return targetFinal > 0;
            }

            private void refreshProgress() {
                if (b.mc.player == null) return;

                int beforeProgress = getProgressTowardsTarget();
                int beforeUsablePulledEchests = usablePulledEchests;
                SourcePhase beforePhase = phase;

                workingStageCapacity = taskType == Type.Food
                    ? b.countConfiguredFoodAdditionalCapacityInInventory()
                    : getUsableFreeSlotsForCurrentTask();
                pickaxesAcquiredCount = Math.max(countInventoryItems(itemStack -> itemStack.isIn(ItemTags.PICKAXES)) - pickaxesStartCount, 0);
                eligiblePickaxesAcquiredCount = Math.max(b.countLooseEligibleEChestMiningPickaxes() - eligiblePickaxeStartCount, 0);
                materialStacksAcquired = Math.max(countInventorySlots(this::isTrackedMaterialStack) - materialStartStacks, 0);
                materialItemsAcquired = Math.max(countInventoryItems(this::isTrackedMaterialStack) - materialStartItems, 0);
                foodItemsAcquired = Math.max(b.countConfiguredFoodItemsInInventory() - foodStartItems, 0);
                enderChestItemsAcquired = Math.max(countInventoryItems(itemStack -> itemStack.getItem() == Items.ENDER_CHEST) - enderChestStartItems, 0);
                obsidianItemsAcquired = Math.max(countInventoryItems(itemStack -> itemStack.getItem() == Items.OBSIDIAN) - obsidianStartItems, 0);

                int looseEchestsInInventory = countInventoryItems(itemStack -> itemStack.getItem() == Items.ENDER_CHEST);
                reserveRemaining = Math.max(saveEchestsReserve - looseEchestsInInventory, 0);
                usablePulledEchests = Math.max(looseEchestsInInventory - saveEchestsReserve, 0);

                remainingTarget = targetFrozen ? Math.max(targetFinal - getProgressTowardsTarget(), 0) : 0;
                if (isTargetSatisfied()) phase = SourcePhase.Complete;

                if (getProgressTowardsTarget() > beforeProgress
                    || usablePulledEchests > beforeUsablePulledEchests
                    || phase != beforePhase) {
                    b.restockWatchdog.markProgress("session-progress");
                }
            }

            private boolean recalculateFrozenCapacityAfterKitbotDelivery() {
                if (!targetFrozen || b.mc.player == null) return false;

                refreshProgress();

                int oldTargetFinal = targetFinal;
                int currentProgress = getProgressTowardsTarget();
                int usableFreeSlots = getUsableFreeSlotsForCurrentTask();
                int currentAdditionalCapacity = switch (taskType) {
                    case Pickaxes -> usableFreeSlots;
                    case Materials -> isObsidianTask() ? getObsidianRestockTargetItems() : usableFreeSlots;
                    case Food -> b.countConfiguredFoodAdditionalCapacityInInventory();
                    case EnderChests -> usableFreeSlots > 0 ? 1 : 0;
                };

                workingStageCapacity = taskType == Type.Food ? currentAdditionalCapacity : usableFreeSlots;
                if (taskType == Type.Food) {
                    boolean usable = oldTargetFinal > 0 && currentProgress + currentAdditionalCapacity >= oldTargetFinal;
                    targetFinal = oldTargetFinal;
                    targetInitialized = targetFinal > 0;
                    remainingTarget = Math.max(targetFinal - currentProgress, 0);

                    if (b.restockDebugLog.get()) {
                        b.restockDebug("RestockSession recalculated frozen target after Kitbot delivery for task=%s (oldTarget=%d, newTarget=%d, progress=%d, usableFreeSlots=%d, additionalCapacity=%d, usable=%s).",
                            item(taskType),
                            oldTargetFinal,
                            targetFinal,
                            currentProgress,
                            usableFreeSlots,
                            currentAdditionalCapacity,
                            usable
                        );
                    }

                    return usable;
                }

                targetFinal = Math.max(Math.min(oldTargetFinal, currentProgress + currentAdditionalCapacity), 0);
                targetInitialized = targetFinal > 0;
                remainingTarget = Math.max(targetFinal - currentProgress, 0);

                if (b.restockDebugLog.get()) {
                    b.restockDebug("RestockSession recalculated frozen target after Kitbot delivery for task=%s (oldTarget=%d, newTarget=%d, progress=%d, usableFreeSlots=%d, additionalCapacity=%d, usable=%s).",
                        item(taskType),
                        oldTargetFinal,
                        targetFinal,
                        currentProgress,
                        usableFreeSlots,
                        currentAdditionalCapacity,
                        targetFinal > 0
                    );
                }

                return targetFinal > 0;
            }

            private void notePhase(SourcePhase phase) {
                SourcePhase previousPhase = this.phase;
                this.phase = phase;
                if (previousPhase != phase) b.restockWatchdog.markProgress("phase:" + phase);
                if (b.restockDebugLog.get()) {
                    b.restockDebug("RestockSession phase=%s task=%s target=%d remaining=%d greatest=%s usableSlots=%d reserveRemaining=%d usableEchests=%d.",
                        phase,
                        item(taskType),
                        targetFinal,
                        remainingTarget,
                        usingGreatestAvailable,
                        workingStageCapacity,
                        reserveRemaining,
                        usablePulledEchests
                    );
                }
            }

            private boolean isObsidianTask() {
                return taskType == Type.Materials && b.blocksToPlace.get().contains(Blocks.OBSIDIAN);
            }

            private boolean isObsidianPickaxePreflightTopoffTask() {
                return taskType == Type.Pickaxes && obsidianPickaxePreflightTopoffActive;
            }

            private int getProgressTowardsTarget() {
                return switch (taskType) {
                    case Pickaxes -> isObsidianPickaxePreflightTopoffTask() ? eligiblePickaxesAcquiredCount : pickaxesAcquiredCount;
                    case Materials -> isObsidianTask() ? obsidianItemsAcquired : materialStacksAcquired;
                    case Food -> foodItemsAcquired;
                    case EnderChests -> enderChestItemsAcquired;
                };
            }

            private int getUsablePulledEchests() {
                return usablePulledEchests;
            }

            private String watchdogSummary() {
                return "phase=" + phase
                    + ",target=" + targetFinal
                    + ",remaining=" + remainingTarget
                    + ",progress=" + getProgressTowardsTarget()
                    + ",usableEchests=" + usablePulledEchests
                    + ",inventoryExhausted=" + inventoryShulkersExhausted
                    + ",enderChestExhausted=" + enderChestExhausted
                    + ",mineEchestsExhausted=" + mineEnderChestsExhausted
                    + ",triedInventory=" + triedInventoryLocal
                    + ",triedEnderChest=" + triedEnderChestBank
                    + ",triedKitbot=" + triedKitbot
                    + ",greatest=" + usingGreatestAvailable;
            }

            private int getRemainingTarget() {
                return Math.max(remainingTarget, 0);
            }

            private boolean isTargetSatisfied() {
                return targetInitialized && targetFinal > 0 && getProgressTowardsTarget() >= targetFinal;
            }

            private boolean hasUsefulProgress() {
                return switch (taskType) {
                    case Pickaxes -> isObsidianPickaxePreflightTopoffTask() ? eligiblePickaxesAcquiredCount > 0 : pickaxesAcquiredCount > 0;
                    case Materials -> isObsidianTask() ? obsidianItemsAcquired > 0 : materialItemsAcquired > 0;
                    case Food -> foodItemsAcquired > 0;
                    case EnderChests -> enderChestItemsAcquired > 0;
                };
            }

            private boolean isTriggerThresholdCleared() {
                return switch (taskType) {
                    case Pickaxes -> !isObsidianPickaxePreflightTopoffTask()
                        && countInventoryItems(itemStack -> itemStack.isIn(ItemTags.PICKAXES)) > b.savePickaxes.get();
                    case Materials -> false;
                    case Food -> b.countConfiguredFoodItemsInInventory() > b.saveFood.get();
                    case EnderChests -> countInventoryItems(itemStack -> itemStack.getItem() == Items.ENDER_CHEST) > b.saveEchests.get() - 1;
                };
            }

            private boolean isTaskSuccessful() {
                refreshProgress();
                if (isObsidianPickaxePreflightTopoffTask()) return isTargetSatisfied();
                if (isObsidianTask()) return isTargetSatisfied() || isTriggerThresholdCleared();
                return isTargetSatisfied() || hasUsefulProgress() || isTriggerThresholdCleared();
            }

            private boolean isTaskSuccessfulAtFinalNoSource() {
                refreshProgress();
                if (isObsidianPickaxePreflightTopoffTask()) return isTaskSuccessful();
                if (isObsidianTask()) return isTargetSatisfied() || obsidianItemsAcquired > 0 || isTriggerThresholdCleared();
                return isTaskSuccessful();
            }

            private boolean shouldCompleteAfterInventoryShulkers() {
                if (isObsidianPickaxePreflightTopoffTask()) return isTaskSuccessful();
                if (isObsidianTask()) return isTargetSatisfied();
                return isTaskSuccessful();
            }

            private boolean canTransitionToMineEnderChests() {
                if (!isObsidianTask()) return false;
                return usablePulledEchests > 0
                    && remainingTarget > 0
                    && getTargetEchestsToBreak() > 0
                    && getRemainingRawEchestsNeeded() == 0;
            }

            private int getRemainingObsidianItems() {
                return Math.max(targetFinal - obsidianItemsAcquired, 0);
            }

            private int getRemainingRawEchestsNeeded() {
                if (!isObsidianTask()) return 0;
                int remainingObsidianItems = getRemainingObsidianItems();
                int usableLooseEchests = Math.max(countInventoryItems(itemStack -> itemStack.getItem() == Items.ENDER_CHEST) - saveEchestsReserve, 0);
                int remainingAfterLooseEchests = Math.max(remainingObsidianItems - (usableLooseEchests * 8), 0);
                return (remainingAfterLooseEchests + 7) / 8;
            }

            private boolean shrinkObsidianTargetToCurrentRawEChestBatch(String reason) {
                if (!isObsidianTask() || !targetFrozen || b.mc.player == null) return false;

                refreshProgress();
                int originalRemainingObsidian = getRemainingObsidianItems();
                if (originalRemainingObsidian <= 0) return false;

                int exactEchestsNeeded = (originalRemainingObsidian + 7) / 8;
                int usableLooseEchests = Math.max(countInventoryItems(itemStack -> itemStack.getItem() == Items.ENDER_CHEST) - saveEchestsReserve, 0);
                int rawShortage = Math.max(exactEchestsNeeded - usableLooseEchests, 0);
                int pullBudget = rawShortage <= 0 ? 0 : Math.max(rawShortage / 64, 1) * 64;
                int batchEchests = Math.min(exactEchestsNeeded, usableLooseEchests + pullBudget);
                if (batchEchests <= 0) return false;

                int oldTarget = targetFinal;
                int oldRemaining = remainingTarget;
                int batchRemainingObsidian = Math.min(originalRemainingObsidian, batchEchests * 8);
                targetFinal = obsidianItemsAcquired + batchRemainingObsidian;
                targetInitialized = targetFinal > 0;
                remainingTarget = Math.max(targetFinal - obsidianItemsAcquired, 0);

                if (b.restockDebugLog.get()) {
                    b.restockDebug(
                        "RestockSession raw e-chest batch target %s (reason=%s, oldTarget=%d, newTarget=%d, oldRemaining=%d, newRemaining=%d, progress=%d, exactEchestsNeeded=%d, usableLooseEchests=%d, rawShortage=%d, pullBudget=%d, batchEchests=%d).",
                        targetFinal < oldTarget ? "shrunk" : "kept",
                        reason,
                        oldTarget,
                        targetFinal,
                        oldRemaining,
                        remainingTarget,
                        obsidianItemsAcquired,
                        exactEchestsNeeded,
                        usableLooseEchests,
                        rawShortage,
                        pullBudget,
                        batchEchests
                    );
                }

                return true;
            }

            private boolean clampObsidianTargetToMineableEChests(String reason) {
                if (!isObsidianTask() || !targetFrozen || b.mc.player == null) return false;

                refreshProgress();
                int remainingObsidianItems = getRemainingObsidianItems();
                if (remainingObsidianItems <= 0) return false;

                int echestsToBreak = getTargetEchestsToBreak();
                if (echestsToBreak <= 0) return false;

                int oldTarget = targetFinal;
                int oldRemaining = remainingTarget;
                int mineableObsidianItems = Math.min(remainingObsidianItems, echestsToBreak * 8);
                targetFinal = obsidianItemsAcquired + mineableObsidianItems;
                targetInitialized = targetFinal > 0;
                remainingTarget = Math.max(targetFinal - obsidianItemsAcquired, 0);

                if (b.restockDebugLog.get()) {
                    b.restockDebug(
                        "RestockSession mine e-chest target %s (reason=%s, oldTarget=%d, newTarget=%d, oldRemaining=%d, newRemaining=%d, progress=%d, usableEchests=%d, echestsToBreak=%d).",
                        targetFinal < oldTarget ? "clamped" : "kept",
                        reason,
                        oldTarget,
                        targetFinal,
                        oldRemaining,
                        remainingTarget,
                        obsidianItemsAcquired,
                        usablePulledEchests,
                        echestsToBreak
                    );
                }

                return true;
            }

            private int getMiningGoalObsidianCount() {
                int achievable = usablePulledEchests * 8;
                if (achievable <= 0) return obsidianStartItems + obsidianItemsAcquired;
                if (usingGreatestAvailable) return obsidianStartItems + obsidianItemsAcquired + achievable;
                return obsidianStartItems + obsidianItemsAcquired + Math.min(remainingTarget, achievable);
            }

            private int getTargetEchestsToBreak() {
                int achievable = usablePulledEchests;
                if (achievable <= 0) return 0;
                if (usingGreatestAvailable) return achievable;
                return Math.min((remainingTarget + 7) / 8, achievable);
            }

            private void markSourceExhausted(SourcePhase phase) {
                lastResult = SourceAttemptResult.SOURCE_EXHAUSTED;
                switch (phase) {
                    case InventoryShulkers -> {
                        inventoryShulkersExhausted = true;
                        triedInventoryLocal = true;
                    }
                    case EnderChest -> {
                        enderChestExhausted = true;
                        triedEnderChestBank = true;
                    }
                    case MineEnderChests -> mineEnderChestsExhausted = true;
                    default -> { }
                }
            }

            private void markEnderChestExhaustedAfterLiveCheck(boolean memoryKnown, int rawMatches, int matchingShulkers, boolean accessAvailable) {
                boolean sameSnapshot = enderChestExhaustedSnapshotValid
                    && enderChestExhaustedMemoryKnown == memoryKnown
                    && enderChestExhaustedRawMatches == rawMatches
                    && enderChestExhaustedMatchingShulkers == matchingShulkers
                    && enderChestExhaustedAccessAvailable == accessAvailable;
                boolean preserveForcedRecheckUsed = sameSnapshot && enderChestForcedLiveRecheckUsed;

                enderChestExhausted = true;
                enderChestExhaustedSnapshotValid = true;
                enderChestExhaustedMemoryKnown = memoryKnown;
                enderChestExhaustedRawMatches = rawMatches;
                enderChestExhaustedMatchingShulkers = matchingShulkers;
                enderChestExhaustedAccessAvailable = accessAvailable;
                enderChestExhaustedAtAge = b.mc.player != null ? b.mc.player.age : 0;
                enderChestForcedLiveRecheckUsed = preserveForcedRecheckUsed;
                triedEnderChestBank = true;
                lastResult = SourceAttemptResult.SOURCE_EXHAUSTED;
            }

            private void reopenSourcePhase(SourcePhase phase) {
                switch (phase) {
                    case InventoryShulkers -> inventoryShulkersExhausted = false;
                    case EnderChest -> enderChestExhausted = false;
                    case MineEnderChests -> mineEnderChestsExhausted = false;
                    default -> { }
                }

                if (lastResult == SourceAttemptResult.SOURCE_EXHAUSTED) {
                    lastResult = SourceAttemptResult.PARTIAL_PROGRESS;
                }
            }

            private boolean shouldReopenEnderChestAfterMeaningfulChange(boolean memoryKnown, int rawMatches, int matchingShulkers, boolean accessAvailable) {
                if (!enderChestExhausted) return false;
                if (!enderChestExhaustedSnapshotValid) return false;
                if (enderChestExhaustedMemoryKnown && !memoryKnown) return true;
                if (rawMatches > enderChestExhaustedRawMatches) return true;
                if (matchingShulkers > enderChestExhaustedMatchingShulkers) return true;
                return accessAvailable && !enderChestExhaustedAccessAvailable;
            }

            private void reopenEnderChestAfterMeaningfulChange() {
                enderChestExhausted = false;
                enderChestExhaustedSnapshotValid = false;
                enderChestExhaustedMemoryKnown = false;
                enderChestExhaustedAccessAvailable = false;
                enderChestForcedLiveRecheckUsed = false;
                enderChestExhaustedRawMatches = 0;
                enderChestExhaustedMatchingShulkers = 0;
                enderChestExhaustedAtAge = 0;
                if (lastResult == SourceAttemptResult.SOURCE_EXHAUSTED) {
                    lastResult = SourceAttemptResult.PARTIAL_PROGRESS;
                }
            }

            private boolean shouldForceDelayedEnderChestLiveRecheck(boolean accessAvailable, int cooldownTicks) {
                if (!enderChestExhausted) return false;
                if (!enderChestExhaustedSnapshotValid) return false;
                if (enderChestForcedLiveRecheckUsed) return false;
                if (!accessAvailable) return false;
                if (isTargetSatisfied()) return false;
                int currentAge = b.mc.player != null ? b.mc.player.age : 0;
                return currentAge - enderChestExhaustedAtAge >= cooldownTicks;
            }

            private void prepareForcedEnderChestLiveRecheck() {
                enderChestExhausted = false;
                enderChestForcedLiveRecheckUsed = true;
                if (lastResult == SourceAttemptResult.SOURCE_EXHAUSTED) {
                    lastResult = SourceAttemptResult.PARTIAL_PROGRESS;
                }
            }

            private boolean isInventoryShulkersExhausted() {
                return inventoryShulkersExhausted;
            }

            private boolean isEnderChestExhausted() {
                return enderChestExhausted;
            }

            private boolean isMineEnderChestsExhausted() {
                return mineEnderChestsExhausted;
            }

            private void markGreatestAvailable() {
                usingGreatestAvailable = true;
            }

            private void fail() {
                phase = SourcePhase.Failed;
            }

            private void markInventoryLocalTried() {
                triedInventoryLocal = true;
            }

            private void markEnderChestBankTried() {
                triedEnderChestBank = true;
            }

            private void markKitbotTried() {
                triedKitbot = true;
            }

            private boolean hasTriedEnderChestBank() {
                return triedEnderChestBank;
            }

            private boolean hasTriedKitbot() {
                return triedKitbot;
            }

            private boolean hasQueuedObsidianPickaxePreflight() {
                return obsidianPickaxePreflightQueued;
            }

            private void markObsidianPickaxePreflightQueued() {
                obsidianPickaxePreflightQueued = true;
            }

            private boolean shouldAttemptKitbot() {
                return !isTaskSuccessful() && !triedKitbot;
            }

            private boolean shouldAttemptKitbotAfterMineEnderChests() {
                return !isTaskSuccessful() && !triedKitbot;
            }

            private void noteSuccessfulLocalSourceAction(SourcePhase sourcePhase) {
                switch (sourcePhase) {
                    case InventoryShulkers -> madeInventoryShulkerForwardProgressThisSession = true;
                    case EnderChest -> madeEnderChestForwardProgressThisSession = true;
                    default -> { }
                }
            }

            private void noteSourceForwardProgress(SourcePhase sourcePhase, int beforeProgress, int beforeUsablePulledEchests) {
                refreshProgress();

                boolean progressed = getProgressTowardsTarget() > beforeProgress
                    || (isObsidianTask() && usablePulledEchests > beforeUsablePulledEchests);
                if (!progressed) return;

                noteSuccessfulLocalSourceAction(sourcePhase);
            }

            private void noteUsefulEnderChestShulkerExtraction() {
                noteSuccessfulLocalSourceAction(SourcePhase.EnderChest);
            }

            private boolean needsMoreRawEchests() {
                return getRemainingRawEchestsNeeded() > 0;
            }

            private boolean isTrackedMaterialStack(ItemStack stack) {
                if (!(stack.getItem() instanceof BlockItem bi)) return false;
                if (isObsidianTask()) return bi.getBlock() == Blocks.OBSIDIAN;
                return b.blocksToPlace.get().contains(bi.getBlock()) && bi.getBlock() != Blocks.OBSIDIAN;
            }

        }

        public RestockTask(HighwayBuilderTHM b) {
            this.b = b;
        }

        public void setMaterials() {
            setTask(Type.Materials);
        }

        public void setPickaxes() {
            setTask(Type.Pickaxes);
        }

        public void setFood() {
            setTask(Type.Food);
        }

        public void setEnderChests() {
            setTask(Type.EnderChests);
        }

        private void setTask(Type type) {
            // ponytail: creative players carry nothing, so every restock trigger fires on the first tick and
            // then waits forever for shulkers/e-chests that don't exist. They conjure supplies instead (see
            // giveCreativePlacementBlock / the creative early-out in findAndMoveBestToolToHotbar).
            if (b.mc.player != null && b.mc.player.isCreative()) return;

            if (isActive(type)) return;

            if (!sequenceActive) {
                startSequence(type);
                return;
            }

            if (enqueue(type)) {
                b.info("Queued follow-up restock task for " + item(type) + ".");
                if (b.restockDebugLog.get()) {
                    b.restockDebug("RestockTask queued follow-up task=%s active=%s pending=%s sequence=%s.",
                        item(type),
                        activeSummary(),
                        pendingSummary(),
                        sequenceActive
                    );
                }
            } else if (sequenceActive && shouldLogDuplicateQueuedTask(type)) {
                b.restockDebug("RestockTask ignored duplicate queued task=%s active=%s pending=%s.",
                    item(type),
                    activeSummary(),
                    pendingSummary()
                );
            }
        }

        public void complete() {
            materials = false;
            pickaxes = false;
            food = false;
            enderChests = false;
            clearPending();
            sequenceActive = false;
            blockadeReady = false;
            blockadeTeardownPending = false;
            blockadeTeardownReadyAtAge = 0;
            blockadeLease.reset();
            session = null;
            clearObsidianPickaxePreflightTopoff();
        }

        public void completeActive() {
            if (obsidianPickaxePreflightTopoffActive && session != null && session.isTaskSuccessful()) {
                obsidianPickaxePreflightSatisfied = true;
                if (b.restockDebugLog.get()) {
                    b.restockDebug("RestockTask completed forced obsidian pickaxe preflight topoff (baseline=%d currentEligible=%d eChestsToBreak=%d reason=%s).",
                        obsidianPickaxePreflightTopoffBaseline,
                        b.countLooseEligibleEChestMiningPickaxes(),
                        obsidianPickaxePreflightEChestsToBreak,
                        obsidianPickaxePreflightReason
                    );
                }
            }

            obsidianPickaxePreflightTopoffActive = false;
            materials = false;
            pickaxes = false;
            food = false;
            enderChests = false;
        }

        public boolean tasksInactive() {
            return !materials && !pickaxes && !food && !enderChests;
        }

        public boolean isSequenceActive() {
            return sequenceActive;
        }

        public boolean isActiveMaterials() {
            return materials;
        }

        public boolean hasPendingPickaxes() {
            return pendingPickaxes;
        }

        public int getPickaxeStartCount() {
            return pickaxeStartCount;
        }

        public boolean isBlockadeReady() {
            return blockadeReady;
        }

        public void setBlockadeReady(boolean value) {
            boolean changed = blockadeReady != value;
            blockadeReady = value;
            if (value) blockadeLease.markBuilt();
            else blockadeLease.invalidate();
            if (changed) b.restockWatchdog.markProgress("blockade-ready:" + value);
        }

        private boolean shouldLogDuplicateQueuedTask(Type type) {
            if (!b.restockDebugLog.get()) return false;

            String key = type.name() + "|" + activeSummary() + "|" + pendingSummary();
            int age = b.mc.player == null ? 0 : b.mc.player.age;
            if (key.equals(lastDuplicateQueuedLogKey)
                && age - lastDuplicateQueuedLogAge < RESTOCK_DUPLICATE_QUEUE_LOG_INTERVAL_TICKS) {
                return false;
            }

            lastDuplicateQueuedLogKey = key;
            lastDuplicateQueuedLogAge = age;
            return true;
        }

        public void deferBlockadeTeardown() {
            blockadeTeardownPending = true;
            blockadeTeardownReadyAtAge = b.mc.player != null ? b.mc.player.age + BLOCKADE_TEARDOWN_DELAY_TICKS : BLOCKADE_TEARDOWN_DELAY_TICKS;
            blockadeLease.markTeardownPending();
        }

        public boolean advanceToPendingTask() {
            Type next = nextPendingTask();
            if (next == null) return false;

            clearPending(next);
            activate(next);
            b.info("Continuing restock with " + item() + ".");
            if (b.restockDebugLog.get()) {
                b.restockDebug("RestockTask advanced to queued task=%s remainingPending=%s blockadeReady=%s sequence=%s.",
                    item(),
                    pendingSummary(),
                    blockadeReady,
                    sequenceActive
                );
            }
            return true;
        }

        public boolean shouldTearDownRestockBlockade() {
            return sequenceActive && blockadeReady && tasksInactive() && !hasPendingTasks();
        }

        public boolean shouldBeginDeferredBlockadeTeardown() {
            return blockadeTeardownPending
                && sequenceActive
                && tasksInactive()
                && !hasPendingTasks()
                && (b.mc.player == null || b.mc.player.age >= blockadeTeardownReadyAtAge);
        }

        public boolean shouldFinishEmptyBlockadeTeardownWait() {
            return sequenceActive
                && tasksInactive()
                && !hasPendingTasks()
                && !blockadeTeardownPending;
        }

        public void finishSequence() {
            completeActive();
            clearPending();
            sequenceActive = false;
            blockadeReady = false;
            blockadeTeardownPending = false;
            blockadeTeardownReadyAtAge = 0;
            pickaxeStartCount = 0;
            blockadeLease.reset();
            session = null;
            clearObsidianPickaxePreflightTopoff();
        }

        public String item() {
            if (materials) return "building materials";
            if (pickaxes) return "pickaxes";
            if (food) return "food";
            if (enderChests) return "ender chests";
            if (blockadeTeardownPending) return "restock blockade teardown";
            return "unknown";
        }

        public String activeSummary() {
            List<String> active = new ArrayList<>();
            if (materials) active.add("materials");
            if (pickaxes) active.add("pickaxes");
            if (food) active.add("food");
            if (enderChests) active.add("ender_chests");
            return active.isEmpty() ? "none" : String.join(",", active);
        }

        public String pendingSummary() {
            List<String> pending = new ArrayList<>();
            if (pendingMaterials) pending.add("materials");
            if (pendingPickaxes) pending.add("pickaxes");
            if (pendingFood) pending.add("food");
            if (pendingEnderChests) pending.add("ender_chests");
            return pending.isEmpty() ? "none" : String.join(",", pending);
        }

        public String watchdogTaskKey() {
            Type type = activeType();
            if (type != null) return type.name();
            if (hasPendingTasks()) return "pending:" + pendingSummary();
            if (blockadeTeardownPending) return "teardown";
            return "none";
        }

        public String watchdogSessionSummary() {
            return session == null ? "none" : session.watchdogSummary();
        }

        private void startSequence(Type type) {
            finishSequence();
            sequenceActive = true;
            blockadeReady = false;
            activate(type);
            setState(State.Restock);
            b.info("Starting new restock task for " + item());
        }

        private void activate(Type type) {
            completeActive();
            blockadeTeardownPending = false;
            blockadeTeardownReadyAtAge = 0;
            onTaskActivated(type);
            session = new RestockSession(type);

            switch (type) {
                case Materials -> materials = true;
                case Pickaxes -> pickaxes = true;
                case Food -> food = true;
                case EnderChests -> enderChests = true;
            }
        }

        private boolean enqueue(Type type) {
            return switch (type) {
                case Materials -> {
                    if (pendingMaterials) yield false;
                    pendingMaterials = true;
                    pendingQueue.addLast(type);
                    yield true;
                }
                case Pickaxes -> {
                    if (pendingPickaxes) yield false;
                    pendingPickaxes = true;
                    pendingQueue.addLast(type);
                    yield true;
                }
                case Food -> {
                    if (pendingFood) yield false;
                    pendingFood = true;
                    pendingQueue.addLast(type);
                    yield true;
                }
                case EnderChests -> {
                    if (pendingEnderChests) yield false;
                    pendingEnderChests = true;
                    pendingQueue.addLast(type);
                    yield true;
                }
            };
        }

        private boolean isActive(Type type) {
            return switch (type) {
                case Materials -> materials;
                case Pickaxes -> pickaxes;
                case Food -> food;
                case EnderChests -> enderChests;
            };
        }

        private boolean hasPendingTasks() {
            return pendingMaterials || pendingPickaxes || pendingFood || pendingEnderChests;
        }

        public boolean hasPendingRestockTasks() {
            return hasPendingTasks();
        }

        public boolean isBlockadeTeardownPending() {
            return blockadeTeardownPending;
        }

        private void onTaskActivated(Type type) {
            b.invalidRestockRecoveryRetries = 0;
            b.invalidRestockRecoveryPending = false;

            if (type == Type.Pickaxes) {
                pickaxeStartCount = countInventoryItems(itemStack -> itemStack.isIn(ItemTags.PICKAXES));
                if (obsidianPickaxePreflightTopoffPending) {
                    obsidianPickaxePreflightTopoffPending = false;
                    obsidianPickaxePreflightTopoffActive = true;
                    obsidianPickaxePreflightTopoffBaseline = b.countLooseEligibleEChestMiningPickaxes();
                    if (b.restockDebugLog.get()) {
                        b.restockDebug("RestockTask activated forced obsidian pickaxe preflight topoff (baseline=%d eChestsToBreak=%d reason=%s).",
                            obsidianPickaxePreflightTopoffBaseline,
                            obsidianPickaxePreflightEChestsToBreak,
                            obsidianPickaxePreflightReason
                        );
                    }
                }
            }
        }

        private void clearObsidianPickaxePreflightTopoff() {
            obsidianPickaxePreflightTopoffPending = false;
            obsidianPickaxePreflightTopoffActive = false;
            obsidianPickaxePreflightSatisfied = false;
            obsidianPickaxePreflightTopoffBaseline = 0;
            obsidianPickaxePreflightEChestsToBreak = 0;
            obsidianPickaxePreflightReason = "";
        }

        private String obsidianPickaxePreflightTopoffFailureMessage() {
            return "Obsidian restock needs one more usable pickaxe, but no enabled restock source could provide it. Enable a pickaxe restock source or bring a spare.";
        }

        public boolean isObsidianPickaxePreflightTopoffActive() {
            return obsidianPickaxePreflightTopoffActive;
        }

        public boolean isObsidianPickaxePreflightSatisfied() {
            return obsidianPickaxePreflightSatisfied;
        }

        public boolean prioritizeObsidianPreflightPickaxesBeforeActiveTask(int eChestsToBreak, String reason) {
            Type current = activeType();
            if (current == null || current == Type.Pickaxes) return false;

            if (session != null) session.markObsidianPickaxePreflightQueued();
            obsidianPickaxePreflightSatisfied = false;
            obsidianPickaxePreflightTopoffPending = true;
            obsidianPickaxePreflightEChestsToBreak = eChestsToBreak;
            obsidianPickaxePreflightReason = reason == null ? "unknown" : reason;

            boolean prioritized = prioritizePickaxesBeforeActiveTask(reason);
            if (!prioritized) {
                obsidianPickaxePreflightTopoffPending = false;
                obsidianPickaxePreflightEChestsToBreak = 0;
                obsidianPickaxePreflightReason = "";
            }

            return prioritized;
        }

        public RestockSession getSession() {
            return session;
        }

        public boolean freezeSessionTargetForSourceSelection() {
            return session != null && session.freezeTargetForSourceSelection();
        }

        public void reportUnusableSessionTarget() {
            if (pickaxes) {
                String message = obsidianPickaxePreflightTopoffActive
                    ? "Not enough empty slots to prepare obsidian restock with an additional usable non-silk pickaxe."
                    : "Not enough empty slots to restock pickaxes.";
                b.notifyDesktop(b.notifyRestockIssues, "THM Highway Builder", message);
                b.error(message);
                return;
            }

            if (food) {
                if (b.restockDebugLog.get()) {
                    b.restockDebug("Food restock target was unusable because inventory capacity could not exceed the configured threshold.");
                }
                failActiveTaskHard();
                return;
            }

            b.notifyDesktop(b.notifyRestockIssues, "THM Highway Builder", "No empty slots available for restocking items.");
            if (b.restockDebugLog.get()) {
                b.restockDebug("RestockTask hard fail: no empty slots for task=%s active=%s pending=%s target=%d remaining=%d progress=%d.",
                    item(),
                    activeSummary(),
                    pendingSummary(),
                    getTargetFinal(),
                    getRemainingTarget(),
                    getSession() == null ? 0 : getSession().getProgressTowardsTarget()
                );
            }
            b.error("No empty slots for restocking items.");
        }

        public boolean failActiveTaskHard() {
            if (tasksInactive()) return false;

            if (obsidianPickaxePreflightTopoffActive) {
                return failActiveTaskHard(obsidianPickaxePreflightTopoffFailureMessage());
            }

            String activeItem = item();
            return failActiveTaskHard("Couldn't restock '" + activeItem + "' — no source had it. Check your inventory, ender chest and shulker sources are set up.");
        }

        public boolean failActiveTaskHard(String message) {
            if (tasksInactive()) return false;

            if (session != null) session.fail();
            b.notifyDesktop(b.notifyRestockIssues, "THM Highway Builder", message);
            b.error(message);
            return true;
        }

        public void notePhase(SourcePhase phase) {
            if (session != null) session.notePhase(phase);
        }

        public void refreshSessionProgress() {
            if (session != null) session.refreshProgress();
        }

        public boolean recalculateFrozenSessionCapacityAfterKitbotDelivery() {
            return session != null && session.recalculateFrozenCapacityAfterKitbotDelivery();
        }

        public void noteSourceForwardProgress(SourcePhase phase, int beforeProgress, int beforeUsablePulledEchests) {
            if (session != null) session.noteSourceForwardProgress(phase, beforeProgress, beforeUsablePulledEchests);
        }

        public void noteSuccessfulLocalSourceAction(SourcePhase phase) {
            if (session != null) session.noteSuccessfulLocalSourceAction(phase);
        }

        public void noteUsefulEnderChestShulkerExtraction() {
            if (session != null) session.noteUsefulEnderChestShulkerExtraction();
        }

        public boolean isTargetSatisfied() {
            return session != null && session.isTargetSatisfied();
        }

        public boolean isCurrentTaskSuccessful() {
            return session != null && session.isTaskSuccessful();
        }

        public boolean isCurrentTaskSuccessfulAtFinalNoSource() {
            return session != null && session.isTaskSuccessfulAtFinalNoSource();
        }

        public int getRemainingTarget() {
            return session != null ? session.getRemainingTarget() : 0;
        }

        public int getTargetFinal() {
            return session != null ? session.targetFinal : 0;
        }

        public boolean shouldCompleteAfterInventoryShulkers() {
            return session != null && session.shouldCompleteAfterInventoryShulkers();
        }

        public boolean canTransitionToMineEnderChests() {
            return session != null && session.canTransitionToMineEnderChests();
        }

        public boolean shrinkObsidianTargetToCurrentRawEChestBatch(String reason) {
            return session != null && session.shrinkObsidianTargetToCurrentRawEChestBatch(reason);
        }

        public boolean clampObsidianTargetToMineableEChests(String reason) {
            return session != null && session.clampObsidianTargetToMineableEChests(reason);
        }

        public boolean needsMoreRawEchestsForSession() {
            return session != null && session.needsMoreRawEchests();
        }

        public int getRemainingRawEchestsNeeded() {
            return session != null ? session.getRemainingRawEchestsNeeded() : 0;
        }

        public int getRemainingObsidianItemsForSession() {
            return session != null ? session.getRemainingObsidianItems() : 0;
        }

        public boolean validateOrInvalidateBlockadeLease() {
            boolean valid = blockadeLease.validateCurrentAnchor();
            if (!valid) blockadeReady = false;
            return valid;
        }

        public BlockPos getSourceContainerPos() {
            if (!blockadeReady) return null;
            if (!blockadeLease.validateCurrentAnchor()) {
                blockadeReady = false;
                return null;
            }
            return blockadeLease.sourceContainerPos();
        }

        public void markCurrentSourceExhausted(SourcePhase phase) {
            if (session != null) session.markSourceExhausted(phase);
        }

        public void markEnderChestSourceExhaustedAfterLiveCheck(boolean memoryKnown, int rawMatches, int matchingShulkers, boolean accessAvailable) {
            if (session != null) session.markEnderChestExhaustedAfterLiveCheck(memoryKnown, rawMatches, matchingShulkers, accessAvailable);
        }

        public void reopenSourcePhase(SourcePhase phase) {
            if (session != null) session.reopenSourcePhase(phase);
        }

        public boolean shouldAttemptKitbot() {
            return session != null && session.shouldAttemptKitbot();
        }

        public boolean shouldAttemptKitbotAfterMineEnderChests() {
            return session != null && session.shouldAttemptKitbotAfterMineEnderChests();
        }

        public void markInventoryLocalTried() {
            if (session != null) session.markInventoryLocalTried();
        }

        public void markEnderChestBankTried() {
            if (session != null) session.markEnderChestBankTried();
        }

        public void markKitbotTried() {
            if (session != null) session.markKitbotTried();
        }

        public boolean hasTriedEnderChestBank() {
            return session != null && session.hasTriedEnderChestBank();
        }

        public boolean hasTriedKitbot() {
            return session != null && session.hasTriedKitbot();
        }

        public boolean hasQueuedObsidianPickaxePreflight() {
            return obsidianPickaxePreflightTopoffPending
                || obsidianPickaxePreflightTopoffActive
                || (session != null && session.hasQueuedObsidianPickaxePreflight());
        }

        public void markObsidianPickaxePreflightQueued() {
            if (session != null) session.markObsidianPickaxePreflightQueued();
        }

        public boolean isObsidianRestockSession() {
            return session != null && session.isObsidianTask();
        }

        private Type activeType() {
            if (materials) return Type.Materials;
            if (pickaxes) return Type.Pickaxes;
            if (food) return Type.Food;
            if (enderChests) return Type.EnderChests;
            return null;
        }

        public boolean prioritizePickaxesBeforeActiveTask(String reason) {
            Type current = activeType();
            if (current == null || current == Type.Pickaxes) return false;

            if (!isPending(current)) {
                switch (current) {
                    case Materials -> pendingMaterials = true;
                    case Food -> pendingFood = true;
                    case EnderChests -> pendingEnderChests = true;
                    case Pickaxes -> { }
                }
            }

            pendingQueue.removeFirstOccurrence(current);
            pendingQueue.addFirst(current);
            pendingPickaxes = false;
            pendingQueue.removeFirstOccurrence(Type.Pickaxes);
            activate(Type.Pickaxes);

            if (b.restockDebugLog.get()) {
                b.restockDebug("RestockTask prioritized pickaxe restock before %s (%s).", item(current), reason);
            }
            return true;
        }

        private Type nextPendingTask() {
            while (!pendingQueue.isEmpty()) {
                Type next = pendingQueue.peekFirst();
                if (next == null) break;
                if (isPending(next)) return next;
                pendingQueue.removeFirst();
            }
            return null;
        }

        private void clearPending() {
            pendingMaterials = false;
            pendingPickaxes = false;
            pendingFood = false;
            pendingEnderChests = false;
            pendingQueue.clear();
        }

        private void clearPending(Type type) {
            switch (type) {
                case Materials -> pendingMaterials = false;
                case Pickaxes -> pendingPickaxes = false;
                case Food -> pendingFood = false;
                case EnderChests -> pendingEnderChests = false;
            }
            pendingQueue.removeFirstOccurrence(type);
        }

        private boolean isPending(Type type) {
            return switch (type) {
                case Materials -> pendingMaterials;
                case Pickaxes -> pendingPickaxes;
                case Food -> pendingFood;
                case EnderChests -> pendingEnderChests;
            };
        }

        private String item(Type type) {
            return switch (type) {
                case Materials -> "building materials";
                case Pickaxes -> "pickaxes";
                case Food -> "food";
                case EnderChests -> "ender chests";
            };
        }

        private int countInventoryItems(Predicate<ItemStack> predicate) {
            int count = 0;
            for (int i = 0; i < b.mc.player.getInventory().getMainStacks().size(); i++) {
                ItemStack stack = b.mc.player.getInventory().getStack(i);
                if (predicate.test(stack)) count += stack.getCount();
            }
            return count;
        }

        private int countInventorySlots(Predicate<ItemStack> predicate) {
            int count = 0;
            for (int i = 0; i < b.mc.player.getInventory().getMainStacks().size(); i++) {
                ItemStack stack = b.mc.player.getInventory().getStack(i);
                if (predicate.test(stack)) count++;
            }
            return count;
        }

        private int countEmptyInventorySlots() {
            int count = 0;
            for (int i = 0; i < b.mc.player.getInventory().getMainStacks().size(); i++) {
                if (b.mc.player.getInventory().getStack(i).isEmpty()) count++;
            }
            return count;
        }
    }
}
