package xyz.thm.addon;

import com.mojang.logging.LogUtils;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.addons.GithubRepo;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.commands.Commands;
import meteordevelopment.meteorclient.pathing.BaritoneUtils;
import meteordevelopment.meteorclient.systems.hud.Hud;
import meteordevelopment.meteorclient.systems.hud.HudGroup;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.Utils;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.minecraft.item.Items;
import org.slf4j.Logger;
import xyz.thm.addon.commands.Center;
import xyz.thm.addon.hud.*;
import xyz.thm.addon.modules.*;

import java.io.File;

public class THMAddon extends MeteorAddon {
    public static final String MOD_ID = "thm-addon";
    public static ModMetadata MOD_META;
    public static final Logger LOG = LogUtils.getLogger();
    public static final Category CATEGORY = new Category("THMAddon");
    public static final ModMetadata METADATA;
    public static final String VERSION;
    public static final Category MAIN;
    public static final Category PVP;
    public static final HudGroup HUD_GROUP = new HudGroup("THM");

    static {METADATA = FabricLoader.getInstance().getModContainer(MOD_ID).orElseThrow().getMetadata();
        VERSION = METADATA.getVersion().getFriendlyString();

        MAIN = new Category("THM Highway", Items.OBSIDIAN.getDefaultStack());
        PVP = new Category("THM PVP", Items.END_CRYSTAL.getDefaultStack());}

    public static File GetConfigFile(String key, String filename) {
        return new File(new File(new File(new File(MeteorClient.FOLDER, "thm"), key), Utils.getFileWorldName()), filename);
    }

    @Override
    public void onInitialize() {
        LOG.info("Initializing THM Addon");

        MOD_META = FabricLoader.getInstance().getModContainer(MOD_ID).orElseThrow().getMetadata();

        // Modules
        Modules.get().add(new HighwayBuilderTHM());
        Modules.get().add(new AxisViewer());
        Modules.get().add(new BlockCounter());
        Modules.get().add(new DiscordNotifs());
        //Modules.get().add(new WebhookEncrypt());
        Modules.get().add(new AntiDrop());
        Modules.get().add(new ScaffoldTHM());
        Modules.get().add(new OffhandManager());
        Modules.get().add(new HotbarManager());
        Modules.get().add(new UnfocusedFpsLimiter());
        Modules.get().add(new AntiConcrete());
        Modules.get().add(new AntiConcreteDetection());
        Modules.get().add(new AntiFeetPlace());
        Modules.get().add(new AutoConcrete());
        Modules.get().add(new AutoMinePlus());
        Modules.get().add(new PacketMinePlus());
        Modules.get().add(new ArmorNotify());
        Modules.get().add(new SurroundPlus());
        Modules.get().add(new Phase());
        Modules.get().add(new AutoPortal());
        Modules.get().add(new HighwayProfiles());
        if (BaritoneUtils.IS_AVAILABLE) {
            Modules.get().add(new HighwaySearcher());
        }


        //Commands
        Commands.add(new Center());


        //Hud
        Hud.get().register(OnlineFriendsList.INFO);
        Hud.get().register(DubCounter.INFO);
        Hud.get().register(HighwayHud.INFO);
        Hud.get().register(WelcomerHud.INFO);
        Hud.get().register(CrystalMetrics.INFO);
        Hud.get().register(MemberHud.INFO);


    }

    @Override
    public void onRegisterCategories() {

        Modules.registerCategory(MAIN);
        Modules.registerCategory(PVP);
    }

    @Override
    public String getPackage() {
        return "xyz.thm.addon";
    }

    @Override
    public String getWebsite() {
        return "https://github.com/Leonn170709/THM-Addons";
    }

    @Override
    public GithubRepo getRepo() {
        return new GithubRepo("Leonn170709", "THM-Addons", "1.21.11", null);
    }
}
