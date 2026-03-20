package xyz.thm.addon.system;

import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.System;
import meteordevelopment.meteorclient.systems.Systems;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.block.Blocks;
import net.minecraft.nbt.NbtCompound;
import xyz.thm.addon.THMAddon;
import xyz.thm.addon.modules.HighwayBuilderTHM;

public class THMSystem extends System<THMSystem> {
    public final Settings settings = new Settings();

    private final SettingGroup sgPrefix = settings.createGroup("Hash");
    private final SettingGroup sgProfiles = settings.createGroup("Highway Profiles");

    // Hash Settings
    private final Setting<String> hash = sgPrefix.add(new StringSetting.Builder()
        .name("Hash")
        .description("The Hash that you got")
        .defaultValue("SetYourHash")
        .build()
    );

    private final Setting<String> crackedPassword = sgPrefix.add(new StringSetting.Builder()
        .name("cracked-password")
        .description("Password used for cracked-account reconnect /login.")
        .defaultValue("")
        .build()
    );

    // Highway Profiles Settings
    public final Setting<Mode> mode = sgProfiles.add(new EnumSetting.Builder<Mode>()
        .name("profile")
        .description("Which highway profile to use.")
        .defaultValue(Mode.None)
        .build()
    );

    private final Setting<Boolean> toggleModules = sgProfiles.add(new BoolSetting.Builder()
        .name("toggle-modules")
        .description("Turn on Highwaybuilder when toggled.")
        .defaultValue(true)
        .build()
    );

    // Store original values
    private int savedWidth = -1;
    private int savedHeight = -1;
    private boolean savedMineAboveRailings = false;
    private boolean savedBuildRailings = false;
    private java.util.List<net.minecraft.block.Block> savedBlocksToPlace = null;

    public THMSystem() {
        super("THM-Addon");
    }

    public static THMSystem get() {
        return Systems.get(THMSystem.class);
    }

    public String getHash() {
        return hash.get();
    }

    public String getCrackedPassword() {
        return crackedPassword.get();
    }

    public void applyProfile() {
        HighwayBuilderTHM hwBuilder = Modules.get().get(HighwayBuilderTHM.class);
        if (hwBuilder == null) return;

        switch (mode.get()) {
            case None -> {
                // Only restore if values were previously saved
                if (savedWidth != -1) {
                    hwBuilder.width.set(savedWidth);
                    hwBuilder.height.set(savedHeight);
                    hwBuilder.mineAboveRailings.set(savedMineAboveRailings);
                    hwBuilder.blocksToPlace.set(savedBlocksToPlace);
                    hwBuilder.railings.set(savedBuildRailings);
                }
            }
            case HighwayBuilding -> {
                // Save original values before changing
                savedWidth = hwBuilder.width.get();
                savedHeight = hwBuilder.height.get();
                savedMineAboveRailings = hwBuilder.mineAboveRailings.get();
                savedBlocksToPlace = hwBuilder.blocksToPlace.get();
                savedBuildRailings = hwBuilder.railings.get();

                hwBuilder.width.set(5);
                hwBuilder.height.set(3);
                hwBuilder.blocksToPlace.set(java.util.List.of(Blocks.OBSIDIAN));
                hwBuilder.mineAboveRailings.set(true);
                hwBuilder.railings.set(true);
            }
            case HighwayDigging -> {
                // Save original values before changing
                savedWidth = hwBuilder.width.get();
                savedHeight = hwBuilder.height.get();
                savedMineAboveRailings = hwBuilder.mineAboveRailings.get();
                savedBlocksToPlace = hwBuilder.blocksToPlace.get();
                savedBuildRailings = hwBuilder.railings.get();

                hwBuilder.blocksToPlace.set(java.util.List.of(Blocks.NETHERRACK, Blocks.BASALT, Blocks.BLACKSTONE));
                hwBuilder.width.set(7);
                hwBuilder.height.set(4);
                hwBuilder.mineAboveRailings.set(false);
                hwBuilder.railings.set(false);
            }
        }
        if (toggleModules.get() && !hwBuilder.isActive()) {
            hwBuilder.toggle();
        }
    }

    public void restoreProfile() {
        HighwayBuilderTHM hwBuilder = Modules.get().get(HighwayBuilderTHM.class);
        if (hwBuilder == null) return;
        if (toggleModules.get() && hwBuilder.isActive()) {
            hwBuilder.toggle();
        }
    }

    @Override
    public NbtCompound toTag() {
        NbtCompound tag = new NbtCompound();
        tag.putString("version", THMAddon.VERSION);
        tag.put("settings", settings.toTag());
        return tag;
    }

    @Override
    public THMSystem fromTag(NbtCompound tag) {
        if (tag.contains("settings")) {
            settings.fromTag(tag.getCompound("settings").orElse(new NbtCompound()));
        }
        return this;
    }

    public enum Mode {
        None,
        HighwayBuilding,
        HighwayDigging
    }
}
