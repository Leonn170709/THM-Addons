package xyz.thm.addon.utils;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Difficulty;
import org.jetbrains.annotations.Nullable;
import xyz.thm.addon.THMAddon;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import javax.imageio.ImageIO;

import static meteordevelopment.meteorclient.MeteorClient.mc;
import static meteordevelopment.meteorclient.utils.world.BlockUtils.canPlace;


public class THMUtils {
    private THMUtils() {}
    private static TrayIcon trayIcon;
    private static boolean trayInitialized;
    private static Image notificationImage;
    private static String notificationIconPath;

    // Block Pos

    public static boolean canPlaceTHM(BlockPos blockPos) {
        return canPlace(blockPos, false);
    }

    public static BlockPos forward(BlockPos pos, int distance) {
        return switch (mc.player.getHorizontalFacing()) {
            case SOUTH -> pos.south(distance);
            case NORTH -> pos.north(distance);
            case WEST -> pos.west(distance);
            default -> pos.east(distance);
        };
    }

    public static BlockPos backward(BlockPos pos, int distance) {
        return switch (mc.player.getHorizontalFacing()) {
            case SOUTH -> pos.north(distance);
            case NORTH -> pos.south(distance);
            case WEST -> pos.east(distance);
            default -> pos.west(distance);
        };
    }

    public static BlockPos left(BlockPos pos, int distance) {
        return switch (mc.player.getHorizontalFacing()) {
            case SOUTH -> pos.east(distance);
            case NORTH -> pos.west(distance);
            case WEST -> pos.south(distance);
            default -> pos.north(distance);
        };
    }

    public static BlockPos right(BlockPos pos, int distance) {
        return switch (mc.player.getHorizontalFacing()) {
            case SOUTH -> pos.west(distance);
            case NORTH -> pos.east(distance);
            case WEST -> pos.north(distance);
            default -> pos.south(distance);
        };
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

        try (InputStream input = THMUtils.class.getClassLoader().getResourceAsStream("assets/icon/icon.png")) {
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

    public static boolean isNot6B6T() {
        assert mc.world != null;
        if (FabricLoader.getInstance().isDevelopmentEnvironment()) return false; // Bypass check in dev environment
        if (mc.isIntegratedServerRunning()) return true;
        ServerInfo server = mc.getCurrentServerEntry();
        if (server == null) return false;
        String address = server.address == null ? "" : server.address.trim().toLowerCase(Locale.ROOT);
        if (address.isEmpty()) return false;
        int colon = address.indexOf(':');
        String host = colon >= 0 ? address.substring(0, colon) : address;
        while (host.endsWith(".")) host = host.substring(0, host.length() - 1);
        return !host.endsWith("6b6t.org");
    }
    public static void pickupAndReturn() {
        if (mc.player == null) return;
        int savedX;
        int savedZ;
        final boolean[] finishedbar = {false};
        final IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
        savedX = (int) mc.player.getX()-1;
        savedZ = (int) mc.player.getZ()-1;

        baritone.getCommandManager().execute("pickup minecraft:obsidian");
        new Thread(() -> {
            try {
                THMAddon.LOG.info("Waiting 10 seconds for baritone to pick up");
                Thread.sleep(10000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            baritone.getPathingBehavior().cancelEverything();
            baritone.getCommandManager().execute("goto " + savedX + " " + savedZ);
            while (!finishedbar[0]) {
                if (Math.abs(mc.player.getX() - savedX) == 0 && Math.abs(mc.player.getZ() - savedZ) == 0) {
                    finishedbar[0] = true;
                    baritone.getPathingBehavior().cancelEverything();
                }
            }
        }).start();

    }
    // TODO: Add this to Highway builder so it picks up all the splattered obsidian
    private boolean checkModLoaded(String... modIds)
    {
        boolean loaded = false;
        for (String id : modIds)
        {
            if (FabricLoader.getInstance().isModLoaded(id))
            {
                loaded = true;
                break;
            }
        }
        if (!loaded)
        {
            THMAddon.LOG.error("{} not found, disabling modules that require it.", modIds[0]);
        }
        return loaded;
    }
    public static boolean checkThreshold(ItemStack i, double threshold) {
        return getDamage(i) <= threshold;
    }

    public static double getDamage(ItemStack i) {
        return (((double) (i.getMaxDamage() - i.getDamage()) / i.getMaxDamage()) * 100);
    }
    public static Vec3d positionInDirection(Vec3d pos, double yaw, double distance) {
        Vec3d offset = yawToDirection(yaw).multiply(distance);
        return pos.add(offset);
    }

    public static Vec3d yawToDirection(double yaw) {
        yaw = yaw * Math.PI / 180;
        double x = -Math.sin(yaw);
        double z = Math.cos(yaw);
        return new Vec3d(x, 0, z);
    }

    public static double distancePointToDirection(Vec3d point, Vec3d direction, @Nullable Vec3d start) {
        if (start == null) start = Vec3d.ZERO;
        point = point.multiply(new Vec3d(1, 0, 1));
        start = start.multiply(new Vec3d(1, 0, 1));
        direction = direction.multiply(new Vec3d(1, 0, 1));
        Vec3d directionVec = point.subtract(start);
        double projectionLength = directionVec.dotProduct(direction) / direction.lengthSquared();
        Vec3d projection = direction.multiply(projectionLength);
        Vec3d perp = directionVec.subtract(projection);
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
}
