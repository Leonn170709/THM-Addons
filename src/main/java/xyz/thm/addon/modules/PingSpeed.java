package xyz.thm.addon.modules;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.c2s.common.CommonPongC2SPacket;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInputC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.PlayerInput;
import xyz.thm.addon.THMAddon;

/**
 * WIPWIPWIPWIPWIPWIPWIPWIWPIWPIWPIWPIWPIPWIPWIWPIPWIPWIWPIWPIWPIWPIWPIWIPWIWPWPIWPPW
 * PingSpeed — alternates between a fast burst phase (move + ping spam)
 * and a slow phase (ping only), replicating the 9.1 / 5.2 BPS pattern
 * observed in the captured packet log.
 *
 * How it works:
 *   - During the FAST phase the module sends PlayerMoveC2SPacket.PositionAndOnGround
 *     (and occasionally .Full) on every client tick while also firing
 *     QueryPingC2SPackets so the server sees dense position updates.
 *   - At the end of the fast phase a PlayerInputC2SPacket(forward=false) +
 *     ClientCommandC2SPacket(STOP_SPRINTING) is sent to cleanly stop.
 *   - During the SLOW phase only the ping spam continues, matching the
 *     idle pattern in the log.
 *   - A PlayerInputC2SPacket(forward=true) + ClientCommandC2SPacket(START_SPRINTING)
 *     kicks off the next fast phase.
 */
public class PingSpeed extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    // ── Settings ──────────────────────────────────────────────────────────────

    private final Setting<Double> fastBPS = sgGeneral.add(new DoubleSetting.Builder()
        .name("fast-bps")
        .description("Blocks-per-second during the burst phase (default: 9.1).")
        .defaultValue(9.1)
        .min(1.0).max(20.0)
        .sliderMin(1.0).sliderMax(20.0)
        .build()
    );

    private final Setting<Double> slowBPS = sgGeneral.add(new DoubleSetting.Builder()
        .name("slow-bps")
        .description("Blocks-per-second during the idle phase (default: 5.2).")
        .defaultValue(5.2)
        .min(0.0).max(20.0)
        .sliderMin(0.0).sliderMax(20.0)
        .build()
    );

    private final Setting<Integer> fastTicks = sgGeneral.add(new IntSetting.Builder()
        .name("fast-ticks")
        .description("Number of ticks to stay in the fast phase.")
        .defaultValue(40)
        .min(1).max(200)
        .sliderMin(5).sliderMax(100)
        .build()
    );

    private final Setting<Integer> slowTicks = sgGeneral.add(new IntSetting.Builder()
        .name("slow-ticks")
        .description("Number of ticks to stay in the slow (idle) phase.")
        .defaultValue(40)
        .min(1).max(200)
        .sliderMin(5).sliderMax(100)
        .build()
    );

    private final Setting<Integer> pingsPerTick = sgGeneral.add(new IntSetting.Builder()
        .name("pings-per-tick")
        .description("QueryPing packets to send each tick.")
        .defaultValue(1)
        .min(1).max(5)
        .sliderMin(1).sliderMax(5)
        .build()
    );

    private final Setting<Boolean> onlyWhenMoving = sgGeneral.add(new BoolSetting.Builder()
        .name("only-when-moving")
        .description("Only activate when the player is pressing a movement key.")
        .defaultValue(true)
        .build()
    );

    // ── State ─────────────────────────────────────────────────────────────────

    private enum Phase { FAST, SLOW }

    private Phase phase       = Phase.SLOW;
    private int   phaseTimer  = 0;
    private int   pingSeq     = 0;
    /** Full-packet counter — every 20th move packet is a .Full (matches log). */
    private int   moveCounter = 0;

    // ── Constructor ───────────────────────────────────────────────────────────

    public PingSpeed() {
        super(THMAddon.MAIN, "ping-speed",
            "Alternates fast (9.1 BPS) and slow (5.2 BPS) movement using ping spam.");
    }

    // ── Module lifecycle ──────────────────────────────────────────────────────

    @Override
    public void onActivate() {
        phase      = Phase.SLOW;
        phaseTimer = 0;
        pingSeq    = 0;
        moveCounter = 0;
    }

    // ── Main tick ─────────────────────────────────────────────────────────────

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;
        if (onlyWhenMoving.get() && !isPlayerMoving()) return;

        phaseTimer++;

        switch (phase) {
            case SLOW -> {
                sendPings();
                if (phaseTimer >= slowTicks.get()) {
                    transitionToFast();
                }
            }
            case FAST -> {
                sendPings();
                sendMovePackets();
                if (phaseTimer >= fastTicks.get()) {
                    transitionToSlow();
                }
            }
        }
    }

    // ── Phase transitions ─────────────────────────────────────────────────────

    private void transitionToFast() {
        phase      = Phase.FAST;
        phaseTimer = 0;

        // Replicate: PlayerInputC2SPacket(forward=true) + ClientCommandC2SPacket(START_SPRINTING)
        sendInput(true);
        mc.player.networkHandler.sendPacket(
            new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_SPRINTING)
        );
    }

    private void transitionToSlow() {
        phase      = Phase.SLOW;
        phaseTimer = 0;

        // Replicate: PlayerInputC2SPacket(forward=false) + ClientCommandC2SPacket(STOP_SPRINTING)
        sendInput(false);
        mc.player.networkHandler.sendPacket(
            new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.STOP_SPRINTING)
        );
    }

    // ── Packet helpers ────────────────────────────────────────────────────────

    /**
     * Send QueryPing packets.
     * In 1.21.x QueryPingC2SPacket is mapped to CommonPongC2SPacket with a rolling id.
     */
    private void sendPings() {
        for (int i = 0; i < pingsPerTick.get(); i++) {
            mc.player.networkHandler.sendPacket(new CommonPongC2SPacket(pingSeq++));
        }
    }

    /**
     * Send a position move packet with the fast-phase speed applied.
     * Every 20 packets sends a .Full (position + look), matching the log pattern.
     */
    private void sendMovePackets() {
        double speed = fastBPS.get() / 20.0; // BPS → blocks per tick

        float yaw    = mc.player.getYaw();
        float pitch  = mc.player.getPitch();
        double radYaw = Math.toRadians(yaw + 90);

        double dx = Math.cos(radYaw) * speed;
        double dz = Math.sin(radYaw) * speed;

        double x  = mc.player.getX() + dx;
        double y  = mc.player.getY();
        double z  = mc.player.getZ() + dz;
        boolean onGround = mc.player.isOnGround();
        boolean horizontalCollision = mc.player.horizontalCollision;

        moveCounter++;

        // Every 20th packet → Full (position + look), otherwise PositionAndOnGround
        if (moveCounter % 20 == 0) {
            mc.player.networkHandler.sendPacket(
                new PlayerMoveC2SPacket.Full(x, y, z, yaw, pitch, onGround, horizontalCollision)
            );
        } else {
            mc.player.networkHandler.sendPacket(
                new PlayerMoveC2SPacket.PositionAndOnGround(x, y, z, onGround, horizontalCollision)
            );
        }

        // Move the actual client-side position so the next packet is coherent
        mc.player.setPosition(x, y, z);
    }

    /**
     * Replicate PlayerInputC2SPacket with forward toggled.
     * In 1.21.x this is PlayerInputC2SPacket wrapping an Input record.
     */
    private void sendInput(boolean forward) {
        // PlayerInputC2SPacket constructor: (Input input)
        // Input fields: forward, backward, left, right, jump, sneak, sprint
        mc.player.networkHandler.sendPacket(
            new PlayerInputC2SPacket(
                new PlayerInput(
                    forward, // forward
                    false,   // backward
                    false,   // left
                    false,   // right
                    false,   // jump
                    false,   // sneak
                    false    // sprint — handled by ClientCommandC2SPacket separately
                )
            )
        );
    }

    /** Returns true when any movement key is held. */
    private boolean isPlayerMoving() {
        return mc.options.forwardKey.isPressed()
            || mc.options.backKey.isPressed()
            || mc.options.leftKey.isPressed()
            || mc.options.rightKey.isPressed();
    }
}
