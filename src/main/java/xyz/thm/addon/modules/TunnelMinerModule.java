package xyz.thm.addon.modules;

import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.player.Reach;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.*;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.*;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import xyz.thm.addon.THMAddon;

import java.util.*;
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

    // ── Settings ──────────────────────────────────────────────────────────────

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRestock = settings.createGroup("Restock");
    private final SettingGroup sgTiming  = settings.createGroup("Timing");
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Integer> targetX = sgGeneral.add(new IntSetting.Builder()
        .name("target-x").description("Target X coordinate.").defaultValue(0).build());

    private final Setting<Integer> targetZ = sgGeneral.add(new IntSetting.Builder()
        .name("target-z").description("Target Z coordinate.").defaultValue(0).build());

    private final Setting<Integer> tunnelHeight = sgGeneral.add(new IntSetting.Builder()
        .name("tunnel-height").description("Height of the tunnel in blocks (2 = player fits).")
        .defaultValue(2).min(2).max(4).sliderMax(4).build());

    private final Setting<Boolean> fillBehind = sgGeneral.add(new BoolSetting.Builder()
        .name("fill-behind")
        .description("Fills the tunnel behind you with selected blocks.")
        .defaultValue(true).build());

    private final Setting<List<Block>> fillBlocks = sgGeneral.add(new BlockListSetting.Builder()
        .name("fill-blocks")
        .description("Blocks to use for filling and bridging.")
        .visible(() -> fillBehind.get())
        .defaultValue(Blocks.NETHERRACK)
        .build());

    private final Setting<Boolean> lavaAvoidance = sgGeneral.add(new BoolSetting.Builder()
        .name("anti-lava")
        .description("Fills lava source blocks")
        .defaultValue(true).build());

    private final Setting<Boolean> checkLavaCeiling = sgGeneral.add(new BoolSetting.Builder()
        .name("check-lava-ceiling")
        .description("Also detects lava above the tunnel (one block above tunnel height).")
        .visible(() -> lavaAvoidance.get())
        .defaultValue(true).build());

    private final Setting<Boolean> airPlace = sgGeneral.add(new BoolSetting.Builder()
        .name("air-place")
        .description("Use air place")
        .defaultValue(false).build());

    private final Setting<Integer> airPlaceDistance = sgGeneral.add(new IntSetting.Builder()
        .name("scaffold")
        .description("How many blocks ahead to place.")
        .defaultValue(3).min(1).max(6).sliderMax(6).build());

    private final Setting<Boolean> rotate = sgGeneral.add(new BoolSetting.Builder()
        .name("rotate")
        .description("Faces the block being interacted with.")
        .defaultValue(true)
        .build());

    public final Setting<Boolean> debugMessages = sgGeneral.add(new BoolSetting.Builder()
        .name("debug-messages")
        .description("Logs detailed information about the module's state.")
        .defaultValue(false)
        .build());

    private final Setting<Boolean> useShulkers = sgRestock.add(new BoolSetting.Builder()
        .name("use-shulkers")
        .description("Open shulker boxes to restock pickaxes when the last one is low.")
        .defaultValue(false).build());

    private final Setting<Boolean> useEnderChest = sgRestock.add(new BoolSetting.Builder()
        .name("use-ender-chest")
        .description("Open ender chests to restock pickaxes when the last one is low.")
        .defaultValue(false).build());

    private final Setting<Integer> lowDurability = sgRestock.add(new IntSetting.Builder()
        .name("low-durability")
        .description("Durability remaining on the last pickaxe that triggers a restock.")
        .defaultValue(100).min(1).max(1561).sliderMin(1).sliderMax(1561).build());

    private final Setting<Integer> minPickaxes = sgRestock.add(new IntSetting.Builder()
        .name("min-pickaxes")
        .description("How many pickaxes to grab from the container before closing it.")
        .defaultValue(3).min(1).max(10).sliderMax(10).build());

    private final Setting<Integer> breaksPerTick = sgTiming.add(new IntSetting.Builder()
        .name("breaks-per-tick").description("Block break attempts sent per tick.")
        .defaultValue(1).min(1).max(5).sliderMax(5).build());

    private final Setting<Integer> placesPerTick = sgTiming.add(new IntSetting.Builder()
        .name("places-per-tick").description("Block placements attempted per tick.")
        .defaultValue(1).min(1).max(5).sliderMax(5).build());

    private final Setting<Integer> placeDelay = sgTiming.add(new IntSetting.Builder()
        .name("place-delay").description("Ticks to wait between fill placements.")
        .defaultValue(1).min(0).sliderMax(10).build());

    private final Setting<Integer> invDelay = sgTiming.add(new IntSetting.Builder()
        .name("inventory-delay").description("Ticks to wait between inventory actions.")
        .defaultValue(3).min(0).sliderMax(10).build());

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .description("How the blocks are rendered.")
        .defaultValue(ShapeMode.Both)
        .build()
    );

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

    private Phase phase;

    // Where we are going
    private int destX, destZ, totalBlocks;

    // Which axis we are currently on (X first, then Z)
    private boolean onXAxis;

    // Walk target set each time we enter WALK
    private double walkTargetX, walkTargetZ;

    // Fill log: original block states of blocks we mined, keyed by position
    private final LinkedHashMap<BlockPos, BlockState> fillLog = new LinkedHashMap<>();

    // Timers
    private int placeTimer, invTimer, waitTicks;
    private static final int MAX_WAIT = 100;

    // Inventory
    private int pickSlot = -1;
    private BlockPos containerPos;
    private boolean restockEC;

    // Stats for HUD
    private long startMs;
    private int blocksMined;

    private final List<BlockPos> renderBreakPositions = new ArrayList<>();
    // ── Constructor ───────────────────────────────────────────────────────────

    public TunnelMinerModule() {
        super(THMAddon.MAIN, "tunnel-miner",
            "Mines a tunnel block-by-block to target XZ coordinates at the same Y.");
        INSTANCE = this;
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    public void onActivate() {
        if (mc.player == null) return;
        destX = targetX.get();
        destZ = targetZ.get();
        onXAxis = true;
        fillLog.clear();
        renderBreakPositions.clear();
        placeTimer = invTimer = waitTicks = 0;
        pickSlot = -1;
        containerPos = null;
        startMs = System.currentTimeMillis();
        blocksMined = 0;

        int px = MathHelper.floor(mc.player.getX());
        int pz = MathHelper.floor(mc.player.getZ());
        totalBlocks = Math.abs(destX - px) + Math.abs(destZ - pz);
        phase = Phase.INIT;
        if (debugMessages.get()) {
            info("Start tunnel to X=" + destX + " Z=" + destZ + " (" + totalBlocks + " blocks)");
        }

    }

    @Override
    public void onDeactivate() {
        if (mc.options != null) mc.options.forwardKey.setPressed(false);
        renderBreakPositions.clear();
        if (debugMessages.get()) {info("Stopped.");}

    }

    @EventHandler
    private void onGameLeft(GameLeftEvent e) {
        if (isActive()) toggle();
    }

    // ── Main tick ─────────────────────────────────────────────────────────────

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) {
            if (isActive()) toggle();
            return;
        }

        // Decrement timers and skip phase logic if active
        if (placeTimer > 0) {
            placeTimer--;
            return;
        }
        if (invTimer > 0) {
            invTimer--;
            return;
        }

        switch (phase) {
            case INIT: initPhase(); break;
            case MINE: minePhase(); break;
            case WALK: walkPhase(); break;
            case FILL: fillPhase(); break;
            case RESTOCK_CLEAR: restockClear(); break;
            case RESTOCK_PLACE: restockPlace(); break;
            case RESTOCK_WAIT: restockWait(); break;
            case RESTOCK_OPEN: restockOpen(); break;
            case RESTOCK_LOOT: restockLoot(); break;
            case RESTOCK_CLOSE: restockClose(); break;
            case RESTOCK_BREAK: restockBreak(); break;
            case RESTOCK_PICKUP: restockPickup(); break;
            case DONE:
                if (debugMessages.get()) { info("Destination reached!");}

                toggle();
                break;
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        // Render blocks to break
        for (BlockPos pos : renderBreakPositions) {
            event.renderer.box(pos, breakSideColor.get(), breakLineColor.get(), shapeMode.get(), 0);
        }

        // Render blocks to place
        if (fillBehind.get()) {
            int px = MathHelper.floor(mc.player.getX());
            int pz = MathHelper.floor(mc.player.getZ());

            for (BlockPos pos : fillLog.keySet()) {
                // Only render if it's a valid placement target (not too close)
                if (Math.abs(pos.getX() - px) <= 1 && Math.abs(pos.getZ() - pz) <= 1) {
                    continue;
                }
                event.renderer.box(pos, placeSideColor.get(), placeLineColor.get(), shapeMode.get(), 0);
            }
        }
    }


    // ── INIT ──────────────────────────────────────────────────────────────────

    private void initPhase() {
        if (countPickaxes() == 0) {
            if (debugMessages.get()) {warning("No pickaxes — stopping.");}
            toggle();
            return;
        }
        pickSlot = equipBestPickaxe();
        setPhase(Phase.MINE);
    }

    // ── MINE ──────────────────────────────────────────────────────────────────
    // Break blocks in the tunnel profile one step ahead until clear, then WALK

    private void minePhase() {
        renderBreakPositions.clear();
        // Restock check
        if (needsRestock()) {
            if (debugMessages.get()) { info("Pickaxe low, starting restock.");}

            setPhase(Phase.RESTOCK_CLEAR);
            return;
        }

        int px = MathHelper.floor(mc.player.getX());
        int py = MathHelper.floor(mc.player.getY());
        int pz = MathHelper.floor(mc.player.getZ());

        // Check if we've reached the target on the current axis
        if (onXAxis) {
            if (px == destX) {
                // Finished X leg — switch to Z
                if (pz == destZ) { // Should not happen if totalBlocks is correct
                    setPhase(Phase.DONE);
                    return;
                }
                onXAxis = false;
                if (debugMessages.get()) {  info("X axis done — now heading Z...");}

            }
        } else {
            if (pz == destZ) {
                setPhase(Phase.DONE);
                return;
            }
        }

        Direction fwd = fwd();

        // Collect solid blocks to break one step ahead (including lava)
        List<BlockPos> toBreak = new ArrayList<>();
        for (int h = 0; h < tunnelHeight.get(); h++) {
            BlockPos bp = new BlockPos(px + fwd.getOffsetX(), py + h, pz + fwd.getOffsetZ());
            BlockState bs = mc.world.getBlockState(bp);
            if (!bs.isAir() && bs.getBlock() != Blocks.BEDROCK) {
                toBreak.add(bp);
            }
        }
        renderBreakPositions.addAll(toBreak);

        // If there are blocks to break, break them and stay in MINE phase
        if (!toBreak.isEmpty()) {
            ensurePickaxe();
            int n = Math.min(breaksPerTick.get(), toBreak.size());
            if (debugMessages.get()) {info("Breaking " + n + " blocks at " + toBreak.get(0).toShortString());}
            for (int i = 0; i < n; i++) {
                BlockPos bp = toBreak.get(i);
                // Log original state BEFORE breaking for fill phase
                if (fillBehind.get()) {
                    fillLog.putIfAbsent(bp, mc.world.getBlockState(bp));
                }
                final BlockPos fBp = bp;
                if (rotate.get()) {
                    Rotations.rotate(Rotations.getYaw(bp), Rotations.getPitch(bp),
                        () -> BlockUtils.breakBlock(fBp, true));
                } else {
                    BlockUtils.breakBlock(fBp, true);
                }
            }
            blocksMined += n;
            return; // Stay in MINE, keep breaking next tick
        }

        // Check for lava ahead and fill it
        if (lavaAvoidance.get() && hasLavaAhead(px, py, pz, fwd)) {
            if (debugMessages.get()) {info("Lava detected ahead, filling...");}
            if (fillLavaAhead(px, py, pz, fwd)) {
                return; // Filled some lava, stay in MINE
            }
        }

        // Profile is clear — transition to WALK
        startWalk(px, pz, fwd);
    }

    // Check if there is lava in the tunnel path ahead
    private boolean hasLavaAhead(int px, int py, int pz, Direction dir) {
        for (int d = 1; d <= 5; d++) {
            for (int h = 0; h < tunnelHeight.get(); h++) {
                BlockPos bp = new BlockPos(px + dir.getOffsetX() * d, py + h, pz + dir.getOffsetZ() * d);
                if (mc.world.getBlockState(bp).getBlock() == Blocks.LAVA) {
                    return true;
                }
            }
            // Check ceiling
            if (checkLavaCeiling.get()) {
                BlockPos ceilingPos = new BlockPos(px + dir.getOffsetX() * d, py + tunnelHeight.get(), pz + dir.getOffsetZ() * d);
                if (mc.world.getBlockState(ceilingPos).getBlock() == Blocks.LAVA) {
                    return true;
                }
            }
        }
        return false;
    }

    // Try to fill lava ahead with blocks
    private boolean fillLavaAhead(int px, int py, int pz, Direction dir) {
        int slot = findBlockToPlace();
        if (slot == -1) {
            if (debugMessages.get()) {warning("No blocks to fill lava with!");}
            return false;
        }

        // Try to place blocks on lava
        for (int d = 1; d <= 5; d++) {
            for (int h = 0; h < tunnelHeight.get(); h++) {
                BlockPos bp = new BlockPos(px + dir.getOffsetX() * d, py + h, pz + dir.getOffsetZ() * d);
                if (mc.world.getBlockState(bp).getBlock() == Blocks.LAVA) {
                    int hb = toHotbar(slot);
                    if (hb == -1) return false;

                    InvUtils.swap(hb, true);
                    final BlockPos fBp = bp;
                    if (rotate.get()) {
                        Rotations.rotate(Rotations.getYaw(bp), Rotations.getPitch(bp),
                            () -> BlockUtils.place(fBp, Hand.MAIN_HAND, hb, true, 0, true, true, true));
                    } else {
                        BlockUtils.place(fBp, Hand.MAIN_HAND, hb, false, 0, true, true, true);
                    }
                    blocksMined++;
                    return true;
                }
            }
        }
        return false;
    }

    // ── WALK ──────────────────────────────────────────────────────────────────
    // Move player toward the next block center, then either FILL or go back to MINE

    private void startWalk(int fromX, int fromZ, Direction fwd) {
        walkTargetX = (fromX + fwd.getOffsetX()) + 0.5;
        walkTargetZ = (fromZ + fwd.getOffsetZ()) + 0.5;
        if (debugMessages.get()) {info("Path clear, walking to " + String.format("%.1f, %.1f", walkTargetX, walkTargetZ));}
        setPhase(Phase.WALK);
    }

    private void walkPhase() {
        // Bridging: check below current pos and target pos
        BlockPos myFloor = mc.player.getBlockPos().down();
        BlockPos targetFloor = new BlockPos(MathHelper.floor(walkTargetX), MathHelper.floor(mc.player.getY()) - 1, MathHelper.floor(walkTargetZ));

        if (placeFloor(myFloor) || placeFloor(targetFloor)) {
            return;
        }

        // Air place blocks ahead if enabled
        if (airPlace.get()) {
            if (placeAheadBlocks()) {
                return;
            }
        }

        moveToward(walkTargetX, walkTargetZ, () -> {
            // After reaching the target, check if we should fill
            if (fillBehind.get() && !fillLog.isEmpty()) {
                setPhase(Phase.FILL);
            } else {
                setPhase(Phase.MINE);
            }
        });
    }

    private boolean placeFloor(BlockPos pos) {
        if (mc.world.getBlockState(pos).isAir() || !mc.world.getBlockState(pos).getFluidState().isEmpty()) {
            int slot = findBlockToPlace();
            if (slot != -1) {
                int hb = toHotbar(slot);
                if (hb != -1) {
                    InvUtils.swap(hb, true);
                    if (rotate.get()) {
                        Rotations.rotate(mc.player.getYaw(), 90, () -> BlockUtils.place(pos, Hand.MAIN_HAND, hb, true, 0, true, true, true));
                    } else {
                        BlockUtils.place(pos, Hand.MAIN_HAND, hb, false, 0, true, true, true);
                    }
                    return true;
                }
            }
        }
        return false;
    }

    // Place blocks ahead in the air to prevent falling
    private boolean placeAheadBlocks() {
        Direction fwd = fwd();
        int px = MathHelper.floor(mc.player.getX());
        int py = MathHelper.floor(mc.player.getY());
        int pz = MathHelper.floor(mc.player.getZ());
        int slot = findBlockToPlace();

        if (slot == -1) return false;

        for (int d = 1; d <= airPlaceDistance.get(); d++) {
            BlockPos placePos = new BlockPos(px + fwd.getOffsetX() * d, py - 1, pz + fwd.getOffsetZ() * d);
            if (mc.world.getBlockState(placePos).isAir()) {
                int hb = toHotbar(slot);
                if (hb == -1) return false;

                InvUtils.swap(hb, true);
                final BlockPos fPos = placePos;
                BlockUtils.place(fPos, Hand.MAIN_HAND, hb, rotate.get(), 0, true, true, true);
                return true;
            }
        }
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

        if (distSq < 0.04) { // < 0.2 blocks
            mc.options.forwardKey.setPressed(false);
            onArrival.run();
            return;
        }

        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        mc.player.setYaw(yaw);
        mc.options.forwardKey.setPressed(true);
    }

    // ── FILL ──────────────────────────────────────────────────────────────────
    // Re-place mined blocks behind the player, then return to MINE

    private void fillPhase() {
        if (!fillBehind.get() || fillLog.isEmpty()) {
            setPhase(Phase.MINE);
            return;
        }

        // Try air place first if enabled
        if (airPlace.get()) {
            if (placeAheadBlocks()) {
                return;
            }
        }

        int px = MathHelper.floor(mc.player.getX());
        int pz = MathHelper.floor(mc.player.getZ());
        int placed = 0;

        Iterator<Map.Entry<BlockPos, BlockState>> it = fillLog.entrySet().iterator();
        while (it.hasNext() && placed < placesPerTick.get()) {
            Map.Entry<BlockPos, BlockState> e = it.next();
            BlockPos pos = e.getKey();

            // Skip if player is standing on or next to this block
            if (Math.abs(pos.getX() - px) <= 1 && Math.abs(pos.getZ() - pz) <= 1) {
                continue;
            }

            // Skip if too far away
            if (Vec3d.ofCenter(pos).distanceTo(mc.player.getEyePos()) > 4 + Objects.requireNonNull(Modules.get().get(Reach.class)).blockReach()) {
                it.remove();
                continue;
            }

            // Skip if already filled
            if (!mc.world.getBlockState(pos).isAir()) {
                it.remove();
                continue;
            }

            if (!airPlace.get() && mc.world.getBlockState(pos.down()).isAir()) {
                it.remove();
                continue;
            }

            // Try to find a block from the fill list
            int slot = findBlockToPlace();
            if (slot == -1) {
                it.remove(); // Don't have any valid block
                continue;
            }

            int hb = toHotbar(slot);
            if (hb == -1) {
                continue; // Hotbar full, try next block
            }

            InvUtils.swap(hb, true);
            final BlockPos fPos = pos;
            final int fHb = hb;
            BlockUtils.place(fPos, Hand.MAIN_HAND, fHb, rotate.get(), 0, true, true, true);

            it.remove();
            placed++;
        }

        // If we placed blocks, wait before going back to mine
        if (placed > 0) {
            if (debugMessages.get()) { info("Placed " + placed + " blocks behind.");}

            placeTimer = placeDelay.get() > 0 ? placeDelay.get() : 1;
            ensurePickaxe();
            return;
        }

        // If we didn't place anything (e.g. because blocks are too close), go back to mining
        setPhase(Phase.MINE);
    }

    // ── HUD getters ───────────────────────────────────────────────────────────

    private void restockClear() {
        int px = MathHelper.floor(mc.player.getX());
        int py = MathHelper.floor(mc.player.getY());
        int pz = MathHelper.floor(mc.player.getZ());
        Direction fwd = fwd();

        boolean clear = true;
        for (int h = 0; h < 2; h++) {
            BlockPos bp = new BlockPos(px + fwd.getOffsetX(), py + h, pz + fwd.getOffsetZ());
            if (!mc.world.getBlockState(bp).isAir()) {
                ensurePickaxe();
                final BlockPos fBp = bp;
                if (rotate.get()) {
                    Rotations.rotate(Rotations.getYaw(bp), Rotations.getPitch(bp),
                        () -> BlockUtils.breakBlock(fBp, true));
                } else {
                    BlockUtils.breakBlock(fBp, true);
                }
                clear = false;
                waitTicks++;
                if (waitTicks > MAX_WAIT) {
                    if (debugMessages.get()) {warning("Can't clear space.");}
                    toggle();
                }
                return;
            }
        }
        if (clear) {
            waitTicks = 0;
            int sk = useShulkers.get() ? findInInv(TunnelMinerModule::isShulkerBox) : -1;
            int ec = useEnderChest.get() ? findInInv(TunnelMinerModule::isEnderChest) : -1;
            if (sk != -1) {
                restockEC = false;
                if (debugMessages.get()) {info("Found shulker box for restock.");}

                setPhase(Phase.RESTOCK_PLACE);
            } else if (ec != -1) {
                restockEC = true;
                if (debugMessages.get()) {info("Found ender chest for restock.");}

                setPhase(Phase.RESTOCK_PLACE);
            } else {
                if (debugMessages.get()) {warning("No restock container — stopping.");}

                toggle();
            }
        }
    }

    private void restockPlace() {
        Direction fwd = fwd();
        int px = MathHelper.floor(mc.player.getX());
        int py = MathHelper.floor(mc.player.getY());
        int pz = MathHelper.floor(mc.player.getZ());
        containerPos = new BlockPos(px + fwd.getOffsetX(), py, pz + fwd.getOffsetZ());
        BlockPos floor = containerPos.down();
        if (mc.world.getBlockState(floor).isAir()) {
            if (debugMessages.get()) {warning("No floor for container.");}

            toggle();
            return;
        }

        int slot = restockEC ? findInInv(TunnelMinerModule::isEnderChest)
            : findInInv(TunnelMinerModule::isShulkerBox);
        if (slot == -1) {
            if (debugMessages.get()) warning("Lost container!");
            toggle();
            return;
        }
        int hb = toHotbar(slot);
        if (hb == -1) {
            if (debugMessages.get()) warning("Hotbar full!");
            toggle();
            return;
        }
        InvUtils.swap(hb, true);

        if (debugMessages.get()) info("Placing restock container at " + containerPos.toShortString());
        final int fHb = hb;
        if (rotate.get()) {
            Rotations.rotate(Rotations.getYaw(floor), Rotations.getPitch(floor),
                () -> BlockUtils.place(containerPos, Hand.MAIN_HAND, fHb, true, 0, true, true, true));
        } else {
            BlockUtils.place(containerPos, Hand.MAIN_HAND, fHb, false, 0, true, true, true);
        }

        ensurePickaxe();
        invTimer = invDelay.get();
        waitTicks = 0;
        setPhase(Phase.RESTOCK_WAIT);
    }

    private void restockWait() {
        waitTicks++;
        boolean here = restockEC
            ? mc.world.getBlockState(containerPos).getBlock() == Blocks.ENDER_CHEST
            : mc.world.getBlockState(containerPos).getBlock() instanceof ShulkerBoxBlock;
        if (here) {
            waitTicks = 0;
            setPhase(Phase.RESTOCK_OPEN);
            return;
        }
        if (waitTicks > MAX_WAIT) {
            if (debugMessages.get()) warning("Container didn't appear.");
            toggle();
        }
    }

    private void restockOpen() {
        waitTicks++;
        if (waitTicks == 1) {
            BlockPos bp = containerPos;
            if (rotate.get()) {
                Rotations.rotate(Rotations.getYaw(bp), Rotations.getPitch(bp),
                    () -> mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND,
                        new BlockHitResult(Vec3d.ofCenter(bp), Direction.UP, bp, false)));
            } else {
                mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND,
                    new BlockHitResult(Vec3d.ofCenter(bp), Direction.UP, bp, false));
            }
            return;
        }
        if (mc.currentScreen != null) {
            waitTicks = 0;
            invTimer = invDelay.get();
            if (debugMessages.get()) info("Container open, looting pickaxes.");
            setPhase(Phase.RESTOCK_LOOT);
            return;
        }
        if (waitTicks > MAX_WAIT) {
            if (debugMessages.get()) warning("Container didn't open.");
            toggle();
        }
    }

    private void restockLoot() {
        if (mc.currentScreen == null) {
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
            if (debugMessages.get()) info("Inventory full, closing container.");
            setPhase(Phase.RESTOCK_CLOSE);
            return;
        }

        // Only grab pickaxes
        int currentPickaxes = countPickaxes();
        if (currentPickaxes >= minPickaxes.get()) {
            if (debugMessages.get()) info("Have enough pickaxes, closing container.");
            setPhase(Phase.RESTOCK_CLOSE);
            return;
        }

        var handler = mc.player.currentScreenHandler;
        for (int i = 0; i < Math.min(27, handler.slots.size()); i++) {
            if (isPickaxe(handler.slots.get(i).getStack())) {
                InvUtils.shiftClick().slotId(i);
                invTimer = invDelay.get();
                return;
            }
        }
        // No more pickaxes
        if (debugMessages.get()) info("No more pickaxes in container, closing.");
        setPhase(Phase.RESTOCK_CLOSE);
    }

    private void restockClose() {
        if (mc.currentScreen != null) {
            mc.currentScreen.close();
            invTimer = invDelay.get();
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
            ensurePickaxe();
            final BlockPos bp = containerPos;
            if (rotate.get()) {
                Rotations.rotate(Rotations.getYaw(bp), Rotations.getPitch(bp),
                    () -> BlockUtils.breakBlock(bp, true));
            } else {
                BlockUtils.breakBlock(bp, true);
            }
            return;
        }
        waitTicks = 0;
        setPhase(Phase.RESTOCK_PICKUP);
    }

    private void restockPickup() {
        if (++waitTicks < 20) return;
        containerPos = null;
        waitTicks = 0;
        pickSlot = equipBestPickaxe();
        if (debugMessages.get()) info("Restock done — resuming.");
        setPhase(Phase.MINE);
    }

    // ── HUD getters ───────────────────────────────────────────────────────────

    public int getBlocksLeft() {
        if (mc.player == null || !isActive()) return 0;
        return Math.abs(destX - MathHelper.floor(mc.player.getX()))
            + Math.abs(destZ - MathHelper.floor(mc.player.getZ()));
    }

    public double getEtaSeconds() {
        if (!isActive() || blocksMined < 5) return -1;
        long ms = System.currentTimeMillis() - startMs;
        if (ms <= 0) return -1;
        double rate = (double) blocksMined / ms;
        return rate <= 0 ? -1 : getBlocksLeft() / (rate * 1000.0);
    }

    public int getDestX() {
        return destX;
    }

    public int getDestZ() {
        return destZ;
    }

    public int getTotalBlocks() {
        return totalBlocks;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    // Current forward direction based on which axis we're on and which way the target is
    private Direction fwd() {
        if (onXAxis) {
            return destX > MathHelper.floor(mc.player.getX()) ? Direction.EAST : Direction.WEST;
        } else {
            return destZ > MathHelper.floor(mc.player.getZ()) ? Direction.SOUTH : Direction.NORTH;
        }
    }

    private boolean isLavaColumn(int x, int y, int z) {
        for (int h = 0; h < tunnelHeight.get(); h++) {
            if (mc.world.getBlockState(new BlockPos(x, y + h, z)).getBlock() == Blocks.LAVA) {
                return true;
            }
        }
        return false;
    }

    // ── Inventory helpers ─────────────────────────────────────────────────────

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
        if (!useShulkers.get() && !useEnderChest.get()) return false;
        if (countPickaxes() != 1) return false;
        for (int i = 0; i < mc.player.getInventory().size(); i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (isPickaxe(s) && durabilityLeft(s) <= lowDurability.get()) return true;
        }
        return false;
    }

    private int findInInv(Predicate<ItemStack> p) {
        for (int i = 0; i < mc.player.getInventory().size(); i++) {
            if (p.test(mc.player.getInventory().getStack(i))) return i;
        }
        return -1;
    }

    private int equipBestPickaxe() {
        int best = -1, score = -1;
        for (int i = 0; i < mc.player.getInventory().size(); i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            int sc = pickScore(s);
            if (sc > score) {
                score = sc;
                best = i;
            }
        }
        if (best == -1) return -1;
        int hb = toHotbar(best);
        if (hb == -1) hb = 0;
        InvUtils.swap(hb, true);
        return hb;
    }

    private void ensurePickaxe() {
        if (pickSlot >= 0 && isPickaxe(mc.player.getInventory().getStack(pickSlot))) {
            InvUtils.swap(pickSlot, true);
        } else {
            pickSlot = equipBestPickaxe();
        }
    }

    // Returns hotbar slot for the given inventory slot, moving to hotbar if needed
    private int toHotbar(int slot) {
        if (slot >= 0 && slot < 9) return slot;
        for (int i = 0; i < 9; i++) {
            if (i == pickSlot) continue;
            if (mc.player.getInventory().getStack(i).isEmpty()) {
                InvUtils.move().from(slot).toHotbar(i);
                return i;
            }
        }
        int t = (pickSlot == 1) ? 0 : 1;
        InvUtils.move().from(slot).toHotbar(t);
        return t;
    }

    private int findBlockToPlace() {
        return findInInv(s -> {
            if (s.isEmpty()) return false;
            if (!(s.getItem() instanceof BlockItem bi)) return false;
            return fillBlocks.get().contains(bi.getBlock());
        });
    }

    private int pickScore(ItemStack s) {
        if (s.isOf(Items.NETHERITE_PICKAXE)) return 5;
        if (s.isOf(Items.DIAMOND_PICKAXE)) return 4;
        if (s.isOf(Items.IRON_PICKAXE)) return 3;
        if (s.isOf(Items.STONE_PICKAXE)) return 2;
        if (s.isOf(Items.GOLDEN_PICKAXE)) return 1;
        return 0;
    }

    private void setPhase(Phase newPhase) {
        if (this.phase != newPhase) {
            if (debugMessages.get()) info("Phase: " + (this.phase == null ? "NONE" : this.phase) + " -> " + newPhase);
            this.phase = newPhase;
        }

    }
}
