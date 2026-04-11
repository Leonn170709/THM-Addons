package xyz.thm.addon.mixin.meteor;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.entity.player.ClipAtLedgeEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.movement.Scaffold;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.thm.addon.utils.PacketPlaceUtils;

import static meteordevelopment.meteorclient.MeteorClient.mc;

@Mixin(value = Scaffold.class, remap = false)
public abstract class ScaffoldMixin {
    @Shadow @Final private SettingGroup sgGeneral;

    @Unique private Setting<Boolean> thm$packetPlace;
    @Unique private Setting<Boolean> thm$packetAirPlace;
    @Unique private Setting<Integer> thm$packetRotateTicks;
    @Unique private Setting<Boolean> thm$safeMove;

    @Unique
    private final Object thm$safeMoveListener = new Object() {
        @EventHandler
        public void onClipAtLedge(ClipAtLedgeEvent event) {
            if (thm$safeMove == null || !thm$safeMove.get()) return;
            if (!((Module)(Object)ScaffoldMixin.this).isActive()) return;
            if (mc.player == null || mc.world == null) return;
            event.setClip(true);
        }
    };

    @Inject(method = "<init>", at = @At("TAIL"))
    private void thm$init(CallbackInfo ci) {
        thm$packetPlace = sgGeneral.add(new BoolSetting.Builder()
            .name("packet-place")
            .description("Places blocks using packets instead of normal interactions.")
            .defaultValue(false)
            .build()
        );
        thm$packetAirPlace = sgGeneral.add(new BoolSetting.Builder()
            .name("packet-air-place")
            .description("Allows packet placement without a supporting block side.")
            .defaultValue(true)
            .visible(thm$packetPlace::get)
            .build()
        );
        thm$packetRotateTicks = sgGeneral.add(new IntSetting.Builder()
            .name("packet-rotate-ticks")
            .description("Rotation ticks for packet place. Lower is faster on high ping.")
            .defaultValue(0)
            .min(0)
            .sliderRange(0, 50)
            .visible(thm$packetPlace::get)
            .build()
        );
        thm$safeMove = sgGeneral.add(new BoolSetting.Builder()
            .name("safe-move")
            .description("Prevents you from walking off edges while scaffolding.")
            .defaultValue(false)
            .build()
        );

        MeteorClient.EVENT_BUS.subscribe(thm$safeMoveListener);
    }

    @Redirect(method = "place", at = @At(value = "INVOKE", target = "Lmeteordevelopment/meteorclient/utils/world/BlockUtils;place(Lnet/minecraft/util/math/BlockPos;Lmeteordevelopment/meteorclient/utils/player/FindItemResult;ZIZZ)Z"))
    private boolean thm$redirectPlace(BlockPos pos, FindItemResult item, boolean rotate, int rotationPriority, boolean swingHand, boolean checkEntities) {
        if (thm$packetPlace != null && thm$packetPlace.get()) {
            int rotateTicks = thm$packetRotateTicks != null ? thm$packetRotateTicks.get() : rotationPriority;
            boolean airPlace = thm$packetAirPlace == null || thm$packetAirPlace.get();
            boolean placed = PacketPlaceUtils.placeBlockPacket(pos, item, rotate, rotateTicks, airPlace);
            if (placed && swingHand && mc.player != null) {
                Hand hand = item.isOffhand() ? Hand.OFF_HAND : Hand.MAIN_HAND;
                mc.player.swingHand(hand, true);
            }
            return placed;
        }
        return BlockUtils.place(pos, item, rotate, rotationPriority, swingHand, checkEntities);
    }
}
