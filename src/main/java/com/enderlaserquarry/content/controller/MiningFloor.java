package com.enderlaserquarry.content.controller;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.Arrays;

/**
 * Lowest Y per column for the depth mode. Surface mode asks the generator for the terrain height, which is slow, so
 * it's spread over a few ticks.
 */
final class MiningFloor {
    private static final int COLUMNS_PER_TICK = 64;

    private final ChunkPos chunk;
    private final DepthMode mode;
    private final int fixedFloor;
    // x + z * 16
    private final int[] floors = new int[256];
    private int computed;
    private int lowest = Integer.MAX_VALUE;

    MiningFloor(ServerLevel level, ChunkPos chunk, DepthMode mode, int fixedFloor) {
        this.chunk = chunk;
        this.mode = mode;
        this.fixedFloor = fixedFloor;
        if (mode != DepthMode.SURFACE) {
            Arrays.fill(floors, fixedFloor);
            lowest = floors[0];
            computed = floors.length;
        }
    }

    boolean matches(ChunkPos chunk, DepthMode mode, int fixedFloor) {
        return this.chunk.equals(chunk) && this.mode == mode && this.fixedFloor == fixedFloor;
    }

    // true once all columns are done
    boolean compute(ServerLevel level) {
        if (computed >= floors.length) {
            return true;
        }
        var source = level.getChunkSource();
        int end = Math.min(floors.length, computed + COLUMNS_PER_TICK);
        for (; computed < end; computed++) {
            int floor = source.getGenerator().getBaseHeight(chunk.getBlockX(computed & 15), chunk.getBlockZ(computed >> 4),
                Heightmap.Types.OCEAN_FLOOR_WG, level, source.randomState());
            floors[computed] = Math.max(fixedFloor, floor);
            lowest = Math.min(lowest, floors[computed]);
        }
        return computed >= floors.length;
    }

    int at(int index) {
return floors[index];
    }

    int lowest() {
        return lowest;
    }
}
