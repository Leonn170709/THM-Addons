/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.waveycapes;

import xyz.thm.addon.system.THMSystem;

public class WaveyCapesConfig {
    public static boolean enabled = false;
    public static CapeStyle capeStyle = CapeStyle.SMOOTH;
    public static WindMode windMode = WindMode.NONE;
    public static float gravity = 25;
    public static float heightMultiplier = 6;
    public static float straveMultiplier = 2;
    public static float damping = 0.85f;
    public static int stiffness = 3;
    public static float maxBend = 20;
    public static boolean computeGravityVector = false;

    public static void syncFromSystem() {
        THMSystem sys = THMSystem.get();
        if (sys == null) return;
        enabled = sys.wavyCapes.get();
        capeStyle = sys.wavyCapeStyle.get();
        windMode = sys.wavyWindMode.get();
        gravity = sys.wavyGravity.get().floatValue();
        heightMultiplier = sys.wavyHeightMultiplier.get().floatValue();
        straveMultiplier = sys.wavyStraveMultiplier.get().floatValue();
        damping = sys.wavyDamping.get().floatValue();
        stiffness = sys.wavyStiffness.get();
        maxBend = sys.wavyMaxBend.get().floatValue();
        computeGravityVector = sys.wavyComputeGravityVector.get();
    }
}
