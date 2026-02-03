package xyz.thm.addon.utils;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
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
        if (FabricLoader.getInstance().isDevelopmentEnvironment()) return false; // Bypass check in dev environment
        if (mc.isIntegratedServerRunning()) return true;
        ServerInfo server = mc.getCurrentServerEntry();
        if (server == null) return false;
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
    private boolean canceled = false;
    public boolean isCanceled() {
        return canceled;
    }
    public void cancel() {
        this.canceled = true;
    }
    public void setCanceled(boolean canceled) {
        this.canceled = canceled;}

}
