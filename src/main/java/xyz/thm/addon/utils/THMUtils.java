/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.utils;

import meteordevelopment.meteorclient.mixininterface.IPlayerMoveC2SPacket;
import meteordevelopment.meteorclient.pathing.BaritoneUtils;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import xyz.thm.addon.THMAddon;
import xyz.thm.addon.modules.TunnelMinerModule;
import xyz.thm.addon.system.THMSystem;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import static meteordevelopment.meteorclient.MeteorClient.mc;
import static meteordevelopment.meteorclient.utils.player.ChatUtils.error;
import static meteordevelopment.meteorclient.utils.player.ChatUtils.sendMsg;
import static meteordevelopment.meteorclient.utils.world.BlockUtils.canPlace;


public class THMUtils {
    private THMUtils() {}

    // Container reads (size-agnostic)
    // The CONTAINER component holds whatever the container actually stores, so reading it back is
    // inherently dynamic - a vanilla shulker gives 27, a plugin-resized container gives whatever it
    // has. Prefer these over `new ItemStack[27]` + `Utils.getItemsInContainerItem(...)`, which caps the
    // read at 27 and silently drops anything past it. For a *live opened* container use inv.size().

    /** Non-empty contents of a container item (shulker, etc.), whatever its real slot count. Empty for a non-container. */
    public static List<ItemStack> getContainerContents(ItemStack containerItem) {
        if (containerItem == null || containerItem.isEmpty()) return List.of();
        ItemContainerContents container = containerItem.get(DataComponents.CONTAINER);
        return container == null ? List.of() : container.nonEmptyStream().toList();
    }

    /** Real slot count of a container item (capacity, including empty slots). 0 for a non-container. */
    public static int getContainerSlotCount(ItemStack containerItem) {
        if (containerItem == null || containerItem.isEmpty()) return 0;
        ItemContainerContents container = containerItem.get(DataComponents.CONTAINER);
        return container == null ? 0 : (int) container.stream().count();
    }
    private static TrayIcon trayIcon;
    private static boolean trayInitialized;
    private static Image notificationImage;
    private static String notificationIconPath;

    // Block Pos

    public static boolean canPlaceTHM(BlockPos blockPos) {
        return canPlace(blockPos, false);
    }

    public static boolean tunnelMinerGoTo(int x, int z, int stealthMode, boolean renderingEnabled) {
        if (mc == null || !mc.isSameThread()) {
            THMAddon.LOG.warn(
                "TunnelMiner goTo rejected: must be called on the Minecraft client thread. target=({}, {}) [stealthMode={},render={}]",
                x,
                z,
                stealthMode,
                renderingEnabled
            );
            return false;
        }

        TunnelMinerModule tunnelMiner = Modules.get().get(TunnelMinerModule.class);
        if (tunnelMiner == null) {
            THMAddon.LOG.warn("TunnelMiner goTo failed: TunnelMiner module not found.");
            return false;
        }

        boolean ok = tunnelMiner.goTo(x, z, stealthMode, renderingEnabled);
        if (!ok) {
            THMAddon.LOG.warn(
                "TunnelMiner goTo failed for target ({}, {}) [stealthMode={},render={}]",
                x,
                z,
                stealthMode,
                renderingEnabled
            );
        }
        return ok;
    }

    public static boolean tunnelMinerGoTo(int x, int z, boolean stealthEnabled, boolean renderingEnabled) {
        return tunnelMinerGoTo(
            x,
            z,
            stealthEnabled ? TunnelMinerModule.API_STEALTH_ON : TunnelMinerModule.API_STEALTH_OFF,
            renderingEnabled
        );
    }

    public static BlockPos forward(BlockPos pos, int distance) {
        return switch (mc.player.getDirection()) {
            case SOUTH -> pos.south(distance);
            case NORTH -> pos.north(distance);
            case WEST -> pos.west(distance);
            default -> pos.east(distance);
        };
    }

    public static BlockPos backward(BlockPos pos, int distance) {
        return switch (mc.player.getDirection()) {
            case SOUTH -> pos.north(distance);
            case NORTH -> pos.south(distance);
            case WEST -> pos.east(distance);
            default -> pos.west(distance);
        };
    }

    public static BlockPos left(BlockPos pos, int distance) {
        return switch (mc.player.getDirection()) {
            case SOUTH -> pos.east(distance);
            case NORTH -> pos.west(distance);
            case WEST -> pos.south(distance);
            default -> pos.north(distance);
        };
    }

    public static BlockPos right(BlockPos pos, int distance) {
        return switch (mc.player.getDirection()) {
            case SOUTH -> pos.west(distance);
            case NORTH -> pos.east(distance);
            case WEST -> pos.north(distance);
            default -> pos.south(distance);
        };
    }

    public static double getBlockCenterCoordinate(int blockCoordinate) {
        return blockCoordinate + 0.5;
    }

    /** Collapses two opposing movement flags into a single signed amount, e.g. for WASD-style input. */
    public static float movementAmount(boolean positive, boolean negative) {
        if (positive == negative) return 0.0f;
        return positive ? 1.0f : -1.0f;
    }

    // Highway Axes

    public static int getHighway() {
        double playerZ = mc.player.getZ();
        double playerX = mc.player.getX();
        boolean x = Math.abs(playerZ) < 5;
        boolean z = Math.abs(playerX) < 5;
        boolean xp = Math.signum(playerX) == 1.0;
        boolean zp = Math.signum(playerZ) == 1.0;
        boolean diag = Math.abs(Math.abs(playerX) - Math.abs(playerZ)) < 5;

        if (x && xp) return 1;
        if (x) return 2;
        if (z && zp) return 3;
        if (z) return 4;
        if (diag && xp && zp) return 5;
        if (diag && !xp && zp) return 6;
        if (diag && xp) return 7;
        if (diag) return 8;
        return -1;
    }

    public static String getHighwayDirectionString() {
        double playerZ = mc.player.getZ();
        double playerX = mc.player.getX();
        boolean x = Math.abs(playerZ) < 5;
        boolean z = Math.abs(playerX) < 5;
        boolean xp = Math.signum(playerX) == 1.0;
        boolean zp = Math.signum(playerZ) == 1.0;
        boolean diag = Math.abs(Math.abs(playerX) - Math.abs(playerZ)) < 5;

        boolean digging = false;
        THMSystem thmSystem = THMSystem.get();
        if (thmSystem != null && thmSystem.mode.get() == THMSystem.Mode.HighwayDigging) {
            digging = true;
        }

        String base;
        if (x && xp)            base = "E";
        else if (x)             base = "W";
        else if (z && zp)       base = "S";
        else if (z)             base = "N";
        else if (diag && xp && zp)  base = "SE";
        else if (diag && !xp && zp) base = "SW";
        else if (diag && xp)        base = "NE";
        else if (diag)              base = "NW";
        else return null;

        return digging ? "dug" + base : base;
    }

    //Notifies
    public static void Notify(String heading, String description) {
        String title = (heading == null || heading.isBlank()) ? "THM Addon" : heading;
        String body = description == null ? "" : description;

        try {
            initSystemTray();
            if (trayIcon != null) {
                trayIcon.displayMessage(title, body, TrayIcon.MessageType.NONE);
                return;
            }
        } catch (Throwable t) {
            THMAddon.LOG.warn("Desktop notification failed: {}", t.getMessage());
        }

        if (sendNativeNotification(title, body)) return;

        THMAddon.LOG.info("[Notify] {} - {}", title, body);
    }

    private static boolean sendNativeNotification(String title, String body) {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);

        if (os.contains("win")) return sendWindowsNotification(title, body);
        if (os.contains("linux")) return sendLinuxNotification(title, body);
        if (os.contains("mac")) return sendMacNotification(title, body);

        return false;
    }

    private static boolean sendLinuxNotification(String title, String body) {
        String iconPath = getNotificationIconPath();

        if (iconPath != null && runCommand("notify-send", "-i", iconPath, title, body)) return true;
        if (runCommand("notify-send", title, body)) return true;

        if (iconPath != null && runCommand("zenity", "--notification", "--window-icon=" + iconPath, "--text=" + title + " - " + body)) return true;
        if (runCommand("zenity", "--notification", "--text=" + title + " - " + body)) return true;

        if (iconPath != null && runCommand("kdialog", "--title", title, "--icon", iconPath, "--passivepopup", body, "5")) return true;
        return runCommand("kdialog", "--title", title, "--passivepopup", body, "5");
    }

    private static boolean sendWindowsNotification(String title, String body) {
        String script = buildWindowsToastScript(title, body);
        if (runCommand("powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", script)) return true;
        return runCommand("pwsh", "-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", script);
    }

    private static boolean sendMacNotification(String title, String body) {
        String escapedTitle = escapeAppleScript(title);
        String escapedBody = escapeAppleScript(body);
        return runCommand("osascript", "-e", "display notification \"" + escapedBody + "\" with title \"" + escapedTitle + "\"");
    }

    private static String buildWindowsToastScript(String title, String body) {
        String escapedTitle = escapePowerShell(title);
        String escapedBody = escapePowerShell(body);

        return "$title='" + escapedTitle + "';" +
            "$body='" + escapedBody + "';" +
            "[Windows.UI.Notifications.ToastNotificationManager, Windows.UI.Notifications, ContentType = WindowsRuntime] > $null;" +
            "$template=[Windows.UI.Notifications.ToastTemplateType]::ToastText02;" +
            "$xml=[Windows.UI.Notifications.ToastNotificationManager]::GetTemplateContent($template);" +
            "$xml.GetElementsByTagName('text').Item(0).AppendChild($xml.CreateTextNode($title)) > $null;" +
            "$xml.GetElementsByTagName('text').Item(1).AppendChild($xml.CreateTextNode($body)) > $null;" +
            "$toast=[Windows.UI.Notifications.ToastNotification]::new($xml);" +
            "[Windows.UI.Notifications.ToastNotificationManager]::CreateToastNotifier('THM Addon').Show($toast);";
    }

    private static String escapePowerShell(String s) {
        return s.replace("'", "''");
    }

    private static String escapeAppleScript(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static boolean runCommand(String... command) {
        try {
            Process process = new ProcessBuilder(command).start();
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void initSystemTray() {
        if (trayInitialized) return;
        trayInitialized = true;

        if (!SystemTray.isSupported()) return;

        try {
            Image image = getNotificationImage();
            if (image == null) return;

            trayIcon = new TrayIcon(image, "THM Addon");
            trayIcon.setImageAutoSize(true);
            SystemTray.getSystemTray().add(trayIcon);
        } catch (Throwable t) {
            trayIcon = null;
            THMAddon.LOG.warn("Unable to initialize system tray notifications: {}", t.getMessage());
        }
    }

    private static Image getNotificationImage() {
        if (notificationImage != null) return notificationImage;

        try (InputStream input = THMUtils.class.getClassLoader().getResourceAsStream("assets/icon/obby.png")) {
            if (input != null) {
                notificationImage = ImageIO.read(input);
                if (notificationImage != null) return notificationImage;
            }
        } catch (Throwable ignored) {
            // Falls back to generated placeholder icon below.
        }

        BufferedImage fallback = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = fallback.createGraphics();
        g.setColor(new java.awt.Color(41, 128, 185));
        g.fillRect(0, 0, 16, 16);
        g.setColor(java.awt.Color.WHITE);
        g.fillRect(4, 4, 8, 8);
        g.dispose();
        notificationImage = fallback;
        return notificationImage;
    }

    private static String getNotificationIconPath() {
        if (notificationIconPath != null) return notificationIconPath;

        try {
            URL resource = THMUtils.class.getClassLoader().getResource("assets/icon/icon.png");
            if (resource == null) return null;

            if ("file".equalsIgnoreCase(resource.getProtocol())) {
                notificationIconPath = Path.of(resource.toURI()).toAbsolutePath().toString();
                return notificationIconPath;
            }

            try (InputStream in = resource.openStream()) {
                Path tempIcon = Files.createTempFile("thm-notify-icon-", ".png");
                Files.copy(in, tempIcon, StandardCopyOption.REPLACE_EXISTING);
                tempIcon.toFile().deleteOnExit();
                notificationIconPath = tempIcon.toAbsolutePath().toString();
                return notificationIconPath;
            }
        } catch (Throwable t) {
            THMAddon.LOG.warn("Unable to resolve notification icon path: {}", t.getMessage());
            return null;
        }
    }

    //Server Check
    // Seed/fallback list - kept in sync with https://www.6b6t.org/api/anarchy-mod.json in case the
    // refresh below never completes (offline, blocked, endpoint down).
    private static volatile Set<String> anarchyModDomains = Set.of(
        "6b6t.org", "6b6t.cc", "6b6t.me", "7b7t.me", "8b8t.org", "8b8t.xyz",
        "10b10t.org", "alacity.net", "anarchypvp.pw", "l2x9.org", "simpleanarchy.org"
    );
    private static final AtomicBoolean anarchyModDomainsRefreshStarted = new AtomicBoolean(false);

    private static final ExecutorService ASYNC = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "thm-async");
        t.setDaemon(true);
        return t;
    });

    /** Shared daemon pool for every background job in the addon — never blocks the game thread. */
    public static void async(String name, Runnable task) {
        ASYNC.execute(() -> {
            Thread.currentThread().setName("thm-" + name);
            try {
                task.run();
            } catch (Throwable t) {
                THMAddon.LOG.warn("[THM] async task '{}' failed", name, t);
            }
        });
    }

    private static void refreshAnarchyModDomainsAsync() {
        if (!anarchyModDomainsRefreshStarted.compareAndSet(false, true)) return;

        async("anarchy-domains", () -> {
            try {
                // IMAGE, not API: third-party endpoint, so HTTPS-only and never our bearer token.
                String responseBody = TrustedHttp.getString("https://www.6b6t.org/api/anarchy-mod.json",
                    TrustedHttp.Kind.IMAGE, TrustedHttp.MAX_JSON_BYTES);
                JsonObject root = new Gson().fromJson(responseBody, JsonObject.class);
                JsonArray domainsJson = root == null ? null : root.getAsJsonArray("domains");
                if (domainsJson == null) return;

                Set<String> domains = new java.util.HashSet<>();
                for (var element : domainsJson) {
                    String domain = element.getAsString().trim().toLowerCase(Locale.ROOT);
                    if (domain.startsWith("*.")) domain = domain.substring(2);
                    if (!domain.isEmpty()) domains.add(domain);
                }
                if (!domains.isEmpty()) anarchyModDomains = Set.copyOf(domains);
            } catch (Exception e) {
                THMAddon.LOG.warn("[THM] Failed to refresh 6b6t anarchy-mod domain list, keeping cached list", e);
            }
        });
    }

    public static boolean isNot6B6T() {
        assert mc.level != null;
        if (FabricLoader.getInstance().isDevelopmentEnvironment()) return false; // Bypass check in dev environment
        if (mc.hasSingleplayerServer()) return true;
        refreshAnarchyModDomainsAsync();
        ServerData server = mc.getCurrentServer();
        if (server == null) return false;
        String address = server.ip == null ? "" : server.ip.trim().toLowerCase(Locale.ROOT);
        if (address.isEmpty()) return false;
        int colon = address.indexOf(':');
        String host = colon >= 0 ? address.substring(0, colon) : address;
        while (host.endsWith(".")) host = host.substring(0, host.length() - 1);
        String finalHost = host;
        return anarchyModDomains.stream().noneMatch(finalHost::endsWith);
    }
    public static boolean checkThreshold(ItemStack i, double threshold) {
        return getDamage(i) <= threshold;
    }

    public static double getDamage(ItemStack i) {
        return (((double) (i.getMaxDamage() - i.getDamageValue()) / i.getMaxDamage()) * 100);
    }
    public static Vec3 positionInDirection(Vec3 pos, double yaw, double distance) {
        Vec3 offset = yawToDirection(yaw).scale(distance);
        return pos.add(offset);
    }

    private void sendPacket(double height) {
        double x = mc.player.getX();
        double y = mc.player.getY();
        double z = mc.player.getZ();

        ServerboundMovePlayerPacket packet = new ServerboundMovePlayerPacket.Pos(x, y + height, z, false, false);
        ((IPlayerMoveC2SPacket) packet).meteor$setTag(1337);
        mc.player.connection.send(packet);
    }

    public static Vec3 yawToDirection(double yaw) {
        yaw = yaw * Math.PI / 180;
        double x = -Math.sin(yaw);
        double z = Math.cos(yaw);
        return new Vec3(x, 0, z);
    }

    public static double distancePointToDirection(Vec3 point, Vec3 direction, @Nullable Vec3 start) {
        if (start == null) start = Vec3.ZERO;
        point = point.multiply(new Vec3(1, 0, 1));
        start = start.multiply(new Vec3(1, 0, 1));
        direction = direction.multiply(new Vec3(1, 0, 1));
        Vec3 directionVec = point.subtract(start);
        double projectionLength = directionVec.dot(direction) / direction.lengthSqr();
        Vec3 projection = direction.scale(projectionLength);
        Vec3 perp = directionVec.subtract(projection);
        return perp.length();
    }

    public static double angleOnAxis(double yaw) {
        if (yaw < 0) yaw += 360;
        return Math.round(yaw / 45.0f) * 45;
    }
    public static long generateTimestamp() {
        // Get current time in milliseconds since epoch (UTC)
        return System.currentTimeMillis();
    }
    private boolean canceled = false;
    public boolean isCanceled() {
        return canceled;
    }
    public void cancel() {
        this.canceled = true;
    }
    public void setCanceled(boolean canceled) {
        this.canceled = canceled;
    }
    public static boolean isOnMainHighway() {
        // Get player's current X and Z coordinates
        if (mc.player == null) return false;
        double playerX = mc.player.getX();
        double playerZ = mc.player.getZ();

        // Check if player is on X or Z axis highway (within a 5 block tolerance)
        boolean onXAxis = Math.abs(playerZ) < 5;
        boolean onZAxis = Math.abs(playerX) < 5;

        // Check if player is on a diagonal highway (within a 5 block tolerance)
        boolean onDiagonal = Math.abs(Math.abs(playerX) - Math.abs(playerZ)) < 5;

        return onXAxis || onZAxis || onDiagonal;
    }

    public static boolean isOnOfficialHighway() {
        if (mc.player == null || mc.level == null) return false;
        if (isNot6B6T()) return false;
        if (mc.level.dimension() != Level.NETHER) return false;
        return isOnMainHighway();
    }
    public static String GetVerbatim(String text)
    {
        int idx = 0;
        var data = new char[text.length()];

        for ( int i = 0; i < text.length(); i++ )
            if ( text.charAt(i) != '§' &&  text.charAt(i) != '&')
                data[idx++] = text.charAt(i);
            else
                i++;

        return new String(data, 0, idx);
    }
    public static String GetVerbatimAll(String text)
    {
        int idx = 0;
        var data = new char[text.length()];

        for ( int i = 0; i < text.length(); i++ )
            if ( text.charAt(i) != '§' && text.charAt(i) != '&' )
                data[idx++] = text.charAt(i);
            else
                i++;

        return new String(data, 0, idx);
    }
    public static void startFly() {
        if (mc.player != null && mc.player.connection != null) {
            mc.player.connection.send(new ServerboundPlayerCommandPacket(mc.player, ServerboundPlayerCommandPacket.Action.START_FALL_FLYING));
        }
    }

    public static void fakeInventoryOpen(boolean open) {
        if (mc.player != null && mc.player.connection != null) {
            if (open)
                mc.player.connection.send(new ServerboundPlayerCommandPacket(mc.player, ServerboundPlayerCommandPacket.Action.OPEN_INVENTORY));
            else
                mc.player.connection.send(new ServerboundContainerClosePacket(0));
        }
    }
    public static boolean isBaritoneInstalled() {
        return BaritoneUtils.IS_AVAILABLE;
    }
    public static String getSaveName() {
        if (mc.player == null) return "Unknown";
        return mc.player.getName().getString();
    }

    /**
     * Discord-style webhook POST carrying text and an attachment in one multipart request.
     * Either part may be omitted (null/blank message = image only, null file = text only).
     * Fires on a daemon thread, same as APIUtils' own webhook sends.
     */
    public static void sendToWebhookWithFile(String url, String message, Path file) {
        async("webhook-attachment", () -> {
            try {
                String boundary = "thm" + System.nanoTime();
                byte[] sep = ("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8);
                ByteArrayOutputStream body = new ByteArrayOutputStream();

                String content = message == null ? "" : message;
                JsonObject payloadJson = new JsonObject();
                payloadJson.addProperty("content", content);
                String payload = payloadJson.toString();
                body.write(sep);
                body.write(("Content-Disposition: form-data; name=\"payload_json\"\r\nContent-Type: application/json\r\n\r\n"
                    + payload + "\r\n").getBytes(StandardCharsets.UTF_8));

                if (file != null) {
                    body.write(sep);
                    body.write(("Content-Disposition: form-data; name=\"files[0]\"; filename=\"" + file.getFileName()
                        + "\"\r\nContent-Type: image/png\r\n\r\n").getBytes(StandardCharsets.UTF_8));
                    body.write(Files.readAllBytes(file));
                    body.write("\r\n".getBytes(StandardCharsets.UTF_8));
                }
                body.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));

                TrustedHttp.postBytes(url, body.toByteArray(),
                    "multipart/form-data; boundary=" + boundary,
                    TrustedHttp.Kind.USER_WEBHOOK, TrustedHttp.MAX_UPLOAD_BYTES);
            } catch (Exception e) {
                THMAddon.LOG.warn("[THM] Failed to send webhook with attachment", e);
            }
        });
    }

    public static void sendClientMsg(String msg, Style style) {
        if (mc.player == null) return;
        try {
            String message = ChatFormatting.GRAY + msg;
            mc.player.displayClientMessage(Component.literal(message).setStyle(style), false);
        } catch (Exception ignored) {}
    }

    public static boolean checkOrCreateFile(Minecraft mc, String fileName) {
        File file = FabricLoader.getInstance().getGameDir().resolve(fileName).toFile();
        if (!file.exists()) {
            try {
                if (file.createNewFile()) {
                    if (mc.player != null) {
                        sendMsg(Component.nullToEmpty("Created " + file.getName() + " in your meteor-client folder."));
                        Style style = Style.EMPTY.withClickEvent(new ClickEvent.OpenFile(file.getAbsolutePath()));
                        sendClientMsg("Click \u00a72\u00a7lhere \u00a7r\u00a77to open the file.", style);
                    }
                    return true;
                }
            } catch (Exception err) {
                error("Error creating " + file.getAbsolutePath() + "! - Why:\n" + err, "THMUtils#checkOrCreateFile");
            }
        } else return true;
        return false;
    }
}
