package xyz.thm.addon.modules;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.movement.elytrafly.ElytraFly;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ArrowItem;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import xyz.thm.addon.THMAddon;
import xyz.thm.addon.utils.RotationUtils;

import java.util.Set;

public class ElytraUAV extends Module {

    private final SettingGroup sgGeneral   = settings.getDefaultGroup();
    private final SettingGroup sgTargeting = settings.createGroup("Targeting");
    private final SettingGroup sgFlight    = settings.createGroup("Flight");

    // ── Targeting ────────────────────────────────────────────────────────────

    private final Setting<Set<EntityType<?>>> entities = sgTargeting.add(new EntityTypeListSetting.Builder()
        .name("entities")
        .description("Entity types to target.")
        .onlyAttackable()
        .defaultValue(EntityType.PLAYER)
        .build());

    // ── General ──────────────────────────────────────────────────────────────

    private final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("mode")
        .description("Bow: charge and release an arrow mid-dive. Mace: removes elytra for a smash attack, then re-equips to fly back up.")
        .defaultValue(Mode.Bow)
        .build());

    private final Setting<Boolean> silentMaceSwap = sgGeneral.add(new BoolSetting.Builder()
        .name("silent-mace-swap")
        .description("Swap to the mace silently (server-side only) so other players don't see the hotbar change.")
        .defaultValue(true)
        .visible(() -> mode.get() == Mode.Mace)
        .build());

    private final Setting<Integer> charge = sgGeneral.add(new IntSetting.Builder()
        .name("bow-charge-ticks")
        .description("How many ticks to hold the bow before releasing. 20 ticks = full-power arrow.")
        .defaultValue(20)
        .range(5, 40)
        .visible(() -> mode.get() == Mode.Bow)
        .build());

    private final Setting<Double> hoverHeight = sgGeneral.add(new DoubleSetting.Builder()
        .name("hover-height")
        .description("How many blocks above the target to hover before diving.")
        .defaultValue(40.0)
        .min(10.0)
        .build());

    private final Setting<Double> diveEndHeight = sgGeneral.add(new DoubleSetting.Builder()
        .name("dive-end-height")
        .description("How many blocks above the target to perform the attack. For Mace, keep this high enough to re-equip the elytra safely.")
        .defaultValue(8.0).min(1.0)
        .build());

    private final Setting<Double> hoverRadius = sgGeneral.add(new DoubleSetting.Builder()
        .name("hover-radius")
        .description("Max horizontal distance from the target before the module corrects position.")
        .defaultValue(1.5)
        .min(0.1)
        .build());

    private final Setting<Double> heightTolerance = sgGeneral.add(new DoubleSetting.Builder()
        .name("height-tolerance")
        .description("Accepted vertical deviation in blocks before altitude correction kicks in.")
        .defaultValue(1.0)
        .min(0.1)
        .build());

    // ── Mace-specific ────────────────────────────────────────────────────────

    /**
     * How many ticks to wait after removing the elytra before swinging the
     * mace. Gives the server time to register the free-fall so the smash
     * bonus applies.
     */
    private final Setting<Integer> elytraRemoveTicks = sgGeneral.add(new IntSetting.Builder()
        .name("elytra-remove-ticks")
        .description("Ticks to wait (in free-fall) after removing the elytra before swinging the mace. More ticks = more fall velocity = harder smash.")
        .defaultValue(6)
        .range(1, 20)
        .visible(() -> mode.get() == Mode.Mace)
        .build());

    // ── Flight ────────────────────────────────────────────────────────────────

    private final Setting<Double> horizontalSpeed = sgFlight.add(new DoubleSetting.Builder()
        .name("horizontal-speed")
        .description("ElytraFly horizontal speed applied when this module activates.")
        .defaultValue(1.5).min(0.1)
        .build());

    private final Setting<Double> verticalSpeed = sgFlight.add(new DoubleSetting.Builder()
        .name("vertical-speed")
        .description("ElytraFly vertical speed applied when this module activates.")
        .defaultValue(2.5).min(0.1)
        .build());

    // ─────────────────────────────────────────────────────────────────────────

    private enum State { APPROACH, HOVER, DIVE, SMASH_WAIT, CLIMB }
    private enum Mode  { Bow, Mace }

    private State        currentState     = State.APPROACH;
    private LivingEntity targetEntity     = null;
    private int          chargeTimer      = 0;
    private boolean      hasShot          = false;
    private int          hoverStableTicks = 0;
    private double       minDiveVy        = 0.0;

    // Mace smash helpers
    private int          smashWaitTimer   = 0;   // counts down after elytra removal
    private int          elytraChestSlot  = -1;  // chest-inventory slot of the stored elytra

    public ElytraUAV() {
        super(THMAddon.PVP, "Elytra-UAV",
            "Hovers above a target and dive-bombs it. Bow mode fires a charged arrow; Mace mode removes the elytra for a smash attack then re-equips it.");
    }

    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public void onActivate() {
        targetEntity = findClosestTarget();
        if (targetEntity == null) {
            ChatUtils.error("ElytraUAV: No valid target found.");
            toggle();
            return;
        }
        resetState();

        ElytraFly elytraFly = Modules.get().get(ElytraFly.class);
        if (elytraFly != null) {
            elytraFly.horizontalSpeed.set(horizontalSpeed.get());
            elytraFly.verticalSpeed.set(verticalSpeed.get());
        }
    }

    @Override
    public void onDeactivate() {
        // If we removed the elytra and never got to re-equip it, put it back.
        tryReequipElytra();
        releaseAll();
    }

    // ─────────────────────────────────────────────────────────────────────────

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;

        // During SMASH_WAIT and CLIMB the player may not be gliding yet.
        if (currentState != State.SMASH_WAIT && currentState != State.CLIMB && !mc.player.isGliding()) return;

        // Re-acquire target if needed.
        if (targetEntity == null || !targetEntity.isAlive()) {
            targetEntity = findClosestTarget();
            if (targetEntity == null) return;
        }

        Vec3d targetPos = targetEntity.getBoundingBox().getCenter();
        Vec3d targetAim = targetEntity.getEyePos();

        double playerY   = mc.player.getY();
        double desiredY  = targetPos.y + hoverHeight.get();
        double distXZ    = Math.hypot(mc.player.getX() - targetPos.x, mc.player.getZ() - targetPos.z);
        double tolY      = heightTolerance.get();

        switch (currentState) {

            // ── APPROACH ─────────────────────────────────────────────────────
            case APPROACH -> {
                mc.options.useKey.setPressed(false);
                aimAt(targetAim);
                applyHorizontalCorrection(targetPos, hoverRadius.get());
                applyVerticalControl(desiredY, tolY);

                if (distXZ <= hoverRadius.get()) {
                    currentState     = State.HOVER;
                    hoverStableTicks = 0;
                }
            }

            // ── HOVER ─────────────────────────────────────────────────────────
            case HOVER -> {
                mc.options.useKey.setPressed(false);
                aimAt(targetAim);

                if (distXZ > hoverRadius.get()) applyHorizontalCorrection(targetPos, hoverRadius.get());
                else stopHorizontal();
                applyVerticalControl(desiredY, tolY);

                boolean stableY = Math.abs(playerY - desiredY) <= tolY;
                if (distXZ <= hoverRadius.get() && stableY) hoverStableTicks++;
                else hoverStableTicks = 0;

                if (hoverStableTicks >= 5) {
                    currentState = State.DIVE;
                    chargeTimer  = 0;
                    hasShot      = false;
                    smashWaitTimer = 0;
                    minDiveVy    = 0.0;
                }
            }

            // ── DIVE ──────────────────────────────────────────────────────────
            case DIVE -> {
                aimAt(targetAim);

                if (distXZ > hoverRadius.get()) applyHorizontalCorrection(targetPos, hoverRadius.get());
                else stopHorizontal();

                if (!canDiveAttack()) {
                    mc.options.useKey.setPressed(false);
                    currentState = State.HOVER;
                    break;
                }

                double diveY        = targetPos.y + diveEndHeight.get();
                boolean inAttackBand = playerY <= diveY + tolY;

                if (mode.get() == Mode.Bow) {
                    // Hold use-key to charge the bow.
                    mc.options.useKey.setPressed(true);
                    chargeTimer++;

                    boolean fullyCharged = chargeTimer >= charge.get();
                    double  vy           = mc.player.getVelocity().y;
                    if (chargeTimer == 1) minDiveVy = vy;
                    if (vy < minDiveVy) minDiveVy = vy;
                    boolean passedPeak = vy > minDiveVy + 0.02;

                    // Descend toward diveY.
                    mc.options.jumpKey.setPressed(playerY < diveY - tolY);
                    mc.options.sneakKey.setPressed(playerY > diveY + tolY);

                    if (!hasShot && inAttackBand && (passedPeak || fullyCharged)) {
                        mc.interactionManager.stopUsingItem(mc.player);
                        mc.options.useKey.setPressed(false);
                        hasShot      = true;
                        currentState = State.CLIMB;
                    }

                } else {
                    // Mace: just descend — the actual hit happens in SMASH_WAIT after elytra removal.
                    mc.options.useKey.setPressed(false);
                    mc.options.jumpKey.setPressed(playerY < diveY - tolY);
                    mc.options.sneakKey.setPressed(playerY > diveY + tolY);

                    if (inAttackBand) {
                        // Remove the elytra so the server counts us as falling.
                        removeElytra();
                        currentState   = State.SMASH_WAIT;
                        smashWaitTimer = 0;
                        releaseAll();
                    }
                }
            }

            // ── SMASH_WAIT ────────────────────────────────────────────────────
            // Elytra is off; we are in free-fall. Wait a few ticks so the server
            // accumulates fall distance, then swing the mace.
            case SMASH_WAIT -> {
                aimAt(targetAim);
                smashWaitTimer++;

                if (smashWaitTimer >= elytraRemoveTicks.get()) {
                    // Perform the smash.
                    performMaceSmash();
                    // Re-equip the elytra immediately so ElytraFly can kick in.
                    tryReequipElytra();
                    currentState = State.CLIMB;
                }
            }

            // ── CLIMB ─────────────────────────────────────────────────────────
            case CLIMB -> {
                mc.options.useKey.setPressed(false);

                // If not gliding yet (elytra was just re-equipped), re-enable
                // ElytraFly as a fallback and keep jumping to get airborne.
                if (!mc.player.isGliding()) {
                    ElytraFly elytraFly = Modules.get().get(ElytraFly.class);
                    if (elytraFly != null && !elytraFly.isActive()) elytraFly.toggle();
                    mc.options.jumpKey.setPressed(true);
                    return;
                }

                if (playerY >= desiredY - tolY) {
                    resetState();
                    currentState = State.APPROACH;
                    return;
                }
                aimAt(targetAim);
                applyHorizontalCorrection(targetPos, hoverRadius.get());
                applyVerticalControl(desiredY, tolY);
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Mace helpers

    /**
     * PlayerScreenHandler container slot layout:
     *   5 = helmet, 6 = chestplate, 7 = leggings, 8 = boots
     *   9–35  = main inventory (inventory indices 9–35)
     *   36–44 = hotbar (inventory indices 0–8)
     *
     * So for a main-inventory slot at inventory index i (9 ≤ i ≤ 35):
     *   container slot = i          (they share the same number in PlayerScreenHandler)
     */
    private static final int CONTAINER_CHEST_SLOT = 6;

    /**
     * Removes the elytra from the chest armour slot into a free main-inventory
     * slot so the server registers us as falling (no elytra = no glide).
     */
    private void removeElytra() {
        if (mc.player == null || mc.interactionManager == null) return;
        if (mc.player.getInventory().getStack(38).getItem() != Items.ELYTRA) return;

        // Find a free MAIN-inventory slot (inventory indices 9–35; skip hotbar 0–8).
        elytraChestSlot = -1;
        for (int i = 9; i < 36; i++) {
            if (mc.player.getInventory().getStack(i).isEmpty()) {
                elytraChestSlot = i;
                break;
            }
        }
        if (elytraChestSlot == -1) return; // No space — abort.

        // In PlayerScreenHandler, main-inv index i (9-35) == container slot i.
        int containerDest = elytraChestSlot;

        // Pick up elytra from chestplate slot onto cursor.
        mc.interactionManager.clickSlot(
            mc.player.playerScreenHandler.syncId,
            CONTAINER_CHEST_SLOT,
            0,
            net.minecraft.screen.slot.SlotActionType.PICKUP,
            mc.player
        );
        // Place cursor item into the free inventory slot.
        mc.interactionManager.clickSlot(
            mc.player.playerScreenHandler.syncId,
            containerDest,
            0,
            net.minecraft.screen.slot.SlotActionType.PICKUP,
            mc.player
        );
    }

    /** Puts the elytra back into the chest armour slot. */
    private void tryReequipElytra() {
        if (mc.player == null || mc.interactionManager == null || elytraChestSlot == -1) return;
        if (mc.player.getInventory().getStack(elytraChestSlot).getItem() != Items.ELYTRA) {
            elytraChestSlot = -1;
            return;
        }

        int containerSrc = elytraChestSlot; // main-inv index i (9-35) == container slot i

        // Pick up elytra from where we stashed it.
        mc.interactionManager.clickSlot(
            mc.player.playerScreenHandler.syncId,
            containerSrc,
            0,
            net.minecraft.screen.slot.SlotActionType.PICKUP,
            mc.player
        );
        // Place it back into the chestplate slot.
        mc.interactionManager.clickSlot(
            mc.player.playerScreenHandler.syncId,
            CONTAINER_CHEST_SLOT,
            0,
            net.minecraft.screen.slot.SlotActionType.PICKUP,
            mc.player
        );
        elytraChestSlot = -1;
    }

    private void performMaceSmash() {
        FindItemResult mace = InvUtils.findInHotbar(Items.MACE);
        if (!mace.found() || mc.interactionManager == null || targetEntity == null || !targetEntity.isAlive()) return;

        int selectedSlot = mc.player.getInventory().getSelectedSlot();
        boolean swapped  = selectedSlot != mace.slot() && InvUtils.swap(mace.slot(), silentMaceSwap.get());

        mc.interactionManager.attackEntity(mc.player, targetEntity);
        mc.player.swingHand(Hand.MAIN_HAND);

        if (silentMaceSwap.get() && swapped) InvUtils.swapBack();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Movement helpers

    private void aimAt(Vec3d target) {
        float[] rot = RotationUtils.getRotationsTo(mc.player.getEyePos(), target);
        mc.player.setYaw(rot[0]);
        mc.player.setPitch(rot[1]);
    }

    private void stopHorizontal() {
        mc.options.forwardKey.setPressed(false);
        mc.options.backKey.setPressed(false);
        mc.options.leftKey.setPressed(false);
        mc.options.rightKey.setPressed(false);
    }

    private void releaseAll() {
        stopHorizontal();
        mc.options.jumpKey.setPressed(false);
        mc.options.sneakKey.setPressed(false);
        mc.options.useKey.setPressed(false);
    }

    private void applyHorizontalCorrection(Vec3d targetPos, double tolerance) {
        double dx   = targetPos.x - mc.player.getX();
        double dz   = targetPos.z - mc.player.getZ();
        boolean move = Math.hypot(dx, dz) > tolerance;
        mc.options.forwardKey.setPressed(move);
        mc.options.backKey.setPressed(false);
        mc.options.rightKey.setPressed(false);
        mc.options.leftKey.setPressed(false);
    }

    private void applyVerticalControl(double targetY, double tolerance) {
        mc.options.jumpKey.setPressed(mc.player.getY() < targetY - tolerance);
        mc.options.sneakKey.setPressed(mc.player.getY() > targetY + tolerance);
    }

    private boolean canDiveAttack() {
        if (mode.get() == Mode.Bow) {
            boolean hasBow    = mc.player.getMainHandStack().getItem() == Items.BOW;
            boolean hasArrows = mc.player.getAbilities().creativeMode
                || InvUtils.find(stack -> stack.getItem() instanceof ArrowItem).found();
            return hasBow && hasArrows;
        }
        // Mace mode: need both mace in hotbar and elytra equipped (so we can remove it).
        boolean hasMace   = InvUtils.findInHotbar(Items.MACE).found();
        boolean hasElytra = mc.player.getInventory().getStack(38).getItem() == Items.ELYTRA;
        return hasMace && hasElytra;
    }

    private void resetState() {
        chargeTimer      = 0;
        hasShot          = false;
        hoverStableTicks = 0;
        smashWaitTimer   = 0;
        minDiveVy        = 0.0;
    }

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
