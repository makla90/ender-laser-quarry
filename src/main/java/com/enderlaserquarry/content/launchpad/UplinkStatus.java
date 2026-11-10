package com.enderlaserquarry.content.launchpad;

// ordinals are synced, only append
public enum UplinkStatus {
    NO_SATELLITE,
    NO_RADAR,
    NO_POWER,
    ONLINE,
    // unused, the uplink no longer needs the quarry
    NO_STATION;

    public static UplinkStatus byOrdinal(int ordinal) {
        return ordinal >= 0 && ordinal < values().length ? values()[ordinal] : NO_SATELLITE;
    }
}
