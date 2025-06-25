package xyz.thm.addon;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.utils.Utils;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.metadata.ModMetadata;
import xyz.thm.addon.modules.*;
import com.mojang.logging.LogUtils;
import meteordevelopment.meteorclient.addons.GithubRepo;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import org.slf4j.Logger;

import java.io.File;

public class THMAddon extends MeteorAddon {
    public static final String MOD_ID = "thm-addon";
    public static ModMetadata MOD_META;
    public static final Logger LOG = LogUtils.getLogger();
    public static final Category CATEGORY = new Category("THMAddon");


    public static File GetConfigFile(String key, String filename) {
        return new File(new File(new File(new File(MeteorClient.FOLDER, "thm"), key), Utils.getFileWorldName()), filename);
    }

    @Override
    public void onInitialize() {
        LOG.info("Initializing THM Addon");

        MOD_META = FabricLoader.getInstance().getModContainer(MOD_ID).orElseThrow().getMetadata();

        // Modules
        Modules.get().add(new TPAAutomationModule());
        Modules.get().add(new ChatFilterModule());

    }

    @Override
    public void onRegisterCategories() {
        Modules.registerCategory(CATEGORY);
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
        return new GithubRepo("Leonn170709", "THM-Addons", "1.21.4", null);
    }

    @Override
    public String getCommit() {
        String commit = MOD_META.getCustomValue(MOD_ID + ":commit").getAsString();
        return commit.isEmpty() ? null : commit;
    }
}
