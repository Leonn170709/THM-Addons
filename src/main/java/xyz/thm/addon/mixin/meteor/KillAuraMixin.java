/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.mixin.meteor;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.combat.CrystalAura;
import meteordevelopment.meteorclient.systems.modules.combat.KillAura;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.AxeItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.MaceItem;
import net.minecraft.item.TridentItem;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.apache.commons.lang3.mutable.MutableDouble;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xyz.thm.addon.mixin.accessor.PlayerInventoryAccessor;
import xyz.thm.addon.modules.ModuleManager;
import xyz.thm.addon.system.THMSystem;
import xyz.thm.addon.utils.InventoryManager;
import xyz.thm.addon.utils.InventoryManager.SwapMode;
import xyz.thm.addon.utils.RenderUtilsTHM;
import xyz.thm.addon.utils.RenderUtilsTHM.RenderMode;
import xyz.thm.addon.utils.RotationUtils;
import xyz.thm.addon.utils.ThmMembers;

import java.util.List;
@Mixin(value = KillAura.class, remap = false)
public abstract class KillAuraMixin extends Module {
    public KillAuraMixin(Category category, String name, String description) {
        super(category, name, description);
    }
    @Shadow @Final private SettingGroup sgGeneral;
    @Shadow @Final private Setting<Boolean> autoSwitch;
    @Shadow @Final private Setting<KillAura.RotationMode> rotation;
    @Shadow @Final private Setting<Double> range;
    @Shadow @Final private Setting<Boolean> tpsSync;
    @Shadow @Final private Setting<Boolean> customDelay;
    @Shadow @Final private Setting<Integer> hitDelay;
    @Shadow @Final private Setting<Integer> switchDelay;
    @Shadow private List<Entity> targets;
    @Shadow public boolean attacking;
    @Shadow private int hitTimer;
    @Shadow private int switchTimer;
    @Unique private SettingGroup thm$sgRotation;
    @Unique private Setting<Boolean> thm$grimRotate;
    @Unique private Setting<Boolean> thm$silentRotate;
    @Unique private Setting<Boolean> thm$yawStep;
    @Unique private Setting<Integer> thm$yawStepLimit;
    @Unique private Setting<RotationUtils.HitVector> thm$hitVector;
    @Unique private SettingGroup thm$sgSwap;
    @Unique private Setting<SwapMode> thm$swapMode;
    @Unique private SettingGroup thm$sgRender;
    @Unique private Setting<Boolean> thm$render;
    @Unique private Setting<RenderMode> thm$renderMode;
    @Unique private Setting<ShapeMode> thm$shapeMode;
    @Unique private Setting<SettingColor> thm$sideColor;
    @Unique private Setting<SettingColor> thm$lineColor;
    @Unique private RotationUtils thm$rotationManager;
    @Unique private InventoryManager thm$inventoryManager;
    @Unique private long thm$lastAttackTime = 0;
    @Unique private boolean thm$rotated = false;
    @Unique private float[] thm$silentRotations = null;
    @Unique private long thm$switchTimer = 0;
    @Unique private long thm$autoSwapTimer = 0;
    @Unique private boolean thm$silentSwapped = false;
    @Inject(method = "<init>", at = @At("TAIL"))
    private void onInit(CallbackInfo ci) {
        thm$rotationManager = RotationUtils.getInstance();
        thm$inventoryManager = InventoryManager.getInstance();
        thm$sgRotation = settings.createGroup("Grim Rotations");
        thm$grimRotate = thm$sgRotation.add(new BoolSetting.Builder()
            .name("grim-rotate")
            .description("Use PVP rotation system")
            .defaultValue(false)
            .build()
        );
        thm$hitVector = thm$sgRotation.add(new EnumSetting.Builder<RotationUtils.HitVector>()
            .name("hit-vector")
            .description("Which part of the entity to aim for")
            .defaultValue(RotationUtils.HitVector.TORSO)
            .visible(thm$grimRotate::get)
            .build()
        );
        thm$silentRotate = thm$sgRotation.add(new BoolSetting.Builder()
            .name("silent-rotate")
            .description("Rotates silently server-side")
            .defaultValue(false)
            .visible(thm$grimRotate::get)
            .build()
        );
        thm$yawStep = thm$sgRotation.add(new BoolSetting.Builder()
            .name("yaw-step")
            .description("Limits rotation speed to avoid flags")
            .defaultValue(false)
            .visible(thm$grimRotate::get)
            .build()
        );
        thm$yawStepLimit = thm$sgRotation.add(new IntSetting.Builder()
            .name("yaw-step-limit")
            .description("Maximum yaw rotation per tick")
            .defaultValue(180)
            .min(1)
            .max(180)
            .sliderRange(1, 180)
            .visible(() -> thm$grimRotate.get() && thm$yawStep.get())
            .build()
        );
        thm$sgSwap = settings.createGroup("Grim Swap");
        thm$swapMode = thm$sgSwap.add(new EnumSetting.Builder<SwapMode>()
            .name("swap-mode")
            .description("How to swap to weapon")
            .defaultValue(SwapMode.Normal)
            .build()
        );
        thm$sgRender = settings.createGroup("Grim Render");
        thm$render = thm$sgRender.add(new BoolSetting.Builder()
            .name("render")
            .description("Renders a box over the target entity")
            .defaultValue(true)
            .build()
        );
        thm$renderMode = thm$sgRender.add(new EnumSetting.Builder<RenderMode>()
            .name("render-mode")
            .description("How the target box is rendered")
            .defaultValue(RenderMode.Fade)
            .visible(thm$render::get)
            .build()
        );
        thm$shapeMode = thm$sgRender.add(new EnumSetting.Builder<ShapeMode>()
            .name("shape-mode")
            .description("How the shapes are rendered")
            .defaultValue(ShapeMode.Both)
            .visible(thm$render::get)
            .build()
        );
        thm$sideColor = thm$sgRender.add(new ColorSetting.Builder()
            .name("side-color")
            .description("The side color of the target box")
            .defaultValue(new SettingColor(255, 0, 0, 50))
            .visible(thm$render::get)
            .build()
        );
        thm$lineColor = thm$sgRender.add(new ColorSetting.Builder()
            .name("line-color")
            .description("The line color of the target box")
            .defaultValue(new SettingColor(255, 0, 0, 255))
            .visible(thm$render::get)
            .build()
        );
    }
    @Inject(method = "onActivate", at = @At("TAIL"))
    private void onActivateInject(CallbackInfo ci) {
        thm$lastAttackTime = 0;
        thm$rotated = false;
        thm$silentRotations = null;
        thm$switchTimer = System.currentTimeMillis();
        thm$autoSwapTimer = System.currentTimeMillis();
        thm$silentSwapped = false;
        ModuleManager.traceKillAuraMixinEvent("onActivate", "Kill Aura activated.");
    }
    @Inject(method = "onDeactivate", at = @At("TAIL"))
    private void onDeactivateInject(CallbackInfo ci) {
        thm$silentRotations = null;
        thm$rotated = false;
        if (thm$silentSwapped && thm$swapMode.get() == SwapMode.Silent) {
            thm$inventoryManager.syncToClient();
            thm$silentSwapped = false;
        }
        ModuleManager.traceKillAuraMixinEvent("onDeactivate", "Kill Aura deactivated.");
    }

    @Inject(method = "onTick", at = @At("HEAD"), cancellable = true)
    private void onTickGuardDisconnectedState(CallbackInfo ci) {
        if (mc.player != null && mc.world != null) return;

        attacking = false;
        thm$silentRotations = null;
        thm$rotated = false;
        ModuleManager.traceKillAuraMixinEvent("disconnected-guard", "Cleared attacking/rotation state because player or world was null.");
        ci.cancel();
    }

    @Unique
    @EventHandler(priority = EventPriority.HIGHEST)
    private void onPreTickHighest(TickEvent.Pre event) {
        if (!isActive() || mc.player == null) return;
        if (!thm$grimRotate.get()) return;
        if (rotation.get() != KillAura.RotationMode.None) {
            ModuleManager.traceKillAuraMixinEvent("force-rotation-none", "Forced rotation mode to None for grim rotate path.");
            rotation.set(KillAura.RotationMode.None);
        }
        if (thm$swapMode.get() == SwapMode.Silent) {
            if (autoSwitch.get()) {
                ModuleManager.traceKillAuraMixinEvent("force-autoswitch-off", "Forced autoSwitch off for silent swap path.");
                autoSwitch.set(false);
            }
        }
        if (targets == null || targets.isEmpty() || !attacking) {
            thm$silentRotations = null;
            return;
        }
        Entity target = targets.get(0);
        if (target == null) return;
        if (!thm$switchTimerPassed()) {
            thm$silentRotations = null;
            return;
        }
        if ((mc.player.isUsingItem() && mc.player.getActiveHand() == Hand.MAIN_HAND) ||
            mc.options.attackKey.isPressed() || thm$isHotbarKeysPressed()) {
            thm$autoSwapTimer = System.currentTimeMillis();
        }
        int slot = thm$getBestWeaponSlot();
        if (slot != -1) {
            switch (thm$swapMode.get()) {
                case Normal -> {
                    if (!thm$isHoldingWeapon() && thm$autoSwapTimerPassed()) {
                        ((PlayerInventoryAccessor) mc.player.getInventory()).setSelectedSlot(slot);
                    }
                }
                case Silent -> {
                    int currentServerSlot = thm$inventoryManager.getServerSlot();
                    if (currentServerSlot != slot) {
                        thm$inventoryManager.setSlot(slot);
                        thm$silentSwapped = true;
                    }
                }
            }
        }
        if (!thm$isHoldingWeapon() && thm$swapMode.get() != SwapMode.Silent) {
            return;
        }
        if (slot == -1 && thm$swapMode.get() == SwapMode.Silent) {
            return;
        }
        thm$handlePVPRotation(target);
        if (!thm$rotated) {
            if (thm$silentSwapped) {
                thm$inventoryManager.syncToClient();
                thm$silentSwapped = false;
            }
            return;
        }
        Vec3d eyepos = mc.player.getEyePos();
        if (!thm$isInAttackRange(target, eyepos)) {
            if (thm$silentSwapped) {
                thm$inventoryManager.syncToClient();
                thm$silentSwapped = false;
            }
            return;
        }
    }
    @Inject(method = "attack", at = @At("HEAD"), cancellable = true)
    private void onAttack(Entity target, CallbackInfo ci) {
        if (!thm$grimRotate.get()) return;
        ci.cancel();
        thm$handleAttackDelay(target);
    }

    @Inject(method = "entityCheck", at = @At("HEAD"), cancellable = true)
    private void thm$ignoreThmMembers(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (!(entity instanceof PlayerEntity player)) return;
        THMSystem system = THMSystem.get();
        if (system != null && system.ignoreThmMembers.get() && ThmMembers.isThmMember(player)) {
            cir.setReturnValue(false);
        }
    }
    @Unique
    @EventHandler(priority = EventPriority.LOWEST)
    private void onPostTickLowest(TickEvent.Post event) {
        if (!isActive() || mc.player == null) return;
        if (thm$silentSwapped && !attacking) {
            thm$inventoryManager.syncToClient();
            thm$silentSwapped = false;
        }
    }
    @Unique
    @EventHandler(priority = EventPriority.HIGHEST)
    private void onSendPacketHighest(PacketEvent.Send event) {
        if (!isActive() || mc.player == null) return;
        if (event.packet instanceof UpdateSelectedSlotC2SPacket) {
            thm$switchTimer = System.currentTimeMillis();
        }
    }
    @Unique
    private boolean thm$switchTimerPassed() {
        return System.currentTimeMillis() - thm$switchTimer >= 0;
    }
    @Unique
    private boolean thm$autoSwapTimerPassed() {
        return System.currentTimeMillis() - thm$autoSwapTimer >= 500;
    }
    @Unique
    private boolean thm$isHotbarKeysPressed() {
        for (int i = 0; i < 9; i++) {
            if (mc.options.hotbarKeys[i].isPressed()) {
                return true;
            }
        }
        return false;
    }
    @Unique
    private void thm$handlePVPRotation(Entity target) {
        float[] rotation = RotationUtils.getRotationsTo(mc.player.getEyePos(),
            thm$getAttackRotateVec(target));
        if (!thm$silentRotate.get() && thm$yawStep.get()) {
            float serverYaw = thm$rotationManager.getWrappedYaw();
            float diff = serverYaw - rotation[0];
            float diff1 = Math.abs(diff);
            if (diff1 > 180.0f) {
                diff += diff > 0.0f ? -360.0f : 360.0f;
            }
            int dir = diff > 0.0f ? -1 : 1;
            float deltaYaw = dir * thm$yawStepLimit.get();
            float yaw;
            if (diff1 > thm$yawStepLimit.get()) {
                yaw = serverYaw + deltaYaw;
                thm$rotated = false;
            } else {
                yaw = rotation[0];
                thm$rotated = true;
            }
            rotation[0] = yaw;
        } else {
            thm$rotated = true;
        }
        if (thm$silentRotate.get()) {
            thm$silentRotations = rotation;
        } else {
            mc.player.setYaw(rotation[0]);
            mc.player.setPitch(MathHelper.clamp(rotation[1], -90.0f, 90.0f));
        }
    }
    @Unique
    private Vec3d thm$getAttackRotateVec(Entity entity) {
        Vec3d feetPos = entity.getEntityPos();
        return switch (thm$hitVector.get()) {
            case FEET -> feetPos;
            case TORSO -> feetPos.add(0.0, entity.getHeight() / 2.0f, 0.0);
            case EYES -> entity.getEyePos();
            case CLOSEST -> {
                Vec3d eyePos = mc.player.getEyePos();
                Vec3d torsoPos = feetPos.add(0.0, entity.getHeight() / 2.0f, 0.0);
                Vec3d eyesPos = entity.getEyePos();
                double feetDist = eyePos.squaredDistanceTo(feetPos);
                double torsoDist = eyePos.squaredDistanceTo(torsoPos);
                double eyesDist = eyePos.squaredDistanceTo(eyesPos);
                if (feetDist <= torsoDist && feetDist <= eyesDist) {
                    yield feetPos;
                } else if (torsoDist <= eyesDist) {
                    yield torsoPos;
                } else {
                    yield eyesPos;
                }
            }
        };
    }
    @Unique
    private boolean thm$isInAttackRange(Entity target, Vec3d eyepos) {
        Vec3d targetEntityPos = thm$getAttackRotateVec(target);
        return eyepos.distanceTo(targetEntityPos) <= range.get();
    }
    @Unique
    private boolean thm$isHoldingWeapon() {
        ItemStack stack = mc.player.getMainHandStack();
        String itemName = stack.getItem().toString().toLowerCase();
        return itemName.contains("sword") ||
            stack.getItem() instanceof AxeItem ||
            stack.getItem() instanceof TridentItem ||
            stack.getItem() instanceof MaceItem;
    }
    @Unique
    private int thm$getBestWeaponSlot() {
        float bestDamage = 0.0f;
        int bestSlot = -1;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isEmpty()) continue;
            MutableDouble damageMutable = new MutableDouble(0.0);
            AttributeModifiersComponent attributeModifiers = stack.get(DataComponentTypes.ATTRIBUTE_MODIFIERS);
            if (attributeModifiers != null) {
                attributeModifiers.applyModifiers(EquipmentSlot.MAINHAND, (entry, modifier) -> {
                    if (entry == EntityAttributes.ATTACK_DAMAGE) {
                        damageMutable.add(modifier.value());
                    }
                });
            }
            float damage = (float) damageMutable.doubleValue();
            if (damage > bestDamage) {
                bestDamage = damage;
                bestSlot = i;
            }
        }
        return bestSlot;
    }
    @Unique
    private void thm$handleAttackDelay(Entity target) {
        if (!thm$rotated) {
            return;
        }
        if (thm$lastAttackTime == 0) {
            if (thm$attackTarget(target)) {
                thm$lastAttackTime = System.currentTimeMillis();
            }
            return;
        }
        int weaponSlot = thm$getBestWeaponSlot();
        int slotToUse = weaponSlot == -1 ? ((PlayerInventoryAccessor) mc.player.getInventory()).getSelectedSlot() : weaponSlot;
        ItemStack weapon = mc.player.getInventory().getStack(slotToUse);
        MutableDouble attackSpeedAttr = new MutableDouble(
            mc.player.getAttributeBaseValue(EntityAttributes.ATTACK_SPEED));
        AttributeModifiersComponent attributeModifiers = weapon.get(DataComponentTypes.ATTRIBUTE_MODIFIERS);
        if (attributeModifiers != null) {
            attributeModifiers.applyModifiers(EquipmentSlot.MAINHAND, (entry, modifier) -> {
                if (entry.equals(EntityAttributes.ATTACK_SPEED)) {
                    attackSpeedAttr.add(modifier.value());
                }
            });
        }
        @SuppressWarnings("deprecation") double attackCooldownTicks = 1.0 / attackSpeedAttr.getValue() * 20.0;
        float ticks = 0.0f;
        float currentTime = (System.currentTimeMillis() - thm$lastAttackTime) + (ticks * 50.0f);
        if ((currentTime / 50.0f) >= attackCooldownTicks && thm$attackTarget(target)) {
            thm$lastAttackTime = System.currentTimeMillis();
        }
    }
    @Unique
    private boolean thm$attackTarget(Entity entity) {
        int weaponSlot = thm$getBestWeaponSlot();
        if (thm$swapMode.get() == SwapMode.Silent && weaponSlot != -1) {
            int currentServerSlot = thm$inventoryManager.getServerSlot();
            if (currentServerSlot != weaponSlot) {
                thm$inventoryManager.setSlot(weaponSlot);
                thm$silentSwapped = true;
            }
        }
        if (thm$silentRotate.get() && thm$silentRotations != null) {
            thm$rotationManager.setRotationSilent(thm$silentRotations[0], thm$silentRotations[1]);
        }
        PlayerInteractEntityC2SPacket packet = PlayerInteractEntityC2SPacket.attack(entity, mc.player.isSneaking());
        mc.getNetworkHandler().sendPacket(packet);
        mc.player.swingHand(Hand.MAIN_HAND);
        if (thm$silentRotate.get()) {
            thm$rotationManager.setRotationSilentSync();
        }
        if (thm$silentSwapped && thm$swapMode.get() == SwapMode.Silent) {
            thm$inventoryManager.syncToClient();
            thm$silentSwapped = false;
        }
        return true;
    }
    @Unique
    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (!isActive() || mc.player == null || !thm$render.get() || !thm$grimRotate.get()) return;
        CrystalAura crystalAura = Modules.get().get(CrystalAura.class);
        if (crystalAura != null && crystalAura.isActive() && crystalAura.kaTimer > 0) return;
        if (targets == null || targets.isEmpty() || !attacking) return;
        Entity target = targets.get(0);
        if (target == null || !target.isAlive()) return;
        if (!(thm$isHoldingWeapon() || thm$swapMode.get() == SwapMode.Silent)) return;
        RenderMode mode = thm$renderMode.get();
        RenderUtilsTHM.renderEntity(event, target, thm$sideColor.get(), thm$lineColor.get(), thm$shapeMode.get(),
            mode, thm$lastAttackTime, mode == RenderMode.Shrink ? 500 : 1000);
    }
}
