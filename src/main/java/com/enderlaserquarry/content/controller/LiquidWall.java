package com.enderlaserquarry.content.controller;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jetbrains.annotations.Nullable;

/**
 * Keeps liquids out of the hole. Builds a wall on the ring around each queued chunk, but only where liquid could get
 * in, and handles liquid found later while mining.
 */
public final class LiquidWall {
    // values() copies the array every call
    private static final Direction[] DIRECTIONS = Direction.values();

    public static final Block WALL_BLOCK = Blocks.DEEPSLATE_TILES;
    // 18 + 18 + 16 + 16
    public static final int COLUMNS = 68;

    // places walls and deletes liquid, both only where the owner may
    public interface Placer {
        void placeWall(BlockPos pos, BlockState replaced);

        boolean mayDelete(BlockPos pos, BlockState state);
    }

    // north row, south row, then west and east side
    public static void setPos(BlockPos.MutableBlockPos pos, ChunkPos ring, int index, int y) {
        int minX = ring.getMinBlockX() - 1;
        int minZ = ring.getMinBlockZ() - 1;
        if (index < 18) {
            pos.set(minX + index, y, minZ);
        } else if (index < 36) {
            pos.set(minX + index - 18, y, minZ + 17);
        } else if (index < 52) {
            pos.set(minX, y, minZ + 1 + index - 36);
        } else {
            pos.set(minX + 17, y, minZ + 1 + index - 52);
        }
    }

    // ignores leaves, otherwise a tree next to the chunk gets a wall up to its top
    public static int columnTop(LevelChunk chunk, BlockPos pos) {
        return chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX() & 15, pos.getZ() & 15);
    }

    // MIN_VALUE while a neighbour chunk is still loading
    public static int top(ServerLevel level, ChunkPos ring, ChunkQueue queue) {
        var pos = new BlockPos.MutableBlockPos();
        int top = level.getMinY();
        for (int i = 0; i < COLUMNS; i++) {
            setPos(pos, ring, i, 0);
            LevelChunk chunk = loadedChunk(level, pos);
            if (chunk == null) {
                return Integer.MIN_VALUE;
            }
            if (queue.containsBlock(pos)) {
                continue;
            }
            top = Math.max(top, columnTop(chunk, pos));
        }
        return top;
    }

    // Liquid in it, or open with liquid next to it. Solid ground is fine as it is.
    public static boolean needsWall(ServerLevel level, ChunkQueue queue, BlockPos pos, BlockState state) {
        if (!canWall(level, pos, state)) {
            return false;
        }
        if (MiningRules.hasLiquid(state)) {
            return true;
        }
        if (state.blocksMotion()) {
            return false;
        }
        var neighbour = new BlockPos.MutableBlockPos();
        for (Direction direction : DIRECTIONS) {
            if (direction == Direction.DOWN) {
                // doesn't flow up
                continue;
            }
            neighbour.setWithOffset(pos, direction);
            if (direction != Direction.UP && queue.containsBlock(neighbour)) {
                continue;
            }
            if (MiningRules.hasLiquid(level.getBlockState(neighbour))) {
                return true;
            }
        }
        return false;
    }

    private static boolean canWall(ServerLevel level, BlockPos pos, BlockState state) {
        return state.isAir() || MiningRules.isLiquid(state) || MiningRules.canMine(level, pos, state);
    }

    // For liquid the wall phase couldn't see (behind a cave wall etc.)
    public static void sealEdge(ServerLevel level, ChunkQueue queue, ChunkPos mining, BlockPos pos, Placer placer) {
        int localX = pos.getX() & 15;
        int localZ = pos.getZ() & 15;
        if (localX != 0 && localX != 15 && localZ != 0 && localZ != 15) {
            return;
        }
        var neighbour = new BlockPos.MutableBlockPos();
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            neighbour.setWithOffset(pos, direction);
            if (isInChunk(neighbour, mining) || loadedChunk(level, neighbour) == null) {
                continue;
            }
            BlockState state = level.getBlockState(neighbour);
            if (queue.containsBlock(neighbour)) {
                if (MiningRules.isLiquid(state) && placer.mayDelete(neighbour, state)) {
                    level.setBlock(neighbour, Blocks.AIR.defaultBlockState(), MiningRules.SILENT);
                }
            } else if (needsWall(level, queue, neighbour, state)) {
                // free, it's rare
                placer.placeWall(neighbour.immutable(), state);
            }
        }
    }

    // Deletes liquid next to an opened spot so a scheduled fluid tick can't flow in. Liquid below floor stays.
    public static int drainNeighbours(ServerLevel level, ChunkQueue queue, ChunkPos mining, BlockPos pos, int floor, Placer placer) {
        int drained = 0;
        var neighbour = new BlockPos.MutableBlockPos();
        for (Direction direction : DIRECTIONS) {
            neighbour.setWithOffset(pos, direction);
            if (direction == Direction.UP || !isInChunk(neighbour, mining) || neighbour.getY() < floor) {
                continue;
            }
            BlockState state = level.getBlockState(neighbour);
            if (MiningRules.isLiquid(state) && placer.mayDelete(neighbour, state)) {
                level.setBlock(neighbour, Blocks.AIR.defaultBlockState(), MiningRules.SILENT);
                sealEdge(level, queue, mining, neighbour, placer);
                drained++;
            }
        }
        return drained;
    }

    public static boolean isInChunk(BlockPos pos, @Nullable ChunkPos chunk) {
        return chunk != null && SectionPos.blockToSectionCoord(pos.getX()) == chunk.x() && SectionPos.blockToSectionCoord(pos.getZ()) == chunk.z();
    }

    // never loads a chunk
    @Nullable
    public static LevelChunk loadedChunk(ServerLevel level, BlockPos pos) {
        return level.getChunkSource().getChunkNow(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()));
    }

    private LiquidWall() {
    }
}
