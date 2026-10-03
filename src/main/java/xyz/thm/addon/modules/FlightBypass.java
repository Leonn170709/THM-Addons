/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.modules;

import meteordevelopment.meteorclient.events.entity.player.PlayerMoveEvent;
import meteordevelopment.meteorclient.events.entity.player.SendMovementPacketsEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import xyz.thm.addon.THMAddon;

import java.util.HashSet;

public class FlightBypass extends Module {
    private final HashSet<ServerboundMovePlayerPacket> packets = new HashSet<>();
    private final SettingGroup sgMovement = settings.createGroup("Movement");
    private final SettingGroup sgClient = settings.createGroup("Client");
    private final SettingGroup sgBypass = settings.createGroup("Bypass");

    private final Setting<Double> horizontalSpeed = sgMovement.add(new DoubleSetting.Builder()
        .name("horizontal-speed")
        .description("Horizontal speed in blocks per second.(No Rotate is recommended)")
        .defaultValue(0.501)
        .min(0.0)
        .max(20.0)
        .sliderMin(0.0)
        .sliderMax(20.0)
        .build()
    );

    private final Setting<Double> verticalSpeed = sgMovement.add(new DoubleSetting.Builder()
        .name("vertical-speed")
        .description("Vertical speed in blocks per second.")
        .defaultValue(0.501)
        .min(0.0)
        .max(20.0)
        .sliderMin(0.0)
        .sliderMax(20.0)
        .build()
    );

    private final Setting<Boolean> sendTeleport = sgBypass.add(new BoolSetting.Builder()
        .name("teleport")
        .description("Sends teleport packets.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> setYaw = sgClient.add(new BoolSetting.Builder()
        .name("set-yaw")
        .description("Sets yaw client side.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> setMove = sgClient.add(new BoolSetting.Builder()
        .name("set-move")
        .description("Sets movement client side.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> setPos = sgClient.add(new BoolSetting.Builder()
        .name("set-pos")
        .description("Sets position client side.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> setID = sgClient.add(new BoolSetting.Builder()
        .name("set-id")
        .description("Updates teleport id when a position packet is received.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> invalidPacket = sgBypass.add(new BoolSetting.Builder()
        .name("invalid-packet")
        .description("Sends invalid movement packets.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> pendingSetbackPoc = sgBypass.add(new BoolSetting.Builder()
        .name("pending-setback-poc")
        .description("Test only: every few seconds sends one flagged move so an NCP set back stays pending, to check if its untracked-move check then goes silent.")
        .defaultValue(false)
        .build()
    );

    private boolean antiKick = false;
    private int downDelayFlying = 10;
    private int downDelay = 4;
    private int flightCounter = 0;
    private int teleportID = 0;
    private int pocCounter = 0;

    public FlightBypass() {
        super(THMAddon.MAIN, "Fly-Bypass", "Fly using packets.");
    }

    @EventHandler
    public void onSendMovementPackets(SendMovementPacketsEvent.Pre event) {
        mc.player.setDeltaMovement(0.0,0.0,0.0);
        double speed = 0.0;
        boolean checkCollisionBoxes = checkHitBoxes();

        boolean movingForward = mc.player.input.keyPresses.forward() && !mc.player.input.keyPresses.backward();
        boolean movingSideways = mc.player.input.keyPresses.left() != mc.player.input.keyPresses.right();
        speed = mc.player.input.keyPresses.jump() && (checkCollisionBoxes || !(movingForward || movingSideways)) ? (antiKick && !checkCollisionBoxes ? (resetCounter(downDelayFlying) ? -0.032 : verticalSpeed.get()/20) : verticalSpeed.get()/20) : (mc.player.input.keyPresses.shift() ? verticalSpeed.get()/-20 : (!checkCollisionBoxes ? (resetCounter(downDelay) ? (antiKick ? -0.04 : 0.0) : 0.0) : 0.0));

        Vec3 horizontal = PlayerUtils.getHorizontalVelocity(horizontalSpeed.get());

        mc.player.setDeltaMovement(horizontal.x, speed, horizontal.z);
        sendPackets(mc.player.getDeltaMovement().x, mc.player.getDeltaMovement().y, mc.player.getDeltaMovement().z, sendTeleport.get());

        // PoC for the NCP untracked-move hole: one flagged move (> 1/16 block, so it fires a real move event)
        // leaves a set back pending. On Folia that set back never confirms without a following move event, and
        // NCP's untracked-move check skips while a set back is pending. If the hole is real, the check goes silent
        // and flight continues; if it is fixed, you keep getting set back regardless.
        if (pendingSetbackPoc.get() && ++pocCounter >= 60) {
            pocCounter = 0;
            packetSender(new ServerboundMovePlayerPacket.Pos(
                mc.player.getX(), mc.player.getY() + 0.5, mc.player.getZ(),
                false, mc.player.horizontalCollision));
        }
    }

    @EventHandler
    public void onMove (PlayerMoveEvent event) {
        if (setMove.get() && flightCounter != 0) {
            event.movement = new Vec3(mc.player.getDeltaMovement().x, mc.player.getDeltaMovement().y, mc.player.getDeltaMovement().z);
        }
    }

    @EventHandler
    public void onPacketSent(PacketEvent.Send event) {
        if (event.packet instanceof ServerboundMovePlayerPacket && !packets.remove((ServerboundMovePlayerPacket) event.packet)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPacketReceive(PacketEvent.Receive event) {
        if (event.packet instanceof ClientboundPlayerPositionPacket && !(mc.player == null || mc.level == null)) {
            ClientboundPlayerPositionPacket packet = (ClientboundPlayerPositionPacket) event.packet;
            PositionMoveRotation oldPos = packet.change();
            if (setYaw.get()) {
                PositionMoveRotation newPos = new PositionMoveRotation(oldPos.position(), oldPos.deltaMovement(), mc.player.getYRot(), mc.player.getXRot());
                event.packet = ClientboundPlayerPositionPacket.of(
                    packet.id(),
                    newPos,
                    packet.relatives()
                );
            }
            if (setID.get()) {
                teleportID = packet.id();
            }
        }
    }

    private boolean checkHitBoxes() {
        return !mc.level.getBlockCollisions(mc.player, mc.player.getBoundingBox().expandTowards(-0.0625,-0.0625,-0.0625)).iterator().hasNext();
    }

    private boolean resetCounter(int counter) {
        if (++flightCounter >= counter) {
            flightCounter = 0;
            return true;
        }
        return false;
    }

    private void sendPackets(double x, double y, double z, boolean teleport) {
        Vec3 vec = new Vec3(x, y, z);
        Vec3 position = new Vec3(mc.player.getX(), mc.player.getY(), mc.player.getZ()).add(vec);
        Vec3 outOfBoundsVec = outOfBoundsVec(vec, position);
        packetSender(new ServerboundMovePlayerPacket.Pos(position.x, position.y, position.z, mc.player.onGround(), mc.player.horizontalCollision));
        if (invalidPacket.get()) {
            packetSender(new ServerboundMovePlayerPacket.Pos(outOfBoundsVec.x, outOfBoundsVec.y, outOfBoundsVec.z, mc.player.onGround(), mc.player.horizontalCollision));
        }
        if (setPos.get()) {
            mc.player.setPosRaw(position.x, position.y, position.z);
        }
        teleportPacket(position, teleport);
    }

    private void teleportPacket(Vec3 pos, boolean shouldTeleport) {
        if (shouldTeleport) {
            mc.player.connection.send(new ServerboundAcceptTeleportationPacket(++teleportID));
        }
    }

    private Vec3 outOfBoundsVec(Vec3 offset, Vec3 position) {
        return position.add(0.0, 1500.0, 0.0);
    }

    private void packetSender(ServerboundMovePlayerPacket packet) {
        packets.add(packet);
        mc.player.connection.send(packet);
    }
    public void onActivate() {
        pocCounter = 0;
        if (mc.player.getItemBySlot(EquipmentSlot.CHEST).getItem() == Items.ELYTRA) {
            warning("You cant have a Elytra equipped");
            toggle();

        }

    }
}
