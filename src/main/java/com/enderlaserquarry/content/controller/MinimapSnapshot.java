package com.enderlaserquarry.content.controller;

import com.enderlaserquarry.content.launchpad.LaunchPadBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import org.jetbrains.annotations.Nullable;

/**
 * Minimap area and the scan state per chunk. Chunk pictures are sent separately, one byte per column in vanilla map
 * colour format.
 */
public record MinimapSnapshot(ChunkPos center, int radius, byte[] scan) {
    public static final byte UNSCANNED = 0;
    public static final byte SCANNED = 1;
    public static final byte SCANNING = 2;

    public int chunksAcross() {
        return radius * 2 + 1;
    }

    public int sizeInBlocks() {
        return chunksAcross() * 16;
    }

    public ChunkPos minChunk() {
        return new ChunkPos(center.x - radius, center.z - radius);
    }

    public boolean contains(ChunkPos chunk) {
        return Math.abs(chunk.x - center.x) <= radius && Math.abs(chunk.z - center.z) <= radius;
    }

    public boolean sameArea(@Nullable MinimapSnapshot other) {
        return other != null && center.equals(other.center) && radius == other.radius;
    }

    public byte scanState(ChunkPos chunk) {
        if (!contains(chunk)) {
            return UNSCANNED;
        }
        int cx = chunk.x - center.x + radius;
        int cz = chunk.z - center.z + radius;
        return scan[cz * chunksAcross() + cx];
    }

    public static MinimapSnapshot capture(@Nullable LaunchPadBlockEntity pad, ChunkPos center, int radius) {
        int chunks = radius * 2 + 1;
        byte[] scan = new byte[chunks * chunks];
        ChunkPos scanning = pad != null ? pad.getScanningChunk() : null;
        for (int cz = -radius; cz <= radius; cz++) {
            for (int cx = -radius; cx <= radius; cx++) {
                ChunkPos chunk = new ChunkPos(center.x + cx, center.z + cz);
                int index = (cz + radius) * chunks + cx + radius;
                if (pad != null && pad.isChunkScanned(chunk)) {
                    scan[index] = SCANNED;
                } else {
                    scan[index] = chunk.equals(scanning) ? SCANNING : UNSCANNED;
                }
            }
        }
        return new MinimapSnapshot(center, radius, scan);
    }

    // Orbit planner: everything the satellite ever pictured counts as known, wherever it was
    public static MinimapSnapshot capturePlanner(LaunchPadBlockEntity pad, ChunkPos center, int radius) {
        int chunks = radius * 2 + 1;
        byte[] scan = new byte[chunks * chunks];
        ChunkPos scanning = pad.getScanningChunk();
        for (int cz = -radius; cz <= radius; cz++) {
            for (int cx = -radius; cx <= radius; cx++) {
                ChunkPos chunk = new ChunkPos(center.x + cx, center.z + cz);
                int index = (cz + radius) * chunks + cx + radius;
                scan[index] = pad.getScanImages().get(chunk) != null ? SCANNED : chunk.equals(scanning) ? SCANNING : UNSCANNED;
            }
        }
        return new MinimapSnapshot(center, radius, scan);
    }

    // 16x16 top down picture
    public static byte[] captureChunk(ServerLevel level, LevelChunk chunk) {
        byte[] image = new byte[256];
        // heights read once, the shading needs the north neighbour too
        int[] heights = new int[256];
        Heightmap heightmap = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE);
        for (int i = 0; i < 256; i++) {
            heights[i] = heightmap.getFirstAvailable(i & 15, i >> 4) - 1;
        }
        var pos = new BlockPos.MutableBlockPos();
        int baseX = chunk.getPos().getMinBlockX();
        int baseZ = chunk.getPos().getMinBlockZ();
        for (int lz = 0; lz < 16; lz++) {
            for (int lx = 0; lx < 16; lx++) {
                int height = heights[lz * 16 + lx];
                int northHeight = lz > 0 ? heights[(lz - 1) * 16 + lx] : height;

                pos.set(baseX + lx, height, baseZ + lz);
                var state = chunk.getBlockState(pos);
                MapColor color = state.getMapColor(level, pos);

                // shading like vanilla maps
                MapColor.Brightness brightness = height > northHeight ? MapColor.Brightness.HIGH
                    : height < northHeight ? MapColor.Brightness.LOW : MapColor.Brightness.NORMAL;

                image[lz * 16 + lx] = color.getPackedId(brightness);
            }
        }
        return image;
    }
}
