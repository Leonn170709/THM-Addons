/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.modules;

import meteordevelopment.meteorclient.events.entity.EntityAddedEvent;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.renderer.text.TextRenderer;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.entity.DamageUtils;
import meteordevelopment.meteorclient.utils.entity.EntityUtils;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.render.NametagUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import xyz.thm.addon.THMAddon;
import xyz.thm.addon.mixin.accessor.PlayerInteractEntityC2SPacketAccessor;
import xyz.thm.addon.system.THMSystem;
import xyz.thm.addon.utils.PlacementUtils;
import xyz.thm.addon.utils.RenderUtilsTHM;
import xyz.thm.addon.utils.RotationUtils;
import xyz.thm.addon.utils.ThmMembers;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Custom CrystalAura implementation, algorithmically inspired by BlackOut's AutoCrystalPlus
 * (https://github.com/H1ggsK/BlackOut). Unlike Meteor's stock CrystalAura, every candidate
 * placement/explosion is checked against friend splash damage (not just "don't target friends"),
 * and a force-pop override lets a lethal hit bypass the normal min-damage/ratio gate while self-
 * and friend-safety limits stay hard limits. Recently-used placement spots are put on a short
 * cooldown to avoid the instant re-place-same-block packet spam pattern.
 *
 * All rotation configuration (mode, yaw-step pacing, priority) lives in this module's own
 * settings and only ever reads from RotationUtils (getServerYaw/canSeePosition) or calls its
 * per-invocation rotate methods - it never touches RotationUtils' shared singleton fields
 * (movementFix/mouseSensFix/preserveTicks/webJumpFixEnabled), which are global to every module.
 *
 * ID-predict is ported (see "ID Predict" settings): on 2b2t-style anarchy servers it's the
 * single biggest edge a crystal aura can have, since it lets the explode happen the same tick
 * the crystal is placed instead of waiting a tick for the spawn packet to round-trip - a real
 * client-vs-client fight is usually decided by whoever's aura reacts faster. It works by
 * guessing the new crystal's entity id (ids are assigned sequentially by the server) and firing
 * a raw attack packet at that guessed id after a short delay, via a mixin-exposed setter on
 * PlayerInteractEntityC2SPacket's normally-final entityId field (PlayerInteractEntityC2SPacketAccessor).
 * If the guess is wrong the packet is just a harmless no-op server-side.
 *
 * Deliberately NOT ported from BlackOut: movement extrapolation (multi-tick position prediction -
 * DamageUtils.crystalDamage's own predict-movement flag, exposed here as predict-movement, covers
 * the common case) and "only own crystals" tracking for the explode phase - add if ever needed.
 */
public class CrystalAuraTHM extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgDamage = settings.createGroup("Damage");
    private final SettingGroup sgPlace = settings.createGroup("Place");
    private final SettingGroup sgExplode = settings.createGroup("Explode");
    private final SettingGroup sgIdPredict = settings.createGroup("ID Predict");
    private final SettingGroup sgSwitch = settings.createGroup("Switch");
    private final SettingGroup sgRotation = settings.createGroup("Rotation");
    private final SettingGroup sgRender = settings.createGroup("Render");

    // General

    private final Setting<Boolean> players = sgGeneral.add(new BoolSetting.Builder()
        .name("players").description("Targets players.").defaultValue(true).build());

    private final Setting<Boolean> mobs = sgGeneral.add(new BoolSetting.Builder()
        .name("mobs").description("Targets hostile mobs.").defaultValue(false).build());

    private final Setting<Boolean> animals = sgGeneral.add(new BoolSetting.Builder()
        .name("animals").description("Targets animals.").defaultValue(false).build());

    private final Setting<Double> targetRange = sgGeneral.add(new DoubleSetting.Builder()
        .name("target-range").description("Range in which to look for targets.")
        .defaultValue(10).min(0).sliderMax(16).build());

    private final Setting<Boolean> predictMovement = sgGeneral.add(new BoolSetting.Builder()
        .name("predict-movement").description("Predicts target movement one tick ahead when calculating damage.")
        .defaultValue(false).build());

    private final Setting<Boolean> sameTick = sgGeneral.add(new BoolSetting.Builder()
        .name("same-tick").description("Allows exploding and placing in the same tick.")
        .defaultValue(false).build());

    private final Setting<Boolean> pauseEat = sgGeneral.add(new BoolSetting.Builder()
        .name("pause-eat").description("Pauses while eating/drinking/using an item.")
        .defaultValue(true).build());

    private final Setting<Double> pauseHealth = sgGeneral.add(new DoubleSetting.Builder()
        .name("pause-health").description("Pauses entirely when your health drops to or below this.")
        .defaultValue(0).min(0).sliderMax(20).build());

    // Damage

    private final Setting<Double> minDamage = sgDamage.add(new DoubleSetting.Builder()
        .name("min-damage").description("Minimum damage a crystal must deal to the best enemy target.")
        .defaultValue(6).min(0).sliderMax(36).build());

    private final Setting<Double> maxSelfDamage = sgDamage.add(new DoubleSetting.Builder()
        .name("max-self-damage").description("Maximum damage a crystal is allowed to deal to you.")
        .defaultValue(7).min(0).sliderMax(36).build());

    private final Setting<Double> minRatio = sgDamage.add(new DoubleSetting.Builder()
        .name("min-ratio").description("Minimum enemy/self damage ratio required.")
        .defaultValue(1.2).min(0).sliderMax(5).build());

    private final Setting<Double> maxFriendDamage = sgDamage.add(new DoubleSetting.Builder()
        .name("max-friend-damage").description("Maximum splash damage a crystal is allowed to deal to nearby friends.")
        .defaultValue(6).min(0).sliderMax(36).build());

    private final Setting<Boolean> antiSuicide = sgDamage.add(new BoolSetting.Builder()
        .name("anti-suicide").description("Never place/explode a crystal that would kill you.")
        .defaultValue(true).build());

    private final Setting<Boolean> forcePop = sgDamage.add(new BoolSetting.Builder()
        .name("force-pop").description("Ignores damage checks for a lethal hit. Safety limits still apply.")
        .defaultValue(true).build());

    private final Setting<Integer> forcePopHits = sgDamage.add(new IntSetting.Builder()
        .name("force-pop-hits").description("Ignores min-damage/ratio if the target would die within this many hits of this damage.")
        .defaultValue(1).min(1).sliderMax(5).visible(forcePop::get).build());

    // Place

    private final Setting<Boolean> doPlace = sgPlace.add(new BoolSetting.Builder()
        .name("place").description("Places crystals.").defaultValue(true).build());

    private final Setting<Double> placeRange = sgPlace.add(new DoubleSetting.Builder()
        .name("place-range").description("Range in which to place crystals.")
        .defaultValue(5.2).min(0).sliderMax(6).build());

    private final Setting<Boolean> raytrace = sgPlace.add(new BoolSetting.Builder()
        .name("raytrace").description("Only places on faces you can actually see. Off places through walls, which is faster but obvious.")
        .defaultValue(true).build());

    private final Setting<Boolean> oldPlacement = sgPlace.add(new BoolSetting.Builder()
        .name("1.12-placement").description("Requires two air blocks above the base, like pre-1.13 clients.")
        .defaultValue(false).build());

    private final Setting<Integer> existedCooldown = sgPlace.add(new IntSetting.Builder()
        .name("existed-cooldown").description("Ticks before placing again on a spot just placed or broken on.")
        .defaultValue(10).min(0).sliderMax(40).build());

    private final Setting<Boolean> support = sgPlace.add(new BoolSetting.Builder()
        .name("support").description("Places obsidian to build a base where a good crystal spot would otherwise not exist.")
        .defaultValue(false).build());

    private final Setting<Double> supportMinGain = sgPlace.add(new DoubleSetting.Builder()
        .name("support-min-gain").description("Extra damage a support spot must beat the best existing spot by. High values make support a last resort.")
        .defaultValue(6).min(0).sliderMax(20).visible(support::get).build());

    private final Setting<Integer> supportDelay = sgPlace.add(new IntSetting.Builder()
        .name("support-delay").description("Ticks to wait after placing a support block, before placing anything else.")
        .defaultValue(4).min(0).sliderMax(20).visible(support::get).build());

    private final Setting<Integer> placeCPT = sgPlace.add(new IntSetting.Builder()
        .name("place-cpt").description("Crystals to place per tick.")
        .defaultValue(1).min(1).sliderMax(6).build());

    private final Setting<Integer> placeCooldown = sgPlace.add(new IntSetting.Builder()
        .name("place-cooldown").description("Ticks to wait before placing the next batch of crystals.")
        .defaultValue(0).min(0).sliderMax(20).build());

    // Explode

    private final Setting<Boolean> doExplode = sgExplode.add(new BoolSetting.Builder()
        .name("explode").description("Attacks/explodes crystals.").defaultValue(true).build());

    private final Setting<Double> explodeRange = sgExplode.add(new DoubleSetting.Builder()
        .name("explode-range").description("Range in which to attack crystals.")
        .defaultValue(5.2).min(0).sliderMax(6).build());

    private final Setting<Integer> explodeCPT = sgExplode.add(new IntSetting.Builder()
        .name("explode-cpt").description("Crystals to attack per tick.")
        .defaultValue(2).min(1).sliderMax(6).build());

    private final Setting<Integer> explodeCooldown = sgExplode.add(new IntSetting.Builder()
        .name("explode-cooldown").description("Ticks to wait before attacking the next batch of crystals.")
        .defaultValue(0).min(0).sliderMax(20).build());

    // ID Predict

    private final Setting<Boolean> idPredict = sgIdPredict.add(new BoolSetting.Builder()
        .name("id-predict").description("Attacks a new crystal by guessing its entity id, without waiting a tick.")
        .defaultValue(false).build());

    private final Setting<Integer> idStartOffset = sgIdPredict.add(new IntSetting.Builder()
        .name("id-start-offset").description("How many ids ahead of the last confirmed entity id to start guessing from.")
        .defaultValue(1).min(0).sliderMax(10).visible(idPredict::get).build());

    private final Setting<Integer> idPackets = sgIdPredict.add(new IntSetting.Builder()
        .name("id-packets").description("How many guessed ids to shoot at, to cover uncertainty in the actual assigned id.")
        .defaultValue(2).min(1).sliderMax(5).visible(idPredict::get).build());

    private final Setting<Integer> idOffset = sgIdPredict.add(new IntSetting.Builder()
        .name("id-offset").description("Spacing between guessed ids.")
        .defaultValue(1).min(1).sliderMax(5).visible(idPredict::get).build());

    private final Setting<Integer> idDelayTicks = sgIdPredict.add(new IntSetting.Builder()
        .name("id-delay-ticks").description("Ticks after placement to fire the predicted attack(s).")
        .defaultValue(1).min(0).sliderMax(10).visible(idPredict::get).build());

    // Switch

    private final Setting<Boolean> autoSwitch = sgSwitch.add(new BoolSetting.Builder()
        .name("auto-switch").description("Automatically switches to crystals in your hotbar to place.")
        .defaultValue(true).build());

    private final Setting<Boolean> silentSwitch = sgSwitch.add(new BoolSetting.Builder()
        .name("silent-switch").description("Switches back to your previous slot after placing.")
        .defaultValue(true).visible(autoSwitch::get).build());

    private final Setting<Boolean> antiWeakness = sgSwitch.add(new BoolSetting.Builder()
        .name("anti-weakness").description("Switches to your strongest weapon while you have weakness so crystals still break.")
        .defaultValue(true).build());

    // Rotation - module-local only; reads RotationUtils' public getters/rotate methods but
    // never writes to its shared singleton fields (those are global across every module).

    private final Setting<Boolean> rotate = sgRotation.add(new BoolSetting.Builder()
        .name("rotate").description("Rotates towards crystals being placed/broken.")
        .defaultValue(true).build());

    private final Setting<RotationMode> rotationMode = sgRotation.add(new EnumSetting.Builder<RotationMode>()
        .name("rotation-mode").description("Silent only sends the rotation to the server. Client also turns your camera.")
        .defaultValue(RotationMode.Silent).visible(rotate::get).build());

    private final Setting<RotateOn> rotateOn = sgRotation.add(new EnumSetting.Builder<RotateOn>()
        .name("rotate-on").description("Which actions rotate.")
        .defaultValue(RotateOn.Both).visible(rotate::get).build());

    private final Setting<Double> yawStepLimit = sgRotation.add(new DoubleSetting.Builder()
        .name("yaw-step-limit").description("Maximum degrees allowed to rotate per tick. 180 = unlimited (instant snap).")
        .defaultValue(180).range(1, 180).visible(rotate::get).build());

    private final Setting<Integer> rotationPriority = sgRotation.add(new IntSetting.Builder()
        .name("rotation-priority").description("Priority of this module's rotations relative to other modules using silent rotation.")
        .defaultValue(1000).min(0).sliderMax(5000).visible(() -> rotate.get() && rotationMode.get() != RotationMode.Client).build());

    // Render

    private final Setting<Boolean> render = sgRender.add(new BoolSetting.Builder()
        .name("render").description("Renders placed/broken crystal positions.")
        .defaultValue(true).build());

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode").description("How the render is shaped.")
        .defaultValue(ShapeMode.Both).build());

    private final Setting<SettingColor> placeSideColor = sgRender.add(new ColorSetting.Builder()
        .name("place-side-color").description("Side color for placements.")
        .defaultValue(new SettingColor(THMAddon.THMColor.r, THMAddon.THMColor.g, THMAddon.THMColor.b, 60)).build());

    private final Setting<SettingColor> placeLineColor = sgRender.add(new ColorSetting.Builder()
        .name("place-line-color").description("Line color for placements.")
        .defaultValue(new SettingColor(THMAddon.THMColor.r, THMAddon.THMColor.g, THMAddon.THMColor.b, THMAddon.THMColor.a)).build());

    private final Setting<SettingColor> breakSideColor = sgRender.add(new ColorSetting.Builder()
        .name("break-side-color").description("Side color for explosions.")
        .defaultValue(new SettingColor(255, 60, 60, 60)).build());

    private final Setting<SettingColor> breakLineColor = sgRender.add(new ColorSetting.Builder()
        .name("break-line-color").description("Line color for explosions.")
        .defaultValue(new SettingColor(255, 60, 60)).build());

    private final Setting<CrystalRender> renderMode = sgRender.add(new EnumSetting.Builder<CrystalRender>()
        .name("render-mode").description("How the box behaves over its lifetime.")
        .defaultValue(CrystalRender.Fade).build());

    private final Setting<Boolean> damageText = sgRender.add(new BoolSetting.Builder()
        .name("damage-text").description("Draws the damage dealt to the target on the crystal position.")
        .defaultValue(true).build());

    private final Setting<Double> damageTextScale = sgRender.add(new DoubleSetting.Builder()
        .name("damage-text-scale").description("Size of the damage text.")
        .defaultValue(1.5).min(0.1).sliderRange(0.5, 4).visible(damageText::get).build());

    private final Setting<Integer> renderDuration = sgRender.add(new IntSetting.Builder()
        .name("render-duration").description("How long placements/explosions render for, in ticks.")
        .defaultValue(10).min(0).sliderMax(40).build());

    // State

    private final List<LivingEntity> targets = new ArrayList<>();
    private final Map<BlockPos, Integer> recentlyUsed = new HashMap<>();
    private final List<PendingPredict> pendingPredicts = new ArrayList<>();
    private final List<DamageLabel> damageLabels = new ArrayList<>();
    private final Vector3d labelPos = new Vector3d(0);
    private int ticksEnabled;
    private int explodeTimer, placeTimer;
    private int confirmedEntityId = Integer.MIN_VALUE;
    private boolean ignoreThmMembers;
    private LivingEntity lastTarget;
    private int lastTargetTimer;

    public CrystalAuraTHM() {
        super(THMAddon.PVP, "crystal-aura-thm", "Custom crystal PvP automation with friend-safety, force-pop and id-predict logic.");
    }

    @Override
    public void onActivate() {
        targets.clear();
        recentlyUsed.clear();
        pendingPredicts.clear();
        ticksEnabled = 0;
        explodeTimer = 0;
        placeTimer = 0;
        confirmedEntityId = Integer.MIN_VALUE;
        lastTarget = null;
        lastTargetTimer = 0;
    }

    @Override
    public String getInfoString() {
        return lastTarget != null && lastTargetTimer > 0 ? lastTarget.getName().getString() : null;
    }

    @EventHandler
    private void onEntityAdded(EntityAddedEvent event) {
        if (event.entity.getId() > confirmedEntityId) confirmedEntityId = event.entity.getId();

        // The crystal we were guessing at has arrived, so the normal explode path will take it this tick.
        // Dropping the guesses here is what keeps the server from logging an invalid-entity attack for
        // every id we never needed to try.
        if (event.entity instanceof EndCrystal) pendingPredicts.clear();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.level == null) return;

        ticksEnabled++;
        if (explodeTimer > 0) explodeTimer--;
        if (placeTimer > 0) placeTimer--;
        if (lastTargetTimer > 0) lastTargetTimer--;
        recentlyUsed.entrySet().removeIf(e -> ticksEnabled - e.getValue() >= existedCooldown.get());
        tickPendingPredicts();
        damageLabels.removeIf(label -> --label.ticks <= 0);

        double health = mc.player.getHealth() + mc.player.getAbsorptionAmount();
        if (health <= pauseHealth.get()) return;
        if (pauseEat.get() && mc.player.isUsingItem()) return;

        buildTargets();
        if (targets.isEmpty()) return;

        boolean exploded = false;
        if (doExplode.get() && explodeTimer <= 0) exploded = tryExplode();
        if (doPlace.get() && placeTimer <= 0 && (sameTick.get() || !exploded)) tryPlace();
    }

    // Targets

    private void buildTargets() {
        targets.clear();
        THMSystem system = THMSystem.get();
        ignoreThmMembers = system != null && system.ignoreThmMembers.get();
        double rangeSq = targetRange.get() * targetRange.get();

        for (var entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity living) || living == mc.player || !living.isAlive()) continue;

            if (living instanceof Player player) {
                if (!players.get()) continue;
                if (player.isSpectator() || player.getAbilities().invulnerable) continue;
                if (Friends.get().isFriend(player)) continue;
                if (ignoreThmMembers && ThmMembers.isThmMember(player)) continue;
            } else if (living instanceof Monster) {
                if (!mobs.get()) continue;
            } else if (living instanceof Animal) {
                if (!animals.get()) continue;
            } else {
                continue;
            }

            if (mc.player.distanceToSqr(living) > rangeSq) continue;
            targets.add(living);
        }
    }

    // Explode

    private boolean tryExplode() {
        List<EndCrystal> crystals = new ArrayList<>();
        for (var entity : mc.level.entitiesForRendering()) {
            if (entity instanceof EndCrystal crystal) crystals.add(crystal);
        }
        crystals.sort((a, b) -> Double.compare(mc.player.distanceToSqr(a), mc.player.distanceToSqr(b)));

        int hits = 0;
        for (EndCrystal crystal : crystals) {
            if (hits >= explodeCPT.get()) break;
            if (mc.player.distanceTo(crystal) > explodeRange.get()) continue;

            BlockPos obsidianPos = crystal.blockPosition().below();
            DamageResult result = computeDamage(crystal.position(), obsidianPos);
            if (!isSafe(result)) continue;

            if (antiWeakness.get() && mc.player.hasEffect(MobEffects.WEAKNESS)) {
                if (!switchToWeaponFor(crystal)) continue;
            }

            if (!performRotation(crystal.position().add(0, 0.5, 0), true)) continue;

            mc.gameMode.attack(mc.player, crystal);
            mc.player.swing(InteractionHand.MAIN_HAND);

            finishRotation();

            recentlyUsed.remove(obsidianPos.above());
            renderAction(obsidianPos.above(), breakSideColor.get(), breakLineColor.get());
            addDamageLabel(obsidianPos.above(), result);

            lastTarget = result.enemyEntity;
            lastTargetTimer = 20;

            hits++;
        }

        if (hits > 0) explodeTimer = explodeCooldown.get();
        return hits > 0;
    }

    private boolean switchToWeaponFor(EndCrystal crystal) {
        if (DamageUtils.getAttackDamage(mc.player, crystal, mc.player.getMainHandItem()) > 0) return true;

        FindItemResult weapon = InvUtils.findInHotbar(stack -> DamageUtils.getAttackDamage(mc.player, crystal, stack) > 0);
        if (!weapon.found()) return false;

        return InvUtils.swap(weapon.slot(), false);
    }

    // Place

    private void tryPlace() {
        FindItemResult hotbarCrystal = InvUtils.findInHotbar(Items.END_CRYSTAL);
        boolean mainHand = mc.player.getMainHandItem().getItem() == Items.END_CRYSTAL;
        boolean offHand = mc.player.getOffhandItem().getItem() == Items.END_CRYSTAL;
        if (!mainHand && !offHand && !(autoSwitch.get() && hotbarCrystal.found())) return;

        if (!sameTick.get() && hasExplodableCrystal()) return;

        int r = (int) Math.ceil(placeRange.get());
        BlockPos base = BlockPos.containing(mc.player.getEyePosition());

        BlockPos bestFloor = null;
        Direction bestDir = null;
        Vec3 bestHitVec = null;
        DamageResult bestResult = null;
        double bestDamage = 0;

        // Support spots are tracked separately, never in the same comparison as real ones. Meteor's aura
        // treats support as a pure last resort — any real obsidian/bedrock spot that passes the checks
        // wins outright, whatever the damage. Scoring them together is what made this overplace.
        BlockPos bestSupportFloor = null;
        double bestSupportDamage = 0;
        FindItemResult supportItem = support.get() ? PlacementUtils.findResistantBlock() : null;

        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                for (int z = -r; z <= r; z++) {
                    BlockPos floor = base.offset(x, y, z);
                    if (floor.distSqr(base) > (double) r * r) continue;
                    if (floor.getY() < mc.level.getMinY() || floor.getY() >= mc.level.getMaxY()) continue;

                    var floorBlock = mc.level.getBlockState(floor).getBlock();
                    boolean needsSupport = floorBlock != Blocks.OBSIDIAN && floorBlock != Blocks.BEDROCK;
                    // A support candidate is scored as if the obsidian were already there — the crystal
                    // itself lands next tick, once the base exists.
                    if (needsSupport && !(supportItem != null && supportItem.found() && BlockUtils.canPlace(floor))) continue;

                    BlockPos above = floor.above();
                    if (!mc.level.getBlockState(above).isAir()) continue;
                    if (oldPlacement.get() && !mc.level.getBlockState(above.above()).isAir()) continue;
                    if (recentlyUsed.containsKey(above)) continue;

                    Vec3 crystalPos = new Vec3(above.getX() + 0.5, above.getY(), above.getZ() + 0.5);
                    if (crystalPos.distanceTo(mc.player.getEyePosition()) > placeRange.get() + 0.6) continue;

                    Direction dir = null;
                    Vec3 hitVec = null;
                    if (needsSupport) {
                        // The base isn't there yet, so there is no face to click for the crystal.
                        // PlacementUtils picks the support block's own click face when we get there.
                        if (PlacementUtils.getPlaceSide(floor) == null) continue;
                        if (raytrace.get() && !RotationUtils.canSeePosition(mc.player.getEyePosition(), Vec3.atCenterOf(floor))) continue;
                    } else {
                        for (Direction d : Direction.values()) {
                            Vec3 face = Vec3.atCenterOf(floor).add(d.getStepX() * 0.5, d.getStepY() * 0.5, d.getStepZ() * 0.5);
                            if (face.distanceTo(mc.player.getEyePosition()) > placeRange.get()) continue;
                            if (raytrace.get() && !RotationUtils.canSeePosition(mc.player.getEyePosition(), face)) continue;
                            dir = d;
                            hitVec = face;
                            break;
                        }
                        if (dir == null) continue;
                    }

                    AABB occupied = new AABB(floor.getX(), floor.getY() + 1, floor.getZ(),
                        floor.getX() + 1, floor.getY() + 1 + (oldPlacement.get() ? 2 : 1), floor.getZ() + 1);
                    if (EntityUtils.intersectsWithEntity(occupied, e -> !(e instanceof Player p && p.isSpectator()))) continue;

                    DamageResult result = computeDamage(crystalPos, floor);
                    if (!isSafe(result)) continue;

                    if (needsSupport) {
                        if (result.enemyDamage > bestSupportDamage) {
                            bestSupportDamage = result.enemyDamage;
                            bestSupportFloor = floor;
                        }
                    } else if (result.enemyDamage > bestDamage) {
                        bestDamage = result.enemyDamage;
                        bestFloor = floor;
                        bestDir = dir;
                        bestHitVec = hitVec;
                        bestResult = result;
                    }
                }
            }
        }

        // Only build a base when it is worth strictly more than the best spot that already exists. With
        // the default gain that means "nothing real found", i.e. Meteor's last-resort rule; lowering it
        // lets support outbid a weak real spot.
        if (bestSupportFloor != null && bestSupportDamage >= bestDamage + supportMinGain.get()) {
            if (PlacementUtils.placeOnSolidSide(bestSupportFloor, supportItem, rotate.get(), rotationPriority.get(), silentSwitch.get())) {
                recentlyUsed.put(bestSupportFloor.above(), ticksEnabled);
                renderAction(bestSupportFloor, placeSideColor.get(), placeLineColor.get());
                // The crystal needs the server to confirm the base first, and without this the next tick
                // just picks another airy spot and stacks a second support block.
                placeTimer = Math.max(placeTimer, supportDelay.get());
            }
            return;
        }

        if (bestFloor == null) return;
        placeCrystal(bestFloor, bestDir, bestHitVec, hotbarCrystal, mainHand, offHand, bestResult);
    }

    private boolean hasExplodableCrystal() {
        for (var entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof EndCrystal crystal)) continue;
            if (mc.player.distanceTo(crystal) > explodeRange.get()) continue;
            if (isSafe(computeDamage(crystal.position(), crystal.blockPosition().below()))) return true;
        }
        return false;
    }

    private void placeCrystal(BlockPos floor, Direction dir, Vec3 hitVec, FindItemResult hotbarCrystal,
                               boolean mainHand, boolean offHand, DamageResult result) {
        // Checked before the swap: bailing out after it would leave the wrong item selected.
        if (!performRotation(hitVec, false)) return;

        boolean switched = false;
        InteractionHand hand;

        if (mainHand) {
            hand = InteractionHand.MAIN_HAND;
        } else if (offHand) {
            hand = InteractionHand.OFF_HAND;
        } else {
            if (!InvUtils.swap(hotbarCrystal.slot(), silentSwitch.get())) return;
            switched = true;
            hand = InteractionHand.MAIN_HAND;
        }

        mc.gameMode.useItemOn(mc.player, hand, new BlockHitResult(hitVec, dir, floor, false));
        mc.player.swing(hand);

        finishRotation();
        if (switched && silentSwitch.get()) InvUtils.swapBack();

        BlockPos above = floor.above();
        recentlyUsed.put(above, ticksEnabled);
        renderAction(above, placeSideColor.get(), placeLineColor.get());
        addDamageLabel(above, result);

        lastTarget = result.enemyEntity;
        lastTargetTimer = 20;
        placeTimer = placeCooldown.get();

        if (idPredict.get()) schedulePredicts();
    }

    // Rotation - module-local helpers, no shared/global RotationUtils state is written.

    /**
     * Returns false when the action must wait — Normal mode steps the yaw toward the target and defers,
     * the way Meteor's own aura does, rather than firing while still pointing the wrong way.
     */
    private boolean performRotation(Vec3 target, boolean breaking) {
        if (!rotate.get() || !rotateOn.get().covers(breaking)) return true;

        if (rotationMode.get() == RotationMode.Normal) {
            double yaw = Rotations.getYaw(target);
            double pitch = Rotations.getPitch(target);
            if (!stepTowards(yaw, pitch)) return false;

            Rotations.rotate(yaw, pitch, rotationPriority.get());
            return true;
        }

        float[] rot = RotationUtils.getRotationsTo(mc.player.getEyePosition(), target);
        float yaw = stepYaw(RotationUtils.getInstance().getServerYaw(), rot[0], yawStepLimit.get().floatValue());

        if (rotationMode.get() == RotationMode.Client) {
            RotationUtils.getInstance().setRotationClient(yaw, rot[1]);
        } else {
            RotationUtils.getInstance().setRotationSilent(yaw, rot[1], rotationPriority.get());
        }
        return true;
    }

    /**
     * True when the target is already within one step. Otherwise it queues a single step at priority
     * -100 (last packet of the tick, same as Meteor) and reports not-ready.
     */
    private boolean stepTowards(double targetYaw, double targetPitch) {
        double limit = yawStepLimit.get();
        if (limit >= 180) return true;

        float serverYaw = RotationUtils.getInstance().getServerYaw();
        float delta = RotationUtils.wrapDegrees((float) targetYaw - serverYaw);
        if (Math.abs(delta) <= limit) return true;

        Rotations.rotate(serverYaw + Math.signum(delta) * limit, targetPitch, -100);
        return false;
    }

    private void finishRotation() {
        if (!rotate.get() || rotationMode.get() != RotationMode.Silent) return;
        RotationUtils.getInstance().setRotationSilentSync();
    }

    private static float stepYaw(float current, float target, float maxStep) {
        if (maxStep >= 180) return target;

        float delta = RotationUtils.wrapDegrees(target - current);
        if (Math.abs(delta) <= maxStep) return target;
        return current + Math.signum(delta) * maxStep;
    }

    // Render

    /** {@code pos} is the block to draw, not the base it sits on. */
    private void renderAction(BlockPos pos, Color side, Color line) {
        if (!render.get()) return;
        RenderUtilsTHM.renderTickingBlock(pos, side, line, shapeMode.get(), 0,
            renderDuration.get(), renderMode.get().fade, renderMode.get().shrink);
    }

    private void addDamageLabel(BlockPos crystalPos, DamageResult result) {
        if (!damageText.get() || result.enemyEntity == null) return;

        // One label per position. Stacking a second one on the same spot just draws the new number
        // straight over the old, which reads as a smeared double-print rather than an update.
        for (DamageLabel existing : damageLabels) {
            if (existing.pos.equals(crystalPos)) {
                existing.text = String.format("%.1f", result.enemyDamage);
                existing.ticks = renderDuration.get();
                return;
            }
        }
        damageLabels.add(new DamageLabel(crystalPos, String.format("%.1f", result.enemyDamage), renderDuration.get()));
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (damageLabels.isEmpty()) return;

        for (DamageLabel label : damageLabels) {
            labelPos.set(label.pos.getX() + 0.5, label.pos.getY() + 0.5, label.pos.getZ() + 0.5);
            if (!NametagUtils.to2D(labelPos, damageTextScale.get())) continue;

            NametagUtils.begin(labelPos);
            TextRenderer.get().begin(1.0, false, true);
            double w = TextRenderer.get().getWidth(label.text) / 2.0;
            TextRenderer.get().render(label.text, -w, 0.0, placeLineColor.get(), true);
            TextRenderer.get().end();
            NametagUtils.end();
        }
    }

    private static final class DamageLabel {
        final BlockPos pos;
        String text;
        int ticks;

        DamageLabel(BlockPos pos, String text, int ticks) {
            this.pos = pos;
            this.text = text;
            this.ticks = ticks;
        }
    }

    /** Solid keeps the box at full size and opacity; the others are Meteor's own fade/shrink animations. */
    public enum CrystalRender {
        Solid(false, false),
        Fade(true, false),
        Shrink(false, true),
        Smooth(true, true);

        final boolean fade, shrink;

        CrystalRender(boolean fade, boolean shrink) {
            this.fade = fade;
            this.shrink = shrink;
        }
    }

    // ID Predict

    private void schedulePredicts() {
        int base = confirmedEntityId + idStartOffset.get();
        for (int i = 0; i < idPackets.get(); i++) {
            pendingPredicts.add(new PendingPredict(base + i * idOffset.get(), ticksEnabled + idDelayTicks.get()));
        }
    }

    private void tickPendingPredicts() {
        if (pendingPredicts.isEmpty()) return;

        pendingPredicts.removeIf(predict -> {
            if (ticksEnabled < predict.fireAtTick()) return false;
            sendPredictedAttack(predict.entityId());
            return true;
        });
    }

    private void sendPredictedAttack(int entityId) {
        if (mc.player == null) return;

        ServerboundInteractPacket packet = ServerboundInteractPacket.createAttackPacket(mc.player, mc.player.isShiftKeyDown());
        ((PlayerInteractEntityC2SPacketAccessor) packet).setEntityId(entityId);
        mc.getConnection().send(packet);
        mc.player.swing(InteractionHand.MAIN_HAND);
    }

    private record PendingPredict(int entityId, int fireAtTick) {}

    // Damage / safety

    private DamageResult computeDamage(Vec3 crystalPos, BlockPos obsidianPos) {
        boolean predict = predictMovement.get();
        float self = DamageUtils.crystalDamage(mc.player, crystalPos, predict, obsidianPos);

        float bestEnemy = 0;
        LivingEntity bestEnemyEntity = null;
        float bestEnemyHealth = 0;
        for (LivingEntity target : targets) {
            float dmg = DamageUtils.crystalDamage(target, crystalPos, predict, obsidianPos);
            if (dmg > bestEnemy) {
                bestEnemy = dmg;
                bestEnemyEntity = target;
                bestEnemyHealth = target.getHealth() + target.getAbsorptionAmount();
            }
        }

        float bestFriend = 0;
        for (Player player : mc.level.players()) {
            if (player == mc.player) continue;
            boolean protect = Friends.get().isFriend(player) || (ignoreThmMembers && ThmMembers.isThmMember(player));
            if (!protect) continue;

            float dmg = DamageUtils.crystalDamage(player, crystalPos, predict, obsidianPos);
            if (dmg > bestFriend) bestFriend = dmg;
        }

        return new DamageResult(bestEnemy, bestEnemyEntity, bestEnemyHealth, bestFriend, self);
    }

    private boolean isSafe(DamageResult result) {
        if (result.enemyEntity == null) return false;

        double selfHealth = mc.player.getHealth() + mc.player.getAbsorptionAmount();
        if (antiSuicide.get() && result.selfDamage >= selfHealth) return false;
        if (result.selfDamage > maxSelfDamage.get()) return false;
        if (result.friendDamage > maxFriendDamage.get()) return false;

        boolean pops = forcePop.get() && result.enemyDamage * forcePopHits.get() >= result.enemyHealth;
        if (pops) return true;

        if (result.enemyDamage < minDamage.get()) return false;
        return result.selfDamage <= 0 || result.enemyDamage / result.selfDamage >= minRatio.get();
    }

    private record DamageResult(float enemyDamage, LivingEntity enemyEntity, float enemyHealth,
                                 float friendDamage, float selfDamage) {}

    public enum RotateOn {
        Place, Break, Both;

        boolean covers(boolean breaking) {
            return this == Both || (breaking ? this == Break : this == Place);
        }
    }

    public enum RotationMode {
        /** Meteor's own rotation manager — shared with every other Meteor module. */
        Normal,
        Silent,
        Client
    }
}
