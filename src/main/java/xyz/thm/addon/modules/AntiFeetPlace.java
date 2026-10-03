/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.modules;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import xyz.thm.addon.THMAddon;

import java.util.ArrayList;
import java.util.List;

public class AntiFeetPlace extends Module {
    public AntiFeetPlace() {
        super(THMAddon.PVP, "AntiFeetPlace",
            "Interrupts Enemies FeetPlace with a chosen block.");
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgFilters = settings.createGroup("Filters");

    // -------------------- General Settings -------------------- //
    private final Setting<List<Block>> blocks = sgGeneral.add(new BlockListSetting.Builder()
        .name("blocks").defaultValue(Blocks.ENDER_CHEST)
        .description("Blocks used to interrupt the feet place.").build());

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("range").defaultValue(5.2).min(1).sliderMax(8)
        .description("Max target range.").build());

    private final Setting<Boolean> rotate = sgGeneral.add(new BoolSetting.Builder()
        .name("rotate").defaultValue(true)
        .description("Rotate during taps / placement / bedrock hold.").build());

    private final Setting<Integer> tapTicksBelow = sgGeneral.add(new IntSetting.Builder()
        .name("tap-ticks-below").defaultValue(1).min(1).sliderMax(8)
        .description("Ticks to tap the block under surround before moving on.").build());

    private final Setting<Integer> tapTicksSurround = sgGeneral.add(new IntSetting.Builder()
        .name("tap-ticks-surround").defaultValue(1).min(1).sliderMax(8)
        .description("Ticks to tap the surround so rebreak targets it before placing.").build());

    private final Setting<Boolean> renderPlace = sgGeneral.add(new BoolSetting.Builder()
        .name("render-placement").defaultValue(true)
        .description("Let BlockUtils render intended placement.").build());

    private final Setting<Boolean> disableAfterPlace = sgGeneral.add(new BoolSetting.Builder()
        .name("disable-after-place").defaultValue(false)
        .description("If true, toggle off immediately after placing the block.").build());

    private final Setting<Integer> postPlacePause = sgGeneral.add(new IntSetting.Builder()
        .name("post-place-pause").defaultValue(40).min(0).sliderMax(200)
        .visible(() -> !disableAfterPlace.get())
        .description("If not disabling, HOLD (do nothing) this many ticks so AutoMine can work.").build());

    private final Setting<Boolean> waitWhileChestPresent = sgGeneral.add(new BoolSetting.Builder()
        .name("wait-while-chest-present").defaultValue(true)
        .description("While the block sits under the chosen surround, don't mine below again.").build());

    private final Setting<Integer> maxWaitChestTicks = sgGeneral.add(new IntSetting.Builder()
        .name("max-wait-chest-ticks").defaultValue(0).min(0).sliderMax(2000)
        .description("0 = wait indefinitely. If >0, leave WAIT_CHEST after this many ticks.")
        .visible(waitWhileChestPresent::get).build());

    private final Setting<Boolean> retargetWhileChestPresent = sgGeneral.add(new BoolSetting.Builder()
        .name("retarget-while-chest-present").defaultValue(true)
        .description("Tap the surround periodically while the block exists so AutoMine keeps that hole open.").build());

    private final Setting<Integer> waitRetargetInterval = sgGeneral.add(new IntSetting.Builder()
        .name("wait-retarget-interval").defaultValue(3).min(1).sliderMax(20)
        .description("How often (ticks) to tap the surround while waiting on the block.")
        .visible(retargetWhileChestPresent::get).build());

    private final Setting<Boolean> placeBridge = sgGeneral.add(new BoolSetting.Builder()
        .name("place-obsidian-bridge").defaultValue(true)
        .description("Place obsidian beside the block (same Y) – any available side.").build());

    private final Setting<Boolean> bridgeMineIfBlocking = sgGeneral.add(new BoolSetting.Builder()
        .name("bridge-mine-if-blocking").defaultValue(true)
        .description("If the bridge spot or its clearance is blocked, briefly mine to clear.")
        .visible(placeBridge::get).build());

    private final Setting<Integer> bridgePrepMaxTicks = sgGeneral.add(new IntSetting.Builder()
        .name("bridge-prep-max-ticks").defaultValue(25).min(0).sliderMax(200)
        .description("Max ticks to try clearing the bridge spot / clearance.")
        .visible(placeBridge::get).build());

    private final Setting<Boolean> ensureCrystalClearance = sgGeneral.add(new BoolSetting.Builder()
        .name("ensure-crystal-clearance").defaultValue(true)
        .description("Ensure 2 air blocks above the bridge so a crystal can be placed.")
        .visible(placeBridge::get).build());

    private final Setting<Boolean> allowBedrockBreak = sgGeneral.add(new BoolSetting.Builder()
        .name("allow-bedrock-break").defaultValue(true)
        .description("If true, will hold-mine bedrock under surround (your server allows it).").build());

    private final Setting<Boolean> skipBedrockBottomLayer = sgGeneral.add(new BoolSetting.Builder()
        .name("skip-bottom-bedrock-layer").defaultValue(true)
        .description("Skip bedrock at the world's bottommost Y (void floor).").build());

    private final Setting<Integer> bedrockHoldMaxTicks = sgGeneral.add(new IntSetting.Builder()
        .name("bedrock-hold-max-ticks").defaultValue(160).min(20).sliderMax(600)
        .description("Abort hold-mining if bedrock didn't break within this many ticks.").build());

    private final Setting<Integer> mineRateLimit = sgGeneral.add(new IntSetting.Builder()
        .name("mine-rate-limit-ticks").defaultValue(3).min(1).sliderMax(10)
        .description("Min ticks between any tap/mine packets to avoid SpeedMine resets & kicks.").build());

    // -------------------- Filter Settings -------------------- //
    private final Setting<Boolean> ignoreFriends = sgFilters.add(new BoolSetting.Builder()
        .name("ignore-friends").defaultValue(true).build());

    private final Setting<Boolean> ignoreNakeds = sgFilters.add(new BoolSetting.Builder()
        .name("ignore-nakeds").defaultValue(false)
        .description("Ignore players with no armor.").build());

    private final Setting<Boolean> skipUnbreakableBelow = sgFilters.add(new BoolSetting.Builder()
        .name("skip-unbreakable-below").defaultValue(true)
        .description("Skip below blocks with hardness < 0 (except bedrock if allowed).").build());

    private final Setting<Boolean> skipHardBelow = sgFilters.add(new BoolSetting.Builder()
        .name("skip-hard-below").defaultValue(false)
        .description("Skip if below block is in the hard list.").build());

    private final Setting<List<Block>> extraHard = sgFilters.add(new BlockListSetting.Builder()
        .name("extra-hard-list")
        .defaultValue(List.of(Blocks.OBSIDIAN, Blocks.ENDER_CHEST, Blocks.ANCIENT_DEBRIS, Blocks.RESPAWN_ANCHOR))
        .visible(skipHardBelow::get).build());

    // -------------------- State Machine -------------------- //
    private enum Stage { SELECT, MINE_BELOW_TAP, BREAK_BEDROCK_HOLD, RETARGET_SURROUND_TAP, PLACE_ECHEST, PLACE_BRIDGE, HOLD, WAIT_CHEST }
    private Stage stage = Stage.SELECT;

    private BlockPos surroundPos = null;
    private BlockPos belowPos = null;
    private Direction outwardDir = null;

    private int tapCounter = 0;
    private int ticksCounter = 0;
    private int tickCounter = 0;
    private int nextMineAllowedAt = 0;

    // -------------------- Lifecycle -------------------- //
    @Override
    public void onDeactivate() {
        resetAll();
    }

    // -------------------- Event Handlers -------------------- //
    @EventHandler
    private void onTick(TickEvent.Post e) {
        if (mc.player == null || mc.level == null) return;

        tickCounter++;

        switch (stage) {
            case SELECT -> selectStage();
            case MINE_BELOW_TAP -> mineBelowTapStage();
            case BREAK_BEDROCK_HOLD -> breakBedrockHoldStage();
            case RETARGET_SURROUND_TAP -> retargetSurroundTapStage();
            case PLACE_ECHEST -> placeEChestStage();
            case PLACE_BRIDGE -> placeBridgeStage();
            case HOLD -> holdStage();
            case WAIT_CHEST -> waitChestStage();
        }
    }

    // -------------------- Stage Implementations -------------------- //
    private void selectStage() {
        Player target = nearestEnemy();
        if (target == null) { resetAll(); return; }

        BlockPos feet = target.blockPosition();
        Direction facingMe = dirTowardPlayer(feet);
        BlockPos preferredSurround = feet.relative(facingMe);

        BlockPos best = null; double bestDist = Double.MAX_VALUE; Direction bestDir = null;

        if (preferredSurround.getY() == feet.getY() && preferredSurround.distManhattan(feet) == 1) {
            BlockState s = mc.level.getBlockState(preferredSurround);
            if (!s.isAir()) {
                BlockPos below = preferredSurround.below();
                BlockState bs = mc.level.getBlockState(below);
                boolean ok = true;

                if (isBlocker(bs) && waitWhileChestPresent.get()) { best = preferredSurround; bestDir = facingMe; bestDist = 0; }
                else {
                    if (skipUnbreakableBelow.get()) {
                        float hardness = bs.getDestroySpeed(mc.level, below);
                        if (hardness < 0f && !bs.is(Blocks.BEDROCK)) ok = false;
                    }
                    if (ok && skipHardBelow.get() && extraHard.get().contains(bs.getBlock()) && !bs.is(Blocks.BEDROCK)) ok = false;
                    if (ok && bs.is(Blocks.BEDROCK) && allowBedrockBreak.get() && skipBedrockBottomLayer.get() && below.getY() <= mc.level.getMinY()) ok = false;

                    Vec3 playerPos = new Vec3(mc.player.getX(), mc.player.getY(), mc.player.getZ());
                    double d = playerPos.distanceToSqr(Vec3.atCenterOf(preferredSurround));
                    if (ok && d <= range.get() * range.get()) { best = preferredSurround; bestDir = facingMe; bestDist = d; }
                }
            }
        }

        if (best == null) {
            for (BlockPos p : surroundOf(feet)) {
                if (p.getY() != feet.getY()) continue;
                if (p.distManhattan(feet) != 1) continue;
                BlockState s = mc.level.getBlockState(p);
                if (s.isAir()) continue;

                BlockPos below = p.below();
                BlockState bs = mc.level.getBlockState(below);

                if (isBlocker(bs) && waitWhileChestPresent.get()) { best = p; bestDir = dirFrom(feet, p); bestDist = 0; break; }

                if (skipUnbreakableBelow.get()) {
                    float hardness = bs.getDestroySpeed(mc.level, below);
                    if (hardness < 0f && !bs.is(Blocks.BEDROCK)) continue;
                }
                if (skipHardBelow.get() && extraHard.get().contains(bs.getBlock()) && !bs.is(Blocks.BEDROCK)) continue;
                if (bs.is(Blocks.BEDROCK) && allowBedrockBreak.get() && skipBedrockBottomLayer.get() && below.getY() <= mc.level.getMinY()) continue;

                Vec3 playerPos = new Vec3(mc.player.getX(), mc.player.getY(), mc.player.getZ());
                double d = playerPos.distanceToSqr(Vec3.atCenterOf(p));
                if (d <= range.get() * range.get() && d < bestDist) { best = p; bestDir = dirFrom(feet, p); bestDist = d; }
            }
        }

        if (best == null) { resetAll(); return; }

        surroundPos = best;
        belowPos = best.below();
        outwardDir = bestDir;
        tapCounter = 0;
        ticksCounter = 0;

        BlockState belowState = mc.level.getBlockState(belowPos);

        if (isBlocker(belowState)) {
            stage = waitOrYieldAfterChest();
        } else if (belowState.is(Blocks.BEDROCK) && allowBedrockBreak.get()) {
            if (skipBedrockBottomLayer.get() && belowPos.getY() <= mc.level.getMinY()) { stage = Stage.SELECT; return; }
            stage = Stage.BREAK_BEDROCK_HOLD;
        } else if (!belowState.isAir()) {
            stage = Stage.MINE_BELOW_TAP;
        } else {
            stage = Stage.RETARGET_SURROUND_TAP;
        }
    }

    private void mineBelowTapStage() {
        if (!validTarget()) { stage = Stage.SELECT; return; }

        BlockState bs = mc.level.getBlockState(belowPos);
        if (isBlocker(bs)) { stage = waitOrYieldAfterChest(); return; }
        if (bs.isAir()) { tapCounter = 0; stage = Stage.RETARGET_SURROUND_TAP; return; }
        if (bs.is(Blocks.BEDROCK) && allowBedrockBreak.get()) {
            if (!(skipBedrockBottomLayer.get() && belowPos.getY() <= mc.level.getMinY())) {
                tapCounter = 0; stage = Stage.BREAK_BEDROCK_HOLD; return;
            } else { stage = Stage.SELECT; return; }
        }

        tapBlockOnce(belowPos);
        if (++tapCounter >= tapTicksBelow.get()) {
            tapCounter = 0;
            stage = Stage.RETARGET_SURROUND_TAP;
        }
    }

    private void breakBedrockHoldStage() {
        if (!validTarget()) { stage = Stage.SELECT; return; }

        BlockState bs = mc.level.getBlockState(belowPos);
        if (bs.isAir()) { ticksCounter = 0; stage = Stage.RETARGET_SURROUND_TAP; return; }
        if (isBlocker(bs)) { stage = waitOrYieldAfterChest(); return; }
        if (skipBedrockBottomLayer.get() && belowPos.getY() <= mc.level.getMinY()) { stage = Stage.SELECT; return; }

        mineOnce(belowPos);
        if (++ticksCounter >= bedrockHoldMaxTicks.get()) stage = Stage.SELECT;
    }

    private void retargetSurroundTapStage() {
        if (!validTarget()) { stage = Stage.SELECT; return; }
        tapBlockOnce(surroundPos);
        if (++tapCounter >= tapTicksSurround.get()) {
            tapCounter = 0;
            stage = Stage.PLACE_ECHEST;
        }
    }

    private void placeEChestStage() {
        if (!validTarget()) { stage = Stage.SELECT; return; }

        BlockPos placeAt = belowPos;
        BlockState bs = mc.level.getBlockState(placeAt);
        if (isBlocker(bs)) { stage = placeBridge.get() ? Stage.PLACE_BRIDGE : waitOrYieldAfterChest(); return; }

        FindItemResult chest = InvUtils.findInHotbar(this::isBlockerItem);
        if (!chest.found()) chest = InvUtils.find(this::isBlockerItem);
        if (!chest.found()) { stage = Stage.SELECT; return; }

        if (!BlockUtils.canPlace(placeAt)) {
            if (++tapCounter > 20) { tapCounter = 0; stage = Stage.SELECT; }
            return;
        }

        if (rotate.get()) {
            float[] yp = lookAt(Vec3.atCenterOf(placeAt));
            Rotations.rotate(yp[0], yp[1], 25, () -> {});
        }

        boolean placed = BlockUtils.place(placeAt, chest, false, 50, renderPlace.get());
        if (placed) {
            stage = placeBridge.get() ? Stage.PLACE_BRIDGE : waitOrYieldAfterChest();
        } else if (++tapCounter > 10) {
            tapCounter = 0; stage = Stage.SELECT;
        }
    }

    private void placeBridgeStage() {
        if (!validTarget() || !placeBridge.get()) {
            stage = waitOrYieldAfterChest(); return;
        }

        if (!isBlocker(mc.level.getBlockState(belowPos))) {
            if (++ticksCounter < 5) return;
        }
        ticksCounter = 0;

        Direction bias = outwardDir != null ? outwardDir : Direction.NORTH;
        Direction[] order = new Direction[] { bias, bias.getOpposite(), rotateLeft(bias), rotateRight(bias) };

        for (Direction d : order) {
            BlockPos bridgeBase = belowPos.relative(d);

            if (mc.level.getBlockState(bridgeBase).is(Blocks.OBSIDIAN)) {
                stage = waitOrYieldAfterChest(); return;
            }

            if (ensureCrystalClearance.get()) {
                if (!clearOrWait(bridgeBase.above(), bridgePrepMaxTicks.get())) return;
                if (!clearOrWait(bridgeBase.above(2), bridgePrepMaxTicks.get())) return;
            }

            if (!mc.level.getBlockState(bridgeBase).isAir()) {
                if (!bridgeMineIfBlocking.get()) continue;
                if (!clearOrWait(bridgeBase, bridgePrepMaxTicks.get())) return;
            }

            FindItemResult obs = InvUtils.findInHotbar(Items.OBSIDIAN);
            if (!obs.found()) obs = InvUtils.find(Items.OBSIDIAN);
            if (!obs.found()) { stage = waitOrYieldAfterChest(); return; }

            if (rotate.get()) {
                float[] yp = lookAt(Vec3.atCenterOf(bridgeBase));
                Rotations.rotate(yp[0], yp[1], 25, () -> {});
            }
            if (BlockUtils.place(bridgeBase, obs, false, 50, renderPlace.get())) {
                stage = waitOrYieldAfterChest();
                return;
            }
        }

        if (++tapCounter > 10) { tapCounter = 0; stage = Stage.SELECT; }
    }

    private void holdStage() {
        if (!validTarget()) { stage = Stage.SELECT; return; }
        if (++ticksCounter >= postPlacePause.get()) stage = Stage.SELECT;
    }

    private void waitChestStage() {
        if (!validTarget()) { stage = Stage.SELECT; return; }

        BlockState under = mc.level.getBlockState(belowPos);
        boolean chestPresent = isBlocker(under);

        if (retargetWhileChestPresent.get() && surroundPos != null) {
            if (!mc.level.getBlockState(surroundPos).isAir()
                && (tickCounter % Math.max(1, waitRetargetInterval.get()) == 0)) {
                tapBlockOnce(surroundPos);
            }
        }

        if (maxWaitChestTicks.get() > 0 && ++ticksCounter >= maxWaitChestTicks.get()) { stage = Stage.SELECT; return; }
        if (chestPresent) return;

        ticksCounter = 0;
        tapCounter = 0;

        BlockState bs = mc.level.getBlockState(belowPos);
        if (bs.isAir()) stage = Stage.RETARGET_SURROUND_TAP;
        else if (bs.is(Blocks.BEDROCK) && allowBedrockBreak.get()
            && !(skipBedrockBottomLayer.get() && belowPos.getY() <= mc.level.getMinY())) {
            stage = Stage.BREAK_BEDROCK_HOLD;
        } else if (!bs.isAir() && !isBlocker(bs)) {
            stage = Stage.MINE_BELOW_TAP;
        } else stage = Stage.SELECT;
    }

    // -------------------- Helpers -------------------- //
    private boolean isBlocker(BlockState state) {
        return blocks.get().contains(state.getBlock());
    }

    private boolean isBlockerItem(ItemStack stack) {
        return blocks.get().contains(Block.byItem(stack.getItem()));
    }

    private void resetAll() {
        stage = Stage.SELECT;
        surroundPos = null;
        belowPos = null;
        outwardDir = null;
        tapCounter = 0;
        ticksCounter = 0;
        nextMineAllowedAt = 0;
    }

    private Stage waitOrYieldAfterChest() {
        if (disableAfterPlace.get()) return nextAfterPlaced();
        if (waitWhileChestPresent.get()) { ticksCounter = 0; return Stage.WAIT_CHEST; }
        ticksCounter = 0; return Stage.HOLD;
    }

    private Stage nextAfterPlaced() {
        if (disableAfterPlace.get()) { this.toggle(); return Stage.SELECT; }
        ticksCounter = 0; return Stage.HOLD;
    }

    private boolean validTarget() {
        return surroundPos != null && belowPos != null && inRange(surroundPos);
    }

    private boolean inRange(BlockPos p) {
        double r = range.get() + 1.0;
        Vec3 playerPos = new Vec3(mc.player.getX(), mc.player.getY(), mc.player.getZ());
        return playerPos.distanceToSqr(Vec3.atCenterOf(p)) <= r * r;
    }

    private void tapBlockOnce(BlockPos pos) {
        if (tickCounter < nextMineAllowedAt) return;
        BlockState st = mc.level.getBlockState(pos);
        if (st.isAir() || isBlocker(st)) return;
        if (rotate.get()) {
            float[] yp = lookAt(Vec3.atCenterOf(pos));
            Rotations.rotate(yp[0], yp[1], 25, () -> {});
        }
        Direction face = faceFor(pos);
        if (face == null) return;
        mc.gameMode.continueDestroyBlock(pos, face);
        mc.player.swing(InteractionHand.MAIN_HAND);
        nextMineAllowedAt = tickCounter + mineRateLimit.get();
    }

    private void mineOnce(BlockPos pos) {
        if (tickCounter < nextMineAllowedAt) return;
        BlockState st = mc.level.getBlockState(pos);
        if (st.isAir() || isBlocker(st)) return;
        if (rotate.get()) {
            float[] yp = lookAt(Vec3.atCenterOf(pos));
            Rotations.rotate(yp[0], yp[1], 25, () -> {});
        }
        Direction face = faceFor(pos);
        if (face == null) return;
        mc.gameMode.continueDestroyBlock(pos, face);
        mc.player.swing(InteractionHand.MAIN_HAND);
        nextMineAllowedAt = tickCounter + mineRateLimit.get();
    }

    private boolean clearOrWait(BlockPos pos, int maxTicks) {
        if (mc.level.getBlockState(pos).isAir()) return true;
        if (!bridgeMineIfBlocking.get()) return false;
        if (ticksCounter++ >= maxTicks) { ticksCounter = 0; stage = waitOrYieldAfterChest(); return false; }
        mineOnce(pos);
        return false;
    }

    private Direction faceFor(BlockPos pos) {
        BlockHitResult bhr = mc.level.clip(new ClipContext(
            mc.player.getEyePosition(),
            Vec3.atCenterOf(pos),
            ClipContext.Block.OUTLINE,
            ClipContext.Fluid.NONE,
            mc.player
        ));
        return bhr != null ? bhr.getDirection() : Direction.UP;
    }

    private float[] lookAt(Vec3 v) {
        Vec3 eye = mc.player.getEyePosition();
        double dx = v.x - eye.x, dy = v.y - eye.y, dz = v.z - eye.z;
        double h = Math.sqrt(dx*dx + dz*dz);
        float yaw = (float)(Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float pitch = (float)(-Math.toDegrees(Math.atan2(dy, h)));
        return new float[]{yaw, pitch};
    }

    private Player nearestEnemy() {
        Player best = null; double bestDist = Double.MAX_VALUE;

        for (Player p : mc.level.players()) {
            if (p == mc.player || !p.isAlive()) continue;
            if (ignoreFriends.get() && Friends.get().isFriend(p)) continue;

            if (ignoreNakeds.get()) {
                if (p.getItemBySlot(EquipmentSlot.HEAD).isEmpty()
                    && p.getItemBySlot(EquipmentSlot.CHEST).isEmpty()
                    && p.getItemBySlot(EquipmentSlot.LEGS).isEmpty()
                    && p.getItemBySlot(EquipmentSlot.FEET).isEmpty()) continue;
            }

            double d = mc.player.distanceTo(p);
            if (d <= range.get() && d < bestDist) { best = p; bestDist = d; }
        }
        return best;
    }

    private List<BlockPos> surroundOf(BlockPos feet) {
        List<BlockPos> list = new ArrayList<>(4);
        list.add(feet.north());
        list.add(feet.south());
        list.add(feet.east());
        list.add(feet.west());
        return list;
    }

    private Direction dirFrom(BlockPos feet, BlockPos surround) {
        if (surround.equals(feet.north())) return Direction.NORTH;
        if (surround.equals(feet.south())) return Direction.SOUTH;
        if (surround.equals(feet.east()))  return Direction.EAST;
        if (surround.equals(feet.west()))  return Direction.WEST;
        return null;
    }

    private Direction dirTowardPlayer(BlockPos enemyFeet) {
        Vec3 my = new Vec3(mc.player.getX(), mc.player.getY(), mc.player.getZ());
        Vec3 tgt = Vec3.atCenterOf(enemyFeet);
        double dx = my.x - tgt.x, dz = my.z - tgt.z;
        if (Math.abs(dx) >= Math.abs(dz)) return dx >= 0 ? Direction.EAST : Direction.WEST;
        else return dz >= 0 ? Direction.SOUTH : Direction.NORTH;
    }

    private Direction rotateLeft(Direction d) {
        return switch (d) {
            case NORTH -> Direction.WEST;
            case WEST -> Direction.SOUTH;
            case SOUTH -> Direction.EAST;
            case EAST -> Direction.NORTH;
            default -> d;
        };
    }

    private Direction rotateRight(Direction d) {
        return switch (d) {
            case NORTH -> Direction.EAST;
            case EAST -> Direction.SOUTH;
            case SOUTH -> Direction.WEST;
            case WEST -> Direction.NORTH;
            default -> d;
        };
    }
}
