/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.modules;
import java.io.*;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import com.google.gson.Gson;
import com.mojang.logging.LogUtils;
import oshi.util.tuples.Pair;
import java.lang.reflect.Type;
import com.google.common.reflect.TypeToken;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.events.world.TickEvent;
import xyz.thm.addon.THMAddon;
import xyz.thm.addon.utils.THMUtils;

public class Loadouts extends Module {
    public Loadouts() { super(THMAddon.PVP, "Loadouts", "Save and load inventory configurations."); }
    public static final String LOADOUTS_FILE = "meteor-client/loadouts.json";
    public final Setting<Boolean> quickLoadout = settings.getDefaultGroup().add(
        new BoolSetting.Builder()
            .name("quick-loadout-buttons")
            .description("Adds quicksave loadout buttons to the inventory screen.")
            .defaultValue(true)
            .build()
    );
    private final Setting<Boolean> chatNotify = settings.getDefaultGroup().add(
        new BoolSetting.Builder()
            .name("chat-notify")
            .description("Notify you in chat when your loadout is saved or loaded.")
            .defaultValue(false)
            .build()
    );
    private final Setting<Boolean> debug = settings.getDefaultGroup().add(
        new BoolSetting.Builder()
            .name("debug")
            .defaultValue(false)
            .visible(() -> false)
            .build()
    );
    private final Setting<Boolean> regear = settings.getDefaultGroup().add(
        new BoolSetting.Builder()
            .name("regear")
            .description("Refills loadout slots from any open container.")
            .defaultValue(false)
            .build()
    );
    private final Setting<Integer> actionsPerTick = settings.getDefaultGroup().add(
        new IntSetting.Builder()
            .name("actions-per-tick")
            .description("Max inventory clicks sent per tick.")
            .range(1, 64)
            .sliderRange(1, 64)
            .defaultValue(64)
            .build()
    );
    private final Setting<Integer> syncDelay = settings.getDefaultGroup().add(
        new IntSetting.Builder()
            .name("sync-delay")
            .description("Ticks to wait for the server after a batch of clicks.")
            .range(0, 20)
            .sliderRange(0, 10)
            .defaultValue(2)
            .build()
    );
    private static final int MAX_RETRIES = 2;
    private int cooldown = 0;
    private int regearedSyncId = -1;
    private int retries = 0;
    public boolean isSorted = true;
    private String activeLoadoutKey = "quicksave";
    private final ArrayDeque<Pair<Integer, Integer>> jobs = new ArrayDeque<>();
    private final HashMap<String, HashMap<Integer, Item>> loadouts = new HashMap<>();
    @Override
    public void onActivate() {
        loadLoadoutsFromFile();
    }
    @Override
    public void onDeactivate() {
        cooldown = 0;
        retries = 0;
        regearedSyncId = -1;
        jobs.clear();
        isSorted = true;
        saveLoadoutsToFile();
        activeLoadoutKey = "quicksave";
    }
    public void clearLoadouts() {
        loadouts.clear();
        saveLoadoutsToFile();
    }
    public void deleteLoadout(String name) {
        loadouts.remove(name);
        saveLoadoutsToFile();
    }
    public boolean noLoadout(String name) {
        return !loadouts.containsKey(name);
    }
    private void loadLoadoutsFromFile() {
        if (!THMUtils.checkOrCreateFile(mc, LOADOUTS_FILE)) {
            LogUtils.getLogger().error("Error checking loadouts file for loading..!", this.name);
        }
        Gson gson = new Gson();
        try (Reader reader = new FileReader(LOADOUTS_FILE)) {
            Type type = new TypeToken<HashMap<String, HashMap<Integer, String>>>() {}.getType();
            HashMap<String, HashMap<Integer, String>> loaded = gson.fromJson(reader, type);
            loadouts.clear();
            for (Map.Entry<String, HashMap<Integer, String>> entry : loaded.entrySet()) {
                HashMap<Integer, Item> itemMap = new HashMap<>();
                for (Map.Entry<Integer, String> itemId : entry.getValue().entrySet()) {
                    itemMap.put(itemId.getKey(), BuiltInRegistries.ITEM.getValue(Identifier.parse(itemId.getValue())));
                }
                loadouts.put(entry.getKey(), itemMap);
                LogUtils.getLogger().info("Successfully loaded loadouts from file..!", this.name);
            }
        } catch (Exception err) {
            LogUtils.getLogger().error("Error loading loadouts from file..! - Why: " + err, this.name);
        }
    }
    private void saveLoadoutsToFile() {
        if (!THMUtils.checkOrCreateFile(mc, LOADOUTS_FILE)) {
            LogUtils.getLogger().error("Error checking loadouts file for saving..!", this.name);
        }
        Gson gson = new Gson();
        try (Writer writer = new FileWriter(LOADOUTS_FILE)) {
            HashMap<String, HashMap<Integer, String>> itemNameMap = new HashMap<>();
            for (Map.Entry<String, HashMap<Integer, Item>> entry : loadouts.entrySet()) {
                HashMap<Integer, String> nameMap = new HashMap<>();
                for (Map.Entry<Integer, Item> itemEntry : entry.getValue().entrySet()) {
                    nameMap.put(itemEntry.getKey(), BuiltInRegistries.ITEM.getKey(itemEntry.getValue()).toString());
                }
                itemNameMap.put(entry.getKey(), nameMap);
            }
            gson.toJson(itemNameMap, writer);
            LogUtils.getLogger().info("Successfully saved loadouts to file..!", this.name);
        } catch (Exception err) {
            LogUtils.getLogger().error("Error saving loadouts to file..! - Why: " + err, this.name);
        }
    }
    private boolean isLoaded(String loadoutKey) {
        if (loadouts.isEmpty()) return true;
        if (mc.player == null) return true;
        if (!loadouts.containsKey(loadoutKey)) return true;
        if (!(mc.player.containerMenu instanceof InventoryMenu handler)) return true;
        HashMap<Integer, Item> loadout = loadouts.get(loadoutKey);
        for (int n = InventoryMenu.ARMOR_SLOT_START; n < handler.slots.size(); n++) {
            if (!loadout.containsKey(n)) continue;
            ItemStack stack = handler.getSlot(n).getItem();
            if (!stack.is(loadout.get(n))) return false;
        }
        return true;
    }
    public void saveLoadout(String name) {
        if (mc.player == null) return;
        if (!(mc.player.containerMenu instanceof InventoryMenu handler)) return;
        HashMap<Integer, Item> loadout = new HashMap<>();
        for (int n = InventoryMenu.ARMOR_SLOT_START; n < handler.slots.size(); n++) {
            ItemStack stack = handler.getSlot(n).getItem();
            if (!stack.isEmpty() && !stack.is(Items.AIR)) {
                loadout.put(n, stack.getItem());
            }
        }
        loadouts.put(name, loadout);
        saveLoadoutsToFile();
        if (chatNotify.get()) {
            info("§oLoadout \"§a§o" + name + "§7§o\" saved successfully§8§o.", this.name);
        }
    }
    public void loadLoadout(String name) {
        if (mc.player == null) return;
        if (!(mc.player.containerMenu instanceof InventoryMenu handler)) return;
        if (loadouts.isEmpty() || !loadouts.containsKey(name) || loadouts.get(name).isEmpty()) {
            info("§oNo loadout \"§3§o" + name + "§7§o\" saved§c§o..!", this.name);
            return;
        }
        jobs.clear();
        activeLoadoutKey = name;
        ArrayList<Integer> sorted = new ArrayList<>();
        HashMap<Integer, Item> loadout = loadouts.get(name);
        HashMap<Integer, ItemStack> changedSlots = new HashMap<>();
        for (int to = InventoryMenu.ARMOR_SLOT_START; to < handler.slots.size(); to++) {
            Item assigned = loadout.get(to);
            if (assigned == null) continue;
            ItemStack current = handler.getSlot(to).getItem();
            if (debug.get()) {
                LogUtils.getLogger().info("Assigned: {} | Current: {}", assigned.getName().getString(), current.getHoverName().getString(), this.name);
            }
            if (current.is(assigned)) {
                if (debug.get()) LogUtils.getLogger().info("Slot already sorted..!", this.name);
                sorted.add(to);
                continue;
            }
            for (int from = InventoryMenu.ARMOR_SLOT_START; from < handler.slots.size(); from++) {
                if (to == from || sorted.contains(from)) continue;
                ItemStack occupiedBy;
                if (changedSlots.containsKey(from)) {
                    occupiedBy = changedSlots.get(from);
                } else {
                    occupiedBy = handler.getSlot(from).getItem();
                }
                if (debug.get()) {
                    LogUtils.getLogger().info("Looking for: {} | found: {}", assigned.getName().getString(), occupiedBy.getHoverName().getString(), this.name);
                }
                if (occupiedBy.is(assigned)) {
                    if (loadout.get(from) != null && occupiedBy.is(loadout.get(from))) {
                        sorted.add(from);
                        continue;
                    }
                    if (!current.isEmpty()) {
                        sorted.add(to);
                        changedSlots.put(from, current);
                        jobs.addLast(new Pair<>(from, to));
                    } else {
                        sorted.add(to);
                        sorted.add(from);
                        changedSlots.remove(from);
                        jobs.addLast(new Pair<>(from, to));
                    }
                    if (debug.get()) {
                        LogUtils.getLogger().info("Moving stack: {} from slot {} to slot {}..!", occupiedBy.getHoverName().getString(), from, to, this.name);
                    }
                    break;
                }
            }
        }
    }
    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null) return;
        if (cooldown > 0) {
            cooldown--;
            return;
        }
        AbstractContainerMenu handler = mc.player.containerMenu;
        // hands off while the player is dragging something around - our clicks would fight theirs
        if (!handler.getCarried().isEmpty()) return;
        if (handler instanceof InventoryMenu) tickLoad();
        else if (regear.get()) tickRegear(handler);
    }
    private void tickLoad() {
        if (!jobs.isEmpty()) {
            isSorted = false;
            int budget = actionsPerTick.get();
            while (!jobs.isEmpty() && budget-- > 0) {
                Pair<Integer, Integer> entry = jobs.removeFirst();
                InvUtils.move().fromId(entry.getA()).toId(entry.getB());
            }
            // ponytail: whole batch goes out at once, then we sit out sync-delay ticks so the
            // server's slot corrections land before the verify pass below re-reads the handler.
            cooldown = syncDelay.get();
            return;
        }
        if (isSorted) return;
        if (retries < MAX_RETRIES && !isLoaded(activeLoadoutKey)) {
            retries++;
            loadLoadout(activeLoadoutKey);
            if (!jobs.isEmpty()) return;
        }
        isSorted = true;
        retries = 0;
        if (chatNotify.get()) {
            info("§oInventory sorted according to the loadout \"§a§o" + activeLoadoutKey + "\"§e§o..!", this.name);
        }
    }
    private void tickRegear(AbstractContainerMenu handler) {
        if (handler.containerId == regearedSyncId) return;
        HashMap<Integer, Item> loadout = loadouts.get(activeLoadoutKey);
        if (loadout == null || loadout.isEmpty()) return;
        int size = handler.slots.size() - 36;
        if (size <= 0) return;
        Set<Integer> taken = new HashSet<>();
        int budget = actionsPerTick.get();
        for (Map.Entry<Integer, Item> entry : loadout.entrySet()) {
            if (budget <= 0) break;
            int to = toContainerId(entry.getKey(), size);
            if (to < 0) continue;
            ItemStack current = handler.getSlot(to).getItem();
            // empty slot -> take it, right item but not a full stack -> top it up, anything else -> leave alone
            if (!current.isEmpty() && (!current.is(entry.getValue()) || current.getCount() >= current.getMaxStackSize())) continue;
            int from = -1;
            for (int i = 0; i < size; i++) {
                if (taken.contains(i)) continue;
                if (handler.getSlot(i).getItem().is(entry.getValue())) {
                    from = i;
                    break;
                }
            }
            if (from == -1) continue;
            taken.add(from);
            // move() clicks any cursor remainder back onto the source slot, so a partial merge is safe
            InvUtils.move().fromId(from).toId(to);
            budget--;
        }
        if (budget < actionsPerTick.get()) cooldown = syncDelay.get();
        // nothing left to pull: done with this container, re-arms when the next one is opened
        else regearedSyncId = handler.containerId;
    }
    /** PlayerScreenHandler slot -> the same inventory slot in an open container handler, -1 if it has none. */
    private static int toContainerId(int playerSlot, int containerSize) {
        if (playerSlot >= 9 && playerSlot <= 35) return containerSize + (playerSlot - 9);
        if (playerSlot >= 36 && playerSlot <= 44) return containerSize + 27 + (playerSlot - 36);
        return -1; // ponytail: armor and offhand aren't reachable while a container is open
    }
}
