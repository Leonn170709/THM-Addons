/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.mixin.meteor;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.combat.AutoWeb;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = AutoWeb.class, remap = false)
public abstract class AutoWebMixin {
    @Shadow private PlayerEntity target;

    @Unique private Setting<Boolean> thm$ignoreNaked;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void thm$init(CallbackInfo ci) {
        SettingGroup sgThm = ((Module) (Object) this).settings.createGroup("THM");
        thm$ignoreNaked = sgThm.add(new BoolSetting.Builder()
            .name("ignore-naked")
            .description("Skips players without armor.")
            .defaultValue(true)
            .build()
        );
    }

    // Injected after target selection, before the webs are pulled from the hotbar.
    @Inject(
        method = "onTick",
        at = @At(
            value = "INVOKE",
            target = "Lmeteordevelopment/meteorclient/utils/player/InvUtils;findInHotbar([Lnet/minecraft/item/Item;)Lmeteordevelopment/meteorclient/utils/player/FindItemResult;"
        ),
        cancellable = true
    )
    private void thm$skipNakedTargets(TickEvent.Pre event, CallbackInfo ci) {
        if (thm$ignoreNaked == null || !thm$ignoreNaked.get()) return;
        if (target != null && target.getArmor() == 0) ci.cancel();
    }
}
