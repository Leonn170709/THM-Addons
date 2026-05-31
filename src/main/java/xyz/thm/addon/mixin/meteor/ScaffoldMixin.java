package xyz.thm.addon.mixin.meteor;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.entity.player.ClipAtLedgeEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.movement.Scaffold;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import xyz.thm.addon.utils.RenderUtilsTHM;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.thm.addon.utils.PacketPlaceUtils;

import static meteordevelopment.meteorclient.MeteorClient.mc;

@Mixin(value = Scaffold.class, remap = false)
public abstract class ScaffoldMixin {
    @Shadow @Final private SettingGroup sgGeneral;
    @Shadow @Final private SettingGroup sgRender;
    @Shadow @Final private BlockPos.Mutable bp;

    @Shadow protected abstract boolean place(BlockPos pos);

    @Unique private Setting<Boolean> thm$packetPlace;
    @Unique private Setting<Boolean> thm$packetAirPlace;
    @Unique private Setting<Integer> thm$packetRotateTicks;
    @Unique private Setting<Boolean> thm$safeMove;
    @Unique private Setting<Boolean> thm$keepY;

    // Gap-fill interpolation
    @Unique private Setting<Boolean> thm$interpolate;
    @Unique private Setting<Boolean> thm$interpolateRender;
    @Unique private Setting<ShapeMode> thm$interpShapeMode;
    @Unique private Setting<SettingColor> thm$interpSideColor;
    @Unique private Setting<SettingColor> thm$interpLineColor;

    // Lookahead prediction
    @Unique private Setting<Boolean> thm$lookahead;
    @Unique private Setting<Integer> thm$lookaheadTicks;
    @Unique private Setting<Boolean> thm$lookaheadRender;
    @Unique private Setting<ShapeMode> thm$lookaheadShapeMode;
    @Unique private Setting<SettingColor> thm$lookaheadSideColor;
    @Unique private Setting<SettingColor> thm$lookaheadLineColor;

    @Unique private int thm$lockedY = Integer.MIN_VALUE;
    @Unique private int thm$lastScaffoldTickAge = Integer.MIN_VALUE;
    @Unique private BlockPos thm$lastScaffoldBp = null;

    @Unique private final LongOpenHashSet thm$interpRenderSet = new LongOpenHashSet();
    @Unique private final LongOpenHashSet thm$lookaheadRenderSet = new LongOpenHashSet();

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

    @Unique
    private final Object thm$renderListener = new Object() {
        @EventHandler
        public void onRender3D(Render3DEvent event) {
            if (!((Module)(Object)ScaffoldMixin.this).isActive()) return;

            if (thm$interpolate != null && thm$interpolate.get()
                    && thm$interpolateRender != null && thm$interpolateRender.get()) {
                RenderUtilsTHM.renderBlockSet(event, thm$interpRenderSet,
                    thm$interpSideColor.get(), thm$interpLineColor.get(), thm$interpShapeMode.get());
            }

            if (thm$lookahead != null && thm$lookahead.get()
                    && thm$lookaheadRender != null && thm$lookaheadRender.get()) {
                RenderUtilsTHM.renderBlockSet(event, thm$lookaheadRenderSet,
                    thm$lookaheadSideColor.get(), thm$lookaheadLineColor.get(), thm$lookaheadShapeMode.get());
            }
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
        thm$keepY = sgGeneral.add(new BoolSetting.Builder()
            .name("keep-y")
            .description("Locks scaffold placement to the Y level from activation.")
            .defaultValue(false)
            .build()
        );

        thm$interpolate = sgGeneral.add(new BoolSetting.Builder()
            .name("interpolate")
            .description("Fills gaps when moving faster than 1 block/tick by placing blocks between tick positions.")
            .defaultValue(false)
            .build()
        );
        thm$interpolateRender = sgGeneral.add(new BoolSetting.Builder()
            .name("interpolate-render")
            .description("Renders the blocks filled in by interpolation.")
            .defaultValue(true)
            .visible(thm$interpolate::get)
            .build()
        );

        thm$lookahead = sgGeneral.add(new BoolSetting.Builder()
            .name("lookahead")
            .description("Pre-places blocks ahead of the player based on velocity prediction.")
            .defaultValue(false)
            .build()
        );
        thm$lookaheadTicks = sgGeneral.add(new IntSetting.Builder()
            .name("lookahead-ticks")
            .description("How many ticks ahead to predict and pre-place scaffold blocks.")
            .defaultValue(5)
            .min(1)
            .sliderRange(1, 20)
            .visible(thm$lookahead::get)
            .build()
        );
        thm$lookaheadRender = sgGeneral.add(new BoolSetting.Builder()
            .name("lookahead-render")
            .description("Renders the predicted lookahead block positions.")
            .defaultValue(true)
            .visible(thm$lookahead::get)
            .build()
        );

        thm$interpShapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
            .name("interp-shape-mode")
            .description("Shape mode for interpolation block rendering.")
            .defaultValue(ShapeMode.Both)
            .visible(() -> thm$interpolate.get() && thm$interpolateRender.get())
            .build()
        );
        thm$interpSideColor = sgRender.add(new ColorSetting.Builder()
            .name("interp-side-color")
            .description("Side color for gap-fill interpolation rendering.")
            .defaultValue(new SettingColor(80, 200, 255, 25))
            .visible(() -> thm$interpolate.get() && thm$interpolateRender.get())
            .build()
        );
        thm$interpLineColor = sgRender.add(new ColorSetting.Builder()
            .name("interp-line-color")
            .description("Line color for gap-fill interpolation rendering.")
            .defaultValue(new SettingColor(80, 200, 255, 200))
            .visible(() -> thm$interpolate.get() && thm$interpolateRender.get())
            .build()
        );
        thm$lookaheadShapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
            .name("lookahead-shape-mode")
            .description("Shape mode for lookahead prediction rendering.")
            .defaultValue(ShapeMode.Both)
            .visible(() -> thm$lookahead.get() && thm$lookaheadRender.get())
            .build()
        );
        thm$lookaheadSideColor = sgRender.add(new ColorSetting.Builder()
            .name("lookahead-side-color")
            .description("Side color for lookahead prediction rendering.")
            .defaultValue(new SettingColor(255, 200, 50, 25))
            .visible(() -> thm$lookahead.get() && thm$lookaheadRender.get())
            .build()
        );
        thm$lookaheadLineColor = sgRender.add(new ColorSetting.Builder()
            .name("lookahead-line-color")
            .description("Line color for lookahead prediction rendering.")
            .defaultValue(new SettingColor(255, 200, 50, 200))
            .visible(() -> thm$lookahead.get() && thm$lookaheadRender.get())
            .build()
        );

        MeteorClient.EVENT_BUS.subscribe(thm$safeMoveListener);
        MeteorClient.EVENT_BUS.subscribe(thm$renderListener);
    }

    @Inject(method = "onTick", at = @At("HEAD"))
    private void thm$handleKeepYReactivation(TickEvent.Pre event, CallbackInfo ci) {
        if (mc.player == null) return;

        if (thm$lastScaffoldTickAge != Integer.MIN_VALUE && mc.player.age - thm$lastScaffoldTickAge > 1) {
            thm$lockedY = Integer.MIN_VALUE;
            thm$lastScaffoldBp = null;
        }

        thm$lastScaffoldTickAge = mc.player.age;
    }

    @Inject(method = "onTick", at = @At("TAIL"))
    private void thm$doInterpolationAndLookahead(TickEvent.Pre event, CallbackInfo ci) {
        if (mc.player == null || mc.world == null) return;

        thm$interpRenderSet.clear();
        thm$lookaheadRenderSet.clear();

        // Lookahead: project current velocity forward and pre-place blocks along predicted path
        if (thm$lookahead != null && thm$lookahead.get()) {
            Vec3d vel = mc.player.getVelocity();
            if (vel.horizontalLength() > 0.05) {
                for (int i = 1; i <= thm$lookaheadTicks.get(); i++) {
                    BlockPos predBp = thm$applyKeepY(BlockPos.ofFloored(
                        mc.player.getX() + vel.x * i,
                        mc.player.getY() - 1,
                        mc.player.getZ() + vel.z * i
                    ));
                    if (!mc.world.getBlockState(predBp).isReplaceable()) continue;
                    place(predBp);
                    if (thm$lookaheadRender != null && thm$lookaheadRender.get()) {
                        thm$lookaheadRenderSet.add(BlockPos.asLong(predBp.getX(), predBp.getY(), predBp.getZ()));
                    }
                }
            }
        }

        // Gap-fill interpolation: fill missing blocks between last tick's bp and current bp
        if (thm$interpolate != null && thm$interpolate.get()) {
            if (thm$lastScaffoldBp != null) {
                int dx = bp.getX() - thm$lastScaffoldBp.getX();
                int dz = bp.getZ() - thm$lastScaffoldBp.getZ();
                int steps = Math.max(Math.abs(dx), Math.abs(dz));
                for (int i = 1; i < steps; i++) {
                    float t = (float) i / steps;
                    BlockPos interpPos = thm$applyKeepY(new BlockPos(
                        thm$lastScaffoldBp.getX() + Math.round(dx * t),
                        bp.getY(),
                        thm$lastScaffoldBp.getZ() + Math.round(dz * t)
                    ));
                    if (!mc.world.getBlockState(interpPos).isReplaceable()) continue;
                    place(interpPos);
                    if (thm$interpolateRender != null && thm$interpolateRender.get()) {
                        thm$interpRenderSet.add(BlockPos.asLong(interpPos.getX(), interpPos.getY(), interpPos.getZ()));
                    }
                }
            }
            thm$lastScaffoldBp = new BlockPos(bp);
        } else {
            thm$lastScaffoldBp = null;
        }
    }

    @Redirect(method = "place", at = @At(value = "INVOKE", target = "Lmeteordevelopment/meteorclient/utils/world/BlockUtils;place(Lnet/minecraft/util/math/BlockPos;Lmeteordevelopment/meteorclient/utils/player/FindItemResult;ZIZZ)Z"))
    private boolean thm$redirectPlace(BlockPos pos, FindItemResult item, boolean rotate, int rotationPriority, boolean swingHand, boolean checkEntities) {
        BlockPos placePos = thm$applyKeepY(pos);

        if (thm$packetPlace != null && thm$packetPlace.get()) {
            int rotateTicks = thm$packetRotateTicks != null ? thm$packetRotateTicks.get() : rotationPriority;
            boolean airPlace = thm$packetAirPlace == null || thm$packetAirPlace.get();
            boolean placed = PacketPlaceUtils.placeBlockPacket(placePos, item, rotate, rotateTicks, airPlace);
            if (placed && swingHand && mc.player != null) {
                Hand hand = item.isOffhand() ? Hand.OFF_HAND : Hand.MAIN_HAND;
                mc.player.swingHand(hand, true);
            }
            return placed;
        }
        return BlockUtils.place(placePos, item, rotate, rotationPriority, swingHand, checkEntities);
    }

    @ModifyArg(
        method = "onTick",
        at = @At(value = "INVOKE", target = "Lmeteordevelopment/meteorclient/systems/modules/movement/Scaffold;place(Lnet/minecraft/util/math/BlockPos;)Z"),
        index = 0
    )
    private BlockPos thm$modifyScaffoldPlaceTarget(BlockPos pos) {
        return thm$applyKeepY(pos);
    }

    @Unique
    private BlockPos thm$applyKeepY(BlockPos pos) {
        if (thm$keepY != null && thm$keepY.get()) {
            if (thm$lockedY == Integer.MIN_VALUE && mc.player != null) thm$lockedY = mc.player.getBlockY() - 1;
            if (thm$lockedY != Integer.MIN_VALUE) return new BlockPos(pos.getX(), thm$lockedY, pos.getZ());
            return pos;
        }
        thm$lockedY = Integer.MIN_VALUE;
        return pos;
    }
}
