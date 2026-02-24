package xyz.thm.addon.utils;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Difficulty;
import org.jetbrains.annotations.Nullable;
import xyz.thm.addon.THMAddon;

import static meteordevelopment.meteorclient.MeteorClient.mc;
import static meteordevelopment.meteorclient.utils.world.BlockUtils.canPlace;


public class THMUtils {
    private THMUtils() {}

    // Block Pos

    public static boolean canPlaceTHM(BlockPos blockPos) {
        return canPlace(blockPos, false);
    }

    public static BlockPos forward(BlockPos pos, int distance) {
        return switch (mc.player.getHorizontalFacing()) {
            case SOUTH -> pos.south(distance);
            case NORTH -> pos.north(distance);
            case WEST -> pos.west(distance);
            default -> pos.east(distance);
        };
    }

    public static BlockPos backward(BlockPos pos, int distance) {
        return switch (mc.player.getHorizontalFacing()) {
            case SOUTH -> pos.north(distance);
            case NORTH -> pos.south(distance);
            case WEST -> pos.east(distance);
            default -> pos.west(distance);
        };
    }

    public static BlockPos left(BlockPos pos, int distance) {
        return switch (mc.player.getHorizontalFacing()) {
            case SOUTH -> pos.east(distance);
            case NORTH -> pos.west(distance);
            case WEST -> pos.south(distance);
            default -> pos.north(distance);
        };
    }

    public static BlockPos right(BlockPos pos, int distance) {
        return switch (mc.player.getHorizontalFacing()) {
            case SOUTH -> pos.west(distance);
            case NORTH -> pos.east(distance);
            case WEST -> pos.north(distance);
            default -> pos.south(distance);
        };
    }

    // Highway Axes

    public static int getHighway() {
        double playerZ = mc.player.getZ();
        double playerX = mc.player.getX();
        boolean x = Math.abs(playerZ) < 5;
        boolean z = Math.abs(playerX) < 5;
        boolean xp = Math.signum(playerX) == 1.0;
        boolean zp = Math.signum(playerZ) == 1.0;
        boolean diag = Math.abs(Math.abs(playerX) - Math.abs(playerZ)) < 5;

        if (x && xp) return 1;
        if (x) return 2;
        if (z && zp) return 3;
        if (z) return 4;
        if (diag && xp && zp) return 5;
        if (diag && !xp && zp) return 6;
        if (diag && xp) return 7;
        if (diag) return 8;
        return -1;
    }

    // TODO: Add toast notifications system (reference Baritone?)

    public static boolean isNot6B6T() {
        assert mc.world != null;
        if (FabricLoader.getInstance().isDevelopmentEnvironment()) return false; // Bypass check in dev environment
        if (mc.isIntegratedServerRunning()) return true;
        ServerInfo server = mc.getCurrentServerEntry();
        if (server == null) return false;
        if (mc.world.getDifficulty() != Difficulty.HARD) return true;
        return !server.address.endsWith("6b6t.org");
    }
    public static void pickupAndReturn() {
        if (mc.player == null) return;
        int savedX;
        int savedZ;
        final boolean[] finishedbar = {false};
        final IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
        savedX = (int) mc.player.getX()-1;
        savedZ = (int) mc.player.getZ()-1;

        baritone.getCommandManager().execute("pickup minecraft:obsidian");
        new Thread(() -> {
            try {
                THMAddon.LOG.info("Waiting 10 seconds for baritone to pick up");
                Thread.sleep(10000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            baritone.getPathingBehavior().cancelEverything();
            baritone.getCommandManager().execute("goto " + savedX + " " + savedZ);
            while (!finishedbar[0]) {
                if (Math.abs(mc.player.getX() - savedX) == 0 && Math.abs(mc.player.getZ() - savedZ) == 0) {
                    finishedbar[0] = true;
                    baritone.getPathingBehavior().cancelEverything();
                }
            }
        }).start();

    }
    // TODO: Add this to Highway builder so it picks up all the splattered obsidian
    private boolean checkModLoaded(String... modIds)
    {
        boolean loaded = false;
        for (String id : modIds)
        {
            if (FabricLoader.getInstance().isModLoaded(id))
            {
                loaded = true;
                break;
            }
        }
        if (!loaded)
        {
            THMAddon.LOG.error("{} not found, disabling modules that require it.", modIds[0]);
        }
        return loaded;
    }
    public static boolean checkThreshold(ItemStack i, double threshold) {
        return getDamage(i) <= threshold;
    }

    public static double getDamage(ItemStack i) {
        return (((double) (i.getMaxDamage() - i.getDamage()) / i.getMaxDamage()) * 100);
    }
    public static Vec3d positionInDirection(Vec3d pos, double yaw, double distance) {
        Vec3d offset = yawToDirection(yaw).multiply(distance);
        return pos.add(offset);
    }

    public static Vec3d yawToDirection(double yaw) {
        yaw = yaw * Math.PI / 180;
        double x = -Math.sin(yaw);
        double z = Math.cos(yaw);
        return new Vec3d(x, 0, z);
    }

    public static double distancePointToDirection(Vec3d point, Vec3d direction, @Nullable Vec3d start) {
        if (start == null) start = Vec3d.ZERO;
        point = point.multiply(new Vec3d(1, 0, 1));
        start = start.multiply(new Vec3d(1, 0, 1));
        direction = direction.multiply(new Vec3d(1, 0, 1));
        Vec3d directionVec = point.subtract(start);
        double projectionLength = directionVec.dotProduct(direction) / direction.lengthSquared();
        Vec3d projection = direction.multiply(projectionLength);
        Vec3d perp = directionVec.subtract(projection);
        return perp.length();
    }

    public static double angleOnAxis(double yaw) {
        if (yaw < 0) yaw += 360;
        return Math.round(yaw / 45.0f) * 45;
    }
    public static long generateTimestamp() {
        // Get current time in milliseconds since epoch (UTC)
        return System.currentTimeMillis();
    }
    private boolean canceled = false;
    public boolean isCanceled() {
        return canceled;
    }
    public void cancel() {
        this.canceled = true;
    }
    public void setCanceled(boolean canceled) {
        this.canceled = canceled;
    }
    public static boolean isOnMainHighway() {
        // Get player's current X and Z coordinates
        assert mc.player != null;
        double playerX = mc.player.getX();
        double playerZ = mc.player.getZ();

        // Check if X coordinate is on main highway (0, 1, or -1)
        boolean xOnHighway = isMainHighwayCoordinate(playerX);

        // Check if Z coordinate is on main highway (0, 1, or -1)
        boolean zOnHighway = isMainHighwayCoordinate(playerZ);

        // Check if coordinates are approximately equal with tolerance of 5
        boolean coordsEqual = Math.abs(playerX - playerZ) <= 5;

        // Return true if on main highway or coordinates are equal
        return (xOnHighway || zOnHighway) || coordsEqual;
    }

    // Helper method to check if a coordinate is a main highway value
    private static boolean isMainHighwayCoordinate(double coord) {
        // Check if coordinate is close to 0, 1, or -1
        double tolerance = 1.0;
        return Math.abs(coord % 1) <= tolerance || Math.abs(coord % 1) >= (1 - tolerance);
    }
}
