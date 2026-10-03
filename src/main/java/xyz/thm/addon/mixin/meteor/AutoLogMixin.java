/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.mixin.meteor;

import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.combat.AutoLog;
import meteordevelopment.meteorclient.systems.modules.misc.AutoReconnect;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.thm.addon.modules.HighwayBuilderTHM;
import xyz.thm.addon.modules.THMHwyMonitor;
import xyz.thm.addon.utils.server.ServerReconnectService;

@Mixin(value = AutoLog.class, remap = false)
public class AutoLogMixin {
    @Unique private Setting<Boolean> thm$disableHwyMonitorReconnect;
    @Unique private Setting<Boolean> thm$disableHighwayBuilder;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void thm$init(CallbackInfo ci) {
        SettingGroup sgThm = ((Module) (Object) this).settings.createGroup("THM");

        thm$disableHwyMonitorReconnect = sgThm.add(new BoolSetting.Builder()
            .name("disable-hwymonitor-reconnect")
            .description("Disables THM Hwy Monitor and Meteor AutoReconnect when Auto Log fires.")
            .defaultValue(true)
            .build()
        );

        thm$disableHighwayBuilder = sgThm.add(new BoolSetting.Builder()
            .name("disable-highwaybuilder")
            .description("Lets THM Highway Builder own Auto Log disconnects, so its stats show.")
            .defaultValue(true)
            .build()
        );
    }

    @Inject(method = "disconnect(Lnet/minecraft/network/chat/Component;)V", at = @At("HEAD"), cancellable = true)
    private void thm$onDisconnect(Component reason, CallbackInfo ci) {
        String reasonText = thm$reasonText(reason);

        if (thm$isEnabled(thm$disableHwyMonitorReconnect)) {
            thm$prepareHwyMonitorTerminalLogout(reasonText);
            thm$forceAutoReconnectOff();
        }

        if (!thm$isEnabled(thm$disableHighwayBuilder)) return;

        HighwayBuilderTHM builder = Modules.get().get(HighwayBuilderTHM.class);
        if (builder == null || !builder.isActive()) return;

        if (builder.hardFailFromAutoLog(reason)) ci.cancel();
    }

    @Unique
    private boolean thm$isEnabled(Setting<Boolean> setting) {
        return setting != null && setting.get();
    }

    @Unique
    private String thm$reasonText(Component reason) {
        String text = reason == null ? null : reason.getString();
        return text == null || text.isBlank() ? "unknown" : text;
    }

    @Unique
    private void thm$prepareHwyMonitorTerminalLogout(String reason) {
        try {
            THMHwyMonitor monitor = Modules.get().get(THMHwyMonitor.class);
            if (monitor != null) monitor.prepareForAutoLogTerminalLogout(reason);
            else THMHwyMonitor.signalNonRestartHardFailFromHighwayBuilder();
        } catch (NoClassDefFoundError | ExceptionInInitializerError ignored) {
            // Baritone-dependent monitor not available.
        }

        ServerReconnectService.getInstance().disarmReconnect("AutoLog terminal logout: " + reason);
    }

    @Unique
    private void thm$forceAutoReconnectOff() {
        AutoReconnect autoReconnect = Modules.get().get(AutoReconnect.class);
        if (autoReconnect != null && autoReconnect.isActive()) autoReconnect.toggle();
    }
}
