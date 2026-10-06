/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.modules;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.entity.player.InteractBlockEvent;
import meteordevelopment.meteorclient.events.entity.player.StartBreakingBlockEvent;
import meteordevelopment.meteorclient.events.game.OpenScreenEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WHorizontalList;
import meteordevelopment.meteorclient.gui.widgets.containers.WSection;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.containers.WVerticalList;
import meteordevelopment.meteorclient.gui.widgets.input.WDropdown;
import meteordevelopment.meteorclient.gui.widgets.input.WTextBox;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.gui.widgets.pressable.WCheckbox;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.render.marker.Marker;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.player.CustomPlayerInput;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.world.TickRate;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.ClientInput;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import xyz.thm.addon.THMAddon;
import xyz.thm.addon.settings.DropdownDescriptions;
import xyz.thm.addon.utils.THMStashMoverErrorLog;
import xyz.thm.addon.utils.ThmMembers;
import xyz.thm.addon.utils.server.ServerStatusHandler;

import java.util.*;
import java.util.function.Consumer;

import static xyz.thm.addon.utils.THMUtils.getSaveName;

public class THMStashMover extends Module {
    private static final int PLAYER_MENU_SLOTS = 36;
    private static final double APPROACH_TOLERANCE_SQ = 0.25;
    private static final double TELEPORT_DETECT_DISTANCE_SQ = 1.0;
    private static final long TELEPORT_VALIDATE_TIMEOUT_MS = 20_000L;
    private static final long MAIN_SERVER_GATE_WARN_INTERVAL_MS = 10_000L;

    private final List<KitPair> pairs = new ArrayList<>();
    private final List<KitPair> runtimePairs = new ArrayList<>();
    private final List<SettingGroup> pairSettingGroups = new ArrayList<>();
    private final Map<String, BlockPos> enderChestApproachesByHome = new LinkedHashMap<>();
    private final Map<String, PairStatusCache> pairStatusCache = new LinkedHashMap<>();

    private boolean sharedInputHome;
    private String sharedInputHomeName = "";
    private boolean sharedDepositHome;
    private String sharedDepositHomeName = "";
    private String selectedPairName = "default";
    private String newPairDraft = "default";
    private String deletePairSelection = "";
    private String renamePairSelection = "";
    private String renamePairDraft = "";

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgLogging = settings.createGroup("Logging");
    private final SettingGroup sgTiming = settings.createGroup("Timing");

    private final Setting<Boolean> hwyMonitorReconnectHandling = sgGeneral.add(new BoolSetting.Builder()
        .name("hwy-monitor-reconnect-handling")
        .description("Lets THM Highway Monitor handle reconnects while this pauses off-server.")
        .defaultValue(false)
        .visible(this::exposeHwyMonitorReconnectSettings)
        .build()
    );

    private final Setting<Boolean> focusedPairRestocking = sgGeneral.add(new BoolSetting.Builder()
        .name("focused-pair-restocking")
        .description("Finish the top-priority pair first. Off = rotate pairs after each batch.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> enableErrorLog = sgLogging.add(new BoolSetting.Builder()
        .name("enable-error-log")
        .description("Writes THM Stash mover errors to a dedicated bounded log file.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> teleportDelay = sgTiming.add(new IntSetting.Builder()
        .name("teleport-delay")
        .description("Seconds to wait after /home before navigating or opening containers.")
        .defaultValue(3)
        .min(1)
        .max(30)
        .sliderMax(15)
        .build()
    );

    private final Setting<Integer> actionDelay = sgTiming.add(new IntSetting.Builder()
        .name("action-delay")
        .description("Ticks to wait between item movement actions.")
        .defaultValue(2)
        .min(0)
        .max(10)
        .build()
    );

    private final Setting<Integer> openDelay = sgTiming.add(new IntSetting.Builder()
        .name("open-delay")
        .description("Ticks to wait after opening a container.")
        .defaultValue(5)
        .min(1)
        .max(40)
        .sliderMax(20)
        .build()
    );

    private final Setting<Integer> openRetries = sgTiming.add(new IntSetting.Builder()
        .name("open-retries")
        .description("How many times to retry opening a container before blocking the pair.")
        .defaultValue(5)
        .min(0)
        .max(20)
        .build()
    );

    private final Setting<Boolean> enableTpCooldown = sgTiming.add(new BoolSetting.Builder()
        .name("enable-tp-cooldown")
        .description("Enforce a cooldown between successfully loaded /home teleports.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> tpCooldown = sgTiming.add(new IntSetting.Builder()
        .name("tp-cooldown")
        .description("Seconds after a successfully loaded /home teleport before another /home command.")
        .defaultValue(30)
        .min(1)
        .max(300)
        .sliderMax(120)
        .visible(enableTpCooldown::get)
        .build()
    );

    private final Setting<Integer> rescanDelay = sgTiming.add(new IntSetting.Builder()
        .name("rescan-delay")
        .description("Seconds to wait before rescanning when all deposits are topped off.")
        .defaultValue(10)
        .min(1)
        .max(300)
        .sliderMax(60)
        .build()
    );

    private RunState state = RunState.IDLE;
    private OpenPurpose purpose = OpenPurpose.NONE;
    private KitPair currentPair;
    private int currentPairIndex;
    private int scanStartIndex;
    private int scannedPairs;
    private int tickTimer;
    private long stateStartTime;
    private long lastCountdownMsg;
    private long lastSuccessfulTeleportTime;
    private String travelHome;
    private BlockPos travelApproach;
    private TravelSide travelSide = TravelSide.UNKNOWN;
    private TravelSide currentTravelSide = TravelSide.UNKNOWN;
    private String currentTravelHome = "";
    private BlockPos openTarget;
    private int openAttempts;
    private int openAttemptSequence;
    private int openTraceSequence;
    private int activeOpenTraceId;
    private OpenAttemptDiagnostics openAttemptDiagnostics;
    private double teleportStartX;
    private double teleportStartY;
    private double teleportStartZ;
    private boolean teleportStartPosValid;
    private boolean teleportValidated;
    private boolean mainServerGatePaused;
    private boolean mainServerStartPending;
    private long mainServerGatePausedAt;
    private long lastMainServerGateMsg;
    private int loopInputBlockedSkips;
    private int loopInvalidConfigSkips;
    private int loopTemporaryBlockedSkips;
    private int loopInactiveSkips;
    private boolean thmHwyMonitorStartedByStashMover;

    private List<BlockPos> activeTargets = new ArrayList<>();
    private List<BlockPos> savedInputTargets = new ArrayList<>();
    private int targetIndex;
    private int slotIndex;
    private int savedInputTargetIndex;
    private int savedInputSlotIndex;
    private int demandRemaining;
    private int lastCargoCount;
    private int currentBatchTarget;
    private int bankSlotCount;
    private final Set<Integer> blockedPairs = new HashSet<>();
    private final Set<Integer> inputBlockedPairs = new HashSet<>();
    private final Set<Integer> reservedInventorySlots = new HashSet<>();
    private final Set<Integer> activeInventorySlots = new HashSet<>();
    private final Set<Integer> reservedBankSlots = new HashSet<>();
    private final Set<Integer> activeBankSlots = new HashSet<>();
    private boolean bankSnapshotTaken;
    private boolean bankAllowedForCurrentPair;
    private boolean inputCursorSaved;
    private boolean pendingInputHardBlockAfterDrain;
    private int pendingInputHardBlockPairIndex = -1;
    private String pendingInputHardBlockReason = "";

    private ClientInput previousInput;
    private CustomPlayerInput navInput;

    public THMStashMover() {
        super(THMAddon.MAIN, "THM Stash mover", "Keeps named kit-pair deposit storage topped off with full shulker kits.");
        ensureDefaultPair();
        rebuildPairSettings();
    }

    private boolean exposeHwyMonitorReconnectSettings() {
        return !ThmMembers.isNovice(getSaveName());
    }

    @Override
    public void onActivate() {
        super.onActivate();
        THMStashMoverErrorLog.resetSessionDisable();
        ensureDefaultPair();
        rebuildPairSettings();
        if (!validateStartupConfig()) {
            toggle();
            return;
        }
        captureRuntimePairs();
        syncThmHwyMonitorReconnectOnActivate();
        activeInventorySlots.clear();
        reservedBankSlots.clear();
        activeBankSlots.clear();
        bankSnapshotTaken = false;
        bankSlotCount = 0;
        currentBatchTarget = 0;
        bankAllowedForCurrentPair = false;
        pendingInputHardBlockAfterDrain = false;
        pendingInputHardBlockPairIndex = -1;
        pendingInputHardBlockReason = "";
        clearSavedInputCursor();
        blockedPairs.clear();
        inputBlockedPairs.clear();
        clearBlockedPairStatuses();
        resetLoopSkipCounters();
        currentTravelSide = TravelSide.UNKNOWN;
        currentTravelHome = "";
        travelSide = TravelSide.UNKNOWN;
        scanStartIndex = 0;
        scannedPairs = 0;
        lastSuccessfulTeleportTime = 0;
        resetOpenDiagnosticSession();
        resetTeleportValidation();

        String activationGateReason = mainServerGateReason();
        if (activationGateReason != null) {
            mainServerStartPending = true;
            state = RunState.IDLE;
            pauseForMainServerGate(activationGateReason);
            return;
        }

        startRuntimeAfterMainServerGate();
    }

    @Override
    public void onDeactivate() {
        super.onDeactivate();
        releaseThmHwyMonitorReconnectOnDeactivate();
        stopNavigation();
        if (mc.player != null && mc.gui.screen() != null) mc.player.closeContainer();
        state = RunState.IDLE;
        purpose = OpenPurpose.NONE;
        currentTravelSide = TravelSide.UNKNOWN;
        currentTravelHome = "";
        travelSide = TravelSide.UNKNOWN;
        mainServerGatePaused = false;
        mainServerStartPending = false;
        mainServerGatePausedAt = 0;
        lastMainServerGateMsg = 0;
        lastSuccessfulTeleportTime = 0;
        runtimePairs.clear();
        resetOpenDiagnosticSession();
        currentBatchTarget = 0;
        bankAllowedForCurrentPair = false;
        pendingInputHardBlockAfterDrain = false;
        pendingInputHardBlockPairIndex = -1;
        pendingInputHardBlockReason = "";
        resetTeleportValidation();
    }

    @Override
    public WWidget getWidget(GuiTheme theme) {
        WVerticalList list = theme.verticalList();
        buildPairManager(theme, list);
        return list;
    }

    private void buildPairManager(GuiTheme theme, WVerticalList list) {
        list.add(theme.label("Pair Manager", true)).expandX();

        WSection shared = list.add(theme.section("Shared Homes", true)).expandX().widget();
        WTable sharedTable = shared.add(theme.table()).expandX().widget();

        sharedTable.add(theme.label("All inputs share home"));
        WCheckbox sharedInput = sharedTable.add(theme.checkbox(sharedInputHome)).widget();
        sharedInput.action = () -> {
            sharedInputHome = sharedInput.checked;
            rebuildPairSettings();
            refreshPairManager(theme, list);
        };
        sharedTable.row();

        if (sharedInputHome) {
            sharedTable.add(theme.label("Input home"));
            WTextBox inputHome = sharedTable.add(theme.textBox(sharedInputHomeName)).expandX().widget();
            inputHome.action = () -> sharedInputHomeName = inputHome.get();
            sharedTable.row();
        }

        sharedTable.add(theme.label("All deposits share home"));
        WCheckbox sharedDeposit = sharedTable.add(theme.checkbox(sharedDepositHome)).widget();
        sharedDeposit.action = () -> {
            sharedDepositHome = sharedDeposit.checked;
            rebuildPairSettings();
            refreshPairManager(theme, list);
        };
        sharedTable.row();

        if (sharedDepositHome) {
            sharedTable.add(theme.label("Deposit home"));
            WTextBox depositHome = sharedTable.add(theme.textBox(sharedDepositHomeName)).expandX().widget();
            depositHome.action = () -> sharedDepositHomeName = depositHome.get();
            sharedTable.row();
        }

        WSection ender = list.add(theme.section("Ender Chest Approaches By Home", true)).expandX().widget();
        WTable enderTable = ender.add(theme.table()).expandX().widget();
        List<String> homes = resolvedHomes();
        if (homes.isEmpty()) {
            enderTable.add(theme.label("No homes configured"));
            enderTable.row();
        } else {
            for (String home : homes) {
                addBlockPosRow(theme, enderTable, home, enderChestApproachForHome(home), false, true, value -> setEnderChestApproachForHome(home, value));
            }
        }

        WSection manager = list.add(theme.section("Pairs", true)).expandX().widget();
        WTable pairTable = manager.add(theme.table()).expandX().widget();

        pairTable.add(theme.label("New pair"));
        WTextBox newPair = pairTable.add(theme.textBox(newPairDraft)).expandX().widget();
        newPair.action = () -> newPairDraft = newPair.get();
        WButton create = pairTable.add(theme.button("Create")).widget();
        create.action = () -> {
            sendMsg(createPair(newPairDraft));
            refreshPairManager(theme, list);
        };
        pairTable.row();

        String[] deletablePairs = blankPlusPairNames();
        deletePairSelection = validSelection(deletePairSelection, deletablePairs);
        pairTable.add(theme.label("Delete pair"));
        WDropdown<String> deleteDropdown = pairTable.add(theme.dropdown(deletablePairs, deletePairSelection)).expandX().widget();
        DropdownDescriptions.configure(deleteDropdown,
            value -> value.equals("") ? "Choose a pair." : "Select this pair for deletion.");
        deleteDropdown.action = () -> deletePairSelection = deleteDropdown.get();
        WButton delete = pairTable.add(theme.button("Delete")).widget();
        delete.action = () -> {
            sendMsg(deleteSelectedPair());
            refreshPairManager(theme, list);
        };
        pairTable.row();

        String[] renamePairs = blankPlusPairNames();
        renamePairSelection = validSelection(renamePairSelection, renamePairs);
        pairTable.add(theme.label("Rename pair"));
        WDropdown<String> renameDropdown = pairTable.add(theme.dropdown(renamePairs, renamePairSelection)).expandX().widget();
        DropdownDescriptions.configure(renameDropdown,
            value -> value.equals("") ? "Choose a pair." : "Select this pair for renaming.");
        renameDropdown.action = () -> renamePairSelection = renameDropdown.get();
        WTextBox renameName = pairTable.add(theme.textBox(renamePairDraft)).expandX().widget();
        renameName.action = () -> renamePairDraft = renameName.get();
        WButton rename = pairTable.add(theme.button("Rename")).widget();
        rename.action = () -> {
            sendMsg(renameSelectedPair());
            refreshPairManager(theme, list);
        };

        for (KitPair pair : pairs) {
            addPairSection(theme, list, pair);
        }
    }

    private void addPairSection(GuiTheme theme, WVerticalList list, KitPair pair) {
        WSection section = list.add(theme.section(pairGroupName(pair), true)).expandX().widget();
        WTable table = section.add(theme.table()).expandX().widget();

        table.add(theme.label("Active"));
        WCheckbox active = table.add(theme.checkbox(pair.active)).widget();
        active.action = () -> {
            pair.active = active.checked;
            warnPairRuntimeSelectionChange("Pair active setting saved for " + pair.name + ".");
        };
        table.row();

        table.add(theme.label("Priority"));
        WHorizontalList priority = table.add(theme.horizontalList()).widget();
        WButton up = priority.add(theme.button("↑")).widget();
        up.action = () -> {
            sendMsg(movePairPriority(pair, -1));
            refreshPairManager(theme, list);
        };
        WButton down = priority.add(theme.button("↓")).widget();
        down.action = () -> {
            sendMsg(movePairPriority(pair, 1));
            refreshPairManager(theme, list);
        };
        table.row();

        if (!sharedInputHome) {
            addStringRow(theme, table, "Input home", pair.inputHome, value -> pair.inputHome = value);
        }

        addBlockPosRow(theme, table, "Input corner 1", pair.inputCorner1, true, false, value -> pair.inputCorner1 = value);
        addBlockPosRow(theme, table, "Input corner 2", pair.inputCorner2, true, false, value -> pair.inputCorner2 = value);
        addBlockPosRow(theme, table, "Input approach", pair.inputApproachPos, false, true, value -> pair.inputApproachPos = value);

        if (!sharedDepositHome) {
            addStringRow(theme, table, "Deposit home", pair.depositHome, value -> pair.depositHome = value);
        }

        addBlockPosRow(theme, table, "Deposit corner 1", pair.depositCorner1, true, false, value -> pair.depositCorner1 = value);
        addBlockPosRow(theme, table, "Deposit corner 2", pair.depositCorner2, true, false, value -> pair.depositCorner2 = value);
        addBlockPosRow(theme, table, "Deposit approach", pair.depositApproachPos, false, true, value -> pair.depositApproachPos = value);
    }

    private void addStringRow(GuiTheme theme, WTable table, String label, String value, Consumer<String> onChanged) {
        table.add(theme.label(label));
        WTextBox textBox = table.add(theme.textBox(value)).expandX().widget();
        textBox.action = () -> onChanged.accept(textBox.get());
        table.row();
    }

    private void addBlockPosRow(GuiTheme theme, WTable table, String label, BlockPos value, boolean clickButton, boolean setHereButton, Consumer<BlockPos> onChanged) {
        table.add(theme.label(label));
        table.add(new PairBlockPosEdit(value, clickButton, setHereButton, onChanged)).expandX();
        table.row();
    }

    private void refreshPairManager(GuiTheme theme, WVerticalList list) {
        list.clear();
        buildPairManager(theme, list);
        list.invalidate();
    }

    @Override
    public CompoundTag toTag() {
        List<SettingGroup> originalGroups = new ArrayList<>(settings.groups);
        CompoundTag tag;

        try {
            settings.groups.removeAll(pairSettingGroups);
            tag = super.toTag();
        } finally {
            settings.groups.clear();
            settings.groups.addAll(originalGroups);
        }

        ListTag pairTags = new ListTag();
        for (KitPair pair : pairs) pairTags.add(pair.toTag());
        tag.put("kitPairs", pairTags);
        tag.putString("selectedKitPair", selectedPairName);
        tag.putBoolean("sharedInputHome", sharedInputHome);
        tag.putString("sharedInputHomeName", sharedInputHomeName);
        tag.putBoolean("sharedDepositHome", sharedDepositHome);
        tag.putString("sharedDepositHomeName", sharedDepositHomeName);
        ListTag enderHomeTags = new ListTag();
        for (Map.Entry<String, BlockPos> entry : enderChestApproachesByHome.entrySet()) {
            if (entry.getKey().isEmpty() || !isConfiguredPos(entry.getValue())) continue;

            CompoundTag enderTag = new CompoundTag();
            enderTag.putString("home", entry.getKey());
            enderTag.putLong("approachPos", entry.getValue().asLong());
            enderHomeTags.add(enderTag);
        }
        tag.put("enderChestApproachesByHome", enderHomeTags);
        return tag;
    }

    @Override
    public Module fromTag(CompoundTag tag) {
        super.fromTag(tag);
        enderChestApproachesByHome.clear();
        sharedInputHome = tag.getBooleanOr("sharedInputHome", false);
        sharedInputHomeName = tag.getStringOr("sharedInputHomeName", "");
        sharedDepositHome = tag.getBooleanOr("sharedDepositHome", false);
        sharedDepositHomeName = tag.getStringOr("sharedDepositHomeName", "");

        pairs.clear();

        for (Tag pairTag : tag.getListOrEmpty("kitPairs")) {
            if (pairTag instanceof CompoundTag compound) pairs.add(KitPair.fromTag(compound));
        }

        ensureDefaultPair();
        for (Tag enderTag : tag.getListOrEmpty("enderChestApproachesByHome")) {
            if (!(enderTag instanceof CompoundTag compound)) continue;

            String home = normalizeHome(compound.getStringOr("home", ""));
            BlockPos approach = BlockPos.of(compound.getLongOr("approachPos", BlockPos.ZERO.asLong()));
            if (!home.isEmpty() && isConfiguredPos(approach)) enderChestApproachesByHome.put(home, approach);
        }
        String selected = tag.getStringOr("selectedKitPair", tag.getStringOr("activeKitPair", selectedPairName));
        if (indexOfPair(selected) < 0) selected = pairs.get(0).name;
        selectedPairName = selected;
        deletePairSelection = "";
        renamePairSelection = "";
        rebuildPairSettings();
        return this;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        String gateReason = mainServerGateReason();
        if (gateReason != null) {
            pauseForMainServerGate(gateReason);
            armThmHwyMonitorReconnect("main-server-gate");
            return;
        }

        resumeFromMainServerGate();
        if (mainServerStartPending) {
            startRuntimeAfterMainServerGate();
            return;
        }

        switch (state) {
            case WAITING_FOR_TP_COOLDOWN -> tickTeleportCooldown();
            case TELEPORTING -> tickTeleport();
            case WAITING_FOR_TELEPORT -> tickWaitForTeleport();
            case NAVIGATING -> tickNavigate();
            case OPENING -> tickOpenTarget();
            case WAITING_FOR_SCREEN -> tickWaitForScreen();
            case SCANNING_DEPOSIT -> tickScanDeposit();
            case SNAPSHOTTING_BANK -> tickSnapshotBank();
            case WITHDRAWING_INPUT -> tickWithdrawInput();
            case BANKING -> tickBank();
            case DEPOSITING -> tickDeposit();
            case UNBANKING -> tickUnbank();
            case WAITING_TO_RESCAN -> tickWaitToRescan();
            case WAITING_WITH_CARGO -> tickWaitWithCargo();
            default -> {}
        }
    }

    @EventHandler
    private void onOpenScreen(OpenScreenEvent event) {
        OpenAttemptDiagnostics diagnostics = openAttemptDiagnostics;
        if (diagnostics == null || state != RunState.WAITING_FOR_SCREEN) return;

        diagnostics.screenEventSeen = true;
        diagnostics.screenEventCancelled = event.isCancelled();
        diagnostics.observedScreen = screenType(event.screen);
        diagnostics.observedMenu = screenMenuType(event.screen);
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        OpenAttemptDiagnostics diagnostics = openAttemptDiagnostics;
        if (diagnostics == null || state != RunState.WAITING_FOR_SCREEN) return;
        if (!(event.packet instanceof ClientboundOpenScreenPacket packet)) return;

        diagnostics.openPacketSeen = true;
        diagnostics.openPacketCancelled = event.isCancelled();
        diagnostics.packetMenu = registryId(BuiltInRegistries.MENU.getKey(packet.getType()));
    }

    public String createPair(String rawName) {
        String name = sanitizePairName(rawName);
        if (name.isEmpty()) return "Pair name cannot be empty.";
        if (indexOfPair(name) >= 0) return "Pair already exists: " + name;

        KitPair pair = new KitPair(name);
        pairs.add(pair);
        selectedPairName = name;
        rebuildPairSettings();
        return "Created pair: " + name;
    }

    private String deleteSelectedPair() {
        String name = sanitizePairName(deletePairSelection);
        if (name.isEmpty()) return "Select a pair to delete first.";
        return deletePair(name);
    }

    public String deletePair(String rawName) {
        String name = sanitizePairName(rawName);
        int index = indexOfPair(name);
        if (index < 0) return "Pair not found: " + name;
        if (pairs.size() == 1) return "Cannot delete the last pair.";

        pairs.remove(index);
        int next = Math.min(index, pairs.size() - 1);
        if (selectedPairName.equals(name)) selectedPairName = pairs.get(next).name;
        clearPairActionSelections(name);
        blockedPairs.clear();
        inputBlockedPairs.clear();
        clearBlockedPairStatuses();
        rebuildPairSettings();
        return "Deleted pair: " + name;
    }

    private String renameSelectedPair() {
        String selected = sanitizePairName(renamePairSelection);
        if (selected.isEmpty()) return "Select a pair to rename first.";
        return renamePair(selected, renamePairDraft);
    }

    public String renamePair(String rawOldName, String rawNewName) {
        String oldName = sanitizePairName(rawOldName);
        String newName = sanitizePairName(rawNewName);
        int index = indexOfPair(oldName);
        if (oldName.isEmpty()) return "Select a pair to rename first.";
        if (index < 0) return "Pair not found: " + oldName;
        if (newName.isEmpty()) return "New pair name cannot be empty.";
        if (indexOfPair(newName) >= 0) return "Pair already exists: " + newName;

        KitPair pair = pairs.get(index);
        pair.name = newName;
        if (selectedPairName.equals(oldName)) selectedPairName = newName;
        clearPairActionSelections(oldName);
        blockedPairs.clear();
        inputBlockedPairs.clear();
        clearBlockedPairStatuses();
        renamePairDraft = "";
        rebuildPairSettings();
        return "Renamed pair " + oldName + " to " + newName + ".";
    }

    public String selectPair(String rawName) {
        String name = sanitizePairName(rawName);
        int index = indexOfPair(name);
        if (index < 0) return "Pair not found: " + name;

        selectedPairName = pairs.get(index).name;
        return "Selected pair: " + pairs.get(index).name;
    }

    private String movePairPriority(KitPair pair, int direction) {
        int index = pairs.indexOf(pair);
        if (index < 0) return "Pair not found.";

        int target = index + direction;
        if (target < 0) return pair.name + " is already highest priority.";
        if (target >= pairs.size()) return pair.name + " is already lowest priority.";

        pairs.remove(index);
        pairs.add(target, pair);
        rebuildPairSettings();
        warnPairRuntimeSelectionChange("Pair priority saved for " + pair.name + ".");
        return "Moved " + pair.name + (direction < 0 ? " up." : " down.");
    }

    private void warnPairRuntimeSelectionChange(String prefix) {
        if (!isActive()) return;
        sendMsg(prefix + " Restart THM Stash mover for active pair selection/order changes to affect this run.");
    }

    public String describePairs() {
        ensureDefaultPair();
        StringBuilder builder = new StringBuilder("Stash Mover pairs:");
        for (int i = 0; i < pairs.size(); i++) {
            KitPair pair = pairs.get(i);
            builder.append("\n").append(pair.name.equals(selectedPairName) ? "* " : "- ");
            builder.append(i + 1).append(". ").append(pair.name);
            if (!pair.active) builder.append(" (inactive)");
        }
        return builder.toString();
    }

    public List<PairHudRow> getPairHudRows() {
        syncPairStatusCache();
        List<PairHudRow> rows = new ArrayList<>();
        for (KitPair pair : pairs) {
            if (!pair.active) {
                rows.add(new PairHudRow(pair.name, "inactive", "inactive"));
                continue;
            }
            PairStatusCache status = pairStatusFor(pair);
            rows.add(new PairHudRow(pair.name, status.inputText(), status.depositText()));
        }
        return rows;
    }

    public String captureApproach(boolean input) {
        return captureApproach(activePair(), input);
    }

    private String captureApproach(KitPair pair, boolean input) {
        if (mc.player == null) return "Player is not available.";
        BlockPos pos = mc.player.blockPosition();
        if (input) pair.inputApproachPos = pos;
        else pair.depositApproachPos = pos;
        rebuildPairSettings();
        return "Captured " + (input ? "input" : "deposit") + " approach for " + pair.name + ": " + pos.toShortString();
    }

    public String captureEnderAccess(boolean input) {
        return captureEnderApproach(input);
    }

    public String captureEnderApproach(boolean input) {
        if (mc.player == null) return "Player is not available.";
        String home = normalizeHome(input ? resolveInputHome(activePair()) : resolveDepositHome(activePair()));
        if (home.isEmpty()) return "Set the active pair's " + (input ? "input" : "deposit") + " home before capturing ender approach.";

        BlockPos pos = mc.player.blockPosition();
        setEnderChestApproachForHome(home, pos);
        settings.invalidate();
        return "Captured ender approach for " + home + ": " + pos.toShortString();
    }

    private void startPairScan(int index) {
        List<KitPair> scanPairs = runtimePairsForScan();
        if (scanPairs.isEmpty()) {
            sendMsg("No kit pairs configured.");
            toggle();
            return;
        }

        if (scannedPairs == 0) resetLoopSkipCounters();

        if (scannedPairs >= scanPairs.size()) {
            blockedPairs.clear();
            clearBlockedPairStatuses();
            currentBatchTarget = 0;
            bankAllowedForCurrentPair = false;
            stateStartTime = System.currentTimeMillis();
            if (loopInputBlockedSkips == 0 && loopInvalidConfigSkips == 0 && loopTemporaryBlockedSkips == 0) {
                sendMsg("All deposits are topped off. Waiting before rescanning.");
            } else {
                String message = noRunnableWorkMessage();
                sendMsg(message);
                logGlobalError("no_runnable_work", "inactive_skips=" + loopInactiveSkips + " input_blocked_skips=" + loopInputBlockedSkips + " invalid_config_skips=" + loopInvalidConfigSkips + " temporary_skips=" + loopTemporaryBlockedSkips);
            }
            state = RunState.WAITING_TO_RESCAN;
            return;
        }

        currentPairIndex = Math.floorMod(index, scanPairs.size());
        currentPair = scanPairs.get(currentPairIndex);
        scannedPairs++;
        currentBatchTarget = 0;
        bankAllowedForCurrentPair = false;
        pendingInputHardBlockAfterDrain = false;
        pendingInputHardBlockPairIndex = -1;
        pendingInputHardBlockReason = "";
        clearSavedInputCursor();

        if (!currentPair.active) {
            loopInactiveSkips++;
            setInputStatus(currentPair, "inactive");
            setDepositStatusText(currentPair, "inactive");
            startPairScan(currentPairIndex + 1);
            return;
        }

        if (inputBlockedPairs.contains(currentPairIndex)) {
            loopInputBlockedSkips++;
            markPairBlocked(currentPair);
            logError("deposit_scan_skipped", "reason=" + quoteLogValue("input blocked"));
            startPairScan(currentPairIndex + 1);
            return;
        }

        if (!isPairRunnable(currentPair)) {
            loopInvalidConfigSkips++;
            blockedPairs.add(currentPairIndex);
            markPairBlocked(currentPair);
            logError("pair_invalid_config", "reason=" + quoteLogValue(invalidPairReason(currentPair)));
            startPairScan(currentPairIndex + 1);
            return;
        }

        activeTargets = new ArrayList<>();
        demandRemaining = 0;
        targetIndex = 0;
        slotIndex = 0;
        purpose = OpenPurpose.SCAN_DEPOSIT;
        beginTravel(resolveDepositHome(currentPair), currentPair.depositApproachPos, TravelSide.DEPOSIT);
    }

    private void startBankSnapshotOrInputWithdraw() {
        if (hasCurrentCargo()) {
            if (!activeInventorySlots.isEmpty()) startDepositItems();
            else startUnbanking();
            return;
        }

        int playerCarryCapacity = availableManagedInventorySlots();
        if (demandRemaining <= playerCarryCapacity) {
            bankAllowedForCurrentPair = false;
            currentBatchTarget = demandRemaining;
            startInputWithdraw(true);
            return;
        }

        BlockPos depositEnderApproach = enderChestApproachForHome(resolveDepositHome(currentPair));
        BlockPos inputEnderApproach = enderChestApproachForHome(resolveInputHome(currentPair));
        bankAllowedForCurrentPair = isConfiguredPos(depositEnderApproach) && isConfiguredPos(inputEnderApproach);

        if (!bankAllowedForCurrentPair) {
            currentBatchTarget = Math.min(demandRemaining, playerCarryCapacity);
            if (currentBatchTarget <= 0) {
                loopTemporaryBlockedSkips++;
                logError("no_carry_capacity", "reason=" + quoteLogValue("no usable player inventory space for inventory-only batch"));
                blockCurrentPair("No usable player inventory space for this inventory-only batch.");
                startPairScan(currentPairIndex + 1);
                return;
            }

            startInputWithdraw(true);
            return;
        }

        if (!bankSnapshotTaken) {
            purpose = OpenPurpose.SNAPSHOT_BANK;
            activeTargets = new ArrayList<>();
            targetIndex = 0;
            slotIndex = 0;
            beginTravel(resolveDepositHome(currentPair), depositEnderApproach, TravelSide.DEPOSIT);
            return;
        }

        startInputWithdrawWithCapacity();
    }

    private void startInputWithdrawWithCapacity() {
        int capacity = availableManagedInventorySlots() + availableBankCapacityForCurrentPair();
        currentBatchTarget = Math.min(demandRemaining, capacity);

        if (currentBatchTarget <= 0) {
            loopTemporaryBlockedSkips++;
            logError("no_carry_capacity", "reason=" + quoteLogValue("no usable player inventory or ender chest bank capacity"));
            blockCurrentPair("No usable player inventory or ender chest bank capacity for this batch.");
            startPairScan(currentPairIndex + 1);
            return;
        }

        startInputWithdraw(true);
    }

    private void startInputWithdraw(boolean resetCursor) {
        if (resetCursor) {
            clearSavedInputCursor();
            activeTargets = new ArrayList<>();
            targetIndex = 0;
            slotIndex = 0;
        } else if (inputCursorSaved) {
            activeTargets = new ArrayList<>(savedInputTargets);
            targetIndex = savedInputTargetIndex;
            slotIndex = savedInputSlotIndex;
        } else {
            activeTargets = new ArrayList<>();
            targetIndex = 0;
            slotIndex = 0;
        }

        lastCargoCount = cargoCount();
        purpose = OpenPurpose.WITHDRAW_INPUT;
        beginTravel(resolveInputHome(currentPair), currentPair.inputApproachPos, TravelSide.INPUT);
    }

    private void startBanking() {
        if (!canUseBankForCurrentPair() || availableBankCapacityForCurrentPair() <= 0) {
            startDepositItems();
            return;
        }

        saveInputCursor();
        String inputHome = resolveInputHome(currentPair);
        BlockPos enderApproach = enderChestApproachForHome(inputHome);
        if (!isConfiguredPos(enderApproach)) {
            startDepositItems();
            return;
        }

        purpose = OpenPurpose.BANK_ITEMS;
        activeTargets = new ArrayList<>();
        targetIndex = 0;
        slotIndex = 0;
        beginTravel(inputHome, enderApproach, TravelSide.INPUT);
    }

    private void startDepositItems() {
        clearSavedInputCursor();
        activeTargets = new ArrayList<>();
        targetIndex = 0;
        slotIndex = 0;
        purpose = OpenPurpose.DEPOSIT_ITEMS;
        beginTravel(resolveDepositHome(currentPair), currentPair.depositApproachPos, TravelSide.DEPOSIT);
    }

    private void saveInputCursor() {
        savedInputTargets = new ArrayList<>(activeTargets);
        savedInputTargetIndex = targetIndex;
        savedInputSlotIndex = slotIndex;
        inputCursorSaved = true;
    }

    private void clearSavedInputCursor() {
        savedInputTargets = new ArrayList<>();
        savedInputTargetIndex = 0;
        savedInputSlotIndex = 0;
        inputCursorSaved = false;
    }

    private void startUnbanking() {
        String depositHome = resolveDepositHome(currentPair);
        BlockPos enderApproach = enderChestApproachForHome(depositHome);
        if (!isConfiguredPos(enderApproach)) {
            blockCurrentPair("Banked shulkers remain but ender approach is not configured for " + depositHome + ".");
            waitWithCurrentCargo("Cannot drain this pair's banked shulkers without an ender approach for " + depositHome + ".");
            return;
        }

        purpose = OpenPurpose.UNBANK_ITEMS;
        activeTargets = new ArrayList<>();
        targetIndex = 0;
        slotIndex = 0;
        beginTravel(depositHome, enderApproach, TravelSide.DEPOSIT);
    }

    private void beginTravel(String home, BlockPos approach, TravelSide side) {
        closeScreen();
        stopNavigation();
        clearOpenFailureTrace();
        travelHome = home;
        travelApproach = approach;
        travelSide = side;
        openAttempts = 0;
        lastCountdownMsg = 0;
        stateStartTime = System.currentTimeMillis();
        if (isAtApproach(approach)) {
            currentTravelSide = side;
            currentTravelHome = home;
            state = RunState.OPENING;
            return;
        }
        if (canWalkWithinSameResolvedHome(home)) {
            currentTravelHome = home;
            startNavigation();
            return;
        }
        currentTravelSide = TravelSide.UNKNOWN;
        currentTravelHome = "";
        resetTeleportValidation();
        state = remainingTeleportCooldownMs(System.currentTimeMillis()) > 0
            ? RunState.WAITING_FOR_TP_COOLDOWN
            : RunState.TELEPORTING;
    }

    private void tickTeleportCooldown() {
        long now = System.currentTimeMillis();
        long remaining = remainingTeleportCooldownMs(now);
        if (remaining <= 0) {
            state = RunState.TELEPORTING;
            return;
        }

        if (now - lastCountdownMsg >= 5000L) {
            lastCountdownMsg = now;
            sendMsg("TP cooldown... " + ((remaining + 999L) / 1000L) + "s left.");
        }
    }

    private long remainingTeleportCooldownMs(long now) {
        if (!enableTpCooldown.get() || lastSuccessfulTeleportTime <= 0) return 0;

        long total = tpCooldown.get() * 1000L;
        long elapsed = Math.max(0L, now - lastSuccessfulTeleportTime);
        return Math.max(0L, total - elapsed);
    }

    private void tickTeleport() {
        captureTeleportStart();
        ChatUtils.sendPlayerMsg("/home " + travelHome);
        stateStartTime = System.currentTimeMillis();
        lastCountdownMsg = 0;
        state = RunState.WAITING_FOR_TELEPORT;
    }

    private void tickWaitForTeleport() {
        long now = System.currentTimeMillis();
        long minimumDelayMs = teleportDelay.get() * 1000L;
        if (now - stateStartTime < minimumDelayMs) return;

        teleportValidated = teleportValidated || hasTeleportPositionValidation();
        if (!teleportValidated || !isTravelWorldReady()) {
            if (now - stateStartTime >= minimumDelayMs + TELEPORT_VALIDATE_TIMEOUT_MS) {
                failCurrentTravel("Teleport to " + travelHome + " did not validate with loaded chunks.");
            }
            return;
        }

        lastSuccessfulTeleportTime = now;
        currentTravelSide = travelSide;
        currentTravelHome = detectedCurrentHomeOr(travelHome);
        if (needsNavigation(travelApproach)) {
            startNavigation();
        } else {
            state = RunState.OPENING;
        }
    }

    private void tickNavigate() {
        if (!needsNavigation(travelApproach)) {
            stopNavigation();
            currentTravelSide = travelSide;
            currentTravelHome = detectedCurrentHomeOr(travelHome);
            state = RunState.OPENING;
            return;
        }

        double targetX = travelApproach.getX() + 0.5;
        double targetZ = travelApproach.getZ() + 0.5;
        Vec3 yawTarget = new Vec3(targetX, mc.player.getY(), targetZ);
        mc.player.setYRot((float) Rotations.getYaw(yawTarget));
        ensureNavigationInput();
        navInput.forward(true);
        navInput.sprint(false);
    }

    private void tickOpenTarget() {
        if (purpose == OpenPurpose.SCAN_DEPOSIT || purpose == OpenPurpose.WITHDRAW_INPUT || purpose == OpenPurpose.DEPOSIT_ITEMS) {
            if (activeTargets.isEmpty()) {
                activeTargets = buildTargetsForPurpose();
                if (activeTargets.isEmpty()) {
                    handleMissingTargetsForPurpose();
                    return;
                }
            }
            if (targetIndex >= activeTargets.size()) {
                finishContainerListPurpose();
                return;
            }
            openTarget = activeTargets.get(targetIndex);
        } else {
            if (activeTargets.isEmpty()) {
                BlockPos ender = findNearestEnderChest(travelApproach);
                if (ender == null) {
                    retryOrBlock("Could not find an ender chest near " + formatPos(travelApproach));
                    return;
                }
                activeTargets = List.of(ender);
            }
            openTarget = activeTargets.get(0);
        }

        if (!openBlock(openTarget)) {
            recordOpenAttemptFailure("Could not dispatch the container interaction.");
            retryOrBlock("Could not dispatch the container interaction.");
            return;
        }

        tickTimer = 0;
        state = RunState.WAITING_FOR_SCREEN;
    }

    private void tickWaitForScreen() {
        tickTimer++;
        if (tickTimer < openDelay.get()) return;

        if (!(mc.gui.screen() instanceof AbstractContainerScreen<?> screen)) {
            recordOpenAttemptFailure("Container screen did not open.");
            retryOrBlock("Container screen did not open.");
            return;
        }

        recordOpenRecovery(screen);
        switch (purpose) {
            case SCAN_DEPOSIT -> state = RunState.SCANNING_DEPOSIT;
            case SNAPSHOT_BANK -> state = RunState.SNAPSHOTTING_BANK;
            case WITHDRAW_INPUT -> state = RunState.WITHDRAWING_INPUT;
            case BANK_ITEMS -> state = RunState.BANKING;
            case DEPOSIT_ITEMS -> state = RunState.DEPOSITING;
            case UNBANK_ITEMS -> state = RunState.UNBANKING;
            default -> state = RunState.IDLE;
        }
    }

    private void tickScanDeposit() {
        AbstractContainerMenu menu = currentMenu();
        if (menu == null) {
            retryOrBlock("Deposit screen closed while scanning.");
            return;
        }

        int containerSlots = getContainerSlots(menu);
        for (int i = 0; i < containerSlots; i++) {
            if (menu.slots.get(i).getItem().isEmpty()) demandRemaining++;
        }

        closeScreen();
        targetIndex++;
        if (targetIndex >= activeTargets.size()) setDepositStatus(currentPair, demandRemaining);
        state = RunState.OPENING;
    }

    private void tickSnapshotBank() {
        AbstractContainerMenu menu = currentMenu();
        if (menu == null) {
            retryOrBlock("Ender chest closed while checking bank capacity.");
            return;
        }

        int bankSlots = getContainerSlots(menu);
        snapshotBankReservedSlots(menu, bankSlots);
        refreshActiveBankSlots(menu, bankSlots);
        closeScreen();
        startInputWithdrawWithCapacity();
    }

    private void tickWithdrawInput() {
        if (waitActionDelay()) return;

        AbstractContainerMenu menu = currentMenu();
        if (menu == null) {
            retryOrBlock("Input screen closed while withdrawing.");
            return;
        }

        int containerSlots = getContainerSlots(menu);
        refreshActiveInventorySlots(menu, containerSlots);

        if (cargoCount() >= currentBatchTarget) {
            setInputStatus(currentPair, "Has kits");
            startDepositItems();
            return;
        }

        if (!hasOpenManagedInventorySlot(menu, containerSlots)) {
            if (activeInventoryCount(menu, containerSlots) > 0) {
                setInputStatus(currentPair, "Has kits");
                if (cargoCount() >= currentBatchTarget) startDepositItems();
                else if (canUseBankForCurrentPair() && availableBankCapacityForCurrentPair() > 0) startBanking();
                else startDepositItems();
            } else {
                loopTemporaryBlockedSkips++;
                logError("no_carry_capacity", "reason=" + quoteLogValue("no usable inventory space while withdrawing input"));
                blockCurrentPair("No usable inventory space for this pair.");
                closeScreen();
                startPairScan(currentPairIndex + 1);
            }
            return;
        }

        while (slotIndex < containerSlots) {
            ItemStack stack = menu.slots.get(slotIndex).getItem();
            if (isFullShulker(stack)) {
                setInputStatus(currentPair, "Has kits");
                click(menu, slotIndex);
                refreshActiveInventorySlots(menu, containerSlots);
                return;
            }
            slotIndex++;
        }

        closeScreen();
        targetIndex++;
        slotIndex = 0;

        if (targetIndex >= activeTargets.size()) {
            if (cargoCount() > 0) {
                queueInputHardBlockAfterDrain("Input ran out of full shulker kits.");
                startDepositItems();
            }
            else {
                hardBlockInputCurrentPair("Input has no full shulker kits available.");
                finishBatchAndAdvance();
            }
        } else {
            state = RunState.OPENING;
        }
    }

    private void tickBank() {
        if (waitActionDelay()) return;

        AbstractContainerMenu menu = currentMenu();
        if (menu == null) {
            retryOrBlock("Ender chest closed while banking.");
            return;
        }

        int bankSlots = getContainerSlots(menu);
        if (!bankSnapshotTaken) snapshotBankReservedSlots(menu, bankSlots);
        refreshActiveInventorySlots(menu, bankSlots);
        refreshActiveBankSlots(menu, bankSlots);

        if (cargoCount() >= currentBatchTarget) {
            closeScreen();
            startDepositItems();
            return;
        }

        int playerSlot = findActivePlayerMenuSlot(menu, bankSlots);
        if (playerSlot < 0) {
            continueAfterBanking();
            return;
        }

        if (findEmptyBankSlot(menu, bankSlots) < 0) {
            continueAfterBanking();
            return;
        }

        click(menu, playerSlot);
        refreshActiveBankSlots(menu, bankSlots);
        refreshActiveInventorySlots(menu, bankSlots);
    }

    private void continueAfterBanking() {
        closeScreen();
        if (cargoCount() >= currentBatchTarget) {
            startDepositItems();
            return;
        }

        if (availableManagedInventorySlots() > 0) {
            startInputWithdraw(false);
        } else {
            startDepositItems();
        }
    }

    private void tickDeposit() {
        if (waitActionDelay()) return;

        AbstractContainerMenu menu = currentMenu();
        if (menu == null) {
            retryOrBlock("Deposit screen closed while depositing.");
            return;
        }

        int containerSlots = getContainerSlots(menu);
        refreshActiveInventorySlots(menu, containerSlots);

        int playerSlot = findActivePlayerMenuSlot(menu, containerSlots);
        if (playerSlot < 0) {
            closeScreen();
            if (!activeBankSlots.isEmpty()) startUnbanking();
            else finishBatchAndAdvance();
            return;
        }

        if (findEmptyContainerSlot(menu, containerSlots) < 0) {
            closeScreen();
            targetIndex++;
            slotIndex = 0;
            state = RunState.OPENING;
            return;
        }

        int before = activeInventorySlots.size();
        click(menu, playerSlot);
        refreshActiveInventorySlots(menu, containerSlots);
        if (activeInventorySlots.size() < before) {
            demandRemaining = Math.max(0, demandRemaining - 1);
            setDepositStatus(currentPair, demandRemaining);
        }
    }

    private void tickUnbank() {
        if (waitActionDelay()) return;

        AbstractContainerMenu menu = currentMenu();
        if (menu == null) {
            retryOrBlock("Ender chest closed while unbanking.");
            return;
        }

        int bankSlots = getContainerSlots(menu);
        refreshActiveBankSlots(menu, bankSlots);
        refreshActiveInventorySlots(menu, bankSlots);

        if (activeBankSlots.isEmpty()) {
            closeScreen();
            startDepositItems();
            return;
        }

        if (!hasOpenManagedInventorySlot(menu, bankSlots)) {
            closeScreen();
            startDepositItems();
            return;
        }

        int bankSlot = findActiveBankSlot(menu, bankSlots);
        if (bankSlot < 0) {
            closeScreen();
            startDepositItems();
            return;
        }

        click(menu, bankSlot);
        refreshActiveBankSlots(menu, bankSlots);
        refreshActiveInventorySlots(menu, bankSlots);
    }

    private void tickWaitToRescan() {
        if (System.currentTimeMillis() - stateStartTime < rescanDelay.get() * 1000L) return;
        scannedPairs = 0;
        startPairScan(scanStartIndex);
    }

    private void finishContainerListPurpose() {
        switch (purpose) {
            case SCAN_DEPOSIT -> {
                setDepositStatus(currentPair, demandRemaining);
                if (demandRemaining <= 0) {
                    sendMsg(currentPair.name + " is topped off.");
                    startPairScan(currentPairIndex + 1);
                } else {
                    sendMsg(currentPair.name + " needs " + demandRemaining + " kit(s).");
                    startBankSnapshotOrInputWithdraw();
                }
            }
            case WITHDRAW_INPUT -> {
                if (cargoCount() > 0) {
                    queueInputHardBlockAfterDrain("Input ran out of full shulker kits.");
                    startDepositItems();
                }
                else {
                    hardBlockInputCurrentPair("Input has no full shulker kits available.");
                    finishBatchAndAdvance();
                }
            }
            case DEPOSIT_ITEMS -> {
                if (!activeBankSlots.isEmpty()) startUnbanking();
                else if (!activeInventorySlots.isEmpty()) {
                    blockCurrentPair("Deposit storage filled before this carried batch was drained.");
                    waitWithCurrentCargo("Waiting to drain current pair before checking another pair.");
                }
                else finishBatchAndAdvance();
            }
            default -> finishBatchAndAdvance();
        }
    }

    private void finishBatchAndAdvance() {
        closeScreen();
        if (!activeInventorySlots.isEmpty() || !activeBankSlots.isEmpty()) {
            if (!activeInventorySlots.isEmpty()) startDepositItems();
            else startUnbanking();
            return;
        }

        if (pendingInputHardBlockAfterDrain && pendingInputHardBlockPairIndex == currentPairIndex) {
            hardBlockInputCurrentPair(pendingInputHardBlockReason);
            pendingInputHardBlockAfterDrain = false;
            pendingInputHardBlockPairIndex = -1;
            pendingInputHardBlockReason = "";
        }

        if (shouldRecheckFocusedPair()) {
            sendMsg("Finished batch for " + currentPair.name + ". Rechecking focused pair.");
            scannedPairs = 0;
            startPairScan(currentPairIndex);
        } else {
            if (!inputBlockedPairs.contains(currentPairIndex)) sendMsg("Finished batch for " + currentPair.name + ". Checking next pair.");
            startPairScan(currentPairIndex + 1);
        }
    }

    private boolean shouldRecheckFocusedPair() {
        return focusedPairRestocking.get()
            && currentPair != null
            && currentPair.active
            && !blockedPairs.contains(currentPairIndex)
            && !inputBlockedPairs.contains(currentPairIndex);
    }

    private List<BlockPos> buildTargetsForPurpose() {
        return switch (purpose) {
            case SCAN_DEPOSIT, DEPOSIT_ITEMS -> buildStorageRegion(currentPair.depositCorner1, currentPair.depositCorner2);
            case WITHDRAW_INPUT -> buildStorageRegion(currentPair.inputCorner1, currentPair.inputCorner2);
            default -> new ArrayList<>();
        };
    }

    private void handleMissingTargetsForPurpose() {
        switch (purpose) {
            case SCAN_DEPOSIT -> {
                loopTemporaryBlockedSkips++;
                logError("missing_targets", "reason=" + quoteLogValue("no supported deposit containers found"));
                blockCurrentPair("Temporary deposit issue: no supported deposit containers found. Check deposit region and access.");
                startPairScan(currentPairIndex + 1);
            }
            case WITHDRAW_INPUT -> {
                logError("missing_targets", "reason=" + quoteLogValue("no supported input containers found"));
                if (hasCurrentCargo()) queueInputHardBlockAfterDrain("No supported input containers remain.");
                else hardBlockInputCurrentPair("No supported input containers found.");
                finishBatchAndAdvance();
            }
            case DEPOSIT_ITEMS -> {
                loopTemporaryBlockedSkips++;
                logError("missing_targets", "reason=" + quoteLogValue("no supported deposit containers found while depositing"));
                blockCurrentPair("Temporary deposit issue: no supported deposit containers found while carrying this pair.");
                if (hasCurrentCargo()) waitWithCurrentCargo("Deposit storage is unavailable while carrying this pair.");
                else finishBatchAndAdvance();
            }
            default -> finishBatchAndAdvance();
        }
    }

    private boolean openBlock(BlockPos target) {
        OpenAttemptDiagnostics diagnostics = beginOpenAttemptDiagnostics(target);
        if (mc.level == null) {
            diagnostics.preflight = "world_unavailable";
            return false;
        }
        if (mc.player == null) {
            diagnostics.preflight = "player_unavailable";
            return false;
        }
        if (mc.gameMode == null) {
            diagnostics.preflight = "game_mode_unavailable";
            return false;
        }
        if (target == null) {
            diagnostics.preflight = "target_unavailable";
            return false;
        }

        BlockEntity blockEntity = mc.level.getBlockEntity(target);
        if (blockEntity == null && !(mc.level.getBlockState(target).getBlock() instanceof EnderChestBlock)) {
            diagnostics.preflight = "supported_block_entity_missing";
            return false;
        }

        Vec3 hitVec = Vec3.atCenterOf(target);
        BlockHitResult hit = new BlockHitResult(hitVec, Direction.UP, target, false);
        diagnostics.preflight = "rotation_queued";
        Rotations.rotate(Rotations.getYaw(hitVec), Rotations.getPitch(hitVec), () -> {
            if (mc.gameMode == null || mc.player == null) {
                diagnostics.interactionResult = "callback_context_unavailable";
                if (diagnostics.failureLogged) logLateOpenInteraction(diagnostics);
                return;
            }

            InteractionResult result = mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
            diagnostics.interactionDispatched = true;
            diagnostics.interactionResult = interactionResultName(result);
            diagnostics.consumesAction = result != null && result.consumesAction();
            if (diagnostics.failureLogged) logLateOpenInteraction(diagnostics);
        });
        return true;
    }

    private void retryOrBlock(String message) {
        openAttempts++;
        if (openAttempts >= openRetries.get()) {
            String trace = activeOpenTraceId > 0 ? "trace=" + activeOpenTraceId + " " : "";
            logError("open_failed", trace + "attempts=" + openAttempts + " " + targetInfo(openTarget) + " reason=" + quoteLogValue(sanitizeLogReason(message)));
            clearOpenFailureTrace();
            if (purpose == OpenPurpose.SNAPSHOT_BANK) {
                sendMsg("Could not inspect ender chest bank. Continuing inventory-only for " + currentPair.name + ".");
                bankAllowedForCurrentPair = false;
                closeScreen();
                startInputWithdrawWithCapacity();
                return;
            }
            if (purpose == OpenPurpose.WITHDRAW_INPUT) {
                closeScreen();
                targetIndex++;
                slotIndex = 0;
                openAttempts = 0;
                if (targetIndex >= activeTargets.size()) {
                    if (hasCurrentCargo()) queueInputHardBlockAfterDrain("Input containers are inaccessible or out of full shulker kits.");
                    else hardBlockInputCurrentPair("Input containers are inaccessible or out of full shulker kits.");
                    finishBatchAndAdvance();
                } else {
                    sendMsg("Input container " + targetIndex + " of " + activeTargets.size() + " failed to open after retries. Trying next input container.");
                    state = RunState.OPENING;
                }
                return;
            }
            loopTemporaryBlockedSkips++;
            blockCurrentPair(message);
            if (purpose == OpenPurpose.BANK_ITEMS && !activeInventorySlots.isEmpty()) {
                startDepositItems();
            } else if ((purpose == OpenPurpose.DEPOSIT_ITEMS || purpose == OpenPurpose.UNBANK_ITEMS) && hasCurrentCargo()) {
                waitWithCurrentCargo("Waiting to drain current pair before checking another pair.");
            } else {
                finishBatchAndAdvance();
            }
        } else {
            tickTimer = 0;
            state = RunState.OPENING;
        }
    }

    private List<BlockPos> buildStorageRegion(BlockPos corner1, BlockPos corner2) {
        List<BlockPos> list = new ArrayList<>();
        if (mc.level == null) return list;
        Set<Long> seenDoubleChests = new HashSet<>();

        BlockPos min = new BlockPos(Math.min(corner1.getX(), corner2.getX()), Math.min(corner1.getY(), corner2.getY()), Math.min(corner1.getZ(), corner2.getZ()));
        BlockPos max = new BlockPos(Math.max(corner1.getX(), corner2.getX()), Math.max(corner1.getY(), corner2.getY()), Math.max(corner1.getZ(), corner2.getZ()));

        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            BlockState state = mc.level.getBlockState(pos);
            Block block = state.getBlock();
            if (block instanceof ChestBlock) {
                ChestType type = state.getValue(ChestBlock.TYPE);
                if (type == ChestType.SINGLE) {
                    list.add(pos.immutable());
                } else {
                    Direction facing = state.getValue(ChestBlock.FACING);
                    Direction connectedDirection = type == ChestType.LEFT ? facing.getClockWise() : facing.getCounterClockWise();
                    BlockPos connected = pos.relative(connectedDirection);
                    long key = Math.min(pos.asLong(), connected.asLong());
                    if (seenDoubleChests.add(key)) list.add(pos.immutable());
                }
            } else if (block instanceof BarrelBlock || block instanceof ShulkerBoxBlock) {
                list.add(pos.immutable());
            }
        }

        return list;
    }

    private BlockPos findNearestEnderChest(BlockPos approach) {
        if (mc.level == null || mc.player == null) return null;

        BlockPos center = isConfiguredPos(approach) ? approach : mc.player.blockPosition();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        int radius = 5;

        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-radius, -radius, -radius), center.offset(radius, radius, radius))) {
            if (!(mc.level.getBlockState(pos).getBlock() instanceof EnderChestBlock)) continue;

            double distance = mc.player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = pos.immutable();
            }
        }

        return best;
    }

    private int getContainerSlots(AbstractContainerMenu menu) {
        return Math.max(0, menu.slots.size() - PLAYER_MENU_SLOTS);
    }

    private AbstractContainerMenu currentMenu() {
        if (mc.gui.screen() instanceof AbstractContainerScreen<?> screen) return screen.getMenu();
        return null;
    }

    private void click(AbstractContainerMenu menu, int slot) {
        if (mc.gameMode != null && mc.player != null) {
            mc.gameMode.handleContainerInput(menu.containerId, slot, 0, ContainerInput.QUICK_MOVE, mc.player);
        }
    }

    private boolean waitActionDelay() {
        tickTimer++;
        if (tickTimer < actionDelay.get()) return true;
        tickTimer = 0;
        return false;
    }

    private boolean isFullShulker(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (!(stack.getItem() instanceof BlockItem blockItem) || !(blockItem.getBlock() instanceof ShulkerBoxBlock)) return false;

        ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
        if (contents == null) return false;

        int nonEmpty = 0;
        for (ItemStack item : contents.nonEmptyItemCopyStream().toList()) nonEmpty++;
        return nonEmpty >= 27;
    }

    private int findEmptyContainerSlot(AbstractContainerMenu menu, int containerSlots) {
        for (int i = 0; i < containerSlots; i++) {
            if (menu.slots.get(i).getItem().isEmpty()) return i;
        }
        return -1;
    }

    private void reserveInitialInventory() {
        reservedInventorySlots.clear();
        if (mc.player == null) return;

        for (int i = 0; i < Math.min(PLAYER_MENU_SLOTS, mc.player.getInventory().getContainerSize()); i++) {
            if (!mc.player.getInventory().getItem(i).isEmpty()) reservedInventorySlots.add(i);
        }
    }

    private void refreshActiveInventorySlots(AbstractContainerMenu menu, int containerSlots) {
        activeInventorySlots.removeIf(index -> {
            ItemStack stack = index < mc.player.getInventory().getContainerSize() ? mc.player.getInventory().getItem(index) : ItemStack.EMPTY;
            return stack.isEmpty();
        });

        for (int i = containerSlots; i < menu.slots.size(); i++) {
            Slot slot = menu.slots.get(i);
            int index = slot.getContainerSlot();
            if (index >= 0 && index < PLAYER_MENU_SLOTS && !reservedInventorySlots.contains(index) && isFullShulker(slot.getItem())) {
                activeInventorySlots.add(index);
            }
        }
    }

    private int activeInventoryCount(AbstractContainerMenu menu, int containerSlots) {
        refreshActiveInventorySlots(menu, containerSlots);
        return activeInventorySlots.size();
    }

    private int cargoCount() {
        return activeInventorySlots.size() + activeBankSlots.size();
    }

    private boolean hasOpenManagedInventorySlot(AbstractContainerMenu menu, int containerSlots) {
        for (int i = containerSlots; i < menu.slots.size(); i++) {
            Slot slot = menu.slots.get(i);
            int index = slot.getContainerSlot();
            if (index >= 0 && index < PLAYER_MENU_SLOTS && !reservedInventorySlots.contains(index) && slot.getItem().isEmpty()) return true;
        }
        return false;
    }

    private int findActivePlayerMenuSlot(AbstractContainerMenu menu, int containerSlots) {
        for (int i = containerSlots; i < menu.slots.size(); i++) {
            Slot slot = menu.slots.get(i);
            int index = slot.getContainerSlot();
            if (activeInventorySlots.contains(index) && isFullShulker(slot.getItem())) return i;
        }
        return -1;
    }

    private void snapshotBankReservedSlots(AbstractContainerMenu menu, int bankSlots) {
        reservedBankSlots.clear();
        bankSlotCount = bankSlots;
        for (int i = 0; i < bankSlots; i++) {
            if (!menu.slots.get(i).getItem().isEmpty()) reservedBankSlots.add(i);
        }
        bankSnapshotTaken = true;
    }

    private void refreshActiveBankSlots(AbstractContainerMenu menu, int bankSlots) {
        activeBankSlots.removeIf(slot -> slot >= bankSlots || menu.slots.get(slot).getItem().isEmpty());
        for (int i = 0; i < bankSlots; i++) {
            if (!reservedBankSlots.contains(i) && isFullShulker(menu.slots.get(i).getItem())) activeBankSlots.add(i);
        }
    }

    private int findEmptyBankSlot(AbstractContainerMenu menu, int bankSlots) {
        for (int i = 0; i < bankSlots; i++) {
            if (!reservedBankSlots.contains(i) && menu.slots.get(i).getItem().isEmpty()) return i;
        }
        return -1;
    }

    private int findActiveBankSlot(AbstractContainerMenu menu, int bankSlots) {
        for (int i = 0; i < bankSlots; i++) {
            if (activeBankSlots.contains(i) && isFullShulker(menu.slots.get(i).getItem())) return i;
        }
        return -1;
    }

    private int availableManagedInventorySlots() {
        if (mc.player == null) return 0;

        int available = 0;
        for (int i = 0; i < Math.min(PLAYER_MENU_SLOTS, mc.player.getInventory().getContainerSize()); i++) {
            if (!reservedInventorySlots.contains(i) && mc.player.getInventory().getItem(i).isEmpty()) available++;
        }
        return available;
    }

    private boolean canUseBankForCurrentPair() {
        if (!bankAllowedForCurrentPair || !bankSnapshotTaken || currentPair == null) return false;
        return isConfiguredPos(enderChestApproachForHome(resolveInputHome(currentPair)))
            && isConfiguredPos(enderChestApproachForHome(resolveDepositHome(currentPair)));
    }

    private int availableBankCapacityForCurrentPair() {
        if (!canUseBankForCurrentPair()) return 0;
        return Math.max(0, bankSlotCount - reservedBankSlots.size() - activeBankSlots.size());
    }

    private boolean needsNavigation(BlockPos pos) {
        return isConfiguredPos(pos) && mc.player != null && horizontalDistanceToApproachSqr(pos) > APPROACH_TOLERANCE_SQ;
    }

    private boolean isAtApproach(BlockPos pos) {
        return isConfiguredPos(pos) && mc.player != null && horizontalDistanceToApproachSqr(pos) <= APPROACH_TOLERANCE_SQ;
    }

    private boolean canWalkWithinSameResolvedHome(String home) {
        return !normalizeHome(home).isEmpty() && findCurrentKnownApproach(home) != null;
    }

    private KnownApproach findCurrentKnownApproach(String home) {
        String normalizedHome = normalizeHome(home);
        if (normalizedHome.isEmpty()) return null;

        for (KnownApproach approach : knownApproaches()) {
            if (normalizeHome(approach.home()).equals(normalizedHome) && isAtApproach(approach.pos())) return approach;
        }

        return null;
    }

    private String detectedCurrentHomeOr(String fallbackHome) {
        KnownApproach approach = findCurrentKnownApproach(fallbackHome);
        return approach == null ? normalizeHome(fallbackHome) : approach.home();
    }

    private double horizontalDistanceToApproachSqr(BlockPos pos) {
        Vec3 center = net.minecraft.world.phys.Vec3.atCenterOf(pos);
        double x = mc.player.getX() - center.x;
        double z = mc.player.getZ() - center.z;
        return x * x + z * z;
    }

    private void startNavigation() {
        ensureNavigationInput();
        state = RunState.NAVIGATING;
    }

    private void ensureNavigationInput() {
        if (mc.player == null) return;
        if (navInput == null) navInput = new CustomPlayerInput();
        if (mc.player.input != navInput) {
            previousInput = mc.player.input;
            mc.player.input = navInput;
        }
    }

    private void stopNavigation() {
        if (navInput != null) navInput.stop();
        if (mc.player != null && previousInput != null && mc.player.input == navInput) mc.player.input = previousInput;
        previousInput = null;
    }

    private void closeScreen() {
        if (mc.player != null && mc.gui.screen() != null) mc.player.closeContainer();
    }

    private List<String> resolvedHomes() {
        LinkedHashSet<String> homes = new LinkedHashSet<>();
        for (KitPair pair : pairs) {
            String inputHome = normalizeHome(resolveInputHome(pair));
            String depositHome = normalizeHome(resolveDepositHome(pair));
            if (!inputHome.isEmpty()) homes.add(inputHome);
            if (!depositHome.isEmpty()) homes.add(depositHome);
        }
        return new ArrayList<>(homes);
    }

    private BlockPos enderChestApproachForHome(String home) {
        return enderChestApproachesByHome.getOrDefault(normalizeHome(home), BlockPos.ZERO);
    }

    private void setEnderChestApproachForHome(String home, BlockPos pos) {
        String normalizedHome = normalizeHome(home);
        if (normalizedHome.isEmpty()) return;

        if (isConfiguredPos(pos)) enderChestApproachesByHome.put(normalizedHome, pos);
        else enderChestApproachesByHome.remove(normalizedHome);
    }

    private void syncPairStatusCache() {
        Set<String> names = new HashSet<>();
        for (KitPair pair : pairs) {
            names.add(pair.name);
            pairStatusFor(pair);
        }
        pairStatusCache.keySet().removeIf(name -> !names.contains(name));
    }

    private PairStatusCache pairStatusFor(KitPair pair) {
        return pairStatusCache.computeIfAbsent(pair.name, ignored -> new PairStatusCache());
    }

    private void markPairBlocked(KitPair pair) {
        if (pair == null) return;
        pairStatusFor(pair).blocked = true;
    }

    private void clearBlockedPairStatuses() {
        List<KitPair> scanPairs = runtimePairsForScan();
        for (int i = 0; i < scanPairs.size(); i++) {
            pairStatusFor(scanPairs.get(i)).blocked = inputBlockedPairs.contains(i);
        }
    }

    private void setInputStatus(KitPair pair, String value) {
        if (pair != null) pairStatusFor(pair).input = value;
    }

    private void setDepositStatus(KitPair pair, int demand) {
        if (pair != null) pairStatusFor(pair).deposit = demand <= 0 ? "full" : "needs " + demand;
    }

    private void setDepositStatusText(KitPair pair, String value) {
        if (pair != null) pairStatusFor(pair).deposit = value;
    }

    private void resetLoopSkipCounters() {
        loopInputBlockedSkips = 0;
        loopInvalidConfigSkips = 0;
        loopTemporaryBlockedSkips = 0;
        loopInactiveSkips = 0;
    }

    private String noRunnableWorkMessage() {
        boolean hasInput = loopInputBlockedSkips > 0;
        boolean hasInvalid = loopInvalidConfigSkips > 0;
        boolean hasTemporary = loopTemporaryBlockedSkips > 0;
        boolean hasInactive = loopInactiveSkips > 0;
        int kinds = (hasInput ? 1 : 0) + (hasInvalid ? 1 : 0) + (hasTemporary ? 1 : 0) + (hasInactive ? 1 : 0);

        if (hasInactive && kinds == 1) {
            return "No runnable deposit work: all configured pairs are inactive. Turn at least one pair back on and restart THM Stash mover.";
        }
        if (hasInput && kinds == 1) {
            return "No runnable deposit work: all pairs needing work are blocked by empty or inaccessible input. Toggle off/on after restocking or fixing input access.";
        }
        if (hasInvalid && kinds == 1) {
            return "No runnable deposit work: configured pairs are incomplete or invalid. Check pair homes, regions, and approaches.";
        }
        if (hasTemporary && kinds == 1) {
            return "No runnable deposit work: pairs are temporarily blocked by runtime failures. Check access to configured storage.";
        }
        return "No runnable deposit work: pairs are inactive, blocked by input issues, invalid configuration, or temporary failures. Check the StashMover error log for details.";
    }

    private void logError(String event, String details) {
        if (!enableErrorLog.get()) return;

        StringBuilder builder = new StringBuilder();
        builder.append("event=").append(event);
        if (currentPair != null) builder.append(" pair=").append(quoteLogValue(currentPair.name));

        String side = currentLogSide();
        if (!side.isEmpty()) builder.append(" side=").append(side);

        String home = currentLogHome(side);
        if (!home.isEmpty()) builder.append(" home=").append(quoteLogValue(home));
        builder.append(' ').append(tpsInfo());

        if (details != null && !details.isBlank()) builder.append(' ').append(details);
        THMStashMoverErrorLog.log(true, builder.toString());
    }

    private void logGlobalError(String event, String details) {
        if (!enableErrorLog.get()) return;

        StringBuilder builder = new StringBuilder();
        builder.append("event=").append(event);
        builder.append(' ').append(tpsInfo());
        if (details != null && !details.isBlank()) builder.append(' ').append(details);
        THMStashMoverErrorLog.log(true, builder.toString());
    }

    private OpenAttemptDiagnostics beginOpenAttemptDiagnostics(BlockPos target) {
        OpenAttemptDiagnostics diagnostics = new OpenAttemptDiagnostics();
        tickTimer = 0;
        diagnostics.attemptId = ++openAttemptSequence;
        diagnostics.attemptNumber = openAttempts + 1;
        diagnostics.traceId = activeOpenTraceId;
        diagnostics.startedAt = System.currentTimeMillis();
        diagnostics.purpose = purpose.name().toLowerCase(Locale.ROOT);
        diagnostics.targetOrdinal = targetOrdinal();

        if (mc.level != null && target != null) {
            BlockState targetState = mc.level.getBlockState(target);
            BlockEntity blockEntity = mc.level.getBlockEntity(target);
            diagnostics.block = registryId(BuiltInRegistries.BLOCK.getKey(targetState.getBlock()));
            diagnostics.blockEntity = blockEntity == null
                ? "none"
                : registryId(BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(blockEntity.getType()));
            diagnostics.blockAbove = registryId(BuiltInRegistries.BLOCK.getKey(mc.level.getBlockState(target.above()).getBlock()));
            diagnostics.chunkReady = Boolean.toString(isChunkLoaded(target));
        }

        if (mc.player != null && target != null) {
            diagnostics.distance = String.format(Locale.ROOT, "%.1f", mc.player.position().distanceTo(Vec3.atCenterOf(target)));
            diagnostics.interactionRange = String.format(Locale.ROOT, "%.1f", mc.player.blockInteractionRange());
            double range = mc.player.blockInteractionRange();
            diagnostics.inRange = Boolean.toString(mc.player.distanceToSqr(Vec3.atCenterOf(target)) <= range * range);
            diagnostics.heldItem = mc.player.getMainHandItem().isEmpty()
                ? "minecraft:air"
                : registryId(BuiltInRegistries.ITEM.getKey(mc.player.getMainHandItem().getItem()));
            diagnostics.sneaking = Boolean.toString(mc.player.isShiftKeyDown());
            diagnostics.pingMs = playerPingInfo();
        }

        openAttemptDiagnostics = diagnostics;
        return diagnostics;
    }

    private void recordOpenAttemptFailure(String reason) {
        OpenAttemptDiagnostics diagnostics = openAttemptDiagnostics;
        if (diagnostics == null) return;

        if (activeOpenTraceId <= 0) activeOpenTraceId = ++openTraceSequence;
        diagnostics.traceId = activeOpenTraceId;
        diagnostics.failureLogged = true;

        logError("open_attempt_failed", openAttemptDetails(diagnostics)
            + " wait_ticks=" + tickTimer
            + " elapsed_ms=" + Math.max(0L, System.currentTimeMillis() - diagnostics.startedAt)
            + " reason=" + quoteLogValue(sanitizeLogReason(reason)));
    }

    private void recordOpenRecovery(AbstractContainerScreen<?> screen) {
        OpenAttemptDiagnostics diagnostics = openAttemptDiagnostics;
        if (activeOpenTraceId > 0 && diagnostics != null) {
            diagnostics.traceId = activeOpenTraceId;
            logError("open_recovered", openAttemptDetails(diagnostics)
                + " elapsed_ms=" + Math.max(0L, System.currentTimeMillis() - diagnostics.startedAt)
                + " current_screen=" + screenType(screen)
                + " current_menu=" + screenMenuType(screen));
        }

        clearOpenFailureTrace();
    }

    private void logLateOpenInteraction(OpenAttemptDiagnostics diagnostics) {
        if (diagnostics.traceId <= 0) return;

        logGlobalError("open_interaction_late",
            "trace=" + diagnostics.traceId
                + " attempt_id=" + diagnostics.attemptId
                + " attempt=" + diagnostics.attemptNumber + "/" + Math.max(1, openRetries.get())
                + " interaction=" + diagnostics.interactionResult
                + " consumes_action=" + diagnostics.consumesAction);
    }

    private String openAttemptDetails(OpenAttemptDiagnostics diagnostics) {
        String interaction = diagnostics.interactionDispatched ? diagnostics.interactionResult : "not_dispatched";
        String consumes = diagnostics.interactionDispatched ? Boolean.toString(diagnostics.consumesAction) : "unknown";
        String currentScreen = screenType(mc.gui.screen());
        String currentMenu = screenMenuType(mc.gui.screen());

        return "trace=" + diagnostics.traceId
            + " attempt_id=" + diagnostics.attemptId
            + " attempt=" + diagnostics.attemptNumber + "/" + Math.max(1, openRetries.get())
            + " purpose=" + diagnostics.purpose
            + optionalLogField("target", diagnostics.targetOrdinal)
            + " preflight=" + diagnostics.preflight
            + " interaction=" + interaction
            + " consumes_action=" + consumes
            + " packet_seen=" + diagnostics.openPacketSeen
            + " packet_cancelled=" + diagnostics.openPacketCancelled
            + " packet_menu=" + diagnostics.packetMenu
            + " screen_event_seen=" + diagnostics.screenEventSeen
            + " screen_event_cancelled=" + diagnostics.screenEventCancelled
            + " observed_screen=" + diagnostics.observedScreen
            + " observed_menu=" + diagnostics.observedMenu
            + " current_screen=" + currentScreen
            + " current_menu=" + currentMenu
            + " block=" + diagnostics.block
            + " block_entity=" + diagnostics.blockEntity
            + " block_above=" + diagnostics.blockAbove
            + " chunk_ready=" + diagnostics.chunkReady
            + " in_range=" + diagnostics.inRange
            + " distance=" + diagnostics.distance
            + " interaction_range=" + diagnostics.interactionRange
            + " held_item=" + diagnostics.heldItem
            + " sneaking=" + diagnostics.sneaking
            + " ping_ms=" + diagnostics.pingMs;
    }

    private String optionalLogField(String name, String value) {
        return value == null || value.isEmpty() ? "" : " " + name + "=" + value;
    }

    private String playerPingInfo() {
        if (mc.player == null || mc.getConnection() == null) return "unknown";
        PlayerInfo info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
        return info == null ? "unknown" : Integer.toString(Math.max(0, info.getLatency()));
    }

    private String interactionResultName(InteractionResult result) {
        if (result == null) return "null";
        if (result == InteractionResult.SUCCESS) return "success";
        if (result == InteractionResult.SUCCESS_SERVER) return "success_server";
        if (result == InteractionResult.CONSUME) return "consume";
        if (result == InteractionResult.FAIL) return "fail";
        if (result == InteractionResult.PASS) return "pass";
        return "other";
    }

    private String screenType(Screen screen) {
        if (screen == null) return "none";
        String name = screen.getClass().getSimpleName();
        return name.isEmpty() ? "unknown" : name.replaceAll("[^A-Za-z0-9_$.-]", "_");
    }

    private String screenMenuType(Screen screen) {
        if (!(screen instanceof AbstractContainerScreen<?> containerScreen)) return "none";
        return registryId(BuiltInRegistries.MENU.getKey(containerScreen.getMenu().getType()));
    }

    private String registryId(Identifier id) {
        if (id == null) return "unknown";
        return id.getNamespace() + ":" + id.getPath();
    }

    private void clearOpenFailureTrace() {
        activeOpenTraceId = 0;
        openAttemptDiagnostics = null;
    }

    private void resetOpenDiagnosticSession() {
        openAttemptSequence = 0;
        openTraceSequence = 0;
        clearOpenFailureTrace();
    }

    private String currentLogSide() {
        return switch (purpose) {
            case SCAN_DEPOSIT, SNAPSHOT_BANK, DEPOSIT_ITEMS, UNBANK_ITEMS -> "deposit";
            case WITHDRAW_INPUT, BANK_ITEMS -> "input";
            default -> switch (travelSide) {
                case INPUT -> "input";
                case DEPOSIT -> "deposit";
                default -> "";
            };
        };
    }

    private String currentLogHome(String side) {
        if (currentPair == null) return "";
        if ("input".equals(side)) return resolveInputHome(currentPair);
        if ("deposit".equals(side)) return resolveDepositHome(currentPair);
        return "";
    }

    private String targetInfo(BlockPos target) {
        StringBuilder builder = new StringBuilder();
        String ordinal = targetOrdinal();
        if (!ordinal.isEmpty()) builder.append("target=").append(ordinal);

        String distance = targetDistanceInfo(target);
        if (!distance.isEmpty()) {
            if (!builder.isEmpty()) builder.append(' ');
            builder.append("distance=").append(distance);
        }

        return builder.toString();
    }

    private String targetOrdinal() {
        if (activeTargets == null || activeTargets.isEmpty()) return "";
        int ordinal = Math.min(Math.max(targetIndex + 1, 1), activeTargets.size());
        return ordinal + "/" + activeTargets.size();
    }

    private String targetDistanceInfo(BlockPos target) {
        if (target == null || mc.player == null) return "";

        double distance = mc.player.position().distanceTo(Vec3.atCenterOf(target));
        return String.format(Locale.ROOT, "%.1f", distance);
    }

    private String tpsInfo() {
        float tps = TickRate.INSTANCE.getTickRate();
        if (!Float.isFinite(tps)) return "tps=unknown";
        return "tps=" + String.format(Locale.ROOT, "%.1f", tps);
    }

    private String quoteLogValue(String value) {
        if (value == null) return "\"\"";
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private String sanitizeLogReason(String value) {
        if (value == null) return "";
        return value.replaceAll("-?\\d+\\s*,\\s*-?\\d+\\s*,\\s*-?\\d+", "[pos]");
    }

    private void startRuntimeAfterMainServerGate() {
        mainServerStartPending = false;
        reserveInitialInventory();
        scannedPairs = 0;
        startPairScan(scanStartIndex);
    }

    private boolean isMainServerReady() {
        return mainServerGateReason() == null;
    }

    public boolean isManagingThmHwyMonitorReconnect() {
        return hwyMonitorReconnectHandling.get();
    }

    public void onMonitorReconnectMainServerReady(long cycleId, String contextTag) {
        if (!isActive()) return;

        String gateReason = mainServerGateReason();
        if (gateReason == null) {
            resumeFromMainServerGate();
            if (mainServerStartPending) startRuntimeAfterMainServerGate();
        }

        sendMsg("THM Highway Monitor finished reconnect handling for StashMover (cycle " + cycleId + ").");
    }

    public void onMonitorReconnectFailure(long cycleId, String reason, String detail) {
        if (!isActive()) return;

        stopNavigation();
        closeScreen();
        pauseScreenWorkForMainServerGate();
        String suffix = detail == null || detail.isBlank() ? "" : ": " + detail;
        sendMsg("THM Highway Monitor reconnect handling failed (" + reason + ")" + suffix + ". StashMover remains paused until main server/survival is available.");
    }

    private void syncThmHwyMonitorReconnectOnActivate() {
        thmHwyMonitorStartedByStashMover = false;
        if (!hwyMonitorReconnectHandling.get()) return;

        THMHwyMonitor monitor = getThmHwyMonitor();
        if (monitor == null) {
            sendMsg("THM Highway Monitor reconnect handling is enabled, but THM Highway Monitor is unavailable. Continuing with pause-only main-server handling.");
            return;
        }

        if (!monitor.isActive()) {
            monitor.toggle();
            thmHwyMonitorStartedByStashMover = monitor.isActive();
        }

        if (!monitor.isActive()) {
            sendMsg("THM Highway Monitor reconnect handling is enabled, but THM Highway Monitor could not be enabled. Continuing with pause-only main-server handling.");
            return;
        }

        monitor.beginStashMoverReconnectHandling(this, "stash-mover-activate");
    }

    private void armThmHwyMonitorReconnect(String source) {
        if (!hwyMonitorReconnectHandling.get()) return;

        THMHwyMonitor monitor = getThmHwyMonitor();
        if (monitor == null || !monitor.isActive()) return;

        monitor.beginStashMoverReconnectHandling(this, source);
    }

    private void releaseThmHwyMonitorReconnectOnDeactivate() {
        THMHwyMonitor monitor = getThmHwyMonitor();
        if (monitor != null) {
            monitor.clearStashMoverReconnectHandling(this, "stash-mover-deactivate");
            if (thmHwyMonitorStartedByStashMover && monitor.isActive()) monitor.toggle();
        }
        thmHwyMonitorStartedByStashMover = false;
    }

    private THMHwyMonitor getThmHwyMonitor() {
        try {
            return Modules.get().get(THMHwyMonitor.class);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private String mainServerGateReason() {
        if (mc.player == null) return "player unavailable";
        if (mc.level == null) return "world unavailable";

        if (hwyMonitorReconnectHandling.get()) {
            ServerStatusHandler.ServerState serverState = ServerStatusHandler.getInstance().getCommittedState();
            if (serverState != ServerStatusHandler.ServerState.MAIN_SERVER) return "server state is " + serverState.name();
        }

        ClientPacketListener connection = mc.getConnection();
        if (connection == null) return "server connection unavailable";

        PlayerInfo self = connection.getPlayerInfo(mc.player.getUUID());
        if (self == null) return "player info unavailable";

        GameType gameType = self.getGameMode();
        if (gameType != GameType.SURVIVAL) return "game mode is " + String.valueOf(gameType);
        return null;
    }

    private void pauseForMainServerGate(String reason) {
        long now = System.currentTimeMillis();
        if (!mainServerGatePaused) {
            mainServerGatePaused = true;
            mainServerGatePausedAt = now;
            logGlobalError("main_server_gate_paused", "reason=" + quoteLogValue(sanitizeLogReason(reason)));
        }

        stopNavigation();
        closeScreen();
        pauseScreenWorkForMainServerGate();

        if (mc.player != null && now - lastMainServerGateMsg >= MAIN_SERVER_GATE_WARN_INTERVAL_MS) {
            lastMainServerGateMsg = now;
            sendMsg("Paused until main server/survival: " + reason);
        }
    }

    private void resumeFromMainServerGate() {
        if (!mainServerGatePaused) return;

        long pausedFor = Math.max(0L, System.currentTimeMillis() - mainServerGatePausedAt);
        if (stateStartTime > 0L) stateStartTime += pausedFor;
        mainServerGatePaused = false;
        mainServerGatePausedAt = 0L;
        lastMainServerGateMsg = 0L;
        lastCountdownMsg = 0L;
        logGlobalError("main_server_gate_resumed", "reason=" + quoteLogValue("main server/survival detected"));
        sendMsg("Main server/survival detected. Resuming.");
    }

    private void pauseScreenWorkForMainServerGate() {
        switch (state) {
            case WAITING_FOR_SCREEN, SCANNING_DEPOSIT, SNAPSHOTTING_BANK, WITHDRAWING_INPUT, BANKING, DEPOSITING, UNBANKING -> {
                tickTimer = 0;
                state = RunState.OPENING;
            }
            default -> {}
        }
    }

    private void resetTeleportValidation() {
        teleportStartX = 0;
        teleportStartY = 0;
        teleportStartZ = 0;
        teleportStartPosValid = false;
        teleportValidated = false;
    }

    private void captureTeleportStart() {
        resetTeleportValidation();
        if (mc.player == null) return;

        teleportStartX = mc.player.getX();
        teleportStartY = mc.player.getY();
        teleportStartZ = mc.player.getZ();
        teleportStartPosValid = true;
    }

    private boolean hasTeleportPositionValidation() {
        if (isAtApproach(travelApproach)) return true;
        if (findCurrentKnownApproach(travelHome) != null) return true;
        if (!teleportStartPosValid || mc.player == null) return false;

        double dx = mc.player.getX() - teleportStartX;
        double dy = mc.player.getY() - teleportStartY;
        double dz = mc.player.getZ() - teleportStartZ;
        return dx * dx + dy * dy + dz * dz >= TELEPORT_DETECT_DISTANCE_SQ;
    }

    private boolean isTravelWorldReady() {
        if (mc.player == null || mc.level == null || mc.getConnection() == null) return false;
        return isChunkLoaded(mc.player.blockPosition()) && (!isConfiguredPos(travelApproach) || isChunkLoaded(travelApproach));
    }

    private boolean isChunkLoaded(BlockPos pos) {
        if (pos == null || mc.level == null) return false;

        try {
            return mc.level.getChunkSource().getChunk(pos.getX() >> 4, pos.getZ() >> 4, ChunkStatus.FULL, false) != null;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private List<KnownApproach> knownApproaches() {
        List<KnownApproach> approaches = new ArrayList<>();

        for (KitPair pair : pairs) {
            addKnownApproach(approaches, pair.inputApproachPos, resolveInputHome(pair), TravelSide.INPUT);
            addKnownApproach(approaches, pair.depositApproachPos, resolveDepositHome(pair), TravelSide.DEPOSIT);
        }

        for (Map.Entry<String, BlockPos> entry : enderChestApproachesByHome.entrySet()) {
            if (resolvedHomes().contains(entry.getKey())) {
                addKnownApproach(approaches, entry.getValue(), entry.getKey(), TravelSide.UNKNOWN);
            }
        }

        return approaches;
    }

    private void addKnownApproach(List<KnownApproach> approaches, BlockPos pos, String home, TravelSide side) {
        String normalizedHome = normalizeHome(home);
        if (isConfiguredPos(pos) && !normalizedHome.isEmpty()) approaches.add(new KnownApproach(pos, normalizedHome, side));
    }

    private String normalizeHome(String home) {
        return home == null ? "" : home.trim();
    }

    private void failCurrentTravel(String reason) {
        closeScreen();
        stopNavigation();
        resetTeleportValidation();
        loopTemporaryBlockedSkips++;
        logError("travel_failed", "reason=" + quoteLogValue(sanitizeLogReason(reason)));

        if (currentPair != null) blockCurrentPair(reason);
        else sendMsg(reason);

        if (hasCurrentCargo()) {
            waitWithCurrentCargo("Travel failed while carrying current pair.");
        } else {
            startPairScan(currentPairIndex + 1);
        }
    }

    private boolean isPairRunnable(KitPair pair) {
        if (pair.name.isEmpty()) return false;
        if (resolveInputHome(pair).isEmpty() || resolveDepositHome(pair).isEmpty()) return false;
        if (sharedInputHome && !isConfiguredPos(pair.inputApproachPos)) return false;
        if (sharedDepositHome && !isConfiguredPos(pair.depositApproachPos)) return false;
        if (!isConfiguredPos(pair.inputCorner1) || !isConfiguredPos(pair.inputCorner2)) return false;
        if (!isConfiguredPos(pair.depositCorner1) || !isConfiguredPos(pair.depositCorner2)) return false;
        return true;
    }

    private String invalidPairReason(KitPair pair) {
        if (pair == null) return "pair unavailable";
        if (pair.name.isEmpty()) return "pair name is empty";
        if (resolveInputHome(pair).isEmpty()) return "input home is empty";
        if (resolveDepositHome(pair).isEmpty()) return "deposit home is empty";
        if (sharedInputHome && !isConfiguredPos(pair.inputApproachPos)) return "input approach is not configured";
        if (sharedDepositHome && !isConfiguredPos(pair.depositApproachPos)) return "deposit approach is not configured";
        if (!isConfiguredPos(pair.inputCorner1) || !isConfiguredPos(pair.inputCorner2)) return "input region is not configured";
        if (!isConfiguredPos(pair.depositCorner1) || !isConfiguredPos(pair.depositCorner2)) return "deposit region is not configured";
        return "unknown invalid config";
    }

    private boolean validateStartupConfig() {
        if (sharedInputHome && sharedInputHomeName.trim().isEmpty()) {
            sendMsg("Shared input home is enabled but blank. Set shared input home before enabling.");
            return false;
        }
        if (sharedDepositHome && sharedDepositHomeName.trim().isEmpty()) {
            sendMsg("Shared deposit home is enabled but blank. Set shared deposit home before enabling.");
            return false;
        }
        return true;
    }

    private String resolveInputHome(KitPair pair) {
        return sharedInputHome ? sharedInputHomeName.trim() : pair.inputHome.trim();
    }

    private String resolveDepositHome(KitPair pair) {
        return sharedDepositHome ? sharedDepositHomeName.trim() : pair.depositHome.trim();
    }

    private void blockCurrentPair(String reason) {
        if (currentPair != null) {
            blockedPairs.add(currentPairIndex);
            markPairBlocked(currentPair);
            sendMsg("Blocked " + currentPair.name + ": " + reason);
        }
    }

    private void hardBlockInputCurrentPair(String reason) {
        if (currentPair == null) return;

        inputBlockedPairs.add(currentPairIndex);
        blockedPairs.add(currentPairIndex);
        setInputStatus(currentPair, "empty");
        markPairBlocked(currentPair);
        logError("input_blocked", "reason=" + quoteLogValue(sanitizeLogReason(reason)));
        sendMsg("Input blocked " + currentPair.name + ": " + reason + " Toggle the module off/on after restocking to check it again.");
    }

    private void queueInputHardBlockAfterDrain(String reason) {
        setInputStatus(currentPair, "empty");
        pendingInputHardBlockAfterDrain = true;
        pendingInputHardBlockPairIndex = currentPairIndex;
        pendingInputHardBlockReason = reason;
        logError("input_block_queued", "reason=" + quoteLogValue(sanitizeLogReason(reason)));
    }

    private boolean hasCurrentCargo() {
        return !activeInventorySlots.isEmpty() || !activeBankSlots.isEmpty();
    }

    private void waitWithCurrentCargo(String reason) {
        closeScreen();
        stopNavigation();
        stateStartTime = System.currentTimeMillis();
        state = RunState.WAITING_WITH_CARGO;
        sendMsg(reason + " Retrying this pair in " + rescanDelay.get() + "s.");
    }

    private void tickWaitWithCargo() {
        if (System.currentTimeMillis() - stateStartTime < rescanDelay.get() * 1000L) return;
        if (!activeInventorySlots.isEmpty()) startDepositItems();
        else if (!activeBankSlots.isEmpty()) startUnbanking();
        else finishBatchAndAdvance();
    }

    private boolean isConfiguredPos(BlockPos pos) {
        return pos != null && !pos.equals(BlockPos.ZERO);
    }

    private void ensureDefaultPair() {
        if (pairs.isEmpty()) pairs.add(new KitPair("default"));
    }

    private void captureRuntimePairs() {
        runtimePairs.clear();
        for (KitPair pair : pairs) runtimePairs.add(pair.copy());
    }

    private List<KitPair> runtimePairsForScan() {
        if (isActive() && !runtimePairs.isEmpty()) return runtimePairs;
        return pairs;
    }

    private KitPair activePair() {
        ensureDefaultPair();
        int index = indexOfPair(selectedPairName);
        return index >= 0 ? pairs.get(index) : pairs.get(0);
    }

    private int indexOfPair(String name) {
        for (int i = 0; i < pairs.size(); i++) {
            if (pairs.get(i).name.equals(name)) return i;
        }
        return -1;
    }

    private String[] pairNames() {
        if (pairs.isEmpty()) return new String[] { "default" };

        String[] names = new String[pairs.size()];
        for (int i = 0; i < pairs.size(); i++) names[i] = pairs.get(i).name;
        return names;
    }

    private String[] blankPlusPairNames() {
        String[] names = pairNames();
        String[] values = new String[names.length + 1];
        values[0] = "";
        System.arraycopy(names, 0, values, 1, names.length);
        return values;
    }

    private String validSelection(String selection, String[] values) {
        for (String value : values) {
            if (value.equals(selection)) return selection;
        }
        return "";
    }

    private String formatPos(BlockPos pos) {
        return isConfiguredPos(pos) ? pos.toShortString() : "Not set";
    }

    private void clearPairActionSelections(String pairName) {
        if (deletePairSelection.equals(pairName)) deletePairSelection = "";
        if (renamePairSelection.equals(pairName)) renamePairSelection = "";
    }

    private void rebuildPairSettings() {
        settings.groups.clear();
        pairSettingGroups.clear();

        settings.groups.add(sgGeneral);
        settings.groups.add(sgLogging);
        settings.groups.add(sgTiming);
        settings.invalidate();
    }

    private String pairGroupName(KitPair pair) {
        return "Pair - " + pair.name;
    }

    private String sanitizePairName(String rawName) {
        return rawName == null ? "" : rawName.trim();
    }

    private void sendMsg(String msg) {
        if (mc.player != null) {
            mc.player.sendSystemMessage(Component.literal("\u00a76[THMStashMover]\u00a7r " + msg));
        }
    }

    private class PairBlockPosEdit extends WHorizontalList {
        private final boolean clickButton;
        private final boolean setHereButton;
        private final Consumer<BlockPos> onChanged;

        private WTextBox textBoxX;
        private WTextBox textBoxY;
        private WTextBox textBoxZ;
        private Screen previousScreen;
        private BlockPos value;
        private boolean clicking;

        private PairBlockPosEdit(BlockPos value, boolean clickButton, boolean setHereButton, Consumer<BlockPos> onChanged) {
            this.value = value == null ? BlockPos.ZERO : value;
            this.clickButton = clickButton;
            this.setHereButton = setHereButton;
            this.onChanged = onChanged;
        }

        @Override
        public void init() {
            addTextBoxes();

            if (!Utils.canUpdate()) return;

            if (clickButton) {
                WButton click = add(theme.button("Click")).widget();
                click.action = this::startClickPick;
            }

            if (setHereButton) {
                WButton here = add(theme.button("Set Here")).widget();
                here.action = () -> {
                    if (mc.player == null) return;
                    updateValue(mc.player.blockPosition());
                    clear();
                    init();
                };
            }
        }

        @EventHandler
        private void onStartBreakingBlock(StartBreakingBlockEvent event) {
            if (!clicking) return;

            clicking = false;
            event.cancel();
            MeteorClient.EVENT_BUS.unsubscribe(this);
            mc.gui.setScreen(previousScreen);
        }

        @EventHandler
        private void onInteractBlock(InteractBlockEvent event) {
            if (!clicking) return;
            if (event.result.getType() == HitResult.Type.MISS) return;

            updateValue(event.result.getBlockPos());
            clear();
            init();

            clicking = false;
            event.cancel();
            MeteorClient.EVENT_BUS.unsubscribe(this);
            mc.gui.setScreen(previousScreen);
        }

        private void startClickPick() {
            Modules.get().get(Marker.class).info("Click!\nRight click to pick a new position.\nLeft click to cancel.");

            clicking = true;
            MeteorClient.EVENT_BUS.subscribe(this);
            previousScreen = mc.gui.screen();
            mc.gui.setScreen(null);
        }

        private void addTextBoxes() {
            textBoxX = add(theme.textBox(Integer.toString(value.getX()), this::filter)).minWidth(75).widget();
            textBoxY = add(theme.textBox(Integer.toString(value.getY()), this::filter)).minWidth(75).widget();
            textBoxZ = add(theme.textBox(Integer.toString(value.getZ()), this::filter)).minWidth(75).widget();

            textBoxX.actionOnUnfocused = () -> updateValue(parseTextBox(textBoxX, value.getY(), value.getZ(), Axis.X));
            textBoxY.actionOnUnfocused = () -> updateValue(parseTextBox(textBoxY, value.getX(), value.getZ(), Axis.Y));
            textBoxZ.actionOnUnfocused = () -> updateValue(parseTextBox(textBoxZ, value.getX(), value.getY(), Axis.Z));
        }

        private BlockPos parseTextBox(WTextBox textBox, int first, int second, Axis axis) {
            if (textBox.get().isEmpty()) return BlockPos.ZERO;

            try {
                int parsed = Integer.parseInt(textBox.get());
                return switch (axis) {
                    case X -> new BlockPos(parsed, first, second);
                    case Y -> new BlockPos(first, parsed, second);
                    case Z -> new BlockPos(first, second, parsed);
                };
            } catch (NumberFormatException ignored) {
                return value;
            }
        }

        private boolean filter(String text, char c) {
            if (c == '-' && text.isEmpty()) return true;
            if (!Character.isDigit(c)) return false;

            try {
                Integer.parseInt(text + c);
                return true;
            } catch (NumberFormatException ignored) {
                return false;
            }
        }

        private void updateValue(BlockPos value) {
            BlockPos newValue = value == null ? BlockPos.ZERO : value;
            if (newValue.equals(this.value)) return;

            this.value = newValue;
            onChanged.accept(newValue);
        }
    }

    private enum Axis {
        X,
        Y,
        Z
    }

    private static final class OpenAttemptDiagnostics {
        private int attemptId;
        private int attemptNumber;
        private int traceId;
        private long startedAt;
        private String purpose = "none";
        private String targetOrdinal = "";
        private String preflight = "not_started";
        private boolean interactionDispatched;
        private String interactionResult = "pending";
        private boolean consumesAction;
        private boolean openPacketSeen;
        private boolean openPacketCancelled;
        private String packetMenu = "none";
        private boolean screenEventSeen;
        private boolean screenEventCancelled;
        private String observedScreen = "none";
        private String observedMenu = "none";
        private String block = "unknown";
        private String blockEntity = "unknown";
        private String blockAbove = "unknown";
        private String chunkReady = "unknown";
        private String inRange = "unknown";
        private String distance = "unknown";
        private String interactionRange = "unknown";
        private String heldItem = "unknown";
        private String sneaking = "unknown";
        private String pingMs = "unknown";
        private boolean failureLogged;
    }

    private enum RunState {
        IDLE,
        WAITING_FOR_TP_COOLDOWN,
        TELEPORTING,
        WAITING_FOR_TELEPORT,
        NAVIGATING,
        OPENING,
        WAITING_FOR_SCREEN,
        SCANNING_DEPOSIT,
        SNAPSHOTTING_BANK,
        WITHDRAWING_INPUT,
        BANKING,
        DEPOSITING,
        UNBANKING,
        WAITING_TO_RESCAN,
        WAITING_WITH_CARGO
    }

    private enum OpenPurpose {
        NONE,
        SCAN_DEPOSIT,
        SNAPSHOT_BANK,
        WITHDRAW_INPUT,
        BANK_ITEMS,
        DEPOSIT_ITEMS,
        UNBANK_ITEMS
    }

    private enum TravelSide {
        UNKNOWN,
        INPUT,
        DEPOSIT
    }

    public record PairHudRow(String pairName, String inputStatus, String depositStatus) {}

    private record KnownApproach(BlockPos pos, String home, TravelSide side) {}

    private static class PairStatusCache {
        String input = "unknown";
        String deposit = "unknown";
        boolean blocked;

        String inputText() {
            return blocked && "unknown".equals(input) ? "blocked" : input;
        }

        String depositText() {
            return blocked && "unknown".equals(deposit) ? "blocked" : deposit;
        }
    }

    private static class KitPair {
        String name;
        boolean active = true;
        String inputHome = "stash-input";
        BlockPos inputCorner1 = BlockPos.ZERO;
        BlockPos inputCorner2 = BlockPos.ZERO;
        BlockPos inputApproachPos = BlockPos.ZERO;
        String depositHome = "stash-deposit";
        BlockPos depositCorner1 = BlockPos.ZERO;
        BlockPos depositCorner2 = BlockPos.ZERO;
        BlockPos depositApproachPos = BlockPos.ZERO;

        KitPair(String name) {
            this.name = name;
        }

        CompoundTag toTag() {
            CompoundTag tag = new CompoundTag();
            tag.putString("name", name);
            tag.putBoolean("active", active);
            tag.putString("inputHome", inputHome);
            tag.putLong("inputCorner1", inputCorner1.asLong());
            tag.putLong("inputCorner2", inputCorner2.asLong());
            tag.putLong("inputApproachPos", inputApproachPos.asLong());
            tag.putString("depositHome", depositHome);
            tag.putLong("depositCorner1", depositCorner1.asLong());
            tag.putLong("depositCorner2", depositCorner2.asLong());
            tag.putLong("depositApproachPos", depositApproachPos.asLong());
            return tag;
        }

        static KitPair fromTag(CompoundTag tag) {
            KitPair pair = new KitPair(tag.getStringOr("name", "default"));
            pair.active = tag.getBooleanOr("active", true);
            pair.inputHome = tag.getStringOr("inputHome", "stash-input");
            pair.inputCorner1 = BlockPos.of(tag.getLongOr("inputCorner1", BlockPos.ZERO.asLong()));
            pair.inputCorner2 = BlockPos.of(tag.getLongOr("inputCorner2", BlockPos.ZERO.asLong()));
            pair.inputApproachPos = BlockPos.of(tag.getLongOr("inputApproachPos", BlockPos.ZERO.asLong()));
            pair.depositHome = tag.getStringOr("depositHome", "stash-deposit");
            pair.depositCorner1 = BlockPos.of(tag.getLongOr("depositCorner1", BlockPos.ZERO.asLong()));
            pair.depositCorner2 = BlockPos.of(tag.getLongOr("depositCorner2", BlockPos.ZERO.asLong()));
            pair.depositApproachPos = BlockPos.of(tag.getLongOr("depositApproachPos", BlockPos.ZERO.asLong()));
            return pair;
        }

        KitPair copy() {
            KitPair pair = new KitPair(name);
            pair.active = active;
            pair.inputHome = inputHome;
            pair.inputCorner1 = inputCorner1;
            pair.inputCorner2 = inputCorner2;
            pair.inputApproachPos = inputApproachPos;
            pair.depositHome = depositHome;
            pair.depositCorner1 = depositCorner1;
            pair.depositCorner2 = depositCorner2;
            pair.depositApproachPos = depositApproachPos;
            return pair;
        }
    }
}
