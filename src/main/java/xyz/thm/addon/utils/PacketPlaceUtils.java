package xyz.thm.addon.utils;

import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public final class PacketPlaceUtils {
    private PacketPlaceUtils() {}

    public static boolean placeBlockPacket(BlockPos pos, int hotbarSlot, boolean offhand, boolean rotate, int rotateTicks) {
        if (!BlockUtils.canPlace(pos)) return false;
        if (!offhand && (hotbarSlot < 0 || hotbarSlot > 8)) return false;

        Direction side = BlockUtils.getPlaceSide(pos);
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
        return placeBlockPacket(pos, item, rotate, rotateTicks, true);
    }

    public static boolean placeBlockPacket(BlockPos pos, FindItemResult item, boolean rotate, int rotateTicks, boolean airPlace) {
        if (!BlockUtils.canPlace(pos)) return false;

        Direction side = BlockUtils.getPlaceSide(pos);
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
                InvUtils.swap(item.slot(), true);
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
}
