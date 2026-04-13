package xyz.thm.addon.modules;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.misc.AutoReconnect;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.world.Dimension;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.common.DisconnectS2CPacket;
import net.minecraft.text.Text;
import xyz.thm.addon.THMAddon;
import xyz.thm.addon.utils.THMUtils;

public class AfkLogout extends Module {
    private static final long SPEED_WINDOW_MS = 5L * 60L * 1000L;
    private static final long MIN_SPEED_WINDOW_MS = 1000L;
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    // Time-based logout settings
    private final Setting<Boolean> enableTimeBased = sgGeneral.add(new BoolSetting.Builder()
        .name("enable-time-based")
        .description("Enable logout after a certain amount of time.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> timeoutMinutes = sgGeneral.add(new IntSetting.Builder()
        .name("timeout-minutes")
        .description("Minutes to wait before logging out.")
        .defaultValue(30)
        .min(1)
        .sliderRange(1, 120)
        .visible(enableTimeBased::get)
        .build()
    );

    // Coordinate-based logout settings
    private final Setting<Boolean> enableCoordBased = sgGeneral.add(new BoolSetting.Builder()
        .name("enable-coord-based")
        .description("Enable logout when reaching certain coordinates.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Dimension> dimension = sgGeneral.add(new EnumSetting.Builder<Dimension>()
        .name("dimension")
        .description("Dimension for the coordinates.")
        .defaultValue(Dimension.Overworld)
        .visible(enableCoordBased::get)
        .build()
    );

    private final Setting<Integer> xCoords = sgGeneral.add(new IntSetting.Builder()
        .name("x-coord")
        .description("The X coordinate at which to log out (world border is at +/- 29999983).")
        .defaultValue(1000)
        .range(-29999983, 29999983)
        .visible(enableCoordBased::get)
        .noSlider()
        .build()
    );

    private final Setting<Integer> zCoords = sgGeneral.add(new IntSetting.Builder()
        .name("z-coord")
        .description("The Z coordinate at which to log out (world border is at +/- 29999983).")
        .defaultValue(1000)
        .range(-29999983, 29999983)
        .visible(enableCoordBased::get)
        .noSlider()
        .build()
    );

    private final Setting<Integer> radius = sgGeneral.add(new IntSetting.Builder()
        .name("radius")
        .description("Log out when you are this far away from the coordinates.")
        .defaultValue(64)
        .min(0)
        .visible(enableCoordBased::get)
        .sliderRange(0, 100)
        .build()
    );

    // Elytra monitor logout settings
    private final Setting<Boolean> enableElytraMonitor = sgGeneral.add(new BoolSetting.Builder()
        .name("enable-elytra-monitor")
        .description("Enable logout when you reach the elytra threshold in your inventory.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> elytraThreshold = sgGeneral.add(new IntSetting.Builder()
        .name("elytra-threshold")
        .description("Log out when usable elytras in inventory are at or below this number.")
        .defaultValue(1)
        .min(0)
        .sliderRange(0, 30)
        .visible(enableElytraMonitor::get)
        .build()
    );

    // Player range logout settings
    private final Setting<Boolean> enablePlayerRange = sgGeneral.add(new BoolSetting.Builder()
        .name("enable-player-range")
        .description("Enable logout when another player is in visual range or within a set radius.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> playerRangeRadius = sgGeneral.add(new IntSetting.Builder()
        .name("player-range-radius")
        .description("Radius in blocks. 0 = any player in render distance.")
        .defaultValue(0)
        .min(0)
        .sliderRange(0, 256)
        .visible(enablePlayerRange::get)
        .build()
    );

    private final Setting<Boolean> toggleAutoReconnect = sgGeneral.add(new BoolSetting.Builder()
        .name("toggle-auto-reconnect")
        .description("Turns off AutoReconnect when logging out.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> autoToggle = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-toggle")
        .description("Turns itself off when logging out.")
        .defaultValue(true)
        .build()
    );
    // Internal tracking for time-based logout
    private long moduleActivationTime = 0;

    // Public variables for displaying time and distance until logout
    public int distanceUntilLogout = 0;
    public int usableElytras = 0;
    public String lastPlayerInRangeName = null;
    public long estimatedTimeRemainingMs = -1L;

    private static final class SpeedSample {
        private long dtMs;
        private double delta;

        private SpeedSample(long dtMs, double delta) {
            this.dtMs = dtMs;
            this.delta = delta;
        }
    }

    private final java.util.ArrayDeque<SpeedSample> speedSamples = new java.util.ArrayDeque<>();
    private double speedDeltaSum = 0.0;
    private long speedTimeSumMs = 0L;
    private long lastSpeedSampleMs = 0L;
    private double lastRemainingDistance = 0.0;

    public AfkLogout() {
        super(THMAddon.MAIN, "afk-logout", "Logs out when you reach certain conditions. Useful for afk travelling.");
    }

    @Override
    public void onActivate() {
        // Record the time when the module is activated
        moduleActivationTime = System.currentTimeMillis();
        resetSpeedTracking();
        estimatedTimeRemainingMs = -1L;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null || mc.getNetworkHandler() == null) return;

        updateMovementSpeedWindow();

        // Update distance until logout if coordinate-based logout is enabled
        if (enableCoordBased.get() && PlayerUtils.getDimension() == dimension.get()) {
            distanceUntilLogout = calculateDistanceToTarget();
        }

        // Update elytra count for HUD/monitoring
        usableElytras = countUsableElytras();

        // Update estimated time remaining (uses movement speed for coord-based)
        estimatedTimeRemainingMs = calculateEstimatedTimeRemainingMs();

        // Check time-based logout condition
        if (enableTimeBased.get() && isTimeoutReached()) {
            logout("Time-based logout triggered after " + timeoutMinutes.get() + " minute(s).");
            return;
        }

        // Check elytra monitor logout condition
        if (enableElytraMonitor.get() && usableElytras <= elytraThreshold.get()) {
            logout("Elytra monitor triggered (usable elytras: " + usableElytras + ", threshold: " + elytraThreshold.get() + ").");
            return;
        }

        // Check player range logout condition
        if (enablePlayerRange.get() && isPlayerInRange()) {
            String name = lastPlayerInRangeName == null ? "unknown" : lastPlayerInRangeName;
            logout(playerRangeRadius.get() == 0
                ? "Player " + name + " entered render distance."
                : "Player " + name + " entered within " + playerRangeRadius.get() + " blocks.");
            return;
        }

        // Check coordinate-based logout condition
        if (enableCoordBased.get() && xCoordsMatch() && zCoordsMatch() && PlayerUtils.getDimension() == dimension.get()) {
            logout("Arrived at destination coordinates.");
        }
    }

    private int calculateDistanceToTarget() {
        double playerX = mc.player.getX();
        double playerZ = mc.player.getZ();
        double targetX = xCoords.get();
        double targetZ = zCoords.get();

        // Calculate Euclidean distance
        double deltaX = playerX - targetX;
        double deltaZ = playerZ - targetZ;
        return (int) (Math.sqrt(deltaX * deltaX + deltaZ * deltaZ) - radius.get());
    }

    private boolean isTimeoutReached() {
        long elapsedTime = System.currentTimeMillis() - moduleActivationTime;
        long timeoutMillis = (long) timeoutMinutes.get() * 60 * 1000;
        return elapsedTime >= timeoutMillis;
    }

    private boolean xCoordsMatch() {
        return (mc.player.getX() <= xCoords.get() + radius.get() && mc.player.getX() >= xCoords.get() - radius.get());
    }

    private boolean zCoordsMatch() {
        return (mc.player.getZ() <= zCoords.get() + radius.get() && mc.player.getZ() >= zCoords.get() - radius.get());
    }

    private void logout(String reason) {
        // Toggle AutoReconnect if enabled
        if (toggleAutoReconnect.get() && Modules.get().isActive(AutoReconnect.class)) {
            Modules.get().get(AutoReconnect.class).toggle();
        }

        // Disable this module if auto-toggle is enabled
        if (autoToggle.get()) toggle();

        // Disconnect with the provided reason
        mc.player.networkHandler.onDisconnect(new DisconnectS2CPacket(Text.literal("[AfkLogout] " + reason)));
    }

    private int countUsableElytras() {
        if (mc.player == null) return 0;
        int count = 0;
        for (int i = 0; i < mc.player.getInventory().size(); i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (isUsableElytra(stack)) count++;
        }
        return count;
    }

    private boolean isUsableElytra(ItemStack stack) {
        if (stack == null || stack.isEmpty() || stack.getItem() != Items.ELYTRA) return false;
        // Ignore broken elytras or those under 10% durability.
        return THMUtils.getDamage(stack) >= 10.0;
    }

    private void resetSpeedTracking() {
        speedSamples.clear();
        speedDeltaSum = 0.0;
        speedTimeSumMs = 0L;
        lastSpeedSampleMs = 0L;
        lastRemainingDistance = 0.0;
    }

    private void updateMovementSpeedWindow() {
        if (!enableCoordBased.get() || PlayerUtils.getDimension() != dimension.get()) {
            resetSpeedTracking();
            return;
        }

        long now = System.currentTimeMillis();
        if (lastSpeedSampleMs == 0L) {
            lastSpeedSampleMs = now;
            lastRemainingDistance = calculateDistanceToTargetPrecise();
            return;
        }

        long dtMs = now - lastSpeedSampleMs;
        if (dtMs <= 0L) return;

        double remaining = calculateDistanceToTargetPrecise();
        double delta = lastRemainingDistance - remaining; // positive when moving toward target

        speedSamples.addLast(new SpeedSample(dtMs, delta));
        speedTimeSumMs += dtMs;
        speedDeltaSum += delta;

        trimSpeedWindow();

        lastSpeedSampleMs = now;
        lastRemainingDistance = remaining;
    }

    private void trimSpeedWindow() {
        long excess = speedTimeSumMs - SPEED_WINDOW_MS;
        while (excess > 0L && !speedSamples.isEmpty()) {
            SpeedSample sample = speedSamples.peekFirst();
            if (sample.dtMs <= excess) {
                speedTimeSumMs -= sample.dtMs;
                speedDeltaSum -= sample.delta;
                excess -= sample.dtMs;
                speedSamples.removeFirst();
            } else {
                double ratio = (double) (sample.dtMs - excess) / (double) sample.dtMs;
                speedDeltaSum -= sample.delta * (1.0 - ratio);
                sample.delta *= ratio;
                sample.dtMs -= excess;
                speedTimeSumMs -= excess;
                excess = 0L;
            }
        }
    }

    private double getAverageApproachSpeedBps() {
        if (speedTimeSumMs < MIN_SPEED_WINDOW_MS) return -1.0;
        double seconds = speedTimeSumMs / 1000.0;
        if (seconds <= 0.0) return -1.0;
        return speedDeltaSum / seconds;
    }

    private long calculateTimeUntilLogoutMs() {
        long elapsedTime = System.currentTimeMillis() - moduleActivationTime;
        long timeoutMillis = (long) timeoutMinutes.get() * 60 * 1000;
        long remaining = timeoutMillis - elapsedTime;
        return Math.max(0L, remaining);
    }

    private long estimateTimeToTargetMs() {
        if (!enableCoordBased.get()) return -1L;
        if (PlayerUtils.getDimension() != dimension.get()) return -1L;
        double remainingBlocks = Math.max(0.0, calculateDistanceToTargetPrecise());
        if (remainingBlocks <= 0.0) return 0L;
        double speedBps = getAverageApproachSpeedBps();
        if (speedBps <= 0.0) return -1L;
        double seconds = remainingBlocks / speedBps;
        return (long) Math.ceil(seconds * 1000.0);
    }

    private long calculateEstimatedTimeRemainingMs() {
        if (enableCoordBased.get() && PlayerUtils.getDimension() == dimension.get()) {
            return estimateTimeToTargetMs();
        }
        if (enableTimeBased.get()) return calculateTimeUntilLogoutMs();
        return -1L;
    }

    private double calculateDistanceToTargetPrecise() {
        double playerX = mc.player.getX();
        double playerZ = mc.player.getZ();
        double targetX = xCoords.get();
        double targetZ = zCoords.get();
        double deltaX = playerX - targetX;
        double deltaZ = playerZ - targetZ;
        return Math.max(0.0, Math.sqrt(deltaX * deltaX + deltaZ * deltaZ) - radius.get());
    }

    private String formatRemainingTime(long remainingMs) {
        if (remainingMs < 0L) return "N/A";
        long totalSeconds = (long) Math.ceil(remainingMs / 1000.0);
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        if (totalSeconds < 10L * 60L) return minutes + "m " + seconds + "s";
        if (hours > 0) return hours + "h " + minutes + "m " + seconds + "s";
        return minutes + "m " + seconds + "s";
    }

    private boolean isPlayerInRange() {
        if (mc.world == null || mc.player == null) return false;
        int radius = playerRangeRadius.get();
        if (radius == 0) {
            for (PlayerEntity player : mc.world.getPlayers()) {
                if (player.getUuid().equals(mc.player.getUuid())) continue;
                lastPlayerInRangeName = player.getGameProfile().name();
                return true;
            }
            return false;
        }

        double radiusSq = (double) radius * (double) radius;
        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player.getUuid().equals(mc.player.getUuid())) continue;
            if (mc.player.squaredDistanceTo(player) <= radiusSq) {
                lastPlayerInRangeName = player.getGameProfile().name();
                return true;
            }
        }
        return false;
    }

    // ── HUD getters ───────────────────────────────────────────────────────────
    public boolean isTimeBasedEnabled() {
        return enableTimeBased.get();
    }

    public boolean isCoordBasedEnabled() {
        return enableCoordBased.get();
    }

    public boolean isElytraMonitorEnabled() {
        return enableElytraMonitor.get();
    }

    public int getTimeoutMinutes() {
        return timeoutMinutes.get();
    }

    public long getEstimatedTimeRemainingMs() { return estimatedTimeRemainingMs; }

    public String getEstimatedTimeRemainingDisplay() { return formatRemainingTime(estimatedTimeRemainingMs); }

    public int getDistanceUntilLogout() {
        return distanceUntilLogout;
    }

    public int getTargetX() {
        return xCoords.get();
    }

    public int getTargetZ() {
        return zCoords.get();
    }

    public int getRadius() {
        return radius.get();
    }

    public Dimension getTargetDimension() {
        return dimension.get();
    }

    public int getElytrasUntilThreshold() {
        return Math.max(0, usableElytras - elytraThreshold.get());
    }

    @Override
    public String getInfoString() {
        if (estimatedTimeRemainingMs >= 0L) {
            return formatRemainingTime(estimatedTimeRemainingMs);
        } else if (enableElytraMonitor.get()) {
            return String.valueOf(usableElytras);
        } else if (enableCoordBased.get()) {
            return String.valueOf(distanceUntilLogout);
        } else
        {return String.valueOf(0);}
    }
}
