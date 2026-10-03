/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.mixin;

import com.mojang.serialization.Codec;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Arrays;
import net.minecraft.client.InactivityFpsLimit;
import net.minecraft.util.StringRepresentable;

/**
 * Adds a third value, "Never", to vanilla's "Reduce FPS when" option — the one Sodium re-exposes on
 * its performance page, so it shows up in both screens. The constant is appended to the enum's
 * $VALUES in the class initializer and CODEC is rebuilt afterwards (it is created from a snapshot of
 * values(), so without the rebuild "never" would fail to parse back out of options.txt).
 *
 * <p>ponytail: the new constant deliberately reuses MINIMIZED's ordinal (0) instead of taking a
 * fresh 2. Vanilla's tooltip factory is a pattern switch over this enum and throws MatchException on
 * an ordinal its switch map doesn't cover, which would crash the vanilla video settings screen; a
 * duplicate ordinal makes it land on the minimized branch instead. Nothing puts this enum in an
 * EnumMap/EnumSet or compares ordinals, so that's the cheaper half of the trade — the alternative is
 * a second mixin into the synthetic GameOptions$5 switch-map holder, whose name moves every version.
 */
@Mixin(InactivityFpsLimit.class)
public class InactivityFpsLimitMixin {
    @Shadow @Final @Mutable private static InactivityFpsLimit[] $VALUES;
    @Shadow @Final @Mutable public static Codec<InactivityFpsLimit> CODEC;

    @Invoker("<init>")
    static InactivityFpsLimit thm$create(String enumName, int ordinal, String name, String translationKey) {
        throw new AssertionError();
    }

    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void thm$addNever(CallbackInfo ci) {
        InactivityFpsLimit never = thm$create("NEVER", 0, "never", "options.inactivityFpsLimit.never");

        InactivityFpsLimit[] values = Arrays.copyOf($VALUES, $VALUES.length + 1);
        values[values.length - 1] = never;
        $VALUES = values;

        CODEC = StringRepresentable.fromEnum(InactivityFpsLimit::values);
    }
}
