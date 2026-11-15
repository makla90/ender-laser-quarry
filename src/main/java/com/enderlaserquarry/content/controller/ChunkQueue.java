package com.enderlaserquarry.content.controller;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import org.jetbrains.annotations.Nullable;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import java.util.ArrayList;
import java.util.List;

/**
 * The mining queue. Chunks before index() are done.
 */
public final class ChunkQueue {
    private static final String TARGET_CHUNK = "TargetChunk";
    private static final String TARGET_CHUNKS = "TargetChunks";
    private static final String CHUNK_INDEX = "ChunkIndex";

    private final List<ChunkPos> chunks = new ArrayList<>();
    // same chunks, for fast lookups (big queues ask per mined block)
    private final LongOpenHashSet lookup = new LongOpenHashSet();
    private int index;
    // "x,z;x,z" for the menu, rebuilt only after a change (the menu asks every tick)
    @Nullable
    private String encoded;

    public List<ChunkPos> chunks() {
        return List.copyOf(chunks);
    }

    public String encoded() {
        if (encoded == null) {
            var builder = new StringBuilder();
            for (ChunkPos chunk : chunks) {
                if (!builder.isEmpty()) {
                    builder.append(';');
                }
                builder.append(chunk.x).append(',').append(chunk.z);
            }
            encoded = builder.toString();
        }
        return encoded;
    }

    public ChunkPos get(int i) {
        return chunks.get(i);
    }

    public int size() {
        return chunks.size();
    }

    public boolean isEmpty() {
        return chunks.isEmpty();
    }

    public int index() {
        return index;
    }

    @Nullable
    public ChunkPos current() {
        return index < chunks.size() ? chunks.get(index) : null;
    }

    public boolean contains(ChunkPos chunk) {
        return lookup.contains(chunk.toLong());
    }

    public boolean containsBlock(BlockPos pos) {
        return lookup.contains(ChunkPos.asLong(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ())));
    }

    public boolean isFinished() {
        return !chunks.isEmpty() && index >= chunks.size();
    }

    public void add(ChunkPos chunk) {
        if (lookup.add(chunk.toLong())) {
            chunks.add(chunk);
            encoded = null;
        }
    }

    public boolean remove(ChunkPos chunk) {
        int i = chunks.indexOf(chunk);
        if (i < 0) {
            return false;
        }
        chunks.remove(i);
        lookup.remove(chunk.toLong());
        encoded = null;
        if (i < index) {
            index--;
        }
        return true;
    }

    public void clear() {
        chunks.clear();
        lookup.clear();
        encoded = null;
        index = 0;
    }

    public void advance() {
        index++;
    }

    public void rewind() {
        index = 0;
    }

    public void save(CompoundTag tag) {
        tag.putLongArray(TARGET_CHUNKS, chunks.stream().mapToLong(ChunkPos::toLong).toArray());
        tag.putInt(CHUNK_INDEX, index);
    }

    public void load(CompoundTag tag) {
        chunks.clear();
        lookup.clear();
        encoded = null;
        if (tag.contains(TARGET_CHUNKS)) {
            for (long chunk : tag.getLongArray(TARGET_CHUNKS)) {
                add(new ChunkPos(chunk));
            }
        } else if (tag.contains(TARGET_CHUNK)) {
            // old saves with a single chunk
            add(new ChunkPos(tag.getLong(TARGET_CHUNK)));
        }
        index = tag.getInt(CHUNK_INDEX);
    }
}
