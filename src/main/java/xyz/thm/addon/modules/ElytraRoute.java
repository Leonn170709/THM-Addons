package xyz.thm.addon.modules;

import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.movement.elytrafly.ElytraFlightModes;
import meteordevelopment.meteorclient.systems.modules.movement.elytrafly.ElytraFly;
import meteordevelopment.meteorclient.systems.modules.player.AutoEat;
import meteordevelopment.meteorclient.systems.modules.player.AutoGap;
import meteordevelopment.meteorclient.systems.modules.player.Rotation;
import meteordevelopment.meteorclient.systems.modules.render.FreeLook;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.SlotUtils;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.client.gui.screen.DisconnectedScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.ChunkStatus;
import xyz.thm.addon.THMAddon;
import xyz.thm.addon.utils.InventoryManager;
import xyz.thm.addon.utils.ServerReconnectService;
import xyz.thm.addon.utils.ServerStatusHandler;
import xyz.thm.addon.utils.ServerStatusHandler.ServerState;
import xyz.thm.addon.utils.THMUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class ElytraRoute extends Module {
    public static ElytraRoute INSTANCE;

    private static final int CHEST_SLOT_INDEX = 38;
    private static final int PLAYER_INVENTORY_SLOTS = 36;
    private static final int TAKEOFF_SECOND_JUMP_DELAY = 4;
    private static final int TAKEOFF_GLIDE_TIMEOUT = 20;
    private static final int TAKEOFF_MAX_FAILURES = 3;
    private static final int SHULKER_PICKUP_TIMEOUT = 120;
    private static final int SHULKER_PICKUP_REACQUIRE_INTERVAL = 20;
    private static final double SHULKER_PICKUP_SEARCH_RADIUS = 8.0;
    private static final int PAD_SEARCH_CHECKS_PER_TICK = 512;
    private static final String RECONNECT_RESUME_LISTENER_KEY = "elytra-route-resume";
    private static final String RECONNECT_FAILURE_LISTENER_KEY = "elytra-route-failure";
    private static final int MAIN_SERVER_GATE_UNKNOWN_GRACE_TICKS = 40;
    private static final int MAIN_SERVER_RESUME_DELAY_TICKS = 120;
    private static final long MAIN_SERVER_GATE_WARNING_INTERVAL_MS = 5_000L;
    private static final int LANDING_CHUNK_LOAD_RADIUS = 1;
    private static final long LANDING_CHUNK_WARNING_INTERVAL_MS = 5_000L;
    private static final int RESTOCK_INVALID_SOURCE_RETRY_LIMIT = PLAYER_INVENTORY_SLOTS;

    private final SettingGroup sgRoute = settings.getDefaultGroup();
    private final SettingGroup sgRestock = settings.createGroup("Restock");
    private final SettingGroup sgFood = settings.createGroup("Food");
    private final SettingGroup sgHome = settings.createGroup("Home");
    private final SettingGroup sgReconnect = settings.createGroup("Reconnect");

    private final Setting<Integer> destinationX = sgRoute.add(new IntSetting.Builder()
        .name("destination-x")
        .description("Target X coordinate.")
        .defaultValue(0)
        .build()
    );

    private final Setting<Integer> destinationZ = sgRoute.add(new IntSetting.Builder()
        .name("destination-z")
        .description("Target Z coordinate.")
        .defaultValue(0)
        .build()
    );

    private final Setting<Double> arrivalRadius = sgRoute.add(new DoubleSetting.Builder()
        .name("arrival-radius")
        .description("Horizontal distance where the route is considered complete.")
        .defaultValue(64)
        .min(1)
        .sliderRange(1, 256)
        .build()
    );

    private final Setting<Integer> flyYLevel = sgRoute.add(new IntSetting.Builder()
        .name("fly-y-level")
        .description("Cruise Y level while gliding.")
        .defaultValue(130)
        .range(-64, 320)
        .sliderRange(64, 256)
        .build()
    );

    private final Setting<Boolean> autoFreeLook = sgRoute.add(new BoolSetting.Builder()
        .name("auto-freelook")
        .description("Enable Meteor FreeLook while the route module runs.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> useBounceMode = sgRoute.add(new BoolSetting.Builder()
        .name("use-bounce-mode")
        .description("Switch ElytraFly into Bounce mode while routing, then restore the previous ElytraFly mode afterward.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> autoReconnect = sgReconnect.add(new BoolSetting.Builder()
        .name("auto-reconnect")
        .description("Keep Elytra Route alive through disconnects and resume only after ServerReconnectService reaches MAIN_SERVER.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> restartRejoinDelayMinutes = sgReconnect.add(new IntSetting.Builder()
        .name("restart-rejoin-delay-minutes")
        .description("Delay in minutes applied to Meteor AutoReconnect.")
        .defaultValue(15)
        .range(1, 240)
        .sliderRange(1, 60)
        .visible(autoReconnect::get)
        .build()
    );

    private final Setting<Boolean> restockElytras = sgRestock.add(new BoolSetting.Builder()
        .name("restock-elytras")
        .description("Restock elytras from inventory shulkers when you run out of flight-ready elytras.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> fixedElytraRestockAmount = sgRestock.add(new BoolSetting.Builder()
        .name("fixed-elytra-restock-amount")
        .description("Use a fixed loose-elytra restock target instead of filling all allowed inventory space.")
        .defaultValue(true)
        .visible(restockElytras::get)
        .build()
    );

    private final Setting<Integer> elytraRestockAmount = sgRestock.add(new IntSetting.Builder()
        .name("elytra-restock-amount")
        .description("How many good loose elytras to keep after a restock pass.")
        .defaultValue(2)
        .range(1, 9)
        .sliderRange(1, 9)
        .visible(() -> restockElytras.get() && fixedElytraRestockAmount.get())
        .build()
    );

    private final Setting<Integer> minimumEmptySlots = sgRestock.add(new IntSetting.Builder()
        .name("minimum-empty-slots")
        .description("Minimum empty loose inventory slots to preserve during restock math.")
        .defaultValue(3)
        .range(3, 10)
        .sliderRange(3, 10)
        .build()
    );

    private final Setting<Integer> inventoryDelay = sgRestock.add(new IntSetting.Builder()
        .name("inventory-delay")
        .description("Ticks to wait between inventory interactions.")
        .defaultValue(3)
        .range(0, 20)
        .sliderRange(0, 10)
        .build()
    );

    private final Setting<Boolean> foodRestock = sgFood.add(new BoolSetting.Builder()
        .name("food-restock")
        .description("Restocks one configured food stack when your valid food count drops to the saved amount.")
        .defaultValue(false)
        .build()
    );

    private final Setting<List<Item>> foodTypes = sgFood.add(new ItemListSetting.Builder()
        .name("food-types")
        .description("Which food item counts as restock food. Maximum 1 food type.")
        .defaultValue()
        .visible(foodRestock::get)
        .onChanged(this::handleFoodTypesChanged)
        .build()
    );

    private final Setting<Integer> saveFood = sgFood.add(new IntSetting.Builder()
        .name("save-food")
        .description("Restock food when your total configured food count is at or below this value.")
        .defaultValue(16)
        .range(1, 32)
        .sliderRange(1, 32)
        .visible(foodRestock::get)
        .build()
    );

    private final Setting<Boolean> periodicHomeSave = sgHome.add(new BoolSetting.Builder()
        .name("periodic-home-save")
        .description("Periodically run /sethome while the route is active.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> homeSaveIntervalMinutes = sgHome.add(new IntSetting.Builder()
        .name("home-save-interval-minutes")
        .description("Minutes between home saves.")
        .defaultValue(10)
        .range(1, 60)
        .sliderRange(1, 60)
        .visible(periodicHomeSave::get)
        .build()
    );

    private final Setting<String> homeName = sgHome.add(new StringSetting.Builder()
        .name("home-name")
        .description("Name passed to /sethome.")
        .defaultValue("")
        .visible(periodicHomeSave::get)
        .build()
    );

    private enum State {
        TAKEOFF_AWAIT_GROUND,
        TAKEOFF_FIRST_JUMP,
        TAKEOFF_SECOND_JUMP_WAIT,
        TAKEOFF_WAIT_GLIDE,
        FLIGHT,
        LANDING,
        RESTOCK_MOVE_TO_PAD,
        RESTOCK_PLACE,
        RESTOCK_OPEN,
        RESTOCK_LOOT,
        RESTOCK_CLOSE,
        RESTOCK_BREAK,
        RESTOCK_PICKUP,
        ARRIVED_WAIT,
        OUT_OF_ELYTRAS_WAIT
    }

    private enum LandingReason {
        RESTOCK,
        ARRIVAL,
        OUT_OF_ELYTRAS
    }

    private enum RestockKind {
        NONE,
        FOOD,
        ELYTRA
    }

    private enum ResumeState {
        ROUTE_FLIGHT,
        ARRIVED_WAIT,
        OUT_OF_ELYTRAS_WAIT
    }

    private enum RestockFailureReason {
        NO_MATCHING_SHULKER,
        PLACE_FAILED,
        OPEN_FAILED,
        LOOT_TIMEOUT,
        TARGET_UNMET,
        NO_FLIGHT_READY_ELYTRA,
        PICKUP_FAILED
    }

    private record RestockPad(BlockPos playerBase, BlockPos containerBase) {}

    private record ShulkerCandidate(int slot, int foodCount, int flightReadyElytras) {}

    private State state;
    private LandingReason landingReason;
    private RestockKind activeRestockKind;
    private ResumeState resumeStateAfterRestock;
    private int stateTicks;
    private int takeoffFailures;
    private int actionDelayTicks;
    private boolean queuedFoodRestock;
    private boolean queuedElytraRestock;
    private boolean foodRestockSuppressed;
    private long homeSaveNextAtMs;
    private long routeStartMs;
    private double routeStartDistance;
    private boolean clampingFoodTypes;

    private FreeLook freeLookModule;
    private boolean managedFreeLook;

    private Rotation rotationModule;
    private Setting<Rotation.LockMode> rotationYawLockMode;
    private Setting<Double> rotationYawAngle;
    private Setting<Rotation.LockMode> rotationPitchLockMode;
    private Setting<Double> rotationPitchAngle;
    private boolean rotationSnapshotTaken;
    private boolean rotationWasActive;
    private Rotation.LockMode rotationPrevYawLockMode;
    private double rotationPrevYawAngle;
    private Rotation.LockMode rotationPrevPitchLockMode;
    private double rotationPrevPitchAngle;
    private boolean routeRotationOwned;

    private boolean bounceSettingsSnapshotTaken;
    private boolean bounceElytraFlyPaused;
    private ElytraFlightModes bouncePrevFlightMode;
    private Rotation.LockMode bouncePrevYawLockMode;
    private double bouncePrevYaw;
    private boolean bouncePrevAutoJump;
    private boolean bouncePrevManualTakeoff;
    private boolean bouncePrevRestart;

    private boolean reconnectListenersRegistered;
    private boolean previousAutoReconnectToggleState;
    private boolean routeStartPending;
    private boolean reconnectPaused;
    private boolean reconnectResumeDelayStarted;
    private boolean reconnectArmAnnounced;
    private boolean reconnectCycleOwnedByRoute;
    private boolean reconnectPreflightWarningAnnounced;
    private long activeReconnectCycleId;
    private int reconnectResumeDelayTicks;
    private int mainServerGateTicks;
    private long lastMainServerGateWarningAtMs;
    private long lastLandingChunkWarningAtMs;

    private RestockPad restockPad;
    private BlockPos restockPadSearchAnchor;
    private int restockPadSearchRadius;
    private int restockPadSearchX;
    private int restockPadSearchZ;
    private BlockPos placedShulkerPos;
    private int restockSourceInventorySlot;
    private int restockSourceHotbarSlot;
    private int pickupTicks;
    private ItemEntity pickupTarget;
    private int currentSourceFailureTicks;
    private int expectedShulkerCountAfterPickup;
    private int invalidRestockSourceAttempts;
    private boolean restockOpenedSourceValidated;
    private boolean openedInvalidRestockSource;
    private RestockFailureReason deferredRestockFailureReason;
    private String deferredRestockFailureMessage;

    public ElytraRoute() {
        super(THMAddon.MAIN, "elytra-route", "Flies to a target bearing, restocks from inventory shulkers, and manages periodic home saves.");
        INSTANCE = this;
    }

    @Override
    public void onActivate() {
        resetRuntime();

        if (mc.player == null || mc.world == null) {
            error("Player or world was not available.");
            toggle();
            return;
        }

        if (!keybind.isSet()) {
            error("Set a module keybind before enabling Elytra Route.");
            toggle();
            return;
        }

        if (periodicHomeSave.get() && homeName.get().isBlank()) {
            error("Enter a home name before enabling periodic home save.");
            toggle();
            return;
        }

        homeSaveNextAtMs = System.currentTimeMillis() + (homeSaveIntervalMinutes.get() * 60_000L);

        registerReconnectServiceListeners();
        if (!allowMainServerRouteActions("activate")) return;

        startRouteFromCurrentMainServerState("activate", true);
    }

    @Override
    public void onDeactivate() {
        releaseMovement();
        restoreRotationSnapshot();

        if (managedFreeLook && freeLookModule != null && freeLookModule.isActive()) {
            freeLookModule.toggle();
        }
        managedFreeLook = false;

        closeHandledRestockScreenIfOpen();

        if (!routeStartPending || bounceSettingsSnapshotTaken) {
            stopBounceElytraFlyForRouteStop();
            restoreBounceRouteSettings();
        }
        disarmOwnedReconnectIfNeeded("deactivate");
        unregisterReconnectServiceListeners();
        clearReconnectRuntime(true);
    }

    @EventHandler(priority = 1000)
    private void onTickPreForceFlightMovement(TickEvent.Pre event) {
        if (!shouldForceRouteFlightMovement()) return;
        forceRouteFlightMovement();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        handleAutoReconnectToggleTransitions();
        clearStaleDisconnectedScreenIfLiveConnected();

        if (mc.player == null || mc.world == null) {
            releaseMovement();
            return;
        }

        if (!allowMainServerRouteActions("tick")) return;

        if (routeStartPending) {
            startRouteFromCurrentMainServerState("main-server-gate", true);
            return;
        }

        ElytraFly elytraFly = Modules.get().get(ElytraFly.class);
        if (!bounceElytraFlyPaused) configureBounceRouteMode(elytraFly, computeBearingYaw());

        if (!isElytraFlyCompatible(elytraFly, false)) {
            error("ElytraFly became inactive or incompatible while the route was running.");
            toggle();
            return;
        }

        if (actionDelayTicks > 0) actionDelayTicks--;
        if (foodRestockSuppressed && countConfiguredFoodItemsInInventory() > saveFood.get()) {
            foodRestockSuppressed = false;
        }

        if (state != State.FLIGHT && shouldPauseForEating()) {
            releaseMovement();
            restoreRotationSnapshot();
            return;
        }

        if (state == State.FLIGHT && periodicHomeSave.get() && isHomeSaveDue()) sendHomeSave();

        if (isRestockState(state)) {
            updateQueuedRestocksWhileRestocking();
        }

        switch (state) {
            case TAKEOFF_AWAIT_GROUND -> tickTakeoffAwaitGround();
            case TAKEOFF_FIRST_JUMP -> tickTakeoffFirstJump();
            case TAKEOFF_SECOND_JUMP_WAIT -> tickTakeoffSecondJumpWait();
            case TAKEOFF_WAIT_GLIDE -> tickTakeoffWaitGlide();
            case FLIGHT -> tickFlight();
            case LANDING -> tickLanding();
            case RESTOCK_MOVE_TO_PAD -> tickRestockMoveToPad();
            case RESTOCK_PLACE -> tickRestockPlace();
            case RESTOCK_OPEN -> tickRestockOpen();
            case RESTOCK_LOOT -> tickRestockLoot();
            case RESTOCK_CLOSE -> tickRestockClose();
            case RESTOCK_BREAK -> tickRestockBreak();
            case RESTOCK_PICKUP -> tickRestockPickup();
            case ARRIVED_WAIT -> tickArrivedWait();
            case OUT_OF_ELYTRAS_WAIT -> tickOutOfElytrasWait();
        }
    }

    @EventHandler
    private void onTickPost(TickEvent.Post event) {
        if (!shouldForceRouteFlightMovement()) return;
        forceRouteFlightMovement();
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        if (!isActive()) return;

        releaseMovement();

        if (autoReconnect.get()) {
            beginRouteReconnectPause("game-left", true);
        } else {
            disable();
        }
    }

    private boolean shouldForceRouteFlightMovement() {
        if (mc.player == null || mc.world == null) return false;
        if (!hasLiveServerConnection()) return false;
        if (routeStartPending || reconnectPaused || state != State.FLIGHT) return false;
        if (ServerStatusHandler.getInstance().getCommittedState() != ServerState.MAIN_SERVER) return false;
        return !isInsideArrivalRadius() && !needsFoodRestock() && !needsElytraRestock();
    }

    private void forceRouteFlightMovement() {
        ElytraFly elytraFly = Modules.get().get(ElytraFly.class);
        mc.options.forwardKey.setPressed(true);

        if (shouldUseRouteBounceMode(elytraFly) && !mc.player.isGliding() && !mc.player.isOnGround()) {
            mc.options.jumpKey.setPressed(true);
            return;
        }

        if (!isBounceElytraFly(elytraFly)) applyFlightAltitudeControl(false);
    }

    public int getHudUsableElytraCount() {
        return countHudUsableElytras();
    }

    public long getHudFlightTimeSeconds() {
        return getTotalFlightTimeSeconds();
    }

    public double getHudEtaSeconds() {
        if (!isActive()) return -1;
        double remaining = getRemainingDistance();
        double progressed = Math.max(0, routeStartDistance - remaining);
        if (progressed < 5) return -1;

        long elapsedMs = System.currentTimeMillis() - routeStartMs;
        if (elapsedMs <= 0) return -1;

        double rate = progressed / elapsedMs;
        return rate <= 0 ? -1 : remaining / (rate * 1000.0);
    }

    public double getRemainingDistance() {
        if (mc.player == null) return 0;
        return Math.hypot(destinationX.get() - mc.player.getX(), destinationZ.get() - mc.player.getZ());
    }

    private void startRouteFromCurrentMainServerState(String source, boolean initialActivation) {
        if (mc.player == null || mc.world == null) return;

        routeStartPending = false;
        reconnectPaused = false;
        reconnectResumeDelayStarted = false;
        reconnectResumeDelayTicks = 0;
        mainServerGateTicks = 0;
        lastMainServerGateWarningAtMs = 0L;

        routeStartMs = System.currentTimeMillis();
        routeStartDistance = getRemainingDistance();

        initializeRouteSupportModules();
        if (autoReconnect.get()) armRouteReconnectIfNeeded(source + "-main-server");

        ElytraFly elytraFly = Modules.get().get(ElytraFly.class);
        configureBounceRouteMode(elytraFly, computeBearingYaw());
        if (!isElytraFlyCompatible(elytraFly, true)) {
            toggle();
            return;
        }

        if (getRemainingDistance() <= arrivalRadius.get()) {
            if (initialActivation) {
                error("Destination is already inside arrival radius.");
                toggle();
            } else {
                state = State.ARRIVED_WAIT;
                stateTicks = 0;
            }
            return;
        }

        if (needsFoodRestock()) {
            ResumeState resumeState = countTotalFlightReadyElytras() <= 0 && !restockElytras.get()
                ? ResumeState.OUT_OF_ELYTRAS_WAIT
                : ResumeState.ROUTE_FLIGHT;
            startRestockInterrupt(RestockKind.FOOD, resumeState);
            return;
        }

        if (countTotalFlightReadyElytras() <= 0) {
            if (restockElytras.get()) startRestockInterrupt(RestockKind.ELYTRA, ResumeState.ROUTE_FLIGHT);
            else beginLanding(LandingReason.OUT_OF_ELYTRAS);
            return;
        }

        if (mc.player.isGliding()) {
            enterFlightState();
        } else {
            state = State.TAKEOFF_AWAIT_GROUND;
            stateTicks = 0;
        }
    }

    private void initializeRouteSupportModules() {
        freeLookModule = Modules.get().get(FreeLook.class);
        managedFreeLook = autoFreeLook.get() && freeLookModule != null;
        if (managedFreeLook && !freeLookModule.isActive()) {
            freeLookModule.toggle();
        }

        rotationModule = Modules.get().get(Rotation.class);
        cacheRotationSettings();
    }

    private boolean allowMainServerRouteActions(String source) {
        ServerState serverState = ServerStatusHandler.getInstance().getCommittedState();
        boolean liveConnection = hasLiveServerConnection();
        if (liveConnection && serverState == ServerState.MAIN_SERVER) {
            mainServerGateTicks = 0;
            lastMainServerGateWarningAtMs = 0L;

            if (reconnectPaused) {
                if (activeReconnectCycleId != 0L && !reconnectResumeDelayStarted) {
                    scheduleReconnectMainServerResume(activeReconnectCycleId, source);
                }

                if (reconnectResumeDelayTicks > 0) {
                    releaseMovement();
                    restoreRotationSnapshot();
                    reconnectResumeDelayTicks--;
                    return false;
                }

                resumeRouteAfterReconnect(source);
                return false;
            }

            return true;
        }

        mainServerGateTicks++;
        releaseMovement();
        restoreRotationSnapshot();
        closeHandledRestockScreenIfOpen();
        if (mc.player != null && !routeStartPending) pauseBounceElytraFlyForGroundControl();

        if (serverState == ServerState.UNKNOWN
            && mainServerGateTicks < MAIN_SERVER_GATE_UNKNOWN_GRACE_TICKS
            && !reconnectPaused) {
            return false;
        }

        reconnectPaused = true;
        if (autoReconnect.get()) {
            String reconnectSource = liveConnection
                ? source + "-" + serverState.name().toLowerCase()
                : source + "-disconnected";
            armRouteReconnectIfNeeded(reconnectSource);
        } else {
            warnMainServerGate(serverState);
        }

        return false;
    }

    private void beginRouteReconnectPause(String source, boolean armReconnect) {
        releaseMovement();
        restoreRotationSnapshot();
        closeHandledRestockScreenIfOpen();
        if (mc.player != null && !routeStartPending) pauseBounceElytraFlyForGroundControl();

        reconnectPaused = true;
        reconnectResumeDelayStarted = false;
        reconnectResumeDelayTicks = 0;
        if (armReconnect && autoReconnect.get()) armRouteReconnectIfNeeded(source);
    }

    private void resumeRouteAfterReconnect(String source) {
        if (mc.player == null || mc.world == null) return;

        if (placedShulkerPos != null) {
            reconnectPaused = false;
            reconnectResumeDelayStarted = false;
            reconnectResumeDelayTicks = 0;
            state = State.RESTOCK_BREAK;
            stateTicks = 0;
            pickupTicks = 0;
            pickupTarget = null;
            currentSourceFailureTicks = 0;
            info("MAIN_SERVER restored after reconnect. Cleaning up placed restock shulker before resuming route.");
            return;
        }

        clearRestockRuntimeForReconnectResume();
        info("MAIN_SERVER restored after reconnect. Resuming Elytra Route.");
        startRouteFromCurrentMainServerState(source + "-resume", false);
    }

    private void scheduleReconnectMainServerResume(long cycleId, String context) {
        if (reconnectResumeDelayStarted && reconnectResumeDelayTicks > 0) return;

        reconnectPaused = true;
        reconnectResumeDelayStarted = true;
        reconnectResumeDelayTicks = MAIN_SERVER_RESUME_DELAY_TICKS;
        info(
            "Reconnect service reached MAIN_SERVER (%s). Waiting 6.0s before Elytra Route resume (cycle %d).",
            context == null ? "unknown" : context,
            cycleId
        );
    }

    private void warnMainServerGate(ServerState serverState) {
        long now = System.currentTimeMillis();
        if (now - lastMainServerGateWarningAtMs < MAIN_SERVER_GATE_WARNING_INTERVAL_MS) return;
        lastMainServerGateWarningAtMs = now;
        warning(
            "Elytra Route paused until MAIN_SERVER. Current server state is %s; enable auto-reconnect to recover automatically.",
            serverState.name()
        );
    }

    private void closeHandledRestockScreenIfOpen() {
        if (!isRestockState(state)) return;
        if (mc.currentScreen instanceof HandledScreen<?>) mc.currentScreen.close();
    }

    private void handleAutoReconnectToggleTransitions() {
        boolean currentToggle = autoReconnect.get();
        if (currentToggle == previousAutoReconnectToggleState) return;

        if (currentToggle) {
            armRouteReconnectIfNeeded("toggle-on");
            info("Auto-reconnect enabled for Elytra Route.");
        } else {
            disarmOwnedReconnectIfNeeded("toggle-off");
            activeReconnectCycleId = 0L;
            reconnectCycleOwnedByRoute = false;
            reconnectArmAnnounced = false;
            reconnectPreflightWarningAnnounced = false;
            info("Auto-reconnect disabled for Elytra Route. Route reconnect state was cleared.");
        }

        previousAutoReconnectToggleState = currentToggle;
    }

    private boolean hasLiveServerConnection() {
        return mc != null
            && mc.player != null
            && mc.world != null
            && mc.getNetworkHandler() != null
            && mc.getNetworkHandler().getConnection() != null
            && mc.getNetworkHandler().getConnection().isOpen();
    }

    private void clearStaleDisconnectedScreenIfLiveConnected() {
        if (!hasLiveServerConnection()) return;
        if (!(mc.currentScreen instanceof DisconnectedScreen)) return;
        info("Clearing stale DisconnectedScreen while Elytra Route is live in-world.");
        mc.setScreen(null);
    }

    private ServerReconnectService reconnectService() {
        return ServerReconnectService.getInstance();
    }

    private void registerReconnectServiceListeners() {
        if (reconnectListenersRegistered) return;
        reconnectService().registerResumeListener(RECONNECT_RESUME_LISTENER_KEY, this::onReconnectMainServerReady);
        reconnectService().registerFailureListener(RECONNECT_FAILURE_LISTENER_KEY, this::onReconnectFailure);
        reconnectListenersRegistered = true;
    }

    private void unregisterReconnectServiceListeners() {
        if (!reconnectListenersRegistered) return;
        reconnectService().unregisterResumeListener(RECONNECT_RESUME_LISTENER_KEY);
        reconnectService().unregisterFailureListener(RECONNECT_FAILURE_LISTENER_KEY);
        reconnectListenersRegistered = false;
    }

    private void onReconnectMainServerReady(long cycleId, String contextTag, long armedAtMs, long detectedAtMs) {
        if (mc != null && !mc.isOnThread()) {
            mc.execute(() -> onReconnectMainServerReady(cycleId, contextTag, armedAtMs, detectedAtMs));
            return;
        }

        if (!isActive()) return;
        if (activeReconnectCycleId == 0L || cycleId != activeReconnectCycleId) return;
        scheduleReconnectMainServerResume(cycleId, contextTag);
    }

    private void onReconnectFailure(
        long cycleId,
        ServerReconnectService.FailureReason reason,
        String detail,
        String contextTag,
        long armedAtMs,
        long failedAtMs
    ) {
        if (mc != null && !mc.isOnThread()) {
            mc.execute(() -> onReconnectFailure(cycleId, reason, detail, contextTag, armedAtMs, failedAtMs));
            return;
        }

        if (!isActive()) return;
        if (activeReconnectCycleId == 0L || cycleId != activeReconnectCycleId) return;

        warning("Elytra Route reconnect failed (%s): %s", reason.name(), detail == null ? "" : detail);
        clearReconnectRuntime(true);
        toggle();
    }

    private void armRouteReconnectIfNeeded(String source) {
        ServerReconnectService.ReconnectPreflight preflight = reconnectService().getReconnectPreflight();
        if (activeReconnectCycleId != 0L && preflight.serviceArmed() && preflight.cycleId() == activeReconnectCycleId) return;

        long cycleId;
        boolean owned;
        if (preflight.serviceArmed() && preflight.cycleId() > 0L) {
            cycleId = preflight.cycleId();
            owned = false;
        } else {
            cycleId = reconnectService().armReconnect(restartRejoinDelayMinutes.get(), "ElytraRoute:" + source);
            owned = true;
        }

        if (activeReconnectCycleId != cycleId) {
            reconnectArmAnnounced = false;
            reconnectPreflightWarningAnnounced = false;
        }
        activeReconnectCycleId = cycleId;
        reconnectCycleOwnedByRoute = owned;
        validateRouteReconnectPreflight(cycleId, source);

        if (!reconnectArmAnnounced) {
            info("Elytra Route AutoReconnect handling armed through ServerReconnectService (cycle %d).", cycleId);
            reconnectArmAnnounced = true;
        }
    }

    private void validateRouteReconnectPreflight(long cycleId, String source) {
        ServerReconnectService.ReconnectPreflight preflight = reconnectService().getReconnectPreflight();
        boolean valid = preflight.serviceArmed()
            && preflight.cycleId() == cycleId
            && preflight.autoReconnectModulePresent()
            && preflight.autoReconnectActive()
            && preflight.autoReconnectSettingDelaySeconds() != null
            && Math.abs(preflight.autoReconnectSettingDelaySeconds() - preflight.effectiveDelaySeconds()) <= 0.5
            && preflight.lastServerConnectionPresent();

        if (valid) {
            reconnectPreflightWarningAnnounced = false;
            return;
        }

        if (reconnectPreflightWarningAnnounced) return;
        reconnectPreflightWarningAnnounced = true;

        if (!preflight.serviceArmed() || preflight.cycleId() != cycleId) {
            warning("Elytra Route reconnect preflight issue (%s): reconnect service did not arm expected cycle %d.", source, cycleId);
        } else if (!preflight.autoReconnectModulePresent()) {
            warning("Elytra Route reconnect preflight issue (%s): Meteor AutoReconnect module was not found.", source);
        } else if (!preflight.autoReconnectActive()) {
            warning("Elytra Route reconnect preflight issue (%s): Meteor AutoReconnect is not active after arming.", source);
        } else if (preflight.autoReconnectSettingDelaySeconds() == null) {
            warning("Elytra Route reconnect preflight issue (%s): Meteor AutoReconnect delay could not be read.", source);
        } else if (Math.abs(preflight.autoReconnectSettingDelaySeconds() - preflight.effectiveDelaySeconds()) > 0.5) {
            warning(
                "Elytra Route reconnect preflight issue (%s): Meteor AutoReconnect delay %.1fs does not match service delay %ds.",
                source,
                preflight.autoReconnectSettingDelaySeconds(),
                preflight.effectiveDelaySeconds()
            );
        } else if (!preflight.lastServerConnectionPresent()) {
            warning("Elytra Route reconnect preflight issue (%s): Meteor AutoReconnect does not have a last-server target yet.", source);
        }
    }

    private void disarmOwnedReconnectIfNeeded(String reason) {
        if (activeReconnectCycleId == 0L || !reconnectCycleOwnedByRoute) return;
        ServerReconnectService.ReconnectPreflight preflight = reconnectService().getReconnectPreflight();
        if (preflight.serviceArmed() && preflight.cycleId() == activeReconnectCycleId) {
            reconnectService().disarmReconnect("ElytraRoute:" + reason);
        }
    }

    private void clearReconnectRuntime(boolean clearCycleBinding) {
        reconnectPaused = false;
        reconnectResumeDelayStarted = false;
        reconnectResumeDelayTicks = 0;
        mainServerGateTicks = 0;
        lastMainServerGateWarningAtMs = 0L;
        reconnectArmAnnounced = false;
        if (clearCycleBinding) {
            activeReconnectCycleId = 0L;
            reconnectCycleOwnedByRoute = false;
        }
    }

    private void clearRestockRuntimeForReconnectResume() {
        activeRestockKind = RestockKind.NONE;
        resumeStateAfterRestock = ResumeState.ROUTE_FLIGHT;
        queuedFoodRestock = false;
        queuedElytraRestock = false;
        restockPad = null;
        resetRestockPadSearch();
        placedShulkerPos = null;
        restockSourceInventorySlot = -1;
        restockSourceHotbarSlot = -1;
        pickupTicks = 0;
        pickupTarget = null;
        currentSourceFailureTicks = 0;
        expectedShulkerCountAfterPickup = -1;
        invalidRestockSourceAttempts = 0;
        restockOpenedSourceValidated = false;
        openedInvalidRestockSource = false;
        clearDeferredRestockFailure();
    }

    private void resetRuntime() {
        state = State.TAKEOFF_AWAIT_GROUND;
        landingReason = LandingReason.RESTOCK;
        activeRestockKind = RestockKind.NONE;
        resumeStateAfterRestock = ResumeState.ROUTE_FLIGHT;
        routeStartPending = true;
        previousAutoReconnectToggleState = autoReconnect.get();
        stateTicks = 0;
        takeoffFailures = 0;
        actionDelayTicks = 0;
        queuedFoodRestock = false;
        queuedElytraRestock = false;
        foodRestockSuppressed = false;
        managedFreeLook = false;
        rotationSnapshotTaken = false;
        routeRotationOwned = false;
        bounceSettingsSnapshotTaken = false;
        bounceElytraFlyPaused = false;
        restockPad = null;
        resetRestockPadSearch();
        placedShulkerPos = null;
        restockSourceInventorySlot = -1;
        restockSourceHotbarSlot = -1;
        pickupTicks = 0;
        pickupTarget = null;
        currentSourceFailureTicks = 0;
        expectedShulkerCountAfterPickup = -1;
        invalidRestockSourceAttempts = 0;
        restockOpenedSourceValidated = false;
        openedInvalidRestockSource = false;
        lastLandingChunkWarningAtMs = 0L;
        clearReconnectRuntime(true);
        clearDeferredRestockFailure();
    }

    private boolean isElytraFlyCompatible(ElytraFly elytraFly, boolean announce) {
        if (elytraFly == null) {
            if (announce) error("Enable ElytraFly before starting Elytra Route.");
            return false;
        }

        boolean routeBounceMode = shouldUseRouteBounceMode(elytraFly);
        boolean bounceMode = isBounceElytraFly(elytraFly);
        if (!elytraFly.isActive() && !routeBounceMode) {
            if (announce) error("Enable ElytraFly before starting Elytra Route.");
            return false;
        }

        ElytraFlightModes mode = elytraFly.flightMode.get();
        if (mode != ElytraFlightModes.Vanilla && mode != ElytraFlightModes.Packet && mode != ElytraFlightModes.Bounce) {
            String modeName = mode.name();
            if (announce) error("ElytraFly mode %s is not supported.", modeName);
            return false;
        }

        if (!bounceMode && elytraFly.autoTakeOff.get()) {
            if (announce) error("Disable ElytraFly auto-take-off.");
            return false;
        }

        if (!bounceMode && elytraFly.autoPilot.get()) {
            if (announce) error("Disable ElytraFly auto-pilot.");
            return false;
        }

        if (!bounceMode && elytraFly.useFireworks.get()) {
            if (announce) error("Disable ElytraFly use-fireworks.");
            return false;
        }

        if (!bounceMode && elytraFly.autoHover.get()) {
            if (announce) error("Disable ElytraFly auto-hover.");
            return false;
        }

        if (!elytraFly.replace.get()) {
            if (announce) error("Enable ElytraFly elytra-replace.");
            return false;
        }

        return true;
    }

    private boolean isBounceElytraFly(ElytraFly elytraFly) {
        return elytraFly != null && elytraFly.flightMode.get() == ElytraFlightModes.Bounce;
    }

    private void configureBounceRouteMode(ElytraFly elytraFly, float yaw) {
        if (!shouldUseRouteBounceMode(elytraFly)) return;

        takeBounceSettingsSnapshotIfNeeded(elytraFly);
        if (!isBounceElytraFly(elytraFly)) elytraFly.flightMode.set(ElytraFlightModes.Bounce);
        elytraFly.yawLockMode.set(Rotation.LockMode.Simple);
        elytraFly.yaw.set(toBounceYaw(yaw));
        elytraFly.manualTakeoff.set(false);
        elytraFly.autoJump.set(true);
        elytraFly.restart.set(true);
    }

    private void takeBounceSettingsSnapshotIfNeeded(ElytraFly elytraFly) {
        if (bounceSettingsSnapshotTaken || elytraFly == null) return;

        bounceSettingsSnapshotTaken = true;
        bouncePrevFlightMode = elytraFly.flightMode.get();
        bouncePrevYawLockMode = elytraFly.yawLockMode.get();
        bouncePrevYaw = elytraFly.yaw.get();
        bouncePrevAutoJump = elytraFly.autoJump.get();
        bouncePrevManualTakeoff = elytraFly.manualTakeoff.get();
        bouncePrevRestart = elytraFly.restart.get();
    }

    private void restoreBounceRouteSettings() {
        ElytraFly elytraFly = Modules.get().get(ElytraFly.class);
        if (!bounceSettingsSnapshotTaken || elytraFly == null) {
            bounceSettingsSnapshotTaken = false;
            bounceElytraFlyPaused = false;
            return;
        }

        if (bouncePrevFlightMode != null) elytraFly.flightMode.set(bouncePrevFlightMode);
        elytraFly.yawLockMode.set(bouncePrevYawLockMode);
        elytraFly.yaw.set(bouncePrevYaw);
        elytraFly.autoJump.set(bouncePrevAutoJump);
        elytraFly.manualTakeoff.set(bouncePrevManualTakeoff);
        elytraFly.restart.set(bouncePrevRestart);
        bounceSettingsSnapshotTaken = false;
        bounceElytraFlyPaused = false;
    }

    private boolean shouldUseRouteBounceMode(ElytraFly elytraFly) {
        return elytraFly != null && (useBounceMode.get() || isBounceElytraFly(elytraFly));
    }

    private double toBounceYaw(float yaw) {
        double normalized = normalizeYaw(yaw);
        return normalized < 0 ? normalized + 360.0 : normalized;
    }

    private void pauseBounceElytraFlyForGroundControl() {
        ElytraFly elytraFly = Modules.get().get(ElytraFly.class);
        if (!shouldUseRouteBounceMode(elytraFly) || bounceElytraFlyPaused) return;

        configureBounceRouteMode(elytraFly, computeBearingYaw());

        if (elytraFly.isActive()) {
            elytraFly.toggle();
            releaseMovement();
        }

        bounceElytraFlyPaused = true;
    }

    private boolean resumeBounceElytraFlyForFlight() {
        ElytraFly elytraFly = Modules.get().get(ElytraFly.class);
        if (!shouldUseRouteBounceMode(elytraFly)) return true;

        configureBounceRouteMode(elytraFly, computeBearingYaw());
        if (!elytraFly.isActive()) elytraFly.toggle();
        bounceElytraFlyPaused = false;
        return elytraFly.isActive();
    }

    private void stopBounceElytraFlyForRouteStop() {
        ElytraFly elytraFly = Modules.get().get(ElytraFly.class);
        if (!shouldUseRouteBounceMode(elytraFly)) return;

        if (elytraFly.isActive()) {
            elytraFly.toggle();
            releaseMovement();
        }

        bounceElytraFlyPaused = true;
    }

    @SuppressWarnings("unchecked")
    private void cacheRotationSettings() {
        if (rotationModule == null) return;

        rotationYawLockMode = (Setting<Rotation.LockMode>) rotationModule.settings.get("yaw-lock-mode");
        rotationYawAngle = (Setting<Double>) rotationModule.settings.get("yaw-angle");
        rotationPitchLockMode = (Setting<Rotation.LockMode>) rotationModule.settings.get("pitch-lock-mode");
        rotationPitchAngle = (Setting<Double>) rotationModule.settings.get("pitch-angle");
    }

    private void takeRotationSnapshotIfNeeded() {
        if (rotationModule == null || rotationSnapshotTaken) return;
        if (rotationYawLockMode == null || rotationYawAngle == null || rotationPitchLockMode == null || rotationPitchAngle == null) return;

        rotationSnapshotTaken = true;
        rotationWasActive = rotationModule.isActive();
        rotationPrevYawLockMode = rotationYawLockMode.get();
        rotationPrevYawAngle = rotationYawAngle.get();
        rotationPrevPitchLockMode = rotationPitchLockMode.get();
        rotationPrevPitchAngle = rotationPitchAngle.get();
    }

    private void steerRouteYaw(float yaw) {
        ElytraFly elytraFly = Modules.get().get(ElytraFly.class);
        if (isBounceElytraFly(elytraFly) && !bounceElytraFlyPaused) {
            configureBounceRouteMode(elytraFly, yaw);
            if (mc.player != null) mc.player.setYaw(normalizeYaw(yaw));
            return;
        }

        if (!ownRouteRotation(yaw) && mc.player != null) mc.player.setYaw(normalizeYaw(yaw));
    }

    private boolean ownRouteRotation(float yaw) {
        if (rotationModule == null || rotationYawLockMode == null || rotationYawAngle == null || rotationPitchLockMode == null || rotationPitchAngle == null) return false;
        takeRotationSnapshotIfNeeded();

        rotationYawLockMode.set(Rotation.LockMode.Simple);
        rotationYawAngle.set((double) normalizeYaw(yaw));
        rotationPitchLockMode.set(Rotation.LockMode.Simple);
        rotationPitchAngle.set(0.0);

        if (!rotationModule.isActive()) rotationModule.toggle();
        routeRotationOwned = true;
        return true;
    }

    private void restoreRotationSnapshot() {
        if (rotationModule == null || !rotationSnapshotTaken) return;
        if (rotationYawLockMode == null || rotationYawAngle == null || rotationPitchLockMode == null || rotationPitchAngle == null) return;

        if (routeRotationOwned && rotationModule.isActive()) rotationModule.toggle();

        rotationYawLockMode.set(rotationPrevYawLockMode);
        rotationYawAngle.set(rotationPrevYawAngle);
        rotationPitchLockMode.set(rotationPrevPitchLockMode);
        rotationPitchAngle.set(rotationPrevPitchAngle);

        if (rotationWasActive && !rotationModule.isActive()) rotationModule.toggle();

        routeRotationOwned = false;
    }

    private void enterFlightState() {
        state = State.FLIGHT;
        stateTicks = 0;
        steerRouteYaw(computeBearingYaw());
    }

    private void tickTakeoffAwaitGround() {
        releaseMovement();
        restoreRotationSnapshot();

        if (mc.player.isGliding()) {
            takeoffFailures = 0;
            enterFlightState();
            return;
        }

        if (!mc.player.isOnGround()) return;

        if (handleGroundedRouteInterruptBeforeTakeoff()) return;

        if (!hasElytraEquipped() && !equipFlightReadyElytraForTakeoff()) {
            error("Unable to equip a flight-ready elytra before takeoff.");
            toggle();
            return;
        }

        if (hasUnusableWornElytra() && hasFlightReadySpareInInventory() && !repairWornElytraIfNeeded()) {
            error("Unable to swap in a grounded backup elytra.");
            toggle();
            return;
        }

        if (actionDelayTicks > 0) return;

        if (!resumeBounceElytraFlyForFlight()) {
            error("Unable to resume ElytraFly Bounce mode for takeoff.");
            toggle();
            return;
        }

        mc.options.jumpKey.setPressed(true);
        state = State.TAKEOFF_FIRST_JUMP;
        stateTicks = 0;
    }

    private void tickTakeoffFirstJump() {
        mc.options.jumpKey.setPressed(stateTicks == 0);
        stateTicks++;

        if (stateTicks > 1) {
            mc.options.jumpKey.setPressed(false);
            state = State.TAKEOFF_SECOND_JUMP_WAIT;
            stateTicks = 0;
        }
    }

    private void tickTakeoffSecondJumpWait() {
        mc.options.jumpKey.setPressed(false);
        stateTicks++;

        if (stateTicks >= TAKEOFF_SECOND_JUMP_DELAY) {
            steerRouteYaw(computeBearingYaw());
            mc.options.jumpKey.setPressed(true);
            state = State.TAKEOFF_WAIT_GLIDE;
            stateTicks = 0;
        }
    }

    private void tickTakeoffWaitGlide() {
        steerRouteYaw(computeBearingYaw());
        mc.options.jumpKey.setPressed(stateTicks == 0);
        stateTicks++;

        if (mc.player.isGliding()) {
            takeoffFailures = 0;
            enterFlightState();
            return;
        }

        if (stateTicks > TAKEOFF_GLIDE_TIMEOUT) {
            mc.options.jumpKey.setPressed(false);
            restoreRotationSnapshot();
            takeoffFailures++;
            if (takeoffFailures >= TAKEOFF_MAX_FAILURES) {
                error("Glide takeoff failed after %d attempts.", TAKEOFF_MAX_FAILURES);
                toggle();
                return;
            }

            state = State.TAKEOFF_AWAIT_GROUND;
            stateTicks = 0;
        }
    }

    private void tickFlight() {
        ElytraFly elytraFly = Modules.get().get(ElytraFly.class);
        boolean routeBounceMode = shouldUseRouteBounceMode(elytraFly);

        if (!mc.player.isGliding()) {
            if (routeBounceMode && !mc.player.isOnGround()) {
                if (!resumeBounceElytraFlyForFlight()) {
                    error("Unable to enable ElytraFly Bounce mode for flight.");
                    toggle();
                    return;
                }

                if (handleFlightInterrupts()) return;

                float yaw = computeBearingYaw();
                steerRouteYaw(yaw);
                mc.options.forwardKey.setPressed(true);
                mc.options.jumpKey.setPressed(true);
                return;
            }

            releaseMovement();
            restoreRotationSnapshot();
            state = State.TAKEOFF_AWAIT_GROUND;
            stateTicks = 0;
            return;
        }

        if (!resumeBounceElytraFlyForFlight()) {
            error("Unable to enable ElytraFly Bounce mode for flight.");
            toggle();
            return;
        }

        if (handleFlightInterrupts()) return;

        float yaw = computeBearingYaw();
        steerRouteYaw(yaw);
        mc.options.forwardKey.setPressed(true);
        if (!isBounceElytraFly(elytraFly)) applyFlightAltitudeControl(false);
    }

    private boolean handleFlightInterrupts() {
        if (isInsideArrivalRadius()) {
            beginLanding(LandingReason.ARRIVAL);
            return true;
        }

        if (needsFoodRestock()) {
            startRestockInterrupt(RestockKind.FOOD, ResumeState.ROUTE_FLIGHT);
            return true;
        }

        if (needsElytraRestock()) {
            if (!restockElytras.get()) {
                beginLanding(LandingReason.OUT_OF_ELYTRAS);
            } else {
                startRestockInterrupt(RestockKind.ELYTRA, ResumeState.ROUTE_FLIGHT);
            }
            return true;
        }

        return false;
    }

    private void beginLanding(LandingReason reason) {
        landingReason = reason;
        state = State.LANDING;
        stateTicks = 0;
        releaseMovement();
        pauseBounceElytraFlyForGroundControl();
        if (reason == LandingReason.RESTOCK && mc.currentScreen != null) mc.currentScreen.close();
    }

    private void tickLanding() {
        stopLandingHorizontalMovement();

        if (mc.player.isGliding() && !areLandingChunksLoaded()) {
            holdLandingForChunkLoad();
            warnLandingChunksUnloaded();
            return;
        }

        lastLandingChunkWarningAtMs = 0L;
        mc.options.jumpKey.setPressed(false);
        mc.options.sneakKey.setPressed(mc.player.isGliding());

        if (mc.player.isGliding()) return;

        mc.options.sneakKey.setPressed(false);
        restoreRotationSnapshot();
        if (!mc.player.isOnGround()) return;

        switch (landingReason) {
            case ARRIVAL -> state = State.ARRIVED_WAIT;
            case OUT_OF_ELYTRAS -> state = State.OUT_OF_ELYTRAS_WAIT;
            case RESTOCK -> state = State.RESTOCK_MOVE_TO_PAD;
        }

        stateTicks = 0;
    }

    private void holdLandingForChunkLoad() {
        stopLandingHorizontalMovement();
        dampenLandingHorizontalVelocity();
        mc.options.sneakKey.setPressed(false);
        mc.options.jumpKey.setPressed(true);
    }

    private void stopLandingHorizontalMovement() {
        mc.options.forwardKey.setPressed(false);
        mc.options.backKey.setPressed(false);
        mc.options.leftKey.setPressed(false);
        mc.options.rightKey.setPressed(false);
    }

    private void dampenLandingHorizontalVelocity() {
        if (mc.player == null) return;
        Vec3d velocity = mc.player.getVelocity();
        mc.player.setVelocity(velocity.x * 0.25, velocity.y, velocity.z * 0.25);
    }

    private boolean areLandingChunksLoaded() {
        if (mc.player == null || mc.world == null) return false;

        int centerChunkX = mc.player.getBlockX() >> 4;
        int centerChunkZ = mc.player.getBlockZ() >> 4;

        for (int x = -LANDING_CHUNK_LOAD_RADIUS; x <= LANDING_CHUNK_LOAD_RADIUS; x++) {
            for (int z = -LANDING_CHUNK_LOAD_RADIUS; z <= LANDING_CHUNK_LOAD_RADIUS; z++) {
                if (mc.world.getChunkManager().getChunk(centerChunkX + x, centerChunkZ + z, ChunkStatus.FULL, false) == null) {
                    return false;
                }
            }
        }

        return true;
    }

    private void warnLandingChunksUnloaded() {
        long now = System.currentTimeMillis();
        if (now - lastLandingChunkWarningAtMs < LANDING_CHUNK_WARNING_INTERVAL_MS) return;
        lastLandingChunkWarningAtMs = now;
        warning("Landing paused until nearby chunks finish loading.");
    }

    private void startRestockInterrupt(RestockKind kind, ResumeState resumeState) {
        if (kind == RestockKind.NONE) return;

        boolean continuingRestockCycle = isRestockState(state) || (state == State.LANDING && landingReason == LandingReason.RESTOCK);
        if (!continuingRestockCycle) invalidRestockSourceAttempts = 0;

        if (kind == RestockKind.FOOD) {
            queuedFoodRestock = false;
            queuedElytraRestock = resumeState == ResumeState.ROUTE_FLIGHT && needsElytraRestock();
        } else {
            queuedElytraRestock = false;
            queuedFoodRestock = needsFoodRestock();
        }

        activeRestockKind = kind;
        resumeStateAfterRestock = resumeState;
        restockPad = null;
        resetRestockPadSearch();
        placedShulkerPos = null;
        restockSourceInventorySlot = -1;
        restockSourceHotbarSlot = -1;
        pickupTicks = 0;
        pickupTarget = null;
        currentSourceFailureTicks = 0;
        expectedShulkerCountAfterPickup = -1;
        restockOpenedSourceValidated = false;
        openedInvalidRestockSource = false;
        clearDeferredRestockFailure();

        beginLanding(LandingReason.RESTOCK);
    }

    private void updateQueuedRestocksWhileRestocking() {
        if (activeRestockKind != RestockKind.FOOD && needsFoodRestock()) queuedFoodRestock = true;
        if (resumeStateAfterRestock == ResumeState.ROUTE_FLIGHT
            && activeRestockKind != RestockKind.ELYTRA
            && needsElytraRestock()
            && !isInsideArrivalRadius()) {
            queuedElytraRestock = true;
        }
    }

    private void tickRestockMoveToPad() {
        releaseMovement();

        if (countEmptyLooseSlots() < minimumEmptySlots.get()) {
            handleRestockFailure(RestockFailureReason.TARGET_UNMET, "Not enough empty inventory slots remain for safe restocking.");
            return;
        }

        if (shouldRestockFoodThisPass() && !hasUsableFoodRestockTarget()) {
            handleRestockFailure(RestockFailureReason.TARGET_UNMET, "Not enough inventory capacity remains to satisfy the configured food restock target.");
            return;
        }

        if (shouldRestockElytraThisPass() && !hasUsableElytraRestockTarget()) {
            handleRestockFailure(RestockFailureReason.TARGET_UNMET, "Not enough inventory capacity remains to satisfy the configured elytra restock target.");
            return;
        }

        if (restockPad == null) {
            restockPad = findNextRestockPad();
            if (restockPad == null) return;
        }

        moveToward(restockPad.playerBase.getX() + 0.5, restockPad.playerBase.getZ() + 0.5, () -> {
            releaseMovement();
            state = State.RESTOCK_PLACE;
            stateTicks = 0;
        });
    }

    private void tickRestockPlace() {
        if (actionDelayTicks > 0) return;

        if (isCurrentRestockRequestSatisfied()) {
            finishCurrentRestockPass();
            return;
        }

        if (restockSourceHotbarSlot == -1) {
            ShulkerCandidate candidate = findBestInventoryShulker();
            if (candidate == null) {
                handleRestockFailure(RestockFailureReason.NO_MATCHING_SHULKER, "No matching inventory shulker was found for the current restock need.");
                return;
            }

            restockSourceInventorySlot = candidate.slot();
            restockSourceHotbarSlot = moveInventorySlotToHotbar(restockSourceInventorySlot);
            if (restockSourceHotbarSlot == -1) {
                handleRestockFailure(RestockFailureReason.PLACE_FAILED, "Unable to move the restock shulker into a safe hotbar slot.");
                return;
            }

            currentSourceFailureTicks = 0;
            if (!SlotUtils.isHotbar(restockSourceInventorySlot)) {
                actionDelayTicks = inventoryDelay.get();
                return;
            }
        }

        if (!isPreparedRestockHotbarShulkerUsable()) {
            currentSourceFailureTicks++;
            if (currentSourceFailureTicks <= 20) {
                actionDelayTicks = Math.max(1, inventoryDelay.get());
                return;
            }

            invalidRestockSourceAttempts++;
            restockSourceInventorySlot = -1;
            restockSourceHotbarSlot = -1;
            currentSourceFailureTicks = 0;

            if (invalidRestockSourceAttempts > RESTOCK_INVALID_SOURCE_RETRY_LIMIT) {
                handleRestockFailure(RestockFailureReason.PLACE_FAILED, "Restock shulker hotbar preparation did not stabilize.");
                return;
            }

            actionDelayTicks = Math.max(1, inventoryDelay.get());
            return;
        }

        BlockPos placePos = restockPad.containerBase.up();
        expectedShulkerCountAfterPickup = countInventoryShulkerBoxes();
        boolean placed = BlockUtils.place(placePos, Hand.MAIN_HAND, restockSourceHotbarSlot, false, 0, true, true, true);
        if (!placed) {
            expectedShulkerCountAfterPickup = -1;
            handleRestockFailure(RestockFailureReason.PLACE_FAILED, "Failed to place the restock shulker.");
            return;
        }

        placedShulkerPos = placePos.toImmutable();
        actionDelayTicks = inventoryDelay.get();
        currentSourceFailureTicks = 0;
        restockOpenedSourceValidated = false;
        openedInvalidRestockSource = false;
        state = State.RESTOCK_OPEN;
    }

    private void tickRestockOpen() {
        if (placedShulkerPos == null) {
            handleRestockFailure(RestockFailureReason.OPEN_FAILED, "Placed shulker position was lost before opening.");
            return;
        }

        currentSourceFailureTicks++;
        if (currentSourceFailureTicks == 1) {
            mc.interactionManager.interactBlock(
                mc.player,
                Hand.MAIN_HAND,
                new net.minecraft.util.hit.BlockHitResult(Vec3d.ofCenter(placedShulkerPos), net.minecraft.util.math.Direction.UP, placedShulkerPos, false)
            );
            return;
        }

        if (mc.currentScreen != null && mc.player.currentScreenHandler != null && mc.player.currentScreenHandler.slots.size() > PLAYER_INVENTORY_SLOTS) {
            currentSourceFailureTicks = 0;
            actionDelayTicks = inventoryDelay.get();
            state = State.RESTOCK_LOOT;
            return;
        }

        if (currentSourceFailureTicks > 40) {
            handleRestockFailure(RestockFailureReason.OPEN_FAILED, "The restock shulker did not open.");
        }
    }

    private void tickRestockLoot() {
        if (mc.currentScreen == null || mc.player.currentScreenHandler == null) {
            state = State.RESTOCK_CLOSE;
            return;
        }

        if (actionDelayTicks > 0) return;

        boolean wantFood = shouldRestockFoodThisPass();
        boolean wantElytra = shouldRestockElytraThisPass();

        if (!restockOpenedSourceValidated) {
            restockOpenedSourceValidated = true;
            if (activeRestockKind == RestockKind.ELYTRA && findBestFlightReadyElytraContainerSlot() == -1) {
                openedInvalidRestockSource = true;
                invalidRestockSourceAttempts++;
                state = State.RESTOCK_CLOSE;
                currentSourceFailureTicks = 0;
                return;
            }
        }

        if (wantElytra) {
            int containerSlot = findBestFlightReadyElytraContainerSlot();
            int brokenInventorySlot = findReplaceableLooseElytraSlot();
            if (containerSlot != -1 && brokenInventorySlot != -1) {
                swapContainerElytraWithBrokenInventorySlot(containerSlot, brokenInventorySlot);
                actionDelayTicks = inventoryDelay.get();
                return;
            }
        }

        if (wantElytra && canPullMoreLooseElytras()) {
            int containerSlot = findBestFlightReadyElytraContainerSlot();
            if (containerSlot != -1) {
                InvUtils.shiftClick().slotId(containerSlot);
                actionDelayTicks = inventoryDelay.get();
                return;
            }
        }

        if (wantFood && shouldPullMoreConfiguredFood()) {
            int containerSlot = findBestFoodContainerSlot();
            if (containerSlot != -1) {
                InvUtils.shiftClick().slotId(containerSlot);
                actionDelayTicks = inventoryDelay.get();
                return;
            }
        }

        currentSourceFailureTicks++;
        if (currentSourceFailureTicks > 20) {
            state = State.RESTOCK_CLOSE;
            currentSourceFailureTicks = 0;
        }
    }

    private void tickRestockClose() {
        if (mc.player.currentScreenHandler != null && !mc.player.currentScreenHandler.getCursorStack().isEmpty()) {
            clearCursorAfterSwap();
            actionDelayTicks = inventoryDelay.get();
            return;
        }

        if (mc.currentScreen != null) {
            mc.currentScreen.close();
            actionDelayTicks = inventoryDelay.get();
            return;
        }

        state = State.RESTOCK_BREAK;
        stateTicks = 0;
    }

    private void tickRestockBreak() {
        if (placedShulkerPos == null) {
            state = State.RESTOCK_PICKUP;
            pickupTicks = 0;
            return;
        }

        if (mc.world.getBlockState(placedShulkerPos).getBlock() instanceof ShulkerBoxBlock) {
            BlockUtils.breakBlock(placedShulkerPos, true);
            return;
        }

        pickupTicks = 0;
        pickupTarget = null;
        state = State.RESTOCK_PICKUP;
    }

    private void tickRestockPickup() {
        if (isPlacedShulkerPickedUp()) {
            finishVerifiedShulkerPickup();
            return;
        }

        List<ItemEntity> drops = collectShulkerDrops();
        if (!drops.isEmpty()) {
            pickupTicks++;

            if (countEmptyLooseSlots() <= 0 && pickupTicks % SHULKER_PICKUP_REACQUIRE_INTERVAL == 0) {
                dropReplaceableLooseElytraForPickup();
            }

            if (pickupTarget == null || !pickupTarget.isAlive() || pickupTicks % SHULKER_PICKUP_REACQUIRE_INTERVAL == 0) {
                pickupTarget = drops.stream()
                    .min(Comparator.comparingDouble(entity -> mc.player.squaredDistanceTo(entity.getX(), entity.getY(), entity.getZ())))
                    .orElse(null);
            }

            if (pickupTarget != null) {
                moveToward(pickupTarget.getX(), pickupTarget.getZ(), this::releaseMovement);
            } else {
                releaseMovement();
            }

            if (pickupTicks > SHULKER_PICKUP_TIMEOUT) {
                handleRestockFailure(RestockFailureReason.PICKUP_FAILED, "Restock shulker was not picked up in time.");
            }
            return;
        }

        pickupTicks++;
        pickupTarget = null;
        releaseMovement();

        if (pickupTicks > SHULKER_PICKUP_TIMEOUT) {
            handleRestockFailure(RestockFailureReason.PICKUP_FAILED, "Restock shulker pickup could not be verified in time.");
        }
    }

    private boolean isPlacedShulkerPickedUp() {
        return expectedShulkerCountAfterPickup >= 0 && countInventoryShulkerBoxes() >= expectedShulkerCountAfterPickup;
    }

    private void finishVerifiedShulkerPickup() {
        RestockFailureReason deferredReason = deferredRestockFailureReason;
        String deferredMessage = deferredRestockFailureMessage;

        releaseMovement();
        placedShulkerPos = null;
        restockPad = null;
        resetRestockPadSearch();
        pickupTicks = 0;
        pickupTarget = null;
        expectedShulkerCountAfterPickup = -1;
        clearDeferredRestockFailure();

        if (openedInvalidRestockSource) {
            openedInvalidRestockSource = false;
            restockOpenedSourceValidated = false;
            restockSourceInventorySlot = -1;
            restockSourceHotbarSlot = -1;

            if (invalidRestockSourceAttempts > RESTOCK_INVALID_SOURCE_RETRY_LIMIT) {
                handleRestockFailure(RestockFailureReason.NO_MATCHING_SHULKER, "Too many invalid elytra shulker sources were opened.");
                return;
            }

            if (hasFlightReadyElytraShulkerInInventory()) {
                startRestockInterrupt(RestockKind.ELYTRA, resumeStateAfterRestock);
                return;
            }

            if (countTotalFlightReadyElytras() > 0) {
                resumeAfterPartialElytraRestock("No more valid elytra shulkers were found after cleaning up an invalid source.");
                return;
            }

            handleRestockFailure(RestockFailureReason.NO_FLIGHT_READY_ELYTRA, "No flight-ready elytras were found in remaining shulkers.");
            return;
        }

        if (deferredReason != null) {
            applyRestockFailureOutcome(deferredReason, deferredMessage);
            return;
        }

        if (requiresFlightReadyStateAfterRestock() && !repairWornElytraIfNeeded()) {
            handleRestockFailure(RestockFailureReason.NO_FLIGHT_READY_ELYTRA, "No flight-ready elytra remained after restock.");
            return;
        }

        finishCurrentRestockPass();
    }

    private void finishCurrentRestockPass() {
        RestockKind completed = activeRestockKind;
        activeRestockKind = RestockKind.NONE;

        if (completed == RestockKind.FOOD && !needsFoodRestock()) queuedFoodRestock = false;
        if (completed == RestockKind.ELYTRA && hasSatisfiedLooseElytraTargetAfterRepair()) queuedElytraRestock = false;

        if (shouldContinueElytraRestockAfterPass(completed)) {
            if (!restockElytras.get()) {
                state = State.OUT_OF_ELYTRAS_WAIT;
            } else {
                startRestockInterrupt(RestockKind.ELYTRA, resumeStateAfterRestock);
            }
            return;
        }

        if (shouldAcceptPartialElytraRestock(completed)) {
            resumeAfterPartialElytraRestock("Elytra restock target was only partially met because no more valid elytra shulkers were found.");
            return;
        }

        if (queuedFoodRestock && !foodRestockSuppressed && needsFoodRestock()) {
            startRestockInterrupt(RestockKind.FOOD, resumeStateAfterRestock);
            return;
        }

        switch (resumeStateAfterRestock) {
            case ARRIVED_WAIT -> {
                state = State.ARRIVED_WAIT;
                return;
            }
            case OUT_OF_ELYTRAS_WAIT -> {
                state = State.OUT_OF_ELYTRAS_WAIT;
                return;
            }
            case ROUTE_FLIGHT -> {
                if (isInsideArrivalRadius()) {
                    state = State.ARRIVED_WAIT;
                    return;
                }

                if (countTotalFlightReadyElytras() <= 0) {
                    state = State.OUT_OF_ELYTRAS_WAIT;
                    return;
                }

                if (needsFoodRestock() && !foodRestockSuppressed) {
                    startRestockInterrupt(RestockKind.FOOD, ResumeState.ROUTE_FLIGHT);
                    return;
                }

                if (shouldContinueElytraRestockAfterPass(completed) && !isInsideArrivalRadius()) {
                    if (!restockElytras.get()) {
                        state = State.OUT_OF_ELYTRAS_WAIT;
                        return;
                    }
                    startRestockInterrupt(RestockKind.ELYTRA, ResumeState.ROUTE_FLIGHT);
                    return;
                }

                state = State.TAKEOFF_AWAIT_GROUND;
                stateTicks = 0;
            }
        }
    }

    private void tickArrivedWait() {
        releaseMovement();
        restoreRotationSnapshot();

        if (needsFoodRestock() && !foodRestockSuppressed) {
            startRestockInterrupt(RestockKind.FOOD, ResumeState.ARRIVED_WAIT);
            return;
        }

        if (!periodicHomeSave.get()) {
            info("Arrived at destination.");
            toggle();
            return;
        }

        if (!isHomeSaveDue()) return;

        if (!sendHomeSave()) return;
        info("Arrived at destination and saved home.");
        toggle();
    }

    private void tickOutOfElytrasWait() {
        releaseMovement();
        restoreRotationSnapshot();

        if (needsFoodRestock() && !foodRestockSuppressed) {
            startRestockInterrupt(RestockKind.FOOD, ResumeState.OUT_OF_ELYTRAS_WAIT);
            return;
        }

        if (periodicHomeSave.get() && !isHomeSaveDue()) return;

        if (periodicHomeSave.get() && !sendHomeSave()) return;

        String reason = "[ElytraRoute] Out of flight-ready elytras.";
        warning("Out of elytras. Disconnecting.");
        toggle();
        if (mc.getNetworkHandler() != null) {
            mc.getNetworkHandler().getConnection().disconnect(Text.literal(reason));
        }
    }

    private boolean shouldPauseForEating() {
        if (isActivelyEating()) return true;
        if (isFoodRestockFlowActive()) return false;
        return doesAutoEatWantToEat() || doesAutoGapWantToEat();
    }

    private boolean isActivelyEating() {
        return isAutoEatEating()
            || isAutoGapEating()
            || shouldPauseForOffhandEating()
            || shouldPauseForActiveFoodUse();
    }

    private boolean isAutoEatEating() {
        AutoEat autoEat = Modules.get().get(AutoEat.class);
        if (autoEat == null || !autoEat.isActive() || mc.player == null) return false;
        return autoEat.eating;
    }

    private boolean doesAutoEatWantToEat() {
        AutoEat autoEat = Modules.get().get(AutoEat.class);
        if (autoEat == null || !autoEat.isActive() || mc.player == null) return false;
        return autoEat.shouldEat();
    }

    private boolean isAutoGapEating() {
        AutoGap autoGap = Modules.get().get(AutoGap.class);
        if (autoGap == null || !autoGap.isActive() || mc.player == null) return false;
        if (autoGap.isEating()) return true;
        return mc.player.isUsingItem() && isUsingGapLikeFood(mc.player.getActiveItem());
    }

    @SuppressWarnings("unchecked")
    private boolean doesAutoGapWantToEat() {
        AutoGap autoGap = Modules.get().get(AutoGap.class);
        if (autoGap == null || !autoGap.isActive() || mc.player == null) return false;

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

    private boolean isFoodRestockFlowActive() {
        return needsFoodRestock()
            || shouldRestockFoodThisPass()
            || (state == State.LANDING && landingReason == LandingReason.RESTOCK && shouldRestockFoodThisPass());
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

    private boolean shouldPauseForOffhandEating() {
        OffhandManager offhandManager = Modules.get().get(OffhandManager.class);
        return offhandManager != null && offhandManager.isEating();
    }

    private boolean shouldPauseForActiveFoodUse() {
        boolean inventoryEating = InventoryManager.getInstance().isEating();
        boolean playerUsingFood = mc.player.isUsingItem() && mc.player.getActiveItem().contains(DataComponentTypes.FOOD);
        return inventoryEating || playerUsingFood;
    }

    private void applyFlightAltitudeControl(boolean landing) {
        if (landing) {
            mc.options.jumpKey.setPressed(false);
            mc.options.sneakKey.setPressed(mc.player.isGliding());
            return;
        }

        double playerY = mc.player.getY();
        boolean tooLow = playerY < flyYLevel.get() - 1;
        boolean tooHigh = playerY > flyYLevel.get() + 1;

        mc.options.jumpKey.setPressed(tooLow);
        mc.options.sneakKey.setPressed(tooHigh);
    }

    private boolean needsFoodRestock() {
        return foodRestock.get()
            && hasConfiguredFoodTypes()
            && !foodRestockSuppressed
            && countConfiguredFoodItemsInInventory() <= saveFood.get();
    }

    private boolean needsElytraRestock() {
        if (!restockElytras.get()) return countTotalFlightReadyElytras() <= 0;
        if (state == State.ARRIVED_WAIT || isInsideArrivalRadius()) return false;
        return countTotalFlightReadyElytras() <= 0;
    }

    private boolean handleGroundedRouteInterruptBeforeTakeoff() {
        if (isInsideArrivalRadius()) {
            state = State.ARRIVED_WAIT;
            stateTicks = 0;
            return true;
        }

        if (needsFoodRestock()) {
            startRestockInterrupt(RestockKind.FOOD, ResumeState.ROUTE_FLIGHT);
            return true;
        }

        if (countTotalFlightReadyElytras() <= 0) {
            if (restockElytras.get()) {
                startRestockInterrupt(RestockKind.ELYTRA, ResumeState.ROUTE_FLIGHT);
            } else {
                state = State.OUT_OF_ELYTRAS_WAIT;
                stateTicks = 0;
            }
            return true;
        }

        return false;
    }

    private boolean shouldRestockFoodThisPass() {
        return activeRestockKind == RestockKind.FOOD || queuedFoodRestock;
    }

    private boolean shouldRestockElytraThisPass() {
        return activeRestockKind == RestockKind.ELYTRA || queuedElytraRestock;
    }

    private boolean isCurrentRestockRequestSatisfied() {
        return switch (activeRestockKind) {
            case ELYTRA -> hasSatisfiedLooseElytraTargetAfterRepair();
            case FOOD -> !needsFoodRestock();
            case NONE -> !queuedFoodRestock && !queuedElytraRestock;
        };
    }

    private boolean isElytraRestockFailure(RestockFailureReason reason) {
        if (!shouldRestockElytraThisPass()) return false;
        return reason == RestockFailureReason.NO_MATCHING_SHULKER
            || reason == RestockFailureReason.PLACE_FAILED
            || reason == RestockFailureReason.OPEN_FAILED
            || reason == RestockFailureReason.LOOT_TIMEOUT
            || reason == RestockFailureReason.TARGET_UNMET
            || reason == RestockFailureReason.NO_FLIGHT_READY_ELYTRA;
    }

    private void handleRestockFailure(RestockFailureReason reason, String message) {
        releaseMovement();
        restoreRotationSnapshot();
        if (mc.currentScreen != null) mc.currentScreen.close();

        if (reason != RestockFailureReason.PICKUP_FAILED && placedShulkerPos != null) {
            deferredRestockFailureReason = reason;
            deferredRestockFailureMessage = message;
            state = State.RESTOCK_BREAK;
            stateTicks = 0;
            currentSourceFailureTicks = 0;
            return;
        }

        applyRestockFailureOutcome(reason, message);
    }

    private void applyRestockFailureOutcome(RestockFailureReason reason, String message) {
        if (reason == RestockFailureReason.PICKUP_FAILED) {
            error(message);
            toggle();
            return;
        }

        boolean noFlightReady = countTotalFlightReadyElytras() <= 0 && requiresFlightReadyStateAfterRestock();
        boolean foodOnlyFailure = !noFlightReady && shouldRestockFoodThisPass() && !shouldRestockElytraThisPass();

        if (isElytraRestockFailure(reason) && restockElytras.get()) {
            if (noFlightReady && hasFlightReadyElytraShulkerInInventory() && reason != RestockFailureReason.PLACE_FAILED) {
                warning(message);
                startRestockInterrupt(RestockKind.ELYTRA, resumeStateAfterRestock);
                return;
            }

            if (countTotalFlightReadyElytras() > 0 && !hasFlightReadyElytraShulkerInInventory()) {
                resumeAfterPartialElytraRestock(message);
                return;
            }
        }

        if (noFlightReady) {
            warning(message);
            state = State.OUT_OF_ELYTRAS_WAIT;
            activeRestockKind = RestockKind.NONE;
            queuedElytraRestock = false;
            return;
        }

        if (foodOnlyFailure) {
            foodRestockSuppressed = true;
            if (resumeStateAfterRestock == ResumeState.ROUTE_FLIGHT) {
                warning(message);
                toggle();
            } else {
                warning(message);
                state = resumeStateAfterRestock == ResumeState.ARRIVED_WAIT ? State.ARRIVED_WAIT : State.OUT_OF_ELYTRAS_WAIT;
            }
            activeRestockKind = RestockKind.NONE;
            queuedFoodRestock = false;
            return;
        }

        error(message);
        toggle();
    }

    private void clearDeferredRestockFailure() {
        deferredRestockFailureReason = null;
        deferredRestockFailureMessage = null;
    }

    private boolean sendHomeSave() {
        if (!periodicHomeSave.get() || homeName.get().isBlank() || mc.player == null || mc.currentScreen != null || shouldPauseForEating()) return false;
        ChatUtils.sendPlayerMsg("/sethome " + homeName.get());
        homeSaveNextAtMs = System.currentTimeMillis() + (homeSaveIntervalMinutes.get() * 60_000L);
        return true;
    }

    private boolean isHomeSaveDue() {
        return periodicHomeSave.get() && System.currentTimeMillis() >= homeSaveNextAtMs;
    }

    private boolean isInsideArrivalRadius() {
        return getRemainingDistance() <= arrivalRadius.get();
    }

    private float computeBearingYaw() {
        double dx = destinationX.get() - mc.player.getX();
        double dz = destinationZ.get() - mc.player.getZ();
        return normalizeYaw((float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0f);
    }

    private float normalizeYaw(float yaw) {
        return MathHelper.wrapDegrees(yaw);
    }

    private void releaseMovement() {
        mc.options.forwardKey.setPressed(false);
        mc.options.backKey.setPressed(false);
        mc.options.leftKey.setPressed(false);
        mc.options.rightKey.setPressed(false);
        mc.options.jumpKey.setPressed(false);
        mc.options.sneakKey.setPressed(false);
    }

    private void moveToward(double targetX, double targetZ, Runnable onArrival) {
        double dx = targetX - mc.player.getX();
        double dz = targetZ - mc.player.getZ();
        double distSq = dx * dx + dz * dz;

        if (distSq < 0.04) {
            mc.options.forwardKey.setPressed(false);
            onArrival.run();
            return;
        }

        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        mc.player.setYaw(yaw);
        mc.options.forwardKey.setPressed(true);
    }

    private int countConfiguredFoodItemsInInventory() {
        int count = 0;
        for (int i = 0; i < PLAYER_INVENTORY_SLOTS; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (isConfiguredFoodStack(stack)) count += stack.getCount();
        }
        return count;
    }

    private boolean hasConfiguredFoodTypes() {
        return !foodTypes.get().isEmpty();
    }

    private boolean isConfiguredFoodStack(ItemStack stack) {
        return stack != null && !stack.isEmpty() && foodTypes.get().contains(stack.getItem());
    }

    private void handleFoodTypesChanged(List<Item> selected) {
        if (clampingFoodTypes || selected == null || selected.size() <= 1) return;

        clampingFoodTypes = true;
        try {
            foodTypes.set(new ArrayList<>(selected.subList(0, 1)));
        } finally {
            clampingFoodTypes = false;
        }

        warning("Maximum 1 food type.");
    }

    private int countConfiguredFoodSlots() {
        int count = 0;
        for (int i = 0; i < PLAYER_INVENTORY_SLOTS; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (isConfiguredFoodStack(stack)) count++;
        }
        return count;
    }

    private int countEmptyLooseSlots() {
        int count = 0;
        for (int i = 0; i < PLAYER_INVENTORY_SLOTS; i++) {
            if (mc.player.getInventory().getStack(i).isEmpty()) count++;
        }
        return count;
    }

    private int countUsableEmptyLooseSlotsAfterMinimumReserve() {
        return Math.max(0, countEmptyLooseSlots() - minimumEmptySlots.get() - pendingShulkerPickupSlotReserve());
    }

    private int pendingShulkerPickupSlotReserve() {
        return expectedShulkerCountAfterPickup >= 0 ? 1 : 0;
    }

    private int countHudUsableElytras() {
        int count = 0;
        ItemStack chest = mc.player.getEquippedStack(EquipmentSlot.CHEST);
        if (isHudUsableElytra(chest)) count++;

        for (int i = 0; i < PLAYER_INVENTORY_SLOTS; i++) {
            if (isHudUsableElytra(mc.player.getInventory().getStack(i))) count++;
        }

        return count;
    }

    private int countTotalFlightReadyElytras() {
        int count = 0;
        ItemStack chest = mc.player.getEquippedStack(EquipmentSlot.CHEST);
        if (isFlightReadyElytra(chest)) count++;

        for (int i = 0; i < PLAYER_INVENTORY_SLOTS; i++) {
            if (isFlightReadyElytra(mc.player.getInventory().getStack(i))) count++;
        }

        return count;
    }

    private int countLooseFlightReadyElytras() {
        int count = 0;
        for (int i = 0; i < PLAYER_INVENTORY_SLOTS; i++) {
            if (isFlightReadyElytra(mc.player.getInventory().getStack(i))) count++;
        }
        return count;
    }

    private int countLooseReplaceableElytras() {
        int count = 0;
        for (int i = 0; i < PLAYER_INVENTORY_SLOTS; i++) {
            if (isReplaceableElytra(mc.player.getInventory().getStack(i))) count++;
        }
        return count;
    }

    private boolean isHudUsableElytra(ItemStack stack) {
        return stack != null && stack.isOf(Items.ELYTRA) && THMUtils.getDamage(stack) > 1.0;
    }

    private boolean isFlightReadyElytra(ItemStack stack) {
        if (stack == null || !stack.isOf(Items.ELYTRA)) return false;

        ElytraFly elytraFly = Modules.get().get(ElytraFly.class);
        if (elytraFly == null) return false;

        int remaining = stack.getMaxDamage() - stack.getDamage();
        return remaining > elytraFly.replaceDurability.get();
    }

    private boolean isReplaceableElytra(ItemStack stack) {
        return stack != null && stack.isOf(Items.ELYTRA) && !isFlightReadyElytra(stack);
    }

    private int getConfiguredFoodMaxStackSize() {
        if (!hasConfiguredFoodTypes()) return 0;
        Item foodItem = foodTypes.get().get(0);
        return foodItem != null ? foodItem.getMaxCount() : 0;
    }

    private int countConfiguredFoodMergeCapacityInInventory() {
        int capacity = 0;
        for (int i = 0; i < PLAYER_INVENTORY_SLOTS; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!isConfiguredFoodStack(stack)) continue;

            capacity += Math.max(stack.getMaxCount() - stack.getCount(), 0);
        }

        return capacity;
    }

    private int countConfiguredFoodAdditionalCapacityInInventory() {
        int maxStackSize = getConfiguredFoodMaxStackSize();
        if (maxStackSize <= 0) return countConfiguredFoodMergeCapacityInInventory();

        int availableFoodSlots = Math.min(Math.max(2 - countConfiguredFoodSlots(), 0), countUsableEmptyLooseSlotsAfterMinimumReserve());
        return countConfiguredFoodMergeCapacityInInventory() + (availableFoodSlots * maxStackSize);
    }

    private int getConfiguredFoodTargetIncrease(int currentLooseFoodCount) {
        return Math.max((saveFood.get() + 1) - currentLooseFoodCount, 0);
    }

    private boolean hasUsableFoodRestockTarget() {
        if (!shouldRestockFoodThisPass()) return true;

        int currentLooseFood = countConfiguredFoodItemsInInventory();
        int targetIncrease = getConfiguredFoodTargetIncrease(currentLooseFood);
        if (targetIncrease <= 0) return true;

        return countConfiguredFoodAdditionalCapacityInInventory() >= targetIncrease;
    }

    private boolean shouldPullMoreConfiguredFood() {
        if (!shouldRestockFoodThisPass()) return false;
        return countConfiguredFoodItemsInInventory() < (saveFood.get() + 1) && countConfiguredFoodAdditionalCapacityInInventory() > 0;
    }

    private long getTotalFlightTimeSeconds() {
        long totalSeconds = 0;

        ItemStack chest = mc.player.getEquippedStack(EquipmentSlot.CHEST);
        if (isHudUsableElytra(chest)) totalSeconds += flightTimeSecondsForStack(chest);

        for (int i = 0; i < PLAYER_INVENTORY_SLOTS; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (isHudUsableElytra(stack)) totalSeconds += flightTimeSecondsForStack(stack);
        }

        return totalSeconds;
    }

    private long flightTimeSecondsForStack(ItemStack stack) {
        return Math.max(stack.getMaxDamage() - stack.getDamage(), 0);
    }

    private void resetRestockPadSearch() {
        restockPadSearchAnchor = null;
        restockPadSearchRadius = 0;
        restockPadSearchX = 0;
        restockPadSearchZ = 0;
    }

    private RestockPad findNextRestockPad() {
        if (restockPadSearchAnchor == null) {
            restockPadSearchAnchor = mc.player.getBlockPos().down().toImmutable();
            restockPadSearchRadius = 0;
            restockPadSearchX = restockPadSearchAnchor.getX();
            restockPadSearchZ = restockPadSearchAnchor.getZ();
        }

        int checks = 0;
        int y = restockPadSearchAnchor.getY();
        while (checks++ < PAD_SEARCH_CHECKS_PER_TICK) {
            BlockPos base = new BlockPos(restockPadSearchX, y, restockPadSearchZ);

            RestockPad xPad = toPadIfValid(base, base.east());
            if (xPad != null) return xPad;

            RestockPad zPad = toPadIfValid(base, base.south());
            if (zPad != null) return zPad;

            advanceRestockPadSearchCursor();
        }

        return null;
    }

    private void advanceRestockPadSearchCursor() {
        int anchorX = restockPadSearchAnchor.getX();
        int anchorZ = restockPadSearchAnchor.getZ();
        int minX = anchorX - restockPadSearchRadius;
        int maxX = anchorX + restockPadSearchRadius;
        int minZ = anchorZ - restockPadSearchRadius;
        int maxZ = anchorZ + restockPadSearchRadius;

        do {
            if (restockPadSearchX < maxX) {
                restockPadSearchX++;
            } else {
                restockPadSearchX = minX;
                restockPadSearchZ++;
            }

            if (restockPadSearchZ > maxZ) {
                restockPadSearchRadius++;
                restockPadSearchX = anchorX - restockPadSearchRadius;
                restockPadSearchZ = anchorZ - restockPadSearchRadius;
                return;
            }
        } while (restockPadSearchRadius != 0
            && Math.abs(restockPadSearchX - anchorX) != restockPadSearchRadius
            && Math.abs(restockPadSearchZ - anchorZ) != restockPadSearchRadius);
    }

    private RestockPad toPadIfValid(BlockPos a, BlockPos b) {
        if (!isValidPadBase(a) || !isValidPadBase(b)) return null;

        double aDist = squaredHorizontalDistanceTo(a);
        double bDist = squaredHorizontalDistanceTo(b);
        return aDist <= bDist ? new RestockPad(a, b) : new RestockPad(b, a);
    }

    private boolean isValidPadBase(BlockPos pos) {
        return mc.world.getBlockState(pos).isSolidBlock(mc.world, pos)
            && mc.world.getBlockState(pos.up()).isAir();
    }

    private double squaredHorizontalDistanceTo(BlockPos pos) {
        double dx = (pos.getX() + 0.5) - mc.player.getX();
        double dz = (pos.getZ() + 0.5) - mc.player.getZ();
        return dx * dx + dz * dz;
    }

    private ShulkerCandidate findBestInventoryShulker() {
        boolean needFood = shouldRestockFoodThisPass() && needsFoodRestock();
        boolean needElytra = needsElytraSourceThisPass();
        RestockKind primary = getPrimaryRestockNeed(needFood, needElytra);
        if (primary == RestockKind.NONE) return null;

        ShulkerCandidate best = null;
        int bestScore = Integer.MIN_VALUE;

        for (int i = 0; i < PLAYER_INVENTORY_SLOTS; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!isShulkerBox(stack)) continue;

            int food = countConfiguredFoodInShulker(stack);
            int elytras = countFlightReadyElytrasInShulker(stack);
            if (primary == RestockKind.ELYTRA && elytras <= 0) continue;
            if (primary == RestockKind.FOOD && food <= 0) continue;

            int tier;
            if (needFood && needElytra && food > 0 && elytras > 0) tier = 3;
            else if (primary == RestockKind.FOOD && food > 0) tier = 2;
            else if (primary == RestockKind.ELYTRA && elytras > 0) tier = 2;
            else tier = 1;

            int score = (tier * 100_000) + (food * 100) + elytras;
            if (score > bestScore) {
                bestScore = score;
                best = new ShulkerCandidate(i, food, elytras);
            }
        }

        return best;
    }

    private boolean needsElytraSourceThisPass() {
        return shouldRestockElytraThisPass()
            && (needsElytraRestock() || canPullMoreLooseElytras() || hasUnusableWornElytra());
    }

    private RestockKind getPrimaryRestockNeed(boolean needFood, boolean needElytra) {
        if (activeRestockKind == RestockKind.ELYTRA && needElytra) return RestockKind.ELYTRA;
        if (activeRestockKind == RestockKind.FOOD && needFood) return RestockKind.FOOD;
        if (needElytra) return RestockKind.ELYTRA;
        if (needFood) return RestockKind.FOOD;
        return RestockKind.NONE;
    }

    private boolean isPreparedRestockHotbarShulkerUsable() {
        if (!SlotUtils.isHotbar(restockSourceHotbarSlot)) return false;
        return isValidRestockSourceForCurrentNeed(mc.player.getInventory().getStack(restockSourceHotbarSlot));
    }

    private boolean isValidRestockSourceForCurrentNeed(ItemStack stack) {
        if (!isShulkerBox(stack)) return false;

        boolean needFood = shouldRestockFoodThisPass() && needsFoodRestock();
        boolean needElytra = needsElytraSourceThisPass();
        RestockKind primary = getPrimaryRestockNeed(needFood, needElytra);

        if (primary == RestockKind.ELYTRA) return countFlightReadyElytrasInShulker(stack) > 0;
        if (primary == RestockKind.FOOD) return countConfiguredFoodInShulker(stack) > 0;
        return false;
    }

    private boolean hasFlightReadyElytraShulkerInInventory() {
        for (int i = 0; i < PLAYER_INVENTORY_SLOTS; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (isShulkerBox(stack) && countFlightReadyElytrasInShulker(stack) > 0) return true;
        }

        return false;
    }

    private boolean isShulkerBox(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof BlockItem bi && bi.getBlock() instanceof ShulkerBoxBlock;
    }

    private int countInventoryShulkerBoxes() {
        int count = 0;
        for (int i = 0; i < PLAYER_INVENTORY_SLOTS; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (isShulkerBox(stack)) count += Math.max(1, stack.getCount());
        }
        return count;
    }

    private int countConfiguredFoodInShulker(ItemStack shulker) {
        if (!isShulkerBox(shulker) || !hasConfiguredFoodTypes()) return 0;
        ContainerComponent container = shulker.get(DataComponentTypes.CONTAINER);
        if (container == null) return 0;

        int count = 0;
        for (ItemStack stack : container.iterateNonEmpty()) {
            if (isConfiguredFoodStack(stack)) count += stack.getCount();
        }
        return count;
    }

    private int countFlightReadyElytrasInShulker(ItemStack shulker) {
        if (!isShulkerBox(shulker)) return 0;
        ContainerComponent container = shulker.get(DataComponentTypes.CONTAINER);
        if (container == null) return 0;

        int count = 0;
        for (ItemStack stack : container.iterateNonEmpty()) {
            if (isFlightReadyElytra(stack)) count += Math.max(1, stack.getCount());
        }
        return count;
    }

    private int moveInventorySlotToHotbar(int inventorySlot) {
        if (SlotUtils.isHotbar(inventorySlot)) return inventorySlot;

        int hotbarSlot = findHotbarMoveTarget();
        if (hotbarSlot == -1) return -1;

        InvUtils.move().from(inventorySlot).toHotbar(hotbarSlot);
        return hotbarSlot;
    }

    private int findHotbarMoveTarget() {
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).isEmpty()) return i;
        }

        for (int i = 0; i < 9; i++) {
            if (!isProtectedRestockHotbarStack(mc.player.getInventory().getStack(i))) return i;
        }

        if (countEmptyLooseSlots() > 0) {
            for (int i = 0; i < 9; i++) {
                ItemStack stack = mc.player.getInventory().getStack(i);
                if (!isShulkerBox(stack) || isValidRestockSourceForCurrentNeed(stack)) continue;

                shiftClickHotbarItemOut(i);
                return i;
            }
        }

        return -1;
    }

    private boolean isProtectedRestockHotbarStack(ItemStack stack) {
        return isConfiguredFoodStack(stack) || isFlightReadyElytra(stack) || isShulkerBox(stack);
    }

    private boolean equipFlightReadyElytraForTakeoff() {
        if (isFlightReadyElytra(mc.player.getEquippedStack(EquipmentSlot.CHEST))) return true;

        int sourceSlot = findBestFlightReadyLooseElytraSlot();
        if (sourceSlot == -1) return false;

        int hotbarSlot = SlotUtils.isHotbar(sourceSlot) ? sourceSlot : prepareHotbarSlotForTakeoffElytra();
        if (hotbarSlot == -1) return false;

        if (!SlotUtils.isHotbar(sourceSlot)) {
            InvUtils.move().from(sourceSlot).toHotbar(hotbarSlot);
            actionDelayTicks = Math.max(actionDelayTicks, inventoryDelay.get());
        }

        if (rightClickEquipHotbarElytra(hotbarSlot)) return true;

        // Right-click cannot equip over an occupied chest slot, so fall back to the cursor-clear swap path.
        return repairWornElytraIfNeeded();
    }

    private boolean hasElytraEquipped() {
        return mc.player.getEquippedStack(EquipmentSlot.CHEST).isOf(Items.ELYTRA);
    }

    private int findBestFlightReadyLooseElytraSlot() {
        int bestSlot = -1;
        int bestRemaining = -1;
        for (int i = 0; i < PLAYER_INVENTORY_SLOTS; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!isFlightReadyElytra(stack)) continue;

            int remaining = stack.getMaxDamage() - stack.getDamage();
            if (remaining > bestRemaining) {
                bestRemaining = remaining;
                bestSlot = i;
            }
        }
        return bestSlot;
    }

    private int prepareHotbarSlotForTakeoffElytra() {
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).isEmpty()) return i;
        }

        int target = findTakeoffHotbarEvictionTarget(false);
        if (target == -1) target = findTakeoffHotbarEvictionTarget(true);
        if (target == -1) return -1;

        if (!isProtectedTakeoffHotbarStack(mc.player.getInventory().getStack(target))) {
            shiftClickHotbarItemOut(target);
        }

        return target;
    }

    private int findTakeoffHotbarEvictionTarget(boolean allowProtected) {
        int selected = mc.player.getInventory().getSelectedSlot();
        for (int i = 0; i < 9; i++) {
            int slot = (selected + 1 + i) % 9;
            ItemStack stack = mc.player.getInventory().getStack(slot);
            if (stack.isEmpty()) return slot;
            if (!allowProtected && isProtectedTakeoffHotbarStack(stack)) continue;
            return slot;
        }
        return selected;
    }

    private boolean isProtectedTakeoffHotbarStack(ItemStack stack) {
        return isConfiguredFoodStack(stack)
            || stack.contains(DataComponentTypes.FOOD)
            || isWeaponStack(stack)
            || stack.isOf(Items.ELYTRA)
            || stack.contains(DataComponentTypes.EQUIPPABLE)
            || isShulkerBox(stack);
    }

    private boolean isWeaponStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        Item item = stack.getItem();
        return item == Items.WOODEN_SWORD
            || item == Items.STONE_SWORD
            || item == Items.IRON_SWORD
            || item == Items.GOLDEN_SWORD
            || item == Items.DIAMOND_SWORD
            || item == Items.NETHERITE_SWORD
            || item == Items.WOODEN_AXE
            || item == Items.STONE_AXE
            || item == Items.IRON_AXE
            || item == Items.GOLDEN_AXE
            || item == Items.DIAMOND_AXE
            || item == Items.NETHERITE_AXE
            || item == Items.BOW
            || item == Items.CROSSBOW
            || item == Items.TRIDENT
            || item == Items.MACE;
    }

    private void shiftClickHotbarItemOut(int hotbarSlot) {
        if (mc.player.getInventory().getStack(hotbarSlot).isEmpty()) return;
        InvUtils.shiftClick().slot(hotbarSlot);
        actionDelayTicks = Math.max(actionDelayTicks, inventoryDelay.get());
    }

    private boolean rightClickEquipHotbarElytra(int hotbarSlot) {
        if (!SlotUtils.isHotbar(hotbarSlot) || !isFlightReadyElytra(mc.player.getInventory().getStack(hotbarSlot))) return false;

        int previousSlot = mc.player.getInventory().getSelectedSlot();
        if (!InvUtils.swap(hotbarSlot, true)) return false;

        mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
        mc.player.swingHand(Hand.MAIN_HAND);
        actionDelayTicks = Math.max(actionDelayTicks, inventoryDelay.get());
        InvUtils.swapBack();

        if (previousSlot >= 0 && previousSlot <= 8 && mc.player.getInventory().getSelectedSlot() != previousSlot) {
            InvUtils.swap(previousSlot, false);
        }

        return isFlightReadyElytra(mc.player.getEquippedStack(EquipmentSlot.CHEST));
    }

    private int getContainerSlotCount() {
        if (mc.player == null || mc.player.currentScreenHandler == null) return 0;
        return Math.max(0, mc.player.currentScreenHandler.slots.size() - PLAYER_INVENTORY_SLOTS);
    }

    private int findBestFlightReadyElytraContainerSlot() {
        int bestSlot = -1;
        int bestRemaining = -1;
        int containerSlots = getContainerSlotCount();
        for (int i = 0; i < containerSlots; i++) {
            ItemStack stack = mc.player.currentScreenHandler.slots.get(i).getStack();
            if (!isFlightReadyElytra(stack)) continue;

            int remaining = stack.getMaxDamage() - stack.getDamage();
            if (remaining > bestRemaining) {
                bestRemaining = remaining;
                bestSlot = i;
            }
        }
        return bestSlot;
    }

    private int findBestFoodContainerSlot() {
        int bestSlot = -1;
        int bestCount = -1;
        int containerSlots = getContainerSlotCount();
        for (int i = 0; i < containerSlots; i++) {
            ItemStack stack = mc.player.currentScreenHandler.slots.get(i).getStack();
            if (!isConfiguredFoodStack(stack)) continue;

            if (stack.getCount() > bestCount) {
                bestCount = stack.getCount();
                bestSlot = i;
            }
        }
        return bestSlot;
    }

    private int findReplaceableLooseElytraSlot() {
        for (int i = 0; i < PLAYER_INVENTORY_SLOTS; i++) {
            if (isReplaceableElytra(mc.player.getInventory().getStack(i))) return i;
        }
        return -1;
    }

    private boolean dropReplaceableLooseElytraForPickup() {
        int slot = findReplaceableLooseElytraSlot();
        if (slot == -1) return false;

        InvUtils.drop().slot(slot);
        return true;
    }

    private int getRequiredLooseFlightReadyBeforeRepair() {
        int targetLoose = fixedElytraRestockAmount.get() ? elytraRestockAmount.get() : countLooseFlightReadyElytras() + countAvailableLooseElytraCapacity();
        return targetLoose + (hasUnusableWornElytra() ? 1 : 0);
    }

    private int countAvailableLooseElytraCapacity() {
        return Math.max(0, countUsableEmptyLooseSlotsAfterMinimumReserve() - reservedFoodSlotsNeeded()) + countLooseReplaceableElytras();
    }

    private boolean canPullMoreLooseElytras() {
        int capacity = countAvailableLooseElytraCapacity();
        if (fixedElytraRestockAmount.get()) {
            return countLooseFlightReadyElytras() < getRequiredLooseFlightReadyBeforeRepair() && capacity > 0;
        }

        return capacity > 0;
    }

    private int reservedFoodSlotsNeeded() {
        if (!foodRestock.get() || !hasConfiguredFoodTypes()) return 0;
        return Math.max(0, 2 - countConfiguredFoodSlots());
    }

    private boolean hasUsableElytraRestockTarget() {
        if (!shouldRestockElytraThisPass()) return true;
        if (!restockElytras.get()) return countTotalFlightReadyElytras() > 0;

        if (fixedElytraRestockAmount.get()) {
            int neededIncrease = Math.max(getRequiredLooseFlightReadyBeforeRepair() - countLooseFlightReadyElytras(), 0);
            return neededIncrease <= countAvailableLooseElytraCapacity();
        }

        if (hasUnusableWornElytra() && countLooseFlightReadyElytras() <= 0) {
            return countAvailableLooseElytraCapacity() > 0;
        }

        return countAvailableLooseElytraCapacity() > 0 || countTotalFlightReadyElytras() > 0;
    }

    private boolean hasSatisfiedLooseElytraTargetAfterRepair() {
        if (!fixedElytraRestockAmount.get()) return countAvailableLooseElytraCapacity() <= 0;
        return countLooseFlightReadyElytras() >= elytraRestockAmount.get();
    }

    private boolean shouldAcceptPartialElytraRestock(RestockKind completed) {
        if (isInsideArrivalRadius()) return false;
        if (!(completed == RestockKind.ELYTRA || queuedElytraRestock)) return false;
        if (!restockElytras.get()) return false;
        if (hasSatisfiedLooseElytraTargetAfterRepair()) return false;
        if (hasFlightReadyElytraShulkerInInventory()) return false;
        return countTotalFlightReadyElytras() > 0;
    }

    private boolean shouldContinueElytraRestockAfterPass(RestockKind completed) {
        if (isInsideArrivalRadius()) return false;
        if (!(completed == RestockKind.ELYTRA || queuedElytraRestock)) return false;
        if (!restockElytras.get()) return countTotalFlightReadyElytras() <= 0;
        return !hasSatisfiedLooseElytraTargetAfterRepair() && hasFlightReadyElytraShulkerInInventory();
    }

    private void resumeAfterPartialElytraRestock(String message) {
        int usable = countTotalFlightReadyElytras();
        warning("%s Resuming with %d flight-ready elytra%s.", message, usable, usable == 1 ? "" : "s");

        activeRestockKind = RestockKind.NONE;
        queuedElytraRestock = false;
        restockSourceInventorySlot = -1;
        restockSourceHotbarSlot = -1;
        currentSourceFailureTicks = 0;
        invalidRestockSourceAttempts = 0;
        restockOpenedSourceValidated = false;
        openedInvalidRestockSource = false;

        if (queuedFoodRestock && !foodRestockSuppressed && needsFoodRestock()) {
            startRestockInterrupt(RestockKind.FOOD, resumeStateAfterRestock);
            return;
        }

        if (isInsideArrivalRadius() || resumeStateAfterRestock == ResumeState.ARRIVED_WAIT) {
            state = State.ARRIVED_WAIT;
            stateTicks = 0;
            return;
        }

        if (usable <= 0) {
            state = State.OUT_OF_ELYTRAS_WAIT;
            stateTicks = 0;
            return;
        }

        state = State.TAKEOFF_AWAIT_GROUND;
        stateTicks = 0;
    }

    private void swapContainerElytraWithBrokenInventorySlot(int containerSlotId, int inventorySlotIndex) {
        int inventorySlotId = SlotUtils.indexToId(inventorySlotIndex);
        int syncId = mc.player.currentScreenHandler.syncId;

        mc.interactionManager.clickSlot(syncId, containerSlotId, 0, SlotActionType.PICKUP, mc.player);
        mc.interactionManager.clickSlot(syncId, inventorySlotId, 0, SlotActionType.PICKUP, mc.player);
        mc.interactionManager.clickSlot(syncId, containerSlotId, 0, SlotActionType.PICKUP, mc.player);
    }

    private boolean repairWornElytraIfNeeded() {
        ItemStack chest = mc.player.getEquippedStack(EquipmentSlot.CHEST);
        if (isFlightReadyElytra(chest)) return true;

        int bestSlot = -1;
        int bestRemaining = -1;
        for (int i = 0; i < PLAYER_INVENTORY_SLOTS; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!isFlightReadyElytra(stack)) continue;

            int remaining = stack.getMaxDamage() - stack.getDamage();
            if (remaining > bestRemaining) {
                bestRemaining = remaining;
                bestSlot = i;
            }
        }

        if (bestSlot == -1) return false;

        THMUtils.fakeInventoryOpen(true);
        try {
            int syncId = mc.player.currentScreenHandler.syncId;
            int spareId = SlotUtils.indexToId(bestSlot);
            int chestId = SlotUtils.indexToId(CHEST_SLOT_INDEX);

            mc.interactionManager.clickSlot(syncId, spareId, 0, SlotActionType.PICKUP, mc.player);
            mc.interactionManager.clickSlot(syncId, chestId, 0, SlotActionType.PICKUP, mc.player);
            mc.interactionManager.clickSlot(syncId, spareId, 0, SlotActionType.PICKUP, mc.player);

            ItemStack cursor = mc.player.currentScreenHandler.getCursorStack();
            if (!cursor.isEmpty()) {
                clearCursorAfterSwap();
            }

            actionDelayTicks = Math.max(actionDelayTicks, inventoryDelay.get());
        } finally {
            THMUtils.fakeInventoryOpen(false);
        }

        return isFlightReadyElytra(mc.player.getEquippedStack(EquipmentSlot.CHEST)) || countTotalFlightReadyElytras() > 0;
    }

    private void clearCursorAfterSwap() {
        ItemStack cursor = mc.player.currentScreenHandler.getCursorStack();
        if (cursor.isEmpty()) return;

        int emptySlot = InvUtils.findEmpty().slot();
        if (emptySlot != -1) {
            mc.interactionManager.clickSlot(
                mc.player.currentScreenHandler.syncId,
                SlotUtils.indexToId(emptySlot),
                0,
                SlotActionType.PICKUP,
                mc.player
            );
            if (mc.player.currentScreenHandler.getCursorStack().isEmpty()) return;
        }

        InvUtils.dropHand();
    }

    private boolean hasUnusableWornElytra() {
        ItemStack chest = mc.player.getEquippedStack(EquipmentSlot.CHEST);
        return !isFlightReadyElytra(chest);
    }

    private boolean isRestockState(State state) {
        return state == State.RESTOCK_MOVE_TO_PAD
            || state == State.RESTOCK_PLACE
            || state == State.RESTOCK_OPEN
            || state == State.RESTOCK_LOOT
            || state == State.RESTOCK_CLOSE
            || state == State.RESTOCK_BREAK
            || state == State.RESTOCK_PICKUP;
    }

    private boolean requiresFlightReadyStateAfterRestock() {
        return resumeStateAfterRestock == ResumeState.ROUTE_FLIGHT || activeRestockKind == RestockKind.ELYTRA || queuedElytraRestock;
    }

    private boolean hasFlightReadySpareInInventory() {
        for (int i = 0; i < PLAYER_INVENTORY_SLOTS; i++) {
            if (isFlightReadyElytra(mc.player.getInventory().getStack(i))) return true;
        }

        return false;
    }

    private List<ItemEntity> collectShulkerDrops() {
        BlockPos center = placedShulkerPos != null ? placedShulkerPos : mc.player.getBlockPos();
        Box area = new Box(center).expand(SHULKER_PICKUP_SEARCH_RADIUS, 2.0, SHULKER_PICKUP_SEARCH_RADIUS)
            .union(mc.player.getBoundingBox().expand(SHULKER_PICKUP_SEARCH_RADIUS, 2.0, SHULKER_PICKUP_SEARCH_RADIUS));
        return mc.world.getEntitiesByClass(
            ItemEntity.class,
            area,
            entity -> entity != null && entity.isAlive() && isShulkerBox(entity.getStack())
        );
    }
}
