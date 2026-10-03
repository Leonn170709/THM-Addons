/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.modules;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.mixin.WorldRendererAccessor;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.BlockDestructionProgress;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import xyz.thm.addon.THMAddon;
import xyz.thm.addon.utils.PearlPhaser;
import xyz.thm.addon.utils.PlacementUtils;

/**
 * Reacts to your surround being mined out.
 *
 * PEARL — phases into the block a player is breaking at your feet (moved here out of {@link Phase}).
 * The throw runs through this module's own {@link PearlPhaser}, so it has its own full copy of the
 * Pearl / Self Place settings — pitch, swap, attack, swing, self-fill, self-place and its rotate.
 *
 * CLIP — no pearls, just position. A crystal can't be placed where an entity's hitbox already is, so
 * standing on the corner shared by a mined-out 2x2 puts your 0.6-wide hitbox inside all four columns
 * at once and denies every one of them. With only one neighbour broken there is no corner to take, so
 * it straddles the edge between your block and that hole instead, denying both.
 */
public class AntiMine extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    public final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("mode")
        .description("Pearl: phase into the block being mined. Clip: stand between blocks so crystals can't be placed.")
        .defaultValue(Mode.Clip)
        .build());

    public final Setting<Integer> breakStage = sgGeneral.add(new IntSetting.Builder()
        .name("break-stage")
        .description("How far the block has to be broken before phasing. 0 fires the moment mining starts, 9 waits until it is about to pop.")
        .defaultValue(5)
        .range(0, 9)
        .sliderRange(0, 9)
        .visible(() -> mode.get() == Mode.Pearl)
        .build());

    public final Setting<Boolean> pearlClip = sgGeneral.add(new BoolSetting.Builder()
        .name("clip-align")
        .description("Runs Clip's positioning once the pearl has phased you, to square the hitbox up on the opening.")
        .defaultValue(true)
        .visible(() -> mode.get() == Mode.Pearl)
        .build());

    public final Setting<Boolean> selfMine = sgGeneral.add(new BoolSetting.Builder()
        .name("trigger-on-self")
        .description("Also triggers on blocks you mine yourself. Off is the real fight case; on is how you test it solo.")
        .defaultValue(false)
        .visible(() -> mode.get() == Mode.Pearl)
        .build());

    public final Setting<Boolean> debug = sgGeneral.add(new BoolSetting.Builder()
        .name("debug")
        .description("Prints which gate stopped a phase — breaking blocks seen, target found, pearl ready.")
        .defaultValue(false)
        .visible(() -> mode.get() == Mode.Pearl)
        .build());

    /**
     * Owns the Pearl / Self Place setting groups and the throw itself, minus attack and self-fill.
     * Every setting it creates is hidden unless the mode is Pearl.
     */
    private final PearlPhaser pearl = new PearlPhaser(settings, false, () -> mode.get() == Mode.Pearl);

    private BlockPos lastPearlTarget;
    /** Both modes fire once per break — cleared again when the surround is whole. */
    private boolean triggered;
    private long lastDebug;
    /** Ticks left to wait for the pearl's teleport before clip-align gives up. */
    private int alignTicks;
    private static final int PHASE_WAIT = 60;

    public AntiMine() {
        super(THMAddon.PVP, "anti-mine", "Phases or clips when your surround gets mined out.");
    }

    @Override
    public void onDeactivate() {
        if (mc.player != null) mc.player.noPhysics = false;
        lastPearlTarget = null;
        triggered = false;
        alignTicks = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.level == null) return;

        if (mode.get() == Mode.Pearl) {
            // Only while the hitbox is genuinely inside a block. A permanent noClip drops you straight
            // through the floor, so you're never in a surround and nothing ever triggers.
            mc.player.noPhysics = PlacementUtils.isPhasing();
            tickPearl();
        } else {
            mc.player.noPhysics = false;
            tickClip();
        }
    }

    // ── Pearl ─────────────────────────────────────────────────────────────────

    private void tickPearl() {
        tickPendingAlign();

        BlockPos target = findBreakingNeighbor();
        debug(target);

        if (!pearl.canThrow()) return;
        if (target == null) {
            lastPearlTarget = null;
            return;
        }
        // One phase per block being mined, not one per tick while it's being mined
        if (target.equals(lastPearlTarget)) return;
        lastPearlTarget = target;

        pearl.throwPearl(pearlAim(target));
        if (pearlClip.get()) alignTicks = PHASE_WAIT;
    }

    /**
     * Where the pearl has to land: the floor of the boundary we share with {@code target}, not the
     * target's centre. The teleport drops you exactly where the pearl stopped — aiming at the centre of a
     * block that is still solid means hitting its side face partway up, which leaves you hovering against
     * the wall instead of straddling it. Aiming at the boundary at floor level lands you on the seam at
     * standing height whatever your sub-block offset is, which is the whole point of the phase.
     */
    private Vec3 pearlAim(BlockPos target) {
        BlockPos feet = mc.player.blockPosition();
        int dx = target.getX() - feet.getX();
        int dz = target.getZ() - feet.getZ();

        double x = dx == 0 ? mc.player.getX() : (dx > 0 ? target.getX() : feet.getX());
        double z = dz == 0 ? mc.player.getZ() : (dz > 0 ? target.getZ() : feet.getZ());
        return new Vec3(x, feet.getY(), z);
    }

    /** Aligns once the pearl has actually landed us inside a block, or gives up. */
    private void tickPendingAlign() {
        if (alignTicks <= 0) return;
        alignTicks--;

        if (PlacementUtils.isPhasing()) {
            align();
            alignTicks = 0;
        }
    }

    /** Any block being broken that touches our own column at feet or head level. */
    private BlockPos findBreakingNeighbor() {
        Int2ObjectMap<BlockDestructionProgress> infos = breakingInfos();
        if (infos.isEmpty()) return null;

        BlockPos feet = mc.player.blockPosition();
        for (BlockDestructionProgress info : infos.values()) {
            if (!selfMine.get() && info.getId() == mc.player.getId()) continue;

            if (info.getProgress() < breakStage.get()) continue;

            BlockPos pos = info.getPos();
            int dy = pos.getY() - feet.getY();
            if (dy != 0 && dy != 1) continue;
            if (Math.abs(pos.getX() - feet.getX()) + Math.abs(pos.getZ() - feet.getZ()) == 1) return pos;
        }
        return null;
    }

    private Int2ObjectMap<BlockDestructionProgress> breakingInfos() {
        return ((WorldRendererAccessor) mc.levelRenderer).meteor$getBlockBreakingInfos();
    }

    /** One line a second while debug is on, so a silent phase says which gate stopped it. */
    private void debug(BlockPos target) {
        if (!debug.get() || mc.level.getGameTime() - lastDebug < 20) return;
        lastDebug = mc.level.getGameTime();

        StringBuilder seen = new StringBuilder();
        for (BlockDestructionProgress info : breakingInfos().values()) {
            seen.append(' ').append(info.getPos().toShortString())
                .append("/by").append(info.getId())
                .append("/st").append(info.getProgress());
        }
        info("feet=%s breaking=[%s] target=%s pearl=%s cooldown=%s",
            mc.player.blockPosition().toShortString(),
            seen.toString().trim(),
            target,
            PlacementUtils.getEnderPearlSlot(),
            mc.player.getCooldowns().isOnCooldown(Items.ENDER_PEARL.getDefaultInstance()));
    }

    // ── Clip ──────────────────────────────────────────────────────────────────

    private void tickClip() {
        BlockPos feet = mc.player.blockPosition();
        // Re-arms only once the surround is whole again, so this is one clip per break, not one per tick
        if (!surroundBroken(feet)) {
            triggered = false;
            return;
        }
        if (triggered) return;
        triggered = true;

        align();
    }

    /**
     * Takes the corner of a mined-out 2x2 if there is one, else straddles the edge into whichever
     * single neighbour is open, so both blocks are denied.
     */
    private void align() {
        BlockPos feet = mc.player.blockPosition();

        BlockPos corner = bestOpenCorner(feet);
        if (corner != null) {
            mc.player.setPos(corner.getX(), mc.player.getY(), corner.getZ());
            return;
        }

        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos n = feet.relative(dir);
            if (open(n.getX(), n.getZ(), feet.getY())) {
                straddleInto(n);
                return;
            }
        }
    }

    /** Some of the surround is gone but not all of it — i.e. we're in a hole, not standing in the open. */
    private boolean surroundBroken(BlockPos feet) {
        int holes = 0;
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos n = feet.relative(dir);
            if (open(n.getX(), n.getZ(), feet.getY())) holes++;
        }
        return holes > 0 && holes < 4;
    }

    /**
     * The nearest of our feet block's four corners whose whole 2x2 of columns is mined out. Standing
     * exactly on it puts the hitbox in all four, so none of them can take a crystal.
     */
    private BlockPos bestOpenCorner(BlockPos feet) {
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;

        for (int dx = 0; dx <= 1; dx++) {
            for (int dz = 0; dz <= 1; dz++) {
                int cx = feet.getX() + dx, cz = feet.getZ() + dz;
                if (!open(cx - 1, cz - 1, feet.getY()) || !open(cx, cz - 1, feet.getY())
                    || !open(cx - 1, cz, feet.getY()) || !open(cx, cz, feet.getY())) continue;

                double dist = mc.player.distanceToSqr(cx, mc.player.getY(), cz);
                if (dist < bestDist) {
                    bestDist = dist;
                    best = new BlockPos(cx, feet.getY(), cz);
                }
            }
        }
        return best;
    }

    /** Both the feet and head block of this column are walk-through. */
    private boolean open(int x, int z, int y) {
        for (int dy = 0; dy < 2; dy++) {
            BlockPos pos = new BlockPos(x, y + dy, z);
            if (!mc.level.getBlockState(pos).getCollisionShape(mc.level, pos).isEmpty()) return false;
        }
        return true;
    }

    /** Sits on the boundary between our block and {@code target}, so the hitbox covers both. */
    private void straddleInto(BlockPos target) {
        BlockPos feet = mc.player.blockPosition();
        double x = mc.player.getX();
        double z = mc.player.getZ();

        int dx = target.getX() - feet.getX();
        int dz = target.getZ() - feet.getZ();
        if (dx != 0) x = target.getX() + (dx > 0 ? 0.0 : 1.0);
        if (dz != 0) z = target.getZ() + (dz > 0 ? 0.0 : 1.0);

        mc.player.setPos(x, mc.player.getY(), z);
    }

    @Override
    public String getInfoString() {
        return mode.get().name();
    }

    public enum Mode {
        Pearl, Clip
    }
}
