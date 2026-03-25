SIMULATION ONLY

This document is a scratch design/review artifact.
It copies current code from the live .java files and edits that copied text to simulate proposed changes.
The real .java files are not being modified by this document.
No .java file is to be created, edited, overwritten, or partially updated as part of this simulation.
All review feedback should target the simulated text here first.

ISOLATED REVIEW FILE

This file is intentionally standalone.
It does not inherit, extend, or depend on the reconnect simulation document.
Only HighwayBuilder statistics finalization and screenshot-proof behavior are in scope here.

## Goal And Scope

This standalone simulation models a screenshot-guaranteed statistics finalization flow for HighwayBuilder.

Implementation guardrail:
This review artifact must not be used to directly modify working Java files until a separate implementation step is explicitly approved.

In scope:
- durable screenshot-required state
- durable screenshot completion state
- durable finalization phase
- deterministic screenshot filename assignment
- reprint-on-recovery versus continue-versus-retire logic
- at-most-once webhook/API send behavior using the existing durable commit fields

Out of scope:
- reconnect logic
- THMHwyMonitor behavior
- Timer reconnect ownership
- unrelated cache or storage redesign outside screenshot-guaranteed stats finalization

## Source File Copied From

Source file copied for review only:
- `src/main/java/xyz/thm/addon/modules/HighwayBuilderTHM.java`

Explicit statements:
- the source text below was copied from the live file for review purposes only
- the copied text inside this markdown file is not a second source file
- all edits shown in the simulated full source are editorial simulation only
- the live Java source remains the source of truth until a separate implementation step is approved
- no `.java` file is created, edited, overwritten, or partially updated by this document

## Current Full Source

```java/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package xyz.thm.addon.modules;

import meteordevelopment.meteorclient.MeteorClient;
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
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
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
import meteordevelopment.meteorclient.utils.render.RenderUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.meteorclient.utils.world.Dir;
import meteordevelopment.meteorclient.utils.world.TickRate;
import meteordevelopment.orbit.EventHandler;
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
import net.minecraft.client.option.Perspective;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.*;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.s2c.play.InventoryS2CPacket;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.EmptyBlockView;
import net.minecraft.world.RaycastContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Range;
import org.joml.Vector3d;
import xyz.thm.addon.THMAddon;
import xyz.thm.addon.system.THMSystem;
import xyz.thm.addon.utils.ServerStatusHandler;
import xyz.thm.addon.utils.ServerStatusHandler.ServerState;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Supplier;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import static xyz.thm.addon.utils.THMUtils.*;
import static xyz.thm.addon.utils.password.*;

@SuppressWarnings("ConstantConditions")
public class HighwayBuilderTHM extends Module {
    private static final String RESTART_DETECTED_MARKER = "server restart detected";
    private static final String STATS_ARTIFACT_MAGIC = "HB_STATS_ARTIFACT_V1";
    private static final int STATS_ARTIFACT_VERSION = 1;
    private static final long STATS_CHECKPOINT_INTERVAL_MS = 10 * 60 * 1000L;
    private static final int STATS_SCREENSHOT_DELAY_MS = 250;
    private static final long STATS_MEMORY_RETRY_RECHECK_MS = 5_000L;
    private static final String STATS_CANONICAL_FILE_NAME = "highwaybuildersettings";
    private static final String STATS_FINALIZATION_FILE_NAME = "highwaybuildersettings.finalization";
    private static final String STATS_SHADOW_FILE_NAME = "highwaybuildersettings.shadow";
    private static final int STATS_GCM_TAG_BITS = 128;
    private static final int STATS_GCM_NONCE_BYTES = 12;
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

    public enum KitbotRestockKit {
        Echest(KitbotFrontend.KitName.Echest),
        Pickaxe(KitbotFrontend.KitName.Pickaxe),
        Highway(KitbotFrontend.KitName.Highway);

        public final KitbotFrontend.KitName kitName;

        KitbotRestockKit(KitbotFrontend.KitName kitName) {
            this.kitName = kitName;
        }

        @Override
        public String toString() {
            return kitName.toString();
        }
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgDigging = settings.createGroup("Digging");
    private final SettingGroup sgPaving = settings.createGroup("Paving");
    private final SettingGroup sgInventory = settings.createGroup("Inventory");
    private final SettingGroup sgStatistics = settings.createGroup("Logging");
    private final SettingGroup sgNotifies = settings.createGroup("Notifies");
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

    private final Setting<Rotation> rotation = sgGeneral.add(new EnumSetting.Builder<Rotation>()
        .name("rotation")
        .description("Mode of rotation.")
        .defaultValue(Rotation.None)
        .build()
    );

    private final Setting<Boolean> kitbotRestock = sgInventory.add(new BoolSetting.Builder()
        .name("kitbot-restock")
        .description("Order a kit from KitBot1 when out of building blocks.")
        .defaultValue(false)
        .build()
    );

    public final Setting<KitbotRestockKit> kitbotRestockKit = sgInventory.add(new EnumSetting.Builder<KitbotRestockKit>()
        .name("kitbot-restock-kit")
        .description("Kit to order when kitbot restock triggers.")
        .defaultValue(KitbotRestockKit.Highway)
        .visible(kitbotRestock::get)
        .build()
    );

    private final Setting<Boolean> disconnectOnToggle = sgGeneral.add(new BoolSetting.Builder()
        .name("disconnect-on-toggle")
        .description("Automatically disconnects when the module is turned off, for example for not having enough blocks.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> pauseOnLag = sgGeneral.add(new BoolSetting.Builder()
        .name("pause-on-lag")
        .description("Pauses the current process while the server stops responding.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> resumeTPS = sgGeneral.add(new IntSetting.Builder()
        .name("resume-tps")
        .description("Server tick speed at which to resume building.")
        .defaultValue(16)
        .range(1, 19)
        .sliderRange(1, 19)
        .visible(pauseOnLag::get)
        .build()
    );

    private final Setting<Boolean> destroyCrystalTraps = sgGeneral.add(new BoolSetting.Builder()
        .name("destroy-crystal-traps")
        .description("Use a bow to defuse crystal traps safely from a distance. An infinity bow is recommended.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> manageThmHwyMonitor = sgGeneral.add(new BoolSetting.Builder()
        .name("manage-thm-hwy-monitor")
        .description("Manages HighwayBuilder to reduce highway-building drift and auto-aligns the user on the current highway when HighwayBuilder is on.")
        .defaultValue(false)
        .onChanged(value -> {
            if (!isActive()) return;
            if (value) syncThmHwyMonitorOnActivate();
            else disableThmHwyMonitorIfActive();
        })
        .visible(() -> isBaritoneInstalled())
        .build()
    );


    // Digging

    private final Setting<Boolean> doubleMine = sgDigging.add(new BoolSetting.Builder()
        .name("double-mine")
        .description("Whether to double mine blocks when applicable (normal mine and packet mine simultaneously).")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> fastBreak = sgDigging.add(new BoolSetting.Builder()
        .name("fast-break")
        .description("Whether to finish breaking blocks faster than normal while double mining.")
        .defaultValue(true)
        .visible(doubleMine::get)
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
        .description("How many pickaxes to ensure are saved. Hitting this number in your inventory will trigger a restock or the module toggling off.")
        .defaultValue(1)
        .range(1, 36)
        .sliderRange(1, 36)
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

    private final Setting<Integer> blocksPerTick = sgDigging.add(new IntSetting.Builder()
        .name("blocks-per-tick")
        .description("The maximum amount of blocks that can be mined in a tick. Only applies to blocks instantly breakable.")
        .defaultValue(7)
        .range(1, 100)
        .sliderRange(1, 25)
        .build()
    );

    private final Setting<Boolean> ignoreSigns = sgDigging.add(new BoolSetting.Builder()
        .name("ignore-signs")
        .description("Ignore breaking signs = preserving history (based).")
        .defaultValue(true)
        .onChanged(value -> updateSignBreakRegex())
        .build()
    );

    private final Setting<Boolean> breakAdvertisementSigns = sgDigging.add(new BoolSetting.Builder()
        .name("break-advertisement-signs")
        .description("Only break signs that look like advertisements/invites.")
        .defaultValue(false)
        .onChanged(value -> updateSignBreakRegex())
        .visible(() -> !ignoreSigns.get())
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
        .defaultValue(4.5)
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

    private final Setting<Boolean> checkBehind = sgGeneral.add(new BoolSetting.Builder()
        .name("check-behind")
        .description("Checks and repairs missing highway floor and railings behind the player.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Integer> placementsPerTick = sgPaving.add(new IntSetting.Builder()
        .name("placements-per-tick")
        .description("The maximum amount of blocks that can be placed in a tick.")
        .defaultValue(1)
        .min(1)
        .build()
    );

    // Inventory

    private boolean clampingFoodTypes;

    private final Setting<List<Item>> trashItems = sgInventory.add(new ItemListSetting.Builder()
        .name("trash-items")
        .description("Items that are considered trash and can be thrown out.")
        .defaultValue(
            Items.NETHERRACK, Items.QUARTZ, Items.GOLD_NUGGET, Items.GOLDEN_SWORD, Items.GLOWSTONE_DUST,
            Items.GLOWSTONE, Items.BLACKSTONE, Items.BASALT, Items.GHAST_TEAR, Items.SOUL_SAND, Items.SOUL_SOIL,
            Items.ROTTEN_FLESH, Items.MAGMA_BLOCK
        )
        .build()
    );

    private final Setting<Boolean> foodRestock = sgInventory.add(new BoolSetting.Builder()
        .name("food-restock")
        .description("Restocks one configured food stack when your valid food count drops to the saved amount.")
        .defaultValue(false)
        .build()
    );

    private final Setting<List<Item>> foodTypes = sgInventory.add(new ItemListSetting.Builder()
        .name("food-types")
        .description("Which food items count as restock food. Maximum 2 food types.")
        .defaultValue()
        .visible(foodRestock::get)
        .onChanged(this::handleFoodTypesChanged)
        .build()
    );

    private final Setting<Integer> saveFood = sgInventory.add(new IntSetting.Builder()
        .name("save-food")
        .description("Restock food when your total configured food count is at or below this value. Do not set higher than half a stack of your chosen food.")
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
        .description("Whether you should eject useless shulkers. Warning - will throw out any shulkers that don't contain blocks to place, pickaxes, or food. Be careful with your kits.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> searchEnderChest = sgInventory.add(new BoolSetting.Builder()
        .name("search-ender-chest")
        .description("Searches your ender chest to find items to use. Be careful with this one, especially if you let it search through shulkers.")
        .defaultValue(false)
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

    private final Setting<Integer> minEmpty = sgInventory.add(new IntSetting.Builder()
        .name("minimum-empty-slots")
        .description("The minimum amount of empty slots you want left after mining obsidian.")
        .defaultValue(3)
        .sliderRange(2, 9)
        .min(2)
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
        .description("What blockade type to use (the structure placed when mining echests). FullRoof adds a roof above the player and container.")
        .defaultValue(BlockadeType.Full)
        .visible(mineEnderChests::get)
        .build()
    );

    public final Setting<Integer> saveEchests = sgInventory.add(new IntSetting.Builder()
        .name("save-ender-chests")
        .description("How many ender chests to ensure are saved. Hitting this number in your inventory will trigger a restock or the module toggling off.")
        .defaultValue(2)
        .range(0, 64)
        .sliderRange(0, 64)
        .visible(mineEnderChests::get)
        .build()
    );

    private final Setting<Boolean> rebreakEchests = sgInventory.add(new BoolSetting.Builder()
        .name("instantly-rebreak-echests")
        .description("Whether or not to use the instant rebreak exploit to break echests.")
        .defaultValue(true)
        .visible(mineEnderChests::get)
        .build()
    );

    private final Setting<Integer> rebreakTimer = sgInventory.add(new IntSetting.Builder()
        .name("rebreak-delay")
        .description("Delay between rebreak attempts.")
        .defaultValue(0)
        .sliderMax(20)
        .visible(() -> mineEnderChests.get() && rebreakEchests.get())
        .build()
    );

    private final Setting<Boolean> silentRebreakSwap = sgInventory.add(new BoolSetting.Builder()
        .name("silent-rebreak-swap")
        .description("Silently swaps to the best pick for instant rebreak packets, then restores your selected slot.")
        .defaultValue(true)
        .visible(() -> mineEnderChests.get() && rebreakEchests.get())
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
        .defaultValue(true)
        .visible(printStatistics::get)
        .build()
    );

    private final Setting<Boolean> restockDebugLog = sgStatistics.add(new BoolSetting.Builder()
        .name("restock-debug-log")
        .description("Prints detailed blockade and restock diagnostics, including placement probes and state transitions.")
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
    private final Setting<String> decryptkey = sgStatistics.add(new StringSetting.Builder()
        .name("webhook-key")
        .description("Optional encryption/decryption key. Only required if the " +
            "webhook URL is encrypted. Ignored for normal (plain) webhook URLs.")
        .defaultValue("MySecureKeyHere123")
        .visible(() -> printStatistics.get() && sendStatisticsWebhhok.get())
        .build()
    );
    private final Setting<String> encryptedWebhook = sgStatistics.add(new StringSetting.Builder()
        .name("encrypted-webhook")
        .description("Webhook URL used to receive statistics. Can be either encrypted or plain text." +
            " Encrypted webhooks will be decrypted using the provided key.")
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
        .description("Switches to third person while Highway Builder is active, then restores your old perspective.")
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

    private Input prevInput;
    private CustomPlayerInput input;

    private State state, lastState;
    private IBlockPosProvider blockPosProvider;

    public Vec3d start;
    public int blocksBroken, blocksPlaced;
    private final MBlockPos lastBreakingPos = new MBlockPos();
    private boolean displayInfo, sentLagMessage;
    private boolean suspended = true, inventory = true;
    private int placeTimer, breakTimer, count, syncId, statusLogTimer;
    private final RestockTask restockTask = new RestockTask(this);
    private int invalidRestockRecoveryRetries;
    private boolean invalidRestockRecoveryPending;
    private boolean kitbotTpHandled;
    private boolean kitbotOrderInFlight;
    private int kitbotOrderBaselineShulkerCount;
    private int kitbotOrderExpectedShulkerGain;
    private int kitbotOrderSentAtAge;
    private int kitbotOrderRetryCount;
    private CenterSpeedSnapshot centerSpeedSnapshot;
    private boolean centerSpeedSnapshotOwned;
    private boolean centerSpeedOverrideActive;
    private boolean centerSpeedMonitorRecoveryOwned;
    private boolean centerSpeedRestorePending;
    private int centerSpeedRestoreRetryTicks;
    private String centerSpeedLastReason = "";
    private ReconnectBaselineLease reconnectBaselineLease;
    private boolean reconnectBaselineRestoreInProgress;
    private boolean reconnectFailureDeactivateArmed;
    private ReconnectResumeContext reconnectResumeContext;
    private Field timerOverrideField;
    private boolean timerOverrideFieldInitialized;
    private boolean timerOverrideReflectionFailureLogged;
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
    private volatile boolean statsProofScreenshotScheduled;
    private volatile boolean statsDisconnectScreenshotScheduled;
    private String lastPrintedStatsSessionId;
    private boolean previousPauseOnLostFocus;
    private boolean pauseOnLostFocusChanged;
    private boolean executionPausedByServerState;
    private boolean offMainEpisodeCheckpointed;
    private ServerState lastCommittedServerState = ServerState.UNKNOWN;
    private Perspective previousPerspective;
    private boolean perspectiveChanged;
    private final ArrayList<EndCrystalEntity> ignoreCrystals = new ArrayList<>();
    public boolean drawingBow;
    public DoubleMineBlock normalMining, packetMining;
    private final MBlockPos posRender2 = new MBlockPos();
    private final MBlockPos posRender3 = new MBlockPos();
    private List<Pattern> signBreakPatterns = Collections.emptyList();
    private static final String KITBOT_NAME = "KitBot1";
    private static final double CENTER_SPEED_OVERRIDE = 0.6;
    private static final int CENTER_SPEED_RESTORE_RETRY_WINDOW_TICKS = 60;
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

    private enum ReconnectBaselineLeaseState {
        CAPTURED,
        INVALIDATED,
        CONSUMED
    }

    private record ReconnectBaselinePayload(
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
        String finalizationReason
    ) {}

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

    // AES-256 encryption with SHA-256 key derivation
    private String decryptWebhook(String encryptedWebhook, String password) {
        try {
            // Derive a 256-bit (32 byte) key from the password using SHA-256
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] keyBytes = digest.digest(password.getBytes(StandardCharsets.UTF_8));

            // Add proper Base64 padding for encrypted webhook if needed
            String padded = encryptedWebhook;
            int padding = padded.length() % 4;
            if (padding > 0) {
                padded += "=".repeat(4 - padding);
            }

            byte[] encryptedBytes = Base64.getDecoder().decode(padded);

            // Create AES-256 cipher
            SecretKeySpec secretKey = new SecretKeySpec(keyBytes, 0, keyBytes.length, "AES");
            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.DECRYPT_MODE, secretKey);

            byte[] decrypted = cipher.doFinal(encryptedBytes);
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            // If decryption fails, treat the webhook as unencrypted
            THMAddon.LOG.warn("Failed to decrypt webhook, treating as unencrypted: " + e.getMessage());
            return encryptedWebhook;
        }
    }

    // AES-256 encryption with SHA-256 key derivation
    private String decryptAPI(String encryptedapi, String password) {
        try {
            // Derive a 256-bit (32 byte) key from the password using SHA-256
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] keyBytes = digest.digest(password.getBytes(StandardCharsets.UTF_8));

            // Add proper Base64 padding for encrypted data if needed
            String padded = encryptedapi;
            int padding = padded.length() % 4;
            if (padding > 0) {
                padded += "=".repeat(4 - padding);
            }

            byte[] encryptedBytes = Base64.getDecoder().decode(padded);

            // Create AES-256 cipher
            SecretKeySpec secretKey = new SecretKeySpec(keyBytes, 0, keyBytes.length, "AES");
            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.DECRYPT_MODE, secretKey);

            byte[] decrypted = cipher.doFinal(encryptedBytes);
            String result = new String(decrypted, StandardCharsets.UTF_8);

            // Validate result looks like a URL
            if (!result.startsWith("http://") && !result.startsWith("https://")) {
                THMAddon.LOG.warn("Decrypted API URL invalid - wrong password or corrupted data");
                return null;
            }

            return result;
        } catch (Exception e) {
            THMAddon.LOG.warn("Failed to decrypt API: " + e.getMessage());
            return null;
        }
    }

    private void sendToWebhook(String webhookUrl, String message) {
        new Thread(() -> {
            try {
                @SuppressWarnings("deprecation") URL url = new URL(webhookUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);

                // Create JSON payload for Discord webhook
                String json = "{\"content\": \"" + message.replace("\"", "\\\"") + "\"}";

                try (OutputStream os = conn.getOutputStream()) {
                    byte[] input = json.getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }

                int responseCode = conn.getResponseCode();
                if (responseCode == 204 || responseCode == 200) {
                    info("Successfully sent statistics to webhook!");
                } else {
                    THMAddon.LOG.warn("Webhook response code: " + responseCode);
                    warning("Failed to send to Webhook");
                }

                conn.disconnect();
            } catch (Exception e) {
                THMAddon.LOG.warn("Failed to send to webhook: " + e.getMessage());
            }
        }).start();
    }
    private void sendToAPI(String message, String password, String EncryptedAPI, String logType) {
        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                // Decrypt the API URL first
                String api = decryptAPI(EncryptedAPI, password);

                // Check if decryption succeeded
                if (api == null) {
                    THMAddon.LOG.warn("Failed to decrypt API URL - check your encryption key");
                    return;
                }

                @SuppressWarnings("deprecation") URL url = new URL(api);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);

                String json = "{\"content\": \"" + message.replace("\"", "\\\"") + "\"}";

                try (OutputStream os = conn.getOutputStream()) {
                    byte[] input = json.getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                    os.flush();
                }

                int responseCode = conn.getResponseCode();

                try (InputStream is = responseCode >= 400 ? conn.getErrorStream() : conn.getInputStream()) {
                    if (is != null) {
                        is.readAllBytes();
                    }
                }

                if (responseCode == 204 || responseCode == 200) {
                    info("Successfully sent %s to API!", logType);
                } else {
                    THMAddon.LOG.warn("API response code: " + responseCode);
                    warning("Failed to send to API");
                }
            } catch (Exception e) {
                warning("Failed to send to API");
            } finally {
                if (conn != null) conn.disconnect();
            }
        }).start();
    }

    private void sendStatusLog() {
        if (!statuslog.get() || mc.player == null || dir == null) return;

        if (isNot6B6T()) {
            warning("Status not sent. You are not on 6B6T");
            return;
        }
        if (!isOnMainHighway()) {
            warning("Status wont get send. You are not on a main highway");
            return;
        }
        if (THMSystem.get() == null || Objects.equals(THMSystem.get().getHash(), "SetYourHash") || Objects.equals(THMSystem.get().getHash(), "")) {
            warning("Status not sent. No Hash set.");
            return;
        }

        String playerName = mc.player.getName().getLiteralString();
        String axis = dir.toString();

        String statusMessage = String.format("%s:%s:%s:%d:%d:%s",
            THMSystem.get().getHash(),
            playerName,
            axis,
            blocksBroken,
            blocksPlaced,
            generateTimestamp()
        );

        sendToAPI(statusMessage, getPassword(), getAPIStatus(), "status");
    }

    private ServerState getCommittedServerState() {
        return ServerStatusHandler.getInstance().getCommittedState();
    }

    private boolean isExecutionAllowedOnCurrentServer(ServerState committedState) {
        return committedState == ServerState.MAIN_SERVER;
    }

    private void trackServerExecutionState(ServerState committedState) {
        if (committedState == lastCommittedServerState) return;

        ServerState previousState = lastCommittedServerState;
        lastCommittedServerState = committedState;

        if (committedState == ServerState.MAIN_SERVER) {
            offMainEpisodeCheckpointed = false;
            return;
        }

        if (previousState == ServerState.MAIN_SERVER && !offMainEpisodeCheckpointed && hasActiveInMemoryStatsSession() && !statsSessionTerminalOrFinalizing) {
            persistCurrentStatsSession(StatsSessionState.OPEN, true, 0L, "server-state-left-main");
            offMainEpisodeCheckpointed = true;
        }
    }

    private void pauseExecutionForServerState(ServerState committedState) {
        if (input != null) input.stop();
        executionPausedByServerState = true;
    }

    @Override
    public void onActivate() {
        if (mc.player == null || mc.world == null) return;
        if (!Utils.canUpdate()) return;
        ReconnectResumeContext reconnectResume = reconnectResumeContext;
        reconnectResumeContext = null;
        boolean reconnectActivation = reconnectResume != null;
        clearKitbotOrderTracking("module-activate");

        if (centerSpeedMonitorRecoveryOwned && !resumeStatsSessionOnNextActivate) {
            restockDebug("Center/Speed stale monitor recovery baseline cleared on activate (lastReason=%s).", centerSpeedLastReason);
            clearCenterSpeedOwnership("activate-stale-monitor-baseline");
        }

        if (!suppressThmHwyMonitorSync) syncThmHwyMonitorOnActivate();
        loadStatsCacheFromDisk();

        previousPauseOnLostFocus = mc.options.pauseOnLostFocus;
        pauseOnLostFocusChanged = previousPauseOnLostFocus;
        if (pauseOnLostFocusChanged) togglePauseOnLostFocus(false);

        updateVariables();
        updateSignBreakRegex();
        dir = reconnectActivation ? reconnectResume.direction() : HorizontalDirection.get(mc.player.getYaw());
        leftDir = dir.rotateLeftSkipOne();
        rightDir = leftDir.opposite();

        blockPosProvider = dir.diagonal ? new DiagonalBlockPosProvider() : new StraightBlockPosProvider();
        state = State.Forward;
        if (!reconnectActivation) setState(State.Center);
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
        else startFreshStatsSession();

        resumeStatsSessionOnNextActivate = false;
        monitorPauseDeactivateArmed = false;
        sentLagMessage = false;
        suspended = reconnectActivation;
        statusLogTimer = 6000;

        restockTask.complete();

        if (blocksPerTick.get() > 1 && rotation.get().mine)
            warning("With rotations enabled, you can break at most 1 block per tick.");
        if (placementsPerTick.get() > 1 && rotation.get().place)
            warning("With rotations enabled, you can place at most 1 block per tick.");
        //all modules that may cause error now print errors/warnings
        if (Modules.get().get(InstantRebreak.class).isActive())
            warning("It's recommended to disable the Instant Rebreak module and instead use the 'instantly-rebreak-echests' setting to avoid errors.");
        if (Modules.get().get(SpeedMine.class).isActive())
            warning("It's recommended to disable the Speedmine module and instead use the 'fast-break' setting to avoid errors.");
        if (Modules.get().get(Speed.class).isActive() && dir.diagonal)
            warning("It's recommended to disable the Speed module to avoid misalignment on diagonals.");
        if (Modules.get().get(Timer.class).isActive() && dir.diagonal)
            warning("It's recommended to disable the Timer module to avoid misalignment on diagonals.");
        //it could be tested to print different warnings depending on the amount of blocks being broken per tick but that would need much testing and wouldn't be reliable
        if (Modules.get().get(NoGhostBlocks.class).isActive())
            warning("It's recommended to disable the NoGhostBlocks module to avoid packet kicks and wrong statistics.");
        if (!Modules.get().get(Velocity.class).isActive()) {
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
        if (!Modules.get().get(AntiDrop.class).isActive() && antidrop.get()) { Modules.get().get(AntiDrop.class).toggle();}

        THMSystem thmSystem = THMSystem.get();
        if (thmSystem != null) {
            int playerY = (int) mc.player.getY();

            if (thmSystem.mode.get() == THMSystem.Mode.HighwayBuilding) {
                if (playerY != 120) {
                    warning("You are not on Y Level 120!!");
                    toggle();
                }
            }
            if (thmSystem.mode.get() == THMSystem.Mode.HighwayDigging) {
                if (playerY != 119) {
                    warning("You are not on Y Level 119!!");
                    toggle();
                }
            }
        }



    }
    @Override
    public void onDeactivate() {
        boolean isMonitorPauseDeactivate = monitorPauseDeactivateArmed;
        boolean isReconnectFailureDeactivate = reconnectFailureDeactivateArmed;
        monitorPauseDeactivateArmed = false;
        reconnectFailureDeactivateArmed = false;
        clearKitbotOrderTracking(isMonitorPauseDeactivate ? "monitor-pause-deactivate" : "module-deactivate");

        if (!suppressThmHwyMonitorSync) syncThmHwyMonitorOnDeactivate();

        Timer timer = Modules.get().get(Timer.class);
        if (!isMonitorPauseDeactivate && !isReconnectFailureDeactivate) {
            if (timer != null) timer.setOverride(Timer.OFF);
            invalidateReconnectBaseline("non-monitor-deactivate");
            restoreCenterSpeedIfOwned("module-deactivate");
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

        if (pauseOnLostFocusChanged) {
            togglePauseOnLostFocus(previousPauseOnLostFocus);
            pauseOnLostFocusChanged = false;
        }

        if (mc.player == null || mc.world == null || !Utils.canUpdate()) {
            if (hasActiveInMemoryStatsSession()) {
                if (isMonitorPauseDeactivate) {
                    persistCurrentStatsSession(StatsSessionState.OPEN, true, 0L, "monitor-pause-deactivate-no-world");
                } else {
                    StatsArtifactSnapshot finalizationRecord = persistFinalizationRecord(createFinalizationRecord("deactivate-pending-print-no-world"), "deactivate-pending-print-no-world");
                    if (finalizationRecord == null) warning("Unable to persist pending HighwayBuilder statistics safely before deactivate.");
                }
            } else {
                THMAddon.LOG.info("[highway-stats-cache] skipped deactivate persistence because no active in-memory stats session exists.");
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

        if (!hasActiveInMemoryStatsSession()) {
            THMAddon.LOG.info("[highway-stats-cache] skipped deactivate persistence because no active in-memory stats session exists.");
            return;
        }

        if (isMonitorPauseDeactivate) {
            persistCurrentStatsSession(StatsSessionState.OPEN, true, 0L, "monitor-pause-deactivate");
            return;
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

        THMHwyMonitor monitor = Modules.get().get(THMHwyMonitor.class);
        if (monitor == null || monitor.isActive()) return;

        monitor.toggle();
    }

    private void syncThmHwyMonitorOnDeactivate() {
        if (!manageThmHwyMonitor.get()) return;

        disableThmHwyMonitorIfActive();
    }

    private void disableThmHwyMonitorIfActive() {
        THMHwyMonitor monitor = Modules.get().get(THMHwyMonitor.class);
        if (monitor == null || !monitor.isActive()) return;

        monitor.toggle();
    }

    public void disableForMonitorRealignPause() {
        if (!isActive()) return;

        resumeStatsSessionOnNextActivate = true;
        monitorPauseDeactivateArmed = true;
        if (!statsSessionTerminalOrFinalizing) persistCurrentStatsSession(StatsSessionState.OPEN, true, 0L, "monitor-pause-request");
        suppressThmHwyMonitorSync = true;
        try {
            toggle();
        } finally {
            suppressThmHwyMonitorSync = false;
        }
    }

    public HorizontalDirection getWorkingDirection() {
        return dir;
    }

    public boolean resumeFromReconnect(HorizontalDirection lockedDirection, long generation) {
        if (lockedDirection == null || generation <= 0L) return false;
        if (mc.player == null || mc.world == null || !Utils.canUpdate()) return false;
        if (isActive()) return false;

        reconnectResumeContext = new ReconnectResumeContext(lockedDirection, generation);
        suppressThmHwyMonitorSync = true;
        try {
            toggle();
        } finally {
            suppressThmHwyMonitorSync = false;
        }

        if (!isActive()) {
            reconnectResumeContext = null;
            return false;
        }

        if (dir != lockedDirection) {
            disableForReconnectResumeFailure();
            return false;
        }

        if (!consumeReconnectBaseline(generation)) {
            disableForReconnectResumeFailure();
            return false;
        }

        setState(State.Center);
        suspended = false;
        return isActive() && dir == lockedDirection;
    }

    public boolean prepareForMonitorReconnectPause(long generation) {
        if (generation <= 0L) return false;
        if (hasUsableReconnectBaselineLease(generation)) return true;
        if (reconnectBaselineLease != null && reconnectBaselineLease.generation() == generation) return false;
        if (centerSpeedOverrideActive) return false;

        ReconnectBaselinePayload payload = captureReconnectBaselinePayload();
        if (payload == null) return false;

        reconnectBaselineLease = new ReconnectBaselineLease(generation, ReconnectBaselineLeaseState.CAPTURED, payload);
        return true;
    }

    public boolean restoreCenterSpeedBaselineForFailedReconnect(long generation) {
        if (generation <= 0L) return false;
        if (reconnectBaselineLease == null || reconnectBaselineLease.generation() != generation) return false;
        if (reconnectBaselineLease.state() == ReconnectBaselineLeaseState.INVALIDATED) return false;
        return consumeReconnectBaseline(generation);
    }

    public void refreshReconnectBaselineValidity(long activeGeneration) {
        if (reconnectBaselineLease == null || reconnectBaselineRestoreInProgress) return;
        if (reconnectBaselineLease.state() != ReconnectBaselineLeaseState.CAPTURED) return;

        if (activeGeneration <= 0L || reconnectBaselineLease.generation() != activeGeneration) {
            invalidateReconnectBaseline("generation-changed");
            return;
        }

        if (!reconnectBaselineMatchesLiveState(reconnectBaselineLease.payload())) {
            invalidateReconnectBaseline("live-state-mismatch");
        }
    }

    public void disableForReconnectSafetyStop() {
        if (!isActive()) return;
        reconnectFailureDeactivateArmed = true;
        suppressThmHwyMonitorSync = true;
        try {
            toggle();
        } finally {
            suppressThmHwyMonitorSync = false;
        }
    }

    private void disableForReconnectResumeFailure() {
        if (!isActive()) return;
        reconnectFailureDeactivateArmed = true;
        suppressThmHwyMonitorSync = true;
        try {
            toggle();
        } finally {
            suppressThmHwyMonitorSync = false;
        }
    }

    @Override
    public void error(String message, Object... args) {
        super.error(message, args);
        THMHwyMonitor.signalNonRestartHardFailFromHighwayBuilder();
        toggle();

        if (disconnectOnToggle.get()) {
            disconnect(message, args);
        }
    }

    private void errorEarly(String message, Object... args) {
        super.error(message, args);
        THMHwyMonitor.signalNonRestartHardFailFromHighwayBuilder();

        displayInfo = false;
        toggle();
    }

    private void errorRestart(String message, Object... args) {
        super.error(message, args);
        THMHwyMonitor.signalRestartHardFailFromHighwayBuilder();
        displayInfo = false;
        toggle();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        maybeCheckpointStatsSession();

        ServerState committedState = getCommittedServerState();
        trackServerExecutionState(committedState);
        if (!isExecutionAllowedOnCurrentServer(committedState)) {
            pauseExecutionForServerState(committedState);
            return;
        }

        executionPausedByServerState = false;
        tickDeferredCenterSpeedRestore();

        if (statuslog.get()) {
            statusLogTimer++;
            if (statusLogTimer >= 6000) { // 5 minutes
                sendStatusLog();
                statusLogTimer = 0;
            }
        }

        if (dir == null) {
            onActivate();
            return;
        }

        if (suspended) {
            if (inventory && Utils.canUpdate()) {
                updateVariables();
                suspended = false;
            }
            else return;
        }

        if (width.get() < 3 && dir.diagonal) {
            errorEarly("Diagonal highways less than 3 blocks wide are not supported, please change the width setting.");
            return;
        }

        if (
            (Modules.get().get(AutoEat.class) != null && Modules.get().get(AutoEat.class).eating)
                || (Modules.get().get(AutoGap.class) != null && Modules.get().get(AutoGap.class).isEating())
                || (Modules.get().get(KillAura.class) != null && Modules.get().get(KillAura.class).attacking)
                || (Modules.get().get(OffhandManager.class) != null && Modules.get().get(OffhandManager.class).isEating())
        ) {
            input.stop();
            return;
        }
        if (pauseOnLag.get() && TickRate.INSTANCE.getTimeSinceLastTick() > 1.5f) {
            if (!sentLagMessage) {
                error("Server isn't responding, pausing.");
                input.stop();
                sentLagMessage = true;
                return;
            }

            if (sentLagMessage) {
                if (TickRate.INSTANCE.getTickRate() > resumeTPS.get()) {
                    sentLagMessage = false;
                    return;
                }
            }
        }

        count = 0;

        if (mc.player.getY() < start.y - 0.5) setState(State.ReLevel); // don't let the current state keep ticking, switch to re-levelling straight away
        tickDoubleMine();
        maybeQueueFoodRestock();
        state.tick(this);

        if (breakTimer > 0) breakTimer--;
        if (placeTimer > 0) placeTimer--;
    }

    @EventHandler
    private void onPacket(PacketEvent.Receive event) {
        if (event.packet instanceof InventoryS2CPacket p) {
            if (p.syncId() == 0 && suspended)
                inventory = true;
            else
                this.syncId = p.syncId();
        }
    }


    @EventHandler
    private void onMessageReceive(ReceiveMessageEvent event) {
        if (!isExecutionAllowedOnCurrentServer(getCommittedServerState())) return;
        if (state != State.KitbotOrder || kitbotTpHandled) return;

        String msg = event.getMessage().getString();
        if (msg.contains(KITBOT_NAME + " wants to teleport to you")) {
            ChatUtils.sendPlayerMsg("/tpy " + KITBOT_NAME);
            info("Accepted " + KITBOT_NAME + " teleport request.");
            kitbotTpHandled = true;
        }
    }

    @EventHandler
    private void onGameLeave(GameLeftEvent event) {
        notifyDesktop(notifyDisconnect, "THM Highway Builder", "Disconnected while Highway Builder was active.");
        if (hasActiveInMemoryStatsSession() && !statsSessionTerminalOrFinalizing) {
            persistCurrentStatsSession(StatsSessionState.OPEN, true, 0L, "game-leave");
        }
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
        if (suspended || blockPosProvider == null) return; // prevents a fascinating crash

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
                render(event, blockPosProvider.getBlockade(true, blockadeType.get()), mBlockPos -> canMine(mBlockPos, true), true);
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
                render(event, blockPosProvider.getBlockade(false, blockadeType.get()), mBlockPos -> canPlace(mBlockPos, false), false);
            }
        }
    }

    private void render(Render3DEvent event, MBPIterator it, Predicate<MBlockPos> predicate, boolean mine) {
        Color sideColor = mine ? renderMineSideColor.get() : renderPlaceSideColor.get();
        Color lineColor = mine ? renderMineLineColor.get() : renderPlaceLineColor.get();
        ShapeMode shapeMode = mine ? renderMineShape.get() : renderPlaceShape.get();

        for (MBlockPos pos : it) {
            posRender2.set(pos);

            if (predicate.test(posRender2)) {
                int excludeDir = 0;

                for (Direction side : Direction.values()) {
                    posRender3.set(posRender2).add(side.getOffsetX(), side.getOffsetY(), side.getOffsetZ());

                    it.save();
                    for (MBlockPos p : it) {
                        if (p.equals(posRender3) && predicate.test(p)) excludeDir |= Dir.get(side);
                    }
                    it.restore();
                }

                event.renderer.box(posRender2.getBlockPos(), sideColor, lineColor, shapeMode, excludeDir);
            }
        }
    }

    private void updateVariables() {
        prevInput = mc.player.input;
        mc.player.input = input = new CustomPlayerInput();

        placeTimer = breakTimer = count = syncId = 0;
        ignoreCrystals.clear();

        normalMining = null;
        packetMining = null;
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

    private boolean isSignBlock(BlockState state) {
        Block block = state.getBlock();
        return block instanceof SignBlock || block instanceof HangingSignBlock;
    }

    private String getSignText(BlockPos pos) {
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
        this.lastState = lastState;
        this.state = state;

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
                restockDebug("RestockTask queue drained; deferring blockade teardown before returning to Forward.");
            }
            restockTask.deferBlockadeTeardown();
            clearKitbotOrderTracking("restock-sequence-finished-with-teardown");
            setState(State.Forward);
            return;
        }

        if (restockDebugLog.get()) {
            restockDebug("RestockTask finished without pending tasks or teardown requirement; returning to Forward.");
        }
        restockTask.finishSequence();
        clearKitbotOrderTracking("restock-sequence-finished");
        setState(State.Forward);
    }

    private void clearKitbotOrderTracking(String reason) {
        if (!kitbotOrderInFlight) return;

        kitbotOrderInFlight = false;
        kitbotOrderBaselineShulkerCount = 0;
        kitbotOrderExpectedShulkerGain = 0;
        kitbotOrderSentAtAge = 0;
        kitbotOrderRetryCount = 0;
        if (restockDebugLog.get()) restockDebug("KitbotOrder cleared in-flight order tracking (%s).", reason);
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
        return BlockUtils.canBreak(pos.getBlockPos(), state) && (mineBlocksToPlace || !blocksToPlace.get().contains(state.getBlock()));
    }

    private boolean canPlace(MBlockPos pos, boolean liquids) {
        if (pos.getBlockPos().getSquaredDistance(mc.player.getEyePos()) > placeRange.get() * placeRange.get()) return false;
        return liquids ? !pos.getState().getFluidState().isEmpty() : BlockUtils.canPlace(pos.getBlockPos());
    }

    private void restockDebug(String message, Object... args) {
        if (!restockDebugLog.get()) return;
        THMAddon.LOG.info("[restock-debug] " + String.format(message, args));
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
            case Center, ThrowOutTrash, Restock, PlaceShulkerBlockade, MineShulkerBlockade, PlaceEChestBlockade, MineEChestBlockade, MineEnderChests, KitbotOrder -> true;
            default -> false;
        };
    }

    private String stateName(State state) {
        return state == null ? "null" : state.name();
    }

    private boolean ensureCenterSpeedSnapshotCaptured(String reason) {
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
        if (!ensureCenterSpeedSnapshotCaptured(reason)) return;

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

    private boolean hasUsableReconnectBaselineLease(long generation) {
        return reconnectBaselineLease != null
            && reconnectBaselineLease.generation() == generation
            && reconnectBaselineLease.state() == ReconnectBaselineLeaseState.CAPTURED
            && reconnectBaselineMatchesLiveState(reconnectBaselineLease.payload());
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

    private boolean consumeReconnectBaseline(long generation) {
        if (reconnectBaselineLease == null) return false;
        if (reconnectBaselineLease.generation() != generation) return false;
        if (reconnectBaselineLease.state() != ReconnectBaselineLeaseState.CAPTURED) return false;

        Speed speed = Modules.get().get(Speed.class);
        Timer timer = Modules.get().get(Timer.class);
        if (speed == null) return false;

        ReconnectBaselinePayload payload = reconnectBaselineLease.payload();
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

        if (!reconnectBaselineMatchesLiveState(payload)) return false;

        reconnectBaselineLease = new ReconnectBaselineLease(
            generation,
            ReconnectBaselineLeaseState.CONSUMED,
            payload
        );
        return true;
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
    }

    private boolean hasActiveInMemoryStatsSession() {
        return start != null && activeStatsSessionId != null && !activeStatsSessionId.isBlank();
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
            THMAddon.LOG.info("[highway-stats-cache] ignored consumed artifact kind={} session={} generation={}",
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

        THMAddon.LOG.info("[highway-stats-cache] loaded reason=startup kind={} session={} generation={} state={} resumeAllowed={} broken={} placed={}",
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
        nextStatsCheckpointAtMs = snapshot.lastCheckpointAt() <= 0
            ? System.currentTimeMillis() + STATS_CHECKPOINT_INTERVAL_MS
            : snapshot.lastCheckpointAt() + STATS_CHECKPOINT_INTERVAL_MS;

        THMAddon.LOG.info("[highway-stats-cache] restored reason={} kind={} session={} generation={} state={} broken={} placed={} start=({}, {}, {})",
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
            THMAddon.LOG.info("[highway-stats-cache] skipped OPEN persist reason={} session={} because finalization is armed.",
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

        return new StatsArtifactSnapshot(
            kind,
            activeStatsSessionId,
            nextStatsGeneration(),
            state,
            resumeAllowed,
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
            ""
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
            finalizationReason
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

            THMAddon.LOG.warn("[highway-stats-cache] canonical recovery succeeded but shadow cleanup failed; staying in memory-retry mode until shadow is cleared.");
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

            THMAddon.LOG.info("[highway-stats-cache] saved reason={} kind={} session={} generation={} {}->{} resumeAllowed={} broken={} placed={} printedAt={}",
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
            THMAddon.LOG.warn("[highway-stats-cache] save failed reason={} session={} message={}",
                reason,
                shortSessionId(snapshot.sessionId()),
                e.getMessage()
            );
            return false;
        }
    }

    private boolean closeAndRetireCurrentStatsSession(String reason) {
        if (!hasActiveInMemoryStatsSession()) return true;
        return closeAndRetireStatsSession(createCurrentStatsSnapshot(StatsArtifactKind.CANONICAL, StatsSessionState.CLOSED, false, System.currentTimeMillis()), reason);
    }

    private boolean closeAndRetireStatsSession(StatsArtifactSnapshot snapshot, String reason) {
        if (snapshot == null) return true;

        boolean deletedCanonical = deleteStatsArtifact(resolveCanonicalStatsArtifactPath(), reason + "-canonical-delete");
        boolean deletedShadow = deleteStatsArtifact(resolveShadowStatsArtifactPath(), reason + "-shadow-delete");

        if (!deletedCanonical || !deletedShadow) {
            THMAddon.LOG.warn("[highway-stats-cache] retire failed session={} reason={}; canonical/shadow artifacts were not fully removed, leaving finalization record intact.",
                shortSessionId(snapshot.sessionId()),
                reason
            );
            return false;
        }

        boolean deletedFinalization = deleteStatsArtifact(resolveFinalizationRecordPath(), reason + "-finalization-delete");
        if (!deletedFinalization) {
            THMAddon.LOG.warn("[highway-stats-cache] retire incomplete session={} reason={}; finalization record remains for safe recovery.",
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
            THMAddon.LOG.info("[highway-stats-cache] deleted reason={} path={}", reason, path);
            return true;
        } catch (IOException e) {
            THMAddon.LOG.warn("[highway-stats-cache] delete failed reason={} path={} message={}", reason, path, e.getMessage());
            return false;
        }
    }

    private StatsArtifactLoadResult loadStatsArtifact(Path path, StatsArtifactKind kind) {
        if (!Files.exists(path)) return new StatsArtifactLoadResult(null, false, false);

        List<String> lines;
        try {
            lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            THMAddon.LOG.warn("[highway-stats-cache] transient read failure kind={} path={} message={}", kind, path, e.getMessage());
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
            THMAddon.LOG.warn("[highway-stats-cache] unsupported artifact version kind={} path={} version={}", kind, path, version);
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
            THMAddon.LOG.warn("[highway-stats-cache] transient decode failure kind={} path={} message={}", kind, path, e.getMessage());
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

        return new StatsArtifactSnapshot(
            kind,
            meta.getOrDefault("sessionId", ""),
            parseLongSafe(meta.get("generation"), 0L),
            state,
            Boolean.parseBoolean(meta.getOrDefault("resumeAllowed", "false")),
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
            meta.getOrDefault("finalizationReason", "")
        );
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

    private RetiredStatsReportSnapshot captureRetiredStatsReportFromLiveSession() {
        if (!hasActiveInMemoryStatsSession()) return null;
        return new RetiredStatsReportSnapshot(activeStatsSessionId, activeStatsGeneration, start, blocksBroken, blocksPlaced);
    }

    private StatsArtifactSnapshot createFinalizationRecord(String reason) {
        if (!hasActiveInMemoryStatsSession()) return null;
        return new StatsArtifactSnapshot(
            StatsArtifactKind.FINALIZATION,
            activeStatsSessionId,
            nextStatsGeneration(),
            StatsSessionState.PENDING_PRINT,
            false,
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
            reason == null ? "" : reason
        );
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

        RetiredStatsReportSnapshot report = createRetiredStatsReportSnapshot(snapshot);
        if (report == null) return false;

        StatsArtifactSnapshot working = snapshot;
        if (allowPrinting && !working.printedToChat()) {
            if (!tryPrintStatsToChat(working.sessionId(), report.startPos(), report.blocksBroken(), report.blocksPlaced(), reason)) return false;
            scheduleStatsProofScreenshotIfEnabled(working.sessionId(), reason);
            working = updateFinalizationRecord(working, System.currentTimeMillis(), true, working.webhookSendCommitted(), working.apiSendCommitted(), reason + "-printed");
            if (working == null) return false;
        }

        working = commitAndSendFinalExternalStats(working, report, reason);
        if (working == null) return false;

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
            String webhookUrl = decryptWebhook(encryptedWebhook.get(), decryptkey.get());
            if (webhookUrl != null) {
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
                        sendToWebhook(webhookUrl, statsMessage);
                        working = committed;
                    }
                } else warning("Statistics NOT sent to webhook! Distance too small: (highlight)%.0f", distance);
            }
        }

        if (sendStatisticsapi.get() && !working.apiSendCommitted()) {
            double distance = PlayerUtils.distanceTo(report.startPos());
            if (distance > 1) {
                if (distance < 50000) {
                    if (isNot6B6T()) warning("API not sent. You are not on 6B6T");
                    else if (THMSystem.get().getHash() == null || Objects.equals(THMSystem.get().getHash(), "SetYourHash") || Objects.equals(THMSystem.get().getHash(), "")) {
                        warning("API not sent. No Hash set.");
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
                            String playerName = mc.player.getName().getLiteralString();
                            String statsMessageapi = String.format("%s:%s:%s:%.0f:%s:%s:%s:%s:%s",
                                THMSystem.get().getHash(),
                                playerName,
                                server,
                                distance,
                                report.blocksBroken(),
                                report.blocksPlaced(),
                                dir,
                                generateTimestamp(),
                                isOnMainHighway()
                            );
                            sendToAPI(statsMessageapi, getPassword(), getAPIHighway(), "statistics");
                            working = committed;
                        }
                    }
                } else warning("Statistics NOT sent to Api! Please Calculate the real Distance using the /calculate command in proof-of-work");
            } else warning("Statistics NOT sent to Api! Distance too small: (highlight)%.0f", distance);
        }

        return working;
    }

    private void scheduleStatsProofScreenshot(String sessionId, String reason) {
        if (statsProofScreenshotScheduled) return;
        statsProofScreenshotScheduled = true;

        Thread thread = new Thread(() -> {
            try {
                Thread.sleep(STATS_SCREENSHOT_DELAY_MS);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }

            mc.execute(() -> {
                try {
                    takeStatsProofScreenshot(sessionId, reason);
                } finally {
                    statsProofScreenshotScheduled = false;
                }
            });
        }, "thm-highwaybuilder-stats-screenshot");
        thread.setDaemon(true);
        thread.start();
    }

    private void scheduleStatsProofScreenshotIfEnabled(String sessionId, String reason) {
        if (!autoScreenshotStatistics.get()) return;
        if (sessionId == null || sessionId.isBlank()) return;
        scheduleStatsProofScreenshot(sessionId, reason);
    }

    private void scheduleDisconnectScreenStatsScreenshotIfEnabled(String sessionId, String reason) {
        if (!autoScreenshotStatistics.get()) return;
        if (sessionId == null || sessionId.isBlank()) return;
        if (statsDisconnectScreenshotScheduled) return;
        statsDisconnectScreenshotScheduled = true;

        Thread thread = new Thread(() -> {
            try {
                Thread.sleep(STATS_SCREENSHOT_DELAY_MS);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }

            mc.execute(() -> {
                try {
                    takeStatsProofScreenshot(sessionId, reason);
                } finally {
                    statsDisconnectScreenshotScheduled = false;
                }
            });
        }, "thm-highwaybuilder-disconnect-stats-screenshot");
        thread.setDaemon(true);
        thread.start();
    }

    private void takeStatsProofScreenshot(String sessionId, String reason) {
        if (mc == null || mc.getFramebuffer() == null) return;

        String fileName = buildStatsScreenshotFileName(sessionId);
        ScreenshotRecorder.saveScreenshot(mc.runDirectory, fileName, mc.getFramebuffer(), 1, message -> info(message.getString()));
        THMAddon.LOG.info("[highway-stats-cache] screenshot saved reason={} session={} file={}",
            reason,
            shortSessionId(sessionId),
            fileName
        );
    }

    private String buildStatsScreenshotFileName(String sessionId) {
        return "thm-highwaybuilder-session-" + STATS_SCREENSHOT_TIME_FORMAT.format(Instant.now()) + "-" + shortSessionId(sessionId) + ".png";
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
            THMAddon.LOG.warn("[highway-stats-cache] quarantined kind={} reason={} path={} quarantine={} error={}",
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
            THMAddon.LOG.warn("[highway-stats-cache] quarantine failed kind={} reason={} path={} message={} error={}",
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

    private boolean tryPrintStatsToChat(String sessionId, Vec3d startPos, int broken, int placed, String reason) {
        if (startPos == null || mc.player == null || mc.world == null) return false;
        if (!Utils.canUpdate()) return false;

        info("Distance: (highlight)%.0f", PlayerUtils.distanceTo(startPos));
        info("Blocks broken: (highlight)%d", broken);
        info("Blocks placed: (highlight)%d", placed);
        lastPrintedStatsSessionId = sessionId;
        THMAddon.LOG.info("[highway-stats-cache] printed reason={} session={} broken={} placed={}",
            reason,
            shortSessionId(sessionId),
            broken,
            placed
        );
        return true;
    }

    private void recordBlockBroken() {
        blocksBroken++;
        statsSessionDirty = true;
    }

    private void recordBlockPlaced() {
        blocksPlaced++;
        statsSessionDirty = true;
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
        return "(" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + ")";
    }

    private boolean isHotbarSlotReservedByManager(int hotbarSlot) {
        if (hotbarSlot < 0 || hotbarSlot >= 9) return false;
        if (!hotbarmanager.get()) return false;

        HotbarManager manager = Modules.get().get(HotbarManager.class);
        return manager != null && manager.isActive() && manager.managesSlot(hotbarSlot);
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

    private boolean hasForwardPlaceableBlock() {
        if (mc.player == null) return false;

        for (int i = 0; i < mc.player.getInventory().getMainStacks().size(); i++) {
            if (isForwardPlaceableBlock(mc.player.getInventory().getStack(i))) return true;
        }

        return false;
    }

    private boolean clearCursorStackToEmptySlot(String reason) {
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

    private void dropCursorHandBypassingAntiDrop() {
        AntiDrop antiDrop = Modules.get().get(AntiDrop.class);
        boolean wasActive = antiDrop != null && antiDrop.isActive();
        if (wasActive) antiDrop.toggle();
        InvUtils.dropHand();
        if (wasActive) antiDrop.toggle();
    }

    private int findTrashBlockSwapSlot() {
        if (mc.player == null) return -1;

        for (int i = 0; i < mc.player.getInventory().getMainStacks().size(); i++) {
            ItemStack itemStack = mc.player.getInventory().getStack(i);
            if (!(itemStack.getItem() instanceof BlockItem)) continue;
            if (!trashItems.get().contains(itemStack.getItem())) continue;
            return i;
        }

        return -1;
    }

    private void handleFoodTypesChanged(List<Item> selected) {
        if (clampingFoodTypes || selected == null || selected.size() <= 2) return;

        clampingFoodTypes = true;
        try {
            foodTypes.set(new ArrayList<>(selected.subList(0, 2)));
        } finally {
            clampingFoodTypes = false;
        }

        warning("Maximum 2 food types.");
    }

    private boolean hasConfiguredFoodTypes() {
        return !foodTypes.get().isEmpty();
    }

    private boolean isConfiguredFoodItem(Item item) {
        return item != null && foodTypes.get().contains(item);
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

    private int countLooseConfiguredFoodItems(Item item) {
        if (mc.player == null || item == null) return 0;

        int count = 0;
        for (int i = 0; i < mc.player.getInventory().getMainStacks().size(); i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isOf(item)) count += stack.getCount();
        }

        return count;
    }

    private int findPreferredConfiguredFoodSlot(Inventory inventory) {
        int firstMatch = -1;
        int bestMergeMatch = -1;
        int bestMergeCount = -1;

        for (int i = 0; i < inventory.size(); i++) {
            ItemStack stack = inventory.getStack(i);
            if (!isConfiguredFoodStack(stack)) continue;

            if (firstMatch == -1) firstMatch = i;

            int looseCount = countLooseConfiguredFoodItems(stack.getItem());
            if (looseCount <= 0) continue;

            if (looseCount > bestMergeCount) {
                bestMergeCount = looseCount;
                bestMergeMatch = i;
            }
        }

        return bestMergeMatch != -1 ? bestMergeMatch : firstMatch;
    }

    private boolean isContainerItemEmpty(ItemStack containerItem) {
        if (containerItem == null || containerItem.isEmpty() || !Utils.isShulker(containerItem.getItem())) return true;

        ItemStack[] items = new ItemStack[27];
        Utils.getItemsInContainerItem(containerItem, items);

        for (ItemStack stack : items) {
            if (!stack.isEmpty()) return false;
        }

        return true;
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

    private boolean isUsefulCursorStack(ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty()) return false;
        if (itemStack.isIn(ItemTags.PICKAXES)) return true;
        if (isConfiguredFoodStack(itemStack)) return true;
        if (itemStack.getItem() instanceof BlockItem bi) {
            if (trashItems.get().contains(itemStack.getItem())) return false;
            if (blocksToPlace.get().contains(bi.getBlock())) return true;
            if (bi == Items.ENDER_CHEST) return true;
        }
        if (itemStack.isOf(Items.OBSIDIAN) && !trashItems.get().contains(Items.OBSIDIAN)) return true;
        if (Utils.isShulker(itemStack.getItem())) return isUsefulShulkerStack(itemStack);
        return false;
    }

    private boolean isUsefulShulkerStack(ItemStack itemStack) {
        ItemStack[] items = new ItemStack[27];
        Utils.getItemsInContainerItem(itemStack, items);

        for (ItemStack stack : items) {
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
                boolean inRange = blockPos.getSquaredDistance(mc.player.getEyePos()) <= placeRange.get() * placeRange.get();
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
            .append(Text.literal(String.format(message, args)).styled(style -> style.withColor(Formatting.RED)))
            .append("\n")
            .append(getStatsText());

        String screenshotSessionId = lastPrintedStatsSessionId;
        if ((screenshotSessionId == null || screenshotSessionId.isBlank()) && retiredStatsReportSnapshot != null) {
            screenshotSessionId = retiredStatsReportSnapshot.sessionId();
        }
        if ((screenshotSessionId == null || screenshotSessionId.isBlank()) && activeStatsSessionId != null && !activeStatsSessionId.isBlank()) {
            screenshotSessionId = activeStatsSessionId;
        }

        mc.getNetworkHandler().getConnection().disconnect(text);
        scheduleDisconnectScreenStatsScreenshotIfEnabled(screenshotSessionId, "disconnect-screen-stats");
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

        double distance = 0.0;
        if (mc.player != null && statsStart != null) distance = PlayerUtils.distanceTo(statsStart);

        MutableText text = Text.literal(String.format("%sDistance: %s%.0f\n", Formatting.GRAY, Formatting.WHITE, distance));
        text.append(String.format("%sBlocks broken: %s%d\n", Formatting.GRAY, Formatting.WHITE, statsBroken));
        text.append(String.format("%sBlocks placed: %s%d\n", Formatting.GRAY, Formatting.WHITE, statsPlaced));
        if (mc.player != null && statsStart != null && distance > 50000) {
            text.append(String.format("%sRestart Detected. Please calculate the real distance using /calculate in proof-of-work",
                Formatting.YELLOW));
        }

        return text;
    }

    private void notifyDesktop(Setting<Boolean> eventToggle, String heading, String description) {
        if (!desktopNotifies.get() || !eventToggle.get()) return;
        Notify(heading, description);
    }

    private void tickDoubleMine() {
        // could add clientside block breaking to speed the system up, but it would probably make it too vulnerable to desyncs
        if (normalMining != null) {
            if (normalMining.shouldRemove()) {
                mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.ABORT_DESTROY_BLOCK, normalMining.blockPos, normalMining.direction));
                normalMining = null;
                DoubleMineBlock.rateLimited = true;
            }
            else if (mc.world.getBlockState(normalMining.blockPos).getBlock() != normalMining.block) {
                normalMining = null;
                recordBlockBroken();
                count++;
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
                packetMining = null;
            }
            else if (mc.world.getBlockState(packetMining.blockPos).getBlock() != packetMining.block) {
                packetMining = null;
                recordBlockBroken();
                count++;
            }
        }
    }

    private enum State {
        Center {
            private static final int RECENTER_TIMEOUT_TICKS = 20 * 20;
            private int timeoutTicks;

            @Override
            protected void start(HighwayBuilderTHM b) {
                timeoutTicks = RECENTER_TIMEOUT_TICKS;
                b.applyCenterSpeedOverrideIfPossible("center-start");
                if (b.mc.player.getEntityPos().isInRange(Vec3d.ofBottomCenter(b.mc.player.getBlockPos()), 0.1)) {
                    stop(b);
                }
            }

            @Override
            protected void tick(HighwayBuilderTHM b) {
                if (timeoutTicks > 0) timeoutTicks--;
                else {
                    restart(b);
                    return;
                }

                // There is probably a much better way to do this
                double x = Math.abs(b.mc.player.getX() - (int) b.mc.player.getX()) - 0.5;
                double z = Math.abs(b.mc.player.getZ() - (int) b.mc.player.getZ()) - 0.5;

                boolean isX = Math.abs(x) <= 0.1;
                boolean isZ = Math.abs(z) <= 0.1;

                if (isX && isZ) {
                    stop(b);
                }
                else {
                    b.mc.player.setYaw(0);

                    if (!isZ) {
                        b.input.forward(z < 0);
                        b.input.backward(z > 0);

                        if (b.mc.player.getZ() < 0) {
                            boolean forward = b.input.playerInput.forward();
                            b.input.forward(b.input.playerInput.backward());
                            b.input.backward(forward);
                        }
                    }

                    if (!isX) {
                        b.input.right(x > 0);
                        b.input.left(x < 0);

                        if (b.mc.player.getX() < 0) {
                            boolean right = b.input.playerInput.right();
                            b.input.right(b.input.playerInput.left());
                            b.input.left(right);
                        }
                    }

                    b.input.sneak(true);
                }
            }

            private void stop(HighwayBuilderTHM b) {
                b.input.stop();
                b.mc.player.setVelocity(0, 0, 0);
                b.restoreCenterSpeedIfOwned("center-stop");
                b.mc.player.setPosition((int) b.mc.player.getX() + (b.mc.player.getX() < 0 ? -0.5 : 0.5), b.mc.player.getY(), (int) b.mc.player.getZ() + (b.mc.player.getZ() < 0 ? -0.5 : 0.5));
                b.setState(b.lastState);
            }

            private void restart(HighwayBuilderTHM b) {
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
                checkTasks(b);
                b.mc.player.setPitch(20);
                if (b.state == Forward) b.mc.player.setYaw(b.dir.yaw);
            }

            @Override
            protected void tick(HighwayBuilderTHM b) {
                checkTasks(b);
                b.mc.player.setPitch(20);
                if (b.state == Forward) b.input.forward(true); // Move
            }

            private void checkTasks(HighwayBuilderTHM b) {
                if (b.restockTask.shouldTearDownRestockBlockadeFromForward()) b.setState(MineShulkerBlockade, Restock);
                else if (b.destroyCrystalTraps.get() && isCrystalTrap(b)) b.setState(DefuseCrystalTraps); // Destroy crystal traps
                else if (needsToPlace(b, b.blockPosProvider.getLiquids(), true)) b.setState(FillLiquids); // Fill Liquids
                else if (needsToMine(b, b.blockPosProvider.getFront(), true)) b.setState(MineFront); // Mine Front
                else if (b.checkBehind.get() && needsToMine(b, b.blockPosProvider.getBehindFront(), true)) b.setState(MineBehind); // Mine Behind
                else if (b.floor.get() == Floor.Replace && needsToMine(b, b.blockPosProvider.getFloor(), false)) b.setState(MineFloor); // Mine Floor
                else if (b.railings.get() && needsToMine(b, b.blockPosProvider.getRailings(0), false)) b.setState(MineRailings); // Mine Railings
                else if (b.mineAboveRailings.get() && needsToMine(b, b.blockPosProvider.getRailings(1), true)) b.setState(MineAboveRailings); // Mine above railings
                else if (b.railings.get() && needsToPlace(b, b.blockPosProvider.getRailings(0, b.checkBehind.get()), false)) {
                    if (b.cornerBlock.get() && needsToPlace(b, b.blockPosProvider.getRailings(-1, b.checkBehind.get()), false)) b.setState(PlaceCornerBlock); // Place corner support block
                    else b.setState(PlaceRailings); // Place Railings
                }
                else if (needsToPlace(b, b.blockPosProvider.getFloor(b.checkBehind.get()), false)) b.setState(PlaceFloor); // Place Floor
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
                    if (b.renderPlace.get()) RenderUtils.renderTickingBlock(pos.toImmutable(), b.renderPlaceSideColor.get(), b.renderPlaceLineColor.get(), b.renderPlaceShape.get(), 0, 5, true, false);
                    b.placeTimer = b.placeDelay.get();
                }
            }

            private int findAcceptablePlacementBlock(HighwayBuilderTHM b) {
                // still should prioritise trash
                int slot = findAndMoveToHotbar(b, itemStack -> {
                    if (!(itemStack.getItem() instanceof BlockItem)) return false;
                    return b.trashItems.get().contains(itemStack.getItem());
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
            private final Set<Integer> keepSlots = new HashSet<>();
            private boolean timerEnabled, firstTick, threwItems;
            private int timer;
            private static final ItemStack[] ITEMS = new ItemStack[27];

            @Override
            protected void start(HighwayBuilderTHM b) {
                keepSlots.clear();
                List<Integer> trashBlockSlots = new ArrayList<>();

                for (int i = 0; i < b.mc.player.getInventory().getMainStacks().size(); i++) {
                    ItemStack itemStack = b.mc.player.getInventory().getStack(i);

                    if (itemStack.getItem() instanceof BlockItem && b.trashItems.get().contains(itemStack.getItem())) trashBlockSlots.add(i);
                }

                trashBlockSlots.sort((a, c) -> Integer.compare(
                    b.mc.player.getInventory().getStack(c).getCount(),
                    b.mc.player.getInventory().getStack(a).getCount()
                ));

                int keepCount = Math.min(b.keepTrashBlockStacks.get(), trashBlockSlots.size());
                for (int i = 0; i < keepCount; i++) keepSlots.add(trashBlockSlots.get(i));

                timerEnabled = false;
                firstTick = true;
                threwItems = false;
            }

            @Override
            protected void tick(HighwayBuilderTHM b) {
                if (timerEnabled) {
                    if (timer > 0) timer--;
                    else b.setState(b.lastState);

                    return;
                }

                b.mc.player.setYaw(b.dir.opposite().yaw);
                b.mc.player.setPitch(-25);

                if (firstTick) {
                    firstTick = false;
                    return;
                }

                if (!b.mc.player.currentScreenHandler.getCursorStack().isEmpty()) {
                    handleCursorStack(b);
                    return;
                }

                for (int i = 0; i < b.mc.player.getInventory().getMainStacks().size(); i++) {
                    if (keepSlots.contains(i)) continue;

                    ItemStack itemStack = b.mc.player.getInventory().getStack(i);
                    if (itemStack.getItem() == Items.OBSIDIAN && !b.trashItems.get().contains(Items.OBSIDIAN)) continue;

                    if (b.trashItems.get().contains(itemStack.getItem())) {
                        InvUtils.drop().slot(i);
                        threwItems = true;
                        return;
                    }

                    if (b.ejectUselessShulkers.get() && Utils.isShulker(itemStack.getItem())) {
                        if (!isUsefulShulker(b, itemStack)) {
                            InvUtils.drop().slot(i);
                            threwItems = true;
                            return;
                        }
                    }
                }

                timerEnabled = true;
                timer = threwItems ? 10 : 1;
            }

            private void handleCursorStack(HighwayBuilderTHM b) {
                ItemStack cursorStack = b.mc.player.currentScreenHandler.getCursorStack();
                if (b.clearCursorStackToEmptySlot("ThrowOutTrash")) return;

                if (trySwapCursorObsidianForTrash(b)) {
                    b.protectUsefulCursorStackFromDrop("ThrowOutTrash-obsidian-swap");
                    if (!b.mc.player.currentScreenHandler.getCursorStack().isEmpty()) InvUtils.dropHand();
                    threwItems = true;
                    return;
                }

                if (b.protectUsefulCursorStackFromDrop("ThrowOutTrash-cursor")) return;

                if (Utils.isShulker(cursorStack.getItem()) && b.ejectUselessShulkers.get()) {
                    if (!isUsefulShulker(b, cursorStack)) {
                        if (!b.protectUsefulCursorStackFromDrop("ThrowOutTrash-useless-shulker")) InvUtils.dropHand();
                        threwItems = true;
                        return;
                    }

                    if (trySwapProtectedCursorForDroppableSlot(b)) {
                        if (!b.protectUsefulCursorStackFromDrop("ThrowOutTrash-protected-shulker-swap")) InvUtils.dropHand();
                        threwItems = true;
                    }
                    return;
                }

                if (!b.protectUsefulCursorStackFromDrop("ThrowOutTrash-default")) InvUtils.dropHand();
            }

            private boolean trySwapCursorObsidianForTrash(HighwayBuilderTHM b) {
                ItemStack cursorStack = b.mc.player.currentScreenHandler.getCursorStack();
                if (!cursorStack.isOf(Items.OBSIDIAN)) return false;

                int trashSlot = findTrashSwapSlot(b);
                if (trashSlot == -1) return false;

                b.mc.interactionManager.clickSlot(
                    b.mc.player.currentScreenHandler.syncId,
                    SlotUtils.indexToId(trashSlot),
                    0,
                    SlotActionType.PICKUP,
                    b.mc.player
                );

                return !b.mc.player.currentScreenHandler.getCursorStack().isOf(Items.OBSIDIAN);
            }

            private int findTrashSwapSlot(HighwayBuilderTHM b) {
                for (int i = 0; i < b.mc.player.getInventory().getMainStacks().size(); i++) {
                    if (keepSlots.contains(i)) continue;

                    ItemStack itemStack = b.mc.player.getInventory().getStack(i);
                    if (!(itemStack.getItem() instanceof BlockItem)) continue;
                    if (!b.trashItems.get().contains(itemStack.getItem())) continue;

                    return i;
                }

                return -1;
            }

            private boolean trySwapProtectedCursorForDroppableSlot(HighwayBuilderTHM b) {
                int droppableSlot = findDroppableSwapSlot(b);
                if (droppableSlot == -1) return false;

                b.mc.interactionManager.clickSlot(
                    b.mc.player.currentScreenHandler.syncId,
                    SlotUtils.indexToId(droppableSlot),
                    0,
                    SlotActionType.PICKUP,
                    b.mc.player
                );

                ItemStack cursorStack = b.mc.player.currentScreenHandler.getCursorStack();
                return !Utils.isShulker(cursorStack.getItem()) || !isUsefulShulker(b, cursorStack);
            }

            private int findDroppableSwapSlot(HighwayBuilderTHM b) {
                for (int i = 0; i < b.mc.player.getInventory().getMainStacks().size(); i++) {
                    if (keepSlots.contains(i)) continue;

                    ItemStack itemStack = b.mc.player.getInventory().getStack(i);
                    if (b.trashItems.get().contains(itemStack.getItem())) return i;
                    if (b.ejectUselessShulkers.get() && Utils.isShulker(itemStack.getItem()) && !isUsefulShulker(b, itemStack)) return i;
                }

                return -1;
            }

            private boolean isUsefulShulker(HighwayBuilderTHM b, ItemStack itemStack) {
                Utils.getItemsInContainerItem(itemStack, ITEMS);

                for (ItemStack stack : ITEMS) {
                    if (stack.getItem() instanceof BlockItem bi
                        && (b.blocksToPlace.get().contains(bi.getBlock())
                        || (b.blocksToPlace.get().contains(Blocks.OBSIDIAN) && bi == Items.ENDER_CHEST))) {
                        return true;
                    }
                    if (stack.isIn(ItemTags.PICKAXES)) return true;
                    if (b.isConfiguredFoodStack(stack)) return true;
                }

                return false;
            }
        },

        PlaceEChestBlockade {
            @Override
            protected void tick(HighwayBuilderTHM b) {
                int slot = findBlocksToPlacePrioritizeTrash(b);
                if (slot == -1) return;

                place(b, b.blockPosProvider.getBlockade(false, b.blockadeType.get()), slot, MineEnderChests);
            }
        },

        MineEChestBlockade {
            @Override
            protected void tick(HighwayBuilderTHM b) {
                mine(b, b.blockPosProvider.getBlockade(true, b.blockadeType.get()), true, Center, Forward);
            }
        },

        MineEnderChests {
            private static final MBlockPos pos = new MBlockPos();
            private int targetEchestsToBreak;
            private int targetObsidianCount;
            private boolean first, primed;
            private boolean stopTimerEnabled;
            private int stopTimer, moveTimer, rebreakTimer, timeout;
            private double returnX, returnY, returnZ;
            private boolean returnAnchorSaved;
            @Override
            protected void start(HighwayBuilderTHM b) {
                b.restockTask.ensureSessionInitialized();
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

                RestockTask.RestockSession session = b.restockTask.getSession();
                session.refreshProgress();
                targetEchestsToBreak = session.getTargetEchestsToBreak();
                targetObsidianCount = session.getMiningGoalObsidianCount();
                if (targetEchestsToBreak <= 0) {
                    b.restockTask.markCurrentSourceExhausted(RestockTask.SourcePhase.MineEnderChests);
                    if (b.restockTask.isTargetSatisfied()) {
                        completeAndReturnToAnchor(b);
                    } else if (b.kitbotRestock.get()) {
                        b.setState(KitbotOrder);
                    } else {
                        b.error("No usable ender chests available to continue obsidian restock.");
                    }
                    return;
                }
                first = true;
                moveTimer = timeout = 0;
                returnX = b.mc.player.getX();
                returnY = b.mc.player.getY();
                returnZ = b.mc.player.getZ();
                returnAnchorSaved = true;

                stopTimerEnabled = false;
                primed = false;
            }

            @Override
            protected void tick(HighwayBuilderTHM b) {
                if (stopTimerEnabled) {
                    if (stopTimer > 0) stopTimer--;
                    else completeAndReturnToAnchor(b);

                    return;
                }

                HorizontalDirection dir = b.dir.diagonal ? b.dir.rotateLeft().rotateLeftSkipOne() : b.dir.opposite();
                pos.set(b.mc.player).offset(dir);

                // Move
                if (moveTimer > 0) {
                    b.mc.player.setYaw(dir.yaw);
                    b.input.forward(moveTimer > 2);

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
                        if (b.restockTask.isActiveMaterials() && b.restockTask.hasPendingPickaxes()) {
                            completeAndReturnToAnchor(b);
                        } else {
                            b.error("Cannot find pickaxe without silk touch to mine ender chests.");
                        }
                        return;
                    }

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
                        } else {
                            if (selectedSlot != slot) InvUtils.swap(slot, false);
                        }

                        if (b.rotation.get().mine) Rotations.rotate(Rotations.getYaw(bp), Rotations.getPitch(bp), sendRebreakPackets);
                        else sendRebreakPackets.run();

                        if (swappedForRebreak) InvUtils.swap(selectedSlot, false);
                    }
                    else {
                        if (selectedSlot != slot) InvUtils.swap(slot, false);
                        if (b.rotation.get().mine) Rotations.rotate(Rotations.getYaw(bp), Rotations.getPitch(bp), () -> BlockUtils.breakBlock(bp, true));
                        else BlockUtils.breakBlock(bp, true);
                    }
                }
                else {
                    // Place ender chest
                    int slot = findAndMoveToHotbar(b, itemStack -> itemStack.getItem() == Items.ENDER_CHEST);
                    if (slot == -1 || countItem(b, stack -> stack.getItem().equals(Items.ENDER_CHEST)) <= b.saveEchests.get()) {
                        b.restockTask.markCurrentSourceExhausted(RestockTask.SourcePhase.MineEnderChests);
                        stopTimerEnabled = true;
                        stopTimer = 12;
                        return;
                    }

                    if (countItem(b, stack -> stack.isIn(ItemTags.PICKAXES)) <= b.savePickaxes.get()) {
                        if (b.searchEnderChest.get() || b.searchShulkers.get()) {
                            b.restockTask.setPickaxes();
                        }
                    }

                    if (!first) primed = true;

                    BlockUtils.place(bp, Hand.MAIN_HAND, slot, b.rotation.get().place, 0, true, true, b.silentRebreakSwap.get());
                    timeout = 0;
                }
            }

            private void completeAndReturnToAnchor(HighwayBuilderTHM b) {
                if (returnAnchorSaved) {
                    b.input.stop();
                    b.mc.player.setPosition(returnX, returnY, returnZ);
                    returnAnchorSaved = false;
                }

                b.completeRestockTaskAndContinue();
            }
        },

        KitbotOrder {
            private static final ItemStack[] ITEMS = new ItemStack[27];
            private static final int KITBOT_FAILSAFE_MIN_SHULKERS = 2;
            private static final int KITBOT_FAILSAFE_DELAY_TICKS = 200;
            private static final int KITBOT_NO_DELIVERY_RETRY_TICKS = 20 * 180;
            private static final int[][] CAGE_OFFSETS = new int[][]{
                {1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1},
                {1, 1, 0}, {-1, 1, 0}, {0, 1, 1}, {0, 1, -1},
                {0, 2, 0}
            };
            private final BlockPos.Mutable cagePos = new BlockPos.Mutable();
            private boolean orderSent;
            private boolean cageReady;
            private double returnX, returnY, returnZ;
            private boolean returnAnchorSaved;

            @Override
            protected void start(HighwayBuilderTHM b) {
                orderSent = b.kitbotOrderInFlight;
                cageReady = false;
                b.kitbotTpHandled = false;
                returnX = b.mc.player.getX();
                returnY = b.mc.player.getY();
                returnZ = b.mc.player.getZ();
                returnAnchorSaved = true;
                b.input.stop();
            }

            @Override
            protected void tick(HighwayBuilderTHM b) {
                b.input.stop();

                if (!b.kitbotRestock.get()) {
                    b.clearKitbotOrderTracking("kitbot-restock-disabled");
                    returnToAnchorAndSetState(b, Forward);
                    return;
                }

                if (!cageReady) {
                    cageReady = buildCage(b);
                    return;
                }

                if (!orderSent) {
                    KitbotRestockKit kit = b.kitbotRestockKit.get();
                    int amount = 4;
                    b.kitbotOrderBaselineShulkerCount = countMatchingRestockShulkersInInventory(b);
                    b.kitbotOrderExpectedShulkerGain = amount;
                    b.kitbotOrderSentAtAge = b.mc.player.age;
                    b.kitbotOrderRetryCount = 0;
                    KitbotFrontend.kitOrder(kit.kitName, amount);
                    b.info("Ordering kit '%s' x%d from %s.", kit.kitName, amount, KITBOT_NAME);
                    orderSent = true;
                    b.kitbotOrderInFlight = true;
                    return;
                }

                if (handleNoDeliveryTimeout(b)) return;

                if (hasExpectedKitDelivery(b)) {
                    if (b.restockTask.isSequenceActive() && !b.restockTask.tasksInactive()) {
                        if (b.restockTask.isBlockadeReady()) {
                            if (!b.restockTask.validateOrInvalidateBlockadeLease()) {
                                b.restockTask.setBlockadeReady(false);
                                b.restockDebug("KitbotOrder invalidated blockade lease after fallback; forcing rebuild.");
                            }
                            if (repairExistingBlockade(b)) return;
                            b.restockDebug("KitbotOrder restored the existing blockade, returning to Restock.");
                        } else {
                            if (breakCageTop(b)) return;
                            b.restockTask.setBlockadeReady(false);
                            b.restockDebug("KitbotOrder received supplies without a valid blockade, forcing proper blockade rebuild before returning to Restock.");
                        }
                        returnToAnchorAndSetState(b, Restock);
                    } else {
                        b.clearKitbotOrderTracking("kitbot-order-supplies-ready-no-restock-sequence");
                        if (breakCageTop(b)) return;
                        returnToAnchorAndSetState(b, Forward);
                    }
                }
            }

            private boolean handleNoDeliveryTimeout(HighwayBuilderTHM b) {
                int currentShulkerCount = countMatchingRestockShulkersInInventory(b);
                int gainedShulkers = Math.max(currentShulkerCount - b.kitbotOrderBaselineShulkerCount, 0);
                int ticksWaiting = Math.max(b.mc.player.age - b.kitbotOrderSentAtAge, 0);

                if (gainedShulkers > 0 || ticksWaiting < KITBOT_NO_DELIVERY_RETRY_TICKS) return false;

                if (b.kitbotOrderRetryCount == 0) {
                    KitbotRestockKit kit = b.kitbotRestockKit.get();
                    int amount = Math.max(b.kitbotOrderExpectedShulkerGain, 4);
                    KitbotFrontend.kitOrder(kit.kitName, amount);
                    b.kitbotOrderSentAtAge = b.mc.player.age;
                    b.kitbotOrderRetryCount = 1;
                    b.warning("Kitbot restock received no shulkers after 3 minutes. Retrying kit order.");
                    if (b.restockDebugLog.get()) {
                        b.restockDebug("KitbotOrder retry issued after %d ticks with gainedShulkers=%d baseline=%d current=%d.",
                            ticksWaiting,
                            gainedShulkers,
                            b.kitbotOrderBaselineShulkerCount,
                            currentShulkerCount
                        );
                    }
                    return true;
                }

                failKitbotOrder(b, "Kitbot restock failed.");
                return true;
            }

            private boolean buildCage(HighwayBuilderTHM b) {
                int slot = findAndMoveToHotbar(b, itemStack -> itemStack.getItem() instanceof BlockItem bi && bi.getBlock() == Blocks.NETHERRACK);
                if (slot == -1) {
                    failKitbotOrder(b, "No netherrack available to encase before kit order.");
                    return false;
                }

                BlockPos base = b.mc.player.getBlockPos();
                boolean allPlaced = true;

                for (int[] offset : CAGE_OFFSETS) {
                    cagePos.set(base.getX() + offset[0], base.getY() + offset[1], base.getZ() + offset[2]);
                    BlockState state = b.mc.world.getBlockState(cagePos);
                    if (!state.isAir() && state.getFluidState().isEmpty()) continue;

                    allPlaced = false;
                    if (b.placeTimer > 0) break;

                    if (BlockUtils.place(cagePos, Hand.MAIN_HAND, slot, b.rotation.get().place, 0, true, true, true)) {
                        b.placeTimer = b.placeDelay.get();
                    }
                    break;
                }

                return allPlaced;
            }

            private boolean hasExpectedKitDelivery(HighwayBuilderTHM b) {
                int expectedGain = Math.max(b.kitbotOrderExpectedShulkerGain, 4);
                int currentShulkerCount = countMatchingRestockShulkersInInventory(b);
                int targetCount = b.kitbotOrderBaselineShulkerCount + expectedGain;
                int failsafeTarget = b.kitbotOrderBaselineShulkerCount + KITBOT_FAILSAFE_MIN_SHULKERS;
                int ticksWaiting = Math.max(b.mc.player.age - b.kitbotOrderSentAtAge, 0);
                boolean failsafeReady = currentShulkerCount >= failsafeTarget && ticksWaiting >= KITBOT_FAILSAFE_DELAY_TICKS;

                if (b.restockDebugLog.get()) {
                    b.restockDebug("KitbotOrder delivery progress: currentMatchingShulkers=%d target=%d baseline=%d expectedGain=%d failsafeTarget=%d ticksWaiting=%d/%d failsafeReady=%s.",
                        currentShulkerCount,
                        targetCount,
                        b.kitbotOrderBaselineShulkerCount,
                        expectedGain,
                        failsafeTarget,
                        ticksWaiting,
                        KITBOT_FAILSAFE_DELAY_TICKS,
                        failsafeReady
                    );
                }

                return currentShulkerCount >= targetCount || failsafeReady;
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
                Utils.getItemsInContainerItem(itemStack, ITEMS);

                for (ItemStack stack : ITEMS) {
                    if (stack.getItem() instanceof BlockItem bi && (b.blocksToPlace.get().contains(bi.getBlock()) || (b.blocksToPlace.get().contains(Blocks.OBSIDIAN) && bi == Items.ENDER_CHEST))) {
                        return true;
                    }
                    if (b.restockTask.pickaxes && stack.isIn(ItemTags.PICKAXES)) return true;
                    if (b.restockTask.food && b.isConfiguredFoodStack(stack)) return true;
                }

                return false;
            }

            private boolean breakCageTop(HighwayBuilderTHM b) {
                BlockPos top = b.mc.player.getBlockPos().up(2);
                if (b.mc.world.getBlockState(top).getBlock() != Blocks.NETHERRACK) return false;
                if (b.breakTimer > 0) return true;

                Runnable breakBlock = () -> BlockUtils.breakBlock(top, true);
                if (b.rotation.get().mine) Rotations.rotate(Rotations.getYaw(top), Rotations.getPitch(top), breakBlock);
                else breakBlock.run();

                b.breakTimer = b.breakDelay.get();
                return true;
            }

            private boolean repairExistingBlockade(HighwayBuilderTHM b) {
                if (clearKitbotOnlyBlockadeBlocks(b)) return true;

                if (!hasMissingBlockadeBlocks(b)) return false;

                int slot = findBlocksToPlacePrioritizeTrash(b);
                if (slot == -1) {
                    b.restockTask.setBlockadeReady(false);
                    b.restockDebug("KitbotOrder could not find a block to repair the existing blockade, falling back to rebuild.");
                    return false;
                }

                if (b.placeTimer > 0) return true;

                for (MBlockPos blockadePos : b.blockPosProvider.getBlockade(false, b.blockadeType.get())) {
                    BlockPos blockPos = blockadePos.getBlockPos();
                    if (!BlockUtils.canPlace(blockPos)) continue;

                    if (BlockUtils.place(blockPos, Hand.MAIN_HAND, slot, b.rotation.get().place, 0, true, true, true)) {
                        b.placeTimer = b.placeDelay.get();
                        b.restockDebug("KitbotOrder repaired blockade block at %s.", b.formatBlockPos(blockPos));
                    }

                    return true;
                }

                return false;
            }

            private boolean clearKitbotOnlyBlockadeBlocks(HighwayBuilderTHM b) {
                BlockPos base = b.mc.player.getBlockPos();

                for (int[] offset : CAGE_OFFSETS) {
                    cagePos.set(base.getX() + offset[0], base.getY() + offset[1], base.getZ() + offset[2]);
                    if (b.mc.world.getBlockState(cagePos).getBlock() != Blocks.NETHERRACK) continue;
                    if (isDesiredBlockadePosition(b, cagePos)) continue;
                    if (b.breakTimer > 0) return true;

                    Runnable breakBlock = () -> BlockUtils.breakBlock(cagePos, true);
                    if (b.rotation.get().mine) Rotations.rotate(Rotations.getYaw(cagePos), Rotations.getPitch(cagePos), breakBlock);
                    else breakBlock.run();

                    b.breakTimer = b.breakDelay.get();
                    b.restockDebug("KitbotOrder cleared temporary cage block at %s while restoring existing blockade.", b.formatBlockPos(cagePos));
                    return true;
                }

                return false;
            }

            private boolean hasMissingBlockadeBlocks(HighwayBuilderTHM b) {
                for (MBlockPos blockadePos : b.blockPosProvider.getBlockade(false, b.blockadeType.get())) {
                    if (BlockUtils.canPlace(blockadePos.getBlockPos())) return true;
                }

                return false;
            }

            private boolean isDesiredBlockadePosition(HighwayBuilderTHM b, BlockPos blockPos) {
                for (MBlockPos blockadePos : b.blockPosProvider.getBlockade(false, b.blockadeType.get())) {
                    if (blockadePos.getBlockPos().equals(blockPos)) return true;
                }

                return false;
            }

            private boolean hasShulkerWithMaterials(HighwayBuilderTHM b) {
                for (int i = 0; i < b.mc.player.getInventory().getMainStacks().size(); i++) {
                    ItemStack itemStack = b.mc.player.getInventory().getStack(i);
                    if (!Utils.isShulker(itemStack.getItem())) continue;

                    Utils.getItemsInContainerItem(itemStack, ITEMS);
                    for (ItemStack stack : ITEMS) {
                        if (stack.getItem() instanceof BlockItem bi) {
                            if (b.blocksToPlace.get().contains(bi.getBlock())
                                || (b.blocksToPlace.get().contains(Blocks.OBSIDIAN) && bi == Items.ENDER_CHEST)) {
                                return true;
                            }
                        }
                        if (b.restockTask.pickaxes && stack.isIn(ItemTags.PICKAXES)) return true;
                    }
                }
                return false;
            }

            private void returnToAnchorAndSetState(HighwayBuilderTHM b, State nextState) {
                if (returnAnchorSaved) {
                    b.input.stop();
                    b.mc.player.setPosition(returnX, returnY, returnZ);
                    returnAnchorSaved = false;
                }

                b.setState(nextState);
            }

            private void failKitbotOrder(HighwayBuilderTHM b, String message) {
                b.clearKitbotOrderTracking("kitbot-order-failed");
                if (returnAnchorSaved) {
                    b.input.stop();
                    b.mc.player.setPosition(returnX, returnY, returnZ);
                    returnAnchorSaved = false;
                }
                b.errorEarly(message);
            }
        },

        // this one was rough to do
        Restock {
            private static final MBlockPos pos = new MBlockPos();
            private static final ItemStack[] ITEMS = new ItemStack[27];
            private static final int INVALID_RESTOCK_RECOVERY_MAX_RETRIES = 1;
            private static final int SOURCE_READY_MAX_RETRIES = 3;
            private int minimumSlots, stopTimer, delayTimer;
            private boolean breakContainer, indicateStopping, transitionToMineEnderChests;
            private int restockPickaxesStartCount;
            private Predicate<ItemStack> shulkerPredicate;
            private Predicate<ItemStack> sourceItemPredicate;
            private String sourceLabel;
            private int sourceReadyRetries;
            private boolean extractedFoodShulkerFromEnderChest;
            private boolean foodShulkerReturnPending;
            private boolean foodShulkerReturnRetryUsed;
            private boolean foodShulkerReturnFinalizeAfterBreak;
            private int foodShulkerReturnWaitTicks;
            private int foodShulkerReturnInventorySlot = -1;
            // if this is ever not -1 when we expect it to be, things break a lot
            private int slot = -1;

            @Override
            protected void start(HighwayBuilderTHM b) {
                restockPickaxesStartCount = b.restockTask.getPickaxeStartCount();
                b.restockTask.ensureSessionInitialized();
                b.restockTask.notePhase(RestockTask.SourcePhase.Inventory);
                b.restockTask.clearBlockedByStaging();

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
                if (!b.restockTask.food) clearFoodReturnTracking();
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

                if (!b.restockTask.isBlockadeReady()) {
                    if (b.lastState != Center && b.lastState != ThrowOutTrash && b.lastState != PlaceShulkerBlockade && b.lastState != this) {
                        b.restockDebug("Restock.start requesting Center before blockade placement.");
                        b.setState(Center);
                        return;
                    }
                    else if (b.lastState == Center) {
                        b.restockDebug("Restock.start requesting ThrowOutTrash before blockade placement.");
                        b.setState(ThrowOutTrash);
                        return;
                    }
                    else if (b.lastState == ThrowOutTrash) {
                        b.restockDebug("Restock.start requesting PlaceShulkerBlockade using configured type %s.", b.blockadeType.get());
                        b.setState(PlaceShulkerBlockade);
                        return;
                    }
                }

                // firstly search your inventory for shulkers that have the items you need
                if (slot == -1 && b.searchShulkers.get() && (b.restockTask.getSession() == null || !b.restockTask.getSession().isInventoryShulkersExhausted())) {
                    b.restockTask.notePhase(RestockTask.SourcePhase.InventoryShulkers);
                    sourceItemPredicate = shulkerPredicate;
                    sourceLabel = "shulker";
                    slot = findAndMoveToHotbar(b, shulkerPredicate, false);
                    if (slot == -1) {
                        b.restockTask.markCurrentSourceExhausted(RestockTask.SourcePhase.InventoryShulkers);
                        sourceItemPredicate = null;
                        sourceLabel = "unknown";
                    }
                }

                // next search your ender chest for raw items and shulkers containing items
                if (slot == -1
                    && b.searchEnderChest.get()
                    && countItem(b, stack -> stack.getItem().equals(Items.ENDER_CHEST)) > 0
                    && (b.restockTask.getSession() == null || !b.restockTask.getSession().isEnderChestExhausted())) {
                    b.restockTask.notePhase(RestockTask.SourcePhase.EnderChest);

                    boolean stop = EChestMemory.isKnown();
                    if (EChestMemory.isKnown()) {
                        for (ItemStack stack : EChestMemory.ITEMS) {
                            if (b.restockTask.materials && stack.getItem() instanceof BlockItem bi) {
                                if (b.blocksToPlace.get().contains(bi.getBlock()) || (b.blocksToPlace.get().contains(Blocks.OBSIDIAN) && bi == Items.ENDER_CHEST && needsMoreRawEchests(b))) {
                                    stop = false;
                                    break;
                                }
                            }
                            if (b.restockTask.pickaxes && stack.isIn(ItemTags.PICKAXES)) {
                                stop = false;
                                break;
                            }
                            if (b.restockTask.food && b.isConfiguredFoodStack(stack)) {
                                stop = false;
                                break;
                            }

                            if (b.searchShulkers.get() && shulkerPredicate.test(stack)) {
                                stop = false;
                                break;
                            }
                        }
                    }

                    if (!stop) {
                        sourceItemPredicate = itemStack -> itemStack.getItem() == Items.ENDER_CHEST;
                        sourceLabel = "ender_chest";
                        slot = findAndMoveToHotbar(b, sourceItemPredicate, false);
                        if (slot == -1) {
                            b.restockTask.markCurrentSourceExhausted(RestockTask.SourcePhase.EnderChest);
                            sourceItemPredicate = null;
                            sourceLabel = "unknown";
                        }
                    } else {
                        b.restockTask.markCurrentSourceExhausted(RestockTask.SourcePhase.EnderChest);
                    }
                }

                if (slot == -1 && shouldMineEnderChestsForMaterials(b) && (b.restockTask.getSession() == null || !b.restockTask.getSession().isMineEnderChestsExhausted())) {
                    b.restockDebug("Restock.start found no direct source; continuing into MineEnderChests.");
                    b.setState(MineEnderChests);
                    return;
                }

                if (slot == -1 && b.kitbotRestock.get()
                    && (b.restockTask.materials || b.restockTask.pickaxes)
                    && !hasShulkerInInventory(b)
                    && b.restockTask.shouldAttemptKitbot()) {
                    b.restockTask.notePhase(RestockTask.SourcePhase.Kitbot);
                    b.restockDebug("Restock.start falling back to KitbotOrder.");
                    b.setState(KitbotOrder);
                    return;
                }

                // by this point we have searched shulkers and your ender chest, and no more items could be found to pull from
                if (slot == -1) {
                    b.restockTask.refreshSessionProgress();
                    if (b.restockTask.isTargetSatisfied()) {
                        b.completeRestockTaskAndContinue();
                    } else if (b.restockTask.isObsidianRestockSession() && b.restockTask.getSession() != null && b.restockTask.getSession().usingGreatestAvailable && b.restockTask.getSession().getProgressTowardsTarget() > 0) {
                        b.completeRestockTaskAndContinue();
                    } else {
                        if (b.restockTask.getSession() != null) b.restockTask.getSession().fail();
                        b.notifyDesktop(b.notifyRestockIssues, "THM Highway Builder", "Unable to perform restock for '" + b.restockTask.item() + "'.");
                        b.error("Unable to perform restock for '" + b.restockTask.item() + "'.");
                    }

                    return;
                }

                int emptySlots = 0;
                for (int i = 0; i < b.mc.player.getInventory().getMainStacks().size(); i++) {
                    if (b.mc.player.getInventory().getStack(i).isEmpty()) emptySlots++;
                }

                if (b.restockTask.getSession() != null && b.restockTask.getSession().targetInitialized) {
                    minimumSlots = b.restockTask.getSession().targetFinal;
                }
                else if (b.restockTask.pickaxes) {
                    int pickaxeRestockSlots = emptySlots - 1;
                    if (pickaxeRestockSlots <= 0) {
                        b.notifyDesktop(b.notifyRestockIssues, "THM Highway Builder", "Not enough empty slots to restock pickaxes.");
                        b.error("Not enough empty slots to restock pickaxes.");
                        return;
                    }

                    minimumSlots = Math.min(pickaxeRestockSlots, b.restockPickaxesAmount.get());
                }
                else {
                    int restockSlots = emptySlots - b.minEmpty.get();
                    if (restockSlots <= 0) {
                        b.notifyDesktop(b.notifyRestockIssues, "THM Highway Builder", "No empty slots available for restocking items.");
                        b.error("No empty slots for restocking items.");
                        return;
                    }

                    minimumSlots = b.restockTask.materials ? restockSlots : 1;
                }

                HorizontalDirection dir = b.dir.diagonal ? b.dir.rotateLeft().rotateLeftSkipOne() : b.dir.opposite();
                pos.set(b.mc.player).offset(dir);
                b.restockDebug("Restock.start container position set to %s, slot=%d, minimumSlots=%d.",
                    b.formatBlockPos(pos.getBlockPos()),
                    slot,
                    minimumSlots
                );
                if (slot >= 0) {
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
                breakContainer = b.mc.world.getBlockState(pos.getBlockPos()).getBlock() == Blocks.ENDER_CHEST;
                b.restockDebug("Restock.start breakContainer=%s because block at restock pos is %s.",
                    breakContainer,
                    b.mc.world.getBlockState(pos.getBlockPos()).getBlock()
                );

                indicateStopping = false;
                delayTimer = b.inventoryDelay.get();
            }

            @Override
            protected void tick(HighwayBuilderTHM b) {
                // this should only tick if there's a valid slot we can restock from
                if (slot == -1 && !foodShulkerReturnPending) {
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
                        && !b.protectUsefulCursorStackFromDrop("Restock.tick")) {
                        InvUtils.dropHand();
                    }
                    delayTimer = b.inventoryDelay.get();
                    return;
                }

                BlockPos blockPos = pos.getBlockPos();
                if (foodShulkerReturnPending && handleFoodShulkerReturn(b, blockPos)) return;

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
                    if (b.restockTask.food
                        && extractedFoodShulkerFromEnderChest
                        && b.mc.currentScreen instanceof ShulkerBoxScreen screen
                        && screen.getScreenHandler().syncId == b.syncId) {
                        Inventory inv = ((ShulkerBoxScreenHandlerAccessor) screen.getScreenHandler()).meteor$getInventory();
                        prepareFoodShulkerReturn(b, inv);
                    }
                    indicateStopping = true;
                    breakContainer = true;
                    stopTimer = 12;
                    if (b.mc.currentScreen != null) b.closeHandledScreen();
                    return;
                }
                if (b.restockTask.canTransitionToMineEnderChests() && !indicateStopping) {
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
                            b.restockTask.markCurrentSourceExhausted(RestockTask.SourcePhase.InventoryShulkers);
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

                            if (restockItems(b, inv)) {
                                delayTimer = b.inventoryDelay.get();
                                return;
                            }

                            // we may have taken items themselves from the ec, but still need more. Now we try to find a shulker containing the items
                            if (b.searchShulkers.get()) {
                                int moveTo = InvUtils.findEmpty().slot();

                                if (moveTo != -1) {
                                    int shulkerSlot = findShulkerSlotInInventory(inv);
                                    if (shulkerSlot != -1) {
                                        InvUtils.shiftClick().slotId(shulkerSlot);
                                        if (b.restockTask.food) trackExtractedFoodShulkerFromEnderChest(b, moveTo);
                                        delayTimer = b.inventoryDelay.get();
                                    }
                                } else {
                                    b.restockTask.markBlockedByStaging(RestockTask.SourcePhase.EnderChest);
                                    b.restockDebug("Restock.tick marked source blocked by staging while extracting shulkers from ender chest.");
                                }
                            }

                            // if it reaches here, we have taken everything we can from your ender chest, and may have also grabbed a shulker
                            // we should be finished in your ender chest, so we can break it and either continue on our way or start checking shulkers
                            b.restockTask.markCurrentSourceExhausted(RestockTask.SourcePhase.EnderChest);
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
                        BlockUtils.place(blockPos, Hand.MAIN_HAND, slot, b.rotation.get().place, 0, true, true, true);
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
                return b.restockTask.materials
                    && b.mineEnderChests.get()
                    && b.blocksToPlace.get().contains(Blocks.OBSIDIAN)
                    && b.restockTask.needsMoreRawEchestsForSession()
                    && countItem(b, stack -> stack.getItem().equals(Items.ENDER_CHEST)) > b.saveEchests.get();
            }

            private boolean restockItems(HighwayBuilderTHM b, Inventory inv) {
                if (b.restockTask.materials) {
                    // take raw material
                    if (grabFromInventory(b, inv, itemStack -> itemStack.getItem() instanceof BlockItem bi && b.blocksToPlace.get().contains(bi.getBlock()))) return true;

                    // prefer taking raw material before echests
                    if (b.blocksToPlace.get().contains(Blocks.OBSIDIAN) && needsMoreRawEchests(b)) {
                        if (grabFromInventory(b, inv, itemStack -> itemStack.getItem() == Items.ENDER_CHEST)) return true;
                    }
                }
                if (b.restockTask.pickaxes) {
                    if (grabFromInventory(b, inv, itemStack -> itemStack.isIn(ItemTags.PICKAXES))) return true;
                }
                if (b.restockTask.food) {
                    return grabFromInventory(b, inv, b::isConfiguredFoodStack);
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
                    if (!Utils.isShulker(itemStack.getItem())) return false;
                    Utils.getItemsInContainerItem(itemStack, ITEMS);

                    for (ItemStack stack : ITEMS) {
                        if (b.restockTask.materials && stack.getItem() instanceof BlockItem bi) {
                            if (b.blocksToPlace.get().contains(bi.getBlock()) || (b.blocksToPlace.get().contains(Blocks.OBSIDIAN) && bi == Items.ENDER_CHEST && needsMoreRawEchests(b))) return true;
                        }
                        if (b.restockTask.pickaxes && stack.isIn(ItemTags.PICKAXES)) return true;
                        if (b.restockTask.food && b.isConfiguredFoodStack(stack)) return true;
                    }

                    return false;
                };
            }

            private int findShulkerSlotInInventory(Inventory inv) {
                for (int i = 0; i < inv.size(); i++) {
                    if (shulkerPredicate.test(inv.getStack(i))) return i;
                }

                return -1;
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

            private void prepareFoodShulkerReturn(HighwayBuilderTHM b, Inventory inv) {
                if (!extractedFoodShulkerFromEnderChest || foodShulkerReturnPending) return;

                foodShulkerReturnInventorySlot = slot;
                if (b.isContainerInventoryEmpty(inv)) {
                    b.restockDebug("Extracted food shulker became empty after pull; keeping it in inventory for later cleanup.");
                    clearFoodReturnTracking();
                    return;
                }

                foodShulkerReturnPending = true;
                foodShulkerReturnRetryUsed = false;
                foodShulkerReturnFinalizeAfterBreak = false;
                foodShulkerReturnWaitTicks = 20;
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

                    BlockUtils.place(blockPos, Hand.MAIN_HAND, echestSlot, b.rotation.get().place, 0, true, true, true);
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
                    foodShulkerReturnWaitTicks = 20;
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
                if (foodShulkerReturnInventorySlot < 0 || foodShulkerReturnInventorySlot >= b.mc.player.getInventory().getMainStacks().size()) return -1;
                ItemStack stack = b.mc.player.getInventory().getStack(foodShulkerReturnInventorySlot);
                return Utils.isShulker(stack.getItem()) ? foodShulkerReturnInventorySlot : -1;
            }

            private void clearFoodReturnTracking() {
                extractedFoodShulkerFromEnderChest = false;
                foodShulkerReturnPending = false;
                foodShulkerReturnRetryUsed = false;
                foodShulkerReturnFinalizeAfterBreak = false;
                foodShulkerReturnWaitTicks = 0;
                foodShulkerReturnInventorySlot = -1;
            }

            private boolean needsMoreRawEchests(HighwayBuilderTHM b) {
                if (!b.blocksToPlace.get().contains(Blocks.OBSIDIAN)) return false;
                return b.restockTask.needsMoreRawEchestsForSession();
            }

            private boolean hasShulkerInInventory(HighwayBuilderTHM b) {
                for (int i = 0; i < b.mc.player.getInventory().getMainStacks().size(); i++) {
                    if (shulkerPredicate.test(b.mc.player.getInventory().getStack(i))) return true;
                }
                return false;
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

            private int countSlots(HighwayBuilderTHM b, Predicate<ItemStack> predicate) {
                int count = 0;
                for (int i = 0; i < b.mc.player.getInventory().getMainStacks().size(); i++) {
                    ItemStack stack = b.mc.player.getInventory().getStack(i);
                    if (predicate.test(stack)) count++;
                }

                return count;
            }
        },

        PlaceShulkerBlockade {
            @Override
            protected void tick(HighwayBuilderTHM b) {
                int slot = findBlocksToPlacePrioritizeTrash(b);
                if (slot == -1) {
                    b.restockDebug("PlaceShulkerBlockade.tick could not find a block slot for blockade placement.");
                    return;
                }

                place(b, b.blockPosProvider.getBlockade(false, b.blockadeType.get()), slot, Restock);
            }
        },

        MineShulkerBlockade {
            private boolean stopTimerEnabled;
            private int stopTimer;

            @Override
            protected void start(HighwayBuilderTHM b) {
                b.restockTask.setBlockadeReady(false);
                b.restockDebug("MineShulkerBlockade.start(blockadeType=%s, lastState=%s)", b.blockadeType.get(), b.stateName(b.lastState));
                stopTimerEnabled = false;
                if (b.lastState == this) {
                    stopTimerEnabled = true;
                    stopTimer = 12;
                    b.restockDebug("MineShulkerBlockade.start entering stop timer cleanup.");
                }
            }

            @Override
            protected void tick(HighwayBuilderTHM b) {
                if (!stopTimerEnabled) {
                    // mining b.blockadeType instead of BlockadeType.Shulker is the fastest fix to the module leaving
                    // some blocks behind if you start a pickaxe restock task while mining echests
                    mine(b, b.blockPosProvider.getBlockade(true, b.blockadeType.get()), true, this, this);
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
                        BlockUtils.canBreak(pos.getBlockPos(), pos.getState())
                            && (mineBlocksToPlace || !b.blocksToPlace.get().contains(pos.getState().getBlock()))
                            && !BlockUtils.canInstaBreak(pos.getBlockPos()) && (!Modules.get().get(SpeedMine.class).instamine() || pos.getState().calcBlockBreakingDelta(b.mc.player, b.mc.world, pos.getBlockPos()) <= 0.5)
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
                if (b.count >= b.blocksPerTick.get()) return;
                if (b.breakTimer > 0) return;

                BlockState state = pos.getState();
                if (state.isAir() || (!mineBlocksToPlace && b.blocksToPlace.get().contains(state.getBlock()))) continue;
                if (b.shouldSkipSignBreak(pos.getBlockPos(), state)) continue;

                int slot = findAndMoveBestToolToHotbar(b, state, false);
                if (slot == -1) return;

                if (slot != b.mc.player.getInventory().getSelectedSlot()) InvUtils.swap(slot, false);

                BlockPos mcPos = pos.getBlockPos();
                boolean multiBreak = b.blocksPerTick.get() > 1 && BlockUtils.canInstaBreak(mcPos) && !b.rotation.get().mine;
                if (BlockUtils.canBreak(mcPos)) {
                    if (b.rotation.get().mine) Rotations.rotate(Rotations.getYaw(mcPos), Rotations.getPitch(mcPos), () -> BlockUtils.breakBlock(mcPos, true));
                    else BlockUtils.breakBlock(mcPos, true);
                    breaking = true;

                    b.breakTimer = b.breakDelay.get();

                    if (!b.lastBreakingPos.equals(pos)) {
                        b.lastBreakingPos.set(pos);
                        b.recordBlockBroken();
                    }

                    b.count++;

                    // can only multi break if we aren't rotating and the block can be insta-mined
                    if (!multiBreak) break;
                }

                if (!it.hasNext() && BlockUtils.canInstaBreak(mcPos)) finishedBreaking = true;
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

            if (b.restockDebugLog.get() && (this == PlaceShulkerBlockade || this == PlaceEChestBlockade)) {
                b.restockDebug("%s tick using hotbar slot %d and blockade=%s.",
                    b.stateName(this),
                    slot,
                    b.blockadeType.get()
                );
                b.logRestockBlockadeProbe(b.stateName(this), it);
            }

            for (MBlockPos pos : it) {
                scannedTargets++;
                if (b.count >= it.placementsPerTick(b)) {
                    if (b.restockDebugLog.get() && (this == PlaceShulkerBlockade || this == PlaceEChestBlockade)) {
                        b.restockDebug("%s paused: placement count limit reached (%d/%d).",
                            b.stateName(this),
                            b.count,
                            it.placementsPerTick(b)
                        );
                    }
                    return;
                }
                if (b.placeTimer > 0) {
                    if (b.restockDebugLog.get() && (this == PlaceShulkerBlockade || this == PlaceEChestBlockade)) {
                        b.restockDebug("%s paused: placeTimer=%d before attempting %s.",
                            b.stateName(this),
                            b.placeTimer,
                            b.formatBlockPos(pos.getBlockPos())
                        );
                    }
                    return;
                }

                if (pos.getBlockPos().getSquaredDistance(b.mc.player.getEyePos()) > b.placeRange.get() * b.placeRange.get()) {
                    if (b.restockDebugLog.get() && (this == PlaceShulkerBlockade || this == PlaceEChestBlockade)) {
                        b.restockDebug("%s skipped %s: out of range.",
                            b.stateName(this),
                            b.formatBlockPos(pos.getBlockPos())
                        );
                    }
                    continue;
                }

                // CheckEntities & SwapBack are disabled for waiting for better accuracy and speed of the builder
                boolean placedThisTick = BlockUtils.place(pos.getBlockPos(), Hand.MAIN_HAND, slot, b.rotation.get().place, 0, true, true, true);

                if (b.restockDebugLog.get() && (this == PlaceShulkerBlockade || this == PlaceEChestBlockade)) {
                    BlockState stateAfterAttempt = b.mc.world.getBlockState(pos.getBlockPos());
                    b.restockDebug("%s attempt %s with slot %d -> success=%s, stateNow=%s, canPlaceNow=%s",
                        b.stateName(this),
                        b.formatBlockPos(pos.getBlockPos()),
                        slot,
                        placedThisTick,
                        stateAfterAttempt.getBlock(),
                        BlockUtils.canPlace(pos.getBlockPos())
                    );
                }

                if (placedThisTick) {
                    placed = true;
                    b.recordBlockPlaced();
                    b.placeTimer = b.placeDelay.get();

                    b.count++;
                    if (b.placementsPerTick.get() == 1) break;
                }

                if (!it.hasNext()) finishedPlacing = true;
            }

            if (b.restockDebugLog.get() && (this == PlaceShulkerBlockade || this == PlaceEChestBlockade)) {
                b.restockDebug("%s completed tick: scanned=%d placedAny=%s finishedPlacing=%s next=%s",
                    b.stateName(this),
                    scannedTargets,
                    placed,
                    finishedPlacing,
                    b.stateName(nextState)
                );
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
                if (b.trashItems.get().contains(itemStack.getItem())) thrashSlot = i;

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

        protected boolean hasItem(HighwayBuilderTHM b, Predicate<ItemStack> predicate) {
            for (int i = 0; i < b.mc.player.getInventory().getMainStacks().size(); i++) {
                if (predicate.test(b.mc.player.getInventory().getStack(i))) return true;
            }

            return false;
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
            if (!b.clearCursorStackToEmptySlot("findAndMoveToHotbar") && !b.protectUsefulCursorStackFromDrop("findAndMoveToHotbar")) InvUtils.dropHand();

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

            if (!b.clearCursorStackToEmptySlot("findAndMoveToHotbar-cursor") && !b.protectUsefulCursorStackFromDrop("findAndMoveToHotbar-cursor")) InvUtils.dropHand();

            return predicate.test(b.mc.player.getInventory().getStack(hotbarSlot)) ? hotbarSlot : -1;
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
                    return !b.dontBreakTools.get() || itemStack.getMaxDamage() - itemStack.getDamage() > (itemStack.getMaxDamage() * (b.breakDurability.get() / 100));
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
                if (count <= b.savePickaxes.get() && !(b.restockTask.pickaxes && bestStack.getMaxDamage() - bestStack.getDamage() > (bestStack.getMaxDamage() * (b.breakDurability.get() / 100)))) {
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
            if (!b.clearCursorStackToEmptySlot("findAndMoveBestToolToHotbar") && !b.protectUsefulCursorStackFromDrop("findAndMoveBestToolToHotbar")) InvUtils.dropHand();

            return hotbarSlot;
        }

        protected int findBlocksToPlace(HighwayBuilderTHM b) {
            // find a block and move it to your hotbar
            int slot = findAndMoveToHotbar(b, itemStack -> itemStack.getItem() instanceof BlockItem blockItem && b.blocksToPlace.get().contains(blockItem.getBlock()));

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
                if (
                    (b.searchEnderChest.get() || b.searchShulkers.get())
                    || (b.mineEnderChests.get() && b.blocksToPlace.get().contains(Blocks.OBSIDIAN) && countItem(b, stack -> stack.getItem().equals(Items.ENDER_CHEST)) > b.saveEchests.get())
                ) {
                    b.restockTask.setMaterials();
                } else if (b.kitbotRestock.get()) {
                    b.setState(KitbotOrder);
                } else {
                    b.notifyDesktop(b.notifyOutOfBlocks, "THM Highway Builder", "Out of blocks to place.");
                    b.error("Out of blocks to place.");
                }

                return -1;
            }

            return slot;
        }

        protected int findBlocksToPlacePrioritizeTrash(HighwayBuilderTHM b) {
            int slot = findAndMoveToHotbar(b, itemStack -> {
                if (!(itemStack.getItem() instanceof BlockItem)) return false;
                return b.trashItems.get().contains(itemStack.getItem());
            });

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
            return b.placementsPerTick.get();
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
            this.direction = BlockUtils.getDirection(pos);
            this.packet = false;
        }

        public DoubleMineBlock startDestroying() {
            b.mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, this.blockPos, this.direction));
            normalStartTime = b.mc.player.age;
            return this;
        }

        public DoubleMineBlock stopDestroying() {
            b.mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, this.blockPos, this.direction));
            return this;
        }

        public DoubleMineBlock packetMine() {
            packetStartTime = b.mc.player.age;
            packet = true;
            return stopDestroying();
        }

        public boolean isReady() {
            return progress() >= (b.fastBreak.get() ? 0.7 : 1.0);
        }

        public boolean shouldRemove() {
            boolean distance = !packet && Utils.distance(b.mc.player.getEyePos().x, b.mc.player.getEyePos().y, b.mc.player.getEyePos().z, blockPos.getX() + direction.getOffsetX(), blockPos.getY() + direction.getOffsetY(), blockPos.getZ() + direction.getOffsetZ()) > b.mc.player.getBlockInteractionRange();

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
        public boolean materials;
        public boolean pickaxes;
        public boolean food;
        private boolean pendingMaterials;
        private boolean pendingPickaxes;
        private boolean pendingFood;
        private boolean sequenceActive;
        private boolean blockadeReady;
        private boolean blockadeTeardownPending;
        private int pickaxeStartCount;
        private long blockadeLeaseGenerationCounter;
        private RestockSession session;
        private final Deque<Type> pendingQueue = new ArrayDeque<>();
        private final HighwayBuilderTHM b;

        private enum Type {
            Materials,
            Pickaxes,
            Food
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
            SOURCE_EXHAUSTED,
            BLOCKED_BY_STAGING
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
            private BlockadeType blockadeType = BlockadeType.Partial;
            private BlockadeLeaseState state = BlockadeLeaseState.NOT_BUILT;
            private long lastValidatedTick;

            private void markBuilt() {
                generationId = ++blockadeLeaseGenerationCounter;
                blockadeType = b.blockadeType.get();
                if (b.mc.player != null) anchorPos.set(b.mc.player.getBlockPos());
                state = BlockadeLeaseState.BUILT;
                lastValidatedTick = b.mc.player != null ? b.mc.player.age : 0L;
            }

            private void markTeardownPending() {
                state = BlockadeLeaseState.TEARDOWN_PENDING;
                lastValidatedTick = b.mc.player != null ? b.mc.player.age : lastValidatedTick;
            }

            private void invalidate() {
                state = BlockadeLeaseState.INVALIDATED;
                lastValidatedTick = b.mc.player != null ? b.mc.player.age : lastValidatedTick;
            }

            private void reset() {
                generationId = 0L;
                anchorPos.set(0, 0, 0);
                blockadeType = BlockadeType.Partial;
                state = BlockadeLeaseState.NOT_BUILT;
                lastValidatedTick = 0L;
            }

            private boolean validateCurrentAnchor() {
                if (state != BlockadeLeaseState.BUILT) return false;
                if (b.mc.player == null || b.mc.world == null) return false;
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
        }

        private final class RestockSession {
            private final Type taskType;
            private final BlockadeLease blockadeLease = new BlockadeLease();
            private SourcePhase phase;
            private SourceAttemptResult lastResult;
            private boolean usingGreatestAvailable;
            private boolean targetInitialized;
            private boolean inventoryShulkersExhausted;
            private boolean enderChestExhausted;
            private boolean mineEnderChestsExhausted;
            private boolean blockedByStaging;
            private int targetFinal;
            private int remainingTarget;
            private int workingStageCapacity;
            private int pickaxesStartCount;
            private int materialStartStacks;
            private int foodStartItems;
            private int obsidianStartItems;
            private int pickaxesAcquiredCount;
            private int materialStacksAcquired;
            private int foodItemsAcquired;
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
                materialStartStacks = countInventorySlots(this::isTrackedMaterialStack);
                foodStartItems = countInventoryItems(this::isTrackedFoodStack);
                obsidianStartItems = countInventoryItems(itemStack -> itemStack.getItem() == Items.OBSIDIAN);
            }

            private void ensureTargetInitialized() {
                if (targetInitialized || b.mc.player == null) return;

                int freeSlots = countEmptyInventorySlots();
                int usableFreeSlots = Math.max(freeSlots - b.minEmpty.get(), 0);
                workingStageCapacity = usableFreeSlots;

                targetFinal = switch (taskType) {
                    case Pickaxes -> Math.min(Math.max(freeSlots - 1, 0), b.restockPickaxesAmount.get());
                    case Materials -> isObsidianTask() ? usableFreeSlots * 64 : usableFreeSlots;
                    case Food -> 1;
                };

                remainingTarget = Math.max(targetFinal, 0);
                targetInitialized = targetFinal > 0;
                refreshProgress();
            }

            private void refreshProgress() {
                if (b.mc.player == null) return;

                workingStageCapacity = Math.max(countEmptyInventorySlots() - b.minEmpty.get(), 0);
                pickaxesAcquiredCount = Math.max(countInventoryItems(itemStack -> itemStack.isIn(ItemTags.PICKAXES)) - pickaxesStartCount, 0);
                materialStacksAcquired = Math.max(countInventorySlots(this::isTrackedMaterialStack) - materialStartStacks, 0);
                foodItemsAcquired = Math.max(countInventoryItems(this::isTrackedFoodStack) - foodStartItems, 0);
                obsidianItemsAcquired = Math.max(countInventoryItems(itemStack -> itemStack.getItem() == Items.OBSIDIAN) - obsidianStartItems, 0);

                int looseEchestsInInventory = countInventoryItems(itemStack -> itemStack.getItem() == Items.ENDER_CHEST);
                reserveRemaining = Math.max(saveEchestsReserve - looseEchestsInInventory, 0);
                usablePulledEchests = Math.max(looseEchestsInInventory - saveEchestsReserve, 0);

                remainingTarget = Math.max(targetFinal - getProgressTowardsTarget(), 0);
                if (isTargetSatisfied()) phase = SourcePhase.Complete;
            }

            private void notePhase(SourcePhase phase) {
                this.phase = phase;
                if (b.restockDebugLog.get()) {
                    b.restockDebug("RestockSession phase=%s task=%s target=%d remaining=%d greatest=%s staging=%d reserveRemaining=%d usableEchests=%d.",
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

            private int getProgressTowardsTarget() {
                return switch (taskType) {
                    case Pickaxes -> pickaxesAcquiredCount;
                    case Materials -> isObsidianTask() ? obsidianItemsAcquired : materialStacksAcquired;
                    case Food -> foodItemsAcquired;
                };
            }

            private boolean isTargetSatisfied() {
                return targetInitialized && getProgressTowardsTarget() >= targetFinal;
            }

            private boolean shouldCompleteAfterInventoryShulkers() {
                if (taskType == Type.Materials && isObsidianTask()) return false;
                return getProgressTowardsTarget() > 0;
            }

            private boolean canTransitionToMineEnderChests() {
                if (!isObsidianTask()) return false;
                return usablePulledEchests > 0 && remainingTarget > 0;
            }

            private int getRemainingObsidianItems() {
                return Math.max(targetFinal - obsidianItemsAcquired, 0);
            }

            private int getObsidianItemsTarget() {
                return targetFinal;
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
                    case InventoryShulkers -> inventoryShulkersExhausted = true;
                    case EnderChest -> enderChestExhausted = true;
                    case MineEnderChests -> mineEnderChestsExhausted = true;
                    default -> { }
                }
            }

            private void markBlockedByStaging(SourcePhase phase) {
                blockedByStaging = true;
                lastResult = SourceAttemptResult.BLOCKED_BY_STAGING;
                markSourceExhausted(phase);
            }

            private void clearBlockedByStaging() {
                blockedByStaging = false;
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

            private void finishSuccessfully() {
                phase = SourcePhase.Complete;
                lastResult = SourceAttemptResult.TARGET_SATISFIED;
                remainingTarget = 0;
            }

            private void fail() {
                phase = SourcePhase.Failed;
            }

            private boolean shouldAttemptKitbot() {
                return !isTargetSatisfied() && !isMineEnderChestsExhausted();
            }

            private boolean hasBlockingStagingShortage() {
                return blockedByStaging && workingStageCapacity <= 0;
            }

            private boolean needsMoreRawEchests() {
                if (!isObsidianTask()) return false;
                int remainingObsidianItems = getRemainingObsidianItems();
                int usableLooseEchests = Math.max(countInventoryItems(itemStack -> itemStack.getItem() == Items.ENDER_CHEST) - saveEchestsReserve, 0);
                int remainingAfterLooseEchests = remainingObsidianItems - (usableLooseEchests * 8);
                return remainingAfterLooseEchests > 0;
            }

            private boolean isTrackedMaterialStack(ItemStack stack) {
                if (!(stack.getItem() instanceof BlockItem bi)) return false;
                if (isObsidianTask()) return bi.getBlock() == Blocks.OBSIDIAN;
                return b.blocksToPlace.get().contains(bi.getBlock()) && bi.getBlock() != Blocks.OBSIDIAN;
            }

            private boolean isTrackedFoodStack(ItemStack stack) {
                return b.isConfiguredFoodStack(stack);
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

        private void setTask(Type type) {
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
            } else if (sequenceActive && b.restockDebugLog.get()) {
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
            clearPending();
            sequenceActive = false;
            blockadeReady = false;
            blockadeTeardownPending = false;
            session = null;
        }

        public void completeActive() {
            materials = false;
            pickaxes = false;
            food = false;
        }

        public boolean tasksInactive() {
            return !materials && !pickaxes && !food;
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
            blockadeReady = value;
            if (session == null) return;
            if (value) session.blockadeLease.markBuilt();
            else session.blockadeLease.invalidate();
        }

        public void deferBlockadeTeardown() {
            blockadeTeardownPending = true;
            if (session != null) session.blockadeLease.markTeardownPending();
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

        public boolean shouldTearDownRestockBlockadeFromForward() {
            return blockadeTeardownPending && shouldTearDownRestockBlockade();
        }

        public void finishSequence() {
            completeActive();
            clearPending();
            sequenceActive = false;
            blockadeReady = false;
            blockadeTeardownPending = false;
            pickaxeStartCount = 0;
            if (session != null) session.blockadeLease.reset();
            session = null;
        }

        public String item() {
            if (materials) return "building materials";
            if (pickaxes) return "pickaxes";
            if (food) return "food";
            return "unknown";
        }

        public String activeSummary() {
            List<String> active = new ArrayList<>();
            if (materials) active.add("materials");
            if (pickaxes) active.add("pickaxes");
            if (food) active.add("food");
            return active.isEmpty() ? "none" : String.join(",", active);
        }

        public String pendingSummary() {
            List<String> pending = new ArrayList<>();
            if (pendingMaterials) pending.add("materials");
            if (pendingPickaxes) pending.add("pickaxes");
            if (pendingFood) pending.add("food");
            return pending.isEmpty() ? "none" : String.join(",", pending);
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
            onTaskActivated(type);
            session = new RestockSession(type);

            switch (type) {
                case Materials -> materials = true;
                case Pickaxes -> pickaxes = true;
                case Food -> food = true;
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
            };
        }

        private boolean isActive(Type type) {
            return switch (type) {
                case Materials -> materials;
                case Pickaxes -> pickaxes;
                case Food -> food;
            };
        }

        private boolean hasPendingTasks() {
            return pendingMaterials || pendingPickaxes || pendingFood;
        }

        private void onTaskActivated(Type type) {
            b.invalidRestockRecoveryRetries = 0;
            b.invalidRestockRecoveryPending = false;

            if (type == Type.Pickaxes) {
                pickaxeStartCount = countInventoryItems(itemStack -> itemStack.isIn(ItemTags.PICKAXES));
            }
        }

        public RestockSession getSession() {
            return session;
        }

        public void ensureSessionInitialized() {
            if (session == null) return;
            session.ensureTargetInitialized();
            session.refreshProgress();
        }

        public void notePhase(SourcePhase phase) {
            if (session != null) session.notePhase(phase);
        }

        public void refreshSessionProgress() {
            if (session != null) session.refreshProgress();
        }

        public boolean isTargetSatisfied() {
            return session != null && session.isTargetSatisfied();
        }

        public boolean shouldCompleteAfterInventoryShulkers() {
            return session != null && session.shouldCompleteAfterInventoryShulkers();
        }

        public boolean canTransitionToMineEnderChests() {
            return session != null && session.canTransitionToMineEnderChests();
        }

        public boolean needsMoreRawEchestsForSession() {
            return session != null && session.needsMoreRawEchests();
        }

        public boolean validateOrInvalidateBlockadeLease() {
            if (session == null) return false;
            boolean valid = session.blockadeLease.validateCurrentAnchor();
            if (!valid) blockadeReady = false;
            return valid;
        }

        public void markCurrentSourceExhausted(SourcePhase phase) {
            if (session != null) session.markSourceExhausted(phase);
        }

        public void markBlockedByStaging(SourcePhase phase) {
            if (session != null) session.markBlockedByStaging(phase);
        }

        public boolean hasBlockingStagingShortage() {
            return session != null && session.hasBlockingStagingShortage();
        }

        public void clearBlockedByStaging() {
            if (session != null) session.clearBlockedByStaging();
        }

        public boolean shouldAttemptKitbot() {
            return session != null && session.shouldAttemptKitbot();
        }

        public boolean isObsidianRestockSession() {
            return session != null && session.isObsidianTask();
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
            pendingQueue.clear();
        }

        private void clearPending(Type type) {
            switch (type) {
                case Materials -> pendingMaterials = false;
                case Pickaxes -> pendingPickaxes = false;
                case Food -> pendingFood = false;
            }
            pendingQueue.removeFirstOccurrence(type);
        }

        private boolean isPending(Type type) {
            return switch (type) {
                case Materials -> pendingMaterials;
                case Pickaxes -> pendingPickaxes;
                case Food -> pendingFood;
            };
        }

        private String item(Type type) {
            return switch (type) {
                case Materials -> "building materials";
                case Pickaxes -> "pickaxes";
                case Food -> "food";
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
```

## Simulated Full Source

```java/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package xyz.thm.addon.modules;

import meteordevelopment.meteorclient.MeteorClient;
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
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
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
import meteordevelopment.meteorclient.utils.render.RenderUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.meteorclient.utils.world.Dir;
import meteordevelopment.meteorclient.utils.world.TickRate;
import meteordevelopment.orbit.EventHandler;
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
import net.minecraft.client.option.Perspective;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.*;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.s2c.play.InventoryS2CPacket;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.EmptyBlockView;
import net.minecraft.world.RaycastContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Range;
import org.joml.Vector3d;
import xyz.thm.addon.THMAddon;
import xyz.thm.addon.system.THMSystem;
import xyz.thm.addon.utils.ServerStatusHandler;
import xyz.thm.addon.utils.ServerStatusHandler.ServerState;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Supplier;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import static xyz.thm.addon.utils.THMUtils.*;
import static xyz.thm.addon.utils.password.*;

@SuppressWarnings("ConstantConditions")
public class HighwayBuilderTHM extends Module {
    private static final String RESTART_DETECTED_MARKER = "server restart detected";
    private static final String STATS_ARTIFACT_MAGIC = "HB_STATS_ARTIFACT_V1";
    private static final int STATS_ARTIFACT_VERSION = 1;
    private static final long STATS_CHECKPOINT_INTERVAL_MS = 10 * 60 * 1000L;
    private static final int STATS_SCREENSHOT_DELAY_MS = 250;
    private static final long STATS_MEMORY_RETRY_RECHECK_MS = 5_000L;
    private static final String STATS_CANONICAL_FILE_NAME = "highwaybuildersettings";
    private static final String STATS_FINALIZATION_FILE_NAME = "highwaybuildersettings.finalization";
    private static final String STATS_SHADOW_FILE_NAME = "highwaybuildersettings.shadow";
    private static final int STATS_GCM_TAG_BITS = 128;
    private static final int STATS_GCM_NONCE_BYTES = 12;
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

    public enum KitbotRestockKit {
        Echest(KitbotFrontend.KitName.Echest),
        Pickaxe(KitbotFrontend.KitName.Pickaxe),
        Highway(KitbotFrontend.KitName.Highway);

        public final KitbotFrontend.KitName kitName;

        KitbotRestockKit(KitbotFrontend.KitName kitName) {
            this.kitName = kitName;
        }

        @Override
        public String toString() {
            return kitName.toString();
        }
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgDigging = settings.createGroup("Digging");
    private final SettingGroup sgPaving = settings.createGroup("Paving");
    private final SettingGroup sgInventory = settings.createGroup("Inventory");
    private final SettingGroup sgStatistics = settings.createGroup("Logging");
    private final SettingGroup sgNotifies = settings.createGroup("Notifies");
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

    private final Setting<Rotation> rotation = sgGeneral.add(new EnumSetting.Builder<Rotation>()
        .name("rotation")
        .description("Mode of rotation.")
        .defaultValue(Rotation.None)
        .build()
    );

    private final Setting<Boolean> kitbotRestock = sgInventory.add(new BoolSetting.Builder()
        .name("kitbot-restock")
        .description("Order a kit from KitBot1 when out of building blocks.")
        .defaultValue(false)
        .build()
    );

    public final Setting<KitbotRestockKit> kitbotRestockKit = sgInventory.add(new EnumSetting.Builder<KitbotRestockKit>()
        .name("kitbot-restock-kit")
        .description("Kit to order when kitbot restock triggers.")
        .defaultValue(KitbotRestockKit.Highway)
        .visible(kitbotRestock::get)
        .build()
    );

    private final Setting<Boolean> disconnectOnToggle = sgGeneral.add(new BoolSetting.Builder()
        .name("disconnect-on-toggle")
        .description("Automatically disconnects when the module is turned off, for example for not having enough blocks.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> pauseOnLag = sgGeneral.add(new BoolSetting.Builder()
        .name("pause-on-lag")
        .description("Pauses the current process while the server stops responding.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> resumeTPS = sgGeneral.add(new IntSetting.Builder()
        .name("resume-tps")
        .description("Server tick speed at which to resume building.")
        .defaultValue(16)
        .range(1, 19)
        .sliderRange(1, 19)
        .visible(pauseOnLag::get)
        .build()
    );

    private final Setting<Boolean> destroyCrystalTraps = sgGeneral.add(new BoolSetting.Builder()
        .name("destroy-crystal-traps")
        .description("Use a bow to defuse crystal traps safely from a distance. An infinity bow is recommended.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> manageThmHwyMonitor = sgGeneral.add(new BoolSetting.Builder()
        .name("manage-thm-hwy-monitor")
        .description("Manages HighwayBuilder to reduce highway-building drift and auto-aligns the user on the current highway when HighwayBuilder is on.")
        .defaultValue(false)
        .onChanged(value -> {
            if (!isActive()) return;
            if (value) syncThmHwyMonitorOnActivate();
            else disableThmHwyMonitorIfActive();
        })
        .visible(() -> isBaritoneInstalled())
        .build()
    );


    // Digging

    private final Setting<Boolean> doubleMine = sgDigging.add(new BoolSetting.Builder()
        .name("double-mine")
        .description("Whether to double mine blocks when applicable (normal mine and packet mine simultaneously).")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> fastBreak = sgDigging.add(new BoolSetting.Builder()
        .name("fast-break")
        .description("Whether to finish breaking blocks faster than normal while double mining.")
        .defaultValue(true)
        .visible(doubleMine::get)
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
        .description("How many pickaxes to ensure are saved. Hitting this number in your inventory will trigger a restock or the module toggling off.")
        .defaultValue(1)
        .range(1, 36)
        .sliderRange(1, 36)
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

    private final Setting<Integer> blocksPerTick = sgDigging.add(new IntSetting.Builder()
        .name("blocks-per-tick")
        .description("The maximum amount of blocks that can be mined in a tick. Only applies to blocks instantly breakable.")
        .defaultValue(7)
        .range(1, 100)
        .sliderRange(1, 25)
        .build()
    );

    private final Setting<Boolean> ignoreSigns = sgDigging.add(new BoolSetting.Builder()
        .name("ignore-signs")
        .description("Ignore breaking signs = preserving history (based).")
        .defaultValue(true)
        .onChanged(value -> updateSignBreakRegex())
        .build()
    );

    private final Setting<Boolean> breakAdvertisementSigns = sgDigging.add(new BoolSetting.Builder()
        .name("break-advertisement-signs")
        .description("Only break signs that look like advertisements/invites.")
        .defaultValue(false)
        .onChanged(value -> updateSignBreakRegex())
        .visible(() -> !ignoreSigns.get())
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
        .defaultValue(4.5)
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

    private final Setting<Boolean> checkBehind = sgGeneral.add(new BoolSetting.Builder()
        .name("check-behind")
        .description("Checks and repairs missing highway floor and railings behind the player.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Integer> placementsPerTick = sgPaving.add(new IntSetting.Builder()
        .name("placements-per-tick")
        .description("The maximum amount of blocks that can be placed in a tick.")
        .defaultValue(1)
        .min(1)
        .build()
    );

    // Inventory

    private boolean clampingFoodTypes;

    private final Setting<List<Item>> trashItems = sgInventory.add(new ItemListSetting.Builder()
        .name("trash-items")
        .description("Items that are considered trash and can be thrown out.")
        .defaultValue(
            Items.NETHERRACK, Items.QUARTZ, Items.GOLD_NUGGET, Items.GOLDEN_SWORD, Items.GLOWSTONE_DUST,
            Items.GLOWSTONE, Items.BLACKSTONE, Items.BASALT, Items.GHAST_TEAR, Items.SOUL_SAND, Items.SOUL_SOIL,
            Items.ROTTEN_FLESH, Items.MAGMA_BLOCK
        )
        .build()
    );

    private final Setting<Boolean> foodRestock = sgInventory.add(new BoolSetting.Builder()
        .name("food-restock")
        .description("Restocks one configured food stack when your valid food count drops to the saved amount.")
        .defaultValue(false)
        .build()
    );

    private final Setting<List<Item>> foodTypes = sgInventory.add(new ItemListSetting.Builder()
        .name("food-types")
        .description("Which food items count as restock food. Maximum 2 food types.")
        .defaultValue()
        .visible(foodRestock::get)
        .onChanged(this::handleFoodTypesChanged)
        .build()
    );

    private final Setting<Integer> saveFood = sgInventory.add(new IntSetting.Builder()
        .name("save-food")
        .description("Restock food when your total configured food count is at or below this value. Do not set higher than half a stack of your chosen food.")
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
        .description("Whether you should eject useless shulkers. Warning - will throw out any shulkers that don't contain blocks to place, pickaxes, or food. Be careful with your kits.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> searchEnderChest = sgInventory.add(new BoolSetting.Builder()
        .name("search-ender-chest")
        .description("Searches your ender chest to find items to use. Be careful with this one, especially if you let it search through shulkers.")
        .defaultValue(false)
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

    private final Setting<Integer> minEmpty = sgInventory.add(new IntSetting.Builder()
        .name("minimum-empty-slots")
        .description("The minimum amount of empty slots you want left after mining obsidian.")
        .defaultValue(3)
        .sliderRange(2, 9)
        .min(2)
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
        .description("What blockade type to use (the structure placed when mining echests). FullRoof adds a roof above the player and container.")
        .defaultValue(BlockadeType.Full)
        .visible(mineEnderChests::get)
        .build()
    );

    public final Setting<Integer> saveEchests = sgInventory.add(new IntSetting.Builder()
        .name("save-ender-chests")
        .description("How many ender chests to ensure are saved. Hitting this number in your inventory will trigger a restock or the module toggling off.")
        .defaultValue(2)
        .range(0, 64)
        .sliderRange(0, 64)
        .visible(mineEnderChests::get)
        .build()
    );

    private final Setting<Boolean> rebreakEchests = sgInventory.add(new BoolSetting.Builder()
        .name("instantly-rebreak-echests")
        .description("Whether or not to use the instant rebreak exploit to break echests.")
        .defaultValue(true)
        .visible(mineEnderChests::get)
        .build()
    );

    private final Setting<Integer> rebreakTimer = sgInventory.add(new IntSetting.Builder()
        .name("rebreak-delay")
        .description("Delay between rebreak attempts.")
        .defaultValue(0)
        .sliderMax(20)
        .visible(() -> mineEnderChests.get() && rebreakEchests.get())
        .build()
    );

    private final Setting<Boolean> silentRebreakSwap = sgInventory.add(new BoolSetting.Builder()
        .name("silent-rebreak-swap")
        .description("Silently swaps to the best pick for instant rebreak packets, then restores your selected slot.")
        .defaultValue(true)
        .visible(() -> mineEnderChests.get() && rebreakEchests.get())
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
        .defaultValue(true)
        .visible(printStatistics::get)
        .build()
    );

    private final Setting<Boolean> restockDebugLog = sgStatistics.add(new BoolSetting.Builder()
        .name("restock-debug-log")
        .description("Prints detailed blockade and restock diagnostics, including placement probes and state transitions.")
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
    private final Setting<String> decryptkey = sgStatistics.add(new StringSetting.Builder()
        .name("webhook-key")
        .description("Optional encryption/decryption key. Only required if the " +
            "webhook URL is encrypted. Ignored for normal (plain) webhook URLs.")
        .defaultValue("MySecureKeyHere123")
        .visible(() -> printStatistics.get() && sendStatisticsWebhhok.get())
        .build()
    );
    private final Setting<String> encryptedWebhook = sgStatistics.add(new StringSetting.Builder()
        .name("encrypted-webhook")
        .description("Webhook URL used to receive statistics. Can be either encrypted or plain text." +
            " Encrypted webhooks will be decrypted using the provided key.")
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
        .description("Switches to third person while Highway Builder is active, then restores your old perspective.")
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

    private Input prevInput;
    private CustomPlayerInput input;

    private State state, lastState;
    private IBlockPosProvider blockPosProvider;

    public Vec3d start;
    public int blocksBroken, blocksPlaced;
    private final MBlockPos lastBreakingPos = new MBlockPos();
    private boolean displayInfo, sentLagMessage;
    private boolean suspended = true, inventory = true;
    private int placeTimer, breakTimer, count, syncId, statusLogTimer;
    private final RestockTask restockTask = new RestockTask(this);
    private int invalidRestockRecoveryRetries;
    private boolean invalidRestockRecoveryPending;
    private boolean kitbotTpHandled;
    private boolean kitbotOrderInFlight;
    private int kitbotOrderBaselineShulkerCount;
    private int kitbotOrderExpectedShulkerGain;
    private int kitbotOrderSentAtAge;
    private int kitbotOrderRetryCount;
    private CenterSpeedSnapshot centerSpeedSnapshot;
    private boolean centerSpeedSnapshotOwned;
    private boolean centerSpeedOverrideActive;
    private boolean centerSpeedMonitorRecoveryOwned;
    private boolean centerSpeedRestorePending;
    private int centerSpeedRestoreRetryTicks;
    private String centerSpeedLastReason = "";
    private ReconnectBaselineLease reconnectBaselineLease;
    private boolean reconnectBaselineRestoreInProgress;
    private boolean reconnectFailureDeactivateArmed;
    private ReconnectResumeContext reconnectResumeContext;
    private Field timerOverrideField;
    private boolean timerOverrideFieldInitialized;
    private boolean timerOverrideReflectionFailureLogged;
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
    private volatile boolean statsProofScreenshotScheduled;
    private volatile boolean statsDisconnectScreenshotScheduled;
    private String lastPrintedStatsSessionId;
    private boolean previousPauseOnLostFocus;
    private boolean pauseOnLostFocusChanged;
    private boolean executionPausedByServerState;
    private boolean offMainEpisodeCheckpointed;
    private ServerState lastCommittedServerState = ServerState.UNKNOWN;
    private Perspective previousPerspective;
    private boolean perspectiveChanged;
    private final ArrayList<EndCrystalEntity> ignoreCrystals = new ArrayList<>();
    public boolean drawingBow;
    public DoubleMineBlock normalMining, packetMining;
    private final MBlockPos posRender2 = new MBlockPos();
    private final MBlockPos posRender3 = new MBlockPos();
    private List<Pattern> signBreakPatterns = Collections.emptyList();
    private static final String KITBOT_NAME = "KitBot1";
    private static final double CENTER_SPEED_OVERRIDE = 0.6;
    private static final int CENTER_SPEED_RESTORE_RETRY_WINDOW_TICKS = 60;
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

    private enum ReconnectBaselineLeaseState {
        CAPTURED,
        INVALIDATED,
        CONSUMED
    }

    private record ReconnectBaselinePayload(
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
        double startX,
        double startY,
        double startZ,
        int blocksBroken,
        int blocksPlaced,
        boolean displayInfo,
        long lastCheckpointAt,
        boolean screenshotRequired,
        boolean screenshotCompleted,
        String screenshotFileName,
        StatsFinalizationPhase finalizationPhase,
        long printedAt,
        boolean printedToChat,
        boolean webhookSendCommitted,
        boolean apiSendCommitted,
        String finalizationReason
    ) {}

    private enum StatsFinalizationPhase {
        AWAITING_SCREENSHOT,
        SCREENSHOT_DONE_AWAITING_SEND,
        SEND_DONE_AWAITING_RETIRE
    }

    private enum StatsFinalizationResult {
        COMPLETED,
        PENDING_SCREENSHOT,
        FAILED
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

    // AES-256 encryption with SHA-256 key derivation
    private String decryptWebhook(String encryptedWebhook, String password) {
        try {
            // Derive a 256-bit (32 byte) key from the password using SHA-256
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] keyBytes = digest.digest(password.getBytes(StandardCharsets.UTF_8));

            // Add proper Base64 padding for encrypted webhook if needed
            String padded = encryptedWebhook;
            int padding = padded.length() % 4;
            if (padding > 0) {
                padded += "=".repeat(4 - padding);
            }

            byte[] encryptedBytes = Base64.getDecoder().decode(padded);

            // Create AES-256 cipher
            SecretKeySpec secretKey = new SecretKeySpec(keyBytes, 0, keyBytes.length, "AES");
            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.DECRYPT_MODE, secretKey);

            byte[] decrypted = cipher.doFinal(encryptedBytes);
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            // If decryption fails, treat the webhook as unencrypted
            THMAddon.LOG.warn("Failed to decrypt webhook, treating as unencrypted: " + e.getMessage());
            return encryptedWebhook;
        }
    }

    // AES-256 encryption with SHA-256 key derivation
    private String decryptAPI(String encryptedapi, String password) {
        try {
            // Derive a 256-bit (32 byte) key from the password using SHA-256
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] keyBytes = digest.digest(password.getBytes(StandardCharsets.UTF_8));

            // Add proper Base64 padding for encrypted data if needed
            String padded = encryptedapi;
            int padding = padded.length() % 4;
            if (padding > 0) {
                padded += "=".repeat(4 - padding);
            }

            byte[] encryptedBytes = Base64.getDecoder().decode(padded);

            // Create AES-256 cipher
            SecretKeySpec secretKey = new SecretKeySpec(keyBytes, 0, keyBytes.length, "AES");
            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.DECRYPT_MODE, secretKey);

            byte[] decrypted = cipher.doFinal(encryptedBytes);
            String result = new String(decrypted, StandardCharsets.UTF_8);

            // Validate result looks like a URL
            if (!result.startsWith("http://") && !result.startsWith("https://")) {
                THMAddon.LOG.warn("Decrypted API URL invalid - wrong password or corrupted data");
                return null;
            }

            return result;
        } catch (Exception e) {
            THMAddon.LOG.warn("Failed to decrypt API: " + e.getMessage());
            return null;
        }
    }

    private void sendToWebhook(String webhookUrl, String message) {
        new Thread(() -> {
            try {
                @SuppressWarnings("deprecation") URL url = new URL(webhookUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);

                // Create JSON payload for Discord webhook
                String json = "{\"content\": \"" + message.replace("\"", "\\\"") + "\"}";

                try (OutputStream os = conn.getOutputStream()) {
                    byte[] input = json.getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }

                int responseCode = conn.getResponseCode();
                if (responseCode == 204 || responseCode == 200) {
                    info("Successfully sent statistics to webhook!");
                } else {
                    THMAddon.LOG.warn("Webhook response code: " + responseCode);
                    warning("Failed to send to Webhook");
                }

                conn.disconnect();
            } catch (Exception e) {
                THMAddon.LOG.warn("Failed to send to webhook: " + e.getMessage());
            }
        }).start();
    }
    private void sendToAPI(String message, String password, String EncryptedAPI, String logType) {
        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                // Decrypt the API URL first
                String api = decryptAPI(EncryptedAPI, password);

                // Check if decryption succeeded
                if (api == null) {
                    THMAddon.LOG.warn("Failed to decrypt API URL - check your encryption key");
                    return;
                }

                @SuppressWarnings("deprecation") URL url = new URL(api);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);

                String json = "{\"content\": \"" + message.replace("\"", "\\\"") + "\"}";

                try (OutputStream os = conn.getOutputStream()) {
                    byte[] input = json.getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                    os.flush();
                }

                int responseCode = conn.getResponseCode();

                try (InputStream is = responseCode >= 400 ? conn.getErrorStream() : conn.getInputStream()) {
                    if (is != null) {
                        is.readAllBytes();
                    }
                }

                if (responseCode == 204 || responseCode == 200) {
                    info("Successfully sent %s to API!", logType);
                } else {
                    THMAddon.LOG.warn("API response code: " + responseCode);
                    warning("Failed to send to API");
                }
            } catch (Exception e) {
                warning("Failed to send to API");
            } finally {
                if (conn != null) conn.disconnect();
            }
        }).start();
    }

    private void sendStatusLog() {
        if (!statuslog.get() || mc.player == null || dir == null) return;

        if (isNot6B6T()) {
            warning("Status not sent. You are not on 6B6T");
            return;
        }
        if (!isOnMainHighway()) {
            warning("Status wont get send. You are not on a main highway");
            return;
        }
        if (THMSystem.get() == null || Objects.equals(THMSystem.get().getHash(), "SetYourHash") || Objects.equals(THMSystem.get().getHash(), "")) {
            warning("Status not sent. No Hash set.");
            return;
        }

        String playerName = mc.player.getName().getLiteralString();
        String axis = dir.toString();

        String statusMessage = String.format("%s:%s:%s:%d:%d:%s",
            THMSystem.get().getHash(),
            playerName,
            axis,
            blocksBroken,
            blocksPlaced,
            generateTimestamp()
        );

        sendToAPI(statusMessage, getPassword(), getAPIStatus(), "status");
    }

    private ServerState getCommittedServerState() {
        return ServerStatusHandler.getInstance().getCommittedState();
    }

    private boolean isExecutionAllowedOnCurrentServer(ServerState committedState) {
        return committedState == ServerState.MAIN_SERVER;
    }

    private void trackServerExecutionState(ServerState committedState) {
        if (committedState == lastCommittedServerState) return;

        ServerState previousState = lastCommittedServerState;
        lastCommittedServerState = committedState;

        if (committedState == ServerState.MAIN_SERVER) {
            offMainEpisodeCheckpointed = false;
            return;
        }

        if (previousState == ServerState.MAIN_SERVER && !offMainEpisodeCheckpointed && hasActiveInMemoryStatsSession() && !statsSessionTerminalOrFinalizing) {
            persistCurrentStatsSession(StatsSessionState.OPEN, true, 0L, "server-state-left-main");
            offMainEpisodeCheckpointed = true;
        }
    }

    private void pauseExecutionForServerState(ServerState committedState) {
        if (input != null) input.stop();
        executionPausedByServerState = true;
    }

    @Override
    public void onActivate() {
        if (mc.player == null || mc.world == null) return;
        if (!Utils.canUpdate()) return;
        ReconnectResumeContext reconnectResume = reconnectResumeContext;
        reconnectResumeContext = null;
        boolean reconnectActivation = reconnectResume != null;
        clearKitbotOrderTracking("module-activate");

        if (centerSpeedMonitorRecoveryOwned && !resumeStatsSessionOnNextActivate) {
            restockDebug("Center/Speed stale monitor recovery baseline cleared on activate (lastReason=%s).", centerSpeedLastReason);
            clearCenterSpeedOwnership("activate-stale-monitor-baseline");
        }

        if (!suppressThmHwyMonitorSync) syncThmHwyMonitorOnActivate();
        loadStatsCacheFromDisk();

        previousPauseOnLostFocus = mc.options.pauseOnLostFocus;
        pauseOnLostFocusChanged = previousPauseOnLostFocus;
        if (pauseOnLostFocusChanged) togglePauseOnLostFocus(false);

        updateVariables();
        updateSignBreakRegex();
        dir = reconnectActivation ? reconnectResume.direction() : HorizontalDirection.get(mc.player.getYaw());
        leftDir = dir.rotateLeftSkipOne();
        rightDir = leftDir.opposite();

        blockPosProvider = dir.diagonal ? new DiagonalBlockPosProvider() : new StraightBlockPosProvider();
        state = State.Forward;
        if (!reconnectActivation) setState(State.Center);
        lastBreakingPos.set(0, 0, 0);
        if (isPendingPrintStatsSession(statsCacheSnapshot)) {
            StatsFinalizationResult pendingPrintResult = completeFinalizationRecord(statsCacheSnapshot, "pending-print-on-activate", true);
            if (pendingPrintResult == StatsFinalizationResult.FAILED) {
                warning("Unable to complete pending HighwayBuilder statistics safely; keeping session pending.");
                toggle();
                return;
            }
            if (pendingPrintResult == StatsFinalizationResult.COMPLETED) {
                statsCacheSnapshot = null;
            }
        }

        boolean resumedStatsSession = isResumableStatsSession(statsCacheSnapshot);
        if (resumedStatsSession) {
            restoreStatsFromCache(statsCacheSnapshot, resumeStatsSessionOnNextActivate ? "monitor-resume" : "cache-resume");
            markArtifactConsumed(statsCacheSnapshot);
        }
        else startFreshStatsSession();

        resumeStatsSessionOnNextActivate = false;
        monitorPauseDeactivateArmed = false;
        sentLagMessage = false;
        suspended = reconnectActivation;
        statusLogTimer = 6000;

        restockTask.complete();

        if (blocksPerTick.get() > 1 && rotation.get().mine)
            warning("With rotations enabled, you can break at most 1 block per tick.");
        if (placementsPerTick.get() > 1 && rotation.get().place)
            warning("With rotations enabled, you can place at most 1 block per tick.");
        //all modules that may cause error now print errors/warnings
        if (Modules.get().get(InstantRebreak.class).isActive())
            warning("It's recommended to disable the Instant Rebreak module and instead use the 'instantly-rebreak-echests' setting to avoid errors.");
        if (Modules.get().get(SpeedMine.class).isActive())
            warning("It's recommended to disable the Speedmine module and instead use the 'fast-break' setting to avoid errors.");
        if (Modules.get().get(Speed.class).isActive() && dir.diagonal)
            warning("It's recommended to disable the Speed module to avoid misalignment on diagonals.");
        if (Modules.get().get(Timer.class).isActive() && dir.diagonal)
            warning("It's recommended to disable the Timer module to avoid misalignment on diagonals.");
        //it could be tested to print different warnings depending on the amount of blocks being broken per tick but that would need much testing and wouldn't be reliable
        if (Modules.get().get(NoGhostBlocks.class).isActive())
            warning("It's recommended to disable the NoGhostBlocks module to avoid packet kicks and wrong statistics.");
        if (!Modules.get().get(Velocity.class).isActive()) {
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
        if (!Modules.get().get(AntiDrop.class).isActive() && antidrop.get()) { Modules.get().get(AntiDrop.class).toggle();}

        THMSystem thmSystem = THMSystem.get();
        if (thmSystem != null) {
            int playerY = (int) mc.player.getY();

            if (thmSystem.mode.get() == THMSystem.Mode.HighwayBuilding) {
                if (playerY != 120) {
                    warning("You are not on Y Level 120!!");
                    toggle();
                }
            }
            if (thmSystem.mode.get() == THMSystem.Mode.HighwayDigging) {
                if (playerY != 119) {
                    warning("You are not on Y Level 119!!");
                    toggle();
                }
            }
        }



    }
    @Override
    public void onDeactivate() {
        boolean isMonitorPauseDeactivate = monitorPauseDeactivateArmed;
        boolean isReconnectFailureDeactivate = reconnectFailureDeactivateArmed;
        monitorPauseDeactivateArmed = false;
        reconnectFailureDeactivateArmed = false;
        clearKitbotOrderTracking(isMonitorPauseDeactivate ? "monitor-pause-deactivate" : "module-deactivate");

        if (!suppressThmHwyMonitorSync) syncThmHwyMonitorOnDeactivate();

        Timer timer = Modules.get().get(Timer.class);
        if (!isMonitorPauseDeactivate && !isReconnectFailureDeactivate) {
            if (timer != null) timer.setOverride(Timer.OFF);
            invalidateReconnectBaseline("non-monitor-deactivate");
            restoreCenterSpeedIfOwned("module-deactivate");
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

        if (pauseOnLostFocusChanged) {
            togglePauseOnLostFocus(previousPauseOnLostFocus);
            pauseOnLostFocusChanged = false;
        }

        if (mc.player == null || mc.world == null || !Utils.canUpdate()) {
            if (hasActiveInMemoryStatsSession()) {
                if (isMonitorPauseDeactivate) {
                    persistCurrentStatsSession(StatsSessionState.OPEN, true, 0L, "monitor-pause-deactivate-no-world");
                } else {
                    StatsArtifactSnapshot finalizationRecord = persistFinalizationRecord(createFinalizationRecord("deactivate-pending-print-no-world"), "deactivate-pending-print-no-world");
                    if (finalizationRecord == null) warning("Unable to persist pending HighwayBuilder statistics safely before deactivate.");
                }
            } else {
                THMAddon.LOG.info("[highway-stats-cache] skipped deactivate persistence because no active in-memory stats session exists.");
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

        if (!hasActiveInMemoryStatsSession()) {
            THMAddon.LOG.info("[highway-stats-cache] skipped deactivate persistence because no active in-memory stats session exists.");
            return;
        }

        if (isMonitorPauseDeactivate) {
            persistCurrentStatsSession(StatsSessionState.OPEN, true, 0L, "monitor-pause-deactivate");
            return;
        }

        StatsArtifactSnapshot finalizationRecord = persistFinalizationRecord(createFinalizationRecord("deactivate"), "deactivate-finalization-record");
        if (finalizationRecord == null) {
            warning("Unable to persist final HighwayBuilder statistics safely before deactivate.");
            return;
        }

        StatsFinalizationResult finalizationResult = completeFinalizationRecord(finalizationRecord, "printed-on-deactivate", true);
        if (finalizationResult == StatsFinalizationResult.FAILED) {
            warning("Unable to complete HighwayBuilder statistics safely during deactivate; keeping durable finalization record.");
        }

    }

    private void syncThmHwyMonitorOnActivate() {
        if (!manageThmHwyMonitor.get()) return;

        THMHwyMonitor monitor = Modules.get().get(THMHwyMonitor.class);
        if (monitor == null || monitor.isActive()) return;

        monitor.toggle();
    }

    private void syncThmHwyMonitorOnDeactivate() {
        if (!manageThmHwyMonitor.get()) return;

        disableThmHwyMonitorIfActive();
    }

    private void disableThmHwyMonitorIfActive() {
        THMHwyMonitor monitor = Modules.get().get(THMHwyMonitor.class);
        if (monitor == null || !monitor.isActive()) return;

        monitor.toggle();
    }

    public void disableForMonitorRealignPause() {
        if (!isActive()) return;

        resumeStatsSessionOnNextActivate = true;
        monitorPauseDeactivateArmed = true;
        if (!statsSessionTerminalOrFinalizing) persistCurrentStatsSession(StatsSessionState.OPEN, true, 0L, "monitor-pause-request");
        suppressThmHwyMonitorSync = true;
        try {
            toggle();
        } finally {
            suppressThmHwyMonitorSync = false;
        }
    }

    public HorizontalDirection getWorkingDirection() {
        return dir;
    }

    public boolean resumeFromReconnect(HorizontalDirection lockedDirection, long generation) {
        if (lockedDirection == null || generation <= 0L) return false;
        if (mc.player == null || mc.world == null || !Utils.canUpdate()) return false;
        if (isActive()) return false;

        reconnectResumeContext = new ReconnectResumeContext(lockedDirection, generation);
        suppressThmHwyMonitorSync = true;
        try {
            toggle();
        } finally {
            suppressThmHwyMonitorSync = false;
        }

        if (!isActive()) {
            reconnectResumeContext = null;
            return false;
        }

        if (dir != lockedDirection) {
            disableForReconnectResumeFailure();
            return false;
        }

        if (!consumeReconnectBaseline(generation)) {
            disableForReconnectResumeFailure();
            return false;
        }

        setState(State.Center);
        suspended = false;
        return isActive() && dir == lockedDirection;
    }

    public boolean prepareForMonitorReconnectPause(long generation) {
        if (generation <= 0L) return false;
        if (hasUsableReconnectBaselineLease(generation)) return true;
        if (reconnectBaselineLease != null && reconnectBaselineLease.generation() == generation) return false;
        if (centerSpeedOverrideActive) return false;

        ReconnectBaselinePayload payload = captureReconnectBaselinePayload();
        if (payload == null) return false;

        reconnectBaselineLease = new ReconnectBaselineLease(generation, ReconnectBaselineLeaseState.CAPTURED, payload);
        return true;
    }

    public boolean restoreCenterSpeedBaselineForFailedReconnect(long generation) {
        if (generation <= 0L) return false;
        if (reconnectBaselineLease == null || reconnectBaselineLease.generation() != generation) return false;
        if (reconnectBaselineLease.state() == ReconnectBaselineLeaseState.INVALIDATED) return false;
        return consumeReconnectBaseline(generation);
    }

    public void refreshReconnectBaselineValidity(long activeGeneration) {
        if (reconnectBaselineLease == null || reconnectBaselineRestoreInProgress) return;
        if (reconnectBaselineLease.state() != ReconnectBaselineLeaseState.CAPTURED) return;

        if (activeGeneration <= 0L || reconnectBaselineLease.generation() != activeGeneration) {
            invalidateReconnectBaseline("generation-changed");
            return;
        }

        if (!reconnectBaselineMatchesLiveState(reconnectBaselineLease.payload())) {
            invalidateReconnectBaseline("live-state-mismatch");
        }
    }

    public void disableForReconnectSafetyStop() {
        if (!isActive()) return;
        reconnectFailureDeactivateArmed = true;
        suppressThmHwyMonitorSync = true;
        try {
            toggle();
        } finally {
            suppressThmHwyMonitorSync = false;
        }
    }

    private void disableForReconnectResumeFailure() {
        if (!isActive()) return;
        reconnectFailureDeactivateArmed = true;
        suppressThmHwyMonitorSync = true;
        try {
            toggle();
        } finally {
            suppressThmHwyMonitorSync = false;
        }
    }

    @Override
    public void error(String message, Object... args) {
        super.error(message, args);
        THMHwyMonitor.signalNonRestartHardFailFromHighwayBuilder();
        toggle();

        if (disconnectOnToggle.get()) {
            disconnect(message, args);
        }
    }

    private void errorEarly(String message, Object... args) {
        super.error(message, args);
        THMHwyMonitor.signalNonRestartHardFailFromHighwayBuilder();

        displayInfo = false;
        toggle();
    }

    private void errorRestart(String message, Object... args) {
        super.error(message, args);
        THMHwyMonitor.signalRestartHardFailFromHighwayBuilder();
        displayInfo = false;
        toggle();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        maybeCheckpointStatsSession();

        ServerState committedState = getCommittedServerState();
        trackServerExecutionState(committedState);
        if (!isExecutionAllowedOnCurrentServer(committedState)) {
            pauseExecutionForServerState(committedState);
            return;
        }

        executionPausedByServerState = false;
        tickDeferredCenterSpeedRestore();

        if (statuslog.get()) {
            statusLogTimer++;
            if (statusLogTimer >= 6000) { // 5 minutes
                sendStatusLog();
                statusLogTimer = 0;
            }
        }

        if (dir == null) {
            onActivate();
            return;
        }

        if (suspended) {
            if (inventory && Utils.canUpdate()) {
                updateVariables();
                suspended = false;
            }
            else return;
        }

        if (width.get() < 3 && dir.diagonal) {
            errorEarly("Diagonal highways less than 3 blocks wide are not supported, please change the width setting.");
            return;
        }

        if (
            (Modules.get().get(AutoEat.class) != null && Modules.get().get(AutoEat.class).eating)
                || (Modules.get().get(AutoGap.class) != null && Modules.get().get(AutoGap.class).isEating())
                || (Modules.get().get(KillAura.class) != null && Modules.get().get(KillAura.class).attacking)
                || (Modules.get().get(OffhandManager.class) != null && Modules.get().get(OffhandManager.class).isEating())
        ) {
            input.stop();
            return;
        }
        if (pauseOnLag.get() && TickRate.INSTANCE.getTimeSinceLastTick() > 1.5f) {
            if (!sentLagMessage) {
                error("Server isn't responding, pausing.");
                input.stop();
                sentLagMessage = true;
                return;
            }

            if (sentLagMessage) {
                if (TickRate.INSTANCE.getTickRate() > resumeTPS.get()) {
                    sentLagMessage = false;
                    return;
                }
            }
        }

        count = 0;

        if (mc.player.getY() < start.y - 0.5) setState(State.ReLevel); // don't let the current state keep ticking, switch to re-levelling straight away
        tickDoubleMine();
        maybeQueueFoodRestock();
        state.tick(this);

        if (breakTimer > 0) breakTimer--;
        if (placeTimer > 0) placeTimer--;
    }

    @EventHandler
    private void onPacket(PacketEvent.Receive event) {
        if (event.packet instanceof InventoryS2CPacket p) {
            if (p.syncId() == 0 && suspended)
                inventory = true;
            else
                this.syncId = p.syncId();
        }
    }


    @EventHandler
    private void onMessageReceive(ReceiveMessageEvent event) {
        if (!isExecutionAllowedOnCurrentServer(getCommittedServerState())) return;
        if (state != State.KitbotOrder || kitbotTpHandled) return;

        String msg = event.getMessage().getString();
        if (msg.contains(KITBOT_NAME + " wants to teleport to you")) {
            ChatUtils.sendPlayerMsg("/tpy " + KITBOT_NAME);
            info("Accepted " + KITBOT_NAME + " teleport request.");
            kitbotTpHandled = true;
        }
    }

    @EventHandler
    private void onGameLeave(GameLeftEvent event) {
        notifyDesktop(notifyDisconnect, "THM Highway Builder", "Disconnected while Highway Builder was active.");
        if (hasActiveInMemoryStatsSession() && !statsSessionTerminalOrFinalizing) {
            persistCurrentStatsSession(StatsSessionState.OPEN, true, 0L, "game-leave");
        }
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
        if (suspended || blockPosProvider == null) return; // prevents a fascinating crash

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
                render(event, blockPosProvider.getBlockade(true, blockadeType.get()), mBlockPos -> canMine(mBlockPos, true), true);
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
                render(event, blockPosProvider.getBlockade(false, blockadeType.get()), mBlockPos -> canPlace(mBlockPos, false), false);
            }
        }
    }

    private void render(Render3DEvent event, MBPIterator it, Predicate<MBlockPos> predicate, boolean mine) {
        Color sideColor = mine ? renderMineSideColor.get() : renderPlaceSideColor.get();
        Color lineColor = mine ? renderMineLineColor.get() : renderPlaceLineColor.get();
        ShapeMode shapeMode = mine ? renderMineShape.get() : renderPlaceShape.get();

        for (MBlockPos pos : it) {
            posRender2.set(pos);

            if (predicate.test(posRender2)) {
                int excludeDir = 0;

                for (Direction side : Direction.values()) {
                    posRender3.set(posRender2).add(side.getOffsetX(), side.getOffsetY(), side.getOffsetZ());

                    it.save();
                    for (MBlockPos p : it) {
                        if (p.equals(posRender3) && predicate.test(p)) excludeDir |= Dir.get(side);
                    }
                    it.restore();
                }

                event.renderer.box(posRender2.getBlockPos(), sideColor, lineColor, shapeMode, excludeDir);
            }
        }
    }

    private void updateVariables() {
        prevInput = mc.player.input;
        mc.player.input = input = new CustomPlayerInput();

        placeTimer = breakTimer = count = syncId = 0;
        ignoreCrystals.clear();

        normalMining = null;
        packetMining = null;
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

    private boolean isSignBlock(BlockState state) {
        Block block = state.getBlock();
        return block instanceof SignBlock || block instanceof HangingSignBlock;
    }

    private String getSignText(BlockPos pos) {
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
        this.lastState = lastState;
        this.state = state;

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
                restockDebug("RestockTask queue drained; deferring blockade teardown before returning to Forward.");
            }
            restockTask.deferBlockadeTeardown();
            clearKitbotOrderTracking("restock-sequence-finished-with-teardown");
            setState(State.Forward);
            return;
        }

        if (restockDebugLog.get()) {
            restockDebug("RestockTask finished without pending tasks or teardown requirement; returning to Forward.");
        }
        restockTask.finishSequence();
        clearKitbotOrderTracking("restock-sequence-finished");
        setState(State.Forward);
    }

    private void clearKitbotOrderTracking(String reason) {
        if (!kitbotOrderInFlight) return;

        kitbotOrderInFlight = false;
        kitbotOrderBaselineShulkerCount = 0;
        kitbotOrderExpectedShulkerGain = 0;
        kitbotOrderSentAtAge = 0;
        kitbotOrderRetryCount = 0;
        if (restockDebugLog.get()) restockDebug("KitbotOrder cleared in-flight order tracking (%s).", reason);
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
        return BlockUtils.canBreak(pos.getBlockPos(), state) && (mineBlocksToPlace || !blocksToPlace.get().contains(state.getBlock()));
    }

    private boolean canPlace(MBlockPos pos, boolean liquids) {
        if (pos.getBlockPos().getSquaredDistance(mc.player.getEyePos()) > placeRange.get() * placeRange.get()) return false;
        return liquids ? !pos.getState().getFluidState().isEmpty() : BlockUtils.canPlace(pos.getBlockPos());
    }

    private void restockDebug(String message, Object... args) {
        if (!restockDebugLog.get()) return;
        THMAddon.LOG.info("[restock-debug] " + String.format(message, args));
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
            case Center, ThrowOutTrash, Restock, PlaceShulkerBlockade, MineShulkerBlockade, PlaceEChestBlockade, MineEChestBlockade, MineEnderChests, KitbotOrder -> true;
            default -> false;
        };
    }

    private String stateName(State state) {
        return state == null ? "null" : state.name();
    }

    private boolean ensureCenterSpeedSnapshotCaptured(String reason) {
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
        if (!ensureCenterSpeedSnapshotCaptured(reason)) return;

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

    private boolean hasUsableReconnectBaselineLease(long generation) {
        return reconnectBaselineLease != null
            && reconnectBaselineLease.generation() == generation
            && reconnectBaselineLease.state() == ReconnectBaselineLeaseState.CAPTURED
            && reconnectBaselineMatchesLiveState(reconnectBaselineLease.payload());
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

    private boolean consumeReconnectBaseline(long generation) {
        if (reconnectBaselineLease == null) return false;
        if (reconnectBaselineLease.generation() != generation) return false;
        if (reconnectBaselineLease.state() != ReconnectBaselineLeaseState.CAPTURED) return false;

        Speed speed = Modules.get().get(Speed.class);
        Timer timer = Modules.get().get(Timer.class);
        if (speed == null) return false;

        ReconnectBaselinePayload payload = reconnectBaselineLease.payload();
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

        if (!reconnectBaselineMatchesLiveState(payload)) return false;

        reconnectBaselineLease = new ReconnectBaselineLease(
            generation,
            ReconnectBaselineLeaseState.CONSUMED,
            payload
        );
        return true;
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
    }

    private boolean hasActiveInMemoryStatsSession() {
        return start != null && activeStatsSessionId != null && !activeStatsSessionId.isBlank();
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
            THMAddon.LOG.info("[highway-stats-cache] ignored consumed artifact kind={} session={} generation={}",
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

        THMAddon.LOG.info("[highway-stats-cache] loaded reason=startup kind={} session={} generation={} state={} resumeAllowed={} broken={} placed={}",
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
        nextStatsCheckpointAtMs = snapshot.lastCheckpointAt() <= 0
            ? System.currentTimeMillis() + STATS_CHECKPOINT_INTERVAL_MS
            : snapshot.lastCheckpointAt() + STATS_CHECKPOINT_INTERVAL_MS;

        THMAddon.LOG.info("[highway-stats-cache] restored reason={} kind={} session={} generation={} state={} broken={} placed={} start=({}, {}, {})",
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
            THMAddon.LOG.info("[highway-stats-cache] skipped OPEN persist reason={} session={} because finalization is armed.",
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

        return new StatsArtifactSnapshot(
            kind,
            activeStatsSessionId,
            nextStatsGeneration(),
            state,
            resumeAllowed,
            start.x,
            start.y,
            start.z,
            blocksBroken,
            blocksPlaced,
            displayInfo,
            checkpointAt,
            false,
            false,
            "",
            null,
            printedAt,
            printedAt > 0L,
            false,
            false,
            ""
        );
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
            snapshot.startX(),
            snapshot.startY(),
            snapshot.startZ(),
            snapshot.blocksBroken(),
            snapshot.blocksPlaced(),
            snapshot.displayInfo(),
            System.currentTimeMillis(),
            snapshot.screenshotRequired(),
            snapshot.screenshotCompleted(),
            snapshot.screenshotFileName(),
            snapshot.finalizationPhase(),
            printedAt,
            printedToChat,
            webhookSendCommitted,
            apiSendCommitted,
            finalizationReason
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
            finalizationReason
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

            THMAddon.LOG.warn("[highway-stats-cache] canonical recovery succeeded but shadow cleanup failed; staying in memory-retry mode until shadow is cleared.");
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

            THMAddon.LOG.info("[highway-stats-cache] saved reason={} kind={} session={} generation={} {}->{} resumeAllowed={} broken={} placed={} printedAt={}",
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
            THMAddon.LOG.warn("[highway-stats-cache] save failed reason={} session={} message={}",
                reason,
                shortSessionId(snapshot.sessionId()),
                e.getMessage()
            );
            return false;
        }
    }

    private boolean closeAndRetireCurrentStatsSession(String reason) {
        if (!hasActiveInMemoryStatsSession()) return true;
        return closeAndRetireStatsSession(createCurrentStatsSnapshot(StatsArtifactKind.CANONICAL, StatsSessionState.CLOSED, false, System.currentTimeMillis()), reason);
    }

    private boolean closeAndRetireStatsSession(StatsArtifactSnapshot snapshot, String reason) {
        if (snapshot == null) return true;

        boolean deletedCanonical = deleteStatsArtifact(resolveCanonicalStatsArtifactPath(), reason + "-canonical-delete");
        boolean deletedShadow = deleteStatsArtifact(resolveShadowStatsArtifactPath(), reason + "-shadow-delete");

        if (!deletedCanonical || !deletedShadow) {
            THMAddon.LOG.warn("[highway-stats-cache] retire failed session={} reason={}; canonical/shadow artifacts were not fully removed, leaving finalization record intact.",
                shortSessionId(snapshot.sessionId()),
                reason
            );
            return false;
        }

        boolean deletedFinalization = deleteStatsArtifact(resolveFinalizationRecordPath(), reason + "-finalization-delete");
        if (!deletedFinalization) {
            THMAddon.LOG.warn("[highway-stats-cache] retire incomplete session={} reason={}; finalization record remains for safe recovery.",
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
            THMAddon.LOG.info("[highway-stats-cache] deleted reason={} path={}", reason, path);
            return true;
        } catch (IOException e) {
            THMAddon.LOG.warn("[highway-stats-cache] delete failed reason={} path={} message={}", reason, path, e.getMessage());
            return false;
        }
    }

    private StatsArtifactLoadResult loadStatsArtifact(Path path, StatsArtifactKind kind) {
        if (!Files.exists(path)) return new StatsArtifactLoadResult(null, false, false);

        List<String> lines;
        try {
            lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            THMAddon.LOG.warn("[highway-stats-cache] transient read failure kind={} path={} message={}", kind, path, e.getMessage());
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
            THMAddon.LOG.warn("[highway-stats-cache] unsupported artifact version kind={} path={} version={}", kind, path, version);
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
            THMAddon.LOG.warn("[highway-stats-cache] transient decode failure kind={} path={} message={}", kind, path, e.getMessage());
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

        if (snapshot.kind() == StatsArtifactKind.FINALIZATION) {
            if (snapshot.finalizationPhase() == null) return false;
            if (snapshot.screenshotRequired() && (snapshot.screenshotFileName() == null || snapshot.screenshotFileName().isBlank())) return false;
            if (!snapshot.screenshotRequired() && snapshot.screenshotCompleted()) return false;
            if (!snapshot.screenshotRequired() && snapshot.finalizationPhase() == StatsFinalizationPhase.AWAITING_SCREENSHOT) return false;
            if (snapshot.screenshotCompleted() && snapshot.finalizationPhase() == StatsFinalizationPhase.AWAITING_SCREENSHOT) return false;
            if (snapshot.screenshotCompleted() && !snapshot.printedToChat()) return false;
            if (snapshot.printedToChat() && snapshot.printedAt() <= 0L) return false;
            if (snapshot.screenshotRequired() && snapshot.printedToChat() && !snapshot.screenshotCompleted()) return false;
            if (snapshot.finalizationPhase() == StatsFinalizationPhase.SEND_DONE_AWAITING_RETIRE
                && snapshot.screenshotRequired()
                && !snapshot.screenshotCompleted()) {
                return false;
            }
        } else {
            if (snapshot.screenshotRequired()) return false;
            if (snapshot.screenshotCompleted()) return false;
            if (snapshot.finalizationPhase() != null) return false;
            if (snapshot.screenshotFileName() != null && !snapshot.screenshotFileName().isBlank()) return false;
        }

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
        out.append("meta|kind|").append(snapshot.kind().name()).append('
');
        out.append("meta|sessionId|").append(snapshot.sessionId()).append('
');
        out.append("meta|generation|").append(snapshot.generation()).append('
');
        out.append("meta|state|").append(snapshot.state().name()).append('
');
        out.append("meta|resumeAllowed|").append(snapshot.resumeAllowed()).append('
');
        out.append("meta|startX|").append(snapshot.startX()).append('
');
        out.append("meta|startY|").append(snapshot.startY()).append('
');
        out.append("meta|startZ|").append(snapshot.startZ()).append('
');
        out.append("meta|blocksBroken|").append(snapshot.blocksBroken()).append('
');
        out.append("meta|blocksPlaced|").append(snapshot.blocksPlaced()).append('
');
        out.append("meta|displayInfo|").append(snapshot.displayInfo()).append('
');
        out.append("meta|lastCheckpointAt|").append(snapshot.lastCheckpointAt()).append('
');
        out.append("meta|screenshotRequired|").append(snapshot.screenshotRequired()).append('
');
        out.append("meta|screenshotCompleted|").append(snapshot.screenshotCompleted()).append('
');
        out.append("meta|screenshotFileName|").append(snapshot.screenshotFileName() == null ? "" : snapshot.screenshotFileName()).append('
');
        out.append("meta|finalizationPhase|").append(snapshot.finalizationPhase() == null ? "" : snapshot.finalizationPhase().name()).append('
');
        out.append("meta|printedAt|").append(snapshot.printedAt()).append('
');
        out.append("meta|printedToChat|").append(snapshot.printedToChat()).append('
');
        out.append("meta|webhookSendCommitted|").append(snapshot.webhookSendCommitted()).append('
');
        out.append("meta|apiSendCommitted|").append(snapshot.apiSendCommitted()).append('
');
        out.append("meta|finalizationReason|").append(snapshot.finalizationReason() == null ? "" : snapshot.finalizationReason()).append('
');
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
            String[] parts = line.split("\|", 3);
            if (parts.length < 3 || !"meta".equals(parts[0])) continue;
            meta.put(parts[1], parts[2]);
        }

        StatsSessionState state = parseStatsSessionState(meta.get("state"));
        if (state == null) throw new IllegalArgumentException("invalid-state");

        return new StatsArtifactSnapshot(
            kind,
            meta.getOrDefault("sessionId", ""),
            parseLongSafe(meta.get("generation"), 0L),
            state,
            Boolean.parseBoolean(meta.getOrDefault("resumeAllowed", "false")),
            parseDoubleSafe(meta.get("startX"), 0.0),
            parseDoubleSafe(meta.get("startY"), 0.0),
            parseDoubleSafe(meta.get("startZ"), 0.0),
            parseIntSafe(meta.get("blocksBroken"), 0),
            parseIntSafe(meta.get("blocksPlaced"), 0),
            Boolean.parseBoolean(meta.getOrDefault("displayInfo", "true")),
            parseLongSafe(meta.get("lastCheckpointAt"), 0L),
            Boolean.parseBoolean(meta.getOrDefault("screenshotRequired", "false")),
            Boolean.parseBoolean(meta.getOrDefault("screenshotCompleted", "false")),
            meta.getOrDefault("screenshotFileName", ""),
            parseStatsFinalizationPhase(meta.get("finalizationPhase")),
            parseLongSafe(meta.get("printedAt"), 0L),
            Boolean.parseBoolean(meta.getOrDefault("printedToChat", "false")),
            Boolean.parseBoolean(meta.getOrDefault("webhookSendCommitted", "false")),
            Boolean.parseBoolean(meta.getOrDefault("apiSendCommitted", "false")),
            meta.getOrDefault("finalizationReason", "")
        );
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

    private RetiredStatsReportSnapshot captureRetiredStatsReportFromLiveSession() {
        if (!hasActiveInMemoryStatsSession()) return null;
        return new RetiredStatsReportSnapshot(activeStatsSessionId, activeStatsGeneration, start, blocksBroken, blocksPlaced);
    }

    private StatsArtifactSnapshot createFinalizationRecord(String reason) {
        if (!hasActiveInMemoryStatsSession()) return null;
        long generation = nextStatsGeneration();
        boolean screenshotRequired = autoScreenshotStatistics.get();
        String screenshotFileName = screenshotRequired ? buildStatsScreenshotFileName(activeStatsSessionId, generation) : "";
        StatsFinalizationPhase phase = screenshotRequired
            ? StatsFinalizationPhase.AWAITING_SCREENSHOT
            : StatsFinalizationPhase.SCREENSHOT_DONE_AWAITING_SEND;
        return new StatsArtifactSnapshot(
            StatsArtifactKind.FINALIZATION,
            activeStatsSessionId,
            generation,
            StatsSessionState.PENDING_PRINT,
            false,
            start.x,
            start.y,
            start.z,
            blocksBroken,
            blocksPlaced,
            displayInfo,
            System.currentTimeMillis(),
            screenshotRequired,
            false,
            screenshotFileName,
            phase,
            0L,
            false,
            false,
            false,
            reason == null ? "" : reason
        );
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
        boolean screenshotCompleted,
        StatsFinalizationPhase finalizationPhase,
        long printedAt,
        boolean printedToChat,
        boolean webhookCommitted,
        boolean apiCommitted,
        String reason
    ) {
        if (snapshot == null) return null;
        StatsArtifactSnapshot updated = new StatsArtifactSnapshot(
            StatsArtifactKind.FINALIZATION,
            snapshot.sessionId(),
            nextStatsGenerationAfter(snapshot.generation()),
            StatsSessionState.PENDING_PRINT,
            false,
            snapshot.startX(),
            snapshot.startY(),
            snapshot.startZ(),
            snapshot.blocksBroken(),
            snapshot.blocksPlaced(),
            snapshot.displayInfo(),
            System.currentTimeMillis(),
            snapshot.screenshotRequired(),
            screenshotCompleted,
            snapshot.screenshotFileName(),
            finalizationPhase,
            printedAt,
            printedToChat,
            webhookCommitted,
            apiCommitted,
            snapshot.finalizationReason()
        );
        return persistFinalizationRecord(updated, reason) != null ? updated : null;
    }

    private StatsFinalizationResult completeFinalizationRecord(StatsArtifactSnapshot snapshot, String reason, boolean allowPrinting) {
        if (snapshot == null) return StatsFinalizationResult.COMPLETED;

        RetiredStatsReportSnapshot report = createRetiredStatsReportSnapshot(snapshot);
        if (report == null) return StatsFinalizationResult.FAILED;

        StatsArtifactSnapshot working = snapshot;
        if (working.screenshotRequired() && working.finalizationPhase() == StatsFinalizationPhase.AWAITING_SCREENSHOT) {
            Path expectedScreenshot = resolveStatsScreenshotPath(working.screenshotFileName());
            if (isValidStatsScreenshotPath(expectedScreenshot)) {
                working = updateFinalizationRecord(
                    working,
                    true,
                    StatsFinalizationPhase.SCREENSHOT_DONE_AWAITING_SEND,
                    System.currentTimeMillis(),
                    true,
                    working.webhookSendCommitted(),
                    working.apiSendCommitted(),
                    reason + "-recovered-screenshot"
                );
                if (working == null) return StatsFinalizationResult.FAILED;
            } else {
                if (!allowPrinting) return StatsFinalizationResult.PENDING_SCREENSHOT;
                if (!tryPrintStatsToChat(working.sessionId(), report.startPos(), report.blocksBroken(), report.blocksPlaced(), reason)) {
                    return StatsFinalizationResult.FAILED;
                }
                scheduleStatsProofScreenshot(working.sessionId(), working.screenshotFileName(), reason);
                return StatsFinalizationResult.PENDING_SCREENSHOT;
            }
        }

        if (!working.screenshotRequired() && !working.printedToChat()) {
            if (!allowPrinting) return StatsFinalizationResult.FAILED;
            if (!tryPrintStatsToChat(working.sessionId(), report.startPos(), report.blocksBroken(), report.blocksPlaced(), reason)) {
                return StatsFinalizationResult.FAILED;
            }
            working = updateFinalizationRecord(
                working,
                false,
                StatsFinalizationPhase.SCREENSHOT_DONE_AWAITING_SEND,
                System.currentTimeMillis(),
                true,
                working.webhookSendCommitted(),
                working.apiSendCommitted(),
                reason + "-printed-no-screenshot"
            );
            if (working == null) return StatsFinalizationResult.FAILED;
        }

        if (working.finalizationPhase() == StatsFinalizationPhase.SCREENSHOT_DONE_AWAITING_SEND) {
            working = commitAndSendFinalExternalStats(working, report, reason);
            if (working == null) return StatsFinalizationResult.FAILED;
            working = updateFinalizationRecord(
                working,
                working.screenshotCompleted(),
                StatsFinalizationPhase.SEND_DONE_AWAITING_RETIRE,
                working.printedAt(),
                working.printedToChat(),
                working.webhookSendCommitted(),
                working.apiSendCommitted(),
                reason + "-send-complete"
            );
            if (working == null) return StatsFinalizationResult.FAILED;
        }

        if (working.finalizationPhase() != StatsFinalizationPhase.SEND_DONE_AWAITING_RETIRE) {
            return working.screenshotRequired() ? StatsFinalizationResult.PENDING_SCREENSHOT : StatsFinalizationResult.FAILED;
        }

        if (!closeAndRetireStatsSession(working, reason)) return StatsFinalizationResult.FAILED;

        retiredStatsReportSnapshot = report;
        markArtifactConsumed(working);
        clearActiveInMemoryStatsSession();
        return StatsFinalizationResult.COMPLETED;
    }

    private StatsArtifactSnapshot commitAndSendFinalExternalStats(StatsArtifactSnapshot snapshot, RetiredStatsReportSnapshot report, String reason) {
        if (snapshot == null || report == null || mc == null || mc.player == null) return snapshot;

        StatsArtifactSnapshot working = snapshot;

        if (sendStatisticsWebhhok.get() && !working.webhookSendCommitted()) {
            String webhookUrl = decryptWebhook(encryptedWebhook.get(), decryptkey.get());
            if (webhookUrl != null) {
                double distance = PlayerUtils.distanceTo(report.startPos());
                if (distance > 1) {
                    StatsArtifactSnapshot committed = updateFinalizationRecord(
                        working,
                        working.screenshotCompleted(),
                        working.finalizationPhase(),
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
                        sendToWebhook(webhookUrl, statsMessage);
                        working = committed;
                    }
                } else warning("Statistics NOT sent to webhook! Distance too small: (highlight)%.0f", distance);
            }
        }

        if (sendStatisticsapi.get() && !working.apiSendCommitted()) {
            double distance = PlayerUtils.distanceTo(report.startPos());
            if (distance > 1) {
                if (distance < 50000) {
                    if (isNot6B6T()) warning("API not sent. You are not on 6B6T");
                    else if (THMSystem.get().getHash() == null || Objects.equals(THMSystem.get().getHash(), "SetYourHash") || Objects.equals(THMSystem.get().getHash(), "")) {
                        warning("API not sent. No Hash set.");
                    } else {
                        StatsArtifactSnapshot committed = updateFinalizationRecord(
                            working,
                            working.screenshotCompleted(),
                            working.finalizationPhase(),
                            working.printedAt(),
                            working.printedToChat(),
                            working.webhookSendCommitted(),
                            true,
                            reason + "-api-commit"
                        );
                        if (committed != null) {
                            String server = mc.getCurrentServerEntry() != null ? mc.getCurrentServerEntry().address : "singleplayer";
                            String playerName = mc.player.getName().getLiteralString();
                            String statsMessageapi = String.format("%s:%s:%s:%.0f:%s:%s:%s:%s:%s",
                                THMSystem.get().getHash(),
                                playerName,
                                server,
                                distance,
                                report.blocksBroken(),
                                report.blocksPlaced(),
                                dir,
                                generateTimestamp(),
                                isOnMainHighway()
                            );
                            sendToAPI(statsMessageapi, getPassword(), getAPIHighway(), "statistics");
                            working = committed;
                        }
                    }
                } else warning("Statistics NOT sent to Api! Please Calculate the real Distance using the /calculate command in proof-of-work");
            } else warning("Statistics NOT sent to Api! Distance too small: (highlight)%.0f", distance);
        }

        return working;
    }

    private void scheduleStatsProofScreenshot(String sessionId, String screenshotFileName, String reason) {
        if (sessionId == null || sessionId.isBlank()) return;
        if (screenshotFileName == null || screenshotFileName.isBlank()) return;
        if (statsProofScreenshotScheduled) return;
        statsProofScreenshotScheduled = true;

        Thread thread = new Thread(() -> {
            try {
                Thread.sleep(STATS_SCREENSHOT_DELAY_MS);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }

            mc.execute(() -> {
                try {
                    takeGuaranteedStatsProofScreenshot(sessionId, screenshotFileName, reason);
                } finally {
                    statsProofScreenshotScheduled = false;
                }
            });
        }, "thm-highwaybuilder-stats-screenshot");
        thread.setDaemon(true);
        thread.start();
    }

    private void scheduleDisconnectScreenStatsScreenshotIfEnabled(String sessionId, String reason) {
        if (!autoScreenshotStatistics.get()) return;
        if (sessionId == null || sessionId.isBlank()) return;
        if (statsDisconnectScreenshotScheduled) return;
        statsDisconnectScreenshotScheduled = true;

        Thread thread = new Thread(() -> {
            try {
                Thread.sleep(STATS_SCREENSHOT_DELAY_MS);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }

            mc.execute(() -> {
                try {
                    takeBestEffortStatsProofScreenshot(sessionId, reason);
                } finally {
                    statsDisconnectScreenshotScheduled = false;
                }
            });
        }, "thm-highwaybuilder-disconnect-stats-screenshot");
        thread.setDaemon(true);
        thread.start();
    }

    private void takeGuaranteedStatsProofScreenshot(String sessionId, String screenshotFileName, String reason) {
        if (mc == null || mc.getFramebuffer() == null) return;

        ScreenshotRecorder.saveScreenshot(mc.runDirectory, screenshotFileName, mc.getFramebuffer(), 1, message -> mc.execute(() -> {
            info(message.getString());
            continueFinalizationAfterStatsScreenshot(sessionId, screenshotFileName, reason + "-callback");
        }));
        THMAddon.LOG.info("[highway-stats-cache] guaranteed screenshot requested reason={} session={} file={}",
            reason,
            shortSessionId(sessionId),
            screenshotFileName
        );
    }

    private void takeBestEffortStatsProofScreenshot(String sessionId, String reason) {
        if (mc == null || mc.getFramebuffer() == null) return;

        String fileName = buildDisconnectStatsScreenshotFileName(sessionId);
        ScreenshotRecorder.saveScreenshot(mc.runDirectory, fileName, mc.getFramebuffer(), 1, message -> info(message.getString()));
        THMAddon.LOG.info("[highway-stats-cache] screenshot saved reason={} session={} file={}",
            reason,
            shortSessionId(sessionId),
            fileName
        );
    }

    private void continueFinalizationAfterStatsScreenshot(String sessionId, String screenshotFileName, String reason) {
        Path expectedPath = resolveStatsScreenshotPath(screenshotFileName);
        if (!isValidStatsScreenshotPath(expectedPath)) {
            THMAddon.LOG.warn("[highway-stats-cache] screenshot proof missing after callback reason={} session={} file={}",
                reason,
                shortSessionId(sessionId),
                screenshotFileName
            );
            return;
        }

        loadStatsCacheFromDisk();
        StatsArtifactSnapshot pending = statsCacheSnapshot;
        if (!isPendingPrintStatsSession(pending)) return;
        if (!Objects.equals(pending.sessionId(), sessionId)) return;
        if (!Objects.equals(pending.screenshotFileName(), screenshotFileName)) return;
        if (pending.finalizationPhase() != StatsFinalizationPhase.AWAITING_SCREENSHOT) return;

        StatsArtifactSnapshot updated = updateFinalizationRecord(
            pending,
            true,
            StatsFinalizationPhase.SCREENSHOT_DONE_AWAITING_SEND,
            System.currentTimeMillis(),
            true,
            pending.webhookSendCommitted(),
            pending.apiSendCommitted(),
            reason + "-proof-recorded"
        );
        if (updated == null) return;

        StatsFinalizationResult continuationResult = completeFinalizationRecord(updated, reason + "-continue", false);
        if (continuationResult == StatsFinalizationResult.FAILED) {
            warning("Unable to finish HighwayBuilder statistics after screenshot proof; keeping durable finalization record.");
        }
    }

    private boolean isValidStatsScreenshotPath(Path path) {
        if (path == null) return false;
        try {
            return Files.exists(path) && Files.isRegularFile(path) && Files.size(path) > 0L;
        } catch (IOException ignored) {
            return false;
        }
    }

    private Path resolveStatsScreenshotPath(String screenshotFileName) {
        if (screenshotFileName == null || screenshotFileName.isBlank()) return null;
        return mc.runDirectory.toPath().resolve("screenshots").resolve(screenshotFileName);
    }

    private String buildStatsScreenshotFileName(String sessionId, long generation) {
        String stableSessionId = sessionId == null ? "unknown-session" : sessionId.replaceAll("[^A-Za-z0-9_-]", "_");
        return "thm-highwaybuilder-session-" + stableSessionId + "-g" + generation + ".png";
    }

    private String buildDisconnectStatsScreenshotFileName(String sessionId) {
        return "thm-highwaybuilder-session-" + STATS_SCREENSHOT_TIME_FORMAT.format(Instant.now()) + "-" + shortSessionId(sessionId) + ".png";
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
            THMAddon.LOG.warn("[highway-stats-cache] quarantined kind={} reason={} path={} quarantine={} error={}",
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
            THMAddon.LOG.warn("[highway-stats-cache] quarantine failed kind={} reason={} path={} message={} error={}",
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

    private StatsFinalizationPhase parseStatsFinalizationPhase(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return StatsFinalizationPhase.valueOf(value.trim());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private boolean tryPrintStatsToChat(String sessionId, Vec3d startPos, int broken, int placed, String reason) {
        if (startPos == null || mc.player == null || mc.world == null) return false;
        if (!Utils.canUpdate()) return false;

        info("Distance: (highlight)%.0f", PlayerUtils.distanceTo(startPos));
        info("Blocks broken: (highlight)%d", broken);
        info("Blocks placed: (highlight)%d", placed);
        lastPrintedStatsSessionId = sessionId;
        THMAddon.LOG.info("[highway-stats-cache] printed reason={} session={} broken={} placed={}",
            reason,
            shortSessionId(sessionId),
            broken,
            placed
        );
        return true;
    }

    private void recordBlockBroken() {
        blocksBroken++;
        statsSessionDirty = true;
    }

    private void recordBlockPlaced() {
        blocksPlaced++;
        statsSessionDirty = true;
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
        return "(" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + ")";
    }

    private boolean isHotbarSlotReservedByManager(int hotbarSlot) {
        if (hotbarSlot < 0 || hotbarSlot >= 9) return false;
        if (!hotbarmanager.get()) return false;

        HotbarManager manager = Modules.get().get(HotbarManager.class);
        return manager != null && manager.isActive() && manager.managesSlot(hotbarSlot);
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

    private boolean hasForwardPlaceableBlock() {
        if (mc.player == null) return false;

        for (int i = 0; i < mc.player.getInventory().getMainStacks().size(); i++) {
            if (isForwardPlaceableBlock(mc.player.getInventory().getStack(i))) return true;
        }

        return false;
    }

    private boolean clearCursorStackToEmptySlot(String reason) {
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

    private void dropCursorHandBypassingAntiDrop() {
        AntiDrop antiDrop = Modules.get().get(AntiDrop.class);
        boolean wasActive = antiDrop != null && antiDrop.isActive();
        if (wasActive) antiDrop.toggle();
        InvUtils.dropHand();
        if (wasActive) antiDrop.toggle();
    }

    private int findTrashBlockSwapSlot() {
        if (mc.player == null) return -1;

        for (int i = 0; i < mc.player.getInventory().getMainStacks().size(); i++) {
            ItemStack itemStack = mc.player.getInventory().getStack(i);
            if (!(itemStack.getItem() instanceof BlockItem)) continue;
            if (!trashItems.get().contains(itemStack.getItem())) continue;
            return i;
        }

        return -1;
    }

    private void handleFoodTypesChanged(List<Item> selected) {
        if (clampingFoodTypes || selected == null || selected.size() <= 2) return;

        clampingFoodTypes = true;
        try {
            foodTypes.set(new ArrayList<>(selected.subList(0, 2)));
        } finally {
            clampingFoodTypes = false;
        }

        warning("Maximum 2 food types.");
    }

    private boolean hasConfiguredFoodTypes() {
        return !foodTypes.get().isEmpty();
    }

    private boolean isConfiguredFoodItem(Item item) {
        return item != null && foodTypes.get().contains(item);
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

    private int countLooseConfiguredFoodItems(Item item) {
        if (mc.player == null || item == null) return 0;

        int count = 0;
        for (int i = 0; i < mc.player.getInventory().getMainStacks().size(); i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isOf(item)) count += stack.getCount();
        }

        return count;
    }

    private int findPreferredConfiguredFoodSlot(Inventory inventory) {
        int firstMatch = -1;
        int bestMergeMatch = -1;
        int bestMergeCount = -1;

        for (int i = 0; i < inventory.size(); i++) {
            ItemStack stack = inventory.getStack(i);
            if (!isConfiguredFoodStack(stack)) continue;

            if (firstMatch == -1) firstMatch = i;

            int looseCount = countLooseConfiguredFoodItems(stack.getItem());
            if (looseCount <= 0) continue;

            if (looseCount > bestMergeCount) {
                bestMergeCount = looseCount;
                bestMergeMatch = i;
            }
        }

        return bestMergeMatch != -1 ? bestMergeMatch : firstMatch;
    }

    private boolean isContainerItemEmpty(ItemStack containerItem) {
        if (containerItem == null || containerItem.isEmpty() || !Utils.isShulker(containerItem.getItem())) return true;

        ItemStack[] items = new ItemStack[27];
        Utils.getItemsInContainerItem(containerItem, items);

        for (ItemStack stack : items) {
            if (!stack.isEmpty()) return false;
        }

        return true;
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

    private boolean isUsefulCursorStack(ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty()) return false;
        if (itemStack.isIn(ItemTags.PICKAXES)) return true;
        if (isConfiguredFoodStack(itemStack)) return true;
        if (itemStack.getItem() instanceof BlockItem bi) {
            if (trashItems.get().contains(itemStack.getItem())) return false;
            if (blocksToPlace.get().contains(bi.getBlock())) return true;
            if (bi == Items.ENDER_CHEST) return true;
        }
        if (itemStack.isOf(Items.OBSIDIAN) && !trashItems.get().contains(Items.OBSIDIAN)) return true;
        if (Utils.isShulker(itemStack.getItem())) return isUsefulShulkerStack(itemStack);
        return false;
    }

    private boolean isUsefulShulkerStack(ItemStack itemStack) {
        ItemStack[] items = new ItemStack[27];
        Utils.getItemsInContainerItem(itemStack, items);

        for (ItemStack stack : items) {
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
                boolean inRange = blockPos.getSquaredDistance(mc.player.getEyePos()) <= placeRange.get() * placeRange.get();
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
            .append(Text.literal(String.format(message, args)).styled(style -> style.withColor(Formatting.RED)))
            .append("\n")
            .append(getStatsText());

        String screenshotSessionId = lastPrintedStatsSessionId;
        if ((screenshotSessionId == null || screenshotSessionId.isBlank()) && retiredStatsReportSnapshot != null) {
            screenshotSessionId = retiredStatsReportSnapshot.sessionId();
        }
        if ((screenshotSessionId == null || screenshotSessionId.isBlank()) && activeStatsSessionId != null && !activeStatsSessionId.isBlank()) {
            screenshotSessionId = activeStatsSessionId;
        }

        mc.getNetworkHandler().getConnection().disconnect(text);
        scheduleDisconnectScreenStatsScreenshotIfEnabled(screenshotSessionId, "disconnect-screen-stats");
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

        double distance = 0.0;
        if (mc.player != null && statsStart != null) distance = PlayerUtils.distanceTo(statsStart);

        MutableText text = Text.literal(String.format("%sDistance: %s%.0f\n", Formatting.GRAY, Formatting.WHITE, distance));
        text.append(String.format("%sBlocks broken: %s%d\n", Formatting.GRAY, Formatting.WHITE, statsBroken));
        text.append(String.format("%sBlocks placed: %s%d\n", Formatting.GRAY, Formatting.WHITE, statsPlaced));
        if (mc.player != null && statsStart != null && distance > 50000) {
            text.append(String.format("%sRestart Detected. Please calculate the real distance using /calculate in proof-of-work",
                Formatting.YELLOW));
        }

        return text;
    }

    private void notifyDesktop(Setting<Boolean> eventToggle, String heading, String description) {
        if (!desktopNotifies.get() || !eventToggle.get()) return;
        Notify(heading, description);
    }

    private void tickDoubleMine() {
        // could add clientside block breaking to speed the system up, but it would probably make it too vulnerable to desyncs
        if (normalMining != null) {
            if (normalMining.shouldRemove()) {
                mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.ABORT_DESTROY_BLOCK, normalMining.blockPos, normalMining.direction));
                normalMining = null;
                DoubleMineBlock.rateLimited = true;
            }
            else if (mc.world.getBlockState(normalMining.blockPos).getBlock() != normalMining.block) {
                normalMining = null;
                recordBlockBroken();
                count++;
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
                packetMining = null;
            }
            else if (mc.world.getBlockState(packetMining.blockPos).getBlock() != packetMining.block) {
                packetMining = null;
                recordBlockBroken();
                count++;
            }
        }
    }

    private enum State {
        Center {
            private static final int RECENTER_TIMEOUT_TICKS = 20 * 20;
            private int timeoutTicks;

            @Override
            protected void start(HighwayBuilderTHM b) {
                timeoutTicks = RECENTER_TIMEOUT_TICKS;
                b.applyCenterSpeedOverrideIfPossible("center-start");
                if (b.mc.player.getEntityPos().isInRange(Vec3d.ofBottomCenter(b.mc.player.getBlockPos()), 0.1)) {
                    stop(b);
                }
            }

            @Override
            protected void tick(HighwayBuilderTHM b) {
                if (timeoutTicks > 0) timeoutTicks--;
                else {
                    restart(b);
                    return;
                }

                // There is probably a much better way to do this
                double x = Math.abs(b.mc.player.getX() - (int) b.mc.player.getX()) - 0.5;
                double z = Math.abs(b.mc.player.getZ() - (int) b.mc.player.getZ()) - 0.5;

                boolean isX = Math.abs(x) <= 0.1;
                boolean isZ = Math.abs(z) <= 0.1;

                if (isX && isZ) {
                    stop(b);
                }
                else {
                    b.mc.player.setYaw(0);

                    if (!isZ) {
                        b.input.forward(z < 0);
                        b.input.backward(z > 0);

                        if (b.mc.player.getZ() < 0) {
                            boolean forward = b.input.playerInput.forward();
                            b.input.forward(b.input.playerInput.backward());
                            b.input.backward(forward);
                        }
                    }

                    if (!isX) {
                        b.input.right(x > 0);
                        b.input.left(x < 0);

                        if (b.mc.player.getX() < 0) {
                            boolean right = b.input.playerInput.right();
                            b.input.right(b.input.playerInput.left());
                            b.input.left(right);
                        }
                    }

                    b.input.sneak(true);
                }
            }

            private void stop(HighwayBuilderTHM b) {
                b.input.stop();
                b.mc.player.setVelocity(0, 0, 0);
                b.restoreCenterSpeedIfOwned("center-stop");
                b.mc.player.setPosition((int) b.mc.player.getX() + (b.mc.player.getX() < 0 ? -0.5 : 0.5), b.mc.player.getY(), (int) b.mc.player.getZ() + (b.mc.player.getZ() < 0 ? -0.5 : 0.5));
                b.setState(b.lastState);
            }

            private void restart(HighwayBuilderTHM b) {
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
                checkTasks(b);
                b.mc.player.setPitch(20);
                if (b.state == Forward) b.mc.player.setYaw(b.dir.yaw);
            }

            @Override
            protected void tick(HighwayBuilderTHM b) {
                checkTasks(b);
                b.mc.player.setPitch(20);
                if (b.state == Forward) b.input.forward(true); // Move
            }

            private void checkTasks(HighwayBuilderTHM b) {
                if (b.restockTask.shouldTearDownRestockBlockadeFromForward()) b.setState(MineShulkerBlockade, Restock);
                else if (b.destroyCrystalTraps.get() && isCrystalTrap(b)) b.setState(DefuseCrystalTraps); // Destroy crystal traps
                else if (needsToPlace(b, b.blockPosProvider.getLiquids(), true)) b.setState(FillLiquids); // Fill Liquids
                else if (needsToMine(b, b.blockPosProvider.getFront(), true)) b.setState(MineFront); // Mine Front
                else if (b.checkBehind.get() && needsToMine(b, b.blockPosProvider.getBehindFront(), true)) b.setState(MineBehind); // Mine Behind
                else if (b.floor.get() == Floor.Replace && needsToMine(b, b.blockPosProvider.getFloor(), false)) b.setState(MineFloor); // Mine Floor
                else if (b.railings.get() && needsToMine(b, b.blockPosProvider.getRailings(0), false)) b.setState(MineRailings); // Mine Railings
                else if (b.mineAboveRailings.get() && needsToMine(b, b.blockPosProvider.getRailings(1), true)) b.setState(MineAboveRailings); // Mine above railings
                else if (b.railings.get() && needsToPlace(b, b.blockPosProvider.getRailings(0, b.checkBehind.get()), false)) {
                    if (b.cornerBlock.get() && needsToPlace(b, b.blockPosProvider.getRailings(-1, b.checkBehind.get()), false)) b.setState(PlaceCornerBlock); // Place corner support block
                    else b.setState(PlaceRailings); // Place Railings
                }
                else if (needsToPlace(b, b.blockPosProvider.getFloor(b.checkBehind.get()), false)) b.setState(PlaceFloor); // Place Floor
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
                    if (b.renderPlace.get()) RenderUtils.renderTickingBlock(pos.toImmutable(), b.renderPlaceSideColor.get(), b.renderPlaceLineColor.get(), b.renderPlaceShape.get(), 0, 5, true, false);
                    b.placeTimer = b.placeDelay.get();
                }
            }

            private int findAcceptablePlacementBlock(HighwayBuilderTHM b) {
                // still should prioritise trash
                int slot = findAndMoveToHotbar(b, itemStack -> {
                    if (!(itemStack.getItem() instanceof BlockItem)) return false;
                    return b.trashItems.get().contains(itemStack.getItem());
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
            private final Set<Integer> keepSlots = new HashSet<>();
            private boolean timerEnabled, firstTick, threwItems;
            private int timer;
            private static final ItemStack[] ITEMS = new ItemStack[27];

            @Override
            protected void start(HighwayBuilderTHM b) {
                keepSlots.clear();
                List<Integer> trashBlockSlots = new ArrayList<>();

                for (int i = 0; i < b.mc.player.getInventory().getMainStacks().size(); i++) {
                    ItemStack itemStack = b.mc.player.getInventory().getStack(i);

                    if (itemStack.getItem() instanceof BlockItem && b.trashItems.get().contains(itemStack.getItem())) trashBlockSlots.add(i);
                }

                trashBlockSlots.sort((a, c) -> Integer.compare(
                    b.mc.player.getInventory().getStack(c).getCount(),
                    b.mc.player.getInventory().getStack(a).getCount()
                ));

                int keepCount = Math.min(b.keepTrashBlockStacks.get(), trashBlockSlots.size());
                for (int i = 0; i < keepCount; i++) keepSlots.add(trashBlockSlots.get(i));

                timerEnabled = false;
                firstTick = true;
                threwItems = false;
            }

            @Override
            protected void tick(HighwayBuilderTHM b) {
                if (timerEnabled) {
                    if (timer > 0) timer--;
                    else b.setState(b.lastState);

                    return;
                }

                b.mc.player.setYaw(b.dir.opposite().yaw);
                b.mc.player.setPitch(-25);

                if (firstTick) {
                    firstTick = false;
                    return;
                }

                if (!b.mc.player.currentScreenHandler.getCursorStack().isEmpty()) {
                    handleCursorStack(b);
                    return;
                }

                for (int i = 0; i < b.mc.player.getInventory().getMainStacks().size(); i++) {
                    if (keepSlots.contains(i)) continue;

                    ItemStack itemStack = b.mc.player.getInventory().getStack(i);
                    if (itemStack.getItem() == Items.OBSIDIAN && !b.trashItems.get().contains(Items.OBSIDIAN)) continue;

                    if (b.trashItems.get().contains(itemStack.getItem())) {
                        InvUtils.drop().slot(i);
                        threwItems = true;
                        return;
                    }

                    if (b.ejectUselessShulkers.get() && Utils.isShulker(itemStack.getItem())) {
                        if (!isUsefulShulker(b, itemStack)) {
                            InvUtils.drop().slot(i);
                            threwItems = true;
                            return;
                        }
                    }
                }

                timerEnabled = true;
                timer = threwItems ? 10 : 1;
            }

            private void handleCursorStack(HighwayBuilderTHM b) {
                ItemStack cursorStack = b.mc.player.currentScreenHandler.getCursorStack();
                if (b.clearCursorStackToEmptySlot("ThrowOutTrash")) return;

                if (trySwapCursorObsidianForTrash(b)) {
                    b.protectUsefulCursorStackFromDrop("ThrowOutTrash-obsidian-swap");
                    if (!b.mc.player.currentScreenHandler.getCursorStack().isEmpty()) InvUtils.dropHand();
                    threwItems = true;
                    return;
                }

                if (b.protectUsefulCursorStackFromDrop("ThrowOutTrash-cursor")) return;

                if (Utils.isShulker(cursorStack.getItem()) && b.ejectUselessShulkers.get()) {
                    if (!isUsefulShulker(b, cursorStack)) {
                        if (!b.protectUsefulCursorStackFromDrop("ThrowOutTrash-useless-shulker")) InvUtils.dropHand();
                        threwItems = true;
                        return;
                    }

                    if (trySwapProtectedCursorForDroppableSlot(b)) {
                        if (!b.protectUsefulCursorStackFromDrop("ThrowOutTrash-protected-shulker-swap")) InvUtils.dropHand();
                        threwItems = true;
                    }
                    return;
                }

                if (!b.protectUsefulCursorStackFromDrop("ThrowOutTrash-default")) InvUtils.dropHand();
            }

            private boolean trySwapCursorObsidianForTrash(HighwayBuilderTHM b) {
                ItemStack cursorStack = b.mc.player.currentScreenHandler.getCursorStack();
                if (!cursorStack.isOf(Items.OBSIDIAN)) return false;

                int trashSlot = findTrashSwapSlot(b);
                if (trashSlot == -1) return false;

                b.mc.interactionManager.clickSlot(
                    b.mc.player.currentScreenHandler.syncId,
                    SlotUtils.indexToId(trashSlot),
                    0,
                    SlotActionType.PICKUP,
                    b.mc.player
                );

                return !b.mc.player.currentScreenHandler.getCursorStack().isOf(Items.OBSIDIAN);
            }

            private int findTrashSwapSlot(HighwayBuilderTHM b) {
                for (int i = 0; i < b.mc.player.getInventory().getMainStacks().size(); i++) {
                    if (keepSlots.contains(i)) continue;

                    ItemStack itemStack = b.mc.player.getInventory().getStack(i);
                    if (!(itemStack.getItem() instanceof BlockItem)) continue;
                    if (!b.trashItems.get().contains(itemStack.getItem())) continue;

                    return i;
                }

                return -1;
            }

            private boolean trySwapProtectedCursorForDroppableSlot(HighwayBuilderTHM b) {
                int droppableSlot = findDroppableSwapSlot(b);
                if (droppableSlot == -1) return false;

                b.mc.interactionManager.clickSlot(
                    b.mc.player.currentScreenHandler.syncId,
                    SlotUtils.indexToId(droppableSlot),
                    0,
                    SlotActionType.PICKUP,
                    b.mc.player
                );

                ItemStack cursorStack = b.mc.player.currentScreenHandler.getCursorStack();
                return !Utils.isShulker(cursorStack.getItem()) || !isUsefulShulker(b, cursorStack);
            }

            private int findDroppableSwapSlot(HighwayBuilderTHM b) {
                for (int i = 0; i < b.mc.player.getInventory().getMainStacks().size(); i++) {
                    if (keepSlots.contains(i)) continue;

                    ItemStack itemStack = b.mc.player.getInventory().getStack(i);
                    if (b.trashItems.get().contains(itemStack.getItem())) return i;
                    if (b.ejectUselessShulkers.get() && Utils.isShulker(itemStack.getItem()) && !isUsefulShulker(b, itemStack)) return i;
                }

                return -1;
            }

            private boolean isUsefulShulker(HighwayBuilderTHM b, ItemStack itemStack) {
                Utils.getItemsInContainerItem(itemStack, ITEMS);

                for (ItemStack stack : ITEMS) {
                    if (stack.getItem() instanceof BlockItem bi
                        && (b.blocksToPlace.get().contains(bi.getBlock())
                        || (b.blocksToPlace.get().contains(Blocks.OBSIDIAN) && bi == Items.ENDER_CHEST))) {
                        return true;
                    }
                    if (stack.isIn(ItemTags.PICKAXES)) return true;
                    if (b.isConfiguredFoodStack(stack)) return true;
                }

                return false;
            }
        },

        PlaceEChestBlockade {
            @Override
            protected void tick(HighwayBuilderTHM b) {
                int slot = findBlocksToPlacePrioritizeTrash(b);
                if (slot == -1) return;

                place(b, b.blockPosProvider.getBlockade(false, b.blockadeType.get()), slot, MineEnderChests);
            }
        },

        MineEChestBlockade {
            @Override
            protected void tick(HighwayBuilderTHM b) {
                mine(b, b.blockPosProvider.getBlockade(true, b.blockadeType.get()), true, Center, Forward);
            }
        },

        MineEnderChests {
            private static final MBlockPos pos = new MBlockPos();
            private int targetEchestsToBreak;
            private int targetObsidianCount;
            private boolean first, primed;
            private boolean stopTimerEnabled;
            private int stopTimer, moveTimer, rebreakTimer, timeout;
            private double returnX, returnY, returnZ;
            private boolean returnAnchorSaved;
            @Override
            protected void start(HighwayBuilderTHM b) {
                b.restockTask.ensureSessionInitialized();
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

                RestockTask.RestockSession session = b.restockTask.getSession();
                session.refreshProgress();
                targetEchestsToBreak = session.getTargetEchestsToBreak();
                targetObsidianCount = session.getMiningGoalObsidianCount();
                if (targetEchestsToBreak <= 0) {
                    b.restockTask.markCurrentSourceExhausted(RestockTask.SourcePhase.MineEnderChests);
                    if (b.restockTask.isTargetSatisfied()) {
                        completeAndReturnToAnchor(b);
                    } else if (b.kitbotRestock.get()) {
                        b.setState(KitbotOrder);
                    } else {
                        b.error("No usable ender chests available to continue obsidian restock.");
                    }
                    return;
                }
                first = true;
                moveTimer = timeout = 0;
                returnX = b.mc.player.getX();
                returnY = b.mc.player.getY();
                returnZ = b.mc.player.getZ();
                returnAnchorSaved = true;

                stopTimerEnabled = false;
                primed = false;
            }

            @Override
            protected void tick(HighwayBuilderTHM b) {
                if (stopTimerEnabled) {
                    if (stopTimer > 0) stopTimer--;
                    else completeAndReturnToAnchor(b);

                    return;
                }

                HorizontalDirection dir = b.dir.diagonal ? b.dir.rotateLeft().rotateLeftSkipOne() : b.dir.opposite();
                pos.set(b.mc.player).offset(dir);

                // Move
                if (moveTimer > 0) {
                    b.mc.player.setYaw(dir.yaw);
                    b.input.forward(moveTimer > 2);

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
                        if (b.restockTask.isActiveMaterials() && b.restockTask.hasPendingPickaxes()) {
                            completeAndReturnToAnchor(b);
                        } else {
                            b.error("Cannot find pickaxe without silk touch to mine ender chests.");
                        }
                        return;
                    }

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
                        } else {
                            if (selectedSlot != slot) InvUtils.swap(slot, false);
                        }

                        if (b.rotation.get().mine) Rotations.rotate(Rotations.getYaw(bp), Rotations.getPitch(bp), sendRebreakPackets);
                        else sendRebreakPackets.run();

                        if (swappedForRebreak) InvUtils.swap(selectedSlot, false);
                    }
                    else {
                        if (selectedSlot != slot) InvUtils.swap(slot, false);
                        if (b.rotation.get().mine) Rotations.rotate(Rotations.getYaw(bp), Rotations.getPitch(bp), () -> BlockUtils.breakBlock(bp, true));
                        else BlockUtils.breakBlock(bp, true);
                    }
                }
                else {
                    // Place ender chest
                    int slot = findAndMoveToHotbar(b, itemStack -> itemStack.getItem() == Items.ENDER_CHEST);
                    if (slot == -1 || countItem(b, stack -> stack.getItem().equals(Items.ENDER_CHEST)) <= b.saveEchests.get()) {
                        b.restockTask.markCurrentSourceExhausted(RestockTask.SourcePhase.MineEnderChests);
                        stopTimerEnabled = true;
                        stopTimer = 12;
                        return;
                    }

                    if (countItem(b, stack -> stack.isIn(ItemTags.PICKAXES)) <= b.savePickaxes.get()) {
                        if (b.searchEnderChest.get() || b.searchShulkers.get()) {
                            b.restockTask.setPickaxes();
                        }
                    }

                    if (!first) primed = true;

                    BlockUtils.place(bp, Hand.MAIN_HAND, slot, b.rotation.get().place, 0, true, true, b.silentRebreakSwap.get());
                    timeout = 0;
                }
            }

            private void completeAndReturnToAnchor(HighwayBuilderTHM b) {
                if (returnAnchorSaved) {
                    b.input.stop();
                    b.mc.player.setPosition(returnX, returnY, returnZ);
                    returnAnchorSaved = false;
                }

                b.completeRestockTaskAndContinue();
            }
        },

        KitbotOrder {
            private static final ItemStack[] ITEMS = new ItemStack[27];
            private static final int KITBOT_FAILSAFE_MIN_SHULKERS = 2;
            private static final int KITBOT_FAILSAFE_DELAY_TICKS = 200;
            private static final int KITBOT_NO_DELIVERY_RETRY_TICKS = 20 * 180;
            private static final int[][] CAGE_OFFSETS = new int[][]{
                {1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1},
                {1, 1, 0}, {-1, 1, 0}, {0, 1, 1}, {0, 1, -1},
                {0, 2, 0}
            };
            private final BlockPos.Mutable cagePos = new BlockPos.Mutable();
            private boolean orderSent;
            private boolean cageReady;
            private double returnX, returnY, returnZ;
            private boolean returnAnchorSaved;

            @Override
            protected void start(HighwayBuilderTHM b) {
                orderSent = b.kitbotOrderInFlight;
                cageReady = false;
                b.kitbotTpHandled = false;
                returnX = b.mc.player.getX();
                returnY = b.mc.player.getY();
                returnZ = b.mc.player.getZ();
                returnAnchorSaved = true;
                b.input.stop();
            }

            @Override
            protected void tick(HighwayBuilderTHM b) {
                b.input.stop();

                if (!b.kitbotRestock.get()) {
                    b.clearKitbotOrderTracking("kitbot-restock-disabled");
                    returnToAnchorAndSetState(b, Forward);
                    return;
                }

                if (!cageReady) {
                    cageReady = buildCage(b);
                    return;
                }

                if (!orderSent) {
                    KitbotRestockKit kit = b.kitbotRestockKit.get();
                    int amount = 4;
                    b.kitbotOrderBaselineShulkerCount = countMatchingRestockShulkersInInventory(b);
                    b.kitbotOrderExpectedShulkerGain = amount;
                    b.kitbotOrderSentAtAge = b.mc.player.age;
                    b.kitbotOrderRetryCount = 0;
                    KitbotFrontend.kitOrder(kit.kitName, amount);
                    b.info("Ordering kit '%s' x%d from %s.", kit.kitName, amount, KITBOT_NAME);
                    orderSent = true;
                    b.kitbotOrderInFlight = true;
                    return;
                }

                if (handleNoDeliveryTimeout(b)) return;

                if (hasExpectedKitDelivery(b)) {
                    if (b.restockTask.isSequenceActive() && !b.restockTask.tasksInactive()) {
                        if (b.restockTask.isBlockadeReady()) {
                            if (!b.restockTask.validateOrInvalidateBlockadeLease()) {
                                b.restockTask.setBlockadeReady(false);
                                b.restockDebug("KitbotOrder invalidated blockade lease after fallback; forcing rebuild.");
                            }
                            if (repairExistingBlockade(b)) return;
                            b.restockDebug("KitbotOrder restored the existing blockade, returning to Restock.");
                        } else {
                            if (breakCageTop(b)) return;
                            b.restockTask.setBlockadeReady(false);
                            b.restockDebug("KitbotOrder received supplies without a valid blockade, forcing proper blockade rebuild before returning to Restock.");
                        }
                        returnToAnchorAndSetState(b, Restock);
                    } else {
                        b.clearKitbotOrderTracking("kitbot-order-supplies-ready-no-restock-sequence");
                        if (breakCageTop(b)) return;
                        returnToAnchorAndSetState(b, Forward);
                    }
                }
            }

            private boolean handleNoDeliveryTimeout(HighwayBuilderTHM b) {
                int currentShulkerCount = countMatchingRestockShulkersInInventory(b);
                int gainedShulkers = Math.max(currentShulkerCount - b.kitbotOrderBaselineShulkerCount, 0);
                int ticksWaiting = Math.max(b.mc.player.age - b.kitbotOrderSentAtAge, 0);

                if (gainedShulkers > 0 || ticksWaiting < KITBOT_NO_DELIVERY_RETRY_TICKS) return false;

                if (b.kitbotOrderRetryCount == 0) {
                    KitbotRestockKit kit = b.kitbotRestockKit.get();
                    int amount = Math.max(b.kitbotOrderExpectedShulkerGain, 4);
                    KitbotFrontend.kitOrder(kit.kitName, amount);
                    b.kitbotOrderSentAtAge = b.mc.player.age;
                    b.kitbotOrderRetryCount = 1;
                    b.warning("Kitbot restock received no shulkers after 3 minutes. Retrying kit order.");
                    if (b.restockDebugLog.get()) {
                        b.restockDebug("KitbotOrder retry issued after %d ticks with gainedShulkers=%d baseline=%d current=%d.",
                            ticksWaiting,
                            gainedShulkers,
                            b.kitbotOrderBaselineShulkerCount,
                            currentShulkerCount
                        );
                    }
                    return true;
                }

                failKitbotOrder(b, "Kitbot restock failed.");
                return true;
            }

            private boolean buildCage(HighwayBuilderTHM b) {
                int slot = findAndMoveToHotbar(b, itemStack -> itemStack.getItem() instanceof BlockItem bi && bi.getBlock() == Blocks.NETHERRACK);
                if (slot == -1) {
                    failKitbotOrder(b, "No netherrack available to encase before kit order.");
                    return false;
                }

                BlockPos base = b.mc.player.getBlockPos();
                boolean allPlaced = true;

                for (int[] offset : CAGE_OFFSETS) {
                    cagePos.set(base.getX() + offset[0], base.getY() + offset[1], base.getZ() + offset[2]);
                    BlockState state = b.mc.world.getBlockState(cagePos);
                    if (!state.isAir() && state.getFluidState().isEmpty()) continue;

                    allPlaced = false;
                    if (b.placeTimer > 0) break;

                    if (BlockUtils.place(cagePos, Hand.MAIN_HAND, slot, b.rotation.get().place, 0, true, true, true)) {
                        b.placeTimer = b.placeDelay.get();
                    }
                    break;
                }

                return allPlaced;
            }

            private boolean hasExpectedKitDelivery(HighwayBuilderTHM b) {
                int expectedGain = Math.max(b.kitbotOrderExpectedShulkerGain, 4);
                int currentShulkerCount = countMatchingRestockShulkersInInventory(b);
                int targetCount = b.kitbotOrderBaselineShulkerCount + expectedGain;
                int failsafeTarget = b.kitbotOrderBaselineShulkerCount + KITBOT_FAILSAFE_MIN_SHULKERS;
                int ticksWaiting = Math.max(b.mc.player.age - b.kitbotOrderSentAtAge, 0);
                boolean failsafeReady = currentShulkerCount >= failsafeTarget && ticksWaiting >= KITBOT_FAILSAFE_DELAY_TICKS;

                if (b.restockDebugLog.get()) {
                    b.restockDebug("KitbotOrder delivery progress: currentMatchingShulkers=%d target=%d baseline=%d expectedGain=%d failsafeTarget=%d ticksWaiting=%d/%d failsafeReady=%s.",
                        currentShulkerCount,
                        targetCount,
                        b.kitbotOrderBaselineShulkerCount,
                        expectedGain,
                        failsafeTarget,
                        ticksWaiting,
                        KITBOT_FAILSAFE_DELAY_TICKS,
                        failsafeReady
                    );
                }

                return currentShulkerCount >= targetCount || failsafeReady;
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
                Utils.getItemsInContainerItem(itemStack, ITEMS);

                for (ItemStack stack : ITEMS) {
                    if (stack.getItem() instanceof BlockItem bi && (b.blocksToPlace.get().contains(bi.getBlock()) || (b.blocksToPlace.get().contains(Blocks.OBSIDIAN) && bi == Items.ENDER_CHEST))) {
                        return true;
                    }
                    if (b.restockTask.pickaxes && stack.isIn(ItemTags.PICKAXES)) return true;
                    if (b.restockTask.food && b.isConfiguredFoodStack(stack)) return true;
                }

                return false;
            }

            private boolean breakCageTop(HighwayBuilderTHM b) {
                BlockPos top = b.mc.player.getBlockPos().up(2);
                if (b.mc.world.getBlockState(top).getBlock() != Blocks.NETHERRACK) return false;
                if (b.breakTimer > 0) return true;

                Runnable breakBlock = () -> BlockUtils.breakBlock(top, true);
                if (b.rotation.get().mine) Rotations.rotate(Rotations.getYaw(top), Rotations.getPitch(top), breakBlock);
                else breakBlock.run();

                b.breakTimer = b.breakDelay.get();
                return true;
            }

            private boolean repairExistingBlockade(HighwayBuilderTHM b) {
                if (clearKitbotOnlyBlockadeBlocks(b)) return true;

                if (!hasMissingBlockadeBlocks(b)) return false;

                int slot = findBlocksToPlacePrioritizeTrash(b);
                if (slot == -1) {
                    b.restockTask.setBlockadeReady(false);
                    b.restockDebug("KitbotOrder could not find a block to repair the existing blockade, falling back to rebuild.");
                    return false;
                }

                if (b.placeTimer > 0) return true;

                for (MBlockPos blockadePos : b.blockPosProvider.getBlockade(false, b.blockadeType.get())) {
                    BlockPos blockPos = blockadePos.getBlockPos();
                    if (!BlockUtils.canPlace(blockPos)) continue;

                    if (BlockUtils.place(blockPos, Hand.MAIN_HAND, slot, b.rotation.get().place, 0, true, true, true)) {
                        b.placeTimer = b.placeDelay.get();
                        b.restockDebug("KitbotOrder repaired blockade block at %s.", b.formatBlockPos(blockPos));
                    }

                    return true;
                }

                return false;
            }

            private boolean clearKitbotOnlyBlockadeBlocks(HighwayBuilderTHM b) {
                BlockPos base = b.mc.player.getBlockPos();

                for (int[] offset : CAGE_OFFSETS) {
                    cagePos.set(base.getX() + offset[0], base.getY() + offset[1], base.getZ() + offset[2]);
                    if (b.mc.world.getBlockState(cagePos).getBlock() != Blocks.NETHERRACK) continue;
                    if (isDesiredBlockadePosition(b, cagePos)) continue;
                    if (b.breakTimer > 0) return true;

                    Runnable breakBlock = () -> BlockUtils.breakBlock(cagePos, true);
                    if (b.rotation.get().mine) Rotations.rotate(Rotations.getYaw(cagePos), Rotations.getPitch(cagePos), breakBlock);
                    else breakBlock.run();

                    b.breakTimer = b.breakDelay.get();
                    b.restockDebug("KitbotOrder cleared temporary cage block at %s while restoring existing blockade.", b.formatBlockPos(cagePos));
                    return true;
                }

                return false;
            }

            private boolean hasMissingBlockadeBlocks(HighwayBuilderTHM b) {
                for (MBlockPos blockadePos : b.blockPosProvider.getBlockade(false, b.blockadeType.get())) {
                    if (BlockUtils.canPlace(blockadePos.getBlockPos())) return true;
                }

                return false;
            }

            private boolean isDesiredBlockadePosition(HighwayBuilderTHM b, BlockPos blockPos) {
                for (MBlockPos blockadePos : b.blockPosProvider.getBlockade(false, b.blockadeType.get())) {
                    if (blockadePos.getBlockPos().equals(blockPos)) return true;
                }

                return false;
            }

            private boolean hasShulkerWithMaterials(HighwayBuilderTHM b) {
                for (int i = 0; i < b.mc.player.getInventory().getMainStacks().size(); i++) {
                    ItemStack itemStack = b.mc.player.getInventory().getStack(i);
                    if (!Utils.isShulker(itemStack.getItem())) continue;

                    Utils.getItemsInContainerItem(itemStack, ITEMS);
                    for (ItemStack stack : ITEMS) {
                        if (stack.getItem() instanceof BlockItem bi) {
                            if (b.blocksToPlace.get().contains(bi.getBlock())
                                || (b.blocksToPlace.get().contains(Blocks.OBSIDIAN) && bi == Items.ENDER_CHEST)) {
                                return true;
                            }
                        }
                        if (b.restockTask.pickaxes && stack.isIn(ItemTags.PICKAXES)) return true;
                    }
                }
                return false;
            }

            private void returnToAnchorAndSetState(HighwayBuilderTHM b, State nextState) {
                if (returnAnchorSaved) {
                    b.input.stop();
                    b.mc.player.setPosition(returnX, returnY, returnZ);
                    returnAnchorSaved = false;
                }

                b.setState(nextState);
            }

            private void failKitbotOrder(HighwayBuilderTHM b, String message) {
                b.clearKitbotOrderTracking("kitbot-order-failed");
                if (returnAnchorSaved) {
                    b.input.stop();
                    b.mc.player.setPosition(returnX, returnY, returnZ);
                    returnAnchorSaved = false;
                }
                b.errorEarly(message);
            }
        },

        // this one was rough to do
        Restock {
            private static final MBlockPos pos = new MBlockPos();
            private static final ItemStack[] ITEMS = new ItemStack[27];
            private static final int INVALID_RESTOCK_RECOVERY_MAX_RETRIES = 1;
            private static final int SOURCE_READY_MAX_RETRIES = 3;
            private int minimumSlots, stopTimer, delayTimer;
            private boolean breakContainer, indicateStopping, transitionToMineEnderChests;
            private int restockPickaxesStartCount;
            private Predicate<ItemStack> shulkerPredicate;
            private Predicate<ItemStack> sourceItemPredicate;
            private String sourceLabel;
            private int sourceReadyRetries;
            private boolean extractedFoodShulkerFromEnderChest;
            private boolean foodShulkerReturnPending;
            private boolean foodShulkerReturnRetryUsed;
            private boolean foodShulkerReturnFinalizeAfterBreak;
            private int foodShulkerReturnWaitTicks;
            private int foodShulkerReturnInventorySlot = -1;
            // if this is ever not -1 when we expect it to be, things break a lot
            private int slot = -1;

            @Override
            protected void start(HighwayBuilderTHM b) {
                restockPickaxesStartCount = b.restockTask.getPickaxeStartCount();
                b.restockTask.ensureSessionInitialized();
                b.restockTask.notePhase(RestockTask.SourcePhase.Inventory);
                b.restockTask.clearBlockedByStaging();

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
                if (!b.restockTask.food) clearFoodReturnTracking();
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

                if (!b.restockTask.isBlockadeReady()) {
                    if (b.lastState != Center && b.lastState != ThrowOutTrash && b.lastState != PlaceShulkerBlockade && b.lastState != this) {
                        b.restockDebug("Restock.start requesting Center before blockade placement.");
                        b.setState(Center);
                        return;
                    }
                    else if (b.lastState == Center) {
                        b.restockDebug("Restock.start requesting ThrowOutTrash before blockade placement.");
                        b.setState(ThrowOutTrash);
                        return;
                    }
                    else if (b.lastState == ThrowOutTrash) {
                        b.restockDebug("Restock.start requesting PlaceShulkerBlockade using configured type %s.", b.blockadeType.get());
                        b.setState(PlaceShulkerBlockade);
                        return;
                    }
                }

                // firstly search your inventory for shulkers that have the items you need
                if (slot == -1 && b.searchShulkers.get() && (b.restockTask.getSession() == null || !b.restockTask.getSession().isInventoryShulkersExhausted())) {
                    b.restockTask.notePhase(RestockTask.SourcePhase.InventoryShulkers);
                    sourceItemPredicate = shulkerPredicate;
                    sourceLabel = "shulker";
                    slot = findAndMoveToHotbar(b, shulkerPredicate, false);
                    if (slot == -1) {
                        b.restockTask.markCurrentSourceExhausted(RestockTask.SourcePhase.InventoryShulkers);
                        sourceItemPredicate = null;
                        sourceLabel = "unknown";
                    }
                }

                // next search your ender chest for raw items and shulkers containing items
                if (slot == -1
                    && b.searchEnderChest.get()
                    && countItem(b, stack -> stack.getItem().equals(Items.ENDER_CHEST)) > 0
                    && (b.restockTask.getSession() == null || !b.restockTask.getSession().isEnderChestExhausted())) {
                    b.restockTask.notePhase(RestockTask.SourcePhase.EnderChest);

                    boolean stop = EChestMemory.isKnown();
                    if (EChestMemory.isKnown()) {
                        for (ItemStack stack : EChestMemory.ITEMS) {
                            if (b.restockTask.materials && stack.getItem() instanceof BlockItem bi) {
                                if (b.blocksToPlace.get().contains(bi.getBlock()) || (b.blocksToPlace.get().contains(Blocks.OBSIDIAN) && bi == Items.ENDER_CHEST && needsMoreRawEchests(b))) {
                                    stop = false;
                                    break;
                                }
                            }
                            if (b.restockTask.pickaxes && stack.isIn(ItemTags.PICKAXES)) {
                                stop = false;
                                break;
                            }
                            if (b.restockTask.food && b.isConfiguredFoodStack(stack)) {
                                stop = false;
                                break;
                            }

                            if (b.searchShulkers.get() && shulkerPredicate.test(stack)) {
                                stop = false;
                                break;
                            }
                        }
                    }

                    if (!stop) {
                        sourceItemPredicate = itemStack -> itemStack.getItem() == Items.ENDER_CHEST;
                        sourceLabel = "ender_chest";
                        slot = findAndMoveToHotbar(b, sourceItemPredicate, false);
                        if (slot == -1) {
                            b.restockTask.markCurrentSourceExhausted(RestockTask.SourcePhase.EnderChest);
                            sourceItemPredicate = null;
                            sourceLabel = "unknown";
                        }
                    } else {
                        b.restockTask.markCurrentSourceExhausted(RestockTask.SourcePhase.EnderChest);
                    }
                }

                if (slot == -1 && shouldMineEnderChestsForMaterials(b) && (b.restockTask.getSession() == null || !b.restockTask.getSession().isMineEnderChestsExhausted())) {
                    b.restockDebug("Restock.start found no direct source; continuing into MineEnderChests.");
                    b.setState(MineEnderChests);
                    return;
                }

                if (slot == -1 && b.kitbotRestock.get()
                    && (b.restockTask.materials || b.restockTask.pickaxes)
                    && !hasShulkerInInventory(b)
                    && b.restockTask.shouldAttemptKitbot()) {
                    b.restockTask.notePhase(RestockTask.SourcePhase.Kitbot);
                    b.restockDebug("Restock.start falling back to KitbotOrder.");
                    b.setState(KitbotOrder);
                    return;
                }

                // by this point we have searched shulkers and your ender chest, and no more items could be found to pull from
                if (slot == -1) {
                    b.restockTask.refreshSessionProgress();
                    if (b.restockTask.isTargetSatisfied()) {
                        b.completeRestockTaskAndContinue();
                    } else if (b.restockTask.isObsidianRestockSession() && b.restockTask.getSession() != null && b.restockTask.getSession().usingGreatestAvailable && b.restockTask.getSession().getProgressTowardsTarget() > 0) {
                        b.completeRestockTaskAndContinue();
                    } else {
                        if (b.restockTask.getSession() != null) b.restockTask.getSession().fail();
                        b.notifyDesktop(b.notifyRestockIssues, "THM Highway Builder", "Unable to perform restock for '" + b.restockTask.item() + "'.");
                        b.error("Unable to perform restock for '" + b.restockTask.item() + "'.");
                    }

                    return;
                }

                int emptySlots = 0;
                for (int i = 0; i < b.mc.player.getInventory().getMainStacks().size(); i++) {
                    if (b.mc.player.getInventory().getStack(i).isEmpty()) emptySlots++;
                }

                if (b.restockTask.getSession() != null && b.restockTask.getSession().targetInitialized) {
                    minimumSlots = b.restockTask.getSession().targetFinal;
                }
                else if (b.restockTask.pickaxes) {
                    int pickaxeRestockSlots = emptySlots - 1;
                    if (pickaxeRestockSlots <= 0) {
                        b.notifyDesktop(b.notifyRestockIssues, "THM Highway Builder", "Not enough empty slots to restock pickaxes.");
                        b.error("Not enough empty slots to restock pickaxes.");
                        return;
                    }

                    minimumSlots = Math.min(pickaxeRestockSlots, b.restockPickaxesAmount.get());
                }
                else {
                    int restockSlots = emptySlots - b.minEmpty.get();
                    if (restockSlots <= 0) {
                        b.notifyDesktop(b.notifyRestockIssues, "THM Highway Builder", "No empty slots available for restocking items.");
                        b.error("No empty slots for restocking items.");
                        return;
                    }

                    minimumSlots = b.restockTask.materials ? restockSlots : 1;
                }

                HorizontalDirection dir = b.dir.diagonal ? b.dir.rotateLeft().rotateLeftSkipOne() : b.dir.opposite();
                pos.set(b.mc.player).offset(dir);
                b.restockDebug("Restock.start container position set to %s, slot=%d, minimumSlots=%d.",
                    b.formatBlockPos(pos.getBlockPos()),
                    slot,
                    minimumSlots
                );
                if (slot >= 0) {
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
                breakContainer = b.mc.world.getBlockState(pos.getBlockPos()).getBlock() == Blocks.ENDER_CHEST;
                b.restockDebug("Restock.start breakContainer=%s because block at restock pos is %s.",
                    breakContainer,
                    b.mc.world.getBlockState(pos.getBlockPos()).getBlock()
                );

                indicateStopping = false;
                delayTimer = b.inventoryDelay.get();
            }

            @Override
            protected void tick(HighwayBuilderTHM b) {
                // this should only tick if there's a valid slot we can restock from
                if (slot == -1 && !foodShulkerReturnPending) {
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
                        && !b.protectUsefulCursorStackFromDrop("Restock.tick")) {
                        InvUtils.dropHand();
                    }
                    delayTimer = b.inventoryDelay.get();
                    return;
                }

                BlockPos blockPos = pos.getBlockPos();
                if (foodShulkerReturnPending && handleFoodShulkerReturn(b, blockPos)) return;

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
                    if (b.restockTask.food
                        && extractedFoodShulkerFromEnderChest
                        && b.mc.currentScreen instanceof ShulkerBoxScreen screen
                        && screen.getScreenHandler().syncId == b.syncId) {
                        Inventory inv = ((ShulkerBoxScreenHandlerAccessor) screen.getScreenHandler()).meteor$getInventory();
                        prepareFoodShulkerReturn(b, inv);
                    }
                    indicateStopping = true;
                    breakContainer = true;
                    stopTimer = 12;
                    if (b.mc.currentScreen != null) b.closeHandledScreen();
                    return;
                }
                if (b.restockTask.canTransitionToMineEnderChests() && !indicateStopping) {
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
                            b.restockTask.markCurrentSourceExhausted(RestockTask.SourcePhase.InventoryShulkers);
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

                            if (restockItems(b, inv)) {
                                delayTimer = b.inventoryDelay.get();
                                return;
                            }

                            // we may have taken items themselves from the ec, but still need more. Now we try to find a shulker containing the items
                            if (b.searchShulkers.get()) {
                                int moveTo = InvUtils.findEmpty().slot();

                                if (moveTo != -1) {
                                    int shulkerSlot = findShulkerSlotInInventory(inv);
                                    if (shulkerSlot != -1) {
                                        InvUtils.shiftClick().slotId(shulkerSlot);
                                        if (b.restockTask.food) trackExtractedFoodShulkerFromEnderChest(b, moveTo);
                                        delayTimer = b.inventoryDelay.get();
                                    }
                                } else {
                                    b.restockTask.markBlockedByStaging(RestockTask.SourcePhase.EnderChest);
                                    b.restockDebug("Restock.tick marked source blocked by staging while extracting shulkers from ender chest.");
                                }
                            }

                            // if it reaches here, we have taken everything we can from your ender chest, and may have also grabbed a shulker
                            // we should be finished in your ender chest, so we can break it and either continue on our way or start checking shulkers
                            b.restockTask.markCurrentSourceExhausted(RestockTask.SourcePhase.EnderChest);
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
                        BlockUtils.place(blockPos, Hand.MAIN_HAND, slot, b.rotation.get().place, 0, true, true, true);
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
                return b.restockTask.materials
                    && b.mineEnderChests.get()
                    && b.blocksToPlace.get().contains(Blocks.OBSIDIAN)
                    && b.restockTask.needsMoreRawEchestsForSession()
                    && countItem(b, stack -> stack.getItem().equals(Items.ENDER_CHEST)) > b.saveEchests.get();
            }

            private boolean restockItems(HighwayBuilderTHM b, Inventory inv) {
                if (b.restockTask.materials) {
                    // take raw material
                    if (grabFromInventory(b, inv, itemStack -> itemStack.getItem() instanceof BlockItem bi && b.blocksToPlace.get().contains(bi.getBlock()))) return true;

                    // prefer taking raw material before echests
                    if (b.blocksToPlace.get().contains(Blocks.OBSIDIAN) && needsMoreRawEchests(b)) {
                        if (grabFromInventory(b, inv, itemStack -> itemStack.getItem() == Items.ENDER_CHEST)) return true;
                    }
                }
                if (b.restockTask.pickaxes) {
                    if (grabFromInventory(b, inv, itemStack -> itemStack.isIn(ItemTags.PICKAXES))) return true;
                }
                if (b.restockTask.food) {
                    return grabFromInventory(b, inv, b::isConfiguredFoodStack);
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
                    if (!Utils.isShulker(itemStack.getItem())) return false;
                    Utils.getItemsInContainerItem(itemStack, ITEMS);

                    for (ItemStack stack : ITEMS) {
                        if (b.restockTask.materials && stack.getItem() instanceof BlockItem bi) {
                            if (b.blocksToPlace.get().contains(bi.getBlock()) || (b.blocksToPlace.get().contains(Blocks.OBSIDIAN) && bi == Items.ENDER_CHEST && needsMoreRawEchests(b))) return true;
                        }
                        if (b.restockTask.pickaxes && stack.isIn(ItemTags.PICKAXES)) return true;
                        if (b.restockTask.food && b.isConfiguredFoodStack(stack)) return true;
                    }

                    return false;
                };
            }

            private int findShulkerSlotInInventory(Inventory inv) {
                for (int i = 0; i < inv.size(); i++) {
                    if (shulkerPredicate.test(inv.getStack(i))) return i;
                }

                return -1;
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

            private void prepareFoodShulkerReturn(HighwayBuilderTHM b, Inventory inv) {
                if (!extractedFoodShulkerFromEnderChest || foodShulkerReturnPending) return;

                foodShulkerReturnInventorySlot = slot;
                if (b.isContainerInventoryEmpty(inv)) {
                    b.restockDebug("Extracted food shulker became empty after pull; keeping it in inventory for later cleanup.");
                    clearFoodReturnTracking();
                    return;
                }

                foodShulkerReturnPending = true;
                foodShulkerReturnRetryUsed = false;
                foodShulkerReturnFinalizeAfterBreak = false;
                foodShulkerReturnWaitTicks = 20;
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

                    BlockUtils.place(blockPos, Hand.MAIN_HAND, echestSlot, b.rotation.get().place, 0, true, true, true);
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
                    foodShulkerReturnWaitTicks = 20;
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
                if (foodShulkerReturnInventorySlot < 0 || foodShulkerReturnInventorySlot >= b.mc.player.getInventory().getMainStacks().size()) return -1;
                ItemStack stack = b.mc.player.getInventory().getStack(foodShulkerReturnInventorySlot);
                return Utils.isShulker(stack.getItem()) ? foodShulkerReturnInventorySlot : -1;
            }

            private void clearFoodReturnTracking() {
                extractedFoodShulkerFromEnderChest = false;
                foodShulkerReturnPending = false;
                foodShulkerReturnRetryUsed = false;
                foodShulkerReturnFinalizeAfterBreak = false;
                foodShulkerReturnWaitTicks = 0;
                foodShulkerReturnInventorySlot = -1;
            }

            private boolean needsMoreRawEchests(HighwayBuilderTHM b) {
                if (!b.blocksToPlace.get().contains(Blocks.OBSIDIAN)) return false;
                return b.restockTask.needsMoreRawEchestsForSession();
            }

            private boolean hasShulkerInInventory(HighwayBuilderTHM b) {
                for (int i = 0; i < b.mc.player.getInventory().getMainStacks().size(); i++) {
                    if (shulkerPredicate.test(b.mc.player.getInventory().getStack(i))) return true;
                }
                return false;
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

            private int countSlots(HighwayBuilderTHM b, Predicate<ItemStack> predicate) {
                int count = 0;
                for (int i = 0; i < b.mc.player.getInventory().getMainStacks().size(); i++) {
                    ItemStack stack = b.mc.player.getInventory().getStack(i);
                    if (predicate.test(stack)) count++;
                }

                return count;
            }
        },

        PlaceShulkerBlockade {
            @Override
            protected void tick(HighwayBuilderTHM b) {
                int slot = findBlocksToPlacePrioritizeTrash(b);
                if (slot == -1) {
                    b.restockDebug("PlaceShulkerBlockade.tick could not find a block slot for blockade placement.");
                    return;
                }

                place(b, b.blockPosProvider.getBlockade(false, b.blockadeType.get()), slot, Restock);
            }
        },

        MineShulkerBlockade {
            private boolean stopTimerEnabled;
            private int stopTimer;

            @Override
            protected void start(HighwayBuilderTHM b) {
                b.restockTask.setBlockadeReady(false);
                b.restockDebug("MineShulkerBlockade.start(blockadeType=%s, lastState=%s)", b.blockadeType.get(), b.stateName(b.lastState));
                stopTimerEnabled = false;
                if (b.lastState == this) {
                    stopTimerEnabled = true;
                    stopTimer = 12;
                    b.restockDebug("MineShulkerBlockade.start entering stop timer cleanup.");
                }
            }

            @Override
            protected void tick(HighwayBuilderTHM b) {
                if (!stopTimerEnabled) {
                    // mining b.blockadeType instead of BlockadeType.Shulker is the fastest fix to the module leaving
                    // some blocks behind if you start a pickaxe restock task while mining echests
                    mine(b, b.blockPosProvider.getBlockade(true, b.blockadeType.get()), true, this, this);
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
                        BlockUtils.canBreak(pos.getBlockPos(), pos.getState())
                            && (mineBlocksToPlace || !b.blocksToPlace.get().contains(pos.getState().getBlock()))
                            && !BlockUtils.canInstaBreak(pos.getBlockPos()) && (!Modules.get().get(SpeedMine.class).instamine() || pos.getState().calcBlockBreakingDelta(b.mc.player, b.mc.world, pos.getBlockPos()) <= 0.5)
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
                if (b.count >= b.blocksPerTick.get()) return;
                if (b.breakTimer > 0) return;

                BlockState state = pos.getState();
                if (state.isAir() || (!mineBlocksToPlace && b.blocksToPlace.get().contains(state.getBlock()))) continue;
                if (b.shouldSkipSignBreak(pos.getBlockPos(), state)) continue;

                int slot = findAndMoveBestToolToHotbar(b, state, false);
                if (slot == -1) return;

                if (slot != b.mc.player.getInventory().getSelectedSlot()) InvUtils.swap(slot, false);

                BlockPos mcPos = pos.getBlockPos();
                boolean multiBreak = b.blocksPerTick.get() > 1 && BlockUtils.canInstaBreak(mcPos) && !b.rotation.get().mine;
                if (BlockUtils.canBreak(mcPos)) {
                    if (b.rotation.get().mine) Rotations.rotate(Rotations.getYaw(mcPos), Rotations.getPitch(mcPos), () -> BlockUtils.breakBlock(mcPos, true));
                    else BlockUtils.breakBlock(mcPos, true);
                    breaking = true;

                    b.breakTimer = b.breakDelay.get();

                    if (!b.lastBreakingPos.equals(pos)) {
                        b.lastBreakingPos.set(pos);
                        b.recordBlockBroken();
                    }

                    b.count++;

                    // can only multi break if we aren't rotating and the block can be insta-mined
                    if (!multiBreak) break;
                }

                if (!it.hasNext() && BlockUtils.canInstaBreak(mcPos)) finishedBreaking = true;
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

            if (b.restockDebugLog.get() && (this == PlaceShulkerBlockade || this == PlaceEChestBlockade)) {
                b.restockDebug("%s tick using hotbar slot %d and blockade=%s.",
                    b.stateName(this),
                    slot,
                    b.blockadeType.get()
                );
                b.logRestockBlockadeProbe(b.stateName(this), it);
            }

            for (MBlockPos pos : it) {
                scannedTargets++;
                if (b.count >= it.placementsPerTick(b)) {
                    if (b.restockDebugLog.get() && (this == PlaceShulkerBlockade || this == PlaceEChestBlockade)) {
                        b.restockDebug("%s paused: placement count limit reached (%d/%d).",
                            b.stateName(this),
                            b.count,
                            it.placementsPerTick(b)
                        );
                    }
                    return;
                }
                if (b.placeTimer > 0) {
                    if (b.restockDebugLog.get() && (this == PlaceShulkerBlockade || this == PlaceEChestBlockade)) {
                        b.restockDebug("%s paused: placeTimer=%d before attempting %s.",
                            b.stateName(this),
                            b.placeTimer,
                            b.formatBlockPos(pos.getBlockPos())
                        );
                    }
                    return;
                }

                if (pos.getBlockPos().getSquaredDistance(b.mc.player.getEyePos()) > b.placeRange.get() * b.placeRange.get()) {
                    if (b.restockDebugLog.get() && (this == PlaceShulkerBlockade || this == PlaceEChestBlockade)) {
                        b.restockDebug("%s skipped %s: out of range.",
                            b.stateName(this),
                            b.formatBlockPos(pos.getBlockPos())
                        );
                    }
                    continue;
                }

                // CheckEntities & SwapBack are disabled for waiting for better accuracy and speed of the builder
                boolean placedThisTick = BlockUtils.place(pos.getBlockPos(), Hand.MAIN_HAND, slot, b.rotation.get().place, 0, true, true, true);

                if (b.restockDebugLog.get() && (this == PlaceShulkerBlockade || this == PlaceEChestBlockade)) {
                    BlockState stateAfterAttempt = b.mc.world.getBlockState(pos.getBlockPos());
                    b.restockDebug("%s attempt %s with slot %d -> success=%s, stateNow=%s, canPlaceNow=%s",
                        b.stateName(this),
                        b.formatBlockPos(pos.getBlockPos()),
                        slot,
                        placedThisTick,
                        stateAfterAttempt.getBlock(),
                        BlockUtils.canPlace(pos.getBlockPos())
                    );
                }

                if (placedThisTick) {
                    placed = true;
                    b.recordBlockPlaced();
                    b.placeTimer = b.placeDelay.get();

                    b.count++;
                    if (b.placementsPerTick.get() == 1) break;
                }

                if (!it.hasNext()) finishedPlacing = true;
            }

            if (b.restockDebugLog.get() && (this == PlaceShulkerBlockade || this == PlaceEChestBlockade)) {
                b.restockDebug("%s completed tick: scanned=%d placedAny=%s finishedPlacing=%s next=%s",
                    b.stateName(this),
                    scannedTargets,
                    placed,
                    finishedPlacing,
                    b.stateName(nextState)
                );
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
                if (b.trashItems.get().contains(itemStack.getItem())) thrashSlot = i;

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

        protected boolean hasItem(HighwayBuilderTHM b, Predicate<ItemStack> predicate) {
            for (int i = 0; i < b.mc.player.getInventory().getMainStacks().size(); i++) {
                if (predicate.test(b.mc.player.getInventory().getStack(i))) return true;
            }

            return false;
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
            if (!b.clearCursorStackToEmptySlot("findAndMoveToHotbar") && !b.protectUsefulCursorStackFromDrop("findAndMoveToHotbar")) InvUtils.dropHand();

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

            if (!b.clearCursorStackToEmptySlot("findAndMoveToHotbar-cursor") && !b.protectUsefulCursorStackFromDrop("findAndMoveToHotbar-cursor")) InvUtils.dropHand();

            return predicate.test(b.mc.player.getInventory().getStack(hotbarSlot)) ? hotbarSlot : -1;
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
                    return !b.dontBreakTools.get() || itemStack.getMaxDamage() - itemStack.getDamage() > (itemStack.getMaxDamage() * (b.breakDurability.get() / 100));
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
                if (count <= b.savePickaxes.get() && !(b.restockTask.pickaxes && bestStack.getMaxDamage() - bestStack.getDamage() > (bestStack.getMaxDamage() * (b.breakDurability.get() / 100)))) {
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
            if (!b.clearCursorStackToEmptySlot("findAndMoveBestToolToHotbar") && !b.protectUsefulCursorStackFromDrop("findAndMoveBestToolToHotbar")) InvUtils.dropHand();

            return hotbarSlot;
        }

        protected int findBlocksToPlace(HighwayBuilderTHM b) {
            // find a block and move it to your hotbar
            int slot = findAndMoveToHotbar(b, itemStack -> itemStack.getItem() instanceof BlockItem blockItem && b.blocksToPlace.get().contains(blockItem.getBlock()));

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
                if (
                    (b.searchEnderChest.get() || b.searchShulkers.get())
                    || (b.mineEnderChests.get() && b.blocksToPlace.get().contains(Blocks.OBSIDIAN) && countItem(b, stack -> stack.getItem().equals(Items.ENDER_CHEST)) > b.saveEchests.get())
                ) {
                    b.restockTask.setMaterials();
                } else if (b.kitbotRestock.get()) {
                    b.setState(KitbotOrder);
                } else {
                    b.notifyDesktop(b.notifyOutOfBlocks, "THM Highway Builder", "Out of blocks to place.");
                    b.error("Out of blocks to place.");
                }

                return -1;
            }

            return slot;
        }

        protected int findBlocksToPlacePrioritizeTrash(HighwayBuilderTHM b) {
            int slot = findAndMoveToHotbar(b, itemStack -> {
                if (!(itemStack.getItem() instanceof BlockItem)) return false;
                return b.trashItems.get().contains(itemStack.getItem());
            });

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
            return b.placementsPerTick.get();
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
            this.direction = BlockUtils.getDirection(pos);
            this.packet = false;
        }

        public DoubleMineBlock startDestroying() {
            b.mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, this.blockPos, this.direction));
            normalStartTime = b.mc.player.age;
            return this;
        }

        public DoubleMineBlock stopDestroying() {
            b.mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, this.blockPos, this.direction));
            return this;
        }

        public DoubleMineBlock packetMine() {
            packetStartTime = b.mc.player.age;
            packet = true;
            return stopDestroying();
        }

        public boolean isReady() {
            return progress() >= (b.fastBreak.get() ? 0.7 : 1.0);
        }

        public boolean shouldRemove() {
            boolean distance = !packet && Utils.distance(b.mc.player.getEyePos().x, b.mc.player.getEyePos().y, b.mc.player.getEyePos().z, blockPos.getX() + direction.getOffsetX(), blockPos.getY() + direction.getOffsetY(), blockPos.getZ() + direction.getOffsetZ()) > b.mc.player.getBlockInteractionRange();

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
        public boolean materials;
        public boolean pickaxes;
        public boolean food;
        private boolean pendingMaterials;
        private boolean pendingPickaxes;
        private boolean pendingFood;
        private boolean sequenceActive;
        private boolean blockadeReady;
        private boolean blockadeTeardownPending;
        private int pickaxeStartCount;
        private long blockadeLeaseGenerationCounter;
        private RestockSession session;
        private final Deque<Type> pendingQueue = new ArrayDeque<>();
        private final HighwayBuilderTHM b;

        private enum Type {
            Materials,
            Pickaxes,
            Food
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
            SOURCE_EXHAUSTED,
            BLOCKED_BY_STAGING
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
            private BlockadeType blockadeType = BlockadeType.Partial;
            private BlockadeLeaseState state = BlockadeLeaseState.NOT_BUILT;
            private long lastValidatedTick;

            private void markBuilt() {
                generationId = ++blockadeLeaseGenerationCounter;
                blockadeType = b.blockadeType.get();
                if (b.mc.player != null) anchorPos.set(b.mc.player.getBlockPos());
                state = BlockadeLeaseState.BUILT;
                lastValidatedTick = b.mc.player != null ? b.mc.player.age : 0L;
            }

            private void markTeardownPending() {
                state = BlockadeLeaseState.TEARDOWN_PENDING;
                lastValidatedTick = b.mc.player != null ? b.mc.player.age : lastValidatedTick;
            }

            private void invalidate() {
                state = BlockadeLeaseState.INVALIDATED;
                lastValidatedTick = b.mc.player != null ? b.mc.player.age : lastValidatedTick;
            }

            private void reset() {
                generationId = 0L;
                anchorPos.set(0, 0, 0);
                blockadeType = BlockadeType.Partial;
                state = BlockadeLeaseState.NOT_BUILT;
                lastValidatedTick = 0L;
            }

            private boolean validateCurrentAnchor() {
                if (state != BlockadeLeaseState.BUILT) return false;
                if (b.mc.player == null || b.mc.world == null) return false;
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
        }

        private final class RestockSession {
            private final Type taskType;
            private final BlockadeLease blockadeLease = new BlockadeLease();
            private SourcePhase phase;
            private SourceAttemptResult lastResult;
            private boolean usingGreatestAvailable;
            private boolean targetInitialized;
            private boolean inventoryShulkersExhausted;
            private boolean enderChestExhausted;
            private boolean mineEnderChestsExhausted;
            private boolean blockedByStaging;
            private int targetFinal;
            private int remainingTarget;
            private int workingStageCapacity;
            private int pickaxesStartCount;
            private int materialStartStacks;
            private int foodStartItems;
            private int obsidianStartItems;
            private int pickaxesAcquiredCount;
            private int materialStacksAcquired;
            private int foodItemsAcquired;
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
                materialStartStacks = countInventorySlots(this::isTrackedMaterialStack);
                foodStartItems = countInventoryItems(this::isTrackedFoodStack);
                obsidianStartItems = countInventoryItems(itemStack -> itemStack.getItem() == Items.OBSIDIAN);
            }

            private void ensureTargetInitialized() {
                if (targetInitialized || b.mc.player == null) return;

                int freeSlots = countEmptyInventorySlots();
                int usableFreeSlots = Math.max(freeSlots - b.minEmpty.get(), 0);
                workingStageCapacity = usableFreeSlots;

                targetFinal = switch (taskType) {
                    case Pickaxes -> Math.min(Math.max(freeSlots - 1, 0), b.restockPickaxesAmount.get());
                    case Materials -> isObsidianTask() ? usableFreeSlots * 64 : usableFreeSlots;
                    case Food -> 1;
                };

                remainingTarget = Math.max(targetFinal, 0);
                targetInitialized = targetFinal > 0;
                refreshProgress();
            }

            private void refreshProgress() {
                if (b.mc.player == null) return;

                workingStageCapacity = Math.max(countEmptyInventorySlots() - b.minEmpty.get(), 0);
                pickaxesAcquiredCount = Math.max(countInventoryItems(itemStack -> itemStack.isIn(ItemTags.PICKAXES)) - pickaxesStartCount, 0);
                materialStacksAcquired = Math.max(countInventorySlots(this::isTrackedMaterialStack) - materialStartStacks, 0);
                foodItemsAcquired = Math.max(countInventoryItems(this::isTrackedFoodStack) - foodStartItems, 0);
                obsidianItemsAcquired = Math.max(countInventoryItems(itemStack -> itemStack.getItem() == Items.OBSIDIAN) - obsidianStartItems, 0);

                int looseEchestsInInventory = countInventoryItems(itemStack -> itemStack.getItem() == Items.ENDER_CHEST);
                reserveRemaining = Math.max(saveEchestsReserve - looseEchestsInInventory, 0);
                usablePulledEchests = Math.max(looseEchestsInInventory - saveEchestsReserve, 0);

                remainingTarget = Math.max(targetFinal - getProgressTowardsTarget(), 0);
                if (isTargetSatisfied()) phase = SourcePhase.Complete;
            }

            private void notePhase(SourcePhase phase) {
                this.phase = phase;
                if (b.restockDebugLog.get()) {
                    b.restockDebug("RestockSession phase=%s task=%s target=%d remaining=%d greatest=%s staging=%d reserveRemaining=%d usableEchests=%d.",
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

            private int getProgressTowardsTarget() {
                return switch (taskType) {
                    case Pickaxes -> pickaxesAcquiredCount;
                    case Materials -> isObsidianTask() ? obsidianItemsAcquired : materialStacksAcquired;
                    case Food -> foodItemsAcquired;
                };
            }

            private boolean isTargetSatisfied() {
                return targetInitialized && getProgressTowardsTarget() >= targetFinal;
            }

            private boolean shouldCompleteAfterInventoryShulkers() {
                if (taskType == Type.Materials && isObsidianTask()) return false;
                return getProgressTowardsTarget() > 0;
            }

            private boolean canTransitionToMineEnderChests() {
                if (!isObsidianTask()) return false;
                return usablePulledEchests > 0 && remainingTarget > 0;
            }

            private int getRemainingObsidianItems() {
                return Math.max(targetFinal - obsidianItemsAcquired, 0);
            }

            private int getObsidianItemsTarget() {
                return targetFinal;
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
                    case InventoryShulkers -> inventoryShulkersExhausted = true;
                    case EnderChest -> enderChestExhausted = true;
                    case MineEnderChests -> mineEnderChestsExhausted = true;
                    default -> { }
                }
            }

            private void markBlockedByStaging(SourcePhase phase) {
                blockedByStaging = true;
                lastResult = SourceAttemptResult.BLOCKED_BY_STAGING;
                markSourceExhausted(phase);
            }

            private void clearBlockedByStaging() {
                blockedByStaging = false;
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

            private void finishSuccessfully() {
                phase = SourcePhase.Complete;
                lastResult = SourceAttemptResult.TARGET_SATISFIED;
                remainingTarget = 0;
            }

            private void fail() {
                phase = SourcePhase.Failed;
            }

            private boolean shouldAttemptKitbot() {
                return !isTargetSatisfied() && !isMineEnderChestsExhausted();
            }

            private boolean hasBlockingStagingShortage() {
                return blockedByStaging && workingStageCapacity <= 0;
            }

            private boolean needsMoreRawEchests() {
                if (!isObsidianTask()) return false;
                int remainingObsidianItems = getRemainingObsidianItems();
                int usableLooseEchests = Math.max(countInventoryItems(itemStack -> itemStack.getItem() == Items.ENDER_CHEST) - saveEchestsReserve, 0);
                int remainingAfterLooseEchests = remainingObsidianItems - (usableLooseEchests * 8);
                return remainingAfterLooseEchests > 0;
            }

            private boolean isTrackedMaterialStack(ItemStack stack) {
                if (!(stack.getItem() instanceof BlockItem bi)) return false;
                if (isObsidianTask()) return bi.getBlock() == Blocks.OBSIDIAN;
                return b.blocksToPlace.get().contains(bi.getBlock()) && bi.getBlock() != Blocks.OBSIDIAN;
            }

            private boolean isTrackedFoodStack(ItemStack stack) {
                return b.isConfiguredFoodStack(stack);
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

        private void setTask(Type type) {
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
            } else if (sequenceActive && b.restockDebugLog.get()) {
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
            clearPending();
            sequenceActive = false;
            blockadeReady = false;
            blockadeTeardownPending = false;
            session = null;
        }

        public void completeActive() {
            materials = false;
            pickaxes = false;
            food = false;
        }

        public boolean tasksInactive() {
            return !materials && !pickaxes && !food;
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
            blockadeReady = value;
            if (session == null) return;
            if (value) session.blockadeLease.markBuilt();
            else session.blockadeLease.invalidate();
        }

        public void deferBlockadeTeardown() {
            blockadeTeardownPending = true;
            if (session != null) session.blockadeLease.markTeardownPending();
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

        public boolean shouldTearDownRestockBlockadeFromForward() {
            return blockadeTeardownPending && shouldTearDownRestockBlockade();
        }

        public void finishSequence() {
            completeActive();
            clearPending();
            sequenceActive = false;
            blockadeReady = false;
            blockadeTeardownPending = false;
            pickaxeStartCount = 0;
            if (session != null) session.blockadeLease.reset();
            session = null;
        }

        public String item() {
            if (materials) return "building materials";
            if (pickaxes) return "pickaxes";
            if (food) return "food";
            return "unknown";
        }

        public String activeSummary() {
            List<String> active = new ArrayList<>();
            if (materials) active.add("materials");
            if (pickaxes) active.add("pickaxes");
            if (food) active.add("food");
            return active.isEmpty() ? "none" : String.join(",", active);
        }

        public String pendingSummary() {
            List<String> pending = new ArrayList<>();
            if (pendingMaterials) pending.add("materials");
            if (pendingPickaxes) pending.add("pickaxes");
            if (pendingFood) pending.add("food");
            return pending.isEmpty() ? "none" : String.join(",", pending);
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
            onTaskActivated(type);
            session = new RestockSession(type);

            switch (type) {
                case Materials -> materials = true;
                case Pickaxes -> pickaxes = true;
                case Food -> food = true;
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
            };
        }

        private boolean isActive(Type type) {
            return switch (type) {
                case Materials -> materials;
                case Pickaxes -> pickaxes;
                case Food -> food;
            };
        }

        private boolean hasPendingTasks() {
            return pendingMaterials || pendingPickaxes || pendingFood;
        }

        private void onTaskActivated(Type type) {
            b.invalidRestockRecoveryRetries = 0;
            b.invalidRestockRecoveryPending = false;

            if (type == Type.Pickaxes) {
                pickaxeStartCount = countInventoryItems(itemStack -> itemStack.isIn(ItemTags.PICKAXES));
            }
        }

        public RestockSession getSession() {
            return session;
        }

        public void ensureSessionInitialized() {
            if (session == null) return;
            session.ensureTargetInitialized();
            session.refreshProgress();
        }

        public void notePhase(SourcePhase phase) {
            if (session != null) session.notePhase(phase);
        }

        public void refreshSessionProgress() {
            if (session != null) session.refreshProgress();
        }

        public boolean isTargetSatisfied() {
            return session != null && session.isTargetSatisfied();
        }

        public boolean shouldCompleteAfterInventoryShulkers() {
            return session != null && session.shouldCompleteAfterInventoryShulkers();
        }

        public boolean canTransitionToMineEnderChests() {
            return session != null && session.canTransitionToMineEnderChests();
        }

        public boolean needsMoreRawEchestsForSession() {
            return session != null && session.needsMoreRawEchests();
        }

        public boolean validateOrInvalidateBlockadeLease() {
            if (session == null) return false;
            boolean valid = session.blockadeLease.validateCurrentAnchor();
            if (!valid) blockadeReady = false;
            return valid;
        }

        public void markCurrentSourceExhausted(SourcePhase phase) {
            if (session != null) session.markSourceExhausted(phase);
        }

        public void markBlockedByStaging(SourcePhase phase) {
            if (session != null) session.markBlockedByStaging(phase);
        }

        public boolean hasBlockingStagingShortage() {
            return session != null && session.hasBlockingStagingShortage();
        }

        public void clearBlockedByStaging() {
            if (session != null) session.clearBlockedByStaging();
        }

        public boolean shouldAttemptKitbot() {
            return session != null && session.shouldAttemptKitbot();
        }

        public boolean isObsidianRestockSession() {
            return session != null && session.isObsidianTask();
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
            pendingQueue.clear();
        }

        private void clearPending(Type type) {
            switch (type) {
                case Materials -> pendingMaterials = false;
                case Pickaxes -> pendingPickaxes = false;
                case Food -> pendingFood = false;
            }
            pendingQueue.removeFirstOccurrence(type);
        }

        private boolean isPending(Type type) {
            return switch (type) {
                case Materials -> pendingMaterials;
                case Pickaxes -> pendingPickaxes;
                case Food -> pendingFood;
            };
        }

        private String item(Type type) {
            return switch (type) {
                case Materials -> "building materials";
                case Pickaxes -> "pickaxes";
                case Food -> "food";
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
```

## Notes And Assumptions

- `auto-screenshot-statistics=false` keeps the current best-effort behavior in the simulation.
- `auto-screenshot-statistics=true` makes screenshot completion part of durable finalization in the simulation.
- `screenshotRequired` is latched when the finalization record is created and is never re-derived from live settings during recovery.
- `screenshotFileName` is assigned once from the finalization lineage and then preserved across recovery.
- reprint-on-recovery is allowed only while screenshot proof is still owed.
- existing `webhookSendCommitted` and `apiSendCommitted` remain the authoritative at-most-once external send state.
- screenshot proof in the simulation requires `Files.exists(path)`, `Files.isRegularFile(path)`, and `Files.size(path) > 0`.

## Questions For Review

1. Is any state needed for screenshot-guaranteed recovery still missing from the simulated full-source clone?
2. Is any path still able to print, send externally, or retire before screenshot proof is durably completed?
3. Is any recovery branch still ambiguous about whether it should reprint, resend, or retire?
4. Is any filename or file-validation rule still too weak to safely prove screenshot completion?

Review guardrail:
This file is a simulation artifact only.
Do not implement from memory and do not edit any .java file during this review stage.
If implementation is approved later, the simulated full-source block must be treated as a specification, not as an already-applied code change.
