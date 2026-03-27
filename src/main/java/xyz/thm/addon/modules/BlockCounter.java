package xyz.thm.addon.modules;

import meteordevelopment.meteorclient.settings.BlockListSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import xyz.thm.addon.THMAddon;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BlockCounter extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    public BlockCounter() {
        super(THMAddon.MAIN, "BlockCounter", "Counts the selected Blocks");
    }

    private final Setting<List<Block>> blocks = sgGeneral.add(new BlockListSetting.Builder()
        .name("blocks-to-search")
        .description("Blocks it searches for")
        .defaultValue(Blocks.OBSIDIAN)
        .build()
    );

    private final Setting<Integer> radiusChunks = sgGeneral.add(new IntSetting.Builder()
        .name("radius-chunks")
        .description("Radius in Chunks to search")
        .defaultValue(1)
        .min(1)
        .max(32)
        .sliderMax(32)
        .sliderMin(1)
        .build()
    );

    private Map<Block, Integer> blockCounts = new HashMap<>();


    @Override
    public void onActivate() {
        if (mc.world == null || mc.player == null) return;
        info("Started Counting. Please wait and don't reenable");
        // starting async scan
        new Thread(() -> {
            int radius = radiusChunks.get() * 16; // Radius in Blöcken
            countBlocksOptimized(mc.world, mc.player.getBlockPos(), radius);
        }).start();
        toggle();

    }

    public void countBlocksOptimized(World world, BlockPos center, int radius) {
        blockCounts.clear();
        // Limiting Y to 500 blocks
        int yRadius = Math.min(500, radius);

        // Chunk based instead of blocks
        int chunkRadiusX = (radius + 15) / 16;
        int chunkCenterX = center.getX() >> 4;
        int chunkCenterZ = center.getZ() >> 4;

        long startTime = System.currentTimeMillis();
        int blocksScanned = 0;

        for (int chunkX = chunkCenterX - chunkRadiusX; chunkX <= chunkCenterX + chunkRadiusX; chunkX++) {
            for (int chunkZ = chunkCenterZ - chunkRadiusX; chunkZ <= chunkCenterZ + chunkRadiusX; chunkZ++) {
                // checking if chunk is in range
                int chunkBlockX = chunkX * 16;
                int chunkBlockZ = chunkZ * 16;

                if (Math.abs(chunkBlockX - center.getX()) > radius && Math.abs(chunkBlockX + 15 - center.getX()) > radius) continue;
                if (Math.abs(chunkBlockZ - center.getZ()) > radius && Math.abs(chunkBlockZ + 15 - center.getZ()) > radius) continue;

                // Searching Chunk for blocks
                for (int x = chunkBlockX; x < chunkBlockX + 16; x++) {
                    for (int z = chunkBlockZ; z < chunkBlockZ + 16; z++) {
                        for (int y = center.getY() - yRadius; y <= center.getY() + yRadius; y++) {
                            BlockPos pos = new BlockPos(x, y, z);
                            Block block = world.getBlockState(pos).getBlock();

                            if (blocks.get().contains(block)) {
                                blockCounts.put(block, blockCounts.getOrDefault(block, 0) + 1);
                            }
                            blocksScanned++;
                        }
                    }
                }
            }
        }

        long duration = System.currentTimeMillis() - startTime;

        // Showing results in chat
        if (!blockCounts.isEmpty()) {
            blockCounts.forEach((block, count) ->
                info(block.getName().getString() + ": (highlight)" + count)
            );
            info("Total: (highlight)" + getTotalCount() + " (highlight)| Scanned: " + blocksScanned + " blocks in " + duration + "ms");
            THMAddon.LOG.info("Scanned: " + blocksScanned + " blocks in " + duration + "ms");
        } else {
            info("No selected blocks found. (Scanned: " + blocksScanned + " blocks)");
        }
    }

    public Map<Block, Integer> getBlockCounts() {
        return blockCounts;
    }

    public int getTotalCount() {
        return blockCounts.values().stream().mapToInt(Integer::intValue).sum();
    }
}
