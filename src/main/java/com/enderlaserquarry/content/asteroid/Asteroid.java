package com.enderlaserquarry.content.asteroid;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * An asteroid, made of a biome's blocks. x / y are map units, seed is for the look. Unanalyzed ones can't be mined.
 */
public record Asteroid(int id, ResourceLocation biome, AsteroidSize size, long total, long remaining, int x, int y, long seed,
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
        ResourceLocation biome = ResourceLocation.tryParse(tag.getString(BIOME));
        if (biome == null) {
            return null;
        }
        AsteroidSize size = tag.contains(SIZE) ? AsteroidSize.byId(tag.getInt(SIZE))
            : tag.getBoolean(LEGACY_LARGE) ? AsteroidSize.LARGE : AsteroidSize.SMALL;
        return new Asteroid(tag.getInt(ID), biome, size, tag.getLong(TOTAL), tag.getLong(REMAINING),
            tag.getInt(X), tag.getInt(Y), tag.getLong(SEED), tag.getBoolean(ANALYZED));
    }
}
