/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.mixin.meteor;

import xyz.thm.addon.settings.DescribedOption;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.mixin.ServerboundMovePlayerPacketAccessor;
import meteordevelopment.meteorclient.mixininterface.IServerboundMovePlayerPacket;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.movement.NoFall;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import static meteordevelopment.meteorclient.MeteorClient.mc;

@Mixin(value = NoFall.class, remap = false)
public class NoFallMixin {
    @Unique private Setting<Mode> thm$mode;
    @Unique private Setting<Boolean> thm$resetOnDisable;

    @Redirect(method = "<init>", at = @At(value = "INVOKE", target = "Lmeteordevelopment/meteorclient/settings/SettingGroup;add(Lmeteordevelopment/meteorclient/settings/Setting;)Lmeteordevelopment/meteorclient/settings/Setting;"))
    @SuppressWarnings("unchecked")
    private Setting<?> thm$addSetting(SettingGroup group, Setting<?> setting) {
        if (setting.name.equals("mode")) {
            Setting<NoFall.Mode> vanilla = (Setting<NoFall.Mode>) setting;
            thm$mode = group.add(new EnumSetting.Builder<Mode>()
                .name(setting.name)
                .description(setting.description)
                .defaultValue(Mode.Packet)
                // Packet keeps vanilla placement and pathing behavior for NoGround.
                .onChanged(value -> vanilla.set(value == Mode.NoGround ? NoFall.Mode.Packet : NoFall.Mode.valueOf(value.name())))
                .build());
            thm$resetOnDisable = group.add(new BoolSetting.Builder()
                .name("reset-on-disable")
                .description("Sends reset packets when disabling NoGround.")
                .defaultValue(true)
                .visible(() -> thm$mode.get() == Mode.NoGround)
                .build());
            return vanilla;
        }

        if (setting.name.equals("pause-on-mace")) {
            return group.add(new BoolSetting.Builder()
                .name(setting.name)
                .description(setting.description)
                .defaultValue((Boolean) setting.getDefaultValue())
                .visible(() -> thm$mode.get() != Mode.NoGround)
                .build());
        }

        return group.add(setting);
    }

    @Inject(method = "onSendPacket", at = @At("HEAD"), cancellable = true)
    private void thm$noGround(PacketEvent.Send event, CallbackInfo ci) {
        if (thm$mode.get() != Mode.NoGround) return;
        ci.cancel();

        if (mc.player == null || mc.player.getAbilities().instabuild
            || !(event.packet instanceof ServerboundMovePlayerPacket packet)
            || ((IServerboundMovePlayerPacket) packet).meteor$getTag() == 1337) return;

        ((ServerboundMovePlayerPacketAccessor) packet).meteor$setOnGround(false);
    }

    @Inject(method = "onDeactivate", at = @At("HEAD"))
    private void thm$resetFallDistance(CallbackInfo ci) {
        if (thm$mode.get() != Mode.NoGround || !thm$resetOnDisable.get()
            || mc.player == null || mc.getConnection() == null) return;

        thm$sendResetPacket(0.0000008);
        thm$sendResetPacket(0);
    }

    @Unique
    private void thm$sendResetPacket(double height) {
        ServerboundMovePlayerPacket packet = new ServerboundMovePlayerPacket.Pos(
            mc.player.getX(), mc.player.getY() + height, mc.player.getZ(), false, false);
        ((IServerboundMovePlayerPacket) packet).meteor$setTag(1337);
        mc.player.connection.send(packet);
    }

    @Inject(method = "getInfoString", at = @At("HEAD"), cancellable = true)
    private void thm$infoString(CallbackInfoReturnable<String> cir) {
        if (thm$mode.get() == Mode.NoGround) cir.setReturnValue("NoGround");
    }

    public enum Mode implements DescribedOption {
        Packet,
        NoGround,
        AirPlace,
        Place;

        @Override
        public String description() {
            return switch (this) {
                case Packet -> "Spoof grounded movement to prevent fall damage.";
                case NoGround -> "Always report airborne movement.";
                case AirPlace -> "Air-place a block beneath you.";
                case Place -> "Place the selected landing item beneath you.";
            };
        }
    }
}
