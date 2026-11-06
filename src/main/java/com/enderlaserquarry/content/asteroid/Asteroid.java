package com.enderlaserquarry.content.asteroid;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * An asteroid, made of a biome's blocks. x / y are map units, seed is for the look. Unanalyzed ones can't be mined.
 */
public record Asteroid(int id, Identifier biome, AsteroidSize size, long total, long remaining, int x, int y, long seed,
                       boolean analyzed) {
    private static final String ID = "Id";
    private static final String BIOME = "Biome";
    private static final String SIZE = "Size";
    // old saves
    private static final String LEGACY_LARGE = "Large";
    private static final String TOTAL = "Total";
    private static final String REMAINING = "Remaining";
    private static final String X = "X";
    private static final String Y = "Y";
    private static final String SEED = "Seed";
    private static final String ANALYZED = "Analyzed";

    public Asteroid withRemaining(long remaining) {
        return new Asteroid(id, biome, size, total, remaining, x, y, seed, analyzed);
    }

    public Asteroid withAnalyzed() {
        return new Asteroid(id, biome, size, total, remaining, x, y, seed, true);
    }

    public boolean isStarted() {
        return remaining < total;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt(ID, id);
        tag.putString(BIOME, biome.toString());
        tag.putInt(SIZE, size.ordinal());
        tag.putLong(TOTAL, total);
        tag.putLong(REMAINING, remaining);
        tag.putInt(X, x);
        tag.putInt(Y, y);
        tag.putLong(SEED, seed);
        tag.putBoolean(ANALYZED, analyzed);
        return tag;
    }

    @Nullable
    public static Asteroid load(CompoundTag tag) {
        Identifier biome = Identifier.tryParse(tag.getStringOr(BIOME, ""));
        if (biome == null) {
            return null;
        }
        AsteroidSize size = tag.contains(SIZE) ? AsteroidSize.byId(tag.getIntOr(SIZE, 0))
            : tag.getBooleanOr(LEGACY_LARGE, false) ? AsteroidSize.LARGE : AsteroidSize.SMALL;
        return new Asteroid(tag.getIntOr(ID, 0), biome, size, tag.getLongOr(TOTAL, 0L), tag.getLongOr(REMAINING, 0L),
            tag.getIntOr(X, 0), tag.getIntOr(Y, 0), tag.getLongOr(SEED, 0L), tag.getBooleanOr(ANALYZED, false));
    }
}
