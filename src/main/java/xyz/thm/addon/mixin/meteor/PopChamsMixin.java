/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.mixin.meteor;

import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.render.PopChams;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = PopChams.class, remap = false)
public abstract class PopChamsMixin {
    @Unique private Setting<Boolean> thm$renderSkin;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void thm$init(CallbackInfo ci) {
        SettingGroup sgThm = ((Module) (Object) this).settings.createGroup("THM");
        sgThm.add(new BoolSetting.Builder()
            .name("capture-limb-animation")
            .description("Keep arm and leg motion from the pop moment.")
            .defaultValue(true)
            .build()
        );
        thm$renderSkin = sgThm.add(new BoolSetting.Builder()
            .name("render-skin")
            .description("Render the popping player's skin instead of the wireframe.")
            .defaultValue(true)
            .build()
        );
        sgThm.add(new BoolSetting.Builder()
            .name("skin-through-walls")
            .description("Show the skin through solid blocks.")
            .defaultValue(false)
            .visible(thm$renderSkin::get)
            .build()
        );
        sgThm.add(new DoubleSetting.Builder()
            .name("transparency")
            .description("How see-through the skin is.")
            .defaultValue(0)
            .sliderRange(0, 1)
            .min(0)
            .max(1)
            .visible(thm$renderSkin::get)
            .build()
        );
    }
}
