package xyz.thm.addon.accessor;

public interface InputAccessor {
    default float getMovementForward() { return 0; }
    default void setMovementForward(float value) {}
    default float getMovementSideways() { return 0; }
    default void setMovementSideways(float value) {}
}
