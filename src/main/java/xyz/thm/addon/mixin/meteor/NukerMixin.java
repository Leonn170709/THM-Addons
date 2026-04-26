package xyz.thm.addon.mixin.meteor;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.world.Nuker;
import net.minecraft.block.BlockState;
import net.minecraft.fluid.FluidState;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Box;
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

    @Unique private Setting<Enums.NukerSwapModes> thm$swapMode;
    @Unique private Setting<Boolean> thm$avoidLiquidContact;
    @Unique private final InventoryManager thm$inventoryManager = InventoryManager.getInstance();
    @Unique private int thm$silentSyncTicks;

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
    }

    @Inject(method = "onActivate", at = @At("TAIL"))
    private void thm$resetSyncTimer(CallbackInfo ci) {
        thm$silentSyncTicks = 0;
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
}
