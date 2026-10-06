/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.shaders;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import xyz.thm.addon.THMAddon;
import xyz.thm.addon.system.THMSystem;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

// Discovers the .fsh backgrounds shipped under assets/thm-addon/shaders and picks which
// one is currently active, per THMSystem's main-menu shader settings.
public class ShaderManager {
    private static final String BASE_PATH = "shaders";
    private static final Random RANDOM = new Random();

    private static List<String> cached;
    private static String activeRoll;

    public static List<String> availableShaders() {
        if (cached == null) {
            List<String> names = new ArrayList<>();
            try {
                Minecraft.getInstance().getResourceManager()
                    .listResources(BASE_PATH, id -> id.getNamespace().equals(THMAddon.MOD_ID) && id.getPath().endsWith(".fsh"))
                    .keySet()
                    .forEach(id -> names.add(id.getPath().substring(BASE_PATH.length() + 1, id.getPath().length() - ".fsh".length())));
            } catch (Exception e) {
                THMAddon.LOG.warn("[THM] Failed to list main-menu shaders", e);
            }
            names.sort(String::compareTo);
            cached = names;
            THMAddon.LOG.info("[THM/Menu] Shader discovery completed; available={}", names.size());
        }
        return cached;
    }

    public static Identifier resourceId(String shaderName) {
        return Identifier.fromNamespaceAndPath(THMAddon.MOD_ID, BASE_PATH + "/" + shaderName + ".fsh");
    }

    /** Re-rolls which shader is active. Called once per TitleScreen#init - i.e. each time the
     *  main menu is (re)opened, not on any kind of timer. */
    public static void reroll() {
        THMSystem system = THMSystem.get();
        List<String> all = availableShaders();

        if (all.isEmpty()) {
            activeRoll = null;
            THMAddon.LOG.warn("[THM/Menu] Shader selection skipped: no background resources found");
            return;
        }

        if (!system.shaderRandom.get()) {
            String choice = system.shaderChoice.get();
            activeRoll = all.contains(choice) ? choice : null;
            THMAddon.LOG.info("[THM/Menu] Shader selection completed; random=false, configured={}, active={}", choice, activeRoll);
            return;
        }

        // Empty selection = no filter configured yet, allow the whole pool.
        java.util.Set<String> allowed = system.shaderPool.get().selected();
        List<String> pool = allowed.isEmpty() ? all : all.stream().filter(allowed::contains).toList();
        if (pool.isEmpty()) pool = all;

        // Avoid rolling the same shader twice in a row when there's more than one to pick from.
        String previous = activeRoll;
        String next = pool.get(RANDOM.nextInt(pool.size()));
        if (next.equals(previous) && pool.size() > 1) {
            List<String> withoutPrevious = pool.stream().filter(s -> !s.equals(previous)).toList();
            next = withoutPrevious.get(RANDOM.nextInt(withoutPrevious.size()));
        }
        activeRoll = next;
        THMAddon.LOG.info("[THM/Menu] Shader selection completed; random=true, candidates={}, active={}", pool.size(), activeRoll);
    }

    /** @return the currently active shader name, or null for the vanilla panorama. */
    public static String active() {
        return activeRoll;
    }
}
