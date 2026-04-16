package xyz.thm.addon.accessor;

public interface StuckEatingRetryBridge {
    boolean thm$isActivelyEating();

    boolean thm$stillNeedsToEat();

    boolean thm$hasValidCurrentEatingItem();

    void thm$forceStopEating();

    StuckEatingRetryResult thm$forceRestartEating();
}
