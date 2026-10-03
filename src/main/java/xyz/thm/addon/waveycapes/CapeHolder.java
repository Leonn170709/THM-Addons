/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.waveycapes;

import net.minecraft.world.entity.LivingEntity;
import xyz.thm.addon.waveycapes.sim.BasicSimulation;
import xyz.thm.addon.waveycapes.sim.StickSimulation3d;
import xyz.thm.addon.waveycapes.util.Mth;
import xyz.thm.addon.waveycapes.util.Vector2;
import xyz.thm.addon.waveycapes.util.Vector3;

public interface CapeHolder {
    BasicSimulation getSimulation();
    void setSimulation(BasicSimulation sim);
    void setDirty();

    /** The render layer may only refresh the gravity vector once per tick, not once per render pass. */
    void setGravityVectorRequest(boolean canUpdate);
    boolean canUpdateGravityVector();

    default void updateSimulation(int partCount) {
        BasicSimulation simulation = getSimulation();
        if (simulation == null || simulation.getClass() != StickSimulation3d.class) {
            simulation = new StickSimulation3d();
            setSimulation(simulation);
        }
        if (simulation.init(partCount)) setDirty();
    }

    default void simulate(LivingEntity entity) {
        BasicSimulation simulation = getSimulation();
        if (simulation == null || simulation.empty()) return;

        double xCloak = entity.xo;
        double zCloak = entity.zo;

        double d = xCloak - entity.getX();
        double m = zCloak - entity.getZ();
        float n = entity.yBodyRotO + (entity.yBodyRot - entity.yBodyRotO);

        double o = Mth.sin(n * 0.017453292F);
        double p = -Mth.cos(n * 0.017453292F);

        float heightMul = WaveyCapesConfig.heightMultiplier;
        float straveMul = WaveyCapesConfig.straveMultiplier;
        if (entity.isUnderWater()) heightMul *= 2;

        double fallHack = Mth.clamp((entity.yo - entity.getY()) * 10, 0, 1);

        simulation.setGravity(entity.isUnderWater()
            ? WaveyCapesConfig.gravity / 10f
            : WaveyCapesConfig.gravity);
        simulation.setDamping(WaveyCapesConfig.damping);
        simulation.setStiffness(WaveyCapesConfig.stiffness);
        simulation.setMaxBend(WaveyCapesConfig.maxBend);

        Vector3 gravity = new Vector3(0, -1, 0);

        Vector2 strave = new Vector2(
            (float) (entity.getX() - entity.xo),
            (float) (entity.getZ() - entity.zo));
        strave.rotateDegrees(-entity.getYRot());

        double changeX = (d * o + m * p) + fallHack
            + (entity.isShiftKeyDown() && !simulation.isSneaking() ? 3 : 0);
        double changeY = ((entity.getY() - entity.yo) * heightMul)
            + (entity.isShiftKeyDown() && !simulation.isSneaking() ? 1 : 0);
        double changeZ = -strave.x * straveMul;

        simulation.setSneaking(entity.isShiftKeyDown());
        Vector3 change = new Vector3((float) changeX, (float) changeY, (float) changeZ);

        // With computeGravityVector on, the render layer sets the direction from the model's real
        // down vector, so the swim-pose approximation would only fight it.
        if (!WaveyCapesConfig.computeGravityVector) {
            if (entity.isVisuallySwimming()) {
                float rotation = entity.getXRot() + 90;
                gravity.rotateDegrees(rotation);
                change.rotateDegrees(rotation);
            }
            simulation.setGravityDirection(gravity);
        }
        simulation.applyMovement(change);
        simulation.simulate();
    }
}
