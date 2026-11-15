package com.enderlaserquarry.content.asteroid.sampling;

import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2BooleanOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.InfestedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Counts the natural ground of one generated chunk, per biome (each block by the biome at its own spot).
 * Main thread only.
 */
public final class ChunkCounter {
    // per state, natural ground or not; shapes of full blocks don't depend on the spot
    private static final Reference2BooleanOpenHashMap<BlockState> TERRAIN = new Reference2BooleanOpenHashMap<>();

    /** Block counts per biome, plus how many ground blocks the chunk had in total. */
    public record Counts(Map<Holder<Biome>, Object2LongMap<BlockState>> blocks, long total) {
        public long of(Holder<Biome> biome) {
            Object2LongMap<BlockState> map = blocks.get(biome);
            if (map == null) {
                return 0;
            }
            long sum = 0;
            for (long count : map.values()) {
                sum += count;
            }
            return sum;
        }
    }

    public static Counts count(ServerLevel level, ChunkAccess chunk) {
        List<BoundingBox> boxes = structureBoxes(level, chunk);
        Map<Holder<Biome>, Object2LongMap<BlockState>> blocks = new HashMap<>();
        long[] total = {0};
        BlockPos origin = chunk.getPos().getWorldPosition();
        LevelChunkSection[] sections = chunk.getSections();
        for (int index = 0; index < sections.length; index++) {
            LevelChunkSection section = sections[index];
            if (section.hasOnlyAir()) {
                continue;
            }
            int sectionY = SectionPos.sectionToBlockCoord(chunk.getSectionYFromSectionIndex(index));
            Holder<Biome> single = singleBiome(section);
            if (single != null && !touches(boxes, origin.getX(), sectionY, origin.getZ())) {
                // whole section is one biome and no structure: count the palette
                Object2LongMap<BlockState> map = blocks.computeIfAbsent(single, b -> new Object2LongOpenHashMap<>());
                section.getStates().count((state, n) -> {
                    if (isTerrain(chunk, origin, state)) {
                        map.mergeLong(state, n, Long::sum);
                        total[0] += n;
                    }
                });
                continue;
            }
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        BlockState state = section.getBlockState(x, y, z);
                        if (!isTerrain(chunk, origin, state) || inBox(boxes, origin.getX() + x, sectionY + y, origin.getZ() + z)) {
                            continue;
                        }
                        Holder<Biome> biome = section.getNoiseBiome(x >> 2, y >> 2, z >> 2);
                        blocks.computeIfAbsent(biome, b -> new Object2LongOpenHashMap<>()).mergeLong(state, 1, Long::sum);
                        total[0]++;
                    }
                }
            }
        }
        return new Counts(blocks, total[0]);
    }

    private static Holder<Biome> singleBiome(LevelChunkSection section) {
        Holder<Biome> first = section.getNoiseBiome(0, 0, 0);
        for (int y = 0; y < 4; y++) {
            for (int z = 0; z < 4; z++) {
                for (int x = 0; x < 4; x++) {
                    if (section.getNoiseBiome(x, y, z) != first) {
                        return null;
                    }
                }
            }
        }
        return first;
    }

    // structure pieces reaching into this chunk are left out
    private static List<BoundingBox> structureBoxes(ServerLevel level, ChunkAccess chunk) {
        List<BoundingBox> boxes = new ArrayList<>();
        BoundingBox chunkBox = new BoundingBox(chunk.getPos().getMinBlockX(), level.getMinBuildHeight(), chunk.getPos().getMinBlockZ(),
            chunk.getPos().getMaxBlockX(), level.getMaxBuildHeight(), chunk.getPos().getMaxBlockZ());
        for (StructureStart start : level.structureManager().startsForStructure(chunk.getPos(), structure -> true)) {
            for (StructurePiece piece : start.getPieces()) {
                if (piece.getBoundingBox().intersects(chunkBox)) {
                    boxes.add(piece.getBoundingBox());
                }
            }
        }
        return boxes;
    }

    private static boolean touches(List<BoundingBox> boxes, int x, int y, int z) {
        for (BoundingBox box : boxes) {
            if (box.maxX() >= x && box.minX() <= x + 15 && box.maxY() >= y && box.minY() <= y + 15 && box.maxZ() >= z && box.minZ() <= z + 15) {
                return true;
            }
        }
        return false;
    }

    private static boolean inBox(List<BoundingBox> boxes, int x, int y, int z) {
        for (BoundingBox box : boxes) {
            if (box.isInside(x, y, z)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Natural ground: a full, breakable, plain block, no liquid, no plant or tree. Full by its outline (mud, soul sand).
     * Infested stone drops nothing; nether bricks are fortress supports reaching past the structure's box.
     */
    private static boolean isTerrain(ChunkAccess chunk, BlockPos pos, BlockState state) {
        if (TERRAIN.containsKey(state)) {
            return TERRAIN.getBoolean(state);
        }
        boolean terrain = !state.isAir() && state.getFluidState().isEmpty() && !state.hasBlockEntity() && state.getDestroySpeed(chunk, pos) >= 0
            && !state.is(BlockTags.LOGS) && !state.is(BlockTags.LEAVES) && !state.is(BlockTags.WART_BLOCKS) && !state.is(BlockTags.MINEABLE_WITH_AXE)
            && !state.is(Blocks.SHROOMLIGHT) && !state.is(Blocks.MUSHROOM_STEM) && !state.is(Blocks.RED_MUSHROOM_BLOCK)
            && !state.is(Blocks.BROWN_MUSHROOM_BLOCK) && !state.is(Blocks.NETHER_BRICKS) && !(state.getBlock() instanceof InfestedBlock)
            && Block.isShapeFullBlock(state.getShape(chunk, pos));
        TERRAIN.put(state, terrain);
        return terrain;
    }

    // tags can change on reload
    public static void clearCache() {
        TERRAIN.clear();
    }

    private ChunkCounter() {
    }
}
