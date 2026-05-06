package xyz.thm.addon.modules;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.pathing.goals.GoalBlock;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.pathing.BaritoneUtils;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.misc.AutoReconnect;
import meteordevelopment.meteorclient.systems.modules.world.Timer;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.player.*;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.*;
import net.minecraft.block.enums.ChestType;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.gui.screen.ingame.HopperScreen;
import net.minecraft.client.gui.screen.ingame.ShulkerBoxScreen;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import xyz.thm.addon.THMAddon;
import xyz.thm.addon.utils.ServerStatusHandler;
import xyz.thm.addon.utils.ServerStatusHandler.ServerState;
import xyz.thm.addon.utils.THMUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

public class ObsidianFarmerTHM extends Module {
    private static final int COORDINATE_LIMIT = 30_000_000;
    private static final int DEFAULT_INTERACTION_DELAY_TICKS = 3;
    private static final long KITBOT_COOLDOWN_MS = 90_000L;
    private static final long KITBOT_TIMEOUT_MS = 180_000L;
    private static final long MAIN_SERVER_REJOIN_DELAY_MS = 6_000L;
    private static final long TRASH_DOOR_GRACE_MS = 2_000L;
    private static final int STORAGE_RECHECK_INTERVAL_TICKS = 60;
    private static final int PICKUP_WAIT_TICKS = 40;
    private static final int POST_RESTOCK_RESUME_DELAY_TICKS = 0;
    private static final String DEBUG_LOG_FILE_NAME = "obsidian-farmer-thm-debug.log";
    private static final float FARM_YAW = -135.0f;
    private static final float FARM_PITCH = 55.0f;
    private static final float CONTAINER_YAW = -90.0f;
    private static final float CONTAINER_PITCH = 55.0f;
    private static final float TRASH_YAW = 0.0f;
    private static final float TRASH_PITCH = -30.0f;
    private static final double POSE_POSITION_TOLERANCE = 0.01;
    private static final float POSE_ROTATION_TOLERANCE = 0.5f;
    private static final double KITBOT_POSE_SETTLE_TOLERANCE = 0.05;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgLocations = settings.createGroup("Locations");
    private final SettingGroup sgInventory = settings.createGroup("Inventory");
    private final SettingGroup sgRestock = settings.createGroup("Restock");

    private final CoordinateSetting depositShulkerLocation = addCoordinateSetting(sgLocations, "deposit-shulker", "Deposit shulker location.");
    private final CoordinateSetting monitoredDoubleChestLocation = addCoordinateSetting(sgLocations, "monitored-double-chest", "Double chest used to determine whether storage is full.");
    private final CoordinateSetting looseObsidianStorageLocation = addCoordinateSetting(sgLocations, "loose-obsidian-storage", "Container checked for loose obsidian before farming resumes.");
    private final CoordinateSetting emptyShulkerDepotLocation = addCoordinateSetting(sgLocations, "empty-shulker-depot", "Container used to store completely empty shulkers.");
    private final CoordinateSetting guardedDoorLocation = addCoordinateSetting(sgLocations, "guarded-door", "Door that should remain closed except during trash throwout.");
    private final CoordinateSetting startingPathBlock = addCoordinateSetting(sgLocations, "starting-path-block", "The southwest path block under the player's standing area.");

    private final Setting<Boolean> manageThmHwyMonitor = sgGeneral.add(new BoolSetting.Builder()
        .name("manage-thm-hwy-monitor")
        .description("Turns THMHwyMonitor on while the farmer is active and off when the farmer disables.")
        .defaultValue(false)
        .visible(THMUtils::isBaritoneInstalled)
        .onChanged(value -> {
            if (!isActive()) return;
            if (value) syncThmHwyMonitorOnActivate();
            else disableThmHwyMonitorIfActive();
        })
        .build()
    );

    private final Setting<Boolean> manageHotbar = sgInventory.add(new BoolSetting.Builder()
        .name("manage-hotbar")
        .description("Turns Hotbar Manager on while the farmer is active and off when the farmer disables.")
        .defaultValue(true)
        .onChanged(value -> {
            if (!isActive()) return;
            if (value) syncHotbarManagerOnActivate();
            else disableHotbarManagerIfActive();
        })
        .build()
    );

    private final Setting<Integer> savePickaxes = sgRestock.add(new IntSetting.Builder()
        .name("save-pickaxes")
        .description("Trigger pickaxe restock when loose pickaxes are at or below this count.")
        .defaultValue(1)
        .range(0, 36)
        .sliderRange(0, 9)
        .build()
    );

    private final Setting<Integer> minimumPickaxeRestock = sgRestock.add(new IntSetting.Builder()
        .name("minimum-pickaxe-restock")
        .description("How many pickaxes each pickaxe restock task tries to pull.")
        .defaultValue(1)
        .range(1, 36)
        .sliderRange(1, 9)
        .build()
    );

    private final Setting<Integer> saveFood = sgRestock.add(new IntSetting.Builder()
        .name("save-food")
        .description("Trigger food restock when loose configured food is at or below this count.")
        .defaultValue(16)
        .range(0, 64)
        .sliderRange(0, 32)
        .build()
    );

    private final Setting<List<Item>> foodTypes = sgRestock.add(new ItemListSetting.Builder()
        .name("food-types")
        .description("Food items that count for food restock decisions.")
        .defaultValue()
        .build()
    );

    private final Setting<Integer> minimumEchests = sgRestock.add(new IntSetting.Builder()
        .name("minimum-echests")
        .description("Additional loose echest reserve to keep on top of the two fallback-placement chests.")
        .defaultValue(1)
        .range(0, 64)
        .sliderRange(0, 16)
        .build()
    );

    private final Setting<Integer> minimumEmptySlots = sgRestock.add(new IntSetting.Builder()
        .name("minimum-empty-slots")
        .description("Minimum empty slots to preserve in the echest restock math.")
        .defaultValue(3)
        .range(0, 36)
        .sliderRange(0, 9)
        .build()
    );

    private final Setting<Integer> echestKitRestockAmount = sgRestock.add(new IntSetting.Builder()
        .name("echest-kit-restock-amount")
        .description("KitBot amount used for Echest kit orders.")
        .defaultValue(1)
        .range(1, 16)
        .sliderRange(1, 8)
        .build()
    );

    private final Setting<List<Item>> trashItems = sgInventory.add(new ItemListSetting.Builder()
        .name("trash-items")
        .description("Items that should be dropped during the pre-restock trash routine.")
        .defaultValue(
            Items.NETHERRACK, Items.QUARTZ, Items.GOLD_NUGGET, Items.GOLDEN_SWORD, Items.GLOWSTONE_DUST,
            Items.GLOWSTONE, Items.BLACKSTONE, Items.BASALT, Items.GHAST_TEAR, Items.SOUL_SAND, Items.SOUL_SOIL,
            Items.ROTTEN_FLESH, Items.MAGMA_BLOCK
        )
        .build()
    );

    private final Setting<Integer> inventoryDelay = sgInventory.add(new IntSetting.Builder()
        .name("inventory-delay")
        .description("Delay in ticks between inventory/container interactions.")
        .defaultValue(DEFAULT_INTERACTION_DELAY_TICKS)
        .range(0, 20)
        .sliderRange(0, 10)
        .build()
    );

    private enum FarmerState {
        WaitingForMainServer,
        WaitingForDoorClose,
        WaitingForStorage,
        WaitingForKitbotCooldown,
        Farming,
        Depositing,
        TrashThrowout,
        Restocking,
        ReconnectSuspended,
        HardFail
    }

    private enum RestockTask {
        None,
        Food,
        Pickaxe,
        Echest
    }

    private enum RestockStage {
        None,
        InventoryShulkers,
        WaitingForKitbotCooldown,
        WaitingForKitbotDelivery,
        SingleFallback,
        DoubleFallback
    }

    private enum ContainerMode {
        None,
        StorageCheck,
        LooseObsidianPickup,
        Deposit,
        EmptyShulkerDepot,
        LocalSourceShulker,
        SingleFallbackSearch,
        DoubleFallbackSearch
    }

    private enum ContainerStep {
        None,
        Prepare,
        Open,
        Transfer,
        Close,
        Break,
        WaitPickup,
        Finish
    }

    private enum ResumeStep {
        None,
        WaitingForMainServerReady,
        StartPathing,
        WaitingForPathing,
        SnapAndResume
    }

    private enum TrashStep {
        None,
        Prepare,
        VerifyDoorOpen,
        DropItems,
        WaitForDoorGrace
    }

    private enum KitbotRequest {
        None,
        Gapples,
        Pickaxe,
        Echest
    }

    private FarmerState state = FarmerState.WaitingForMainServer;
    private RestockTask activeRestockTask = RestockTask.None;
    private RestockStage activeRestockStage = RestockStage.None;
    private RestockStage lastKitbotPoseSettledStage = RestockStage.None;
    private ContainerMode containerMode = ContainerMode.None;
    private ContainerStep containerStep = ContainerStep.None;
    private ResumeStep resumeStep = ResumeStep.None;
    private TrashStep trashStep = TrashStep.None;
    private KitbotRequest pendingKitbotRequest = KitbotRequest.None;

    private long lastKitbotOrderAtMs;
    private long kitbotDeadlineAtMs;
    private long doorEnforcementSuppressedUntilMs;
    private long reconnectResumeCycleId;
    private long nextStorageRecheckAtTick;
    private int actionDelayTicks;
    private int genericWaitTicks;
    private int postRestockResumeDelayTicks;
    private int storageRecheckGeneration;
    private int localSourceInventorySlot = -1;
    private int pendingUsefulShulkerInventorySlot = -1;
    private int currentLocalSourceHotbarSlot = -1;
    private int kitbotBaselineMatchingShulkers;
    private int kitbotBaselineLooseUnits;
    private boolean restockNeedsTrashCheck;
    private boolean awaitingKitbotTeleport;
    private boolean kitbotRetryUsed;
    private boolean reconnectResumeArmed;
    private boolean reopenDepositAfterAutoBreak;
    private boolean postJoinDelayPending;
    private boolean pendingDepositAfterStorageCheck;
    private boolean storageChestFullOfShulkers;
    private boolean needsLooseObsidianPreFarmCheck;
    private boolean fallbackRecoveredUsefulShulker;
    private boolean fallbackRecoveredLooseItems;
    private boolean debugFileErrorLogged;
    private long postJoinDelayUntilMs;
    private BlockPos activeContainerPos;
    private BlockPos activeSecondaryContainerPos;
    private String hardFailMessage = "";
    private FarmerState lastTracedState;
    private RestockTask lastTracedRestockTask;
    private RestockStage lastTracedRestockStage;
    private ContainerMode lastTracedContainerMode;
    private ContainerStep lastTracedContainerStep;
    private ResumeStep lastTracedResumeStep;
    private TrashStep lastTracedTrashStep;
    private boolean lastTracedStorageFull;
    private boolean lastTracedReopenDeposit;
    private boolean lastTracedPostJoinDelay;
    private boolean lastTracedPendingDepositCheck;
    private boolean lastTracedWorkerActive;
    private boolean tunnelMinerConflictWarned;

    public ObsidianFarmerTHM() {
        super(THMAddon.MAIN, "Obsidian-farmer-thm", "Static obsidian farmer orchestrator that drives BetterEchestFarmer with reconnect-aware restock and storage handling.");
    }

    @Override
    public void onActivate() {
        state = FarmerState.WaitingForMainServer;
        activeRestockTask = RestockTask.None;
        activeRestockStage = RestockStage.None;
        lastKitbotPoseSettledStage = RestockStage.None;
        containerMode = ContainerMode.None;
        containerStep = ContainerStep.None;
        resumeStep = ResumeStep.None;
        trashStep = TrashStep.None;
        pendingKitbotRequest = KitbotRequest.None;
        hardFailMessage = "";
        lastKitbotOrderAtMs = 0L;
        kitbotDeadlineAtMs = 0L;
        doorEnforcementSuppressedUntilMs = 0L;
        reconnectResumeCycleId = 0L;
        nextStorageRecheckAtTick = 0L;
        actionDelayTicks = 0;
        genericWaitTicks = 0;
        postRestockResumeDelayTicks = 0;
        storageRecheckGeneration = 0;
        localSourceInventorySlot = -1;
        pendingUsefulShulkerInventorySlot = -1;
        currentLocalSourceHotbarSlot = -1;
        kitbotBaselineMatchingShulkers = 0;
        kitbotBaselineLooseUnits = 0;
        restockNeedsTrashCheck = false;
        awaitingKitbotTeleport = false;
        kitbotRetryUsed = false;
        reconnectResumeArmed = false;
        reopenDepositAfterAutoBreak = false;
        postJoinDelayPending = false;
        pendingDepositAfterStorageCheck = false;
        storageChestFullOfShulkers = false;
        needsLooseObsidianPreFarmCheck = true;
        fallbackRecoveredUsefulShulker = false;
        fallbackRecoveredLooseItems = false;
        debugFileErrorLogged = false;
        postJoinDelayUntilMs = 0L;
        activeContainerPos = null;
        activeSecondaryContainerPos = null;
        lastTracedState = null;
        lastTracedRestockTask = null;
        lastTracedRestockStage = null;
        lastTracedContainerMode = null;
        lastTracedContainerStep = null;
        lastTracedResumeStep = null;
        lastTracedTrashStep = null;
        lastTracedStorageFull = false;
        lastTracedReopenDeposit = false;
        lastTracedPostJoinDelay = false;
        lastTracedPendingDepositCheck = false;
        lastTracedWorkerActive = false;
        tunnelMinerConflictWarned = false;

        disableWorker("activate");
        syncHotbarManagerOnActivate();
        syncThmHwyMonitorOnActivate();
        traceDebug("=== activate === startBlock=%s deposit=%s storage=%s looseObby=%s emptyDepot=%s door=%s manageMonitor=%s manageHotbar=%s",
            formatBlockPosSafe(startingPathBlock.get()),
            formatBlockPosSafe(depositShulkerLocation.get()),
            formatBlockPosSafe(monitoredDoubleChestLocation.get()),
            formatBlockPosSafe(looseObsidianStorageLocation.get()),
            formatBlockPosSafe(emptyShulkerDepotLocation.get()),
            formatBlockPosSafe(guardedDoorLocation.get()),
            manageThmHwyMonitor.get(),
            manageHotbar.get()
        );
    }

    @Override
    public void onDeactivate() {
        traceDebug("=== deactivate === state=%s task=%s stage=%s container=%s/%s reason=module-toggle",
            state, activeRestockTask, activeRestockStage, containerMode, containerStep);
        lastKitbotPoseSettledStage = RestockStage.None;
        disableWorker("deactivate");
        cancelBaritonePathing();
        closeHandledScreen();
        disableHotbarManagerIfActive();
        disableThmHwyMonitorIfActive();
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        traceDebug("game-left manageMonitor=%s", manageThmHwyMonitor.get());
        disableWorker("disconnect");
        if (!manageThmHwyMonitor.get()) {
            postJoinDelayPending = true;
            postJoinDelayUntilMs = 0L;
        }
    }

    @EventHandler
    private void onReceiveMessage(ReceiveMessageEvent event) {
        if (!isActive()) return;
        String msg = event.getMessage().getString();
        if (awaitingKitbotTeleport && msg.contains(KitbotFrontend.KITBOT_NAME + " wants to teleport to you")) {
            ChatUtils.sendPlayerMsg("/tpy " + KitbotFrontend.KITBOT_NAME);
            awaitingKitbotTeleport = false;
            info("Accepted KitBot teleport request for the active restock order.");
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) {
            disableWorker("player-or-world-missing");
            return;
        }

        if (actionDelayTicks > 0) {
            actionDelayTicks--;
            return;
        }

        traceRuntimeChanges();

        if (postRestockResumeDelayTicks > 0) {
            resetTimerOverride();
            postRestockResumeDelayTicks--;
            disableWorker("post-restock-pickup-wait");
            state = FarmerState.Restocking;
            return;
        }

        if (!isExecutionAllowedOnCurrentServer()) {
            resetTimerOverride();
            state = FarmerState.WaitingForMainServer;
            disableWorker("off-main-server");
            return;
        }

        if (isTunnelMinerConflictActive()) {
            resetTimerOverride();
            state = FarmerState.WaitingForMainServer;
            disableWorker("tunnel-miner-conflict");
            if (!tunnelMinerConflictWarned) {
                tunnelMinerConflictWarned = true;
                warning("Tunnel Miner is active. Obsidian Farmer Thm is paused until Tunnel Miner is turned off.");
                traceDebug("conflict: tunnel miner active, farmer paused");
            }
            return;
        }
        tunnelMinerConflictWarned = false;

        if (resumeStep != ResumeStep.None) {
            resetTimerOverride();
            handleReconnectResume();
            return;
        }

        if (handlePostJoinMainServerDelay()) return;

        if (trashStep != TrashStep.None) {
            resetTimerOverride();
            handleTrashRoutine();
            return;
        }

        if (containerMode != ContainerMode.None) {
            resetTimerOverride();
            handleActiveContainerMode();
            return;
        }

        if (!isDoorEnforcementSuppressed() && !ensureGuardedDoorClosed()) {
            resetTimerOverride();
            state = FarmerState.WaitingForDoorClose;
            disableWorker("door-open");
            return;
        }

        if (!isShulkerBlock(depositShulkerLocation.get())) {
            resetTimerOverride();
            state = FarmerState.WaitingForStorage;
            disableWorker("storage-unavailable");
            return;
        }

        if (shouldHandleDepositNow()) {
            resetTimerOverride();
            if (!isUsableContainerBlock(resolveUsableContainerInteractionPos(monitoredDoubleChestLocation.get()))) {
                state = FarmerState.WaitingForStorage;
                disableWorker("storage-unavailable");
                return;
            }

            if (pendingDepositAfterStorageCheck) {
                pendingDepositAfterStorageCheck = false;
                startDepositWorkflow(reopenDepositAfterAutoBreak);
                return;
            }

            if (storageChestFullOfShulkers && mc.world.getTime() < nextStorageRecheckAtTick) {
                state = FarmerState.WaitingForStorage;
                disableWorker("storage-full");
                return;
            }

            startStorageInspection();
            return;
        }

        if (hasCompletelyEmptyShulkerInInventory()) {
            resetTimerOverride();
            startEmptyShulkerDepotRouting();
            return;
        }

        if (activeRestockTask == RestockTask.None) {
            RestockTask nextTask = selectHighestPriorityRestockTask();
            if (nextTask != RestockTask.None) beginRestockTask(nextTask);
        }

        if (activeRestockTask != RestockTask.None) {
            resetTimerOverride();
            handleRestockTask();
            return;
        }

        if (needsLooseObsidianPreFarmCheck) {
            resetTimerOverride();
            BlockPos looseObbyPos = resolveUsableContainerInteractionPos(looseObsidianStorageLocation.get());
            if (!isUsableContainerBlock(looseObbyPos)) {
                state = FarmerState.WaitingForStorage;
                disableWorker("loose-obsidian-storage-unavailable");
                return;
            }

            startLooseObsidianPickupWorkflow();
            return;
        }

        if (isWorkerActive() && !isAtPose(getFarmingPose(), FARM_YAW, FARM_PITCH)) {
            traceDebug("worker-pose-drift detected while active; disabling worker current=%s yaw=%.2f pitch=%.2f",
                formatPlayerPosSafe(),
                mc.player.getYaw(),
                mc.player.getPitch()
            );
            disableWorker("farming-pose-drift");
            actionDelayTicks = Math.max(actionDelayTicks, 1);
            return;
        }

        if (!ensureFarmingPose()) return;
        enableWorkerIfNeeded();
        state = FarmerState.Farming;
    }

    public boolean isManagingThmHwyMonitor() {
        return manageThmHwyMonitor.get();
    }

    public void onMonitorReconnectMainServerReady(long cycleId, String contextTag) {
        if (!isActive()) return;
        reconnectResumeCycleId = cycleId;
        reconnectResumeArmed = true;
        postJoinDelayPending = false;
        postJoinDelayUntilMs = 0L;
        resumeStep = ResumeStep.StartPathing;
        disableWorker("monitor-main-server-ready");
        state = FarmerState.ReconnectSuspended;
        traceDebug("monitor-main-server-ready cycle=%d context=%s", cycleId, contextTag);
        info("Reconnect returned to MAIN_SERVER. Resuming obsidian farmer setup now.");
    }

    public void onMonitorReconnectFailure(long cycleId, String reason, String detail) {
        if (!isActive()) return;
        reconnectResumeCycleId = 0L;
        reconnectResumeArmed = false;
        postJoinDelayPending = false;
        postJoinDelayUntilMs = 0L;
        resumeStep = ResumeStep.None;
        disableWorker("monitor-reconnect-failure");
        state = FarmerState.ReconnectSuspended;
        traceDebug("monitor-reconnect-failure cycle=%d reason=%s detail=%s", cycleId, reason, detail);
        warning("Reconnect failed (%s): %s", reason, detail == null ? "" : detail);
    }

    private CoordinateSetting addCoordinateSetting(SettingGroup group, String prefix, String description) {
        Setting<Integer> x = group.add(new IntSetting.Builder()
            .name(prefix + "-x")
            .description(description)
            .defaultValue(0)
            .range(-COORDINATE_LIMIT, COORDINATE_LIMIT)
            .sliderRange(-1_000, 1_000)
            .build()
        );
        Setting<Integer> y = group.add(new IntSetting.Builder()
            .name(prefix + "-y")
            .description(description)
            .defaultValue(0)
            .range(-128, 512)
            .sliderRange(0, 256)
            .build()
        );
        Setting<Integer> z = group.add(new IntSetting.Builder()
            .name(prefix + "-z")
            .description(description)
            .defaultValue(0)
            .range(-COORDINATE_LIMIT, COORDINATE_LIMIT)
            .sliderRange(-1_000, 1_000)
            .build()
        );
        return new CoordinateSetting(x, y, z);
    }

    private record CoordinateSetting(Setting<Integer> x, Setting<Integer> y, Setting<Integer> z) {
        private BlockPos get() {
            return new BlockPos(x.get(), y.get(), z.get());
        }
    }

    private void handleReconnectResume() {
        if (!reconnectResumeArmed) {
            resumeStep = ResumeStep.None;
            reconnectResumeCycleId = 0L;
            return;
        }
        if (!isExecutionAllowedOnCurrentServer()) return;

        switch (resumeStep) {
            case StartPathing -> {
                if (!BaritoneUtils.IS_AVAILABLE) {
                    traceDebug("resume-start-pathing failed: baritone unavailable");
                    warning("Baritone is not available, so reconnect resume is staying suspended.");
                    reconnectResumeArmed = false;
                    reconnectResumeCycleId = 0L;
                    resumeStep = ResumeStep.None;
                    return;
                }

                IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
                baritone.getPathingBehavior().cancelEverything();
                baritone.getCustomGoalProcess().setGoalAndPath(new GoalBlock(startingPathBlock.get()));
                traceDebug("resume-start-pathing goal=%s", formatBlockPosSafe(startingPathBlock.get()));
                actionDelayTicks = 4;
                resumeStep = ResumeStep.WaitingForPathing;
            }
            case WaitingForPathing -> {
                if (mc.player.getBlockPos().equals(startingPathBlock.get())) {
                    traceDebug("resume-pathing reached start block");
                    cancelBaritonePathing();
                    resumeStep = ResumeStep.SnapAndResume;
                    return;
                }

                if (!BaritoneUtils.IS_AVAILABLE) {
                    traceDebug("resume-pathing fallback: baritone missing");
                    resumeStep = ResumeStep.SnapAndResume;
                    return;
                }

                IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
                if (!baritone.getPathingBehavior().isPathing()) {
                    traceDebug("resume-pathing was idle; reissuing goal=%s", formatBlockPosSafe(startingPathBlock.get()));
                    baritone.getCustomGoalProcess().setGoalAndPath(new GoalBlock(startingPathBlock.get()));
                    actionDelayTicks = 10;
                }
            }
            case SnapAndResume -> {
                reuseHighwayBuilderCenterSnap();
                if (!ensureFarmingPose()) return;
                traceDebug("resume-complete cycle=%d", reconnectResumeCycleId);
                reconnectResumeArmed = false;
                reconnectResumeCycleId = 0L;
                resumeStep = ResumeStep.None;
                nextStorageRecheckAtTick = 0L;
                state = FarmerState.Farming;
            }
            default -> { }
        }
    }

    private boolean handlePostJoinMainServerDelay() {
        if (!postJoinDelayPending) return false;
        if (!isExecutionAllowedOnCurrentServer()) return true;

        if (postJoinDelayUntilMs == 0L) {
            postJoinDelayUntilMs = System.currentTimeMillis() + MAIN_SERVER_REJOIN_DELAY_MS;
            traceDebug("post-join-delay started until=%d", postJoinDelayUntilMs);
            state = FarmerState.WaitingForMainServer;
            disableWorker("post-join-delay");
            return true;
        }

        if (System.currentTimeMillis() < postJoinDelayUntilMs) {
            state = FarmerState.WaitingForMainServer;
            disableWorker("post-join-delay");
            return true;
        }

        postJoinDelayPending = false;
        postJoinDelayUntilMs = 0L;
        nextStorageRecheckAtTick = 0L;
        traceDebug("post-join-delay complete");
        return false;
    }

    private void handleActiveContainerMode() {
        switch (containerMode) {
            case StorageCheck -> handleStorageInspectionMode();
            case LooseObsidianPickup -> handleLooseObsidianPickupMode();
            case Deposit -> handleDepositMode();
            case EmptyShulkerDepot -> handleEmptyShulkerDepotMode();
            case LocalSourceShulker -> handleLocalSourceShulkerMode();
            case SingleFallbackSearch -> handleSingleFallbackMode();
            case DoubleFallbackSearch -> handleDoubleFallbackMode();
            default -> containerMode = ContainerMode.None;
        }
    }

    private boolean ensureGuardedDoorClosed() {
        BlockPos doorPos = resolveGuardedDoorBasePos();
        if (doorPos == null) {
            traceDebug("guarded-door invalid: configured position does not resolve to a usable door");
            return false;
        }
        if (!isDoorBlock(doorPos)) {
            traceDebug("guarded-door invalid: block at %s is not a door", formatBlockPosSafe(doorPos));
            return false;
        }
        if (!isDoorOpen(doorPos)) return true;

        traceDebug("guarded-door open: attempting close at %s", formatBlockPosSafe(doorPos));
        interactBlock(doorPos);
        actionDelayTicks = 1;
        return false;
    }

    private boolean shouldHandleDepositNow() {
        return (reopenDepositAfterAutoBreak && countLooseObsidianInInventory() > 0)
            || hasReachedMinimumEmptySlotReserveForFarming()
            || isInventoryActuallyFull();
    }

    private boolean hasReachedMinimumEmptySlotReserveForFarming() {
        return countLooseObsidianInInventory() > 0 && countEmptySlotsInInventory() <= minimumEmptySlots.get();
    }

    private void startStorageInspection() {
        disableWorker("storage-check");
        containerMode = ContainerMode.StorageCheck;
        containerStep = ContainerStep.Prepare;
        activeContainerPos = resolveUsableContainerInteractionPos(monitoredDoubleChestLocation.get());
        activeSecondaryContainerPos = null;
        state = FarmerState.WaitingForStorage;
        traceDebug("start-storage-check target=%s", formatBlockPosSafe(activeContainerPos));
    }

    private void startLooseObsidianPickupWorkflow() {
        disableWorker("loose-obsidian-precheck");
        containerMode = ContainerMode.LooseObsidianPickup;
        containerStep = ContainerStep.Prepare;
        activeContainerPos = resolveUsableContainerInteractionPos(looseObsidianStorageLocation.get());
        activeSecondaryContainerPos = null;
        state = FarmerState.WaitingForStorage;
        traceDebug("start-loose-obsidian-pickup target=%s", formatBlockPosSafe(activeContainerPos));
    }

    private boolean hasCompletelyEmptyShulkerInInventory() {
        if (mc.player == null) return false;
        for (int i = 0; i < mc.player.getInventory().getMainStacks().size(); i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!Utils.isShulker(stack.getItem())) continue;
            if (isShulkerItemEmpty(stack)) return true;
        }
        return false;
    }

    private void startEmptyShulkerDepotRouting() {
        disableWorker("empty-shulker-depot");
        containerMode = ContainerMode.EmptyShulkerDepot;
        containerStep = ContainerStep.Prepare;
        activeContainerPos = resolveUsableContainerInteractionPos(emptyShulkerDepotLocation.get());
        activeSecondaryContainerPos = null;
        state = FarmerState.WaitingForStorage;
        traceDebug("start-empty-shulker-depot target=%s", formatBlockPosSafe(activeContainerPos));
    }

    private boolean isInventoryActuallyFull() {
        if (mc.player == null) return false;
        if (countLooseObsidianInInventory() <= 0) return false;
        for (int i = 0; i < mc.player.getInventory().getMainStacks().size(); i++) {
            if (mc.player.getInventory().getStack(i).isEmpty()) return false;
        }
        return true;
    }

    private void startDepositWorkflow(boolean continuation) {
        disableWorker("deposit");
        containerMode = ContainerMode.Deposit;
        containerStep = ContainerStep.Prepare;
        activeContainerPos = depositShulkerLocation.get();
        activeSecondaryContainerPos = null;
        pendingDepositAfterStorageCheck = false;
        if (!continuation) reopenDepositAfterAutoBreak = false;
        state = FarmerState.Depositing;
        traceDebug("start-deposit continuation=%s obsidian=%d target=%s",
            continuation,
            countLooseObsidianInInventory(),
            formatBlockPosSafe(activeContainerPos)
        );
    }

    private RestockTask selectHighestPriorityRestockTask() {
        if (shouldTriggerFoodRestock()) return RestockTask.Food;
        if (shouldTriggerPickaxeRestock()) return RestockTask.Pickaxe;
        if (shouldTriggerEchestRestock()) return RestockTask.Echest;
        return RestockTask.None;
    }

    private void beginRestockTask(RestockTask task) {
        if (task == RestockTask.None) return;
        activeRestockTask = task;
        activeRestockStage = RestockStage.InventoryShulkers;
        lastKitbotPoseSettledStage = RestockStage.None;
        restockNeedsTrashCheck = true;
        pendingKitbotRequest = KitbotRequest.None;
        awaitingKitbotTeleport = false;
        kitbotRetryUsed = false;
        state = FarmerState.Restocking;
        disableWorker("restock-start");
        traceDebug("begin-restock task=%s food=%d pickaxes=%d echests=%d emptySlots=%d",
            task,
            countConfiguredFoodItemsInInventory(),
            countLoosePickaxesInInventory(),
            countLooseInventoryEnderChests(),
            countEmptySlotsInInventory()
        );
    }

    private void handleRestockTask() {
        disableWorker("restocking");
        state = FarmerState.Restocking;

        if (isRestockTaskSatisfied(activeRestockTask)) {
            finishRestockTask();
            return;
        }

        if (restockNeedsTrashCheck) {
            if (hasTrashItemsInInventory()) {
                startTrashRoutine();
                return;
            }
            restockNeedsTrashCheck = false;
        }

        if (hasCompletelyEmptyShulkerInInventory()) {
            startEmptyShulkerDepotRouting();
            return;
        }

        if (containerMode != ContainerMode.None) return;

        switch (activeRestockStage) {
            case InventoryShulkers -> startInventorySourceAttemptOrAdvance();
            case WaitingForKitbotCooldown -> handleKitbotCooldownStage();
            case WaitingForKitbotDelivery -> handleKitbotDeliveryStage();
            case SingleFallback -> startSingleFallbackAttemptIfIdle();
            case DoubleFallback -> startDoubleFallbackAttemptIfIdle();
            default -> finishRestockTask();
        }
    }

    private void snapToFarmingPose() {
        if (mc.player == null) return;
        Vec3d target = getFarmingPose();
        snapPlayerTo(target, FARM_YAW, FARM_PITCH);
    }

    private boolean ensureFarmingPose() {
        return ensurePose(getFarmingPose(), FARM_YAW, FARM_PITCH);
    }

    private void reuseHighwayBuilderCenterSnap() {
        HighwayBuilderTHM highwayBuilder = Modules.get().get(HighwayBuilderTHM.class);
        if (highwayBuilder != null) highwayBuilder.snapPlayerToCurrentBlockCenter();
    }

    private void resetTimerOverride() {
        Timer timer = Modules.get().get(Timer.class);
        if (timer != null) timer.setOverride(1.0);
    }

    private void enableWorkerIfNeeded() {
        BetterEchestFarmer worker = Modules.get().get(BetterEchestFarmer.class);
        if (worker == null || worker.isActive()) return;
        traceDebug("worker-enable");
        worker.toggle();
    }

    private void disableWorker(String reason) {
        BetterEchestFarmer worker = Modules.get().get(BetterEchestFarmer.class);
        if (worker != null && worker.isActive()) {
            traceDebug("worker-disable reason=%s", reason);
            worker.toggle();
        }
        needsLooseObsidianPreFarmCheck = true;
        resetTimerOverride();
    }

    private void cancelBaritonePathing() {
        if (!BaritoneUtils.IS_AVAILABLE) return;
        try {
            IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
            baritone.getPathingBehavior().cancelEverything();
            baritone.getCustomGoalProcess().setGoal(null);
        } catch (Throwable t) {
            traceDebug("baritone-cancel failed: %s", t.getMessage());
        }
    }

    private void closeHandledScreen() {
        if (mc.player != null && mc.currentScreen != null) mc.player.closeHandledScreen();
    }

    private boolean isExecutionAllowedOnCurrentServer() {
        return ServerStatusHandler.getInstance().getCommittedState() == ServerState.MAIN_SERVER;
    }

    private boolean isDoorEnforcementSuppressed() {
        return System.currentTimeMillis() < doorEnforcementSuppressedUntilMs;
    }

    private void syncThmHwyMonitorOnActivate() {
        if (!manageThmHwyMonitor.get()) return;

        try {
            THMHwyMonitor monitor = Modules.get().get(THMHwyMonitor.class);
            if (monitor == null || monitor.isActive()) return;
            monitor.toggle();
            traceDebug("manage-monitor activate: toggled THMHwyMonitor on");
        } catch (NoClassDefFoundError | ExceptionInInitializerError t) {
            traceDebug("manage-monitor activate failed: %s", t.getMessage());
        }
    }

    private void syncHotbarManagerOnActivate() {
        if (!manageHotbar.get()) return;

        try {
            HotbarManager manager = Modules.get().get(HotbarManager.class);
            if (manager == null || manager.isActive()) return;
            manager.toggle();
            traceDebug("manage-hotbar activate: toggled HotbarManager on");
        } catch (NoClassDefFoundError | ExceptionInInitializerError t) {
            traceDebug("manage-hotbar activate failed: %s", t.getMessage());
        }
    }

    private void disableHotbarManagerIfActive() {
        if (!manageHotbar.get()) return;

        try {
            HotbarManager manager = Modules.get().get(HotbarManager.class);
            if (manager == null || !manager.isActive()) return;
            manager.toggle();
            traceDebug("manage-hotbar deactivate: toggled HotbarManager off");
        } catch (NoClassDefFoundError | ExceptionInInitializerError t) {
            traceDebug("manage-hotbar deactivate failed: %s", t.getMessage());
        }
    }

    private void disableThmHwyMonitorIfActive() {
        if (!manageThmHwyMonitor.get()) return;

        try {
            THMHwyMonitor monitor = Modules.get().get(THMHwyMonitor.class);
            if (monitor == null || !monitor.isActive()) return;
            monitor.toggle();
            traceDebug("manage-monitor deactivate: toggled THMHwyMonitor off");
        } catch (NoClassDefFoundError | ExceptionInInitializerError t) {
            traceDebug("manage-monitor deactivate failed: %s", t.getMessage());
        }
    }

    private void handleStorageInspectionMode() {
        switch (containerStep) {
            case Prepare -> {
                if (!isUsableContainerBlock(activeContainerPos)) {
                    traceDebug("storage-check aborted: target %s is not a usable storage container", formatBlockPosSafe(activeContainerPos));
                    containerMode = ContainerMode.None;
                    containerStep = ContainerStep.None;
                    state = FarmerState.WaitingForStorage;
                    return;
                }

                if (!ensureContainerScreenOpen(activeContainerPos)) return;

                int slots = getOpenedContainerSlotCount();
                if (slots <= 0) return;

                boolean fullOfShulkers = true;
                for (int i = 0; i < slots; i++) {
                    ItemStack stack = mc.player.currentScreenHandler.slots.get(i).getStack();
                    if (stack.isEmpty() || !Utils.isShulker(stack.getItem())) {
                        fullOfShulkers = false;
                        break;
                    }
                }

                storageChestFullOfShulkers = fullOfShulkers;
                storageRecheckGeneration++;
                nextStorageRecheckAtTick = mc.world.getTime() + STORAGE_RECHECK_INTERVAL_TICKS;
                pendingDepositAfterStorageCheck = !fullOfShulkers && shouldHandleDepositNow();
                traceDebug("storage-check result fullOfShulkers=%s slots=%d pendingDeposit=%s nextRecheckTick=%d generation=%d",
                    fullOfShulkers,
                    slots,
                    pendingDepositAfterStorageCheck,
                    nextStorageRecheckAtTick,
                    storageRecheckGeneration
                );
                closeHandledScreen();
                actionDelayTicks = Math.max(actionDelayTicks, inventoryDelay.get());
                containerStep = ContainerStep.Close;
            }
            case Close -> {
                if (mc.currentScreen != null) {
                    traceDebug("storage-check waiting for chest screen to close screen=%s", formatCurrentScreenSafe());
                    closeHandledScreen();
                    actionDelayTicks = Math.max(actionDelayTicks, 1);
                    return;
                }

                containerMode = ContainerMode.None;
                containerStep = ContainerStep.None;
                state = storageChestFullOfShulkers ? FarmerState.WaitingForStorage : getPostRoutineState();
            }
            default -> containerStep = ContainerStep.Prepare;
        }
    }

    private void handleDepositMode() {
        if (!isUsableContainerBlock(activeContainerPos)) {
            traceDebug("deposit aborted: target %s is not a usable container", formatBlockPosSafe(activeContainerPos));
            containerMode = ContainerMode.None;
            state = FarmerState.WaitingForStorage;
            return;
        }

        if (!ensureContainerScreenOpen(activeContainerPos)) return;
        if (getOpenedContainerSlotCount() <= 0) return;

        if (shiftClickPlayerInventoryItems(stack -> stack.isOf(Items.OBSIDIAN), 1)) {
            actionDelayTicks = inventoryDelay.get();
            return;
        }

        if (countLooseObsidianInInventory() == 0) {
            traceDebug("deposit complete: no loose obsidian remains");
            closeHandledScreen();
            containerMode = ContainerMode.None;
            reopenDepositAfterAutoBreak = false;
            state = getPostRoutineState();
            return;
        }

        traceDebug("deposit awaiting continuation: loose obsidian remains=%d waitTicks=%d",
            countLooseObsidianInInventory(),
            PICKUP_WAIT_TICKS
        );
        closeHandledScreen();
        containerMode = ContainerMode.None;
        reopenDepositAfterAutoBreak = true;
        actionDelayTicks = Math.max(actionDelayTicks, PICKUP_WAIT_TICKS);
        state = FarmerState.WaitingForStorage;
    }

    private void handleLooseObsidianPickupMode() {
        switch (containerStep) {
            case Prepare -> {
                if (!isUsableContainerBlock(activeContainerPos)) {
                    traceDebug("loose-obsidian-pickup aborted: target %s is not usable", formatBlockPosSafe(activeContainerPos));
                    containerMode = ContainerMode.None;
                    containerStep = ContainerStep.None;
                    state = FarmerState.WaitingForStorage;
                    return;
                }

                if (!ensureContainerScreenOpen(activeContainerPos)) return;

                if (shiftClickContainerItems(stack -> stack.isOf(Items.OBSIDIAN), 1)) {
                    actionDelayTicks = inventoryDelay.get();
                    return;
                }

                if (hasMatchingContainerItem(stack -> stack.isOf(Items.OBSIDIAN))) {
                    traceDebug("loose-obsidian-pickup incomplete: loose obsidian still remains in source");
                    closeHandledScreen();
                    containerMode = ContainerMode.None;
                    containerStep = ContainerStep.None;
                    state = FarmerState.WaitingForStorage;
                    return;
                }

                traceDebug("loose-obsidian-pickup complete");
                closeHandledScreen();
                actionDelayTicks = Math.max(actionDelayTicks, inventoryDelay.get());
                containerStep = ContainerStep.Close;
            }
            case Close -> {
                if (mc.currentScreen != null) {
                    traceDebug("loose-obsidian-pickup waiting for screen close screen=%s", formatCurrentScreenSafe());
                    closeHandledScreen();
                    actionDelayTicks = Math.max(actionDelayTicks, 1);
                    return;
                }

                needsLooseObsidianPreFarmCheck = false;
                containerMode = ContainerMode.None;
                containerStep = ContainerStep.None;
                state = getPostRoutineState();
            }
            default -> containerStep = ContainerStep.Prepare;
        }
    }

    private void handleEmptyShulkerDepotMode() {
        if (!isUsableContainerBlock(activeContainerPos)) {
            traceDebug("empty-shulker-depot aborted: target %s is not usable", formatBlockPosSafe(activeContainerPos));
            containerMode = ContainerMode.None;
            state = FarmerState.WaitingForStorage;
            return;
        }

        if (!ensureContainerScreenOpen(activeContainerPos)) return;

        if (shiftClickPlayerInventoryItems(this::isCompletelyEmptyShulkerStack, Integer.MAX_VALUE)) {
            actionDelayTicks = inventoryDelay.get();
            return;
        }

        if (hasCompletelyEmptyShulkerInInventory()) {
            traceDebug("empty-shulker-depot incomplete: empty shulkers still remain in inventory");
            closeHandledScreen();
            containerMode = ContainerMode.None;
            state = FarmerState.WaitingForStorage;
            return;
        }

        traceDebug("empty-shulker-depot complete");
        closeHandledScreen();
        containerMode = ContainerMode.None;
        state = getPostRoutineState();
    }

    private BlockPos resolveGuardedDoorBasePos() {
        BlockPos configured = guardedDoorLocation.get();
        if (mc.world == null) return null;

        BlockState state = mc.world.getBlockState(configured);
        if (!(state.getBlock() instanceof DoorBlock)) return null;
        if (state.contains(DoorBlock.HALF) && state.get(DoorBlock.HALF) == DoubleBlockHalf.UPPER) {
            BlockPos lower = configured.down();
            BlockState lowerState = mc.world.getBlockState(lower);
            if (lowerState.getBlock() instanceof DoorBlock) return lower;
        }
        return configured;
    }

    private boolean isDoorBlock(BlockPos pos) {
        return mc.world != null && mc.world.getBlockState(pos).getBlock() instanceof DoorBlock;
    }

    private boolean isDoorOpen(BlockPos pos) {
        if (!isDoorBlock(pos)) return false;
        BlockState state = mc.world.getBlockState(pos);
        return state.contains(DoorBlock.OPEN) && state.get(DoorBlock.OPEN);
    }

    private void interactBlock(BlockPos pos) {
        if (mc.player == null || mc.interactionManager == null || pos == null) return;
        mc.interactionManager.interactBlock(
            mc.player,
            Hand.MAIN_HAND,
            new BlockHitResult(Vec3d.ofCenter(pos), Direction.UP, pos, false)
        );
    }

    private boolean isShulkerBlock(BlockPos pos) {
        return mc.world != null && mc.world.getBlockState(pos).getBlock() instanceof ShulkerBoxBlock;
    }

    private boolean isChestBlock(BlockPos pos) {
        if (pos == null || mc.world == null) return false;
        if (mc.world == null) return false;
        Block block = mc.world.getBlockState(pos).getBlock();
        return block instanceof ChestBlock || block instanceof TrappedChestBlock;
    }

    private boolean isInspectableStorageBlock(BlockPos pos) {
        if (pos == null || mc.world == null) return false;
        Block block = mc.world.getBlockState(pos).getBlock();
        return block instanceof ChestBlock
            || block instanceof TrappedChestBlock
            || block instanceof BarrelBlock
            || block instanceof HopperBlock;
    }

    private boolean isUsableContainerBlock(BlockPos pos) {
        if (pos == null || mc.world == null) return false;
        if (mc.world == null) return false;
        Block block = mc.world.getBlockState(pos).getBlock();
        return block instanceof ShulkerBoxBlock
            || block instanceof ChestBlock
            || block instanceof TrappedChestBlock
            || block instanceof BarrelBlock
            || block instanceof HopperBlock
            || block instanceof EnderChestBlock;
    }

    private boolean isShulkerItemEmpty(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !Utils.isShulker(stack.getItem())) return false;
        ItemStack[] items = new ItemStack[27];
        Utils.getItemsInContainerItem(stack, items);
        for (ItemStack item : items) {
            if (item != null && !item.isEmpty()) return false;
        }
        return true;
    }

    private boolean isCompletelyEmptyShulkerStack(ItemStack stack) {
        return Utils.isShulker(stack.getItem()) && isShulkerItemEmpty(stack);
    }

    private boolean isUsefulLocalSourceShulkerStack(ItemStack stack, RestockTask task) {
        return stack != null
            && !stack.isEmpty()
            && task != null
            && Utils.isShulker(stack.getItem())
            && countUsefulUnitsInShulkerItem(stack, task) > 0;
    }

    private Vec3d getFarmingPose() {
        BlockPos start = startingPathBlock.get();
        return new Vec3d(start.getX() + 0.7, start.getY() + 1.0, start.getZ() + 0.3);
    }

    private Vec3d getStartCenterPose() {
        BlockPos start = startingPathBlock.get();
        return new Vec3d(start.getX() + 0.5, start.getY() + 1.0, start.getZ() + 0.5);
    }

    private float getLocalRestockYaw() {
        BlockPos target = activeContainerPos != null ? activeContainerPos : getLocalRestockContainerPos();
        return (float) Rotations.getYaw(target);
    }

    private float getLocalRestockPitch() {
        BlockPos target = activeContainerPos != null ? activeContainerPos : getLocalRestockContainerPos();
        return (float) Rotations.getPitch(target);
    }

    private Vec3d getKitbotRestockPose() {
        BlockPos start = startingPathBlock.get();
        return new Vec3d(start.getX() + 1.0, start.getY() + 1.0, start.getZ());
    }

    private BlockPos getLocalRestockContainerPos() {
        return startingPathBlock.get().add(0, 1, -1);
    }

    private Vec3d getDoublePlacementPose() {
        BlockPos start = startingPathBlock.get().add(0, 0, -1);
        return new Vec3d(start.getX() + 0.5, start.getY() + 1.0, start.getZ() + 0.5);
    }

    private BlockPos getSingleFallbackContainerPos() {
        return startingPathBlock.get().add(-1, 1, 0);
    }

    private BlockPos getDoubleFallbackContainerPos() {
        return startingPathBlock.get().add(-1, 1, -1);
    }

    private void snapPlayerTo(Vec3d position, float yaw, float pitch) {
        if (mc.player == null || position == null) return;
        mc.player.setVelocity(0.0, 0.0, 0.0);
        mc.player.setPosition(position.x, position.y, position.z);
        mc.player.setYaw(yaw);
        mc.player.setPitch(pitch);
    }

    private void snapToSinglePlacementPose() {
        snapPlayerTo(getStartCenterPose(), CONTAINER_YAW, CONTAINER_PITCH);
    }

    private boolean ensureSinglePlacementPose() {
        return ensurePose(getStartCenterPose(), CONTAINER_YAW, CONTAINER_PITCH);
    }

    private boolean ensureLocalRestockPose() {
        return ensurePose(getStartCenterPose(), getLocalRestockYaw(), getLocalRestockPitch());
    }

    private void snapToDoublePlacementPose() {
        snapPlayerTo(getDoublePlacementPose(), CONTAINER_YAW, CONTAINER_PITCH);
    }

    private boolean ensureDoublePlacementPose() {
        return ensurePose(getDoublePlacementPose(), CONTAINER_YAW, CONTAINER_PITCH);
    }

    private void snapToTrashPose() {
        snapPlayerTo(getStartCenterPose(), TRASH_YAW, TRASH_PITCH);
    }

    private boolean ensureTrashPose() {
        return ensurePose(getStartCenterPose(), TRASH_YAW, TRASH_PITCH);
    }

    private boolean ensureKitbotRestockPose() {
        if (mc.player == null) return false;
        if (activeRestockStage != RestockStage.WaitingForKitbotCooldown && activeRestockStage != RestockStage.WaitingForKitbotDelivery) {
            lastKitbotPoseSettledStage = RestockStage.None;
            return true;
        }

        if (lastKitbotPoseSettledStage == activeRestockStage) return true;

        Vec3d target = getKitbotRestockPose();
        if (isWithinPositionTolerance(target, KITBOT_POSE_SETTLE_TOLERANCE)) {
            lastKitbotPoseSettledStage = activeRestockStage;
            return true;
        }

        traceDebug("kitbot-pose-settle target=(%.3f, %.3f, %.3f) current=%s currentYaw=%.2f currentPitch=%.2f stage=%s",
            target.x,
            target.y,
            target.z,
            formatPlayerPosSafe(),
            mc.player.getYaw(),
            mc.player.getPitch(),
            activeRestockStage
        );
        snapPlayerTo(target, mc.player.getYaw(), mc.player.getPitch());
        actionDelayTicks = Math.max(actionDelayTicks, 1);

        if (isWithinPositionTolerance(target, KITBOT_POSE_SETTLE_TOLERANCE)) {
            lastKitbotPoseSettledStage = activeRestockStage;
            return true;
        }

        return false;
    }

    private boolean ensurePose(Vec3d position, float yaw, float pitch) {
        if (mc.player == null || position == null) return false;
        if (isAtPose(position, yaw, pitch)) return true;

        traceDebug("pose-resnap target=(%.3f, %.3f, %.3f) yaw=%.2f pitch=%.2f current=%s currentYaw=%.2f currentPitch=%.2f",
            position.x,
            position.y,
            position.z,
            yaw,
            pitch,
            formatPlayerPosSafe(),
            mc.player.getYaw(),
            mc.player.getPitch()
        );
        snapPlayerTo(position, yaw, pitch);
        if (isAtPose(position, yaw, pitch)) return true;

        actionDelayTicks = Math.max(actionDelayTicks, 1);
        return false;
    }

    private boolean isAtPose(Vec3d position, float yaw, float pitch) {
        if (mc.player == null || position == null) return false;

        return Math.abs(mc.player.getX() - position.x) <= POSE_POSITION_TOLERANCE
            && Math.abs(mc.player.getY() - position.y) <= POSE_POSITION_TOLERANCE
            && Math.abs(mc.player.getZ() - position.z) <= POSE_POSITION_TOLERANCE
            && Math.abs(MathHelper.wrapDegrees(mc.player.getYaw() - yaw)) <= POSE_ROTATION_TOLERANCE
            && Math.abs(mc.player.getPitch() - pitch) <= POSE_ROTATION_TOLERANCE;
    }

    private boolean isWithinPositionTolerance(Vec3d position, double tolerance) {
        if (mc.player == null || position == null) return false;

        return Math.abs(mc.player.getX() - position.x) <= tolerance
            && Math.abs(mc.player.getY() - position.y) <= tolerance
            && Math.abs(mc.player.getZ() - position.z) <= tolerance;
    }

    private int countInventoryItems(Predicate<ItemStack> predicate) {
        if (mc.player == null) return 0;
        int count = 0;
        for (int i = 0; i < mc.player.getInventory().getMainStacks().size(); i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (predicate.test(stack)) count += stack.getCount();
        }
        return count;
    }

    private int countConfiguredFoodItemsInInventory() {
        return countInventoryItems(this::isConfiguredFoodStack);
    }

    private int countLoosePickaxesInInventory() {
        return countInventoryItems(stack -> !stack.isEmpty() && stack.isIn(ItemTags.PICKAXES));
    }

    private int countLooseInventoryEnderChests() {
        return countInventoryItems(stack -> stack.isOf(Items.ENDER_CHEST));
    }

    private int countLooseObsidianInInventory() {
        return countInventoryItems(stack -> stack.isOf(Items.OBSIDIAN));
    }

    private boolean isConfiguredFoodStack(ItemStack stack) {
        return stack != null && !stack.isEmpty() && foodTypes.get().contains(stack.getItem());
    }

    private boolean shouldTriggerFoodRestock() {
        return !foodTypes.get().isEmpty() && countConfiguredFoodItemsInInventory() <= saveFood.get();
    }

    private boolean shouldTriggerPickaxeRestock() {
        return countLoosePickaxesInInventory() <= savePickaxes.get();
    }

    private boolean shouldTriggerEchestRestock() {
        return countLooseInventoryEnderChests() <= minimumEchests.get() + 2;
    }

    private boolean isRestockTaskSatisfied(RestockTask task) {
        return switch (task) {
            case Food -> !shouldTriggerFoodRestock();
            case Pickaxe -> !shouldTriggerPickaxeRestock();
            case Echest -> !shouldTriggerEchestRestock();
            case None -> true;
        };
    }

    private void finishRestockTask() {
        traceDebug("finish-restock task=%s postResumeDelay=%d", activeRestockTask, POST_RESTOCK_RESUME_DELAY_TICKS);
        activeRestockTask = RestockTask.None;
        activeRestockStage = RestockStage.None;
        lastKitbotPoseSettledStage = RestockStage.None;
        pendingKitbotRequest = KitbotRequest.None;
        awaitingKitbotTeleport = false;
        kitbotRetryUsed = false;
        restockNeedsTrashCheck = false;
        pendingUsefulShulkerInventorySlot = -1;
        localSourceInventorySlot = -1;
        currentLocalSourceHotbarSlot = -1;
        fallbackRecoveredUsefulShulker = false;
        fallbackRecoveredLooseItems = false;
        postRestockResumeDelayTicks = POST_RESTOCK_RESUME_DELAY_TICKS;
        state = FarmerState.Farming;
    }

    private boolean hasTrashItemsInInventory() {
        if (mc.player == null) return false;
        for (int i = 0; i < mc.player.getInventory().getMainStacks().size(); i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!stack.isEmpty() && trashItems.get().contains(stack.getItem())) return true;
        }
        return false;
    }

    private void startTrashRoutine() {
        disableWorker("trash-routine");
        trashStep = TrashStep.Prepare;
        state = FarmerState.TrashThrowout;
        traceDebug("start-trash count=%d", countMatchingTrashStacks());
    }

    private void handleTrashRoutine() {
        BlockPos doorPos = resolveGuardedDoorBasePos();
        if (doorPos == null || !isDoorBlock(doorPos)) {
            state = FarmerState.WaitingForDoorClose;
            trashStep = TrashStep.None;
            return;
        }

        switch (trashStep) {
            case Prepare -> {
                if (!ensureTrashPose()) return;
                interactBlock(doorPos);
                actionDelayTicks = 1;
                trashStep = TrashStep.VerifyDoorOpen;
            }
            case VerifyDoorOpen -> {
                if (!isDoorOpen(doorPos)) {
                    interactBlock(doorPos);
                    actionDelayTicks = 1;
                    return;
                }

                if (!ensureTrashPose()) return;
                trashStep = TrashStep.DropItems;
            }
            case DropItems -> {
                if (mc.player != null) {
                    for (int i = mc.player.getInventory().getMainStacks().size() - 1; i >= 0; i--) {
                        ItemStack stack = mc.player.getInventory().getStack(i);
                        if (stack.isEmpty() || !trashItems.get().contains(stack.getItem())) continue;
                        InvUtils.drop().slot(i);
                    }
                }
                doorEnforcementSuppressedUntilMs = System.currentTimeMillis() + TRASH_DOOR_GRACE_MS;
                trashStep = TrashStep.WaitForDoorGrace;
            }
            case WaitForDoorGrace -> {
                if (System.currentTimeMillis() < doorEnforcementSuppressedUntilMs) return;
                if (!ensureGuardedDoorClosed()) return;

                trashStep = TrashStep.None;
                restockNeedsTrashCheck = false;
                state = FarmerState.Restocking;
            }
            default -> trashStep = TrashStep.None;
        }
    }

    private boolean ensureContainerScreenOpen(BlockPos pos) {
        if (pos == null) return false;
        if (mc.currentScreen instanceof GenericContainerScreen
            || mc.currentScreen instanceof HopperScreen
            || mc.currentScreen instanceof ShulkerBoxScreen) return true;
        interactBlock(pos);
        actionDelayTicks = inventoryDelay.get();
        return false;
    }

    private int getOpenedContainerSlotCount() {
        if (mc.player == null || mc.player.currentScreenHandler == null) return 0;
        return Math.max(mc.player.currentScreenHandler.slots.size() - 36, 0);
    }

    private boolean shiftClickPlayerInventoryItems(Predicate<ItemStack> predicate, int maxMoves) {
        if (mc.player == null || mc.player.currentScreenHandler == null) return false;

        boolean moved = false;
        int moves = 0;
        for (int i = 0; i < mc.player.getInventory().getMainStacks().size() && moves < maxMoves; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!predicate.test(stack)) continue;
            mc.interactionManager.clickSlot(
                mc.player.currentScreenHandler.syncId,
                SlotUtils.indexToId(i),
                0,
                SlotActionType.QUICK_MOVE,
                mc.player
            );
            moved = true;
            moves++;
        }
        return moved;
    }

    private boolean shiftClickContainerItems(Predicate<ItemStack> predicate, int maxMoves) {
        if (mc.player == null || mc.player.currentScreenHandler == null) return false;

        boolean moved = false;
        int moves = 0;
        for (int i = 0; i < getOpenedContainerSlotCount() && moves < maxMoves; i++) {
            ItemStack stack = mc.player.currentScreenHandler.slots.get(i).getStack();
            if (!predicate.test(stack)) continue;
            if (!quickMoveContainerSlot(i)) continue;
            moved = true;
            moves++;
        }
        return moved;
    }

    private boolean quickMoveContainerSlot(int slot) {
        if (mc.player == null || mc.player.currentScreenHandler == null) return false;
        if (slot < 0 || slot >= getOpenedContainerSlotCount()) return false;
        ItemStack before = mc.player.currentScreenHandler.slots.get(slot).getStack().copy();
        mc.interactionManager.clickSlot(
            mc.player.currentScreenHandler.syncId,
            slot,
            0,
            SlotActionType.QUICK_MOVE,
            mc.player
        );
        ItemStack after = mc.player.currentScreenHandler.slots.get(slot).getStack();
        return after.getCount() < before.getCount() || after.getItem() != before.getItem();
    }

    private void startInventorySourceAttemptOrAdvance() {
        int slot = findBestInventorySourceShulkerSlot(activeRestockTask, true);
        if (slot != -1) {
            pendingUsefulShulkerInventorySlot = slot;
            localSourceInventorySlot = slot;
            activeContainerPos = getLocalRestockContainerPos();
            activeSecondaryContainerPos = null;
            containerMode = ContainerMode.LocalSourceShulker;
            containerStep = ContainerStep.Prepare;
            traceDebug("inventory-source selected slot=%d usefulUnits=%d target=%s",
                slot,
                countUsefulUnitsInShulkerItem(mc.player.getInventory().getStack(slot), activeRestockTask),
                formatBlockPosSafe(activeContainerPos)
            );
            return;
        }

        traceDebug("inventory-source unavailable for task=%s", activeRestockTask);
        advanceAfterInventorySourceFailure();
    }

    private void handleKitbotCooldownStage() {
        if (!ensureKitbotRestockPose()) return;

        long now = System.currentTimeMillis();
        if (now - lastKitbotOrderAtMs < KITBOT_COOLDOWN_MS) {
            state = FarmerState.WaitingForKitbotCooldown;
            return;
        }

        issueKitbotOrderForActiveTask();
    }

    private void handleKitbotDeliveryStage() {
        if (!ensureKitbotRestockPose()) return;

        if (isRestockTaskSatisfied(activeRestockTask) || hasObservedKitbotSupplyGain()) {
            activeRestockStage = RestockStage.InventoryShulkers;
            pendingKitbotRequest = KitbotRequest.None;
            awaitingKitbotTeleport = false;
            kitbotRetryUsed = false;
            state = FarmerState.Restocking;
            return;
        }

        if (System.currentTimeMillis() >= kitbotDeadlineAtMs) {
            if (!kitbotRetryUsed && pendingKitbotRequest != KitbotRequest.None) {
                retryActiveKitbotOrder();
                return;
            }

            awaitingKitbotTeleport = false;
            pendingKitbotRequest = KitbotRequest.None;
            kitbotRetryUsed = false;
            advanceAfterKitbotFailure();
        }
    }

    private void startSingleFallbackAttemptIfIdle() {
        fallbackRecoveredUsefulShulker = false;
        fallbackRecoveredLooseItems = false;
        containerMode = ContainerMode.SingleFallbackSearch;
        containerStep = ContainerStep.Prepare;
        activeContainerPos = getSingleFallbackContainerPos();
        activeSecondaryContainerPos = null;
        traceDebug("single-fallback start target=%s", formatBlockPosSafe(activeContainerPos));
    }

    private void startDoubleFallbackAttemptIfIdle() {
        fallbackRecoveredUsefulShulker = false;
        fallbackRecoveredLooseItems = false;
        containerMode = ContainerMode.DoubleFallbackSearch;
        containerStep = ContainerStep.Prepare;
        activeContainerPos = getSingleFallbackContainerPos();
        activeSecondaryContainerPos = getDoubleFallbackContainerPos();
        traceDebug("double-fallback start primary=%s secondary=%s",
            formatBlockPosSafe(activeContainerPos),
            formatBlockPosSafe(activeSecondaryContainerPos)
        );
    }

    private boolean transferFromOpenedContainerForActiveTask(boolean allowFallbackShulkerRecovery) {
        return switch (activeRestockTask) {
            case Food -> transferConfiguredFoodFromOpenedContainer();
            case Pickaxe -> transferPickaxesFromOpenedContainer();
            case Echest -> transferEchestsFromOpenedContainer();
            case None -> false;
        };
    }

    private boolean transferConfiguredFoodFromOpenedContainer() {
        int slot = findLargestMatchingFoodContainerSlot();
        return slot != -1 && quickMoveContainerSlot(slot);
    }

    private boolean transferPickaxesFromOpenedContainer() {
        boolean moved = false;
        int target = Math.max(Math.min(minimumPickaxeRestock.get(), Math.max(countEmptySlotsInInventory(), 1)), 1);
        int movedCount = 0;
        while (movedCount < target) {
            int slot = findFirstMatchingContainerSlot(stack -> !stack.isEmpty() && stack.isIn(ItemTags.PICKAXES));
            if (slot == -1) break;
            ItemStack stack = mc.player.currentScreenHandler.slots.get(slot).getStack();
            int count = Math.max(stack.getCount(), 1);
            if (!quickMoveContainerSlot(slot)) break;
            moved = true;
            movedCount += count;
            if (isRestockTaskSatisfied(activeRestockTask)) break;
        }
        return moved;
    }

    private boolean transferEchestsFromOpenedContainer() {
        boolean moved = false;
        int desired = Math.max(getEchestRestockTargetUnits(), (minimumEchests.get() + 3) - countLooseInventoryEnderChests());
        while (desired > 0) {
            int slot = findFirstMatchingContainerSlot(stack -> stack.isOf(Items.ENDER_CHEST));
            if (slot == -1) break;
            ItemStack stack = mc.player.currentScreenHandler.slots.get(slot).getStack();
            int count = stack.getCount();
            if (!quickMoveContainerSlot(slot)) break;
            moved = true;
            desired -= count;
            if (isRestockTaskSatisfied(activeRestockTask)) break;
        }
        return moved;
    }

    private int findLargestMatchingFoodContainerSlot() {
        int bestSlot = -1;
        int bestCount = -1;
        for (int i = 0; i < getOpenedContainerSlotCount(); i++) {
            ItemStack stack = mc.player.currentScreenHandler.slots.get(i).getStack();
            if (!isConfiguredFoodStack(stack)) continue;
            if (stack.getCount() > bestCount) {
                bestCount = stack.getCount();
                bestSlot = i;
            }
        }
        return bestSlot;
    }

    private int findFirstMatchingContainerSlot(Predicate<ItemStack> predicate) {
        for (int i = 0; i < getOpenedContainerSlotCount(); i++) {
            if (predicate.test(mc.player.currentScreenHandler.slots.get(i).getStack())) return i;
        }
        return -1;
    }

    private boolean hasMatchingContainerItem(Predicate<ItemStack> predicate) {
        return findFirstMatchingContainerSlot(predicate) != -1;
    }

    private int findBestUsefulContainerShulkerSlot(RestockTask task, boolean preferLargest) {
        int bestSlot = -1;
        int bestUnits = -1;
        for (int i = 0; i < getOpenedContainerSlotCount(); i++) {
            ItemStack stack = mc.player.currentScreenHandler.slots.get(i).getStack();
            int useful = countUsefulUnitsInShulkerItem(stack, task);
            if (useful <= 0) continue;
            if (bestSlot == -1
                || (preferLargest && useful > bestUnits)
                || (!preferLargest && useful < bestUnits)
                || (useful == bestUnits && i < bestSlot)) {
                bestSlot = i;
                bestUnits = useful;
            }
        }
        return bestSlot;
    }

    private int findBestInventorySourceShulkerSlot(RestockTask task, boolean preferSmallest) {
        if (mc.player == null) return -1;

        int bestSlot = -1;
        int bestUnits = -1;
        for (int i = 0; i < mc.player.getInventory().getMainStacks().size(); i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            int useful = countUsefulUnitsInShulkerItem(stack, task);
            if (useful <= 0) continue;
            if (bestSlot == -1
                || (preferSmallest && useful < bestUnits)
                || (!preferSmallest && useful > bestUnits)
                || (useful == bestUnits && i < bestSlot)) {
                bestSlot = i;
                bestUnits = useful;
            }
        }
        return bestSlot;
    }

    private int countUsefulUnitsInShulkerItem(ItemStack stack, RestockTask task) {
        if (stack == null || stack.isEmpty() || !Utils.isShulker(stack.getItem())) return 0;
        ItemStack[] items = new ItemStack[27];
        Utils.getItemsInContainerItem(stack, items);

        int count = 0;
        for (ItemStack item : items) {
            if (item == null || item.isEmpty()) continue;
            switch (task) {
                case Food -> {
                    if (isConfiguredFoodStack(item)) count += item.getCount();
                }
                case Pickaxe -> {
                    if (item.isIn(ItemTags.PICKAXES)) count += item.getCount();
                }
                case Echest -> {
                    if (item.isOf(Items.ENDER_CHEST)) count += item.getCount();
                }
            }
        }
        return count;
    }

    private int countMatchingInventoryShulkersForTask(RestockTask task) {
        if (mc.player == null) return 0;
        int count = 0;
        for (int i = 0; i < mc.player.getInventory().getMainStacks().size(); i++) {
            if (countUsefulUnitsInShulkerItem(mc.player.getInventory().getStack(i), task) > 0) count++;
        }
        return count;
    }

    private int countLooseUnitsForTask(RestockTask task) {
        return switch (task) {
            case Food -> countConfiguredFoodItemsInInventory();
            case Pickaxe -> countLoosePickaxesInInventory();
            case Echest -> countLooseInventoryEnderChests();
            case None -> 0;
        };
    }

    private void advanceAfterInventorySourceFailure() {
        activeRestockStage = RestockStage.WaitingForKitbotCooldown;
        state = FarmerState.WaitingForKitbotCooldown;
        traceDebug("restock advance: inventory source exhausted -> kitbot cooldown");
    }

    private void retryLocalSourceAttempt(String reason, int delayTicks) {
        state = FarmerState.Restocking;
        actionDelayTicks = Math.max(actionDelayTicks, delayTicks);
        containerMode = ContainerMode.LocalSourceShulker;
        containerStep = ContainerStep.Prepare;
        traceDebug("local-source retry reason=%s target=%s delay=%d",
            reason,
            formatBlockPosSafe(activeContainerPos),
            actionDelayTicks
        );
    }

    private void pauseForBlockedLocalSourceWorkspace(BlockState blockingState) {
        state = FarmerState.Restocking;
        actionDelayTicks = Math.max(actionDelayTicks, STORAGE_RECHECK_INTERVAL_TICKS);
        containerMode = ContainerMode.LocalSourceShulker;
        containerStep = ContainerStep.Prepare;
        traceDebug("restock pause: local source workspace blocked at %s by %s retryDelay=%d",
            formatBlockPosSafe(activeContainerPos),
            blockingState.getBlock(),
            actionDelayTicks
        );
    }

    private void advanceAfterKitbotFailure() {
        activeRestockStage = RestockStage.SingleFallback;
        state = FarmerState.Restocking;
        traceDebug("restock advance: kitbot failed -> single fallback");
    }

    private void issueKitbotOrderForActiveTask() {
        pendingKitbotRequest = switch (activeRestockTask) {
            case Food -> KitbotRequest.Gapples;
            case Pickaxe -> KitbotRequest.Pickaxe;
            case Echest -> KitbotRequest.Echest;
            case None -> KitbotRequest.None;
        };
        if (pendingKitbotRequest == KitbotRequest.None) {
            finishRestockTask();
            return;
        }

        kitbotBaselineMatchingShulkers = countMatchingInventoryShulkersForTask(activeRestockTask);
        kitbotBaselineLooseUnits = countLooseUnitsForTask(activeRestockTask);
        kitbotRetryUsed = false;
        awaitingKitbotTeleport = true;
        lastKitbotOrderAtMs = System.currentTimeMillis();
        kitbotDeadlineAtMs = lastKitbotOrderAtMs + KITBOT_TIMEOUT_MS;

        traceDebug("kitbot-order request=%s task=%s baselineShulkers=%d baselineLoose=%d deadline=%d",
            pendingKitbotRequest,
            activeRestockTask,
            kitbotBaselineMatchingShulkers,
            kitbotBaselineLooseUnits,
            kitbotDeadlineAtMs
        );
        dispatchPendingKitbotOrder();

        activeRestockStage = RestockStage.WaitingForKitbotDelivery;
        state = FarmerState.Restocking;
    }

    private void handleLocalSourceShulkerMode() {
        if (pendingUsefulShulkerInventorySlot < 0 || pendingUsefulShulkerInventorySlot >= mc.player.getInventory().size()) {
            containerMode = ContainerMode.None;
            startInventorySourceAttemptOrAdvance();
            return;
        }

        switch (containerStep) {
            case Prepare -> {
                if (!ensureLocalRestockPose()) return;

                BlockState targetState = mc.world.getBlockState(activeContainerPos);
                if (targetState.getBlock() instanceof EnderChestBlock) {
                    traceDebug("local-source prepare clearing stray echest at %s before restock",
                        formatBlockPosSafe(activeContainerPos)
                    );
                    breakBlockAt(activeContainerPos);
                    actionDelayTicks = inventoryDelay.get();
                    return;
                }

                if (!targetState.isAir()) {
                    pauseForBlockedLocalSourceWorkspace(targetState);
                    return;
                }

                ItemStack expectedSourceStack = mc.player.getInventory().getStack(pendingUsefulShulkerInventorySlot).copy();
                if (!isUsefulLocalSourceShulkerStack(expectedSourceStack, activeRestockTask)) {
                    currentLocalSourceHotbarSlot = -1;
                    traceDebug("local-source prepare source slot %d invalid before hotbar move stack=%s task=%s",
                        pendingUsefulShulkerInventorySlot,
                        formatItemStackSafe(expectedSourceStack),
                        activeRestockTask
                    );
                    startInventorySourceAttemptOrAdvance();
                    return;
                }

                currentLocalSourceHotbarSlot = moveInventorySlotToHotbar(pendingUsefulShulkerInventorySlot, true, "local-source-shulker");
                if (currentLocalSourceHotbarSlot == -1) {
                    retryLocalSourceAttempt("hotbar-move-failed", STORAGE_RECHECK_INTERVAL_TICKS);
                    return;
                }

                ItemStack actualHotbarStack = mc.player.getInventory().getStack(currentLocalSourceHotbarSlot).copy();
                if (!isUsefulLocalSourceShulkerStack(actualHotbarStack, activeRestockTask)
                    || actualHotbarStack.getItem() != expectedSourceStack.getItem()) {
                    int mismatchedHotbarSlot = currentLocalSourceHotbarSlot;
                    currentLocalSourceHotbarSlot = -1;
                    traceDebug("local-source prepare hotbar mismatch sourceSlot=%d expected=%s hotbar=%d actual=%s task=%s",
                        pendingUsefulShulkerInventorySlot,
                        formatItemStackSafe(expectedSourceStack),
                        mismatchedHotbarSlot,
                        formatItemStackSafe(actualHotbarStack),
                        activeRestockTask
                    );
                    retryLocalSourceAttempt("hotbar-mismatch", inventoryDelay.get());
                    return;
                }

                if (!placeBlockFromHotbar(activeContainerPos, currentLocalSourceHotbarSlot)) {
                    retryLocalSourceAttempt("place-failed", inventoryDelay.get());
                    return;
                }

                actionDelayTicks = inventoryDelay.get();
                containerStep = ContainerStep.Open;
            }
            case Open -> {
                if (mc.currentScreen instanceof ShulkerBoxScreen) {
                    containerStep = ContainerStep.Transfer;
                    return;
                }

                BlockState targetState = mc.world.getBlockState(activeContainerPos);
                if (targetState.getBlock() instanceof ShulkerBoxBlock) {
                    if (!ensureContainerScreenOpen(activeContainerPos)) return;
                    return;
                }

                if (targetState.getBlock() instanceof EnderChestBlock) {
                    traceDebug("local-source open clearing stray echest at %s before reopen",
                        formatBlockPosSafe(activeContainerPos)
                    );
                    breakBlockAt(activeContainerPos);
                    actionDelayTicks = inventoryDelay.get();
                    containerStep = ContainerStep.Prepare;
                    return;
                }

                if (targetState.isAir()) {
                    retryLocalSourceAttempt("placed-shulker-missing", inventoryDelay.get());
                    return;
                }

                pauseForBlockedLocalSourceWorkspace(targetState);
            }
            case Transfer -> {
                boolean moved = transferFromOpenedContainerForActiveTask(false);
                traceDebug("local-source transfer moved=%s taskSatisfied=%s",
                    moved,
                    isRestockTaskSatisfied(activeRestockTask)
                );
                closeHandledScreen();
                containerStep = ContainerStep.Break;
            }
            case Break -> {
                if (!ensureLocalRestockPose()) return;

                if (mc.world.getBlockState(activeContainerPos).isAir()) {
                    genericWaitTicks = PICKUP_WAIT_TICKS;
                    containerStep = ContainerStep.WaitPickup;
                    return;
                }

                breakBlockAt(activeContainerPos);
            }
            case WaitPickup -> {
                if (genericWaitTicks > 0) {
                    genericWaitTicks--;
                    return;
                }

                currentLocalSourceHotbarSlot = -1;
                pendingUsefulShulkerInventorySlot = -1;
                localSourceInventorySlot = -1;
                containerMode = ContainerMode.None;
                containerStep = ContainerStep.None;
                if (isRestockTaskSatisfied(activeRestockTask)) finishRestockTask();
            }
            default -> containerStep = ContainerStep.Prepare;
        }
    }

    private void handleSingleFallbackMode() {
        switch (containerStep) {
            case Prepare -> {
                if (!ensureSingleFallbackPlaced()) {
                    handleSingleFallbackFailure();
                    return;
                }
                containerStep = ContainerStep.Open;
            }
            case Open -> {
                if (!ensureContainerScreenOpen(getSingleFallbackContainerPos())) return;
                if (mc.currentScreen instanceof GenericContainerScreen) containerStep = ContainerStep.Transfer;
            }
            case Transfer -> {
                fallbackRecoveredLooseItems = transferFromOpenedContainerForActiveTask(true);
                if (!isRestockTaskSatisfied(activeRestockTask)) {
                    int shulkerSlot = findBestUsefulContainerShulkerSlot(activeRestockTask, true);
                    if (shulkerSlot != -1 && quickMoveContainerSlot(shulkerSlot)) {
                        fallbackRecoveredUsefulShulker = true;
                        actionDelayTicks = inventoryDelay.get();
                    }
                }

                closeHandledScreen();

                if (!fallbackRecoveredLooseItems && !fallbackRecoveredUsefulShulker && !isRestockTaskSatisfied(activeRestockTask)) {
                    handleSingleFallbackFailure();
                    return;
                }

                traceDebug("single-fallback transfer looseRecovered=%s shulkerRecovered=%s taskSatisfied=%s",
                    fallbackRecoveredLooseItems,
                    fallbackRecoveredUsefulShulker,
                    isRestockTaskSatisfied(activeRestockTask)
                );
                containerStep = ContainerStep.Break;
            }
            case Break -> {
                if (mc.world.getBlockState(getSingleFallbackContainerPos()).isAir()) {
                    genericWaitTicks = PICKUP_WAIT_TICKS;
                    containerStep = ContainerStep.WaitPickup;
                    return;
                }

                breakBlockAt(getSingleFallbackContainerPos());
            }
            case WaitPickup -> {
                if (genericWaitTicks > 0) {
                    genericWaitTicks--;
                    return;
                }

                completeFallbackSuccess();
            }
            default -> containerStep = ContainerStep.Prepare;
        }
    }

    private void handleDoubleFallbackMode() {
        switch (containerStep) {
            case Prepare -> {
                if (!ensureDoubleFallbackPlaced()) {
                    handleDoubleFallbackFailure();
                    return;
                }
                containerStep = ContainerStep.Open;
            }
            case Open -> {
                if (!ensureContainerScreenOpen(getSingleFallbackContainerPos())) return;
                if (mc.currentScreen instanceof GenericContainerScreen) containerStep = ContainerStep.Transfer;
            }
            case Transfer -> {
                fallbackRecoveredLooseItems = transferFromOpenedContainerForActiveTask(true);
                if (!isRestockTaskSatisfied(activeRestockTask)) {
                    int shulkerSlot = findBestUsefulContainerShulkerSlot(activeRestockTask, true);
                    if (shulkerSlot != -1 && quickMoveContainerSlot(shulkerSlot)) {
                        fallbackRecoveredUsefulShulker = true;
                        actionDelayTicks = inventoryDelay.get();
                    }
                }

                closeHandledScreen();

                if (!fallbackRecoveredLooseItems && !fallbackRecoveredUsefulShulker && !isRestockTaskSatisfied(activeRestockTask)) {
                    handleDoubleFallbackFailure();
                    return;
                }

                traceDebug("double-fallback transfer looseRecovered=%s shulkerRecovered=%s taskSatisfied=%s",
                    fallbackRecoveredLooseItems,
                    fallbackRecoveredUsefulShulker,
                    isRestockTaskSatisfied(activeRestockTask)
                );
                containerStep = ContainerStep.Break;
            }
            case Break -> {
                boolean singleGone = mc.world.getBlockState(getSingleFallbackContainerPos()).isAir();
                boolean doubleGone = mc.world.getBlockState(getDoubleFallbackContainerPos()).isAir();
                if (singleGone && doubleGone) {
                    genericWaitTicks = PICKUP_WAIT_TICKS;
                    containerStep = ContainerStep.WaitPickup;
                    return;
                }

                if (!singleGone) breakBlockAt(getSingleFallbackContainerPos());
                if (!doubleGone) breakBlockAt(getDoubleFallbackContainerPos());
            }
            case WaitPickup -> {
                if (genericWaitTicks > 0) {
                    genericWaitTicks--;
                    return;
                }

                completeFallbackSuccess();
            }
            default -> containerStep = ContainerStep.Prepare;
        }
    }

    private boolean ensureSingleFallbackPlaced() {
        BlockPos singlePos = getSingleFallbackContainerPos();
        BlockState state = mc.world.getBlockState(singlePos);
        if (state.getBlock() instanceof EnderChestBlock) {
            traceDebug("single-fallback reuse existing echest at %s", formatBlockPosSafe(singlePos));
            return true;
        }
        if (!state.isAir()) {
            traceDebug("single-fallback failed: target %s occupied by %s", formatBlockPosSafe(singlePos), state.getBlock());
            return false;
        }

        int echestSlot = ensureItemAvailableInHotbar(Items.ENDER_CHEST);
        if (echestSlot == -1) {
            traceDebug("single-fallback failed: no ender chest available in inventory/hotbar");
            return false;
        }

        if (!ensureSinglePlacementPose()) return false;
        if (!placeBlockFromHotbar(singlePos, echestSlot)) {
            traceDebug("single-fallback place failed at %s using hotbar=%d", formatBlockPosSafe(singlePos), echestSlot);
            return false;
        }
        traceDebug("single-fallback placed echest at %s using hotbar=%d", formatBlockPosSafe(singlePos), echestSlot);
        actionDelayTicks = inventoryDelay.get();
        return true;
    }

    private boolean ensureDoubleFallbackPlaced() {
        BlockPos singlePos = getSingleFallbackContainerPos();
        BlockPos doublePos = getDoubleFallbackContainerPos();

        BlockState singleState = mc.world.getBlockState(singlePos);
        if (!(singleState.getBlock() instanceof EnderChestBlock)) {
            if (!singleState.isAir()) {
                traceDebug("double-fallback failed: primary %s occupied by %s", formatBlockPosSafe(singlePos), singleState.getBlock());
                return false;
            }
            int singleSlot = ensureItemAvailableInHotbar(Items.ENDER_CHEST);
            if (singleSlot == -1) {
                traceDebug("double-fallback failed: no echest available for primary placement");
                return false;
            }
            if (!ensureSinglePlacementPose()) return false;
            if (!placeBlockFromHotbar(singlePos, singleSlot)) {
                traceDebug("double-fallback primary place failed at %s using hotbar=%d", formatBlockPosSafe(singlePos), singleSlot);
                return false;
            }
            traceDebug("double-fallback primary placed at %s using hotbar=%d", formatBlockPosSafe(singlePos), singleSlot);
            actionDelayTicks = inventoryDelay.get();
            return true;
        }

        BlockState doubleState = mc.world.getBlockState(doublePos);
        if (doubleState.getBlock() instanceof EnderChestBlock) {
            traceDebug("double-fallback reuse existing secondary echest at %s", formatBlockPosSafe(doublePos));
            return true;
        }
        if (!doubleState.isAir()) {
            traceDebug("double-fallback failed: secondary %s occupied by %s", formatBlockPosSafe(doublePos), doubleState.getBlock());
            return false;
        }

        int doubleSlot = ensureItemAvailableInHotbar(Items.ENDER_CHEST);
        if (doubleSlot == -1) {
            traceDebug("double-fallback failed: no echest available for secondary placement");
            return false;
        }
        if (!ensureDoublePlacementPose()) return false;
        if (!placeBlockFromHotbar(doublePos, doubleSlot)) {
            traceDebug("double-fallback secondary place failed at %s using hotbar=%d", formatBlockPosSafe(doublePos), doubleSlot);
            return false;
        }
        traceDebug("double-fallback secondary placed at %s using hotbar=%d", formatBlockPosSafe(doublePos), doubleSlot);
        actionDelayTicks = inventoryDelay.get();
        return true;
    }

    private void completeFallbackSuccess() {
        traceDebug("fallback-success task=%s looseRecovered=%s shulkerRecovered=%s",
            activeRestockTask,
            fallbackRecoveredLooseItems,
            fallbackRecoveredUsefulShulker
        );
        fallbackRecoveredUsefulShulker = false;
        fallbackRecoveredLooseItems = false;
        containerMode = ContainerMode.None;
        containerStep = ContainerStep.None;
        activeRestockStage = RestockStage.InventoryShulkers;
        if (isRestockTaskSatisfied(activeRestockTask)) finishRestockTask();
    }

    private void handleSingleFallbackFailure() {
        traceDebug("single-fallback failure task=%s", activeRestockTask);
        closeHandledScreen();
        containerMode = ContainerMode.None;
        containerStep = ContainerStep.None;
        fallbackRecoveredUsefulShulker = false;
        fallbackRecoveredLooseItems = false;

        if (activeRestockTask == RestockTask.Echest) {
            activeRestockStage = RestockStage.DoubleFallback;
            return;
        }

        hardFailAndToggle("Unable to perform restock for %s.", activeRestockTask.name().toLowerCase(Locale.ROOT));
    }

    private void handleDoubleFallbackFailure() {
        traceDebug("double-fallback failure task=%s", activeRestockTask);
        closeHandledScreen();
        containerMode = ContainerMode.None;
        containerStep = ContainerStep.None;
        triggerEchestTerminalFailure("Out of echest material supply.");
    }

    private void hardFailAndToggle(String message, Object... args) {
        hardFailMessage = String.format(Locale.ROOT, message, args);
        warning(hardFailMessage);
        state = FarmerState.HardFail;
        toggle();
    }

    private void triggerEchestTerminalFailure(String message) {
        traceDebug("terminal-failure message=%s", message);
        hardFailMessage = message;
        state = FarmerState.HardFail;
        disableWorker("terminal-echest-failure");
        warning(message);
        disableThmHwyMonitorIfActive();

        AutoReconnect autoReconnect = Modules.get().get(AutoReconnect.class);
        if (autoReconnect != null && autoReconnect.isActive()) autoReconnect.toggle();

        if (mc.getNetworkHandler() != null && mc.getNetworkHandler().getConnection() != null) {
            mc.getNetworkHandler().getConnection().disconnect(Text.literal("[ObsidianFarmerTHM] " + message));
        }
        toggle();
    }

    private boolean isHotbarSlotReservedByManager(int hotbarSlot) {
        if (hotbarSlot < 0 || hotbarSlot >= 9) return false;
        if (!manageHotbar.get()) return false;

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
        if (!manageHotbar.get()) return -1;

        HotbarManager manager = Modules.get().get(HotbarManager.class);
        if (manager == null || !manager.isActive()) return -1;

        for (int i = 0; i < 9; i++) {
            if (manager.managesSlot(i) && manager.getManagedItem(i) == item) return i;
        }

        return -1;
    }

    private int findHotbarSlotWithItem(Item item) {
        if (item == null || item == Items.AIR || mc.player == null) return -1;
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).getItem() == item) return i;
        }
        return -1;
    }

    private int verifyResolvedHotbarSlot(int sourceSlot, int hotbarSlot, Item expectedItem, String reason) {
        if (mc.player == null || hotbarSlot < 0 || hotbarSlot >= 9) return -1;

        ItemStack actualStack = mc.player.getInventory().getStack(hotbarSlot);
        if (!actualStack.isEmpty() && actualStack.getItem() == expectedItem) return hotbarSlot;

        traceDebug("hotbar-resolve mismatch reason=%s sourceSlot=%d targetHotbar=%d expected=%s actual=%s",
            reason,
            sourceSlot,
            hotbarSlot,
            expectedItem,
            formatItemStackSafe(actualStack)
        );
        return -1;
    }

    private int moveInventorySlotToHotbar(int slot) {
        return moveInventorySlotToHotbar(slot, true, "generic");
    }

    private int moveInventorySlotToHotbar(int slot, boolean allowSameItemReuse, String reason) {
        if (slot < 0 || mc.player == null) return -1;

        ItemStack sourceStack = mc.player.getInventory().getStack(slot);
        if (sourceStack.isEmpty()) {
            traceDebug("hotbar-resolve failed reason=%s sourceSlot=%d expected=empty", reason, slot);
            return -1;
        }

        Item expectedItem = sourceStack.getItem();
        if (slot < 9) return verifyResolvedHotbarSlot(slot, slot, expectedItem, reason + "-already-hotbar");

        if (allowSameItemReuse) {
            int existingHotbarSlot = findHotbarSlotWithItem(expectedItem);
            if (existingHotbarSlot != -1) {
                traceDebug("hotbar-resolve reusing existing item reason=%s sourceSlot=%d hotbar=%d item=%s reserved=%s",
                    reason,
                    slot,
                    existingHotbarSlot,
                    expectedItem,
                    isHotbarSlotReservedByManager(existingHotbarSlot)
                );
                return verifyResolvedHotbarSlot(slot, existingHotbarSlot, expectedItem, reason + "-reuse-existing");
            }
        }

        int hotbarSlot = getPreferredManagedHotbarSlot(expectedItem);
        if (hotbarSlot != -1) {
            traceDebug("hotbar-resolve using managed slot reason=%s sourceSlot=%d hotbar=%d item=%s",
                reason,
                slot,
                hotbarSlot,
                expectedItem
            );
        } else {
            hotbarSlot = findReusableHotbarSlot();
            if (hotbarSlot == -1) {
                traceDebug("hotbar-resolve failed reason=%s sourceSlot=%d item=%s no-unreserved-slot=true",
                    reason,
                    slot,
                    expectedItem
                );
                return -1;
            }

            traceDebug("hotbar-resolve using fallback slot reason=%s sourceSlot=%d hotbar=%d item=%s",
                reason,
                slot,
                hotbarSlot,
                expectedItem
            );
        }

        InvUtils.move().from(slot).toHotbar(hotbarSlot);
        return verifyResolvedHotbarSlot(slot, hotbarSlot, expectedItem, reason + "-post-move");
    }

    private int findReusableHotbarSlot() {
        if (mc.player == null) return -1;

        for (int i = 0; i < 9; i++) {
            if (isHotbarSlotReservedByManager(i)) {
                traceDebug("hotbar-slot skip reserved slot=%d item=%s", i, getReservedHotbarItem(i));
                continue;
            }

            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isEmpty()) return i;
        }

        for (int i = 0; i < 9; i++) {
            if (isHotbarSlotReservedByManager(i)) continue;

            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!stack.isIn(ItemTags.PICKAXES) && !stack.isOf(Items.ENDER_CHEST)) return i;
        }

        return -1;
    }

    private int ensureItemAvailableInHotbar(Item item) {
        if (item == null || mc.player == null) return -1;

        FindItemResult hotbar = InvUtils.findInHotbar(item);
        if (hotbar.found()) return verifyResolvedHotbarSlot(hotbar.slot(), hotbar.slot(), item, "ensure-item-existing");

        FindItemResult inv = InvUtils.find(item);
        if (!inv.found()) return -1;
        return moveInventorySlotToHotbar(inv.slot(), true, "ensure-item");
    }

    private boolean placeBlockFromHotbar(BlockPos pos, int hotbarSlot) {
        if (hotbarSlot < 0 || mc.player == null) return false;
        ItemStack hotbarStack = mc.player.getInventory().getStack(hotbarSlot).copy();
        int prevSlot = mc.player.getInventory().getSelectedSlot();
        if (prevSlot != hotbarSlot) InvUtils.swap(hotbarSlot, false);
        boolean placed = BlockUtils.place(pos, Hand.MAIN_HAND, hotbarSlot, false, 0, true, true, true);
        if (prevSlot != hotbarSlot) InvUtils.swap(prevSlot, false);
        traceDebug("place-block pos=%s hotbar=%d stack=%s placed=%s",
            formatBlockPosSafe(pos),
            hotbarSlot,
            formatItemStackSafe(hotbarStack),
            placed
        );
        return placed;
    }

    private void breakBlockAt(BlockPos pos) {
        if (mc.world == null || mc.player == null || pos == null) return;
        HighwayBuilderTHM highwayBuilder = Modules.get().get(HighwayBuilderTHM.class);
        int toolSlot;

        if (highwayBuilder != null) {
            toolSlot = highwayBuilder.breakContainerBlockForSharedUtility(pos, false, false);
        } else {
            toolSlot = ensureBestToolAvailableInHotbar(mc.world.getBlockState(pos));
            int prevSlot = mc.player.getInventory().getSelectedSlot();
            if (toolSlot != -1 && toolSlot != prevSlot) InvUtils.swap(toolSlot, false);
            Rotations.rotate(Rotations.getYaw(pos), Rotations.getPitch(pos), () -> BlockUtils.breakBlock(pos, true));
            if (toolSlot != -1 && toolSlot != prevSlot) InvUtils.swap(prevSlot, false);
        }

        traceDebug("break-block pos=%s toolHotbar=%d block=%s",
            formatBlockPosSafe(pos),
            toolSlot,
            mc.world.getBlockState(pos).getBlock()
        );
    }

    private int ensureBestToolAvailableInHotbar(BlockState state) {
        int inventorySlot = findBestToolInventorySlotFor(state);
        if (inventorySlot == -1) return -1;
        return moveInventorySlotToHotbar(inventorySlot, true, "best-tool");
    }

    private int findBestToolInventorySlotFor(BlockState state) {
        if (mc.player == null || state == null) return -1;
        double best = -1.0;
        int bestSlot = -1;
        for (int i = 0; i < mc.player.getInventory().getMainStacks().size(); i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            double score = stack.getMiningSpeedMultiplier(state);
            if (score > best) {
                best = score;
                bestSlot = i;
            }
        }
        return bestSlot;
    }

    private int findBestToolHotbarSlotFor(BlockState state) {
        if (mc.player == null || state == null) return -1;
        double best = -1.0;
        int bestSlot = -1;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            double score = stack.getMiningSpeedMultiplier(state);
            if (score > best) {
                best = score;
                bestSlot = i;
            }
        }
        return bestSlot;
    }

    private int countEmptySlotsInInventory() {
        if (mc.player == null) return 0;
        int count = 0;
        for (int i = 0; i < mc.player.getInventory().getMainStacks().size(); i++) {
            if (mc.player.getInventory().getStack(i).isEmpty()) count++;
        }
        return count;
    }

    private int getEchestRestockTargetUnits() {
        int emptySlots = countEmptySlotsInInventory();
        int loosePickaxes = countLoosePickaxesInInventory();
        int deficit = Math.max((savePickaxes.get() + minimumPickaxeRestock.get()) - loosePickaxes, 0);
        int usableSlots = Math.max((emptySlots - (deficit + (3 + minimumEmptySlots.get()))) * 8, 0);
        int stacks = (int) Math.floor(usableSlots / 64.0);
        int thresholdUnits = Math.max((minimumEchests.get() + 3) - countLooseInventoryEnderChests(), 0);
        return Math.max(stacks * 64, thresholdUnits);
    }

    private FarmerState getPostRoutineState() {
        return activeRestockTask == RestockTask.None ? FarmerState.Farming : FarmerState.Restocking;
    }

    private boolean hasObservedKitbotSupplyGain() {
        return countMatchingInventoryShulkersForTask(activeRestockTask) > kitbotBaselineMatchingShulkers
            || countLooseUnitsForTask(activeRestockTask) > kitbotBaselineLooseUnits;
    }

    private void retryActiveKitbotOrder() {
        if (pendingKitbotRequest == KitbotRequest.None) {
            advanceAfterKitbotFailure();
            return;
        }

        kitbotRetryUsed = true;
        awaitingKitbotTeleport = true;
        lastKitbotOrderAtMs = System.currentTimeMillis();
        kitbotDeadlineAtMs = lastKitbotOrderAtMs + KITBOT_TIMEOUT_MS;
        traceDebug("kitbot-order retry request=%s newDeadline=%d", pendingKitbotRequest, kitbotDeadlineAtMs);
        warning("KitBot order for %s timed out without delivery. Retrying once.", pendingKitbotRequest.name().toLowerCase(Locale.ROOT));
        dispatchPendingKitbotOrder();
    }

    private void dispatchPendingKitbotOrder() {
        traceDebug("kitbot-order dispatch request=%s amount=%d",
            pendingKitbotRequest,
            pendingKitbotRequest == KitbotRequest.Echest ? echestKitRestockAmount.get() : 1
        );
        switch (pendingKitbotRequest) {
            case Gapples -> KitbotFrontend.kitOrder(KitbotFrontend.KitName.Gapples, 1);
            case Pickaxe -> KitbotFrontend.kitOrder(KitbotFrontend.KitName.Pickaxe, 1);
            case Echest -> KitbotFrontend.kitOrder(KitbotFrontend.KitName.Echest, echestKitRestockAmount.get());
            default -> { }
        }
    }

    private BlockPos resolveUsableContainerInteractionPos(BlockPos configuredPos) {
        if (configuredPos == null || mc.world == null) return null;

        BlockState state = mc.world.getBlockState(configuredPos);
        Block block = state.getBlock();
        if (block instanceof ChestBlock || block instanceof TrappedChestBlock) {
            return resolveCanonicalChestHalf(configuredPos, state);
        }

        return configuredPos;
    }

    private BlockPos resolveCanonicalChestHalf(BlockPos pos, BlockState state) {
        if (pos == null || state == null) return pos;
        if (!(state.getBlock() instanceof ChestBlock || state.getBlock() instanceof TrappedChestBlock)) return pos;
        if (!state.contains(ChestBlock.CHEST_TYPE) || !state.contains(ChestBlock.FACING)) return pos;

        ChestType chestType = state.get(ChestBlock.CHEST_TYPE);
        if (chestType == ChestType.SINGLE || chestType == ChestType.LEFT) return pos;

        Direction facing = state.get(ChestBlock.FACING);
        BlockPos otherHalf = pos.offset(facing.rotateYCounterclockwise());
        BlockState otherState = mc.world.getBlockState(otherHalf);
        if (otherState.getBlock().getClass() == state.getBlock().getClass()) return otherHalf;
        return pos;
    }

    private void traceRuntimeChanges() {
        boolean workerActive = isWorkerActive();
        boolean changed = lastTracedState != state
            || lastTracedRestockTask != activeRestockTask
            || lastTracedRestockStage != activeRestockStage
            || lastTracedContainerMode != containerMode
            || lastTracedContainerStep != containerStep
            || lastTracedResumeStep != resumeStep
            || lastTracedTrashStep != trashStep
            || lastTracedStorageFull != storageChestFullOfShulkers
            || lastTracedReopenDeposit != reopenDepositAfterAutoBreak
            || lastTracedPostJoinDelay != postJoinDelayPending
            || lastTracedPendingDepositCheck != pendingDepositAfterStorageCheck
            || lastTracedWorkerActive != workerActive;

        if (!changed) return;

        traceDebug("runtime-transition primary=%s secondary=%s waits[action=%d generic=%d postRestock=%d] inv[obsidian=%d food=%d pickaxes=%d echests=%d empty=%d trashStacks=%d] kit[request=%s awaitingTp=%s retryUsed=%s deadline=%d baselineShulkers=%d baselineLoose=%d]",
            formatBlockPosSafe(activeContainerPos),
            formatBlockPosSafe(activeSecondaryContainerPos),
            actionDelayTicks,
            genericWaitTicks,
            postRestockResumeDelayTicks,
            countLooseObsidianInInventory(),
            countConfiguredFoodItemsInInventory(),
            countLoosePickaxesInInventory(),
            countLooseInventoryEnderChests(),
            countEmptySlotsInInventory(),
            countMatchingTrashStacks(),
            pendingKitbotRequest,
            awaitingKitbotTeleport,
            kitbotRetryUsed,
            kitbotDeadlineAtMs,
            kitbotBaselineMatchingShulkers,
            kitbotBaselineLooseUnits
        );

        lastTracedState = state;
        lastTracedRestockTask = activeRestockTask;
        lastTracedRestockStage = activeRestockStage;
        lastTracedContainerMode = containerMode;
        lastTracedContainerStep = containerStep;
        lastTracedResumeStep = resumeStep;
        lastTracedTrashStep = trashStep;
        lastTracedStorageFull = storageChestFullOfShulkers;
        lastTracedReopenDeposit = reopenDepositAfterAutoBreak;
        lastTracedPostJoinDelay = postJoinDelayPending;
        lastTracedPendingDepositCheck = pendingDepositAfterStorageCheck;
        lastTracedWorkerActive = workerActive;
    }

    private boolean isWorkerActive() {
        BetterEchestFarmer worker = Modules.get().get(BetterEchestFarmer.class);
        return worker != null && worker.isActive();
    }

    private boolean isTunnelMinerConflictActive() {
        TunnelMinerModule tunnelMiner = Modules.get().get(TunnelMinerModule.class);
        return tunnelMiner != null && tunnelMiner.isActive();
    }

    private int countMatchingTrashStacks() {
        if (mc.player == null) return 0;

        int count = 0;
        for (int i = 0; i < mc.player.getInventory().getMainStacks().size(); i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!stack.isEmpty() && trashItems.get().contains(stack.getItem())) count++;
        }
        return count;
    }

    private String formatBlockPosSafe(BlockPos pos) {
        if (pos == null) return "null";
        return String.format(Locale.ROOT, "(%d, %d, %d)", pos.getX(), pos.getY(), pos.getZ());
    }

    private String formatPlayerPosSafe() {
        if (mc.player == null) return "null";
        return String.format(Locale.ROOT, "(%.3f, %.3f, %.3f)", mc.player.getX(), mc.player.getY(), mc.player.getZ());
    }

    private String formatCurrentScreenSafe() {
        return mc.currentScreen == null ? "none" : mc.currentScreen.getClass().getSimpleName();
    }

    private String formatItemStackSafe(ItemStack stack) {
        if (stack == null) return "null";
        if (stack.isEmpty()) return "empty";
        return String.format(Locale.ROOT, "%s x%d", stack.getItem(), stack.getCount());
    }

    private Path getDebugLogPath() {
        if (mc == null || mc.runDirectory == null) return null;
        return mc.runDirectory.toPath().resolve("logs").resolve(DEBUG_LOG_FILE_NAME);
    }

    private void writeDebugLine(String line) {
        Path path = getDebugLogPath();
        if (path == null) return;

        try {
            Path parent = path.getParent();
            if (parent != null) Files.createDirectories(parent);
            Files.writeString(path, line, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            if (!debugFileErrorLogged) {
                THMAddon.LOG.warn("Failed to write obsidian farmer debug log: {}", e.getMessage());
                debugFileErrorLogged = true;
            }
        }
    }

    private void traceDebug(String format, Object... args) {
        String message;
        try {
            message = String.format(Locale.ROOT, format, args);
        } catch (Throwable t) {
            message = format + " [trace-format-error=" + t.getMessage() + "]";
        }

        String line;
        try {
            line = String.format(
                Locale.ROOT,
                "[%s] OF state=%s task=%s/%s container=%s/%s resume=%s trash=%s worker=%s server=%s delay[action=%d generic=%d postRestock=%d] pos=%s rot=(%.2f, %.2f) screen=%s msg=%s%n",
                Instant.now(),
                state,
                activeRestockTask,
                activeRestockStage,
                containerMode,
                containerStep,
                resumeStep,
                trashStep,
                isWorkerActive(),
                ServerStatusHandler.getInstance().getCommittedState(),
                actionDelayTicks,
                genericWaitTicks,
                postRestockResumeDelayTicks,
                formatPlayerPosSafe(),
                mc.player == null ? 0.0f : mc.player.getYaw(),
                mc.player == null ? 0.0f : mc.player.getPitch(),
                formatCurrentScreenSafe(),
                message
            );
        } catch (Throwable t) {
            line = String.format(Locale.ROOT, "[%s] OF trace-build-failed msg=%s error=%s%n", Instant.now(), message, t.getMessage());
        }

        writeDebugLine(line);
    }
}
