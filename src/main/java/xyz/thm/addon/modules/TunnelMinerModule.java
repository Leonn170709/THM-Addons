package xyz.thm.addon.modules;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.movement.speed.Speed;
import meteordevelopment.meteorclient.systems.modules.movement.speed.SpeedModes;
import meteordevelopment.meteorclient.systems.modules.player.AutoEat;
import meteordevelopment.meteorclient.systems.modules.player.AutoGap;
import meteordevelopment.meteorclient.systems.modules.player.Reach;
import meteordevelopment.meteorclient.systems.modules.render.FreeLook;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.*;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import xyz.thm.addon.THMAddon;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Predicate;

/**
 * TunnelMiner — digs a straight tunnel block by block from current position
 * to (targetX, currentY, targetZ). Travels X axis first, then Z axis.
 *
 * Main loop per block step:
 *   1. MINE  — break every block in the tunnel cross-section one step ahead
 *   2. WALK  — move the player exactly one block forward
 *   3. FILL  — re-place blocks behind (optional)
 *   Then repeat until destination reached
 */
public class TunnelMinerModule extends Module {

    public static TunnelMinerModule INSTANCE;
    private static final int DETOUR_ASTAR_RADIUS = 24;
    private static final int DETOUR_ASTAR_MAX_NODES = 2000;
    private static final double ASTAR_STRAIGHT_COST = 1.0;
    private static final double ASTAR_DIAGONAL_COST = 1.41421356237;
    private static final int PROACTIVE_ASTAR_REUSE_TICKS = 4;
    private static final int PROBE_LOOP_VISIT_REPLAN_THRESHOLD = 6;
    private static final int PROBE_REPLAN_COOLDOWN_TICKS = 8;
    private static final int PROBE_EMPTY_REPLAN_COOLDOWN_TICKS = 20;
    private static final int PROBE_NO_STEPS_FORCE_REPLAN_TICKS = 35;
    private static final int WALL_FOLLOW_PREFIX_FALLBACK_STEPS = 4;
    private static final int WALL_FOLLOW_MAX_REROUTE_BLOCKS = 200;
    private static final int WALL_FOLLOW_MIN_REJOIN_DISTANCE_BLOCKS = 5;
    private static final int EMERGENCY_AIR_FALLBACK_MAX_TICKS = 40;
    private static final int EMERGENCY_AIR_FALLBACK_MAX_BLOCKS = 5;
    private static final boolean ASTAR_ASYNC = true;
    private static final ExecutorService ASTAR_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "thm-tunnelminer-astar");
        t.setDaemon(true);
        return t;
    });
    private static final int HAZARD_RECOVERY_MAX_TICKS = 40;
    private static final boolean HAZARD_RECOVERY_FORCE_HARD_FAIL = true;
    private static final int NON_STEALTH_PROBE_MAX = 16;
    private static final double WALL_FOLLOW_MLINE_EPSILON = 0.75;
    private static final double GREEDY_BACKTRACK_PENALTY = 1000.0;
    // Hidden temporary hard-coded tuning values (kept alongside hidden settings for easy re-enable).
    private static final double HARD_DETOUR_SAFETY_BUFFER_COST = 0.0;
    private static final int HARD_PATH_CALC_INTERVAL_TICKS = 4;
    private static final int HARD_PATH_CALC_MAX_NODES = 768;
    private static final int HARD_ASTAR_MAX_FAILS = 3;
    private static final int HARD_STALL_STOP_TICKS = 400;
    private static final int WATCHDOG_BUFFER_FLUSH_LINES = 256;
    private static final int WATCHDOG_BUFFER_FLUSH_CHARS = 262_144;
    private static final boolean HARD_WATCHDOG_LOG_TICKS = true;
    private static final boolean HARD_WATCHDOG_VERBOSE = true;
    private static final boolean HARD_WATCHDOG_HOTPATH_CALCULATIONS = false;
    private static final int HARD_WATCHDOG_MAX_FILE_MB = 256;
    private static final int GOAL_FALLBACK_RADIUS = 47;
    private static final int GOAL_FALLBACK_MAX_NODES = 4096;
    private static final String RESUME_CACHE_MAGIC = "THM_TUNNEL_RESUME_V1";
    private static final String SPEED_SNAPSHOT_MAGIC = "THM_TUNNEL_SPEED_SNAPSHOT_V1";
    private static final String[] EMBEDDED_STATE_CHANGE_BLOCK_IDS = new String[] {
        "minecraft:stone",
        "minecraft:deepslate",
        "minecraft:grass_block",
        "minecraft:mycelium",
        "minecraft:podzol",
        "minecraft:dirt_path",
        "minecraft:crimson_nylium",
        "minecraft:warped_nylium",
        "minecraft:farmland",
        "minecraft:daylight_detector",
        "minecraft:campfire",
        "minecraft:soul_campfire",
        "minecraft:bookshelf",
        "minecraft:clay",
        "minecraft:glowstone",
        "minecraft:amethyst_cluster",
        "minecraft:sea_lantern",
        "minecraft:melon",
        "minecraft:ender_chest",
        "minecraft:decorated_pot",
        "minecraft:coal_ore",
        "minecraft:deepslate_coal_ore",
        "minecraft:copper_ore",
        "minecraft:deepslate_copper_ore",
        "minecraft:iron_ore",
        "minecraft:deepslate_iron_ore",
        "minecraft:gold_ore",
        "minecraft:deepslate_gold_ore",
        "minecraft:diamond_ore",
        "minecraft:deepslate_diamond_ore",
        "minecraft:emerald_ore",
        "minecraft:deepslate_emerald_ore",
        "minecraft:lapis_ore",
        "minecraft:deepslate_lapis_ore",
        "minecraft:redstone_ore",
        "minecraft:deepslate_redstone_ore",
        "minecraft:nether_quartz_ore",
        "minecraft:nether_gold_ore",
        "minecraft:gilded_blackstone",
        "minecraft:gravel",
        "minecraft:dead_bush",
        "minecraft:twisting_vines",
        "minecraft:twisting_vines_plant",
        "minecraft:weeping_vines",
        "minecraft:weeping_vines_plant",
        "minecraft:brown_mushroom_block",
        "minecraft:red_mushroom_block",
        "minecraft:budding_amethyst",
        "minecraft:small_amethyst_bud",
        "minecraft:medium_amethyst_bud",
        "minecraft:large_amethyst_bud",
        "minecraft:bee_nest",
        "minecraft:beehive",
        "minecraft:blue_ice",
        "minecraft:cake",
        "minecraft:fire",
        "minecraft:frosted_ice",
        "minecraft:glass",
        "minecraft:glass_pane",
        "minecraft:ice",
        "minecraft:packed_ice",
        "minecraft:powder_snow",
        "minecraft:reinforced_deepslate",
        "minecraft:sculk_vein",
        "minecraft:snow",
        "minecraft:snow_block",
        "minecraft:suspicious_sand",
        "minecraft:suspicious_gravel",
        "minecraft:turtle_egg",
        "minecraft:mushroom_stem",
        "minecraft:spawner",
        "minecraft:sculk",
        "minecraft:sculk_catalyst",
        "minecraft:sculk_sensor",
        "minecraft:calibrated_sculk_sensor",
        "minecraft:sculk_shrieker",
        "minecraft:infested_stone",
        "minecraft:infested_cobblestone",
        "minecraft:infested_stone_bricks",
        "minecraft:infested_mossy_stone_bricks",
        "minecraft:infested_cracked_stone_bricks",
        "minecraft:infested_chiseled_stone_bricks",
        "minecraft:infested_deepslate",
        "minecraft:creaking_heart",
        "minecraft:chiseled_bookshelf",
        "minecraft:wheat",
        "minecraft:carrots",
        "minecraft:potatoes",
        "minecraft:beetroots",
        "minecraft:nether_wart",
        "minecraft:cocoa",
        "minecraft:melon_stem",
        "minecraft:attached_melon_stem",
        "minecraft:pumpkin_stem",
        "minecraft:attached_pumpkin_stem",
        "minecraft:oak_leaves",
        "minecraft:spruce_leaves",
        "minecraft:birch_leaves",
        "minecraft:jungle_leaves",
        "minecraft:acacia_leaves",
        "minecraft:dark_oak_leaves",
        "minecraft:mangrove_leaves",
        "minecraft:cherry_leaves",
        "minecraft:pale_oak_leaves",
        "minecraft:azalea_leaves",
        "minecraft:flowering_azalea_leaves",
        "minecraft:tube_coral_block",
        "minecraft:brain_coral_block",
        "minecraft:bubble_coral_block",
        "minecraft:fire_coral_block",
        "minecraft:horn_coral_block",
        "minecraft:tube_coral",
        "minecraft:brain_coral",
        "minecraft:bubble_coral",
        "minecraft:fire_coral",
        "minecraft:horn_coral",
        "minecraft:dead_tube_coral",
        "minecraft:dead_brain_coral",
        "minecraft:dead_bubble_coral",
        "minecraft:dead_fire_coral",
        "minecraft:dead_horn_coral",
        "minecraft:tube_coral_fan",
        "minecraft:brain_coral_fan",
        "minecraft:bubble_coral_fan",
        "minecraft:fire_coral_fan",
        "minecraft:horn_coral_fan",
        "minecraft:dead_tube_coral_fan",
        "minecraft:dead_brain_coral_fan",
        "minecraft:dead_bubble_coral_fan",
        "minecraft:dead_fire_coral_fan",
        "minecraft:dead_horn_coral_fan",
        "minecraft:tube_coral_wall_fan",
        "minecraft:brain_coral_wall_fan",
        "minecraft:bubble_coral_wall_fan",
        "minecraft:fire_coral_wall_fan",
        "minecraft:horn_coral_wall_fan",
        "minecraft:dead_tube_coral_wall_fan",
        "minecraft:dead_brain_coral_wall_fan",
        "minecraft:dead_bubble_coral_wall_fan",
        "minecraft:dead_fire_coral_wall_fan",
        "minecraft:dead_horn_coral_wall_fan",
        "minecraft:white_stained_glass",
        "minecraft:orange_stained_glass",
        "minecraft:magenta_stained_glass",
        "minecraft:light_blue_stained_glass",
        "minecraft:yellow_stained_glass",
        "minecraft:lime_stained_glass",
        "minecraft:pink_stained_glass",
        "minecraft:gray_stained_glass",
        "minecraft:light_gray_stained_glass",
        "minecraft:cyan_stained_glass",
        "minecraft:purple_stained_glass",
        "minecraft:blue_stained_glass",
        "minecraft:brown_stained_glass",
        "minecraft:green_stained_glass",
        "minecraft:red_stained_glass",
        "minecraft:black_stained_glass",
        "minecraft:white_stained_glass_pane",
        "minecraft:orange_stained_glass_pane",
        "minecraft:magenta_stained_glass_pane",
        "minecraft:light_blue_stained_glass_pane",
        "minecraft:yellow_stained_glass_pane",
        "minecraft:lime_stained_glass_pane",
        "minecraft:pink_stained_glass_pane",
        "minecraft:gray_stained_glass_pane",
        "minecraft:light_gray_stained_glass_pane",
        "minecraft:cyan_stained_glass_pane",
        "minecraft:purple_stained_glass_pane",
        "minecraft:blue_stained_glass_pane",
        "minecraft:brown_stained_glass_pane",
        "minecraft:green_stained_glass_pane",
        "minecraft:red_stained_glass_pane",
        "minecraft:black_stained_glass_pane"
    };
    private static final String[] EMBEDDED_GRAVITY_CHAIN_BLOCK_IDS = new String[] {
        "minecraft:sand",
        "minecraft:red_sand",
        "minecraft:gravel",
        "minecraft:suspicious_sand",
        "minecraft:suspicious_gravel",
        "minecraft:anvil",
        "minecraft:chipped_anvil",
        "minecraft:damaged_anvil",
        "minecraft:dragon_egg",
        "minecraft:scaffolding",
        "minecraft:pointed_dripstone",
        "minecraft:snow",
        "minecraft:white_concrete_powder",
        "minecraft:orange_concrete_powder",
        "minecraft:magenta_concrete_powder",
        "minecraft:light_blue_concrete_powder",
        "minecraft:yellow_concrete_powder",
        "minecraft:lime_concrete_powder",
        "minecraft:pink_concrete_powder",
        "minecraft:gray_concrete_powder",
        "minecraft:light_gray_concrete_powder",
        "minecraft:cyan_concrete_powder",
        "minecraft:purple_concrete_powder",
        "minecraft:blue_concrete_powder",
        "minecraft:brown_concrete_powder",
        "minecraft:green_concrete_powder",
        "minecraft:red_concrete_powder",
        "minecraft:black_concrete_powder"
    };

    private enum PathMode {
        AxisFirst,
        DiagonalThenAxis
    }
    private static final PathMode HARD_PATH_MODE = PathMode.DiagonalThenAxis;

    public enum SettingsSource {
        MANUAL("manual"),
        API("api");

        private final String id;

        SettingsSource(String id) {
            this.id = id;
        }
    }

    public static final int API_STEALTH_OFF = 0;
    public static final int API_STEALTH_ON = 1;

    private record ApiGoToRequest(int x, int z, int stealthMode, boolean renderingEnabled) {}

    private record EffectiveOptions(
        int targetX,
        int targetZ,
        int tunnelHeight,
        boolean fillBehind,
        List<Block> fillBlocks,
        boolean lavaAvoidance,
        boolean airPlace,
        int airPlaceDistance,
        boolean autoFreeLook,
        boolean resumeCacheOnReactivate,
        boolean debugMessages,
        boolean stealthMode,
        int stealthProbeDistance,
        int stealthMineAheadDistance,
        double stealthStopRange,
        boolean stealthDoubleMine,
        boolean stealthFastBreak,
        int stealthRestoreLagDistance,
        boolean strictStealthExactRestore,
        boolean watchdogEnabled,
        boolean useShulkers,
        boolean useEnderChest,
        int minPickaxes,
        int breaksPerTick,
        int stealthBreakDelay,
        int placesPerTick,
        int placeDelay,
        int invDelay,
        ShapeMode shapeMode,
        boolean renderMining,
        boolean renderPlacing,
        boolean renderPath,
        int renderPathSteps,
        SettingColor breakSideColor,
        SettingColor breakLineColor,
        SettingColor placeSideColor,
        SettingColor placeLineColor,
        SettingColor pathSideColor,
        SettingColor pathLineColor
    ) {}

    private record ApiModePreset(
        int tunnelHeight,
        boolean fillBehind,
        List<Block> fillBlocks,
        boolean lavaAvoidance,
        boolean airPlace,
        int airPlaceDistance,
        boolean autoFreeLook,
        boolean resumeCacheOnReactivate,
        boolean debugMessages,
        int stealthProbeDistance,
        int stealthMineAheadDistance,
        double stealthStopRange,
        boolean stealthDoubleMine,
        boolean stealthFastBreak,
        int stealthRestoreLagDistance,
        boolean strictStealthExactRestore,
        boolean watchdogEnabled,
        boolean useShulkers,
        boolean useEnderChest,
        int minPickaxes,
        int breaksPerTick,
        int stealthBreakDelay,
        int placesPerTick,
        int placeDelay,
        int invDelay,
        ShapeMode shapeMode,
        SettingColor breakSideColor,
        SettingColor breakLineColor,
        SettingColor placeSideColor,
        SettingColor placeLineColor,
        int renderPathSteps,
        SettingColor pathSideColor,
        SettingColor pathLineColor
    ) {}

    private record SpeedSnapshot(
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

    private static final Set<String> EFFECTIVE_OPTION_SETTING_FIELDS = Set.of(
        "targetX",
        "targetZ",
        "tunnelHeight",
        "fillBehind",
        "fillBlocks",
        "lavaAvoidance",
        "airPlace",
        "airPlaceDistance",
        "autoFreeLook",
        "resumeCacheOnReactivate",
        "debugMessages",
        "stealthMode",
        "stealthProbeDistance",
        "stealthMineAheadDistance",
        "stealthStopRange",
        "stealthDoubleMine",
        "stealthFastBreak",
        "stealthRestoreLagDistance",
        "strictStealthExactRestore",
        "watchdogEnabled",
        "useShulkers",
        "useEnderChest",
        "minPickaxes",
        "breaksPerTick",
        "stealthBreakDelay",
        "placesPerTick",
        "placeDelay",
        "invDelay",
        "shapeMode",
        "renderMining",
        "renderPlacing",
        "renderPath",
        "renderPathSteps",
        "breakSideColor",
        "breakLineColor",
        "placeSideColor",
        "placeLineColor",
        "pathSideColor",
        "pathLineColor"
    );

    private static final Set<String> NON_EFFECTIVE_OPTION_SETTING_FIELDS = Set.of(
        "pathMode",
        "detourSafetyBufferCost",
        "rotate",
        "stealthPathCalcIntervalTicks",
        "stealthPathCalcMaxNodes",
        "stealthAStarMaxFails",
        "stealthStallStopTicks"
    );

    private static final ApiModePreset API_PRESET_STEALTH_ON = new ApiModePreset(
        2,
        true,
        List.of(Blocks.NETHERRACK),
        true,
        true,
        5,
        true,
        true,
        false,
        48,
        4,
        1.5,
        true,
        true,
        4,
        false,
        false,
        true,
        false,
        2,
        2,
        0,
        1,
        0,
        3,
        ShapeMode.Both,
        new SettingColor(255, 0, 0, 35),
        new SettingColor(255, 0, 0, 255),
        new SettingColor(0, 0, 255, 35),
        new SettingColor(0, 0, 255, 255),
        32,
        new SettingColor(255, 255, 0, 35),
        new SettingColor(255, 255, 0, 255)
    );

    private static final ApiModePreset API_PRESET_STEALTH_OFF = new ApiModePreset(
        2,
        false,
        List.of(Blocks.NETHERRACK),
        true,
        true,
        5,
        true,
        true,
        false,
        48,
        4,
        1.5,
        true,
        true,
        4,
        false,
        false,
        true,
        false,
        2,
        2,
        0,
        1,
        0,
        3,
        ShapeMode.Both,
        new SettingColor(255, 0, 0, 35),
        new SettingColor(255, 0, 0, 255),
        new SettingColor(0, 0, 255, 35),
        new SettingColor(0, 0, 255, 255),
        32,
        new SettingColor(255, 255, 0, 35),
        new SettingColor(255, 255, 0, 255)
    );

    // ── Settings ──────────────────────────────────────────────────────────────

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgStealth = settings.createGroup("Mining Options");
    private final SettingGroup sgRestock = settings.createGroup("Restock");
    private final SettingGroup sgTiming  = settings.createGroup("Timing");
    private final SettingGroup sgRender = settings.createGroup("Render");
    private final SettingGroup sgWatchdog = settings.createGroup("Watchdog", false);

    private final Setting<Integer> targetX = sgGeneral.add(new IntSetting.Builder()
        .name("target-x").description("Target X coordinate.").defaultValue(0).build());

    private final Setting<Integer> targetZ = sgGeneral.add(new IntSetting.Builder()
        .name("target-z").description("Target Z coordinate.").defaultValue(0).build());

    private final Setting<PathMode> pathMode = sgGeneral.add(new EnumSetting.Builder<PathMode>()
        .name("path-mode")
        .description("Path planner mode. AxisFirst preserves legacy behavior; DiagonalThenAxis alternates X/Z steps for a zigzag diagonal path while both axes differ.")
        .visible(() -> false)
        .defaultValue(PathMode.AxisFirst)
        .build());

    private final Setting<Integer> tunnelHeight = sgGeneral.add(new IntSetting.Builder()
        .name("tunnel-height").description("Height of the tunnel in blocks (2 = player fits).")
        .defaultValue(2).min(2).max(4).sliderMax(4).build());

    private final Setting<Boolean> fillBehind = sgGeneral.add(new BoolSetting.Builder()
        .name("fill-behind")
        .description("Fills the tunnel behind you with selected blocks.")
        .defaultValue(false)
        .build());

    private final Setting<List<Block>> fillBlocks = sgGeneral.add(new BlockListSetting.Builder()
        .name("fill-blocks")
        .description("Blocks to use for filling and bridging.")
        .visible(() -> fillBehind.get())
        .defaultValue(Blocks.NETHERRACK)
        .build());

    private final Setting<Boolean> lavaAvoidance = sgGeneral.add(new BoolSetting.Builder()
        .name("anti-lava")
        .description("Detects and seals lava (source/flowing) in tunnel, ceiling, and tunnel-adjacent cells.")
        .defaultValue(true).build());

    private final Setting<Double> detourSafetyBufferCost = sgGeneral.add(new DoubleSetting.Builder()
        .name("detour-safety-buffer-cost")
        .description("Extra A* cost per blocked adjacent cell to keep detours farther from avoided blocks (0 disables).")
        .visible(() -> false)
        .defaultValue(0.0).min(0.0).sliderMax(4.0)
        .build());

    private final Setting<Boolean> airPlace = sgGeneral.add(new BoolSetting.Builder()
        .name("air-place")
        .description("Use air place")
        .defaultValue(true).build());

    private final Setting<Integer> airPlaceDistance = sgGeneral.add(new IntSetting.Builder()
        .name("scaffold")
        .description("How many blocks ahead to place.")
        .defaultValue(5).min(1).max(6).sliderMax(6).build());

    private final Setting<Boolean> rotate = sgGeneral.add(new BoolSetting.Builder()
        .name("rotate")
        .description("Faces the block being interacted with.")
        .visible(() -> false)
        .defaultValue(true)
        .build());

    private final Setting<Boolean> autoFreeLook = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-freelook")
        .description("Enable Meteor FreeLook while Tunnel Miner runs to prevent mouse look from disrupting pathing.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> resumeCacheOnReactivate = sgGeneral.add(new BoolSetting.Builder()
        .name("resume-cache-on-reactivate")
        .description("Keeps tunnel restore/probe cache when toggled off and back on, so the module can resume from where it left off.")
        .defaultValue(true)
        .build());

    public final Setting<Boolean> debugMessages = sgGeneral.add(new BoolSetting.Builder()
        .name("debug-messages")
        .description("Logs detailed information about the module's state.")
        .defaultValue(false)
        .build());

    private final Setting<Boolean> stealthMode = sgStealth.add(new BoolSetting.Builder()
        .name("stealth-mode")
        .description("Probe ahead, mine ahead while moving, restore exact block types behind, and auto-enable all stealth avoidance rules.(VERY CPU INTENSIVE)")
        .defaultValue(false)
        .build());

    private final Setting<Integer> stealthProbeDistance = sgStealth.add(new IntSetting.Builder()
        .name("probe-distance")
        .description("How many path steps ahead to cache for exact restoration.")
        .defaultValue(48).min(1).max(64).sliderMax(32)
        .build());

    private final Setting<Integer> stealthPathCalcIntervalTicks = sgStealth.add(new IntSetting.Builder()
        .name("path-calc-interval")
        .description("Minimum ticks between heavy probe path recalculations (higher = less lag, slower path updates).")
        .visible(() -> false)
        .defaultValue(4).min(1).max(40).sliderMax(20)
        .build());

    private final Setting<Integer> stealthPathCalcMaxNodes = sgStealth.add(new IntSetting.Builder()
        .name("path-calc-max-nodes")
        .description("Maximum A* nodes expanded for probe/detour path calculations (lower = less lag).")
        .visible(() -> false)
        .defaultValue(768).min(64).max(4096).sliderMax(2048)
        .build());

    private final Setting<Integer> stealthAStarMaxFails = sgStealth.add(new IntSetting.Builder()
        .name("a-star-max-fails")
        .description("Consecutive probe A* failures before switching to counterclockwise wall-follow fallback.")
        .visible(() -> false)
        .defaultValue(3).min(1).max(20).sliderMax(10)
        .build());

    private final Setting<Integer> stealthMineAheadDistance = sgStealth.add(new IntSetting.Builder()
        .name("mine-ahead-distance")
        .description("How many path steps ahead to actively mine while moving.")
        .defaultValue(4).min(1).max(5).sliderMax(5)
        .build());

    private final Setting<Double> stealthStopRange = sgStealth.add(new DoubleSetting.Builder()
        .name("close-mine-range")
        .description("Pause movement when a queued block to mine is this close to the player.")
        .defaultValue(1.5).min(0.5).sliderMax(4.0)
        .build());

    private final Setting<Boolean> stealthDoubleMine = sgStealth.add(new BoolSetting.Builder()
        .name("double-mine-ahead")
        .description("Use the Highway Builder double-mine pipeline for ahead mining.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> stealthFastBreak = sgStealth.add(new BoolSetting.Builder()
        .name("fast-break-ahead")
        .description("Allow the ahead-mining pipeline to finish blocks early, like Highway Builder fast-break.")
        .visible(stealthDoubleMine::get)
        .defaultValue(true)
        .build());

    private final Setting<Integer> stealthRestoreLagDistance = sgStealth.add(new IntSetting.Builder()
        .name("restore-lag-distance")
        .description("Allowed distance (in blocks) from pending restore blocks before movement pauses to catch up.")
        .defaultValue(4).min(0).max(4).sliderMax(4)
        .build());

    private final Setting<Boolean> strictStealthExactRestore = sgStealth.add(new BoolSetting.Builder()
        .name("strict-exact-restore")
        .description("Require exact original block restoration in stealth mode. If an exact restore block is missing from inventory, stop Tunnel Miner immediately.")
        .defaultValue(false)
        .build());

    private final Setting<Integer> stealthStallStopTicks = sgStealth.add(new IntSetting.Builder()
        .name("stall-stop-ticks")
        .description("Stop the module if position/progress does not change for this many ticks (0 disables).")
        .visible(() -> false)
        .defaultValue(400).min(0).max(36000).sliderMax(1200)
        .build());

    private final Setting<Boolean> watchdogEnabled = sgWatchdog.add(new BoolSetting.Builder()
        .name("watchdog-enabled")
        .description("Write Tunnel Miner execution watchdog logs to file. For Bug Reporting only.")
        .visible(() -> false)
        .defaultValue(false)
        .build());

    private final Setting<Boolean> useShulkers = sgRestock.add(new BoolSetting.Builder()
        .name("use-shulkers")
        .description("Open shulker boxes to restock pickaxes when count is below minimum.")
        .defaultValue(true).build());

    private final Setting<Boolean> useEnderChest = sgRestock.add(new BoolSetting.Builder()
        .name("use-ender-chest")
        .description("Open ender chests to restock pickaxes when count is below minimum.")
        .defaultValue(false).build());

    private final Setting<Integer> minPickaxes = sgRestock.add(new IntSetting.Builder()
        .name("min-pickaxes")
        .description("How many pickaxes to keep in inventory.")
        .defaultValue(2).min(1).max(10).sliderMax(10).build());

    private final Setting<Integer> breaksPerTick = sgTiming.add(new IntSetting.Builder()
        .name("breaks-per-tick").description("Block break attempts sent per tick.")
        .defaultValue(2).min(1).max(5).sliderMax(5).build());

    private final Setting<Integer> stealthBreakDelay = sgTiming.add(new IntSetting.Builder()
        .name("break-delay")
        .description("Delay between ahead-mining break actions.")
        .defaultValue(0).min(0).sliderMax(10)
        .build());

    private final Setting<Integer> placesPerTick = sgTiming.add(new IntSetting.Builder()
        .name("places-per-tick").description("Block placements attempted per tick.")
        .defaultValue(1).min(1).max(5).sliderMax(5).build());

    private final Setting<Integer> placeDelay = sgTiming.add(new IntSetting.Builder()
        .name("place-delay").description("Ticks to wait between fill placements.")
        .defaultValue(0).min(0).sliderMax(10).build());

    private final Setting<Integer> invDelay = sgTiming.add(new IntSetting.Builder()
        .name("inventory-delay").description("Ticks to wait between inventory actions.")
        .defaultValue(3).min(0).sliderMax(10).build());

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .description("How the blocks are rendered.")
        .defaultValue(ShapeMode.Both)
        .build()
    );

    private final Setting<Boolean> renderMining = sgRender.add(new BoolSetting.Builder()
        .name("render-mining")
        .description("Render blocks currently queued/active for mining.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> renderPlacing = sgRender.add(new BoolSetting.Builder()
        .name("render-placing")
        .description("Render blocks queued for restoration/placing.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> renderPath = sgRender.add(new BoolSetting.Builder()
        .name("render-path")
        .description("Render the planned tunnel path.")
        .defaultValue(false)
        .build());

    private final Setting<Integer> renderPathSteps = sgRender.add(new IntSetting.Builder()
        .name("render-path-steps")
        .description("Maximum number of path steps to render ahead.")
        .defaultValue(32).min(4).max(256).sliderMax(128)
        .build());

    private final Setting<SettingColor> breakSideColor = sgRender.add(new ColorSetting.Builder()
        .name("break-side-color")
        .description("The side color of blocks being broken.")
        .defaultValue(new SettingColor(255, 0, 0, 35))
        .build()
    );

    private final Setting<SettingColor> breakLineColor = sgRender.add(new ColorSetting.Builder()
        .name("break-line-color")
        .description("The line color of blocks being broken.")
        .defaultValue(new SettingColor(255, 0, 0, 255))
        .build()
    );

    private final Setting<SettingColor> placeSideColor = sgRender.add(new ColorSetting.Builder()
        .name("place-side-color")
        .description("The side color of blocks being placed.")
        .defaultValue(new SettingColor(0, 0, 255, 35))
        .build()
    );

    private final Setting<SettingColor> placeLineColor = sgRender.add(new ColorSetting.Builder()
        .name("place-line-color")
        .description("The line color of blocks being placed.")
        .defaultValue(new SettingColor(0, 0, 255, 255))
        .build()
    );

    private final Setting<SettingColor> pathSideColor = sgRender.add(new ColorSetting.Builder()
        .name("path-side-color")
        .description("The side color of path preview blocks.")
        .defaultValue(new SettingColor(255, 255, 0, 35))
        .build()
    );

    private final Setting<SettingColor> pathLineColor = sgRender.add(new ColorSetting.Builder()
        .name("path-line-color")
        .description("The line color of path preview blocks.")
        .defaultValue(new SettingColor(255, 255, 0, 255))
        .build()
    );


    // ── State machine ─────────────────────────────────────────────────────────

    private enum Phase {
        INIT,
        MINE,           // Break blocks one step ahead — loops every tick until clear
        WALK,           // Move forward one block — loops every tick until arrived
        FILL,           // Re-place mined blocks behind — then goes back to MINE
        RESTOCK_CLEAR, RESTOCK_PLACE, RESTOCK_WAIT,
        RESTOCK_OPEN, RESTOCK_LOOT, RESTOCK_CLOSE,
        RESTOCK_BREAK, RESTOCK_PICKUP,
        DONE
    }

    private enum HazardCondition {
        NONE("none"),
        SUFFOCATING("suffocating"),
        SWIMMING("swimming"),
        DROWNING("drowning"),
        BURNING("burning");

        private final String id;

        HazardCondition(String id) {
            this.id = id;
        }
    }

    private enum GoalMode {
        EXACT("exact"),
        FALLBACK("fallback"),
        UNREACHABLE("unreachable");

        private final String id;

        GoalMode(String id) {
            this.id = id;
        }
    }

    private Phase phase;

    private record PathStep(int fromX, int fromZ, int toX, int toZ, int stepX, int stepZ) {}

    private record AStarRequest(
        int startX,
        int startZ,
        int py,
        int destX,
        int destZ,
        boolean rejectAirGapDetours,
        int radius,
        int nodeLimit
    ) {}

    private record AStarResult(int stepX, int stepZ, String summary) {}

    private static final class AStarSnapshot {
        private final int minX;
        private final int minZ;
        private final int width;
        private final int depth;
        private final boolean[] columnTraversable;
        private final boolean[] obstacleCell;
        private final boolean[] floorUnsafe;

        private AStarSnapshot(int minX, int minZ, int width, int depth, boolean[] columnTraversable, boolean[] obstacleCell, boolean[] floorUnsafe) {
            this.minX = minX;
            this.minZ = minZ;
            this.width = width;
            this.depth = depth;
            this.columnTraversable = columnTraversable;
            this.obstacleCell = obstacleCell;
            this.floorUnsafe = floorUnsafe;
        }

        private boolean inBounds(int x, int z) {
            return x >= minX && z >= minZ && x < minX + width && z < minZ + depth;
        }

        private int idx(int x, int z) {
            return (z - minZ) * width + (x - minX);
        }

        private boolean isColumnTraversable(int x, int z) {
            if (!inBounds(x, z)) return false;
            return columnTraversable[idx(x, z)];
        }

        private boolean isObstacleCell(int x, int z) {
            if (!inBounds(x, z)) return true;
            return obstacleCell[idx(x, z)];
        }

        private boolean isFloorUnsafe(int x, int z) {
            if (!inBounds(x, z)) return true;
            return floorUnsafe[idx(x, z)];
        }
    }
    private record RestockPlacement(BlockPos containerPos, int forwardStepX, int forwardStepZ) {}
    private record GoalResolution(int x, int z, GoalMode mode, String reason) {}

    // Requested destination (identity) and active navigation goal.
    private int requestedDestX, requestedDestZ;
    private int destX, destZ, totalBlocks;
    private int activeGoalX, activeGoalZ;
    private GoalMode goalMode = GoalMode.EXACT;
    private String goalReason = "";
    private int fallbackSearchCachedPx = Integer.MIN_VALUE;
    private int fallbackSearchCachedPz = Integer.MIN_VALUE;
    private int fallbackSearchCachedPy = Integer.MIN_VALUE;
    private int fallbackSearchCachedDestX = Integer.MIN_VALUE;
    private int fallbackSearchCachedDestZ = Integer.MIN_VALUE;
    private boolean fallbackSearchCachedRejectAirGaps;
    private int fallbackSearchCachedAge = Integer.MIN_VALUE;
    private int fallbackSearchResultX = Integer.MIN_VALUE;
    private int fallbackSearchResultZ = Integer.MIN_VALUE;
    private GoalMode fallbackSearchResultMode = GoalMode.UNREACHABLE;
    private String fallbackSearchResultReason = "";
    private int tunnelY;

    // Which axis we are currently on (X first, then Z)
    private boolean onXAxis;

    // Walk target set each time we enter WALK
    private double walkTargetX, walkTargetZ;

    // Fill log: original block states of blocks we mined, keyed by position
    private final LinkedHashMap<BlockPos, BlockState> fillLog = new LinkedHashMap<>();
    private final LinkedHashMap<BlockPos, BlockState> stealthCache = new LinkedHashMap<>();

    // Timers
    private int placeTimer, invTimer, waitTicks;
    private int restockPlaceRetries;
    private int stealthBreakTimer;
    private static final int MAX_WAIT = 100;
    private static final int MAX_RESTOCK_PLACE_RETRIES = 3;

    // Inventory
    private int pickSlot = -1;
    private BlockPos containerPos;
    private BlockPos restockCleanupPos;
    private boolean restockEC;
    private boolean restockFailOutOfPickaxes;
    private boolean restockPreferShulkerNext;
    private boolean restockEcSearchExhausted;
    private boolean restockEcExtractedPickShulker;

    // Stats for HUD
    private long startMs;
    private int blocksMined;
    private long watchdogSequence;
    private static final DateTimeFormatter WATCHDOG_TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private String watchdogStealthGate = "unset";
    private String watchdogStealthGateDetails = "";
    private String watchdogProbeSummary = "";
    private String watchdogMineSummary = "";
    private String watchdogRestoreSummary = "";
    private String watchdogDetourSummary = "";
    private String watchdogTraverseFail = "";
    private String watchdogAStarSummary = "";
    private boolean watchdogAStarPending;
    private int watchdogSamePosTicks;
    private int watchdogNoProgressTicks;
    private int watchdogLastX = Integer.MIN_VALUE;
    private int watchdogLastZ = Integer.MIN_VALUE;
    private int watchdogLastBlocksLeft = Integer.MIN_VALUE;
    private int watchdogLastBlocksMined = Integer.MIN_VALUE;
    private int watchdogLastTelemetryAge = Integer.MIN_VALUE;
    private int watchdogPendingCacheAge = Integer.MIN_VALUE;
    private int watchdogCachedPendingFar = -1;
    private int watchdogCachedPendingNear = -1;
    private final StringBuilder watchdogWriteBuffer = new StringBuilder(65_536);
    private int watchdogWriteBufferLines;

    private final List<BlockPos> renderBreakPositions = new ArrayList<>();
    private final List<BlockPos> renderPlacePositions = new ArrayList<>();
    private final ArrayList<PathStep> renderPathPreviewSteps = new ArrayList<>();
    private final Set<BlockPos> activeProbePositions = new HashSet<>();
    private final List<BlockPos> activeMineTargets = new ArrayList<>();
    private StealthDoubleMineBlock normalMining;
    private StealthDoubleMineBlock packetMining;
    private FreeLook freeLookModule;
    private boolean managedFreeLook;
    private final Set<Block> protectedStateBlocks = new HashSet<>();
    private final Set<Block> noTouchChainBlocks = new HashSet<>();
    private final HashMap<Long, Integer> detourVisitCounts = new HashMap<>();
    private Future<AStarResult> pendingAStar;
    private AStarRequest pendingAStarRequest;
    private final ArrayList<PathStep> cachedProbePath = new ArrayList<>();
    private int cachedProbeStartX = Integer.MIN_VALUE;
    private int cachedProbeStartZ = Integer.MIN_VALUE;
    private int cachedProbeY = Integer.MIN_VALUE;
    private int cachedProbeDestX = Integer.MIN_VALUE;
    private int cachedProbeDestZ = Integer.MIN_VALUE;
    private int cachedProbeRadius = Integer.MIN_VALUE;
    private int cachedProbeLastReplanAge = Integer.MIN_VALUE;
    private int cachedProbeLastEmptyReplanAge = Integer.MIN_VALUE;
    private boolean cachedProbeOnXAxis;
    private boolean cachedProbeRejectAirGaps;
    private boolean cachedProbeEmergencyNoScaffold;
    private PathMode cachedProbePathMode = HARD_PATH_MODE;
    private int noProbePauseTicks;
    private int emergencyAirFallbackTicksLeft;
    private int emergencyAirFallbackBlocksLeft;
    private int probeAStarFailStreak;
    private boolean probeWallFollowActive;
    private int wallFollowMLineStartX = Integer.MIN_VALUE;
    private int wallFollowMLineStartZ = Integer.MIN_VALUE;
    private double wallFollowHitDistance = Double.POSITIVE_INFINITY;
    private int wallFollowHeading = 0;
    private int wallFollowRerouteBlocks;
    private HazardCondition hazardRecoveryCondition = HazardCondition.NONE;
    private int hazardRecoveryTicks;
    private int proactiveAStarCacheFromX = Integer.MIN_VALUE;
    private int proactiveAStarCacheFromZ = Integer.MIN_VALUE;
    private int proactiveAStarCachePy = Integer.MIN_VALUE;
    private int proactiveAStarCacheDestX = Integer.MIN_VALUE;
    private int proactiveAStarCacheDestZ = Integer.MIN_VALUE;
    private boolean proactiveAStarCacheAxisMode;
    private int proactiveAStarCacheAge = Integer.MIN_VALUE;
    private int proactiveAStarCacheStepX;
    private int proactiveAStarCacheStepZ;
    private String proactiveAStarCacheSummary = "";
    private PathStep committedMoveStep;
    private int prevVisitedX = Integer.MIN_VALUE;
    private int prevVisitedZ = Integer.MIN_VALUE;
    private int lastVisitedX = Integer.MIN_VALUE;
    private int lastVisitedZ = Integer.MIN_VALUE;
    private boolean resumeStateAvailable;
    private int resumeDestX, resumeDestZ;
    private int resumePauseX, resumePauseZ;
    private boolean resumePausePosValid;
    private boolean resumeOnXAxis;
    private boolean resumeStealthMode;
    private int resumeTunnelHeight;
    private int resumeTunnelY;
    private boolean resumeFromCompletedRun;
    private SettingsSource settingsSource = SettingsSource.MANUAL;
    private boolean apiModeActive;
    private EffectiveOptions effectiveOptions;
    private ApiGoToRequest pendingApiGoToRequest;
    private SpeedSnapshot speedSnapshot;
    private boolean speedSnapshotOwned;
    private boolean speedOverrideActive;
    private boolean stealthSpeedWasEnabled;
    private String speedSnapshotLastReason = "";
    // ── Constructor ───────────────────────────────────────────────────────────

    public TunnelMinerModule() {
        super(THMAddon.MAIN, "tunnel-miner",
            "Mines a tunnel block-by-block to target XZ coordinates at the same Y.");
        INSTANCE = this;
        verifyEffectiveOptionsCoverage();
        installManualEffectiveOptions();
        loadSpeedSnapshotFromDisk();
    }

    private void verifyEffectiveOptionsCoverage() {
        Set<String> discovered = new HashSet<>();
        for (Field field : TunnelMinerModule.class.getDeclaredFields()) {
            if (Setting.class.isAssignableFrom(field.getType())) discovered.add(field.getName());
        }

        Set<String> unmapped = new TreeSet<>(discovered);
        unmapped.removeAll(EFFECTIVE_OPTION_SETTING_FIELDS);
        unmapped.removeAll(NON_EFFECTIVE_OPTION_SETTING_FIELDS);
        if (!unmapped.isEmpty()) {
            throw new IllegalStateException("TunnelMiner EffectiveOptions mapping missing settings: " + unmapped);
        }

        Set<String> expected = new HashSet<>(EFFECTIVE_OPTION_SETTING_FIELDS);
        expected.addAll(NON_EFFECTIVE_OPTION_SETTING_FIELDS);
        Set<String> stale = new TreeSet<>(expected);
        stale.removeAll(discovered);
        if (!stale.isEmpty()) {
            throw new IllegalStateException("TunnelMiner EffectiveOptions mapping references unknown settings: " + stale);
        }
    }

    private void installManualEffectiveOptions() {
        settingsSource = SettingsSource.MANUAL;
        apiModeActive = false;
        effectiveOptions = buildManualEffectiveOptions();
    }

    private void installApiEffectiveOptions(int x, int z, int stealthMode, boolean renderingEnabled) {
        settingsSource = SettingsSource.API;
        apiModeActive = true;
        effectiveOptions = buildApiEffectiveOptions(x, z, stealthMode, renderingEnabled);
    }

    private void resetApiOwnershipAfterStop() {
        pendingApiGoToRequest = null;
        apiModeActive = false;
        settingsSource = SettingsSource.MANUAL;
        effectiveOptions = null;
    }

    private EffectiveOptions getEffectiveOptions() {
        if (effectiveOptions == null) {
            installManualEffectiveOptions();
        } else if (!apiModeActive && !isActive()) {
            // Keep manual previews/HUD reads current while the module is idle.
            effectiveOptions = buildManualEffectiveOptions();
        }
        return effectiveOptions;
    }

    private EffectiveOptions buildManualEffectiveOptions() {
        return new EffectiveOptions(
            targetX.get(),
            targetZ.get(),
            tunnelHeight.get(),
            fillBehind.get(),
            List.copyOf(fillBlocks.get()),
            lavaAvoidance.get(),
            airPlace.get(),
            airPlaceDistance.get(),
            autoFreeLook.get(),
            resumeCacheOnReactivate.get(),
            debugMessages.get(),
            stealthMode.get(),
            stealthProbeDistance.get(),
            stealthMineAheadDistance.get(),
            stealthStopRange.get(),
            stealthDoubleMine.get(),
            stealthFastBreak.get(),
            stealthRestoreLagDistance.get(),
            strictStealthExactRestore.get(),
            watchdogEnabled.get(),
            useShulkers.get(),
            useEnderChest.get(),
            minPickaxes.get(),
            breaksPerTick.get(),
            stealthBreakDelay.get(),
            placesPerTick.get(),
            placeDelay.get(),
            invDelay.get(),
            shapeMode.get(),
            renderMining.get(),
            renderPlacing.get(),
            renderPath.get(),
            renderPathSteps.get(),
            copyColor(breakSideColor.get()),
            copyColor(breakLineColor.get()),
            copyColor(placeSideColor.get()),
            copyColor(placeLineColor.get()),
            copyColor(pathSideColor.get()),
            copyColor(pathLineColor.get())
        );
    }

    private static boolean isSupportedApiStealthMode(int stealthMode) {
        return stealthMode == API_STEALTH_OFF || stealthMode == API_STEALTH_ON;
    }

    private static boolean resolveApiStealthEnabled(int stealthMode) {
        return stealthMode == API_STEALTH_ON;
    }

    private EffectiveOptions buildApiEffectiveOptions(int x, int z, int stealthMode, boolean renderingEnabled) {
        boolean stealthEnabled = resolveApiStealthEnabled(stealthMode);
        ApiModePreset preset = stealthEnabled ? API_PRESET_STEALTH_ON : API_PRESET_STEALTH_OFF;
        return new EffectiveOptions(
            x,
            z,
            preset.tunnelHeight(),
            preset.fillBehind(),
            List.copyOf(preset.fillBlocks()),
            preset.lavaAvoidance(),
            preset.airPlace(),
            preset.airPlaceDistance(),
            preset.autoFreeLook(),
            preset.resumeCacheOnReactivate(),
            preset.debugMessages(),
            stealthEnabled,
            preset.stealthProbeDistance(),
            preset.stealthMineAheadDistance(),
            preset.stealthStopRange(),
            preset.stealthDoubleMine(),
            preset.stealthFastBreak(),
            preset.stealthRestoreLagDistance(),
            preset.strictStealthExactRestore(),
            preset.watchdogEnabled(),
            preset.useShulkers(),
            preset.useEnderChest(),
            preset.minPickaxes(),
            preset.breaksPerTick(),
            preset.stealthBreakDelay(),
            preset.placesPerTick(),
            preset.placeDelay(),
            preset.invDelay(),
            preset.shapeMode(),
            renderingEnabled,
            renderingEnabled,
            renderingEnabled,
            preset.renderPathSteps(),
            copyColor(preset.breakSideColor()),
            copyColor(preset.breakLineColor()),
            copyColor(preset.placeSideColor()),
            copyColor(preset.placeLineColor()),
            copyColor(preset.pathSideColor()),
            copyColor(preset.pathLineColor())
        );
    }

    private static SettingColor copyColor(SettingColor color) {
        return color == null ? new SettingColor(255, 255, 255, 255) : new SettingColor(color);
    }

    private int optTargetX() { return getEffectiveOptions().targetX(); }
    private int optTargetZ() { return getEffectiveOptions().targetZ(); }
    private int optTunnelHeight() { return getEffectiveOptions().tunnelHeight(); }
    private boolean optFillBehind() { return getEffectiveOptions().fillBehind(); }
    private List<Block> optFillBlocks() { return getEffectiveOptions().fillBlocks(); }
    private boolean optLavaAvoidance() { return getEffectiveOptions().lavaAvoidance(); }
    private boolean optAirPlace() { return getEffectiveOptions().airPlace(); }
    private int optAirPlaceDistance() { return getEffectiveOptions().airPlaceDistance(); }
    private boolean optAutoFreeLook() { return getEffectiveOptions().autoFreeLook(); }
    private boolean optResumeCacheOnReactivate() { return getEffectiveOptions().resumeCacheOnReactivate(); }
    private boolean optDebugMessages() { return getEffectiveOptions().debugMessages(); }
    private boolean optStealthMode() { return getEffectiveOptions().stealthMode(); }
    private int optStealthProbeDistance() { return getEffectiveOptions().stealthProbeDistance(); }
    private int optStealthMineAheadDistance() { return getEffectiveOptions().stealthMineAheadDistance(); }
    private double optStealthStopRange() { return getEffectiveOptions().stealthStopRange(); }
    private boolean optStealthDoubleMine() { return getEffectiveOptions().stealthDoubleMine(); }
    private boolean optStealthFastBreak() { return getEffectiveOptions().stealthFastBreak(); }
    private int optStealthRestoreLagDistance() { return getEffectiveOptions().stealthRestoreLagDistance(); }
    private boolean optStrictStealthExactRestore() { return getEffectiveOptions().strictStealthExactRestore(); }
    private boolean optWatchdogEnabled() { return getEffectiveOptions().watchdogEnabled(); }
    private boolean optUseShulkers() { return getEffectiveOptions().useShulkers(); }
    private boolean optUseEnderChest() { return getEffectiveOptions().useEnderChest(); }
    private int optMinPickaxes() { return getEffectiveOptions().minPickaxes(); }
    private int optBreaksPerTick() { return getEffectiveOptions().breaksPerTick(); }
    private int optStealthBreakDelay() { return getEffectiveOptions().stealthBreakDelay(); }
    private int optPlacesPerTick() { return getEffectiveOptions().placesPerTick(); }
    private int optPlaceDelay() { return getEffectiveOptions().placeDelay(); }
    private int optInvDelay() { return getEffectiveOptions().invDelay(); }
    private ShapeMode optShapeMode() { return getEffectiveOptions().shapeMode(); }
    private boolean optRenderMining() { return getEffectiveOptions().renderMining(); }
    private boolean optRenderPlacing() { return getEffectiveOptions().renderPlacing(); }
    private boolean optRenderPath() { return getEffectiveOptions().renderPath(); }
    private int optRenderPathSteps() { return getEffectiveOptions().renderPathSteps(); }
    private SettingColor optBreakSideColor() { return getEffectiveOptions().breakSideColor(); }
    private SettingColor optBreakLineColor() { return getEffectiveOptions().breakLineColor(); }
    private SettingColor optPlaceSideColor() { return getEffectiveOptions().placeSideColor(); }
    private SettingColor optPlaceLineColor() { return getEffectiveOptions().placeLineColor(); }
    private SettingColor optPathSideColor() { return getEffectiveOptions().pathSideColor(); }
    private SettingColor optPathLineColor() { return getEffectiveOptions().pathLineColor(); }

    private boolean isStealthEnabledForSpeedOwnership() {
        if (settingsSource == SettingsSource.API || apiModeActive) return optStealthMode();
        return stealthMode.get();
    }

    private void tryApplyStealthSpeedControl(String reason) {
        if (!isStealthEnabledForSpeedOwnership()) return;
        if (!ensureSpeedSnapshotCapturedIfNeeded(reason)) return;
        applyStealthSpeedOverride(reason);
    }

    private boolean ensureSpeedSnapshotCapturedIfNeeded(String reason) {
        if (speedSnapshotOwned && speedSnapshot != null) return true;
        if (speedSnapshotOwned && speedSnapshot == null) clearSpeedSnapshotState("owned-without-snapshot", true);

        Speed speed = Modules.get().get(Speed.class);
        if (speed == null) {
            if (optDebugMessages()) warning("Speed snapshot capture skipped: Speed module not found.");
            speedSnapshotLastReason = "capture-missing-speed:" + reason;
            return false;
        }

        speedSnapshot = new SpeedSnapshot(
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
        speedSnapshotOwned = true;
        speedSnapshotLastReason = "captured:" + reason;
        persistSpeedSnapshotToDisk();
        return true;
    }

    private void applyStealthSpeedOverride(String reason) {
        Speed speed = Modules.get().get(Speed.class);
        if (speed == null) {
            if (optDebugMessages()) warning("Speed override skipped: Speed module not found.");
            speedSnapshotLastReason = "override-missing-speed:" + reason;
            return;
        }

        speed.speedMode.set(SpeedModes.Vanilla);
        speed.vanillaSpeed.set(4.0);
        speed.timer.set(1.0);
        speed.inLiquids.set(false);
        speed.whenSneaking.set(false);
        speed.vanillaOnGround.set(false);
        if (!speed.isActive()) speed.toggle();
        speedOverrideActive = true;
        speedSnapshotLastReason = "override-applied:" + reason;
    }

    private void restoreSpeedFromSnapshotIfOwned(String reason) {
        if (!speedSnapshotOwned || speedSnapshot == null) return;

        Speed speed = Modules.get().get(Speed.class);
        if (speed == null) {
            if (optDebugMessages()) warning("Speed restore deferred: Speed module not found.");
            speedSnapshotLastReason = "restore-missing-speed:" + reason;
            return;
        }

        try {
            speed.speedMode.set(parseSpeedModeOrDefault(speedSnapshot.speedModeName()));
            speed.vanillaSpeed.set(speedSnapshot.vanillaSpeed());
            speed.ncpSpeed.set(speedSnapshot.ncpSpeed());
            speed.ncpSpeedLimit.set(speedSnapshot.ncpSpeedLimit());
            speed.timer.set(speedSnapshot.timer());
            speed.inLiquids.set(speedSnapshot.inLiquids());
            speed.whenSneaking.set(speedSnapshot.whenSneaking());
            speed.vanillaOnGround.set(speedSnapshot.vanillaOnGround());

            boolean active = speed.isActive();
            if (speedSnapshot.wasActive() && !active) speed.toggle();
            else if (!speedSnapshot.wasActive() && active) speed.toggle();

            clearSpeedSnapshotState("restored:" + reason, true);
        } catch (Exception e) {
            speedSnapshotLastReason = "restore-error:" + e.getClass().getSimpleName();
            if (optDebugMessages()) warning("Speed restore failed; keeping snapshot for retry. " + e.getMessage());
        }
    }

    private SpeedModes parseSpeedModeOrDefault(String value) {
        if (value == null || value.isBlank()) return SpeedModes.Vanilla;
        try {
            return SpeedModes.valueOf(value.trim());
        } catch (IllegalArgumentException ignored) {
            return SpeedModes.Vanilla;
        }
    }

    private void clearSpeedSnapshotState(String reason, boolean deleteFile) {
        speedSnapshotOwned = false;
        speedSnapshot = null;
        speedOverrideActive = false;
        speedSnapshotLastReason = reason == null ? "" : reason;
        if (!deleteFile) return;

        Path path = resolveSpeedSnapshotPath();
        try {
            if (Files.exists(path)) Files.delete(path);
        } catch (IOException e) {
            if (optDebugMessages()) warning("Failed deleting speed snapshot file (" + path + "): " + e.getMessage());
        }
    }

    private void persistSpeedSnapshotToDisk() {
        Path path = resolveSpeedSnapshotPath();
        if (!speedSnapshotOwned || speedSnapshot == null) {
            clearSpeedSnapshotState("persist-clear", true);
            return;
        }

        try {
            Path parent = path.getParent();
            if (parent != null) Files.createDirectories(parent);

            StringBuilder out = new StringBuilder(1024);
            out.append(SPEED_SNAPSHOT_MAGIC).append('\n');
            appendResumeMeta(out, "speedMode", speedSnapshot.speedModeName());
            appendResumeMeta(out, "vanillaSpeed", speedSnapshot.vanillaSpeed());
            appendResumeMeta(out, "ncpSpeed", speedSnapshot.ncpSpeed());
            appendResumeMeta(out, "ncpSpeedLimit", speedSnapshot.ncpSpeedLimit());
            appendResumeMeta(out, "timer", speedSnapshot.timer());
            appendResumeMeta(out, "inLiquids", speedSnapshot.inLiquids());
            appendResumeMeta(out, "whenSneaking", speedSnapshot.whenSneaking());
            appendResumeMeta(out, "vanillaOnGround", speedSnapshot.vanillaOnGround());
            appendResumeMeta(out, "wasActive", speedSnapshot.wasActive());
            appendResumeMeta(out, "savedAt", System.currentTimeMillis());

            Path tmp = path.resolveSibling(path.getFileName().toString() + ".tmp");
            Files.writeString(tmp, out.toString(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            try {
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            speedSnapshotLastReason = "persist-error";
            if (optDebugMessages()) warning("Failed writing speed snapshot file (" + path + "): " + e.getMessage());
        }
    }

    private void loadSpeedSnapshotFromDisk() {
        Path path = resolveSpeedSnapshotPath();
        if (!Files.exists(path)) return;

        HashMap<String, String> meta = new HashMap<>();
        try {
            List<String> lines = Files.readAllLines(path);
            if (lines.isEmpty() || !SPEED_SNAPSHOT_MAGIC.equals(lines.get(0).trim())) {
                clearSpeedSnapshotState("load-invalid-magic", true);
                return;
            }

            for (int i = 1; i < lines.size(); i++) {
                String line = lines.get(i);
                if (line == null) continue;
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] parts = line.split("\\|", 3);
                if (parts.length < 3 || !"meta".equals(parts[0])) continue;
                meta.put(parts[1], parts[2]);
            }

            speedSnapshot = new SpeedSnapshot(
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
            speedSnapshotOwned = true;
            speedOverrideActive = false;
            speedSnapshotLastReason = "loaded-disk";
        } catch (Exception e) {
            speedSnapshotLastReason = "load-error";
            clearSpeedSnapshotState("load-error", true);
        }
    }

    private Path resolveSpeedSnapshotPath() {
        return MeteorClient.FOLDER.toPath()
            .resolve("thm")
            .resolve("tunnelminer-speed-snapshot.txt");
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    public void onActivate() {
        if (mc.player == null) return;
        ApiGoToRequest pendingRequest = pendingApiGoToRequest;
        pendingApiGoToRequest = null;
        if (pendingRequest != null) {
            installApiEffectiveOptions(
                pendingRequest.x(),
                pendingRequest.z(),
                pendingRequest.stealthMode(),
                pendingRequest.renderingEnabled()
            );
        } else {
            installManualEffectiveOptions();
        }
        stealthSpeedWasEnabled = isStealthEnabledForSpeedOwnership();
        if (stealthSpeedWasEnabled) tryApplyStealthSpeedControl("activate");
        flushWatchdogBuffer();
        watchdogWriteBuffer.setLength(0);
        watchdogWriteBufferLines = 0;
        requestedDestX = optTargetX();
        requestedDestZ = optTargetZ();
        int px = MathHelper.floor(mc.player.getX());
        int py = MathHelper.floor(mc.player.getY());
        int pz = MathHelper.floor(mc.player.getZ());

        if (!optResumeCacheOnReactivate()) {
            clearResumeCacheState("setting-disabled");
        } else {
            loadResumeCacheFromDisk();
        }

        boolean hasResumeEntries = resumeStateAvailable && (!fillLog.isEmpty() || !stealthCache.isEmpty());
        boolean pendingRestoreInRange = true;
        if (hasResumeEntries && shouldMaintainBehindParity()) {
            pendingRestoreInRange = hasAnyPendingRestoreWithinDistance(4.5);
            if (!pendingRestoreInRange) {
                clearResumeCacheState("out-of-restore-range-4.5");
                hasResumeEntries = false;
            }
        }

        // Destination changes should not invalidate restore cache.
        // We only treat core execution context (mode/shape/y/pause position) as mismatch.
        boolean contextMatch = !hasResumeEntries || (
            resumeStealthMode == optStealthMode()
                && resumeTunnelHeight == optTunnelHeight()
                && resumeTunnelY == py
                && resumePausePosValid
        );

        boolean allowCompletedMismatchCarryover = hasResumeEntries
            && !contextMatch
            && resumeFromCompletedRun
            && shouldMaintainBehindParity()
            && pendingRestoreInRange;

        if (hasResumeEntries && !contextMatch && !allowCompletedMismatchCarryover) {
            clearResumeCacheState("mismatch");
            hasResumeEntries = false;
        }

        boolean canResume = optResumeCacheOnReactivate()
            && hasResumeEntries
            && contextMatch
            && resumePausePosValid;
        boolean keepCarryoverCache = optResumeCacheOnReactivate()
            && hasResumeEntries
            && !contextMatch
            && allowCompletedMismatchCarryover;
        String resumeMode = canResume ? "resume-path" : (keepCarryoverCache ? "carryover-cache" : "fresh-start");

        destX = requestedDestX;
        destZ = requestedDestZ;
        activeGoalX = destX;
        activeGoalZ = destZ;
        goalMode = GoalMode.EXACT;
        goalReason = "activate";
        fallbackSearchCachedPx = Integer.MIN_VALUE;
        fallbackSearchCachedPz = Integer.MIN_VALUE;
        fallbackSearchCachedPy = Integer.MIN_VALUE;
        fallbackSearchCachedDestX = Integer.MIN_VALUE;
        fallbackSearchCachedDestZ = Integer.MIN_VALUE;
        fallbackSearchCachedAge = Integer.MIN_VALUE;
        fallbackSearchResultMode = GoalMode.UNREACHABLE;
        fallbackSearchResultReason = "";
        tunnelY = canResume ? resumeTunnelY : py;
        onXAxis = canResume ? resumeOnXAxis : true;
        if (!canResume && !keepCarryoverCache) {
            fillLog.clear();
            stealthCache.clear();
            resumeStateAvailable = false;
            clearResumeCacheFile();
        }
        renderBreakPositions.clear();
        activeProbePositions.clear();
        activeMineTargets.clear();
        cachedProbePath.clear();
        cachedProbeStartX = Integer.MIN_VALUE;
        cachedProbeStartZ = Integer.MIN_VALUE;
        cachedProbeY = Integer.MIN_VALUE;
        cachedProbeDestX = Integer.MIN_VALUE;
        cachedProbeDestZ = Integer.MIN_VALUE;
        cachedProbeRadius = Integer.MIN_VALUE;
        cachedProbeLastReplanAge = Integer.MIN_VALUE;
        cachedProbeLastEmptyReplanAge = Integer.MIN_VALUE;
        cachedProbeOnXAxis = onXAxis;
        cachedProbeRejectAirGaps = useStealthAvoidAirGapDetours();
        cachedProbeEmergencyNoScaffold = false;
        cachedProbePathMode = hardPathMode();
        noProbePauseTicks = 0;
        emergencyAirFallbackTicksLeft = 0;
        emergencyAirFallbackBlocksLeft = 0;
        emergencyAirFallbackTicksLeft = 0;
        emergencyAirFallbackBlocksLeft = 0;
        probeAStarFailStreak = 0;
        probeWallFollowActive = false;
        wallFollowMLineStartX = Integer.MIN_VALUE;
        wallFollowMLineStartZ = Integer.MIN_VALUE;
        wallFollowHitDistance = Double.POSITIVE_INFINITY;
        wallFollowHeading = 0;
        wallFollowRerouteBlocks = 0;
        hazardRecoveryCondition = HazardCondition.NONE;
        hazardRecoveryTicks = 0;
        placeTimer = invTimer = waitTicks = 0;
        restockPlaceRetries = 0;
        stealthBreakTimer = 0;
        detourVisitCounts.clear();
        proactiveAStarCacheFromX = Integer.MIN_VALUE;
        proactiveAStarCacheFromZ = Integer.MIN_VALUE;
        proactiveAStarCachePy = Integer.MIN_VALUE;
        proactiveAStarCacheDestX = Integer.MIN_VALUE;
        proactiveAStarCacheDestZ = Integer.MIN_VALUE;
        proactiveAStarCacheAge = Integer.MIN_VALUE;
        proactiveAStarCacheStepX = 0;
        proactiveAStarCacheStepZ = 0;
        proactiveAStarCacheSummary = "";
        committedMoveStep = null;
        prevVisitedX = Integer.MIN_VALUE;
        prevVisitedZ = Integer.MIN_VALUE;
        lastVisitedX = Integer.MIN_VALUE;
        lastVisitedZ = Integer.MIN_VALUE;
        pickSlot = -1;
        containerPos = null;
        restockCleanupPos = null;
        resetRestockStageState();
        restockPlaceRetries = 0;
        startMs = System.currentTimeMillis();
        blocksMined = 0;
        watchdogSequence = 0;
        resetWatchdogTelemetry();
        reloadAvoidanceBlockSets();
        managedFreeLook = false;
        freeLookModule = Modules.get().get(FreeLook.class);
        if (optAutoFreeLook() && freeLookModule != null && !freeLookModule.isActive()) {
            freeLookModule.toggle();
            managedFreeLook = freeLookModule.isActive();
        }
        clearStealthMining(false);
        totalBlocks = blocksLeftFrom(px, pz);
        phase = Phase.INIT;
        if (optDebugMessages()) {
            if (canResume) info("Resuming tunnel to X=" + requestedDestX + " Z=" + requestedDestZ + " (" + totalBlocks + " blocks left)");
            else if (keepCarryoverCache) info("Starting tunnel with carryover restore cache to X=" + requestedDestX + " Z=" + requestedDestZ + " (" + totalBlocks + " blocks)");
            else info("Start tunnel to X=" + requestedDestX + " Z=" + requestedDestZ + " (" + totalBlocks + " blocks)");
        }
        watchdog("onActivate", String.format(Locale.ROOT, "targetX=%d,targetZ=%d,targetY=%d,totalBlocks=%d,resumeMode=%s", requestedDestX, requestedDestZ, tunnelY, totalBlocks, resumeMode));

    }

    @Override
    public void onDeactivate() {
        if (mc.options != null) mc.options.forwardKey.setPressed(false);
        renderBreakPositions.clear();
        renderPlacePositions.clear();
        renderPathPreviewSteps.clear();
        activeProbePositions.clear();
        activeMineTargets.clear();
        noProbePauseTicks = 0;
        cachedProbeEmergencyNoScaffold = false;
        wallFollowRerouteBlocks = 0;
        emergencyAirFallbackTicksLeft = 0;
        emergencyAirFallbackBlocksLeft = 0;
        hazardRecoveryCondition = HazardCondition.NONE;
        hazardRecoveryTicks = 0;
        containerPos = null;
        restockCleanupPos = null;
        resetRestockStageState();
        committedMoveStep = null;
        clearStealthMining(true);
        if (pendingAStar != null) {
            pendingAStar.cancel(true);
            pendingAStar = null;
            pendingAStarRequest = null;
        }
        if (mc.player != null) {
            resumePauseX = MathHelper.floor(mc.player.getX());
            resumePauseZ = MathHelper.floor(mc.player.getZ());
            resumePausePosValid = true;
        }
        resumeDestX = requestedDestX;
        resumeDestZ = requestedDestZ;
        resumeOnXAxis = onXAxis;
        resumeStealthMode = optStealthMode();
        resumeTunnelHeight = optTunnelHeight();
        resumeTunnelY = tunnelY;
        resumeFromCompletedRun = phase == Phase.DONE;
        resumeStateAvailable = (!fillLog.isEmpty() || !stealthCache.isEmpty());
        saveResumeCacheToDisk();
        restoreSpeedFromSnapshotIfOwned(phase == Phase.DONE ? "done" : "stopped");
        if (managedFreeLook && freeLookModule != null && freeLookModule.isActive()) freeLookModule.toggle();
        managedFreeLook = false;
        stealthSpeedWasEnabled = false;
        if (optDebugMessages()) {info("Stopped.");}
        watchdog("onDeactivate");
        flushWatchdogBuffer();
        resetApiOwnershipAfterStop();

    }

    @EventHandler
    private void onGameLeft(GameLeftEvent e) {
        if (mc.player != null) {
            resumePauseX = MathHelper.floor(mc.player.getX());
            resumePauseZ = MathHelper.floor(mc.player.getZ());
            resumePausePosValid = true;
        }
        watchdog("onGameLeft");
        flushWatchdogBuffer();
        if (isActive()) toggle();
    }

    // ── Main tick ─────────────────────────────────────────────────────────────

    private boolean shouldPauseForEating() {
        AutoEat autoEat = Modules.get().get(AutoEat.class);
        boolean autoEatEating = autoEat != null && autoEat.isActive() && autoEat.eating;

        AutoGap autoGap = Modules.get().get(AutoGap.class);
        boolean autoGapEating = autoGap != null && autoGap.isActive() && autoGap.isEating();

        OffhandManager offhandManager = Modules.get().get(OffhandManager.class);
        boolean offhandEating = offhandManager != null && offhandManager.isEating();

        boolean playerUsingFood = mc.player != null
            && mc.player.isUsingItem()
            && mc.player.getActiveItem().contains(DataComponentTypes.FOOD);

        boolean pause = autoEatEating || autoGapEating || offhandEating || playerUsingFood;
        if (pause) {
            watchdogStealthGate = "pause-eating";
            watchdogStealthGateDetails = String.format(
                Locale.ROOT,
                "autoEat=%s,autoGap=%s,offhand=%s,usingFood=%s",
                autoEatEating,
                autoGapEating,
                offhandEating,
                playerUsingFood
            );
        }

        return pause;
    }

    private boolean handleHazardRecovery(long tickStartNs) {
        HazardCondition hazard = detectCurrentHazard();
        if (hazard == HazardCondition.NONE) {
            if (hazardRecoveryCondition != HazardCondition.NONE || hazardRecoveryTicks > 0) {
                watchdog(
                    "hazard-recovery-cleared",
                    String.format(
                        Locale.ROOT,
                        "lastHazard=%s,recoveryTicks=%d,air=%d,maxAir=%d",
                        hazardRecoveryCondition.id,
                        hazardRecoveryTicks,
                        mc.player.getAir(),
                        mc.player.getMaxAir()
                    )
                );
            }
            hazardRecoveryCondition = HazardCondition.NONE;
            hazardRecoveryTicks = 0;
            return false;
        }

        if (hazardRecoveryCondition != hazard) {
            watchdog(
                "hazard-recovery-switch",
                String.format(
                    Locale.ROOT,
                    "from=%s,to=%s,air=%d,maxAir=%d",
                    hazardRecoveryCondition.id,
                    hazard.id,
                    mc.player.getAir(),
                    mc.player.getMaxAir()
                )
            );
            hazardRecoveryCondition = hazard;
            hazardRecoveryTicks = 0;
        }

        hazardRecoveryTicks++;
        if (mc.options != null) mc.options.forwardKey.setPressed(false);
        boolean swimming = mc.player.isSwimming();
        boolean swimmingPose = mc.player.getPose() == EntityPose.SWIMMING;
        watchdogStealthGate = "pause-hazard-recovery";
        watchdogStealthGateDetails = String.format(
            Locale.ROOT,
            "hazard=%s,recoveryTick=%d,max=%d",
            hazard.id,
            hazardRecoveryTicks,
            HAZARD_RECOVERY_MAX_TICKS
        );
        watchdog(
            "hazard-detected",
            String.format(
                Locale.ROOT,
                "hazard=%s,recoveryTick=%d,max=%d,air=%d,maxAir=%d,onFire=%s,inLava=%s,submergedWater=%s,isSwimming=%s,inSwimmingPose=%s",
                hazard.id,
                hazardRecoveryTicks,
                HAZARD_RECOVERY_MAX_TICKS,
                mc.player.getAir(),
                mc.player.getMaxAir(),
                mc.player.isOnFire(),
                mc.player.isInLava(),
                mc.player.isSubmergedInWater(),
                swimming,
                swimmingPose
            )
        );

        if (HAZARD_RECOVERY_FORCE_HARD_FAIL) {
            watchdog(
                "hazard-recovery-auto-fail",
                String.format(
                    Locale.ROOT,
                    "hazard=%s,recoveryTick=%d,reason=temporary-force-hard-fail",
                    hazard.id,
                    hazardRecoveryTicks
                )
            );
            hardFailHazard(hazard, "auto-fail");
            watchdogTick("onTick-hazard-auto-hard-fail", tickStartNs);
            return true;
        }

        if (hazardRecoveryTicks < HAZARD_RECOVERY_MAX_TICKS) {
            watchdogTick("onTick-skip-hazard-recovery", tickStartNs);
            return true;
        }

        watchdog(
            "hazard-recovery-fallback-stop",
            String.format(
                Locale.ROOT,
                "hazard=%s,recoveryTicks=%d,max=%d,action=toggle",
                hazard.id,
                hazardRecoveryTicks,
                HAZARD_RECOVERY_MAX_TICKS
            )
        );
        if (optDebugMessages()) {
            warning("Hazard persisted (" + hazard.id + ") for " + hazardRecoveryTicks + " ticks; stopping Tunnel Miner.");
        }
        hardFailHazard(hazard, "recovery-timeout");
        watchdogTick("onTick-hazard-fallback-stop", tickStartNs);
        return true;
    }

    private HazardCondition detectCurrentHazard() {
        if (mc.player == null || mc.world == null) return HazardCondition.NONE;

        boolean suffocating = isLikelySuffocating(BlockPos.ofFloored(mc.player.getX(), mc.player.getY(), mc.player.getZ()))
            || isLikelySuffocating(BlockPos.ofFloored(mc.player.getX(), mc.player.getEyeY(), mc.player.getZ()));
        if (suffocating) return HazardCondition.SUFFOCATING;

        if (optTunnelHeight() >= 2) {
            boolean swimming = mc.player.isSwimming();
            boolean swimmingPose = mc.player.getPose() == EntityPose.SWIMMING;
            if (swimming || swimmingPose) return HazardCondition.SWIMMING;
        }

        boolean drowning = mc.player.isSubmergedInWater() && mc.player.getAir() <= 40;
        if (drowning) return HazardCondition.DROWNING;

        boolean burning = mc.player.isOnFire() || mc.player.isInLava();
        if (optStealthMode() && burning) return HazardCondition.BURNING;

        return HazardCondition.NONE;
    }

    private boolean isLikelySuffocating(BlockPos pos) {
        BlockState state = mc.world.getBlockState(pos);
        if (state.isAir()) return false;
        if (!state.getFluidState().isEmpty()) return false;
        return state.isSolidBlock(mc.world, pos);
    }

    private boolean isRestockPhase(Phase p) {
        if (p == null) return false;
        return p == Phase.RESTOCK_CLEAR
            || p == Phase.RESTOCK_PLACE
            || p == Phase.RESTOCK_WAIT
            || p == Phase.RESTOCK_OPEN
            || p == Phase.RESTOCK_LOOT
            || p == Phase.RESTOCK_CLOSE
            || p == Phase.RESTOCK_BREAK
            || p == Phase.RESTOCK_PICKUP;
    }

    private void resetRestockStageState() {
        restockFailOutOfPickaxes = false;
        restockPreferShulkerNext = false;
        restockEcSearchExhausted = false;
        restockEcExtractedPickShulker = false;
    }

    private int findPreferredRestockShulkerSlot() {
        int pickShulker = findInInv(this::shulkerContainsPickaxe);
        if (pickShulker != -1) return pickShulker;
        boolean forcedShulkerStage = restockPreferShulkerNext || restockEcExtractedPickShulker;
        if (forcedShulkerStage || (optUseShulkers() && !optUseEnderChest())) {
            return findInInv(TunnelMinerModule::isShulkerBox);
        }
        return -1;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        long tickStartNs = optWatchdogEnabled() && HARD_WATCHDOG_LOG_TICKS ? System.nanoTime() : 0L;

        if (mc.player == null || mc.world == null) {
            watchdogTick("onTick-skip-null-world-player", tickStartNs);
            if (isActive()) toggle();
            return;
        }

        boolean stealthNow = isStealthEnabledForSpeedOwnership();
        if (stealthNow && !stealthSpeedWasEnabled) tryApplyStealthSpeedControl("stealth-enabled-mid-run");
        stealthSpeedWasEnabled = stealthNow;

        if (handleHazardRecovery(tickStartNs)) return;

        int currentY = MathHelper.floor(mc.player.getY());
        if (currentY != tunnelY) {
            mc.options.forwardKey.setPressed(false);
            if (optDebugMessages()) warning("Y changed from " + tunnelY + " to " + currentY + "; stopping to preserve fixed-Y pathing.");
            watchdog("y-lock-stop", "lockedY=" + tunnelY + ",currentY=" + currentY);
            toggle();
            return;
        }

        if (shouldPauseForEating()) {
            if (mc.options != null) mc.options.forwardKey.setPressed(false);
            watchdogTick("onTick-skip-eating", tickStartNs);
            return;
        }

        updateWatchdogProgressTelemetry(blocksLeftFrom(MathHelper.floor(mc.player.getX()), MathHelper.floor(mc.player.getZ())));

        // Decrement timers and skip phase logic if active
        if (placeTimer > 0) {
            placeTimer--;
            watchdogTick("onTick-skip-place-timer", tickStartNs);
            return;
        }
        if (invTimer > 0) {
            invTimer--;
            watchdogTick("onTick-skip-inv-timer", tickStartNs);
            return;
        }

        renderPlacePositions.clear();
        markDetourVisit(MathHelper.floor(mc.player.getX()), MathHelper.floor(mc.player.getZ()));

        Phase executedPhase = phase;
        switch (phase) {
            case INIT: initPhase(); break;
            case MINE:
            case WALK:
            case FILL:
                // Use the polished stealth execution pipeline for movement/restock in both modes.
                stealthTick();
                break;
            case RESTOCK_CLEAR: restockClear(); break;
            case RESTOCK_PLACE: restockPlace(); break;
            case RESTOCK_WAIT: restockWait(); break;
            case RESTOCK_OPEN: restockOpen(); break;
            case RESTOCK_LOOT: restockLoot(); break;
            case RESTOCK_CLOSE: restockClose(); break;
            case RESTOCK_BREAK: restockBreak(); break;
            case RESTOCK_PICKUP: restockPickup(); break;
            case DONE:
                if (optDebugMessages()) {
                    if (goalMode == GoalMode.FALLBACK) {
                        warning("Requested destination unreachable; completed at fallback (" + activeGoalX + ", " + activeGoalZ + ") for requested (" + requestedDestX + ", " + requestedDestZ + ").");
                    } else {
                        info("Destination reached!");
                    }
                }

                disconnectOnSuccessfulCompletion();
                break;
        }

        int px = MathHelper.floor(mc.player.getX());
        int py = MathHelper.floor(mc.player.getY());
        int pz = MathHelper.floor(mc.player.getZ());
        refreshRenderOverlays(px, py, pz);

        maybeStopForStealthStall();
        watchdogTick("onTick-phase-" + (executedPhase == null ? "null" : executedPhase.name()), tickStartNs);
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        // Render blocks to break
        if (optRenderMining()) {
            for (BlockPos pos : renderBreakPositions) {
                event.renderer.box(pos, optBreakSideColor(), optBreakLineColor(), optShapeMode(), 0);
            }
        }

        // Render blocks to place
        if (optRenderPlacing()) {
            int px = MathHelper.floor(mc.player.getX());
            int pz = MathHelper.floor(mc.player.getZ());

            for (BlockPos pos : renderPlacePositions) {
                // Only render if it's a valid placement target (not in player's current column)
                if (pos.getX() == px && pos.getZ() == pz) {
                    continue;
                }
                event.renderer.box(pos, optPlaceSideColor(), optPlaceLineColor(), optShapeMode(), 0);
            }
        }

        // Render planned path
        if (optRenderPath()) {
            double y = tunnelY + 0.5;
            for (PathStep step : renderPathPreviewSteps) {
                if (step == null) continue;
                double x1 = step.fromX() + 0.5;
                double z1 = step.fromZ() + 0.5;
                double x2 = step.toX() + 0.5;
                double z2 = step.toZ() + 0.5;
                event.renderer.line(x1, y, z1, x2, y, z2, optPathLineColor());
            }
        }
    }

    private void refreshRenderOverlays(int px, int py, int pz) {
        refreshRenderPathSteps(px, pz);
    }

    private void refreshRenderPlacePositions(int px, int py, int pz) {
        renderPlacePositions.clear();
        if (!optRenderPlacing()) return;

        int[] step = nextStep(px, pz);
        int stepX = step[0];
        int stepZ = step[1];

        LinkedHashSet<BlockPos> positions = new LinkedHashSet<>();
        if (optStealthMode()) {
            for (BlockPos pos : stealthCache.keySet()) {
                if (isAheadOfStep(px, pz, stepX, stepZ, pos)) continue;
                positions.add(pos);
            }
        }
        if (optFillBehind()) {
            for (BlockPos pos : fillLog.keySet()) {
                if (optStealthMode() && isAheadOfStep(px, pz, stepX, stepZ, pos)) continue;
                positions.add(pos);
            }
        }

        if (optLavaAvoidance()) addLavaPlacePreview(positions, px, py, pz);
        if (optAirPlace()) addAirPlacePreview(positions, px, py, pz);
        if (phase == Phase.WALK) addFloorPlacePreview(positions);

        renderPlacePositions.addAll(positions);
    }

    private boolean isAheadOfStep(int px, int pz, int stepX, int stepZ, BlockPos pos) {
        if (stepX == 0 && stepZ == 0) return false;
        int dx = pos.getX() - px;
        int dz = pos.getZ() - pz;
        return dx * stepX + dz * stepZ > 0;
    }

    private void addFloorPlacePreview(LinkedHashSet<BlockPos> positions) {
        BlockPos myFloor = mc.player.getBlockPos().down();
        if (shouldPreviewFloor(myFloor)) positions.add(myFloor.toImmutable());

        BlockPos targetFloor = new BlockPos(MathHelper.floor(walkTargetX), tunnelY - 1, MathHelper.floor(walkTargetZ));
        if (shouldPreviewFloor(targetFloor)) positions.add(targetFloor.toImmutable());
    }

    private boolean shouldPreviewFloor(BlockPos pos) {
        BlockState state = mc.world.getBlockState(pos);
        return state.isAir() || !state.getFluidState().isEmpty();
    }

    private void addAirPlacePreview(LinkedHashSet<BlockPos> positions, int px, int py, int pz) {
        int[] step = nextStep(px, pz);
        int stepX = step[0];
        int stepZ = step[1];
        if (stepX == 0 && stepZ == 0) return;

        for (int d = 1; d <= optAirPlaceDistance(); d++) {
            BlockPos placePos = new BlockPos(px + stepX * d, py - 1, pz + stepZ * d);
            if (mc.world.getBlockState(placePos).isAir()) {
                positions.add(placePos.toImmutable());
            }
        }
    }

    private void addLavaPlacePreview(LinkedHashSet<BlockPos> positions, int px, int py, int pz) {
        int[] step = nextStep(px, pz);
        int stepX = step[0];
        int stepZ = step[1];
        if (stepX == 0 && stepZ == 0) return;

        for (int d = 1; d <= 5; d++) {
            int x = px + stepX * d;
            int z = pz + stepZ * d;
            positions.addAll(collectLavaSealTargetsAtColumn(py, x, z));

            if (stepX != 0 && stepZ != 0) {
                int cornerA = pz + stepZ * (d - 1);
                int cornerB = px + stepX * (d - 1);
                positions.addAll(collectLavaSealTargetsAtColumn(py, x, cornerA));
                positions.addAll(collectLavaSealTargetsAtColumn(py, cornerB, z));
            }
        }
    }

    private void refreshRenderPathSteps(int px, int pz) {
        renderPathPreviewSteps.clear();
        if (!optRenderPath()) return;

        int maxSteps = Math.min(optRenderPathSteps(), blocksLeftFrom(px, pz));
        if (maxSteps <= 0) return;

        if (optStealthMode() && !cachedProbePath.isEmpty()) {
            int count = Math.min(maxSteps, cachedProbePath.size());
            renderPathPreviewSteps.addAll(cachedProbePath.subList(0, count));
            return;
        }

        int cx = px;
        int cz = pz;
        boolean axisMode = onXAxis;
        for (int i = 0; i < maxSteps; i++) {
            int[] step = computeStep(cx, cz, axisMode);
            int stepX = step[0];
            int stepZ = step[1];
            if (stepX == 0 && stepZ == 0) break;

            int nx = cx + stepX;
            int nz = cz + stepZ;
            renderPathPreviewSteps.add(new PathStep(cx, cz, nx, nz, stepX, stepZ));

            if (hardPathMode() == PathMode.AxisFirst && axisMode && nx == destX) {
                axisMode = false;
            }
            cx = nx;
            cz = nz;
        }
    }

    private void maybeStopForStealthStall() {
        int timeout = hardStallStopTicks();
        if (timeout <= 0) return;
        if (phase != Phase.MINE && phase != Phase.WALK && phase != Phase.FILL) return;
        if (watchdogSamePosTicks < timeout || watchdogNoProgressTicks < timeout) return;
        if ("move".equals(watchdogStealthGate)) return;

        String details = String.format(
            Locale.ROOT,
            "samePosTicks=%d,noProgressTicks=%d,timeout=%d,gate=%s,gateDetails=%s",
            watchdogSamePosTicks,
            watchdogNoProgressTicks,
            timeout,
            watchdogStealthGate,
            watchdogStealthGateDetails
        );

        // Harden known no-probe deadlock: force a full planner reset/retry instead of auto-toggle.
        if (optStealthMode() && "pause-no-probe-steps".equals(watchdogStealthGate)) {
            if (mc.options != null) mc.options.forwardKey.setPressed(false);
            recoverFromNoProbeStepsStall(details);
            return;
        }

        if (optDebugMessages()) warning("Stealth stall detected, stopping: " + details);
        if (mc.options != null) mc.options.forwardKey.setPressed(false);
        watchdog("stall-stop", details);
        toggle();
    }

    private void recoverFromNoProbeStepsStall(String details) {
        cachedProbePath.clear();
        cachedProbeStartX = Integer.MIN_VALUE;
        cachedProbeStartZ = Integer.MIN_VALUE;
        cachedProbeY = Integer.MIN_VALUE;
        cachedProbeDestX = Integer.MIN_VALUE;
        cachedProbeDestZ = Integer.MIN_VALUE;
        cachedProbeRadius = Integer.MIN_VALUE;
        cachedProbeLastReplanAge = Integer.MIN_VALUE;
        cachedProbeLastEmptyReplanAge = Integer.MIN_VALUE;
        cachedProbeOnXAxis = onXAxis;
        cachedProbeRejectAirGaps = useStealthAvoidAirGapDetours();
        cachedProbeEmergencyNoScaffold = false;
        cachedProbePathMode = hardPathMode();
        noProbePauseTicks = 0;

        proactiveAStarCacheFromX = Integer.MIN_VALUE;
        proactiveAStarCacheFromZ = Integer.MIN_VALUE;
        proactiveAStarCachePy = Integer.MIN_VALUE;
        proactiveAStarCacheDestX = Integer.MIN_VALUE;
        proactiveAStarCacheDestZ = Integer.MIN_VALUE;
        proactiveAStarCacheAge = Integer.MIN_VALUE;
        proactiveAStarCacheStepX = 0;
        proactiveAStarCacheStepZ = 0;
        proactiveAStarCacheSummary = "";

        probeWallFollowActive = false;
        probeAStarFailStreak = Math.max(probeAStarFailStreak, hardAStarMaxFails() + 1);
        detourVisitCounts.clear();
        committedMoveStep = null;

        if (mc != null && mc.player != null) {
            int px = MathHelper.floor(mc.player.getX());
            int pz = MathHelper.floor(mc.player.getZ());
            wallFollowHeading = chooseWallFollowHeading(px, pz);
        } else {
            wallFollowHeading = 0;
        }
        wallFollowRerouteBlocks = 0;

        watchdogSamePosTicks = 0;
        watchdogNoProgressTicks = 0;

        watchdog(
            "stall-recover-no-probe-steps",
            String.format(
                Locale.ROOT,
                "%s,action=force-replan,failStreak=%d,wallHeading=%d",
                sanitizeLogField(details),
                probeAStarFailStreak,
                wallFollowHeading
            )
        );
    }

    // ── INIT ──────────────────────────────────────────────────────────────────

    private void initPhase() {
        if (countPickaxes() == 0) {
            if (optDebugMessages()) {warning("No pickaxes — stopping.");}
            toggle();
            return;
        }
        pickSlot = equipBestPickaxe();
        setPhase(Phase.MINE);
    }

    // ── MINE ──────────────────────────────────────────────────────────────────
    // Break blocks in the tunnel profile one step ahead until clear, then WALK

    private void stealthTick() {
        renderBreakPositions.clear();
        watchdogStealthGate = "tick-start";
        watchdogStealthGateDetails = "";
        watchdogProbeSummary = "";
        watchdogRestoreSummary = "";

        if (needsRestock()) {
            clearStealthMining(true);
            resetRestockStageState();
            watchdogStealthGate = "restock-required";
            watchdogStealthGateDetails = "needsRestock=true";
            if (optDebugMessages()) info("Pickaxe low, starting restock.");
            setPhase(Phase.RESTOCK_CLEAR);
            return;
        }

        int px = MathHelper.floor(mc.player.getX());
        int py = tunnelY;
        int pz = MathHelper.floor(mc.player.getZ());
        tickEmergencyAirFallbackWindow();
        if (!refreshActiveNavigationGoal(px, py, pz)) return;
        boolean atGoalCoords = px == destX && pz == destZ;
        boolean atGoalReady = atGoalCoords && isGoalCompletionCellLoaded();

        if (hardPathMode() == PathMode.AxisFirst && onXAxis && px == destX) {
            onXAxis = false;
            if (optDebugMessages()) info("X axis done, now heading Z...");
        }

        if (stealthBreakTimer > 0) stealthBreakTimer--;

        int probeDistance = effectiveProbeDistance();
        List<PathStep> probeSteps = getOrBuildProbeSteps(px, pz, py, probeDistance);
        boolean emergencyNoScaffold = cachedProbeEmergencyNoScaffold;
        activeProbePositions.clear();
        probeAhead(py, probeSteps);

        int mineStepCount = Math.min(optStealthMineAheadDistance(), probeSteps.size());
        List<PathStep> mineSteps = probeSteps.subList(0, mineStepCount);
        activeMineTargets.clear();
        activeMineTargets.addAll(collectMineTargets(py, mineSteps));
        watchdogProbeSummary = String.format(
            Locale.ROOT,
            "probeSteps=%d,mineSteps=%d,mineTargets=%d,probeCache=%d,activeProbe=%d,firstStep=%s",
            probeSteps.size(),
            mineStepCount,
            activeMineTargets.size(),
            stealthCache.size(),
            activeProbePositions.size(),
            probeSteps.isEmpty() ? "none" : formatPathStep(probeSteps.get(0))
        );

        ensureStealthPickaxe();
        tickStealthDoubleMine();
        runAheadMining(activeMineTargets);

        renderBreakPositions.addAll(activeMineTargets);
        if (normalMining != null && !renderBreakPositions.contains(normalMining.blockPos)) renderBreakPositions.add(normalMining.blockPos);
        if (packetMining != null && !renderBreakPositions.contains(packetMining.blockPos)) renderBreakPositions.add(packetMining.blockPos);

        boolean lavaBlocked = false;
        boolean filledLava = false;
        boolean hasLava = false;
        if (isAntiLavaActiveForCurrentMode()) {
            filledLava = tryFillStealthLava(py, mineSteps);
            hasLava = hasStealthLava(py, mineSteps);
            lavaBlocked = filledLava || hasLava;
        }

        boolean closeMineBlocked = hasCloseMineTarget(activeMineTargets) || hasCloseActiveMine();
        if (closeMineBlocked || lavaBlocked) {
            noProbePauseTicks = 0;
            watchdogRestoreSummary = "skipped=true,reason=movement-gated";
            watchdogStealthGate = closeMineBlocked ? "pause-close-mine" : "pause-lava";
            watchdogStealthGateDetails = String.format(
                Locale.ROOT,
                "closeMineBlocked=%s,lavaBlocked=%s,filledLava=%s,hasLava=%s,mineTargets=%d,normal=%s,packet=%s",
                closeMineBlocked,
                lavaBlocked,
                filledLava,
                hasLava,
                activeMineTargets.size(),
                formatMineBlock(normalMining),
                formatMineBlock(packetMining)
            );
            mc.options.forwardKey.setPressed(false);
        } else {
            boolean maintainBehindParity = shouldMaintainBehindParity();
            boolean finalizing = probeSteps.isEmpty() && atGoalReady;
            if (maintainBehindParity) {
                restoreBehindFromCache(finalizing);
            } else {
                watchdogRestoreSummary = "skipped=true,reason=fill-behind-disabled-non-stealth";
            }

            if (finalizing) {
                noProbePauseTicks = 0;
                if (!maintainBehindParity) {
                    if (atGoalReady) setPhase(Phase.DONE);
                    watchdogStealthGate = "finalizing-complete-no-restore";
                    watchdogStealthGateDetails = "restoreDisabled=true,atDestCoords=" + atGoalCoords + ",atDestReady=" + atGoalReady;
                    return;
                }
                // End-of-path: drain remaining restore work before finishing.
                if (!hasAnyStealthRestorePending(true)) {
                    if (atGoalReady) setPhase(Phase.DONE);
                    watchdogStealthGate = "finalizing-complete";
                    watchdogStealthGateDetails = "restorePending=false,atDestCoords=" + atGoalCoords + ",atDestReady=" + atGoalReady;
                    return;
                }
                if (!hasReachableStealthRestore(true)) {
                    watchdogStealthGate = "finalizing-no-reachable-restore";
                    watchdogStealthGateDetails = "restorePending=true,reachable=false";
                    mc.options.forwardKey.setPressed(false);
                    return;
                }
                watchdogStealthGate = "finalizing-restore-drain";
                watchdogStealthGateDetails = "restorePending=true,reachable=true";
                mc.options.forwardKey.setPressed(false);
                return;
            }

            // While moving, allow limited restore lag before pausing to catch up.
            if (maintainBehindParity) {
                boolean restoreLagExceeded = hasStealthRestoreLagExceeded(optStealthRestoreLagDistance());
                if (restoreLagExceeded) {
                    boolean reachableRestore = hasReachableStealthRestore(false);
                    if (reachableRestore) {
                        watchdogStealthGate = "pause-restore-lag";
                        watchdogStealthGateDetails = "maxLag=" + optStealthRestoreLagDistance() + ",reachable=true";
                        mc.options.forwardKey.setPressed(false);
                        return;
                    } else {
                        watchdogStealthGate = "lag-unreachable-continue";
                        watchdogStealthGateDetails = "maxLag=" + optStealthRestoreLagDistance() + ",reachable=false";
                    }
                }
            }

            if (!probeSteps.isEmpty()) {
                noProbePauseTicks = 0;
                PathStep next = pickStealthMoveStep(px, pz, py, probeSteps);
                boolean supportReady = emergencyNoScaffold
                    ? hasSolidStealthSupportFloor(px, py, pz, next)
                    : ensureStealthSupportFloor(px, py, pz, next);
                if (!supportReady) {
                    if (emergencyNoScaffold) {
                        committedMoveStep = null;
                        cachedProbePath.clear();
                    }
                    watchdogStealthGate = "pause-floor-support-failed";
                    watchdogStealthGateDetails = "next=" + formatPathStep(next) + ",emergencyNoScaffold=" + emergencyNoScaffold;
                    mc.options.forwardKey.setPressed(false);
                    return;
                }
                if (isStepClear(py, next)) {
                    committedMoveStep = next;
                    watchdogStealthGate = "move";
                    watchdogStealthGateDetails = "next=" + formatPathStep(next);
                    moveToward(next.toX() + 0.5, next.toZ() + 0.5, () -> {});
                } else {
                    watchdogStealthGate = "pause-step-not-clear";
                    watchdogStealthGateDetails = "next=" + formatPathStep(next);
                    mc.options.forwardKey.setPressed(false);
                }
            } else {
                noProbePauseTicks++;
                committedMoveStep = null;
                watchdogStealthGate = "pause-no-probe-steps";
                watchdogStealthGateDetails = "probeSteps=0";
                mc.options.forwardKey.setPressed(false);
                if (atGoalReady && (!maintainBehindParity || !hasAnyStealthRestorePending(true))) setPhase(Phase.DONE);
            }
        }

        boolean doneRestoreReady = !shouldMaintainBehindParity() || !hasAnyStealthRestorePending(true);
        if (probeSteps.isEmpty() && doneRestoreReady && normalMining == null && packetMining == null && atGoalReady) {
            setPhase(Phase.DONE);
        }
    }

    private List<PathStep> computeFuturePathSteps(int startX, int startZ, int py, int maxSteps, boolean emergencyRelaxAirGaps) {
        boolean rejectAirGapDetours = useStealthAvoidAirGapDetours() && !emergencyRelaxAirGaps;
        if (rejectAirGapDetours) {
            if (probeWallFollowActive) {
                int initialHeading = ((wallFollowHeading % 4) + 4) % 4;
                for (int attempt = 0; attempt < 4; attempt++) {
                    int attemptHeading = (initialHeading + attempt) & 3;
                    wallFollowHeading = attemptHeading;
                    watchdogCalc(
                        "wall-follow",
                        String.format(
                            Locale.ROOT,
                            "event=try-heading,start=(%d,%d),attempt=%d,heading=%d",
                            startX,
                            startZ,
                            attempt + 1,
                            attemptHeading
                        )
                    );
                    List<PathStep> wallSteps = computeFuturePathStepsWallFollow(startX, startZ, py, maxSteps, true);
                    if (!wallSteps.isEmpty()) return wallSteps;

                    // Try a different cardinal heading before giving up this tick.
                    int nextHeading = (initialHeading + attempt + 1) & 3;
                    watchdogCalc(
                        "wall-follow",
                        String.format(
                            Locale.ROOT,
                            "event=rotate-heading,start=(%d,%d),attempt=%d,newHeading=%d",
                            startX,
                            startZ,
                            attempt + 1,
                            nextHeading
                        )
                    );
                }
                wallFollowHeading = (initialHeading + 1) & 3;
            }

            List<PathStep> astar = computeFuturePathStepsAStar(startX, startZ, py, maxSteps, true);
            if (!astar.isEmpty()) {
                if (isAStarSoftFailure(startX, startZ, astar)) {
                    noteProbeAStarFailure(startX, startZ, "soft-loop");
                    if (probeWallFollowActive) {
                        List<PathStep> wallSteps = computeFuturePathStepsWallFollow(startX, startZ, py, maxSteps, true);
                        if (!wallSteps.isEmpty()) return wallSteps;
                    }
                    return astar;
                }

                probeAStarFailStreak = 0;
                if (probeWallFollowActive && wallFollowRerouteBlocks > 0 && (startX != wallFollowMLineStartX || startZ != wallFollowMLineStartZ)) {
                    boolean nearMLine = isNearMLine(startX, startZ, wallFollowMLineStartX, wallFollowMLineStartZ, destX, destZ);
                    if (nearMLine) {
                        boolean farEnough = hasWallFollowMinRejoinDistance(startX, startZ);
                        boolean goalSide = isOnGoalSideOfMLine(startX, startZ, wallFollowMLineStartX, wallFollowMLineStartZ, destX, destZ);
                        if (farEnough && goalSide) {
                            deactivateWallFollow("rejoined-mline-at-start", startX, startZ);
                        } else {
                            watchdogCalc(
                                "wall-follow",
                                String.format(
                                    Locale.ROOT,
                                    "event=rejoin-mline-ignored,at=(%d,%d),reason=%s,farEnough=%s,goalSide=%s,minRejoinDist=%d",
                                    startX,
                                    startZ,
                                    farEnough ? "not-goal-side" : "too-close",
                                    farEnough,
                                    goalSide,
                                    WALL_FOLLOW_MIN_REJOIN_DISTANCE_BLOCKS
                                )
                            );
                        }
                    }
                }
                return astar;
            }

            noteProbeAStarFailure(startX, startZ, "empty");

            if (probeWallFollowActive) {
                List<PathStep> wallSteps = computeFuturePathStepsWallFollow(startX, startZ, py, maxSteps, true);
                if (!wallSteps.isEmpty()) return wallSteps;
            }

            return astar;
        }

        List<PathStep> steps = new ArrayList<>();
        int x = startX;
        int z = startZ;
        boolean axisMode = onXAxis;
        int probeRadius = Math.max(1, maxSteps);
        int probeRadiusSq = probeRadius * probeRadius;
        int planningBudget = Math.max(probeRadius * 8, 128);
        HashMap<Long, Integer> localVisits = new HashMap<>();
        watchdogCalc("future-steps-start", String.format(Locale.ROOT, "start=(%d,%d),py=%d,probeRadius=%d,planningBudget=%d,axisMode=%s,dest=(%d,%d)", startX, startZ, py, probeRadius, planningBudget, axisMode, destX, destZ));

        for (int i = 0; i < planningBudget; i++) {
            if (x == destX && z == destZ) break;

            // When avoid-air-gaps is enabled, enforce it across the full probe horizon
            // so near-step execution cannot silently fall back to scaffold placement.
            int[] step = computeStepWithDetour(x, z, py, axisMode, i == 0, rejectAirGapDetours);
            int stepX = step[0];
            int stepZ = step[1];
            if (hardPathMode() == PathMode.AxisFirst && axisMode && x == destX) axisMode = false;

            if (stepX == 0 && stepZ == 0) {
                watchdogCalc("future-steps-stop", String.format(Locale.ROOT, "i=%d,from=(%d,%d),reason=zero-step,detour=%s,traverseFail=%s,astar=%s", i, x, z, watchdogDetourSummary, watchdogTraverseFail, watchdogAStarSummary));
                break;
            }

            int nextX = x + stepX;
            int nextZ = z + stepZ;
            int rx = nextX - startX;
            int rz = nextZ - startZ;
            if (rx * rx + rz * rz > probeRadiusSq) {
                watchdogCalc("future-steps-stop", String.format(Locale.ROOT, "i=%d,from=(%d,%d),to=(%d,%d),reason=radius-limit,probeRadius=%d", i, x, z, nextX, nextZ, probeRadius));
                break;
            }

            steps.add(new PathStep(x, z, nextX, nextZ, stepX, stepZ));
            watchdogCalc("future-steps-step", String.format(Locale.ROOT, "i=%d,from=(%d,%d),to=(%d,%d),step=(%d,%d),axisModeNow=%s,rejectAirGapDetours=%s,detour=%s,traverseFail=%s,astar=%s", i, x, z, nextX, nextZ, stepX, stepZ, axisMode, rejectAirGapDetours, watchdogDetourSummary, watchdogTraverseFail, watchdogAStarSummary));
            x = nextX;
            z = nextZ;

            long key = packXZ(x, z);
            int visits = localVisits.merge(key, 1, Integer::sum);
            if (visits > 3) {
                watchdogCalc("future-steps-stop", String.format(Locale.ROOT, "i=%d,at=(%d,%d),reason=local-loop,visits=%d", i, x, z, visits));
                break;
            }
        }

        watchdogCalc("future-steps-end", String.format(Locale.ROOT, "count=%d,end=(%d,%d)", steps.size(), x, z));
        return steps;
    }

    private void noteProbeAStarFailure(int startX, int startZ, String reason) {
        probeAStarFailStreak++;
        int failLimit = Math.max(1, hardAStarMaxFails());
        watchdogCalc(
            "wall-follow",
            String.format(
                Locale.ROOT,
                "event=astar-fail,start=(%d,%d),reason=%s,streak=%d,limit=%d,active=%s",
                startX,
                startZ,
                reason,
                probeAStarFailStreak,
                failLimit,
                probeWallFollowActive
            )
        );

        if (!probeWallFollowActive && probeAStarFailStreak >= failLimit) {
            probeWallFollowActive = true;
            wallFollowMLineStartX = startX;
            wallFollowMLineStartZ = startZ;
            wallFollowHitDistance = octileDistance(startX, startZ, destX, destZ);
            wallFollowHeading = chooseWallFollowHeading(startX, startZ);
            wallFollowRerouteBlocks = 0;
            watchdogCalc(
                "wall-follow",
                String.format(
                    Locale.ROOT,
                    "event=activate,start=(%d,%d),dest=(%d,%d),hitDist=%.3f,heading=%d,rerouteMax=%d",
                    startX,
                    startZ,
                    destX,
                    destZ,
                    wallFollowHitDistance,
                    wallFollowHeading,
                    WALL_FOLLOW_MAX_REROUTE_BLOCKS
                )
            );
        }
    }

    private void deactivateWallFollow(String reason, int x, int z) {
        if (!probeWallFollowActive) return;
        probeWallFollowActive = false;
        probeAStarFailStreak = 0;
        watchdogCalc(
            "wall-follow",
            String.format(
                Locale.ROOT,
                "event=deactivate,reason=%s,at=(%d,%d),rerouteBlocks=%d,rerouteMax=%d,mLineStart=(%d,%d),dest=(%d,%d)",
                reason,
                x,
                z,
                wallFollowRerouteBlocks,
                WALL_FOLLOW_MAX_REROUTE_BLOCKS,
                wallFollowMLineStartX,
                wallFollowMLineStartZ,
                destX,
                destZ
            )
        );
    }

    private boolean isAStarSoftFailure(int startX, int startZ, List<PathStep> astar) {
        if (astar == null || astar.isEmpty()) return false;

        PathStep first = astar.get(0);
        PathStep last = astar.get(astar.size() - 1);

        int startVisits = detourVisitCounts.getOrDefault(packXZ(startX, startZ), 0);
        double startH = octileDistance(startX, startZ, destX, destZ);
        double firstH = octileDistance(first.toX(), first.toZ(), destX, destZ);
        double endH = octileDistance(last.toX(), last.toZ(), destX, destZ);
        boolean shortPath = astar.size() <= 2;
        boolean noProgressFirst = firstH + 1e-6 >= startH;
        boolean noProgressEnd = endH + 1e-6 >= startH;
        boolean immediateBacktrack = startX == lastVisitedX
            && startZ == lastVisitedZ
            && first.toX() == prevVisitedX
            && first.toZ() == prevVisitedZ;
        boolean loopVisits = startVisits >= PROBE_LOOP_VISIT_REPLAN_THRESHOLD;

        boolean softFail = immediateBacktrack || (loopVisits && (shortPath || noProgressFirst || noProgressEnd));
        if (softFail) {
            watchdogCalc(
                "wall-follow",
                String.format(
                    Locale.ROOT,
                    "event=astar-soft-fail,start=(%d,%d),first=%s,last=%s,count=%d,visits=%d,startH=%.3f,firstH=%.3f,endH=%.3f,short=%s,noProgFirst=%s,noProgEnd=%s,backtrack=%s",
                    startX,
                    startZ,
                    formatPathStep(first),
                    formatPathStep(last),
                    astar.size(),
                    startVisits,
                    startH,
                    firstH,
                    endH,
                    shortPath,
                    noProgressFirst,
                    noProgressEnd,
                    immediateBacktrack
                )
            );
        }
        return softFail;
    }

    private List<PathStep> computeFuturePathStepsAStar(int startX, int startZ, int py, int maxSteps, boolean rejectAirGapDetours) {
        final int[][] dirs = {
            { 1, 0 }, { 1, 1 }, { 0, 1 }, { -1, 1 },
            { -1, 0 }, { -1, -1 }, { 0, -1 }, { 1, -1 }
        };

        List<PathStep> steps = new ArrayList<>();
        int probeRadius = Math.max(1, maxSteps);
        int probeRadiusSq = probeRadius * probeRadius;
        int configuredNodeLimit = Math.max(64, hardPathCalcMaxNodes());
        int dynamicLimit = Math.max(128, probeRadius * probeRadius * 2);
        int failureBoost = Math.max(0, probeAStarFailStreak - hardAStarMaxFails());
        int boostedNodeLimit = configuredNodeLimit + failureBoost * 256;
        int astarNodeLimit = Math.max(64, Math.min(Math.min(4096, boostedNodeLimit), dynamicLimit));

        long startKey = packXZ(startX, startZ);
        PriorityQueue<AStarNode> open = new PriorityQueue<>(Comparator.comparingDouble(a -> a.f));
        HashMap<Long, Double> gScore = new HashMap<>();
        HashMap<Long, Long> cameFrom = new HashMap<>();
        HashSet<Long> closed = new HashSet<>();

        double startH = octileDistance(startX, startZ, destX, destZ);
        open.add(new AStarNode(startX, startZ, 0.0, startH));
        gScore.put(startKey, 0.0);

        long bestKey = startKey;
        double bestH = startH;
        long bestReachableKey = startKey;
        double bestReachableH = Double.POSITIVE_INFINITY;
        int expanded = 0;
        boolean reachedGoal = false;

        watchdogCalc(
            "future-steps-astar-start",
            String.format(
                Locale.ROOT,
                "start=(%d,%d),dest=(%d,%d),py=%d,probeRadius=%d,nodeLimit=%d,rejectAirGapDetours=%s,failStreak=%d,failureBoost=%d",
                startX,
                startZ,
                destX,
                destZ,
                py,
                probeRadius,
                astarNodeLimit,
                rejectAirGapDetours,
                probeAStarFailStreak,
                failureBoost
            )
        );

        while (!open.isEmpty() && expanded < astarNodeLimit) {
            AStarNode current = open.poll();
            long currentKey = packXZ(current.x, current.z);
            if (!closed.add(currentKey)) continue;
            expanded++;

            double h = octileDistance(current.x, current.z, destX, destZ);
            if (h < bestH) {
                bestH = h;
                bestKey = currentKey;
            }
            if (currentKey != startKey && h < bestReachableH) {
                bestReachableH = h;
                bestReachableKey = currentKey;
            }

            if (current.x == destX && current.z == destZ) {
                bestKey = currentKey;
                reachedGoal = true;
                break;
            }

            for (int[] dir : dirs) {
                int nx = current.x + dir[0];
                int nz = current.z + dir[1];
                int rx = nx - startX;
                int rz = nz - startZ;
                if (rx * rx + rz * rz > probeRadiusSq) continue;
                if (!isStepTraversable(current.x, current.z, nx, nz, py, false, rejectAirGapDetours)) continue;

                long nKey = packXZ(nx, nz);
                if (closed.contains(nKey)) continue;

                double tentativeG = current.g + stepMovementCost(current.x, current.z, nx, nz);
                double prevG = gScore.getOrDefault(nKey, Double.POSITIVE_INFINITY);
                if (tentativeG >= prevG) continue;

                gScore.put(nKey, tentativeG);
                cameFrom.put(nKey, currentKey);

                double visitPenalty = detourVisitCounts.getOrDefault(nKey, 0) * 2.0;
                double safetyPenalty = computeDetourSafetyPenalty(nx, nz, py, rejectAirGapDetours);
                double nf = tentativeG + octileDistance(nx, nz, destX, destZ) + visitPenalty + safetyPenalty;
                open.add(new AStarNode(nx, nz, tentativeG, nf));
            }
        }

        if (bestKey == startKey && bestReachableKey != startKey) {
            bestKey = bestReachableKey;
            bestH = bestReachableH;
        }

        if (bestKey == startKey) {
            watchdogCalc(
                "future-steps-astar-end",
                String.format(
                    Locale.ROOT,
                    "count=0,reason=no-progress,expanded=%d,nodeLimit=%d,reachedGoal=%s,bestH=%.3f,best=(%d,%d)",
                    expanded,
                    astarNodeLimit,
                    reachedGoal,
                    bestH,
                    unpackX(bestKey),
                    unpackZ(bestKey)
                )
            );
            return steps;
        }

        ArrayList<Long> chain = new ArrayList<>();
        long cursor = bestKey;
        chain.add(cursor);
        while (cameFrom.containsKey(cursor) && cursor != startKey && chain.size() <= astarNodeLimit + 4) {
            cursor = cameFrom.get(cursor);
            chain.add(cursor);
        }
        if (chain.get(chain.size() - 1) != startKey) {
            watchdogCalc(
                "future-steps-astar-end",
                String.format(
                    Locale.ROOT,
                    "count=0,reason=broken-chain,expanded=%d,nodeLimit=%d,reachedGoal=%s,best=(%d,%d),chain=%d",
                    expanded,
                    astarNodeLimit,
                    reachedGoal,
                    unpackX(bestKey),
                    unpackZ(bestKey),
                    chain.size()
                )
            );
            return steps;
        }

        Collections.reverse(chain);
        int curX = startX;
        int curZ = startZ;
        for (int i = 1; i < chain.size() && steps.size() < probeRadius; i++) {
            int nx = unpackX(chain.get(i));
            int nz = unpackZ(chain.get(i));
            int dx = Integer.compare(nx, curX);
            int dz = Integer.compare(nz, curZ);

            if (dx != 0 && dz != 0) {
                int[][] options = { { dx, 0 }, { 0, dz } };
                int bestIdx = -1;
                double bestScore = Double.POSITIVE_INFINITY;
                for (int oi = 0; oi < options.length; oi++) {
                    int ox = options[oi][0];
                    int oz = options[oi][1];
                    int oxTo = curX + ox;
                    int ozTo = curZ + oz;
                    if (!isStepTraversable(curX, curZ, oxTo, ozTo, py, false, rejectAirGapDetours)) continue;
                    double score = octileDistance(oxTo, ozTo, destX, destZ);
                    if (score < bestScore) {
                        bestScore = score;
                        bestIdx = oi;
                    }
                }

                if (bestIdx == -1) break;
                int sx = options[bestIdx][0];
                int sz = options[bestIdx][1];
                int midX = curX + sx;
                int midZ = curZ + sz;
                steps.add(new PathStep(curX, curZ, midX, midZ, sx, sz));
                curX = midX;
                curZ = midZ;
                if (steps.size() >= probeRadius) break;

                int remX = Integer.compare(nx, curX);
                int remZ = Integer.compare(nz, curZ);
                if (remX != 0 || remZ != 0) {
                    if (!isStepTraversable(curX, curZ, nx, nz, py, false, rejectAirGapDetours)) break;
                    steps.add(new PathStep(curX, curZ, nx, nz, remX, remZ));
                    curX = nx;
                    curZ = nz;
                }
                continue;
            }

            if (!isStepTraversable(curX, curZ, nx, nz, py, false, rejectAirGapDetours)) break;
            steps.add(new PathStep(curX, curZ, nx, nz, dx, dz));
            curX = nx;
            curZ = nz;
        }

        watchdogCalc(
            "future-steps-astar-end",
            String.format(
                Locale.ROOT,
                "count=%d,end=(%d,%d),expanded=%d,nodeLimit=%d,reachedGoal=%s,bestH=%.3f,best=(%d,%d)",
                steps.size(),
                curX,
                curZ,
                expanded,
                astarNodeLimit,
                reachedGoal,
                bestH,
                unpackX(bestKey),
                unpackZ(bestKey)
            )
        );

        return steps;
    }

    private List<PathStep> computeFuturePathStepsWallFollow(int startX, int startZ, int py, int maxSteps, boolean rejectAirGapDetours) {
        final int[][] dirs = {
            { 1, 0 }, { 0, 1 }, { -1, 0 }, { 0, -1 }
        };

        List<PathStep> steps = new ArrayList<>();
        int stepLimit = Math.max(1, maxSteps);
        int budget = Math.max(stepLimit * 6, 24);
        int x = startX;
        int z = startZ;
        int heading = ((wallFollowHeading % 4) + 4) % 4;
        HashMap<Long, Integer> localVisits = new HashMap<>();
        String stopReason = "";

        watchdogCalc(
            "wall-follow",
            String.format(
                Locale.ROOT,
                "event=start,start=(%d,%d),dest=(%d,%d),mLineStart=(%d,%d),hitDist=%.3f,heading=%d,stepLimit=%d,budget=%d,rerouteBlocks=%d,rerouteMax=%d",
                startX,
                startZ,
                destX,
                destZ,
                wallFollowMLineStartX,
                wallFollowMLineStartZ,
                wallFollowHitDistance,
                heading,
                stepLimit,
                budget,
                wallFollowRerouteBlocks,
                WALL_FOLLOW_MAX_REROUTE_BLOCKS
            )
        );

        for (int i = 0; i < budget && steps.size() < stepLimit; i++) {
            int[] order = {
                (heading + 3) & 3, // left turn first => counterclockwise boundary following
                heading,
                (heading + 1) & 3,
                (heading + 2) & 3
            };

            int chosenDir = -1;
            int nextX = x;
            int nextZ = z;

            // Prefer steps that keep an obstacle on the left side.
            for (int dir : order) {
                int nx = x + dirs[dir][0];
                int nz = z + dirs[dir][1];
                if (!isStepTraversable(x, z, nx, nz, py, false, rejectAirGapDetours)) continue;

                int left = (dir + 3) & 3;
                int lx = nx + dirs[left][0];
                int lz = nz + dirs[left][1];
                if (isObstacleCell(lx, lz, py, rejectAirGapDetours)) {
                    chosenDir = dir;
                    nextX = nx;
                    nextZ = nz;
                    break;
                }
            }

            // Fallback: any traversable direction in left-hand order.
            if (chosenDir == -1) {
                for (int dir : order) {
                    int nx = x + dirs[dir][0];
                    int nz = z + dirs[dir][1];
                    if (!isStepTraversable(x, z, nx, nz, py, false, rejectAirGapDetours)) continue;
                    chosenDir = dir;
                    nextX = nx;
                    nextZ = nz;
                    break;
                }
            }

            if (chosenDir == -1) {
                watchdogCalc("wall-follow", String.format(Locale.ROOT, "event=stop,reason=no-traversable-step,at=(%d,%d),steps=%d", x, z, steps.size()));
                stopReason = "no-traversable-step";
                break;
            }

            int stepX = dirs[chosenDir][0];
            int stepZ = dirs[chosenDir][1];
            steps.add(new PathStep(x, z, nextX, nextZ, stepX, stepZ));
            x = nextX;
            z = nextZ;
            heading = chosenDir;

            int projectedRerouteBlocks = wallFollowRerouteBlocks + steps.size();
            if (projectedRerouteBlocks >= WALL_FOLLOW_MAX_REROUTE_BLOCKS) {
                wallFollowRerouteBlocks = projectedRerouteBlocks;
                deactivateWallFollow("max-reroute-blocks", x, z);
                break;
            }

            long key = packXZ(x, z);
            int visits = localVisits.merge(key, 1, Integer::sum);
            if (visits > 3) {
                watchdogCalc("wall-follow", String.format(Locale.ROOT, "event=stop,reason=local-loop,at=(%d,%d),visits=%d,steps=%d", x, z, visits, steps.size()));
                stopReason = "local-loop";
                break;
            }

            if ((x != wallFollowMLineStartX || z != wallFollowMLineStartZ)
                && isNearMLine(x, z, wallFollowMLineStartX, wallFollowMLineStartZ, destX, destZ)) {
                boolean farEnough = hasWallFollowMinRejoinDistance(x, z);
                boolean goalSide = isOnGoalSideOfMLine(x, z, wallFollowMLineStartX, wallFollowMLineStartZ, destX, destZ);
                if (farEnough && goalSide) {
                    deactivateWallFollow("hit-mline", x, z);
                    break;
                }
                watchdogCalc(
                    "wall-follow",
                    String.format(
                        Locale.ROOT,
                        "event=hit-mline-ignored,at=(%d,%d),farEnough=%s,goalSide=%s,minRejoinDist=%d",
                        x,
                        z,
                        farEnough,
                        goalSide,
                        WALL_FOLLOW_MIN_REJOIN_DISTANCE_BLOCKS
                    )
                );
            }
        }

        wallFollowHeading = heading;

        // For hard stop conditions (including local loops), force caller to retry
        // with rotated headings instead of accepting a short prefix that may
        // bounce between the same cells.
        if (probeWallFollowActive && !stopReason.isEmpty()) {
            watchdogCalc(
                "wall-follow",
                String.format(
                    Locale.ROOT,
                    "event=retry-needed,reason=%s,start=(%d,%d),end=(%d,%d),count=%d,heading=%d",
                    stopReason,
                    startX,
                    startZ,
                    x,
                    z,
                    steps.size(),
                    wallFollowHeading
                )
            );
            return new ArrayList<>();
        }

        watchdogCalc("wall-follow", String.format(Locale.ROOT, "event=end,count=%d,end=(%d,%d),heading=%d,active=%s", steps.size(), x, z, wallFollowHeading, probeWallFollowActive));
        return steps;
    }

    private int chooseWallFollowHeading(int x, int z) {
        int dx = Integer.compare(destX, x);
        int dz = Integer.compare(destZ, z);
        if (dx != 0 && dz != 0) {
            if (Math.abs(destX - x) >= Math.abs(destZ - z)) dz = 0;
            else dx = 0;
        }

        if (dx > 0) return 0;
        if (dz > 0) return 1;
        if (dx < 0) return 2;
        if (dz < 0) return 3;
        return 0;
    }

    private PathStep pickStealthMoveStep(int px, int pz, int py, List<PathStep> probeSteps) {
        if (probeSteps == null || probeSteps.isEmpty()) {
            committedMoveStep = null;
            return null;
        }

        PathStep planned = probeSteps.get(0);
        if (committedMoveStep == null) return planned;

        // Once we reach the committed destination center, allow normal step selection.
        if (isAtStepToCenter(committedMoveStep)) {
            watchdogCalc("move-commit", String.format(Locale.ROOT, "action=release,reason=reached-center,step=%s", formatPathStep(committedMoveStep)));
            committedMoveStep = null;
            return planned;
        }

        if (!isStepTraversable(
            committedMoveStep.fromX(),
            committedMoveStep.fromZ(),
            committedMoveStep.toX(),
            committedMoveStep.toZ(),
            py,
            false,
            useStealthAvoidAirGapDetours()
        )) {
            watchdogCalc("move-commit", String.format(Locale.ROOT, "action=release,reason=blocked,step=%s", formatPathStep(committedMoveStep)));
            committedMoveStep = null;
            return planned;
        }

        // If planner flips to immediate reverse while we are still mid-step, keep current commitment.
        if (isReverseStep(planned, committedMoveStep)) {
            watchdogCalc(
                "move-commit",
                String.format(
                    Locale.ROOT,
                    "action=hold,reason=reverse,planned=%s,committed=%s,player=(%d,%d)",
                    formatPathStep(planned),
                    formatPathStep(committedMoveStep),
                    px,
                    pz
                )
            );
            return committedMoveStep;
        }

        return planned;
    }

    private boolean isAtStepToCenter(PathStep step) {
        if (step == null || mc == null || mc.player == null) return true;
        double tx = step.toX() + 0.5;
        double tz = step.toZ() + 0.5;
        double dx = tx - mc.player.getX();
        double dz = tz - mc.player.getZ();
        return dx * dx + dz * dz <= 0.09;
    }

    private boolean isReverseStep(PathStep a, PathStep b) {
        if (a == null || b == null) return false;
        return a.fromX() == b.toX()
            && a.fromZ() == b.toZ()
            && a.toX() == b.fromX()
            && a.toZ() == b.fromZ();
    }

    private boolean isNearMLine(int x, int z, int startX, int startZ, int endX, int endZ) {
        if (startX == Integer.MIN_VALUE || startZ == Integer.MIN_VALUE) return false;
        long dx = (long) endX - startX;
        long dz = (long) endZ - startZ;
        if (dx == 0L && dz == 0L) return x == endX && z == endZ;

        long rx = (long) x - startX;
        long rz = (long) z - startZ;
        double lineLenSq = (double) dx * dx + (double) dz * dz;
        if (lineLenSq <= 0.0) return false;

        double t = ((double) rx * dx + (double) rz * dz) / lineLenSq;
        if (t < -0.25 || t > 1.25) return false;

        double cross = Math.abs((double) rx * dz - (double) rz * dx);
        double lineLen = Math.sqrt(lineLenSq);
        if (lineLen <= 1e-6) return false;

        double dist = cross / lineLen;
        return dist <= WALL_FOLLOW_MLINE_EPSILON;
    }

    private boolean hasWallFollowMinRejoinDistance(int x, int z) {
        if (wallFollowMLineStartX == Integer.MIN_VALUE || wallFollowMLineStartZ == Integer.MIN_VALUE) return false;
        long dx = (long) x - wallFollowMLineStartX;
        long dz = (long) z - wallFollowMLineStartZ;
        long distSq = dx * dx + dz * dz;
        long minSq = (long) WALL_FOLLOW_MIN_REJOIN_DISTANCE_BLOCKS * WALL_FOLLOW_MIN_REJOIN_DISTANCE_BLOCKS;
        return distSq >= minSq;
    }

    private boolean isOnGoalSideOfMLine(int x, int z, int startX, int startZ, int endX, int endZ) {
        if (startX == Integer.MIN_VALUE || startZ == Integer.MIN_VALUE) return false;
        long dx = (long) endX - startX;
        long dz = (long) endZ - startZ;
        if (dx == 0L && dz == 0L) return false;
        long rx = (long) x - startX;
        long rz = (long) z - startZ;
        double dot = (double) rx * dx + (double) rz * dz;
        return dot > 0.0;
    }

    private List<PathStep> getOrBuildProbeSteps(int px, int pz, int py, int probeRadius) {
        boolean rejectAirGapDetours = useStealthAvoidAirGapDetours();
        boolean forceNoProbeReplan = shouldForceNoProbeReplan();
        if (forceNoProbeReplan && rejectAirGapDetours && noProbePauseTicks == PROBE_NO_STEPS_FORCE_REPLAN_TICKS) {
            startEmergencyAirFallbackWindow();
        }
        boolean emergencyRelaxAirGaps = forceNoProbeReplan && rejectAirGapDetours && isEmergencyAirFallbackActive();
        int radius = Math.max(1, probeRadius);
        int age = (mc != null && mc.player != null) ? mc.player.age : Integer.MIN_VALUE;
        int sinceLastReplan = (age == Integer.MIN_VALUE || cachedProbeLastReplanAge == Integer.MIN_VALUE)
            ? Integer.MAX_VALUE
            : age - cachedProbeLastReplanAge;
        if (sinceLastReplan < 0) sinceLastReplan = Integer.MAX_VALUE;
        consumeCachedProbeStepsToCurrent(px, pz);

        boolean settingsChanged =
            cachedProbeY != py
            || cachedProbeDestX != destX
            || cachedProbeDestZ != destZ
            || cachedProbeRadius != radius
            || cachedProbeOnXAxis != onXAxis
            || cachedProbeRejectAirGaps != rejectAirGapDetours
            || cachedProbeEmergencyNoScaffold != emergencyRelaxAirGaps
            || cachedProbePathMode != hardPathMode();

        boolean needsReplan = cachedProbePath.isEmpty() || settingsChanged;
        String reason = needsReplan
            ? (cachedProbePath.isEmpty() ? "empty-cache" : "settings-changed")
            : "";

        if (forceNoProbeReplan && cachedProbePath.isEmpty()) {
            needsReplan = true;
            reason = "force-no-probe-replan";
        }

        if (cachedProbePath.isEmpty() && !settingsChanged && !forceNoProbeReplan) {
            boolean sameEmptyQuery =
                cachedProbeStartX == px
                && cachedProbeStartZ == pz
                && cachedProbeY == py
                && cachedProbeDestX == destX
                && cachedProbeDestZ == destZ
                && cachedProbeRadius == radius
                && cachedProbeOnXAxis == onXAxis
                && cachedProbeRejectAirGaps == rejectAirGapDetours
                && cachedProbeEmergencyNoScaffold == emergencyRelaxAirGaps
                && cachedProbePathMode == hardPathMode();

            if (sameEmptyQuery) {
                int sinceLastEmpty = (age == Integer.MIN_VALUE || cachedProbeLastEmptyReplanAge == Integer.MIN_VALUE)
                    ? Integer.MAX_VALUE
                    : age - cachedProbeLastEmptyReplanAge;
                if (sinceLastEmpty < 0) sinceLastEmpty = Integer.MAX_VALUE;

                if (sinceLastEmpty < PROBE_EMPTY_REPLAN_COOLDOWN_TICKS) {
                    needsReplan = false;
                    reason = "empty-cache-cooldown";
                    watchdogCalc(
                        "probe-path-cache",
                        String.format(
                            Locale.ROOT,
                            "replan=false,reason=empty-cache-cooldown,sinceLastEmpty=%d,cooldown=%d,start=(%d,%d),dest=(%d,%d),radius=%d,noProbeTicks=%d",
                            sinceLastEmpty,
                            PROBE_EMPTY_REPLAN_COOLDOWN_TICKS,
                            px,
                            pz,
                            destX,
                            destZ,
                            radius,
                            noProbePauseTicks
                        )
                    );
                }
            }
        }

        if (needsReplan && !"settings-changed".equals(reason) && !forceNoProbeReplan) {
            int minInterval = Math.max(1, hardPathCalcIntervalTicks());
            if (sinceLastReplan < minInterval) {
                needsReplan = false;
                reason = "replan-interval-throttle";
                watchdogCalc(
                    "probe-path-cache",
                    String.format(
                        Locale.ROOT,
                        "replan=false,reason=replan-interval-throttle,sinceLast=%d,interval=%d,count=%d,start=(%d,%d),dest=(%d,%d),radius=%d,noProbeTicks=%d",
                        sinceLastReplan,
                        minInterval,
                        cachedProbePath.size(),
                        px,
                        pz,
                        destX,
                        destZ,
                        radius,
                        noProbePauseTicks
                    )
                );
            }
        }

        if (!needsReplan && !cachedProbePath.isEmpty()) {
            PathStep first = cachedProbePath.get(0);
            if (first.fromX() != px || first.fromZ() != pz) {
                needsReplan = true;
                reason = "start-mismatch";
            }
        }

        if (!needsReplan && shouldReplanFromProbeMarkers(py, rejectAirGapDetours)) {
            int sinceLast = (age == Integer.MIN_VALUE || cachedProbeLastReplanAge == Integer.MIN_VALUE) ? Integer.MAX_VALUE : age - cachedProbeLastReplanAge;
            if (sinceLast < 0) sinceLast = Integer.MAX_VALUE;

            if (sinceLast < PROBE_REPLAN_COOLDOWN_TICKS && !forceNoProbeReplan) {
                watchdogCalc(
                    "probe-path-cache",
                    String.format(
                        Locale.ROOT,
                        "replan=false,reason=suppressed-cooldown,sinceLast=%d,cooldown=%d,count=%d,start=(%d,%d),dest=(%d,%d),radius=%d,noProbeTicks=%d",
                        sinceLast,
                        PROBE_REPLAN_COOLDOWN_TICKS,
                        cachedProbePath.size(),
                        px,
                        pz,
                        destX,
                        destZ,
                        radius,
                        noProbePauseTicks
                    )
                );
            } else {
                needsReplan = true;
                reason = forceNoProbeReplan ? "force-no-probe-replan" : "probe-avoidance-detected";
            }
        }

        if (needsReplan) {
            cachedProbePath.clear();
            cachedProbePath.addAll(computeFuturePathSteps(px, pz, py, radius, emergencyRelaxAirGaps));
            cachedProbeStartX = px;
            cachedProbeStartZ = pz;
            cachedProbeY = py;
            cachedProbeDestX = destX;
            cachedProbeDestZ = destZ;
            cachedProbeRadius = radius;
            cachedProbeLastReplanAge = age != Integer.MIN_VALUE ? age : cachedProbeLastReplanAge;
            cachedProbeLastEmptyReplanAge = cachedProbePath.isEmpty()
                ? (age != Integer.MIN_VALUE ? age : cachedProbeLastEmptyReplanAge)
                : Integer.MIN_VALUE;
            cachedProbeOnXAxis = onXAxis;
            cachedProbeRejectAirGaps = rejectAirGapDetours;
            cachedProbeEmergencyNoScaffold = emergencyRelaxAirGaps;
            cachedProbePathMode = hardPathMode();
            watchdogCalc(
                "probe-path-cache",
                String.format(
                    Locale.ROOT,
                    "replan=true,reason=%s,count=%d,start=(%d,%d),dest=(%d,%d),radius=%d,forceNoProbe=%s,noProbeTicks=%d,emergencyNoScaffold=%s,emergencyTicksLeft=%d,emergencyBlocksLeft=%d",
                    reason,
                    cachedProbePath.size(),
                    px,
                    pz,
                    destX,
                    destZ,
                    radius,
                    forceNoProbeReplan,
                    noProbePauseTicks,
                    cachedProbeEmergencyNoScaffold,
                    emergencyAirFallbackTicksLeft,
                    emergencyAirFallbackBlocksLeft
                )
            );
        } else {
            watchdogCalc(
                "probe-path-cache",
                String.format(
                    Locale.ROOT,
                    "replan=false,count=%d,start=(%d,%d),dest=(%d,%d),radius=%d,forceNoProbe=%s,noProbeTicks=%d,emergencyNoScaffold=%s,emergencyTicksLeft=%d,emergencyBlocksLeft=%d",
                    cachedProbePath.size(),
                    px,
                    pz,
                    destX,
                    destZ,
                    radius,
                    forceNoProbeReplan,
                    noProbePauseTicks,
                    cachedProbeEmergencyNoScaffold,
                    emergencyAirFallbackTicksLeft,
                    emergencyAirFallbackBlocksLeft
                )
            );
        }

        return cachedProbePath;
    }

    private boolean shouldForceNoProbeReplan() {
        return noProbePauseTicks >= PROBE_NO_STEPS_FORCE_REPLAN_TICKS;
    }

    private boolean isEmergencyAirFallbackActive() {
        return emergencyAirFallbackTicksLeft > 0 && emergencyAirFallbackBlocksLeft > 0;
    }

    private void startEmergencyAirFallbackWindow() {
        emergencyAirFallbackTicksLeft = EMERGENCY_AIR_FALLBACK_MAX_TICKS;
        emergencyAirFallbackBlocksLeft = EMERGENCY_AIR_FALLBACK_MAX_BLOCKS;
        watchdogCalc(
            "probe-path-cache",
            String.format(
                Locale.ROOT,
                "event=emergency-air-fallback-start,ticks=%d,blocks=%d,noProbeTicks=%d",
                emergencyAirFallbackTicksLeft,
                emergencyAirFallbackBlocksLeft,
                noProbePauseTicks
            )
        );
    }

    private void tickEmergencyAirFallbackWindow() {
        if (emergencyAirFallbackTicksLeft <= 0) return;
        emergencyAirFallbackTicksLeft--;
        if (isEmergencyAirFallbackActive()) return;

        if (cachedProbeEmergencyNoScaffold) {
            cachedProbeEmergencyNoScaffold = false;
            cachedProbePath.clear();
            watchdogCalc(
                "probe-path-cache",
                String.format(
                    Locale.ROOT,
                    "event=emergency-air-fallback-expired,reason=ticks,ticksLeft=%d,blocksLeft=%d,cacheCleared=true",
                    emergencyAirFallbackTicksLeft,
                    emergencyAirFallbackBlocksLeft
                )
            );
        }
    }

    private void consumeEmergencyAirFallbackBlock() {
        if (emergencyAirFallbackBlocksLeft <= 0) return;
        emergencyAirFallbackBlocksLeft--;
        if (isEmergencyAirFallbackActive()) return;

        if (cachedProbeEmergencyNoScaffold) {
            cachedProbeEmergencyNoScaffold = false;
            cachedProbePath.clear();
            watchdogCalc(
                "probe-path-cache",
                String.format(
                    Locale.ROOT,
                    "event=emergency-air-fallback-expired,reason=blocks,ticksLeft=%d,blocksLeft=%d,cacheCleared=true",
                    emergencyAirFallbackTicksLeft,
                    emergencyAirFallbackBlocksLeft
                )
            );
        }
    }

    private void consumeCachedProbeStepsToCurrent(int px, int pz) {
        while (!cachedProbePath.isEmpty()) {
            PathStep first = cachedProbePath.get(0);
            if (first.fromX() == px && first.fromZ() == pz) return;
            if (first.toX() == px && first.toZ() == pz) {
                cachedProbePath.remove(0);
                if (cachedProbeEmergencyNoScaffold) consumeEmergencyAirFallbackBlock();
                if (probeWallFollowActive) {
                    wallFollowRerouteBlocks++;
                    if (wallFollowRerouteBlocks >= WALL_FOLLOW_MAX_REROUTE_BLOCKS) {
                        deactivateWallFollow("max-reroute-blocks-consumed", px, pz);
                        cachedProbePath.clear();
                        return;
                    }
                }
                continue;
            }
            cachedProbePath.clear();
            return;
        }
    }

    private boolean shouldReplanFromProbeMarkers(int py, boolean rejectAirGapDetours) {
        if (cachedProbePath.isEmpty()) return true;

        PathStep first = cachedProbePath.get(0);
        if (!isStepTraversable(first.fromX(), first.fromZ(), first.toX(), first.toZ(), py, false, rejectAirGapDetours)) {
            watchdogCalc("probe-path-cache-reason", String.format(Locale.ROOT, "reason=first-step-blocked,step=%s", formatPathStep(first)));
            return true;
        }

        if (probeWallFollowActive) {
            // While wall-follow is active, keep following the computed boundary path
            // unless the immediate first step is no longer traversable.
            return false;
        }

        int currentVisits = detourVisitCounts.getOrDefault(packXZ(first.fromX(), first.fromZ()), 0);
        double currentH = octileDistance(first.fromX(), first.fromZ(), destX, destZ);
        double nextH = octileDistance(first.toX(), first.toZ(), destX, destZ);
        if (currentVisits >= PROBE_LOOP_VISIT_REPLAN_THRESHOLD && nextH >= currentH) {
            watchdogCalc(
                "probe-path-cache-reason",
                String.format(
                    Locale.ROOT,
                    "reason=loop-visits-no-progress,visits=%d,currentH=%.3f,nextH=%.3f,step=%s",
                    currentVisits,
                    currentH,
                    nextH,
                    formatPathStep(first)
                )
            );
            return true;
        }

        if (cachedProbePath.size() <= 2 && currentVisits >= PROBE_LOOP_VISIT_REPLAN_THRESHOLD) {
            watchdogCalc(
                "probe-path-cache-reason",
                String.format(Locale.ROOT, "reason=short-cache-loop,visits=%d,count=%d,step=%s", currentVisits, cachedProbePath.size(), formatPathStep(first))
            );
            return true;
        }

        if (cachedProbePath.size() >= 2) {
            PathStep second = cachedProbePath.get(1);
            if (second.toX() == first.fromX() && second.toZ() == first.fromZ()) {
                watchdogCalc(
                    "probe-path-cache-reason",
                    String.format(Locale.ROOT, "reason=immediate-backtrack,first=%s,second=%s", formatPathStep(first), formatPathStep(second))
                );
                return true;
            }
        }

        int index = 0;
        for (PathStep step : cachedProbePath) {
            if (stepHasAvoidanceMarker(py, step, rejectAirGapDetours)) {
                watchdogCalc("probe-path-cache-reason", String.format(Locale.ROOT, "reason=avoidance-marker,index=%d,step=%s", index, formatPathStep(step)));
                return true;
            }
            index++;
        }

        return false;
    }

    private boolean stepHasAvoidanceMarker(int py, PathStep step, boolean rejectAirGapDetours) {
        if (step == null) return true;
        if (rejectAirGapDetours) {
            BlockPos floor = new BlockPos(step.toX(), py - 1, step.toZ());
            if (touchesAirGapDetourRisk(floor, true)) return true;
        }

        for (BlockPos pos : getStepProfilePositions(py, step, false)) {
            BlockState state = mc.world.getBlockState(pos);
            if (isAvoidanceBlock(state)) return true;
            if (touchesNoTouchChainBlock(pos)) return true;
            if (touchesLavaDetourRisk(pos)) return true;
        }

        return false;
    }

    private void probeAhead(int py, List<PathStep> steps) {
        int added = 0;
        for (PathStep step : steps) {
            for (BlockPos pos : getStepProfilePositions(py, step, true)) {
                activeProbePositions.add(pos);
                int before = stealthCache.size();
                stealthCache.putIfAbsent(pos, mc.world.getBlockState(pos));
                if (stealthCache.size() != before) added++;
            }
        }
        watchdogCalc("probe-ahead", String.format(Locale.ROOT, "steps=%d,activeProbe=%d,cacheSize=%d,cacheAdded=%d", steps.size(), activeProbePositions.size(), stealthCache.size(), added));
    }

    private List<BlockPos> collectMineTargets(int py, List<PathStep> steps) {
        LinkedHashSet<BlockPos> targets = new LinkedHashSet<>();
        int scanned = 0;
        int candidates = 0;
        int skippedRange = 0;

        for (PathStep step : steps) {
            for (BlockPos pos : getStepProfilePositions(py, step, false)) {
                scanned++;
                BlockState state = mc.world.getBlockState(pos);
                if (isMineCandidate(pos, state)) {
                    if (!isMineReachableNow(pos)) {
                        skippedRange++;
                        continue;
                    }
                    candidates++;
                    targets.add(pos.toImmutable());
                }
            }
        }

        List<BlockPos> ordered = new ArrayList<>(targets);
        ordered.sort(Comparator.comparingDouble(pos -> Vec3d.ofCenter(pos).distanceTo(mc.player.getEyePos())));
        watchdogCalc(
            "collect-mine-targets",
            String.format(
                Locale.ROOT,
                "steps=%d,scanned=%d,candidates=%d,skippedRange=%d,uniqueTargets=%d,closest=%s",
                steps.size(),
                scanned,
                candidates,
                skippedRange,
                ordered.size(),
                ordered.isEmpty() ? "none" : formatPos(ordered.get(0))
            )
        );
        return ordered;
    }

    private boolean isMineReachableNow(BlockPos pos) {
        if (mc.player == null) return false;
        Direction dir = BlockUtils.getDirection(pos);
        if (dir == null) dir = Direction.UP;
        Vec3d eyes = mc.player.getEyePos();
        double tx = pos.getX() + dir.getOffsetX();
        double ty = pos.getY() + dir.getOffsetY();
        double tz = pos.getZ() + dir.getOffsetZ();
        double range = mc.player.getBlockInteractionRange();
        return eyes.squaredDistanceTo(tx, ty, tz) <= range * range;
    }

    private List<BlockPos> getStepProfilePositions(int py, PathStep step, boolean includeFloor) {
        LinkedHashSet<BlockPos> positions = new LinkedHashSet<>();
        addColumnPositions(positions, step.toX(), py, step.toZ());

        if (step.stepX() != 0 && step.stepZ() != 0) {
            addColumnPositions(positions, step.toX(), py, step.fromZ());
            addColumnPositions(positions, step.fromX(), py, step.toZ());
        }

        if (includeFloor) positions.add(new BlockPos(step.toX(), py - 1, step.toZ()));
        return new ArrayList<>(positions);
    }

    private void addColumnPositions(Set<BlockPos> positions, int x, int py, int z) {
        for (int h = 0; h < optTunnelHeight(); h++) positions.add(new BlockPos(x, py + h, z));
    }

    private boolean isMineCandidate(BlockPos pos, BlockState state) {
        if (state.isAir() || state.getBlock() == Blocks.BEDROCK || isLavaState(state) || isAvoidanceBlock(state)) return false;
        if (touchesNoTouchChainBlock(pos)) return false;
        if (touchesLavaDetourRisk(pos)) return false;
        return BlockUtils.canBreak(pos, state);
    }

    private boolean hasCloseMineTarget(List<BlockPos> targets) {
        double stopRangeSq = optStealthStopRange() * optStealthStopRange();
        Vec3d playerPos = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());
        int tested = 0;

        for (BlockPos pos : targets) {
            tested++;
            if (!isMineCandidate(pos, mc.world.getBlockState(pos))) continue;
            double distSq = Vec3d.ofCenter(pos).squaredDistanceTo(playerPos);
            if (distSq <= stopRangeSq) {
                watchdogCalc("close-mine-target", String.format(Locale.ROOT, "result=true,tested=%d,pos=%s,distSq=%.3f,stopRangeSq=%.3f", tested, formatPos(pos), distSq, stopRangeSq));
                return true;
            }
        }

        watchdogCalc("close-mine-target", String.format(Locale.ROOT, "result=false,tested=%d,stopRangeSq=%.3f", tested, stopRangeSq));
        return false;
    }

    private boolean hasCloseActiveMine() {
        double stopRangeSq = optStealthStopRange() * optStealthStopRange();
        Vec3d playerPos = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());

        boolean result = (normalMining != null && Vec3d.ofCenter(normalMining.blockPos).squaredDistanceTo(playerPos) <= stopRangeSq)
            || (packetMining != null && Vec3d.ofCenter(packetMining.blockPos).squaredDistanceTo(playerPos) <= stopRangeSq);
        watchdogCalc("close-active-mine", String.format(Locale.ROOT, "result=%s,stopRangeSq=%.3f,normal=%s,packet=%s", result, stopRangeSq, formatMineBlock(normalMining), formatMineBlock(packetMining)));
        return result;
    }

    private boolean hasStealthLava(int py, List<PathStep> steps) {
        for (PathStep step : steps) {
            LinkedHashSet<BlockPos> columns = new LinkedHashSet<>();
            columns.add(new BlockPos(step.toX(), py, step.toZ()));
            if (step.stepX() != 0 && step.stepZ() != 0) {
                columns.add(new BlockPos(step.toX(), py, step.fromZ()));
                columns.add(new BlockPos(step.fromX(), py, step.toZ()));
            }

            for (BlockPos column : columns) {
                if (isLavaAtColumn(column.getX(), py, column.getZ())) return true;
            }
        }

        return false;
    }

    private boolean tryFillStealthLava(int py, List<PathStep> steps) {
        BlockPos target = steps.isEmpty()
            ? mc.player.getBlockPos().down()
            : new BlockPos(steps.get(0).toX(), py - 1, steps.get(0).toZ());
        int slot = findTraversalPlacementSlot(target);
        if (slot == -1) {
            watchdogCalc("fill-lava", "slot=-1,result=false");
            return false;
        }

        int attemptedColumns = 0;
        for (PathStep step : steps) {
            LinkedHashSet<BlockPos> columns = new LinkedHashSet<>();
            columns.add(new BlockPos(step.toX(), py, step.toZ()));
            if (step.stepX() != 0 && step.stepZ() != 0) {
                columns.add(new BlockPos(step.toX(), py, step.fromZ()));
                columns.add(new BlockPos(step.fromX(), py, step.toZ()));
            }

            for (BlockPos column : columns) {
                attemptedColumns++;
                if (fillLavaAtColumn(py, column.getX(), column.getZ(), slot)) {
                    watchdogCalc("fill-lava", String.format(Locale.ROOT, "result=true,slot=%d,attemptedColumns=%d,column=%s", slot, attemptedColumns, formatPos(column)));
                    return true;
                }
            }
        }

        watchdogCalc("fill-lava", String.format(Locale.ROOT, "result=false,slot=%d,attemptedColumns=%d", slot, attemptedColumns));
        return false;
    }

    private boolean isStepClear(int py, PathStep step) {
        for (BlockPos pos : getStepProfilePositions(py, step, false)) {
            BlockState state = mc.world.getBlockState(pos);
            if (!state.isAir() || isLavaState(state)) return false;
        }

        BlockState floorState = mc.world.getBlockState(new BlockPos(step.toX(), py - 1, step.toZ()));
        return !floorState.isAir() && floorState.getFluidState().isEmpty();
    }

    private void restoreBehindFromCache(boolean allowNear) {
        boolean activeDoubleMine = normalMining != null || packetMining != null;
        boolean strictExactRestore = isStrictStealthExactRestoreEnabled();

        int px = MathHelper.floor(mc.player.getX());
        int pz = MathHelper.floor(mc.player.getZ());
        int actions = 0;
        boolean placed = false;
        double reach = 4 + Objects.requireNonNull(Modules.get().get(Reach.class)).blockReach();
        Iterator<Map.Entry<BlockPos, BlockState>> it = stealthCache.entrySet().iterator();
        int scanned = 0;
        int skippedProbe = 0;
        int skippedNear = 0;
        int skippedPlayerColumn = 0;
        int removedAlreadyMatching = 0;
        int skippedReach = 0;
        int removedOriginalLava = 0;
        int removedCurrentLava = 0;
        int removedAdjacentAir = 0;
        int skippedNoRestoreSlot = 0;
        int skippedNoHotbar = 0;
        int brokeForAir = 0;
        int brokeForSolid = 0;
        int placedExact = 0;
        int placedFallback = 0;
        int deferredSolidUnbreakable = 0;
        int deferredAirUnbreakable = 0;
        int deferredBreakForActiveMine = 0;

        while (it.hasNext() && actions < optPlacesPerTick()) {
            Map.Entry<BlockPos, BlockState> entry = it.next();
            BlockPos pos = entry.getKey();
            BlockState original = entry.getValue();
            BlockState current = mc.world.getBlockState(pos);
            scanned++;

            if (activeProbePositions.contains(pos)) {
                skippedProbe++;
                continue;
            }
            if (!allowNear) {
                if (Math.abs(pos.getX() - px) <= 1 && Math.abs(pos.getZ() - pz) <= 1) {
                    skippedNear++;
                    continue;
                }
            } else {
                // In finalization allow near restores, but never touch the player's current column.
                if (pos.getX() == px && pos.getZ() == pz) {
                    skippedPlayerColumn++;
                    continue;
                }
            }

            if (isRestoreSatisfied(original, current, strictExactRestore)) {
                it.remove();
                removedAlreadyMatching++;
                continue;
            }

            if (Vec3d.ofCenter(pos).distanceTo(mc.player.getEyePos()) > reach) {
                skippedReach++;
                continue;
            }

            if (isLavaState(original)) {
                removedOriginalLava++;
                continue;
            }

            if (isLavaState(current)) {
                removedCurrentLava++;
                continue;
            }

            if (original.isAir()) {
                if (current.isAir()) {
                    it.remove();
                    removedAlreadyMatching++;
                    continue;
                }

                if (activeDoubleMine) {
                    deferredBreakForActiveMine++;
                    continue;
                }

                if (BlockUtils.canBreak(pos, current)) {
                    ensurePickaxe();
                    BlockUtils.breakBlock(pos, true);
                    actions++;
                    brokeForAir++;
                } else {
                    deferredAirUnbreakable++;
                }
                continue;
            }

            if (!current.isAir()) {
                if (activeDoubleMine) {
                    deferredBreakForActiveMine++;
                    continue;
                }

                if (BlockUtils.canBreak(pos, current)) {
                    ensurePickaxe();
                    BlockUtils.breakBlock(pos, true);
                    actions++;
                    brokeForSolid++;
                } else {
                    deferredSolidUnbreakable++;
                }
                continue;
            }

            int slot = findExactBlockInInv(original.getBlock());
            boolean exactRestore = true;
            if (slot == -1) {
                if (strictExactRestore) {
                    hardFailStealthExactRestore(pos, original, "restore");
                    return;
                }
                slot = findFallbackRestoreSlot(pos);
                exactRestore = false;
                if (slot == -1) {
                    skippedNoRestoreSlot++;
                    continue;
                }
            }

            int hb = toHotbar(slot);
            if (hb == -1) {
                skippedNoHotbar++;
                continue;
            }

            InvUtils.swap(hb, true);
            if (tryAirPlaceAt(pos, hb, false)) {
                actions++;
                placed = true;
                BlockState after = mc.world.getBlockState(pos);
                if (isRestoreSatisfied(original, after, strictExactRestore)) {
                    it.remove();
                    if (exactRestore) placedExact++;
                    else placedFallback++;
                }
            }
        }

        if (placed) {
            placeTimer = optPlaceDelay();
            ensurePickaxe();
        }

        watchdogRestoreSummary = String.format(
            Locale.ROOT,
            "allowNear=%s,activeDoubleMine=%s,cache=%d,scanned=%d,actions=%d,placed=%s,skippedProbe=%d,skippedNear=%d,skippedPlayerColumn=%d,removedMatch=%d,skippedReach=%d,removedOriginalLava=%d,removedCurrentLava=%d,brokeForAir=%d,brokeForSolid=%d,deferredAirUnbreakable=%d,deferredSolidUnbreakable=%d,deferredBreakForActiveMine=%d,removedAdjacentAir=%d,skippedNoRestoreSlot=%d,skippedNoHotbar=%d,placedExact=%d,placedFallback=%d",
            allowNear,
            activeDoubleMine,
            stealthCache.size(),
            scanned,
            actions,
            placed,
            skippedProbe,
            skippedNear,
            skippedPlayerColumn,
            removedAlreadyMatching,
            skippedReach,
            removedOriginalLava,
            removedCurrentLava,
            brokeForAir,
            brokeForSolid,
            deferredAirUnbreakable,
            deferredSolidUnbreakable,
            deferredBreakForActiveMine,
            removedAdjacentAir,
            skippedNoRestoreSlot,
            skippedNoHotbar,
            placedExact,
            placedFallback
        );
        watchdogCalc("restore", watchdogRestoreSummary);
    }

    private boolean isRestoreSatisfied(BlockState original, BlockState current, boolean strictExactRestore) {
        if (strictExactRestore) return original.getBlock() == current.getBlock();
        if (original.isAir()) return current.isAir();
        if (original.getBlock() == current.getBlock()) return true;
        return !current.isAir() && !isLavaState(current);
    }

    private boolean hasAnyPendingRestoreWithinDistance(double maxDistance) {
        if (mc == null || mc.player == null || mc.world == null) return false;

        boolean strictExactRestore = isStrictStealthExactRestoreEnabled();
        double maxSq = maxDistance * maxDistance;
        Vec3d eyes = mc.player.getEyePos();

        for (Map.Entry<BlockPos, BlockState> entry : stealthCache.entrySet()) {
            BlockPos pos = entry.getKey();
            BlockState original = entry.getValue();
            BlockState current = mc.world.getBlockState(pos);
            if (isRestoreSatisfied(original, current, strictExactRestore)) continue;

            if (Vec3d.ofCenter(pos).squaredDistanceTo(eyes) <= maxSq) return true;
        }

        for (Map.Entry<BlockPos, BlockState> entry : fillLog.entrySet()) {
            BlockPos pos = entry.getKey();
            BlockState original = entry.getValue();
            BlockState current = mc.world.getBlockState(pos);
            if (isRestoreSatisfied(original, current, strictExactRestore)) continue;

            if (Vec3d.ofCenter(pos).squaredDistanceTo(eyes) <= maxSq) return true;
        }

        return false;
    }

    private boolean hasReachableStealthRestore(boolean allowNear) {
        if (mc.player == null) return false;

        boolean strictExactRestore = isStrictStealthExactRestoreEnabled();
        int px = MathHelper.floor(mc.player.getX());
        int pz = MathHelper.floor(mc.player.getZ());
        double reach = 4 + Objects.requireNonNull(Modules.get().get(Reach.class)).blockReach();

        for (Map.Entry<BlockPos, BlockState> entry : stealthCache.entrySet()) {
            BlockPos pos = entry.getKey();
            BlockState original = entry.getValue();
            BlockState current = mc.world.getBlockState(pos);

            if (activeProbePositions.contains(pos)) continue;
            if (isLavaState(original)) continue;
            if (isLavaState(current)) continue;
            if (isRestoreSatisfied(original, current, strictExactRestore)) continue;
            if (!allowNear) {
                if (Math.abs(pos.getX() - px) <= 1 && Math.abs(pos.getZ() - pz) <= 1) continue;
            } else {
                if (pos.getX() == px && pos.getZ() == pz) continue;
            }
            if (Vec3d.ofCenter(pos).distanceTo(mc.player.getEyePos()) > reach) continue;
            return true;
        }

        return false;
    }

    private boolean hasAnyStealthRestorePending(boolean allowNear) {
        if (mc.player == null) return false;

        boolean strictExactRestore = isStrictStealthExactRestoreEnabled();
        int px = MathHelper.floor(mc.player.getX());
        int pz = MathHelper.floor(mc.player.getZ());

        for (Map.Entry<BlockPos, BlockState> entry : stealthCache.entrySet()) {
            BlockPos pos = entry.getKey();
            BlockState original = entry.getValue();
            BlockState current = mc.world.getBlockState(pos);

            if (activeProbePositions.contains(pos)) continue;
            if (isLavaState(original)) continue;
            if (isLavaState(current)) continue;
            if (isRestoreSatisfied(original, current, strictExactRestore)) continue;
            if (!allowNear) {
                if (Math.abs(pos.getX() - px) <= 1 && Math.abs(pos.getZ() - pz) <= 1) continue;
            } else {
                if (pos.getX() == px && pos.getZ() == pz) continue;
            }
            return true;
        }

        return false;
    }

    private boolean hasStealthRestoreLagExceeded(int maxDistance) {
        if (mc.player == null) return false;
        boolean strictExactRestore = isStrictStealthExactRestoreEnabled();
        double maxSq = (double) maxDistance * maxDistance;
        double playerX = mc.player.getX();
        double playerZ = mc.player.getZ();
        int px = MathHelper.floor(playerX);
        int pz = MathHelper.floor(playerZ);

        for (Map.Entry<BlockPos, BlockState> entry : stealthCache.entrySet()) {
            BlockPos pos = entry.getKey();
            BlockState original = entry.getValue();
            BlockState current = mc.world.getBlockState(pos);

            if (activeProbePositions.contains(pos)) continue;
            if (isLavaState(original)) continue;
            if (isLavaState(current)) continue;
            if (isRestoreSatisfied(original, current, strictExactRestore)) continue;
            if (Math.abs(pos.getX() - px) <= 1 && Math.abs(pos.getZ() - pz) <= 1) continue;

            double dx = (pos.getX() + 0.5) - playerX;
            double dz = (pos.getZ() + 0.5) - playerZ;
            double distSq = dx * dx + dz * dz;
            if (distSq > maxSq) {
                watchdogCalc("restore-lag", String.format(Locale.ROOT, "result=true,pos=%s,distSq=%.3f,maxSq=%.3f,maxDistance=%d", formatPos(pos), distSq, maxSq, maxDistance));
                return true;
            }
        }

        watchdogCalc("restore-lag", String.format(Locale.ROOT, "result=false,maxSq=%.3f,maxDistance=%d", maxSq, maxDistance));
        return false;
    }

    private boolean ensureStealthSupportFloor(int px, int py, int pz, PathStep step) {
        BlockPos currentFloor = new BlockPos(px, py - 1, pz);
        BlockPos nextFloor = new BlockPos(step.toX(), py - 1, step.toZ());

        if (!ensureStealthFloorAt(currentFloor, true)) return false;
        return ensureStealthFloorAt(nextFloor, false);
    }

    private boolean hasSolidStealthSupportFloor(int px, int py, int pz, PathStep step) {
        if (step == null) return false;
        BlockPos currentFloor = new BlockPos(px, py - 1, pz);
        BlockPos nextFloor = new BlockPos(step.toX(), py - 1, step.toZ());

        if (!isSolidSupportFloor(currentFloor)) {
            watchdogCalc("support-floor", "result=false,reason=emergency-no-scaffold-current-air,pos=" + formatPos(currentFloor));
            return false;
        }
        if (!isSolidSupportFloor(nextFloor)) {
            watchdogCalc("support-floor", "result=false,reason=emergency-no-scaffold-next-air,pos=" + formatPos(nextFloor));
            return false;
        }

        watchdogCalc("support-floor", "result=true,reason=emergency-no-scaffold-solid,current=" + formatPos(currentFloor) + ",next=" + formatPos(nextFloor));
        return true;
    }

    private boolean isSolidSupportFloor(BlockPos floorPos) {
        BlockState state = mc.world.getBlockState(floorPos);
        return !state.isAir() && state.getFluidState().isEmpty();
    }

    private boolean ensureStealthFloorAt(BlockPos floorPos, boolean allowEntityIntersection) {
        BlockState floorState = mc.world.getBlockState(floorPos);
        if (!floorState.isAir() && floorState.getFluidState().isEmpty()) {
            watchdogCalc("support-floor", "result=true,reason=floor-solid,pos=" + formatPos(floorPos));
            return true;
        }

        if (useStealthAvoidAirGapDetours() && floorState.isAir()) {
            watchdogCalc("support-floor", "result=false,reason=air-gap-no-scaffold,pos=" + formatPos(floorPos));
            return false;
        }

        // Cache floor blocks we support-place so restore can remove/rebuild them later.
        stealthCache.putIfAbsent(floorPos.toImmutable(), floorState);

        int slot = findTraversalPlacementSlot(floorPos);
        if (slot == -1) {
            watchdogCalc("support-floor", "result=false,reason=no-slot,pos=" + formatPos(floorPos));
            return false;
        }

        int hb = toHotbar(slot);
        if (hb == -1) {
            watchdogCalc("support-floor", String.format(Locale.ROOT, "result=false,reason=no-hotbar,slot=%d,pos=%s", slot, formatPos(floorPos)));
            return false;
        }
        InvUtils.swap(hb, true);

        if (tryAirPlaceAt(floorPos, hb, allowEntityIntersection)) {
            placeTimer = optPlaceDelay();
            watchdogCalc("support-floor", String.format(Locale.ROOT, "result=true,reason=placed,pos=%s,slot=%d,hb=%d,allowEntityIntersection=%s", formatPos(floorPos), slot, hb, allowEntityIntersection));
            return true;
        }

        watchdogCalc("support-floor", String.format(Locale.ROOT, "result=false,reason=place-failed,pos=%s,slot=%d,hb=%d,allowEntityIntersection=%s", formatPos(floorPos), slot, hb, allowEntityIntersection));
        return false;
    }

    private boolean tryAirPlaceAt(BlockPos pos, int hotbarSlot, boolean allowEntityIntersection) {
        markRenderPlace(pos);
        // Primary attempt.
        if (BlockUtils.place(pos, Hand.MAIN_HAND, hotbarSlot, false, 0, true, !allowEntityIntersection, true)) {
            watchdogCalc("air-place", String.format(Locale.ROOT, "result=true,attempt=1,pos=%s,hotbar=%d,allowEntityIntersection=%s", formatPos(pos), hotbarSlot, allowEntityIntersection));
            return true;
        }

        // Fallback attempts to improve reliability when the first place packet is rejected.
        if (BlockUtils.place(pos, Hand.MAIN_HAND, hotbarSlot, false, 0, true, !allowEntityIntersection, true)) {
            watchdogCalc("air-place", String.format(Locale.ROOT, "result=true,attempt=2,pos=%s,hotbar=%d,allowEntityIntersection=%s", formatPos(pos), hotbarSlot, allowEntityIntersection));
            return true;
        }
        boolean third = BlockUtils.place(pos, Hand.MAIN_HAND, hotbarSlot, false, 0, true, false, true);
        watchdogCalc("air-place", String.format(Locale.ROOT, "result=%s,attempt=3,pos=%s,hotbar=%d,allowEntityIntersection=%s", third, formatPos(pos), hotbarSlot, allowEntityIntersection));
        return third;
    }

    private boolean tryDirectPlaceAt(BlockPos pos, int hotbarSlot, boolean allowEntityIntersection) {
        markRenderPlace(pos);
        boolean result = BlockUtils.place(pos, Hand.MAIN_HAND, hotbarSlot, false, 0, true, !allowEntityIntersection, true);
        watchdogCalc(
            "direct-place",
            String.format(
                Locale.ROOT,
                "result=%s,pos=%s,hotbar=%d,allowEntityIntersection=%s",
                result,
                formatPos(pos),
                hotbarSlot,
                allowEntityIntersection
            )
        );
        return result;
    }

    private void markRenderPlace(BlockPos pos) {
        if (!optRenderPlacing() || pos == null) return;
        renderPlacePositions.add(pos.toImmutable());
    }

    private void minePhase() {
        renderBreakPositions.clear();
        // Restock check
        if (needsRestock()) {
            if (optDebugMessages()) info("Pickaxe low, starting restock.");
            resetRestockStageState();
            setPhase(Phase.RESTOCK_CLEAR);
            return;
        }

        int px = MathHelper.floor(mc.player.getX());
        int py = MathHelper.floor(mc.player.getY());
        int pz = MathHelper.floor(mc.player.getZ());
        if (!refreshActiveNavigationGoal(px, py, pz)) return;
        boolean atGoalReady = px == destX && pz == destZ && isGoalCompletionCellLoaded();

        // Destination reached.
        if (atGoalReady) {
            setPhase(Phase.DONE);
            return;
        }

        // Legacy axis mode: keep old X-then-Z transition behavior.
        if (hardPathMode() == PathMode.AxisFirst && onXAxis && px == destX) {
            onXAxis = false;
            if (optDebugMessages()) info("X axis done, now heading Z...");
        }

        int[] step = nextStep(px, pz);
        int stepX = step[0];
        int stepZ = step[1];
        watchdogCalc("mine-phase-step", String.format(Locale.ROOT, "pos=(%d,%d),step=(%d,%d),dest=(%d,%d),onXAxis=%s,pathMode=%s", px, pz, stepX, stepZ, destX, destZ, onXAxis, hardPathMode()));
        if (stepX == 0 && stepZ == 0) {
            if (atGoalReady) setPhase(Phase.DONE);
            else mc.options.forwardKey.setPressed(false);
            return;
        }

        // Collect solid blocks to break one step ahead.
        Set<BlockPos> toBreakSet = new LinkedHashSet<>();
        collectBreakColumn(toBreakSet, px + stepX, py, pz + stepZ);

        // For diagonal steps, also clear both corner-adjacent columns.
        if (stepX != 0 && stepZ != 0) {
            collectBreakColumn(toBreakSet, px + stepX, py, pz);
            collectBreakColumn(toBreakSet, px, py, pz + stepZ);
        }

        List<BlockPos> toBreak = new ArrayList<>(toBreakSet);
        renderBreakPositions.addAll(toBreak);
        watchdogCalc("mine-phase-breaklist", String.format(Locale.ROOT, "count=%d,first=%s", toBreak.size(), toBreak.isEmpty() ? "none" : formatPos(toBreak.get(0))));

        // If there are blocks to break, break them and stay in MINE phase.
        if (!toBreak.isEmpty()) {
            ensurePickaxe();
            int n = Math.min(optBreaksPerTick(), toBreak.size());
            if (optDebugMessages()) info("Breaking " + n + " blocks at " + toBreak.get(0).toShortString());

            for (int i = 0; i < n; i++) {
                BlockPos bp = toBreak.get(i);
                if (optFillBehind()) fillLog.putIfAbsent(bp, mc.world.getBlockState(bp));

                BlockUtils.breakBlock(bp, true);
            }

            blocksMined += n;
            return;
        }

        // Check for lava ahead and fill it.
        if (isAntiLavaActiveForCurrentMode() && hasLavaAhead(px, py, pz, stepX, stepZ)) {
            if (optDebugMessages()) info("Lava detected ahead, filling...");
            if (fillLavaAhead(px, py, pz, stepX, stepZ)) return;
        }

        // Profile is clear, transition to WALK.
        startWalk(px, pz, stepX, stepZ);
    }

    private void collectBreakColumn(Set<BlockPos> out, int x, int py, int z) {
        int added = 0;
        for (int h = 0; h < optTunnelHeight(); h++) {
            BlockPos bp = new BlockPos(x, py + h, z);
            BlockState bs = mc.world.getBlockState(bp);
            if (!bs.isAir() && bs.getBlock() != Blocks.BEDROCK && !isAvoidanceBlock(bs) && !touchesNoTouchChainBlock(bp) && !touchesLavaDetourRisk(bp)) {
                out.add(bp);
                added++;
            }
        }
        watchdogCalc("collect-break-column", String.format(Locale.ROOT, "column=(%d,%d),added=%d,tunnelHeight=%d", x, z, added, optTunnelHeight()));
    }

    // Check if there is lava in the tunnel path ahead
    private boolean hasLavaAhead(int px, int py, int pz, int stepX, int stepZ) {
        for (int d = 1; d <= 5; d++) {
            int x = px + stepX * d;
            int z = pz + stepZ * d;
            if (isLavaAtColumn(x, py, z)) {
                watchdogCalc("lava-ahead", String.format(Locale.ROOT, "result=true,pos=(%d,%d),d=%d,type=main", x, z, d));
                return true;
            }

            if (stepX != 0 && stepZ != 0) {
                int cornerA = pz + stepZ * (d - 1);
                int cornerB = px + stepX * (d - 1);
                if (isLavaAtColumn(x, py, cornerA)) {
                    watchdogCalc("lava-ahead", String.format(Locale.ROOT, "result=true,pos=(%d,%d),d=%d,type=cornerA", x, cornerA, d));
                    return true;
                }
                if (isLavaAtColumn(cornerB, py, z)) {
                    watchdogCalc("lava-ahead", String.format(Locale.ROOT, "result=true,pos=(%d,%d),d=%d,type=cornerB", cornerB, z, d));
                    return true;
                }
            }
        }
        watchdogCalc("lava-ahead", String.format(Locale.ROOT, "result=false,from=(%d,%d),step=(%d,%d)", px, pz, stepX, stepZ));
        return false;
    }

    // Try to fill lava ahead with blocks
    private boolean fillLavaAhead(int px, int py, int pz, int stepX, int stepZ) {
        BlockPos target = new BlockPos(px + stepX, py - 1, pz + stepZ);
        int slot = findTraversalPlacementSlot(target);
        if (slot == -1) {
            if (optDebugMessages()) warning("No blocks to fill lava with!");
            watchdogCalc("fill-lava-ahead", String.format(Locale.ROOT, "result=false,reason=no-slot,from=(%d,%d),step=(%d,%d)", px, pz, stepX, stepZ));
            return false;
        }

        // Try to place blocks on lava
        for (int d = 1; d <= 5; d++) {
            int x = px + stepX * d;
            int z = pz + stepZ * d;
            if (fillLavaAtColumn(py, x, z, slot)) {
                watchdogCalc("fill-lava-ahead", String.format(Locale.ROOT, "result=true,pos=(%d,%d),slot=%d,d=%d", x, z, slot, d));
                return true;
            }

            if (stepX != 0 && stepZ != 0) {
                int cornerA = pz + stepZ * (d - 1);
                int cornerB = px + stepX * (d - 1);
                if (fillLavaAtColumn(py, x, cornerA, slot)) {
                    watchdogCalc("fill-lava-ahead", String.format(Locale.ROOT, "result=true,pos=(%d,%d),slot=%d,d=%d,corner=A", x, cornerA, slot, d));
                    return true;
                }
                if (fillLavaAtColumn(py, cornerB, z, slot)) {
                    watchdogCalc("fill-lava-ahead", String.format(Locale.ROOT, "result=true,pos=(%d,%d),slot=%d,d=%d,corner=B", cornerB, z, slot, d));
                    return true;
                }
            }
        }
        watchdogCalc("fill-lava-ahead", String.format(Locale.ROOT, "result=false,from=(%d,%d),step=(%d,%d),slot=%d", px, pz, stepX, stepZ, slot));
        return false;
    }

    private boolean isLavaAtColumn(int x, int py, int z) {
        for (BlockPos lavaPos : collectLavaSealTargetsAtColumn(py, x, z)) {
            watchdogCalc("lava-column", String.format(Locale.ROOT, "result=true,column=(%d,%d),lava=%s", x, z, formatPos(lavaPos)));
            return true;
        }

        return false;
    }

    private boolean fillLavaAtColumn(int py, int x, int z, int slot) {
        int hb = toHotbar(slot);
        if (hb == -1) {
            watchdogCalc("fill-lava-column", String.format(Locale.ROOT, "result=false,pos=(%d,%d),reason=no-hotbar,slot=%d", x, z, slot));
            return false;
        }

        for (BlockPos bp : collectLavaSealTargetsAtColumn(py, x, z)) {
            InvUtils.swap(hb, true);
            if (tryAirPlaceAt(bp, hb, false)) {
                blocksMined++;
                watchdogCalc("fill-lava-column", String.format(Locale.ROOT, "result=true,column=(%d,%d),lava=%s,slot=%d,hb=%d", x, z, formatPos(bp), slot, hb));
                return true;
            }
        }

        watchdogCalc("fill-lava-column", String.format(Locale.ROOT, "result=false,pos=(%d,%d),slot=%d,hb=%d", x, z, slot, hb));
        return false;
    }

    private LinkedHashSet<BlockPos> collectLavaSealTargetsAtColumn(int py, int x, int z) {
        LinkedHashSet<BlockPos> targets = new LinkedHashSet<>();
        LinkedHashSet<BlockPos> probes = new LinkedHashSet<>();
        for (int h = 0; h < optTunnelHeight(); h++) probes.add(new BlockPos(x, py + h, z));
        probes.add(new BlockPos(x, py + optTunnelHeight(), z));

        for (BlockPos probe : probes) {
            if (isLavaState(mc.world.getBlockState(probe))) targets.add(probe.toImmutable());

            for (Direction dir : Direction.values()) {
                BlockPos adjacent = probe.offset(dir);
                if (isLavaState(mc.world.getBlockState(adjacent))) targets.add(adjacent.toImmutable());
            }
        }

        return targets;
    }

    // ── WALK ──────────────────────────────────────────────────────────────────
    // Move player toward the next block center, then either FILL or go back to MINE

    private void startWalk(int fromX, int fromZ, int stepX, int stepZ) {
        walkTargetX = (fromX + stepX) + 0.5;
        walkTargetZ = (fromZ + stepZ) + 0.5;
        watchdogCalc("start-walk", String.format(Locale.ROOT, "from=(%d,%d),step=(%d,%d),target=(%.3f,%.3f)", fromX, fromZ, stepX, stepZ, walkTargetX, walkTargetZ));
        if (optDebugMessages()) info("Path clear, walking to " + String.format("%.1f, %.1f", walkTargetX, walkTargetZ));
        setPhase(Phase.WALK);
    }

    private void walkPhase() {
        // Bridging: check below current pos and target pos
        BlockPos myFloor = mc.player.getBlockPos().down();
        BlockPos targetFloor = new BlockPos(MathHelper.floor(walkTargetX), tunnelY - 1, MathHelper.floor(walkTargetZ));

        if (placeFloor(myFloor) || placeFloor(targetFloor)) {
            return;
        }

        // Air place blocks ahead if enabled
        if (optAirPlace()) {
            if (placeAheadBlocks()) {
                return;
            }
        }

        moveToward(walkTargetX, walkTargetZ, () -> {
            // After reaching the target, check if we should fill
            if (optFillBehind() && !fillLog.isEmpty()) {
                setPhase(Phase.FILL);
            } else {
                setPhase(Phase.MINE);
            }
        });
    }

    private boolean placeFloor(BlockPos pos) {
        if (mc.world.getBlockState(pos).isAir() || !mc.world.getBlockState(pos).getFluidState().isEmpty()) {
            int slot = findTraversalPlacementSlot(pos);
            if (slot != -1) {
                int hb = toHotbar(slot);
                if (hb != -1) {
                    InvUtils.swap(hb, true);
                    if (tryAirPlaceAt(pos, hb, true)) {
                        watchdogCalc("place-floor", String.format(Locale.ROOT, "result=true,pos=%s,slot=%d,hb=%d", formatPos(pos), slot, hb));
                        return true;
                    }
                }
            }
            watchdogCalc("place-floor", String.format(Locale.ROOT, "result=false,pos=%s,slot=%d", formatPos(pos), slot));
        }
        return false;
    }

    // Place blocks ahead in the air to prevent falling
    private boolean placeAheadBlocks() {
        int px = MathHelper.floor(mc.player.getX());
        int py = tunnelY;
        int pz = MathHelper.floor(mc.player.getZ());
        int[] step = nextStep(px, pz);
        int stepX = step[0];
        int stepZ = step[1];

        if (stepX == 0 && stepZ == 0) {
            watchdogCalc("place-ahead", String.format(Locale.ROOT, "result=false,reason=no-step,pos=(%d,%d),step=(%d,%d)", px, pz, stepX, stepZ));
            return false;
        }

        for (int d = 1; d <= optAirPlaceDistance(); d++) {
            BlockPos placePos = new BlockPos(px + stepX * d, py - 1, pz + stepZ * d);
            if (mc.world.getBlockState(placePos).isAir()) {
                int slot = findTraversalPlacementSlot(placePos);
                if (slot == -1) {
                    watchdogCalc("place-ahead", String.format(Locale.ROOT, "result=false,reason=no-slot,pos=%s,d=%d", formatPos(placePos), d));
                    return false;
                }
                int hb = toHotbar(slot);
                if (hb == -1) {
                    watchdogCalc("place-ahead", String.format(Locale.ROOT, "result=false,reason=no-hotbar,pos=%s,slot=%d", formatPos(placePos), slot));
                    return false;
                }

                InvUtils.swap(hb, true);
                if (tryAirPlaceAt(placePos, hb, false)) {
                    watchdogCalc("place-ahead", String.format(Locale.ROOT, "result=true,pos=%s,d=%d,slot=%d,hb=%d", formatPos(placePos), d, slot, hb));
                    return true;
                }
            }
        }
        watchdogCalc("place-ahead", String.format(Locale.ROOT, "result=false,reason=no-placement,pos=(%d,%d),step=(%d,%d),dist=%d", px, pz, stepX, stepZ, optAirPlaceDistance()));
        return false;
    }
    /**
     * Moves the player toward (targetX, targetZ) by setting position each tick.
     * When distance < 0.5, snaps to target and calls onArrival.
     */
    private void moveToward(double targetX, double targetZ, Runnable onArrival) {
        double dx = targetX - mc.player.getX();
        double dz = targetZ - mc.player.getZ();
        double distSq = dx * dx + dz * dz;
        watchdogCalc("move-toward", String.format(Locale.ROOT, "target=(%.3f,%.3f),dx=%.5f,dz=%.5f,distSq=%.5f", targetX, targetZ, dx, dz, distSq));

        if (distSq < 0.04) { // < 0.2 blocks
            mc.options.forwardKey.setPressed(false);
            watchdogCalc("move-toward", String.format(Locale.ROOT, "arrived=true,target=(%.3f,%.3f)", targetX, targetZ));
            onArrival.run();
            return;
        }

        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        mc.player.setYaw(yaw);
        mc.options.forwardKey.setPressed(true);
        watchdogCalc("move-toward", String.format(Locale.ROOT, "arrived=false,yaw=%.3f", yaw));
    }

    // ── FILL ──────────────────────────────────────────────────────────────────
    // Re-place mined blocks behind the player, then return to MINE

    private void fillPhase() {
        if (!optFillBehind() || fillLog.isEmpty()) {
            watchdogCalc("fill-phase", String.format(Locale.ROOT, "result=skip,fillBehind=%s,fillLog=%d", optFillBehind(), fillLog.size()));
            setPhase(Phase.MINE);
            return;
        }

        boolean strictExactRestore = isStrictStealthExactRestoreEnabled();

        // Try air place first if enabled
        if (optAirPlace()) {
            if (placeAheadBlocks()) {
                return;
            }
        }

        int px = MathHelper.floor(mc.player.getX());
        int pz = MathHelper.floor(mc.player.getZ());
        int placed = 0;
        int scanned = 0;
        int skippedNear = 0;
        int skippedFar = 0;
        int removedAlreadyFilled = 0;
        int skippedNoSupport = 0;
        int skippedNoSlot = 0;
        int skippedNoHotbar = 0;

        Iterator<Map.Entry<BlockPos, BlockState>> it = fillLog.entrySet().iterator();
        while (it.hasNext() && placed < optPlacesPerTick()) {
            Map.Entry<BlockPos, BlockState> e = it.next();
            BlockPos pos = e.getKey();
            BlockState original = e.getValue();
            BlockState current = mc.world.getBlockState(pos);
            scanned++;

            // Skip if player is standing on or next to this block
            if (Math.abs(pos.getX() - px) <= 1 && Math.abs(pos.getZ() - pz) <= 1) {
                skippedNear++;
                continue;
            }

            // Skip if too far away
            if (Vec3d.ofCenter(pos).distanceTo(mc.player.getEyePos()) > 4 + Objects.requireNonNull(Modules.get().get(Reach.class)).blockReach()) {
                skippedFar++;
                continue;
            }

            // Remove only when restore criteria are satisfied at this exact position.
            if (isRestoreSatisfied(original, current, strictExactRestore)) {
                it.remove();
                removedAlreadyFilled++;
                continue;
            }

            if (isLavaState(original) || isLavaState(current)) {
                skippedNoSupport++;
                continue;
            }

            if (original.isAir()) {
                if (current.isAir()) {
                    it.remove();
                    removedAlreadyFilled++;
                    continue;
                }

                if (BlockUtils.canBreak(pos, current)) {
                    ensurePickaxe();
                    BlockUtils.breakBlock(pos, true);
                } else {
                    skippedNoSupport++;
                }
                continue;
            }

            if (!current.isAir()) {
                if (BlockUtils.canBreak(pos, current)) {
                    ensurePickaxe();
                    BlockUtils.breakBlock(pos, true);
                } else {
                    skippedNoSupport++;
                }
                continue;
            }

            if (!optAirPlace() && mc.world.getBlockState(pos.down()).isAir()) {
                skippedNoSupport++;
                continue;
            }

            int slot = findExactBlockInInv(original.getBlock());
            boolean exactRestore = true;
            if (slot == -1) {
                if (strictExactRestore) {
                    hardFailStealthExactRestore(pos, original, "fill");
                    return;
                }

                slot = findFallbackRestoreSlot(pos);
                exactRestore = false;
                if (slot == -1) {
                    skippedNoSlot++;
                    continue;
                }
            }

            int hb = toHotbar(slot);
            if (hb == -1) {
                skippedNoHotbar++;
                continue; // Hotbar full, try next block
            }

            InvUtils.swap(hb, true);
            if (tryAirPlaceAt(pos, hb, false)) {
                BlockState after = mc.world.getBlockState(pos);
                if (isRestoreSatisfied(original, after, strictExactRestore)) {
                    it.remove();
                    placed++;
                }
            }
        }

        // If we placed blocks, wait before going back to mine
        if (placed > 0) {
            if (optDebugMessages()) { info("Placed " + placed + " blocks behind.");}

            placeTimer = optPlaceDelay();
            ensurePickaxe();
            watchdogCalc("fill-phase", String.format(Locale.ROOT, "result=placed,placed=%d,scanned=%d,skippedNear=%d,skippedFar=%d,removedAlreadyFilled=%d,skippedNoSupport=%d,skippedNoSlot=%d,skippedNoHotbar=%d,remaining=%d", placed, scanned, skippedNear, skippedFar, removedAlreadyFilled, skippedNoSupport, skippedNoSlot, skippedNoHotbar, fillLog.size()));
            return;
        }

        // If we didn't place anything (e.g. because blocks are too close), go back to mining
        watchdogCalc("fill-phase", String.format(Locale.ROOT, "result=no-place,placed=%d,scanned=%d,skippedNear=%d,skippedFar=%d,removedAlreadyFilled=%d,skippedNoSupport=%d,skippedNoSlot=%d,skippedNoHotbar=%d,remaining=%d", placed, scanned, skippedNear, skippedFar, removedAlreadyFilled, skippedNoSupport, skippedNoSlot, skippedNoHotbar, fillLog.size()));
        setPhase(Phase.MINE);
    }

    // ── HUD getters ───────────────────────────────────────────────────────────

    private RestockPlacement computeRestockPlacement(int px, int py, int pz) {
        int[] step = nextStep(px, pz);
        int stepX = step[0];
        int stepZ = step[1];

        if (stepX == 0 && stepZ == 0) {
            Direction facing = mc.player.getHorizontalFacing();
            stepX = facing.getOffsetX();
            stepZ = facing.getOffsetZ();
        }

        // Reserve one column directly behind the player for container placement.
        BlockPos pos = new BlockPos(px - stepX, py, pz - stepZ);
        return new RestockPlacement(pos, stepX, stepZ);
    }

    private boolean isBehindForRestock(int px, int pz, int forwardX, int forwardZ, BlockPos pos) {
        int relX = pos.getX() - px;
        int relZ = pos.getZ() - pz;
        return (relX * forwardX + relZ * forwardZ) < 0;
    }

    private boolean hasPendingRestockBehindParity(int px, int pz, int forwardX, int forwardZ, int reservedX, int reservedZ, boolean reachableOnly) {
        double reach = 4 + Objects.requireNonNull(Modules.get().get(Reach.class)).blockReach();
        boolean strictExactRestore = isStrictStealthExactRestoreEnabled();

        for (Map.Entry<BlockPos, BlockState> entry : stealthCache.entrySet()) {
            BlockPos pos = entry.getKey();
            BlockState original = entry.getValue();
            BlockState current = mc.world.getBlockState(pos);

            if (pos.getX() == reservedX && pos.getZ() == reservedZ) continue;
            if (!isBehindForRestock(px, pz, forwardX, forwardZ, pos)) continue;
            if (isLavaState(original)) continue;
            if (isLavaState(current)) continue;
            if (isRestoreSatisfied(original, current, strictExactRestore)) continue;
            if (reachableOnly && Vec3d.ofCenter(pos).distanceTo(mc.player.getEyePos()) > reach) continue;
            return true;
        }

        return false;
    }

    private boolean restoreBehindForRestock(int px, int pz, int forwardX, int forwardZ, int reservedX, int reservedZ) {
        int actions = 0;
        int maxActions = Math.max(1, optPlacesPerTick());
        boolean placed = false;
        double reach = 4 + Objects.requireNonNull(Modules.get().get(Reach.class)).blockReach();
        boolean strictExactRestore = isStrictStealthExactRestoreEnabled();

        int scanned = 0;
        int skippedNonBehind = 0;
        int skippedReserved = 0;
        int skippedReach = 0;
        int removedMatch = 0;
        int removedLava = 0;
        int brokeForAir = 0;
        int brokeForSolid = 0;
        int deferredUnbreakable = 0;
        int removedAdjacentAir = 0;
        int skippedNoRestoreSlot = 0;
        int skippedNoHotbar = 0;
        int placedExact = 0;
        int placedFallback = 0;

        Iterator<Map.Entry<BlockPos, BlockState>> it = stealthCache.entrySet().iterator();
        while (it.hasNext() && actions < maxActions) {
            Map.Entry<BlockPos, BlockState> entry = it.next();
            BlockPos pos = entry.getKey();
            BlockState original = entry.getValue();
            BlockState current = mc.world.getBlockState(pos);
            scanned++;

            if (pos.getX() == reservedX && pos.getZ() == reservedZ) {
                skippedReserved++;
                continue;
            }
            if (!isBehindForRestock(px, pz, forwardX, forwardZ, pos)) {
                skippedNonBehind++;
                continue;
            }

            if (isRestoreSatisfied(original, current, strictExactRestore)) {
                it.remove();
                removedMatch++;
                continue;
            }

            if (Vec3d.ofCenter(pos).distanceTo(mc.player.getEyePos()) > reach) {
                skippedReach++;
                continue;
            }

            if (isLavaState(original) || isLavaState(current)) {
                removedLava++;
                continue;
            }

            if (original.isAir()) {
                if (current.isAir()) {
                    it.remove();
                    removedMatch++;
                    continue;
                }
                if (!BlockUtils.canBreak(pos, current)) {
                    deferredUnbreakable++;
                    continue;
                }
                ensurePickaxe();
                BlockUtils.breakBlock(pos, true);
                actions++;
                brokeForAir++;
                continue;
            }

            if (!current.isAir()) {
                if (!BlockUtils.canBreak(pos, current)) {
                    deferredUnbreakable++;
                    continue;
                }
                ensurePickaxe();
                BlockUtils.breakBlock(pos, true);
                actions++;
                brokeForSolid++;
                continue;
            }

            int slot = findExactBlockInInv(original.getBlock());
            boolean exactRestore = true;
            if (slot == -1) {
                if (strictExactRestore) {
                    hardFailStealthExactRestore(pos, original, "restock-restore");
                    return false;
                }
                slot = findFallbackRestoreSlot(pos);
                exactRestore = false;
                if (slot == -1) {
                    skippedNoRestoreSlot++;
                    continue;
                }
            }

            int hb = toHotbar(slot);
            if (hb == -1) {
                skippedNoHotbar++;
                continue;
            }

            InvUtils.swap(hb, true);
            if (tryAirPlaceAt(pos, hb, false)) {
                actions++;
                placed = true;
                BlockState after = mc.world.getBlockState(pos);
                if (isRestoreSatisfied(original, after, strictExactRestore)) {
                    it.remove();
                    if (exactRestore) placedExact++;
                    else placedFallback++;
                }
            }
        }

        if (placed) {
            placeTimer = optPlaceDelay();
            ensurePickaxe();
        }

        boolean pendingReachable = hasPendingRestockBehindParity(px, pz, forwardX, forwardZ, reservedX, reservedZ, true);
        boolean pendingAny = hasPendingRestockBehindParity(px, pz, forwardX, forwardZ, reservedX, reservedZ, false);
        watchdogCalc(
            "restock-restore",
            String.format(
                Locale.ROOT,
                "scanned=%d,actions=%d,maxActions=%d,placed=%s,skippedNonBehind=%d,skippedReserved=%d,skippedReach=%d,removedMatch=%d,removedLava=%d,brokeForAir=%d,brokeForSolid=%d,deferredUnbreakable=%d,removedAdjacentAir=%d,skippedNoRestoreSlot=%d,skippedNoHotbar=%d,placedExact=%d,placedFallback=%d,pendingReachable=%s,pendingAny=%s,reserved=(%d,%d),forward=(%d,%d)",
                scanned,
                actions,
                maxActions,
                placed,
                skippedNonBehind,
                skippedReserved,
                skippedReach,
                removedMatch,
                removedLava,
                brokeForAir,
                brokeForSolid,
                deferredUnbreakable,
                removedAdjacentAir,
                skippedNoRestoreSlot,
                skippedNoHotbar,
                placedExact,
                placedFallback,
                pendingReachable,
                pendingAny,
                reservedX,
                reservedZ,
                forwardX,
                forwardZ
            )
        );

        return !pendingReachable;
    }

    private void restockClear() {
        int px = MathHelper.floor(mc.player.getX());
        int py = tunnelY;
        int pz = MathHelper.floor(mc.player.getZ());
        RestockPlacement placement = computeRestockPlacement(px, py, pz);
        containerPos = placement.containerPos();
        boolean maintainBehindParity = shouldMaintainBehindParity();

        activeProbePositions.clear();
        if (maintainBehindParity && !restoreBehindForRestock(px, pz, placement.forwardStepX(), placement.forwardStepZ(), containerPos.getX(), containerPos.getZ())) {
            return;
        }

        boolean clear = true;
        for (int h = 0; h < optTunnelHeight(); h++) {
            BlockPos bp = containerPos.up(h);
            BlockState state = mc.world.getBlockState(bp);
            if (state.isAir()) continue;

            if (maintainBehindParity) stealthCache.putIfAbsent(bp.toImmutable(), state);
            ensurePickaxe();
            BlockUtils.breakBlock(bp, true);
            clear = false;
            waitTicks++;
            if (waitTicks > MAX_WAIT) {
                if (optDebugMessages()) warning("Can't clear reserved restock column.");
                hardFailRestock("clear-timeout");
            }
            return;
        }

        if (clear) {
            waitTicks = 0;
            int sk = findPreferredRestockShulkerSlot();
            int ec = (!restockEcSearchExhausted && optUseEnderChest()) ? findInInv(TunnelMinerModule::isEnderChest) : -1;
            if (sk != -1) {
                restockEC = false;
                restockPreferShulkerNext = false;
                restockPlaceRetries = 0;
                if (optDebugMessages()) info("Found shulker box for restock.");
                watchdogCalc(
                    "restock-clear-select",
                    String.format(
                        Locale.ROOT,
                        "container=shulker,slot=%d,pickShulker=%s,preferNext=%s,fromEcExtract=%s,ecExhausted=%s",
                        sk,
                        shulkerContainsPickaxe(mc.player.getInventory().getStack(sk)),
                        restockPreferShulkerNext,
                        restockEcExtractedPickShulker,
                        restockEcSearchExhausted
                    )
                );
                setPhase(Phase.RESTOCK_PLACE);
            } else if (ec != -1) {
                restockEC = true;
                restockPlaceRetries = 0;
                if (optDebugMessages()) info("Found ender chest for restock.");
                watchdogCalc(
                    "restock-clear-select",
                    String.format(
                        Locale.ROOT,
                        "container=ender-chest,slot=%d,ecExhausted=%s",
                        ec,
                        restockEcSearchExhausted
                    )
                );
                setPhase(Phase.RESTOCK_PLACE);
            } else {
                watchdogCalc(
                    "restock-clear-select",
                    String.format(
                        Locale.ROOT,
                        "container=none,ecExhausted=%s,preferNext=%s,fromEcExtract=%s",
                        restockEcSearchExhausted,
                        restockPreferShulkerNext,
                        restockEcExtractedPickShulker
                    )
                );
                if (optDebugMessages()) warning("No restock container - stopping.");
                hardFailRestock("no-restock-container");
            }
        }
    }

    private void restockPlace() {
        if (!centerForRestockPlacement()) return;

        int px = MathHelper.floor(mc.player.getX());
        int py = tunnelY;
        int pz = MathHelper.floor(mc.player.getZ());
        RestockPlacement placement = computeRestockPlacement(px, py, pz);
        containerPos = placement.containerPos();
        restockCleanupPos = containerPos.toImmutable();

        BlockPos floor = containerPos.down();
        BlockState floorState = mc.world.getBlockState(floor);
        if (floorState.isAir()) {
            if (shouldMaintainBehindParity()) stealthCache.putIfAbsent(floor.toImmutable(), floorState);
            int supportSlot = findTraversalPlacementSlot(floor);
            if (supportSlot == -1) {
                if (optDebugMessages()) warning("No floor and no support block for restock container.");
                hardFailRestock("no-support-block-for-container");
                return;
            }
            int supportHb = toHotbar(supportSlot);
            if (supportHb == -1) {
                if (optDebugMessages()) warning("No hotbar slot for restock support block.");
                hardFailRestock("no-hotbar-slot-for-support");
                return;
            }
            InvUtils.swap(supportHb, true);
            if (!tryDirectPlaceAt(floor, supportHb, false)) {
                if (optDebugMessages()) warning("Failed to place floor support for restock container.");
                hardFailRestock("failed-place-support");
                return;
            }
        }

        int slot = restockEC ? findInInv(TunnelMinerModule::isEnderChest)
            : findPreferredRestockShulkerSlot();
        if (slot == -1) {
            if (optDebugMessages()) warning("Lost container!");
            hardFailRestock("lost-container-item");
            return;
        }
        int hb = toHotbar(slot);
        if (hb == -1) {
            if (optDebugMessages()) warning("Hotbar full!");
            hardFailRestock("no-hotbar-slot-for-container");
            return;
        }
        InvUtils.swap(hb, true);

        if (optDebugMessages()) info("Placing restock container at " + containerPos.toShortString());
        if (!tryDirectPlaceAt(containerPos, hb, false)) {
            if (optDebugMessages()) warning("Failed to place restock container.");
            return;
        }

        boolean placedNow = restockEC
            ? mc.world.getBlockState(containerPos).getBlock() == Blocks.ENDER_CHEST
            : mc.world.getBlockState(containerPos).getBlock() instanceof ShulkerBoxBlock;
        if (!placedNow) {
            restockPlaceRetries++;
            watchdogCalc(
                "restock-place-verify",
                String.format(
                    Locale.ROOT,
                    "result=retry,reason=immediate-container-miss,retry=%d,maxRetries=%d,pos=%s,restockEC=%s",
                    restockPlaceRetries,
                    MAX_RESTOCK_PLACE_RETRIES,
                    formatPos(containerPos),
                    restockEC
                )
            );
            if (restockPlaceRetries > MAX_RESTOCK_PLACE_RETRIES) {
                if (optDebugMessages()) warning("Container failed immediate placement verification.");
                hardFailRestock("container-immediate-verify-failed");
                return;
            }
            waitTicks = 0;
            invTimer = optInvDelay();
            setPhase(Phase.RESTOCK_PLACE);
            return;
        }

        ensurePickaxe();
        invTimer = optInvDelay();
        waitTicks = 0;
        setPhase(Phase.RESTOCK_WAIT);
    }

    private boolean centerForRestockPlacement() {
        int px = MathHelper.floor(mc.player.getX());
        int pz = MathHelper.floor(mc.player.getZ());
        double targetX = px + 0.5;
        double targetZ = pz + 0.5;
        double dx = targetX - mc.player.getX();
        double dz = targetZ - mc.player.getZ();
        double distSq = dx * dx + dz * dz;

        // Keep this tight so we are reliably centered before attempting placement.
        if (distSq <= 0.0025) {
            mc.options.forwardKey.setPressed(false);
            waitTicks = 0;
            watchdogCalc(
                "restock-center",
                String.format(
                    Locale.ROOT,
                    "result=centered,target=(%.3f,%.3f),distSq=%.6f",
                    targetX,
                    targetZ,
                    distSq
                )
            );
            return true;
        }

        // If close enough for moveToward "arrival", snap exactly to .5/.5 like HighwayBuilder.
        if (distSq < 0.04) {
            mc.options.forwardKey.setPressed(false);
            mc.player.setVelocity(0, 0, 0);
            mc.player.setPosition(targetX, mc.player.getY(), targetZ);
            waitTicks = 0;
            watchdogCalc(
                "restock-center",
                String.format(
                    Locale.ROOT,
                    "result=snap-exact,target=(%.3f,%.3f),distSq=%.6f",
                    targetX,
                    targetZ,
                    distSq
                )
            );
            return true;
        }

        waitTicks++;
        if (waitTicks > MAX_WAIT) {
            mc.options.forwardKey.setPressed(false);
            mc.player.setVelocity(0, 0, 0);
            mc.player.setPosition(targetX, mc.player.getY(), targetZ);
            watchdogCalc(
                "restock-center",
                String.format(
                    Locale.ROOT,
                    "result=fallback-timeout-snap,target=(%.3f,%.3f),distSq=%.6f,waitTicks=%d,maxWait=%d",
                    targetX,
                    targetZ,
                    distSq,
                    waitTicks,
                    MAX_WAIT
                )
            );
            waitTicks = 0;
            return true;
        }

        watchdogCalc(
            "restock-center",
            String.format(
                Locale.ROOT,
                "result=moving,target=(%.3f,%.3f),distSq=%.6f,waitTicks=%d,maxWait=%d",
                targetX,
                targetZ,
                distSq,
                waitTicks,
                MAX_WAIT
            )
        );
        moveToward(targetX, targetZ, () -> {});
        return false;
    }

    private void restockWait() {
        waitTicks++;
        boolean here = restockEC
            ? mc.world.getBlockState(containerPos).getBlock() == Blocks.ENDER_CHEST
            : mc.world.getBlockState(containerPos).getBlock() instanceof ShulkerBoxBlock;
        if (here) {
            waitTicks = 0;
            restockPlaceRetries = 0;
            setPhase(Phase.RESTOCK_OPEN);
            return;
        }
            if (waitTicks > MAX_WAIT) {
                if (restockPlaceRetries < MAX_RESTOCK_PLACE_RETRIES) {
                restockPlaceRetries++;
                watchdogCalc(
                    "restock-wait-timeout",
                    String.format(
                        Locale.ROOT,
                        "result=retry,reason=container-not-present,retry=%d,maxRetries=%d,waitTicks=%d,pos=%s,restockEC=%s",
                        restockPlaceRetries,
                        MAX_RESTOCK_PLACE_RETRIES,
                        waitTicks,
                        formatPos(containerPos),
                        restockEC
                    )
                );
                if (optDebugMessages()) warning("Container didn't appear. Retrying place (" + restockPlaceRetries + "/" + MAX_RESTOCK_PLACE_RETRIES + ").");
                waitTicks = 0;
                setPhase(Phase.RESTOCK_PLACE);
                return;
            }

            watchdogCalc(
                "restock-wait-timeout",
                String.format(
                    Locale.ROOT,
                    "result=fail,reason=container-not-present,retry=%d,maxRetries=%d,waitTicks=%d,pos=%s,restockEC=%s,action=toggle",
                    restockPlaceRetries,
                    MAX_RESTOCK_PLACE_RETRIES,
                    waitTicks,
                    formatPos(containerPos),
                    restockEC
                )
            );
            if (optDebugMessages()) warning("Container didn't appear.");
            hardFailRestock("container-did-not-appear");
        }
    }

    private void restockOpen() {
        waitTicks++;
        if (waitTicks == 1) {
            BlockPos bp = containerPos;
            Rotations.rotate(Rotations.getYaw(bp), Rotations.getPitch(bp),
                () -> mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND,
                    new BlockHitResult(Vec3d.ofCenter(bp), Direction.UP, bp, false)));
            return;
        }
        if (mc.currentScreen != null) {
            waitTicks = 0;
            invTimer = optInvDelay();
            if (optDebugMessages()) info("Container open, looting pickaxes.");
            setPhase(Phase.RESTOCK_LOOT);
            return;
        }
        if (waitTicks > MAX_WAIT) {
            if (optDebugMessages()) warning("Container didn't open.");
            hardFailRestock("container-did-not-open");
        }
    }

    private void restockLoot() {
        if (mc.currentScreen == null) {
            watchdogCalc("restock-loot", "action=close-screen-null");
            setPhase(Phase.RESTOCK_CLOSE);
            return;
        }

        // Count free slots — must keep AT LEAST 1 free for the container item drop
        int free = 0;
        for (int i = 0; i < mc.player.getInventory().size(); i++) {
            if (mc.player.getInventory().getStack(i).isEmpty()) {
                free++;
            }
        }
        if (free <= 1) {
            watchdogCalc("restock-loot", String.format(Locale.ROOT, "action=close-inventory-full,free=%d", free));
            if (optDebugMessages()) info("Inventory full, closing container.");
            setPhase(Phase.RESTOCK_CLOSE);
            return;
        }

        // Only grab pickaxes
        int currentPickaxes = countPickaxes();
        int closeTarget = restockEC ? (optMinPickaxes() + 1) : optMinPickaxes();
        if (currentPickaxes >= closeTarget) {
            watchdogCalc(
                "restock-loot",
                String.format(
                    Locale.ROOT,
                    "action=close-target-met,pickaxes=%d,target=%d,restockEC=%s",
                    currentPickaxes,
                    closeTarget,
                    restockEC
                )
            );
            if (optDebugMessages()) info("Have enough pickaxes, closing container.");
            setPhase(Phase.RESTOCK_CLOSE);
            return;
        }

        var handler = mc.player.currentScreenHandler;
        int containerSlots = Math.min(27, handler.slots.size());
        for (int i = 0; i < containerSlots; i++) {
            if (isPickaxe(handler.slots.get(i).getStack())) {
                InvUtils.shiftClick().slotId(i);
                watchdogCalc(
                    "restock-loot",
                    String.format(
                        Locale.ROOT,
                        "action=picked-pickaxe,slot=%d,containerSlots=%d,pickaxes=%d,target=%d",
                        i,
                        containerSlots,
                        currentPickaxes,
                        optMinPickaxes()
                    )
                );
                invTimer = optInvDelay();
                return;
            }
        }

        if (restockEC) {
            int minimum = optMinPickaxes();
            int ecTarget = minimum + 1;
            int projected = currentPickaxes + countPickaxesStoredInInventoryShulkers();
            if (projected >= ecTarget) {
                restockPreferShulkerNext = true;
                restockFailOutOfPickaxes = false;
                watchdogCalc(
                    "restock-loot",
                    String.format(
                        Locale.ROOT,
                        "action=close-ec-to-shulker-stage,reason=projected-target-met,projected=%d,pickaxes=%d,target=%d,min=%d,containerSlots=%d",
                        projected,
                        currentPickaxes,
                        ecTarget,
                        minimum,
                        containerSlots
                    )
                );
                if (optDebugMessages()) info("Ender chest search complete; switching to shulker restock.");
                setPhase(Phase.RESTOCK_CLOSE);
                return;
            }

            int remaining = ecTarget - projected;
            int shulkersSeen = 0;
            int shulkersWithPick = 0;
            int bestSlot = -1;
            int bestPickCount = 0;
            for (int i = 0; i < containerSlots; i++) {
                ItemStack stack = handler.slots.get(i).getStack();
                if (!isShulkerBox(stack)) continue;
                shulkersSeen++;
                int picksInShulker = countPickaxesInShulker(stack);
                if (picksInShulker <= 0) continue;
                shulkersWithPick++;

                boolean better = false;
                if (bestSlot == -1) better = true;
                else if (bestPickCount >= remaining) {
                    better = picksInShulker >= remaining && picksInShulker < bestPickCount;
                } else {
                    better = picksInShulker >= remaining || picksInShulker > bestPickCount;
                }

                if (better) {
                    bestSlot = i;
                    bestPickCount = picksInShulker;
                }
            }

            if (bestSlot != -1) {
                InvUtils.shiftClick().slotId(bestSlot);
                restockEcExtractedPickShulker = true;
                restockPreferShulkerNext = true;
                restockFailOutOfPickaxes = false;
                watchdogCalc(
                    "restock-loot",
                    String.format(
                        Locale.ROOT,
                        "action=ec-picked-shulker-stage,slot=%d,containerSlots=%d,shulkersSeen=%d,shulkersWithPick=%d,picksInShulker=%d,pickaxes=%d,projectedBefore=%d,remainingBefore=%d,target=%d,min=%d,next=shulker-stage",
                        bestSlot,
                        containerSlots,
                        shulkersSeen,
                        shulkersWithPick,
                        bestPickCount,
                        currentPickaxes,
                        projected,
                        remaining,
                        ecTarget,
                        minimum
                    )
                );
                invTimer = optInvDelay();
                setPhase(Phase.RESTOCK_CLOSE);
                return;
            }

            restockEcSearchExhausted = true;
            int invShulkerPicks = countPickaxesStoredInInventoryShulkers();
            int invPickShulkerSlot = findInInv(this::shulkerContainsPickaxe);
            boolean canContinueShulkerStage = invPickShulkerSlot != -1;
            restockPreferShulkerNext = canContinueShulkerStage;
            restockFailOutOfPickaxes = !canContinueShulkerStage && currentPickaxes < minimum;
            watchdogCalc(
                "restock-loot",
                String.format(
                    Locale.ROOT,
                    "action=close-ec-search-exhausted,reason=no-pickaxe-or-pickaxe-shulker,containerSlots=%d,shulkersSeen=%d,shulkersWithPick=%d,pickaxes=%d,invShulkerPicks=%d,invPickShulkerSlot=%d,min=%d,target=%d,nextShulkerStage=%s,hardFail=%s",
                    containerSlots,
                    shulkersSeen,
                    shulkersWithPick,
                    currentPickaxes,
                    invShulkerPicks,
                    invPickShulkerSlot,
                    minimum,
                    ecTarget,
                    canContinueShulkerStage,
                    (!canContinueShulkerStage && currentPickaxes < minimum)
                )
            );
            if (optDebugMessages()) {
                if (canContinueShulkerStage) info("Ender chest search complete; switching to shulker restock.");
                else info("No viable pickaxe source found in ender chest.");
            }
            setPhase(Phase.RESTOCK_CLOSE);
            return;
        }

        watchdogCalc(
            "restock-loot",
            String.format(
                Locale.ROOT,
                "action=close-no-picks,reason=no-pickaxe-in-container,containerSlots=%d,pickaxes=%d,target=%d",
                containerSlots,
                currentPickaxes,
                optMinPickaxes()
            )
        );
        restockFailOutOfPickaxes = false;
        if (optDebugMessages()) info("No more pickaxes in container, closing.");
        setPhase(Phase.RESTOCK_CLOSE);
    }

    private void restockClose() {
        if (mc.currentScreen != null) {
            mc.currentScreen.close();
            invTimer = optInvDelay();
            return;
        }
        waitTicks = 0;
        setPhase(Phase.RESTOCK_BREAK);
    }

    private void restockBreak() {
        if (containerPos == null) {
            setPhase(Phase.RESTOCK_PICKUP);
            return;
        }
        boolean here = mc.world.getBlockState(containerPos).getBlock() instanceof ShulkerBoxBlock
            || mc.world.getBlockState(containerPos).getBlock() == Blocks.ENDER_CHEST;
        if (here) {
            restockCleanupPos = containerPos.toImmutable();
            ensurePickaxe();
            BlockUtils.breakBlock(containerPos, true);
            return;
        }
        waitTicks = 0;
        setPhase(Phase.RESTOCK_PICKUP);
    }

    private boolean isRestockDropItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (stack.isOf(Items.ENDER_CHEST) || stack.isOf(Items.OBSIDIAN)) return true;
        return stack.getItem() instanceof BlockItem bi && bi.getBlock() instanceof ShulkerBoxBlock;
    }

    private List<ItemEntity> collectRestockDropEntities() {
        BlockPos center = restockCleanupPos != null
            ? restockCleanupPos
            : (containerPos != null ? containerPos : mc.player.getBlockPos());
        Box area = new Box(center).expand(2.5, 1.5, 2.5);
        return mc.world.getEntitiesByClass(
            ItemEntity.class,
            area,
            entity -> entity != null && entity.isAlive() && isRestockDropItem(entity.getStack())
        );
    }

    private String summarizeRestockDropEntities(List<ItemEntity> drops) {
        HashMap<String, Integer> counts = new HashMap<>();
        for (ItemEntity drop : drops) {
            ItemStack stack = drop.getStack();
            String id = Registries.ITEM.getId(stack.getItem()).toString();
            counts.merge(id, stack.getCount(), Integer::sum);
        }
        ArrayList<Map.Entry<String, Integer>> entries = new ArrayList<>(counts.entrySet());
        entries.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        StringBuilder out = new StringBuilder();
        for (Map.Entry<String, Integer> entry : entries) {
            if (out.length() > 0) out.append(';');
            out.append(entry.getKey()).append(':').append(entry.getValue());
        }
        return out.toString();
    }

    private void restockPickup() {
        List<ItemEntity> drops = collectRestockDropEntities();
        if (!drops.isEmpty()) {
            waitTicks++;

            ItemEntity nearestDrop = null;
            double nearestDistSq = Double.POSITIVE_INFINITY;
            for (ItemEntity drop : drops) {
                double distSq = mc.player.squaredDistanceTo(drop.getX(), drop.getY(), drop.getZ());
                if (distSq < nearestDistSq) {
                    nearestDistSq = distSq;
                    nearestDrop = drop;
                }
            }

            // Move toward pending restock drops every other tick to improve pickup reliability.
            boolean moveThisTick = (waitTicks & 1) == 0 && nearestDrop != null;
            if (moveThisTick) {
                moveToward(nearestDrop.getX(), nearestDrop.getZ(), () -> {});
            } else {
                mc.options.forwardKey.setPressed(false);
            }

            watchdogCalc(
                "restock-pickup",
                String.format(
                    Locale.ROOT,
                    "waiting=true,reason=drop-entities,pending=%d,ticks=%d,items=%s,center=%s,moveEveryOtherTick=true,moveThisTick=%s,target=%s,nearestDistSq=%.3f",
                    drops.size(),
                    waitTicks,
                    summarizeRestockDropEntities(drops),
                    restockCleanupPos == null ? "null" : formatPos(restockCleanupPos),
                    moveThisTick,
                    nearestDrop == null ? "none" : formatPos(nearestDrop.getBlockPos()),
                    nearestDistSq
                )
            );
            if (waitTicks > MAX_WAIT * 3) {
                if (optDebugMessages()) warning("Restock drops not picked up in time; stopping.");
                hardFailRestock("drops-not-picked-up");
            }
            return;
        }

        mc.options.forwardKey.setPressed(false);
        if (++waitTicks < 8) return;
        containerPos = null;
        restockCleanupPos = null;
        waitTicks = 0;
        int currentPickaxes = countPickaxes();
        int minimum = optMinPickaxes();
        boolean stillNeedsRestock = needsRestock();
        int invPickShulkerSlot = findInInv(this::shulkerContainsPickaxe);
        int invAnyShulkerSlot = findInInv(TunnelMinerModule::isShulkerBox);
        boolean forcedShulkerStage = restockPreferShulkerNext || restockEcExtractedPickShulker;
        boolean canRunShulkerStage = invPickShulkerSlot != -1
            || (forcedShulkerStage && invAnyShulkerSlot != -1)
            || (optUseShulkers() && !optUseEnderChest() && invAnyShulkerSlot != -1);
        boolean canRunEcStage = optUseEnderChest()
            && !restockEcSearchExhausted
            && findInInv(TunnelMinerModule::isEnderChest) != -1;

        if (canRunShulkerStage && (stillNeedsRestock || restockEcExtractedPickShulker)) {
            restockEC = false;
            restockPreferShulkerNext = true;
            restockFailOutOfPickaxes = false;
            restockEcExtractedPickShulker = false;
            watchdogCalc(
                "restock-pickup",
                String.format(
                    Locale.ROOT,
                    "result=continue,stage=inventory-shulker,pickaxes=%d,min=%d,needsRestock=%s,pickShulkerSlot=%d,anyShulkerSlot=%d",
                    currentPickaxes,
                    minimum,
                    stillNeedsRestock,
                    invPickShulkerSlot,
                    invAnyShulkerSlot
                )
            );
            setPhase(Phase.RESTOCK_CLEAR);
            return;
        }

        if (stillNeedsRestock && canRunEcStage) {
            restockEC = true;
            restockPreferShulkerNext = false;
            restockFailOutOfPickaxes = false;
            watchdogCalc(
                "restock-pickup",
                String.format(
                    Locale.ROOT,
                    "result=continue,stage=ender-chest,pickaxes=%d,min=%d,needsRestock=%s,ecExhausted=%s",
                    currentPickaxes,
                    minimum,
                    stillNeedsRestock,
                    restockEcSearchExhausted
                )
            );
            setPhase(Phase.RESTOCK_CLEAR);
            return;
        }

        if (stillNeedsRestock || (restockFailOutOfPickaxes && currentPickaxes < minimum)) {
            watchdogCalc(
                "restock-pickup",
                String.format(
                    Locale.ROOT,
                    "result=fail-out-of-pickaxes,pickaxes=%d,target=%d,needsRestock=%s,ecExhausted=%s,pickShulkerSlot=%d,anyShulkerSlot=%d,action=toggle",
                    currentPickaxes,
                    minimum,
                    stillNeedsRestock,
                    restockEcSearchExhausted,
                    invPickShulkerSlot,
                    invAnyShulkerSlot
                )
            );
            restockFailOutOfPickaxes = false;
            warning("Out of pickaxes - stopping Tunnel Miner.");
            hardFailRestock("out-of-pickaxes");
            return;
        }

        resetRestockStageState();
        pickSlot = equipBestPickaxe();
        if (optDebugMessages()) info("Restock done - resuming.");
        setPhase(Phase.MINE);
    }

    private void hardFailRestock(String reason) {
        watchdogCalc("restock-hard-fail", "reason=" + sanitizeLogField(reason));
        if (mc.getNetworkHandler() != null && mc.getNetworkHandler().getConnection() != null) {
            mc.getNetworkHandler().getConnection().disconnect(Text.literal("[TunnelMiner] Restock failure: " + reason));
        }
        if (isActive()) toggle();
    }

    private void disconnectOnSuccessfulCompletion() {
        String completionMessage;
        if (goalMode == GoalMode.FALLBACK) {
            completionMessage = String.format(
                Locale.ROOT,
                "Requested destination unreachable; completed at fallback (%d, %d) for requested (%d, %d).",
                activeGoalX,
                activeGoalZ,
                requestedDestX,
                requestedDestZ
            );
        } else {
            completionMessage = String.format(
                Locale.ROOT,
                "Destination reached at (%d, %d).",
                activeGoalX,
                activeGoalZ
            );
        }

        watchdog("success-disconnect", sanitizeLogField(completionMessage));
        watchdogCalc("success-disconnect", sanitizeLogField(completionMessage));

        if (mc.getNetworkHandler() != null && mc.getNetworkHandler().getConnection() != null) {
            MutableText text = Text.literal("[")
                .styled(style -> style.withColor(Formatting.WHITE))
                .append(Text.literal(title).styled(style -> style.withColor(Formatting.BLUE)))
                .append(Text.literal("] ").styled(style -> style.withColor(Formatting.WHITE)))
                .append(Text.literal(completionMessage).styled(style -> style.withColor(Formatting.GREEN)));
            mc.getNetworkHandler().getConnection().disconnect(text);
        }

        if (isActive()) toggle();
    }

    private String hazardUserMessage(HazardCondition hazard) {
        return switch (hazard) {
            case SUFFOCATING -> "player is suffocating";
            case DROWNING -> "player is drowning";
            case BURNING -> "player is burning / in lava";
            case SWIMMING -> "player is swimming / in swimming pose";
            default -> "hazard detected";
        };
    }

    private void hardFailHazard(HazardCondition hazard, String stage) {
        String userReason = hazardUserMessage(hazard);
        String details = String.format(
            Locale.ROOT,
            "hazard=%s,stage=%s,message=%s",
            hazard.id,
            sanitizeLogField(stage),
            sanitizeLogField(userReason)
        );
        watchdogCalc("hazard-hard-fail", details);
        if (mc.getNetworkHandler() != null && mc.getNetworkHandler().getConnection() != null) {
            mc.getNetworkHandler().getConnection().disconnect(Text.literal("[TunnelMiner] Hazard failure: " + userReason));
        }
        if (isActive()) toggle();
    }

    private void hardFailDestinationUnreachable(int px, int py, int pz, String reason) {
        String details = String.format(
            Locale.ROOT,
            "requested=(%d,%d),active=(%d,%d),player=(%d,%d,%d),radius=%d,reason=%s",
            requestedDestX,
            requestedDestZ,
            activeGoalX,
            activeGoalZ,
            px,
            py,
            pz,
            GOAL_FALLBACK_RADIUS,
            sanitizeLogField(reason)
        );
        watchdog("destination-unreachable", details);
        watchdogCalc("destination-unreachable", details);
        if (optDebugMessages()) warning("Destination unreachable under current safety rules: " + details);
        if (mc.options != null) mc.options.forwardKey.setPressed(false);
        if (isActive()) toggle();
    }

    private void setActiveGoal(int goalX, int goalZ, GoalMode mode, String reason) {
        GoalMode nextMode = mode == null ? GoalMode.EXACT : mode;
        String nextReason = reason == null ? "" : reason;
        boolean changed = activeGoalX != goalX || activeGoalZ != goalZ || goalMode != nextMode || !Objects.equals(goalReason, nextReason);

        activeGoalX = goalX;
        activeGoalZ = goalZ;
        goalMode = nextMode;
        goalReason = nextReason;

        // Planner continues to use destX/destZ as the active navigation goal.
        destX = activeGoalX;
        destZ = activeGoalZ;

        if (changed) {
            watchdog(
                "goal-update",
                String.format(
                    Locale.ROOT,
                    "mode=%s,requested=(%d,%d),active=(%d,%d),reason=%s",
                    goalMode.id,
                    requestedDestX,
                    requestedDestZ,
                    activeGoalX,
                    activeGoalZ,
                    sanitizeLogField(goalReason)
                )
            );
            if (optDebugMessages()) {
                if (goalMode == GoalMode.EXACT) info("Goal mode exact: (" + activeGoalX + ", " + activeGoalZ + ")");
                else if (goalMode == GoalMode.FALLBACK) warning("Goal mode fallback: (" + activeGoalX + ", " + activeGoalZ + ") requested=(" + requestedDestX + ", " + requestedDestZ + ")");
            }
        }
    }

    private boolean refreshActiveNavigationGoal(int px, int py, int pz) {
        boolean rejectAirGapDetours = useStealthAvoidAirGapDetours();

        // Do not run fallback goal search while the requested destination is still far away.
        // This prevents expensive 48-radius scans from running continuously on distant goals.
        int requestedDx = requestedDestX - px;
        int requestedDz = requestedDestZ - pz;
        int fallbackRadiusSq = GOAL_FALLBACK_RADIUS * GOAL_FALLBACK_RADIUS;
        int requestedDistSq = requestedDx * requestedDx + requestedDz * requestedDz;
        if (requestedDistSq > fallbackRadiusSq) {
            setActiveGoal(requestedDestX, requestedDestZ, GoalMode.EXACT, "fallback-deferred-distance>48");
            return true;
        }

        if (isGoalCellTraversable(requestedDestX, requestedDestZ, py, rejectAirGapDetours)) {
            setActiveGoal(requestedDestX, requestedDestZ, GoalMode.EXACT, "requested-traversable");
            return true;
        }

        GoalResolution fallback = resolveFallbackGoal(px, pz, py, rejectAirGapDetours);
        if (fallback.mode() == GoalMode.UNREACHABLE) {
            setActiveGoal(requestedDestX, requestedDestZ, GoalMode.UNREACHABLE, fallback.reason());
            hardFailDestinationUnreachable(px, py, pz, fallback.reason());
            return false;
        }

        setActiveGoal(fallback.x(), fallback.z(), GoalMode.FALLBACK, fallback.reason());
        return true;
    }

    private GoalResolution resolveFallbackGoal(int px, int pz, int py, boolean rejectAirGapDetours) {
        if (mc != null && mc.player != null
            && fallbackSearchCachedPx == px
            && fallbackSearchCachedPz == pz
            && fallbackSearchCachedPy == py
            && fallbackSearchCachedDestX == requestedDestX
            && fallbackSearchCachedDestZ == requestedDestZ
            && fallbackSearchCachedRejectAirGaps == rejectAirGapDetours
            && fallbackSearchCachedAge != Integer.MIN_VALUE
            && (mc.player.age - fallbackSearchCachedAge) <= hardPathCalcIntervalTicks()) {
            return new GoalResolution(
                fallbackSearchResultX,
                fallbackSearchResultZ,
                fallbackSearchResultMode,
                fallbackSearchResultReason
            );
        }

        final int[][] dirs = {
            { 1, 0 }, { 1, 1 }, { 0, 1 }, { -1, 1 },
            { -1, 0 }, { -1, -1 }, { 0, -1 }, { 1, -1 }
        };

        int radiusSq = GOAL_FALLBACK_RADIUS * GOAL_FALLBACK_RADIUS;
        long startKey = packXZ(px, pz);
        PriorityQueue<AStarNode> open = new PriorityQueue<>(Comparator.comparingDouble(a -> a.f));
        HashMap<Long, Double> gScore = new HashMap<>();
        HashSet<Long> closed = new HashSet<>();

        double startH = octileDistance(px, pz, requestedDestX, requestedDestZ);
        open.add(new AStarNode(px, pz, 0.0, startH));
        gScore.put(startKey, 0.0);

        long bestKey = Long.MIN_VALUE;
        double bestTargetDistance = Double.POSITIVE_INFINITY;
        double bestPathCost = Double.POSITIVE_INFINITY;
        double bestPlayerDistSq = Double.POSITIVE_INFINITY;
        int expanded = 0;

        while (!open.isEmpty() && expanded < GOAL_FALLBACK_MAX_NODES) {
            AStarNode current = open.poll();
            long currentKey = packXZ(current.x, current.z);
            if (!closed.add(currentKey)) continue;
            expanded++;

            int centerDx = current.x - px;
            int centerDz = current.z - pz;
            if (centerDx * centerDx + centerDz * centerDz > radiusSq) continue;

            if (isGoalCellTraversable(current.x, current.z, py, rejectAirGapDetours)) {
                double targetDistance = octileDistance(current.x, current.z, requestedDestX, requestedDestZ);
                double playerDistSq = (double) centerDx * centerDx + (double) centerDz * centerDz;
                if (targetDistance < bestTargetDistance - 1e-6
                    || (Math.abs(targetDistance - bestTargetDistance) <= 1e-6 && current.g < bestPathCost - 1e-6)
                    || (Math.abs(targetDistance - bestTargetDistance) <= 1e-6 && Math.abs(current.g - bestPathCost) <= 1e-6 && playerDistSq < bestPlayerDistSq - 1e-6)) {
                    bestTargetDistance = targetDistance;
                    bestPathCost = current.g;
                    bestPlayerDistSq = playerDistSq;
                    bestKey = currentKey;
                }
            }

            for (int[] dir : dirs) {
                int nx = current.x + dir[0];
                int nz = current.z + dir[1];
                int rx = nx - px;
                int rz = nz - pz;
                if (rx * rx + rz * rz > radiusSq) continue;
                if (!isStepTraversable(current.x, current.z, nx, nz, py, false, rejectAirGapDetours)) continue;

                long nKey = packXZ(nx, nz);
                if (closed.contains(nKey)) continue;

                double tentativeG = current.g + stepMovementCost(current.x, current.z, nx, nz);
                double prevG = gScore.getOrDefault(nKey, Double.POSITIVE_INFINITY);
                if (tentativeG >= prevG) continue;

                gScore.put(nKey, tentativeG);
                double nf = tentativeG + octileDistance(nx, nz, requestedDestX, requestedDestZ);
                open.add(new AStarNode(nx, nz, tentativeG, nf));
            }
        }

        GoalResolution result;
        if (bestKey == Long.MIN_VALUE) {
            result = new GoalResolution(
                requestedDestX,
                requestedDestZ,
                GoalMode.UNREACHABLE,
                String.format(
                    Locale.ROOT,
                    "no-reachable-goal-within-radius,expanded=%d,nodeLimit=%d,radius=%d",
                    expanded,
                    GOAL_FALLBACK_MAX_NODES,
                    GOAL_FALLBACK_RADIUS
                )
            );
        } else {
            result = new GoalResolution(
                unpackX(bestKey),
                unpackZ(bestKey),
                GoalMode.FALLBACK,
                String.format(
                    Locale.ROOT,
                    "fallback-best-reachable,expanded=%d,nodeLimit=%d,radius=%d,targetDist=%.3f,pathCost=%.3f",
                    expanded,
                    GOAL_FALLBACK_MAX_NODES,
                    GOAL_FALLBACK_RADIUS,
                    bestTargetDistance,
                    bestPathCost
                )
            );
        }

        fallbackSearchCachedPx = px;
        fallbackSearchCachedPz = pz;
        fallbackSearchCachedPy = py;
        fallbackSearchCachedDestX = requestedDestX;
        fallbackSearchCachedDestZ = requestedDestZ;
        fallbackSearchCachedRejectAirGaps = rejectAirGapDetours;
        fallbackSearchCachedAge = (mc != null && mc.player != null) ? mc.player.age : Integer.MIN_VALUE;
        fallbackSearchResultX = result.x();
        fallbackSearchResultZ = result.z();
        fallbackSearchResultMode = result.mode();
        fallbackSearchResultReason = result.reason();

        watchdogCalc(
            "goal-resolve",
            String.format(
                Locale.ROOT,
                "requested=(%d,%d),player=(%d,%d),mode=%s,active=(%d,%d),reason=%s",
                requestedDestX,
                requestedDestZ,
                px,
                pz,
                result.mode().id,
                result.x(),
                result.z(),
                sanitizeLogField(result.reason())
            )
        );

        return result;
    }

    private boolean isGoalCellTraversable(int x, int z, int py, boolean rejectAirGapDetours) {
        return !isObstacleCell(x, z, py, rejectAirGapDetours);
    }

    public boolean goTo(int x, int z, int stealthMode, boolean renderingEnabled) {
        if (mc == null || !mc.isOnThread()) {
            warning("TunnelMiner API goTo must be called on the Minecraft client thread.");
            return false;
        }

        if (!isSupportedApiStealthMode(stealthMode)) {
            warning("Unsupported TunnelMiner API stealth mode: " + stealthMode + " (expected 0=off or 1=on).");
            return false;
        }

        if (mc == null || mc.player == null || mc.world == null) {
            warning("Cannot start TunnelMiner API goTo without an active world/player context.");
            return false;
        }

        if (isActive()) {
            pendingApiGoToRequest = null;
            installApiEffectiveOptions(x, z, stealthMode, renderingEnabled);
            startFreshRunFromCurrentPosition("api-retarget");
        } else {
            pendingApiGoToRequest = new ApiGoToRequest(x, z, stealthMode, renderingEnabled);
            toggle();
        }

        return true;
    }

    public boolean goTo(int x, int z, boolean stealthEnabled, boolean renderingEnabled) {
        return goTo(x, z, stealthEnabled ? API_STEALTH_ON : API_STEALTH_OFF, renderingEnabled);
    }

    public boolean isApiModeActive() {
        return apiModeActive;
    }

    public SettingsSource getSettingsSource() {
        return settingsSource;
    }

    private void startFreshRunFromCurrentPosition(String reason) {
        if (mc.player == null) return;
        flushWatchdogBuffer();
        watchdogWriteBuffer.setLength(0);
        watchdogWriteBufferLines = 0;

        int px = MathHelper.floor(mc.player.getX());
        int py = MathHelper.floor(mc.player.getY());
        int pz = MathHelper.floor(mc.player.getZ());
        requestedDestX = optTargetX();
        requestedDestZ = optTargetZ();

        destX = requestedDestX;
        destZ = requestedDestZ;
        activeGoalX = destX;
        activeGoalZ = destZ;
        goalMode = GoalMode.EXACT;
        goalReason = reason;
        fallbackSearchCachedPx = Integer.MIN_VALUE;
        fallbackSearchCachedPz = Integer.MIN_VALUE;
        fallbackSearchCachedPy = Integer.MIN_VALUE;
        fallbackSearchCachedDestX = Integer.MIN_VALUE;
        fallbackSearchCachedDestZ = Integer.MIN_VALUE;
        fallbackSearchCachedAge = Integer.MIN_VALUE;
        fallbackSearchResultMode = GoalMode.UNREACHABLE;
        fallbackSearchResultReason = "";
        tunnelY = py;
        onXAxis = true;
        fillLog.clear();
        stealthCache.clear();
        resumeStateAvailable = false;
        resumeFromCompletedRun = false;
        clearResumeCacheFile();
        renderBreakPositions.clear();
        activeProbePositions.clear();
        activeMineTargets.clear();
        cachedProbePath.clear();
        cachedProbeStartX = Integer.MIN_VALUE;
        cachedProbeStartZ = Integer.MIN_VALUE;
        cachedProbeY = Integer.MIN_VALUE;
        cachedProbeDestX = Integer.MIN_VALUE;
        cachedProbeDestZ = Integer.MIN_VALUE;
        cachedProbeRadius = Integer.MIN_VALUE;
        cachedProbeLastReplanAge = Integer.MIN_VALUE;
        cachedProbeLastEmptyReplanAge = Integer.MIN_VALUE;
        cachedProbeOnXAxis = onXAxis;
        cachedProbeRejectAirGaps = useStealthAvoidAirGapDetours();
        cachedProbeEmergencyNoScaffold = false;
        cachedProbePathMode = hardPathMode();
        noProbePauseTicks = 0;
        emergencyAirFallbackTicksLeft = 0;
        emergencyAirFallbackBlocksLeft = 0;
        probeAStarFailStreak = 0;
        probeWallFollowActive = false;
        wallFollowMLineStartX = Integer.MIN_VALUE;
        wallFollowMLineStartZ = Integer.MIN_VALUE;
        wallFollowHitDistance = Double.POSITIVE_INFINITY;
        wallFollowHeading = 0;
        wallFollowRerouteBlocks = 0;
        hazardRecoveryCondition = HazardCondition.NONE;
        hazardRecoveryTicks = 0;
        placeTimer = invTimer = waitTicks = 0;
        restockPlaceRetries = 0;
        stealthBreakTimer = 0;
        detourVisitCounts.clear();
        proactiveAStarCacheFromX = Integer.MIN_VALUE;
        proactiveAStarCacheFromZ = Integer.MIN_VALUE;
        proactiveAStarCachePy = Integer.MIN_VALUE;
        proactiveAStarCacheDestX = Integer.MIN_VALUE;
        proactiveAStarCacheDestZ = Integer.MIN_VALUE;
        proactiveAStarCacheAge = Integer.MIN_VALUE;
        proactiveAStarCacheStepX = 0;
        proactiveAStarCacheStepZ = 0;
        proactiveAStarCacheSummary = "";
        committedMoveStep = null;
        prevVisitedX = Integer.MIN_VALUE;
        prevVisitedZ = Integer.MIN_VALUE;
        lastVisitedX = Integer.MIN_VALUE;
        lastVisitedZ = Integer.MIN_VALUE;
        pickSlot = -1;
        containerPos = null;
        restockCleanupPos = null;
        resetRestockStageState();
        restockPlaceRetries = 0;
        startMs = System.currentTimeMillis();
        blocksMined = 0;
        watchdogSequence = 0;
        resetWatchdogTelemetry();
        reloadAvoidanceBlockSets();
        freeLookModule = Modules.get().get(FreeLook.class);
        if (optAutoFreeLook()) {
            if (freeLookModule != null && !freeLookModule.isActive()) {
                freeLookModule.toggle();
                managedFreeLook = freeLookModule.isActive();
            }
        } else if (managedFreeLook && freeLookModule != null && freeLookModule.isActive()) {
            freeLookModule.toggle();
            managedFreeLook = false;
        }
        clearStealthMining(false);
        totalBlocks = blocksLeftFrom(px, pz);
        phase = Phase.INIT;

        if (optDebugMessages()) {
            info("Retarget tunnel to X=" + requestedDestX + " Z=" + requestedDestZ + " (" + totalBlocks + " blocks)");
        }
        watchdog(
            "external-goto-reset",
            String.format(
                Locale.ROOT,
                "reason=%s,targetX=%d,targetZ=%d,targetY=%d,totalBlocks=%d",
                reason,
                requestedDestX,
                requestedDestZ,
                tunnelY,
                totalBlocks
            )
        );
    }

    public int getBlocksLeft() {
        if (mc.player == null || !isActive()) return 0;
        return blocksLeftFrom(MathHelper.floor(mc.player.getX()), MathHelper.floor(mc.player.getZ()));
    }

    public double getEtaSeconds() {
        if (!isActive()) return -1;
        int blocksLeft = getBlocksLeft();
        int progressed = Math.max(0, totalBlocks - blocksLeft);
        if (progressed < 5) return -1;
        long ms = System.currentTimeMillis() - startMs;
        if (ms <= 0) return -1;
        double rate = (double) progressed / ms;
        return rate <= 0 ? -1 : blocksLeft / (rate * 1000.0);
    }

    public int getDestX() {
        return requestedDestX;
    }

    public int getDestZ() {
        return requestedDestZ;
    }

    public int getActiveGoalX() {
        return activeGoalX;
    }

    public int getActiveGoalZ() {
        return activeGoalZ;
    }

    public int getTotalBlocks() {
        return totalBlocks;
    }

    public int getApproxRequiredPicks() {
        int remainingDistance = Math.max(0, getBlocksLeft());
        long approxBlocksToMine = (long) remainingDistance * Math.max(1, optTunnelHeight());
        return ceilDivPositive(approxBlocksToMine, 8128);
    }

    public int getApproxRequiredPickShulkers() {
        int picks = getApproxRequiredPicks();
        return picks <= 0 ? 0 : ((picks + 26) / 27);
    }

    private int ceilDivPositive(long numerator, int denominator) {
        if (numerator <= 0 || denominator <= 0) return 0;
        return (int) ((numerator + denominator - 1L) / denominator);
    }

    private boolean isGoalCompletionCellLoaded() {
        if (mc == null || mc.world == null) return false;
        BlockPos completionPos = new BlockPos(destX, tunnelY, destZ);
        return mc.world.getBlockState(completionPos).getBlock() != Blocks.VOID_AIR;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private int blocksLeftFrom(int px, int pz) {
        int dx = Math.abs(destX - px);
        int dz = Math.abs(destZ - pz);
        return dx + dz;
    }

    private int[] nextStep(int px, int pz) {
        int py = tunnelY;
        return computeStepWithDetour(px, pz, py, onXAxis, true);
    }

    private int[] computeStep(int x, int z, boolean axisMode) {
        int dx = Integer.compare(destX, x);
        int dz = Integer.compare(destZ, z);
        int[] step;

        if (hardPathMode() == PathMode.AxisFirst) {
            boolean useXAxis = axisMode && x != destX;
            step = useXAxis ? new int[] { dx, 0 } : new int[] { 0, dz };
            watchdogCalc("compute-step", String.format(Locale.ROOT, "mode=AxisFirst,x=%d,z=%d,destX=%d,destZ=%d,axisMode=%s,dx=%d,dz=%d,out=(%d,%d)", x, z, destX, destZ, axisMode, dx, dz, step[0], step[1]));
            return step;
        }

        // Zigzag diagonal: while both axes differ, alternate X and Z so each step clears one forward column.
        if (dx != 0 && dz != 0) {
            boolean stepX = ((x + z) & 1) == 0;
            step = stepX ? new int[] { dx, 0 } : new int[] { 0, dz };
            watchdogCalc("compute-step", String.format(Locale.ROOT, "mode=DiagonalThenAxis,x=%d,z=%d,destX=%d,destZ=%d,dx=%d,dz=%d,parity=%d,out=(%d,%d)", x, z, destX, destZ, dx, dz, ((x + z) & 1), step[0], step[1]));
            return step;
        }

        step = new int[] { dx, dz };
        watchdogCalc("compute-step", String.format(Locale.ROOT, "mode=DiagonalThenAxis-tail,x=%d,z=%d,destX=%d,destZ=%d,dx=%d,dz=%d,out=(%d,%d)", x, z, destX, destZ, dx, dz, step[0], step[1]));
        return step;
    }

    private int[] computeStepWithDetour(int x, int z, int py, boolean axisMode, boolean useAStar) {
        return computeStepWithDetour(x, z, py, axisMode, useAStar, false);
    }

    private int[] computeStepWithDetour(int x, int z, int py, boolean axisMode, boolean useAStar, boolean rejectAirGapDetours) {
        int[] preferred = computeStep(x, z, axisMode);
        watchdogDetourSummary = "";
        watchdogTraverseFail = "";
        watchdogAStarSummary = "";
        boolean astarAttempted = false;
        if (preferred[0] == 0 && preferred[1] == 0) {
            watchdogDetourSummary = "preferred=(0,0),source=arrived,rejectAirGapDetours=" + rejectAirGapDetours;
            watchdogCalc("compute-step-detour", String.format(Locale.ROOT, "from=(%d,%d),py=%d,axisMode=%s,useAStar=%s,rejectAirGapDetours=%s,preferred=(0,0),result=(0,0),source=arrived", x, z, py, axisMode, useAStar, rejectAirGapDetours));
            return preferred;
        }

        // In avoid-air-gaps mode, use A* proactively so hazards visible in the probe
        // horizon can influence the very next step instead of waiting for a hard block.
        if (useAStar && rejectAirGapDetours) {
            int[] cachedStep = getProactiveAStarCachedStep(x, z, py, axisMode);
            if (cachedStep != null) {
                int[] normalizedCached = normalizeSingleWidthStep(x, z, py, preferred, cachedStep, rejectAirGapDetours);
                watchdogAStarSummary = proactiveAStarCacheSummary;
                watchdogDetourSummary = String.format(Locale.ROOT, "preferred=(%d,%d),source=astar-proactive-cache,result=(%d,%d),normalized=(%d,%d)", preferred[0], preferred[1], cachedStep[0], cachedStep[1], normalizedCached[0], normalizedCached[1]);
                watchdogCalc("compute-step-detour", String.format(Locale.ROOT, "from=(%d,%d),py=%d,axisMode=%s,useAStar=%s,rejectAirGapDetours=%s,preferred=(%d,%d),result=(%d,%d),normalized=(%d,%d),source=astar-proactive-cache,astar=%s", x, z, py, axisMode, useAStar, rejectAirGapDetours, preferred[0], preferred[1], cachedStep[0], cachedStep[1], normalizedCached[0], normalizedCached[1], watchdogAStarSummary));
                return normalizedCached;
            }
            astarAttempted = true;
            int[] astar = findAStarDetourStep(x, z, py, true);
            if (!watchdogAStarPending) {
                storeProactiveAStarCache(x, z, py, axisMode, astar[0], astar[1], watchdogAStarSummary);
                int[] normalizedAStar = normalizeSingleWidthStep(x, z, py, preferred, astar, rejectAirGapDetours);
                if (normalizedAStar[0] != 0 || normalizedAStar[1] != 0) {
                    watchdogDetourSummary = String.format(Locale.ROOT, "preferred=(%d,%d),source=astar-proactive,result=(%d,%d),normalized=(%d,%d)", preferred[0], preferred[1], astar[0], astar[1], normalizedAStar[0], normalizedAStar[1]);
                    watchdogCalc("compute-step-detour", String.format(Locale.ROOT, "from=(%d,%d),py=%d,axisMode=%s,useAStar=%s,rejectAirGapDetours=%s,preferred=(%d,%d),result=(%d,%d),normalized=(%d,%d),source=astar-proactive,astar=%s", x, z, py, axisMode, useAStar, rejectAirGapDetours, preferred[0], preferred[1], astar[0], astar[1], normalizedAStar[0], normalizedAStar[1], watchdogAStarSummary));
                    return normalizedAStar;
                }
            }
        }

        int[] normalizedPreferred = normalizeSingleWidthStep(x, z, py, preferred, preferred, rejectAirGapDetours);
        if (isStepTraversable(x, z, x + normalizedPreferred[0], z + normalizedPreferred[1], py, true, rejectAirGapDetours)) {
            watchdogDetourSummary = String.format(Locale.ROOT, "preferred=(%d,%d),source=preferred", preferred[0], preferred[1]);
            watchdogCalc("compute-step-detour", String.format(Locale.ROOT, "from=(%d,%d),py=%d,axisMode=%s,useAStar=%s,rejectAirGapDetours=%s,preferred=(%d,%d),result=(%d,%d),source=preferred", x, z, py, axisMode, useAStar, rejectAirGapDetours, preferred[0], preferred[1], normalizedPreferred[0], normalizedPreferred[1]));
            return normalizedPreferred;
        }
        watchdogCalc("compute-step-detour", String.format(Locale.ROOT, "preferred-blocked,from=(%d,%d),py=%d,axisMode=%s,useAStar=%s,rejectAirGapDetours=%s,preferred=(%d,%d),reason=%s", x, z, py, axisMode, useAStar, rejectAirGapDetours, preferred[0], preferred[1], watchdogTraverseFail));
        if (useAStar && !astarAttempted) {
            int[] astar = findAStarDetourStep(x, z, py, rejectAirGapDetours);
            if (!watchdogAStarPending) {
                int[] normalizedAStar = normalizeSingleWidthStep(x, z, py, preferred, astar, rejectAirGapDetours);
                if (normalizedAStar[0] != 0 || normalizedAStar[1] != 0) {
                    watchdogDetourSummary = String.format(Locale.ROOT, "preferred=(%d,%d),source=astar,result=(%d,%d),normalized=(%d,%d)", preferred[0], preferred[1], astar[0], astar[1], normalizedAStar[0], normalizedAStar[1]);
                    watchdogCalc("compute-step-detour", String.format(Locale.ROOT, "from=(%d,%d),py=%d,axisMode=%s,useAStar=%s,rejectAirGapDetours=%s,preferred=(%d,%d),result=(%d,%d),normalized=(%d,%d),source=astar,astar=%s", x, z, py, axisMode, useAStar, rejectAirGapDetours, preferred[0], preferred[1], astar[0], astar[1], normalizedAStar[0], normalizedAStar[1], watchdogAStarSummary));
                    return normalizedAStar;
                }
            }
        }
        int[] greedy = pickDetourStep(x, z, py, preferred, rejectAirGapDetours);
        int[] normalizedGreedy = normalizeSingleWidthStep(x, z, py, preferred, greedy, rejectAirGapDetours);
        watchdogDetourSummary = String.format(Locale.ROOT, "preferred=(%d,%d),source=greedy,result=(%d,%d),normalized=(%d,%d)", preferred[0], preferred[1], greedy[0], greedy[1], normalizedGreedy[0], normalizedGreedy[1]);
        watchdogCalc("compute-step-detour", String.format(Locale.ROOT, "from=(%d,%d),py=%d,axisMode=%s,useAStar=%s,rejectAirGapDetours=%s,preferred=(%d,%d),result=(%d,%d),normalized=(%d,%d),source=greedy", x, z, py, axisMode, useAStar, rejectAirGapDetours, preferred[0], preferred[1], greedy[0], greedy[1], normalizedGreedy[0], normalizedGreedy[1]));
        return normalizedGreedy;
    }

    private int[] normalizeSingleWidthStep(int fromX, int fromZ, int py, int[] preferred, int[] candidate, boolean rejectAirGapDetours) {
        if (candidate == null || candidate.length < 2) return new int[] { 0, 0 };
        int dx = Integer.compare(candidate[0], 0);
        int dz = Integer.compare(candidate[1], 0);
        if (dx == 0 && dz == 0) return new int[] { 0, 0 };
        if (dx == 0 || dz == 0) return new int[] { dx, dz };

        int prefX = preferred == null || preferred.length < 2 ? 0 : Integer.compare(preferred[0], 0);
        int prefZ = preferred == null || preferred.length < 2 ? 0 : Integer.compare(preferred[1], 0);

        int[][] options = {
            { dx, 0 },
            { 0, dz }
        };

        int bestIdx = -1;
        double bestScore = Double.POSITIVE_INFINITY;
        for (int i = 0; i < options.length; i++) {
            int ox = options[i][0];
            int oz = options[i][1];
            int nx = fromX + ox;
            int nz = fromZ + oz;
            if (!isStepTraversable(fromX, fromZ, nx, nz, py, false, rejectAirGapDetours)) continue;

            int prefPenalty = 0;
            if (prefX != 0 || prefZ != 0) prefPenalty = Math.abs(ox - prefX) + Math.abs(oz - prefZ);
            double score = octileDistance(nx, nz, destX, destZ) + prefPenalty * 0.25;
            if (score < bestScore) {
                bestScore = score;
                bestIdx = i;
            }
        }

        if (bestIdx == -1) return new int[] { 0, 0 };
        return options[bestIdx];
    }

    private int[] pickDetourStep(int x, int z, int py, int[] preferred, boolean rejectAirGapDetours) {
        final int[][] dirs = {
            { 1, 0 }, { 0, 1 }, { -1, 0 }, { 0, -1 }
        };

        double bestScore = Double.POSITIVE_INFINITY;
        int bestDx = 0;
        int bestDz = 0;
        int traversable = 0;

        for (int[] dir : dirs) {
            int dx = dir[0];
            int dz = dir[1];
            int nx = x + dx;
            int nz = z + dz;

            boolean traversableStep = isStepTraversable(x, z, nx, nz, py, false, rejectAirGapDetours);
            if (!traversableStep) {
                watchdogCalc("greedy-detour-candidate", String.format(Locale.ROOT, "from=(%d,%d),to=(%d,%d),dir=(%d,%d),traversable=false", x, z, nx, nz, dx, dz));
                continue;
            }
            traversable++;

            int h = Math.max(Math.abs(destX - nx), Math.abs(destZ - nz));
            int prefDelta = Math.abs(dx - preferred[0]) + Math.abs(dz - preferred[1]);
            int visits = detourVisitCounts.getOrDefault(packXZ(nx, nz), 0);
            boolean immediateBacktrack = x == lastVisitedX && z == lastVisitedZ && nx == prevVisitedX && nz == prevVisitedZ;
            double backtrackPenalty = immediateBacktrack ? GREEDY_BACKTRACK_PENALTY : 0.0;
            double score = h * 10.0 + prefDelta * 2.0 + visits * 6.0 + backtrackPenalty;
            watchdogCalc(
                "greedy-detour-candidate",
                String.format(
                    Locale.ROOT,
                    "from=(%d,%d),to=(%d,%d),dir=(%d,%d),traversable=true,h=%d,prefDelta=%d,visits=%d,backtrack=%s,backtrackPenalty=%.3f,score=%.3f",
                    x,
                    z,
                    nx,
                    nz,
                    dx,
                    dz,
                    h,
                    prefDelta,
                    visits,
                    immediateBacktrack,
                    backtrackPenalty,
                    score
                )
            );

            if (score < bestScore) {
                bestScore = score;
                bestDx = dx;
                bestDz = dz;
            }
        }

        watchdogCalc("greedy-detour-result", String.format(Locale.ROOT, "from=(%d,%d),preferred=(%d,%d),traversable=%d,result=(%d,%d),bestScore=%s", x, z, preferred[0], preferred[1], traversable, bestDx, bestDz, Double.isInfinite(bestScore) ? "INF" : String.format(Locale.ROOT, "%.3f", bestScore)));
        return new int[] { bestDx, bestDz };
    }

    private static class AStarNode {
        private final int x;
        private final int z;
        private final double g;
        private final double f;

        private AStarNode(int x, int z, double g, double f) {
            this.x = x;
            this.z = z;
            this.g = g;
            this.f = f;
        }
    }

    private int[] findAStarDetourStep(int startX, int startZ, int py, boolean rejectAirGapDetours) {
        watchdogAStarPending = false;
        if (!ASTAR_ASYNC) {
            return findAStarDetourStepSync(startX, startZ, py, rejectAirGapDetours);
        }

        int astarRadius = getDetourAStarRadius(rejectAirGapDetours);
        int astarNodeLimit = getDetourAStarMaxNodes(astarRadius, rejectAirGapDetours);
        AStarRequest request = new AStarRequest(startX, startZ, py, destX, destZ, rejectAirGapDetours, astarRadius, astarNodeLimit);

        if (pendingAStar != null && pendingAStarRequest != null && pendingAStarRequest.equals(request)) {
            if (pendingAStar.isDone()) {
                try {
                    AStarResult result = pendingAStar.get();
                    pendingAStar = null;
                    pendingAStarRequest = null;
                    watchdogAStarSummary = result.summary();
                    return new int[] { result.stepX(), result.stepZ() };
                } catch (Exception e) {
                    pendingAStar = null;
                    pendingAStarRequest = null;
                    watchdogAStarSummary = "async-error:" + e.getClass().getSimpleName();
                    return new int[] { 0, 0 };
                }
            }
            watchdogAStarSummary = "async-pending";
            watchdogAStarPending = true;
            return new int[] { 0, 0 };
        }

        if (pendingAStar != null && !pendingAStar.isDone()) {
            pendingAStar.cancel(true);
        }

        AStarSnapshot snapshot = buildAStarSnapshot(startX, startZ, py, astarRadius, rejectAirGapDetours);
        HashMap<Long, Integer> visitSnapshot = buildDetourVisitSnapshot(startX, startZ, astarRadius);
        double safetyPenalty = hardDetourSafetyBufferCost();
        pendingAStarRequest = request;
        pendingAStar = ASTAR_EXECUTOR.submit(() -> computeAStarWithSnapshot(request, snapshot, visitSnapshot, safetyPenalty));
        watchdogAStarSummary = "async-start";
        watchdogAStarPending = true;
        return new int[] { 0, 0 };
    }

    private int[] findAStarDetourStepSync(int startX, int startZ, int py, boolean rejectAirGapDetours) {
        final int[][] dirs = {
            { 1, 0 }, { 1, 1 }, { 0, 1 }, { -1, 1 },
            { -1, 0 }, { -1, -1 }, { 0, -1 }, { 1, -1 }
        };
        int astarRadius = getDetourAStarRadius(rejectAirGapDetours);
        int astarRadiusSq = astarRadius * astarRadius;
        int astarNodeLimit = getDetourAStarMaxNodes(astarRadius, rejectAirGapDetours);

        long startKey = packXZ(startX, startZ);
        PriorityQueue<AStarNode> open = new PriorityQueue<>(Comparator.comparingDouble(a -> a.f));
        HashMap<Long, Double> gScore = new HashMap<>();
        HashMap<Long, Long> cameFrom = new HashMap<>();
        HashSet<Long> closed = new HashSet<>();

        double startH = octileDistance(startX, startZ, destX, destZ);
        open.add(new AStarNode(startX, startZ, 0.0, startH));
        gScore.put(startKey, 0.0);

        long bestKey = startKey;
        double bestH = startH;
        int expanded = 0;
        boolean reachedGoal = false;

        while (!open.isEmpty() && expanded < astarNodeLimit) {
            AStarNode current = open.poll();
            long currentKey = packXZ(current.x, current.z);
            if (!closed.add(currentKey)) continue;
            expanded++;
            watchdogCalc("astar-expand", String.format(Locale.ROOT, "start=(%d,%d),current=(%d,%d),g=%.3f,f=%.3f,open=%d,closed=%d,expanded=%d", startX, startZ, current.x, current.z, current.g, current.f, open.size(), closed.size(), expanded));

            double h = octileDistance(current.x, current.z, destX, destZ);
            if (h < bestH) {
                bestH = h;
                bestKey = currentKey;
            }

            if (current.x == destX && current.z == destZ) {
                bestKey = currentKey;
                reachedGoal = true;
                break;
            }

            for (int[] dir : dirs) {
                int nx = current.x + dir[0];
                int nz = current.z + dir[1];

                int rx = nx - startX;
                int rz = nz - startZ;
                if (rx * rx + rz * rz > astarRadiusSq) {
                    watchdogCalc("astar-neighbor", String.format(Locale.ROOT, "from=(%d,%d),to=(%d,%d),reject=radius", current.x, current.z, nx, nz));
                    continue;
                }
                if (!isStepTraversable(current.x, current.z, nx, nz, py, false, rejectAirGapDetours)) {
                    watchdogCalc("astar-neighbor", String.format(Locale.ROOT, "from=(%d,%d),to=(%d,%d),reject=blocked", current.x, current.z, nx, nz));
                    continue;
                }

                long nKey = packXZ(nx, nz);
                if (closed.contains(nKey)) {
                    watchdogCalc("astar-neighbor", String.format(Locale.ROOT, "from=(%d,%d),to=(%d,%d),reject=closed", current.x, current.z, nx, nz));
                    continue;
                }

                double tentativeG = current.g + stepMovementCost(current.x, current.z, nx, nz);
                double prevG = gScore.getOrDefault(nKey, Double.POSITIVE_INFINITY);
                if (tentativeG >= prevG) {
                    watchdogCalc("astar-neighbor", String.format(Locale.ROOT, "from=(%d,%d),to=(%d,%d),reject=not-better,tentativeG=%.3f,prevG=%.3f", current.x, current.z, nx, nz, tentativeG, prevG));
                    continue;
                }

                gScore.put(nKey, tentativeG);
                cameFrom.put(nKey, currentKey);

                double visitPenalty = detourVisitCounts.getOrDefault(nKey, 0) * 2.0;
                double safetyPenalty = computeDetourSafetyPenalty(nx, nz, py, rejectAirGapDetours);
                double nf = tentativeG + octileDistance(nx, nz, destX, destZ) + visitPenalty + safetyPenalty;
                open.add(new AStarNode(nx, nz, tentativeG, nf));
                watchdogCalc("astar-neighbor", String.format(Locale.ROOT, "from=(%d,%d),to=(%d,%d),accept=true,g=%.3f,visitPenalty=%.3f,safetyPenalty=%.3f,f=%.3f", current.x, current.z, nx, nz, tentativeG, visitPenalty, safetyPenalty, nf));
            }
        }

        if (bestKey == startKey) {
            watchdogAStarSummary = String.format(Locale.ROOT, "radius=%d,nodeLimit=%d,expanded=%d,reachedGoal=%s,bestH=%.3f,best=(%d,%d),result=(0,0)", astarRadius, astarNodeLimit, expanded, reachedGoal, bestH, unpackX(bestKey), unpackZ(bestKey));
            watchdogCalc("astar-result", String.format(Locale.ROOT, "start=(%d,%d),%s", startX, startZ, watchdogAStarSummary));
            return new int[] { 0, 0 };
        }

        long stepKey = bestKey;
        while (cameFrom.containsKey(stepKey) && cameFrom.get(stepKey) != startKey) {
            stepKey = cameFrom.get(stepKey);
        }

        if (!cameFrom.containsKey(stepKey) && stepKey != bestKey) {
            watchdogAStarSummary = String.format(Locale.ROOT, "radius=%d,nodeLimit=%d,expanded=%d,reachedGoal=%s,bestH=%.3f,best=(%d,%d),result=(0,0),reason=broken-chain", astarRadius, astarNodeLimit, expanded, reachedGoal, bestH, unpackX(bestKey), unpackZ(bestKey));
            watchdogCalc("astar-result", String.format(Locale.ROOT, "start=(%d,%d),%s", startX, startZ, watchdogAStarSummary));
            return new int[] { 0, 0 };
        }

        int sx = unpackX(stepKey);
        int sz = unpackZ(stepKey);
        int outDx = Integer.compare(sx, startX);
        int outDz = Integer.compare(sz, startZ);
        watchdogAStarSummary = String.format(Locale.ROOT, "radius=%d,nodeLimit=%d,expanded=%d,reachedGoal=%s,bestH=%.3f,best=(%d,%d),next=(%d,%d),result=(%d,%d)", astarRadius, astarNodeLimit, expanded, reachedGoal, bestH, unpackX(bestKey), unpackZ(bestKey), sx, sz, outDx, outDz);
        watchdogCalc("astar-result", String.format(Locale.ROOT, "start=(%d,%d),%s", startX, startZ, watchdogAStarSummary));
        return new int[] { outDx, outDz };
    }

    private AStarSnapshot buildAStarSnapshot(int startX, int startZ, int py, int radius, boolean rejectAirGapDetours) {
        int minX = startX - radius;
        int minZ = startZ - radius;
        int width = radius * 2 + 1;
        int depth = radius * 2 + 1;
        int size = width * depth;
        boolean[] columnTraversable = new boolean[size];
        boolean[] obstacleCell = new boolean[size];
        boolean[] floorUnsafe = new boolean[size];
        int height = optTunnelHeight();

        for (int z = minZ; z <= minZ + depth - 1; z++) {
            for (int x = minX; x <= minX + width - 1; x++) {
                int idx = (z - minZ) * width + (x - minX);
                boolean traversable = true;
                boolean obstacle = false;

                for (int h = 0; h < height; h++) {
                    BlockPos pos = new BlockPos(x, py + h, z);
                    BlockState state = mc.world.getBlockState(pos);
                    boolean isLava = isLavaState(state);
                    boolean isAir = state.isAir() || isLava;

                    if (!isAir) obstacle = true;

                    if (state.getBlock() == Blocks.BEDROCK || isAvoidanceBlock(state) || touchesNoTouchChainBlock(pos) || touchesLavaDetourRisk(pos)) {
                        traversable = false;
                        obstacle = true;
                        if (state.getBlock() == Blocks.BEDROCK || isAvoidanceBlock(state)) break;
                    }

                    if (!isAir && !BlockUtils.canBreak(pos, state)) {
                        traversable = false;
                        obstacle = true;
                    }
                }

                BlockPos floor = new BlockPos(x, py - 1, z);
                boolean unsafe = touchesAirGapDetourRisk(floor, rejectAirGapDetours) || touchesLavaDetourRisk(floor);

                columnTraversable[idx] = traversable;
                obstacleCell[idx] = obstacle;
                floorUnsafe[idx] = unsafe;
            }
        }

        return new AStarSnapshot(minX, minZ, width, depth, columnTraversable, obstacleCell, floorUnsafe);
    }

    private HashMap<Long, Integer> buildDetourVisitSnapshot(int startX, int startZ, int radius) {
        HashMap<Long, Integer> snapshot = new HashMap<>();
        if (detourVisitCounts.isEmpty()) return snapshot;
        int r2 = radius * radius;
        for (Map.Entry<Long, Integer> entry : detourVisitCounts.entrySet()) {
            long key = entry.getKey();
            int x = unpackX(key);
            int z = unpackZ(key);
            int dx = x - startX;
            int dz = z - startZ;
            if (dx * dx + dz * dz <= r2) snapshot.put(key, entry.getValue());
        }
        return snapshot;
    }

    private AStarResult computeAStarWithSnapshot(AStarRequest request, AStarSnapshot snapshot, HashMap<Long, Integer> visitCounts, double safetyPenaltyPerNeighbor) {
        final int[][] dirs = {
            { 1, 0 }, { 1, 1 }, { 0, 1 }, { -1, 1 },
            { -1, 0 }, { -1, -1 }, { 0, -1 }, { 1, -1 }
        };

        int startX = request.startX();
        int startZ = request.startZ();
        int destX = request.destX();
        int destZ = request.destZ();
        int radius = request.radius();
        int nodeLimit = request.nodeLimit();
        boolean rejectAirGapDetours = request.rejectAirGapDetours();
        int radiusSq = radius * radius;

        long startKey = packXZ(startX, startZ);
        PriorityQueue<AStarNode> open = new PriorityQueue<>(Comparator.comparingDouble(a -> a.f));
        HashMap<Long, Double> gScore = new HashMap<>();
        HashMap<Long, Long> cameFrom = new HashMap<>();
        HashSet<Long> closed = new HashSet<>();

        double startH = octileDistance(startX, startZ, destX, destZ);
        open.add(new AStarNode(startX, startZ, 0.0, startH));
        gScore.put(startKey, 0.0);

        long bestKey = startKey;
        double bestH = startH;
        int expanded = 0;
        boolean reachedGoal = false;

        while (!open.isEmpty() && expanded < nodeLimit) {
            AStarNode current = open.poll();
            long currentKey = packXZ(current.x, current.z);
            if (!closed.add(currentKey)) continue;
            expanded++;

            double h = octileDistance(current.x, current.z, destX, destZ);
            if (h < bestH) {
                bestH = h;
                bestKey = currentKey;
            }

            if (current.x == destX && current.z == destZ) {
                bestKey = currentKey;
                reachedGoal = true;
                break;
            }

            for (int[] dir : dirs) {
                int nx = current.x + dir[0];
                int nz = current.z + dir[1];

                int rx = nx - startX;
                int rz = nz - startZ;
                if (rx * rx + rz * rz > radiusSq) continue;
                if (!isStepTraversableSnapshot(snapshot, current.x, current.z, nx, nz, rejectAirGapDetours)) continue;

                long nKey = packXZ(nx, nz);
                if (closed.contains(nKey)) continue;

                double tentativeG = current.g + stepMovementCost(current.x, current.z, nx, nz);
                double prevG = gScore.getOrDefault(nKey, Double.POSITIVE_INFINITY);
                if (tentativeG >= prevG) continue;

                gScore.put(nKey, tentativeG);
                cameFrom.put(nKey, currentKey);

                double visitPenalty = visitCounts.getOrDefault(nKey, 0) * 2.0;
                double safetyPenalty = computeDetourSafetyPenaltySnapshot(snapshot, nx, nz, safetyPenaltyPerNeighbor);
                double nf = tentativeG + octileDistance(nx, nz, destX, destZ) + visitPenalty + safetyPenalty;
                open.add(new AStarNode(nx, nz, tentativeG, nf));
            }
        }

        if (bestKey == startKey) {
            String summary = String.format(Locale.ROOT, "radius=%d,nodeLimit=%d,expanded=%d,reachedGoal=%s,bestH=%.3f,best=(%d,%d),result=(0,0)", radius, nodeLimit, expanded, reachedGoal, bestH, unpackX(bestKey), unpackZ(bestKey));
            return new AStarResult(0, 0, summary);
        }

        long stepKey = bestKey;
        while (cameFrom.containsKey(stepKey) && cameFrom.get(stepKey) != startKey) {
            stepKey = cameFrom.get(stepKey);
        }

        if (!cameFrom.containsKey(stepKey) && stepKey != bestKey) {
            String summary = String.format(Locale.ROOT, "radius=%d,nodeLimit=%d,expanded=%d,reachedGoal=%s,bestH=%.3f,best=(%d,%d),result=(0,0),reason=broken-chain", radius, nodeLimit, expanded, reachedGoal, bestH, unpackX(bestKey), unpackZ(bestKey));
            return new AStarResult(0, 0, summary);
        }

        int sx = unpackX(stepKey);
        int sz = unpackZ(stepKey);
        int outDx = Integer.compare(sx, startX);
        int outDz = Integer.compare(sz, startZ);
        String summary = String.format(Locale.ROOT, "radius=%d,nodeLimit=%d,expanded=%d,reachedGoal=%s,bestH=%.3f,best=(%d,%d),next=(%d,%d),result=(%d,%d)", radius, nodeLimit, expanded, reachedGoal, bestH, unpackX(bestKey), unpackZ(bestKey), sx, sz, outDx, outDz);
        return new AStarResult(outDx, outDz, summary);
    }

    private boolean isStepTraversableSnapshot(AStarSnapshot snapshot, int fromX, int fromZ, int toX, int toZ, boolean rejectAirGapDetours) {
        int stepX = Integer.compare(toX, fromX);
        int stepZ = Integer.compare(toZ, fromZ);
        if (stepX == 0 && stepZ == 0) return false;

        if (snapshot.isFloorUnsafe(toX, toZ)) return false;
        if (!snapshot.isColumnTraversable(toX, toZ)) return false;

        if (stepX != 0 && stepZ != 0) {
            if (!snapshot.isColumnTraversable(toX, fromZ)) return false;
            if (!snapshot.isColumnTraversable(fromX, toZ)) return false;
        }

        return true;
    }

    private double computeDetourSafetyPenaltySnapshot(AStarSnapshot snapshot, int x, int z, double penaltyPerNeighbor) {
        if (penaltyPerNeighbor <= 0.0) return 0.0;
        final int[][] dirs = {
            { 1, 0 }, { 1, 1 }, { 0, 1 }, { -1, 1 },
            { -1, 0 }, { -1, -1 }, { 0, -1 }, { 1, -1 }
        };

        int blockedAdjacent = 0;
        for (int[] dir : dirs) {
            int nx = x + dir[0];
            int nz = z + dir[1];
            if (snapshot.isObstacleCell(nx, nz)) blockedAdjacent++;
        }

        return blockedAdjacent * penaltyPerNeighbor;
    }

    private double stepMovementCost(int fromX, int fromZ, int toX, int toZ) {
        int dx = Math.abs(toX - fromX);
        int dz = Math.abs(toZ - fromZ);
        return (dx == 1 && dz == 1) ? ASTAR_DIAGONAL_COST : ASTAR_STRAIGHT_COST;
    }

    private double octileDistance(int x1, int z1, int x2, int z2) {
        int dx = Math.abs(x2 - x1);
        int dz = Math.abs(z2 - z1);
        int min = Math.min(dx, dz);
        int max = Math.max(dx, dz);
        return ASTAR_STRAIGHT_COST * (max - min) + ASTAR_DIAGONAL_COST * min;
    }

    private double computeDetourSafetyPenalty(int x, int z, int py, boolean rejectAirGapDetours) {
        double penaltyPerNeighbor = hardDetourSafetyBufferCost();
        if (penaltyPerNeighbor <= 0.0) return 0.0;

        final int[][] dirs = {
            { 1, 0 }, { 1, 1 }, { 0, 1 }, { -1, 1 },
            { -1, 0 }, { -1, -1 }, { 0, -1 }, { 1, -1 }
        };

        int blockedAdjacent = 0;
        for (int[] dir : dirs) {
            int nx = x + dir[0];
            int nz = z + dir[1];
            if (isObstacleCell(nx, nz, py, rejectAirGapDetours)) blockedAdjacent++;
        }

        return blockedAdjacent * penaltyPerNeighbor;
    }

    private boolean isObstacleCell(int x, int z, int py, boolean rejectAirGapDetours) {
        if (useStealthAvoidLavaDetours()) {
            BlockPos floor = new BlockPos(x, py - 1, z);
            if (touchesLavaDetourRisk(floor)) return true;
        }

        if (rejectAirGapDetours) {
            BlockPos floor = new BlockPos(x, py - 1, z);
            if (touchesAirGapDetourRisk(floor, true)) return true;
        }

        for (int h = 0; h < optTunnelHeight(); h++) {
            BlockPos pos = new BlockPos(x, py + h, z);
            BlockState state = mc.world.getBlockState(pos);

            if (isAvoidanceBlock(state)) return true;
            if (touchesNoTouchChainBlock(pos)) return true;
            if (touchesLavaDetourRisk(pos)) return true;
            if (state.getBlock() == Blocks.BEDROCK) return true;
            if (state.isAir() || isLavaState(state)) continue;
            if (!BlockUtils.canBreak(pos, state)) return true;
        }

        return false;
    }

    private int getDetourAStarRadius(boolean rejectAirGapDetours) {
        if (!rejectAirGapDetours) return DETOUR_ASTAR_RADIUS;
        int configuredProbe = optStealthMode() ? effectiveProbeDistance() : DETOUR_ASTAR_RADIUS;
        return Math.max(1, Math.min(configuredProbe, 64));
    }

    private int getDetourAStarMaxNodes(int radius, boolean rejectAirGapDetours) {
        if (!rejectAirGapDetours) return DETOUR_ASTAR_MAX_NODES;
        int scaled = Math.max(DETOUR_ASTAR_MAX_NODES, radius * radius);
        int configured = Math.max(64, hardPathCalcMaxNodes());
        return Math.max(64, Math.min(configured, Math.min(2048, scaled)));
    }

    private int[] getProactiveAStarCachedStep(int x, int z, int py, boolean axisMode) {
        if (mc == null || mc.player == null) return null;
        if (proactiveAStarCacheFromX == Integer.MIN_VALUE || proactiveAStarCacheAge == Integer.MIN_VALUE) return null;
        int ageDelta = mc.player.age - proactiveAStarCacheAge;
        if (ageDelta < 0 || ageDelta > PROACTIVE_ASTAR_REUSE_TICKS) return null;
        if (x != proactiveAStarCacheFromX || z != proactiveAStarCacheFromZ) return null;
        if (py != proactiveAStarCachePy) return null;
        if (axisMode != proactiveAStarCacheAxisMode) return null;
        if (destX != proactiveAStarCacheDestX || destZ != proactiveAStarCacheDestZ) return null;
        return new int[] { proactiveAStarCacheStepX, proactiveAStarCacheStepZ };
    }

    private void storeProactiveAStarCache(int x, int z, int py, boolean axisMode, int stepX, int stepZ, String summary) {
        proactiveAStarCacheFromX = x;
        proactiveAStarCacheFromZ = z;
        proactiveAStarCachePy = py;
        proactiveAStarCacheDestX = destX;
        proactiveAStarCacheDestZ = destZ;
        proactiveAStarCacheAxisMode = axisMode;
        proactiveAStarCacheStepX = stepX;
        proactiveAStarCacheStepZ = stepZ;
        proactiveAStarCacheSummary = summary == null ? "" : summary;
        proactiveAStarCacheAge = (mc != null && mc.player != null) ? mc.player.age : Integer.MIN_VALUE;
    }

    private boolean isStepTraversable(int fromX, int fromZ, int toX, int toZ, int py) {
        return isStepTraversable(fromX, fromZ, toX, toZ, py, false, false);
    }

    private boolean isStepTraversable(int fromX, int fromZ, int toX, int toZ, int py, boolean captureFailure) {
        return isStepTraversable(fromX, fromZ, toX, toZ, py, captureFailure, false);
    }

    private boolean isStepTraversable(int fromX, int fromZ, int toX, int toZ, int py, boolean captureFailure, boolean rejectAirGaps) {
        int stepX = Integer.compare(toX, fromX);
        int stepZ = Integer.compare(toZ, fromZ);
        if (stepX == 0 && stepZ == 0) {
            if (captureFailure) watchdogTraverseFail = "zero-step";
            watchdogCalc("traversable", String.format(Locale.ROOT, "from=(%d,%d),to=(%d,%d),py=%d,result=false,reason=zero-step", fromX, fromZ, toX, toZ, py));
            return false;
        }

        if (rejectAirGaps) {
            BlockPos supportFloor = new BlockPos(toX, py - 1, toZ);
            if (touchesAirGapDetourRisk(supportFloor, true)) {
                if (captureFailure) watchdogTraverseFail = "touch-air-gap-floor@" + supportFloor.toShortString();
                watchdogCalc("traversable", String.format(Locale.ROOT, "from=(%d,%d),to=(%d,%d),pos=%s,result=false,reason=touch-air-gap-floor", fromX, fromZ, toX, toZ, supportFloor.toShortString()));
                return false;
            }
        }

        if (useStealthAvoidLavaDetours()) {
            BlockPos supportFloor = new BlockPos(toX, py - 1, toZ);
            if (touchesLavaDetourRisk(supportFloor)) {
                if (captureFailure) watchdogTraverseFail = "touch-lava-floor@" + supportFloor.toShortString();
                watchdogCalc("traversable", String.format(Locale.ROOT, "from=(%d,%d),to=(%d,%d),pos=%s,result=false,reason=touch-lava-floor", fromX, fromZ, toX, toZ, supportFloor.toShortString()));
                return false;
            }
        }

        PathStep step = new PathStep(fromX, fromZ, toX, toZ, stepX, stepZ);
        for (BlockPos pos : getStepProfilePositions(py, step, false)) {
            BlockState state = mc.world.getBlockState(pos);

            if (isAvoidanceBlock(state)) {
                if (captureFailure) watchdogTraverseFail = "avoidance@" + pos.toShortString() + ":" + Registries.BLOCK.getId(state.getBlock());
                watchdogCalc("traversable", String.format(Locale.ROOT, "from=(%d,%d),to=(%d,%d),pos=%s,state=%s,result=false,reason=avoidance", fromX, fromZ, toX, toZ, pos.toShortString(), Registries.BLOCK.getId(state.getBlock())));
                return false;
            }
            if (touchesNoTouchChainBlock(pos)) {
                if (captureFailure) watchdogTraverseFail = "touch-chain@" + pos.toShortString();
                watchdogCalc("traversable", String.format(Locale.ROOT, "from=(%d,%d),to=(%d,%d),pos=%s,result=false,reason=touch-chain", fromX, fromZ, toX, toZ, pos.toShortString()));
                return false;
            }
            if (touchesLavaDetourRisk(pos)) {
                if (captureFailure) watchdogTraverseFail = "touch-lava@" + pos.toShortString();
                watchdogCalc("traversable", String.format(Locale.ROOT, "from=(%d,%d),to=(%d,%d),pos=%s,result=false,reason=touch-lava", fromX, fromZ, toX, toZ, pos.toShortString()));
                return false;
            }
            if (state.isAir() || isLavaState(state)) continue;
            if (state.getBlock() == Blocks.BEDROCK) {
                if (captureFailure) watchdogTraverseFail = "bedrock@" + pos.toShortString();
                watchdogCalc("traversable", String.format(Locale.ROOT, "from=(%d,%d),to=(%d,%d),pos=%s,result=false,reason=bedrock", fromX, fromZ, toX, toZ, pos.toShortString()));
                return false;
            }
            if (!BlockUtils.canBreak(pos, state)) {
                if (captureFailure) watchdogTraverseFail = "cannot-break@" + pos.toShortString() + ":" + Registries.BLOCK.getId(state.getBlock());
                watchdogCalc("traversable", String.format(Locale.ROOT, "from=(%d,%d),to=(%d,%d),pos=%s,state=%s,result=false,reason=cannot-break", fromX, fromZ, toX, toZ, pos.toShortString(), Registries.BLOCK.getId(state.getBlock())));
                return false;
            }
        }

        if (captureFailure) watchdogTraverseFail = "";
        watchdogCalc("traversable", String.format(Locale.ROOT, "from=(%d,%d),to=(%d,%d),py=%d,result=true", fromX, fromZ, toX, toZ, py));
        return true;
    }

    private int effectiveProbeDistance() {
        int configured = Math.max(1, optStealthProbeDistance());
        if (optStealthMode()) return configured;
        return Math.min(configured, NON_STEALTH_PROBE_MAX);
    }

    private boolean isAntiLavaActiveForCurrentMode() {
        // Requested behavior: anti-lava fill/pause logic is disabled while stealth mode is active.
        return optLavaAvoidance() && !optStealthMode();
    }

    private double hardDetourSafetyBufferCost() {
        return HARD_DETOUR_SAFETY_BUFFER_COST;
    }

    private int hardPathCalcIntervalTicks() {
        return HARD_PATH_CALC_INTERVAL_TICKS;
    }

    private int hardPathCalcMaxNodes() {
        return HARD_PATH_CALC_MAX_NODES;
    }

    private int hardAStarMaxFails() {
        return HARD_ASTAR_MAX_FAILS;
    }

    private int hardStallStopTicks() {
        return HARD_STALL_STOP_TICKS;
    }

    private boolean shouldMaintainBehindParity() {
        // Stealth mode always maintains parity; non-stealth follows fill-behind toggle.
        return optStealthMode() || optFillBehind();
    }

    private boolean isStrictStealthExactRestoreEnabled() {
        return optStealthMode() && optStrictStealthExactRestore();
    }

    private void hardFailStealthExactRestore(BlockPos pos, BlockState original, String stage) {
        String blockId = Registries.BLOCK.getId(original.getBlock()).toString();
        String details = String.format(
            Locale.ROOT,
            "reason=missing-exact-restore-block,stage=%s,pos=%s,block=%s",
            stage,
            formatPos(pos),
            blockId
        );
        watchdog("stealth-restore-hard-fail", details);
        String msg = "[TunnelMiner] Missing materials to restore tunnel (block=" + blockId + ", pos=" + pos.toShortString() + ").";
        if (optDebugMessages()) warning("Stealth restore hard fail: " + msg + " stage=" + stage);
        if (mc.options != null) mc.options.forwardKey.setPressed(false);
        if (mc.getNetworkHandler() != null && mc.getNetworkHandler().getConnection() != null) {
            mc.getNetworkHandler().getConnection().disconnect(Text.literal(msg));
        }
        if (isActive()) toggle();
    }

    private PathMode hardPathMode() {
        return HARD_PATH_MODE;
    }

    private boolean useStealthAvoidLavaDetours() {
        return optStealthMode();
    }

    private boolean useStealthAvoidAirGapDetours() {
        return optStealthMode();
    }

    private boolean useStealthAvoidStateChangeBlocks() {
        return optStealthMode();
    }

    private boolean useStealthAvoidChainReactionBlocks() {
        return optStealthMode();
    }

    private boolean isAvoidanceBlock(BlockState state) {
        if (useStealthAvoidLavaDetours() && isLavaState(state)) return true;
        return isProtectedState(state) || isNoTouchChainState(state);
    }

    private boolean isProtectedState(BlockState state) {
        if (!useStealthAvoidStateChangeBlocks()) return false;
        return protectedStateBlocks.contains(state.getBlock());
    }

    private boolean isNoTouchChainState(BlockState state) {
        if (!useStealthAvoidChainReactionBlocks()) return false;
        return noTouchChainBlocks.contains(state.getBlock());
    }

    private boolean touchesNoTouchChainBlock(BlockPos pos) {
        if (!useStealthAvoidChainReactionBlocks()) return false;
        if (isNoTouchChainState(mc.world.getBlockState(pos))) return true;

        for (Direction dir : Direction.values()) {
            if (isNoTouchChainState(mc.world.getBlockState(pos.offset(dir)))) return true;
        }

        return false;
    }

    private boolean touchesLavaDetourRisk(BlockPos pos) {
        if (!useStealthAvoidLavaDetours()) return false;
        if (isLavaState(mc.world.getBlockState(pos))) return true;

        for (Direction dir : Direction.values()) {
            if (isLavaState(mc.world.getBlockState(pos.offset(dir)))) return true;
        }

        return false;
    }

    private boolean touchesAirGapDetourRisk(BlockPos floorPos, boolean rejectAirGaps) {
        if (!rejectAirGaps) return false;
        if (mc.world.getBlockState(floorPos).isAir()) return true;

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                BlockPos adjacentFloor = floorPos.add(dx, 0, dz);
                if (mc.world.getBlockState(adjacentFloor).isAir()) return true;
            }
        }

        return false;
    }

    private void reloadAvoidanceBlockSets() {
        protectedStateBlocks.clear();
        noTouchChainBlocks.clear();

        int protectedLoaded = loadEmbeddedAvoidanceBlockIds(EMBEDDED_STATE_CHANGE_BLOCK_IDS, protectedStateBlocks);
        int chainLoaded = loadEmbeddedAvoidanceBlockIds(EMBEDDED_GRAVITY_CHAIN_BLOCK_IDS, noTouchChainBlocks);
        if (optDebugMessages()) info("Loaded avoidance sets: state-change=" + protectedLoaded + ", chain-no-touch=" + chainLoaded + ".");
    }

    private int loadEmbeddedAvoidanceBlockIds(String[] ids, Set<Block> out) {
        int loaded = 0;
        if (ids == null) return 0;

        for (String raw : ids) {
            String token = raw == null ? "" : raw.trim();
            if (token.isEmpty()) continue;

            Identifier id = Identifier.tryParse(token);
            if (id == null || !Registries.BLOCK.containsId(id)) {
                if (optDebugMessages()) warning("Embedded avoidance block id not found: " + token);
                continue;
            }

            Block block = Registries.BLOCK.get(id);
            if (block != Blocks.AIR && out.add(block)) loaded++;
        }

        return loaded;
    }

    private long packXZ(int x, int z) {
        return ((long) x << 32) | (z & 0xffffffffL);
    }

    private int unpackX(long packed) {
        return (int) (packed >> 32);
    }

    private int unpackZ(long packed) {
        return (int) packed;
    }

    private int chebyshevDistance(int x1, int z1, int x2, int z2) {
        return Math.max(Math.abs(x2 - x1), Math.abs(z2 - z1));
    }

    private void markDetourVisit(int x, int z) {
        if (x == lastVisitedX && z == lastVisitedZ) return;
        prevVisitedX = lastVisitedX;
        prevVisitedZ = lastVisitedZ;
        lastVisitedX = x;
        lastVisitedZ = z;

        long key = packXZ(x, z);
        detourVisitCounts.merge(key, 1, Integer::sum);
        watchdogCalc("detour-visit", String.format(Locale.ROOT, "pos=(%d,%d),visits=%d,mapSize=%d", x, z, detourVisitCounts.getOrDefault(key, 0), detourVisitCounts.size()));

        if (detourVisitCounts.size() <= 4096) return;
        int trimmed = 0;
        Iterator<Long> it = detourVisitCounts.keySet().iterator();
        while (it.hasNext() && trimmed < 512) {
            it.next();
            it.remove();
            trimmed++;
        }
        watchdogCalc("detour-visit-trim", String.format(Locale.ROOT, "trimmed=%d,newSize=%d", trimmed, detourVisitCounts.size()));
    }

    private void ensureStealthPickaxe() {
        if (normalMining != null || packetMining != null || !activeMineTargets.isEmpty()) ensurePickaxe();
    }

    private void runAheadMining(List<BlockPos> targets) {
        if (targets.isEmpty()) {
            watchdogMineSummary = "targets=0,action=none";
            watchdogCalc("mine-ahead", watchdogMineSummary);
            return;
        }

        int doubleQueued = 0;
        int directAttempts = 0;
        int directSkippedNonCandidate = 0;
        int directSkippedTimer = 0;
        int doubleSkipped = 0;
        boolean startedDoubleMine = false;

        if (optStealthDoubleMine()) {
            ArrayDeque<BlockPos> doubleMineTargets = new ArrayDeque<>();
            for (BlockPos pos : targets) {
                BlockState state = mc.world.getBlockState(pos);
                if (!isMineCandidate(pos, state) || BlockUtils.canInstaBreak(pos)) {
                    doubleSkipped++;
                    continue;
                }
                if (normalMining != null && pos.equals(normalMining.blockPos)) {
                    doubleSkipped++;
                    continue;
                }
                if (packetMining != null && pos.equals(packetMining.blockPos)) {
                    doubleSkipped++;
                    continue;
                }
                doubleMineTargets.add(pos.toImmutable());
            }
            doubleQueued = doubleMineTargets.size();

            if (!doubleMineTargets.isEmpty()) {
                ensurePickaxe();
                runStealthDoubleMine(doubleMineTargets);
                startedDoubleMine = normalMining != null || packetMining != null;
            }

            if (normalMining != null || packetMining != null) {
                watchdogMineSummary = String.format(
                    Locale.ROOT,
                    "targets=%d,doubleQueued=%d,doubleSkipped=%d,doubleActive=true,started=%s,normal=%s,packet=%s",
                    targets.size(),
                    doubleQueued,
                    doubleSkipped,
                    startedDoubleMine,
                    formatMineBlock(normalMining),
                    formatMineBlock(packetMining)
                );
                watchdogCalc("mine-ahead", watchdogMineSummary);
                return;
            }
        }

        int attempts = 0;
        for (BlockPos pos : targets) {
            if (attempts >= optBreaksPerTick() || stealthBreakTimer > 0) {
                directSkippedTimer++;
                watchdogMineSummary = String.format(
                    Locale.ROOT,
                    "targets=%d,doubleQueued=%d,doubleSkipped=%d,directAttempts=%d,directSkippedNonCandidate=%d,directSkippedTimer=%d,breaksPerTick=%d,stealthBreakTimer=%d,returnedEarly=true,reason=tick-limit-or-break-timer,normal=%s,packet=%s",
                    targets.size(),
                    doubleQueued,
                    doubleSkipped,
                    directAttempts,
                    directSkippedNonCandidate,
                    directSkippedTimer,
                    optBreaksPerTick(),
                    stealthBreakTimer,
                    formatMineBlock(normalMining),
                    formatMineBlock(packetMining)
                );
                watchdogCalc("mine-ahead", watchdogMineSummary);
                return;
            }

            BlockState state = mc.world.getBlockState(pos);
            if (!isMineCandidate(pos, state)) {
                directSkippedNonCandidate++;
                continue;
            }

            ensurePickaxe();
            BlockUtils.breakBlock(pos, true);

            stealthBreakTimer = optStealthBreakDelay();
            attempts++;
            directAttempts++;
            if (!BlockUtils.canInstaBreak(pos)) {
                watchdogMineSummary = String.format(
                    Locale.ROOT,
                    "targets=%d,doubleQueued=%d,doubleSkipped=%d,directAttempts=%d,directSkippedNonCandidate=%d,directSkippedTimer=%d,breaksPerTick=%d,stealthBreakTimer=%d,returnedEarly=true,normal=%s,packet=%s",
                    targets.size(),
                    doubleQueued,
                    doubleSkipped,
                    directAttempts,
                    directSkippedNonCandidate,
                    directSkippedTimer,
                    optBreaksPerTick(),
                    stealthBreakTimer,
                    formatMineBlock(normalMining),
                    formatMineBlock(packetMining)
                );
                watchdogCalc("mine-ahead", watchdogMineSummary);
                return;
            }
        }

        watchdogMineSummary = String.format(
            Locale.ROOT,
            "targets=%d,doubleQueued=%d,doubleSkipped=%d,directAttempts=%d,directSkippedNonCandidate=%d,directSkippedTimer=%d,breaksPerTick=%d,stealthBreakTimer=%d,normal=%s,packet=%s",
            targets.size(),
            doubleQueued,
            doubleSkipped,
            directAttempts,
            directSkippedNonCandidate,
            directSkippedTimer,
            optBreaksPerTick(),
            stealthBreakTimer,
            formatMineBlock(normalMining),
            formatMineBlock(packetMining)
        );
        watchdogCalc("mine-ahead", watchdogMineSummary);
    }

    private void tickStealthDoubleMine() {
        String prevNormal = formatMineBlock(normalMining);
        String prevPacket = formatMineBlock(packetMining);
        boolean changed = false;

        if (normalMining != null) {
            if (normalMining.shouldRemove()) {
                normalMining.abortDestroying();
                normalMining = null;
                StealthDoubleMineBlock.rateLimited = true;
                changed = true;
            } else if (mc.world.getBlockState(normalMining.blockPos).getBlock() != normalMining.block) {
                normalMining = null;
                blocksMined++;
                StealthDoubleMineBlock.rateLimited = false;
                changed = true;
            } else if (normalMining.isReady()) {
                normalMining.stopDestroying();
                changed = true;
            }

            mc.player.swingHand(Hand.MAIN_HAND);
        }

        if (packetMining != null) {
            if (packetMining.shouldRemove()) {
                packetMining = null;
                changed = true;
            } else if (mc.world.getBlockState(packetMining.blockPos).getBlock() != packetMining.block) {
                packetMining = null;
                blocksMined++;
                changed = true;
            }
        }

        if (changed) {
            watchdogCalc(
                "double-mine-tick",
                String.format(
                    Locale.ROOT,
                    "prevNormal=%s,prevPacket=%s,newNormal=%s,newPacket=%s,rateLimited=%s,blocksMined=%d",
                    prevNormal,
                    prevPacket,
                    formatMineBlock(normalMining),
                    formatMineBlock(packetMining),
                    StealthDoubleMineBlock.rateLimited,
                    blocksMined
                )
            );
        }
    }

    private void runStealthDoubleMine(ArrayDeque<BlockPos> blocks) {
        if (stealthBreakTimer > 0) {
            watchdogCalc("double-mine-run", String.format(Locale.ROOT, "result=blocked-timer,stealthBreakTimer=%d,queue=%d", stealthBreakTimer, blocks.size()));
            return;
        }

        if (normalMining == null && !blocks.isEmpty()) {
            normalMining = new StealthDoubleMineBlock(this, blocks.pop()).startDestroying();
            stealthBreakTimer = optStealthBreakDelay();
            watchdogCalc("double-mine-run", String.format(Locale.ROOT, "action=start-normal,normal=%s,queue=%d,stealthBreakTimer=%d", formatMineBlock(normalMining), blocks.size(), stealthBreakTimer));
            if (stealthBreakTimer > 0) return;
        }

        if (StealthDoubleMineBlock.rateLimited) {
            watchdogCalc("double-mine-run", String.format(Locale.ROOT, "result=rate-limited,normal=%s,packet=%s,queue=%d", formatMineBlock(normalMining), formatMineBlock(packetMining), blocks.size()));
            return;
        }

        if (packetMining == null && !blocks.isEmpty() && normalMining != null) {
            StealthDoubleMineBlock block = new StealthDoubleMineBlock(this, blocks.pop());
            packetMining = normalMining.packetMine();
            normalMining = block.startDestroying();
            stealthBreakTimer = optStealthBreakDelay();
            watchdogCalc("double-mine-run", String.format(Locale.ROOT, "action=start-packet,normal=%s,packet=%s,queue=%d,stealthBreakTimer=%d", formatMineBlock(normalMining), formatMineBlock(packetMining), blocks.size(), stealthBreakTimer));
        }
    }

    private void clearStealthMining(boolean abortNormal) {
        if (abortNormal && normalMining != null) normalMining.abortDestroying();
        normalMining = null;
        packetMining = null;
        StealthDoubleMineBlock.rateLimited = false;
        stealthBreakTimer = 0;
        watchdogCalc("double-mine-clear", "abortNormal=" + abortNormal);
    }

    private int findExactBlockInInv(Block block) {
        return findInInv(stack -> {
            if (stack.isEmpty()) return false;
            if (!(stack.getItem() instanceof BlockItem blockItem)) return false;
            return blockItem.getBlock() == block;
        });
    }

    private int findFallbackRestoreSlot(BlockPos target) {
        int nearby = findAdjacentReplacementSlot(target);
        if (nearby != -1) {
            watchdogCalc("restore-fallback-slot", String.format(Locale.ROOT, "target=%s,source=adjacent,slot=%d", formatPos(target), nearby));
            return nearby;
        }

        int fill = findBlockToPlace();
        if (fill != -1) {
            watchdogCalc("restore-fallback-slot", String.format(Locale.ROOT, "target=%s,source=fill-blocks,slot=%d", formatPos(target), fill));
            return fill;
        }

        int any = findAnySafeReplacementSlot();
        watchdogCalc("restore-fallback-slot", String.format(Locale.ROOT, "target=%s,source=any-safe,slot=%d", formatPos(target), any));
        return any;
    }

    private boolean hasAdjacentAirForRestore(BlockPos target) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    BlockPos probe = target.add(dx, dy, dz);
                    if (!mc.world.getBlockState(probe).isAir()) continue;
                    watchdogCalc("restore-adj-air", String.format(Locale.ROOT, "target=%s,result=true,adj=%s,delta=(%d,%d,%d)", formatPos(target), formatPos(probe), dx, dy, dz));
                    return true;
                }
            }
        }

        watchdogCalc("restore-adj-air", String.format(Locale.ROOT, "target=%s,result=false", formatPos(target)));
        return false;
    }

    private int findAdjacentReplacementSlot(BlockPos target) {
        HashMap<Block, Integer> localCounts = new HashMap<>();

        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;

                    BlockState state = mc.world.getBlockState(target.add(dx, dy, dz));
                    if (!isUsableRestoreReplacementBlock(state)) continue;
                    localCounts.merge(state.getBlock(), 1, Integer::sum);
                }
            }
        }

        if (localCounts.isEmpty()) {
            watchdogCalc("restore-adj-slot", String.format(Locale.ROOT, "target=%s,result=-1,reason=no-nearby-usable-blocks", formatPos(target)));
            return -1;
        }

        List<Map.Entry<Block, Integer>> ranked = new ArrayList<>(localCounts.entrySet());
        ranked.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        StringBuilder ranking = new StringBuilder();

        for (Map.Entry<Block, Integer> entry : ranked) {
            if (ranking.length() > 0) ranking.append(';');
            ranking.append(Registries.BLOCK.getId(entry.getKey())).append(':').append(entry.getValue());
            int slot = findExactBlockInInv(entry.getKey());
            if (slot != -1) {
                watchdogCalc("restore-adj-slot", String.format(Locale.ROOT, "target=%s,result=%d,match=%s,ranking=%s", formatPos(target), slot, Registries.BLOCK.getId(entry.getKey()), ranking));
                return slot;
            }
        }

        watchdogCalc("restore-adj-slot", String.format(Locale.ROOT, "target=%s,result=-1,reason=not-in-inv,ranking=%s", formatPos(target), ranking));
        return -1;
    }

    private int findAnySafeReplacementSlot() {
        int slot = findInInv(stack -> {
            if (stack.isEmpty()) return false;
            if (!(stack.getItem() instanceof BlockItem blockItem)) return false;

            Block block = blockItem.getBlock();
            BlockState state = block.getDefaultState();
            if (!isUsableRestoreReplacementBlock(state)) return false;

            // Avoid placing containers/utility blocks as generic restore filler.
            if (block instanceof BlockWithEntity) return false;
            return true;
        });
        watchdogCalc("restore-any-slot", String.format(Locale.ROOT, "result=%d", slot));
        return slot;
    }

    private boolean isUsableRestoreReplacementBlock(BlockState state) {
        if (state.isAir()) return false;
        if (state.getBlock() == Blocks.BEDROCK) return false;
        if (!state.getFluidState().isEmpty()) return false;
        if (isAvoidanceBlock(state)) return false;
        return true;
    }

    private boolean isLavaState(BlockState state) {
        return state.getBlock() == Blocks.LAVA || state.getFluidState().isIn(FluidTags.LAVA);
    }

    private static boolean isPickaxe(ItemStack s) {
        if (s.isEmpty()) return false;
        Item it = s.getItem();
        return it == Items.WOODEN_PICKAXE || it == Items.STONE_PICKAXE
            || it == Items.IRON_PICKAXE || it == Items.GOLDEN_PICKAXE
            || it == Items.DIAMOND_PICKAXE || it == Items.NETHERITE_PICKAXE;
    }

    private static boolean isShulkerBox(ItemStack s) {
        return !s.isEmpty() && s.getItem() instanceof BlockItem bi
            && bi.getBlock() instanceof ShulkerBoxBlock;
    }

    private boolean shulkerContainsPickaxe(ItemStack shulker) {
        return countPickaxesInShulker(shulker) > 0;
    }

    private int countPickaxesInShulker(ItemStack shulker) {
        if (!isShulkerBox(shulker)) return 0;
        ContainerComponent container = shulker.get(DataComponentTypes.CONTAINER);
        if (container == null) return 0;
        int picks = 0;
        for (ItemStack stack : container.iterateNonEmpty()) {
            if (isPickaxe(stack)) picks += Math.max(1, stack.getCount());
        }
        return picks;
    }

    private int countPickaxesStoredInInventoryShulkers() {
        int total = 0;
        for (int i = 0; i < mc.player.getInventory().size(); i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!isShulkerBox(stack)) continue;
            total += countPickaxesInShulker(stack);
        }
        return total;
    }

    private static boolean isEnderChest(ItemStack s) {
        return !s.isEmpty() && s.isOf(Items.ENDER_CHEST);
    }

    private static int durabilityLeft(ItemStack s) {
        if (!s.isDamageable()) return Integer.MAX_VALUE;
        Integer d = s.get(DataComponentTypes.DAMAGE);
        return s.getMaxDamage() - (d != null ? d : 0);
    }

    private int countPickaxes() {
        int n = 0;
        for (int i = 0; i < mc.player.getInventory().size(); i++) {
            if (isPickaxe(mc.player.getInventory().getStack(i))) n++;
        }
        return n;
    }

    private boolean needsRestock() {
        if (!optUseShulkers() && !optUseEnderChest()) return false;
        int pickaxes = countPickaxes();
        int minimum = optMinPickaxes();
        return pickaxes < minimum;
    }

    private int findInInv(Predicate<ItemStack> p) {
        for (int i = 0; i < mc.player.getInventory().size(); i++) {
            if (p.test(mc.player.getInventory().getStack(i))) return i;
        }
        return -1;
    }

    private int equipBestPickaxe() {
        int best = -1;
        int bestDurability = Integer.MAX_VALUE;
        StringBuilder scored = new StringBuilder();
        for (int i = 0; i < mc.player.getInventory().size(); i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            int sc = isPickaxe(s) ? durabilityLeft(s) : Integer.MAX_VALUE;
            if (scored.length() > 0) scored.append(';');
            scored.append(i).append(':').append(sc).append(':').append(Registries.ITEM.getId(s.getItem()));
            if (isPickaxe(s) && sc < bestDurability) {
                bestDurability = sc;
                best = i;
            }
        }
        if (best == -1) {
            watchdogCalc("equip-pickaxe", "result=-1,scores=" + scored);
            return -1;
        }
        int hb = toHotbar(best);
        if (hb == -1) hb = 0;
        InvUtils.swap(hb, true);
        watchdogCalc("equip-pickaxe", String.format(Locale.ROOT, "bestInv=%d,bestDurability=%d,hotbar=%d,scores=%s", best, bestDurability, hb, scored));
        return hb;
    }

    private void ensurePickaxe() {
        if (pickSlot >= 0 && isPickaxe(mc.player.getInventory().getStack(pickSlot))) {
            InvUtils.swap(pickSlot, true);
            watchdogCalc("ensure-pickaxe", String.format(Locale.ROOT, "action=swap-existing,pickSlot=%d", pickSlot));
        } else {
            pickSlot = equipBestPickaxe();
            watchdogCalc("ensure-pickaxe", String.format(Locale.ROOT, "action=equip-best,newPickSlot=%d", pickSlot));
        }
    }

    // Returns hotbar slot for the given inventory slot, moving to hotbar if needed.
    // All scripted inventory transfers here intentionally use shift-click semantics.
    private int toHotbar(int slot) {
        if (slot >= 0 && slot < 9) {
            watchdogCalc("to-hotbar", String.format(Locale.ROOT, "from=%d,to=%d,reason=already-hotbar,pickSlot=%d", slot, slot, pickSlot));
            return slot;
        }
        if (slot < 0 || slot >= mc.player.getInventory().size()) {
            watchdogCalc("to-hotbar", String.format(Locale.ROOT, "from=%d,to=-1,reason=invalid-slot,pickSlot=%d", slot, pickSlot));
            return -1;
        }

        int direct = shiftClickToHotbar(slot);
        if (direct != -1) return direct;

        int freed = freeHotbarSlotByShiftClick();
        if (freed != -1) {
            int retried = shiftClickToHotbar(slot);
            if (retried != -1) {
                watchdogCalc("to-hotbar", String.format(Locale.ROOT, "from=%d,to=%d,reason=retry-after-free,freed=%d,pickSlot=%d", slot, retried, freed, pickSlot));
                return retried;
            }
        }

        watchdogCalc("to-hotbar", String.format(Locale.ROOT, "from=%d,to=-1,reason=shift-failed,pickSlot=%d", slot, pickSlot));
        return -1;
    }

    private int shiftClickToHotbar(int sourceSlot) {
        ItemStack sourceBefore = mc.player.getInventory().getStack(sourceSlot).copy();
        if (sourceBefore.isEmpty()) {
            watchdogCalc("to-hotbar-shift", String.format(Locale.ROOT, "from=%d,result=-1,reason=source-empty", sourceSlot));
            return -1;
        }

        ItemStack[] hotbarBefore = snapshotHotbar();
        int sourceCountBefore = sourceBefore.getCount();

        InvUtils.shiftClick().slot(sourceSlot);

        int bestChanged = -1;
        int bestDelta = Integer.MIN_VALUE;

        for (int i = 0; i < 9; i++) {
            ItemStack before = hotbarBefore[i];
            ItemStack after = mc.player.getInventory().getStack(i);
            if (sameInventorySignature(before, after)) continue;

            if (!after.isEmpty() && after.getItem() == sourceBefore.getItem()) {
                int delta = after.getCount() - before.getCount();
                if (delta > bestDelta) {
                    bestDelta = delta;
                    bestChanged = i;
                }
            } else if (bestChanged == -1) {
                bestChanged = i;
            }
        }

        if (bestChanged != -1) {
            watchdogCalc(
                "to-hotbar-shift",
                String.format(
                    Locale.ROOT,
                    "from=%d,result=%d,reason=changed-hotbar,sourceItem=%s,sourceCountBefore=%d,bestDelta=%d",
                    sourceSlot,
                    bestChanged,
                    Registries.ITEM.getId(sourceBefore.getItem()),
                    sourceCountBefore,
                    bestDelta
                )
            );
            return bestChanged;
        }

        ItemStack sourceAfter = mc.player.getInventory().getStack(sourceSlot);
        int sourceCountAfter = sourceAfter.getCount();
        if (sourceAfter.isEmpty() || sourceCountAfter < sourceCountBefore) {
            int fallback = findHotbarWithItem(sourceBefore.getItem());
            watchdogCalc(
                "to-hotbar-shift",
                String.format(
                    Locale.ROOT,
                    "from=%d,result=%d,reason=source-decreased-fallback,sourceItem=%s,sourceCountBefore=%d,sourceCountAfter=%d",
                    sourceSlot,
                    fallback,
                    Registries.ITEM.getId(sourceBefore.getItem()),
                    sourceCountBefore,
                    sourceCountAfter
                )
            );
            return fallback;
        }

        watchdogCalc(
            "to-hotbar-shift",
            String.format(
                Locale.ROOT,
                "from=%d,result=-1,reason=no-hotbar-change,sourceItem=%s,sourceCountBefore=%d,sourceCountAfter=%d",
                sourceSlot,
                Registries.ITEM.getId(sourceBefore.getItem()),
                sourceCountBefore,
                sourceCountAfter
            )
        );
        return -1;
    }

    private int freeHotbarSlotByShiftClick() {
        for (int i = 0; i < 9; i++) {
            if (i == pickSlot) continue;
            ItemStack before = mc.player.getInventory().getStack(i).copy();
            if (before.isEmpty()) return i;

            InvUtils.shiftClick().slot(i);
            ItemStack after = mc.player.getInventory().getStack(i);
            if (after.isEmpty() || !sameInventorySignature(before, after)) {
                watchdogCalc(
                    "to-hotbar-free",
                    String.format(
                        Locale.ROOT,
                        "result=%d,reason=shift-cleared-or-changed,itemBefore=%s,countBefore=%d,countAfter=%d",
                        i,
                        Registries.ITEM.getId(before.getItem()),
                        before.getCount(),
                        after.getCount()
                    )
                );
                return i;
            }
        }

        watchdogCalc("to-hotbar-free", "result=-1,reason=no-freeable-slot");
        return -1;
    }

    private int findHotbarWithItem(Item item) {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!stack.isEmpty() && stack.getItem() == item) return i;
        }
        return -1;
    }

    private ItemStack[] snapshotHotbar() {
        ItemStack[] out = new ItemStack[9];
        for (int i = 0; i < 9; i++) out[i] = mc.player.getInventory().getStack(i).copy();
        return out;
    }

    private boolean sameInventorySignature(ItemStack a, ItemStack b) {
        if (a.isEmpty() && b.isEmpty()) return true;
        if (a.isEmpty() || b.isEmpty()) return false;
        if (a.getItem() != b.getItem()) return false;
        return a.getCount() == b.getCount();
    }

    private int findBlockToPlace() {
        int slot = findInInv(s -> {
            if (s.isEmpty()) return false;
            if (!(s.getItem() instanceof BlockItem bi)) return false;
            return optFillBlocks().contains(bi.getBlock());
        });
        watchdogCalc("find-block-to-place", String.format(Locale.ROOT, "slot=%d,fillBlocks=%d", slot, optFillBlocks().size()));
        return slot;
    }

    private int findTraversalPlacementSlot(BlockPos target) {
        int fill = findBlockToPlace();
        if (fill != -1) {
            watchdogCalc("find-traversal-slot", String.format(Locale.ROOT, "target=%s,source=fill-blocks,slot=%d", formatPos(target), fill));
            return fill;
        }

        int nearby = findAdjacentReplacementSlot(target);
        if (nearby != -1) {
            watchdogCalc("find-traversal-slot", String.format(Locale.ROOT, "target=%s,source=adjacent,slot=%d", formatPos(target), nearby));
            return nearby;
        }

        int any = findAnySafeReplacementSlot();
        watchdogCalc("find-traversal-slot", String.format(Locale.ROOT, "target=%s,source=any-safe,slot=%d", formatPos(target), any));
        return any;
    }

    private static class StealthDoubleMineBlock {
        private static boolean rateLimited = false;

        private final TunnelMinerModule module;
        private final BlockPos blockPos;
        private final BlockState blockState;
        private final Block block;
        private final Direction direction;

        private int normalStartTime;
        private int packetStartTime;
        private boolean packet;

        private StealthDoubleMineBlock(TunnelMinerModule module, BlockPos pos) {
            this.module = module;
            this.blockPos = pos;
            this.blockState = module.mc.world.getBlockState(this.blockPos);
            this.block = this.blockState.getBlock();
            Direction dir = BlockUtils.getDirection(pos);
            this.direction = dir != null ? dir : Direction.UP;
            this.packet = false;
        }

        private StealthDoubleMineBlock startDestroying() {
            if (module.mc.getNetworkHandler() != null) {
                module.mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(
                    PlayerActionC2SPacket.Action.START_DESTROY_BLOCK,
                    this.blockPos,
                    this.direction
                ));
            }
            normalStartTime = module.mc.player.age;
            return this;
        }

        private StealthDoubleMineBlock stopDestroying() {
            if (module.mc.getNetworkHandler() != null) {
                module.mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(
                    PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK,
                    this.blockPos,
                    this.direction
                ));
            }
            return this;
        }

        private void abortDestroying() {
            if (module.mc.getNetworkHandler() != null) {
                module.mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(
                    PlayerActionC2SPacket.Action.ABORT_DESTROY_BLOCK,
                    this.blockPos,
                    this.direction
                ));
            }
        }

        private StealthDoubleMineBlock packetMine() {
            packetStartTime = module.mc.player.age;
            packet = true;
            return stopDestroying();
        }

        private boolean isReady() {
            return progress() >= (module.optStealthFastBreak() ? 0.7 : 1.0);
        }

        private boolean shouldRemove() {
            if (module.mc.player == null) return true;

            Vec3d eyes = module.mc.player.getEyePos();
            double tx = blockPos.getX() + direction.getOffsetX();
            double ty = blockPos.getY() + direction.getOffsetY();
            double tz = blockPos.getZ() + direction.getOffsetZ();
            double range = module.mc.player.getBlockInteractionRange();

            boolean distance = !packet && eyes.squaredDistanceTo(tx, ty, tz) > range * range;
            boolean timeout = progress() > 2.0 && (module.mc.player.age - (packet ? packetStartTime : normalStartTime) > 60);

            return distance || timeout;
        }

        private double progress() {
            int slot = module.mc.player.getInventory().getSelectedSlot();
            return BlockUtils.getBreakDelta(slot, blockState) * ((module.mc.player.age - (packet ? packetStartTime : normalStartTime)) + 1);
        }
    }

    private void setPhase(Phase newPhase) {
        if (this.phase != newPhase) {
            if (optDebugMessages()) info("Phase: " + (this.phase == null ? "NONE" : this.phase) + " -> " + newPhase);
            watchdog("phase-change", (this.phase == null ? "NONE" : this.phase.name()) + "->" + newPhase.name());
            this.phase = newPhase;
        }

    }

    private void clearResumeCacheState(String reason) {
        fillLog.clear();
        stealthCache.clear();
        resumeStateAvailable = false;
        resumeFromCompletedRun = false;
        clearResumeCacheFile();
        watchdog("resume-cache-cleared", "reason=" + reason);
    }

    private void saveResumeCacheToDisk() {
        if (!optResumeCacheOnReactivate()) {
            clearResumeCacheFile();
            return;
        }

        Path path = resolveResumeCachePath();
        if (!resumeStateAvailable || (fillLog.isEmpty() && stealthCache.isEmpty())) {
            try {
                if (Files.exists(path)) Files.delete(path);
            } catch (IOException e) {
                warning("Failed deleting resume cache file (" + path + "): " + e.getMessage());
            }
            return;
        }

        try {
            Path parent = path.getParent();
            if (parent != null) Files.createDirectories(parent);

            StringBuilder out = new StringBuilder(Math.max(8192, (stealthCache.size() + fillLog.size()) * 40));
            out.append(RESUME_CACHE_MAGIC).append('\n');
            appendResumeMeta(out, "destX", requestedDestX);
            appendResumeMeta(out, "destZ", requestedDestZ);
            appendResumeMeta(out, "onXAxis", onXAxis);
            appendResumeMeta(out, "stealthMode", resumeStealthMode);
            appendResumeMeta(out, "tunnelHeight", resumeTunnelHeight);
            appendResumeMeta(out, "tunnelY", resumeTunnelY);
            appendResumeMeta(out, "pauseX", resumePauseX);
            appendResumeMeta(out, "pauseZ", resumePauseZ);
            appendResumeMeta(out, "pauseValid", resumePausePosValid);
            appendResumeMeta(out, "stateAvailable", resumeStateAvailable);
            appendResumeMeta(out, "completedRun", resumeFromCompletedRun);
            appendResumeMeta(out, "dimension", currentDimensionKey());
            appendResumeMeta(out, "savedAt", System.currentTimeMillis());
            appendResumeMeta(out, "stealthEntries", stealthCache.size());
            appendResumeMeta(out, "fillEntries", fillLog.size());

            for (Map.Entry<BlockPos, BlockState> entry : stealthCache.entrySet()) {
                appendResumeEntry(out, "stealth", entry);
            }
            for (Map.Entry<BlockPos, BlockState> entry : fillLog.entrySet()) {
                appendResumeEntry(out, "fill", entry);
            }

            Path tmp = path.resolveSibling(path.getFileName().toString() + ".tmp");
            Files.writeString(tmp, out.toString(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
            watchdogCalc("resume-cache-save", String.format(Locale.ROOT, "result=true,path=%s,stealth=%d,fill=%d,pauseValid=%s,completedRun=%s", path, stealthCache.size(), fillLog.size(), resumePausePosValid, resumeFromCompletedRun));
        } catch (IOException e) {
            warning("Failed writing resume cache file (" + path + "): " + e.getMessage());
            watchdogCalc("resume-cache-save", String.format(Locale.ROOT, "result=false,path=%s,error=%s", path, e.getMessage()));
        }
    }

    private void loadResumeCacheFromDisk() {
        if (!optResumeCacheOnReactivate()) return;

        Path path = resolveResumeCachePath();
        if (!Files.exists(path)) return;

        LinkedHashMap<BlockPos, BlockState> loadedStealth = new LinkedHashMap<>();
        LinkedHashMap<BlockPos, BlockState> loadedFill = new LinkedHashMap<>();
        HashMap<String, String> meta = new HashMap<>();

        try {
            List<String> lines = Files.readAllLines(path);
            if (lines.isEmpty() || !RESUME_CACHE_MAGIC.equals(lines.get(0).trim())) {
                warning("Invalid resume cache header, ignoring file: " + path);
                return;
            }

            for (int i = 1; i < lines.size(); i++) {
                String line = lines.get(i) == null ? "" : lines.get(i).trim();
                if (line.isEmpty() || line.startsWith("#")) continue;

                String[] parts = line.split("\\|");
                if (parts.length < 1) continue;

                if ("meta".equals(parts[0])) {
                    if (parts.length >= 3) meta.put(parts[1], parts[2]);
                    continue;
                }

                if (parts.length < 5) continue;
                int x = parseIntSafe(parts[1], Integer.MIN_VALUE);
                int y = parseIntSafe(parts[2], Integer.MIN_VALUE);
                int z = parseIntSafe(parts[3], Integer.MIN_VALUE);
                if (x == Integer.MIN_VALUE || y == Integer.MIN_VALUE || z == Integer.MIN_VALUE) continue;

                BlockState state = parsePersistedBlockState(parts[4]);
                if (state == null) continue;

                BlockPos pos = new BlockPos(x, y, z);
                if ("stealth".equals(parts[0])) loadedStealth.put(pos, state);
                else if ("fill".equals(parts[0])) loadedFill.put(pos, state);
            }
        } catch (IOException e) {
            warning("Failed reading resume cache file (" + path + "): " + e.getMessage());
            watchdogCalc("resume-cache-load", String.format(Locale.ROOT, "result=false,path=%s,error=%s", path, e.getMessage()));
            return;
        }

        String savedDimension = meta.getOrDefault("dimension", "unknown");
        String currentDimension = currentDimensionKey();
        if (!"unknown".equals(savedDimension) && !"unknown".equals(currentDimension) && !savedDimension.equals(currentDimension)) {
            watchdogCalc("resume-cache-load", String.format(Locale.ROOT, "result=false,path=%s,reason=dimension-mismatch,saved=%s,current=%s", path, savedDimension, currentDimension));
            clearResumeCacheState("dimension-mismatch");
            return;
        }

        resumeDestX = parseIntSafe(meta.get("destX"), resumeDestX);
        resumeDestZ = parseIntSafe(meta.get("destZ"), resumeDestZ);
        resumeOnXAxis = Boolean.parseBoolean(meta.getOrDefault("onXAxis", Boolean.toString(resumeOnXAxis)));
        resumeStealthMode = Boolean.parseBoolean(meta.getOrDefault("stealthMode", Boolean.toString(resumeStealthMode)));
        resumeTunnelHeight = parseIntSafe(meta.get("tunnelHeight"), resumeTunnelHeight);
        resumeTunnelY = parseIntSafe(meta.get("tunnelY"), resumeTunnelY);
        resumePauseX = parseIntSafe(meta.get("pauseX"), resumePauseX);
        resumePauseZ = parseIntSafe(meta.get("pauseZ"), resumePauseZ);
        resumePausePosValid = Boolean.parseBoolean(meta.getOrDefault("pauseValid", Boolean.toString(resumePausePosValid)));
        resumeStateAvailable = Boolean.parseBoolean(meta.getOrDefault("stateAvailable", "false"));
        resumeFromCompletedRun = Boolean.parseBoolean(meta.getOrDefault("completedRun", "false"));

        if (!resumeStateAvailable) return;

        stealthCache.clear();
        fillLog.clear();
        stealthCache.putAll(loadedStealth);
        fillLog.putAll(loadedFill);
        resumeStateAvailable = !stealthCache.isEmpty() || !fillLog.isEmpty();
        watchdogCalc("resume-cache-load", String.format(Locale.ROOT, "result=true,path=%s,stealth=%d,fill=%d,pauseValid=%s,completedRun=%s", path, stealthCache.size(), fillLog.size(), resumePausePosValid, resumeFromCompletedRun));
    }

    private void clearResumeCacheFile() {
        Path path = resolveResumeCachePath();
        try {
            if (Files.exists(path)) Files.delete(path);
        } catch (IOException e) {
            warning("Failed deleting resume cache file (" + path + "): " + e.getMessage());
        }
    }

    private void appendResumeMeta(StringBuilder out, String key, Object value) {
        out.append("meta|").append(key).append('|').append(value == null ? "" : value).append('\n');
    }

    private void appendResumeEntry(StringBuilder out, String group, Map.Entry<BlockPos, BlockState> entry) {
        if (entry == null || entry.getKey() == null || entry.getValue() == null) return;
        Identifier id = Registries.BLOCK.getId(entry.getValue().getBlock());
        if (id == null) return;
        BlockPos pos = entry.getKey();
        out.append(group).append('|')
            .append(pos.getX()).append('|')
            .append(pos.getY()).append('|')
            .append(pos.getZ()).append('|')
            .append(id)
            .append('\n');
    }

    private int parseIntSafe(String value, int fallback) {
        if (value == null || value.isBlank()) return fallback;
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private double parseDoubleSafe(String value, double fallback) {
        if (value == null || value.isBlank()) return fallback;
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private BlockState parsePersistedBlockState(String value) {
        if (value == null || value.isBlank()) return null;
        Identifier id = Identifier.tryParse(value.trim());
        if (id == null || !Registries.BLOCK.containsId(id)) return null;
        return Registries.BLOCK.get(id).getDefaultState();
    }

    private String currentDimensionKey() {
        if (mc != null && mc.world != null && mc.world.getRegistryKey() != null && mc.world.getRegistryKey().getValue() != null) {
            return mc.world.getRegistryKey().getValue().toString();
        }
        return "unknown";
    }

    private Path resolveResumeCachePath() {
        if (mc != null && mc.runDirectory != null) {
            return mc.runDirectory.toPath().resolve("thm-tunnelminer-resume-cache.txt");
        }
        return Path.of("thm-tunnelminer-resume-cache.txt").toAbsolutePath();
    }

    private void resetWatchdogTelemetry() {
        watchdogStealthGate = "unset";
        watchdogStealthGateDetails = "";
        watchdogProbeSummary = "";
        watchdogMineSummary = "";
        watchdogRestoreSummary = "";
        watchdogDetourSummary = "";
        watchdogTraverseFail = "";
        watchdogAStarSummary = "";
        watchdogSamePosTicks = 0;
        watchdogNoProgressTicks = 0;
        watchdogLastX = Integer.MIN_VALUE;
        watchdogLastZ = Integer.MIN_VALUE;
        watchdogLastBlocksLeft = Integer.MIN_VALUE;
        watchdogLastBlocksMined = Integer.MIN_VALUE;
        watchdogLastTelemetryAge = Integer.MIN_VALUE;
        watchdogPendingCacheAge = Integer.MIN_VALUE;
        watchdogCachedPendingFar = -1;
        watchdogCachedPendingNear = -1;
    }

    private void updateWatchdogProgressTelemetry(int blocksLeft) {
        if (mc == null || mc.player == null) {
            watchdogSamePosTicks = 0;
            watchdogNoProgressTicks = 0;
            watchdogLastX = Integer.MIN_VALUE;
            watchdogLastZ = Integer.MIN_VALUE;
            watchdogLastBlocksLeft = Integer.MIN_VALUE;
            watchdogLastBlocksMined = Integer.MIN_VALUE;
            watchdogLastTelemetryAge = Integer.MIN_VALUE;
            return;
        }

        int age = mc.player.age;
        if (watchdogLastTelemetryAge == age) return;
        watchdogLastTelemetryAge = age;

        int px = MathHelper.floor(mc.player.getX());
        int pz = MathHelper.floor(mc.player.getZ());
        if (px == watchdogLastX && pz == watchdogLastZ) watchdogSamePosTicks++;
        else watchdogSamePosTicks = 0;

        if (blocksLeft == watchdogLastBlocksLeft && blocksMined == watchdogLastBlocksMined) watchdogNoProgressTicks++;
        else watchdogNoProgressTicks = 0;

        watchdogLastX = px;
        watchdogLastZ = pz;
        watchdogLastBlocksLeft = blocksLeft;
        watchdogLastBlocksMined = blocksMined;
    }

    private int countPendingStealthRestore(boolean allowNear) {
        if (mc == null || mc.player == null || mc.world == null) return -1;

        boolean strictExactRestore = isStrictStealthExactRestoreEnabled();
        int px = MathHelper.floor(mc.player.getX());
        int pz = MathHelper.floor(mc.player.getZ());
        int count = 0;

        for (Map.Entry<BlockPos, BlockState> entry : stealthCache.entrySet()) {
            BlockPos pos = entry.getKey();
            BlockState original = entry.getValue();
            BlockState current = mc.world.getBlockState(pos);

            if (activeProbePositions.contains(pos)) continue;
            if (isLavaState(original)) continue;
            if (isLavaState(current)) continue;
            if (isRestoreSatisfied(original, current, strictExactRestore)) continue;
            if (!allowNear) {
                if (Math.abs(pos.getX() - px) <= 1 && Math.abs(pos.getZ() - pz) <= 1) continue;
            } else {
                if (pos.getX() == px && pos.getZ() == pz) continue;
            }
            count++;
        }

        return count;
    }

    private String formatPos(BlockPos pos) {
        if (pos == null) return "null";
        return String.format(Locale.ROOT, "(%d,%d,%d)", pos.getX(), pos.getY(), pos.getZ());
    }

    private String formatPathStep(PathStep step) {
        if (step == null) return "null";
        return String.format(Locale.ROOT, "(%d,%d)->(%d,%d),d=(%d,%d)", step.fromX(), step.fromZ(), step.toX(), step.toZ(), step.stepX(), step.stepZ());
    }

    private String formatMineBlock(StealthDoubleMineBlock block) {
        if (block == null) return "null";
        boolean canReadProgress = mc != null && mc.player != null;
        double progress = canReadProgress ? block.progress() : -1.0;
        boolean ready = canReadProgress && block.isReady();
        return String.format(
            Locale.ROOT,
            "%s,dir=%s,packet=%s,progress=%.3f,ready=%s",
            formatPos(block.blockPos),
            block.direction == null ? "null" : block.direction.asString(),
            block.packet,
            progress,
            ready
        );
    }

    private String sanitizeLogField(String value) {
        if (value == null) return "";
        String out = value.replace('\n', ' ').replace('\r', ' ');
        return out.replace('|', '/');
    }

    private boolean isHotPathCalculation(String calculation) {
        if (calculation == null) return false;
        return calculation.equals("traversable")
            || calculation.equals("astar-expand")
            || calculation.equals("astar-neighbor")
            || calculation.equals("greedy-detour-candidate");
    }

    private void watchdogCalc(String calculation, String details) {
        if (!optWatchdogEnabled() || !HARD_WATCHDOG_VERBOSE) return;
        if (!HARD_WATCHDOG_HOTPATH_CALCULATIONS && isHotPathCalculation(calculation)) return;

        try {
            String phaseName = phase == null ? "null" : phase.name();
            int blocksLeft = watchdogBlocksLeft();
            updateWatchdogProgressTelemetry(blocksLeft);
            String extra = (details == null || details.isBlank()) ? "" : " | details=" + sanitizeLogField(details);
            String player = "player=null";
            if (mc != null && mc.player != null) {
                player = String.format(
                    Locale.ROOT,
                    "player=(%.3f,%.3f,%.3f),yaw=%.2f,pitch=%.2f",
                    mc.player.getX(), mc.player.getY(), mc.player.getZ(), mc.player.getYaw(), mc.player.getPitch()
                );
            }

            String line = String.format(
                Locale.ROOT,
                "%s | #%d | event=calc-%s%s | phase=%s,onXAxis=%s,requestedX=%d,requestedZ=%d,goalX=%d,goalZ=%d,goalMode=%s,lockedY=%d,blocksLeft=%d,blocksMined=%d,samePosTicks=%d,noProgressTicks=%d | %s%n",
                LocalDateTime.now().format(WATCHDOG_TS),
                ++watchdogSequence,
                calculation,
                extra,
                phaseName,
                onXAxis,
                requestedDestX,
                requestedDestZ,
                destX,
                destZ,
                goalMode.id,
                tunnelY,
                blocksLeft,
                blocksMined,
                watchdogSamePosTicks,
                watchdogNoProgressTicks,
                player
            );

            enqueueWatchdogLine(line);
        } catch (IOException ignored) {
            // Watchdog logging must never interrupt module behavior.
        }
    }

    private void watchdogTick(String event, long tickStartNs) {
        if (!optWatchdogEnabled() || !HARD_WATCHDOG_LOG_TICKS) return;

        long tickMicros = tickStartNs > 0L ? (System.nanoTime() - tickStartNs) / 1_000L : -1L;
        watchdog(event, "tickUs=" + tickMicros);
    }

    private void watchdog(String event) {
        watchdog(event, "");
    }

    private void watchdog(String event, String details) {
        if (!optWatchdogEnabled()) return;

        try {
            String phaseName = phase == null ? "null" : phase.name();
            int blocksLeft = watchdogBlocksLeft();
            updateWatchdogProgressTelemetry(blocksLeft);
            String extra = (details == null || details.isBlank()) ? "" : " | details=" + sanitizeLogField(details);
            String player = "player=null";
            if (mc != null && mc.player != null) {
                player = String.format(
                    Locale.ROOT,
                    "player=(%.3f,%.3f,%.3f),yaw=%.2f,pitch=%.2f",
                    mc.player.getX(), mc.player.getY(), mc.player.getZ(), mc.player.getYaw(), mc.player.getPitch()
                );
            }
            int pendingRestoreFar = -1;
            int pendingRestoreNear = -1;
            if (optStealthMode()) {
                cachePendingRestoreCountsIfNeeded();
                pendingRestoreFar = watchdogCachedPendingFar;
                pendingRestoreNear = watchdogCachedPendingNear;
            }

            String line = String.format(
                Locale.ROOT,
                "%s | #%d | event=%s%s | phase=%s,onXAxis=%s,requestedX=%d,requestedZ=%d,goalX=%d,goalZ=%d,goalMode=%s,lockedY=%d,totalBlocks=%d,blocksLeft=%d,blocksMined=%d,fillLog=%d,renderBreak=%d,placeTimer=%d,invTimer=%d,waitTicks=%d,pickSlot=%d,restockEC=%s,pathMode=%s,settingsSource=%s,apiMode=%s,stealthMode=%s,effectiveRenderMining=%s,effectiveRenderPlacing=%s,speedSnapshotOwned=%s,speedOverrideActive=%s,stealthCache=%d,activeProbe=%d,activeMine=%d,pendingRestoreFar=%d,pendingRestoreNear=%d,samePosTicks=%d,noProgressTicks=%d,detourVisits=%d | %s",
                LocalDateTime.now().format(WATCHDOG_TS),
                ++watchdogSequence,
                event,
                extra,
                phaseName,
                onXAxis,
                requestedDestX,
                requestedDestZ,
                destX,
                destZ,
                goalMode.id,
                tunnelY,
                totalBlocks,
                blocksLeft,
                blocksMined,
                fillLog.size(),
                renderBreakPositions.size(),
                placeTimer,
                invTimer,
                waitTicks,
                pickSlot,
                restockEC,
                hardPathMode(),
                settingsSource.id,
                apiModeActive,
                optStealthMode(),
                optRenderMining(),
                optRenderPlacing(),
                speedSnapshotOwned,
                speedOverrideActive,
                stealthCache.size(),
                activeProbePositions.size(),
                activeMineTargets.size(),
                pendingRestoreFar,
                pendingRestoreNear,
                watchdogSamePosTicks,
                watchdogNoProgressTicks,
                detourVisitCounts.size(),
                player
            );
            if (HARD_WATCHDOG_VERBOSE) {
                line += String.format(
                    Locale.ROOT,
                    " | goalReason=%s,speedReason=%s,gate=%s,gateDetails=%s,probe=%s,mine=%s,restore=%s,detour=%s,traverseFail=%s,astar=%s,normalMine=%s,packetMine=%s",
                    sanitizeLogField(goalReason),
                    sanitizeLogField(speedSnapshotLastReason),
                    sanitizeLogField(watchdogStealthGate),
                    sanitizeLogField(watchdogStealthGateDetails),
                    sanitizeLogField(watchdogProbeSummary),
                    sanitizeLogField(watchdogMineSummary),
                    sanitizeLogField(watchdogRestoreSummary),
                    sanitizeLogField(watchdogDetourSummary),
                    sanitizeLogField(watchdogTraverseFail),
                    sanitizeLogField(watchdogAStarSummary),
                    sanitizeLogField(formatMineBlock(normalMining)),
                    sanitizeLogField(formatMineBlock(packetMining))
                );
            }
            line += System.lineSeparator();
            enqueueWatchdogLine(line);
        } catch (IOException ignored) {
            // Watchdog logging must never interrupt module behavior.
        }
    }

    private void cachePendingRestoreCountsIfNeeded() {
        if (mc == null || mc.player == null || mc.world == null) {
            watchdogPendingCacheAge = Integer.MIN_VALUE;
            watchdogCachedPendingFar = -1;
            watchdogCachedPendingNear = -1;
            return;
        }

        int age = mc.player.age;
        if (watchdogPendingCacheAge == age) return;
        watchdogPendingCacheAge = age;
        watchdogCachedPendingFar = countPendingStealthRestore(false);
        watchdogCachedPendingNear = countPendingStealthRestore(true);
    }

    private void enqueueWatchdogLine(String line) throws IOException {
        if (line == null || line.isEmpty()) return;

        watchdogWriteBuffer.append(line);
        watchdogWriteBufferLines++;

        if (watchdogWriteBufferLines >= WATCHDOG_BUFFER_FLUSH_LINES || watchdogWriteBuffer.length() >= WATCHDOG_BUFFER_FLUSH_CHARS) {
            flushWatchdogBufferInternal();
        }
    }

    private void flushWatchdogBuffer() {
        try {
            flushWatchdogBufferInternal();
        } catch (IOException ignored) {
            // Watchdog logging must never interrupt module behavior.
        }
    }

    private void flushWatchdogBufferInternal() throws IOException {
        if (watchdogWriteBufferLines <= 0 || watchdogWriteBuffer.length() == 0) return;

        Path logPath = resolveWatchdogLogPath();
        Path parent = logPath.getParent();
        if (parent != null) Files.createDirectories(parent);
        rotateWatchdogLogIfNeeded(logPath);

        Files.writeString(
            logPath,
            watchdogWriteBuffer.toString(),
            StandardOpenOption.CREATE,
            StandardOpenOption.WRITE,
            StandardOpenOption.APPEND
        );
        watchdogWriteBuffer.setLength(0);
        watchdogWriteBufferLines = 0;
    }

    private void rotateWatchdogLogIfNeeded(Path logPath) throws IOException {
        int maxMb = HARD_WATCHDOG_MAX_FILE_MB;
        if (maxMb <= 0) return;

        long maxBytes = (long) maxMb * 1024L * 1024L;
        long incomingBytes = watchdogWriteBuffer.length();
        long currentSize = Files.exists(logPath) ? Files.size(logPath) : 0L;
        if (currentSize + incomingBytes <= maxBytes) return;
        if (Files.exists(logPath)) Files.delete(logPath);
    }

    private int watchdogBlocksLeft() {
        if (mc == null || mc.player == null) return -1;
        return blocksLeftFrom(MathHelper.floor(mc.player.getX()), MathHelper.floor(mc.player.getZ()));
    }

    private Path resolveWatchdogLogPath() {
        if (mc != null && mc.runDirectory != null) {
            return mc.runDirectory.toPath().resolve("thm-tunnelminer-watchdog.log");
        }
        return Path.of("thm-tunnelminer-watchdog.log").toAbsolutePath();
    }
}
