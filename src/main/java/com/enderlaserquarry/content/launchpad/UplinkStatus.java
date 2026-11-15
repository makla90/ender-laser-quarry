package com.enderlaserquarry.content.launchpad;

// ordinals are synced, only append
public enum UplinkStatus {
    NO_SATELLITE,
    NO_RADAR,
    NO_POWER,
    ONLINE,
    // unused, kept so the synced ordinals don't shift
    NO_STATION;

    public static UplinkStatus byOrdinal(int ordinal) {
        return ordinal >= 0 && ordinal < values().length ? values()[ordinal] : NO_SATELLITE;
    }
}
