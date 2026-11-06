package com.enderlaserquarry.content.controller;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import com.mojang.serialization.Codec;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
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

    public List<ChunkPos> chunks() {
        return List.copyOf(chunks);
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
        return lookup.contains(chunk.pack());
    }

    public boolean containsBlock(BlockPos pos) {
        return lookup.contains(ChunkPos.pack(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ())));
    }

    public boolean isFinished() {
        return !chunks.isEmpty() && index >= chunks.size();
    }

    public void add(ChunkPos chunk) {
        if (lookup.add(chunk.pack())) {
            chunks.add(chunk);
        }
    }

    public boolean remove(ChunkPos chunk) {
        int i = chunks.indexOf(chunk);
        if (i < 0) {
            return false;
        }
        chunks.remove(i);
        lookup.remove(chunk.pack());
        if (i < index) {
            index--;
        }
        return true;
    }

    public void clear() {
        chunks.clear();
        lookup.clear();
        index = 0;
    }

    public void advance() {
        index++;
    }

    public void rewind() {
        index = 0;
    }

    public void save(ValueOutput tag) {
        tag.store(TARGET_CHUNKS, Codec.LONG_STREAM, chunks.stream().mapToLong(ChunkPos::pack));
        tag.putInt(CHUNK_INDEX, index);
    }

    public void load(ValueInput tag) {
        chunks.clear();
        lookup.clear();
        var saved = tag.read(TARGET_CHUNKS, Codec.LONG_STREAM);
        if (saved.isPresent()) {
            saved.get().forEach(chunk -> add(ChunkPos.unpack(chunk)));
        } else {
            // old saves with a single chunk
            tag.getLong(TARGET_CHUNK).ifPresent(chunk -> add(ChunkPos.unpack(chunk)));
        }
        index = tag.getIntOr(CHUNK_INDEX, 0);
    }
}
