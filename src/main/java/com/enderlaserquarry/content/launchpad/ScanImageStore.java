package com.enderlaserquarry.content.launchpad;

import com.enderlaserquarry.content.controller.MinimapSnapshot;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongPredicate;

/**
 * Chunk pictures of the satellite. Each change gets a new revision so the minimap only sends what the GUI doesn't have.
 */
public final class ScanImageStore {
    public record ScanImage(long chunk, byte[] pixels, int revision) {
    }

    private final Map<Long, ScanImage> images = new HashMap<>();
    private int revision;

    public byte @Nullable [] get(ChunkPos chunk) {
        ScanImage image = images.get(chunk.toLong());
        return image == null ? null : image.pixels();
    }

    // true if it changed
    public boolean put(ChunkPos chunk, byte[] pixels) {
        long key = chunk.toLong();
        ScanImage old = images.get(key);
        if (old != null && Arrays.equals(old.pixels(), pixels)) {
            return false;
        }
        images.put(key, new ScanImage(key, pixels, ++revision));
        return true;
    }

    public int revision() {
        return revision;
    }

    // oldest first, the rest comes with the next request
    public List<ScanImage> changedSince(int since, LongPredicate inArea, int limit) {
        List<ScanImage> changed = new ArrayList<>();
        for (ScanImage image : images.values()) {
            if (image.revision() > since && inArea.test(image.chunk())) {
                changed.add(image);
            }
        }
        changed.sort(Comparator.comparingInt(ScanImage::revision));
        return changed.size() > limit ? changed.subList(0, limit) : changed;
    }

    public boolean isEmpty() {
        return images.isEmpty();
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        images.forEach((chunk, image) -> tag.putByteArray(Long.toString(chunk), image.pixels()));
        return tag;
    }

    public void load(CompoundTag tag) {
        images.clear();
        revision = 0;
        for (String key : tag.getAllKeys()) {
            put(new ChunkPos(Long.parseLong(key)), tag.getByteArray(key));
        }
    }
}
