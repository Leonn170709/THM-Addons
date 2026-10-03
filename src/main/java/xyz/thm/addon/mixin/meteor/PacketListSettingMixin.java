/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.mixin.meteor;

import meteordevelopment.meteorclient.settings.PacketListSetting;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import xyz.thm.addon.utils.PacketSettingMigration;

@Mixin(value = PacketListSetting.class, remap = false)
public abstract class PacketListSettingMixin {
    @ModifyVariable(method = "load(Lnet/minecraft/nbt/CompoundTag;)Ljava/util/Set;", at = @At("HEAD"), argsOnly = true)
    private CompoundTag thm$legacyPackets(CompoundTag tag) {
        return PacketSettingMigration.migrate(tag);
    }
}
