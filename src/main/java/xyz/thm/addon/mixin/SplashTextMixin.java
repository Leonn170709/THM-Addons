/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.resources.SplashManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;

@Mixin(SplashManager.class)
public class SplashTextMixin {
    @Shadow @Mutable
    private List<Component> splashes;

    @Inject(method = "apply(Ljava/util/List;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V", at = @At("TAIL"))
    private void addThmSplashes(List<Component> prepared, ResourceManager manager, ProfilerFiller profiler, CallbackInfo ci) {
        List<Component> mutable = new ArrayList<>(this.splashes);
        mutable.add(Component.literal("THM highway go brrr!"));
        mutable.add(Component.literal("Place the blocks, mine the blocks!"));
        mutable.add(Component.literal("Highways last forever!!"));
        mutable.add(Component.literal("6b6t at home!"));
        mutable.add(Component.literal("Running THM Addons!"));
        mutable.add(Component.literal("Highway is life!"));
        mutable.add(Component.literal("Builidng Highways!!!"));
        mutable.add(Component.literal("Mine or be mined!"));
        mutable.add(Component.literal("Be fair or be square"));
        mutable.add(Component.literal("https://discord.gg/thm"));
        this.splashes = mutable;
    }
}
