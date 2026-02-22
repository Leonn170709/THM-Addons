package xyz.thm.addon.modules;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.misc.AutoReconnect;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.world.Dimension;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.s2c.common.DisconnectS2CPacket;
import net.minecraft.text.Text;
import xyz.thm.addon.THMAddon;

public class AfkLogout extends Module {
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
        .visible(() -> enableTimeBased.get())
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
    public int timeUntilLogout = 0;
    public int distanceUntilLogout = 0;

    public AfkLogout() {
        super(THMAddon.MAIN, "afk-logout", "Logs out when you reach certain coords or after a timeout. Useful for afk travelling.");
    }

    @Override
    public void onActivate() {
        // Record the time when the module is activated
        moduleActivationTime = System.currentTimeMillis();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null || mc.getNetworkHandler() == null) return;

        // Update distance until logout if coordinate-based logout is enabled
        if (enableCoordBased.get() && PlayerUtils.getDimension() == dimension.get()) {
            distanceUntilLogout = calculateDistanceToTarget();
        }

        // Update time until logout if time-based logout is enabled
        if (enableTimeBased.get()) {
            timeUntilLogout = calculateTimeUntilLogout();
        }

        // Check time-based logout condition
        if (enableTimeBased.get() && isTimeoutReached()) {
            logout("Time-based logout triggered after " + timeoutMinutes.get() + " minute(s).");
            return;
        }

        // Check coordinate-based logout condition
        if (enableCoordBased.get() && xCoordsMatch() && zCoordsMatch() && PlayerUtils.getDimension() == dimension.get()) {
            logout("Arrived at destination coordinates.");
        }
    }

    private int calculateTimeUntilLogout() {
        long elapsedTime = System.currentTimeMillis() - moduleActivationTime;
        long timeoutMillis = (long) timeoutMinutes.get() * 60 * 1000;
        long remaining = timeoutMillis - elapsedTime;
        return (int) Math.max(0, remaining / 60000); // Convert to minutes
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
    @Override
    public String getInfoString() {
        if (enableTimeBased.get()) {
            return String.valueOf(timeUntilLogout);
        } else if (enableCoordBased.get()) {
            return String.valueOf(distanceUntilLogout);
        } else
        {return String.valueOf(0);}
    }
}
