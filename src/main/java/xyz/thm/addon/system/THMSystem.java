package xyz.thm.addon.system;

import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.System;
import meteordevelopment.meteorclient.systems.Systems;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.block.Blocks;
import net.minecraft.nbt.NbtCompound;
import xyz.thm.addon.THMAddon;
import xyz.thm.addon.modules.HighwayBuilderTHM;
import xyz.thm.addon.utils.KitbotChatRouter;

public class THMSystem extends System<THMSystem> {
    public final Settings settings = new Settings();

    private final SettingGroup sgPrefix = settings.createGroup("Hash");
    private final SettingGroup sgProfiles = settings.createGroup("Highway Profiles");
    private final SettingGroup sgPvp = settings.createGroup("PVP");
    private final SettingGroup sgRender = settings.createGroup("THM Rendering");
    private final SettingGroup sgKitbot = settings.createGroup("KitBot");

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

    public final Setting<Boolean> ignoreThmMembers = sgPvp.add(new BoolSetting.Builder()
        .name("ignore-thm-members")
        .description("Ignore THM members in PvP modules.")
        .defaultValue(false)
        .build()
    );

    public static final String BRANCH_ALL = "All";
    public static final String BRANCH_MAIN = "Main";
    public static final String BRANCH_PVP = "PvP";

    public final Setting<String> showBranch = sgPvp.add(new ProvidedStringSetting.Builder()
        .name("show-branch")
        .description("Which branch members to show in THM member lists.")
        .defaultValue(BRANCH_ALL)
        .supplier(() -> new String[] { BRANCH_ALL, BRANCH_MAIN, BRANCH_PVP })
        .build()
    );

    public final Setting<Boolean> highlightInTab = sgRender.add(new BoolSetting.Builder()
        .name("highlight-in-tab")
        .description("Highlights THM members in the player tab list.")
        .defaultValue(false)
        .build()
    );

    public final Setting<Boolean> highlightNametags = sgRender.add(new BoolSetting.Builder()
        .name("highlight-nametags")
        .description("Highlights THM members in nametags.")
        .defaultValue(false)
        .build()
    );

    public final Setting<Boolean> useRankColor = sgRender.add(new BoolSetting.Builder()
        .name("use-rank-color")
        .description("Use the member's rank color instead of a single highlight color.")
        .defaultValue(true)
        .build()
    );

    public final Setting<meteordevelopment.meteorclient.utils.render.color.SettingColor> highlightColor = sgRender.add(new ColorSetting.Builder()
        .name("highlight-color")
        .description("Highlight color for THM members.")
        .defaultValue(new meteordevelopment.meteorclient.utils.render.color.SettingColor(255, 217, 94, 255))
        .visible(() -> !useRankColor.get())
        .build()
    );

    public final Setting<Boolean> showNametagIcon = sgRender.add(new BoolSetting.Builder()
        .name("show-nametag-icon")
        .description("Shows the THM icon before member names in nametags.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Boolean> kitbotChatRouterEnabled = sgKitbot.add(new BoolSetting.Builder()
        .name("kitbot-chat-router")
        .description("Routes recognized $kitbot chat commands through Kitbot Frontend.")
        .defaultValue(true)
        .onChanged(KitbotChatRouter::setEnabled)
        .build()
    );
    public final Setting<Type> nametagType = sgRender.add(new EnumSetting.Builder<Type>()
        .name("Icon Type")
        .description("Select the nametag rendering style")
        .defaultValue(Type.TransparentWhite)
        .visible(showNametagIcon::get)
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
        KitbotChatRouter.setEnabled(kitbotChatRouterEnabled.get());
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
                hwBuilder.kitbotRestockKit.set(HighwayBuilderTHM.KitbotRestockKit.Highway);
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
                hwBuilder.kitbotRestockKit.set(HighwayBuilderTHM.KitbotRestockKit.Pickaxe);
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
        KitbotChatRouter.setEnabled(kitbotChatRouterEnabled.get());
        return this;
    }

    public enum Mode {
        None,
        HighwayBuilding,
        HighwayDigging
    }
    public enum Type {
        Obby,
        TransparentWhite,
        TransparentBlack
    }
}
