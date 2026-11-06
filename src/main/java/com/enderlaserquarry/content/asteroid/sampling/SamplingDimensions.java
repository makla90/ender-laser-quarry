package com.enderlaserquarry.content.asteroid.sampling;

import com.enderlaserquarry.EnderLaserQuarry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * Hidden copies of the Overworld, Nether and End, only for measuring biomes. Never saved (see ChunkMapMixin).
 */
@EventBusSubscriber(modid = EnderLaserQuarry.MOD_ID)
public final class SamplingDimensions {
    public static final ResourceKey<Level> OVERWORLD = key("sampling_overworld");
    public static final ResourceKey<Level> NETHER = key("sampling_nether");
    public static final ResourceKey<Level> END = key("sampling_end");

    private static ResourceKey<Level> key(String path) {
        return ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath(EnderLaserQuarry.MOD_ID, path));
    }

    public static boolean isSampling(ResourceKey<Level> dimension) {
        return dimension == OVERWORLD || dimension == NETHER || dimension == END;
    }

    public static boolean isSamplingStem(ResourceKey<LevelStem> stem) {
        return isSampling(Registries.levelStemToLevel(stem));
    }

    public static ResourceKey<Level> forSource(ResourceKey<Level> source) {
        return source == Level.NETHER ? NETHER : source == Level.END ? END : OVERWORLD;
    }

    // leftovers from a crash: the mixin keeps chunks out, but the folders can still appear
    @SubscribeEvent
    public static void onAboutToStart(ServerAboutToStartEvent event) {
        wipe(event.getServer());
    }

    @SubscribeEvent
    public static void onStopped(ServerStoppedEvent event) {
        wipe(event.getServer());
    }

    private static void wipe(MinecraftServer server) {
        Path root = server.getWorldPath(LevelResource.ROOT);
        for (ResourceKey<Level> key : new ResourceKey[]{OVERWORLD, NETHER, END}) {
            Path folder = DimensionType.getStorageFolder(key, root);
            if (!Files.isDirectory(folder)) {
                continue;
            }
            try (Stream<Path> files = Files.walk(folder)) {
                files.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException e) {
                        EnderLaserQuarry.LOGGER.warn("Couldn't delete sampling file {}", path, e);
                    }
                });
            } catch (IOException e) {
                EnderLaserQuarry.LOGGER.warn("Couldn't clear sampling folder {}", folder, e);
            }
        }
    }

    private SamplingDimensions() {
    }
}
