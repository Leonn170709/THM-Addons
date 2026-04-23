package xyz.thm.addon.accessor;

public interface StuckEatingRetryBridge {
    boolean thm$isActivelyEating();

    boolean thm$stillNeedsToEat();

    boolean thm$hasValidCurrentEatingItem();

    long thm$beginWatchdogRecovery();

    void thm$endWatchdogRecovery(long token);

    void thm$forceStopEating(long token);

    StuckEatingRetryResult thm$forceRestartEating(long token);
}
