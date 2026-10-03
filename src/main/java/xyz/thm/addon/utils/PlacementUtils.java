/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.utils;

import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import java.util.Arrays;
import java.util.List;

import static meteordevelopment.meteorclient.MeteorClient.mc;
public class PlacementUtils {
    private static final List<Block> RESISTANT_BLOCKS = Arrays.asList(
        Blocks.OBSIDIAN,
        Blocks.CRYING_OBSIDIAN,
        Blocks.ENDER_CHEST,
        Blocks.RESPAWN_ANCHOR,
        Blocks.ENCHANTING_TABLE,
        Blocks.ANVIL
    );
    public static FindItemResult findResistantBlock() {
        for (Block block : RESISTANT_BLOCKS) {
            FindItemResult result = InvUtils.findInHotbar(block.asItem());
            if (result.found()) return result;
        }
        return InvUtils.findInHotbar(itemStack -> false);
    }
    public static boolean placeBlock(BlockPos pos, boolean rotate, boolean swing, boolean strictDirection) {
        FindItemResult block = findResistantBlock();
        if (!block.found()) return false;
        return placeBlock(pos, block, rotate, swing, strictDirection);
    }
    public static boolean placeBlock(BlockPos pos, FindItemResult block, boolean rotate, boolean swing, boolean strictDirection) {
        if (!block.found() || !canPlace(pos, strictDirection)) return false;
        Direction side = getPlaceSide(pos);
        if (side == null) return false;
        BlockPos neighbor = pos.relative(side);
        Direction opposite = side.getOpposite();
        Vec3 hitPos = Vec3.atCenterOf(neighbor).add(Vec3.atLowerCornerOf(opposite.getUnitVec3i()).scale(0.5));
        if (rotate) {
            Rotations.rotate(Rotations.getYaw(hitPos), Rotations.getPitch(hitPos));
        }
        if (block.getHand() == null && !InvUtils.swap(block.slot(), false)) return false;
        BlockHitResult hitResult = new BlockHitResult(hitPos, opposite, neighbor, false);
        InteractionHand hand = block.getHand() != null ? block.getHand() : InteractionHand.MAIN_HAND;
        mc.getConnection().send(new ServerboundUseItemOnPacket(hand, hitResult, 0));
        if (swing) {
            if (hand == InteractionHand.MAIN_HAND) {
                mc.player.swing(InteractionHand.MAIN_HAND);
            } else {
                mc.getConnection().send(new ServerboundSwingPacket(hand));
            }
        }
        return true;
    }
    public static boolean canPlace(BlockPos pos, boolean strictDirection) {
        if (!mc.level.getBlockState(pos).canBeReplaced()) return false;
        if (!mc.level.isUnobstructed(Blocks.OBSIDIAN.defaultBlockState(), pos, net.minecraft.world.phys.shapes.CollisionContext.empty())) return false;
        AABB checkBox = AABB.unitCubeFromLowerCorner(Vec3.atCenterOf(pos));
        List<net.minecraft.world.entity.Entity> entities = mc.level.getEntities(null, checkBox);
        for (net.minecraft.world.entity.Entity entity : entities) {
            if (!entity.isSpectator() && entity.isAlive()) {
                return false;
            }
        }
        return !strictDirection || getPlaceSide(pos) != null;
    }
    /**
     * A neighbour face that can actually be clicked to place at {@code pos}.
     *
     * Meteor's own {@code BlockUtils.getPlaceSide} only rejects <i>air</i> neighbours, so it happily
     * returns a snow layer, grass or any other replaceable block — and clicking a replaceable block
     * makes vanilla place into <i>that</i> block instead of offsetting to {@code pos}, so the block
     * lands in the wrong spot. This one requires a real, non-replaceable face (which also covers air
     * and fluids, both replaceable) and skips blocks whose right-click opens a GUI.
     */
    public static Direction getPlaceSide(BlockPos pos) {
        if (isSolidFace(pos.below())) return Direction.DOWN;
        for (Direction side : Direction.Plane.HORIZONTAL) {
            if (isSolidFace(pos.relative(side))) return side;
        }
        if (isSolidFace(pos.above())) return Direction.UP;
        return null;
    }

    private static boolean isSolidFace(BlockPos pos) {
        BlockState state = mc.level.getBlockState(pos);
        return !state.canBeReplaced() && !BlockUtils.isClickable(state.getBlock());
    }

    /**
     * {@code BlockUtils.place} with {@link #getPlaceSide} in place of Meteor's air-only side check —
     * same rotation priority, swap and swap-back behaviour, just aimed at a face that works.
     */
    public static boolean placeOnSolidSide(BlockPos pos, FindItemResult item, boolean rotate, int rotationPriority, boolean swapBack) {
        if (!item.found() || !BlockUtils.canPlace(pos)) return false;

        Direction side = getPlaceSide(pos);
        if (side == null) return false;

        InteractionHand hand = item.isOffhand() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        if (hand == InteractionHand.MAIN_HAND && !item.isHotbar()) return false;

        Vec3 hitPos = Vec3.atCenterOf(pos)
            .add(side.getStepX() * 0.5, side.getStepY() * 0.5, side.getStepZ() * 0.5);
        BlockHitResult hit = new BlockHitResult(hitPos, side.getOpposite(), pos.relative(side), false);

        Runnable place = () -> {
            boolean swap = hand == InteractionHand.MAIN_HAND;
            if (swap) InvUtils.swap(item.slot(), swapBack);
            BlockUtils.interact(hit, hand, true);
            if (swap && swapBack) InvUtils.swapBack();
        };

        if (rotate) Rotations.rotate(Rotations.getYaw(hitPos), Rotations.getPitch(hitPos), rotationPriority, place);
        else place.run();

        return true;
    }
    public static BlockPos getDirectionalPlacement(float yaw, BlockPos basePos) {
        float normalizedYaw = yaw % 360.0f;
        if (normalizedYaw < 0.0f) normalizedYaw += 360.0f;
        if (normalizedYaw >= 22.5 && normalizedYaw < 67.5) return basePos.south().west();
        else if (normalizedYaw >= 67.5 && normalizedYaw < 112.5) return basePos.west();
        else if (normalizedYaw >= 112.5 && normalizedYaw < 157.5) return basePos.north().west();
        else if (normalizedYaw >= 157.5 && normalizedYaw < 202.5) return basePos.north();
        else if (normalizedYaw >= 202.5 && normalizedYaw < 247.5) return basePos.north().east();
        else if (normalizedYaw >= 247.5 && normalizedYaw < 292.5) return basePos.east();
        else if (normalizedYaw >= 292.5 && normalizedYaw < 337.5) return basePos.south().east();
        else return basePos.south();
    }
    public static boolean isPhasing() {
        if (mc.player == null) return false;
        AABB bb = mc.player.getBoundingBox();
        int minX = net.minecraft.util.Mth.floor(bb.minX);
        int maxX = net.minecraft.util.Mth.floor(bb.maxX) + 1;
        int minY = net.minecraft.util.Mth.floor(bb.minY);
        int maxY = net.minecraft.util.Mth.floor(bb.maxY) + 1;
        int minZ = net.minecraft.util.Mth.floor(bb.minZ);
        int maxZ = net.minecraft.util.Mth.floor(bb.maxZ) + 1;
        for (int x = minX; x < maxX; x++) {
            for (int y = minY; y < maxY; y++) {
                for (int z = minZ; z < maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (!mc.level.getBlockState(pos).getCollisionShape(mc.level, pos).isEmpty()) {
                        AABB blockBox = new AABB(x, y, z, x + 1.0, y + 1.0, z + 1.0);
                        if (bb.intersects(blockBox)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }
    public static int getEnderPearlSlot() {
        if (mc.player == null) return -1;
        for (int i = 0; i < 45; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.getItem() == net.minecraft.world.item.Items.ENDER_PEARL) {
                return i;
            }
        }
        return -1;
    }
    public static void clickSlot(int slot, net.minecraft.world.inventory.ClickType actionType) {
        if (mc.gameMode != null && mc.player != null) {
            mc.gameMode.handleInventoryMouseClick(0, slot, 0, actionType, mc.player);
        }
    }
    public static boolean isPhased() {
        return isPhasing();
    }
    public static boolean isDoublePhased() {
        if (mc.player == null || mc.level == null) return false;
        AABB playerBox = mc.player.getBoundingBox();
        boolean feetBlocked = false;
        boolean headBlocked = false;
        for (int x = (int) Math.floor(playerBox.minX); x <= Math.floor(playerBox.maxX); x++) {
            for (int z = (int) Math.floor(playerBox.minZ); z <= Math.floor(playerBox.maxZ); z++) {
                BlockPos feetPos = new BlockPos(x, (int) Math.floor(playerBox.minY), z);
                if (!mc.level.getBlockState(feetPos).getCollisionShape(mc.level, feetPos).isEmpty()) {
                    feetBlocked = true;
                }
                BlockPos headPos = new BlockPos(x, (int) Math.floor(playerBox.maxY), z);
                if (!mc.level.getBlockState(headPos).getCollisionShape(mc.level, headPos).isEmpty()) {
                    headBlocked = true;
                }
                if (feetBlocked && headBlocked) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean placeBlockPacket(BlockPos pos, int hotbarSlot, boolean offhand, boolean rotate, int rotateTicks) {
        if (!BlockUtils.canPlace(pos) || !mc.level.getWorldBorder().isWithinBounds(pos)) return false;
        if (!offhand && (hotbarSlot < 0 || hotbarSlot > 8)) return false;

        Direction side = getPlaceSide(pos);
        BlockPos neighbour = side == null ? pos : pos.relative(side);
        Direction hitSide = side == null ? Direction.UP : side.getOpposite();
        Vec3 hitPos = Vec3.atCenterOf(pos);
        if (side != null) {
            hitPos = hitPos.add(side.getStepX() * 0.5, side.getStepY() * 0.5, side.getStepZ() * 0.5);
        }

        InteractionHand hand = offhand ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        Vec3 finalHitPos = hitPos;
        Direction finalHitSide = hitSide;
        BlockPos finalNeighbour = neighbour;

        Runnable place = () -> {
            boolean swapped = false;
            if (!offhand) {
                InvUtils.swap(hotbarSlot, true);
                swapped = true;
            }

            Minecraft.getInstance().player.connection.send(
                new ServerboundUseItemOnPacket(hand, new BlockHitResult(finalHitPos, finalHitSide, finalNeighbour, false), 0)
            );
            Minecraft.getInstance().player.connection.send(new ServerboundSwingPacket(hand));

            if (swapped) InvUtils.swapBack();
        };

        if (rotate) {
            Rotations.rotate(Rotations.getYaw(hitPos), Rotations.getPitch(hitPos), rotateTicks, place);
        } else {
            place.run();
        }

        return true;
    }

    public static boolean placeBlockPacket(BlockPos pos, FindItemResult item, boolean rotate, int rotateTicks) {
        return placeBlockPacket(pos, item, rotate, rotateTicks, true, true);
    }

    public static boolean placeBlockPacket(BlockPos pos, FindItemResult item, boolean rotate, int rotateTicks, boolean airPlace) {
        return placeBlockPacket(pos, item, rotate, rotateTicks, airPlace, true);
    }

    /**
     * @param swapBack When false, the hotbar selection is kept after placing (only swaps if the slot needs to change).
     *                 Use false for packet-build highway placing to minimise UpdateSelectedSlot packets.
     *                 Use true (default) for PvP modules that need the hand restored after each place.
     */
    public static boolean placeBlockPacket(BlockPos pos, FindItemResult item, boolean rotate, int rotateTicks, boolean airPlace, boolean swapBack) {
        if (!BlockUtils.canPlace(pos) || !mc.level.getWorldBorder().isWithinBounds(pos)) return false;

        Direction side = getPlaceSide(pos);
        if (side == null && !airPlace) return false;
        BlockPos neighbour = side == null ? pos : pos.relative(side);
        Direction hitSide = side == null ? Direction.UP : side.getOpposite();
        Vec3 hitPos = Vec3.atCenterOf(pos);
        if (side != null) {
            hitPos = hitPos.add(side.getStepX() * 0.5, side.getStepY() * 0.5, side.getStepZ() * 0.5);
        }

        InteractionHand hand = item.isOffhand() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        Vec3 finalHitPos = hitPos;
        Direction finalHitSide = hitSide;
        BlockPos finalNeighbour = neighbour;

        Runnable place = () -> {
            boolean swapped = false;
            if (item.isHotbar()) {
                if (swapBack) {
                    InvUtils.swap(item.slot(), true);
                    swapped = true;
                } else if (Minecraft.getInstance().player.getInventory().getSelectedSlot() != item.slot()) {
                    InvUtils.swap(item.slot(), false);
                }
            }

            Minecraft.getInstance().player.connection.send(
                new ServerboundUseItemOnPacket(hand, new BlockHitResult(finalHitPos, finalHitSide, finalNeighbour, false), 0)
            );
            Minecraft.getInstance().player.connection.send(new ServerboundSwingPacket(hand));

            if (swapped) InvUtils.swapBack();
        };

        if (rotate) {
            Rotations.rotate(Rotations.getYaw(hitPos), Rotations.getPitch(hitPos), rotateTicks, place);
        } else {
            place.run();
        }

        return true;
    }
}
