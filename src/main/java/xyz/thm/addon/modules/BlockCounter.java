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
        super(THMAddon.CATEGORY, "BlockCounter", "Zählt ausgewählte Blöcke in der Nähe und zeigt die Anzahl im Chat.");
    }

    private final Setting<List<Block>> blocks = sgGeneral.add(new BlockListSetting.Builder()
        .name("blocks-to-search")
        .description("Blocks it searches for")
        .defaultValue(Blocks.OBSIDIAN)
        .build()
    );

    //gotta optimize more later
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

        int radius = radiusChunks.get() * 16; // Radius in Blöcken
        countBlocks(mc.world, mc.player.getBlockPos(), radius);
        toggle();
    }

    public void countBlocks(World world, BlockPos center, int radius) {
        blockCounts.clear();

        for (int x = center.getX() - radius; x <= center.getX() + radius; x++) {
            for (int y = center.getY() - radius; y <= center.getY() + radius; y++) {
                for (int z = center.getZ() - radius; z <= center.getZ() + radius; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    Block block = world.getBlockState(pos).getBlock();

                    if (blocks.get().contains(block)) {
                        blockCounts.put(block, blockCounts.getOrDefault(block, 0) + 1);
                    }
                }
            }
        }

        if (!blockCounts.isEmpty()) {
            blockCounts.forEach((block, count) ->
                ChatUtils.info(block.getName().getString() + ": " + count)
            );
            ChatUtils.info("Total: " + getTotalCount());
        } else {
            ChatUtils.info("Keine ausgewählten Blöcke gefunden.");
        }

    }

    public Map<Block, Integer> getBlockCounts() {
        return blockCounts;
    }

    public int getTotalCount() {
        return blockCounts.values().stream().mapToInt(Integer::intValue).sum();
    }
}
