package xyz.thm.addon.modules;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.meteorclient.utils.world.Dimension;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.item.BoatItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import xyz.thm.addon.THMAddon;
import xyz.thm.addon.utils.THMUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.PriorityQueue;
import java.util.UUID;

public class HighwayChecker extends Module {
    private static final int TRUST_RADIUS_BLOCKS = 48;
    private static final int BOAT_RECOVERY_TIMEOUT_TICKS = 100;
    private static final int SAMPLE_Y_LOW = 118;
    private static final int SAMPLE_Y_HIGH = 119;
    private static final int PATH_Y_LOW = 120;
    private static final int PATH_Y_HIGH = 122;
    private static final int MAX_ALLOWED_Y = 122;
    private static final double MIN_PLAYER_TRAVEL_Y = 118.6;
    private static final double MAX_PLAYER_TRAVEL_Y = 121.1;
    private static final int PATH_LOOKAHEAD = 8;
    private static final int PATH_RADIUS = 10;
    private static final int PATH_NODE_LIMIT = 300;
    private static final int STALL_TIMEOUT_TICKS = 200;
    private static final int SECTION_HYSTERESIS_SAMPLES = 2;
    private static final int MIN_BOUNDARY_GAP_BLOCKS = 8;
    private static final int BOAT_MOUNT_GRACE_TICKS = 20;
    private static final int OUTPUT_RECOVERY_TIMEOUT_TICKS = 60;
    private static final double STEP_STRAIGHT_COST = 1.0;
    private static final double STEP_DIAGONAL_COST = Math.sqrt(2.0);
    private static final double MINE_SECTION_COST = 100.0;
    private static final double MINE_BLOCK_COST = 12.0;
    private static final double OFF_LINE_PENALTY = 0.35;
    private static final double START_ALIGNMENT_TOLERANCE = 5.0;
    private static final double REANCHOR_DISTANCE_THRESHOLD = 1.5;
    private static final double BOAT_INTERACT_RANGE = 5.25;
    private static final DateTimeFormatter SESSION_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH.mm.ss").withZone(ZoneId.systemDefault());
    private static final int[][] NEIGHBOR_DIRS = {
        { 1, 0 }, { 1, 1 }, { 0, 1 }, { -1, 1 },
        { -1, 0 }, { -1, -1 }, { 0, -1 }, { 1, -1 }
    };

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgLogging = settings.createGroup("Logging");

    private final Setting<Boolean> notifyChat = sgGeneral.add(new BoolSetting.Builder()
        .name("chat-notify")
        .description("Show section and failure events in Meteor chat.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> notifyDesktop = sgGeneral.add(new BoolSetting.Builder()
        .name("desktop-notify")
        .description("Send desktop notifications for section and failure events.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> debugLog = sgLogging.add(new BoolSetting.Builder()
        .name("debug-log")
        .description("Write bounded runtime diagnostics to a debug file.")
        .defaultValue(true)
        .build()
    );

    private TravelDirection direction;
    private WorkLine line;
    private String sessionId;
    private String activeSectionId;
    private int nextSectionNumber;
    private boolean sectionOpen;
    private SectionPhase sectionPhase;
    private BoundaryType lastBoundaryType;
    private long lastSampleKey = Long.MIN_VALUE;
    private long lastBoundaryKey = Long.MIN_VALUE;
    private boolean lastSampleTrusted = true;
    private int boatRecoveryTicks;
    private int boatMountGraceTicks;
    private int lastProgressAge;
    private double lastMeasuredY;
    private String lastPathDecision = "";
    private String pendingStopReason;
    private int startSectionStreak;
    private int stopSectionStreak;
    private PendingOutputWrite pendingOutputWrite;
    private Path csvPath;
    private Path sectionsPath;
    private Path debugPath;

    public HighwayChecker() {
        super(THMAddon.MAIN, "Highway Checker", "Travels Nether highways in a boat and records trusted obsidian section boundaries.");
    }

    @Override
    public void onActivate() {
        if (mc.player == null || mc.world == null) return;

        if (THMUtils.isNot6B6T() && !mc.isInSingleplayer()) {
            error("Highway Checker is intended for 6B6T highway runs.");
            toggle();
            return;
        }

        if (World.NETHER != mc.world.getRegistryKey()) {
            error("Highway Checker can only be used in the Nether.");
            toggle();
            return;
        }

        direction = TravelDirection.fromYaw(mc.player.getYaw());
        line = direction.line;
        double distanceToLine = distanceToLockedLine(mc.player.getX(), mc.player.getZ(), line);
        if (distanceToLine > START_ALIGNMENT_TOLERANCE) {
            error("Too far from the inferred %s highway line (%.2f blocks).", direction.label, distanceToLine);
            toggle();
            return;
        }

        sessionId = UUID.randomUUID().toString().substring(0, 8);
        activeSectionId = null;
        nextSectionNumber = 1;
        sectionOpen = false;
        sectionPhase = SectionPhase.LOOKING_FOR_ANY;
        lastBoundaryType = null;
        lastSampleKey = Long.MIN_VALUE;
        lastBoundaryKey = Long.MIN_VALUE;
        boatRecoveryTicks = 0;
        boatMountGraceTicks = 0;
        lastPathDecision = "";
        lastSampleTrusted = true;
        lastProgressAge = mc.player.age;
        lastMeasuredY = mc.player.getY();
        pendingStopReason = null;
        startSectionStreak = 0;
        stopSectionStreak = 0;
        pendingOutputWrite = null;

        try {
            initializeOutputFiles();
        } catch (IOException e) {
            error("Failed to initialize Highway Checker output files.");
            toggle();
            return;
        }

        alignPlayerToLockedLine();
        stopMovementKeys();
        debug("activate", "session=%s direction=%s line=%s", sessionId, direction.label, line);
        notifyEvent("Started Highway Checker on %s.", direction.label);
    }

    @Override
    public void onDeactivate() {
        stopMovementKeys();

        if (sectionOpen) {
            BlockPos center = currentCenterBlock();
            closeSection(center, sampleCurrentSlice(center), pendingStopReason == null ? "manual-stop" : pendingStopReason);
        }

        if (sessionId != null) debug("deactivate", "session=%s", sessionId);

        direction = null;
        line = null;
        sessionId = null;
        activeSectionId = null;
        nextSectionNumber = 0;
        sectionOpen = false;
        sectionPhase = SectionPhase.LOOKING_FOR_ANY;
        lastBoundaryType = null;
        lastSampleKey = Long.MIN_VALUE;
        lastBoundaryKey = Long.MIN_VALUE;
        boatRecoveryTicks = 0;
        boatMountGraceTicks = 0;
        lastPathDecision = "";
        lastMeasuredY = 0.0;
        pendingStopReason = null;
        startSectionStreak = 0;
        stopSectionStreak = 0;
        pendingOutputWrite = null;
        csvPath = null;
        sectionsPath = null;
        debugPath = null;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null || direction == null || line == null) return;

        if (World.NETHER != mc.world.getRegistryKey()) {
            fail("Left the Nether while Highway Checker was active.");
            return;
        }

        if (!Utils.canUpdate()) {
            stopMovementKeys();
            return;
        }

        if (!recoverPendingOutputWrite()) return;

        if (!isRidingBoat()) {
            handleBoatRecovery();
            return;
        }

        boatRecoveryTicks = 0;
        boatMountGraceTicks = 0;
        if (handleMountedBoatHeightRecovery()) return;
        sampleIfAdvanced();
        if (mc.player.age - lastProgressAge > STALL_TIMEOUT_TICKS) {
            fail("Highway Checker stalled without progress.");
            return;
        }

        StepDecision step = chooseStep();
        if (step == null) {
            fail("Unable to find a traversable section path.");
            return;
        }

        if (!step.mode.equals(lastPathDecision)) {
            lastPathDecision = step.mode;
            debug("path", "mode=%s next=(%d,%d) obstacles=%d", step.mode, step.dx, step.dz, step.obstacleCount);
        }

        if (step.requiresMining) {
            mineSection(step.probe);
            return;
        }

        steerToward(step.dx, step.dz);
    }

    private void handleBoatRecovery() {
        stopMovementKeys();
        boatRecoveryTicks++;
        if (boatRecoveryTicks == 1) debug("boat-recovery", "started");
        if (boatRecoveryTicks > BOAT_RECOVERY_TIMEOUT_TICKS) {
            fail("Boat recovery timed out.");
            return;
        }

        BoatEntity nearbyBoat = findNearestBoatEntity();
        if (nearbyBoat != null) {
            if (tryMountBoat(nearbyBoat)) {
                boatRecoveryTicks = 0;
                boatMountGraceTicks = 0;
                lastProgressAge = mc.player != null ? mc.player.age : lastProgressAge;
                debug("boat-recovery", "mounted-existing-boat");
                return;
            }
            if (boatMountGraceTicks > 0) {
                boatMountGraceTicks--;
                return;
            }
            steerTowardEntity(nearbyBoat);
            return;
        }

        ItemEntity boatDrop = findNearestBoatDrop();
        if (boatDrop != null && !hasBoatItem()) {
            steerTowardEntity(boatDrop);
            return;
        }

        FindItemResult boatItem = findBoatItem();
        if (!boatItem.found()) return;

        BoatPlacementCandidate placement = findBoatPlacementCandidate();
        if (placement == null) return;

        if (!boatItem.isHotbar()) {
            int hotbarSlot = findEmptyHotbarSlot();
            if (hotbarSlot == -1) {
                debug("boat-recovery", "no-empty-hotbar-slot-for-boat");
                return;
            }
            InvUtils.move().from(boatItem.slot()).toHotbar(hotbarSlot);
            boatItem = findBoatItem();
            if (!boatItem.found()) return;
        }

        if (!InvUtils.swap(boatItem.slot(), true)) return;

        mc.interactionManager.interactBlock(
            mc.player,
            Hand.MAIN_HAND,
            placement.hitResult()
        );
        mc.player.swingHand(Hand.MAIN_HAND);
        boatMountGraceTicks = BOAT_MOUNT_GRACE_TICKS;
        debug("boat-recovery", "placed-boat floor=%s", formatPos(placement.floorAnchor()));
    }

    private void sampleIfAdvanced() {
        BlockPos center = currentCenterBlock();
        long key = pack(center.getX(), center.getZ());
        if (key == lastSampleKey) return;
        lastSampleKey = key;
        lastProgressAge = mc.player.age;

        SampleSnapshot snapshot = sampleCurrentSlice(center);
        if (!snapshot.trusted()) {
            if (lastSampleTrusted) {
                debug("sample", "skipped-untrusted center=%s", formatPos(center));
                lastSampleTrusted = false;
            }
            startSectionStreak = 0;
            stopSectionStreak = 0;
            return;
        }
        lastSampleTrusted = true;

        if (sectionPhase == SectionPhase.LOOKING_FOR_ANY) {
            if (snapshot.startsSection()) {
                startSectionStreak++;
                stopSectionStreak = 0;
                if (startSectionStreak >= SECTION_HYSTERESIS_SAMPLES && canRecordBoundaryAt(center) && canRecordBoundaryType(BoundaryType.START)) {
                    openSection(center, snapshot);
                    startSectionStreak = 0;
                }
            } else if (snapshot.stopsSection()) {
                stopSectionStreak++;
                startSectionStreak = 0;
                if (stopSectionStreak >= SECTION_HYSTERESIS_SAMPLES && canRecordBoundaryAt(center) && canRecordBoundaryType(BoundaryType.STOP)) {
                    closeSection(center, snapshot, "detected-stop");
                    stopSectionStreak = 0;
                }
            } else {
                startSectionStreak = 0;
                stopSectionStreak = 0;
            }
        } else if (sectionPhase == SectionPhase.LOOKING_FOR_START) {
            if (snapshot.startsSection()) {
                startSectionStreak++;
                stopSectionStreak = 0;
                if (startSectionStreak >= SECTION_HYSTERESIS_SAMPLES && canRecordBoundaryAt(center) && canRecordBoundaryType(BoundaryType.START)) {
                    openSection(center, snapshot);
                    startSectionStreak = 0;
                }
            } else {
                startSectionStreak = 0;
            }
        } else {
            if (snapshot.stopsSection()) {
                stopSectionStreak++;
                startSectionStreak = 0;
                if (stopSectionStreak >= SECTION_HYSTERESIS_SAMPLES && canRecordBoundaryAt(center) && canRecordBoundaryType(BoundaryType.STOP)) {
                    closeSection(center, snapshot, "detected-stop");
                    stopSectionStreak = 0;
                }
            } else {
                stopSectionStreak = 0;
            }
        }
    }

    private SampleSnapshot sampleCurrentSlice(BlockPos center) {
        ArrayList<BlockPos> y118Positions = new ArrayList<>(5);
        ArrayList<BlockPos> y119Positions = new ArrayList<>(5);
        ArrayList<String> y118Blocks = new ArrayList<>(5);
        ArrayList<String> y119Blocks = new ArrayList<>(5);
        boolean trusted = true;

        int[] perp = direction.perpendicular();
        for (int offset = -2; offset <= 2; offset++) {
            int sx = center.getX() + perp[0] * offset;
            int sz = center.getZ() + perp[1] * offset;
            BlockPos low = new BlockPos(sx, SAMPLE_Y_LOW, sz);
            BlockPos high = new BlockPos(sx, SAMPLE_Y_HIGH, sz);

            if (!isTrustedSamplePos(low) || !isTrustedSamplePos(high)) trusted = false;

            y118Positions.add(low);
            y119Positions.add(high);
            y118Blocks.add(blockId(mc.world.getBlockState(low)));
            y119Blocks.add(blockId(mc.world.getBlockState(high)));
        }

        boolean lowAllObs = allObsidian(y118Positions);
        boolean highAllObs = allObsidian(y119Positions);
        boolean lowAllNonObs = allNonObsidian(y118Positions);
        boolean highAllNonObs = allNonObsidian(y119Positions);

        return new SampleSnapshot(
            trusted,
            center,
            new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ()),
            lowAllObs || highAllObs,
            lowAllNonObs && highAllNonObs,
            y118Blocks,
            y119Blocks
        );
    }

    private StepDecision chooseStep() {
        BlockPos center = currentCenterBlock();
        BlockPos actual = mc.player.getBlockPos();
        int startX = actual.getX();
        int startZ = actual.getZ();
        int destX = center.getX() + direction.dx * PATH_LOOKAHEAD;
        int destZ = center.getZ() + direction.dz * PATH_LOOKAHEAD;
        return findAStarStep(startX, startZ, destX, destZ);
    }

    private StepDecision findAStarStep(int startX, int startZ, int destX, int destZ) {
        long startKey = pack(startX, startZ);
        PriorityQueue<AStarNode> open = new PriorityQueue<>(Comparator.comparingDouble(node -> node.f));
        HashMap<Long, Double> gScore = new HashMap<>();
        HashMap<Long, Long> cameFrom = new HashMap<>();
        HashMap<Long, SectionProbe> probes = new HashMap<>();
        HashSet<Long> closed = new HashSet<>();

        open.add(new AStarNode(startX, startZ, 0.0, octileDistance(startX, startZ, destX, destZ)));
        gScore.put(startKey, 0.0);

        long bestKey = startKey;
        double bestH = octileDistance(startX, startZ, destX, destZ);
        int expanded = 0;

        while (!open.isEmpty() && expanded < PATH_NODE_LIMIT) {
            AStarNode current = open.poll();
            long currentKey = pack(current.x, current.z);
            if (!closed.add(currentKey)) continue;
            expanded++;

            double h = octileDistance(current.x, current.z, destX, destZ);
            if (h < bestH) {
                bestH = h;
                bestKey = currentKey;
            }
            if (current.x == destX && current.z == destZ) {
                bestKey = currentKey;
                break;
            }

            for (int[] dir : NEIGHBOR_DIRS) {
                int nx = current.x + dir[0];
                int nz = current.z + dir[1];

                int rx = nx - startX;
                int rz = nz - startZ;
                if (rx * rx + rz * rz > PATH_RADIUS * PATH_RADIUS) continue;

                SectionProbe probe = probeTraversalSection(nx, nz);
                if (!probe.open() && !probe.mineable()) continue;

                long neighborKey = pack(nx, nz);
                if (closed.contains(neighborKey)) continue;

                double tentativeG = current.g
                    + ((Math.abs(dir[0]) == 1 && Math.abs(dir[1]) == 1) ? STEP_DIAGONAL_COST : STEP_STRAIGHT_COST)
                    + lineDistancePenalty(nx, nz)
                    + (probe.mineable() ? MINE_SECTION_COST + probe.obstacles().size() * MINE_BLOCK_COST : 0.0);

                if (tentativeG >= gScore.getOrDefault(neighborKey, Double.POSITIVE_INFINITY)) continue;

                gScore.put(neighborKey, tentativeG);
                cameFrom.put(neighborKey, currentKey);
                probes.put(neighborKey, probe);
                double f = tentativeG + octileDistance(nx, nz, destX, destZ);
                open.add(new AStarNode(nx, nz, tentativeG, f));
            }
        }

        if (bestKey == startKey) return null;

        long stepKey = bestKey;
        while (cameFrom.containsKey(stepKey) && cameFrom.get(stepKey) != startKey) {
            stepKey = cameFrom.get(stepKey);
        }

        int sx = unpackX(stepKey);
        int sz = unpackZ(stepKey);
        SectionProbe probe = probes.getOrDefault(stepKey, probeTraversalSection(sx, sz));
        int dx = Integer.compare(sx, startX);
        int dz = Integer.compare(sz, startZ);
        String mode = probe.mineable() ? "mine-fallback" : (dx == direction.dx && dz == direction.dz ? "forward-open" : "detour-open");
        return new StepDecision(dx, dz, probe, probe.mineable(), mode, probe.obstacles().size());
    }

    private SectionProbe probeTraversalSection(int centerX, int centerZ) {
        ArrayList<BlockPos> obstacles = new ArrayList<>();
        boolean nonMineable = false;

        for (BlockPos pos : getSectionProbeVolume(centerX, centerZ)) {
            BlockState state = mc.world.getBlockState(pos);
            if (state.isAir() || state.isReplaceable()) continue;

            if (!isMineableBlock(state, pos)) nonMineable = true;
            else obstacles.add(pos);
        }

        boolean open = obstacles.isEmpty() && !nonMineable && isTravelSpaceClear(centerX, centerZ);
        boolean mineable = !open && !nonMineable;
        return new SectionProbe(open, mineable, obstacles);
    }

    private void mineSection(SectionProbe probe) {
        if (probe.obstacles().isEmpty()) {
            fail("Mine fallback selected without mineable obstacles.");
            return;
        }

        BlockPos target = probe.obstacles().stream()
            .filter(pos -> mc.player.squaredDistanceTo(Vec3d.ofCenter(pos)) <= BOAT_INTERACT_RANGE * BOAT_INTERACT_RANGE)
            .min(Comparator.comparingDouble(pos -> mc.player.squaredDistanceTo(Vec3d.ofCenter(pos))))
            .orElse(null);

        if (target == null) {
            debug("mine", "no-reachable-obstacle");
            return;
        }

        FindItemResult tool = InvUtils.findFastestTool(mc.world.getBlockState(target));
        if (tool.found() && tool.slot() != mc.player.getInventory().getSelectedSlot()) InvUtils.swap(tool.slot(), false);
        BlockUtils.breakBlock(target, true);
        debug("mine", "target=%s", formatPos(target));
    }

    private boolean handleMountedBoatHeightRecovery() {
        if (mc.player == null) return false;

        mc.options.jumpKey.setPressed(false);
        mc.options.sprintKey.setPressed(false);

        double y = mc.player.getY();
        if (Math.abs(y - lastMeasuredY) >= 0.05) {
            lastMeasuredY = y;
            lastProgressAge = mc.player.age;
        }

        if (y < MIN_PLAYER_TRAVEL_Y) {
            mc.options.jumpKey.setPressed(true);
            steerToward(direction.dx, direction.dz);
            return true;
        }

        if (y > MAX_PLAYER_TRAVEL_Y) {
            mc.options.sprintKey.setPressed(true);
            steerToward(direction.dx, direction.dz);
            return true;
        }

        return false;
    }

    private void steerToward(int dx, int dz) {
        if (mc.player == null) return;
        if (distanceToLockedLine(mc.player.getX(), mc.player.getZ(), line) > REANCHOR_DISTANCE_THRESHOLD) {
            debug("path", "reanchor distance=%.2f", distanceToLockedLine(mc.player.getX(), mc.player.getZ(), line));
        }

        float yaw = yawForStep(dx, dz);
        mc.player.setYaw(yaw);
        Entity vehicle = mc.player.getVehicle();
        if (vehicle != null) vehicle.setYaw(yaw);

        mc.options.forwardKey.setPressed(true);
        mc.options.backKey.setPressed(false);
        mc.options.leftKey.setPressed(false);
        mc.options.rightKey.setPressed(false);
    }

    private void steerTowardEntity(Entity entity) {
        if (mc.player == null || entity == null) return;

        Vec3d delta = new Vec3d(
            entity.getX() - mc.player.getX(),
            entity.getY() - mc.player.getY(),
            entity.getZ() - mc.player.getZ()
        );
        float yaw = (float) Math.toDegrees(Math.atan2(-delta.x, delta.z));
        mc.player.setYaw(yaw);
        mc.options.forwardKey.setPressed(true);
        mc.options.backKey.setPressed(false);
        mc.options.leftKey.setPressed(false);
        mc.options.rightKey.setPressed(false);
    }

    private boolean tryMountBoat(BoatEntity boat) {
        if (mc.player == null || boat == null) return false;
        if (mc.player.squaredDistanceTo(boat) > BOAT_INTERACT_RANGE * BOAT_INTERACT_RANGE) return false;

        mc.interactionManager.interactEntity(mc.player, boat, Hand.MAIN_HAND);
        boatMountGraceTicks = BOAT_MOUNT_GRACE_TICKS;
        return isRidingBoat();
    }

    private BoatEntity findNearestBoatEntity() {
        if (mc.player == null || mc.world == null) return null;

        return mc.world.getEntitiesByClass(
            BoatEntity.class,
            new Box(mc.player.getBlockPos()).expand(6.0),
            boat -> boat.isAlive() && boat.getPassengerList().size() < 2
        ).stream().min(Comparator.comparingDouble(mc.player::squaredDistanceTo)).orElse(null);
    }

    private ItemEntity findNearestBoatDrop() {
        if (mc.player == null || mc.world == null) return null;

        return mc.world.getEntitiesByClass(
            ItemEntity.class,
            new Box(mc.player.getBlockPos()).expand(8.0),
            item -> isBoatItem(item.getStack().getItem())
        ).stream().min(Comparator.comparingDouble(mc.player::squaredDistanceTo)).orElse(null);
    }

    private boolean hasBoatItem() {
        return findBoatItem().found();
    }

    private FindItemResult findBoatItem() {
        return InvUtils.find(stack -> isBoatItem(stack.getItem()));
    }

    private boolean isBoatItem(Item item) {
        return item instanceof BoatItem;
    }

    private int findEmptyHotbarSlot() {
        if (mc.player == null) return -1;
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).isEmpty()) return i;
        }
        return -1;
    }

    private BoatPlacementCandidate findBoatPlacementCandidate() {
        if (mc.player == null || mc.world == null) return null;

        BlockPos playerPos = mc.player.getBlockPos();
        int[] floorYs = { 119, 118 };
        for (int floorY : floorYs) {
            for (int ox = -2; ox <= 2; ox++) {
                for (int oz = -2; oz <= 2; oz++) {
                    BlockPos floor = new BlockPos(playerPos.getX() + ox, floorY, playerPos.getZ() + oz);
                    if (!isValidBoatPlacementArea(floor)) continue;
                    return new BoatPlacementCandidate(
                        floor,
                        new BlockHitResult(Vec3d.ofCenter(floor), Direction.UP, floor, false),
                        floor.up()
                    );
                }
            }
        }
        return null;
    }

    private boolean isValidBoatPlacementArea(BlockPos floorAnchor) {
        if (mc.world == null) return false;

        for (int dx = 0; dx < 2; dx++) {
            for (int dz = 0; dz < 2; dz++) {
                BlockPos floor = floorAnchor.add(dx, 0, dz);
                if (!mc.world.getBlockState(floor).isSolidBlock(mc.world, floor)) return false;
            }
        }

        for (int dx = 0; dx < 2; dx++) {
            for (int dz = 0; dz < 2; dz++) {
                for (int y = 1; y <= 3; y++) {
                    BlockPos air = floorAnchor.add(dx, y, dz);
                    BlockState state = mc.world.getBlockState(air);
                    if (!state.isAir() && !state.isReplaceable()) return false;
                }
            }
        }

        return true;
    }

    private List<BlockPos> getSectionProbeVolume(int centerX, int centerZ) {
        ArrayList<BlockPos> positions = new ArrayList<>(15);
        int[] perp = direction.perpendicular();

        for (int offset = -2; offset <= 2; offset++) {
            int sx = centerX + perp[0] * offset;
            int sz = centerZ + perp[1] * offset;
            for (int y = PATH_Y_LOW; y <= PATH_Y_HIGH; y++) {
                positions.add(new BlockPos(sx, y, sz));
            }
        }

        return positions;
    }

    private boolean isTravelSpaceClear(int centerX, int centerZ) {
        if (mc.world == null) return false;

        for (BlockPos pos : getSectionProbeVolume(centerX, centerZ)) {
            BlockState state = mc.world.getBlockState(pos);
            if (!state.isAir() && !state.isReplaceable()) return false;
        }

        return true;
    }

    private boolean isTrustedSamplePos(BlockPos pos) {
        if (mc.player == null) return false;
        double dx = pos.getX() + 0.5 - mc.player.getX();
        double dz = pos.getZ() + 0.5 - mc.player.getZ();
        return Math.hypot(dx, dz) <= TRUST_RADIUS_BLOCKS;
    }

    private boolean isRidingBoat() {
        return mc.player != null && mc.player.getVehicle() instanceof BoatEntity;
    }

    private boolean allObsidian(List<BlockPos> positions) {
        for (BlockPos pos : positions) {
            if (!mc.world.getBlockState(pos).isOf(Blocks.OBSIDIAN)) return false;
        }
        return true;
    }

    private boolean allNonObsidian(List<BlockPos> positions) {
        for (BlockPos pos : positions) {
            if (mc.world.getBlockState(pos).isOf(Blocks.OBSIDIAN)) return false;
        }
        return true;
    }

    private boolean isMineableBlock(BlockState state, BlockPos pos) {
        if (state.isAir() || state.isReplaceable()) return true;
        if (state.isOf(Blocks.BEDROCK) || state.isOf(Blocks.OBSIDIAN) || state.isOf(Blocks.CRYING_OBSIDIAN)) return false;
        return state.getHardness(mc.world, pos) >= 0;
    }

    private void openSection(BlockPos center, SampleSnapshot snapshot) {
        activeSectionId = sessionId + "-S" + nextSectionNumber++;
        sectionOpen = true;
        if (!appendCoreLine(sectionsPath, "START," + activeSectionId + "," + center.getX() + "," + center.getY() + "," + center.getZ(), "section-start")) return;
        lastBoundaryKey = pack(center.getX(), center.getZ());
        sectionPhase = SectionPhase.LOOKING_FOR_STOP;
        lastBoundaryType = BoundaryType.START;
        writeCsvRow(BoundaryType.START, activeSectionId, snapshot);
        debug("section", "start id=%s center=%s", activeSectionId, formatPos(center));
        notifyEvent("Section %s started at %s.", activeSectionId, formatPos(center));
    }

    private void closeSection(BlockPos center, SampleSnapshot snapshot, String reason) {
        String sectionIdForRow = activeSectionId == null ? "" : activeSectionId;
        if (!appendCoreLine(sectionsPath, "STOP," + sectionIdForRow + "," + center.getX() + "," + center.getY() + "," + center.getZ(), "section-stop")) return;
        lastBoundaryKey = pack(center.getX(), center.getZ());
        sectionPhase = SectionPhase.LOOKING_FOR_START;
        lastBoundaryType = BoundaryType.STOP;
        writeCsvRow(BoundaryType.STOP, sectionIdForRow, snapshot);
        debug("section", "stop id=%s center=%s reason=%s", sectionIdForRow, formatPos(center), reason);
        notifyEvent("Section %s stopped at %s.", sectionIdForRow.isEmpty() ? "<none>" : sectionIdForRow, formatPos(center));
        activeSectionId = null;
        sectionOpen = false;
    }

    private boolean canRecordBoundaryAt(BlockPos center) {
        if (lastBoundaryKey == Long.MIN_VALUE) return true;

        int lastX = unpackX(lastBoundaryKey);
        int lastZ = unpackZ(lastBoundaryKey);
        int dx = center.getX() - lastX;
        int dz = center.getZ() - lastZ;
        return Math.hypot(dx, dz) >= MIN_BOUNDARY_GAP_BLOCKS;
    }

    private boolean canRecordBoundaryType(BoundaryType boundaryType) {
        return lastBoundaryType == null || lastBoundaryType != boundaryType;
    }

    private void writeCsvRow(BoundaryType boundaryType, String sectionId, SampleSnapshot snapshot) {
        if (csvPath == null) return;

        StringBuilder lineOut = new StringBuilder(256);
        lineOut.append(Instant.now()).append(',')
            .append(sessionId).append(',')
            .append(boundaryType.name()).append(',')
            .append(sectionId == null ? "" : sectionId).append(',')
            .append(direction.label).append(',')
            .append(snapshot.center().getX()).append(',')
            .append(snapshot.center().getY()).append(',')
            .append(snapshot.center().getZ()).append(',')
            .append(String.format(Locale.ROOT, "%.3f", snapshot.playerPos().x)).append(',')
            .append(String.format(Locale.ROOT, "%.3f", snapshot.playerPos().y)).append(',')
            .append(String.format(Locale.ROOT, "%.3f", snapshot.playerPos().z)).append(',')
            .append(boundaryType == BoundaryType.START);

        for (String block : snapshot.y118Blocks()) lineOut.append(',').append(block);
        for (String block : snapshot.y119Blocks()) lineOut.append(',').append(block);
        appendCoreLine(csvPath, lineOut.toString(), "csv-sample");
    }

    private void initializeOutputFiles() throws IOException {
        Path sessionCsv = THMAddon.GetConfigFile("highway-checker", "highway-checker-samples-" + SESSION_TIME_FORMAT.format(Instant.now()) + "-" + sessionId + ".csv").toPath();
        Path sections = THMAddon.GetConfigFile("highway-checker", "highway-checker-sections.txt").toPath();
        Path debug = THMAddon.GetConfigFile("highway-checker", "highway-checker-debug.log").toPath();

        csvPath = sessionCsv;
        sectionsPath = sections;
        debugPath = debug;

        Files.createDirectories(csvPath.getParent());
        if (!Files.exists(csvPath)) {
            Files.writeString(
                csvPath,
                "timestamp,session_id,boundary_type,section_id,direction,center_x,center_y,center_z,player_x,player_y,player_z,section_open,"
                    + "y118_m2,y118_m1,y118_0,y118_p1,y118_p2,"
                    + "y119_m2,y119_m1,y119_0,y119_p1,y119_p2"
                    + System.lineSeparator(),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE
            );
        }
    }

    private void appendLine(Path path, String line) {
        if (path == null) return;
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(
                path,
                line + System.lineSeparator(),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
                StandardOpenOption.APPEND
            );
        } catch (IOException e) {
            THMAddon.LOG.warn("Highway Checker failed to append to {}: {}", path, e.getMessage());
        }
    }

    private boolean appendCoreLine(Path path, String line, String kind) {
        if (path == null) return true;
        if (tryAppendLine(path, line)) {
            if (pendingOutputWrite != null && pendingOutputWrite.path().equals(path) && pendingOutputWrite.line().equals(line)) {
                debug("io", "recovered kind=%s path=%s", kind, path.getFileName());
                pendingOutputWrite = null;
            }
            return true;
        }

        if (pendingOutputWrite == null) {
            pendingOutputWrite = new PendingOutputWrite(path, line, kind, OUTPUT_RECOVERY_TIMEOUT_TICKS);
            debug("io", "pause-for-retry kind=%s path=%s", kind, path.getFileName());
        }
        return false;
    }

    private boolean recoverPendingOutputWrite() {
        if (pendingOutputWrite == null) return true;

        stopMovementKeys();
        PendingOutputWrite pending = pendingOutputWrite;
        if (tryAppendLine(pending.path(), pending.line())) {
            debug("io", "retry-success kind=%s path=%s", pending.kind(), pending.path().getFileName());
            pendingOutputWrite = null;
            return true;
        }

        int remaining = pending.ticksRemaining() - 1;
        if (remaining <= 0) {
            String kind = pending.kind();
            pendingOutputWrite = null;
            fail("Highway Checker %s logging failed after retry.", kind);
            return false;
        }

        pendingOutputWrite = new PendingOutputWrite(pending.path(), pending.line(), pending.kind(), remaining);
        return false;
    }

    private boolean tryAppendLine(Path path, String line) {
        if (path == null) return false;
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(
                path,
                line + System.lineSeparator(),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
                StandardOpenOption.APPEND
            );
            return true;
        } catch (IOException e) {
            THMAddon.LOG.warn("Highway Checker failed to append to {}: {}", path, e.getMessage());
            return false;
        }
    }

    private void debug(String event, String format, Object... args) {
        if (!debugLog.get() || debugPath == null) return;
        String line = String.format(Locale.ROOT, "%s session=%s event=%s %s",
            Instant.now(),
            sessionId == null ? "none" : sessionId,
            event,
            String.format(Locale.ROOT, format, args)
        );
        appendLine(debugPath, line);
    }

    private void notifyEvent(String format, Object... args) {
        String message = String.format(Locale.ROOT, format, args);
        if (notifyChat.get()) info(message);
        if (notifyDesktop.get()) THMUtils.Notify("Highway Checker", message);
    }

    private void fail(String format, Object... args) {
        String message = String.format(Locale.ROOT, format, args);
        pendingStopReason = "hard-fail";
        debug("fail", "%s", message);
        if (notifyDesktop.get()) THMUtils.Notify("Highway Checker", message);
        error(message);
        toggle();
    }

    private void alignPlayerToLockedLine() {
        if (mc.player == null || line == null) return;

        double[] projected = projectToLine(mc.player.getX(), mc.player.getZ(), line);
        mc.player.setVelocity(0.0, mc.player.getVelocity().y, 0.0);
        mc.player.setPosition(projected[0], mc.player.getY(), projected[1]);
        mc.player.setYaw(yawForStep(direction.dx, direction.dz));
    }

    private BlockPos currentCenterBlock() {
        if (mc.player == null || line == null) return BlockPos.ORIGIN;
        double[] projected = projectToLine(mc.player.getX(), mc.player.getZ(), line);
        return new BlockPos(floorToBlock(projected[0]), SAMPLE_Y_LOW, floorToBlock(projected[1]));
    }

    private double[] projectToLine(double x, double z, WorkLine workLine) {
        return closestPointOnLine(x, z, workLine.a, workLine.b, workLine.c);
    }

    private double distanceToLockedLine(double x, double z, WorkLine workLine) {
        return distanceToLine(x, z, workLine.a, workLine.b, workLine.c);
    }

    private double lineDistancePenalty(int x, int z) {
        return distanceToLockedLine(x + 0.5, z + 0.5, line) * OFF_LINE_PENALTY;
    }

    private static double distanceToLine(double x, double z, double a, double b, double c) {
        return Math.abs(a * x + b * z - c) / Math.sqrt(a * a + b * b);
    }

    private static double[] closestPointOnLine(double x, double z, double a, double b, double c) {
        double denom = a * a + b * b;
        if (denom == 0.0) return new double[] { x, z };

        double t = (a * x + b * z - c) / denom;
        return new double[] { x - a * t, z - b * t };
    }

    private static int floorToBlock(double value) {
        return MathHelper.floor(value);
    }

    private static long pack(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }

    private static int unpackX(long packed) {
        return (int) (packed >> 32);
    }

    private static int unpackZ(long packed) {
        return (int) packed;
    }

    private static double octileDistance(int x1, int z1, int x2, int z2) {
        int dx = Math.abs(x2 - x1);
        int dz = Math.abs(z2 - z1);
        int min = Math.min(dx, dz);
        int max = Math.max(dx, dz);
        return min * STEP_DIAGONAL_COST + (max - min) * STEP_STRAIGHT_COST;
    }

    private static float yawForStep(int dx, int dz) {
        return (float) Math.toDegrees(Math.atan2(-dx, dz));
    }

    private static String blockId(BlockState state) {
        return Registries.BLOCK.getId(state.getBlock()).toString();
    }

    private static String formatPos(BlockPos pos) {
        return pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    private void stopMovementKeys() {
        if (mc.options == null) return;
        mc.options.forwardKey.setPressed(false);
        mc.options.backKey.setPressed(false);
        mc.options.leftKey.setPressed(false);
        mc.options.rightKey.setPressed(false);
        mc.options.jumpKey.setPressed(false);
        mc.options.sprintKey.setPressed(false);
        mc.options.sneakKey.setPressed(false);
    }

    private enum WorkLine {
        X_AXIS("x-axis", 0.0, 1.0, 0.5),
        Z_AXIS("z-axis", 1.0, 0.0, 0.5),
        NW_SE("nw-se", 1.0, -1.0, 0.0),
        NE_SW("ne-sw", 1.0, 1.0, 1.0);

        private final String label;
        private final double a;
        private final double b;
        private final double c;

        WorkLine(String label, double a, double b, double c) {
            this.label = label;
            this.a = a;
            this.b = b;
            this.c = c;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private enum TravelDirection {
        EAST("east", 1, 0, WorkLine.X_AXIS),
        SOUTH_EAST("south-east", 1, 1, WorkLine.NW_SE),
        SOUTH("south", 0, 1, WorkLine.Z_AXIS),
        SOUTH_WEST("south-west", -1, 1, WorkLine.NE_SW),
        WEST("west", -1, 0, WorkLine.X_AXIS),
        NORTH_WEST("north-west", -1, -1, WorkLine.NW_SE),
        NORTH("north", 0, -1, WorkLine.Z_AXIS),
        NORTH_EAST("north-east", 1, -1, WorkLine.NE_SW);

        private final String label;
        private final int dx;
        private final int dz;
        private final WorkLine line;

        TravelDirection(String label, int dx, int dz, WorkLine line) {
            this.label = label;
            this.dx = dx;
            this.dz = dz;
            this.line = line;
        }

        private int[] perpendicular() {
            return new int[] { -dz, dx };
        }

        private double normX() {
            return dx == 0 || dz == 0 ? dx : dx / Math.sqrt(2.0);
        }

        private double normZ() {
            return dx == 0 || dz == 0 ? dz : dz / Math.sqrt(2.0);
        }

        private static TravelDirection fromYaw(float yaw) {
            double radians = Math.toRadians(yaw);
            double fx = -Math.sin(radians);
            double fz = Math.cos(radians);

            TravelDirection best = SOUTH;
            double bestDot = Double.NEGATIVE_INFINITY;
            for (TravelDirection value : values()) {
                double dot = fx * value.normX() + fz * value.normZ();
                if (dot > bestDot) {
                    bestDot = dot;
                    best = value;
                }
            }
            return best;
        }
    }

    private record AStarNode(int x, int z, double g, double f) {}

    private record SampleSnapshot(
        boolean trusted,
        BlockPos center,
        Vec3d playerPos,
        boolean startsSection,
        boolean stopsSection,
        List<String> y118Blocks,
        List<String> y119Blocks
    ) {}

    private record SectionProbe(boolean open, boolean mineable, List<BlockPos> obstacles) {}

    private record StepDecision(
        int dx,
        int dz,
        SectionProbe probe,
        boolean requiresMining,
        String mode,
        int obstacleCount
    ) {}

    private record BoatPlacementCandidate(
        BlockPos floorAnchor,
        BlockHitResult hitResult,
        BlockPos placePos
    ) {}

    private record PendingOutputWrite(
        Path path,
        String line,
        String kind,
        int ticksRemaining
    ) {}

    private enum BoundaryType {
        START,
        STOP
    }

    private enum SectionPhase {
        LOOKING_FOR_ANY,
        LOOKING_FOR_START,
        LOOKING_FOR_STOP
    }
}
