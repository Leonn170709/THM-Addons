/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package xyz.thm.addon.modules;

import meteordevelopment.meteorclient.events.entity.EntityAddedEvent;
import meteordevelopment.meteorclient.events.entity.EntityRemovedEvent;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.entity.DamageUtils;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ServerboundAttackPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import xyz.thm.addon.THMAddon;
import xyz.thm.addon.mixin.accessor.ClientPlayerInteractionManagerTHMAccessor;
import xyz.thm.addon.settings.DescribedOption;
import xyz.thm.addon.system.THMSystem;
import xyz.thm.addon.utils.InventoryManager;
import xyz.thm.addon.utils.RenderUtilsTHM;
import xyz.thm.addon.utils.ThmMembers;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Java/Meteor port of Lambda's CrystalAura at e8eb261c9d605cc2312ea05063d0cb74d005ce46. */
public class CrystalAura extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgPlace = settings.createGroup("Placement");
    private final SettingGroup sgBreak = settings.createGroup("Exploding");
    private final SettingGroup sgPrediction = settings.createGroup("Prediction");
    private final SettingGroup sgTarget = settings.createGroup("Targeting");
    private final SettingGroup sgRender = settings.createGroup("Rendering");
    private final Setting<Boolean> rotate = sgGeneral.add(new BoolSetting.Builder()
        .name("rotate").description("Rotate before crystal actions.").defaultValue(true).build());

    private final Setting<UpdateMode> updateMode = sgGeneral.add(new EnumSetting.Builder<UpdateMode>()
        .name("update-mode").description("When to evaluate crystal opportunities.").defaultValue(UpdateMode.Ticked).onChanged(mode -> updateScheduler()).build());

    private final Setting<Integer> updateDelay = sgGeneral.add(new IntSetting.Builder()
        .name("update-delay").description("Milliseconds between asynchronous evaluations.").defaultValue(25).min(5).sliderRange(5, 200).visible(() -> updateMode.get() == UpdateMode.Async).build());

    private final Setting<Integer> maxUpdatesPerFrame = sgGeneral.add(new IntSetting.Builder()
        .name("max-updates-per-frame").description("Limit queued evaluations each frame.").defaultValue(5).min(1).max(20).sliderRange(1, 20).visible(() -> updateMode.get() == UpdateMode.Async).build());

    private final Setting<Boolean> debug = sgGeneral.add(new BoolSetting.Builder()
        .name("debug").description("Show confirmed crystal removals per second.").defaultValue(false).build());

    private final Setting<Integer> rotationPriority = sgGeneral.add(new IntSetting.Builder()
        .name("rotation-priority").description("Priority for crystal rotations.").defaultValue(50).min(0).sliderRange(0, 100).visible(rotate::get).build());

    private final Setting<Double> placeRange = sgPlace.add(new DoubleSetting.Builder()
        .name("place-range").description("Maximum crystal placement distance.").defaultValue(4.6).min(1).max(7).sliderRange(1, 7).build());

    private final Setting<Integer> placeDelay = sgPlace.add(new IntSetting.Builder()
        .name("place-delay").description("Milliseconds between placement attempts.").defaultValue(50).min(0).sliderRange(0, 1000).build());

    private final Setting<Boolean> swap = sgPlace.add(new BoolSetting.Builder()
        .name("swap").description("Swap to crystals when needed.").defaultValue(true).build());

    private final Setting<SwapHand> swapHand = sgPlace.add(new EnumSetting.Builder<SwapHand>()
        .name("swap-hand").description("Preferred hand for crystal placement.").defaultValue(SwapHand.MainHand).visible(swap::get).build());

    private final Setting<Boolean> silentSwap = sgPlace.add(new BoolSetting.Builder()
        .name("silent-swap").description("Restore the previous slot after placing.").defaultValue(true).visible(() -> swap.get() && swapHand.get() == SwapHand.MainHand).build());

    private final Setting<Priority> priorityMode = sgPlace.add(new EnumSetting.Builder<Priority>()
        .name("crystal-priority").description("How to score crystal opportunities.").defaultValue(Priority.Damage).build());

    private final Setting<Double> minDamageAdvantage = sgPlace.add(new DoubleSetting.Builder()
        .name("min-damage-advantage").description("Minimum target damage minus self damage.").defaultValue(4.0).min(1).sliderRange(1, 10).visible(() -> priorityMode.get() == Priority.Advantage).build());

    private final Setting<Double> minTargetDamage = sgPlace.add(new DoubleSetting.Builder()
        .name("min-target-damage").description("Minimum damage to the target.").defaultValue(8.0).min(0).sliderRange(0, 20).build());

    private final Setting<Double> maxSelfDamage = sgPlace.add(new DoubleSetting.Builder()
        .name("max-self-damage").description("Maximum damage to yourself.").defaultValue(8.0).min(0).sliderRange(0, 36).build());

    private final Setting<Double> minPlaceHealth = sgPlace.add(new DoubleSetting.Builder()
        .name("min-place-health").description("Health remaining after crystal damage.").defaultValue(5.0).min(0).sliderRange(0, 36).build());

    private final Setting<Boolean> preventDeath = sgPlace.add(new BoolSetting.Builder()
        .name("prevent-death").description("Reject lethal crystal damage.").defaultValue(true).build());

    private final Setting<Boolean> oldPlace = sgPlace.add(new BoolSetting.Builder()
        .name("1.12-placement").description("Require two air blocks above the base.").defaultValue(false).build());

    private final Setting<Boolean> support = sgPlace.add(new BoolSetting.Builder()
        .name("support").description("Place obsidian when no usable crystal base exists.").defaultValue(false).build());

    private final Setting<Integer> supportDelay = sgPlace.add(new IntSetting.Builder()
        .name("support-delay").description("Ticks before placing a crystal on new support.").defaultValue(1).min(0).sliderRange(0, 20).visible(support::get).build());

    private final Setting<Double> explodeRange = sgBreak.add(new DoubleSetting.Builder()
        .name("explode-range").description("Maximum crystal attack distance.").defaultValue(3.0).min(1).max(7).sliderRange(1, 7).build());

    private final Setting<Integer> explodeDelay = sgBreak.add(new IntSetting.Builder()
        .name("explode-delay").description("Milliseconds between crystal attacks.").defaultValue(10).min(0).sliderRange(0, 1000).build());

    private final Setting<PredictionMode> prediction = sgPrediction.add(new EnumSetting.Builder<PredictionMode>()
        .name("prediction").description("When to predict unconfirmed crystal IDs.").defaultValue(PredictionMode.None).build());

    private final Setting<Integer> packetPredictions = sgPrediction.add(new IntSetting.Builder()
        .name("packet-predictions").description("Predicted attacks following a crystal spawn.").defaultValue(1).min(0).max(20).sliderRange(0, 20).visible(() -> prediction.get().onPacket).build());

    private final Setting<Boolean> explodeOnPacket = sgPrediction.add(new BoolSetting.Builder()
        .name("explode-on-packet").description("Attack a safe crystal as it spawns.").defaultValue(false).visible(() -> prediction.get() != PredictionMode.None).build());

    private final Setting<Boolean> placePostPause = sgPrediction.add(new BoolSetting.Builder()
        .name("place-post-pause").description("Restart placement delay after a crystal spawns.").defaultValue(false).visible(() -> prediction.get() != PredictionMode.None).build());

    private final Setting<Boolean> postPacketPlace = sgPrediction.add(new BoolSetting.Builder()
        .name("post-packet-place").description("Allow immediate placement after a crystal spawns.").defaultValue(true).visible(() -> prediction.get() == PredictionMode.Tick && !placePostPause.get()).build());

    private final Setting<Integer> placePredictions = sgPrediction.add(new IntSetting.Builder()
        .name("place-predictions").description("Predicted attacks after placing a crystal.").defaultValue(4).min(1).max(20).sliderRange(1, 20).visible(() -> prediction.get().onPlace).build());

    private final Setting<Integer> packetLifetime = sgPrediction.add(new IntSetting.Builder()
        .name("packet-lifetime").description("Milliseconds before the last entity ID expires.").defaultValue(500).min(50).sliderRange(50, 1000).visible(() -> prediction.get().onPlace).build());

    private final Setting<Double> targetRange = sgTarget.add(new DoubleSetting.Builder()
        .name("targeting-range").description("Maximum target distance.").defaultValue(10.0).min(1).max(16).sliderRange(1, 16).build());

    private final Setting<Set<EntityType<?>>> entityTypes = sgTarget.add(new EntityTypeListSetting.Builder()
        .name("targets").description("Entity types to attack.")
        .defaultValue(BuiltInRegistries.ENTITY_TYPE.stream().filter(type -> type == EntityTypes.PLAYER || type.getCategory() == MobCategory.MONSTER).collect(Collectors.toSet())).build());

    private final Setting<TargetPriority> targetPriority = sgTarget.add(new EnumSetting.Builder<TargetPriority>()
        .name("target-priority").description("How to select the current target.").defaultValue(TargetPriority.Distance).build());

    private final Setting<Integer> fov = sgTarget.add(new IntSetting.Builder()
        .name("fov-limit").description("Maximum angle from your crosshair.").defaultValue(180).min(5).max(180).sliderRange(5, 180).visible(() -> targetPriority.get() == TargetPriority.Fov).build());

    private final Setting<Boolean> targetNamed = sgTarget.add(new BoolSetting.Builder()
        .name("target-named-entities").description("Target named non-player entities.").defaultValue(false).build());

    private final Setting<Boolean> targetTamed = sgTarget.add(new BoolSetting.Builder()
        .name("target-tamed-entities").description("Target tamed entities.").defaultValue(false).build());

    private final Setting<Boolean> targetOwned = sgTarget.add(new BoolSetting.Builder()
        .name("owned").description("Include pets owned by you.").defaultValue(false).visible(targetTamed::get).build());

    private final Setting<Boolean> render = sgRender.add(new BoolSetting.Builder()
        .name("rendering").description("Draw recent crystal placements.").defaultValue(true).build());

    private final Setting<Boolean> renderPlacements = sgRender.add(new BoolSetting.Builder()
        .name("placement-rendering").description("Draw the last placement base.").defaultValue(true).visible(render::get).build());

    private final Setting<SettingColor> primaryColorLine = sgRender.add(new ColorSetting.Builder()
        .name("primary-outline").description("Color at the top of the placement box.").defaultValue(new SettingColor(THMAddon.THMColor.getPacked())).visible(render::get).build());

    private final Setting<SettingColor> secondaryColorLine = sgRender.add(new ColorSetting.Builder()
        .name("secondary-outline").description("Color at the bottom of the placement box.").defaultValue(new SettingColor(THMAddon.THMColor.getPacked())).visible(render::get).build());

    private final Setting<SettingColor> primaryColor = sgRender.add(new ColorSetting.Builder()
        .name("primary-fill").description("Color at the top of the placement box.").defaultValue(new SettingColor(THMAddon.THMSideColor.getPacked())).visible(render::get).build());

    private final Setting<SettingColor> secondaryColor = sgRender.add(new ColorSetting.Builder()
        .name("secondary-fill").description("Color at the bottom of the placement box.").defaultValue(new SettingColor(THMAddon.THMSideColor.getPacked())).visible(render::get).build());

    private final Map<BlockPos, Opportunity> blueprint = new LinkedHashMap<>();
    private final ArrayDeque<Long> removals = new ArrayDeque<>();
    private final AtomicInteger frameBudget = new AtomicInteger();
    private ScheduledExecutorService scheduler;
    private volatile long generation;
    private LivingEntity currentTarget;
    private Opportunity activeOpportunity;
    private BlockPos lastHit, lastPlace;
    private long lastPlaceMs, lastExplodeMs, lastSpawnMs, lastUpdateMs, lastDebugMs;
    private long supportRetryAfterMs;
    private int supportTicks;
    private int lastEntityId;
    private boolean waitingForCrystal, safeToPlaceInstantly, actionPending;

    public CrystalAura() {
        super(THMAddon.PVP, "thmcrystal-aura", "Crystal placement and attack automation.");
    }

    @Override
    public void onActivate() {
        reset();
        for (Entity entity : mc.level.entitiesForRendering()) lastEntityId = Math.max(lastEntityId, entity.getId());
        updateScheduler();
    }

    @Override
    public void onDeactivate() {
        stopScheduler();
        reset();
    }

    private void reset() {
        blueprint.clear();
        removals.clear();
        currentTarget = null;
        activeOpportunity = null;
        lastHit = lastPlace = null;
        lastPlaceMs = lastExplodeMs = lastSpawnMs = lastUpdateMs = 0;
        lastEntityId = 0;
        supportTicks = 0;
        supportRetryAfterMs = 0;
        waitingForCrystal = safeToPlaceInstantly = actionPending = false;
    }

    private void stopScheduler() {
        generation++;
        if (scheduler != null) scheduler.shutdownNow();
        scheduler = null;
        frameBudget.set(0);
    }

    private void updateScheduler() {
        stopScheduler();
        if (!isActive() || updateMode.get() != UpdateMode.Async) return;
        long epoch = generation;
        frameBudget.set(maxUpdatesPerFrame.get());
        scheduler = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "THM Crystal Aura");
            thread.setDaemon(true);
            return thread;
        });
        scheduler.scheduleAtFixedRate(() -> {
            // Only enqueue bounded work; world access stays on the client thread.
            if (frameBudget.getAndUpdate(value -> Math.max(0, value - 1)) == 0) return;
            mc.execute(() -> {
                if (generation == epoch && isActive() && updateMode.get() == UpdateMode.Async) tickAura();
            });
        }, 1, 1, TimeUnit.MILLISECONDS);
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        stopScheduler();
        reset();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.level == null) return;
        if (supportTicks > 0) supportTicks--;
        if (updateMode.get() == UpdateMode.Async && scheduler == null) updateScheduler();
        if (updateMode.get() == UpdateMode.Ticked) tickAura();
        long now = System.currentTimeMillis();
        while (!removals.isEmpty() && now - removals.peekFirst() >= 3000) removals.removeFirst();
        if (debug.get() && now - lastDebugMs >= 1000) {
            info("Crystals/s: %.1f confirmed removals", removals.size() / 3.0);
            lastDebugMs = now;
        }
    }

    @EventHandler
    private void onSpawn(EntityAddedEvent event) {
        lastEntityId = Math.max(lastEntityId, event.entity.getId());
        lastSpawnMs = System.currentTimeMillis();
        if (!(event.entity instanceof EndCrystal crystal) || !canAct()) return;
        BlockPos base = baseOf(crystal);
        Opportunity opportunity = blueprint.get(base);
        if (opportunity == null) return;
        opportunity.crystal = crystal;
        if (prediction.get() == PredictionMode.None || !safeDamage(opportunity.position())) return;
        if (opportunity.priority < minDamageAdvantage.get() && lastPlace != null && base.distSqr(lastPlace) > 2.5) return;
        if (explodeOnPacket.get()) attackCrystal(opportunity);
        if (prediction.get().onPacket) predictAttacks(base, packetPredictions.get());
        if (placePostPause.get()) lastPlaceMs = lastSpawnMs;
        if (postPacketPlace.get() && prediction.get() == PredictionMode.Tick && !placePostPause.get()) safeToPlaceInstantly = true;
    }

    @EventHandler
    private void onRemoval(EntityRemovedEvent event) {
        if (!(event.entity instanceof EndCrystal crystal)) return;
        Opportunity opportunity = blueprint.get(baseOf(crystal));
        if (opportunity != null && opportunity.crystal == crystal) opportunity.crystal = null;
        if (removals.size() == 10000) removals.removeFirst();
        removals.addLast(System.currentTimeMillis());
    }

    private boolean canAct() {
        if (mc.player == null || mc.level == null || mc.gameMode == null || mc.getConnection() == null) return false;
        InventoryManager inventory = InventoryManager.getInstance();
        return !mc.player.isUsingItem() && !inventory.isEating()
            && inventory.getCurrentPriority() <= InventoryManager.Priority.NORMAL;
    }

    private void tickAura() {
        if (!canAct() || actionPending) return;
        currentTarget = selectTarget();
        if (currentTarget == null) {
            blueprint.clear();
            activeOpportunity = null;
            return;
        }
        long now = System.currentTimeMillis();
        if (updateMode.get() == UpdateMode.Ticked || now - lastUpdateMs >= updateDelay.get()) {
            updateBlueprint();
            lastUpdateMs = now;
        }
        if (activeOpportunity != null) interact(activeOpportunity);
    }

    private LivingEntity selectTarget() {
        List<LivingEntity> targets = new ArrayList<>();
        THMSystem system = THMSystem.get();
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity living) || living == mc.player || !living.isAlive()
                || !entityTypes.get().contains(entity.getType()) || mc.player.distanceToSqr(entity) > targetRange.get() * targetRange.get()) continue;
            if (living instanceof Player player) {
                if (player.isSpectator() || player.getAbilities().invulnerable || Friends.get().isFriend(player)) continue;
                if (system != null && system.ignoreThmMembers.get() && ThmMembers.isThmMember(player)) continue;
            } else if (living.hasCustomName() && !targetNamed.get()) continue;
            if (living instanceof TamableAnimal pet && pet.isTame()
                && (!targetTamed.get() || (!targetOwned.get() && pet.isOwnedBy(mc.player)))) continue;
            if (targetPriority.get() == TargetPriority.Fov && angle(living) > fov.get()) continue;
            targets.add(living);
        }
        return targets.stream().min(Comparator.comparingDouble(entity -> switch (targetPriority.get()) {
            case Distance -> mc.player.distanceToSqr(entity);
            case Health -> entity.getHealth() + entity.getAbsorptionAmount();
            case Fov -> angle(entity);
        })).orElse(null);
    }

    private double angle(Entity entity) {
        double yaw = Math.abs(net.minecraft.util.Mth.wrapDegrees(Rotations.getYaw(entity.position()) - mc.player.getYRot()));
        double pitch = Math.abs(Rotations.getPitch(entity.position()) - mc.player.getXRot());
        return Math.hypot(yaw, pitch);
    }

    private void updateBlueprint() {
        blueprint.clear();
        boolean canSupport = support.get() && InvUtils.findInHotbar(Items.OBSIDIAN).found()
            && InvUtils.find(Items.END_CRYSTAL).found();
        double range = Math.max(placeRange.get(), explodeRange.get()) + 1;
        List<EndCrystal> crystals = new ArrayList<>();
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof EndCrystal crystal) || !crystal.isAlive()) continue;
            crystals.add(crystal);
            if (crystal.position().distanceTo(mc.player.getEyePosition()) > explodeRange.get()) continue;
            Opportunity opportunity = opportunity(baseOf(crystal), crystal.position(), crystal, null, false);
            if (opportunity != null) blueprint.put(opportunity.base, opportunity);
        }
        int radius = (int) Math.ceil(range);
        BlockPos center = mc.player.blockPosition().above();
        for (BlockPos mutable : BlockPos.betweenClosed(center.offset(-radius, -radius, -radius), center.offset(radius, radius, radius))) {
            BlockPos base = mutable.immutable();
            if (blueprint.containsKey(base) || mc.player.getEyePosition().distanceTo(crystalPosition(base)) > placeRange.get()) continue;
            var state = mc.level.getBlockState(base);
            boolean needsSupport = !state.is(Blocks.OBSIDIAN) && !state.is(Blocks.BEDROCK);
            if ((needsSupport && (!canSupport || !BlockUtils.canPlace(base) || !mc.level.getWorldBorder().isWithinBounds(base)))
                || !mc.level.getBlockState(base.above()).isAir()
                || (oldPlace.get() && !mc.level.getBlockState(base.above(2)).isAir())) continue;
            AABB box = crystalBox(base);
            if (!mc.level.getEntities((Entity) null, box, entity -> !(entity instanceof EndCrystal) && !entity.isSpectator()).isEmpty()) continue;
            EndCrystal crystal = null, blocker = null;
            for (EndCrystal candidate : crystals) {
                if (baseOf(candidate).equals(base)) crystal = candidate;
                else if (candidate.getBoundingBox().intersects(box)) blocker = candidate;
            }
            Opportunity opportunity = opportunity(base, crystalPosition(base), crystal, blocker, needsSupport);
            if (opportunity != null) blueprint.put(base, opportunity);
        }
        activeOpportunity = bestOpportunity(blueprint.values(), lastHit);
    }

    private Opportunity opportunity(BlockPos base, Vec3 position, EndCrystal crystal, EndCrystal blocker, boolean support) {
        double target = damage(currentTarget, position, support ? base : null), self = damage(mc.player, position, support ? base : null);
        if (!allowedDamage(target, self, mc.player.getHealth() + mc.player.getAbsorptionAmount(), minTargetDamage.get(),
            maxSelfDamage.get(), minPlaceHealth.get(), preventDeath.get(), priorityMode.get(), minDamageAdvantage.get())) return null;
        return new Opportunity(base, target, self, priorityMode.get().score(target, self), crystal, blocker, support);
    }

    static boolean allowedDamage(double target, double self, double health, double minTarget, double maxSelf,
                                 double minHealth, boolean preventDeath, Priority priority, double advantage) {
        return Double.isFinite(target) && Double.isFinite(self) && target >= minTarget && self <= maxSelf
            && health - self > minHealth && (!preventDeath || health - self > 0)
            && (priority != Priority.Advantage || priority.score(target, self) >= advantage);
    }

    private boolean safeDamage(Vec3 position) {
        return currentTarget != null && currentTarget.isAlive() && opportunity(BlockPos.containing(position).below(), position, null, null, false) != null;
    }

    private double damage(LivingEntity entity, Vec3 position, BlockPos supportBase) {
        return DamageUtils.crystalDamage(entity, entity.position(), entity.getBoundingBox(), position, (context, pos) -> {
            var state = pos.equals(supportBase) ? Blocks.OBSIDIAN.defaultBlockState() : mc.level.getBlockState(pos);
            return state.getCollisionShape(mc.level, pos).clip(context.start(), context.end(), pos);
        });
    }

    static Opportunity bestOpportunity(Collection<Opportunity> opportunities, BlockPos lastHit) {
        Opportunity best = opportunities.stream().filter(op -> !op.blocked() || op.base.equals(lastHit))
            .max(Comparator.comparing((Opportunity op) -> !op.support).thenComparingDouble(op -> op.priority)).orElse(null);
        if (best != null) return best;
        Opportunity blocked = opportunities.stream().filter(Opportunity::blocked)
            .max(Comparator.comparingDouble(op -> op.priority)).orElse(null);
        return blocked == null ? null : opportunities.stream().filter(op -> op.crystal != null && op.crystal == blocked.blocker)
            .findFirst().orElse(null);
    }

    private void interact(Opportunity best) {
        if (best.blocked()) {
            Opportunity blocker = blueprint.get(baseOf(best.blocker));
            if (blocker != null) attackCrystal(blocker);
            return;
        }
        if (best.crystal != null && best.crystal.isAlive()) {
            attackCrystal(best);
            return;
        }
        for (Opportunity other : blueprint.values()) {
            if (other.crystal != null && other.crystal.isAlive() && other.crystal.getBoundingBox().intersects(crystalBox(best.base))) {
                attackCrystal(other);
                return;
            }
        }
        if (best.support) {
            placeSupport(best);
            return;
        }
        if (prediction.get().onTick && waitingForCrystal && System.currentTimeMillis() - lastExplodeMs >= explodeDelay.get()) {
            runRotated(best, () -> {
                predictAttacks(best.base, 1);
                waitingForCrystal = false;
                lastHit = best.base;
                lastExplodeMs = System.currentTimeMillis();
            });
        } else place(best);
    }

    private void runRotated(Opportunity opportunity, Runnable action) {
        runRotated(opportunity, hitPosition(opportunity.base, placeSide(opportunity.base)), action);
    }

    private void runRotated(Opportunity opportunity, Vec3 hit, Runnable action) {
        long epoch = generation;
        Runnable checked = () -> {
            actionPending = false;
            if (!isActive() || generation != epoch || !canAct() || currentTarget == null || !currentTarget.isAlive()
                || opportunity(opportunity.base, opportunity.position(), null, null, opportunity.support) == null) return;
            action.run();
        };
        if (!rotate.get()) checked.run();
        else {
            actionPending = true;
            Rotations.rotate(Rotations.getYaw(hit), Rotations.getPitch(hit), rotationPriority.get(), checked);
        }
    }

    private void attackCrystal(Opportunity opportunity) {
        EndCrystal crystal = opportunity.crystal;
        if (crystal == null || !crystal.isAlive() || mc.player.getEyePosition().distanceTo(crystal.position()) > explodeRange.get()
            || System.currentTimeMillis() - lastExplodeMs < explodeDelay.get()) return;
        runRotated(opportunity, () -> {
            if (!crystal.isAlive() || mc.player.getEyePosition().distanceTo(crystal.position()) > explodeRange.get()) return;
            attackId(crystal.getId());
            lastEntityId = Math.max(lastEntityId, crystal.getId());
            lastHit = opportunity.base;
            lastExplodeMs = System.currentTimeMillis();
        });
    }

    private void predictAttacks(BlockPos base, int count) {
        if (lastEntityId <= 0 || !safeDamage(crystalPosition(base))
            || mc.player.getEyePosition().distanceTo(crystalPosition(base)) > explodeRange.get()) return;
        for (int i = 0; i < count; i++) {
            if (lastEntityId == Integer.MAX_VALUE) break;
            attackId(++lastEntityId);
        }
        lastHit = base;
    }

    private void attackId(int id) {
        Entity known = mc.level.getEntity(id);
        if (known != null && !(known instanceof EndCrystal)) return;
        mc.getConnection().send(new ServerboundAttackPacket(id));
        mc.player.swing(InteractionHand.MAIN_HAND);
    }

    private void place(Opportunity opportunity) {
        long now = System.currentTimeMillis();
        if (supportTicks > 0 || (now - lastPlaceMs < placeDelay.get()
            && !(postPacketPlace.get() && prediction.get() == PredictionMode.Tick && safeToPlaceInstantly && !placePostPause.get()))) return;
        runRotated(opportunity, () -> placeCrystal(opportunity));
    }

    private void placeCrystal(Opportunity opportunity) {
        if (supportTicks > 0 || !validPlacement(opportunity.base)) return;
        InteractionHand hand = crystalHand();
        if (hand == null) return;
        placeInternal(opportunity, hand);
        safeToPlaceInstantly = false;
        lastHit = null;
        if (prediction.get().onPlace && System.currentTimeMillis() - lastSpawnMs <= packetLifetime.get()) {
            int previous = lastEntityId;
            predictAttacks(opportunity.base, placePredictions.get());
            lastEntityId = previous == Integer.MAX_VALUE ? previous : previous + 1;
        }
        lastPlaceMs = System.currentTimeMillis();
    }

    private void placeSupport(Opportunity opportunity) {
        long now = System.currentTimeMillis();
        if (!support.get() || now < supportRetryAfterMs || now - lastPlaceMs < placeDelay.get()) return;
        Direction side = BlockUtils.getPlaceSide(opportunity.base);
        BlockPos neighbour = side == null ? opportunity.base : opportunity.base.relative(side);
        Vec3 hit = side == null ? Vec3.atCenterOf(opportunity.base) : hitPosition(opportunity.base, side);
        Direction face = side == null ? Direction.DOWN : side.getOpposite();
        if (mc.player.getEyePosition().distanceTo(hit) > placeRange.get()) return;
        runRotated(opportunity, hit, () -> {
            var item = InvUtils.findInHotbar(Items.OBSIDIAN);
            if (!support.get() || !item.found() || !BlockUtils.canPlace(opportunity.base)
                || !mc.level.getWorldBorder().isWithinBounds(opportunity.base)
                || !mc.level.getBlockState(opportunity.base.above()).isAir()
                || (oldPlace.get() && !mc.level.getBlockState(opportunity.base.above(2)).isAir())
                || !mc.level.getEntities((Entity) null, crystalBox(opportunity.base), entity -> !entity.isSpectator()).isEmpty()
                || mc.player.getEyePosition().distanceTo(hit) > placeRange.get()) return;
            InteractionHand hand = item.isOffhand() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
            InventoryManager inventory = InventoryManager.getInstance();
            int previousSlot = inventory.getServerSlot();
            int previousClientSlot = mc.player.getInventory().getSelectedSlot();
            if (hand == InteractionHand.MAIN_HAND && !swap.get() && !mc.player.getMainHandItem().is(Items.OBSIDIAN)) return;
            boolean switched = hand == InteractionHand.MAIN_HAND;
            if (switched) {
                // Vanilla placement prediction reads the client-held item.
                inventory.setClientSlot(item.slot());
                if (inventory.getServerSlot() != item.slot()) inventory.setSlotForced(item.slot());
            }
            try {
                BlockUtils.interact(new BlockHitResult(hit, face, neighbour, false), hand, true);
            } finally {
                if (switched && silentSwap.get()) {
                    inventory.setClientSlot(previousClientSlot);
                    if (inventory.getServerSlot() != previousSlot) inventory.setSlotForced(previousSlot);
                }
            }
            supportTicks = supportDelay.get();
            supportRetryAfterMs = System.currentTimeMillis() + 500;
            lastPlace = opportunity.base;
            lastPlaceMs = System.currentTimeMillis();
            if (supportTicks == 0) placeCrystal(opportunity);
        });
    }

    private boolean validPlacement(BlockPos base) {
        var state = mc.level.getBlockState(base);
        if ((!state.is(Blocks.OBSIDIAN) && !state.is(Blocks.BEDROCK)) || !mc.level.getBlockState(base.above()).isAir()
            || (oldPlace.get() && !mc.level.getBlockState(base.above(2)).isAir())
            || mc.player.getEyePosition().distanceTo(crystalPosition(base)) > placeRange.get()) return false;
        return mc.level.getEntities((Entity) null, crystalBox(base), entity -> !entity.isSpectator()).isEmpty();
    }

    private InteractionHand crystalHand() {
        InteractionHand preferred = swapHand.get() == SwapHand.MainHand ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
        if (mc.player.getItemInHand(preferred).is(Items.END_CRYSTAL)) return preferred;
        if (!swap.get()) return null;
        if (mc.player.getOffhandItem().is(Items.END_CRYSTAL)) return InteractionHand.OFF_HAND;
        if (preferred == InteractionHand.MAIN_HAND) {
            if (mc.player.getMainHandItem().is(Items.END_CRYSTAL)) return preferred;
            var item = InvUtils.find(Items.END_CRYSTAL);
            if (!item.found()) return null;
            if (item.slot() >= 9) {
                if (mc.player.containerMenu != mc.player.inventoryMenu) return null;
                InvUtils.move().from(item.slot()).toHotbar(mc.player.getInventory().getSelectedSlot());
            }
            return InteractionHand.MAIN_HAND;
        }
        var item = InvUtils.find(Items.END_CRYSTAL);
        if (!item.found() || mc.player.containerMenu != mc.player.inventoryMenu) return null;
        InvUtils.move().from(item.slot()).toOffhand();
        return mc.player.getOffhandItem().is(Items.END_CRYSTAL) ? InteractionHand.OFF_HAND : null;
    }

    private void placeInternal(Opportunity opportunity, InteractionHand hand) {
        InventoryManager inventory = InventoryManager.getInstance();
        int previousSlot = inventory.getServerSlot();
        boolean swapped = false;
        if (hand == InteractionHand.MAIN_HAND) {
            var crystals = InvUtils.findInHotbar(Items.END_CRYSTAL);
            if (!crystals.found() || crystals.isOffhand()) return;
            int slot = mc.player.getMainHandItem().is(Items.END_CRYSTAL)
                ? mc.player.getInventory().getSelectedSlot() : crystals.slot();
            if (silentSwap.get()) {
                if (slot != previousSlot) { inventory.setSlotForced(slot); swapped = true; }
            } else {
                inventory.setClientSlot(slot);
                if (inventory.getServerSlot() != slot) inventory.setSlotForced(slot);
            }
        }
        try {
            Direction side = placeSide(opportunity.base);
            BlockHitResult hit = new BlockHitResult(crystalPosition(opportunity.base), side, opportunity.base, false);
            ((ClientPlayerInteractionManagerTHMAccessor) mc.gameMode).thm$sendSequencedPacket(mc.level,
                sequence -> new ServerboundUseItemOnPacket(hand, hit, sequence));
            mc.player.swing(hand);
            lastPlace = opportunity.base;
            lastPlaceMs = System.currentTimeMillis();
            waitingForCrystal = true;
        } finally {
            if (swapped) inventory.setSlotForced(previousSlot);
        }
    }

    private Direction placeSide(BlockPos base) {
        Vec3 eyes = mc.player.getEyePosition();
        if (eyes.y > base.getY() + 1) return Direction.UP;
        Direction best = Direction.UP;
        double distance = Double.MAX_VALUE;
        for (Direction side : Direction.values()) {
            double outside = side.getAxis().choose(eyes.x, eyes.y, eyes.z) - side.getAxis().choose(base.getX(), base.getY(), base.getZ());
            if ((side.getAxisDirection() == Direction.AxisDirection.POSITIVE && outside <= 1)
                || (side.getAxisDirection() == Direction.AxisDirection.NEGATIVE && outside >= 0)) continue;
            double candidate = eyes.distanceToSqr(hitPosition(base, side));
            if (candidate < distance) { best = side; distance = candidate; }
        }
        return best;
    }

    private static Vec3 hitPosition(BlockPos base, Direction side) {
        return Vec3.atCenterOf(base).add(side.getStepX() * 0.5, side.getStepY() * 0.5, side.getStepZ() * 0.5);
    }

    static Vec3 crystalPosition(BlockPos base) { return new Vec3(base.getX() + 0.5, base.getY() + 1, base.getZ() + 0.5); }
    static AABB crystalBox(BlockPos base) { return new AABB(base.getX(), base.getY() + 1, base.getZ(), base.getX() + 1, base.getY() + 3, base.getZ() + 1); }
    private static BlockPos baseOf(EndCrystal crystal) { return BlockPos.containing(crystal.position().add(0, -0.5, 0)); }

    @EventHandler
    private void onRender(Render3DEvent event) {
        frameBudget.set(maxUpdatesPerFrame.get());
        if (!render.get() || !renderPlacements.get() || lastPlace == null || System.currentTimeMillis() - lastPlaceMs > 100) return;
        RenderUtilsTHM.renderBlockGradient(event, lastPlace, secondaryColor.get(), primaryColor.get(),
            secondaryColorLine.get(), primaryColorLine.get());
    }

    @Override public String getInfoString() { return currentTarget == null ? null : currentTarget.getName().getString(); }

    static final class Opportunity {
        final BlockPos base;
        final double target, self, priority;
        EndCrystal crystal;
        final EndCrystal blocker;
        final boolean support;
        Opportunity(BlockPos base, double target, double self, double priority, EndCrystal crystal, EndCrystal blocker, boolean support) {
            this.base = base.immutable(); this.target = target; this.self = self; this.priority = priority;
            this.crystal = crystal; this.blocker = blocker;
            this.support = support;
        }
        boolean blocked() { return crystal == null && blocker != null && blocker.isAlive(); }
        Vec3 position() { return crystal == null ? crystalPosition(base) : crystal.position(); }
    }

    public enum UpdateMode implements DescribedOption {
        Async, Ticked;
        @Override public String description() { return this == Async ? "Evaluate between ticks with bounded frame work." : "Evaluate once per client tick."; }
    }
    public enum SwapHand implements DescribedOption {
        MainHand, OffHand;
        @Override public String description() { return this == MainHand ? "Prefer crystals in your main hand." : "Prefer crystals in your offhand."; }
    }
    public enum Priority implements DescribedOption {
        Damage, Advantage;
        double score(double target, double self) { return this == Damage ? target : target - self; }
        @Override public String description() { return this == Damage ? "Prefer the highest target damage." : "Prefer target damage minus self damage."; }
    }
    public enum TargetPriority implements DescribedOption {
        Distance, Health, Fov;
        @Override public String description() { return switch (this) {
            case Distance -> "Target the nearest entity.";
            case Health -> "Target the entity with the lowest health.";
            case Fov -> "Target the entity closest to your crosshair.";
        }; }
    }
    public enum PredictionMode implements DescribedOption {
        None(false, false, false), Packet(true, false, false), Deferred(false, true, false), Tick(false, false, true), Mixed(true, true, false);
        final boolean onPacket, onPlace, onTick;
        PredictionMode(boolean onPacket, boolean onPlace, boolean onTick) { this.onPacket = onPacket; this.onPlace = onPlace; this.onTick = onTick; }
        @Override public String description() { return switch (this) {
            case None -> "Attack confirmed crystals only.";
            case Packet -> "Predict crystal IDs after spawn updates.";
            case Deferred -> "Predict crystal IDs after placement.";
            case Tick -> "Predict the next crystal when the attack delay expires.";
            case Mixed -> "Predict crystal IDs after spawns and placements.";
        }; }
    }
}
