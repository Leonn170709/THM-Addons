package xyz.thm.addon.modules;

import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.block.Blocks;
import xyz.thm.addon.modules.HighwayBuilderTHM.*;
import xyz.thm.addon.THMAddon;

public class HighwayProfiles extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    public final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("profile")
        .description("Which highway profile to use.")
        .defaultValue(Mode.HighwayBuilding)
        .build()
    );
    private final Setting<Boolean> toggleModules = sgGeneral.add(new BoolSetting.Builder()
            .name("toggle-modules")
            .description("Turn on Highwaybuilder when toggled.")
      .defaultValue(true)
      .build()
      );

    private final Setting<Boolean> restore = sgGeneral.add(new BoolSetting.Builder()
        .name("restore-values")
        .description("Restores original values on deactivate")
        .defaultValue(true)
        .build()
    );
    // Store original values
    private int savedWidth;
    private int savedHeight;
    private boolean savedMineAboveRailings, savedBuildRailings;
    private java.util.List<net.minecraft.block.Block> savedBlocksToPlace; // Store the original blocks

    public HighwayProfiles() {
        super(THMAddon.MAIN, "Highway-Profiles", "Allows you to switch Highway between profiles");
    }

    @Override
    public void onActivate() {
        if (mc.player == null || mc.world == null) return;

        HighwayBuilderTHM hwBuilder = Modules.get().get(HighwayBuilderTHM.class);
        if (hwBuilder == null) return;

        // Save original values before changing them
        savedWidth = hwBuilder.width.get();
        savedHeight = hwBuilder.height.get();
        savedMineAboveRailings = hwBuilder.mineAboveRailings.get();
        savedBlocksToPlace = hwBuilder.blocksToPlace.get();
        savedBuildRailings = hwBuilder.railings.get();

        switch (mode.get()) {
            case HighwayBuilding -> {
                hwBuilder.width.set(5);
                hwBuilder.height.set(3);
                hwBuilder.blocksToPlace.set(java.util.List.of(Blocks.OBSIDIAN));
                hwBuilder.mineAboveRailings.set(true);
                hwBuilder.railings.set(true);
            }
            case HighwayDigging -> {
                hwBuilder.blocksToPlace.set(java.util.List.of(Blocks.NETHERRACK, Blocks.BASALT, Blocks.BLACKSTONE));
                hwBuilder.width.set(7);
                hwBuilder.height.set(4);
                hwBuilder.mineAboveRailings.set(false);
                hwBuilder.railings.set(false);
            }
        }
        if (toggleModules.get()) {
            hwBuilder.toggle();
        }

    }

    @Override
    public void onDeactivate() {
        HighwayBuilderTHM hwBuilder = Modules.get().get(HighwayBuilderTHM.class);
        if (hwBuilder == null) return;
        if (restore.get()) {
            // Restore original values
            hwBuilder.width.set(savedWidth);
            hwBuilder.height.set(savedHeight);
            hwBuilder.mineAboveRailings.set(savedMineAboveRailings);
            hwBuilder.blocksToPlace.set(savedBlocksToPlace);
            hwBuilder.railings.set(savedBuildRailings);
        }
        if (toggleModules.get() && hwBuilder.isActive()) {
            hwBuilder.toggle();
        }
    }

    public enum Mode {
        HighwayBuilding,
        HighwayDigging
    }
}
