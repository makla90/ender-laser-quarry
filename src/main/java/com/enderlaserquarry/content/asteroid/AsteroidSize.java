package com.enderlaserquarry.content.asteroid;

import com.enderlaserquarry.config.QuarryConfig;
import net.minecraft.util.RandomSource;

public enum AsteroidSize {
    SMALL,
    MEDIUM,
    LARGE;

    private static final AsteroidSize[] VALUES = values();

    public static AsteroidSize byId(int id) {
        return VALUES[Math.floorMod(id, VALUES.length)];
    }

    public static AsteroidSize roll(RandomSource random) {
        double roll = random.nextDouble();
        double large = QuarryConfig.VOID_LARGE_CHANCE.get();
        if (roll < large) {
            return LARGE;
        }
        return roll < large + QuarryConfig.VOID_MEDIUM_CHANCE.get() ? MEDIUM : SMALL;
    }

    public long blocks() {
        int chunks = switch (this) {
            case SMALL -> QuarryConfig.VOID_SMALL_CHUNKS.get();
            case MEDIUM -> QuarryConfig.VOID_MEDIUM_CHUNKS.get();
            case LARGE -> QuarryConfig.VOID_LARGE_CHUNKS.get();
        };
        return (long) chunks * QuarryConfig.VOID_BLOCKS_PER_CHUNK.get();
    }

    public String key() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}
