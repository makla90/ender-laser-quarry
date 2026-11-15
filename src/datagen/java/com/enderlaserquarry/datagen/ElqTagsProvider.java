package com.enderlaserquarry.datagen;

import com.enderlaserquarry.EnderLaserQuarry;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.TagsProvider;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagBuilder;
import net.minecraft.tags.TagKey;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/** Tags of any registry by id, so entries of other mods (Ender IO fluids) and biomes need no lookup. */
public class ElqTagsProvider<T> extends TagsProvider<T> {
    private final Consumer<ElqTagsProvider<T>> tags;

    public ElqTagsProvider(PackOutput output, ResourceKey<? extends Registry<T>> registry, CompletableFuture<HolderLookup.Provider> lookup,
                           ExistingFileHelper files, Consumer<ElqTagsProvider<T>> tags) {
        super(output, registry, lookup, EnderLaserQuarry.MOD_ID, files);
        this.tags = tags;
    }

    @Override
    protected void addTags(HolderLookup.Provider registries) {
        tags.accept(this);
    }

    /** Entries: "ns:id", or "#ns:tag" for a tag. */
    public void tag(ResourceLocation tag, String... entries) {
        TagBuilder builder = getOrCreateRawBuilder(TagKey.create(registryKey, tag));
        for (String entry : entries) {
            if (entry.startsWith("#")) {
                builder.addTag(ResourceLocation.parse(entry.substring(1)));
            } else {
                builder.addElement(ResourceLocation.parse(entry));
            }
        }
    }
}
