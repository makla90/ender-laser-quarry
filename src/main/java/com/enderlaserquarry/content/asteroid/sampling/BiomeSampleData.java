package com.enderlaserquarry.content.asteroid.sampling;

import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Measured ground per biome, saved with the world. Thrown away when the mods (or their versions, or the datapacks)
 * change, since the terrain would be different.
 */
public final class BiomeSampleData extends SavedData {
    private static final String NAME = "ender_laser_quarry_biome_samples";

    public static final class Entry {
        final Object2LongOpenHashMap<ResourceLocation> blocks = new Object2LongOpenHashMap<>();
        long own;
        // planned spots (chunk), the first `done` are counted
        final List<ChunkPos> spots = new ArrayList<>();
        int done;
        boolean searched;

        public Object2LongMap<ResourceLocation> blocks() {
            return blocks;
        }

        public long own() {
            return own;
        }

        public int done() {
            return done;
        }

        public int planned() {
            return spots.size();
        }

        public boolean searched() {
            return searched;
        }

        /** Searched, and no ground of the biome anywhere (modded biome of another dimension...). */
        public boolean unsampleable() {
            return searched && spots.isEmpty();
        }
    }

    private String fingerprint = "";
    private final Map<ResourceLocation, Entry> entries = new HashMap<>();

    public static BiomeSampleData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new Factory<>(BiomeSampleData::new, BiomeSampleData::load, null), NAME);
    }

    public Entry entry(ResourceLocation biome) {
        return entries.computeIfAbsent(biome, b -> new Entry());
    }

    public Entry peek(ResourceLocation biome) {
        return entries.get(biome);
    }

    public Map<ResourceLocation, Entry> entries() {
        return entries;
    }

    /** True if the data was thrown away. */
    public boolean checkFingerprint(String current) {
        if (current.equals(fingerprint)) {
            return false;
        }
        boolean had = !entries.isEmpty();
        entries.clear();
        fingerprint = current;
        setDirty();
        return had;
    }

    public void clear() {
        entries.clear();
        setDirty();
    }

    public void debugSetFingerprint(String value) {
        fingerprint = value;
    }

    public static BiomeSampleData load(CompoundTag tag, HolderLookup.Provider registries) {
        BiomeSampleData data = new BiomeSampleData();
        data.fingerprint = tag.getString("Fingerprint");
        CompoundTag biomes = tag.getCompound("Biomes");
        for (String key : biomes.getAllKeys()) {
            ResourceLocation biome = ResourceLocation.tryParse(key);
            if (biome == null) {
                continue;
            }
            CompoundTag e = biomes.getCompound(key);
            Entry entry = data.entry(biome);
            entry.own = e.getLong("Own");
            entry.done = e.getInt("Done");
            entry.searched = e.getBoolean("Searched");
            for (long chunk : e.getLongArray("Spots")) {
                entry.spots.add(new ChunkPos(chunk));
            }
            for (Tag t : e.getList("Blocks", Tag.TAG_COMPOUND)) {
                CompoundTag b = (CompoundTag) t;
                ResourceLocation block = ResourceLocation.tryParse(b.getString("Id"));
                if (block != null) {
                    entry.blocks.put(block, b.getLong("N"));
                }
            }
            entry.done = Math.min(entry.done, entry.spots.size());
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putString("Fingerprint", fingerprint);
        CompoundTag biomes = new CompoundTag();
        entries.forEach((biome, entry) -> {
            CompoundTag e = new CompoundTag();
            e.putLong("Own", entry.own);
            e.putInt("Done", entry.done);
            e.putBoolean("Searched", entry.searched);
            e.put("Spots", new LongArrayTag(entry.spots.stream().mapToLong(ChunkPos::toLong).toArray()));
            ListTag blocks = new ListTag();
            for (var b : entry.blocks.object2LongEntrySet()) {
                CompoundTag t = new CompoundTag();
                t.putString("Id", b.getKey().toString());
                t.putLong("N", b.getLongValue());
                blocks.add(t);
            }
            e.put("Blocks", blocks);
            biomes.put(biome.toString(), e);
        });
        tag.put("Biomes", biomes);
        return tag;
    }
}
