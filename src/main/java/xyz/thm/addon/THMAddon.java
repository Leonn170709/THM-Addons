package xyz.thm.addon;

import com.mojang.logging.LogUtils;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.addons.GithubRepo;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.commands.Commands;
import meteordevelopment.meteorclient.gui.GuiThemes;
import meteordevelopment.meteorclient.gui.tabs.Tabs;
import meteordevelopment.meteorclient.pathing.BaritoneUtils;
import meteordevelopment.meteorclient.systems.hud.Hud;
import meteordevelopment.meteorclient.systems.hud.HudGroup;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.Utils;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.fabricmc.loader.impl.util.log.Log;
import net.minecraft.item.Items;
import org.slf4j.Logger;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import xyz.thm.addon.commands.Center;
import xyz.thm.addon.commands.EclipCommand;
import xyz.thm.addon.gui.themes.*;
import xyz.thm.addon.hud.*;
import xyz.thm.addon.modules.*;
import xyz.thm.addon.modules.chesttracker.ChestTrackerModule;
import xyz.thm.addon.system.THMTab;
import xyz.thm.addon.utils.JoinPayload;
import xyz.thm.addon.utils.THMUtils;


import java.io.File;

public class THMAddon extends MeteorAddon implements ClientModInitializer {
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
    public void onInitializeClient() {
        PayloadTypeRegistry.playC2S().register(JoinPayload.ID, JoinPayload.CODEC);
        ClientPlayConnectionEvents.JOIN.register((listener, sender, client) -> {
            if (!THMUtils.isNot6B6T()) {
                sender.sendPacket(new JoinPayload());
                LOG.info("Join payload sent.");}
            LOG.info("too Late");
        });
    }

    @Override
    public void onInitialize() {
        LOG.info("Initializing THM Addon");

        MOD_META = FabricLoader.getInstance().getModContainer(MOD_ID).orElseThrow().getMetadata();

        // Modules
        Modules.get().add(new HighwayBuilderTHM());
        Modules.get().add(new THMHwyMonitor());
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
        Modules.get().add(new DiscordRPC());
        Modules.get().add(new TunnelMinerModule());
        Modules.get().add(new SignRender());
        Modules.get().add(new AfkLogout());
        Modules.get().add(new FlightBypass());
        Modules.get().add(new KitbotFrontend());
        Modules.get().add(new ChestTrackerModule());
        if (BaritoneUtils.IS_AVAILABLE) {
            Modules.get().add(new HighwaySearcher());
        }


        //Commands
        Commands.add(new Center());
        Commands.add(new EclipCommand());


        //Hud
        Hud.get().register(OnlineFriendsList.INFO);
        Hud.get().register(DubCounter.INFO);
        Hud.get().register(HighwayHud.INFO);
        Hud.get().register(WelcomerHud.INFO);
        Hud.get().register(CrystalMetrics.INFO);
        Hud.get().register(MemberHud.INFO);
        Hud.get().register(TunnelMinerHud.INFO);

        //Themes
        GuiThemes.add(DarkTheme.INSTANCE);
        GuiThemes.add(SnowyTheme.INSTANCE);
        GuiThemes.add(LambdaTheme.INSTANCE);
        GuiThemes.add(StardustTheme.INSTANCE);
        GuiThemes.add(MidnightTheme.INSTANCE);
        GuiThemes.add(MonochromeTheme.INSTANCE);
        GuiThemes.add(Nether.INSTANCE);

        //System/Tab
        Tabs.add(new THMTab());


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
