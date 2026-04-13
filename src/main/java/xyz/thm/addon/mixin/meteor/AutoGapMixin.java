package xyz.thm.addon.mixin.meteor;

import meteordevelopment.meteorclient.systems.modules.player.AutoGap;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
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
    public void thm$forceStopEating() {
        if (eating) stopEating();
    }

    @Override
    public StuckEatingRetryResult thm$forceRestartEating() {
        AutoGap autoGap = (AutoGap) (Object) this;
        if (!autoGap.isActive()) return StuckEatingRetryResult.CLEARED;
        if (!shouldEat()) return StuckEatingRetryResult.CLEARED;

        int newSlot = findSlot();
        if (newSlot == -1) return StuckEatingRetryResult.IMPOSSIBLE;

        slot = newSlot;
        startEating();
        return eating ? StuckEatingRetryResult.RESTARTED : StuckEatingRetryResult.IMPOSSIBLE;
    }
}
