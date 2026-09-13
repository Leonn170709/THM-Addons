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
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

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
        BlockPos neighbor = pos.offset(side);
        Direction opposite = side.getOpposite();
        Vec3d hitPos = Vec3d.ofCenter(neighbor).add(Vec3d.of(opposite.getVector()).multiply(0.5));
        if (rotate) {
            Rotations.rotate(Rotations.getYaw(hitPos), Rotations.getPitch(hitPos));
        }
        if (block.getHand() == null && !InvUtils.swap(block.slot(), false)) return false;
        BlockHitResult hitResult = new BlockHitResult(hitPos, opposite, neighbor, false);
        Hand hand = block.getHand() != null ? block.getHand() : Hand.MAIN_HAND;
        mc.getNetworkHandler().sendPacket(new PlayerInteractBlockC2SPacket(hand, hitResult, 0));
        if (swing) {
            if (hand == Hand.MAIN_HAND) {
                mc.player.swingHand(Hand.MAIN_HAND);
            } else {
                mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(hand));
            }
        }
        return true;
    }
    public static boolean canPlace(BlockPos pos, boolean strictDirection) {
        if (!mc.world.getBlockState(pos).isReplaceable()) return false;
        if (!mc.world.canPlace(Blocks.OBSIDIAN.getDefaultState(), pos, net.minecraft.block.ShapeContext.absent())) return false;
        Box checkBox = Box.from(Vec3d.ofCenter(pos));
        List<net.minecraft.entity.Entity> entities = mc.world.getOtherEntities(null, checkBox);
        for (net.minecraft.entity.Entity entity : entities) {
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
        if (isSolidFace(pos.down())) return Direction.DOWN;
        for (Direction side : Direction.Type.HORIZONTAL) {
            if (isSolidFace(pos.offset(side))) return side;
        }
        if (isSolidFace(pos.up())) return Direction.UP;
        return null;
    }

    private static boolean isSolidFace(BlockPos pos) {
        BlockState state = mc.world.getBlockState(pos);
        return !state.isReplaceable() && !BlockUtils.isClickable(state.getBlock());
    }

    /**
     * {@code BlockUtils.place} with {@link #getPlaceSide} in place of Meteor's air-only side check —
     * same rotation priority, swap and swap-back behaviour, just aimed at a face that works.
     */
    public static boolean placeOnSolidSide(BlockPos pos, FindItemResult item, boolean rotate, int rotationPriority, boolean swapBack) {
        if (!item.found() || !BlockUtils.canPlace(pos)) return false;

        Direction side = getPlaceSide(pos);
        if (side == null) return false;

        Hand hand = item.isOffhand() ? Hand.OFF_HAND : Hand.MAIN_HAND;
        if (hand == Hand.MAIN_HAND && !item.isHotbar()) return false;

        Vec3d hitPos = Vec3d.ofCenter(pos)
            .add(side.getOffsetX() * 0.5, side.getOffsetY() * 0.5, side.getOffsetZ() * 0.5);
        BlockHitResult hit = new BlockHitResult(hitPos, side.getOpposite(), pos.offset(side), false);

        Runnable place = () -> {
            boolean swap = hand == Hand.MAIN_HAND;
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
        Box bb = mc.player.getBoundingBox();
        int minX = net.minecraft.util.math.MathHelper.floor(bb.minX);
        int maxX = net.minecraft.util.math.MathHelper.floor(bb.maxX) + 1;
        int minY = net.minecraft.util.math.MathHelper.floor(bb.minY);
        int maxY = net.minecraft.util.math.MathHelper.floor(bb.maxY) + 1;
        int minZ = net.minecraft.util.math.MathHelper.floor(bb.minZ);
        int maxZ = net.minecraft.util.math.MathHelper.floor(bb.maxZ) + 1;
        for (int x = minX; x < maxX; x++) {
            for (int y = minY; y < maxY; y++) {
                for (int z = minZ; z < maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (!mc.world.getBlockState(pos).getCollisionShape(mc.world, pos).isEmpty()) {
                        Box blockBox = new Box(x, y, z, x + 1.0, y + 1.0, z + 1.0);
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
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.getItem() == net.minecraft.item.Items.ENDER_PEARL) {
                return i;
            }
        }
        return -1;
    }
    public static void clickSlot(int slot, net.minecraft.screen.slot.SlotActionType actionType) {
        if (mc.interactionManager != null && mc.player != null) {
            mc.interactionManager.clickSlot(0, slot, 0, actionType, mc.player);
        }
    }
    public static boolean isPhased() {
        return isPhasing();
    }
    public static boolean isDoublePhased() {
        if (mc.player == null || mc.world == null) return false;
        Box playerBox = mc.player.getBoundingBox();
        boolean feetBlocked = false;
        boolean headBlocked = false;
        for (int x = (int) Math.floor(playerBox.minX); x <= Math.floor(playerBox.maxX); x++) {
            for (int z = (int) Math.floor(playerBox.minZ); z <= Math.floor(playerBox.maxZ); z++) {
                BlockPos feetPos = new BlockPos(x, (int) Math.floor(playerBox.minY), z);
                if (!mc.world.getBlockState(feetPos).getCollisionShape(mc.world, feetPos).isEmpty()) {
                    feetBlocked = true;
                }
                BlockPos headPos = new BlockPos(x, (int) Math.floor(playerBox.maxY), z);
                if (!mc.world.getBlockState(headPos).getCollisionShape(mc.world, headPos).isEmpty()) {
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
        if (!BlockUtils.canPlace(pos) || !mc.world.getWorldBorder().contains(pos)) return false;
        if (!offhand && (hotbarSlot < 0 || hotbarSlot > 8)) return false;

        Direction side = getPlaceSide(pos);
        BlockPos neighbour = side == null ? pos : pos.offset(side);
        Direction hitSide = side == null ? Direction.UP : side.getOpposite();
        Vec3d hitPos = Vec3d.ofCenter(pos);
        if (side != null) {
            hitPos = hitPos.add(side.getOffsetX() * 0.5, side.getOffsetY() * 0.5, side.getOffsetZ() * 0.5);
        }

        Hand hand = offhand ? Hand.OFF_HAND : Hand.MAIN_HAND;
        Vec3d finalHitPos = hitPos;
        Direction finalHitSide = hitSide;
        BlockPos finalNeighbour = neighbour;

        Runnable place = () -> {
            boolean swapped = false;
            if (!offhand) {
                InvUtils.swap(hotbarSlot, true);
                swapped = true;
            }

            MinecraftClient.getInstance().player.networkHandler.sendPacket(
                new PlayerInteractBlockC2SPacket(hand, new BlockHitResult(finalHitPos, finalHitSide, finalNeighbour, false), 0)
            );
            MinecraftClient.getInstance().player.networkHandler.sendPacket(new HandSwingC2SPacket(hand));

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
        if (!BlockUtils.canPlace(pos) || !mc.world.getWorldBorder().contains(pos)) return false;

        Direction side = getPlaceSide(pos);
        if (side == null && !airPlace) return false;
        BlockPos neighbour = side == null ? pos : pos.offset(side);
        Direction hitSide = side == null ? Direction.UP : side.getOpposite();
        Vec3d hitPos = Vec3d.ofCenter(pos);
        if (side != null) {
            hitPos = hitPos.add(side.getOffsetX() * 0.5, side.getOffsetY() * 0.5, side.getOffsetZ() * 0.5);
        }

        Hand hand = item.isOffhand() ? Hand.OFF_HAND : Hand.MAIN_HAND;
        Vec3d finalHitPos = hitPos;
        Direction finalHitSide = hitSide;
        BlockPos finalNeighbour = neighbour;

        Runnable place = () -> {
            boolean swapped = false;
            if (item.isHotbar()) {
                if (swapBack) {
                    InvUtils.swap(item.slot(), true);
                    swapped = true;
                } else if (MinecraftClient.getInstance().player.getInventory().getSelectedSlot() != item.slot()) {
                    InvUtils.swap(item.slot(), false);
                }
            }

            MinecraftClient.getInstance().player.networkHandler.sendPacket(
                new PlayerInteractBlockC2SPacket(hand, new BlockHitResult(finalHitPos, finalHitSide, finalNeighbour, false), 0)
            );
            MinecraftClient.getInstance().player.networkHandler.sendPacket(new HandSwingC2SPacket(hand));

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
