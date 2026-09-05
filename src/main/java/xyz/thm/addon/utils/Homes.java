/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.utils;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.gui.GuiThemes;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.CommandExecutionC2SPacket;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import xyz.thm.addon.THMAddon;
import xyz.thm.addon.gui.HomesMeteorScreen;
import xyz.thm.addon.gui.HomesScreen;
import xyz.thm.addon.system.THMSystem;

import static meteordevelopment.meteorclient.MeteorClient.mc;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns the chat output of the server's own {@code /homes} command into a clickable screen. Nothing about
 * the home list is persisted — running {@code /homes} again is the refresh — only the per-home icons are.
 *
 * <p>Not a module: the settings live in the THM tab ({@link THMSystem}), so this is a plain singleton
 * subscribed to the event bus once from {@code THMAddon#onInitialize}.
 */
public class Homes {
    public enum GuiStyle {
        Minecraft,
        Meteor
    }

    private static Homes INSTANCE;

    public static Homes get() {
        if (INSTANCE == null) INSTANCE = new Homes();
        return INSTANCE;
    }

    /** Subscribes the singleton to the event bus; called once at addon init. */
    public static void initialize() {
        MeteorClient.EVENT_BUS.subscribe(get());
    }

    /** How long after sending /homes chat lines are treated as its answer. */
    private static final long CAPTURE_MS = 3000;
    /** Derived from the home name, not rolled per call - icon() runs every frame. */
    private static List<Item> defaultIcons;
    /** The server's answer, verbatim: {@code Your homes (18/34): stash_RED, backupstash, ...} */
    private static final Pattern HOMES_LINE = Pattern.compile("homes\\s*\\(\\d+\\s*/\\s*\\d+\\)\\s*:\\s*(.+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern HOME_NAME = Pattern.compile("[A-Za-z0-9_\\-]{1,32}");

    private final List<String> homes = new ArrayList<>();
    private final Map<String, String> icons = new HashMap<>();
    private long captureUntil;
    private boolean iconsLoaded;

    private Homes() {}

    private static boolean enabled() {
        THMSystem system = THMSystem.get();
        return system != null && system.homesGui.get();
    }

    // Capture

    @EventHandler
    private void onPacketSend(PacketEvent.Send event) {
        if (!enabled()) return;
        if (!(event.packet instanceof CommandExecutionC2SPacket packet)) return;
        String command = packet.command().toLowerCase();
        if (!command.equals("homes") && !command.startsWith("homes ")) return;
        homes.clear();
        captureUntil = System.currentTimeMillis() + CAPTURE_MS;
    }

    @EventHandler
    private void onReceiveMessage(ReceiveMessageEvent event) {
        if (!enabled() || System.currentTimeMillis() > captureUntil) return;

        List<String> found = parse(event.getMessage());
        if (found.isEmpty()) return;

        // the whole list arrives on one line - stop looking, or the next chat message gets eaten too
        captureUntil = 0;
        homes.clear();
        homes.addAll(found);
        loadIcons();
        if (THMSystem.get().homesAutoOpen.get()) open();
    }

    /**
     * Home names out of one chat line, empty when the line isn't the homes list. Anchored on the server's
     * own {@code homes (x/y):} header rather than "text after the first colon" — that earlier version
     * turned a chat prefix into a colon of its own and read "Your" and "homes" as home names.
     */
    private static List<String> parse(Text message) {
        List<String> found = new ArrayList<>();

        String raw = Formatting.strip(message.getString());
        if (raw == null) return found;

        Matcher matcher = HOMES_LINE.matcher(raw);
        if (!matcher.find()) return found;

        for (String token : matcher.group(1).split(",")) {
            // first name-shaped run of each entry, not the whole token: the last home carries no comma and
            // whatever the server appends after it (a period, a hint) would otherwise reject it outright
            Matcher name = HOME_NAME.matcher(token);
            if (name.find()) found.add(name.group());
        }
        return found;
    }

    // Screen

    public void open() {
        if (homes.isEmpty()) return;
        if (THMSystem.get().homesGuiStyle.get() == GuiStyle.Meteor) mc.setScreen(new HomesMeteorScreen(GuiThemes.get(), this));
        else mc.setScreen(new HomesScreen(this));
    }

    public List<String> homes() {
        return homes;
    }

    public ItemStack icon(String home) {
        String id = icons.get(home);
        Item item = id == null ? null : Registries.ITEM.get(Identifier.of(id));
        return new ItemStack(item == null || item == Items.AIR ? defaultIcon(home) : item);
    }

    private static Item defaultIcon(String home) {
        if (defaultIcons == null) {
            defaultIcons = Registries.BLOCK.stream().map(Block::asItem).filter(i -> i != Items.AIR).toList();
        }
        return defaultIcons.get(Math.floorMod(home.hashCode(), defaultIcons.size()));
    }

    /** Null resets the home to the default icon. */
    public void setIcon(String home, Item item) {
        if (item == null || item == Items.AIR) icons.remove(home);
        else icons.put(home, Registries.ITEM.getId(item).toString());
        saveIcons();
    }

    public void teleport(String home) {
        sendCommand("home " + home);
        if (mc.currentScreen != null) mc.currentScreen.close();
    }

    public void delete(String home) {
        sendCommand("delhome " + home);
        homes.remove(home);
    }

    private void sendCommand(String command) {
        // ponytail: /home and /delhome are the Essentials-style names /homes itself implies. Two string
        // settings if a server ever uses different ones.
        if (mc.getNetworkHandler() != null) mc.getNetworkHandler().sendChatCommand(command);
    }

    // Icon storage

    private File iconFile() {
        return THMAddon.GetConfigFile("homes", "icons.json");
    }

    private void loadIcons() {
        if (iconsLoaded) return;
        iconsLoaded = true;

        File file = iconFile();
        if (!file.exists()) return;

        Type type = new TypeToken<HashMap<String, String>>() {}.getType();
        try (Reader reader = new FileReader(file)) {
            Map<String, String> loaded = new Gson().fromJson(reader, type);
            if (loaded != null) icons.putAll(loaded);
        } catch (Exception err) {
            THMAddon.LOG.error("Failed to read home icons: {}", err.toString());
        }
    }

    private void saveIcons() {
        File file = iconFile();
        file.getParentFile().mkdirs();
        try (Writer writer = new FileWriter(file)) {
            new Gson().toJson(icons, writer);
        } catch (Exception err) {
            THMAddon.LOG.error("Failed to write home icons: {}", err.toString());
        }
    }
}
