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

    public static void wipe(MinecraftServer server) {
        Path root = server.getWorldPath(LevelResource.ROOT);
        for (ResourceKey<Level> key : new ResourceKey[]{OVERWORLD, NETHER, END}) {
            deleteTree(DimensionType.getStorageFolder(key, root));
        }
        // other mods keep per-dimension data in their own folders (Astral Sorcery: astralsorcery/DIM_ender_laser_quarry_sampling_overworld)
        try (Stream<Path> dirs = Files.find(root, 3, (path, attrs) -> attrs.isDirectory() && isSamplingFolder(path))) {
            dirs.toList().forEach(SamplingDimensions::deleteTree);
        } catch (IOException e) {
            EnderLaserQuarry.LOGGER.warn("Couldn't look for sampling leftovers in {}", root, e);
        }
    }

    private static boolean isSamplingFolder(Path path) {
        String name = path.getFileName() == null ? "" : path.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
        if (name.contains(EnderLaserQuarry.MOD_ID) && name.contains("sampling")) {
            return true;
        }
        Path parent = path.getParent();
        return name.startsWith("sampling_") && parent != null && parent.getFileName() != null
            && parent.getFileName().toString().equals(EnderLaserQuarry.MOD_ID);
    }

    private static void deleteTree(Path folder) {
        if (!Files.isDirectory(folder)) {
            return;
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

    private SamplingDimensions() {
    }
}
