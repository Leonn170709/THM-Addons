/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.mixin.meteor;

import meteordevelopment.meteorclient.systems.modules.Module;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.thm.addon.modules.ModuleManager;

@Mixin(value = Module.class, remap = false)
public abstract class ModuleMixin {
    @Shadow @Final @Mutable public String title;

    @Inject(method = "<init>(Lmeteordevelopment/meteorclient/systems/modules/Category;Ljava/lang/String;Ljava/lang/String;[Ljava/lang/String;)V", at = @At("RETURN"))
    private void thm$addonTitles(CallbackInfo ci) {
        // Titles preserve the existing distinct registry names.
        if (((Module) (Object) this).name.equals("thmcrystal-aura")) title = "THMcrystal aura";
        if (((Module) (Object) this).name.equals("surround-plus")) title = "Surround";
    }

    @Inject(method = "toggle", at = @At("HEAD"))
    private void thm$traceToggle(CallbackInfo ci) {
        ModuleManager.traceModuleMethodInvocation((Module) (Object) this, "toggle");
    }

    @Inject(method = "enable", at = @At("HEAD"))
    private void thm$traceEnable(CallbackInfo ci) {
        ModuleManager.traceModuleMethodInvocation((Module) (Object) this, "enable");
    }

    @Inject(method = "disable", at = @At("HEAD"))
    private void thm$traceDisable(CallbackInfo ci) {
        ModuleManager.traceModuleMethodInvocation((Module) (Object) this, "disable");
    }
}
