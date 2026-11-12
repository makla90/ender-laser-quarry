package com.enderlaserquarry.content.launchpad;

/**
 * Flight stages, durations in ticks. Ordinals are saved, only append.
 */
public enum FlightPhase {
    IDLE(0),
    COUNTDOWN(60),
    ASCENT(140),
    // release or catch the satellite
    ORBIT_OPS(80),
    DESCENT(160);

    private final int duration;

    FlightPhase(int duration) {
        this.duration = duration;
    }

    public int duration() {
        return duration;
    }

    public static FlightPhase byOrdinal(int ordinal) {
        return ordinal >= 0 && ordinal < values().length ? values()[ordinal] : IDLE;
    }

    public static int totalFlightTicks() {
        return COUNTDOWN.duration + ASCENT.duration + ORBIT_OPS.duration + DESCENT.duration;
    }

    // ordinals are saved, only append
    public enum Mission {
        DEPLOY,
        RECOVER,
        // fuel the satellite up, it then flies over a new area
        TRANSFER,
        // bring Dew of the Void up to the Void Satellite, it stays up
        RESUPPLY;

        // the rocket meets the satellite up there instead of docking it
        public boolean refuelsInOrbit() {
            return this == TRANSFER || this == RESUPPLY;
        }

        public static Mission byOrdinal(int ordinal) {
            return ordinal >= 0 && ordinal < values().length ? values()[ordinal] : DEPLOY;
        }
    }
}
