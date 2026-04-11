package xyz.thm.addon.mixin.meteor;

import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.misc.Notifier;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.thm.addon.utils.THMUtils;

@Mixin(Notifier.class)
public abstract class NotifierMixin {
    @Unique private Setting<Boolean> thm$desktopNotifications;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void thm$init(CallbackInfo ci) {
        SettingGroup sgDesktop = ((Module) (Object) this).settings.createGroup("Desktop");
        thm$desktopNotifications = sgDesktop.add(new BoolSetting.Builder()
            .name("desktop-notifications")
            .description("Send notifier events as desktop notifications.")
            .defaultValue(false)
            .build()
        );
    }

    @Redirect(method = {"onEntityAdded", "onEntityRemoved", "onReceivePacket", "onTick"}, at = @At(value = "INVOKE", target = "Lmeteordevelopment/meteorclient/utils/player/ChatUtils;sendMsg(ILnet/minecraft/util/Formatting;Ljava/lang/String;[Ljava/lang/Object;)V"))
    private void thm$redirectChatSend(int id, Formatting color, String message, Object... args) {
        ChatUtils.sendMsg(id, color, message, args);
        thm$notifyDesktop(String.format(message, args));
    }

    @Redirect(method = "onTick", at = @At(value = "INVOKE", target = "Lmeteordevelopment/meteorclient/utils/player/ChatUtils;sendMsg(Lnet/minecraft/text/Text;)V"))
    private void thm$redirectChatSendText(Text message) {
        ChatUtils.sendMsg(message);
        thm$notifyDesktop(message);
    }

    @Redirect(method = "onTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;sendMessage(Lnet/minecraft/text/Text;Z)V"))
    private void thm$redirectSendMessage(ClientPlayerEntity player, Text message, boolean actionBar) {
        player.sendMessage(message, actionBar);
        thm$notifyDesktop(message);
    }

    @Redirect(method = {"onEntityAdded", "onEntityRemoved"}, at = @At(value = "INVOKE", target = "Lmeteordevelopment/meteorclient/systems/modules/misc/Notifier;info(Lnet/minecraft/text/Text;)V"))
    private void thm$redirectInfoText(Notifier self, Text message) {
        ((Module) (Object) self).info(message);
        thm$notifyDesktop(message);
    }

    @Redirect(method = "onEntityRemoved", at = @At(value = "INVOKE", target = "Lmeteordevelopment/meteorclient/systems/modules/misc/Notifier;info(Ljava/lang/String;[Ljava/lang/Object;)V"))
    private void thm$redirectInfoString(Notifier self, String message, Object... args) {
        ((Module) (Object) self).info(message, args);
        thm$notifyDesktop(String.format(message, args));
    }

    @Unique
    private void thm$notifyDesktop(Text message) {
        if (message == null) return;
        thm$notifyDesktop(message.getString());
    }

    @Unique
    private void thm$notifyDesktop(String message) {
        if (thm$desktopNotifications == null || !thm$desktopNotifications.get()) return;
        String clean = thm$cleanMessage(message);
        if (!clean.isBlank()) THMUtils.Notify("Notifier", clean);
    }

    @Unique
    private String thm$cleanMessage(String message) {
        if (message == null) return "";
        return message
            .replace("(highlight)", "")
            .replace("(default)", "")
            .replaceAll("§.", "")
            .trim();
    }
}
