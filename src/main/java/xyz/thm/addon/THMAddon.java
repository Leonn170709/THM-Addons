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
import meteordevelopment.meteorclient.utils.render.color.Color;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.minecraft.item.Items;
import org.slf4j.Logger;
import xyz.thm.addon.commands.Center;
import xyz.thm.addon.commands.DesyncCommand;
import xyz.thm.addon.commands.EclipCommand;
import xyz.thm.addon.commands.UUIDCommand;
import xyz.thm.addon.gui.themes.*;
import xyz.thm.addon.hud.*;
import xyz.thm.addon.modules.*;
import xyz.thm.addon.system.THMTab;
import xyz.thm.addon.utils.*;

import org.lwjgl.util.tinyfd.TinyFileDialogs;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

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
    public static final Color THMSideColor = new Color(145, 60, 255, 75);
    public static final Color THMColor = new Color(145, 60, 255, 255);

    static {METADATA = FabricLoader.getInstance().getModContainer(MOD_ID).orElseThrow().getMetadata();
        VERSION = METADATA.getVersion().getFriendlyString();
        MAIN = new Category("THM Highway", Items.OBSIDIAN.getDefaultStack());
        PVP = new Category("THM PVP", Items.END_CRYSTAL.getDefaultStack());}

    public static File GetConfigFile(String key, String filename) {
        return new File(new File(new File(new File(MeteorClient.FOLDER, "thm"), key), Utils.getFileWorldName()), filename);
    }
    @Override
    public void onInitializeClient() {
        if (!FabricLoader.getInstance().isModLoaded("anarchymod")) {
            PayloadTypeRegistry.playC2S().register(JoinPayload.ID, JoinPayload.CODEC);
            ClientPlayConnectionEvents.JOIN.register((listener, sender, client) -> {
                if (!THMUtils.isNot6B6T()) {
                    sender.sendPacket(new JoinPayload());
                    LOG.info("Join payload sent.");
                } else {
                    LOG.info("Join payload not sent.");
                }
            });
        }
    }

    @Override
    public void onInitialize() {
        LOG.info("Initializing THM Addon");
        FabricLoader loader = FabricLoader.getInstance();

        record ModOption(String modId, String displayName, String url) {}
        record RequiredMod(String groupName, String downloadUrl, ModOption... options) {}

        List<RequiredMod> required = List.of(
            new RequiredMod("Baritone",
                "https://github.com/Leonn170709/THM-Addons/raw/refs/heads/1.21.11/docs/SuperDuperFreeFileHost/baritone-meteor-1.21.11.jar",
                new ModOption("baritone-meteor", "Baritone Meteor Fork (Recommended)", null),
                new ModOption("baritone", "Baritone (Original)", null)
            )
            // Add more RequiredMod entries here as needed
        );

        List<RequiredMod> missing = new ArrayList<>();
        for (RequiredMod req : required) {
            boolean anyLoaded = Arrays.stream(req.options())
                .anyMatch(opt -> loader.isModLoaded(opt.modId()));
            if (!anyLoaded) missing.add(req);
        }

        if (!missing.isEmpty()) {
            StringBuilder msg = new StringBuilder("THM Addon is missing required dependencies:\n\n");
            List<String> downloadUrls = new ArrayList<>();

            for (RequiredMod req : missing) {
                if (req.options().length == 1) {
                    msg.append("- ").append(req.options()[0].displayName()).append("\n\n");
                } else {
                    msg.append(req.groupName()).append(" - install one of:\n");
                    for (ModOption opt : req.options()) {
                        msg.append("  - ").append(opt.displayName()).append("\n");
                    }
                    msg.append("\n");
                }

                // Only add download URL if the group has one declared
                if (req.downloadUrl() != null && !req.downloadUrl().isEmpty()) {
                    downloadUrls.add(req.downloadUrl());
                }
            }

            boolean hasDownload = !downloadUrls.isEmpty();
            msg.append(hasDownload
                ? "Click OK to open the download page, or Cancel to just close."
                : "The game will now close.");

            String depList = missing.stream().map(RequiredMod::groupName).collect(Collectors.joining(", "));
            LOG.error("[THM Addon] Missing dependencies: {}", depList);

            boolean openDownload = TinyFileDialogs.tinyfd_messageBox(
                "THM Addon - Missing Dependencies",
                msg.toString(),
                hasDownload ? "okcancel" : "ok",
                "error",
                true
            );

            if (hasDownload && openDownload) {
                for (String url : downloadUrls) {
                    try {
                        String os = System.getProperty("os.name").toLowerCase();
                        ProcessBuilder pb;
                        if (os.contains("win")) {
                            pb = new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", url);
                        } else if (os.contains("mac")) {
                            pb = new ProcessBuilder("open", url);
                        } else {
                            pb = new ProcessBuilder("xdg-open", url);
                        }
                        pb.start();
                    } catch (Exception e) {
                        LOG.error("Failed to open URL: {}", url, e);
                    }
                }
            }

            System.exit(1);
        }

        MOD_META = FabricLoader.getInstance().getModContainer(MOD_ID).orElseThrow().getMetadata();
        ServerStatusHandler.getInstance();
        ServerReconnectService.getInstance();
        KitbotChatRouter.getInstance();
        ThmMembers.initialize();

        // Modules
        Modules.get().add(new HighwayBuilderTHM());
        Modules.get().add(new ModuleManager());
        Modules.get().add(new AxisViewer());
        Modules.get().add(new DiscordNotifs());
        addOptionalModule("xyz.thm.addon.modules.WebhookEncrypt");
        Modules.get().add(new AntiDrop());
        Modules.get().add(new ScaffoldTHM());
        Modules.get().add(new Nuker());
        Modules.get().add(new PaketLimiter());
        Modules.get().add(new PacketLoggerTHM());
        Modules.get().add(new OffhandManager());
        Modules.get().add(new HotbarManager());
        Modules.get().add(new UnfocusedFpsLimiter());
        Modules.get().add(new AntiConcrete());
        Modules.get().add(new AntiConcreteDetection());
        Modules.get().add(new AntiFeetPlace());
        Modules.get().add(new AutoConcrete());
        Modules.get().add(new Speedmine());
        Modules.get().add(new AutoTrapPlus());
        Modules.get().add(new AutoIgnore());
        Modules.get().add(new ArmorNotify());
        Modules.get().add(new BetterEchestFarmer());
        Modules.get().add(new SurroundPlus());
        Modules.get().add(new Phase());
        Modules.get().add(new AutoPortal());
        Modules.get().add(new DiscordRPC());
        Modules.get().add(new TunnelMinerModule());
        Modules.get().add(new ElytraRoute());
        Modules.get().add(new SignRender());
        Modules.get().add(new AfkLogout());
        Modules.get().add(new FriendsSyncModule());
        addOptionalModule("xyz.thm.addon.modules.SpearTargetPlus");
        addOptionalModule("xyz.thm.addon.modules.BoatNoclipPlus");
        addOptionalModule("xyz.thm.addon.modules.MCMapSender");
        Modules.get().add(new FlightBypass());
        Modules.get().add(new KitbotFrontend());
        addOptionalModule("xyz.thm.addon.modules.WebmapModule");
        //addOptionalModule("xyz.thm.addon.modules.ElytraUAV"); // Still WIP and may be excluded from release jars.
        if (BaritoneUtils.IS_AVAILABLE) {
            LOG.info("Baritone detected. Enabling Baritone-dependent THM modules.");
            Modules.get().add(new THMHwyMonitor());
            //Modules.get().add(new ObsidianFarmerTHM()); //Not enabled in production
            Modules.get().add(new HighwayTools());
        } else {
            LOG.warn("Baritone not detected. Skipping Baritone-dependent modules (THM Highway Monitor, Highway Tools).");
        }


        //Commands
        Commands.add(new Center());
        Commands.add(new EclipCommand());
        Commands.add(new DesyncCommand());
        Commands.add(new UUIDCommand());


        //Hud
        Hud.get().register(OnlineFriendsList.INFO);
        Hud.get().register(DubCounter.INFO);
        Hud.get().register(HighwayHud.INFO);
        Hud.get().register(WelcomerHud.INFO);
        Hud.get().register(CrystalMetrics.INFO);
        Hud.get().register(MemberHud.INFO);
        Hud.get().register(KosHud.INFO);
        Hud.get().register(TunnelMinerHud.INFO);
        Hud.get().register(ElytraFlightHud.INFO);
        Hud.get().register(AfkLogoutHud.INFO);
        Hud.get().register(ItemCounterHud.INFO);

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
        String branch = FabricLoader
            .getInstance()
            .getModContainer("thm-addon")
            .get().getMetadata()
            .getCustomValue("github:branch")
            .getAsString();
        return new GithubRepo("Leonn170709", "THM-Addons", branch.isEmpty() ? "master" : branch.trim(), null);
    }

    @Override
    public String getCommit() {
        String commit = FabricLoader
            .getInstance()
            .getModContainer("thm-addon")
            .get().getMetadata()
            .getCustomValue("github:sha")
            .getAsString();
        LOG.info("Rejects version: {}", commit);
        return commit.isEmpty() ? null : commit.trim();
    }


    private static void addOptionalModule(String className) {
        try {
            Object instance = Class.forName(className).getDeclaredConstructor().newInstance();
            if (instance instanceof meteordevelopment.meteorclient.systems.modules.Module module) {
                Modules.get().add(module);
            }
        } catch (ClassNotFoundException ignored) {
        } catch (ReflectiveOperationException | LinkageError e) {
            LOG.warn("Failed to load optional module {}", className, e);
        }
    }
}
