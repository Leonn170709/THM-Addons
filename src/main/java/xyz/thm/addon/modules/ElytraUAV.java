package xyz.thm.addon.modules;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.movement.elytrafly.ElytraFly;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import xyz.thm.addon.THMAddon;
import xyz.thm.addon.utils.RotationUtils;

import java.util.Set;

public class ElytraUAV extends Module {

    private final SettingGroup sgGeneral  = settings.getDefaultGroup();
    private final SettingGroup sgTargeting = settings.createGroup("Targeting");
    private final SettingGroup sgFlight   = settings.createGroup("Flight");

    // --- Targeting ---

    /** Which entity types to target. Defaults to players only. */
    private final Setting<Set<EntityType<?>>> entities = sgTargeting.add(new EntityTypeListSetting.Builder()
        .name("entities")
        .description("Entity types to target.")
        .onlyAttackable()
        .defaultValue(EntityType.PLAYER)
        .build());

    // --- General ---

    /** Ticks to hold the bow before releasing. 20 = full power arrow. */
    private final Setting<Integer> charge = sgGeneral.add(new IntSetting.Builder()
        .name("charge")
        .description("Ticks to hold bow before releasing. 20 ticks = full power.")
        .defaultValue(20).range(5, 40).build());

    /** How many blocks above the target to hover before diving. */
    private final Setting<Double> hoverHeight = sgGeneral.add(new DoubleSetting.Builder()
        .name("hover-height")
        .description("Blocks above the target to hover at before diving.")
        .defaultValue(40.0).build());

    /** Height above target at which the dive ends and climb begins. */
    private final Setting<Double> diveEndHeight = sgGeneral.add(new DoubleSetting.Builder()
        .name("dive-end-height")
        .description("Height above target where the dive stops and the climb starts.")
        .defaultValue(8.0).build());

    /**
     * Horizontal tolerance. Player must be within this distance
     * before the dive starts. Lower values = more precise hovering.
     */
    private final Setting<Double> hoverRadius = sgGeneral.add(new DoubleSetting.Builder()
        .name("hover-radius")
        .description("Horizontal tolerance above target. Lower = more precise positioning.")
        .defaultValue(1.5).min(0.1).build());

    /** Vertical tolerance for hover/dive positioning. */
    private final Setting<Double> heightTolerance = sgGeneral.add(new DoubleSetting.Builder()
        .name("height-tolerance")
        .description("Vertical tolerance in blocks.")
        .defaultValue(1.0).min(0.1).build());


    // --- Flight ---

    /** Horizontal speed applied to ElytraFly on activation. */
    private final Setting<Double> horizontalSpeed = sgFlight.add(new DoubleSetting.Builder()
        .name("horizontal-speed")
        .description("Horizontal speed set on ElytraFly when the module activates.")
        .defaultValue(1.5).min(0).build());

    /** Vertical speed applied to ElytraFly on activation. */
    private final Setting<Double> verticalSpeed = sgFlight.add(new DoubleSetting.Builder()
        .name("vertical-speed")
        .description("Vertical speed set on ElytraFly when the module activates.")
        .defaultValue(2.5).min(0).build());

    // -------------------------------------------------------------------------

    private enum State { APPROACH, HOVER, DIVE, CLIMB }

    private State         currentState = State.APPROACH;
    private LivingEntity  targetEntity = null;
    private int           chargeTimer  = 0;
    private boolean       hasShot      = false;
    private int           hoverStableTicks = 0;
    private double        minDiveVy = 0.0;

    public ElytraUAV() {
        super(THMAddon.PVP, "Elytra-UAV", "Hovers above a target and dive-bombs them with a charged arrow.");
    }

    // -------------------------------------------------------------------------

    @Override
    public void onActivate() {
        targetEntity = findClosestTarget();
        if (targetEntity == null) {
            ChatUtils.error("ElytraUAV: No valid target found.");
            toggle();
            return;
        }

        currentState = State.APPROACH;
        chargeTimer  = 0;
        hasShot      = false;
        hoverStableTicks = 0;

        ElytraFly elytraFly = Modules.get().get(ElytraFly.class);
        if (elytraFly != null) {
            elytraFly.horizontalSpeed.set(horizontalSpeed.get());
            elytraFly.verticalSpeed.set(verticalSpeed.get());
        }
    }

    @Override
    public void onDeactivate() {
        releaseAll();
    }

    // -------------------------------------------------------------------------

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null || !mc.player.isGliding()) return;

        // Re-acquire if target is gone
        if (targetEntity == null || !targetEntity.isAlive()) {
            targetEntity = findClosestTarget();
            if (targetEntity == null) return;
        }

        // Use the true bounding box center — getPos() is feet-only and can be
        // offset from the actual hitbox center, causing the "one block west" miss.
        Vec3d targetPos = targetEntity.getBoundingBox().getCenter();
        Vec3d targetAim = targetEntity.getEyePos();

        double playerY    = mc.player.getY();
        double desiredY   = targetPos.y + hoverHeight.get();
        double distXZ     = Math.hypot(mc.player.getX() - targetPos.x, mc.player.getZ() - targetPos.z);
        double tolY       = heightTolerance.get();

        switch (currentState) {

            // -----------------------------------------------------------------
            // APPROACH: Fly toward the target horizontally and gain altitude.
            // Transitions to HOVER once inside the dead-zone at correct height.
            // A minimum effective radius of 2.0 is enforced so floating-point
            // imprecision never prevents the transition even if the setting is 0.
            // -----------------------------------------------------------------
            case APPROACH -> {
                mc.options.useKey.setPressed(false);
                // Aim at target for precise movement
                float[] rot = RotationUtils.getRotationsTo(mc.player.getEyePos(), targetAim);
                mc.player.setYaw(rot[0]);
                mc.player.setPitch(rot[1]);

                applyHorizontalCorrection(targetPos, hoverRadius.get());
                applyVerticalControl(desiredY, tolY);

                if (distXZ <= hoverRadius.get()) {
                    currentState = State.HOVER;
                    hoverStableTicks = 0;
                }
            }

            // -----------------------------------------------------------------
            // HOVER: Player is directly above the target.
            // Look straight down, hold altitude, NO horizontal input whatsoever.
            // If we drift out of the dead-zone, fall back to APPROACH.
            // -----------------------------------------------------------------
            case HOVER -> {
                mc.options.useKey.setPressed(false);
                float[] rot = RotationUtils.getRotationsTo(mc.player.getEyePos(), targetAim);
                mc.player.setYaw(rot[0]);
                mc.player.setPitch(rot[1]);

                if (distXZ > hoverRadius.get()) applyHorizontalCorrection(targetPos, hoverRadius.get());
                else {
                    mc.options.forwardKey.setPressed(false);
                    mc.options.backKey.setPressed(false);
                    mc.options.leftKey.setPressed(false);
                    mc.options.rightKey.setPressed(false);
                }
                applyVerticalControl(desiredY, tolY);

                boolean stableY = Math.abs(playerY - desiredY) <= tolY;
                if (distXZ <= hoverRadius.get() && stableY) hoverStableTicks++;
                else hoverStableTicks = 0;

                if (hoverStableTicks >= 5) {
                    currentState = State.DIVE;
                    chargeTimer  = 0;
                    hasShot      = false;
                    hoverStableTicks = 0;
                    minDiveVy = 0.0;
                }
            }

            // -----------------------------------------------------------------
            // DIVE: Look straight down, charge bow, descend fast.
            // Release arrow when fully charged OR approaching abort height.
            // -----------------------------------------------------------------
            case DIVE -> {
                float[] rot = RotationUtils.getRotationsTo(mc.player.getEyePos(), targetAim);
                mc.player.setYaw(rot[0]);
                mc.player.setPitch(rot[1]);

                if (distXZ > hoverRadius.get()) applyHorizontalCorrection(targetPos, hoverRadius.get());
                else {
                    mc.options.forwardKey.setPressed(false);
                    mc.options.backKey.setPressed(false);
                    mc.options.leftKey.setPressed(false);
                    mc.options.rightKey.setPressed(false);
                }

                boolean hasBow = mc.player.getMainHandStack().getItem() == net.minecraft.item.Items.BOW;
                boolean hasArrows = mc.player.getAbilities().creativeMode
                    || meteordevelopment.meteorclient.utils.player.InvUtils.find(itemStack -> itemStack.getItem() instanceof net.minecraft.item.ArrowItem).found();
                if (!hasBow || !hasArrows) {
                    mc.options.useKey.setPressed(false);
                    currentState = State.HOVER;
                    break;
                }

                // Charge bow
                mc.options.useKey.setPressed(true);
                chargeTimer++;

                boolean fullyCharged = chargeTimer >= charge.get();
                double diveY = targetPos.y + diveEndHeight.get();
                boolean tooLow  = playerY < diveY - tolY;
                boolean tooHigh = playerY > diveY + tolY;

                // Descend until diveY, then hold
                mc.options.jumpKey.setPressed(tooLow);
                mc.options.sneakKey.setPressed(tooHigh);

                // Release at peak downward velocity (ignore charge) or when fully charged.
                double vy = mc.player.getVelocity().y;
                if (chargeTimer == 1) minDiveVy = vy;
                if (vy < minDiveVy) minDiveVy = vy;
                boolean passedPeak = vy > minDiveVy + 0.02;
                boolean inReleaseBand = playerY <= diveY + tolY;

                if (!hasShot && inReleaseBand && (passedPeak || fullyCharged)) {
                    mc.interactionManager.stopUsingItem(mc.player);
                    mc.options.useKey.setPressed(false);
                    hasShot = true;
                    currentState = State.CLIMB;
                }
            }

            // -----------------------------------------------------------------
            // CLIMB: Hold space and go straight up.
            // If already at or above hover height (e.g. started the module from
            // high up), skip directly to APPROACH so no time is wasted.
            // -----------------------------------------------------------------
            case CLIMB -> {
                mc.options.useKey.setPressed(false);
                if (playerY >= desiredY - tolY) {
                    // Already high enough — re-align horizontally before next dive
                    currentState = State.APPROACH;
                    chargeTimer  = 0;
                    hasShot      = false;
                    return;
                }

                float[] rot = RotationUtils.getRotationsTo(mc.player.getEyePos(), targetAim);
                mc.player.setYaw(rot[0]);
                mc.player.setPitch(rot[1]);

                applyHorizontalCorrection(targetPos, hoverRadius.get());
                applyVerticalControl(desiredY, tolY);
            }
        }
    }

    // -------------------------------------------------------------------------

    /** Releases every movement and action key. */
    private void releaseAll() {
        mc.options.forwardKey.setPressed(false);
        mc.options.backKey.setPressed(false);
        mc.options.leftKey.setPressed(false);
        mc.options.rightKey.setPressed(false);
        mc.options.jumpKey.setPressed(false);
        mc.options.sneakKey.setPressed(false);
        mc.options.useKey.setPressed(false);
    }

    private void applyHorizontalCorrection(Vec3d targetPos, double tolerance) {
        double dx = targetPos.x - mc.player.getX();
        double dz = targetPos.z - mc.player.getZ();

        double dist = Math.hypot(dx, dz);
        boolean move = dist > tolerance;

        // Forward-only correction to prevent left/right oscillation.
        mc.options.forwardKey.setPressed(move);
        mc.options.backKey.setPressed(false);
        mc.options.rightKey.setPressed(false);
        mc.options.leftKey.setPressed(false);
    }

    private void applyVerticalControl(double targetY, double tolerance) {
        boolean tooLow = mc.player.getY() < targetY - tolerance;
        boolean tooHigh = mc.player.getY() > targetY + tolerance;
        mc.options.jumpKey.setPressed(tooLow);
        mc.options.sneakKey.setPressed(tooHigh);
    }

    /**
     * Returns the closest living, attackable entity whose type is in the
     * entities setting, excluding the local player and any friends.
     */
    private LivingEntity findClosestTarget() {
        LivingEntity closest = null;
        double minDist = Double.MAX_VALUE;
        for (Entity e : mc.world.getEntities()) {
            if (!(e instanceof LivingEntity living)) continue;
            if (e == mc.player || !e.isAlive()) continue;
            if (!entities.get().contains(e.getType())) continue;
            if (e instanceof PlayerEntity p && !Friends.get().shouldAttack(p)) continue;
            double d = mc.player.distanceTo(e);
            if (d < minDist) { minDist = d; closest = living; }
        }
        return closest;
    }
}
