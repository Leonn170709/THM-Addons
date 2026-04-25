package xyz.thm.addon.mixin.meteor;

import meteordevelopment.meteorclient.systems.modules.player.AutoGap;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.thm.addon.accessor.StuckEatingRetryBridge;
import xyz.thm.addon.accessor.StuckEatingRetryResult;

import static meteordevelopment.meteorclient.MeteorClient.mc;

@Mixin(value = AutoGap.class, remap = false)
public abstract class AutoGapMixin implements StuckEatingRetryBridge {
    @Shadow private boolean eating;
    @Shadow private int slot;
    @Shadow protected abstract void stopEating();
    @Shadow protected abstract void startEating();
    @Shadow protected abstract boolean shouldEat();
    @Shadow protected abstract int findSlot();
    @Shadow protected abstract boolean isNotGapOrEGap(ItemStack stack);
    @Unique private long thm$activeRecoveryToken;
    @Unique private long thm$nextRecoveryToken = 1L;
    @Unique private boolean thm$hasEatingOwnership;

    @Inject(method = "onTick", at = @At("HEAD"), cancellable = true)
    private void thm$suppressAutonomousStarts(CallbackInfo ci) {
        if (!eating && thm$activeRecoveryToken != 0L) ci.cancel();
    }

    @Inject(method = "startEating", at = @At("TAIL"))
    private void thm$markEatingOwnership(CallbackInfo ci) {
        thm$hasEatingOwnership = true;
    }

    @Inject(method = "stopEating", at = @At("TAIL"))
    private void thm$clearEatingOwnership(CallbackInfo ci) {
        thm$hasEatingOwnership = false;
    }

    @Inject(method = "onDeactivate", at = @At("HEAD"))
    private void thm$cleanupOwnedStateOnDeactivate(CallbackInfo ci) {
        if (thm$hasEatingOwnership && !eating) stopEating();
    }

    @Override
    public boolean thm$isActivelyEating() {
        return ((AutoGap) (Object) this).isActive() && eating;
    }

    @Override
    public boolean thm$stillNeedsToEat() {
        return ((AutoGap) (Object) this).isActive() && shouldEat();
    }

    @Override
    public boolean thm$hasValidCurrentEatingItem() {
        AutoGap autoGap = (AutoGap) (Object) this;
        if (!autoGap.isActive() || mc == null || mc.player == null) return false;
        if (slot < 0 || slot >= mc.player.getInventory().getMainStacks().size()) return false;
        return !isNotGapOrEGap(mc.player.getInventory().getStack(slot));
    }

    @Override
    public long thm$beginWatchdogRecovery() {
        AutoGap autoGap = (AutoGap) (Object) this;
        if (!autoGap.isActive()) return 0L;
        if (thm$activeRecoveryToken != 0L) return thm$activeRecoveryToken;

        long token = thm$nextRecoveryToken++;
        if (token == 0L) token = thm$nextRecoveryToken++;
        thm$activeRecoveryToken = token;
        return token;
    }

    @Override
    public void thm$endWatchdogRecovery(long token) {
        if (token == 0L || token != thm$activeRecoveryToken) return;
        thm$activeRecoveryToken = 0L;
    }

    @Override
    public void thm$forceStopEating(long token) {
        if (token == 0L || token != thm$activeRecoveryToken) return;
        if (thm$hasEatingOwnership || eating) stopEating();
    }

    @Override
    public StuckEatingRetryResult thm$forceRestartEating(long token) {
        if (token == 0L || token != thm$activeRecoveryToken) return StuckEatingRetryResult.IMPOSSIBLE;

        AutoGap autoGap = (AutoGap) (Object) this;
        if (!autoGap.isActive()) return StuckEatingRetryResult.IMPOSSIBLE;
        if (!shouldEat()) return StuckEatingRetryResult.CLEARED;

        int newSlot = findSlot();
        if (newSlot == -1) return StuckEatingRetryResult.IMPOSSIBLE;

        slot = newSlot;
        startEating();
        return eating ? StuckEatingRetryResult.RESTARTED : StuckEatingRetryResult.IMPOSSIBLE;
    }
}
