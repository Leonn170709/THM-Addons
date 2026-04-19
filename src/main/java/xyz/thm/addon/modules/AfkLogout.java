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
    private static final long COORD_LOGOUT_DELAY_MS = 2_000L;

    // Growing buffer: up to 1 hour of ticks (20 tps * 3600s), min 40 samples (2s) before first estimate
    private static final int SPEED_BUFFER_MAX = 72_000;
    private static final int SPEED_BUFFER_MIN = 40;

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

    private final Setting<Boolean> disableHighwayBuilderBeforeCoordLogout = sgGeneral.add(new BoolSetting.Builder()
        .name("disable-highwaybuilder-before-logout")
        .description("When coordinate logout triggers, disable HighwayBuilder first and wait 2 seconds before logging out.")
        .defaultValue(false)
        .visible(enableCoordBased::get)
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
    private boolean pendingDelayedLogout;
    private String pendingDelayedLogoutReason;
    private long pendingDelayedLogoutAtMs;
    public long estimatedTimeRemainingMs = -1L;

    // Growing buffer speed tracking — more accurate the longer the module runs
    private final java.util.ArrayDeque<Double> bpsBuffer = new java.util.ArrayDeque<>();
    private double bpsBufferSum = 0.0;
    private double lastSampledDistance = -1.0;
    private long smoothedEtaMs = -1L;
    private static final double ETA_SMOOTH_ALPHA = 0.05; // lower = smoother, higher = more reactive

    public AfkLogout() {
        super(THMAddon.MAIN, "afk-logout", "Logs out when you reach certain conditions. Useful for afk travelling.");
    }

    @Override
    public void onActivate() {
        clearPendingDelayedLogout();
        moduleActivationTime = System.currentTimeMillis();
        resetSpeedTracking();
        estimatedTimeRemainingMs = -1L;
    }

    @Override
    public void onDeactivate() {
        clearPendingDelayedLogout();
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

        // Update estimated time remaining
        estimatedTimeRemainingMs = calculateEstimatedTimeRemainingMs();

        if (pendingDelayedLogout) {
            if (enableTimeBased.get() && isTimeoutReached()) {
                logout("Time-based logout triggered after " + timeoutMinutes.get() + " minute(s).");
                return;
            }

            if (enableElytraMonitor.get() && usableElytras <= elytraThreshold.get()) {
                logout("Elytra monitor triggered (usable elytras: " + usableElytras + ", threshold: " + elytraThreshold.get() + ").");
                return;
            }

            if (enablePlayerRange.get() && isPlayerInRange()) {
                String name = lastPlayerInRangeName == null ? "unknown" : lastPlayerInRangeName;
                logout(playerRangeRadius.get() == 0
                    ? "Player " + name + " entered render distance."
                    : "Player " + name + " entered within " + playerRangeRadius.get() + " blocks.");
                return;
            }

            if (System.currentTimeMillis() >= pendingDelayedLogoutAtMs) {
                logout(pendingDelayedLogoutReason);
            }
            return;
        }

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
            if (shouldDelayCoordinateLogoutForHighwayBuilder()) {
                startDelayedCoordinateLogout("Arrived at destination coordinates.");
            } else {
                logout("Arrived at destination coordinates.");
            }
        }
    }

    private int calculateDistanceToTarget() {
        double playerX = mc.player.getX();
        double playerZ = mc.player.getZ();
        double targetX = xCoords.get();
        double targetZ = zCoords.get();
        double deltaX = playerX - targetX;
        double deltaZ = playerZ - targetZ;
        return (int) (Math.sqrt(deltaX * deltaX + deltaZ * deltaZ) - radius.get());
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

    private boolean shouldDelayCoordinateLogoutForHighwayBuilder() {
        if (!disableHighwayBuilderBeforeCoordLogout.get()) return false;
        HighwayBuilderTHM highwayBuilder = Modules.get().get(HighwayBuilderTHM.class);
        return highwayBuilder != null && highwayBuilder.isActive();
    }

    private void startDelayedCoordinateLogout(String reason) {
        if (pendingDelayedLogout) return;
        HighwayBuilderTHM highwayBuilder = Modules.get().get(HighwayBuilderTHM.class);
        if (highwayBuilder != null && highwayBuilder.isActive()) highwayBuilder.disable();
        pendingDelayedLogout = true;
        pendingDelayedLogoutReason = reason;
        pendingDelayedLogoutAtMs = System.currentTimeMillis() + COORD_LOGOUT_DELAY_MS;
    }

    private void clearPendingDelayedLogout() {
        pendingDelayedLogout = false;
        pendingDelayedLogoutReason = null;
        pendingDelayedLogoutAtMs = 0L;
    }

    private void logout(String reason) {
        clearPendingDelayedLogout();
        if (toggleAutoReconnect.get() && Modules.get().isActive(AutoReconnect.class)) {
            Modules.get().get(AutoReconnect.class).toggle();
        }
        if (autoToggle.get()) toggle();
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
        return THMUtils.getDamage(stack) >= 10.0;
    }

    // ── Speed tracking (growing buffer, more accurate the longer it runs) ─────

    private void resetSpeedTracking() {
        bpsBuffer.clear();
        bpsBufferSum = 0.0;
        lastSampledDistance = -1.0;
        lastSampleTimeMs = -1L;
        smoothedEtaMs = -1L;
    }

    private long lastSampleTimeMs = -1L;

    private void updateMovementSpeedWindow() {
        if (!enableCoordBased.get() || PlayerUtils.getDimension() != dimension.get()) {
            resetSpeedTracking();
            return;
        }

        long now = System.currentTimeMillis();
        double remaining = calculateDistanceToTargetPrecise();

        if (lastSampledDistance < 0.0 || lastSampleTimeMs < 0L) {
            lastSampledDistance = remaining;
            lastSampleTimeMs = now;
            return;
        }

        long dtMs = now - lastSampleTimeMs;
        if (dtMs <= 0L) return;

        double delta = lastSampledDistance - remaining;
        double bps = (delta / dtMs) * 1000.0; // real blocks per real second

        if (bps > 0.0) {
            bpsBuffer.addLast(bps);
            bpsBufferSum += bps;
            if (bpsBuffer.size() > SPEED_BUFFER_MAX) {
                bpsBufferSum -= bpsBuffer.removeFirst();
            }
        }

        lastSampledDistance = remaining;
        lastSampleTimeMs = now;
    }

    private double getAverageApproachSpeedBps() {
        if (bpsBuffer.size() < SPEED_BUFFER_MIN) return -1.0;
        return bpsBufferSum / bpsBuffer.size();
    }

    // ── ETA calculations ──────────────────────────────────────────────────────

    private long estimateTimeToTargetMs() {
        if (!enableCoordBased.get()) return -1L;
        if (PlayerUtils.getDimension() != dimension.get()) return -1L;
        double remainingBlocks = calculateDistanceToTargetPrecise();
        if (remainingBlocks <= 0.0) return 0L;
        double speedBps = getAverageApproachSpeedBps();
        if (speedBps <= 0.0) return -1L;
        double seconds = remainingBlocks / speedBps;
        return (long) Math.ceil(seconds * 1000.0);
    }

    private long calculateTimeUntilLogoutMs() {
        long elapsedTime = System.currentTimeMillis() - moduleActivationTime;
        long timeoutMillis = (long) timeoutMinutes.get() * 60 * 1000;
        return Math.max(0L, timeoutMillis - elapsedTime);
    }

    private long calculateEstimatedTimeRemainingMs() {
        long rawEta;
        if (enableCoordBased.get() && PlayerUtils.getDimension() == dimension.get()) {
            rawEta = estimateTimeToTargetMs();
            if (rawEta < 0L) {
                // Buffer not ready yet — fall back to time-based if available
                if (enableTimeBased.get()) return calculateTimeUntilLogoutMs();
                return -1L;
            }
        } else if (enableTimeBased.get()) {
            return calculateTimeUntilLogoutMs(); // timer is already smooth, no EMA needed
        } else {
            smoothedEtaMs = -1L;
            return -1L;
        }

        // Apply EMA smoothing to coord-based ETA
        if (smoothedEtaMs < 0L) {
            smoothedEtaMs = rawEta; // seed with first real value
        } else {
            smoothedEtaMs = (long) (ETA_SMOOTH_ALPHA * rawEta + (1.0 - ETA_SMOOTH_ALPHA) * smoothedEtaMs);
        }
        return smoothedEtaMs;
    }

    private String formatRemainingTime(long remainingMs) {
        if (remainingMs < 0L) return "N/A";
        long totalSeconds = (long) Math.ceil(remainingMs / 1000.0);
        long days    = totalSeconds / 86400;
        long hours   = (totalSeconds % 86400) / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        if (totalSeconds < 10L * 60L) return minutes + "m " + seconds + "s";
        if (days > 0)  return days + "d " + hours + "h " + minutes + "m " + seconds + "s";
        if (hours > 0) return hours + "h " + minutes + "m " + seconds + "s";
        return minutes + "m " + seconds + "s";
    }

    // ── Player range check ────────────────────────────────────────────────────

    private boolean isPlayerInRange() {
        if (mc.world == null || mc.player == null) return false;
        int r = playerRangeRadius.get();
        if (r == 0) {
            for (PlayerEntity player : mc.world.getPlayers()) {
                if (player.getUuid().equals(mc.player.getUuid())) continue;
                lastPlayerInRangeName = player.getGameProfile().name();
                return true;
            }
            return false;
        }
        double radiusSq = (double) r * (double) r;
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

    public boolean isTimeBasedEnabled()              { return enableTimeBased.get(); }
    public boolean isCoordBasedEnabled()             { return enableCoordBased.get(); }
    public boolean isElytraMonitorEnabled()          { return enableElytraMonitor.get(); }
    public int getTimeoutMinutes()                   { return timeoutMinutes.get(); }
    public long getEstimatedTimeRemainingMs()        { return estimatedTimeRemainingMs; }
    public String getEstimatedTimeRemainingDisplay() { return formatRemainingTime(estimatedTimeRemainingMs); }
    public int getDistanceUntilLogout()              { return distanceUntilLogout; }
    public int getTargetX()                          { return xCoords.get(); }
    public int getTargetZ()                          { return zCoords.get(); }
    public int getRadius()                           { return radius.get(); }
    public Dimension getTargetDimension()            { return dimension.get(); }
    public int getElytrasUntilThreshold()            { return Math.max(0, usableElytras - elytraThreshold.get()); }

    @Override
    public String getInfoString() {
        if (estimatedTimeRemainingMs >= 0L) {
            return formatRemainingTime(estimatedTimeRemainingMs);
        } else if (enableElytraMonitor.get()) {
            return String.valueOf(usableElytras);
        } else if (enableCoordBased.get()) {
            return String.valueOf(distanceUntilLogout);
        } else {
            return String.valueOf(0);
        }
    }
}
