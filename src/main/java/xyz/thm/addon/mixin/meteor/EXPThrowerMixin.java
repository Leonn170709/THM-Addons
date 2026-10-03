/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.mixin.meteor;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.player.EXPThrower;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.player.SlotUtils;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownExperienceBottle;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.thm.addon.utils.InventoryManager;

@Mixin(value = EXPThrower.class, remap = false)
public abstract class EXPThrowerMixin extends Module {
    public EXPThrowerMixin(Category category, String name, String description, String... aliases) {
        super(category, name, description, aliases);
    }

    @Unique private Setting<Boolean> thm$fromInventory;
    @Unique private Setting<Boolean> thm$autoRepair;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void thm$init(CallbackInfo ci) {
        thm$fromInventory = settings.getDefaultGroup().add(new BoolSetting.Builder()
            .name("from-inventory")
            .description("Throws from the inventory when the hotbar has none.")
            .defaultValue(true)
            .build()
        );
        thm$autoRepair = settings.getDefaultGroup().add(new BoolSetting.Builder()
            .name("auto-repair")
            .description("Throws until your Mending armor is repaired, then turns off.")
            .defaultValue(false)
            .build()
        );
    }

    @Inject(method = "onTick", at = @At("HEAD"), cancellable = true)
    private void thm$onTick(TickEvent.Pre event, CallbackInfo ci) {
        if (thm$autoRepair.get()) {
            int missing = thm$missingDurability();
            if (missing <= 0) {
                info("Armor repaired.");
                toggle();
                ci.cancel();
                return;
            }
            // XP already in the air covers it — wait for it to land instead of overthrowing.
            if (missing <= thm$pendingRepair()) {
                ci.cancel();
                return;
            }
        }

        // Hotbar/offhand bottles: Meteor's own throw handles it.
        if (InvUtils.findInHotbar(Items.EXPERIENCE_BOTTLE).found()) return;
        ci.cancel();

        FindItemResult exp = thm$fromInventory.get()
            ? InvUtils.find(stack -> stack.is(Items.EXPERIENCE_BOTTLE), SlotUtils.MAIN_START, SlotUtils.MAIN_END)
            : null;
        if (exp == null || !exp.found()) {
            if (thm$autoRepair.get()) {
                warning("Out of XP bottles.");
                toggle();
            }
            return;
        }

        int slot = exp.slot();
        Rotations.rotate(mc.player.getYRot(), 90, () -> {
            InventoryManager.swapTo(slot, false, true);
            mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
            InventoryManager.swapBack(false);
        });
    }

    @Unique
    private int thm$missingDurability() {
        int missing = 0;
        for (EquipmentSlot slot : EquipmentSlot.VALUES) {
            if (slot.getType() != EquipmentSlot.Type.HUMANOID_ARMOR) continue;
            ItemStack stack = mc.player.getItemBySlot(slot);
            if (stack.isDamaged() && EnchantmentHelper.has(stack, EnchantmentEffectComponents.REPAIR_WITH_XP)) {
                missing += stack.getDamageValue();
            }
        }
        return missing;
    }

    /** Durability the nearby orbs and in-flight bottles will still repair (Mending: 2 per XP, bottle ≈ 7 XP). */
    @Unique
    private int thm$pendingRepair() {
        int pending = 0;
        for (Entity e : mc.level.getEntities(mc.player, mc.player.getBoundingBox().inflate(8))) {
            if (e instanceof ExperienceOrb orb) pending += orb.getValue() * 2;
            else if (e instanceof ThrownExperienceBottle) pending += 14;
        }
        return pending;
    }
}
