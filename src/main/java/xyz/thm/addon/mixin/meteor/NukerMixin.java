package xyz.thm.addon.mixin.meteor;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.world.Nuker;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.fluid.FluidState;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.thm.addon.mixin.accessor.PlayerInventoryAccessor;
import xyz.thm.addon.utils.InventoryManager;
import xyz.thm.addon.utils.Enums;

import static meteordevelopment.meteorclient.MeteorClient.mc;

@Mixin(value = Nuker.class, remap = false)
public class NukerMixin {
    @Shadow @Final private SettingGroup sgGeneral;
    @Shadow @Final private Setting<Boolean> packetMine;
    @Shadow @Final private Setting<Boolean> interact;
    @Shadow @Final private Setting<Boolean> swing;
    @Shadow @Final private Setting<Boolean> rotate;
    @Shadow @Final private Setting<Nuker.Mode> mode;

    @Unique private Setting<Enums.NukerSwapModes> thm$swapMode;
    @Unique private Setting<Boolean> thm$avoidLiquidContact;
    @Unique private Setting<Boolean> thm$mineBedrock;
    @Unique private Setting<Double> thm$bedrockRange;
    @Unique private final InventoryManager thm$inventoryManager = InventoryManager.getInstance();
    @Unique private int thm$silentSyncTicks;
    @Unique private BlockPos thm$activeBedrockPos;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void thm$onInit(CallbackInfo ci) {
        thm$swapMode = sgGeneral.add(new EnumSetting.Builder<Enums.NukerSwapModes>()
            .name("swap-mode")
            .description("How Nuker swaps to the best tool.")
            .defaultValue(Enums.NukerSwapModes.None)
            .build()
        );

        thm$avoidLiquidContact = sgGeneral.add(new BoolSetting.Builder()
            .name("avoid-liquid-contact")
            .description("Skips mining blocks that are touching water or lava.")
            .defaultValue(false)
            .build()
        );

        thm$mineBedrock = sgGeneral.add(new BoolSetting.Builder()
            .name("mine-bedrock")
            .description("Allows Nuker to mine bedrock.")
            .defaultValue(false)
            .build()
        );

        thm$bedrockRange = sgGeneral.add(new DoubleSetting.Builder()
            .name("bedrock-range")
            .description("Range for bedrock mining.")
            .defaultValue(10.0)
            .min(0.0)
            .sliderRange(0.0, 20.0)
            .visible(() -> thm$mineBedrock != null && thm$mineBedrock.get())
            .build()
        );
    }

    @Inject(method = "breakBlock", at = @At("HEAD"), cancellable = true)
    private void thm$breakBlockSilentSwitch(BlockPos blockPos, CallbackInfo ci) {
        if (thm$avoidLiquidContact != null && thm$avoidLiquidContact.get() && thm$touchesLiquid(blockPos)) {
            ci.cancel();
            return;
        }

        if (thm$swapMode == null || thm$swapMode.get() == Enums.NukerSwapModes.None) return;
        if (mc.player == null || mc.world == null || mc.getNetworkHandler() == null) return;
        if (interact.get()) return;

        BlockState state = mc.world.getBlockState(blockPos);
        int bestSlot = thm$getBestToolSlot(state);
        switch (thm$swapMode.get()) {
            case Normal -> {
                int selected = ((PlayerInventoryAccessor) mc.player.getInventory()).getSelectedSlot();
                if (selected != bestSlot) {
                    ((PlayerInventoryAccessor) mc.player.getInventory()).setSelectedSlot(bestSlot);
                    mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(bestSlot));
                }
            }
            case Silent -> {
                int serverSlot = thm$inventoryManager.getServerSlot();
                if (serverSlot != bestSlot) {
                    thm$inventoryManager.setSlotForced(bestSlot);
                    // Match Speedmine-style behavior: delay sync-back so the server applies the mined action with swapped slot.
                    thm$silentSyncTicks = 1;
                }
            }
            case None -> {}
        }
    }

    @Inject(method = "onTickPre", at = @At("HEAD"))
    private void thm$syncSilentSwapDelayed(TickEvent.Pre event, CallbackInfo ci) {
        if (thm$silentSyncTicks > 0) {
            thm$silentSyncTicks--;
            if (thm$silentSyncTicks == 0) {
                if (mc.player == null || mc.getNetworkHandler() == null) return;
                if (thm$swapMode == null || thm$swapMode.get() != Enums.NukerSwapModes.Silent) return;
                if (interact.get()) return;
                if (!((Nuker) (Object) this).isActive()) return;
                if (thm$inventoryManager.isDesynced()) {
                    thm$inventoryManager.syncToClient();
                }
            }
        }

        if (thm$mineBedrock != null && thm$mineBedrock.get()) {
            thm$tickBedrockMining();
        } else {
            thm$activeBedrockPos = null;
        }
    }

    @Inject(method = "onActivate", at = @At("TAIL"))
    private void thm$resetSyncTimer(CallbackInfo ci) {
        thm$silentSyncTicks = 0;
        thm$activeBedrockPos = null;
    }

    @Inject(method = "lambda$onTickPre$9", at = @At("HEAD"), cancellable = true)
    private void thm$filterLiquidCandidates(
        double pX, double pY, double pZ, double rangeSq,
        BlockPos playerBlockPos, Box box,
        BlockPos blockPos, BlockState blockState,
        CallbackInfo ci
    ) {
        if (thm$avoidLiquidContact != null && thm$avoidLiquidContact.get() && thm$touchesLiquid(blockPos)) {
            ci.cancel();
        }
    }

    @Unique
    private boolean thm$touchesLiquid(BlockPos pos) {
        if (mc.world == null) return false;
        if (thm$isWaterOrLava(pos)) return true;

        for (Direction direction : Direction.values()) {
            if (thm$isWaterOrLava(pos.offset(direction))) return true;
        }

        return false;
    }

    @Unique
    private boolean thm$isWaterOrLava(BlockPos pos) {
        FluidState fluidState = mc.world.getFluidState(pos);
        return fluidState.isIn(FluidTags.WATER) || fluidState.isIn(FluidTags.LAVA);
    }

    @Unique
    private int thm$getBestToolSlot(BlockState state) {
        int selected = ((PlayerInventoryAccessor) mc.player.getInventory()).getSelectedSlot();
        int bestSlot = selected;
        float bestSpeed = 0f;

        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isEmpty()) continue;
            float speed = stack.getMiningSpeedMultiplier(state);
            if (speed > bestSpeed) {
                bestSpeed = speed;
                bestSlot = i;
            }
        }

        return bestSlot;
    }

    @Unique
    private void thm$tickBedrockMining() {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;
        if (mode != null && mode.get() == Nuker.Mode.Smash) {
            thm$activeBedrockPos = null;
            return;
        }

        if (thm$activeBedrockPos != null && mc.world.getBlockState(thm$activeBedrockPos).getBlock() != Blocks.BEDROCK) {
            thm$activeBedrockPos = null;
        }

        if (thm$activeBedrockPos != null && thm$isOutOfBedrockRange(thm$activeBedrockPos)) {
            thm$activeBedrockPos = null;
        }

        if (thm$activeBedrockPos != null && mode != null && mode.get() == Nuker.Mode.Flatten && thm$activeBedrockPos.getY() + 0.5 < mc.player.getY()) {
            thm$activeBedrockPos = null;
        }

        if (thm$activeBedrockPos == null) {
            thm$activeBedrockPos = thm$findNearestBedrock();
            if (thm$activeBedrockPos == null) return;
        }

        if (rotate.get()) {
            Rotations.rotate(Rotations.getYaw(thm$activeBedrockPos), Rotations.getPitch(thm$activeBedrockPos));
        }

        Direction direction = BlockUtils.getDirection(thm$activeBedrockPos);
        if (direction == null) direction = Direction.UP;
        mc.interactionManager.updateBlockBreakingProgress(thm$activeBedrockPos, direction);
        if (swing.get()) mc.player.swingHand(Hand.MAIN_HAND);
    }

    @Unique
    private BlockPos thm$findNearestBedrock() {
        if (mc.player == null || mc.world == null) return null;
        double range = thm$getBedrockRange();
        double rangeSq = range * range;

        BlockPos origin = mc.player.getBlockPos();
        int radius = (int) Math.ceil(range);
        BlockPos best = null;
        double bestDistSq = Double.MAX_VALUE;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    BlockPos pos = origin.add(dx, dy, dz);
                    if (mc.world.getBlockState(pos).getBlock() != Blocks.BEDROCK) continue;

                    Vec3d center = pos.toCenterPos();
                    double distSq = mc.player.squaredDistanceTo(center);
                    if (distSq > rangeSq) continue;
                    if (mode != null && mode.get() == Nuker.Mode.Flatten && pos.getY() + 0.5 < mc.player.getY()) continue;
                    if (distSq < bestDistSq) {
                        bestDistSq = distSq;
                        best = pos.toImmutable();
                    }
                }
            }
        }

        return best;
    }

    @Unique
    private boolean thm$isOutOfBedrockRange(BlockPos pos) {
        if (mc.player == null) return true;
        double range = thm$getBedrockRange();
        return mc.player.squaredDistanceTo(pos.toCenterPos()) > range * range;
    }

    @Unique
    private double thm$getBedrockRange() {
        if (thm$bedrockRange == null) return 10.0;
        return Math.max(0.0, thm$bedrockRange.get());
    }
}
