package com.enderlaserquarry.content.obelisk;

import net.minecraft.util.Mth;

/**
 * Timeline of the 10 s power up before mining: dishes spool up, beams grow, crystal charges, fires up to the satellite.
 * Everything goes 0 to 1 so the renderers and sounds share it.
 */
public final class PowerUpSequence {
    public static final int TOTAL = 200;

    public static final int SPOOL_END = 50;
    public static final int DISH_BEAM_START = 50;
    public static final int DISH_BEAM_END = 100;
    public static final int CHARGE_START = 60;
    public static final int SHAKE_START = 140;
    public static final int FIRE = 170;
    public static final int UPLINK_END = 185;

    private PowerUpSequence() {
    }

    public static float dishSpool(float ticks) {
        return ramp(ticks, 0, SPOOL_END);
    }

    public static float dishBeam(float ticks) {
        return ramp(ticks, DISH_BEAM_START, DISH_BEAM_END);
    }

    public static float crystalCharge(float ticks) {
        return ramp(ticks, CHARGE_START, FIRE);
    }

    public static float shake(float ticks) {
        return ticks < FIRE ? ramp(ticks, SHAKE_START, FIRE) : 0;
    }

    public static float uplink(float ticks) {
        float t = Mth.clamp((ticks - FIRE) / (UPLINK_END - FIRE), 0, 1);
        return t * t;
    }

    public static float flash(float ticks) {
        return ticks < FIRE ? 0 : Math.max(0, 1 - (ticks - FIRE) / 10f);
    }

    private static float ramp(float ticks, float start, float end) {
        float t = Mth.clamp((ticks - start) / (end - start), 0, 1);
        return t * t * (3 - 2 * t);
    }
}
